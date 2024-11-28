/*
 * Copyright (C) 2022-2024 Eli Wittman
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
import android.text.InputType;
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
import com.wittmane.testingedittext.settings.datamanager.LocaleArrayDataManager;

import java.util.Locale;

public class LocaleEntryListPreference
        extends EntryListPreference<Locale, Locale[], LocaleArrayDataManager> {

    public LocaleEntryListPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected Locale[] getRowData(final Locale[] fullData) {
        return fullData;
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
                R.string.locale_language, false);
        EditText countryView = createEditText(data != null ? data.getCountry() : "",
                R.string.locale_country, true);
        EditText variantView = createEditText(data != null ? data.getVariant() : "",
                R.string.locale_variant, false);
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

                updateAcceptButtonState();
            }
        };
        textWatcher.afterTextChanged(null);

        // documentation for Locale states that the language field should be an ISO 639 alpha-2 or
        // alpha-3 language code, or it should be a registered language subtags up to 8 alpha
        // letters, and although the language field is case insensitive, Locale always canonicalizes
        // to lower case. well-formed values have the form [a-zA-Z]{2,8}
        languageView.setFilters(new InputFilter[] {
                new AlphaFilter(),
                new LowerCaseFilter(),
                new LengthFilter(8)
        });
        // documentation for Locale states that the country (region) field should be an ISO 3166
        // alpha-2 country code or UN M.49 numeric-3 area code, and although The country (region)
        // field is case insensitive, Locale always canonicalizes to upper case. well-formed values
        // have the form [a-zA-Z]{2} | [0-9]{3}
        countryView.setFilters(new InputFilter[] {
                new AlphaNumericFilter(),
                new AllCaps(),
                new LengthFilter(3)
        });
        // documentation for Locale states that the variant field should be any arbitrary value
        // (case sensitive), and multiple variant values should be separated by underscore or
        // hyphen. well-formed values have the form SUBTAG (('_'|'-') SUBTAG)* where
        // SUBTAG = [0-9][0-9a-zA-Z]{3} | [0-9a-zA-Z]{5,8}
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

    private EditText createEditText(CharSequence text, int hintResId, boolean caps) {
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
        editText.setInputType(InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE
                | (caps ? InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS : 0));
        return editText;
    }

    @Override
    protected void setExtraDataUI(Locale[] data) {
    }

    @Override
    protected boolean isDataValid() {
        for (Locale locale : getUIData()) {
            if (!isValidLocale(LocaleArrayDataManager.getLocaleString(locale))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidLocale(String localeString) {
        // based on the documentation for Locale
        String languageRegex = "[a-zA-Z]{2,8}";
        String countryRegex = "[a-zA-Z]{2}|[0-9]{3}";
        String variantSubtagRegex = "[0-9][0-9a-zA-Z]{3}|[0-9a-zA-Z]{5,8}";
        return localeString.matches(
                "^" + languageRegex
                        + "(?:_(?:" + countryRegex + ")?)?"
                        + "(?:_(?:" + variantSubtagRegex + ")?)?"
                        + "(?:[_-](?:" + variantSubtagRegex + "))*$");
    }

    @NonNull
    @Override
    protected Locale[] getUIData() {
        return getUIRowDataList().toArray(new Locale[0]);
    }

    @Override
    protected Locale getUIRowData(View[] rowContent) {
        EditText languageView = (EditText)rowContent[0];
        EditText countryView = (EditText)rowContent[1];
        EditText variantView = (EditText)rowContent[2];
        return new Locale(
                languageView.getText().toString(),
                countryView.getText().toString(),
                variantView.getText().toString());
    }

    @Override
    protected boolean isRowEmpty(Locale rowData) {
        return TextUtils.isEmpty(rowData.getLanguage())
                && TextUtils.isEmpty(rowData.getCountry())
                && TextUtils.isEmpty(rowData.getVariant());
    }

    @Override
    protected LocaleArrayDataManager createDataManager(SharedPreferenceManager prefs, String key) {
        return new LocaleArrayDataManager(prefs, key);
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
}
