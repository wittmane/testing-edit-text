/*
 * Copyright (C) 2024 Eli Wittman
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

package com.wittmane.testingedittext.settings;

import java.util.Locale;

/**
 * Cached preference values for a field
 */
/* package */ class TestField  extends AppLevelDefaults {
    public final int mId;

    public int mInputType;
    public boolean mNullInputTypeMultiline;
    public boolean mCreateInputConnection;
    public boolean mSendSelectionInfo;
    public boolean mSendText;
    public int mComposingTextBehavior;
    public boolean mAllowDeleteSurroundingText;
    public boolean mAllowSettingSelection;
    public int mImeOptions;
    public int mImeActionId;
    public String mImeActionLabel;
    public String mPrivateImeOptions;
    public boolean mSelectAllOnFocus;
    public int mMaxLength;
    public boolean mAllowUndo;
    public Locale[] mTextLocales;
    public Locale[] mImeHintLocales;
    public CharSequence mLabelText;
    public CharSequence mDefaultText;
    public CharSequence mHintText;

    public boolean mOverrideTextInputModification;
    public boolean mOverrideTextReturn;
    public boolean mOverrideTextComposition;
    public boolean mOverrideTargetVersion;
    public boolean mOverrideSystemBehavior;

    public TestField(int id) {
        if (id < 0) {
            throw new IllegalArgumentException("The field id can't be negative (" + id + ")");
        }
        mId = id;
    }
}
