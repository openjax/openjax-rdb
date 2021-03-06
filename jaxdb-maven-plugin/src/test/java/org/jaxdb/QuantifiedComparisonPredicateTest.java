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
public abstract class QuantifiedComparisonPredicateTest {
  @VendorSchemaRunner.Vendor(value=Derby.class, parallel=2)
  @VendorSchemaRunner.Vendor(SQLite.class)
  public static class IntegrationTest extends QuantifiedComparisonPredicateTest {
  }

  @VendorSchemaRunner.Vendor(MySQL.class)
  @VendorSchemaRunner.Vendor(PostgreSQL.class)
  @VendorSchemaRunner.Vendor(Oracle.class)
  public static class RegressionTest extends QuantifiedComparisonPredicateTest {
  }

  @Test
  @VendorSchemaRunner.Unsupported(SQLite.class)
  public void testAll(@Schema(classicmodels.class) final Transaction transaction) throws IOException, SQLException {
    final classicmodels.Purchase p = classicmodels.Purchase();
    final classicmodels.Customer c = classicmodels.Customer();

    try (final RowIterator<type.BIGINT> rows =
      SELECT(COUNT(c)).
      FROM(c).
      WHERE(LT(c.creditLimit, ALL(
        SELECT(COUNT(p)).
        FROM(p).
        WHERE(NE(p.purchaseDate, p.shippedDate)))))
          .execute(transaction)) {

      assertTrue(rows.nextRow());
      assertEquals(24, rows.nextEntity().getAsLong());
    }
  }

  @Test
  @VendorSchemaRunner.Unsupported(SQLite.class)
  public void testAny(@Schema(classicmodels.class) final Transaction transaction) throws IOException, SQLException {
    final classicmodels.Purchase p = classicmodels.Purchase();
    final classicmodels.Customer c = classicmodels.Customer();

    try (final RowIterator<type.BIGINT> rows =
      SELECT(COUNT(c)).
      FROM(c).
      WHERE(GT(c.customerNumber, ANY(
        SELECT(COUNT(p)).
        FROM(p).
        WHERE(GT(p.purchaseDate, p.shippedDate)))))
          .execute(transaction)) {

      assertTrue(rows.nextRow());
      assertTrue(rows.nextEntity().getAsLong() > 100);
    }
  }

  @Test
  @VendorSchemaRunner.Unsupported(SQLite.class)
  public void testSome(@Schema(classicmodels.class) final Transaction transaction) throws IOException, SQLException {
    final classicmodels.Purchase p = classicmodels.Purchase();
    final classicmodels.Customer c = classicmodels.Customer();

    try (final RowIterator<type.BIGINT> rows =
      SELECT(COUNT(c)).
      FROM(c).
      WHERE(GT(c.customerNumber, SOME(
        SELECT(COUNT(p)).
        FROM(p).
        WHERE(LT(p.purchaseDate, p.shippedDate)))))
          .execute(transaction)) {

      assertTrue(rows.nextRow());
      assertTrue(rows.nextEntity().getAsLong() > 50);
    }
  }
}