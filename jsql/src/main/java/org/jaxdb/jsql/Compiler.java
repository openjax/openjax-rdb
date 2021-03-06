/* Copyright (c) 2017 JAX-DB
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * You should have received a copy of The MIT License (MIT) along with this
 * program. If not, see <http://opensource.org/licenses/MIT/>.
 */

package org.jaxdb.jsql;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jaxdb.ddlx.dt;
import org.jaxdb.vendor.DBVendor;
import org.jaxdb.vendor.DBVendorBase;
import org.jaxdb.vendor.Dialect;
import org.libj.io.Readers;
import org.libj.io.Streams;
import org.libj.lang.Hexadecimal;
import org.libj.lang.PackageLoader;
import org.libj.util.IdentityHashSet;
import org.libj.util.function.Throwing;

abstract class Compiler extends DBVendorBase {
  private static final Compiler[] compilers = new Compiler[DBVendor.values().length];

  static {
    try {
      PackageLoader.getContextPackageLoader().loadPackage(Compiler.class.getPackage(), Throwing.<Class<?>>rethrow(c -> {
        if (Compiler.class.isAssignableFrom(c) && !Modifier.isAbstract(c.getModifiers())) {
          final Compiler compiler = (Compiler)c.getDeclaredConstructor().newInstance();
          compilers[compiler.getVendor().ordinal()] = compiler;
        }

        return false;
      }));
    }
    catch (final Exception e) {
      if (e instanceof InvocationTargetException) {
        if (e.getCause() instanceof RuntimeException)
          throw (RuntimeException)e.getCause();

        throw new ExceptionInInitializerError(e.getCause());
      }

      throw new ExceptionInInitializerError(e);
    }
  }

  static Compiler getCompiler(final DBVendor vendor) {
    final Compiler compiler = compilers[vendor.ordinal()];
    if (compiler == null)
      throw new UnsupportedOperationException("Vendor " + vendor + " is not supported");

    return compiler;
  }

  protected Compiler(final DBVendor vendor) {
    super(vendor);
  }

  final void compileEntities(final kind.Entity<?>[] entities, final boolean isFromGroupBy, final boolean useAliases, final Map<Integer,type.ENUM<?>> translateTypes, final Compilation compilation, final boolean addToColumnTokens) throws IOException, SQLException {
    for (int i = 0; i < entities.length; ++i) {
      if (i > 0)
        compilation.comma();

      final Subject subject = (Subject)entities[i];
      compileNextSubject(subject, i, isFromGroupBy, useAliases, translateTypes, compilation, addToColumnTokens);
    }
  }

  /**
   * Default no-op implementation of method to compile an enum translation
   * phrase.
   *
   * @param from The source enum.
   * @param to The target enum.
   * @return Enum translation phrase.
   */
  String translateEnum(final type.ENUM<?> from, final type.ENUM<?> to) {
    return "";
  }

  private void checkTranslateType(final Map<Integer,type.ENUM<?>> translateTypes, final type.DataType<?> column, final int index, final Compilation compilation) {
    if (column instanceof type.ENUM<?> && translateTypes != null) {
      final type.ENUM<?> translateType = translateTypes.get(index);
      if (translateType != null)
        compilation.concat(translateEnum((type.ENUM<?>)column, translateType));
    }
  }

  void compileNextSubject(final Subject subject, final int index, final boolean isFromGroupBy, final boolean useAliases, final Map<Integer,type.ENUM<?>> translateTypes, final Compilation compilation, final boolean addToColumnTokens) throws IOException, SQLException {
    if (subject instanceof type.Table) {
      final type.Table table = (type.Table)subject;
      final Alias alias = compilation.registerAlias(table);
      for (int c = 0; c < table._column$.length; ++c) {
        final type.DataType<?> column = table._column$[c];
        if (c > 0)
          compilation.comma();

        if (useAliases) {
          alias.compile(compilation, false);
          compilation.append('.');
        }

        compilation.append(q(column.name));
        checkTranslateType(translateTypes, column, c, compilation);
        if (addToColumnTokens)
          compilation.getColumnTokens().add(compilation.tokens.get(compilation.tokens.size() - 1).toString());
      }
    }
    else if (subject instanceof type.DataType) {
      final type.DataType<?> column = (type.DataType<?>)subject;
      compilation.registerAlias(column.table);
      final Alias alias;
      if (useAliases && isFromGroupBy && column.wrapper() instanceof As && (alias = compilation.getAlias(((As<?>)column.wrapper()).getVariable())) != null)
        alias.compile(compilation, false);
      else
        column.compile(compilation, false);

      checkTranslateType(translateTypes, column, index, compilation);
      if (addToColumnTokens)
        compilation.getColumnTokens().add(compilation.tokens.get(compilation.tokens.size() - 1).toString());
    }
    else if (subject instanceof Keyword) {
      compilation.append('(');
      ((Keyword<?>)subject).compile(compilation, false);
      compilation.append(')');
    }
    else {
      throw new UnsupportedOperationException("Unsupported subject type: " + subject.getClass().getName());
    }
  }

  abstract void onRegister(Connection connection) throws SQLException;
  abstract void onConnect(Connection connection) throws SQLException;

  static <T extends kind.Entity<?>>Subject toSubject(final T kind) {
    return (Subject)kind;
  }

  /**
   * Returns the quoted name of the specified {@link type.Table}.
   *
   * @param table The {@link type.Table}.
   * @param compilation The {@link Compilation}
   * @return The quoted name of the specified {@link type.Table}.
   */
  String tableName(final type.Table table, final Compilation compilation) {
    return q(table.name());
  }

  /**
   * Get the parameter mark for {@link PreparedStatement}s.
   *
   * @param dataType The {@link type.DataType} for the requested mark.
   * @return The mark.
   */
  String getPreparedStatementMark(final type.DataType<?> dataType) {
    return "?";
  }

  /**
   * Compile the specified parameters, and append to the provided
   * {@link Compilation}.
   *
   * @param variable The variable to evaluate.
   * @param _else The {@link CaseImpl.ELSE}.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compileCaseElse(final type.DataType<?> variable, final type.DataType<?> _else, final Compilation compilation) throws IOException, SQLException {
    compilation.append("CASE ");
    variable.compile(compilation, true);
  }

  /**
   * Compile the specified parameters, and append to the provided
   * {@link Compilation}.
   *
   * @param when The {@link CaseImpl.WHEN}.
   * @param compilation The target {@link Compilation}.
   */
  void compileWhen(final CaseImpl.Search.WHEN<?> when, final Compilation compilation) {
    compilation.append("CASE");
  }

