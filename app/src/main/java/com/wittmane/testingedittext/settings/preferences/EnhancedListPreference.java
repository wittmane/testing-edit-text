/*
 * Copyright (C) 2022-2025 Eli Wittman
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
import android.content.res.TypedArray;
import android.preference.ListPreference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.wittmane.testingedittext.util.PreferenceSummaryManager;

/**
 * A ListPreference with a few minor enhancements.
 * - shows its value in the summary
 * - forces a default value when one isn't specified
 * - allows the title to wrap
 * - supports long clicking to read the full text if ellipsized
 */
public class EnhancedListPreference extends ListPreference {
    private static final String TAG = EnhancedListPreference.class.getSimpleName();

    private final PreferenceSummaryManager mSummaryManager;
    private CharSequence mDefaultValue;

    public EnhancedListPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mSummaryManager = new PreferenceSummaryManager(this, this::onClick, super::setSummary);
        // if a default hasn't already been defined, use the first entry
        if (TextUtils.isEmpty(mDefaultValue)) {
            CharSequence[] entryValues = getEntryValues();
            if (entryValues != null && entryValues.length > 0) {
                setDefaultValue(entryValues[0]);
            }
        }
    }

    @Override
    public void setDefaultValue(Object defaultValue) {
        super.setDefaultValue(defaultValue);
        if (defaultValue instanceof CharSequence) {
            mDefaultValue = (CharSequence)defaultValue;
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        Object defaultValue = super.onGetDefaultValue(a, index);
        if (defaultValue instanceof CharSequence) {
            mDefaultValue = (CharSequence)defaultValue;
        }
        return defaultValue;
    }

    @Override
    public void setSummary(CharSequence summary) {
        mSummaryManager.onSetSummary(summary);
    }

    /**
     * Set the secondary summary for the preference to display the value (as opposed to the regular
     * summary as a description of the preference)
     * @param summary the display text for the current value of the preference
     */
    protected void setValueSummary(CharSequence summary) {
        mSummaryManager.onSetValueSummary(summary);
    }

    protected void updateValueSummary() {
        setValueSummary(getEntry());
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
        updateValueSummary();
        return view;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        updateValueSummary();

        mSummaryManager.onBindView(view);
    }

    @Override
    protected void onAttachedToActivity() {
        super.onAttachedToActivity();
        updateValueSummary();
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        updateValueSummary();
    }

    @Override
    public void setValue(String value) {
        super.setValue(value);
        updateValueSummary();
    }
}
