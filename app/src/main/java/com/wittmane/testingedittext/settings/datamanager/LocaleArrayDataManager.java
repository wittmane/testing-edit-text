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

package com.wittmane.testingedittext.settings.datamanager;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.wittmane.testingedittext.settings.SharedPreferenceManager;

import java.util.Locale;

public class LocaleArrayDataManager extends ListDataManager<Locale[]> {

    public LocaleArrayDataManager(SharedPreferenceManager prefs, String key) {
        super(prefs, key);
    }

    @Override
    protected int getExtraDataLength() {
        return 0;
    }

    @Override
    protected Locale[] buildFullData(String[] rowData, String[] extraData) {
        Locale[] localeArray = new Locale[rowData.length];
        for (int i = 0; i < rowData.length; i++) {
            localeArray[i] = constructLocaleFromString(rowData[i]);
        }
        return localeArray;
    }

    @NonNull
    public Locale[] readDefaultValue() {
        return new Locale[0];
    }

    @NonNull
    @Override
    protected String[] flattenRowData(final @NonNull Locale[] localeArray) {
        String[] rowData = new String[localeArray.length];
        for (int i = 0; i < localeArray.length; i++) {
            rowData[i] = getLocaleString(localeArray[i]);
        }
        return rowData;
    }

    @NonNull
    protected String[] flattenExtraData(final @NonNull Locale[] fullData) {
        return new String[0];
    }


    /**
     * Creates a locale from a string specification.
     * @param localeString a string specification of a locale, in a format of "ll_cc_variant" where
     * "ll" is a language code, "cc" is a country code.
     */
    public static Locale constructLocaleFromString(final String localeString) {
        final String[] elements = localeString.split("_", 3);
        final Locale locale;
        if (elements.length == 1) {
            locale = new Locale(elements[0] /* language */);
        } else if (elements.length == 2) {
            locale = new Locale(elements[0] /* language */, elements[1] /* country */);
        } else { // elements.length == 3
            locale = new Locale(elements[0] /* language */, elements[1] /* country */,
                    elements[2] /* variant */);
        }
        return locale;
    }

    /**
     * Creates a string specification for a locale.
     * @param locale the locale.
     * @return a string specification of a locale, in a format of "ll_cc_variant" where "ll" is a
     * language code, "cc" is a country code.
     */
    public static String getLocaleString(final Locale locale) {
        if (!TextUtils.isEmpty(locale.getVariant())) {
            return locale.getLanguage() + "_" + locale.getCountry() + "_" + locale.getVariant();
        }
        if (!TextUtils.isEmpty(locale.getCountry())) {
            return locale.getLanguage() + "_" + locale.getCountry();
        }
        return locale.getLanguage();
    }
}
