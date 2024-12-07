/*
 * Copyright (C) 2024 Eli Wittman
 * Copyright (C) 2007-2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.wittmane.testingedittext.aosp.android.view.inputmethod;

import static android.view.inputmethod.InputConnection.CURSOR_UPDATE_IMMEDIATE;

import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.RequiresApi;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * (EW) content from {@link InputMethodManager} that is blocked from apps accessing
 */
public class InputMethodManagerExtension {
    private static final String TAG = InputMethodManagerExtension.class.getSimpleName();
    static final boolean DEBUG = false;

    private static final int REQUEST_UPDATE_CURSOR_ANCHOR_INFO_NONE = 0x0;

    // (EW) tracking the real object that this supplements
    private final InputMethodManager mIMM;

    //TODO: (EW) note that InputMethodManager.Handler#handleMessage accesses InputMethodManager's
    // own version of this, which won't be set since we can't set it and have to work off of this
    // mimic. I'm not sure how we can get that to work. find some other way to send that update that
    // currently is getting skipped since it won't ever think it is monitoring.
    /**
     * The monitor mode for
     * {@link InputMethodManager#updateCursorAnchorInfo(View, CursorAnchorInfo)}.
     */
    private int mRequestUpdateCursorAnchorInfoMonitorMode = REQUEST_UPDATE_CURSOR_ANCHOR_INFO_NONE;

    // (EW) we're tracking the input connection to see when it changes to simulate what happens in
    // InputMethodManager.Handler#handleMessage that we can't directly tie into
    private InputConnection mInputConnection;

    // (EW) I'm not certain if this synchronization is necessary outside of InputMethodManager, but
    // it probably doesn't hurt to keep. we can't get the same object for synchronization as
    // InputMethodManager since that's hidden, so we'll just manage our own object for the things we
    // need to manage separately, which is probably good enough.
    protected final Object mH = new Object();

    /**
     * The instance that has previously been sent to the input method.
     */
    private CursorAnchorInfo mCursorAnchorInfo = null;

    // (EW) map to track the appropriate helper instance for each InputMethodManager
    private static final Map<InputMethodManager, InputMethodManagerExtension> mHelperMap =
            new HashMap<>();

    // (EW) get the supplemental object. this class needs to manage things related to the
    // InputMethodManager, potentially across separate places that access it, so we'll track a
    // single instance of this class per instance of the InputMethodManager to get the appropriate
    // helper anywhere.
    public static InputMethodManagerExtension getSupplementalObject(InputMethodManager imm,
                                                                    InputConnection ic) {
        synchronized (mHelperMap) {
            if (!mHelperMap.containsKey(imm)) {
                mHelperMap.put(imm, new InputMethodManagerExtension(imm));
            }
            InputMethodManagerExtension helper = mHelperMap.get(imm);

            // (EW) InputMethodManager.Handler#handleMessage (I think ultimately triggered from
            // IInputMethodClient.Stub#onBindMethod) resets this in the AOSP version, but I think
            // that is due to it managing input methods and being reused. since a new input
            // connection gets created when switching input methods, we can just check if the input
            // connection changed and trigger the reset now to mimic functionality close enough.
            if (helper.mInputConnection != ic) {
                helper.mRequestUpdateCursorAnchorInfoMonitorMode =
                        REQUEST_UPDATE_CURSOR_ANCHOR_INFO_NONE;
                helper.mInputConnection = ic;
            }

            return helper;
        }
    }

    // (EW) force instances of this to only get created tied to an instance of InputMethodManager
    private InputMethodManagerExtension(InputMethodManager imm) {
        mIMM = imm;
    }

    // (EW) the AOSP version is hidden and restricted, so we have to manage the mode separately
    /**
     * Return true if the current input method wants to be notified when cursor/anchor location
     * is changed.
     */
    public boolean isCursorAnchorInfoEnabled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        synchronized (mH) {
            final boolean isImmediate = (mRequestUpdateCursorAnchorInfoMonitorMode &
                    InputConnection.CURSOR_UPDATE_IMMEDIATE) != 0;
            final boolean isMonitoring = (mRequestUpdateCursorAnchorInfoMonitorMode &
                    InputConnection.CURSOR_UPDATE_MONITOR) != 0;
            return isImmediate || isMonitoring;
        }
    }

    // (EW) the AOSP version is hidden and marked with UnsupportedAppUsage. we just need to track it
    // separately since we're not allowed to use the AOSP methods to get or set it for some reason.
    // this seems to only be called from AOSP's EditableInputConnection, so we can reasonably keep
    // track of the mode entirely separately from InputMethodManager (with the 1 slight exception
    // mentioned in #getSupplementalObject).
    /**
     * Set the requested mode for
     * {@link InputMethodManager#updateCursorAnchorInfo(View, CursorAnchorInfo)}.
     */
    public void setUpdateCursorAnchorInfoMode(int flags) {
        synchronized (mH) {
            mRequestUpdateCursorAnchorInfoMonitorMode = flags;
        }
    }

    // (EW) the AOSP version isn't actually hidden, but since we're managing the cursor anchor info
    // monitor mode, we need to wrap the call and add the handling that InputMethodManager won't
    // correctly check, since it will use it's own version
    @RequiresApi(api = VERSION_CODES.LOLLIPOP)
    public void updateCursorAnchorInfo(View view, final CursorAnchorInfo cursorAnchorInfo) {
        synchronized (mH) {
            // If immediate bit is set, we will call updateCursorAnchorInfo() even when the data has
            // not been changed from the previous call.
            final boolean isImmediate = (mRequestUpdateCursorAnchorInfoMonitorMode &
                    CURSOR_UPDATE_IMMEDIATE) != 0;
            if (!isImmediate && Objects.equals(mCursorAnchorInfo, cursorAnchorInfo)) {
                // TODO: Consider always emitting this message once we have addressed redundant
                // calls of this method from android.widget.Editor.
                if (DEBUG) {
                    Log.w(TAG, "Ignoring redundant updateCursorAnchorInfo: info="
                            + cursorAnchorInfo);
                }
                return;
            }
            if (DEBUG) Log.v(TAG, "updateCursorAnchorInfo: " + cursorAnchorInfo);

            mIMM.updateCursorAnchorInfo(view, cursorAnchorInfo);

            mCursorAnchorInfo = cursorAnchorInfo;
            // Clear immediate bit (if any).
            mRequestUpdateCursorAnchorInfoMonitorMode &= ~CURSOR_UPDATE_IMMEDIATE;
        }
    }

    // (EW) the AOSP version is hidden
    /**
     * Get the requested mode for
     * {@link InputMethodManager#updateCursorAnchorInfo(View, CursorAnchorInfo)}.
     */
    public int getUpdateCursorAnchorInfoMode() {
        synchronized (mH) {
            return mRequestUpdateCursorAnchorInfoMonitorMode;
        }
    }
}
