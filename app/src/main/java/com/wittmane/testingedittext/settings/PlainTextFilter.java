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
import android.text.Spanned;

/**
 * Input filter to remove spans
 */
public class PlainTextFilter implements InputFilter {
    @Override
    public CharSequence filter(CharSequence source, int sourceStart, int sourceEnd,
                               Spanned dest, int destStart, int destEnd) {
        if (source instanceof Spanned) {
            return source.toString();
        }
        // keep the original
        return null;
    }
}
