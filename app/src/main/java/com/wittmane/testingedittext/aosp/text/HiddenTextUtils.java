package com.wittmane.testingedittext.aosp.text;

import android.os.Parcelable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ParagraphStyle;
import android.text.style.UpdateAppearance;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class HiddenTextUtils {
    /** @hide */
    public static final int SUGGESTION_RANGE_SPAN = 21;

    /**
     * Intent size limitations prevent sending over a megabyte of data. Limit
     * text length to 100K characters - 200KB.
     */
    private static final int PARCEL_SAFE_TEXT_LENGTH = 100000;

    /**
     * Trims the text to {@link #PARCEL_SAFE_TEXT_LENGTH} length. Returns the string as it is if
     * the length() is smaller than {@link #PARCEL_SAFE_TEXT_LENGTH}. Used for text that is parceled
     * into a {@link Parcelable}.
     *
     * @hide
     */
    @Nullable
    public static <T extends CharSequence> T trimToParcelableSize(@Nullable T text) {
        return trimToSize(text, PARCEL_SAFE_TEXT_LENGTH);
    }

    /**
     * Trims the text to {@code size} length. Returns the string as it is if the length() is
     * smaller than {@code size}. If chars at {@code size-1} and {@code size} is a surrogate
     * pair, returns a CharSequence of length {@code size-1}.
     *
     * @param size length of the result, should be greater than 0
     *
     * @hide
     */
    @Nullable
    public static <T extends CharSequence> T trimToSize(@Nullable T text,
                                                        @IntRange(from = 1) int size) {
//        Preconditions.checkArgument(size > 0);
        if (TextUtils.isEmpty(text) || text.length() <= size) return text;
        if (Character.isHighSurrogate(text.charAt(size - 1))
                && Character.isLowSurrogate(text.charAt(size))) {
            size = size - 1;
        }
        return (T) text.subSequence(0, size);
    }

    /**
     * Pack 2 int values into a long, useful as a return value for a range
     * @see #unpackRangeStartFromLong(long)
     * @see #unpackRangeEndFromLong(long)
     * @hide
     */
    public static long packRangeInLong(int start, int end) {
        return (((long) start) << 32) | end;
    }

    /**
     * Get the start value from a range packed in a long by {@link #packRangeInLong(int, int)}
     * @see #unpackRangeEndFromLong(long)
     * @see #packRangeInLong(int, int)
     * @hide
     */
    public static int unpackRangeStartFromLong(long range) {
        return (int) (range >>> 32);
    }

    /**
     * Get the end value from a range packed in a long by {@link #packRangeInLong(int, int)}
     * @see #unpackRangeStartFromLong(long)
     * @see #packRangeInLong(int, int)
     * @hide
     */
    public static int unpackRangeEndFromLong(long range) {
        return (int) (range & 0x00000000FFFFFFFFL);
    }

    /**
     * Returns whether or not the specified spanned text has a style span.
     * @hide
     */
    public static boolean hasStyleSpan(@NonNull Spanned spanned) {
//        Preconditions.checkArgument(spanned != null);
        final Class<?>[] styleClasses = {
                CharacterStyle.class, ParagraphStyle.class, UpdateAppearance.class};
        for (Class<?> clazz : styleClasses) {
            if (spanned.nextSpanTransition(-1, spanned.length(), clazz) < spanned.length()) {
                return true;
            }
        }
        return false;
    }
}
