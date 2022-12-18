/*
 * Copyright (C) 2022 Eli Wittman
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

package com.wittmane.testingedittext;

import android.text.Spanned;

import java.lang.reflect.Array;

public class SpanUtils {

    public static boolean textAndSpansMatch(CharSequence textA, CharSequence textB) {
        if (textA == null && textB == null) {
            return true;
        }
        if (textA == null || textB == null) {
            // only one is null
            return false;
        }
        if (!textA.toString().equals(textB.toString())) {
            return false;
        }
        Object[] spansA = getSpans(textA, Object.class);
        Object[] spansB = getSpans(textB, Object.class);
        if (spansA.length != spansB.length) {
            return false;
        }
        if (spansA.length == 0) {
            // no spans and already validated text is the same
            return true;
        }
        return spansMatch(spansA, (Spanned)textA, spansB, (Spanned)textB);
    }

    public static <T> T[] getSpans(CharSequence text, Class<T> type) {
        if (text instanceof Spanned) {
            return ((Spanned)text).getSpans(0, text.length(), type);
        } else {
            return (T[])Array.newInstance(type, 0);
        }
    }

    // based on SpannableStringBuilder#equals
    // this may only really work with the exact span objects in both since at least some (such as
    // StyleSpan) don't override equals, so it defaults to comparing the reference
    public static boolean spansMatch(Object[] spansA, Spanned spannedA,
                              Object[] spansB, Spanned spannedB) {
        if (spansA.length != spansB.length) {
            return false;
        }
        for (int i = 0; i < spansA.length; ++i) {
            final Object spanA = spansA[i];
            final Object spanB = spansB[i];
            if (spanA == spannedA) {
                if (spannedB != spanB ||
                        spannedA.getSpanStart(spanA) != spannedB.getSpanStart(spanB) ||
                        spannedA.getSpanEnd(spanA) != spannedB.getSpanEnd(spanB) ||
                        spannedA.getSpanFlags(spanA) != spannedB.getSpanFlags(spanB)) {
                    return false;
                }
            } else if (!spanA.equals(spanB) ||
                    spannedA.getSpanStart(spanA) != spannedB.getSpanStart(spanB) ||
                    spannedA.getSpanEnd(spanA) != spannedB.getSpanEnd(spanB) ||
                    spannedA.getSpanFlags(spanA) != spannedB.getSpanFlags(spanB)) {
                return false;
            }
        }
        return true;
    }
}
