package com.twitter.common.jar.tool;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.common.base.Preconditions;

import org.apache.commons.io.IOUtils;

/** Super shady hacks to copy a jar entry from one jar to another without recompressing! */
public final class SuperShady {

  private static class FieldReader<T, V> {
    public static <T, V> FieldReader<T, V> create(Class<T> clazz,
        Class<V> fieldType, String name) {
      try {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        return new FieldReader<T, V>(field, fieldType);
      } catch (NoSuchFieldException e) {
        throw new RuntimeException(e);
      }
    }

    protected final Field field;
    private final Class<V> fieldType;

    FieldReader(Field field, Class<V> fieldType) {
      Preconditions.checkArgument(fieldType.isAssignableFrom(field.getType()));
      this.field = field;
      this.fieldType = fieldType;
    }

    @SuppressWarnings("unchecked") // already verified fieldType
    public V get(T instance) {
      try {
        return (V) field.get(instance);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }

    public Class<V> getType() {
      return fieldType;
    }
  }

  private static class FieldAccessor<T, V> extends FieldReader<T, V> {
    public static <T, V> FieldAccessor<T, V> create(Class<T> clazz, Class<V> fieldType,
        String name) {
      try {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        return new FieldAccessor<T, V>(field, fieldType);
      } catch (NoSuchFieldException e) {
        throw new RuntimeException(e);
      }
    }

    FieldAccessor(Field field, Class<V> fieldType) {
      super(field, fieldType);
      Preconditions.checkArgument(field.getType().isAssignableFrom(fieldType));
    }

    public void set(T instance, V value) {
      try {
        field.set(instance, value);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static final FieldReader<FilterInputStream, InputStream> FIS_IN =
      FieldReader.create(FilterInputStream.class, InputStream.class, "in");

  private static final FieldReader<ZipOutputStream, CRC32> ZOS_CRC =
      FieldReader.create(ZipOutputStream.class, CRC32.class, "crc");

  private static final FieldAccessor<CRC32, Integer> CRC_VALUE =
      FieldAccessor.create(CRC32.class, int.class, "crc");

  private static final FieldAccessor<ZipEntry, String> ZE_NAME =
      FieldAccessor.create(ZipEntry.class, String.class, "name");

  /**
   * Copy a compressed entry to an output file without decompressing and re-compressing the entry.
   *
   * @param jarOut The jar file being created or appended to
   * @param name The resource name to write
   * @param jarIn The input JarFile.
   * @param jarEntry The entry extracted from <code>jarIn</code>.  The compression method
   * passed in to this entry is preserved in the output file.
   * @throws IOException if there is a problem reading from {@code jarIn} or writing to {@code jarOut}.
   */
  public static void shadyCopy(JarOutputStream jarOut, String name, JarFile jarIn,
      JarEntry jarEntry) throws IOException {
    JarEntry outEntry = new JarEntry(jarEntry);
    ZE_NAME.set(outEntry, name);

    if (outEntry.isDirectory()) {
      outEntry.setMethod(ZipEntry.STORED);
      outEntry.setSize(0);
      outEntry.setCompressedSize(0);
      outEntry.setCrc(0);
      jarOut.putNextEntry(outEntry);
      jarOut.closeEntry();
    } else if (jarEntry.getMethod() == ZipEntry.STORED) {
      InputStream is = jarIn.getInputStream(jarEntry);
      jarOut.putNextEntry(outEntry);
      IOUtils.copy(is, jarOut);
      is.close();
      jarOut.closeEntry();
    } else {
      // Grab the underlying stream so we can read the compressed bytes.
      FilterInputStream zis = (FilterInputStream) jarIn.getInputStream(jarEntry);
      InputStream is = FIS_IN.get(zis);

      // Start it as a DEFLATE....
      jarOut.putNextEntry(outEntry);

      // But swap out the method to STORE to the bytes don't get compressed.
      // This works because ZipFile doesn't make a defensive copy.
      outEntry.setMethod(ZipEntry.STORED);
      outEntry.setSize(jarEntry.getCompressedSize());
      IOUtils.copy(is, jarOut);
      zis.close();

      // The internal CRC is now wrong, so hack it before we close the entry.
      CRC_VALUE.set(ZOS_CRC.get(jarOut), (int) jarEntry.getCrc());
      jarOut.closeEntry();

      // Restore entry back to normal, so it will be written out correctly at the end.
      outEntry.setMethod(ZipEntry.DEFLATED);
      outEntry.setSize(jarEntry.getSize());
    }
  }

  /**
   * Utility class, do not instantiate
   */
  private SuperShady() {
  }
}