  /**
   * Compile the specified parameters, and append to the provided
   * {@link Compilation}.
   *
   * @param when The {@link CaseImpl.WHEN}.
   * @param then The {@link CaseImpl.THEN}.
   * @param _else The {@link CaseImpl.ELSE}.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compileWhenThenElse(final Subject when, final type.DataType<?> then, final type.DataType<?> _else, final Compilation compilation) throws IOException, SQLException {
    compilation.append(" WHEN ");
    when.compile(compilation, true);
    compilation.append(" THEN ");
    then.compile(compilation, true);
  }

  /**
   * Compile the specified parameters, and append to the provided
   * {@link Compilation}.
   *
   * @param _else The {@link CaseImpl.ELSE}.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compileElse(final type.DataType<?> _else, final Compilation compilation) throws IOException, SQLException {
    compilation.append(" ELSE ");
    _else.compile(compilation, true);
    compilation.append(" END");
  }

  void compileSelect(final SelectImpl.untyped.SELECT<?> select, final boolean useAliases, final Compilation compilation) throws IOException, SQLException {
    compilation.append("SELECT ");
    if (select.distinct)
      compilation.append("DISTINCT ");

    compileEntities(select.entities, false, useAliases, select.translateTypes, compilation, true);
  }

  void compileFrom(final SelectImpl.untyped.SELECT<?> select, final boolean useAliases, final Compilation compilation) throws IOException, SQLException {
    if (select.from() == null)
      return;

    compilation.append(" FROM ");

    // FIXME: If FROM is followed by a JOIN, then we must see what table the ON clause is
    // FIXME: referring to, because this table must be the last in the table order here
    final Iterator<type.Table> iterator = select.from().iterator();
    while (true) {
      final type.Table table = iterator.next();
      if (table.wrapper() != null) {
        table.wrapper().compile(compilation, false);
      }
      else {
        compilation.append(tableName(table, compilation));
        if (useAliases) {
          compilation.append(' ');
          compilation.getAlias(table).compile(compilation, false);
        }
      }

      if (iterator.hasNext())
        compilation.comma();
      else
        break;
    }
  }

  void compileJoin(final SelectImpl.untyped.SELECT.JoinKind joinKind, final Object join, final Condition<?> on, final Compilation compilation) throws IOException, SQLException {
    if (join != null) {
      compilation.append(joinKind);
      compilation.append(" JOIN ");
      if (join instanceof type.Table) {
        final type.Table table = (type.Table)join;
        compilation.append(tableName(table, compilation)).append(' ');
        compilation.registerAlias(table).compile(compilation, false);
        if (on != null) {
          compilation.append(" ON (");
          on.compile(compilation, false);
          compilation.append(')');
        }
      }
      else if (join instanceof SelectImpl.untyped.SELECT) {
        final SelectImpl.untyped.SELECT<?> select = (SelectImpl.untyped.SELECT<?>)join;
        compilation.append('(');
        final Compilation subCompilation = compilation.getSubCompilation(select);
        final Alias alias = compilation.getAlias(select);
        compilation.append(subCompilation);
        compilation.append(") ");
        compilation.append(alias);
        if (on != null) {
          compilation.append(" ON (");
          on.compile(compilation, false);
          compilation.subCompile(on);
          compilation.append(')');
        }
      }
      else {
        throw new IllegalStateException();
      }
    }
  }

  void compileWhere(final SelectImpl.untyped.SELECT<?> select, final Compilation compilation) throws IOException, SQLException {
    if (select.where() != null) {
      compilation.append(" WHERE ");
      select.where().compile(compilation, false);
    }
  }

  void compileGroupByHaving(final SelectImpl.untyped.SELECT<?> select, final boolean useAliases, final Compilation compilation) throws IOException, SQLException {
    if (select.groupBy != null) {
      compilation.append(" GROUP BY ");
      compileEntities(select.groupBy, true, useAliases, null, compilation, false);
    }

    if (select.having != null) {
      compilation.append(" HAVING ");
      select.having.compile(compilation, false);
    }
  }

  void compileOrderBy(final SelectImpl.untyped.SELECT<?> select, final Compilation compilation) throws IOException, SQLException {
    if (select.orderBy != null || select.orderByIndexes != null) {
      compilation.append(" ORDER BY ");
      if (select.orderBy != null) {
        for (int i = 0; i < select.orderBy.length; ++i) {
          final type.DataType<?> dataType = select.orderBy[i];
          if (i > 0)
            compilation.comma();

          if (dataType.wrapper() instanceof As) {
            // FIXME: This commented-out code replaces the variables in the comparison to aliases in case an AS is used.
            // FIXME: This code is commented-out, because Derby complains when this is done.
            // final Alias alias = compilation.getAlias(((As<?>)dataType.wrapper()).getVariable());
            // if (alias != null) {
            //   alias.compile(compilation);
            // }
            // else {
              unwrapAlias(dataType).compile(compilation, false);
            // }
          }
          else {
            compilation.registerAlias(dataType.table);
            dataType.compile(compilation, false);
          }
        }
      }
      else if (select.orderByIndexes != null) {
        for (int i = 0; i < select.orderByIndexes.length; ++i) {
          final int columnIndex = select.orderByIndexes[i];
          if (i > 0)
            compilation.comma();

          compilation.append(String.valueOf(columnIndex));
        }
      }
      else {
        throw new IllegalStateException();
      }
    }
  }

  void compileLimitOffset(final SelectImpl.untyped.SELECT<?> select, final Compilation compilation) {
    if (select.limit != -1) {
      compilation.append(" LIMIT " + select.limit);
      if (select.offset != -1)
        compilation.append(" OFFSET " + select.offset);
    }
  }

  boolean aliasInForUpdate() {
    return true;
  }

  void compileFor(final SelectImpl.untyped.SELECT<?> select, final Compilation compilation) {
    if (select.forLockStrength != null) {
      compilation.append(" FOR ").append(select.forLockStrength);
      if (select.forSubjects != null && select.forSubjects.length > 0)
        compileForOf(select, compilation);
    }

    if (select.forLockOption != null)
      compilation.append(' ').append(select.forLockOption);
  }

  void compileForOf(final SelectImpl.untyped.SELECT<?> select, final Compilation compilation) {
    compilation.append(" OF ");
    final HashSet<type.Table> tables = new HashSet<>(1);
    for (int i = 0; i < select.forSubjects.length; ++i) {
      final type.Entity<?> entity = select.forSubjects[i];
      final type.Table table;
      if (entity instanceof type.Table)
        table = (type.Table)entity;
      else if (entity instanceof type.DataType)
        table = ((type.DataType<?>)entity).table;
      else
        throw new UnsupportedOperationException("Unsupported type.Entity: " + entity.getClass().getName());

      if (!tables.add(table))
        continue;

      if (tables.size() > 1)
        compilation.comma();

      compilation.append(q(table.name()));
    }
  }

  void compileUnion(final SelectImpl.untyped.SELECT<?> select, final Compilation compilation) throws IOException, SQLException {
    if (select.unions != null) {
      for (int i = 0, len = select.unions.size(); i < len;) {
        final Boolean all = (Boolean)select.unions.get(i++);
        final Subject union = (Subject)select.unions.get(i++);
        compilation.append(" UNION ");
        if (all)
          compilation.append("ALL ");

        union.compile(compilation, false);
      }
    }
  }

  void compileInsert(final type.DataType<?>[] columns, final boolean ignore, final Compilation compilation) throws IOException, SQLException {
    compilation.append("INSERT ");
    if (ignore)
      compilation.append("IGNORE ");

    compilation.append("INTO ");
    compilation.append(q(columns[0].table.name())).append(" (");
    for (int i = 0; i < columns.length; ++i) {
      final type.DataType<?> column = columns[i];
      if (i > 0)
        compilation.comma();

      compilation.append(q(column.name));
    }

    compilation.append(") VALUES (");

    for (int i = 0; i < columns.length; ++i) {
      final type.DataType<?> column = columns[i];
      if (i > 0)
        compilation.comma();

      if (shouldInsert(column, true, compilation))
        compilation.addParameter(column, false);
      else
        compilation.append("DEFAULT");
    }

    compilation.append(')');
  }

  final void compileInsert(final type.Table insert, final type.DataType<?>[] columns, final boolean ignore, final Compilation compilation) throws IOException, SQLException {
    compileInsert(insert != null ? insert._column$ : columns, ignore, compilation);
  }

  Compilation compileInsertSelect(final type.DataType<?>[] columns, final Select.untyped.SELECT<?> select, final boolean ignore, final Compilation compilation) throws IOException, SQLException {
    final HashMap<Integer,type.ENUM<?>> translateTypes = new HashMap<>();
    compilation.append("INSERT ");
    if (ignore)
      compilation.append("IGNORE ");

    compilation.append("INTO ");
    compilation.append(q(columns[0].table.name()));
    compilation.append(" (");
    for (int i = 0; i < columns.length; ++i) {
      if (i > 0)
        compilation.comma();

      final type.DataType<?> column = columns[i];
      column.compile(compilation, false);
      if (column instanceof type.ENUM<?>)
        translateTypes.put(i, (type.ENUM<?>)column);
    }

    compilation.append(") ");

    final SelectImpl.untyped.SELECT<?> selectImpl = (SelectImpl.untyped.SELECT<?>)select;
    final Compilation selectCompilation = compilation.newSubCompilation(selectImpl);
    selectImpl.translateTypes = translateTypes;
    selectImpl.compile(selectCompilation, false);
    compilation.append(selectCompilation);
    return selectCompilation;
  }

  abstract void compileInsertOnConflict(type.DataType<?>[] columns, Select.untyped.SELECT<?> select, type.DataType<?>[] onConflict, boolean doUpdate, Compilation compilation) throws IOException, SQLException;

  @SuppressWarnings({"rawtypes", "unchecked"})
  static boolean shouldInsert(final type.DataType column, final boolean modify, final Compilation compilation) {
    if (column.wasSet())
      return true;

    if (column.generateOnInsert == null || column.generateOnInsert == GenerateOn.AUTO_GENERATED)
      return false;

    if (modify)
      column.generateOnInsert.generate(column, compilation.vendor);

    return true;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  static boolean shouldUpdate(final type.DataType column, final Compilation compilation) {
    if (column.primary)
      return false;

    boolean shouldUpdate = column.wasSet();
    if ((!shouldUpdate || column.keyForUpdate) && column.generateOnUpdate != null) {
      column.generateOnUpdate.generate(column, compilation.vendor);
      shouldUpdate = true;
    }

    if (column.indirection != null) {
      shouldUpdate = true;
      compilation.afterExecute(success -> {
        if (success) {
          final Object evaluated = column.evaluate(new IdentityHashSet<>());
          if (evaluated == null)
            column.setValue(null);
          else if (column.type() == evaluated.getClass())
            column.setValue(evaluated);
          else if (evaluated instanceof Number && Number.class.isAssignableFrom(column.type()))
            column.setValue(type.Numeric.valueOf((Number)evaluated, (Class<? extends Number>)column.type()));
          else
            throw new IllegalStateException("Value exceeds bounds of type " + type.DataType.getSimpleName(column.getClass()) + ": " + evaluated);
        }
      });
    }

    return shouldUpdate;
  }

  void compileUpdate(final type.Table update, final Compilation compilation) throws IOException, SQLException {
    compilation.append("UPDATE ");
    compilation.append(q(update.name()));
    compilation.append(" SET ");
    boolean modified = false;
    for (int c = 0; c < update._column$.length; ++c) {
      final type.DataType<?> column = update._column$[c];
      if (shouldUpdate(column, compilation)) {
        if (modified)
          compilation.comma();

        compilation.append(q(column.name)).append(" = ");
        compilation.addParameter(column, true);
        modified = true;
      }
    }

    // No changes were found
    if (!modified)
      return;

    modified = false;
    for (final type.DataType<?> column : update._column$) {
      if (column.primary || column.keyForUpdate) {
        if (modified)
          compilation.append(" AND ");
        else
          compilation.append(" WHERE ");

        compilation.addCondition(column, false);
        modified = true;
      }
    }
  }

  void compileUpdate(final type.Table update, final List<Subject> sets, final Condition<?> where, final Compilation compilation) throws IOException, SQLException {
    compilation.append("UPDATE ");
    compilation.append(q(update.name()));
    compilation.append(" SET ");
    for (int i = 0, len = sets.size(); i < len;) {
      if (i > 0)
        compilation.comma();

      final type.DataType<?> column = (type.DataType<?>)sets.get(i++);
      final Subject to = sets.get(i++);
      compilation.append(q(column.name)).append(" = ");
      to.compile(compilation, false);
    }

    if (where != null) {
      compilation.append(" WHERE ");
      where.compile(compilation, false);
    }
  }

  void compileDelete(final type.Table delete, final Compilation compilation) throws IOException, SQLException {
    compilation.append("DELETE FROM ");
    compilation.append(q(delete.name()));
    boolean modified = false;
    for (int j = 0; j < delete._column$.length; ++j) {
      final type.DataType<?> column = delete._column$[j];
      if (column.wasSet()) {
        if (modified)
          compilation.append(" AND ");
        else
          compilation.append(" WHERE ");

        compilation.addCondition(column, false);
        modified = true;
      }
    }
  }

  void compileDelete(final type.Table delete, final Condition<?> where, final Compilation compilation) throws IOException, SQLException {
    compilation.append("DELETE FROM ");
    compilation.append(q(delete.name()));
    compilation.append(" WHERE ");
    where.compile(compilation, false);
  }

  <T extends type.Entity<?>>void compile(final type.Table table, final Compilation compilation, final boolean isExpression) throws IOException, SQLException {
    if (table.wrapper() != null) {
      table.wrapper().compile(compilation, isExpression);
    }
    else {
      compilation.append(tableName(table, compilation));
      final Alias alias = compilation.registerAlias(table);
      compilation.append(' ');
      alias.compile(compilation, true);
    }
  }

  void compile(final expression.ChangeCase expression, final Compilation compilation) throws IOException, SQLException {
    compilation.append(expression.operator).append('(');
    toSubject(expression.a).compile(compilation, true);
    compilation.append(')');
  }

  void compile(final expression.Concat expression, final Compilation compilation) throws IOException, SQLException {
    compilation.append('(');
    for (int i = 0; i < expression.a.length; ++i) {
      final Subject arg = toSubject(expression.a[i]);
      if (i > 0)
        compilation.append(" || ");

      arg.compile(compilation, true);
    }
    compilation.append(')');
  }

  void compile(final Interval interval, final Compilation compilation) {
    compilation.append("INTERVAL '");
    final List<TemporalUnit> units = interval.getUnits();
    for (int i = 0, len = units.size(); i < len; ++i) {
      final TemporalUnit unit = units.get(i);
      if (i > 0)
        compilation.append(' ');

      compilation.append(interval.get(unit)).append(' ').append(unit);
    }

    compilation.append('\'');
  }

  void compile(final expression.Temporal expression, final Compilation compilation) throws IOException, SQLException {
    compilation.append("((");
    expression.a.compile(compilation, true);
    compilation.append(") ");
    compilation.append(expression.operator);
    compilation.append(" (");
    expression.b.compile(compilation, true);
    compilation.append("))");
  }

  void compile(final expression.Numeric expression, final Compilation compilation) throws IOException, SQLException {
    compilation.append("((");
    toSubject(expression.a).compile(compilation, true);
    compilation.append(") ").append(expression.operator).append(" (");
    toSubject(expression.b).compile(compilation, true);
    compilation.append("))");
  }

  static void compile(final type.DataType<?> dataType, final Compilation compilation, final boolean isExpression) throws IOException, SQLException {
    if (dataType.wrapper() == null) {
      if (dataType.table != null) {
        Alias alias = compilation.getAlias(dataType.table);
        if (alias != null) {
          alias.compile(compilation, false);
          compilation.concat("." + compilation.vendor.getDialect().quoteIdentifier(dataType.name));
        }
        else if (!compilation.subCompile(dataType.table)) {
          compilation.append(compilation.vendor.getDialect().quoteIdentifier(dataType.name));
        }
        else {
          return;
        }
      }
      else {
        compilation.addParameter(dataType, false);
      }
    }
    else if (!compilation.subCompile(dataType)) {
      dataType.wrapper().compile(compilation, isExpression);
    }
  }

  /**
   * Compile the specified {@link Alias}, and append to the provided {@link Compilation}.
   *
   * @param alias The {@link Alias}.
   * @param compilation The target {@link Compilation}.
   */
  void compile(final Alias alias, final Compilation compilation) {
    compilation.append(alias.name);
  }

