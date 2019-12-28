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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

import org.jaxdb.vendor.DBVendor;
import org.libj.sql.exception.SQLExceptions;
import org.libj.sql.exception.SQLInvalidSchemaNameException;
import org.libj.util.ConcurrentHashSet;

public abstract class Schema {
  private static final ConcurrentHashSet<Class<? extends Schema>> registered = new ConcurrentHashSet<>();
  private static final ConcurrentHashMap<Connection,Boolean> connected = new ConcurrentHashMap<>();

  static DBVendor getDBVendor(final Connection connection) throws SQLException {
    if (connection == null)
      return null;

    try {
      final String url = connection.getMetaData().getURL();
      if (url.contains("jdbc:sqlite"))
        return DBVendor.SQLITE;

      if (url.contains("jdbc:derby"))
        return DBVendor.DERBY;

      if (url.contains("jdbc:mariadb"))
        return DBVendor.MARIA_DB;

      if (url.contains("jdbc:mysql"))
        return DBVendor.MY_SQL;

      if (url.contains("jdbc:oracle"))
        return DBVendor.ORACLE;

      if (url.contains("jdbc:postgresql"))
        return DBVendor.POSTGRE_SQL;
    }
    catch (final SQLException e) {
      throw SQLExceptions.getStrongType(e);
    }

    return null;
  }

  private static final Class<? extends Schema> NULL = Schema.class;

  static Connection getConnection(final Class<? extends Schema> schema, final String dataSourceId) throws SQLException {
    final Connector dataSource = Registry.getDataSource(schema, dataSourceId);
    if (dataSource == null)
      throw new SQLInvalidSchemaNameException("A " + Connector.class.getSimpleName() + " has not been registered for " + (schema == null ? null : schema.getName()) + ", id: " + dataSourceId);

    try {
      final Connection connection = dataSource.getConnection();
      if (!connected.containsKey(connection)) {
        synchronized (connection) {
          if (!connected.containsKey(connection)) {
            Compiler.getCompiler(getDBVendor(connection)).onConnect(connection);
            connected.put(connection, Boolean.TRUE);
          }
        }
      }

      final Class<? extends Schema> key = schema != null ? schema : NULL;
      if (!registered.contains(key)) {
        synchronized (key) {
          if (!registered.contains(key)) {
            Compiler.getCompiler(getDBVendor(connection)).onRegister(connection);
            registered.add(key);
          }
        }
      }

      return connection;
    }
    catch (final SQLException e) {
      throw SQLExceptions.getStrongType(e);
    }
  }
}