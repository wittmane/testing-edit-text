/*
 * Copyright (C) 2024-2025 Eli Wittman
 * Copyright (C) 2008 The Android Open Source Project
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

/**
 * (EW) content from {@link android.view.inputmethod.EditorInfo} that is blocked from apps accessing
 */
public class EditorInfoExtension {

    // (EW) AOSP version was hidden
    /**
     * AndroidX Core library 1.13.0 introduced EditorInfoCompat#setStylusHandwritingEnabled and
     * EditorInfoCompat#isStylusHandwritingEnabled which used a boolean value in the EditorInfo
     * extras bundle. These methods do not set or check the Android V property since the Android V
     * SDK was not yet available. In order for EditorInfoCompat#isStylusHandwritingEnabled to return
     * the correct value for EditorInfo created by Android V TextView, the extras bundle value
     * should be set. This is the extras bundle key.
     */
    public static final String STYLUS_HANDWRITING_ENABLED_ANDROIDX_EXTRAS_KEY =
            "androidx.core.view.inputmethod.EditorInfoCompat.STYLUS_HANDWRITING_ENABLED";

    // (EW) made public because our version of EditableInputConnection (originally accessed in
    // BaseInputConnection but we merged these classes), isn't in the same package
    /**
     * The maximum length of initialSurroundingText. When the input text from
     * {@code setInitialSurroundingText(CharSequence)} is longer than this, trimming shall be
     * performed to keep memory efficiency.
     */
    public static final int MEMORY_EFFICIENT_TEXT_LENGTH = 2048;
}
