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

import android.content.Context;
import android.graphics.Canvas;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.LocaleList;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.textclassifier.SelectionEvent;
import android.view.textclassifier.TextClassification;
import android.view.textclassifier.TextClassifier;
import android.view.textclassifier.TextSelection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.wittmane.testingedittext.aosp.widget.Editor.SelectionModifierCursorController;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Helper class for starting selection action mode
 * (synchronously without the TextClassifier, asynchronously with the TextClassifier).
 * @hide
 */
public final class SelectionActionModeHelper {

    private static final String LOG_TAG = "SelectActionModeHelper";

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

    /**
     * Starts Selection ActionMode.
     */
    public void startSelectionActionModeAsync() {
        int[] sortedSelectionIndices = sortSelectionIndicesFromTextView(mTextView);
        mSelectionTracker.onOriginalSelection(
                getText(mTextView),
                sortedSelectionIndices[0],
                sortedSelectionIndices[1],
                false /*isLink*/);
        startSelectionActionMode();
    }

    //(EW) unused
//    /**
//     * Starts Link ActionMode.
//     */
//    public void startLinkActionModeAsync(int start, int end) {
//        int[] indexResult = sortSelectionIndices(start, end);
//        mSelectionTracker.onOriginalSelection(getText(mTextView), indexResult[0], indexResult[1],
//                true /*isLink*/);
//        startLinkActionMode();
//    }

    public void invalidateActionModeAsync() {
        invalidateActionMode();
    }

    /** Reports a selection action event. */
    public void onSelectionAction(int menuItemId) {
        mSelectionTracker.onSelectionAction();
    }

    public void onSelectionDrag() {
        mSelectionTracker.onSelectionAction();
    }

    public void onTextChanged(int start, int end) {
        int[] sortedSelectionIndices = sortSelectionIndices(start, end);
        mSelectionTracker.onTextChanged(sortedSelectionIndices[0], sortedSelectionIndices[1]);
    }

    public boolean resetSelection(int textIndex) {
        if (mSelectionTracker.resetSelection(textIndex, mEditor)) {
            invalidateActionModeAsync();
            return true;
        }
        return false;
    }

    public void onDestroyActionMode() {
        mSelectionTracker.onSelectionDestroyed();
    }

    private void startSelectionActionMode() {
        startActionMode(Editor.TextActionMode.SELECTION);
    }

    private void startActionMode(@Editor.TextActionMode int actionMode) {
        final SelectionModifierCursorController controller = mEditor.getSelectionController();
        if (controller != null && mTextView.isTextEditable()) {
            controller.show();
        }
        mEditor.startActionModeInternal(actionMode);
        mEditor.setRestartActionModeOnNextRefresh(false);
    }

    private void invalidateActionMode() {
        final ActionMode actionMode = mEditor.getTextActionMode();
        if (actionMode != null) {
            actionMode.invalidate();
        }
        final int[] sortedSelectionIndices = sortSelectionIndicesFromTextView(mTextView);
        mSelectionTracker.onSelectionUpdated(
                sortedSelectionIndices[0], sortedSelectionIndices[1]);
    }

    /**
     * Tracks and logs smart selection changes.
     * It is important to trigger this object's methods at the appropriate event so that it tracks
     * smart selection events appropriately.
     */
    private static final class SelectionTracker {

        private final EditText mTextView;

        private int mSelectionStart;
        private int mSelectionEnd;
        private boolean mAllowReset;
        private final SelectionTracker.LogAbandonRunnable mDelayedLogAbandon = new SelectionTracker.LogAbandonRunnable();

        SelectionTracker(EditText textView) {
            mTextView = Objects.requireNonNull(textView);
        }

        /**
         * Called when the original selection happens, before smart selection is triggered.
         */
        public void onOriginalSelection(
                CharSequence text, int selectionStart, int selectionEnd, boolean isLink) {
            // If we abandoned a selection and created a new one very shortly after, we may still
            // have a pending request to log ABANDON, which we flush here.
            mDelayedLogAbandon.flush();

            mSelectionStart = selectionStart;
            mSelectionEnd = selectionEnd;
            mAllowReset = false;
        }

        /**
         * Called when selection bounds change.
         */
        public void onSelectionUpdated(int selectionStart, int selectionEnd) {
            if (isSelectionStarted()) {
                mSelectionStart = selectionStart;
                mSelectionEnd = selectionEnd;
                mAllowReset = false;
                mTextView.notifyContentCaptureTextChanged();
            }
        }

        /**
         * Called when the selection action mode is destroyed.
         */
        public void onSelectionDestroyed() {
            mAllowReset = false;
            mTextView.notifyContentCaptureTextChanged();
            // Wait a few ms to see if the selection was destroyed because of a text change event.
            mDelayedLogAbandon.schedule(100 /* ms */);
        }

        /**
         * Called when an action is taken on a smart selection.
         */
        public void onSelectionAction() {
            if (isSelectionStarted()) {
                mAllowReset = false;
            }
        }

        /**
         * Returns true if the current smart selection should be reset to normal selection based on
         * information that has been recorded about the original selection and the smart selection.
         * The expected UX here is to allow the user to select a word inside of the smart selection
         * on a single tap.
         */
        public boolean resetSelection(int textIndex, Editor editor) {
            final EditText textView = editor.getTextView();
            if (isSelectionStarted()
                    && mAllowReset
                    && textIndex >= mSelectionStart && textIndex <= mSelectionEnd
                    && getText(textView) instanceof Spannable) {
                mAllowReset = false;
                boolean selected = editor.selectCurrentWord();
                if (selected) {
                    final int[] sortedSelectionIndices = sortSelectionIndicesFromTextView(textView);
                    mSelectionStart = sortedSelectionIndices[0];
                    mSelectionEnd = sortedSelectionIndices[1];
                }
                return selected;
            }
            return false;
        }

        public void onTextChanged(int start, int end) {
            if (isSelectionStarted() && start == mSelectionStart && end == mSelectionEnd) {
                onSelectionAction();
            }
        }

        private boolean isSelectionStarted() {
            return mSelectionStart >= 0 && mSelectionEnd >= 0 && mSelectionStart != mSelectionEnd;
        }

        /** A helper for keeping track of pending abandon logging requests. */
        private final class LogAbandonRunnable implements Runnable {
            private boolean mIsPending;

            /** Schedules an abandon to be logged with the given delay. Flush if necessary. */
            void schedule(int delayMillis) {
                if (mIsPending) {
                    Log.e(LOG_TAG, "Force flushing abandon due to new scheduling request");
                    flush();
                }
                mIsPending = true;
                mTextView.postDelayed(this, delayMillis);
            }

            /** If there is a pending log request, execute it now. */
            void flush() {
                mTextView.removeCallbacks(this);
                run();
            }

            @Override
            public void run() {
                if (mIsPending) {
                    mSelectionStart = mSelectionEnd = -1;
                    mIsPending = false;
                }
            }
        }
    }

    private static CharSequence getText(EditText textView) {
        // Extracts the textView's text.
        // TODO: Investigate why/when TextView.getText() is null.
        final CharSequence text = textView.getText();
        if (text != null) {
            return text;
        }
        return "";
    }
}