  /**
   * Returns the string representation of the specified {@link As}.
   *
   * @param as The {@link As}.
   * @return The string representation of the specified {@link As}.
   */
  String compile(final As<?> as) {
    return "AS";
  }

  void compile(final As<?> as, final Compilation compilation, final boolean isExpression) throws IOException, SQLException {
    final Alias alias = compilation.registerAlias(as.getVariable());
    compilation.append('(');
    as.parent().compile(compilation, true);
    compilation.append(')');
    if (!isExpression && as.isExplicit()) {
      final String string = compile(as);
      compilation.append(' ');
      if (string != null && string.length() != 0)
        compilation.append(string).append(' ');

      alias.compile(compilation, false);
    }
  }

  // FIXME: Move this to a Util class or something
  static <T extends type.Entity<?>> void formatBraces(final operator.Boolean operator, final Condition<?> condition, final Compilation compilation) throws IOException, SQLException {
    if (condition instanceof BooleanTerm) {
      if (operator == ((BooleanTerm)condition).operator) {
        condition.compile(compilation, false);
      }
      else {
        compilation.append('(');
        condition.compile(compilation, false);
        compilation.append(')');
      }
    }
    else {
      condition.compile(compilation, false);
    }
  }

  void compile(final BooleanTerm condition, final Compilation compilation) throws IOException, SQLException {
    formatBraces(condition.operator, condition.a, compilation);
    compilation.append(' ').append(condition.operator).append(' ');
    formatBraces(condition.operator, condition.b, compilation);
    for (int i = 0; i < condition.conditions.length; ++i) {
      compilation.append(' ').append(condition.operator).append(' ');
      formatBraces(condition.operator, condition.conditions[i], compilation);
    }
  }

