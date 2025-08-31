/*
 * Copyright (C) 2022-2025 Eli Wittman
 * Copyright 2019 The Android Open Source Project
 * Copyright (C) 2007 The Android Open Source Project
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

package com.wittmane.testingedittext.settings;

import static com.wittmane.testingedittext.settings.fragments.PerTestFieldSettingsFragment.FIELD_INDEX_BUNDLE_KEY;
import static com.wittmane.testingedittext.settings.fragments.PerTestGroupSettingsFragment.GROUP_INDEX_BUNDLE_KEY;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.text.TextUtils;
import android.transition.Fade;
import android.transition.Slide;
import android.transition.Transition;
import android.transition.Transition.TransitionListener;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.RoundedCorner;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewParent;
import android.view.WindowInsets;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.util.EdgeToEdgeUtils;
import com.wittmane.testingedittext.settings.fragments.DisplaySettingsFragment;
import com.wittmane.testingedittext.settings.fragments.MainSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.ModifyTextSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.ReturningTextSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.SystemBehaviorSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.TargetVersionSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.ComposingTextSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.ImeActionSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.ImeOptionsSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.InputTypeSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.TestFieldGroupListSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.TestFieldGroupSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.TestFieldSettingsFragment;

import java.util.HashSet;

public class SettingsActivity extends PreferenceActivity {
    private static final String TAG = SettingsActivity.class.getSimpleName();

    // use a consistent transition duration to keep all of the simultaneous transitions in sync.
    // this value was determined by measuring the default duration of the transitions (both fragment
    // transitions with default values and the activity back transition) measuring wasn't super
    // precise, so a nice round number that was close was picked.
    private static final int TRANSITION_DURATION = 300;

    public static final String FIELD_ID_BUNDLE_KEY = "FIELD_ID";
    private static final String FRAGMENT_TAG_PREFIX = "NavigationStackFragment";
    private static final char FRAGMENT_TAG_DIVIDER = '-';
    private static final String STATE_CURRENT_FRAGMENT_TAG = "STATE_CURRENT_FRAGMENT_TAG";

    private boolean mIsBackCallbackRegistered = false;
    private final OnBackInvokedCallback mOnBackInvokedCallback =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    ? new OnBackCallbackWithAnimation()
                    : Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            ? new OnBackCallback()
                            : null;
    private final FragmentManager.OnBackStackChangedListener mOnBackStackChangedListener =
            this::updateBackCallbackRegistrationState;

    /**
     * determine if fragments should be hidden (rather than replaced) as new fragments are added.
     * in order to support predictive back animation between fragments (OnBackAnimationCallback
     * added in Android 14), we need to manage hiding and unhiding the previous fragment, rather
     * than replace the fragment and let the framework manage that in a single transaction. we could
     * just do this for Android 14+ (and given that hiding seems atypical that may theoretically be
     * preferred), but due to the fact that hiding a fragment leaves it in the resumed state, this
     * will cause a mismatch of lifecycle events, which seems likely to result in bugs from
     * overlooking this difference in the versions, so we'll just hide it on all versions. I'm
     * leaving as a method at least for now, rather than just hard-coding the logic, to easily swap
     * functionality back if this ends up causing problems.
     * @return whether fragments should be hid in instead of replaced
     */
    private boolean shouldManageHidingFragments() {
        return true;
    }

    private String mCurrentFragmentTag;

    private int predictiveBackMargin;

    @Override
    protected void onCreate(final Bundle savedState) {
        setTheme(Settings.getThemeId(this));
        super.onCreate(savedState);

        predictiveBackMargin = getResources().getDimensionPixelSize(R.dimen.predictive_back_margin);

        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        if (savedState == null) {
            Bundle extras = getIntent().getExtras();
            Fragment f = null;
            if (extras != null) {
                int fieldId = extras.getInt(FIELD_ID_BUNDLE_KEY, -1);
                FieldPosition position = fieldId >= 0
                        ? Settings.getTestFieldPosition(fieldId)
                        : null;
                if (position != null) {
                    Bundle targetExtras = new Bundle();
                    targetExtras.putString(GROUP_INDEX_BUNDLE_KEY, "" + position.groupIndex);
                    targetExtras.putString(FIELD_INDEX_BUNDLE_KEY, "" + position.fieldIndex);
                    f = new TestFieldSettingsFragment();
                    f.setArguments(targetExtras);
                }
            }
            if (f == null) {
                f = new MainSettingsFragment();
            }
            addFragment(f, null);
        }
        // handle the insets excluding the bottom to support showing the preference list behind the
        // navigation bar
        EdgeToEdgeUtils.addInsetHandling(this, true, true, true, false);

        updateBackCallbackRegistrationState();
        getFragmentManager().addOnBackStackChangedListener(mOnBackStackChangedListener);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(STATE_CURRENT_FRAGMENT_TAG, mCurrentFragmentTag);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mCurrentFragmentTag = savedInstanceState.getString(STATE_CURRENT_FRAGMENT_TAG);
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref) {
        // (EW) based on PreferenceActivity#onPreferenceStartFragment and
        // PreferenceActivity#startPreferencePanel

        Fragment f = Fragment.instantiate(this, pref.getFragment(), pref.getExtras());
        addFragment(f, pref);
        return true;
    }

    private void addFragment(Fragment f, Preference pref) {
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        Fragment currentFragment = getCurrentFragment();
        mCurrentFragmentTag = createFragmentTag(currentFragment);
        if (currentFragment != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    && mOnBackInvokedCallback instanceof OnBackCallbackWithAnimation) {
                // have the current fragment fade out as the new fragment slides in. this is meant
                // to be similar to the inverse dark overlay/shadow over the previous
                // activity/fragment disappearing when completing a predictive back animation.
                Transition exitTransition = new Fade(Fade.MODE_OUT);
                if (TRANSITION_DURATION >= 0) {
                    exitTransition.setDuration(TRANSITION_DURATION);
                }
                currentFragment.setExitTransition(exitTransition);
            }
        }
        if (shouldManageHidingFragments()) {
            if (currentFragment != null) {
                // this needs to be part of a separate transaction for some reason or else we
                // can't show it behind the soon-to-be current fragment later for predictive
                // back
                getFragmentManager().beginTransaction().hide(currentFragment).commit();
            }
            transaction.add(android.R.id.content, f, mCurrentFragmentTag);
        } else {
            transaction.replace(android.R.id.content, f, mCurrentFragmentTag);
        }
        if (pref != null) {
            if (pref.getTitleRes() != 0) {
                transaction.setBreadCrumbTitle(pref.getTitleRes());
            } else if (pref.getTitle() != null) {
                transaction.setBreadCrumbTitle(pref.getTitle());
            }
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    && mOnBackInvokedCallback instanceof OnBackCallbackWithAnimation) {
                // add a transition (slide in) to pair with the predictive back animation (slide
                // out)
                Transition enterTransition = new Slide(Gravity.END);
                if (TRANSITION_DURATION >= 0) {
                    enterTransition.setDuration(TRANSITION_DURATION);
                }
                f.setEnterTransition(enterTransition);
            }
            transaction.addToBackStack(null);
        }
        transaction.commit();
    }

    private String createFragmentTag(Fragment parent) {
        String prefix;
        if (parent == null || TextUtils.isEmpty(parent.getTag())) {
            prefix = FRAGMENT_TAG_PREFIX;
        } else {
            prefix = parent.getTag() + FRAGMENT_TAG_DIVIDER;
        }
        FragmentManager fragmentManager = getFragmentManager();
        // make sure to create a unique tag
        int childNum = 0;
        while (fragmentManager.findFragmentByTag(prefix + childNum) != null) {
            childNum++;
        }
        return prefix + childNum;
    }

    public Fragment getCurrentFragment() {
        if (mCurrentFragmentTag == null) {
            return null;
        }
        return getFragmentManager().findFragmentByTag(mCurrentFragmentTag);
    }

    public Fragment getPreviousFragment() {
        Fragment currentFragment = getCurrentFragment();
        if (currentFragment == null) {
            return null;
        }
        return getPreviousFragment(currentFragment);
    }

    public Fragment getPreviousFragment(Fragment currentFragment) {
        if (currentFragment == null) {
            Log.e(TAG, "current fragment is null");
            return null;
        }
        String currentFragmentTag = currentFragment.getTag();
        String previousFragmentTag = getPreviousFragmentTag(currentFragmentTag);
        Fragment previousFragment = getFragmentManager().findFragmentByTag(previousFragmentTag);
        if (previousFragment == null) {
            Log.e(TAG, "couldn't find previous fragment with tag: " + previousFragmentTag);
        }
        return previousFragment;
    }

    private String getPreviousFragmentTag(String currentFragmentTag) {
        if (currentFragmentTag == null || !currentFragmentTag.startsWith(FRAGMENT_TAG_PREFIX)) {
            Log.e(TAG, "unexpected settings fragment tag: " + currentFragmentTag);
            return null;
        }
        int lastDivider = currentFragmentTag.lastIndexOf(FRAGMENT_TAG_DIVIDER);
        if (lastDivider < 0) {
            // no previous fragment
            return null;
        }
        return currentFragmentTag.substring(0, lastDivider);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            navigateBack();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void navigateBack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && mOnBackInvokedCallback instanceof OnBackCallbackWithAnimation) {
            // artificially trigger handling for the start of the back animation to set up the the
            // transition to match the swipe/long press (except for the scaling since there won't be
            // any progress). also, this will handle unhiding the previous fragment because the
            // standard back handling is only going to process undoing adding the current fragment
            // since that is all that was included as part of the back stack. then immediately
            // trigger the back invoked handling (remove the current fragment).
            ((OnBackCallbackWithAnimation) mOnBackInvokedCallback).onBackStarted();
            mOnBackInvokedCallback.onBackInvoked();
        } else {
            onBackPressed();
        }
    }

    @Override
    public boolean isValidFragment(final String fragmentName) {
        return MainSettingsFragment.class.getName().equals(fragmentName)
                || ModifyTextSettingsFragment.class.getName().equals(fragmentName)
                || SystemBehaviorSettingsFragment.class.getName().equals(fragmentName)
                || TargetVersionSettingsFragment.class.getName().equals(fragmentName)
                || ComposingTextSettingsFragment.class.getName().equals(fragmentName)
                || ReturningTextSettingsFragment.class.getName().equals(fragmentName)
                || TestFieldGroupListSettingsFragment.class.getName().equals(fragmentName)
                || TestFieldGroupSettingsFragment.class.getName().equals(fragmentName)
                || TestFieldSettingsFragment.class.getName().equals(fragmentName)
                || InputTypeSettingsFragment.class.getName().equals(fragmentName)
                || ImeOptionsSettingsFragment.class.getName().equals(fragmentName)
                || ImeActionSettingsFragment.class.getName().equals(fragmentName)
                || DisplaySettingsFragment.class.getName().equals(fragmentName);
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        if (shouldManageHidingFragments()
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                        || !(mOnBackInvokedCallback instanceof OnBackCallbackWithAnimation))) {
            // unhide the previous fragment (not necessary for the animated callback since that is
            // already done as part of the animation)
            Fragment previousFragment = getPreviousFragment(getCurrentFragment());
            if (previousFragment != null) {
                getFragmentManager().beginTransaction()
                        .show(previousFragment)
                        .commit();
            }
        }
        mCurrentFragmentTag = getPreviousFragmentTag(mCurrentFragmentTag);
        super.onBackPressed();
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private class OnBackCallback implements OnBackInvokedCallback {

        @Override
        public void onBackInvoked() {
            onBackPressed();
            updateBackCallbackRegistrationState();
            invalidateOptionsMenu();
        }
    }

    // (EW) manually animate the back gesture to match the system animations. based on
    // https://github.com/android/animation-samples/blob/main/Motion/app/src/main/java/com/example/android/motion/demo/containertransform/CheeseArticleFragment.kt
    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private class OnBackCallbackWithAnimation extends OnBackCallback
            implements OnBackAnimationCallback {
        private final PathInterpolator mGestureInterpolator = new PathInterpolator(0f, 0f, 0f, 0f);

        private float initialTouchY = -1f;
        private Fragment mPreviousFragment;
        private View mFragmentContent;
        private Drawable mOriginalBackground;
        private boolean mOriginalClipToOutline;
        private LinearLayout mDarkOverlay;
        private ViewGroup mTransitioningOutSceneRoot;

        @Override
        public void onBackStarted(@NonNull BackEvent backEvent) {
            onBackStarted();
        }

        public void onBackStarted() {
            Fragment currentFragment = getCurrentFragment();
            if (currentFragment == null) {
                return;
            }

            Transition returnTransition = new Slide(Gravity.END);
            if (TRANSITION_DURATION >= 0) {
                returnTransition.setDuration(TRANSITION_DURATION);
            }
            currentFragment.setReturnTransition(returnTransition);

            mFragmentContent = currentFragment.getView();
            if (mFragmentContent == null) {
                return;
            }

            synchronized (OnBackCallbackWithAnimation.this) {
                if (mTransitioningOutSceneRoot != null) {
                    // the cleanup from the previous canceled back didn't finish yet, so force the
                    // transition to end immediately so we can redo the things it's in the process
                    // of undoing
                    TransitionManager.endTransitions(mTransitioningOutSceneRoot);
                }
            }

            // if the background under the fragment (either its direct background, some ancestor, or
            // the base activity default) is a simple color (ignoring transparent backgrounds),
            // create a new background directly under the fragment to prevent overlapping with the
            // previous fragment when that is unhidden, and create it with rounded corners matching
            // the device's corners to match behavior from activity predictive back animations
            mOriginalBackground = mFragmentContent.getBackground();
            mOriginalClipToOutline = mFragmentContent.getClipToOutline();
            Drawable background = getNearestBackground(mFragmentContent);
            int originalNearestBackgroundColor;
            if (background == null) {
                final TypedArray a = getTheme().obtainStyledAttributes(new int[]{
                        android.R.attr.colorBackground
                });
                originalNearestBackgroundColor = a.getColor(0, Color.TRANSPARENT);
                a.recycle();
            } else if (background instanceof ColorDrawable) {
                originalNearestBackgroundColor = ((ColorDrawable) background).getColor();
            } else {
                originalNearestBackgroundColor = Color.TRANSPARENT;
            }
            if (originalNearestBackgroundColor != Color.TRANSPARENT) {
                setRoundedBackground(mFragmentContent, originalNearestBackgroundColor, true);
            }
            if (originalNearestBackgroundColor == Color.TRANSPARENT && mOriginalBackground == null) {
                // we can't recreate the background and there isn't an existing background to reuse,
                // so we won't be able to prevent the previous fragment from overlapping with the
                // current fragment, so we shouldn't try to unhide the previous fragment. all we'll
                // show is the animation of the content of the current fragment shifting.
                return;
            }

            // add a semi-transparent overlay between the previous fragment and the current fragment
            // to give a better distinction between the two and match behavior from activity
            // predictive back animations
            LinearLayout darkOverlay = new LinearLayout(getApplication());
            darkOverlay.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            darkOverlay.setBackgroundColor(Color.argb(0.5f, 0f, 0f, 0f));
            if (addSiblingBefore(darkOverlay, mFragmentContent)) {
                mDarkOverlay = darkOverlay;
            }

            // unhide the previous fragment
            Fragment previousFragment = getPreviousFragment(currentFragment);
            if (previousFragment != null) {
                mPreviousFragment = previousFragment;
                // clear the previous enter transition (slide in) so the current fragment can just
                // slide out to reveal this fragment behind it. ideally, a separate reenter
                // transition would be used, but since the hiding/unhiding has to be managed
                // separate from the back stack, the framework will just reuse the enter transition
                // that isn't appropriate here.
                mPreviousFragment.setEnterTransition(null);

                getFragmentManager().beginTransaction()
                        .show(mPreviousFragment)
                        .commit();
            }
        }

        @Override
        public void onBackProgressed(@NonNull BackEvent backEvent) {
            if (mFragmentContent == null) {
                return;
            }

            float progress = mGestureInterpolator.getInterpolation(backEvent.getProgress());
            if (initialTouchY < 0f) {
                initialTouchY = backEvent.getTouchY();
            }
            float progressY = mGestureInterpolator.getInterpolation(
                    (backEvent.getTouchY() - initialTouchY) / mFragmentContent.getHeight()
            );

            // See the motion spec about the calculations below.
            // https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back#motion-specs

            // Shift horizontally.
            Configuration config = getResources().getConfiguration();
            boolean isSwipingWithTransition = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
                            && backEvent.getSwipeEdge() == BackEvent.EDGE_NONE)
                    || (config.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                            ? backEvent.getSwipeEdge() == BackEvent.EDGE_RIGHT
                            : backEvent.getSwipeEdge() == BackEvent.EDGE_LEFT);
            // only shift if the swipe matches the direction the fragment is going to slide away (or
            // if the back button is held). otherwise, this will the fragment will just be scaled
            // and centered (similar to the animation for switching activities when swiping from the
            // other side).
            if (isSwipingWithTransition) {
                int maxTranslationX = (mFragmentContent.getWidth() / 20) - predictiveBackMargin;
                mFragmentContent.setTranslationX(progress * maxTranslationX *
                        ((config.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) ? -1 : 1));
            }

            // Shift vertically.
            int maxTranslationY = (mFragmentContent.getHeight() / 20) - predictiveBackMargin;
            mFragmentContent.setTranslationY(progressY * maxTranslationY);

            // Scale down from 100% to 90%.
            float scale = 1f - (0.1f * progress);
            mFragmentContent.setScaleX(scale);
            mFragmentContent.setScaleY(scale);
        }

        @Override
        public void onBackCancelled() {
            initialTouchY = -1f;
            if (mFragmentContent == null) {
                return;
            }
            mFragmentContent.setTranslationX(0f);
            mFragmentContent.setTranslationY(0f);
            mFragmentContent.setScaleX(1f);
            mFragmentContent.setScaleY(1f);

            if (mPreviousFragment != null) {
                if (mFragmentContent != null) {
                    // wait until the fragment finishes visibly getting removed to replace the
                    // background (likely with nothing) to avoid a flash of the previous fragment
                    // overlapping. since this transition is behind the current fragment, just
                    // transition immediately. this transition is only really needed for the
                    // callback to know when it's safe to replace the background of the current
                    // fragment. setting duration to 1, rather than 0 in case anything handles 0
                    // differently. 1 ms is effectively instantly, and depending on how it's
                    // actually implemented 0 ms still may have some delay for asynchronous handling
                    // and effectively be the same.
                    Transition exitTransition = new Fade(Fade.MODE_OUT);
                    exitTransition.setDuration(1);
                    synchronized (OnBackCallbackWithAnimation.this) {
                        ViewParent parent = mFragmentContent.getParent();
                        if (parent instanceof ViewGroup) {
                            mTransitioningOutSceneRoot = (ViewGroup) parent;
                        }
                    }
                    View fragmentContent = mFragmentContent;
                    Drawable originalBackground = mOriginalBackground;
                    boolean originalClipToOutline = mOriginalClipToOutline;
                    exitTransition.addListener(new TransitionListener() {
                        @Override
                        public void onTransitionCancel(Transition transition) { }

                        @Override
                        public void onTransitionEnd(Transition transition) {
                            fragmentContent.setBackground(originalBackground);
                            fragmentContent.setClipToOutline(originalClipToOutline);
                            synchronized (OnBackCallbackWithAnimation.this) {
                                mTransitioningOutSceneRoot = null;
                            }
                        }

                        @Override
                        public void onTransitionPause(Transition transition) { }

                        @Override
                        public void onTransitionResume(Transition transition) { }

                        @Override
                        public void onTransitionStart(Transition transition) { }
                    });
                    mPreviousFragment.setExitTransition(exitTransition);
                }

                getFragmentManager().beginTransaction()
                        .hide(mPreviousFragment)
                        .commit();
            } else {
                if (mFragmentContent != null) {
                    if (mFragmentContent.getBackground() != mOriginalBackground) {
                        mFragmentContent.setBackground(mOriginalBackground);
                        mFragmentContent.setClipToOutline(mOriginalClipToOutline);
                    }
                }
            }
            if (mDarkOverlay != null) {
                ((ViewGroup) mDarkOverlay.getParent()).removeView(mDarkOverlay);
            }
            mPreviousFragment = null;
            mFragmentContent = null;
            mOriginalBackground = null;
            mDarkOverlay = null;
        }

        @Override
        public void onBackInvoked() {
            if (mPreviousFragment != null && mPreviousFragment.isHidden()) {
                getFragmentManager().beginTransaction()
                        .show(mPreviousFragment)
                        .commit();
            }
            if (mDarkOverlay != null) {
                // have the dark overlay fade out before removing it (basically fading in the
                // previous fragment as it becomes the current again while the current slides out to
                // be removed)
                Animation animation = new AlphaAnimation(1f, 0f);
                if (TRANSITION_DURATION >= 0) {
                    animation.setDuration(TRANSITION_DURATION);
                }
                final View darkOverlay = mDarkOverlay;
                animation.setAnimationListener(new AnimationListener() {
                    @Override
                    public void onAnimationEnd(Animation animation) {
                        ((ViewGroup) darkOverlay.getParent()).removeView(darkOverlay);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {

                    }

                    @Override
                    public void onAnimationStart(Animation animation) {

                    }
                });
                mDarkOverlay.setAnimation(animation);
            }
            mPreviousFragment = null;
            mFragmentContent = null;
            mOriginalBackground = null;
            mDarkOverlay = null;
            super.onBackInvoked();
        }
    }

    private void updateBackCallbackRegistrationState() {
        // use the new APIs for predictive back handling starting in Android 13. prior to Android,
        // #onBackPressed gets called and the parent class handles navigation appropriately.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        // without the back callback registered the predictive back animation is shown, but it goes
        // away when the callback is registered. without the back callback registered, the back
        // navigation bar button and gesture return to the previous activity rather than traverse up
        // the back stack. have the back callback registered when there are entries in the back
        // stack to properly support going to the previous fragment, but once the back stack is
        // empty, unregister the callback to get the predictive back animation to appear. this
        // pattern came from PreferenceActivity, but I'm not certain if it did this for the same
        // reason.
        if (getFragmentManager().getBackStackEntryCount() != 0) {
            if (!mIsBackCallbackRegistered) {
                getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_DEFAULT, mOnBackInvokedCallback);
                mIsBackCallbackRegistered = true;
            }
        } else if (mIsBackCallbackRegistered) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(mOnBackInvokedCallback);
            mIsBackCallbackRegistered = false;
        }
    }

    @Override
    protected void onDestroy() {
        EdgeToEdgeUtils.removeInsetHandling(this);
        getFragmentManager().removeOnBackStackChangedListener(mOnBackStackChangedListener);
        super.onDestroy();
    }

    private static Drawable getNearestBackground(View v) {
        Drawable background = null;
        View currentView = v;
        // track the views traversed to avoid an infinite loop if a view lists itself (or some
        // descendant) as its parent
        HashSet<View> traversedViews = new HashSet<>();
        traversedViews.add(v);
        while (currentView != null) {
            background = currentView.getBackground();
            if (background instanceof ColorDrawable
                    && ((ColorDrawable) background).getColor() == Color.TRANSPARENT) {
                // ignore transparent backgrounds
                background = null;
            }
            if (background != null) {
                break;
            }
            ViewParent parent = v.getParent();
            if (parent instanceof ViewGroup && !traversedViews.contains(parent)) {
                currentView = (View) parent;
                traversedViews.add(currentView);
            } else {
                currentView = null;
            }
        }
        return background;
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
    private static void setRoundedBackground(View view, int color, boolean clipToContentTop) {
        WindowInsets insets = view.getRootWindowInsets();
        RoundedCorner topLeft = clipToContentTop
                ? null
                : insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT);
        RoundedCorner topRight = clipToContentTop
                ? null
                : insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT);
        RoundedCorner bottomLeft = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT);
        RoundedCorner bottomRight = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT);
        int topRightRadius = topRight != null ? topRight.getRadius() : 0;
        int topLeftRadius = topLeft != null ? topLeft.getRadius() : 0;
        int bottomRightRadius = bottomRight != null ? bottomRight.getRadius() : 0;
        int bottomLeftRadius = bottomLeft != null ? bottomLeft.getRadius() : 0;
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
        if (clipToContentTop) {
            // clip to exclude the portion of the view that is under the action bar
            ClipDrawable clipDrawable =
                    new ClipDrawable(shapeDrawable, Gravity.BOTTOM, ClipDrawable.VERTICAL);
            int viewHeight = view.getHeight();
            clipDrawable.setLevel(10000 * (viewHeight - view.getPaddingTop()) / viewHeight);
            view.setBackground(clipDrawable);
        } else {
            view.setBackground(shapeDrawable);
        }
        view.setClipToOutline(true);
    }
}
