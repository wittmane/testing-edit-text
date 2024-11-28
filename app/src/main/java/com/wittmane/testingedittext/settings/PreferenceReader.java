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

import static com.wittmane.testingedittext.settings.EditorSettings.COMPOSING_TEXT_BEHAVIOR_INVISIBLE;
import static com.wittmane.testingedittext.settings.EditorSettings.COMPOSING_TEXT_BEHAVIOR_COMPOSE;
import static com.wittmane.testingedittext.settings.EditorSettings.COMPOSING_TEXT_BEHAVIOR_COMMIT;
import static com.wittmane.testingedittext.settings.EditorSettings.COMPOSING_TEXT_BEHAVIOR_IGNORE;
import static com.wittmane.testingedittext.settings.PreferenceKey.createFieldDefaultKey;
import static com.wittmane.testingedittext.settings.PreferenceKey.createFieldKey;
import static com.wittmane.testingedittext.settings.PreferenceKeys.*;

import android.os.Build;
import android.text.InputType;
import android.text.Spanned;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.Nullable;

import com.wittmane.testingedittext.function.BiFunction;
import com.wittmane.testingedittext.function.Function;
import com.wittmane.testingedittext.function.Predicate;
import com.wittmane.testingedittext.function.TriFunction;
import com.wittmane.testingedittext.settings.datamanager.DataManager;
import com.wittmane.testingedittext.settings.datamanager.IntRangeDataManager;
import com.wittmane.testingedittext.settings.datamanager.LocaleArrayDataManager;
import com.wittmane.testingedittext.settings.datamanager.StringTextListDataManager;
import com.wittmane.testingedittext.settings.datamanager.TranslateTextTextListDataManager;

import java.util.Locale;

