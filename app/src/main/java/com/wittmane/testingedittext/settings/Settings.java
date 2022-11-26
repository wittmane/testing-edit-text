/*
 * Copyright (C) 2022 Eli Wittman
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

import com.wittmane.testingedittext.BuildConfig;
import com.wittmane.testingedittext.settings.preferences.TextListPreference;
import com.wittmane.testingedittext.settings.preferences.CodepointRangeDialogPreference;
import com.wittmane.testingedittext.settings.preferences.TextTranslateListPreference;

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
    public static final String PREF_USE_DEBUG_SCREEN = "pref_key_use_debug_screen";
    public static final String PREF_INPUT_TYPE_CLASS = "pref_key_input_type_class";
    public static final String PREF_INPUT_TYPE_TEXT_VARIATION =
            "pref_key_input_type_text_variation";
    public static final String PREF_INPUT_TYPE_NUMBER_VARIATION =
            "pref_key_input_type_number_variation";
    public static final String PREF_INPUT_TYPE_DATETIME_VARIATION =
            "pref_key_input_type_datetime_variation";
    public static final String PREF_INPUT_TYPE_TEXT_FLAG_MULTI_LINE =
            "pref_key_input_type_text_flag_multi_line";
    public static final String PREF_INPUT_TYPE_TEXT_FLAG_CAP =
            "pref_key_input_type_text_flag_cap";
    public static final String PREF_INPUT_TYPE_TEXT_FLAG_AUTO_COMPLETE =
            "pref_key_input_type_text_flag_auto_complete";
    public static final String PREF_INPUT_TYPE_TEXT_FLAG_AUTO_CORRECT =
            "pref_key_input_type_text_flag_auto_correct";
    public static final String PREF_INPUT_TYPE_TEXT_FLAG_NO_SUGGESTIONS =
            "pref_key_input_type_text_flag_no_suggestions";
    public static final String PREF_INPUT_TYPE_NUMBER_FLAG_SIGNED =
            "pref_key_input_type_number_flag_signed";
    public static final String PREF_INPUT_TYPE_NUMBER_FLAG_DECIMAL =
            "pref_key_input_type_number_flag_decimal";
    public static final String PREF_IME_OPTIONS_ACTION = "pref_key_ime_options_action";
    public static final String PREF_IME_OPTIONS_FLAG_FORCE_ASCII =
            "pref_key_ime_options_flag_force_ascii";
    public static final String PREF_IME_OPTIONS_FLAG_NAVIGATE_NEXT =
            "pref_key_ime_options_flag_navigate_next";
    public static final String PREF_IME_OPTIONS_FLAG_NAVIGATE_PREVIOUS =
            "pref_key_ime_options_flag_navigate_previous";
    public static final String PREF_IME_OPTIONS_FLAG_NO_ACCESSORY_ACTION =
            "pref_key_ime_options_flag_no_accessory_action";
    public static final String PREF_IME_OPTIONS_FLAG_NO_ENTER_ACTION =
            "pref_key_ime_options_flag_no_enter_action";
    public static final String PREF_IME_OPTIONS_FLAG_NO_EXTRACT_UI =
            "pref_key_ime_options_flag_no_extract_ui";
    public static final String PREF_IME_OPTIONS_FLAG_NO_FULLSCREEN =
            "pref_key_ime_options_flag_no_fullscreen";
    public static final String PREF_IME_OPTIONS_FLAG_NO_PERSONALIZED_LEARNING =
            "pref_key_ime_options_flag_no_personalized_learning";
    public static final String PREF_IME_ACTION_ID = "pref_key_ime_action_id";
    public static final String PREF_IME_ACTION_LABEL = "pref_key_ime_action_label";
    public static final String PREF_PRIVATE_IME_OPTIONS = "pref_key_private_ime_options";
    public static final String PREF_SELECT_ALL_ON_FOCUS = "pref_key_select_all_on_focus";
    public static final String PREF_MAX_LENGTH = "pref_key_max_length";
    public static final String PREF_ALLOW_UNDO = "pref_key_allow_undo";

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
    private boolean mUseDebugScreen;
    private int mInputType;
    private int mImeOptions;
    private int mImeActionId;
    private String mImeActionLabel;
    private String mPrivateImeOptions;
    private boolean mSelectAllOnFocus;
    private int mMaxLength;
    private boolean mAllowUndo;

    private SharedPreferences mPrefs;

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
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mPrefs.registerOnSharedPreferenceChangeListener(this);
        loadSettings();
    }

    public static void onDestroy() {
        getInstance().mPrefs.unregisterOnSharedPreferenceChangeListener(getInstance());
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        loadSettings();
    }

    private void loadSettings() {
        mModifyCommittedText = readModifyCommittedText(mPrefs);
        mModifyComposedText = readModifyComposedText(mPrefs);
        mModifyComposedChangesOnly = readModifyComposedChangesOnly(mPrefs);
        mConsiderComposedChangesFromEnd = readConsiderComposedChangesFromEnd(mPrefs);
        mRestrictToInclude = readRestrictToInclude(mPrefs);
        mRestrictSpecific = readRestrictSpecific(mPrefs);
        mRestrictRange = readRestrictRange(mPrefs);
        mTranslateSpecific = readTranslateSpecific(mPrefs);
        mShiftCodepoint = readShiftCodepoint(mPrefs);

        mSkipExtractingText = readSkipExtractingText(mPrefs);
        mIgnoreExtractedTextMonitor = readIgnoreExtractedTextMonitor(mPrefs);
        mUpdateSelectionBeforeExtractedText = readUpdateSelectionBeforeExtractedText(mPrefs);
        mUpdateExtractedTextOnlyOnNetChanges = readUpdateExtractedTextOnlyOnNetChanges(mPrefs);
        mExtractFullText = readExtractFullText(mPrefs);
        mExtractMonitorTextLimit = readExtractMonitorTextLimit(mPrefs);
        mReturnedTextLimit = readReturnedTextLimit(mPrefs);

        mDeleteThroughComposingText = readDeleteThroughComposingText(mPrefs);
        mKeepEmptyComposingPosition = readKeepEmptyComposingPosition(mPrefs);

        mSkipGetSurroundingText = readSkipGetSurroundingText(mPrefs);
        mSkipPerformSpellCheck = readSkipPerformSpellCheck(mPrefs);
        mSkipSetImeConsumesInput = readSkipSetImeConsumesInput(mPrefs);
        mSkipCommitContent = readSkipCommitContent(mPrefs);
        mSkipCloseConnection = readSkipCloseConnection(mPrefs);
        mSkipDeleteSurroundingTextInCodePoints = readSkipDeleteSurroundingTextInCodePoints(mPrefs);
        mSkipRequestCursorUpdates = readSkipRequestCursorUpdates(mPrefs);
        mSkipCommitCorrection = readSkipCommitCorrection(mPrefs);
        mSkipGetSelectedText = readSkipGetSelectedText(mPrefs);
        mSkipSetComposingRegion = readSkipSetComposingRegion(mPrefs);

        mUpdateDelay = readUpdateDelay(mPrefs);
        mFinishComposingTextDelay = readFinishComposingTextDelay(mPrefs);
        mGetSurroundingTextDelay = readGetSurroundingTextDelay(mPrefs);
        mGetTextBeforeCursorDelay = readGetTextBeforeCursorDelay(mPrefs);
        mGetSelectedTextDelay = readGetSelectedTextDelay(mPrefs);
        mGetTextAfterCursorDelay = readGetTextAfterCursorDelay(mPrefs);
        mGetCursorCapsModeDelay = readGetCursorCapsModeDelay(mPrefs);
        mGetExtractedTextDelay = readGetExtractedTextDelay(mPrefs);

        mUseDebugScreen = readUseDebugScreen(mPrefs);
        mInputType = readInputType(mPrefs);
        mImeOptions = readImeOptions(mPrefs);
        mImeActionId = readImeActionId(mPrefs);
        mImeActionLabel = readImeActionLabel(mPrefs);
        mPrivateImeOptions = readPrivateImeOptions(mPrefs);
        mSelectAllOnFocus = readSelectAllOnFocus(mPrefs);
        mMaxLength = readMaxLength(mPrefs);
        mAllowUndo = readAllowUndo(mPrefs);
    }

    private static boolean readModifyCommittedText(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_MODIFY_COMMITTED_TEXT, false);
    }

    public static boolean shouldModifyCommittedText() {
        return getInstance().mModifyCommittedText;
    }

    private static boolean readModifyComposedText(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_MODIFY_COMPOSED_TEXT, false);
    }

    public static boolean shouldModifyComposedText() {
        return getInstance().mModifyComposedText;
    }

    private static boolean readModifyComposedChangesOnly(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_MODIFY_COMPOSED_CHANGES_ONLY, false);
    }

    public static boolean shouldModifyComposedChangesOnly() {
        return getInstance().mModifyComposedChangesOnly;
    }

    private static boolean readConsiderComposedChangesFromEnd(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_CONSIDER_COMPOSED_CHANGES_FROM_END, false);
    }

    public static boolean shouldConsiderComposedChangesFromEnd() {
        return getInstance().mConsiderComposedChangesFromEnd;
    }

    private static boolean readRestrictToInclude(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_RESTRICT_TO_INCLUDE, false);
    }

    public static boolean shouldRestrictToInclude() {
        return getInstance().mRestrictToInclude;
    }

    private static String[] readRestrictSpecific(final SharedPreferences prefs) {
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
    private static IntRange readRestrictRange(final SharedPreferences prefs) {
        return (new CodepointRangeDialogPreference.Reader(prefs, PREF_RESTRICT_RANGE)).readValue();
    }

    public static @Nullable IntRange getRestrictRange() {
        return getInstance().mRestrictRange;
    }

    private static TranslateText[] readTranslateSpecific(final SharedPreferences prefs) {
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

    private static int readShiftCodepoint(final SharedPreferences prefs) {
        return prefs.getInt(PREF_SHIFT_CODEPOINT, 0);
    }

    public static int getShiftCodepoint() {
        return getInstance().mShiftCodepoint;
    }

    private static boolean readSkipExtractingText(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_SKIP_EXTRACTING_TEXT, false);
    }

    public static boolean shouldSkipExtractingText() {
        return getInstance().mSkipExtractingText;
    }

    private static boolean readIgnoreExtractedTextMonitor(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_IGNORE_EXTRACTED_TEXT_MONITOR, false);
    }

    public static boolean shouldIgnoreExtractedTextMonitor() {
        return getInstance().mIgnoreExtractedTextMonitor;
    }

    private static boolean readUpdateSelectionBeforeExtractedText(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT, false);
    }

    public static boolean shouldUpdateSelectionBeforeExtractedText() {
        return getInstance().mUpdateSelectionBeforeExtractedText;
    }

    private static boolean readUpdateExtractedTextOnlyOnNetChanges(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES, false);
    }

    public static boolean shouldUpdateExtractedTextOnlyOnNetChanges() {
        return getInstance().mUpdateExtractedTextOnlyOnNetChanges;
    }

    private static boolean readExtractFullText(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_EXTRACT_FULL_TEXT, false);
    }

    public static boolean shouldExtractFullText() {
        return getInstance().mExtractFullText;
    }

    private static int readExtractMonitorTextLimit(final SharedPreferences prefs) {
        return prefs.getInt(PREF_LIMIT_EXTRACT_MONITOR_TEXT, -1);
    }

    public static int getExtractMonitorTextLimit() {
        return getInstance().mExtractMonitorTextLimit;
    }

    private static int readReturnedTextLimit(final SharedPreferences prefs) {
        return prefs.getInt(PREF_LIMIT_RETURNED_TEXT, -1);
    }

    public static int getReturnedTextLimit() {
        return getInstance().mReturnedTextLimit;
    }

    private static boolean readDeleteThroughComposingText(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_DELETE_THROUGH_COMPOSING_TEXT, false);
    }

    public static boolean shouldDeleteThroughComposingText() {
        return getInstance().mDeleteThroughComposingText;
    }

    private static boolean readKeepEmptyComposingPosition(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_KEEP_EMPTY_COMPOSING_POSITION, false);
    }

    public static boolean shouldKeepEmptyComposingPosition() {
        return getInstance().mKeepEmptyComposingPosition;
    }

    private static boolean readSkipGetSurroundingText(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_SKIP_GETSURROUNDINGTEXT, false);
    }

    public static boolean shouldSkipGetSurroundingText() {
        return getInstance().mSkipGetSurroundingText;
    }

    private static boolean readSkipPerformSpellCheck(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_SKIP_PERFORMSPELLCHECK, false);
    }

    public static boolean shouldSkipPerformSpellCheck() {
        return getInstance().mSkipPerformSpellCheck;
    }

    private static boolean readSkipSetImeConsumesInput(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_SKIP_SETIMECONSUMESINPUT, false);
    }

    public static boolean shouldSkipSetImeConsumesInput() {
        return getInstance().mSkipSetImeConsumesInput;
    }

    private static boolean readSkipCommitContent(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_SKIP_COMMITCONTENT, false);
    }

    public static boolean shouldSkipCommitContent() {
        return getInstance().mSkipCommitContent;
    }

    private static boolean readSkipCloseConnection(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_SKIP_CLOSECONNECTION, false);
    }

    public static boolean shouldSkipCloseConnection() {
        return getInstance().mSkipCloseConnection;
    }

    private static boolean readSkipDeleteSurroundingTextInCodePoints(
            final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_SKIP_DELETESURROUNDINGTEXTINCODEPOINTS, false);
    }

    public static boolean shouldSkipDeleteSurroundingTextInCodePoints() {
        return getInstance().mSkipDeleteSurroundingTextInCodePoints;
    }

    private static boolean readSkipRequestCursorUpdates(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_SKIP_REQUESTCURSORUPDATES, false);
    }

    public static boolean shouldSkipRequestCursorUpdates() {
        return getInstance().mSkipRequestCursorUpdates;
    }

    private static boolean readSkipCommitCorrection(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_SKIP_COMMITCORRECTION, false);
    }

    public static boolean shouldSkipCommitCorrection() {
        return getInstance().mSkipCommitCorrection;
    }

    private static boolean readSkipGetSelectedText(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_SKIP_GETSELECTEDTEXT, false);
    }

    public static boolean shouldSkipGetSelectedText() {
        return getInstance().mSkipGetSelectedText;
    }

    private static boolean readSkipSetComposingRegion(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_SKIP_SETCOMPOSINGREGION, false);
    }

    public static boolean shouldSkipSetComposingRegion() {
        return getInstance().mSkipSetComposingRegion;
    }

    private static int readUpdateDelay(final SharedPreferences prefs) {
        return prefs.getInt(PREF_UPDATE_DELAY, 0);
    }

    public static int getUpdateDelay() {
        return getInstance().mUpdateDelay;
    }

    private static int readFinishComposingTextDelay(final SharedPreferences prefs) {
        return prefs.getInt(PREF_FINISHCOMPOSINGTEXT_DELAY, 0);
    }

    public static int getFinishComposingTextDelay() {
        return getInstance().mFinishComposingTextDelay;
    }

    private static int readGetSurroundingTextDelay(final SharedPreferences prefs) {
        return prefs.getInt(PREF_GETSURROUNDINGTEXT_DELAY, 0);
    }

    public static int getGetSurroundingTextDelay() {
        return getInstance().mGetSurroundingTextDelay;
    }

    private static int readGetTextBeforeCursorDelay(final SharedPreferences prefs) {
        return prefs.getInt(PREF_GETTEXTBEFORECURSOR_DELAY, 0);
    }

    public static int getGetTextBeforeCursorDelay() {
        return getInstance().mGetTextBeforeCursorDelay;
    }

    private static int readGetSelectedTextDelay(final SharedPreferences prefs) {
        return prefs.getInt(PREF_GETSELECTEDTEXT_DELAY, 0);
    }

    public static int getGetSelectedTextDelay() {
        return getInstance().mGetSelectedTextDelay;
    }

    private static int readGetTextAfterCursorDelay(final SharedPreferences prefs) {
        return prefs.getInt(PREF_GETTEXTAFTERCURSOR_DELAY, 0);
    }

    public static int getGetTextAfterCursorDelay() {
        return getInstance().mGetTextAfterCursorDelay;
    }

    private static int readGetCursorCapsModeDelay(final SharedPreferences prefs) {
        return prefs.getInt(PREF_GETCURSORCAPSMODE_DELAY, 0);
    }

    public static int getGetCursorCapsModeDelay() {
        return getInstance().mGetCursorCapsModeDelay;
    }

    private static int readGetExtractedTextDelay(final SharedPreferences prefs) {
        return prefs.getInt(PREF_GETEXTRACTEDTEXT_DELAY, 0);
    }

    public static int getGetExtractedTextDelay() {
        return getInstance().mGetExtractedTextDelay;
    }

    private static boolean readUseDebugScreen(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_USE_DEBUG_SCREEN, false);
    }

    public static boolean useDebugScreen() {
        if (!BuildConfig.DEBUG) {
            return false;
        }
        return getInstance().mUseDebugScreen;
    }

    private static int readInputType(final SharedPreferences prefs) {
        String inputTypeClass = prefs.getString(PREF_INPUT_TYPE_CLASS, "TYPE_CLASS_TEXT");
        String variation;
        int inputType;
        switch (inputTypeClass) {
            case "TYPE_CLASS_DATETIME":
                inputType = InputType.TYPE_CLASS_DATETIME;
                variation = prefs.getString(PREF_INPUT_TYPE_DATETIME_VARIATION,
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
                variation = prefs.getString(PREF_INPUT_TYPE_NUMBER_VARIATION,
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
                if (prefs.getBoolean(PREF_INPUT_TYPE_NUMBER_FLAG_SIGNED, false)) {
                    inputType |= InputType.TYPE_NUMBER_FLAG_SIGNED;
                }
                if (prefs.getBoolean(PREF_INPUT_TYPE_NUMBER_FLAG_DECIMAL, false)) {
                    inputType |= InputType.TYPE_NUMBER_FLAG_DECIMAL;
                }
                break;
            case "TYPE_CLASS_PHONE":
                inputType = InputType.TYPE_CLASS_PHONE;
                break;
            case "TYPE_CLASS_TEXT":
                inputType = InputType.TYPE_CLASS_TEXT;
                variation = prefs.getString(PREF_INPUT_TYPE_TEXT_VARIATION,
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
                String multiLineFlag = prefs.getString(PREF_INPUT_TYPE_TEXT_FLAG_MULTI_LINE, "");
                switch (multiLineFlag) {
                    case "TYPE_TEXT_FLAG_MULTI_LINE":
                        inputType |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;
                        break;
                    case "TYPE_TEXT_FLAG_IME_MULTI_LINE":
                        inputType |= InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE;
                        break;
                }
                String capFlag = prefs.getString(PREF_INPUT_TYPE_TEXT_FLAG_CAP, "");
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
                if (prefs.getBoolean(PREF_INPUT_TYPE_TEXT_FLAG_AUTO_COMPLETE, false)) {
                    inputType |= InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE;
                }
                if (prefs.getBoolean(PREF_INPUT_TYPE_TEXT_FLAG_AUTO_CORRECT, false)) {
                    inputType |= InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
                }
                if (prefs.getBoolean(PREF_INPUT_TYPE_TEXT_FLAG_NO_SUGGESTIONS, false)) {
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

    public static int getInputType() {
        return getInstance().mInputType;
    }

    private static int readImeOptions(final SharedPreferences prefs) {
        String imeOptionsAction = prefs.getString(PREF_IME_OPTIONS_ACTION,
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
        if (prefs.getBoolean(PREF_IME_OPTIONS_FLAG_FORCE_ASCII, false)) {
            imeOptions |=  EditorInfo.IME_FLAG_FORCE_ASCII;
        }
        if (prefs.getBoolean(PREF_IME_OPTIONS_FLAG_NAVIGATE_NEXT, false)) {
            imeOptions |=  EditorInfo.IME_FLAG_NAVIGATE_NEXT;
        }
        if (prefs.getBoolean(PREF_IME_OPTIONS_FLAG_NAVIGATE_PREVIOUS, false)) {
            imeOptions |=  EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS;
        }
        if (prefs.getBoolean(PREF_IME_OPTIONS_FLAG_NO_ACCESSORY_ACTION, false)) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_ACCESSORY_ACTION;
        }
        if (prefs.getBoolean(PREF_IME_OPTIONS_FLAG_NO_ENTER_ACTION, false)) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        }
        if (prefs.getBoolean(PREF_IME_OPTIONS_FLAG_NO_EXTRACT_UI, false)) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        }
        if (prefs.getBoolean(PREF_IME_OPTIONS_FLAG_NO_FULLSCREEN, false)) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_FULLSCREEN;
        }
        if (prefs.getBoolean(PREF_IME_OPTIONS_FLAG_NO_PERSONALIZED_LEARNING, false)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            imeOptions |=  EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        }
        return imeOptions;
    }

    public static int getImeOptions() {
        return getInstance().mImeOptions;
    }

    private static int readImeActionId(final SharedPreferences prefs) {
        return prefs.getInt(PREF_IME_ACTION_ID, 0);
    }

    public static int getImeActionId() {
        return getInstance().mImeActionId;
    }

    private static String readImeActionLabel(final SharedPreferences prefs) {
        return prefs.getString(PREF_IME_ACTION_LABEL, null);
    }

    public static String getImeActionLabel() {
        return getInstance().mImeActionLabel;
    }

    private static String readPrivateImeOptions(final SharedPreferences prefs) {
        return prefs.getString(PREF_PRIVATE_IME_OPTIONS, null);
    }

    public static String getPrivateImeOptions() {
        return getInstance().mPrivateImeOptions;
    }

    private static boolean readSelectAllOnFocus(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_SELECT_ALL_ON_FOCUS, false);
    }

    public static boolean shouldSelectAllOnFocus() {
        return getInstance().mSelectAllOnFocus;
    }

    private static int readMaxLength(final SharedPreferences prefs) {
        return prefs.getInt(PREF_MAX_LENGTH, -1);
    }

    public static int getMaxLength() {
        return getInstance().mMaxLength;
    }

    private static boolean readAllowUndo(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_ALLOW_UNDO, true);
    }

    public static boolean shouldAllowUndo() {
        return getInstance().mAllowUndo;
    }
}
