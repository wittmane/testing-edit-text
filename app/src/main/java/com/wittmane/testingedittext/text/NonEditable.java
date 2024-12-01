/*
 * Copyright (C) 2024 Eli Wittman
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

package com.wittmane.testingedittext.text;

import android.text.Editable;
import android.text.InputFilter;
import android.text.NoCopySpan;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;

import java.util.HashSet;

/**
 * An {@link Editable} that blocks all edits. It only allows adding {@link NoCopySpan}s to allow
 * this to work in an {@link android.widget.EditText}.
 */
public class NonEditable implements Editable {
    private static final String TAG = NonEditable.class.getSimpleName();

    private final Editable mWrappedEditable;
    private final HashSet<Object> mExtraSpans = new HashSet<>();

    public NonEditable(CharSequence source) {
        mWrappedEditable = new SpannableStringBuilder(source);
    }

    @NonNull
    @Override
    public Editable replace(int st, int en, CharSequence source, int start, int end) {
        return this;
    }

    @NonNull
    @Override
    public Editable replace(int st, int en, CharSequence text) {
        return this;
    }

    @NonNull
    @Override
    public Editable insert(int where, CharSequence text, int start, int end) {
        return this;
    }

    @NonNull
    @Override
    public Editable insert(int where, CharSequence text) {
        return this;
    }

    @NonNull
    @Override
    public Editable delete(int st, int en) {
        return this;
    }

    @NonNull
    @Override
    public Editable append(CharSequence text) {
        return this;
    }

    @NonNull
    @Override
    public Editable append(CharSequence text, int start, int end) {
        return this;
    }

    @NonNull
    @Override
    public Editable append(char text) {
        return this;
    }

    @Override
    public void clear() {
    }

    @Override
    public void clearSpans() {
        for (Object span : mExtraSpans) {
            mWrappedEditable.removeSpan(span);
        }
        mExtraSpans.clear();
    }

    @Override
    public void setFilters(InputFilter[] filters) {
    }

    @Override
    public InputFilter[] getFilters() {
        return new InputFilter[0];
    }

    @Override
    public void getChars(int start, int end, char[] destination, int destinationOffset) {
        mWrappedEditable.getChars(start, end, destination, destinationOffset);
    }

    @Override
    public void setSpan(Object what, int start, int end, int flags) {
        // presumably any NoCopySpan should indicate something that doesn't functionally change the
        // content of the char sequence, unlike a span changing the font/style (other than maybe a
        // temporary state, like an indicator for the composing text), so we'll allow those
        if (what instanceof NoCopySpan) {
            mExtraSpans.add(what);
            mWrappedEditable.setSpan(what, start, end, flags);
        }
    }

    @Override
    public void removeSpan(Object what) {
        if (mExtraSpans.contains(what)) {
            mExtraSpans.remove(what);
            mWrappedEditable.removeSpan(what);
        }
    }

    @Override
    public <T> T[] getSpans(int start, int end, Class<T> type) {
        return mWrappedEditable.getSpans(start, end, type);
    }

    @Override
    public int getSpanStart(Object tag) {
        return mWrappedEditable.getSpanStart(tag);
    }

    @Override
    public int getSpanEnd(Object tag) {
        return mWrappedEditable.getSpanEnd(tag);
    }

    @Override
    public int getSpanFlags(Object tag) {
        return mWrappedEditable.getSpanFlags(tag);
    }

    @Override
    public int nextSpanTransition(int start, int limit, Class type) {
        return mWrappedEditable.nextSpanTransition(start, limit, type);
    }

    @Override
    public int length() {
        return mWrappedEditable.length();
    }

    @Override
    public char charAt(int index) {
        return mWrappedEditable.charAt(index);
    }

    @NonNull
    @Override
    public CharSequence subSequence(int start, int end) {
        return new NonEditable(mWrappedEditable.subSequence(start, end));
    }

    @NonNull
    @Override
    public String toString() {
        return mWrappedEditable.toString();
    }

    @Override
    public boolean equals(Object o) {
        return mWrappedEditable.equals(o);
    }

    @Override
    public int hashCode() {
        return mWrappedEditable.hashCode();
    }

    /**
     * Factory used to create new {@link NonEditable}s. This can be used with
     * {@link android.widget.EditText#setEditableFactory(Factory)} to make an
     * {@link android.widget.EditText} simply uneditable without being entirely disabled.
     */
    public static class NonEditableFactory extends Factory {
        private static final NonEditableFactory sInstance = new NonEditableFactory();

        /**
         * Returns the standard NonEditable Factory.
         */
        public static NonEditableFactory getInstance() {
            return sInstance;
        }

        /**
         * Returns a new {@link NonEditableFactory} from the specified CharSequence.
         */
        @Override
        public Editable newEditable(CharSequence source) {
            return new NonEditable(source);
        }
    }
}
