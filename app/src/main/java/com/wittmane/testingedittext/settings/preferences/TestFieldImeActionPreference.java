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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;

import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.fragments.TestFieldImeActionSettingsFragment;

public class TestFieldImeActionPreference extends SingleFieldPreference {

    public TestFieldImeActionPreference(Context context) {
        super(context);
        init();
    }

    public TestFieldImeActionPreference(Context context, int fieldIndex) {
        super(context, fieldIndex);
        init();
    }

    public TestFieldImeActionPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TestFieldImeActionPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public TestFieldImeActionPreference(Context context, AttributeSet attrs, int defStyleAttr,
                                        int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        setFragment(TestFieldImeActionSettingsFragment.class.getName());
    }

    @Override
    protected void updateSummary() {
        int fieldIndex = getFieldIndex();
        setSummary(getImeActionDescription(Settings.getTestFieldImeActionId(fieldIndex),
                Settings.getTestFieldImeActionLabel(fieldIndex), getContext()));
    }

    public static String getImeActionDescription(int imeActionId, String imeActionLabel,
                                                  Context context) {
        if (imeActionId == 0 && TextUtils.isEmpty(imeActionLabel)) {
            return "";
        }
        return getDescription(imeActionLabel, "" + imeActionId, context);
    }
}
