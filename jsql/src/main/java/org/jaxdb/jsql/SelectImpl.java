/* Copyright (c) 2014 JAX-DB
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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.jaxdb.vendor.DBVendor;
import org.libj.lang.Throwables;
import org.libj.sql.AuditConnection;
import org.libj.sql.AuditStatement;
import org.libj.sql.ResultSets;
import org.libj.sql.exception.SQLExceptions;

// FIXME: Order the declarations to be same as in Select.java
final class SelectImpl {
  private static final Predicate<kind.Entity<?>> entitiesWithOwnerPredicate = t -> !(t instanceof type.DataType) || ((type.DataType<?>)t).table != null;

  private static Object[][] compile(final kind.Entity<?>[] entities, final int index, final int depth) {
    if (index == entities.length)
      return new Object[depth][2];

    final kind.Entity<?> entity = entities[index];
    if (entity instanceof type.Table) {
      final type.Table table = (type.Table)entity;
      final Object[][] dataTypes = compile(entities, index + 1, depth + table._column$.length);
      for (int i = 0; i < table._column$.length; ++i) {
        final Object[] array = dataTypes[depth + i];
        array[0] = table._column$[i];
        array[1] = i;
      }

      return dataTypes;
    }

    if (entity instanceof type.DataType) {
      final type.DataType<?> dataType = (type.DataType<?>)entity;
      final Object[][] dataTypes = compile(entities, index + 1, depth + 1);
      final Object[] array = dataTypes[depth];
      array[0] = dataType;
      array[1] = -1;
      return dataTypes;
    }

    if (entity instanceof Keyword) {
      final untyped.SELECT<?> select = (untyped.SELECT<?>)entity;
      if (select.entities.length != 1)
        throw new IllegalStateException("Expected 1 entity, but got " + select.entities.length);

      final kind.Entity<?> selectEntity = select.entities[0];
      if (!(selectEntity instanceof type.DataType))
        throw new IllegalStateException("Expected DataType, but got: " + selectEntity.getClass().getName());

      final Object[][] dataTypes = compile(entities, index + 1, depth + 1);
      final Object[] array = dataTypes[depth];
      array[0] = selectEntity;
      array[1] = -1;
      return dataTypes;
    }

    throw new IllegalStateException("Unknown entity type: " + entity.getClass().getName());
  }

  public static class untyped {
    abstract static class SELECT<T extends type.Entity<?>> extends Command<T> implements Select.untyped._SELECT<T>, Select.untyped.FROM<T>, Select.untyped.GROUP_BY<T>, Select.untyped.HAVING<T>, Select.untyped.UNION<T>, Select.untyped.JOIN<T>, Select.untyped.ADV_JOIN<T>, Select.untyped.ON<T>, Select.untyped.ORDER_BY<T>, Select.untyped.LIMIT<T>, Select.untyped.OFFSET<T>, Select.untyped.FOR<T>, Select.untyped.NOWAIT<T>, Select.untyped.SKIP_LOCKED<T>, Select.untyped.WHERE<T> {
      enum LockStrength {
        SHARE,
        UPDATE
      }

      enum LockOption {
        NOWAIT("NOWAIT"),
        SKIP_LOCKED("SKIP LOCKED");

        private final String name;

        private LockOption(final String name) {
          this.name = name;
        }

        @Override
        public String toString() {
          return name;
        }
      }

      enum JoinKind {
        INNER(""),
        CROSS(" CROSS"),
        NATURAL(" NATURAL"),
        LEFT(" LEFT OUTER"),
        RIGHT(" RIGHT OUTER"),
        FULL(" FULL OUTER");

        private final String keyword;

        private JoinKind(final String keyword) {
          this.keyword = keyword;
        }

        @Override
        public String toString() {
          return keyword;
        }
      }

      private boolean tableMutex;
      private type.Table table;

      final boolean distinct;
      final kind.Entity<?>[] entities;

      // FIXME: Does this need to be a Collection?
      private boolean fromMutex;
      private List<type.Table> from;

      List<Object> joins;

      List<Condition<?>> on;

      kind.Entity<?>[] groupBy;
      Condition<?> having;

      List<Object> unions;

      type.DataType<?>[] orderBy;
      int[] orderByIndexes;

      int limit = -1;
      int offset = -1;

      LockStrength forLockStrength;
      type.Entity<?>[] forSubjects;
      LockOption forLockOption;

      private boolean isObjectQuery;
      private boolean whereMutex;
      private Condition<?> where;

      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        if (entities.length < 1)
          throw new IllegalArgumentException("entities.length < 1");

        for (final kind.Entity<?> entity : entities)
          if (entity == null)
            throw new IllegalArgumentException("Argument to SELECT cannot be null (use type.?.NULL instead)");

        this.entities = entities;
        this.distinct = distinct;
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        this.from = Arrays.asList(from);
        fromMutex = true;
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        return JOIN(JoinKind.CROSS, table);
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        return JOIN(JoinKind.CROSS, select);
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        return JOIN(JoinKind.NATURAL, table);
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        return JOIN(JoinKind.NATURAL, select);
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        return JOIN(JoinKind.LEFT, table);
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        return JOIN(JoinKind.LEFT, select);
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        return JOIN(JoinKind.RIGHT, table);
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        return JOIN(JoinKind.RIGHT, select);
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        return JOIN(JoinKind.FULL, table);
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        return JOIN(JoinKind.FULL, select);
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        return JOIN(JoinKind.INNER, table);
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        return JOIN(JoinKind.INNER, select);
      }

      private SELECT<T> JOIN(final JoinKind kind, final Object join) {
        if (this.joins == null)
          this.joins = new ArrayList<>();

        this.joins.add(kind);
        this.joins.add(join);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        if (this.on == null)
          this.on = new ArrayList<>();

        // Since ON is optional, for each JOIN without ON, add a null to this.on
        for (int i = 0, joinsSize = this.joins.size(), onSize = this.on.size(); i < joinsSize / 2 - onSize - 1; ++i)
          this.on.add(null);

        this.on.add(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        this.groupBy = groupBy;
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        this.having = having;
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.untyped.SELECT<T> select) {
        UNION(Boolean.FALSE, select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.untyped.SELECT<T> select) {
        UNION(Boolean.TRUE, select);
        return this;
      }

      private SELECT<T> UNION(final Boolean all, final Select.untyped.SELECT<T> select) {
        if (this.unions == null)
          this.unions = new ArrayList<>();

        this.unions.add(all);
        this.unions.add(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        this.orderBy = columns;
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        this.orderByIndexes = columnNumbers;
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        this.limit = rows;
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        this.offset = rows;
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        return FOR(LockStrength.SHARE, subjects);
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        return FOR(LockStrength.UPDATE, subjects);
      }

      private SELECT<T> FOR(final LockStrength lockStrength, final type.Entity<?> ... subjects) {
        this.forLockStrength = lockStrength;
        this.forSubjects = subjects;
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        this.forLockOption = LockOption.NOWAIT;
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        this.forLockOption = LockOption.SKIP_LOCKED;
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        this.where = where;
        return this;
      }

      kind.Entity<?>[] getEntitiesWithOwners() {
        // FIXME: Do this via recursive array builder
        return Arrays.stream(entities).filter(entitiesWithOwnerPredicate).toArray(kind.Entity<?>[]::new);
      }

      @SuppressWarnings("unchecked")
      private RowIterator<T> execute(final Transaction transaction, final String dataSourceId, final QueryConfig config) throws IOException, SQLException {
        Connection connection = null;
        Statement statement = null;
        try {
          final Connection finalConnection = connection = transaction != null ? transaction.getConnection() : Schema.getConnection(schema(), dataSourceId, true);
          try (final Compilation compilation = new Compilation(this, DBVendor.valueOf(connection.getMetaData()), Registry.isPrepared(schema(), dataSourceId))) {
            compile(compilation, false);

            final Object[][] dataTypes = SelectImpl.compile(entities, 0, 0);

            final int columnOffset = compilation.skipFirstColumn() ? 2 : 1;
            final ResultSet resultSet = compilation.executeQuery(connection, config);
            final Statement finalStatement = statement = resultSet.getStatement();
            final int noColumns = resultSet.getMetaData().getColumnCount() + 1 - columnOffset;
            return new RowIterator<T>(resultSet, config) {
              private final HashMap<Class<? extends type.Table>,type.Table> prototypes = new HashMap<>();
              private final HashMap<type.Table,type.Table> cache = new HashMap<>();
              private type.Table currentTable;

              @Override
              @SuppressWarnings({"null", "rawtypes"})
              public boolean nextRow() throws SQLException {
                if (super.nextRow())
                  return true;

                if (endReached)
                  return false;

                final type.Entity<?>[] row;
                int index = 0;
                type.Table table;
                try {
                  if (endReached = !resultSet.next()) {
                    suppressed = Throwables.addSuppressed(suppressed, ResultSets.close(resultSet));
                    return false;
                  }

                  row = new type.Entity[entities.length];
                  table = null;
                  for (int i = 0; i < noColumns; ++i) {
                    final Object[] dataTypePrototype = dataTypes[i];
                    final type.DataType<?> prototypeDataType = (type.DataType<?>)dataTypePrototype[0];
                    final Integer prototypeIndex = (Integer)dataTypePrototype[1];
                    final type.DataType dataType;
                    if (currentTable != null && (currentTable != prototypeDataType.table || prototypeIndex == -1)) {
                      final type.Table cached = cache.get(table);
                      if (cached != null) {
                        row[index++] = cached;
                      }
                      else {
                        row[index++] = table;
                        cache.put(table, table);
                        prototypes.put(table.getClass(), table.newInstance());
                      }
                    }

                    if (prototypeIndex != -1) {
                      currentTable = prototypeDataType.table;
                      table = prototypes.get(currentTable.getClass());
                      if (table == null)
                        prototypes.put(currentTable.getClass(), table = currentTable.newInstance());

                      dataType = table._column$[prototypeIndex];
                    }
                    else {
                      table = null;
                      currentTable = null;
                      dataType = prototypeDataType.clone();
                      row[index++] = dataType;
                    }

                    dataType.set(resultSet, i + columnOffset);
                  }
                }
                catch (SQLException e) {
                  e = Throwables.addSuppressed(e, suppressed);
                  suppressed = null;
                  throw SQLExceptions.toStrongType(e);
                }

                if (table != null) {
                  final type.Table cached = cache.get(table);
                  row[index++] = cached != null ? cached : table;
                }

                rows.add((T[])row);
                ++rowIndex;
                resetEntities();
                prototypes.clear();
                currentTable = null;
                return true;
              }

              @Override
              public void close() throws SQLException {
                SQLException e = Throwables.addSuppressed(suppressed, ResultSets.close(resultSet));
                e = Throwables.addSuppressed(e, AuditStatement.close(finalStatement));
                if (transaction == null)
                  e = Throwables.addSuppressed(e, AuditConnection.close(finalConnection));

                prototypes.clear();
                cache.clear();
                currentTable = null;
                rows.clear();
                if (e != null)
                  throw SQLExceptions.toStrongType(e);
              }
            };
          }
        }
        catch (SQLException e) {
          if (statement != null)
            e = Throwables.addSuppressed(e, AuditStatement.close(statement));

          if (transaction == null && connection != null)
            e = Throwables.addSuppressed(e, AuditConnection.close(connection));

          throw SQLExceptions.toStrongType(e);
        }
      }

      @Override
      public final RowIterator<T> execute(final String dataSourceId) throws IOException, SQLException {
        return execute(null, dataSourceId, null);
      }

      @Override
      public final RowIterator<T> execute(final Transaction transaction) throws IOException, SQLException {
        return execute(transaction, transaction != null ? transaction.getDataSourceId() : null, null);
      }

      @Override
      public RowIterator<T> execute() throws IOException, SQLException {
        return execute(null, null, null);
      }

      @Override
      public final RowIterator<T> execute(final String dataSourceId, final QueryConfig config) throws IOException, SQLException {
        return execute(null, dataSourceId, config);
      }

      @Override
      public final RowIterator<T> execute(final Transaction transaction, final QueryConfig config) throws IOException, SQLException {
        return execute(transaction, transaction != null ? transaction.getDataSourceId() : null, config);
      }

      @Override
      public RowIterator<T> execute(final QueryConfig config) throws IOException, SQLException {
        return execute(null, null, config);
      }

      @Override
      final type.Table table() {
        if (tableMutex)
          return table;

        tableMutex = true;
        // FIXME: Note that this returns the 1st table only! Is this what we want?!
        if (entities[0] instanceof SelectImpl.untyped.SELECT)
          return table = ((SelectImpl.untyped.SELECT<?>)entities[0]).table();

        return from() != null ? table = from().get(0) : null;
      }

      // FIXME: What is translateTypes for? Looks unlinked to me!
      Map<Integer,type.ENUM<?>> translateTypes;

      List<type.Table> from() {
        if (fromMutex)
          return from;

        fromMutex = true;
        isObjectQuery = where == null;
        final List<type.Table> tables = new ArrayList<>();
        for (int i = 0; i < entities.length; ++i) {
          final Subject subject = (Subject)entities[i];
          isObjectQuery &= subject instanceof type.Table;
          final type.Table table = subject.table();
          if (table != null)
            tables.add(table);
        }

        if (tables.size() > 0)
          from = tables;

        return from;
      }

      Condition<?> where() {
        if (whereMutex)
          return where;

        whereMutex = true;
        if (isObjectQuery)
          where = createCondition(entities);

        return where;
      }

      private Condition<?> createCondition(final kind.Entity<?>[] entities) {
        final Condition<?>[] conditions = createConditions(entities, 0, 0);
        return conditions == null ? null : conditions.length == 1 ? conditions[0] : DML.AND(conditions);
      }

      private Condition<?>[] createConditions(final kind.Entity<?>[] entities, final int index, final int depth) {
        if (index == entities.length)
          return depth == 0 ? null : new Condition[depth];

        final kind.Entity<?> entity = entities[index];
        final Condition<?> condition;
        if (entity instanceof type.Table)
          condition = createCondition(((type.Table)entity)._column$);
        else if (entity instanceof SelectImpl.untyped.SELECT)
          condition = null;
        else
          throw new UnsupportedOperationException("Unsupported entity of object query: " + entity.getClass().getName());

        final Condition<?>[] conditions = createConditions(entities, index + 1, condition != null ? depth + 1 : depth);
        if (condition != null)
          conditions[depth] = condition;

        return conditions;
      }

      private Condition<?> createCondition(final type.DataType<?>[] columns) {
        final Condition<?>[] conditions = createConditions(columns, 0, 0);
        return conditions == null ? null : conditions.length == 1 ? conditions[0] : DML.AND(conditions);
      }

      private Condition<?>[] createConditions(final type.DataType<?>[] columns, final int index, final int depth) {
        if (index == columns.length)
          return depth == 0 ? null : new Condition[depth];

        final type.DataType<?> column = columns[index];
        final Condition<?>[] cinditions = createConditions(columns, index + 1, column.wasSet() ? depth + 1 : depth);
        if (column.wasSet())
          cinditions[depth] = DML.EQ(column, column.get());

        return cinditions;
      }

      @Override
      void compile(final Compilation compilation, final boolean isExpression) throws IOException, SQLException {
        final Compiler compiler = compilation.compiler;
        final boolean isForUpdate = forLockStrength != null && forSubjects != null && forSubjects.length > 0;
        final boolean useAliases = !isForUpdate || compiler.aliasInForUpdate();

        compiler.assignAliases(from(), joins, compilation);
        compiler.compileSelect(this, useAliases, compilation);
        compiler.compileFrom(this, useAliases, compilation);
        if (joins != null)
          for (int i = 0, j = 0, len = joins.size(); i < len; j = i / 2)
            compiler.compileJoin((JoinKind)joins.get(i++), joins.get(i++), on != null && j < on.size() ? on.get(j) : null, compilation);

        compiler.compileWhere(this, compilation);
        compiler.compileGroupByHaving(this, useAliases, compilation);
        compiler.compileUnion(this, compilation);
        compiler.compileOrderBy(this, compilation);
        compiler.compileLimitOffset(this, compilation);
        if (forLockStrength != null)
          compiler.compileFor(this, compilation);
      }
    }
  }

  public static class ARRAY {
    static class SELECT<T extends type.Entity<?>> extends untyped.SELECT<T> implements Select.ARRAY._SELECT<T>, Select.ARRAY.FROM<T>, Select.ARRAY.GROUP_BY<T>, Select.ARRAY.HAVING<T>, Select.ARRAY.UNION<T>, Select.ARRAY.JOIN<T>, Select.ARRAY.ADV_JOIN<T>, Select.ARRAY.ON<T>, Select.ARRAY.ORDER_BY<T>, Select.ARRAY.LIMIT<T>, Select.ARRAY.OFFSET<T>, Select.ARRAY.FOR<T>, Select.ARRAY.NOWAIT<T>, Select.ARRAY.SKIP_LOCKED<T>, Select.ARRAY.WHERE<T> {
      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        super(distinct, entities);
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        super.FROM(from);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        super.CROSS_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        super.CROSS_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        super.NATURAL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        super.NATURAL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        super.LEFT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        super.LEFT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        super.RIGHT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        super.RIGHT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        super.FULL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        super.FULL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        super.JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        super.JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        super.ON(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        super.GROUP_BY(groupBy);
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        super.HAVING(having);
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.ARRAY.SELECT<T> select) {
        super.UNION(select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.ARRAY.SELECT<T> select) {
        super.UNION_ALL(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        super.ORDER_BY(columns);
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        super.ORDER_BY(columnNumbers);
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        super.LIMIT(rows);
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        super.OFFSET(rows);
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        super.FOR_SHARE(subjects);
        return this;
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        super.FOR_UPDATE(subjects);
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        super.NOWAIT();
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        super.SKIP_LOCKED();
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        super.WHERE(where);
        return this;
      }
    }
  }

  public static class BIGINT {
    static class SELECT<T extends type.Entity<?>> extends untyped.SELECT<T> implements Select.BIGINT._SELECT<T>, Select.BIGINT.FROM<T>, Select.BIGINT.GROUP_BY<T>, Select.BIGINT.HAVING<T>, Select.BIGINT.UNION<T>, Select.BIGINT.JOIN<T>, Select.BIGINT.ADV_JOIN<T>, Select.BIGINT.ON<T>, Select.BIGINT.ORDER_BY<T>, Select.BIGINT.LIMIT<T>, Select.BIGINT.OFFSET<T>, Select.BIGINT.FOR<T>, Select.BIGINT.NOWAIT<T>, Select.BIGINT.SKIP_LOCKED<T>, Select.BIGINT.WHERE<T> {
      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        super(distinct, entities);
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        super.FROM(from);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        super.CROSS_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        super.CROSS_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        super.NATURAL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        super.NATURAL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        super.LEFT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        super.LEFT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        super.RIGHT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        super.RIGHT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        super.FULL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        super.FULL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        super.JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        super.JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        super.ON(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        super.GROUP_BY(groupBy);
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        super.HAVING(having);
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.BIGINT.SELECT<T> select) {
        super.UNION(select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.BIGINT.SELECT<T> select) {
        super.UNION_ALL(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        super.ORDER_BY(columns);
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        super.ORDER_BY(columnNumbers);
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        super.LIMIT(rows);
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        super.OFFSET(rows);
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        super.FOR_SHARE(subjects);
        return this;
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        super.FOR_UPDATE(subjects);
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        super.NOWAIT();
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        super.SKIP_LOCKED();
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        super.WHERE(where);
        return this;
      }
    }
  }

  public static class BINARY {
    static class SELECT<T extends type.Entity<?>> extends untyped.SELECT<T> implements Select.BINARY._SELECT<T>, Select.BINARY.FROM<T>, Select.BINARY.GROUP_BY<T>, Select.BINARY.HAVING<T>, Select.BINARY.UNION<T>, Select.BINARY.JOIN<T>, Select.BINARY.ADV_JOIN<T>, Select.BINARY.ON<T>, Select.BINARY.ORDER_BY<T>, Select.BINARY.LIMIT<T>, Select.BINARY.OFFSET<T>, Select.BINARY.FOR<T>, Select.BINARY.NOWAIT<T>, Select.BINARY.SKIP_LOCKED<T>, Select.BINARY.WHERE<T> {
      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        super(distinct, entities);
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        super.FROM(from);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        super.CROSS_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        super.CROSS_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        super.NATURAL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        super.NATURAL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        super.LEFT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        super.LEFT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        super.RIGHT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        super.RIGHT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        super.FULL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        super.FULL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        super.JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        super.JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        super.ON(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        super.GROUP_BY(groupBy);
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        super.HAVING(having);
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.BINARY.SELECT<T> select) {
        super.UNION(select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.BINARY.SELECT<T> select) {
        super.UNION_ALL(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        super.ORDER_BY(columns);
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        super.ORDER_BY(columnNumbers);
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        super.LIMIT(rows);
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        super.OFFSET(rows);
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        super.FOR_SHARE(subjects);
        return this;
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        super.FOR_UPDATE(subjects);
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        super.NOWAIT();
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        super.SKIP_LOCKED();
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        super.WHERE(where);
        return this;
      }
    }
  }

  public static class BLOB {
    static class SELECT<T extends type.Entity<?>> extends untyped.SELECT<T> implements Select.BLOB._SELECT<T>, Select.BLOB.FROM<T>, Select.BLOB.GROUP_BY<T>, Select.BLOB.HAVING<T>, Select.BLOB.UNION<T>, Select.BLOB.JOIN<T>, Select.BLOB.ADV_JOIN<T>, Select.BLOB.ON<T>, Select.BLOB.ORDER_BY<T>, Select.BLOB.LIMIT<T>, Select.BLOB.OFFSET<T>, Select.BLOB.FOR<T>, Select.BLOB.NOWAIT<T>, Select.BLOB.SKIP_LOCKED<T>, Select.BLOB.WHERE<T> {
      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        super(distinct, entities);
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        super.FROM(from);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        super.CROSS_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        super.CROSS_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        super.NATURAL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        super.NATURAL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        super.LEFT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        super.LEFT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        super.RIGHT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        super.RIGHT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        super.FULL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        super.FULL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        super.JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        super.JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        super.ON(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        super.GROUP_BY(groupBy);
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        super.HAVING(having);
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.BLOB.SELECT<T> select) {
        super.UNION(select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.BLOB.SELECT<T> select) {
        super.UNION_ALL(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        super.ORDER_BY(columns);
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        super.ORDER_BY(columnNumbers);
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        super.LIMIT(rows);
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        super.OFFSET(rows);
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        super.FOR_SHARE(subjects);
        return this;
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        super.FOR_UPDATE(subjects);
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        super.NOWAIT();
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        super.SKIP_LOCKED();
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        super.WHERE(where);
        return this;
      }
    }
  }

  public static class BOOLEAN {
    static class SELECT<T extends type.Entity<?>> extends untyped.SELECT<T> implements Select.BOOLEAN._SELECT<T>, Select.BOOLEAN.FROM<T>, Select.BOOLEAN.GROUP_BY<T>, Select.BOOLEAN.HAVING<T>, Select.BOOLEAN.UNION<T>, Select.BOOLEAN.JOIN<T>, Select.BOOLEAN.ADV_JOIN<T>, Select.BOOLEAN.ON<T>, Select.BOOLEAN.ORDER_BY<T>, Select.BOOLEAN.LIMIT<T>, Select.BOOLEAN.OFFSET<T>, Select.BOOLEAN.FOR<T>, Select.BOOLEAN.NOWAIT<T>, Select.BOOLEAN.SKIP_LOCKED<T>, Select.BOOLEAN.WHERE<T> {
      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        super(distinct, entities);
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        super.FROM(from);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        super.CROSS_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        super.CROSS_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        super.NATURAL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        super.NATURAL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        super.LEFT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        super.LEFT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        super.RIGHT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        super.RIGHT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        super.FULL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        super.FULL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        super.JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        super.JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        super.ON(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        super.GROUP_BY(groupBy);
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        super.HAVING(having);
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.BOOLEAN.SELECT<T> select) {
        super.UNION(select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.BOOLEAN.SELECT<T> select) {
        super.UNION_ALL(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        super.ORDER_BY(columns);
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        super.ORDER_BY(columnNumbers);
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        super.LIMIT(rows);
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        super.OFFSET(rows);
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        super.FOR_SHARE(subjects);
        return this;
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        super.FOR_UPDATE(subjects);
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        super.NOWAIT();
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        super.SKIP_LOCKED();
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        super.WHERE(where);
        return this;
      }
    }
  }

  public static class CHAR {
    static class SELECT<T extends type.Entity<?>> extends untyped.SELECT<T> implements Select.CHAR._SELECT<T>, Select.CHAR.FROM<T>, Select.CHAR.GROUP_BY<T>, Select.CHAR.HAVING<T>, Select.CHAR.UNION<T>, Select.CHAR.JOIN<T>, Select.CHAR.ADV_JOIN<T>, Select.CHAR.ON<T>, Select.CHAR.ORDER_BY<T>, Select.CHAR.LIMIT<T>, Select.CHAR.OFFSET<T>, Select.CHAR.FOR<T>, Select.CHAR.NOWAIT<T>, Select.CHAR.SKIP_LOCKED<T>, Select.CHAR.WHERE<T> {
      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        super(distinct, entities);
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        super.FROM(from);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        super.CROSS_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        super.CROSS_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        super.NATURAL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        super.NATURAL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        super.LEFT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        super.LEFT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        super.RIGHT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        super.RIGHT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        super.FULL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        super.FULL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        super.JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        super.JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        super.ON(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        super.GROUP_BY(groupBy);
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        super.HAVING(having);
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.CHAR.SELECT<T> select) {
        super.UNION(select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.CHAR.SELECT<T> select) {
        super.UNION_ALL(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        super.ORDER_BY(columns);
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        super.ORDER_BY(columnNumbers);
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        super.LIMIT(rows);
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        super.OFFSET(rows);
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        super.FOR_SHARE(subjects);
        return this;
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        super.FOR_UPDATE(subjects);
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        super.NOWAIT();
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        super.SKIP_LOCKED();
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        super.WHERE(where);
        return this;
      }
    }
  }

  public static class CLOB {
    static class SELECT<T extends type.Entity<?>> extends untyped.SELECT<T> implements Select.CLOB._SELECT<T>, Select.CLOB.FROM<T>, Select.CLOB.GROUP_BY<T>, Select.CLOB.HAVING<T>, Select.CLOB.UNION<T>, Select.CLOB.JOIN<T>, Select.CLOB.ADV_JOIN<T>, Select.CLOB.ON<T>, Select.CLOB.ORDER_BY<T>, Select.CLOB.LIMIT<T>, Select.CLOB.OFFSET<T>, Select.CLOB.FOR<T>, Select.CLOB.NOWAIT<T>, Select.CLOB.SKIP_LOCKED<T>, Select.CLOB.WHERE<T> {
      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        super(distinct, entities);
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        super.FROM(from);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        super.CROSS_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        super.CROSS_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        super.NATURAL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        super.NATURAL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        super.LEFT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        super.LEFT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        super.RIGHT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        super.RIGHT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        super.FULL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        super.FULL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        super.JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        super.JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        super.ON(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        super.GROUP_BY(groupBy);
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        super.HAVING(having);
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.CLOB.SELECT<T> select) {
        super.UNION(select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.CLOB.SELECT<T> select) {
        super.UNION_ALL(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        super.ORDER_BY(columns);
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        super.ORDER_BY(columnNumbers);
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        super.LIMIT(rows);
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        super.OFFSET(rows);
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        super.FOR_SHARE(subjects);
        return this;
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        super.FOR_UPDATE(subjects);
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        super.NOWAIT();
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        super.SKIP_LOCKED();
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        super.WHERE(where);
        return this;
      }
    }
  }

  public static class DataType {
    static class SELECT<T extends type.Entity<?>> extends untyped.SELECT<T> implements Select.DataType._SELECT<T>, Select.DataType.FROM<T>, Select.DataType.GROUP_BY<T>, Select.DataType.HAVING<T>, Select.DataType.UNION<T>, Select.DataType.JOIN<T>, Select.DataType.ADV_JOIN<T>, Select.DataType.ON<T>, Select.DataType.ORDER_BY<T>, Select.DataType.LIMIT<T>, Select.DataType.OFFSET<T>, Select.DataType.FOR<T>, Select.DataType.NOWAIT<T>, Select.DataType.SKIP_LOCKED<T>, Select.DataType.WHERE<T> {
      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        super(distinct, entities);
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        super.FROM(from);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        super.CROSS_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        super.CROSS_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        super.NATURAL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        super.NATURAL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        super.LEFT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        super.LEFT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        super.RIGHT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        super.RIGHT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        super.FULL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        super.FULL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        super.JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        super.JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        super.ON(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        super.GROUP_BY(groupBy);
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        super.HAVING(having);
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.DataType.SELECT<T> select) {
        super.UNION(select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.DataType.SELECT<T> select) {
        super.UNION_ALL(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        super.ORDER_BY(columns);
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        super.ORDER_BY(columnNumbers);
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        super.LIMIT(rows);
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        super.OFFSET(rows);
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        super.FOR_SHARE(subjects);
        return this;
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        super.FOR_UPDATE(subjects);
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        super.NOWAIT();
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        super.SKIP_LOCKED();
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        super.WHERE(where);
        return this;
      }
    }
  }

  public static class DATE {
    static class SELECT<T extends type.Entity<?>> extends untyped.SELECT<T> implements Select.DATE._SELECT<T>, Select.DATE.FROM<T>, Select.DATE.GROUP_BY<T>, Select.DATE.HAVING<T>, Select.DATE.UNION<T>, Select.DATE.JOIN<T>, Select.DATE.ADV_JOIN<T>, Select.DATE.ON<T>, Select.DATE.ORDER_BY<T>, Select.DATE.LIMIT<T>, Select.DATE.OFFSET<T>, Select.DATE.FOR<T>, Select.DATE.NOWAIT<T>, Select.DATE.SKIP_LOCKED<T>, Select.DATE.WHERE<T> {
      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        super(distinct, entities);
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        super.FROM(from);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        super.CROSS_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        super.CROSS_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        super.NATURAL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        super.NATURAL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        super.LEFT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        super.LEFT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        super.RIGHT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        super.RIGHT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        super.FULL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        super.FULL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        super.JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        super.JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        super.ON(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        super.GROUP_BY(groupBy);
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        super.HAVING(having);
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.DATE.SELECT<T> select) {
        super.UNION(select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.DATE.SELECT<T> select) {
        super.UNION_ALL(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        super.ORDER_BY(columns);
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        super.ORDER_BY(columnNumbers);
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        super.LIMIT(rows);
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        super.OFFSET(rows);
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        super.FOR_SHARE(subjects);
        return this;
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        super.FOR_UPDATE(subjects);
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        super.NOWAIT();
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        super.SKIP_LOCKED();
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        super.WHERE(where);
        return this;
      }
    }
  }

  public static class DATETIME {
    static class SELECT<T extends type.Entity<?>> extends untyped.SELECT<T> implements Select.DATETIME._SELECT<T>, Select.DATETIME.FROM<T>, Select.DATETIME.GROUP_BY<T>, Select.DATETIME.HAVING<T>, Select.DATETIME.UNION<T>, Select.DATETIME.JOIN<T>, Select.DATETIME.ADV_JOIN<T>, Select.DATETIME.ON<T>, Select.DATETIME.ORDER_BY<T>, Select.DATETIME.LIMIT<T>, Select.DATETIME.OFFSET<T>, Select.DATETIME.FOR<T>, Select.DATETIME.NOWAIT<T>, Select.DATETIME.SKIP_LOCKED<T>, Select.DATETIME.WHERE<T> {
      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        super(distinct, entities);
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        super.FROM(from);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        super.CROSS_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        super.CROSS_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        super.NATURAL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        super.NATURAL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        super.LEFT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        super.LEFT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        super.RIGHT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        super.RIGHT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        super.FULL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        super.FULL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        super.JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        super.JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        super.ON(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        super.GROUP_BY(groupBy);
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        super.HAVING(having);
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.DATETIME.SELECT<T> select) {
        super.UNION(select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.DATETIME.SELECT<T> select) {
        super.UNION_ALL(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        super.ORDER_BY(columns);
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        super.ORDER_BY(columnNumbers);
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        super.LIMIT(rows);
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        super.OFFSET(rows);
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        super.FOR_SHARE(subjects);
        return this;
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        super.FOR_UPDATE(subjects);
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        super.NOWAIT();
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        super.SKIP_LOCKED();
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        super.WHERE(where);
        return this;
      }
    }
  }

  public static class DECIMAL {
    static class SELECT<T extends type.Entity<?>> extends untyped.SELECT<T> implements Select.DECIMAL._SELECT<T>, Select.DECIMAL.FROM<T>, Select.DECIMAL.GROUP_BY<T>, Select.DECIMAL.HAVING<T>, Select.DECIMAL.UNION<T>, Select.DECIMAL.JOIN<T>, Select.DECIMAL.ADV_JOIN<T>, Select.DECIMAL.ON<T>, Select.DECIMAL.ORDER_BY<T>, Select.DECIMAL.LIMIT<T>, Select.DECIMAL.OFFSET<T>, Select.DECIMAL.FOR<T>, Select.DECIMAL.NOWAIT<T>, Select.DECIMAL.SKIP_LOCKED<T>, Select.DECIMAL.WHERE<T> {
      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        super(distinct, entities);
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        super.FROM(from);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        super.CROSS_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        super.CROSS_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        super.NATURAL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        super.NATURAL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        super.LEFT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        super.LEFT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        super.RIGHT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        super.RIGHT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        super.FULL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        super.FULL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        super.JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        super.JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        super.ON(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        super.GROUP_BY(groupBy);
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        super.HAVING(having);
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.DECIMAL.SELECT<T> select) {
        super.UNION(select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.DECIMAL.SELECT<T> select) {
        super.UNION_ALL(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        super.ORDER_BY(columns);
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        super.ORDER_BY(columnNumbers);
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        super.LIMIT(rows);
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        super.OFFSET(rows);
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        super.FOR_SHARE(subjects);
        return this;
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        super.FOR_UPDATE(subjects);
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        super.NOWAIT();
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        super.SKIP_LOCKED();
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        super.WHERE(where);
        return this;
      }
    }
  }

  public static class DOUBLE {
    static class SELECT<T extends type.Entity<?>> extends untyped.SELECT<T> implements Select.DOUBLE._SELECT<T>, Select.DOUBLE.FROM<T>, Select.DOUBLE.GROUP_BY<T>, Select.DOUBLE.HAVING<T>, Select.DOUBLE.UNION<T>, Select.DOUBLE.JOIN<T>, Select.DOUBLE.ADV_JOIN<T>, Select.DOUBLE.ON<T>, Select.DOUBLE.ORDER_BY<T>, Select.DOUBLE.LIMIT<T>, Select.DOUBLE.OFFSET<T>, Select.DOUBLE.FOR<T>, Select.DOUBLE.NOWAIT<T>, Select.DOUBLE.SKIP_LOCKED<T>, Select.DOUBLE.WHERE<T> {
      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        super(distinct, entities);
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        super.FROM(from);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        super.CROSS_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        super.CROSS_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        super.NATURAL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        super.NATURAL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        super.LEFT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        super.LEFT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        super.RIGHT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        super.RIGHT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        super.FULL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        super.FULL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        super.JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        super.JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        super.ON(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        super.GROUP_BY(groupBy);
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        super.HAVING(having);
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.DOUBLE.SELECT<T> select) {
        super.UNION(select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.DOUBLE.SELECT<T> select) {
        super.UNION_ALL(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        super.ORDER_BY(columns);
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        super.ORDER_BY(columnNumbers);
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        super.LIMIT(rows);
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        super.OFFSET(rows);
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        super.FOR_SHARE(subjects);
        return this;
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        super.FOR_UPDATE(subjects);
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        super.NOWAIT();
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        super.SKIP_LOCKED();
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        super.WHERE(where);
        return this;
      }
    }
  }

  public static class Entity {
    static class SELECT<T extends type.Entity<?>> extends untyped.SELECT<T> implements Select.Entity._SELECT<T>, Select.Entity.FROM<T>, Select.Entity.GROUP_BY<T>, Select.Entity.HAVING<T>, Select.Entity.UNION<T>, Select.Entity.JOIN<T>, Select.Entity.ADV_JOIN<T>, Select.Entity.ON<T>, Select.Entity.ORDER_BY<T>, Select.Entity.LIMIT<T>, Select.Entity.OFFSET<T>, Select.Entity.FOR<T>, Select.Entity.NOWAIT<T>, Select.Entity.SKIP_LOCKED<T>, Select.Entity.WHERE<T> {
      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        super(distinct, entities);
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        super.FROM(from);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        super.CROSS_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        super.CROSS_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        super.NATURAL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        super.NATURAL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        super.LEFT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        super.LEFT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        super.RIGHT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        super.RIGHT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        super.FULL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        super.FULL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        super.JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        super.JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        super.ON(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        super.GROUP_BY(groupBy);
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        super.HAVING(having);
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.Entity.SELECT<T> select) {
        super.UNION(select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.Entity.SELECT<T> select) {
        super.UNION_ALL(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        super.ORDER_BY(columns);
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        super.ORDER_BY(columnNumbers);
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        super.LIMIT(rows);
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        super.OFFSET(rows);
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        super.FOR_SHARE(subjects);
        return this;
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        super.FOR_UPDATE(subjects);
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        super.NOWAIT();
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        super.SKIP_LOCKED();
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        super.WHERE(where);
        return this;
      }
    }
  }

  public static class ENUM {
    static class SELECT<T extends type.Entity<?>> extends untyped.SELECT<T> implements Select.ENUM._SELECT<T>, Select.ENUM.FROM<T>, Select.ENUM.GROUP_BY<T>, Select.ENUM.HAVING<T>, Select.ENUM.UNION<T>, Select.ENUM.JOIN<T>, Select.ENUM.ADV_JOIN<T>, Select.ENUM.ON<T>, Select.ENUM.ORDER_BY<T>, Select.ENUM.LIMIT<T>, Select.ENUM.OFFSET<T>, Select.ENUM.FOR<T>, Select.ENUM.NOWAIT<T>, Select.ENUM.SKIP_LOCKED<T>, Select.ENUM.WHERE<T> {
      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        super(distinct, entities);
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        super.FROM(from);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        super.CROSS_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        super.CROSS_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        super.NATURAL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        super.NATURAL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        super.LEFT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        super.LEFT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        super.RIGHT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        super.RIGHT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        super.FULL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        super.FULL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        super.JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        super.JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        super.ON(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        super.GROUP_BY(groupBy);
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        super.HAVING(having);
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.ENUM.SELECT<T> select) {
        super.UNION(select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.ENUM.SELECT<T> select) {
        super.UNION_ALL(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        super.ORDER_BY(columns);
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        super.ORDER_BY(columnNumbers);
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        super.LIMIT(rows);
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        super.OFFSET(rows);
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        super.FOR_SHARE(subjects);
        return this;
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        super.FOR_UPDATE(subjects);
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        super.NOWAIT();
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        super.SKIP_LOCKED();
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        super.WHERE(where);
        return this;
      }
    }
  }

  public static class FLOAT {
    static class SELECT<T extends type.Entity<?>> extends untyped.SELECT<T> implements Select.FLOAT._SELECT<T>, Select.FLOAT.FROM<T>, Select.FLOAT.GROUP_BY<T>, Select.FLOAT.HAVING<T>, Select.FLOAT.UNION<T>, Select.FLOAT.JOIN<T>, Select.FLOAT.ADV_JOIN<T>, Select.FLOAT.ON<T>, Select.FLOAT.ORDER_BY<T>, Select.FLOAT.LIMIT<T>, Select.FLOAT.OFFSET<T>, Select.FLOAT.FOR<T>, Select.FLOAT.NOWAIT<T>, Select.FLOAT.SKIP_LOCKED<T>, Select.FLOAT.WHERE<T> {
      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        super(distinct, entities);
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        super.FROM(from);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        super.CROSS_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        super.CROSS_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        super.NATURAL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        super.NATURAL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        super.LEFT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        super.LEFT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        super.RIGHT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        super.RIGHT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        super.FULL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        super.FULL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        super.JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        super.JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        super.ON(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        super.GROUP_BY(groupBy);
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        super.HAVING(having);
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.FLOAT.SELECT<T> select) {
        super.UNION(select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.FLOAT.SELECT<T> select) {
        super.UNION_ALL(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        super.ORDER_BY(columns);
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        super.ORDER_BY(columnNumbers);
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        super.LIMIT(rows);
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        super.OFFSET(rows);
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        super.FOR_SHARE(subjects);
        return this;
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        super.FOR_UPDATE(subjects);
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        super.NOWAIT();
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        super.SKIP_LOCKED();
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        super.WHERE(where);
        return this;
      }
    }
  }

  public static class INT {
    static class SELECT<T extends type.Entity<?>> extends untyped.SELECT<T> implements Select.INT._SELECT<T>, Select.INT.FROM<T>, Select.INT.GROUP_BY<T>, Select.INT.HAVING<T>, Select.INT.UNION<T>, Select.INT.JOIN<T>, Select.INT.ADV_JOIN<T>, Select.INT.ON<T>, Select.INT.ORDER_BY<T>, Select.INT.LIMIT<T>, Select.INT.OFFSET<T>, Select.INT.FOR<T>, Select.INT.NOWAIT<T>, Select.INT.SKIP_LOCKED<T>, Select.INT.WHERE<T> {
      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        super(distinct, entities);
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        super.FROM(from);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        super.CROSS_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        super.CROSS_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        super.NATURAL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        super.NATURAL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        super.LEFT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        super.LEFT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        super.RIGHT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        super.RIGHT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        super.FULL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        super.FULL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        super.JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        super.JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        super.ON(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        super.GROUP_BY(groupBy);
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        super.HAVING(having);
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.INT.SELECT<T> select) {
        super.UNION(select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.INT.SELECT<T> select) {
        super.UNION_ALL(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        super.ORDER_BY(columns);
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        super.ORDER_BY(columnNumbers);
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        super.LIMIT(rows);
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        super.OFFSET(rows);
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        super.FOR_SHARE(subjects);
        return this;
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        super.FOR_UPDATE(subjects);
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        super.NOWAIT();
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        super.SKIP_LOCKED();
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        super.WHERE(where);
        return this;
      }
    }
  }

  public static class LargeObject {
    static class SELECT<T extends type.Entity<?>> extends untyped.SELECT<T> implements Select.LargeObject._SELECT<T>, Select.LargeObject.FROM<T>, Select.LargeObject.GROUP_BY<T>, Select.LargeObject.HAVING<T>, Select.LargeObject.UNION<T>, Select.LargeObject.JOIN<T>, Select.LargeObject.ADV_JOIN<T>, Select.LargeObject.ON<T>, Select.LargeObject.ORDER_BY<T>, Select.LargeObject.LIMIT<T>, Select.LargeObject.OFFSET<T>, Select.LargeObject.FOR<T>, Select.LargeObject.NOWAIT<T>, Select.LargeObject.SKIP_LOCKED<T>, Select.LargeObject.WHERE<T> {
      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        super(distinct, entities);
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        super.FROM(from);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        super.CROSS_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        super.CROSS_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        super.NATURAL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        super.NATURAL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        super.LEFT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        super.LEFT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        super.RIGHT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        super.RIGHT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        super.FULL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        super.FULL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        super.JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        super.JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        super.ON(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        super.GROUP_BY(groupBy);
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        super.HAVING(having);
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.LargeObject.SELECT<T> select) {
        super.UNION(select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.LargeObject.SELECT<T> select) {
        super.UNION_ALL(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        super.ORDER_BY(columns);
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        super.ORDER_BY(columnNumbers);
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        super.LIMIT(rows);
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        super.OFFSET(rows);
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        super.FOR_SHARE(subjects);
        return this;
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        super.FOR_UPDATE(subjects);
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        super.NOWAIT();
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        super.SKIP_LOCKED();
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        super.WHERE(where);
        return this;
      }
    }
  }

  public static class Numeric {
    static class SELECT<T extends type.Entity<?>> extends untyped.SELECT<T> implements Select.Numeric._SELECT<T>, Select.Numeric.FROM<T>, Select.Numeric.GROUP_BY<T>, Select.Numeric.HAVING<T>, Select.Numeric.UNION<T>, Select.Numeric.JOIN<T>, Select.Numeric.ADV_JOIN<T>, Select.Numeric.ON<T>, Select.Numeric.ORDER_BY<T>, Select.Numeric.LIMIT<T>, Select.Numeric.OFFSET<T>, Select.Numeric.FOR<T>, Select.Numeric.NOWAIT<T>, Select.Numeric.SKIP_LOCKED<T>, Select.Numeric.WHERE<T> {
      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        super(distinct, entities);
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        super.FROM(from);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        super.CROSS_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        super.CROSS_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        super.NATURAL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        super.NATURAL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        super.LEFT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        super.LEFT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        super.RIGHT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        super.RIGHT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        super.FULL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        super.FULL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        super.JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        super.JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        super.ON(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        super.GROUP_BY(groupBy);
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        super.HAVING(having);
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.Numeric.SELECT<T> select) {
        super.UNION(select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.Numeric.SELECT<T> select) {
        super.UNION_ALL(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        super.ORDER_BY(columns);
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        super.ORDER_BY(columnNumbers);
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        super.LIMIT(rows);
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        super.OFFSET(rows);
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        super.FOR_SHARE(subjects);
        return this;
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        super.FOR_UPDATE(subjects);
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        super.NOWAIT();
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        super.SKIP_LOCKED();
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        super.WHERE(where);
        return this;
      }
    }
  }

  public static class SMALLINT {
    static class SELECT<T extends type.Entity<?>> extends untyped.SELECT<T> implements Select.SMALLINT._SELECT<T>, Select.SMALLINT.FROM<T>, Select.SMALLINT.GROUP_BY<T>, Select.SMALLINT.HAVING<T>, Select.SMALLINT.UNION<T>, Select.SMALLINT.JOIN<T>, Select.SMALLINT.ADV_JOIN<T>, Select.SMALLINT.ON<T>, Select.SMALLINT.ORDER_BY<T>, Select.SMALLINT.LIMIT<T>, Select.SMALLINT.OFFSET<T>, Select.SMALLINT.FOR<T>, Select.SMALLINT.NOWAIT<T>, Select.SMALLINT.SKIP_LOCKED<T>, Select.SMALLINT.WHERE<T> {
      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        super(distinct, entities);
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        super.FROM(from);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        super.CROSS_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        super.CROSS_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        super.NATURAL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        super.NATURAL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        super.LEFT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        super.LEFT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        super.RIGHT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        super.RIGHT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        super.FULL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        super.FULL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        super.JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        super.JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        super.ON(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        super.GROUP_BY(groupBy);
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        super.HAVING(having);
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.SMALLINT.SELECT<T> select) {
        super.UNION(select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.SMALLINT.SELECT<T> select) {
        super.UNION_ALL(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        super.ORDER_BY(columns);
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        super.ORDER_BY(columnNumbers);
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        super.LIMIT(rows);
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        super.OFFSET(rows);
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        super.FOR_SHARE(subjects);
        return this;
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        super.FOR_UPDATE(subjects);
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        super.NOWAIT();
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        super.SKIP_LOCKED();
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        super.WHERE(where);
        return this;
      }
    }
  }

  public static class Temporal {
    static class SELECT<T extends type.Entity<?>> extends untyped.SELECT<T> implements Select.Temporal._SELECT<T>, Select.Temporal.FROM<T>, Select.Temporal.GROUP_BY<T>, Select.Temporal.HAVING<T>, Select.Temporal.UNION<T>, Select.Temporal.JOIN<T>, Select.Temporal.ADV_JOIN<T>, Select.Temporal.ON<T>, Select.Temporal.ORDER_BY<T>, Select.Temporal.LIMIT<T>, Select.Temporal.OFFSET<T>, Select.Temporal.FOR<T>, Select.Temporal.NOWAIT<T>, Select.Temporal.SKIP_LOCKED<T>, Select.Temporal.WHERE<T> {
      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        super(distinct, entities);
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        super.FROM(from);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        super.CROSS_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        super.CROSS_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        super.NATURAL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        super.NATURAL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        super.LEFT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        super.LEFT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        super.RIGHT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        super.RIGHT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        super.FULL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        super.FULL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        super.JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        super.JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        super.ON(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        super.GROUP_BY(groupBy);
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        super.HAVING(having);
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.Temporal.SELECT<T> select) {
        super.UNION(select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.Temporal.SELECT<T> select) {
        super.UNION_ALL(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        super.ORDER_BY(columns);
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        super.ORDER_BY(columnNumbers);
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        super.LIMIT(rows);
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        super.OFFSET(rows);
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        super.FOR_SHARE(subjects);
        return this;
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        super.FOR_UPDATE(subjects);
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        super.NOWAIT();
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        super.SKIP_LOCKED();
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        super.WHERE(where);
        return this;
      }
    }
  }

  public static class Textual {
    static class SELECT<T extends type.Entity<?>> extends untyped.SELECT<T> implements Select.Textual._SELECT<T>, Select.Textual.FROM<T>, Select.Textual.GROUP_BY<T>, Select.Textual.HAVING<T>, Select.Textual.UNION<T>, Select.Textual.JOIN<T>, Select.Textual.ADV_JOIN<T>, Select.Textual.ON<T>, Select.Textual.ORDER_BY<T>, Select.Textual.LIMIT<T>, Select.Textual.OFFSET<T>, Select.Textual.FOR<T>, Select.Textual.NOWAIT<T>, Select.Textual.SKIP_LOCKED<T>, Select.Textual.WHERE<T> {
      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        super(distinct, entities);
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        super.FROM(from);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        super.CROSS_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        super.CROSS_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        super.NATURAL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        super.NATURAL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        super.LEFT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        super.LEFT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        super.RIGHT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        super.RIGHT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        super.FULL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        super.FULL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        super.JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        super.JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        super.ON(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        super.GROUP_BY(groupBy);
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        super.HAVING(having);
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.Textual.SELECT<T> select) {
        super.UNION(select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.Textual.SELECT<T> select) {
        super.UNION_ALL(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        super.ORDER_BY(columns);
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        super.ORDER_BY(columnNumbers);
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        super.LIMIT(rows);
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        super.OFFSET(rows);
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        super.FOR_SHARE(subjects);
        return this;
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        super.FOR_UPDATE(subjects);
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        super.NOWAIT();
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        super.SKIP_LOCKED();
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        super.WHERE(where);
        return this;
      }
    }
  }

  public static class TIME {
    static class SELECT<T extends type.Entity<?>> extends untyped.SELECT<T> implements Select.TIME._SELECT<T>, Select.TIME.FROM<T>, Select.TIME.GROUP_BY<T>, Select.TIME.HAVING<T>, Select.TIME.UNION<T>, Select.TIME.JOIN<T>, Select.TIME.ADV_JOIN<T>, Select.TIME.ON<T>, Select.TIME.ORDER_BY<T>, Select.TIME.LIMIT<T>, Select.TIME.OFFSET<T>, Select.TIME.FOR<T>, Select.TIME.NOWAIT<T>, Select.TIME.SKIP_LOCKED<T>, Select.TIME.WHERE<T> {
      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        super(distinct, entities);
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        super.FROM(from);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        super.CROSS_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        super.CROSS_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        super.NATURAL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        super.NATURAL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        super.LEFT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        super.LEFT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        super.RIGHT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        super.RIGHT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        super.FULL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        super.FULL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        super.JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        super.JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        super.ON(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        super.GROUP_BY(groupBy);
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        super.HAVING(having);
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.TIME.SELECT<T> select) {
        super.UNION(select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.TIME.SELECT<T> select) {
        super.UNION_ALL(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        super.ORDER_BY(columns);
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        super.ORDER_BY(columnNumbers);
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        super.LIMIT(rows);
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        super.OFFSET(rows);
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        super.FOR_SHARE(subjects);
        return this;
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        super.FOR_UPDATE(subjects);
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        super.NOWAIT();
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        super.SKIP_LOCKED();
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        super.WHERE(where);
        return this;
      }
    }
  }

  public static class TINYINT {
    static class SELECT<T extends type.Entity<?>> extends untyped.SELECT<T> implements Select.TINYINT._SELECT<T>, Select.TINYINT.FROM<T>, Select.TINYINT.GROUP_BY<T>, Select.TINYINT.HAVING<T>, Select.TINYINT.UNION<T>, Select.TINYINT.JOIN<T>, Select.TINYINT.ADV_JOIN<T>, Select.TINYINT.ON<T>, Select.TINYINT.ORDER_BY<T>, Select.TINYINT.LIMIT<T>, Select.TINYINT.OFFSET<T>, Select.TINYINT.FOR<T>, Select.TINYINT.NOWAIT<T>, Select.TINYINT.SKIP_LOCKED<T>, Select.TINYINT.WHERE<T> {
      SELECT(final boolean distinct, final kind.Entity<?>[] entities) {
        super(distinct, entities);
      }

      @Override
      public T AS(final T as) {
        as.wrapper(new As<T>(this, as, true));
        return as;
      }

      @Override
      public SELECT<T> FROM(final type.Table ... from) {
        super.FROM(from);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final type.Table table) {
        super.CROSS_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> CROSS_JOIN(final Select.untyped.SELECT<?> select) {
        super.CROSS_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final type.Table table) {
        super.NATURAL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> NATURAL_JOIN(final Select.untyped.SELECT<?> select) {
        super.NATURAL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final type.Table table) {
        super.LEFT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> LEFT_JOIN(final Select.untyped.SELECT<?> select) {
        super.LEFT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final type.Table table) {
        super.RIGHT_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> RIGHT_JOIN(final Select.untyped.SELECT<?> select) {
        super.RIGHT_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final type.Table table) {
        super.FULL_JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> FULL_JOIN(final Select.untyped.SELECT<?> select) {
        super.FULL_JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final type.Table table) {
        super.JOIN(table);
        return this;
      }

      @Override
      public SELECT<T> JOIN(final Select.untyped.SELECT<?> select) {
        super.JOIN(select);
        return this;
      }

      @Override
      public SELECT<T> ON(final Condition<?> on) {
        super.ON(on);
        return this;
      }

      @Override
      public SELECT<T> GROUP_BY(final kind.Entity<?> ... groupBy) {
        super.GROUP_BY(groupBy);
        return this;
      }

      @Override
      public SELECT<T> HAVING(final Condition<?> having) {
        super.HAVING(having);
        return this;
      }

      @Override
      public SELECT<T> UNION(final Select.TINYINT.SELECT<T> select) {
        super.UNION(select);
        return this;
      }

      @Override
      public SELECT<T> UNION_ALL(final Select.TINYINT.SELECT<T> select) {
        super.UNION_ALL(select);
        return this;
      }

      @Override
      public SELECT<T> ORDER_BY(final type.DataType<?> ... columns) {
        super.ORDER_BY(columns);
        return this;
      }

      private SELECT<T> ORDER_BY(final int ... columnNumbers) {
        super.ORDER_BY(columnNumbers);
        return this;
      }

      @Override
      public SELECT<T> LIMIT(final int rows) {
        super.LIMIT(rows);
        return this;
      }

      @Override
      public SELECT<T> OFFSET(final int rows) {
        super.OFFSET(rows);
        return this;
      }

      @Override
      public SELECT<T> FOR_SHARE(final type.Entity<?> ... subjects) {
        super.FOR_SHARE(subjects);
        return this;
      }

      @Override
      public SELECT<T> FOR_UPDATE(final type.Entity<?> ... subjects) {
        super.FOR_UPDATE(subjects);
        return this;
      }

      @Override
      public SELECT<T> NOWAIT() {
        super.NOWAIT();
        return this;
      }

      @Override
      public SELECT<T> SKIP_LOCKED() {
        super.SKIP_LOCKED();
        return this;
      }

      @Override
      public SELECT<T> WHERE(final Condition<?> where) {
        super.WHERE(where);
        return this;
      }
    }
  }

  private SelectImpl() {
  }
}