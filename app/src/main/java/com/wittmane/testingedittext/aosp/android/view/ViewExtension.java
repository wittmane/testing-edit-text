/*
 * Copyright (C) 2024 Eli Wittman
 * Copyright (C) 2006 The Android Open Source Project
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

package com.wittmane.testingedittext.aosp.android.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.Size;

import com.wittmane.testingedittext.aosp.android.graphics.MatrixExtension;
import com.wittmane.testingedittext.wrapper.Insets;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * (EW) content from {@link View} that is blocked from apps accessing
 */
public class ViewExtension extends View {
    private static final String TAG = ViewExtension.class.getSimpleName();

    /**
     * A hint indicating that this view can be autofilled with a password.
     *
     * This is a heuristic-based hint that is meant to be used by UI Toolkit developers when a
     * view is a password field but doesn't specify a
     * <code>{@value View#AUTOFILL_HINT_PASSWORD}</code>.
     */
    public static final String AUTOFILL_HINT_PASSWORD_AUTO = "passwordAuto";

    public static final int VIEW_STRUCTURE_FOR_ASSIST = 0;
    public static final int VIEW_STRUCTURE_FOR_AUTOFILL = 1;
    public static final int VIEW_STRUCTURE_FOR_CONTENT_CAPTURE = 2;

