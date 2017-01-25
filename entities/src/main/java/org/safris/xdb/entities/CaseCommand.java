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

package org.safris.xdb.entities;

import java.io.IOException;

import org.safris.xdb.entities.Case.CASE_WHEN;
import org.safris.xdb.entities.Case.ELSE;
import org.safris.xdb.entities.Case.THEN;

final class CaseCommand extends Command {
  private CASE_WHEN<?> caseWhen;
  private THEN<?> then;
  private ELSE<?> els;

  protected CASE_WHEN<?> caseWhen() {
    return caseWhen;
  }

  protected void add(final CASE_WHEN<?> caseWhen) {
    this.caseWhen = caseWhen;
  }

  protected THEN<?> then() {
    return then;
  }

  protected void add(THEN<?> then) {
    this.then = then;
  }

  protected ELSE<?> els() {
    return els;
  }

  protected void add(final ELSE<?> els) {
    this.els = els;
  }

  @Override
  protected void serialize(final Serialization serialization) throws IOException {
  }
}