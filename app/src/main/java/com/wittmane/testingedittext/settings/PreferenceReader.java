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

import static com.wittmane.testingedittext.settings.Settings.COMPOSING_TEXT_BEHAVIOR_INVISIBLE;
import static com.wittmane.testingedittext.settings.Settings.COMPOSING_TEXT_BEHAVIOR_COMPOSE;
import static com.wittmane.testingedittext.settings.Settings.COMPOSING_TEXT_BEHAVIOR_COMMIT;
import static com.wittmane.testingedittext.settings.Settings.COMPOSING_TEXT_BEHAVIOR_IGNORE;
import static com.wittmane.testingedittext.settings.PreferenceKeys.*;

import android.os.Build;
import android.text.InputType;
import android.text.Spanned;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.Nullable;

import com.wittmane.testingedittext.settings.preferences.CodepointRangeDialogPreference;
import com.wittmane.testingedittext.settings.preferences.LocaleEntryListPreference;
import com.wittmane.testingedittext.settings.preferences.TextListPreference;
import com.wittmane.testingedittext.settings.preferences.TextTranslateListPreference;

import java.util.Locale;

//TODO: (EW) make package private
public class PreferenceReader {
    private static final String TAG = PreferenceReader.class.getSimpleName();

    //#region data types
    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_BOOLEAN = 1;
    public static final int TYPE_INT = 2;
    public static final int TYPE_LONG = 3;
    public static final int TYPE_FLOAT = 4;
    public static final int TYPE_STRING = 5;
    public static final int TYPE_SPANNED = 6;
    public static final int TYPE_CHAR_SEQUENCE = 7;
    public static final int TYPE_INT_ARRAY = 8;
    public static final int TYPE_STRING_ARRAY = 9;
    public static final int TYPE_STRING_SET = 10;
    public static final int TYPE_INT_RANGE = 11;
    public static final int TYPE_LOCALE_ARRAY = 12;
    public static final int TYPE_TEXT_LIST_STRING = 13;
    public static final int TYPE_TEXT_LIST_TRANSLATE_TEXT = 14;

