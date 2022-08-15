/*
 * Copyright (C) 2022 Eli Wittman
 * Copyright (C) 2010 The Android Open Source Project
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

package com.wittmane.testingedittext.aosp.text;

import android.os.Build;
import android.text.Layout;

import android.icu.text.Bidi;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.wittmane.testingedittext.aosp.text.HiddenLayout.Directions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// (EW) the AOSP version of this is hidden from apps, so it had to be copied here in order to copy
// the hidden MeasuredParagraph
/**
 * Access the ICU bidi implementation.
 */
public class AndroidBidi {
    private static final String TAG = AndroidBidi.class.getSimpleName();

    /**
     * Runs the bidi algorithm on input text.
     */
    @RequiresApi(api = Build.VERSION_CODES.P)
    public static int bidi(int dir, char[] chs, byte[] chInfo) {
        if (chs == null || chInfo == null) {
            throw new NullPointerException();
        }

        final int length = chs.length;
        if (chInfo.length < length) {
            throw new IndexOutOfBoundsException();
        }

        // (EW) despite not actually getting called, on Pie, simply having this block of code here
        // causes this warning to be logged:
        // Accessing hidden method Landroid/icu/text/Bidi;-><init>(II)V (dark greylist, linking)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            final byte paraLevel;
            switch (dir) {
                case HiddenLayout.DIR_REQUEST_LTR:
                    paraLevel = Bidi.LTR;
                    break;
                case HiddenLayout.DIR_REQUEST_RTL:
                    paraLevel = Bidi.RTL;
                    break;
                case HiddenLayout.DIR_REQUEST_DEFAULT_LTR:
                    paraLevel = Bidi.LEVEL_DEFAULT_LTR;
                    break;
                case HiddenLayout.DIR_REQUEST_DEFAULT_RTL:
                    paraLevel = Bidi.LEVEL_DEFAULT_RTL;
                    break;
                default:
                    paraLevel = Bidi.LTR;
                    break;
            }
            final Bidi icuBidi = new Bidi(length /* maxLength */, 0 /* maxRunCount */);
            icuBidi.setPara(chs, paraLevel, null /* embeddingLevels */);
            for (int i = 0; i < length; i++) {
                chInfo[i] = icuBidi.getLevelAt(i);
            }
            final byte result = icuBidi.getParaLevel();
            return (result & 0x1) == 0 ? Layout.DIR_LEFT_TO_RIGHT : Layout.DIR_RIGHT_TO_LEFT;
        }

        // (EW) ICU Bidi was used in Pie, but it wasn't made accessible until Q. for some reason
        // using reflection on the constructor doesn't seem to work, so we'll just have to use
        // reflection on the AOSP version of this copied method.
        try {
            Class<?> androidBidiClass = Class.forName("android.text.AndroidBidi");

            // (EW) this line on Pie works, but logs the warning:
            // Accessing hidden method Landroid/text/AndroidBidi;->bidi(I[C[B)I (light greylist, reflection)
            Method bidiMethod = androidBidiClass.getMethod("bidi", int.class, char[].class,
                    byte[].class);

            return (int) bidiMethod.invoke(null, dir, chs, chInfo);

        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                | InvocationTargetException e) {
            Log.e(TAG, "bidi: Reflection failed on AndroidBidi: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
            return Layout.DIR_LEFT_TO_RIGHT;
        }
    }

    // (EW) only can be called prior to Pie
    public static int bidi(int dir, char[] chs, byte[] chInfo, int n, boolean haveInfo) {
        if (chs == null || chInfo == null) {
            throw new NullPointerException();
        }

        if (n < 0 || chs.length < n || chInfo.length < n) {
            throw new IndexOutOfBoundsException();
        }

        switch(dir) {
            case HiddenLayout.DIR_REQUEST_LTR: dir = 0; break;
            case HiddenLayout.DIR_REQUEST_RTL: dir = 1; break;
            case HiddenLayout.DIR_REQUEST_DEFAULT_LTR: dir = -2; break;
            case HiddenLayout.DIR_REQUEST_DEFAULT_RTL: dir = -1; break;
            default: dir = 0; break;
        }

        int result = runBidi(dir, chs, chInfo, n, haveInfo);
        result = (result & 0x1) == 0 ? Layout.DIR_LEFT_TO_RIGHT : Layout.DIR_RIGHT_TO_LEFT;
        return result;
    }

