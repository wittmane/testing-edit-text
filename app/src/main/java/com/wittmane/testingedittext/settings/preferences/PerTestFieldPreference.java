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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;

import com.wittmane.testingedittext.R;

import java.util.ArrayList;
import java.util.List;

import static com.wittmane.testingedittext.settings.Settings.BASE_FIELD_INDEX;
import static com.wittmane.testingedittext.settings.fragments.PerTestFieldSettingsFragment.FIELD_INDEX_BUNDLE_KEY;
import static com.wittmane.testingedittext.settings.fragments.PerTestGroupSettingsFragment.GROUP_INDEX_BUNDLE_KEY;

/**
 * Preference to link to a test field specific settings screen.
 */
public abstract class PerTestFieldPreference extends PerTestGroupPreference {
    private static final String TAG = PerTestFieldPreference.class.getSimpleName();

    private int mFieldIndex = -1;
    private Bundle mExtras;

    public PerTestFieldPreference(Context context) {
        super(context);
    }

    public PerTestFieldPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PerTestFieldPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public PerTestFieldPreference(Context context, AttributeSet attrs, int defStyleAttr,
                                  int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setFieldIndex(int groupIndex, int fieldIndex) {
        setGroupIndex(groupIndex);
        if (mFieldIndex == fieldIndex) {
            return;
        }
        mFieldIndex = fieldIndex;
        mExtras = null;
        updateSummary();
    }

    public int getFieldIndex() {
        return mFieldIndex;
    }

    @Override
    public Bundle getExtras() {
        if (mFieldIndex == BASE_FIELD_INDEX) {
            return super.getExtras();
        }
        if (mExtras == null) {
            mExtras = new Bundle();
            //TODO: (EW) should this call super to manage group index?
            mExtras.putString(GROUP_INDEX_BUNDLE_KEY, "" + getGroupIndex());
            mExtras.putString(FIELD_INDEX_BUNDLE_KEY, "" + getFieldIndex());
        }
        return mExtras;
    }

    @Override
    public Bundle peekExtras() {
        if (getGroupIndex() < 0 || getFieldIndex() < 0) {
            Log.e(TAG, "No field index for extras");
            return super.getExtras();
        }
        return mExtras;
    }

    protected static String getDescription(String display, Context context) {
        return getDescription(display, (List<String>)null, context);
    }

    protected static String getDescription(List<String> details, Context context) {
        return getDescription(null, details, context);
    }

    protected static String getDescription(String baseDisplay, String details,
                                         Context context) {
        List<String> detailsList;
        if (details != null) {
            detailsList = new ArrayList<>();
            detailsList.add(details);
        } else {
            detailsList = null;
        }
        return getDescription(baseDisplay, detailsList, context);
    }

    protected static String getDescription(String baseDisplay, List<String> details,
                                         Context context) {
        String valueSummary;
        if (details == null || details.size() == 0) {
            if (TextUtils.isEmpty(baseDisplay)) {
                return null;
            }
            valueSummary = baseDisplay;
        } else {
            String detailsDisplay;
            if (details.size() == 1) {
                detailsDisplay = details.get(0);
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < details.size(); i++) {
                    if (i > 0) {
                        sb.append(context.getString(
                                R.string.test_field_property_details_delimiter));
                    }
                    sb.append(details.get(i));
                }
                detailsDisplay = sb.toString();
            }
            if (TextUtils.isEmpty(baseDisplay)) {
                valueSummary = detailsDisplay;
            } else {
                valueSummary = context.getString(R.string.test_field_property_detailed_summary,
                        baseDisplay, detailsDisplay);
            }
        }
        return valueSummary;
    }
}
