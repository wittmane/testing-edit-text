/*
 * Copyright (C) 2024 Eli Wittman
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

package com.wittmane.testingedittext.aosp.text.method;

import android.os.Build;
import android.text.method.DigitsKeyListener;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.HashMap;
import java.util.Locale;

/**
 * Simple extension of DigitsKeyListener (for digits-only text entry) that only supports the variant
 * that includes the locale (ie not string mode) to allow creating a copy with a different locale.
 * This is just a copy of a small bit of DigitsKeyListener to get
 * {@link LocaleDigitsKeyListener#getInstance(Locale, LocaleDigitsKeyListener)} since
 * DigitsKeyListener's version is hidden.
 */
@RequiresApi(api = Build.VERSION_CODES.O)
public class LocaleDigitsKeyListener extends DigitsKeyListener {
    private final boolean mSign;
    private final boolean mDecimal;

    private static final int SIGN = 1;
    private static final int DECIMAL = 2;

    public LocaleDigitsKeyListener(@Nullable Locale locale, boolean sign, boolean decimal) {
        super(locale, sign, decimal);
        mSign = sign;
        mDecimal = decimal;
    }

    private static final Object sLocaleCacheLock = new Object();
    @GuardedBy("sLocaleCacheLock")
    private static final HashMap<Locale, LocaleDigitsKeyListener[]> sLocaleInstanceCache =
            new HashMap<>();

    /**
     * Returns a LocaleDigitsKeyListener that accepts the locale-appropriate digits, plus the
     * locale-appropriate plus or minus sign (only at the beginning) and/or the locale-appropriate
     * decimal separator (only one per field) if specified.
     */
    @NonNull
    public static LocaleDigitsKeyListener getInstance(
            @Nullable Locale locale, boolean sign, boolean decimal) {
        final int kind = (sign ? SIGN : 0) | (decimal ? DECIMAL : 0);
        synchronized (sLocaleCacheLock) {
            LocaleDigitsKeyListener[] cachedValue = sLocaleInstanceCache.get(locale);
            if (cachedValue != null && cachedValue[kind] != null) {
                return cachedValue[kind];
            }
            if (cachedValue == null) {
                cachedValue = new LocaleDigitsKeyListener[4];
                sLocaleInstanceCache.put(locale, cachedValue);
            }
            return cachedValue[kind] = new LocaleDigitsKeyListener(locale, sign, decimal);
        }
    }

    /**
     * Returns a LocaleDigitsKeyListener based on an the settings of a existing
     * LocaleDigitsKeyListener, with the locale modified.
     */
    @NonNull
    public static LocaleDigitsKeyListener getInstance(
            @Nullable Locale locale,
            @NonNull LocaleDigitsKeyListener listener) {
        return getInstance(locale, listener.mSign, listener.mDecimal);
    }
}
