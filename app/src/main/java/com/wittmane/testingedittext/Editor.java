package com.wittmane.testingedittext;

import android.app.PendingIntent;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Layout;
import android.text.ParcelableSpan;
import android.text.Selection;
import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.KeyListener;
import android.text.method.MetaKeyKeyListener;
import android.text.method.TextKeyListener;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.ActionMode;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wittmane.testingedittext.editor.CursorController;
//import com.wittmane.testingedittext.editor.InsertionPointCursorController;
import com.wittmane.testingedittext.editor.InsertionPointCursorController28;
import com.wittmane.testingedittext.editor.PositionListener;
import com.wittmane.testingedittext.editor.ProcessTextIntentActionsHandler28;
import com.wittmane.testingedittext.editor.SelectionModifierCursorController28;
import com.wittmane.testingedittext.editor.TextActionModeCallback28;
import com.wittmane.testingedittext.method.WordIterator28;
import com.wittmane.testingedittext.widget.SelectionActionModeHelper28;

import java.text.BreakIterator;
//import com.wittmane.testingedittext.editor.TextActionModeCallback;

public class Editor {
    static final String TAG = Editor.class.getSimpleName();

    static final int EXTRACT_NOTHING = -2;
    static final int EXTRACT_UNKNOWN = -1;

    public final CustomEditTextView mTextView;
    private final EditorTouchState mTouchState;
    InputMethodState mInputMethodState;
    boolean mInBatchEditControllers;

    public final ProcessTextIntentActionsHandler28 mProcessTextIntentActionsHandler;

    boolean mTouchFocusSelected;

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

    public boolean mFlagCursorDragFromAnywhereEnabled = true;//TODO: (EW) figure out if this should be conditionally set and where to get the value
    public boolean mFlagInsertionHandleGesturesEnabled;
    public final boolean mHapticTextHandleEnabled;

    // Cursor Controllers.
    InsertionPointCursorController28 mInsertionPointCursorController;
    public SelectionModifierCursorController28 mSelectionModifierCursorController;

    private boolean mInsertionControllerEnabled;
    private boolean mSelectionControllerEnabled;
    // Action mode used when text is selected or when actions on an insertion cursor are triggered.
    public ActionMode mTextActionMode;

    private SelectionActionModeHelper28 mSelectionActionModeHelper;

    // For calculating the line change slops while moving cursor/selection.
    // The slop max/min value include line height and the slop on the upper/lower line.
    private static final int LINE_CHANGE_SLOP_MAX_DP = 45;
    private static final int LINE_CHANGE_SLOP_MIN_DP = 12;
    private int mLineChangeSlopMax;
    private int mLineChangeSlopMin;

    // Global listener that detects changes in the global position of the TextView
    private PositionListener mPositionListener;

    boolean mCursorVisible = true;
    boolean mSelectAllOnFocus;
    public boolean mTextIsSelectable;

    public Rect mTempRect;

    public static final int DRAG_SHADOW_MAX_TEXT_LENGTH = 20;
    public static final float LINE_SLOP_MULTIPLIER_FOR_HANDLEVIEWS = 0.5f;
    public static final int UNSET_X_VALUE = -1;
    public static final int UNSET_LINE = -1;

    // Indicates the current tap state (first tap, double tap, or triple click).
    public int mTapState = TAP_STATE_INITIAL;
    public long mLastTouchUpTime = 0;
    public static final int TAP_STATE_INITIAL = 0;
    public static final int TAP_STATE_FIRST_TAP = 1;
    public static final int TAP_STATE_DOUBLE_TAP = 2;
    // Only for mouse input.
    public static final int TAP_STATE_TRIPLE_CLICK = 3;

    // The button state as of the last time #onTouchEvent is called.
    private int mLastButtonState;

    public boolean mDiscardNextActionUp;
    public boolean mIgnoreActionUpEvent;

    public Runnable mInsertionActionModeRunnable;

    boolean mIsInsertionActionModeStartPending = false;

    public Drawable mSelectHandleLeft;
    public Drawable mSelectHandleRight;
    public Drawable mSelectHandleCenter;

    public float mLastDownPositionX, mLastDownPositionY;
    private float mLastUpPositionX, mLastUpPositionY;
    private float mContextMenuAnchorX, mContextMenuAnchorY;

    private boolean mRequestingLinkActionMode;

    public boolean mPreserveSelection;
    private boolean mRestartActionModeOnNextRefresh;

    boolean mIsBeingLongClicked;

    private WordIterator28 mWordIterator;

    // This word iterator is set with text and used to determine word boundaries
    // when a user is selecting text.
    private WordIterator28 mWordIteratorWithText;
    // Indicate that the text in the word iterator needs to be updated.
    private boolean mUpdateWordIteratorText;

    @IntDef({TextActionMode.SELECTION, TextActionMode.INSERTION, TextActionMode.TEXT_LINK})
    public @interface TextActionMode {
        int SELECTION = 0;
        int INSERTION = 1;
        int TEXT_LINK = 2;
    }

