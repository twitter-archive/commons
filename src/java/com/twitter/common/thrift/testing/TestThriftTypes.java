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

package com.twitter.common.thrift.testing;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.apache.thrift.TBase;
import org.apache.thrift.TBaseHelper;
import org.apache.thrift.TException;
import org.apache.thrift.TFieldIdEnum;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TStruct;
import org.apache.thrift.protocol.TType;

import java.util.Map;
import java.util.Map.Entry;

/**
 * Hand-coded thrift types for use in tests.
 *
 * @author John Sirois
 */
public class TestThriftTypes {
  public static class Field implements TFieldIdEnum {
    private static final Map<Short, Field> FIELDS_BY_ID = Maps.newHashMap();
    public static Field forId(int id) {
      Field field = FIELDS_BY_ID.get((short) id);
      Preconditions.checkArgument(field != null, "No Field with id: %s", id);
      return field;
    }

    public static final Field NAME = new Field((short) 0, "name");
    public static final Field VALUE = new Field((short) 1, "value");

    private final short fieldId;
    private final String fieldName;

    private Field(short fieldId, String fieldName) {
      this.fieldId = fieldId;
      this.fieldName = fieldName;
      FIELDS_BY_ID.put(fieldId, this);
    }

    @Override
    public short getThriftFieldId() {
      return fieldId;
    }

    @Override
    public String getFieldName() {
      return fieldName;
    }
  }

  public static class Struct implements TBase<Struct, Field> {
    private final Map<Field, Object> fields = Maps.newHashMap();

    public Struct() {}

    public Struct(String name, String value) {
      fields.put(Field.NAME, name);
      fields.put(Field.VALUE, value);
    }

    public String getName() {
      Object name = getFieldValue(Field.NAME);
      return name == null ? null : (String) name;
    }

    public String getValue() {
      Object value = getFieldValue(Field.VALUE);
      return value == null ? null : (String) value;
    }

    @Override
    public void read(TProtocol tProtocol) throws TException {
      tProtocol.readStructBegin();
      TField field;
      while((field = tProtocol.readFieldBegin()).type != TType.STOP) {
        fields.put(fieldForId(field.id), tProtocol.readString());
        tProtocol.readFieldEnd();
      }
      tProtocol.readStructEnd();
    }

    @Override
    public void write(TProtocol tProtocol) throws TException {
      tProtocol.writeStructBegin(new TStruct("Field"));
      for (Entry<Field, Object> entry : fields.entrySet()) {
        Field field = entry.getKey();
        tProtocol.writeFieldBegin(
            new TField(field.getFieldName(), TType.STRING, field.getThriftFieldId()));
        tProtocol.writeString(entry.getValue().toString());
        tProtocol.writeFieldEnd();
      }
      tProtocol.writeFieldStop();
      tProtocol.writeStructEnd();
    }

    @Override
    public boolean isSet(Field field) {
      return fields.containsKey(field);
    }

    @Override
    public Object getFieldValue(Field field) {
      return fields.get(field);
    }

    @Override
    public void setFieldValue(Field field, Object o) {
      fields.put(field, o);
    }

    @Override
    public TBase<Struct, Field> deepCopy() {
      Struct struct = new Struct();
      struct.fields.putAll(fields);
      return struct;
    }

    @Override
    public int compareTo(Struct other) {
      if (!getClass().equals(other.getClass())) {
        return getClass().getName().compareTo(other.getClass().getName());
      }

      int lastComparison;

      lastComparison = Integer.valueOf(fields.size()).compareTo(other.fields.size());
      if (lastComparison != 0) {
        return lastComparison;
      }

      for (Map.Entry<Field, Object> entry : fields.entrySet()) {
        Field field = entry.getKey();
        lastComparison = Boolean.TRUE.compareTo(other.isSet(field));
        if (lastComparison != 0) {
          return lastComparison;
        }
        lastComparison = TBaseHelper.compareTo(entry.getValue(), other.getFieldValue(field));
        if (lastComparison != 0) {
          return lastComparison;
        }
      }
      return 0;
    }

    @Override
    public void clear() {
      fields.clear();
    }

    @Override
    public Field fieldForId(int fieldId) {
      return Field.forId(fieldId);
    }
  }
}
