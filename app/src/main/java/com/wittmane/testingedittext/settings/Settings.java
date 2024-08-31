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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wittmane.testingedittext.aosp.internal.util.ArrayUtils;
import com.wittmane.testingedittext.settings.SharedPreferenceManager.Editor;
import com.wittmane.testingedittext.settings.preferences.LocaleEntryListPreference;
import com.wittmane.testingedittext.settings.preferences.TextListPreference;
import com.wittmane.testingedittext.settings.preferences.CodepointRangeDialogPreference;
import com.wittmane.testingedittext.settings.preferences.TextTranslateListPreference;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class Settings implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = Settings.class.getSimpleName();

    private static final boolean LIST_PREFS = false;

    public static final int BASE_GROUP_INDEX = -1;
    public static final int BASE_FIELD_INDEX = -1;
    public static final int BASE_FIELD_ID = -1;

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

    public static final String PREF_TEST_GROUP_IDS =
            "pref_key_test_group_ids";

    public static final String PREF_TEST_FIELD_IDS_PREFIX =
            "pref_key_test_field_ids";
    public static final String PREF_TEST_GROUP_NAME_PREFIX =
            "pref_key_test_group_name";

    public static final String PREF_TEST_GROUP_FIELD_COUNTS =
            "pref_key_test_group_field_count";

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

    private int[] mTestGroupIds;
    private final Map<Integer, TestGroup> mTestGroups = new HashMap<>();
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

        logPreferences();

        convertToUseGroupIds(mPrefs);
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
        final String[] prefKeys = new String[] {
        };
        for (String prefKey : prefKeys) {
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
        String groupName = readTestGroupName(mPrefs, groupId);
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
            case PREF_TEST_GROUP_IDS:
                // internal state is updated while these are modified since they aren't managed by a
                // simple Preference, so we don't need to do anything when these change
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
            if (!target.containsKey(id) && !Settings.getInstance().mPrefs.contains(prefKey)) {
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
                getGroupById(groupId).mName = readTestGroupName(mPrefs, groupId);
                break;
            default:
                Log.w(TAG, "Preference " + prefKeyPrefix + GROUP_INFIX + groupId
                        + " wasn't processed");
        }
    }

    private void loadTestFieldSetting(String prefKeyPrefix, int fieldId) {
        TestField testField = getField(fieldId);
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

    public static boolean shouldModifyCommittedText(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextInputModification(groupIndex, fieldIndex)
                .mModifyCommittedText;
    }

    public static final boolean DEFAULT_MODIFY_COMPOSED_TEXT = false;

    private static boolean readModifyComposedText(final SharedPreferenceManager prefs,
                                                  int fieldId) {
        return prefs.getBoolean(PREF_MODIFY_COMPOSED_TEXT_PREFIX + getSuffix(fieldId),
                DEFAULT_MODIFY_COMPOSED_TEXT);
    }

    public static boolean shouldModifyComposedText(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextInputModification(groupIndex, fieldIndex)
                .mModifyComposedText;
    }

    public static final boolean DEFAULT_MODIFY_COMPOSED_CHANGES_ONLY = false;

    private static boolean readModifyComposedChangesOnly(final SharedPreferenceManager prefs,
                                                         int fieldId) {
        return prefs.getBoolean(PREF_MODIFY_COMPOSED_CHANGES_ONLY_PREFIX + getSuffix(fieldId),
                DEFAULT_MODIFY_COMPOSED_CHANGES_ONLY);
    }

    public static boolean shouldModifyComposedChangesOnly(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextInputModification(groupIndex, fieldIndex)
                .mModifyComposedChangesOnly;
    }

    public static final boolean DEFAULT_CONSIDER_COMPOSED_CHANGES_FROM_END = false;

    private static boolean readConsiderComposedChangesFromEnd(final SharedPreferenceManager prefs,
                                                              int fieldId) {
        return prefs.getBoolean(PREF_CONSIDER_COMPOSED_CHANGES_FROM_END_PREFIX + getSuffix(fieldId),
                DEFAULT_CONSIDER_COMPOSED_CHANGES_FROM_END);
    }

    public static boolean shouldConsiderComposedChangesFromEnd(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextInputModification(groupIndex, fieldIndex)
                .mConsiderComposedChangesFromEnd;
    }

    public static final boolean DEFAULT_RESTRICT_TO_INCLUDE = false;

    private static boolean readRestrictToInclude(final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getBoolean(PREF_RESTRICT_TO_INCLUDE_PREFIX + getSuffix(fieldId),
                DEFAULT_RESTRICT_TO_INCLUDE);
    }

    public static boolean shouldRestrictToInclude(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextInputModification(groupIndex, fieldIndex)
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
        return (new CodepointRangeDialogPreference.Reader(prefs,
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

    public static TranslateText[] getTranslateSpecific(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextInputModification(groupIndex, fieldIndex)
                .mTranslateSpecific;
    }

    public static final boolean DEFAULT_TRANSLATE_FULL_MATCH_ONLY = false;

    private static boolean readTranslateFullMatchOnly(final SharedPreferenceManager prefs,
                                                      int fieldId) {
        return prefs.getBoolean(PREF_TRANSLATE_FULL_MATCH_ONLY_PREFIX + getSuffix(fieldId),
                DEFAULT_TRANSLATE_FULL_MATCH_ONLY);
    }

    public static boolean shouldTranslateFullMatchOnly(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextInputModification(groupIndex, fieldIndex)
                .mTranslateFullMatchOnly;
    }

    public static final int DEFAULT_CODEPOINT_SHIFT = 0;

    private static int readShiftCodepoint(final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getInt(PREF_SHIFT_CODEPOINT_PREFIX + getSuffix(fieldId),
                DEFAULT_CODEPOINT_SHIFT);
    }

    public static int getShiftCodepoint(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextInputModification(groupIndex, fieldIndex)
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

    public static boolean shouldSkipExtractingText(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextReturn(groupIndex, fieldIndex)
                .mSkipExtractingText;
    }

    public static final boolean DEFAULT_IGNORE_EXTRACTED_TEXT_MONITOR = false;

    private static boolean readIgnoreExtractedTextMonitor(final SharedPreferenceManager prefs,
                                                          int fieldId) {
        return prefs.getBoolean(PREF_IGNORE_EXTRACTED_TEXT_MONITOR_PREFIX + getSuffix(fieldId),
                DEFAULT_IGNORE_EXTRACTED_TEXT_MONITOR);
    }

    public static boolean shouldIgnoreExtractedTextMonitor(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextReturn(groupIndex, fieldIndex)
                .mIgnoreExtractedTextMonitor;
    }

    public static final boolean DEFAULT_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT = false;

    private static boolean readUpdateSelectionBeforeExtractedText(
            final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getBoolean(
                PREF_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT_PREFIX + getSuffix(fieldId),
                DEFAULT_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT);
    }

    public static boolean shouldUpdateSelectionBeforeExtractedText(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextReturn(groupIndex, fieldIndex)
                .mUpdateSelectionBeforeExtractedText;
    }

    public static final boolean DEFAULT_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES = false;

    private static boolean readUpdateExtractedTextOnlyOnNetChanges(
            final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getBoolean(
                PREF_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES_PREFIX + getSuffix(fieldId),
                DEFAULT_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES);
    }

    public static boolean shouldUpdateExtractedTextOnlyOnNetChanges(int groupIndex,
                                                                    int fieldIndex) {
        return getTestFieldOrBaseForTextReturn(groupIndex, fieldIndex)
                .mUpdateExtractedTextOnlyOnNetChanges;
    }

    public static final boolean DEFAULT_EXTRACT_FULL_TEXT = false;

    private static boolean readExtractFullText(final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getBoolean(PREF_EXTRACT_FULL_TEXT_PREFIX + getSuffix(fieldId),
                DEFAULT_EXTRACT_FULL_TEXT);
    }

    public static boolean shouldExtractFullText(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextReturn(groupIndex, fieldIndex)
                .mExtractFullText;
    }

    public static final int DEFAULT_EXTRACT_MONITOR_TEXT_LIMIT = -1;

    private static int readExtractMonitorTextLimit(final SharedPreferenceManager prefs,
                                                   int fieldId) {
        return prefs.getInt(PREF_LIMIT_EXTRACT_MONITOR_TEXT_PREFIX + getSuffix(fieldId),
                DEFAULT_EXTRACT_MONITOR_TEXT_LIMIT);
    }

    public static int getExtractMonitorTextLimit(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextReturn(groupIndex, fieldIndex)
                .mExtractMonitorTextLimit;
    }

    public static final int DEFAULT_RETURNED_TEXT_LIMIT = -1;

    private static int readReturnedTextLimit(final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getInt(PREF_LIMIT_RETURNED_TEXT_PREFIX + getSuffix(fieldId),
                DEFAULT_RETURNED_TEXT_LIMIT);
    }

    public static int getReturnedTextLimit(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextReturn(groupIndex, fieldIndex)
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

    public static boolean shouldDeleteThroughComposingText(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextComposition(groupIndex, fieldIndex)
                .mDeleteThroughComposingText;
    }

    public static final boolean DEFAULT_KEEP_EMPTY_COMPOSING_POSITION = false;

    private static boolean readKeepEmptyComposingPosition(final SharedPreferenceManager prefs,
                                                          int fieldId) {
        return prefs.getBoolean(PREF_KEEP_EMPTY_COMPOSING_POSITION_PREFIX + getSuffix(fieldId),
                DEFAULT_KEEP_EMPTY_COMPOSING_POSITION);
    }

    public static boolean shouldKeepEmptyComposingPosition(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTextComposition(groupIndex, fieldIndex)
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

    public static boolean shouldSkipTakeSnapshot(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTargetVersion(groupIndex, fieldIndex)
                .mSkipTakeSnapshot;
    }

    public static final boolean DEFAULT_SKIP_GETSURROUNDINGTEXT = false;

    private static boolean readSkipGetSurroundingText(final SharedPreferenceManager prefs,
                                                      int fieldId) {
        return prefs.getBoolean(PREF_SKIP_GETSURROUNDINGTEXT_PREFIX + getSuffix(fieldId),
                DEFAULT_SKIP_GETSURROUNDINGTEXT);
    }

    public static boolean shouldSkipGetSurroundingText(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTargetVersion(groupIndex, fieldIndex)
                .mSkipGetSurroundingText;
    }

    public static final boolean DEFAULT_SKIP_PERFORMSPELLCHECK = false;

    private static boolean readSkipPerformSpellCheck(final SharedPreferenceManager prefs,
                                                     int fieldId) {
        return prefs.getBoolean(PREF_SKIP_PERFORMSPELLCHECK_PREFIX + getSuffix(fieldId),
                DEFAULT_SKIP_PERFORMSPELLCHECK);
    }

    public static boolean shouldSkipPerformSpellCheck(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTargetVersion(groupIndex, fieldIndex)
                .mSkipPerformSpellCheck;
    }

    public static final boolean DEFAULT_SKIP_SETIMECONSUMESINPUT = false;

    private static boolean readSkipSetImeConsumesInput(final SharedPreferenceManager prefs,
                                                       int fieldId) {
        return prefs.getBoolean(PREF_SKIP_SETIMECONSUMESINPUT_PREFIX + getSuffix(fieldId),
                DEFAULT_SKIP_SETIMECONSUMESINPUT);
    }

    public static boolean shouldSkipSetImeConsumesInput(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTargetVersion(groupIndex, fieldIndex)
                .mSkipSetImeConsumesInput;
    }

    public static final boolean DEFAULT_SKIP_COMMITCONTENT = false;

    private static boolean readSkipCommitContent(final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getBoolean(PREF_SKIP_COMMITCONTENT_PREFIX + getSuffix(fieldId),
                DEFAULT_SKIP_COMMITCONTENT);
    }

    public static boolean shouldSkipCommitContent(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTargetVersion(groupIndex, fieldIndex)
                .mSkipCommitContent;
    }

    public static final boolean DEFAULT_SKIP_CLOSECONNECTION = false;

    private static boolean readSkipCloseConnection(final SharedPreferenceManager prefs,
                                                   int fieldId) {
        return prefs.getBoolean(PREF_SKIP_CLOSECONNECTION_PREFIX + getSuffix(fieldId),
                DEFAULT_SKIP_CLOSECONNECTION);
    }

    public static boolean shouldSkipCloseConnection(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTargetVersion(groupIndex, fieldIndex)
                .mSkipCloseConnection;
    }

    public static final boolean DEFAULT_SKIP_DELETESURROUNDINGTEXTINCODEPOINTS = false;

    private static boolean readSkipDeleteSurroundingTextInCodePoints(
            final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getBoolean(PREF_SKIP_DELETESURROUNDINGTEXTINCODEPOINTS_PREFIX + getSuffix(fieldId), DEFAULT_SKIP_DELETESURROUNDINGTEXTINCODEPOINTS);
    }

    public static boolean shouldSkipDeleteSurroundingTextInCodePoints(int groupIndex,
                                                                      int fieldIndex) {
        return getTestFieldOrBaseForTargetVersion(groupIndex, fieldIndex)
                .mSkipDeleteSurroundingTextInCodePoints;
    }

    public static final boolean DEFAULT_SKIP_REQUESTCURSORUPDATES = false;

    private static boolean readSkipRequestCursorUpdates(final SharedPreferenceManager prefs,
                                                        int fieldId) {
        return prefs.getBoolean(PREF_SKIP_REQUESTCURSORUPDATES_PREFIX + getSuffix(fieldId),
                DEFAULT_SKIP_REQUESTCURSORUPDATES);
    }

    public static boolean shouldSkipRequestCursorUpdates(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTargetVersion(groupIndex, fieldIndex)
                .mSkipRequestCursorUpdates;
    }

    public static final boolean DEFAULT_SKIP_COMMITCORRECTION = false;

    private static boolean readSkipCommitCorrection(final SharedPreferenceManager prefs,
                                                    int fieldId) {
        return prefs.getBoolean(PREF_SKIP_COMMITCORRECTION_PREFIX + getSuffix(fieldId),
                DEFAULT_SKIP_COMMITCORRECTION);
    }

    public static boolean shouldSkipCommitCorrection(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTargetVersion(groupIndex, fieldIndex)
                .mSkipCommitCorrection;
    }

    public static final boolean DEFAULT_SKIP_GETSELECTEDTEXT = false;

    private static boolean readSkipGetSelectedText(final SharedPreferenceManager prefs,
                                                   int fieldId) {
        return prefs.getBoolean(PREF_SKIP_GETSELECTEDTEXT_PREFIX + getSuffix(fieldId),
                DEFAULT_SKIP_GETSELECTEDTEXT);
    }

    public static boolean shouldSkipGetSelectedText(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTargetVersion(groupIndex, fieldIndex)
                .mSkipGetSelectedText;
    }

    public static final boolean DEFAULT_SKIP_SETCOMPOSINGREGION = false;

    private static boolean readSkipSetComposingRegion(final SharedPreferenceManager prefs,
                                                      int fieldId) {
        return prefs.getBoolean(PREF_SKIP_SETCOMPOSINGREGION_PREFIX + getSuffix(fieldId),
                DEFAULT_SKIP_SETCOMPOSINGREGION);
    }

    public static boolean shouldSkipSetComposingRegion(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForTargetVersion(groupIndex, fieldIndex)
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

    public static int getUpdateDelay(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForSystemBehavior(groupIndex, fieldIndex)
                .mUpdateDelay;
    }

    public static final int DEFAULT_FINISHCOMPOSINGTEXT_DELAY = 0;

    private static int readFinishComposingTextDelay(final SharedPreferenceManager prefs,
                                                    int fieldId) {
        return prefs.getInt(PREF_FINISHCOMPOSINGTEXT_DELAY_PREFIX + getSuffix(fieldId),
                DEFAULT_FINISHCOMPOSINGTEXT_DELAY);
    }

    public static int getFinishComposingTextDelay(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForSystemBehavior(groupIndex, fieldIndex)
                .mFinishComposingTextDelay;
    }

    public static final int DEFAULT_GETSURROUNDINGTEXT_DELAY = 0;

    private static int readGetSurroundingTextDelay(final SharedPreferenceManager prefs,
                                                   int fieldId) {
        return prefs.getInt(PREF_GETSURROUNDINGTEXT_DELAY_PREFIX + getSuffix(fieldId),
                DEFAULT_GETSURROUNDINGTEXT_DELAY);
    }

    public static int getGetSurroundingTextDelay(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForSystemBehavior(groupIndex, fieldIndex)
                .mGetSurroundingTextDelay;
    }

    public static final int DEFAULT_GETTEXTBEFORECURSOR_DELAY = 0;

    private static int readGetTextBeforeCursorDelay(final SharedPreferenceManager prefs,
                                                    int fieldId) {
        return prefs.getInt(PREF_GETTEXTBEFORECURSOR_DELAY_PREFIX + getSuffix(fieldId),
                DEFAULT_GETTEXTBEFORECURSOR_DELAY);
    }

    public static int getGetTextBeforeCursorDelay(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForSystemBehavior(groupIndex, fieldIndex)
                .mGetTextBeforeCursorDelay;
    }

    public static final int DEFAULT_GETSELECTEDTEXT_DELAY = 0;

    private static int readGetSelectedTextDelay(final SharedPreferenceManager prefs,
                                                int fieldId) {
        return prefs.getInt(PREF_GETSELECTEDTEXT_DELAY_PREFIX + getSuffix(fieldId),
                DEFAULT_GETSELECTEDTEXT_DELAY);
    }

    public static int getGetSelectedTextDelay(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForSystemBehavior(groupIndex, fieldIndex)
                .mGetSelectedTextDelay;
    }

    public static final int DEFAULT_GETTEXTAFTERCURSOR_DELAY = 0;

    private static int readGetTextAfterCursorDelay(final SharedPreferenceManager prefs,
                                                   int fieldId) {
        return prefs.getInt(PREF_GETTEXTAFTERCURSOR_DELAY_PREFIX + getSuffix(fieldId),
                DEFAULT_GETTEXTAFTERCURSOR_DELAY);
    }

    public static int getGetTextAfterCursorDelay(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForSystemBehavior(groupIndex, fieldIndex)
                .mGetTextAfterCursorDelay;
    }

    public static final int DEFAULT_GETCURSORCAPSMODE_DELAY = 0;

    private static int readGetCursorCapsModeDelay(final SharedPreferenceManager prefs,
                                                  int fieldId) {
        return prefs.getInt(PREF_GETCURSORCAPSMODE_DELAY_PREFIX + getSuffix(fieldId),
                DEFAULT_GETCURSORCAPSMODE_DELAY);
    }

    public static int getGetCursorCapsModeDelay(int groupIndex, int fieldIndex) {
        return getTestFieldOrBaseForSystemBehavior(groupIndex, fieldIndex)
                .mGetCursorCapsModeDelay;
    }

    public static final int DEFAULT_GETEXTRACTEDTEXT_DELAY = 0;

    private static int readGetExtractedTextDelay(final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getInt(PREF_GETEXTRACTEDTEXT_DELAY_PREFIX + getSuffix(fieldId),
                DEFAULT_GETEXTRACTEDTEXT_DELAY);
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

    private static String readTestGroupName(final SharedPreferenceManager prefs, int groupId) {
        return prefs.getString(PREF_TEST_GROUP_NAME_PREFIX + GROUP_INFIX + groupId, null);
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
        final String[] testGroupPrefKeyPrefixes = new String[]{
                PREF_TEST_FIELD_IDS_PREFIX,
                PREF_TEST_GROUP_NAME_PREFIX
        };
        for (String prefKeyPrefix : testGroupPrefKeyPrefixes) {
            editor.remove(prefKeyPrefix + GROUP_INFIX + groupId);
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

    private static int getNextId(Collection<Integer> existingIds) {
        int max = -1;
        for (int id : existingIds) {
            if (id > max) {
                max = id;
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

    public static int getTestFieldInputType(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mInputType;
    }

    public static final boolean DEFAULT_NULL_INPUT_TYPE_MULTILINE = false;

    private static boolean readTestFieldNullInputTypeMultiline(final SharedPreferenceManager prefs,
                                                               int fieldId) {
        return prefs.getBoolean(PREF_NULL_INPUT_TYPE_MULTILINE_PREFIX + FIELD_INFIX + fieldId,
                DEFAULT_NULL_INPUT_TYPE_MULTILINE);
    }

    public static boolean getTestFieldNullInputTypeMultiline(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mNullInputTypeMultiline;
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

    public static boolean getTestFieldCreateInputConnection(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mCreateInputConnection;
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

    public static boolean getTestFieldSendSelectionInfo(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mSendSelectionInfo;
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

    public static boolean getTestFieldSendText(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mSendText;
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

    public static int getTestFieldComposingTextBehavior(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mComposingTextBehavior;
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

    public static boolean getTestFieldAllowDeleteSurroundingText(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mAllowDeleteSurroundingText;
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

    public static boolean getTestFieldAllowSettingSelection(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mAllowSettingSelection;
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

    public static int getTestFieldImeOptions(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mImeOptions;
    }

    private static int readTestFieldImeActionId(final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getInt(PREF_IME_ACTION_ID_PREFIX + FIELD_INFIX + fieldId, 0);
    }

    public static int getTestFieldImeActionId(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mImeActionId;
    }

    private static String readTestFieldImeActionLabel(final SharedPreferenceManager prefs,
                                                      int fieldId) {
        return prefs.getString(PREF_IME_ACTION_LABEL_PREFIX + FIELD_INFIX + fieldId, null);
    }

    public static String getTestFieldImeActionLabel(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mImeActionLabel;
    }

    private static String readTestFieldPrivateImeOptions(final SharedPreferenceManager prefs,
                                                         int fieldId) {
        return prefs.getString(PREF_PRIVATE_IME_OPTIONS_PREFIX + FIELD_INFIX + fieldId, null);
    }

    public static String getTestFieldPrivateImeOptions(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mPrivateImeOptions;
    }

    private static boolean readTestFieldSelectAllOnFocus(final SharedPreferenceManager prefs,
                                                         int fieldId) {
        return prefs.getBoolean(PREF_SELECT_ALL_ON_FOCUS_PREFIX + FIELD_INFIX + fieldId, false);
    }

    public static boolean shouldTestFieldSelectAllOnFocus(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mSelectAllOnFocus;
    }

    private static int readTestFieldMaxLength(final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getInt(PREF_MAX_LENGTH_PREFIX + FIELD_INFIX + fieldId, -1);
    }

    public static int getTestFieldMaxLength(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mMaxLength;
    }

    private static boolean readTestFieldAllowUndo(final SharedPreferenceManager prefs,
                                                  int fieldId) {
        return prefs.getBoolean(PREF_ALLOW_UNDO_PREFIX + FIELD_INFIX + fieldId, true);
    }

    public static boolean shouldTestFieldAllowUndo(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mAllowUndo;
    }

    private static Locale[] readTestFieldTextLocales(final SharedPreferenceManager prefs,
                                                     int fieldId) {
        return (new LocaleEntryListPreference.Reader(prefs,
                PREF_TEXT_LOCALES_PREFIX + FIELD_INFIX + fieldId)).readValue();
    }

    public static Locale[] getTestFieldTextLocales(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mTextLocales;
    }

    private static Locale[] readTestFieldImeHintLocales(final SharedPreferenceManager prefs,
                                                        int fieldId) {
        return (new LocaleEntryListPreference.Reader(prefs,
                PREF_IME_HINT_LOCALES_PREFIX + FIELD_INFIX + fieldId)).readValue();
    }

    public static Locale[] getTestFieldImeHintLocales(int groupIndex, int fieldIndex) {
        return getField(groupIndex, fieldIndex).mImeHintLocales;
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

    private static void convertToUseGroupIds(final SharedPreferenceManager prefs) {
        // only convert if new preference data doesn't exist
        if (prefs.contains(PREF_TEST_GROUP_IDS)) {
            return;
        }

        int[] fieldIds = prefs.getIntArray(PREF_TEST_FIELD_IDS_PREFIX, new int[] { 0 });
        Log.d(TAG, "Read " + PREF_TEST_FIELD_IDS_PREFIX + ": " + Arrays.toString(fieldIds));
        if (fieldIds == null || fieldIds.length < 1) {
            // there should always be at least 1 field
            Log.e(TAG, "No test fields");
            fieldIds = new int[] { 0 };
        }

        int[] groupFieldCounts = prefs.getIntArray(PREF_TEST_GROUP_FIELD_COUNTS,
                new int[] { fieldIds.length });
        Log.d(TAG, "Read " + PREF_TEST_GROUP_FIELD_COUNTS + ": "
                + Arrays.toString(groupFieldCounts));
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
        int[] groupIds = new int[groupCount];
        for (int i = 0; i < groupCount; i++) {
            groupIds[i] = i;
            String oldGroupNamePrefKey = PREF_TEST_GROUP_NAME_PREFIX + "_" + i;
            String name = prefs.getString(oldGroupNamePrefKey, null);
            String newGroupNamePrefKey = PREF_TEST_GROUP_NAME_PREFIX + GROUP_INFIX + i;
            prefs.setString(newGroupNamePrefKey, name);
            Log.d(TAG, "Write " + newGroupNamePrefKey + ": " + name);
            String fieldIdsPrefKey = PREF_TEST_FIELD_IDS_PREFIX + GROUP_INFIX + i;
            prefs.setIntArray(fieldIdsPrefKey, groupFieldIds[i]);
            Log.d(TAG, "Write " + fieldIdsPrefKey + ": " + Arrays.toString(groupFieldIds[i]));

            prefs.remove(oldGroupNamePrefKey);
            Log.d(TAG, "Delete " + oldGroupNamePrefKey);
        }
        prefs.setIntArray(PREF_TEST_GROUP_IDS, groupIds);

        prefs.remove(PREF_TEST_FIELD_IDS_PREFIX);
        Log.d(TAG, "Delete " + PREF_TEST_FIELD_IDS_PREFIX);
        prefs.remove(PREF_TEST_GROUP_FIELD_COUNTS);
        Log.d(TAG, "Delete " + PREF_TEST_GROUP_FIELD_COUNTS);
    }
}
