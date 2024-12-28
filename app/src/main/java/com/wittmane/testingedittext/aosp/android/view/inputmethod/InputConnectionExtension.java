/*
 * Copyright (C) 2024 Eli Wittman
 * Copyright (C) 2007 The Android Open Source Project
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

package com.wittmane.testingedittext.aosp.android.view.inputmethod;

import static android.view.inputmethod.InputConnection.CURSOR_UPDATE_FILTER_CHARACTER_BOUNDS;
import static android.view.inputmethod.InputConnection.CURSOR_UPDATE_FILTER_EDITOR_BOUNDS;
import static android.view.inputmethod.InputConnection.CURSOR_UPDATE_FILTER_INSERTION_MARKER;
import static android.view.inputmethod.InputConnection.CURSOR_UPDATE_FILTER_TEXT_APPEARANCE;
import static android.view.inputmethod.InputConnection.CURSOR_UPDATE_FILTER_VISIBLE_LINE_BOUNDS;
import static android.view.inputmethod.InputConnection.CURSOR_UPDATE_IMMEDIATE;
import static android.view.inputmethod.InputConnection.CURSOR_UPDATE_MONITOR;

import android.view.inputmethod.InputConnection;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * (EW) content from {@link InputConnection} that is blocked from apps accessing
 */
public class InputConnectionExtension {

    // (EW) the AOSP version is hidden
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {CURSOR_UPDATE_IMMEDIATE, CURSOR_UPDATE_MONITOR}, flag = true)
    public @interface CursorUpdateMode{}

    // (EW) the AOSP version is hidden
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {CURSOR_UPDATE_FILTER_EDITOR_BOUNDS, CURSOR_UPDATE_FILTER_CHARACTER_BOUNDS,
            CURSOR_UPDATE_FILTER_INSERTION_MARKER, CURSOR_UPDATE_FILTER_VISIBLE_LINE_BOUNDS,
            CURSOR_UPDATE_FILTER_TEXT_APPEARANCE},
            flag = true)
    public @interface CursorUpdateFilter{}

}
