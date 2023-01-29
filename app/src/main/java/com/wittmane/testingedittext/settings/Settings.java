/*
 * Copyright (C) 2022-2023 Eli Wittman
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
import java.util.List;
import java.util.Locale;

public class Settings implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = Settings.class.getSimpleName();

    public static final String PREF_MODIFY_COMMITTED_TEXT = "pref_key_modify_committed_text";
    public static final String PREF_MODIFY_COMPOSED_TEXT = "pref_key_modify_composed_text";
    public static final String PREF_MODIFY_COMPOSED_CHANGES_ONLY =
            "pref_key_modify_composed_changes_only";
    public static final String PREF_CONSIDER_COMPOSED_CHANGES_FROM_END =
            "pref_key_consider_composed_changes_from_end";
    public static final String PREF_RESTRICT_TO_INCLUDE = "pref_key_restrict_to_include";
    public static final String PREF_RESTRICT_SPECIFIC = "pref_key_restrict_specific";
    public static final String PREF_RESTRICT_RANGE = "pref_key_restrict_range";
    public static final String PREF_TRANSLATE_SPECIFIC = "pref_key_translate_specific";
    public static final String PREF_SHIFT_CODEPOINT = "pref_key_shift_codepoint";
    public static final String PREF_SKIP_EXTRACTING_TEXT = "pref_key_skip_extracting_text";
    public static final String PREF_IGNORE_EXTRACTED_TEXT_MONITOR =
            "pref_key_ignore_extracted_text_monitor";
    public static final String PREF_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT =
            "pref_key_update_selection_before_extracted_text";
    public static final String PREF_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES =
            "pref_key_update_extracted_text_only_on_net_changes";
    public static final String PREF_EXTRACT_FULL_TEXT = "pref_key_extract_full_text";
    public static final String PREF_LIMIT_EXTRACT_MONITOR_TEXT =
            "pref_key_limit_extract_monitor_text";
    public static final String PREF_LIMIT_RETURNED_TEXT = "pref_key_limit_returned_text";
    public static final String PREF_DELETE_THROUGH_COMPOSING_TEXT =
            "pref_key_delete_through_composing_text";
    public static final String PREF_KEEP_EMPTY_COMPOSING_POSITION =
            "pref_key_keep_empty_composing_position";
    public static final String PREF_SKIP_TAKESNAPSHOT = "pref_key_skip_takesnapshot";
    public static final String PREF_SKIP_GETSURROUNDINGTEXT = "pref_key_skip_getsurroundingtext";
    public static final String PREF_SKIP_PERFORMSPELLCHECK = "pref_key_skip_performspellcheck";
    public static final String PREF_SKIP_SETIMECONSUMESINPUT = "pref_key_skip_setimeconsumesinput";
    public static final String PREF_SKIP_COMMITCONTENT = "pref_key_skip_commitcontent";
    public static final String PREF_SKIP_CLOSECONNECTION = "pref_key_skip_closeconnection";
    public static final String PREF_SKIP_DELETESURROUNDINGTEXTINCODEPOINTS =
            "pref_key_skip_deletesurroundingtextincodepoints";
    public static final String PREF_SKIP_REQUESTCURSORUPDATES =
            "pref_key_skip_requestcursorupdates";
    public static final String PREF_SKIP_COMMITCORRECTION = "pref_key_skip_commitcorrection";
    public static final String PREF_SKIP_GETSELECTEDTEXT = "pref_key_skip_getselectedtext";
    public static final String PREF_SKIP_SETCOMPOSINGREGION = "pref_key_skip_setcomposingregion";
    public static final String PREF_UPDATE_DELAY = "pref_key_update_delay";
    public static final String PREF_FINISHCOMPOSINGTEXT_DELAY =
            "pref_key_finishcomposingtext_delay";
    public static final String PREF_GETSURROUNDINGTEXT_DELAY = "pref_key_getsurroundingtext_delay";
    public static final String PREF_GETTEXTBEFORECURSOR_DELAY =
            "pref_key_gettextbeforecursor_delay";
    public static final String PREF_GETSELECTEDTEXT_DELAY = "pref_key_getselectedtext_delay";
    public static final String PREF_GETTEXTAFTERCURSOR_DELAY = "pref_key_gettextaftercursor_delay";
    public static final String PREF_GETCURSORCAPSMODE_DELAY = "pref_key_getcursorcapsmode_delay";
    public static final String PREF_GETEXTRACTEDTEXT_DELAY = "pref_key_getextractedtext_delay";
    public static final String PREF_TEST_FIELD_IDS = "pref_key_test_field_ids";
    public static final String PREF_TEST_FIELD_INPUT_TYPE_CLASS_PREFIX =
            "pref_key_test_field_input_type_class_";
    public static final String PREF_TEST_FIELD_INPUT_TYPE_TEXT_VARIATION_PREFIX =
            "pref_key_test_field_input_type_text_variation_";
    public static final String PREF_TEST_FIELD_INPUT_TYPE_NUMBER_VARIATION_PREFIX =
            "pref_key_test_field_input_type_number_variation_";
    public static final String PREF_TEST_FIELD_INPUT_TYPE_DATETIME_VARIATION_PREFIX =
            "pref_key_test_field_input_type_datetime_variation_";
    public static final String PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_MULTI_LINE_PREFIX =
            "pref_key_test_field_input_type_text_flag_multi_line_";
    public static final String PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_CAP_PREFIX =
            "pref_key_test_field_input_type_text_flag_cap_";
    public static final String PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_AUTO_COMPLETE_PREFIX =
            "pref_key_test_field_input_type_text_flag_auto_complete_";
    public static final String PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_AUTO_CORRECT_PREFIX =
            "pref_key_test_field_input_type_text_flag_auto_correct_";
    public static final String PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_NO_SUGGESTIONS_PREFIX =
            "pref_key_test_field_input_type_text_flag_no_suggestions_";
    public static final String PREF_TEST_FIELD_INPUT_TYPE_NUMBER_FLAG_SIGNED_PREFIX =
            "pref_key_test_field_input_type_number_flag_signed_";
    public static final String PREF_TEST_FIELD_INPUT_TYPE_NUMBER_FLAG_DECIMAL_PREFIX =
            "pref_key_test_field_input_type_number_flag_decimal_";
    public static final String PREF_TEST_FIELD_IME_OPTIONS_ACTION_PREFIX =
            "pref_key_test_field_ime_options_action_";
    public static final String PREF_TEST_FIELD_IME_OPTIONS_FLAG_FORCE_ASCII_PREFIX =
            "pref_key_test_field_ime_options_flag_force_ascii_";
    public static final String PREF_TEST_FIELD_IME_OPTIONS_FLAG_NAVIGATE_NEXT_PREFIX =
            "pref_key_test_field_ime_options_flag_navigate_next_";
    public static final String PREF_TEST_FIELD_IME_OPTIONS_FLAG_NAVIGATE_PREVIOUS_PREFIX =
            "pref_key_test_field_ime_options_flag_navigate_previous_";
    public static final String PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_ACCESSORY_ACTION_PREFIX =
            "pref_key_test_field_ime_options_flag_no_accessory_action_";
    public static final String PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_ENTER_ACTION_PREFIX =
            "pref_key_test_field_ime_options_flag_no_enter_action_";
    public static final String PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_EXTRACT_UI_PREFIX =
            "pref_key_test_field_ime_options_flag_no_extract_ui_";
    public static final String PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_FULLSCREEN_PREFIX =
            "pref_key_test_field_ime_options_flag_no_fullscreen_";
    public static final String PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_PERSONALIZED_LEARNING_PREFIX =
            "pref_key_test_field_ime_options_flag_no_personalized_learning_";
    public static final String PREF_TEST_FIELD_IME_ACTION_ID_PREFIX =
            "pref_key_test_field_ime_action_id_";
    public static final String PREF_TEST_FIELD_IME_ACTION_LABEL_PREFIX =
            "pref_key_test_field_ime_action_label_";
    public static final String PREF_TEST_FIELD_PRIVATE_IME_OPTIONS_PREFIX =
            "pref_key_test_field_private_ime_options_";
    public static final String PREF_TEST_FIELD_SELECT_ALL_ON_FOCUS_PREFIX =
            "pref_key_test_field_select_all_on_focus_";
    public static final String PREF_TEST_FIELD_MAX_LENGTH_PREFIX =
            "pref_key_test_field_max_length_";
    public static final String PREF_TEST_FIELD_ALLOW_UNDO_PREFIX =
            "pref_key_test_field_allow_undo_";
    public static final String PREF_TEST_FIELD_TEXT_LOCALES_PREFIX =
            "pref_key_test_field_text_locales_";
    public static final String PREF_TEST_FIELD_IME_HINT_LOCALES_PREFIX =
            "pref_key_test_field_ime_hint_locales_";
    public static final String PREF_TEST_FIELD_IME_DEFAULT_TEXT_PREFIX =
            "pref_key_test_field_default_text_";
    public static final String PREF_TEST_FIELD_IME_HINT_TEXT_PREFIX =
            "pref_key_test_field_hint_text_";

    private boolean mModifyCommittedText;
    private boolean mModifyComposedText;
    private boolean mConsiderComposedChangesFromEnd;
    private boolean mModifyComposedChangesOnly;
    private boolean mRestrictToInclude;
    private String[] mRestrictSpecific;
    private IntRange mRestrictRange;
    private TranslateText[] mTranslateSpecific;
    private int mShiftCodepoint;
    private boolean mSkipExtractingText;
    private boolean mIgnoreExtractedTextMonitor;
    private boolean mUpdateSelectionBeforeExtractedText;
    private boolean mUpdateExtractedTextOnlyOnNetChanges;
    private boolean mExtractFullText;
    private int mExtractMonitorTextLimit;
    private int mReturnedTextLimit;
    private boolean mDeleteThroughComposingText;
    private boolean mKeepEmptyComposingPosition;
    private boolean mSkipTakeSnapshot;
    private boolean mSkipGetSurroundingText;
    private boolean mSkipPerformSpellCheck;
    private boolean mSkipSetImeConsumesInput;
    private boolean mSkipCommitContent;
    private boolean mSkipCloseConnection;
    private boolean mSkipDeleteSurroundingTextInCodePoints;
    private boolean mSkipRequestCursorUpdates;
    private boolean mSkipCommitCorrection;
    private boolean mSkipGetSelectedText;
    private boolean mSkipSetComposingRegion;
    private int mUpdateDelay;
    private int mFinishComposingTextDelay;
    private int mGetSurroundingTextDelay;
    private int mGetTextBeforeCursorDelay;
    private int mGetSelectedTextDelay;
    private int mGetTextAfterCursorDelay;
    private int mGetCursorCapsModeDelay;
    private int mGetExtractedTextDelay;
    private final List<TestField> mTestFields = new ArrayList<>();

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
                PREF_MODIFY_COMMITTED_TEXT,
                PREF_MODIFY_COMPOSED_TEXT,
                PREF_MODIFY_COMPOSED_CHANGES_ONLY,
                PREF_CONSIDER_COMPOSED_CHANGES_FROM_END,
                PREF_RESTRICT_TO_INCLUDE,
                PREF_RESTRICT_SPECIFIC,
                PREF_RESTRICT_RANGE,
                PREF_TRANSLATE_SPECIFIC,
                PREF_SHIFT_CODEPOINT,
                PREF_SKIP_EXTRACTING_TEXT,
                PREF_IGNORE_EXTRACTED_TEXT_MONITOR,
                PREF_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT,
                PREF_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES,
                PREF_EXTRACT_FULL_TEXT,
                PREF_LIMIT_EXTRACT_MONITOR_TEXT,
                PREF_LIMIT_RETURNED_TEXT,
                PREF_DELETE_THROUGH_COMPOSING_TEXT,
                PREF_KEEP_EMPTY_COMPOSING_POSITION,
                PREF_SKIP_TAKESNAPSHOT,
                PREF_SKIP_GETSURROUNDINGTEXT,
                PREF_SKIP_PERFORMSPELLCHECK,
                PREF_SKIP_SETIMECONSUMESINPUT,
                PREF_SKIP_COMMITCONTENT,
                PREF_SKIP_CLOSECONNECTION,
                PREF_SKIP_DELETESURROUNDINGTEXTINCODEPOINTS,
                PREF_SKIP_REQUESTCURSORUPDATES,
                PREF_SKIP_COMMITCORRECTION,
                PREF_SKIP_GETSELECTEDTEXT,
                PREF_SKIP_SETCOMPOSINGREGION,
                PREF_UPDATE_DELAY,
                PREF_FINISHCOMPOSINGTEXT_DELAY,
                PREF_GETSURROUNDINGTEXT_DELAY,
                PREF_GETTEXTBEFORECURSOR_DELAY,
                PREF_GETSELECTEDTEXT_DELAY,
                PREF_GETTEXTAFTERCURSOR_DELAY,
                PREF_GETCURSORCAPSMODE_DELAY,
                PREF_GETEXTRACTEDTEXT_DELAY
        };
        for (String prefKey : prefKeys) {
            loadSetting(prefKey);
        }
        int[] fieldIds = readTestFieldIds(mPrefs);
        mTestFields.clear();
        for (int id : fieldIds) {
            mTestFields.add(new TestField(id));
            loadTestFieldSettings(id);
        }
    }

    private void loadTestFieldSettings(int fieldId) {
        // intentionally skipping some preferences since they are read in groups, so listing them
        // all would just read all of them multiple times. leaving them commented out here for
        // visibility.
        final String[] testFieldPrefKeyPrefixes = new String[]{
                PREF_TEST_FIELD_INPUT_TYPE_CLASS_PREFIX,
                //PREF_TEST_FIELD_INPUT_TYPE_TEXT_VARIATION_PREFIX,
                //PREF_TEST_FIELD_INPUT_TYPE_NUMBER_VARIATION_PREFIX,
                //PREF_TEST_FIELD_INPUT_TYPE_DATETIME_VARIATION_PREFIX,
                //PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_MULTI_LINE_PREFIX,
                //PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_CAP_PREFIX,
                //PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_AUTO_COMPLETE_PREFIX,
                //PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_AUTO_CORRECT_PREFIX,
                //PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_NO_SUGGESTIONS_PREFIX,
                //PREF_TEST_FIELD_INPUT_TYPE_NUMBER_FLAG_SIGNED_PREFIX,
                //PREF_TEST_FIELD_INPUT_TYPE_NUMBER_FLAG_DECIMAL_PREFIX,
                PREF_TEST_FIELD_IME_OPTIONS_ACTION_PREFIX,
                //PREF_TEST_FIELD_IME_OPTIONS_FLAG_FORCE_ASCII_PREFIX,
                //PREF_TEST_FIELD_IME_OPTIONS_FLAG_NAVIGATE_NEXT_PREFIX,
                //PREF_TEST_FIELD_IME_OPTIONS_FLAG_NAVIGATE_PREVIOUS_PREFIX,
                //PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_ACCESSORY_ACTION_PREFIX,
                //PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_ENTER_ACTION_PREFIX,
                //PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_EXTRACT_UI_PREFIX,
                //PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_FULLSCREEN_PREFIX,
                //PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_PERSONALIZED_LEARNING_PREFIX,
                PREF_TEST_FIELD_IME_ACTION_ID_PREFIX,
                PREF_TEST_FIELD_IME_ACTION_LABEL_PREFIX,
                PREF_TEST_FIELD_PRIVATE_IME_OPTIONS_PREFIX,
                PREF_TEST_FIELD_SELECT_ALL_ON_FOCUS_PREFIX,
                PREF_TEST_FIELD_MAX_LENGTH_PREFIX,
                PREF_TEST_FIELD_ALLOW_UNDO_PREFIX,
                PREF_TEST_FIELD_TEXT_LOCALES_PREFIX,
                PREF_TEST_FIELD_IME_HINT_LOCALES_PREFIX,
                PREF_TEST_FIELD_IME_DEFAULT_TEXT_PREFIX,
                PREF_TEST_FIELD_IME_HINT_TEXT_PREFIX
        };
        for (String prefKeyPrefix : testFieldPrefKeyPrefixes) {
            loadTestFieldSetting(prefKeyPrefix, fieldId);
        }
    }

    private void loadSetting(String prefKey) {
        switch (prefKey) {
            case PREF_MODIFY_COMMITTED_TEXT:
                mModifyCommittedText = readModifyCommittedText(mPrefs);
                break;
            case PREF_MODIFY_COMPOSED_TEXT:
                mModifyComposedText = readModifyComposedText(mPrefs);
                break;
            case PREF_MODIFY_COMPOSED_CHANGES_ONLY:
                mModifyComposedChangesOnly = readModifyComposedChangesOnly(mPrefs);
                break;
            case PREF_CONSIDER_COMPOSED_CHANGES_FROM_END:
                mConsiderComposedChangesFromEnd = readConsiderComposedChangesFromEnd(mPrefs);
                break;
            case PREF_RESTRICT_TO_INCLUDE:
                mRestrictToInclude = readRestrictToInclude(mPrefs);
                break;
            case PREF_RESTRICT_SPECIFIC:
                mRestrictSpecific = readRestrictSpecific(mPrefs);
                break;
            case PREF_RESTRICT_RANGE:
                mRestrictRange = readRestrictRange(mPrefs);
                break;
            case PREF_TRANSLATE_SPECIFIC:
                mTranslateSpecific = readTranslateSpecific(mPrefs);
                break;
            case PREF_SHIFT_CODEPOINT:
                mShiftCodepoint = readShiftCodepoint(mPrefs);
                break;

            case PREF_SKIP_EXTRACTING_TEXT:
                mSkipExtractingText = readSkipExtractingText(mPrefs);
                break;
            case PREF_IGNORE_EXTRACTED_TEXT_MONITOR:
                mIgnoreExtractedTextMonitor = readIgnoreExtractedTextMonitor(mPrefs);
                break;
            case PREF_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT:
                mUpdateSelectionBeforeExtractedText =
                        readUpdateSelectionBeforeExtractedText(mPrefs);
                break;
            case PREF_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES:
                mUpdateExtractedTextOnlyOnNetChanges =
                        readUpdateExtractedTextOnlyOnNetChanges(mPrefs);
                break;
            case PREF_EXTRACT_FULL_TEXT:
                mExtractFullText = readExtractFullText(mPrefs);
                break;
            case PREF_LIMIT_EXTRACT_MONITOR_TEXT:
                mExtractMonitorTextLimit = readExtractMonitorTextLimit(mPrefs);
                break;
            case PREF_LIMIT_RETURNED_TEXT:
                mReturnedTextLimit = readReturnedTextLimit(mPrefs);
                break;

            case PREF_DELETE_THROUGH_COMPOSING_TEXT:
                mDeleteThroughComposingText = readDeleteThroughComposingText(mPrefs);
                break;
            case PREF_KEEP_EMPTY_COMPOSING_POSITION:
                mKeepEmptyComposingPosition = readKeepEmptyComposingPosition(mPrefs);
                break;

            case PREF_SKIP_TAKESNAPSHOT:
                mSkipTakeSnapshot = readSkipTakeSnapshot(mPrefs);
                break;
            case PREF_SKIP_GETSURROUNDINGTEXT:
                mSkipGetSurroundingText = readSkipGetSurroundingText(mPrefs);
                break;
            case PREF_SKIP_PERFORMSPELLCHECK:
                mSkipPerformSpellCheck = readSkipPerformSpellCheck(mPrefs);
                break;
            case PREF_SKIP_SETIMECONSUMESINPUT:
                mSkipSetImeConsumesInput = readSkipSetImeConsumesInput(mPrefs);
                break;
            case PREF_SKIP_COMMITCONTENT:
                mSkipCommitContent = readSkipCommitContent(mPrefs);
                break;
            case PREF_SKIP_CLOSECONNECTION:
                mSkipCloseConnection = readSkipCloseConnection(mPrefs);
                break;
            case PREF_SKIP_DELETESURROUNDINGTEXTINCODEPOINTS:
                mSkipDeleteSurroundingTextInCodePoints =
                        readSkipDeleteSurroundingTextInCodePoints(mPrefs);
                break;
            case PREF_SKIP_REQUESTCURSORUPDATES:
                mSkipRequestCursorUpdates = readSkipRequestCursorUpdates(mPrefs);
                break;
            case PREF_SKIP_COMMITCORRECTION:
                mSkipCommitCorrection = readSkipCommitCorrection(mPrefs);
                break;
            case PREF_SKIP_GETSELECTEDTEXT:
                mSkipGetSelectedText = readSkipGetSelectedText(mPrefs);
                break;
            case PREF_SKIP_SETCOMPOSINGREGION:
                mSkipSetComposingRegion = readSkipSetComposingRegion(mPrefs);
                break;

            case PREF_UPDATE_DELAY:
                mUpdateDelay = readUpdateDelay(mPrefs);
                break;
            case PREF_FINISHCOMPOSINGTEXT_DELAY:
                mFinishComposingTextDelay = readFinishComposingTextDelay(mPrefs);
                break;
            case PREF_GETSURROUNDINGTEXT_DELAY:
                mGetSurroundingTextDelay = readGetSurroundingTextDelay(mPrefs);
                break;
            case PREF_GETTEXTBEFORECURSOR_DELAY:
                mGetTextBeforeCursorDelay = readGetTextBeforeCursorDelay(mPrefs);
                break;
            case PREF_GETSELECTEDTEXT_DELAY:
                mGetSelectedTextDelay = readGetSelectedTextDelay(mPrefs);
                break;
            case PREF_GETTEXTAFTERCURSOR_DELAY:
                mGetTextAfterCursorDelay = readGetTextAfterCursorDelay(mPrefs);
                break;
            case PREF_GETCURSORCAPSMODE_DELAY:
                mGetCursorCapsModeDelay = readGetCursorCapsModeDelay(mPrefs);
                break;
            case PREF_GETEXTRACTEDTEXT_DELAY:
                mGetExtractedTextDelay = readGetExtractedTextDelay(mPrefs);
                break;

            default:
                // try loading as a specific field's setting
                loadTestFieldSetting(prefKey);
                break;
        }
    }

    private void loadTestFieldSetting(String prefKey) {
        if (prefKey == null) {
            return;
        }
        int prefixEnd = prefKey.lastIndexOf("_");
        if (prefixEnd < 0 || prefKey.length() - prefixEnd - 1 <= 0) {
            return;
        }
        int id;
        try {
            id = Integer.parseInt(prefKey.substring(prefixEnd + 1));
        } catch (NumberFormatException ignored) {
            return;
        }
        loadTestFieldSetting(prefKey.substring(0, prefixEnd + 1), id);
    }

    private void loadTestFieldSetting(String prefKeyPrefix, int fieldId) {
        int index = indexOf(mTestFields, fieldId);
        if (index < 0) {
            return;
        }
        TestField testField = mTestFields.get(index);
        switch (prefKeyPrefix) {
            case PREF_TEST_FIELD_INPUT_TYPE_CLASS_PREFIX:
            case PREF_TEST_FIELD_INPUT_TYPE_TEXT_VARIATION_PREFIX:
            case PREF_TEST_FIELD_INPUT_TYPE_NUMBER_VARIATION_PREFIX:
            case PREF_TEST_FIELD_INPUT_TYPE_DATETIME_VARIATION_PREFIX:
            case PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_MULTI_LINE_PREFIX:
            case PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_CAP_PREFIX:
            case PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_AUTO_COMPLETE_PREFIX:
            case PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_AUTO_CORRECT_PREFIX:
            case PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_NO_SUGGESTIONS_PREFIX:
            case PREF_TEST_FIELD_INPUT_TYPE_NUMBER_FLAG_SIGNED_PREFIX:
            case PREF_TEST_FIELD_INPUT_TYPE_NUMBER_FLAG_DECIMAL_PREFIX:
                testField.mInputType = readTestFieldInputType(mPrefs, fieldId);
                break;
            case PREF_TEST_FIELD_IME_OPTIONS_ACTION_PREFIX:
            case PREF_TEST_FIELD_IME_OPTIONS_FLAG_FORCE_ASCII_PREFIX:
            case PREF_TEST_FIELD_IME_OPTIONS_FLAG_NAVIGATE_NEXT_PREFIX:
            case PREF_TEST_FIELD_IME_OPTIONS_FLAG_NAVIGATE_PREVIOUS_PREFIX:
            case PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_ACCESSORY_ACTION_PREFIX:
            case PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_ENTER_ACTION_PREFIX:
            case PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_EXTRACT_UI_PREFIX:
            case PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_FULLSCREEN_PREFIX:
            case PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_PERSONALIZED_LEARNING_PREFIX:
                testField.mImeOptions = readTestFieldImeOptions(mPrefs, fieldId);
                break;
            case PREF_TEST_FIELD_IME_ACTION_ID_PREFIX:
                testField.mImeActionId = readTestFieldImeActionId(mPrefs, fieldId);
                break;
            case PREF_TEST_FIELD_IME_ACTION_LABEL_PREFIX:
                testField.mImeActionLabel = readTestFieldImeActionLabel(mPrefs, fieldId);
                break;
            case PREF_TEST_FIELD_PRIVATE_IME_OPTIONS_PREFIX:
                testField.mPrivateImeOptions = readTestFieldPrivateImeOptions(mPrefs, fieldId);
                break;
            case PREF_TEST_FIELD_SELECT_ALL_ON_FOCUS_PREFIX:
                testField.mSelectAllOnFocus = readTestFieldSelectAllOnFocus(mPrefs, fieldId);
                break;
            case PREF_TEST_FIELD_MAX_LENGTH_PREFIX:
                testField.mMaxLength = readTestFieldMaxLength(mPrefs, fieldId);
                break;
            case PREF_TEST_FIELD_ALLOW_UNDO_PREFIX:
                testField.mAllowUndo = readTestFieldAllowUndo(mPrefs, fieldId);
                break;
            case PREF_TEST_FIELD_TEXT_LOCALES_PREFIX:
                testField.mTextLocales = readTestFieldTextLocales(mPrefs, fieldId);
                break;
            case PREF_TEST_FIELD_IME_HINT_LOCALES_PREFIX:
                testField.mImeHintLocales = readTestFieldImeHintLocales(mPrefs, fieldId);
                break;
            case PREF_TEST_FIELD_IME_DEFAULT_TEXT_PREFIX:
                testField.mDefaultText = readTestFieldDefaultText(mPrefs, fieldId);
                break;
            case PREF_TEST_FIELD_IME_HINT_TEXT_PREFIX:
                testField.mHintText = readTestFieldHintText(mPrefs, fieldId);
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

    private static boolean readModifyCommittedText(final SharedPreferenceManager prefs) {
        return prefs.getBoolean(PREF_MODIFY_COMMITTED_TEXT, false);
    }

    public static boolean shouldModifyCommittedText() {
        return getInstance().mModifyCommittedText;
    }

    private static boolean readModifyComposedText(final SharedPreferenceManager prefs) {
        return prefs.getBoolean(PREF_MODIFY_COMPOSED_TEXT, false);
    }

    public static boolean shouldModifyComposedText() {
        return getInstance().mModifyComposedText;
    }

    private static boolean readModifyComposedChangesOnly(final SharedPreferenceManager prefs) {
        return prefs.getBoolean(PREF_MODIFY_COMPOSED_CHANGES_ONLY, false);
    }

    public static boolean shouldModifyComposedChangesOnly() {
        return getInstance().mModifyComposedChangesOnly;
    }

    private static boolean readConsiderComposedChangesFromEnd(final SharedPreferenceManager prefs) {
        return prefs.getBoolean(PREF_CONSIDER_COMPOSED_CHANGES_FROM_END, false);
    }

    public static boolean shouldConsiderComposedChangesFromEnd() {
        return getInstance().mConsiderComposedChangesFromEnd;
    }

    private static boolean readRestrictToInclude(final SharedPreferenceManager prefs) {
        return prefs.getBoolean(PREF_RESTRICT_TO_INCLUDE, false);
    }

    public static boolean shouldRestrictToInclude() {
        return getInstance().mRestrictToInclude;
    }

    private static String[] readRestrictSpecific(final SharedPreferenceManager prefs) {
        TextList<String> textList =
                (new TextListPreference.Reader(prefs, PREF_RESTRICT_SPECIFIC)).readValue();
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

    public static String[] getRestrictSpecific() {
        return getInstance().mRestrictSpecific;
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
                            char foo = text.charAt(i + unicodeIndex + 1);
                            if (!((foo >= '0' && foo <= '9') || (foo >= 'a' && foo <= 'f'))) {
                                break;
                            }
                            unicode[unicodeIndex] = foo;
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

    @Nullable
    private static IntRange readRestrictRange(final SharedPreferenceManager prefs) {
        return (new CodepointRangeDialogPreference.Reader(prefs, PREF_RESTRICT_RANGE)).readValue();
    }

    public static @Nullable IntRange getRestrictRange() {
        return getInstance().mRestrictRange;
    }

    private static TranslateText[] readTranslateSpecific(final SharedPreferenceManager prefs) {
        TextList<TranslateText> textList =
                (new TextTranslateListPreference.Reader(prefs, PREF_TRANSLATE_SPECIFIC))
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

    public static TranslateText[] getTranslateSpecific() {
        return getInstance().mTranslateSpecific;
    }

    private static int readShiftCodepoint(final SharedPreferenceManager prefs) {
        return prefs.getInt(PREF_SHIFT_CODEPOINT, 0);
    }

    public static int getShiftCodepoint() {
        return getInstance().mShiftCodepoint;
    }

    private static boolean readSkipExtractingText(final SharedPreferenceManager prefs) {
        return prefs.getBoolean(PREF_SKIP_EXTRACTING_TEXT, false);
    }

    public static boolean shouldSkipExtractingText() {
        return getInstance().mSkipExtractingText;
    }

    private static boolean readIgnoreExtractedTextMonitor(final SharedPreferenceManager prefs) {
        return prefs.getBoolean(PREF_IGNORE_EXTRACTED_TEXT_MONITOR, false);
    }

    public static boolean shouldIgnoreExtractedTextMonitor() {
        return getInstance().mIgnoreExtractedTextMonitor;
    }

    private static boolean readUpdateSelectionBeforeExtractedText(
            final SharedPreferenceManager prefs) {
        return prefs.getBoolean(PREF_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT, false);
    }

    public static boolean shouldUpdateSelectionBeforeExtractedText() {
        return getInstance().mUpdateSelectionBeforeExtractedText;
    }

    private static boolean readUpdateExtractedTextOnlyOnNetChanges(
            final SharedPreferenceManager prefs) {
        return prefs.getBoolean(PREF_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES, false);
    }

    public static boolean shouldUpdateExtractedTextOnlyOnNetChanges() {
        return getInstance().mUpdateExtractedTextOnlyOnNetChanges;
    }

    private static boolean readExtractFullText(final SharedPreferenceManager prefs) {
        return prefs.getBoolean(PREF_EXTRACT_FULL_TEXT, false);
    }

    public static boolean shouldExtractFullText() {
        return getInstance().mExtractFullText;
    }

    private static int readExtractMonitorTextLimit(final SharedPreferenceManager prefs) {
        return prefs.getInt(PREF_LIMIT_EXTRACT_MONITOR_TEXT, -1);
    }

    public static int getExtractMonitorTextLimit() {
        return getInstance().mExtractMonitorTextLimit;
    }

    private static int readReturnedTextLimit(final SharedPreferenceManager prefs) {
        return prefs.getInt(PREF_LIMIT_RETURNED_TEXT, -1);
    }

    public static int getReturnedTextLimit() {
        return getInstance().mReturnedTextLimit;
    }

    private static boolean readDeleteThroughComposingText(final SharedPreferenceManager prefs) {
        return prefs.getBoolean(PREF_DELETE_THROUGH_COMPOSING_TEXT, false);
    }

    public static boolean shouldDeleteThroughComposingText() {
        return getInstance().mDeleteThroughComposingText;
    }

    private static boolean readKeepEmptyComposingPosition(final SharedPreferenceManager prefs) {
        return prefs.getBoolean(PREF_KEEP_EMPTY_COMPOSING_POSITION, false);
    }

    public static boolean shouldKeepEmptyComposingPosition() {
        return getInstance().mKeepEmptyComposingPosition;
    }

    private static boolean readSkipTakeSnapshot(final SharedPreferenceManager prefs) {
        return prefs.getBoolean(PREF_SKIP_TAKESNAPSHOT, false);
    }

    public static boolean shouldSkipTakeSnapshot() {
        return getInstance().mSkipTakeSnapshot;
    }

    private static boolean readSkipGetSurroundingText(final SharedPreferenceManager prefs) {
        return prefs.getBoolean(PREF_SKIP_GETSURROUNDINGTEXT, false);
    }

    public static boolean shouldSkipGetSurroundingText() {
        return getInstance().mSkipGetSurroundingText;
    }

    private static boolean readSkipPerformSpellCheck(final SharedPreferenceManager prefs) {
        return prefs.getBoolean(PREF_SKIP_PERFORMSPELLCHECK, false);
    }

    public static boolean shouldSkipPerformSpellCheck() {
        return getInstance().mSkipPerformSpellCheck;
    }

    private static boolean readSkipSetImeConsumesInput(final SharedPreferenceManager prefs) {
        return prefs.getBoolean(PREF_SKIP_SETIMECONSUMESINPUT, false);
    }

    public static boolean shouldSkipSetImeConsumesInput() {
        return getInstance().mSkipSetImeConsumesInput;
    }

    private static boolean readSkipCommitContent(final SharedPreferenceManager prefs) {
        return prefs.getBoolean(PREF_SKIP_COMMITCONTENT, false);
    }

    public static boolean shouldSkipCommitContent() {
        return getInstance().mSkipCommitContent;
    }

    private static boolean readSkipCloseConnection(final SharedPreferenceManager prefs) {
        return prefs.getBoolean(PREF_SKIP_CLOSECONNECTION, false);
    }

    public static boolean shouldSkipCloseConnection() {
        return getInstance().mSkipCloseConnection;
    }

    private static boolean readSkipDeleteSurroundingTextInCodePoints(
            final SharedPreferenceManager prefs) {
        return prefs.getBoolean(PREF_SKIP_DELETESURROUNDINGTEXTINCODEPOINTS, false);
    }

    public static boolean shouldSkipDeleteSurroundingTextInCodePoints() {
        return getInstance().mSkipDeleteSurroundingTextInCodePoints;
    }

    private static boolean readSkipRequestCursorUpdates(final SharedPreferenceManager prefs) {
        return prefs.getBoolean(PREF_SKIP_REQUESTCURSORUPDATES, false);
    }

    public static boolean shouldSkipRequestCursorUpdates() {
        return getInstance().mSkipRequestCursorUpdates;
    }

    private static boolean readSkipCommitCorrection(final SharedPreferenceManager prefs) {
        return prefs.getBoolean(PREF_SKIP_COMMITCORRECTION, false);
    }

    public static boolean shouldSkipCommitCorrection() {
        return getInstance().mSkipCommitCorrection;
    }

    private static boolean readSkipGetSelectedText(final SharedPreferenceManager prefs) {
        return prefs.getBoolean(PREF_SKIP_GETSELECTEDTEXT, false);
    }

    public static boolean shouldSkipGetSelectedText() {
        return getInstance().mSkipGetSelectedText;
    }

    private static boolean readSkipSetComposingRegion(final SharedPreferenceManager prefs) {
        return prefs.getBoolean(PREF_SKIP_SETCOMPOSINGREGION, false);
    }

    public static boolean shouldSkipSetComposingRegion() {
        return getInstance().mSkipSetComposingRegion;
    }

    private static int readUpdateDelay(final SharedPreferenceManager prefs) {
        return prefs.getInt(PREF_UPDATE_DELAY, 0);
    }

    public static int getUpdateDelay() {
        return getInstance().mUpdateDelay;
    }

    private static int readFinishComposingTextDelay(final SharedPreferenceManager prefs) {
        return prefs.getInt(PREF_FINISHCOMPOSINGTEXT_DELAY, 0);
    }

    public static int getFinishComposingTextDelay() {
        return getInstance().mFinishComposingTextDelay;
    }

    private static int readGetSurroundingTextDelay(final SharedPreferenceManager prefs) {
        return prefs.getInt(PREF_GETSURROUNDINGTEXT_DELAY, 0);
    }

    public static int getGetSurroundingTextDelay() {
        return getInstance().mGetSurroundingTextDelay;
    }

    private static int readGetTextBeforeCursorDelay(final SharedPreferenceManager prefs) {
        return prefs.getInt(PREF_GETTEXTBEFORECURSOR_DELAY, 0);
    }

    public static int getGetTextBeforeCursorDelay() {
        return getInstance().mGetTextBeforeCursorDelay;
    }

    private static int readGetSelectedTextDelay(final SharedPreferenceManager prefs) {
        return prefs.getInt(PREF_GETSELECTEDTEXT_DELAY, 0);
    }

    public static int getGetSelectedTextDelay() {
        return getInstance().mGetSelectedTextDelay;
    }

    private static int readGetTextAfterCursorDelay(final SharedPreferenceManager prefs) {
        return prefs.getInt(PREF_GETTEXTAFTERCURSOR_DELAY, 0);
    }

    public static int getGetTextAfterCursorDelay() {
        return getInstance().mGetTextAfterCursorDelay;
    }

    private static int readGetCursorCapsModeDelay(final SharedPreferenceManager prefs) {
        return prefs.getInt(PREF_GETCURSORCAPSMODE_DELAY, 0);
    }

    public static int getGetCursorCapsModeDelay() {
        return getInstance().mGetCursorCapsModeDelay;
    }

    private static int readGetExtractedTextDelay(final SharedPreferenceManager prefs) {
        return prefs.getInt(PREF_GETEXTRACTEDTEXT_DELAY, 0);
    }

    public static int getGetExtractedTextDelay() {
        return getInstance().mGetExtractedTextDelay;
    }

    private static int[] readTestFieldIds(final SharedPreferenceManager prefs) {
        int[] fieldIds = prefs.getIntArray(PREF_TEST_FIELD_IDS, new int[] { 0 });
        if (fieldIds == null || fieldIds.length < 1) {
            // there should always be at least 1 field
            Log.e(TAG, "No test fields");
            return new int[] { 0 };
        }
        return fieldIds;
    }

    public static void setTestFieldIds(int[] testFieldIds) {
        Settings settings = getInstance();

        Editor editor = settings.mPrefs.edit();

        TestField[] newTestFields = new TestField[testFieldIds.length];
        // delete any fields that are getting removed
        for (TestField testField : settings.mTestFields) {
            boolean isRemovingField = true;
            for (int i = 0; i < testFieldIds.length; i++) {
                int testFieldId = testFieldIds[i];
                if (testField.mId == testFieldId) {
                    isRemovingField = false;
                    newTestFields[i] = testField;
                    break;
                }
            }
            if (isRemovingField) {
                // clear all of the now orphaned test field preferences to avoid bloat
                removeTestFieldPrefs(editor, testField.mId);
            }
        }
        settings.mTestFields.clear();
        // add the new fields (reusing any that already existed and may have just changes positions)
        List<Integer> newFieldIds = new ArrayList<>();
        for (int i = 0; i < newTestFields.length; i++) {
            TestField testField = newTestFields[i];
            if (testField == null) {
                testField = new TestField(testFieldIds[i]);
                newFieldIds.add(testFieldIds[i]);
            }
            settings.mTestFields.add(testField);
        }

        editor.putIntArray(PREF_TEST_FIELD_IDS, testFieldIds);

        editor.apply();

        // load the default values for any new fields
        for (int newFieldId : newFieldIds) {
            settings.loadTestFieldSettings(newFieldId);
        }
    }

    public static int getTestFieldCount() {
        return getInstance().mTestFields.size();
    }

    public static int getTestFieldId(int index) {
        return getInstance().mTestFields.get(index).mId;
    }

    public static void addTestField() {
        Settings settings = getInstance();
        int newFieldId = getNextId(settings.mTestFields);
        settings.mTestFields.add(new TestField(newFieldId));
        settings.mPrefs.setIntArray(PREF_TEST_FIELD_IDS, getTestFieldIds(settings.mTestFields));
        // load the default values for the new field
        settings.loadTestFieldSettings(newFieldId);
    }

    public static void removeTestField(int index) {
        Settings settings = getInstance();

        int idToRemove = settings.mTestFields.get(index).mId;
        settings.mTestFields.remove(index);
        if (settings.mTestFields.size() < 1) {
            // there should always be at least 1 field
            settings.mTestFields.add(new TestField(0));
        }

        Editor editor = settings.mPrefs.edit();

        editor.putIntArray(PREF_TEST_FIELD_IDS, getTestFieldIds(settings.mTestFields));

        // clear all of the now orphaned test field preferences to avoid bloat
        removeTestFieldPrefs(editor, idToRemove);

        editor.apply();
    }

    private static void removeTestFieldPrefs(Editor editor, int idToRemove) {
        final String[] testFieldPrefKeyPrefixes = new String[]{
                PREF_TEST_FIELD_INPUT_TYPE_CLASS_PREFIX,
                PREF_TEST_FIELD_INPUT_TYPE_TEXT_VARIATION_PREFIX,
                PREF_TEST_FIELD_INPUT_TYPE_NUMBER_VARIATION_PREFIX,
                PREF_TEST_FIELD_INPUT_TYPE_DATETIME_VARIATION_PREFIX,
                PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_MULTI_LINE_PREFIX,
                PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_CAP_PREFIX,
                PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_AUTO_COMPLETE_PREFIX,
                PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_AUTO_CORRECT_PREFIX,
                PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_NO_SUGGESTIONS_PREFIX,
                PREF_TEST_FIELD_INPUT_TYPE_NUMBER_FLAG_SIGNED_PREFIX,
                PREF_TEST_FIELD_INPUT_TYPE_NUMBER_FLAG_DECIMAL_PREFIX,
                PREF_TEST_FIELD_IME_OPTIONS_ACTION_PREFIX,
                PREF_TEST_FIELD_IME_OPTIONS_FLAG_FORCE_ASCII_PREFIX,
                PREF_TEST_FIELD_IME_OPTIONS_FLAG_NAVIGATE_NEXT_PREFIX,
                PREF_TEST_FIELD_IME_OPTIONS_FLAG_NAVIGATE_PREVIOUS_PREFIX,
                PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_ACCESSORY_ACTION_PREFIX,
                PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_ENTER_ACTION_PREFIX,
                PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_EXTRACT_UI_PREFIX,
                PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_FULLSCREEN_PREFIX,
                PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_PERSONALIZED_LEARNING_PREFIX,
                PREF_TEST_FIELD_IME_ACTION_ID_PREFIX,
                PREF_TEST_FIELD_IME_ACTION_LABEL_PREFIX,
                PREF_TEST_FIELD_PRIVATE_IME_OPTIONS_PREFIX,
                PREF_TEST_FIELD_SELECT_ALL_ON_FOCUS_PREFIX,
                PREF_TEST_FIELD_MAX_LENGTH_PREFIX,
                PREF_TEST_FIELD_ALLOW_UNDO_PREFIX,
                PREF_TEST_FIELD_TEXT_LOCALES_PREFIX,
                PREF_TEST_FIELD_IME_HINT_LOCALES_PREFIX,
                PREF_TEST_FIELD_IME_DEFAULT_TEXT_PREFIX,
                PREF_TEST_FIELD_IME_HINT_TEXT_PREFIX
        };
        for (String prefKeyPrefix : testFieldPrefKeyPrefixes) {
            editor.remove(prefKeyPrefix + idToRemove);
        }
    }

    private static int[] getTestFieldIds(List<TestField> fields) {
        int[] fieldIds = new int[fields.size()];
        for (int i = 0; i < fieldIds.length; i++) {
            fieldIds[i] = fields.get(i).mId;
        }
        return fieldIds;
    }

    private static int getNextId(List<TestField> fields) {
        int max = -1;
        for (TestField field : fields) {
            if (field.mId > max) {
                max = field.mId;
            }
        }
        return max + 1;
    }

    private static int readTestFieldInputType(final SharedPreferenceManager prefs, int fieldId) {
        String inputTypeClass = prefs.getString(PREF_TEST_FIELD_INPUT_TYPE_CLASS_PREFIX + fieldId,
                "TYPE_CLASS_TEXT");
        String variation;
        int inputType;
        switch (inputTypeClass) {
            case "TYPE_CLASS_DATETIME":
                inputType = InputType.TYPE_CLASS_DATETIME;
                variation = prefs.getString(
                        PREF_TEST_FIELD_INPUT_TYPE_DATETIME_VARIATION_PREFIX + fieldId,
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
                        PREF_TEST_FIELD_INPUT_TYPE_NUMBER_VARIATION_PREFIX + fieldId,
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
                        PREF_TEST_FIELD_INPUT_TYPE_NUMBER_FLAG_SIGNED_PREFIX + fieldId,
                        false)) {
                    inputType |= InputType.TYPE_NUMBER_FLAG_SIGNED;
                }
                if (prefs.getBoolean(
                        PREF_TEST_FIELD_INPUT_TYPE_NUMBER_FLAG_DECIMAL_PREFIX + fieldId,
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
                        PREF_TEST_FIELD_INPUT_TYPE_TEXT_VARIATION_PREFIX + fieldId,
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
                        PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_MULTI_LINE_PREFIX + fieldId,
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
                        PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_CAP_PREFIX + fieldId,
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
                        PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_AUTO_COMPLETE_PREFIX + fieldId,
                        false)) {
                    inputType |= InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE;
                }
                if (prefs.getBoolean(
                        PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_AUTO_CORRECT_PREFIX + fieldId,
                        false)) {
                    inputType |= InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
                }
                if (prefs.getBoolean(
                        PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_NO_SUGGESTIONS_PREFIX + fieldId,
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
        return getInstance().mTestFields.get(fieldIndex).mInputType;
    }

    private static int readTestFieldImeOptions(final SharedPreferenceManager prefs, int fieldId) {
        String imeOptionsAction = prefs.getString(
                PREF_TEST_FIELD_IME_OPTIONS_ACTION_PREFIX + fieldId,
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
        if (prefs.getBoolean(PREF_TEST_FIELD_IME_OPTIONS_FLAG_FORCE_ASCII_PREFIX + fieldId,
                false)) {
            imeOptions |=  EditorInfo.IME_FLAG_FORCE_ASCII;
        }
        if (prefs.getBoolean(PREF_TEST_FIELD_IME_OPTIONS_FLAG_NAVIGATE_NEXT_PREFIX + fieldId,
                false)) {
            imeOptions |=  EditorInfo.IME_FLAG_NAVIGATE_NEXT;
        }
        if (prefs.getBoolean(PREF_TEST_FIELD_IME_OPTIONS_FLAG_NAVIGATE_PREVIOUS_PREFIX + fieldId,
                false)) {
            imeOptions |=  EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS;
        }
        if (prefs.getBoolean(
                PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_ACCESSORY_ACTION_PREFIX + fieldId,
                false)) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_ACCESSORY_ACTION;
        }
        if (prefs.getBoolean(PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_ENTER_ACTION_PREFIX + fieldId,
                false)) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        }
        if (prefs.getBoolean(PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_EXTRACT_UI_PREFIX + fieldId,
                false)) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        }
        if (prefs.getBoolean(PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_FULLSCREEN_PREFIX + fieldId,
                false)) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_FULLSCREEN;
        }
        if (prefs.getBoolean(
                PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_PERSONALIZED_LEARNING_PREFIX + fieldId,
                false)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        }
        return imeOptions;
    }

    public static int getTestFieldImeOptions(int fieldIndex) {
        return getInstance().mTestFields.get(fieldIndex).mImeOptions;
    }

    private static int readTestFieldImeActionId(final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getInt(PREF_TEST_FIELD_IME_ACTION_ID_PREFIX + fieldId, 0);
    }

    public static int getTestFieldImeActionId(int fieldIndex) {
        return getInstance().mTestFields.get(fieldIndex).mImeActionId;
    }

    private static String readTestFieldImeActionLabel(final SharedPreferenceManager prefs,
                                                      int fieldId) {
        return prefs.getString(PREF_TEST_FIELD_IME_ACTION_LABEL_PREFIX + fieldId, null);
    }

    public static String getTestFieldImeActionLabel(int fieldIndex) {
        return getInstance().mTestFields.get(fieldIndex).mImeActionLabel;
    }

    private static String readTestFieldPrivateImeOptions(final SharedPreferenceManager prefs,
                                                         int fieldId) {
        return prefs.getString(PREF_TEST_FIELD_PRIVATE_IME_OPTIONS_PREFIX + fieldId, null);
    }

    public static String getTestFieldPrivateImeOptions(int fieldIndex) {
        return getInstance().mTestFields.get(fieldIndex).mPrivateImeOptions;
    }

    private static boolean readTestFieldSelectAllOnFocus(final SharedPreferenceManager prefs,
                                                         int fieldId) {
        return prefs.getBoolean(PREF_TEST_FIELD_SELECT_ALL_ON_FOCUS_PREFIX + fieldId, false);
    }

    public static boolean shouldTestFieldSelectAllOnFocus(int fieldIndex) {
        return getInstance().mTestFields.get(fieldIndex).mSelectAllOnFocus;
    }

    private static int readTestFieldMaxLength(final SharedPreferenceManager prefs, int fieldId) {
        return prefs.getInt(PREF_TEST_FIELD_MAX_LENGTH_PREFIX + fieldId, -1);
    }

    public static int getTestFieldMaxLength(int fieldIndex) {
        return getInstance().mTestFields.get(fieldIndex).mMaxLength;
    }

    private static boolean readTestFieldAllowUndo(final SharedPreferenceManager prefs,
                                                  int fieldId) {
        return prefs.getBoolean(PREF_TEST_FIELD_ALLOW_UNDO_PREFIX + fieldId, true);
    }

    public static boolean shouldTestFieldAllowUndo(int fieldIndex) {
        return getInstance().mTestFields.get(fieldIndex).mAllowUndo;
    }

    private static Locale[] readTestFieldTextLocales(final SharedPreferenceManager prefs,
                                                     int fieldId) {
        return (new LocaleEntryListPreference.Reader(prefs,
                PREF_TEST_FIELD_TEXT_LOCALES_PREFIX + fieldId)).readValue();
    }

    public static Locale[] getTestFieldTextLocales(int fieldIndex) {
        return getInstance().mTestFields.get(fieldIndex).mTextLocales;
    }

    private static Locale[] readTestFieldImeHintLocales(final SharedPreferenceManager prefs,
                                                        int fieldId) {
        return (new LocaleEntryListPreference.Reader(prefs,
                PREF_TEST_FIELD_IME_HINT_LOCALES_PREFIX + fieldId)).readValue();
    }

    public static Locale[] getTestFieldImeHintLocales(int fieldIndex) {
        return getInstance().mTestFields.get(fieldIndex).mImeHintLocales;
    }

    private static CharSequence readTestFieldDefaultText(final SharedPreferenceManager prefs,
                                                         int fieldId) {
        return prefs.getCharSequence(PREF_TEST_FIELD_IME_DEFAULT_TEXT_PREFIX + fieldId, null);
    }

    public static CharSequence getTestFieldDefaultText(int fieldIndex) {
        return getInstance().mTestFields.get(fieldIndex).mDefaultText;
    }

    private static CharSequence readTestFieldHintText(final SharedPreferenceManager prefs,
                                                      int fieldId) {
        return prefs.getCharSequence(PREF_TEST_FIELD_IME_HINT_TEXT_PREFIX + fieldId, null);
    }

    public static CharSequence getTestFieldHintText(int fieldIndex) {
        return getInstance().mTestFields.get(fieldIndex).mHintText;
    }

    private static class TestField {
        private final int mId;

        private int mInputType;
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

        public TestField(int id) {
            mId = id;
        }
    }
}
