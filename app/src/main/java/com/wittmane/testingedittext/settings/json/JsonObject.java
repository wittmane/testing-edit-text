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

package com.wittmane.testingedittext.settings.json;

import android.text.Spanned;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wittmane.testingedittext.settings.SharedPreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;

/**
 * Wrapper class for {@link JSONObject} to handle {@code null} in a more normal way, support adding
 * arrays, collections, and spanned objects more easily, and track the path that objects were taken
 * from.
 */
public class JsonObject {

    private static final String CUSTOM_OBJECT_DATA_FORMAT_JSON_PROP = "format";
    private static final String CUSTOM_OBJECT_DATA_JSON_PROP = "data";

    private static final int DATA_FORMAT_SPANNED = 1;

    /* package*/ final JSONObject mJsonObject;
    /* package*/ String mPath;

    /* package */ JsonObject(JSONObject jsonObject, String path) {
        mJsonObject = jsonObject;
        mPath = path;
    }

    public JsonObject() {
        this(new JSONObject(), null);
    }

    public JsonObject(String rawJson) throws JSONException {
        this(new JSONObject(rawJson), null);
    }

    public int length() {
        return mJsonObject.length();
    }

    @NonNull
    public JsonObject put(@NonNull String name, boolean value) throws JSONException {
        mJsonObject.put(name, value);
        return this;
    }

    @NonNull
    public JsonObject put(@NonNull String name, double value) throws JSONException {
        mJsonObject.put(name, value);
        return this;
    }

    @NonNull
    public JsonObject put(@NonNull String name, int value) throws JSONException {
        mJsonObject.put(name, value);
        return this;
    }

    @NonNull
    public JsonObject put(@NonNull String name, long value) throws JSONException {
        mJsonObject.put(name, value);
        return this;
    }

    @NonNull
    public JsonObject put(@NonNull String name, @Nullable Object value) throws JSONException {
        mJsonObject.put(name, toInternalJsonType(value));
        return this;
    }

    /* package */ static Object toInternalJsonType(@Nullable Object value) throws JSONException {
        if (value == null) {
            return JSONObject.NULL;
        }
        if (value instanceof Collection) {
            return new JSONArray((Collection<?>) value);
        }
        if (value.getClass().isArray()) {
            JSONArray jsonArray = new JSONArray();
            final int length = Array.getLength(value);
            for (int i = 0; i < length; ++i) {
                jsonArray.put(toInternalJsonType(Array.get(value, i)));
            }
            return jsonArray;
        }
        if (value instanceof Spanned) {
            // embed an indication of what data this holds so that if other CharSequence types are
            // supported in the future or we find a better way to export the data, we can maintain
            // compatibility between varying versions between the exporting and importing app
            JSONObject dataJsonObject = new JSONObject();
            dataJsonObject.put(CUSTOM_OBJECT_DATA_FORMAT_JSON_PROP, DATA_FORMAT_SPANNED);
            // get the data that SharedPreferenceManager uses to save spanned objects
            dataJsonObject.put(CUSTOM_OBJECT_DATA_JSON_PROP,
                    toInternalJsonType(SharedPreferenceManager.getSpannedInfo((Spanned)value)));
            return dataJsonObject;
        }
        if (value instanceof CharSequence) {
            return value.toString();
        }
        if (value instanceof JsonObject) {
            return ((JsonObject) value).mJsonObject;
        }
        if (value instanceof JsonArray) {
            return ((JsonArray) value).mJsonArray;
        }
        return value;
    }


    @Nullable
    public Object remove(@Nullable String name) {
        return wrapIfNeeded(mJsonObject.remove(name), name);
    }

    public boolean isNull(@Nullable String name) {
        return mJsonObject.isNull(name);
    }

    public boolean has(@Nullable String name) {
        return mJsonObject.has(name);
    }

    @Nullable
    public Object get(@NonNull String name) throws JSONException {
        return wrapIfNeeded(mJsonObject.get(name), name);
    }

    public boolean getBoolean(@NonNull String name) throws JSONException {
        return mJsonObject.getBoolean(name);
    }

    public double getDouble(@NonNull String name) throws JSONException {
        return mJsonObject.getDouble(name);
    }

    public int getInt(@NonNull String name) throws JSONException {
        return mJsonObject.getInt(name);
    }

    public long getLong(@NonNull String name) throws JSONException {
        return mJsonObject.getLong(name);
    }

    @Nullable
    public String getString(@NonNull String name) throws JSONException {
        return mJsonObject.getString(name);
    }

    @NonNull
    public JsonArray getJsonArray(@NonNull String name) throws JSONException {
        return wrap(mJsonObject.getJSONArray(name), name);
    }

    @NonNull
    public JsonObject getJsonObject(@NonNull String name) throws JSONException {
        return wrap(mJsonObject.getJSONObject(name), name);
    }

    @NonNull
    public Iterator<String> keys() {
        return mJsonObject.keys();
    }

    @NonNull
    @Override
    public String toString() {
        return mJsonObject.toString();
    }

    private Object wrapIfNeeded(Object object, String propertyName) {
        if (object instanceof JSONObject) {
            return wrap((JSONObject) object, propertyName);
        }
        if (object instanceof JSONArray) {
            return wrap((JSONArray) object, propertyName);
        }
        if (object == JSONObject.NULL) {
            return null;
        }
        return object;
    }

    private JsonObject wrap(JSONObject jsonObject, String propertyName) {
        return new JsonObject(jsonObject, fullPath(propertyName));
    }

    private JsonArray wrap(JSONArray jsonArray, String propertyName) {
        return new JsonArray(jsonArray, fullPath(propertyName));
    }

    public String fullPath(String propertyName) {
        return (mPath == null ? "" : (mPath + ".")) + propertyName;
    }
}