    // Ordering constants used to place the Action Mode or context menu items in their menu.
    public static final int MENU_ITEM_ORDER_ASSIST = 0;
    public static final int MENU_ITEM_ORDER_UNDO = 2;
    public static final int MENU_ITEM_ORDER_REDO = 3;
    public static final int MENU_ITEM_ORDER_CUT = 4;
    public static final int MENU_ITEM_ORDER_COPY = 5;
    public static final int MENU_ITEM_ORDER_PASTE = 6;
    public static final int MENU_ITEM_ORDER_SHARE = 7;
    public static final int MENU_ITEM_ORDER_SELECT_ALL = 8;
    public static final int MENU_ITEM_ORDER_REPLACE = 9;
    public static final int MENU_ITEM_ORDER_AUTOFILL = 10;
    public static final int MENU_ITEM_ORDER_PASTE_AS_PLAIN_TEXT = 11;
    public static final int MENU_ITEM_ORDER_SECONDARY_ASSIST_ACTIONS_START = 50;
    public static final int MENU_ITEM_ORDER_PROCESS_TEXT_INTENT_ACTIONS_START = 100;

    public final MenuItem.OnMenuItemClickListener mOnContextMenuItemClickListener =
            new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    if (mProcessTextIntentActionsHandler.performMenuItemAction(item)) {
                        return true;
                    }
                    return mTextView.onTextContextMenuItem(item.getItemId());
                }
            };

    public Editor(CustomEditTextView textView) {
        mTextView = textView;
        mProcessTextIntentActionsHandler = new ProcessTextIntentActionsHandler28(this);
        mTouchState = mTextView.mTouchState;
        mHapticTextHandleEnabled = /*mTextView.getContext().getResources().getBoolean(
                R.bool.config_enableHapticTextHandle)*/false;
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
        Log.w(TAG, "finishBatchEdit: mTextActionMode=" + mTextActionMode);
        if (mTextActionMode != null) {
            final CursorController cursorController = mTextView.hasSelection()
                    ? /*getSelectionController()*/null : getInsertionController();
            if (cursorController != null && !cursorController.isActive()
                    && !cursorController.isCursorBeingModified()) {
                cursorController.show();
            }
        }
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



    /**
     * Forgets all undo and redo operations for this Editor.
     */
    void forgetUndoRedo() {
//        UndoOwner[] owners = { mUndoOwner };
//        mUndoManager.forgetUndos(owners, -1 /* all */);
//        mUndoManager.forgetRedos(owners, -1 /* all */);
    }

    boolean canUndo() {
//        UndoOwner[] owners = { mUndoOwner };
//        return mAllowUndo && mUndoManager.countUndos(owners) > 0;
        return false;
    }

    boolean canRedo() {
//        UndoOwner[] owners = { mUndoOwner };
//        return mAllowUndo && mUndoManager.countRedos(owners) > 0;
        return false;
    }

    void undo() {
//        if (!mAllowUndo) {
//            return;
//        }
//        UndoOwner[] owners = { mUndoOwner };
//        mUndoManager.undo(owners, 1);  // Undo 1 action.
    }

    void redo() {
//        if (!mAllowUndo) {
//            return;
//        }
//        UndoOwner[] owners = { mUndoOwner };
//        mUndoManager.redo(owners, 1);  // Redo 1 action.
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



    //TODO: hook up these events (using aosp28) and try to get the insertion cursor to show up
    // probably get rid of SimpleTouchManager to match aosp for easier reference (reorganize later)
    void onTouchEvent(MotionEvent event) {
        final boolean filterOutEvent = shouldFilterOutTouchEvent(event);
        mLastButtonState = event.getButtonState();
        if (filterOutEvent) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                mDiscardNextActionUp = true;
            }
            return;
        }
        updateTapState(event);
//        updateFloatingToolbarVisibility(event);

        if (hasSelectionController()) {
            getSelectionController().onTouchEvent(event);
        }

//        if (mShowSuggestionRunnable != null) {
//            mTextView.removeCallbacks(mShowSuggestionRunnable);
//            mShowSuggestionRunnable = null;
//        }

        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            mLastUpPositionX = event.getX();
            mLastUpPositionY = event.getY();
        }

        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mLastDownPositionX = event.getX();
            mLastDownPositionY = event.getY();

            // Reset this state; it will be re-set if super.onTouchEvent
            // causes focus to move to the view.
            mTouchFocusSelected = false;
            mIgnoreActionUpEvent = false;
        }
    }

    void onTouchUpEvent(MotionEvent event) {
        if (getSelectionActionModeHelper().resetSelection(
                mTextView.getOffsetForPosition(event.getX(), event.getY()))) {
            return;
        }

        boolean selectAllGotFocus = mSelectAllOnFocus && mTextView.didTouchFocusSelect();
        hideCursorAndSpanControllers();
        stopTextActionMode();
        CharSequence text = mTextView.getText();
        if (!selectAllGotFocus && text.length() > 0) {
            // Move cursor
            final int offset = mTextView.getOffsetForPosition(event.getX(), event.getY());

            final boolean shouldInsertCursor = !mRequestingLinkActionMode;
            if (shouldInsertCursor) {
                Selection.setSelection((Spannable) text, offset);
//                if (mSpellChecker != null) {
//                    // When the cursor moves, the word that was typed may need spell check
//                    mSpellChecker.onSelectionChanged();
//                }
            }

            Log.w(TAG, "onTouchUpEvent: extractedTextModeWillBeStarted=" + extractedTextModeWillBeStarted()
                    + ", hasInsertionController=" + hasInsertionController()
                    + ", shouldInsertCursor=" + shouldInsertCursor);
            if (!extractedTextModeWillBeStarted()) {
                /*if (isCursorInsideEasyCorrectionSpan()) {
                    // Cancel the single tap delayed runnable.
                    if (mInsertionActionModeRunnable != null) {
                        mTextView.removeCallbacks(mInsertionActionModeRunnable);
                    }

                    mShowSuggestionRunnable = this::replace;

                    // removeCallbacks is performed on every touch
                    mTextView.postDelayed(mShowSuggestionRunnable,
                            ViewConfiguration.getDoubleTapTimeout());
                } else */if (hasInsertionController()) {
                    if (shouldInsertCursor) {
                        getInsertionController().show();
                    } else {
                        getInsertionController().hide();
                    }
                }
            }
        }
    }

    void onScrollChanged() {
        if (mPositionListener != null) {
            mPositionListener.onScrollChanged();
        }
        if (mTextActionMode != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mTextActionMode.invalidateContentRect();
            } else {
                //TODO: (EW) handle?
            }
        }
    }

    private void updateTapState(MotionEvent event) {
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            final boolean isMouse = event.isFromSource(InputDevice.SOURCE_MOUSE);
            // Detect double tap and triple click.
            if (((mTapState == TAP_STATE_FIRST_TAP)
                    || ((mTapState == TAP_STATE_DOUBLE_TAP) && isMouse))
                    && (SystemClock.uptimeMillis() - mLastTouchUpTime)
                    <= ViewConfiguration.getDoubleTapTimeout()) {
                if (mTapState == TAP_STATE_FIRST_TAP) {
                    mTapState = TAP_STATE_DOUBLE_TAP;
                } else {
                    mTapState = TAP_STATE_TRIPLE_CLICK;
                }
            } else {
                mTapState = TAP_STATE_FIRST_TAP;
            }
        }
        if (action == MotionEvent.ACTION_UP) {
            mLastTouchUpTime = SystemClock.uptimeMillis();
        }
    }

    public boolean performLongClick(boolean handled) {
        Log.w(TAG, "performLongClick: handled=" + handled
                + ", isPositionOnText=" + isPositionOnText(mLastDownPositionX, mLastDownPositionY)
                + ", mInsertionControllerEnabled=" + mInsertionControllerEnabled);
        // Long press in empty space moves cursor and starts the insertion action mode.
        if (!handled && !isPositionOnText(mLastDownPositionX, mLastDownPositionY)
                && mInsertionControllerEnabled) {
            final int offset = mTextView.getOffsetForPosition(mLastDownPositionX,
                    mLastDownPositionY);
            Selection.setSelection((Spannable) mTextView.getText(), offset);
            getInsertionController().show();
            mIsInsertionActionModeStartPending = true;
            handled = true;
//            MetricsLogger.action(
//                    mTextView.getContext(),
//                    MetricsEvent.TEXT_LONGPRESS,
//                    TextViewMetrics.SUBTYPE_LONG_PRESS_OTHER);
        }

        Log.w(TAG, "performLongClick: handled=" + handled
                + ", mTextActionMode=" + mTextActionMode
                + ", touchPositionIsInSelection=" + touchPositionIsInSelection());
        if (!handled && mTextActionMode != null) {
            if (touchPositionIsInSelection()) {
                startDragAndDrop();
//                MetricsLogger.action(
//                        mTextView.getContext(),
//                        MetricsEvent.TEXT_LONGPRESS,
//                        TextViewMetrics.SUBTYPE_LONG_PRESS_DRAG_AND_DROP);
            } else {
                stopTextActionMode();
                selectCurrentWordAndStartDrag();
//                MetricsLogger.action(
//                        mTextView.getContext(),
//                        MetricsEvent.TEXT_LONGPRESS,
//                        TextViewMetrics.SUBTYPE_LONG_PRESS_SELECTION);
            }
            handled = true;
        }

        // Start a new selection
        if (!handled) {
            handled = selectCurrentWordAndStartDrag();
            if (handled) {
//                MetricsLogger.action(
//                        mTextView.getContext(),
//                        MetricsEvent.TEXT_LONGPRESS,
//                        TextViewMetrics.SUBTYPE_LONG_PRESS_SELECTION);
            }
        }

        return handled;
    }

    float getLastUpPositionX() {
        return mLastUpPositionX;
    }

    float getLastUpPositionY() {
        return mLastUpPositionY;
    }

    private long getLastTouchOffsets() {
        SelectionModifierCursorController28 selectionController = getSelectionController();
        final int minOffset = selectionController.getMinTouchOffset();
        final int maxOffset = selectionController.getMaxTouchOffset();
        return HiddenTextUtils.packRangeInLong(minOffset, maxOffset);
    }

    private boolean shouldFilterOutTouchEvent(MotionEvent event) {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            return false;
        }
        final boolean primaryButtonStateChanged =
                ((mLastButtonState ^ event.getButtonState()) & MotionEvent.BUTTON_PRIMARY) != 0;
        final int action = event.getActionMasked();
        if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP)
                && !primaryButtonStateChanged) {
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE
                && !event.isButtonPressed(MotionEvent.BUTTON_PRIMARY)) {
            return true;
        }
        return false;
    }



    public void setLineChangeSlopMinMaxForTesting(final int min, final int max) {
        mLineChangeSlopMin = min;
        mLineChangeSlopMax = max;
    }

    public int getCurrentLineAdjustedForSlop(Layout layout, int prevLine, float y) {
        final int trueLine = mTextView.getLineAtCoordinate(y);
        if (layout == null || prevLine > layout.getLineCount()
                || layout.getLineCount() <= 0 || prevLine < 0) {
            // Invalid parameters, just return whatever line is at y.
            return trueLine;
        }

        if (Math.abs(trueLine - prevLine) >= 2) {
            // Only stick to lines if we're within a line of the previous selection.
            return trueLine;
        }

        final int lineHeight = layout.getLineBottom(prevLine) - layout.getLineTop(prevLine);
        int slop = (int)(LINE_SLOP_MULTIPLIER_FOR_HANDLEVIEWS
                * (layout.getLineBottom(trueLine) - layout.getLineTop(trueLine)));
        slop = Math.max(mLineChangeSlopMin,
                Math.min(mLineChangeSlopMax, lineHeight + slop)) - lineHeight;
        slop = Math.max(0, slop);

        final float verticalOffset = mTextView.viewportToContentVerticalOffset();
        if (trueLine > prevLine && y >= layout.getLineBottom(prevLine) + slop + verticalOffset) {
            return trueLine;
        }
        if (trueLine < prevLine && y <= layout.getLineTop(prevLine) - slop + verticalOffset) {
            return trueLine;
        }
        return prevLine;
    }

    /**
     * Return clamped position for the drawable. If the drawable is within the boundaries of the
     * view, then it is offset with the left padding of the cursor drawable. If the drawable is at
     * the beginning or the end of the text then its drawable edge is aligned with left or right of
     * the view boundary. If the drawable is null, horizontal parameter is aligned to left or right
     * of the view.
     *
     * @param drawable Drawable. Can be null.
     * @param horizontal Horizontal position for the drawable.
     * @return The clamped horizontal position for the drawable.
     */
    public int clampHorizontalPosition(@Nullable final Drawable drawable, float horizontal) {
        horizontal = Math.max(0.5f, horizontal - 0.5f);
        if (mTempRect == null) mTempRect = new Rect();

        int drawableWidth = 0;
        if (drawable != null) {
            drawable.getPadding(mTempRect);
            drawableWidth = drawable.getIntrinsicWidth();
        } else {
            mTempRect.setEmpty();
        }

        int scrollX = mTextView.getScrollX();
        float horizontalDiff = horizontal - scrollX;
        int viewClippedWidth = mTextView.getWidth() - mTextView.getCompoundPaddingLeft()
                - mTextView.getCompoundPaddingRight();

        final int left;
        if (horizontalDiff >= (viewClippedWidth - 1f)) {
            // at the rightmost position
            left = viewClippedWidth + scrollX - (drawableWidth - mTempRect.right);
        } else if (Math.abs(horizontalDiff) <= 1f
                || (TextUtils.isEmpty(mTextView.getText())
                && (CustomEditTextView.VERY_WIDE - scrollX) <= (viewClippedWidth + 1f)
                && horizontal <= 1f)) {
            // at the leftmost position
            left = scrollX - mTempRect.left;
        } else {
            left = (int) horizontal - mTempRect.left;
        }
        return left;
    }




    /** Returns true if the screen coordinates position (x,y) corresponds to a character displayed
     * in the view. Returns false when the position is in the empty space of left/right of text.
     */
    public boolean isPositionOnText(float x, float y) {
        Layout layout = mTextView.getLayout();
        if (layout == null) return false;

        final int line = mTextView.getLineAtCoordinate(y);
        x = mTextView.convertToLocalHorizontalCoordinate(x);

        if (x < layout.getLineLeft(line)) return false;
        if (x > layout.getLineRight(line)) return false;
        return true;
    }

    public void startDragAndDrop() {
        getSelectionActionModeHelper().onSelectionDrag();

        // TODO: Fix drag and drop in full screen extracted mode.
        if (mTextView.isInExtractedMode()) {
            return;
        }
        final int start = mTextView.getSelectionStart();
        final int end = mTextView.getSelectionEnd();
//        CharSequence selectedText = mTextView.getTransformedText(start, end);
//        ClipData data = ClipData.newPlainText(null, selectedText);
//        DragLocalState localState = new DragLocalState(mTextView, start, end);
//        mTextView.startDragAndDrop(data, getTextThumbnailBuilder(start, end), localState,
//                View.DRAG_FLAG_GLOBAL);
        stopTextActionMode();
        if (hasSelectionController()) {
            getSelectionController().resetTouchOffsets();
        }
    }

    /**
     * If the TextView allows text selection, selects the current word when no existing selection
     * was available and starts a drag.
     *
     * @return true if the drag was started.
     */
    public boolean selectCurrentWordAndStartDrag() {
        Log.w(TAG, "selectCurrentWordAndStartDrag");
        if (mInsertionActionModeRunnable != null) {
            mTextView.removeCallbacks(mInsertionActionModeRunnable);
        }
        if (extractedTextModeWillBeStarted()) {
            Log.w(TAG, "selectCurrentWordAndStartDrag: extractedTextModeWillBeStarted");
            return false;
        }
        if (!checkField()) {
            Log.w(TAG, "selectCurrentWordAndStartDrag: !checkField");
            return false;
        }
        if (!mTextView.hasSelection() && !selectCurrentWord()) {
            Log.w(TAG, "selectCurrentWordAndStartDrag: !mTextView.hasSelection() && !selectCurrentWord()");
            // No selection and cannot select a word.
            return false;
        }
        stopTextActionModeWithPreservingSelection();
        getSelectionController().enterDrag(
                SelectionModifierCursorController28.DRAG_ACCELERATOR_MODE_WORD);
        return true;
    }

    private boolean touchPositionIsInSelection() {
        int selectionStart = mTextView.getSelectionStart();
        int selectionEnd = mTextView.getSelectionEnd();

        if (selectionStart == selectionEnd) {
            return false;
        }

        if (selectionStart > selectionEnd) {
            int tmp = selectionStart;
            selectionStart = selectionEnd;
            selectionEnd = tmp;
            Selection.setSelection((Spannable) mTextView.getText(), selectionStart, selectionEnd);
        }

//        SelectionModifierCursorController selectionController = getSelectionController();
//        int minOffset = selectionController.getMinTouchOffset();
//        int maxOffset = selectionController.getMaxTouchOffset();
//
//        return ((minOffset >= selectionStart) && (maxOffset < selectionEnd));
        return false;
    }

    /**
     * Checks whether a selection can be performed on the current TextView.
     *
     * @return true if a selection can be performed
     */
    boolean checkField() {
        if (!mTextView.canSelectText() || !mTextView.requestFocus()) {
//            Log.w(TextView.LOG_TAG,
//                    "TextView does not support text selection. Selection cancelled.");
            return false;
        }
        return true;
    }

    /**
     * Adjusts selection to the word under last touch offset. Return true if the operation was
     * successfully performed.
     */
    public boolean selectCurrentWord() {
        if (!mTextView.canSelectText()) {
            Log.w(TAG, "selectCurrentWord: can't select text");
            return false;
        }

//        if (needsToSelectAllToSelectWordOrParagraph()) {
//            return mTextView.selectAllText();
//        }

        long lastTouchOffsets = getLastTouchOffsets();
        final int minOffset = HiddenTextUtils.unpackRangeStartFromLong(lastTouchOffsets);
        final int maxOffset = HiddenTextUtils.unpackRangeEndFromLong(lastTouchOffsets);

        // Safety check in case standard touch event handling has been bypassed
//        if (minOffset < 0 || minOffset > mTextView.getText().length()) return false;
//        if (maxOffset < 0 || maxOffset > mTextView.getText().length()) return false;
        if (minOffset < 0 || minOffset > mTextView.getText().length()
                || maxOffset < 0 || maxOffset > mTextView.getText().length()) {
            Log.w(TAG, "selectCurrentWord: failed safety check: minOffset=" + minOffset
                    + ", maxOffset=" + maxOffset + ", length=" + mTextView.getText().length());
            return false;
        }

        int selectionStart, selectionEnd;

        // If a URLSpan (web address, email, phone...) is found at that position, select it.
        URLSpan[] urlSpans =
                ((Spanned) mTextView.getText()).getSpans(minOffset, maxOffset, URLSpan.class);
        if (urlSpans.length >= 1) {
            URLSpan urlSpan = urlSpans[0];
            selectionStart = ((Spanned) mTextView.getText()).getSpanStart(urlSpan);
            selectionEnd = ((Spanned) mTextView.getText()).getSpanEnd(urlSpan);
            Log.w(TAG, "selectCurrentWord: urlSpans selectionStart=" + selectionStart + ", selectionEnd=" + selectionEnd);
        } else {
            // FIXME - We should check if there's a LocaleSpan in the text, this may be
            // something we should try handling or checking for.
            final WordIterator28 wordIterator = getWordIterator();
            wordIterator.setCharSequence(mTextView.getText(), minOffset, maxOffset);

            selectionStart = wordIterator.getBeginning(minOffset);
            selectionEnd = wordIterator.getEnd(maxOffset);
            Log.w(TAG, "selectCurrentWord: selectionStart=" + selectionStart + ", selectionEnd=" + selectionEnd);

            if (selectionStart == BreakIterator.DONE || selectionEnd == BreakIterator.DONE
                    || selectionStart == selectionEnd) {
                // Possible when the word iterator does not properly handle the text's language
                long range = getCharClusterRange(minOffset);
                selectionStart = HiddenTextUtils.unpackRangeStartFromLong(range);
                selectionEnd = HiddenTextUtils.unpackRangeEndFromLong(range);
                Log.w(TAG, "selectCurrentWord: selectionStart2=" + selectionStart + ", selectionEnd2=" + selectionEnd);
            }
        }

        Selection.setSelection((Spannable) mTextView.getText(), selectionStart, selectionEnd);
        return selectionEnd > selectionStart;
//        return false;
    }

    /**
     * Adjusts selection to the paragraph under last touch offset. Return true if the operation was
     * successfully performed.
     */
    public boolean selectCurrentParagraph() {
//        if (!mTextView.canSelectText()) {
//            return false;
//        }
//
//        if (needsToSelectAllToSelectWordOrParagraph()) {
//            return mTextView.selectAllText();
//        }
//
//        long lastTouchOffsets = getLastTouchOffsets();
//        final int minLastTouchOffset = TextUtils.unpackRangeStartFromLong(lastTouchOffsets);
//        final int maxLastTouchOffset = TextUtils.unpackRangeEndFromLong(lastTouchOffsets);
//
//        final long paragraphsRange = getParagraphsRange(minLastTouchOffset, maxLastTouchOffset);
//        final int start = TextUtils.unpackRangeStartFromLong(paragraphsRange);
//        final int end = TextUtils.unpackRangeEndFromLong(paragraphsRange);
//        if (start < end) {
//            Selection.setSelection((Spannable) mTextView.getText(), start, end);
//            return true;
//        }
        return false;
    }










    void refreshTextActionMode() {
        if (extractedTextModeWillBeStarted()) {
            mRestartActionModeOnNextRefresh = false;
            return;
        }
        final boolean hasSelection = mTextView.hasSelection();
        final SelectionModifierCursorController28 selectionController = getSelectionController();
        final InsertionPointCursorController28 insertionController = getInsertionController();
        if ((selectionController != null && selectionController.isCursorBeingModified())
                || (insertionController != null && insertionController.isCursorBeingModified())) {
            // ActionMode should be managed by the currently active cursor controller.
            mRestartActionModeOnNextRefresh = false;
            return;
        }
        if (hasSelection) {
            hideInsertionPointCursorController();
            if (mTextActionMode == null) {
                if (mRestartActionModeOnNextRefresh) {
                    // To avoid distraction, newly start action mode only when selection action
                    // mode is being restarted.
                    startSelectionActionModeAsync(false);
                }
            } else if (selectionController == null || !selectionController.isActive()) {
                // Insertion action mode is active. Avoid dismissing the selection.
                stopTextActionModeWithPreservingSelection();
                startSelectionActionModeAsync(false);
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mTextActionMode.invalidateContentRect();
                } else {
                    //TODO: (EW) handle?
                }
            }
        } else {
            // Insertion action mode is started only when insertion controller is explicitly
            // activated.
            if (insertionController == null || !insertionController.isActive()) {
                stopTextActionMode();
            } else if (mTextActionMode != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mTextActionMode.invalidateContentRect();
                }
            }
        }
        mRestartActionModeOnNextRefresh = false;
    }

    public void stopTextActionMode() {
        if (mTextActionMode != null) {
            // This will hide the mSelectionModifierCursorController
            mTextActionMode.finish();
        }
    }

    private void stopTextActionModeWithPreservingSelection() {
        if (mTextActionMode != null) {
            mRestartActionModeOnNextRefresh = true;
        }
        mPreserveSelection = true;
        stopTextActionMode();
        mPreserveSelection = false;
    }

    @Nullable
    public ActionMode getTextActionMode() {
        return mTextActionMode;
    }

    public void setRestartActionModeOnNextRefresh(boolean value) {
        mRestartActionModeOnNextRefresh = value;
    }


    public void toggleInsertionActionMode() {
        if (mTextActionMode != null) {
            stopTextActionMode();
        } else {
            startInsertionActionMode();
        }
    }

    /**
     * Start an Insertion action mode.
     */
    public void startInsertionActionMode() {
        Log.w(TAG, "startInsertionActionMode");
        if (mInsertionActionModeRunnable != null) {
            mTextView.removeCallbacks(mInsertionActionModeRunnable);
        }
        if (extractedTextModeWillBeStarted()) {
            return;
        }
        stopTextActionMode();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActionMode.Callback actionModeCallback =
                    new TextActionModeCallback28(TextActionMode.INSERTION, mTextView);
            mTextActionMode = mTextView.startActionMode(
                    actionModeCallback, ActionMode.TYPE_FLOATING);
        } else {
            //TODO: handle
        }
        if (mTextActionMode != null && getInsertionController() != null) {
            getInsertionController().show();
        }
    }

    /**
     * Synchronously invalidates an action mode without the TextClassifier.
     */
    public void invalidateActionMode() {
        if (mTextActionMode != null) {
            mTextActionMode.invalidate();
        }
    }

    /**
     * Asynchronously starts a selection action mode using the TextClassifier.
     */
    public void startSelectionActionModeAsync(boolean adjustSelection) {
        getSelectionActionModeHelper().startSelectionActionModeAsync(adjustSelection);
    }

    public SelectionActionModeHelper28 getSelectionActionModeHelper() {
        if (mSelectionActionModeHelper == null) {
            mSelectionActionModeHelper = new SelectionActionModeHelper28(this);
            Log.w(TAG, "getSelectionActionModeHelper: create mSelectionActionModeHelper=" + mSelectionActionModeHelper);
        }
        return mSelectionActionModeHelper;
    }

    public boolean startActionModeInternal(@TextActionMode int actionMode) {
        Log.w(TAG, "startActionModeInternal: actionMode=" + actionMode);
        if (extractedTextModeWillBeStarted()) {
            return false;
        }
        if (mTextActionMode != null) {
            // Text action mode is already started
            invalidateActionMode();
            return false;
        }

        if (actionMode != TextActionMode.TEXT_LINK
                && (!checkField() || !mTextView.hasSelection())) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActionMode.Callback actionModeCallback = new TextActionModeCallback28(actionMode, mTextView);
            mTextActionMode = mTextView.startActionMode(actionModeCallback, ActionMode.TYPE_FLOATING);
        } else {
            //TODO: (EW) handle
        }

        final boolean selectableText = mTextView.isTextEditable() || mTextView.isTextSelectable();
