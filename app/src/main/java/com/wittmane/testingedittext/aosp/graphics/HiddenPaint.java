/*
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

package com.wittmane.testingedittext.aosp.graphics;

import android.annotation.SuppressLint;
import android.graphics.Paint;
import android.os.Build;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * (EW) content from Paint that is blocked from apps accessing
 */
public class HiddenPaint {
    /**
     * Flag for getTextRunAdvances indicating left-to-right run direction.
     */
    public static final int DIRECTION_LTR = 0;

    /**
     * Flag for getTextRunAdvances indicating right-to-left run direction.
     */
    public static final int DIRECTION_RTL = 1;

    @SuppressLint("InlinedApi")// (EW) versions shouldn't matter for the IntDef
    @IntDef(value = {
            Paint.CURSOR_AFTER,
            Paint.CURSOR_AT_OR_AFTER,
            Paint.CURSOR_BEFORE,
            Paint.CURSOR_AT_OR_BEFORE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface CursorOption {}

    /**
     * Option for getTextRunCursor.
     *
     * Compute the valid cursor after offset or the limit of the context, whichever is less.
     */
    public static final int CURSOR_AFTER = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            ? Paint.CURSOR_AFTER : 0;

    /**
     * Option for getTextRunCursor.
     *
     * Compute the valid cursor at or after the offset or the limit of the context, whichever is
     * less.
     */
    public static final int CURSOR_AT_OR_AFTER = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            ? Paint.CURSOR_AT_OR_AFTER : 1;

    /**
     * Option for getTextRunCursor.
     *
     * Compute the valid cursor before offset or the start of the context, whichever is greater.
     */
    public static final int CURSOR_BEFORE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            ? Paint.CURSOR_BEFORE : 2;

    /**
     * Option for getTextRunCursor.
     *
     * Compute the valid cursor at or before offset or the start of the context, whichever is
     * greater.
     */
    public static final int CURSOR_AT_OR_BEFORE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            ? Paint.CURSOR_AT_OR_BEFORE : 3;

    /**
     * Option for getTextRunCursor.
     *
     * Return offset if the cursor at offset is valid, or -1 if it isn't.
     */
    public static final int CURSOR_AT = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            ? Paint.CURSOR_AT : 4;

    @SuppressLint("InlinedApi")// (EW) versions shouldn't matter for the IntDef
    @IntDef(value = {
            Paint.START_HYPHEN_EDIT_NO_EDIT,
            Paint.START_HYPHEN_EDIT_INSERT_HYPHEN,
            Paint.START_HYPHEN_EDIT_INSERT_ZWJ
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface StartHyphenEdit {}

    /**
     * An integer representing the starting of the line has no modification for hyphenation.
     */
    public static final int START_HYPHEN_EDIT_NO_EDIT =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? Paint.START_HYPHEN_EDIT_NO_EDIT : 0x00;

    /**
     * An integer representing the starting of the line has normal hyphen character (U+002D).
     */
    public static final int START_HYPHEN_EDIT_INSERT_HYPHEN =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? Paint.START_HYPHEN_EDIT_INSERT_HYPHEN : 0x01;

    /**
     * An integer representing the starting of the line has Zero-Width-Joiner (U+200D).
     */
    public static final int START_HYPHEN_EDIT_INSERT_ZWJ =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? Paint.START_HYPHEN_EDIT_INSERT_ZWJ : 0x02;

    @SuppressLint("InlinedApi")// (EW) versions shouldn't matter for the IntDef
    @IntDef(value = {
            Paint.END_HYPHEN_EDIT_NO_EDIT,
            Paint.END_HYPHEN_EDIT_REPLACE_WITH_HYPHEN,
            Paint.END_HYPHEN_EDIT_INSERT_HYPHEN,
            Paint.END_HYPHEN_EDIT_INSERT_ARMENIAN_HYPHEN,
            Paint.END_HYPHEN_EDIT_INSERT_MAQAF,
            Paint.END_HYPHEN_EDIT_INSERT_UCAS_HYPHEN,
            Paint.END_HYPHEN_EDIT_INSERT_ZWJ_AND_HYPHEN
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface EndHyphenEdit {}

    /**
     * An integer representing the end of the line has no modification for hyphenation.
     */
    public static final int END_HYPHEN_EDIT_NO_EDIT =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? Paint.END_HYPHEN_EDIT_NO_EDIT : 0x00;

    /**
     * An integer representing the character at the end of the line is replaced with hyphen
     * character (U+002D).
     */
    public static final int END_HYPHEN_EDIT_REPLACE_WITH_HYPHEN =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? Paint.END_HYPHEN_EDIT_REPLACE_WITH_HYPHEN : 0x01;

    /**
     * An integer representing the end of the line has normal hyphen character (U+002D).
     */
    public static final int END_HYPHEN_EDIT_INSERT_HYPHEN =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? Paint.END_HYPHEN_EDIT_INSERT_HYPHEN : 0x02;

    /**
     * An integer representing the end of the line has Armentian hyphen (U+058A).
     */
    public static final int END_HYPHEN_EDIT_INSERT_ARMENIAN_HYPHEN =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? Paint.END_HYPHEN_EDIT_INSERT_ARMENIAN_HYPHEN : 0x03;

    /**
     * An integer representing the end of the line has maqaf (Hebrew hyphen, U+05BE).
     */
    public static final int END_HYPHEN_EDIT_INSERT_MAQAF =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? Paint.END_HYPHEN_EDIT_INSERT_MAQAF : 0x04;

    /**
     * An integer representing the end of the line has Canadian Syllabics hyphen (U+1400).
     */
    public static final int END_HYPHEN_EDIT_INSERT_UCAS_HYPHEN =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? Paint.END_HYPHEN_EDIT_INSERT_UCAS_HYPHEN : 0x05;

    /**
     * An integer representing the end of the line has Zero-Width-Joiner (U+200D) followed by normal
     * hyphen character (U+002D).
     */
    public static final int END_HYPHEN_EDIT_INSERT_ZWJ_AND_HYPHEN =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? Paint.END_HYPHEN_EDIT_INSERT_ZWJ_AND_HYPHEN : 0x06;

    // (EW) from Pie
    /**
     * Mask for hyphen edits that happen at the end of a line. Keep in sync with the definition in
     * Minikin's Hyphenator.h.
     */
    public static final int HYPHENEDIT_MASK_END_OF_LINE = 0x07;

    // (EW) from Pie
    /**
     * Mask for hyphen edits that happen at the start of a line. Keep in sync with the definition in
     * Minikin's Hyphenator.h.
     */
    public static final int HYPHENEDIT_MASK_START_OF_LINE = 0x03 << 3;
}
