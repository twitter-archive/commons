package com.twitter.common.inject;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.util.List;

import javax.inject.Qualifier;

import com.google.inject.AbstractModule;
import com.google.inject.BindingAnnotation;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import org.junit.Test;

import com.twitter.common.inject.Bindings.KeyFactory;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BindingsTest {

  private static final Named NAME_KEY = Names.named("fred");
  private static final TypeLiteral<List<String>> STRING_LIST = new TypeLiteral<List<String>>() { };

  @Retention(RUNTIME)
  @BindingAnnotation
  @interface BindKey { }

  @Retention(RUNTIME)
  @Qualifier
  @interface QualifierKey { }

  @Test
  public void testCheckBindingAnnotation() {
    Bindings.checkBindingAnnotation(NAME_KEY);
    Bindings.checkBindingAnnotation(BindKey.class);

    try {
      Bindings.checkBindingAnnotation((Class<? extends Annotation>) null);
      fail();
    } catch (NullPointerException e) {
      // expected
    }

    try {
      Bindings.checkBindingAnnotation((Annotation) null);
      fail();
    } catch (NullPointerException e) {
      // expected
    }

    try {
      Bindings.checkBindingAnnotation(BindingAnnotation.class);
      fail();
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  public BindingsTest() {
    super();    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Test
  public void testPlainKeyFactory() {
    assertEquals(Key.get(String.class), KeyFactory.PLAIN.create(String.class));
    assertEquals(Key.get(STRING_LIST), KeyFactory.PLAIN.create(STRING_LIST));
  }

  @Test
  public void testAnnotationKeyFactory() {
    KeyFactory factory = Bindings.annotatedKeyFactory(NAME_KEY);
    assertEquals(Key.get(String.class, NAME_KEY), factory.create(String.class));
    assertEquals(Key.get(STRING_LIST, NAME_KEY), factory.create(STRING_LIST));
  }

  @Test
  public void testAnnotationKeyFactoryJsr330() {
    KeyFactory factory = Bindings.annotatedKeyFactory(NAME_KEY);
    assertEquals(Key.get(String.class, NAME_KEY), factory.create(String.class));
    assertEquals(Key.get(STRING_LIST, NAME_KEY), factory.create(STRING_LIST));
  }

  @Test
  public void testAnnotationTypeKeyFactory() {
    KeyFactory factory = Bindings.annotatedKeyFactory(QualifierKey.class);
    assertEquals(Key.get(String.class, QualifierKey.class), factory.create(String.class));
    assertEquals(Key.get(STRING_LIST, QualifierKey.class), factory.create(STRING_LIST));
  }

  @Test
  public void testRebinder() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        Key<Integer> fromKey = Key.get(Integer.class, NAME_KEY);
        bind(fromKey).toInstance(42);
        Bindings.rebinder(binder(), BindKey.class).rebind(fromKey);
      }
    });
    assertEquals(42, injector.getInstance(Key.get(Integer.class, BindKey.class)).intValue());
  }

  @Test
  public void testExposing() {
    Injector injector =
        Guice.createInjector(Bindings.exposing(Key.get(String.class),
            new AbstractModule() {
              @Override protected void configure() {
                bind(String.class).toInstance("jake");
                bind(Integer.class).toInstance(42);
              }
            }));

    assertTrue(injector.getBindings().containsKey(Key.get(String.class)));
    assertEquals("jake", injector.getInstance(String.class));

    assertFalse(injector.getBindings().containsKey(Key.get(Integer.class)));
  }
}
