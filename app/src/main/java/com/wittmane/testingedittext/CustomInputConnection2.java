package com.wittmane.testingedittext;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputContentInfo;
import android.view.inputmethod.InputMethodManager;

public class CustomInputConnection2 extends BaseInputConnection {
    static final String TAG = CustomInputConnection2.class.getSimpleName();

    static final boolean LOG_CALLS = true;

    protected final InputMethodManager mIMM;

    private final ICustomTextView mTextView;
    // Keeps track of nested begin/end batch edit to ensure this connection always has a
    // balanced impact on its associated TextView.
    // A negative value means that this connection has been finished by the InputMethodManager.
    private int mBatchEditNesting;

    public CustomInputConnection2(View targetView) {
        super(targetView, true);
        if (targetView instanceof ICustomTextView) {
            mTextView = (ICustomTextView)targetView;
        } else {
            mTextView = null;
        }
        // copied from BaseInputConnection because this protected variable is marked @hide
        mIMM = (InputMethodManager)targetView.getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
    }

    @Override
    public Editable getEditable() {
        if (mTextView != null) {
            return mTextView.getEditableText();
        }
        return null;
    }

    @Override
    public CharSequence getTextBeforeCursor(int n, int flags) {
        if (LOG_CALLS) {
            Log.d(TAG, "getTextBeforeCursor: n=" + n + ", flags=" + flags);
        }
        return super.getTextBeforeCursor(n, flags);
    }

    @Override
    public CharSequence getTextAfterCursor(int n, int flags) {
        if (LOG_CALLS) {
            Log.d(TAG, "getTextAfterCursor: n=" + n + ", flags=" + flags);
        }
        return super.getTextAfterCursor(n, flags);
    }

    @Override
    public CharSequence getSelectedText(int flags) {
        if (LOG_CALLS) {
            Log.d(TAG, "getSelectedText: flags=" + flags);
        }
        return super.getSelectedText(flags);
    }

    @Override
    public int getCursorCapsMode(int reqModes) {
        if (LOG_CALLS) {
            Log.d(TAG, "getCursorCapsMode: reqModes=" + reqModes);
        }
        return super.getCursorCapsMode(reqModes);
    }

    @Override
    public ExtractedText getExtractedText(ExtractedTextRequest extractedTextRequest, int i) {
        if (LOG_CALLS) {
            Log.d(TAG, "getExtractedText: extractedTextRequest=" + extractedTextRequest + ", i=" + i);
        }
        //TODO: implement (maybe copy from AOSP EditableInputConnection)
        return super.getExtractedText(extractedTextRequest, i);
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        if (LOG_CALLS) {
            Log.d(TAG, "getExtractedText: deleteSurroundingText=" + beforeLength + ", afterLength=" + afterLength);
        }
        return super.deleteSurroundingText(beforeLength, afterLength);
    }

