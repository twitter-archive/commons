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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.MapMaker;
import com.google.common.collect.Sets;

/**
 * Contains utility methods for calculating the memory usage of objects. It
 * only works on the HotSpot JVM, and infers the actual memory layout (32 bit
 * vs. 64 bit word size, compressed object pointers vs. uncompressed) from
 * best available indicators. It can reliably detect a 32 bit vs. 64 bit JVM.
 * It can only make an educated guess at whether compressed OOPs are used,
 * though; specifically, it knows what the JVM's default choice of OOP
 * compression would be based on HotSpot version and maximum heap sizes, but if
 * the choice is explicitly overridden with the <tt>-XX:{+|-}UseCompressedOops</tt> command line
 * switch, it can not detect
 * this fact and will report incorrect sizes, as it will presume the default JVM
 * behavior.
 *
 * @author Attila Szegedi
 */
public class ObjectSizeCalculator {
  private static final MemoryLayoutSpecification LAYOUT_SPEC =
      getEffectiveMemoryLayoutSpecification();
  // Fixed object header size for arrays.
  private static final int ARRAY_HEADER_SIZE = LAYOUT_SPEC.getArrayHeaderSize();
  // Fixed object header size for non-array objects.
  private static final int OBJECT_HEADER_SIZE =
      LAYOUT_SPEC.getObjectHeaderSize();
  // Padding for the object size - if the object size is not an exact multiple
  // of this, it is padded to the next multiple. All currently supported JVMs
  // use 8.
  private static final int OBJECT_PADDING = 8;
  // Size of reference (pointer) fields.
  private static final int REFERENCE_SIZE = LAYOUT_SPEC.getReferenceSize();
  // Padding for the fields of superclass before fields of subclasses are
  // added.
  private static final int SUPERCLASS_FIELD_PADDING =
      LAYOUT_SPEC.getSuperclassFieldPadding();

  private static final ConcurrentMap<Class<?>, ClassSizeInfo> classSizeInfos =
      new MapMaker().makeComputingMap(new Function<Class<?>, ClassSizeInfo>() {
        public ClassSizeInfo apply(Class<?> clazz) {
          return new ClassSizeInfo(clazz);
        }
      });

  /**
   * Given an object, returns the total allocated size, in bytes, of the object
   * and all other objects reachable from it.
   *
   * @param obj the object; can be null. Passing in a {@link java.lang.Class} object doesn't do
   *          anything special, it measures the size of all objects
   *          reachable through it (which will include its class loader, and by
   *          extension, all other Class objects loaded by
   *          the same loader, and all the parent class loaders). It doesn't provide the
   *          size of the static fields in the JVM class that the Class object
   *          represents.
   * @return the total allocated size of the object and all other objects it
   *         retains.
   */
  public static long getObjectSize(Object obj) {
    return obj == null ? 0 : new ObjectSizeCalculator().getObjectSizeInternal(
        obj);
  }

  private final Set<Object> alreadyVisited = Sets.newIdentityHashSet();
  private final Deque<Object> pending = new ArrayDeque<Object>(16 * 1024);
  private long size;

  private ObjectSizeCalculator() {
    // Clients use getObjectSize(Object obj)
  }

  private long getObjectSizeInternal(Object obj) {
    // Breadth-first traversal instead of naive depth-first with recursive
    // implementation, so we don't blow the stack traversing long linked lists.
    for (;;) {
      visit(obj);
      if (pending.isEmpty()) {
        return size;
      }
      obj = pending.removeFirst();
    }
  }

  private void visit(Object obj) {
    if (alreadyVisited.contains(obj)) {
      return;
    }
    final Class<?> clazz = obj.getClass();
    if (clazz == ArrayElementsVisitor.class) {
      ((ArrayElementsVisitor) obj).visit(this);
    } else {
      alreadyVisited.add(obj);
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
      increaseByArraySize(length, getPrimitiveFieldSize(componentType));
    } else {
      increaseByArraySize(length, REFERENCE_SIZE);
      // If we didn't use an ArrayElementsVisitor, we would be enqueueing every
      // element of the array here instead. For large arrays, it would
      // tremendously enlarge the queue. In essence, we're compressing it into
      // a small command object instead. This is different than immediately
      // visiting the elements, as their visiting is scheduled for the end of
      // the current queue.
      switch (length) {
        case 0: {
          break;
        }
        case 1: {
          enqueue(Array.get(array, 0));
          break;
        }
        default: {
          enqueue(new ArrayElementsVisitor((Object[]) array));
        }
      }
    }
  }

  private void increaseByArraySize(int length, long elementSize) {
    increaseSize(roundTo(ARRAY_HEADER_SIZE + length * elementSize,
        OBJECT_PADDING));
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
    size += objectSize;
  }

  @VisibleForTesting
  static long roundTo(long x, int multiple) {
    return ((x + multiple - 1) / multiple) * multiple;
  }

