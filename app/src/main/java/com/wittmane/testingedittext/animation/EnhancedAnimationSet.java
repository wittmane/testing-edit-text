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

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.Transformation;

import com.wittmane.testingedittext.aosp.android.util.MathUtils;
import com.wittmane.testingedittext.function.Consumer;

import java.util.ArrayList;
import java.util.List;

/**
 * An {@link AnimationSet} that supports pausing, canceling in place, and extra functionality to
 * avoid skewing child animations when setting a start delay or limiting the duration.
 */
public class EnhancedAnimationSet extends AnimationSet {
    private static final String TAG = EnhancedAnimationSet.class.getSimpleName();

    private long mAnimationSetStartTime = Animation.START_ON_FIRST_FRAME;
    private long mMaxPrevChildTime;
    private long mMostRecentActiveTime;
    private long mPauseStartTime;
    private long mPreviousPausedTime;
    private boolean mIsCancelingInPlace;
    private boolean mIsCanceled;
    private Transformation mLastTransformation;
    private long mChildrenTotalDuration = 0;
    private long mStartDelay;
    private long mClippedDuration = -1;
    private AnimationUpdateListener mAnimationUpdateListener;
    private boolean mIsTempEnded;

    public EnhancedAnimationSet() {
        super(false);
    }

    @Override
    public void setStartTime(long startTimeMillis) {
        if (mIsTempEnded) {
            // ideally we would just ignore updates to the start time to retain previous state for
            // when this gets restarted, but we don't have a way to reset AnimationSet's internal
            // ended state other than calling setStartTime, and if that's not reset, we'll trigger
            // the animation end handler shortly after starting in AnimationSet#getTransformation.
            // since #getAnimations directly exposes the list of animations, we'll remove them
            // temporarily to call #setStartTime to the same value it already had to simply reset
            // this animation to not be ended while avoiding resetting the child animation, and then
            // we'll add all of the animations back without the parent class realizing.
            List<Animation> animationList = getAnimations();
            List<Animation> animationListCopy = new ArrayList<>(animationList);
            animationList.clear();
            if (!getAnimations().isEmpty()) {
                // safety check in case something changes in a new version to not expose the real
                // list of child animations. resetting all of the child animations is probably worse
                // than sending an early animation end event, so just do nothing.
                Log.e(TAG, "Can't reset the ended state for resuming from temporary ending");
                return;
            }
            super.setStartTime(mAnimationSetStartTime);
            animationList.addAll(animationListCopy);
            return;
        }
        super.setStartTime(startTimeMillis);
    }

    @Override
    public void reset() {
        if (mIsTempEnded) {
            // ignore to retain previous state for when this gets restarted
            return;
        }
        super.reset();
        mAnimationSetStartTime = Animation.START_ON_FIRST_FRAME;
        mMaxPrevChildTime = 0;
        mMostRecentActiveTime = 0;
        mPauseStartTime = 0;
        mPreviousPausedTime = 0;
        mIsCancelingInPlace = false;
        mIsCanceled = false;
        mLastTransformation = null;
        mChildrenTotalDuration = 0;
    }

