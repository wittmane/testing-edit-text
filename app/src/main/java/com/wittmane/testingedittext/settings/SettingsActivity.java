/*
 * Copyright (C) 2022-2026 Eli Wittman
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
import android.graphics.Paint;
import android.graphics.Path;
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
import android.transition.TransitionSet;
import android.transition.Visibility;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.RoundedCorner;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowInsets;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.wittmane.testingedittext.animation.ActivityAnimationTransition;
import com.wittmane.testingedittext.animation.PartialTransitionListener;
import com.wittmane.testingedittext.animation.PartialSlide;
import com.wittmane.testingedittext.function.Consumer;
import com.wittmane.testingedittext.function.Supplier;
import com.wittmane.testingedittext.util.BackHandler;
import com.wittmane.testingedittext.util.BackHandler.BackNavigationManager;
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
import com.wittmane.testingedittext.util.TransitionUtils;
import com.wittmane.testingedittext.util.ViewUtils;

import java.util.Timer;
import java.util.TimerTask;

public class SettingsActivity extends PreferenceActivity
        implements FragmentManager.OnBackStackChangedListener {
    private static final String TAG = SettingsActivity.class.getSimpleName();

    private static final boolean LOG_FRAGMENT_CHANGES = false;
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

    private String mCurrentFragmentTag;
    private Timer mFragmentCleanupTimer;
    private final BackHandler mBackHandler = new BackHandler(this,
            new FragmentBackNavigationManager(),
            BackHandler.ANIMATION_STYLE_ASYMMETRIC_WITH_START);

    /**
     * determine if fragments should be hidden (rather than replaced) as new fragments are added.
     * in order to support predictive back animation between fragments (OnBackAnimationCallback
     * added in Android 14), we need to manage hiding and unhiding the previous fragment, rather
     * than replace the fragment and let the framework manage that in a single transaction. we could
     * just do this for Android 14+ (and given that hiding seems atypical that may theoretically be
     * preferred), but due to the fact that hiding a fragment leaves it in the resumed state, this
     * will cause a mismatch of lifecycle events, which seems likely to result in bugs from
     * overlooking this difference in the versions, so we'll just hide it on all versions. I'm
     * leaving this as a method at least for now, rather than just hard-coding the logic, to easily
     * swap functionality back if this ends up causing problems.
     * @return whether fragments should be hidden in instead of replaced
     */
    private static boolean shouldManageHidingFragments() {
        return true;
    }

    private static boolean useDefaultTransitions() {
        // custom transitions are available starting in Lollipop, but due to a bug in Lollipop (see
        // #fragmentOpenExitTransition and #fragmentCloseExitTransition) we can't show a custom
        // transition when only hiding a fragment (not also adding something). this is particularly
        // bad when navigating back and the current fragment can't animate leaving, so we'll still
        // just use the framework transitions on Lollipop if we're manually hiding fragments
        // since we have to manage separate transactions for hiding the current fragment and showing
        // the new fragment.
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
                || (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && shouldManageHidingFragments());
    }

    @Override
    protected void onCreate(final Bundle savedState) {
        setTheme(Settings.getThemeId(this));
        super.onCreate(savedState);

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
            startFragment(f, null, null, false);
        }
        // handle the insets excluding the bottom to support showing the preference list behind the
        // navigation bar
        EdgeToEdgeUtils.addInsetHandling(this, true, true, true, false);

        mBackHandler.setUp();
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

        // originally based on PreferenceActivity#onPreferenceStartFragment and
        // PreferenceActivity#startPreferencePanel
        Fragment f = Fragment.instantiate(this, pref.getFragment(), pref.getExtras());
        startFragment(f, pref, onNavigateForward, allowPendedAction);

        return true;
    }

    private void startFragment(Fragment fragmentToAdd, Preference pref, Runnable onNavigateForward,
                               boolean allowPendedAction) {
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        Fragment currentFragment = getCurrentFragment();
        String nextFragmentTag = createChildFragmentTag();
        setOpenExitTransition(currentFragment);
        boolean addToBackStack;
        if (pref != null) {
            if (pref.getTitleRes() != 0) {
                transaction.setBreadCrumbTitle(pref.getTitleRes());
            } else if (pref.getTitle() != null) {
                transaction.setBreadCrumbTitle(pref.getTitle());
            }
            setOpenEnterTransition(transaction, fragmentToAdd, nextFragmentTag);
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
                hideCurrent = (chainedAction) ->
                        hideFragment(currentFragment, chainedAction, allowPendedAction);
            }
            navigateForward = () ->
                    addFragment(fragmentToAdd, nextFragmentTag, transaction, addToBackStack,
                            onNavigateForward);
        } else {
            navigateForward = () ->
                    replaceFragment(currentFragment, fragmentToAdd, nextFragmentTag, transaction,
                            addToBackStack, onNavigateForward);
        }
        if (hideCurrent != null) {
            hideCurrent.accept(navigateForward);
        } else {
            navigateForward.run();
        }
    }

    private void addFragment(Fragment fragmentToAdd, String nextFragmentTag,
                             FragmentTransaction transaction, boolean addToBackStack,
                             Runnable onNavigateForward) {
        if (LOG_FRAGMENT_CHANGES) {
            Log.d(TAG, "Fragment change: add "
                    + fragmentDisplayInfo(fragmentToAdd, nextFragmentTag)
                    + (addToBackStack ? ", adding to back stack" : "")
                    + getEnterTransitionLogInfo(fragmentToAdd));
        }
        setCurrentFragmentTag(nextFragmentTag);
        transaction.add(android.R.id.content, fragmentToAdd, nextFragmentTag);
        chainRunTransactions(transaction,
                false,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                        ? fragmentToAdd.getEnterTransition()
                        : null,
                onNavigateForward,
                false);
    }

    private void replaceFragment(Fragment currentFragment, Fragment fragmentToAdd,
                                 String nextFragmentTag, FragmentTransaction transaction,
                                 boolean addToBackStack, Runnable onNavigateForward) {
        if (LOG_FRAGMENT_CHANGES) {
            Log.d(TAG, "Fragment change: replace with "
                    + fragmentDisplayInfo(fragmentToAdd, nextFragmentTag)
                    + (addToBackStack ? ", adding to back stack" : "")
                    + getEnterTransitionLogInfo(fragmentToAdd)
                    + "\ncurrent: " + currentFragment
                    + getExitTransitionLogInfo(currentFragment));
        }
        setCurrentFragmentTag(nextFragmentTag);
        transaction.replace(android.R.id.content, fragmentToAdd, nextFragmentTag);
        chainRunTransactions(transaction,
                false,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                        ? fragmentToAdd.getEnterTransition()
                        : null,
                onNavigateForward,
                false);
    }

    private void showFragment(Fragment fragment, Runnable chainedAction) {
        if (!fragment.isHidden()) {
            if (chainedAction != null) {
                chainedAction.run();
            }
            return;
        }
        if (LOG_FRAGMENT_CHANGES) {
            Log.d(TAG, "Fragment change: show " + fragment + getEnterTransitionLogInfo(fragment));
        }
        FragmentTransaction transaction = getFragmentManager().beginTransaction().show(fragment);
        chainRunTransactions(transaction,
                true,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                        ? fragment.getEnterTransition()
                        : null,
                chainedAction,
                false);
    }

    private void hideFragment(Fragment fragment, Runnable chainedAction,
                              boolean allowPendedAction) {
        if (fragment.isHidden()) {
            if (chainedAction != null) {
                chainedAction.run();
            }
            return;
        }
        if (LOG_FRAGMENT_CHANGES) {
            Log.d(TAG, "Fragment change: hide " + fragment + getExitTransitionLogInfo(fragment));
        }
        FragmentTransaction transaction = getFragmentManager().beginTransaction().hide(fragment);
        chainRunTransactions(transaction,
                true,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                        ? fragment.getExitTransition()
                        : null,
                chainedAction,
                allowPendedAction);
    }

    private void chainRunTransactions(FragmentTransaction transaction, boolean commitNow,
                                      Transition transition, Runnable chainedAction,
                                      boolean allowPendedAction) {
        String currentFragmentTag = mCurrentFragmentTag;
        if (transition != null && chainedAction != null) {
            // the framework doesn't seem to handle committing concurrent fragments well. the
            // transition on the second doesn't always run. to resolve this, we'll run the first
            // immediately and wait for it to start the transition, at which point it should be safe
            // to commit the next transaction. the transitions might be out of sync by a few
            // milliseconds, but they'll still mostly be running at the same time, so it probably
            // won't be very noticeable.
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
                chainedAction.run();
            });
        }
        if (commitNow) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                transaction.commitNow();
            } else {
                transaction.commit();
                // theoretically this has the side effect of committing all currently pending
                // transactions, but I don't think there should be any others at this time, so it
                // should be fine
                getFragmentManager().executePendingTransactions();
            }
        } else {
            transaction.commit();
        }
        if (transition == null && chainedAction != null) {
            chainedAction.run();
        }
    }

    private void runOnTransitionStart(Transition transition, Runnable runnable) {
        if (runnable == null) {
            return;
        }
        final TransitionListener listener = new PartialTransitionListener() {
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
            mBackHandler.navigateBack(false, null);
            return true;
        }
        return super.onOptionsItemSelected(item);
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

    public void navigateBack(boolean isImmediatelyAddingNewFragment, Runnable onNavigateBack) {
        mBackHandler.navigateBack(isImmediatelyAddingNewFragment, onNavigateBack);
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        mBackHandler.navigateBack(false, null);
    }

    private class FragmentBackNavigationManager implements BackNavigationManager {
        private Fragment mPreviousFragment;

        @Override
        public void onNavigateBack(boolean isTransientAction) {
            mPreviousFragment = null;

            Fragment currentFragment = getCurrentFragment();
            setCloseExitTransition(currentFragment, isTransientAction);

            if (LOG_FRAGMENT_CHANGES) {
                Log.d(TAG, "Fragment change: pop from back stack " + currentFragment
                        + getReturnTransitionLogInfo(currentFragment)
                        + (!shouldManageHidingFragments()
                                ? "\nprevious: " + getPreviousFragment(currentFragment)
                                        + getReenterTransitionLogInfo(
                                                getPreviousFragment(currentFragment))
                                : ""));
            }
            setCurrentFragmentTag(getPreviousFragmentTag(mCurrentFragmentTag));
            SettingsActivity.super.onBackPressed();
            invalidateOptionsMenu();
        }

        @Override
        public View getAnimatingView() {
            Fragment currentFragment = getCurrentFragment();
            return currentFragment != null ? currentFragment.getView() : null;
        }

        @Override
        public void showPreviousContent(boolean isShowingForPredictiveBack, Runnable onReady) {
            Fragment currentFragment = getCurrentFragment();
            Fragment previousFragment = mPreviousFragment != null
                    ? mPreviousFragment
                    : currentFragment != null
                            ? getPreviousFragment(currentFragment)
                            : null;
            setCloseEnterTransition(previousFragment, isShowingForPredictiveBack);
            Consumer<Runnable> showPrevious = null;
            if (previousFragment != null && previousFragment.isHidden()) {
                mPreviousFragment = previousFragment;
                showPrevious = (chainedAction) -> {
                    // note that for some reason on Nougat only, showing the fragment doesn't
                    // trigger the enter transition. the transition based on the stock emulator is
                    // basically just to appear, so most, if not all, devices probably won't look
                    // significantly different from having it without a transition even if we could
                    // figure out a workaround. also, this is behind the current fragment, so that
                    // makes it even less visible. this seems to just be a framework bug in a single
                    // old version of android, so it's probably not worth putting in more time to
                    // trying to find the core issue and seeing if there is any workaround we can do
                    // (which there very well may not be).
                    showFragment(previousFragment, chainedAction);
                };
            }
            if (showPrevious != null) {
                showPrevious.accept(onReady);
            } else if (onReady != null) {
                onReady.run();
            }
        }

        @Override
        public void hidePreviousContent(Runnable onHidden) {
            if (mPreviousFragment == null) {
                onHidden.run();
                return;
            }
            // since this transition is behind the current fragment, just transition immediately.
            // this transition is only really needed for the callback to know when it's safe to
            // replace the background of the current fragment. setting duration to 1, rather than 0
            // in case anything handles 0 differently. 1 ms is effectively instantly, and depending
            // on how it's actually implemented 0 ms still may have some delay for asynchronous
            // handling and effectively be the same.
            Transition exitTransition = new Fade(Fade.MODE_OUT);
            exitTransition.setDuration(1);
            exitTransition.addListener(new PartialTransitionListener() {
                @Override
                public void onTransitionEnd(Transition transition) {
                    if (onHidden != null) {
                        onHidden.run();
                    }
                }
            });
            mPreviousFragment.setExitTransition(exitTransition);

            hideFragment(mPreviousFragment, null, false);
        }

        @Override
        public long getExitTransitionDuration() {
            Fragment currentFragment = getCurrentFragment();
            Transition currentFragmentTransition = currentFragment != null
                    ? currentFragment.getReturnTransition()
                    : null;
            return currentFragmentTransition != null
                    ? TransitionUtils.getTotalDuration(currentFragmentTransition, true)
                    : 0;
        }
    }

    @Override
    public void onBackStackChanged() {
        // start the fragment cleanup timer in case the back stack somehow changes unrelated to
        // setting the fragment tag as that could indicate or cause messed up state that needs
        // fixing
        startFragmentCleanupTimer();
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
            boolean isCurrentFragment = index == 0;
            boolean isPreviousFragment = index == 1;
            boolean isBackInProgress = mBackHandler.isPredictiveBackInProgress();
            if (!isCurrentFragment && (!isPreviousFragment || !isBackInProgress)) {
                if (fragment != null && fragment.isAdded() && !fragment.isHidden()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        // clear any transition so it disappears immediately
                        fragment.setExitTransition(null);
                    }
                    Log.w(TAG, "Fragment change: hide (cleanup) " + fragment);
                    getFragmentManager().beginTransaction().hide(fragment).commit();
                }
            }
            // clean up any current fragment that somehow isn't shown
            if (isCurrentFragment && fragment != null && fragment.isAdded()
                    && fragment.isHidden()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // clear any transition so it appears immediately
                    fragment.setEnterTransition(null);
                }
                Log.w(TAG, "Fragment change: show (cleanup) " + fragment);
                getFragmentManager().beginTransaction().show(fragment).commit();
            }
            fragmentTag = getPreviousFragmentTag(fragmentTag);
            index++;
        }
    }

    @Override
    protected void onDestroy() {
        EdgeToEdgeUtils.removeInsetHandling(this);
        getFragmentManager().removeOnBackStackChangedListener(this);
        mBackHandler.tearDown();
        super.onDestroy();
    }

    /**
     * Set the transition to run on the new fragment that is entering the screen when it is being
     * opened.
     * @param transaction The transaction that is opening the fragment.
     * @param fragmentToAdd The fragment that is being added.
     * @param nextFragmentTag The tag that will be used for the new fragment.
     */
    private void setOpenEnterTransition(@NonNull FragmentTransaction transaction,
                                        @NonNull Fragment fragmentToAdd, String nextFragmentTag) {
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
                        fragmentDisplayInfo(fragmentToAdd, nextFragmentTag)
                                + " enter (openEnter)");
            }
            // intentionally not hiding the scroll bar during the transition because the transition
            // is likely just sliding up (ie the scroll bar will be on the edge of the screen, so it
            // will look normal) or horizontally sliding in from the screen end (so the scroll bar
            // will only appear as the transition completes)
        }
    }

    /**
     * Set the transition to run on the previous fragment that is exiting the screen when a new
     * fragment is being opened.
     * @param fragment The fragment to attach the transition.
     */
    private void setOpenExitTransition(Fragment fragment) {
        if (fragment == null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || useDefaultTransitions()) {
            return;
        }
        // the framework draws disappearing views' animation on top of other things and doesn't have
        // any z-order control. when transitioning in a new fragment and removing the old one in the
        // same transaction, the exiting fragment (meant to be behind the new one that is entering)
        // is drawn on top of the fragment that is entering, which messes up how our transitions are
        // designed to look. this is a known issue that isn't going to be fixed
        // (https://issuetracker.google.com/issues/142056487). we'll just skip the exit transition
        // in this case. this isn't an issue navigating back because the exiting view is intended to
        // be on top. this also isn't an issue when separately managing hiding the fragments. I'm
        // not entirely sure why, but it's probably related to how the enter transition starts after
        // the exit transition starts.
        Transition openExitTransition = shouldManageHidingFragments()
                ? fragmentOpenExitTransition()
                : null;
        fragment.setExitTransition(openExitTransition);
        if (LOG_TRANSITION_EVENTS) {
            addTransitionLoggingListener(openExitTransition,
                    fragmentDisplayInfo(fragment) + " exit (openExit)");
        }
        // hide the scroll bar during the transition because it looks weird sliding to the side (ie
        // not on the edge of the screen) underneath the new view sliding in
        hideScrollBarDuringTransition(fragment, openExitTransition);
    }

    /**
     * Set the transition to run on the previous fragment that is reentering the screen when the
     * current fragment is being closed.
     * @param fragment The fragment to attach the transition.
     * @param isShowingForPredictiveBack Whether the transition is being triggered as part of
     *                                   showing the fragment for the predictive back animation.
     */
    private void setCloseEnterTransition(Fragment fragment, boolean isShowingForPredictiveBack) {
        if (fragment == null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || useDefaultTransitions()) {
            return;
        }
        // skip animating unhiding the previous fragment if we're showing it for the predictive back
        // animation since we just want a preview of what we're going back to, so animating that
        // could look weird
        Transition closeEnterTransition = isShowingForPredictiveBack
                ? null
                : fragmentCloseEnterTransition();
        if (shouldManageHidingFragments()) {
            fragment.setEnterTransition(closeEnterTransition);
        } else {
            fragment.setReenterTransition(closeEnterTransition);
        }
        if (LOG_TRANSITION_EVENTS) {
            addTransitionLoggingListener(closeEnterTransition,
                    fragmentDisplayInfo(fragment)
                            + (shouldManageHidingFragments()
                                    ? " enter (closeEnter)"
                                    : " reenter (closeEnter)"));
        }
        // hide the scroll bar during the transition because it looks weird sliding to the side (ie
        // not on the edge of the screen) underneath the old view sliding out
        hideScrollBarDuringTransition(fragment, closeEnterTransition);
    }

    /**
     * Create a transition to run on the current fragment that is exiting the screen when it is
     * being closed.
     * @param fragment The fragment to attach the transition.
     * @param isTransientAction Whether the action to close is transient (something else will
     *                          immediately launch).
     */
    private void setCloseExitTransition(Fragment fragment, boolean isTransientAction) {
        if (fragment == null || useDefaultTransitions()) {
            return;
        }
        // if this back is only a transient state (ideally not visible to the user), just skip the
        // transition as the new fragment slides in over it (instead of this fragment sliding out
        // from a normal back action)
        Transition closeExitTransition = isTransientAction ? null : fragmentCloseExitTransition();
        fragment.setReturnTransition(closeExitTransition);
        if (LOG_TRANSITION_EVENTS) {
            addTransitionLoggingListener(closeExitTransition,
                    fragmentDisplayInfo(fragment) + " return (closeExit)");
        }
        // intentionally not hiding the scroll bar during the transition because transition is
        // likely just sliding down (ie the scroll bar will be on the edge of the screen, so it
        // will look normal) or horizontally sliding out on the screen end (so the scroll bar
        // will be out of view right away)
    }

    /**
     * Create a transition to run on the new fragment that is entering the screen when it is being
     * opened.
     * @return An enter transition.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private Transition fragmentOpenEnterTransition() {
        Transition enterTransition;
        if (mBackHandler.isPredictiveBackEnabled()) {
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
            TransitionUtils.setTotalDuration(enterTransition, TRANSITION_DURATION);
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
        if (mBackHandler.isPredictiveBackEnabled()) {
            // based on AOSP anim/activity_open_exit.xml (Android 16). Android 15 and 16 use -96dp,
            // but Android 14 used -10%. that's similar enough, so we'll just go with the most
            // recent version.
            exitTransition = new PartialSlide(Gravity.START, 96, PartialSlide.DP);
            exitTransition.setDuration(450);
            exitTransition.setInterpolator(fastOutExtraSlowInInterpolator());
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && shouldManageHidingFragments()) {
            // in Lollipop, BackStackRecord makes an incorrect assumption that if there is any
            // transition, there must be an incoming fragment (ie it doesn't do a null check), so
            // it crashes, so we'll have to skip the exit transition on Lollipop if we're manually
            // hiding the fragment separate from adding the new fragment
            exitTransition = null;
        } else {
            // have the new fragment transition match the system transition for exiting an activity
            exitTransition = new ActivityAnimationTransition(this, false);
        }
        if (exitTransition != null && TRANSITION_DURATION >= 0) {
            TransitionUtils.setTotalDuration(exitTransition, TRANSITION_DURATION);
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
        if (mBackHandler.isPredictiveBackEnabled()) {
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
            TransitionUtils.setTotalDuration(enterTransition, TRANSITION_DURATION);
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
        if (mBackHandler.isPredictiveBackEnabled()) {
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
            // in Lollipop, BackStackRecord makes an incorrect assumption that if there is any
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
            TransitionUtils.setTotalDuration(returnTransition, TRANSITION_DURATION);
        }
        return returnTransition;
    }

    private static void hideScrollBarDuringTransition(Fragment fragment, Transition transition) {
        if (fragment == null || transition == null) {
            return;
        }
        Supplier<ListView> listViewSupplier = () -> {
            View fragmentView = fragment.getView();
            if (fragmentView == null) {
                return null;
            }
            ListView listView = fragmentView.findViewById(android.R.id.list);
            if (listView == null) {
                return null;
            }
            if (!listView.isVerticalScrollBarEnabled()) {
                return null;
            }
            return listView;
        };
        // try to get the list view before starting the transition because in the case that the
        // transition removes a view, it just takes an image of the view and animates it leaving, so
        // removing the scroll bar after the transition starts won't actually prevent the scroll bar
        // from being visible in the transition
        ListView listView = listViewSupplier.get();
        if (listView != null) {
            listView.setVerticalScrollBarEnabled(false);
            listView.invalidate();
        }
        transition.addListener(new PartialTransitionListener() {
            ListView mListView;

            @Override
            public void onTransitionStart(Transition transition) {
                // try to get the list and remove the scroll bar now that the transition started
                // since the view may have not existed before the transition started if the view is
                // being added to the screen
                mListView = listViewSupplier.get();
                if (mListView == null) {
                    mListView = listView;
                    return;
                }
                mListView.setVerticalScrollBarEnabled(false);
            }

            @Override
            public void onTransitionEnd(Transition transition) {
                if (mListView == null) {
                    return;
                }
                mListView.setVerticalScrollBarEnabled(true);
                if (mListView.isScrollbarFadingEnabled()
                        && ViewUtils.aggregateIsVisible(mListView)) {
                    // trigger the handling for visibility change to allow it to awaken the scroll
                    // bars like it normally would when making the view appear (which we essentially
                    // just delayed while disabling the scroll bar during the transition)
                    mListView.onVisibilityAggregated(true);
                }
            }
        });
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

    private String fragmentDisplayInfo(Fragment fragment) {
        return fragmentDisplayInfo(fragment, null);
    }

    private String fragmentDisplayInfo(Fragment fragment, String fragmentTag) {
        if (fragment == null)  {
            if (fragmentTag != null) {
                fragment = getFragmentManager().findFragmentByTag(fragmentTag);
            }
        }
        if (fragment != null && (fragmentTag == null || fragmentTag.equals(fragment.getTag()))) {
            // this includes the tag, so the tag doesn't need to be added beyond that
            return fragment.toString();
        }
        if (fragment == null) {
            return fragmentTag;
        }
        return fragment + " (" + fragmentTag + ")";
    }

    private static String getEnterTransitionLogInfo(Fragment fragment) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || fragment == null) {
            return "";
        }
        return ", transition=" + fragment.getEnterTransition();
    }

    private static String getExitTransitionLogInfo(Fragment fragment) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || fragment == null) {
            return "";
        }
        return ", transition=" + fragment.getExitTransition();
    }

    private static String getReenterTransitionLogInfo(Fragment fragment) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || fragment == null) {
            return "";
        }
        return ", transition=" + fragment.getReenterTransition();
    }

    private static String getReturnTransitionLogInfo(Fragment fragment) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || fragment == null) {
            return "";
        }
        return ", transition=" + fragment.getReturnTransition();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static Interpolator fastOutExtraSlowInInterpolator() {
        // based on fast_out_extra_slow_in.xml
        Path path = new Path();
        path.cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f);
        path.cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f);
        return new PathInterpolator(path);
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
