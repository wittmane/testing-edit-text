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

import static com.wittmane.testingedittext.util.DrawableUtils.DRAWABLE_LEVEL_MAX;

import android.app.Activity;
import android.app.Dialog;
import android.app.FragmentManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.Gravity;
import android.view.RoundedCorner;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewParent;
import android.view.WindowInsets;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.animation.PartialAnimationListener;
import com.wittmane.testingedittext.function.Supplier;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


public class BackHandler {
    private static final String TAG = BackHandler.class.getSimpleName();

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

    // this value was determined by measuring the default duration of the transitions (both fragment
    // transitions with default values and the activity back transition) measuring wasn't super
    // precise, so a nice round number that was close was picked.
    private static final int DEFAULT_TRANSITION_DURATION = 300;

    private final Context mContext;
    private final FragmentManager mFragmentManager;
    private final BackNavigationManager mBackNavigationManager;
    private final Supplier<OnBackInvokedDispatcher> mOnBackInvokedDispatcherSupplier;
    private final int mAnimationStyle;
    private boolean mIsBackCallbackRegistered = false;
    private final BackCallback mBackCallback =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    ? new OnBackCallbackWithAnimation()
                    : Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            ? new OnBackCallback()
                            : new BackCallback();

    public static void setUpPredictiveBack(Dialog dialog) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // predictive back animations aren't supported in older versions
            return;
        }
        new com.wittmane.testingedittext.util.BackHandler(dialog.getContext(),
                null,
                dialog::getOnBackInvokedDispatcher,
                new BackNavigationManager() {
                    @Override
                    public View getAnimatingView() {
                        return dialog.findViewById(android.R.id.content).getRootView();
                    }

                    @Override
                    public void onNavigateBack(boolean isTransientAction) {
                        dialog.cancel();
                    }
                },
                ANIMATION_STYLE_SYMMETRIC)
                .setUp();
    }

    public BackHandler(Activity activity,
                       BackNavigationManager previousContentManager,
                       @AnimationStyle int animationStyle) {
        this(activity, activity.getFragmentManager(),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ? activity::getOnBackInvokedDispatcher
                        : null,
                previousContentManager, animationStyle);
    }

    private BackHandler(Context context,
                        FragmentManager fragmentManager,
                        Supplier<OnBackInvokedDispatcher> onBackInvokedDispatcherSupplier,
                        BackNavigationManager backNavigationManager,
                        @AnimationStyle int animationStyle) {
        mContext = context;
        mFragmentManager = fragmentManager;
        mOnBackInvokedDispatcherSupplier = onBackInvokedDispatcherSupplier;
        mBackNavigationManager = backNavigationManager;
        mAnimationStyle = animationStyle;
    }

    public void setUp() {
        updateBackCallbackRegistrationState();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && mFragmentManager != null) {
            mFragmentManager.addOnBackStackChangedListener(
                    this::updateBackCallbackRegistrationState);
        }
    }

    public void tearDown() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && mFragmentManager != null) {
            mFragmentManager.removeOnBackStackChangedListener(
                    this::updateBackCallbackRegistrationState);
        }
    }

    private void updateBackCallbackRegistrationState() {
        // use the new APIs for predictive back handling starting in Android 13. prior to Android,
        // #onBackPressed gets called and the parent class handles navigation appropriately.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (!(mBackCallback instanceof OnBackInvokedCallback)) {
            Log.e(TAG, "unable to register back callback");
            return;
        }
        if (mFragmentManager != null) {
            // without the back callback registered, the predictive back animation is shown, but it
            // goes away when the callback is registered. without the back callback registered, the
            // back navigation bar button and gesture return to the previous activity rather than
            // traverse up the back stack. have the back callback registered when there are entries
            // in the back stack to properly support going to the previous fragment, but once the
            // back stack is empty, unregister the callback to get the activity-level predictive
            // back animation to appear. this pattern came from PreferenceActivity, but I'm not
            // certain if it did this for the same reason.
            if (mFragmentManager.getBackStackEntryCount() != 0) {
                registerOnBackInvokedCallback();
            } else {
                unregisterOnBackInvokedCallback();
            }
        } else {
            registerOnBackInvokedCallback();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void registerOnBackInvokedCallback() {
        if (mIsBackCallbackRegistered) {
            return;
        }
        mOnBackInvokedDispatcherSupplier.get().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                (OnBackInvokedCallback) mBackCallback);
        mIsBackCallbackRegistered = true;
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void unregisterOnBackInvokedCallback() {
        if (!mIsBackCallbackRegistered) {
            return;
        }
        mOnBackInvokedDispatcherSupplier.get().unregisterOnBackInvokedCallback(
                (OnBackInvokedCallback) mBackCallback);
        mIsBackCallbackRegistered = false;
    }

    private boolean shouldManageContentBehind() {
        return mFragmentManager != null;
    }

    public boolean isPredictiveBackEnabled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                || !(mBackCallback instanceof OnBackCallbackWithAnimation)) {
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // although predictive back animations were added for apps (OnBackCallbackWithAnimation)
            // in Android 14, the system predictive back animations on Android 13 and 14 were hidden
            // behind a dev option setting. we should respect this setting because it's weird to
            // show the predictive back animation between fragments and on popups when the animation
            // isn't shown between activities. this setting seems to at least internally exist after
            // Android 14, but it doesn't seem to be respected in Android 15 and beyond (predictive
            // back is always enabled), so we'll only check this for Android 14 to match.
            try {
                // based on com.android.settings.development.BackAnimationPreferenceController (AOSP
                // Settings app)
                int enableBackAnimation = Settings.Global.getInt(mContext.getContentResolver(),
                        "enable_back_animation");
                return enableBackAnimation == 1;
            } catch (SettingNotFoundException e) {
                return false;
            }
        }
        return true;
    }

    public void navigateBack(boolean skipShowingPrevious, Runnable onNavigateBack) {
        mBackCallback.navigateBack(skipShowingPrevious, onNavigateBack);
    }

    public boolean isPredictiveBackInProgress() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    && mBackCallback instanceof OnBackCallbackWithAnimation
                    && ((OnBackCallbackWithAnimation) mBackCallback).isInProgress();
    }

    public interface BackNavigationManager {
        View getAnimatingView();

        default void showPreviousContent(boolean isShowingForPredictiveBack, Runnable onReady) {
            run(onReady);
        }

        default void hidePreviousContent(Runnable onHidden) {
            run(onHidden);
        }

        default long getExitTransitionDuration() {
            return DEFAULT_TRANSITION_DURATION;
        }

        void onNavigateBack(boolean isTransientAction);
    }

    private class BackCallback {

        public void navigateBack(boolean skipShowingPrevious, Runnable onNavigateBack) {
            // handle any setup for the back action/animation (such as unhiding the previous
            // fragment). then immediately trigger the back invoked handling (such as removing the
            // current fragment).
            prepBack(skipShowingPrevious,
                    () -> invokeBack(skipShowingPrevious, onNavigateBack));

        }

        protected void prepBack(boolean skipShowingPrevious, Runnable onReady) {
            if (skipShowingPrevious) {
                run(onReady);
                return;
            }
            mBackNavigationManager.showPreviousContent(false, onReady);
        }

        protected void invokeBack(boolean isTransientAction, Runnable onNavigateBack) {
            mBackNavigationManager.onNavigateBack(isTransientAction);
            updateBackCallbackRegistrationState();
            run(onNavigateBack);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private class OnBackCallback extends BackCallback implements OnBackInvokedCallback {

        @Override
        public void onBackInvoked() {
            navigateBack(false, null);
        }
    }

    // manually animate the back gesture to match the system animations. based on
    // https://github.com/android/animation-samples/blob/main/Motion/app/src/main/java/com/example/android/motion/demo/containertransform/CheeseArticleFragment.kt
    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private class OnBackCallbackWithAnimation extends OnBackCallback
            implements OnBackAnimationCallback {
        private final PathInterpolator mGestureInterpolator = new PathInterpolator(0f, 0f, 0f, 0f);

        private int mPredictiveBackMargin;
        private float mInitialTouchY = -1f;
        private View mAnimatingView;
        private Drawable mOriginalBackground;
        private boolean mOriginalClipToOutline;
        private LinearLayout mDarkOverlay;
        private boolean mIsBackProgressed = false;

        @Override
        public void onBackStarted(@NonNull BackEvent backEvent) {
            prepBack(false, null);
        }

        @Override
        protected void prepBack(boolean skipShowingPrevious, Runnable onReady) {
            mAnimatingView = isPredictiveBackEnabled()
                    ? mBackNavigationManager.getAnimatingView()
                    : null;
            mPredictiveBackMargin =
                    mContext.getResources().getDimensionPixelSize(R.dimen.predictive_back_margin);
            if (mAnimatingView == null || !shouldManageContentBehind()) {
                run(onReady);
                return;
            }

            // end any active transitions to make this predictive back animation show correctly
            // immediately and avoid potentially leaving a fragment hidden when going back to it due
            // to mixed up transition state tracking
            ViewParent parent = mAnimatingView.getParent();
            if (parent instanceof ViewGroup) {
                TransitionManager.endTransitions((ViewGroup) parent);
            }

            if (!ensureBackground()) {
                // we won't be able to prevent the previous fragment from overlapping with the
                // current fragment, so we shouldn't try to unhide the previous fragment. all we'll
                // show is the animation of the content of the current fragment shifting.
                skipShowingPrevious = true;
            }

            if (skipShowingPrevious) {
                run(onReady);
                return;
            }

            mBackNavigationManager.showPreviousContent(onReady == null, () -> {
                addDarkOverlay();
                run(onReady);
            });
        }

        @Override
        public void onBackProgressed(@NonNull BackEvent backEvent) {
            if (mAnimatingView == null) {
                return;
            }
            if (backEvent.getProgress() > 0f) {
                mIsBackProgressed = true;
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
            mIsBackProgressed = false;
            if (mAnimatingView == null) {
                return;
            }
            mAnimatingView.setTranslationX(0f);
            mAnimatingView.setTranslationY(0f);
            mAnimatingView.setScaleX(1f);
            mAnimatingView.setScaleY(1f);

            if (!shouldManageContentBehind()) {
                mAnimatingView = null;
                return;
            }

            // wait until the fragment finishes visibly getting removed to replace the current
            // fragment's background (likely with nothing) to avoid a flash of the previous fragment
            // overlapping. since this transition is behind the current fragment, just transition
            // immediately.
            View animatingView = mAnimatingView;
            Drawable originalBackground = mOriginalBackground;
            boolean originalClipToOutline = mOriginalClipToOutline;
            Runnable resetBackground = () -> {
                if (animatingView.getBackground() != originalBackground) {
                    animatingView.setBackground(originalBackground);
                }
                if (animatingView.getClipToOutline() != originalClipToOutline) {
                    animatingView.setClipToOutline(originalClipToOutline);
                }
            };
            mBackNavigationManager.hidePreviousContent(resetBackground);

            removeDarkOverlay(true);

            mAnimatingView = null;
            mOriginalBackground = null;
        }

        @Override
        public void onBackInvoked() {
            invokeBack(false, null);
        }

        @Override
        protected void invokeBack(boolean isTransientAction, Runnable onNavigateBack) {
            Runnable navigateBack = () -> {
                removeDarkOverlay(isTransientAction);
                super.invokeBack(isTransientAction, onNavigateBack);
            };

            if (!isTransientAction && shouldManageContentBehind()) {
                // unhide if it isn't already
                mBackNavigationManager.showPreviousContent(mIsBackProgressed, navigateBack);
            } else {
                navigateBack.run();
            }
            mIsBackProgressed = false;
            mAnimatingView = null;
            mOriginalBackground = null;
        }

        public boolean isInProgress() {
            return mAnimatingView != null;
        }

        /**
         * Ensure the current fragment directly has a background to prevent overlapping with the
         * previous fragment when that is unhidden. If the background under the fragment (either its
         * direct background, some ancestor, or the base activity default) is a simple color
         * (ignoring transparent backgrounds), this will create a new background matching that.
         * Alternatively, this will copy any more complex drawable background. Additionally, if
         * possible, this will create the new background with rounded corners matching the device's
         * corners to match behavior from activity predictive back animations (card matching the
         * screen shape slides away).
         * @return Whether the current fragment has a background now.
         */
        private boolean ensureBackground() {
            mOriginalBackground = mAnimatingView.getBackground();
            mOriginalClipToOutline = mAnimatingView.getClipToOutline();
            Drawable background = DrawableUtils.getNearestBackground(mAnimatingView);
            int originalNearestBackgroundColor;
            if (background == null) {
                originalNearestBackgroundColor = ResourceUtils.getColor(
                        android.R.attr.colorBackground, mAnimatingView.getContext());
            } else if (background instanceof ColorDrawable) {
                originalNearestBackgroundColor = ((ColorDrawable) background).getColor();
            } else {
                originalNearestBackgroundColor = Color.TRANSPARENT;
            }
            Drawable drawable;
            if (originalNearestBackgroundColor != Color.TRANSPARENT) {
                drawable = createRoundedDrawable(mAnimatingView, originalNearestBackgroundColor);
            } else {
                // ideally we would round the corners on this too, but I'm not sure that there is a
                // good way to do that on any random drawable
                drawable = DrawableUtils.copyDrawable(background);
                if (drawable == null && mOriginalBackground == null) {
                    // we can't recreate the background and there isn't an existing background to
                    // reuse
                    return false;
                }
            }
            if (drawable != null) {
                // clip to exclude the portion of the view that is under the action bar
                ClipDrawable clipDrawable =
                        new ClipDrawable(drawable, Gravity.BOTTOM, ClipDrawable.VERTICAL);
                int viewHeight = mAnimatingView.getHeight();
                int paddingTop = mAnimatingView.getPaddingTop();
                clipDrawable.setLevel(
                        DRAWABLE_LEVEL_MAX * (viewHeight - paddingTop) / viewHeight);
                mAnimatingView.setBackground(clipDrawable);

                mAnimatingView.setClipToOutline(true);
            }
            return true;
        }

        private void addDarkOverlay() {
            // add a semi-transparent overlay between the previous fragment and the current fragment
            // to give a better distinction between the two and match behavior from activity
            // predictive back animations
            LinearLayout darkOverlay = new LinearLayout(mAnimatingView.getContext());
            darkOverlay.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            darkOverlay.setBackgroundColor(Color.argb(0.5f, 0f, 0f, 0f));
            if (addSiblingBefore(darkOverlay, mAnimatingView)) {
                mDarkOverlay = darkOverlay;
            }
        }

        private void removeDarkOverlay(boolean immediate) {
            if (mDarkOverlay == null) {
                return;
            }
            if (immediate) {
                ((ViewGroup) mDarkOverlay.getParent()).removeView(mDarkOverlay);
            } else {
                // have the dark overlay fade out before removing it (basically fading in the
                // previous fragment as it becomes the current again while the current slides out to
                // be removed). since the current fades as it slides out, the overlay should
                // disappear a bit before the current fragment's transition completes.
                Animation animation = new AlphaAnimation(1f, 0f);
                long duration = mBackNavigationManager.getExitTransitionDuration();
                if (duration >= 0) {
                    animation.setDuration(duration / 2);
                } else {
                    animation.setDuration(DEFAULT_TRANSITION_DURATION / 2);
                }
                final View darkOverlay = mDarkOverlay;
                animation.setAnimationListener(new PartialAnimationListener() {
                    @Override
                    public void onAnimationEnd(Animation animation) {
                        ((ViewGroup) darkOverlay.getParent()).removeView(darkOverlay);
                    }
                });
                mDarkOverlay.setAnimation(animation);
            }
            mDarkOverlay = null;
        }
    }

    private static boolean addSiblingBefore(View viewToInsert, View sibling) {
        ViewParent viewParent = sibling.getParent();
        if (viewParent instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) viewParent;
            for (int i = 0; i < parent.getChildCount(); i++) {
                if (parent.getChildAt(i) == sibling) {
                    parent.addView(viewToInsert, i);
                    return true;
                }
            }
        }
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private static ShapeDrawable createRoundedDrawable(View view, int color) {
        int topRightRadius;
        int topLeftRadius;
        int bottomRightRadius;
        int bottomLeftRadius;
        if (EdgeToEdgeUtils.isEdgeToEdgeEnforced()) {
            WindowInsets insets = view.getRootWindowInsets();
            RoundedCorner topLeft =
                    insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT);
            RoundedCorner topRight =
                    insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT);
            RoundedCorner bottomLeft =
                    insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT);
            RoundedCorner bottomRight =
                    insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT);
            topRightRadius = topRight != null ? topRight.getRadius() : 0;
            topLeftRadius = topLeft != null ? topLeft.getRadius() : 0;
            bottomRightRadius = bottomRight != null ? bottomRight.getRadius() : 0;
            bottomLeftRadius = bottomLeft != null ? bottomLeft.getRadius() : 0;
        } else {
            // without edge-to-edge, the activity won't be flush with the top/bottom of the device,
            // so rounding the corners to match the device doesn't make sense since the round edge
            // won't start at the device edge. it will be inset some and just look weird.
            topRightRadius = 0;
            topLeftRadius = 0;
            bottomRightRadius = 0;
            bottomLeftRadius = 0;
        }
        RoundRectShape rectShape = new RoundRectShape(new float[] {
                topLeftRadius, topLeftRadius,
                topRightRadius, topRightRadius,
                bottomRightRadius, bottomRightRadius,
                bottomLeftRadius, bottomLeftRadius
        }, null, null);
        ShapeDrawable shapeDrawable = new ShapeDrawable(rectShape);
        shapeDrawable.getPaint().setColor(color);
        shapeDrawable.getPaint().setStyle(Paint.Style.FILL);
        shapeDrawable.getPaint().setAntiAlias(true);
        shapeDrawable.getPaint().setFlags(Paint.ANTI_ALIAS_FLAG);
        return shapeDrawable;
    }

    private static void run(Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }
}
