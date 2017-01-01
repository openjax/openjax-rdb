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

public final class Binary extends DataType<byte[]> {
  protected static byte[] get(final ResultSet resultSet, final int columnIndex) throws SQLException {
    final int columnType = resultSet.getMetaData().getColumnType(columnIndex);
    if (columnType == Types.BIT)
      return new byte[] {resultSet.getBoolean(columnIndex) ? (byte)0x01 : (byte)0x00};

    return resultSet.getBytes(columnIndex);
  }

  protected static void set(final PreparedStatement statement, final int parameterIndex, final byte[] value) throws SQLException {
    if (value != null)
      statement.setBytes(parameterIndex, value);
    else
      statement.setNull(parameterIndex, statement.getParameterMetaData().getParameterType(parameterIndex)); // FIXME: Does it matter if we know if this is BIT, BINARY, VARBINARY, or LONGVARBINARY?
  }

  public final int length;
  public final boolean varying;

  public Binary(final Entity owner, final String specName, final String name, final byte[] _default, final boolean unique, final boolean primary, final boolean nullable, final GenerateOn<byte[]> generateOnInsert, final GenerateOn<byte[]> generateOnUpdate, final int length, final boolean varying) {
    super(varying ? Types.VARBINARY : Types.BINARY, byte[].class, owner, specName, name, _default, unique, primary, nullable, generateOnInsert, generateOnUpdate);
    this.length = length;
    this.varying = varying;
  }

  public Binary() {
    this(null, null, null, null, false, false, true, null, null, Integer.MAX_VALUE, true);
  }

  protected Binary(final Binary copy) {
    super(copy);
    this.length = copy.length;
    this.varying = copy.varying;
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