  private static Subject unwrapAlias(final Subject subject) {
    if (!(subject instanceof type.Entity))
      return subject;

    final type.Entity<?> entity = (type.Entity<?>)subject;
    if (!(entity.wrapper() instanceof As))
      return subject;

    return ((As<?>)entity.wrapper()).parent();
  }

  void compile(final ComparisonPredicate<?> predicate, final Compilation compilation) throws IOException, SQLException {
    if (!compilation.subCompile(predicate.a)) {
      // FIXME: This commented-out code replaces the variables in the comparison to aliases in case an AS is used.
      // FIXME: This code is commented-out, because Derby complains when this is done.
      // final Alias alias;
      // if (predicate.a.wrapper() instanceof As && (alias = compilation.getAlias(((As<?>)predicate.a.wrapper()).getVariable())) != null)
      //   alias.compile(compilation);
      // else
        unwrapAlias(predicate.a).compile(compilation, true);
    }

    compilation.append(' ').append(predicate.operator).append(' ');
    if (!compilation.subCompile(predicate.b)) {
      // FIXME: This commented-out code replaces the variables in the comparison to aliases in case an AS is used.
      // FIXME: This code is commented-out, because Derby complains when this is done.
      // final Alias alias;
      // if (predicate.b.wrapper() instanceof As && (alias = compilation.getAlias(((As<?>)predicate.b.wrapper()).getVariable())) != null)
      // alias.compile(compilation);
      //   else
        unwrapAlias(predicate.b).compile(compilation, true);
    }
  }

  void compile(final InPredicate predicate, final Compilation compilation) throws IOException, SQLException {
    toSubject(predicate.dataType).compile(compilation, true);
    compilation.append(' ');
    if (!predicate.positive)
      compilation.append("NOT ");

    compilation.append("IN (");
    for (int i = 0; i < predicate.values.length; ++i) {
      if (i > 0)
        compilation.comma();

      predicate.values[i].compile(compilation, true);
    }

    compilation.append(')');
  }

