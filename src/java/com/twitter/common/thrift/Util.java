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

package com.twitter.common.thrift;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import org.apache.thrift.TBase;
import org.apache.thrift.TFieldIdEnum;
import org.apache.thrift.meta_data.FieldMetaData;

import com.twitter.thrift.Endpoint;
import com.twitter.thrift.ServiceInstance;

/**
 * Utility functions for thrift.
 *
 * @author William Farner
 */
public class Util {

  /**
   * Maps a {@link ServiceInstance} to an {@link InetSocketAddress} given the {@code endpointName}.
   *
   * @param optionalEndpointName the name of the end-point on the service's additional end-points,
   *      if not set, maps to the primary service end-point
   */
  public static Function<ServiceInstance, InetSocketAddress> getAddress(
      final Optional<String> optionalEndpointName) {
    if (!optionalEndpointName.isPresent()) {
      return GET_ADDRESS;
    }

    final String endpointName = optionalEndpointName.get();
    return getAddress(
        new Function<ServiceInstance, Endpoint>() {
          @Override public Endpoint apply(@Nullable ServiceInstance serviceInstance) {
            Map<String, Endpoint> endpoints = serviceInstance.getAdditionalEndpoints();
            Preconditions.checkArgument(endpoints.containsKey(endpointName),
                "Did not find end-point %s on %s", endpointName, serviceInstance);
            return endpoints.get(endpointName);
          }
        });
  }

  private static Function<ServiceInstance, InetSocketAddress> getAddress(
      final Function<ServiceInstance, Endpoint> serviceToEndpoint) {
    return new Function<ServiceInstance, InetSocketAddress>() {
          @Override public InetSocketAddress apply(ServiceInstance serviceInstance) {
            Endpoint endpoint = serviceToEndpoint.apply(serviceInstance);
            return InetSocketAddress.createUnresolved(endpoint.getHost(), endpoint.getPort());
          }
        };
  }

  private static Function<ServiceInstance, Endpoint> GET_PRIMARY_ENDPOINT =
      new Function<ServiceInstance, Endpoint>() {
        @Override public Endpoint apply(ServiceInstance input) {
          return input.getServiceEndpoint();
        }
      };

  public static Function<ServiceInstance, InetSocketAddress> GET_ADDRESS =
      getAddress(GET_PRIMARY_ENDPOINT);

  public static final Predicate<ServiceInstance> IS_ALIVE = new Predicate<ServiceInstance>() {
    @Override public boolean apply(ServiceInstance serviceInstance) {
      switch (serviceInstance.getStatus()) {
        case ALIVE:
          return true;

        // We'll be optimistic here and let MTCP's ranking deal with
        // unhealthy services in a WARNING state.
        case WARNING:
          return true;

        // Services which are just starting up, on the other hand... are much easier to just not
        // send requests to.  The STARTING state is useful to distinguish from WARNING or ALIVE:
        // you exist in ZooKeeper, but don't yet serve traffic.
        case STARTING:
        default:
          return false;
      }
    }
  };

  /**
   * Pretty-prints a thrift object contents.
   *
   * @param t The thrift object to print.
   * @return The pretty-printed version of the thrift object.
   */
  public static String prettyPrint(TBase t) {
    return t == null ? "null" : printTbase(t, 0);
  }

  /**
   * Prints an object contained in a thrift message.
   *
   * @param o The object to print.
   * @param depth The print nesting level.
   * @return The pretty-printed version of the thrift field.
   */
  private static String printValue(Object o, int depth) {
    if (o == null) {
      return "null";
    } else if (TBase.class.isAssignableFrom(o.getClass())) {
      return "\n" + printTbase((TBase) o, depth + 1);
    } else if (Map.class.isAssignableFrom(o.getClass())) {
      return printMap((Map) o, depth + 1);
    } else if (List.class.isAssignableFrom(o.getClass())) {
      return printList((List) o, depth + 1);
    } else if (Set.class.isAssignableFrom(o.getClass())) {
      return printSet((Set) o, depth + 1);
    } else if (String.class == o.getClass()) {
      return '"' + o.toString() + '"';
    } else {
      return o.toString();
    }
  }

  private static final String METADATA_MAP_FIELD_NAME = "metaDataMap";

  /**
   * Prints a TBase.
   *
   * @param t The object to print.
   * @param depth The print nesting level.
   * @return The pretty-printed version of the TBase.
   */
  private static String printTbase(TBase t, int depth) {
    List<String> fields = Lists.newArrayList();
    for (Map.Entry<? extends TFieldIdEnum, FieldMetaData> entry :
        FieldMetaData.getStructMetaDataMap(t.getClass()).entrySet()) {
      @SuppressWarnings("unchecked")
      boolean fieldSet = t.isSet(entry.getKey());
      String strValue;
      if (fieldSet) {
        @SuppressWarnings("unchecked")
        Object value = t.getFieldValue(entry.getKey());
        strValue = printValue(value, depth);
      } else {
        strValue = "not set";
      }
      fields.add(tabs(depth) + entry.getValue().fieldName + ": " + strValue);
    }

    return Joiner.on("\n").join(fields);
  }

  /**
   * Prints a map in a style that is consistent with TBase pretty printing.
   *
   * @param map The map to print
   * @param depth The print nesting level.
   * @return The pretty-printed version of the map.
   */
  private static String printMap(Map<?, ?> map, int depth) {
    List<String> entries = Lists.newArrayList();
    for (Map.Entry entry : map.entrySet()) {
      entries.add(tabs(depth) + printValue(entry.getKey(), depth)
          + " = " + printValue(entry.getValue(), depth));
    }

    return entries.isEmpty() ? "{}"
        : String.format("{\n%s\n%s}", Joiner.on(",\n").join(entries), tabs(depth - 1));
  }

  /**
   * Prints a list in a style that is consistent with TBase pretty printing.
   *
   * @param list The list to print
   * @param depth The print nesting level.
   * @return The pretty-printed version of the list
   */
  private static String printList(List<?> list, int depth) {
    List<String> entries = Lists.newArrayList();
    for (int i = 0; i < list.size(); i++) {
      entries.add(
          String.format("%sItem[%d] = %s", tabs(depth), i, printValue(list.get(i), depth)));
    }

    return entries.isEmpty() ? "[]"
        : String.format("[\n%s\n%s]", Joiner.on(",\n").join(entries), tabs(depth - 1));
  }
  /**
   * Prints a set in a style that is consistent with TBase pretty printing.
   *
   * @param set The set to print
   * @param depth The print nesting level.
   * @return The pretty-printed version of the set
   */
  private static String printSet(Set<?> set, int depth) {
    List<String> entries = Lists.newArrayList();
    for (Object item : set) {
      entries.add(
          String.format("%sItem = %s", tabs(depth), printValue(item, depth)));
    }

    return entries.isEmpty() ? "{}"
        : String.format("{\n%s\n%s}", Joiner.on(",\n").join(entries), tabs(depth - 1));
  }

  private static String tabs(int n) {
    return Strings.repeat("  ", n);
  }

  private Util() {
    // Utility class.
  }
}
