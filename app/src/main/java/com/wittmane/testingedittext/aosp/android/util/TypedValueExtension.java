/*
 * Copyright (C) 2024 Eli Wittman
 * Copyright (C) 2007 The Android Open Source Project
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

package com.wittmane.testingedittext.aosp.android.util;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;
import static android.util.TypedValue.COMPLEX_UNIT_IN;
import static android.util.TypedValue.COMPLEX_UNIT_MASK;
import static android.util.TypedValue.COMPLEX_UNIT_MM;
import static android.util.TypedValue.COMPLEX_UNIT_PT;
import static android.util.TypedValue.COMPLEX_UNIT_PX;
import static android.util.TypedValue.COMPLEX_UNIT_SP;

import android.util.DisplayMetrics;
import android.util.TypedValue;

import androidx.annotation.FloatRange;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * (EW) content from {@link TypedValue} that is blocked from apps accessing
 */
public class TypedValueExtension {

    @IntDef(value = {
            COMPLEX_UNIT_PX,
            COMPLEX_UNIT_DIP,
            COMPLEX_UNIT_SP,
            COMPLEX_UNIT_PT,
            COMPLEX_UNIT_IN,
            COMPLEX_UNIT_MM,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ComplexDimensionUnit {}

    /**
     * Return the complex unit type for the given complex dimension. For example, a dimen type
     * with value 12sp will return {@link TypedValue#COMPLEX_UNIT_SP}. Use with values created with
     * {@link #createComplexDimension} etc.
     *
     * @return The complex unit type.
     */
    public static int getUnitFromComplexDimension(int complexDimension) {
        return COMPLEX_UNIT_MASK & (complexDimension >> TypedValue.COMPLEX_UNIT_SHIFT);
    }

    /**
     * Construct a complex data integer.  This validates the radix and the magnitude of the
     * mantissa, and sets the {@link TypedValue#COMPLEX_MANTISSA_MASK} and
     * {@link TypedValue#COMPLEX_RADIX_MASK} components as provided. The units are not set.
     **
     * @param mantissa an integer representing the mantissa.
     * @param radix a radix option, e.g. {@link TypedValue#COMPLEX_RADIX_23p0}.
     * @return A complex data integer representing the value.
     */
    private static int createComplex(@IntRange(from = -0x800000, to = 0x7FFFFF) int mantissa,
                                     int radix) {
        if (mantissa < -0x800000 || mantissa >= 0x800000) {
            throw new IllegalArgumentException("Magnitude of mantissa is too large: " + mantissa);
        }
        if (radix < TypedValue.COMPLEX_RADIX_23p0 || radix > TypedValue.COMPLEX_RADIX_0p23) {
            throw new IllegalArgumentException("Invalid radix: " + radix);
        }
        return ((mantissa & TypedValue.COMPLEX_MANTISSA_MASK) << TypedValue.COMPLEX_MANTISSA_SHIFT)
                | (radix << TypedValue.COMPLEX_RADIX_SHIFT);
    }

    /**
     * Convert a base value to a complex data integer.  This sets the {@link
     * TypedValue#COMPLEX_MANTISSA_MASK} and {@link TypedValue#COMPLEX_RADIX_MASK} fields of the
     * data to create a floating point representation of the given value. The units are not set.
     *
     * <p>This is the inverse of {@link TypedValue#complexToFloat(int)}.
     *
     * @param value A floating point value.
     * @return A complex data integer representing the value.
     */
    public static int floatToComplex(@FloatRange(from = -0x800000, to = 0x7FFFFF) float value) {
        // validate that the magnitude fits in this representation
        if (value < (float) -0x800000 - .5f || value >= (float) 0x800000 - .5f) {
            throw new IllegalArgumentException("Magnitude of the value is too large: " + value);
        }
        try {
            // If there's no fraction, use integer representation, as that's clearer
            if (value == (float) (int) value) {
                return createComplex((int) value, TypedValue.COMPLEX_RADIX_23p0);
            }
            float absValue = Math.abs(value);
            // If the magnitude is 0, we don't need any magnitude digits
            if (absValue < 1f) {
                return createComplex(Math.round(value * (1 << 23)), TypedValue.COMPLEX_RADIX_0p23);
            }
            // If the magnitude is less than 2^8, use 8 magnitude digits
            if (absValue < (float) (1 << 8)) {
                return createComplex(Math.round(value * (1 << 15)), TypedValue.COMPLEX_RADIX_8p15);
            }
            // If the magnitude is less than 2^16, use 16 magnitude digits
            if (absValue < (float) (1 << 16)) {
                return createComplex(Math.round(value * (1 << 7)), TypedValue.COMPLEX_RADIX_16p7);
            }
            // The magnitude requires all 23 digits
            return createComplex(Math.round(value), TypedValue.COMPLEX_RADIX_23p0);
        } catch (IllegalArgumentException ex) {
            // Wrap exception so as to include the value argument in the message.
            throw new IllegalArgumentException("Unable to convert value to complex: " + value, ex);
        }
    }

    /**
     * <p>Creates a complex data integer that stores a dimension value and units.
     *
     * <p>The resulting value can be passed to e.g.
     * {@link TypedValue#complexToDimensionPixelOffset(int, DisplayMetrics)} to calculate the pixel
     * value for the dimension.
     *
     * @param value the value of the dimension
     * @param units the units of the dimension, e.g. {@link TypedValue#COMPLEX_UNIT_DIP}
     * @return A complex data integer representing the value and units of the dimension.
     */
    public static int createComplexDimension(
            @FloatRange(from = -0x800000, to = 0x7FFFFF) float value,
            @ComplexDimensionUnit int units) {
        if (units < COMPLEX_UNIT_PX || units > COMPLEX_UNIT_MM) {
            throw new IllegalArgumentException("Must be a valid COMPLEX_UNIT_*: " + units);
        }
        return floatToComplex(value) | units;
    }
}
