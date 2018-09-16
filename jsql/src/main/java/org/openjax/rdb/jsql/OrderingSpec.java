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

package org.openjax.rdb.jsql;

import java.io.IOException;
import java.util.Set;

final class OrderingSpec extends Evaluable {
  protected final operator.Ordering operator;
  protected final type.DataType<?> dataType;

  protected OrderingSpec(final operator.Ordering operator, final type.DataType<?> dataType) {
    this.operator = operator;
    this.dataType = dataType;
  }

  @Override
  protected final void compile(final Compilation compilation) throws IOException {
    Compiler.getCompiler(compilation.vendor).compile(this, compilation);
  }

  @Override
  protected Object evaluate(final Set<Evaluable> visited) {
    return dataType.evaluate(visited);
  }
}