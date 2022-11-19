/*
 * Copyright (C) 2022 Eli Wittman
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.wittmane.testingedittext;

import android.text.TextUtils;

public class CodePointUtils {

    /**
     * Get the number of code points in some text.
     * @param text the text to count the number of code points.
     * @return the number of code points.
     */
    public static int codePointCount(final CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            return 0;
        }
        return Character.codePointCount(text, 0, text.length());
    }

    /**
     * Convert from an index of code points to an index of chars.
     * @param text the text the indices refer to.
     * @param codePointIndex the index in code points to convert.
     * @return the index in chars.
     */
    public static int codePointIndexToCharIndex(final CharSequence text, int codePointIndex) {
        return Character.offsetByCodePoints(text, 0, codePointIndex);
    }

    /**
     * Returns a CharSequence that is a subsequence of some text based on indices in code points.
     * @param text the full text to find the subsequence.
     * @param start the start index for the subsequence in code points.
     * @param end the end index for the subsequence in code points.
     * @return the specified subsequence.
     */
    public static CharSequence codePointSubsequence(final CharSequence text, int start, int end) {
        return text.subSequence(codePointIndexToCharIndex(text, start),
                codePointIndexToCharIndex(text, end));
    }

    /**
     * Returns the number of chars a code point at a certain index takes up.
     * @param text the full text to evaluate.
     * @param codePointIndex the index of the code point.
     * @return the number of chars for the specified code point.
     */
    public static int codePointLength(final CharSequence text, int codePointIndex) {
        return Character.offsetByCodePoints(text, 0, codePointIndex + 1)
                - Character.offsetByCodePoints(text, 0, codePointIndex);
    }

    /**
     * Return a string for a specified code point.
     * @param codePoint the code point to convert.
     * @return a string representation of the code point.
     */
    public static String codePointString(int codePoint) {
        return new String(Character.toChars(codePoint));
    }
}
