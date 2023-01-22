/*
 * Copyright (C) 2022-2023 Eli Wittman
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wittmane.testingedittext.aosp.internal.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Array;
import java.util.Objects;

// (EW) the AOSP version of this is hidden from apps, so it had to be copied here
/**
 * ArrayUtils contains some methods that you can call to find out
 * the most efficient increments by which to grow arrays.
 */
public class ArrayUtils {
    private static final int CACHE_SIZE = 73;
    private static final Object[] sCache = new Object[CACHE_SIZE];

    private ArrayUtils() { /* cannot be instantiated */ }

    // (EW) AOSP versions of newUnpadded*Array calls VMRuntime#getRuntime and
    // VMRuntime#newUnpaddedArray, which are hidden. at least for now we'll just have a simple
    // implementation but leave these functions to allow the callers to match the AOSP version for
    // better comparison and leaves room for improvements here if there is something that we can do,
    // but realistically, that probably won't ever happen.

    public static byte[] newUnpaddedByteArray(int minLen) {
        return new byte[minLen];
    }

    public static char[] newUnpaddedCharArray(int minLen) {
        return new char[minLen];
    }

    public static int[] newUnpaddedIntArray(int minLen) {
        return new int[minLen];
    }

    public static boolean[] newUnpaddedBooleanArray(int minLen) {
        return new boolean[minLen];
    }

    public static long[] newUnpaddedLongArray(int minLen) {
        return new long[minLen];
    }

    public static float[] newUnpaddedFloatArray(int minLen) {
        return new float[minLen];
    }

    public static Object[] newUnpaddedObjectArray(int minLen) {
        return new Object[minLen];
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] newUnpaddedArray(Class<T> clazz, int minLen) {
        return (T[]) Array.newInstance(clazz, minLen);
    }

    /**
     * Returns an empty array of the specified type.  The intent is that
     * it will return the same empty array every time to avoid reallocation,
     * although this is not guaranteed.
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] emptyArray(Class<T> kind) {
        if (kind == Object.class) {
            return (T[]) EmptyArray.OBJECT;
        }

        int bucket = (kind.hashCode() & 0x7FFFFFFF) % CACHE_SIZE;
        Object cache = sCache[bucket];

        if (cache == null || cache.getClass().getComponentType() != kind) {
            cache = Array.newInstance(kind, 0);
            sCache[bucket] = cache;
        }

        return (T[]) cache;
    }

    /**
     * Checks that value is present as at least one of the elements of the array.
     * @param array the array to check in
     * @param value the value to check for
     * @return true if the value is present in the array
     */
    public static <T> boolean contains(@Nullable T[] array, T value) {
        return indexOf(array, value) != -1;
    }

    /**
     * Return first index of {@code value} in {@code array}, or {@code -1} if
     * not found.
     */
    public static <T> int indexOf(@Nullable T[] array, T value) {
        if (array == null) return -1;
        for (int i = 0; i < array.length; i++) {
            if (Objects.equals(array[i], value)) return i;
        }
        return -1;
    }

    /**
     * Test if any {@code check} items are contained in {@code array}.
     */
    public static <T> boolean containsAny(@Nullable T[] array, T[] check) {
        if (check == null) return false;
        for (T checkItem : check) {
            if (contains(array, checkItem)) {
                return true;
            }
        }
        return false;
    }

    public static boolean contains(@Nullable int[] array, int value) {
        if (array == null) return false;
        for (int element : array) {
            if (element == value) {
                return true;
            }
        }
        return false;
    }

    public static boolean contains(@Nullable long[] array, long value) {
        if (array == null) return false;
        for (long element : array) {
            if (element == value) {
                return true;
            }
        }
        return false;
    }

    public static boolean contains(@Nullable char[] array, char value) {
        if (array == null) return false;
        for (char element : array) {
            if (element == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * Test if all {@code check} items are contained in {@code array}.
     */
    public static <T> boolean containsAll(@Nullable char[] array, char[] check) {
        if (check == null) return true;
        for (char checkItem : check) {
            if (!contains(array, checkItem)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Adds value to given array if not already present, providing set-like
     * behavior.
     */
    @SuppressWarnings("unchecked")
    public static @NonNull <T> T[] appendElement(Class<T> kind, @Nullable T[] array, T element) {
        return appendElement(kind, array, element, false);
    }

    /**
     * Adds value to given array.
     */
    @SuppressWarnings("unchecked")
    public static @NonNull <T> T[] appendElement(Class<T> kind, @Nullable T[] array, T element,
                                                 boolean allowDuplicates) {
        final T[] result;
        final int end;
        if (array != null) {
            if (!allowDuplicates && contains(array, element)) return array;
            end = array.length;
            result = (T[])Array.newInstance(kind, end + 1);
            System.arraycopy(array, 0, result, 0, end);
        } else {
            end = 0;
            result = (T[])Array.newInstance(kind, 1);
        }
        result[end] = element;
        return result;
    }

    // (EW) from libcore.util
    public static final class EmptyArray {
        private EmptyArray() {}
        public static final @NonNull boolean[] BOOLEAN = new boolean[0];
        public static final @NonNull byte[] BYTE = new byte[0];
        public static final char[] CHAR = new char[0];
        public static final double[] DOUBLE = new double[0];
        public static final @NonNull float[] FLOAT = new float[0];
        public static final @NonNull int[] INT = new int[0];
        public static final @NonNull long[] LONG = new long[0];
        public static final Class<?>[] CLASS = new Class[0];
        public static final @NonNull Object[] OBJECT = new Object[0];
        public static final @NonNull String[] STRING = new String[0];
        public static final Throwable[] THROWABLE = new Throwable[0];
        public static final StackTraceElement[] STACK_TRACE_ELEMENT = new StackTraceElement[0];
        public static final java.lang.reflect.Type[] TYPE = new java.lang.reflect.Type[0];
        public static final java.lang.reflect.TypeVariable[] TYPE_VARIABLE =
                new java.lang.reflect.TypeVariable[0];
    }
}
