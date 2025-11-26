/*
 * Copyright (C) 2025 Eli Wittman
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
import android.content.Context;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.wittmane.testingedittext.util.ResourceUtils;

/**
 * A {@link android.transition.Transition} that matches the activity open/close enter/exit
 * animations.
 */
public class ActivityAnimationTransition extends Visibility {
    private static final String TAG = ActivityAnimationTransition.class.getSimpleName();

    private final Context mContext;
    private final int mWindowAnimationStyle;
    private final boolean mIsActivityStackTop;

    /**
     * Create an {@link ActivityAnimationTransition}
     * @param context The context to use to load the animation from the theme.
     * @param isActivityStackTop Whether the target animation is for the new activity that is either
     *                           starting and appearing over the previous activity or is returning
     *                           from and going back to the previous activity (as opposed to the
     *                           animation for the previous activity that is being navigated away
     *                           from or returning to).
     */
    public ActivityAnimationTransition(Context context, boolean isActivityStackTop) {
        mContext = context;
        mWindowAnimationStyle =
                ResourceUtils.getResourceId(android.R.attr.windowAnimationStyle, context);
        mIsActivityStackTop = isActivityStackTop;
    }

    @Override
    public Animator onAppear(ViewGroup sceneRoot, View view,
                             TransitionValues startValues, TransitionValues endValues) {
        return createAnimator(view, mIsActivityStackTop
                ? android.R.attr.activityOpenEnterAnimation
                : android.R.attr.activityCloseEnterAnimation);
    }

    @Override
    public Animator onDisappear(ViewGroup sceneRoot, View view,
                                TransitionValues startValues, TransitionValues endValues) {
        return createAnimator(view, mIsActivityStackTop
                ? android.R.attr.activityCloseExitAnimation
                : android.R.attr.activityOpenExitAnimation);
    }

    private Animator createAnimator(View view, int attr) {
        if (mWindowAnimationStyle == ResourceUtils.RESOURCES_ID_NULL) {
            Log.e(TAG, "missing windowAnimationStyle");
            return null;
        }
        int animationResId = ResourceUtils.getResourceId(mWindowAnimationStyle, attr, mContext);
        if (animationResId == ResourceUtils.RESOURCES_ID_NULL) {
            Log.e(TAG, "missing animation resource");
            return null;
        }

        AnimationAnimator animator = new AnimationAnimator(view, mContext, animationResId);

        long startDelay = getStartDelay();
        if (startDelay >= 0) {
            animator.setStartDelay(startDelay);
        }

        long duration = getDuration();
        if (duration >= 0) {
            animator.setDuration(duration);
        }

        return animator;
    }
}
