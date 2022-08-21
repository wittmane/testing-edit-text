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

package com.wittmane.testingedittext;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Settings implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String PREF_LIMIT_RETURNED_TEXT = "pref_key_limit_returned_text";
    public static final String PREF_SKIP_EXTRACTING_TEXT = "pref_key_skip_extracting_text";
    public static final String PREF_EXTRACT_FULL_TEXT = "pref_key_extract_full_text";
    public static final String PREF_LIMIT_EXTRACT_MONITOR_TEXT =
            "pref_key_limit_extract_monitor_text";
    public static final String PREF_SKIP_SETCOMPOSINGREGION = "pref_key_skip_setcomposingregion";
    public static final String PREF_SKIP_GETSURROUNDINGTEXT = "pref_key_skip_getsurroundingtext";

    private int mReturnedTextLimit;
    private boolean mSkipExtractingText;
    private boolean mExtractFullText;
    private int mExtractMonitorTextLimit;
    private boolean mSkipSetComposingRegion;
    private boolean mSkipGetSurroundingText;

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
        mReturnedTextLimit = readReturnedTextLimit(mPrefs);
        mSkipExtractingText = readSkipExtractingText(mPrefs);
        mExtractFullText = readExtractFullText(mPrefs);
        mExtractMonitorTextLimit = readExtractMonitorTextLimit(mPrefs);
        mSkipSetComposingRegion = readSkipSetComposingRegion(mPrefs);
        mSkipGetSurroundingText = readSkipGetSurroundingText(mPrefs);
    }

    private static int readReturnedTextLimit(final SharedPreferences prefs) {
        return prefs.getInt(PREF_LIMIT_RETURNED_TEXT, -1);
    }

    public static int getReturnedTextLimit() {
        return getInstance().mReturnedTextLimit;
    }

    private static boolean readSkipExtractingText(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_SKIP_EXTRACTING_TEXT, false);
    }

    public static boolean shouldSkipExtractingText() {
        return getInstance().mSkipExtractingText;
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

    private static boolean readSkipSetComposingRegion(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_SKIP_SETCOMPOSINGREGION, false);
    }

    public static boolean shouldSkipSetComposingRegion() {
        return getInstance().mSkipSetComposingRegion;
    }

    private static boolean readSkipGetSurroundingText(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_SKIP_GETSURROUNDINGTEXT, false);
    }

    public static boolean shouldSkipGetSurroundingText() {
        return getInstance().mSkipGetSurroundingText;
    }
}
