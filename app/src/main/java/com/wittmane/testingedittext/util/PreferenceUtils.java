/*
 * Copyright (C) 2025 Eli Wittman
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

package com.wittmane.testingedittext.util;

import android.annotation.SuppressLint;
import android.os.Build;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.ListAdapter;

import androidx.annotation.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Stack;

public class PreferenceUtils {
    private static final String TAG = PreferenceUtils.class.getSimpleName();

    @Nullable
    public static PreferenceScreen getParentScreen(Preference preference) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            HashSet<PreferenceGroup> ancestors = new HashSet<>();
            PreferenceGroup parent = preference.getParent();
            if (parent != null) {
                ancestors.add(parent);
            }
            while (parent != null && !(parent instanceof PreferenceScreen)) {
                parent = parent.getParent();
                if (ancestors.contains(parent)) {
                    // avoid infinitely looping in case parents form a loop for some reason
                    break;
                }
                if (parent != null) {
                    ancestors.add(parent);
                }
            }
            if (parent instanceof PreferenceScreen) {
                return (PreferenceScreen) parent;
            }
            return null;
        }

        PreferenceManager manager = preference.getPreferenceManager();
        if (manager != null) {
            PreferenceScreen rootPreferenceScreen = null;
            // there doesn't seem to be any good way to get the parent preference prior to Oreo, so
            // we'll have to resort to reflection, but since it's only for some older versions,
            // there should be less risk of it not working.
            try {
                @SuppressLint("DiscouragedPrivateApi")
                Method getPreferenceScreenMethod =
                        PreferenceManager.class.getDeclaredMethod("getPreferenceScreen");
                getPreferenceScreenMethod.setAccessible(true);
                rootPreferenceScreen = (PreferenceScreen) getPreferenceScreenMethod.invoke(manager);
            } catch (NoSuchMethodException | IllegalAccessException |
                     InvocationTargetException e) {
                Log.e(TAG, "Reflection failed on PreferenceManager.getPreferenceScreen: "
                        + e.getMessage());
            }
            return getNearestParentPrefScreen(preference, rootPreferenceScreen);
        }
        return null;
    }

    private static PreferenceScreen getNearestParentPrefScreen(Preference pref,
            PreferenceScreen rootPreferenceScreen) {
        Stack<PreferenceGroup> path = new Stack<>();
        path.push(rootPreferenceScreen);
        if (!pathLeadsToPref(pref, path)) {
            return null;
        }
        while (!path.empty()) {
            PreferenceGroup pathElement = path.pop();
            if (pathElement instanceof PreferenceScreen) {
                return (PreferenceScreen) pathElement;
            }
        }
        return null;
    }

    private static boolean pathLeadsToPref(Preference pref, Stack<PreferenceGroup> path) {
        if (path.empty()) {
            return false;
        }
        PreferenceGroup group = path.peek();
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference child = group.getPreference(i);
            if (child == pref) {
                return true;
            }
            if (!(child instanceof PreferenceGroup)) {
                continue;
            }
            path.push((PreferenceGroup) child);
            if (pathLeadsToPref(pref, path)) {
                return true;
            }
            path.pop();
        }
        return false;
    }

    public static int getPosition(Preference pref, ListAdapter listAdapter) {
        for (int i = 0; i < listAdapter.getCount(); i++) {
            if (listAdapter.getItem(i) == pref) {
                return i;
            }
        }
        return -1;
    }
}
