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
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.Layout;
import android.text.TextUtils.TruncateAt;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.ListAdapter;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.wittmane.testingedittext.function.Function;

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

    public static <TPref extends Preference> void handleLongText(TPref pref, View view,
            Function<TPref, CharSequence> getDisplayedSummary) {
        TextView titleTextView = view.findViewById(android.R.id.title);
        if (titleTextView != null) {
            titleTextView.setSingleLine(false);
        }

        TextView summaryTextView = view.findViewById(android.R.id.summary);
        if (summaryTextView != null) {
            // make sure the text shows an ellipsis for any overflow
            summaryTextView.setEllipsize(TruncateAt.END);

            summaryTextView.post(new Runnable() {
                @Override
                public void run() {
                    Layout layout = summaryTextView.getLayout();

                    OnLongClickListener longClickListener;
                    if (layout != null && ((layout.getLineCount() == summaryTextView.getMaxLines()
                            && layout.getEllipsisCount(layout.getLineCount() - 1) > 0)
                            || layout.getLineCount() > summaryTextView.getMaxLines())) {
                        // the text is cut off, so we need a long click to be able to show the full
                        // text
                        longClickListener = new OnLongClickListener() {
                            @Override
                            public boolean onLongClick(View v) {
                                // Create the object of AlertDialog Builder class
                                AlertDialog dialog = new AlertDialog.Builder(pref.getContext())
                                        .setTitle(pref.getTitle())
                                        .setMessage(getDisplayedSummary.apply(pref))
                                        .setPositiveButton(android.R.string.ok,
                                                new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog,
                                                                        int which) {
                                                    }
                                                })
                                        .create();
                                dialog.show();

                                return true;
                            }
                        };
                    } else {
                        // the text isn't cut off (or we don't have any indication that it is), so
                        // we don't need a long click handler, but we need to explicitly clear it
                        // because the system seems to reuse the UI content, which could leak an old
                        // long click listener from some other preference that is out of view
                        // otherwise
                        longClickListener = null;
                    }
                    summaryTextView.setOnLongClickListener(longClickListener);

                    // adding a long click listener seems to block the single click on the text from
                    // triggering the normal click action for the preference (open a screen, toggle
                    // the preference, etc), so we'll need to add a regular click listener to
                    // reproduce that. although simply triggering the Preference's #onClick may work
                    // on some Preferences, such as SwitchPreference, the base Preference
                    // implementation does nothing. AdapterView#performItemClick calls
                    // PreferenceScreen#onItemClick, which calls Preference#performClick normally
                    // when a preference is clicked. Preference#performClick what we really need,
                    // but that's hidden from apps for some reason, so we'll just go one stack up
                    // and replicate the call to PreferenceScreen#onItemClick.
                    OnClickListener clickListener = new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            PreferenceScreen prefScreen = getParentScreen(pref);
                            if (prefScreen != null) {
                                ListAdapter rootListAdapter = prefScreen.getRootAdapter();
                                int position = getPosition(pref, rootListAdapter);
                                if (position >= 0) {
                                    long id = rootListAdapter.getItemId(position);
                                    // PreferenceScreen#onItemClick only uses the AdapterView parent
                                    // parameter to shift the position, but since we looked up the
                                    // position directly from the root list adapter, we wouldn't
                                    // actually want the position to be shifted.
                                    // PreferenceScreen#onItemClick doesn't use the View view
                                    // parameter. it's possible that there is some child class that
                                    // does something with them, but until there is some known case
                                    // for that, skipping these parameters should be fine.
                                    prefScreen.onItemClick(null, null, position, id);
                                }
                            }
                        }
                    };
                    summaryTextView.setOnClickListener(clickListener);

                    // add the listener to the title too
                    if (titleTextView != null) {
                        titleTextView.setOnLongClickListener(longClickListener);
                        titleTextView.setOnClickListener(clickListener);
                    }

                }
            });
        }
    }
}
