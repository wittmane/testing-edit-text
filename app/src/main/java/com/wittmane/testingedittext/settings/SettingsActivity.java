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
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.view.MenuItem;
import android.view.RoundedCorner;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewParent;
import android.view.WindowInsets;
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

    public static final String FIELD_ID_BUNDLE_KEY = "FIELD_ID";
    private static final String FRAGMENT_TAG_PREFIX = "NavigationStackFragment";

    private boolean mIsBackCallbackRegistered = false;
    private final OnBackInvokedCallback mOnBackInvokedCallback =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    ? new OnBackCallbackAndroid14()
                    : Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            ? new OnBackCallbackAndroid13()
                            : null;
    private final FragmentManager.OnBackStackChangedListener mOnBackStackChangedListener =
            this::updateBackCallbackRegistrationState;
    // in order to support predictive back animation between fragments (OnBackAnimationCallback
    // added in Android 14), we need to manage hiding and unhiding the previous fragment, rather
    // than replace the fragment and let the framework manage that in a single transaction
    private static final boolean MANAGE_HIDING_FRAGMENTS =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

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
    public boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref) {
        // (EW) based on PreferenceActivity#onPreferenceStartFragment and
        // PreferenceActivity#startPreferencePanel

        Fragment f = Fragment.instantiate(this, pref.getFragment(), pref.getExtras());
        addFragment(f, pref);
        return true;
    }

    private void addFragment(Fragment f, Preference pref) {
        Fragment[] navStack = getFragmentNavStack();
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        if (MANAGE_HIDING_FRAGMENTS) {
            if (navStack.length > 0) {
                Fragment currentFragment = navStack[navStack.length - 1];
                if (currentFragment != null) {
                    // this needs to be part of a separate transaction for some reason or else we
                    // can't show it behind the soon-to-be current fragment later for predictive
                    // back
                    getFragmentManager().beginTransaction().hide(currentFragment).commit();
                }
            }
            transaction.add(android.R.id.content, f, FRAGMENT_TAG_PREFIX + navStack.length);
        } else {
            transaction.replace(android.R.id.content, f, FRAGMENT_TAG_PREFIX + navStack.length);
        }
        if (pref != null) {
            if (pref.getTitleRes() != 0) {
                transaction.setBreadCrumbTitle(pref.getTitleRes());
            } else if (pref.getTitle() != null) {
                transaction.setBreadCrumbTitle(pref.getTitle());
            }
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            transaction.addToBackStack(null);
        }
        transaction.commit();
    }

    private Fragment[] getFragmentNavStack() {
        FragmentManager fragmentManager = getFragmentManager();
        int backStackEntryCount = fragmentManager.getBackStackEntryCount();
        Fragment[] navStack;
        if (backStackEntryCount == 0) {
            Fragment fragment = fragmentManager.findFragmentByTag(FRAGMENT_TAG_PREFIX + 0);
            navStack = fragment == null ? new Fragment[0] : new Fragment[] { fragment };
        } else {
            navStack = new Fragment[backStackEntryCount + 1];
            for (int i = 0; i < navStack.length; i++) {
                navStack[i] = fragmentManager.findFragmentByTag(FRAGMENT_TAG_PREFIX + i);
            }
        }
        return navStack;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (MANAGE_HIDING_FRAGMENTS && item.getItemId() == android.R.id.home) {
            // due to the fact that fragments are being added instead of replaced and the previous
            // fragment is getting hidden in a separate transaction, we need to handle unhiding the
            // previous fragment because the standard back handling is only going to process
            // undoing adding the current fragment since that is all that was included as part of
            // the back stack
            Fragment[] navStack = getFragmentNavStack();
            if (navStack.length > 1) {
                Fragment previousFragment = navStack[navStack.length - 2];
                if (previousFragment != null) {
                    getFragmentManager().beginTransaction().show(previousFragment).commit();
                }
            }
        }
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
    private class OnBackCallbackAndroid13 implements OnBackInvokedCallback {

        @Override
        public void onBackInvoked() {
            onBackPressed();
            updateBackCallbackRegistrationState();
        }
    }

    // (EW) manually animate the back gesture to match the system animations. based on
    // https://github.com/android/animation-samples/blob/main/Motion/app/src/main/java/com/example/android/motion/demo/containertransform/CheeseArticleFragment.kt
    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private class OnBackCallbackAndroid14 extends OnBackCallbackAndroid13
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
            Fragment[] navStack = getFragmentNavStack();
            if (navStack.length < 1) {
                return;
            }
            Fragment currentFragment = navStack[navStack.length - 1];
            if (currentFragment == null) {
                return;
            }
            mFragmentContent = currentFragment.getView();
            if (mFragmentContent == null) {
                return;
            }

            // if the background under the fragment (either its direct background, some ancestor, or
            // the base activity default) is a simple color (ignoring transparent backgrounds),
            // create a new background directly under the fragment to prevent overlapping with the
            // previous fragment when that is unhidden, and create it with rounded corners matching
            // the device's corners to match behavior from activity predictive back animations
            mOriginalBackground = mFragmentContent.getBackground();
            mOriginalClipToOutline = mFragmentContent.getClipToOutline();
            Drawable background = getNearestBackground(mFragmentContent);
            int originalBackgroundColor;
            if (background == null) {
                final TypedArray a = getTheme().obtainStyledAttributes(new int[]{
                        android.R.attr.colorBackground
                });
                originalBackgroundColor = a.getColor(0, 0);
                a.recycle();
            } else if (background instanceof ColorDrawable) {
                originalBackgroundColor = ((ColorDrawable) background).getColor();
            } else {
                originalBackgroundColor = Color.TRANSPARENT;
            }
            if (originalBackgroundColor != Color.TRANSPARENT) {
                setRoundedBackground(mFragmentContent, originalBackgroundColor);
            }
            if (originalBackgroundColor == Color.TRANSPARENT && mOriginalBackground == null) {
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
            if (navStack.length > 1) {
                Fragment previousFragment = navStack[navStack.length - 2];
                if (previousFragment != null) {
                    mPreviousFragment = previousFragment;
                    getFragmentManager().beginTransaction()
                            .show(mPreviousFragment)
                            .commit();
                }
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
            if (mFragmentContent == null) {
                return;
            }
            mFragmentContent.setTranslationX(0f);
            mFragmentContent.setTranslationY(0f);
            mFragmentContent.setScaleX(1f);
            mFragmentContent.setScaleY(1f);

            if (mPreviousFragment != null) {
                getFragmentManager().beginTransaction()
                        .hide(mPreviousFragment)
                        .commit();
            }
            if (mDarkOverlay != null) {
                ((ViewGroup) mDarkOverlay.getParent()).removeView(mDarkOverlay);
            }
            if (mFragmentContent != null) {
                if (mFragmentContent.getBackground() != mOriginalBackground) {
                    // delay the background from being replaced (likely with nothing) to avoid a
                    // flash of the previous fragment overlapping since there is a delay in the
                    // fragment transaction to hide the previous fragment again
                    View fragmentContent = mFragmentContent;
                    Drawable originalBackground = mOriginalBackground;
                    boolean originalClipToOutline = mOriginalClipToOutline;
                    mFragmentContent.post(() -> {
                        fragmentContent.setBackground(originalBackground);
                        fragmentContent.setClipToOutline(originalClipToOutline);
                    });
                }
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
                ((ViewGroup) mDarkOverlay.getParent()).removeView(mDarkOverlay);
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
    private static void setRoundedBackground(View view, int color) {
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
        view.setBackground(shapeDrawable);
        view.setClipToOutline(true);
    }
}
