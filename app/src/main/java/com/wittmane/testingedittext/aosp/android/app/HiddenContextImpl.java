/*
 * Copyright (C) 2024 Eli Wittman
 * Copyright (C) 2006 The Android Open Source Project
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

package com.wittmane.testingedittext.aosp.android.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

/**
 * (EW) content from android.app.ContextImpl (ReceiverRestrictedContext) and defined in
 * {@link android.view.View} that is blocked from apps accessing
 */
public class HiddenContextImpl {
    private static final String TAG = HiddenContextImpl.class.getSimpleName();

    // (EW) Context#canLoadUnsafeResources is an abstract method and hidden, but I only found
    // ContextImpl that really implements it. documentation says it "returns true if the context can
    // load unsafe resources, e.g. fonts." made static and take a Context since we can't actually
    // adjust the Context object.
    public static boolean canLoadUnsafeResources(Context context) {
        if (context.getPackageName().equals(getOpPackageName(context))) {
            return true;
        }
        // (EW) the AOSP version also checked if the Context.CONTEXT_IGNORE_SECURITY flag was set,
        // but we don't have access to that flag, so there isn't a good way to recreate this logic.
        // returning false to be extra restrictive to be safe. the alternative would be just
        // returning true, at which point this method would only return true, so it should be
        // removed. realistically I'm not certain if this method is even necessary for non-framework
        // views, so removing this is probably fine if this does turn out to cause issues.
        return false;
    }

    // (EW) Context#getOpPackageName existed since at least Kitkat, but it was hidden until Q, so it
    // should be able to be called normally, but adding a try/catch to be safe. the documentation
    // prior to making it visible stated that it is normally the same as getBasePackageName (also
    // hidden, but ContextImpl just returns getPackageName if the base package name was null), so
    // we'll use getPackageName as a fallback (getOpPackageName has been the same as getPackageName
    // in my testing for a regular app), but theoretically that shouldn't ever be used.
    @SuppressLint("NewApi")
    private static String getOpPackageName(Context context) {
        try {
            return context.getOpPackageName();
        } catch (Exception e) {
            Log.w(TAG, "Context#getOpPackageName couldn't be called: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return context.getPackageName();
        }
    }
}
