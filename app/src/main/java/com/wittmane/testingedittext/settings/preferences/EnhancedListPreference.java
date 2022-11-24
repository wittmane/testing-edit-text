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
import android.content.res.TypedArray;
import android.preference.ListPreference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

//TODO: (EW) see if there is a way to reduce duplicate code with DialogPreferenceBase
/**
 * A ListPreference with a few minor enhancements.
 * - shows its value in the summary
 * - forces a default value when one isn't specified
 * - allows the title to wrap
 */
public class EnhancedListPreference extends ListPreference {

    private CharSequence mBaseSummary;
    private CharSequence mValueSummary;
    private CharSequence mDefaultValue;

    public EnhancedListPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mBaseSummary = getSummary();
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
        mBaseSummary = summary;
        setFullSummary();
    }

    /**
     * Set the secondary summary for the preference to display the value (as opposed to the regular
     * summary as a description of the preference)
     * @param summary the display text for the current value of the preference
     */
    protected void setValueSummary(CharSequence summary) {
        mValueSummary = summary;
        setFullSummary();
    }

    private void setFullSummary() {
        if (TextUtils.isEmpty(mValueSummary)) {
            super.setSummary(mBaseSummary);
        } else if (TextUtils.isEmpty(mBaseSummary)) {
            super.setSummary(mValueSummary);
        } else {
            super.setSummary(new StringBuilder()
                    .append(mBaseSummary)
                    .append('\n')
                    .append(mValueSummary));
        }
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

        // allow the title to wrap
        TextView titleTextView = view.findViewById(android.R.id.title);
        if (titleTextView != null) {
            titleTextView.setSingleLine(false);
        }
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
}
