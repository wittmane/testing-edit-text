package com.wittmane.testingedittext.editor;

import android.view.ViewTreeObserver;

import com.wittmane.testingedittext.CustomEditTextView;

public class PositionListener implements ViewTreeObserver.OnPreDrawListener {
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

    private final CustomEditTextView mTextView;

    public PositionListener(CustomEditTextView textView) {
        mTextView = textView;
    }

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
