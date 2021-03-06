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
import java.math.BigDecimal;
import java.sql.SQLException;

import org.jaxdb.jsql.RowIterator;
import org.jaxdb.jsql.Transaction;
import org.jaxdb.jsql.classicmodels;
import org.jaxdb.jsql.type;
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
public abstract class QueryExpressionTest {
  @VendorSchemaRunner.Vendor(value=Derby.class, parallel=2)
  @VendorSchemaRunner.Vendor(SQLite.class)
  public static class IntegrationTest extends QueryExpressionTest {
  }

  @VendorSchemaRunner.Vendor(MySQL.class)
  @VendorSchemaRunner.Vendor(PostgreSQL.class)
  @VendorSchemaRunner.Vendor(Oracle.class)
  public static class RegressionTest extends QueryExpressionTest {
  }

  @Test
  public void testObjectSelectFound(@Schema(classicmodels.class) final Transaction transaction) throws IOException, SQLException {
    classicmodels.Office o = new classicmodels.Office();
    o.address1.set("100 Market Street");
    o.city.set("San Francisco");
    o.locality.set("CA");
    try (final RowIterator<classicmodels.Office> rows =
      SELECT(o)
        .execute(transaction)) {

      assertTrue(rows.nextRow());
      o = rows.nextEntity();
      assertEquals("100 Market Street", o.address1.get());
      assertEquals("San Francisco", o.city.get());
      assertEquals("CA", o.locality.get());
      assertFalse(rows.nextRow());
    }
  }

  @Test
  public void testObjectSelectNotFound(@Schema(classicmodels.class) final Transaction transaction) throws IOException, SQLException {
    final classicmodels.Office o = new classicmodels.Office();
    o.address1.set("100 Market Street");
    o.city.set("San Francisco");
    o.locality.set("");
    try (final RowIterator<classicmodels.Office> rows =
      SELECT(o)
        .execute(transaction)) {

      assertFalse(rows.nextRow());
    }
  }

  @Test
  public void testFrom(@Schema(classicmodels.class) final Transaction transaction) throws IOException, SQLException {
    final classicmodels.Office o = classicmodels.Office();
    try (final RowIterator<classicmodels.Office> rows =
      SELECT(o).
      FROM(o)
        .execute(transaction)) {

      assertTrue(rows.nextRow());
      assertEquals("100 Market Street", rows.nextEntity().address1.get());
      assertTrue(rows.nextRow() && rows.nextRow() && rows.nextRow() && rows.nextRow() && rows.nextRow() && rows.nextRow());
      assertEquals("25 Old Broad Street", rows.nextEntity().address1.get());
      assertFalse(rows.nextRow());
    }
  }

  @Test
  public void testFromMultiple(@Schema(classicmodels.class) final Transaction transaction) throws IOException, SQLException {
    final classicmodels.Office o = classicmodels.Office();
    final classicmodels.Customer c = classicmodels.Customer();
    try (final RowIterator<classicmodels.Address> rows =
      SELECT(o, c).
      FROM(o, c)
        .execute(transaction)) {

      assertTrue(rows.nextRow());
      assertEquals("100 Market Street", rows.nextEntity().address1.get());
      assertEquals("54, rue Royale", rows.nextEntity().address1.get());
    }
  }

  @Test
  public void testWhere(@Schema(classicmodels.class) final Transaction transaction) throws IOException, SQLException {
    final classicmodels.Office o = classicmodels.Office();
    try (final RowIterator<? extends type.DataType<?>> rows =
      SELECT(o.address1, o.latitude).
      FROM(o).
      WHERE(AND(
        EQ(o.phone, 81332245000L),
        OR(GT(o.latitude, 20d),
          LT(o.longitude, 100d))))
        .execute(transaction)) {

      assertTrue(rows.nextRow());
      assertEquals("4-1 Kioicho", rows.nextEntity().get());
      assertEquals(35.6811759, ((BigDecimal)rows.nextEntity().get()).doubleValue(), 0.0000000001);
    }
  }
}