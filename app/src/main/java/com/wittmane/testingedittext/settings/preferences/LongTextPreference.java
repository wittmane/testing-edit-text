/*
 * Copyright (C) 2025 Eli Wittman
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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;

import com.wittmane.testingedittext.util.PreferenceSummaryManager;

public class LongTextPreference extends Preference {
    private static final String TAG = LongTextPreference.class.getSimpleName();

    private final PreferenceSummaryManager mSummaryManager;

    public LongTextPreference(Context context) {
        super(context);
        mSummaryManager = new PreferenceSummaryManager(this, this::onClick, super::setSummary);
    }

    public LongTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mSummaryManager = new PreferenceSummaryManager(this, this::onClick, super::setSummary);
    }

    public LongTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mSummaryManager = new PreferenceSummaryManager(this, this::onClick, super::setSummary);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public LongTextPreference(Context context, AttributeSet attrs, int defStyleAttr,
                              int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mSummaryManager = new PreferenceSummaryManager(this, this::onClick, super::setSummary);
    }

    @Override
    public void setSummary(CharSequence summary) {
        mSummaryManager.onSetSummary(summary);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        mSummaryManager.onBindView(view);
    }
}
