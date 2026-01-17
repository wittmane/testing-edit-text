/*
 * Copyright (C) 2025-2026 Eli Wittman
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
import android.os.Build;
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
                : android.R.attr.activityCloseEnterAnimation, true);
    }

    @Override
    public Animator onDisappear(ViewGroup sceneRoot, View view,
                                TransitionValues startValues, TransitionValues endValues) {
        return createAnimator(view, mIsActivityStackTop
                ? android.R.attr.activityCloseExitAnimation
                : android.R.attr.activityOpenExitAnimation, false);
    }

    private Animator createAnimator(View view, int attr, boolean isAppear) {
        if (mWindowAnimationStyle == ResourceUtils.RESOURCES_ID_NULL) {
            Log.e(TAG, "missing windowAnimationStyle");
            return null;
        }
        int animationResId = ResourceUtils.getResourceId(mWindowAnimationStyle, attr, mContext);
        if (animationResId == ResourceUtils.RESOURCES_ID_NULL) {
            Log.e(TAG, "missing animation resource");
            return null;
        }

        // in Android 10, Visibility#onDisappear was changed to add a transition listener to remove
        // the overlay view from the overlay, which eventually calls into
        // ViewGroup#removeViewInternal, which calls ViewGroup#addDisappearingView if the view has
        // an animation, and that just adds the view to a list without any duplicate checking. this
        // disappearing view gets removed in ViewGroup#finishAnimatingView, which is called from
        // View#draw when the view has an animation that is no longer running. as far as I can tell,
        // the overlay view and disappearing view serve the same general function, but seem to be
        // built for the different Transition/Animator vs Animation frameworks, and since we're
        // already using the overlay view, there is no benefit in adding it to the list of
        // disappearing views. a single instance of the view in the list of disappearing views
        // doesn't seem to necessary, but it also doesn't really seem to hurt. the only difference
        // I've noticed is the z-order when there are multiple transitions running at the same time.
        // since there is no duplicate checking, a view can be added multiple times, such as when
        // the transition pauses multiple times from having multiple additional transitions running
        // at the same time, and since a view is only removed at the moment that the animation
        // finishes, it won't ever get removed multiple times to match getting added multiple times,
        // which leaves the view stuck in the overlay. since the animation finished and is expected
        // to be removed, the view is no longer transformed when it's drawn, so it returns to it's
        // original position and is stuck there while the other animations run or any additional
        // ones run in that view group in the future. this is simply a bug in the framework that we
        // need to work around (it probably shouldn't allow adding disappearing views on an overlay
        // view group, but it definitely shouldn't allow adding the same view multiple times unless
        // it removed all on remove). the method and field to get to the overlay view group are
        // blocked from reflection, so we can't manually remove the duplicates. instead, we'll
        // remove the animation on pause and add it back on resume since we can remove that before
        // the problematic framework transition listener is called and checks if the view has an
        // animation, which will prevent it from adding the overlay view as a disappearing view. if
        // the framework fixes this bug, we can revert back to keeping the animation running while
        // paused (still not actively animated) on whatever version that happens in.
        boolean endAnimationOnPause = !isAppear && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
        AnimationAnimator animator =
                new AnimationAnimator(view, mContext, animationResId, endAnimationOnPause);

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