  void compile(final ExistsPredicate predicate, final Compilation compilation) throws IOException, SQLException {
    compilation.append("EXISTS (");
    predicate.subQuery.compile(compilation, true);
    compilation.append(')');
  }

  void compile(final LikePredicate predicate, final Compilation compilation) throws IOException, SQLException {
    compilation.append('(');
    toSubject(predicate.dataType).compile(compilation, true);
    compilation.append(") ");
    if (!predicate.positive)
      compilation.append("NOT ");

    compilation.append("LIKE '").append(predicate.pattern).append('\'');
  }

  void compile(final QuantifiedComparisonPredicate<?> predicate, final Compilation compilation) throws IOException, SQLException {
    compilation.append(predicate.qualifier).append(" (");
    predicate.subQuery.compile(compilation, true);
    compilation.append(')');
  }

  void compile(final BetweenPredicates.BetweenPredicate predicate, final Compilation compilation) throws IOException, SQLException {
    compilation.append('(');
    toSubject(predicate.dataType).compile(compilation, true);
    compilation.append(')');
    if (!predicate.positive)
      compilation.append(" NOT");

    compilation.append(" BETWEEN ");
    predicate.a().compile(compilation, true);
    compilation.append(" AND ");
    predicate.b().compile(compilation, true);
  }

  <T> void compile(final NullPredicate predicate, final Compilation compilation) throws IOException, SQLException {
    toSubject(predicate.dataType).compile(compilation, true);
    compilation.append(" IS ");
    if (!predicate.positive)
      compilation.append("NOT ");

    compilation.append("NULL");
  }

  /**
   * Compile the PI function, and append to the provided {@link Compilation}.
   *
   * @param function The function to compile.
   * @param compilation The target {@link Compilation}.
   */
  void compile(final function.Pi function, final Compilation compilation) {
    compilation.append("PI()");
  }

