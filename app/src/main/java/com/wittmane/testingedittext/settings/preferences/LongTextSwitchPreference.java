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
import android.preference.SwitchPreference;
import android.util.AttributeSet;
import android.view.View;

import com.wittmane.testingedittext.util.PreferenceSummaryManager;

public class LongTextSwitchPreference extends SwitchPreference {
    private static final String TAG = LongTextSwitchPreference.class.getSimpleName();

    private final PreferenceSummaryManager mSummaryManager;

    public LongTextSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mSummaryManager = new PreferenceSummaryManager(this, this::onClick, super::setSummary,
                super::setSummaryOn, super::setSummaryOff);
    }

    @Override
    public void setSummary(CharSequence summary) {
        mSummaryManager.onSetSummary(summary);
    }

    @Override
    public void setSummaryOn(CharSequence summaryOn) {
        if (mSummaryManager != null) {
            mSummaryManager.onSetSummaryOn(summaryOn);
        } else {
            super.setSummaryOn(summaryOn);
        }
    }

    @Override
    public void setSummaryOff(CharSequence summaryOff) {
        if (mSummaryManager != null) {
            mSummaryManager.onSetSummaryOff(summaryOff);
        } else {
            super.setSummaryOff(summaryOff);
        }
    }

    @Override
    public void setChecked(boolean checked) {
        boolean changed = checked != isChecked();
        super.setChecked(checked);
        if (changed) {
            mSummaryManager.onSetChecked(checked);
        }
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        mSummaryManager.onBindView(view);
    }
}
