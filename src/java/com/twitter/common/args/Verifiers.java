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

package com.twitter.common.args;

import java.lang.annotation.Annotation;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;

import com.twitter.common.args.apt.Configuration;
import com.twitter.common.collections.Pair;

import static com.twitter.common.args.apt.Configuration.ConfigurationException;
import static com.twitter.common.args.apt.Configuration.VerifierInfo;

/**
 * Utility class to manage relationships between constraints and types.
 *
 * @author William Farner
 */
public final class Verifiers {

  private final ImmutableMap<Pair<Class<?>, Class<? extends Annotation>>,
                             Verifier<?>> registry;

  private Verifiers(Map<Pair<Class<?>, Class<? extends Annotation>>,
                        Verifier<?>> registry) {

    this.registry = ImmutableMap.copyOf(registry);
  }

  @Nullable
  <T> Verifier<T> get(TypeToken<T> type, Annotation constraint) {
    for (Map.Entry<Pair<Class<?>, Class<? extends Annotation>>, Verifier<?>> entry
        : registry.entrySet()) {
      if (entry.getKey().getSecond() == constraint.annotationType()
          && entry.getKey().getFirst().isAssignableFrom(type.getRawType())) {

        // We control the registry which ensures a proper mapping of class -> verifier.
        @SuppressWarnings("unchecked")
        Verifier<T> verifier = (Verifier<T>) entry.getValue();
        return verifier;
      }
    }

    return null;
  }

  static Verifiers fromConfiguration(Configuration configuration) {
    ImmutableMap.Builder<Pair<Class<?>, Class<? extends Annotation>>,
                         Verifier<?>> registry = ImmutableMap.builder();

    for (VerifierInfo info : configuration.verifierInfo()) {
      Class<?> verifiedType = forName(info.verifiedType);
      Class<? extends Annotation> verifyingAnnotation = forName(info.verifyingAnnotation);
      Class<? extends Verifier<?>> verifierClass = forName(info.verifierClass);
      try {
        registry.put(
            Pair.<Class<?>, Class<? extends Annotation>>of(verifiedType, verifyingAnnotation),
          verifierClass.newInstance());
      } catch (InstantiationException e) {
        throw new ConfigurationException(e);
      } catch (IllegalAccessException e) {
        throw new ConfigurationException(e);
      }
    }
    return new Verifiers(registry.build());
  }

  @SuppressWarnings("unchecked")
  private static <T> Class<T> forName(String name) {
    try {
      return (Class<T>) Class.forName(name);
    } catch (ClassNotFoundException e) {
      throw new ConfigurationException(e);
    }
  }
}
