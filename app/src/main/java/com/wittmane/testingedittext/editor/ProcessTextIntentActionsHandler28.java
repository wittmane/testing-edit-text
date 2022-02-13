package com.wittmane.testingedittext.editor;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.IntRange;
import androidx.annotation.Nullable;

import com.wittmane.testingedittext.CustomEditTextView;
import com.wittmane.testingedittext.Editor;
import com.wittmane.testingedittext.HiddenTextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * A helper for enabling and handling "PROCESS_TEXT" menu actions.
 * These allow external applications to plug into currently selected text.
 */
public class ProcessTextIntentActionsHandler28 {


    private final Editor mEditor;
    private final CustomEditTextView mTextView;
    private final Context mContext;
    private final PackageManager mPackageManager;
    private final String mPackageName;
    private final SparseArray<Intent> mAccessibilityIntents = new SparseArray<>();
    private final SparseArray<AccessibilityNodeInfo.AccessibilityAction> mAccessibilityActions =
            new SparseArray<>();
    private final List<ResolveInfo> mSupportedActivities = new ArrayList<>();

    public ProcessTextIntentActionsHandler28(Editor editor) {
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
                    Editor.MENU_ITEM_ORDER_PROCESS_TEXT_INTENT_ACTIONS_START + i,
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
            int actionId = CustomEditTextView.ACCESSIBILITY_ACTION_PROCESS_TEXT_START_ID + i++;
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
//            mTextView.startActivityForResult(intent, CustomEditTextView.PROCESS_TEXT_REQUEST_CODE);
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
