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

package org.jaxdb.ddlx;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import org.jaxdb.runner.Derby;
import org.jaxdb.runner.MySQL;
import org.jaxdb.runner.Oracle;
import org.jaxdb.runner.PostgreSQL;
import org.jaxdb.runner.SQLite;
import org.jaxdb.runner.VendorRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xml.sax.SAXException;

@RunWith(VendorRunner.class)
public abstract class ImplicitTest extends DDLxTest {
  @VendorRunner.Vendor(value=Derby.class, parallel=2)
  @VendorRunner.Vendor(SQLite.class)
  public static class IntegrationTest extends ImplicitTest {
  }

  @VendorRunner.Vendor(MySQL.class)
  @VendorRunner.Vendor(PostgreSQL.class)
  @VendorRunner.Vendor(Oracle.class)
  public static class RegressionTest extends ImplicitTest {
  }

  @Test
  public void test(final Connection connection) throws GeneratorExecutionException, IOException, SAXException, SQLException {
    recreateSchema(connection, "implicit");
  }
}