//        if (actionMode == TextActionMode.TEXT_LINK && !selectableText
//                && mTextActionMode instanceof FloatingActionMode) {
//            // Make the toolbar outside-touchable so that it can be dismissed when the user clicks
//            // outside of it.
//            ((FloatingActionMode) mTextActionMode).setOutsideTouchable(true,
//                    () -> stopTextActionMode());
//        }

        final boolean selectionStarted = mTextActionMode != null;
        if (selectionStarted
                && mTextView.isTextEditable() && !mTextView.isTextSelectable()
                /*&& mShowSoftInputOnFocus*/) {
            // Show the IME to be able to replace text, except when selecting non editable text.
            final InputMethodManager imm = /*InputMethodManager.peekInstance*/getInputMethodManager();
            if (imm != null) {
                imm.showSoftInput(mTextView, 0, null);
            }
        }
        return selectionStarted;
    }

    void invalidateHandlesAndActionMode() {
        if (mSelectionModifierCursorController != null) {
            mSelectionModifierCursorController.invalidateHandles();
        }
        if (mInsertionPointCursorController != null) {
            mInsertionPointCursorController.invalidateHandle();
        }
        if (mTextActionMode != null) {
            invalidateActionMode();
        }
    }

    /**
     * Asynchronously invalidates an action mode using the TextClassifier.
     */
    void invalidateActionModeAsync() {
        getSelectionActionModeHelper().invalidateActionModeAsync();
    }

    public boolean extractedTextModeWillBeStarted() {
        if (!(mTextView.isInExtractedMode())) {
            final InputMethodManager imm = getInputMethodManager();
            return  imm != null && imm.isFullscreenMode();
        }
        return false;
    }


    public PositionListener getPositionListener() {
        if (mPositionListener == null) {
            mPositionListener = new PositionListener(mTextView);
        }
        return mPositionListener;
    }

    public void prepareCursorControllers() {
        boolean windowSupportsHandles = false;

        ViewGroup.LayoutParams params = mTextView.getRootView().getLayoutParams();
        if (params instanceof WindowManager.LayoutParams) {
            WindowManager.LayoutParams windowParams = (WindowManager.LayoutParams) params;
            windowSupportsHandles = windowParams.type < WindowManager.LayoutParams.FIRST_SUB_WINDOW
                    || windowParams.type > WindowManager.LayoutParams.LAST_SUB_WINDOW;
        }

        boolean enabled = windowSupportsHandles && mTextView.getLayout() != null;
        mInsertionControllerEnabled = enabled && isCursorVisible();
        mSelectionControllerEnabled = enabled && mTextView.textCanBeSelected();

        Log.w(TAG, "prepareCursorControllers: mInsertionControllerEnabled=" + mInsertionControllerEnabled
                + ", mInsertionPointCursorController=" + mInsertionPointCursorController);
        if (!mInsertionControllerEnabled) {
            hideInsertionPointCursorController();
            if (mInsertionPointCursorController != null) {
                mInsertionPointCursorController.onDetached();
                mInsertionPointCursorController = null;
            }
        }

        if (!mSelectionControllerEnabled) {
            stopTextActionMode();
            if (mSelectionModifierCursorController != null) {
                mSelectionModifierCursorController.onDetached();
                mSelectionModifierCursorController = null;
            }
        }
    }

    public void hideInsertionPointCursorController() {
        Log.w(TAG, "hideInsertionPointCursorController: mInsertionPointCursorController=" + mInsertionPointCursorController);
        if (mInsertionPointCursorController != null) {
            mInsertionPointCursorController.hide();
        }
    }

    /**
     * Hides the insertion and span controllers.
     */
    void hideCursorAndSpanControllers() {
        hideCursorControllers();
        hideSpanControllers();
    }

    private void hideSpanControllers() {
        if (mSpanController != null) {
            mSpanController.hide();
        }
    }

    private void hideCursorControllers() {
        // When mTextView is not ExtractEditText, we need to distinguish two kinds of focus-lost.
        // One is the true focus lost where suggestions pop-up (if any) should be dismissed, and the
        // other is an side effect of showing the suggestions pop-up itself. We use isShowingUp()
        // to distinguish one from the other.
//        if (mSuggestionsPopupWindow != null && ((mTextView.isInExtractedMode())
//                || !mSuggestionsPopupWindow.isShowingUp())) {
//            // Should be done before hide insertion point controller since it triggers a show of it
//            mSuggestionsPopupWindow.hide();
//        }
        hideInsertionPointCursorController();
    }

    /**
     * @return True if this view supports insertion handles.
     */
    boolean hasInsertionController() {
        return mInsertionControllerEnabled;
    }

    /**
     * @return True if this view supports selection handles.
     */
    public boolean hasSelectionController() {
        return mSelectionControllerEnabled;
    }

    public InsertionPointCursorController28 getInsertionController() {
        if (!mInsertionControllerEnabled) {
            Log.w(TAG, "getInsertionController: !mInsertionControllerEnabled");
            return null;
        }

        if (mInsertionPointCursorController == null) {
            mInsertionPointCursorController = new InsertionPointCursorController28(mTextView);

            final ViewTreeObserver observer = mTextView.getViewTreeObserver();
            observer.addOnTouchModeChangeListener(mInsertionPointCursorController);
            Log.w(TAG, "getInsertionController: creating mInsertionPointCursorController=" + mInsertionPointCursorController);
        }

        return mInsertionPointCursorController;
    }

    @Nullable
    public SelectionModifierCursorController28 getSelectionController() {
        if (!mSelectionControllerEnabled) {
            return null;
        }

        if (mSelectionModifierCursorController == null) {
            mSelectionModifierCursorController = new SelectionModifierCursorController28(mTextView);

            final ViewTreeObserver observer = mTextView.getViewTreeObserver();
            observer.addOnTouchModeChangeListener(mSelectionModifierCursorController);
            Log.w(TAG, "getSelectionController: creating mSelectionModifierCursorController=" + mSelectionModifierCursorController);
        }

        return mSelectionModifierCursorController;
    }