  /**
   * Compile the ABS function, and append to the provided {@link Compilation}.
   *
   * @param function The function to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final function.Abs function, final Compilation compilation) throws IOException, SQLException {
    compilation.append("ABS(");
    function.a.compile(compilation, true);
    compilation.append(')');
  }

  /**
   * Compile the SIGN function, and append to the provided {@link Compilation}.
   *
   * @param function The function to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final function.Sign function, final Compilation compilation) throws IOException, SQLException {
    compilation.append("SIGN(");
    function.a.compile(compilation, true);
    compilation.append(')');
  }

  /**
   * Compile the ROUND function, and append to the provided {@link Compilation}.
   *
   * @param function The function to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final function.Round function, final Compilation compilation) throws IOException, SQLException {
    compilation.append("ROUND(");
    function.a.compile(compilation, true);
    compilation.comma();
    function.b.compile(compilation, true);
    compilation.append(')');
  }

  /**
   * Compile the FLOOR function, and append to the provided {@link Compilation}.
   *
   * @param function The function to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final function.Floor function, final Compilation compilation) throws IOException, SQLException {
    compilation.append("FLOOR(");
    function.a.compile(compilation, true);
    compilation.append(')');
  }

  /**
   * Compile the CEIL function, and append to the provided {@link Compilation}.
   *
   * @param function The function to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final function.Ceil function, final Compilation compilation) throws IOException, SQLException {
    compilation.append("CEIL(");
    function.a.compile(compilation, true);
    compilation.append(')');
  }

  /**
   * Compile the SQRT function, and append to the provided {@link Compilation}.
   *
   * @param function The function to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final function.Sqrt function, final Compilation compilation) throws IOException, SQLException {
    compilation.append("SQRT(");
    function.a.compile(compilation, true);
    compilation.append(')');
  }

  /**
   * Compile the DEGREES function, and append to the provided {@link Compilation}.
   *
   * @param function The function to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final function.Degrees function, final Compilation compilation) throws IOException, SQLException {
    compilation.append("DEGREES(");
    function.a.compile(compilation, true);
    compilation.append(')');
  }

  /**
   * Compile the RADIANS function, and append to the provided {@link Compilation}.
   *
   * @param function The function to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final function.Radians function, final Compilation compilation) throws IOException, SQLException {
    compilation.append("RADIANS(");
    function.a.compile(compilation, true);
    compilation.append(')');
  }

  /**
   * Compile the POW function, and append to the provided {@link Compilation}.
   *
   * @param function The function to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final function.Pow function, final Compilation compilation) throws IOException, SQLException {
    compilation.append("POWER(");
    function.a.compile(compilation, true);
    compilation.comma();
    function.b.compile(compilation, true);
    compilation.append(')');
  }

  /**
   * Compile the MOD function, and append to the provided {@link Compilation}.
   *
   * @param function The function to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final function.Mod function, final Compilation compilation) throws IOException, SQLException {
    compilation.append("MOD(");
    function.a.compile(compilation, true);
    compilation.comma();
    function.b.compile(compilation, true);
    compilation.append(')');
  }

  /**
   * Compile the SIN function, and append to the provided {@link Compilation}.
   *
   * @param function The function to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final function.Sin function, final Compilation compilation) throws IOException, SQLException {
    compilation.append("SIN(");
    function.a.compile(compilation, true);
    compilation.append(')');
  }

  /**
   * Compile the ASIN function, and append to the provided {@link Compilation}.
   *
   * @param function The function to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final function.Asin function, final Compilation compilation) throws IOException, SQLException {
    compilation.append("ASIN(");
    function.a.compile(compilation, true);
    compilation.append(')');
  }

  /**
   * Compile the COS function, and append to the provided {@link Compilation}.
   *
   * @param function The function to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final function.Cos function, final Compilation compilation) throws IOException, SQLException {
    compilation.append("COS(");
    function.a.compile(compilation, true);
    compilation.append(')');
  }

  /**
   * Compile the ACOS function, and append to the provided {@link Compilation}.
   *
   * @param function The function to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final function.Acos function, final Compilation compilation) throws IOException, SQLException {
    compilation.append("ACOS(");
    function.a.compile(compilation, true);
    compilation.append(')');
  }

  /**
   * Compile the TAN function, and append to the provided {@link Compilation}.
   *
   * @param function The function to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final function.Tan function, final Compilation compilation) throws IOException, SQLException {
    compilation.append("TAN(");
    function.a.compile(compilation, true);
    compilation.append(')');
  }

  /**
   * Compile the ATAN function, and append to the provided {@link Compilation}.
   *
   * @param function The function to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final function.Atan function, final Compilation compilation) throws IOException, SQLException {
    compilation.append("ATAN(");
    function.a.compile(compilation, true);
    compilation.append(')');
  }

  /**
   * Compile the ATAN2 function, and append to the provided {@link Compilation}.
   *
   * @param function The function to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final function.Atan2 function, final Compilation compilation) throws IOException, SQLException {
    compilation.append("ATAN2(");
    function.a.compile(compilation, true);
    compilation.comma();
    function.b.compile(compilation, true);
    compilation.append(')');
  }

  /**
   * Compile the EXP function, and append to the provided {@link Compilation}.
   *
   * @param function The function to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final function.Exp function, final Compilation compilation) throws IOException, SQLException {
    compilation.append("EXP(");
    function.a.compile(compilation, true);
    compilation.append(')');
  }

  /**
   * Compile the LN function, and append to the provided {@link Compilation}.
   *
   * @param function The function to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final function.Ln function, final Compilation compilation) throws IOException, SQLException {
    compilation.append("LN(");
    function.a.compile(compilation, true);
    compilation.append(')');
  }

  /**
   * Compile the LOG function, and append to the provided {@link Compilation}.
   *
   * @param function The function to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final function.Log function, final Compilation compilation) throws IOException, SQLException {
    compilation.append("LOG(");
    function.a.compile(compilation, true);
    compilation.comma();
    function.b.compile(compilation, true);
    compilation.append(')');
  }

  /**
   * Compile the LOG2 function, and append to the provided {@link Compilation}.
   *
   * @param function The function to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final function.Log2 function, final Compilation compilation) throws IOException, SQLException {
    compilation.append("LOG2(");
    function.a.compile(compilation, true);
    compilation.append(')');
  }

  /**
   * Compile the LOG10 function, and append to the provided {@link Compilation}.
   *
   * @param function The function to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final function.Log10 function, final Compilation compilation) throws IOException, SQLException {
    compilation.append("LOG10(");
    function.a.compile(compilation, true);
    compilation.append(')');
  }

  /**
   * Compile the COUNT expression, and append to the provided {@link Compilation}.
   *
   * @param expression The expression to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final expression.Count expression, final Compilation compilation) throws IOException, SQLException {
    compilation.append("COUNT").append('(');
    if (expression.a instanceof type.Table) {
      compilation.append('*');
    }
    else {
      if (expression.distinct)
        compilation.append("DISTINCT ");

      toSubject(expression.a).compile(compilation, true);
    }

    compilation.append(')');
  }

  /**
   * Compile the SET expression, and append to the provided {@link Compilation}.
   *
   * @param expression The expression to compile.
   * @param compilation The target {@link Compilation}.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void compile(final expression.Set expression, final Compilation compilation) throws IOException, SQLException {
    compilation.append(expression.function).append('(');
    if (expression.a != null) {
      if (expression.distinct)
        compilation.append("DISTINCT ");

      expression.a.compile(compilation, true);

      // if (function.b != null) {
      // compilation.comma();
      // function.b.compile(compilation);
      // }
    }

    compilation.append(')');
  }

  void compile(final OrderingSpec spec, final Compilation compilation) throws IOException, SQLException {
    unwrapAlias(spec.dataType).compile(compilation, true);
    compilation.append(' ').append(spec.operator);
  }

  void compile(final function.Temporal function, final Compilation compilation) {
    compilation.append(function.function).append("()");
  }

  <T>String compile(final type.ARRAY<? extends T> column, final type.DataType<T> dataType) throws IOException {
    final StringBuilder builder = new StringBuilder("(");
    final type.DataType<T> clone = dataType.clone();
    final T[] items = column.get();
    for (int i = 0; i < items.length; ++i) {
      clone.setValue(items[i]);
      if (i > 0)
        builder.append(", ");

      builder.append(type.DataType.compile(dataType, getVendor()));
    }

    return builder.append(')').toString();
  }

  void compile(final Cast.AS as, final Compilation compilation) throws IOException, SQLException {
    compilation.append("CAST((");
    toSubject(as.dataType).compile(compilation, true);
    compilation.append(") AS ").append(as.cast.declare(compilation.vendor)).append(')');
  }

  String cast(final type.DataType<?> dataType, final Compilation compilation) {
    return dataType.declare(compilation.vendor);
  }

  String compile(final type.BIGINT dataType) {
    return dataType.isNull() ? "NULL" : Dialect.NUMBER_FORMAT.get().format(dataType.get());
  }

  String compile(final type.BINARY dataType) {
    return dataType.isNull() ? "NULL" : "X'" + new Hexadecimal(dataType.get()) + "'";
  }

  String compile(final type.BLOB dataType) throws IOException {
    try (final InputStream in = dataType.get()) {
      return in == null ? "NULL" : "X'" + new Hexadecimal(Streams.readBytes(in)) + "'";
    }
  }

  String compile(final type.BOOLEAN dataType) {
    return String.valueOf(dataType.get()).toUpperCase();
  }

  String compile(final type.CHAR dataType) {
    return dataType.isNull() ? "NULL" : "'" + dataType.get().replace("'", "''") + "'";
  }

  String compile(final type.CLOB dataType) throws IOException {
    try (final Reader in = dataType.get()) {
      return in == null ? "NULL" : "'" + Readers.readFully(in) + "'";
    }
  }

  String compile(final type.DATE dataType) {
    return dataType.isNull() ? "NULL" : "'" + Dialect.dateToString(dataType.get()) + "'";
  }

  String compile(final type.DATETIME dataType) {
    return dataType.isNull() ? "NULL" : "'" + Dialect.dateTimeToString(dataType.get()) + "'";
  }

  String compile(final type.DECIMAL dataType) {
    return dataType.isNull() ? "NULL" : Dialect.NUMBER_FORMAT.get().format(dataType.get());
  }

  String compile(final type.DOUBLE dataType) {
    return dataType.isNull() ? "NULL" : Dialect.NUMBER_FORMAT.get().format(dataType.get());
  }

  String compile(final type.ENUM<?> dataType) {
    return dataType.isNull() ? "NULL" : "'" + dataType.get() + "'";
  }

  String compile(final type.FLOAT dataType) {
    return dataType.isNull() ? "NULL" : Dialect.NUMBER_FORMAT.get().format(dataType.get());
  }

  String compile(final type.INT dataType) {
    return dataType.isNull() ? "NULL" : Dialect.NUMBER_FORMAT.get().format(dataType.get());
  }

  String compile(final type.SMALLINT dataType) {
    return dataType.isNull() ? "NULL" : Dialect.NUMBER_FORMAT.get().format(dataType.get());
  }

  String compile(final type.TINYINT dataType) {
    return dataType.isNull() ? "NULL" : Dialect.NUMBER_FORMAT.get().format(dataType.get());
  }

  String compile(final type.TIME dataType) {
    return dataType.isNull() ? "NULL" : "'" + Dialect.timeToString(dataType.get()) + "'";
  }

  void assignAliases(final Collection<type.Table> from, final List<Object> joins, final Compilation compilation) throws IOException, SQLException {
    if (from != null) {
      for (final type.Table table : from) {
        table.wrapper(null);
        compilation.registerAlias(table);
      }
    }

    if (joins != null) {
      for (int i = 0, len = joins.size(); i < len;) {
        final SelectImpl.untyped.SELECT.JoinKind joinKind = (SelectImpl.untyped.SELECT.JoinKind)joins.get(i++);
        final Subject join = (Subject)joins.get(i++);
        if (join instanceof type.Table) {
          final type.Table table = (type.Table)join;
          table.wrapper(null);
          compilation.registerAlias(table);
        }
        else if (join instanceof SelectImpl.untyped.SELECT) {
          final SelectImpl.untyped.SELECT<?> select = (SelectImpl.untyped.SELECT<?>)join;
          final Compilation subCompilation = compilation.newSubCompilation(select);
          compilation.registerAlias(select);
          select.compile(subCompilation, false);
        }
        else {
          throw new IllegalStateException();
        }
      }
    }
  }

  /**
   * Sets the specified {@link type.DataType} as a parameter in the provided
   * {@link PreparedStatement} at the given parameter index.
   *
   * @param dataType The data type.
   * @param statement The {@link PreparedStatement}.
   * @param parameterIndex The parameter index.
   * @throws SQLException If a SQL error has occurred.
   */
  void setParameter(final type.CHAR dataType, final PreparedStatement statement, final int parameterIndex) throws SQLException {
    if (dataType.isNull())
      statement.setNull(parameterIndex, dataType.sqlType());
    else
      statement.setString(parameterIndex, dataType.get());
  }

