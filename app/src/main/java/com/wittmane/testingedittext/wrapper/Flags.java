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

package com.wittmane.testingedittext.wrapper;

import android.os.Build;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

/**
 * Wrapper class for various feature flag classes.
 * <p>
 * I can't even find the source of any of these (except for ClientFlags, which seems to be just some
 * alternative to calling the main flag), so I don't have any idea how they're supposed to work.
 * None of them seem to exist prior to Android 15. I initially left them all (except the ClientFlags
 * ones) disabled for because I wasn't sure when they should be enabled, thinking they could just be
 * testing for a potential new feature, but I found that some were enabled in multiple devices. we
 * could just leave these all disabled to just leave functionality the same as previous versions,
 * but on Android 15 through at least 16 they can be accessed via reflection (still logs a warning
 * "(unsupported, reflection, allowed)"), so it seems reasonable enough to at least try matching the
 * framework functionality as much as we can.
 * </p>
 */
public class Flags {
    private static final String TAG = Flags.class.getSimpleName();

    private static final HashMap<String, Boolean> sFlagCache = new HashMap<>();

    private static boolean checkFlag(String className, String methodName, boolean defaultValue) {
        String cacheKey = className + "#" + methodName;
        if (!sFlagCache.containsKey(cacheKey)) {
            Boolean flagValue = null;
            try {
                Class<?> classObject = Class.forName(className);
                Method methodObject = classObject.getMethod(methodName);
                Object result = methodObject.invoke(null);
                if (result instanceof Boolean) {
                    flagValue = (Boolean) result;
                } else {
                    Log.e(TAG, "Unexpected result from " + className + "#" + methodName + ": "
                            + result);
                }
            } catch (NoSuchMethodException | IllegalAccessException
                     | InvocationTargetException | ClassNotFoundException e) {
                Log.e(TAG, "Reflection failed on " + className + "#" + methodName + ": "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            sFlagCache.put(cacheKey, flagValue);
        }
        Boolean flagValue = sFlagCache.get(cacheKey);
        return flagValue == null ? defaultValue : flagValue;
    }

    // this started to be checked in EditText in Android 16 around new functionality. from some
    // limited testing, this seems to normally be true, so we'll have that as the default.
    public static boolean a11yCharacterInWindowApi() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            // from testing, this method doesn't seem to exist prior to Android 16, so don't bother
            // trying on older versions, and since this functionality wasn't ever available in older
            // versions, we'll keep this disabled
            return false;
        }
        return checkFlag("android.view.accessibility.Flags", "a11yCharacterInWindowApi", false);
    }

