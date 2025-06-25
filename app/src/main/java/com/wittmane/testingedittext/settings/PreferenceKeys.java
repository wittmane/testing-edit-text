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

package com.wittmane.testingedittext.settings;

import com.wittmane.testingedittext.aosp.com.android.internal.util.ArrayUtils;

import java.util.HashMap;
import java.util.Map;

public class PreferenceKeys {

    public static final int BASE_GROUP_INDEX = -1;
    public static final int BASE_FIELD_INDEX = -1;
    public static final int BASE_FIELD_ID = -1;

    public static final String PREF_KEY_PREFIX = "pref_key_";
    public static final String BASE_SUFFIX = "_base";
    public static final String GROUP_INFIX = "_group_";
    public static final String FIELD_INFIX = "_field_";

    //#region preference keys
    //#region text input modification
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
    //#endregion

    //#region text return
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
    //#endregion

    //#region text composition
    public static final String PREF_OVERRIDE_TEXT_COMPOSITION_PREFIX =
            "pref_key_override_text_composition";
    public static final String PREF_DELETE_THROUGH_COMPOSING_TEXT_PREFIX =
            "pref_key_delete_through_composing_text";
    public static final String PREF_KEEP_EMPTY_COMPOSING_POSITION_PREFIX =
            "pref_key_keep_empty_composing_position";
    //#endregion

    //#region target version simulation
    public static final String PREF_OVERRIDE_TARGET_VERSION_SIMULATION_PREFIX =
            "pref_key_override_target_version_simulation";
    public static final String PREF_SKIP_PERFORMHANDWRITINGGESTURE_PREFIX =
            "pref_key_skip_performhandwritinggesture";
    public static final String PREF_SKIP_PREVIEWHANDWRITINGGESTURE_PREFIX =
            "pref_key_skip_previewhandwritinggesture";
    public static final String PREF_SKIP_REPLACETEXT_PREFIX =
            "pref_key_skip_replacetext";
    public static final String PREF_SKIP_REQUESTTEXTBOUNDSINFO_PREFIX =
            "pref_key_skip_requesttextboundsinfo";
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
    //#endregion

    //#region system behavior simulation
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
    public static final String PREF_REQUESTTEXTBOUNDSINFO_DELAY_PREFIX =
            "pref_key_requesttextboundsinfo_delay";
    //#endregion

    public static final String PREF_TEST_GROUP_IDS =
            "pref_key_test_group_ids";

    //#region test group
    public static final String PREF_TEST_FIELD_IDS_PREFIX =
            "pref_key_test_field_ids";
    public static final String PREF_TEST_GROUP_NAME_PREFIX =
            "pref_key_test_group_name";
    //#endregion

    //#region test field
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
    //#endregion

    //#region display
    public static final String PREF_THEME =
            "pref_key_theme";
    public static final String PREF_SHOW_REFERENCE_EDITTEXT =
            "pref_key_show_reference_edittext";
    public static final String PREF_SHOW_FIELD_QUICK_SETTINGS_BUTTON =
            "pref_key_show_field_quick_settings_button";
    //#endregion
    //#endregion

    //#region preference key groups
    public static final String[] TEXT_INPUT_MODIFICATION_PREF_KEY_PREFIXES = new String[] {
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

    public static final String[] TEXT_RETURN_PREF_KEY_PREFIXES = new String[] {
            PREF_SKIP_EXTRACTING_TEXT_PREFIX,
            PREF_IGNORE_EXTRACTED_TEXT_MONITOR_PREFIX,
            PREF_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT_PREFIX,
            PREF_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES_PREFIX,
            PREF_EXTRACT_FULL_TEXT_PREFIX,
            PREF_LIMIT_EXTRACT_MONITOR_TEXT_PREFIX,
            PREF_LIMIT_RETURNED_TEXT_PREFIX
    };

    public static final String[] TEXT_COMPOSITION_PREF_KEY_PREFIXES = new String[] {
            PREF_DELETE_THROUGH_COMPOSING_TEXT_PREFIX,
            PREF_KEEP_EMPTY_COMPOSING_POSITION_PREFIX
    };

    public static final String[] TARGET_VERSION_SIMULATION_PREF_KEY_PREFIXES = new String[] {
            PREF_SKIP_PERFORMHANDWRITINGGESTURE_PREFIX,
            PREF_SKIP_PREVIEWHANDWRITINGGESTURE_PREFIX,
            PREF_SKIP_REPLACETEXT_PREFIX,
            PREF_SKIP_REQUESTTEXTBOUNDSINFO_PREFIX,
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

    public static final String[] SYSTEM_BEHAVIOR_SIMULATION_PREF_KEY_PREFIXES = new String[] {
            PREF_UPDATE_DELAY_PREFIX,
            PREF_FINISHCOMPOSINGTEXT_DELAY_PREFIX,
            PREF_GETSURROUNDINGTEXT_DELAY_PREFIX,
            PREF_GETTEXTBEFORECURSOR_DELAY_PREFIX,
            PREF_GETSELECTEDTEXT_DELAY_PREFIX,
            PREF_GETTEXTAFTERCURSOR_DELAY_PREFIX,
            PREF_GETCURSORCAPSMODE_DELAY_PREFIX,
            PREF_GETEXTRACTEDTEXT_DELAY_PREFIX,
            PREF_REQUESTTEXTBOUNDSINFO_DELAY_PREFIX
    };

    public static final String[] DEFAULTABLE_TEST_FIELD_PREF_KEY_PREFIXES = ArrayUtils.join(
            TEXT_INPUT_MODIFICATION_PREF_KEY_PREFIXES,
            TEXT_RETURN_PREF_KEY_PREFIXES,
            TEXT_COMPOSITION_PREF_KEY_PREFIXES,
            TARGET_VERSION_SIMULATION_PREF_KEY_PREFIXES,
            SYSTEM_BEHAVIOR_SIMULATION_PREF_KEY_PREFIXES
    );

    public static final String[] TEST_GROUP_PREF_KEY_PREFIXES = new String[] {
            PREF_TEST_GROUP_NAME_PREFIX,
            PREF_TEST_FIELD_IDS_PREFIX
    };

    public static final String[] TEST_FIELD_PREF_KEY_PREFIXES = new String[] {
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

    public static final String[] MISC_PREF_KEYS = new String[] {
            PREF_THEME,
            PREF_SHOW_REFERENCE_EDITTEXT,
            PREF_SHOW_FIELD_QUICK_SETTINGS_BUTTON
    };
    //#endregion

    public static final Map<String, String[]> DEFAULT_OVERRIDE_PREF_PREFIX_MAP = new HashMap<>();
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

    //#region preference values
    public static final String THEME_SYSTEM_DEFAULT = "THEME_SYSTEM_DEFAULT";
    public static final String THEME_MATERIAL_DARK = "THEME_MATERIAL_DARK";
    public static final String THEME_MATERIAL_LIGHT = "THEME_MATERIAL_LIGHT";
    public static final String THEME_HOLO_DARK = "THEME_HOLO_DARK";
    public static final String THEME_HOLO_LIGHT = "THEME_HOLO_LIGHT";
    //#endregion
}
