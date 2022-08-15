/*
 * Copyright (C) 2022 Eli Wittman
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

package com.wittmane.testingedittext.aosp.text;

import android.text.Selection;
import android.text.Spannable;

import com.wittmane.testingedittext.wrapper.BreakIterator;

/**
 * (EW) content from Selection that is blocked from apps accessing
 */
public class HiddenSelection {
    // (EW) this was restricted with @hide
    public interface PositionIterator {
        int DONE = BreakIterator.DONE;

        int preceding(int position);
        int following(int position);
    }

    // (EW) this was restricted with @hide and @UnsupportedAppUsage
    public static boolean moveToPreceding(
            Spannable text, PositionIterator iterator, boolean extendSelection) {
        final int offset = iterator.preceding(Selection.getSelectionEnd(text));
        if (offset != PositionIterator.DONE) {
            if (extendSelection) {
                Selection.extendSelection(text, offset);
            } else {
                Selection.setSelection(text, offset);
            }
        }
        return true;
    }

    // (EW) this was restricted with @hide and @UnsupportedAppUsage
    public static boolean moveToFollowing(
            Spannable text, PositionIterator iterator, boolean extendSelection) {
        final int offset = iterator.following(Selection.getSelectionEnd(text));
        if (offset != PositionIterator.DONE) {
            if (extendSelection) {
                Selection.extendSelection(text, offset);
            } else {
                Selection.setSelection(text, offset);
            }
        }
        return true;
    }
}
