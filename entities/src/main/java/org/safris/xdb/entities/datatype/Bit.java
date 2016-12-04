/* Copyright (c) 2014 Seva Safris
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

package org.safris.xdb.entities.datatype;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.safris.xdb.entities.DataType;
import org.safris.xdb.entities.Entity;
import org.safris.xdb.entities.GenerateOn;
import org.safris.xdb.schema.DBVendor;

public final class Bit extends DataType<String> {
  protected static final int sqlType = Types.VARCHAR;

  protected static String get(final ResultSet resultSet, final int columnIndex) throws SQLException {
    return resultSet.getString(columnIndex);
  }

  protected static void set(final PreparedStatement statement, final int parameterIndex, final String value) throws SQLException {
    if (value != null)
      statement.setString(parameterIndex, value);
    else
      statement.setNull(parameterIndex, sqlType);
  }

  public final int length;
  public final boolean varyant;

  public Bit(final Entity owner, final String specName, final String name, final String _default, final boolean unique, final boolean primary, final boolean nullable, final GenerateOn<String> generateOnInsert, final GenerateOn<String> generateOnUpdate, final int length, final boolean varyant) {
    super(sqlType, String.class, owner, specName, name, _default, unique, primary, nullable, generateOnInsert, generateOnUpdate);
    this.length = length;
    this.varyant = varyant;
  }

  protected Bit(final Bit copy) {
    super(copy);
    this.length = copy.length;
    this.varyant = copy.varyant;
  }

  @Override
  protected String getPreparedStatementMark(final DBVendor vendor) {
    return "?";
  }

  @Override
  protected void get(final PreparedStatement statement, final int parameterIndex) throws SQLException {
    set(statement, parameterIndex, get());
  }

  @Override
  protected void set(final ResultSet resultSet, final int columnIndex) throws SQLException {
    this.value = get(resultSet, columnIndex);
  }
}