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

import static com.wittmane.testingedittext.settings.PreferenceKey.createBasicKey;
import static com.wittmane.testingedittext.settings.PreferenceKey.createFieldDefaultKey;
import static com.wittmane.testingedittext.settings.PreferenceKey.createFieldKey;
import static com.wittmane.testingedittext.settings.PreferenceKey.createGroupKey;
import static com.wittmane.testingedittext.settings.PreferenceKeys.*;

import android.content.Context;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.aosp.internal.util.ArrayUtils;
import com.wittmane.testingedittext.function.BiFunction;
import com.wittmane.testingedittext.function.Function;
import com.wittmane.testingedittext.function.TriConsumer;
import com.wittmane.testingedittext.settings.PreferenceReader.PrefInfo;
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
    private static final String CUSTOM_OBJECT_DATA_FORMAT_JSON_PROP = "format";
    private static final String CUSTOM_OBJECT_DATA_JSON_PROP = "data";

    private static final int DATA_FORMAT_SPANNED = 1;

    private static final String UNEXPECTED_CUSTOM_OBJECT_MESSAGE = "unexpected data format";

    public static String getJson(boolean exportFieldDefaults, List<GroupInfo> groupInfoList,
                                 boolean embedFieldDefaults, boolean exportOtherSettings) {
        PreferenceReader preferenceReader = Settings.getInstance().getPreferenceReader();
        JSONObject jsonObject = new JSONObject();
        try {
            if (exportFieldDefaults) {
                JSONObject fieldDefaultsJsonObject = new JSONObject();
                for (String defaultsPrefKeyPrefix : DEFAULTABLE_TEST_FIELD_PREF_KEY_PREFIXES) {
                    addPrefDataToJson(fieldDefaultsJsonObject,
                            createFieldDefaultKey(defaultsPrefKeyPrefix),
                            preferenceReader);
                }
                jsonObject.put(FIELD_DEFAULTS_JSON_PROP, fieldDefaultsJsonObject);
            }

            if (groupInfoList != null && preferenceReader.contains(PREF_TEST_GROUP_IDS)) {
                int[] groupIds = preferenceReader.readIntArray(createBasicKey(PREF_TEST_GROUP_IDS));

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
                                createGroupKey(PREF_TEST_FIELD_IDS_PREFIX, groupId))) {
                            addGroupFieldsJson(looseFieldsJsonArray, groupId, groupInfo,
                                    embedFieldDefaults, preferenceReader);
                        }
                    }
                    jsonObject.put(FIELDS_JSON_PROP, looseFieldsJsonArray);
                }
            }

            if (exportOtherSettings) {
                JSONObject otherSettingsJsonObject = new JSONObject();
                for (String miscPrefKey : MISC_PREF_KEYS) {
                    addPrefDataToJson(otherSettingsJsonObject, createBasicKey(miscPrefKey),
                            preferenceReader);
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
            PreferenceKey prefKey = createGroupKey(groupPrefKeyPrefix, groupId);

            if (groupPrefKeyPrefix.equals(PREF_TEST_FIELD_IDS_PREFIX)) {
                if (preferenceReader.contains(prefKey)) {
                    JSONArray fieldsJsonArray = new JSONArray();

                    addGroupFieldsJson(fieldsJsonArray, groupId, groupInfo, embedFieldDefaults,
                            preferenceReader);

                    groupJsonObject.put(FIELDS_JSON_PROP, fieldsJsonArray);
                }
            } else {
                addPrefDataToJson(groupJsonObject, prefKey, preferenceReader);
            }
        }

        return groupJsonObject;
    }

    private static void addGroupFieldsJson(JSONArray fieldsJsonArray, int groupId,
                                           GroupInfo groupInfo, boolean embedFieldDefaults,
                                           PreferenceReader preferenceReader)
            throws JSONException {
        int[] fieldIds = getFieldIds(groupId, preferenceReader);
        for (int fieldIndex = 0; fieldIndex < fieldIds.length; fieldIndex++) {
            int fieldId = fieldIds[fieldIndex];
            if (!groupInfo.mFields.get(fieldIndex).mInclude) {
                continue;
            }
            fieldsJsonArray.put(getFieldJson(fieldId, embedFieldDefaults, preferenceReader));
        }
    }

    private static int[] getFieldIds(int groupId, PreferenceReader preferenceReader) {
        return preferenceReader.readIntArray(
                createGroupKey(PREF_TEST_FIELD_IDS_PREFIX, groupId));
    }

    private static JSONObject getFieldJson(int fieldId, boolean embedFieldDefaults,
                                           PreferenceReader preferenceReader)
            throws JSONException {
        JSONObject fieldJsonObject = new JSONObject();

        for (String fieldPrefKeyPrefix : TEST_FIELD_PREF_KEY_PREFIXES) {

            if (DEFAULT_OVERRIDE_PREF_PREFIX_MAP.containsKey(fieldPrefKeyPrefix)) {
                boolean useFieldDefaults;
                // embed defaults if requested and the field doesn't already override them,
                // otherwise just load the override values
                if (embedFieldDefaults
                        && !preferenceReader.readBoolean(
                                createFieldKey(fieldPrefKeyPrefix, fieldId))) {
                    useFieldDefaults = true;
                    String jsonPropName = prefKeyPrefixToJsonName(fieldPrefKeyPrefix);
                    fieldJsonObject.put(jsonPropName, true);
                } else {
                    useFieldDefaults = false;
                    addPrefDataToJson(fieldJsonObject,
                            createFieldKey(fieldPrefKeyPrefix, fieldId),
                            preferenceReader);
                }
                String[] fieldDefaultsPrefKeys =
                        DEFAULT_OVERRIDE_PREF_PREFIX_MAP.get(fieldPrefKeyPrefix);
                for (String fieldDefaultPrefKeyPrefix : fieldDefaultsPrefKeys) {
                    addPrefDataToJson(fieldJsonObject,
                            useFieldDefaults
                                    ? createFieldDefaultKey(fieldDefaultPrefKeyPrefix)
                                    : createFieldKey(fieldDefaultPrefKeyPrefix, fieldId),
                            preferenceReader);
                }
            } else {
                addPrefDataToJson(fieldJsonObject,
                        createFieldKey(fieldPrefKeyPrefix, fieldId),
                        preferenceReader);
            }
        }

        return fieldJsonObject;
    }

    private static void addPrefDataToJson(JSONObject jsonObject, PreferenceKey prefKey,
                                          PreferenceReader preferenceReader)
            throws JSONException {
        if (!EXPORT_UNSET_PREFS && !preferenceReader.contains(prefKey)) {
            return;
        }
        String jsonPropName = prefKeyPrefixToJsonName(prefKey.getStem());
        int dataType = PreferenceReader.prefDataType(prefKey.getStem());
        switch (dataType) {
            case PreferenceReader.TYPE_BOOLEAN:
                addBooleanPrefToJson(jsonObject, jsonPropName, prefKey, preferenceReader);
                break;
            case PreferenceReader.TYPE_INT:
                addIntPrefToJson(jsonObject, jsonPropName, prefKey, preferenceReader);
                break;
            case PreferenceReader.TYPE_LONG:
                addLongPrefToJson(jsonObject, jsonPropName, prefKey, preferenceReader);
                break;
            case PreferenceReader.TYPE_FLOAT:
                addFloatPrefToJson(jsonObject, jsonPropName, prefKey, preferenceReader);
                break;
            case PreferenceReader.TYPE_STRING:
                addStringPrefToJson(jsonObject, jsonPropName, prefKey, preferenceReader);
                break;
            case PreferenceReader.TYPE_SPANNED:
                addSpannedPrefToJson(jsonObject, jsonPropName, prefKey, preferenceReader);
                break;
            case PreferenceReader.TYPE_CHAR_SEQUENCE:
                addCharSequencePrefToJson(jsonObject, jsonPropName, prefKey, preferenceReader);
                break;
            case PreferenceReader.TYPE_INT_ARRAY:
                addIntArrayPrefToJson(jsonObject, jsonPropName, prefKey, preferenceReader);
                break;
            case PreferenceReader.TYPE_STRING_ARRAY:
                addStringArrayPrefToJson(jsonObject, jsonPropName, prefKey, preferenceReader);
                break;
            case PreferenceReader.TYPE_INT_RANGE:
                addIntRangePrefToJson(jsonObject, jsonPropName, prefKey, preferenceReader);
                break;
            case PreferenceReader.TYPE_LOCALE_ARRAY:
                addLocaleArrayPrefToJson(jsonObject, jsonPropName, prefKey, preferenceReader);
                break;
            case PreferenceReader.TYPE_TEXT_LIST_STRING:
                addTextListStringPrefToJson(jsonObject, jsonPropName, prefKey, preferenceReader);
                break;
            case PreferenceReader.TYPE_TEXT_LIST_TRANSLATE_TEXT:
                addTextListTranslateTextPrefToJson(jsonObject, jsonPropName, prefKey,
                        preferenceReader);
                break;
            case PreferenceReader.TYPE_UNKNOWN:
            default:
                //TODO: (EW) probably handle gracefully, but hard crash for now to catch issues
                throw new RuntimeException("Unknown data type for " + prefKey);
        }
    }

    private static <T> void addPrefToJson(PreferenceKey prefKey,
                                          Function<PreferenceKey, PrefInfo<T>> readPref,
                                          String jsonPropName,
                                          AddPropToJsonFunction<T, JSONObject> addToJson)
            throws JSONException {
        addPrefToJson(prefKey, readPref, value -> addToJson.apply(jsonPropName, value));
    }

    private static <T> void addPrefToJson(PreferenceKey prefKey,
                                          Function<PreferenceKey, PrefInfo<T>> readPref,
                                          AddPropToJsonConsumer<T> addToJson)
            throws JSONException {
        PrefInfo<T> info = readPref.apply(prefKey);
        if (EXPORT_DEFAULT_PREFS_VALUES || !info.valueIsDefault) {
            addToJson.accept(info.value);
        }
    }

    public interface AddPropToJsonFunction<T, R> {
        R apply(String propName, T propValue) throws JSONException;
    }

    public interface AddPropToJsonConsumer<T> {
        void accept(T propValue) throws JSONException;
    }

    private static void addBooleanPrefToJson(JSONObject jsonObject, String jsonPropName,
                                             PreferenceKey prefKey,
                                             PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readBooleanWithInfo, jsonPropName,
                jsonObject::put);
    }

    private static void addIntPrefToJson(JSONObject jsonObject, String jsonPropName,
                                         PreferenceKey prefKey, PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readIntWithInfo, jsonPropName, jsonObject::put);
    }

    private static void addLongPrefToJson(JSONObject jsonObject, String jsonPropName,
                                          PreferenceKey prefKey, PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readLongWithInfo, jsonPropName, jsonObject::put);
    }

    private static void addFloatPrefToJson(JSONObject jsonObject, String jsonPropName,
                                           PreferenceKey prefKey, PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readFloatWithInfo, jsonPropName, jsonObject::put);
    }

    private static void addStringPrefToJson(JSONObject jsonObject, String jsonPropName,
                                            PreferenceKey prefKey,
                                            PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readStringWithInfo, jsonPropName, jsonObject::put);
    }

    private static void addSpannedPrefToJson(JSONObject jsonObject, String jsonPropName,
                                             PreferenceKey prefKey,
                                             PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readSpannedWithInfo,
                value -> addSpanned(jsonObject, jsonPropName, value));
    }

    private static void addCharSequencePrefToJson(JSONObject jsonObject, String jsonPropName,
                                                  PreferenceKey prefKey,
                                                  PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readCharSequenceWithInfo, value -> {
            if (value instanceof Spanned) {
                addSpanned(jsonObject, jsonPropName, (Spanned)value);
            } else if (value == null || value instanceof String) {
                addObject(jsonObject, jsonPropName, value);
            } else {
                jsonObject.put(jsonPropName, value.toString());
            }
        });
    }

    private static void addIntArrayPrefToJson(JSONObject jsonObject, String jsonPropName,
                                              PreferenceKey prefKey,
                                              PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readIntArrayWithInfo,
                value -> addArray(jsonObject, jsonPropName, value));
    }

    private static void addStringArrayPrefToJson(JSONObject jsonObject, String jsonPropName,
                                                 PreferenceKey prefKey,
                                                 PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readStringArrayWithInfo,
                value -> addArray(jsonObject, jsonPropName, value));
    }

    private static void addIntRangePrefToJson(JSONObject jsonObject, String jsonPropName,
                                              PreferenceKey prefKey,
                                              PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readIntRangeWithInfo, value -> {
            addArray(jsonObject, jsonPropName, value == null
                    ? null
                    : new int[] { value.getStart(), value.getEnd() });
        });
    }

    private static void addLocaleArrayPrefToJson(JSONObject jsonObject, String jsonPropName,
                                                 PreferenceKey prefKey,
                                                 PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readLocaleArrayWithInfo, value -> {
            String[] localeStrings;
            if (value == null) {
                localeStrings = null;
            } else {
                localeStrings = new String[value.length];
                for (int i = 0; i < value.length; i++) {
                    localeStrings[i] = LocaleEntryListPreference.getLocaleString(value[i]);
                }
            }
            addArray(jsonObject, jsonPropName, localeStrings);
        });
    }

    private static void addTextListStringPrefToJson(JSONObject jsonObject, String jsonPropName,
                                                    PreferenceKey prefKey,
                                                    PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readTextListStringWithInfo, value -> {
            JSONObject textListJsonObject = new JSONObject();
            textListJsonObject.put(TEXT_LIST_ESCAPE_CHARS_JSON_PROP, value.escapeChars());
            addArray(textListJsonObject, TEXT_LIST_DATA_ARRAY_JSON_PROP, value.getDataArray());
            jsonObject.put(jsonPropName, textListJsonObject);
        });
    }

    private static void addTextListTranslateTextPrefToJson(JSONObject jsonObject,
                                                           String jsonPropName,
                                                           PreferenceKey prefKey,
                                                           PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readTextListTranslateTextWithInfo, value -> {
            JSONObject translateTextJsonObject = new JSONObject();
            translateTextJsonObject.put(TEXT_LIST_ESCAPE_CHARS_JSON_PROP, value.escapeChars());
            JSONObject[] translateTextArray = new JSONObject[value.getDataArray().length];
            for (int i = 0; i < translateTextArray.length; i++) {
                translateTextArray[i] = new JSONObject();
                translateTextArray[i].put(TRANSLATE_TEXT_ORIGINAL_JSON_PROP,
                        value.getDataArray()[i].getOriginal());
                translateTextArray[i].put(TRANSLATE_TEXT_TRANSLATION_JSON_PROP,
                        value.getDataArray()[i].getTranslation());
            }
            addArray(translateTextJsonObject, TEXT_LIST_DATA_ARRAY_JSON_PROP, translateTextArray);
            jsonObject.put(jsonPropName, translateTextJsonObject);
        });
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

    private static void addSpanned(JSONObject jsonObject, String jsonPropName, Spanned value)
            throws JSONException {
        if (value == null) {
            jsonObject.put(jsonPropName, JSONObject.NULL);
        } else {
            // embed an indication of what data this holds so that if other CharSequence types are
            // supported in the future or we find a better way to export the data, we can maintain
            // compatibility between varying versions between the exporting and importing app
            JSONObject dataJsonObject = new JSONObject();
            dataJsonObject.put(CUSTOM_OBJECT_DATA_FORMAT_JSON_PROP, DATA_FORMAT_SPANNED);
            // get the data that SharedPreferenceManager uses to save spanned objects
            addArray(dataJsonObject, CUSTOM_OBJECT_DATA_JSON_PROP,
                    SharedPreferenceManager.getSpannedInfo(value));
            jsonObject.put(jsonPropName, dataJsonObject);
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

    private static class UnusedPropertyTracker {
        private final Set<String> mUnusedProperties;

        public UnusedPropertyTracker(JSONObject jsonObject) {
            mUnusedProperties = new HashSet<>();
            Iterator<String> keys = jsonObject.keys();
            while(keys.hasNext()) {
                String key = keys.next();
                mUnusedProperties.add(key);
            }
        }

        public boolean contains(String property) {
            boolean result = mUnusedProperties.contains(property);
            mUnusedProperties.remove(property);
            return result;
        }

        public Set<String> getUnusedProperties() {
            return mUnusedProperties;
        }
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
            UnusedPropertyTracker props = new UnusedPropertyTracker(info.mJsonObject);

            if (props.contains(GROUPS_JSON_PROP)) {
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
                JSONObject fieldDefaultsJsonObject =
                        info.mJsonObject.getJSONObject(FIELD_DEFAULTS_JSON_PROP);
                if (!validateSettingsClusterJson(DEFAULTABLE_TEST_FIELD_PREF_KEY_PREFIXES,
                        fieldDefaultsJsonObject, info, FIELD_DEFAULTS_JSON_PROP, context)) {
                    return info;
                }
                info.mIsFieldDefaultsIncluded = true;
            }

            if (props.contains(OTHER_SETTINGS_JSON_PROP)) {
                JSONObject otherSettingsJsonObject =
                        info.mJsonObject.getJSONObject(OTHER_SETTINGS_JSON_PROP);
                if (!validateSettingsClusterJson(MISC_PREF_KEYS,
                        otherSettingsJsonObject, info, OTHER_SETTINGS_JSON_PROP, context)) {
                    return info;
                }
                info.mIsOtherSettingsIncluded = true;
            }

            info.mUnexpectedProps.addAll(props.getUnusedProperties());
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

        UnusedPropertyTracker props = new UnusedPropertyTracker(groupJsonObject);

        if (props.contains(FIELDS_JSON_PROP)) {
            JSONArray fieldsJsonArray = groupJsonObject.getJSONArray(FIELDS_JSON_PROP);
            for (int i = 0; i < fieldsJsonArray.length(); i++) {
                FieldInfo fieldInfo = new FieldInfo();
                JSONObject fieldJsonObject = fieldsJsonArray.getJSONObject(i);
                if (!validateFieldJson(fieldJsonObject, info, i,
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
                if (!testGroupPrefKeyPrefix.equals(PREF_TEST_FIELD_IDS_PREFIX)) {
                    if (!validatePrefData(groupJsonObject, jsonProp, path, testGroupPrefKeyPrefix,
                            info, context, namesMap)) {
                        return false;
                    }
                }
            }
        }

        for (String prop : props.getUnusedProperties()) {
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
                                             int fieldIndex, String path, Context context,
                                             FieldInfo fieldInfo) {
        Map<String, String> namesMap = new HashMap<>();

        UnusedPropertyTracker props = new UnusedPropertyTracker(fieldJsonObject);

        for (String testFieldPrefKeyPrefix : TEST_FIELD_PREF_KEY_PREFIXES) {
            String jsonProp = prefKeyPrefixToJsonName(testFieldPrefKeyPrefix);
            if (props.contains(jsonProp)) {
                if (!validatePrefData(fieldJsonObject, jsonProp, path, testFieldPrefKeyPrefix, info,
                        context, namesMap)) {
                    return false;
                }
            }
        }

        for (String defaultableTestFieldPrefKeyPrefix : DEFAULTABLE_TEST_FIELD_PREF_KEY_PREFIXES) {
            String jsonProp = prefKeyPrefixToJsonName(defaultableTestFieldPrefKeyPrefix);
            if (props.contains(jsonProp)) {
                if (!validatePrefData(fieldJsonObject, jsonProp, path,
                        defaultableTestFieldPrefKeyPrefix, info, context, null)) {
                    return false;
                }
            }
        }

        for (String prop : props.getUnusedProperties()) {
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

    private static boolean validateSettingsClusterJson(String[] keyOrKeyPrefixArray,
                                                       JSONObject jsonObject,
                                                       ImportFileInfo info, String path,
                                                       Context context) {
        UnusedPropertyTracker props = new UnusedPropertyTracker(jsonObject);

        for (String prefKeyPrefix : keyOrKeyPrefixArray) {
            String jsonProp = prefKeyPrefixToJsonName(prefKeyPrefix);
            if (props.contains(jsonProp)) {
                if (!validatePrefData(jsonObject, jsonProp, path, prefKeyPrefix, info, context,
                        null)) {
                    return false;
                }
            }
        }

        for (String prop : props.getUnusedProperties()) {
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

    private static boolean loadOrValidatePrefData(JSONObject jsonObject, String jsonPropName,
                                                  String path, String prefKeyOrPrefix,
                                                  @Nullable ImportFileInfo info,
                                                  @NonNull Context context,
                                                  @Nullable String prefKey,
                                                  @Nullable Map<String, String> namesMap) {
        String fullPath = (path == null ? "" : (path + ".")) + jsonPropName;
        int dataType = PreferenceReader.prefDataType(prefKeyOrPrefix);
        try {
            switch (dataType) {
                case PreferenceReader.TYPE_BOOLEAN:
                    loadOrValidateBoolean(jsonObject, jsonPropName, prefKeyOrPrefix, prefKey);
                    break;
                case PreferenceReader.TYPE_INT:
                    loadOrValidateInt(jsonObject, jsonPropName, fullPath, prefKeyOrPrefix, info, context, prefKey);
                    break;
                case PreferenceReader.TYPE_LONG:
                    loadOrValidateLong(jsonObject, jsonPropName, prefKeyOrPrefix, prefKey);
                    break;
                case PreferenceReader.TYPE_FLOAT:
                    loadOrValidateFloat(jsonObject, jsonPropName, fullPath, prefKeyOrPrefix, info, context, prefKey);
                    break;
                case PreferenceReader.TYPE_STRING:
                    loadOrValidateString(jsonObject, jsonPropName, fullPath, prefKeyOrPrefix, info, context, prefKey, namesMap);
                    break;
                case PreferenceReader.TYPE_SPANNED:
                    loadOrValidateSpanned(jsonObject, jsonPropName, prefKeyOrPrefix, prefKey);
                    break;
                case PreferenceReader.TYPE_CHAR_SEQUENCE:
                    loadOrValidateCharSequence(jsonObject, jsonPropName, prefKeyOrPrefix, prefKey, namesMap);
                    break;
                case PreferenceReader.TYPE_INT_ARRAY:
                    loadOrValidateIntArray(jsonObject, jsonPropName, prefKeyOrPrefix, prefKey);
                    break;
                case PreferenceReader.TYPE_STRING_ARRAY:
                    loadOrValidateStringArray(jsonObject, jsonPropName, prefKeyOrPrefix, prefKey);
                    break;
                case PreferenceReader.TYPE_INT_RANGE:
                    loadOrValidateIntRange(jsonObject, jsonPropName, fullPath, prefKeyOrPrefix, info, context, prefKey);
                    break;
                case PreferenceReader.TYPE_LOCALE_ARRAY:
                    loadOrValidateLocaleArray(jsonObject, jsonPropName, fullPath, info, context, prefKey);
                    break;
                case PreferenceReader.TYPE_TEXT_LIST_STRING:
                    loadOrValidateTextListString(jsonObject, jsonPropName, fullPath, info, context, prefKey);
                    break;
                case PreferenceReader.TYPE_TEXT_LIST_TRANSLATE_TEXT:
                    loadOrValidateTextListTranslateText(jsonObject, jsonPropName, fullPath, info, context, prefKey);
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

    private static void loadOrValidateBoolean(JSONObject jsonObject, String jsonPropName,
                                              String prefKeyOrPrefix, @Nullable String prefKey)
            throws JSONException {
        boolean value = jsonObject.getBoolean(jsonPropName);
        setPref(prefKey, prefKeyOrPrefix, value, SharedPreferenceManager::setBoolean);
    }

    private static void loadOrValidateInt(JSONObject jsonObject, String jsonPropName,
                                          String fullPath, String prefKeyOrPrefix,
                                          @Nullable ImportFileInfo info, @NonNull Context context,
                                          @Nullable String prefKey)
            throws JSONException {
        int value = jsonObject.getInt(jsonPropName);
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
        int constrainedIntData = constrain(value, minValue, maxValue, stepValue);
        if (constrainedIntData != value
                && value != PreferenceReader.getPrefDefaultInt(prefKeyOrPrefix)) {
            if (value < minValue || value > maxValue) {
                Log.e(TAG, fullPath + " ( " + value + ") isn't in the range "
                        + minValue + " - " + maxValue);
                if (info != null) {
                    info.mWarnings.add(context.getString(R.string.value_not_in_range,
                            fullPath, value, minValue, maxValue));
                }
                return;
            } else {
                Log.w(TAG, fullPath + " has an int ( " + value
                        + ") that doesn't conform to the constraints: min=" + minValue
                        + ", max=" + maxValue + ", step=" + stepValue);
                if (info != null) {
                    info.mWarnings.add(context.getString(R.string.invalid_step_value,
                            fullPath));
                }
                // it should be relatively safe to just shift to the nearest step
                value = constrainedIntData;
            }
        }
        setPref(prefKey, prefKeyOrPrefix, value, SharedPreferenceManager::setInt);
    }

    private static void loadOrValidateLong(JSONObject jsonObject, String jsonPropName,
                                           String prefKeyOrPrefix, @Nullable String prefKey)
            throws JSONException {
        long value = jsonObject.getLong(jsonPropName);
        setPref(prefKey, prefKeyOrPrefix, value, SharedPreferenceManager::setLong);
    }

    private static void loadOrValidateFloat(JSONObject jsonObject, String jsonPropName,
                                            String fullPath, String prefKeyOrPrefix,
                                            @Nullable ImportFileInfo info, @NonNull Context context,
                                            @Nullable String prefKey)
            throws JSONException {
        double value = jsonObject.getDouble(jsonPropName);
        if (value > Float.MAX_VALUE || value < Float.MIN_VALUE) {
            Log.e(TAG, value + " isn't a valid float for " + fullPath);
            if (info != null) {
                info.mWarnings.add(
                        context.getString(R.string.invalid_float_data, fullPath));
            }
            return;
        }
        setPref(prefKey, prefKeyOrPrefix, (float) value, SharedPreferenceManager::setFloat);
    }

    private static void loadOrValidateString(JSONObject jsonObject, String jsonPropName,
                                             String fullPath, String prefKeyOrPrefix,
                                             @Nullable ImportFileInfo info,
                                             @NonNull Context context, @Nullable String prefKey,
                                             @Nullable Map<String, String> namesMap)
            throws JSONException {
        String value = getString(jsonObject, jsonPropName);
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
                && !ArrayUtils.contains(allowedStringValues, value)
                && !TextUtils.equals(value,
                PreferenceReader.getPrefDefaultString(prefKeyOrPrefix))) {
            Log.e(TAG, fullPath + " has an invalid value: " + value);
            if (info != null) {
                info.mWarnings.add(context.getString(R.string.invalid_value,
                        fullPath, value));
            }
            return;
        }
        setPref(prefKey, prefKeyOrPrefix, value, SharedPreferenceManager::setString);
        if (namesMap != null) {
            switch (prefKeyOrPrefix) {
                case PREF_TEST_GROUP_NAME_PREFIX:
                    namesMap.put(prefKeyOrPrefix, value);
            }
        }
    }

    private static void loadOrValidateSpanned(JSONObject jsonObject, String jsonPropName,
                                              String prefKeyOrPrefix, @Nullable String prefKey)
            throws JSONException {
        Spanned value = getSpanned(jsonObject, jsonPropName);
        setPref(prefKey, prefKeyOrPrefix, value, SharedPreferenceManager::setSpanned);
    }

    private static void loadOrValidateCharSequence(JSONObject jsonObject, String jsonPropName,
                                                   String prefKeyOrPrefix, @Nullable String prefKey,
                                                   @Nullable Map<String, String> namesMap)
            throws JSONException {
        CharSequence value;
        if (jsonObject.isNull(jsonPropName)) {
            value = null;
        } else if (jsonObject.get(jsonPropName) instanceof String) {
            value = jsonObject.getString(jsonPropName);
        } else {
            value = getSpanned(jsonObject, jsonPropName);
        }
        setPref(prefKey, prefKeyOrPrefix, value, SharedPreferenceManager::setCharSequence);
        if (namesMap != null) {
            switch (prefKeyOrPrefix) {
                case PREF_IME_LABEL_TEXT_PREFIX:
                case PREF_IME_DEFAULT_TEXT_PREFIX:
                case PREF_IME_HINT_TEXT_PREFIX:
                    namesMap.put(prefKeyOrPrefix, value == null ? null : value.toString());
            }
        }
    }

    private static void loadOrValidateIntArray(JSONObject jsonObject, String jsonPropName,
                                               String prefKeyOrPrefix, @Nullable String prefKey)
            throws JSONException {
        int[] value = getIntArray(jsonObject, jsonPropName);
        setPref(prefKey, prefKeyOrPrefix, value, SharedPreferenceManager::setIntArray);
    }

    private static void loadOrValidateStringArray(JSONObject jsonObject, String jsonPropName,
                                                  String prefKeyOrPrefix,
                                                  @Nullable String prefKey) throws JSONException {
        String[] value = getStringArray(jsonObject, jsonPropName);
        setPref(prefKey, prefKeyOrPrefix, value, SharedPreferenceManager::setStringArray);
    }

    private static void loadOrValidateIntRange(JSONObject jsonObject, String jsonPropName,
                                               String fullPath, String prefKeyOrPrefix,
                                               @Nullable ImportFileInfo info,
                                               @NonNull Context context,
                                               @Nullable String prefKey)
            throws JSONException {
        int[] value = getIntArray(jsonObject, jsonPropName);
        if (value != null && value.length != 2) {
            Log.e(TAG, fullPath + " doesn't have exactly 2 values: " + Arrays.toString(value));
            if (info != null) {
                info.mWarnings.add(context.getString(R.string.invalid_data, fullPath));
            }
            return;
        }
        if (prefKeyOrPrefix.equals(PREF_RESTRICT_RANGE_PREFIX)) {
            IntRange range = value == null
                    ? null
                    : new IntRange(value[0], value[1]);
            if (!CodepointRangeDialogPreference.isValidRange(range)) {
                Log.e(TAG, fullPath + " contains an invalid codepoint range: " + range);
                if (info != null) {
                    info.mWarnings.add(
                            context.getString(R.string.invalid_data, fullPath));
                }
                return;
            }
            setPref(prefKey, range, CodepointRangeDialogPreference.DataManager::new);
        } else {
            Log.e(TAG, prefKey + " doesn't have handling to be imported");
        }
    }

    private static void loadOrValidateLocaleArray(JSONObject jsonObject, String jsonPropName,
                                                  String fullPath, @Nullable ImportFileInfo info,
                                                  @NonNull Context context,
                                                  @Nullable String prefKey)
            throws JSONException {
        String[] localStrings = getStringArray(jsonObject, jsonPropName);
        int localeCount = localStrings == null ? 0 : localStrings.length;
        List<Locale> localeList = new ArrayList<>();
        for (int i = 0; i < localeCount; i++) {
            if (!LocaleEntryListPreference.isValidLocale(localStrings[i])) {
                Log.e(TAG, fullPath + "[" + i + "] doesn't have a valid locale string: "
                        + localStrings[i]);
                if (info != null) {
                    info.mWarnings.add(context.getString(R.string.invalid_locale,
                            fullPath + "[" + i + "]", localStrings[i]));
                }
                continue;
            }
            localeList.add(LocaleEntryListPreference.constructLocaleFromString(localStrings[i]));
        }
        Locale[] localeArray = localeList.toArray(new Locale[0]);
        setPref(prefKey, localeArray, LocaleEntryListPreference.DataManager::new);
    }

    private static void loadOrValidateTextListString(JSONObject jsonObject, String jsonPropName,
                                                     String fullPath, @Nullable ImportFileInfo info,
                                                     @NonNull Context context,
                                                     @Nullable String prefKey)
            throws JSONException {
        JSONObject textListJsonObject = jsonObject.getJSONObject(jsonPropName);
        String[] stringArray = getStringArray(textListJsonObject, TEXT_LIST_DATA_ARRAY_JSON_PROP);
        if (stringArray == null) {
            Log.e(TAG, fullPath + "." + TEXT_LIST_DATA_ARRAY_JSON_PROP + " is null");
            if (info != null) {
                info.mWarnings.add(context.getString(R.string.null_data,
                        fullPath + "." + TEXT_LIST_DATA_ARRAY_JSON_PROP));
            }
            return;
        }
        TextList<String> textList = new TextList<>(
                stringArray,
                textListJsonObject.getBoolean(TEXT_LIST_ESCAPE_CHARS_JSON_PROP));
        setPref(prefKey, textList, TextListPreference.DataManager::new);

    }

    private static void loadOrValidateTextListTranslateText(JSONObject jsonObject,
                                                            String jsonPropName, String fullPath,
                                                            @Nullable ImportFileInfo info,
                                                            @NonNull Context context,
                                                            @Nullable String prefKey)
            throws JSONException {
        JSONObject textListJsonObject = jsonObject.getJSONObject(jsonPropName);
        JSONObject[] translateTextJsonObjects = getJsonObjectArray(textListJsonObject,
                TEXT_LIST_DATA_ARRAY_JSON_PROP);
        if (translateTextJsonObjects == null) {
            Log.e(TAG, fullPath + "." + TEXT_LIST_DATA_ARRAY_JSON_PROP + " is null");
            if (info != null) {
                info.mWarnings.add(context.getString(R.string.null_data,
                        fullPath + "." + TEXT_LIST_DATA_ARRAY_JSON_PROP));
            }
            return;
        }
        TranslateText[] translateTextArray = new TranslateText[translateTextJsonObjects.length];
        for (int i = 0; i < translateTextJsonObjects.length; i++) {
            translateTextArray[i] = new TranslateText(
                    translateTextJsonObjects[i].getString(TRANSLATE_TEXT_ORIGINAL_JSON_PROP),
                    translateTextJsonObjects[i].getString(TRANSLATE_TEXT_TRANSLATION_JSON_PROP));
        }
        TextList<TranslateText> textListTranslateTextData = new TextList<>(
                translateTextArray,
                textListJsonObject.getBoolean(TEXT_LIST_ESCAPE_CHARS_JSON_PROP));
        setPref(prefKey, textListTranslateTextData, TextTranslateListPreference.DataManager::new);
    }

    //TODO: (EW) rename - this is a confusing name that sounds like it would always save
    private static <T> void setPref(@Nullable String prefKey, String prefKeyOrPrefix, T value,
                                    TriConsumer<SharedPreferenceManager, String, T> setPref) {
        if (prefKey != null) {
            Spanned defaultValue = PreferenceReader.getPrefDefaultSpanned(prefKeyOrPrefix);
            if (IMPORT_DEFAULT_PREFS_VALUES
                    || !SharedPreferenceManager.equals(value, defaultValue)) {
                SharedPreferenceManager prefs = Settings.getInstance().getPrefManager();
                setPref.accept(prefs, prefKey, value);
            }
        }
    }

    private static <T> void setPref(@Nullable String prefKey, T value,
            BiFunction<SharedPreferenceManager, String, DataManager<T>> getDataManager) {
        if (prefKey != null) {
            SharedPreferenceManager prefs = Settings.getInstance().getPrefManager();
            DataManager<T> dataManager = getDataManager.apply(prefs, prefKey);
            T defaultValue = dataManager.readDefaultValue();
            if (IMPORT_DEFAULT_PREFS_VALUES
                    || !SharedPreferenceManager.equals(value, defaultValue)) {
                dataManager.writeValue(value);
            }
        }
    }

    private static void logJsonException(JSONException e, String path,
                                         @Nullable ImportFileInfo info, @NonNull Context context) {
        String message = e.getMessage();
        Log.e(TAG, path + ": " + message);
        if (info != null) {
            if (message != null && message.matches(
                    "Value .* at \\w+ of type [\\w.]+ cannot be converted to [\\w.]+")) {
                info.mWarnings.add(context.getString(R.string.invalid_data_type, path));
            } else if (message != null && message.equals(UNEXPECTED_CUSTOM_OBJECT_MESSAGE)) {
                info.mWarnings.add(context.getString(R.string.unexpected_data_format, path));
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
        JSONObject dataJsonObject = jsonObject.getJSONObject(jsonPropName);
        int dataFormat = dataJsonObject.getInt(CUSTOM_OBJECT_DATA_FORMAT_JSON_PROP);
        if (dataFormat == DATA_FORMAT_SPANNED) {
            String[] spannedInfo = getStringArray(dataJsonObject, CUSTOM_OBJECT_DATA_JSON_PROP);
            return SharedPreferenceManager.buildSpanned(spannedInfo);
        }
        throw new JSONException(UNEXPECTED_CUSTOM_OBJECT_MESSAGE);
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
                        getFieldIds(groupId, preferenceReader),
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
