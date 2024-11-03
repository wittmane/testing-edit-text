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

import static com.wittmane.testingedittext.settings.PreferenceKeys.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.aosp.internal.util.ArrayUtils;
import com.wittmane.testingedittext.function.Predicate;
import com.wittmane.testingedittext.settings.SharedPreferenceManager.Editor;

import java.util.Arrays;
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

    private int[] mTestGroupIds;
    private final Map<Integer, TestGroup> mTestGroups = new HashMap<>();
    private final Map<Integer, TestField> mTestFields = new HashMap<>();
    private final AppLevelDefaults mTestFieldDefaults = new AppLevelDefaults();

    private String mTheme;
    private boolean mShowReferenceEditText;

    private SharedPreferenceManager mPrefs;
    private PreferenceReader mPreferenceReader;

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
        mPreferenceReader = new PreferenceReader(mPrefs);

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

    /* package */ SharedPreferenceManager getPrefManager() {
        return mPrefs;
    }

    /* package */ PreferenceReader getPreferenceReader() {
        return mPreferenceReader;
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
        int[] groupIds = prefs.getIntArray(PREF_TEST_GROUP_IDS, new int[0]);
        if (groupIds == null) {
            return;
        }
        for (int groupId : groupIds) {
            int[] fieldIds = prefs.getIntArray(PREF_TEST_FIELD_IDS_PREFIX + GROUP_INFIX + groupId,
                    new int[0]);
            if (fieldIds == null) {
                continue;
            }
            for (int fieldId : fieldIds) {
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

    /* package */ void loadSettings() {
        for (String prefKey : MISC_PREF_KEYS) {
            loadSetting(prefKey);
        }
        loadTestFieldSettings(BASE_FIELD_ID);
        mTestGroupIds = mPreferenceReader.readIntArray(PREF_TEST_GROUP_IDS);
        mTestGroups.clear();
        mTestFields.clear();
        for (int groupId : mTestGroupIds) {
            loadExistingGroup(groupId);
        }
    }

    private TestGroup loadExistingGroup(int groupId) {
        //TODO: (EW) see if PREF_TEST_GROUP_NAME_PREFIX can only be loaded in 1 call
        String groupName =
                mPreferenceReader.readTestGroupString(groupId, PREF_TEST_GROUP_NAME_PREFIX);
        int[] groupFieldIds =
                mPreferenceReader.readTestGroupIntArray(groupId, PREF_TEST_FIELD_IDS_PREFIX);
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
                mTheme = mPreferenceReader.readString(PREF_THEME);
                break;
            case PREF_SHOW_REFERENCE_EDITTEXT:
                mShowReferenceEditText =
                        mPreferenceReader.readBoolean(PREF_SHOW_REFERENCE_EDITTEXT);
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
                //TODO: (EW) see if PREF_TEST_GROUP_NAME_PREFIX can only be loaded in 1 call
                getGroupById(groupId).mName = mPreferenceReader.readTestGroupString(groupId,
                        PREF_TEST_GROUP_NAME_PREFIX);
                break;
            default:
                Log.w(TAG, "Preference " + prefKeyPrefix + GROUP_INFIX + groupId
                        + " wasn't processed");
        }
    }

    private void loadTestFieldSetting(String prefKeyPrefix, int fieldId) {
        TestField testField = getField(fieldId);
        mPreferenceReader.loadTestFieldSetting(prefKeyPrefix, testField);
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
        mPreferenceReader.loadTestFieldOrDefaultSetting(prefKeyPrefix, testFieldOrDefault);
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

    private static void setTestFieldGroupIds(Editor editor, int[] groupIds) {
        editor.putIntArray(PREF_TEST_GROUP_IDS, groupIds);
        getInstance().mTestGroupIds = deepCopy(groupIds);
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

        Set<Integer> oldGroupIds = settings.getGroupIds();
        Set<Integer> newGroupIds = new HashSet<>();
        Set<Integer> oldFieldIds = settings.getFieldIds();
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
        int groupId = getNextId(settings.getGroupIds());
        setTestFieldGroupIds(ArrayUtils.appendInt(settings.mTestGroupIds, groupId, true));
    }

    public static void removeTestFieldGroup(int groupIndex) {
        Settings settings = getInstance();
        setTestFieldGroupIds(ArrayUtils.removeIntAt(settings.mTestGroupIds, groupIndex));
    }

    public static void addTestField(int groupIndex) {
        Settings settings = getInstance();
        int fieldId = getNextId(settings.getFieldIds());
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

    /* package */ Set<Integer> getGroupIds() {
        return mTestGroups.keySet();
    }

    /* package */ Set<Integer> getFieldIds() {
        return mTestFields.keySet();
    }

    /* package */ static int getNextId(Iterable<Integer> existingIds) {
        int max = -1;
        for (int id : existingIds) {
            if (id > max) {
                max = id;
            }
        }
        return max + 1;
    }

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

    private static int[] deepCopy(int[] array) {
        int[] copy = new int[array.length];
        System.arraycopy(array, 0, copy, 0, array.length);
        return copy;
    }

    public static TestFieldSettings getTestFieldSettings(int groupIndex, int fieldIndex) {
        return new TestFieldSettings(groupIndex, fieldIndex);
    }

    private static class TestFieldCustomEditorSettings implements EditorSettings {
        //TODO: (EW) would it be better to use the field ID instead of the index?
        private final int mGroupIndex;
        private final int mFieldIndex;

        private TestFieldCustomEditorSettings(int groupIndex, int fieldIndex) {
            mGroupIndex = groupIndex;
            mFieldIndex = fieldIndex;
        }

        protected TestField getField() {
            return Settings.getField(mGroupIndex, mFieldIndex);
        }

        private AppLevelDefaults getTestFieldOrBase(Predicate<TestField> override) {
            TestField testField = getField();
            if (!override.test(testField)) {
                return getInstance().mTestFieldDefaults;
            }
            return testField;
        }

        private AppLevelDefaults getTestFieldOrBaseForTextInputModification() {
            return getTestFieldOrBase(testField -> testField.mOverrideTextInputModification);
        }

        private AppLevelDefaults getTestFieldOrBaseForTextReturn() {
            return getTestFieldOrBase(testField -> testField.mOverrideTextReturn);
        }

        private AppLevelDefaults getTestFieldOrBaseForTextComposition() {
            return getTestFieldOrBase(testField -> testField.mOverrideTextComposition);
        }

        private AppLevelDefaults getTestFieldOrBaseForTargetVersion() {
            return getTestFieldOrBase(testField -> testField.mOverrideTargetVersion);
        }

        private AppLevelDefaults getTestFieldOrBaseForSystemBehavior() {
            return getTestFieldOrBase(testField -> testField.mOverrideSystemBehavior);
        }

        @Override
        public boolean getNullInputTypeMultiline() {
            return getField().mNullInputTypeMultiline;
        }

        @Override
        public boolean shouldCreateInputConnection() {
            return getField().mCreateInputConnection;
        }

        @Override
        public boolean shouldSendSelectionInfo() {
            return getField().mSendSelectionInfo;
        }

        @Override
        public boolean shouldSendText() {
            return getField().mSendText;
        }

        @Override
        public int getComposingTextBehavior() {
            return getField().mComposingTextBehavior;
        }

        @Override
        public boolean allowDeleteSurroundingText() {
            return getField().mAllowDeleteSurroundingText;
        }

        @Override
        public boolean allowSettingSelection() {
            return getField().mAllowSettingSelection;
        }

        @Override
        public boolean shouldModifyCommittedText() {
            return getTestFieldOrBaseForTextInputModification().mModifyCommittedText;
        }

        @Override
        public boolean shouldModifyComposedText() {
            return getTestFieldOrBaseForTextInputModification().mModifyComposedText;
        }

        @Override
        public boolean shouldModifyComposedChangesOnly() {
            return getTestFieldOrBaseForTextInputModification().mModifyComposedChangesOnly;
        }

        @Override
        public boolean shouldConsiderComposedChangesFromEnd() {
            return getTestFieldOrBaseForTextInputModification().mConsiderComposedChangesFromEnd;
        }

        @Override
        public boolean shouldRestrictToInclude() {
            return getTestFieldOrBaseForTextInputModification().mRestrictToInclude;
        }

        @Override
        public String[] getRestrictSpecific() {
            return getTestFieldOrBaseForTextInputModification().mRestrictSpecific;
        }

        @Override
        public @Nullable IntRange getRestrictRange() {
            return getTestFieldOrBaseForTextInputModification().mRestrictRange;
        }

        @Override
        public TranslateText[] getTranslateSpecific() {
            return getTestFieldOrBaseForTextInputModification().mTranslateSpecific;
        }

        @Override
        public boolean shouldTranslateFullMatchOnly() {
            return getTestFieldOrBaseForTextInputModification().mTranslateFullMatchOnly;
        }

        @Override
        public int getCodepointShift() {
            return getTestFieldOrBaseForTextInputModification().mShiftCodepoint;
        }

        @Override
        public boolean shouldSkipExtractingText() {
            return getTestFieldOrBaseForTextReturn().mSkipExtractingText;
        }

        @Override
        public boolean shouldIgnoreExtractedTextMonitor() {
            return getTestFieldOrBaseForTextReturn().mIgnoreExtractedTextMonitor;
        }

        @Override
        public boolean shouldUpdateSelectionBeforeExtractedText() {
            return getTestFieldOrBaseForTextReturn().mUpdateSelectionBeforeExtractedText;
        }

        @Override
        public boolean shouldUpdateExtractedTextOnlyOnNetChanges() {
            return getTestFieldOrBaseForTextReturn().mUpdateExtractedTextOnlyOnNetChanges;
        }

        @Override
        public boolean shouldExtractFullText() {
            return getTestFieldOrBaseForTextReturn().mExtractFullText;
        }

        @Override
        public int getExtractMonitorTextLimit() {
            return getTestFieldOrBaseForTextReturn().mExtractMonitorTextLimit;
        }

        @Override
        public int getReturnedTextLimit() {
            return getTestFieldOrBaseForTextReturn().mReturnedTextLimit;
        }

        @Override
        public boolean shouldDeleteThroughComposingText() {
            return getTestFieldOrBaseForTextComposition().mDeleteThroughComposingText;
        }

        @Override
        public boolean shouldKeepEmptyComposingPosition() {
            return getTestFieldOrBaseForTextComposition().mKeepEmptyComposingPosition;
        }

        @Override
        public boolean shouldSkipTakeSnapshot() {
            return getTestFieldOrBaseForTargetVersion().mSkipTakeSnapshot;
        }

        @Override
        public boolean shouldSkipGetSurroundingText() {
            return getTestFieldOrBaseForTargetVersion().mSkipGetSurroundingText;
        }

        @Override
        public boolean shouldSkipPerformSpellCheck() {
            return getTestFieldOrBaseForTargetVersion().mSkipPerformSpellCheck;
        }

        @Override
        public boolean shouldSkipSetImeConsumesInput() {
            return getTestFieldOrBaseForTargetVersion().mSkipSetImeConsumesInput;
        }

        @Override
        public boolean shouldSkipCommitContent() {
            return getTestFieldOrBaseForTargetVersion().mSkipCommitContent;
        }

        @Override
        public boolean shouldSkipCloseConnection() {
            return getTestFieldOrBaseForTargetVersion().mSkipCloseConnection;
        }

        @Override
        public boolean shouldSkipDeleteSurroundingTextInCodePoints() {
            return getTestFieldOrBaseForTargetVersion().mSkipDeleteSurroundingTextInCodePoints;
        }

        @Override
        public boolean shouldSkipRequestCursorUpdates() {
            return getTestFieldOrBaseForTargetVersion().mSkipRequestCursorUpdates;
        }

        @Override
        public boolean shouldSkipCommitCorrection() {
            return getTestFieldOrBaseForTargetVersion().mSkipCommitCorrection;
        }

        @Override
        public boolean shouldSkipGetSelectedText() {
            return getTestFieldOrBaseForTargetVersion().mSkipGetSelectedText;
        }

        @Override
        public boolean shouldSkipSetComposingRegion() {
            return getTestFieldOrBaseForTargetVersion().mSkipSetComposingRegion;
        }

        @Override
        public int getUpdateDelay() {
            return getTestFieldOrBaseForSystemBehavior().mUpdateDelay;
        }

        @Override
        public int getFinishComposingTextDelay() {
            return getTestFieldOrBaseForSystemBehavior().mFinishComposingTextDelay;
        }

        @Override
        public int getGetSurroundingTextDelay() {
            return getTestFieldOrBaseForSystemBehavior().mGetSurroundingTextDelay;
        }

        @Override
        public int getGetTextBeforeCursorDelay() {
            return getTestFieldOrBaseForSystemBehavior().mGetTextBeforeCursorDelay;
        }

        @Override
        public int getGetSelectedTextDelay() {
            return getTestFieldOrBaseForSystemBehavior().mGetSelectedTextDelay;
        }

        @Override
        public int getGetTextAfterCursorDelay() {
            return getTestFieldOrBaseForSystemBehavior().mGetTextAfterCursorDelay;
        }

        @Override
        public int getGetCursorCapsModeDelay() {
            return getTestFieldOrBaseForSystemBehavior().mGetCursorCapsModeDelay;
        }

        @Override
        public int getGetExtractedTextDelay() {
            return getTestFieldOrBaseForSystemBehavior().mGetExtractedTextDelay;
        }
    }

    public static class TestFieldSettings extends TestFieldCustomEditorSettings {

        private TestFieldSettings(int groupIndex, int fieldIndex) {
            super(groupIndex, fieldIndex);
        }

        public CharSequence getLabelText() {
            return getField().mLabelText;
        }

        public CharSequence getDefaultText() {
            return getField().mDefaultText;
        }

        public CharSequence getHintText() {
            return getField().mHintText;
        }

        public int getInputType() {
            return getField().mInputType;
        }

        public int getImeOptions() {
            return getField().mImeOptions;
        }

        public int getImeActionId() {
            return getField().mImeActionId;
        }

        public String getImeActionLabel() {
            return getField().mImeActionLabel;
        }

        public String getPrivateImeOptions() {
            return getField().mPrivateImeOptions;
        }

        public boolean shouldSelectAllOnFocus() {
            return getField().mSelectAllOnFocus;
        }

        public int getMaxLength() {
            return getField().mMaxLength;
        }

        public boolean shouldAllowUndo() {
            return getField().mAllowUndo;
        }

        public Locale[] getTextLocales() {
            return getField().mTextLocales;
        }

        public Locale[] getImeHintLocales() {
            return getField().mImeHintLocales;
        }
    }
}