//    /**
//     * Loads the insertion and selection handle Drawables from TextView. If the handle
//     * drawables are already loaded, do not overwrite them unless the method parameter
//     * is set to true. This logic is required to avoid overwriting Drawables assigned
//     * to mSelectHandle[Center/Left/Right] by developers using reflection, unless they
//     * explicitly call the setters in TextView.
//     *
//     * @param overwrite whether to overwrite already existing nonnull Drawables
//     */
//    void loadHandleDrawables(final boolean overwrite) {
//        if (mSelectHandleCenter == null || overwrite) {
//            mSelectHandleCenter = mTextView.getTextSelectHandle();
//            if (hasInsertionController()) {
//                getInsertionController().reloadHandleDrawable();
//            }
//        }
//
//        if (mSelectHandleLeft == null || mSelectHandleRight == null || overwrite) {
//            mSelectHandleLeft = mTextView.getTextSelectHandleLeft();
//            mSelectHandleRight = mTextView.getTextSelectHandleRight();
//            if (hasSelectionController()) {
//                getSelectionController().reloadHandleDrawables();
//            }
//        }
//    }





    public int getWordStart(int offset) {
//        // FIXME - For this and similar methods we're not doing anything to check if there's
//        // a LocaleSpan in the text, this may be something we should try handling or checking for.
//        int retOffset = getWordIteratorWithText().prevBoundary(offset);
//        if (getWordIteratorWithText().isOnPunctuation(retOffset)) {
//            // On punctuation boundary or within group of punctuation, find punctuation start.
//            retOffset = getWordIteratorWithText().getPunctuationBeginning(offset);
//        } else {
//            // Not on a punctuation boundary, find the word start.
//            retOffset = getWordIteratorWithText().getPrevWordBeginningOnTwoWordsBoundary(offset);
//        }
//        if (retOffset == BreakIterator.DONE) {
//            return offset;
//        }
//        return retOffset;
        return offset;
    }

    public int getWordEnd(int offset) {
//        int retOffset = getWordIteratorWithText().nextBoundary(offset);
//        if (getWordIteratorWithText().isAfterPunctuation(retOffset)) {
//            // On punctuation boundary or within group of punctuation, find punctuation end.
//            retOffset = getWordIteratorWithText().getPunctuationEnd(offset);
//        } else {
//            // Not on a punctuation boundary, find the word end.
//            retOffset = getWordIteratorWithText().getNextWordEndOnTwoWordBoundary(offset);
//        }
//        if (retOffset == BreakIterator.DONE) {
//            return offset;
//        }
//        return retOffset;
        return offset;
    }

    public WordIterator28 getWordIterator() {
        if (mWordIterator == null) {
            mWordIterator = new WordIterator28(mTextView.getTextServicesLocale());
        }
        return mWordIterator;
    }

    public WordIterator28 getWordIteratorWithText() {
        if (mWordIteratorWithText == null) {
            mWordIteratorWithText = new WordIterator28(mTextView.getTextServicesLocale());
            mUpdateWordIteratorText = true;
        }
        if (mUpdateWordIteratorText) {
            // FIXME - Shouldn't copy all of the text as only the area of the text relevant
            // to the user's selection is needed. A possible solution would be to
            // copy some number N of characters near the selection and then when the
            // user approaches N then we'd do another copy of the next N characters.
            CharSequence text = mTextView.getText();
            mWordIteratorWithText.setCharSequence(text, 0, text.length());
            mUpdateWordIteratorText = false;
        }
        return mWordIteratorWithText;
    }

    public int getNextCursorOffset(int offset, boolean findAfterGivenOffset) {
        final Layout layout = mTextView.getLayout();
        if (layout == null) return offset;
        return findAfterGivenOffset == layout.isRtlCharAt(offset)
                ? layout.getOffsetToLeftOf(offset) : layout.getOffsetToRightOf(offset);
    }

    private long getCharClusterRange(int offset) {
        final int textLength = mTextView.getText().length();
        if (offset < textLength) {
            final int clusterEndOffset = getNextCursorOffset(offset, true);
            return HiddenTextUtils.packRangeInLong(
                    getNextCursorOffset(clusterEndOffset, false), clusterEndOffset);
        }
        if (offset - 1 >= 0) {
            final int clusterStartOffset = getNextCursorOffset(offset, false);
            return HiddenTextUtils.packRangeInLong(clusterStartOffset,
                    getNextCursorOffset(clusterStartOffset, true));
        }
        return HiddenTextUtils.packRangeInLong(offset, offset);
    }



    private boolean isCursorVisible() {
        // The default value is true, even when there is no associated Editor
        return mCursorVisible && mTextView.isTextEditable();
    }


    @NonNull
    public CustomEditTextView getTextView() {
        return mTextView;
    }


    static void logCursor(String location, @Nullable String msgFormat, Object ... msgArgs) {
        if (msgFormat == null) {
            Log.d(TAG, location);
        } else {
            Log.d(TAG, location + ": " + String.format(msgFormat, msgArgs));
        }
    }
}
