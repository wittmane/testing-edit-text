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

package com.wittmane.testingedittext.settings;

import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

/**
 * An InputFilter base for only allowing specific characters. This will retain spans from the
 * original text.
 */
public abstract class CharFilter implements InputFilter {

    public CharSequence filter(CharSequence source, int sourceStart, int sourceEnd,
                               Spanned dest, int destStart, int destEnd) {
        boolean willChangeText = false;
        for (int i = sourceStart; i < sourceEnd; i++) {
            char c = source.charAt(i);
            if (!isValidChar(c) || convertChar(c) != c) {
                willChangeText = true;
                break;
            }
        }
        if (!willChangeText) {
            // keep the original
            return null;
        }
        boolean copySpans = source instanceof Spanned;
        StringBuilderProxy stringBuilder;
        if (copySpans) {
            stringBuilder = new StringBuilderProxy(new SpannableStringBuilder(source));
        } else {
            stringBuilder = new StringBuilderProxy(new StringBuilder(source));
        }
        if (source.length() > sourceEnd) {
            stringBuilder.delete(sourceEnd, source.length());
        }
        for (int i = sourceEnd - 1; i >= sourceStart; i--) {
            char currentChar = source.charAt(i);
            if (!isValidChar(currentChar)) {
                stringBuilder.delete(i, i + 1);
            } else {
                char convertedChar = convertChar(currentChar);
                if (convertedChar != currentChar) {
                    stringBuilder.replace(i, convertedChar);
                }
            }
        }
        return stringBuilder.getText();
    }

    protected abstract boolean isValidChar(char c);

    protected abstract char convertChar(char c);

    private static class StringBuilderProxy {
        private final StringBuilder mStringBuilder;
        private final SpannableStringBuilder mSpannableStringBuilder;

        public StringBuilderProxy(StringBuilder stringBuilder) {
            if (stringBuilder == null) {
                throw new IllegalArgumentException("stringBuilder is null");
            }
            mStringBuilder = stringBuilder;
            mSpannableStringBuilder = null;
        }

        public StringBuilderProxy(SpannableStringBuilder stringBuilder) {
            if (stringBuilder == null) {
                throw new IllegalArgumentException("stringBuilder is null");
            }
            mStringBuilder = null;
            mSpannableStringBuilder = stringBuilder;
        }

        public StringBuilderProxy delete(int start, int end) {
            if (mSpannableStringBuilder != null) {
                mSpannableStringBuilder.delete(start, end);
            } else {
                mStringBuilder.delete(start, end);
            }
            return this;
        }

        public StringBuilderProxy replace(int index, char newChar) {
            if (mSpannableStringBuilder != null) {
                mSpannableStringBuilder.replace(index, index + 1, "" + newChar);
            } else {
                mStringBuilder.replace(index, index + 1, "" + newChar);
            }
            return this;
        }

        public CharSequence getText() {
            if (mSpannableStringBuilder != null) {
                return mSpannableStringBuilder;
            } else {
                return mStringBuilder;
            }
        }
    }
}
