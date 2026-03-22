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

import static com.wittmane.testingedittext.animation.EnhancedAnimationSet.calcTotalDuration;

import android.animation.Animator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;

import com.wittmane.testingedittext.function.BiConsumer;
import com.wittmane.testingedittext.function.Supplier;

/**
 * An {@link Animator} that matches a specified {@link Animation} to allow effectively using an
 * {@link Animation} in places that require an {@link Animator}.
 *
 * <p>Animator and Animation have similar APIs, but they differ in a few ways, particularly related
 * to the start delay/offset. These differences need to be reconciled when wrapping an Animation
 * to function as an Animator. Due to inconsistencies explained below, this can't just translate the
 * seemingly corresponding values between the Animator and the wrapped Animation. Instead, this will
 * treat the wrapped Animation as a fixed unit, so that the Animator's duration equates to the full
 * Animation (start offset, duration, and any repeats) and Animator's start delay is an entirely
 * separate and additional delay from the wrapped Animation's start offset. Adjusting the duration
 * of the Animator will scale the full Animation, unless the Animation has an infinite duration, in
 * which case it will be clipped to any duration specified on the Animator.</p>
 *
 * <p>Since Animator doesn't require supporting repetition, we won't bother implementing that (an
 * additional level of repeat from the wrapped Animation) since we don't have an immediate need for
 * it. Additionally, this doesn't support any means to modify specific aspects of the wrapped
 * Animation (adjusting the total duration is the only option), but since the Animation creator is
 * passed, the caller could handle making any necessary modifications before this gets the core
 * Animation.</p>
 *
 * <h6>Animator and Animation differences:</h6>
 *
 * <p>Animator has a start delay and Animation has a start offset, which are conceptually similar,
 * but AnimatorSet and ValueAnimator (the only public direct subclasses of Animator) restrict the
 * start delay to be non-negative, whereas Animation simply accepts any value (which can allow it to
 * immediately jump to some midpoint of the animation).</p>
 *
 * <p>Animation calls onAnimationStart effectively immediately when the animation is triggered to
 * start, but Animator waits to call onAnimationStart until after the start delay (when the
 * animation visibly starts), which means that Animator's onAnimationStart listener can't just be
 * triggered from Animation's onAnimationStart listener. An odd thing to note related to this timing
 * is that due to Animator waiting until after the delay to call onAnimationStart, if it is paused
 * before the start delay completes, onAnimationPause can get called before onAnimationStart.</p>
 *
 * <p>Additionally, when an Animation repeats, the whole animation sequence (i.e. start offset and
 * duration) gets processed, but when an Animator repeats, only the visible animation is repeated
 * (i.e. only the duration, not the start delay). Since these differ in this conflicting way, a
 * repeat defined in an Animator can't just be taken and directly used as a repeat in the wrapped
 * Animation to have the same visual effect if there is a non-zero start offset. In both an
 * Animation and an Animator, onAnimationStart and onAnimationEnd are each only called a single
 * time, regardless of having any number of repeats.</p>
 */
public class AnimationAnimator extends Animator {
    private static final String TAG = AnimationAnimator.class.getSimpleName();

    private final View mView;
    private final Supplier<Animation> mAnimationCreator;
    private final boolean mEndAnimationOnPause;

    private Interpolator mInterpolator;
    private long mStartDelay;
    private boolean mIsDurationSet;
    private long mDuration;

    private EnhancedAnimationSet mAnimationSet;
    private boolean mIsRunning = false;

    public AnimationAnimator(View view, Context context, int animationResId) {
        this(view, context, animationResId, false);
    }

    /* package */ AnimationAnimator(View view, Context context, int animationResId,
                                    boolean endAnimationOnPause) {
        this(view, () -> AnimationUtils.loadAnimation(context, animationResId),
                endAnimationOnPause);
    }

    public AnimationAnimator(View view, Supplier<Animation> animationCreator) {
        this(view, animationCreator, false);
    }

    /* package */ AnimationAnimator(View view, Supplier<Animation> animationCreator,
                                    boolean endAnimationOnPause) {
        mView = view;
        mAnimationCreator = animationCreator;
        mEndAnimationOnPause = endAnimationOnPause;

        // load default duration from the animation
        Animation animation = mAnimationCreator.get();
        long totalDuration = calcTotalDuration(animation, view);
        mDuration = totalDuration == Animation.INFINITE
                ? (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                        ? Animator.DURATION_INFINITE
                        : Long.MAX_VALUE)
                : totalDuration;
    }

    private void resetState() {
        mIsRunning = false;
        mAnimationSet = null;
    }

