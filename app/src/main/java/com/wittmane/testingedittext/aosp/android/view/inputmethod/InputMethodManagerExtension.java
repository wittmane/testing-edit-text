/*
 * Copyright (C) 2024 Eli Wittman
 * Copyright (C) 2007-2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.wittmane.testingedittext.aosp.android.view.inputmethod;

import static android.view.inputmethod.InputMethodManager.HIDE_IMPLICIT_ONLY;
import static android.view.inputmethod.InputMethodManager.HIDE_NOT_ALWAYS;

import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * (EW) content from {@link InputMethodManager} that is blocked from apps accessing
 */
public class InputMethodManagerExtension {
    private static final String TAG = InputMethodManagerExtension.class.getSimpleName();

    @IntDef(flag = true, value = {
            HIDE_IMPLICIT_ONLY,
            HIDE_NOT_ALWAYS,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface HideFlags {}

    // (EW) static wrapper
    // (EW) InputMethodManager#hideSoftInputFromView is hidden, there doesn't seem to be a good way
    // to copy the logic from it, and it's blocked by reflection. for now this is just a basic
    // wrapper around other method calls that this replaced in a couple places in EditText
    // (TextView). the old method seems to work well enough. I'm not certain of the need for the
    // change other than being a simpler call.
    /**
     * Synonym for {@link InputMethodManager#hideSoftInputFromWindow(IBinder, int)} but takes a
     * {@link View} as a parameter to be a counterpart of
     * {@link InputMethodManager#showSoftInput(View, int)}.
     *
     * @param view {@link View} to be used to conditionally issue hide request when and only when
     *             this {@link View} is serving as an IME target.
     */
    public static boolean hideSoftInputFromView(@NonNull InputMethodManager imm, @NonNull View view,
                                                @HideFlags int flags) {
        if (imm.isActive(view)) {
            return imm.hideSoftInputFromWindow(view.getWindowToken(), flags);
        }
        return false;
    }

    // (EW) InputMethodManager#hasActiveInputConnection is hidden, and there doesn't seem to be a
    // good way to copy the logic from it, and reflection is blocked. for now this is just a basic
    // wrapper around another method call that this replaced in a couple places in Editor. it
    // probably isn't as good, but I don't know what specific issues this may cause. maybe something
    // better will be available in the future.
    /**
     * Checks whether the active input connection (if any) is for the given view.
     *
     * <p>Note that {@code view} parameter does not take
     * {@link View#checkInputConnectionProxy(View)} into account. This method returns {@code true}
     * when and only when the specified {@code view} is the actual {@link View} instance that is
     * connected to the IME.</p>
     *
     * @param view {@link View} to be checked.
     * @return {@code true} if {@code view} is currently interacting with IME.
     */
    public static boolean hasActiveInputConnection(@NonNull InputMethodManager imm,
                                                   @Nullable View view) {
        return imm.isActive(view);
    }
}
