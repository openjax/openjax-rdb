/* Copyright (c) 2017 OpenJAX
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

package org.openjax.maven.plugin.rdb.jsql;

import static org.openjax.rdb.jsql.DML.*;

import java.io.IOException;
import java.sql.SQLException;

import org.fastjax.test.MixedTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.openjax.rdb.ddlx.runner.Derby;
import org.openjax.rdb.ddlx.runner.MySQL;
import org.openjax.rdb.ddlx.runner.Oracle;
import org.openjax.rdb.ddlx.runner.PostgreSQL;
import org.openjax.rdb.ddlx.runner.SQLite;
import org.openjax.rdb.jsql.RowIterator;
import org.openjax.rdb.jsql.classicmodels;
import org.openjax.rdb.jsql.type;
import org.openjax.maven.plugin.rdb.jsql.runner.VendorSchemaRunner;

@RunWith(VendorSchemaRunner.class)
@VendorSchemaRunner.Schema(classicmodels.class)
@VendorSchemaRunner.Test({Derby.class, SQLite.class})
@VendorSchemaRunner.Integration({MySQL.class, PostgreSQL.class, Oracle.class})
@Category(MixedTest.class)
public class BooleanValueExpressionTest {
  @Test
  public void test() throws IOException, SQLException {
    final classicmodels.Product p = new classicmodels.Product();
    try (final RowIterator<type.BOOLEAN> rows =
      SELECT(
        EQ(p.price, p.msrp),
        LT(p.price, p.msrp),
        AND(EQ(p.price, p.msrp),
          GT(p.price, p.msrp)),
        OR(EQ(p.price, p.msrp),
          GT(p.price, p.msrp)),
        SELECT(EQ(p.price, p.msrp)).
          FROM(p).
          WHERE(AND(LIKE(p.name, "%Ford%"), GT(p.quantityInStock, 100))).LIMIT(1)).
      FROM(p).
      WHERE(AND(LIKE(p.name, "%Ford%"), GT(p.quantityInStock, 100))).
      execute()) {
      Assert.assertTrue(rows.nextRow());
      Assert.assertEquals(false, rows.nextEntity().get());
      Assert.assertEquals(true, rows.nextEntity().get());
      Assert.assertEquals(false, rows.nextEntity().get());
      Assert.assertEquals(false, rows.nextEntity().get());
    }
  }
}