    // this started to be checked in EditText in Android 15 around new functionality. this was true
    // everywhere I tested (where the class existed), so we'll default to enabling this in case
    // reflection stops working in some version.
    public static boolean editorinfoHandwritingEnabled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // from testing, this Flags class doesn't seem to exist prior to Android 15, and the
            // functionality this flag controls relies on some handwriting APIs that only exist
            // starting in Android 15, so it doesn't make sense to even consider enabling this for
            // prior versions
            return false;
        }
        return checkFlag("android.view.inputmethod.Flags", "editorinfoHandwritingEnabled", true);
    }

    // this started to be checked in EditText in Android 16 around new functionality, but this flag
    // seems to have existed since Android 15. this was true everywhere I tested (where the class
    // existed), so we'll default to enabling this in case reflection stops working in some version.
    public static boolean initiationWithoutInputConnection() {
        // note that we're actually checking this flag for the versions that the flag existed, not
        // only the versions that EditText, which could cause a change in behavior compared to the
        // framework EditText, but at least currently the functionality that is driven by this flag
        // can't even be implemented, so at least for now this doesn't make any difference. if we
        // ever find a way to implement it, we can reevaluate if this needs to change.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // from testing, this Flags class doesn't seem to exist prior to Android 15, so don't
            // bother trying on older versions, and since this functionality wasn't ever available
            // in older versions, we'll keep this disabled
            return false;
        }
        return checkFlag("android.view.inputmethod.Flags", "initiationWithoutInputConnection",
                true);
    }

    // this started to be checked in EditText in Android 16 around new functionality. from some
    // limited testing, this seems to normally be true, so we'll have that as the default.
    public static boolean writingTools() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            // from testing, this method doesn't seem to exist prior to Android 16, so don't bother
            // trying on older versions, and since this functionality wasn't ever available in older
            // versions, we'll keep this disabled
            return false;
        }
        return checkFlag("android.view.inputmethod.Flags", "writingTools", false);
    }

    // this started to be checked in Editor in Android 15 around alternate functionality. from some
    // limited testing, I this seemed to be false on Android 15 devices but true on Android 16
    // devices, so we'll just leave the default as a version check to match that.
    public static boolean highContrastTextSmallTextRect() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // from testing, this Flags class doesn't seem to exist prior to Android 15, so don't
            // bother trying on older versions, and since this functionality wasn't ever available
            // in older versions, we'll keep this disabled
            return false;
        }
        return checkFlag("com.android.graphics.hwui.flags.Flags", "highContrastTextSmallTextRect",
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA);
    }

    // this started to be checked in Editor in Android 16 around new functionality. from some
    // limited testing, I found this to be true everywhere, so we'll enable it as the default.
    public static boolean contextMenuHideUnavailableItems() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            // from testing, this method doesn't seem to exist prior to Android 16, so don't bother
            // trying on older versions, and since this functionality wasn't ever available in older
            // versions, we'll keep this disabled
            return false;
        }
        return checkFlag("com.android.text.flags.Flags", "contextMenuHideUnavailableItems", true);
    }

    // this started to be checked in EditText in Android 15 around new functionality. this was true
    // everywhere I tested (where the class existed), and since this functionality seems very
    // reasonable, we'll default to enabling this in case reflection stops working in some version.
    public static boolean escapeClearsFocus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // from testing, this Flags class doesn't seem to exist prior to Android 15. this
            // functionality seems very reasonable (I'm not sure why it's behind a feature flag), so
            // it doesn't seem unreasonable to keep the default for older versions too since this
            // functionality should be able to work on any version, but since we're checking this
            // flag to match functionality with the framework EditText, it makes more sense to just
            // disable the functionality on previous versions to match there too. if the feature
            // ever changes to always be enabled and not hidden behind this feature flag, I'd
            // consider just enabling it for all versions.
            return false;
        }
        return checkFlag("com.android.text.flags.Flags", "escapeClearsFocus", true);
    }

    // android.text.ClientFlags#fixLineHeightForLocale started to be checked in BoringLayout in
    // Android 15 around new functionality, which was changed to
    // com.android.text.flags.Flags#fixLineHeightForLocale in Android 16. ClientFlags was removed
    // entirely in Android 16, but it did reference the other class's methods in the javadocs in
    // Android 15, so I think they were meant to match. the ClientFlags version called into
    // TextFlags#isFeatureEnabled and then AppGlobals#getIntCoreSetting, but I'm not certain how
    // these flags/settings are supposed to work, and I can't even find the source for the
    // replacement. based on the documentation for locale-aware default line height for EditText
    // indicating the new option and default when targeting Android 15 (API level 35), my initial
    // theory was that it was managing that, but I didn't find it (or any other flag I added in this
    // wrapper class) to have different values based on what target version I set for this app, so
    // just expecting this to be true on Android 15+ because this app is targeting that (technically
    // more recent at this point) doesn't seem to be appropriate. the ClientFlags version and what
    // it called into were all blocked from reflection, but the com.android.text.flags.Flags version
    // is accessible from reflection (at least on Android 15 and 16), so we'll use that instead on
    // all versions as long as we can and fall back to just being true if reflection fails since
    // everywhere I tested returned true and based on the documentation that is what I expect always
    // should happen starting in Android 15.
    public static boolean fixLineHeightForLocale() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // from testing, this Flags class doesn't seem to exist prior to Android 15, so don't
            // bother trying on older versions, and since this functionality wasn't ever available
            // in older versions, we'll keep this disabled
            return false;
        }
        return checkFlag("com.android.text.flags.Flags", "fixLineHeightForLocale", true);
    }

    // this started to be checked in EditText in Android 16 around alternate functionality, but this
    // flag seems to have existed since Android 15. from some testing, I didn't find a consistent
    // pattern of this being enabled, so we'll just leave the default as disabled.
    public static boolean fixNullTypefaceBolding() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // from testing, this Flags class doesn't seem to exist prior to Android 15, so don't
            // bother trying on older versions, and since this functionality wasn't ever available
            // in older versions, we'll keep this disabled
            return false;
        }
        return checkFlag("com.android.text.flags.Flags", "fixNullTypefaceBolding", false);
    }

    // this started to be checked in EditText in Android 15 around new functionality. this was true
    // everywhere I tested (where the class existed), so we'll default to enabling this in case
    // reflection stops working in some version.
    public static boolean handwritingEndOfLineTap() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // from testing, this Flags class doesn't seem to exist prior to Android 15, and the
            // functionality this flag controls relies on some handwriting APIs that only exist
            // starting in Android 14, so it doesn't make sense to consider enabling this for prior
            // versions, but we could at least consider it for Android 14. still, we're checking
            // this flag to match the framework EditText functionality, so we should disable it on
            // versions that never had the functionality to match there too, but if the feature ever
            // changes to always be enabled and not hidden behind this feature flag, I'd consider
            // just enabling it back to Android 14 (or generally and expect the caller to manage API
            // versions appropriately).
            return false;
        }
        return checkFlag("com.android.text.flags.Flags", "handwritingEndOfLineTap", true);
    }

    // this started to be checked in EditText in Android 16 around alternate functionality. from
    // some limited testing, I didn't find this enabled anywhere, so we'll just leave the default as
    // disabled.
    public static boolean handwritingGestureWithTransformation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            // from testing, this method doesn't seem to exist prior to Android 16, so don't bother
            // trying on older versions, and since this functionality wasn't ever available in older
            // versions, we'll keep this disabled
            return false;
        }
        return checkFlag("com.android.text.flags.Flags", "handwritingGestureWithTransformation",
                false);
    }

    // android.text.ClientFlags#icuBidiMigration started to be checked in MeasuredParagraph in
    // Android 15, around what seems to just be an alternate implementation of functionality, but
    // the check was removed in Android 16 to only use the new implementation. that and what it
    // calls into (TextFlags#isFeatureEnabled and then AppGlobals#getIntCoreSetting) are all blocked
    // from reflection, but the javadoc references com.android.text.flags.Flags as if they are meant
    // to match, and that is accessible with reflection, so we can just use that for the one version
    // this check was used.
    public static boolean icuBidiMigration() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // from testing, this Flags class doesn't seem to exist prior to Android 15, so don't
            // bother trying on older versions, and since this functionality wasn't ever available
            // in older versions, we'll keep this disabled
            return false;
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // the default probably doesn't really matter when we're only using reflection on one
            // version. I don't expect it to only work on certain devices or only under certain
            // circumstances, but in case it doesn't work for some reason, we'll just default to
            // true since that's the value I saw when testing, and based on my initial theory in
            // #fixLineHeightForLocale (also using ClientFlags) I thought that would always be the
            // case, and although that theory doesn't seem to be correct, that's the what I was
            // already having this do, so maintaining that as the default seems alright.
            return checkFlag("com.android.text.flags.Flags", "icuBidiMigration", true);
        }
        // (EW) the icuBidiMigration checks in MeasuredParagraph was removed in Android 16 (making
        // the functionality always used), and this method was removed from the Flags class, so this
        // will just be true in Android 16+.
        return true;
    }

    // this started to be checked in InsertModeTransformationMethod in Android 16 around new
    // functionality. this was false everywhere I tested, so we'll just have that as the default.
    public static boolean insertModeHighlightRange() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            // from testing, this method doesn't seem to exist prior to Android 16, so don't bother
            // trying on older versions, and since this functionality wasn't ever available in older
            // versions, we'll keep this disabled
            return false;
        }
        return checkFlag("com.android.text.flags.Flags", "insertModeHighlightRange", false);
    }

    // this started to be checked in EditText in Android 15 around alternate functionality. this was
    // false everywhere I tested, so we'll just have that as the default.
    public static boolean insertModeNotUpdateSelection() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // from testing, this Flags class doesn't seem to exist prior to Android 15, so don't
            // bother trying on older versions, and since this functionality wasn't ever available
            // in older versions, we'll keep this disabled
            return false;
        }
        return checkFlag("com.android.text.flags.Flags", "insertModeNotUpdateSelection", false);
    }

    // this started to be checked in EditText in Android 16 around alternate functionality. from
    // some limited testing, I didn't find this enabled anywhere, so we'll just leave the default as
    // disabled.
    public static boolean typefaceRedesignReadonly() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            // from testing, this method doesn't seem to exist prior to Android 16, so don't bother
            // trying on older versions, and since this functionality wasn't ever available in older
            // versions, we'll keep this disabled
            return false;
        }
        return checkFlag("com.android.text.flags.Flags", "typefaceRedesignReadonly", false);
    }
}
