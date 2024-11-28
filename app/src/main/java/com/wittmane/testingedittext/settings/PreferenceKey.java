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

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wittmane.testingedittext.aosp.internal.util.ArrayUtils;
import com.wittmane.testingedittext.function.BiFunction;

import java.util.Objects;
import java.util.regex.Pattern;

public class PreferenceKey {
    private static final String TAG = PreferenceKey.class.getSimpleName();

    private final @NonNull String mKeyStem;
    private final @Nullable String mPrimaryAffix;
    private final @Nullable Integer mIdSuffix;

    private PreferenceKey(@NonNull String keyStem, @Nullable String primaryAffix,
                          @Nullable Integer idSuffix) {
        mKeyStem = keyStem;
        mPrimaryAffix = primaryAffix;
        mIdSuffix = idSuffix;
    }

    public static PreferenceKey createGroupKey(@NonNull String keyPrefix, int groupId) {
        if (!ArrayUtils.contains(TEST_GROUP_PREF_KEY_PREFIXES, keyPrefix)) {
            Log.e(TAG, keyPrefix + " is not a valid group preference key prefix");
            return null;
        }
        return new PreferenceKey(keyPrefix, GROUP_INFIX, groupId);
    }

    public static PreferenceKey createFieldKey(@NonNull String keyPrefix, int fieldId) {
        if (!ArrayUtils.contains(DEFAULTABLE_TEST_FIELD_PREF_KEY_PREFIXES, keyPrefix)
                && !ArrayUtils.contains(TEST_FIELD_PREF_KEY_PREFIXES, keyPrefix)) {
            Log.e(TAG, keyPrefix + " is not a valid field preference key prefix");
            return null;
        }
        return new PreferenceKey(keyPrefix, FIELD_INFIX, fieldId);
    }

    public static PreferenceKey createFieldDefaultKey(@NonNull String keyPrefix) {
        if (!ArrayUtils.contains(DEFAULTABLE_TEST_FIELD_PREF_KEY_PREFIXES, keyPrefix)) {
            Log.e(TAG, keyPrefix + " is not a valid field default preference key prefix");
            return null;
        }
        return new PreferenceKey(keyPrefix, BASE_SUFFIX, null);
    }

    public static PreferenceKey createBasicKey(@NonNull String key) {
        if (!ArrayUtils.contains(MISC_PREF_KEYS, key) && !PREF_TEST_GROUP_IDS.equals(key)) {
            Log.e(TAG, key + " is not a valid basic preference key");
            return null;
        }
        return new PreferenceKey(key, null, null);
    }

    public static PreferenceKey parse(String prefKey) {
        if (prefKey == null) {
            return null;
        }
        if (prefKey.endsWith(BASE_SUFFIX)) {
            return createFieldDefaultKey(
                    prefKey.substring(0, prefKey.length() - BASE_SUFFIX.length()));
        }
        if (containsIdSuffix(prefKey, GROUP_INFIX)) {
            PreferenceKey groupKey = splitPreferenceKey(prefKey, GROUP_INFIX,
                    PreferenceKey::createGroupKey);
            if (groupKey != null) {
                return groupKey;
            }
        }
        if (containsIdSuffix(prefKey, FIELD_INFIX)) {
            PreferenceKey fieldKey = splitPreferenceKey(prefKey, FIELD_INFIX,
                    PreferenceKey::createFieldKey);
            if (fieldKey != null) {
                return fieldKey;
            }
        }
        return createBasicKey(prefKey);
    }

    private static boolean containsIdSuffix(String prefKey, String infix) {
        return prefKey.matches(".*" + Pattern.quote(infix) + "\\d+$");
    }

    private static PreferenceKey splitPreferenceKey(String prefKey, String infix,
            BiFunction<String, Integer, PreferenceKey> createKey) {
        int prefixLength = prefKey.lastIndexOf(infix);
        int id;
        try {
            id = Integer.parseInt(prefKey.substring(prefixLength + infix.length()));
        } catch (NumberFormatException ignored) {
            return null;
        }
        String prefKeyPrefix = prefKey.substring(0, prefixLength);
        return createKey.apply(prefKeyPrefix, id);
    }

    public @NonNull String getStem() {
        return mKeyStem;
    }

    public boolean isGroup() {
        return GROUP_INFIX.equals(mPrimaryAffix);
    }

    public boolean isField() {
        return FIELD_INFIX.equals(mPrimaryAffix);
    }

    public boolean isFieldDefault() {
        return BASE_SUFFIX.equals(mPrimaryAffix);
    }

    public int getId() {
        return mIdSuffix;
    }

    @Override
    public @NonNull String toString() {
        if (mIdSuffix != null) {
            return mKeyStem + mPrimaryAffix + mIdSuffix;
        } else if (mPrimaryAffix != null) {
            return mKeyStem + mPrimaryAffix;
        } else {
            return mKeyStem;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PreferenceKey)) {
            return false;
        }
        PreferenceKey other = (PreferenceKey) o;
        return Objects.equals(mKeyStem, other.mKeyStem)
                && Objects.equals(mPrimaryAffix, other.mPrimaryAffix)
                && Objects.equals(mIdSuffix, other.mIdSuffix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mKeyStem, mPrimaryAffix, mIdSuffix);
    }
}
