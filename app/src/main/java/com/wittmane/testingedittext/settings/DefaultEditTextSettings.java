/*
 * Copyright (C) 2024 Eli Wittman
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

import static com.wittmane.testingedittext.settings.PreferenceKeys.*;

import com.wittmane.testingedittext.aosp.android.widget.EditText;
import com.wittmane.testingedittext.settings.Settings.TestFieldCustomEditorSettings;

/**
 * Object to pull the defaults for the custom settings based on the existing configuration in
 * EditText
 */
public class DefaultEditTextSettings extends TestFieldCustomEditorSettings {

    // note that the ID doesn't really matter since this doesn't actually connect to a preference
    // value, but just uses the default values. using int max value to ensure avoiding confusion
    // with a real preference value.
    private final TestField mTestField = new TestField(Integer.MAX_VALUE);

    private final PreferenceReader mPreferenceReader;

    private final EditText mEditText;
    private int mLastInputType;

    public DefaultEditTextSettings(EditText editText) {
        mEditText = editText;
        mLastInputType = mEditText.getInputType();
        mPreferenceReader = new DefaultPreferenceReader();
        mPreferenceReader.loadTestFieldDefaultableSettings(mTestField);
        mPreferenceReader.loadTestFieldSpecificSettings(mTestField);
    }

    @Override
    /* package */ TestField getField() {
        // since calling this is required for getting any settings value, we can use this to check
        // if any changes were made to the EditText that could impact any of these settings (since
        // we don't have easy triggers to listen for when they change) and then reload the settings.
        // this may not be the most efficient to reload everything, but it avoid requiring this to
        // track specifics of dependencies, doing it like this only requires this to know what
        // EditText properties have any sort of impact, which should help reduce duplicate code and
        // risk of things getting out of sync.
        // currently only the input type cause any other settings to have different defaults
        // (technically allowed values).
        int inputType = mEditText.getInputType();
        if (mLastInputType != inputType) {
            mLastInputType = inputType;

            mPreferenceReader.loadTestFieldSpecificSetting(
                    PreferenceKey.createFieldKey(PREF_INPUT_TYPE_CLASS_PREFIX, mTestField.mId),
                    mTestField);
        }
        return mTestField;
    }

    private class DefaultPreferenceReader extends PreferenceReader {
        public DefaultPreferenceReader() {
            super(null);
        }

        @Override
        protected int readTestFieldInputType(int fieldId) {
            return mEditText.getInputType();
        }
    }
}
