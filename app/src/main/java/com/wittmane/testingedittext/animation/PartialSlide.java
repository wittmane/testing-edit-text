/*
 * Copyright (C) 2026 Eli Wittman
 * Copyright (C) 2014 The Android Open Source Project
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
package com.wittmane.testingedittext.animation;

import android.animation.Animator;
import android.animation.TimeInterpolator;
import androidx.annotation.IntDef;
import android.content.Context;
import android.transition.SidePropagation;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import com.wittmane.testingedittext.aosp.android.util.MathUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// (EW) slightly modified Slide to allow sliding to/from a point other than the edge of the scene
/**
 * This transition tracks changes to the visibility of target views in the
 * start and end scenes and moves views in or out from near one of the edges of
 * the scene. Visibility is determined by both the
 * {@link View#setVisibility(int)} state of the view as well as whether it
 * is parented in the current view hierarchy. Disappearing Views are
 * limited as described in {@link Visibility#onDisappear(android.view.ViewGroup,
 * TransitionValues, int, TransitionValues, int)}.
 */
public class PartialSlide extends Visibility {
    private static final TimeInterpolator sDecelerate = new DecelerateInterpolator();
    private static final TimeInterpolator sAccelerate = new AccelerateInterpolator();
    private static final String PROPNAME_SCREEN_POSITION = "android:partialSlide:screenPosition";
    private CalculateSlide mSlideCalculator = sCalculateBottom;
    private @GravityFlag int mSlideEdge = Gravity.BOTTOM;
    private float mDistanceValue = 0;
    private @UnitFlag int mDistanceUnit = FRACTION;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({Gravity.LEFT, Gravity.TOP, Gravity.RIGHT, Gravity.BOTTOM, Gravity.START, Gravity.END})
    public @interface GravityFlag {}

    public static final int FRACTION = 0;
    public static final int DP = 1;
    public static final int PX = 2;
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({FRACTION, DP, PX})
    public @interface UnitFlag {}

    private interface CalculateSlide {

        /** Returns the translation value for view when it goes out of the scene */
        float getGoneX(ViewGroup sceneRoot, View view, float distancePx);

        /** Returns the translation value for view when it goes out of the scene */
        float getGoneY(ViewGroup sceneRoot, View view, float distancePx);
    }

    private static abstract class CalculateSlideHorizontal implements CalculateSlide {

        @Override
        public float getGoneY(ViewGroup sceneRoot, View view, float distancePx) {
            return view.getTranslationY();
        }
    }

    private static abstract class CalculateSlideVertical implements CalculateSlide {

        @Override
        public float getGoneX(ViewGroup sceneRoot, View view, float distancePx) {
            return view.getTranslationX();
        }
    }

    private static final CalculateSlide sCalculateLeft = new CalculateSlideHorizontal() {
        @Override
        public float getGoneX(ViewGroup sceneRoot, View view, float distancePx) {
            return view.getTranslationX() - MathUtils.constrain(distancePx,
                    -sceneRoot.getWidth(), sceneRoot.getWidth());
        }
    };

    private static final CalculateSlide sCalculateStart = new CalculateSlideHorizontal() {
        @Override
        public float getGoneX(ViewGroup sceneRoot, View view, float distancePx) {
            final boolean isRtl = sceneRoot.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            final float x;
            if (isRtl) {
                x = view.getTranslationX() + MathUtils.constrain(distancePx,
                        -sceneRoot.getWidth(), sceneRoot.getWidth());
            } else {
                x = view.getTranslationX() - MathUtils.constrain(distancePx,
                        -sceneRoot.getWidth(), sceneRoot.getWidth());
            }
            return x;
        }
    };

    private static final CalculateSlide sCalculateTop = new CalculateSlideVertical() {
        @Override
        public float getGoneY(ViewGroup sceneRoot, View view, float distancePx) {
            return view.getTranslationY() - MathUtils.constrain(distancePx,
                    -sceneRoot.getHeight(), sceneRoot.getHeight());
        }
    };

    private static final CalculateSlide sCalculateRight = new CalculateSlideHorizontal() {
        @Override
        public float getGoneX(ViewGroup sceneRoot, View view, float distancePx) {
            return view.getTranslationX() + MathUtils.constrain(distancePx,
                    -sceneRoot.getWidth(), sceneRoot.getWidth());
        }
    };

    private static final CalculateSlide sCalculateEnd = new CalculateSlideHorizontal() {
        @Override
        public float getGoneX(ViewGroup sceneRoot, View view, float distancePx) {
            final boolean isRtl = sceneRoot.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            final float x;
            if (isRtl) {
                x = view.getTranslationX() - MathUtils.constrain(distancePx,
                        -sceneRoot.getWidth(), sceneRoot.getWidth());
            } else {
                x = view.getTranslationX() + MathUtils.constrain(distancePx,
                        -sceneRoot.getWidth(), sceneRoot.getWidth());
            }
            return x;
        }
    };

    private static final CalculateSlide sCalculateBottom = new CalculateSlideVertical() {
        @Override
        public float getGoneY(ViewGroup sceneRoot, View view, float distancePx) {
            return view.getTranslationY() + MathUtils.constrain(distancePx,
                    -sceneRoot.getHeight(), sceneRoot.getHeight());
        }
    };

    /**
     * Constructor using the provided slide edge direction and distance value and unit.
     */
    public PartialSlide(@GravityFlag int slideEdge, float distanceValue,
                        @UnitFlag int distanceUnit) {
        setSlideEdge(slideEdge);
        setDistance(distanceValue, distanceUnit);
    }

