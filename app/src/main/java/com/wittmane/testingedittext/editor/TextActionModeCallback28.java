package com.wittmane.testingedittext.editor;

import android.app.RemoteAction;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.util.Log;
import android.view.ActionMode;
import android.view.ActionMode.Callback;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.textclassifier.TextClassification;
import android.view.textclassifier.TextClassificationManager;

import androidx.annotation.RequiresApi;

import com.wittmane.testingedittext.CustomEditTextView;
import com.wittmane.testingedittext.Editor;
import com.wittmane.testingedittext.Editor.TextActionMode;

import java.util.HashMap;
import java.util.Map;

/**
 * An ActionMode Callback class that is used to provide actions while in text insertion or
 * selection mode.
 *
 * The default callback provides a subset of Select All, Cut, Copy, Paste, Share and Replace
 * actions, depending on which of these this TextView supports and the current selection.
 */
@RequiresApi(api = Build.VERSION_CODES.M)
public class TextActionModeCallback28 extends ActionMode.Callback2 {
    static final String TAG = TextActionModeCallback28.class.getSimpleName();

    private final Path mSelectionPath = new Path();
    private final RectF mSelectionBounds = new RectF();
    private final boolean mHasSelection;
    private final int mHandleHeight;
    private final Map<MenuItem, OnClickListener> mAssistClickHandlers = new HashMap<>();

    private final CustomEditTextView mTextView;

