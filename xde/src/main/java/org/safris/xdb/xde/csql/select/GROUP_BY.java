/* Copyright (c) 2015 Seva Safris
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

package org.safris.xdb.xde.csql.select;

import org.safris.xdb.xde.csql.Entity;

public interface GROUP_BY<T extends Entity> extends SELECT<T>, _LIMIT<T>, _HAVING<T> {
}

interface _GROUP_BY<T extends Entity> {
  public GROUP_BY<T> GROUP_BY(final org.safris.xdb.xde.Column<?> column);
}