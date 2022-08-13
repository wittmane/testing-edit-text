/*
 * Copyright (C) 2022 Eli Wittman
 * Copyright (C) 2007-2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.wittmane.testingedittext.aosp.internal.widget;

import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.method.KeyListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.SurroundingText;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.wittmane.testingedittext.aosp.widget.EditText;

/**
 * Base class for an editable InputConnection instance. This is created by {@link EditText}.
 */
@RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
public class EditableInputConnection extends BaseInputConnection {
    private static final boolean DEBUG = false;
    private static final boolean LOG_CALLS = true;
    private static final String TAG = EditableInputConnection.class.getSimpleName();
    private final @NonNull EditText mEditText;

    protected final InputMethodManager mIMM;

    // Keeps track of nested begin/end batch edit to ensure this connection always has a
    // balanced impact on its associated EditText.
    // A negative value means that this connection has been finished by the InputMethodManager.
    private int mBatchEditNesting;

    // (EW) from InputMethodManager
    private static final int REQUEST_UPDATE_CURSOR_ANCHOR_INFO_NONE = 0x0;

    // (EW) from InputMethodManager. InputMethodManager.Handler#handleMessage (I think ultimately
    // triggered from IInputMethodClient.Stub#onBindMethod) also resets this in the AOSP version,
    // but I think that is due to it managing input methods and being reused. since a new input
    // connection gets created when switching input methods, that same reset shouldn't apply.
    /**
     * The monitor mode for
     * {@link InputMethodManager#updateCursorAnchorInfo(View, CursorAnchorInfo)}.
     */
    private int mRequestUpdateCursorAnchorInfoMonitorMode = REQUEST_UPDATE_CURSOR_ANCHOR_INFO_NONE;

    // (EW) from InputMethodManager. I'm not certain if this synchronization is necessary outside of
    // InputMethodManager, but it probably doesn't hurt to keep.
    final Object mH = new Object();

