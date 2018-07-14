/* Copyright (c) 2017 lib4j
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

package org.libx4j.rdb.ddlx;

import java.util.HashMap;
import java.util.Map;

class ReservedWords {
  private static final Map<String,Integer> reservedWords = new HashMap<>();

  static {
    // reserved words per SQL spec (SQL-92, SQL-99, SQL-2003)
    reservedWords.put("ABSOLUTE", 0b110);
    reservedWords.put("ACTION", 0b110);
    reservedWords.put("ADD", 0b111);
    reservedWords.put("AFTER", 0b010);
    reservedWords.put("ALL", 0b111);
    reservedWords.put("ALLOCATE", 0b111);
    reservedWords.put("ALTER", 0b111);
    reservedWords.put("AND", 0b111);
    reservedWords.put("ANY", 0b111);
    reservedWords.put("ARE", 0b111);
    reservedWords.put("ARRAY", 0b011);
    reservedWords.put("AS", 0b111);
    reservedWords.put("ASC", 0b110);
    reservedWords.put("ASENSITIVE", 0b011);
    reservedWords.put("ASSERTION", 0b110);
    reservedWords.put("ASYMMETRIC", 0b011);
    reservedWords.put("AT", 0b111);
    reservedWords.put("ATOMIC", 0b011);
    reservedWords.put("AUTHORIZATION", 0b111);
    reservedWords.put("AVG", 0b100);
    reservedWords.put("BEFORE", 0b010);
    reservedWords.put("BEGIN", 0b111);
    reservedWords.put("BETWEEN", 0b111);
    reservedWords.put("BIGINT", 0b001);
    reservedWords.put("BINARY", 0b011);
    reservedWords.put("BIT", 0b110);
    reservedWords.put("BIT_LENGTH", 0b100);
    reservedWords.put("BLOB", 0b011);
    reservedWords.put("BOOLEAN", 0b011);
    reservedWords.put("BOTH", 0b111);
    reservedWords.put("BREADTH", 0b010);
    reservedWords.put("BY", 0b111);
    reservedWords.put("CALL", 0b111);
    reservedWords.put("CALLED", 0b001);
    reservedWords.put("CASCADE", 0b110);
    reservedWords.put("CASCADED", 0b111);
    reservedWords.put("CASE", 0b111);
    reservedWords.put("CAST", 0b111);
    reservedWords.put("CATALOG", 0b110);
    reservedWords.put("CHAR", 0b111);
    reservedWords.put("CHAR_LENGTH", 0b100);
    reservedWords.put("CHARACTER", 0b111);
    reservedWords.put("CHARACTER_LENGTH", 0b100);
    reservedWords.put("CHECK", 0b111);
    reservedWords.put("CLOB", 0b011);
    reservedWords.put("CLOSE", 0b111);
    reservedWords.put("COALESCE", 0b100);
    reservedWords.put("COLLATE", 0b111);
    reservedWords.put("COLLATION", 0b110);
    reservedWords.put("COLUMN", 0b111);
    reservedWords.put("COMMIT", 0b111);
    reservedWords.put("CONDITION", 0b111);
    reservedWords.put("CONNECT", 0b111);
    reservedWords.put("CONNECTION", 0b110);
    reservedWords.put("CONSTRAINT", 0b111);
    reservedWords.put("CONSTRAINTS", 0b110);
    reservedWords.put("CONSTRUCTOR", 0b010);
    reservedWords.put("CONTAINS", 0b100);
    reservedWords.put("CONTINUE", 0b111);
    reservedWords.put("CONVERT", 0b100);
    reservedWords.put("CORRESPONDING", 0b111);
    reservedWords.put("COUNT", 0b100);
    reservedWords.put("CREATE", 0b111);
    reservedWords.put("CROSS", 0b111);
    reservedWords.put("CUBE", 0b011);
    reservedWords.put("CURRENT", 0b111);
    reservedWords.put("CURRENT_DATE", 0b111);
    reservedWords.put("CURRENT_DEFAULT_TRANSFORM_GROUP", 0b011);
    reservedWords.put("CURRENT_PATH", 0b111);
    reservedWords.put("CURRENT_ROLE", 0b011);
    reservedWords.put("CURRENT_TIME", 0b111);
    reservedWords.put("CURRENT_TIMESTAMP", 0b111);
    reservedWords.put("CURRENT_TRANSFORM_GROUP_FOR_TYPE", 0b011);
    reservedWords.put("CURRENT_USER", 0b111);
    reservedWords.put("CURSOR", 0b111);
    reservedWords.put("CYCLE", 0b011);
    reservedWords.put("DATA", 0b010);
    reservedWords.put("DATE", 0b111);
    reservedWords.put("DAY", 0b111);
    reservedWords.put("DEALLOCATE", 0b111);
    reservedWords.put("DEC", 0b111);
    reservedWords.put("DECIMAL", 0b111);
    reservedWords.put("DECLARE", 0b111);
    reservedWords.put("DEFAULT", 0b111);
    reservedWords.put("DEFERRABLE", 0b110);
    reservedWords.put("DEFERRED", 0b110);
    reservedWords.put("DELETE", 0b111);
    reservedWords.put("DEPTH", 0b010);
    reservedWords.put("DEREF", 0b011);
    reservedWords.put("DESC", 0b110);
    reservedWords.put("DESCRIBE", 0b111);
    reservedWords.put("DESCRIPTOR", 0b110);
    reservedWords.put("DETERMINISTIC", 0b111);
    reservedWords.put("DIAGNOSTICS", 0b110);
    reservedWords.put("DISCONNECT", 0b111);
    reservedWords.put("DISTINCT", 0b111);
    reservedWords.put("DO", 0b111);
    reservedWords.put("DOMAIN", 0b110);
    reservedWords.put("DOUBLE", 0b111);
    reservedWords.put("DROP", 0b111);
    reservedWords.put("DYNAMIC", 0b011);
    reservedWords.put("EACH", 0b011);
    reservedWords.put("ELEMENT", 0b001);
    reservedWords.put("ELSE", 0b111);
    reservedWords.put("ELSEIF", 0b111);
    reservedWords.put("END", 0b111);
    reservedWords.put("EQUALS", 0b010);
    reservedWords.put("ESCAPE", 0b111);
    reservedWords.put("EXCEPT", 0b111);
    reservedWords.put("EXCEPTION", 0b110);
    reservedWords.put("EXEC", 0b111);
    reservedWords.put("EXECUTE", 0b111);
    reservedWords.put("EXISTS", 0b111);
    reservedWords.put("EXIT", 0b111);
    reservedWords.put("EXTERNAL", 0b111);
    reservedWords.put("EXTRACT", 0b100);
    reservedWords.put("FALSE", 0b111);
    reservedWords.put("FETCH", 0b111);
    reservedWords.put("FILTER", 0b011);
    reservedWords.put("FIRST", 0b110);
    reservedWords.put("FLOAT", 0b111);
    reservedWords.put("FOR", 0b111);
    reservedWords.put("FOREIGN", 0b111);
    reservedWords.put("FOUND", 0b110);
    reservedWords.put("FREE", 0b011);
    reservedWords.put("FROM", 0b111);
    reservedWords.put("FULL", 0b111);
    reservedWords.put("FUNCTION", 0b111);
    reservedWords.put("GENERAL", 0b010);
    reservedWords.put("GET", 0b111);
    reservedWords.put("GLOBAL", 0b111);
    reservedWords.put("GO", 0b110);
    reservedWords.put("GOTO", 0b110);
    reservedWords.put("GRANT", 0b111);
    reservedWords.put("GROUP", 0b111);
    reservedWords.put("GROUPING", 0b011);
    reservedWords.put("HANDLER", 0b111);
    reservedWords.put("HAVING", 0b111);
    reservedWords.put("HOLD", 0b011);
    reservedWords.put("HOUR", 0b111);
    reservedWords.put("IDENTITY", 0b111);
    reservedWords.put("IF", 0b111);
    reservedWords.put("IMMEDIATE", 0b111);
    reservedWords.put("IN", 0b111);
    reservedWords.put("INDICATOR", 0b111);
    reservedWords.put("INITIALLY", 0b110);
    reservedWords.put("INNER", 0b111);
    reservedWords.put("INOUT", 0b111);
    reservedWords.put("INPUT", 0b111);
    reservedWords.put("INSENSITIVE", 0b111);
    reservedWords.put("INSERT", 0b111);
    reservedWords.put("INT", 0b111);
    reservedWords.put("INTEGER", 0b111);
    reservedWords.put("INTERSECT", 0b111);
    reservedWords.put("INTERVAL", 0b111);
    reservedWords.put("INTO", 0b111);
    reservedWords.put("IS", 0b111);
    reservedWords.put("ISOLATION", 0b110);
    reservedWords.put("ITERATE", 0b011);
    reservedWords.put("JOIN", 0b111);
    reservedWords.put("KEY", 0b110);
    reservedWords.put("LANGUAGE", 0b111);
    reservedWords.put("LARGE", 0b011);
    reservedWords.put("LAST", 0b110);
    reservedWords.put("LATERAL", 0b011);
    reservedWords.put("LEADING", 0b111);
    reservedWords.put("LEAVE", 0b111);
    reservedWords.put("LEFT", 0b111);
    reservedWords.put("LEVEL", 0b110);
    reservedWords.put("LIKE", 0b111);
    reservedWords.put("LOCAL", 0b111);
    reservedWords.put("LOCALTIME", 0b011);
    reservedWords.put("LOCALTIMESTAMP", 0b011);
    reservedWords.put("LOCATOR", 0b010);
    reservedWords.put("LOOP", 0b111);
    reservedWords.put("LOWER", 0b100);
    reservedWords.put("MAP", 0b010);
    reservedWords.put("MATCH", 0b111);
    reservedWords.put("MAX", 0b100);
    reservedWords.put("MEMBER", 0b001);
    reservedWords.put("MERGE", 0b001);
    reservedWords.put("METHOD", 0b011);
    reservedWords.put("MIN", 0b100);
    reservedWords.put("MINUTE", 0b111);
    reservedWords.put("MODIFIES", 0b011);
    reservedWords.put("MODULE", 0b111);
    reservedWords.put("MONTH", 0b111);
    reservedWords.put("MULTISET", 0b001);
    reservedWords.put("NAMES", 0b110);
    reservedWords.put("NATIONAL", 0b111);
    reservedWords.put("NATURAL", 0b111);
    reservedWords.put("NCHAR", 0b111);
    reservedWords.put("NCLOB", 0b011);
    reservedWords.put("NEW", 0b011);
    reservedWords.put("NEXT", 0b110);
    reservedWords.put("NO", 0b111);
    reservedWords.put("NONE", 0b011);
    reservedWords.put("NOT", 0b111);
    reservedWords.put("NULL", 0b111);
    reservedWords.put("NULLIF", 0b100);
    reservedWords.put("NUMERIC", 0b111);
    reservedWords.put("OBJECT", 0b010);
    reservedWords.put("OCTET_LENGTH", 0b100);
    reservedWords.put("OF", 0b111);
    reservedWords.put("OLD", 0b011);
    reservedWords.put("ON", 0b111);
    reservedWords.put("ONLY", 0b111);
    reservedWords.put("OPEN", 0b111);
    reservedWords.put("OPTION", 0b110);
    reservedWords.put("OR", 0b111);
    reservedWords.put("ORDER", 0b111);
    reservedWords.put("ORDINALITY", 0b010);
    reservedWords.put("OUT", 0b111);
    reservedWords.put("OUTER", 0b111);
    reservedWords.put("OUTPUT", 0b111);
    reservedWords.put("OVER", 0b011);
    reservedWords.put("OVERLAPS", 0b111);
    reservedWords.put("PAD", 0b110);
    reservedWords.put("PARAMETER", 0b111);
    reservedWords.put("PARTIAL", 0b110);
    reservedWords.put("PARTITION", 0b011);
    reservedWords.put("PATH", 0b110);
    reservedWords.put("POSITION", 0b100);
    reservedWords.put("PRECISION", 0b111);
    reservedWords.put("PREPARE", 0b111);
    reservedWords.put("PRESERVE", 0b110);
    reservedWords.put("PRIMARY", 0b111);
    reservedWords.put("PRIOR", 0b110);
    reservedWords.put("PRIVILEGES", 0b110);
    reservedWords.put("PROCEDURE", 0b111);
    reservedWords.put("PUBLIC", 0b110);
    reservedWords.put("RANGE", 0b011);
    reservedWords.put("READ", 0b110);
    reservedWords.put("READS", 0b011);
    reservedWords.put("REAL", 0b111);
    reservedWords.put("RECURSIVE", 0b011);
    reservedWords.put("REF", 0b011);
    reservedWords.put("REFERENCES", 0b111);
    reservedWords.put("REFERENCING", 0b011);
    reservedWords.put("RELATIVE", 0b110);
    reservedWords.put("RELEASE", 0b011);
    reservedWords.put("REPEAT", 0b111);
    reservedWords.put("RESIGNAL", 0b111);
    reservedWords.put("RESTRICT", 0b110);
    reservedWords.put("RESULT", 0b011);
    reservedWords.put("RETURN", 0b111);
    reservedWords.put("RETURNS", 0b111);
    reservedWords.put("REVOKE", 0b111);
    reservedWords.put("RIGHT", 0b111);
    reservedWords.put("ROLE", 0b010);
    reservedWords.put("ROLLBACK", 0b111);
    reservedWords.put("ROLLUP", 0b011);
    reservedWords.put("ROUTINE", 0b110);
    reservedWords.put("ROW", 0b011);
    reservedWords.put("ROWS", 0b111);
    reservedWords.put("SAVEPOINT", 0b011);
    reservedWords.put("SCHEMA", 0b110);
    reservedWords.put("SCOPE", 0b011);
    reservedWords.put("SCROLL", 0b111);
    reservedWords.put("SEARCH", 0b011);
    reservedWords.put("SECOND", 0b111);
    reservedWords.put("SECTION", 0b110);
    reservedWords.put("SELECT", 0b111);
    reservedWords.put("SENSITIVE", 0b011);
    reservedWords.put("SESSION", 0b110);
    reservedWords.put("SESSION_USER", 0b111);
    reservedWords.put("SET", 0b111);
    reservedWords.put("SETS", 0b010);
    reservedWords.put("SIGNAL", 0b111);
    reservedWords.put("SIMILAR", 0b011);
    reservedWords.put("SIZE", 0b110);
    reservedWords.put("SMALLINT", 0b111);
    reservedWords.put("SOME", 0b111);
    reservedWords.put("SPACE", 0b110);
    reservedWords.put("SPECIFIC", 0b111);
    reservedWords.put("SPECIFICTYPE", 0b011);
    reservedWords.put("SQL", 0b111);
    reservedWords.put("SQLCODE", 0b100);
    reservedWords.put("SQLERROR", 0b100);
    reservedWords.put("SQLEXCEPTION", 0b111);
    reservedWords.put("SQLSTATE", 0b111);
    reservedWords.put("SQLWARNING", 0b111);
    reservedWords.put("START", 0b011);
    reservedWords.put("STATE", 0b010);
    reservedWords.put("STATIC", 0b011);
    reservedWords.put("SUBMULTISET", 0b001);
    reservedWords.put("SUBSTRING", 0b100);
    reservedWords.put("SUM", 0b100);
    reservedWords.put("SYMMETRIC", 0b011);
    reservedWords.put("SYSTEM", 0b011);
    reservedWords.put("SYSTEM_USER", 0b111);
    reservedWords.put("TABLE", 0b111);
    reservedWords.put("TABLESAMPLE", 0b001);
    reservedWords.put("TEMPORARY", 0b110);
    reservedWords.put("THEN", 0b111);
    reservedWords.put("TIME", 0b111);
    reservedWords.put("TIMESTAMP", 0b111);
    reservedWords.put("TIMEZONE_HOUR", 0b111);
    reservedWords.put("TIMEZONE_MINUTE", 0b111);
    reservedWords.put("TO", 0b111);
    reservedWords.put("TRAILING", 0b111);
    reservedWords.put("TRANSACTION", 0b110);
    reservedWords.put("TRANSLATE", 0b100);
    reservedWords.put("TRANSLATION", 0b111);
    reservedWords.put("TREAT", 0b011);
    reservedWords.put("TRIGGER", 0b011);
    reservedWords.put("TRIM", 0b100);
    reservedWords.put("TRUE", 0b111);
    reservedWords.put("UNDER", 0b010);
    reservedWords.put("UNDO", 0b111);
    reservedWords.put("UNION", 0b111);
    reservedWords.put("UNIQUE", 0b111);
    reservedWords.put("UNKNOWN", 0b111);
    reservedWords.put("UNNEST", 0b011);
    reservedWords.put("UNTIL", 0b111);
    reservedWords.put("UPDATE", 0b111);
    reservedWords.put("UPPER", 0b100);
    reservedWords.put("USAGE", 0b110);
    reservedWords.put("USER", 0b111);
    reservedWords.put("USING", 0b111);
    reservedWords.put("VALUE", 0b111);
    reservedWords.put("VALUES", 0b111);
    reservedWords.put("VARCHAR", 0b111);
    reservedWords.put("VARYING", 0b111);
    reservedWords.put("VIEW", 0b110);
    reservedWords.put("WHEN", 0b111);
    reservedWords.put("WHENEVER", 0b111);
    reservedWords.put("WHERE", 0b111);
    reservedWords.put("WHILE", 0b111);
    reservedWords.put("WINDOW", 0b011);
    reservedWords.put("WITH", 0b111);
    reservedWords.put("WITHIN", 0b011);
    reservedWords.put("WITHOUT", 0b011);
    reservedWords.put("WORK", 0b110);
    reservedWords.put("WRITE", 0b110);
    reservedWords.put("YEAR", 0b111);
    reservedWords.put("ZONE", 0b110);
  }

  protected static SQLStandard[] get(final String word) {
    final Integer mask = reservedWords.get(word);
    return mask == null ? null : SQLStandard.toArray(mask);
  }
}