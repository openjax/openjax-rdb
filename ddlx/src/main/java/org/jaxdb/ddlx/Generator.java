/* Copyright (c) 2011 JAX-DB
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

package org.jaxdb.ddlx;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.jaxdb.vendor.DBVendor;
import org.jaxdb.www.ddlx_0_4.xLygluGCXAA.$Column;
import org.jaxdb.www.ddlx_0_4.xLygluGCXAA.$Integer;
import org.jaxdb.www.ddlx_0_4.xLygluGCXAA.$Table;
import org.jaxdb.www.ddlx_0_4.xLygluGCXAA.Schema;
import org.jaxsb.runtime.Bindings;
import org.libj.lang.PackageLoader;
import org.libj.lang.PackageNotFoundException;
import org.libj.util.ArrayUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

public final class Generator {
  static final Logger logger = LoggerFactory.getLogger(Generator.class);

  static {
    try {
      PackageLoader.getContextPackageLoader().loadPackage(Schema.class.getPackage().getName());
    }
    catch (final IOException | PackageNotFoundException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static void trapPrintUsage() {
    final String vendors = ArrayUtil.toString(DBVendor.values(), "|");
    System.err.println("Usage: Generator <-d DEST_DIR> <-v VENDOR> <DDLx_FILE>");
    System.err.println();
    System.err.println("Mandatory arguments:");
    System.err.println("  -v <VENDOR>        One of: <" + vendors + ">");
    System.err.println("  -d <DEST_DIR>      Specify the destination directory.");
    System.exit(1);
  }

  public static void main(final String[] args) throws GeneratorExecutionException, IOException, SAXException {
    if (args.length != 5)
      trapPrintUsage();

    DBVendor vendor = null;
    File destDir = null;
    URL schemaUrl = null;
    String sqlFileName = null;
    for (int i = 0; i < args.length; ++i) {
      if ("-v".equals(args[i]))
        vendor = DBVendor.valueOf(args[++i]);
      else if ("-d".equals(args[i]))
        destDir = new File(args[++i]).getAbsoluteFile();
      else {
        final File schemaFile = new File(args[i]);
        sqlFileName = schemaFile.getName().substring(0, schemaFile.getName().lastIndexOf('.') + 1) + "sql";
        schemaUrl = schemaFile.getAbsoluteFile().toURI().toURL();
      }
    }

    if (vendor == null || destDir == null || schemaUrl == null) {
      trapPrintUsage();
    }
    else {
      final StatementBatch statementBatch = createDDL((Schema)Bindings.parse(schemaUrl), vendor);
      destDir.mkdirs();
      statementBatch.writeOutput(new File(destDir, sqlFileName));
    }
  }

  public static StatementBatch createDDL(final Schema schema, final DBVendor vendor) throws GeneratorExecutionException {
    return new StatementBatch(new Generator(new DDLxAudit(schema)).parse(vendor));
  }

  private static String checkNameViolation(String string) {
    string = string.toUpperCase();

    final SQLStandard[] enums = ReservedWords.get(string);
    if (enums == null)
      return null;

    final StringBuilder message = new StringBuilder("The name '").append(string).append("' is reserved word in ").append(enums[0]);

    for (int i = 1; i < enums.length; ++i)
      message.append(", ").append(enums[i]);

    message.append('.');
    return message.toString();
  }

  final DDLxAudit audit;
  final Schema schema;

  Generator(final DDLxAudit audit) {
    this.audit = audit;
    this.schema = Schemas.flatten(audit.schema());

    final List<String> errors = getErrors();
    if (errors.size() > 0)
      for (final String error : errors)
        logger.warn(error);
  }

  private List<String> getErrors() {
    final List<String> errors = new ArrayList<>();
    for (final $Table table : schema.getTable()) {
      if (!table.getAbstract$().text()) {
        if (table.getConstraints() == null || table.getConstraints().getPrimaryKey() == null) {
          errors.add("Table `" + table.getName$().text() + "` does not have a primary key.");
        }
        else {
          for (final $Column column : table.getColumn()) {
            if (audit.isPrimary(table, column) && column.getNull$().text())
              errors.add("Primary key column `" + column.getName$().text() + "` on table `" + table.getName$().text() + "` is NULL.");
          }
        }
      }
    }

    return errors;
  }

  private final Map<String,Integer> columnCount = new HashMap<>();

  public Map<String,Integer> getColumnCount() {
    return columnCount;
  }

  static class ColumnRef {
    final $Column column;
    final int index;

    private ColumnRef(final $Column column, final int index) {
      this.column = column;
      this.index = index;
    }
  }

  private static void registerColumns(final $Table table, final Set<? super String> tableNames, final Map<? super String,ColumnRef> columnNameToColumn) throws GeneratorExecutionException {
    final String tableName = table.getName$().text();
    final List<String> violations = new ArrayList<>();
    String nameViolation = checkNameViolation(tableName);
    if (nameViolation != null)
      violations.add(nameViolation);

    if (tableNames.contains(tableName))
      throw new GeneratorExecutionException("Circular table dependency detected: " + tableName);

    tableNames.add(tableName);
    if (table.getColumn() != null) {
      final List<$Column> columns = table.getColumn();
      for (int c = 0, len = columns.size(); c < len; ++c) {
        final $Column column = columns.get(c);
        final String columnName = column.getName$().text();
        nameViolation = checkNameViolation(columnName);
        if (nameViolation != null)
          violations.add(nameViolation);

        final ColumnRef existing = columnNameToColumn.get(columnName);
        if (existing != null)
          throw new GeneratorExecutionException("Duplicate column definition: " + tableName + "." + columnName);

        columnNameToColumn.put(columnName, new ColumnRef(column, c));
      }
    }

    if (violations.size() > 0)
      violations.forEach(logger::warn);
  }

  private LinkedHashSet<CreateStatement> parseTable(final DBVendor vendor, final $Table table, final Set<? super String> tableNames) throws GeneratorExecutionException {
    // Next, register the column names to be referenceable by the @primaryKey element
    final Map<String,ColumnRef> columnNameToColumn = new HashMap<>();
    registerColumns(table, tableNames, columnNameToColumn);

    final Compiler compiler = Compiler.getCompiler(vendor);
    final LinkedHashSet<CreateStatement> statements = new LinkedHashSet<>(compiler.types(table));
    // FIXME: Redo this whole "CreateStatement" class model
    final LinkedHashSet<CreateStatement> alterStatements = new LinkedHashSet<>();

    columnCount.put(table.getName$().text(), table.getColumn() != null ? table.getColumn().size() : 0);
    final CreateStatement createTable = compiler.createTableIfNotExists(alterStatements, table, columnNameToColumn);

    statements.add(createTable);
    statements.addAll(alterStatements);

    statements.addAll(compiler.triggers(table));
    statements.addAll(compiler.indexes(table, columnNameToColumn));
    return statements;
  }

  public LinkedHashSet<Statement> parse(final DBVendor vendor) throws GeneratorExecutionException {
    final Map<String,LinkedHashSet<DropStatement>> dropTableStatements = new HashMap<>();
    final Map<String,LinkedHashSet<DropStatement>> dropTypeStatements = new HashMap<>();
    final Map<String,LinkedHashSet<CreateStatement>> createTableStatements = new HashMap<>();

    final Set<String> skipTables = new HashSet<>();

    for (final $Table table : schema.getTable()) {
      if (table.getSkip$().text()) {
        skipTables.add(table.getName$().text());
      }
      else if (!table.getAbstract$().text()) {
        dropTableStatements.put(table.getName$().text(), Compiler.getCompiler(vendor).dropTable(table));
        dropTypeStatements.put(table.getName$().text(), Compiler.getCompiler(vendor).dropTypes(table));
      }
    }

    final Set<String> tableNames = new HashSet<>();
    for (final $Table table : schema.getTable())
      if (!table.getAbstract$().text())
        createTableStatements.put(table.getName$().text(), parseTable(vendor, table, tableNames));

    final LinkedHashSet<Statement> statements = new LinkedHashSet<>();
    final CreateStatement createSchema = Compiler.getCompiler(vendor).createSchemaIfNotExists(audit.schema());
    if (createSchema != null)
      statements.add(createSchema);

    final ListIterator<$Table> listIterator = schema.getTable().listIterator(schema.getTable().size());
    while (listIterator.hasPrevious()) {
      final $Table table = listIterator.previous();
      final String tableName = table.getName$().text();
      if (!skipTables.contains(tableName))
        statements.addAll(dropTableStatements.get(tableName));
    }

    for (final $Table table : schema.getTable()) {
      final String tableName = table.getName$().text();
      if (!skipTables.contains(tableName))
        statements.addAll(dropTypeStatements.get(tableName));
    }

    for (final $Table table : schema.getTable()) {
      final String tableName = table.getName$().text();
      if (!skipTables.contains(tableName))
        statements.addAll(createTableStatements.get(tableName));
    }

    return statements;
  }

  public static boolean isAuto(final $Column column) {
    if (!(column instanceof $Integer))
      return false;

    final $Integer integer = ($Integer)column;
    return integer.getGenerateOnInsert$() != null && $Integer.GenerateOnInsert$.AUTO_5FINCREMENT.text().equals(integer.getGenerateOnInsert$().text());
  }
}