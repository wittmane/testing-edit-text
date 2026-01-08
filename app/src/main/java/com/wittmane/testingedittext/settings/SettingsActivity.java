/*
 * Copyright (C) 2022-2026 Eli Wittman
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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.text.TextUtils;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.Transition.TransitionListener;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.Visibility;
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
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.animation.ActivityAnimationTransition;
import com.wittmane.testingedittext.animation.PartialSlide;
import com.wittmane.testingedittext.function.Consumer;
import com.wittmane.testingedittext.util.DrawableUtils;
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
import com.wittmane.testingedittext.util.ResourceUtils;

import java.util.Timer;
import java.util.TimerTask;

public class SettingsActivity extends PreferenceActivity
        implements FragmentManager.OnBackStackChangedListener {
    private static final String TAG = SettingsActivity.class.getSimpleName();

    private static final boolean LOG_FRAGMENT_CHANGES = true;//TODO: (EW) disable
    private static final boolean LOG_TRANSITION_EVENTS = false;
    // this value was determined by measuring the default duration of the transitions (both fragment
    // transitions with default values and the activity back transition) measuring wasn't super
    // precise, so a nice round number that was close was picked.
    private static final int DEFAULT_TRANSITION_DURATION = 300;
    // this could be used to have a consistent transition duration to keep all of the simultaneous
    // transitions in sync. since all of the transitions have a specific duration set or came from a
    // resource to match activity transitions, this isn't really needed anymore and now just serves
    // a good way to easily slow down all transitions for debugging purposes.
    private static final int TRANSITION_DURATION = -1;
    private static final int FRAGMENT_CLEANUP_TIMER_DELAY = 500;
    private static final int RUN_ON_TRANSITION_START_FALLBACK_DELAY = 250;

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

    private boolean useDefaultTransitions() {
        // custom transitions are available starting in Lollipop, but due to a bug in Lollipop (see
        // #fragmentReplacedTransitionOut and #fragmentRemovedTransitionOut) we can't show a custom
        // transition when only hiding a fragment (not also adding something). this is particularly
        // bad when navigating back and the current fragment can't animate leaving, so we'll still
        // just use the framework transitions on Lollipop if we're manually hiding fragments
        // since we have to manage separate transactions for hiding the current fragment and showing
        // the new fragment.
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
                || (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && shouldManageHidingFragments());
    }

    private Timer mFragmentCleanupTimer;

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
            addFragment(f, null, null, false);
        }
        // handle the insets excluding the bottom to support showing the preference list behind the
        // navigation bar
        EdgeToEdgeUtils.addInsetHandling(this, true, true, true, false);

        updateBackCallbackRegistrationState();
        getFragmentManager().addOnBackStackChangedListener(this);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(STATE_CURRENT_FRAGMENT_TAG, mCurrentFragmentTag);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        setCurrentFragmentTag(savedInstanceState.getString(STATE_CURRENT_FRAGMENT_TAG));
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref) {
        return onPreferenceStartFragment(caller, pref, null, false);
    }

    public boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref,
                                             Runnable onNavigateForward,
                                             boolean allowPendedAction) {
        if (!allowPendedAction && caller != getCurrentFragment()) {
            // this is probably from a user clicking on a preference to navigate into a child screen
            // after already clicking to navigate away from the current screen, so we shouldn't
            // process this or else the preference screen navigation stack will be messed up
            // (navigating back will result in returning to the same screen, a sibling screen, or
            // the grandparent screen).
            Log.w(TAG, "Skipping " + pref + " click from " + caller
                    + " since it isn't the current fragment anymore");
            return false;
        }

        // (EW) based on PreferenceActivity#onPreferenceStartFragment and
        // PreferenceActivity#startPreferencePanel

        Fragment f = Fragment.instantiate(this, pref.getFragment(), pref.getExtras());
        addFragment(f, pref, onNavigateForward, allowPendedAction);
        return true;
    }

    /**
     * Create a transition to run on the new fragment that is entering the screen when it is being
     * opened.
     * @return An enter transition.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private Transition fragmentOpenEnterTransition() {
        Transition enterTransition;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && mOnBackInvokedCallback instanceof OnBackCallbackWithAnimation) {
            // have the new fragment slide in to pair with the predictive back animation (slide
            // out). based on AOSP anim/activity_open_enter.xml (Android 16).
            TransitionSet transitionSet = new TransitionSet();

            Fade alpha = new Fade(Visibility.MODE_IN);
            alpha.setInterpolator(new LinearInterpolator());
            alpha.setStartDelay(50);
            alpha.setDuration(83);
            transitionSet.addTransition(alpha);

            // Android 15 and 16 use 96dp, but Android 14 used 10%. that's similar enough, so we'll
            // just go with the most recent version
            PartialSlide translate = new PartialSlide(Gravity.END, 96, PartialSlide.DP);
            translate.setDuration(450);
            translate.setInterpolator(fastOutExtraSlowInInterpolator());
            transitionSet.addTransition(translate);

            enterTransition = transitionSet;
        } else {
            // have the new fragment transition match the system transition for navigating to a new
            // activity
            enterTransition = new ActivityAnimationTransition(this, true);
        }
        if (enterTransition != null && TRANSITION_DURATION >= 0) {
            setTotalDuration(enterTransition, TRANSITION_DURATION);
        }
        return enterTransition;
    }

    /**
     * Create a transition to run on the previous fragment that is exiting the screen when a new
     * fragment is being opened.
     * @return An exit transition.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private Transition fragmentOpenExitTransition() {
        Transition exitTransition;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && mOnBackInvokedCallback instanceof OnBackCallbackWithAnimation) {
            // based on AOSP anim/activity_open_exit.xml (Android 16). Android 15 and 16 use -96dp,
            // but Android 14 used -10%. that's similar enough, so we'll just go with the most
            // recent version.
            exitTransition = new PartialSlide(Gravity.START, 96, PartialSlide.DP);
            exitTransition.setDuration(450);
            exitTransition.setInterpolator(fastOutExtraSlowInInterpolator());
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && shouldManageHidingFragments()) {
            // in Lollipop BackStackRecord makes an incorrect assumption that if there is any
            // transition, there must be an incoming fragment (ie it doesn't do a null check), so
            // it crashes, so we'll have to skip the exit transition on Lollipop if we're manually
            // hiding the fragment separate from adding the new fragment
            exitTransition = null;
        } else {
            // have the new fragment transition match the system transition for exiting an activity
            exitTransition = new ActivityAnimationTransition(this, false);
        }
        if (exitTransition != null && TRANSITION_DURATION >= 0) {
            setTotalDuration(exitTransition, TRANSITION_DURATION);
        }
        return exitTransition;
    }

    /**
     * Create a transition to run on the previous fragment that is reentering the screen when the
     * current fragment is being closed.
     * @return An enter transition.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private Transition fragmentCloseEnterTransition() {
        Transition enterTransition;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && mOnBackInvokedCallback instanceof OnBackCallbackWithAnimation) {
            // based on AOSP anim/activity_close_enter.xml (Android 16). Android 15 and 16 use
            // -96dp but Android 14 used -10%. that's similar enough, so we'll just go with the most
            // recent version.
            enterTransition = new PartialSlide(Gravity.START, 96, PartialSlide.DP);
            enterTransition.setDuration(450);
            enterTransition.setInterpolator(fastOutExtraSlowInInterpolator());
        } else {
            // have the new fragment transition match the system transition for returning to the
            // previous activity
            enterTransition = new ActivityAnimationTransition(this, false);
        }
        if (enterTransition != null && TRANSITION_DURATION >= 0) {
            setTotalDuration(enterTransition, TRANSITION_DURATION);
        }
        return enterTransition;
    }

    /**
     * Create a transition to run on the current fragment that is exiting the screen when it is
     * being closed.
     * @return An exit transition.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private Transition fragmentCloseExitTransition() {
        Transition returnTransition;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && mOnBackInvokedCallback instanceof OnBackCallbackWithAnimation) {
            // based on AOSP anim/activity_close_exit.xml (Android 16)
            TransitionSet transitionSet = new TransitionSet();

            Fade alpha = new Fade(Visibility.MODE_OUT);
            alpha.setInterpolator(new LinearInterpolator());
            alpha.setStartDelay(35);
            alpha.setDuration(83);
            transitionSet.addTransition(alpha);

            // Android 15 and 16 use 96dp but Android 14 used 10%. that's similar enough, so we'll
            // just go with the most recent version
            PartialSlide translate = new PartialSlide(Gravity.END, 96, PartialSlide.DP);
            translate.setDuration(450);
            translate.setInterpolator(fastOutExtraSlowInInterpolator());
            transitionSet.addTransition(translate);

            returnTransition = transitionSet;
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && shouldManageHidingFragments()) {
            // in Lollipop BackStackRecord makes an incorrect assumption that if there is any
            // transition, there must be an incoming fragment (ie doesn't do a null check), so
            // it crashes, so we'll have to skip the return transition on Lollipop if we're
            // manually hiding the fragment separate from adding the new fragment
            returnTransition = null;
        } else {
            // have the new fragment transition match the system transition for navigating away from
            // the current activity
            returnTransition = new ActivityAnimationTransition(this, true);
        }
        if (returnTransition != null && TRANSITION_DURATION >= 0) {
            setTotalDuration(returnTransition, TRANSITION_DURATION);
        }
        return returnTransition;
    }

    private static Interpolator fastOutExtraSlowInInterpolator() {
        Path path = new Path();
        path.cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f);
        path.cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f);
        return new PathInterpolator(path);
    }

    private static void setTotalDuration(Transition transition, long totalDuration) {
        long originalTotalDuration = getTotalDuration(transition, true);
        if (originalTotalDuration < 0) {
            // we can't tell the exact duration, so assume no start delay and set the duration, even
            // if that inappropriately evenly distributes to all children
            Log.w(TAG, "Can't determine total duration for " + transition
                    + ", so it can't be scaled properly");
            transition.setStartDelay(0);
            transition.setDuration(totalDuration);
            return;
        }

        long startDelay = transition.getStartDelay();
        long duration = transition.getDuration();
        if (duration >= 0) {
            if (startDelay < 0) {
                // assume there should be no start delay and explicitly set that
                Log.w(TAG, "Explicitly setting no start delay for " + transition);
                transition.setStartDelay(0);
            }
            if (originalTotalDuration > 0) {
                // scale the start delay and duration
                transition.setDuration(totalDuration * duration / originalTotalDuration);
                transition.setStartDelay(totalDuration - transition.getDuration());
            } else {
                // start delay and duration are both 0, so just set the duration and leave no start
                // delay
                transition.setDuration(totalDuration);
            }
        } else if (transition instanceof TransitionSet) {
            long newDuration;
            if (startDelay > 0) {
                // scale the start delay and duration
                newDuration = totalDuration * (originalTotalDuration - startDelay)
                        / originalTotalDuration;
            } else {
                // start delay and duration are both 0, so just set the duration and leave no start
                // delay
                newDuration = totalDuration;
            }
            transition.setStartDelay(totalDuration - newDuration);
            TransitionSet transitionSet = (TransitionSet) transition;
            long originalMaxChildTotalDuration =
                    originalTotalDuration - (startDelay >= 0 ? startDelay : 0);
            for (int i = 0; i < transitionSet.getTransitionCount(); i++) {
                Transition childTransition = transitionSet.getTransitionAt(i);
                long childTotalDuration = getTotalDuration(childTransition, true);
                setTotalDuration(childTransition,
                        newDuration * childTotalDuration
                                / originalMaxChildTotalDuration);
            }
        }
    }

    private static long getTotalDuration(Transition transition, boolean assumeZeroStartOffsets) {
        long startDelay = transition.getStartDelay();
        long duration = transition.getDuration();
        if (duration >= 0) {
            if (startDelay >= 0) {
                return startDelay + duration;
            }
            if (assumeZeroStartOffsets) {
                return duration;
            }
        }
        if (transition instanceof TransitionSet) {
            TransitionSet transitionSet = (TransitionSet) transition;
            long maxTotalDuration = 0;
            for (int i = 0; i < transitionSet.getTransitionCount(); i++) {
                Transition childTransition = transitionSet.getTransitionAt(i);
                long totalDuration = getTotalDuration(childTransition, assumeZeroStartOffsets);
                if (totalDuration < 0) {
                    return -1;
                }
                if (totalDuration > maxTotalDuration) {
                    maxTotalDuration = totalDuration;
                }
            }
            return (startDelay >= 0 ? startDelay : 0) + maxTotalDuration;
        }
        // duration comes from animator, so we can't cleanly get that
        return -1;
    }

    private void addTransitionLoggingListener(Transition transition, String transitionIdentifier) {
        if (transition == null) {
            return;
        }
        transition.addListener(new TransitionListener() {
            @Override
            public void onTransitionCancel(Transition transition) {
                Log.d(TAG, "onTransitionCancel: " + transitionIdentifier);
            }

            @Override
            public void onTransitionEnd(Transition transition) {
                Log.d(TAG, "onTransitionEnd: " + transitionIdentifier);
            }

            @Override
            public void onTransitionPause(Transition transition) {
                Log.d(TAG, "onTransitionPause: " + transitionIdentifier);
            }

            @Override
            public void onTransitionResume(Transition transition) {
                Log.d(TAG, "onTransitionResume: " + transitionIdentifier);
            }

            @Override
            public void onTransitionStart(Transition transition) {
                Log.d(TAG, "onTransitionStart: " + transitionIdentifier);
            }
        });
    }

    private void addFragment(Fragment fragmentToAdd, Preference pref, Runnable onNavigateForward,
                             boolean allowPendedAction) {
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        Fragment currentFragment = getCurrentFragment();
        String nextFragmentTag = createChildFragmentTag();
        if (currentFragment != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !useDefaultTransitions()) {
                Transition openExitTransition = fragmentOpenExitTransition();
                currentFragment.setExitTransition(openExitTransition);
                if (LOG_TRANSITION_EVENTS) {
                    addTransitionLoggingListener(openExitTransition,
                            mCurrentFragmentTag + " exit (openExit)");
                }
            }
        }
        boolean addToBackStack;
        if (pref != null) {
            if (pref.getTitleRes() != 0) {
                transaction.setBreadCrumbTitle(pref.getTitleRes());
            } else if (pref.getTitle() != null) {
                transaction.setBreadCrumbTitle(pref.getTitle());
            }
            // when we're managing custom transitions, we don't want a default transition, but
            // TRANSIT_NONE causes some weird flashing, so we'll use the fade option, which is very
            // subtle
            transaction.setTransition(useDefaultTransitions()
                    ? FragmentTransaction.TRANSIT_FRAGMENT_OPEN
                    : FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !useDefaultTransitions()) {
                Transition openEnterTransition = fragmentOpenEnterTransition();
                fragmentToAdd.setEnterTransition(openEnterTransition);
                if (LOG_TRANSITION_EVENTS) {
                    addTransitionLoggingListener(openEnterTransition,
                            nextFragmentTag + " enter (openEnter)");
                }
            }
            transaction.addToBackStack(null);
            addToBackStack = true;
        } else {
            addToBackStack = false;
        }
        Consumer<Runnable> hideCurrent = null;
        Runnable navigateForward;
        if (shouldManageHidingFragments()) {
            if (currentFragment != null && !currentFragment.isHidden()) {
                // this needs to be part of a separate transaction for some reason or else we can't
                // show it behind the soon-to-be current fragment later for predictive back
                hideCurrent = (onTransactionStarted) -> {
                    if (LOG_FRAGMENT_CHANGES) {
                        Log.d(TAG, "Fragment change: hide " + currentFragment
                                + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                                ? (", transition=" + currentFragment.getExitTransition())
                                : ""));
                    }
                    FragmentTransaction hideCurrentTransaction =
                            getFragmentManager().beginTransaction().hide(currentFragment);
                    chainRunTransactions(hideCurrentTransaction,
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                                    ? currentFragment.getExitTransition()
                                    : null, onTransactionStarted,
                            allowPendedAction);
                };
            }
            navigateForward = () -> {
                if (LOG_FRAGMENT_CHANGES) {
                    Log.d(TAG, "Fragment change: add " + fragmentToAdd
                            + (addToBackStack ? ", adding to back stack" : "")
                            + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                            ? (", transition=" + fragmentToAdd.getEnterTransition())
                            : ""));
                }
                setCurrentFragmentTag(nextFragmentTag);
                transaction.add(android.R.id.content, fragmentToAdd, nextFragmentTag);
                transaction.commit();
                if (onNavigateForward != null) {
                    onNavigateForward.run();
                }
            };
        } else {
            navigateForward = () -> {
                if (LOG_FRAGMENT_CHANGES) {
                    Log.d(TAG, "Fragment change: replace with " + fragmentToAdd
                            + (addToBackStack ? ", adding to back stack" : "")
                            + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                            ? (", transition=" + fragmentToAdd.getEnterTransition())
                            : ""));
                }
                setCurrentFragmentTag(nextFragmentTag);
                transaction.replace(android.R.id.content, fragmentToAdd, nextFragmentTag);
                transaction.commit();
                if (onNavigateForward != null) {
                    onNavigateForward.run();
                }
            };
        }
        if (hideCurrent != null) {
            hideCurrent.accept(navigateForward);
        } else {
            navigateForward.run();
        }
    }

    private void chainRunTransactions(FragmentTransaction transaction, Transition transition,
                                      Runnable chainedAction) {
        chainRunTransactions(transaction, transition, chainedAction, false);
    }

    private void chainRunTransactions(FragmentTransaction transaction, Transition transition,
                                      Runnable chainedAction, boolean allowPendedAction) {
        String currentFragmentTag = mCurrentFragmentTag;
        if (transition != null) {
            // the framework doesn't seem to handle committing concurrent fragments well. the
            // transition on the second doesn't always run. to resolve this, we'll run the first
            // immediately and wait for it to start the transition, at which point it should be safe
            // to commit the next transaction. the transitions might be out of sync by a few
            // milliseconds, but they shouldn't be intrinsically tied to each other, so that should
            // be fine. they'll still mostly be running at the same time, so it probably won't be
            // very noticeable.
            // for some reason the transition listener needs to be added before running committing
            // the transaction. it's not that the listener would get called before we have time.
            // simply adding a dummy listener here and adding the real one after committing the
            // transaction works fine. the framework must be doing something weird with the list of
            // listeners.
            runOnTransitionStart(transition, () -> {
                if (SettingsActivity.this.isDestroyed()) {
                    return;
                }
                if (!allowPendedAction
                        && !TextUtils.equals(mCurrentFragmentTag, currentFragmentTag)) {
                    // we're not at the same position that we were when trying to add the new
                    // fragment (the user probably navigated back), so abandon navigating to the
                    // specified fragment
                    return;
                }
                if (chainedAction != null) {
                    chainedAction.run();
                }
            });
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            transaction.commitNow();
        } else {
            transaction.commit();
            // theoretically this has the side effect of committing all currently pending
            // transactions, but I don't think there should be any others at this time, so it should
            // be fine
            getFragmentManager().executePendingTransactions();
        }
        if (transition == null && chainedAction != null) {
            chainedAction.run();
        }
    }

    private void runOnTransitionStart(Transition transition, Runnable runnable) {
        if (runnable == null) {
            return;
        }
        final TransitionListener listener = new TransitionListener() {
            private boolean mCalledRunnable;

            @Override
            public void onTransitionStart(Transition transition) {
                // remove the listener to make sure we don't try to commit this multiple times
                transition.removeListener(this);
                synchronized (this) {
                    if (mCalledRunnable) {
                        return;
                    }
                    mCalledRunnable = true;
                }
                runnable.run();
            }

            @Override
            public void onTransitionPause(Transition transition) {
            }

            @Override
            public void onTransitionResume(Transition transition) {
            }

            @Override
            public void onTransitionCancel(Transition transition) {
            }

            @Override
            public void onTransitionEnd(Transition transition) {
            }
        };
        transition.addListener(listener);
        // add a brief delay to force calling the transition start handler in case it somehow never
        // gets called
        new Handler().postDelayed(() -> listener.onTransitionStart(transition),
                RUN_ON_TRANSITION_START_FALLBACK_DELAY);
    }

    private String createChildFragmentTag() {
        String prefix;
        if (TextUtils.isEmpty(mCurrentFragmentTag)) {
            prefix = FRAGMENT_TAG_PREFIX;
        } else {
            prefix = mCurrentFragmentTag + FRAGMENT_TAG_DIVIDER;
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
        Fragment currentFragment = getFragmentManager().findFragmentByTag(mCurrentFragmentTag);
        if (currentFragment == null) {
            Log.w(TAG, "Failed to find current fragment (tag: " + mCurrentFragmentTag + ")");
        }
        return currentFragment;
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
        if (currentFragmentTag == null) {
            Log.e(TAG, "fragment is missing tag: " + currentFragment);
            return null;
        }
        String previousFragmentTag = getPreviousFragmentTag(currentFragmentTag);
        if (previousFragmentTag == null) {
            // there isn't a previous fragment
            return null;
        }
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
            navigateBack(false);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void navigateBack(boolean isImmediatelyAddingNewFragment) {
        navigateBack(isImmediatelyAddingNewFragment, null);
    }

    public void navigateBack(boolean isImmediatelyAddingNewFragment, Runnable onNavigateBack) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && mOnBackInvokedCallback instanceof OnBackCallbackWithAnimation) {
            OnBackCallbackWithAnimation onBackCallback =
                    (OnBackCallbackWithAnimation) mOnBackInvokedCallback;
            // artificially trigger handling for the start of the back animation to set up the the
            // transition to match the swipe/long press (except for the scaling since there won't be
            // any progress). also, this will handle unhiding the previous fragment because the
            // standard back handling is only going to process undoing adding the current fragment
            // since that is all that was included as part of the back stack. then immediately
            // trigger the back invoked handling (remove the current fragment).
            onBackCallback.onBackStarted(isImmediatelyAddingNewFragment,
                    () -> {
                onBackCallback.onBackInvoked(isImmediatelyAddingNewFragment);
                if (onNavigateBack != null) {
                    onNavigateBack.run();
                }
            });
        } else {
            onBackPressed(onNavigateBack);
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
        onBackPressed(null);
    }

    private void onBackPressed(Runnable onNavigateBack) {
        Consumer<Runnable> showPrevious = null;
        if (shouldManageHidingFragments()
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                        || !(mOnBackInvokedCallback instanceof OnBackCallbackWithAnimation))) {
            Fragment currentFragment = getCurrentFragment();
            if (currentFragment != null && !useDefaultTransitions()) {
                Transition closeExitTransition = fragmentCloseExitTransition();
                currentFragment.setReturnTransition(closeExitTransition);
                if (LOG_TRANSITION_EVENTS) {
                    addTransitionLoggingListener(closeExitTransition,
                            currentFragment.getTag() + " return (closeExit)");
                }
            }
            // unhide the previous fragment (not necessary for the animated callback since that is
            // already done as part of the animation)
            Fragment previousFragment = currentFragment != null
                    ? getPreviousFragment(currentFragment)
                    : null;
            if (previousFragment != null && previousFragment.isHidden()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                        && !useDefaultTransitions()) {
                    Transition closeEnterTransition = fragmentCloseEnterTransition();
                    previousFragment.setEnterTransition(closeEnterTransition);
                    if (LOG_TRANSITION_EVENTS) {
                        addTransitionLoggingListener(closeEnterTransition,
                                previousFragment.getTag() + " enter (closeEnter)");
                    }
                }

                showPrevious = (onTransactionStarted) -> {
                    if (LOG_FRAGMENT_CHANGES) {
                        Log.d(TAG, "Fragment change: show " + previousFragment
                                + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                                ? (", transition=" + previousFragment.getEnterTransition())
                                : ""));
                    }
                    // note that for some reason on Nougat only, showing the fragment doesn't
                    // trigger the enter transition. the transition based on the stock emulator is
                    // basically just to appear, so most, if not all, devices probably won't look
                    // significantly different from having it without a transition even if we could
                    // figure out a workaround. also, this is behind the current fragment, so that
                    // makes it even less visible. this seems to just be a framework bug in a single
                    // old version of android, so it's probably not worth putting in more time to
                    // trying to find the core issue and seeing if there is any workaround we can do
                    // (which there very well may not be).
                    FragmentTransaction transaction = getFragmentManager().beginTransaction()
                            .show(previousFragment);
                    chainRunTransactions(transaction,
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                                    ? previousFragment.getEnterTransition()
                                    : null, onTransactionStarted);
                };
            }
        }
        Runnable navigateBack = () -> {
            if (LOG_FRAGMENT_CHANGES) {
                Fragment currentFragment = getCurrentFragment();
                Log.d(TAG, "Fragment change: pop from back stack " + currentFragment
                        + ((currentFragment != null
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                        ? (", transition=" + currentFragment.getReturnTransition())
                        : ""));
            }
            setCurrentFragmentTag(getPreviousFragmentTag(mCurrentFragmentTag));
            SettingsActivity.super.onBackPressed();
            invalidateOptionsMenu();
            if (onNavigateBack != null) {
                onNavigateBack.run();
            }
        };
        if (showPrevious != null) {
            showPrevious.accept(navigateBack);
        } else {
            navigateBack.run();
        }
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

        @Override
        public void onBackStarted(@NonNull BackEvent backEvent) {
            onBackStarted(false, null);
        }

        public void onBackStarted(boolean skipShowingPrevious, Runnable afterBackStarted) {
            Fragment currentFragment = getCurrentFragment();
            if (currentFragment == null) {
                return;
            }

            Transition closeExitTransition = fragmentCloseExitTransition();
            currentFragment.setReturnTransition(closeExitTransition);
            if (LOG_TRANSITION_EVENTS) {
                addTransitionLoggingListener(closeExitTransition,
                        currentFragment.getTag() + " return (closeExit)");
            }

            mFragmentContent = currentFragment.getView();
            if (mFragmentContent == null) {
                return;
            }

            // end any active transitions to make this predictive back animation show correctly
            // immediately and avoid potentially leaving a fragment hidden when going back to it due
            // to mixed up transition state tracking
            ViewParent parent = mFragmentContent.getParent();
            if (parent instanceof ViewGroup) {
                TransitionManager.endTransitions((ViewGroup) parent);
            }

            // if the background under the fragment (either its direct background, some ancestor, or
            // the base activity default) is a simple color (ignoring transparent backgrounds),
            // create a new background directly under the fragment to prevent overlapping with the
            // previous fragment when that is unhidden, and create it with rounded corners matching
            // the device's corners to match behavior from activity predictive back animations
            mOriginalBackground = mFragmentContent.getBackground();
            mOriginalClipToOutline = mFragmentContent.getClipToOutline();
            Drawable background = DrawableUtils.getNearestBackground(mFragmentContent);
            int originalNearestBackgroundColor;
            if (background == null) {
                originalNearestBackgroundColor = ResourceUtils.getColor(
                        android.R.attr.colorBackground, SettingsActivity.this);
            } else if (background instanceof ColorDrawable) {
                originalNearestBackgroundColor = ((ColorDrawable) background).getColor();
            } else {
                originalNearestBackgroundColor = Color.TRANSPARENT;
            }
            Drawable drawable;
            if (originalNearestBackgroundColor != Color.TRANSPARENT) {
                drawable = createRoundedDrawable(mFragmentContent, originalNearestBackgroundColor);
            } else {
                // ideally we would round the corners on this too, but I'm not sure that there is a
                // good way to do that on any random drawable
                drawable = DrawableUtils.copyDrawable(background);
                if (drawable == null && mOriginalBackground == null) {
                    // we can't recreate the background and there isn't an existing background to
                    // reuse, so we won't be able to prevent the previous fragment from overlapping
                    // with the current fragment, so we shouldn't try to unhide the previous
                    // fragment. all we'll show is the animation of the content of the current
                    // fragment shifting.
                    return;
                }
            }
            if (drawable != null) {
                // clip to exclude the portion of the view that is under the action bar
                ClipDrawable clipDrawable =
                        new ClipDrawable(drawable, Gravity.BOTTOM, ClipDrawable.VERTICAL);
                int viewHeight = mFragmentContent.getHeight();
                clipDrawable.setLevel(
                        10000 * (viewHeight - mFragmentContent.getPaddingTop()) / viewHeight);
                mFragmentContent.setBackground(clipDrawable);

                mFragmentContent.setClipToOutline(true);
            }

            // add a semi-transparent overlay between the previous fragment and the current fragment
            // to give a better distinction between the two and match behavior from activity
            // predictive back animations
            LinearLayout darkOverlay = new LinearLayout(SettingsActivity.this);
            darkOverlay.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            darkOverlay.setBackgroundColor(Color.argb(0.5f, 0f, 0f, 0f));
            if (addSiblingBefore(darkOverlay, mFragmentContent)) {
                mDarkOverlay = darkOverlay;
            }

            // unhide the previous fragment
            Consumer<Runnable> showPrevious = null;
            Fragment previousFragment = getPreviousFragment(currentFragment);
            if (previousFragment != null && previousFragment.isHidden() && !skipShowingPrevious) {
                mPreviousFragment = previousFragment;
                showPrevious = (onTransactionStarted) -> {
                    // skip animating unhiding the previous fragment if we're only starting the back
                    // animation to immediately trigger completing the back action
                    Transition closeEnterTransition = afterBackStarted == null
                            ? null
                            : fragmentCloseEnterTransition();
                    mPreviousFragment.setEnterTransition(closeEnterTransition);
                    if (LOG_TRANSITION_EVENTS) {
                        addTransitionLoggingListener(closeEnterTransition,
                                mPreviousFragment.getTag() + " enter (closeEnter)");
                    }

                    if (LOG_FRAGMENT_CHANGES) {
                        Log.d(TAG, "Fragment change: show " + mPreviousFragment
                                + ", transition=" + mPreviousFragment.getEnterTransition());
                    }
                    FragmentTransaction transaction = getFragmentManager().beginTransaction()
                            .show(mPreviousFragment);
                    chainRunTransactions(transaction,
                            previousFragment.getEnterTransition(), onTransactionStarted);
                };
            }
            if (showPrevious != null) {
                showPrevious.accept(afterBackStarted);
            } else if (afterBackStarted != null) {
                afterBackStarted.run();
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

                if (!mPreviousFragment.isHidden()) {
                    if (LOG_FRAGMENT_CHANGES) {
                        Log.d(TAG, "Fragment change: hide " + mPreviousFragment
                                + ", transition=" + mPreviousFragment.getExitTransition());
                    }
                    getFragmentManager().beginTransaction()
                            .hide(mPreviousFragment)
                            .commit();
                }

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
            onBackInvoked(false);
        }

        public void onBackInvoked(boolean isImmediatelyAddingNewFragment) {
            if (mPreviousFragment != null && mPreviousFragment.isHidden()
                    && !isImmediatelyAddingNewFragment) {
                if (LOG_FRAGMENT_CHANGES) {
                    Log.d(TAG, "Fragment change: show " + mPreviousFragment
                            + ", transition=" + mPreviousFragment.getEnterTransition());
                }
                getFragmentManager().beginTransaction()
                        .show(mPreviousFragment)
                        .commit();
            }
            if (mDarkOverlay != null) {
                if (isImmediatelyAddingNewFragment) {
                    // since this back is only a transient state (ideally not visible to the user),
                    // we're not showing the previous fragment, so just hide the dark overlay
                    // immediately (fade in effect not useful here)
                    ((ViewGroup) mDarkOverlay.getParent()).removeView(mDarkOverlay);
                } else {
                    // have the dark overlay fade out before removing it (basically fading in the
                    // previous fragment as it becomes the current again while the current slides
                    // out to be removed). since the current fades as it slides out, the overlay
                    // should disappear a bit before the current fragment's transition completes.
                    Animation animation = new AlphaAnimation(1f, 0f);
                    Fragment currentFragment = getCurrentFragment();
                    Transition currentFragmentTransition = currentFragment != null
                            ? currentFragment.getReturnTransition()
                            : null;
                    long duration = currentFragmentTransition != null
                            ? getTotalDuration(currentFragmentTransition, true)
                            : 0;
                    if (duration >= 0) {
                        animation.setDuration(duration / 2);
                    } else {
                        animation.setDuration(DEFAULT_TRANSITION_DURATION / 2);
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
            }
            mPreviousFragment = null;
            mFragmentContent = null;
            mOriginalBackground = null;
            mDarkOverlay = null;
            if (isImmediatelyAddingNewFragment) {
                Fragment currentFragment = getCurrentFragment();
                if (currentFragment != null) {
                    // since this back is only a transient state (ideally not visible to the user),
                    // just skip the transition as the new fragment slides in over it (instead of
                    // this fragment sliding out from a normal back action)
                    currentFragment.setReturnTransition(null);
                }
            }
            super.onBackInvoked();
        }

        public boolean isInProgress() {
            return mFragmentContent != null;
        }
    }

    @Override
    public void onBackStackChanged() {
        // start the fragment cleanup timer in case the back stack somehow changes unrelated to
        // setting the fragment tag as that could indicate or cause messed up state that needs
        // fixing
        startFragmentCleanupTimer();

        updateBackCallbackRegistrationState();
    }

    private synchronized void setCurrentFragmentTag(String tag) {
        if (TextUtils.equals(mCurrentFragmentTag, tag)) {
            return;
        }
        mCurrentFragmentTag = tag;

        startFragmentCleanupTimer();
    }

    private synchronized void startFragmentCleanupTimer() {
        if (!shouldManageHidingFragments()) {
            // since we're not managing showing/hiding fragments manually, we don't need safety
            // checks cleaning up any potentially incorrect state
            return;
        }
        if (mFragmentCleanupTimer != null) {
            // reset the timer since the current fragment changed, so we need to wait until changes
            // from this settle before we need to check if the state is correct
            mFragmentCleanupTimer.cancel();
            mFragmentCleanupTimer = null;
        }
        // schedule a timer to trigger after all of the UI changes from the fragment change should
        // have settled where we'll check if all of the fragment state is correct and fix anything
        // that may be messed up
        Timer timer = new Timer();
        mFragmentCleanupTimer = timer;
        mFragmentCleanupTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (SettingsActivity.this) {
                    if (timer != mFragmentCleanupTimer) {
                        // this is an old timer
                        return;
                    }
                    if (SettingsActivity.this.isDestroyed()) {
                        return;
                    }
                    cleanUpFragmentState();
                }
            }
        }, FRAGMENT_CLEANUP_TIMER_DELAY);
    }

    private void cleanUpFragmentState() {
        String fragmentTag = mCurrentFragmentTag;
        int index = 0;
        while (!TextUtils.isEmpty(fragmentTag)) {
            Fragment fragment = getFragmentManager().findFragmentByTag(fragmentTag);
            // clean up any old fragments that somehow aren't hidden (don't hide the previous
            // fragment if the predictive back animation is active)
            if (index > 1 || (index == 1
                    && (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                            || !(mOnBackInvokedCallback instanceof OnBackCallbackWithAnimation)
                            || !((OnBackCallbackWithAnimation) mOnBackInvokedCallback).isInProgress()))) {
                if (fragment != null && fragment.isAdded() && !fragment.isHidden()) {
                    if (LOG_FRAGMENT_CHANGES) {
                        Log.d(TAG, "Fragment change: hide (cleanup) " + fragment
                                + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                                        ? (", transition=" + fragment.getExitTransition())
                                        : ""));
                    }
                    getFragmentManager().beginTransaction().hide(fragment).commit();
                }
            }
            // clean up any current fragment that somehow isn't shown
            if (index == 0 && fragment != null && fragment.isAdded() && fragment.isHidden()) {
                if (LOG_FRAGMENT_CHANGES) {
                    Log.d(TAG, "Fragment change: show (cleanup) " + fragment
                            + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                            ? (", transition=" + fragment.getEnterTransition())
                            : ""));
                }
                getFragmentManager().beginTransaction().show(fragment).commit();
            }
            fragmentTag = getPreviousFragmentTag(fragmentTag);
            index++;
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
        getFragmentManager().removeOnBackStackChangedListener(this);
        super.onDestroy();
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
        WindowInsets insets = view.getRootWindowInsets();
        RoundedCorner topLeft = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT);
        RoundedCorner topRight = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT);
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
        return shapeDrawable;
    }
}