    @IntDef(flag = true, value = {
            VIEW_STRUCTURE_FOR_ASSIST,
            VIEW_STRUCTURE_FOR_AUTOFILL,
            VIEW_STRUCTURE_FOR_CONTENT_CAPTURE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ViewStructureType {}

    public ViewExtension(Context context) {
        super(context);
    }

    public ViewExtension(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ViewExtension(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ViewExtension(Context context, @Nullable AttributeSet attrs, int defStyleAttr,
                         int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    /**
     * Map a rectangle from view-relative coordinates to screen-relative coordinates
     *
     * @param rect The rectangle to be mapped
     * @param clipToParent Whether to clip child bounds to the parent ones.
     */
    public void mapRectFromViewToScreenCoords(RectF rect, boolean clipToParent) {
        if (!hasIdentityMatrix(this)) {
            getMatrix().mapRect(rect);
        }

        rect.offset(getLeft(), getTop());

        ViewParent parent = getParent();
        while (parent instanceof View) {
            View parentView = (View) parent;

            rect.offset(-parentView.getScrollX(), -parentView.getScrollY());

            if (clipToParent) {
                rect.left = Math.max(rect.left, 0);
                rect.top = Math.max(rect.top, 0);
                rect.right = Math.min(rect.right, parentView.getWidth());
                rect.bottom = Math.min(rect.bottom, parentView.getHeight());
            }

            if (!hasIdentityMatrix(parentView)) {
                parentView.getMatrix().mapRect(rect);
            }

            rect.offset(parentView.getLeft(), parentView.getTop());

            parent = parentView.getParent();
        }

        // (EW) the AOSP version used ViewRootImpl#mCurScrollY to update rect's offset, but we can't
        // get that scroll. see comment in #transformFromViewToWindowSpace.

        // (EW) the AOSP version used View#mAttachInfo.mWindowLeft and View#mAttachInfo.mWindowTop
        // directly, but those are hidden. those values are returned in View#getLocationOnScreen, so
        // we can use that instead.
        int[] windowLocation = getLocationOnScreen();
        rect.offset(windowLocation[0], windowLocation[1]);
    }

    /**
     * Indicates whether or not this view's layout is right-to-left. This is resolved from
     * layout attribute and/or the inherited value from the parent
     *
     * @return true if the layout is right-to-left.
     */
    public boolean isLayoutRtl() {
        return (getLayoutDirection() == LAYOUT_DIRECTION_RTL);
    }

    // (EW) made static to allow calling on any view
    /**
     * Returns true if the transform matrix is the identity matrix.
     * Recomputes the matrix if necessary.
     *
     * @return True if the transform matrix is the identity matrix, false otherwise.
     */
    public static boolean hasIdentityMatrix(View view) {
        // (EW) the AOSP version called RenderNode#hasIdentityMatrix, and documentation for that
        // states that it's just a faster way to do the otherwise equivalent
        // RenderNode#getMatrix(Matrix) Matrix#isIdentity(). View#getMatrix calls
        // RenderNode#getMatrix(Matrix), so we can just use that for an equivalent (but slower)
        // check.
        return view.getMatrix().isIdentity();
    }

    // (EW) based on Kitkat code (changed in Lollipop) since this should only be getting called
    // prior to Lollipop. made static to allow calling on any view
    /**
     * Utility method to retrieve the inverse of the current mMatrix property.
     * We cache the matrix to avoid recalculating it when transform properties
     * have not changed.
     *
     * @return The inverse of the current matrix of this view.
     */
    private static Matrix getInverseMatrix(View view) {
        // (EW) the AOSP version used mTransformationInfo, which we don't have access to, and
        // verified that it wasn't null. View#getMatrix calls View#updateMatrix, which was done next
        // here in the AOSP version, and it gets the gets the matrix we need to work with. it also
        // verifies mTransformationInfo isn't null and returns the identity matrix otherwise, and
        // since the inverse of the identity matrix is itself, no work would need to be done.
        Matrix matrix = view.getMatrix();
        if (!matrix.isIdentity()) {
            // (EW) the AOSP version used mTransformationInfo.mInverseMatrix as a cached version as
            // long as it wasn't marked dirty, but we don't have access to that, so we'll just
            // always calculate the inverse
            Matrix inverseMatrix = new Matrix();
            matrix.invert(inverseMatrix);
        }
        return MatrixExtension.IDENTITY_MATRIX;
    }

    /**
     * Return true if o is a ViewGroup that is laying out using optical bounds.
     */
    public static boolean isLayoutModeOptical(Object o) {
        return o instanceof ViewGroup && ViewGroupExtension.isLayoutModeOptical((ViewGroup) o);
    }

    Insets computeOpticalInsets() {
        Drawable background = getBackground();
        if (background == null) {
            return Insets.NONE;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // (EW) despite not actually getting called, on Pie, simply having this code here causes
            // this warning to be logged:
            // Accessing hidden method Landroid/graphics/drawable/Drawable;->getOpticalInsets()Landroid/graphics/Insets; (light greylist, linking)
            return new Insets(background.getOpticalInsets());
        }

        try {
            Method getOpticalInsetsMethod = Drawable.class.getMethod("getOpticalInsets");
            Object opticalInsets = getOpticalInsetsMethod.invoke(background);
            return new Insets(opticalInsets);
        } catch (NoSuchMethodException | IllegalAccessException
                 | InvocationTargetException e) {
            Log.e(TAG, "computeOpticalInsets: Reflection failed on Drawable#getOpticalInsets: "
                    + e.getMessage());
            return Insets.NONE;
        }
    }

    public Insets getOpticalInsets() {
        // (EW) the AOSP version first checks for the value that was manually set from
        // View#setOpticalInsets, but there isn't a way to get that other than reflection, and I
        // only found one case where it was called, so it's probably unlikely that it would cause a
        // problem here.
        return computeOpticalInsets();
    }

    /**
     * Transforms a motion event from on-screen coordinates to view-local
     * coordinates.
     *
     * @param ev the on-screen motion event
     * @return false if the transformation could not be applied
     */
    public boolean toLocalMotionEvent(MotionEvent ev) {
        // (EW) the AOSP version checked if View#mAttachInfo was null directly, but that's hidden,
        // so we need to call the equivalent API
        if (!isAttachedToWindow()) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Matrix m = new Matrix();
            m.set(MatrixExtension.IDENTITY_MATRIX);
            // (EW) transformMatrixToLocal should be available prior to Q, but in case it isn't,
            // fallback to pre-Lollipop logic
            if (tryTransformMatrixToLocal(m)) {
                ev.transform(m);
                return true;
            }
        }
        // (EW) this is the logic from Kitkat
        // (EW) the AOSP version used the negative values of View#mAttachInfo.mWindowLeft and
        // View#mAttachInfo.mWindowTop directly to call MotionEvent#offsetLocation, but since
        // View#mAttachInfo is hidden, we would need to call View#getLocationOnScreen instead. I'm
        // not sure why it did that. At least in my testing, transformMotionEventToLocal does that
        // same offsetting of the location, so with both, it just doubles the shift, which is
        // incorrect. #transformMotionEventToLocal is more analogous to View#transformMatrixToLocal,
        // which replaced it, so I'm keeping that and skipping the offset from
        // View#getLocationOnScreen.
        transformMotionEventToLocal(this, ev);
        return true;
    }

    // (EW) View#transformMatrixToLocal was made available in Q, but it was actually added in
    // Lollipop, so it should be safe to call on these older versions, but to be extra safe we'll
    // wrap it in a try/catch
    @SuppressLint("NewApi")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private boolean tryTransformMatrixToLocal(@NonNull Matrix matrix) {
        try {
            transformMatrixToLocal(matrix);
        } catch (Exception e) {
            Log.w(TAG, "View#transformMatrixToLocal couldn't be called: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
        return true;
    }

    // (EW) from Kitkat. made static to allow calling on any view
    /**
     * Recursive helper method that applies transformations in post-order.
     *
     * @param ev the on-screen motion event
     */
    private static void transformMotionEventToLocal(View view, MotionEvent ev) {
        final ViewParent parent = view.getParent();
        if (parent instanceof View) {
            final View vp = (View) parent;
            transformMotionEventToLocal(vp, ev);
            ev.offsetLocation(vp.getScrollX(), vp.getScrollY());
        }
        // (EW) the AOSP version also used ViewRootImpl#mCurScrollY to call
        // MotionEvent#offsetLocation, but we can't get that scroll. see comment in
        // #transformFromViewToWindowSpace.

        ev.offsetLocation(-view.getLeft(), -view.getTop());

        if (!hasIdentityMatrix(view)) {
            ev.transform(getInverseMatrix(view));
        }
    }

    public int[] getLocationOnScreen() {
        int[] location = new int[2];
        getLocationOnScreen(location);
        return location;
    }

    public void transformFromViewToWindowSpace(@Size(2) int[] inOutLocation) {
        if (inOutLocation == null || inOutLocation.length < 2) {
            throw new IllegalArgumentException("inOutLocation must be an array of two integers");
        }

        if (!isAttachedToWindow()) {
            // When the view is not attached to a window, this method does not make sense
            inOutLocation[0] = inOutLocation[1] = 0;
            return;
        }

        float[] position = new float[2];
        position[0] = inOutLocation[0];
        position[1] = inOutLocation[1];

        if (!hasIdentityMatrix(this)) {
            getMatrix().mapPoints(position);
        }

        position[0] += getLeft();
        position[1] += getTop();

        ViewParent viewParent = getParent();
        while (viewParent instanceof View) {
            final View view = (View) viewParent;

            position[0] -= view.getScrollX();
            position[1] -= view.getScrollY();

            if (!hasIdentityMatrix(view)) {
                view.getMatrix().mapPoints(position);
            }

            position[0] += view.getLeft();
            position[1] += view.getTop();

            viewParent = view.getParent();
        }

        // (EW) the AOSP version would subtract ViewRootImpl#mCurScrollY from position[1] if
        // viewParent was a ViewRootImpl, but ViewRootImpl is hidden and starting in Pie,
        // ViewRootImpl#mCurScrollY is a restricted API (warning logged specifies "dark greylist").
        // I'm not sure when this is actually necessary, but it seems that there isn't anything we
        // can do. until there is a known issue skipping this causes, there probably isn't a chance
        // of finding some alternative.

        inOutLocation[0] = Math.round(position[0]);
        inOutLocation[1] = Math.round(position[1]);
    }
}
