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
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.Size;

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

    private boolean mModifyCommittedText;
    private boolean mModifyComposedText;
    private boolean mConsiderComposedChangesFromEnd;
    private boolean mModifyComposedChangesOnly;
    private boolean mRestrictToInclude;
    private String[] mRestrictSpecific;
    private int[] mRestrictRange;
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

    private static @Nullable @Size(2) int[] readRestrictRange(final SharedPreferences prefs) {
        return (new CodepointRangeDialogPreference.Reader(prefs, PREF_RESTRICT_RANGE)).readValue();
    }

    public static @Nullable @Size(2) int[] getRestrictRange() {
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
}
