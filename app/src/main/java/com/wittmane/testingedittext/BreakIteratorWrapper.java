package com.wittmane.testingedittext;

import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;

import java.text.CharacterIterator;
import java.util.Locale;

//TODO: (EW) does this wrapper make sense? what benefit do we get from using the ICU version? should
// we just use the java version and not bother with the wrapper?
public class BreakIteratorWrapper {
    private android.icu.text.BreakIterator mIcuBreakIterator;
    private java.text.BreakIterator mJavaBreakIterator;

    public BreakIteratorWrapper(Locale locale) {
        if (VERSION.SDK_INT >= VERSION_CODES.N) {
            mIcuBreakIterator = android.icu.text.BreakIterator.getWordInstance(locale);
        } else {
            mJavaBreakIterator = java.text.BreakIterator.getWordInstance(locale);
        }
    }

    public void setText (String newText) {
        if (VERSION.SDK_INT >= VERSION_CODES.N) {
            mIcuBreakIterator.setText(newText);
        } else {
            mJavaBreakIterator.setText(newText);
        }
    }

    public void setText (CharacterIterator newText) {
        if (VERSION.SDK_INT >= VERSION_CODES.N) {
            mIcuBreakIterator.setText(newText);
        } else {
            mJavaBreakIterator.setText(newText);
        }
    }

    public int preceding (int offset) {
        if (VERSION.SDK_INT >= VERSION_CODES.N) {
            return mIcuBreakIterator.preceding(offset);
        } else {
            return mJavaBreakIterator.preceding(offset);
        }
    }

    public int following (int offset) {
        if (VERSION.SDK_INT >= VERSION_CODES.N) {
            return mIcuBreakIterator.following(offset);
        } else {
            return mJavaBreakIterator.following(offset);
        }
    }

    public boolean isBoundary (int offset) {
        if (VERSION.SDK_INT >= VERSION_CODES.N) {
            return mIcuBreakIterator.isBoundary(offset);
        } else {
            return mJavaBreakIterator.isBoundary(offset);
        }
    }
}