    @Override
    public boolean getTransformation(long currentTime, Transformation outTransformation) {
        if (mLastTransformation == null) {
            mLastTransformation = new Transformation();
            mLastTransformation.set(outTransformation);
        }
        if (mAnimationSetStartTime == Animation.START_ON_FIRST_FRAME) {
            long startTime = getStartTime();
            if (startTime == Animation.START_ON_FIRST_FRAME) {
                startTime = currentTime;
            }
            mAnimationSetStartTime = startTime;
            mChildrenTotalDuration = calcTotalDuration(this, null);
            if (mChildrenTotalDuration != Animation.INFINITE) {
                mChildrenTotalDuration -= mStartDelay;
            }
        }
        if (mMostRecentActiveTime == 0 || !isPaused()) {
            mMostRecentActiveTime = currentTime;
        }
        long elapsedTime = getElapsedTime();
        long childElapsedTime = Math.max(0, elapsedTime - mStartDelay);
        boolean isAnimationStillRunning;
        if (mIsCancelingInPlace || mIsTempEnded) {
            // just use the last transform since the canceled children will try to move the state to
            // the end of the animation
            outTransformation.set(mLastTransformation);
            isAnimationStillRunning = false;
        } else {
            boolean interpolatorHasMore = false;
            long childTime;
            // skip the interpolator if the duration is 0 (can't really do anything with that) or
            // the duration is infinite (can't really treat it as a percent complete to adjust
            // because it would effectively always be at the start).
            Interpolator interpolator = getInterpolator();
            if (mChildrenTotalDuration > 0 && interpolator != null
                    && childElapsedTime <= mChildrenTotalDuration) {
                // limit the interpolated time going past the edges of the real time since negative
                // doesn't make sense and we don't allow going back in time, so going past the end
                // could cause issues
                float interpolatedTime = MathUtils.constrain(
                        interpolator.getInterpolation(
                                (float) childElapsedTime / mChildrenTotalDuration),
                        0f, 1f);
                childTime = Math.round((double) interpolatedTime * mChildrenTotalDuration)
                        + mAnimationSetStartTime;
                // flag to prevent quitting the animation early if the interpolator reaches the
                // "end" of the animation early (repeat, overshoot, etc)
                interpolatorHasMore = true;
            } else {
                childTime = mAnimationSetStartTime + childElapsedTime;
            }
            // prevent going back in time as that doesn't work when a child animation repeats (can't
            // go to the previous instance since it updates its internal start time and repeat
            // count)
            if (mMaxPrevChildTime > childTime) {
                childTime = mMaxPrevChildTime;
            } else {
                mMaxPrevChildTime = childTime;
            }
            isAnimationStillRunning = super.getTransformation(childTime, outTransformation)
                    || interpolatorHasMore;
            if (mAnimationUpdateListener != null && !isPaused() && !mIsCanceled) {
                // notify listener
                mAnimationUpdateListener.onAnimationUpdate(this, elapsedTime);
            }
            mLastTransformation.set(outTransformation);
        }
        if (mClippedDuration >= 0 && elapsedTime >= mClippedDuration) {
            cancel();
        }
        if (mIsCanceled) {
            return false;
        }
        return isAnimationStillRunning;
    }

    public long getElapsedTime() {
        return Math.max(0, Math.min(
                mMostRecentActiveTime - mAnimationSetStartTime - mPreviousPausedTime,
                mClippedDuration >= 0
                        ? mClippedDuration
                        : Long.MAX_VALUE));
    }

    public void pause() {
        if (isPaused()) {
            return;
        }
        mPauseStartTime = AnimationUtils.currentAnimationTimeMillis();
    }

    public void resume() {
        if (!isPaused()) {
            return;
        }
        mPreviousPausedTime += AnimationUtils.currentAnimationTimeMillis() - mPauseStartTime;
        mPauseStartTime = 0;
    }

    public boolean isPaused() {
        return mPauseStartTime > 0;
    }

    /* package */ void tempEnd() {
        if (mIsTempEnded) {
            return;
        }
        mIsTempEnded = true;
        pause();
    }

    /* package */ void resumeFromTempEnd() {
        if (!mIsTempEnded) {
            return;
        }
        mIsTempEnded = false;
        if (mLastTransformation != null) {
            // call into Animation#getTransformation to get it to set mStarted to true in case we
            // cancel before the framework calls it because without this, we wouldn't send the
            // animation end event (since super manages that for us and relies on mStarted)
            getTransformation(mMostRecentActiveTime > 0
                    ? mMostRecentActiveTime
                    : mAnimationSetStartTime,
                    new Transformation());
        }
        resume();
    }

    /* package */ boolean isTempEnded() {
        return mIsTempEnded;
    }

    public void cancelInPlace() {
        mIsCancelingInPlace = true;
        cancel();
    }

    @Override
    public void cancel() {
        mIsCanceled = true;
        // for some reason AnimationSets don't cancel their children, which results in the animation
        // still continuing, so we'll traverse the children and cancel them all
        for (Animation childAnimation : getAnimations()) {
            callAllAnimations(childAnimation, Animation::cancel, true);
        }
        super.cancel();
    }

    public boolean isCanceled() {
        return mIsCanceled;
    }

    /**
     * Clip the total duration (combined start delay, start offset, and duration) of the animation.
     * This is similar to {@link #restrictDuration(long)}, but that immediately updates the repeat
     * count and duration, which skews the animation, rather than just stopping it at at a certain
     * point. This won't impact any sequencing or relative duration of any child animations and will
     * simply end the animation at a fixed point before the normal end. If this is larger than the
     * total duration, this will have no effect.
     * @param durationMillis The maximum number of milliseconds that this animation will run (not
     *                       including any time paused). Any negative value indicates that the
     *                       animation should not be clipped.
     */
    public void clipDuration(long durationMillis) {
        mClippedDuration = durationMillis >= 0 ? durationMillis : -1;
    }

    /**
     * Get the duration that this animation is going to be clipped to.
     * @return The duration that this animation will be clipped to or -1 if it isn't clipped.
     */
    public long getClippedDuration() {
        return mClippedDuration;
    }

