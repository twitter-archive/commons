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

package com.twitter.common.objectsize;

import com.google.common.base.Function;
import com.google.common.collect.MapMaker;

import javax.annotation.Nullable;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * Contains utility methods for calculating the memory usage of objects. It
 * assumes the memory layout used by 64-bit Oracle HotSpot VMs.
 *
 * @author Attila Szegedi
 */
public class ObjectSizeCalculator {
  // TODO: generalize for different VMs.
  private static final int OBJECT_HEADER_SIZE = 16;
  private static final int ARRAY_HEADER_SIZE = OBJECT_HEADER_SIZE + 8;
  private static final int REFERENCE_FIELD_SIZE = 8;

  private static final ConcurrentMap<Class<?>, ClassSizeInfo> classSizeInfos =
    new MapMaker().makeComputingMap(new Function<Class<?>, ClassSizeInfo>() {
      public ClassSizeInfo apply(Class<?> clazz) {
        return new ClassSizeInfo(clazz);
      }
    });

  /**
   * Given an object, returns the total allocated size, in bytes, of the object and all
   * other objects reachable from it. Assumes the 64-bit HotSpot VM.
   *
   * @param obj the object; can be null. Passing in a {@link java.lang.Class} object doesn't
   * do anything special, it measures the size of all objects reachable through it (which
   * will include its class loader, and by extension, all other Class objects loaded by
   * the same loader, and all the parent class loaders). It doesn't provide the size of the
   * static fields in the JVM class that the Class object represents.
   * @return the total allocated size of the object and all other objects it retains.
   */
  @Nullable
  public static long getObjectSize(Object obj) {
    return obj == null ? 0 : new ObjectSizeCalculator().getObjectSizeInternal(obj);
  }

  // We'd use an IdentityHashSet, if such a thing existed. Sets#newSetFromMap could be
  // used, but don't want to create a wrapper for the simple usage we have here.
  private final Map<Object, Object> alreadyVisited = new IdentityHashMap<Object, Object>(512 * 1024);
  private final Deque<Object> pending = new ArrayDeque<Object>(16 * 1024);
  private long size;

  private ObjectSizeCalculator() {
    // Clients use getObjectSize(Object obj)
  }

  private long getObjectSizeInternal(Object obj) {
    for (;;) {
      visit(obj);
      if (pending.isEmpty()) {
        return size;
      }
      obj = pending.removeFirst();
    }
  }

  private void visit(Object obj) {
    if (alreadyVisited.containsKey(obj)) {
      return;
    }
    final Class<?> clazz = obj.getClass();
    if (clazz == ArrayElementsVisitor.class) {
      ((ArrayElementsVisitor) obj).visit(this);
    } else {
      alreadyVisited.put(obj, obj);
      if (clazz.isArray()) {
        visitArray(obj);
      } else {
        classSizeInfos.get(clazz).visit(obj, this);
      }
    }
  }

  private void visitArray(Object array) {
    final Class<?> componentType = array.getClass().getComponentType();
    final int length = Array.getLength(array);
    if (componentType.isPrimitive()) {
      increaseSize(ARRAY_HEADER_SIZE + length * getPrimitiveFieldSize(componentType));
    } else {
      increaseSize(ARRAY_HEADER_SIZE + length * REFERENCE_FIELD_SIZE);
      // If we didn't use an ArrayElementsVisitor, we would be enqueueing every element
      // of the array here instead. For large arrays, it would create a huge amount of
      // linked nodes in the pending list. In essence, we're compressing it into a
      // small command object instead. This is different than immediately visiting the
      // elements, as the visiting is now scheduled for the end of the current queue.
      switch (length) {
        case 0: {
          break;
        }
        case 1: {
          enqueue(Array.get(array, 0));
        }
        default: {
          enqueue(new ArrayElementsVisitor((Object[]) array));
        }
      }
    }
  }

  private static class ArrayElementsVisitor {
    private final Object[] array;

    ArrayElementsVisitor(Object[] array) {
      this.array = array;
    }

    public void visit(ObjectSizeCalculator calc) {
      for (Object elem : array) {
        if (elem != null) {
          calc.visit(elem);
        }
      }
    }
  }

  void enqueue(Object obj) {
    if (obj != null) {
      pending.addLast(obj);
    }
  }

  void increaseSize(long objectSize) {
    size += roundToEight(objectSize);
  }

  private static long roundToEight(long x) {
    return ((x + 7) / 8) * 8;
  }

  private static class ClassSizeInfo {
    private final long objectSize;
    private final Field[] referenceFields;

    public ClassSizeInfo(Class<?> clazz) {
      long objectSize = 0;
      final List<Field> referenceFields = new LinkedList<Field>();
      for (Field f: clazz.getDeclaredFields()) {
        if (Modifier.isStatic(f.getModifiers())) {
          continue;
        }
        final Class<?> type = f.getType();
        if (type.isPrimitive()) {
          objectSize += getPrimitiveFieldSize(type);
        } else {
          f.setAccessible(true);
          referenceFields.add(f);
          objectSize += REFERENCE_FIELD_SIZE;
        }
      }
      final Class<?> superClass = clazz.getSuperclass();
      if (superClass != null) {
        final ClassSizeInfo superClassInfo = classSizeInfos.get(superClass);
        objectSize += superClassInfo.objectSize - OBJECT_HEADER_SIZE;
        referenceFields.addAll(Arrays.asList(superClassInfo.referenceFields));
      }
      this.objectSize = OBJECT_HEADER_SIZE + roundToEight(objectSize);
      this.referenceFields = referenceFields.toArray(new Field[referenceFields.size()]);
    }

    void visit(Object obj, ObjectSizeCalculator calc) {
      calc.increaseSize(objectSize);
      enqueueReferencedObjects(obj, calc);
    }

    public void enqueueReferencedObjects(Object obj, ObjectSizeCalculator calc) {
      for (Field f : referenceFields) {
        try {
          calc.enqueue(f.get(obj));
        } catch(IllegalAccessException e) {
          final AssertionError ae = new AssertionError("Unexpected denial of access to " + f);
          ae.initCause(e);
          throw ae;
        }
      }
    }
  }

  private static long getPrimitiveFieldSize(Class<?> type) {
    if (type == boolean.class || type == byte.class) {
      return 1;
    }
    if (type == char.class || type == short.class) {
      return 2;
    }
    if (type == int.class || type == float.class) {
      return 4;
    }
    if (type == long.class || type == double.class) {
      return 8;
    }
    throw new AssertionError("Encountered unexpected primitive type " + type.getName());
  }
}
