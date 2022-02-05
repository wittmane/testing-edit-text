package com.wittmane.testingedittext;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.text.Editable;
import android.text.ParcelableSpan;
import android.text.Selection;
import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.KeyListener;
import android.text.method.MetaKeyKeyListener;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.Nullable;

public class Editor {
    static final String TAG = Editor.class.getSimpleName();

    static final int EXTRACT_NOTHING = -2;
    static final int EXTRACT_UNKNOWN = -1;

    private final CustomEditTextView mTextView;
    InputMethodState mInputMethodState;
    boolean mInBatchEditControllers;

    //TODO: maybe remove - was used when specifying inputMethod (deprecated),
    // digits (prioritized over inputType, DigitsKeyListener),
    // phoneNumber (deprecated, DialerKeyListener), numeric (deprecated, DigitsKeyListener),
    // autoText/capitalize (deprecated, TextKeyListener), editable (deprecated, TextKeyListener)
    // actually it looks like this is used by inputType (see TextView#setInputType). setting default
    // for now, but probably should update
    KeyListener mKeyListener = TextKeyListener.getInstance();

    // The span controller helps monitoring the changes to which the Editor needs to react:
    // - EasyEditSpans, for which we have some UI to display on attach and on hide
    // - SelectionSpans, for which we need to call updateSelection if an IME is attached
    private SpanController mSpanController;

    public Editor(CustomEditTextView textView) {
        mTextView = textView;
    }

    void createInputMethodStateIfNeeded() {
        if (mInputMethodState == null) {
            mInputMethodState = new InputMethodState();
        }
    }

    public void beginBatchEdit() {
        mInBatchEditControllers = true;
        final InputMethodState ims = mInputMethodState;
        if (ims != null) {
            int nesting = ++ims.mBatchEditNesting;
            if (nesting == 1) {
                ims.mCursorChanged = false;
                ims.mChangedDelta = 0;
                if (ims.mContentChanged) {
                    // We already have a pending change from somewhere else,
                    // so turn this into a full update.
                    ims.mChangedStart = 0;
                    ims.mChangedEnd = mTextView.getText().length();
                } else {
                    ims.mChangedStart = EXTRACT_UNKNOWN;
                    ims.mChangedEnd = EXTRACT_UNKNOWN;
                    ims.mContentChanged = false;
                }
//                mUndoInputFilter.beginBatchEdit();
                mTextView.onBeginBatchEdit();
            }
        }
    }

    public void endBatchEdit() {
        mInBatchEditControllers = false;
        final InputMethodState ims = mInputMethodState;
        if (ims != null) {
            int nesting = --ims.mBatchEditNesting;
            if (nesting == 0) {
                finishBatchEdit(ims);
            }
        }
    }

    void ensureEndedBatchEdit() {
        final InputMethodState ims = mInputMethodState;
        if (ims != null && ims.mBatchEditNesting != 0) {
            ims.mBatchEditNesting = 0;
            finishBatchEdit(ims);
        }
    }

    void finishBatchEdit(final InputMethodState ims) {
        mTextView.onEndBatchEdit();
//        mUndoInputFilter.endBatchEdit();

        if (ims.mContentChanged || ims.mSelectionModeChanged) {
            Log.w(TAG, "finishBatchEdit: content or selection mode changed");
            mTextView.updateAfterEdit();
            reportExtractedText();
        } else if (ims.mCursorChanged) {
            Log.w(TAG, "finishBatchEdit: cursor changed");
            // Cheesy way to get us to report the current cursor location.
            mTextView.invalidateCursor();
        }
        // sendUpdateSelection knows to avoid sending if the selection did
        // not actually change.
        sendUpdateSelection();

        // Show drag handles if they were blocked by batch edit mode.
//        if (mTextActionMode != null) {
//            final CursorController cursorController = mTextView.hasSelection()
//                    ? getSelectionController() : getInsertionController();
//            if (cursorController != null && !cursorController.isActive()
//                    && !cursorController.isCursorBeingModified()) {
//                cursorController.show();
//            }
//        }
    }

    boolean extractText(ExtractedTextRequest request, ExtractedText outText) {
        return extractTextInternal(request, EXTRACT_UNKNOWN, EXTRACT_UNKNOWN,
                EXTRACT_UNKNOWN, outText);
    }

