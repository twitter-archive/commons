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

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author John Sirois
 */
public class DefaultProviderTest {
  public static final Key<String> CUSTOM_STRING = Key.get(String.class, Names.named("custom"));
  public static final Key<String> DEFAULT_STRING = Key.get(String.class, Names.named("default"));
  public static final Key<String> FINAL_STRING = Key.get(String.class, Names.named("final"));

  @Test
  public void testDefault() {
    assertEquals("jack", Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        DefaultProvider.bindOrElse(CUSTOM_STRING, DEFAULT_STRING, FINAL_STRING, binder());
      }

      @Provides @Named("default") String provideDefault() {
        return "jack";
      }
    }).getInstance(FINAL_STRING));
  }

  @Test
  public void testCustom() {
    assertEquals("jill", Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        DefaultProvider.bindOrElse(CUSTOM_STRING, DEFAULT_STRING, FINAL_STRING, binder());

      }

      @Provides @Named("default") String provideDefault() {
        return "jack";
      }

      @Provides @Named("custom") String provideCustom() {
        return "jill";
      }
    }).getInstance(FINAL_STRING));
  }
}
