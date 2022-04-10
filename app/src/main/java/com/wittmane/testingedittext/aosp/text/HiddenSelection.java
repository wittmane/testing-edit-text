package com.wittmane.testingedittext.aosp.text;

import android.text.Selection;
import android.text.Spannable;

import com.wittmane.testingedittext.BreakIterator;

/**
 * (EW) content from Selection that is blocked for app developers to access
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
            Spannable text, PositionIterator iter, boolean extendSelection) {
        final int offset = iter.preceding(Selection.getSelectionEnd(text));
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
            Spannable text, PositionIterator iter, boolean extendSelection) {
        final int offset = iter.following(Selection.getSelectionEnd(text));
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
