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

package com.wittmane.testingedittext.aosp.android.view;

import static android.view.MotionEvent.TOOL_TYPE_ERASER;
import static android.view.MotionEvent.TOOL_TYPE_STYLUS;

import android.view.InputDevice;
import android.view.MotionEvent;

/**
 * (EW) content from {@link MotionEvent} that is blocked from apps accessing
 */
public class MotionEventExtension {

    // (EW) made static since the AOSP version is hidden
    /**
     * Returns {@code true} if this motion event is from a stylus pointer.
     */
    public static boolean isStylusPointer(MotionEvent motionEvent) {
        final int actionIndex = motionEvent.getActionIndex();
        return motionEvent.isFromSource(InputDevice.SOURCE_STYLUS)
                && (motionEvent.getToolType(actionIndex) == TOOL_TYPE_STYLUS
                || motionEvent.getToolType(actionIndex) == TOOL_TYPE_ERASER);
    }
}
