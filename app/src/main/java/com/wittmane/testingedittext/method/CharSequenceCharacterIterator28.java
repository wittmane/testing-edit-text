package com.wittmane.testingedittext.method;

import androidx.annotation.NonNull;

import java.text.CharacterIterator;

/**
 * An implementation of {@link java.text.CharacterIterator} that iterates over a given CharSequence.
 * {@hide}
 */
public class CharSequenceCharacterIterator28 implements CharacterIterator {
    private final int mBeginIndex, mEndIndex;
    private int mIndex;
    private final CharSequence mCharSeq;

    /**
     * Constructs the iterator given a CharSequence and a range. The position of the iterator index
     * is set to the beginning of the range.
     */
    public CharSequenceCharacterIterator28(@NonNull CharSequence text, int start, int end) {
        mCharSeq = text;
        mBeginIndex = mIndex = start;
        mEndIndex = end;
    }

    public char first() {
        mIndex = mBeginIndex;
        return current();
    }

    public char last() {
        if (mBeginIndex == mEndIndex) {
            mIndex = mEndIndex;
            return DONE;
        } else {
            mIndex = mEndIndex - 1;
            return mCharSeq.charAt(mIndex);
        }
    }

    public char current() {
        return (mIndex == mEndIndex) ? DONE : mCharSeq.charAt(mIndex);
    }

    public char next() {
        mIndex++;
        if (mIndex >= mEndIndex) {
            mIndex = mEndIndex;
            return DONE;
        } else {
            return mCharSeq.charAt(mIndex);
        }
    }

    public char previous() {
        if (mIndex <= mBeginIndex) {
            return DONE;
        } else {
            mIndex--;
            return mCharSeq.charAt(mIndex);
        }
    }

    public char setIndex(int position) {
        if (mBeginIndex <= position && position <= mEndIndex) {
            mIndex = position;
            return current();
        } else {
            throw new IllegalArgumentException("invalid position");
        }
    }

    public int getBeginIndex() {
        return mBeginIndex;
    }

    public int getEndIndex() {
        return mEndIndex;
    }

    public int getIndex() {
        return mIndex;
    }

    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError();
        }
    }
}
