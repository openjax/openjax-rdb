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

import org.jaxdb.ddlx.runner.Derby;
import org.jaxdb.ddlx.runner.MySQL;
import org.jaxdb.ddlx.runner.Oracle;
import org.jaxdb.ddlx.runner.PostgreSQL;
import org.jaxdb.ddlx.runner.SQLite;
import org.jaxdb.jsql.RowIterator;
import org.jaxdb.jsql.classicmodels;
import org.jaxdb.jsql.type;
import org.jaxdb.runner.VendorSchemaRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

public abstract class QuantifiedComparisonPredicateTest {
  @RunWith(VendorSchemaRunner.class)
  @VendorSchemaRunner.Schema(classicmodels.class)
  @VendorSchemaRunner.Vendor({Derby.class, SQLite.class})
  public static class IntegrationTest extends QuantifiedComparisonPredicateTest {
  }

  @RunWith(VendorSchemaRunner.class)
  @VendorSchemaRunner.Schema(classicmodels.class)
  @VendorSchemaRunner.Vendor({MySQL.class, PostgreSQL.class, Oracle.class})
  public static class RegressionTest extends QuantifiedComparisonPredicateTest {
  }

  @Test
  @VendorSchemaRunner.Unsupported(SQLite.class)
  public void testAll() throws IOException, SQLException {
    final classicmodels.Purchase p = new classicmodels.Purchase();
    final classicmodels.Customer c = new classicmodels.Customer();
    try (final RowIterator<type.INT> rows =
      SELECT(COUNT()).
      FROM(c).
      WHERE(
        LT(c.creditLimit,
          ALL(SELECT(COUNT()).
            FROM(p).
            WHERE(NE(p.purchaseDate, p.shippedDate))))).
      execute()) {
      assertTrue(rows.nextRow());
      assertEquals(Integer.valueOf(24), rows.nextEntity().get());
    }
  }

  @Test
  @VendorSchemaRunner.Unsupported(SQLite.class)
  public void testAny() throws IOException, SQLException {
    final classicmodels.Purchase p = new classicmodels.Purchase();
    final classicmodels.Customer c = new classicmodels.Customer();
    try (final RowIterator<type.INT> rows =
      SELECT(COUNT()).
      FROM(c).
      WHERE(
        GT(c.customerNumber,
          ANY(SELECT(COUNT()).
            FROM(p).
            WHERE(GT(p.purchaseDate, p.shippedDate))))).
      execute()) {
      assertTrue(rows.nextRow());
      assertTrue(rows.nextEntity().get() > 100);
    }
  }

  @Test
  @VendorSchemaRunner.Unsupported(SQLite.class)
  public void testSome() throws IOException, SQLException {
    final classicmodels.Purchase p = new classicmodels.Purchase();
    final classicmodels.Customer c = new classicmodels.Customer();
    try (final RowIterator<type.INT> rows =
      SELECT(COUNT()).
      FROM(c).
      WHERE(
        GT(c.customerNumber,
          SOME(SELECT(COUNT()).
            FROM(p).
            WHERE(LT(p.purchaseDate, p.shippedDate))))).
      execute()) {
      assertTrue(rows.nextRow());
      assertTrue(rows.nextEntity().get() > 50);
    }
  }
}