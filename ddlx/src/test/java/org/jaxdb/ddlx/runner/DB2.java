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

package org.jaxdb.ddlx.runner;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.openjax.ext.sql.AuditConnection;
import org.jaxdb.vendor.DBVendor;

import com.ibm.db2.jcc.DB2BaseDataSource;
import com.ibm.db2.jcc.DB2Driver;

public class DB2 implements Vendor {
  @Override
  public DBVendor getDBVendor() {
    return DBVendor.DB2;
  }

  @Override
  @SuppressWarnings("unused")
  public synchronized void init() throws IOException, SQLException {
    new DB2Driver();
  }

  @Override
  public Connection getConnection() throws SQLException {
    return new AuditConnection(DriverManager.getConnection("jdbc:db2://localhost:50001/jaxdb:user=jaxdb;password=jaxdb;traceLevel=" + DB2BaseDataSource.TRACE_ALL + ";"));
  }

  @Override
  public void destroy() throws SQLException {
  }
}