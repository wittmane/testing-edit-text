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

import static com.wittmane.testingedittext.settings.PreferenceKeys.*;

import android.content.Context;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.aosp.internal.util.ArrayUtils;
import com.wittmane.testingedittext.settings.preferences.CodepointRangeDialogPreference;
import com.wittmane.testingedittext.settings.preferences.LocaleEntryListPreference;
import com.wittmane.testingedittext.settings.preferences.TextListPreference;
import com.wittmane.testingedittext.settings.preferences.TextTranslateListPreference;
import com.wittmane.testingedittext.util.IterableUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public abstract class JsonManager {
    private static final String TAG = JsonManager.class.getSimpleName();

    private static final boolean EXPORT_UNSET_PREFS = false;
    private static final boolean EXPORT_DEFAULT_PREFS_VALUES = false;
    private static final boolean IMPORT_DEFAULT_PREFS_VALUES = false;

    private static final String FIELD_DEFAULTS_JSON_PROP = "fieldDefaults";
    private static final String GROUPS_JSON_PROP = "groups";
    private static final String FIELDS_JSON_PROP = "fields";
    private static final String OTHER_SETTINGS_JSON_PROP = "other";
    private static final String TEXT_LIST_ESCAPE_CHARS_JSON_PROP = "escapeChars";
    private static final String TEXT_LIST_DATA_ARRAY_JSON_PROP = "dataArray";
    private static final String TRANSLATE_TEXT_ORIGINAL_JSON_PROP = "original";
    private static final String TRANSLATE_TEXT_TRANSLATION_JSON_PROP = "translation";

    public static String getJson(boolean exportFieldDefaults, List<GroupInfo> groupInfoList,
                                 boolean embedFieldDefaults, boolean exportOtherSettings) {
        SharedPreferenceManager prefs = Settings.getInstance().getPrefManager();
        PreferenceReader preferenceReader = Settings.getInstance().getPreferenceReader();
        JSONObject jsonObject = new JSONObject();
        try {
            if (groupInfoList != null && preferenceReader.contains(PREF_TEST_GROUP_IDS)) {
                int[] groupIds = preferenceReader.readIntArray(PREF_TEST_GROUP_IDS);

                if (IterableUtils.any(groupInfoList, groupInfo -> groupInfo.mInclude)
                        || !IterableUtils.any(groupInfoList,
                                groupInfo -> IterableUtils.any(groupInfo.mFields,
                                        fieldInfo -> fieldInfo.mInclude))) {
                    // either at least one group was flagged to include or there are no fields and
                    // no groups to include
                    JSONArray groupsJsonArray = new JSONArray();
                    for (int groupIndex = 0; groupIndex < groupIds.length; groupIndex++) {
                        int groupId = groupIds[groupIndex];
                        GroupInfo groupInfo = groupInfoList.get(groupIndex);
                        if (groupInfoList.get(groupIndex).mInclude) {
                            groupsJsonArray.put(
                                    getGroupJson(groupId, groupInfo, embedFieldDefaults,
                                            preferenceReader));
                        }
                    }
                    jsonObject.put(GROUPS_JSON_PROP, groupsJsonArray);
                } else {
                    // there are no groups flagged to include, so skip the groups themselves and
                    // just collect the fields that are flagged to include from any group
                    JSONArray looseFieldsJsonArray = new JSONArray();
                    for (int groupIndex = 0; groupIndex < groupIds.length; groupIndex++) {
                        int groupId = groupIds[groupIndex];
                        GroupInfo groupInfo = groupInfoList.get(groupIndex);
                        if (preferenceReader.contains(
                                PREF_TEST_FIELD_IDS_PREFIX + GROUP_INFIX + groupId)) {
                            addGroupFieldsJson(looseFieldsJsonArray, groupId, groupInfo,
                                    embedFieldDefaults, preferenceReader);
                        }
                    }
                    jsonObject.put(FIELDS_JSON_PROP, looseFieldsJsonArray);
                }
            }

            //TODO: (EW) consider moving this up to match the settings screen and dialog
            if (exportFieldDefaults) {
                JSONObject fieldDefaultsJsonObject = new JSONObject();
                for (String defaultsPrefKeyPrefix : DEFAULTABLE_TEST_FIELD_PREF_KEY_PREFIXES) {
                    String defaultsPrefKey = defaultsPrefKeyPrefix + BASE_SUFFIX;
                    addPrefData(fieldDefaultsJsonObject, defaultsPrefKeyPrefix, defaultsPrefKey,
                            prefs);
                }
                jsonObject.put(FIELD_DEFAULTS_JSON_PROP, fieldDefaultsJsonObject);
            }

            if (exportOtherSettings) {
                JSONObject otherSettingsJsonObject = new JSONObject();
                for (String miscPrefKey : MISC_PREF_KEYS) {
                    addPrefData(otherSettingsJsonObject, miscPrefKey, miscPrefKey, prefs);
                }
                jsonObject.put(OTHER_SETTINGS_JSON_PROP, otherSettingsJsonObject);
            }
        } catch (JSONException | IllegalArgumentException e) {
            Log.e(TAG, "Failed to build settings JSON: " + e.getMessage());
            return null;
        }
        return jsonObject.toString();
    }

    private static JSONObject getGroupJson(int groupId, GroupInfo groupInfo,
                                           boolean embedFieldDefaults,
                                           PreferenceReader preferenceReader)
            throws JSONException {
        JSONObject groupJsonObject = new JSONObject();

        for (String groupPrefKeyPrefix : TEST_GROUP_PREF_KEY_PREFIXES) {
            String groupPrefKey = groupPrefKeyPrefix + GROUP_INFIX + groupId;

            if (groupPrefKeyPrefix.equals(PREF_TEST_FIELD_IDS_PREFIX)) {
                if (preferenceReader.contains(groupPrefKey)) {
                    JSONArray fieldsJsonArray = new JSONArray();

                    addGroupFieldsJson(fieldsJsonArray, groupId, groupInfo, embedFieldDefaults,
                            preferenceReader);

                    groupJsonObject.put(FIELDS_JSON_PROP, fieldsJsonArray);
                }
            } else {
                addPrefData(groupJsonObject, groupPrefKeyPrefix, groupPrefKey,
                        Settings.getInstance().getPrefManager());
            }
        }

        return groupJsonObject;
    }

    private static void addGroupFieldsJson(JSONArray fieldsJsonArray, int groupId,
                                           GroupInfo groupInfo, boolean embedFieldDefaults,
                                           PreferenceReader preferenceReader)
            throws JSONException {
        int[] fieldIds =
                preferenceReader.readTestGroupIntArray(groupId, PREF_TEST_FIELD_IDS_PREFIX);
        for (int fieldIndex = 0; fieldIndex < fieldIds.length; fieldIndex++) {
            int fieldId = fieldIds[fieldIndex];
            if (!groupInfo.mFields.get(fieldIndex).mInclude) {
                continue;
            }
            fieldsJsonArray.put(getFieldJson(fieldId, embedFieldDefaults, preferenceReader));
        }
    }

    private static JSONObject getFieldJson(int fieldId, boolean embedFieldDefaults,
                                           PreferenceReader preferenceReader)
            throws JSONException {
        JSONObject fieldJsonObject = new JSONObject();
        SharedPreferenceManager prefs = Settings.getInstance().getPrefManager();

        for (String fieldPrefKeyPrefix : TEST_FIELD_PREF_KEY_PREFIXES) {
            String fieldPrefKey = fieldPrefKeyPrefix + FIELD_INFIX + fieldId;

            if (DEFAULT_OVERRIDE_PREF_PREFIX_MAP.containsKey(fieldPrefKeyPrefix)) {
                String suffix;
                // embed defaults if requested and the field doesn't already override them,
                // otherwise just load the override values
                if (embedFieldDefaults
                        && !preferenceReader.readTestFieldBoolean(fieldId, fieldPrefKeyPrefix)) {
                    suffix = BASE_SUFFIX;
                    String jsonPropName = prefKeyPrefixToJsonName(fieldPrefKeyPrefix);
                    fieldJsonObject.put(jsonPropName, true);
                } else {
                    suffix = FIELD_INFIX + fieldId;
                    addPrefData(fieldJsonObject, fieldPrefKeyPrefix, fieldPrefKey, prefs);
                }
                String[] fieldDefaultsPrefKeys =
                        DEFAULT_OVERRIDE_PREF_PREFIX_MAP.get(fieldPrefKeyPrefix);
                for (String fieldDefaultPrefKeyPrefix : fieldDefaultsPrefKeys) {
                    String fieldDefaultPrefKey = fieldDefaultPrefKeyPrefix + suffix;
                    addPrefData(fieldJsonObject, fieldDefaultPrefKeyPrefix, fieldDefaultPrefKey,
                            prefs);
                }
            } else {
                addPrefData(fieldJsonObject, fieldPrefKeyPrefix, fieldPrefKey, prefs);
            }
        }

        return fieldJsonObject;
    }

    //TODO: (EW) probably should break this into multiple helper methods
    private static void addPrefData(JSONObject jsonObject, String prefKeyOrPrefix, String prefKey,
                                    SharedPreferenceManager prefs) throws JSONException {
        //TODO: (EW) consider not using SharedPreferenceManager, but only using PreferenceReader
        // (find a good way to avoid loading the default value twice)
        if (!EXPORT_UNSET_PREFS && !prefs.contains(prefKey)) {
            return;
        }
        String jsonPropName = prefKeyPrefixToJsonName(prefKeyOrPrefix);
        int dataType = PreferenceReader.prefDataType(prefKeyOrPrefix);
        switch (dataType) {
            case PreferenceReader.TYPE_BOOLEAN:
                boolean defaultBoolean = PreferenceReader.getPrefDefaultBoolean(prefKeyOrPrefix);
                boolean prefValueBoolean = prefs.getBoolean(prefKey, defaultBoolean);
                if (EXPORT_DEFAULT_PREFS_VALUES || prefValueBoolean != defaultBoolean) {
                    jsonObject.put(jsonPropName, prefValueBoolean);
                }
                break;
            case PreferenceReader.TYPE_INT:
                int defaultInt = PreferenceReader.getPrefDefaultInt(prefKeyOrPrefix);
                int prefValueInt = prefs.getInt(prefKey, defaultInt);
                if (EXPORT_DEFAULT_PREFS_VALUES || prefValueInt != defaultInt) {
                    jsonObject.put(jsonPropName, prefValueInt);
                }
                break;
            case PreferenceReader.TYPE_LONG:
                long defaultLong = PreferenceReader.getPrefDefaultLong(prefKeyOrPrefix);
                long prefValueLong = prefs.getLong(prefKey, defaultLong);
                if (EXPORT_DEFAULT_PREFS_VALUES || prefValueLong != defaultLong) {
                    jsonObject.put(jsonPropName, prefValueLong);
                }
                break;
            case PreferenceReader.TYPE_FLOAT:
                float defaultFloat = PreferenceReader.getPrefDefaultFloat(prefKeyOrPrefix);
                float prefValueFloat = prefs.getFloat(prefKey, defaultFloat);
                if (EXPORT_DEFAULT_PREFS_VALUES || prefValueFloat != defaultFloat) {
                    jsonObject.put(jsonPropName, prefValueFloat);
                }
                break;
            case PreferenceReader.TYPE_STRING:
                String defaultString = PreferenceReader.getPrefDefaultString(prefKeyOrPrefix);
                String prefValueString = prefs.getString(prefKey, defaultString);
                if (EXPORT_DEFAULT_PREFS_VALUES
                        || !TextUtils.equals(prefValueString, defaultString)) {
                    addObject(jsonObject, jsonPropName, prefValueString);
                }
                break;
            case PreferenceReader.TYPE_SPANNED:
                Spanned defaultSpanned = PreferenceReader.getPrefDefaultSpanned(prefKeyOrPrefix);
                Spanned prefValueSpanned = prefs.getSpanned(prefKey, defaultSpanned);
                if (EXPORT_DEFAULT_PREFS_VALUES
                        || !definitelyEqual(prefValueSpanned, defaultSpanned)) {
                    // get the data that SharedPreferenceManager uses to save spanned objects
                    addArray(jsonObject, jsonPropName, prefValueSpanned == null
                            ? null
                            : SharedPreferenceManager.getSpannedInfo(prefValueSpanned));
                }
                break;
            case PreferenceReader.TYPE_CHAR_SEQUENCE:
                CharSequence defaultCharSequence =
                        PreferenceReader.getPrefDefaultCharSequence(prefKeyOrPrefix);
                CharSequence prefValueCharSequence =
                        prefs.getCharSequence(prefKey, defaultCharSequence);
                if (EXPORT_DEFAULT_PREFS_VALUES
                        || !definitelyEqual(prefValueCharSequence, defaultCharSequence)) {
                    if (prefValueCharSequence instanceof Spanned) {
                        // get the data that SharedPreferenceManager uses to save spanned objects
                        //TODO: (EW) possibly should embed some indication of what data this holds
                        // so that if other supported CharSequence type are supported in the future
                        // or we find a better way to export the data, we can maintain compatibility
                        // between varying versions between the exporting and importing app.
                        addArray(jsonObject, jsonPropName,
                                SharedPreferenceManager.getSpannedInfo(
                                        (Spanned) prefValueCharSequence));
                    } else if (prefValueCharSequence == null
                            || prefValueCharSequence instanceof String) {
                        addObject(jsonObject, jsonPropName, prefValueCharSequence);
                    } else {
                        jsonObject.put(jsonPropName, prefValueCharSequence.toString());
                    }
                }
                break;
            case PreferenceReader.TYPE_INT_ARRAY:
                int[] defaultIntArray = PreferenceReader.getPrefDefaultIntArray(prefKeyOrPrefix);
                int[] prefValueIntArray = prefs.getIntArray(prefKey, defaultIntArray);
                if (EXPORT_DEFAULT_PREFS_VALUES
                        || !Arrays.equals(prefValueIntArray, defaultIntArray)) {
                    addArray(jsonObject, jsonPropName, prefValueIntArray);
                }
                break;
            case PreferenceReader.TYPE_STRING_ARRAY:
                String[] defaultStringArray =
                        PreferenceReader.getPrefDefaultStringArray(prefKeyOrPrefix);
                String[] prefValueStringArray = prefs.getStringArray(prefKey, defaultStringArray);
                if (EXPORT_DEFAULT_PREFS_VALUES
                        || !Arrays.equals(prefValueStringArray, defaultStringArray)) {
                    addArray(jsonObject, jsonPropName, prefValueStringArray);
                }
                break;
            case PreferenceReader.TYPE_INT_RANGE:
                //TODO: (EW) make more generic. the only use case for the int range currently is the
                // codepoint range preference. maybe just convert this preference to use an int
                // array and just have extra validation on the length when reading the data.
                CodepointRangeDialogPreference.DataManager codepointRangeDialogDataManager =
                        new CodepointRangeDialogPreference.DataManager(prefs, prefKey);
                IntRange defaultIntRange = codepointRangeDialogDataManager.readDefaultValue();
                IntRange prefValueIntRange = codepointRangeDialogDataManager.readValue();
                if (EXPORT_DEFAULT_PREFS_VALUES
                        || !Objects.equals(prefValueIntRange, defaultIntRange)) {
                    addArray(jsonObject, jsonPropName, prefValueIntRange == null
                            ? null
                            : new int[] {
                                    prefValueIntRange.getStart(),
                                    prefValueIntRange.getEnd()
                            });
                }
                break;
            case PreferenceReader.TYPE_LOCALE_ARRAY:
                //TODO: (EW) possibly could be more generic (or at least decoupled from the specific
                // preference)
                LocaleEntryListPreference.DataManager localeEntryListDataManager =
                        new LocaleEntryListPreference.DataManager(prefs, prefKey);
                Locale[] defaultLocaleArray = localeEntryListDataManager.readDefaultValue();
                Locale[] prefValueLocaleArray = localeEntryListDataManager.readValue();
                if (EXPORT_DEFAULT_PREFS_VALUES
                        || !Arrays.equals(prefValueLocaleArray, defaultLocaleArray)) {
                    String[] localeStrings = new String[prefValueLocaleArray.length];
                    for (int i = 0; i < prefValueLocaleArray.length; i++) {
                        localeStrings[i] = LocaleEntryListPreference.getLocaleString(
                                prefValueLocaleArray[i]);
                    }
                    addArray(jsonObject, jsonPropName, localeStrings);
                }
                break;
            case PreferenceReader.TYPE_TEXT_LIST_STRING:
                //TODO: (EW) possibly could be more generic (or at least decoupled from the specific
                // preference)
                TextListPreference.DataManager textListDataManager =
                        new TextListPreference.DataManager(prefs,prefKey);
                TextList<String> defaultTextListString = textListDataManager.readDefaultValue();
                TextList<String> prefValueTextListString = textListDataManager.readValue();
                if (EXPORT_DEFAULT_PREFS_VALUES
                        || !Objects.equals(prefValueTextListString, defaultTextListString)) {
                    JSONObject textListStringJsonObject = new JSONObject();
                    textListStringJsonObject.put(TEXT_LIST_ESCAPE_CHARS_JSON_PROP,
                            prefValueTextListString.escapeChars());
                    addArray(textListStringJsonObject, TEXT_LIST_DATA_ARRAY_JSON_PROP,
                            prefValueTextListString.getDataArray());
                    jsonObject.put(jsonPropName, textListStringJsonObject);
                }
                break;
            case PreferenceReader.TYPE_TEXT_LIST_TRANSLATE_TEXT:
                //TODO: (EW) possibly could be more generic (or at least decoupled from the specific
                // preference)
                TextTranslateListPreference.DataManager textTranslateListDataManager =
                        new TextTranslateListPreference.DataManager(prefs, prefKey);
                TextList<TranslateText> defaultTextListTranslateText =
                        textTranslateListDataManager.readDefaultValue();
                TextList<TranslateText> prefValueTextListTranslateText =
                        textTranslateListDataManager.readValue();
                if (EXPORT_DEFAULT_PREFS_VALUES
                        || !Objects.equals(prefValueTextListTranslateText,
                                defaultTextListTranslateText)) {
                    JSONObject translateTextJsonObject = new JSONObject();
                    translateTextJsonObject.put(TEXT_LIST_ESCAPE_CHARS_JSON_PROP,
                            prefValueTextListTranslateText.escapeChars());
                    JSONObject[] translateTextArray =
                            new JSONObject[prefValueTextListTranslateText.getDataArray().length];
                    for (int i = 0; i < translateTextArray.length; i++) {
                        translateTextArray[i] = new JSONObject();
                        translateTextArray[i].put(TRANSLATE_TEXT_ORIGINAL_JSON_PROP,
                                prefValueTextListTranslateText.getDataArray()[i].getOriginal());
                        translateTextArray[i].put(TRANSLATE_TEXT_TRANSLATION_JSON_PROP,
                                prefValueTextListTranslateText.getDataArray()[i].getTranslation());
                    }
                    addArray(translateTextJsonObject, TEXT_LIST_DATA_ARRAY_JSON_PROP,
                            translateTextArray);
                    jsonObject.put(jsonPropName, translateTextJsonObject);
                }
                break;
            case PreferenceReader.TYPE_UNKNOWN:
            default:
                //TODO: (EW) probably handle gracefully, but hard crash for now to catch issues
                throw new RuntimeException("Unknown data type for " + prefKeyOrPrefix);
        }
    }

    private static <T> void addArray(JSONObject jsonObject, String jsonPropName, T[] data)
            throws JSONException {
        if (data == null) {
            jsonObject.put(jsonPropName, JSONObject.NULL);
        } else {
            JSONArray jsonArray = new JSONArray();
            for (T value : data) {
                jsonArray.put(value);
            }
            jsonObject.put(jsonPropName, jsonArray);
        }
    }

    private static void addArray(JSONObject jsonObject, String jsonPropName, int[] data)
            throws JSONException {
        if (data == null) {
            jsonObject.put(jsonPropName, JSONObject.NULL);
        } else {
            JSONArray jsonArray = new JSONArray();
            for (int value : data) {
                jsonArray.put(value);
            }
            jsonObject.put(jsonPropName, jsonArray);
        }
    }

    private static void addObject(JSONObject jsonObject, String jsonPropName, Object data)
            throws JSONException {
        if (data == null) {
            jsonObject.put(jsonPropName, JSONObject.NULL);
        } else {
            jsonObject.put(jsonPropName, data);
        }
    }

    private static String prefKeyPrefixToJsonName(String prefKeyOrKeyPrefix) {
        int start = prefKeyOrKeyPrefix.startsWith(PREF_KEY_PREFIX) ? PREF_KEY_PREFIX.length() : 0;
        String coreName = prefKeyOrKeyPrefix.substring(start);
        return snakeCaseToCamelCase(coreName);
    }

    private static String snakeCaseToCamelCase(String name) {
        if (!name.matches("[a-z](?:[a-z]|\\d)*(?:_(?:[a-z]|\\d)+)*")) {
            throw new IllegalArgumentException(name + " isn't valid snake case");
        }
        int nextToAdd = 0;
        int nextUnderscore;
        StringBuilder sb = new StringBuilder();
        while ((nextUnderscore = name.indexOf('_', nextToAdd)) >= 0) {
            // add any text before the underscore that hasn't already been added/adjusted yet
            sb.append(name.substring(nextToAdd, nextUnderscore));
            nextToAdd = nextUnderscore + 1;

            if (nextUnderscore + 1 < name.length()) {
                char c = name.charAt(nextUnderscore + 1);
                if (c >= 'a' && c <= 'z') {
                    // convert the character after the underscore to uppercase
                    sb.append((char) (c - 'a' + 'A'));
                    nextToAdd++;
                } else if (c >= '0' && c <= '9') {
                    // add the number without adjustment
                    sb.append(c);
                    nextToAdd++;
                }
            }
        }
        if (nextToAdd < name.length()) {
            sb.append(name.substring(nextToAdd));
        }
        return sb.toString();
    }

    // modified from TextUtils#equals since Spanned objects generally don't compare well (see
    // https://stackoverflow.com/a/46403431)
    private static boolean definitelyEqual(CharSequence a, CharSequence b) {
        if (a == b) return true;
        if (a != null && b != null && a.length() == b.length()) {
            if (a instanceof String && b instanceof String) {
                return a.equals(b);
            } else {
                // these may be equal, but we may not be able to be completely sure
                return false;
            }
        }
        return false;
    }

    public static class ImportFileInfo {
        private String mError;
        private final List<String> mWarnings = new ArrayList<>();
        private final List<String> mUnexpectedProps = new ArrayList<>();
        private JSONObject mJsonObject;
        private List<GroupInfo> mGroups;
        private boolean mIsFieldDefaultsIncluded;
        private boolean mIsOtherSettingsIncluded;

        public String getError() {
            return mError;
        }

        public List<String> getWarnings() {
            return mWarnings;
        }

        public List<String> getUnexpectedProps() {
            return mUnexpectedProps;
        }

        public JSONObject getJsonObject() {
            return mJsonObject;
        }

        public List<GroupInfo> getGroups() {
            return mGroups;
        }

        public boolean isFieldDefaultsIncluded() {
            return mIsFieldDefaultsIncluded;
        }

        public boolean isOtherSettingsIncluded() {
            return mIsOtherSettingsIncluded;
        }
    }
    //TODO: (EW) don't make properties public
    public static class GroupInfo {
        public List<FieldInfo> mFields;
        public String mName;
        public boolean mInclude;
        public boolean mIsAdHoc;
    }
    public static class FieldInfo {
        public String mName;
        public boolean mInclude;
    }

    private static Set<String> getProps(JSONObject jsonObject) {
        Set<String> props = new HashSet<>();
        Iterator<String> keys = jsonObject.keys();
        while(keys.hasNext()) {
            String key = keys.next();
            props.add(key);
        }
        return props;
    }

    private static JSONObject getJsonObject(JSONObject jsonObject, String propName)
            throws JSONException {
        return jsonObject.has(propName)
                ? jsonObject.getJSONObject(propName)
                : null;
    }

    public static ImportFileInfo validateJson(String rawJson, Context context) {
        ImportFileInfo info = new ImportFileInfo();

        try {
            info.mJsonObject = new JSONObject(rawJson);
            Set<String> props = getProps(info.mJsonObject);

            //TODO: (EW) see if there is a good way to reduce duplicate code with tracking the
            // unexpected properties
            if (props.contains(GROUPS_JSON_PROP)) {
                props.remove(GROUPS_JSON_PROP);

                List<GroupInfo> groups = new ArrayList<>();
                JSONArray groupsJsonArray = info.mJsonObject.getJSONArray(GROUPS_JSON_PROP);
                for (int i = 0; i < groupsJsonArray.length(); i++) {
                    GroupInfo groupInfo = new GroupInfo();
                    JSONObject groupJsonObject = groupsJsonArray.getJSONObject(i);
                    if (!validateGroupJson(groupJsonObject, info, i,
                            GROUPS_JSON_PROP + "[" + i + "]",
                            context, groupInfo)) {
                        return info;
                    }
                    groups.add(groupInfo);
                }
                info.mGroups = groups;
            } else if (props.contains(FIELDS_JSON_PROP)) {
                props.remove(FIELDS_JSON_PROP);

                List<GroupInfo> groups = new ArrayList<>();

                GroupInfo groupInfo = new GroupInfo();

                // build the ad-hoc group to load
                JSONObject groupJsonObject = new JSONObject();
                JSONArray looseFieldsJsonArray = info.mJsonObject.getJSONArray(FIELDS_JSON_PROP);
                groupJsonObject.put(FIELDS_JSON_PROP, looseFieldsJsonArray);

                if (!validateGroupJson(groupJsonObject, info, 0, null,
                        context, groupInfo)) {
                    return info;
                }
                groupInfo.mIsAdHoc = true;
                groupInfo.mName = context.getText(R.string.ad_hoc_import_group_name).toString();
                groups.add(groupInfo);

                info.mGroups = groups;
            }

            if (props.contains(FIELD_DEFAULTS_JSON_PROP)) {
                props.remove(FIELD_DEFAULTS_JSON_PROP);

                JSONObject fieldDefaultsJsonObject =
                        info.mJsonObject.getJSONObject(FIELD_DEFAULTS_JSON_PROP);
                if (!validateGroupedSettingsJson(DEFAULTABLE_TEST_FIELD_PREF_KEY_PREFIXES,
                        fieldDefaultsJsonObject, info, FIELD_DEFAULTS_JSON_PROP, context)) {
                    return info;
                }
                info.mIsFieldDefaultsIncluded = true;
            }

            if (props.contains(OTHER_SETTINGS_JSON_PROP)) {
                props.remove(OTHER_SETTINGS_JSON_PROP);

                JSONObject otherSettingsJsonObject =
                        info.mJsonObject.getJSONObject(OTHER_SETTINGS_JSON_PROP);
                if (!validateGroupedSettingsJson(MISC_PREF_KEYS,
                        otherSettingsJsonObject, info, OTHER_SETTINGS_JSON_PROP, context)) {
                    return info;
                }
                info.mIsOtherSettingsIncluded = true;
            }

            info.mUnexpectedProps.addAll(props);
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
            info.mError = context.getString(R.string.failed_to_parse_import_file);
            return info;
        }

        return info;
    }

    private static boolean validateGroupJson(JSONObject groupJsonObject, ImportFileInfo info,
                                             int groupIndex, String path, Context context,
                                             GroupInfo groupInfo)
            throws JSONException {
        Map<String, String> namesMap = new HashMap<>();
        List<FieldInfo> fields = new ArrayList<>();

        Set<String> props = getProps(groupJsonObject);

        if (props.contains(FIELDS_JSON_PROP)) {
            props.remove(FIELDS_JSON_PROP);

            JSONArray fieldsJsonArray = groupJsonObject.getJSONArray(FIELDS_JSON_PROP);
            for (int i = 0; i < fieldsJsonArray.length(); i++) {
                FieldInfo fieldInfo = new FieldInfo();
                JSONObject fieldJsonObject = fieldsJsonArray.getJSONObject(i);
                if (!validateFieldJson(fieldJsonObject, info, groupIndex, i,
                        (path != null ? path + "." : "") + FIELDS_JSON_PROP + "[" + i + "]",
                        context, fieldInfo)) {
                    return false;
                }
                fields.add(fieldInfo);
            }
        }

        for (String testGroupPrefKeyPrefix : TEST_GROUP_PREF_KEY_PREFIXES) {
            String jsonProp = prefKeyPrefixToJsonName(testGroupPrefKeyPrefix);
            if (props.contains(jsonProp)) {
                props.remove(jsonProp);

                if (!testGroupPrefKeyPrefix.equals(PREF_TEST_FIELD_IDS_PREFIX)) {
                    if (!validatePrefData(groupJsonObject, jsonProp, path, testGroupPrefKeyPrefix,
                            info, context, namesMap)) {
                        return false;
                    }
                }
            }
        }

        for (String prop : props) {
            info.mUnexpectedProps.add(path + "." + prop);
        }

        groupInfo.mFields = fields;
        String name = getName(namesMap, new String[] { PREF_TEST_GROUP_NAME_PREFIX });
        groupInfo.mName = name != null
                ? name
                : context.getString(R.string.test_group_default_name, groupIndex + 1);
        return true;
    }

    private static boolean validateFieldJson(JSONObject fieldJsonObject, ImportFileInfo info,
                                             int groupIndex, int fieldIndex, String path,
                                             Context context, FieldInfo fieldInfo) {
        Map<String, String> namesMap = new HashMap<>();

        Set<String> props = getProps(fieldJsonObject);

        for (String testFieldPrefKeyPrefix : TEST_FIELD_PREF_KEY_PREFIXES) {
            String jsonProp = prefKeyPrefixToJsonName(testFieldPrefKeyPrefix);
            if (props.contains(jsonProp)) {
                props.remove(jsonProp);

                if (!validatePrefData(fieldJsonObject, jsonProp, path, testFieldPrefKeyPrefix, info,
                        context, namesMap)) {
                    return false;
                }
            }
        }

        for (String defaultableTestFieldPrefKeyPrefix : DEFAULTABLE_TEST_FIELD_PREF_KEY_PREFIXES) {
            String jsonProp = prefKeyPrefixToJsonName(defaultableTestFieldPrefKeyPrefix);
            if (props.contains(jsonProp)) {
                props.remove(jsonProp);

                if (!validatePrefData(fieldJsonObject, jsonProp, path,
                        defaultableTestFieldPrefKeyPrefix, info, context, null)) {
                    return false;
                }
            }
        }

        for (String prop : props) {
            info.mUnexpectedProps.add(path + "." + prop);
        }

        String name = getName(namesMap, new String[] {
                PREF_IME_LABEL_TEXT_PREFIX,
                PREF_IME_DEFAULT_TEXT_PREFIX,
                PREF_IME_HINT_TEXT_PREFIX
        });
        fieldInfo.mName = name != null
                ? name
                : context.getString(R.string.test_field_default_name, fieldIndex + 1);
        return true;
    }

    //TODO: (EW) rename - it sounds too much like it's referring to field groups. maybe use
    // something different, like "cluster"
    private static boolean validateGroupedSettingsJson(String[] keyOrKeyPrefixArray,
                                                       JSONObject jsonObject,
                                                       ImportFileInfo info, String path,
                                                       Context context) {
        Set<String> props = getProps(jsonObject);

        for (String prefKeyPrefix : keyOrKeyPrefixArray) {
            String jsonProp = prefKeyPrefixToJsonName(prefKeyPrefix);
            if (props.contains(jsonProp)) {
                props.remove(jsonProp);

                if (!validatePrefData(jsonObject, jsonProp, path, prefKeyPrefix, info, context,
                        null)) {
                    return false;
                }
            }
        }

        for (String prop : props) {
            info.mUnexpectedProps.add(path + "." + prop);
        }

        return true;
    }

    private static String getName(Map<String, String> namesMap, String[] keys) {
        for (String key : keys) {
            if (!namesMap.containsKey(key)) {
                continue;
            }
            String name = namesMap.get(key);
            if (!TextUtils.isEmpty(name)) {
                return name;
            }
        }
        return null;
    }

    private static boolean validatePrefData(JSONObject jsonObject, String jsonPropName, String path,
                                            String prefKeyOrPrefix, ImportFileInfo info,
                                            Context context, Map<String, String> namesMap) {
        // just need to try getting the data for basic types to ensure the right data type is set
        return loadOrValidatePrefData(jsonObject, jsonPropName, path, prefKeyOrPrefix, info,
                context, null, namesMap);
    }

    private static boolean loadPrefData(JSONObject jsonObject, String jsonPropName, String path,
                                        String prefKeyOrPrefix, @NonNull Context context,
                                        @NonNull String prefKey) {
        return loadOrValidatePrefData(jsonObject, jsonPropName, path, prefKeyOrPrefix, null,
                context, prefKey, null);

    }

    //TODO: (EW) probably should break this into multiple helper methods
    private static boolean loadOrValidatePrefData(JSONObject jsonObject, String jsonPropName,
                                                  String path, String prefKeyOrPrefix,
                                                  @Nullable ImportFileInfo info,
                                                  @NonNull Context context,
                                                  @Nullable String prefKey,
                                                  @Nullable Map<String, String> namesMap) {
        String fullPath = (path == null ? "" : (path + ".")) + jsonPropName;
        int dataType = PreferenceReader.prefDataType(prefKeyOrPrefix);
        SharedPreferenceManager prefs = Settings.getInstance().getPrefManager();
        try {
            switch (dataType) {
                case PreferenceReader.TYPE_BOOLEAN:
                    boolean booleanData = jsonObject.getBoolean(jsonPropName);
                    if (prefKey != null) {
                        boolean defaultBoolean =
                                PreferenceReader.getPrefDefaultBoolean(prefKeyOrPrefix);
                        if (IMPORT_DEFAULT_PREFS_VALUES || booleanData != defaultBoolean) {
                            prefs.setBoolean(prefKey, booleanData);
                        }
                    }
                    break;
                case PreferenceReader.TYPE_INT:
                    int intData = jsonObject.getInt(jsonPropName);
                    int minValue;
                    int maxValue;
                    int stepValue;
                    switch (prefKeyOrPrefix) {
                        case PREF_MAX_LENGTH_PREFIX:
                            // non-negative
                            minValue = 0;
                            maxValue = Integer.MAX_VALUE;
                            stepValue = 1;
                            break;
                        case PREF_SHIFT_CODEPOINT_PREFIX:
                            minValue = context.getResources().getInteger(
                                    R.integer.config_shift_codepoint_min);
                            maxValue = context.getResources().getInteger(
                                    R.integer.config_shift_codepoint_max);
                            stepValue = context.getResources().getInteger(
                                    R.integer.config_shift_codepoint_step);
                            break;
                        case PREF_LIMIT_EXTRACT_MONITOR_TEXT_PREFIX:
                            minValue = context.getResources().getInteger(
                                    R.integer.config_extract_monitor_text_limit_min);
                            maxValue = context.getResources().getInteger(
                                    R.integer.config_extract_monitor_text_limit_max);
                            stepValue = context.getResources().getInteger(
                                    R.integer.config_extract_monitor_text_limit_step);
                            break;
                        case PREF_LIMIT_RETURNED_TEXT_PREFIX:
                            minValue = context.getResources().getInteger(
                                    R.integer.config_returned_text_limit_min);
                            maxValue = context.getResources().getInteger(
                                    R.integer.config_returned_text_limit_max);
                            stepValue = context.getResources().getInteger(
                                    R.integer.config_returned_text_limit_step);
                            break;
                        case PREF_UPDATE_DELAY_PREFIX:
                            minValue = context.getResources().getInteger(
                                    R.integer.config_update_delay_min);
                            maxValue = context.getResources().getInteger(
                                    R.integer.config_update_delay_max);
                            stepValue = context.getResources().getInteger(
                                    R.integer.config_update_delay_step);
                            break;
                        case PREF_FINISHCOMPOSINGTEXT_DELAY_PREFIX:
                        case PREF_GETSURROUNDINGTEXT_DELAY_PREFIX:
                        case PREF_GETTEXTBEFORECURSOR_DELAY_PREFIX:
                        case PREF_GETSELECTEDTEXT_DELAY_PREFIX:
                        case PREF_GETTEXTAFTERCURSOR_DELAY_PREFIX:
                        case PREF_GETCURSORCAPSMODE_DELAY_PREFIX:
                        case PREF_GETEXTRACTEDTEXT_DELAY_PREFIX:
                            minValue = context.getResources().getInteger(
                                    R.integer.config_inputconnection_method_delay_min);
                            maxValue = context.getResources().getInteger(
                                    R.integer.config_inputconnection_method_delay_max);
                            stepValue = context.getResources().getInteger(
                                    R.integer.config_inputconnection_method_delay_step);
                            break;
                        default:
                            minValue = Integer.MIN_VALUE;
                            maxValue = Integer.MAX_VALUE;
                            stepValue = 1;
                    }
                    int constrainedIntData = constrain(intData, minValue, maxValue, stepValue);
                    if (constrainedIntData != intData
                            && intData != PreferenceReader.getPrefDefaultInt(prefKeyOrPrefix)) {
                        if (intData < minValue || intData > maxValue) {
                            Log.e(TAG, fullPath + " ( " + intData + ") isn't in the range "
                                    + minValue + " - " + maxValue);
                            if (info != null) {
                                info.mWarnings.add(context.getString(R.string.value_not_in_range,
                                        fullPath, intData, minValue, maxValue));
                            }
                            break;
                        } else {
                            Log.w(TAG, fullPath + " has an int ( " + intData
                                    + ") that doesn't conform to the constraints: min=" + minValue
                                    + ", max=" + maxValue + ", step=" + stepValue);
                            if (info != null) {
                                info.mWarnings.add(context.getString(R.string.invalid_step_value,
                                        fullPath));
                            }
                            // it should be relatively safe to just shift to the nearest step
                            intData = constrainedIntData;
                        }
                    }
                    if (prefKey != null) {
                        int defaultInt = PreferenceReader.getPrefDefaultInt(prefKeyOrPrefix);
                        if (IMPORT_DEFAULT_PREFS_VALUES || intData != defaultInt) {
                            prefs.setInt(prefKey, intData);
                        }
                    }
                    break;
                case PreferenceReader.TYPE_LONG:
                    long longData = jsonObject.getLong(jsonPropName);
                    if (prefKey != null) {
                        long defaultLong = PreferenceReader.getPrefDefaultLong(prefKeyOrPrefix);
                        if (IMPORT_DEFAULT_PREFS_VALUES || longData != defaultLong) {
                            prefs.setLong(prefKey, longData);
                        }
                    }
                    break;
                case PreferenceReader.TYPE_FLOAT:
                    double doubleData = jsonObject.getDouble(jsonPropName);
                    if (doubleData > Float.MAX_VALUE || doubleData < Float.MIN_VALUE) {
                        Log.e(TAG, doubleData + " isn't a valid float for " + fullPath);
                        if (info != null) {
                            info.mWarnings.add(
                                    context.getString(R.string.invalid_float_data, fullPath));
                        }
                        break;
                    }
                    if (prefKey != null) {
                        float defaultFloat = PreferenceReader.getPrefDefaultFloat(prefKeyOrPrefix);
                        float floatData = (float) doubleData;
                        if (IMPORT_DEFAULT_PREFS_VALUES || floatData != defaultFloat) {
                            prefs.setFloat(prefKey, floatData);
                        }
                    }
                    break;
                case PreferenceReader.TYPE_STRING:
                    String stringData = getString(jsonObject, jsonPropName);
                    String[] allowedStringValues;
                    switch (prefKeyOrPrefix) {
                        case PREF_INPUT_TYPE_CLASS_PREFIX:
                            allowedStringValues = context.getResources().getStringArray(
                                    R.array.type_class_values);
                            break;
                        case PREF_INPUT_TYPE_TEXT_VARIATION_PREFIX:
                            allowedStringValues = context.getResources().getStringArray(
                                    R.array.type_text_variation_values);
                            break;
                        case PREF_INPUT_TYPE_NUMBER_VARIATION_PREFIX:
                            allowedStringValues = context.getResources().getStringArray(
                                    R.array.type_number_variation_values);
                            break;
                        case PREF_INPUT_TYPE_DATETIME_VARIATION_PREFIX:
                            allowedStringValues = context.getResources().getStringArray(
                                    R.array.type_datetime_variation_values);
                            break;
                        case PREF_INPUT_TYPE_TEXT_FLAG_MULTI_LINE_PREFIX:
                            allowedStringValues = context.getResources().getStringArray(
                                    R.array.type_text_multi_line_flag_values);
                            break;
                        case PREF_INPUT_TYPE_TEXT_FLAG_CAP_PREFIX:
                            allowedStringValues = context.getResources().getStringArray(
                                    R.array.type_text_cap_flag_values);
                            break;
                        case PREF_NULL_INPUT_TYPE_COMPOSING_TEXT_BEHAVIOR_PREFIX:
                            allowedStringValues = context.getResources().getStringArray(
                                    R.array.composing_text_behavior_values);
                            break;
                        case PREF_IME_OPTIONS_ACTION_PREFIX:
                            allowedStringValues = context.getResources().getStringArray(
                                    R.array.ime_options_action_values);
                            break;
                        case PREF_THEME:
                            allowedStringValues = context.getResources().getStringArray(
                                    R.array.theme_values);
                            break;
                        default:
                            allowedStringValues = null;
                    }
                    if (allowedStringValues != null
                            && !ArrayUtils.contains(allowedStringValues, stringData)
                            && !TextUtils.equals(stringData,
                            PreferenceReader.getPrefDefaultString(prefKeyOrPrefix))) {
                        Log.e(TAG, fullPath + " has an invalid value: " + stringData);
                        if (info != null) {
                            info.mWarnings.add(context.getString(R.string.invalid_value,
                                    fullPath, stringData));
                        }
                        break;
                    }
                    if (prefKey != null) {
                        String defaultString =
                                PreferenceReader.getPrefDefaultString(prefKeyOrPrefix);
                        if (IMPORT_DEFAULT_PREFS_VALUES
                                || !TextUtils.equals(stringData, defaultString)) {
                            prefs.setString(prefKey, stringData);
                        }
                    }
                    if (namesMap != null) {
                        switch (prefKeyOrPrefix) {
                            case PREF_TEST_GROUP_NAME_PREFIX:
                                namesMap.put(prefKeyOrPrefix, stringData);
                        }
                    }
                    break;
                case PreferenceReader.TYPE_SPANNED:
                    Spanned spannedData = getSpanned(jsonObject, jsonPropName);
                    if (prefKey != null) {
                        Spanned defaultSpanned =
                                PreferenceReader.getPrefDefaultSpanned(prefKeyOrPrefix);
                        if (IMPORT_DEFAULT_PREFS_VALUES
                                || !definitelyEqual(spannedData, defaultSpanned)) {
                            prefs.setSpanned(prefKey, spannedData);
                        }
                    }
                    break;
                case PreferenceReader.TYPE_CHAR_SEQUENCE:
                    CharSequence charSequenceData;
                    if (jsonObject.isNull(jsonPropName)) {
                        charSequenceData = null;
                    } else if (jsonObject.get(jsonPropName) instanceof String) {
                        charSequenceData = jsonObject.getString(jsonPropName);
                    } else {
                        charSequenceData = getSpanned(jsonObject, jsonPropName);
                    }
                    if (prefKey != null) {
                        CharSequence defaultCharSequence =
                                PreferenceReader.getPrefDefaultCharSequence(prefKeyOrPrefix);
                        if (IMPORT_DEFAULT_PREFS_VALUES
                                || !definitelyEqual(charSequenceData, defaultCharSequence)) {
                            prefs.setCharSequence(prefKey, charSequenceData);
                        }
                    }
                    if (namesMap != null) {
                        switch (prefKeyOrPrefix) {
                            case PREF_IME_LABEL_TEXT_PREFIX:
                            case PREF_IME_DEFAULT_TEXT_PREFIX:
                            case PREF_IME_HINT_TEXT_PREFIX:
                                namesMap.put(prefKeyOrPrefix, charSequenceData == null
                                        ? null
                                        : charSequenceData.toString());
                        }
                    }
                    break;
                case PreferenceReader.TYPE_INT_ARRAY:
                    int[] intArrayData = getIntArray(jsonObject, jsonPropName);
                    if (prefKey != null) {
                        int[] defaultIntArray =
                                PreferenceReader.getPrefDefaultIntArray(prefKeyOrPrefix);
                        if (IMPORT_DEFAULT_PREFS_VALUES
                                || !Arrays.equals(intArrayData, defaultIntArray)) {
                            prefs.setIntArray(prefKey, intArrayData);
                        }
                    }
                    break;
                case PreferenceReader.TYPE_STRING_ARRAY:
                    String[] stringArrayData = getStringArray(jsonObject, jsonPropName);
                    if (prefKey != null) {
                        String[] defaultStringArray =
                                PreferenceReader.getPrefDefaultStringArray(prefKeyOrPrefix);
                        if (IMPORT_DEFAULT_PREFS_VALUES
                                || !Arrays.equals(stringArrayData, defaultStringArray)) {
                            prefs.setStringArray(prefKey, stringArrayData);
                        }
                    }
                    break;
                case PreferenceReader.TYPE_INT_RANGE:
                    int[] rangeArray = getIntArray(jsonObject, jsonPropName);
                    if (rangeArray != null && rangeArray.length != 2) {
                        Log.e(TAG, fullPath + " doesn't have exactly 2 values: "
                                + Arrays.toString(rangeArray));
                        if (info != null) {
                            info.mWarnings.add(context.getString(R.string.invalid_data, fullPath));
                        }
                        break;
                    }
                    if (prefKeyOrPrefix.equals(PREF_RESTRICT_RANGE_PREFIX)) {
                        IntRange range = rangeArray == null
                                ? null
                                : new IntRange(rangeArray[0], rangeArray[1]);
                        if (!CodepointRangeDialogPreference.isValidRange(range)) {
                            Log.e(TAG, fullPath + " contains an invalid codepoint range: "
                                    + range);
                            if (info != null) {
                                info.mWarnings.add(
                                        context.getString(R.string.invalid_data, fullPath));
                            }
                            break;
                        }
                        if (prefKey != null) {
                            CodepointRangeDialogPreference.DataManager codepointRangeDialogDataManager =
                                    new CodepointRangeDialogPreference.DataManager(
                                            prefs, prefKey);
                            IntRange defaultIntRange =
                                    codepointRangeDialogDataManager.readDefaultValue();
                            if (IMPORT_DEFAULT_PREFS_VALUES
                                    || !Objects.equals(range, defaultIntRange)) {
                                codepointRangeDialogDataManager.writeValue(range);
                            }
                        }
                    } else {
                        Log.e(TAG, prefKey + " doesn't have handling to be imported");
                    }
                    break;
                case PreferenceReader.TYPE_LOCALE_ARRAY:
                    //TODO: (EW) possibly could be more generic (or at least decoupled from the
                    // specific preference)
                    String[] localStringArray = getStringArray(jsonObject, jsonPropName);
                    int localeCount = localStringArray == null ? 0 : localStringArray.length;
                    List<Locale> localeList = new ArrayList<>();
                    for (int i = 0; i < localeCount; i++) {
                        if (!LocaleEntryListPreference.isValidLocale(localStringArray[i])) {
                            Log.e(TAG, fullPath + "[" + i + "] doesn't have a valid locale string: "
                                    + localStringArray[i]);
                            if (info != null) {
                                info.mWarnings.add(context.getString(R.string.invalid_locale,
                                        fullPath + "[" + i + "]", localStringArray[i]));
                            }
                            continue;
                        }
                        localeList.add(LocaleEntryListPreference.constructLocaleFromString(
                                localStringArray[i]));
                    }
                    if (prefKey != null) {
                        LocaleEntryListPreference.DataManager localeEntryListDataManager =
                                new LocaleEntryListPreference.DataManager(prefs,
                                        prefKey);
                        Locale[] defaultLocaleArray = localeEntryListDataManager.readDefaultValue();
                        Locale[] localeArray = localeList.toArray(new Locale[0]);
                        if (IMPORT_DEFAULT_PREFS_VALUES
                                || !Arrays.equals(localeArray, defaultLocaleArray)) {
                            localeEntryListDataManager.writeValue(localeArray);
                        }
                    }
                    break;
                case PreferenceReader.TYPE_TEXT_LIST_STRING:
                    //TODO: (EW) possibly could be more generic (or at least decoupled from the
                    // specific preference)
                    JSONObject textListStringJsonObject = jsonObject.getJSONObject(jsonPropName);
                    String[] textListStringStringArray = getStringArray(textListStringJsonObject,
                            TEXT_LIST_DATA_ARRAY_JSON_PROP);
                    if (textListStringStringArray == null) {
                        Log.e(TAG, fullPath + "." + TEXT_LIST_DATA_ARRAY_JSON_PROP + " is null");
                        if (info != null) {
                            info.mWarnings.add(context.getString(R.string.null_data,
                                    fullPath + "." + TEXT_LIST_DATA_ARRAY_JSON_PROP));
                        }
                        break;
                    }
                    TextList<String> textListStringData = new TextList<>(
                            textListStringStringArray,
                            textListStringJsonObject.getBoolean(TEXT_LIST_ESCAPE_CHARS_JSON_PROP));
                    if (prefKey != null) {
                        TextListPreference.DataManager textListDataManager =
                                new TextListPreference.DataManager(prefs, prefKey);
                        TextList<String> defaultTextListString =
                                textListDataManager.readDefaultValue();
                        if (IMPORT_DEFAULT_PREFS_VALUES
                                || !Objects.equals(textListStringData, defaultTextListString)) {
                            textListDataManager.writeValue(textListStringData);
                        }
                    }
                    break;
                case PreferenceReader.TYPE_TEXT_LIST_TRANSLATE_TEXT:
                    //TODO: (EW) possibly could be more generic (or at least decoupled from the
                    // specific preference)
                    JSONObject textListTranslateTextJsonObject =
                            jsonObject.getJSONObject(jsonPropName);
                    JSONObject[] translateTextJsonObjects = getJsonObjectArray(
                            textListTranslateTextJsonObject, TEXT_LIST_DATA_ARRAY_JSON_PROP);
                    if (translateTextJsonObjects == null) {
                        Log.e(TAG, fullPath + "." + TEXT_LIST_DATA_ARRAY_JSON_PROP + " is null");
                        if (info != null) {
                            info.mWarnings.add(context.getString(R.string.null_data,
                                    fullPath + "." + TEXT_LIST_DATA_ARRAY_JSON_PROP));
                        }
                        break;
                    }
                    TranslateText[] translateTextArray =
                            new TranslateText[translateTextJsonObjects.length];
                    for (int i = 0; i < translateTextJsonObjects.length; i++) {
                        translateTextArray[i] = new TranslateText(
                                translateTextJsonObjects[i].getString(
                                        TRANSLATE_TEXT_ORIGINAL_JSON_PROP),
                                translateTextJsonObjects[i].getString(
                                        TRANSLATE_TEXT_TRANSLATION_JSON_PROP));
                    }
                    TextList<TranslateText> textListTranslateTextData = new TextList<>(
                            translateTextArray,
                            textListTranslateTextJsonObject.getBoolean(
                                    TEXT_LIST_ESCAPE_CHARS_JSON_PROP));
                    if (prefKey != null) {
                        TextTranslateListPreference.DataManager textTranslateListDataManager =
                                new TextTranslateListPreference.DataManager(prefs, prefKey);
                        TextList<TranslateText> defaultTextListTranslateText =
                                textTranslateListDataManager.readDefaultValue();
                        if (IMPORT_DEFAULT_PREFS_VALUES
                                || !Objects.equals(textListTranslateTextData,
                                        defaultTextListTranslateText)) {
                            textTranslateListDataManager.writeValue(textListTranslateTextData);
                        }
                    }
                    break;
                case PreferenceReader.TYPE_UNKNOWN:
                default:
                    //TODO: (EW) probably handle gracefully, but hard crash for now to catch issues
                    throw new RuntimeException("Unknown data type for " + prefKeyOrPrefix);
            }
        } catch (JSONException e) {
            logJsonException(e, fullPath, info, context);
        }
        return true;
    }

    private static void logJsonException(JSONException e, String path,
                                         @Nullable ImportFileInfo info, @NonNull Context context) {
        String message = e.getMessage();
        Log.e(TAG, path + ": " + message);
        if (info != null) {
            if (message != null && message.matches(
                    "Value .* at \\w+ of type [\\w\\.]+ cannot be converted to [\\w\\.]+")) {
                info.mWarnings.add(context.getString(R.string.invalid_data_type, path));
            } else {
                info.mWarnings.add(context.getString(R.string.failed_to_parse_data, path));
            }
        }
    }

    private static int constrain(int value, int minValue, int maxValue, int stepValue) {
        if (value < minValue) {
            return minValue;
        }
        if (value > maxValue) {
            return maxValue;
        }
        long stepLowerEdge = (((long) value - minValue) / stepValue) * stepValue + minValue;
        long stepUpperEdge =
                (((long) value + stepValue - 1 - minValue) / stepValue) * stepValue + minValue;
        if (stepLowerEdge == stepUpperEdge || value - stepLowerEdge <= stepUpperEdge - value) {
            return (int) stepLowerEdge;
        }
        return (int) stepUpperEdge;
    }

    private static String getString(JSONObject jsonObject, String jsonPropName)
            throws JSONException {
        if (jsonObject.isNull(jsonPropName)) {
            return null;
        }
        return jsonObject.getString(jsonPropName);
    }

    private static JSONObject[] getJsonObjectArray(JSONObject jsonObject, String jsonPropName)
            throws JSONException {
        if (jsonObject.isNull(jsonPropName)) {
            return null;
        }
        JSONArray jsonArray = jsonObject.getJSONArray(jsonPropName);
        JSONObject[] result = new JSONObject[jsonArray.length()];
        for (int i = 0; i < result.length; i++) {
            result[i] = jsonArray.getJSONObject(i);
        }
        return result;
    }

    private static String[] getStringArray(JSONObject jsonObject, String jsonPropName)
            throws JSONException {
        if (jsonObject.isNull(jsonPropName)) {
            return null;
        }
        JSONArray jsonArray = jsonObject.getJSONArray(jsonPropName);
        String[] result = new String[jsonArray.length()];
        for (int i = 0; i < result.length; i++) {
            result[i] = jsonArray.getString(i);
        }
        return result;
    }

    private static int[] getIntArray(JSONObject jsonObject, String jsonPropName)
            throws JSONException {
        if (jsonObject.isNull(jsonPropName)) {
            return null;
        }
        JSONArray jsonArray = jsonObject.getJSONArray(jsonPropName);
        int[] result = new int[jsonArray.length()];
        for (int i = 0; i < result.length; i++) {
            result[i] = jsonArray.getInt(i);
        }
        return result;
    }

    private static Spanned getSpanned(JSONObject jsonObject, String jsonPropName)
            throws JSONException {
        if (jsonObject.isNull(jsonPropName)) {
            return null;
        }
        String[] spannedInfo = getStringArray(jsonObject, jsonPropName);
        //TODO: (EW) figure out how to get validation issue from this
        return SharedPreferenceManager.buildSpanned(spannedInfo);
    }

    public static void importSettings(JSONObject jsonObject, boolean replaceFieldDefaults,
                                      boolean replaceFields, List<GroupInfo> groupInfoList,
                                      boolean embedFieldDefaults, boolean replaceOtherSettings,
                                      Context context) {
        Settings instance = Settings.getInstance();
        SharedPreferenceManager prefs = instance.getPrefManager();
        prefs.unregisterOnSharedPreferenceChangeListener(instance);
        try {
            JSONObject fieldDefaultsJsonObject =
                    getJsonObject(jsonObject, FIELD_DEFAULTS_JSON_PROP);

            if (replaceFields) {
                // delete the old groups and fields before adding the new ones
                Settings.setTestFieldGroupIds(new int[0]);
            }

            List<Integer> groupIds = new ArrayList<>();
            List<Integer> fieldIds = new ArrayList<>();
            if (!replaceFields) {
                groupIds.addAll(instance.getGroupIds());
                fieldIds.addAll(instance.getFieldIds());
            }
            if (jsonObject.has(GROUPS_JSON_PROP)) {
                JSONArray groupsJsonArray = jsonObject.getJSONArray(GROUPS_JSON_PROP);

                for (int i = 0; i < groupsJsonArray.length(); i++) {
                    JSONObject groupJsonObject = groupsJsonArray.getJSONObject(i);

                    importGroupJson(groupJsonObject, GROUPS_JSON_PROP + "[" + i + "]",
                            groupInfoList.get(i), groupIds, fieldIds, embedFieldDefaults,
                            fieldDefaultsJsonObject, context);
                }

                prefs.setIntArray(PREF_TEST_GROUP_IDS, toPrimitiveArray(groupIds));
            } else if (jsonObject.has(FIELDS_JSON_PROP) && groupInfoList.size() == 1) {
                // build the ad-hoc group to load
                JSONObject groupJsonObject = new JSONObject();
                JSONArray looseFieldsJsonArray = jsonObject.getJSONArray(FIELDS_JSON_PROP);
                groupJsonObject.put(FIELDS_JSON_PROP, looseFieldsJsonArray);

                importGroupJson(groupJsonObject, null, groupInfoList.get(0), groupIds, fieldIds,
                        embedFieldDefaults, fieldDefaultsJsonObject, context);

                prefs.setIntArray(PREF_TEST_GROUP_IDS, toPrimitiveArray(groupIds));
            }

            if (replaceFieldDefaults) {
                for (String prefKeyPrefix : DEFAULTABLE_TEST_FIELD_PREF_KEY_PREFIXES) {
                    String prefKey = prefKeyPrefix + BASE_SUFFIX;
                    prefs.remove(prefKey);
                    if (fieldDefaultsJsonObject != null) {
                        String jsonProp = prefKeyPrefixToJsonName(prefKeyPrefix);
                        if (fieldDefaultsJsonObject.has(jsonProp)) {
                            loadPrefData(fieldDefaultsJsonObject, jsonProp, null, prefKeyPrefix,
                                    context, prefKey);
                        }
                    }
                }
            }

            if (replaceOtherSettings) {
                JSONObject otherSettingsJsonObject =
                        getJsonObject(jsonObject, OTHER_SETTINGS_JSON_PROP);
                for (String prefKey : MISC_PREF_KEYS) {
                    prefs.remove(prefKey);
                    if (otherSettingsJsonObject != null) {
                        String jsonProp = prefKeyPrefixToJsonName(prefKey);
                        if (otherSettingsJsonObject.has(jsonProp)) {
                            loadPrefData(otherSettingsJsonObject, jsonProp, null, prefKey, context,
                                    prefKey);
                        }
                    }
                }
            }

        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        }

        prefs.registerOnSharedPreferenceChangeListener(instance);
        instance.loadSettings();
    }

    private static void importGroupJson(JSONObject groupJsonObject, String path,
                                        GroupInfo groupInfo, List<Integer> groupIds,
                                        List<Integer> fieldIds, boolean embedFieldDefaults,
                                        JSONObject fieldDefaultsJsonObject, Context context)
            throws JSONException {
        int groupId;
        if (groupInfo.mInclude) {
            groupId = Settings.getNextId(groupIds);
            groupIds.add(groupId);
        } else {
            // add any fields to the last group
            groupId = Settings.getTestGroupId(Settings.getTestFieldGroupCount() - 1);
        }
        addGroupJson(groupJsonObject, groupId, path,
                fieldIds, groupInfo, embedFieldDefaults, fieldDefaultsJsonObject,
                context);
    }

    private static void addGroupJson(JSONObject groupJsonObject, int groupId, String path,
                                     List<Integer> fieldIds, GroupInfo groupInfo,
                                     boolean embedFieldDefaults, JSONObject fieldDefaultsJsonObject,
                                     Context context)
            throws JSONException {
        SharedPreferenceManager prefs = Settings.getInstance().getPrefManager();
        PreferenceReader preferenceReader = Settings.getInstance().getPreferenceReader();

        if (groupJsonObject.has(FIELDS_JSON_PROP)) {
            JSONArray fieldsJsonArray = groupJsonObject.getJSONArray(FIELDS_JSON_PROP);

            List<Integer> groupFieldIds = new ArrayList<>();
            for (int i = 0; i < fieldsJsonArray.length(); i++) {
                if (!groupInfo.mFields.get(i).mInclude) {
                    continue;
                }
                int fieldId = Settings.getNextId(fieldIds);
                fieldIds.add(fieldId);
                groupFieldIds.add(fieldId);
                JSONObject fieldJsonObject = fieldsJsonArray.getJSONObject(i);
                addFieldJson(fieldJsonObject, fieldId,
                        (path != null ? path + "." : "") + FIELDS_JSON_PROP + "[" + i + "]",
                        embedFieldDefaults, fieldDefaultsJsonObject, context);
            }

            int[] fieldIdsArray;
            if (groupInfo.mInclude) {
                fieldIdsArray = toPrimitiveArray(groupFieldIds);
            } else {
                fieldIdsArray = ArrayUtils.join(
                        preferenceReader.readTestGroupIntArray(groupId,
                                PREF_TEST_FIELD_IDS_PREFIX),
                        toPrimitiveArray(groupFieldIds));
            }
            prefs.setIntArray(PREF_TEST_FIELD_IDS_PREFIX + GROUP_INFIX + groupId, fieldIdsArray);
        }

        if (groupInfo.mInclude) {
            for (String prefKeyPrefix : TEST_GROUP_PREF_KEY_PREFIXES) {
                if (prefKeyPrefix.equals(PREF_TEST_FIELD_IDS_PREFIX)) {
                    continue;
                }

                String prefKey = prefKeyPrefix + GROUP_INFIX + groupId;
                prefs.remove(prefKey);
                String jsonProp = prefKeyPrefixToJsonName(prefKeyPrefix);
                if (groupJsonObject.has(jsonProp)) {
                    loadPrefData(groupJsonObject, jsonProp, path, prefKeyPrefix, context, prefKey);
                }
            }
        }
    }

    private static void addFieldJson(JSONObject fieldJsonObject, int fieldId, String path,
                                     boolean embedFieldDefaults, JSONObject fieldDefaultsJsonObject,
                                     Context context) {
        SharedPreferenceManager prefs = Settings.getInstance().getPrefManager();

        for (String fieldPrefKeyPrefix : TEST_FIELD_PREF_KEY_PREFIXES) {
            String fieldPrefKey = fieldPrefKeyPrefix + FIELD_INFIX + fieldId;
            prefs.remove(fieldPrefKey);
            String jsonProp = prefKeyPrefixToJsonName(fieldPrefKeyPrefix);

            if (DEFAULT_OVERRIDE_PREF_PREFIX_MAP.containsKey(fieldPrefKeyPrefix)) {
                JSONObject defaultableValueJsonObject;
                String defaultableValuePath;
                // embed defaults if requested and the field doesn't already override them,
                // otherwise just save the override values
                if (embedFieldDefaults
                        && !tryGetBoolean(fieldJsonObject, jsonProp, false)) {
                    defaultableValueJsonObject = fieldDefaultsJsonObject;
                    defaultableValuePath = FIELD_DEFAULTS_JSON_PROP;
                    prefs.setBoolean(fieldPrefKey, true);
                } else {
                    defaultableValueJsonObject = fieldJsonObject;
                    defaultableValuePath = path;
                    if (fieldJsonObject.has(jsonProp)) {
                        loadPrefData(fieldJsonObject, jsonProp, path, fieldPrefKeyPrefix, context,
                                fieldPrefKey);
                    }
                }
                String[] fieldDefaultsPrefKeys =
                        DEFAULT_OVERRIDE_PREF_PREFIX_MAP.get(fieldPrefKeyPrefix);
                for (String fieldDefaultPrefKeyPrefix : fieldDefaultsPrefKeys) {
                    String fieldDefaultPrefKey = fieldDefaultPrefKeyPrefix + FIELD_INFIX + fieldId;
                    String fieldDefaultJsonProp =
                            prefKeyPrefixToJsonName(fieldDefaultPrefKeyPrefix);
                    if (defaultableValueJsonObject.has(fieldDefaultJsonProp)) {
                        loadPrefData(defaultableValueJsonObject, fieldDefaultJsonProp,
                                defaultableValuePath, fieldDefaultPrefKeyPrefix, context,
                                fieldDefaultPrefKey);
                    }
                }
            } else {
                if (fieldJsonObject.has(jsonProp)) {
                    loadPrefData(fieldJsonObject, jsonProp, path, fieldPrefKeyPrefix, context,
                            fieldPrefKey);
                }
            }
        }
    }

    private static boolean tryGetBoolean(JSONObject jsonObject, String prop, boolean defaultValue) {
        if (jsonObject == null || !jsonObject.has(prop)) {
            return defaultValue;
        }
        try {
            return jsonObject.getBoolean(prop);
        } catch (JSONException e) {
            return defaultValue;
        }
    }

    private static int[] toPrimitiveArray(List<Integer> list) {
        int[] array = new int[list.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = list.get(i);
        }
        return array;
    }
}
