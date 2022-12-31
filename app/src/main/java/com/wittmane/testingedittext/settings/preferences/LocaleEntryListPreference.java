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

package com.wittmane.testingedittext.settings.preferences;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputFilter.AllCaps;
import android.text.InputFilter.LengthFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.AlphaFilter;
import com.wittmane.testingedittext.settings.AlphaNumericFilter;
import com.wittmane.testingedittext.settings.LowerCaseFilter;
import com.wittmane.testingedittext.settings.SharedPreferenceManager;
import com.wittmane.testingedittext.settings.preferences.LocaleEntryListPreference.Reader;

import java.util.Locale;

public class LocaleEntryListPreference extends SimpleEntryListPreference<Locale, Reader> {

    public LocaleEntryListPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected View[] createRowContent(Locale data, TableRow tableRow) {

        LinearLayout cellLayout = new LinearLayout(getContext());
        cellLayout.setOrientation(LinearLayout.VERTICAL);
        TableRow.LayoutParams editTextLayoutParams = new TableRow.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        cellLayout.setLayoutParams(editTextLayoutParams);

        LinearLayout textFieldLayout = new LinearLayout(getContext());
        textFieldLayout.setOrientation(LinearLayout.HORIZONTAL);

        EditText languageView = createEditText(data != null ? data.getLanguage() : "",
                R.string.locale_language);
        EditText countryView = createEditText(data != null ? data.getCountry() : "",
                R.string.locale_country);
        EditText variantView = createEditText(data != null ? data.getVariant() : "",
                R.string.locale_variant);
        textFieldLayout.addView(languageView);
        textFieldLayout.addView(countryView);
        textFieldLayout.addView(variantView);

        cellLayout.addView(textFieldLayout);

        TextView localeNameView = new TextView(getContext());

        cellLayout.addView(localeNameView);

        tableRow.addView(cellLayout);

        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count,
                                          int after) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before,
                                      int count) { }

            @Override
            public void afterTextChanged(Editable editable) {
                Locale locale = new Locale(languageView.getText().toString(),
                        countryView.getText().toString(), variantView.getText().toString());
                localeNameView.setText(locale.getDisplayName());
            }
        };
        textWatcher.afterTextChanged(null);

        languageView.setFilters(new InputFilter[] {
                new AlphaFilter(),
                new LowerCaseFilter(),
                new LengthFilter(8)
        });
        countryView.setFilters(new InputFilter[] {
                new AlphaNumericFilter(),
                new AllCaps(),
                new LengthFilter(3)
        });
        variantView.setFilters(new InputFilter[] {
                new AlphaNumericFilter() {
                    @Override
                    protected boolean isValidChar(char c) {
                        return super.isValidChar(c) || c == '_' || c == '-';
                    }
                }
        });

        languageView.addTextChangedListener(textWatcher);
        countryView.addTextChangedListener(textWatcher);
        variantView.addTextChangedListener(textWatcher);

        return new View[] {
                languageView,
                countryView,
                variantView,
                localeNameView
        };
    }

    protected EditText createEditText(CharSequence text, int hintResId) {
        EditText editText = new EditText(getContext());
        editText.setSingleLine();
        LinearLayout.LayoutParams editTextLayoutParams = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        editText.setLayoutParams(editTextLayoutParams);
        editText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        if (!TextUtils.isEmpty(text)) {
            editText.setText(text);
        }
        editText.setHint(hintResId);
        return editText;
    }

    @Override
    protected boolean isRowEmpty(Locale rowData) {
        return TextUtils.isEmpty(rowData.getLanguage())
                && TextUtils.isEmpty(rowData.getCountry())
                && TextUtils.isEmpty(rowData.getVariant());
    }

    @NonNull
    @Override
    protected Locale[] getUIData() {
        Locale[] localeArray = new Locale[mRows.size()];
        for (int i = 0; i < mRows.size(); i++) {
            EditText languageView = (EditText)mRows.get(i).mContent[0];
            EditText countryView = (EditText)mRows.get(i).mContent[1];
            EditText variantView = (EditText)mRows.get(i).mContent[2];
            localeArray[i] = new Locale(
                    languageView.getText().toString(),
                    countryView.getText().toString(),
                    variantView.getText().toString());
        }
        return localeArray;
    }

    @NonNull
    @Override
    protected String[] flattenDataArray(final @NonNull Locale[] dataArray) {
        String[] result = new String[dataArray.length];
        for (int i = 0; i < dataArray.length; i++) {
            result[i] = getLocaleString(dataArray[i]);
        }
        return result;
    }

    @Override
    protected Reader createReader(SharedPreferenceManager prefs, String key) {
        return new Reader(prefs, key);
    }

    public static class Reader extends SimpleEntryListPreference.SimpleReader<Locale> {

        public Reader(SharedPreferenceManager prefs, String key) {
            super(prefs, key);
        }

        @Override
        protected Locale[] buildRowData(String[] rowData) {
            Locale[] localeArray = new Locale[rowData.length];
            for (int i = 0; i < rowData.length; i++) {
                localeArray[i] = constructLocaleFromString(rowData[i]);
            }
            return localeArray;
        }

        @NonNull
        protected Locale[] readDefaultValue() {
            return new Locale[0];
        }
    }

    @Override
    protected String getValueText(final @NonNull Locale[] dataArray) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < dataArray.length; i++) {
            if (isRowEmpty(dataArray[i])) {
                continue;
            }
            if (sb.length() != 0) {
                sb.append(", ");
            }
            sb.append(dataArray[i].getDisplayName());
        }
        return sb.toString();
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
