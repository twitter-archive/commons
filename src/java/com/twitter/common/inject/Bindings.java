// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed to the Apache Software Foundation (ASF) under one or more contributor license
// agreements.  See the NOTICE file distributed with this work for additional information regarding
// copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with the License.  You may
// obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express or implied.  See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.inject;

import com.google.common.base.Preconditions;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

/**
 * A utility that helps with guice bindings.
 *
 * @author John Sirois
 */
public final class Bindings {

  /**
   * Equivalent to calling {@code requireBinding(binder, Key.get(required, Names.named(namedKey)))}.
   */
  public static void requireNamedBinding(Binder binder, Class<?> required, String namedKey) {
    requireBinding(binder, Key.get(Preconditions.checkNotNull(required),
        Names.named(Preconditions.checkNotNull(namedKey))));
  }

  /**
   * Equivalent to calling {@code requireBinding(binder, Key.get(required))}.
   */
  public static void requireBinding(Binder binder, Class<?> required) {
    requireBinding(binder, Key.get(Preconditions.checkNotNull(required)));
  }

  /**
   * Registers {@code required} as non-optional dependency in the {@link com.google.inject.Injector}
   * associated with {@code binder}.
   *
   * @param binder a guice binder to require bindings against
   * @param required the dependency that is required
   */
  public static void requireBinding(Binder binder, final Key<?> required) {
    Preconditions.checkNotNull(binder);
    Preconditions.checkNotNull(required);

    binder.install(new AbstractModule() {
      @Override protected void configure() {
        requireBinding(required);
      }
    });
  }

  /**
   * A guice binding helper that allows for any combination of Class, TypeLiteral or Key binding
   * without forcing guiced implementation to provide all the overloaded binding methods they would
   * otherwise have to.
   *
   * @param <T> the type this helper can be used to bind implementations for
   */
  public interface BindHelper<T> {

    /**
     * Associates this BindHelper with an Injector instance.
     *
     * @param binder the binder for the injector implementations will be bound in
     * @return a binding builder that can be used to bind an implementation with
     */
    LinkedBindingBuilder<T> with(Binder binder);
  }

  /**
   * Creates a BindHelper for the given binding key that can be used to bind a single instance.
   *
   * @param key the binding key the returned BindHelper can be use to bind implementations for
   * @param <T> the type the returned BindHelper can be used to bind implementations for
   * @return a BindHelper that can be used to bind an implementation with
   */
  public static <T> BindHelper<T> binderFor(final Key<T> key) {
    return new BindHelper<T>() {
      public LinkedBindingBuilder<T> with(Binder binder) {
        return binder.bind(key);
      }
    };
  }

  /**
   * Creates a BindHelper for the given type that can be used to add a binding of to a set.
   *
   * @param type the type the returned BindHelper can be use to bind implementations for
   * @param <T> the type the returned BindHelper can be used to bind implementations for
   * @return a BindHelper that can be used to bind an implementation with
   */
  public static <T> BindHelper<T> multiBinderFor(final Class<T> type) {
    return new BindHelper<T>() {
      public LinkedBindingBuilder<T> with(Binder binder) {
        return Multibinder.newSetBinder(binder, type).addBinding();
      }
    };
  }

  private Bindings() {
    // utility
  }
}
