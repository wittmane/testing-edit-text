/*
 * Copyright (C) 2022-2024 Eli Wittman
 * Copyright (C) 2013 The Android Open Source Project
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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.Nullable;

import com.wittmane.testingedittext.settings.SharedPreferenceManager.Editor;
import com.wittmane.testingedittext.settings.preferences.LocaleEntryListPreference;
import com.wittmane.testingedittext.settings.preferences.TextListPreference;
import com.wittmane.testingedittext.settings.preferences.CodepointRangeDialogPreference;
import com.wittmane.testingedittext.settings.preferences.TextTranslateListPreference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Settings implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = Settings.class.getSimpleName();

    public static final int BASE_GROUP_INDEX = -1;
    public static final int BASE_FIELD_INDEX = -1;
    public static final int BASE_FIELD_ID = -1;

    public static final String BASE_SUFFIX = "_base";
    public static final String FIELD_INFIX = "_field_";

    public static final String PREF_OVERRIDE_TEXT_INPUT_MODIFICATION_PREFIX =
            "pref_key_override_text_input_modification";
    public static final String PREF_MODIFY_COMMITTED_TEXT_PREFIX =
            "pref_key_modify_committed_text";
    public static final String PREF_MODIFY_COMPOSED_TEXT_PREFIX =
            "pref_key_modify_composed_text";
    public static final String PREF_MODIFY_COMPOSED_CHANGES_ONLY_PREFIX =
            "pref_key_modify_composed_changes_only";
    public static final String PREF_CONSIDER_COMPOSED_CHANGES_FROM_END_PREFIX =
            "pref_key_consider_composed_changes_from_end";
    public static final String PREF_RESTRICT_TO_INCLUDE_PREFIX =
            "pref_key_restrict_to_include";
    public static final String PREF_RESTRICT_SPECIFIC_PREFIX =
            "pref_key_restrict_specific";
    public static final String PREF_RESTRICT_RANGE_PREFIX =
            "pref_key_restrict_range";
    public static final String PREF_TRANSLATE_SPECIFIC_PREFIX =
            "pref_key_translate_specific";
    public static final String PREF_TRANSLATE_FULL_MATCH_ONLY_PREFIX =
            "pref_key_translate_full_match_only";
    public static final String PREF_SHIFT_CODEPOINT_PREFIX =
            "pref_key_shift_codepoint";

    public static final String PREF_OVERRIDE_TEXT_RETURN_PREFIX =
            "pref_key_override_text_return";
    public static final String PREF_SKIP_EXTRACTING_TEXT_PREFIX =
            "pref_key_skip_extracting_text";
    public static final String PREF_IGNORE_EXTRACTED_TEXT_MONITOR_PREFIX =
            "pref_key_ignore_extracted_text_monitor";
    public static final String PREF_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT_PREFIX =
            "pref_key_update_selection_before_extracted_text";
    public static final String PREF_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES_PREFIX =
            "pref_key_update_extracted_text_only_on_net_changes";
    public static final String PREF_EXTRACT_FULL_TEXT_PREFIX =
            "pref_key_extract_full_text";
    public static final String PREF_LIMIT_EXTRACT_MONITOR_TEXT_PREFIX =
            "pref_key_limit_extract_monitor_text";
    public static final String PREF_LIMIT_RETURNED_TEXT_PREFIX =
            "pref_key_limit_returned_text";

    public static final String PREF_OVERRIDE_TEXT_COMPOSITION_PREFIX =
            "pref_key_override_text_composition";
    public static final String PREF_DELETE_THROUGH_COMPOSING_TEXT_PREFIX =
            "pref_key_delete_through_composing_text";
    public static final String PREF_KEEP_EMPTY_COMPOSING_POSITION_PREFIX =
            "pref_key_keep_empty_composing_position";

    public static final String PREF_OVERRIDE_TARGET_VERSION_SIMULATION_PREFIX =
            "pref_key_override_target_version_simulation";
    public static final String PREF_SKIP_TAKESNAPSHOT_PREFIX =
            "pref_key_skip_takesnapshot";
    public static final String PREF_SKIP_GETSURROUNDINGTEXT_PREFIX =
            "pref_key_skip_getsurroundingtext";
    public static final String PREF_SKIP_PERFORMSPELLCHECK_PREFIX =
            "pref_key_skip_performspellcheck";
    public static final String PREF_SKIP_SETIMECONSUMESINPUT_PREFIX =
            "pref_key_skip_setimeconsumesinput";
    public static final String PREF_SKIP_COMMITCONTENT_PREFIX =
            "pref_key_skip_commitcontent";
    public static final String PREF_SKIP_CLOSECONNECTION_PREFIX =
            "pref_key_skip_closeconnection";
    public static final String PREF_SKIP_DELETESURROUNDINGTEXTINCODEPOINTS_PREFIX =
            "pref_key_skip_deletesurroundingtextincodepoints";
    public static final String PREF_SKIP_REQUESTCURSORUPDATES_PREFIX =
            "pref_key_skip_requestcursorupdates";
    public static final String PREF_SKIP_COMMITCORRECTION_PREFIX =
            "pref_key_skip_commitcorrection";
    public static final String PREF_SKIP_GETSELECTEDTEXT_PREFIX =
            "pref_key_skip_getselectedtext";
    public static final String PREF_SKIP_SETCOMPOSINGREGION_PREFIX =
            "pref_key_skip_setcomposingregion";

    public static final String PREF_OVERRIDE_SYSTEM_BEHAVIOR_SIMULATION_PREFIX =
            "pref_key_override_system_behavior_simulation";
    public static final String PREF_UPDATE_DELAY_PREFIX = "pref_key_update_delay";
    public static final String PREF_FINISHCOMPOSINGTEXT_DELAY_PREFIX =
            "pref_key_finishcomposingtext_delay";
    public static final String PREF_GETSURROUNDINGTEXT_DELAY_PREFIX =
            "pref_key_getsurroundingtext_delay";
    public static final String PREF_GETTEXTBEFORECURSOR_DELAY_PREFIX =
            "pref_key_gettextbeforecursor_delay";
    public static final String PREF_GETSELECTEDTEXT_DELAY_PREFIX =
            "pref_key_getselectedtext_delay";
    public static final String PREF_GETTEXTAFTERCURSOR_DELAY_PREFIX =
            "pref_key_gettextaftercursor_delay";
    public static final String PREF_GETCURSORCAPSMODE_DELAY_PREFIX =
            "pref_key_getcursorcapsmode_delay";
    public static final String PREF_GETEXTRACTEDTEXT_DELAY_PREFIX =
            "pref_key_getextractedtext_delay";

    public static final String PREF_TEST_FIELD_IDS =
            "pref_key_test_field_ids";
    public static final String PREF_TEST_GROUP_FIELD_COUNTS =
            "pref_key_test_group_field_count";
    public static final String TEST_GROUP_PREF_PREFIX =
            "pref_key_test_group_";
    public static final String PREF_TEST_GROUP_NAME_PREFIX =
            "pref_key_test_group_name_";

    public static final String PREF_INPUT_TYPE_CLASS_PREFIX =
            "pref_key_input_type_class";
    public static final String PREF_INPUT_TYPE_TEXT_VARIATION_PREFIX =
            "pref_key_input_type_text_variation";
    public static final String PREF_INPUT_TYPE_NUMBER_VARIATION_PREFIX =
            "pref_key_input_type_number_variation";
    public static final String PREF_INPUT_TYPE_DATETIME_VARIATION_PREFIX =
            "pref_key_input_type_datetime_variation";
    public static final String PREF_INPUT_TYPE_TEXT_FLAG_MULTI_LINE_PREFIX =
            "pref_key_input_type_text_flag_multi_line";
    public static final String PREF_INPUT_TYPE_TEXT_FLAG_CAP_PREFIX =
            "pref_key_input_type_text_flag_cap";
    public static final String PREF_INPUT_TYPE_TEXT_FLAG_AUTO_COMPLETE_PREFIX =
            "pref_key_input_type_text_flag_auto_complete";
    public static final String PREF_INPUT_TYPE_TEXT_FLAG_AUTO_CORRECT_PREFIX =
            "pref_key_input_type_text_flag_auto_correct";
    public static final String PREF_INPUT_TYPE_TEXT_FLAG_NO_SUGGESTIONS_PREFIX =
            "pref_key_input_type_text_flag_no_suggestions";
    public static final String PREF_INPUT_TYPE_NUMBER_FLAG_SIGNED_PREFIX =
            "pref_key_input_type_number_flag_signed";
    public static final String PREF_INPUT_TYPE_NUMBER_FLAG_DECIMAL_PREFIX =
            "pref_key_input_type_number_flag_decimal";
    public static final String PREF_NULL_INPUT_TYPE_MULTILINE_PREFIX =
            "pref_key_null_input_type_multiline";
    public static final String PREF_CREATE_INPUT_CONNECTION_PREFIX =
            "pref_key_create_input_connection";
    public static final String PREF_SEND_SELECTION_INFO_PREFIX =
            "pref_key_send_selection_info";
    public static final String PREF_SEND_TEXT_PREFIX =
            "pref_key_send_text";
    public static final String PREF_COMPOSING_TEXT_BEHAVIOR_PREFIX =
            "pref_key_composing_text_behavior";
    public static final String PREF_ALLOW_DELETE_SURROUNDING_TEXT_PREFIX =
            "pref_key_allow_delete_surrounding_text";
    public static final String PREF_ALLOW_SETTING_SELECTION_PREFIX =
            "pref_key_allow_setting_selection";
    public static final String PREF_IME_OPTIONS_ACTION_PREFIX =
            "pref_key_ime_options_action";
    public static final String PREF_IME_OPTIONS_FLAG_FORCE_ASCII_PREFIX =
            "pref_key_ime_options_flag_force_ascii";
    public static final String PREF_IME_OPTIONS_FLAG_NAVIGATE_NEXT_PREFIX =
            "pref_key_ime_options_flag_navigate_next";
    public static final String PREF_IME_OPTIONS_FLAG_NAVIGATE_PREVIOUS_PREFIX =
            "pref_key_ime_options_flag_navigate_previous";
    public static final String PREF_IME_OPTIONS_FLAG_NO_ACCESSORY_ACTION_PREFIX =
            "pref_key_ime_options_flag_no_accessory_action";
    public static final String PREF_IME_OPTIONS_FLAG_NO_ENTER_ACTION_PREFIX =
            "pref_key_ime_options_flag_no_enter_action";
    public static final String PREF_IME_OPTIONS_FLAG_NO_EXTRACT_UI_PREFIX =
            "pref_key_ime_options_flag_no_extract_ui";
    public static final String PREF_IME_OPTIONS_FLAG_NO_FULLSCREEN_PREFIX =
            "pref_key_ime_options_flag_no_fullscreen";
    public static final String PREF_IME_OPTIONS_FLAG_NO_PERSONALIZED_LEARNING_PREFIX =
            "pref_key_ime_options_flag_no_personalized_learning";
    public static final String PREF_IME_ACTION_ID_PREFIX =
            "pref_key_ime_action_id";
    public static final String PREF_IME_ACTION_LABEL_PREFIX =
            "pref_key_ime_action_label";
    public static final String PREF_PRIVATE_IME_OPTIONS_PREFIX =
            "pref_key_private_ime_options";
    public static final String PREF_SELECT_ALL_ON_FOCUS_PREFIX =
            "pref_key_select_all_on_focus";
    public static final String PREF_MAX_LENGTH_PREFIX =
            "pref_key_max_length";
    public static final String PREF_ALLOW_UNDO_PREFIX =
            "pref_key_allow_undo";
    public static final String PREF_TEXT_LOCALES_PREFIX =
            "pref_key_text_locales";
    public static final String PREF_IME_HINT_LOCALES_PREFIX =
            "pref_key_ime_hint_locales";
    public static final String PREF_IME_DEFAULT_TEXT_PREFIX =
            "pref_key_default_text";
    public static final String PREF_IME_HINT_TEXT_PREFIX =
            "pref_key_hint_text";

    //TODO: (EW) would it make sense for this to just be an array?
    private final List<TestGroup> mTestGroups = new ArrayList<>();
    private final Map<Integer, TestField> mTestFields = new HashMap<>();
    private final AppLevelDefaults mTestFieldDefaults = new AppLevelDefaults();

    private SharedPreferenceManager mPrefs;

    private static final Settings sInstance = new Settings();

    private Settings() {
        // Intentional empty constructor for singleton.
    }

    public static Settings getInstance() {
        return sInstance;
    }

    public static void init(final Context context) {
        sInstance.onCreate(context);
    }

    private void onCreate(final Context context) {
        mPrefs = new SharedPreferenceManager(
                PreferenceManager.getDefaultSharedPreferences(context));
        mPrefs.registerOnSharedPreferenceChangeListener(this);
        loadSettings();
    }

    public static void onDestroy() {
        getInstance().mPrefs.unregisterOnSharedPreferenceChangeListener(getInstance());
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key == null) {
            loadSettings();
        } else {
            loadSetting(key);
        }
    }

    private void loadSettings() {
        final String[] prefKeys = new String[] {
        };
        for (String prefKey : prefKeys) {
            loadSetting(prefKey);
        }
        loadTestFieldSettings(BASE_FIELD_ID);
        TestGroup[] testGroups = readTestFieldGroups(mPrefs);
        mTestGroups.clear();
        Collections.addAll(mTestGroups, testGroups);
        for (TestGroup group : testGroups) {
            for (int id : group.mFieldIds) {
                mTestFields.put(id, new TestField(id));
                loadTestFieldSettings(id);
            }
        }
    }

    private void loadTestFieldSettings(int fieldId) {
        // intentionally skipping some preferences since they are read in groups, so listing them
        // all would just read all of them multiple times. leaving them commented out here for
        // visibility.
        final String[] testFieldPrefKeyPrefixes = new String[]{
                PREF_INPUT_TYPE_CLASS_PREFIX,
                //PREF_INPUT_TYPE_TEXT_VARIATION_PREFIX,
                //PREF_INPUT_TYPE_NUMBER_VARIATION_PREFIX,
                //PREF_INPUT_TYPE_DATETIME_VARIATION_PREFIX,
                //PREF_INPUT_TYPE_TEXT_FLAG_MULTI_LINE_PREFIX,
                //PREF_INPUT_TYPE_TEXT_FLAG_CAP_PREFIX,
                //PREF_INPUT_TYPE_TEXT_FLAG_AUTO_COMPLETE_PREFIX,
                //PREF_INPUT_TYPE_TEXT_FLAG_AUTO_CORRECT_PREFIX,
                //PREF_INPUT_TYPE_TEXT_FLAG_NO_SUGGESTIONS_PREFIX,
                //PREF_INPUT_TYPE_NUMBER_FLAG_SIGNED_PREFIX,
                //PREF_INPUT_TYPE_NUMBER_FLAG_DECIMAL_PREFIX,
                //PREF_NULL_INPUT_TYPE_MULTILINE_PREFIX,
                //PREF_CREATE_INPUT_CONNECTION_PREFIX,
                //PREF_SEND_SELECTION_INFO_PREFIX,
                //PREF_SEND_TEXT_PREFIX,
                //PREF_COMPOSING_TEXT_BEHAVIOR_PREFIX,
                //PREF_ALLOW_DELETE_SURROUNDING_TEXT_PREFIX,
                //PREF_ALLOW_SETTING_SELECTION_PREFIX,
                PREF_IME_OPTIONS_ACTION_PREFIX,
                //PREF_IME_OPTIONS_FLAG_FORCE_ASCII_PREFIX,
                //PREF_IME_OPTIONS_FLAG_NAVIGATE_NEXT_PREFIX,
                //PREF_IME_OPTIONS_FLAG_NAVIGATE_PREVIOUS_PREFIX,
                //PREF_IME_OPTIONS_FLAG_NO_ACCESSORY_ACTION_PREFIX,
                //PREF_IME_OPTIONS_FLAG_NO_ENTER_ACTION_PREFIX,
                //PREF_IME_OPTIONS_FLAG_NO_EXTRACT_UI_PREFIX,
                //PREF_IME_OPTIONS_FLAG_NO_FULLSCREEN_PREFIX,
                //PREF_IME_OPTIONS_FLAG_NO_PERSONALIZED_LEARNING_PREFIX,
                PREF_IME_ACTION_ID_PREFIX,
                PREF_IME_ACTION_LABEL_PREFIX,
                PREF_PRIVATE_IME_OPTIONS_PREFIX,
                PREF_SELECT_ALL_ON_FOCUS_PREFIX,
                PREF_MAX_LENGTH_PREFIX,
                PREF_ALLOW_UNDO_PREFIX,
                PREF_TEXT_LOCALES_PREFIX,
                PREF_IME_HINT_LOCALES_PREFIX,
                PREF_IME_DEFAULT_TEXT_PREFIX,
                PREF_IME_HINT_TEXT_PREFIX,

                PREF_OVERRIDE_TEXT_INPUT_MODIFICATION_PREFIX,
                PREF_OVERRIDE_TEXT_RETURN_PREFIX,
                PREF_OVERRIDE_TEXT_COMPOSITION_PREFIX,
                PREF_OVERRIDE_TARGET_VERSION_SIMULATION_PREFIX,
                PREF_OVERRIDE_SYSTEM_BEHAVIOR_SIMULATION_PREFIX,
        };
        if (fieldId != BASE_FIELD_ID) {
            for (String prefKeyPrefix : testFieldPrefKeyPrefixes) {
                loadTestFieldSetting(prefKeyPrefix, fieldId);
            }
        }

        final String[] testFieldWithDefaultPrefKeyPrefixes = new String[]{
                PREF_MODIFY_COMMITTED_TEXT_PREFIX,
                PREF_MODIFY_COMPOSED_TEXT_PREFIX,
                PREF_MODIFY_COMPOSED_CHANGES_ONLY_PREFIX,
                PREF_CONSIDER_COMPOSED_CHANGES_FROM_END_PREFIX,
                PREF_RESTRICT_TO_INCLUDE_PREFIX,
                PREF_RESTRICT_SPECIFIC_PREFIX,
                PREF_RESTRICT_RANGE_PREFIX,
                PREF_TRANSLATE_SPECIFIC_PREFIX,
                PREF_TRANSLATE_FULL_MATCH_ONLY_PREFIX,
                PREF_SHIFT_CODEPOINT_PREFIX,

                PREF_SKIP_EXTRACTING_TEXT_PREFIX,
                PREF_IGNORE_EXTRACTED_TEXT_MONITOR_PREFIX,
                PREF_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT_PREFIX,
                PREF_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES_PREFIX,
                PREF_EXTRACT_FULL_TEXT_PREFIX,
                PREF_LIMIT_EXTRACT_MONITOR_TEXT_PREFIX,
                PREF_LIMIT_RETURNED_TEXT_PREFIX,

                PREF_DELETE_THROUGH_COMPOSING_TEXT_PREFIX,
                PREF_KEEP_EMPTY_COMPOSING_POSITION_PREFIX,

                PREF_SKIP_TAKESNAPSHOT_PREFIX,
                PREF_SKIP_GETSURROUNDINGTEXT_PREFIX,
                PREF_SKIP_PERFORMSPELLCHECK_PREFIX,
                PREF_SKIP_SETIMECONSUMESINPUT_PREFIX,
                PREF_SKIP_COMMITCONTENT_PREFIX,
                PREF_SKIP_CLOSECONNECTION_PREFIX,
                PREF_SKIP_DELETESURROUNDINGTEXTINCODEPOINTS_PREFIX,
                PREF_SKIP_REQUESTCURSORUPDATES_PREFIX,
                PREF_SKIP_COMMITCORRECTION_PREFIX,
                PREF_SKIP_GETSELECTEDTEXT_PREFIX,
                PREF_SKIP_SETCOMPOSINGREGION_PREFIX,

                PREF_UPDATE_DELAY_PREFIX,
                PREF_FINISHCOMPOSINGTEXT_DELAY_PREFIX,
                PREF_GETSURROUNDINGTEXT_DELAY_PREFIX,
                PREF_GETTEXTBEFORECURSOR_DELAY_PREFIX,
                PREF_GETSELECTEDTEXT_DELAY_PREFIX,
                PREF_GETTEXTAFTERCURSOR_DELAY_PREFIX,
                PREF_GETCURSORCAPSMODE_DELAY_PREFIX,
                PREF_GETEXTRACTEDTEXT_DELAY_PREFIX
        };
        for (String prefKeyPrefix : testFieldWithDefaultPrefKeyPrefixes) {
            loadTestFieldOrDefaultSetting(prefKeyPrefix, fieldId);
        }
    }

    private void loadSetting(String prefKey) {
        switch (prefKey) {
            case PREF_TEST_FIELD_IDS:
            case PREF_TEST_GROUP_FIELD_COUNTS:
                // internal state is updated while these are modified since they aren't managed by a
                // simple Preference, so we don't need to do anything when these change
                break;

            default:
                // try loading as a specific field's setting
                loadPrefixedSetting(prefKey);
                break;
        }
    }

    private void loadPrefixedSetting(String prefKey) {
        if (prefKey == null) {
            return;
        }
        if (prefKey.startsWith(TEST_GROUP_PREF_PREFIX)) {
            int prefixEnd = prefKey.lastIndexOf("_");
            if (prefixEnd < 0 || prefKey.length() - prefixEnd - 1 <= 0) {
                return;
            }
            int index;
            try {
                index = Integer.parseInt(prefKey.substring(prefixEnd + 1));
            } catch (NumberFormatException ignored) {
                return;
            }
            if (index >= mTestGroups.size() && !mPrefs.contains(prefKey)) {
                // this is most likely from deleting an old preference when the group is deleted, so
                // we don't need to bother loading this value
                return;
            }
            loadTestGroupSetting(prefKey.substring(0, prefixEnd + 1), index);
        } else if (prefKey.endsWith(BASE_SUFFIX)) {
            loadTestFieldOrDefaultSetting(
                    prefKey.substring(0, prefKey.length() - BASE_SUFFIX.length()),
                    BASE_FIELD_ID);
        } else if (prefKey.contains(FIELD_INFIX)) {
            int prefixLength = prefKey.lastIndexOf(FIELD_INFIX);
            int id;
            try {
                id = Integer.parseInt(prefKey.substring(prefixLength + FIELD_INFIX.length()));
            } catch (NumberFormatException ignored) {
                return;
            }
            if (!mTestFields.containsKey(id) && !mPrefs.contains(prefKey)) {
                // this is most likely from deleting an old preference when the field is deleted, so
                // we don't need to bother loading this value
                return;
            }
            String prefKeyPrefix = prefKey.substring(0, prefixLength);
            loadTestFieldSetting(prefKeyPrefix, id);
            loadTestFieldOrDefaultSetting(prefKeyPrefix, id);
        }
    }

    private void loadTestGroupSetting(String prefKeyPrefix, int groupIndex) {
        switch (prefKeyPrefix) {
            case PREF_TEST_GROUP_NAME_PREFIX:
                mTestGroups.get(groupIndex).mName = readGroupName(mPrefs, groupIndex);
                break;
        }
    }

    private void loadTestFieldSetting(String prefKeyPrefix, int fieldId) {
        TestField testField = getField(fieldId);
        if (testField == null) {
            return;
        }
        switch (prefKeyPrefix) {
            case PREF_INPUT_TYPE_CLASS_PREFIX:
            case PREF_INPUT_TYPE_TEXT_VARIATION_PREFIX:
            case PREF_INPUT_TYPE_NUMBER_VARIATION_PREFIX:
            case PREF_INPUT_TYPE_DATETIME_VARIATION_PREFIX:
            case PREF_INPUT_TYPE_TEXT_FLAG_MULTI_LINE_PREFIX:
            case PREF_INPUT_TYPE_TEXT_FLAG_CAP_PREFIX:
            case PREF_INPUT_TYPE_TEXT_FLAG_AUTO_COMPLETE_PREFIX:
            case PREF_INPUT_TYPE_TEXT_FLAG_AUTO_CORRECT_PREFIX:
            case PREF_INPUT_TYPE_TEXT_FLAG_NO_SUGGESTIONS_PREFIX:
            case PREF_INPUT_TYPE_NUMBER_FLAG_SIGNED_PREFIX:
            case PREF_INPUT_TYPE_NUMBER_FLAG_DECIMAL_PREFIX:
            case PREF_NULL_INPUT_TYPE_MULTILINE_PREFIX:
            case PREF_CREATE_INPUT_CONNECTION_PREFIX:
            case PREF_SEND_SELECTION_INFO_PREFIX:
            case PREF_SEND_TEXT_PREFIX:
            case PREF_COMPOSING_TEXT_BEHAVIOR_PREFIX:
            case PREF_ALLOW_DELETE_SURROUNDING_TEXT_PREFIX:
            case PREF_ALLOW_SETTING_SELECTION_PREFIX:
                testField.mInputType = readTestFieldInputType(mPrefs, fieldId);
                testField.mNullInputTypeMultiline =
                        readTestFieldNullInputTypeMultiline(mPrefs, fieldId);
                testField.mCreateInputConnection =
                        readTestFieldCreateInputConnection(mPrefs, fieldId, testField.mInputType);
                testField.mSendSelectionInfo =
                        readTestFieldSendSelectionInfo(mPrefs, fieldId, testField.mInputType);
                testField.mSendText =
                        readTestFieldSendText(mPrefs, fieldId, testField.mInputType);
                testField.mComposingTextBehavior =
                        readTestFieldComposingTextBehavior(mPrefs, fieldId, testField.mInputType,
                                testField.mCreateInputConnection);
                testField.mAllowDeleteSurroundingText =
                        readTestFieldAllowDeleteSurroundingText(mPrefs, fieldId,
                                testField.mInputType, testField.mCreateInputConnection);
                testField.mAllowSettingSelection =
                        readTestFieldAllowSettingSelection(mPrefs, fieldId, testField.mInputType,
                                testField.mCreateInputConnection);
                break;
            case PREF_IME_OPTIONS_ACTION_PREFIX:
            case PREF_IME_OPTIONS_FLAG_FORCE_ASCII_PREFIX:
            case PREF_IME_OPTIONS_FLAG_NAVIGATE_NEXT_PREFIX:
            case PREF_IME_OPTIONS_FLAG_NAVIGATE_PREVIOUS_PREFIX:
            case PREF_IME_OPTIONS_FLAG_NO_ACCESSORY_ACTION_PREFIX:
            case PREF_IME_OPTIONS_FLAG_NO_ENTER_ACTION_PREFIX:
            case PREF_IME_OPTIONS_FLAG_NO_EXTRACT_UI_PREFIX:
            case PREF_IME_OPTIONS_FLAG_NO_FULLSCREEN_PREFIX:
            case PREF_IME_OPTIONS_FLAG_NO_PERSONALIZED_LEARNING_PREFIX:
                testField.mImeOptions = readTestFieldImeOptions(mPrefs, fieldId);
                break;
            case PREF_IME_ACTION_ID_PREFIX:
                testField.mImeActionId = readTestFieldImeActionId(mPrefs, fieldId);
                break;
            case PREF_IME_ACTION_LABEL_PREFIX:
                testField.mImeActionLabel = readTestFieldImeActionLabel(mPrefs, fieldId);
                break;
            case PREF_PRIVATE_IME_OPTIONS_PREFIX:
                testField.mPrivateImeOptions = readTestFieldPrivateImeOptions(mPrefs, fieldId);
                break;
            case PREF_SELECT_ALL_ON_FOCUS_PREFIX:
                testField.mSelectAllOnFocus = readTestFieldSelectAllOnFocus(mPrefs, fieldId);
                break;
            case PREF_MAX_LENGTH_PREFIX:
                testField.mMaxLength = readTestFieldMaxLength(mPrefs, fieldId);
                break;
            case PREF_ALLOW_UNDO_PREFIX:
                testField.mAllowUndo = readTestFieldAllowUndo(mPrefs, fieldId);
                break;
            case PREF_TEXT_LOCALES_PREFIX:
                testField.mTextLocales = readTestFieldTextLocales(mPrefs, fieldId);
                break;
            case PREF_IME_HINT_LOCALES_PREFIX:
                testField.mImeHintLocales = readTestFieldImeHintLocales(mPrefs, fieldId);
                break;
            case PREF_IME_DEFAULT_TEXT_PREFIX:
                testField.mDefaultText = readTestFieldDefaultText(mPrefs, fieldId);
                break;
            case PREF_IME_HINT_TEXT_PREFIX:
                testField.mHintText = readTestFieldHintText(mPrefs, fieldId);
                break;

            case PREF_OVERRIDE_TEXT_INPUT_MODIFICATION_PREFIX:
                testField.mOverrideTextInputModification =
                        readOverrideTextInputModification(mPrefs, fieldId);
                break;
            case PREF_OVERRIDE_TEXT_RETURN_PREFIX:
                testField.mOverrideTextReturn = readOverrideTextReturn(mPrefs, fieldId);
                break;
            case PREF_OVERRIDE_TEXT_COMPOSITION_PREFIX:
                testField.mOverrideTextComposition = readOverrideTextComposition(mPrefs, fieldId);
                break;
            case PREF_OVERRIDE_TARGET_VERSION_SIMULATION_PREFIX:
                testField.mOverrideTargetVersion = readOverrideTargetVersion(mPrefs, fieldId);
                break;
            case PREF_OVERRIDE_SYSTEM_BEHAVIOR_SIMULATION_PREFIX:
                testField.mOverrideSystemBehavior = readOverrideSystemBehavior(mPrefs, fieldId);
                break;
        }
    }

    private void loadTestFieldOrDefaultSetting(String prefKeyPrefix, int fieldId) {
        AppLevelDefaults testFieldOrDefault;
        if (fieldId == BASE_FIELD_ID) {
            testFieldOrDefault = mTestFieldDefaults;
        } else {
            testFieldOrDefault = getField(fieldId);
            if (testFieldOrDefault == null) {
                return;
            }
        }
        switch (prefKeyPrefix) {
            case PREF_MODIFY_COMMITTED_TEXT_PREFIX:
                testFieldOrDefault.mModifyCommittedText = readModifyCommittedText(mPrefs, fieldId);
                break;
            case PREF_MODIFY_COMPOSED_TEXT_PREFIX:
                testFieldOrDefault.mModifyComposedText = readModifyComposedText(mPrefs, fieldId);
                break;
            case PREF_MODIFY_COMPOSED_CHANGES_ONLY_PREFIX:
                testFieldOrDefault.mModifyComposedChangesOnly =
                        readModifyComposedChangesOnly(mPrefs, fieldId);
                break;
            case PREF_CONSIDER_COMPOSED_CHANGES_FROM_END_PREFIX:
                testFieldOrDefault.mConsiderComposedChangesFromEnd =
                        readConsiderComposedChangesFromEnd(mPrefs, fieldId);
                break;
            case PREF_RESTRICT_TO_INCLUDE_PREFIX:
                testFieldOrDefault.mRestrictToInclude = readRestrictToInclude(mPrefs, fieldId);
                break;
            case PREF_RESTRICT_SPECIFIC_PREFIX:
                testFieldOrDefault.mRestrictSpecific = readRestrictSpecific(mPrefs, fieldId);
                break;
            case PREF_RESTRICT_RANGE_PREFIX:
                testFieldOrDefault.mRestrictRange = readRestrictRange(mPrefs, fieldId);
                break;
            case PREF_TRANSLATE_SPECIFIC_PREFIX:
                testFieldOrDefault.mTranslateSpecific = readTranslateSpecific(mPrefs, fieldId);
                break;
            case PREF_TRANSLATE_FULL_MATCH_ONLY_PREFIX:
                testFieldOrDefault.mTranslateFullMatchOnly =
                        readTranslateFullMatchOnly(mPrefs, fieldId);
                break;
            case PREF_SHIFT_CODEPOINT_PREFIX:
                testFieldOrDefault.mShiftCodepoint = readShiftCodepoint(mPrefs, fieldId);
                break;

            case PREF_SKIP_EXTRACTING_TEXT_PREFIX:
                testFieldOrDefault.mSkipExtractingText = readSkipExtractingText(mPrefs, fieldId);
                break;
            case PREF_IGNORE_EXTRACTED_TEXT_MONITOR_PREFIX:
                testFieldOrDefault.mIgnoreExtractedTextMonitor =
                        readIgnoreExtractedTextMonitor(mPrefs, fieldId);
                break;
            case PREF_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT_PREFIX:
                testFieldOrDefault.mUpdateSelectionBeforeExtractedText =
                        readUpdateSelectionBeforeExtractedText(mPrefs, fieldId);
                break;
            case PREF_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES_PREFIX:
                testFieldOrDefault.mUpdateExtractedTextOnlyOnNetChanges =
                        readUpdateExtractedTextOnlyOnNetChanges(mPrefs, fieldId);
                break;
            case PREF_EXTRACT_FULL_TEXT_PREFIX:
                testFieldOrDefault.mExtractFullText = readExtractFullText(mPrefs, fieldId);
                break;
            case PREF_LIMIT_EXTRACT_MONITOR_TEXT_PREFIX:
                testFieldOrDefault.mExtractMonitorTextLimit =
                        readExtractMonitorTextLimit(mPrefs, fieldId);
                break;
            case PREF_LIMIT_RETURNED_TEXT_PREFIX:
                testFieldOrDefault.mReturnedTextLimit = readReturnedTextLimit(mPrefs, fieldId);
                break;

            case PREF_DELETE_THROUGH_COMPOSING_TEXT_PREFIX:
                testFieldOrDefault.mDeleteThroughComposingText =
                        readDeleteThroughComposingText(mPrefs, fieldId);
                break;
            case PREF_KEEP_EMPTY_COMPOSING_POSITION_PREFIX:
                testFieldOrDefault.mKeepEmptyComposingPosition =
                        readKeepEmptyComposingPosition(mPrefs, fieldId);
                break;

            case PREF_SKIP_TAKESNAPSHOT_PREFIX:
                testFieldOrDefault.mSkipTakeSnapshot = readSkipTakeSnapshot(mPrefs, fieldId);
                break;
            case PREF_SKIP_GETSURROUNDINGTEXT_PREFIX:
                testFieldOrDefault.mSkipGetSurroundingText =
                        readSkipGetSurroundingText(mPrefs, fieldId);
                break;
            case PREF_SKIP_PERFORMSPELLCHECK_PREFIX:
                testFieldOrDefault.mSkipPerformSpellCheck =
                        readSkipPerformSpellCheck(mPrefs, fieldId);
                break;
            case PREF_SKIP_SETIMECONSUMESINPUT_PREFIX:
                testFieldOrDefault.mSkipSetImeConsumesInput =
                        readSkipSetImeConsumesInput(mPrefs, fieldId);
                break;
            case PREF_SKIP_COMMITCONTENT_PREFIX:
                testFieldOrDefault.mSkipCommitContent = readSkipCommitContent(mPrefs, fieldId);
                break;
            case PREF_SKIP_CLOSECONNECTION_PREFIX:
                testFieldOrDefault.mSkipCloseConnection = readSkipCloseConnection(mPrefs, fieldId);
                break;
            case PREF_SKIP_DELETESURROUNDINGTEXTINCODEPOINTS_PREFIX:
                testFieldOrDefault.mSkipDeleteSurroundingTextInCodePoints =
                        readSkipDeleteSurroundingTextInCodePoints(mPrefs, fieldId);
                break;
            case PREF_SKIP_REQUESTCURSORUPDATES_PREFIX:
                testFieldOrDefault.mSkipRequestCursorUpdates =
                        readSkipRequestCursorUpdates(mPrefs, fieldId);
                break;
            case PREF_SKIP_COMMITCORRECTION_PREFIX:
                testFieldOrDefault.mSkipCommitCorrection =
                        readSkipCommitCorrection(mPrefs, fieldId);
                break;
            case PREF_SKIP_GETSELECTEDTEXT_PREFIX:
                testFieldOrDefault.mSkipGetSelectedText = readSkipGetSelectedText(mPrefs, fieldId);
                break;
            case PREF_SKIP_SETCOMPOSINGREGION_PREFIX:
                testFieldOrDefault.mSkipSetComposingRegion =
                        readSkipSetComposingRegion(mPrefs, fieldId);
                break;

            case PREF_UPDATE_DELAY_PREFIX:
                testFieldOrDefault.mUpdateDelay = readUpdateDelay(mPrefs, fieldId);
                break;
            case PREF_FINISHCOMPOSINGTEXT_DELAY_PREFIX:
                testFieldOrDefault.mFinishComposingTextDelay =
                        readFinishComposingTextDelay(mPrefs, fieldId);
                break;
            case PREF_GETSURROUNDINGTEXT_DELAY_PREFIX:
                testFieldOrDefault.mGetSurroundingTextDelay =
                        readGetSurroundingTextDelay(mPrefs, fieldId);
                break;
            case PREF_GETTEXTBEFORECURSOR_DELAY_PREFIX:
                testFieldOrDefault.mGetTextBeforeCursorDelay =
                        readGetTextBeforeCursorDelay(mPrefs, fieldId);
                break;
            case PREF_GETSELECTEDTEXT_DELAY_PREFIX:
                testFieldOrDefault.mGetSelectedTextDelay =
                        readGetSelectedTextDelay(mPrefs, fieldId);
                break;
            case PREF_GETTEXTAFTERCURSOR_DELAY_PREFIX:
                testFieldOrDefault.mGetTextAfterCursorDelay =
                        readGetTextAfterCursorDelay(mPrefs, fieldId);
                break;
            case PREF_GETCURSORCAPSMODE_DELAY_PREFIX:
                testFieldOrDefault.mGetCursorCapsModeDelay =
                        readGetCursorCapsModeDelay(mPrefs, fieldId);
                break;
            case PREF_GETEXTRACTEDTEXT_DELAY_PREFIX:
                testFieldOrDefault.mGetExtractedTextDelay =
                        readGetExtractedTextDelay(mPrefs, fieldId);
                break;
        }
    }

    private static int indexOf(List<TestField> fields, int id) {
        if (fields != null) {
            for (int i = 0; i < fields.size(); i++) {
                if (fields.get(i).mId == id) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String getSuffix(int fieldId) {
        return fieldId == BASE_FIELD_ID ? BASE_SUFFIX : (FIELD_INFIX + fieldId);
    }

    //TODO: (EW) remove this overload and force specifying the group index everywhere
    private static AppLevelDefaults getTestFieldOrBase(int flatIndex,
                                                       Predicate<TestField> override) {
        FieldIndex fieldIndex = new FieldIndex(flatIndex);
        return getTestFieldOrBase(fieldIndex.groupIndex, fieldIndex.fieldIndex, override);
    }

    private static AppLevelDefaults getTestFieldOrBase(int groupIndex, int fieldIndex,
                                                       Predicate<TestField> override) {
        TestField testField;
        //TODO: (EW) consider moving this index check into getField since this is the only caller
        if (groupIndex < 0 || groupIndex >= getTestFieldGroupCount()
                || fieldIndex < 0 || fieldIndex >= getTestFieldCount(groupIndex)) {
            testField = null;
        } else {
            testField = getField(groupIndex, fieldIndex);
        }
        if (testField == null || !override.test(testField)) {
            return getInstance().mTestFieldDefaults;
        }
        return testField;
    }

    private static boolean readOverrideTextInputModification(final SharedPreferenceManager prefs,
                                                             int fieldId) {
        return prefs.getBoolean(PREF_OVERRIDE_TEXT_INPUT_MODIFICATION_PREFIX + getSuffix(fieldId),
                false);
    }

    public static final boolean DEFAULT_MODIFY_COMMITTED_TEXT = false;

    private static boolean readModifyCommittedText(final SharedPreferenceManager prefs,
                                                   int fieldId) {
        return prefs.getBoolean(PREF_MODIFY_COMMITTED_TEXT_PREFIX + getSuffix(fieldId),
                DEFAULT_MODIFY_COMMITTED_TEXT);
    }

    public static boolean shouldModifyCommittedText(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTextInputModification)
                .mModifyCommittedText;
    }

    public static final boolean DEFAULT_MODIFY_COMPOSED_TEXT = false;

    private static boolean readModifyComposedText(final SharedPreferenceManager prefs,
                                                  int fieldId) {
        return prefs.getBoolean(PREF_MODIFY_COMPOSED_TEXT_PREFIX + getSuffix(fieldId),
                DEFAULT_MODIFY_COMPOSED_TEXT);
    }

    public static boolean shouldModifyComposedText(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTextInputModification)
                .mModifyComposedText;
    }

    public static final boolean DEFAULT_MODIFY_COMPOSED_CHANGES_ONLY = false;

    private static boolean readModifyComposedChangesOnly(final SharedPreferenceManager prefs,
                                                         int fieldId) {
        return prefs.getBoolean(PREF_MODIFY_COMPOSED_CHANGES_ONLY_PREFIX + getSuffix(fieldId),
                DEFAULT_MODIFY_COMPOSED_CHANGES_ONLY);
    }

    public static boolean shouldModifyComposedChangesOnly(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTextInputModification)
                .mModifyComposedChangesOnly;
    }

    public static final boolean DEFAULT_CONSIDER_COMPOSED_CHANGES_FROM_END = false;

    private static boolean readConsiderComposedChangesFromEnd(final SharedPreferenceManager prefs,
                                                              int fieldId) {
        return prefs.getBoolean(PREF_CONSIDER_COMPOSED_CHANGES_FROM_END_PREFIX + getSuffix(fieldId),
                DEFAULT_CONSIDER_COMPOSED_CHANGES_FROM_END);
    }

    public static boolean shouldConsiderComposedChangesFromEnd(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTextInputModification)
                .mConsiderComposedChangesFromEnd;
    }

    public static final boolean DEFAULT_RESTRICT_TO_INCLUDE = false;

    private static boolean readRestrictToInclude(final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getBoolean(PREF_RESTRICT_TO_INCLUDE_PREFIX + getSuffix(fieldId),
                DEFAULT_RESTRICT_TO_INCLUDE);
    }

    public static boolean shouldRestrictToInclude(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTextInputModification)
                .mRestrictToInclude;
    }

    public static final String[] DEFAULT_RESTRICT_SPECIFIC = new String[0];

    private static String[] readRestrictSpecific(final SharedPreferenceManager prefs, int fieldId) {
        TextList<String> textList = (new TextListPreference.Reader(prefs,
                PREF_RESTRICT_SPECIFIC_PREFIX + getSuffix(fieldId))).readValue();
        String[] result = new String[textList.getDataArray().length];
        for (int i = 0; i < textList.getDataArray().length; i++) {
            if (textList.escapeChars()) {
                result[i] = escapeChars(textList.getDataArray()[i]);
            } else {
                result[i] = textList.getDataArray()[i];
            }
        }
        return result;
    }

    public static String[] getRestrictSpecific(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTextInputModification)
                .mRestrictSpecific;
    }

    private static String escapeChars(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean escapeNextChar = false;
        char[] unicode;
        int i = 0;
        while (i < text.length()) {
            char current = text.charAt(i);
            if (escapeNextChar) {
                switch (current) {
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case '0':
                        sb.append('\0');
                        break;
                    case 'u':
                        unicode = new char[4];
                        int unicodeIndex = 0;
                        while (unicodeIndex < unicode.length
                                && i + unicodeIndex + 1 < text.length()) {
                            char unicodeChar = text.charAt(i + unicodeIndex + 1);
                            if (!((unicodeChar >= '0' && unicodeChar <= '9')
                                    || (unicodeChar >= 'a' && unicodeChar <= 'f'))) {
                                break;
                            }
                            unicode[unicodeIndex] = unicodeChar;
                            unicodeIndex++;
                        }
                        if (unicodeIndex != unicode.length) {
                            Log.e(TAG, "Invalid escape character at " + (i + unicodeIndex + 1)
                                    + ": \"" + text + "\"");
                            if (unicodeIndex == 0) {
                                // no hex digits were listed, so treat as just an unnecessary escape
                                // of the 'u' character and just use the original character
                                // (skipping '\'). don't increment i to process the current char
                                // again as not being escaped
                                escapeNextChar = false;
                                continue;
                            }
                            // assume leading 0s were just skipped. shift the existing values and
                            // insert 0s.
                            int shift = unicode.length - unicodeIndex;
                            for (int j = unicode.length - 1; j >= 0; j--) {
                                unicode[j] = j - shift >= 0 ? unicode[j - shift] : '0';
                            }
                        }
                        sb.append((char)Integer.parseInt(new String(unicode), 16));
                        i += unicodeIndex;
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    default:
                        Log.e(TAG, "Invalid escape character at " + i + ": \"" + text + "\"");
                        // treat as just an unnecessary escape of the character and just use the
                        // original character (skipping '\'). don't increment i to process the
                        // current char again as not being escaped
                        escapeNextChar = false;
                        continue;
                }
                escapeNextChar = false;
            } else if (text.charAt(i) == '\\') {
                escapeNextChar = true;
            } else {
                sb.append(current);
            }
            i++;
        }
        return sb.toString();
    }

    public static final IntRange DEFAULT_RESTRICT_RANGE = null;

    @Nullable
    private static IntRange readRestrictRange(final SharedPreferenceManager prefs, int fieldId) {
        return (new CodepointRangeDialogPreference.Reader(prefs,
                PREF_RESTRICT_RANGE_PREFIX + getSuffix(fieldId)))
                .readValue();
    }

    public static @Nullable IntRange getRestrictRange(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTextInputModification)
                .mRestrictRange;
    }

    public static final TranslateText[] DEFAULT_TRANSLATE_SPECIFIC = new TranslateText[0];

    private static TranslateText[] readTranslateSpecific(final SharedPreferenceManager prefs,
                                                         int fieldId) {
        TextList<TranslateText> textList =
                (new TextTranslateListPreference.Reader(prefs,
                        PREF_TRANSLATE_SPECIFIC_PREFIX + getSuffix(fieldId)))
                        .readValue();
        TranslateText[] result = new TranslateText[textList.getDataArray().length];
        for (int i = 0; i < textList.getDataArray().length; i++) {
            if (textList.escapeChars()) {
                result[i] = new TranslateText(escapeChars(textList.getDataArray()[i].getOriginal()),
                        escapeChars(textList.getDataArray()[i].getTranslation()));
            } else {
                result[i] = textList.getDataArray()[i];
            }
        }
        return result;
    }

    public static TranslateText[] getTranslateSpecific(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTextInputModification)
                .mTranslateSpecific;
    }

    public static final boolean DEFAULT_TRANSLATE_FULL_MATCH_ONLY = false;

    private static boolean readTranslateFullMatchOnly(final SharedPreferenceManager prefs,
                                                      int fieldId) {
        return prefs.getBoolean(PREF_TRANSLATE_FULL_MATCH_ONLY_PREFIX + getSuffix(fieldId),
                DEFAULT_TRANSLATE_FULL_MATCH_ONLY);
    }

    public static boolean shouldTranslateFullMatchOnly(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTextInputModification)
                .mTranslateFullMatchOnly;
    }

    public static final int DEFAULT_CODEPOINT_SHIFT = 0;

    private static int readShiftCodepoint(final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getInt(PREF_SHIFT_CODEPOINT_PREFIX + getSuffix(fieldId),
                DEFAULT_CODEPOINT_SHIFT);
    }

    public static int getShiftCodepoint(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTextInputModification)
                .mShiftCodepoint;
    }

    private static boolean readOverrideTextReturn(final SharedPreferenceManager prefs,
                                                  int fieldId) {
        return prefs.getBoolean(PREF_OVERRIDE_TEXT_RETURN_PREFIX + getSuffix(fieldId), false);
    }

    public static final boolean DEFAULT_SKIP_EXTRACTING_TEXT = false;

    private static boolean readSkipExtractingText(final SharedPreferenceManager prefs,
                                                  int fieldId) {
        return prefs.getBoolean(PREF_SKIP_EXTRACTING_TEXT_PREFIX + getSuffix(fieldId),
                DEFAULT_SKIP_EXTRACTING_TEXT);
    }

    public static boolean shouldSkipExtractingText(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTextReturn)
                .mSkipExtractingText;
    }

    public static final boolean DEFAULT_IGNORE_EXTRACTED_TEXT_MONITOR = false;

    private static boolean readIgnoreExtractedTextMonitor(final SharedPreferenceManager prefs,
                                                          int fieldId) {
        return prefs.getBoolean(PREF_IGNORE_EXTRACTED_TEXT_MONITOR_PREFIX + getSuffix(fieldId),
                DEFAULT_IGNORE_EXTRACTED_TEXT_MONITOR);
    }

    public static boolean shouldIgnoreExtractedTextMonitor(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTextReturn)
                .mIgnoreExtractedTextMonitor;
    }

    public static final boolean DEFAULT_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT = false;

    private static boolean readUpdateSelectionBeforeExtractedText(
            final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getBoolean(
                PREF_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT_PREFIX + getSuffix(fieldId),
                DEFAULT_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT);
    }

    public static boolean shouldUpdateSelectionBeforeExtractedText(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTextReturn)
                .mUpdateSelectionBeforeExtractedText;
    }

    public static final boolean DEFAULT_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES = false;

    private static boolean readUpdateExtractedTextOnlyOnNetChanges(
            final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getBoolean(
                PREF_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES_PREFIX + getSuffix(fieldId),
                DEFAULT_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES);
    }

    public static boolean shouldUpdateExtractedTextOnlyOnNetChanges(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTextReturn)
                .mUpdateExtractedTextOnlyOnNetChanges;
    }

    public static final boolean DEFAULT_EXTRACT_FULL_TEXT = false;

    private static boolean readExtractFullText(final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getBoolean(PREF_EXTRACT_FULL_TEXT_PREFIX + getSuffix(fieldId),
                DEFAULT_EXTRACT_FULL_TEXT);
    }

    public static boolean shouldExtractFullText(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTextReturn)
                .mExtractFullText;
    }

    public static final int DEFAULT_EXTRACT_MONITOR_TEXT_LIMIT = -1;

    private static int readExtractMonitorTextLimit(final SharedPreferenceManager prefs,
                                                   int fieldId) {
        return prefs.getInt(PREF_LIMIT_EXTRACT_MONITOR_TEXT_PREFIX + getSuffix(fieldId),
                DEFAULT_EXTRACT_MONITOR_TEXT_LIMIT);
    }

    public static int getExtractMonitorTextLimit(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTextReturn)
                .mExtractMonitorTextLimit;
    }

    public static final int DEFAULT_RETURNED_TEXT_LIMIT = -1;

    private static int readReturnedTextLimit(final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getInt(PREF_LIMIT_RETURNED_TEXT_PREFIX + getSuffix(fieldId),
                DEFAULT_RETURNED_TEXT_LIMIT);
    }

    public static int getReturnedTextLimit(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTextReturn)
                .mReturnedTextLimit;
    }

    private static boolean readOverrideTextComposition(final SharedPreferenceManager prefs,
                                                       int fieldId) {
        return prefs.getBoolean(PREF_OVERRIDE_TEXT_COMPOSITION_PREFIX + getSuffix(fieldId), false);
    }

    public static final boolean DEFAULT_DELETE_THROUGH_COMPOSING_TEXT = false;

    private static boolean readDeleteThroughComposingText(final SharedPreferenceManager prefs,
                                                          int fieldId) {
        return prefs.getBoolean(PREF_DELETE_THROUGH_COMPOSING_TEXT_PREFIX + getSuffix(fieldId),
                DEFAULT_DELETE_THROUGH_COMPOSING_TEXT);
    }

    public static boolean shouldDeleteThroughComposingText(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTextComposition)
                .mDeleteThroughComposingText;
    }

    public static final boolean DEFAULT_KEEP_EMPTY_COMPOSING_POSITION = false;

    private static boolean readKeepEmptyComposingPosition(final SharedPreferenceManager prefs,
                                                          int fieldId) {
        return prefs.getBoolean(PREF_KEEP_EMPTY_COMPOSING_POSITION_PREFIX + getSuffix(fieldId),
                DEFAULT_KEEP_EMPTY_COMPOSING_POSITION);
    }

    public static boolean shouldKeepEmptyComposingPosition(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTextComposition)
                .mKeepEmptyComposingPosition;
    }

    private static boolean readOverrideTargetVersion(final SharedPreferenceManager prefs,
                                                     int fieldId) {
        return prefs.getBoolean(PREF_OVERRIDE_TARGET_VERSION_SIMULATION_PREFIX + getSuffix(fieldId),
                false);
    }

    public static final boolean DEFAULT_SKIP_TAKESNAPSHOT = false;

    private static boolean readSkipTakeSnapshot(final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getBoolean(PREF_SKIP_TAKESNAPSHOT_PREFIX + getSuffix(fieldId),
                DEFAULT_SKIP_TAKESNAPSHOT);
    }

    public static boolean shouldSkipTakeSnapshot(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTargetVersion)
                .mSkipTakeSnapshot;
    }

    public static final boolean DEFAULT_SKIP_GETSURROUNDINGTEXT = false;

    private static boolean readSkipGetSurroundingText(final SharedPreferenceManager prefs,
                                                      int fieldId) {
        return prefs.getBoolean(PREF_SKIP_GETSURROUNDINGTEXT_PREFIX + getSuffix(fieldId),
                DEFAULT_SKIP_GETSURROUNDINGTEXT);
    }

    public static boolean shouldSkipGetSurroundingText(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTargetVersion)
                .mSkipGetSurroundingText;
    }

    public static final boolean DEFAULT_SKIP_PERFORMSPELLCHECK = false;

    private static boolean readSkipPerformSpellCheck(final SharedPreferenceManager prefs,
                                                     int fieldId) {
        return prefs.getBoolean(PREF_SKIP_PERFORMSPELLCHECK_PREFIX + getSuffix(fieldId),
                DEFAULT_SKIP_PERFORMSPELLCHECK);
    }

    public static boolean shouldSkipPerformSpellCheck(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTargetVersion)
                .mSkipPerformSpellCheck;
    }

    public static final boolean DEFAULT_SKIP_SETIMECONSUMESINPUT = false;

    private static boolean readSkipSetImeConsumesInput(final SharedPreferenceManager prefs,
                                                       int fieldId) {
        return prefs.getBoolean(PREF_SKIP_SETIMECONSUMESINPUT_PREFIX + getSuffix(fieldId),
                DEFAULT_SKIP_SETIMECONSUMESINPUT);
    }

    public static boolean shouldSkipSetImeConsumesInput(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTargetVersion)
                .mSkipSetImeConsumesInput;
    }

    public static final boolean DEFAULT_SKIP_COMMITCONTENT = false;

    private static boolean readSkipCommitContent(final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getBoolean(PREF_SKIP_COMMITCONTENT_PREFIX + getSuffix(fieldId),
                DEFAULT_SKIP_COMMITCONTENT);
    }

    public static boolean shouldSkipCommitContent(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTargetVersion)
                .mSkipCommitContent;
    }

    public static final boolean DEFAULT_SKIP_CLOSECONNECTION = false;

    private static boolean readSkipCloseConnection(final SharedPreferenceManager prefs,
                                                   int fieldId) {
        return prefs.getBoolean(PREF_SKIP_CLOSECONNECTION_PREFIX + getSuffix(fieldId),
                DEFAULT_SKIP_CLOSECONNECTION);
    }

    public static boolean shouldSkipCloseConnection(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTargetVersion)
                .mSkipCloseConnection;
    }

    public static final boolean DEFAULT_SKIP_DELETESURROUNDINGTEXTINCODEPOINTS = false;

    private static boolean readSkipDeleteSurroundingTextInCodePoints(
            final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getBoolean(PREF_SKIP_DELETESURROUNDINGTEXTINCODEPOINTS_PREFIX + getSuffix(fieldId), DEFAULT_SKIP_DELETESURROUNDINGTEXTINCODEPOINTS);
    }

    public static boolean shouldSkipDeleteSurroundingTextInCodePoints(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTargetVersion)
                .mSkipDeleteSurroundingTextInCodePoints;
    }

    public static final boolean DEFAULT_SKIP_REQUESTCURSORUPDATES = false;

    private static boolean readSkipRequestCursorUpdates(final SharedPreferenceManager prefs,
                                                        int fieldId) {
        return prefs.getBoolean(PREF_SKIP_REQUESTCURSORUPDATES_PREFIX + getSuffix(fieldId),
                DEFAULT_SKIP_REQUESTCURSORUPDATES);
    }

    public static boolean shouldSkipRequestCursorUpdates(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTargetVersion)
                .mSkipRequestCursorUpdates;
    }

    public static final boolean DEFAULT_SKIP_COMMITCORRECTION = false;

    private static boolean readSkipCommitCorrection(final SharedPreferenceManager prefs,
                                                    int fieldId) {
        return prefs.getBoolean(PREF_SKIP_COMMITCORRECTION_PREFIX + getSuffix(fieldId),
                DEFAULT_SKIP_COMMITCORRECTION);
    }

    public static boolean shouldSkipCommitCorrection(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTargetVersion)
                .mSkipCommitCorrection;
    }

    public static final boolean DEFAULT_SKIP_GETSELECTEDTEXT = false;

    private static boolean readSkipGetSelectedText(final SharedPreferenceManager prefs,
                                                   int fieldId) {
        return prefs.getBoolean(PREF_SKIP_GETSELECTEDTEXT_PREFIX + getSuffix(fieldId),
                DEFAULT_SKIP_GETSELECTEDTEXT);
    }

    public static boolean shouldSkipGetSelectedText(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTargetVersion)
                .mSkipGetSelectedText;
    }

    public static final boolean DEFAULT_SKIP_SETCOMPOSINGREGION = false;

    private static boolean readSkipSetComposingRegion(final SharedPreferenceManager prefs,
                                                      int fieldId) {
        return prefs.getBoolean(PREF_SKIP_SETCOMPOSINGREGION_PREFIX + getSuffix(fieldId),
                DEFAULT_SKIP_SETCOMPOSINGREGION);
    }

    public static boolean shouldSkipSetComposingRegion(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideTargetVersion)
                .mSkipSetComposingRegion;
    }

    private static boolean readOverrideSystemBehavior(final SharedPreferenceManager prefs,
                                                      int fieldId) {
        return prefs.getBoolean(
                PREF_OVERRIDE_SYSTEM_BEHAVIOR_SIMULATION_PREFIX + getSuffix(fieldId),
                false);
    }

    public static final int DEFAULT_UPDATE_DELAY = 0;

    private static int readUpdateDelay(final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getInt(PREF_UPDATE_DELAY_PREFIX + getSuffix(fieldId), DEFAULT_UPDATE_DELAY);
    }

    public static int getUpdateDelay(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideSystemBehavior)
                .mUpdateDelay;
    }

    public static final int DEFAULT_FINISHCOMPOSINGTEXT_DELAY = 0;

    private static int readFinishComposingTextDelay(final SharedPreferenceManager prefs,
                                                    int fieldId) {
        return prefs.getInt(PREF_FINISHCOMPOSINGTEXT_DELAY_PREFIX + getSuffix(fieldId),
                DEFAULT_FINISHCOMPOSINGTEXT_DELAY);
    }

    public static int getFinishComposingTextDelay(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideSystemBehavior)
                .mFinishComposingTextDelay;
    }

    public static final int DEFAULT_GETSURROUNDINGTEXT_DELAY = 0;

    private static int readGetSurroundingTextDelay(final SharedPreferenceManager prefs,
                                                   int fieldId) {
        return prefs.getInt(PREF_GETSURROUNDINGTEXT_DELAY_PREFIX + getSuffix(fieldId),
                DEFAULT_GETSURROUNDINGTEXT_DELAY);
    }

    public static int getGetSurroundingTextDelay(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideSystemBehavior)
                .mGetSurroundingTextDelay;
    }

    public static final int DEFAULT_GETTEXTBEFORECURSOR_DELAY = 0;

    private static int readGetTextBeforeCursorDelay(final SharedPreferenceManager prefs,
                                                    int fieldId) {
        return prefs.getInt(PREF_GETTEXTBEFORECURSOR_DELAY_PREFIX + getSuffix(fieldId),
                DEFAULT_GETTEXTBEFORECURSOR_DELAY);
    }

    public static int getGetTextBeforeCursorDelay(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideSystemBehavior)
                .mGetTextBeforeCursorDelay;
    }

    public static final int DEFAULT_GETSELECTEDTEXT_DELAY = 0;

    private static int readGetSelectedTextDelay(final SharedPreferenceManager prefs,
                                                int fieldId) {
        return prefs.getInt(PREF_GETSELECTEDTEXT_DELAY_PREFIX + getSuffix(fieldId),
                DEFAULT_GETSELECTEDTEXT_DELAY);
    }

    public static int getGetSelectedTextDelay(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideSystemBehavior)
                .mGetSelectedTextDelay;
    }

    public static final int DEFAULT_GETTEXTAFTERCURSOR_DELAY = 0;

    private static int readGetTextAfterCursorDelay(final SharedPreferenceManager prefs,
                                                   int fieldId) {
        return prefs.getInt(PREF_GETTEXTAFTERCURSOR_DELAY_PREFIX + getSuffix(fieldId),
                DEFAULT_GETTEXTAFTERCURSOR_DELAY);
    }

    public static int getGetTextAfterCursorDelay(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideSystemBehavior)
                .mGetTextAfterCursorDelay;
    }

    public static final int DEFAULT_GETCURSORCAPSMODE_DELAY = 0;

    private static int readGetCursorCapsModeDelay(final SharedPreferenceManager prefs,
                                                  int fieldId) {
        return prefs.getInt(PREF_GETCURSORCAPSMODE_DELAY_PREFIX + getSuffix(fieldId),
                DEFAULT_GETCURSORCAPSMODE_DELAY);
    }

    public static int getGetCursorCapsModeDelay(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideSystemBehavior)
                .mGetCursorCapsModeDelay;
    }

    public static final int DEFAULT_GETEXTRACTEDTEXT_DELAY = 0;
    
    private static int readGetExtractedTextDelay(final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getInt(PREF_GETEXTRACTEDTEXT_DELAY_PREFIX + getSuffix(fieldId),
                DEFAULT_GETEXTRACTEDTEXT_DELAY);
    }

    public static int getGetExtractedTextDelay(int fieldId) {
        return getTestFieldOrBase(fieldId, testField -> testField.mOverrideSystemBehavior)
                .mGetExtractedTextDelay;
    }

    private static String readGroupName(final SharedPreferenceManager prefs, int groupIndex) {
        //TODO: (EW) it probably would be cleaner to use an ID rather than an index
        return prefs.getString(PREF_TEST_GROUP_NAME_PREFIX + groupIndex, null);
    }

    private static TestGroup[] readTestFieldGroups(final SharedPreferenceManager prefs) {
        int[] fieldIds = prefs.getIntArray(PREF_TEST_FIELD_IDS, new int[] { 0 });
        if (fieldIds == null || fieldIds.length < 1) {
            // there should always be at least 1 field
            Log.e(TAG, "No test fields");
            fieldIds = new int[] { 0 };
        }

        int[] groupFieldCounts = prefs.getIntArray(PREF_TEST_GROUP_FIELD_COUNTS,
                new int[] { fieldIds.length });
        if (groupFieldCounts == null || groupFieldCounts.length < 1) {
            // there should always be at least 1 group
            Log.e(TAG, "No test field groups");
            groupFieldCounts = new int[] { fieldIds.length };
        }
        int[][] groupFieldIds = new int[groupFieldCounts.length][];
        int fieldIndex = 0;
        for (int i = 0; i < groupFieldCounts.length; i++) {
            // determine how many fields should be part of the group
            int remainingFields = fieldIds.length - fieldIndex;
            if (i == groupFieldCounts.length - 1) {
                // put all of the remaining fields in the last group
                groupFieldIds[i] = new int[remainingFields];
            } else {
                // make sure the group doesn't have a negative field count and that it isn't trying
                // to claim more fields than are actually left
                groupFieldIds[i] = new int[
                        Math.min(remainingFields, Math.max(0, groupFieldCounts[i]))];
            }
            if (groupFieldIds[i].length != groupFieldCounts[i]) {
                Log.e(TAG, "Group " + i + " was configured with " + groupFieldCounts[i]
                        + " fields but actually received " + groupFieldIds[i].length + " fields");
            }

            // add the fields to the group
            System.arraycopy(fieldIds, fieldIndex, groupFieldIds[i], 0, groupFieldIds[i].length);

            // set the next unused field
            fieldIndex += groupFieldIds[i].length;
        }

        int groupCount = groupFieldCounts.length;
        TestGroup[] testGroups = new TestGroup[groupCount];
        for (int i = 0; i < testGroups.length; i++) {
            String name = prefs.getString(PREF_TEST_GROUP_NAME_PREFIX + i, null);
            testGroups[i] = new TestGroup(name, groupFieldIds[i]);
        }

        return testGroups;
    }

    private static int fieldCount(TestGroup[] testGroups) {
        int totalTestFieldCount = 0;
        for (TestGroup testGroup : testGroups) {
            totalTestFieldCount += testGroup.mFieldIds.length;
        }
        return totalTestFieldCount;
    }

    public static void setTestGroupFields(int groupIndex, int[] fieldIds) {
        TestGroup[] testGroups = new TestGroup[Settings.getTestFieldGroupCount()];
        for (int i = 0; i < testGroups.length; i++) {
            if (i == groupIndex) {
                testGroups[i] = new TestGroup(getInstance().mTestGroups.get(i).mName,
                        deepCopy(fieldIds));
            } else {
                //TODO: (EW) probably don't need the deep copy
                testGroups[i] = getInstance().mTestGroups.get(i).copy();
            }
        }
        Settings.setTestFieldGroups(testGroups);
    }

    public static void setTestFieldGroups(TestGroup[] testGroups) {
        // flatten the group/field data for saving preferences
        int[] testFieldIds = new int[fieldCount(testGroups)];
        int[] testGroupFieldCounts = new int[testGroups.length];
        String[] testGroupNames = new String[testGroups.length];
        Set<Integer> updatedTestFieldIds = new HashSet<>();
        int fieldIndex = 0;
        for (int i = 0; i < testGroups.length; i++) {
            System.arraycopy(testGroups[i].mFieldIds, 0, testFieldIds, fieldIndex,
                    testGroups[i].mFieldIds.length);
            // set the next unset field
            fieldIndex += testGroups[i].mFieldIds.length;

            testGroupFieldCounts[i] = testGroups[i].mFieldIds.length;
            testGroupNames[i] = testGroups[i].mName;

            for (int fieldId : testGroups[i].mFieldIds) {
                updatedTestFieldIds.add(fieldId);
            }
        }

        Settings settings = getInstance();

        Editor editor = settings.mPrefs.edit();

        // delete any fields that are getting removed
        for (int fieldId : settings.mTestFields.keySet().toArray(new Integer[0])) {
            if (!updatedTestFieldIds.contains(fieldId)) {
                // remove the old field
                settings.mTestFields.remove(fieldId);
                // clear all of the now orphaned test field preferences to avoid bloat
                removeTestFieldPrefs(editor, fieldId);
            }
        }

        // add any new fields
        List<Integer> addedFieldIds = new ArrayList<>();
        for (int fieldId : updatedTestFieldIds) {
            if (settings.mTestFields.containsKey(fieldId)) {
                continue;
            }
            settings.mTestFields.put(fieldId, new TestField(fieldId));
            addedFieldIds.add(fieldId);
        }

        // delete any groups that are getting removed
        for (int i = 0; i < settings.mTestFields.size() - testGroups.length; i++) {
            removeTestGroupPrefs(editor, testGroups.length + i);
        }

        // update the groups
        settings.mTestGroups.clear();
        for (TestGroup testGroup : testGroups) {
            settings.mTestGroups.add(testGroup.copy());
        }

        editor.putIntArray(PREF_TEST_FIELD_IDS, testFieldIds);
        editor.putIntArray(PREF_TEST_GROUP_FIELD_COUNTS, testGroupFieldCounts);
        for (int i = 0; i < testGroupNames.length; i++) {
            editor.putString(PREF_TEST_GROUP_NAME_PREFIX + i, testGroupNames[i]);
        }

        editor.apply();

        // load the default values for any new fields
        for (int fieldId : addedFieldIds) {
            settings.loadTestFieldSettings(fieldId);
            getField(fieldId).mHintText = "field " + fieldId;
        }
    }

    //TODO: (EW) update this function to force adding a field to a specific group
    public static void setTestFieldIds(int[] testFieldIds) {
        setTestFieldGroups(new TestGroup[] {
                new TestGroup(null, testFieldIds)
        });
    }

    public static int getTestFieldGroupCount() {
        return getInstance().mTestGroups.size();
    }

    public static int getTestFieldCount(int groupIndex) {
        return getInstance().mTestGroups.get(groupIndex).mFieldIds.length;
    }

    public static String getTestFieldGroupName(int groupIndex) {
        return getInstance().mTestGroups.get(groupIndex).mName;
    }

    public static int getTestFieldId(int groupIndex, int fieldIndex) {
        return getInstance().mTestGroups.get(groupIndex).mFieldIds[fieldIndex];
    }

    //TODO: (EW) consider removing the need for this
    public static int getTestFieldFlatIndex(int groupIndex, int fieldIndex) {
        int flatIndex = 0;
        for (int i = 0; i < groupIndex; i++) {
            flatIndex += getInstance().mTestGroups.get(i).mFieldIds.length;
        }
        flatIndex += fieldIndex;
        return flatIndex;
    }

    //TODO: (EW) remove the need for this (or at least the flat index converting constructor)
    private static class FieldIndex {
        public final int groupIndex;
        public final int fieldIndex;
        public FieldIndex(int flatIndex) {
            int groupIndex = 0;
            int fieldIndex = flatIndex;
            while (groupIndex < getInstance().mTestGroups.size()
                    && getInstance().mTestGroups.get(groupIndex).mFieldIds.length <= fieldIndex) {
                fieldIndex -= getInstance().mTestGroups.get(groupIndex).mFieldIds.length;
                groupIndex++;
            }
            this.groupIndex = groupIndex;
            this.fieldIndex = fieldIndex;
        }
    }

    //TODO: (EW) consider removing the need for this
    public static int getTestFieldCount() {
        int totalTestFieldCount = 0;
        for (TestGroup group : getInstance().mTestGroups) {
            totalTestFieldCount += group.mFieldIds.length;
        }
        return totalTestFieldCount;
    }

    private static TestField getField(int groupIndex, int fieldIndex) {
        return getInstance().mTestFields.get(
                getInstance().mTestGroups.get(groupIndex).mFieldIds[fieldIndex]);
    }

    private static TestField getField(int fieldId) {
        if (!getInstance().mTestFields.containsKey(fieldId)) {
            //TODO: (EW) consider just crashing similar to the index out of bounds in the other
            // overload (or make that fail gracefully to match this)
            Log.e(TAG, "Tried to get field " + fieldId + ", which doesn't exist");
            return null;
        }
        return getInstance().mTestFields.get(fieldId);
    }

    //TODO: (EW) consider removing the need for this
    public static int getTestFieldId(int flatIndex) {
        FieldIndex fieldIndex = new FieldIndex(flatIndex);
        return getInstance().mTestGroups.get(fieldIndex.groupIndex)
                .mFieldIds[fieldIndex.fieldIndex];
    }

    public static void addTestFieldGroup() {
        Settings settings = getInstance();
        TestGroup[] testGroups = new TestGroup[settings.mTestGroups.size() + 1];
        for (int i = 0; i < settings.mTestGroups.size(); i++) {
            //TODO: (EW) probably don't need the deep copy
            testGroups[i] = settings.mTestGroups.get(i).copy();
        }
        testGroups[testGroups.length - 1] = new TestGroup(null, new int[0]);
        setTestFieldGroups(testGroups);
    }

    public static void removeTestFieldGroup(int groupIndex) {
        Settings settings = getInstance();
        TestGroup[] testGroups = new TestGroup[settings.mTestGroups.size() - 1];
        for (int i = 0; i < settings.mTestGroups.size(); i++) {
            if (i == groupIndex) {
                continue;
            }
            //TODO: (EW) probably don't need the deep copy
            testGroups[i > groupIndex ? i + 1 : i] = settings.mTestGroups.get(i).copy();
        }
        setTestFieldGroups(testGroups);
    }

    public static void addTestField(int groupIndex) {
        Settings settings = getInstance();
        TestGroup[] testGroups = new TestGroup[settings.mTestGroups.size()];
        for (int i = 0; i < testGroups.length; i++) {
            testGroups[i] = settings.mTestGroups.get(i).copy();
            if (i == groupIndex) {
                // add a new field
                int[] fieldIds = new int[testGroups[i].mFieldIds.length + 1];
                System.arraycopy(testGroups[i].mFieldIds, 0, fieldIds, 0,
                        testGroups[i].mFieldIds.length);
                fieldIds[fieldIds.length - 1] = getNextId(settings.mTestFields.keySet());
                testGroups[i].mFieldIds = fieldIds;
            }
        }
        setTestFieldGroups(testGroups);
    }

    public static void removeTestField(int groupIndex, int fieldIndex) {
        Settings settings = getInstance();
        TestGroup[] testGroups = new TestGroup[settings.mTestGroups.size()];
        for (int i = 0; i < testGroups.length; i++) {
            testGroups[i] = settings.mTestGroups.get(i).copy();
            if (i == groupIndex) {
                // remove a new field
                int[] fieldIds = new int[testGroups[i].mFieldIds.length - 1];
                System.arraycopy(testGroups[i].mFieldIds, 0, fieldIds, 0, fieldIndex);
                System.arraycopy(testGroups[i].mFieldIds, fieldIndex + 1, fieldIds, fieldIndex,
                        testGroups[i].mFieldIds.length - fieldIndex - 1);
                testGroups[i].mFieldIds = fieldIds;
            }
        }
        setTestFieldGroups(testGroups);
    }

    private static void removeTestGroupPrefs(Editor editor, int index) {
        final String[] testGroupPrefKeyPrefixes = new String[]{
                PREF_TEST_GROUP_NAME_PREFIX
        };
        for (String prefKeyPrefix : testGroupPrefKeyPrefixes) {
            editor.remove(prefKeyPrefix + index);
        }
    }

    private static void removeTestFieldPrefs(Editor editor, int idToRemove) {
        final String[] testFieldPrefKeyPrefixes = new String[]{
                PREF_INPUT_TYPE_CLASS_PREFIX,
                PREF_INPUT_TYPE_TEXT_VARIATION_PREFIX,
                PREF_INPUT_TYPE_NUMBER_VARIATION_PREFIX,
                PREF_INPUT_TYPE_DATETIME_VARIATION_PREFIX,
                PREF_INPUT_TYPE_TEXT_FLAG_MULTI_LINE_PREFIX,
                PREF_INPUT_TYPE_TEXT_FLAG_CAP_PREFIX,
                PREF_INPUT_TYPE_TEXT_FLAG_AUTO_COMPLETE_PREFIX,
                PREF_INPUT_TYPE_TEXT_FLAG_AUTO_CORRECT_PREFIX,
                PREF_INPUT_TYPE_TEXT_FLAG_NO_SUGGESTIONS_PREFIX,
                PREF_INPUT_TYPE_NUMBER_FLAG_SIGNED_PREFIX,
                PREF_INPUT_TYPE_NUMBER_FLAG_DECIMAL_PREFIX,
                PREF_NULL_INPUT_TYPE_MULTILINE_PREFIX,
                PREF_CREATE_INPUT_CONNECTION_PREFIX,
                PREF_SEND_SELECTION_INFO_PREFIX,
                PREF_SEND_TEXT_PREFIX,
                PREF_COMPOSING_TEXT_BEHAVIOR_PREFIX,
                PREF_ALLOW_DELETE_SURROUNDING_TEXT_PREFIX,
                PREF_ALLOW_SETTING_SELECTION_PREFIX,
                PREF_IME_OPTIONS_ACTION_PREFIX,
                PREF_IME_OPTIONS_FLAG_FORCE_ASCII_PREFIX,
                PREF_IME_OPTIONS_FLAG_NAVIGATE_NEXT_PREFIX,
                PREF_IME_OPTIONS_FLAG_NAVIGATE_PREVIOUS_PREFIX,
                PREF_IME_OPTIONS_FLAG_NO_ACCESSORY_ACTION_PREFIX,
                PREF_IME_OPTIONS_FLAG_NO_ENTER_ACTION_PREFIX,
                PREF_IME_OPTIONS_FLAG_NO_EXTRACT_UI_PREFIX,
                PREF_IME_OPTIONS_FLAG_NO_FULLSCREEN_PREFIX,
                PREF_IME_OPTIONS_FLAG_NO_PERSONALIZED_LEARNING_PREFIX,
                PREF_IME_ACTION_ID_PREFIX,
                PREF_IME_ACTION_LABEL_PREFIX,
                PREF_PRIVATE_IME_OPTIONS_PREFIX,
                PREF_SELECT_ALL_ON_FOCUS_PREFIX,
                PREF_MAX_LENGTH_PREFIX,
                PREF_ALLOW_UNDO_PREFIX,
                PREF_TEXT_LOCALES_PREFIX,
                PREF_IME_HINT_LOCALES_PREFIX,
                PREF_IME_DEFAULT_TEXT_PREFIX,
                PREF_IME_HINT_TEXT_PREFIX,

                PREF_OVERRIDE_TEXT_INPUT_MODIFICATION_PREFIX,
                PREF_MODIFY_COMMITTED_TEXT_PREFIX,
                PREF_MODIFY_COMPOSED_TEXT_PREFIX,
                PREF_MODIFY_COMPOSED_CHANGES_ONLY_PREFIX,
                PREF_CONSIDER_COMPOSED_CHANGES_FROM_END_PREFIX,
                PREF_RESTRICT_TO_INCLUDE_PREFIX,
                PREF_RESTRICT_SPECIFIC_PREFIX,
                PREF_RESTRICT_RANGE_PREFIX,
                PREF_TRANSLATE_SPECIFIC_PREFIX,
                PREF_TRANSLATE_FULL_MATCH_ONLY_PREFIX,
                PREF_SHIFT_CODEPOINT_PREFIX,

                PREF_OVERRIDE_TEXT_RETURN_PREFIX,
                PREF_SKIP_EXTRACTING_TEXT_PREFIX,
                PREF_IGNORE_EXTRACTED_TEXT_MONITOR_PREFIX,
                PREF_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT_PREFIX,
                PREF_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES_PREFIX,
                PREF_EXTRACT_FULL_TEXT_PREFIX,
                PREF_LIMIT_EXTRACT_MONITOR_TEXT_PREFIX,
                PREF_LIMIT_RETURNED_TEXT_PREFIX,

                PREF_OVERRIDE_TEXT_COMPOSITION_PREFIX,
                PREF_DELETE_THROUGH_COMPOSING_TEXT_PREFIX,
                PREF_KEEP_EMPTY_COMPOSING_POSITION_PREFIX,

                PREF_OVERRIDE_TARGET_VERSION_SIMULATION_PREFIX,
                PREF_SKIP_TAKESNAPSHOT_PREFIX,
                PREF_SKIP_GETSURROUNDINGTEXT_PREFIX,
                PREF_SKIP_PERFORMSPELLCHECK_PREFIX,
                PREF_SKIP_SETIMECONSUMESINPUT_PREFIX,
                PREF_SKIP_COMMITCONTENT_PREFIX,
                PREF_SKIP_CLOSECONNECTION_PREFIX,
                PREF_SKIP_DELETESURROUNDINGTEXTINCODEPOINTS_PREFIX,
                PREF_SKIP_REQUESTCURSORUPDATES_PREFIX,
                PREF_SKIP_COMMITCORRECTION_PREFIX,
                PREF_SKIP_GETSELECTEDTEXT_PREFIX,
                PREF_SKIP_SETCOMPOSINGREGION_PREFIX,

                PREF_OVERRIDE_SYSTEM_BEHAVIOR_SIMULATION_PREFIX,
                PREF_UPDATE_DELAY_PREFIX,
                PREF_FINISHCOMPOSINGTEXT_DELAY_PREFIX,
                PREF_GETSURROUNDINGTEXT_DELAY_PREFIX,
                PREF_GETTEXTBEFORECURSOR_DELAY_PREFIX,
                PREF_GETSELECTEDTEXT_DELAY_PREFIX,
                PREF_GETTEXTAFTERCURSOR_DELAY_PREFIX,
                PREF_GETCURSORCAPSMODE_DELAY_PREFIX,
                PREF_GETEXTRACTEDTEXT_DELAY_PREFIX
        };
        for (String prefKeyPrefix : testFieldPrefKeyPrefixes) {
            editor.remove(prefKeyPrefix + FIELD_INFIX + idToRemove);
        }
    }

    private static int[] getTestFieldIds(List<TestField> fields) {
        int[] fieldIds = new int[fields.size()];
        for (int i = 0; i < fieldIds.length; i++) {
            fieldIds[i] = fields.get(i).mId;
        }
        return fieldIds;
    }

    private static int getNextId(Collection<Integer> fieldIds) {
        int max = -1;
        for (int fieldId : fieldIds) {
            if (fieldId > max) {
                max = fieldId;
            }
        }
        return max + 1;
    }

    private static int readTestFieldInputType(final SharedPreferenceManager prefs, int fieldId) {
        String inputTypeClass = prefs.getString(
                PREF_INPUT_TYPE_CLASS_PREFIX + FIELD_INFIX + fieldId,
                "TYPE_CLASS_TEXT");
        String variation;
        int inputType;
        switch (inputTypeClass) {
            case "TYPE_NULL":
                inputType = InputType.TYPE_NULL;
                break;
            case "TYPE_CLASS_DATETIME":
                inputType = InputType.TYPE_CLASS_DATETIME;
                variation = prefs.getString(
                        PREF_INPUT_TYPE_DATETIME_VARIATION_PREFIX + FIELD_INFIX + fieldId,
                        "TYPE_DATETIME_VARIATION_NORMAL");
                switch (variation) {
                    case "TYPE_DATETIME_VARIATION_NORMAL":
                        inputType |= InputType.TYPE_DATETIME_VARIATION_NORMAL;
                        break;
                    case "TYPE_DATETIME_VARIATION_DATE":
                        inputType |= InputType.TYPE_DATETIME_VARIATION_DATE;
                        break;
                    case "TYPE_DATETIME_VARIATION_TIME":
                        inputType |= InputType.TYPE_DATETIME_VARIATION_TIME;
                        break;
                    default:
                        Log.e(TAG, "Unexpected input type datetime variation: " + variation);
                        break;
                }
                break;
            case "TYPE_CLASS_NUMBER":
                inputType = InputType.TYPE_CLASS_NUMBER;
                variation = prefs.getString(
                        PREF_INPUT_TYPE_NUMBER_VARIATION_PREFIX + FIELD_INFIX + fieldId,
                        "TYPE_NUMBER_VARIATION_NORMAL");
                switch (variation) {
                    case "TYPE_NUMBER_VARIATION_NORMAL":
                        inputType |= InputType.TYPE_NUMBER_VARIATION_NORMAL;
                        break;
                    case "TYPE_NUMBER_VARIATION_PASSWORD":
                        inputType |= InputType.TYPE_NUMBER_VARIATION_PASSWORD;
                        break;
                    default:
                        Log.e(TAG, "Unexpected input type number variation: " + variation);
                        break;
                }
                if (prefs.getBoolean(
                        PREF_INPUT_TYPE_NUMBER_FLAG_SIGNED_PREFIX + FIELD_INFIX + fieldId,
                        false)) {
                    inputType |= InputType.TYPE_NUMBER_FLAG_SIGNED;
                }
                if (prefs.getBoolean(
                        PREF_INPUT_TYPE_NUMBER_FLAG_DECIMAL_PREFIX + FIELD_INFIX + fieldId,
                        false)) {
                    inputType |= InputType.TYPE_NUMBER_FLAG_DECIMAL;
                }
                break;
            case "TYPE_CLASS_PHONE":
                inputType = InputType.TYPE_CLASS_PHONE;
                break;
            case "TYPE_CLASS_TEXT":
                inputType = InputType.TYPE_CLASS_TEXT;
                variation = prefs.getString(
                        PREF_INPUT_TYPE_TEXT_VARIATION_PREFIX + FIELD_INFIX + fieldId,
                        "TYPE_TEXT_VARIATION_NORMAL");
                switch (variation) {
                    case "TYPE_TEXT_VARIATION_NORMAL":
                        inputType |= InputType.TYPE_TEXT_VARIATION_NORMAL;
                        break;
                    case "TYPE_TEXT_VARIATION_URI":
                        inputType |= InputType.TYPE_TEXT_VARIATION_URI;
                        break;
                    case "TYPE_TEXT_VARIATION_EMAIL_ADDRESS":
                        inputType |= InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
                        break;
                    case "TYPE_TEXT_VARIATION_EMAIL_SUBJECT":
                        inputType |= InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT;
                        break;
                    case "TYPE_TEXT_VARIATION_SHORT_MESSAGE":
                        inputType |= InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE;
                        break;
                    case "TYPE_TEXT_VARIATION_LONG_MESSAGE":
                        inputType |= InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE;
                        break;
                    case "TYPE_TEXT_VARIATION_PERSON_NAME":
                        inputType |= InputType.TYPE_TEXT_VARIATION_PERSON_NAME;
                        break;
                    case "TYPE_TEXT_VARIATION_POSTAL_ADDRESS":
                        inputType |= InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS;
                        break;
                    case "TYPE_TEXT_VARIATION_PASSWORD":
                        inputType |= InputType.TYPE_TEXT_VARIATION_PASSWORD;
                        break;
                    case "TYPE_TEXT_VARIATION_VISIBLE_PASSWORD":
                        inputType |= InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
                        break;
                    case "TYPE_TEXT_VARIATION_WEB_EDIT_TEXT":
                        inputType |= InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT;
                        break;
                    case "TYPE_TEXT_VARIATION_FILTER":
                        inputType |= InputType.TYPE_TEXT_VARIATION_FILTER;
                        break;
                    case "TYPE_TEXT_VARIATION_PHONETIC":
                        inputType |= InputType.TYPE_TEXT_VARIATION_PHONETIC;
                        break;
                    case "TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS":
                        inputType |= InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS;
                        break;
                    case "TYPE_TEXT_VARIATION_WEB_PASSWORD":
                        inputType |= InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
                        break;
                    default:
                        Log.e(TAG, "Unexpected input type text variation: " + variation);
                        break;
                }
                String multiLineFlag = prefs.getString(
                        PREF_INPUT_TYPE_TEXT_FLAG_MULTI_LINE_PREFIX + FIELD_INFIX + fieldId,
                        "");
                switch (multiLineFlag) {
                    case "TYPE_TEXT_FLAG_MULTI_LINE":
                        inputType |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;
                        break;
                    case "TYPE_TEXT_FLAG_IME_MULTI_LINE":
                        inputType |= InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE;
                        break;
                }
                String capFlag = prefs.getString(
                        PREF_INPUT_TYPE_TEXT_FLAG_CAP_PREFIX + FIELD_INFIX + fieldId,
                        "");
                switch (capFlag) {
                    case "TYPE_TEXT_FLAG_CAP_CHARACTERS":
                        inputType |= InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
                        break;
                    case "TYPE_TEXT_FLAG_CAP_WORDS":
                        inputType |= InputType.TYPE_TEXT_FLAG_CAP_WORDS;
                        break;
                    case "TYPE_TEXT_FLAG_CAP_SENTENCES":
                        inputType |= InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
                        break;
                }
                if (prefs.getBoolean(
                        PREF_INPUT_TYPE_TEXT_FLAG_AUTO_COMPLETE_PREFIX + FIELD_INFIX + fieldId,
                        false)) {
                    inputType |= InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE;
                }
                if (prefs.getBoolean(
                        PREF_INPUT_TYPE_TEXT_FLAG_AUTO_CORRECT_PREFIX + FIELD_INFIX + fieldId,
                        false)) {
                    inputType |= InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
                }
                if (prefs.getBoolean(
                        PREF_INPUT_TYPE_TEXT_FLAG_NO_SUGGESTIONS_PREFIX + FIELD_INFIX + fieldId,
                        false)) {
                    inputType |= InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
                }
                break;
            default:
                Log.e(TAG, "Unexpected input type class: " + inputTypeClass);
                inputType = InputType.TYPE_CLASS_TEXT;
                break;
        }
        return inputType;
    }

    public static int getTestFieldInputType(int fieldIndex) {
        return getField(getTestFieldId(fieldIndex)).mInputType;
    }

    public static final boolean DEFAULT_NULL_INPUT_TYPE_MULTILINE = false;

    private static boolean readTestFieldNullInputTypeMultiline(final SharedPreferenceManager prefs,
                                                               int fieldId) {
        return prefs.getBoolean(PREF_NULL_INPUT_TYPE_MULTILINE_PREFIX + FIELD_INFIX + fieldId,
                DEFAULT_NULL_INPUT_TYPE_MULTILINE);
    }

    public static boolean getTestFieldNullInputTypeMultiline(int fieldIndex) {
        return getField(getTestFieldId(fieldIndex)).mNullInputTypeMultiline;
    }

    public static boolean defaultCreateInputConnection(int inputType) {
        return inputType != EditorInfo.TYPE_NULL;
    }

    private static boolean readTestFieldCreateInputConnection(final SharedPreferenceManager prefs,
                                                              int fieldId, int inputType) {
        // this setting only applies to null input types since as far as I can tell, the others are
        // expected to create the input connection to fully support rich input
        if (inputType != EditorInfo.TYPE_NULL) {
            return defaultCreateInputConnection(inputType);
        }
        return prefs.getBoolean(PREF_CREATE_INPUT_CONNECTION_PREFIX + FIELD_INFIX + fieldId,
                defaultCreateInputConnection(inputType));
    }

    public static boolean getTestFieldCreateInputConnection(int fieldIndex) {
        return getField(getTestFieldId(fieldIndex)).mCreateInputConnection;
    }

    public static boolean defaultSendSelectionInfo(int inputType) {
        return inputType != EditorInfo.TYPE_NULL;
    }

    private static boolean readTestFieldSendSelectionInfo(final SharedPreferenceManager prefs,
                                                          int fieldId, int inputType) {
        // this setting only applies to null input types since as far as I can tell, the others are
        // expected to send selection info (possibly based on the same understanding for them
        // needing to create an input connection)
        if (inputType != EditorInfo.TYPE_NULL) {
            return defaultCreateInputConnection(inputType);
        }
        return prefs.getBoolean(PREF_SEND_SELECTION_INFO_PREFIX + FIELD_INFIX + fieldId,
                defaultCreateInputConnection(inputType));
    }

    public static boolean getTestFieldSendSelectionInfo(int fieldIndex) {
        return getField(getTestFieldId(fieldIndex)).mSendSelectionInfo;
    }

    public static boolean defaultSendText(int inputType) {
        return inputType != EditorInfo.TYPE_NULL;
    }

    private static boolean readTestFieldSendText(final SharedPreferenceManager prefs, int fieldId,
                                                 int inputType) {
        // this setting only applies to null input types since as far as I can tell, the others are
        // expected to return text as part of fully supporting rich input
        if (inputType != EditorInfo.TYPE_NULL) {
            return defaultSendText(inputType);
        }
        return prefs.getBoolean(PREF_SEND_TEXT_PREFIX + FIELD_INFIX + fieldId,
                defaultSendText(inputType));
    }

    public static boolean getTestFieldSendText(int fieldIndex) {
        return getField(getTestFieldId(fieldIndex)).mSendText;
    }

    public static final int COMPOSING_TEXT_BEHAVIOR_INVISIBLE = 0;
    public static final int COMPOSING_TEXT_BEHAVIOR_COMPOSE = 1;
    public static final int COMPOSING_TEXT_BEHAVIOR_COMMIT = 2;
    public static final int COMPOSING_TEXT_BEHAVIOR_IGNORE = 3;

    public static int defaultComposingTextBehavior(int inputType) {
        return inputType != EditorInfo.TYPE_NULL
                ? COMPOSING_TEXT_BEHAVIOR_COMPOSE
                : COMPOSING_TEXT_BEHAVIOR_INVISIBLE;
    }

    private static int readTestFieldComposingTextBehavior(final SharedPreferenceManager prefs,
                                                          int fieldId, int inputType,
                                                          boolean createInputConnection) {
        // composition (or custom management) is only possible if an input connection is created
        if (!createInputConnection) {
            return COMPOSING_TEXT_BEHAVIOR_INVISIBLE;
        }
        // this setting only applies to null input types since as far as I can tell, the others are
        // expected to support all of the rich editing specified in documentation for
        // InputConnection (that isn't noted as being optional)
        if (inputType != EditorInfo.TYPE_NULL) {
            return defaultComposingTextBehavior(inputType);
        }
        String behavior =
                prefs.getString(PREF_COMPOSING_TEXT_BEHAVIOR_PREFIX + FIELD_INFIX + fieldId, "");
        switch (behavior) {
            case "INVISIBLE":
                return COMPOSING_TEXT_BEHAVIOR_INVISIBLE;
            case "COMPOSE":
                return COMPOSING_TEXT_BEHAVIOR_COMPOSE;
            case "COMMIT":
                return COMPOSING_TEXT_BEHAVIOR_COMMIT;
            case "IGNORE":
                return COMPOSING_TEXT_BEHAVIOR_IGNORE;
            default:
                return defaultComposingTextBehavior(inputType);
        }
    }

    public static int getTestFieldComposingTextBehavior(int fieldIndex) {
        return getField(getTestFieldId(fieldIndex)).mComposingTextBehavior;
    }

    public static boolean defaultAllowDeleteSurroundingText(int inputType) {
        return inputType != EditorInfo.TYPE_NULL;
    }

    private static boolean readTestFieldAllowDeleteSurroundingText(
            final SharedPreferenceManager prefs, int fieldId, int inputType,
            boolean createInputConnection) {
        // composition is only possible if an input connection is created
        if (!createInputConnection) {
            return false;
        }
        // this setting only applies to null input types since as far as I can tell, the others are
        // expected to support all of the rich editing specified in documentation for
        // InputConnection (that isn't noted as being optional)
        if (inputType != EditorInfo.TYPE_NULL) {
            return defaultAllowDeleteSurroundingText(inputType);
        }
        return prefs.getBoolean(PREF_ALLOW_DELETE_SURROUNDING_TEXT_PREFIX + FIELD_INFIX + fieldId,
                defaultAllowDeleteSurroundingText(inputType));
    }

    public static boolean getTestFieldAllowDeleteSurroundingText(int fieldIndex) {
        return getField(getTestFieldId(fieldIndex)).mAllowDeleteSurroundingText;
    }

    public static boolean defaultAllowSettingSelection(int inputType) {
        return inputType != EditorInfo.TYPE_NULL;
    }

    private static boolean readTestFieldAllowSettingSelection(final SharedPreferenceManager prefs,
                                                              int fieldId, int inputType,
                                                              boolean createInputConnection) {
        // setting the selection position is only possible if an input connection is created
        if (!createInputConnection) {
            return false;
        }
        // this setting only applies to null input types since as far as I can tell, the others are
        // expected to support all of the rich editing specified in documentation for
        // InputConnection (that isn't noted as being optional)
        if (inputType != EditorInfo.TYPE_NULL) {
            return defaultAllowSettingSelection(inputType);
        }
        return prefs.getBoolean(PREF_ALLOW_SETTING_SELECTION_PREFIX + FIELD_INFIX + fieldId,
                defaultAllowSettingSelection(inputType));
    }

    public static boolean getTestFieldAllowSettingSelection(int fieldIndex) {
        return getField(getTestFieldId(fieldIndex)).mAllowSettingSelection;
    }

    private static int readTestFieldImeOptions(final SharedPreferenceManager prefs, int fieldId) {
        String imeOptionsAction = prefs.getString(
                PREF_IME_OPTIONS_ACTION_PREFIX + FIELD_INFIX + fieldId,
                "IME_ACTION_UNSPECIFIED");
        int imeOptions;
        switch (imeOptionsAction) {
            case "IME_ACTION_UNSPECIFIED":
                imeOptions = EditorInfo.IME_NULL;
                break;
            case "IME_ACTION_NONE":
                imeOptions = EditorInfo.IME_ACTION_NONE;
                break;
            case "IME_ACTION_GO":
                imeOptions = EditorInfo.IME_ACTION_GO;
                break;
            case "IME_ACTION_SEARCH":
                imeOptions = EditorInfo.IME_ACTION_SEARCH;
                break;
            case "IME_ACTION_SEND":
                imeOptions = EditorInfo.IME_ACTION_SEND;
                break;
            case "IME_ACTION_NEXT":
                imeOptions = EditorInfo.IME_ACTION_NEXT;
                break;
            case "IME_ACTION_DONE":
                imeOptions = EditorInfo.IME_ACTION_DONE;
                break;
            case "IME_ACTION_PREVIOUS":
                imeOptions = EditorInfo.IME_ACTION_PREVIOUS;
                break;
            default:
                Log.e(TAG, "Unexpected IME options action: " + imeOptionsAction);
                imeOptions = EditorInfo.IME_NULL;
                break;
        }
        if (prefs.getBoolean(PREF_IME_OPTIONS_FLAG_FORCE_ASCII_PREFIX + FIELD_INFIX + fieldId,
                false)) {
            imeOptions |=  EditorInfo.IME_FLAG_FORCE_ASCII;
        }
        if (prefs.getBoolean(PREF_IME_OPTIONS_FLAG_NAVIGATE_NEXT_PREFIX + FIELD_INFIX + fieldId,
                false)) {
            imeOptions |=  EditorInfo.IME_FLAG_NAVIGATE_NEXT;
        }
        if (prefs.getBoolean(PREF_IME_OPTIONS_FLAG_NAVIGATE_PREVIOUS_PREFIX + FIELD_INFIX + fieldId,
                false)) {
            imeOptions |=  EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS;
        }
        if (prefs.getBoolean(
                PREF_IME_OPTIONS_FLAG_NO_ACCESSORY_ACTION_PREFIX + FIELD_INFIX + fieldId,
                false)) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_ACCESSORY_ACTION;
        }
        if (prefs.getBoolean(PREF_IME_OPTIONS_FLAG_NO_ENTER_ACTION_PREFIX + FIELD_INFIX + fieldId,
                false)) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        }
        if (prefs.getBoolean(PREF_IME_OPTIONS_FLAG_NO_EXTRACT_UI_PREFIX + FIELD_INFIX + fieldId,
                false)) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        }
        if (prefs.getBoolean(PREF_IME_OPTIONS_FLAG_NO_FULLSCREEN_PREFIX + FIELD_INFIX + fieldId,
                false)) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_FULLSCREEN;
        }
        if (prefs.getBoolean(
                PREF_IME_OPTIONS_FLAG_NO_PERSONALIZED_LEARNING_PREFIX + FIELD_INFIX + fieldId,
                false)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        }
        return imeOptions;
    }

    public static int getTestFieldImeOptions(int fieldIndex) {
        return getField(getTestFieldId(fieldIndex)).mImeOptions;
    }

    private static int readTestFieldImeActionId(final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getInt(PREF_IME_ACTION_ID_PREFIX + FIELD_INFIX + fieldId, 0);
    }

    public static int getTestFieldImeActionId(int fieldIndex) {
        return getField(getTestFieldId(fieldIndex)).mImeActionId;
    }

    private static String readTestFieldImeActionLabel(final SharedPreferenceManager prefs,
                                                      int fieldId) {
        return prefs.getString(PREF_IME_ACTION_LABEL_PREFIX + FIELD_INFIX + fieldId, null);
    }

    public static String getTestFieldImeActionLabel(int fieldIndex) {
        return getField(getTestFieldId(fieldIndex)).mImeActionLabel;
    }

    private static String readTestFieldPrivateImeOptions(final SharedPreferenceManager prefs,
                                                         int fieldId) {
        return prefs.getString(PREF_PRIVATE_IME_OPTIONS_PREFIX + FIELD_INFIX + fieldId, null);
    }

    public static String getTestFieldPrivateImeOptions(int fieldIndex) {
        return getField(getTestFieldId(fieldIndex)).mPrivateImeOptions;
    }

    private static boolean readTestFieldSelectAllOnFocus(final SharedPreferenceManager prefs,
                                                         int fieldId) {
        return prefs.getBoolean(PREF_SELECT_ALL_ON_FOCUS_PREFIX + FIELD_INFIX + fieldId, false);
    }

    public static boolean shouldTestFieldSelectAllOnFocus(int fieldIndex) {
        return getField(getTestFieldId(fieldIndex)).mSelectAllOnFocus;
    }

    private static int readTestFieldMaxLength(final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getInt(PREF_MAX_LENGTH_PREFIX + FIELD_INFIX + fieldId, -1);
    }

    public static int getTestFieldMaxLength(int fieldIndex) {
        return getField(getTestFieldId(fieldIndex)).mMaxLength;
    }

    private static boolean readTestFieldAllowUndo(final SharedPreferenceManager prefs,
                                                  int fieldId) {
        return prefs.getBoolean(PREF_ALLOW_UNDO_PREFIX + FIELD_INFIX + fieldId, true);
    }

    public static boolean shouldTestFieldAllowUndo(int fieldIndex) {
        return getField(getTestFieldId(fieldIndex)).mAllowUndo;
    }

    private static Locale[] readTestFieldTextLocales(final SharedPreferenceManager prefs,
                                                     int fieldId) {
        return (new LocaleEntryListPreference.Reader(prefs,
                PREF_TEXT_LOCALES_PREFIX + FIELD_INFIX + fieldId)).readValue();
    }

    public static Locale[] getTestFieldTextLocales(int fieldIndex) {
        return getField(getTestFieldId(fieldIndex)).mTextLocales;
    }

    private static Locale[] readTestFieldImeHintLocales(final SharedPreferenceManager prefs,
                                                        int fieldId) {
        return (new LocaleEntryListPreference.Reader(prefs,
                PREF_IME_HINT_LOCALES_PREFIX + FIELD_INFIX + fieldId)).readValue();
    }

    public static Locale[] getTestFieldImeHintLocales(int fieldIndex) {
        return getField(getTestFieldId(fieldIndex)).mImeHintLocales;
    }

    private static CharSequence readTestFieldDefaultText(final SharedPreferenceManager prefs,
                                                         int fieldId) {
        return prefs.getCharSequence(PREF_IME_DEFAULT_TEXT_PREFIX + FIELD_INFIX + fieldId, null);
    }

    public static CharSequence getTestFieldDefaultText(int fieldIndex) {
        return getField(getTestFieldId(fieldIndex)).mDefaultText;
    }

    private static CharSequence readTestFieldHintText(final SharedPreferenceManager prefs,
                                                      int fieldId) {
        return prefs.getCharSequence(PREF_IME_HINT_TEXT_PREFIX + FIELD_INFIX + fieldId, null);
    }

    public static CharSequence getTestFieldHintText(int fieldIndex) {
        return getField(getTestFieldId(fieldIndex)).mHintText;
    }

    public static class TestGroup {
        public String mName;
        public int[] mFieldIds;
        public TestGroup(String name, int[] fieldIds) {
            mName = name;
            mFieldIds = fieldIds;
        }

        @Override
        public String toString() {
            return "{ mName=" + (mName == null ? "null" : "\"" + mName + "\"")
                    + ", mFieldIds=" + Arrays.toString(mFieldIds) + " }";
        }

        public TestGroup copy() {
            return new TestGroup(mName, deepCopy(mFieldIds));
        }
    }

    private static int[] deepCopy(int[] array) {
        int[] copy = new int[array.length];
        System.arraycopy(array, 0, copy, 0, array.length);
        return copy;
    }

    private static class TestField extends AppLevelDefaults {
        private final int mId;

        private int mInputType;
        private boolean mNullInputTypeMultiline;
        private boolean mCreateInputConnection;
        private boolean mSendSelectionInfo;
        private boolean mSendText;
        private int mComposingTextBehavior;
        private boolean mAllowDeleteSurroundingText;
        private boolean mAllowSettingSelection;
        private int mImeOptions;
        private int mImeActionId;
        private String mImeActionLabel;
        private String mPrivateImeOptions;
        private boolean mSelectAllOnFocus;
        private int mMaxLength;
        private boolean mAllowUndo;
        private Locale[] mTextLocales;
        private Locale[] mImeHintLocales;
        private CharSequence mDefaultText;
        private CharSequence mHintText;

        private boolean mOverrideTextInputModification;
        private boolean mOverrideTextReturn;
        private boolean mOverrideTextComposition;
        private boolean mOverrideTargetVersion;
        private boolean mOverrideSystemBehavior;

        public TestField(int id) {
            mId = id;
        }
    }

    private static class AppLevelDefaults {
        boolean mModifyCommittedText;
        boolean mModifyComposedText;
        boolean mConsiderComposedChangesFromEnd;
        boolean mModifyComposedChangesOnly;
        boolean mRestrictToInclude;
        String[] mRestrictSpecific;
        IntRange mRestrictRange;
        TranslateText[] mTranslateSpecific;
        boolean mTranslateFullMatchOnly;
        int mShiftCodepoint;
        boolean mSkipExtractingText;
        boolean mIgnoreExtractedTextMonitor;
        boolean mUpdateSelectionBeforeExtractedText;
        boolean mUpdateExtractedTextOnlyOnNetChanges;
        boolean mExtractFullText;
        int mExtractMonitorTextLimit;
        int mReturnedTextLimit;
        boolean mDeleteThroughComposingText;
        boolean mKeepEmptyComposingPosition;
        boolean mSkipTakeSnapshot;
        boolean mSkipGetSurroundingText;
        boolean mSkipPerformSpellCheck;
        boolean mSkipSetImeConsumesInput;
        boolean mSkipCommitContent;
        boolean mSkipCloseConnection;
        boolean mSkipDeleteSurroundingTextInCodePoints;
        boolean mSkipRequestCursorUpdates;
        boolean mSkipCommitCorrection;
        boolean mSkipGetSelectedText;
        boolean mSkipSetComposingRegion;
        int mUpdateDelay;
        int mFinishComposingTextDelay;
        int mGetSurroundingTextDelay;
        int mGetTextBeforeCursorDelay;
        int mGetSelectedTextDelay;
        int mGetTextAfterCursorDelay;
        int mGetCursorCapsModeDelay;
        int mGetExtractedTextDelay;
    }

    public static EditorSettings getTestFieldSettings(int fieldIndex) {
        return new FieldPrefEditorSettings(fieldIndex);
    }

    public interface EditorSettings {
        boolean nullInputTypeMultiline();
        boolean shouldCreateInputConnection();
        boolean shouldSendSelectionInfo();
        boolean shouldSendText();
        int composingTextBehavior();
        boolean allowDeleteSurroundingText();
        boolean allowSettingSelection();

        boolean shouldModifyCommittedText();
        boolean shouldModifyComposedText();
        boolean shouldModifyComposedChangesOnly();
        boolean shouldConsiderComposedChangesFromEnd();
        boolean shouldRestrictToInclude();
        String[] getRestrictSpecific();
        @Nullable IntRange getRestrictRange();
        TranslateText[] getTranslateSpecific();
        boolean shouldTranslateFullMatchOnly();
        int getCodepointShift();

        boolean shouldSkipExtractingText();
        boolean shouldIgnoreExtractedTextMonitor();
        boolean shouldUpdateSelectionBeforeExtractedText();
        boolean shouldUpdateExtractedTextOnlyOnNetChanges();
        boolean shouldExtractFullText();
        int getExtractMonitorTextLimit();
        int getReturnedTextLimit();

        boolean shouldDeleteThroughComposingText();
        boolean shouldKeepEmptyComposingPosition();

        boolean shouldSkipTakeSnapshot();
        boolean shouldSkipGetSurroundingText();
        boolean shouldSkipPerformSpellCheck();
        boolean shouldSkipSetImeConsumesInput();
        boolean shouldSkipCommitContent();
        boolean shouldSkipCloseConnection();
        boolean shouldSkipDeleteSurroundingTextInCodePoints();
        boolean shouldSkipRequestCursorUpdates();
        boolean shouldSkipCommitCorrection();
        boolean shouldSkipGetSelectedText();
        boolean shouldSkipSetComposingRegion();

        int getUpdateDelay();
        int getFinishComposingTextDelay();
        int getGetSurroundingTextDelay();
        int getGetTextBeforeCursorDelay();
        int getGetSelectedTextDelay();
        int getGetTextAfterCursorDelay();
        int getGetCursorCapsModeDelay();
        int getGetExtractedTextDelay();
    }

    public static class FieldPrefEditorSettings implements EditorSettings {
        private final int mIndex;

        private FieldPrefEditorSettings(int fieldIndex) {
            mIndex = fieldIndex;
        }

        @Override
        public boolean nullInputTypeMultiline() {
            return Settings.getTestFieldNullInputTypeMultiline(mIndex);
        }

        @Override
        public boolean shouldCreateInputConnection() {
            return Settings.getTestFieldCreateInputConnection(mIndex);
        }

        @Override
        public boolean shouldSendSelectionInfo() {
            return Settings.getTestFieldSendSelectionInfo(mIndex);
        }

        @Override
        public boolean shouldSendText() {
            return Settings.getTestFieldSendText(mIndex);
        }

        @Override
        public int composingTextBehavior() {
            return Settings.getTestFieldComposingTextBehavior(mIndex);
        }

        @Override
        public boolean allowDeleteSurroundingText() {
            return Settings.getTestFieldAllowDeleteSurroundingText(mIndex);
        }

        @Override
        public boolean allowSettingSelection() {
            return Settings.getTestFieldAllowSettingSelection(mIndex);
        }

        @Override
        public boolean shouldModifyCommittedText() {
            return Settings.shouldModifyCommittedText(mIndex);
        }

        @Override
        public boolean shouldModifyComposedText() {
            return Settings.shouldModifyComposedText(mIndex);
        }

        @Override
        public boolean shouldModifyComposedChangesOnly() {
            return Settings.shouldModifyComposedChangesOnly(mIndex);
        }

        @Override
        public boolean shouldConsiderComposedChangesFromEnd() {
            return Settings.shouldConsiderComposedChangesFromEnd(mIndex);
        }

        @Override
        public boolean shouldRestrictToInclude() {
            return Settings.shouldRestrictToInclude(mIndex);
        }

        @Override
        public String[] getRestrictSpecific() {
            return Settings.getRestrictSpecific(mIndex);
        }

        @Override
        public @Nullable IntRange getRestrictRange() {
            return Settings.getRestrictRange(mIndex);
        }

        @Override
        public TranslateText[] getTranslateSpecific() {
            return Settings.getTranslateSpecific(mIndex);
        }

        @Override
        public boolean shouldTranslateFullMatchOnly() {
            return Settings.shouldTranslateFullMatchOnly(mIndex);
        }

        @Override
        public int getCodepointShift() {
            return Settings.getShiftCodepoint(mIndex);
        }

        @Override
        public boolean shouldSkipExtractingText() {
            return Settings.shouldSkipExtractingText(mIndex);
        }

        @Override
        public boolean shouldIgnoreExtractedTextMonitor() {
            return Settings.shouldIgnoreExtractedTextMonitor(mIndex);
        }

        @Override
        public boolean shouldUpdateSelectionBeforeExtractedText() {
            return Settings.shouldUpdateSelectionBeforeExtractedText(mIndex);
        }

        @Override
        public boolean shouldUpdateExtractedTextOnlyOnNetChanges() {
            return Settings.shouldUpdateExtractedTextOnlyOnNetChanges(mIndex);
        }

        @Override
        public boolean shouldExtractFullText() {
            return Settings.shouldExtractFullText(mIndex);
        }

        @Override
        public int getExtractMonitorTextLimit() {
            return Settings.getExtractMonitorTextLimit(mIndex);
        }

        @Override
        public int getReturnedTextLimit() {
            return Settings.getReturnedTextLimit(mIndex);
        }

        @Override
        public boolean shouldDeleteThroughComposingText() {
            return Settings.shouldDeleteThroughComposingText(mIndex);
        }

        @Override
        public boolean shouldKeepEmptyComposingPosition() {
            return Settings.shouldKeepEmptyComposingPosition(mIndex);
        }

        @Override
        public boolean shouldSkipTakeSnapshot() {
            return Settings.shouldSkipTakeSnapshot(mIndex);
        }

        @Override
        public boolean shouldSkipGetSurroundingText() {
            return Settings.shouldSkipGetSurroundingText(mIndex);
        }

        @Override
        public boolean shouldSkipPerformSpellCheck() {
            return Settings.shouldSkipPerformSpellCheck(mIndex);
        }

        @Override
        public boolean shouldSkipSetImeConsumesInput() {
            return Settings.shouldSkipSetImeConsumesInput(mIndex);
        }

        @Override
        public boolean shouldSkipCommitContent() {
            return Settings.shouldSkipCommitContent(mIndex);
        }

        @Override
        public boolean shouldSkipCloseConnection() {
            return Settings.shouldSkipCloseConnection(mIndex);
        }

        @Override
        public boolean shouldSkipDeleteSurroundingTextInCodePoints() {
            return Settings.shouldSkipDeleteSurroundingTextInCodePoints(mIndex);
        }

        @Override
        public boolean shouldSkipRequestCursorUpdates() {
            return Settings.shouldSkipRequestCursorUpdates(mIndex);
        }

        @Override
        public boolean shouldSkipCommitCorrection() {
            return Settings.shouldSkipCommitCorrection(mIndex);
        }

        @Override
        public boolean shouldSkipGetSelectedText() {
            return Settings.shouldSkipGetSelectedText(mIndex);
        }

        @Override
        public boolean shouldSkipSetComposingRegion() {
            return Settings.shouldSkipSetComposingRegion(mIndex);
        }

        @Override
        public int getUpdateDelay() {
            return Settings.getUpdateDelay(mIndex);
        }

        @Override
        public int getFinishComposingTextDelay() {
            return Settings.getFinishComposingTextDelay(mIndex);
        }

        @Override
        public int getGetSurroundingTextDelay() {
            return Settings.getGetSurroundingTextDelay(mIndex);
        }

        @Override
        public int getGetTextBeforeCursorDelay() {
            return Settings.getGetTextBeforeCursorDelay(mIndex);
        }

        @Override
        public int getGetSelectedTextDelay() {
            return Settings.getGetSelectedTextDelay(mIndex);
        }

        @Override
        public int getGetTextAfterCursorDelay() {
            return Settings.getGetTextAfterCursorDelay(mIndex);
        }

        @Override
        public int getGetCursorCapsModeDelay() {
            return Settings.getGetCursorCapsModeDelay(mIndex);
        }

        @Override
        public int getGetExtractedTextDelay() {
            return Settings.getGetExtractedTextDelay(mIndex);
        }
    }

    // copied from java.util.function.Predicate to support older versions because that requires
    // API level 24
    /**
     * Represents a predicate (boolean-valued function) of one argument.
     * @param <T> the type of the input to the predicate
     */
    public interface Predicate<T> {
        /**
         * Evaluates this predicate on the given argument.
         * @param t the input argument
         * @return {@code true} if the input argument matches the predicate,
         * otherwise {@code false}
         */
        boolean test(T t);
    }
}