    private void captureValues(TransitionValues transitionValues) {
        View view = transitionValues.view;
        int[] position = new int[2];
        view.getLocationOnScreen(position);
        transitionValues.values.put(PROPNAME_SCREEN_POSITION, position);
    }

    @Override
    public void captureStartValues(TransitionValues transitionValues) {
        super.captureStartValues(transitionValues);
        captureValues(transitionValues);
    }

    @Override
    public void captureEndValues(TransitionValues transitionValues) {
        super.captureEndValues(transitionValues);
        captureValues(transitionValues);
    }

    /**
     * Change the edge that Views appear and disappear from.
     *
     * @param slideEdge The edge of the scene to use for Views appearing and disappearing. One of
     *                  {@link android.view.Gravity#LEFT}, {@link android.view.Gravity#TOP},
     *                  {@link android.view.Gravity#RIGHT}, {@link android.view.Gravity#BOTTOM},
     *                  {@link android.view.Gravity#START}, {@link android.view.Gravity#END}.
     */
    public void setSlideEdge(@GravityFlag int slideEdge) {
        switch (slideEdge) {
            case Gravity.LEFT:
                mSlideCalculator = sCalculateLeft;
                break;
            case Gravity.TOP:
                mSlideCalculator = sCalculateTop;
                break;
            case Gravity.RIGHT:
                mSlideCalculator = sCalculateRight;
                break;
            case Gravity.BOTTOM:
                mSlideCalculator = sCalculateBottom;
                break;
            case Gravity.START:
                mSlideCalculator = sCalculateStart;
                break;
            case Gravity.END:
                mSlideCalculator = sCalculateEnd;
                break;
            default:
                throw new IllegalArgumentException("Invalid slide direction");
        }
        mSlideEdge = slideEdge;
        SidePropagation propagation = new SidePropagation();
        propagation.setSide(slideEdge);
        setPropagation(propagation);
    }

    /**
     * Returns the edge that Views appear and disappear from.
     *
     * @return the edge of the scene to use for Views appearing and disappearing. One of
     *         {@link android.view.Gravity#LEFT}, {@link android.view.Gravity#TOP},
     *         {@link android.view.Gravity#RIGHT}, {@link android.view.Gravity#BOTTOM},
     *         {@link android.view.Gravity#START}, {@link android.view.Gravity#END}.
     */
    @GravityFlag
    public int getSlideEdge() {
        return mSlideEdge;
    }

    public void setDistance(float distance, @UnitFlag int unit) {
        mDistanceValue = distance;
        mDistanceUnit = unit;
    }

    /**
     * Returns the distance that the view will slide. See {@link #getDistanceUnit} to get the unit
     * for this distance.
     *
     * @return distance that the view will slide.
     */
    public float getDistanceValue() {
        return mDistanceValue;
    }

    /**
     * Returns the unit for the distance that the view will slide. See {@link #getDistanceValue} to get
     * the value for this distance.
     *
     * @return unit for the distance that the view will slide.
     */
    public @UnitFlag int getDistanceUnit() {
        return mDistanceUnit;
    }

    @Override
    public Animator onAppear(ViewGroup sceneRoot, View view,
                             TransitionValues startValues, TransitionValues endValues) {
        if (endValues == null) {
            return null;
        }
        int[] position = (int[]) endValues.values.get(PROPNAME_SCREEN_POSITION);
        float endX = view.getTranslationX();
        float endY = view.getTranslationY();
        float distancePx = getDistancePx(sceneRoot);
        float startX = mSlideCalculator.getGoneX(sceneRoot, view, distancePx);
        float startY = mSlideCalculator.getGoneY(sceneRoot, view, distancePx);
        return TranslationAnimationCreator
                .createAnimation(view, endValues, position[0], position[1],
                        startX, startY, endX, endY, sDecelerate, this);
    }

    @Override
    public Animator onDisappear(ViewGroup sceneRoot, View view,
                                TransitionValues startValues, TransitionValues endValues) {
        if (startValues == null) {
            return null;
        }
        int[] position = (int[]) startValues.values.get(PROPNAME_SCREEN_POSITION);
        float startX = view.getTranslationX();
        float startY = view.getTranslationY();
        float distancePx = getDistancePx(sceneRoot);
        float endX = mSlideCalculator.getGoneX(sceneRoot, view, distancePx);
        float endY = mSlideCalculator.getGoneY(sceneRoot, view, distancePx);
        return TranslationAnimationCreator
                .createAnimation(view, startValues, position[0], position[1],
                        startX, startY, endX, endY, sAccelerate, this);
    }

    private float getDistancePx(ViewGroup sceneRoot) {
        float distancePx;
        if (mDistanceUnit == FRACTION) {
            int referenceDistance = mSlideEdge == Gravity.TOP || mSlideEdge == Gravity.BOTTOM
                    ? sceneRoot.getHeight()
                    : sceneRoot.getWidth();
            distancePx = referenceDistance * mDistanceValue;
        } else if (mDistanceUnit == DP) {
            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager windowManager =
                    (WindowManager) sceneRoot.getContext().getSystemService(Context.WINDOW_SERVICE);
            windowManager.getDefaultDisplay().getMetrics(metrics);
            distancePx = mDistanceValue * metrics.density;
        } else {
            // already in px
            distancePx = mDistanceValue;
        }
        return distancePx;
    }
}
