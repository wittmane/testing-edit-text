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

import android.app.ActionBar;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
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

public class SettingsActivity extends PreferenceActivity {
    private static final String TAG = SettingsActivity.class.getSimpleName();

    public static final String FIELD_ID_BUNDLE_KEY = "FIELD_ID";

    private boolean mIsBackCallbackRegistered = false;
    private final OnBackInvokedCallback mOnBackInvokedCallback =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    ? new OnBackCallback14()
                    : Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            ? new OnBackCallback13()
                            : null;
    private final FragmentManager.OnBackStackChangedListener mOnBackStackChangedListener =
            this::updateBackCallbackRegistrationState;

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
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new MainSettingsFragment()).commit();
        }
        // handle the insets excluding the bottom to support showing the preference list behind the
        // navigation bar
        EdgeToEdgeUtils.addInsetHandling(this, true, true, true, false);

        updateBackCallbackRegistrationState();
        getFragmentManager().addOnBackStackChangedListener(mOnBackStackChangedListener);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            int fieldId = extras.getInt(FIELD_ID_BUNDLE_KEY, -1);
            FieldPosition position = fieldId >= 0 ? Settings.getTestFieldPosition(fieldId) : null;
            if (position != null) {
                Bundle targetExtras = new Bundle();
                targetExtras.putString(GROUP_INDEX_BUNDLE_KEY, "" + position.groupIndex);
                targetExtras.putString(FIELD_INDEX_BUNDLE_KEY, "" + position.fieldIndex);
                Fragment f = new TestFieldSettingsFragment();
                f.setArguments(targetExtras);
                FragmentTransaction transaction = getFragmentManager().beginTransaction();
                transaction.replace(android.R.id.content, f);
                transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                transaction.commitAllowingStateLoss();
            }
        }
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref) {
        // (EW) based on PreferenceActivity#onPreferenceStartFragment and
        // PreferenceActivity#startPreferencePanel

        Fragment f = Fragment.instantiate(this, pref.getFragment(), pref.getExtras());
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(android.R.id.content, f);
        if (pref.getTitleRes() != 0) {
            transaction.setBreadCrumbTitle(pref.getTitleRes());
        } else if (pref.getTitle() != null) {
            transaction.setBreadCrumbTitle(pref.getTitle());
        }
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        transaction.addToBackStack(null);
        transaction.commitAllowingStateLoss();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        // starting in Oreo, the default implementation handles the top back button correctly, but
        // prior to that, we need to have custom handling
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (item.getItemId() == android.R.id.home) {
                onBackPressed();
                return true;
            }
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

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private class OnBackCallback13 implements OnBackInvokedCallback {

        @Override
        public void onBackInvoked() {
            onBackPressed();
            updateBackCallbackRegistrationState();
        }
    }

    // (EW) manually animate the back gesture to match the system animations. based on
    // https://github.com/android/animation-samples/blob/main/Motion/app/src/main/java/com/example/android/motion/demo/containertransform/CheeseArticleFragment.kt
    //TODO: (EW) this doesn't show the previous fragment, so it's not really "predictive". the
    // animation is better than nothing, but making it actually predictive would be better.
    // https://developer.android.com/guide/navigation/custom-back/support-animations calls out that
    // this is a known limitation when using callbacks. see if there is some other method to get
    // this to work or something else clever to do to get the previous fragment to show.
    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private class OnBackCallback14 extends OnBackCallback13 implements OnBackAnimationCallback {
        private final PathInterpolator mGestureInterpolator = new PathInterpolator(0f, 0f, 0f, 0f);

        private float initialTouchY = -1f;
        private ViewGroup mFragmentContent;

        @Override
        public void onBackStarted(@NonNull BackEvent backEvent) {
            mFragmentContent = findViewById(android.R.id.list_container);
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
            int maxTranslationX = (mFragmentContent.getWidth() / 20) - predictiveBackMargin;
            mFragmentContent.setTranslationX(progress * maxTranslationX *
                    ((backEvent.getSwipeEdge() == BackEvent.EDGE_LEFT) ? 1 : -1));

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
            mFragmentContent.setTranslationX(0f);
            mFragmentContent.setTranslationY(0f);
            mFragmentContent.setScaleX(1f);
            mFragmentContent.setScaleY(1f);
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
        //TODO: (EW) figure out how to get predictive back animations between the fragments on the
        // back stack to work
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
}
