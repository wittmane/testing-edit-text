/*
 * Copyright (C) 2022 Eli Wittman
 * Copyright (C) 2017 The Android Open Source Project
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

package com.wittmane.testingedittext.aosp.widget;

import android.view.ActionMode;

import androidx.annotation.NonNull;

import com.wittmane.testingedittext.aosp.widget.Editor.SelectionModifierCursorController;

import java.util.Objects;

// (EW) the AOSP version of this is hidden from apps, so it had to be copied here in order to be
// used, but text classification (including smart selection and link handling) and logging was
// stripped out, but the public methods were generally kept as-is for easier comparison and in case
// at least some of text classification functionality gets copied over later
/**
 * Helper class for starting selection action mode
 * (synchronously without the TextClassifier, asynchronously with the TextClassifier).
 */
public final class SelectionActionModeHelper {

    private final Editor mEditor;
    private final EditText mTextView;

    private final SelectionTracker mSelectionTracker;

    public SelectionActionModeHelper(@NonNull Editor editor) {
        mEditor = Objects.requireNonNull(editor);
        mTextView = mEditor.getTextView();
        mSelectionTracker = new SelectionTracker(mTextView);
    }

    /**
     * Swap the selection index if the start index is greater than end index.
     *
     * @return the swap result, index 0 is the start index and index 1 is the end index.
     */
    private static int[] sortSelectionIndices(int selectionStart, int selectionEnd) {
        if (selectionStart < selectionEnd) {
            return new int[]{selectionStart, selectionEnd};
        }
        return new int[]{selectionEnd, selectionStart};
    }

    /**
     * The {@link EditText} selection start and end index may not be sorted, this method will swap
     * the {@link EditText} selection index if the start index is greater than end index.
     *
     * @param textView the selected TextView.
     * @return the swap result, index 0 is the start index and index 1 is the end index.
     */
    private static int[] sortSelectionIndicesFromTextView(EditText textView) {
        int selectionStart = textView.getSelectionStart();
        int selectionEnd = textView.getSelectionEnd();
        return sortSelectionIndices(selectionStart, selectionEnd);
    }

    // (EW) skipped the adjustSelection parameter since it was just used for text classification.
    // the AOSP version would start an async task for text classification, which was skipped, so
    // this name doesn't make a lot of sense anymore.
    /**
     * Starts Selection ActionMode.
     */
    public void startSelectionActionModeAsync() {
        int[] sortedSelectionIndices = sortSelectionIndicesFromTextView(mTextView);
        mSelectionTracker.onOriginalSelection(sortedSelectionIndices[0], sortedSelectionIndices[1]);
        startSelectionActionMode();
    }

    // (EW) skipping #startLinkActionModeAsync since it's related to text classification

    // (EW) the AOSP version would start an async task for text classification, which was skipped,
    // so this name doesn't make a lot of sense anymore.
    public void invalidateActionModeAsync() {
        invalidateActionMode();
    }

    // (EW) skipped the actionLabel parameter since it was just used for logging
    /** Reports a selection action event. */
    public void onSelectionAction(int menuItemId) {
        // (EW) this just called SelectionTracker#onSelectionAction, which was skipped (see comment
        // in SelectionTracker)
    }

    public void onSelectionDrag() {
        // (EW) this just called SelectionTracker#onSelectionAction, which was skipped (see comment
        // in SelectionTracker)
    }

    public void onTextChanged(int start, int end) {
        // (EW) this just called SelectionTracker#onTextChanged, which was skipped (see comment
        // in SelectionTracker)
    }

    public boolean resetSelection(int textIndex) {
        // (EW) since SelectionTracker#mAllowReset was always false due to
        // SelectionTracker#onClassifiedSelection (the only place that could set it to true) being
        // skipped, this is always false
        return false;
    }

    // (EW) skipped #getTextClassification

    public void onDestroyActionMode() {
        mSelectionTracker.onSelectionDestroyed();
    }

