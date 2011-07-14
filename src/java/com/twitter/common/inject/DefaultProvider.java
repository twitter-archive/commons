// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this work except in compliance with the License.
// You may obtain a copy of the License in the LICENSE file, or at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.inject;

import com.google.common.base.Preconditions;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

/**
 * Provider that has a default value which can be overridden.
 *
 * The intended use of this class is:
 * <pre>
 * Default installer:
 *   bind(DefaultProvider.makeDefaultKey(Runnable.class, "mykey").toInstance(defaultRunnable);
 *   DefaultProvider.bindOrElse(Runnable.class, "mykey", binder());
 *
 * Custom override:
 *     bind(DefaultProvider.makeCustomKey(Runnable.class, "mykey")).toInstance(myCustomRunnable);
 *
 * Injection:
 *     {@literal Inject} Named("myKey") Runnable runnable;
 *
 * </pre>
 *
 * @param <T> the type of object this provides
 *
 * @author William Farner
 * @author John Sirois
 */
public class DefaultProvider<T> implements Provider<T> {
  private static final String DEFAULT_BINDING_KEY_SUFFIX = "_default";
  private static final String CUSTOM_BINDING_KEY_SUFFIX = "_custom";

  private final Key<T> defaultProviderKey;
  private final Key<T> customProviderKey;

  private Injector injector;

  public DefaultProvider(Key<T> defaultProviderKey, Key<T> customProviderKey) {
    this.defaultProviderKey = Preconditions.checkNotNull(defaultProviderKey);
    this.customProviderKey = Preconditions.checkNotNull(customProviderKey);
    Preconditions.checkArgument(!defaultProviderKey.equals(customProviderKey));
  }

  @Inject
  public void setInjector(Injector injector) {
    this.injector = injector;
  }

  @Override
  public T get() {
     Preconditions.checkNotNull(injector);
     return injector.getBindings().containsKey(customProviderKey)
         ? injector.getInstance(customProviderKey)
         : injector.getInstance(defaultProviderKey);
  }

  /**
   * Creates a DefaultProvider and installs a new module to {@code binder}, which will serve as
   * an indirection layer for swapping the default binding with a custom one.
   *
   * @param customBinding The custom binding key.
   * @param defaultBinding The default binding key.
   * @param exposedBinding The exposed binding key.
   * @param binder The binder to install bindings to.
   * @param <T> The type of binding to make.
   */
  public static <T> void bindOrElse(final Key<T> customBinding, final Key<T> defaultBinding,
      final Key<T> exposedBinding, Binder binder) {
    Preconditions.checkNotNull(customBinding);
    Preconditions.checkNotNull(defaultBinding);
    Preconditions.checkNotNull(exposedBinding);
    Preconditions.checkArgument(!customBinding.equals(defaultBinding)
        && !customBinding.equals(exposedBinding));

    binder.install(new AbstractModule() {
      @Override protected void configure() {
        Provider<T> defaultProvider = new DefaultProvider<T>(defaultBinding, customBinding);
        requestInjection(defaultProvider);
        bind(exposedBinding).toProvider(defaultProvider);
      }
    });
  }

  /**
   * Convenience function for creating and installing a DefaultProvider.  This will use internal
   * suffixes to create names for the custom and default bindings.  When bound this way, callers
   * should use one of the functions such as {@link #makeDefaultBindingKey(String)} to set default
   * and custom bindings.
   *
   * @param type The type of object to bind.
   * @param exposedKey The exposed key.
   * @param binder The binder to install to.
   * @param <T> The type of binding to make.
   */
  public static <T> void bindOrElse(TypeLiteral<T> type, String exposedKey, Binder binder) {
    bindOrElse(Key.get(type, Names.named(makeCustomBindingKey(exposedKey))),
        Key.get(type, Names.named(makeDefaultBindingKey(exposedKey))),
        Key.get(type, Names.named(exposedKey)),
        binder);
  }

  /**
   * Convenience method for calls to {@link #bindOrElse(TypeLiteral, String, Binder)}, that are not
   * binding a parameterized type.
   *
   * @param type The class of the object to bind.
   * @param exposedKey The exposed key.
   * @param binder The binder to install to.
   * @param <T> The type of binding to make.
   */
  public static <T> void bindOrElse(Class<T> type, String exposedKey, Binder binder) {
    bindOrElse(TypeLiteral.get(type), exposedKey, binder);
  }

  public static String makeDefaultBindingKey(String rootKey) {
    return rootKey + DEFAULT_BINDING_KEY_SUFFIX;
  }

  public static Named makeDefaultBindingName(String rootKey) {
    return Names.named(makeDefaultBindingKey(rootKey));
  }

  public static <T> Key<T> makeDefaultKey(TypeLiteral<T> type, String rootKey) {
    return Key.get(type, makeDefaultBindingName(rootKey));
  }

  public static <T> Key<T> makeDefaultKey(Class<T> type, String rootKey) {
    return makeDefaultKey(TypeLiteral.get(type), rootKey);
  }

  public static String makeCustomBindingKey(String rootKey) {
    return rootKey + CUSTOM_BINDING_KEY_SUFFIX;
  }

  public static Named makeCustomBindingName(String rootKey) {
    return Names.named(makeCustomBindingKey(rootKey));
  }

  public static <T> Key<T> makeCustomKey(Class<T> type, String rootKey) {
    return Key.get(type, makeCustomBindingName(rootKey));
  }

  public static <T> Key<T> makeCustomKey(TypeLiteral<T> type, String rootKey) {
    return Key.get(type, makeCustomBindingName(rootKey));
  }
}
