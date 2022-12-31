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
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.fragments.TestFieldImeOptionsSettingsFragment;

import java.util.ArrayList;
import java.util.List;

public class TestFieldImeOptionsPreference extends SingleFieldPreference {

    public TestFieldImeOptionsPreference(Context context) {
        super(context);
        init();
    }

    public TestFieldImeOptionsPreference(Context context, int fieldIndex) {
        super(context, fieldIndex);
        init();
    }

    public TestFieldImeOptionsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TestFieldImeOptionsPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public TestFieldImeOptionsPreference(Context context, AttributeSet attrs, int defStyleAttr,
                                         int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        setFragment(TestFieldImeOptionsSettingsFragment.class.getName());
    }

    @Override
    protected void updateSummary() {
        setSummary(getImeOptionsDescription(Settings.getTestFieldImeOptions(getFieldIndex()),
                getContext()));
    }

    public static String getImeOptionsDescription(int imeOptions, Context context) {
        int imeOptionsAction = imeOptions & EditorInfo.IME_MASK_ACTION;
        String imeOptionsActionBaseDisplay;
        List<String> imeOptionsActionDetails = new ArrayList<>();
        switch (imeOptionsAction) {
            case EditorInfo.IME_NULL:
                imeOptionsActionBaseDisplay =
                        context.getString(R.string.ime_options_action_unspecified);
                break;
            case EditorInfo.IME_ACTION_NONE:
                imeOptionsActionBaseDisplay =
                        context.getString(R.string.ime_options_action_none);
                break;
            case EditorInfo.IME_ACTION_GO:
                imeOptionsActionBaseDisplay = context.getString(R.string.ime_options_action_go);
                break;
            case EditorInfo.IME_ACTION_SEARCH:
                imeOptionsActionBaseDisplay = context.getString(R.string.ime_options_action_search);
                break;
            case EditorInfo.IME_ACTION_SEND:
                imeOptionsActionBaseDisplay = context.getString(R.string.ime_options_action_send);
                break;
            case EditorInfo.IME_ACTION_NEXT:
                imeOptionsActionBaseDisplay = context.getString(R.string.ime_options_action_next);
                break;
            case EditorInfo.IME_ACTION_DONE:
                imeOptionsActionBaseDisplay = context.getString(R.string.ime_options_action_done);
                break;
            case EditorInfo.IME_ACTION_PREVIOUS:
                imeOptionsActionBaseDisplay =
                        context.getString(R.string.ime_options_action_previous);
                break;
            default:
                // this shouldn't happen
                return "";
        }
        if ((imeOptions & EditorInfo.IME_FLAG_FORCE_ASCII) != 0) {
            imeOptionsActionDetails.add(context.getString(
                    R.string.ime_options_flag_force_ascii));
        }
        if ((imeOptions & EditorInfo.IME_FLAG_NAVIGATE_NEXT) != 0) {
            imeOptionsActionDetails.add(context.getString(
                    R.string.ime_options_flag_navigate_next));
        }
        if ((imeOptions & EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS) != 0) {
            imeOptionsActionDetails.add(context.getString(
                    R.string.ime_options_flag_navigate_previous));
        }
        if ((imeOptions & EditorInfo.IME_FLAG_NO_ACCESSORY_ACTION) != 0) {
            imeOptionsActionDetails.add(context.getString(
                    R.string.ime_options_flag_no_accessory_action));
        }
        if ((imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            imeOptionsActionDetails.add(context.getString(
                    R.string.ime_options_flag_no_enter_action));
        }
        if ((imeOptions & EditorInfo.IME_FLAG_NO_EXTRACT_UI) != 0) {
            imeOptionsActionDetails.add(context.getString(
                    R.string.ime_options_flag_no_extract_ui));
        }
        if ((imeOptions & EditorInfo.IME_FLAG_NO_FULLSCREEN) != 0) {
            imeOptionsActionDetails.add(context.getString(
                    R.string.ime_options_flag_no_fullscreen));
        }
        if ((imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) {
            imeOptionsActionDetails.add(context.getString(
                    R.string.ime_options_flag_no_personalized_learning));
        }

        return getDescription(imeOptionsActionBaseDisplay, imeOptionsActionDetails, context);
    }
}
