/* Copyright (c) 2017 Seva Safris
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

package org.safris.xdb.schema;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.safris.commons.lang.Resources;
import org.safris.commons.test.LoggableTest;
import org.safris.commons.xml.validate.ValidationException;
import org.safris.xdb.schema.vendor.Derby;
import org.safris.xdb.schema.vendor.MySQL;
import org.safris.xdb.schema.vendor.PostgreSQL;
import org.safris.xdb.xds.xe.xds_schema;
import org.safris.xsb.runtime.Bindings;
import org.safris.xsb.runtime.ParseException;
import org.xml.sax.InputSource;

@RunWith(VendorClassRunner.class)
@VendorTest(Derby.class)
@VendorIntegration({MySQL.class, PostgreSQL.class})
public class SchemaTest extends LoggableTest {
  @Test
  public void testClassicModels(final Connection connection) throws GeneratorExecutionException, IOException, ParseException, SQLException, ValidationException {
    final xds_schema schema;
    try (final InputStream in = Resources.getResource("classicmodels.xds").getURL().openStream()) {
      schema = (xds_schema)Bindings.parse(new InputSource(in));
    }

    Schemas.create(schema, connection);
  }

  @Test
  public void testWorld(final Connection connection) throws GeneratorExecutionException, IOException, ParseException, SQLException, ValidationException {
    final xds_schema schema;
    try (final InputStream in = Resources.getResource("world.xds").getURL().openStream()) {
      schema = (xds_schema)Bindings.parse(new InputSource(in));
    }

    Schemas.create(schema, connection);
  }

  @Test
  public void testTypes(final Connection connection) throws GeneratorExecutionException, IOException, ParseException, SQLException, ValidationException {
    final xds_schema schema;
    try (final InputStream in = Resources.getResource("types.xds").getURL().openStream()) {
      schema = (xds_schema)Bindings.parse(new InputSource(in));
    }

    Schemas.create(schema, connection);
  }
}