    @Override
    public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        if (LOG_CALLS) {
            Log.d(TAG, "getExtractedText: deleteSurroundingTextInCodePoints=" + beforeLength + ", afterLength=" + afterLength);
        }
        return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        if (LOG_CALLS) {
            Log.d(TAG, "setComposingText: text=" + text + ", newCursorPosition=" + newCursorPosition);
        }
        return super.setComposingText(text, newCursorPosition);
    }

    @Override
    public boolean setComposingRegion(int start, int end) {
        if (LOG_CALLS) {
            Log.d(TAG, "setComposingRegion: start=" + start + ", end=" + end);
        }
        return super.setComposingRegion(start, end);
    }

    @Override
    public boolean finishComposingText() {
        if (LOG_CALLS) {
            Log.d(TAG, "finishComposingText");
        }
        return super.finishComposingText();
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        if (LOG_CALLS) {
            Log.d(TAG, "commitText: text=" + text + ", newCursorPosition=" + newCursorPosition);
        }
        //TODO: maybe copy from AOSP EditableInputConnection (not currently implementing error flags, so maybe not)
        return super.commitText(text, newCursorPosition);
    }

    @Override
    public boolean commitCompletion(CompletionInfo text) {
        if (LOG_CALLS) {
            Log.d(TAG, "commitCompletion: CompletionInfo=" + text);
        }
        //TODO: maybe copy from AOSP EditableInputConnection
        return super.commitCompletion(text);
    }

    @Override
    public boolean commitCorrection(CorrectionInfo correctionInfo) {
        if (LOG_CALLS) {
            Log.d(TAG, "commitCorrection: correctionInfo=" + correctionInfo);
        }
        //TODO: maybe copy from AOSP EditableInputConnection
        return super.commitCorrection(correctionInfo);
    }

    @Override
    public boolean setSelection(int start, int end) {
        if (LOG_CALLS) {
            Log.d(TAG, "setSelection: start=" + start + ", end=" + end);
        }
        return super.setSelection(start, end);
    }

    @Override
    public boolean performEditorAction(int editorAction) {
        if (LOG_CALLS) {
            Log.d(TAG, "performEditorAction: editorAction=" + editorAction);
        }
        //TODO: maybe copy from AOSP EditableInputConnection
        return super.performEditorAction(editorAction);
    }

    @Override
    public boolean performContextMenuAction(int id) {
        if (LOG_CALLS) {
            Log.d(TAG, "performContextMenuAction: id=" + id);
        }
        //TODO: maybe copy from AOSP EditableInputConnection
        return super.performContextMenuAction(id);
    }

    @Override
    public boolean beginBatchEdit() {
        if (LOG_CALLS) {
            Log.d(TAG, "beginBatchEdit");
        }
//        return super.beginBatchEdit();
        synchronized(this) {
            if (mBatchEditNesting >= 0) {
                mTextView.beginBatchEdit();
                mBatchEditNesting++;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean endBatchEdit() {
        if (LOG_CALLS) {
            Log.d(TAG, "endBatchEdit");
        }
//        return super.endBatchEdit();
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

    @Override
    public boolean sendKeyEvent(KeyEvent keyEvent) {
        if (LOG_CALLS) {
            Log.d(TAG, "sendKeyEvent: keyEvent=" + keyEvent);
        }
        return super.sendKeyEvent(keyEvent);
    }

    @Override
    public boolean clearMetaKeyStates(int states) {
        if (LOG_CALLS) {
            Log.d(TAG, "clearMetaKeyStates: states=" + states);
        }
        //TODO: maybe copy from AOSP EditableInputConnection
        return super.clearMetaKeyStates(states);
    }

    @Override
    public boolean reportFullscreenMode(boolean enabled) {
        if (LOG_CALLS) {
            Log.d(TAG, "reportFullscreenMode: enabled=" + enabled);
        }
        return super.reportFullscreenMode(enabled);
    }

    @Override
    public boolean performPrivateCommand(String s, Bundle bundle) {
        if (LOG_CALLS) {
            Log.d(TAG, "performPrivateCommand: s=" + s + ", bundle=" + bundle);
        }
        //TODO: maybe copy from AOSP EditableInputConnection
        return super.performPrivateCommand(s, bundle);
    }

    @Override
    public boolean requestCursorUpdates(int cursorUpdateMode) {
        if (LOG_CALLS) {
            Log.d(TAG, "requestCursorUpdates: cursorUpdateMode=" + cursorUpdateMode);
        }
        //TODO: maybe copy from AOSP EditableInputConnection (calls some inaccessible things, so not sure what needs to be done)
        return super.requestCursorUpdates(cursorUpdateMode);
    }

    @Override
    public Handler getHandler() {
        if (LOG_CALLS) {
            Log.d(TAG, "getHandler");
        }
        return super.getHandler();
    }

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

    @Override
    public boolean commitContent(InputContentInfo inputContentInfo, int flags, Bundle opts) {
        if (LOG_CALLS) {
            Log.d(TAG, "commitContent: inputContentInfo=" + inputContentInfo + ", flags=" + flags + ", opts=" + opts);
        }
        return super.commitContent(inputContentInfo, flags, opts);
    }
}