    private boolean extractTextInternal(@Nullable ExtractedTextRequest request,
                                        int partialStartOffset, int partialEndOffset, int delta,
                                        @Nullable ExtractedText outText) {
        if (request == null || outText == null) {
            return false;
        }

        final CharSequence content = mTextView.getText();
        if (content == null) {
            return false;
        }

        if (partialStartOffset != EXTRACT_NOTHING) {
            final int N = content.length();
            if (partialStartOffset < 0) {
                outText.partialStartOffset = outText.partialEndOffset = -1;
                partialStartOffset = 0;
                partialEndOffset = N;
            } else {
                // Now use the delta to determine the actual amount of text
                // we need.
                partialEndOffset += delta;
                // Adjust offsets to ensure we contain full spans.
                if (content instanceof Spanned) {
                    Spanned spanned = (Spanned) content;
                    Object[] spans = spanned.getSpans(partialStartOffset,
                            partialEndOffset, ParcelableSpan.class);
                    int i = spans.length;
                    while (i > 0) {
                        i--;
                        int j = spanned.getSpanStart(spans[i]);
                        if (j < partialStartOffset) partialStartOffset = j;
                        j = spanned.getSpanEnd(spans[i]);
                        if (j > partialEndOffset) partialEndOffset = j;
                    }
                }
                outText.partialStartOffset = partialStartOffset;
                outText.partialEndOffset = partialEndOffset - delta;

                if (partialStartOffset > N) {
                    partialStartOffset = N;
                } else if (partialStartOffset < 0) {
                    partialStartOffset = 0;
                }
                if (partialEndOffset > N) {
                    partialEndOffset = N;
                } else if (partialEndOffset < 0) {
                    partialEndOffset = 0;
                }
            }
            if ((request.flags & InputConnection.GET_TEXT_WITH_STYLES) != 0) {
                outText.text = content.subSequence(partialStartOffset,
                        partialEndOffset);
            } else {
                outText.text = TextUtils.substring(content, partialStartOffset,
                        partialEndOffset);
            }
        } else {
            outText.partialStartOffset = 0;
            outText.partialEndOffset = 0;
            outText.text = "";
        }
        outText.flags = 0;
        if (MetaKeyKeyListener.getMetaState(content, /*MetaKeyKeyListener.META_SELECTING*//*KeyEvent.META_SELECTING*/0x800) != 0) {
            outText.flags |= ExtractedText.FLAG_SELECTING;
        }
        if (mTextView.isSingleLine()) {
            outText.flags |= ExtractedText.FLAG_SINGLE_LINE;
        }
        outText.startOffset = 0;
        outText.selectionStart = mTextView.getSelectionStart();
        outText.selectionEnd = mTextView.getSelectionEnd();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            outText.hint = mTextView.getHint();
        }
        return true;
    }

    boolean reportExtractedText() {
        final Editor.InputMethodState ims = mInputMethodState;
        if (ims == null) {
            return false;
        }
        final boolean wasContentChanged = ims.mContentChanged;
        if (!wasContentChanged && !ims.mSelectionModeChanged) {
            return false;
        }
        ims.mContentChanged = false;
        ims.mSelectionModeChanged = false;
        final ExtractedTextRequest req = ims.mExtractedTextRequest;
        if (req == null) {
            return false;
        }
        final InputMethodManager imm = getInputMethodManager();
        if (imm == null) {
            return false;
        }
        if (CustomEditTextView.DEBUG_EXTRACT) {
            Log.v(CustomEditTextView.LOG_TAG, "Retrieving extracted start="
                    + ims.mChangedStart
                    + " end=" + ims.mChangedEnd
                    + " delta=" + ims.mChangedDelta);
        }
        if (ims.mChangedStart < 0 && !wasContentChanged) {
            ims.mChangedStart = EXTRACT_NOTHING;
        }
        if (extractTextInternal(req, ims.mChangedStart, ims.mChangedEnd,
                ims.mChangedDelta, ims.mExtractedText)) {
            if (CustomEditTextView.DEBUG_EXTRACT) {
                Log.v(CustomEditTextView.LOG_TAG,
                        "Reporting extracted start="
                                + ims.mExtractedText.partialStartOffset
                                + " end=" + ims.mExtractedText.partialEndOffset
                                + ": " + ims.mExtractedText.text);
            }

            imm.updateExtractedText(mTextView, req.token, ims.mExtractedText);
            ims.mChangedStart = EXTRACT_UNKNOWN;
            ims.mChangedEnd = EXTRACT_UNKNOWN;
            ims.mChangedDelta = 0;
            ims.mContentChanged = false;
            return true;
        }
        return false;
    }

    private InputMethodManager getInputMethodManager() {
        return mTextView.getInputMethodManager();
    }

    private void sendUpdateSelection() {
        if (null != mInputMethodState && mInputMethodState.mBatchEditNesting <= 0) {
            final InputMethodManager imm = getInputMethodManager();
            if (null != imm) {
                final int selectionStart = mTextView.getSelectionStart();
                final int selectionEnd = mTextView.getSelectionEnd();
                int candStart = -1;
                int candEnd = -1;
                if (mTextView.getText() instanceof Spannable) {
                    final Spannable sp = (Spannable) mTextView.getText();
                    candStart = CustomInputConnection2.getComposingSpanStart(sp);
                    candEnd = CustomInputConnection2.getComposingSpanEnd(sp);
                }
                // InputMethodManager#updateSelection skips sending the message if
                // none of the parameters have changed since the last time we called it.
                imm.updateSelection(mTextView,
                        selectionStart, selectionEnd, candStart, candEnd);
            }
        }
    }



    public void addSpanWatchers(Spannable text) {
        final int textLength = text.length();

        if (mKeyListener != null) {
            text.setSpan(mKeyListener, 0, textLength, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }

        if (mSpanController == null) {
            mSpanController = new SpanController();
        }
        text.setSpan(mSpanController, 0, textLength, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
    }



    static class InputMethodState {
        ExtractedTextRequest mExtractedTextRequest;
        final ExtractedText mExtractedText = new ExtractedText();
        int mBatchEditNesting;
        boolean mCursorChanged;
        boolean mSelectionModeChanged;
        boolean mContentChanged;
        int mChangedStart, mChangedEnd, mChangedDelta;
    }




    /**
     * Controls the {@link EasyEditSpan} monitoring when it is added, and when the related
     * pop-up should be displayed.
     * Also monitors {@link Selection} to call back to the attached input method.
     */
    private class SpanController implements SpanWatcher {

        private static final int DISPLAY_TIMEOUT_MS = 3000; // 3 secs

//        private EasyEditPopupWindow mPopupWindow;
//
//        private Runnable mHidePopup;

        // This function is pure but inner classes can't have static functions
        private boolean isNonIntermediateSelectionSpan(final Spannable text,
                                                       final Object span) {
            return (Selection.SELECTION_START == span || Selection.SELECTION_END == span)
                    && (text.getSpanFlags(span) & Spanned.SPAN_INTERMEDIATE) == 0;
        }

        @Override
        public void onSpanAdded(Spannable text, Object span, int start, int end) {
            if (isNonIntermediateSelectionSpan(text, span)) {
                sendUpdateSelection();
            } else if (span instanceof EasyEditSpan) {
//                if (mPopupWindow == null) {
//                    mPopupWindow = new EasyEditPopupWindow();
//                    mHidePopup = new Runnable() {
//                        @Override
//                        public void run() {
//                            hide();
//                        }
//                    };
//                }
//
//                // Make sure there is only at most one EasyEditSpan in the text
//                if (mPopupWindow.mEasyEditSpan != null) {
//                    mPopupWindow.mEasyEditSpan.setDeleteEnabled(false);//@hide
//                }
//
//                mPopupWindow.setEasyEditSpan((EasyEditSpan) span);
//                mPopupWindow.setOnDeleteListener(new EasyEditDeleteListener() {
//                    @Override
//                    public void onDeleteClick(EasyEditSpan span) {
//                        Editable editable = (Editable) mTextView.getText();
//                        int start = editable.getSpanStart(span);
//                        int end = editable.getSpanEnd(span);
//                        if (start >= 0 && end >= 0) {
//                            sendEasySpanNotification(EasyEditSpan.TEXT_DELETED, span);
//                            mTextView.deleteText_internal(start, end);
//                        }
//                        editable.removeSpan(span);
//                    }
//                });
//
//                if (mTextView.getWindowVisibility() != View.VISIBLE) {
//                    // The window is not visible yet, ignore the text change.
//                    return;
//                }
//
//                if (mTextView.getLayout() == null) {
//                    // The view has not been laid out yet, ignore the text change
//                    return;
//                }
//
//                if (extractedTextModeWillBeStarted()) {
//                    // The input is in extract mode. Do not handle the easy edit in
//                    // the original TextView, as the ExtractEditText will do
//                    return;
//                }
//
//                mPopupWindow.show();
//                mTextView.removeCallbacks(mHidePopup);
//                mTextView.postDelayed(mHidePopup, DISPLAY_TIMEOUT_MS);
            }
        }

        @Override
        public void onSpanRemoved(Spannable text, Object span, int start, int end) {
            if (isNonIntermediateSelectionSpan(text, span)) {
                sendUpdateSelection();
//            } else if (mPopupWindow != null && span == mPopupWindow.mEasyEditSpan) {
//                hide();
            }
        }

        @Override
        public void onSpanChanged(Spannable text, Object span, int previousStart, int previousEnd,
                                  int newStart, int newEnd) {
            if (isNonIntermediateSelectionSpan(text, span)) {
                sendUpdateSelection();
//            } else if (mPopupWindow != null && span instanceof EasyEditSpan) {
//                EasyEditSpan easyEditSpan = (EasyEditSpan) span;
//                sendEasySpanNotification(EasyEditSpan.TEXT_MODIFIED, easyEditSpan);
//                text.removeSpan(easyEditSpan);
            }
        }

        public void hide() {
//            if (mPopupWindow != null) {
//                mPopupWindow.hide();
//                mTextView.removeCallbacks(mHidePopup);
//            }
        }

        private void sendEasySpanNotification(int textChangedType, EasyEditSpan span) {
            try {
                PendingIntent pendingIntent = span.getPendingIntent();//@hide
                if (pendingIntent != null) {
                    Intent intent = new Intent();
                    intent.putExtra(EasyEditSpan.EXTRA_TEXT_CHANGED_TYPE, textChangedType);
                    pendingIntent.send(mTextView.getContext(), 0, intent);
                }
            } catch (PendingIntent.CanceledException e) {
                // This should not happen, as we should try to send the intent only once.
                Log.w(TAG, "PendingIntent for notification cannot be sent", e);
            }
        }
    }
}
