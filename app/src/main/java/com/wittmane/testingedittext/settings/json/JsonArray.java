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

import static com.wittmane.testingedittext.settings.json.JsonObject.toInternalJsonType;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Wrapper class for {@link JSONArray} to handle {@code null} in a more normal way, support adding
 * arrays, collections, and spanned objects more easily, and track the path that objects were taken
 * from.
 */
public class JsonArray {

    /* package*/ final JSONArray mJsonArray;
    /* package*/ String mPath;

    /* package*/ JsonArray(JSONArray jsonArray, String path) {
        mJsonArray = jsonArray;
        mPath = path;
    }

    /* package*/ JsonArray(JSONArray jsonArray) {
        this(jsonArray, null);
    }

    public JsonArray() {
        this(new JSONArray(), null);
    }

    public JsonArray(String rawJson) throws JSONException {
        this(new JSONArray(rawJson));
    }

    public int length() {
        return mJsonArray.length();
    }

    public JsonArray put(boolean value) {
        mJsonArray.put(value);
        return this;
    }

    public JsonArray put(double value) throws JSONException {
        mJsonArray.put(value);
        return this;
    }

    public JsonArray put(int value) {
        mJsonArray.put(value);
        return this;
    }

    public JsonArray put(long value) {
        mJsonArray.put(value);
        return this;
    }

    public JsonArray put(Object value) throws JSONException {
        mJsonArray.put(toInternalJsonType(value));
        return this;
    }

    public JsonArray put(int index, boolean value) throws JSONException {
        mJsonArray.put(index, value);
        return this;
    }

    public JsonArray put(int index, double value) throws JSONException {
        mJsonArray.put(index, value);
        return this;
    }

    public JsonArray put(int index, int value) throws JSONException {
        mJsonArray.put(index, value);
        return this;
    }

    public JsonArray put(int index, long value) throws JSONException {
        mJsonArray.put(index, value);
        return this;
    }

    public JsonArray put(int index, Object value) throws JSONException {
        mJsonArray.put(index, toInternalJsonType(value));
        return this;
    }

    public boolean isNull(int index) {
        return mJsonArray.isNull(index);
    }

    public Object get(int index) throws JSONException {
        return wrapIfNeeded(mJsonArray.get(index), index);
    }

    public Object remove(int index) {
        return wrapIfNeeded(mJsonArray.remove(index), index);
    }

    public boolean getBoolean(int index) throws JSONException {
        return mJsonArray.getBoolean(index);
    }

    public double getDouble(int index) throws JSONException {
        return mJsonArray.getDouble(index);
    }

    public int getInt(int index) throws JSONException {
        return mJsonArray.getInt(index);
    }

    public long getLong(int index) throws JSONException {
        return mJsonArray.getLong(index);
    }

    public String getString(int index) throws JSONException {
        return mJsonArray.getString(index);
    }

    public JsonArray getJSONArray(int index) throws JSONException {
        return wrap(mJsonArray.getJSONArray(index), index);
    }

    public JsonObject getJsonObject(int index) throws JSONException {
        return wrap(mJsonArray.getJSONObject(index), index);
    }

    @NonNull
    @Override
    public String toString() {
        return mJsonArray.toString();
    }

    private Object wrapIfNeeded(Object object, int index) {
        if (object instanceof JSONObject) {
            return wrap((JSONObject) object, index);
        }
        if (object instanceof JSONArray) {
            return wrap((JSONArray) object, index);
        }
        if (object == JSONObject.NULL) {
            return null;
        }
        return object;
    }

    private JsonObject wrap(JSONObject jsonObject, int index) {
        return new JsonObject(jsonObject, fullPath(index));
    }

    private JsonArray wrap(JSONArray jsonArray, int index) {
        return new JsonArray(jsonArray, fullPath(index));
    }

    public String fullPath(int index) {
        return (mPath == null ? "" : mPath) + "[" + index + "]";
    }
}
