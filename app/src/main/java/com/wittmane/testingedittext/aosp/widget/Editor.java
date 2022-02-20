package com.wittmane.testingedittext.aosp.widget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.SystemClock;
import android.text.Layout;
import android.text.ParcelableSpan;
import android.text.Selection;
import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.KeyListener;
import android.text.method.MetaKeyKeyListener;
import android.text.style.URLSpan;
import android.util.Log;
import android.util.SparseArray;
import android.view.ActionMode;
import android.view.ActionMode.Callback;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.textclassifier.TextClassification;
import android.widget.PopupWindow;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.wittmane.testingedittext.aosp.text.method.MovementMethod;
import com.wittmane.testingedittext.aosp.text.method.WordIterator;
import com.wittmane.testingedittext.aosp.text.style.EasyEditSpan;
import com.wittmane.testingedittext.CustomInputConnection;
import com.wittmane.testingedittext.HiddenTextUtils;
import com.wittmane.testingedittext.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Editor {
    private static final String TAG = Editor.class.getSimpleName();
    private static final boolean DEBUG_UNDO = false;
    // Specifies whether to use or not the magnifier when pressing the insertion or selection
    // handles.
    private static final boolean FLAG_USE_MAGNIFIER = true;

    static final int BLINK = 500;
    private static final int DRAG_SHADOW_MAX_TEXT_LENGTH = 20;
    private static final float LINE_SLOP_MULTIPLIER_FOR_HANDLEVIEWS = 0.5f;
    private static final int UNSET_X_VALUE = -1;
    private static final int UNSET_LINE = -1;
    // Tag used when the Editor maintains its own separate UndoManager.
    private static final String UNDO_OWNER_TAG = "Editor";

    // Ordering constants used to place the Action Mode or context menu items in their menu.
    private static final int MENU_ITEM_ORDER_ASSIST = 0;
    private static final int MENU_ITEM_ORDER_UNDO = 2;
    private static final int MENU_ITEM_ORDER_REDO = 3;
    private static final int MENU_ITEM_ORDER_CUT = 4;
    private static final int MENU_ITEM_ORDER_COPY = 5;
    private static final int MENU_ITEM_ORDER_PASTE = 6;
    private static final int MENU_ITEM_ORDER_SHARE = 7;
    private static final int MENU_ITEM_ORDER_SELECT_ALL = 8;
    private static final int MENU_ITEM_ORDER_REPLACE = 9;
    private static final int MENU_ITEM_ORDER_AUTOFILL = 10;
    private static final int MENU_ITEM_ORDER_PASTE_AS_PLAIN_TEXT = 11;
    private static final int MENU_ITEM_ORDER_SECONDARY_ASSIST_ACTIONS_START = 50;
    private static final int MENU_ITEM_ORDER_PROCESS_TEXT_INTENT_ACTIONS_START = 100;

    @IntDef({MagnifierHandleTrigger.SELECTION_START,
            MagnifierHandleTrigger.SELECTION_END,
            MagnifierHandleTrigger.INSERTION})
    @Retention(RetentionPolicy.SOURCE)
    private @interface MagnifierHandleTrigger {
        int INSERTION = 0;
        int SELECTION_START = 1;
        int SELECTION_END = 2;
    }

    @IntDef({TextActionMode.SELECTION, TextActionMode.INSERTION, TextActionMode.TEXT_LINK})
    @interface TextActionMode {
        int SELECTION = 0;
        int INSERTION = 1;
        int TEXT_LINK = 2;
    }

    // Each Editor manages its own undo stack.
//    private final UndoManager mUndoManager = new UndoManager();
//    private UndoOwner mUndoOwner = mUndoManager.getOwner(UNDO_OWNER_TAG, this);
//    final UndoInputFilter mUndoInputFilter = new UndoInputFilter(this);
    boolean mAllowUndo = true;

//    private final MetricsLogger mMetricsLogger = new MetricsLogger();

    // Cursor Controllers.
    private InsertionPointCursorController mInsertionPointCursorController;
    SelectionModifierCursorController mSelectionModifierCursorController;
    // Action mode used when text is selected or when actions on an insertion cursor are triggered.
    private ActionMode mTextActionMode;
    private boolean mInsertionControllerEnabled;
    private boolean mSelectionControllerEnabled;

    private final boolean mHapticTextHandleEnabled;

    // Used to highlight a word when it is corrected by the IME
//    private CorrectionHighlighter mCorrectionHighlighter;

    InputContentType mInputContentType;
    InputMethodState mInputMethodState;

    boolean mFrozenWithFocus;
    boolean mSelectionMoved;
    boolean mTouchFocusSelected;

    KeyListener mKeyListener;
    int mInputType = EditorInfo.TYPE_NULL;

    boolean mDiscardNextActionUp;
    boolean mIgnoreActionUpEvent;

    private long mShowCursor;
    private boolean mRenderCursorRegardlessTiming;
    private Blink mBlink;

    boolean mCursorVisible = true;
    boolean mSelectAllOnFocus;
    boolean mTextIsSelectable;

    CharSequence mError;
    boolean mErrorWasChanged;
//    private ErrorPopup mErrorPopup;

    /**
     * This flag is set if the TextView tries to display an error before it
     * is attached to the window (so its position is still unknown).
     * It causes the error to be shown later, when onAttachedToWindow()
     * is called.
     */
    private boolean mShowErrorAfterAttach;

    boolean mInBatchEditControllers;
    boolean mShowSoftInputOnFocus = true;
    private boolean mPreserveSelection;
    private boolean mRestartActionModeOnNextRefresh;
    private boolean mRequestingLinkActionMode;

    private SelectionActionModeHelper mSelectionActionModeHelper;

    boolean mIsBeingLongClicked;

//    private SuggestionsPopupWindow mSuggestionsPopupWindow;
//    SuggestionRangeSpan mSuggestionRangeSpan;
    private Runnable mShowSuggestionRunnable;

    Drawable mDrawableForCursor = null;

    private Drawable mSelectHandleLeft;
    private Drawable mSelectHandleRight;
    private Drawable mSelectHandleCenter;

    // Global listener that detects changes in the global position of the TextView
    private PositionListener mPositionListener;

    private float mLastDownPositionX, mLastDownPositionY;
    private float mLastUpPositionX, mLastUpPositionY;
    private float mContextMenuAnchorX, mContextMenuAnchorY;
    Callback mCustomSelectionActionModeCallback;
    Callback mCustomInsertionActionModeCallback;

    // Set when this TextView gained focus with some text selected. Will start selection mode.
    boolean mCreatedWithASelection;

    // Indicates the current tap state (first tap, double tap, or triple click).
    private int mTapState = TAP_STATE_INITIAL;
    private long mLastTouchUpTime = 0;
    private static final int TAP_STATE_INITIAL = 0;
    private static final int TAP_STATE_FIRST_TAP = 1;
    private static final int TAP_STATE_DOUBLE_TAP = 2;
    // Only for mouse input.
    private static final int TAP_STATE_TRIPLE_CLICK = 3;

    // The button state as of the last time #onTouchEvent is called.
    private int mLastButtonState;

    private Runnable mInsertionActionModeRunnable;

    // The span controller helps monitoring the changes to which the Editor needs to react:
    // - EasyEditSpans, for which we have some UI to display on attach and on hide
    // - SelectionSpans, for which we need to call updateSelection if an IME is attached
    private SpanController mSpanController;

    private WordIterator mWordIterator;
//    SpellChecker mSpellChecker;

    // This word iterator is set with text and used to determine word boundaries
    // when a user is selecting text.
    private WordIterator mWordIteratorWithText;
    // Indicate that the text in the word iterator needs to be updated.
    private boolean mUpdateWordIteratorText;

    private Rect mTempRect;

    private final EditText mTextView;

    final ProcessTextIntentActionsHandler mProcessTextIntentActionsHandler;

//    private final CursorAnchorInfoNotifier mCursorAnchorInfoNotifier =
//            new CursorAnchorInfoNotifier();

    boolean mIsInsertionActionModeStartPending = false;

//    private final SuggestionHelper mSuggestionHelper = new SuggestionHelper();

    private int mLineChangeSlopMax;
    private int mLineChangeSlopMin;

    Editor(EditText textView) {
        mTextView = textView;
        mProcessTextIntentActionsHandler = new ProcessTextIntentActionsHandler(this);
        mHapticTextHandleEnabled = /*mTextView.getContext().getResources().getBoolean(
                R.bool.config_enableHapticTextHandle)*/false;
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

    void createInputContentTypeIfNeeded() {
        if (mInputContentType == null) {
            mInputContentType = new InputContentType();
        }
    }

    void createInputMethodStateIfNeeded() {
        if (mInputMethodState == null) {
            mInputMethodState = new InputMethodState();
        }
    }

    private boolean isCursorVisible() {
        // The default value is true, even when there is no associated Editor
        return mCursorVisible && mTextView.isTextEditable();
    }

    boolean shouldRenderCursor() {
        if (!isCursorVisible()) {
            return false;
        }
        if (mRenderCursorRegardlessTiming) {
            return true;
        }
        final long showCursorDelta = SystemClock.uptimeMillis() - mShowCursor;
        return showCursorDelta % (2 * BLINK) < BLINK;
    }

    void prepareCursorControllers() {
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

    void hideInsertionPointCursorController() {
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

    private void suspendBlink() {
        if (mBlink != null) {
            mBlink.cancel();
        }
    }

    private void resumeBlink() {
        if (mBlink != null) {
            mBlink.uncancel();
            makeBlink();
        }
    }

    void adjustInputType(boolean password, boolean passwordInputType,
                         boolean webPasswordInputType, boolean numberPasswordInputType) {
        // mInputType has been set from inputType, possibly modified by mInputMethod.
        // Specialize mInputType to [web]password if we have a text class and the original input
        // type was a password.
        if ((mInputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT) {
            if (password || passwordInputType) {
                mInputType = (mInputType & ~(EditorInfo.TYPE_MASK_VARIATION))
                        | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD;
            }
            if (webPasswordInputType) {
                mInputType = (mInputType & ~(EditorInfo.TYPE_MASK_VARIATION))
                        | EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD;
            }
        } else if ((mInputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_NUMBER) {
            if (numberPasswordInputType) {
                mInputType = (mInputType & ~(EditorInfo.TYPE_MASK_VARIATION))
                        | EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD;
            }
        }
    }

    private int getWordStart(int offset) {
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

    private int getWordEnd(int offset) {
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

    /**
     * Adjusts selection to the word under last touch offset. Return true if the operation was
     * successfully performed.
     */
    boolean selectCurrentWord() {
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
            final WordIterator wordIterator = getWordIterator();
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
    private boolean selectCurrentParagraph() {
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

    public WordIterator getWordIterator() {
        if (mWordIterator == null) {
            mWordIterator = new WordIterator(mTextView.getTextServicesLocale());
        }
        return mWordIterator;
    }

    private WordIterator getWordIteratorWithText() {
        if (mWordIteratorWithText == null) {
            mWordIteratorWithText = new WordIterator(mTextView.getTextServicesLocale());
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

    private int getNextCursorOffset(int offset, boolean findAfterGivenOffset) {
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

    private PositionListener getPositionListener() {
        if (mPositionListener == null) {
            mPositionListener = new PositionListener();
        }
        return mPositionListener;
    }

    private interface TextViewPositionListener {
        void updatePosition(int parentPositionX, int parentPositionY,
                            boolean parentPositionChanged, boolean parentScrolled);
    }

    /** Returns true if the screen coordinates position (x,y) corresponds to a character displayed
     * in the view. Returns false when the position is in the empty space of left/right of text.
     */
    private boolean isPositionOnText(float x, float y) {
        Layout layout = mTextView.getLayout();
        if (layout == null) return false;

        final int line = mTextView.getLineAtCoordinate(y);
        x = mTextView.convertToLocalHorizontalCoordinate(x);

        if (x < layout.getLineLeft(line)) return false;
        if (x > layout.getLineRight(line)) return false;
        return true;
    }

    private void startDragAndDrop() {
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

    private void toggleInsertionActionMode() {
        if (mTextActionMode != null) {
            stopTextActionMode();
        } else {
            startInsertionActionMode();
        }
    }

    float getLastUpPositionX() {
        return mLastUpPositionX;
    }

    float getLastUpPositionY() {
        return mLastUpPositionY;
    }

    private long getLastTouchOffsets() {
        SelectionModifierCursorController selectionController = getSelectionController();
        final int minOffset = selectionController.getMinTouchOffset();
        final int maxOffset = selectionController.getMaxTouchOffset();
        return HiddenTextUtils.packRangeInLong(minOffset, maxOffset);
    }

    void onFocusChanged(boolean focused, int direction) {
        mShowCursor = SystemClock.uptimeMillis();
        ensureEndedBatchEdit();

        if (focused) {
            int selStart = mTextView.getSelectionStart();
            int selEnd = mTextView.getSelectionEnd();

            // SelectAllOnFocus fields are highlighted and not selected. Do not start text selection
            // mode for these, unless there was a specific selection already started.
            final boolean isFocusHighlighted = mSelectAllOnFocus && selStart == 0
                    && selEnd == mTextView.getText().length();

            mCreatedWithASelection = mFrozenWithFocus && mTextView.hasSelection()
                    && !isFocusHighlighted;

            if (!mFrozenWithFocus || (selStart < 0 || selEnd < 0)) {
                // If a tap was used to give focus to that view, move cursor at tap position.
                // Has to be done before onTakeFocus, which can be overloaded.
                final int lastTapPosition = getLastTapPosition();
                if (lastTapPosition >= 0) {
                    Selection.setSelection((Spannable) mTextView.getText(), lastTapPosition);
                }

                // Note this may have to be moved out of the Editor class
                MovementMethod mMovement = mTextView.getMovementMethod();
                if (mMovement != null) {
                    mMovement.onTakeFocus(mTextView, (Spannable) mTextView.getText(), direction);
                }

                // The DecorView does not have focus when the 'Done' ExtractEditText button is
                // pressed. Since it is the ViewAncestor's mView, it requests focus before
                // ExtractEditText clears focus, which gives focus to the ExtractEditText.
                // This special case ensure that we keep current selection in that case.
                // It would be better to know why the DecorView does not have focus at that time.
                if (((mTextView.isInExtractedMode()) || mSelectionMoved)
                        && selStart >= 0 && selEnd >= 0) {
                    /*
                     * Someone intentionally set the selection, so let them
                     * do whatever it is that they wanted to do instead of
                     * the default on-focus behavior.  We reset the selection
                     * here instead of just skipping the onTakeFocus() call
                     * because some movement methods do something other than
                     * just setting the selection in theirs and we still
                     * need to go through that path.
                     */
                    Selection.setSelection((Spannable) mTextView.getText(), selStart, selEnd);
                }

                if (mSelectAllOnFocus) {
                    mTextView.selectAllText();
                }

                mTouchFocusSelected = true;
            }

            mFrozenWithFocus = false;
            mSelectionMoved = false;

//            if (mError != null) {
//                showError();
//            }

            makeBlink();
        } else {
//            if (mError != null) {
//                hideError();
//            }
            // Don't leave us in the middle of a batch edit.
            mTextView.onEndBatchEdit();

            if (mTextView.isInExtractedMode()) {
                hideCursorAndSpanControllers();
//                stopTextActionModeWithPreservingSelection();
            } else {
                hideCursorAndSpanControllers();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    if (mTextView.isTemporarilyDetached()) {
//                        stopTextActionModeWithPreservingSelection();
                    } else {
                        stopTextActionMode();
                    }
                }
//                downgradeEasyCorrectionSpans();
            }
            // No need to create the controller
//            if (mSelectionModifierCursorController != null) {
//                mSelectionModifierCursorController.resetTouchOffsets();
//            }

//            ensureNoSelectionIfNonSelectable();
        }
    }

    void sendOnTextChanged(int start, int before, int after) {
//        getSelectionActionModeHelper().onTextChanged(start, start + before);
//        updateSpellCheckSpans(start, start + after, false);

        // Flip flag to indicate the word iterator needs to have the text reset.
//        mUpdateWordIteratorText = true;

        // Hide the controllers as soon as text is modified (typing, procedural...)
        // We do not hide the span controllers, since they can be added when a new text is
        // inserted into the text view (voice IME).
//        hideCursorControllers();
        // Reset drag accelerator.
//        if (mSelectionModifierCursorController != null) {
//            mSelectionModifierCursorController.resetTouchOffsets();
//        }
//        stopTextActionMode();
    }

    private int getLastTapPosition() {
        // No need to create the controller at that point, no last tap position saved
        if (mSelectionModifierCursorController != null) {
            int lastTapPosition = mSelectionModifierCursorController.getMinTouchOffset();
            if (lastTapPosition >= 0) {
                // Safety check, should not be possible.
                if (lastTapPosition > mTextView.getText().length()) {
                    lastTapPosition = mTextView.getText().length();
                }
                return lastTapPosition;
            }
        }
//        int lastTapPosition = mTouchManager.getMinTouchOffset();
//        if (lastTapPosition >= 0) {
//            // Safety check, should not be possible.
//            if (lastTapPosition > mText.length()) {
//                lastTapPosition = mText.length();
//            }
//            return lastTapPosition;
//        }

        return -1;
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
//    /**
//     * Handles touch events on an editable text view, implementing cursor movement, selection, etc.
//     */
//    public void onTouchEvent(MotionEvent event) {
//        final boolean filterOutEvent = shouldFilterOutTouchEvent(event);
//        mLastButtonState = event.getButtonState();
//        if (filterOutEvent) {
//            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
//                mDiscardNextActionUp = true;
//            }
//            return;
//        }
//        ViewConfiguration viewConfiguration = ViewConfiguration.get(getContext());
//        mTouchState.update(event, viewConfiguration);
////        updateFloatingToolbarVisibility(event);
////
////        if (hasInsertionController()) {
////            getInsertionController().onTouchEvent(event);
////        }
////        if (hasSelectionController()) {
////            getSelectionController().onTouchEvent(event);
////        }
////
////        if (mShowSuggestionRunnable != null) {
////            mTextView.removeCallbacks(mShowSuggestionRunnable);
////            mShowSuggestionRunnable = null;
////        }
//
//        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
//            // Reset this state; it will be re-set if super.onTouchEvent
//            // causes focus to move to the view.
//            mTouchFocusSelected = false;
//            mIgnoreActionUpEvent = false;
//        }
//    }
//    void onTouchUpEvent(MotionEvent event) {
////        if (TextView.DEBUG_CURSOR) {
////            logCursor("onTouchUpEvent", null);
////        }
//        if (getSelectionActionModeHelper().resetSelection(
//                /*getTextView().*/getOffsetForPosition(event.getX(), event.getY()))) {
//            return;
//        }
//
//        boolean selectAllGotFocus = mSelectAllOnFocus && /*mTextView.*/didTouchFocusSelect();
//        hideCursorAndSpanControllers();
//        stopTextActionMode();
//        CharSequence text = mText;
//        if (!selectAllGotFocus && text.length() > 0) {
//            // Move cursor
//            final int offset = getOffsetForPosition(event.getX(), event.getY());
//
//            final boolean shouldInsertCursor = !mRequestingLinkActionMode;
//            if (shouldInsertCursor) {
//                Selection.setSelection((Spannable) text, offset);
////                if (mSpellChecker != null) {
////                    // When the cursor moves, the word that was typed may need spell check
////                    mSpellChecker.onSelectionChanged();
////                }
//            }
//
////            if (!extractedTextModeWillBeStarted()) {
////                if (isCursorInsideEasyCorrectionSpan()) {
////                    // Cancel the single tap delayed runnable.
////                    if (mInsertionActionModeRunnable != null) {
////                        removeCallbacks(mInsertionActionModeRunnable);
////                    }
////
////                    mShowSuggestionRunnable = this::replace;
////
////                    // removeCallbacks is performed on every touch
////                    postDelayed(mShowSuggestionRunnable,
////                            ViewConfiguration.getDoubleTapTimeout());
////                } else if (hasInsertionController()) {
////                    if (shouldInsertCursor) {
////                        getInsertionController().show();
////                    } else {
////                        getInsertionController().hide();
////                    }
////                }
////            }
//        }
//    }

    private InputMethodManager getInputMethodManager() {
        return mTextView.getInputMethodManager();
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

    static final int EXTRACT_NOTHING = -2;
    static final int EXTRACT_UNKNOWN = -1;

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
        final InputMethodState ims = mInputMethodState;
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
        if (EditText.DEBUG_EXTRACT) {
            Log.v(EditText.LOG_TAG, "Retrieving extracted start="
                    + ims.mChangedStart
                    + " end=" + ims.mChangedEnd
                    + " delta=" + ims.mChangedDelta);
        }
        if (ims.mChangedStart < 0 && !wasContentChanged) {
            ims.mChangedStart = EXTRACT_NOTHING;
        }
        if (extractTextInternal(req, ims.mChangedStart, ims.mChangedEnd,
                ims.mChangedDelta, ims.mExtractedText)) {
            if (EditText.DEBUG_EXTRACT) {
                Log.v(EditText.LOG_TAG,
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
                    candStart = CustomInputConnection.getComposingSpanStart(sp);
                    candEnd = CustomInputConnection.getComposingSpanEnd(sp);
                }
                // InputMethodManager#updateSelection skips sending the message if
                // none of the parameters have changed since the last time we called it.
                imm.updateSelection(mTextView,
                        selectionStart, selectionEnd, candStart, candEnd);
            }
        }
    }

    void onDraw(Canvas canvas, Layout layout, Path highlight, Paint highlightPaint,
                int cursorOffsetVertical) {
        final int selectionStart = mTextView.getSelectionStart();
        final int selectionEnd = mTextView.getSelectionEnd();

        final InputMethodState ims = mInputMethodState;
        if (ims != null && ims.mBatchEditNesting == 0) {
            InputMethodManager imm = getInputMethodManager();
            if (imm != null) {
                if (imm.isActive(mTextView)) {
                    if (ims.mContentChanged || ims.mSelectionModeChanged) {
                        // We are in extract mode and the content has changed
                        // in some way... just report complete new text to the
                        // input method.
                        reportExtractedText();
                    }
                }
            }
        }

//        if (mCorrectionHighlighter != null) {
//            mCorrectionHighlighter.draw(canvas, cursorOffsetVertical);
//        }

        if (highlight != null && selectionStart == selectionEnd && mDrawableForCursor != null) {
            drawCursor(canvas, cursorOffsetVertical);
            // Rely on the drawable entirely, do not draw the cursor line.
            // Has to be done after the IMM related code above which relies on the highlight.
            highlight = null;
        }

        if (mSelectionActionModeHelper != null) {
            mSelectionActionModeHelper.onDraw(canvas);
            if (mSelectionActionModeHelper.isDrawingHighlight()) {
                highlight = null;
            }
        }

//        if (mTextView.canHaveDisplayList() && canvas.isHardwareAccelerated()) {
//            drawHardwareAccelerated(canvas, layout, highlight, highlightPaint,
//                    cursorOffsetVertical);
//        } else {
            layout.draw(canvas, highlight, highlightPaint, cursorOffsetVertical);
//        }
    }

    private void drawCursor(Canvas canvas, int cursorOffsetVertical) {
//        Log.w(TAG, "drawCursor: selectionStart=" + getSelectionStart() + ", selectionEnd=" + getSelectionEnd());
        final boolean translate = cursorOffsetVertical != 0;
        if (translate) canvas.translate(0, cursorOffsetVertical);
        if (mDrawableForCursor != null) {
            mDrawableForCursor.draw(canvas);
        }
        if (translate) canvas.translate(0, -cursorOffsetVertical);
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

    private void updateCursorPosition(int top, int bottom, float horizontal) {
        loadCursorDrawable();
        final int left = clampHorizontalPosition(mDrawableForCursor, horizontal);
        final int width = mDrawableForCursor.getIntrinsicWidth();
        Log.w(TAG, String.format("updateCursorPosition: left=%s, top=%s", left, (top - mTempRect.top)));
        mDrawableForCursor.setBounds(left, top - mTempRect.top, left + width,
                bottom + mTempRect.bottom);
    }

    void refreshTextActionMode() {
        if (extractedTextModeWillBeStarted()) {
            mRestartActionModeOnNextRefresh = false;
            return;
        }
        final boolean hasSelection = mTextView.hasSelection();
        final SelectionModifierCursorController selectionController = getSelectionController();
        final InsertionPointCursorController insertionController = getInsertionController();
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

    /**
     * Start an Insertion action mode.
     */
    void startInsertionActionMode() {
        Log.w(TAG, "startInsertionActionMode");
        if (mInsertionActionModeRunnable != null) {
            mTextView.removeCallbacks(mInsertionActionModeRunnable);
        }
        if (extractedTextModeWillBeStarted()) {
            return;
        }
        stopTextActionMode();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Callback actionModeCallback =
                    new TextActionModeCallback(TextActionMode.INSERTION);
            mTextActionMode = mTextView.startActionMode(
                    actionModeCallback, ActionMode.TYPE_FLOATING);
        } else {
            //TODO: handle
        }
        if (mTextActionMode != null && getInsertionController() != null) {
            getInsertionController().show();
        }
    }

    @NonNull
    EditText getTextView() {
        return mTextView;
    }

    @Nullable
    ActionMode getTextActionMode() {
        return mTextActionMode;
    }

    void setRestartActionModeOnNextRefresh(boolean value) {
        mRestartActionModeOnNextRefresh = value;
    }

    /**
     * Asynchronously starts a selection action mode using the TextClassifier.
     */
    void startSelectionActionModeAsync(boolean adjustSelection) {
        getSelectionActionModeHelper().startSelectionActionModeAsync(adjustSelection);
    }

    /**
     * Asynchronously invalidates an action mode using the TextClassifier.
     */
    void invalidateActionModeAsync() {
        getSelectionActionModeHelper().invalidateActionModeAsync();
    }

    /**
     * Synchronously invalidates an action mode without the TextClassifier.
     */
    private void invalidateActionMode() {
        if (mTextActionMode != null) {
            mTextActionMode.invalidate();
        }
    }

    private SelectionActionModeHelper getSelectionActionModeHelper() {
        if (mSelectionActionModeHelper == null) {
            mSelectionActionModeHelper = new SelectionActionModeHelper(this);
            Log.w(TAG, "getSelectionActionModeHelper: create mSelectionActionModeHelper=" + mSelectionActionModeHelper);
        }
        return mSelectionActionModeHelper;
    }
//    private SelectionActionModeHelper getSelectionActionModeHelper() {
//        if (mSelectionActionModeHelper == null) {
//            mSelectionActionModeHelper = new SelectionActionModeHelper(this);
//        }
//        return mSelectionActionModeHelper;
//    }

    /**
     * If the TextView allows text selection, selects the current word when no existing selection
     * was available and starts a drag.
     *
     * @return true if the drag was started.
     */
    private boolean selectCurrentWordAndStartDrag() {
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
                SelectionModifierCursorController.DRAG_ACCELERATOR_MODE_WORD);
        return true;
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

    boolean startActionModeInternal(@TextActionMode int actionMode) {
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
            Callback actionModeCallback = new TextActionModeCallback(actionMode);
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

    private boolean extractedTextModeWillBeStarted() {
        if (!(mTextView.isInExtractedMode())) {
            final InputMethodManager imm = getInputMethodManager();
            return  imm != null && imm.isFullscreenMode();
        }
        return false;
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

    protected void stopTextActionMode() {
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

    /**
     * @return True if this view supports insertion handles.
     */
    boolean hasInsertionController() {
        return mInsertionControllerEnabled;
    }

    /**
     * @return True if this view supports selection handles.
     */
    boolean hasSelectionController() {
        return mSelectionControllerEnabled;
    }

    private InsertionPointCursorController getInsertionController() {
        if (!mInsertionControllerEnabled) {
            Log.w(TAG, "getInsertionController: !mInsertionControllerEnabled");
            return null;
        }

        if (mInsertionPointCursorController == null) {
            mInsertionPointCursorController = new InsertionPointCursorController(mTextView);

            final ViewTreeObserver observer = mTextView.getViewTreeObserver();
            observer.addOnTouchModeChangeListener(mInsertionPointCursorController);
            Log.w(TAG, "getInsertionController: creating mInsertionPointCursorController=" + mInsertionPointCursorController);
        }

        return mInsertionPointCursorController;
    }

    @Nullable
    SelectionModifierCursorController getSelectionController() {
        if (!mSelectionControllerEnabled) {
            return null;
        }

        if (mSelectionModifierCursorController == null) {
            mSelectionModifierCursorController = new SelectionModifierCursorController(mTextView);

            final ViewTreeObserver observer = mTextView.getViewTreeObserver();
            observer.addOnTouchModeChangeListener(mSelectionModifierCursorController);
            Log.w(TAG, "getSelectionController: creating mSelectionModifierCursorController=" + mSelectionModifierCursorController);
        }

        return mSelectionModifierCursorController;
    }
//    /** Returns the controller for selection. */
//    public @Nullable SelectionModifierCursorController getSelectionController() {
////        if (!mSelectionControllerEnabled) {
////            return null;
////        }
//
//        if (mSelectionModifierCursorController == null) {
//            mSelectionModifierCursorController = new SelectionModifierCursorController(this);
//
//            final ViewTreeObserver observer = getViewTreeObserver();
//            observer.addOnTouchModeChangeListener(mSelectionModifierCursorController);
//        }
//
//        return mSelectionModifierCursorController;
//    }

    void updateCursorPosition() {
        loadCursorDrawable();
        if (mDrawableForCursor == null) {
            return;
        }

        final Layout layout = mTextView.getLayout();
        final int offset = mTextView.getSelectionStart();
        final int line = layout.getLineForOffset(offset);
        final int top = layout.getLineTop(line);
        final int bottom = /*layout.getLineBottomWithoutSpacing(line)*/layout.getLineBottom(line);//hidden - hopefully this is good enough

//        final boolean clamped = layout.shouldClampCursor(line);
        updateCursorPosition(top, bottom, layout.getPrimaryHorizontal(offset/*, clamped*/));
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
    private int clampHorizontalPosition(@Nullable final Drawable drawable, float horizontal) {
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
                && (EditText.VERY_WIDE - scrollX) <= (viewClippedWidth + 1f)
                && horizontal <= 1f)) {
            // at the leftmost position
            left = scrollX - mTempRect.left;
        } else {
            left = (int) horizontal - mTempRect.left;
        }
        return left;
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

    /**
     * @return True when the TextView isFocused and has a valid zero-length selection (cursor).
     */
    private boolean shouldBlink() {
        if (!isCursorVisible() || !mTextView.isFocused()) return false;

        final int start = mTextView.getSelectionStart();
        if (start < 0) return false;

        final int end = mTextView.getSelectionEnd();
        if (end < 0) return false;

        return start == end;
    }

    void makeBlink() {
        if (shouldBlink()) {
//            Log.w(TAG, "makeBlink shouldBlink");
            mShowCursor = SystemClock.uptimeMillis();
            if (mBlink == null) mBlink = new Blink();
            mTextView.removeCallbacks(mBlink);
            mTextView.postDelayed(mBlink, BLINK);
        } else {
            Log.w(TAG, "makeBlink not shouldBlink");
            if (mBlink != null) mTextView.removeCallbacks(mBlink);
        }
    }

    private class Blink implements Runnable {
        private boolean mCancelled;

        public void run() {
            if (mCancelled) {
                return;
            }

            mTextView.removeCallbacks(this);

            if (shouldBlink()) {
                if (mTextView.getLayout() != null) {
//                    Log.w(TAG, "Blink invalidateCursorPath");
                    mTextView.invalidateCursorPath();
                }

                mTextView.postDelayed(this, BLINK);
            }
        }

        void cancel() {
            if (!mCancelled) {
                mTextView.removeCallbacks(this);
                mCancelled = true;
            }
        }

        void uncancel() {
            mCancelled = false;
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

    private final MenuItem.OnMenuItemClickListener mOnContextMenuItemClickListener =
            new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    if (mProcessTextIntentActionsHandler.performMenuItemAction(item)) {
                        return true;
                    }
                    return mTextView.onTextContextMenuItem(item.getItemId());
                }
            };

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


    private class PositionListener implements ViewTreeObserver.OnPreDrawListener {
        // 3 handles
        // 3 ActionPopup [replace, suggestion, easyedit] (suggestionsPopup first hides the others)
        // 1 CursorAnchorInfoNotifier
        private static final int MAXIMUM_NUMBER_OF_LISTENERS = 7;
        private TextViewPositionListener[] mPositionListeners =
                new TextViewPositionListener[MAXIMUM_NUMBER_OF_LISTENERS];
        private boolean[] mCanMove = new boolean[MAXIMUM_NUMBER_OF_LISTENERS];
        private boolean mPositionHasChanged = true;
        // Absolute position of the TextView with respect to its parent window
        private int mPositionX, mPositionY;
        private int mPositionXOnScreen, mPositionYOnScreen;
        private int mNumberOfListeners;
        private boolean mScrollHasChanged;
        final int[] mTempCoords = new int[2];

        public void addSubscriber(TextViewPositionListener positionListener, boolean canMove) {
            if (mNumberOfListeners == 0) {
                updatePosition();
                ViewTreeObserver vto = mTextView.getViewTreeObserver();
                vto.addOnPreDrawListener(this);
            }

            int emptySlotIndex = -1;
            for (int i = 0; i < MAXIMUM_NUMBER_OF_LISTENERS; i++) {
                TextViewPositionListener listener = mPositionListeners[i];
                if (listener == positionListener) {
                    return;
                } else if (emptySlotIndex < 0 && listener == null) {
                    emptySlotIndex = i;
                }
            }

            mPositionListeners[emptySlotIndex] = positionListener;
            mCanMove[emptySlotIndex] = canMove;
            mNumberOfListeners++;
        }

        public void removeSubscriber(TextViewPositionListener positionListener) {
            for (int i = 0; i < MAXIMUM_NUMBER_OF_LISTENERS; i++) {
                if (mPositionListeners[i] == positionListener) {
                    mPositionListeners[i] = null;
                    mNumberOfListeners--;
                    break;
                }
            }

            if (mNumberOfListeners == 0) {
                ViewTreeObserver vto = mTextView.getViewTreeObserver();
                vto.removeOnPreDrawListener(this);
            }
        }

        public int getPositionX() {
            return mPositionX;
        }

        public int getPositionY() {
            return mPositionY;
        }

        public int getPositionXOnScreen() {
            return mPositionXOnScreen;
        }

        public int getPositionYOnScreen() {
            return mPositionYOnScreen;
        }

        @Override
        public boolean onPreDraw() {
            updatePosition();

            for (int i = 0; i < MAXIMUM_NUMBER_OF_LISTENERS; i++) {
                if (mPositionHasChanged || mScrollHasChanged || mCanMove[i]) {
                    TextViewPositionListener positionListener = mPositionListeners[i];
                    if (positionListener != null) {
                        positionListener.updatePosition(mPositionX, mPositionY,
                                mPositionHasChanged, mScrollHasChanged);
                    }
                }
            }

            mScrollHasChanged = false;
            return true;
        }

        private void updatePosition() {
            mTextView.getLocationInWindow(mTempCoords);

            mPositionHasChanged = mTempCoords[0] != mPositionX || mTempCoords[1] != mPositionY;

            mPositionX = mTempCoords[0];
            mPositionY = mTempCoords[1];

            mTextView.getLocationOnScreen(mTempCoords);

            mPositionXOnScreen = mTempCoords[0];
            mPositionYOnScreen = mTempCoords[1];
        }

        public void onScrollChanged() {
            mScrollHasChanged = true;
        }
    }

    /**
     * An ActionMode Callback class that is used to provide actions while in text insertion or
     * selection mode.
     *
     * The default callback provides a subset of Select All, Cut, Copy, Paste, Share and Replace
     * actions, depending on which of these this TextView supports and the current selection.
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private class TextActionModeCallback extends ActionMode.Callback2 {
        private /*static */final String TAG = TextActionModeCallback.class.getSimpleName();


        private final Path mSelectionPath = new Path();
        private final RectF mSelectionBounds = new RectF();
        private final boolean mHasSelection;
        private final int mHandleHeight;
        private final Map<MenuItem, View.OnClickListener> mAssistClickHandlers = new HashMap<>();

        public TextActionModeCallback(@TextActionMode int mode) {
            mHasSelection = mode == TextActionMode.SELECTION
                    || (mTextIsSelectable && mode == TextActionMode.TEXT_LINK);
            if (mHasSelection) {
                SelectionModifierCursorController selectionController = getSelectionController();
                if (selectionController.mStartHandle == null) {
                    // As these are for initializing selectionController, hide() must be called.
                    selectionController.initDrawables();
                    selectionController.initHandles();
                    selectionController.hide();
                }
                mHandleHeight = Math.max(
                        mSelectHandleLeft.getMinimumHeight(),
                        mSelectHandleRight.getMinimumHeight());
            } else {
                InsertionPointCursorController insertionController = getInsertionController();
                if (insertionController != null) {
                    insertionController.getHandle();
                    mHandleHeight = mSelectHandleCenter.getMinimumHeight();
                } else {
                    mHandleHeight = 0;
                }
            }
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mAssistClickHandlers.clear();

            mode.setTitle(null);
            mode.setSubtitle(null);
            mode.setTitleOptionalHint(true);
            populateMenuWithItems(menu);

            Callback customCallback = getCustomCallback();
            if (customCallback != null) {
                if (!customCallback.onCreateActionMode(mode, menu)) {
                    // The custom mode can choose to cancel the action mode, dismiss selection.
                    Selection.setSelection((Spannable) mTextView.getText(),
                            mTextView.getSelectionEnd());
                    return false;
                }
            }

            if (mTextView.canProcessText()) {
//            mProcessTextIntentActionsHandler.onInitializeMenu(menu);
            }

            if (mHasSelection && !mTextView.hasTransientState()) {
                mTextView.setHasTransientState(true);
            }
            return true;
        }

        private Callback getCustomCallback() {
//        return mHasSelection
//                ? mCustomSelectionActionModeCallback
//                : mCustomInsertionActionModeCallback;
            return null;
        }

        private void populateMenuWithItems(Menu menu) {
            if (mTextView.canCut()) {
                menu.add(Menu.NONE, EditText.ID_CUT, MENU_ITEM_ORDER_CUT,
                        /*com.android.internal.R.string.cut*/android.R.string.cut)
                        .setAlphabeticShortcut('x')
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }

            if (mTextView.canCopy()) {
                menu.add(Menu.NONE, EditText.ID_COPY, MENU_ITEM_ORDER_COPY,
                        /*com.android.internal.R.string.copy*/android.R.string.copy)
                        .setAlphabeticShortcut('c')
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }

            if (mTextView.canPaste()) {
                menu.add(Menu.NONE, EditText.ID_PASTE, MENU_ITEM_ORDER_PASTE,
                        /*com.android.internal.R.string.paste*/android.R.string.paste)
                        .setAlphabeticShortcut('v')
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }

//        if (mTextView.canShare()) {
//            menu.add(Menu.NONE, EditText.ID_SHARE, MENU_ITEM_ORDER_SHARE,
//                    com.android.internal.R.string.share)
//                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
//        }

//        if (mTextView.canRequestAutofill()) {
//            final String selected = mTextView.getSelectedText();
//            if (selected == null || selected.isEmpty()) {
//                menu.add(Menu.NONE, EditText.ID_AUTOFILL, MENU_ITEM_ORDER_AUTOFILL,
//                        com.android.internal.R.string.autofill)
//                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
//            }
//        }

            if (mTextView.canPasteAsPlainText()) {
                menu.add(
                        Menu.NONE,
                        EditText.ID_PASTE_AS_PLAIN_TEXT,
                        MENU_ITEM_ORDER_PASTE_AS_PLAIN_TEXT,
                        /*com.android.internal.R.string.paste_as_plain_text*/android.R.string.paste_as_plain_text)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            }

            updateSelectAllItem(menu);
            updateReplaceItem(menu);
            updateAssistMenuItems(menu);
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            updateSelectAllItem(menu);
            updateReplaceItem(menu);
            updateAssistMenuItems(menu);

            Callback customCallback = getCustomCallback();
            if (customCallback != null) {
                return customCallback.onPrepareActionMode(mode, menu);
            }
            return true;
        }

        private void updateSelectAllItem(Menu menu) {
//        boolean canSelectAll = mTextView.canSelectAllText();
//        boolean selectAllItemExists = menu.findItem(EditText.ID_SELECT_ALL) != null;
//        if (canSelectAll && !selectAllItemExists) {
//            menu.add(Menu.NONE, EditText.ID_SELECT_ALL, MENU_ITEM_ORDER_SELECT_ALL,
//                    com.android.internal.R.string.selectAll)
//                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
//        } else if (!canSelectAll && selectAllItemExists) {
//            menu.removeItem(EditText.ID_SELECT_ALL);
//        }
        }

        private void updateReplaceItem(Menu menu) {
//        boolean canReplace = mTextView.isSuggestionsEnabled() && shouldOfferToShowSuggestions();
//        boolean replaceItemExists = menu.findItem(EditText.ID_REPLACE) != null;
//        if (canReplace && !replaceItemExists) {
//            menu.add(Menu.NONE, EditText.ID_REPLACE, MENU_ITEM_ORDER_REPLACE,
//                    com.android.internal.R.string.replace)
//                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
//        } else if (!canReplace && replaceItemExists) {
//            menu.removeItem(EditText.ID_REPLACE);
//        }
        }

        private void updateAssistMenuItems(Menu menu) {
//        clearAssistMenuItems(menu);
//        if (!shouldEnableAssistMenuItems()) {
//            return;
//        }
//        final TextClassification textClassification =
//                getSelectionActionModeHelper().getTextClassification();
//        if (textClassification == null) {
//            return;
//        }
//        if (!textClassification.getActions().isEmpty()) {
//            // Primary assist action (Always shown).
//            final MenuItem item = addAssistMenuItem(menu,
//                    textClassification.getActions().get(0), TextView.ID_ASSIST,
//                    MENU_ITEM_ORDER_ASSIST, MenuItem.SHOW_AS_ACTION_ALWAYS);
//            item.setIntent(textClassification.getIntent());
//        } else if (hasLegacyAssistItem(textClassification)) {
//            // Legacy primary assist action (Always shown).
//            final MenuItem item = menu.add(
//                    TextView.ID_ASSIST, TextView.ID_ASSIST, MENU_ITEM_ORDER_ASSIST,
//                    textClassification.getLabel())
//                    .setIcon(textClassification.getIcon())
//                    .setIntent(textClassification.getIntent());
//            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
//            mAssistClickHandlers.put(item, TextClassification.createIntentOnClickListener(
//                    TextClassification.createPendingIntent(mTextView.getContext(),
//                            textClassification.getIntent(),
//                            createAssistMenuItemPendingIntentRequestCode())));
//        }
//        final int count = textClassification.getActions().size();
//        for (int i = 1; i < count; i++) {
//            // Secondary assist action (Never shown).
//            addAssistMenuItem(menu, textClassification.getActions().get(i), Menu.NONE,
//                    MENU_ITEM_ORDER_SECONDARY_ASSIST_ACTIONS_START + i - 1,
//                    MenuItem.SHOW_AS_ACTION_NEVER);
//        }
        }

//    private MenuItem addAssistMenuItem(Menu menu, RemoteAction action, int intemId, int order,
//                                       int showAsAction) {
//        final MenuItem item = menu.add(TextView.ID_ASSIST, intemId, order, action.getTitle())
//                .setContentDescription(action.getContentDescription());
//        if (action.shouldShowIcon()) {
//            item.setIcon(action.getIcon().loadDrawable(mTextView.getContext()));
//        }
//        item.setShowAsAction(showAsAction);
//        mAssistClickHandlers.put(item,
//                TextClassification.createIntentOnClickListener(action.getActionIntent()));
//        return item;
//    }

//    private void clearAssistMenuItems(Menu menu) {
//        int i = 0;
//        while (i < menu.size()) {
//            final MenuItem menuItem = menu.getItem(i);
//            if (menuItem.getGroupId() == TextView.ID_ASSIST) {
//                menu.removeItem(menuItem.getItemId());
//                continue;
//            }
//            i++;
//        }
//    }

//    private boolean hasLegacyAssistItem(TextClassification classification) {
//        // Check whether we have the UI data and and action.
//        return (classification.getIcon() != null || !TextUtils.isEmpty(
//                classification.getLabel())) && (classification.getIntent() != null
//                || classification.getOnClickListener() != null);
//    }

        private boolean onAssistMenuItemClicked(MenuItem assistMenuItem) {
//        Preconditions.checkArgument(assistMenuItem.getGroupId() == TextView.ID_ASSIST);

            final TextClassification textClassification =
                    getSelectionActionModeHelper().getTextClassification();
            if (!shouldEnableAssistMenuItems() || textClassification == null) {
                // No textClassification result to handle the click. Eat the click.
                return true;
            }

            View.OnClickListener onClickListener = mAssistClickHandlers.get(assistMenuItem);
            if (onClickListener == null) {
                final Intent intent = assistMenuItem.getIntent();
                if (intent != null) {
//                onClickListener = TextClassification.createIntentOnClickListener(
//                        TextClassification.createPendingIntent(
//                                mTextView.getContext(), intent,
//                                createAssistMenuItemPendingIntentRequestCode()));
                }
            }
            if (onClickListener != null) {
                onClickListener.onClick(mTextView);
                stopTextActionMode();
            }
            // We tried our best.
            return true;
        }

        private int createAssistMenuItemPendingIntentRequestCode() {
            return mTextView.hasSelection()
                    ? mTextView.getText().subSequence(
                    mTextView.getSelectionStart(), mTextView.getSelectionEnd())
                    .hashCode()
                    : 0;
        }

        private boolean shouldEnableAssistMenuItems() {
//        return mTextView.isDeviceProvisioned()
//                && TextClassificationManager.getSettings(mTextView.getContext())
//                .isSmartTextShareEnabled();
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            getSelectionActionModeHelper().onSelectionAction(item.getItemId());

            if (mProcessTextIntentActionsHandler.performMenuItemAction(item)) {
                return true;
            }
            Callback customCallback = getCustomCallback();
            if (customCallback != null && customCallback.onActionItemClicked(mode, item)) {
                return true;
            }
            if (item.getGroupId() == EditText.ID_ASSIST && onAssistMenuItemClicked(item)) {
                return true;
            }
            return mTextView.onTextContextMenuItem(item.getItemId());
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            // Clear mTextActionMode not to recursively destroy action mode by clearing selection.
            getSelectionActionModeHelper().onDestroyActionMode();
            Log.w(TAG, "onDestroyActionMode: mode=" + mode);
            mTextActionMode = null;
            Callback customCallback = getCustomCallback();
            if (customCallback != null) {
                customCallback.onDestroyActionMode(mode);
            }

            if (!mPreserveSelection) {
                /*
                 * Leave current selection when we tentatively destroy action mode for the
                 * selection. If we're detaching from a window, we'll bring back the selection
                 * mode when (if) we get reattached.
                 */
                Selection.setSelection((Spannable) mTextView.getText(),
                        mTextView.getSelectionEnd());
            }

            if (mSelectionModifierCursorController != null) {
                mSelectionModifierCursorController.hide();
            }

            mAssistClickHandlers.clear();
//        mRequestingLinkActionMode = false;
        }

        @Override
        public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
            if (!view.equals(mTextView) || mTextView.getLayout() == null) {
                super.onGetContentRect(mode, view, outRect);
                return;
            }
            if (mTextView.getSelectionStart() != mTextView.getSelectionEnd()) {
                // We have a selection.
                mSelectionPath.reset();
                mTextView.getLayout().getSelectionPath(
                        mTextView.getSelectionStart(), mTextView.getSelectionEnd(), mSelectionPath);
                mSelectionPath.computeBounds(mSelectionBounds, true);
                mSelectionBounds.bottom += mHandleHeight;
            } else {
                // We have a cursor.
                Layout layout = mTextView.getLayout();
                int line = layout.getLineForOffset(mTextView.getSelectionStart());
                float primaryHorizontal = clampHorizontalPosition(null,
                        layout.getPrimaryHorizontal(mTextView.getSelectionStart()));
                mSelectionBounds.set(
                        primaryHorizontal,
                        layout.getLineTop(line),
                        primaryHorizontal,
                        layout.getLineBottom(line) + mHandleHeight);
            }
            // Take TextView's padding and scroll into account.
            int textHorizontalOffset = mTextView.viewportToContentHorizontalOffset();
            int textVerticalOffset = mTextView.viewportToContentVerticalOffset();
            outRect.set(
                    (int) Math.floor(mSelectionBounds.left + textHorizontalOffset),
                    (int) Math.floor(mSelectionBounds.top + textVerticalOffset),
                    (int) Math.ceil(mSelectionBounds.right + textHorizontalOffset),
                    (int) Math.ceil(mSelectionBounds.bottom + textVerticalOffset));
        }
    }

    public abstract class HandleView extends View implements TextViewPositionListener {
        private /*static */final String TAG = HandleView.class.getSimpleName();

        public static final int UNSET_X_VALUE = -1;
        public static final int UNSET_LINE = -1;

        protected Drawable mDrawable;
        protected Drawable mDrawableLtr;
        protected Drawable mDrawableRtl;
        private final PopupWindow mContainer;
        // Position with respect to the parent TextView
        private int mPositionX, mPositionY;
        private boolean mIsDragging;
        // Offset from touch position to mPosition
        private float mTouchToWindowOffsetX, mTouchToWindowOffsetY;
        protected int mHotspotX;
        protected int mHorizontalGravity;
        // Offsets the hotspot point up, so that cursor is not hidden by the finger when moving up
        private float mTouchOffsetY;
        // Where the touch position should be on the handle to ensure a maximum cursor visibility
        private float mIdealVerticalOffset;
        // Parent's (TextView) previous position in window
        private int mLastParentX, mLastParentY;
        // Parent's (TextView) previous position on screen
        private int mLastParentXOnScreen, mLastParentYOnScreen;
        // Previous text character offset
        protected int mPreviousOffset = -1;
        // Previous text character offset
        private boolean mPositionHasChanged = true;
        // Minimum touch target size for handles
        private int mMinSize;
        // Indicates the line of text that the handle is on.
        protected int mPrevLine = UNSET_LINE;
        // Indicates the line of text that the user was touching. This can differ from mPrevLine
        // when selecting text when the handles jump to the end / start of words which may be on
        // a different line.
        protected int mPreviousLineTouched = UNSET_LINE;

        HandleView(Drawable drawableLtr, Drawable drawableRtl, final int id) {
            super(mTextView.getContext());
            setId(id);
            mContainer = new PopupWindow(mTextView.getContext(), null,
                    /*com.android.internal.*/R.attr.textSelectHandleWindowStyle);
            mContainer.setSplitTouchEnabled(true);
            mContainer.setClippingEnabled(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mContainer.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL);
            } else {
                //TODO: (EW) handle?
            }
            mContainer.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
            mContainer.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
            mContainer.setContentView(this);

            mDrawableLtr = drawableLtr;
            mDrawableRtl = drawableRtl;
            mMinSize = mTextView.getContext().getResources().getDimensionPixelSize(
                    /*com.android.internal.*/R.dimen.text_handle_min_size);

            updateDrawable();

            final int handleHeight = getPreferredHeight();
            mTouchOffsetY = -0.3f * handleHeight;
            mIdealVerticalOffset = 0.7f * handleHeight;
        }

        public float getIdealVerticalOffset() {
            return mIdealVerticalOffset;
        }

        protected void updateDrawable() {
            if (mIsDragging) {
                // Don't update drawable during dragging.
                return;
            }
            final Layout layout = mTextView.getLayout();
            if (layout == null) {
                return;
            }
            final int offset = getCurrentCursorOffset();
            final boolean isRtlCharAtOffset = isAtRtlRun(layout, offset);
            final Drawable oldDrawable = mDrawable;
            mDrawable = isRtlCharAtOffset ? mDrawableRtl : mDrawableLtr;
            mHotspotX = getHotspotX(mDrawable, isRtlCharAtOffset);
            mHorizontalGravity = getHorizontalGravity(isRtlCharAtOffset);
            if (oldDrawable != mDrawable && isShowing()) {
                // Update popup window position.
                mPositionX = getCursorHorizontalPosition(layout, offset) - mHotspotX
                        - getHorizontalOffset() + getCursorOffset();
                mPositionX += mTextView.viewportToContentHorizontalOffset();
                mPositionHasChanged = true;
                updatePosition(mLastParentX, mLastParentY, false, false);
                postInvalidate();
            }
        }

        protected abstract int getHotspotX(Drawable drawable, boolean isRtlRun);
        protected abstract int getHorizontalGravity(boolean isRtlRun);

        // Touch-up filter: number of previous positions remembered
        private static final int HISTORY_SIZE = 5;
        private static final int TOUCH_UP_FILTER_DELAY_AFTER = 150;
        private static final int TOUCH_UP_FILTER_DELAY_BEFORE = 350;
        private final long[] mPreviousOffsetsTimes = new long[HISTORY_SIZE];
        private final int[] mPreviousOffsets = new int[HISTORY_SIZE];
        private int mPreviousOffsetIndex = 0;
        private int mNumberPreviousOffsets = 0;

        private void startTouchUpFilter(int offset) {
            mNumberPreviousOffsets = 0;
            addPositionToTouchUpFilter(offset);
        }

        private void addPositionToTouchUpFilter(int offset) {
            mPreviousOffsetIndex = (mPreviousOffsetIndex + 1) % HISTORY_SIZE;
            mPreviousOffsets[mPreviousOffsetIndex] = offset;
            mPreviousOffsetsTimes[mPreviousOffsetIndex] = SystemClock.uptimeMillis();
            mNumberPreviousOffsets++;
        }

        private void filterOnTouchUp(boolean fromTouchScreen) {
            final long now = SystemClock.uptimeMillis();
            int i = 0;
            int index = mPreviousOffsetIndex;
            final int iMax = Math.min(mNumberPreviousOffsets, HISTORY_SIZE);
            while (i < iMax && (now - mPreviousOffsetsTimes[index]) < TOUCH_UP_FILTER_DELAY_AFTER) {
                i++;
                index = (mPreviousOffsetIndex - i + HISTORY_SIZE) % HISTORY_SIZE;
            }

            if (i > 0 && i < iMax
                    && (now - mPreviousOffsetsTimes[index]) > TOUCH_UP_FILTER_DELAY_BEFORE) {
                positionAtCursorOffset(mPreviousOffsets[index], false, fromTouchScreen);
            }
        }

        public boolean offsetHasBeenChanged() {
            return mNumberPreviousOffsets > 1;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(getPreferredWidth(), getPreferredHeight());
        }

        @Override
        public void invalidate() {
            super.invalidate();
            if (isShowing()) {
                positionAtCursorOffset(getCurrentCursorOffset(), true, false);
            }
        };

        private int getPreferredWidth() {
            return Math.max(mDrawable.getIntrinsicWidth(), mMinSize);
        }

        private int getPreferredHeight() {
            return Math.max(mDrawable.getIntrinsicHeight(), mMinSize);
        }

        public void show() {
            if (isShowing()) return;

            getPositionListener().addSubscriber(this, true /* local position may change */);

            // Make sure the offset is always considered new, even when focusing at same position
            mPreviousOffset = -1;
            positionAtCursorOffset(getCurrentCursorOffset(), false, false);
        }

        protected void dismiss() {
            mIsDragging = false;
            mContainer.dismiss();
            onDetached();
        }

        public void hide() {
            dismiss();

            getPositionListener().removeSubscriber(this);
        }

        public boolean isShowing() {
            return mContainer.isShowing();
        }

        private boolean shouldShow() {
            // A dragging handle should always be shown.
            if (mIsDragging) {
                return true;
            }

            if (mTextView.isInBatchEditMode()) {
                return false;
            }

            return mTextView.isPositionVisible(
                    mPositionX + mHotspotX + getHorizontalOffset(), mPositionY);
        }

        private void setVisible(final boolean visible) {
            mContainer.getContentView().setVisibility(visible ? VISIBLE : INVISIBLE);
        }

        public abstract int getCurrentCursorOffset();

        protected abstract void updateSelection(int offset);

        protected abstract void updatePosition(float x, float y, boolean fromTouchScreen);

        //    @MagnifierHandleTrigger
        protected abstract int getMagnifierHandleTrigger();

        protected boolean isAtRtlRun(@NonNull Layout layout, int offset) {
            return layout.isRtlCharAt(offset);
        }

        public float getHorizontal(@NonNull Layout layout, int offset) {
            return layout.getPrimaryHorizontal(offset);
        }

        protected int getOffsetAtCoordinate(@NonNull Layout layout, int line, float x) {
            return mTextView.getOffsetAtCoordinate(line, x);
        }

        /**
         * @param offset Cursor offset. Must be in [-1, length].
         * @param forceUpdatePosition whether to force update the position.  This should be true
         * when If the parent has been scrolled, for example.
         * @param fromTouchScreen {@code true} if the cursor is moved with motion events from the
         * touch screen.
         */
        protected void positionAtCursorOffset(int offset, boolean forceUpdatePosition,
                                              boolean fromTouchScreen) {
            // A HandleView relies on the layout, which may be nulled by external methods
            Layout layout = mTextView.getLayout();
            if (layout == null) {
                // Will update controllers' state, hiding them and stopping selection mode if needed
                prepareCursorControllers();
                return;
            }
            layout = mTextView.getLayout();

            boolean offsetChanged = offset != mPreviousOffset;
            if (offsetChanged || forceUpdatePosition) {
                if (offsetChanged) {
                    updateSelection(offset);
                    if (fromTouchScreen && mHapticTextHandleEnabled) {
                        mTextView.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
                    }
                    addPositionToTouchUpFilter(offset);
                }
                final int line = layout.getLineForOffset(offset);
                mPrevLine = line;

                mPositionX = getCursorHorizontalPosition(layout, offset) - mHotspotX
                        - getHorizontalOffset() + getCursorOffset();
                mPositionY = layout.getLineBottom/*WithoutSpacing*/(line);//TODO: (EW) verify this is fine

                // Take TextView's padding and scroll into account.
                mPositionX += mTextView.viewportToContentHorizontalOffset();
                mPositionY += mTextView.viewportToContentVerticalOffset();

                mPreviousOffset = offset;
                mPositionHasChanged = true;
            }
        }

        /**
         * Return the clamped horizontal position for the cursor.
         *
         * @param layout Text layout.
         * @param offset Character offset for the cursor.
         * @return The clamped horizontal position for the cursor.
         */
        int getCursorHorizontalPosition(Layout layout, int offset) {
            return (int) (getHorizontal(layout, offset) - 0.5f);
        }

        @Override
        public void updatePosition(int parentPositionX, int parentPositionY,
                                   boolean parentPositionChanged, boolean parentScrolled) {
            positionAtCursorOffset(getCurrentCursorOffset(), parentScrolled, false);
            if (parentPositionChanged || mPositionHasChanged) {
                if (mIsDragging) {
                    // Update touchToWindow offset in case of parent scrolling while dragging
                    if (parentPositionX != mLastParentX || parentPositionY != mLastParentY) {
                        mTouchToWindowOffsetX += parentPositionX - mLastParentX;
                        mTouchToWindowOffsetY += parentPositionY - mLastParentY;
                        mLastParentX = parentPositionX;
                        mLastParentY = parentPositionY;
                    }

                    onHandleMoved();
                }

                if (shouldShow()) {
                    // Transform to the window coordinates to follow the view tranformation.
                    final int[] pts = { mPositionX + mHotspotX + getHorizontalOffset(), mPositionY};
                    mTextView.transformFromViewToWindowSpace(pts);
                    pts[0] -= mHotspotX + getHorizontalOffset();

                    if (isShowing()) {
                        mContainer.update(pts[0], pts[1], -1, -1);
                    } else {
                        mContainer.showAtLocation(mTextView, Gravity.NO_GRAVITY, pts[0], pts[1]);
                    }
                } else {
                    if (isShowing()) {
                        dismiss();
                    }
                }

                mPositionHasChanged = false;
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            final int drawWidth = mDrawable.getIntrinsicWidth();
            final int left = getHorizontalOffset();

            mDrawable.setBounds(left, 0, left + drawWidth, mDrawable.getIntrinsicHeight());
            mDrawable.draw(c);
        }

        private int getHorizontalOffset() {
            final int width = getPreferredWidth();
            final int drawWidth = mDrawable.getIntrinsicWidth();
            final int left;
            switch (mHorizontalGravity) {
                case Gravity.LEFT:
                    left = 0;
                    break;
                default:
                case Gravity.CENTER:
                    left = (width - drawWidth) / 2;
                    break;
                case Gravity.RIGHT:
                    left = width - drawWidth;
                    break;
            }
            return left;
        }

        protected int getCursorOffset() {
            return 0;
        }

        private boolean tooLargeTextForMagnifier() {
//        final float magnifierContentHeight = Math.round(
//                mMagnifierAnimator.mMagnifier.getHeight()
//                        / mMagnifierAnimator.mMagnifier.getZoom());
//        final Paint.FontMetrics fontMetrics = mTextView.getPaint().getFontMetrics();
//        final float glyphHeight = fontMetrics.descent - fontMetrics.ascent;
//        return glyphHeight > magnifierContentHeight;
            return true;
        }

        /**
         * Computes the position where the magnifier should be shown, relative to
         * {@code mTextView}, and writes them to {@code showPosInView}. Also decides
         * whether the magnifier should be shown or dismissed after this touch event.
         * @return Whether the magnifier should be shown at the computed coordinates or dismissed.
         */
        private boolean obtainMagnifierShowCoordinates(@NonNull final MotionEvent event,
                                                       final PointF showPosInView) {

//        final int trigger = getMagnifierHandleTrigger();
//        final int offset;
//        final int otherHandleOffset;
//        switch (trigger) {
//            case MagnifierHandleTrigger.INSERTION:
//                offset = mTextView.getSelectionStart();
//                otherHandleOffset = -1;
//                break;
//            case MagnifierHandleTrigger.SELECTION_START:
//                offset = mTextView.getSelectionStart();
//                otherHandleOffset = mTextView.getSelectionEnd();
//                break;
//            case MagnifierHandleTrigger.SELECTION_END:
//                offset = mTextView.getSelectionEnd();
//                otherHandleOffset = mTextView.getSelectionStart();
//                break;
//            default:
//                offset = -1;
//                otherHandleOffset = -1;
//                break;
//        }
//
//        if (offset == -1) {
//            return false;
//        }
//
//        final Layout layout = mTextView.getLayout();
//        final int lineNumber = layout.getLineForOffset(offset);
//        // Compute whether the selection handles are currently on the same line, and,
//        // in this particular case, whether the selected text is right to left.
//        final boolean sameLineSelection = otherHandleOffset != -1
//                && lineNumber == layout.getLineForOffset(otherHandleOffset);
//        final boolean rtl = sameLineSelection
//                && (offset < otherHandleOffset)
//                != (getHorizontal(mTextView.getLayout(), offset)
//                < getHorizontal(mTextView.getLayout(), otherHandleOffset));
//
//        // Horizontally move the magnifier smoothly, clamp inside the current line / selection.
//        final int[] textViewLocationOnScreen = new int[2];
//        mTextView.getLocationOnScreen(textViewLocationOnScreen);
//        final float touchXInView = event.getRawX() - textViewLocationOnScreen[0];
//        float leftBound = mTextView.getTotalPaddingLeft() - mTextView.getScrollX();
//        float rightBound = mTextView.getTotalPaddingLeft() - mTextView.getScrollX();
//        if (sameLineSelection && ((trigger == MagnifierHandleTrigger.SELECTION_END) ^ rtl)) {
//            leftBound += getHorizontal(mTextView.getLayout(), otherHandleOffset);
//        } else {
//            leftBound += mTextView.getLayout().getLineLeft(lineNumber);
//        }
//        if (sameLineSelection && ((trigger == MagnifierHandleTrigger.SELECTION_START) ^ rtl)) {
//            rightBound += getHorizontal(mTextView.getLayout(), otherHandleOffset);
//        } else {
//            rightBound += mTextView.getLayout().getLineRight(lineNumber);
//        }
//        final float contentWidth = Math.round(mMagnifierAnimator.mMagnifier.getWidth()
//                / mMagnifierAnimator.mMagnifier.getZoom());
//        if (touchXInView < leftBound - contentWidth / 2
//                || touchXInView > rightBound + contentWidth / 2) {
//            // The touch is too far from the current line / selection, so hide the magnifier.
//            return false;
//        }
//        showPosInView.x = Math.max(leftBound, Math.min(rightBound, touchXInView));
//
//        // Vertically snap to middle of current line.
//        showPosInView.y = (mTextView.getLayout().getLineTop(lineNumber)
//                + mTextView.getLayout().getLineBottom(lineNumber)) / 2.0f
//                + mTextView.getTotalPaddingTop() - mTextView.getScrollY();
//
//        return true;
            return false;
        }

        private boolean handleOverlapsMagnifier(@NonNull final HandleView handle,
                                                @NonNull final Rect magnifierRect) {
//        final PopupWindow window = handle.mContainer;
//        if (!window.hasDecorView()) {
//            return false;
//        }
//        final Rect handleRect = new Rect(
//                window.getDecorViewLayoutParams().x,
//                window.getDecorViewLayoutParams().y,
//                window.getDecorViewLayoutParams().x + window.getContentView().getWidth(),
//                window.getDecorViewLayoutParams().y + window.getContentView().getHeight());
//        return Rect.intersects(handleRect, magnifierRect);
            return false;
        }

        private @Nullable HandleView getOtherSelectionHandle() {
            //TODO: (EW) is this necessary?
//        final SelectionModifierCursorController controller = getSelectionController();
//        if (controller == null || !controller.isActive()) {
//            return null;
//        }
//        return controller.mStartHandle != this
//                ? controller.mStartHandle
//                : controller.mEndHandle;
            return null;
        }

//    private final Magnifier.Callback mHandlesVisibilityCallback = new Magnifier.Callback() {
//        @Override
//        public void onOperationComplete() {
//            final Point magnifierTopLeft = mMagnifierAnimator.mMagnifier.getWindowCoords();
//            if (magnifierTopLeft == null) {
//                return;
//            }
//            final Rect magnifierRect = new Rect(magnifierTopLeft.x, magnifierTopLeft.y,
//                    magnifierTopLeft.x + mMagnifierAnimator.mMagnifier.getWidth(),
//                    magnifierTopLeft.y + mMagnifierAnimator.mMagnifier.getHeight());
//            setVisible(!handleOverlapsMagnifier(HandleView.this, magnifierRect));
//            final HandleView otherHandle = getOtherSelectionHandle();
//            if (otherHandle != null) {
//                otherHandle.setVisible(!handleOverlapsMagnifier(otherHandle, magnifierRect));
//            }
//        }
//    };

        protected final void updateMagnifier(@NonNull final MotionEvent event) {
//        if (mMagnifierAnimator == null) {
//            return;
//        }
//
//        final PointF showPosInView = new PointF();
//        final boolean shouldShow = !tooLargeTextForMagnifier()
//                && obtainMagnifierShowCoordinates(event, showPosInView);
//        if (shouldShow) {
//            // Make the cursor visible and stop blinking.
//            mRenderCursorRegardlessTiming = true;
//            mTextView.invalidateCursorPath();
//            suspendBlink();
//            mMagnifierAnimator.mMagnifier
//                    .setOnOperationCompleteCallback(mHandlesVisibilityCallback);
//
//            mMagnifierAnimator.show(showPosInView.x, showPosInView.y);
//        } else {
//            dismissMagnifier();
//        }
        }

        protected final void dismissMagnifier() {
//        if (mMagnifierAnimator != null) {
//            mMagnifierAnimator.dismiss();
//            mRenderCursorRegardlessTiming = false;
//            resumeBlink();
//            setVisible(true);
//            final HandleView otherHandle = getOtherSelectionHandle();
//            if (otherHandle != null) {
//                otherHandle.setVisible(true);
//            }
//        }
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
//        updateFloatingToolbarVisibility(ev);

            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    startTouchUpFilter(getCurrentCursorOffset());

                    final PositionListener positionListener = getPositionListener();
                    mLastParentX = positionListener.getPositionX();
                    mLastParentY = positionListener.getPositionY();
                    mLastParentXOnScreen = positionListener.getPositionXOnScreen();
                    mLastParentYOnScreen = positionListener.getPositionYOnScreen();

                    final float xInWindow = ev.getRawX() - mLastParentXOnScreen + mLastParentX;
                    final float yInWindow = ev.getRawY() - mLastParentYOnScreen + mLastParentY;
                    mTouchToWindowOffsetX = xInWindow - mPositionX;
                    mTouchToWindowOffsetY = yInWindow - mPositionY;

                    mIsDragging = true;
                    mPreviousLineTouched = UNSET_LINE;
                    break;
                }

                case MotionEvent.ACTION_MOVE: {
                    final float xInWindow = ev.getRawX() - mLastParentXOnScreen + mLastParentX;
                    final float yInWindow = ev.getRawY() - mLastParentYOnScreen + mLastParentY;

                    // Vertical hysteresis: vertical down movement tends to snap to ideal offset
                    final float previousVerticalOffset = mTouchToWindowOffsetY - mLastParentY;
                    final float currentVerticalOffset = yInWindow - mPositionY - mLastParentY;
                    float newVerticalOffset;
                    if (previousVerticalOffset < mIdealVerticalOffset) {
                        newVerticalOffset = Math.min(currentVerticalOffset, mIdealVerticalOffset);
                        newVerticalOffset = Math.max(newVerticalOffset, previousVerticalOffset);
                    } else {
                        newVerticalOffset = Math.max(currentVerticalOffset, mIdealVerticalOffset);
                        newVerticalOffset = Math.min(newVerticalOffset, previousVerticalOffset);
                    }
                    mTouchToWindowOffsetY = newVerticalOffset + mLastParentY;

                    final float newPosX =
                            xInWindow - mTouchToWindowOffsetX + mHotspotX + getHorizontalOffset();
                    final float newPosY = yInWindow - mTouchToWindowOffsetY + mTouchOffsetY;

                    updatePosition(newPosX, newPosY,
                            ev.isFromSource(InputDevice.SOURCE_TOUCHSCREEN));
                    break;
                }

                case MotionEvent.ACTION_UP:
                    filterOnTouchUp(ev.isFromSource(InputDevice.SOURCE_TOUCHSCREEN));
                    // Fall through.
                case MotionEvent.ACTION_CANCEL:
                    mIsDragging = false;
                    updateDrawable();
                    break;
            }
            return true;
        }

        public boolean isDragging() {
            return mIsDragging;
        }

        void onHandleMoved() {}

        public void onDetached() {}
    }

    public class InsertionHandleView extends HandleView {
        private static final int DELAY_BEFORE_HANDLE_FADES_OUT = 4000;
        private static final int RECENT_CUT_COPY_DURATION = 15 * 1000; // seconds

        // Used to detect taps on the insertion handle, which will affect the insertion action mode
        private float mDownPositionX, mDownPositionY;
        private Runnable mHider;

        public InsertionHandleView(Drawable drawable, EditText textView) {
            super(drawable, drawable, /*com.android.internal.*/R.id.insertion_handle);
        }

        @Override
        public void show() {
            super.show();

            final long durationSinceCutOrCopy =
                    SystemClock.uptimeMillis() - EditText.sLastCutCopyOrTextChangedTime;

            // Cancel the single tap delayed runnable.
            if (mInsertionActionModeRunnable != null
                    && ((mTapState == TAP_STATE_DOUBLE_TAP)
                    || (mTapState == TAP_STATE_TRIPLE_CLICK)
                    /*|| isCursorInsideEasyCorrectionSpan()*/)) {
                mTextView.removeCallbacks(mInsertionActionModeRunnable);
            }

            // Prepare and schedule the single tap runnable to run exactly after the double tap
            // timeout has passed.
            if ((mTapState != TAP_STATE_DOUBLE_TAP) && (mTapState != TAP_STATE_TRIPLE_CLICK)
//                && !isCursorInsideEasyCorrectionSpan()
                    && (durationSinceCutOrCopy < RECENT_CUT_COPY_DURATION)) {
                if (mTextActionMode == null) {
                    if (mInsertionActionModeRunnable == null) {
                        mInsertionActionModeRunnable = new Runnable() {
                            @Override
                            public void run() {
                                startInsertionActionMode();
                            }
                        };
                    }
                    mTextView.postDelayed(
                            mInsertionActionModeRunnable,
                            ViewConfiguration.getDoubleTapTimeout() + 1);
                }

            }

            hideAfterDelay();
        }

        private void hideAfterDelay() {
            if (mHider == null) {
                mHider = new Runnable() {
                    public void run() {
                        hide();
                    }
                };
            } else {
                removeHiderCallback();
            }
            mTextView.postDelayed(mHider, DELAY_BEFORE_HANDLE_FADES_OUT);
        }

        private void removeHiderCallback() {
            if (mHider != null) {
                mTextView.removeCallbacks(mHider);
            }
        }

        @Override
        protected int getHotspotX(Drawable drawable, boolean isRtlRun) {
            return drawable.getIntrinsicWidth() / 2;
        }

        @Override
        protected int getHorizontalGravity(boolean isRtlRun) {
            return Gravity.CENTER_HORIZONTAL;
        }

        @Override
        protected int getCursorOffset() {
            int offset = super.getCursorOffset();
            if (mDrawableForCursor != null) {
                mDrawableForCursor.getPadding(mTempRect);
                offset += (mDrawableForCursor.getIntrinsicWidth()
                        - mTempRect.left - mTempRect.right) / 2;
            }
            return offset;
        }

        @Override
        int getCursorHorizontalPosition(Layout layout, int offset) {
            if (mDrawableForCursor != null) {
                final float horizontal = getHorizontal(layout, offset);
                return clampHorizontalPosition(mDrawableForCursor, horizontal) + mTempRect.left;
            }
            return super.getCursorHorizontalPosition(layout, offset);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            final boolean result = super.onTouchEvent(ev);

            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mDownPositionX = ev.getRawX();
                    mDownPositionY = ev.getRawY();
                    updateMagnifier(ev);
                    break;

                case MotionEvent.ACTION_MOVE:
                    updateMagnifier(ev);
                    break;

                case MotionEvent.ACTION_UP:
                    if (!offsetHasBeenChanged()) {
                        final float deltaX = mDownPositionX - ev.getRawX();
                        final float deltaY = mDownPositionY - ev.getRawY();
                        final float distanceSquared = deltaX * deltaX + deltaY * deltaY;

                        final ViewConfiguration viewConfiguration = ViewConfiguration.get(
                                mTextView.getContext());
                        final int touchSlop = viewConfiguration.getScaledTouchSlop();

                        if (distanceSquared < touchSlop * touchSlop) {
                            // Tapping on the handle toggles the insertion action mode.
                            if (mTextActionMode != null) {
                                stopTextActionMode();
                            } else {
                                startInsertionActionMode();
                            }
                        }
                    } else {
                        if (mTextActionMode != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                mTextActionMode.invalidateContentRect();
                            } else {
                                //TODO: (EW) handle?
                            }
                        }
                    }
                    // Fall through.
                case MotionEvent.ACTION_CANCEL:
                    hideAfterDelay();
                    dismissMagnifier();
                    break;

                default:
                    break;
            }

            return result;
        }

        @Override
        public int getCurrentCursorOffset() {
            return mTextView.getSelectionStart();
        }

        @Override
        public void updateSelection(int offset) {
            Selection.setSelection((Spannable) mTextView.getText(), offset);
        }

        @Override
        protected void updatePosition(float x, float y, boolean fromTouchScreen) {
            Layout layout = mTextView.getLayout();
            int offset;
            if (layout != null) {
                if (mPreviousLineTouched == UNSET_LINE) {
                    mPreviousLineTouched = mTextView.getLineAtCoordinate(y);
                }
                int currLine = getCurrentLineAdjustedForSlop(layout, mPreviousLineTouched, y);
                offset = getOffsetAtCoordinate(layout, currLine, x);
                mPreviousLineTouched = currLine;
            } else {
                offset = -1;
            }
            positionAtCursorOffset(offset, false, fromTouchScreen);
            if (mTextActionMode != null) {
                invalidateActionMode();
            }
        }

        @Override
        void onHandleMoved() {
            super.onHandleMoved();
            removeHiderCallback();
        }

        @Override
        public void onDetached() {
            super.onDetached();
            removeHiderCallback();
        }

        @Override
//    @MagnifierHandleTrigger
        protected int getMagnifierHandleTrigger() {
            return /*MagnifierHandleTrigger.INSERTION*/0;
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(/*prefix = { "HANDLE_TYPE_" }, */value = {
            HANDLE_TYPE_SELECTION_START,
            HANDLE_TYPE_SELECTION_END
    })
    public @interface HandleType {}
    public static final int HANDLE_TYPE_SELECTION_START = 0;
    public static final int HANDLE_TYPE_SELECTION_END = 1;

    /** For selection handles */
    public final class SelectionHandleView extends HandleView {
        // Indicates the handle type, selection start (HANDLE_TYPE_SELECTION_START) or selection
        // end (HANDLE_TYPE_SELECTION_END).
        @HandleType
        private final int mHandleType;
        // Indicates whether the cursor is making adjustments within a word.
        private boolean mInWord = false;
        // Difference between touch position and word boundary position.
        private float mTouchWordDelta;
        // X value of the previous updatePosition call.
        private float mPrevX;
        // Indicates if the handle has moved a boundary between LTR and RTL text.
        private boolean mLanguageDirectionChanged = false;
        // Distance from edge of horizontally scrolling text view
        // to use to switch to character mode.
        private final float mTextViewEdgeSlop;
        // Used to save text view location.
        private final int[] mTextViewLocation = new int[2];

        public SelectionHandleView(Drawable drawableLtr, Drawable drawableRtl, int id,
                                     @HandleType int handleType) {
            super(drawableLtr, drawableRtl, id);
            mHandleType = handleType;
            ViewConfiguration viewConfiguration = ViewConfiguration.get(mTextView.getContext());
            mTextViewEdgeSlop = viewConfiguration.getScaledTouchSlop() * 4;
        }

        private boolean isStartHandle() {
            return mHandleType == HANDLE_TYPE_SELECTION_START;
        }

        @Override
        protected int getHotspotX(Drawable drawable, boolean isRtlRun) {
            if (isRtlRun == isStartHandle()) {
                return drawable.getIntrinsicWidth() / 4;
            } else {
                return (drawable.getIntrinsicWidth() * 3) / 4;
            }
        }

        @Override
        protected int getHorizontalGravity(boolean isRtlRun) {
            return (isRtlRun == isStartHandle()) ? Gravity.LEFT : Gravity.RIGHT;
        }

        @Override
        public int getCurrentCursorOffset() {
            return isStartHandle() ? mTextView.getSelectionStart() : mTextView.getSelectionEnd();
        }

        @Override
        protected void updateSelection(int offset) {
            if (isStartHandle()) {
                Selection.setSelection((Spannable) mTextView.getText(), offset,
                        mTextView.getSelectionEnd());
            } else {
                Selection.setSelection((Spannable) mTextView.getText(),
                        mTextView.getSelectionStart(), offset);
            }
            updateDrawable();
            if (mTextActionMode != null) {
                invalidateActionMode();
            }
        }

        @Override
        protected void updatePosition(float x, float y, boolean fromTouchScreen) {
            final Layout layout = mTextView.getLayout();
            if (layout == null) {
                // HandleView will deal appropriately in positionAtCursorOffset when
                // layout is null.
                positionAndAdjustForCrossingHandles(mTextView.getOffsetForPosition(x, y),
                        fromTouchScreen);
                return;
            }

            if (mPreviousLineTouched == UNSET_LINE) {
                mPreviousLineTouched = mTextView.getLineAtCoordinate(y);
            }

            boolean positionCursor = false;
            final int anotherHandleOffset =
                    isStartHandle() ? mTextView.getSelectionEnd() : mTextView.getSelectionStart();
            int currLine = getCurrentLineAdjustedForSlop(layout, mPreviousLineTouched, y);
            int initialOffset = getOffsetAtCoordinate(layout, currLine, x);

            if (isStartHandle() && initialOffset >= anotherHandleOffset
                    || !isStartHandle() && initialOffset <= anotherHandleOffset) {
                // Handles have crossed, bound it to the first selected line and
                // adjust by word / char as normal.
                currLine = layout.getLineForOffset(anotherHandleOffset);
                initialOffset = getOffsetAtCoordinate(layout, currLine, x);
            }

            int offset = initialOffset;
            final int wordEnd = getWordEnd(offset);
            final int wordStart = getWordStart(offset);

            if (mPrevX == UNSET_X_VALUE) {
                mPrevX = x;
            }

            final int currentOffset = getCurrentCursorOffset();
            final boolean rtlAtCurrentOffset = isAtRtlRun(layout, currentOffset);
            final boolean atRtl = isAtRtlRun(layout, offset);
//        final boolean isLvlBoundary = layout.isLevelBoundary(offset);

            // We can't determine if the user is expanding or shrinking the selection if they're
            // on a bi-di boundary, so until they've moved past the boundary we'll just place
            // the cursor at the current position.
            if (/*isLvlBoundary || */(rtlAtCurrentOffset && !atRtl) || (!rtlAtCurrentOffset && atRtl)) {
                // We're on a boundary or this is the first direction change -- just update
                // to the current position.
                mLanguageDirectionChanged = true;
                mTouchWordDelta = 0.0f;
                positionAndAdjustForCrossingHandles(offset, fromTouchScreen);
                return;
            } else if (mLanguageDirectionChanged/* && !isLvlBoundary*/) {
                // We've just moved past the boundary so update the position. After this we can
                // figure out if the user is expanding or shrinking to go by word or character.
                positionAndAdjustForCrossingHandles(offset, fromTouchScreen);
                mTouchWordDelta = 0.0f;
                mLanguageDirectionChanged = false;
                return;
            }

            boolean isExpanding;
            final float xDiff = x - mPrevX;
            if (isStartHandle()) {
                isExpanding = currLine < mPreviousLineTouched;
            } else {
                isExpanding = currLine > mPreviousLineTouched;
            }
            if (atRtl == isStartHandle()) {
                isExpanding |= xDiff > 0;
            } else {
                isExpanding |= xDiff < 0;
            }

            if (mTextView.getHorizontallyScrolling()) {
                if (positionNearEdgeOfScrollingView(x, atRtl)
                        && ((isStartHandle() && mTextView.getScrollX() != 0)
                        || (!isStartHandle()
                        && mTextView.canScrollHorizontally(atRtl ? -1 : 1)))
                        && ((isExpanding && ((isStartHandle() && offset < currentOffset)
                        || (!isStartHandle() && offset > currentOffset)))
                        || !isExpanding)) {
                    // If we're expanding ensure that the offset is actually expanding compared to
                    // the current offset, if the handle snapped to the word, the finger position
                    // may be out of sync and we don't want the selection to jump back.
                    mTouchWordDelta = 0.0f;
                    final int nextOffset = (atRtl == isStartHandle())
                            ? layout.getOffsetToRightOf(mPreviousOffset)
                            : layout.getOffsetToLeftOf(mPreviousOffset);
                    positionAndAdjustForCrossingHandles(nextOffset, fromTouchScreen);
                    return;
                }
            }

            if (isExpanding) {
                // User is increasing the selection.
                int wordBoundary = isStartHandle() ? wordStart : wordEnd;
                final boolean snapToWord = (!mInWord
                        || (isStartHandle() ? currLine < mPrevLine : currLine > mPrevLine))
                        && atRtl == isAtRtlRun(layout, wordBoundary);
                if (snapToWord) {
                    // Sometimes words can be broken across lines (Chinese, hyphenation).
                    // We still snap to the word boundary but we only use the letters on the
                    // current line to determine if the user is far enough into the word to snap.
                    if (layout.getLineForOffset(wordBoundary) != currLine) {
                        wordBoundary = isStartHandle()
                                ? layout.getLineStart(currLine) : layout.getLineEnd(currLine);
                    }
                    final int offsetThresholdToSnap = isStartHandle()
                            ? wordEnd - ((wordEnd - wordBoundary) / 2)
                            : wordStart + ((wordBoundary - wordStart) / 2);
                    if (isStartHandle()
                            && (offset <= offsetThresholdToSnap || currLine < mPrevLine)) {
                        // User is far enough into the word or on a different line so we expand by
                        // word.
                        offset = wordStart;
                    } else if (!isStartHandle()
                            && (offset >= offsetThresholdToSnap || currLine > mPrevLine)) {
                        // User is far enough into the word or on a different line so we expand by
                        // word.
                        offset = wordEnd;
                    } else {
                        offset = mPreviousOffset;
                    }
                }
                if ((isStartHandle() && offset < initialOffset)
                        || (!isStartHandle() && offset > initialOffset)) {
                    final float adjustedX = getHorizontal(layout, offset);
                    mTouchWordDelta =
                            mTextView.convertToLocalHorizontalCoordinate(x) - adjustedX;
                } else {
                    mTouchWordDelta = 0.0f;
                }
                positionCursor = true;
            } else {
                final int adjustedOffset =
                        getOffsetAtCoordinate(layout, currLine, x - mTouchWordDelta);
                final boolean shrinking = isStartHandle()
                        ? adjustedOffset > mPreviousOffset || currLine > mPrevLine
                        : adjustedOffset < mPreviousOffset || currLine < mPrevLine;
                if (shrinking) {
                    // User is shrinking the selection.
                    if (currLine != mPrevLine) {
                        // We're on a different line, so we'll snap to word boundaries.
                        offset = isStartHandle() ? wordStart : wordEnd;
                        if ((isStartHandle() && offset < initialOffset)
                                || (!isStartHandle() && offset > initialOffset)) {
                            final float adjustedX = getHorizontal(layout, offset);
                            mTouchWordDelta =
                                    mTextView.convertToLocalHorizontalCoordinate(x) - adjustedX;
                        } else {
                            mTouchWordDelta = 0.0f;
                        }
                    } else {
                        offset = adjustedOffset;
                    }
                    positionCursor = true;
                } else if ((isStartHandle() && adjustedOffset < mPreviousOffset)
                        || (!isStartHandle() && adjustedOffset > mPreviousOffset)) {
                    // Handle has jumped to the word boundary, and the user is moving
                    // their finger towards the handle, the delta should be updated.
                    mTouchWordDelta = mTextView.convertToLocalHorizontalCoordinate(x)
                            - getHorizontal(layout, mPreviousOffset);
                }
            }

            if (positionCursor) {
                mPreviousLineTouched = currLine;
                positionAndAdjustForCrossingHandles(offset, fromTouchScreen);
            }
            mPrevX = x;
        }

        @Override
        protected void positionAtCursorOffset(int offset, boolean forceUpdatePosition,
                                              boolean fromTouchScreen) {
            super.positionAtCursorOffset(offset, forceUpdatePosition, fromTouchScreen);
            mInWord = (offset != -1) && !getWordIteratorWithText().isBoundary(offset);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            boolean superResult = super.onTouchEvent(event);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // Reset the touch word offset and x value when the user
                    // re-engages the handle.
                    mTouchWordDelta = 0.0f;
                    mPrevX = UNSET_X_VALUE;
                    updateMagnifier(event);
                    break;

                case MotionEvent.ACTION_MOVE:
                    updateMagnifier(event);
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dismissMagnifier();
                    break;
            }

            return superResult;
        }

        private void positionAndAdjustForCrossingHandles(int offset, boolean fromTouchScreen) {
            final int anotherHandleOffset =
                    isStartHandle() ? mTextView.getSelectionEnd() : mTextView.getSelectionStart();
            if ((isStartHandle() && offset >= anotherHandleOffset)
                    || (!isStartHandle() && offset <= anotherHandleOffset)) {
                mTouchWordDelta = 0.0f;
                final Layout layout = mTextView.getLayout();
                if (layout != null && offset != anotherHandleOffset) {
                    final float horiz = getHorizontal(layout, offset);
                    final float anotherHandleHoriz = getHorizontal(layout, anotherHandleOffset,
                            !isStartHandle());
                    final float currentHoriz = getHorizontal(layout, mPreviousOffset);
                    if (currentHoriz < anotherHandleHoriz && horiz < anotherHandleHoriz
                            || currentHoriz > anotherHandleHoriz && horiz > anotherHandleHoriz) {
                        // This handle passes another one as it crossed a direction boundary.
                        // Don't minimize the selection, but keep the handle at the run boundary.
                        final int currentOffset = getCurrentCursorOffset();
                        final int offsetToGetRunRange = isStartHandle()
                                ? currentOffset : Math.max(currentOffset - 1, 0);
                        //TODO: (EW) handle
//                    final long range = layout.getRunRange(offsetToGetRunRange);
//                    if (isStartHandle()) {
//                        offset = TextUtils.unpackRangeStartFromLong(range);
//                    } else {
//                        offset = TextUtils.unpackRangeEndFromLong(range);
//                    }
                        positionAtCursorOffset(offset, false, fromTouchScreen);
                        return;
                    }
                }
                // Handles can not cross and selection is at least one character.
                offset = getNextCursorOffset(anotherHandleOffset, !isStartHandle());
            }
            positionAtCursorOffset(offset, false, fromTouchScreen);
        }

        private boolean positionNearEdgeOfScrollingView(float x, boolean atRtl) {
            mTextView.getLocationOnScreen(mTextViewLocation);
            boolean nearEdge;
            if (atRtl == isStartHandle()) {
                int rightEdge = mTextViewLocation[0] + mTextView.getWidth()
                        - mTextView.getPaddingRight();
                nearEdge = x > rightEdge - mTextViewEdgeSlop;
            } else {
                int leftEdge = mTextViewLocation[0] + mTextView.getPaddingLeft();
                nearEdge = x < leftEdge + mTextViewEdgeSlop;
            }
            return nearEdge;
        }

        @Override
        protected boolean isAtRtlRun(@NonNull Layout layout, int offset) {
            final int offsetToCheck = isStartHandle() ? offset : Math.max(offset - 1, 0);
            return layout.isRtlCharAt(offsetToCheck);
        }

        @Override
        public float getHorizontal(@NonNull Layout layout, int offset) {
            return getHorizontal(layout, offset, isStartHandle());
        }

        private float getHorizontal(@NonNull Layout layout, int offset, boolean startHandle) {
            final int line = layout.getLineForOffset(offset);
            final int offsetToCheck = startHandle ? offset : Math.max(offset - 1, 0);
            final boolean isRtlChar = layout.isRtlCharAt(offsetToCheck);
            final boolean isRtlParagraph = layout.getParagraphDirection(line) == -1;
            return (isRtlChar == isRtlParagraph)
                    ? layout.getPrimaryHorizontal(offset) : layout.getSecondaryHorizontal(offset);
        }

        @Override
        protected int getOffsetAtCoordinate(@NonNull Layout layout, int line, float x) {
            final float localX = mTextView.convertToLocalHorizontalCoordinate(x);
            //TODO: (EW) handle better
            final int primaryOffset = layout.getOffsetForHorizontal(line, localX/*, true*/);
//        if (!layout.isLevelBoundary(primaryOffset)) {
//            return primaryOffset;
//        }
            final int secondaryOffset = layout.getOffsetForHorizontal(line, localX/*, false*/);
            final int currentOffset = getCurrentCursorOffset();
            final int primaryDiff = Math.abs(primaryOffset - currentOffset);
            final int secondaryDiff = Math.abs(secondaryOffset - currentOffset);
            if (primaryDiff < secondaryDiff) {
                return primaryOffset;
            } else if (primaryDiff > secondaryDiff) {
                return secondaryOffset;
            } else {
                final int offsetToCheck = isStartHandle()
                        ? currentOffset : Math.max(currentOffset - 1, 0);
                final boolean isRtlChar = layout.isRtlCharAt(offsetToCheck);
                final boolean isRtlParagraph = layout.getParagraphDirection(line) == -1;
                return isRtlChar == isRtlParagraph ? primaryOffset : secondaryOffset;
            }
        }

        //    @MagnifierHandleTrigger
        protected int getMagnifierHandleTrigger() {
            return isStartHandle()
                    ? /*MagnifierHandleTrigger.SELECTION_START*/1
                    : /*MagnifierHandleTrigger.SELECTION_END*/2;
        }



        // copied from Layout
//    public static final Layout.Directions DIRS_ALL_LEFT_TO_RIGHT =
//            new Layout.Directions(new int[] { 0, /*RUN_LENGTH_MASK*/0x03ffffff });
//    public static final Layout.Directions DIRS_ALL_RIGHT_TO_LEFT =
//            new Layout.Directions(new int[] { 0, /*RUN_LENGTH_MASK*/0x03ffffff | /*RUN_RTL_FLAG*/(1 << /*RUN_LEVEL_SHIFT*/26) });
//    private static boolean isLevelBoundary(Layout layout, int offset) {
//        int line = layout.getLineForOffset(offset);
//        Layout.Directions dirs = layout.getLineDirections(line);
//        if (dirs == DIRS_ALL_LEFT_TO_RIGHT || dirs == DIRS_ALL_RIGHT_TO_LEFT) {
//            return false;
//        }
//
//        int[] runs = dirs.mDirections;
//        int lineStart = layout.getLineStart(line);
//        int lineEnd = layout.getLineEnd(line);
//        if (offset == lineStart || offset == lineEnd) {
//            int paraLevel = layout.getParagraphDirection(line) == 1 ? 0 : 1;
//            int runIndex = offset == lineStart ? 0 : runs.length - 2;
//            return ((runs[runIndex + 1] >>> /*RUN_LEVEL_SHIFT*/26) & /*RUN_LEVEL_MASK*/0x3f) != paraLevel;
//        }
//
//        offset -= lineStart;
//        for (int i = 0; i < runs.length; i += 2) {
//            if (offset == runs[i]) {
//                return true;
//            }
//        }
//        return false;
//    }
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
     * A CursorController instance can be used to control a cursor in the text.
     */
    private interface CursorController extends ViewTreeObserver.OnTouchModeChangeListener {
        /**
         * Makes the cursor controller visible on screen.
         * See also {@link #hide()}.
         */
        void show();

        /**
         * Hide the cursor controller from screen.
         * See also {@link #show()}.
         */
        void hide();

        /**
         * Called when the view is detached from window. Perform house keeping task, such as
         * stopping Runnable thread that would otherwise keep a reference on the context, thus
         * preventing the activity from being recycled.
         */
        void onDetached();

        boolean isCursorBeingModified();

        boolean isActive();
    }

    //AOSP30
    void loadCursorDrawable() {
        if (mDrawableForCursor == null && mTextView.mCursorDrawableRes != 0) {
//            mDrawableForCursor = getContext().getDrawable(mCursorDrawableRes);
            mDrawableForCursor = ContextCompat.getDrawable(mTextView.getContext(), mTextView.mCursorDrawableRes);
            Log.w(TAG, "mDrawableForCursor=" + mDrawableForCursor);
        }
    }

    /** Controller for the insertion cursor. */
    private class InsertionPointCursorController implements CursorController {
        private InsertionHandleView mHandle;

        protected final EditText mTextView;

        public InsertionPointCursorController(EditText textView) {
            mTextView = textView;
        }

        public void show() {
            getHandle().show();

            //TODO: (EW) add back once the selection modifier is added
            if (mSelectionModifierCursorController != null) {
                mSelectionModifierCursorController.hide();
            }
        }

        public void hide() {
            if (mHandle != null) {
                mHandle.hide();
            }
        }

        public void onTouchModeChanged(boolean isInTouchMode) {
            if (!isInTouchMode) {
                hide();
            }
        }

        InsertionHandleView getHandle() {
            if (mSelectHandleCenter == null) {
//            mSelectHandleCenter = mTextView.getContext().getDrawable(
//                    mTextView.mTextSelectHandleRes);
                mSelectHandleCenter = ContextCompat.getDrawable(
                        mTextView.getContext(), mTextView.mTextSelectHandleRes);
            }
            if (mHandle == null) {
                mHandle = new InsertionHandleView(mSelectHandleCenter, mTextView);
            }
            return mHandle;
        }

        @Override
        public void onDetached() {
            final ViewTreeObserver observer = mTextView.getViewTreeObserver();
            observer.removeOnTouchModeChangeListener(this);

            if (mHandle != null) mHandle.onDetached();
        }

        @Override
        public boolean isCursorBeingModified() {
            return mHandle != null && mHandle.isDragging();
        }

        @Override
        public boolean isActive() {
            return mHandle != null && mHandle.isShowing();
        }

        public void invalidateHandle() {
            if (mHandle != null) {
                mHandle.invalidate();
            }
        }
    }

    class SelectionModifierCursorController implements CursorController {
        private /*static */final String TAG = SelectionModifierCursorController.class.getSimpleName();

        // The cursor controller handles, lazily created when shown.
        SelectionHandleView mStartHandle;
        private SelectionHandleView mEndHandle;
        // The offsets of that last touch down event. Remembered to start selection there.
        private int mMinTouchOffset, mMaxTouchOffset;

        private float mDownPositionX, mDownPositionY;
        private boolean mGestureStayedInTapRegion;

        // Where the user first starts the drag motion.
        private int mStartOffset = -1;

        private boolean mHaventMovedEnoughToStartDrag;
        // The line that a selection happened most recently with the drag accelerator.
        private int mLineSelectionIsOn = -1;
        // Whether the drag accelerator has selected past the initial line.
        private boolean mSwitchedLines = false;

        // Indicates the drag accelerator mode that the user is currently using.
        private int mDragAcceleratorMode = DRAG_ACCELERATOR_MODE_INACTIVE;
        // Drag accelerator is inactive.
        private static final int DRAG_ACCELERATOR_MODE_INACTIVE = 0;
        // Character based selection by dragging. Only for mouse.
        private static final int DRAG_ACCELERATOR_MODE_CHARACTER = 1;
        // Word based selection by dragging. Enabled after long pressing or double tapping.
        private static final int DRAG_ACCELERATOR_MODE_WORD = 2;
        // Paragraph based selection by dragging. Enabled after mouse triple click.
        private static final int DRAG_ACCELERATOR_MODE_PARAGRAPH = 3;

        protected final EditText mTextView;

        SelectionModifierCursorController(EditText textView) {
            mTextView = textView;
            resetTouchOffsets();
        }

        public void show() {
            if (mTextView.isInBatchEditMode()) {
                return;
            }
            initDrawables();
            initHandles();
        }

        private void initDrawables() {
            if (mSelectHandleLeft == null) {
                mSelectHandleLeft = mTextView.getContext().getDrawable(
                        mTextView.mTextSelectHandleLeftRes);
            }
            if (mSelectHandleRight == null) {
                mSelectHandleRight = mTextView.getContext().getDrawable(
                        mTextView.mTextSelectHandleRightRes);
            }
        }

        private void initHandles() {
            // Lazy object creation has to be done before updatePosition() is called.
            if (mStartHandle == null) {
                mStartHandle = new SelectionHandleView(mSelectHandleLeft, mSelectHandleRight,
                        /*com.android.internal.*/R.id.selection_start_handle,
                        HANDLE_TYPE_SELECTION_START);
            }
            if (mEndHandle == null) {
                mEndHandle = new SelectionHandleView(mSelectHandleRight, mSelectHandleLeft,
                        /*com.android.internal.*/R.id.selection_end_handle,
                        HANDLE_TYPE_SELECTION_END);
            }

            mStartHandle.show();
            mEndHandle.show();

            hideInsertionPointCursorController();
        }

        public void hide() {
            if (mStartHandle != null) mStartHandle.hide();
            if (mEndHandle != null) mEndHandle.hide();
        }

        public void enterDrag(int dragAcceleratorMode) {
            // Just need to init the handles / hide insertion cursor.
            show();
            mDragAcceleratorMode = dragAcceleratorMode;
            // Start location of selection.
            mStartOffset = mTextView.getOffsetForPosition(mLastDownPositionX,
                    mLastDownPositionY);
            mLineSelectionIsOn = mTextView.getLineAtCoordinate(mLastDownPositionY);
            // Don't show the handles until user has lifted finger.
            hide();

            // This stops scrolling parents from intercepting the touch event, allowing
            // the user to continue dragging across the screen to select text; TextView will
            // scroll as necessary.
            mTextView.getParent().requestDisallowInterceptTouchEvent(true);
            mTextView.cancelLongPress();
        }

        public void onTouchEvent(MotionEvent event) {
            // This is done even when the View does not have focus, so that long presses can start
            // selection and tap can move cursor from this tap position.
            final float eventX = event.getX();
            final float eventY = event.getY();
            final boolean isMouse = event.isFromSource(InputDevice.SOURCE_MOUSE);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (extractedTextModeWillBeStarted()) {
                        // Prevent duplicating the selection handles until the mode starts.
                        hide();
                    } else {
                        // Remember finger down position, to be able to start selection from there.
                        mMinTouchOffset = mMaxTouchOffset = mTextView.getOffsetForPosition(
                                eventX, eventY);
                        Log.w(TAG, "onTouchEvent: touchOffset=" + mMinTouchOffset);

                        // Double tap detection
                        if (mGestureStayedInTapRegion) {
                            if (mTapState == TAP_STATE_DOUBLE_TAP
                                    || mTapState == TAP_STATE_TRIPLE_CLICK) {
                                final float deltaX = eventX - mDownPositionX;
                                final float deltaY = eventY - mDownPositionY;
                                final float distanceSquared = deltaX * deltaX + deltaY * deltaY;

                                ViewConfiguration viewConfiguration = ViewConfiguration.get(
                                        mTextView.getContext());
                                int doubleTapSlop = viewConfiguration.getScaledDoubleTapSlop();
                                boolean stayedInArea =
                                        distanceSquared < doubleTapSlop * doubleTapSlop;

                                if (stayedInArea && (isMouse || isPositionOnText(eventX, eventY))) {
                                    if (mTapState == TAP_STATE_DOUBLE_TAP) {
                                        selectCurrentWordAndStartDrag();
                                    } else if (mTapState == TAP_STATE_TRIPLE_CLICK) {
                                        selectCurrentParagraphAndStartDrag();
                                    }
                                    mDiscardNextActionUp = true;
                                }
                            }
                        }

                        mDownPositionX = eventX;
                        mDownPositionY = eventY;
                        mGestureStayedInTapRegion = true;
                        mHaventMovedEnoughToStartDrag = true;
                    }
                    break;

                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_POINTER_UP:
                    // Handle multi-point gestures. Keep min and max offset positions.
                    // Only activated for devices that correctly handle multi-touch.
                    if (mTextView.getContext().getPackageManager().hasSystemFeature(
                            PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT)) {
                        updateMinAndMaxOffsets(event);
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    final ViewConfiguration viewConfig = ViewConfiguration.get(
                            mTextView.getContext());
                    final int touchSlop = viewConfig.getScaledTouchSlop();

                    if (mGestureStayedInTapRegion || mHaventMovedEnoughToStartDrag) {
                        final float deltaX = eventX - mDownPositionX;
                        final float deltaY = eventY - mDownPositionY;
                        final float distanceSquared = deltaX * deltaX + deltaY * deltaY;

                        if (mGestureStayedInTapRegion) {
                            int doubleTapTouchSlop = viewConfig./*getScaledDoubleTapTouchSlop*/getScaledTouchSlop();
                            mGestureStayedInTapRegion =
                                    distanceSquared <= doubleTapTouchSlop * doubleTapTouchSlop;
                        }
                        if (mHaventMovedEnoughToStartDrag) {
                            // We don't start dragging until the user has moved enough.
                            mHaventMovedEnoughToStartDrag =
                                    distanceSquared <= touchSlop * touchSlop;
                        }
                    }

                    if (isMouse && !isDragAcceleratorActive()) {
                        final int offset = mTextView.getOffsetForPosition(eventX, eventY);
                        if (mTextView.hasSelection()
                                && (!mHaventMovedEnoughToStartDrag || mStartOffset != offset)
                                && offset >= mTextView.getSelectionStart()
                                && offset <= mTextView.getSelectionEnd()) {
                            startDragAndDrop();
                            break;
                        }

                        if (mStartOffset != offset) {
                            // Start character based drag accelerator.
                            stopTextActionMode();
                            enterDrag(DRAG_ACCELERATOR_MODE_CHARACTER);
                            mDiscardNextActionUp = true;
                            mHaventMovedEnoughToStartDrag = false;
                        }
                    }

                    if (mStartHandle != null && mStartHandle.isShowing()) {
                        // Don't do the drag if the handles are showing already.
                        break;
                    }

                    updateSelection(event);
                    break;

                case MotionEvent.ACTION_UP:
                    if (!isDragAcceleratorActive()) {
                        break;
                    }
                    updateSelection(event);

                    // No longer dragging to select text, let the parent intercept events.
                    mTextView.getParent().requestDisallowInterceptTouchEvent(false);

                    // No longer the first dragging motion, reset.
                    resetDragAcceleratorState();

                    if (mTextView.hasSelection()) {
                        // Drag selection should not be adjusted by the text classifier.
                        startSelectionActionModeAsync(mHaventMovedEnoughToStartDrag);
                    }
                    break;
            }
        }

        private void updateSelection(MotionEvent event) {
            if (mTextView.getLayout() != null) {
                switch (mDragAcceleratorMode) {
                    case DRAG_ACCELERATOR_MODE_CHARACTER:
                        updateCharacterBasedSelection(event);
                        break;
                    case DRAG_ACCELERATOR_MODE_WORD:
                        updateWordBasedSelection(event);
                        break;
                    case DRAG_ACCELERATOR_MODE_PARAGRAPH:
                        updateParagraphBasedSelection(event);
                        break;
                }
            }
        }

        /**
         * If the TextView allows text selection, selects the current paragraph and starts a drag.
         *
         * @return true if the drag was started.
         */
        private boolean selectCurrentParagraphAndStartDrag() {
            if (mInsertionActionModeRunnable != null) {
                mTextView.removeCallbacks(mInsertionActionModeRunnable);
            }
            stopTextActionMode();
            if (!selectCurrentParagraph()) {
                return false;
            }
            enterDrag(SelectionModifierCursorController.DRAG_ACCELERATOR_MODE_PARAGRAPH);
            return true;
        }

        private void updateCharacterBasedSelection(MotionEvent event) {
            final int offset = mTextView.getOffsetForPosition(event.getX(), event.getY());
            updateSelectionInternal(mStartOffset, offset,
                    event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN));
        }

        private void updateWordBasedSelection(MotionEvent event) {
            if (mHaventMovedEnoughToStartDrag) {
                return;
            }
            final boolean isMouse = event.isFromSource(InputDevice.SOURCE_MOUSE);
            final ViewConfiguration viewConfig = ViewConfiguration.get(
                    mTextView.getContext());
            final float eventX = event.getX();
            final float eventY = event.getY();
            final int currLine;
            if (isMouse) {
                // No need to offset the y coordinate for mouse input.
                currLine = mTextView.getLineAtCoordinate(eventY);
            } else {
                float y = eventY;
                if (mSwitchedLines) {
                    // Offset the finger by the same vertical offset as the handles.
                    // This improves visibility of the content being selected by
                    // shifting the finger below the content, this is applied once
                    // the user has switched lines.
                    final int touchSlop = viewConfig.getScaledTouchSlop();
                    final float fingerOffset = (mStartHandle != null)
                            ? mStartHandle.getIdealVerticalOffset()
                            : touchSlop;
                    y = eventY - fingerOffset;
                }

                currLine = getCurrentLineAdjustedForSlop(mTextView.getLayout(), mLineSelectionIsOn,
                        y);
                if (!mSwitchedLines && currLine != mLineSelectionIsOn) {
                    // Break early here, we want to offset the finger position from
                    // the selection highlight, once the user moved their finger
                    // to a different line we should apply the offset and *not* switch
                    // lines until recomputing the position with the finger offset.
                    mSwitchedLines = true;
                    return;
                }
            }

            int startOffset;
            int offset = mTextView.getOffsetAtCoordinate(currLine, eventX);
            // Snap to word boundaries.
            if (mStartOffset < offset) {
                // Expanding with end handle.
                offset = getWordEnd(offset);
                startOffset = getWordStart(mStartOffset);
            } else {
                // Expanding with start handle.
                offset = getWordStart(offset);
                startOffset = getWordEnd(mStartOffset);
                if (startOffset == offset) {
                    offset = getNextCursorOffset(offset, false);
                }
            }
            mLineSelectionIsOn = currLine;
            updateSelectionInternal(startOffset, offset,
                    event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN));
        }

        private void updateParagraphBasedSelection(MotionEvent event) {
            final int offset = mTextView.getOffsetForPosition(event.getX(), event.getY());

            final int start = Math.min(offset, mStartOffset);
            final int end = Math.max(offset, mStartOffset);
            //TODO: (EW) handle
//        final long paragraphsRange = getParagraphsRange(start, end);
//        final int selectionStart = TextUtils.unpackRangeStartFromLong(paragraphsRange);
//        final int selectionEnd = TextUtils.unpackRangeEndFromLong(paragraphsRange);
//        updateSelectionInternal(selectionStart, selectionEnd,
//                event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN));
        }

        private void updateSelectionInternal(int selectionStart, int selectionEnd,
                                             boolean fromTouchScreen) {
            final boolean performHapticFeedback = fromTouchScreen && mHapticTextHandleEnabled
                    && ((mTextView.getSelectionStart() != selectionStart)
                    || (mTextView.getSelectionEnd() != selectionEnd));
            Selection.setSelection((Spannable) mTextView.getText(), selectionStart, selectionEnd);
            if (performHapticFeedback) {
                mTextView.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
            }
        }

        /**
         * @param event
         */
        private void updateMinAndMaxOffsets(MotionEvent event) {
            int pointerCount = event.getPointerCount();
            for (int index = 0; index < pointerCount; index++) {
                int offset = mTextView.getOffsetForPosition(event.getX(index), event.getY(index));
                if (offset < mMinTouchOffset) {
                    mMinTouchOffset = offset;
                    Log.w(TAG, "updateMinAndMaxOffsets: mMinTouchOffset=" + mMinTouchOffset);
                }
                if (offset > mMaxTouchOffset) {
                    mMaxTouchOffset = offset;
                    Log.w(TAG, "updateMinAndMaxOffsets: mMaxTouchOffset=" + mMaxTouchOffset);
                }
            }
        }

        public int getMinTouchOffset() {
            return mMinTouchOffset;
        }

        public int getMaxTouchOffset() {
            return mMaxTouchOffset;
        }

        public void resetTouchOffsets() {
            mMinTouchOffset = mMaxTouchOffset = -1;
            Log.w(TAG, "resetTouchOffsets: touchOffset=" + mMinTouchOffset);
            resetDragAcceleratorState();
        }

        private void resetDragAcceleratorState() {
            mStartOffset = -1;
            mDragAcceleratorMode = DRAG_ACCELERATOR_MODE_INACTIVE;
            mSwitchedLines = false;
            final int selectionStart = mTextView.getSelectionStart();
            final int selectionEnd = mTextView.getSelectionEnd();
            if (selectionStart < 0 || selectionEnd < 0) {
                Selection.removeSelection((Spannable) mTextView.getText());
            } else if (selectionStart > selectionEnd) {
                Selection.setSelection((Spannable) mTextView.getText(),
                        selectionEnd, selectionStart);
            }
        }

        /**
         * @return true iff this controller is currently used to move the selection start.
         */
        public boolean isSelectionStartDragged() {
            return mStartHandle != null && mStartHandle.isDragging();
        }

        @Override
        public boolean isCursorBeingModified() {
            return isDragAcceleratorActive() || isSelectionStartDragged()
                    || (mEndHandle != null && mEndHandle.isDragging());
        }

        /**
         * @return true if the user is selecting text using the drag accelerator.
         */
        public boolean isDragAcceleratorActive() {
            return mDragAcceleratorMode != DRAG_ACCELERATOR_MODE_INACTIVE;
        }

        public void onTouchModeChanged(boolean isInTouchMode) {
            if (!isInTouchMode) {
                hide();
            }
        }

        @Override
        public void onDetached() {
            final ViewTreeObserver observer = mTextView.getViewTreeObserver();
            observer.removeOnTouchModeChangeListener(this);

            if (mStartHandle != null) mStartHandle.onDetached();
            if (mEndHandle != null) mEndHandle.onDetached();
        }

        @Override
        public boolean isActive() {
            return mStartHandle != null && mStartHandle.isShowing();
        }

        public void invalidateHandles() {
            if (mStartHandle != null) {
                mStartHandle.invalidate();
            }
            if (mEndHandle != null) {
                mEndHandle.invalidate();
            }
        }
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

    static class InputContentType {
        public int imeOptions = EditorInfo.IME_NULL;
        public String privateImeOptions;
        public CharSequence imeActionLabel;
        public int imeActionId;
        public Bundle extras;
//        OnEditorActionListener onEditorActionListener;
        public boolean enterDown;
        public LocaleList imeHintLocales;
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
     * A helper for enabling and handling "PROCESS_TEXT" menu actions.
     * These allow external applications to plug into currently selected text.
     */
    static final class ProcessTextIntentActionsHandler {

        private final Editor mEditor;
        private final EditText mTextView;
        private final Context mContext;
        private final PackageManager mPackageManager;
        private final String mPackageName;
        private final SparseArray<Intent> mAccessibilityIntents = new SparseArray<>();
        private final SparseArray<AccessibilityNodeInfo.AccessibilityAction> mAccessibilityActions =
                new SparseArray<>();
        private final List<ResolveInfo> mSupportedActivities = new ArrayList<>();

        private ProcessTextIntentActionsHandler(Editor editor) {
            mEditor = /*Preconditions.checkNotNull*/(editor);
            mTextView = /*Preconditions.checkNotNull*/(mEditor.mTextView);
            mContext = /*Preconditions.checkNotNull*/(mTextView.getContext());
            mPackageManager = /*Preconditions.checkNotNull*/(mContext.getPackageManager());
            mPackageName = /*Preconditions.checkNotNull*/(mContext.getPackageName());
        }

        /**
         * Adds "PROCESS_TEXT" menu items to the specified menu.
         */
        public void onInitializeMenu(Menu menu) {
            loadSupportedActivities();
            final int size = mSupportedActivities.size();
            for (int i = 0; i < size; i++) {
                final ResolveInfo resolveInfo = mSupportedActivities.get(i);
                menu.add(Menu.NONE, Menu.NONE,
                        MENU_ITEM_ORDER_PROCESS_TEXT_INTENT_ACTIONS_START + i,
                        getLabel(resolveInfo))
                        .setIntent(createProcessTextIntentForResolveInfo(resolveInfo))
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            }
        }

        /**
         * Performs a "PROCESS_TEXT" action if there is one associated with the specified
         * menu item.
         *
         * @return True if the action was performed, false otherwise.
         */
        public boolean performMenuItemAction(MenuItem item) {
            return fireIntent(item.getIntent());
        }

        /**
         * Initializes and caches "PROCESS_TEXT" accessibility actions.
         */
        public void initializeAccessibilityActions() {
            mAccessibilityIntents.clear();
            mAccessibilityActions.clear();
            int i = 0;
            loadSupportedActivities();
            for (ResolveInfo resolveInfo : mSupportedActivities) {
                int actionId = EditText.ACCESSIBILITY_ACTION_PROCESS_TEXT_START_ID + i++;
                mAccessibilityActions.put(
                        actionId,
                        new AccessibilityNodeInfo.AccessibilityAction(
                                actionId, getLabel(resolveInfo)));
                mAccessibilityIntents.put(
                        actionId, createProcessTextIntentForResolveInfo(resolveInfo));
            }
        }

        /**
         * Adds "PROCESS_TEXT" accessibility actions to the specified accessibility node info.
         * NOTE: This needs a prior call to {@link #initializeAccessibilityActions()} to make the
         * latest accessibility actions available for this call.
         */
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo nodeInfo) {
            for (int i = 0; i < mAccessibilityActions.size(); i++) {
                nodeInfo.addAction(mAccessibilityActions.valueAt(i));
            }
        }

        /**
         * Performs a "PROCESS_TEXT" action if there is one associated with the specified
         * accessibility action id.
         *
         * @return True if the action was performed, false otherwise.
         */
        public boolean performAccessibilityAction(int actionId) {
            return fireIntent(mAccessibilityIntents.get(actionId));
        }

        private boolean fireIntent(Intent intent) {
            if (intent != null && Intent.ACTION_PROCESS_TEXT.equals(intent.getAction())) {
                String selectedText = mTextView.getSelectedText();
                selectedText = HiddenTextUtils.trimToParcelableSize(selectedText);
                intent.putExtra(Intent.EXTRA_PROCESS_TEXT, selectedText);
                mEditor.mPreserveSelection = true;
                //TODO: (EW) find an alternative (blocked on View - UnsupportedAppUsage)
//            mTextView.startActivityForResult(intent, EditText.PROCESS_TEXT_REQUEST_CODE);
                return true;
            }
            return false;
        }

        private void loadSupportedActivities() {
            mSupportedActivities.clear();
//        if (!mContext.canStartActivityForResult()) {
//            return;
//        }
            PackageManager packageManager = mTextView.getContext().getPackageManager();
            List<ResolveInfo> unfiltered =
                    packageManager.queryIntentActivities(createProcessTextIntent(), 0);
            for (ResolveInfo info : unfiltered) {
                if (isSupportedActivity(info)) {
                    mSupportedActivities.add(info);
                }
            }
        }

        private boolean isSupportedActivity(ResolveInfo info) {
            return mPackageName.equals(info.activityInfo.packageName)
                    || info.activityInfo.exported
                    && (info.activityInfo.permission == null
                    || Build.VERSION.SDK_INT < Build.VERSION_CODES.M//TODO: (EW) verify this is right
                    || mContext.checkSelfPermission(info.activityInfo.permission)
                    == PackageManager.PERMISSION_GRANTED);
        }

        private Intent createProcessTextIntentForResolveInfo(ResolveInfo info) {
            return createProcessTextIntent()
                    .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, !mTextView.isTextEditable())
                    .setClassName(info.activityInfo.packageName, info.activityInfo.name);
        }

        private Intent createProcessTextIntent() {
            return new Intent()
                    .setAction(Intent.ACTION_PROCESS_TEXT)
                    .setType("text/plain");
        }

        private CharSequence getLabel(ResolveInfo resolveInfo) {
            return resolveInfo.loadLabel(mPackageManager);
        }
    }

    static void logCursor(String location, @Nullable String msgFormat, Object ... msgArgs) {
        if (msgFormat == null) {
            Log.d(TAG, location);
        } else {
            Log.d(TAG, location + ": " + String.format(msgFormat, msgArgs));
        }
    }
}
