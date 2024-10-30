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
import android.content.res.Configuration;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.aosp.internal.util.ArrayUtils;
import com.wittmane.testingedittext.function.Predicate;
import com.wittmane.testingedittext.settings.SharedPreferenceManager.Editor;
import com.wittmane.testingedittext.settings.preferences.CodepointRangeDialogPreference;
import com.wittmane.testingedittext.settings.preferences.LocaleEntryListPreference;
import com.wittmane.testingedittext.settings.preferences.TextListPreference;
import com.wittmane.testingedittext.settings.preferences.TextTranslateListPreference;
import com.wittmane.testingedittext.util.IterableUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class Settings implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = Settings.class.getSimpleName();

    private static final boolean LIST_PREFS = false;

    public static final int BASE_GROUP_INDEX = -1;
    public static final int BASE_FIELD_INDEX = -1;
    public static final int BASE_FIELD_ID = -1;


    private static final String PREF_KEY_PREFIX = "pref_key_";
    public static final String BASE_SUFFIX = "_base";
    public static final String GROUP_INFIX = "_group_";
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
    public static final String PREF_UPDATE_DELAY_PREFIX =
            "pref_key_update_delay";
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

    public static final String PREF_TEST_GROUP_IDS =
            "pref_key_test_group_ids";

    public static final String PREF_TEST_FIELD_IDS_PREFIX =
            "pref_key_test_field_ids";
    public static final String PREF_TEST_GROUP_NAME_PREFIX =
            "pref_key_test_group_name";

    public static final String PREF_IME_LABEL_TEXT_PREFIX =
            "pref_key_label_text";
    public static final String PREF_IME_DEFAULT_TEXT_PREFIX =
            "pref_key_default_text";
    public static final String PREF_IME_HINT_TEXT_PREFIX =
            "pref_key_hint_text";
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
    public static final String PREF_NULL_INPUT_TYPE_CREATE_INPUT_CONNECTION_PREFIX =
            "pref_key_null_input_type_create_input_connection";
    public static final String PREF_NULL_INPUT_TYPE_SEND_SELECTION_INFO_PREFIX =
            "pref_key_null_input_type_send_selection_info";
    public static final String PREF_NULL_INPUT_TYPE_SEND_TEXT_PREFIX =
            "pref_key_null_input_type_send_text";
    public static final String PREF_NULL_INPUT_TYPE_COMPOSING_TEXT_BEHAVIOR_PREFIX =
            "pref_key_null_input_type_composing_text_behavior";
    public static final String PREF_NULL_INPUT_TYPE_ALLOW_DELETE_SURROUNDING_TEXT_PREFIX =
            "pref_key_null_input_type_allow_delete_surrounding_text";
    public static final String PREF_NULL_INPUT_TYPE_ALLOW_SETTING_SELECTION_PREFIX =
            "pref_key_null_input_type_allow_setting_selection";
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

    public static final String PREF_THEME =
            "pref_key_theme";
    public static final String PREF_SHOW_REFERENCE_EDITTEXT =
            "pref_key_show_reference_edittext";

    private static final String[] TEST_FIELD_PREF_KEY_PREFIXES = new String[] {
            PREF_IME_LABEL_TEXT_PREFIX,
            PREF_IME_DEFAULT_TEXT_PREFIX,
            PREF_IME_HINT_TEXT_PREFIX,
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
            PREF_NULL_INPUT_TYPE_CREATE_INPUT_CONNECTION_PREFIX,
            PREF_NULL_INPUT_TYPE_SEND_SELECTION_INFO_PREFIX,
            PREF_NULL_INPUT_TYPE_SEND_TEXT_PREFIX,
            PREF_NULL_INPUT_TYPE_COMPOSING_TEXT_BEHAVIOR_PREFIX,
            PREF_NULL_INPUT_TYPE_ALLOW_DELETE_SURROUNDING_TEXT_PREFIX,
            PREF_NULL_INPUT_TYPE_ALLOW_SETTING_SELECTION_PREFIX,
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

            PREF_OVERRIDE_TEXT_INPUT_MODIFICATION_PREFIX,
            PREF_OVERRIDE_TEXT_RETURN_PREFIX,
            PREF_OVERRIDE_TEXT_COMPOSITION_PREFIX,
            PREF_OVERRIDE_TARGET_VERSION_SIMULATION_PREFIX,
            PREF_OVERRIDE_SYSTEM_BEHAVIOR_SIMULATION_PREFIX,
    };

    private static final String[] TEXT_INPUT_MODIFICATION_PREF_KEY_PREFIXES = new String[] {
            PREF_MODIFY_COMMITTED_TEXT_PREFIX,
            PREF_MODIFY_COMPOSED_TEXT_PREFIX,
            PREF_MODIFY_COMPOSED_CHANGES_ONLY_PREFIX,
            PREF_CONSIDER_COMPOSED_CHANGES_FROM_END_PREFIX,
            PREF_RESTRICT_TO_INCLUDE_PREFIX,
            PREF_RESTRICT_SPECIFIC_PREFIX,
            PREF_RESTRICT_RANGE_PREFIX,
            PREF_TRANSLATE_SPECIFIC_PREFIX,
            PREF_TRANSLATE_FULL_MATCH_ONLY_PREFIX,
            PREF_SHIFT_CODEPOINT_PREFIX
    };

    private static final String[] TEXT_RETURN_PREF_KEY_PREFIXES = new String[] {
            PREF_SKIP_EXTRACTING_TEXT_PREFIX,
            PREF_IGNORE_EXTRACTED_TEXT_MONITOR_PREFIX,
            PREF_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT_PREFIX,
            PREF_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES_PREFIX,
            PREF_EXTRACT_FULL_TEXT_PREFIX,
            PREF_LIMIT_EXTRACT_MONITOR_TEXT_PREFIX,
            PREF_LIMIT_RETURNED_TEXT_PREFIX
    };

    private static final String[] TEXT_COMPOSITION_PREF_KEY_PREFIXES = new String[] {
            PREF_DELETE_THROUGH_COMPOSING_TEXT_PREFIX,
            PREF_KEEP_EMPTY_COMPOSING_POSITION_PREFIX
    };

    private static final String[] TARGET_VERSION_SIMULATION_PREF_KEY_PREFIXES = new String[] {
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
            PREF_SKIP_SETCOMPOSINGREGION_PREFIX
    };

    private static final String[] SYSTEM_BEHAVIOR_SIMULATION_PREF_KEY_PREFIXES = new String[] {
            PREF_UPDATE_DELAY_PREFIX,
            PREF_FINISHCOMPOSINGTEXT_DELAY_PREFIX,
            PREF_GETSURROUNDINGTEXT_DELAY_PREFIX,
            PREF_GETTEXTBEFORECURSOR_DELAY_PREFIX,
            PREF_GETSELECTEDTEXT_DELAY_PREFIX,
            PREF_GETTEXTAFTERCURSOR_DELAY_PREFIX,
            PREF_GETCURSORCAPSMODE_DELAY_PREFIX,
            PREF_GETEXTRACTEDTEXT_DELAY_PREFIX
    };

    private static final String[] DEFAULTABLE_TEST_FIELD_PREF_KEY_PREFIXES = ArrayUtils.join(
            TEXT_INPUT_MODIFICATION_PREF_KEY_PREFIXES,
            TEXT_RETURN_PREF_KEY_PREFIXES,
            TEXT_COMPOSITION_PREF_KEY_PREFIXES,
            TARGET_VERSION_SIMULATION_PREF_KEY_PREFIXES,
            SYSTEM_BEHAVIOR_SIMULATION_PREF_KEY_PREFIXES
    );

    private static final Map<String, String[]> DEFAULT_OVERRIDE_PREF_PREFIX_MAP = new HashMap<>();
    static {
        DEFAULT_OVERRIDE_PREF_PREFIX_MAP.put(PREF_OVERRIDE_TEXT_INPUT_MODIFICATION_PREFIX,
                TEXT_INPUT_MODIFICATION_PREF_KEY_PREFIXES);
        DEFAULT_OVERRIDE_PREF_PREFIX_MAP.put(PREF_OVERRIDE_TEXT_RETURN_PREFIX,
                TEXT_RETURN_PREF_KEY_PREFIXES);
        DEFAULT_OVERRIDE_PREF_PREFIX_MAP.put(PREF_OVERRIDE_TEXT_COMPOSITION_PREFIX,
                TEXT_COMPOSITION_PREF_KEY_PREFIXES);
        DEFAULT_OVERRIDE_PREF_PREFIX_MAP.put(PREF_OVERRIDE_TARGET_VERSION_SIMULATION_PREFIX,
                TARGET_VERSION_SIMULATION_PREF_KEY_PREFIXES);
        DEFAULT_OVERRIDE_PREF_PREFIX_MAP.put(PREF_OVERRIDE_SYSTEM_BEHAVIOR_SIMULATION_PREFIX,
                SYSTEM_BEHAVIOR_SIMULATION_PREF_KEY_PREFIXES);
    }

    private static final String[] TEST_GROUP_PREF_KEY_PREFIXES = new String[] {
            PREF_TEST_GROUP_NAME_PREFIX,
            PREF_TEST_FIELD_IDS_PREFIX
    };

    private static final String[] MISC_PREF_KEYS = new String[] {
            PREF_THEME,
            PREF_SHOW_REFERENCE_EDITTEXT
    };

    private static final int TYPE_UNKNOWN = 0;
    private static final int TYPE_BOOLEAN = 1;
    private static final int TYPE_INT = 2;
    private static final int TYPE_LONG = 3;
    private static final int TYPE_FLOAT = 4;
    private static final int TYPE_STRING = 5;
    private static final int TYPE_SPANNED = 6;
    private static final int TYPE_CHAR_SEQUENCE = 7;
    private static final int TYPE_INT_ARRAY = 8;
    private static final int TYPE_STRING_ARRAY = 9;
    private static final int TYPE_STRING_SET = 10;
    private static final int TYPE_INT_RANGE = 11;
    private static final int TYPE_LOCALE_ARRAY = 12;
    private static final int TYPE_TEXT_LIST_STRING = 13;
    private static final int TYPE_TEXT_LIST_TRANSLATE_TEXT = 14;

    private static int prefDataType(String keyOrPrefix) {
        switch (keyOrPrefix) {
            case PREF_OVERRIDE_TEXT_INPUT_MODIFICATION_PREFIX:
            case PREF_MODIFY_COMMITTED_TEXT_PREFIX:
            case PREF_MODIFY_COMPOSED_TEXT_PREFIX:
            case PREF_MODIFY_COMPOSED_CHANGES_ONLY_PREFIX:
            case PREF_CONSIDER_COMPOSED_CHANGES_FROM_END_PREFIX:
            case PREF_RESTRICT_TO_INCLUDE_PREFIX:
            case PREF_TRANSLATE_FULL_MATCH_ONLY_PREFIX:
            case PREF_OVERRIDE_TEXT_RETURN_PREFIX:
            case PREF_SKIP_EXTRACTING_TEXT_PREFIX:
            case PREF_IGNORE_EXTRACTED_TEXT_MONITOR_PREFIX:
            case PREF_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT_PREFIX:
            case PREF_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES_PREFIX:
            case PREF_EXTRACT_FULL_TEXT_PREFIX:
            case PREF_OVERRIDE_TEXT_COMPOSITION_PREFIX:
            case PREF_DELETE_THROUGH_COMPOSING_TEXT_PREFIX:
            case PREF_KEEP_EMPTY_COMPOSING_POSITION_PREFIX:
            case PREF_OVERRIDE_TARGET_VERSION_SIMULATION_PREFIX:
            case PREF_SKIP_TAKESNAPSHOT_PREFIX:
            case PREF_SKIP_GETSURROUNDINGTEXT_PREFIX:
            case PREF_SKIP_PERFORMSPELLCHECK_PREFIX:
            case PREF_SKIP_SETIMECONSUMESINPUT_PREFIX:
            case PREF_SKIP_COMMITCONTENT_PREFIX:
            case PREF_SKIP_CLOSECONNECTION_PREFIX:
            case PREF_SKIP_DELETESURROUNDINGTEXTINCODEPOINTS_PREFIX:
            case PREF_SKIP_REQUESTCURSORUPDATES_PREFIX:
            case PREF_SKIP_COMMITCORRECTION_PREFIX:
            case PREF_SKIP_GETSELECTEDTEXT_PREFIX:
            case PREF_SKIP_SETCOMPOSINGREGION_PREFIX:
            case PREF_OVERRIDE_SYSTEM_BEHAVIOR_SIMULATION_PREFIX:
            case PREF_INPUT_TYPE_TEXT_FLAG_AUTO_COMPLETE_PREFIX:
            case PREF_INPUT_TYPE_TEXT_FLAG_AUTO_CORRECT_PREFIX:
            case PREF_INPUT_TYPE_TEXT_FLAG_NO_SUGGESTIONS_PREFIX:
            case PREF_INPUT_TYPE_NUMBER_FLAG_SIGNED_PREFIX:
            case PREF_INPUT_TYPE_NUMBER_FLAG_DECIMAL_PREFIX:
            case PREF_NULL_INPUT_TYPE_MULTILINE_PREFIX:
            case PREF_NULL_INPUT_TYPE_CREATE_INPUT_CONNECTION_PREFIX:
            case PREF_NULL_INPUT_TYPE_SEND_SELECTION_INFO_PREFIX:
            case PREF_NULL_INPUT_TYPE_SEND_TEXT_PREFIX:
            case PREF_NULL_INPUT_TYPE_ALLOW_DELETE_SURROUNDING_TEXT_PREFIX:
            case PREF_NULL_INPUT_TYPE_ALLOW_SETTING_SELECTION_PREFIX:
            case PREF_IME_OPTIONS_FLAG_FORCE_ASCII_PREFIX:
            case PREF_IME_OPTIONS_FLAG_NAVIGATE_NEXT_PREFIX:
            case PREF_IME_OPTIONS_FLAG_NAVIGATE_PREVIOUS_PREFIX:
            case PREF_IME_OPTIONS_FLAG_NO_ACCESSORY_ACTION_PREFIX:
            case PREF_IME_OPTIONS_FLAG_NO_ENTER_ACTION_PREFIX:
            case PREF_IME_OPTIONS_FLAG_NO_EXTRACT_UI_PREFIX:
            case PREF_IME_OPTIONS_FLAG_NO_FULLSCREEN_PREFIX:
            case PREF_IME_OPTIONS_FLAG_NO_PERSONALIZED_LEARNING_PREFIX:
            case PREF_SELECT_ALL_ON_FOCUS_PREFIX:
            case PREF_ALLOW_UNDO_PREFIX:
            case PREF_SHOW_REFERENCE_EDITTEXT:
                return TYPE_BOOLEAN;
            case PREF_IME_ACTION_ID_PREFIX:
            case PREF_MAX_LENGTH_PREFIX:
            case PREF_SHIFT_CODEPOINT_PREFIX:
            case PREF_LIMIT_EXTRACT_MONITOR_TEXT_PREFIX:
            case PREF_LIMIT_RETURNED_TEXT_PREFIX:
            case PREF_UPDATE_DELAY_PREFIX:
            case PREF_FINISHCOMPOSINGTEXT_DELAY_PREFIX:
            case PREF_GETSURROUNDINGTEXT_DELAY_PREFIX:
            case PREF_GETTEXTBEFORECURSOR_DELAY_PREFIX:
            case PREF_GETSELECTEDTEXT_DELAY_PREFIX:
            case PREF_GETTEXTAFTERCURSOR_DELAY_PREFIX:
            case PREF_GETCURSORCAPSMODE_DELAY_PREFIX:
            case PREF_GETEXTRACTEDTEXT_DELAY_PREFIX:
                return TYPE_INT;
            case PREF_TEST_GROUP_NAME_PREFIX:
            case PREF_INPUT_TYPE_CLASS_PREFIX:
            case PREF_INPUT_TYPE_TEXT_VARIATION_PREFIX:
            case PREF_INPUT_TYPE_NUMBER_VARIATION_PREFIX:
            case PREF_INPUT_TYPE_DATETIME_VARIATION_PREFIX:
            case PREF_INPUT_TYPE_TEXT_FLAG_MULTI_LINE_PREFIX:
            case PREF_INPUT_TYPE_TEXT_FLAG_CAP_PREFIX:
            case PREF_NULL_INPUT_TYPE_COMPOSING_TEXT_BEHAVIOR_PREFIX:
            case PREF_IME_OPTIONS_ACTION_PREFIX:
            case PREF_IME_ACTION_LABEL_PREFIX:
            case PREF_PRIVATE_IME_OPTIONS_PREFIX:
            case PREF_THEME:
                return TYPE_STRING;
            case PREF_IME_LABEL_TEXT_PREFIX:
            case PREF_IME_DEFAULT_TEXT_PREFIX:
            case PREF_IME_HINT_TEXT_PREFIX:
                return TYPE_CHAR_SEQUENCE;
            case PREF_TEST_GROUP_IDS:
            case PREF_TEST_FIELD_IDS_PREFIX:
                return TYPE_INT_ARRAY;
            case PREF_RESTRICT_RANGE_PREFIX:
                return TYPE_INT_RANGE;
            case PREF_TEXT_LOCALES_PREFIX:
            case PREF_IME_HINT_LOCALES_PREFIX:
                return TYPE_LOCALE_ARRAY;
            case PREF_RESTRICT_SPECIFIC_PREFIX:
                return TYPE_TEXT_LIST_STRING;
            case PREF_TRANSLATE_SPECIFIC_PREFIX:
                return TYPE_TEXT_LIST_TRANSLATE_TEXT;
            default:
                return TYPE_UNKNOWN;
        }
    }

    public static final int COMPOSING_TEXT_BEHAVIOR_INVISIBLE = 0;
    public static final int COMPOSING_TEXT_BEHAVIOR_COMPOSE = 1;
    public static final int COMPOSING_TEXT_BEHAVIOR_COMMIT = 2;
    public static final int COMPOSING_TEXT_BEHAVIOR_IGNORE = 3;
    private static int getComposingTextBehaviorInt(
            String nullInputTypeComposingTextBehaviorString) {
        switch (nullInputTypeComposingTextBehaviorString) {
            case "INVISIBLE":
                return COMPOSING_TEXT_BEHAVIOR_INVISIBLE;
            case "COMPOSE":
                return COMPOSING_TEXT_BEHAVIOR_COMPOSE;
            case "COMMIT":
                return COMPOSING_TEXT_BEHAVIOR_COMMIT;
            case "IGNORE":
                return COMPOSING_TEXT_BEHAVIOR_IGNORE;
            default:
                Log.e(TAG, nullInputTypeComposingTextBehaviorString
                        + " isn't a valid composing text behavior");
                // the fallback is based on a null input type because the preference is specific to
                // this because other types have a fixed behavior
                return DEFAULT_NULL_INPUT_TYPE_COMPOSING_TEXT_BEHAVIOR;
        }
    }

    public static final boolean DEFAULT_MODIFY_COMMITTED_TEXT = false;
    public static final boolean DEFAULT_MODIFY_COMPOSED_TEXT = false;
    public static final boolean DEFAULT_MODIFY_COMPOSED_CHANGES_ONLY = false;
    public static final boolean DEFAULT_CONSIDER_COMPOSED_CHANGES_FROM_END = false;
    public static final boolean DEFAULT_RESTRICT_TO_INCLUDE = false;
    public static final boolean DEFAULT_TRANSLATE_FULL_MATCH_ONLY = false;
    public static final int DEFAULT_CODEPOINT_SHIFT = 0;
    public static final boolean DEFAULT_SKIP_EXTRACTING_TEXT = false;
    public static final boolean DEFAULT_IGNORE_EXTRACTED_TEXT_MONITOR = false;
    public static final boolean DEFAULT_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT = false;
    public static final boolean DEFAULT_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES = false;
    public static final boolean DEFAULT_EXTRACT_FULL_TEXT = false;
    public static final int DEFAULT_EXTRACT_MONITOR_TEXT_LIMIT = -1;
    public static final int DEFAULT_RETURNED_TEXT_LIMIT = -1;
    public static final boolean DEFAULT_DELETE_THROUGH_COMPOSING_TEXT = false;
    public static final boolean DEFAULT_KEEP_EMPTY_COMPOSING_POSITION = false;
    public static final boolean DEFAULT_SKIP_TAKESNAPSHOT = false;
    public static final boolean DEFAULT_SKIP_GETSURROUNDINGTEXT = false;
    public static final boolean DEFAULT_SKIP_PERFORMSPELLCHECK = false;
    public static final boolean DEFAULT_SKIP_SETIMECONSUMESINPUT = false;
    public static final boolean DEFAULT_SKIP_COMMITCONTENT = false;
    public static final boolean DEFAULT_SKIP_CLOSECONNECTION = false;
    public static final boolean DEFAULT_SKIP_DELETESURROUNDINGTEXTINCODEPOINTS = false;
    public static final boolean DEFAULT_SKIP_REQUESTCURSORUPDATES = false;
    public static final boolean DEFAULT_SKIP_COMMITCORRECTION = false;
    public static final boolean DEFAULT_SKIP_GETSELECTEDTEXT = false;
    public static final boolean DEFAULT_SKIP_SETCOMPOSINGREGION = false;
    public static final int DEFAULT_UPDATE_DELAY = 0;
    public static final int DEFAULT_FINISHCOMPOSINGTEXT_DELAY = 0;
    public static final int DEFAULT_GETSURROUNDINGTEXT_DELAY = 0;
    public static final int DEFAULT_GETTEXTBEFORECURSOR_DELAY = 0;
    public static final int DEFAULT_GETSELECTEDTEXT_DELAY = 0;
    public static final int DEFAULT_GETTEXTAFTERCURSOR_DELAY = 0;
    public static final int DEFAULT_GETCURSORCAPSMODE_DELAY = 0;
    public static final int DEFAULT_GETEXTRACTEDTEXT_DELAY = 0;
    public static final boolean DEFAULT_NULL_INPUT_TYPE_MULTILINE = false;
    public static final boolean NONNULL_INPUT_TYPE_CREATE_INPUT_CONNECTION = true;
    public static final boolean DEFAULT_NULL_INPUT_TYPE_CREATE_INPUT_CONNECTION = false;
    public static final boolean NONNULL_INPUT_TYPE_SEND_SELECTION_INFO = true;
    public static final boolean DEFAULT_NULL_INPUT_TYPE_SEND_SELECTION_INFO = false;
    public static final boolean NONNULL_INPUT_TYPE_SEND_TEXT = true;
    public static final boolean DEFAULT_NULL_INPUT_TYPE_SEND_TEXT = false;
    public static final int NONNULL_INPUT_TYPE_COMPOSING_TEXT_BEHAVIOR =
            COMPOSING_TEXT_BEHAVIOR_COMPOSE;
    public static final int DEFAULT_NULL_INPUT_TYPE_COMPOSING_TEXT_BEHAVIOR =
            COMPOSING_TEXT_BEHAVIOR_INVISIBLE;
    public static final boolean NONNULL_INPUT_TYPE_ALLOW_DELETE_SURROUNDING_TEXT = true;
    public static final boolean DEFAULT_NULL_INPUT_TYPE_ALLOW_DELETE_SURROUNDING_TEXT = false;
    public static final boolean NONNULL_INPUT_TYPE_ALLOW_SETTING_SELECTION = true;
    public static final boolean DEFAULT_NULL_INPUT_TYPE_ALLOW_SETTING_SELECTION = false;

    public static boolean defaultCreateInputConnection(int inputType) {
        return inputType != EditorInfo.TYPE_NULL
                ? NONNULL_INPUT_TYPE_CREATE_INPUT_CONNECTION
                : DEFAULT_NULL_INPUT_TYPE_CREATE_INPUT_CONNECTION;
    }
    public static boolean defaultSendSelectionInfo(int inputType) {
        return inputType != EditorInfo.TYPE_NULL
                ? NONNULL_INPUT_TYPE_SEND_SELECTION_INFO
                : DEFAULT_NULL_INPUT_TYPE_SEND_SELECTION_INFO;
    }
    public static boolean defaultSendText(int inputType) {
        return inputType != EditorInfo.TYPE_NULL
                ? NONNULL_INPUT_TYPE_SEND_TEXT
                : DEFAULT_NULL_INPUT_TYPE_SEND_TEXT;
    }
    public static int defaultComposingTextBehavior(int inputType) {
        return inputType != EditorInfo.TYPE_NULL
                ? NONNULL_INPUT_TYPE_COMPOSING_TEXT_BEHAVIOR
                : DEFAULT_NULL_INPUT_TYPE_COMPOSING_TEXT_BEHAVIOR;
    }
    public static boolean defaultAllowDeleteSurroundingText(int inputType) {
        return inputType != EditorInfo.TYPE_NULL
                ? NONNULL_INPUT_TYPE_ALLOW_DELETE_SURROUNDING_TEXT
                : DEFAULT_NULL_INPUT_TYPE_ALLOW_DELETE_SURROUNDING_TEXT;
    }
    public static boolean defaultAllowSettingSelection(int inputType) {
        return inputType != EditorInfo.TYPE_NULL
                ? NONNULL_INPUT_TYPE_ALLOW_SETTING_SELECTION
                : DEFAULT_NULL_INPUT_TYPE_ALLOW_SETTING_SELECTION;
    }

    //TODO: (EW) remove constants to consolidate and have this method manage whatever used the
    // constants before
    private static boolean getPrefDefaultBoolean(String keyOrPrefix) {
        switch (keyOrPrefix) {
            case PREF_OVERRIDE_TEXT_INPUT_MODIFICATION_PREFIX:
                return false;
            case PREF_MODIFY_COMMITTED_TEXT_PREFIX:
                return DEFAULT_MODIFY_COMMITTED_TEXT;
            case PREF_MODIFY_COMPOSED_TEXT_PREFIX:
                return DEFAULT_MODIFY_COMPOSED_TEXT;
            case PREF_MODIFY_COMPOSED_CHANGES_ONLY_PREFIX:
                return DEFAULT_MODIFY_COMPOSED_CHANGES_ONLY;
            case PREF_CONSIDER_COMPOSED_CHANGES_FROM_END_PREFIX:
                return DEFAULT_CONSIDER_COMPOSED_CHANGES_FROM_END;
            case PREF_RESTRICT_TO_INCLUDE_PREFIX:
                return DEFAULT_RESTRICT_TO_INCLUDE;
            case PREF_TRANSLATE_FULL_MATCH_ONLY_PREFIX:
                return DEFAULT_TRANSLATE_FULL_MATCH_ONLY;
            case PREF_OVERRIDE_TEXT_RETURN_PREFIX:
                return false;
            case PREF_SKIP_EXTRACTING_TEXT_PREFIX:
                return DEFAULT_SKIP_EXTRACTING_TEXT;
            case PREF_IGNORE_EXTRACTED_TEXT_MONITOR_PREFIX:
                return DEFAULT_IGNORE_EXTRACTED_TEXT_MONITOR;
            case PREF_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT_PREFIX:
                return DEFAULT_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT;
            case PREF_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES_PREFIX:
                return DEFAULT_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES;
            case PREF_EXTRACT_FULL_TEXT_PREFIX:
                return DEFAULT_EXTRACT_FULL_TEXT;
            case PREF_OVERRIDE_TEXT_COMPOSITION_PREFIX:
                return false;
            case PREF_DELETE_THROUGH_COMPOSING_TEXT_PREFIX:
                return DEFAULT_DELETE_THROUGH_COMPOSING_TEXT;
            case PREF_KEEP_EMPTY_COMPOSING_POSITION_PREFIX:
                return DEFAULT_KEEP_EMPTY_COMPOSING_POSITION;
            case PREF_OVERRIDE_TARGET_VERSION_SIMULATION_PREFIX:
                return false;
            case PREF_SKIP_TAKESNAPSHOT_PREFIX:
                return DEFAULT_SKIP_TAKESNAPSHOT;
            case PREF_SKIP_GETSURROUNDINGTEXT_PREFIX:
                return DEFAULT_SKIP_GETSURROUNDINGTEXT;
            case PREF_SKIP_PERFORMSPELLCHECK_PREFIX:
                return DEFAULT_SKIP_PERFORMSPELLCHECK;
            case PREF_SKIP_SETIMECONSUMESINPUT_PREFIX:
                return DEFAULT_SKIP_SETIMECONSUMESINPUT;
            case PREF_SKIP_COMMITCONTENT_PREFIX:
                return DEFAULT_SKIP_COMMITCONTENT;
            case PREF_SKIP_CLOSECONNECTION_PREFIX:
                return DEFAULT_SKIP_CLOSECONNECTION;
            case PREF_SKIP_DELETESURROUNDINGTEXTINCODEPOINTS_PREFIX:
                return DEFAULT_SKIP_DELETESURROUNDINGTEXTINCODEPOINTS;
            case PREF_SKIP_REQUESTCURSORUPDATES_PREFIX:
                return DEFAULT_SKIP_REQUESTCURSORUPDATES;
            case PREF_SKIP_COMMITCORRECTION_PREFIX:
                return DEFAULT_SKIP_COMMITCORRECTION;
            case PREF_SKIP_GETSELECTEDTEXT_PREFIX:
                return DEFAULT_SKIP_GETSELECTEDTEXT;
            case PREF_SKIP_SETCOMPOSINGREGION_PREFIX:
                return DEFAULT_SKIP_SETCOMPOSINGREGION;
            case PREF_OVERRIDE_SYSTEM_BEHAVIOR_SIMULATION_PREFIX:
                return false;
            case PREF_INPUT_TYPE_TEXT_FLAG_AUTO_COMPLETE_PREFIX:
                return false;
            case PREF_INPUT_TYPE_TEXT_FLAG_AUTO_CORRECT_PREFIX:
                return false;
            case PREF_INPUT_TYPE_TEXT_FLAG_NO_SUGGESTIONS_PREFIX:
                return false;
            case PREF_INPUT_TYPE_NUMBER_FLAG_SIGNED_PREFIX:
                return false;
            case PREF_INPUT_TYPE_NUMBER_FLAG_DECIMAL_PREFIX:
                return false;
            case PREF_NULL_INPUT_TYPE_MULTILINE_PREFIX:
                return DEFAULT_NULL_INPUT_TYPE_MULTILINE;
            case PREF_NULL_INPUT_TYPE_CREATE_INPUT_CONNECTION_PREFIX:
                return DEFAULT_NULL_INPUT_TYPE_CREATE_INPUT_CONNECTION;
            case PREF_NULL_INPUT_TYPE_SEND_SELECTION_INFO_PREFIX:
                return DEFAULT_NULL_INPUT_TYPE_SEND_SELECTION_INFO;
            case PREF_NULL_INPUT_TYPE_SEND_TEXT_PREFIX:
                return DEFAULT_NULL_INPUT_TYPE_SEND_TEXT;
            case PREF_NULL_INPUT_TYPE_ALLOW_DELETE_SURROUNDING_TEXT_PREFIX:
                return DEFAULT_NULL_INPUT_TYPE_ALLOW_DELETE_SURROUNDING_TEXT;
            case PREF_NULL_INPUT_TYPE_ALLOW_SETTING_SELECTION_PREFIX:
                return DEFAULT_NULL_INPUT_TYPE_ALLOW_SETTING_SELECTION;
            case PREF_IME_OPTIONS_FLAG_FORCE_ASCII_PREFIX:
                return false;
            case PREF_IME_OPTIONS_FLAG_NAVIGATE_NEXT_PREFIX:
                return false;
            case PREF_IME_OPTIONS_FLAG_NAVIGATE_PREVIOUS_PREFIX:
                return false;
            case PREF_IME_OPTIONS_FLAG_NO_ACCESSORY_ACTION_PREFIX:
                return false;
            case PREF_IME_OPTIONS_FLAG_NO_ENTER_ACTION_PREFIX:
                return false;
            case PREF_IME_OPTIONS_FLAG_NO_EXTRACT_UI_PREFIX:
                return false;
            case PREF_IME_OPTIONS_FLAG_NO_FULLSCREEN_PREFIX:
                return false;
            case PREF_IME_OPTIONS_FLAG_NO_PERSONALIZED_LEARNING_PREFIX:
                return false;
            case PREF_SELECT_ALL_ON_FOCUS_PREFIX:
                return false;
            case PREF_ALLOW_UNDO_PREFIX:
                return true;
            case PREF_SHOW_REFERENCE_EDITTEXT:
                return false;
            default:
                Log.e(TAG, "boolean default missing for " + keyOrPrefix
                        + (prefDataType(keyOrPrefix) != TYPE_BOOLEAN ? " (not a boolean)" : ""));
                return false;
        }
    }
    private static int getPrefDefaultInt(String keyOrPrefix) {
        switch (keyOrPrefix) {
            case PREF_IME_ACTION_ID_PREFIX:
                return 0;
            case PREF_MAX_LENGTH_PREFIX:
                return -1;
            case PREF_SHIFT_CODEPOINT_PREFIX:
                return DEFAULT_CODEPOINT_SHIFT;
            case PREF_LIMIT_EXTRACT_MONITOR_TEXT_PREFIX:
                return DEFAULT_EXTRACT_MONITOR_TEXT_LIMIT;
            case PREF_LIMIT_RETURNED_TEXT_PREFIX:
                return DEFAULT_RETURNED_TEXT_LIMIT;
            case PREF_UPDATE_DELAY_PREFIX:
                return DEFAULT_UPDATE_DELAY;
            case PREF_FINISHCOMPOSINGTEXT_DELAY_PREFIX:
                return DEFAULT_FINISHCOMPOSINGTEXT_DELAY;
            case PREF_GETSURROUNDINGTEXT_DELAY_PREFIX:
                return DEFAULT_GETSURROUNDINGTEXT_DELAY;
            case PREF_GETTEXTBEFORECURSOR_DELAY_PREFIX:
                return DEFAULT_GETTEXTBEFORECURSOR_DELAY;
            case PREF_GETSELECTEDTEXT_DELAY_PREFIX:
                return DEFAULT_GETSELECTEDTEXT_DELAY;
            case PREF_GETTEXTAFTERCURSOR_DELAY_PREFIX:
                return DEFAULT_GETTEXTAFTERCURSOR_DELAY;
            case PREF_GETCURSORCAPSMODE_DELAY_PREFIX:
                return DEFAULT_GETCURSORCAPSMODE_DELAY;
            case PREF_GETEXTRACTEDTEXT_DELAY_PREFIX:
                return DEFAULT_GETEXTRACTEDTEXT_DELAY;
            default:
                Log.e(TAG, "int default missing for " + keyOrPrefix
                        + (prefDataType(keyOrPrefix) != TYPE_INT ? " (not an int)" : ""));
                return 0;
        }
    }
    private static long getPrefDefaultLong(String keyOrPrefix) {
        switch (keyOrPrefix) {
            default:
                Log.e(TAG, "long default missing for " + keyOrPrefix
                        + (prefDataType(keyOrPrefix) != TYPE_LONG ? " (not a long)" : ""));
                return 0;
        }
    }
    private static long getPrefDefaultFloat(String keyOrPrefix) {
        switch (keyOrPrefix) {
            default:
                Log.e(TAG, "float default missing for " + keyOrPrefix
                        + (prefDataType(keyOrPrefix) != TYPE_FLOAT ? " (not a float)" : ""));
                return 0;
        }
    }
    private static String getPrefDefaultString(String keyOrPrefix) {
        switch (keyOrPrefix) {
            case PREF_INPUT_TYPE_CLASS_PREFIX:
                return "TYPE_CLASS_TEXT";
            case PREF_INPUT_TYPE_TEXT_VARIATION_PREFIX:
                return "TYPE_TEXT_VARIATION_NORMAL";
            case PREF_INPUT_TYPE_NUMBER_VARIATION_PREFIX:
                return "TYPE_NUMBER_VARIATION_NORMAL";
            case PREF_INPUT_TYPE_DATETIME_VARIATION_PREFIX:
                return "TYPE_DATETIME_VARIATION_NORMAL";
            case PREF_IME_OPTIONS_ACTION_PREFIX:
                return "IME_ACTION_UNSPECIFIED";
            case PREF_THEME:
                return THEME_SYSTEM_DEFAULT;
            case PREF_INPUT_TYPE_TEXT_FLAG_MULTI_LINE_PREFIX:
            case PREF_INPUT_TYPE_TEXT_FLAG_CAP_PREFIX:
            case PREF_NULL_INPUT_TYPE_COMPOSING_TEXT_BEHAVIOR_PREFIX:
                return "";
            case PREF_TEST_GROUP_NAME_PREFIX:
            case PREF_IME_ACTION_LABEL_PREFIX:
            case PREF_PRIVATE_IME_OPTIONS_PREFIX:
                return null;
            default:
                Log.e(TAG, "String default missing for " + keyOrPrefix
                        + (prefDataType(keyOrPrefix) != TYPE_STRING ? " (not a String)" : ""));
                return null;
        }
    }
    private static Spanned getPrefDefaultSpanned(String keyOrPrefix) {
        switch (keyOrPrefix) {
            default:
                Log.e(TAG, "Spanned default missing for " + keyOrPrefix
                        + (prefDataType(keyOrPrefix) != TYPE_SPANNED ? " (not a Spanned)" : ""));
                return null;
        }
    }
    private static CharSequence getPrefDefaultCharSequence(String keyOrPrefix) {
        switch (keyOrPrefix) {
            case PREF_IME_LABEL_TEXT_PREFIX:
            case PREF_IME_DEFAULT_TEXT_PREFIX:
            case PREF_IME_HINT_TEXT_PREFIX:
                return null;
            default:
                Log.e(TAG, "CharSequence default missing for " + keyOrPrefix
                        + (prefDataType(keyOrPrefix) != TYPE_CHAR_SEQUENCE ? " (not a CharSequence)" : ""));
                return null;
        }
    }
    private static int[] getPrefDefaultIntArray(String keyOrPrefix) {
        switch (keyOrPrefix) {
            case PREF_TEST_GROUP_IDS:
                return new int[0];
            case PREF_TEST_FIELD_IDS_PREFIX:
                return new int[0];
            default:
                Log.e(TAG, "int[] default missing for " + keyOrPrefix
                        + (prefDataType(keyOrPrefix) != TYPE_INT_ARRAY ? " (not an int[])" : ""));
                return null;
        }
    }
    private static String[] getPrefDefaultStringArray(String keyOrPrefix) {
        switch (keyOrPrefix) {
            default:
                Log.e(TAG, "String[] default missing for " + keyOrPrefix
                        + (prefDataType(keyOrPrefix) != TYPE_STRING_ARRAY
                                ? " (not a String[])"
                                : ""));
                return null;
        }
    }

    private boolean readBoolean(String prefKey) {
        return mPrefs.getBoolean(prefKey, getPrefDefaultBoolean(prefKey));
    }
    private boolean readTestFieldBoolean(int fieldId, String prefKeyPrefix) {
        return mPrefs.getBoolean(prefKeyPrefix + getSuffix(fieldId),
                getPrefDefaultBoolean(prefKeyPrefix));
    }

    private int readInt(String prefKey) {
        return mPrefs.getInt(prefKey, getPrefDefaultInt(prefKey));
    }
    private int readTestFieldInt(int fieldId, String prefKeyPrefix) {
        return mPrefs.getInt(prefKeyPrefix + getSuffix(fieldId),
                getPrefDefaultInt(prefKeyPrefix));
    }

    private String readString(String prefKey) {
        return mPrefs.getString(prefKey, getPrefDefaultString(prefKey));
    }
    private String readTestGroupString(int groupId, String prefKeyPrefix) {
        return mPrefs.getString(prefKeyPrefix + GROUP_INFIX + groupId,
                getPrefDefaultString(prefKeyPrefix));
    }
    private String readTestFieldString(int fieldId, String prefKeyPrefix) {
        return mPrefs.getString(prefKeyPrefix + getSuffix(fieldId),
                getPrefDefaultString(prefKeyPrefix));
    }

    private int[] mTestGroupIds;
    private final Map<Integer, TestGroup> mTestGroups = new HashMap<>();
    private final Map<Integer, TestField> mTestFields = new HashMap<>();
    private final AppLevelDefaults mTestFieldDefaults = new AppLevelDefaults();

    private String mTheme;
    private boolean mShowReferenceEditText;

    private SharedPreferenceManager mPrefs;

    private static final Settings sInstance = new Settings();

    private Settings() {
        // Intentional empty constructor for singleton.
    }

    public static Settings getInstance() {
        return sInstance;
    }

    public static void init(final Context context) {
        if (sInstance.mPrefs != null) {
            // already initialized
            return;
        }
        sInstance.onCreate(context);
    }

    private void onCreate(final Context context) {
        mPrefs = new SharedPreferenceManager(
                PreferenceManager.getDefaultSharedPreferences(context));

        logPreferences();

        convertInputTypeNullPrefs(mPrefs);

        if (!mPrefs.contains(PREF_TEST_GROUP_IDS)) {
            // create a default group and field the first time the app is opened
            Log.d(TAG, "creating defaults");
            mPrefs.setIntArray(PREF_TEST_GROUP_IDS, new int[] { 0 });
            mPrefs.setIntArray(PREF_TEST_FIELD_IDS_PREFIX, new int[] { 0 });
        }
        mPrefs.registerOnSharedPreferenceChangeListener(this);
        loadSettings();

        if (LIST_PREFS) {
            for (String prefKey : mPrefs.getPrefKeysNotLoaded()) {
                Log.w(TAG, "Preference key " + prefKey + " has data but wasn't loaded");
            }
        }
    }

    private static void convertInputTypeNullPrefs(final SharedPreferenceManager prefs) {
        String[] prefKeyPrefixes = new String[] {
                PREF_NULL_INPUT_TYPE_CREATE_INPUT_CONNECTION_PREFIX,
                PREF_NULL_INPUT_TYPE_SEND_SELECTION_INFO_PREFIX,
                PREF_NULL_INPUT_TYPE_SEND_TEXT_PREFIX,
                PREF_NULL_INPUT_TYPE_COMPOSING_TEXT_BEHAVIOR_PREFIX,
                PREF_NULL_INPUT_TYPE_ALLOW_DELETE_SURROUNDING_TEXT_PREFIX,
                PREF_NULL_INPUT_TYPE_ALLOW_SETTING_SELECTION_PREFIX
        };
        for (int groupId : readTestFieldGroupIds(prefs)) {
            for (int fieldId : readTestGroupFieldIds(prefs, groupId)) {
                for (String newPrefKeyPrefix : prefKeyPrefixes) {
                    String oldPrefKeyPrefix = newPrefKeyPrefix.replace("null_input_type_", "");
                    String oldPrefKey = oldPrefKeyPrefix + FIELD_INFIX + fieldId;
                    String newPrefKey = newPrefKeyPrefix + FIELD_INFIX + fieldId;

                    if (!prefs.contains(oldPrefKey)) {
                        continue;
                    }

                    if (newPrefKeyPrefix.equals(
                            PREF_NULL_INPUT_TYPE_COMPOSING_TEXT_BEHAVIOR_PREFIX)) {
                        String val = prefs.getString(oldPrefKey, null);
                        Log.d(TAG, "Write " + newPrefKey + ": " + val);
                        prefs.setString(newPrefKey, val);
                    } else {
                        boolean val = prefs.getBoolean(oldPrefKey, false);
                        Log.d(TAG, "Write " + newPrefKey + ": " + val);
                        prefs.setBoolean(newPrefKey, val);
                    }

                    prefs.remove(oldPrefKey);
                    Log.d(TAG, "Delete " + oldPrefKey);
                }
            }
        }
    }

    private String[] sortPrefKeys(Set<String> allPrefs) {
        String[] sortedPrefKeys = allPrefs.toArray(new String[0]);
        Arrays.sort(sortedPrefKeys, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                // sort nulls to the end (shouldn't ever be any)
                if (a == null) {
                    if (b == null) {
                        return 0;
                    }
                    return 1;
                }
                if (b == null) {
                    return -1;
                }

                // first sort keys in groupings of related things (groups, fields, etc)
                int aCategory = getPrefKeyCategory(a);
                int bCategory = getPrefKeyCategory(b);
                if (aCategory != bCategory) {
                    return aCategory - bCategory;
                }

                // then sort group and field preferences on their ID
                if (aCategory == 3) {
                    int aId = parseIntSuffix(a, GROUP_INFIX);
                    int bId = parseIntSuffix(b, GROUP_INFIX);
                    if (aId != bId) {
                        return aId - bId;
                    }
                } else if (aCategory == 4) {
                    int aId = parseIntSuffix(a, FIELD_INFIX);
                    int bId = parseIntSuffix(b, FIELD_INFIX);
                    if (aId != bId) {
                        return aId - bId;
                    }
                }

                // finally just sort based on the default string sort order
                return a.compareTo(b);
            }

            private int getPrefKeyCategory(@NonNull String prefKey) {
                if (prefKey.endsWith(BASE_SUFFIX)) {
                    return 1;
                } else if (prefKey.equals(PREF_TEST_GROUP_IDS)) {
                    return 2;
                } else if (containsIdSuffix(prefKey, GROUP_INFIX)) {
                    return 3;
                } else if (containsIdSuffix(prefKey, FIELD_INFIX)) {
                    return 4;
                }
                return 0;
            }

            private int parseIntSuffix(String prefKey, String infix) {
                int prefixLength = prefKey.lastIndexOf(infix);
                try {
                    return Integer.parseInt(prefKey.substring(prefixLength + infix.length()));
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        });

        return sortedPrefKeys;
    }

    private void logPreferences() {
        if (!LIST_PREFS) {
            return;
        }
        Map<String, ?> allPrefs = mPrefs.getAll();
        String[] prefKeys = sortPrefKeys(allPrefs.keySet());

        Log.d(TAG, "existing preferences:");
        for (String prefKey : prefKeys) {
            Object value = allPrefs.get(prefKey);
            String valueDisplay;
            if (value instanceof int[]) {
                valueDisplay = Arrays.toString((int[]) value);
            } else if (value instanceof String[]) {
                valueDisplay = Arrays.toString((String[]) value);
            } else {
                valueDisplay = "" + value;
            }
            Log.d(TAG, prefKey + ": " + valueDisplay);
        }
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
        for (String prefKey : MISC_PREF_KEYS) {
            loadSetting(prefKey);
        }
        loadTestFieldSettings(BASE_FIELD_ID);
        mTestGroupIds = readTestFieldGroupIds(mPrefs);
        mTestGroups.clear();
        mTestFields.clear();
        for (int groupId : mTestGroupIds) {
            loadExistingGroup(groupId);
        }
    }

    private TestGroup loadExistingGroup(int groupId) {
        //TODO: (EW) see if PREF_TEST_GROUP_NAME_PREFIX can only be loaded in 1 call
        String groupName = readTestGroupString(groupId, PREF_TEST_GROUP_NAME_PREFIX);
        int[] groupFieldIds = readTestGroupFieldIds(mPrefs, groupId);
        TestGroup group = new TestGroup(groupId, groupName, groupFieldIds);
        mTestGroups.put(groupId, group);

        for (int fieldId : groupFieldIds) {
            loadExistingField(fieldId);
        }

        return group;
    }

    private TestField loadExistingField(int fieldId) {
        TestField field = new TestField(fieldId);
        mTestFields.put(fieldId, field);
        loadTestFieldSettings(fieldId);
        return field;
    }

    private void loadTestFieldSettings(int fieldId) {
        // intentionally skipping some preferences since they are read in groups, so listing them
        // all would just read all of them multiple times. leaving them commented out here for
        // visibility.
        final String[] testFieldPrefKeyPrefixes = new String[] {
                PREF_IME_LABEL_TEXT_PREFIX,
                PREF_IME_DEFAULT_TEXT_PREFIX,
                PREF_IME_HINT_TEXT_PREFIX,
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
                //PREF_NULL_INPUT_TYPE_CREATE_INPUT_CONNECTION_PREFIX,
                //PREF_NULL_INPUT_TYPE_SEND_SELECTION_INFO_PREFIX,
                //PREF_NULL_INPUT_TYPE_SEND_TEXT_PREFIX,
                //PREF_NULL_INPUT_TYPE_COMPOSING_TEXT_BEHAVIOR_PREFIX,
                //PREF_NULL_INPUT_TYPE_ALLOW_DELETE_SURROUNDING_TEXT_PREFIX,
                //PREF_NULL_INPUT_TYPE_ALLOW_SETTING_SELECTION_PREFIX,
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

        for (String prefKeyPrefix : DEFAULTABLE_TEST_FIELD_PREF_KEY_PREFIXES) {
            loadTestFieldOrDefaultSetting(prefKeyPrefix, fieldId);
        }
    }

    private void loadSetting(String prefKey) {
        switch (prefKey) {
            case PREF_TEST_GROUP_IDS:
                // internal state is updated while these are modified since they aren't managed by a
                // simple Preference, so we don't need to do anything when these change
                break;
            case PREF_THEME:
                mTheme = readString(PREF_THEME);
                break;
            case PREF_SHOW_REFERENCE_EDITTEXT:
                mShowReferenceEditText = readBoolean(PREF_SHOW_REFERENCE_EDITTEXT);
                break;
            default:
                // try loading as a specific field or group's setting
                loadPrefixedSetting(prefKey);
                break;
        }
    }

    private void loadPrefixedSetting(String prefKey) {
        if (prefKey == null) {
            return;
        }
        if (prefKey.endsWith(BASE_SUFFIX)) {
            loadTestFieldOrDefaultSetting(
                    prefKey.substring(0, prefKey.length() - BASE_SUFFIX.length()),
                    BASE_FIELD_ID);
        } else if (containsIdSuffix(prefKey, GROUP_INFIX)) {
            PrefKeyPieces prefKeyPieces = PrefKeyPieces.parse(prefKey, GROUP_INFIX, mTestGroups,
                    Settings::isGroupIdValid);
            if (prefKeyPieces == null) {
                return;
            }
            loadTestGroupSetting(prefKeyPieces.mPrefix, prefKeyPieces.mId);
        } else if (containsIdSuffix(prefKey, FIELD_INFIX)) {
            PrefKeyPieces prefKeyPieces = PrefKeyPieces.parse(prefKey, FIELD_INFIX, mTestFields,
                    Settings::isFieldIdValid);
            if (prefKeyPieces == null) {
                return;
            }
            loadTestFieldSetting(prefKeyPieces.mPrefix, prefKeyPieces.mId);
            loadTestFieldOrDefaultSetting(prefKeyPieces.mPrefix, prefKeyPieces.mId);
        } else {
            Log.w(TAG, "Preference " + prefKey + " couldn't be processed as a prefixed setting");
        }
    }

    private static boolean containsIdSuffix(String prefKey, String infix) {
        return prefKey.matches(".*" + Pattern.quote(infix) + "\\d+$");
    }

    private static class PrefKeyPieces {
        public final String mPrefix;
        public final String mInfix;
        public final int mId;

        public PrefKeyPieces(String prefix, String infix, int id) {
            mPrefix = prefix;
            mInfix = infix;
            mId = id;
        }

        public static PrefKeyPieces parse(String prefKey, String infix, Map<Integer, ?> target,
                                          Predicate<Integer> isIdValid) {
            int prefixLength = prefKey.lastIndexOf(infix);
            String infixName = infix.replaceAll("^_", "").replaceAll("_$", "").replaceAll("_", " ");
            int id;
            try {
                id = Integer.parseInt(prefKey.substring(prefixLength + infix.length()));
            } catch (NumberFormatException ignored) {
                Log.e(TAG, "Failed to parse " + infixName + " ID for pref " + prefKey);
                return null;
            }
            if (!target.containsKey(id) && !getInstance().mPrefs.contains(prefKey)) {
                // this is most likely from deleting an old preference when the parent is deleted,
                // so we don't need to bother loading this value
                return null;
            }
            if (!isIdValid.test(id)) {
                Log.e(TAG, "The " + infixName + " " + id + " for pref " + prefKey
                        + " doesn't exist");
                return null;
            }
            String prefKeyPrefix = prefKey.substring(0, prefixLength);
            return new PrefKeyPieces(prefKeyPrefix, infix, id);
        }
    }

    private void loadTestGroupSetting(String prefKeyPrefix, int groupId) {
        switch (prefKeyPrefix) {
            case PREF_TEST_FIELD_IDS_PREFIX:
                // internal state is updated while these are modified since they aren't managed by a
                // simple Preference, so we don't need to do anything when these change
                break;
            case PREF_TEST_GROUP_NAME_PREFIX:
                getGroupById(groupId).mName =
                        readTestGroupString(groupId, PREF_TEST_GROUP_NAME_PREFIX);
                break;
            default:
                Log.w(TAG, "Preference " + prefKeyPrefix + GROUP_INFIX + groupId
                        + " wasn't processed");
        }
    }

    private void loadTestFieldSetting(String prefKeyPrefix, int fieldId) {
        TestField testField = getField(fieldId);
        switch (prefKeyPrefix) {
            case PREF_IME_LABEL_TEXT_PREFIX:
                testField.mLabelText = readTestFieldLabelText(mPrefs, fieldId);
                break;
            case PREF_IME_DEFAULT_TEXT_PREFIX:
                testField.mDefaultText = readTestFieldDefaultText(mPrefs, fieldId);
                break;
            case PREF_IME_HINT_TEXT_PREFIX:
                testField.mHintText = readTestFieldHintText(mPrefs, fieldId);
                break;
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
            case PREF_NULL_INPUT_TYPE_CREATE_INPUT_CONNECTION_PREFIX:
            case PREF_NULL_INPUT_TYPE_SEND_SELECTION_INFO_PREFIX:
            case PREF_NULL_INPUT_TYPE_SEND_TEXT_PREFIX:
            case PREF_NULL_INPUT_TYPE_COMPOSING_TEXT_BEHAVIOR_PREFIX:
            case PREF_NULL_INPUT_TYPE_ALLOW_DELETE_SURROUNDING_TEXT_PREFIX:
            case PREF_NULL_INPUT_TYPE_ALLOW_SETTING_SELECTION_PREFIX:
                testField.mInputType = readTestFieldInputType(fieldId);
                testField.mNullInputTypeMultiline =
                        readTestFieldBoolean(fieldId, PREF_NULL_INPUT_TYPE_MULTILINE_PREFIX);
                // the following settings only apply to null input types since as far as I can
                // tell, the others are expected to create the input connection to fully support
                // rich input, are expected to send selection info (possibly based on the same
                // understanding for them needing to create an input connection), are expected to
                // return text as part of fully supporting rich input, and are expected to support
                // all of the rich editing specified in documentation for InputConnection (that
                // isn't noted as being optional)
                if (testField.mInputType == EditorInfo.TYPE_NULL) {
                    testField.mCreateInputConnection = readTestFieldBoolean(fieldId,
                            PREF_NULL_INPUT_TYPE_CREATE_INPUT_CONNECTION_PREFIX);
                    testField.mSendSelectionInfo = readTestFieldBoolean(fieldId,
                            PREF_NULL_INPUT_TYPE_SEND_SELECTION_INFO_PREFIX);
                    testField.mSendText = readTestFieldBoolean(fieldId,
                            PREF_NULL_INPUT_TYPE_SEND_TEXT_PREFIX);
                    if (testField.mCreateInputConnection) {
                        testField.mComposingTextBehavior = getComposingTextBehaviorInt(
                                readTestFieldString(fieldId,
                                        PREF_NULL_INPUT_TYPE_COMPOSING_TEXT_BEHAVIOR_PREFIX));
                        testField.mAllowDeleteSurroundingText = readTestFieldBoolean(fieldId,
                                PREF_NULL_INPUT_TYPE_ALLOW_DELETE_SURROUNDING_TEXT_PREFIX);
                        testField.mAllowSettingSelection = readTestFieldBoolean(fieldId,
                                PREF_NULL_INPUT_TYPE_ALLOW_SETTING_SELECTION_PREFIX);
                    } else {
                        // composition (or custom management) is only possible if an input
                        // connection is created
                        testField.mComposingTextBehavior = COMPOSING_TEXT_BEHAVIOR_INVISIBLE;
                        // these APIs are only possible to be implemented if an input connection is
                        // created
                        testField.mAllowDeleteSurroundingText = false;
                        // setting the selection position is only possible if an input connection is
                        // created
                        testField.mAllowSettingSelection = false;
                    }
                } else {
                    testField.mCreateInputConnection = NONNULL_INPUT_TYPE_CREATE_INPUT_CONNECTION;
                    testField.mSendSelectionInfo = NONNULL_INPUT_TYPE_SEND_SELECTION_INFO;
                    testField.mSendText = NONNULL_INPUT_TYPE_SEND_TEXT;
                    testField.mComposingTextBehavior = NONNULL_INPUT_TYPE_COMPOSING_TEXT_BEHAVIOR;
                    testField.mAllowDeleteSurroundingText =
                            NONNULL_INPUT_TYPE_ALLOW_DELETE_SURROUNDING_TEXT;
                    testField.mAllowSettingSelection = NONNULL_INPUT_TYPE_ALLOW_SETTING_SELECTION;
                }
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
                testField.mImeOptions = readTestFieldImeOptions(fieldId);
                break;
            case PREF_IME_ACTION_ID_PREFIX:
                testField.mImeActionId = readTestFieldInt(fieldId, PREF_IME_ACTION_ID_PREFIX);
                break;
            case PREF_IME_ACTION_LABEL_PREFIX:
                testField.mImeActionLabel =
                        readTestFieldString(fieldId, PREF_IME_ACTION_LABEL_PREFIX);
                break;
            case PREF_PRIVATE_IME_OPTIONS_PREFIX:
                testField.mPrivateImeOptions =
                        readTestFieldString(fieldId, PREF_PRIVATE_IME_OPTIONS_PREFIX);
                break;
            case PREF_SELECT_ALL_ON_FOCUS_PREFIX:
                testField.mSelectAllOnFocus =
                        readTestFieldBoolean(fieldId, PREF_SELECT_ALL_ON_FOCUS_PREFIX);
                break;
            case PREF_MAX_LENGTH_PREFIX:
                testField.mMaxLength = readTestFieldInt(fieldId, PREF_MAX_LENGTH_PREFIX);
                break;
            case PREF_ALLOW_UNDO_PREFIX:
                testField.mAllowUndo = readTestFieldBoolean(fieldId, PREF_ALLOW_UNDO_PREFIX);
                break;
            case PREF_TEXT_LOCALES_PREFIX:
                testField.mTextLocales = readTestFieldTextLocales(mPrefs, fieldId);
                break;
            case PREF_IME_HINT_LOCALES_PREFIX:
                testField.mImeHintLocales = readTestFieldImeHintLocales(mPrefs, fieldId);
                break;

            case PREF_OVERRIDE_TEXT_INPUT_MODIFICATION_PREFIX:
                testField.mOverrideTextInputModification =
                        readTestFieldBoolean(fieldId, PREF_OVERRIDE_TEXT_INPUT_MODIFICATION_PREFIX);
                break;
            case PREF_OVERRIDE_TEXT_RETURN_PREFIX:
                testField.mOverrideTextReturn =
                        readTestFieldBoolean(fieldId, PREF_OVERRIDE_TEXT_RETURN_PREFIX);
                break;
            case PREF_OVERRIDE_TEXT_COMPOSITION_PREFIX:
                testField.mOverrideTextComposition =
                        readTestFieldBoolean(fieldId, PREF_OVERRIDE_TEXT_COMPOSITION_PREFIX);
                break;
            case PREF_OVERRIDE_TARGET_VERSION_SIMULATION_PREFIX:
                testField.mOverrideTargetVersion = readTestFieldBoolean(fieldId,
                        PREF_OVERRIDE_TARGET_VERSION_SIMULATION_PREFIX);
                break;
            case PREF_OVERRIDE_SYSTEM_BEHAVIOR_SIMULATION_PREFIX:
                testField.mOverrideSystemBehavior = readTestFieldBoolean(fieldId,
                        PREF_OVERRIDE_SYSTEM_BEHAVIOR_SIMULATION_PREFIX);
                break;
            default:
                Log.w(TAG, "Preference " + prefKeyPrefix + FIELD_INFIX + fieldId
                        + " wasn't processed");
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
                testFieldOrDefault.mModifyCommittedText =
                        readTestFieldBoolean(fieldId, PREF_MODIFY_COMMITTED_TEXT_PREFIX);
                break;
            case PREF_MODIFY_COMPOSED_TEXT_PREFIX:
                testFieldOrDefault.mModifyComposedText =
                        readTestFieldBoolean(fieldId, PREF_MODIFY_COMPOSED_TEXT_PREFIX);
                break;
            case PREF_MODIFY_COMPOSED_CHANGES_ONLY_PREFIX:
                testFieldOrDefault.mModifyComposedChangesOnly =
                        readTestFieldBoolean(fieldId, PREF_MODIFY_COMPOSED_CHANGES_ONLY_PREFIX);
                break;
            case PREF_CONSIDER_COMPOSED_CHANGES_FROM_END_PREFIX:
                testFieldOrDefault.mConsiderComposedChangesFromEnd =
                        readTestFieldBoolean(fieldId,
                                PREF_CONSIDER_COMPOSED_CHANGES_FROM_END_PREFIX);
                break;
            case PREF_RESTRICT_TO_INCLUDE_PREFIX:
                testFieldOrDefault.mRestrictToInclude =
                        readTestFieldBoolean(fieldId, PREF_RESTRICT_TO_INCLUDE_PREFIX);
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
                        readTestFieldBoolean(fieldId, PREF_TRANSLATE_FULL_MATCH_ONLY_PREFIX);
                break;
            case PREF_SHIFT_CODEPOINT_PREFIX:
                testFieldOrDefault.mShiftCodepoint =
                        readTestFieldInt(fieldId, PREF_SHIFT_CODEPOINT_PREFIX);
                break;

            case PREF_SKIP_EXTRACTING_TEXT_PREFIX:
                testFieldOrDefault.mSkipExtractingText =
                        readTestFieldBoolean(fieldId, PREF_SKIP_EXTRACTING_TEXT_PREFIX);
                break;
            case PREF_IGNORE_EXTRACTED_TEXT_MONITOR_PREFIX:
                testFieldOrDefault.mIgnoreExtractedTextMonitor =
                        readTestFieldBoolean(fieldId, PREF_IGNORE_EXTRACTED_TEXT_MONITOR_PREFIX);
                break;
            case PREF_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT_PREFIX:
                testFieldOrDefault.mUpdateSelectionBeforeExtractedText =
                        readTestFieldBoolean(fieldId,
                                PREF_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT_PREFIX);
                break;
            case PREF_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES_PREFIX:
                testFieldOrDefault.mUpdateExtractedTextOnlyOnNetChanges =
                        readTestFieldBoolean(fieldId,
                                PREF_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES_PREFIX);
                break;
            case PREF_EXTRACT_FULL_TEXT_PREFIX:
                testFieldOrDefault.mExtractFullText =
                        readTestFieldBoolean(fieldId, PREF_EXTRACT_FULL_TEXT_PREFIX);
                break;
            case PREF_LIMIT_EXTRACT_MONITOR_TEXT_PREFIX:
                testFieldOrDefault.mExtractMonitorTextLimit =
                        readTestFieldInt(fieldId, PREF_LIMIT_EXTRACT_MONITOR_TEXT_PREFIX);
                break;
            case PREF_LIMIT_RETURNED_TEXT_PREFIX:
                testFieldOrDefault.mReturnedTextLimit =
                        readTestFieldInt(fieldId, PREF_LIMIT_RETURNED_TEXT_PREFIX);
                break;

            case PREF_DELETE_THROUGH_COMPOSING_TEXT_PREFIX:
                testFieldOrDefault.mDeleteThroughComposingText =
                        readTestFieldBoolean(fieldId, PREF_DELETE_THROUGH_COMPOSING_TEXT_PREFIX);
                break;
            case PREF_KEEP_EMPTY_COMPOSING_POSITION_PREFIX:
                testFieldOrDefault.mKeepEmptyComposingPosition =
                        readTestFieldBoolean(fieldId, PREF_KEEP_EMPTY_COMPOSING_POSITION_PREFIX);
                break;

            case PREF_SKIP_TAKESNAPSHOT_PREFIX:
                testFieldOrDefault.mSkipTakeSnapshot =
                        readTestFieldBoolean(fieldId, PREF_SKIP_TAKESNAPSHOT_PREFIX);
                break;
            case PREF_SKIP_GETSURROUNDINGTEXT_PREFIX:
                testFieldOrDefault.mSkipGetSurroundingText =
                        readTestFieldBoolean(fieldId, PREF_SKIP_GETSURROUNDINGTEXT_PREFIX);
                break;
            case PREF_SKIP_PERFORMSPELLCHECK_PREFIX:
                testFieldOrDefault.mSkipPerformSpellCheck =
                        readTestFieldBoolean(fieldId, PREF_SKIP_PERFORMSPELLCHECK_PREFIX);
                break;
            case PREF_SKIP_SETIMECONSUMESINPUT_PREFIX:
                testFieldOrDefault.mSkipSetImeConsumesInput =
                        readTestFieldBoolean(fieldId, PREF_SKIP_SETIMECONSUMESINPUT_PREFIX);
                break;
            case PREF_SKIP_COMMITCONTENT_PREFIX:
                testFieldOrDefault.mSkipCommitContent =
                        readTestFieldBoolean(fieldId, PREF_SKIP_COMMITCONTENT_PREFIX);
                break;
            case PREF_SKIP_CLOSECONNECTION_PREFIX:
                testFieldOrDefault.mSkipCloseConnection =
                        readTestFieldBoolean(fieldId, PREF_SKIP_CLOSECONNECTION_PREFIX);
                break;
            case PREF_SKIP_DELETESURROUNDINGTEXTINCODEPOINTS_PREFIX:
                testFieldOrDefault.mSkipDeleteSurroundingTextInCodePoints =
                        readTestFieldBoolean(fieldId,
                                PREF_SKIP_DELETESURROUNDINGTEXTINCODEPOINTS_PREFIX);
                break;
            case PREF_SKIP_REQUESTCURSORUPDATES_PREFIX:
                testFieldOrDefault.mSkipRequestCursorUpdates =
                        readTestFieldBoolean(fieldId, PREF_SKIP_REQUESTCURSORUPDATES_PREFIX);
                break;
            case PREF_SKIP_COMMITCORRECTION_PREFIX:
                testFieldOrDefault.mSkipCommitCorrection =
                        readTestFieldBoolean(fieldId, PREF_SKIP_COMMITCORRECTION_PREFIX);
                break;
            case PREF_SKIP_GETSELECTEDTEXT_PREFIX:
                testFieldOrDefault.mSkipGetSelectedText =
                        readTestFieldBoolean(fieldId, PREF_SKIP_GETSELECTEDTEXT_PREFIX);
                break;
            case PREF_SKIP_SETCOMPOSINGREGION_PREFIX:
                testFieldOrDefault.mSkipSetComposingRegion =
                        readTestFieldBoolean(fieldId, PREF_SKIP_SETCOMPOSINGREGION_PREFIX);
                break;

            case PREF_UPDATE_DELAY_PREFIX:
                testFieldOrDefault.mUpdateDelay =
                        readTestFieldInt(fieldId, PREF_UPDATE_DELAY_PREFIX);
                break;
            case PREF_FINISHCOMPOSINGTEXT_DELAY_PREFIX:
                testFieldOrDefault.mFinishComposingTextDelay =
                        readTestFieldInt(fieldId, PREF_FINISHCOMPOSINGTEXT_DELAY_PREFIX);
                break;
            case PREF_GETSURROUNDINGTEXT_DELAY_PREFIX:
                testFieldOrDefault.mGetSurroundingTextDelay =
                        readTestFieldInt(fieldId, PREF_GETSURROUNDINGTEXT_DELAY_PREFIX);
                break;
            case PREF_GETTEXTBEFORECURSOR_DELAY_PREFIX:
                testFieldOrDefault.mGetTextBeforeCursorDelay =
                        readTestFieldInt(fieldId, PREF_GETTEXTBEFORECURSOR_DELAY_PREFIX);
                break;
            case PREF_GETSELECTEDTEXT_DELAY_PREFIX:
                testFieldOrDefault.mGetSelectedTextDelay =
                        readTestFieldInt(fieldId, PREF_GETSELECTEDTEXT_DELAY_PREFIX);
                break;
            case PREF_GETTEXTAFTERCURSOR_DELAY_PREFIX:
                testFieldOrDefault.mGetTextAfterCursorDelay =
                        readTestFieldInt(fieldId, PREF_GETTEXTAFTERCURSOR_DELAY_PREFIX);
                break;
            case PREF_GETCURSORCAPSMODE_DELAY_PREFIX:
                testFieldOrDefault.mGetCursorCapsModeDelay =
                        readTestFieldInt(fieldId, PREF_GETCURSORCAPSMODE_DELAY_PREFIX);
                break;
            case PREF_GETEXTRACTEDTEXT_DELAY_PREFIX:
                testFieldOrDefault.mGetExtractedTextDelay =
                        readTestFieldInt(fieldId, PREF_GETEXTRACTEDTEXT_DELAY_PREFIX);
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

    private static AppLevelDefaults getTestFieldOrBase(int groupIndex, int fieldIndex,
                                                       Predicate<TestField> override) {
        TestField testField = getField(groupIndex, fieldIndex);
        if (!override.test(testField)) {
            return getInstance().mTestFieldDefaults;
        }
        return testField;
    }

    private static AppLevelDefaults getTestFieldOrBaseForTextInputModification(int groupIndex,
                                                                               int fieldIndex) {
        return getTestFieldOrBase(groupIndex, fieldIndex,
                testField -> testField.mOverrideTextInputModification);
    }

    private static AppLevelDefaults getTestFieldOrBaseForTextReturn(int groupIndex,
                                                                    int fieldIndex) {
        return getTestFieldOrBase(groupIndex, fieldIndex,
                testField -> testField.mOverrideTextReturn);
    }

    private static AppLevelDefaults getTestFieldOrBaseForTextComposition(int groupIndex,
                                                                         int fieldIndex) {
        return getTestFieldOrBase(groupIndex, fieldIndex,
                testField -> testField.mOverrideTextComposition);
    }

    private static AppLevelDefaults getTestFieldOrBaseForTargetVersion(int groupIndex,
                                                                       int fieldIndex) {
        return getTestFieldOrBase(groupIndex, fieldIndex,
                testField -> testField.mOverrideTargetVersion);
    }

    private static AppLevelDefaults getTestFieldOrBaseForSystemBehavior(int groupIndex,
                                                                        int fieldIndex) {
        return getTestFieldOrBase(groupIndex, fieldIndex,
                testField -> testField.mOverrideSystemBehavior);
    }

    public static boolean shouldModifyCommittedText(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextInputModification(groupIndex, fieldIndex)
                .mModifyCommittedText;
    }

    public static boolean shouldModifyComposedText(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextInputModification(groupIndex, fieldIndex)
                .mModifyComposedText;
    }

    public static boolean shouldModifyComposedChangesOnly(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextInputModification(groupIndex, fieldIndex)
                .mModifyComposedChangesOnly;
    }

    public static boolean shouldConsiderComposedChangesFromEnd(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextInputModification(groupIndex, fieldIndex)
                .mConsiderComposedChangesFromEnd;
    }

    public static boolean shouldRestrictToInclude(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextInputModification(groupIndex, fieldIndex)
                .mRestrictToInclude;
    }

    public static final String[] DEFAULT_RESTRICT_SPECIFIC = new String[0];

    private static String[] readRestrictSpecific(final SharedPreferenceManager prefs, int fieldId) {
        TextList<String> textList = (new TextListPreference.DataManager(prefs,
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

    public static String[] getRestrictSpecific(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextInputModification(groupIndex, fieldIndex)
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
        return (new CodepointRangeDialogPreference.DataManager(prefs,
                PREF_RESTRICT_RANGE_PREFIX + getSuffix(fieldId)))
                .readValue();
    }

    public static @Nullable IntRange getRestrictRange(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextInputModification(groupIndex, fieldIndex)
                .mRestrictRange;
    }

    public static final TranslateText[] DEFAULT_TRANSLATE_SPECIFIC = new TranslateText[0];

    private static TranslateText[] readTranslateSpecific(final SharedPreferenceManager prefs,
                                                         int fieldId) {
        TextList<TranslateText> textList =
                (new TextTranslateListPreference.DataManager(prefs,
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

    public static TranslateText[] getTranslateSpecific(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextInputModification(groupIndex, fieldIndex)
                .mTranslateSpecific;
    }

    public static boolean shouldTranslateFullMatchOnly(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextInputModification(groupIndex, fieldIndex)
                .mTranslateFullMatchOnly;
    }

    public static int getShiftCodepoint(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextInputModification(groupIndex, fieldIndex)
                .mShiftCodepoint;
    }

    public static boolean shouldSkipExtractingText(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextReturn(groupIndex, fieldIndex)
                .mSkipExtractingText;
    }

    public static boolean shouldIgnoreExtractedTextMonitor(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextReturn(groupIndex, fieldIndex)
                .mIgnoreExtractedTextMonitor;
    }

    public static boolean shouldUpdateSelectionBeforeExtractedText(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextReturn(groupIndex, fieldIndex)
                .mUpdateSelectionBeforeExtractedText;
    }

    public static boolean shouldUpdateExtractedTextOnlyOnNetChanges(int groupIndex,
                                                                    int fieldIndex) {
        return getTestFieldOrBaseForTextReturn(groupIndex, fieldIndex)
                .mUpdateExtractedTextOnlyOnNetChanges;
    }

    public static boolean shouldExtractFullText(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextReturn(groupIndex, fieldIndex)
                .mExtractFullText;
    }

    public static int getExtractMonitorTextLimit(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextReturn(groupIndex, fieldIndex)
                .mExtractMonitorTextLimit;
    }

    public static int getReturnedTextLimit(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextReturn(groupIndex, fieldIndex)
                .mReturnedTextLimit;
    }

    public static boolean shouldDeleteThroughComposingText(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextComposition(groupIndex, fieldIndex)
                .mDeleteThroughComposingText;
    }

    public static boolean shouldKeepEmptyComposingPosition(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextComposition(groupIndex, fieldIndex)
                .mKeepEmptyComposingPosition;
    }

    public static boolean shouldSkipTakeSnapshot(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTargetVersion(groupIndex, fieldIndex)
                .mSkipTakeSnapshot;
    }

    public static boolean shouldSkipGetSurroundingText(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTargetVersion(groupIndex, fieldIndex)
                .mSkipGetSurroundingText;
    }

    public static boolean shouldSkipPerformSpellCheck(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTargetVersion(groupIndex, fieldIndex)
                .mSkipPerformSpellCheck;
    }

    public static boolean shouldSkipSetImeConsumesInput(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTargetVersion(groupIndex, fieldIndex)
                .mSkipSetImeConsumesInput;
    }

    public static boolean shouldSkipCommitContent(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTargetVersion(groupIndex, fieldIndex)
                .mSkipCommitContent;
    }

    public static boolean shouldSkipCloseConnection(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTargetVersion(groupIndex, fieldIndex)
                .mSkipCloseConnection;
    }

    public static boolean shouldSkipDeleteSurroundingTextInCodePoints(int groupIndex,
                                                                      int fieldIndex) {
        return getTestFieldOrBaseForTargetVersion(groupIndex, fieldIndex)
                .mSkipDeleteSurroundingTextInCodePoints;
    }

    public static boolean shouldSkipRequestCursorUpdates(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTargetVersion(groupIndex, fieldIndex)
                .mSkipRequestCursorUpdates;
    }

    public static boolean shouldSkipCommitCorrection(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTargetVersion(groupIndex, fieldIndex)
                .mSkipCommitCorrection;
    }

    public static boolean shouldSkipGetSelectedText(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTargetVersion(groupIndex, fieldIndex)
                .mSkipGetSelectedText;
    }

    public static boolean shouldSkipSetComposingRegion(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTargetVersion(groupIndex, fieldIndex)
                .mSkipSetComposingRegion;
    }

    public static int getUpdateDelay(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForSystemBehavior(groupIndex, fieldIndex)
                .mUpdateDelay;
    }

    public static int getFinishComposingTextDelay(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForSystemBehavior(groupIndex, fieldIndex)
                .mFinishComposingTextDelay;
    }

    public static int getGetSurroundingTextDelay(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForSystemBehavior(groupIndex, fieldIndex)
                .mGetSurroundingTextDelay;
    }

    public static int getGetTextBeforeCursorDelay(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForSystemBehavior(groupIndex, fieldIndex)
                .mGetTextBeforeCursorDelay;
    }

    public static int getGetSelectedTextDelay(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForSystemBehavior(groupIndex, fieldIndex)
                .mGetSelectedTextDelay;
    }

    public static int getGetTextAfterCursorDelay(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForSystemBehavior(groupIndex, fieldIndex)
                .mGetTextAfterCursorDelay;
    }

    public static int getGetCursorCapsModeDelay(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForSystemBehavior(groupIndex, fieldIndex)
                .mGetCursorCapsModeDelay;
    }

    public static int getGetExtractedTextDelay(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForSystemBehavior(groupIndex, fieldIndex)
                .mGetExtractedTextDelay;
    }

    private static int[] readTestFieldGroupIds(final SharedPreferenceManager prefs) {
        int[] groupIds = prefs.getIntArray(PREF_TEST_GROUP_IDS, new int[0]);
        if (groupIds == null) {
            Log.e(TAG, "Group IDs is null");
            groupIds = new int[0];
        }
        return groupIds;
    }

    private static void setTestFieldGroupIds(Editor editor, int[] groupIds) {
        editor.putIntArray(PREF_TEST_GROUP_IDS, groupIds);
        getInstance().mTestGroupIds = deepCopy(groupIds);
    }

    private static int[] readTestGroupFieldIds(final SharedPreferenceManager prefs, int groupId) {
        int[] fieldIds = prefs.getIntArray(PREF_TEST_FIELD_IDS_PREFIX + GROUP_INFIX + groupId, new int[0]);
        if (fieldIds == null) {
            Log.e(TAG, "null field IDs for group " + groupId);
            fieldIds = new int[0];
        }
        return fieldIds;
    }

    private static void setTestGroupFieldIds(Editor editor, int groupId, int[] fieldIds) {
        editor.putIntArray(PREF_TEST_FIELD_IDS_PREFIX + GROUP_INFIX + groupId,
                fieldIds);
        getGroupById(groupId).mFieldIds = deepCopy(fieldIds);
    }

    public static void setTestGroupAndFieldIds(FieldIdGroup[] testGroups) {
        Editor editor = getInstance().mPrefs.edit();

        manageChangedTestGroups(editor, testGroups);

        int[] groupIds = new int[testGroups.length];
        for (int groupIndex = 0; groupIndex < testGroups.length; groupIndex++) {
            groupIds[groupIndex] = testGroups[groupIndex].mGroupId;
            setTestGroupFieldIds(editor, testGroups[groupIndex].mGroupId,
                    testGroups[groupIndex].mFieldIds);
        }
        setTestFieldGroupIds(editor, groupIds);

        editor.apply();
    }

    public static void setTestFieldGroupIds(int[] groupIds) {
        Editor editor = getInstance().mPrefs.edit();

        FieldIdGroup[] testGroups = new FieldIdGroup[groupIds.length];
        for (int groupIndex = 0; groupIndex < testGroups.length; groupIndex++) {
            testGroups[groupIndex] = new FieldIdGroup(groupIds[groupIndex],
                    isGroupIdValid(groupIds[groupIndex])
                            ? getGroupById(groupIds[groupIndex]).mFieldIds
                            : new int[0]);
        }
        manageChangedTestGroups(editor, testGroups);

        setTestFieldGroupIds(editor, groupIds);

        editor.apply();
    }

    public static void setTestGroupFieldIds(int groupIndex, int[] fieldIds) {
        Editor editor = getInstance().mPrefs.edit();

        FieldIdGroup[] testGroups = new FieldIdGroup[getTestFieldGroupCount()];
        for (int i = 0; i < testGroups.length; i++) {
            TestGroup group = getGroupByIndex(i);
            testGroups[i] = new FieldIdGroup(group.mId,
                    i == groupIndex ? fieldIds : group.mFieldIds);
        }
        manageChangedTestGroups(editor, testGroups);

        setTestGroupFieldIds(editor, getTestGroupId(groupIndex), fieldIds);

        editor.apply();
    }

    private static void manageChangedTestGroups(Editor editor, FieldIdGroup[] newTestGroups) {
        Settings settings = getInstance();

        Set<Integer> oldGroupIds = settings.mTestGroups.keySet();
        Set<Integer> newGroupIds = new HashSet<>();
        Set<Integer> oldFieldIds = settings.mTestFields.keySet();
        Set<Integer> newFieldIds = new HashSet<>();
        for (FieldIdGroup group : newTestGroups) {
            newGroupIds.add(group.mGroupId);
            for (int fieldIndex = 0; fieldIndex < group.mFieldIds.length; fieldIndex++) {
                newFieldIds.add(group.mFieldIds[fieldIndex]);
            }
        }

        Set<Integer> removedGroupIds = getRemovedIds(oldGroupIds, newGroupIds);
        Set<Integer> addedGroupIds = getAddedIds(oldGroupIds, newGroupIds);
        Set<Integer> removedFieldIds = getRemovedIds(oldFieldIds, newFieldIds);
        Set<Integer> addedFieldIds = getAddedIds(oldFieldIds, newFieldIds);

        // delete any groups that are getting removed
        for (int groupId : removedGroupIds) {
            removeTestGroupPrefs(editor, groupId);
        }

        // delete any fields that are getting removed
        for (int fieldId : removedFieldIds) {
            removeTestFieldPrefs(editor, fieldId);
        }

        // load the default values for any new groups
        for (int groupId : addedGroupIds) {
            deleteLingeringPrefs(GROUP_INFIX + groupId);

            settings.loadExistingGroup(groupId);
        }

        // load the default values for any new fields
        for (int fieldId : addedFieldIds) {
            deleteLingeringPrefs(FIELD_INFIX + fieldId);

            settings.loadExistingField(fieldId);
        }
    }

    private static Set<Integer> getRemovedIds(Set<Integer> originalIds, Set<Integer> updatedIds) {
        return getItemsUniqueToA(originalIds, updatedIds);
    }

    private static Set<Integer> getAddedIds(Set<Integer> originalIds, Set<Integer> updatedIds) {
        return getItemsUniqueToA(updatedIds, originalIds);
    }

    private static <T> Set<T> getItemsUniqueToA(Set<T> a, Set<T> b) {
        Set<T> itemsUniqueToA = new HashSet<>();
        for (T id : a) {
            if (!b.contains(id)) {
                itemsUniqueToA.add(id);
            }
        }
        return itemsUniqueToA;
    }

    private static void deleteLingeringPrefs(String prefSuffix) {
        SharedPreferenceManager prefs = getInstance().mPrefs;
        Map<String, ?> allPrefs = prefs.getAll();
        for (String prefKey : allPrefs.keySet()) {
            if (prefKey.endsWith(prefSuffix)) {
                Log.e(TAG, "cleaning up lingering preference: " + prefKey);
                prefs.remove(prefKey);
            }
        }
    }

    public static class FieldIdGroup {
        public final int mGroupId;
        public final int[] mFieldIds;

        public FieldIdGroup(int groupId, int[] fieldIds) {
            mGroupId = groupId;
            mFieldIds = fieldIds;
        }

        @Override
        public String toString() {
            return "{ groupId=" + mGroupId + ", fieldIds=" + Arrays.toString(mFieldIds) + "}";
        }
    }

    public static int getTestGroupId(int groupIndex) {
        return getInstance().mTestGroupIds[groupIndex];
    }

    private static TestGroup getGroupByIndex(int groupIndex) {
        int groupId = getTestGroupId(groupIndex);
        TestGroup group = getGroupById(groupId);
        if (group == null) {
            Log.e(TAG, "The object for group " + groupId + " (index " + groupIndex
                    + ") is missing");
            // we know the group should exist, so add it
            group = getInstance().loadExistingGroup(groupId);
        }
        return group;
    }

    private static TestGroup getGroupById(int groupId) {
        if (!isGroupIdValid(groupId)) {
            throw new IllegalArgumentException(
                    "Tried to get group " + groupId + ", which doesn't exist");
        }
        return getInstance().mTestGroups.get(groupId);
    }

    private static boolean isGroupIdValid(int groupId) {
        if (getInstance().mTestGroups.containsKey(groupId)) {
            return true;
        }
        // double check that the ID doesn't exist
        for (int id : getInstance().mTestGroupIds) {
            if (id == groupId) {
                Log.e(TAG, "The object for group " + groupId + " is missing");
                // add the missing group
                getInstance().loadExistingGroup(groupId);
                return true;
            }
        }
        return false;
    }

    public static int getTestFieldGroupCount() {
        return getInstance().mTestGroupIds.length;
    }

    public static int getTestFieldCount(int groupIndex) {
        return getGroupByIndex(groupIndex).mFieldIds.length;
    }

    public static String getTestFieldGroupName(int groupIndex) {
        return getGroupByIndex(groupIndex).mName;
    }

    public static int getTestFieldId(int groupIndex, int fieldIndex) {
        return getGroupByIndex(groupIndex).mFieldIds[fieldIndex];
    }

    private static TestField getField(int groupIndex, int fieldIndex) {
        int fieldId = getTestFieldId(groupIndex, fieldIndex);
        TestField field = getField(fieldId);
        if (field == null) {
            Log.e(TAG, "The object for field " + fieldId + " (group " + groupIndex
                    + ", field " + fieldIndex + ") is missing");
            // we know the field should exist, so add it
            field = getInstance().loadExistingField(fieldId);
        }
        return field;
    }

    private static TestField getField(int fieldId) {
        if (!isFieldIdValid(fieldId)) {
            throw new IllegalArgumentException(
                    "Tried to get field " + fieldId + ", which doesn't exist");
        }
        return getInstance().mTestFields.get(fieldId);
    }

    private static boolean isFieldIdValid(int fieldId) {
        if (getInstance().mTestFields.containsKey(fieldId)) {
            return true;
        }
        // double check that the ID doesn't exist in any of the groups
        for (int groupIndex = 0; groupIndex < getTestFieldGroupCount(); groupIndex++) {
            for (int id : getGroupByIndex(groupIndex).mFieldIds) {
                if (id == fieldId) {
                    Log.e(TAG, "The object for field " + fieldId + " is missing");
                    // add the missing field
                    getInstance().loadExistingField(fieldId);
                    return true;
                }
            }
        }
        return false;
    }

    public static void addTestFieldGroup() {
        Settings settings = getInstance();
        int groupId = getNextId(settings.mTestGroups.keySet());
        setTestFieldGroupIds(ArrayUtils.appendInt(settings.mTestGroupIds, groupId, true));
    }

    public static void removeTestFieldGroup(int groupIndex) {
        Settings settings = getInstance();
        setTestFieldGroupIds(ArrayUtils.removeIntAt(settings.mTestGroupIds, groupIndex));
    }

    public static void addTestField(int groupIndex) {
        Settings settings = getInstance();
        int fieldId = getNextId(settings.mTestFields.keySet());
        setTestGroupFieldIds(groupIndex,
                ArrayUtils.appendInt(getGroupByIndex(groupIndex).mFieldIds, fieldId, true));
    }

    public static void removeTestField(int groupIndex, int fieldIndex) {
        setTestGroupFieldIds(groupIndex,
                ArrayUtils.removeIntAt(getGroupByIndex(groupIndex).mFieldIds,fieldIndex));
    }

    private static void removeTestGroupPrefs(Editor editor, int groupId) {
        for (String prefKeyPrefix : TEST_GROUP_PREF_KEY_PREFIXES) {
            editor.remove(prefKeyPrefix + GROUP_INFIX + groupId);
        }
    }

    private static void removeTestFieldPrefs(Editor editor, int idToRemove) {
        for (String prefKeyPrefix : TEST_FIELD_PREF_KEY_PREFIXES) {
            editor.remove(prefKeyPrefix + FIELD_INFIX + idToRemove);
        }
        for (String prefKeyPrefix : DEFAULTABLE_TEST_FIELD_PREF_KEY_PREFIXES) {
            editor.remove(prefKeyPrefix + FIELD_INFIX + idToRemove);
        }
    }

    private static int getNextId(Iterable<Integer> existingIds) {
        int max = -1;
        for (int id : existingIds) {
            if (id > max) {
                max = id;
            }
        }
        return max + 1;
    }

    private static CharSequence readTestFieldLabelText(final SharedPreferenceManager prefs,
                                                       int fieldId) {
        return prefs.getCharSequence(PREF_IME_LABEL_TEXT_PREFIX + FIELD_INFIX + fieldId, null);
    }

    public static CharSequence getTestFieldLabelText(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mLabelText;
    }

    private static CharSequence readTestFieldDefaultText(final SharedPreferenceManager prefs,
                                                         int fieldId) {
        return prefs.getCharSequence(PREF_IME_DEFAULT_TEXT_PREFIX + FIELD_INFIX + fieldId, null);
    }

    public static CharSequence getTestFieldDefaultText(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mDefaultText;
    }

    private static CharSequence readTestFieldHintText(final SharedPreferenceManager prefs,
                                                      int fieldId) {
        return prefs.getCharSequence(PREF_IME_HINT_TEXT_PREFIX + FIELD_INFIX + fieldId, null);
    }

    public static CharSequence getTestFieldHintText(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mHintText;
    }

    private int readTestFieldInputType(int fieldId) {
        String inputTypeClass = readTestFieldString(fieldId, PREF_INPUT_TYPE_CLASS_PREFIX);
        String variation;
        int inputType;
        switch (inputTypeClass) {
            case "TYPE_NULL":
                inputType = InputType.TYPE_NULL;
                break;
            case "TYPE_CLASS_DATETIME":
                inputType = InputType.TYPE_CLASS_DATETIME;
                variation = readTestFieldString(fieldId, PREF_INPUT_TYPE_DATETIME_VARIATION_PREFIX);
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
                variation = readTestFieldString(fieldId, PREF_INPUT_TYPE_NUMBER_VARIATION_PREFIX);
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
                if (readTestFieldBoolean(fieldId, PREF_INPUT_TYPE_NUMBER_FLAG_SIGNED_PREFIX)) {
                    inputType |= InputType.TYPE_NUMBER_FLAG_SIGNED;
                }
                if (readTestFieldBoolean(fieldId, PREF_INPUT_TYPE_NUMBER_FLAG_DECIMAL_PREFIX)) {
                    inputType |= InputType.TYPE_NUMBER_FLAG_DECIMAL;
                }
                break;
            case "TYPE_CLASS_PHONE":
                inputType = InputType.TYPE_CLASS_PHONE;
                break;
            case "TYPE_CLASS_TEXT":
                inputType = InputType.TYPE_CLASS_TEXT;
                variation = readTestFieldString(fieldId, PREF_INPUT_TYPE_TEXT_VARIATION_PREFIX);
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
                String multiLineFlag =
                        readTestFieldString(fieldId, PREF_INPUT_TYPE_TEXT_FLAG_MULTI_LINE_PREFIX);
                switch (multiLineFlag) {
                    case "TYPE_TEXT_FLAG_MULTI_LINE":
                        inputType |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;
                        break;
                    case "TYPE_TEXT_FLAG_IME_MULTI_LINE":
                        inputType |= InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE;
                        break;
                }
                String capFlag = readTestFieldString(fieldId, PREF_INPUT_TYPE_TEXT_FLAG_CAP_PREFIX);
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
                if (readTestFieldBoolean(fieldId, PREF_INPUT_TYPE_TEXT_FLAG_AUTO_COMPLETE_PREFIX)) {
                    inputType |= InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE;
                }
                if (readTestFieldBoolean(fieldId, PREF_INPUT_TYPE_TEXT_FLAG_AUTO_CORRECT_PREFIX)) {
                    inputType |= InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
                }
                if (readTestFieldBoolean(fieldId,
                        PREF_INPUT_TYPE_TEXT_FLAG_NO_SUGGESTIONS_PREFIX)) {
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

    public static int getTestFieldInputType(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mInputType;
    }

    public static boolean getTestFieldNullInputTypeMultiline(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mNullInputTypeMultiline;
    }

    public static boolean getTestFieldCreateInputConnection(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mCreateInputConnection;
    }

    public static boolean getTestFieldSendSelectionInfo(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mSendSelectionInfo;
    }

    public static boolean getTestFieldSendText(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mSendText;
    }

    public static int getTestFieldComposingTextBehavior(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mComposingTextBehavior;
    }

    public static boolean getTestFieldAllowDeleteSurroundingText(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mAllowDeleteSurroundingText;
    }

    public static boolean getTestFieldAllowSettingSelection(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mAllowSettingSelection;
    }

    private int readTestFieldImeOptions(int fieldId) {
        String imeOptionsAction = readTestFieldString(fieldId, PREF_IME_OPTIONS_ACTION_PREFIX);
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
        if (readTestFieldBoolean(fieldId, PREF_IME_OPTIONS_FLAG_FORCE_ASCII_PREFIX)) {
            imeOptions |=  EditorInfo.IME_FLAG_FORCE_ASCII;
        }
        if (readTestFieldBoolean(fieldId, PREF_IME_OPTIONS_FLAG_NAVIGATE_NEXT_PREFIX)) {
            imeOptions |=  EditorInfo.IME_FLAG_NAVIGATE_NEXT;
        }
        if (readTestFieldBoolean(fieldId, PREF_IME_OPTIONS_FLAG_NAVIGATE_PREVIOUS_PREFIX)) {
            imeOptions |=  EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS;
        }
        if (readTestFieldBoolean(fieldId, PREF_IME_OPTIONS_FLAG_NO_ACCESSORY_ACTION_PREFIX)) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_ACCESSORY_ACTION;
        }
        if (readTestFieldBoolean(fieldId, PREF_IME_OPTIONS_FLAG_NO_ENTER_ACTION_PREFIX)) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        }
        if (readTestFieldBoolean(fieldId, PREF_IME_OPTIONS_FLAG_NO_EXTRACT_UI_PREFIX)) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        }
        if (readTestFieldBoolean(fieldId, PREF_IME_OPTIONS_FLAG_NO_FULLSCREEN_PREFIX)) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_FULLSCREEN;
        }
        if (readTestFieldBoolean(fieldId, PREF_IME_OPTIONS_FLAG_NO_PERSONALIZED_LEARNING_PREFIX)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        }
        return imeOptions;
    }

    public static int getTestFieldImeOptions(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mImeOptions;
    }

    public static int getTestFieldImeActionId(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mImeActionId;
    }

    public static String getTestFieldImeActionLabel(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mImeActionLabel;
    }

    public static String getTestFieldPrivateImeOptions(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mPrivateImeOptions;
    }

    public static boolean shouldTestFieldSelectAllOnFocus(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mSelectAllOnFocus;
    }

    public static int getTestFieldMaxLength(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mMaxLength;
    }

    public static boolean shouldTestFieldAllowUndo(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mAllowUndo;
    }

    private static Locale[] readTestFieldTextLocales(final SharedPreferenceManager prefs,
                                                     int fieldId) {
        return (new LocaleEntryListPreference.DataManager(prefs,
                PREF_TEXT_LOCALES_PREFIX + FIELD_INFIX + fieldId)).readValue();
    }

    public static Locale[] getTestFieldTextLocales(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mTextLocales;
    }

    private static Locale[] readTestFieldImeHintLocales(final SharedPreferenceManager prefs,
                                                        int fieldId) {
        return (new LocaleEntryListPreference.DataManager(prefs,
                PREF_IME_HINT_LOCALES_PREFIX + FIELD_INFIX + fieldId)).readValue();
    }

    public static Locale[] getTestFieldImeHintLocales(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mImeHintLocales;
    }

    public static final String THEME_SYSTEM_DEFAULT = "THEME_SYSTEM_DEFAULT";
    public static final String THEME_MATERIAL_DARK = "THEME_MATERIAL_DARK";
    public static final String THEME_MATERIAL_LIGHT = "THEME_MATERIAL_LIGHT";
    public static final String THEME_HOLO_DARK = "THEME_HOLO_DARK";
    public static final String THEME_HOLO_LIGHT = "THEME_HOLO_LIGHT";

    public static int getThemeId(final Context context) {
        return getThemeId(getInstance().mTheme, context);
    }

    public static int getThemeId(String theme, final Context context) {
        switch (theme) {
            case THEME_MATERIAL_DARK:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    return R.style.Theme_Material;
                }
                break;
            case THEME_MATERIAL_LIGHT:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    return R.style.Theme_Material_Light;
                }
                break;
            case THEME_HOLO_DARK:
                return R.style.Theme_Holo;
            case THEME_HOLO_LIGHT:
                return R.style.Theme_Holo_Light;
        }
        return isDarkModeEnabled(context)
                ? R.style.Theme_DeviceDefault
                : R.style.Theme_DeviceDefault_Light;
    }

    public static boolean isHoloTheme(int themeId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return true;
        }
        return themeId == R.style.Theme_Holo || themeId == R.style.Theme_Holo_Light;
    }

    private static boolean isDarkModeEnabled(final Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // prior to Lollipop Android just used a dark theme
            return true;
        }
        int nightModeFlags = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    public static boolean getShowReferenceEditText() {
        return getInstance().mShowReferenceEditText;
    }

    private static class TestGroup {
        private final int mId;

        public String mName;
        public int[] mFieldIds;
        public TestGroup(int id, String name, int[] fieldIds) {
            if (id < 0) {
                throw new IllegalArgumentException(
                        "The group id can't be negative (" + id + ")");
            }
            mId = id;
            mName = name;
            mFieldIds = fieldIds;
        }

        @Override
        public String toString() {
            return "{ mId=" + mId + ", mName=" + (mName == null ? "null" : "\"" + mName + "\"")
                    + ", mFieldIds=" + Arrays.toString(mFieldIds) + " }";
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
        private CharSequence mLabelText;
        private CharSequence mDefaultText;
        private CharSequence mHintText;

        private boolean mOverrideTextInputModification;
        private boolean mOverrideTextReturn;
        private boolean mOverrideTextComposition;
        private boolean mOverrideTargetVersion;
        private boolean mOverrideSystemBehavior;

        public TestField(int id) {
            if (id < 0) {
                throw new IllegalArgumentException(
                        "The field id can't be negative (" + id + ")");
            }
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

    public static EditorSettings getTestFieldSettings(int groupIndex, int fieldIndex) {
        return new FieldPrefEditorSettings(groupIndex, fieldIndex);
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
        //TODO: (EW) would it be better to use the field ID instead of the index?
        private final int mGroupIndex;
        private final int mFieldIndex;

        private FieldPrefEditorSettings(int groupIndex, int fieldIndex) {
            mGroupIndex = groupIndex;
            mFieldIndex = fieldIndex;
        }

        @Override
        public boolean nullInputTypeMultiline() {
            return Settings.getTestFieldNullInputTypeMultiline(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldCreateInputConnection() {
            return Settings.getTestFieldCreateInputConnection(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldSendSelectionInfo() {
            return Settings.getTestFieldSendSelectionInfo(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldSendText() {
            return Settings.getTestFieldSendText(mGroupIndex, mFieldIndex);
        }

        @Override
        public int composingTextBehavior() {
            return Settings.getTestFieldComposingTextBehavior(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean allowDeleteSurroundingText() {
            return Settings.getTestFieldAllowDeleteSurroundingText(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean allowSettingSelection() {
            return Settings.getTestFieldAllowSettingSelection(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldModifyCommittedText() {
            return Settings.shouldModifyCommittedText(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldModifyComposedText() {
            return Settings.shouldModifyComposedText(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldModifyComposedChangesOnly() {
            return Settings.shouldModifyComposedChangesOnly(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldConsiderComposedChangesFromEnd() {
            return Settings.shouldConsiderComposedChangesFromEnd(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldRestrictToInclude() {
            return Settings.shouldRestrictToInclude(mGroupIndex, mFieldIndex);
        }

        @Override
        public String[] getRestrictSpecific() {
            return Settings.getRestrictSpecific(mGroupIndex, mFieldIndex);
        }

        @Override
        public @Nullable IntRange getRestrictRange() {
            return Settings.getRestrictRange(mGroupIndex, mFieldIndex);
        }

        @Override
        public TranslateText[] getTranslateSpecific() {
            return Settings.getTranslateSpecific(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldTranslateFullMatchOnly() {
            return Settings.shouldTranslateFullMatchOnly(mGroupIndex, mFieldIndex);
        }

        @Override
        public int getCodepointShift() {
            return Settings.getShiftCodepoint(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldSkipExtractingText() {
            return Settings.shouldSkipExtractingText(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldIgnoreExtractedTextMonitor() {
            return Settings.shouldIgnoreExtractedTextMonitor(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldUpdateSelectionBeforeExtractedText() {
            return Settings.shouldUpdateSelectionBeforeExtractedText(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldUpdateExtractedTextOnlyOnNetChanges() {
            return Settings.shouldUpdateExtractedTextOnlyOnNetChanges(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldExtractFullText() {
            return Settings.shouldExtractFullText(mGroupIndex, mFieldIndex);
        }

        @Override
        public int getExtractMonitorTextLimit() {
            return Settings.getExtractMonitorTextLimit(mGroupIndex, mFieldIndex);
        }

        @Override
        public int getReturnedTextLimit() {
            return Settings.getReturnedTextLimit(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldDeleteThroughComposingText() {
            return Settings.shouldDeleteThroughComposingText(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldKeepEmptyComposingPosition() {
            return Settings.shouldKeepEmptyComposingPosition(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldSkipTakeSnapshot() {
            return Settings.shouldSkipTakeSnapshot(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldSkipGetSurroundingText() {
            return Settings.shouldSkipGetSurroundingText(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldSkipPerformSpellCheck() {
            return Settings.shouldSkipPerformSpellCheck(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldSkipSetImeConsumesInput() {
            return Settings.shouldSkipSetImeConsumesInput(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldSkipCommitContent() {
            return Settings.shouldSkipCommitContent(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldSkipCloseConnection() {
            return Settings.shouldSkipCloseConnection(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldSkipDeleteSurroundingTextInCodePoints() {
            return Settings.shouldSkipDeleteSurroundingTextInCodePoints(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldSkipRequestCursorUpdates() {
            return Settings.shouldSkipRequestCursorUpdates(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldSkipCommitCorrection() {
            return Settings.shouldSkipCommitCorrection(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldSkipGetSelectedText() {
            return Settings.shouldSkipGetSelectedText(mGroupIndex, mFieldIndex);
        }

        @Override
        public boolean shouldSkipSetComposingRegion() {
            return Settings.shouldSkipSetComposingRegion(mGroupIndex, mFieldIndex);
        }

        @Override
        public int getUpdateDelay() {
            return Settings.getUpdateDelay(mGroupIndex, mFieldIndex);
        }

        @Override
        public int getFinishComposingTextDelay() {
            return Settings.getFinishComposingTextDelay(mGroupIndex, mFieldIndex);
        }

        @Override
        public int getGetSurroundingTextDelay() {
            return Settings.getGetSurroundingTextDelay(mGroupIndex, mFieldIndex);
        }

        @Override
        public int getGetTextBeforeCursorDelay() {
            return Settings.getGetTextBeforeCursorDelay(mGroupIndex, mFieldIndex);
        }

        @Override
        public int getGetSelectedTextDelay() {
            return Settings.getGetSelectedTextDelay(mGroupIndex, mFieldIndex);
        }

        @Override
        public int getGetTextAfterCursorDelay() {
            return Settings.getGetTextAfterCursorDelay(mGroupIndex, mFieldIndex);
        }

        @Override
        public int getGetCursorCapsModeDelay() {
            return Settings.getGetCursorCapsModeDelay(mGroupIndex, mFieldIndex);
        }

        @Override
        public int getGetExtractedTextDelay() {
            return Settings.getGetExtractedTextDelay(mGroupIndex, mFieldIndex);
        }
    }

    private static final boolean EXPORT_UNSET_PREFS = false;
    private static final boolean EXPORT_DEFAULT_PREFS_VALUES = false;
    private static final boolean IMPORT_DEFAULT_PREFS_VALUES = false;

    private static final String FIELD_DEFAULTS_JSON_PROP = "fieldDefaults";
    private static final String GROUPS_JSON_PROP = "groups";
    private static final String FIELDS_JSON_PROP = "fields";
    private static final String OTHER_SETTINGS_JSON_PROP = "other";
    private static final String TEXT_LIST_ESCAPE_CHARS_JSON_PROP = "escapeChars";
    private static final String TEXT_LIST_DATA_ARRAY_JSON_PROP = "dataArray";
    private static final String TRANSLATE_TEXT_ORIGINAL_JSON_PROP = "original";
    private static final String TRANSLATE_TEXT_TRANSLATION_JSON_PROP = "translation";

    public static String getJson(boolean exportFieldDefaults, List<GroupInfo> groupInfoList,
                                 boolean embedFieldDefaults, boolean exportOtherSettings) {
        SharedPreferenceManager prefs = getInstance().mPrefs;
        JSONObject jsonObject = new JSONObject();
        try {
            if (groupInfoList != null && prefs.contains(PREF_TEST_GROUP_IDS)) {
                int[] groupIds = readTestFieldGroupIds(prefs);

                if (IterableUtils.any(groupInfoList, groupInfo -> groupInfo.mInclude)
                        || !IterableUtils.any(groupInfoList,
                                groupInfo -> IterableUtils.any(groupInfo.mFields,
                                        fieldInfo -> fieldInfo.mInclude))) {
                    // either at least one group was flagged to include or there are no fields and
                    // no groups to include
                    JSONArray groupsJsonArray = new JSONArray();
                    for (int groupIndex = 0; groupIndex < groupIds.length; groupIndex++) {
                        int groupId = groupIds[groupIndex];
                        GroupInfo groupInfo = groupInfoList.get(groupIndex);
                        if (groupInfoList.get(groupIndex).mInclude) {
                            groupsJsonArray.put(
                                    getGroupJson(groupId, groupInfo, embedFieldDefaults, prefs));
                        }
                    }
                    jsonObject.put(GROUPS_JSON_PROP, groupsJsonArray);
                } else {
                    // there are no groups flagged to include, so skip the groups themselves and
                    // just collect the fields that are flagged to include from any group
                    JSONArray looseFieldsJsonArray = new JSONArray();
                    for (int groupIndex = 0; groupIndex < groupIds.length; groupIndex++) {
                        int groupId = groupIds[groupIndex];
                        GroupInfo groupInfo = groupInfoList.get(groupIndex);
                        if (prefs.contains(PREF_TEST_FIELD_IDS_PREFIX + GROUP_INFIX + groupId)) {
                            addGroupFieldsJson(looseFieldsJsonArray, groupId, groupInfo,
                                    embedFieldDefaults, prefs);
                        }
                    }
                    jsonObject.put(FIELDS_JSON_PROP, looseFieldsJsonArray);
                }
            }

            //TODO: (EW) consider moving this up to match the settings screen and dialog
            if (exportFieldDefaults) {
                JSONObject fieldDefaultsJsonObject = new JSONObject();
                for (String defaultsPrefKeyPrefix : DEFAULTABLE_TEST_FIELD_PREF_KEY_PREFIXES) {
                    String defaultsPrefKey = defaultsPrefKeyPrefix + BASE_SUFFIX;
                    addPrefData(fieldDefaultsJsonObject, defaultsPrefKeyPrefix, defaultsPrefKey,
                            prefs);
                }
                jsonObject.put(FIELD_DEFAULTS_JSON_PROP, fieldDefaultsJsonObject);
            }

            if (exportOtherSettings) {
                JSONObject otherSettingsJsonObject = new JSONObject();
                for (String miscPrefKey : MISC_PREF_KEYS) {
                    addPrefData(otherSettingsJsonObject, miscPrefKey, miscPrefKey, prefs);
                }
                jsonObject.put(OTHER_SETTINGS_JSON_PROP, otherSettingsJsonObject);
            }
        } catch (JSONException | IllegalArgumentException e) {
            Log.e(TAG, "Failed to build settings JSON: " + e.getMessage());
            return null;
        }
        return jsonObject.toString();
    }

    private static JSONObject getGroupJson(int groupId, GroupInfo groupInfo,
                                           boolean embedFieldDefaults,
                                           SharedPreferenceManager prefs)
            throws JSONException {
        JSONObject groupJsonObject = new JSONObject();

        for (String groupPrefKeyPrefix : TEST_GROUP_PREF_KEY_PREFIXES) {
            String groupPrefKey = groupPrefKeyPrefix + GROUP_INFIX + groupId;

            if (groupPrefKeyPrefix.equals(PREF_TEST_FIELD_IDS_PREFIX)) {
                if (prefs.contains(groupPrefKey)) {
                    JSONArray fieldsJsonArray = new JSONArray();

                    addGroupFieldsJson(fieldsJsonArray, groupId, groupInfo, embedFieldDefaults,
                            prefs);

                    groupJsonObject.put(FIELDS_JSON_PROP, fieldsJsonArray);
                }
            } else {
                addPrefData(groupJsonObject, groupPrefKeyPrefix, groupPrefKey, prefs);
            }
        }

        return groupJsonObject;
    }

    private static void addGroupFieldsJson(JSONArray fieldsJsonArray, int groupId,
                                           GroupInfo groupInfo, boolean embedFieldDefaults,
                                           SharedPreferenceManager prefs)
            throws JSONException {
        int[] fieldIds = readTestGroupFieldIds(prefs, groupId);
        for (int fieldIndex = 0; fieldIndex < fieldIds.length; fieldIndex++) {
            int fieldId = fieldIds[fieldIndex];
            if (!groupInfo.mFields.get(fieldIndex).mInclude) {
                continue;
            }
            fieldsJsonArray.put(getFieldJson(fieldId, embedFieldDefaults, prefs));
        }
    }

    private static JSONObject getFieldJson(int fieldId, boolean embedFieldDefaults,
                                           SharedPreferenceManager prefs)
            throws JSONException {
        JSONObject fieldJsonObject = new JSONObject();

        for (String fieldPrefKeyPrefix : TEST_FIELD_PREF_KEY_PREFIXES) {
            String fieldPrefKey = fieldPrefKeyPrefix + FIELD_INFIX + fieldId;

            if (DEFAULT_OVERRIDE_PREF_PREFIX_MAP.containsKey(fieldPrefKeyPrefix)) {
                String suffix;
                // embed defaults if requested and the field doesn't already override them,
                // otherwise just load the override values
                if (embedFieldDefaults
                        && !prefs.getBoolean(fieldPrefKey,
                                getPrefDefaultBoolean(fieldPrefKeyPrefix))) {
                    suffix = BASE_SUFFIX;
                    String jsonPropName = prefKeyPrefixToJsonName(fieldPrefKeyPrefix);
                    fieldJsonObject.put(jsonPropName, true);
                } else {
                    suffix = FIELD_INFIX + fieldId;
                    addPrefData(fieldJsonObject, fieldPrefKeyPrefix, fieldPrefKey, prefs);
                }
                String[] fieldDefaultsPrefKeys =
                        DEFAULT_OVERRIDE_PREF_PREFIX_MAP.get(fieldPrefKeyPrefix);
                for (String fieldDefaultPrefKeyPrefix : fieldDefaultsPrefKeys) {
                    String fieldDefaultPrefKey = fieldDefaultPrefKeyPrefix + suffix;
                    addPrefData(fieldJsonObject, fieldDefaultPrefKeyPrefix, fieldDefaultPrefKey,
                            prefs);
                }
            } else {
                addPrefData(fieldJsonObject, fieldPrefKeyPrefix, fieldPrefKey, prefs);
            }
        }

        return fieldJsonObject;
    }

    //TODO: (EW) probably should break this into multiple helper methods
    private static void addPrefData(JSONObject jsonObject, String prefKeyOrPrefix, String prefKey,
                                    SharedPreferenceManager prefs) throws JSONException {
        if (!EXPORT_UNSET_PREFS && !prefs.contains(prefKey)) {
            return;
        }
        String jsonPropName = prefKeyPrefixToJsonName(prefKeyOrPrefix);
        int dataType = prefDataType(prefKeyOrPrefix);
        switch (dataType) {
            case TYPE_BOOLEAN:
                boolean defaultBoolean = getPrefDefaultBoolean(prefKeyOrPrefix);
                boolean prefValueBoolean = prefs.getBoolean(prefKey, defaultBoolean);
                if (EXPORT_DEFAULT_PREFS_VALUES || prefValueBoolean != defaultBoolean) {
                    jsonObject.put(jsonPropName, prefValueBoolean);
                }
                break;
            case TYPE_INT:
                int defaultInt = getPrefDefaultInt(prefKeyOrPrefix);
                int prefValueInt = prefs.getInt(prefKey, defaultInt);
                if (EXPORT_DEFAULT_PREFS_VALUES || prefValueInt != defaultInt) {
                    jsonObject.put(jsonPropName, prefValueInt);
                }
                break;
            case TYPE_LONG:
                long defaultLong = getPrefDefaultLong(prefKeyOrPrefix);
                long prefValueLong = prefs.getLong(prefKey, defaultLong);
                if (EXPORT_DEFAULT_PREFS_VALUES || prefValueLong != defaultLong) {
                    jsonObject.put(jsonPropName, prefValueLong);
                }
                break;
            case TYPE_FLOAT:
                float defaultFloat = getPrefDefaultFloat(prefKeyOrPrefix);
                float prefValueFloat = prefs.getFloat(prefKey, defaultFloat);
                if (EXPORT_DEFAULT_PREFS_VALUES || prefValueFloat != defaultFloat) {
                    jsonObject.put(jsonPropName, prefValueFloat);
                }
                break;
            case TYPE_STRING:
                String defaultString = getPrefDefaultString(prefKeyOrPrefix);
                String prefValueString = prefs.getString(prefKey, defaultString);
                if (EXPORT_DEFAULT_PREFS_VALUES
                        || !TextUtils.equals(prefValueString, defaultString)) {
                    addObject(jsonObject, jsonPropName, prefValueString);
                }
                break;
            case TYPE_SPANNED:
                Spanned defaultSpanned = getPrefDefaultSpanned(prefKeyOrPrefix);
                Spanned prefValueSpanned = prefs.getSpanned(prefKey, defaultSpanned);
                if (EXPORT_DEFAULT_PREFS_VALUES
                        || !definitelyEqual(prefValueSpanned, defaultSpanned)) {
                    // get the data that SharedPreferenceManager uses to save spanned objects
                    addArray(jsonObject, jsonPropName, prefValueSpanned == null
                            ? null
                            : SharedPreferenceManager.getSpannedInfo(prefValueSpanned));
                }
                break;
            case TYPE_CHAR_SEQUENCE:
                CharSequence defaultCharSequence = getPrefDefaultCharSequence(prefKeyOrPrefix);
                CharSequence prefValueCharSequence =
                        prefs.getCharSequence(prefKey, defaultCharSequence);
                if (EXPORT_DEFAULT_PREFS_VALUES
                        || !definitelyEqual(prefValueCharSequence, defaultCharSequence)) {
                    if (prefValueCharSequence instanceof Spanned) {
                        // get the data that SharedPreferenceManager uses to save spanned objects
                        //TODO: (EW) possibly should embed some indication of what data this holds
                        // so that if other supported CharSequence type are supported in the future
                        // or we find a better way to export the data, we can maintain compatibility
                        // between varying versions between the exporting and importing app.
                        addArray(jsonObject, jsonPropName,
                                SharedPreferenceManager.getSpannedInfo(
                                        (Spanned) prefValueCharSequence));
                    } else if (prefValueCharSequence == null
                            || prefValueCharSequence instanceof String) {
                        addObject(jsonObject, jsonPropName, prefValueCharSequence);
                    } else {
                        jsonObject.put(jsonPropName, prefValueCharSequence.toString());
                    }
                }
                break;
            case TYPE_INT_ARRAY:
                int[] defaultIntArray = getPrefDefaultIntArray(prefKeyOrPrefix);
                int[] prefValueIntArray = prefs.getIntArray(prefKey, defaultIntArray);
                if (EXPORT_DEFAULT_PREFS_VALUES
                        || !Arrays.equals(prefValueIntArray, defaultIntArray)) {
                    addArray(jsonObject, jsonPropName, prefValueIntArray);
                }
                break;
            case TYPE_STRING_ARRAY:
                String[] defaultStringArray = getPrefDefaultStringArray(prefKeyOrPrefix);
                String[] prefValueStringArray = prefs.getStringArray(prefKey, defaultStringArray);
                if (EXPORT_DEFAULT_PREFS_VALUES
                        || !Arrays.equals(prefValueStringArray, defaultStringArray)) {
                    addArray(jsonObject, jsonPropName, prefValueStringArray);
                }
                break;
            case TYPE_INT_RANGE:
                //TODO: (EW) make more generic. the only use case for the int range currently is the
                // codepoint range preference. maybe just convert this preference to use an int
                // array and just have extra validation on the length when reading the data.
                CodepointRangeDialogPreference.DataManager codepointRangeDialogDataManager =
                        new CodepointRangeDialogPreference.DataManager(prefs, prefKey);
                IntRange defaultIntRange = codepointRangeDialogDataManager.readDefaultValue();
                IntRange prefValueIntRange = codepointRangeDialogDataManager.readValue();
                if (EXPORT_DEFAULT_PREFS_VALUES
                        || !Objects.equals(prefValueIntRange, defaultIntRange)) {
                    addArray(jsonObject, jsonPropName, prefValueIntRange == null
                            ? null
                            : new int[] {
                                    prefValueIntRange.getStart(),
                                    prefValueIntRange.getEnd()
                            });
                }
                break;
            case TYPE_LOCALE_ARRAY:
                //TODO: (EW) possibly could be more generic (or at least decoupled from the specific
                // preference)
                LocaleEntryListPreference.DataManager localeEntryListDataManager =
                        new LocaleEntryListPreference.DataManager(prefs, prefKey);
                Locale[] defaultLocaleArray = localeEntryListDataManager.readDefaultValue();
                Locale[] prefValueLocaleArray = localeEntryListDataManager.readValue();
                if (EXPORT_DEFAULT_PREFS_VALUES
                        || !Arrays.equals(prefValueLocaleArray, defaultLocaleArray)) {
                    String[] localeStrings = new String[prefValueLocaleArray.length];
                    for (int i = 0; i < prefValueLocaleArray.length; i++) {
                        localeStrings[i] = LocaleEntryListPreference.getLocaleString(
                                prefValueLocaleArray[i]);
                    }
                    addArray(jsonObject, jsonPropName, localeStrings);
                }
                break;
            case TYPE_TEXT_LIST_STRING:
                //TODO: (EW) possibly could be more generic (or at least decoupled from the specific
                // preference)
                TextListPreference.DataManager textListDataManager =
                        new TextListPreference.DataManager(prefs,prefKey);
                TextList<String> defaultTextListString = textListDataManager.readDefaultValue();
                TextList<String> prefValueTextListString = textListDataManager.readValue();
                if (EXPORT_DEFAULT_PREFS_VALUES
                        || !Objects.equals(prefValueTextListString, defaultTextListString)) {
                    JSONObject textListStringJsonObject = new JSONObject();
                    textListStringJsonObject.put(TEXT_LIST_ESCAPE_CHARS_JSON_PROP,
                            prefValueTextListString.escapeChars());
                    addArray(textListStringJsonObject, TEXT_LIST_DATA_ARRAY_JSON_PROP,
                            prefValueTextListString.getDataArray());
                    jsonObject.put(jsonPropName, textListStringJsonObject);
                }
                break;
            case TYPE_TEXT_LIST_TRANSLATE_TEXT:
                //TODO: (EW) possibly could be more generic (or at least decoupled from the specific
                // preference)
                TextTranslateListPreference.DataManager textTranslateListDataManager =
                        new TextTranslateListPreference.DataManager(prefs, prefKey);
                TextList<TranslateText> defaultTextListTranslateText =
                        textTranslateListDataManager.readDefaultValue();
                TextList<TranslateText> prefValueTextListTranslateText =
                        textTranslateListDataManager.readValue();
                if (EXPORT_DEFAULT_PREFS_VALUES
                        || !Objects.equals(prefValueTextListTranslateText,
                                defaultTextListTranslateText)) {
                    JSONObject translateTextJsonObject = new JSONObject();
                    translateTextJsonObject.put(TEXT_LIST_ESCAPE_CHARS_JSON_PROP,
                            prefValueTextListTranslateText.escapeChars());
                    JSONObject[] translateTextArray =
                            new JSONObject[prefValueTextListTranslateText.getDataArray().length];
                    for (int i = 0; i < translateTextArray.length; i++) {
                        translateTextArray[i] = new JSONObject();
                        translateTextArray[i].put(TRANSLATE_TEXT_ORIGINAL_JSON_PROP,
                                prefValueTextListTranslateText.getDataArray()[i].getOriginal());
                        translateTextArray[i].put(TRANSLATE_TEXT_TRANSLATION_JSON_PROP,
                                prefValueTextListTranslateText.getDataArray()[i].getTranslation());
                    }
                    addArray(translateTextJsonObject, TEXT_LIST_DATA_ARRAY_JSON_PROP,
                            translateTextArray);
                    jsonObject.put(jsonPropName, translateTextJsonObject);
                }
                break;
            case TYPE_UNKNOWN:
            default:
                //TODO: (EW) probably handle gracefully, but hard crash for now to catch issues
                throw new RuntimeException("Unknown data type for " + prefKeyOrPrefix);
        }
    }

    private static <T> void addArray(JSONObject jsonObject, String jsonPropName, T[] data)
            throws JSONException {
        if (data == null) {
            jsonObject.put(jsonPropName, JSONObject.NULL);
        } else {
            JSONArray jsonArray = new JSONArray();
            for (T value : data) {
                jsonArray.put(value);
            }
            jsonObject.put(jsonPropName, jsonArray);
        }
    }

    private static void addArray(JSONObject jsonObject, String jsonPropName, int[] data)
            throws JSONException {
        if (data == null) {
            jsonObject.put(jsonPropName, JSONObject.NULL);
        } else {
            JSONArray jsonArray = new JSONArray();
            for (int value : data) {
                jsonArray.put(value);
            }
            jsonObject.put(jsonPropName, jsonArray);
        }
    }

    private static void addObject(JSONObject jsonObject, String jsonPropName, Object data)
            throws JSONException {
        if (data == null) {
            jsonObject.put(jsonPropName, JSONObject.NULL);
        } else {
            jsonObject.put(jsonPropName, data);
        }
    }

    private static String prefKeyPrefixToJsonName(String prefKeyOrKeyPrefix) {
        int start = prefKeyOrKeyPrefix.startsWith(PREF_KEY_PREFIX) ? PREF_KEY_PREFIX.length() : 0;
        String coreName = prefKeyOrKeyPrefix.substring(start);
        return snakeCaseToCamelCase(coreName);
    }

    private static String snakeCaseToCamelCase(String name) {
        if (!name.matches("[a-z](?:[a-z]|\\d)*(?:_(?:[a-z]|\\d)+)*")) {
            throw new IllegalArgumentException(name + " isn't valid snake case");
        }
        int nextToAdd = 0;
        int nextUnderscore;
        StringBuilder sb = new StringBuilder();
        while ((nextUnderscore = name.indexOf('_', nextToAdd)) >= 0) {
            // add any text before the underscore that hasn't already been added/adjusted yet
            sb.append(name.substring(nextToAdd, nextUnderscore));
            nextToAdd = nextUnderscore + 1;

            if (nextUnderscore + 1 < name.length()) {
                char c = name.charAt(nextUnderscore + 1);
                if (c >= 'a' && c <= 'z') {
                    // convert the character after the underscore to uppercase
                    sb.append((char) (c - 'a' + 'A'));
                    nextToAdd++;
                } else if (c >= '0' && c <= '9') {
                    // add the number without adjustment
                    sb.append(c);
                    nextToAdd++;
                }
            }
        }
        if (nextToAdd < name.length()) {
            sb.append(name.substring(nextToAdd));
        }
        return sb.toString();
    }

    // modified from TextUtils#equals since Spanned objects generally don't compare well (see
    // https://stackoverflow.com/a/46403431)
    private static boolean definitelyEqual(CharSequence a, CharSequence b) {
        if (a == b) return true;
        if (a != null && b != null && a.length() == b.length()) {
            if (a instanceof String && b instanceof String) {
                return a.equals(b);
            } else {
                // these may be equal, but we may not be able to be completely sure
                return false;
            }
        }
        return false;
    }

    public static class ImportFileInfo {
        private String mError;
        private final List<String> mWarnings = new ArrayList<>();
        private final List<String> mUnexpectedProps = new ArrayList<>();
        private JSONObject mJsonObject;
        private List<GroupInfo> mGroups;
        private boolean mIsFieldDefaultsIncluded;
        private boolean mIsOtherSettingsIncluded;

        public String getError() {
            return mError;
        }

        public List<String> getWarnings() {
            return mWarnings;
        }

        public List<String> getUnexpectedProps() {
            return mUnexpectedProps;
        }

        public JSONObject getJsonObject() {
            return mJsonObject;
        }

        public List<GroupInfo> getGroups() {
            return mGroups;
        }

        public boolean isFieldDefaultsIncluded() {
            return mIsFieldDefaultsIncluded;
        }

        public boolean isOtherSettingsIncluded() {
            return mIsOtherSettingsIncluded;
        }
    }
    //TODO: (EW) don't make properties public
    public static class GroupInfo {
        public List<FieldInfo> mFields;
        public String mName;
        public boolean mInclude;
        public boolean mIsAdHoc;
    }
    public static class FieldInfo {
        public String mName;
        public boolean mInclude;
    }

    private static Set<String> getProps(JSONObject jsonObject) {
        Set<String> props = new HashSet<>();
        Iterator<String> keys = jsonObject.keys();
        while(keys.hasNext()) {
            String key = keys.next();
            props.add(key);
        }
        return props;
    }

    private static JSONObject getJsonObject(JSONObject jsonObject, String propName)
            throws JSONException {
        return jsonObject.has(propName)
                ? jsonObject.getJSONObject(propName)
                : null;
    }

    public static ImportFileInfo validateJson(String rawJson, Context context) {
        ImportFileInfo info = new ImportFileInfo();

        try {
            info.mJsonObject = new JSONObject(rawJson);
            Set<String> props = getProps(info.mJsonObject);

            //TODO: (EW) see if there is a good way to reduce duplicate code with tracking the
            // unexpected properties
            if (props.contains(GROUPS_JSON_PROP)) {
                props.remove(GROUPS_JSON_PROP);

                List<GroupInfo> groups = new ArrayList<>();
                JSONArray groupsJsonArray = info.mJsonObject.getJSONArray(GROUPS_JSON_PROP);
                for (int i = 0; i < groupsJsonArray.length(); i++) {
                    GroupInfo groupInfo = new GroupInfo();
                    JSONObject groupJsonObject = groupsJsonArray.getJSONObject(i);
                    if (!validateGroupJson(groupJsonObject, info, i,
                            GROUPS_JSON_PROP + "[" + i + "]",
                            context, groupInfo)) {
                        return info;
                    }
                    groups.add(groupInfo);
                }
                info.mGroups = groups;
            } else if (props.contains(FIELDS_JSON_PROP)) {
                props.remove(FIELDS_JSON_PROP);

                List<GroupInfo> groups = new ArrayList<>();

                GroupInfo groupInfo = new GroupInfo();

                // build the ad-hoc group to load
                JSONObject groupJsonObject = new JSONObject();
                JSONArray looseFieldsJsonArray = info.mJsonObject.getJSONArray(FIELDS_JSON_PROP);
                groupJsonObject.put(FIELDS_JSON_PROP, looseFieldsJsonArray);

                if (!validateGroupJson(groupJsonObject, info, 0, null,
                        context, groupInfo)) {
                    return info;
                }
                groupInfo.mIsAdHoc = true;
                groupInfo.mName = context.getText(R.string.ad_hoc_import_group_name).toString();
                groups.add(groupInfo);

                info.mGroups = groups;
            }

            if (props.contains(FIELD_DEFAULTS_JSON_PROP)) {
                props.remove(FIELD_DEFAULTS_JSON_PROP);

                JSONObject fieldDefaultsJsonObject =
                        info.mJsonObject.getJSONObject(FIELD_DEFAULTS_JSON_PROP);
                if (!validateGroupedSettingsJson(DEFAULTABLE_TEST_FIELD_PREF_KEY_PREFIXES,
                        fieldDefaultsJsonObject, info, FIELD_DEFAULTS_JSON_PROP, context)) {
                    return info;
                }
                info.mIsFieldDefaultsIncluded = true;
            }

            if (props.contains(OTHER_SETTINGS_JSON_PROP)) {
                props.remove(OTHER_SETTINGS_JSON_PROP);

                JSONObject otherSettingsJsonObject =
                        info.mJsonObject.getJSONObject(OTHER_SETTINGS_JSON_PROP);
                if (!validateGroupedSettingsJson(MISC_PREF_KEYS,
                        otherSettingsJsonObject, info, OTHER_SETTINGS_JSON_PROP, context)) {
                    return info;
                }
                info.mIsOtherSettingsIncluded = true;
            }

            info.mUnexpectedProps.addAll(props);
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
            info.mError = context.getString(R.string.failed_to_parse_import_file);
            return info;
        }

        return info;
    }

    private static boolean validateGroupJson(JSONObject groupJsonObject, ImportFileInfo info,
                                             int groupIndex, String path, Context context,
                                             GroupInfo groupInfo)
            throws JSONException {
        Map<String, String> namesMap = new HashMap<>();
        List<FieldInfo> fields = new ArrayList<>();

        Set<String> props = getProps(groupJsonObject);

        if (props.contains(FIELDS_JSON_PROP)) {
            props.remove(FIELDS_JSON_PROP);

            JSONArray fieldsJsonArray = groupJsonObject.getJSONArray(FIELDS_JSON_PROP);
            for (int i = 0; i < fieldsJsonArray.length(); i++) {
                FieldInfo fieldInfo = new FieldInfo();
                JSONObject fieldJsonObject = fieldsJsonArray.getJSONObject(i);
                if (!validateFieldJson(fieldJsonObject, info, groupIndex, i,
                        (path != null ? path + "." : "") + FIELDS_JSON_PROP + "[" + i + "]",
                        context, fieldInfo)) {
                    return false;
                }
                fields.add(fieldInfo);
            }
        }

        for (String testGroupPrefKeyPrefix : TEST_GROUP_PREF_KEY_PREFIXES) {
            String jsonProp = prefKeyPrefixToJsonName(testGroupPrefKeyPrefix);
            if (props.contains(jsonProp)) {
                props.remove(jsonProp);

                if (!testGroupPrefKeyPrefix.equals(PREF_TEST_FIELD_IDS_PREFIX)) {
                    if (!validatePrefData(groupJsonObject, jsonProp, path, testGroupPrefKeyPrefix,
                            info, context, namesMap)) {
                        return false;
                    }
                }
            }
        }

        for (String prop : props) {
            info.mUnexpectedProps.add(path + "." + prop);
        }

        groupInfo.mFields = fields;
        String name = getName(namesMap, new String[] { PREF_TEST_GROUP_NAME_PREFIX });
        groupInfo.mName = name != null
                ? name
                : context.getString(R.string.test_group_default_name, groupIndex + 1);
        return true;
    }

    private static boolean validateFieldJson(JSONObject fieldJsonObject, ImportFileInfo info,
                                             int groupIndex, int fieldIndex, String path,
                                             Context context, FieldInfo fieldInfo) {
        Map<String, String> namesMap = new HashMap<>();

        Set<String> props = getProps(fieldJsonObject);

        for (String testFieldPrefKeyPrefix : TEST_FIELD_PREF_KEY_PREFIXES) {
            String jsonProp = prefKeyPrefixToJsonName(testFieldPrefKeyPrefix);
            if (props.contains(jsonProp)) {
                props.remove(jsonProp);

                if (!validatePrefData(fieldJsonObject, jsonProp, path, testFieldPrefKeyPrefix, info,
                        context, namesMap)) {
                    return false;
                }
            }
        }

        for (String defaultableTestFieldPrefKeyPrefix : DEFAULTABLE_TEST_FIELD_PREF_KEY_PREFIXES) {
            String jsonProp = prefKeyPrefixToJsonName(defaultableTestFieldPrefKeyPrefix);
            if (props.contains(jsonProp)) {
                props.remove(jsonProp);

                if (!validatePrefData(fieldJsonObject, jsonProp, path,
                        defaultableTestFieldPrefKeyPrefix, info, context, null)) {
                    return false;
                }
            }
        }

        for (String prop : props) {
            info.mUnexpectedProps.add(path + "." + prop);
        }

        String name = getName(namesMap, new String[] {
                PREF_IME_LABEL_TEXT_PREFIX,
                PREF_IME_DEFAULT_TEXT_PREFIX,
                PREF_IME_HINT_TEXT_PREFIX
        });
        fieldInfo.mName = name != null
                ? name
                : context.getString(R.string.test_field_default_name, fieldIndex + 1);
        return true;
    }

    //TODO: (EW) rename - it sounds too much like it's referring to field groups. maybe use
    // something different, like "cluster"
    private static boolean validateGroupedSettingsJson(String[] keyOrKeyPrefixArray,
                                                       JSONObject jsonObject,
                                                       ImportFileInfo info, String path,
                                                       Context context) {
        Set<String> props = getProps(jsonObject);

        for (String prefKeyPrefix : keyOrKeyPrefixArray) {
            String jsonProp = prefKeyPrefixToJsonName(prefKeyPrefix);
            if (props.contains(jsonProp)) {
                props.remove(jsonProp);

                if (!validatePrefData(jsonObject, jsonProp, path, prefKeyPrefix, info, context,
                        null)) {
                    return false;
                }
            }
        }

        for (String prop : props) {
            info.mUnexpectedProps.add(path + "." + prop);
        }

        return true;
    }

    private static String getName(Map<String, String> namesMap, String[] keys) {
        for (String key : keys) {
            if (!namesMap.containsKey(key)) {
                continue;
            }
            String name = namesMap.get(key);
            if (!TextUtils.isEmpty(name)) {
                return name;
            }
        }
        return null;
    }

    private static boolean validatePrefData(JSONObject jsonObject, String jsonPropName, String path,
                                            String prefKeyOrPrefix, ImportFileInfo info,
                                            Context context, Map<String, String> namesMap) {
        // just need to try getting the data for basic types to ensure the right data type is set
        return loadOrValidatePrefData(jsonObject, jsonPropName, path, prefKeyOrPrefix, info,
                context, null, namesMap);
    }

    private static boolean loadPrefData(JSONObject jsonObject, String jsonPropName, String path,
                                        String prefKeyOrPrefix, @NonNull Context context,
                                        @NonNull String prefKey) {
        return loadOrValidatePrefData(jsonObject, jsonPropName, path, prefKeyOrPrefix, null,
                context, prefKey, null);

    }

    //TODO: (EW) probably should break this into multiple helper methods
    private static boolean loadOrValidatePrefData(JSONObject jsonObject, String jsonPropName,
                                                  String path, String prefKeyOrPrefix,
                                                  @Nullable ImportFileInfo info,
                                                  @NonNull Context context,
                                                  @Nullable String prefKey,
                                                  @Nullable Map<String, String> namesMap) {
        String fullPath = (path == null ? "" : (path + ".")) + jsonPropName;
        int dataType = prefDataType(prefKeyOrPrefix);
        try {
            switch (dataType) {
                case TYPE_BOOLEAN:
                    boolean booleanData = jsonObject.getBoolean(jsonPropName);
                    if (prefKey != null) {
                        boolean defaultBoolean = getPrefDefaultBoolean(prefKeyOrPrefix);
                        if (IMPORT_DEFAULT_PREFS_VALUES || booleanData != defaultBoolean) {
                            getInstance().mPrefs.setBoolean(prefKey, booleanData);
                        }
                    }
                    break;
                case TYPE_INT:
                    int intData = jsonObject.getInt(jsonPropName);
                    int minValue;
                    int maxValue;
                    int stepValue;
                    switch (prefKeyOrPrefix) {
                        case PREF_MAX_LENGTH_PREFIX:
                            // non-negative
                            minValue = 0;
                            maxValue = Integer.MAX_VALUE;
                            stepValue = 1;
                            break;
                        case PREF_SHIFT_CODEPOINT_PREFIX:
                            minValue = context.getResources().getInteger(
                                    R.integer.config_shift_codepoint_min);
                            maxValue = context.getResources().getInteger(
                                    R.integer.config_shift_codepoint_max);
                            stepValue = context.getResources().getInteger(
                                    R.integer.config_shift_codepoint_step);
                            break;
                        case PREF_LIMIT_EXTRACT_MONITOR_TEXT_PREFIX:
                            minValue = context.getResources().getInteger(
                                    R.integer.config_extract_monitor_text_limit_min);
                            maxValue = context.getResources().getInteger(
                                    R.integer.config_extract_monitor_text_limit_max);
                            stepValue = context.getResources().getInteger(
                                    R.integer.config_extract_monitor_text_limit_step);
                            break;
                        case PREF_LIMIT_RETURNED_TEXT_PREFIX:
                            minValue = context.getResources().getInteger(
                                    R.integer.config_returned_text_limit_min);
                            maxValue = context.getResources().getInteger(
                                    R.integer.config_returned_text_limit_max);
                            stepValue = context.getResources().getInteger(
                                    R.integer.config_returned_text_limit_step);
                            break;
                        case PREF_UPDATE_DELAY_PREFIX:
                            minValue = context.getResources().getInteger(
                                    R.integer.config_update_delay_min);
                            maxValue = context.getResources().getInteger(
                                    R.integer.config_update_delay_max);
                            stepValue = context.getResources().getInteger(
                                    R.integer.config_update_delay_step);
                            break;
                        case PREF_FINISHCOMPOSINGTEXT_DELAY_PREFIX:
                        case PREF_GETSURROUNDINGTEXT_DELAY_PREFIX:
                        case PREF_GETTEXTBEFORECURSOR_DELAY_PREFIX:
                        case PREF_GETSELECTEDTEXT_DELAY_PREFIX:
                        case PREF_GETTEXTAFTERCURSOR_DELAY_PREFIX:
                        case PREF_GETCURSORCAPSMODE_DELAY_PREFIX:
                        case PREF_GETEXTRACTEDTEXT_DELAY_PREFIX:
                            minValue = context.getResources().getInteger(
                                    R.integer.config_inputconnection_method_delay_min);
                            maxValue = context.getResources().getInteger(
                                    R.integer.config_inputconnection_method_delay_max);
                            stepValue = context.getResources().getInteger(
                                    R.integer.config_inputconnection_method_delay_step);
                            break;
                        default:
                            minValue = Integer.MIN_VALUE;
                            maxValue = Integer.MAX_VALUE;
                            stepValue = 1;
                    }
                    int constrainedIntData = constrain(intData, minValue, maxValue, stepValue);
                    if (constrainedIntData != intData
                            && intData != getPrefDefaultInt(prefKeyOrPrefix)) {
                        if (intData < minValue || intData > maxValue) {
                            Log.e(TAG, fullPath + " ( " + intData + ") isn't in the range "
                                    + minValue + " - " + maxValue);
                            if (info != null) {
                                info.mWarnings.add(context.getString(R.string.value_not_in_range,
                                        fullPath, intData, minValue, maxValue));
                            }
                            break;
                        } else {
                            Log.w(TAG, fullPath + " has an int ( " + intData
                                    + ") that doesn't conform to the constraints: min=" + minValue
                                    + ", max=" + maxValue + ", step=" + stepValue);
                            if (info != null) {
                                info.mWarnings.add(context.getString(R.string.invalid_step_value,
                                        fullPath));
                            }
                            // it should be relatively safe to just shift to the nearest step
                            intData = constrainedIntData;
                        }
                    }
                    if (prefKey != null) {
                        int defaultInt = getPrefDefaultInt(prefKeyOrPrefix);
                        if (IMPORT_DEFAULT_PREFS_VALUES || intData != defaultInt) {
                            getInstance().mPrefs.setInt(prefKey, intData);
                        }
                    }
                    break;
                case TYPE_LONG:
                    long longData = jsonObject.getLong(jsonPropName);
                    if (prefKey != null) {
                        long defaultLong = getPrefDefaultLong(prefKeyOrPrefix);
                        if (IMPORT_DEFAULT_PREFS_VALUES || longData != defaultLong) {
                            getInstance().mPrefs.setLong(prefKey, longData);
                        }
                    }
                    break;
                case TYPE_FLOAT:
                    double doubleData = jsonObject.getDouble(jsonPropName);
                    if (doubleData > Float.MAX_VALUE || doubleData < Float.MIN_VALUE) {
                        Log.e(TAG, doubleData + " isn't a valid float for " + fullPath);
                        if (info != null) {
                            info.mWarnings.add(
                                    context.getString(R.string.invalid_float_data, fullPath));
                        }
                        break;
                    }
                    if (prefKey != null) {
                        float defaultFloat = getPrefDefaultFloat(prefKeyOrPrefix);
                        float floatData = (float) doubleData;
                        if (IMPORT_DEFAULT_PREFS_VALUES || floatData != defaultFloat) {
                            getInstance().mPrefs.setFloat(prefKey, floatData);
                        }
                    }
                    break;
                case TYPE_STRING:
                    String stringData = getString(jsonObject, jsonPropName);
                    String[] allowedStringValues;
                    switch (prefKeyOrPrefix) {
                        case PREF_INPUT_TYPE_CLASS_PREFIX:
                            allowedStringValues = context.getResources().getStringArray(
                                    R.array.type_class_values);
                            break;
                        case PREF_INPUT_TYPE_TEXT_VARIATION_PREFIX:
                            allowedStringValues = context.getResources().getStringArray(
                                    R.array.type_text_variation_values);
                            break;
                        case PREF_INPUT_TYPE_NUMBER_VARIATION_PREFIX:
                            allowedStringValues = context.getResources().getStringArray(
                                    R.array.type_number_variation_values);
                            break;
                        case PREF_INPUT_TYPE_DATETIME_VARIATION_PREFIX:
                            allowedStringValues = context.getResources().getStringArray(
                                    R.array.type_datetime_variation_values);
                            break;
                        case PREF_INPUT_TYPE_TEXT_FLAG_MULTI_LINE_PREFIX:
                            allowedStringValues = context.getResources().getStringArray(
                                    R.array.type_text_multi_line_flag_values);
                            break;
                        case PREF_INPUT_TYPE_TEXT_FLAG_CAP_PREFIX:
                            allowedStringValues = context.getResources().getStringArray(
                                    R.array.type_text_cap_flag_values);
                            break;
                        case PREF_NULL_INPUT_TYPE_COMPOSING_TEXT_BEHAVIOR_PREFIX:
                            allowedStringValues = context.getResources().getStringArray(
                                    R.array.composing_text_behavior_values);
                            break;
                        case PREF_IME_OPTIONS_ACTION_PREFIX:
                            allowedStringValues = context.getResources().getStringArray(
                                    R.array.ime_options_action_values);
                            break;
                        case PREF_THEME:
                            allowedStringValues = context.getResources().getStringArray(
                                    R.array.theme_values);
                            break;
                        default:
                            allowedStringValues = null;
                    }
                    if (allowedStringValues != null
                            && !ArrayUtils.contains(allowedStringValues, stringData)
                            && !TextUtils.equals(stringData,
                                    getPrefDefaultString(prefKeyOrPrefix))) {
                        Log.e(TAG, fullPath + " has an invalid value: " + stringData);
                        if (info != null) {
                            info.mWarnings.add(context.getString(R.string.invalid_value,
                                    fullPath, stringData));
                        }
                        break;
                    }
                    if (prefKey != null) {
                        String defaultString = getPrefDefaultString(prefKeyOrPrefix);
                        if (IMPORT_DEFAULT_PREFS_VALUES
                                || !TextUtils.equals(stringData, defaultString)) {
                            getInstance().mPrefs.setString(prefKey, stringData);
                        }
                    }
                    if (namesMap != null) {
                        switch (prefKeyOrPrefix) {
                            case PREF_TEST_GROUP_NAME_PREFIX:
                                namesMap.put(prefKeyOrPrefix, stringData);
                        }
                    }
                    break;
                case TYPE_SPANNED:
                    Spanned spannedData = getSpanned(jsonObject, jsonPropName);
                    if (prefKey != null) {
                        Spanned defaultSpanned = getPrefDefaultSpanned(prefKeyOrPrefix);
                        if (IMPORT_DEFAULT_PREFS_VALUES
                                || !definitelyEqual(spannedData, defaultSpanned)) {
                            getInstance().mPrefs.setSpanned(prefKey, spannedData);
                        }
                    }
                    break;
                case TYPE_CHAR_SEQUENCE:
                    CharSequence charSequenceData;
                    if (jsonObject.isNull(jsonPropName)) {
                        charSequenceData = null;
                    } else if (jsonObject.get(jsonPropName) instanceof String) {
                        charSequenceData = jsonObject.getString(jsonPropName);
                    } else {
                        charSequenceData = getSpanned(jsonObject, jsonPropName);
                    }
                    if (prefKey != null) {
                        CharSequence defaultCharSequence =
                                getPrefDefaultCharSequence(prefKeyOrPrefix);
                        if (IMPORT_DEFAULT_PREFS_VALUES
                                || !definitelyEqual(charSequenceData, defaultCharSequence)) {
                            getInstance().mPrefs.setCharSequence(prefKey, charSequenceData);
                        }
                    }
                    if (namesMap != null) {
                        switch (prefKeyOrPrefix) {
                            case PREF_IME_LABEL_TEXT_PREFIX:
                            case PREF_IME_DEFAULT_TEXT_PREFIX:
                            case PREF_IME_HINT_TEXT_PREFIX:
                                namesMap.put(prefKeyOrPrefix, charSequenceData == null
                                        ? null
                                        : charSequenceData.toString());
                        }
                    }
                    break;
                case TYPE_INT_ARRAY:
                    int[] intArrayData = getIntArray(jsonObject, jsonPropName);
                    if (prefKey != null) {
                        int[] defaultIntArray = getPrefDefaultIntArray(prefKeyOrPrefix);
                        if (IMPORT_DEFAULT_PREFS_VALUES
                                || !Arrays.equals(intArrayData, defaultIntArray)) {
                            getInstance().mPrefs.setIntArray(prefKey, intArrayData);
                        }
                    }
                    break;
                case TYPE_STRING_ARRAY:
                    String[] stringArrayData = getStringArray(jsonObject, jsonPropName);
                    if (prefKey != null) {
                        String[] defaultStringArray = getPrefDefaultStringArray(prefKeyOrPrefix);
                        if (IMPORT_DEFAULT_PREFS_VALUES
                                || !Arrays.equals(stringArrayData, defaultStringArray)) {
                            getInstance().mPrefs.setStringArray(prefKey, stringArrayData);
                        }
                    }
                    break;
                case TYPE_INT_RANGE:
                    int[] rangeArray = getIntArray(jsonObject, jsonPropName);
                    if (rangeArray != null && rangeArray.length != 2) {
                        Log.e(TAG, fullPath + " doesn't have exactly 2 values: "
                                + Arrays.toString(rangeArray));
                        if (info != null) {
                            info.mWarnings.add(context.getString(R.string.invalid_data, fullPath));
                        }
                        break;
                    }
                    if (prefKeyOrPrefix.equals(PREF_RESTRICT_RANGE_PREFIX)) {
                        IntRange range = rangeArray == null
                                ? null
                                : new IntRange(rangeArray[0], rangeArray[1]);
                        if (!CodepointRangeDialogPreference.isValidRange(range)) {
                            Log.e(TAG, fullPath + " contains an invalid codepoint range: "
                                    + range);
                            if (info != null) {
                                info.mWarnings.add(
                                        context.getString(R.string.invalid_data, fullPath));
                            }
                            break;
                        }
                        if (prefKey != null) {
                            CodepointRangeDialogPreference.DataManager codepointRangeDialogDataManager =
                                    new CodepointRangeDialogPreference.DataManager(
                                            getInstance().mPrefs, prefKey);
                            IntRange defaultIntRange =
                                    codepointRangeDialogDataManager.readDefaultValue();
                            if (IMPORT_DEFAULT_PREFS_VALUES
                                    || !Objects.equals(range, defaultIntRange)) {
                                codepointRangeDialogDataManager.writeValue(range);
                            }
                        }
                    } else {
                        Log.e(TAG, prefKey + " doesn't have handling to be imported");
                    }
                    break;
                case TYPE_LOCALE_ARRAY:
                    //TODO: (EW) possibly could be more generic (or at least decoupled from the
                    // specific preference)
                    String[] localStringArray = getStringArray(jsonObject, jsonPropName);
                    int localeCount = localStringArray == null ? 0 : localStringArray.length;
                    List<Locale> localeList = new ArrayList<>();
                    for (int i = 0; i < localeCount; i++) {
                        if (!LocaleEntryListPreference.isValidLocale(localStringArray[i])) {
                            Log.e(TAG, fullPath + "[" + i + "] doesn't have a valid locale string: "
                                    + localStringArray[i]);
                            if (info != null) {
                                info.mWarnings.add(context.getString(R.string.invalid_locale,
                                        fullPath + "[" + i + "]", localStringArray[i]));
                            }
                            continue;
                        }
                        localeList.add(LocaleEntryListPreference.constructLocaleFromString(
                                localStringArray[i]));
                    }
                    if (prefKey != null) {
                        LocaleEntryListPreference.DataManager localeEntryListDataManager =
                                new LocaleEntryListPreference.DataManager(getInstance().mPrefs,
                                        prefKey);
                        Locale[] defaultLocaleArray = localeEntryListDataManager.readDefaultValue();
                        Locale[] localeArray = localeList.toArray(new Locale[0]);
                        if (IMPORT_DEFAULT_PREFS_VALUES
                                || !Arrays.equals(localeArray, defaultLocaleArray)) {
                            localeEntryListDataManager.writeValue(localeArray);
                        }
                    }
                    break;
                case TYPE_TEXT_LIST_STRING:
                    //TODO: (EW) possibly could be more generic (or at least decoupled from the
                    // specific preference)
                    JSONObject textListStringJsonObject = jsonObject.getJSONObject(jsonPropName);
                    String[] textListStringStringArray = getStringArray(textListStringJsonObject,
                            TEXT_LIST_DATA_ARRAY_JSON_PROP);
                    if (textListStringStringArray == null) {
                        Log.e(TAG, fullPath + "." + TEXT_LIST_DATA_ARRAY_JSON_PROP + " is null");
                        if (info != null) {
                            info.mWarnings.add(context.getString(R.string.null_data,
                                    fullPath + "." + TEXT_LIST_DATA_ARRAY_JSON_PROP));
                        }
                        break;
                    }
                    TextList<String> textListStringData = new TextList<>(
                            textListStringStringArray,
                            textListStringJsonObject.getBoolean(TEXT_LIST_ESCAPE_CHARS_JSON_PROP));
                    if (prefKey != null) {
                        TextListPreference.DataManager textListDataManager =
                                new TextListPreference.DataManager(getInstance().mPrefs, prefKey);
                        TextList<String> defaultTextListString =
                                textListDataManager.readDefaultValue();
                        if (IMPORT_DEFAULT_PREFS_VALUES
                                || !Objects.equals(textListStringData, defaultTextListString)) {
                            textListDataManager.writeValue(textListStringData);
                        }
                    }
                    break;
                case TYPE_TEXT_LIST_TRANSLATE_TEXT:
                    //TODO: (EW) possibly could be more generic (or at least decoupled from the
                    // specific preference)
                    JSONObject textListTranslateTextJsonObject =
                            jsonObject.getJSONObject(jsonPropName);
                    JSONObject[] translateTextJsonObjects = getJsonObjectArray(
                            textListTranslateTextJsonObject, TEXT_LIST_DATA_ARRAY_JSON_PROP);
                    if (translateTextJsonObjects == null) {
                        Log.e(TAG, fullPath + "." + TEXT_LIST_DATA_ARRAY_JSON_PROP + " is null");
                        if (info != null) {
                            info.mWarnings.add(context.getString(R.string.null_data,
                                    fullPath + "." + TEXT_LIST_DATA_ARRAY_JSON_PROP));
                        }
                        break;
                    }
                    TranslateText[] translateTextArray =
                            new TranslateText[translateTextJsonObjects.length];
                    for (int i = 0; i < translateTextJsonObjects.length; i++) {
                        translateTextArray[i] = new TranslateText(
                                translateTextJsonObjects[i].getString(
                                        TRANSLATE_TEXT_ORIGINAL_JSON_PROP),
                                translateTextJsonObjects[i].getString(
                                        TRANSLATE_TEXT_TRANSLATION_JSON_PROP));
                    }
                    TextList<TranslateText> textListTranslateTextData = new TextList<>(
                            translateTextArray,
                            textListTranslateTextJsonObject.getBoolean(
                                    TEXT_LIST_ESCAPE_CHARS_JSON_PROP));
                    if (prefKey != null) {
                        TextTranslateListPreference.DataManager textTranslateListDataManager =
                                new TextTranslateListPreference.DataManager(getInstance().mPrefs,
                                        prefKey);
                        TextList<TranslateText> defaultTextListTranslateText =
                                textTranslateListDataManager.readDefaultValue();
                        if (IMPORT_DEFAULT_PREFS_VALUES
                                || !Objects.equals(textListTranslateTextData,
                                        defaultTextListTranslateText)) {
                            textTranslateListDataManager.writeValue(textListTranslateTextData);
                        }
                    }
                    break;
                case TYPE_UNKNOWN:
                default:
                    //TODO: (EW) probably handle gracefully, but hard crash for now to catch issues
                    throw new RuntimeException("Unknown data type for " + prefKeyOrPrefix);
            }
        } catch (JSONException e) {
            logJsonException(e, fullPath, info, context);
        }
        return true;
    }

    private static void logJsonException(JSONException e, String path,
                                         @Nullable ImportFileInfo info, @NonNull Context context) {
        String message = e.getMessage();
        Log.e(TAG, path + ": " + message);
        if (info != null) {
            if (message != null && message.matches(
                    "Value .* at \\w+ of type [\\w\\.]+ cannot be converted to [\\w\\.]+")) {
                info.mWarnings.add(context.getString(R.string.invalid_data_type, path));
            } else {
                info.mWarnings.add(context.getString(R.string.failed_to_parse_data, path));
            }
        }
    }

    private static int constrain(int value, int minValue, int maxValue, int stepValue) {
        if (value < minValue) {
            return minValue;
        }
        if (value > maxValue) {
            return maxValue;
        }
        long stepLowerEdge = (((long) value - minValue) / stepValue) * stepValue + minValue;
        long stepUpperEdge =
                (((long) value + stepValue - 1 - minValue) / stepValue) * stepValue + minValue;
        if (stepLowerEdge == stepUpperEdge || value - stepLowerEdge <= stepUpperEdge - value) {
            return (int) stepLowerEdge;
        }
        return (int) stepUpperEdge;
    }

    private static String getString(JSONObject jsonObject, String jsonPropName)
            throws JSONException {
        if (jsonObject.isNull(jsonPropName)) {
            return null;
        }
        return jsonObject.getString(jsonPropName);
    }

    private static JSONObject[] getJsonObjectArray(JSONObject jsonObject, String jsonPropName)
            throws JSONException {
        if (jsonObject.isNull(jsonPropName)) {
            return null;
        }
        JSONArray jsonArray = jsonObject.getJSONArray(jsonPropName);
        JSONObject[] result = new JSONObject[jsonArray.length()];
        for (int i = 0; i < result.length; i++) {
            result[i] = jsonArray.getJSONObject(i);
        }
        return result;
    }

    private static String[] getStringArray(JSONObject jsonObject, String jsonPropName)
            throws JSONException {
        if (jsonObject.isNull(jsonPropName)) {
            return null;
        }
        JSONArray jsonArray = jsonObject.getJSONArray(jsonPropName);
        String[] result = new String[jsonArray.length()];
        for (int i = 0; i < result.length; i++) {
            result[i] = jsonArray.getString(i);
        }
        return result;
    }

    private static int[] getIntArray(JSONObject jsonObject, String jsonPropName)
            throws JSONException {
        if (jsonObject.isNull(jsonPropName)) {
            return null;
        }
        JSONArray jsonArray = jsonObject.getJSONArray(jsonPropName);
        int[] result = new int[jsonArray.length()];
        for (int i = 0; i < result.length; i++) {
            result[i] = jsonArray.getInt(i);
        }
        return result;
    }

    private static Spanned getSpanned(JSONObject jsonObject, String jsonPropName)
            throws JSONException {
        if (jsonObject.isNull(jsonPropName)) {
            return null;
        }
        String[] spannedInfo = getStringArray(jsonObject, jsonPropName);
        //TODO: (EW) figure out how to get validation issue from this
        return SharedPreferenceManager.buildSpanned(spannedInfo);
    }

    public static void importSettings(JSONObject jsonObject, boolean replaceFieldDefaults,
                                      boolean replaceFields, List<GroupInfo> groupInfoList,
                                      boolean embedFieldDefaults, boolean replaceOtherSettings,
                                      Context context) {
        Settings instance = getInstance();
        SharedPreferenceManager prefs = instance.mPrefs;
        prefs.unregisterOnSharedPreferenceChangeListener(instance);
        try {
            JSONObject fieldDefaultsJsonObject =
                    getJsonObject(jsonObject, FIELD_DEFAULTS_JSON_PROP);

            if (replaceFields) {
                // delete the old groups and fields before adding the new ones
                setTestFieldGroupIds(new int[0]);
            }

            List<Integer> groupIds = new ArrayList<>();
            List<Integer> fieldIds = new ArrayList<>();
            if (!replaceFields) {
                groupIds.addAll(instance.mTestGroups.keySet());
                fieldIds.addAll(instance.mTestFields.keySet());
            }
            if (jsonObject.has(GROUPS_JSON_PROP)) {
                JSONArray groupsJsonArray = jsonObject.getJSONArray(GROUPS_JSON_PROP);

                for (int i = 0; i < groupsJsonArray.length(); i++) {
                    JSONObject groupJsonObject = groupsJsonArray.getJSONObject(i);

                    importGroupJson(groupJsonObject, GROUPS_JSON_PROP + "[" + i + "]",
                            groupInfoList.get(i), groupIds, fieldIds, embedFieldDefaults,
                            fieldDefaultsJsonObject, context);
                }

                prefs.setIntArray(PREF_TEST_GROUP_IDS, toPrimitiveArray(groupIds));
            } else if (jsonObject.has(FIELDS_JSON_PROP) && groupInfoList.size() == 1) {
                // build the ad-hoc group to load
                JSONObject groupJsonObject = new JSONObject();
                JSONArray looseFieldsJsonArray = jsonObject.getJSONArray(FIELDS_JSON_PROP);
                groupJsonObject.put(FIELDS_JSON_PROP, looseFieldsJsonArray);

                importGroupJson(groupJsonObject, null, groupInfoList.get(0), groupIds, fieldIds,
                        embedFieldDefaults, fieldDefaultsJsonObject, context);

                prefs.setIntArray(PREF_TEST_GROUP_IDS, toPrimitiveArray(groupIds));
            }

            if (replaceFieldDefaults) {
                for (String prefKeyPrefix : DEFAULTABLE_TEST_FIELD_PREF_KEY_PREFIXES) {
                    String prefKey = prefKeyPrefix + BASE_SUFFIX;
                    prefs.remove(prefKey);
                    if (fieldDefaultsJsonObject != null) {
                        String jsonProp = prefKeyPrefixToJsonName(prefKeyPrefix);
                        if (fieldDefaultsJsonObject.has(jsonProp)) {
                            loadPrefData(fieldDefaultsJsonObject, jsonProp, null, prefKeyPrefix,
                                    context, prefKey);
                        }
                    }
                }
            }

            if (replaceOtherSettings) {
                JSONObject otherSettingsJsonObject =
                        getJsonObject(jsonObject, OTHER_SETTINGS_JSON_PROP);
                for (String prefKey : MISC_PREF_KEYS) {
                    prefs.remove(prefKey);
                    if (otherSettingsJsonObject != null) {
                        String jsonProp = prefKeyPrefixToJsonName(prefKey);
                        if (otherSettingsJsonObject.has(jsonProp)) {
                            loadPrefData(otherSettingsJsonObject, jsonProp, null, prefKey, context,
                                    prefKey);
                        }
                    }
                }
            }

        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        }

        prefs.registerOnSharedPreferenceChangeListener(instance);
        instance.loadSettings();
    }

    private static void importGroupJson(JSONObject groupJsonObject, String path,
                                        GroupInfo groupInfo, List<Integer> groupIds,
                                        List<Integer> fieldIds, boolean embedFieldDefaults,
                                        JSONObject fieldDefaultsJsonObject, Context context)
            throws JSONException {
        Settings instance = getInstance();
        int groupId;
        if (groupInfo.mInclude) {
            groupId = getNextId(groupIds);
            groupIds.add(groupId);
        } else {
            // add any fields to the last group
            groupId = instance.mTestGroupIds[instance.mTestGroupIds.length - 1];
        }
        addGroupJson(groupJsonObject, groupId, path,
                fieldIds, groupInfo, embedFieldDefaults, fieldDefaultsJsonObject,
                context);
    }

    private static void addGroupJson(JSONObject groupJsonObject, int groupId, String path,
                                     List<Integer> fieldIds, GroupInfo groupInfo,
                                     boolean embedFieldDefaults, JSONObject fieldDefaultsJsonObject,
                                     Context context)
            throws JSONException {
        SharedPreferenceManager prefs = getInstance().mPrefs;

        if (groupJsonObject.has(FIELDS_JSON_PROP)) {
            JSONArray fieldsJsonArray = groupJsonObject.getJSONArray(FIELDS_JSON_PROP);

            List<Integer> groupFieldIds = new ArrayList<>();
            for (int i = 0; i < fieldsJsonArray.length(); i++) {
                if (!groupInfo.mFields.get(i).mInclude) {
                    continue;
                }
                int fieldId = getNextId(fieldIds);
                fieldIds.add(fieldId);
                groupFieldIds.add(fieldId);
                JSONObject fieldJsonObject = fieldsJsonArray.getJSONObject(i);
                addFieldJson(fieldJsonObject, fieldId,
                        (path != null ? path + "." : "") + FIELDS_JSON_PROP + "[" + i + "]",
                        embedFieldDefaults, fieldDefaultsJsonObject, context);
            }

            int[] fieldIdsArray;
            if (groupInfo.mInclude) {
                fieldIdsArray = toPrimitiveArray(groupFieldIds);
            } else {
                fieldIdsArray = ArrayUtils.join(readTestGroupFieldIds(prefs, groupId),
                        toPrimitiveArray(groupFieldIds));
            }
            prefs.setIntArray(PREF_TEST_FIELD_IDS_PREFIX + GROUP_INFIX + groupId, fieldIdsArray);
        }

        if (groupInfo.mInclude) {
            for (String prefKeyPrefix : TEST_GROUP_PREF_KEY_PREFIXES) {
                if (prefKeyPrefix.equals(PREF_TEST_FIELD_IDS_PREFIX)) {
                    continue;
                }

                String prefKey = prefKeyPrefix + GROUP_INFIX + groupId;
                prefs.remove(prefKey);
                String jsonProp = prefKeyPrefixToJsonName(prefKeyPrefix);
                if (groupJsonObject.has(jsonProp)) {
                    loadPrefData(groupJsonObject, jsonProp, path, prefKeyPrefix, context, prefKey);
                }
            }
        }
    }

    private static void addFieldJson(JSONObject fieldJsonObject, int fieldId, String path,
                                     boolean embedFieldDefaults, JSONObject fieldDefaultsJsonObject,
                                     Context context) {
        SharedPreferenceManager prefs = getInstance().mPrefs;

        for (String fieldPrefKeyPrefix : TEST_FIELD_PREF_KEY_PREFIXES) {
            String fieldPrefKey = fieldPrefKeyPrefix + FIELD_INFIX + fieldId;
            prefs.remove(fieldPrefKey);
            String jsonProp = prefKeyPrefixToJsonName(fieldPrefKeyPrefix);

            if (DEFAULT_OVERRIDE_PREF_PREFIX_MAP.containsKey(fieldPrefKeyPrefix)) {
                JSONObject defaultableValueJsonObject;
                String defaultableValuePath;
                // embed defaults if requested and the field doesn't already override them,
                // otherwise just save the override values
                if (embedFieldDefaults
                        && !tryGetBoolean(fieldJsonObject, jsonProp, false)) {
                    defaultableValueJsonObject = fieldDefaultsJsonObject;
                    defaultableValuePath = FIELD_DEFAULTS_JSON_PROP;
                    getInstance().mPrefs.setBoolean(fieldPrefKey, true);
                } else {
                    defaultableValueJsonObject = fieldJsonObject;
                    defaultableValuePath = path;
                    if (fieldJsonObject.has(jsonProp)) {
                        loadPrefData(fieldJsonObject, jsonProp, path, fieldPrefKeyPrefix, context,
                                fieldPrefKey);
                    }
                }
                String[] fieldDefaultsPrefKeys =
                        DEFAULT_OVERRIDE_PREF_PREFIX_MAP.get(fieldPrefKeyPrefix);
                for (String fieldDefaultPrefKeyPrefix : fieldDefaultsPrefKeys) {
                    String fieldDefaultPrefKey = fieldDefaultPrefKeyPrefix + FIELD_INFIX + fieldId;
                    String fieldDefaultJsonProp =
                            prefKeyPrefixToJsonName(fieldDefaultPrefKeyPrefix);
                    if (defaultableValueJsonObject.has(fieldDefaultJsonProp)) {
                        loadPrefData(defaultableValueJsonObject, fieldDefaultJsonProp,
                                defaultableValuePath, fieldDefaultPrefKeyPrefix, context,
                                fieldDefaultPrefKey);
                    }
                }
            } else {
                if (fieldJsonObject.has(jsonProp)) {
                    loadPrefData(fieldJsonObject, jsonProp, path, fieldPrefKeyPrefix, context,
                            fieldPrefKey);
                }
            }
        }
    }

    private static boolean tryGetBoolean(JSONObject jsonObject, String prop, boolean defaultValue) {
        if (jsonObject == null || !jsonObject.has(prop)) {
            return defaultValue;
        }
        try {
            return jsonObject.getBoolean(prop);
        } catch (JSONException e) {
            return defaultValue;
        }
    }

    private static int[] toPrimitiveArray(List<Integer> list) {
        int[] array = new int[list.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = list.get(i);
        }
        return array;
    }
}
