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

package org.jaxdb;

import static org.jaxdb.jsql.DML.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.sql.SQLException;

import org.jaxdb.jsql.DML.NOT;
import org.jaxdb.jsql.RowIterator;
import org.jaxdb.jsql.Transaction;
import org.jaxdb.jsql.type;
import org.jaxdb.jsql.types;
import org.jaxdb.runner.Derby;
import org.jaxdb.runner.MySQL;
import org.jaxdb.runner.Oracle;
import org.jaxdb.runner.PostgreSQL;
import org.jaxdb.runner.SQLite;
import org.jaxdb.runner.VendorSchemaRunner;
import org.jaxdb.runner.VendorSchemaRunner.Schema;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VendorSchemaRunner.class)
public abstract class CastTest {
  @VendorSchemaRunner.Vendor(value=Derby.class, parallel=2)
  @VendorSchemaRunner.Vendor(SQLite.class)
  public static class IntegrationTest extends CastTest {
  }

  @VendorSchemaRunner.Vendor(MySQL.class)
  @VendorSchemaRunner.Vendor(PostgreSQL.class)
  @VendorSchemaRunner.Vendor(Oracle.class)
  public static class RegressionTest extends CastTest {
  }

  @Test
  public void testBooleanToChar(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.CHAR> rows =
      SELECT(
        CAST(t.booleanType).AS.CHAR(5),
        CAST(SELECT(t.booleanType).FROM(t).LIMIT(1)).AS.CHAR(5)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testBooleanToClob(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.CLOB> rows =
      SELECT(
        CAST(t.booleanType).AS.CLOB(5),
        CAST(SELECT(t.booleanType).FROM(t).LIMIT(1)).AS.CLOB(5)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testFloatToDouble(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.DOUBLE> rows =
      SELECT(
        CAST(t.floatType).AS.DOUBLE(),
        CAST(SELECT(AVG(t.floatType)).FROM(t)).AS.DOUBLE()).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testFloatToDecimal(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.DECIMAL> rows =
      SELECT(
        CAST(t.floatType).AS.DECIMAL(transaction.getVendor().getDialect().decimalMaxPrecision(), 10),
        CAST(SELECT(t.floatType).FROM(t).LIMIT(1)).AS.DECIMAL(transaction.getVendor().getDialect().decimalMaxPrecision(), 10)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testFloatToSmallInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.TINYINT> rows =
      SELECT(
        CAST(t.floatType).AS.TINYINT(3),
        CAST(SELECT(MIN(t.floatType)).FROM(t).WHERE(AND(GTE(t.floatType, Byte.MIN_VALUE), LTE(t.floatType, Byte.MAX_VALUE)))).AS.TINYINT(3)).
      FROM(t).
      WHERE(AND(GTE(t.floatType, Byte.MIN_VALUE), LTE(t.floatType, Byte.MAX_VALUE)))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testFloatToMediumInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.SMALLINT> rows =
      SELECT(
        CAST(t.floatType).AS.SMALLINT(5),
        CAST(SELECT(t.floatType).FROM(t).WHERE(AND(GTE(t.floatType, Byte.MIN_VALUE), LTE(t.floatType, Byte.MAX_VALUE))).LIMIT(1)).AS.SMALLINT(5)).
      FROM(t).
      WHERE(AND(GTE(t.floatType, Byte.MIN_VALUE), LTE(t.floatType, Byte.MAX_VALUE)))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testFloatToInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.INT> rows =
      SELECT(
        CAST(t.floatType).AS.INT(10),
        CAST(SELECT(MIN(t.floatType)).FROM(t)).AS.INT(10)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testFloatToBigInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.BIGINT> rows =
      SELECT(
        CAST(t.floatType).AS.BIGINT(19),
        CAST(SELECT(MAX(t.floatType)).FROM(t)).AS.BIGINT(19)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testDoubleToFloat(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.FLOAT> rows =
      SELECT(
        CAST(t.doubleType).AS.FLOAT(),
        CAST(SELECT(AVG(t.doubleType)).FROM(t)).AS.FLOAT()).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testDoubleToDecimal(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.DECIMAL> rows =
      SELECT(
        CAST(t.doubleType).AS.DECIMAL(transaction.getVendor().getDialect().decimalMaxPrecision(), 10),
        CAST(SELECT(AVG(t.doubleType)).FROM(t)).AS.DECIMAL(transaction.getVendor().getDialect().decimalMaxPrecision(), 10)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testDoubleToSmallInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.TINYINT> rows =
      SELECT(
        CAST(t.doubleType).AS.TINYINT(3),
        CAST(SELECT(MIN(t.doubleType)).FROM(t).WHERE(AND(GTE(t.doubleType, Byte.MIN_VALUE), LTE(t.doubleType, Byte.MAX_VALUE)))).AS.TINYINT(3)).
      FROM(t).
      WHERE(AND(GTE(t.doubleType, Byte.MIN_VALUE), LTE(t.doubleType, Byte.MAX_VALUE)))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testDoubleToMediumInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.SMALLINT> rows =
      SELECT(
        CAST(t.doubleType).AS.SMALLINT(5),
        CAST(SELECT(MAX(t.doubleType)).FROM(t).WHERE(AND(GTE(t.doubleType, Short.MIN_VALUE), LTE(t.doubleType, Short.MAX_VALUE)))).AS.SMALLINT(5)).
      FROM(t).
      WHERE(AND(GTE(t.doubleType, Short.MIN_VALUE), LTE(t.doubleType, Short.MAX_VALUE)))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testDoubleToInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.INT> rows =
      SELECT(
        CAST(t.doubleType).AS.INT(10),
        CAST(SELECT(AVG(t.doubleType)).FROM(t)).AS.INT(10)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testDoubleToBigInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.BIGINT> rows =
      SELECT(
        CAST(t.doubleType).AS.BIGINT(19),
        CAST(SELECT(MIN(t.doubleType)).FROM(t)).AS.BIGINT(19)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testDecimalToFloat(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.FLOAT> rows =
      SELECT(
        CAST(t.decimalType).AS.FLOAT(),
        CAST(SELECT(MAX(t.decimalType)).FROM(t)).AS.FLOAT()).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testDecimalToDouble(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.DOUBLE> rows =
      SELECT(
        CAST(t.decimalType).AS.DOUBLE(),
        CAST(SELECT(AVG(t.decimalType)).FROM(t)).AS.DOUBLE()).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testDecimalToDecimal(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.DECIMAL> rows =
      SELECT(
        CAST(t.decimalType).AS.DECIMAL(transaction.getVendor().getDialect().decimalMaxPrecision(), 10),
        CAST(SELECT(MIN(t.decimalType)).FROM(t)).AS.DECIMAL(transaction.getVendor().getDialect().decimalMaxPrecision(), 10)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testDecimalToSmallInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.TINYINT> rows =
      SELECT(
        CAST(t.decimalType).AS.TINYINT(3),
        CAST(SELECT(MAX(t.decimalType)).FROM(t).WHERE(AND(GTE(t.decimalType, Byte.MIN_VALUE), LTE(t.decimalType, Byte.MAX_VALUE)))).AS.TINYINT(3)).
      FROM(t).
      WHERE(AND(GTE(t.decimalType, Byte.MIN_VALUE), LTE(t.decimalType, Byte.MAX_VALUE)))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testDecimalToMediumInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.SMALLINT> rows =
      SELECT(
        CAST(t.decimalType).AS.SMALLINT(5),
        CAST(SELECT(AVG(t.decimalType)).FROM(t).WHERE(AND(GTE(t.decimalType, Byte.MIN_VALUE), LTE(t.decimalType, Byte.MAX_VALUE)))).AS.SMALLINT(5)).
      FROM(t).
      WHERE(AND(GTE(t.decimalType, Byte.MIN_VALUE), LTE(t.decimalType, Byte.MAX_VALUE)))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testDecimalToInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.INT> rows =
      SELECT(
        CAST(t.decimalType).AS.INT(10),
        CAST(SELECT(MIN(t.decimalType)).FROM(t).WHERE(AND(LTE(t.decimalType, Integer.MAX_VALUE), GTE(t.decimalType, Integer.MIN_VALUE)))).AS.INT(10)).
      FROM(t).
      WHERE(AND(LTE(t.decimalType, Integer.MAX_VALUE), GTE(t.decimalType, Integer.MIN_VALUE)))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testDecimalToBigInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.BIGINT> rows =
      SELECT(
        CAST(t.decimalType).AS.BIGINT(19),
        CAST(SELECT(MAX(t.decimalType)).FROM(t).WHERE(AND(GTE(t.decimalType, 0), LTE(t.decimalType, Integer.MAX_VALUE)))).AS.BIGINT(19)).
      FROM(t).
      WHERE(AND(GTE(t.decimalType, 0), LTE(t.decimalType, Integer.MAX_VALUE)))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testDecimalToChar(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.CHAR> rows =
      SELECT(
        CAST(t.decimalType).AS.CHAR(254),
        CAST(SELECT(AVG(t.decimalType)).FROM(t)).AS.CHAR(254)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testSmallIntToFloat(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.FLOAT> rows =
      SELECT(
        CAST(t.tinyintType).AS.FLOAT(),
        CAST(SELECT(MAX(t.tinyintType)).FROM(t)).AS.FLOAT()).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testSmallIntToDouble(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.DOUBLE> rows =
      SELECT(
        CAST(t.tinyintType).AS.DOUBLE(),
        CAST(SELECT(AVG(t.tinyintType)).FROM(t)).AS.DOUBLE()).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testSmallIntToDecimal(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.DECIMAL> rows =
      SELECT(
        CAST(t.tinyintType).AS.DECIMAL(transaction.getVendor().getDialect().decimalMaxPrecision(), 10),
        CAST(SELECT(MIN(t.tinyintType)).FROM(t)).AS.DECIMAL(transaction.getVendor().getDialect().decimalMaxPrecision(), 10)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testSmallIntToSmallInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.TINYINT> rows =
      SELECT(
        CAST(t.tinyintType).AS.TINYINT(3),
        CAST(SELECT(MAX(t.tinyintType)).FROM(t).WHERE(AND(GTE(t.tinyintType, Byte.MIN_VALUE), LTE(t.tinyintType, Byte.MAX_VALUE)))).AS.TINYINT(3)).
      FROM(t).
      WHERE(AND(GTE(t.tinyintType, Byte.MIN_VALUE), LTE(t.tinyintType, Byte.MAX_VALUE)))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testSmallIntToMediumInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.SMALLINT> rows =
      SELECT(
        CAST(t.tinyintType).AS.SMALLINT(5),
        CAST(SELECT(MIN(t.tinyintType)).FROM(t).WHERE(AND(GTE(t.tinyintType, Byte.MIN_VALUE), LTE(t.tinyintType, Byte.MAX_VALUE)))).AS.SMALLINT(5)).
      FROM(t).
      WHERE(AND(GTE(t.tinyintType, Byte.MIN_VALUE), LTE(t.tinyintType, Byte.MAX_VALUE)))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testSmallIntToInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.INT> rows =
      SELECT(
        CAST(t.tinyintType).AS.INT(10),
        CAST(SELECT(MIN(t.tinyintType)).FROM(t)).AS.INT(10)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testSmallIntToBigInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.BIGINT> rows =
      SELECT(
        CAST(t.tinyintType).AS.BIGINT(19),
        CAST(SELECT(MAX(t.tinyintType)).FROM(t)).AS.BIGINT(19)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testSmallIntToChar(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.CHAR> rows =
      SELECT(
        CAST(t.tinyintType).AS.CHAR(254),
        CAST(SELECT(MAX(t.tinyintType)).FROM(t)).AS.CHAR(254)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testMediumIntToFloat(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.FLOAT> rows =
      SELECT(
        CAST(t.smallintType).AS.FLOAT(),
        CAST(SELECT(MAX(t.smallintType)).FROM(t)).AS.FLOAT()).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testMediumIntToDouble(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.DOUBLE> rows =
      SELECT(
        CAST(t.smallintType).AS.DOUBLE(),
        CAST(SELECT(AVG(t.smallintType)).FROM(t)).AS.DOUBLE()).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testMediumIntToDecimal(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.DECIMAL> rows =
      SELECT(
        CAST(t.smallintType).AS.DECIMAL(transaction.getVendor().getDialect().decimalMaxPrecision(), 10),
        CAST(SELECT(MIN(t.smallintType)).FROM(t)).AS.DECIMAL(transaction.getVendor().getDialect().decimalMaxPrecision(), 10)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testMediumIntToSmallInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.TINYINT> rows =
      SELECT(
        CAST(t.smallintType).AS.TINYINT(3),
        CAST(SELECT(MAX(t.smallintType)).FROM(t).WHERE(AND(GTE(t.smallintType, Byte.MIN_VALUE), LTE(t.smallintType, Byte.MAX_VALUE)))).AS.TINYINT(3)).
      FROM(t).
      WHERE(AND(GTE(t.smallintType, Byte.MIN_VALUE), LTE(t.smallintType, Byte.MAX_VALUE)))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testMediumIntToMediumInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.SMALLINT> rows =
      SELECT(
        CAST(t.smallintType).AS.SMALLINT(5),
        CAST(SELECT(AVG(t.smallintType)).FROM(t).WHERE(AND(GTE(t.smallintType, Short.MIN_VALUE), LTE(t.smallintType, Short.MAX_VALUE)))).AS.SMALLINT(5)).
      FROM(t).
      WHERE(AND(GTE(t.smallintType, Short.MIN_VALUE), LTE(t.smallintType, Short.MAX_VALUE)))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testMediumIntToInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.INT> rows =
      SELECT(
        CAST(t.smallintType).AS.INT(10),
        CAST(SELECT(MIN(t.smallintType)).FROM(t)).AS.INT(10)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testMediumIntToBigInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.BIGINT> rows =
      SELECT(
        CAST(t.smallintType).AS.BIGINT(19),
        CAST(SELECT(MAX(t.smallintType)).FROM(t)).AS.BIGINT(19)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testMediumIntToChar(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.CHAR> rows =
      SELECT(
        CAST(t.smallintType).AS.CHAR(254),
        CAST(SELECT(AVG(t.smallintType)).FROM(t)).AS.CHAR(254)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testIntToFloat(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.FLOAT> rows =
      SELECT(
        CAST(t.intType).AS.FLOAT(),
        CAST(SELECT(MAX(t.intType)).FROM(t)).AS.FLOAT()).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testIntToDouble(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.DOUBLE> rows =
      SELECT(
        CAST(t.intType).AS.DOUBLE(),
        CAST(SELECT(AVG(t.intType)).FROM(t)).AS.DOUBLE()).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testIntToDecimal(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.DECIMAL> rows =
      SELECT(
        CAST(t.intType).AS.DECIMAL(transaction.getVendor().getDialect().decimalMaxPrecision(), 10),
        CAST(SELECT(MIN(t.intType)).FROM(t)).AS.DECIMAL(transaction.getVendor().getDialect().decimalMaxPrecision(), 10)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testIntToSmallInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.TINYINT> rows =
      SELECT(
        CAST(t.intType).AS.TINYINT(3),
        CAST(SELECT(MAX(t.intType)).FROM(t).WHERE(AND(GTE(t.intType, Byte.MIN_VALUE), LTE(t.intType, Byte.MAX_VALUE)))).AS.TINYINT(3)).
      FROM(t).
      WHERE(AND(GTE(t.intType, Byte.MIN_VALUE), LTE(t.intType, Byte.MAX_VALUE)))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testIntToMediumInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.SMALLINT> rows =
      SELECT(
        CAST(t.intType).AS.SMALLINT(5),
        CAST(SELECT(AVG(t.intType)).FROM(t).WHERE(AND(GTE(t.intType, Short.MIN_VALUE), LTE(t.intType, Short.MAX_VALUE)))).AS.SMALLINT(5)).
      FROM(t).
      WHERE(AND(GTE(t.intType, Short.MIN_VALUE), LTE(t.intType, Short.MAX_VALUE)))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testIntToInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.INT> rows =
      SELECT(
        CAST(t.intType).AS.INT(10),
        CAST(SELECT(MIN(t.intType)).FROM(t)).AS.INT(10)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testIntToBigInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.BIGINT> rows =
      SELECT(
        CAST(t.intType).AS.BIGINT(19),
        CAST(SELECT(MAX(t.intType)).FROM(t)).AS.BIGINT(19)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testIntToChar(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.CHAR> rows =
      SELECT(
        CAST(t.intType).AS.CHAR(254),
        CAST(SELECT(AVG(t.intType)).FROM(t)).AS.CHAR(254)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testBigIntToFloat(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.FLOAT> rows =
      SELECT(
        CAST(t.bigintType).AS.FLOAT(),
        CAST(SELECT(MAX(t.bigintType)).FROM(t)).AS.FLOAT()).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testBigIntToDouble(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.DOUBLE> rows =
      SELECT(
        CAST(t.bigintType).AS.DOUBLE(),
        CAST(SELECT(AVG(t.bigintType)).FROM(t)).AS.DOUBLE()).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testBigIntToDecimal(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.DECIMAL> rows =
      SELECT(
        CAST(t.bigintType).AS.DECIMAL(transaction.getVendor().getDialect().decimalMaxPrecision(), 5),
        CAST(
          SELECT(MIN(t.bigintType)).
            FROM(t).
            WHERE(AND(LT(t.bigintType, Integer.MAX_VALUE), GT(t.bigintType, Integer.MIN_VALUE)))).AS.DECIMAL(transaction.getVendor().getDialect().decimalMaxPrecision(), 5)).
      FROM(t).
      WHERE(AND(LT(t.bigintType, Integer.MAX_VALUE), GT(t.bigintType, Integer.MIN_VALUE)))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testBigIntToSmallInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.TINYINT> rows =
      SELECT(
        CAST(t.bigintType).AS.TINYINT(3),
        CAST(SELECT(MAX(t.bigintType)).FROM(t).WHERE(AND(GTE(t.bigintType, Byte.MIN_VALUE), LTE(t.bigintType, Byte.MAX_VALUE)))).AS.TINYINT(3)).
      FROM(t).
      WHERE(AND(GTE(t.bigintType, Byte.MIN_VALUE), LTE(t.bigintType, Byte.MAX_VALUE)))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testBigIntToMediumInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.SMALLINT> rows =
      SELECT(
        CAST(t.bigintType).AS.SMALLINT(5),
        CAST(SELECT(AVG(t.bigintType)).FROM(t).WHERE(AND(GTE(t.bigintType, Short.MIN_VALUE), LTE(t.bigintType, Short.MAX_VALUE)))).AS.SMALLINT(5)).
      FROM(t).
      WHERE(AND(GTE(t.bigintType, Short.MIN_VALUE), LTE(t.bigintType, Short.MAX_VALUE)))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testBigIntToInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.INT> rows =
      SELECT(
        CAST(t.bigintType).AS.INT(10),
        CAST(SELECT(MIN(t.bigintType)).FROM(t).WHERE(AND(LT(t.bigintType, Integer.MAX_VALUE), GT(t.bigintType, Integer.MIN_VALUE)))).AS.INT(10)).
      FROM(t).
      WHERE(AND(LT(t.bigintType, Integer.MAX_VALUE), GT(t.bigintType, Integer.MIN_VALUE)))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testBigIntToBigInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.BIGINT> rows =
      SELECT(
        CAST(t.bigintType).AS.BIGINT(19),
        CAST(SELECT(MAX(t.bigintType)).FROM(t)).AS.BIGINT(19)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testBigIntToChar(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.CHAR> rows =
      SELECT(
        CAST(t.bigintType).AS.CHAR(254),
        CAST(SELECT(AVG(t.bigintType)).FROM(t)).AS.CHAR(254)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testCharToDecimal(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.DECIMAL> rows =
      SELECT(
        CAST(t.charType).AS.DECIMAL(transaction.getVendor().getDialect().decimalMaxPrecision(), 10),
        CAST(
          SELECT(t.charType).
          FROM(t).
          WHERE(AND(LIKE(t.charType, "%1%"), NOT.LIKE(t.charType, "%-%"), NOT.LIKE(t.charType, "%:%"))).LIMIT(1)).AS.DECIMAL(transaction.getVendor().getDialect().decimalMaxPrecision(), 10)).
      FROM(t).
      WHERE(AND(LIKE(t.charType, "%1%"), NOT.LIKE(t.charType, "%-%"), NOT.LIKE(t.charType, "%:%")))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testCharToSmallInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.TINYINT> rows =
      SELECT(
        CAST(t.charType).AS.TINYINT(3),
        CAST(SELECT(t.charType).FROM(t).WHERE(AND(LIKE(t.charType, "%1%"), NOT.LIKE(t.charType, "%.%"), NOT.LIKE(t.charType, "%-%"), NOT.LIKE(t.charType, "%:%"))).LIMIT(1)).AS.TINYINT(3)).
      FROM(t).
      WHERE(AND(LIKE(t.charType, "%1%"), NOT.LIKE(t.charType, "%.%"), NOT.LIKE(t.charType, "%-%"), NOT.LIKE(t.charType, "%:%")))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testCharToMediumInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.SMALLINT> rows =
      SELECT(
        CAST(t.charType).AS.SMALLINT(5),
        CAST(SELECT(t.charType).FROM(t).WHERE(AND(LIKE(t.charType, "%1%"), NOT.LIKE(t.charType, "%.%"), NOT.LIKE(t.charType, "%-%"), NOT.LIKE(t.charType, "%:%"))).LIMIT(1)).AS.SMALLINT(5)).
      FROM(t).
      WHERE(AND(LIKE(t.charType, "%1%"), NOT.LIKE(t.charType, "%.%"), NOT.LIKE(t.charType, "%-%"), NOT.LIKE(t.charType, "%:%")))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testCharToInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.INT> rows =
      SELECT(
        CAST(t.charType).AS.INT(10),
        CAST(SELECT(t.charType).FROM(t).WHERE(AND(LIKE(t.charType, "%1%"), NOT.LIKE(t.charType, "%.%"), NOT.LIKE(t.charType, "%-%"), NOT.LIKE(t.charType, "%:%"))).LIMIT(1)).AS.INT(10)).
      FROM(t).
      WHERE(AND(LIKE(t.charType, "%1%"), NOT.LIKE(t.charType, "%.%"), NOT.LIKE(t.charType, "%-%"), NOT.LIKE(t.charType, "%:%")))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testCharToBigInt(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.BIGINT> rows =
      SELECT(
        CAST(t.charType).AS.BIGINT(19),
        CAST(SELECT(t.charType).FROM(t).WHERE(AND(LIKE(t.charType, "%1%"), NOT.LIKE(t.charType, "%.%"), NOT.LIKE(t.charType, "%-%"), NOT.LIKE(t.charType, "%:%"))).LIMIT(1)).AS.BIGINT(19)).
      FROM(t).
      WHERE(AND(LIKE(t.charType, "%1%"), NOT.LIKE(t.charType, "%.%"), NOT.LIKE(t.charType, "%-%"), NOT.LIKE(t.charType, "%:%")))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testCharToChar(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.CHAR> rows =
      SELECT(
        CAST(t.charType).AS.CHAR(254),
        CAST(SELECT(t.charType).FROM(t).LIMIT(1)).AS.CHAR(254)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testCharToDate(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.DATE> rows =
      SELECT(
        CAST(t.charType).AS.DATE(),
        CAST(SELECT(t.charType).FROM(t).WHERE(AND(LIKE(t.charType, "%-%-%"), NOT.LIKE(t.charType, "%-%-% %"))).LIMIT(1)).AS.DATE()).
      FROM(t).
      WHERE(AND(LIKE(t.charType, "%-%-%"), NOT.LIKE(t.charType, "%-%-% %")))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testCharToTime(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.TIME> rows =
      SELECT(
        CAST(t.charType).AS.TIME(),
        CAST(SELECT(t.charType).FROM(t).WHERE(AND(LIKE(t.charType, "%:%:%"), NOT.LIKE(t.charType, "% %:%:%"))).LIMIT(1)).AS.TIME()).
      FROM(t).
      WHERE(AND(LIKE(t.charType, "%:%:%"), NOT.LIKE(t.charType, "% %:%:%")))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testCharToDateTime(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.DATETIME> rows =
      SELECT(
        CAST(t.charType).AS.DATETIME(),
        CAST(SELECT(t.charType).FROM(t).WHERE(LIKE(t.charType, "%-%-% %:%:%")).LIMIT(1)).AS.DATETIME()).
      FROM(t).
      WHERE(LIKE(t.charType, "%-%-% %:%:%"))
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testCharToClob(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.CLOB> rows =
      SELECT(
        CAST(t.charType).AS.CLOB(254),
        CAST(SELECT(t.charType).FROM(t).LIMIT(1)).AS.CLOB(254)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testDateToChar(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.CHAR> rows =
      SELECT(
        CAST(t.dateType).AS.CHAR(254),
        CAST(SELECT(t.dateType).FROM(t).LIMIT(1)).AS.CHAR(254)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testTimeToChar(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.CHAR> rows =
      SELECT(
        CAST(t.timeType).AS.CHAR(254),
        CAST(SELECT(t.timeType).FROM(t).LIMIT(1)).AS.CHAR(254)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testTimeToTime(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.TIME> rows =
      SELECT(
        CAST(t.timeType).AS.TIME(),
        CAST(SELECT(t.timeType).FROM(t).LIMIT(1)).AS.TIME()).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testDateTimeToChar(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.CHAR> rows =
      SELECT(
        CAST(t.datetimeType).AS.CHAR(254),
        CAST(SELECT(t.datetimeType).FROM(t).LIMIT(1)).AS.CHAR(254)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testDateTimeToDate(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.DATE> rows =
      SELECT(
        CAST(t.datetimeType).AS.DATE(),
        CAST(SELECT(t.datetimeType).FROM(t).LIMIT(1)).AS.DATE()).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testDateTimeToTime(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.TIME> rows =
      SELECT(
        CAST(t.datetimeType).AS.TIME(),
        CAST(SELECT(t.datetimeType).FROM(t).LIMIT(1)).AS.TIME()).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testDateTimeToDateTime(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.DATETIME> rows =
      SELECT(
        CAST(t.datetimeType).AS.DATETIME(),
        CAST(SELECT(t.datetimeType).FROM(t).LIMIT(1)).AS.DATETIME()).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testClobToChar(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.CHAR> rows =
      SELECT(
        CAST(t.clobType).AS.CHAR(254),
        CAST(SELECT(t.clobType).FROM(t).LIMIT(1)).AS.CHAR(254)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testClobToClob(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.CLOB> rows =
      SELECT(
        CAST(t.clobType).AS.CLOB(254),
        CAST(SELECT(t.clobType).FROM(t).LIMIT(1)).AS.CLOB(254)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testBlobToBlob(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.BLOB> rows =
      SELECT(
        CAST(t.blobType).AS.BLOB(254),
        CAST(SELECT(t.blobType).FROM(t).LIMIT(1)).AS.BLOB(254)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testBinaryToBlob(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.BLOB> rows =
      SELECT(
        CAST(t.binaryType).AS.BLOB(254),
        CAST(SELECT(t.binaryType).FROM(t).LIMIT(1)).AS.BLOB(254)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }

  @Test
  public void testBinaryToBinary(@Schema(types.class) final Transaction transaction) throws IOException, SQLException {
    final types.Type t = types.Type();
    try (final RowIterator<type.BINARY> rows =
      SELECT(
        CAST(t.binaryType).AS.BINARY(254),
        CAST(SELECT(t.binaryType).FROM(t).LIMIT(1)).AS.BINARY(254)).
      FROM(t)
        .execute(transaction)) {
      assertTrue(rows.nextRow());
    }
  }
}