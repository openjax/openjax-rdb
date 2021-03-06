/* Copyright (c) 2015 JAX-DB
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
import java.util.ArrayList;
import java.util.List;

import org.jaxdb.jsql.Update.SET;
import org.jaxdb.jsql.type.Table;

final class UpdateImpl extends Command<type.DataType<?>> implements SET {
  private type.Table table;
  private List<Subject> sets;
  private Condition<?> where;

  UpdateImpl(final type.Table table) {
    this.table = table;
  }

  private void initSets() {
    if (sets == null)
      sets = new ArrayList<>();
  }

  @Override
  public final <T>UpdateImpl SET(final type.DataType<? extends T> column, final type.DataType<? extends T> to) {
    return set(column, to);
  }

  @Override
  public final <T>UpdateImpl SET(final type.DataType<T> column, final T to) {
    return set(column, to);
  }

  @Override
  public UpdateImpl WHERE(final Condition<?> condition) {
    return where(condition);
  }

  private UpdateImpl where(final Condition<?> where) {
    this.where = where;
    return this;
  }

  private <T>UpdateImpl set(final type.DataType<T> column, final T to) {
    initSets();
    sets.add(column);
    sets.add(type.DataType.wrap(to));
    return this;
  }

  private <T>UpdateImpl set(final type.DataType<? extends T> column, final Case.CASE<? extends T> to) {
    initSets();
    sets.add(column);
    sets.add((Subject)to);
    return this;
  }

  private <T>UpdateImpl set(final type.DataType<? extends T> column, final type.DataType<? extends T> to) {
    initSets();
    sets.add(column);
    sets.add(to);
    return this;
  }

  @Override
  final Table table() {
    return table;
  }

  @Override
  void compile(final Compilation compilation, final boolean isExpression) throws IOException, SQLException {
    final Compiler compiler = compilation.compiler;
    if (sets != null)
      compiler.compileUpdate(table, sets, where, compilation);
    else
      compiler.compileUpdate(table, compilation);
  }

  @Override
  public void close() {
    table = null;
    where = null;
    if (sets != null) {
      sets.clear();
      sets = null;
    }
  }
}