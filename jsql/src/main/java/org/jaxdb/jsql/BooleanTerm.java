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

import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;

import org.jaxdb.vendor.DBVendor;

final class BooleanTerm extends type.BOOLEAN {
  final operator.Boolean operator;
  final Condition<?> a;
  final Condition<?> b;
  final Condition<?>[] conditions;

  @SafeVarargs
  BooleanTerm(final operator.Boolean operator, final Condition<?> a, final Condition<?> b, final Condition<?> ... conditions) {
    this.operator = operator;
    this.a = a;
    this.b = b;
    this.conditions = conditions;
  }

  @Override
  final Boolean evaluate(final Set<Evaluable> visited) {
    if (a == null || b == null || a.evaluate(visited) == null || b.evaluate(visited) == null)
      return null;

    for (int i = 0; i < conditions.length; i++)
      if (conditions[i] == null)
        return null;

    for (int i = 0; i < conditions.length; i++) {
      final Object evaluated = conditions[i].evaluate(visited);
      if (evaluated == null)
        return null;

      if (!(evaluated instanceof Boolean))
        throw new AssertionError();

      if (!(Boolean)evaluated)
        return Boolean.FALSE;
    }

    return Boolean.TRUE;
  }

  @Override
  final String compile(final DBVendor vendor) {
    return operator.toString();
  }

  @Override
  final void compile(final Compilation compilation, final boolean isExpression) throws IOException, SQLException {
    compilation.compiler.compile(this, compilation);
  }
}