  private static class ClassSizeInfo {
    // Padded fields + header size
    private final long objectSize;
    // Only the fields size - used to calculate the subclasses' memory
    // footprint.
    private final long fieldsSize;
    private final Field[] referenceFields;

    public ClassSizeInfo(Class<?> clazz) {
      long fieldsSize = 0;
      final List<Field> referenceFields = new LinkedList<Field>();
      for (Field f : clazz.getDeclaredFields()) {
        if (Modifier.isStatic(f.getModifiers())) {
          continue;
        }
        final Class<?> type = f.getType();
        if (type.isPrimitive()) {
          fieldsSize += getPrimitiveFieldSize(type);
        } else {
          f.setAccessible(true);
          referenceFields.add(f);
          fieldsSize += REFERENCE_SIZE;
        }
      }
      final Class<?> superClass = clazz.getSuperclass();
      if (superClass != null) {
        final ClassSizeInfo superClassInfo = classSizeInfos.get(superClass);
        fieldsSize += roundTo(superClassInfo.fieldsSize,
            SUPERCLASS_FIELD_PADDING);
        referenceFields.addAll(Arrays.asList(superClassInfo.referenceFields));
      }
      this.fieldsSize = fieldsSize;
      this.objectSize = roundTo(OBJECT_HEADER_SIZE + fieldsSize,
          OBJECT_PADDING);
      this.referenceFields = referenceFields.toArray(
          new Field[referenceFields.size()]);
    }

    void visit(Object obj, ObjectSizeCalculator calc) {
      calc.increaseSize(objectSize);
      enqueueReferencedObjects(obj, calc);
    }

    public void enqueueReferencedObjects(Object obj, ObjectSizeCalculator calc) {
      for (Field f : referenceFields) {
        try {
          calc.enqueue(f.get(obj));
        } catch (IllegalAccessException e) {
          final AssertionError ae = new AssertionError(
              "Unexpected denial of access to " + f);
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
    throw new AssertionError("Encountered unexpected primitive type " +
        type.getName());
  }

  private abstract static class MemoryLayoutSpecification {
    abstract int getArrayHeaderSize();

    abstract int getObjectHeaderSize();

    abstract int getReferenceSize();

    abstract int getSuperclassFieldPadding();
  }

  private static MemoryLayoutSpecification getEffectiveMemoryLayoutSpecification() {
    final String vmName = System.getProperty("java.vm.name");
    if (vmName == null || !vmName.startsWith("Java HotSpot(TM) ")) {
      throw new UnsupportedOperationException(
          "ObjectSizeCalculator only supported on HotSpot VM");
    }

    final String dataModel = System.getProperty("sun.arch.data.model");
    if ("32".equals(dataModel)) {
      // Running with 32-bit data model
      return new MemoryLayoutSpecification() {
        int getArrayHeaderSize() {
          return 12;
        }

        int getObjectHeaderSize() {
          return 8;
        }

        int getReferenceSize() {
          return 4;
        }

        int getSuperclassFieldPadding() {
          return 4;
        }
      };
    } else if (!"64".equals(dataModel)) {
      throw new UnsupportedOperationException("Unrecognized value '" +
          dataModel + "' of sun.arch.data.model system property");
    }

    final String strVmVersion = System.getProperty("java.vm.version");
    final int vmVersion = Integer.parseInt(strVmVersion.substring(0,
        strVmVersion.indexOf('.')));
    if (vmVersion >= 17) {
      long maxMemory = 0;
      for (MemoryPoolMXBean mp : ManagementFactory.getMemoryPoolMXBeans()) {
        maxMemory += mp.getUsage().getMax();
      }
      if (maxMemory < 30L * 1024 * 1024 * 1024) {
        // HotSpot 17.0 and above use compressed OOPs below 30GB of RAM total
        // for all memory pools (yes, including code cache).
        return new MemoryLayoutSpecification() {
          int getArrayHeaderSize() {
            return 16;
          }

          int getObjectHeaderSize() {
            return 12;
          }

          int getReferenceSize() {
            return 4;
          }

          int getSuperclassFieldPadding() {
            return 4;
          }
        };
      }
    }

    // In other cases, it's a 64-bit uncompressed OOPs object model
    return new MemoryLayoutSpecification() {
      int getArrayHeaderSize() {
        return 24;
      }

      int getObjectHeaderSize() {
        return 16;
      }

      int getReferenceSize() {
        return 8;
      }

      int getSuperclassFieldPadding() {
        return 8;
      }
    };
  }

  @VisibleForTesting
  static int getArrayHeaderSize() {
    return ARRAY_HEADER_SIZE;
  }

  @VisibleForTesting
  static int getObjectHeaderSize() {
    return OBJECT_HEADER_SIZE;
  }

  @VisibleForTesting
  static int getReferenceSize() {
    return REFERENCE_SIZE;
  }

  @VisibleForTesting
  static int getSuperclassFieldPadding() {
    return SUPERCLASS_FIELD_PADDING;
  }
}