  /**
   * Sets the specified {@link type.DataType} as a parameter in the provided
   * {@link PreparedStatement} at the given parameter index.
   *
   * @param dataType The data type.
   * @param resultSet The {@link PreparedStatement}.
   * @param columnIndex The parameter index.
   * @throws SQLException If a SQL error has occurred.
   */
  void updateColumn(final type.CHAR dataType, final ResultSet resultSet, final int columnIndex) throws SQLException {
    if (dataType.isNull())
      resultSet.updateNull(columnIndex);
    else
      resultSet.updateString(columnIndex, dataType.get());
  }

  /**
   * Returns the parameter of the specified {@link type.DataType} from the
   * provided {@link ResultSet} at the given column index.
   *
   * @param dataType The data type.
   * @param resultSet The {@link ResultSet}.
   * @param columnIndex The column index.
   * @return The parameter of the specified {@code dataType} from the provided
   *         {@link ResultSet} at the given column index.
   * @throws SQLException If a SQL error has occurred.
   */
  // FIXME: This should be named getColumn()
  String getParameter(final type.CHAR dataType, final ResultSet resultSet, final int columnIndex) throws SQLException {
    return resultSet.getString(columnIndex);
  }

  /**
   * Sets the specified {@link type.DataType} as a parameter in the provided
   * {@link PreparedStatement} at the given parameter index.
   *
   * @param dataType The data type.
   * @param statement The {@link PreparedStatement}.
   * @param parameterIndex The parameter index.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void setParameter(final type.CLOB dataType, final PreparedStatement statement, final int parameterIndex) throws IOException, SQLException {
    final Reader in = dataType.get();
    if (in != null)
      statement.setClob(parameterIndex, in);
    else
      statement.setNull(parameterIndex, dataType.sqlType());
  }

  /**
   * Sets the specified {@link type.DataType} as a parameter in the provided
   * {@link PreparedStatement} at the given parameter index.
   *
   * @param dataType The data type.
   * @param resultSet The {@link PreparedStatement}.
   * @param columnIndex The parameter index.
   * @throws SQLException If a SQL error has occurred.
   */
  void updateColumn(final type.CLOB dataType, final ResultSet resultSet, final int columnIndex) throws SQLException {
    final Reader in = dataType.get();
    if (in != null)
      resultSet.updateClob(columnIndex, in);
    else
      resultSet.updateNull(columnIndex);
  }

  /**
   * Returns the parameter of the specified {@link type.DataType} from the
   * provided {@link ResultSet} at the given column index.
   *
   * @param dataType The data type.
   * @param resultSet The {@link ResultSet}.
   * @param columnIndex The column index.
   * @return The parameter of the specified {@code dataType} from the provided
   *         {@link ResultSet} at the given column index.
   * @throws SQLException If a SQL error has occurred.
   */
  Reader getParameter(final type.CLOB dataType, final ResultSet resultSet, final int columnIndex) throws SQLException {
    final Clob value = resultSet.getClob(columnIndex);
    return value == null ? null : value.getCharacterStream();
  }

  /**
   * Sets the specified {@link type.DataType} as a parameter in the provided
   * {@link PreparedStatement} at the given parameter index.
   *
   * @param dataType The data type.
   * @param statement The {@link PreparedStatement}.
   * @param parameterIndex The parameter index.
   * @throws IOException If an I/O error has occurred.
   * @throws SQLException If a SQL error has occurred.
   */
  void setParameter(final type.BLOB dataType, final PreparedStatement statement, final int parameterIndex) throws IOException, SQLException {
    final InputStream in = dataType.get();
    if (in == null)
      statement.setBlob(parameterIndex, in);
    else
      statement.setNull(parameterIndex, Types.BLOB);
  }

  /**
   * Sets the specified {@link type.DataType} as a parameter in the provided
   * {@link PreparedStatement} at the given parameter index.
   *
   * @param dataType The data type.
   * @param resultSet The {@link PreparedStatement}.
   * @param columnIndex The parameter index.
   * @throws SQLException If a SQL error has occurred.
   */
  void updateColumn(final type.BLOB dataType, final ResultSet resultSet, final int columnIndex) throws SQLException {
    final InputStream in = dataType.get();
    if (in != null)
      resultSet.updateBlob(columnIndex, in);
    else
      resultSet.updateNull(columnIndex);
  }

  /**
   * Returns the parameter of the specified {@link type.DataType} from the
   * provided {@link ResultSet} at the given column index.
   *
   * @param dataType The data type.
   * @param resultSet The {@link ResultSet}.
   * @param columnIndex The column index.
   * @return The parameter of the specified {@code dataType} from the provided
   *         {@link ResultSet} at the given column index.
   * @throws SQLException If a SQL error has occurred.
   */
  InputStream getParameter(final type.BLOB dataType, final ResultSet resultSet, final int columnIndex) throws SQLException {
    return resultSet.getBinaryStream(columnIndex);
  }

  /**
   * Sets the specified {@link type.DataType} as a parameter in the provided
   * {@link PreparedStatement} at the given parameter index.
   *
   * @param dataType The data type.
   * @param statement The {@link PreparedStatement}.
   * @param parameterIndex The parameter index.
   * @throws SQLException If a SQL error has occurred.
   */
  @SuppressWarnings("deprecation")
  void setParameter(final type.DATE dataType, final PreparedStatement statement, final int parameterIndex) throws SQLException {
    final LocalDate value = dataType.get();
    if (value != null)
      statement.setDate(parameterIndex, new Date(value.getYear() - 1900, value.getMonthValue() - 1, value.getDayOfMonth()));
    else
      statement.setNull(parameterIndex, dataType.sqlType());
  }

