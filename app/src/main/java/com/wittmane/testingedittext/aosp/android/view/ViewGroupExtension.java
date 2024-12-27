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

package com.wittmane.testingedittext.aosp.android.view;

import android.view.ViewGroup;

/**
 * (EW) content from {@link ViewGroup} that is blocked from apps accessing
 */
public class ViewGroupExtension {
    // (EW) made public and static to call on any ViewGroup
    /** Return true if this ViewGroup is laying out using optical bounds. */
    public static boolean isLayoutModeOptical(ViewGroup viewGroup) {
        return viewGroup.getLayoutMode() == ViewGroup.LAYOUT_MODE_OPTICAL_BOUNDS;
    }
}