    /**
     * Returns run direction information for a line within a paragraph.
     *
     * @param dir base line direction, either Layout.DIR_LEFT_TO_RIGHT or
     *     Layout.DIR_RIGHT_TO_LEFT
     * @param levels levels as returned from {@link #bidi}
     * @param lstart start of the line in the levels array
     * @param chars the character array (used to determine whitespace)
     * @param cstart the start of the line in the chars array
     * @param len the length of the line
     * @return the directions
     */
    public static Directions directions(int dir, byte[] levels, int lstart,
                                        char[] chars, int cstart, int len) {
        if (len == 0) {
            return HiddenLayout.DIRS_ALL_LEFT_TO_RIGHT;
        }

        int baseLevel = dir == Layout.DIR_LEFT_TO_RIGHT ? 0 : 1;
        int curLevel = levels[lstart];
        int minLevel = curLevel;
        int runCount = 1;
        for (int i = lstart + 1, e = lstart + len; i < e; ++i) {
            int level = levels[i];
            if (level != curLevel) {
                curLevel = level;
                ++runCount;
            }
        }

        // add final run for trailing counter-directional whitespace
        int visLen = len;
        if ((curLevel & 1) != (baseLevel & 1)) {
            // look for visible end
            while (--visLen >= 0) {
                char ch = chars[cstart + visLen];

                if (ch == '\n') {
                    --visLen;
                    break;
                }

                if (ch != ' ' && ch != '\t') {
                    break;
                }
            }
            ++visLen;
            if (visLen != len) {
                ++runCount;
            }
        }

        if (runCount == 1 && minLevel == baseLevel) {
            // we're done, only one run on this line
            if ((minLevel & 1) != 0) {
                return HiddenLayout.DIRS_ALL_RIGHT_TO_LEFT;
            }
            return HiddenLayout.DIRS_ALL_LEFT_TO_RIGHT;
        }

        int[] ld = new int[runCount * 2];
        int maxLevel = minLevel;
        int levelBits = minLevel << HiddenLayout.RUN_LEVEL_SHIFT;
        {
            // Start of first pair is always 0, we write
            // length then start at each new run, and the
            // last run length after we're done.
            int n = 1;
            int prev = lstart;
            curLevel = minLevel;
            for (int i = lstart, e = lstart + visLen; i < e; ++i) {
                int level = levels[i];
                if (level != curLevel) {
                    curLevel = level;
                    if (level > maxLevel) {
                        maxLevel = level;
                    } else if (level < minLevel) {
                        minLevel = level;
                    }
                    // XXX ignore run length limit of 2^RUN_LEVEL_SHIFT
                    ld[n++] = (i - prev) | levelBits;
                    ld[n++] = i - lstart;
                    levelBits = curLevel << HiddenLayout.RUN_LEVEL_SHIFT;
                    prev = i;
                }
            }
            ld[n] = (lstart + visLen - prev) | levelBits;
            if (visLen < len) {
                ld[++n] = visLen;
                ld[++n] = (len - visLen) | (baseLevel << HiddenLayout.RUN_LEVEL_SHIFT);
            }
        }

        // See if we need to swap any runs.
        // If the min level run direction doesn't match the base
        // direction, we always need to swap (at this point
        // we have more than one run).
        // Otherwise, we don't need to swap the lowest level.
        // Since there are no logically adjacent runs at the same
        // level, if the max level is the same as the (new) min
        // level, we have a series of alternating levels that
        // is already in order, so there's no more to do.
        //
        boolean swap;
        if ((minLevel & 1) == baseLevel) {
            minLevel += 1;
            swap = maxLevel > minLevel;
        } else {
            swap = runCount > 1;
        }
        if (swap) {
            for (int level = maxLevel - 1; level >= minLevel; --level) {
                for (int i = 0; i < ld.length; i += 2) {
                    if (levels[ld[i]] >= level) {
                        int e = i + 2;
                        while (e < ld.length && levels[ld[e]] >= level) {
                            e += 2;
                        }
                        for (int low = i, hi = e - 2; low < hi; low += 2, hi -= 2) {
                            int x = ld[low]; ld[low] = ld[hi]; ld[hi] = x;
                            x = ld[low+1]; ld[low+1] = ld[hi+1]; ld[hi+1] = x;
                        }
                        i = e + 2;
                    }
                }
            }
        }
        return new Directions(ld);
    }

    // (EW) only can be called prior to Pie because the native method this this calls got removed in
    // Pie
    private static int runBidi(int dir, char[] chs, byte[] chInfo, int n, boolean haveInfo) {
        try {
            Method runBidiMethod = Class.forName("android.text.AndroidBidi").getDeclaredMethod(
                        "runBidi", int.class, char[].class, byte[].class, int.class, boolean.class);
            runBidiMethod.setAccessible(true);
                return (int) runBidiMethod.invoke(null, dir, chs, chInfo, n, haveInfo);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException
                | ClassNotFoundException e) {
            Log.e(TAG, "getRunAdvance: Reflection failed on runBidi: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return Layout.DIR_LEFT_TO_RIGHT;
        }
    }
}