/* package */ class PreferenceReader {
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
    public static boolean getPrefDefaultBoolean(String keyOrPrefix) {
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
            case PREF_SHOW_REFERENCE_EDITTEXT:
                return false;
            case PREF_ALLOW_UNDO_PREFIX:
                return true;
            default:
                Log.e(TAG, "boolean default missing for " + keyOrPrefix
                        + (prefDataType(keyOrPrefix) != TYPE_BOOLEAN ? " (not a boolean)" : ""));
                return false;
        }
    }

    public static int getPrefDefaultInt(String keyOrPrefix) {
        switch (keyOrPrefix) {
            case PREF_MAX_LENGTH_PREFIX:
            case PREF_LIMIT_EXTRACT_MONITOR_TEXT_PREFIX:
            case PREF_LIMIT_RETURNED_TEXT_PREFIX:
                return -1;
            case PREF_IME_ACTION_ID_PREFIX:
            case PREF_SHIFT_CODEPOINT_PREFIX:
            case PREF_UPDATE_DELAY_PREFIX:
            case PREF_FINISHCOMPOSINGTEXT_DELAY_PREFIX:
            case PREF_GETSURROUNDINGTEXT_DELAY_PREFIX:
            case PREF_GETTEXTBEFORECURSOR_DELAY_PREFIX:
            case PREF_GETSELECTEDTEXT_DELAY_PREFIX:
            case PREF_GETTEXTAFTERCURSOR_DELAY_PREFIX:
            case PREF_GETCURSORCAPSMODE_DELAY_PREFIX:
            case PREF_GETEXTRACTEDTEXT_DELAY_PREFIX:
                return 0;
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

    public static float getPrefDefaultFloat(String keyOrPrefix) {
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
                        + (prefDataType(keyOrPrefix) != TYPE_CHAR_SEQUENCE
                                ? " (not a CharSequence)"
                                : ""));
                return null;
        }
    }

    public static int[] getPrefDefaultIntArray(String keyOrPrefix) {
        switch (keyOrPrefix) {
            case PREF_TEST_GROUP_IDS:
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

    private final @Nullable SharedPreferenceManager mPrefs;

    public PreferenceReader(@Nullable SharedPreferenceManager prefs) {
        mPrefs = prefs;
    }

    public boolean contains(PreferenceKey prefKey) {
        return prefKey != null && contains(prefKey.toString());
    }

    public boolean contains(String prefKey) {
        return mPrefs != null && mPrefs.contains(prefKey);
    }

    public static class PrefInfo<T> {
        public T value;
        public boolean valueIsDefault;
    }

    //#region generic read methods
    public <T> PrefInfo<T> readWithInfo(PreferenceKey prefKey,
            BiFunction<SharedPreferenceManager, String, DataManager<T>> getDataManager) {
        DataManager<T> dataManager = getDataManager.apply(mPrefs,
                prefKey == null ? null : prefKey.toString());
        return readWithInfo(prefKey, key -> dataManager.readDefaultValue(),
                (prefs, key, defaultValue) -> dataManager.readValue());
    }

    private <T> PrefInfo<T> readWithInfo(PreferenceKey prefKey,
                                         Function<String, T> getDefault,
                                         TriFunction<SharedPreferenceManager,String,T,T> getValue) {
        return readWithInfo(prefKey, getDefault, getValue, null);
    }

    private <T> PrefInfo<T> readWithInfo(PreferenceKey prefKey,
                                         Function<String, T> getDefault,
                                         TriFunction<SharedPreferenceManager,String,T,T> getValue,
                                         Predicate<String> allowNull) {
        T defaultValue = getDefault.apply(prefKey == null ? null : prefKey.getStem());
        PrefInfo<T> info = new PrefInfo<>();
        if (!contains(prefKey)) {
            info.value = defaultValue;
            info.valueIsDefault = true;
        } else {
            info.value = getValue.apply(mPrefs, prefKey.toString(), defaultValue);
            if (info.value == null && allowNull != null && !allowNull.test(prefKey.getStem())) {
                Log.e(TAG, "Preference " + prefKey + " has a value of null.");
                info.value = defaultValue;
            }
            info.valueIsDefault = SharedPreferenceManager.equals(info.value, defaultValue);
        }
        return info;
    }

    public PrefInfo<Boolean> readBooleanWithInfo(PreferenceKey prefKey) {
        return readWithInfo(prefKey, PreferenceReader::getPrefDefaultBoolean,
                SharedPreferenceManager::getBoolean);
    }

    public boolean readBoolean(PreferenceKey prefKey) {
        return readBooleanWithInfo(prefKey).value;
    }

    public PrefInfo<Integer> readIntWithInfo(PreferenceKey prefKey) {
        return readWithInfo(prefKey, PreferenceReader::getPrefDefaultInt,
                SharedPreferenceManager::getInt);
    }

    public int readInt(PreferenceKey prefKey) {
        return readIntWithInfo(prefKey).value;
    }

    public PrefInfo<Long> readLongWithInfo(PreferenceKey prefKey) {
        return readWithInfo(prefKey, PreferenceReader::getPrefDefaultLong,
                SharedPreferenceManager::getLong);
    }

    public long readLong(PreferenceKey prefKey) {
        return readLongWithInfo(prefKey).value;
    }

    public PrefInfo<Float> readFloatWithInfo(PreferenceKey prefKey) {
        return readWithInfo(prefKey, PreferenceReader::getPrefDefaultFloat,
                SharedPreferenceManager::getFloat);
    }

    public float readFloat(PreferenceKey prefKey) {
        return readFloatWithInfo(prefKey).value;
    }

    public PrefInfo<String> readStringWithInfo(PreferenceKey prefKey) {
        return readWithInfo(prefKey, PreferenceReader::getPrefDefaultString,
                SharedPreferenceManager::getString);
    }

    public String readString(PreferenceKey prefKey) {
        return readStringWithInfo(prefKey).value;
    }

    public PrefInfo<Spanned> readSpannedWithInfo(PreferenceKey prefKey) {
        return readWithInfo(prefKey, PreferenceReader::getPrefDefaultSpanned,
                SharedPreferenceManager::getSpanned);
    }

    public Spanned readSpanned(PreferenceKey prefKey) {
        return readSpannedWithInfo(prefKey).value;
    }

    public PrefInfo<CharSequence> readCharSequenceWithInfo(PreferenceKey prefKey) {
        return readWithInfo(prefKey, PreferenceReader::getPrefDefaultCharSequence,
                SharedPreferenceManager::getCharSequence);
    }

    public CharSequence readCharSequence(PreferenceKey prefKey) {
        return readCharSequenceWithInfo(prefKey).value;
    }

    public PrefInfo<int[]> readIntArrayWithInfo(PreferenceKey prefKey) {
        return readWithInfo(prefKey, PreferenceReader::getPrefDefaultIntArray,
                SharedPreferenceManager::getIntArray,
                PreferenceReader::prefAllowsNullIntArray);
    }

    public int[] readIntArray(PreferenceKey prefKey) {
        return readIntArrayWithInfo(prefKey).value;
    }

    public PrefInfo<String[]> readStringArrayWithInfo(PreferenceKey prefKey) {
        return readWithInfo(prefKey, PreferenceReader::getPrefDefaultStringArray,
                SharedPreferenceManager::getStringArray);
    }

    public String[] readStringArray(PreferenceKey prefKey) {
        return readStringArrayWithInfo(prefKey).value;
    }

    public PrefInfo<Locale[]> readLocaleArrayWithInfo(PreferenceKey prefKey) {
        return readWithInfo(prefKey, LocaleArrayDataManager::new);
    }

    public Locale[] readLocaleArray(PreferenceKey prefKey) {
        return readLocaleArrayWithInfo(prefKey).value;
    }

    public PrefInfo<TextList<String>> readTextListStringWithInfo(PreferenceKey prefKey) {
        return readWithInfo(prefKey, StringTextListDataManager::new);
    }

    public TextList<String> readTextListString(PreferenceKey prefKey) {
        return readTextListStringWithInfo(prefKey).value;
    }

    public PrefInfo<TextList<TranslateText>> readTextListTranslateTextWithInfo(
            PreferenceKey prefKey) {
        return readWithInfo(prefKey, TranslateTextTextListDataManager::new);
    }

    public TextList<TranslateText> readTextListTranslateText(PreferenceKey prefKey) {
        return readTextListTranslateTextWithInfo(prefKey).value;
    }

    public PrefInfo<IntRange> readIntRangeWithInfo(PreferenceKey prefKey) {
        return readWithInfo(prefKey, IntRangeDataManager::new);
    }

    public IntRange readIntRange(PreferenceKey prefKey) {
        return readIntRangeWithInfo(prefKey).value;
    }
    //#endregion

    //#region object loading methods
    /* package */ void loadTestFieldDefaultableSettings(AppLevelFieldDefaults testFieldOrDefault) {
        for (String prefKeyPrefix : DEFAULTABLE_TEST_FIELD_PREF_KEY_PREFIXES) {
            PreferenceKey prefKey = testFieldOrDefault instanceof TestField
                    ? createFieldKey(prefKeyPrefix, ((TestField) testFieldOrDefault).mId)
                    : createFieldDefaultKey(prefKeyPrefix);
            if (!loadTestFieldDefaultableSetting(prefKey, testFieldOrDefault)) {
                Log.e(TAG, "Test field defaultable preference " + prefKey + " wasn't processed");
            }
        }
    }

    /* package*/ boolean loadTestFieldDefaultableSetting(PreferenceKey prefKey,
                                                         AppLevelFieldDefaults testFieldOrDefault) {
        if (prefKey == null) {
            return false;
        }
        switch (prefKey.getStem()) {
            case PREF_MODIFY_COMMITTED_TEXT_PREFIX:
                testFieldOrDefault.mModifyCommittedText = readBoolean(prefKey);
                break;
            case PREF_MODIFY_COMPOSED_TEXT_PREFIX:
                testFieldOrDefault.mModifyComposedText = readBoolean(prefKey);
                break;
            case PREF_MODIFY_COMPOSED_CHANGES_ONLY_PREFIX:
                testFieldOrDefault.mModifyComposedChangesOnly = readBoolean(prefKey);
                break;
            case PREF_CONSIDER_COMPOSED_CHANGES_FROM_END_PREFIX:
                testFieldOrDefault.mConsiderComposedChangesFromEnd = readBoolean(prefKey);
                break;
            case PREF_RESTRICT_TO_INCLUDE_PREFIX:
                testFieldOrDefault.mRestrictToInclude = readBoolean(prefKey);
                break;
            case PREF_RESTRICT_SPECIFIC_PREFIX:
                testFieldOrDefault.mRestrictSpecific = getStrings(readTextListString(prefKey));
                break;
            case PREF_RESTRICT_RANGE_PREFIX:
                testFieldOrDefault.mRestrictRange = readIntRange(prefKey);
                break;
            case PREF_TRANSLATE_SPECIFIC_PREFIX:
                testFieldOrDefault.mTranslateSpecific =
                        getTranslateTexts(readTextListTranslateText(prefKey));
                break;
            case PREF_TRANSLATE_FULL_MATCH_ONLY_PREFIX:
                testFieldOrDefault.mTranslateFullMatchOnly = readBoolean(prefKey);
                break;
            case PREF_SHIFT_CODEPOINT_PREFIX:
                testFieldOrDefault.mShiftCodepoint = readInt(prefKey);
                break;

            case PREF_SKIP_EXTRACTING_TEXT_PREFIX:
                testFieldOrDefault.mSkipExtractingText = readBoolean(prefKey);
                break;
            case PREF_IGNORE_EXTRACTED_TEXT_MONITOR_PREFIX:
                testFieldOrDefault.mIgnoreExtractedTextMonitor = readBoolean(prefKey);
                break;
            case PREF_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT_PREFIX:
                testFieldOrDefault.mUpdateSelectionBeforeExtractedText = readBoolean(prefKey);
                break;
            case PREF_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES_PREFIX:
                testFieldOrDefault.mUpdateExtractedTextOnlyOnNetChanges = readBoolean(prefKey);
                break;
            case PREF_EXTRACT_FULL_TEXT_PREFIX:
                testFieldOrDefault.mExtractFullText = readBoolean(prefKey);
                break;
            case PREF_LIMIT_EXTRACT_MONITOR_TEXT_PREFIX:
                testFieldOrDefault.mExtractMonitorTextLimit = readInt(prefKey);
                break;
            case PREF_LIMIT_RETURNED_TEXT_PREFIX:
                testFieldOrDefault.mReturnedTextLimit = readInt(prefKey);
                break;

            case PREF_DELETE_THROUGH_COMPOSING_TEXT_PREFIX:
                testFieldOrDefault.mDeleteThroughComposingText = readBoolean(prefKey);
                break;
            case PREF_KEEP_EMPTY_COMPOSING_POSITION_PREFIX:
                testFieldOrDefault.mKeepEmptyComposingPosition = readBoolean(prefKey);
                break;

            case PREF_SKIP_TAKESNAPSHOT_PREFIX:
                testFieldOrDefault.mSkipTakeSnapshot = readBoolean(prefKey);
                break;
            case PREF_SKIP_GETSURROUNDINGTEXT_PREFIX:
                testFieldOrDefault.mSkipGetSurroundingText = readBoolean(prefKey);
                break;
            case PREF_SKIP_PERFORMSPELLCHECK_PREFIX:
                testFieldOrDefault.mSkipPerformSpellCheck = readBoolean(prefKey);
                break;
            case PREF_SKIP_SETIMECONSUMESINPUT_PREFIX:
                testFieldOrDefault.mSkipSetImeConsumesInput = readBoolean(prefKey);
                break;
            case PREF_SKIP_COMMITCONTENT_PREFIX:
                testFieldOrDefault.mSkipCommitContent = readBoolean(prefKey);
                break;
            case PREF_SKIP_CLOSECONNECTION_PREFIX:
                testFieldOrDefault.mSkipCloseConnection = readBoolean(prefKey);
                break;
            case PREF_SKIP_DELETESURROUNDINGTEXTINCODEPOINTS_PREFIX:
                testFieldOrDefault.mSkipDeleteSurroundingTextInCodePoints = readBoolean(prefKey);
                break;
            case PREF_SKIP_REQUESTCURSORUPDATES_PREFIX:
                testFieldOrDefault.mSkipRequestCursorUpdates = readBoolean(prefKey);
                break;
            case PREF_SKIP_COMMITCORRECTION_PREFIX:
                testFieldOrDefault.mSkipCommitCorrection = readBoolean(prefKey);
                break;
            case PREF_SKIP_GETSELECTEDTEXT_PREFIX:
                testFieldOrDefault.mSkipGetSelectedText = readBoolean(prefKey);
                break;
            case PREF_SKIP_SETCOMPOSINGREGION_PREFIX:
                testFieldOrDefault.mSkipSetComposingRegion = readBoolean(prefKey);
                break;

            case PREF_UPDATE_DELAY_PREFIX:
                testFieldOrDefault.mUpdateDelay = readInt(prefKey);
                break;
            case PREF_FINISHCOMPOSINGTEXT_DELAY_PREFIX:
                testFieldOrDefault.mFinishComposingTextDelay = readInt(prefKey);
                break;
            case PREF_GETSURROUNDINGTEXT_DELAY_PREFIX:
                testFieldOrDefault.mGetSurroundingTextDelay = readInt(prefKey);
                break;
            case PREF_GETTEXTBEFORECURSOR_DELAY_PREFIX:
                testFieldOrDefault.mGetTextBeforeCursorDelay = readInt(prefKey);
                break;
            case PREF_GETSELECTEDTEXT_DELAY_PREFIX:
                testFieldOrDefault.mGetSelectedTextDelay = readInt(prefKey);
                break;
            case PREF_GETTEXTAFTERCURSOR_DELAY_PREFIX:
                testFieldOrDefault.mGetTextAfterCursorDelay = readInt(prefKey);
                break;
            case PREF_GETCURSORCAPSMODE_DELAY_PREFIX:
                testFieldOrDefault.mGetCursorCapsModeDelay = readInt(prefKey);
                break;
            case PREF_GETEXTRACTEDTEXT_DELAY_PREFIX:
                testFieldOrDefault.mGetExtractedTextDelay = readInt(prefKey);
                break;
            default:
                return false;
        }
        return true;
    }

    private static String[] getStrings(TextList<String> textList) {
        if (textList == null) {
            return null;
        }
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

    private static TranslateText[] getTranslateTexts(TextList<TranslateText> textList) {
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

    /* package */ void loadTestFieldSpecificSettings(TestField testField) {
        // intentionally skipping some preferences since they are read in groups, so listing them
        // all would just read all of them multiple times. leaving them commented out here for
        // visibility.
        final String[] testFieldPrefKeyPrefixes = new String[]{
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
        for (String prefKeyPrefix : testFieldPrefKeyPrefixes) {
            PreferenceKey prefKey = createFieldKey(prefKeyPrefix, testField.mId);
            if (!loadTestFieldSpecificSetting(prefKey, testField)) {
                Log.e(TAG, "Test field specific preference " + prefKey + " wasn't processed");
            }
        }
    }

    /* package*/ boolean loadTestFieldSpecificSetting(PreferenceKey prefKey, TestField testField) {
        if (prefKey == null) {
            return false;
        }
        int fieldId = testField.mId;
        switch (prefKey.getStem()) {
            case PREF_IME_LABEL_TEXT_PREFIX:
                testField.mLabelText = readCharSequence(prefKey);
                break;
            case PREF_IME_DEFAULT_TEXT_PREFIX:
                testField.mDefaultText = readCharSequence(prefKey);
                break;
            case PREF_IME_HINT_TEXT_PREFIX:
                testField.mHintText = readCharSequence(prefKey);
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
                testField.mNullInputTypeMultiline = readBoolean(
                        createFieldKey(PREF_NULL_INPUT_TYPE_MULTILINE_PREFIX, fieldId));
                // the following settings only apply to null input types since as far as I can tell,
                // the others are expected to create the input connection to fully support rich
                // input, are expected to send selection info (possibly based on the same
                // understanding for them needing to create an input connection), are expected to
                // return text as part of fully supporting rich input, and are expected to support
                // all of the rich editing specified in documentation for InputConnection (that
                // isn't noted as being optional)
                if (testField.mInputType == EditorInfo.TYPE_NULL) {
                    testField.mCreateInputConnection = readBoolean(createFieldKey(
                            PREF_NULL_INPUT_TYPE_CREATE_INPUT_CONNECTION_PREFIX, fieldId));
                    testField.mSendSelectionInfo = readBoolean(createFieldKey(
                            PREF_NULL_INPUT_TYPE_SEND_SELECTION_INFO_PREFIX, fieldId));
                    testField.mSendText = readBoolean(createFieldKey(
                            PREF_NULL_INPUT_TYPE_SEND_TEXT_PREFIX, fieldId));
                    if (testField.mCreateInputConnection) {
                        testField.mComposingTextBehavior = getComposingTextBehaviorInt(
                                readString(createFieldKey(
                                        PREF_NULL_INPUT_TYPE_COMPOSING_TEXT_BEHAVIOR_PREFIX,
                                        fieldId)));
                        testField.mAllowDeleteSurroundingText = readBoolean(createFieldKey(
                                PREF_NULL_INPUT_TYPE_ALLOW_DELETE_SURROUNDING_TEXT_PREFIX,
                                fieldId));
                        testField.mAllowSettingSelection = readBoolean(createFieldKey(
                                PREF_NULL_INPUT_TYPE_ALLOW_SETTING_SELECTION_PREFIX, fieldId));
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
                    testField.mCreateInputConnection = true;
                    testField.mSendSelectionInfo = true;
                    testField.mSendText = true;
                    testField.mComposingTextBehavior = COMPOSING_TEXT_BEHAVIOR_COMPOSE;
                    testField.mAllowDeleteSurroundingText = true;
                    testField.mAllowSettingSelection = true;
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
                testField.mImeActionId = readInt(prefKey);
                break;
            case PREF_IME_ACTION_LABEL_PREFIX:
                testField.mImeActionLabel = readString(prefKey);
                break;
            case PREF_PRIVATE_IME_OPTIONS_PREFIX:
                testField.mPrivateImeOptions = readString(prefKey);
                break;
            case PREF_SELECT_ALL_ON_FOCUS_PREFIX:
                testField.mSelectAllOnFocus = readBoolean(prefKey);
                break;
            case PREF_MAX_LENGTH_PREFIX:
                testField.mMaxLength = readInt(prefKey);
                break;
            case PREF_ALLOW_UNDO_PREFIX:
                testField.mAllowUndo = readBoolean(prefKey);
                break;
            case PREF_TEXT_LOCALES_PREFIX:
                testField.mTextLocales = readLocaleArray(prefKey);
                break;
            case PREF_IME_HINT_LOCALES_PREFIX:
                testField.mImeHintLocales = readLocaleArray(prefKey);
                break;

            case PREF_OVERRIDE_TEXT_INPUT_MODIFICATION_PREFIX:
                testField.mOverrideTextInputModification = readBoolean(prefKey);
                break;
            case PREF_OVERRIDE_TEXT_RETURN_PREFIX:
                testField.mOverrideTextReturn = readBoolean(prefKey);
                break;
            case PREF_OVERRIDE_TEXT_COMPOSITION_PREFIX:
                testField.mOverrideTextComposition = readBoolean(prefKey);
                break;
            case PREF_OVERRIDE_TARGET_VERSION_SIMULATION_PREFIX:
                testField.mOverrideTargetVersion = readBoolean(prefKey);
                break;
            case PREF_OVERRIDE_SYSTEM_BEHAVIOR_SIMULATION_PREFIX:
                testField.mOverrideSystemBehavior = readBoolean(prefKey);
                break;
            default:
                return false;
        }
        return true;
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
                return COMPOSING_TEXT_BEHAVIOR_INVISIBLE;
        }
    }

    //#region compound preferences
    protected int readTestFieldInputType(int fieldId) {
        String inputTypeClass = readString(createFieldKey(PREF_INPUT_TYPE_CLASS_PREFIX, fieldId));
        String variation;
        int inputType;
        switch (inputTypeClass) {
            case "TYPE_NULL":
                inputType = InputType.TYPE_NULL;
                break;
            case "TYPE_CLASS_DATETIME":
                inputType = InputType.TYPE_CLASS_DATETIME;
                variation = readString(
                        createFieldKey(PREF_INPUT_TYPE_DATETIME_VARIATION_PREFIX, fieldId));
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
                variation = readString(
                        createFieldKey(PREF_INPUT_TYPE_NUMBER_VARIATION_PREFIX, fieldId));
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
                if (readBoolean(
                        createFieldKey(PREF_INPUT_TYPE_NUMBER_FLAG_SIGNED_PREFIX, fieldId))) {
                    inputType |= InputType.TYPE_NUMBER_FLAG_SIGNED;
                }
                if (readBoolean(
                        createFieldKey(PREF_INPUT_TYPE_NUMBER_FLAG_DECIMAL_PREFIX, fieldId))) {
                    inputType |= InputType.TYPE_NUMBER_FLAG_DECIMAL;
                }
                break;
            case "TYPE_CLASS_PHONE":
                inputType = InputType.TYPE_CLASS_PHONE;
                break;
            case "TYPE_CLASS_TEXT":
                inputType = InputType.TYPE_CLASS_TEXT;
                variation = readString(
                        createFieldKey(PREF_INPUT_TYPE_TEXT_VARIATION_PREFIX, fieldId));
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
                String multiLineFlag = readString(
                        createFieldKey(PREF_INPUT_TYPE_TEXT_FLAG_MULTI_LINE_PREFIX, fieldId));
                switch (multiLineFlag) {
                    case "TYPE_TEXT_FLAG_MULTI_LINE":
                        inputType |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;
                        break;
                    case "TYPE_TEXT_FLAG_IME_MULTI_LINE":
                        inputType |= InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE;
                        break;
                }
                String capFlag = readString(
                        createFieldKey(PREF_INPUT_TYPE_TEXT_FLAG_CAP_PREFIX, fieldId));
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
                if (readBoolean(
                        createFieldKey(PREF_INPUT_TYPE_TEXT_FLAG_AUTO_COMPLETE_PREFIX, fieldId))) {
                    inputType |= InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE;
                }
                if (readBoolean(
                        createFieldKey(PREF_INPUT_TYPE_TEXT_FLAG_AUTO_CORRECT_PREFIX, fieldId))) {
                    inputType |= InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
                }
                if (readBoolean(
                        createFieldKey(PREF_INPUT_TYPE_TEXT_FLAG_NO_SUGGESTIONS_PREFIX, fieldId))) {
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
        String imeOptionsAction = readString(
                createFieldKey(PREF_IME_OPTIONS_ACTION_PREFIX, fieldId));
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
        if (readBoolean(createFieldKey(PREF_IME_OPTIONS_FLAG_FORCE_ASCII_PREFIX, fieldId))) {
            imeOptions |=  EditorInfo.IME_FLAG_FORCE_ASCII;
        }
        if (readBoolean(createFieldKey(PREF_IME_OPTIONS_FLAG_NAVIGATE_NEXT_PREFIX, fieldId))) {
            imeOptions |=  EditorInfo.IME_FLAG_NAVIGATE_NEXT;
        }
        if (readBoolean(createFieldKey(PREF_IME_OPTIONS_FLAG_NAVIGATE_PREVIOUS_PREFIX, fieldId))) {
            imeOptions |=  EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS;
        }
        if (readBoolean(
                createFieldKey(PREF_IME_OPTIONS_FLAG_NO_ACCESSORY_ACTION_PREFIX, fieldId))) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_ACCESSORY_ACTION;
        }
        if (readBoolean(createFieldKey(PREF_IME_OPTIONS_FLAG_NO_ENTER_ACTION_PREFIX, fieldId))) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        }
        if (readBoolean(createFieldKey(PREF_IME_OPTIONS_FLAG_NO_EXTRACT_UI_PREFIX, fieldId))) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        }
        if (readBoolean(createFieldKey(PREF_IME_OPTIONS_FLAG_NO_FULLSCREEN_PREFIX, fieldId))) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_FULLSCREEN;
        }
        if (readBoolean(
                createFieldKey(PREF_IME_OPTIONS_FLAG_NO_PERSONALIZED_LEARNING_PREFIX, fieldId))
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        }
        return imeOptions;
    }
    //#endregion
    //#endregion
}
