/* Copyright (c) 2017 JAX-DB
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, variable to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * You should have received a copy of The MIT License (MIT) along with this
 * program. If not, see <http://opensource.org/licenses/MIT/>.
 */

package org.jaxdb.jsql;

import java.io.IOException;
import java.util.Set;

final class As<T> extends type.Subject<T> {
  private final Evaluable parent;
  private final type.Subject<?> variable;
  private final boolean explicit;

  As(final Keyword<? extends type.Subject<?>> parent, final type.Subject<?> variable, final boolean explicit) {
    this.parent = parent;
    this.variable = variable;
    this.explicit = explicit;
  }

  As(final type.Subject<T> parent, final type.Subject<?> variable) {
    this.parent = parent;
    this.variable = variable;
    this.explicit = true;
  }

  Evaluable parent() {
    return parent;
  }

  type.Subject<?> getVariable() {
    return variable;
  }

  boolean isExplicit() {
    return this.explicit;
  }

  @Override
  void compile(final Compilation compilation) throws IOException {
    Compiler.getCompiler(compilation.vendor).compile(this, compilation);
  }

  @Override
  Object evaluate(final Set<Evaluable> visited) {
    return variable.evaluate(visited);
  }
}