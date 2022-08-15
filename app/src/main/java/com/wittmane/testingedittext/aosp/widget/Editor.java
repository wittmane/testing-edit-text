/*
 * Copyright (C) 2022 Eli Wittman
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipData.Item;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Layout;
import android.text.ParcelableSpan;
import android.text.Selection;
import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.KeyListener;
import android.text.style.EasyEditSpan;
import android.text.style.SuggestionSpan;
import android.text.style.TextAppearanceSpan;
import android.text.style.URLSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.ActionMode.Callback;
import android.view.ContentInfo;
import android.view.ContextMenu;
import android.view.ContextThemeWrapper;
import android.view.DragAndDropPermissions;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.OnReceiveContentListener;
import android.view.SubMenu;
import android.view.View;
import android.view.View.DragShadowBuilder;
import android.view.View.MeasureSpec;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.wittmane.testingedittext.aosp.text.style.SpellCheckSpan;
import com.wittmane.testingedittext.wrapper.BreakIterator;
import com.wittmane.testingedittext.aosp.content.UndoManager;
import com.wittmane.testingedittext.aosp.content.UndoOperation;
import com.wittmane.testingedittext.aosp.content.UndoOwner;
import com.wittmane.testingedittext.aosp.os.ParcelableParcel;
import com.wittmane.testingedittext.aosp.text.HiddenLayout;
import com.wittmane.testingedittext.aosp.text.method.MovementMethod;
import com.wittmane.testingedittext.aosp.text.method.WordIterator;
import com.wittmane.testingedittext.aosp.internal.widget.EditableInputConnection;
import com.wittmane.testingedittext.aosp.text.HiddenTextUtils;
import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.aosp.text.style.SuggestionRangeSpan;
import com.wittmane.testingedittext.aosp.widget.EditText.OnEditorActionListener;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static android.view.ContentInfo.SOURCE_DRAG_AND_DROP;

/**
 * Helper class used by EditText to handle editable text views.
 */
class Editor {
    private static final String TAG = Editor.class.getSimpleName();
    private static final boolean DEBUG_UNDO = false;
    private static final boolean DEBUG_CURSOR_ANCHOR_INFO = false;

    private static final int DELAY_BEFORE_HANDLE_FADES_OUT = 4000;
    private static final int RECENT_CUT_COPY_DURATION_MS = 15 * 1000; // 15 seconds in millis

    static final int BLINK = 500;
    private static final int DRAG_SHADOW_MAX_TEXT_LENGTH = 20;
    private static final int UNSET_X_VALUE = -1;
    private static final int UNSET_LINE = -1;
    // Tag used when the Editor maintains its own separate UndoManager.
    private static final String UNDO_OWNER_TAG = "Editor";

    // Ordering constants used to place the Action Mode or context menu items in their menu.
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
    private static final int MENU_ITEM_ORDER_PROCESS_TEXT_INTENT_ACTIONS_START = 100;

