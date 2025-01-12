/*
 * Copyright (C) 2022-2025 Eli Wittman
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
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.aosp.com.android.internal.util.ArrayUtils;
import com.wittmane.testingedittext.datatype.IntRange;
import com.wittmane.testingedittext.datatype.TranslateText;
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
    private final AppLevelFieldDefaults mTestFieldDefaults = new AppLevelFieldDefaults();

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

        if (!mPrefs.contains(PREF_TEST_GROUP_IDS)) {
            // create a default group and field the first time the app is opened
            Log.d(TAG, "creating defaults");
            mPrefs.setIntArray(PREF_TEST_GROUP_IDS, new int[] { 0 });
            mPrefs.setIntArray(PREF_TEST_FIELD_IDS_PREFIX + GROUP_INFIX + 0, new int[] { 0 });
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

    private static boolean containsIdSuffix(String prefKey, String infix) {
        return prefKey.matches(".*" + Pattern.quote(infix) + "\\d+$");
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
            if (!loadBasicSetting(PreferenceKey.createBasicKey(prefKey))) {
                Log.e(TAG, "Basic preference " + prefKey + " wasn't processed");
            }
        }
        mPreferenceReader.loadTestFieldDefaultableSettings(mTestFieldDefaults);
        mTestGroupIds = mPreferenceReader.readIntArray(
                PreferenceKey.createBasicKey(PREF_TEST_GROUP_IDS));
        mTestGroups.clear();
        mTestFields.clear();
        for (int groupId : mTestGroupIds) {
            loadExistingGroup(groupId);
        }
    }

    private TestGroup loadExistingGroup(int groupId) {
        String groupName = mPreferenceReader.readString(
                PreferenceKey.createGroupKey(PREF_TEST_GROUP_NAME_PREFIX, groupId));
        int[] groupFieldIds = mPreferenceReader.readIntArray(
                PreferenceKey.createGroupKey(PREF_TEST_FIELD_IDS_PREFIX, groupId));
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
        mPreferenceReader.loadTestFieldSpecificSettings(field);
        mPreferenceReader.loadTestFieldDefaultableSettings(field);
        return field;
    }

    private void loadSetting(String prefKeyString) {
        PreferenceKey prefKey = PreferenceKey.parse(prefKeyString);
        if (prefKey == null) {
            return;
        }
        if (prefKey.isFieldDefault()) {
            if (!mPreferenceReader.loadTestFieldDefaultableSetting(prefKey, mTestFieldDefaults)) {
                Log.w(TAG, "Test field defaultable preference " + prefKey + " wasn't processed");
            }
        } else if (prefKey.isGroup()) {
            if (!mTestGroups.containsKey(prefKey.getId())
                    && !mPreferenceReader.contains(prefKey)) {
                // this is most likely from deleting an old preference when the parent is deleted,
                // so we don't need to bother loading this value
                return;
            }
            if (!isGroupIdValid(prefKey.getId())) {
                Log.e(TAG, "The group " + prefKey.getId() + " for pref " + prefKey
                        + " doesn't exist");
                return;
            }
            if (!loadTestGroupSetting(prefKey)) {
                Log.w(TAG, "Group preference " + prefKey + " wasn't processed");
            }
        } else if (prefKey.isField()) {
            if (!mTestFields.containsKey(prefKey.getId()) && !mPreferenceReader.contains(prefKey)) {
                // this is most likely from deleting an old preference when the parent is deleted,
                // so we don't need to bother loading this value
                return;
            }
            if (!isFieldIdValid(prefKey.getId())) {
                Log.e(TAG, "The field " + prefKey.getId() + " for pref " + prefKey
                        + " doesn't exist");
                return;
            }
            TestField testField = getField(prefKey.getId());
            if (!mPreferenceReader.loadTestFieldSpecificSetting(prefKey, testField)
                    && !mPreferenceReader.loadTestFieldDefaultableSetting(prefKey, testField)) {
                Log.w(TAG, "Test field preference " + prefKey + " wasn't processed");

            }
        } else {
            if (!loadBasicSetting(prefKey)) {
                Log.w(TAG, "Basic preference " + prefKey + " wasn't processed");
            }
        }
    }

    private boolean loadBasicSetting(PreferenceKey prefKey) {
        if (prefKey == null) {
            return false;
        }
        switch (prefKey.toString()) {
            case PREF_TEST_GROUP_IDS:
                // internal state is updated while these are modified since they aren't managed by a
                // simple Preference, so we don't need to do anything when these change
                break;
            case PREF_THEME:
                mTheme = mPreferenceReader.readString(prefKey);
                break;
            case PREF_SHOW_REFERENCE_EDITTEXT:
                mShowReferenceEditText = mPreferenceReader.readBoolean(prefKey);
                break;
            default:
                return false;
        }
        return true;
    }

    private boolean loadTestGroupSetting(PreferenceKey prefKey) {
        switch (prefKey.getStem()) {
            case PREF_TEST_FIELD_IDS_PREFIX:
                // internal state is updated while these are modified since they aren't managed by a
                // simple Preference, so we don't need to do anything when these change
                break;
            case PREF_TEST_GROUP_NAME_PREFIX:
                getGroupById(prefKey.getId()).mName = mPreferenceReader.readString(prefKey);
                break;
            default:
                return false;
        }
        return true;
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
            editor.remove(PreferenceKey.createGroupKey(prefKeyPrefix, groupId).toString());
        }
    }

    private static void removeTestFieldPrefs(Editor editor, int idToRemove) {
        for (String prefKeyPrefix : TEST_FIELD_PREF_KEY_PREFIXES) {
            editor.remove(PreferenceKey.createFieldKey(prefKeyPrefix, idToRemove).toString());
        }
        for (String prefKeyPrefix : DEFAULTABLE_TEST_FIELD_PREF_KEY_PREFIXES) {
            editor.remove(PreferenceKey.createFieldKey(prefKeyPrefix, idToRemove).toString());
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
        if (theme != null) {
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

    public static String getGroupDisplayName(final Context context, final int groupIndex) {
        final String groupName = getTestFieldGroupName(groupIndex);
        if (groupName == null) {
            return context.getString(R.string.test_group_default_name, (groupIndex + 1));
        }
        return groupName;
    }

    public static CharSequence getFieldDisplayName(final Context context, final int groupIndex,
                                                   final int fieldIndex) {
        TestFieldSettings fieldSettings = getTestFieldSettings(groupIndex, fieldIndex);

        CharSequence labelText = fieldSettings.getLabelText();
        if (!TextUtils.isEmpty(labelText)) {
            return labelText;
        }

        CharSequence defaultText = fieldSettings.getDefaultText();
        if (!TextUtils.isEmpty(defaultText)) {
            return defaultText;
        }

        CharSequence hintText = fieldSettings.getHintText();
        if (!TextUtils.isEmpty(hintText)) {
            return hintText;
        }

        return context.getString(R.string.test_field_default_name, (fieldIndex + 1));
    }

    public static TestFieldSettings getTestFieldSettings(int groupIndex, int fieldIndex) {
        return new TestFieldSettings(groupIndex, fieldIndex);
    }

    /* package */ static abstract class TestFieldCustomEditorSettings implements EditorSettings {

        /* package */ abstract TestField getField();

        private AppLevelFieldDefaults getTestFieldOrBase(Predicate<TestField> override) {
            TestField testField = getField();
            if (!override.test(testField)) {
                return getInstance().mTestFieldDefaults;
            }
            return testField;
        }

        private AppLevelFieldDefaults getTestFieldOrBaseForTextInputModification() {
            return getTestFieldOrBase(testField -> testField.mOverrideTextInputModification);
        }

        private AppLevelFieldDefaults getTestFieldOrBaseForTextReturn() {
            return getTestFieldOrBase(testField -> testField.mOverrideTextReturn);
        }

        private AppLevelFieldDefaults getTestFieldOrBaseForTextComposition() {
            return getTestFieldOrBase(testField -> testField.mOverrideTextComposition);
        }

        private AppLevelFieldDefaults getTestFieldOrBaseForTargetVersion() {
            return getTestFieldOrBase(testField -> testField.mOverrideTargetVersion);
        }

        private AppLevelFieldDefaults getTestFieldOrBaseForSystemBehavior() {
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
        public boolean shouldSkipPerformHandwritingGesture() {
            return getTestFieldOrBaseForTargetVersion().mSkipPerformHandwritingGesture;
        }

        @Override
        public boolean shouldSkipPreviewHandwritingGesture() {
            return getTestFieldOrBaseForTargetVersion().mSkipPreviewHandwritingGesture;
        }

        @Override
        public boolean shouldSkipReplaceText() {
            return getTestFieldOrBaseForTargetVersion().mSkipReplaceText;
        }

        @Override
        public boolean shouldSkipRequestTextBoundsInfo() {
            return getTestFieldOrBaseForTargetVersion().mSkipRequestTextBoundsInfo;
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

        @Override
        public int getRequestTextBoundsInfoDelay() {
            return getTestFieldOrBaseForSystemBehavior().mRequestTextBoundsInfoDelay;
        }
    }

    public static class TestFieldSettings extends TestFieldCustomEditorSettings {
        //TODO: (EW) would it be better to use the field ID instead of the index?
        private final int mGroupIndex;
        private final int mFieldIndex;

        private TestFieldSettings(int groupIndex, int fieldIndex) {
            mGroupIndex = groupIndex;
            mFieldIndex = fieldIndex;
        }

        /* package */ TestField getField() {
            return Settings.getField(mGroupIndex, mFieldIndex);
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
