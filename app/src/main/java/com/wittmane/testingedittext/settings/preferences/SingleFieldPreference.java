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
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;

import com.wittmane.testingedittext.R;

import java.util.ArrayList;
import java.util.List;

import static com.wittmane.testingedittext.settings.fragments.TestFieldBaseSettingsFragment.FIELD_INDEX_BUNDLE_KEY;

/**
 * Preference to link to a test field specific settings screen.
 */
public abstract class SingleFieldPreference extends Preference {
    private static final String TAG = SingleFieldPreference.class.getSimpleName();

    private int mFieldIndex = -1;
    private Bundle mExtras;

    public SingleFieldPreference(Context context) {
        super(context);
    }

    public SingleFieldPreference(Context context, int fieldIndex) {
        this(context);
        setFieldIndex(fieldIndex);
    }

    public SingleFieldPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SingleFieldPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SingleFieldPreference(Context context, AttributeSet attrs, int defStyleAttr,
                                 int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        super.onAttachedToHierarchy(preferenceManager);

        if (mFieldIndex >= 0) {
            updateSummary();
        }
    }

    public void setFieldIndex(int fieldIndex) {
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
        if (mFieldIndex < 0) {
            Log.e(TAG, "No field index for extras");
            return super.getExtras();
        }
        if (mExtras == null) {
            mExtras = new Bundle();
            mExtras.putString(FIELD_INDEX_BUNDLE_KEY, "" + mFieldIndex);
        }
        return mExtras;
    }

    @Override
    public Bundle peekExtras() {
        if (mFieldIndex < 0) {
            Log.e(TAG, "No field index for extras");
            return super.getExtras();
        }
        return mExtras;
    }

    protected abstract void updateSummary();

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
