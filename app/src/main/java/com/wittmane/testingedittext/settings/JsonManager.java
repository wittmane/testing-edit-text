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
import com.wittmane.testingedittext.function.Consumer;
import com.wittmane.testingedittext.function.Function;
import com.wittmane.testingedittext.function.TriConsumer;
import com.wittmane.testingedittext.settings.PreferenceReader.PrefInfo;
import com.wittmane.testingedittext.settings.datamanager.DataManager;
import com.wittmane.testingedittext.settings.datamanager.IntRangeDataManager;
import com.wittmane.testingedittext.settings.datamanager.LocaleArrayDataManager;
import com.wittmane.testingedittext.settings.datamanager.StringTextListDataManager;
import com.wittmane.testingedittext.settings.datamanager.TranslateTextTextListDataManager;
import com.wittmane.testingedittext.settings.json.JsonArray;
import com.wittmane.testingedittext.settings.json.JsonObject;
import com.wittmane.testingedittext.settings.preferences.CodepointRangeDialogPreference;
import com.wittmane.testingedittext.settings.preferences.LocaleEntryListPreference;
import com.wittmane.testingedittext.util.IterableUtils;

import org.json.JSONException;

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

    public static String getJson(boolean exportFieldDefaults, List<GroupTransferInfo> groupInfoList,
                                 boolean embedFieldDefaults, boolean exportOtherSettings) {
        PreferenceReader preferenceReader = Settings.getInstance().getPreferenceReader();
        JsonObject jsonObject = new JsonObject();
        try {
            if (exportFieldDefaults) {
                JsonObject fieldDefaultsJsonObject = new JsonObject();
                for (String defaultsPrefKeyPrefix : DEFAULTABLE_TEST_FIELD_PREF_KEY_PREFIXES) {
                    addPrefDataToJson(fieldDefaultsJsonObject,
                            createFieldDefaultKey(defaultsPrefKeyPrefix),
                            preferenceReader);
                }
                jsonObject.put(FIELD_DEFAULTS_JSON_PROP, fieldDefaultsJsonObject);
            }

            if (groupInfoList != null && preferenceReader.contains(PREF_TEST_GROUP_IDS)) {
                int[] groupIds = preferenceReader.readIntArray(createBasicKey(PREF_TEST_GROUP_IDS));

                if (IterableUtils.any(groupInfoList, GroupTransferInfo::isIncluded)
                        || !IterableUtils.any(groupInfoList,
                                groupInfo -> IterableUtils.any(groupInfo.getFields(),
                                        fieldInfo -> fieldInfo.isIncluded()))) {
                    // either at least one group was flagged to include or there are no fields and
                    // no groups to include
                    JsonArray groupsJsonArray = new JsonArray();
                    for (int groupIndex = 0; groupIndex < groupIds.length; groupIndex++) {
                        int groupId = groupIds[groupIndex];
                        GroupTransferInfo groupInfo = groupInfoList.get(groupIndex);
                        if (groupInfoList.get(groupIndex).isIncluded()) {
                            groupsJsonArray.put(
                                    getGroupJson(groupId, groupInfo, embedFieldDefaults,
                                            preferenceReader));
                        }
                    }
                    jsonObject.put(GROUPS_JSON_PROP, groupsJsonArray);
                } else {
                    // there are no groups flagged to include, so skip the groups themselves and
                    // just collect the fields that are flagged to include from any group
                    JsonArray looseFieldsJsonArray = new JsonArray();
                    for (int groupIndex = 0; groupIndex < groupIds.length; groupIndex++) {
                        int groupId = groupIds[groupIndex];
                        GroupTransferInfo groupInfo = groupInfoList.get(groupIndex);
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
                JsonObject otherSettingsJsonObject = new JsonObject();
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

    private static JsonObject getGroupJson(int groupId, GroupTransferInfo groupInfo,
                                           boolean embedFieldDefaults,
                                           PreferenceReader preferenceReader)
            throws JSONException {
        JsonObject groupJsonObject = new JsonObject();

        for (String groupPrefKeyPrefix : TEST_GROUP_PREF_KEY_PREFIXES) {
            PreferenceKey prefKey = createGroupKey(groupPrefKeyPrefix, groupId);

            if (groupPrefKeyPrefix.equals(PREF_TEST_FIELD_IDS_PREFIX)) {
                if (preferenceReader.contains(prefKey)) {
                    JsonArray fieldsJsonArray = new JsonArray();

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

    private static void addGroupFieldsJson(JsonArray fieldsJsonArray, int groupId,
                                           GroupTransferInfo groupInfo, boolean embedFieldDefaults,
                                           PreferenceReader preferenceReader)
            throws JSONException {
        int[] fieldIds = getFieldIds(groupId, preferenceReader);
        for (int fieldIndex = 0; fieldIndex < fieldIds.length; fieldIndex++) {
            int fieldId = fieldIds[fieldIndex];
            if (!groupInfo.getFields().get(fieldIndex).isIncluded()) {
                continue;
            }
            fieldsJsonArray.put(getFieldJson(fieldId, embedFieldDefaults, preferenceReader));
        }
    }

    private static int[] getFieldIds(int groupId, PreferenceReader preferenceReader) {
        return preferenceReader.readIntArray(
                createGroupKey(PREF_TEST_FIELD_IDS_PREFIX, groupId));
    }

    private static JsonObject getFieldJson(int fieldId, boolean embedFieldDefaults,
                                           PreferenceReader preferenceReader)
            throws JSONException {
        JsonObject fieldJsonObject = new JsonObject();

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

    private static void addPrefDataToJson(JsonObject jsonObject, PreferenceKey prefKey,
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
                                          AddPropToJsonFunction<T, JsonObject> addToJson)
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

    private static void addBooleanPrefToJson(JsonObject jsonObject, String jsonPropName,
                                             PreferenceKey prefKey,
                                             PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readBooleanWithInfo, jsonPropName,
                jsonObject::put);
    }

    private static void addIntPrefToJson(JsonObject jsonObject, String jsonPropName,
                                         PreferenceKey prefKey, PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readIntWithInfo, jsonPropName, jsonObject::put);
    }

    private static void addLongPrefToJson(JsonObject jsonObject, String jsonPropName,
                                          PreferenceKey prefKey, PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readLongWithInfo, jsonPropName, jsonObject::put);
    }

    private static void addFloatPrefToJson(JsonObject jsonObject, String jsonPropName,
                                           PreferenceKey prefKey, PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readFloatWithInfo, jsonPropName, jsonObject::put);
    }

    private static void addStringPrefToJson(JsonObject jsonObject, String jsonPropName,
                                            PreferenceKey prefKey,
                                            PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readStringWithInfo, jsonPropName, jsonObject::put);
    }

    private static void addSpannedPrefToJson(JsonObject jsonObject, String jsonPropName,
                                             PreferenceKey prefKey,
                                             PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readSpannedWithInfo, jsonPropName,
                jsonObject::put);
    }

    private static void addCharSequencePrefToJson(JsonObject jsonObject, String jsonPropName,
                                                  PreferenceKey prefKey,
                                                  PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readCharSequenceWithInfo, jsonPropName,
                jsonObject::put);
    }

    private static void addIntArrayPrefToJson(JsonObject jsonObject, String jsonPropName,
                                              PreferenceKey prefKey,
                                              PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readIntArrayWithInfo, jsonPropName,
                jsonObject::put);
    }

    private static void addStringArrayPrefToJson(JsonObject jsonObject, String jsonPropName,
                                                 PreferenceKey prefKey,
                                                 PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readStringArrayWithInfo, jsonPropName,
                jsonObject::put);
    }

    private static void addIntRangePrefToJson(JsonObject jsonObject, String jsonPropName,
                                              PreferenceKey prefKey,
                                              PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readIntRangeWithInfo, value -> {
            jsonObject.put(jsonPropName, value == null
                    ? null
                    : new int[] { value.getStart(), value.getEnd() });
        });
    }

    private static void addLocaleArrayPrefToJson(JsonObject jsonObject, String jsonPropName,
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
                    localeStrings[i] = LocaleArrayDataManager.getLocaleString(value[i]);
                }
            }
            jsonObject.put(jsonPropName, localeStrings);
        });
    }

    private static void addTextListStringPrefToJson(JsonObject jsonObject, String jsonPropName,
                                                    PreferenceKey prefKey,
                                                    PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readTextListStringWithInfo, value -> {
            JsonObject textListJsonObject = new JsonObject();
            textListJsonObject.put(TEXT_LIST_ESCAPE_CHARS_JSON_PROP, value.escapeChars());
            textListJsonObject.put(TEXT_LIST_DATA_ARRAY_JSON_PROP, value.getDataArray());
            jsonObject.put(jsonPropName, textListJsonObject);
        });
    }

    private static void addTextListTranslateTextPrefToJson(JsonObject jsonObject,
                                                           String jsonPropName,
                                                           PreferenceKey prefKey,
                                                           PreferenceReader preferenceReader)
            throws JSONException {
        addPrefToJson(prefKey, preferenceReader::readTextListTranslateTextWithInfo, value -> {
            JsonObject translateTextJsonObject = new JsonObject();
            translateTextJsonObject.put(TEXT_LIST_ESCAPE_CHARS_JSON_PROP, value.escapeChars());
            JsonObject[] translateTextArray = new JsonObject[value.getDataArray().length];
            for (int i = 0; i < translateTextArray.length; i++) {
                translateTextArray[i] = new JsonObject();
                translateTextArray[i].put(TRANSLATE_TEXT_ORIGINAL_JSON_PROP,
                        value.getDataArray()[i].getOriginal());
                translateTextArray[i].put(TRANSLATE_TEXT_TRANSLATION_JSON_PROP,
                        value.getDataArray()[i].getTranslation());
            }
            translateTextJsonObject.put(TEXT_LIST_DATA_ARRAY_JSON_PROP, translateTextArray);
            jsonObject.put(jsonPropName, translateTextJsonObject);
        });
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
        private JsonObject mJsonObject;
        private List<GroupTransferInfo> mGroups;
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

        public JsonObject getJsonObject() {
            return mJsonObject;
        }

        public List<GroupTransferInfo> getGroups() {
            return mGroups;
        }

        public boolean isFieldDefaultsIncluded() {
            return mIsFieldDefaultsIncluded;
        }

        public boolean isOtherSettingsIncluded() {
            return mIsOtherSettingsIncluded;
        }
    }

    public static class GroupTransferInfo {
        private List<FieldTransferInfo> mFields;
        private String mName;
        private boolean mInclude;
        private boolean mIsAdHoc;

        public List<FieldTransferInfo> getFields() {
            return mFields;
        }

        public void setFields(List<FieldTransferInfo> fields) {
            mFields = fields;
        }

        public String getName() {
            return mName;
        }

        public void setName(String name) {
            mName = name;
        }

        public void setIncluded(boolean include) {
            mInclude = include;
        }

        public boolean isIncluded() {
            return mInclude;
        }

        public boolean isAdHoc() {
            return mIsAdHoc;
        }
    }

    public static class FieldTransferInfo {
        private String mName;
        private boolean mInclude;

        public String getName() {
            return mName;
        }

        public void setName(String name) {
            mName = name;
        }

        public void setIncluded(boolean include) {
            mInclude = include;
        }

        public boolean isIncluded() {
            return mInclude;
        }
    }

    private static class UnusedPropertyTracker {
        private final Set<String> mUnusedProperties;

        public UnusedPropertyTracker(JsonObject jsonObject) {
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

    private static JsonObject getJsonObject(JsonObject jsonObject, String propName)
            throws JSONException {
        return jsonObject.has(propName)
                ? jsonObject.getJsonObject(propName)
                : null;
    }

    public static ImportFileInfo validateJson(String rawJson, Context context) {
        ImportFileInfo info = new ImportFileInfo();

        try {
            info.mJsonObject = new JsonObject(rawJson);
            UnusedPropertyTracker props = new UnusedPropertyTracker(info.mJsonObject);

            if (props.contains(GROUPS_JSON_PROP)) {
                List<GroupTransferInfo> groups = new ArrayList<>();
                JsonArray groupsJsonArray = info.mJsonObject.getJsonArray(GROUPS_JSON_PROP);
                for (int i = 0; i < groupsJsonArray.length(); i++) {
                    GroupTransferInfo groupInfo = new GroupTransferInfo();
                    JsonObject groupJsonObject = groupsJsonArray.getJsonObject(i);
                    if (!validateGroupJson(groupJsonObject, info, i, context, groupInfo)) {
                        return info;
                    }
                    groups.add(groupInfo);
                }
                info.mGroups = groups;
            } else if (props.contains(FIELDS_JSON_PROP)) {
                List<GroupTransferInfo> groups = new ArrayList<>();
                GroupTransferInfo groupInfo = new GroupTransferInfo();

                // build the ad-hoc group to load
                JsonObject groupJsonObject = new JsonObject();
                JsonArray looseFieldsJsonArray = info.mJsonObject.getJsonArray(FIELDS_JSON_PROP);
                groupJsonObject.put(FIELDS_JSON_PROP, looseFieldsJsonArray);

                if (!validateGroupJson(groupJsonObject, info, 0, context, groupInfo)) {
                    return info;
                }
                groupInfo.mIsAdHoc = true;
                groupInfo.setName(context.getText(R.string.ad_hoc_import_group_name).toString());
                groups.add(groupInfo);

                info.mGroups = groups;
            }

            if (props.contains(FIELD_DEFAULTS_JSON_PROP)) {
                JsonObject fieldDefaultsJsonObject =
                        info.mJsonObject.getJsonObject(FIELD_DEFAULTS_JSON_PROP);
                if (!validateSettingsClusterJson(DEFAULTABLE_TEST_FIELD_PREF_KEY_PREFIXES,
                        fieldDefaultsJsonObject, info, context)) {
                    return info;
                }
                info.mIsFieldDefaultsIncluded = true;
            }

            if (props.contains(OTHER_SETTINGS_JSON_PROP)) {
                JsonObject otherSettingsJsonObject =
                        info.mJsonObject.getJsonObject(OTHER_SETTINGS_JSON_PROP);
                if (!validateSettingsClusterJson(MISC_PREF_KEYS, otherSettingsJsonObject, info,
                        context)) {
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

    private static boolean validateGroupJson(JsonObject groupJsonObject, ImportFileInfo info,
                                             int groupIndex, Context context,
                                             GroupTransferInfo groupInfo)
            throws JSONException {
        Map<String, String> namesMap = new HashMap<>();
        List<FieldTransferInfo> fields = new ArrayList<>();

        UnusedPropertyTracker props = new UnusedPropertyTracker(groupJsonObject);

        if (props.contains(FIELDS_JSON_PROP)) {
            JsonArray fieldsJsonArray = groupJsonObject.getJsonArray(FIELDS_JSON_PROP);
            for (int i = 0; i < fieldsJsonArray.length(); i++) {
                FieldTransferInfo fieldInfo = new FieldTransferInfo();
                JsonObject fieldJsonObject = fieldsJsonArray.getJsonObject(i);
                if (!validateFieldJson(fieldJsonObject, info, i, context, fieldInfo)) {
                    return false;
                }
                fields.add(fieldInfo);
            }
        }

        for (String testGroupPrefKeyPrefix : TEST_GROUP_PREF_KEY_PREFIXES) {
            String jsonProp = prefKeyPrefixToJsonName(testGroupPrefKeyPrefix);
            if (props.contains(jsonProp)) {
                if (!testGroupPrefKeyPrefix.equals(PREF_TEST_FIELD_IDS_PREFIX)) {
                    validatePropValue(groupJsonObject, jsonProp, testGroupPrefKeyPrefix, info,
                            context, namesMap);
                }
            }
        }

        for (String prop : props.getUnusedProperties()) {
            info.mUnexpectedProps.add(groupJsonObject.fullPath(prop));
        }

        groupInfo.setFields(fields);
        String name = getName(namesMap, new String[] { PREF_TEST_GROUP_NAME_PREFIX });
        groupInfo.setName(name != null
                ? name
                : context.getString(R.string.test_group_default_name, groupIndex + 1));
        return true;
    }

    private static boolean validateFieldJson(JsonObject fieldJsonObject, ImportFileInfo info,
                                             int fieldIndex, Context context,
                                             FieldTransferInfo fieldInfo) {
        Map<String, String> namesMap = new HashMap<>();

        UnusedPropertyTracker props = new UnusedPropertyTracker(fieldJsonObject);

        for (String testFieldPrefKeyPrefix : TEST_FIELD_PREF_KEY_PREFIXES) {
            String jsonProp = prefKeyPrefixToJsonName(testFieldPrefKeyPrefix);
            if (props.contains(jsonProp)) {
                validatePropValue(fieldJsonObject, jsonProp, testFieldPrefKeyPrefix, info, context,
                        namesMap);
            }
        }

        for (String defaultableTestFieldPrefKeyPrefix : DEFAULTABLE_TEST_FIELD_PREF_KEY_PREFIXES) {
            String jsonProp = prefKeyPrefixToJsonName(defaultableTestFieldPrefKeyPrefix);
            if (props.contains(jsonProp)) {
                validatePropValue(fieldJsonObject, jsonProp, defaultableTestFieldPrefKeyPrefix,
                        info, context, null);
            }
        }

        for (String prop : props.getUnusedProperties()) {
            info.mUnexpectedProps.add(fieldJsonObject.fullPath(prop));
        }

        String name = getName(namesMap, new String[] {
                PREF_IME_LABEL_TEXT_PREFIX,
                PREF_IME_DEFAULT_TEXT_PREFIX,
                PREF_IME_HINT_TEXT_PREFIX
        });
        fieldInfo.setName(name != null
                ? name
                : context.getString(R.string.test_field_default_name, fieldIndex + 1));
        return true;
    }

    private static boolean validateSettingsClusterJson(String[] keyOrKeyPrefixArray,
                                                       JsonObject jsonObject,
                                                       ImportFileInfo info,
                                                       Context context) {
        UnusedPropertyTracker props = new UnusedPropertyTracker(jsonObject);

        for (String prefKeyPrefix : keyOrKeyPrefixArray) {
            String jsonProp = prefKeyPrefixToJsonName(prefKeyPrefix);
            if (props.contains(jsonProp)) {
                validatePropValue(jsonObject, jsonProp, prefKeyPrefix, info, context, null);
            }
        }

        for (String prop : props.getUnusedProperties()) {
            info.mUnexpectedProps.add(jsonObject.fullPath(prop));
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

    private static void loadPrefData(JsonObject jsonObject, String jsonPropName,
                                     PreferenceKey prefKey, @NonNull Context context) {
        if (prefKey == null) {
            return;
        }
        Consumer<String> prefSetter = validatePropValue(jsonObject, jsonPropName, prefKey.getStem(),
                null, context, null);
        if (prefSetter != null) {
            prefSetter.accept(prefKey.toString());
        }
    }

    private static Consumer<String> validatePropValue(JsonObject jsonObject, String jsonPropName,
                                                      String prefKeyOrPrefix,
                                                      @Nullable ImportFileInfo info,
                                                      @NonNull Context context,
                                                      @Nullable Map<String, String> namesMap) {
        int dataType = PreferenceReader.prefDataType(prefKeyOrPrefix);
        try {
            switch (dataType) {
                case PreferenceReader.TYPE_BOOLEAN:
                    return validateBoolean(jsonObject, jsonPropName, prefKeyOrPrefix);
                case PreferenceReader.TYPE_INT:
                    return validateInt(jsonObject, jsonPropName, prefKeyOrPrefix, info, context);
                case PreferenceReader.TYPE_LONG:
                    return validateLong(jsonObject, jsonPropName, prefKeyOrPrefix);
                case PreferenceReader.TYPE_FLOAT:
                    return validateFloat(jsonObject, jsonPropName, prefKeyOrPrefix, info, context);
                case PreferenceReader.TYPE_STRING:
                    return validateString(jsonObject, jsonPropName, prefKeyOrPrefix, info, context,
                            namesMap);
                case PreferenceReader.TYPE_SPANNED:
                    return validateSpanned(jsonObject, jsonPropName, prefKeyOrPrefix);
                case PreferenceReader.TYPE_CHAR_SEQUENCE:
                    return validateCharSequence(jsonObject, jsonPropName, prefKeyOrPrefix,
                            namesMap);
                case PreferenceReader.TYPE_INT_ARRAY:
                    return validateIntArray(jsonObject, jsonPropName, prefKeyOrPrefix);
                case PreferenceReader.TYPE_STRING_ARRAY:
                    return validateStringArray(jsonObject, jsonPropName, prefKeyOrPrefix);
                case PreferenceReader.TYPE_INT_RANGE:
                    return validateIntRange(jsonObject, jsonPropName, prefKeyOrPrefix, info,
                            context);
                case PreferenceReader.TYPE_LOCALE_ARRAY:
                    return validateLocaleArray(jsonObject, jsonPropName, prefKeyOrPrefix, info,
                            context);
                case PreferenceReader.TYPE_TEXT_LIST_STRING:
                    return validateTextListString(jsonObject, jsonPropName, prefKeyOrPrefix, info,
                            context);
                case PreferenceReader.TYPE_TEXT_LIST_TRANSLATE_TEXT:
                    return validateTextListTranslateText(jsonObject, jsonPropName, prefKeyOrPrefix,
                            info, context);
                case PreferenceReader.TYPE_UNKNOWN:
                default:
                    //TODO: (EW) probably handle gracefully, but hard crash for now to catch issues
                    throw new RuntimeException("Unknown data type for " + prefKeyOrPrefix);
            }
        } catch (JSONException e) {
            logJsonException(e, jsonObject.fullPath(jsonPropName), info, context);
            return null;
        }
    }

    private static Consumer<String> validateBoolean(JsonObject jsonObject, String jsonPropName,
                                                    String prefKeyOrPrefix)
            throws JSONException {
        // just need to try getting the data for basic types to ensure the right data type is set
        boolean value = jsonObject.getBoolean(jsonPropName);
        return preferenceSetter(prefKeyOrPrefix, value, SharedPreferenceManager::setBoolean);
    }

    private static Consumer<String> validateInt(JsonObject jsonObject, String jsonPropName,
                                                String prefKeyOrPrefix,
                                                @Nullable ImportFileInfo info,
                                                @NonNull Context context)
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
            String fullPath = jsonObject.fullPath(jsonPropName);
            if (value < minValue || value > maxValue) {
                Log.e(TAG, fullPath + " ( " + value + ") isn't in the range "
                        + minValue + " - " + maxValue);
                if (info != null) {
                    info.mWarnings.add(context.getString(R.string.value_not_in_range,
                            fullPath, value, minValue, maxValue));
                }
                return null;
            } else {
                Log.w(TAG, fullPath + " has an int ( " + value
                        + ") that doesn't conform to the constraints: min=" + minValue
                        + ", max=" + maxValue + ", step=" + stepValue);
                if (info != null) {
                    info.mWarnings.add(context.getString(R.string.invalid_step_value, fullPath));
                }
                // it should be relatively safe to just shift to the nearest step
                value = constrainedIntData;
            }
        }
        return preferenceSetter(prefKeyOrPrefix, value, SharedPreferenceManager::setInt);
    }

    private static Consumer<String> validateLong(JsonObject jsonObject, String jsonPropName,
                                                 String prefKeyOrPrefix)
            throws JSONException {
        long value = jsonObject.getLong(jsonPropName);
        return preferenceSetter(prefKeyOrPrefix, value, SharedPreferenceManager::setLong);
    }

    private static Consumer<String> validateFloat(JsonObject jsonObject, String jsonPropName,
                                                  String prefKeyOrPrefix,
                                                  @Nullable ImportFileInfo info,
                                                  @NonNull Context context)
            throws JSONException {
        double value = jsonObject.getDouble(jsonPropName);
        if (value > Float.MAX_VALUE || value < Float.MIN_VALUE) {
            String fullPath = jsonObject.fullPath(jsonPropName);
            Log.e(TAG, value + " isn't a valid float for " + fullPath);
            if (info != null) {
                info.mWarnings.add(
                        context.getString(R.string.invalid_float_data, fullPath));
            }
            return null;
        }
        return preferenceSetter(prefKeyOrPrefix, (float) value, SharedPreferenceManager::setFloat);
    }

    private static Consumer<String> validateString(JsonObject jsonObject, String jsonPropName,
                                                   String prefKeyOrPrefix,
                                                   @Nullable ImportFileInfo info,
                                                   @NonNull Context context,
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
            String fullPath = jsonObject.fullPath(jsonPropName);
            Log.e(TAG, fullPath + " has an invalid value: " + value);
            if (info != null) {
                info.mWarnings.add(context.getString(R.string.invalid_value,
                        fullPath, value));
            }
            return null;
        }
        if (namesMap != null) {
            switch (prefKeyOrPrefix) {
                case PREF_TEST_GROUP_NAME_PREFIX:
                    namesMap.put(prefKeyOrPrefix, value);
            }
        }
        return preferenceSetter(prefKeyOrPrefix, value, SharedPreferenceManager::setString);
    }

    private static Consumer<String> validateSpanned(JsonObject jsonObject, String jsonPropName,
                                                    String prefKeyOrPrefix)
            throws JSONException {
        Spanned value = getSpanned(jsonObject, jsonPropName);
        return preferenceSetter(prefKeyOrPrefix, value, SharedPreferenceManager::setSpanned);
    }

    private static Consumer<String> validateCharSequence(JsonObject jsonObject, String jsonPropName,
                                                         String prefKeyOrPrefix,
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
        if (namesMap != null) {
            switch (prefKeyOrPrefix) {
                case PREF_IME_LABEL_TEXT_PREFIX:
                case PREF_IME_DEFAULT_TEXT_PREFIX:
                case PREF_IME_HINT_TEXT_PREFIX:
                    namesMap.put(prefKeyOrPrefix, value == null ? null : value.toString());
            }
        }
        return preferenceSetter(prefKeyOrPrefix, value, SharedPreferenceManager::setCharSequence);
    }

    private static Consumer<String> validateIntArray(JsonObject jsonObject, String jsonPropName,
                                                     String prefKeyOrPrefix)
            throws JSONException {
        int[] value = getIntArray(jsonObject, jsonPropName);
        return preferenceSetter(prefKeyOrPrefix, value, SharedPreferenceManager::setIntArray);
    }

    private static Consumer<String> validateStringArray(JsonObject jsonObject, String jsonPropName,
                                                        String prefKeyOrPrefix)
            throws JSONException {
        String[] value = getStringArray(jsonObject, jsonPropName);
        return preferenceSetter(prefKeyOrPrefix, value, SharedPreferenceManager::setStringArray);
    }

    private static Consumer<String> validateIntRange(JsonObject jsonObject, String jsonPropName,
                                                     String prefKeyOrPrefix,
                                                     @Nullable ImportFileInfo info,
                                                     @NonNull Context context)
            throws JSONException {
        int[] value = getIntArray(jsonObject, jsonPropName);
        if (value != null && value.length != 2) {
            String fullPath = jsonObject.fullPath(jsonPropName);
            Log.e(TAG, fullPath + " doesn't have exactly 2 values: " + Arrays.toString(value));
            if (info != null) {
                info.mWarnings.add(context.getString(R.string.invalid_data, fullPath));
            }
            return null;
        }
        IntRange range = value == null
                ? null
                : new IntRange(value[0], value[1]);
        if (prefKeyOrPrefix.equals(PREF_RESTRICT_RANGE_PREFIX)) {
            String fullPath = jsonObject.fullPath(jsonPropName);
            if (!CodepointRangeDialogPreference.isValidRange(range)) {
                Log.e(TAG, fullPath + " contains an invalid codepoint range: " + range);
                if (info != null) {
                    info.mWarnings.add(context.getString(R.string.invalid_data, fullPath));
                }
                return null;
            }
        }
        return preferenceSetter(prefKeyOrPrefix, range, IntRangeDataManager::new);
    }

    private static Consumer<String> validateLocaleArray(JsonObject jsonObject, String jsonPropName,
                                                        String prefKeyOrPrefix,
                                                        @Nullable ImportFileInfo info,
                                                        @NonNull Context context)
            throws JSONException {
        String[] localStrings = getStringArray(jsonObject, jsonPropName);
        int localeCount = localStrings == null ? 0 : localStrings.length;
        List<Locale> localeList = new ArrayList<>();
        for (int i = 0; i < localeCount; i++) {
            if (!LocaleEntryListPreference.isValidLocale(localStrings[i])) {
                String fullPath = jsonObject.fullPath(jsonPropName);
                Log.e(TAG, fullPath + "[" + i + "] doesn't have a valid locale string: "
                        + localStrings[i]);
                if (info != null) {
                    info.mWarnings.add(context.getString(R.string.invalid_locale,
                            fullPath + "[" + i + "]", localStrings[i]));
                }
                continue;
            }
            localeList.add(LocaleArrayDataManager.constructLocaleFromString(localStrings[i]));
        }
        Locale[] localeArray = localeList.toArray(new Locale[0]);
        return preferenceSetter(prefKeyOrPrefix, localeArray, LocaleArrayDataManager::new);
    }

    private static Consumer<String> validateTextListString(JsonObject jsonObject,
                                                           String jsonPropName,
                                                           String prefKeyOrPrefix,
                                                           @Nullable ImportFileInfo info,
                                                           @NonNull Context context)
            throws JSONException {
        JsonObject textListJsonObject = jsonObject.getJsonObject(jsonPropName);
        String[] stringArray = getStringArray(textListJsonObject, TEXT_LIST_DATA_ARRAY_JSON_PROP);
        if (stringArray == null) {
            String fullPath = jsonObject.fullPath(jsonPropName);
            Log.e(TAG, fullPath + "." + TEXT_LIST_DATA_ARRAY_JSON_PROP + " is null");
            if (info != null) {
                info.mWarnings.add(context.getString(R.string.null_data,
                        fullPath + "." + TEXT_LIST_DATA_ARRAY_JSON_PROP));
            }
            return null;
        }
        TextList<String> textList = new TextList<>(
                stringArray,
                textListJsonObject.getBoolean(TEXT_LIST_ESCAPE_CHARS_JSON_PROP));
        return preferenceSetter(prefKeyOrPrefix, textList, StringTextListDataManager::new);
    }

    private static Consumer<String> validateTextListTranslateText(JsonObject jsonObject,
                                                                  String jsonPropName,
                                                                  String prefKeyOrPrefix,
                                                                  @Nullable ImportFileInfo info,
                                                                  @NonNull Context context)
            throws JSONException {
        JsonObject textListJsonObject = jsonObject.getJsonObject(jsonPropName);
        JsonObject[] translateTextJsonObjects = getJsonObjectArray(textListJsonObject,
                TEXT_LIST_DATA_ARRAY_JSON_PROP);
        if (translateTextJsonObjects == null) {
            String fullPath = jsonObject.fullPath(jsonPropName);
            Log.e(TAG, fullPath + "." + TEXT_LIST_DATA_ARRAY_JSON_PROP + " is null");
            if (info != null) {
                info.mWarnings.add(context.getString(R.string.null_data,
                        fullPath + "." + TEXT_LIST_DATA_ARRAY_JSON_PROP));
            }
            return null;
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
        return preferenceSetter(prefKeyOrPrefix, textListTranslateTextData,
                TranslateTextTextListDataManager::new);
    }

    private static <T> Consumer<String> preferenceSetter(String prefKeyOrPrefix, T value,
            TriConsumer<SharedPreferenceManager, String, T> setPref) {
        return (prefKey) -> {
            if (prefKey == null || !prefKey.startsWith(prefKeyOrPrefix)) {
                Log.e(TAG, "Unexpected preference key " + prefKey
                        + ". It should start with " + prefKeyOrPrefix);
                return;
            }
            Spanned defaultValue = PreferenceReader.getPrefDefaultSpanned(prefKeyOrPrefix);
            if (IMPORT_DEFAULT_PREFS_VALUES
                    || !SharedPreferenceManager.equals(value, defaultValue)) {
                SharedPreferenceManager prefs = Settings.getInstance().getPrefManager();
                setPref.accept(prefs, prefKey, value);
            }
        };
    }

    private static <T> Consumer<String> preferenceSetter(String prefKeyOrPrefix, T value,
            BiFunction<SharedPreferenceManager, String, DataManager<T>> getDataManager) {
        return (prefKey) -> {
            if (prefKey == null || !prefKey.startsWith(prefKeyOrPrefix)) {
                Log.e(TAG, "Unexpected preference key " + prefKey
                        + ". It should start with " + prefKeyOrPrefix);
                return;
            }
            SharedPreferenceManager prefs = Settings.getInstance().getPrefManager();
            DataManager<T> dataManager = getDataManager.apply(prefs, prefKey);
            T defaultValue = dataManager.readDefaultValue();
            if (IMPORT_DEFAULT_PREFS_VALUES
                    || !SharedPreferenceManager.equals(value, defaultValue)) {
                dataManager.writeValue(value);
            }
        };
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

    private static String getString(JsonObject jsonObject, String jsonPropName)
            throws JSONException {
        if (jsonObject.isNull(jsonPropName)) {
            return null;
        }
        return jsonObject.getString(jsonPropName);
    }

    private static JsonObject[] getJsonObjectArray(JsonObject jsonObject, String jsonPropName)
            throws JSONException {
        if (jsonObject.isNull(jsonPropName)) {
            return null;
        }
        JsonArray jsonArray = jsonObject.getJsonArray(jsonPropName);
        JsonObject[] result = new JsonObject[jsonArray.length()];
        for (int i = 0; i < result.length; i++) {
            result[i] = jsonArray.getJsonObject(i);
        }
        return result;
    }

    private static String[] getStringArray(JsonObject jsonObject, String jsonPropName)
            throws JSONException {
        if (jsonObject.isNull(jsonPropName)) {
            return null;
        }
        JsonArray jsonArray = jsonObject.getJsonArray(jsonPropName);
        String[] result = new String[jsonArray.length()];
        for (int i = 0; i < result.length; i++) {
            result[i] = jsonArray.getString(i);
        }
        return result;
    }

    private static int[] getIntArray(JsonObject jsonObject, String jsonPropName)
            throws JSONException {
        if (jsonObject.isNull(jsonPropName)) {
            return null;
        }
        JsonArray jsonArray = jsonObject.getJsonArray(jsonPropName);
        int[] result = new int[jsonArray.length()];
        for (int i = 0; i < result.length; i++) {
            result[i] = jsonArray.getInt(i);
        }
        return result;
    }

    private static Spanned getSpanned(JsonObject jsonObject, String jsonPropName)
            throws JSONException {
        if (jsonObject.isNull(jsonPropName)) {
            return null;
        }
        JsonObject dataJsonObject = jsonObject.getJsonObject(jsonPropName);
        int dataFormat = dataJsonObject.getInt(CUSTOM_OBJECT_DATA_FORMAT_JSON_PROP);
        if (dataFormat == DATA_FORMAT_SPANNED) {
            String[] spannedInfo = getStringArray(dataJsonObject, CUSTOM_OBJECT_DATA_JSON_PROP);
            return SharedPreferenceManager.buildSpanned(spannedInfo);
        }
        throw new JSONException(UNEXPECTED_CUSTOM_OBJECT_MESSAGE);
    }

    public static void importSettings(JsonObject jsonObject, boolean replaceFieldDefaults,
                                      boolean replaceFields, List<GroupTransferInfo> groupInfoList,
                                      boolean embedFieldDefaults, boolean replaceOtherSettings,
                                      Context context) {
        Settings instance = Settings.getInstance();
        SharedPreferenceManager prefs = instance.getPrefManager();
        prefs.unregisterOnSharedPreferenceChangeListener(instance);
        try {
            JsonObject fieldDefaultsJsonObject =
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
                JsonArray groupsJsonArray = jsonObject.getJsonArray(GROUPS_JSON_PROP);

                for (int i = 0; i < groupsJsonArray.length(); i++) {
                    JsonObject groupJsonObject = groupsJsonArray.getJsonObject(i);

                    importGroupJson(groupJsonObject, groupInfoList.get(i), groupIds, fieldIds,
                            embedFieldDefaults, fieldDefaultsJsonObject, context);
                }

                prefs.setIntArray(PREF_TEST_GROUP_IDS, toPrimitiveArray(groupIds));
            } else if (jsonObject.has(FIELDS_JSON_PROP) && groupInfoList.size() == 1) {
                // build the ad-hoc group to load
                JsonObject groupJsonObject = new JsonObject();
                JsonArray looseFieldsJsonArray = jsonObject.getJsonArray(FIELDS_JSON_PROP);
                groupJsonObject.put(FIELDS_JSON_PROP, looseFieldsJsonArray);

                importGroupJson(groupJsonObject, groupInfoList.get(0), groupIds, fieldIds,
                        embedFieldDefaults, fieldDefaultsJsonObject, context);

                prefs.setIntArray(PREF_TEST_GROUP_IDS, toPrimitiveArray(groupIds));
            }

            if (replaceFieldDefaults) {
                for (String prefKeyPrefix : DEFAULTABLE_TEST_FIELD_PREF_KEY_PREFIXES) {
                    PreferenceKey prefKey = PreferenceKey.createFieldDefaultKey(prefKeyPrefix);
                    prefs.remove(prefKey.toString());
                    if (fieldDefaultsJsonObject != null) {
                        String jsonProp = prefKeyPrefixToJsonName(prefKeyPrefix);
                        if (fieldDefaultsJsonObject.has(jsonProp)) {
                            loadPrefData(fieldDefaultsJsonObject, jsonProp, prefKey, context);
                        }
                    }
                }
            }

            if (replaceOtherSettings) {
                JsonObject otherSettingsJsonObject =
                        getJsonObject(jsonObject, OTHER_SETTINGS_JSON_PROP);
                for (String prefKey : MISC_PREF_KEYS) {
                    prefs.remove(prefKey);
                    if (otherSettingsJsonObject != null) {
                        String jsonProp = prefKeyPrefixToJsonName(prefKey);
                        if (otherSettingsJsonObject.has(jsonProp)) {
                            loadPrefData(otherSettingsJsonObject, jsonProp,
                                    PreferenceKey.createBasicKey(prefKey), context);
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

    private static void importGroupJson(JsonObject groupJsonObject,
                                        GroupTransferInfo groupInfo, List<Integer> groupIds,
                                        List<Integer> fieldIds, boolean embedFieldDefaults,
                                        JsonObject fieldDefaultsJsonObject, Context context)
            throws JSONException {
        int groupId;
        if (groupInfo.isIncluded()) {
            groupId = Settings.getNextId(groupIds);
            groupIds.add(groupId);
        } else {
            // add any fields to the last group
            groupId = Settings.getTestGroupId(Settings.getTestFieldGroupCount() - 1);
        }
        addGroupFromJson(groupJsonObject, groupId,
                fieldIds, groupInfo, embedFieldDefaults, fieldDefaultsJsonObject,
                context);
    }

    private static void addGroupFromJson(JsonObject groupJsonObject, int groupId,
                                         List<Integer> fieldIds, GroupTransferInfo groupInfo,
                                         boolean embedFieldDefaults,
                                         JsonObject fieldDefaultsJsonObject,
                                         Context context)
            throws JSONException {
        SharedPreferenceManager prefs = Settings.getInstance().getPrefManager();
        PreferenceReader preferenceReader = Settings.getInstance().getPreferenceReader();

        if (groupJsonObject.has(FIELDS_JSON_PROP)) {
            JsonArray fieldsJsonArray = groupJsonObject.getJsonArray(FIELDS_JSON_PROP);

            List<Integer> groupFieldIds = new ArrayList<>();
            for (int i = 0; i < fieldsJsonArray.length(); i++) {
                if (!groupInfo.getFields().get(i).isIncluded()) {
                    continue;
                }
                int fieldId = Settings.getNextId(fieldIds);
                fieldIds.add(fieldId);
                groupFieldIds.add(fieldId);
                JsonObject fieldJsonObject = fieldsJsonArray.getJsonObject(i);
                addFieldFromJson(fieldJsonObject, fieldId, embedFieldDefaults,
                        fieldDefaultsJsonObject, context);
            }

            int[] fieldIdsArray;
            if (groupInfo.isIncluded()) {
                fieldIdsArray = toPrimitiveArray(groupFieldIds);
            } else {
                fieldIdsArray = ArrayUtils.join(
                        getFieldIds(groupId, preferenceReader),
                        toPrimitiveArray(groupFieldIds));
            }
            prefs.setIntArray(
                    PreferenceKey.createGroupKey(PREF_TEST_FIELD_IDS_PREFIX, groupId).toString(),
                    fieldIdsArray);
        }

        if (groupInfo.isIncluded()) {
            for (String prefKeyPrefix : TEST_GROUP_PREF_KEY_PREFIXES) {
                if (prefKeyPrefix.equals(PREF_TEST_FIELD_IDS_PREFIX)) {
                    continue;
                }

                PreferenceKey prefKey = PreferenceKey.createGroupKey(prefKeyPrefix, groupId);
                prefs.remove(prefKey.toString());
                String jsonProp = prefKeyPrefixToJsonName(prefKeyPrefix);
                if (groupJsonObject.has(jsonProp)) {
                    loadPrefData(groupJsonObject, jsonProp, prefKey, context);
                }
            }
        }
    }

    private static void addFieldFromJson(JsonObject fieldJsonObject, int fieldId,
                                         boolean embedFieldDefaults,
                                         JsonObject fieldDefaultsJsonObject, Context context) {
        SharedPreferenceManager prefs = Settings.getInstance().getPrefManager();

        for (String fieldPrefKeyPrefix : TEST_FIELD_PREF_KEY_PREFIXES) {
            PreferenceKey fieldPrefKey = PreferenceKey.createFieldKey(fieldPrefKeyPrefix, fieldId);
            prefs.remove(fieldPrefKey.toString());
            String jsonProp = prefKeyPrefixToJsonName(fieldPrefKeyPrefix);

            if (DEFAULT_OVERRIDE_PREF_PREFIX_MAP.containsKey(fieldPrefKeyPrefix)) {
                JsonObject defaultableValueJsonObject;
                // embed defaults if requested and the field doesn't already override them,
                // otherwise just save the override values
                if (embedFieldDefaults
                        && !tryGetBoolean(fieldJsonObject, jsonProp, false)) {
                    defaultableValueJsonObject = fieldDefaultsJsonObject;
                    prefs.setBoolean(fieldPrefKey.toString(), true);
                } else {
                    defaultableValueJsonObject = fieldJsonObject;
                    if (fieldJsonObject.has(jsonProp)) {
                        loadPrefData(fieldJsonObject, jsonProp, fieldPrefKey, context);
                    }
                }
                String[] fieldDefaultsPrefKeys =
                        DEFAULT_OVERRIDE_PREF_PREFIX_MAP.get(fieldPrefKeyPrefix);
                for (String fieldDefaultPrefKeyPrefix : fieldDefaultsPrefKeys) {
                    PreferenceKey fieldDefaultPrefKey =
                            PreferenceKey.createFieldKey(fieldDefaultPrefKeyPrefix, fieldId);
                    String fieldDefaultJsonProp =
                            prefKeyPrefixToJsonName(fieldDefaultPrefKeyPrefix);
                    if (defaultableValueJsonObject.has(fieldDefaultJsonProp)) {
                        loadPrefData(defaultableValueJsonObject, fieldDefaultJsonProp,
                                fieldDefaultPrefKey, context);
                    }
                }
            } else {
                if (fieldJsonObject.has(jsonProp)) {
                    loadPrefData(fieldJsonObject, jsonProp, fieldPrefKey, context);
                }
            }
        }
    }

    private static boolean tryGetBoolean(JsonObject jsonObject, String prop, boolean defaultValue) {
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
