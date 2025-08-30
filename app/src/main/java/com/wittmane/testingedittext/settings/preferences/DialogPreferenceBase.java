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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;

import com.wittmane.testingedittext.settings.SharedPreferenceManager;
import com.wittmane.testingedittext.util.PreferenceSummaryManager;

public abstract class DialogPreferenceBase extends DialogPreference {
    private static final String TAG = DialogPreferenceBase.class.getSimpleName();

    private final PreferenceSummaryManager mSummaryManager;

    /** SharedPreference wrapper */
    private SharedPreferenceManager mSharedPrefManager;
    /** The wrapped SharedPreference to track if the wrapper needs to be replaced */
    private SharedPreferences mSharedPrefs;

    public DialogPreferenceBase(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mSummaryManager = new PreferenceSummaryManager(this, this::onClick, super::setSummary);
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        Dialog dialog = getDialog();
        if (dialog != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
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

    /**
     * Set the enabled state of the accept button for the dialog.
     * @param enabled True if the button should be enabled, false otherwise.
     */
    protected void setAcceptButtonEnabled(boolean enabled) {
        AlertDialog dialog = (AlertDialog)getDialog();
        if (dialog == null) {
            return;
        }
        Button acceptButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (acceptButton == null) {
            return;
        }
        acceptButton.setEnabled(enabled);
    }

    protected abstract void updateValueSummary();

    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
        updateValueSummary();
        return view;
    }

    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        super.onAttachedToHierarchy(preferenceManager);
        updateValueSummary();
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        mSummaryManager.onBindView(view);
    }

    protected SharedPreferenceManager getPrefs() {
        SharedPreferences sharedPrefs = getSharedPreferences();
        if (sharedPrefs != null && (mSharedPrefs == null || sharedPrefs != mSharedPrefs)) {
            mSharedPrefManager = new SharedPreferenceManager(sharedPrefs);
            mSharedPrefs = sharedPrefs;
        }
        return mSharedPrefManager;
    }
}
