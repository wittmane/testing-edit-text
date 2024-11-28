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

import androidx.annotation.Nullable;

public interface EditorSettings {
    int COMPOSING_TEXT_BEHAVIOR_INVISIBLE = 0;
    int COMPOSING_TEXT_BEHAVIOR_COMPOSE = 1;
    int COMPOSING_TEXT_BEHAVIOR_COMMIT = 2;
    int COMPOSING_TEXT_BEHAVIOR_IGNORE = 3;

    boolean getNullInputTypeMultiline();
    boolean shouldCreateInputConnection();
    boolean shouldSendSelectionInfo();
    boolean shouldSendText();
    int getComposingTextBehavior();
    boolean allowDeleteSurroundingText();
    boolean allowSettingSelection();

    boolean shouldModifyCommittedText();
    boolean shouldModifyComposedText();
    boolean shouldModifyComposedChangesOnly();
    boolean shouldConsiderComposedChangesFromEnd();
    boolean shouldRestrictToInclude();
    String[] getRestrictSpecific();
    @Nullable IntRange getRestrictRange();
    TranslateText[] getTranslateSpecific();
    boolean shouldTranslateFullMatchOnly();
    int getCodepointShift();

    boolean shouldSkipExtractingText();
    boolean shouldIgnoreExtractedTextMonitor();
    boolean shouldUpdateSelectionBeforeExtractedText();
    boolean shouldUpdateExtractedTextOnlyOnNetChanges();
    boolean shouldExtractFullText();
    int getExtractMonitorTextLimit();
    int getReturnedTextLimit();

    boolean shouldDeleteThroughComposingText();
    boolean shouldKeepEmptyComposingPosition();

    boolean shouldSkipTakeSnapshot();
    boolean shouldSkipGetSurroundingText();
    boolean shouldSkipPerformSpellCheck();
    boolean shouldSkipSetImeConsumesInput();
    boolean shouldSkipCommitContent();
    boolean shouldSkipCloseConnection();
    boolean shouldSkipDeleteSurroundingTextInCodePoints();
    boolean shouldSkipRequestCursorUpdates();
    boolean shouldSkipCommitCorrection();
    boolean shouldSkipGetSelectedText();
    boolean shouldSkipSetComposingRegion();

    int getUpdateDelay();
    int getFinishComposingTextDelay();
    int getGetSurroundingTextDelay();
    int getGetTextBeforeCursorDelay();
    int getGetSelectedTextDelay();
    int getGetTextAfterCursorDelay();
    int getGetCursorCapsModeDelay();
    int getGetExtractedTextDelay();
}
