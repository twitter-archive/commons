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

import java.lang.annotation.Annotation;

import javax.inject.Qualifier;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.BindingAnnotation;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

/**
 * A utility that helps with guice bindings.
 *
 * @author John Sirois
 */
public final class Bindings {


  private Bindings() {
    // utility
  }

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
   * @param binder A binder to require bindings against.
   * @param required The dependency that is required.
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
   * A convenient version of {@link #exposing(Iterable, com.google.inject.Module)} when you just
   * want to expose a single binding.
   */
  public static Module exposing(Key<?> key, Module module) {
    return exposing(ImmutableList.of(key), module);
  }

  /**
   * Creates a module that hides all the given module's bindings and only exposes bindings for
   * the given key.
   *
   * @param keys The keys of the bindings to expose.
   * @param module The module to hide most bindings for.
   * @return A limited visibility module.
   */
  public static Module exposing(final Iterable<? extends Key<?>> keys, final Module module) {
    Preconditions.checkNotNull(keys);
    Preconditions.checkNotNull(module);

    return new PrivateModule() {
      @Override protected void configure() {
        install(module);
        for (Key<?> key : keys) {
          expose(key);
        }
      }
    };
  }

  /**
   * A guice binding helper that allows for any combination of Class, TypeLiteral or Key binding
   * without forcing guiced implementation to provide all the overloaded binding methods they would
   * otherwise have to.
   *
   * @param <T> The type this helper can be used to bind implementations for.
   */
  public interface BindHelper<T> {

    /**
     * Associates this BindHelper with an Injector instance.
     *
     * @param binder The binder for the injector implementations will be bound in.
     * @return A binding builder that can be used to bind an implementation with.
     */
    LinkedBindingBuilder<T> with(Binder binder);
  }

  /**
   * Creates a BindHelper for the given binding key that can be used to bind a single instance.
   *
   * @param key The binding key the returned BindHelper can be use to bind implementations for.
   * @param <T> The type the returned BindHelper can be used to bind implementations for.
   * @return A BindHelper that can be used to bind an implementation with.
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
   * @param type The type the returned BindHelper can be use to bind implementations for.
   * @param <T> The type the returned BindHelper can be used to bind implementations for.
   * @return A BindHelper that can be used to bind an implementation with.
   */
  public static <T> BindHelper<T> multiBinderFor(final Class<T> type) {
    return new BindHelper<T>() {
      public LinkedBindingBuilder<T> with(Binder binder) {
        return Multibinder.newSetBinder(binder, type).addBinding();
      }
    };
  }

  /**
   * Checks that the given annotation instance is a {@link BindingAnnotation @BindingAnnotation}.
   *
   * @param annotation The annotation instance to check.
   * @param <T> The type of the binding annotation.
   * @return The checked binding annotation.
   * @throws NullPointerException If the given {@code annotation} is null.
   * @throws IllegalArgumentException If the given {@code annotation} is not a
   *     {@literal @BindingAnnotation}.
   */
  public static <T extends Annotation> T checkBindingAnnotation(T annotation) {
    Preconditions.checkNotNull(annotation);
    checkBindingAnnotation(annotation.annotationType());
    return annotation;
  }

  /**
   * Checks that the given annotation type is a {@link BindingAnnotation @BindingAnnotation}.
   *
   * @param annotationType The annotation type to check.
   * @param <T> The type of the binding annotation.
   * @return The checked binding annotation type.
   * @throws NullPointerException If the given {@code annotationType} is null.
   * @throws IllegalArgumentException If the given {@code annotationType} is not a
   *     {@literal @BindingAnnotation}.
   */
  public static <T extends Annotation> Class<T> checkBindingAnnotation(Class<T> annotationType) {
    Preconditions.checkNotNull(annotationType);
    boolean bindingAnnotation = annotationType.isAnnotationPresent(BindingAnnotation.class);
    boolean qualifier = annotationType.isAnnotationPresent(Qualifier.class);
    Preconditions.checkArgument(bindingAnnotation || qualifier,
        "%s is not a @BindingAnnotation or @Qualifier", annotationType);
    return annotationType;
  }

  /**
   * A factory for binding {@link Key keys}.
   */
  public interface KeyFactory {

    /**
     * Creates plain un-annotated keys.
     */
    KeyFactory PLAIN = new KeyFactory() {
      @Override public <T> Key<T> create(Class<T> type) {
        return Key.get(type);
      }
      @Override public <T> Key<T> create(TypeLiteral<T> type) {
        return Key.get(type);
      }
    };

    /**
     * Creates a key for the given type.
     *
     * @param type The type to create a key for.
     * @param <T> The keyed type.
     * @return A key.
     */
    <T> Key<T> create(Class<T> type);

    /**
     * Creates a key for the given type.
     *
     * @param type The type to create a key for.
     * @param <T> The keyed type.
     * @return A key.
     */
    <T> Key<T> create(TypeLiteral<T> type);
  }

  /**
   * Creates a key factory that produces keys for a given annotation instance.
   *
   * @param annotation The annotation instance to apply to all keys.
   * @return A key factory that creates annotated keys.
   */
  public static KeyFactory annotatedKeyFactory(final Annotation annotation) {
    checkBindingAnnotation(annotation);
    return new KeyFactory() {
      @Override public <T> Key<T> create(Class<T> type) {
        return Key.get(type, annotation);
      }
      @Override public <T> Key<T> create(TypeLiteral<T> type) {
        return Key.get(type, annotation);
      }
    };
  }

  /**
   * Creates a key factory that produces keys for a given annotation type.
   *
   * @param annotationType The annotation type to apply to all keys.
   * @return A key factory that creates annotated keys.
   */
  public static KeyFactory annotatedKeyFactory(final Class<? extends Annotation> annotationType) {
    checkBindingAnnotation(annotationType);
    return new KeyFactory() {
      @Override public <T> Key<T> create(Class<T> type) {
        return Key.get(type, annotationType);
      }
      @Override public <T> Key<T> create(TypeLiteral<T> type) {
        return Key.get(type, annotationType);
      }
    };
  }

  /**
   * A utility that helps rebind keys.
   */
  public static final class Rebinder {
    private final Binder binder;
    private final KeyFactory bindToFactory;

    /**
     * Creates a Rebinder that links bindings to keys from the given {@code bindToFactory}.
     *
     * @param binder A binder to rebind keys in.
     * @param bindToFactory A factory for the rebinding key.
     */
    public Rebinder(Binder binder, KeyFactory bindToFactory) {
      this.binder = Preconditions.checkNotNull(binder);
      this.bindToFactory = Preconditions.checkNotNull(bindToFactory);
    }

    /**
     * Rebinds the given key to another, linking bindings.
     *
     * @param fromKey The source key to rebind.
     * @return The key that {@code key} was rebound to.
     */
    public <T> Key<T> rebind(Key<T> fromKey) {
      Key<T> toKey = bindToFactory.create(fromKey.getTypeLiteral());
      binder.bind(toKey).to(fromKey);
      requireBinding(binder, fromKey);
      return toKey;
    }
  }

  /**
   * Creates a Rebinder that rebinds keys to the given annotation instance.
   *
   * @param binder A binder to rebind keys in.
   * @param annotation The annotation instance to rebind keys to.
   * @return A Rebinder targeting the given {@code annotationType}.
   */
  public static Rebinder rebinder(Binder binder, Annotation annotation) {
    return new Rebinder(binder, annotatedKeyFactory(annotation));
  }

  /**
   * Creates a Rebinder that rebinds keys to the given annotation type.
   *
   * @param binder A binder to rebind keys in.
   * @param annotationType The annotation type to rebind keys to.
   * @return A Rebinder targeting the given {@code annotationType}.
   */
  public static Rebinder rebinder(Binder binder, Class<? extends Annotation> annotationType) {
    return new Rebinder(binder, annotatedKeyFactory(annotationType));
  }
}