    @Override
    public synchronized void start() {
        if (isStarted()) {
            return;
        }

        // wrap the provided Animation in a custom AnimationSet that supports pausing and some other
        // necessary additional features
        mAnimationSet = new EnhancedAnimationSet();
        mAnimationSet.addAnimation(mAnimationCreator.get());
        mAnimationSet.setStartDelay(mStartDelay);
        if (mIsDurationSet) {
            long totalDuration = calcTotalDuration(mAnimationSet, mView);
            if (totalDuration == Animation.INFINITE) {
                mAnimationSet.clipDuration(mDuration);
            } else if (mDuration != totalDuration && totalDuration > 0) {
                mAnimationSet.scaleCurrentDuration(
                        (float) mDuration / (totalDuration - mStartDelay));
            }
        }
        if (mInterpolator != null) {
            mAnimationSet.setInterpolator(mInterpolator);
        }

        mAnimationSet.setAnimationListener(new PartialAnimationListener() {
            @Override
            public void onAnimationEnd(Animation animation) {
                synchronized (AnimationAnimator.this) {
                    if (animation != mAnimationSet) {
                        // ignore events from an old wrapper (previous intermediate animation)
                        return;
                    }
                    if (!isStarted()) {
                        // this shouldn't happen. if we haven't even started or already canceled or
                        // finished, there shouldn't be anything to end.
                        return;
                    }
                    if (mEndAnimationOnPause && mAnimationSet.isTempEnded()) {
                        // ignore the internal animation ending since it ended for a pause
                        return;
                    }
                    resetState();
                    // at least in some cases when there are multiple animations running at the same
                    // time, the animations are placed on some OverlayViewGroup, and this gets
                    // called from within that group's draw loop for its children. calling
                    // onAnimationEnd will remove this view from the group, so if this view isn't
                    // the last child, the loop won't find a child in the last index (since the
                    // other views will shift indices), and the framework doesn't handle that
                    // gracefully, so it would result in null pointer exception. to avoid the
                    // removal breaking things, queue up onAnimationEnd to get called after we're
                    // out of that loop.
                    Handler handler = mView.getHandler();
                    if (handler != null) {
                        handler.post(() -> notifyListener(AnimatorListener::onAnimationEnd));
                    } else {
                        // the view probably is orphaned at this point, so the listener probably
                        // doesn't matter, but this probably also means we're not in the problematic
                        // loop, so calling the listener is probably fine, so just do that for
                        // completeness
                        notifyListener(AnimatorListener::onAnimationEnd);
                    }
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
                // this shouldn't ever happen since we're wrapping the animation in an animation set
                // which should never be set to repeat and doesn't even support repeating
                Log.e(TAG, "animation repeated unexpectedly");
            }
        });
        mAnimationSet.setAnimationUpdateListener((animation, elapsedTime) -> {
            synchronized (AnimationAnimator.this) {
                if (animation != mAnimationSet) {
                    // ignore events from an old wrapper (previous intermediate animation)
                    return;
                }
                if (!isStarted()) {
                    // if we haven't even started or already canceled or finished, we shouldn't
                    // suddenly jump to running
                    return;
                }
                if (isRunning()) {
                    // we already got to started and running, so we should have already notified the
                    // listener and shouldn't do it again
                    return;
                }
                // we could consider having the AnimationSet track whether the transformation
                // changed to allow only triggering onAnimationStart after the start delay + start
                // offset and the animation visibly starting, but based on how we're treating the
                // whole wrapped Animation as a unit, I think it makes the most sense to just say
                // that the animation started when the wrapped Animation is scheduled to run, even
                // if that happens to include a visible delay. also, if the animation happens to
                // never change the transformation for some reason, it would be odd to only have
                // onAnimationEnd (or artificially trigger onAnimationStart immediately before
                // onAnimationEnd, or maybe we shouldn't even have onAnimationEnd called since
                // nothing really happened, but that seems inappropriate).
                if (elapsedTime >= animation.getStartDelay()) {
                    mIsRunning = true;
                    notifyListener(AnimatorListener::onAnimationStart);
                }
            }
        });

        mView.startAnimation(mAnimationSet);
    }

    @Override
    public synchronized void pause() {
        if (!isStarted() || isPaused() || mAnimationSet.isCanceled()) {
            return;
        }
        if (mEndAnimationOnPause) {
            mAnimationSet.tempEnd();
            // remove the animation from the view so that ViewGroup#removeViewInternal won't call
            // ViewGroup#addDisappearingView, which is only called if the view has an animation. see
            // ActivityAnimationTransition#createAnimator for more details about the issue.
            // unfortunately, this means that the view will reset to its original position, rather
            // than staying in place while paused. this end on pause functionality is only intended
            // as a workaround for the framework bug when being used in a Visibility Transition, so
            // the scope of this is small, and the transition only gets paused momentarily, so at
            // most, there may be a brief flash on the screen where the view suddenly shifts
            // positions, but I haven't ever noticed that happening. not that this end on pause
            // functionality is necessary for anything else, but ideally we could just skip this
            // call to leave the animation in its existing state (relying on getFillAfter, which
            // is used to prevent calling View#clearAnimation in ViewGroup#finishAnimatingView).
            mView.clearAnimation();
        } else {
            mAnimationSet.pause();
        }
        super.pause();
    }

    @Override
    public synchronized void resume() {
        if (!isStarted() || !isPaused() || mAnimationSet.isCanceled()) {
            return;
        }
        if (mEndAnimationOnPause) {
            mView.startAnimation(mAnimationSet);
            mAnimationSet.resumeFromTempEnd();
        } else {
            mAnimationSet.resume();
        }
        super.resume();
    }

    @Override
    public synchronized void cancel() {
        if (!isStarted()) {
            return;
        }
        ensureAnimationStartListenerNotified();
        super.cancel();
        notifyListener(AnimatorListener::onAnimationCancel);
        // leave the view at whatever intermediate position it's at currently
        mAnimationSet.cancelInPlace();
    }

    @Override
    public synchronized void end() {
        if (!isStarted()) {
            return;
        }
        ensureAnimationStartListenerNotified();
        super.end();
        // this jumps the animation to the end position
        mAnimationSet.cancel();
    }

    private void ensureAnimationStartListenerNotified() {
        if (mIsRunning) {
            return;
        }
        // make sure we notified animation start before we notify animation end. I can't tell from
        // the documentation that this is necessary, but it's what ValueAnimator does, so we'll be
        // consistent with that.
        mIsRunning = true;
        notifyListener(AnimatorListener::onAnimationStart);
    }

    @Override
    public synchronized boolean isRunning() {
        return isStarted() && mIsRunning;
    }

    @Override
    public synchronized boolean isStarted() {
        return mAnimationSet != null;
    }

    @Override
    public void setStartDelay(long startDelay) {
        if (startDelay < 0) {
            Log.e(TAG, "Start delay can't be negative");
            mStartDelay = 0;
        } else {
            mStartDelay = startDelay;
        }
    }

    @Override
    public long getStartDelay() {
        return mStartDelay;
    }

    @Override
    public Animator setDuration(long duration) {
        if (duration < 0) {
            throw new IllegalArgumentException("Invalid duration: " + duration);
        }
        mDuration = duration;
        mIsDurationSet = true;
        return this;
    }

    @Override
    public long getDuration() {
        return mDuration;
    }

    @Override
    public void setInterpolator(TimeInterpolator value) {
        if (value instanceof Interpolator || value == null) {
            mInterpolator = (Interpolator) value;
        } else {
            // wrap to get the correct interpolator type needed for an Animation since that requires
            // a subtype of what an Animator takes (with no functional difference in the interfaces)
            mInterpolator = new WrapperInterpolator(value);
        }
    }

    @Override
    public TimeInterpolator getInterpolator() {
        return mInterpolator instanceof WrapperInterpolator
                ? ((WrapperInterpolator) mInterpolator).mWrappedInterpolator
                : mInterpolator;
    }

    private void notifyListener(BiConsumer<AnimatorListener, AnimationAnimator> event) {
        AnimatorListener[] listeners = getListeners().toArray(new AnimatorListener[0]);
        for (AnimatorListener listener : listeners) {
            event.accept(listener, AnimationAnimator.this);
        }
    }

    @Override
    @NonNull
    public AnimationAnimator clone() {
        final AnimationAnimator animator = (AnimationAnimator) super.clone();

        // don't copy the running state since the copy can't actually be running and immediately in
        // the same state
        if (isStarted()) {
            Log.w(TAG, "clone called while the animation is active");
        }
        animator.resetState();

        return animator;
    }

    private static class WrapperInterpolator implements Interpolator {
        private final TimeInterpolator mWrappedInterpolator;

        public WrapperInterpolator(TimeInterpolator interpolator) {
            mWrappedInterpolator = interpolator;
        }

        @Override
        public float getInterpolation(float input) {
            return mWrappedInterpolator.getInterpolation(input);
        }
    }
}
