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

package com.twitter.common.thrift.text;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.google.gson.JsonObject;

import org.apache.thrift.TBase;
import org.apache.thrift.TException;
import org.apache.thrift.TFieldIdEnum;
import org.apache.thrift.meta_data.FieldMetaData;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TType;

/**
 * A struct parsing context. Builds a map from field name to TField.
 *
 * @author Alex Roetter
 */
class StructContext extends PairContext {
  private static final Logger LOG = Logger.getLogger(
    TTextProtocol.class.getName());

  // When processing a given thrift struct, we need certain information
  // for every field in that struct. We store that here, in a map
  // from fieldName (a string) to a TField object describing that
  // field.
  private final Map<String, TField> fieldNameMap;

  /**
   * Build the name -> TField map
   */
  StructContext(JsonObject json) {
    super(json);
    fieldNameMap = computeFieldNameMap();
  }

  @Override
  protected TField getTFieldByName(String name) throws TException {
    if (!fieldNameMap.containsKey(name)) {
      throw new TException("Unknown field: " + name);
    }
    return fieldNameMap.get(name);
  }

  /**
   * I need to know what type thrift message we are processing,
   * in order to look up fields by their field name. For example,
   * i I parse a line "count : 7", I need to know we are in a
   * StatsThriftMessage, or similar, to know that count should be
   * of type int32, and have a thrift id 1.
   *
   * In order to figure this out, I assume that this method was
   * called (indirectly) by the read() method in a class T which
   * is a TBase subclass. It is called that way by thrift generated
   * code. So, I iterate backwards up the call stack, stopping
   * at the first method call which belongs to a TBase object.
   * I return the Class for that object.
   *
   * One could argue this is someone fragile and error prone.
   * The alternative is to modify the thrift compiler to generate
   * code which passes class information into this (and other)
   * TProtocol objects, and that seems like a lot more work. Given
   * the low level interface of TProtocol (e.g. methods like readInt(),
   * rather than readThriftMessage()), it seems likely that a TBase
   * subclass, which has the knowledge of what fields exist, as well as
   * their types & relationships, will have to be the caller of
   * the TProtocol methods.
   */
  private Class<? extends TBase> getCurrentThriftMessageClass() {
    StackTraceElement[] frames =
        Thread.currentThread().getStackTrace();

    for (int i = 0; i < frames.length; ++i) {
      String className = frames[i].getClassName();

      try {
        Class clazz = Class.forName(className);

        if (TBase.class.isAssignableFrom(clazz)) {
          // Safe to suppress this, since I've just checked that clazz
          // can be assigned to a TBase.
          @SuppressWarnings("unchecked")
          Class<? extends TBase> asTBase = clazz.asSubclass(TBase.class);
          return asTBase;
        }
      } catch (ClassNotFoundException ex) {
        LOG.warning("Can't find class: " + className);
      }
    }
    throw new RuntimeException("Must call (indirectly) from a TBase object.");
  }

  /**
   * Compute a new field name map for the current thrift message
   * we are parsing.
   */
  private Map<String, TField> computeFieldNameMap() {
    Map<String, TField> map = new HashMap<String, TField>();

    Class<? extends TBase> clazz = getCurrentThriftMessageClass();

    // Get the metaDataMap for this Thrift class
    Map<? extends TFieldIdEnum, FieldMetaData> metaDataMap =
        FieldMetaData.getStructMetaDataMap(clazz);

    for (TFieldIdEnum key : metaDataMap.keySet()) {
      final String fieldName = key.getFieldName();
      final FieldMetaData metaData = metaDataMap.get(key);

      // Workaround a bug in the generated thrift message read()
      // method by mapping the ENUM type to the INT32 type
      // The thrift generated parsing code requires that, when expecting
      // a value of enum, we actually parse a value of type int32. The
      // generated read() method then looks up the enum value in a map.
      byte type = (TType.ENUM == metaData.valueMetaData.type)
        ? TType.I32 : metaData.valueMetaData.type;

      map.put(fieldName,
          new TField(fieldName,
          type,
          key.getThriftFieldId()));
    }
    return map;
  }
}
