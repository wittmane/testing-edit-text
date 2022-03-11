package com.wittmane.testingedittext;

import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;

import java.text.CharacterIterator;
import java.util.Locale;

public class BreakIterator {
    private static final String TAG = BreakIterator.class.getSimpleName();
    private android.icu.text.BreakIterator mIcuBreakIterator;
    private java.text.BreakIterator mJavaBreakIterator;
    private int mBeginIndex;

    public static final int DONE = VERSION.SDK_INT >= VERSION_CODES.N
            ? android.icu.text.BreakIterator.DONE
            : java.text.BreakIterator.DONE;

    private BreakIterator(android.icu.text.BreakIterator icuBreakIterator) {
        mIcuBreakIterator = icuBreakIterator;
    }

    private BreakIterator(java.text.BreakIterator javaBreakIterator) {
        mJavaBreakIterator = javaBreakIterator;
    }

    public static BreakIterator getWordInstance(Locale locale) {
        if (VERSION.SDK_INT >= VERSION_CODES.N) {
            return new BreakIterator(android.icu.text.BreakIterator.getWordInstance(locale));
        } else {
            return new BreakIterator(java.text.BreakIterator.getWordInstance(locale));
        }
    }

    public void setText(CharacterIterator newText) {
        if (VERSION.SDK_INT >= VERSION_CODES.N) {
            mIcuBreakIterator.setText(newText);
        } else {
            // using a CharacterIterator in older versions doesn't work correctly with a non-zero
            // begin index. Note that the following would be used:
            // https://android.googlesource.com/platform/libcore/+/refs/heads/lollipop-mr1-release/luni/src/main/java/java/text/RuleBasedBreakIterator.java
            // https://android.googlesource.com/platform/libcore/+/refs/heads/lollipop-mr1-release/luni/src/main/java/libcore/icu/NativeBreakIterator.java
            // https://android.googlesource.com/platform/libcore/+/refs/heads/lollipop-mr1-release/luni/src/main/native/libcore_icu_NativeBreakIterator.cpp
            // Based on a review of that code, this appears to be the source of the issue:
            // When calling NativeBreakIterator#setText, it would iterate the text to build a
            // string, and this is what is passed to the native code (no offset for the first
            // character). This means that when NativeBreakIterator#getText is called, it calls the
            // native implementation to get the current index using the string, which doesn't
            // include the offset for start index, so the returned index will be shifted, but that
            // isn't accounted for when it passes that value to the iterator's setIndex, which
            // causes crashes when the current index excluding the offset is less than the offset,
            // such as when calling #isBoundary > RuleBasedBreakIterator#isBoundary >
            // RuleBasedBreakIterator#checkOffset
            //TODO: (EW) diff the java and icu versions to verify this was the problem
            //TODO: (EW) verify this isn't a problem for other versions. the change to use the
            // character iterator rather than just a substring started in oreo. based on the code
            // for the icu break iterator, this issue should be fixed, but test to be sure.
            // https://android.googlesource.com/platform/external/icu/+/refs/heads/marshmallow-release/icu4j/main/classes/core/src/com/ibm/icu/text/RuleBasedBreakIterator.java
            mBeginIndex = newText.getBeginIndex();
            StringBuilder sb = new StringBuilder();
            for (char c = newText.first(); c != CharacterIterator.DONE; c = newText.next()) {
                sb.append(c);
            }
            mJavaBreakIterator.setText(sb.toString());
        }
    }

    public int preceding(int offset) {
        if (VERSION.SDK_INT >= VERSION_CODES.N) {
            return mIcuBreakIterator.preceding(offset);
        } else {
            return mJavaBreakIterator.preceding(offset - mBeginIndex) + mBeginIndex;
        }
    }

    public int following(int offset) {
        if (VERSION.SDK_INT >= VERSION_CODES.N) {
            return mIcuBreakIterator.following(offset);
        } else {
            return mJavaBreakIterator.following(offset - mBeginIndex) + mBeginIndex;
        }
    }

    public boolean isBoundary(int offset) {
        if (VERSION.SDK_INT >= VERSION_CODES.N) {
            return mIcuBreakIterator.isBoundary(offset);
        } else {
            return mJavaBreakIterator.isBoundary(offset - mBeginIndex);
        }
    }
}
