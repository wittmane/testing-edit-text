/*
 * Copyright (C) 2024-2025 Eli Wittman
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
import android.util.AttributeSet;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.Settings.TestFieldSettings;
import com.wittmane.testingedittext.settings.fragments.ReturningTextSettingsFragment;

public class ReturningTextPreference extends PerTestFieldPreference {

    public ReturningTextPreference(Context context) {
        super(context);
        init();
    }

    public ReturningTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ReturningTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ReturningTextPreference(Context context, AttributeSet attrs, int defStyleAttr,
                                int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        setFragment(ReturningTextSettingsFragment.class.getName());
    }

    @Override
    protected void updateDisplayText() {
        int groupIndex = getGroupIndex();
        int fieldIndex = getFieldIndex();
        if (groupIndex != -1 && fieldIndex != -1) {
            TestFieldSettings fieldSettings = Settings.getTestFieldSettings(groupIndex, fieldIndex);
            setSummary(getContext().getString(fieldSettings.overridesTextReturn()
                    ? R.string.overrides_app_level_defaults
                    : R.string.uses_app_level_defaults));
        }
    }
}
