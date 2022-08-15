/*
 * Copyright (C) 2016 The Android Open Source Project
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

import androidx.annotation.NonNull;

import java.text.CharacterIterator;

// (EW) the AOSP version of this is hidden from apps, so it had to be copied here in order to be
// used in other hidden classes
/**
 * An implementation of {@link java.text.CharacterIterator} that iterates over a given CharSequence.
 */
public class CharSequenceCharacterIterator implements CharacterIterator {
    private final int mBeginIndex, mEndIndex;
    private int mIndex;
    private final CharSequence mCharSeq;

    /**
     * Constructs the iterator given a CharSequence and a range. The position of the iterator index
     * is set to the beginning of the range.
     */
    public CharSequenceCharacterIterator(@NonNull CharSequence text, int start, int end) {
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

    @NonNull
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError();
        }
    }
}
