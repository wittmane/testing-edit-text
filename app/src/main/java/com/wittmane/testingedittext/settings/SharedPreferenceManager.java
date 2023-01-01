/*
 * Copyright (C) 2022 Eli Wittman
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

import android.content.SharedPreferences;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.wittmane.testingedittext.settings.StringArraySerializer.InvalidSerializedDataException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wrapper for {@link SharedPreferences} that has options for extra types and convenience methods.
 */
public class SharedPreferenceManager implements SharedPreferences {
    private static final String TAG = SharedPreferenceManager.class.getSimpleName();

    private static final String SPANNED_STRING_PREF_PREFIX =
            createTypePrefix("ca64cdf7e8164fd2ac8d6be6c23785e2");
    private static final String STRING_ARRAY_STRING_PREF_PREFIX =
            createTypePrefix("8ef6104ff54e4f0699a74a8e5b181ace");
    private static final String INT_ARRAY_STRING_PREF_PREFIX =
            createTypePrefix("99cc955a57ba4fc08ea6237afd4ba0b9");

    private final SharedPreferences mPrefs;

    public SharedPreferenceManager(SharedPreferences prefs) {
        mPrefs = prefs;
    }

    /**
     * Create a prefix for a special type to be stored in a string preference. This uses a uuid
     * (which should be unlikely for a real value stored in a string preference) converted to
     * unicode non-characters (which are intended for internal private use, meaning that it would be
     * extra unlikely for this to come from some user input) to create prefix to denote the internal
     * type that is stored in a string preference that should be incredibly unlikely to conflict
     * with a real value intended to be stored as a string.
     * @param uuid A uuid to identify the type and help ensure the prefix doesn't conflict with a
     *            normal string value.
     * @return The prefix to use for the string preference to denote a particular type.
     */
    private static String createTypePrefix(String uuid) {
        char[] prefixArray = new char[uuid.length() + 1];
        for (int i = 0; i < uuid.length(); i++) {
            int hexValue = getHexValue(uuid.charAt(i));
            // the contiguous range of 32 non-characters (U+FDD0 - U+FDEF) fits all of the hex
            // values to directly map
            prefixArray[i] = (char) ('\ufdd0' + hexValue);
        }
        // use a consistent end to the prefix
        prefixArray[prefixArray.length - 1] = '\uffff';
        return new String(prefixArray);
    }