    public EditableInputConnection(@NonNull EditText targetView) {
        super(targetView, true);
        mEditText = targetView;
        // (EW) copied from BaseInputConnection because this protected variable is marked @hide
        mIMM = (InputMethodManager)targetView.getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public @NonNull Editable getEditable() {
        return mEditText.getEditableText();
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public CharSequence getTextBeforeCursor(int n, int flags) {
        if (LOG_CALLS) {
            Log.d(TAG, "getTextBeforeCursor: n=" + n + ", flags=" + flags);
        }
        return super.getTextBeforeCursor(n, flags);
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public CharSequence getTextAfterCursor(int n, int flags) {
        if (LOG_CALLS) {
            Log.d(TAG, "getTextAfterCursor: n=" + n + ", flags=" + flags);
        }
        return super.getTextAfterCursor(n, flags);
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    public SurroundingText getSurroundingText(@IntRange(from = 0) int beforeLength,
                                              @IntRange(from = 0) int afterLength, int flags) {
        if (LOG_CALLS) {
            Log.d(TAG, "getTextAfterCursor: beforeLength=" + beforeLength
                    + ", afterLength=" + afterLength + ", flags=" + flags);
        }
        return super.getSurroundingText(beforeLength, afterLength, flags);
    }

    @RequiresApi(api = Build.VERSION_CODES.GINGERBREAD)
    @Override
    public CharSequence getSelectedText(int flags) {
        if (LOG_CALLS) {
            Log.d(TAG, "getSelectedText: flags=" + flags);
        }
        return super.getSelectedText(flags);
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public int getCursorCapsMode(int reqModes) {
        if (LOG_CALLS) {
            Log.d(TAG, "getCursorCapsMode: reqModes=" + reqModes);
        }
        return super.getCursorCapsMode(reqModes);
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        if (LOG_CALLS) {
            Log.d(TAG, "getExtractedText: deleteSurroundingText=" + beforeLength
                    + ", afterLength=" + afterLength);
        }
        return super.deleteSurroundingText(beforeLength, afterLength);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        if (LOG_CALLS) {
            Log.d(TAG, "getExtractedText: deleteSurroundingTextInCodePoints="
                    + beforeLength + ", afterLength=" + afterLength);
        }
        return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        if (LOG_CALLS) {
            Log.d(TAG, "setComposingText: text=" + text
                    + ", newCursorPosition=" + newCursorPosition);
        }
        return super.setComposingText(text, newCursorPosition);
    }

    @RequiresApi(api = Build.VERSION_CODES.GINGERBREAD)
    @Override
    public boolean setComposingRegion(int start, int end) {
        if (LOG_CALLS) {
            Log.d(TAG, "setComposingRegion: start=" + start + ", end=" + end);
        }
        final boolean result = super.setComposingRegion(start, end);
        // (EW) BaseInputConnection calls this, which is expected to be implemented by the
        // inherited type, but it is hidden from app developers, so we have to manually call it
        endComposingRegionEditInternal();
        return result;
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean finishComposingText() {
        if (LOG_CALLS) {
            Log.d(TAG, "finishComposingText");
        }
        final boolean result = super.finishComposingText();
        // (EW) BaseInputConnection calls this, which is expected to be implemented by the
        // inherited type, but it is hidden from app developers, so we have to manually call it
        endComposingRegionEditInternal();
        return result;
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean setSelection(int start, int end) {
        if (LOG_CALLS) {
            Log.d(TAG, "setSelection: start=" + start + ", end=" + end);
        }
        return super.setSelection(start, end);
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean beginBatchEdit() {
        if (LOG_CALLS) {
            Log.d(TAG, "beginBatchEdit");
        }
        synchronized(this) {
            if (mBatchEditNesting >= 0) {
                mEditText.beginBatchEdit();
                mBatchEditNesting++;
                return true;
            }
        }
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean endBatchEdit() {
        if (LOG_CALLS) {
            Log.d(TAG, "endBatchEdit");
        }
        synchronized(this) {
            if (mBatchEditNesting > 0) {
                // When the connection is reset by the InputMethodManager and reportFinish
                // is called, some endBatchEdit calls may still be asynchronously received from the
                // IME. Do not take these into account, thus ensuring that this IC's final
                // contribution to mEditText's nested batch edit count is zero.
                mEditText.endBatchEdit();
                mBatchEditNesting--;
                return true;
            }
        }
        return false;
    }

    private void endComposingRegionEditInternal() {
        // The ContentCapture service is interested in Composing-state changes.
        mEditText.notifyContentCaptureTextChanged();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void closeConnection() {
        if (LOG_CALLS) {
            Log.d(TAG, "closeConnection");
        }
        super.closeConnection();
        synchronized(this) {
            while (mBatchEditNesting > 0) {
                endBatchEdit();
            }
            // Will prevent any further calls to begin or endBatchEdit
            mBatchEditNesting = -1;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean sendKeyEvent(KeyEvent keyEvent) {
        if (LOG_CALLS) {
            Log.d(TAG, "sendKeyEvent: keyEvent=" + keyEvent);
        }
        return super.sendKeyEvent(keyEvent);
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean clearMetaKeyStates(int states) {
        if (LOG_CALLS) {
            Log.d(TAG, "clearMetaKeyStates: states=" + states);
        }
        final Editable content = getEditable();
        KeyListener kl = mEditText.getKeyListener();
        if (kl != null) {
            try {
                kl.clearMetaKeyState(mEditText, content, states);
            } catch (AbstractMethodError e) {
                // This is an old listener that doesn't implement the
                // new method.
            }
        }
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean commitCompletion(CompletionInfo text) {
        if (LOG_CALLS) {
            Log.d(TAG, "commitCompletion: CompletionInfo=" + text);
        }
        mEditText.beginBatchEdit();
        mEditText.onCommitCompletion(text);
        mEditText.endBatchEdit();
        return true;
    }

    /**
     * Calls the {@link EditText#onCommitCorrection} method of the associated EditText.
     */
    @RequiresApi(api = VERSION_CODES.HONEYCOMB)
    @Override
    public boolean commitCorrection(CorrectionInfo correctionInfo) {
        if (LOG_CALLS) {
            Log.d(TAG, "commitCorrection: correctionInfo=" + correctionInfo);
        }
        mEditText.beginBatchEdit();
        //FUTURE: (EW) the AOSP version only flashes a highlight on the new text position as if
        // assuming that correction was already made and this method was only meant as a visual
        // indication despite the documentation sounding like this should actually change text. This
        // is probably a good candidate for alternate functionality options.
        mEditText.onCommitCorrection(correctionInfo);
        mEditText.endBatchEdit();
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean performEditorAction(int editorAction) {
        if (LOG_CALLS) {
            Log.d(TAG, "performEditorAction: editorAction=" + editorAction);
        }
        mEditText.onEditorAction(editorAction);
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean performContextMenuAction(int id) {
        if (LOG_CALLS) {
            Log.d(TAG, "performContextMenuAction: id=" + id);
        }
        mEditText.beginBatchEdit();
        mEditText.onTextContextMenuItem(id);
        mEditText.endBatchEdit();
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public ExtractedText getExtractedText(ExtractedTextRequest extractedTextRequest, int flags) {
        if (LOG_CALLS) {
            Log.d(TAG, "getExtractedText: extractedTextRequest=" + extractedTextRequest
                    + ", flags=" + flags);
        }
        //FUTURE: (EW) if this returns null, no text is shown in the full screen text field
        // (landscape) so be sure to consider this when figuring out the weird behavior options
        ExtractedText et = new ExtractedText();
        if (mEditText.extractText(extractedTextRequest, et)) {
            if ((flags&GET_EXTRACTED_TEXT_MONITOR) != 0) {
                mEditText.setExtracting(extractedTextRequest);
            }
            return et;
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    public boolean performSpellCheck() {
        mEditText.onPerformSpellCheck();
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean reportFullscreenMode(boolean enabled) {
        if (LOG_CALLS) {
            Log.d(TAG, "reportFullscreenMode: enabled=" + enabled);
        }
        return super.reportFullscreenMode(enabled);
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean performPrivateCommand(String action, Bundle bundle) {
        if (LOG_CALLS) {
            Log.d(TAG, "performPrivateCommand: action=" + action + ", bundle=" + bundle);
        }
        mEditText.onPrivateIMECommand(action, bundle);
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        if (LOG_CALLS) {
            Log.d(TAG, "commitText: text=" + text + ", newCursorPosition=" + newCursorPosition);
        }
        return super.commitText(text, newCursorPosition);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean requestCursorUpdates(int cursorUpdateMode) {
        if (LOG_CALLS) {
            Log.d(TAG, "requestCursorUpdates: cursorUpdateMode=" + cursorUpdateMode);
        }

        // It is possible that any other bit is used as a valid flag in a future release.
        // We should reject the entire request in such a case.
        final int KNOWN_FLAGS_MASK = InputConnection.CURSOR_UPDATE_IMMEDIATE |
                InputConnection.CURSOR_UPDATE_MONITOR;
        final int unknownFlags = cursorUpdateMode & ~KNOWN_FLAGS_MASK;
        if (unknownFlags != 0) {
            //FUTURE: (EW) failing because of an unknown flag seems weird, but the documentation does
            // call this out. still might be a decent thing for configurable handling.
            if (DEBUG) {
                Log.d(TAG, "Rejecting requestUpdateCursorAnchorInfo due to unknown flags." +
                        " cursorUpdateMode=" + cursorUpdateMode +
                        " unknownFlags=" + unknownFlags);
            }
            return false;
        }
        if (mIMM == null) {
            // In this case, TYPE_CURSOR_ANCHOR_INFO is not handled.
            // TODO: Return some notification code rather than false to indicate method that
            // CursorAnchorInfo is temporarily unavailable.
            return false;
        }
        // (EW) AOSP version calls InputMethodManager#setUpdateCursorAnchorInfoMode, but that is
        // hidden and marked with UnsupportedAppUsage. it's used to track the mode, mostly so
        // InputMethodManager#isCursorAnchorInfoEnabled can be checked in
        // Editor.CursorAnchorInfoNotifier#updatePosition. we just need to track it separately
        // since we're not allowed to use those for some reason.
        setUpdateCursorAnchorInfoMode(cursorUpdateMode);
        if ((cursorUpdateMode & InputConnection.CURSOR_UPDATE_IMMEDIATE) != 0) {
            if (mEditText.isInLayout()) {
                // In this case, the view hierarchy is currently undergoing a layout pass.
                // IMM#updateCursorAnchorInfo is supposed to be called soon after the layout
                // pass is finished.
            } else {
                // This will schedule a layout pass of the view tree, and the layout event
                // eventually triggers IMM#updateCursorAnchorInfo.
                mEditText.requestLayout();
            }
        }
        return true;
    }

    // (EW) from InputMethodManager
    /**
     * Return true if the current input method wants to be notified when cursor/anchor location
     * is changed.
     */
    public boolean isCursorAnchorInfoEnabled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        synchronized (mH) {
            final boolean isImmediate = (mRequestUpdateCursorAnchorInfoMonitorMode &
                    InputConnection.CURSOR_UPDATE_IMMEDIATE) != 0;
            final boolean isMonitoring = (mRequestUpdateCursorAnchorInfoMonitorMode &
                    InputConnection.CURSOR_UPDATE_MONITOR) != 0;
            return isImmediate || isMonitoring;
        }
    }

    // (EW) based on InputMethodManager#updateCursorAnchorInfo
    public boolean isCursorAnchorInfoModeImmediate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        synchronized (mH) {
            return (mRequestUpdateCursorAnchorInfoMonitorMode &
                    InputConnection.CURSOR_UPDATE_IMMEDIATE) != 0;
        }
    }

    // (EW) based on InputMethodManager#updateCursorAnchorInfo
    public void clearCursorAnchorInfoModeImmediate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        synchronized (mH) {
            // Clear immediate bit (if any).
            mRequestUpdateCursorAnchorInfoMonitorMode &= ~InputConnection.CURSOR_UPDATE_IMMEDIATE;
        }
    }

    // (EW) from InputMethodManager
    /**
     * Set the requested mode for
     * {@link InputMethodManager#updateCursorAnchorInfo(View, CursorAnchorInfo)}.
     */
    private void setUpdateCursorAnchorInfoMode(int flags) {
        synchronized (mH) {
            mRequestUpdateCursorAnchorInfoMonitorMode = flags;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    public boolean setImeConsumesInput(boolean imeConsumesInput) {
        mEditText.setImeConsumesInput(imeConsumesInput);
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public Handler getHandler() {
        if (LOG_CALLS) {
            Log.d(TAG, "getHandler");
        }
        return super.getHandler();
    }

    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    @Override
    public boolean commitContent(InputContentInfo inputContentInfo, int flags, Bundle opts) {
        if (LOG_CALLS) {
            Log.d(TAG, "commitContent: inputContentInfo=" + inputContentInfo
                    + ", flags=" + flags + ", opts=" + opts);
        }
        return super.commitContent(inputContentInfo, flags, opts);
    }
}