    public TextActionModeCallback28(@TextActionMode int mode, CustomEditTextView textView) {
        mTextView = textView;
        mHasSelection = mode == TextActionMode.SELECTION
                || (mTextView.mEditor.mTextIsSelectable && mode == TextActionMode.TEXT_LINK);
        if (mHasSelection) {
            SelectionModifierCursorController28 selectionController = mTextView.mEditor.getSelectionController();
            if (selectionController.mStartHandle == null) {
                // As these are for initializing selectionController, hide() must be called.
                selectionController.initDrawables();
                selectionController.initHandles();
                selectionController.hide();
            }
            mHandleHeight = Math.max(
                    mTextView.mEditor.mSelectHandleLeft.getMinimumHeight(),
                    mTextView.mEditor.mSelectHandleRight.getMinimumHeight());
        } else {
            InsertionPointCursorController28 insertionController = mTextView.mEditor.getInsertionController();
            if (insertionController != null) {
                insertionController.getHandle();
                mHandleHeight = mTextView.mEditor.mSelectHandleCenter.getMinimumHeight();
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
//            mTextView.mEditor.mProcessTextIntentActionsHandler.onInitializeMenu(menu);
        }

        if (mHasSelection && !mTextView.hasTransientState()) {
            mTextView.setHasTransientState(true);
        }
        return true;
    }

    private Callback getCustomCallback() {
//        return mHasSelection
//                ? mTextView.mEditor.mCustomSelectionActionModeCallback
//                : mTextView.mEditor.mCustomInsertionActionModeCallback;
        return null;
    }

    private void populateMenuWithItems(Menu menu) {
//        if (mTextView.canCut()) {
//            menu.add(Menu.NONE, CustomEditTextView.ID_CUT, MENU_ITEM_ORDER_CUT,
//                    com.android.internal.R.string.cut)
//                    .setAlphabeticShortcut('x')
//                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
//        }
//
//        if (mTextView.canCopy()) {
//            menu.add(Menu.NONE, CustomEditTextView.ID_COPY, MENU_ITEM_ORDER_COPY,
//                    com.android.internal.R.string.copy)
//                    .setAlphabeticShortcut('c')
//                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
//        }
//
//        if (mTextView.canPaste()) {
//            menu.add(Menu.NONE, CustomEditTextView.ID_PASTE, MENU_ITEM_ORDER_PASTE,
//                    com.android.internal.R.string.paste)
//                    .setAlphabeticShortcut('v')
//                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
//        }
//
//        if (mTextView.canShare()) {
//            menu.add(Menu.NONE, CustomEditTextView.ID_SHARE, MENU_ITEM_ORDER_SHARE,
//                    com.android.internal.R.string.share)
//                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
//        }
//
//        if (mTextView.canRequestAutofill()) {
//            final String selected = mTextView.getSelectedText();
//            if (selected == null || selected.isEmpty()) {
//                menu.add(Menu.NONE, CustomEditTextView.ID_AUTOFILL, MENU_ITEM_ORDER_AUTOFILL,
//                        com.android.internal.R.string.autofill)
//                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
//            }
//        }
//
//        if (mTextView.canPasteAsPlainText()) {
//            menu.add(
//                    Menu.NONE,
//                    CustomEditTextView.ID_PASTE_AS_PLAIN_TEXT,
//                    MENU_ITEM_ORDER_PASTE_AS_PLAIN_TEXT,
//                    com.android.internal.R.string.paste_as_plain_text)
//                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
//        }

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
//        boolean selectAllItemExists = menu.findItem(CustomEditTextView.ID_SELECT_ALL) != null;
//        if (canSelectAll && !selectAllItemExists) {
//            menu.add(Menu.NONE, CustomEditTextView.ID_SELECT_ALL, MENU_ITEM_ORDER_SELECT_ALL,
//                    com.android.internal.R.string.selectAll)
//                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
//        } else if (!canSelectAll && selectAllItemExists) {
//            menu.removeItem(CustomEditTextView.ID_SELECT_ALL);
//        }
    }

    private void updateReplaceItem(Menu menu) {
//        boolean canReplace = mTextView.isSuggestionsEnabled() && shouldOfferToShowSuggestions();
//        boolean replaceItemExists = menu.findItem(CustomEditTextView.ID_REPLACE) != null;
//        if (canReplace && !replaceItemExists) {
//            menu.add(Menu.NONE, CustomEditTextView.ID_REPLACE, MENU_ITEM_ORDER_REPLACE,
//                    com.android.internal.R.string.replace)
//                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
//        } else if (!canReplace && replaceItemExists) {
//            menu.removeItem(CustomEditTextView.ID_REPLACE);
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
                mTextView.mEditor.getSelectionActionModeHelper().getTextClassification();
        if (!shouldEnableAssistMenuItems() || textClassification == null) {
            // No textClassification result to handle the click. Eat the click.
            return true;
        }

        OnClickListener onClickListener = mAssistClickHandlers.get(assistMenuItem);
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
            mTextView.mEditor.stopTextActionMode();
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
//        mTextView.mEditor.getSelectionActionModeHelper().onSelectionAction(item.getItemId());
//
//        if (mTextView.mEditor.mProcessTextIntentActionsHandler.performMenuItemAction(item)) {
//            return true;
//        }
//        Callback customCallback = getCustomCallback();
//        if (customCallback != null && customCallback.onActionItemClicked(mode, item)) {
//            return true;
//        }
//        if (item.getGroupId() == CustomEditTextView.ID_ASSIST && onAssistMenuItemClicked(item)) {
//            return true;
//        }
//        return mTextView.onTextContextMenuItem(item.getItemId());
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        // Clear mTextActionMode not to recursively destroy action mode by clearing selection.
        mTextView.mEditor.getSelectionActionModeHelper().onDestroyActionMode();
        Log.w(TAG, "onDestroyActionMode: mode=" + mode);
        mTextView.mEditor.mTextActionMode = null;
        Callback customCallback = getCustomCallback();
        if (customCallback != null) {
            customCallback.onDestroyActionMode(mode);
        }

        if (!mTextView.mEditor.mPreserveSelection) {
            /*
             * Leave current selection when we tentatively destroy action mode for the
             * selection. If we're detaching from a window, we'll bring back the selection
             * mode when (if) we get reattached.
             */
            Selection.setSelection((Spannable) mTextView.getText(),
                    mTextView.getSelectionEnd());
        }

        if (mTextView.mEditor.mSelectionModifierCursorController != null) {
            mTextView.mEditor.mSelectionModifierCursorController.hide();
        }

        mAssistClickHandlers.clear();
//        mTextView.mEditor.mRequestingLinkActionMode = false;
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
            float primaryHorizontal = mTextView.mEditor.clampHorizontalPosition(null,
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
