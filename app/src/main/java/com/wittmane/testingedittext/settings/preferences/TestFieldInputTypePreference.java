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
import android.text.InputType;
import android.util.AttributeSet;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.fragments.TestFieldInputTypeSettingsFragment;

import java.util.ArrayList;
import java.util.List;

public class TestFieldInputTypePreference extends SingleFieldPreference {

    public TestFieldInputTypePreference(Context context) {
        super(context);
        init();
    }

    public TestFieldInputTypePreference(Context context, int fieldIndex) {
        super(context, fieldIndex);
        init();
    }

    public TestFieldInputTypePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TestFieldInputTypePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public TestFieldInputTypePreference(Context context, AttributeSet attrs, int defStyleAttr,
                                        int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        setFragment(TestFieldInputTypeSettingsFragment.class.getName());
    }

    @Override
    protected void updateSummary() {
        setSummary(getInputTypeDescription(Settings.getTestFieldInputType(getFieldIndex()),
                getContext()));
    }

    public static String getInputTypeDescription(int inputType, Context context) {
        int inputTypeClass = inputType & InputType.TYPE_MASK_CLASS;
        int inputTypeFlags = inputType & InputType.TYPE_MASK_FLAGS;
        int inputTypeVariation = inputType & InputType.TYPE_MASK_VARIATION;

        String inputTypeClassBaseDisplay;
        List<String> inputTypeClassDetails = new ArrayList<>();
        switch (inputTypeClass) {
            case InputType.TYPE_CLASS_DATETIME:
                switch (inputTypeVariation) {
                    case InputType.TYPE_DATETIME_VARIATION_DATE:
                        inputTypeClassBaseDisplay =
                                context.getString(R.string.input_type_datetime_variation_date);
                        break;
                    case InputType.TYPE_DATETIME_VARIATION_TIME:
                        inputTypeClassBaseDisplay =
                                context.getString(R.string.input_type_datetime_variation_time);
                        break;
                    case InputType.TYPE_DATETIME_VARIATION_NORMAL:
                        inputTypeClassBaseDisplay =
                                context.getString(R.string.input_type_datetime_variation_normal);
                        break;
                    default:
                        // this shouldn't happen
                        inputTypeClassBaseDisplay =
                                context.getString(R.string.input_type_class_datetime);
                        break;
                }
                break;
            case InputType.TYPE_CLASS_NUMBER:
                inputTypeClassBaseDisplay = context.getString(R.string.input_type_class_number);
                if (inputTypeVariation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) {
                    inputTypeClassDetails.add(context.getString(
                            R.string.input_type_number_variation_password));
                }
                if ((inputTypeFlags & InputType.TYPE_NUMBER_FLAG_SIGNED) != 0) {
                    inputTypeClassDetails.add(context.getString(
                            R.string.input_type_number_flag_signed));
                }
                if ((inputTypeFlags & InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0) {
                    inputTypeClassDetails.add(context.getString(
                            R.string.input_type_number_flag_decimal));
                }
                break;
            case InputType.TYPE_CLASS_PHONE:
                inputTypeClassBaseDisplay = context.getString(R.string.input_type_class_phone);
                break;
            case InputType.TYPE_CLASS_TEXT:
                inputTypeClassBaseDisplay = context.getString(R.string.input_type_class_text);
                switch (inputTypeVariation) {
                    case InputType.TYPE_TEXT_VARIATION_URI:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_uri));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_email_address));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_email_subject));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_short_message));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_long_message));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_PERSON_NAME:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_person_name));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_postal_address));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_PASSWORD:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_password));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_visible_password));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_web_edit_text));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_FILTER:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_filter));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_PHONETIC:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_phonetic));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_web_email_address));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_web_password));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_NORMAL:
                    default:
                        break;
                }
                if ((inputTypeFlags & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) {
                    inputTypeClassDetails.add(context.getString(
                            R.string.input_type_text_flag_multi_line));
                } else if ((inputTypeFlags & InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE) != 0) {
                    inputTypeClassDetails.add(context.getString(
                            R.string.input_type_text_flag_ime_multi_line));
                }
                if ((inputTypeFlags & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) {
                    inputTypeClassDetails.add(context.getString(
                            R.string.input_type_text_flag_cap_characters));
                } else if ((inputTypeFlags & InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0) {
                    inputTypeClassDetails.add(context.getString(
                            R.string.input_type_text_flag_cap_words));
                } else if ((inputTypeFlags & InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0) {
                    inputTypeClassDetails.add(context.getString(
                            R.string.input_type_text_flag_cap_sentences));
                }
                if ((inputTypeFlags & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    inputTypeClassDetails.add(context.getString(
                            R.string.input_type_text_flag_auto_complete));
                }
                if ((inputTypeFlags & InputType.TYPE_TEXT_FLAG_AUTO_CORRECT) != 0) {
                    inputTypeClassDetails.add(context.getString(
                            R.string.input_type_text_flag_auto_correct));
                }
                if ((inputTypeFlags & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) {
                    inputTypeClassDetails.add(context.getString(
                            R.string.input_type_text_flag_no_suggestions));
                }
                break;
            default:
                // this shouldn't happen
                return "";
        }

        return getDescription(inputTypeClassBaseDisplay, inputTypeClassDetails, context);
    }
}