    public static int prefDataType(String keyOrPrefix) {
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
    //#endregion

    //#region defaults
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

    public static final String[] DEFAULT_RESTRICT_SPECIFIC = new String[0];
    public static final IntRange DEFAULT_RESTRICT_RANGE = null;
    public static final TranslateText[] DEFAULT_TRANSLATE_SPECIFIC = new TranslateText[0];

    //TODO: (EW) remove constants to consolidate and have this method manage whatever used the
    // constants before
    public static boolean getPrefDefaultBoolean(String keyOrPrefix) {
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
    public static int getPrefDefaultInt(String keyOrPrefix) {
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
    public static long getPrefDefaultLong(String keyOrPrefix) {
        switch (keyOrPrefix) {
            default:
                Log.e(TAG, "long default missing for " + keyOrPrefix
                        + (prefDataType(keyOrPrefix) != TYPE_LONG ? " (not a long)" : ""));
                return 0;
        }
    }
    public static long getPrefDefaultFloat(String keyOrPrefix) {
        switch (keyOrPrefix) {
            default:
                Log.e(TAG, "float default missing for " + keyOrPrefix
                        + (prefDataType(keyOrPrefix) != TYPE_FLOAT ? " (not a float)" : ""));
                return 0;
        }
    }
    public static String getPrefDefaultString(String keyOrPrefix) {
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
    public static Spanned getPrefDefaultSpanned(String keyOrPrefix) {
        switch (keyOrPrefix) {
            default:
                Log.e(TAG, "Spanned default missing for " + keyOrPrefix
                        + (prefDataType(keyOrPrefix) != TYPE_SPANNED ? " (not a Spanned)" : ""));
                return null;
        }
    }
    public static CharSequence getPrefDefaultCharSequence(String keyOrPrefix) {
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
    public static int[] getPrefDefaultIntArray(String keyOrPrefix) {
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
    public static String[] getPrefDefaultStringArray(String keyOrPrefix) {
        switch (keyOrPrefix) {
            default:
                Log.e(TAG, "String[] default missing for " + keyOrPrefix
                        + (prefDataType(keyOrPrefix) != TYPE_STRING_ARRAY
                        ? " (not a String[])"
                        : ""));
                return null;
        }
    }

    private static boolean prefAllowsNullIntArray(String keyOrPrefix) {
        switch (keyOrPrefix) {
            case PREF_TEST_GROUP_IDS:
            case PREF_TEST_FIELD_IDS_PREFIX:
                return false;
            default:
                return true;
        }
    }
    //#endregion

    private final SharedPreferenceManager mPrefs;

    public PreferenceReader(SharedPreferenceManager prefs) {
        mPrefs = prefs;
    }

    public boolean contains(String prefKey) {
        return mPrefs.contains(prefKey);
    }

    //#region generic read methods
    //#region core read methods
    private boolean readBoolean(String prefKey, String prefKeyOrPrefix) {
        return mPrefs.getBoolean(prefKey, getPrefDefaultBoolean(prefKeyOrPrefix));
    }

    public int readInt(String prefKey, String prefKeyOrPrefix) {
        return mPrefs.getInt(prefKey, getPrefDefaultInt(prefKeyOrPrefix));
    }

    public String readString(String prefKey, String prefKeyOrPrefix) {
        return mPrefs.getString(prefKey, getPrefDefaultString(prefKeyOrPrefix));
    }

    private CharSequence readCharSequence(String prefKey, String prefKeyOrPrefix) {
        return mPrefs.getCharSequence(prefKey, getPrefDefaultCharSequence(prefKeyOrPrefix));
    }

    private int[] readIntArray(String prefKey, String prefKeyOrPrefix) {
        int[] value = mPrefs.getIntArray(prefKey, getPrefDefaultIntArray(prefKeyOrPrefix));
        if (value == null && !prefAllowsNullIntArray(prefKeyOrPrefix)) {
            Log.e(TAG, "Preference " + prefKey + " has a value of null.");
            return new int[0];
        }
        return value;
    }
    //#endregion

    //TODO: (EW) should these read* methods be private and only called internally and have helper
    // methods to load blocks of data?

    //#region base property read methods
    public boolean readBoolean(String prefKey) {
        return readBoolean(prefKey, prefKey);
    }

    public int readInt(String prefKey) {
        return readInt(prefKey, prefKey);
    }

    public String readString(String prefKey) {
        return readString(prefKey, prefKey);
    }

    public int[] readIntArray(String prefKey) {
        return readIntArray(prefKey, prefKey);
    }
    //#endregion

    //#region test group property read methods
    public String readTestGroupString(int groupId, String prefKeyPrefix) {
        return readString(prefKeyPrefix + GROUP_INFIX + groupId, prefKeyPrefix);
    }

    public int[] readTestGroupIntArray(int groupId, String prefKeyPrefix) {
        return readIntArray(prefKeyPrefix + GROUP_INFIX + groupId, prefKeyPrefix);
    }
    //#endregion

    //#region test field property read methods
    public boolean readTestFieldBoolean(int fieldId, String prefKeyPrefix) {
        return readBoolean(prefKeyPrefix + getSuffix(fieldId), prefKeyPrefix);
    }

    public int readTestFieldInt(int fieldId, String prefKeyPrefix) {
        return readInt(prefKeyPrefix + getSuffix(fieldId), prefKeyPrefix);
    }

    public String readTestFieldString(int fieldId, String prefKeyPrefix) {
        return readString(prefKeyPrefix + getSuffix(fieldId), prefKeyPrefix);
    }

    private CharSequence readTestFieldCharSequence(int fieldId, String prefKeyPrefix) {
        return readCharSequence(prefKeyPrefix + getSuffix(fieldId), prefKeyPrefix);
    }


    //TODO: (EW) consider decoupling these method from the specific preference class (maybe have
    // the preference classes call this class to read)

    //#region preference class specific methods
    private static Locale[] readTestFieldTextLocales(final SharedPreferenceManager prefs,
                                                     int fieldId) {
        return (new LocaleEntryListPreference.DataManager(prefs,
                PREF_TEXT_LOCALES_PREFIX + getSuffix(fieldId))).readValue();
    }

    private static Locale[] readTestFieldImeHintLocales(final SharedPreferenceManager prefs,
                                                        int fieldId) {
        return (new LocaleEntryListPreference.DataManager(prefs,
                PREF_IME_HINT_LOCALES_PREFIX + getSuffix(fieldId))).readValue();
    }

    private String[] readRestrictSpecific(int fieldId) {
        TextList<String> textList = (new TextListPreference.DataManager(mPrefs,
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

    @Nullable
    private IntRange readRestrictRange(int fieldId) {
        return (new CodepointRangeDialogPreference.DataManager(mPrefs,
                PREF_RESTRICT_RANGE_PREFIX + getSuffix(fieldId)))
                .readValue();
    }

    private TranslateText[] readTranslateSpecific(int fieldId) {
        TextList<TranslateText> textList =
                (new TextTranslateListPreference.DataManager(mPrefs,
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
    //#endregion

    private static String getSuffix(int fieldId) {
        return fieldId == BASE_FIELD_ID ? BASE_SUFFIX : (FIELD_INFIX + fieldId);
    }
    //#endregion
    //#endregion

    //#region object loading methods
    public void loadTestFieldOrDefaultSetting(String prefKeyPrefix,
                                              AppLevelDefaults testFieldOrDefault) {
        int fieldId = testFieldOrDefault instanceof TestField
                ? ((TestField) testFieldOrDefault).mId
                : BASE_FIELD_ID;
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
                testFieldOrDefault.mRestrictSpecific = readRestrictSpecific(fieldId);
                break;
            case PREF_RESTRICT_RANGE_PREFIX:
                testFieldOrDefault.mRestrictRange = readRestrictRange(fieldId);
                break;
            case PREF_TRANSLATE_SPECIFIC_PREFIX:
                testFieldOrDefault.mTranslateSpecific = readTranslateSpecific(fieldId);
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

    public void loadTestFieldSetting(String prefKeyPrefix, TestField testField) {
        int fieldId = testField.mId;
        switch (prefKeyPrefix) {
            case PREF_IME_LABEL_TEXT_PREFIX:
                testField.mLabelText =
                        readTestFieldCharSequence(fieldId, PREF_IME_LABEL_TEXT_PREFIX);
                break;
            case PREF_IME_DEFAULT_TEXT_PREFIX:
                testField.mDefaultText =
                        readTestFieldCharSequence(fieldId, PREF_IME_DEFAULT_TEXT_PREFIX);
                break;
            case PREF_IME_HINT_TEXT_PREFIX:
                testField.mHintText = readTestFieldCharSequence(fieldId, PREF_IME_HINT_TEXT_PREFIX);
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
                // the following settings only apply to null input types since as far as I can tell,
                // the others are expected to create the input connection to fully support rich
                // input, are expected to send selection info (possibly based on the same
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

    //#region compound preferences
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
    //#endregion
    //#endregion
}
