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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.DialogPreference;
import android.text.TextUtils;
import android.util.AttributeSet;

//TODO: (EW) name better
public abstract class DialogPreferenceBase extends DialogPreference {

    private CharSequence mBaseSummary;
    private CharSequence mValueSummary;

    public DialogPreferenceBase(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mBaseSummary = getSummary();
    }

    @Override
    public void setSummary(CharSequence summary) {
        mBaseSummary = summary;
        setFullSummary();
    }

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

    protected SharedPreferences getPrefs() {
        return getPreferenceManager().getSharedPreferences();
    }
}
