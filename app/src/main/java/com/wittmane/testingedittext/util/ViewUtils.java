/*
 * Copyright (C) 2025 Eli Wittman
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;

import androidx.annotation.Nullable;

public class ViewUtils {
    // (EW) copied from MediaRouteButton. this is necessary because the way the AOSP Editor gets the
    // DragAndDropPermissions isn't accessible for apps, so we need to find the activity to get it.
    @Nullable
    public static Activity getActivity(View view) {
        // Gross way of unwrapping the Activity so we can get the FragmentManager
        Context context = view.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity)context;
            }
            context = ((ContextWrapper)context).getBaseContext();
        }
        // (EW) MediaRouteButton threw an IllegalStateException because its Context was not an
        // Activity, but an something else could be added to a view with a Context that isn't an
        // Activity, so we'll return null to allow it to be handled.
        return null;
    }
}