    // (EW) skipped #onDraw and #isDrawingHighlight since they were used for the smart selection
    // sprite, which is related to text classification

    // (EW) skipped #getTextClassificationSettings, #cancelAsyncTask, #skipTextClassification,
    // #startLinkActionMode, and #startSelectionActionMode since they are for text classification

    private void startSelectionActionMode() {
        startActionMode(Editor.TextActionMode.SELECTION);
    }

    // (EW) skipped SelectionResult parameter since it was just used for text classification or
    // handling smart or link selections
    private void startActionMode(@Editor.TextActionMode int actionMode) {
        final SelectionModifierCursorController controller = mEditor.getSelectionController();
        if (controller != null && mTextView.isTextEditable()) {
            controller.show();
        }
        mEditor.startActionModeInternal(actionMode);

        mEditor.setRestartActionModeOnNextRefresh(false);
    }

    // (EW) skipped #startSelectionActionModeWithSmartSelectAnimation,
    // #convertSelectionToRectangles, #mergeRectangleIntoList, and #movePointInsideNearestRectangle
    // because they are for the smart selection, which is related to text classification

    // (EW) skipped the SelectionResult parameter since it was just used for text classification
    private void invalidateActionMode() {
        final ActionMode actionMode = mEditor.getTextActionMode();
        if (actionMode != null) {
            actionMode.invalidate();
        }
        final int[] sortedSelectionIndices = sortSelectionIndicesFromTextView(mTextView);
        mSelectionTracker.onSelectionUpdated(sortedSelectionIndices[0], sortedSelectionIndices[1]);
    }

    // (EW) skipped #resetTextClassificationHelper

    /**
     * Tracks selection changes.
     */
    private static final class SelectionTracker {

        private final EditText mTextView;

        private int mSelectionStart;
        private int mSelectionEnd;

        SelectionTracker(EditText textView) {
            mTextView = Objects.requireNonNull(textView);
        }

        // (EW) skipped the text and isLink parameters since they were just used for logging
        /**
         * Called when the original selection happens.
         */
        public void onOriginalSelection(int selectionStart, int selectionEnd) {
            mSelectionStart = selectionStart;
            mSelectionEnd = selectionEnd;
        }

        // (EW) skipped #onSmartSelection, #onLinkSelected, and #onClassifiedSelection since they
        // are for text classification

        // (EW) skipped the TextClassification parameter
        /**
         * Called when selection bounds change.
         */
        public void onSelectionUpdated(int selectionStart, int selectionEnd) {
            if (isSelectionStarted()) {
                mSelectionStart = selectionStart;
                mSelectionEnd = selectionEnd;
                mTextView.notifyContentCaptureTextChanged();
            }
        }

        /**
         * Called when the selection action mode is destroyed.
         */
        public void onSelectionDestroyed() {
            mTextView.notifyContentCaptureTextChanged();
        }

        // (EW) skipped #onSelectionAction because it just did logging and set #mAllowReset to
        // false, which it always was due to SelectionTracker#onClassifiedSelection (the only place
        // that could set it to true) being skipped

        // (EW) skipped #resetSelection because it would always just return false due to
        // SelectionTracker#onClassifiedSelection (the only place that could set it to true) being
        // skipped

        // (EW) skipped #onTextChanged because it just called into #onSelectionAction, which is
        // skipped (see above)

        // (EW) skipped #maybeInvalidateLogger because it was just for logging

        private boolean isSelectionStarted() {
            return mSelectionStart >= 0 && mSelectionEnd >= 0 && mSelectionStart != mSelectionEnd;
        }

        // (EW) skipped LogAbandonRunnable since it was just for logging
    }

    // (EW) skipped SelectionMetricsLogger, TextClassificationAsyncTask, TextClassificationHelper,
    // SelectionResult, and #getActionType because they are just for text classification or logging

    // (EW) skipped #getText because our version of EditText#getText won't ever be null, so this
    // wrapper became useless
}
