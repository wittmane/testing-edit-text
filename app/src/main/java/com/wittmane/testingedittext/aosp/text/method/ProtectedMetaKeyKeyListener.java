/*
 * Copyright (C) 2022 Eli Wittman
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

package com.wittmane.testingedittext.aosp.text.method;

import android.text.Spannable;
import android.text.method.MetaKeyKeyListener;

/**
 * (EW) wrapper for MetaKeyKeyListener to access protected or hidden methods and constants
 */
public class ProtectedMetaKeyKeyListener extends android.text.method.MetaKeyKeyListener {

    protected static void resetLockedMeta(Spannable content) {
        MetaKeyKeyListener.resetLockedMeta(content);
    }
}
