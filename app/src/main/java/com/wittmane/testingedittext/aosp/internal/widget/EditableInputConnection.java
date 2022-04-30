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
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.SurroundingText;

import androidx.annotation.IntRange;
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
    private final EditText mTextView;

    protected final InputMethodManager mIMM;

    // Keeps track of nested begin/end batch edit to ensure this connection always has a
    // balanced impact on its associated TextView.
    // A negative value means that this connection has been finished by the InputMethodManager.
    private int mBatchEditNesting;

    public EditableInputConnection(EditText targetView) {
        super(targetView, true);
        mTextView = (EditText)targetView;
        // (EW) copied from BaseInputConnection because this protected variable is marked @hide
        mIMM = (InputMethodManager)targetView.getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public Editable getEditable() {
        if (mTextView != null) {
            return mTextView.getEditableText();
        }
        return null;
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
            Log.d(TAG, "getExtractedText: deleteSurroundingText=" + beforeLength + ", afterLength=" + afterLength);
        }
        return super.deleteSurroundingText(beforeLength, afterLength);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        if (LOG_CALLS) {
            Log.d(TAG, "getExtractedText: deleteSurroundingTextInCodePoints=" + beforeLength + ", afterLength=" + afterLength);
        }
        return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        if (LOG_CALLS) {
            Log.d(TAG, "setComposingText: text=" + text + ", newCursorPosition=" + newCursorPosition);
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
        final Editable content = getEditable();
        if (content != null) {
            // (EW) BaseInputConnection calls this, which is expected to be implemented by the
            // inherited type, but it is hidden from app developers, so we have to manually call it
            endComposingRegionEditInternal();
        }
        return result;
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean finishComposingText() {
        if (LOG_CALLS) {
            Log.d(TAG, "finishComposingText");
        }
        final boolean result = super.finishComposingText();
        final Editable content = getEditable();
        if (content != null) {
            // (EW) BaseInputConnection calls this, which is expected to be implemented by the
            // inherited type, but it is hidden from app developers, so we have to manually call it
            endComposingRegionEditInternal();
        }
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
                mTextView.beginBatchEdit();
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
                // contribution to mTextView's nested batch edit count is zero.
                mTextView.endBatchEdit();
                mBatchEditNesting--;
                return true;
            }
        }
        return false;
    }

    private void endComposingRegionEditInternal() {
        // The ContentCapture service is interested in Composing-state changes.
//        mTextView.notifyContentCaptureTextChanged();
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
        if (content == null) return false;
        KeyListener kl = mTextView.getKeyListener();
        if (kl != null) {
            try {
                kl.clearMetaKeyState(mTextView, content, states);
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
        mTextView.beginBatchEdit();
        mTextView.onCommitCompletion(text);
        mTextView.endBatchEdit();
        return true;
    }

    /**
     * Calls the {@link EditText#onCommitCorrection} method of the associated TextView.
     */
    @RequiresApi(api = VERSION_CODES.HONEYCOMB)
    @Override
    public boolean commitCorrection(CorrectionInfo correctionInfo) {
        if (LOG_CALLS) {
            Log.d(TAG, "commitCorrection: correctionInfo=" + correctionInfo);
        }
        mTextView.beginBatchEdit();
        //TODO: (EW) the AOSP version only flashes a highlight on the new text position as if
        // assuming that correction was already made and this method was only meant as a visual
        // indication despite the documentation sounding like this should actually change text. This
        // is probably a good candidate for alternate functionality options.
        mTextView.onCommitCorrection(correctionInfo);
        mTextView.endBatchEdit();
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean performEditorAction(int editorAction) {
        if (LOG_CALLS) {
            Log.d(TAG, "performEditorAction: editorAction=" + editorAction);
        }
        mTextView.onEditorAction(editorAction);
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean performContextMenuAction(int id) {
        if (LOG_CALLS) {
            Log.d(TAG, "performContextMenuAction: id=" + id);
        }
        mTextView.beginBatchEdit();
        mTextView.onTextContextMenuItem(id);
        mTextView.endBatchEdit();
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public ExtractedText getExtractedText(ExtractedTextRequest extractedTextRequest, int flags) {
        if (LOG_CALLS) {
            Log.d(TAG, "getExtractedText: extractedTextRequest=" + extractedTextRequest + ", flags=" + flags);
        }
        //TODO: (EW) if this returns null, no text is shown in the full screen text field
        // (landscape) so be sure to consider this when figuring out the weird behavior options
        if (mTextView != null) {
            ExtractedText et = new ExtractedText();
            if (mTextView.extractText(extractedTextRequest, et)) {
                if ((flags&GET_EXTRACTED_TEXT_MONITOR) != 0) {
                    mTextView.setExtracting(extractedTextRequest);
                }
                return et;
            }
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    public boolean performSpellCheck() {
        //TODO: (EW) maybe implement
//        mTextView.onPerformSpellCheck();
//        return true;
        return false;
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
        mTextView.onPrivateIMECommand(action, bundle);
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
        //TODO: (EW) is there anything we can do to replace this, or is it fine to just skip it?
//        mIMM.setUpdateCursorAnchorInfoMode(cursorUpdateMode);
        if ((cursorUpdateMode & InputConnection.CURSOR_UPDATE_IMMEDIATE) != 0) {
            if (mTextView == null) {
                // In this case, FLAG_CURSOR_ANCHOR_INFO_IMMEDIATE is silently ignored.
                // TODO: Return some notification code for the input method that indicates
                // FLAG_CURSOR_ANCHOR_INFO_IMMEDIATE is ignored.
            } else if (mTextView.isInLayout()) {
                // In this case, the view hierarchy is currently undergoing a layout pass.
                // IMM#updateCursorAnchorInfo is supposed to be called soon after the layout
                // pass is finished.
            } else {
                // This will schedule a layout pass of the view tree, and the layout event
                // eventually triggers IMM#updateCursorAnchorInfo.
                mTextView.requestLayout();
            }
        }
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    public boolean setImeConsumesInput(boolean imeConsumesInput) {
        if (mTextView == null) {
            return super.setImeConsumesInput(imeConsumesInput);
        }
        mTextView.setImeConsumesInput(imeConsumesInput);
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
            Log.d(TAG, "commitContent: inputContentInfo=" + inputContentInfo + ", flags=" + flags + ", opts=" + opts);
        }
        return super.commitContent(inputContentInfo, flags, opts);
    }
}