  /**
   * Sets the specified {@link type.DataType} as a parameter in the provided
   * {@link PreparedStatement} at the given parameter index.
   *
   * @param dataType The data type.
   * @param resultSet The {@link PreparedStatement}.
   * @param columnIndex The parameter index.
   * @throws SQLException If a SQL error has occurred.
   */
  @SuppressWarnings("deprecation")
  void updateColumn(final type.DATE dataType, final ResultSet resultSet, final int columnIndex) throws SQLException {
    final LocalDate value = dataType.get();
    if (value != null)
      resultSet.updateDate(columnIndex, new Date(value.getYear() - 1900, value.getMonthValue() - 1, value.getDayOfMonth()));
    else
      resultSet.updateNull(columnIndex);
  }

  /**
   * Returns the parameter of the specified {@link type.DataType} from the
   * provided {@link ResultSet} at the given column index.
   *
   * @param dataType The data type.
   * @param resultSet The {@link ResultSet}.
   * @param columnIndex The column index.
   * @return The parameter of the specified {@code dataType} from the provided
   *         {@link ResultSet} at the given column index.
   * @throws SQLException If a SQL error has occurred.
   */
  @SuppressWarnings("deprecation")
  LocalDate getParameter(final type.DATE dataType, final ResultSet resultSet, final int columnIndex) throws SQLException {
    final Date value = resultSet.getDate(columnIndex);
    return resultSet.wasNull() || value == null ? null : LocalDate.of(value.getYear() + 1900, value.getMonth() + 1, value.getDate());
  }

  /**
   * Sets the specified {@link type.DataType} as a parameter in the provided
   * {@link PreparedStatement} at the given parameter index.
   *
   * @param dataType The data type.
   * @param statement The {@link PreparedStatement}.
   * @param parameterIndex The parameter index.
   * @throws SQLException If a SQL error has occurred.
   */
  void setParameter(final type.TIME dataType, final PreparedStatement statement, final int parameterIndex) throws SQLException {
    final LocalTime value = dataType.get();
    if (value != null)
      statement.setTimestamp(parameterIndex, Timestamp.valueOf("1970-01-01 " + Dialect.timeToString(value)));
    else
      statement.setNull(parameterIndex, dataType.sqlType());
  }

  /**
   * Sets the specified {@link type.DataType} as a parameter in the provided
   * {@link PreparedStatement} at the given parameter index.
   *
   * @param dataType The data type.
   * @param resultSet The {@link PreparedStatement}.
   * @param columnIndex The parameter index.
   * @throws SQLException If a SQL error has occurred.
   */
  void updateColumn(final type.TIME dataType, final ResultSet resultSet, final int columnIndex) throws SQLException {
    final LocalTime value = dataType.get();
    if (value != null)
      resultSet.updateTimestamp(columnIndex, Timestamp.valueOf("1970-01-01 " + Dialect.timeToString(value)));
    else
      resultSet.updateNull(columnIndex);
  }

  /**
   * Returns the parameter of the specified {@link type.DataType} from the
   * provided {@link ResultSet} at the given column index.
   *
   * @param dataType The data type.
   * @param resultSet The {@link ResultSet}.
   * @param columnIndex The column index.
   * @return The parameter of the specified {@code dataType} from the provided
   *         {@link ResultSet} at the given column index.
   * @throws SQLException If a SQL error has occurred.
   */
  LocalTime getParameter(final type.TIME dataType, final ResultSet resultSet, final int columnIndex) throws SQLException {
    final Timestamp value = resultSet.getTimestamp(columnIndex);
    return resultSet.wasNull() || value == null ? null : value.toLocalDateTime().toLocalTime();
  }

  /**
   * Sets the specified {@link type.DataType} as a parameter in the provided
   * {@link PreparedStatement} at the given parameter index.
   *
   * @param dataType The data type.
   * @param statement The {@link PreparedStatement}.
   * @param parameterIndex The parameter index.
   * @throws SQLException If a SQL error has occurred.
   */
  void setParameter(final type.DATETIME dataType, final PreparedStatement statement, final int parameterIndex) throws SQLException {
    final LocalDateTime value = dataType.get();
    if (value != null)
      statement.setTimestamp(parameterIndex, dt.DATETIME.toTimestamp(value));
    else
      statement.setNull(parameterIndex, dataType.sqlType());
  }

  /**
   * Sets the specified {@link type.DataType} as a parameter in the provided
   * {@link PreparedStatement} at the given parameter index.
   *
   * @param dataType The data type.
   * @param resultSet The {@link PreparedStatement}.
   * @param columnIndex The parameter index.
   * @throws SQLException If a SQL error has occurred.
   */
  void updateColumn(final type.DATETIME dataType, final ResultSet resultSet, final int columnIndex) throws SQLException {
    final LocalDateTime value = dataType.get();
    if (value != null)
      resultSet.updateTimestamp(columnIndex, dt.DATETIME.toTimestamp(value));
    else
      resultSet.updateNull(columnIndex);
  }

  /**
   * Returns the parameter of the specified {@link type.DataType} from the
   * provided {@link ResultSet} at the given column index.
   *
   * @param dataType The data type.
   * @param resultSet The {@link ResultSet}.
   * @param columnIndex The column index.
   * @return The parameter of the specified {@code dataType} from the provided
   *         {@link ResultSet} at the given column index.
   * @throws SQLException If a SQL error has occurred.
   */
  @SuppressWarnings("deprecation")
  LocalDateTime getParameter(final type.DATETIME dataType, final ResultSet resultSet, final int columnIndex) throws SQLException {
    final Timestamp value = resultSet.getTimestamp(columnIndex);
    return resultSet.wasNull() || value == null ? null : LocalDateTime.of(value.getYear() + 1900, value.getMonth() + 1, value.getDate(), value.getHours(), value.getMinutes(), value.getSeconds(), value.getNanos());
  }

  boolean supportsPreparedBatch() {
    return true;
  }

  boolean supportsReturnGeneratedKeysBatch() {
    return true;
  }

  String prepareSqlReturning(final String sql, final type.DataType<?>[] autos) {
    return sql;
  }

  PreparedStatement prepareStatementReturning(final Connection connection, final String sql, final type.DataType<?>[] autos) throws SQLException {
    return connection.prepareStatement(prepareSqlReturning(sql, autos), Statement.RETURN_GENERATED_KEYS);
  }

  int executeUpdateReturning(final Statement statement, final String sql, final type.DataType<?>[] autos) throws SQLException {
    return statement.executeUpdate(prepareSqlReturning(sql, autos), Statement.RETURN_GENERATED_KEYS);
  }
}