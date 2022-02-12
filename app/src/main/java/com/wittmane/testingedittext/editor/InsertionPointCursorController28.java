package com.wittmane.testingedittext.editor;

import android.view.ViewTreeObserver;

import androidx.core.content.ContextCompat;

import com.wittmane.testingedittext.CustomEditTextView;

public class InsertionPointCursorController28 implements CursorController {
    private InsertionHandleView28 mHandle;

    protected final CustomEditTextView mTextView;

    public InsertionPointCursorController28(CustomEditTextView textView) {
        mTextView = textView;
    }

    public void show() {
        getHandle().show();

        //TODO: (EW) add back once the selection modifier is added
//        if (mTextView.mEditor.mSelectionModifierCursorController != null) {
//            mTextView.mEditor.mSelectionModifierCursorController.hide();
//        }
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

    InsertionHandleView28 getHandle() {
        if (mTextView.mEditor.mSelectHandleCenter == null) {
//            mTextView.mEditor.mSelectHandleCenter = mTextView.getContext().getDrawable(
//                    mTextView.mTextSelectHandleRes);
            mTextView.mEditor.mSelectHandleCenter = ContextCompat.getDrawable(
                    mTextView.getContext(), mTextView.mTextSelectHandleRes);
        }
        if (mHandle == null) {
            mHandle = new InsertionHandleView28(mTextView.mEditor.mSelectHandleCenter, mTextView);
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
