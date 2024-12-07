/*
 * Copyright (C) 2024 Eli Wittman
 * Copyright (C) 2011 The Android Open Source Project
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

package com.wittmane.testingedittext.aosp.view.textservice;

import androidx.annotation.Nullable;

import android.os.Build;
import android.text.TextUtils;
import android.view.textservice.SpellCheckerSubtype;

import com.wittmane.testingedittext.aosp.internal.inputmethod.SubtypeLocaleUtils;

import java.util.Locale;

/**
 * (EW) content from {@link SpellCheckerSubtype} that is blocked from apps accessing
 */
public class HiddenSpellCheckerSubtype {
    // (EW) added in Nougat. in prior versions, there was a constructLocaleFromString method that
    // got used instead, and the logic was here, rather than in
    // SubtypeLocaleUtils#constructLocaleFromString
    /**
     * @return {@link Locale} constructed from {@link SpellCheckerSubtype#getLanguageTag()}. If the
     * Language Tag is not specified, then try to construct from
     * {@link SpellCheckerSubtype#getLocale()}
     */
    @Nullable
    public static Locale getLocaleObject(SpellCheckerSubtype spellCheckerSubtype) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            String mSubtypeLanguageTag = spellCheckerSubtype.getLanguageTag();
            if (!TextUtils.isEmpty(mSubtypeLanguageTag)) {
                return Locale.forLanguageTag(mSubtypeLanguageTag);
            }
        }
        return SubtypeLocaleUtils.constructLocaleFromString(spellCheckerSubtype.getLocale());
    }
}