    private static int getHexValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        throw new IllegalArgumentException("'" + c + "' is not a hex value");
    }

    private static String getSpecialTypeName(String prefValue) {
        if (prefValue == null) {
            return null;
        }
        if (prefValue.startsWith(SPANNED_STRING_PREF_PREFIX)) {
            return "Spanned";
        }
        if (prefValue.startsWith(STRING_ARRAY_STRING_PREF_PREFIX)) {
            return "String[]";
        }
        if (prefValue.startsWith(INT_ARRAY_STRING_PREF_PREFIX)) {
            return "int[]";
        }
        return null;
    }

    @Override
    public Map<String, ?> getAll() {
        return mPrefs.getAll();
    }

    @Nullable
    @Override
    public String getString(String key, @Nullable String defaultValue) {
        String value = mPrefs.getString(key, defaultValue);
        String specialTypeName = getSpecialTypeName(value);
        if (specialTypeName != null) {
            // this technically could be a false positive (although extremely unlikely), so it's
            // probably not worth hard crashing the application
            Log.e(TAG, "The value for " + key + " is typed as a " + specialTypeName
                    + " but is being read as a String");
        }
        return value;
    }

    @Nullable
    @Override
    public Set<String> getStringSet(String key, @Nullable Set<String> defaultValues) {
        return mPrefs.getStringSet(key, defaultValues);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        return mPrefs.getInt(key, defaultValue);
    }

    @Override
    public long getLong(String key, long defaultValue) {
        return mPrefs.getLong(key, defaultValue);
    }

    @Override
    public float getFloat(String key, float defaultValue) {
        return mPrefs.getFloat(key, defaultValue);
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        return mPrefs.getBoolean(key, defaultValue);
    }

    /**
     * Retrieve a Spanned value from the preferences.
     * See {@link #setSpanned}.
     * @param key The name of the preference to retrieve.
     * @param defaultValue Value to return if this preference does not exist.
     * @return Returns the preference value if it exists, or defaultValue. Throws ClassCastException
     *         if there is a preference with this name that is not a Spanned (technically stored as
     *         String).
     */
    @Nullable
    public Spanned getSpanned(String key, @Nullable Spanned defaultValue) {
        if (!contains(key)) {
            return defaultValue;
        }
        String rawValue = mPrefs.getString(key, null);
        if (rawValue == null || !rawValue.startsWith(SPANNED_STRING_PREF_PREFIX)) {
            throw new ClassCastException(key + " does not contain a Spanned");
        }
        String serializedSpannedInfo = rawValue.substring(SPANNED_STRING_PREF_PREFIX.length());
        return getSpannedInternal(serializedSpannedInfo, defaultValue, key);
    }

    private Spanned getSpannedInternal(String serializedSpannedInfo,
                                       @Nullable Spanned defaultValue, String key) {
        if (TextUtils.isEmpty(serializedSpannedInfo)) {
            // this shouldn't be null due to needing to prefix it, but if it's an empty string, that
            // means that no info for the spanned was included, which means it was set to null
            return null;
        }
        String[] spannedInfo;
        try {
            spannedInfo = StringArraySerializer.deserialize(serializedSpannedInfo);
        } catch (InvalidSerializedDataException e) {
            Log.e(TAG, "Failed to read Spanned from " + key + ": " + e.getMessage());
            // preference data is corrupt somehow. treat as if it was empty.
            return defaultValue;
        }
        if (spannedInfo == null) {
            return null;
        }
        if (spannedInfo.length < 1) {
            // StringArraySerializer.deserialize may return an empty array when the text is blank
            return new SpannedString("");
        }
        String baseText = spannedInfo[0];
        if (spannedInfo.length == 1) {
            // no html for building a spannable
            return new SpannedString(baseText);
        }
        String html = spannedInfo[1];
        SpannableStringBuilder spannedText = new SpannableStringBuilder(Html.fromHtml(html));
        // for some reason converting to html and back to a spanned adds new lines at the end, so
        // they need to be removed, and to be safe, this might as well just handle any other new
        // line mismatches that might occur too
        int i = 0;
        while (i < baseText.length() || i < spannedText.length()) {
            boolean baseCharIsNewLine = i < baseText.length() && baseText.charAt(i) == '\n';
            boolean spannedCharIsNewLine =
                    i < spannedText.length() && spannedText.charAt(i) == '\n';
            if (baseCharIsNewLine && !spannedCharIsNewLine) {
                // html dropped a newline, so it needs to be added
                spannedText.insert(i, "\n");
                i++;
            } else if (!baseCharIsNewLine && spannedCharIsNewLine) {
                // html inserted an extra newline, so it needs to be removed
                spannedText.delete(i, i + 1);
                // don't increment i because we may need to remove multiple adjacent new lines
            } else {
                i++;
            }
        }
        if (!spannedText.toString().equals(baseText)) {
            Log.e(TAG, "HTML spanned text doesn't match the base text: \nbase=\"" + baseText
                    + "\"\nspanned=\"" + spannedText.toString() + "\"");
            // prioritize accurate text over keeping spans
            return new SpannedString(baseText);
        }
        return spannedText;
    }

    /**
     * Retrieve a String array value from the preferences.
     * See {@link #setStringArray}.
     * @param key The name of the preference to retrieve.
     * @param defaultValue Value to return if this preference does not exist.
     * @return Returns the preference value if it exists, or defaultValue. Throws ClassCastException
     *         if there is a preference with this name that is not a String array (technically
     *         stored as String).
     */
    @Nullable
    public String[] getStringArray(String key, @Nullable String[] defaultValue) {
        if (!contains(key)) {
            return defaultValue;
        }
        String rawValue = mPrefs.getString(key, null);
        if (rawValue == null || !rawValue.startsWith(STRING_ARRAY_STRING_PREF_PREFIX)) {
            throw new ClassCastException(key + " does not contain a String[]");
        }
        String serializedArrayInfo = rawValue.substring(STRING_ARRAY_STRING_PREF_PREFIX.length());
        if (TextUtils.isEmpty(serializedArrayInfo)) {
            // this shouldn't be null due to needing to prefix it, but if it's an empty string, that
            // means that no info for the array was included, which means it was set to null
            return null;
        }
        String[] stringArray;
        try {
            String[] arrayInfo = StringArraySerializer.deserialize(serializedArrayInfo);
            if (arrayInfo == null) {
                stringArray = null;
            } else {
                // array info should have an odd length since the first item is the length and the
                // rest come in pairs
                if (arrayInfo.length % 2 == 0) {
                    throw new InvalidSerializedDataException(
                            "Invalid array info length: " + arrayInfo.length);
                }
                int length = Integer.parseInt(arrayInfo[0]);
                if (length < 0) {
                    throw new InvalidSerializedDataException("Invalid array length: " + length);
                }
                stringArray = new String[length];
                for (int i = 1; i < arrayInfo.length; i += 2) {
                    int index = Integer.parseInt(arrayInfo[i]);
                    if (index < 0 || index >= stringArray.length) {
                        throw new InvalidSerializedDataException("Invalid index: " + index);
                    }
                    stringArray[index] = arrayInfo[i + 1];
                }
            }
        } catch (InvalidSerializedDataException | NumberFormatException e) {
            Log.e(TAG, "Failed to read String[] from " + key + ": " + e.getMessage());
            // preference data is corrupt somehow. treat as if it was empty.
            return defaultValue;
        }
        return stringArray;
    }

    /**
     * Retrieve a int array value from the preferences.
     * See {@link #setIntArray}.
     * @param key The name of the preference to retrieve.
     * @param defaultValue Value to return if this preference does not exist.
     * @return Returns the preference value if it exists, or defaultValue. Throws ClassCastException
     *         if there is a preference with this name that is not a int array (technically stored
     *         as String).
     */
    @Nullable
    public int[] getIntArray(String key, @Nullable int[] defaultValue) {
        if (!contains(key)) {
            return defaultValue;
        }
        String rawValue = mPrefs.getString(key, null);
        if (rawValue == null || !rawValue.startsWith(INT_ARRAY_STRING_PREF_PREFIX)) {
            throw new ClassCastException(key + " does not contain an int[]");
        }
        String serializedArrayInfo = rawValue.substring(INT_ARRAY_STRING_PREF_PREFIX.length());
        if (TextUtils.isEmpty(serializedArrayInfo)) {
            // this shouldn't be null due to needing to prefix it, but if it's an empty string, that
            // means that no info for the array was included, which means it was set to null
            return null;
        }
        int[] intArray;
        try {
            String[] stringArray = StringArraySerializer.deserialize(serializedArrayInfo);
            if (stringArray == null) {
                intArray = null;
            } else {
                intArray = new int[stringArray.length];
                for (int i = 0; i < stringArray.length; i++) {
                    intArray[i] = Integer.parseInt(stringArray[i]);
                }
            }
        } catch (InvalidSerializedDataException | NumberFormatException e) {
            Log.e(TAG, "Failed to read int[] from " + key + ": " + e.getMessage());
            // preference data is corrupt somehow. treat as if it was empty.
            return defaultValue;
        }
        return intArray;
    }

    /**
     * Retrieve a CharSequence value from the preferences. Currently this only supports a String or
     * Spanned.
     * This is safe to use to read a preference set with {@link #setCharSequence},
     * {@link #setSpanned}, or {@link #setString}.
     * @param key The name of the preference to retrieve.
     * @param defaultValue Value to return if this preference does not exist.
     * @return Returns the preference value if it exists, or defaultValue. Throws ClassCastException
     *         if there is a preference with this name that is not a Spanned (technically stored as
     *         String).
     */
    @Nullable
    public CharSequence getCharSequence(String key, @Nullable CharSequence defaultValue) {
        if (!contains(key)) {
            return defaultValue;
        }
        String rawValue = mPrefs.getString(key, null);
        if (rawValue == null) {
            // we already validated that the preference exists, so this should mean that a regular
            // string preference was specifically set to null, so that should be returned
            return null;
        }
        if (rawValue.startsWith(SPANNED_STRING_PREF_PREFIX)) {
            String serializedSpannedInfo = rawValue.substring(SPANNED_STRING_PREF_PREFIX.length());
            Spanned spanned = getSpannedInternal(serializedSpannedInfo, null, key);
            if (spanned == null) {
                return defaultValue;
            }
            return spanned;
        }
        // stored as a regular string
        return rawValue;
    }

    @Override
    public boolean contains(String key) {
        return mPrefs.contains(key);
    }

    @Override
    public Editor edit() {
        return new Editor(mPrefs.edit());
    }

    /**
     * Set a String value in the preferences.
     * Convenience for {@link Editor#putString} and applying the changes immediately.
     * @param key The name of the preference to modify.
     * @param value The new value for the preference.
     */
    public void setString(String key, @Nullable String value) {
        edit().putString(key, value).apply();
    }

    /**
     * Set a set of String values in the preferences.
     * Convenience for {@link Editor#putStringSet} and applying the changes immediately.
     * @param key The name of the preference to modify.
     * @param value The new value for the preference.
     */
    public void setStringSet(String key, @Nullable Set<String> value) {
        edit().putStringSet(key, value).apply();
    }

    /**
     * Set a float value in the preferences.
     * Convenience for {@link Editor#putInt} and applying the changes immediately.
     * @param key The name of the preference to modify.
     * @param value The new value for the preference.
     */
    public void setInt(String key, int value) {
        edit().putInt(key, value).apply();
    }

    /**
     * Set a float value in the preferences.
     * Convenience for {@link Editor#putLong} and applying the changes immediately.
     * @param key The name of the preference to modify.
     * @param value The new value for the preference.
     */
    public void setLong(String key, long value) {
        edit().putLong(key, value).apply();
    }

    /**
     * Set a float value in the preferences.
     * Convenience for {@link Editor#putFloat} and applying the changes immediately.
     * @param key The name of the preference to modify.
     * @param value The new value for the preference.
     */
    public void setFloat(String key, float value) {
        edit().putFloat(key, value).apply();
    }

    /**
     * Set a boolean value in the preferences.
     * Convenience for {@link Editor#putBoolean} and applying the changes
     * immediately.
     * @param key The name of the preference to modify.
     * @param value The new value for the preference.
     */
    public void setBoolean(String key, boolean value) {
        edit().putBoolean(key, value).apply();
    }

    /**
     * Set a Spanned value in the preferences.
     * Convenience for {@link Editor#putSpanned} and applying the changes immediately.
     * @param key The name of the preference to modify.
     * @param value The new value for the preference.
     */
    public void setSpanned(String key, @Nullable Spanned value) {
        edit().putSpanned(key, value).apply();
    }

    /**
     * Set a CharSequence value in the preferences.
     * Convenience for {@link Editor#putCharSequence} and applying the changes immediately.
     * @param key The name of the preference to modify.
     * @param value The new value for the preference.
     */
    public void setCharSequence(String key, @Nullable CharSequence value) {
        edit().putCharSequence(key, value).apply();
    }

    /**
     * Set a string array value in the preferences.
     * Convenience for {@link Editor#putStringArray} and applying the changes immediately.
     * @param key The name of the preference to modify.
     * @param value The new value for the preference.
     */
    public void setStringArray(String key, @Nullable String[] value) {
        edit().putStringArray(key, value).apply();
    }

    /**
     * Set an int array value in the preferences.
     * Convenience for {@link Editor#putIntArray} and applying the changes immediately.
     * @param key The name of the preference to modify.
     * @param value The new value for the preference.
     */
    public void setIntArray(String key, @Nullable int[] value) {
        edit().putIntArray(key, value).apply();
    }

    /**
     * Mark in the editor that a preference value should be removed.
     * Convenience for {@link Editor#remove} and applying the changes immediately.
     * @param key The name of the preference to remove.
     */
    public void remove(String key) {
        edit().remove(key).apply();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener onSharedPreferenceChangeListener) {
        mPrefs.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener onSharedPreferenceChangeListener) {
        mPrefs.unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);

    }

    /**
     * Wrapper for {@link SharedPreferences.Editor} that has options for extra types.
     */
    public static class Editor implements SharedPreferences.Editor {
        private final SharedPreferences.Editor mEditor;
        private Editor(SharedPreferences.Editor editor) {
            mEditor = editor;
        }

        @Override
        public Editor putString(String key, @Nullable String value) {
            String specialTypeName = getSpecialTypeName(value);
            if (specialTypeName != null) {
                // it's technically possible (although extremely unlikely) for this to be an
                // appropriate string value, so it's probably not worth hard crashing the
                // application
                Log.e(TAG, "The value for " + key + " appears to be a " + specialTypeName
                        + " but is being set as a String, which may cause issues");
            }
            mEditor.putString(key, value);
            return this;
        }

        @Override
        public Editor putStringSet(String key, @Nullable Set<String> value) {
            mEditor.putStringSet(key, value);
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            mEditor.putInt(key, value);
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            mEditor.putLong(key, value);
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            mEditor.putFloat(key, value);
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            mEditor.putBoolean(key, value);
            return this;
        }

        /**
         * Set a Spanned value in the preferences. This currently supports saving a Spanned with
         * spans that are capable of having an HTML representation. Other spans will be lost.
         * @param key The name of the preference to modify.
         * @param value The new value for the preference.
         */
        public Editor putSpanned(String key, @Nullable Spanned value) {
            String serializedSpannedInfo;
            if (value == null) {
                serializedSpannedInfo = "";
            } else {
                String[] spannedInfo = null;
                Object[] spans = value.getSpans(0, value.length(), Object.class);
                if (spans.length > 0) {
                    // save spans by converting it to html. this isn't guaranteed to save all spans,
                    // but there doesn't seem to be a good way to persistently store and recover
                    // random spans, so this may be the best option for now. we probably could also
                    // store a list of the span types and their positions to try to recover specific
                    // ones that don't get saved with the html if that becomes necessary.
                    spannedInfo = new String[]{
                            value.toString(),
                            Html.toHtml(new SpannableStringBuilder(value))
                    };
                }
                if (spannedInfo == null) {
                    spannedInfo = new String[]{value.toString()};
                }
                serializedSpannedInfo = StringArraySerializer.serialize(spannedInfo);
            }
            mEditor.putString(key, SPANNED_STRING_PREF_PREFIX + serializedSpannedInfo);
            return this;
        }

        /**
         * Set a CharSequence value in the preferences. This currently supports saving a Spanned or
         * a String. Any other type of CharSequence will be converted to a string.
         * Convenience for {@link #putSpanned} or {@link #putString} (whichever is appropriate) and
         * applying the changes immediately.
         * Since this could save in different formats, {@link #getCharSequence} should be used to
         * handle reading the appropriate type.
         * @param key The name of the preference to modify.
         * @param value The new value for the preference.
         */
        public Editor putCharSequence(String key, @Nullable CharSequence value) {
            if (value instanceof Spanned) {
                return putSpanned(key, (Spanned)value);
            } else if (value == null || value instanceof String) {
                return putString(key, (String)value);
            } else {
                return putString(key, value.toString());
            }
        }

        /**
         * Set a string array value in the preferences.
         * @param key The name of the preference to modify.
         * @param value The new value for the preference.
         */
        public Editor putStringArray(String key, @Nullable String[] value) {
            String serializedArrayInfo;
            if (value == null) {
                serializedArrayInfo = "";
            } else {
                List<String> arrayInfo = new ArrayList<>();
                // first element is the array length
                arrayInfo.add("" + value.length);
                // all subsequent elements are in pairs of the index and the value (skipping null
                // indices)
                for (int i = 0; i < value.length; i++) {
                    if (value[i] == null) {
                        continue;
                    }
                    arrayInfo.add("" + i);
                    arrayInfo.add(value[i]);
                }
                serializedArrayInfo =
                        StringArraySerializer.serialize(arrayInfo.toArray(new String[0]));
            }
            mEditor.putString(key, STRING_ARRAY_STRING_PREF_PREFIX + serializedArrayInfo);
            return this;
        }

        /**
         * Set an int array value in the preferences.
         * @param key The name of the preference to modify.
         * @param value The new value for the preference.
         */
        public Editor putIntArray(String key, @Nullable int[] value) {
            String serializedArrayInfo;
            if (value == null) {
                serializedArrayInfo = "";
            } else {
                String[] stringArray = new String[value.length];
                for (int i = 0; i < value.length; i++) {
                    stringArray[i] = "" + value[i];
                }
                serializedArrayInfo = StringArraySerializer.serialize(stringArray);
            }
            mEditor.putString(key, INT_ARRAY_STRING_PREF_PREFIX + serializedArrayInfo);
            return this;
        }

        @Override
        public Editor remove(String key) {
            mEditor.remove(key);
            return this;
        }

        @Override
        public Editor clear() {
            mEditor.clear();
            return this;
        }

        @Override
        public boolean commit() {
            return mEditor.commit();
        }

        @Override
        public void apply() {
            mEditor.apply();
        }
    }
}
