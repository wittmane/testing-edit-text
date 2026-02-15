/*
 * Copyright (C) 2026 Eli Wittman
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wittmane.testingedittext.util;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.function.Supplier;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// based on
// https://github.com/android/animation-samples/blob/main/Motion/app/src/main/java/com/example/android/motion/demo/containertransform/CheeseArticleFragment.kt
@RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class PredictiveBackAnimationManager implements OnBackAnimationCallback {
    private static final String TAG = PredictiveBackAnimationManager.class.getSimpleName();

    /** Animation is symmetric, regardless of the direction of the gesture */
    public static final int ANIMATION_STYLE_SYMMETRIC = 0;
    /** Animation is asymmetric to match the direction of the gesture only if the gesture from the
     *  start edge. If the gesture is from the end edge, the animation is symmetric. */
    public static final int ANIMATION_STYLE_ASYMMETRIC_WITH_START = 1;
    /** Animation is always asymmetric but also always in the same direction, regardless of the
     *  gesture. */
    public static final int ANIMATION_STYLE_ASYMMETRIC_FIXED_START = 2;
    /** Animation is asymmetric and matches the direction of the gesture. */
    public static final int ANIMATION_STYLE_ASYMMETRIC_WITH_EITHER = 3;
    @IntDef({
            ANIMATION_STYLE_SYMMETRIC,
            ANIMATION_STYLE_ASYMMETRIC_WITH_START,
            ANIMATION_STYLE_ASYMMETRIC_FIXED_START,
            ANIMATION_STYLE_ASYMMETRIC_WITH_EITHER
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AnimationStyle {}

    private final PathInterpolator mGestureInterpolator = new PathInterpolator(0f, 0f, 0f, 0f);
    private float mInitialTouchY = -1f;
    private View mAnimatingView;
    private int mPredictiveBackMargin;

    private final Context mContext;
    private final Supplier<View> mAnimatingViewSupplier;
    private final Runnable mOnNavigateBack;
    private final int mAnimationStyle;

    public static void setUp(Dialog dialog) {
        dialog.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                new PredictiveBackAnimationManager(dialog.getContext(),
                        () -> dialog.findViewById(android.R.id.content).getRootView(),
                        dialog::cancel, ANIMATION_STYLE_SYMMETRIC));
    }

    public PredictiveBackAnimationManager(Context context, Supplier<View> animatingViewSupplier,
                                          Runnable onNavigateBack,
                                          @AnimationStyle int animationStyle) {
        mContext = context;
        mAnimatingViewSupplier = animatingViewSupplier;
        mOnNavigateBack = onNavigateBack;
        mAnimationStyle = animationStyle;
    }

    @Override
    public void onBackStarted(@NonNull BackEvent backEvent) {
        mAnimatingView = mAnimatingViewSupplier.get();
        mPredictiveBackMargin =
                mContext.getResources().getDimensionPixelSize(R.dimen.predictive_back_margin);
    }

    //TODO: (EW) reduce duplicate code with SettingsActivity
    @Override
    public void onBackProgressed(@NonNull BackEvent backEvent) {
        if (mAnimatingView == null) {
            return;
        }

        float progress = mGestureInterpolator.getInterpolation(backEvent.getProgress());
        if (mInitialTouchY < 0f) {
            mInitialTouchY = backEvent.getTouchY();
        }
        float progressY = mGestureInterpolator.getInterpolation(
                (backEvent.getTouchY() - mInitialTouchY) / mAnimatingView.getHeight()
        );

        // See the motion spec about the calculations below.
        // https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back#motion-specs

        // Shift horizontally.
        Configuration config = mContext.getResources().getConfiguration();
        int maxTranslationX = (mAnimatingView.getWidth() / 20) - mPredictiveBackMargin;
        int sign = getTranslateXSign(backEvent, config);
        mAnimatingView.setTranslationX(progress * maxTranslationX * sign);

        // Shift vertically.
        int maxTranslationY = (mAnimatingView.getHeight() / 20) - mPredictiveBackMargin;
        mAnimatingView.setTranslationY(progressY * maxTranslationY);

        // Scale down from 100% to 90%.
        float scale = 1f - (0.1f * progress);
        mAnimatingView.setScaleX(scale);
        mAnimatingView.setScaleY(scale);
    }

    private int getTranslateXSign(@NonNull BackEvent backEvent, Configuration config) {
        int startEdgeSign = (config.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) ? -1 : 1;

        if (mAnimationStyle == ANIMATION_STYLE_ASYMMETRIC_WITH_EITHER) {
            // animate matching the direction of the gesture
            if (backEvent.getSwipeEdge() == BackEvent.EDGE_RIGHT) {
                return -1;
            }
            if (backEvent.getSwipeEdge() == BackEvent.EDGE_LEFT) {
                return 1;
            }
            // default to the start edge for the layout from a back button long press
            return startEdgeSign;
        }

        if (mAnimationStyle == ANIMATION_STYLE_ASYMMETRIC_FIXED_START) {
            // animate always matching the start edge for the layout
            return startEdgeSign;
        }

        if (mAnimationStyle == ANIMATION_STYLE_ASYMMETRIC_WITH_START) {
            if (config.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                        ? backEvent.getSwipeEdge() == BackEvent.EDGE_RIGHT
                        : backEvent.getSwipeEdge() == BackEvent.EDGE_LEFT) {
                // gesture is on the start edge, so animate with that
                return startEdgeSign;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
                        && backEvent.getSwipeEdge() == BackEvent.EDGE_NONE) {
                // default to the start edge for the layout from a back button long press
                return startEdgeSign;
            }
            // gesture not matching the start should just cause a symmetric animation (avoid
            // somewhat of a conflict with the animation after invoking the back action)
            return 0;
        }

        // symmetric animation
        return 0;
    }

    @Override
    public void onBackCancelled() {
        mInitialTouchY = -1f;
        if (mAnimatingView == null) {
            return;
        }
        mAnimatingView.setTranslationX(0f);
        mAnimatingView.setTranslationY(0f);
        mAnimatingView.setScaleX(1f);
        mAnimatingView.setScaleY(1f);
    }

    @Override
    public void onBackInvoked() {
        mOnNavigateBack.run();
    }
}