    /**
     * Set the initial delay for running this animation. This is different from
     * {@link #setStartOffset(long)} because this delay only ever occurs once in the total duration
     * of the animation. The start offset gets included in any child animations that repeat so that
     * every repeat includes the delay from the start offset.
     * @param startDelay The initial delay for the animation in milliseconds.
     */
    public void setStartDelay(long startDelay) {
        if (startDelay < 0) {
            Log.e(TAG, "Start delay can't be negative");
            mStartDelay = 0;
        } else {
            mStartDelay = startDelay;
        }
    }

    /**
     * Get the initial delay for running this animation. This is different from
     * {@link #getStartOffset()} because this delay only ever occurs once in the total duration
     * of the animation. The start offset gets included in any child animations that repeat so that
     * every repeat includes the delay from the start offset.
     * @return The initial delay for the animation in milliseconds.
     */
    public long getStartDelay() {
        return mStartDelay;
    }

    @Override
    protected void ensureInterpolator() {
        if (getInterpolator() == null) {
            setInterpolator(new LinearInterpolator());
        }
    }

    @Override
    public boolean getFillAfter() {
        if (mIsCancelingInPlace || mIsTempEnded) {
            return true;
        }
        return super.getFillAfter();
    }

    /**
     * Set the interpolator for this animation. The interpolator is not allowed to go in reverse. If
     * it tries, the animation will essentially pause at the previous position until it starts going
     * forward.
     * @param i The interpolator which defines the acceleration curve.
     */
    @Override
    public void setInterpolator(Interpolator i) {
        super.setInterpolator(i);
    }

    public void setAnimationUpdateListener(AnimationUpdateListener listener) {
        mAnimationUpdateListener = listener;
    }

    private static void callAllAnimations(Animation animation,
                                          Consumer<Animation> animationAction,
                                          boolean callChildFirst) {
        if (!callChildFirst) {
            animationAction.accept(animation);
        }
        if (animation instanceof AnimationSet) {
            for (Animation childAnimation : ((AnimationSet) animation).getAnimations()) {
                callAllAnimations(childAnimation, animationAction, callChildFirst);
            }
        }
        if (callChildFirst) {
            animationAction.accept(animation);
        }
    }

    /**
     * Calculate the expected total duration for running an animation. This includes any start
     * delay, start offset, and repetition.
     * @param animation The animation to check.
     * @param view The to be animated. This only necessary if the animation isn't already
     *             initialized. If this is null, the animation needs to have already been
     *             initialized or else values from parent AnimationSets won't have propagated to the
     *             children and this calculation could be incorrect.
     * @return The expected total duration for the animation or {@link Animation#INFINITE} if any
     *         part of the animation has an infinite repeat.
     */
    /* package */ static long calcTotalDuration(Animation animation, View view) {
        if (animation instanceof AnimationSet) {
            if (view != null) {
                ViewGroup viewParent = view.getParent() instanceof ViewGroup
                        ? (ViewGroup) view.getParent()
                        : null;
                // trigger initialize to get the values from AnimationSet to propagate to the
                // children
                animation.initialize(view.getWidth(), view.getHeight(),
                        viewParent != null ? viewParent.getWidth() : 0,
                        viewParent != null ? viewParent.getHeight() : 0);
            }
            long maxDuration = 0;
            for (Animation childAnimation : ((AnimationSet) animation).getAnimations()) {
                long duration = calcTotalDuration(childAnimation, view);
                if (view != null) {
                    childAnimation.reset();
                }
                if (duration == Animation.INFINITE) {
                    maxDuration = Animation.INFINITE;
                } else if (maxDuration >= 0 && duration > maxDuration) {
                    maxDuration = duration;
                }
            }
            if (maxDuration != Animation.INFINITE && animation instanceof EnhancedAnimationSet) {
                maxDuration += ((EnhancedAnimationSet) animation).getStartDelay();
            }
            if (animation instanceof EnhancedAnimationSet) {
                long clippedDuration = ((EnhancedAnimationSet) animation).getClippedDuration();
                if (clippedDuration >= 0 && clippedDuration < maxDuration) {
                    maxDuration = clippedDuration;
                }
            }
            if (view != null) {
                animation.reset();
            }
            return maxDuration;
        } else {
            if (animation.getStartOffset() + animation.getDuration() <= 0) {
                return 0;
            }
            if (animation.getRepeatCount() == Animation.INFINITE) {
                return Animation.INFINITE;
            }
            return animation.computeDurationHint();
        }
    }

    public interface AnimationUpdateListener {
        void onAnimationUpdate(EnhancedAnimationSet animation, long elapsedTime);
    }
}
