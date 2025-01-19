/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.view.ContentInfo.SOURCE_APP;
import static android.view.ContentInfo.SOURCE_AUTOFILL;
import static android.view.ContentInfo.SOURCE_CLIPBOARD;
import static android.view.ContentInfo.SOURCE_DRAG_AND_DROP;
import static android.view.ContentInfo.SOURCE_INPUT_METHOD;
import static android.view.ContentInfo.SOURCE_PROCESS_TEXT;

import android.view.ContentInfo;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * (EW) content from {@link ContentInfo} that is blocked from apps accessing
 */
public class ContentInfoExtension {
    /**
     * Specifies the UI through which content is being inserted. Future versions of Android may
     * support additional values.
     */
    @IntDef(value = {SOURCE_APP, SOURCE_CLIPBOARD, SOURCE_INPUT_METHOD,
            SOURCE_DRAG_AND_DROP, SOURCE_AUTOFILL, SOURCE_PROCESS_TEXT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Source {}
}