    private static final int FLAG_MISSPELLED_OR_GRAMMAR_ERROR =
            SuggestionSpan.FLAG_MISSPELLED | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? SuggestionSpan.FLAG_GRAMMAR_ERROR : 8);

    @IntDef({TextActionMode.SELECTION, TextActionMode.INSERTION})
    @interface TextActionMode {
        int SELECTION = 0;
        int INSERTION = 1;
    }

    // Default content insertion handler.
    private final TextViewOnReceiveContentListener mDefaultOnReceiveContentListener =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? new TextViewOnReceiveContentListener() : null;

    // Each Editor manages its own undo stack.
    private final UndoManager mUndoManager = new UndoManager();
    private UndoOwner mUndoOwner = mUndoManager.getOwner(UNDO_OWNER_TAG, this);
    final UndoInputFilter mUndoInputFilter = new UndoInputFilter(this);
    boolean mAllowUndo = true;

    // Cursor Controllers.
    InsertionPointCursorController mInsertionPointCursorController;
    SelectionModifierCursorController mSelectionModifierCursorController;
    // Action mode used when text is selected or when actions on an insertion cursor are triggered.
    private ActionMode mTextActionMode;
    private boolean mInsertionControllerEnabled;
    private boolean mSelectionControllerEnabled;

    private final boolean mHapticTextHandleEnabled;

    // Used to highlight a word when it is corrected by the IME
    private CorrectionHighlighter mCorrectionHighlighter;

    /**
     * {@code true} when {@link EditText#setText(CharSequence, boolean, int)}
     * is being executed and {@link InputMethodManager#restartInput(View)} is scheduled to be
     * called.
     *
     * <p>This is also used to avoid an unnecessary invocation of
     * {@link InputMethodManager#updateSelection(View, int, int, int, int)} when
     * {@link InputMethodManager#restartInput(View)} is scheduled to be called already
     * See bug 186582769 for details.</p>
     *
     * <p>TODO(186582769): Come up with better way.</p>
     */
    private boolean mHasPendingRestartInputForSetText = false;

    InputContentType mInputContentType;
    InputMethodState mInputMethodState;

    boolean mFrozenWithFocus;
    boolean mSelectionMoved;
    boolean mTouchFocusSelected;

    KeyListener mKeyListener;
    int mInputType = EditorInfo.TYPE_NULL;

    boolean mDiscardNextActionUp;
    boolean mIgnoreActionUpEvent;

    /**
     * To set a custom cursor, you should use {@link EditText#setTextCursorDrawable(Drawable)}
     * or {@link EditText#setTextCursorDrawable(int)}.
     */
    private long mShowCursor;
    private Blink mBlink;

    boolean mCursorVisible = true;
    boolean mSelectAllOnFocus;

    boolean mInBatchEditControllers;
    boolean mShowSoftInputOnFocus = true;
    // (EW) needed for versions prior to Nougat since View#isTemporarilyDetached didn't exist yet
    boolean mTemporaryDetach;
    private boolean mPreserveSelection;
    private boolean mRestartActionModeOnNextRefresh;

    private SelectionActionModeHelper mSelectionActionModeHelper;

    boolean mIsBeingLongClicked;
    boolean mIsBeingLongClickedByAccessibility;

    private SuggestionsPopupWindow mSuggestionsPopupWindow;
    SuggestionRangeSpan mSuggestionRangeSpan;
    private Runnable mShowSuggestionRunnable;

    Drawable mDrawableForCursor = null;

    private Drawable mSelectHandleLeft;
    private Drawable mSelectHandleRight;
    private Drawable mSelectHandleCenter;

    // Global listener that detects changes in the global position of the EditText
    private PositionListener mPositionListener;

    private float mContextMenuAnchorX, mContextMenuAnchorY;
    Callback mCustomSelectionActionModeCallback;
    Callback mCustomInsertionActionModeCallback;

    // Set when this EditText gained focus with some text selected. Will start selection mode.
    boolean mCreatedWithASelection;

    // The button state as of the last time #onTouchEvent is called.
    private int mLastButtonState;

    private final EditorTouchState mTouchState = new EditorTouchState();

    private Runnable mInsertionActionModeRunnable;

    // The span controller helps monitoring the changes to which the Editor needs to react:
    // - EasyEditSpans, for which we have some UI to display on attach and on hide
    // - SelectionSpans, for which we need to call updateSelection if an IME is attached
    private SpanController mSpanController;

    private WordIterator mWordIterator;
    SpellChecker mSpellChecker;

    // This word iterator is set with text and used to determine word boundaries
    // when a user is selecting text.
    private WordIterator mWordIteratorWithText;
    // Indicate that the text in the word iterator needs to be updated.
    private boolean mUpdateWordIteratorText;

    private Rect mTempRect;

    private final EditText mEditText;

    final ProcessTextIntentActionsHandler mProcessTextIntentActionsHandler;

    private final CursorAnchorInfoNotifier mCursorAnchorInfoNotifier =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                    ? new CursorAnchorInfoNotifier() : null;

    private final Runnable mShowFloatingToolbar = new Runnable() {
        @Override
        public void run() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                // (EW) this wasn't done prior to M because there was a fixed, rather than floating,
                // toolbar
                return;
            }
            if (mTextActionMode != null) {
                mTextActionMode.hide(0);  // hide off.
            }
        }
    };

    boolean mIsInsertionActionModeStartPending = false;

    private final SuggestionHelper mSuggestionHelper = new SuggestionHelper();

    private final boolean mFlagCursorDragFromAnywhereEnabled;
    private final float mCursorDragDirectionMinXYRatio;
    private final boolean mFlagInsertionHandleGesturesEnabled;

    // For calculating the line change slops while moving cursor/selection.
    // The slop value as ratio of the current line height. It indicates the tolerant distance to
    // avoid the cursor jumps to upper/lower line when the hit point is moving vertically out of
    // the current line.
    private final float mLineSlopRatio;
    // The slop max/min value include line height and the slop on the upper/lower line.
    private static final int LINE_CHANGE_SLOP_MAX_DP = 45;
    private static final int LINE_CHANGE_SLOP_MIN_DP = 8;
    private final int mLineChangeSlopMax;
    private final int mLineChangeSlopMin;

    private CursorAnchorInfo mLastCursorAnchorInfo;

    Editor(EditText editText) {
        mEditText = editText;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mProcessTextIntentActionsHandler = new ProcessTextIntentActionsHandler(this);
        } else {
            mProcessTextIntentActionsHandler = null;
        }
        mHapticTextHandleEnabled = mEditText.getContext().getResources().getBoolean(
                R.bool.config_enableHapticTextHandle);

        // (EW) the AOSP version checks AppGlobals.getIntCoreSetting starting in R, which is on the
        // hidden API blacklist, so it can't even be used with reflection. it might be nice to pull
        // in some system settings, but at least for now that doesn't really seem to be an option.
        // I disabled the blacklist on an emulator with
        // adb shell settings put global hidden_api_policy 1
        // to at least check the value, although this could be different on different devices or
        // maybe from some system settings. from my testing using reflection, 1 (true) was returned
        // (not actually coming from the default value
        // WidgetFlags.ENABLE_CURSOR_DRAG_FROM_ANYWHERE_DEFAULT (true)). based on this, it would
        // make sense to have this be true, but it prevents scrolling a single line, which seems
        // annoying/buggy and is different from functionality in older versions so I decided to
        // disable this. alternatively, this could be a version check so it matches the framework
        // view, but it seems weird to change this behavior in different versions of the app.
        mFlagCursorDragFromAnywhereEnabled = false;
        // (EW) the AOSP version checks AppGlobals.getIntCoreSetting starting in S with a default
        // value of WidgetFlags.CURSOR_DRAG_MIN_ANGLE_FROM_VERTICAL_DEFAULT (45). this replaced the
        // previously hard-coded handling in EditorTouchState, which was also 45.
        final int cursorDragMinAngleFromVertical = 45;
        mCursorDragDirectionMinXYRatio = EditorTouchState.getXYRatio(
                cursorDragMinAngleFromVertical);
        // (EW) the AOSP version checks AppGlobals.getIntCoreSetting starting in R with a default
        // value of WidgetFlags.ENABLE_CURSOR_DRAG_FROM_ANYWHERE_DEFAULT (true). from my testing
        // using reflection, 0 (false) was returned. there was no equivalent for this in older
        // versions to reference. unfortunately these don't match to make a simple decision, but
        // since all of the others that match the default aren't actually using the default
        // (something is explicitly set to that same value), the hard-coded default may not be that
        // important to reference. if I find cases that match the default, this should be
        // reevaluated, but matching the framework view's functionality as much as I'm aware seems
        // best.
        mFlagInsertionHandleGesturesEnabled = false;
        // (EW) the AOSP version checks AppGlobals.getFloatCoreSetting starting in S with a default
        // value of WidgetFlags.LINE_SLOP_RATIO_DEFAULT (.5f). from my testing using reflection,
        // 0.5f was returned (not actually coming from the default value). this replaced
        // LINE_SLOP_MULTIPLIER_FOR_HANDLEVIEWS from older versions, which was also 0.5f.
        mLineSlopRatio = 0.5f;
        if (EditText.DEBUG_CURSOR) {
            logCursor("Editor", "Cursor drag from anywhere is %s.",
                    mFlagCursorDragFromAnywhereEnabled ? "enabled" : "disabled");
            logCursor("Editor", "Cursor drag min angle from vertical is %d (= %f x/y ratio)",
                    cursorDragMinAngleFromVertical, mCursorDragDirectionMinXYRatio);
            logCursor("Editor", "Insertion handle gestures is %s.",
                    mFlagInsertionHandleGesturesEnabled ? "enabled" : "disabled");
        }

        mLineChangeSlopMax = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, LINE_CHANGE_SLOP_MAX_DP,
                mEditText.getContext().getResources().getDisplayMetrics());
        mLineChangeSlopMin = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, LINE_CHANGE_SLOP_MIN_DP,
                mEditText.getContext().getResources().getDisplayMetrics());
    }

    ParcelableParcel saveInstanceState() {
        ParcelableParcel state = new ParcelableParcel(getClass().getClassLoader());
        Parcel parcel = state.getParcel();
        mUndoManager.saveInstanceState(parcel);
        mUndoInputFilter.saveInstanceState(parcel);
        return state;
    }

    void restoreInstanceState(ParcelableParcel state) {
        Parcel parcel = state.getParcel();
        mUndoManager.restoreInstanceState(parcel, state.getClassLoader());
        mUndoInputFilter.restoreInstanceState(parcel);
        // Re-associate this object as the owner of undo state.
        mUndoOwner = mUndoManager.getOwner(UNDO_OWNER_TAG, this);
    }

    /**
     * Returns the default handler for receiving content in an editable {@link EditText}. This
     * listener impl is used to encapsulate the default behavior but it is not part of the public
     * API. If an app wants to execute the default platform behavior for receiving content, it
     * should call {@link View#onReceiveContent}. Alternatively, if an app implements a custom
     * listener for receiving content and wants to delegate some of the content to be handled by
     * the platform, it should return the corresponding content from its listener. See
     * {@link View#setOnReceiveContentListener} and {@link OnReceiveContentListener} for more info.
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
    @NonNull
    public TextViewOnReceiveContentListener getDefaultOnReceiveContentListener() {
        return mDefaultOnReceiveContentListener;
    }

    /**
     * Forgets all undo and redo operations for this Editor.
     */
    void forgetUndoRedo() {
        UndoOwner[] owners = { mUndoOwner };
        mUndoManager.forgetUndos(owners, -1 /* all */);
        mUndoManager.forgetRedos(owners, -1 /* all */);
    }

    boolean canUndo() {
        UndoOwner[] owners = { mUndoOwner };
        return mAllowUndo && mUndoManager.countUndos(owners) > 0;
    }

    boolean canRedo() {
        UndoOwner[] owners = { mUndoOwner };
        return mAllowUndo && mUndoManager.countRedos(owners) > 0;
    }

    void undo() {
        if (!mAllowUndo) {
            return;
        }
        UndoOwner[] owners = { mUndoOwner };
        mUndoManager.undo(owners, 1);  // Undo 1 action.
    }

    void redo() {
        if (!mAllowUndo) {
            return;
        }
        UndoOwner[] owners = { mUndoOwner };
        mUndoManager.redo(owners, 1);  // Redo 1 action.
    }

    void replace() {
        if (mSuggestionsPopupWindow == null) {
            mSuggestionsPopupWindow = new SuggestionsPopupWindow();
        }
        hideCursorAndSpanControllers();
        mSuggestionsPopupWindow.show();

        int middle = (mEditText.getSelectionStart() + mEditText.getSelectionEnd()) / 2;
        Selection.setSelection(mEditText.getText(), middle);
    }

    void onAttachedToWindow() {
        // (EW) needed for versions prior to Nougat
        mTemporaryDetach = false;

        final ViewTreeObserver observer = mEditText.getViewTreeObserver();
        if (observer.isAlive()) {
            // No need to create the controller.
            // The get method will add the listener on controller creation.
            if (mInsertionPointCursorController != null) {
                observer.addOnTouchModeChangeListener(mInsertionPointCursorController);
            }
            if (mSelectionModifierCursorController != null) {
                mSelectionModifierCursorController.resetTouchOffsets();
                observer.addOnTouchModeChangeListener(mSelectionModifierCursorController);
            }
        }

        updateSpellCheckSpans(0, mEditText.getText().length(),
                true /* create the spell checker if needed */);

        if (mEditText.hasSelection()) {
            refreshTextActionMode();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // (EW) only necessary for Lollipop and later. InputMethodManager#updateCursor was used
            // in previous versions, which has different handling.
            getPositionListener().addSubscriber(mCursorAnchorInfoNotifier, true);
        }
        resumeBlink();
    }

    void onDetachedFromWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getPositionListener().removeSubscriber(mCursorAnchorInfoNotifier);
        }

        suspendBlink();

        if (mInsertionPointCursorController != null) {
            mInsertionPointCursorController.onDetached();
        }

        if (mSelectionModifierCursorController != null) {
            mSelectionModifierCursorController.onDetached();
        }

        if (mShowSuggestionRunnable != null) {
            mEditText.removeCallbacks(mShowSuggestionRunnable);
        }

        // Cancel the single tap delayed runnable.
        if (mInsertionActionModeRunnable != null) {
            mEditText.removeCallbacks(mInsertionActionModeRunnable);
        }

        mEditText.removeCallbacks(mShowFloatingToolbar);

        if (mSpellChecker != null) {
            mSpellChecker.closeSession();
            // Forces the creation of a new SpellChecker next time this window is created.
            // Will handle the cases where the settings has been changed in the meantime.
            mSpellChecker = null;
        }

        hideCursorAndSpanControllers();
        stopTextActionModeWithPreservingSelection();
        // (EW) needed for versions prior to Nougat
        mTemporaryDetach = false;

        if (mDefaultOnReceiveContentListener != null
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mDefaultOnReceiveContentListener.clearInputConnectionInfo();
        }
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
        return mCursorVisible && mEditText.isTextEditable();
    }

    boolean shouldRenderCursor() {
        if (!isCursorVisible()) {
            return false;
        }
        final long showCursorDelta = SystemClock.uptimeMillis() - mShowCursor;
        return showCursorDelta % (2 * BLINK) < BLINK;
    }

    void prepareCursorControllers() {
        boolean windowSupportsHandles = false;

        ViewGroup.LayoutParams params = mEditText.getRootView().getLayoutParams();
        if (params instanceof WindowManager.LayoutParams) {
            WindowManager.LayoutParams windowParams = (WindowManager.LayoutParams) params;
            windowSupportsHandles = windowParams.type < WindowManager.LayoutParams.FIRST_SUB_WINDOW
                    || windowParams.type > WindowManager.LayoutParams.LAST_SUB_WINDOW;
        }

        boolean enabled = windowSupportsHandles && mEditText.getLayout() != null;
        mInsertionControllerEnabled = enabled && isCursorVisible();
        mSelectionControllerEnabled = enabled && mEditText.textCanBeSelected();

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
        // When mEditText is not ExtractEditText, we need to distinguish two kinds of focus-lost.
        // One is the true focus lost where suggestions pop-up (if any) should be dismissed, and the
        // other is an side effect of showing the suggestions pop-up itself. We use isShowingUp()
        // to distinguish one from the other.
        if (mSuggestionsPopupWindow != null && ((mEditText.isInExtractedMode())
                || !mSuggestionsPopupWindow.isShowingUp())) {
            // Should be done before hide insertion point controller since it triggers a show of it
            mSuggestionsPopupWindow.hide();
        }
        hideInsertionPointCursorController();
    }

    /**
     * Create new SpellCheckSpans on the modified region.
     */
    private void updateSpellCheckSpans(int start, int end, boolean createSpellChecker) {
        // Remove spans whose adjacent characters are text not punctuation
        mEditText.removeAdjacentSuggestionSpans(start);
        mEditText.removeAdjacentSuggestionSpans(end);

        if (mEditText.isTextEditable() && mEditText.isSuggestionsEnabled()
                && !(mEditText.isInExtractedMode())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // (EW) InputMethodManager#isInputMethodSuppressingSpellChecker was added in S and
                // there wasn't any alternate handling in older versions
                final InputMethodManager imm = getInputMethodManager();
                if (imm != null && imm.isInputMethodSuppressingSpellChecker()) {
                    // Do not close mSpellChecker here as it may be reused when the current IME has
                    // been changed.
                    return;
                }
            }
            if (mSpellChecker == null && createSpellChecker) {
                mSpellChecker = new SpellChecker(mEditText);
            }
            if (mSpellChecker != null) {
                mSpellChecker.spellCheck(start, end);
            }
        }
    }

    void onScreenStateChanged(int screenState) {
        switch (screenState) {
            case View.SCREEN_STATE_ON:
                resumeBlink();
                break;
            case View.SCREEN_STATE_OFF:
                suspendBlink();
                break;
        }
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

    void adjustInputType(boolean passwordInputType,
                         boolean webPasswordInputType, boolean numberPasswordInputType) {
        // mInputType has been set from inputType, possibly modified by mInputMethod.
        // Specialize mInputType to [web]password if we have a text class and the original input
        // type was a password.
        if ((mInputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT) {
            if (passwordInputType) {
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
        // FIXME - For this and similar methods we're not doing anything to check if there's
        // a LocaleSpan in the text, this may be something we should try handling or checking for.
        int retOffset = getWordIteratorWithText().prevBoundary(offset);
        if (getWordIteratorWithText().isOnPunctuation(retOffset)) {
            // On punctuation boundary or within group of punctuation, find punctuation start.
            retOffset = getWordIteratorWithText().getPunctuationBeginning(offset);
        } else {
            // Not on a punctuation boundary, find the word start.
            retOffset = getWordIteratorWithText().getPrevWordBeginningOnTwoWordsBoundary(offset);
        }
        if (retOffset == BreakIterator.DONE) {
            return offset;
        }
        return retOffset;
    }

    private int getWordEnd(int offset) {
        int retOffset = getWordIteratorWithText().nextBoundary(offset);
        if (getWordIteratorWithText().isAfterPunctuation(retOffset)) {
            // On punctuation boundary or within group of punctuation, find punctuation end.
            retOffset = getWordIteratorWithText().getPunctuationEnd(offset);
        } else {
            // Not on a punctuation boundary, find the word end.
            retOffset = getWordIteratorWithText().getNextWordEndOnTwoWordBoundary(offset);
        }
        if (retOffset == BreakIterator.DONE) {
            return offset;
        }
        return retOffset;
    }

    private boolean needsToSelectAllToSelectWordOrParagraph() {
        if (mEditText.hasPasswordTransformationMethod()) {
            // Always select all on a password field.
            // Cut/copy menu entries are not available for passwords, but being able to select all
            // is however useful to delete or paste to replace the entire content.
            return true;
        }

        int inputType = mEditText.getInputType();
        int klass = inputType & InputType.TYPE_MASK_CLASS;
        int variation = inputType & InputType.TYPE_MASK_VARIATION;

        // Specific text field types: select the entire text for these
        if (klass == InputType.TYPE_CLASS_NUMBER
                || klass == InputType.TYPE_CLASS_PHONE
                || klass == InputType.TYPE_CLASS_DATETIME
                || variation == InputType.TYPE_TEXT_VARIATION_URI
                || variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
            return true;
        }
        return false;
    }

    /**
     * Adjusts selection to the word under last touch offset. Return true if the operation was
     * successfully performed.
     */
    boolean selectCurrentWord() {
        if (!mEditText.canSelectText()) {
            return false;
        }

        if (needsToSelectAllToSelectWordOrParagraph()) {
            return mEditText.selectAllText();
        }

        long lastTouchOffsets = getLastTouchOffsets();
        final int minOffset = HiddenTextUtils.unpackRangeStartFromLong(lastTouchOffsets);
        final int maxOffset = HiddenTextUtils.unpackRangeEndFromLong(lastTouchOffsets);

        // Safety check in case standard touch event handling has been bypassed
        if (minOffset < 0 || minOffset > mEditText.getText().length()) return false;
        if (maxOffset < 0 || maxOffset > mEditText.getText().length()) return false;

        int selectionStart, selectionEnd;

        // If a URLSpan (web address, email, phone...) is found at that position, select it.
        URLSpan[] urlSpans = mEditText.getText().getSpans(minOffset, maxOffset, URLSpan.class);
        if (urlSpans.length >= 1) {
            URLSpan urlSpan = urlSpans[0];
            selectionStart = mEditText.getText().getSpanStart(urlSpan);
            selectionEnd = mEditText.getText().getSpanEnd(urlSpan);
        } else {
            // FIXME - We should check if there's a LocaleSpan in the text, this may be
            // something we should try handling or checking for.
            final WordIterator wordIterator = getWordIterator();
            wordIterator.setCharSequence(mEditText.getText(), minOffset, maxOffset);

            selectionStart = wordIterator.getBeginning(minOffset);
            selectionEnd = wordIterator.getEnd(maxOffset);

            if (selectionStart == BreakIterator.DONE || selectionEnd == BreakIterator.DONE
                    || selectionStart == selectionEnd) {
                // Possible when the word iterator does not properly handle the text's language
                long range = getCharClusterRange(minOffset);
                selectionStart = HiddenTextUtils.unpackRangeStartFromLong(range);
                selectionEnd = HiddenTextUtils.unpackRangeEndFromLong(range);
            }
        }

        Selection.setSelection(mEditText.getText(), selectionStart, selectionEnd);
        return selectionEnd > selectionStart;
    }

    /**
     * Adjusts selection to the paragraph under last touch offset. Return true if the operation was
     * successfully performed.
     */
    private boolean selectCurrentParagraph() {
        if (!mEditText.canSelectText()) {
            return false;
        }

        if (needsToSelectAllToSelectWordOrParagraph()) {
            return mEditText.selectAllText();
        }

        long lastTouchOffsets = getLastTouchOffsets();
        final int minLastTouchOffset = HiddenTextUtils.unpackRangeStartFromLong(lastTouchOffsets);
        final int maxLastTouchOffset = HiddenTextUtils.unpackRangeEndFromLong(lastTouchOffsets);

        final long paragraphsRange = getParagraphsRange(minLastTouchOffset, maxLastTouchOffset);
        final int start = HiddenTextUtils.unpackRangeStartFromLong(paragraphsRange);
        final int end = HiddenTextUtils.unpackRangeEndFromLong(paragraphsRange);
        if (start < end) {
            Selection.setSelection(mEditText.getText(), start, end);
            return true;
        }
        return false;
    }

    /**
     * Get the minimum range of paragraphs that contains startOffset and endOffset.
     */
    private long getParagraphsRange(int startOffset, int endOffset) {
        final Layout layout = mEditText.getLayout();
        if (layout == null) {
            return HiddenTextUtils.packRangeInLong(-1, -1);
        }
        final CharSequence text = mEditText.getText();
        int minLine = layout.getLineForOffset(startOffset);
        // Search paragraph start.
        while (minLine > 0) {
            final int prevLineEndOffset = layout.getLineEnd(minLine - 1);
            if (text.charAt(prevLineEndOffset - 1) == '\n') {
                break;
            }
            minLine--;
        }
        int maxLine = layout.getLineForOffset(endOffset);
        // Search paragraph end.
        while (maxLine < layout.getLineCount() - 1) {
            final int lineEndOffset = layout.getLineEnd(maxLine);
            if (text.charAt(lineEndOffset - 1) == '\n') {
                break;
            }
            maxLine++;
        }
        return HiddenTextUtils.packRangeInLong(layout.getLineStart(minLine), layout.getLineEnd(maxLine));
    }

    void onLocaleChanged() {
        // Will be re-created on demand in getWordIterator and getWordIteratorWithText with the
        // proper new locale
        mWordIterator = null;
        mWordIteratorWithText = null;
    }

    public WordIterator getWordIterator() {
        if (mWordIterator == null) {
            mWordIterator = new WordIterator(mEditText.getTextServicesLocale());
        }
        return mWordIterator;
    }

    private WordIterator getWordIteratorWithText() {
        if (mWordIteratorWithText == null) {
            mWordIteratorWithText = new WordIterator(mEditText.getTextServicesLocale());
            mUpdateWordIteratorText = true;
        }
        if (mUpdateWordIteratorText) {
            // FIXME - Shouldn't copy all of the text as only the area of the text relevant
            // to the user's selection is needed. A possible solution would be to
            // copy some number N of characters near the selection and then when the
            // user approaches N then we'd do another copy of the next N characters.
            CharSequence text = mEditText.getText();
            mWordIteratorWithText.setCharSequence(text, 0, text.length());
            mUpdateWordIteratorText = false;
        }
        return mWordIteratorWithText;
    }

    private int getNextCursorOffset(int offset, boolean findAfterGivenOffset) {
        final Layout layout = mEditText.getLayout();
        if (layout == null) return offset;
        return findAfterGivenOffset == layout.isRtlCharAt(offset)
                ? layout.getOffsetToLeftOf(offset) : layout.getOffsetToRightOf(offset);
    }

    private long getCharClusterRange(int offset) {
        final int textLength = mEditText.getText().length();
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
        int selectionStart = mEditText.getSelectionStart();
        int selectionEnd = mEditText.getSelectionEnd();

        if (selectionStart == selectionEnd) {
            return false;
        }

        if (selectionStart > selectionEnd) {
            int tmp = selectionStart;
            selectionStart = selectionEnd;
            selectionEnd = tmp;
            Selection.setSelection(mEditText.getText(), selectionStart, selectionEnd);
        }

        SelectionModifierCursorController selectionController = getSelectionController();
        int minOffset = selectionController.getMinTouchOffset();
        int maxOffset = selectionController.getMaxTouchOffset();

        return ((minOffset >= selectionStart) && (maxOffset < selectionEnd));
    }

    private PositionListener getPositionListener() {
        if (mPositionListener == null) {
            mPositionListener = new PositionListener();
        }
        return mPositionListener;
    }

    private interface EditTextPositionListener {
        void updatePosition(int parentPositionX, int parentPositionY,
                            boolean parentPositionChanged, boolean parentScrolled);
    }

    private boolean isOffsetVisible(int offset) {
        Layout layout = mEditText.getLayout();
        if (layout == null) return false;

        final int line = layout.getLineForOffset(offset);
        final int lineBottom = layout.getLineBottom(line);
        final int primaryHorizontal = (int) layout.getPrimaryHorizontal(offset);
        return mEditText.isPositionVisible(
                primaryHorizontal + mEditText.viewportToContentHorizontalOffset(),
                lineBottom + mEditText.viewportToContentVerticalOffset());
    }

    /** Returns true if the screen coordinates position (x,y) corresponds to a character displayed
     * in the view. Returns false when the position is in the empty space of left/right of text.
     */
    private boolean isPositionOnText(float x, float y) {
        Layout layout = mEditText.getLayout();
        if (layout == null) return false;

        final int line = mEditText.getLineAtCoordinate(y);
        x = mEditText.convertToLocalHorizontalCoordinate(x);

        if (x < layout.getLineLeft(line)) return false;
        if (x > layout.getLineRight(line)) return false;
        return true;
    }

    private void startDragAndDrop() {
        getSelectionActionModeHelper().onSelectionDrag();

        // TODO: Fix drag and drop in full screen extracted mode.
        if (mEditText.isInExtractedMode()) {
            return;
        }
        final int start = mEditText.getSelectionStart();
        final int end = mEditText.getSelectionEnd();
        CharSequence selectedText = mEditText.getTransformedText(start, end);
        ClipData data = ClipData.newPlainText(null, selectedText);
        DragLocalState localState = new DragLocalState(mEditText, start, end);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mEditText.startDragAndDrop(data, getTextThumbnailBuilder(start, end), localState,
                    View.DRAG_FLAG_GLOBAL);
        } else {
            mEditText.startDrag(data, getTextThumbnailBuilder(start, end), localState, 0);
        }
        stopTextActionMode();
        if (hasSelectionController()) {
            getSelectionController().resetTouchOffsets();
        }
    }

    public boolean performLongClick(boolean handled) {
        if (EditText.DEBUG_CURSOR) {
            logCursor("performLongClick", "handled=%s", handled);
        }
        if (mIsBeingLongClickedByAccessibility) {
            if (!handled) {
                toggleInsertionActionMode();
            }
            return true;
        }
        // Long press in empty space moves cursor and starts the insertion action mode.
        if (!handled && !isPositionOnText(mTouchState.getLastDownX(), mTouchState.getLastDownY())
                && !mTouchState.isOnHandle() && mInsertionControllerEnabled) {
            final int offset = mEditText.getOffsetForPosition(mTouchState.getLastDownX(),
                    mTouchState.getLastDownY());
            Selection.setSelection(mEditText.getText(), offset);
            getInsertionController().show();
            mIsInsertionActionModeStartPending = true;
            handled = true;
        }

        if (!handled && mTextActionMode != null) {
            if (touchPositionIsInSelection()) {
                startDragAndDrop();
            } else {
                stopTextActionMode();
                selectCurrentWordAndStartDrag();
            }
            handled = true;
        }

        // Start a new selection
        if (!handled) {
            handled = selectCurrentWordAndStartDrag();
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

    private long getLastTouchOffsets() {
        SelectionModifierCursorController selectionController = getSelectionController();
        final int minOffset = selectionController.getMinTouchOffset();
        final int maxOffset = selectionController.getMaxTouchOffset();
        return HiddenTextUtils.packRangeInLong(minOffset, maxOffset);
    }

    void onFocusChanged(boolean focused, int direction) {
        if (EditText.DEBUG_CURSOR) {
            logCursor("onFocusChanged", "focused=%s", focused);
        }

        mShowCursor = SystemClock.uptimeMillis();
        ensureEndedBatchEdit();

        if (focused) {
            int selStart = mEditText.getSelectionStart();
            int selEnd = mEditText.getSelectionEnd();

            // SelectAllOnFocus fields are highlighted and not selected. Do not start text selection
            // mode for these, unless there was a specific selection already started.
            final boolean isFocusHighlighted = mSelectAllOnFocus && selStart == 0
                    && selEnd == mEditText.getText().length();

            mCreatedWithASelection = mFrozenWithFocus && mEditText.hasSelection()
                    && !isFocusHighlighted;

            if (!mFrozenWithFocus || (selStart < 0 || selEnd < 0)) {
                // If a tap was used to give focus to that view, move cursor at tap position.
                // Has to be done before onTakeFocus, which can be overloaded.
                final int lastTapPosition = getLastTapPosition();
                if (lastTapPosition >= 0) {
                    if (EditText.DEBUG_CURSOR) {
                        logCursor("onFocusChanged", "setting cursor position: %d", lastTapPosition);
                    }
                    Selection.setSelection(mEditText.getText(), lastTapPosition);
                }

                // Note this may have to be moved out of the Editor class
                MovementMethod mMovement = mEditText.getMovementMethod();
                if (mMovement != null) {
                    mMovement.onTakeFocus(mEditText, mEditText.getText(), direction);
                }

                // The DecorView does not have focus when the 'Done' ExtractEditText button is
                // pressed. Since it is the ViewAncestor's mView, it requests focus before
                // ExtractEditText clears focus, which gives focus to the ExtractEditText.
                // This special case ensure that we keep current selection in that case.
                // It would be better to know why the DecorView does not have focus at that time.
                if (((mEditText.isInExtractedMode()) || mSelectionMoved)
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
                    Selection.setSelection(mEditText.getText(), selStart, selEnd);
                }

                if (mSelectAllOnFocus) {
                    mEditText.selectAllText();
                }

                mTouchFocusSelected = true;
            }

            mFrozenWithFocus = false;
            mSelectionMoved = false;

            makeBlink();
        } else {
            // Don't leave us in the middle of a batch edit.
            mEditText.onEndBatchEdit();

            if (mEditText.isInExtractedMode()) {
                hideCursorAndSpanControllers();
                stopTextActionModeWithPreservingSelection();
            } else {
                hideCursorAndSpanControllers();
                boolean isTemporarilyDetached;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    isTemporarilyDetached = mEditText.isTemporarilyDetached();
                } else {
                    isTemporarilyDetached = mTemporaryDetach;
                }
                if (isTemporarilyDetached) {
                    stopTextActionModeWithPreservingSelection();
                } else {
                    stopTextActionMode();
                }
                //FUTURE: (EW) this makes a single click not pop up suggestions after having focused
                // away from the text field, but the underline is still there, which seems weird.
                // this is what AOSP does, but maybe consider ditching this.
                downgradeEasyCorrectionSpans();
            }
            // No need to create the controller
            if (mSelectionModifierCursorController != null) {
                mSelectionModifierCursorController.resetTouchOffsets();
            }

            ensureNoSelectionIfNonSelectable();
        }
    }

    private void ensureNoSelectionIfNonSelectable() {
        // This could be the case if a TextLink has been tapped.
        if (!mEditText.textCanBeSelected() && mEditText.hasSelection()) {
            Selection.setSelection(mEditText.getText(), mEditText.length(), mEditText.length());
        }
    }

    /**
     * Downgrades to simple suggestions all the easy correction spans that are not a spell check
     * span.
     */
    private void downgradeEasyCorrectionSpans() {
        Spannable spannable = mEditText.getText();
        SuggestionSpan[] suggestionSpans = spannable.getSpans(0,
                spannable.length(), SuggestionSpan.class);
        for (int i = 0; i < suggestionSpans.length; i++) {
            int flags = suggestionSpans[i].getFlags();
            if ((flags & SuggestionSpan.FLAG_EASY_CORRECT) != 0
                    && (flags & FLAG_MISSPELLED_OR_GRAMMAR_ERROR) == 0) {
                flags &= ~SuggestionSpan.FLAG_EASY_CORRECT;
                suggestionSpans[i].setFlags(flags);
            }
        }
    }

    void sendOnTextChanged(int start, int before, int after) {
        getSelectionActionModeHelper().onTextChanged(start, start + before);
        updateSpellCheckSpans(start, start + after, false);

        // Flip flag to indicate the word iterator needs to have the text reset.
        mUpdateWordIteratorText = true;

        // Hide the controllers as soon as text is modified (typing, procedural...)
        // We do not hide the span controllers, since they can be added when a new text is
        // inserted into the text view (voice IME).
        hideCursorControllers();
        // Reset drag accelerator.
        if (mSelectionModifierCursorController != null) {
            mSelectionModifierCursorController.resetTouchOffsets();
        }
        stopTextActionMode();
    }

    private int getLastTapPosition() {
        // No need to create the controller at that point, no last tap position saved
        if (mSelectionModifierCursorController != null) {
            int lastTapPosition = mSelectionModifierCursorController.getMinTouchOffset();
            if (lastTapPosition >= 0) {
                // Safety check, should not be possible.
                if (lastTapPosition > mEditText.getText().length()) {
                    lastTapPosition = mEditText.getText().length();
                }
                return lastTapPosition;
            }
        }

        return -1;
    }

    void onWindowFocusChanged(boolean hasWindowFocus) {
        if (hasWindowFocus) {
            if (mBlink != null) {
                mBlink.uncancel();
                makeBlink();
            }
            if (mEditText.hasSelection() && !extractedTextModeWillBeStarted()) {
                refreshTextActionMode();
            }
        } else {
            if (mBlink != null) {
                mBlink.cancel();
            }
            if (mInputContentType != null) {
                mInputContentType.enterDown = false;
            }
            // Order matters! Must be done before onParentLostFocus to rely on isShowingUp
            hideCursorAndSpanControllers();
            // (EW) the AOSP version didn't start stopping the text action mode here until Nougat,
            // although in Marshmallow it did start hiding the selection modifier cursor controller,
            // which is now done in in the text action mode's #onDestroyActionMode. the fixed
            // location TextActionModeFixedCallback (originally named SelectionActionModeCallback),
            // which was used prior to Marshmallow (replaced with TextActionModeCallback), shouldn't
            // be stopped because clicking its overflow menu button triggers focus changed, so if it
            // was stopped, the user could never click an overflow item.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                stopTextActionModeWithPreservingSelection();
            }
            if (mSuggestionsPopupWindow != null) {
                mSuggestionsPopupWindow.onParentLostFocus();
            }

            // Don't leave us in the middle of a batch edit. Same as in onFocusChanged
            ensureEndedBatchEdit();

            ensureNoSelectionIfNonSelectable();
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
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                // (EW) prior to Lollipop no touch events were being filtered. isButtonPressed
                // doesn't exist in those versions, so simply ignoring it should be fine
                && !event.isButtonPressed(MotionEvent.BUTTON_PRIMARY)) {
            return true;
        }
        return false;
    }

    /**
     * Handles touch events on an editable text view, implementing cursor movement, selection, etc.
     */
    void onTouchEvent(MotionEvent event) {
        final boolean filterOutEvent = shouldFilterOutTouchEvent(event);
        mLastButtonState = event.getButtonState();
        if (filterOutEvent) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                mDiscardNextActionUp = true;
            }
            return;
        }
        ViewConfiguration viewConfiguration = ViewConfiguration.get(mEditText.getContext());
        mTouchState.update(event, viewConfiguration);
        updateFloatingToolbarVisibility(event);

        if (hasInsertionController()) {
            getInsertionController().onTouchEvent(event);
        }
        if (hasSelectionController()) {
            getSelectionController().onTouchEvent(event);
        }

        if (mShowSuggestionRunnable != null) {
            mEditText.removeCallbacks(mShowSuggestionRunnable);
            mShowSuggestionRunnable = null;
        }

        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            // Reset this state; it will be re-set if super.onTouchEvent
            // causes focus to move to the view.
            mTouchFocusSelected = false;
            mIgnoreActionUpEvent = false;
        }
    }

    private void updateFloatingToolbarVisibility(MotionEvent event) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // (EW) this wasn't done prior to Marshmallow because there was a fixed, rather than
            // floating, toolbar
            return;
        }
        if (mTextActionMode != null) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    hideFloatingToolbar(ActionMode.DEFAULT_HIDE_DURATION);
                    break;
                case MotionEvent.ACTION_UP:  // fall through
                case MotionEvent.ACTION_CANCEL:
                    showFloatingToolbar();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    void hideFloatingToolbar(int duration) {
        if (mTextActionMode != null) {
            mEditText.removeCallbacks(mShowFloatingToolbar);
            mTextActionMode.hide(duration);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void showFloatingToolbar() {
        if (mTextActionMode != null) {
            // Delay "show" so it doesn't interfere with click confirmations
            // or double-clicks that could "dismiss" the floating toolbar.
            int delay = ViewConfiguration.getDoubleTapTimeout();
            mEditText.postDelayed(mShowFloatingToolbar, delay);

            // This classifies the text and most likely returns before the toolbar is actually
            // shown. If not, it will update the toolbar with the result when classification
            // returns. We would rather not wait for a long running classification process.
            invalidateActionModeAsync();
        }
    }

    private InputMethodManager getInputMethodManager() {
        return mEditText.getInputMethodManager();
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
                    ims.mChangedEnd = mEditText.getText().length();
                } else {
                    ims.mChangedStart = EXTRACT_UNKNOWN;
                    ims.mChangedEnd = EXTRACT_UNKNOWN;
                    ims.mContentChanged = false;
                }
                mUndoInputFilter.beginBatchEdit();
                mEditText.onBeginBatchEdit();
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
        mEditText.onEndBatchEdit();
        mUndoInputFilter.endBatchEdit();

        if (ims.mContentChanged || ims.mSelectionModeChanged) {
            mEditText.updateAfterEdit();
            reportExtractedText();
        } else if (ims.mCursorChanged) {
            // Cheesy way to get us to report the current cursor location.
            mEditText.invalidateCursor();
        }
        // sendUpdateSelection knows to avoid sending if the selection did
        // not actually change.
        sendUpdateSelection();

        // Show drag handles if they were blocked by batch edit mode.
        if (mTextActionMode != null) {
            final CursorController cursorController = mEditText.hasSelection()
                    ? getSelectionController() : getInsertionController();
            if (cursorController != null && !cursorController.isActive()
                    && !cursorController.isCursorBeingModified()) {
                cursorController.show();
            }
        }
    }

    /**
     * Called from {@link EditText#setText(CharSequence, boolean, int)} to
     * schedule {@link InputMethodManager#restartInput(View)}.
     */
    void scheduleRestartInputForSetText() {
        mHasPendingRestartInputForSetText = true;
    }

    /**
     * Called from {@link EditText#setText(CharSequence, boolean, int)} to
     * actually call {@link InputMethodManager#restartInput(View)} if it's scheduled.  Does nothing
     * otherwise.
     */
    void maybeFireScheduledRestartInputForSetText() {
        if (mHasPendingRestartInputForSetText) {
            final InputMethodManager imm = getInputMethodManager();
            if (imm != null) {
                imm.restartInput(mEditText);
            }
            mHasPendingRestartInputForSetText = false;
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

        final Editable content = mEditText.getText();

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
                //FUTURE: (EW) this expands the extracted text to include all of the
                // ParcelableSpans, and in the case of spellcheck being enabled, this seems to
                // result in a full extract, so this will need to be handled when adding options
                // to limit extracting text
                Object[] spans = getParcelableSpans(content, partialStartOffset, partialEndOffset);
                int i = spans.length;
                while (i > 0) {
                    i--;
                    int j = content.getSpanStart(spans[i]);
                    if (j < partialStartOffset) partialStartOffset = j;
                    j = content.getSpanEnd(spans[i]);
                    if (j > partialEndOffset) partialEndOffset = j;
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
        // (EW) the AOSP version also checked MetaKeyKeyListener#getMetaState with
        // MetaKeyKeyListener.META_SELECTING, which is hidden, to set ExtractedText.FLAG_SELECTING.
        // MetaKeyKeyListener.META_SELECTING = KeyEvent.META_SELECTING = 0x800 has been defined at
        // least since Kitkat, but it has been hidden with a comment saying it's pending API review,
        // and at least as of S, KeyEvent.META_SELECTING has been marked UnsupportedAppUsage
        // (maxTargetSdk R). after this long it seems unlikely for this to be released for apps to
        // use, and this could theoretically get changed in a future version, so it wouldn't be
        // completely safe to just hard-code 0x800. I only found this constant used in
        // getMetaState throughout AOSP code, so skipping it probably won't even cause a real lack
        // of functionality (at least currently) since other apps probably aren't using it either.
        // same basic need to skip this in EditText.ChangeWatcher#afterTextChanged,
        // ArrowKeyMovementMethod#handleMovementKey, and Touch#onTouchEvent.
        if (mEditText.isSingleLine()) {
            outText.flags |= ExtractedText.FLAG_SINGLE_LINE;
        }
        outText.startOffset = 0;
        outText.selectionStart = mEditText.getSelectionStart();
        outText.selectionEnd = mEditText.getSelectionEnd();
        // (EW) the output hint only exists since Pie
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            outText.hint = mEditText.getHint();
        }
        return true;
    }

    // (EW) wrapper for Spanned#getSpans for ParcelableSpan and related spans. see
    // EditText#isParcelableSpan.
    static Object[] getParcelableSpans(Spanned spanned, int start, int end) {
        Object[] actualParcelableSpans = spanned.getSpans(start, end, ParcelableSpan.class);
        Class<?>[] pseudoParcelableSpanTypes = new Class<?>[] {
                SpellCheckSpan.class, SuggestionRangeSpan.class
        };
        int totalSpans = actualParcelableSpans.length;
        Object[][] pseudoParcelableSpansArrays = new Object[pseudoParcelableSpanTypes.length][];
        for (int i = 0; i < pseudoParcelableSpanTypes.length; i++) {
            Class<?> type = pseudoParcelableSpanTypes[i];
            pseudoParcelableSpansArrays[i] = spanned.getSpans(start, end, type);
            totalSpans += pseudoParcelableSpansArrays[i].length;
        }
        Object[] allSpans = new Object[totalSpans];
        System.arraycopy(actualParcelableSpans, 0, allSpans, 0, actualParcelableSpans.length);
        int allSpansIndex = actualParcelableSpans.length;
        for (Object[] pseudoParcelableSpansArray : pseudoParcelableSpansArrays) {
            System.arraycopy(pseudoParcelableSpansArray, 0, allSpans, allSpansIndex,
                    pseudoParcelableSpansArray.length);
            allSpansIndex += pseudoParcelableSpansArray.length;
        }
        return allSpans;
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

            imm.updateExtractedText(mEditText, req.token, ims.mExtractedText);
            ims.mChangedStart = EXTRACT_UNKNOWN;
            ims.mChangedEnd = EXTRACT_UNKNOWN;
            ims.mChangedDelta = 0;
            ims.mContentChanged = false;
            return true;
        }
        return false;
    }

    private void sendUpdateSelection() {
        if (null != mInputMethodState && mInputMethodState.mBatchEditNesting <= 0
                && !mHasPendingRestartInputForSetText) {
            final InputMethodManager imm = getInputMethodManager();
            if (null != imm) {
                final int selectionStart = mEditText.getSelectionStart();
                final int selectionEnd = mEditText.getSelectionEnd();
                final Spannable sp = mEditText.getText();
                int candStart = EditableInputConnection.getComposingSpanStart(sp);
                int candEnd = EditableInputConnection.getComposingSpanEnd(sp);
                // InputMethodManager#updateSelection skips sending the message if
                // none of the parameters have changed since the last time we called it.
                imm.updateSelection(mEditText,
                        selectionStart, selectionEnd, candStart, candEnd);
            }
        }
    }

    void onDraw(Canvas canvas, Layout layout, Path highlight, Paint highlightPaint,
                int cursorOffsetVertical) {
        final int selectionStart = mEditText.getSelectionStart();
        final int selectionEnd = mEditText.getSelectionEnd();

        final InputMethodState ims = mInputMethodState;
        if (ims != null && ims.mBatchEditNesting == 0) {
            InputMethodManager imm = getInputMethodManager();
            if (imm != null) {
                if (imm.isActive(mEditText)) {
                    if (ims.mContentChanged || ims.mSelectionModeChanged) {
                        // We are in extract mode and the content has changed
                        // in some way... just report complete new text to the
                        // input method.
                        reportExtractedText();
                    }
                }

                // (EW) InputMethodState#updateCursor was only called prior to Lollipop and was
                // replaced with InputMethodState#updateCursorAnchorInfo in later versions (handled
                // elsewhere)
                //FUTURE: (EW) rather than just comparing versions, this might be a decent thing as a
                // config option for testing (call updateCursor instead of updateCursorAnchorInfo on
                // more recent version or maybe both)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
                        && imm.isWatchingCursor(mEditText) && highlight != null) {
                    highlight.computeBounds(ims.mTmpRectF, true);
                    ims.mTmpOffset[0] = ims.mTmpOffset[1] = 0;

                    canvas.getMatrix().mapPoints(ims.mTmpOffset);
                    ims.mTmpRectF.offset(ims.mTmpOffset[0], ims.mTmpOffset[1]);

                    ims.mTmpRectF.offset(0, cursorOffsetVertical);

                    ims.mCursorRectInWindow.set((int)(ims.mTmpRectF.left + 0.5),
                            (int)(ims.mTmpRectF.top + 0.5),
                            (int)(ims.mTmpRectF.right + 0.5),
                            (int)(ims.mTmpRectF.bottom + 0.5));

                    imm.updateCursor(mEditText,
                            ims.mCursorRectInWindow.left, ims.mCursorRectInWindow.top,
                            ims.mCursorRectInWindow.right, ims.mCursorRectInWindow.bottom);
                }
            }
        }

        if (mCorrectionHighlighter != null) {
            mCorrectionHighlighter.draw(canvas, cursorOffsetVertical);
        }

        if (highlight != null && selectionStart == selectionEnd && mDrawableForCursor != null) {
            drawCursor(canvas, cursorOffsetVertical);
            // Rely on the drawable entirely, do not draw the cursor line.
            // Has to be done after the IMM related code above which relies on the highlight.
            highlight = null;
        }

        layout.draw(canvas, highlight, highlightPaint, cursorOffsetVertical);
    }

    private void drawCursor(Canvas canvas, int cursorOffsetVertical) {
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

    void updateCursorPosition() {
        loadCursorDrawable();
        if (mDrawableForCursor == null) {
            Log.e(TAG, "updateCursorPosition: no drawable for cursor");
            return;
        }

        final Layout layout = mEditText.getLayout();
        final int offset = mEditText.getSelectionStart();
        final int line = layout.getLineForOffset(offset);
        final int top = layout.getLineTop(line);
        final int bottom = HiddenLayout.getLineBottomWithoutSpacing(layout, line);

        final boolean clamped = HiddenLayout.shouldClampCursor(layout, line);
        updateCursorPosition(top, bottom,
                HiddenLayout.getPrimaryHorizontal(layout, mEditText.getTextDir(), offset, clamped));
    }

    void refreshTextActionMode() {
        if (extractedTextModeWillBeStarted()) {
            mRestartActionModeOnNextRefresh = false;
            return;
        }
        final boolean hasSelection = mEditText.hasSelection();
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
                    startSelectionActionModeAsync();
                }
            } else if (selectionController == null || !selectionController.isActive()) {
                // Insertion action mode is active. Avoid dismissing the selection.
                stopTextActionModeWithPreservingSelection();
                startSelectionActionModeAsync();
            } else {
                // (EW) nothing needs to be done prior to M because the copy/paste/etc popup was in
                // a fixed position and this function only makes sense for dynamic positioning
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mTextActionMode.invalidateContentRect();
                }
            }
        } else {
            // Insertion action mode is started only when insertion controller is explicitly
            // activated.
            if (insertionController == null || !insertionController.isActive()) {
                stopTextActionMode();
            } else if (mTextActionMode != null) {
                // (EW) nothing needs to be done prior to M because the copy/paste/etc popup was in
                // a fixed position and this function only makes sense for dynamic positioning
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
        if (mInsertionActionModeRunnable != null) {
            mEditText.removeCallbacks(mInsertionActionModeRunnable);
        }
        if (extractedTextModeWillBeStarted()) {
            return;
        }
        stopTextActionMode();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Callback actionModeCallback =
                    new TextActionModeCallback(TextActionMode.INSERTION);
            mTextActionMode = mEditText.startActionMode(
                    actionModeCallback, ActionMode.TYPE_FLOATING);
        } else {
            ActionMode.Callback actionModeCallback =
                    new TextActionModeFixedCallback(TextActionMode.INSERTION);
            mTextActionMode = mEditText.startActionMode(actionModeCallback);
        }
        if (mTextActionMode != null && getInsertionController() != null) {
            getInsertionController().show();
        }
    }

    @NonNull
    EditText getEditText() {
        return mEditText;
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
    void startSelectionActionModeAsync() {
        getSelectionActionModeHelper().startSelectionActionModeAsync();
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
        }
        return mSelectionActionModeHelper;
    }

    /**
     * If the EditText allows text selection, selects the current word when no existing selection
     * was available and starts a drag.
     *
     * @return true if the drag was started.
     */
    private boolean selectCurrentWordAndStartDrag() {
        if (mInsertionActionModeRunnable != null) {
            mEditText.removeCallbacks(mInsertionActionModeRunnable);
        }
        if (extractedTextModeWillBeStarted()) {
            return false;
        }
        if (!checkField()) {
            return false;
        }
        if (!mEditText.hasSelection() && !selectCurrentWord()) {
            // No selection and cannot select a word.
            return false;
        }
        stopTextActionModeWithPreservingSelection();
        getSelectionController().enterDrag(
                SelectionModifierCursorController.DRAG_ACCELERATOR_MODE_WORD);
        return true;
    }

    /**
     * Checks whether a selection can be performed on the current EditText.
     *
     * @return true if a selection can be performed
     */
    boolean checkField() {
        if (!mEditText.canSelectText() || !mEditText.requestFocus()) {
            Log.w(EditText.LOG_TAG,
                    "EditText does not support text selection. Selection cancelled.");
            return false;
        }
        return true;
    }

    boolean startActionModeInternal(@TextActionMode int actionMode) {
        if (extractedTextModeWillBeStarted()) {
            return false;
        }
        if (mTextActionMode != null) {
            // Text action mode is already started
            invalidateActionMode();
            return false;
        }

        if (!checkField() || !mEditText.hasSelection()) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Callback actionModeCallback = new TextActionModeCallback(actionMode);
            mTextActionMode =
                    mEditText.startActionMode(actionModeCallback, ActionMode.TYPE_FLOATING);
        } else {
            ActionMode.Callback actionModeCallback = new TextActionModeFixedCallback(actionMode);
            mTextActionMode = mEditText.startActionMode(actionModeCallback);
        }

        final boolean selectionStarted = mTextActionMode != null;
        if (selectionStarted && mEditText.isTextEditable() && mShowSoftInputOnFocus) {
            // Show the IME to be able to replace text, except when selecting non editable text.
            final InputMethodManager imm = getInputMethodManager();
            if (imm != null) {
                imm.showSoftInput(mEditText, 0, null);
            }
        }
        return selectionStarted;
    }

    private boolean extractedTextModeWillBeStarted() {
        if (!(mEditText.isInExtractedMode())) {
            final InputMethodManager imm = getInputMethodManager();
            return  imm != null && imm.isFullscreenMode();
        }
        return false;
    }

    // (EW) needed prior to Marshmallow for ActionPopupWindow
    /**
     * @return <code>true</code> if the cursor/current selection overlaps a {@link SuggestionSpan}.
     */
    private boolean isCursorInsideSuggestionSpan() {
        Spannable text = mEditText.getText();

        SuggestionSpan[] suggestionSpans = text.getSpans(
                mEditText.getSelectionStart(), mEditText.getSelectionEnd(), SuggestionSpan.class);
        return (suggestionSpans.length > 0);
    }

    /**
     * @return <code>true</code> if it's reasonable to offer to show suggestions depending on
     * the current cursor position or selection range. This method is consistent with the
     * method to show suggestions {@link SuggestionsPopupWindow#updateSuggestions}.
     */
    private boolean shouldOfferToShowSuggestions() {
        final Spannable spannable = mEditText.getText();
        final int selectionStart = mEditText.getSelectionStart();
        final int selectionEnd = mEditText.getSelectionEnd();
        final SuggestionSpan[] suggestionSpans = spannable.getSpans(selectionStart, selectionEnd,
                SuggestionSpan.class);
        if (suggestionSpans.length == 0) {
            return false;
        }
        if (selectionStart == selectionEnd) {
            // Spans overlap the cursor.
            for (int i = 0; i < suggestionSpans.length; i++) {
                if (suggestionSpans[i].getSuggestions().length > 0) {
                    return true;
                }
            }
            return false;
        }
        int minSpanStart = spannable.length();
        int maxSpanEnd = 0;
        int unionOfSpansCoveringSelectionStartStart = spannable.length();
        int unionOfSpansCoveringSelectionStartEnd = 0;
        boolean hasValidSuggestions = false;
        for (int i = 0; i < suggestionSpans.length; i++) {
            final int spanStart = spannable.getSpanStart(suggestionSpans[i]);
            final int spanEnd = spannable.getSpanEnd(suggestionSpans[i]);
            minSpanStart = Math.min(minSpanStart, spanStart);
            maxSpanEnd = Math.max(maxSpanEnd, spanEnd);
            if (selectionStart < spanStart || selectionStart > spanEnd) {
                // The span doesn't cover the current selection start point.
                continue;
            }
            hasValidSuggestions =
                    hasValidSuggestions || suggestionSpans[i].getSuggestions().length > 0;
            unionOfSpansCoveringSelectionStartStart =
                    Math.min(unionOfSpansCoveringSelectionStartStart, spanStart);
            unionOfSpansCoveringSelectionStartEnd =
                    Math.max(unionOfSpansCoveringSelectionStartEnd, spanEnd);
        }
        if (!hasValidSuggestions) {
            return false;
        }
        if (unionOfSpansCoveringSelectionStartStart >= unionOfSpansCoveringSelectionStartEnd) {
            // No spans cover the selection start point.
            return false;
        }
        if (minSpanStart < unionOfSpansCoveringSelectionStartStart
                || maxSpanEnd > unionOfSpansCoveringSelectionStartEnd) {
            // There is a span that is not covered by the union. In this case, we shouldn't offer
            // to show suggestions as it's confusing.
            return false;
        }
        return true;
    }

    /**
     * @return <code>true</code> if the cursor is inside an {@link SuggestionSpan} with
     * {@link SuggestionSpan#FLAG_EASY_CORRECT} set.
     */
    private boolean isCursorInsideEasyCorrectionSpan() {
        Spannable spannable = mEditText.getText();
        SuggestionSpan[] suggestionSpans = spannable.getSpans(mEditText.getSelectionStart(),
                mEditText.getSelectionEnd(), SuggestionSpan.class);
        for (int i = 0; i < suggestionSpans.length; i++) {
            if ((suggestionSpans[i].getFlags() & SuggestionSpan.FLAG_EASY_CORRECT) != 0) {
                return true;
            }
        }
        return false;
    }

    void onTouchUpEvent(MotionEvent event) {
        if (EditText.DEBUG_CURSOR) {
            logCursor("onTouchUpEvent", null);
        }
        if (getSelectionActionModeHelper().resetSelection(
                mEditText.getOffsetForPosition(event.getX(), event.getY()))) {
            return;
        }

        boolean selectAllGotFocus = mSelectAllOnFocus && mEditText.didTouchFocusSelect();
        hideCursorAndSpanControllers();
        stopTextActionMode();
        Spannable text = mEditText.getText();
        if (!selectAllGotFocus && text.length() > 0) {
            // Move cursor
            final int offset = mEditText.getOffsetForPosition(event.getX(), event.getY());

            Selection.setSelection(text, offset);
            if (mSpellChecker != null) {
                // When the cursor moves, the word that was typed may need spell check
                mSpellChecker.onSelectionChanged();
            }

            if (!extractedTextModeWillBeStarted()) {
                if (isCursorInsideEasyCorrectionSpan()) {
                    // Cancel the single tap delayed runnable.
                    if (mInsertionActionModeRunnable != null) {
                        mEditText.removeCallbacks(mInsertionActionModeRunnable);
                    }

                    mShowSuggestionRunnable = this::replace;

                    // removeCallbacks is performed on every touch
                    mEditText.postDelayed(mShowSuggestionRunnable,
                            ViewConfiguration.getDoubleTapTimeout());
                } else if (hasInsertionController()) {
                    getInsertionController().show();
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

    /** Returns the controller for the insertion cursor. */
    private @Nullable InsertionPointCursorController getInsertionController() {
        if (!mInsertionControllerEnabled) {
            return null;
        }

        if (mInsertionPointCursorController == null) {
            mInsertionPointCursorController = new InsertionPointCursorController();

            final ViewTreeObserver observer = mEditText.getViewTreeObserver();
            observer.addOnTouchModeChangeListener(mInsertionPointCursorController);
        }

        return mInsertionPointCursorController;
    }

    /** Returns the controller for selection. */
    @Nullable SelectionModifierCursorController getSelectionController() {
        if (!mSelectionControllerEnabled) {
            return null;
        }

        if (mSelectionModifierCursorController == null) {
            mSelectionModifierCursorController = new SelectionModifierCursorController();

            final ViewTreeObserver observer = mEditText.getViewTreeObserver();
            observer.addOnTouchModeChangeListener(mSelectionModifierCursorController);
        }

        return mSelectionModifierCursorController;
    }

    private void updateCursorPosition(int top, int bottom, float horizontal) {
        loadCursorDrawable();
        final int left = clampHorizontalPosition(mDrawableForCursor, horizontal);
        final int width = mDrawableForCursor.getIntrinsicWidth();
        if (EditText.DEBUG_CURSOR) {
            logCursor("updateCursorPosition", "left=%s, top=%s", left, (top - mTempRect.top));
        }
        mDrawableForCursor.setBounds(left, top - mTempRect.top, left + width,
                bottom + mTempRect.bottom);
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

        int scrollX = mEditText.getScrollX();
        float horizontalDiff = horizontal - scrollX;
        int viewClippedWidth = mEditText.getWidth() - mEditText.getCompoundPaddingLeft()
                - mEditText.getCompoundPaddingRight();

        final int left;
        if (horizontalDiff >= (viewClippedWidth - 1f)) {
            // at the rightmost position
            left = viewClippedWidth + scrollX - (drawableWidth - mTempRect.right);
        } else if (Math.abs(horizontalDiff) <= 1f
                || (TextUtils.isEmpty(mEditText.getText())
                        && (EditText.VERY_WIDE - scrollX) <= (viewClippedWidth + 1f)
                        && horizontal <= 1f)) {
            // at the leftmost position
            left = scrollX - mTempRect.left;
        } else {
            left = (int) horizontal - mTempRect.left;
        }
        return left;
    }

    /**
     * Called by the framework in response to a text auto-correction (such as fixing a typo using a
     * a dictionary) from the current input method, provided by it calling
     * {@link InputConnection#commitCorrection} InputConnection.commitCorrection()}. The default
     * implementation flashes the background of the corrected word to provide feedback to the user.
     *
     * @param info The auto correct info about the text that was corrected.
     */
    public void onCommitCorrection(CorrectionInfo info) {
        if (mCorrectionHighlighter == null) {
            mCorrectionHighlighter = new CorrectionHighlighter();
        } else {
            mCorrectionHighlighter.invalidate(false);
        }

        mCorrectionHighlighter.highlight(info);
        mUndoInputFilter.freezeLastEdit();
    }

    void onScrollChanged() {
        if (mPositionListener != null) {
            mPositionListener.onScrollChanged();
        }
        if (mTextActionMode != null) {
            // (EW) nothing needs to be done prior to M because the copy/paste/etc popup was in
            // a fixed position and this function only makes sense for dynamic positioning
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mTextActionMode.invalidateContentRect();
            }
        }
    }

    /**
     * @return True when the EditText isFocused and has a valid zero-length selection (cursor).
     */
    private boolean shouldBlink() {
        if (!isCursorVisible() || !mEditText.isFocused()) return false;

        final int start = mEditText.getSelectionStart();
        if (start < 0) return false;

        final int end = mEditText.getSelectionEnd();
        if (end < 0) return false;

        return start == end;
    }

    void makeBlink() {
        if (shouldBlink()) {
            mShowCursor = SystemClock.uptimeMillis();
            if (mBlink == null) mBlink = new Blink();
            mEditText.removeCallbacks(mBlink);
            mEditText.postDelayed(mBlink, BLINK);
        } else {
            if (mBlink != null) mEditText.removeCallbacks(mBlink);
        }
    }

    private class Blink implements Runnable {
        private boolean mCancelled;

        public void run() {
            if (mCancelled) {
                return;
            }

            mEditText.removeCallbacks(this);

            if (shouldBlink()) {
                if (mEditText.getLayout() != null) {
                    mEditText.invalidateCursorPath();
                }

                mEditText.postDelayed(this, BLINK);
            }
        }

        void cancel() {
            if (!mCancelled) {
                mEditText.removeCallbacks(this);
                mCancelled = true;
            }
        }

        void uncancel() {
            mCancelled = false;
        }
    }

    private DragShadowBuilder getTextThumbnailBuilder(int start, int end) {
        android.widget.TextView shadowView =
                (android.widget.TextView) View.inflate(mEditText.getContext(),
                        R.layout.text_drag_thumbnail, null);

        if (shadowView == null) {
            throw new IllegalArgumentException("Unable to inflate text drag thumbnail");
        }

        if (end - start > DRAG_SHADOW_MAX_TEXT_LENGTH) {
            final long range = getCharClusterRange(start + DRAG_SHADOW_MAX_TEXT_LENGTH);
            end = HiddenTextUtils.unpackRangeEndFromLong(range);
        }
        final CharSequence text = mEditText.getTransformedText(start, end);
        shadowView.setText(text);
        shadowView.setTextColor(mEditText.getTextColors());

        // (EW) the AOSP version just passed R.styleable.Theme_textAppearanceLarge into
        // setTextAppearance, but that contains a reference to a styleable, rather than actually
        // being the styleable. Android Studio shows the error "Expected resource of type style" due
        // to this. setting android:textAppearanceLarge in the theme didn't have any impact, so this
        // seems like it was just a bug, so I fixed it to actually look up the styleable.
        TypedArray a = mEditText.getContext().obtainStyledAttributes(new int[]{
                android.R.attr.textAppearanceLarge
        });
        int textAppearanceLarge = a.getResourceId(0, 0);
        a.recycle();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            shadowView.setTextAppearance(textAppearanceLarge);
        } else {
            shadowView.setTextAppearance(mEditText.getContext(), textAppearanceLarge);
        }
        shadowView.setGravity(Gravity.CENTER);

        shadowView.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));

        final int size = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        shadowView.measure(size, size);

        shadowView.layout(0, 0, shadowView.getMeasuredWidth(), shadowView.getMeasuredHeight());
        shadowView.invalidate();
        return new DragShadowBuilder(shadowView);
    }

    private static class DragLocalState {
        public EditText sourceEditText;
        public int start, end;

        public DragLocalState(EditText sourceEditText, int start, int end) {
            this.sourceEditText = sourceEditText;
            this.start = start;
            this.end = end;
        }
    }

    void onDrop(DragEvent event) {
        final int offset = mEditText.getOffsetForPosition(event.getX(), event.getY());
        Object localState = event.getLocalState();
        DragLocalState dragLocalState = null;
        if (localState instanceof DragLocalState) {
            dragLocalState = (DragLocalState) localState;
        }
        boolean dragDropIntoItself = dragLocalState != null
                && dragLocalState.sourceEditText == mEditText;
        if (dragDropIntoItself) {
            if (offset >= dragLocalState.start && offset < dragLocalState.end) {
                // A drop inside the original selection discards the drop.
                return;
            }
        }

        // (EW) the AOSP version called DragAndDropPermissions.obtain to get the permissions and
        // then called takeTransient on it, but apps don't have access to those, so we have to look
        // up the activity and get it from that.
        final DragAndDropPermissions permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Activity activity = mEditText.getActivity();
            if (activity != null) {
                permissions = activity.requestDragAndDropPermissions(event);
            } else {
                permissions = null;
            }
        } else {
            permissions = null;
        }

        mEditText.beginBatchEdit();
        mUndoInputFilter.freezeLastEdit();
        try {
            final int originalLength = mEditText.getText().length();
            Selection.setSelection(mEditText.getText(), offset);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                final ClipData clip = event.getClipData();
                // (EW) the AOSP version called setDragAndDropPermissions on the builder. it seems
                // to work fine without it. maybe that's only necessary because of how they're
                // getting the permissions
                final ContentInfo payload = new ContentInfo.Builder(clip, SOURCE_DRAG_AND_DROP)
                        .build();
                mEditText.performReceiveContent(payload);
            } else {
                SpannableStringBuilder content = new SpannableStringBuilder();
                ClipData clipData = event.getClipData();
                final int itemCount = clipData.getItemCount();
                for (int i = 0; i < itemCount; i++) {
                    Item item = clipData.getItemAt(i);
                    content.append(item.coerceToStyledText(mEditText.getContext()));
                }
                int min = offset;
                int max = offset;
                mEditText.replaceText_internal(min, max, content);
            }

            if (dragDropIntoItself) {
                deleteSourceAfterLocalDrop(dragLocalState, offset, originalLength);
            }
        } finally {
            mEditText.endBatchEdit();
            mUndoInputFilter.freezeLastEdit();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && permissions != null) {
                permissions.release();
            }
        }
    }

    private void deleteSourceAfterLocalDrop(@NonNull DragLocalState dragLocalState, int dropOffset,
                                            int lengthBeforeDrop) {
        int dragSourceStart = dragLocalState.start;
        int dragSourceEnd = dragLocalState.end;
        if (dropOffset <= dragSourceStart) {
            // Inserting text before selection has shifted positions
            final int shift = mEditText.getText().length() - lengthBeforeDrop;
            dragSourceStart += shift;
            dragSourceEnd += shift;
        }

        // Delete original selection
        mEditText.deleteText_internal(dragSourceStart, dragSourceEnd);

        // Make sure we do not leave two adjacent spaces.
        final int prevCharIdx = Math.max(0, dragSourceStart - 1);
        final int nextCharIdx = Math.min(mEditText.getText().length(), dragSourceStart + 1);
        if (nextCharIdx > prevCharIdx + 1) {
            CharSequence t = mEditText.getTransformedText(prevCharIdx, nextCharIdx);
            if (Character.isSpaceChar(t.charAt(0)) && Character.isSpaceChar(t.charAt(1))) {
                mEditText.deleteText_internal(prevCharIdx, prevCharIdx + 1);
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

    void setContextMenuAnchor(float x, float y) {
        mContextMenuAnchorX = x;
        mContextMenuAnchorY = y;
    }

    void onCreateContextMenu(ContextMenu menu) {
        if (mIsBeingLongClicked || Float.isNaN(mContextMenuAnchorX)
                || Float.isNaN(mContextMenuAnchorY)) {
            return;
        }
        final int offset = mEditText.getOffsetForPosition(mContextMenuAnchorX, mContextMenuAnchorY);
        if (offset == -1) {
            return;
        }
        stopTextActionModeWithPreservingSelection();
        if (mEditText.canSelectText()) {
            final boolean isOnSelection = mEditText.hasSelection()
                    && offset >= mEditText.getSelectionStart()
                    && offset <= mEditText.getSelectionEnd();
            if (!isOnSelection) {
                // Right clicked position is not on the selection. Remove the selection and move the
                // cursor to the right clicked position.
                Selection.setSelection(mEditText.getText(), offset);
                stopTextActionMode();
            }
        }
        if (shouldOfferToShowSuggestions()) {
            final SuggestionInfo[] suggestionInfoArray =
                    new SuggestionInfo[SuggestionSpan.SUGGESTIONS_MAX_SIZE];
            for (int i = 0; i < suggestionInfoArray.length; i++) {
                suggestionInfoArray[i] = new SuggestionInfo();
            }
            final SubMenu subMenu = menu.addSubMenu(Menu.NONE, Menu.NONE, MENU_ITEM_ORDER_REPLACE,
                    R.string.replace);
            final int numItems = mSuggestionHelper.getSuggestionInfo(suggestionInfoArray, null);
            for (int i = 0; i < numItems; i++) {
                final SuggestionInfo info = suggestionInfoArray[i];
                subMenu.add(Menu.NONE, Menu.NONE, i, info.mText)
                        .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem item) {
                                replaceWithSuggestion(info);
                                return true;
                            }
                        });
            }
        }
        menu.add(Menu.NONE, EditText.ID_UNDO, MENU_ITEM_ORDER_UNDO,
                R.string.undo)
                .setAlphabeticShortcut('z')
                .setOnMenuItemClickListener(mOnContextMenuItemClickListener)
                .setEnabled(mEditText.canUndo());
        menu.add(Menu.NONE, EditText.ID_REDO, MENU_ITEM_ORDER_REDO,
                R.string.redo)
                .setOnMenuItemClickListener(mOnContextMenuItemClickListener)
                .setEnabled(mEditText.canRedo());
        menu.add(Menu.NONE, EditText.ID_CUT, MENU_ITEM_ORDER_CUT,
                android.R.string.cut)
                .setAlphabeticShortcut('x')
                .setOnMenuItemClickListener(mOnContextMenuItemClickListener)
                .setEnabled(mEditText.canCut());
        menu.add(Menu.NONE, EditText.ID_COPY, MENU_ITEM_ORDER_COPY,
                android.R.string.copy)
                .setAlphabeticShortcut('c')
                .setOnMenuItemClickListener(mOnContextMenuItemClickListener)
                .setEnabled(mEditText.canCopy());
        menu.add(Menu.NONE, EditText.ID_PASTE, MENU_ITEM_ORDER_PASTE,
                android.R.string.paste)
                .setAlphabeticShortcut('v')
                .setEnabled(mEditText.canPaste())
                .setOnMenuItemClickListener(mOnContextMenuItemClickListener);
        menu.add(Menu.NONE, EditText.ID_PASTE_AS_PLAIN_TEXT, MENU_ITEM_ORDER_PASTE_AS_PLAIN_TEXT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? android.R.string.paste_as_plain_text
                        : R.string.paste_as_plain_text)
                .setEnabled(mEditText.canPasteAsPlainText())
                .setOnMenuItemClickListener(mOnContextMenuItemClickListener);
        menu.add(Menu.NONE, EditText.ID_SHARE, MENU_ITEM_ORDER_SHARE,
                R.string.share)
                .setEnabled(mEditText.canShare())
                .setOnMenuItemClickListener(mOnContextMenuItemClickListener);
        menu.add(Menu.NONE, EditText.ID_SELECT_ALL, MENU_ITEM_ORDER_SELECT_ALL,
                android.R.string.selectAll)
                .setAlphabeticShortcut('a')
                .setEnabled(mEditText.canSelectAllText())
                .setOnMenuItemClickListener(mOnContextMenuItemClickListener);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            menu.add(Menu.NONE, EditText.ID_AUTOFILL, MENU_ITEM_ORDER_AUTOFILL,
                    R.string.autofill)
                    .setEnabled(mEditText.canRequestAutofill())
                    .setOnMenuItemClickListener(mOnContextMenuItemClickListener);
        }
        mPreserveSelection = true;
    }

    @Nullable
    private SuggestionSpan findEquivalentSuggestionSpan(
            @NonNull SuggestionSpanInfo suggestionSpanInfo) {
        final Editable editable = mEditText.getText();
        if (editable.getSpanStart(suggestionSpanInfo.mSuggestionSpan) >= 0) {
            // Exactly same span is found.
            return suggestionSpanInfo.mSuggestionSpan;
        }
        // Suggestion span couldn't be found. Try to find a suggestion span that has the same
        // contents.
        final SuggestionSpan[] suggestionSpans = editable.getSpans(suggestionSpanInfo.mSpanStart,
                suggestionSpanInfo.mSpanEnd, SuggestionSpan.class);
        for (final SuggestionSpan suggestionSpan : suggestionSpans) {
            final int start = editable.getSpanStart(suggestionSpan);
            if (start != suggestionSpanInfo.mSpanStart) {
                continue;
            }
            final int end = editable.getSpanEnd(suggestionSpan);
            if (end != suggestionSpanInfo.mSpanEnd) {
                continue;
            }
            if (suggestionSpan.equals(suggestionSpanInfo.mSuggestionSpan)) {
                return suggestionSpan;
            }
        }
        return null;
    }

    private void replaceWithSuggestion(@NonNull final SuggestionInfo suggestionInfo) {
        final SuggestionSpan targetSuggestionSpan = findEquivalentSuggestionSpan(
                suggestionInfo.mSuggestionSpanInfo);
        if (targetSuggestionSpan == null) {
            // Span has been removed
            return;
        }
        final Editable editable = mEditText.getText();
        final int spanStart = editable.getSpanStart(targetSuggestionSpan);
        final int spanEnd = editable.getSpanEnd(targetSuggestionSpan);
        if (spanStart < 0 || spanEnd <= spanStart) {
            // Span has been removed
            return;
        }

        final String originalText = TextUtils.substring(editable, spanStart, spanEnd);
        // SuggestionSpans are removed by replace: save them before
        SuggestionSpan[] suggestionSpans = editable.getSpans(spanStart, spanEnd,
                SuggestionSpan.class);
        final int length = suggestionSpans.length;
        int[] suggestionSpansStarts = new int[length];
        int[] suggestionSpansEnds = new int[length];
        int[] suggestionSpansFlags = new int[length];
        for (int i = 0; i < length; i++) {
            final SuggestionSpan suggestionSpan = suggestionSpans[i];
            suggestionSpansStarts[i] = editable.getSpanStart(suggestionSpan);
            suggestionSpansEnds[i] = editable.getSpanEnd(suggestionSpan);
            suggestionSpansFlags[i] = editable.getSpanFlags(suggestionSpan);

            // Remove potential misspelled flags
            int suggestionSpanFlags = suggestionSpan.getFlags();
            if ((suggestionSpanFlags & FLAG_MISSPELLED_OR_GRAMMAR_ERROR) != 0) {
                suggestionSpanFlags &= ~SuggestionSpan.FLAG_MISSPELLED;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    suggestionSpanFlags &= ~SuggestionSpan.FLAG_GRAMMAR_ERROR;
                }
                suggestionSpanFlags &= ~SuggestionSpan.FLAG_EASY_CORRECT;
                suggestionSpan.setFlags(suggestionSpanFlags);
            }
        }

        // (EW) the AOSP version called SuggestionSpan#notifySelection, which called
        // InputMethodManager#notifySuggestionPicked, which is hidden. I'm not sure why that isn't
        // accessible, but it seems there is nothing we can do to replicate that functionality. This
        // call was to notify the source IME of the suggestion pick and was to be done before
        // swapping texts. This stopped being called in version 29 (Q), and
        // SuggestionSpan#notifySelection was deprecated and does nothing. Based on that, it's
        // probably fine to skip on the older versions too. Maybe it was some sort of internal
        // prototype feature that was going to be added for apps to use but ended up getting
        // dropped.

        // Swap text content between actual text and Suggestion span
        final int suggestionStart = suggestionInfo.mSuggestionStart;
        final int suggestionEnd = suggestionInfo.mSuggestionEnd;
        final String suggestion = suggestionInfo.mText.subSequence(
                suggestionStart, suggestionEnd).toString();
        mEditText.replaceText_internal(spanStart, spanEnd, suggestion);

        String[] suggestions = targetSuggestionSpan.getSuggestions();
        suggestions[suggestionInfo.mSuggestionIndex] = originalText;

        // Restore previous SuggestionSpans
        final int lengthDelta = suggestion.length() - (spanEnd - spanStart);
        for (int i = 0; i < length; i++) {
            // Only spans that include the modified region make sense after replacement
            // Spans partially included in the replaced region are removed, there is no
            // way to assign them a valid range after replacement
            if (suggestionSpansStarts[i] <= spanStart && suggestionSpansEnds[i] >= spanEnd) {
                mEditText.setSpan_internal(suggestionSpans[i], suggestionSpansStarts[i],
                        suggestionSpansEnds[i] + lengthDelta, suggestionSpansFlags[i]);
            }
        }
        // Move cursor at the end of the replaced word
        final int newCursorPosition = spanEnd + lengthDelta;
        mEditText.setCursorPosition_internal(newCursorPosition, newCursorPosition);
    }

    private final MenuItem.OnMenuItemClickListener mOnContextMenuItemClickListener =
            new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    // (EW) the AOSP version didn't actually do anything with the view's context
                    // menu prior to Nougat, so there wasn't matching functionality to, and the
                    // PROCESS_TEXT intent wasn't added until Marshmallow
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            && mProcessTextIntentActionsHandler.performMenuItemAction(item)) {
                        return true;
                    }
                    return mEditText.onTextContextMenuItem(item.getItemId());
                }
            };

    /**
     * Controls the {@link EasyEditSpan} monitoring when it is added, and when the related
     * pop-up should be displayed.
     * Also monitors {@link Selection} to call back to the attached input method.
     */
    private class SpanController implements SpanWatcher {

        private static final int DISPLAY_TIMEOUT_MS = 3000; // 3 secs

        private EasyEditPopupWindow mPopupWindow;

        private Runnable mHidePopup;

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
                if (mPopupWindow == null) {
                    mPopupWindow = new EasyEditPopupWindow();
                    mHidePopup = new Runnable() {
                        @Override
                        public void run() {
                            hide();
                        }
                    };
                }

                // Make sure there is only at most one EasyEditSpan in the text
                if (mPopupWindow.mEasyEditSpan != null) {
                    // (EW) starting in Jelly Bean MR2, the AOSP version called
                    // EasyEditSpan#setDeleteEnabled with false on the EasyEditSpan, which is not
                    // accessible by apps. as far as I can tell, this function is only used here.
                    // prior to that version, it just removed the span. this seems to have been
                    // changed in order to still be able to send the notification that the span
                    // changed, but we can't send the notification (see comment in
                    // #sendEasySpanNotification), so leaving it with the old functionality should
                    // be fine. if there ever is a way that we're allowed to send the notification,
                    // this will need to change.
                    text.removeSpan(mPopupWindow.mEasyEditSpan);
                }

                mPopupWindow.setEasyEditSpan((EasyEditSpan) span);
                mPopupWindow.setOnDeleteListener(new EasyEditDeleteListener() {
                    @Override
                    public void onDeleteClick(EasyEditSpan span) {
                        Editable editable = mEditText.getText();
                        int start = editable.getSpanStart(span);
                        int end = editable.getSpanEnd(span);
                        if (start >= 0 && end >= 0) {
                            sendEasySpanNotification(EasyEditSpan.TEXT_DELETED, span);
                            mEditText.deleteText_internal(start, end);
                        }
                        editable.removeSpan(span);
                    }
                });

                if (mEditText.getWindowVisibility() != View.VISIBLE) {
                    // The window is not visible yet, ignore the text change.
                    return;
                }

                if (mEditText.getLayout() == null) {
                    // The view has not been laid out yet, ignore the text change
                    return;
                }

                if (extractedTextModeWillBeStarted()) {
                    // The input is in extract mode. Do not handle the easy edit in
                    // the original EditText, as the ExtractEditText will do
                    return;
                }

                mPopupWindow.show();
                mEditText.removeCallbacks(mHidePopup);
                mEditText.postDelayed(mHidePopup, DISPLAY_TIMEOUT_MS);
            }
        }

        @Override
        public void onSpanRemoved(Spannable text, Object span, int start, int end) {
            if (isNonIntermediateSelectionSpan(text, span)) {
                sendUpdateSelection();
            } else if (mPopupWindow != null && span == mPopupWindow.mEasyEditSpan) {
                hide();
            }
        }

        @Override
        public void onSpanChanged(Spannable text, Object span, int previousStart, int previousEnd,
                                  int newStart, int newEnd) {
            if (isNonIntermediateSelectionSpan(text, span)) {
                sendUpdateSelection();
            } else if (mPopupWindow != null && span instanceof EasyEditSpan) {
                EasyEditSpan easyEditSpan = (EasyEditSpan) span;
                sendEasySpanNotification(EasyEditSpan.TEXT_MODIFIED, easyEditSpan);
                text.removeSpan(easyEditSpan);
            }
        }

        public void hide() {
            if (mPopupWindow != null) {
                mPopupWindow.hide();
                mEditText.removeCallbacks(mHidePopup);
            }
        }

        private void sendEasySpanNotification(int textChangedType, EasyEditSpan span) {
            //FUTURE: (EW) EasyEditSpan#getPendingIntent is hidden from apps, which seems weird since
            // setting it in the constructor can be used by apps. I'm not sure how non-framework
            // text editors are expected to be able to handle this. I'm leaving this here for
            // visibility in case there ever is a way for us to do this.
        }
    }

    /**
     * Listens for the delete event triggered by {@link EasyEditPopupWindow}.
     */
    private interface EasyEditDeleteListener {

        /**
         * Clicks the delete pop-up.
         */
        void onDeleteClick(EasyEditSpan span);
    }

    /**
     * Displays the actions associated to an {@link EasyEditSpan}. The pop-up is controlled
     * by {@link SpanController}.
     */
    private class EasyEditPopupWindow extends PinnedPopupWindow
            implements OnClickListener {
        private static final int POPUP_TEXT_LAYOUT = R.layout.text_edit_action_popup_text;
        private android.widget.TextView mDeleteTextView;
        private EasyEditSpan mEasyEditSpan;
        private EasyEditDeleteListener mOnDeleteListener;

        @Override
        protected void createPopupWindow() {
            mPopupWindow = new PopupWindow(mEditText.getContext(), null,
                    android.R.attr.textSelectHandleWindowStyle);
            mPopupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
            mPopupWindow.setClippingEnabled(true);
        }

        @Override
        protected void initContentView() {
            LinearLayout linearLayout = new LinearLayout(mEditText.getContext());
            linearLayout.setOrientation(LinearLayout.HORIZONTAL);
            mContentView = linearLayout;
            mContentView.setBackgroundResource(R.drawable.text_edit_side_paste_window);

            LayoutInflater inflater = (LayoutInflater) mEditText.getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            LayoutParams wrapContent = new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

            mDeleteTextView = (android.widget.TextView) inflater.inflate(POPUP_TEXT_LAYOUT, null);
            mDeleteTextView.setLayoutParams(wrapContent);
            mDeleteTextView.setText(R.string.delete);
            mDeleteTextView.setOnClickListener(this);
            mContentView.addView(mDeleteTextView);
        }

        public void setEasyEditSpan(EasyEditSpan easyEditSpan) {
            mEasyEditSpan = easyEditSpan;
        }

        private void setOnDeleteListener(EasyEditDeleteListener listener) {
            mOnDeleteListener = listener;
        }

        @Override
        public void onClick(View view) {
            // (EW) the AOSP version also checked EasyEditSpan#isDeleteEnabled on the EasyEditSpan,
            // which is isn't accessible by apps. as far as I can tell, setDeleteEnabled is the only
            // way to disable delete, which seems to only be called from the AOSP Editor for this,
            // so it should be relatively safe to ignore since we're just removing the span instead
            // of flagging deleting as disabled. see comment in SpanController#onSpanAdded
            if (view == mDeleteTextView
                    && mEasyEditSpan != null
                    && mOnDeleteListener != null) {
                mOnDeleteListener.onDeleteClick(mEasyEditSpan);
            }
        }

        @Override
        public void hide() {
            if (mEasyEditSpan != null) {
                // (EW) prior to Jelly Bean MR2 (where the AOSP version started calling
                // EasyEditSpan#setDeleteEnabled with false on the EasyEditSpan), this function
                // didn't exist, but it probably makes sense to match how SpanController#onSpanAdded
                // handles the alternative to EasyEditSpan#setDeleteEnabled by just removing the
                // span. see comment in SpanController#onSpanAdded.
                Editable editable = mEditText.getText();
                editable.removeSpan(mEasyEditSpan);
                mEasyEditSpan = null;
            }
            mOnDeleteListener = null;
            super.hide();
        }

        @Override
        protected int getTextOffset() {
            // Place the pop-up at the end of the span
            Editable editable = mEditText.getText();
            return editable.getSpanEnd(mEasyEditSpan);
        }

        @Override
        protected int getVerticalLocalPosition(int line) {
            final Layout layout = mEditText.getLayout();
            return HiddenLayout.getLineBottomWithoutSpacing(layout, line);
        }

        @Override
        protected int clipVertically(int positionY) {
            // As we display the pop-up below the span, no vertical clipping is required.
            return positionY;
        }
    }

    private class PositionListener implements ViewTreeObserver.OnPreDrawListener {
        // 3 handles
        // 3 ActionPopup [replace, suggestion, easyedit] (suggestionsPopup first hides the others)
        // 1 CursorAnchorInfoNotifier
        private static final int MAXIMUM_NUMBER_OF_LISTENERS = 7;
        private final EditTextPositionListener[] mPositionListeners =
                new EditTextPositionListener[MAXIMUM_NUMBER_OF_LISTENERS];
        private final boolean[] mCanMove = new boolean[MAXIMUM_NUMBER_OF_LISTENERS];
        private boolean mPositionHasChanged = true;
        // Absolute position of the EditText with respect to its parent window
        private int mPositionX, mPositionY;
        private int mPositionXOnScreen, mPositionYOnScreen;
        private int mNumberOfListeners;
        private boolean mScrollHasChanged;
        final int[] mTempCoords = new int[2];

        public void addSubscriber(EditTextPositionListener positionListener, boolean canMove) {
            if (mNumberOfListeners == 0) {
                updatePosition();
                ViewTreeObserver vto = mEditText.getViewTreeObserver();
                vto.addOnPreDrawListener(this);
            }

            int emptySlotIndex = -1;
            for (int i = 0; i < MAXIMUM_NUMBER_OF_LISTENERS; i++) {
                EditTextPositionListener listener = mPositionListeners[i];
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

        public void removeSubscriber(EditTextPositionListener positionListener) {
            for (int i = 0; i < MAXIMUM_NUMBER_OF_LISTENERS; i++) {
                if (mPositionListeners[i] == positionListener) {
                    mPositionListeners[i] = null;
                    mNumberOfListeners--;
                    break;
                }
            }

            if (mNumberOfListeners == 0) {
                ViewTreeObserver vto = mEditText.getViewTreeObserver();
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
                    EditTextPositionListener positionListener = mPositionListeners[i];
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
            mEditText.getLocationInWindow(mTempCoords);

            mPositionHasChanged = mTempCoords[0] != mPositionX || mTempCoords[1] != mPositionY;

            mPositionX = mTempCoords[0];
            mPositionY = mTempCoords[1];

            mEditText.getLocationOnScreen(mTempCoords);

            mPositionXOnScreen = mTempCoords[0];
            mPositionYOnScreen = mTempCoords[1];
        }

        public void onScrollChanged() {
            mScrollHasChanged = true;
        }
    }

    private abstract class PinnedPopupWindow implements EditTextPositionListener {
        protected PopupWindow mPopupWindow;
        protected ViewGroup mContentView;
        int mPositionX, mPositionY;
        int mClippingLimitLeft, mClippingLimitRight;

        protected abstract void createPopupWindow();
        protected abstract void initContentView();
        protected abstract int getTextOffset();
        protected abstract int getVerticalLocalPosition(int line);
        protected abstract int clipVertically(int positionY);
        protected void setUp() {
        }

        public PinnedPopupWindow() {
            // Due to calling subclass methods in base constructor, subclass constructor is not
            // called before subclass methods, e.g. createPopupWindow or initContentView. To give
            // a chance to initialize subclasses, call setUp() method here.
            // TODO: It is good to extract non trivial initialization code from constructor.
            setUp();

            createPopupWindow();

            // (EW) the AOSP version set the window layout type to
            // WindowManager.LayoutParams.TYPE_APPLICATION_ABOVE_SUB_PANEL, which is hidden from
            // apps. prior to Marshmallow, it set it to
            // WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL. since it's just an int, we
            // could hard-code the value, but that value could change, so that isn't completely
            // safe. I'm not sure what the difference is, and I haven't noticed a difference, so
            // we'll use the old functionality at least until there is a known reason that it should
            // be changed.
            setWindowLayoutType(mPopupWindow,
                    WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL);

            // (EW) the AOSP version uses WRAP_CONTENT, but for some reason that limits the max
            // width of the popup, so instead we'll initially have this match the parent to allow
            // the max content and once the width of the actual content is determined, we'll update
            // the width of the popup to match.
            mPopupWindow.setWidth(ViewGroup.LayoutParams.MATCH_PARENT);
            mPopupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);

            initContentView();

            LayoutParams wrapContent = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            mContentView.setLayoutParams(wrapContent);

            mPopupWindow.setContentView(mContentView);
        }

        public void show() {
            getPositionListener().addSubscriber(this, false /* offset is fixed */);

            computeLocalPosition();

            final PositionListener positionListener = getPositionListener();
            updatePosition(positionListener.getPositionX(), positionListener.getPositionY());
        }

        protected void measureContent() {
            final DisplayMetrics displayMetrics = mEditText.getResources().getDisplayMetrics();
            mContentView.measure(
                    View.MeasureSpec.makeMeasureSpec(displayMetrics.widthPixels,
                            View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(displayMetrics.heightPixels,
                            View.MeasureSpec.AT_MOST));
        }

        /* The popup window will be horizontally centered on the getTextOffset() and vertically
         * positioned according to viewportToContentHorizontalOffset.
         *
         * This method assumes that mContentView has properly been measured from its content. */
        private void computeLocalPosition() {
            measureContent();
            final int width = mContentView.getMeasuredWidth();

            // (EW) now that we've determined the actual width of the content, we can set the width
            // of the popup to match (see #PinnedPopupWindow)
            mPopupWindow.setWidth(width);

            final int offset = getTextOffset();
            mPositionX = (int) (mEditText.getLayout().getPrimaryHorizontal(offset) - width / 2.0f);
            mPositionX += mEditText.viewportToContentHorizontalOffset();

            final int line = mEditText.getLayout().getLineForOffset(offset);
            mPositionY = getVerticalLocalPosition(line);
            mPositionY += mEditText.viewportToContentVerticalOffset();
        }

        private void updatePosition(int parentPositionX, int parentPositionY) {
            int positionX = parentPositionX + mPositionX;
            int positionY = parentPositionY + mPositionY;

            positionY = clipVertically(positionY);

            // Horizontal clipping
            final DisplayMetrics displayMetrics = mEditText.getResources().getDisplayMetrics();
            final int width = mContentView.getMeasuredWidth();
            positionX = Math.min(
                    displayMetrics.widthPixels - width + mClippingLimitRight, positionX);
            positionX = Math.max(-mClippingLimitLeft, positionX);

            if (isShowing()) {
                mPopupWindow.update(positionX, positionY, -1, -1);
            } else {
                mPopupWindow.showAtLocation(mEditText, Gravity.NO_GRAVITY,
                        positionX, positionY);
            }
        }

        public void hide() {
            if (!isShowing()) {
                return;
            }
            mPopupWindow.dismiss();
            getPositionListener().removeSubscriber(this);
        }

        @Override
        public void updatePosition(int parentPositionX, int parentPositionY,
                                   boolean parentPositionChanged, boolean parentScrolled) {
            // Either parentPositionChanged or parentScrolled is true, check if still visible
            if (isShowing() && isOffsetVisible(getTextOffset())) {
                if (parentScrolled) computeLocalPosition();
                updatePosition(parentPositionX, parentPositionY);
            } else {
                hide();
            }
        }

        public boolean isShowing() {
            return mPopupWindow.isShowing();
        }
    }

    private static final class SuggestionInfo {
        // Range of actual suggestion within mText
        int mSuggestionStart, mSuggestionEnd;

        // The SuggestionSpan that this EditText represents
        final SuggestionSpanInfo mSuggestionSpanInfo = new SuggestionSpanInfo();

        // The index of this suggestion inside suggestionSpan
        int mSuggestionIndex;

        final SpannableStringBuilder mText = new SpannableStringBuilder();

        void clear() {
            mSuggestionSpanInfo.clear();
            mText.clear();
        }

        // Utility method to set attributes about a SuggestionSpan.
        void setSpanInfo(SuggestionSpan span, int spanStart, int spanEnd) {
            mSuggestionSpanInfo.mSuggestionSpan = span;
            mSuggestionSpanInfo.mSpanStart = spanStart;
            mSuggestionSpanInfo.mSpanEnd = spanEnd;
        }
    }

    private static final class SuggestionSpanInfo {
        // The SuggestionSpan;
        @Nullable
        SuggestionSpan mSuggestionSpan;

        // The SuggestionSpan start position
        int mSpanStart;

        // The SuggestionSpan end position
        int mSpanEnd;

        void clear() {
            mSuggestionSpan = null;
        }
    }

    private class SuggestionHelper {
        private final Comparator<SuggestionSpan> mSuggestionSpanComparator =
                new SuggestionSpanComparator();
        private final HashMap<SuggestionSpan, Integer> mSpansLengths =
                new HashMap<SuggestionSpan, Integer>();

        private class SuggestionSpanComparator implements Comparator<SuggestionSpan> {
            public int compare(SuggestionSpan span1, SuggestionSpan span2) {
                final int flag1 = span1.getFlags();
                final int flag2 = span2.getFlags();
                if (flag1 != flag2) {
                    // Compare so that the order will be: easy -> misspelled -> grammarError
                    int easy = compareFlag(SuggestionSpan.FLAG_EASY_CORRECT, flag1, flag2);
                    if (easy != 0) return easy;
                    int misspelled = compareFlag(SuggestionSpan.FLAG_MISSPELLED, flag1, flag2);
                    if (misspelled != 0) return misspelled;
                    // (EW) the AOSP version didn't do anything for grammar errors prior to S
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        int grammarError = compareFlag(SuggestionSpan.FLAG_GRAMMAR_ERROR, flag1, flag2);
                        if (grammarError != 0) return grammarError;
                    }
                }

                return mSpansLengths.get(span1).intValue() - mSpansLengths.get(span2).intValue();
            }

            /*
             * Returns -1 if flags1 has flagToCompare but flags2 does not.
             * Returns 1 if flags2 has flagToCompare but flags1 does not.
             * Otherwise, returns 0.
             */
            private int compareFlag(int flagToCompare, int flags1, int flags2) {
                boolean hasFlag1 = (flags1 & flagToCompare) != 0;
                boolean hasFlag2 = (flags2 & flagToCompare) != 0;
                if (hasFlag1 == hasFlag2) return 0;
                return hasFlag1 ? -1 : 1;
            }
        }

        /**
         * Returns the suggestion spans that cover the current cursor position. The suggestion
         * spans are sorted according to the length of text that they are attached to.
         */
        private SuggestionSpan[] getSortedSuggestionSpans() {
            int pos = mEditText.getSelectionStart();
            Spannable spannable = mEditText.getText();
            SuggestionSpan[] suggestionSpans = spannable.getSpans(pos, pos, SuggestionSpan.class);

            mSpansLengths.clear();
            for (SuggestionSpan suggestionSpan : suggestionSpans) {
                int start = spannable.getSpanStart(suggestionSpan);
                int end = spannable.getSpanEnd(suggestionSpan);
                mSpansLengths.put(suggestionSpan, end - start);
            }

            // The suggestions are sorted according to their types (easy correction first,
            // misspelled second, then grammar error) and to the length of the text that they cover
            // (shorter first).
            Arrays.sort(suggestionSpans, mSuggestionSpanComparator);
            mSpansLengths.clear();

            return suggestionSpans;
        }

        /**
         * Gets the SuggestionInfo list that contains suggestion information at the current cursor
         * position.
         *
         * @param suggestionInfos SuggestionInfo array the results will be set.
         * @param misspelledSpanInfo a struct the misspelled SuggestionSpan info will be set.
         * @return the number of suggestions actually fetched.
         */
        public int getSuggestionInfo(SuggestionInfo[] suggestionInfos,
                                     @Nullable SuggestionSpanInfo misspelledSpanInfo) {
            final Spannable spannable = mEditText.getText();
            final SuggestionSpan[] suggestionSpans = getSortedSuggestionSpans();
            final int nbSpans = suggestionSpans.length;
            if (nbSpans == 0) return 0;

            int numberOfSuggestions = 0;
            for (final SuggestionSpan suggestionSpan : suggestionSpans) {
                final int spanStart = spannable.getSpanStart(suggestionSpan);
                final int spanEnd = spannable.getSpanEnd(suggestionSpan);

                if (misspelledSpanInfo != null
                        && (suggestionSpan.getFlags() & FLAG_MISSPELLED_OR_GRAMMAR_ERROR) != 0) {
                    misspelledSpanInfo.mSuggestionSpan = suggestionSpan;
                    misspelledSpanInfo.mSpanStart = spanStart;
                    misspelledSpanInfo.mSpanEnd = spanEnd;
                }

                final String[] suggestions = suggestionSpan.getSuggestions();
                final int nbSuggestions = suggestions.length;
                suggestionLoop:
                for (int suggestionIndex = 0; suggestionIndex < nbSuggestions; suggestionIndex++) {
                    final String suggestion = suggestions[suggestionIndex];
                    for (int i = 0; i < numberOfSuggestions; i++) {
                        final SuggestionInfo otherSuggestionInfo = suggestionInfos[i];
                        if (otherSuggestionInfo.mText.toString().equals(suggestion)) {
                            final int otherSpanStart =
                                    otherSuggestionInfo.mSuggestionSpanInfo.mSpanStart;
                            final int otherSpanEnd =
                                    otherSuggestionInfo.mSuggestionSpanInfo.mSpanEnd;
                            if (spanStart == otherSpanStart && spanEnd == otherSpanEnd) {
                                continue suggestionLoop;
                            }
                        }
                    }

                    SuggestionInfo suggestionInfo = suggestionInfos[numberOfSuggestions];
                    suggestionInfo.setSpanInfo(suggestionSpan, spanStart, spanEnd);
                    suggestionInfo.mSuggestionIndex = suggestionIndex;
                    suggestionInfo.mSuggestionStart = 0;
                    suggestionInfo.mSuggestionEnd = suggestion.length();
                    suggestionInfo.mText.replace(0, suggestionInfo.mText.length(), suggestion);
                    numberOfSuggestions++;
                    if (numberOfSuggestions >= suggestionInfos.length) {
                        return numberOfSuggestions;
                    }
                }
            }
            return numberOfSuggestions;
        }
    }

    private final class SuggestionsPopupWindow extends PinnedPopupWindow
            implements OnItemClickListener {
        private static final int MAX_NUMBER_SUGGESTIONS = SuggestionSpan.SUGGESTIONS_MAX_SIZE;

        // Key of intent extras for inserting new word into user dictionary.
        private static final String USER_DICTIONARY_EXTRA_WORD = "word";
        private static final String USER_DICTIONARY_EXTRA_LOCALE = "locale";

        private SuggestionInfo[] mSuggestionInfos;
        private int mNumberOfSuggestions;
        private boolean mCursorWasVisibleBeforeSuggestions;
        private boolean mIsShowingUp = false;
        private SuggestionAdapter mSuggestionsAdapter;
        private TextAppearanceSpan mHighlightSpan;  // TODO: Make mHighlightSpan final.
        private android.widget.TextView mAddToDictionaryButton;
        private android.widget.TextView mDeleteButton;
        private ListView mSuggestionListView;
        private final SuggestionSpanInfo mMisspelledSpanInfo = new SuggestionSpanInfo();
        private int mContainerMarginWidth;
        private int mContainerMarginTop;
        private LinearLayout mContainerView;
        private Context mContext;  // TODO: Make mContext final.

        private class CustomPopupWindow extends PopupWindow {

            @Override
            public void dismiss() {
                if (!isShowing()) {
                    return;
                }
                super.dismiss();
                getPositionListener().removeSubscriber(SuggestionsPopupWindow.this);

                mEditText.getText().removeSpan(mSuggestionRangeSpan);

                mEditText.setCursorVisible(mCursorWasVisibleBeforeSuggestions);
                if (hasInsertionController() && !extractedTextModeWillBeStarted()) {
                    getInsertionController().show();
                }
            }
        }

        public SuggestionsPopupWindow() {
            mCursorWasVisibleBeforeSuggestions = mEditText.isCursorVisibleFromAttr();
        }

        @Override
        protected void setUp() {
            mContext = applyDefaultTheme(mEditText.getContext());
            mHighlightSpan = new TextAppearanceSpan(mContext,
                    mEditText.mTextEditSuggestionHighlightStyle);
        }

        private Context applyDefaultTheme(Context originalContext) {
            // (EW) starting in Nougat, the AOSP version checked
            // com.android.internal.R.attr.isLightTheme even though android.R.attr.isLightTheme
            // wasn't made public until Q, so for older versions, we'll need to use our own copy of
            // the attribute.
            TypedArray a = originalContext.obtainStyledAttributes(new int[]{
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                            ? android.R.attr.isLightTheme : R.attr.isLightTheme,
                    R.attr.isHoloTheme
            });
            boolean isLightTheme = a.getBoolean(0, true);
            boolean isHoloTheme = a.getBoolean(1, false);
            a.recycle();

            // (EW) prior to Nougat, the AOSP version didn't bother with the theme wrapper and just
            // used the text view's context directly. despite normally using the Material theme,
            // Lollipop through Marshmallow and essentially used the Holo theme for this popup.
            // we're matching the more recent versions use of the Material theme here for those
            // versions. see comment in SuggestionsPopupWindow.SuggestionAdapter#getView. since
            // Nougat through at least S always use the Material overlay themes, it might be
            // reasonable to also always do that in Lollipop through Marshmallow (I didn't notice
            // any issues), but to be safe we'll still match the AOSP code when using the Holo
            // theme.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
                    || (Build.VERSION.SDK_INT < Build.VERSION_CODES.N && isHoloTheme)) {
                return originalContext;
            }

            int themeId = isLightTheme ? android.R.style.ThemeOverlay_Material_Light
                    : android.R.style.ThemeOverlay_Material_Dark;
            return new ContextThemeWrapper(originalContext, themeId);
        }

        @Override
        protected void createPopupWindow() {
            mPopupWindow = new CustomPopupWindow();
            mPopupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
            mPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            mPopupWindow.setFocusable(true);
            mPopupWindow.setClippingEnabled(false);
        }

        @Override
        protected void initContentView() {
            final LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
            mContentView = (ViewGroup) inflater.inflate(
                    mEditText.mTextEditSuggestionContainerLayout, null);

            mContainerView = (LinearLayout) mContentView.findViewById(
                    R.id.suggestionWindowContainer);
            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) mContainerView.getLayoutParams();
            mContainerMarginWidth = lp.leftMargin + lp.rightMargin;
            mContainerMarginTop = lp.topMargin;
            mClippingLimitLeft = lp.leftMargin;
            mClippingLimitRight = lp.rightMargin;

            mSuggestionListView = (ListView) mContentView.findViewById(R.id.suggestionContainer);

            mSuggestionsAdapter = new SuggestionAdapter();
            mSuggestionListView.setAdapter(mSuggestionsAdapter);
            mSuggestionListView.setOnItemClickListener(this);

            // Inflate the suggestion items once and for all.
            mSuggestionInfos = new SuggestionInfo[MAX_NUMBER_SUGGESTIONS];
            for (int i = 0; i < mSuggestionInfos.length; i++) {
                mSuggestionInfos[i] = new SuggestionInfo();
            }

            mAddToDictionaryButton = (android.widget.TextView) mContentView.findViewById(
                    R.id.addToDictionaryButton);
            mAddToDictionaryButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    final SuggestionSpan misspelledSpan =
                            findEquivalentSuggestionSpan(mMisspelledSpanInfo);
                    if (misspelledSpan == null) {
                        // Span has been removed.
                        return;
                    }
                    final Editable editable = mEditText.getText();
                    final int spanStart = editable.getSpanStart(misspelledSpan);
                    final int spanEnd = editable.getSpanEnd(misspelledSpan);
                    if (spanStart < 0 || spanEnd <= spanStart) {
                        return;
                    }
                    final String originalText = TextUtils.substring(editable, spanStart, spanEnd);

                    //FUTURE: (EW) Settings.ACTION_USER_DICTIONARY_INSERT is hidden and starting in
                    // S, it is restricted so reflection doesn't even work, but since it's just a
                    // string, we can just hard-code the value, but that has the chance to suddenly
                    // break on a new version, and given that reflection is blocked this seems even
                    // more likely that it could break. also, the comment for that constant says
                    // that the matching activity might not exist in some cases.
                    // I haven't been able to find an alternative. I tried just adding the word to
                    // the dictionary with UserDictionary.Words#addWord rather than relying on the
                    // questionably accessible system activity, but that doesn't seem to work
                    // on anything later than lollipop, and that's mentioned in the comment in
                    // UserDictionary saying "Starting on API 23, the user dictionary is only
                    // accessible through IME and spellchecker." also see
                    // https://stackoverflow.com/a/38417970. it seems that there currently isn't a
                    // legitimate option to do this in a custom text editor. it might be a better
                    // idea to just drop the feature of trying to add words to the dictionary since
                    // this easily could break in the future and there already might be some cases
                    // that it doesn't work.
                    try {
                        final Intent intent = new Intent(
                                "com.android.settings.USER_DICTIONARY_INSERT");
                        intent.putExtra(USER_DICTIONARY_EXTRA_WORD, originalText);
                        intent.putExtra(USER_DICTIONARY_EXTRA_LOCALE,
                                mEditText.getTextServicesLocale().toString());
                        intent.setFlags(intent.getFlags() | Intent.FLAG_ACTIVITY_NEW_TASK);
                        mEditText.getContext().startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        Log.e(TAG, "Failed to add word to dictionary: " + e);
                        hideWithCleanUp();
                        return;
                    }

                    // There is no way to know if the word was indeed added. Re-check.
                    // TODO The ExtractEditText should remove the span in the original text instead
                    editable.removeSpan(mMisspelledSpanInfo.mSuggestionSpan);
                    Selection.setSelection(editable, spanEnd);
                    updateSpellCheckSpans(spanStart, spanEnd, false);
                    hideWithCleanUp();
                }
            });

            mDeleteButton = (android.widget.TextView) mContentView.findViewById(R.id.deleteButton);
            mDeleteButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    final Editable editable = mEditText.getText();

                    final int spanUnionStart = editable.getSpanStart(mSuggestionRangeSpan);
                    int spanUnionEnd = editable.getSpanEnd(mSuggestionRangeSpan);
                    if (spanUnionStart >= 0 && spanUnionEnd > spanUnionStart) {
                        // Do not leave two adjacent spaces after deletion, or one at beginning of
                        // text
                        if (spanUnionEnd < editable.length()
                                && Character.isSpaceChar(editable.charAt(spanUnionEnd))
                                && (spanUnionStart == 0
                                        || Character.isSpaceChar(
                                                editable.charAt(spanUnionStart - 1)))) {
                            spanUnionEnd = spanUnionEnd + 1;
                        }
                        mEditText.deleteText_internal(spanUnionStart, spanUnionEnd);
                    }
                    hideWithCleanUp();
                }
            });

        }

        public boolean isShowingUp() {
            return mIsShowingUp;
        }

        public void onParentLostFocus() {
            mIsShowingUp = false;
        }

        private class SuggestionAdapter extends BaseAdapter {
            private final LayoutInflater mInflater = (LayoutInflater) mContext.getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);

            @Override
            public int getCount() {
                return mNumberOfSuggestions;
            }

            @Override
            public Object getItem(int position) {
                return mSuggestionInfos[position];
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                android.widget.TextView textView = (android.widget.TextView) convertView;

                if (textView == null) {
                    textView = (android.widget.TextView) mInflater.inflate(
                            mEditText.mTextEditSuggestionItemLayout, parent, false);
                }

                final SuggestionInfo suggestionInfo = mSuggestionInfos[position];
                textView.setText(suggestionInfo.mText);

                // (EW) prior to Nougat, the AOSP version set the background for the suggestions in
                // code. starting in Nougat for the Holo theme, the background was set in
                // Widget.Holo.SuggestionItem so setting it here isn't necessary anymore. rather
                // than matching the framework functionality, when not using the Holo theme on
                // Lollipop through Marshmallow we'll deviate from the framework functionality to be
                // more consistent with the rest of the Material theme that is used and be more
                // consistent between versions of this app, so those versions shouldn't set the
                // background, except for when the Holo theme is used. see comment in Theme.Material
                // in v21/themes.
                TypedArray a = mEditText.getContext().obtainStyledAttributes(new int[]{
                        R.attr.isHoloTheme
                });
                boolean isHoloTheme = a.getBoolean(0, false);
                a.recycle();
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
                        || (Build.VERSION.SDK_INT < Build.VERSION_CODES.N && isHoloTheme)) {
                    textView.setBackgroundColor(Color.WHITE);
                }

                return textView;
            }
        }

        @Override
        public void show() {
            if (extractedTextModeWillBeStarted()) {
                return;
            }

            if (updateSuggestions()) {
                mCursorWasVisibleBeforeSuggestions = mEditText.isCursorVisibleFromAttr();
                mEditText.setCursorVisible(false);
                mIsShowingUp = true;
                super.show();
            }

            mSuggestionListView.setVisibility(mNumberOfSuggestions == 0 ? View.GONE : View.VISIBLE);
        }

        @Override
        protected void measureContent() {
            final DisplayMetrics displayMetrics = mEditText.getResources().getDisplayMetrics();
            final int horizontalMeasure = View.MeasureSpec.makeMeasureSpec(
                    displayMetrics.widthPixels, View.MeasureSpec.AT_MOST);
            final int verticalMeasure = View.MeasureSpec.makeMeasureSpec(
                    displayMetrics.heightPixels, View.MeasureSpec.AT_MOST);

            int width = 0;
            View view = null;
            for (int i = 0; i < mNumberOfSuggestions; i++) {
                view = mSuggestionsAdapter.getView(i, view, mContentView);
                view.getLayoutParams().width = LayoutParams.WRAP_CONTENT;
                view.measure(horizontalMeasure, verticalMeasure);
                width = Math.max(width, view.getMeasuredWidth());
            }

            if (mAddToDictionaryButton.getVisibility() != View.GONE) {
                mAddToDictionaryButton.measure(horizontalMeasure, verticalMeasure);
                width = Math.max(width, mAddToDictionaryButton.getMeasuredWidth());
            }

            mDeleteButton.measure(horizontalMeasure, verticalMeasure);
            width = Math.max(width, mDeleteButton.getMeasuredWidth());

            width += mContainerView.getPaddingLeft() + mContainerView.getPaddingRight()
                    + mContainerMarginWidth;

            // Enforce the width based on actual text widths
            mContentView.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    verticalMeasure);

            Drawable popupBackground = mPopupWindow.getBackground();
            if (popupBackground != null) {
                if (mTempRect == null) mTempRect = new Rect();
                popupBackground.getPadding(mTempRect);
                width += mTempRect.left + mTempRect.right;
            }
            mPopupWindow.setWidth(width);
        }

        @Override
        protected int getTextOffset() {
            return (mEditText.getSelectionStart() + mEditText.getSelectionStart()) / 2;
        }

        @Override
        protected int getVerticalLocalPosition(int line) {
            final Layout layout = mEditText.getLayout();
            return HiddenLayout.getLineBottomWithoutSpacing(layout, line) - mContainerMarginTop;
        }

        @Override
        protected int clipVertically(int positionY) {
            final int height = mContentView.getMeasuredHeight();
            final DisplayMetrics displayMetrics = mEditText.getResources().getDisplayMetrics();
            return Math.min(positionY, displayMetrics.heightPixels - height);
        }

        private void hideWithCleanUp() {
            for (final SuggestionInfo info : mSuggestionInfos) {
                info.clear();
            }
            mMisspelledSpanInfo.clear();
            hide();
        }

        private boolean updateSuggestions() {
            Spannable spannable = mEditText.getText();
            mNumberOfSuggestions =
                    mSuggestionHelper.getSuggestionInfo(mSuggestionInfos, mMisspelledSpanInfo);
            if (mNumberOfSuggestions == 0 && mMisspelledSpanInfo.mSuggestionSpan == null) {
                return false;
            }

            int spanUnionStart = spannable.length();
            int spanUnionEnd = 0;

            for (int i = 0; i < mNumberOfSuggestions; i++) {
                final SuggestionSpanInfo spanInfo = mSuggestionInfos[i].mSuggestionSpanInfo;
                spanUnionStart = Math.min(spanUnionStart, spanInfo.mSpanStart);
                spanUnionEnd = Math.max(spanUnionEnd, spanInfo.mSpanEnd);
            }
            if (mMisspelledSpanInfo.mSuggestionSpan != null) {
                spanUnionStart = Math.min(spanUnionStart, mMisspelledSpanInfo.mSpanStart);
                spanUnionEnd = Math.max(spanUnionEnd, mMisspelledSpanInfo.mSpanEnd);
            }

            for (int i = 0; i < mNumberOfSuggestions; i++) {
                highlightTextDifferences(mSuggestionInfos[i], spanUnionStart, spanUnionEnd);
            }

            // Make "Add to dictionary" item visible if there is a span with the misspelled flag
            int addToDictionaryButtonVisibility = View.GONE;
            if (mMisspelledSpanInfo.mSuggestionSpan != null) {
                if (mMisspelledSpanInfo.mSpanStart >= 0
                        && mMisspelledSpanInfo.mSpanEnd > mMisspelledSpanInfo.mSpanStart) {
                    addToDictionaryButtonVisibility = View.VISIBLE;
                }
            }
            mAddToDictionaryButton.setVisibility(addToDictionaryButtonVisibility);

            if (mSuggestionRangeSpan == null) mSuggestionRangeSpan = new SuggestionRangeSpan();
            final int underlineColor;
            if (mNumberOfSuggestions != 0) {
                underlineColor =
                        getUnderlineColor(mSuggestionInfos[0].mSuggestionSpanInfo.mSuggestionSpan);
            } else {
                underlineColor = getUnderlineColor(mMisspelledSpanInfo.mSuggestionSpan);
            }

            if (underlineColor == 0) {
                // Fallback on the default highlight color when the first span does not provide one
                mSuggestionRangeSpan.setBackgroundColor(mEditText.mHighlightColor);
            } else {
                final float BACKGROUND_TRANSPARENCY = 0.4f;
                final int newAlpha = (int) (Color.alpha(underlineColor) * BACKGROUND_TRANSPARENCY);
                mSuggestionRangeSpan.setBackgroundColor(
                        (underlineColor & 0x00FFFFFF) + (newAlpha << 24));
            }
            spannable.setSpan(mSuggestionRangeSpan, spanUnionStart, spanUnionEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            mSuggestionsAdapter.notifyDataSetChanged();
            return true;
        }

        private void highlightTextDifferences(SuggestionInfo suggestionInfo, int unionStart,
                                              int unionEnd) {
            final Spannable text = mEditText.getText();
            final int spanStart = suggestionInfo.mSuggestionSpanInfo.mSpanStart;
            final int spanEnd = suggestionInfo.mSuggestionSpanInfo.mSpanEnd;

            // Adjust the start/end of the suggestion span
            suggestionInfo.mSuggestionStart = spanStart - unionStart;
            suggestionInfo.mSuggestionEnd = suggestionInfo.mSuggestionStart
                    + suggestionInfo.mText.length();

            suggestionInfo.mText.setSpan(mHighlightSpan, 0, suggestionInfo.mText.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            // Add the text before and after the span.
            final String textAsString = text.toString();
            suggestionInfo.mText.insert(0, textAsString.substring(unionStart, spanStart));
            suggestionInfo.mText.append(textAsString.substring(spanEnd, unionEnd));
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            SuggestionInfo suggestionInfo = mSuggestionInfos[position];
            replaceWithSuggestion(suggestionInfo);
            hideWithCleanUp();
        }
    }

    /**
     * An ActionMode Callback class that is used to provide actions while in text insertion or
     * selection mode.
     *
     * The default callback provides a subset of Select All, Cut, Copy, Paste, Share and Replace
     * actions, depending on which of these this EditText supports and the current selection.
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private class TextActionModeCallback extends ActionMode.Callback2 {

        private final Path mSelectionPath = new Path();
        private final RectF mSelectionBounds = new RectF();
        private final boolean mHasSelection;
        private final int mHandleHeight;

        public TextActionModeCallback(@TextActionMode int mode) {
            mHasSelection = mode == TextActionMode.SELECTION;
            if (mHasSelection) {
                SelectionModifierCursorController selectionController = getSelectionController();
                if (selectionController.mStartHandle == null) {
                    // As these are for initializing selectionController, hide() must be called.
                    loadHandleDrawables(false /* overwrite */);
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
            mode.setTitle(null);
            mode.setSubtitle(null);
            mode.setTitleOptionalHint(true);
            populateMenuWithItems(menu);

            Callback customCallback = getCustomCallback();
            if (customCallback != null) {
                if (!customCallback.onCreateActionMode(mode, menu)) {
                    // The custom mode can choose to cancel the action mode, dismiss selection.
                    Selection.setSelection(mEditText.getText(), mEditText.getSelectionEnd());
                    return false;
                }
            }

            if (mEditText.canProcessText()) {
                mProcessTextIntentActionsHandler.onInitializeMenu(menu);
            }

            if (mHasSelection && !mEditText.hasTransientState()) {
                mEditText.setHasTransientState(true);
            }
            return true;
        }

        private Callback getCustomCallback() {
            return mHasSelection
                    ? mCustomSelectionActionModeCallback
                    : mCustomInsertionActionModeCallback;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            updateSelectAllItem(menu);
            updateReplaceItem(menu);

            Callback customCallback = getCustomCallback();
            if (customCallback != null) {
                return customCallback.onPrepareActionMode(mode, menu);
            }
            return true;
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
            return mEditText.onTextContextMenuItem(item.getItemId());
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            // Clear mTextActionMode not to recursively destroy action mode by clearing selection.
            getSelectionActionModeHelper().onDestroyActionMode();
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
                Selection.setSelection(mEditText.getText(), mEditText.getSelectionEnd());
            }

            if (mSelectionModifierCursorController != null) {
                mSelectionModifierCursorController.hide();
            }
        }

        @Override
        public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
            if (!view.equals(mEditText) || mEditText.getLayout() == null) {
                super.onGetContentRect(mode, view, outRect);
                return;
            }
            if (mEditText.getSelectionStart() != mEditText.getSelectionEnd()) {
                // We have a selection.
                mSelectionPath.reset();
                mEditText.getLayout().getSelectionPath(
                        mEditText.getSelectionStart(), mEditText.getSelectionEnd(), mSelectionPath);
                mSelectionPath.computeBounds(mSelectionBounds, true);
                mSelectionBounds.bottom += mHandleHeight;
            } else {
                // We have a cursor.
                Layout layout = mEditText.getLayout();
                int line = layout.getLineForOffset(mEditText.getSelectionStart());
                float primaryHorizontal = clampHorizontalPosition(null,
                        layout.getPrimaryHorizontal(mEditText.getSelectionStart()));
                mSelectionBounds.set(
                        primaryHorizontal,
                        layout.getLineTop(line),
                        primaryHorizontal,
                        layout.getLineBottom(line) + mHandleHeight);
            }
            // Take EditText's padding and scroll into account.
            int textHorizontalOffset = mEditText.viewportToContentHorizontalOffset();
            int textVerticalOffset = mEditText.viewportToContentVerticalOffset();
            outRect.set(
                    (int) Math.floor(mSelectionBounds.left + textHorizontalOffset),
                    (int) Math.floor(mSelectionBounds.top + textVerticalOffset),
                    (int) Math.ceil(mSelectionBounds.right + textHorizontalOffset),
                    (int) Math.ceil(mSelectionBounds.bottom + textVerticalOffset));
        }
    }

    private void populateMenuWithItems(Menu menu) {
        if (mEditText.canCut()) {
            menu.add(Menu.NONE, EditText.ID_CUT, MENU_ITEM_ORDER_CUT,
                    android.R.string.cut)
                    .setAlphabeticShortcut('x')
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }

        if (mEditText.canCopy()) {
            menu.add(Menu.NONE, EditText.ID_COPY, MENU_ITEM_ORDER_COPY,
                    android.R.string.copy)
                    .setAlphabeticShortcut('c')
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }

        if (mEditText.canPaste()) {
            menu.add(Menu.NONE, EditText.ID_PASTE, MENU_ITEM_ORDER_PASTE,
                    android.R.string.paste)
                    .setAlphabeticShortcut('v')
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }

        if (mEditText.canShare()) {
            menu.add(Menu.NONE, EditText.ID_SHARE, MENU_ITEM_ORDER_SHARE, R.string.share)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mEditText.canRequestAutofill()) {
            final String selected = mEditText.getSelectedText();
            if (selected == null || selected.isEmpty()) {
                menu.add(Menu.NONE, EditText.ID_AUTOFILL, MENU_ITEM_ORDER_AUTOFILL,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
                                ? android.R.string.autofill : R.string.autofill)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            }
        }

        if (mEditText.canPasteAsPlainText()) {
            menu.add(
                    Menu.NONE,
                    EditText.ID_PASTE_AS_PLAIN_TEXT,
                    MENU_ITEM_ORDER_PASTE_AS_PLAIN_TEXT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? android.R.string.paste_as_plain_text
                            : R.string.paste_as_plain_text)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }

        updateSelectAllItem(menu);
        updateReplaceItem(menu);
    }

    private void updateSelectAllItem(Menu menu) {
        boolean canSelectAll = mEditText.canSelectAllText();
        boolean selectAllItemExists = menu.findItem(EditText.ID_SELECT_ALL) != null;
        if (canSelectAll && !selectAllItemExists) {
            menu.add(Menu.NONE, EditText.ID_SELECT_ALL, MENU_ITEM_ORDER_SELECT_ALL,
                    android.R.string.selectAll)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        } else if (!canSelectAll && selectAllItemExists) {
            menu.removeItem(EditText.ID_SELECT_ALL);
        }
    }

    private void updateReplaceItem(Menu menu) {
        boolean canReplace = mEditText.isSuggestionsEnabled() && shouldOfferToShowSuggestions();
        boolean replaceItemExists = menu.findItem(EditText.ID_REPLACE) != null;
        if (canReplace && !replaceItemExists) {
            menu.add(Menu.NONE, EditText.ID_REPLACE, MENU_ITEM_ORDER_REPLACE, R.string.replace)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        } else if (!canReplace && replaceItemExists) {
            menu.removeItem(EditText.ID_REPLACE);
        }
    }

    /**
     * A listener to call {@link InputMethodManager#updateCursorAnchorInfo(View, CursorAnchorInfo)}
     * while the input method is requesting the cursor/anchor position. Does nothing as long as
     * {@link InputMethodManager#isWatchingCursor(View)} returns false.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private final class CursorAnchorInfoNotifier implements EditTextPositionListener {
        final CursorAnchorInfo.Builder mSelectionInfoBuilder = new CursorAnchorInfo.Builder();
        final int[] mTmpIntOffset = new int[2];
        final Matrix mViewToScreenMatrix = new Matrix();

        @Override
        public void updatePosition(int parentPositionX, int parentPositionY,
                                   boolean parentPositionChanged, boolean parentScrolled) {
            final InputMethodState ims = mInputMethodState;
            if (ims == null || ims.mBatchEditNesting > 0) {
                return;
            }
            final InputMethodManager imm = getInputMethodManager();
            if (null == imm) {
                return;
            }
            if (!imm.isActive(mEditText)) {
                return;
            }
            // Skip if the IME has not requested the cursor/anchor position.
            // (EW) AOSP version calls InputMethodManager#isCursorAnchorInfoEnabled, but that is
            // hidden and restricted, and we couldn't call
            // InputMethodManager#setUpdateCursorAnchorInfoMode in EditableInputConnection, also due
            // to it being hidden and restricted, so we have to manage the mode separately.
            EditableInputConnection inputConnection = mEditText.getInputConnection();
            if (inputConnection == null || !inputConnection.isCursorAnchorInfoEnabled()) {
                return;
            }
            Layout layout = mEditText.getLayout();
            if (layout == null) {
                return;
            }

            final CursorAnchorInfo.Builder builder = mSelectionInfoBuilder;
            builder.reset();

            final int selectionStart = mEditText.getSelectionStart();
            builder.setSelectionRange(selectionStart, mEditText.getSelectionEnd());

            // Construct transformation matrix from view local coordinates to screen coordinates.
            mViewToScreenMatrix.set(mEditText.getMatrix());
            mEditText.getLocationOnScreen(mTmpIntOffset);
            mViewToScreenMatrix.postTranslate(mTmpIntOffset[0], mTmpIntOffset[1]);
            builder.setMatrix(mViewToScreenMatrix);

            final float viewportToContentHorizontalOffset =
                    mEditText.viewportToContentHorizontalOffset();
            final float viewportToContentVerticalOffset =
                    mEditText.viewportToContentVerticalOffset();

            final Spannable text = mEditText.getText();
            int composingTextStart = EditableInputConnection.getComposingSpanStart(text);
            int composingTextEnd = EditableInputConnection.getComposingSpanEnd(text);
            if (composingTextEnd < composingTextStart) {
                final int temp = composingTextEnd;
                composingTextEnd = composingTextStart;
                composingTextStart = temp;
            }
            final boolean hasComposingText =
                    (0 <= composingTextStart) && (composingTextStart < composingTextEnd);
            if (hasComposingText) {
                final CharSequence composingText = text.subSequence(composingTextStart,
                        composingTextEnd);
                builder.setComposingText(composingTextStart, composingText);
                mEditText.populateCharacterBounds(builder, composingTextStart,
                        composingTextEnd, viewportToContentHorizontalOffset,
                        viewportToContentVerticalOffset);
            }

            // Treat selectionStart as the insertion point.
            if (0 <= selectionStart) {
                final int offset = selectionStart;
                final int line = layout.getLineForOffset(offset);
                final float insertionMarkerX = layout.getPrimaryHorizontal(offset)
                        + viewportToContentHorizontalOffset;
                final float insertionMarkerTop = layout.getLineTop(line)
                        + viewportToContentVerticalOffset;
                final float insertionMarkerBaseline = layout.getLineBaseline(line)
                        + viewportToContentVerticalOffset;
                final float insertionMarkerBottom = HiddenLayout.getLineBottomWithoutSpacing(layout, line)
                        + viewportToContentVerticalOffset;
                final boolean isTopVisible = mEditText
                        .isPositionVisible(insertionMarkerX, insertionMarkerTop);
                final boolean isBottomVisible = mEditText
                        .isPositionVisible(insertionMarkerX, insertionMarkerBottom);
                int insertionMarkerFlags = 0;

                if (isTopVisible || isBottomVisible) {
                    insertionMarkerFlags |= CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION;
                }
                if (!isTopVisible || !isBottomVisible) {
                    insertionMarkerFlags |= CursorAnchorInfo.FLAG_HAS_INVISIBLE_REGION;
                }
                if (layout.isRtlCharAt(offset)) {
                    insertionMarkerFlags |= CursorAnchorInfo.FLAG_IS_RTL;
                }
                builder.setInsertionMarkerLocation(insertionMarkerX, insertionMarkerTop,
                        insertionMarkerBaseline, insertionMarkerBottom, insertionMarkerFlags);
            }

            CursorAnchorInfo cursorAnchorInfo = builder.build();

            // (EW) logic pulled from InputMethodManager#updateCursorAnchorInfo because we couldn't
            // call InputMethodManager#setUpdateCursorAnchorInfoMode in EditableInputConnection due
            // to it being hidden and restricted, so we have to manage the mode here instead.
            if (!inputConnection.isCursorAnchorInfoModeImmediate()
                    && Objects.equals(mLastCursorAnchorInfo, cursorAnchorInfo)) {
                if (DEBUG_CURSOR_ANCHOR_INFO) {
                    Log.w(TAG, "Ignoring redundant updateCursorAnchorInfo: info="
                            + cursorAnchorInfo);
                }
                return;
            }

            imm.updateCursorAnchorInfo(mEditText, builder.build());

            // (EW) logic pulled from InputMethodManager#updateCursorAnchorInfo because we couldn't
            // call InputMethodManager#setUpdateCursorAnchorInfoMode in EditableInputConnection due
            // to it being hidden and restricted, so we have to manage the mode here instead.
            mLastCursorAnchorInfo = cursorAnchorInfo;
            inputConnection.clearCursorAnchorInfoModeImmediate();
        }
    }

    // (EW) replaced with TextActionModeCallback in Marshmallow when it move to a floating popup,
    // rather than a fixed location. TextActionModeCallback is used for both selection and
    // insertion, but as this class's original name (SelectionActionModeCallback) implied, it was
    // only used for selection, and in addition to this, ActionPopupWindow was used both for
    // selection and insertion to show a popup, similar to TextActionModeCallback. to simplify logic
    // and have more consistent functionality between versions, our version of this also is used for
    // insertion, and it uses same menu items as TextActionModeCallback, which is more than the AOSP
    // version, is only text (rather than icons), and has a slightly different order than the AOSP
    // version.
    //FUTURE: (EW) since more buttons from TextActionModeCallback from more recent versions were
    // added to the ActionPopupWindow, the functionality there more closely matches the
    // functionality from TextActionModeCallback from more recent versions. consider removing this
    // and migrate any missing functionality to ActionPopupWindow to have a more consistent between
    // versions of this app (especially since I didn't bother to get the icons for the action bar
    // and just show text instead, so this doesn't even completely match the framework version).
    /**
     * An ActionMode Callback class that is used to provide actions while in text selection mode.
     *
     * The default callback provides a subset of Select All, Cut, Copy and Paste actions, depending
     * on which of these this EditText supports.
     */
    private class TextActionModeFixedCallback implements ActionMode.Callback {
        private final boolean mHasSelection;

        public TextActionModeFixedCallback(@TextActionMode int mode) {
            mHasSelection = mode == TextActionMode.SELECTION;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.setTitle(mEditText.getContext().getString(R.string.textSelectionCABTitle));
            mode.setSubtitle(null);
            mode.setTitleOptionalHint(true);

            populateMenuWithItems(menu);

            Callback customCallback = getCustomCallback();
            if (customCallback != null) {
                if (!customCallback.onCreateActionMode(mode, menu)) {
                    // The custom mode can choose to cancel the action mode, dismiss selection.
                    Selection.setSelection(mEditText.getText(), mEditText.getSelectionEnd());
                    return false;
                }
            }

            if (mHasSelection) {
                if (menu.hasVisibleItems() || mode.getCustomView() != null) {
                    getSelectionController().show();
                    mEditText.setHasTransientState(true);
                    return true;
                } else {
                    return false;
                }
            } else {
                return true;
            }
        }

        private Callback getCustomCallback() {
            return mHasSelection
                    ? mCustomSelectionActionModeCallback
                    : mCustomInsertionActionModeCallback;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            updateSelectAllItem(menu);
            updateReplaceItem(menu);

            Callback customCallback = getCustomCallback();
            if (customCallback != null) {
                return customCallback.onPrepareActionMode(mode, menu);
            }
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            Callback customCallback = getCustomCallback();
            if (customCallback != null && customCallback.onActionItemClicked(mode, item)) {
                return true;
            }
            return mEditText.onTextContextMenuItem(item.getItemId());
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            Callback customCallback = getCustomCallback();
            if (customCallback != null) {
                customCallback.onDestroyActionMode(mode);
            }
            /*
             * If we're ending this mode because we're detaching from a window,
             * we still have selection state to preserve. Don't clear it, we'll
             * bring back the selection mode when (if) we get reattached.
             */
            if (!mPreserveSelection) {
                Selection.setSelection(mEditText.getText(), mEditText.getSelectionEnd());
                mEditText.setHasTransientState(false);
            }
            if (mSelectionModifierCursorController != null) {
                mSelectionModifierCursorController.hide();
            }
            mTextActionMode = null;
        }
    }

    // (EW) only used prior to Marshmallow. this was essentially replaced by the
    // TextActionModeCallback menu
    private class ActionPopupWindow extends PinnedPopupWindow implements OnClickListener {
        private static final int POPUP_TEXT_LAYOUT = R.layout.text_edit_action_popup_text;
        private android.widget.TextView mPasteTextView;
        private android.widget.TextView mReplaceTextView;
        private android.widget.TextView mPasteAsPlainTextTextView;
        private android.widget.TextView mShareTextView;

        @Override
        protected void createPopupWindow() {
            mPopupWindow = new PopupWindow(mEditText.getContext(), null,
                    android.R.attr.textSelectHandleWindowStyle);
            mPopupWindow.setClippingEnabled(true);
        }

        @Override
        protected void initContentView() {
            // (EW) the AOSP version simply used a LinearLayout, but this intentionally deviates
            // from that because this has additional items to be more consistent with newer versions
            // of this app. if all of the items are shown at once, they won't fit, so this now has
            // to support dynamically shifting to multiple rows.
            mContentView = new RelativeLayout(mEditText.getContext());
            mContentView.setLayoutParams(new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            mContentView.setBackgroundResource(R.drawable.text_edit_paste_window);

            LayoutInflater inflater = (LayoutInflater) mEditText.getContext().
                    getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            LayoutParams wrapContent = new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

            mPasteTextView = (android.widget.TextView) inflater.inflate(POPUP_TEXT_LAYOUT, null);
            mPasteTextView.setLayoutParams(wrapContent);
            mContentView.addView(mPasteTextView);
            mPasteTextView.setText(android.R.string.paste);
            mPasteTextView.setOnClickListener(this);
            mPasteTextView.setId(R.id.action_popup_window_paste);

            mReplaceTextView = (android.widget.TextView) inflater.inflate(POPUP_TEXT_LAYOUT, null);
            mReplaceTextView.setLayoutParams(wrapContent);
            mContentView.addView(mReplaceTextView);
            mReplaceTextView.setText(R.string.replace);
            mReplaceTextView.setOnClickListener(this);
            mReplaceTextView.setId(R.id.action_popup_window_replace);

            // (EW) this didn't exist in the AOSP version but adding to make this app more
            // consistent between versions since later versions support this in
            // TextActionModeCallback
            mPasteAsPlainTextTextView =
                    (android.widget.TextView) inflater.inflate(POPUP_TEXT_LAYOUT, null);
            mPasteAsPlainTextTextView.setLayoutParams(wrapContent);
            mContentView.addView(mPasteAsPlainTextTextView);
            mPasteAsPlainTextTextView.setText(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? android.R.string.paste_as_plain_text
                    : R.string.paste_as_plain_text);
            mPasteAsPlainTextTextView.setOnClickListener(this);
            mPasteAsPlainTextTextView.setId(R.id.action_popup_window_paste_as_plain_text);

            // (EW) this didn't exist in the AOSP version but adding to make this app more
            // consistent between versions since later versions support this in
            // TextActionModeCallback
            mShareTextView =
                    (android.widget.TextView) inflater.inflate(POPUP_TEXT_LAYOUT, null);
            mShareTextView.setLayoutParams(wrapContent);
            mContentView.addView(mShareTextView);
            mShareTextView.setText(R.string.share);
            mShareTextView.setOnClickListener(this);
            mShareTextView.setId(R.id.action_popup_window_share);
        }

        @Override
        public void show() {
            boolean canPaste = mEditText.canPaste();
            boolean canSuggest = mEditText.isSuggestionsEnabled() && isCursorInsideSuggestionSpan();
            boolean canPasteAsPlainText = mEditText.canPasteAsPlainText();
            boolean canShare = mEditText.canShare();
            mPasteTextView.setVisibility(canPaste ? View.VISIBLE : View.GONE);
            mReplaceTextView.setVisibility(canSuggest ? View.VISIBLE : View.GONE);
            mPasteAsPlainTextTextView.setVisibility(canPasteAsPlainText ? View.VISIBLE : View.GONE);
            mShareTextView.setVisibility(canShare ? View.VISIBLE : View.GONE);

            if (!canPaste && !canSuggest && !canPasteAsPlainText && !canShare) return;

            mContentView.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;

            // (EW) this isn't in the AOSP version, but due to the additional items, not everything
            // will fit in one line, so anything that doesn't fit will be pushed onto the next line
            final DisplayMetrics displayMetrics = mEditText.getResources().getDisplayMetrics();
            int curWidth = 0;
            android.widget.TextView previousTextViewOnLine = null;
            android.widget.TextView firstTextViewOnPreviousLine = null;
            android.widget.TextView firstTextViewOnLine = null;
            final android.widget.TextView[] textViews = new android.widget.TextView[] {
                    mPasteTextView, mReplaceTextView, mPasteAsPlainTextTextView, mShareTextView
            };
            for (android.widget.TextView textView : textViews) {
                if (textView.getVisibility() != View.VISIBLE) {
                    continue;
                }

                RelativeLayout.LayoutParams params =
                        (RelativeLayout.LayoutParams) textView.getLayoutParams();

                params.removeRule(RelativeLayout.BELOW);
                params.removeRule(RelativeLayout.END_OF);
                params.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
                params.removeRule(RelativeLayout.ALIGN_PARENT_START);

                int widthMeasureSpec = MeasureSpec.makeMeasureSpec(
                        mContentView.getLayoutParams().width, MeasureSpec.AT_MOST);
                int heightMeasureSpec = MeasureSpec.makeMeasureSpec(
                        mContentView.getLayoutParams().height, MeasureSpec.AT_MOST);
                textView.measure(widthMeasureSpec, heightMeasureSpec);
                int measuredWidth = textView.getMeasuredWidth();

                if (curWidth + measuredWidth > displayMetrics.widthPixels) {
                    firstTextViewOnPreviousLine = firstTextViewOnLine;
                    firstTextViewOnLine = textView;
                    curWidth = measuredWidth;

                    params.addRule(RelativeLayout.ALIGN_PARENT_START, 1);
                } else {
                    curWidth += measuredWidth;

                    if (previousTextViewOnLine != null) {
                        params.addRule(RelativeLayout.END_OF, previousTextViewOnLine.getId());
                    } else {
                        params.addRule(RelativeLayout.ALIGN_PARENT_START, 1);
                    }
                }

                if (firstTextViewOnPreviousLine != null) {
                    params.addRule(RelativeLayout.BELOW, firstTextViewOnPreviousLine.getId());
                } else {
                    params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 1);
                }

                if (firstTextViewOnLine == null) {
                    firstTextViewOnLine = textView;
                }
                previousTextViewOnLine = textView;
            }

            super.show();
        }

        @Override
        public void onClick(View view) {
            if (view == mPasteTextView && mEditText.canPaste()) {
                mEditText.onTextContextMenuItem(EditText.ID_PASTE);
                hide();
            } else if (view == mReplaceTextView) {
                stopTextActionMode();
                mEditText.onTextContextMenuItem(EditText.ID_REPLACE);
            } else if (view == mPasteAsPlainTextTextView && mEditText.canPasteAsPlainText()) {
                mEditText.onTextContextMenuItem(EditText.ID_PASTE_AS_PLAIN_TEXT);
                hide();
            } else if (view == mShareTextView && mEditText.canShare()) {
                mEditText.onTextContextMenuItem(EditText.ID_SHARE);
                hide();
            }
        }

        @Override
        protected int getTextOffset() {
            return (mEditText.getSelectionStart() + mEditText.getSelectionEnd()) / 2;
        }

        @Override
        protected int getVerticalLocalPosition(int line) {
            return mEditText.getLayout().getLineTop(line) - mContentView.getMeasuredHeight();
        }

        @Override
        protected int clipVertically(int positionY) {
            if (positionY < 0) {
                final int offset = getTextOffset();
                final Layout layout = mEditText.getLayout();
                final int line = layout.getLineForOffset(offset);
                positionY += layout.getLineBottom(line) - layout.getLineTop(line);
                positionY += mContentView.getMeasuredHeight();

                // Assumes insertion and selection handles share the same height
                final Drawable handle = mEditText.getTextSelectHandle();
                positionY += handle.getIntrinsicHeight();
            }

            return positionY;
        }
    }

    abstract class HandleView extends View implements EditTextPositionListener {
        protected Drawable mDrawable;
        protected Drawable mDrawableLtr;
        protected Drawable mDrawableRtl;
        private final PopupWindow mContainer;
        // Position with respect to the parent EditText
        private int mPositionX, mPositionY;
        private boolean mIsDragging;
        // Offset from touch position to mPosition
        private float mTouchToWindowOffsetX, mTouchToWindowOffsetY;
        protected int mHotspotX;
        protected int mHorizontalGravity;
        // Offsets the hotspot point up, so that cursor is not hidden by the finger when moving up
        private float mTouchOffsetY;
        // Where the touch position should be on the handle to ensure a maximum cursor visibility.
        // This is the distance in pixels from the top of the handle view.
        private final float mIdealVerticalOffset;
        // Parent's (EditText) previous position in window
        private int mLastParentX, mLastParentY;
        // Parent's (EditText) previous position on screen
        private int mLastParentXOnScreen, mLastParentYOnScreen;
        // Transient action popup window for Paste and Replace actions
        // (EW) only used prior to Marshmallow
        protected ActionPopupWindow mActionPopupWindow;
        // Previous text character offset
        protected int mPreviousOffset = -1;
        // Previous text character offset
        private boolean mPositionHasChanged = true;
        // Used to delay the appearance of the action popup window
        // (EW) only used prior to Marshmallow
        private Runnable mActionPopupShower;
        // Minimum touch target size for handles
        private int mMinSize;
        // Indicates the line of text that the handle is on.
        protected int mPrevLine = UNSET_LINE;
        // Indicates the line of text that the user was touching. This can differ from mPrevLine
        // when selecting text when the handles jump to the end / start of words which may be on
        // a different line.
        protected int mPreviousLineTouched = UNSET_LINE;
        /**
         * The vertical distance in pixels from finger to the cursor Y while dragging.
         * See {@link Editor.InsertionPointCursorController#getLineDuringDrag}.
         */
        private final int mIdealFingerToCursorOffset;

        private HandleView(Drawable drawableLtr, Drawable drawableRtl, final int id) {
            super(mEditText.getContext());
            setId(id);
            mContainer = new PopupWindow(mEditText.getContext(), null,
                    android.R.attr.textSelectHandleWindowStyle);
            mContainer.setSplitTouchEnabled(true);
            mContainer.setClippingEnabled(false);
            setWindowLayoutType(mContainer, WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL);
            mContainer.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
            mContainer.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
            mContainer.setContentView(this);

            setDrawables(drawableLtr, drawableRtl);

            mMinSize = mEditText.getContext().getResources().getDimensionPixelSize(
                    R.dimen.text_handle_min_size);

            final int handleHeight = getPreferredHeight();
            mTouchOffsetY = -0.3f * handleHeight;
            // (EW) the AOSP version starting in R checks AppGlobals.getIntCoreSetting (See #Editor)
            // to get a finger to cursor distance. the default value was -1, which is the same as I
            // found in testing with reflection (not even actually coming from the default specified
            // here). it treats -1 as essentially no value, so instead of using that to populate
            // mIdealFingerToCursorOffset and use that to calculate mIdealVerticalOffset, it would
            // just do this to hard-code mIdealVerticalOffset (same as in previous versions).
            mIdealVerticalOffset = 0.7f * handleHeight;
            mIdealFingerToCursorOffset = (int)(mIdealVerticalOffset - mTouchOffsetY);
        }

        public float getIdealVerticalOffset() {
            return mIdealVerticalOffset;
        }

        final int getIdealFingerToCursorOffset() {
            return mIdealFingerToCursorOffset;
        }

        void setDrawables(final Drawable drawableLtr, final Drawable drawableRtl) {
            mDrawableLtr = drawableLtr;
            mDrawableRtl = drawableRtl;
            updateDrawable(true /* updateDrawableWhenDragging */);
        }

        protected void updateDrawable(final boolean updateDrawableWhenDragging) {
            if (!updateDrawableWhenDragging && mIsDragging) {
                return;
            }
            final Layout layout = mEditText.getLayout();
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
                mPositionX += mEditText.viewportToContentHorizontalOffset();
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

        protected final int getPreferredWidth() {
            return Math.max(mDrawable.getIntrinsicWidth(), mMinSize);
        }

        protected final int getPreferredHeight() {
            return Math.max(mDrawable.getIntrinsicHeight(), mMinSize);
        }

        public void show() {
            if (EditText.DEBUG_CURSOR) {
                logCursor(getClass().getSimpleName() + ": HandleView: show()", "offset=%s",
                        getCurrentCursorOffset());
            }

            if (isShowing()) return;

            getPositionListener().addSubscriber(this, true /* local position may change */);

            // Make sure the offset is always considered new, even when focusing at same position
            mPreviousOffset = -1;
            positionAtCursorOffset(getCurrentCursorOffset(), false, false);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                hideActionPopupWindow();
            }
        }

        protected void dismiss() {
            mIsDragging = false;
            mContainer.dismiss();
            onDetached();
        }

        public void hide() {
            if (EditText.DEBUG_CURSOR) {
                logCursor(getClass().getSimpleName() + ": HandleView: hide()", "offset=%s",
                        getCurrentCursorOffset());
            }

            dismiss();

            getPositionListener().removeSubscriber(this);
        }

        // (EW) this should only be used in versions prior to Marshmallow since the floating
        // TextActionModeCallback menu added in Marshmallow essentially replaces this functionality
        void showActionPopupWindow(int delay) {
            if (mActionPopupWindow == null) {
                mActionPopupWindow = new ActionPopupWindow();
            }
            if (mActionPopupShower == null) {
                mActionPopupShower = new Runnable() {
                    public void run() {
                        mActionPopupWindow.show();
                    }
                };
            } else {
                mEditText.removeCallbacks(mActionPopupShower);
            }
            mEditText.postDelayed(mActionPopupShower, delay);
        }

        // (EW) this is only really necessary in versions prior to Marshmallow since the
        // ActionPopupWindow shouldn't be used in more recent versions, so there won't be anything
        // to hide
        protected void hideActionPopupWindow() {
            if (mActionPopupShower != null) {
                mEditText.removeCallbacks(mActionPopupShower);
            }
            if (mActionPopupWindow != null) {
                mActionPopupWindow.hide();
            }
        }

        public boolean isShowing() {
            return mContainer.isShowing();
        }

        private boolean shouldShow() {
            // A dragging handle should always be shown.
            if (mIsDragging) {
                return true;
            }

            if (mEditText.isInBatchEditMode()) {
                return false;
            }

            return mEditText.isPositionVisible(
                    mPositionX + mHotspotX + getHorizontalOffset(), mPositionY);
        }

        private void setVisible(final boolean visible) {
            mContainer.getContentView().setVisibility(visible ? VISIBLE : INVISIBLE);
        }

        public abstract int getCurrentCursorOffset();

        protected abstract void updateSelection(int offset);

        protected abstract void updatePosition(float x, float y, boolean fromTouchScreen);

        protected boolean isAtRtlRun(@NonNull Layout layout, int offset) {
            return layout.isRtlCharAt(offset);
        }

        public float getHorizontal(@NonNull Layout layout, int offset) {
            return layout.getPrimaryHorizontal(offset);
        }

        protected int getOffsetAtCoordinate(@NonNull Layout layout, int line, float x) {
            return mEditText.getOffsetAtCoordinate(line, x);
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
            Layout layout = mEditText.getLayout();
            if (layout == null) {
                // Will update controllers' state, hiding them and stopping selection mode if needed
                prepareCursorControllers();
                return;
            }
            layout = mEditText.getLayout();

            boolean offsetChanged = offset != mPreviousOffset;
            if (offsetChanged || forceUpdatePosition) {
                if (offsetChanged) {
                    updateSelection(offset);
                    // (EW) prior to Oreo MR1, the AOSP version didn't perform haptic feedback, and
                    // since that constant doesn't exist, there probably isn't much to do even if we
                    // wanted this in old versions
                    if (fromTouchScreen && mHapticTextHandleEnabled
                            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        mEditText.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
                    }
                    addPositionToTouchUpFilter(offset);
                }
                final int line = layout.getLineForOffset(offset);
                mPrevLine = line;

                mPositionX = getCursorHorizontalPosition(layout, offset) - mHotspotX
                        - getHorizontalOffset() + getCursorOffset();
                mPositionY = HiddenLayout.getLineBottomWithoutSpacing(layout, line);

                // Take EditText's padding and scroll into account.
                mPositionX += mEditText.viewportToContentHorizontalOffset();
                mPositionY += mEditText.viewportToContentVerticalOffset();

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
                    // Transform to the window coordinates to follow the view transformation.
                    final int[] pts = { mPositionX + mHotspotX + getHorizontalOffset(), mPositionY};
                    // (EW) added in Nougat (both the call here and the original method in View
                    // (broken out from getLocationInWindow))
                    mEditText.transformFromViewToWindowSpace(pts);
                    pts[0] -= mHotspotX + getHorizontalOffset();

                    if (isShowing()) {
                        mContainer.update(pts[0], pts[1], -1, -1);
                    } else {
                        mContainer.showAtLocation(mEditText, Gravity.NO_GRAVITY, pts[0], pts[1]);
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

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (EditText.DEBUG_CURSOR) {
                logCursor(this.getClass().getSimpleName() + ": HandleView: onTouchEvent",
                        "%s (%f,%f)",
                        MotionEvent.actionToString(ev.getActionMasked()),
                        ev.getX(), ev.getY());
            }

            updateFloatingToolbarVisibility(ev);

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
                    updateDrawable(false /* updateDrawableWhenDragging */);
                    break;
            }
            return true;
        }

        public boolean isDragging() {
            return mIsDragging;
        }

        void onHandleMoved() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                hideActionPopupWindow();
            }
        }

        public void onDetached() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                hideActionPopupWindow();
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            super.onSizeChanged(w, h, oldW, oldH);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, 0, w, h)));
            }
        }
    }

    private class InsertionHandleView extends HandleView {
        // Used to detect taps on the insertion handle, which will affect the insertion action mode
        // and ActionPopupWindow
        private float mLastDownRawX, mLastDownRawY;
        private Runnable mHider;

        // Members for fake-dismiss effect in touch through mode.
        // It is to make InsertionHandleView can receive the MOVE/UP events after calling dismiss(),
        // which could happen in case of long-press (making selection will dismiss the insertion
        // handle).

        // Whether the finger is down and hasn't been up yet.
        private boolean mIsTouchDown = false;
        // Whether the popup window is in the invisible state and will be dismissed when finger up.
        private boolean mPendingDismissOnUp = false;
        // The alpha value of the drawable.
        private final int mDrawableOpacity;

        // Members for toggling the insertion menu in touch through mode.

        // The coordinate for the touch down event, which is used for transforming the coordinates
        // of the events to the text view.
        private float mTouchDownX;
        private float mTouchDownY;
        // The cursor offset when touch down. This is to detect whether the cursor is moved when
        // finger move/up.
        private int mOffsetDown;
        // Whether the cursor offset has been changed on the move/up events.
        private boolean mOffsetChanged;
        // Whether it is in insertion action mode when finger down.
        private boolean mIsInActionMode;
        // The timestamp for the last up event, which is used for double tap detection.
        private long mLastUpTime;

        // The delta height applied to the insertion handle view.
        private final int mDeltaHeight;

        InsertionHandleView(Drawable drawable) {
            super(drawable, drawable, R.id.insertion_handle);

            int deltaHeight = 0;
            int opacity = 255;
            if (mFlagInsertionHandleGesturesEnabled) {
                // (EW) the AOSP version checks AppGlobals.getIntCoreSetting (See #Editor) starting
                // in R with a default value of WidgetFlags.INSERTION_HANDLE_DELTA_HEIGHT_DEFAULT
                // (25). from my testing using reflection, 25 was returned (not actually coming from
                // the default value). there was no equivalent for this in older versions to
                // reference. the AOSP version also had a second level default for values outside of
                // some valid/supported range (-25 - 50) that also matched.
                deltaHeight = 25;
                // (EW) the AOSP version checks AppGlobals.getIntCoreSetting starting in R with a
                // default value of WidgetFlags.INSERTION_HANDLE_OPACITY_DEFAULT (50). from my
                // testing using reflection, 50 was returned (not actually coming from the default
                // value). there was no equivalent for this in older versions to reference. the AOSP
                // version also had a second level default for values outside of some
                // valid/supported range (10 - 100) that also matched.
                opacity = 50;
                // Converts the opacity value from range {0..100} to {0..255}.
                opacity = opacity * 255 / 100;
            }
            mDeltaHeight = deltaHeight;
            mDrawableOpacity = opacity;
        }

        // (EW) this should only be used prior to Marshmallow. see showActionPopupWindow
        public void showWithActionPopup() {
            show();
            showActionPopupWindow(0);
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
            mEditText.postDelayed(mHider, DELAY_BEFORE_HANDLE_FADES_OUT);
        }

        private void removeHiderCallback() {
            if (mHider != null) {
                mEditText.removeCallbacks(mHider);
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
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (mFlagInsertionHandleGesturesEnabled) {
                final int height = Math.max(
                        getPreferredHeight() + mDeltaHeight, mDrawable.getIntrinsicHeight());
                setMeasuredDimension(getPreferredWidth(), height);
                return;
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (!mEditText.isFromPrimePointer(ev, true)) {
                return true;
            }
            if (mFlagInsertionHandleGesturesEnabled && mFlagCursorDragFromAnywhereEnabled) {
                // Should only enable touch through when cursor drag is enabled.
                // Otherwise the insertion handle view cannot be moved.
                return touchThrough(ev);
            }
            final boolean result = super.onTouchEvent(ev);

            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mLastDownRawX = ev.getRawX();
                    mLastDownRawY = ev.getRawY();
                    break;

                case MotionEvent.ACTION_UP:
                    if (!offsetHasBeenChanged()) {
                        ViewConfiguration config = ViewConfiguration.get(mEditText.getContext());
                        boolean isWithinTouchSlop = EditorTouchState.isDistanceWithin(
                                mLastDownRawX, mLastDownRawY, ev.getRawX(), ev.getRawY(),
                                config.getScaledTouchSlop());
                        if (isWithinTouchSlop) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                // Tapping on the handle toggles the insertion action mode.
                                toggleInsertionActionMode();
                            } else {
                                // (EW) on previous versions, the insertion/text action mode adds
                                // buttons on the action bar instead of the floating menu that was
                                // made available starting in Marshmallow. instead, we'll show the
                                // old version of the custom floating view, which is what the
                                // framework version does for these older versions.
                                if (mActionPopupWindow != null && mActionPopupWindow.isShowing()) {
                                    // Tapping on the handle dismisses the displayed action popup
                                    mActionPopupWindow.hide();
                                } else {
                                    showWithActionPopup();
                                }
                            }
                        }
                    } else {
                        if (mTextActionMode != null) {
                            // (EW) nothing needs to be done prior to Marshmallow because the
                            // copy/paste/etc popup was in a fixed position and this function only
                            // makes sense for dynamic positioning
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                mTextActionMode.invalidateContentRect();
                            }
                        }
                    }
                    // Fall through.
                case MotionEvent.ACTION_CANCEL:
                    hideAfterDelay();
                    break;

                default:
                    break;
            }

            return result;
        }

        // Handles the touch events in touch through mode.
        private boolean touchThrough(MotionEvent ev) {
            final int actionType = ev.getActionMasked();
            switch (actionType) {
                case MotionEvent.ACTION_DOWN:
                    mIsTouchDown = true;
                    mOffsetChanged = false;
                    mOffsetDown = mEditText.getSelectionStart();
                    mTouchDownX = ev.getX();
                    mTouchDownY = ev.getY();
                    mIsInActionMode = mTextActionMode != null;
                    if (ev.getEventTime() - mLastUpTime < ViewConfiguration.getDoubleTapTimeout()) {
                        stopTextActionMode();  // Avoid crash when double tap and drag backwards.
                    }
                    mTouchState.setIsOnHandle(true);
                    break;
                case MotionEvent.ACTION_UP:
                    mLastUpTime = ev.getEventTime();
                    break;
            }
            // Performs the touch through by forward the events to the text view.
            boolean ret = mEditText.onTouchEvent(transformEventForTouchThrough(ev));

            if (actionType == MotionEvent.ACTION_UP || actionType == MotionEvent.ACTION_CANCEL) {
                mIsTouchDown = false;
                if (mPendingDismissOnUp) {
                    dismiss();
                }
                mTouchState.setIsOnHandle(false);
            }

            // Checks for cursor offset change.
            if (!mOffsetChanged) {
                int start = mEditText.getSelectionStart();
                int end = mEditText.getSelectionEnd();
                if (start != end || mOffsetDown != start) {
                    mOffsetChanged = true;
                }
            }

            // Toggling the insertion action mode on finger up.
            if (!mOffsetChanged && actionType == MotionEvent.ACTION_UP) {
                if (mIsInActionMode) {
                    stopTextActionMode();
                } else {
                    startInsertionActionMode();
                }
            }
            return ret;
        }

        private MotionEvent transformEventForTouchThrough(MotionEvent ev) {
            final Layout layout = mEditText.getLayout();
            final int line = layout.getLineForOffset(getCurrentCursorOffset());
            final int textHeight = HiddenLayout.getLineBottomWithoutSpacing(layout, line)
                    - layout.getLineTop(line);
            // Transforms the touch events to screen coordinates.
            // And also shift up to make the hit point is on the text.
            // Note:
            //  - The revised X should reflect the distance to the horizontal center of touch down.
            //  - The revised Y should be at the top of the text.
            Matrix m = new Matrix();
            m.setTranslate(ev.getRawX() - ev.getX() + (getMeasuredWidth() >> 1) - mTouchDownX,
                    ev.getRawY() - ev.getY() - (textHeight >> 1) - mTouchDownY);
            ev.transform(m);
            // Transforms the touch events to text view coordinates.
            mEditText.toLocalMotionEvent(ev);
            if (EditText.DEBUG_CURSOR) {
                logCursor("InsertionHandleView#transformEventForTouchThrough",
                        "Touch through: %d, (%f, %f)",
                        ev.getAction(), ev.getX(), ev.getY());
            }
            return ev;
        }

        @Override
        public boolean isShowing() {
            if (mPendingDismissOnUp) {
                return false;
            }
            return super.isShowing();
        }

        @Override
        public void show() {
            super.show();
            mPendingDismissOnUp = false;
            mDrawable.setAlpha(mDrawableOpacity);
        }

        @Override
        public void dismiss() {
            if (mIsTouchDown) {
                if (EditText.DEBUG_CURSOR) {
                    logCursor("InsertionHandleView#dismiss",
                            "Suppressed the real dismiss, only become invisible");
                }
                mPendingDismissOnUp = true;
                mDrawable.setAlpha(0);
            } else {
                super.dismiss();
                mPendingDismissOnUp = false;
            }
        }

        @Override
        protected void updateDrawable(final boolean updateDrawableWhenDragging) {
            super.updateDrawable(updateDrawableWhenDragging);
            mDrawable.setAlpha(mDrawableOpacity);
        }

        @Override
        public int getCurrentCursorOffset() {
            return mEditText.getSelectionStart();
        }

        @Override
        public void updateSelection(int offset) {
            Selection.setSelection(mEditText.getText(), offset);
        }

        @Override
        protected void updatePosition(float x, float y, boolean fromTouchScreen) {
            Layout layout = mEditText.getLayout();
            int offset;
            if (layout != null) {
                if (mPreviousLineTouched == UNSET_LINE) {
                    mPreviousLineTouched = mEditText.getLineAtCoordinate(y);
                }
                int currLine = getCurrentLineAdjustedForSlop(layout, mPreviousLineTouched, y);
                offset = getOffsetAtCoordinate(layout, currLine, x);
                mPreviousLineTouched = currLine;
            } else {
                offset = -1;
            }
            if (EditText.DEBUG_CURSOR) {
                logCursor("InsertionHandleView: updatePosition", "x=%f, y=%f, offset=%d, line=%d",
                        x, y, offset, mPreviousLineTouched);
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
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
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
        // Distance from edge of horizontally scrolling edit text
        // to use to switch to character mode.
        private final float mEditTextEdgeSlop;
        // Used to save edit text location.
        private final int[] mEditTextLocation = new int[2];

        public SelectionHandleView(Drawable drawableLtr, Drawable drawableRtl, int id,
                                   @HandleType int handleType) {
            super(drawableLtr, drawableRtl, id);
            mHandleType = handleType;
            ViewConfiguration viewConfiguration = ViewConfiguration.get(mEditText.getContext());
            mEditTextEdgeSlop = viewConfiguration.getScaledTouchSlop() * 4;
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
            return isStartHandle() ? mEditText.getSelectionStart() : mEditText.getSelectionEnd();
        }

        @Override
        protected void updateSelection(int offset) {
            if (isStartHandle()) {
                Selection.setSelection(mEditText.getText(), offset, mEditText.getSelectionEnd());
            } else {
                Selection.setSelection(mEditText.getText(), mEditText.getSelectionStart(), offset);
            }
            updateDrawable(false /* updateDrawableWhenDragging */);
            if (mTextActionMode != null) {
                invalidateActionMode();
            }
        }

        @Override
        protected void updatePosition(float x, float y, boolean fromTouchScreen) {
            final Layout layout = mEditText.getLayout();
            if (layout == null) {
                // HandleView will deal appropriately in positionAtCursorOffset when
                // layout is null.
                positionAndAdjustForCrossingHandles(mEditText.getOffsetForPosition(x, y),
                        fromTouchScreen);
                return;
            }

            if (mPreviousLineTouched == UNSET_LINE) {
                mPreviousLineTouched = mEditText.getLineAtCoordinate(y);
            }

            boolean positionCursor = false;
            final int anotherHandleOffset =
                    isStartHandle() ? mEditText.getSelectionEnd() : mEditText.getSelectionStart();
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
            final boolean isLvlBoundary = HiddenLayout.isLevelBoundary(layout,
                    mEditText.getTextDir(), offset);

            // We can't determine if the user is expanding or shrinking the selection if they're
            // on a bi-di boundary, so until they've moved past the boundary we'll just place
            // the cursor at the current position.
            if (isLvlBoundary || (rtlAtCurrentOffset && !atRtl) || (!rtlAtCurrentOffset && atRtl)) {
                // We're on a boundary or this is the first direction change -- just update
                // to the current position.
                mLanguageDirectionChanged = true;
                mTouchWordDelta = 0.0f;
                positionAndAdjustForCrossingHandles(offset, fromTouchScreen);
                return;
            } else if (mLanguageDirectionChanged) {
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

            if (mEditText.isHorizontallyScrollable()) {
                if (positionNearEdgeOfScrollingView(x, atRtl)
                        && ((isStartHandle() && mEditText.getScrollX() != 0)
                                || (!isStartHandle()
                                        && mEditText.canScrollHorizontally(atRtl ? -1 : 1)))
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
                            mEditText.convertToLocalHorizontalCoordinate(x) - adjustedX;
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
                                    mEditText.convertToLocalHorizontalCoordinate(x) - adjustedX;
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
                    mTouchWordDelta = mEditText.convertToLocalHorizontalCoordinate(x)
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
            if (!mEditText.isFromPrimePointer(event, true)) {
                return true;
            }
            boolean superResult = super.onTouchEvent(event);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // Reset the touch word offset and x value when the user
                    // re-engages the handle.
                    mTouchWordDelta = 0.0f;
                    mPrevX = UNSET_X_VALUE;
                    break;
            }

            return superResult;
        }

        private void positionAndAdjustForCrossingHandles(int offset, boolean fromTouchScreen) {
            final int anotherHandleOffset =
                    isStartHandle() ? mEditText.getSelectionEnd() : mEditText.getSelectionStart();
            if ((isStartHandle() && offset >= anotherHandleOffset)
                    || (!isStartHandle() && offset <= anotherHandleOffset)) {
                mTouchWordDelta = 0.0f;
                final Layout layout = mEditText.getLayout();
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
                        final long range = HiddenLayout.getRunRange(layout, mEditText.getTextDir(),
                                offsetToGetRunRange);
                        if (isStartHandle()) {
                            offset = HiddenTextUtils.unpackRangeStartFromLong(range);
                        } else {
                            offset = HiddenTextUtils.unpackRangeEndFromLong(range);
                        }
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
            mEditText.getLocationOnScreen(mEditTextLocation);
            boolean nearEdge;
            if (atRtl == isStartHandle()) {
                int rightEdge = mEditTextLocation[0] + mEditText.getWidth()
                        - mEditText.getPaddingRight();
                nearEdge = x > rightEdge - mEditTextEdgeSlop;
            } else {
                int leftEdge = mEditTextLocation[0] + mEditText.getPaddingLeft();
                nearEdge = x < leftEdge + mEditTextEdgeSlop;
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
            final float localX = mEditText.convertToLocalHorizontalCoordinate(x);
            //FUTURE: (EW) this doesn't match AOSP. as of S, it got Layout#getOffsetForHorizontal for
            // both the primary and secondary horizontal and had some logic to determine which to
            // use, but the overload with the boolean primary argument isn't accessible. I'm not
            // completely sure what the separate horizontals are for, but it seems related to LTR vs
            // RTL or bidirectional text.
            // prior to Nougat, TextView#getOffsetAtCoordinate was just used instead. at that time
            // Layout#getOffsetForHorizontal didn't take the boolean primary argument, although
            // Layout#getPrimaryHorizontal and Layout#getSecondaryHorizontal did exist by then.
            // Layout#getOffsetForHorizontal seemed to just work off of the primary horizontal.
            // I tried to just copy the logic from Layout#getOffsetForHorizontal, but it called into
            // Layout#getIndentAdjust, Layout#getStartHyphenEdit, and Layout#getEndHyphenEdit,
            // which are hidden and are overridden by some child Layout classes, so there isn't a
            // good way to call those. it also checks Layout#mJustificationMode, which is only set
            // by a hidden method and there isn't a good way to access it.
            // since we only have access to the Layout#getOffsetForHorizontal for the primary
            // horizontal (which is similar to logic used in previous version) and just copying the
            // logic isn't really an option, this will have to be good enough for now. maybe if AOSP
            // changes logic or opens up an API in the future, we can pull that in. also, if I know
            // a specific bug that this causes, maybe there is something more that can be done then
            // to mitigate it.
            final int primaryOffset = layout.getOffsetForHorizontal(line, localX);
            return primaryOffset;
        }

        public ActionPopupWindow getActionPopupWindow() {
            return mActionPopupWindow;
        }

        public void setActionPopupWindow(ActionPopupWindow actionPopupWindow) {
            mActionPopupWindow = actionPopupWindow;
        }
    }

    private int getCurrentLineAdjustedForSlop(Layout layout, int prevLine, float y) {
        final int trueLine = mEditText.getLineAtCoordinate(y);
        if (layout == null || prevLine > layout.getLineCount()
                || layout.getLineCount() <= 0 || prevLine < 0) {
            // Invalid parameters, just return whatever line is at y.
            return trueLine;
        }

        if (Math.abs(trueLine - prevLine) >= 2) {
            // Only stick to lines if we're within a line of the previous selection.
            return trueLine;
        }

        final int lineHeight = mEditText.getLineHeight();
        int slop = (int)(mLineSlopRatio * lineHeight);
        slop = Math.max(mLineChangeSlopMin,
                Math.min(mLineChangeSlopMax, lineHeight + slop)) - lineHeight;
        slop = Math.max(0, slop);

        final float verticalOffset = mEditText.viewportToContentVerticalOffset();
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

    void loadCursorDrawable() {
        if (mDrawableForCursor == null) {
            mDrawableForCursor = mEditText.getTextCursorDrawable();
        }
    }

    /** Controller for the insertion cursor. */
    class InsertionPointCursorController implements CursorController {
        private InsertionHandleView mHandle;
        // Tracks whether the cursor is currently being dragged.
        private boolean mIsDraggingCursor;
        // During a drag, tracks whether the user's finger has adjusted to be over the handle rather
        // than the cursor bar.
        private boolean mIsTouchSnappedToHandleDuringDrag;
        // During a drag, tracks the line of text where the cursor was last positioned.
        private int mPrevLineDuringDrag;

        public void onTouchEvent(MotionEvent event) {
            if (hasSelectionController() && getSelectionController().isCursorBeingModified()) {
                return;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
                        break;
                    }
                    if (mIsDraggingCursor) {
                        performCursorDrag(event);
                    } else if (mFlagCursorDragFromAnywhereEnabled
                            && mEditText.getLayout() != null
                            && mEditText.isFocused()
                            && mTouchState.isMovedEnoughForDrag()
                            && (mTouchState.getInitialDragDirectionXYRatio()
                            > mCursorDragDirectionMinXYRatio || mTouchState.isOnHandle())) {
                        startCursorDrag(event);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (mIsDraggingCursor) {
                        endCursorDrag(event);
                    }
                    break;
            }
        }

        private void positionCursorDuringDrag(MotionEvent event) {
            mPrevLineDuringDrag = getLineDuringDrag(event);
            int offset = mEditText.getOffsetAtCoordinate(mPrevLineDuringDrag, event.getX());
            int oldSelectionStart = mEditText.getSelectionStart();
            int oldSelectionEnd = mEditText.getSelectionEnd();
            if (offset == oldSelectionStart && offset == oldSelectionEnd) {
                return;
            }
            Selection.setSelection(mEditText.getText(), offset);
            updateCursorPosition();
            // (EW) prior to Oreo MR1, the AOSP version didn't perform haptic feedback, and since
            // that constant doesn't exist, there probably isn't much to do even if we wanted this
            // in old versions
            if (mHapticTextHandleEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                mEditText.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
            }
        }

        /**
         * Returns the line where the cursor should be positioned during a cursor drag. Rather than
         * simply returning the line directly at the touch position, this function has the following
         * additional logic:
         * 1) Apply some slop to avoid switching lines if the touch moves just slightly off the
         * current line.
         * 2) Allow the user's finger to slide down and "snap" to the handle to provide better
         * visibility of the cursor and text.
         */
        private int getLineDuringDrag(MotionEvent event) {
            final Layout layout = mEditText.getLayout();
            if (mPrevLineDuringDrag == UNSET_LINE) {
                return getCurrentLineAdjustedForSlop(layout, mPrevLineDuringDrag, event.getY());
            }
            // In case of touch through on handle (when isOnHandle() returns true), event.getY()
            // returns the midpoint of the cursor vertical bar, while event.getRawY() returns the
            // finger location on the screen. See {@link InsertionHandleView#touchThrough}.
            final float fingerY = mTouchState.isOnHandle()
                    ? event.getRawY() - mEditText.getLocationOnScreen()[1]
                    : event.getY();
            final float cursorY = fingerY - getHandle().getIdealFingerToCursorOffset();
            int line = getCurrentLineAdjustedForSlop(layout, mPrevLineDuringDrag, cursorY);
            if (mIsTouchSnappedToHandleDuringDrag) {
                // Just returns the line hit by cursor Y when already snapped.
                return line;
            }
            if (line < mPrevLineDuringDrag) {
                // The cursor Y aims too high & not yet snapped, check the finger Y.
                // If finger Y is moving downwards, don't jump to lower line (until snap).
                // If finger Y is moving upwards, can jump to upper line.
                return Math.min(mPrevLineDuringDrag,
                        getCurrentLineAdjustedForSlop(layout, mPrevLineDuringDrag, fingerY));
            }
            // The cursor Y aims not too high, so snap!
            mIsTouchSnappedToHandleDuringDrag = true;
            if (EditText.DEBUG_CURSOR) {
                logCursor("InsertionPointCursorController",
                        "snapped touch to handle: fingerY=%d, cursorY=%d, mLastLine=%d, line=%d",
                        (int) fingerY, (int) cursorY, mPrevLineDuringDrag, line);
            }
            return line;
        }

        private void startCursorDrag(MotionEvent event) {
            if (EditText.DEBUG_CURSOR) {
                logCursor("InsertionPointCursorController", "start cursor drag");
            }
            mIsDraggingCursor = true;
            mIsTouchSnappedToHandleDuringDrag = false;
            mPrevLineDuringDrag = UNSET_LINE;
            // We don't want the parent scroll/long-press handlers to take over while dragging.
            mEditText.getParent().requestDisallowInterceptTouchEvent(true);
            mEditText.cancelLongPress();
            // Update the cursor position.
            positionCursorDuringDrag(event);
            // Show the cursor handle.
            show();
            getHandle().removeHiderCallback();
            // TODO(b/146555651): Figure out if suspendBlink() should be called here.
        }

        private void performCursorDrag(MotionEvent event) {
            positionCursorDuringDrag(event);
        }

        private void endCursorDrag(MotionEvent event) {
            if (EditText.DEBUG_CURSOR) {
                logCursor("InsertionPointCursorController", "end cursor drag");
            }
            mIsDraggingCursor = false;
            mIsTouchSnappedToHandleDuringDrag = false;
            mPrevLineDuringDrag = UNSET_LINE;
            // Set the handle to be hidden after a delay.
            getHandle().hideAfterDelay();
            // We're no longer dragging, so let the parent receive events.
            mEditText.getParent().requestDisallowInterceptTouchEvent(false);
        }

        public void show() {
            getHandle().show();

            final long durationSinceCutOrCopy =
                    SystemClock.uptimeMillis() - EditText.sLastCutCopyOrTextChangedTime;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                    && durationSinceCutOrCopy < RECENT_CUT_COPY_DURATION_MS) {
                getHandle().showActionPopupWindow(0);
            }

            if (mInsertionActionModeRunnable != null) {
                if (mIsDraggingCursor
                        || mTouchState.isMultiTap()
                        || isCursorInsideEasyCorrectionSpan()) {
                    // Cancel the runnable for showing the floating toolbar.
                    mEditText.removeCallbacks(mInsertionActionModeRunnable);
                }
            }

            // If the user recently performed a Cut or Copy action, we want to show the floating
            // toolbar even for a single tap.
            if (!mIsDraggingCursor
                    && !mTouchState.isMultiTap()
                    && !isCursorInsideEasyCorrectionSpan()
                    && (durationSinceCutOrCopy < RECENT_CUT_COPY_DURATION_MS)) {
                if (mTextActionMode == null) {
                    if (mInsertionActionModeRunnable == null) {
                        mInsertionActionModeRunnable = new Runnable() {
                            @Override
                            public void run() {
                                startInsertionActionMode();
                            }
                        };
                    }
                    mEditText.postDelayed(
                            mInsertionActionModeRunnable,
                            ViewConfiguration.getDoubleTapTimeout() + 1);
                }
            }

            if (!mIsDraggingCursor) {
                getHandle().hideAfterDelay();
            }

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
            if (mHandle == null) {
                loadHandleDrawables(false /* overwrite */);
                mHandle = new InsertionHandleView(mSelectHandleCenter);
            }
            return mHandle;
        }

        private void reloadHandleDrawable() {
            if (mHandle == null) {
                // No need to reload, the potentially new drawable will
                // be used when the handle is created.
                return;
            }
            mHandle.setDrawables(mSelectHandleCenter, mSelectHandleCenter);
        }

        @Override
        public void onDetached() {
            final ViewTreeObserver observer = mEditText.getViewTreeObserver();
            observer.removeOnTouchModeChangeListener(this);

            if (mHandle != null) mHandle.onDetached();
        }

        @Override
        public boolean isCursorBeingModified() {
            return mIsDraggingCursor || (mHandle != null && mHandle.isDragging());
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

    /** Controller for selection. */
    class SelectionModifierCursorController implements CursorController {
        private static final int DELAY_BEFORE_REPLACE_ACTION = 200; // milliseconds

        // The cursor controller handles, lazily created when shown.
        private SelectionHandleView mStartHandle;
        private SelectionHandleView mEndHandle;
        // The offsets of that last touch down event. Remembered to start selection there.
        private int mMinTouchOffset, mMaxTouchOffset;

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

        SelectionModifierCursorController() {
            resetTouchOffsets();
        }

        public void show() {
            if (mEditText.isInBatchEditMode()) {
                return;
            }
            loadHandleDrawables(false /* overwrite */);
            initHandles();
        }

        private void initHandles() {
            // Lazy object creation has to be done before updatePosition() is called.
            if (mStartHandle == null) {
                mStartHandle = new SelectionHandleView(mSelectHandleLeft, mSelectHandleRight,
                        R.id.selection_start_handle,
                        HANDLE_TYPE_SELECTION_START);
            }
            if (mEndHandle == null) {
                mEndHandle = new SelectionHandleView(mSelectHandleRight, mSelectHandleLeft,
                        R.id.selection_end_handle,
                        HANDLE_TYPE_SELECTION_END);
            }

            mStartHandle.show();
            mEndHandle.show();

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                // Make sure both left and right handles share the same ActionPopupWindow (so that
                // moving any of the handles hides the action popup).
                mStartHandle.showActionPopupWindow(DELAY_BEFORE_REPLACE_ACTION);
                mEndHandle.setActionPopupWindow(mStartHandle.getActionPopupWindow());
            }

            hideInsertionPointCursorController();
        }

        private void reloadHandleDrawables() {
            if (mStartHandle == null) {
                // No need to reload, the potentially new drawables will
                // be used when the handles are created.
                return;
            }
            mStartHandle.setDrawables(mSelectHandleLeft, mSelectHandleRight);
            mEndHandle.setDrawables(mSelectHandleRight, mSelectHandleLeft);
        }

        public void hide() {
            if (mStartHandle != null) mStartHandle.hide();
            if (mEndHandle != null) mEndHandle.hide();
        }

        public void enterDrag(int dragAcceleratorMode) {
            if (EditText.DEBUG_CURSOR) {
                logCursor("SelectionModifierCursorController: enterDrag",
                        "starting selection drag: mode=%s", dragAcceleratorMode);
            }

            // Just need to init the handles / hide insertion cursor.
            show();
            mDragAcceleratorMode = dragAcceleratorMode;
            // Start location of selection.
            mStartOffset = mEditText.getOffsetForPosition(mTouchState.getLastDownX(),
                    mTouchState.getLastDownY());
            mLineSelectionIsOn = mEditText.getLineAtCoordinate(mTouchState.getLastDownY());
            // Don't show the handles until user has lifted finger.
            hide();

            // This stops scrolling parents from intercepting the touch event, allowing
            // the user to continue dragging across the screen to select text; EditText will
            // scroll as necessary.
            mEditText.getParent().requestDisallowInterceptTouchEvent(true);
            mEditText.cancelLongPress();
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
                        mMinTouchOffset = mMaxTouchOffset = mEditText.getOffsetForPosition(
                                eventX, eventY);

                        // Double tap detection
                        if (mGestureStayedInTapRegion
                                && mTouchState.isMultiTapInSameArea()
                                && (isMouse || isPositionOnText(eventX, eventY)
                                || mTouchState.isOnHandle())) {
                            if (EditText.DEBUG_CURSOR) {
                                logCursor("SelectionModifierCursorController: onTouchEvent",
                                        "ACTION_DOWN: select and start drag");
                            }
                            if (mTouchState.isDoubleTap()) {
                                selectCurrentWordAndStartDrag();
                            } else if (mTouchState.isTripleClick()) {
                                selectCurrentParagraphAndStartDrag();
                            }
                            mDiscardNextActionUp = true;
                        }
                        mGestureStayedInTapRegion = true;
                        mHaventMovedEnoughToStartDrag = true;
                    }
                    break;

                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_POINTER_UP:
                    // Handle multi-point gestures. Keep min and max offset positions.
                    // Only activated for devices that correctly handle multi-touch.
                    if (mEditText.getContext().getPackageManager().hasSystemFeature(
                            PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT)) {
                        updateMinAndMaxOffsets(event);
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (mGestureStayedInTapRegion) {
                        final ViewConfiguration viewConfig = ViewConfiguration.get(
                                mEditText.getContext());
                        // (EW) the AOSP version used ViewConfiguration#getScaledDoubleTapTouchSlop
                        // instead of ViewConfiguration#getScaledTouchSlop, but at least as of S,
                        // ViewConfiguration#ViewConfiguration(Context) sets mDoubleTapTouchSlop
                        // (what ViewConfiguration#getScaledDoubleTapTouchSlop returns) to
                        // mTouchSlop, which is what is returned in
                        // ViewConfiguration#getScaledTouchSlop, so at least as of S, this is
                        // equivalent to what the AOSP does.
                        mGestureStayedInTapRegion = EditorTouchState.isDistanceWithin(
                                mTouchState.getLastDownX(), mTouchState.getLastDownY(),
                                eventX, eventY, viewConfig.getScaledTouchSlop());
                    }

                    if (mHaventMovedEnoughToStartDrag) {
                        mHaventMovedEnoughToStartDrag = !mTouchState.isMovedEnoughForDrag();
                    }

                    if (isMouse && !isDragAcceleratorActive()) {
                        final int offset = mEditText.getOffsetForPosition(eventX, eventY);
                        if (mEditText.hasSelection()
                                && (!mHaventMovedEnoughToStartDrag || mStartOffset != offset)
                                && offset >= mEditText.getSelectionStart()
                                && offset <= mEditText.getSelectionEnd()) {
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
                    if (EditText.DEBUG_CURSOR) {
                        logCursor("SelectionModifierCursorController: onTouchEvent", "ACTION_UP");
                    }
                    if (!isDragAcceleratorActive()) {
                        break;
                    }
                    updateSelection(event);

                    // No longer dragging to select text, let the parent intercept events.
                    mEditText.getParent().requestDisallowInterceptTouchEvent(false);

                    // No longer the first dragging motion, reset.
                    resetDragAcceleratorState();

                    if (mEditText.hasSelection()) {
                        // Drag selection should not be adjusted by the text classifier.
                        startSelectionActionModeAsync();
                    }
                    break;
            }
        }

        private void updateSelection(MotionEvent event) {
            if (mEditText.getLayout() != null) {
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
         * If the EditText allows text selection, selects the current paragraph and starts a drag.
         *
         * @return true if the drag was started.
         */
        private boolean selectCurrentParagraphAndStartDrag() {
            if (mInsertionActionModeRunnable != null) {
                mEditText.removeCallbacks(mInsertionActionModeRunnable);
            }
            stopTextActionMode();
            if (!selectCurrentParagraph()) {
                return false;
            }
            enterDrag(SelectionModifierCursorController.DRAG_ACCELERATOR_MODE_PARAGRAPH);
            return true;
        }

        private void updateCharacterBasedSelection(MotionEvent event) {
            final int offset = mEditText.getOffsetForPosition(event.getX(), event.getY());
            updateSelectionInternal(mStartOffset, offset,
                    event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN));
        }

        private void updateWordBasedSelection(MotionEvent event) {
            if (mHaventMovedEnoughToStartDrag) {
                return;
            }
            final boolean isMouse = event.isFromSource(InputDevice.SOURCE_MOUSE);
            final ViewConfiguration viewConfig = ViewConfiguration.get(
                    mEditText.getContext());
            final float eventX = event.getX();
            final float eventY = event.getY();
            final int currLine;
            if (isMouse) {
                // No need to offset the y coordinate for mouse input.
                currLine = mEditText.getLineAtCoordinate(eventY);
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

                currLine = getCurrentLineAdjustedForSlop(mEditText.getLayout(), mLineSelectionIsOn,
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
            int offset = mEditText.getOffsetAtCoordinate(currLine, eventX);
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
            final int offset = mEditText.getOffsetForPosition(event.getX(), event.getY());

            final int start = Math.min(offset, mStartOffset);
            final int end = Math.max(offset, mStartOffset);
            final long paragraphsRange = getParagraphsRange(start, end);
            final int selectionStart = HiddenTextUtils.unpackRangeStartFromLong(paragraphsRange);
            final int selectionEnd = HiddenTextUtils.unpackRangeEndFromLong(paragraphsRange);
            updateSelectionInternal(selectionStart, selectionEnd,
                    event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN));
        }

        private void updateSelectionInternal(int selectionStart, int selectionEnd,
                                             boolean fromTouchScreen) {
            final boolean performHapticFeedback = fromTouchScreen && mHapticTextHandleEnabled
                    && ((mEditText.getSelectionStart() != selectionStart)
                            || (mEditText.getSelectionEnd() != selectionEnd));
            Selection.setSelection(mEditText.getText(), selectionStart, selectionEnd);
            // (EW) prior to Oreo MR1, the AOSP version didn't perform haptic feedback, and since
            // that constant doesn't exist, there probably isn't much to do even if we wanted this
            // in old versions
            if (performHapticFeedback && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                mEditText.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
            }
        }

        private void updateMinAndMaxOffsets(MotionEvent event) {
            int pointerCount = event.getPointerCount();
            for (int index = 0; index < pointerCount; index++) {
                int offset = mEditText.getOffsetForPosition(event.getX(index), event.getY(index));
                if (offset < mMinTouchOffset) mMinTouchOffset = offset;
                if (offset > mMaxTouchOffset) mMaxTouchOffset = offset;
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
            resetDragAcceleratorState();
        }

        private void resetDragAcceleratorState() {
            mStartOffset = -1;
            mDragAcceleratorMode = DRAG_ACCELERATOR_MODE_INACTIVE;
            mSwitchedLines = false;
            final int selectionStart = mEditText.getSelectionStart();
            final int selectionEnd = mEditText.getSelectionEnd();
            if (selectionStart < 0 || selectionEnd < 0) {
                Selection.removeSelection(mEditText.getText());
            } else if (selectionStart > selectionEnd) {
                Selection.setSelection(mEditText.getText(),
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
            final ViewTreeObserver observer = mEditText.getViewTreeObserver();
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

    /**
     * Loads the insertion and selection handle Drawables from EditText. If the handle
     * drawables are already loaded, do not overwrite them unless the method parameter
     * is set to true. This logic is required to avoid overwriting Drawables assigned
     * to mSelectHandle[Center/Left/Right] by developers using reflection, unless they
     * explicitly call the setters in EditText.
     *
     * @param overwrite whether to overwrite already existing nonnull Drawables
     */
    void loadHandleDrawables(final boolean overwrite) {
        if (mSelectHandleCenter == null || overwrite) {
            mSelectHandleCenter = mEditText.getTextSelectHandle();
            if (hasInsertionController()) {
                getInsertionController().reloadHandleDrawable();
            }
        }

        if (mSelectHandleLeft == null || mSelectHandleRight == null || overwrite) {
            mSelectHandleLeft = mEditText.getTextSelectHandleLeft();
            mSelectHandleRight = mEditText.getTextSelectHandleRight();
            if (hasSelectionController()) {
                getSelectionController().reloadHandleDrawables();
            }
        }
    }

    private class CorrectionHighlighter {
        private final Path mPath = new Path();
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int mStart, mEnd;
        private long mFadingStartTime;
        private RectF mTempRectF;
        private static final int FADE_OUT_DURATION = 400;

        public CorrectionHighlighter() {
            // (EW) the AOSP version calls Paint#setCompatibilityScaling using
            // Resources#getCompatibilityInfo, both of which are not accessible from apps. I don't
            // think there is really anything to do, and I'm not certain if it's really necessary.
            mPaint.setStyle(Paint.Style.FILL);
        }

        public void highlight(CorrectionInfo info) {
            mStart = info.getOffset();
            mEnd = mStart + info.getNewText().length();
            mFadingStartTime = SystemClock.uptimeMillis();

            if (mStart < 0 || mEnd < 0) {
                stopAnimation();
            }
        }

        public void draw(Canvas canvas, int cursorOffsetVertical) {
            if (updatePath() && updatePaint()) {
                if (cursorOffsetVertical != 0) {
                    canvas.translate(0, cursorOffsetVertical);
                }

                canvas.drawPath(mPath, mPaint);

                if (cursorOffsetVertical != 0) {
                    canvas.translate(0, -cursorOffsetVertical);
                }
                invalidate(true); // TODO invalidate cursor region only
            } else {
                stopAnimation();
                invalidate(false); // TODO invalidate cursor region only
            }
        }

        private boolean updatePaint() {
            final long duration = SystemClock.uptimeMillis() - mFadingStartTime;
            if (duration > FADE_OUT_DURATION) return false;

            final float coef = 1.0f - (float) duration / FADE_OUT_DURATION;
            final int highlightColorAlpha = Color.alpha(mEditText.mHighlightColor);
            final int color = (mEditText.mHighlightColor & 0x00FFFFFF)
                    + ((int) (highlightColorAlpha * coef) << 24);
            mPaint.setColor(color);
            return true;
        }

        private boolean updatePath() {
            final Layout layout = mEditText.getLayout();
            if (layout == null) return false;

            // Update in case text is edited while the animation is run
            final int length = mEditText.getText().length();
            int start = Math.min(length, mStart);
            int end = Math.min(length, mEnd);

            mPath.reset();
            layout.getSelectionPath(start, end, mPath);
            return true;
        }

        private void invalidate(boolean delayed) {
            if (mEditText.getLayout() == null) return;

            if (mTempRectF == null) mTempRectF = new RectF();
            mPath.computeBounds(mTempRectF, false);

            int left = mEditText.getCompoundPaddingLeft();
            int top = mEditText.getExtendedPaddingTop() + mEditText.getVerticalOffset(true);

            if (delayed) {
                mEditText.postInvalidateOnAnimation(
                        left + (int) mTempRectF.left, top + (int) mTempRectF.top,
                        left + (int) mTempRectF.right, top + (int) mTempRectF.bottom);
            } else {
                mEditText.postInvalidate((int) mTempRectF.left, (int) mTempRectF.top,
                        (int) mTempRectF.right, (int) mTempRectF.bottom);
            }
        }

        private void stopAnimation() {
            Editor.this.mCorrectionHighlighter = null;
        }
    }

    static class InputContentType {
        int imeOptions = EditorInfo.IME_NULL;
        String privateImeOptions;
        CharSequence imeActionLabel;
        int imeActionId;
        Bundle extras;
        OnEditorActionListener onEditorActionListener;
        boolean enterDown;
        LocaleList imeHintLocales;
    }

    static class InputMethodState {
        // (EW) only used prior to Lollipop
        Rect mCursorRectInWindow = new Rect();
        RectF mTmpRectF = new RectF();
        float[] mTmpOffset = new float[2];

        ExtractedTextRequest mExtractedTextRequest;
        final ExtractedText mExtractedText = new ExtractedText();
        int mBatchEditNesting;
        boolean mCursorChanged;
        boolean mSelectionModeChanged;
        boolean mContentChanged;
        int mChangedStart, mChangedEnd, mChangedDelta;
    }

    /**
     * @return True iff (start, end) is a valid range within the text.
     */
    private static boolean isValidRange(CharSequence text, int start, int end) {
        return 0 <= start && start <= end && end <= text.length();
    }

    /**
     * An InputFilter that monitors text input to maintain undo history. It does not modify the
     * text being typed (and hence always returns null from the filter() method).
     *
     * TODO: Make this span aware.
     */
    public static class UndoInputFilter implements InputFilter {
        private final Editor mEditor;

        // Whether the current filter pass is directly caused by an end-user text edit.
        private boolean mIsUserEdit;

        // Whether the text field is handling an IME composition. Must be parceled in case the user
        // rotates the screen during composition.
        private boolean mHasComposition;

        // Whether the user is expanding or shortening the text
        private boolean mExpanding;

        // Whether the previous edit operation was in the current batch edit.
        private boolean mPreviousOperationWasInSameBatchEdit;

        public UndoInputFilter(Editor editor) {
            mEditor = editor;
        }

        public void saveInstanceState(Parcel parcel) {
            parcel.writeInt(mIsUserEdit ? 1 : 0);
            parcel.writeInt(mHasComposition ? 1 : 0);
            parcel.writeInt(mExpanding ? 1 : 0);
            parcel.writeInt(mPreviousOperationWasInSameBatchEdit ? 1 : 0);
        }

        public void restoreInstanceState(Parcel parcel) {
            mIsUserEdit = parcel.readInt() != 0;
            mHasComposition = parcel.readInt() != 0;
            mExpanding = parcel.readInt() != 0;
            mPreviousOperationWasInSameBatchEdit = parcel.readInt() != 0;
        }

        /**
         * Signals that a user-triggered edit is starting.
         */
        public void beginBatchEdit() {
            if (DEBUG_UNDO) Log.d(TAG, "beginBatchEdit");
            mIsUserEdit = true;
        }

        public void endBatchEdit() {
            if (DEBUG_UNDO) Log.d(TAG, "endBatchEdit");
            mIsUserEdit = false;
            mPreviousOperationWasInSameBatchEdit = false;
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end,
                                   Spanned dest, int dstart, int dend) {
            if (DEBUG_UNDO) {
                Log.d(TAG, "filter: source=" + source + " (" + start + "-" + end + ") "
                        + "dest=" + dest + " (" + dstart + "-" + dend + ")");
            }

            // Check to see if this edit should be tracked for undo.
            if (!canUndoEdit(source, start, end, dest, dstart, dend)) {
                return null;
            }

            final boolean hadComposition = mHasComposition;
            mHasComposition = isComposition(source);
            final boolean wasExpanding = mExpanding;
            boolean shouldCreateSeparateState = false;
            if ((end - start) != (dend - dstart)) {
                mExpanding = (end - start) > (dend - dstart);
                if (hadComposition && mExpanding != wasExpanding) {
                    shouldCreateSeparateState = true;
                }
            }

            // Handle edit.
            handleEdit(source, start, end, dest, dstart, dend, shouldCreateSeparateState);
            return null;
        }

        void freezeLastEdit() {
            mEditor.mUndoManager.beginUpdate("Edit text");
            EditOperation lastEdit = getLastEdit();
            if (lastEdit != null) {
                lastEdit.mFrozen = true;
            }
            mEditor.mUndoManager.endUpdate();
        }

        @Retention(RetentionPolicy.SOURCE)
        @IntDef(value = {
                MERGE_EDIT_MODE_FORCE_MERGE,
                MERGE_EDIT_MODE_NEVER_MERGE,
                MERGE_EDIT_MODE_NORMAL
        })
        private @interface MergeMode {}
        private static final int MERGE_EDIT_MODE_FORCE_MERGE = 0;
        private static final int MERGE_EDIT_MODE_NEVER_MERGE = 1;
        /** Use {@link EditOperation#mergeWith} to merge */
        private static final int MERGE_EDIT_MODE_NORMAL = 2;

        private void handleEdit(CharSequence source, int start, int end,
                                Spanned dest, int dstart, int dend,
                                boolean shouldCreateSeparateState) {
            // An application may install a TextWatcher to provide additional modifications after
            // the initial input filters run (e.g. a credit card formatter that adds spaces to a
            // string). This results in multiple filter() calls for what the user considers to be
            // a single operation. Always undo the whole set of changes in one step.
            @MergeMode
            final int mergeMode;
            if (isInTextWatcher() || mPreviousOperationWasInSameBatchEdit) {
                mergeMode = MERGE_EDIT_MODE_FORCE_MERGE;
            } else if (shouldCreateSeparateState) {
                mergeMode = MERGE_EDIT_MODE_NEVER_MERGE;
            } else {
                mergeMode = MERGE_EDIT_MODE_NORMAL;
            }
            // Build a new operation with all the information from this edit.
            String newText = TextUtils.substring(source, start, end);
            String oldText = TextUtils.substring(dest, dstart, dend);
            EditOperation edit = new EditOperation(mEditor, oldText, dstart, newText,
                    mHasComposition);
            if (mHasComposition && TextUtils.equals(edit.mNewText, edit.mOldText)) {
                return;
            }
            recordEdit(edit, mergeMode);
        }

        private EditOperation getLastEdit() {
            final UndoManager um = mEditor.mUndoManager;
            return um.getLastOperation(
                    EditOperation.class, mEditor.mUndoOwner, UndoManager.MERGE_MODE_UNIQUE);
        }
        /**
         * Fetches the last undo operation and checks to see if a new edit should be merged into it.
         * If forceMerge is true then the new edit is always merged.
         */
        private void recordEdit(EditOperation edit, @MergeMode int mergeMode) {
            // Fetch the last edit operation and attempt to merge in the new edit.
            final UndoManager um = mEditor.mUndoManager;
            um.beginUpdate("Edit text");
            EditOperation lastEdit = getLastEdit();
            if (lastEdit == null) {
                // Add this as the first edit.
                if (DEBUG_UNDO) Log.d(TAG, "filter: adding first op " + edit);
                um.addOperation(edit, UndoManager.MERGE_MODE_NONE);
            } else if (mergeMode == MERGE_EDIT_MODE_FORCE_MERGE) {
                // Forced merges take priority because they could be the result of a non-user-edit
                // change and this case should not create a new undo operation.
                if (DEBUG_UNDO) Log.d(TAG, "filter: force merge " + edit);
                lastEdit.forceMergeWith(edit);
            } else if (!mIsUserEdit) {
                // An application directly modified the Editable outside of a text edit. Treat this
                // as a new change and don't attempt to merge.
                if (DEBUG_UNDO) Log.d(TAG, "non-user edit, new op " + edit);
                um.commitState(mEditor.mUndoOwner);
                um.addOperation(edit, UndoManager.MERGE_MODE_NONE);
            } else if (mergeMode == MERGE_EDIT_MODE_NORMAL && lastEdit.mergeWith(edit)) {
                // Merge succeeded, nothing else to do.
                if (DEBUG_UNDO) Log.d(TAG, "filter: merge succeeded, created " + lastEdit);
            } else {
                // Could not merge with the last edit, so commit the last edit and add this edit.
                if (DEBUG_UNDO) Log.d(TAG, "filter: merge failed, adding " + edit);
                um.commitState(mEditor.mUndoOwner);
                um.addOperation(edit, UndoManager.MERGE_MODE_NONE);
            }
            mPreviousOperationWasInSameBatchEdit = mIsUserEdit;
            um.endUpdate();
        }

        private boolean canUndoEdit(CharSequence source, int start, int end,
                                    Spanned dest, int dstart, int dend) {
            if (!mEditor.mAllowUndo) {
                if (DEBUG_UNDO) Log.d(TAG, "filter: undo is disabled");
                return false;
            }

            if (mEditor.mUndoManager.isInUndo()) {
                if (DEBUG_UNDO) Log.d(TAG, "filter: skipping, currently performing undo/redo");
                return false;
            }

            // Text filters run before input operations are applied. However, some input operations
            // are invalid and will throw exceptions when applied. This is common in tests. Don't
            // attempt to undo invalid operations.
            if (!isValidRange(source, start, end) || !isValidRange(dest, dstart, dend)) {
                if (DEBUG_UNDO) Log.d(TAG, "filter: invalid op");
                return false;
            }

            // Earlier filters can rewrite input to be a no-op, for example due to a length limit
            // on an input field. Skip no-op changes.
            if (start == end && dstart == dend) {
                if (DEBUG_UNDO) Log.d(TAG, "filter: skipping no-op");
                return false;
            }

            return true;
        }

        private static boolean isComposition(CharSequence source) {
            if (!(source instanceof Spannable)) {
                return false;
            }
            // This is a composition edit if the source has a non-zero-length composing span.
            Spannable text = (Spannable) source;
            int composeBegin = EditableInputConnection.getComposingSpanStart(text);
            int composeEnd = EditableInputConnection.getComposingSpanEnd(text);
            return composeBegin < composeEnd;
        }

        private boolean isInTextWatcher() {
            CharSequence text = mEditor.mEditText.getText();
            if (text instanceof SpannableStringBuilder) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    return ((SpannableStringBuilder) text).getTextWatcherDepth() > 0;
                } else {
                    //FUTURE: (EW) AOSP EditText didn't support undo in this version, so there is
                    // nothing there to copy from for this case. SpannableStringBuilder doesn't seem
                    // to even internally track this, so there doesn't seem to be any other way to
                    // do this check. This is incorrect, but I'm not sure if it actually has a bad
                    // impact. See if there is actually something to do to fix this if this does
                    // cause a real problem.
                    return false;
                }
            }
            return false;
        }
    }

    /**
     * An operation to undo a single "edit" to a text view.
     */
    public static class EditOperation extends UndoOperation<Editor> {
        private static final int TYPE_INSERT = 0;
        private static final int TYPE_DELETE = 1;
        private static final int TYPE_REPLACE = 2;

        private int mType;
        private String mOldText;
        private String mNewText;
        private int mStart;

        private final int mOldCursorPos;
        private int mNewCursorPos;
        private boolean mFrozen;
        private boolean mIsComposition;

        /**
         * Constructs an edit operation from a text input operation on editor that replaces the
         * oldText starting at dstart with newText.
         */
        public EditOperation(Editor editor, String oldText, int dstart, String newText,
                             boolean isComposition) {
            super(editor.mUndoOwner);
            mOldText = oldText;
            mNewText = newText;

            // Determine the type of the edit.
            if (mNewText.length() > 0 && mOldText.length() == 0) {
                mType = TYPE_INSERT;
            } else if (mNewText.length() == 0 && mOldText.length() > 0) {
                mType = TYPE_DELETE;
            } else {
                mType = TYPE_REPLACE;
            }

            mStart = dstart;
            // Store cursor data.
            mOldCursorPos = editor.mEditText.getSelectionStart();
            mNewCursorPos = dstart + mNewText.length();
            mIsComposition = isComposition;
        }

        public EditOperation(Parcel src, ClassLoader loader) {
            super(src, loader);
            mType = src.readInt();
            mOldText = src.readString();
            mNewText = src.readString();
            mStart = src.readInt();
            mOldCursorPos = src.readInt();
            mNewCursorPos = src.readInt();
            mFrozen = src.readInt() == 1;
            mIsComposition = src.readInt() == 1;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mType);
            dest.writeString(mOldText);
            dest.writeString(mNewText);
            dest.writeInt(mStart);
            dest.writeInt(mOldCursorPos);
            dest.writeInt(mNewCursorPos);
            dest.writeInt(mFrozen ? 1 : 0);
            dest.writeInt(mIsComposition ? 1 : 0);
        }

        private int getNewTextEnd() {
            return mStart + mNewText.length();
        }

        private int getOldTextEnd() {
            return mStart + mOldText.length();
        }

        @Override
        public void commit() {
        }

        @Override
        public void undo() {
            if (DEBUG_UNDO) Log.d(TAG, "undo");
            // Remove the new text and insert the old.
            Editor editor = getOwnerData();
            Editable text = editor.mEditText.getText();
            modifyText(text, mStart, getNewTextEnd(), mOldText, mStart, mOldCursorPos);
        }

        @Override
        public void redo() {
            if (DEBUG_UNDO) Log.d(TAG, "redo");
            // Remove the old text and insert the new.
            Editor editor = getOwnerData();
            Editable text = editor.mEditText.getText();
            modifyText(text, mStart, getOldTextEnd(), mNewText, mStart, mNewCursorPos);
        }

        /**
         * Attempts to merge this existing operation with a new edit.
         * @param edit The new edit operation.
         * @return If the merge succeeded, returns true. Otherwise returns false and leaves this
         * object unchanged.
         */
        private boolean mergeWith(EditOperation edit) {
            if (DEBUG_UNDO) {
                Log.d(TAG, "mergeWith old " + this);
                Log.d(TAG, "mergeWith new " + edit);
            }

            if (mFrozen) {
                return false;
            }

            switch (mType) {
                case TYPE_INSERT:
                    return mergeInsertWith(edit);
                case TYPE_DELETE:
                    return mergeDeleteWith(edit);
                case TYPE_REPLACE:
                    return mergeReplaceWith(edit);
                default:
                    return false;
            }
        }

        private boolean mergeInsertWith(EditOperation edit) {
            if (edit.mType == TYPE_INSERT) {
                // Merge insertions that are contiguous even when it's frozen.
                if (getNewTextEnd() != edit.mStart) {
                    return false;
                }
                mNewText += edit.mNewText;
                mNewCursorPos = edit.mNewCursorPos;
                mFrozen = edit.mFrozen;
                mIsComposition = edit.mIsComposition;
                return true;
            }
            if (mIsComposition && edit.mType == TYPE_REPLACE
                    && mStart <= edit.mStart && getNewTextEnd() >= edit.getOldTextEnd()) {
                // Merge insertion with replace as they can be single insertion.
                mNewText = mNewText.substring(0, edit.mStart - mStart) + edit.mNewText
                        + mNewText.substring(edit.getOldTextEnd() - mStart, mNewText.length());
                mNewCursorPos = edit.mNewCursorPos;
                mIsComposition = edit.mIsComposition;
                return true;
            }
            return false;
        }

        // TODO: Support forward delete.
        private boolean mergeDeleteWith(EditOperation edit) {
            // Only merge continuous deletes.
            if (edit.mType != TYPE_DELETE) {
                return false;
            }
            // Only merge deletions that are contiguous.
            if (mStart != edit.getOldTextEnd()) {
                return false;
            }
            mStart = edit.mStart;
            mOldText = edit.mOldText + mOldText;
            mNewCursorPos = edit.mNewCursorPos;
            mIsComposition = edit.mIsComposition;
            return true;
        }

        private boolean mergeReplaceWith(EditOperation edit) {
            if (edit.mType == TYPE_INSERT && getNewTextEnd() == edit.mStart) {
                // Merge with adjacent insert.
                mNewText += edit.mNewText;
                mNewCursorPos = edit.mNewCursorPos;
                return true;
            }
            if (!mIsComposition) {
                return false;
            }
            if (edit.mType == TYPE_DELETE && mStart <= edit.mStart
                    && getNewTextEnd() >= edit.getOldTextEnd()) {
                // Merge with delete as they can be single operation.
                mNewText = mNewText.substring(0, edit.mStart - mStart)
                        + mNewText.substring(edit.getOldTextEnd() - mStart, mNewText.length());
                if (mNewText.isEmpty()) {
                    mType = TYPE_DELETE;
                }
                mNewCursorPos = edit.mNewCursorPos;
                mIsComposition = edit.mIsComposition;
                return true;
            }
            if (edit.mType == TYPE_REPLACE && mStart == edit.mStart
                    && TextUtils.equals(mNewText, edit.mOldText)) {
                // Merge with the replace that replaces the same region.
                mNewText = edit.mNewText;
                mNewCursorPos = edit.mNewCursorPos;
                mIsComposition = edit.mIsComposition;
                return true;
            }
            return false;
        }

        /**
         * Forcibly creates a single merged edit operation by simulating the entire text
         * contents being replaced.
         */
        public void forceMergeWith(EditOperation edit) {
            if (DEBUG_UNDO) Log.d(TAG, "forceMerge");
            if (mergeWith(edit)) {
                return;
            }
            Editor editor = getOwnerData();

            // Copy the text of the current field.
            // NOTE: Using StringBuilder instead of SpannableStringBuilder would be somewhat faster,
            // but would require two parallel implementations of modifyText() because Editable and
            // StringBuilder do not share an interface for replace/delete/insert.
            Editable editable = editor.mEditText.getText();
            Editable originalText = new SpannableStringBuilder(editable.toString());

            // Roll back the last operation.
            modifyText(originalText, mStart, getNewTextEnd(), mOldText, mStart, mOldCursorPos);

            // Clone the text again and apply the new operation.
            Editable finalText = new SpannableStringBuilder(editable.toString());
            modifyText(finalText, edit.mStart, edit.getOldTextEnd(),
                    edit.mNewText, edit.mStart, edit.mNewCursorPos);

            // Convert this operation into a replace operation.
            mType = TYPE_REPLACE;
            mNewText = finalText.toString();
            mOldText = originalText.toString();
            mStart = 0;
            mNewCursorPos = edit.mNewCursorPos;
            mIsComposition = edit.mIsComposition;
            // mOldCursorPos is unchanged.
        }

        private static void modifyText(Editable text, int deleteFrom, int deleteTo,
                                       CharSequence newText, int newTextInsertAt, int newCursorPos) {
            // Apply the edit if it is still valid.
            if (isValidRange(text, deleteFrom, deleteTo)
                    && newTextInsertAt <= text.length() - (deleteTo - deleteFrom)) {
                if (deleteFrom != deleteTo) {
                    text.delete(deleteFrom, deleteTo);
                }
                if (newText.length() != 0) {
                    text.insert(newTextInsertAt, newText);
                }
            }
            // Restore the cursor position. If there wasn't an old cursor (newCursorPos == -1) then
            // don't explicitly set it and rely on SpannableStringBuilder to position it.
            // TODO: Select all the text that was undone.
            if (0 <= newCursorPos && newCursorPos <= text.length()) {
                Selection.setSelection(text, newCursorPos);
            }
        }

        private String getTypeString() {
            switch (mType) {
                case TYPE_INSERT:
                    return "insert";
                case TYPE_DELETE:
                    return "delete";
                case TYPE_REPLACE:
                    return "replace";
                default:
                    return "";
            }
        }

        @Override
        public String toString() {
            return "[mType=" + getTypeString() + ", "
                    + "mOldText=" + mOldText + ", "
                    + "mNewText=" + mNewText + ", "
                    + "mStart=" + mStart + ", "
                    + "mOldCursorPos=" + mOldCursorPos + ", "
                    + "mNewCursorPos=" + mNewCursorPos + ", "
                    + "mFrozen=" + mFrozen + ", "
                    + "mIsComposition=" + mIsComposition + "]";
        }

        public static final Parcelable.ClassLoaderCreator<EditOperation> CREATOR =
                new Parcelable.ClassLoaderCreator<EditOperation>() {
                    @Override
                    public EditOperation createFromParcel(Parcel in) {
                        return new EditOperation(in, null);
                    }

                    @Override
                    public EditOperation createFromParcel(Parcel in, ClassLoader loader) {
                        return new EditOperation(in, loader);
                    }

                    @Override
                    public EditOperation[] newArray(int size) {
                        return new EditOperation[size];
                    }
                };
    }

    /**
     * A helper for enabling and handling "PROCESS_TEXT" menu actions.
     * These allow external applications to plug into currently selected text.
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    static final class ProcessTextIntentActionsHandler {

        private final Editor mEditor;
        private final EditText mEditTet;
        private final Context mContext;
        private final PackageManager mPackageManager;
        private final String mPackageName;
        private final List<ResolveInfo> mSupportedActivities = new ArrayList<>();

        private ProcessTextIntentActionsHandler(Editor editor) {
            mEditor = Objects.requireNonNull(editor);
            mEditTet = Objects.requireNonNull(mEditor.mEditText);
            mContext = Objects.requireNonNull(mEditTet.getContext());
            mPackageManager = Objects.requireNonNull(mContext.getPackageManager());
            mPackageName = Objects.requireNonNull(mContext.getPackageName());
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

        private boolean fireIntent(Intent intent) {
            if (intent != null && Intent.ACTION_PROCESS_TEXT.equals(intent.getAction())) {
                String selectedText = mEditTet.getSelectedText();
                selectedText = HiddenTextUtils.trimToParcelableSize(selectedText);
                intent.putExtra(Intent.EXTRA_PROCESS_TEXT, selectedText);
                mEditor.mPreserveSelection = true;
                mEditTet.startActivityForResult(intent, EditText.PROCESS_TEXT_REQUEST_CODE);
                return true;
            }
            return false;
        }

        private void loadSupportedActivities() {
            mSupportedActivities.clear();
            // (EW) the AOSP version checked Context#canStartActivityForResult, but that's hidden
            // along with View#startActivityForResult (and how that's implemented), so we need to
            // start the activity for result slightly differently, which has different logic to
            // determine if it can be done.
            if (!mEditTet.canStartActivityForResult()) {
                return;
            }
            PackageManager packageManager = mEditTet.getContext().getPackageManager();
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
                                    || mContext.checkSelfPermission(info.activityInfo.permission)
                                            == PackageManager.PERMISSION_GRANTED);
        }

        private Intent createProcessTextIntentForResolveInfo(ResolveInfo info) {
            return createProcessTextIntent()
                    .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, !mEditTet.isTextEditable())
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

    /**
     * (EW) wrapper for SuggestionSpan#getUnderlineColor to support older versions
     *
     * @return The color of the underline for that span, or 0 if there is no underline
     */
    private static int getUnderlineColor(SuggestionSpan span) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // (EW) despite not actually getting called, on Pie, simply having this code here causes
            // this warning to be logged:
            // Accessing hidden method Landroid/text/style/SuggestionSpan;->getUnderlineColor()I (light greylist, linking)
            return span.getUnderlineColor();
        }

        // (EW) from SuggestionSpan for use on older versions
        int flags = span.getFlags();
        // The order here should match what is used in updateDrawState
        final boolean misspelled = (flags & SuggestionSpan.FLAG_MISSPELLED) != 0;
        final boolean easy = (flags & SuggestionSpan.FLAG_EASY_CORRECT) != 0;
        final boolean autoCorrection = (flags & SuggestionSpan.FLAG_AUTO_CORRECTION) != 0;
        if (easy) {
            if (!misspelled) {
                // mEasyCorrectUnderlineColor
                return Color.parseColor("#88C8C8C8");
            } else {
                // mMisspelledUnderlineColor
                return  Color.parseColor("#ffff4444");
            }
        } else if (autoCorrection) {
            // mAutoCorrectionUnderlineColor
            return Color.parseColor("#ff33b5e5");
        }
        return 0;
    }

    /**
     * (EW) wrapper for PopupWindow#setWindowLayoutType to support older versions
     *
     * Set the layout type for this window.
     * <p>
     * See {@link WindowManager.LayoutParams#type} for possible values.
     *
     * @param layoutType Layout type for this window.
     *
     * @see WindowManager.LayoutParams#type
     */
    private static void setWindowLayoutType(PopupWindow popupWindow, int layoutType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            popupWindow.setWindowLayoutType(layoutType);
        } else {
            // (EW) PopupWindow#setWindowLayoutType was called at least since Kitkat, but it only
            // became available for apps to call in Marshmallow. this isn't great, but this use of
            // reflection is at least relatively safe since it's only done on old versions so it
            // shouldn't just stop working at some point in the future.
            try {
                Method setWindowLayoutTypeMethod = PopupWindow.class.getMethod(
                        "setWindowLayoutType", int.class);
                setWindowLayoutTypeMethod.invoke(popupWindow, layoutType);
            } catch (NoSuchMethodException | IllegalAccessException
                    | InvocationTargetException e) {
                Log.e(TAG, "setWindowLayoutType: "
                        + "Reflection failed on PopupWindow#setWindowLayoutType: "
                        + e.getMessage());
            }
        }
    }
}
