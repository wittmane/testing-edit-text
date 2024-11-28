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

/* package */ class AppLevelFieldDefaults {
    public boolean mModifyCommittedText;
    public boolean mModifyComposedText;
    public boolean mConsiderComposedChangesFromEnd;
    public boolean mModifyComposedChangesOnly;
    public boolean mRestrictToInclude;
    public String[] mRestrictSpecific;
    public IntRange mRestrictRange;
    public TranslateText[] mTranslateSpecific;
    public boolean mTranslateFullMatchOnly;
    public int mShiftCodepoint;
    public boolean mSkipExtractingText;
    public boolean mIgnoreExtractedTextMonitor;
    public boolean mUpdateSelectionBeforeExtractedText;
    public boolean mUpdateExtractedTextOnlyOnNetChanges;
    public boolean mExtractFullText;
    public int mExtractMonitorTextLimit;
    public int mReturnedTextLimit;
    public boolean mDeleteThroughComposingText;
    public boolean mKeepEmptyComposingPosition;
    public boolean mSkipTakeSnapshot;
    public boolean mSkipGetSurroundingText;
    public boolean mSkipPerformSpellCheck;
    public boolean mSkipSetImeConsumesInput;
    public boolean mSkipCommitContent;
    public boolean mSkipCloseConnection;
    public boolean mSkipDeleteSurroundingTextInCodePoints;
    public boolean mSkipRequestCursorUpdates;
    public boolean mSkipCommitCorrection;
    public boolean mSkipGetSelectedText;
    public boolean mSkipSetComposingRegion;
    public int mUpdateDelay;
    public int mFinishComposingTextDelay;
    public int mGetSurroundingTextDelay;
    public int mGetTextBeforeCursorDelay;
    public int mGetSelectedTextDelay;
    public int mGetTextAfterCursorDelay;
    public int mGetCursorCapsModeDelay;
    public int mGetExtractedTextDelay;
}
