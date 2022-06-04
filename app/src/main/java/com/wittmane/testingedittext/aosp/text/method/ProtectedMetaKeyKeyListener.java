package com.wittmane.testingedittext.aosp.text.method;

//import android.text.method.MetaKeyKeyListener;

import android.text.Spannable;
import android.text.method.MetaKeyKeyListener;

/**
 * (EW) wrapper for MetaKeyKeyListener to access protected or hidden methods and constants
 */
public class ProtectedMetaKeyKeyListener extends android.text.method.MetaKeyKeyListener {

    //TODO: (EW) MetaKeyKeyListener.META_SELECTING = KeyEvent.META_SELECTING = 0x800 has been
    // defined at least since Kitkat, but it has been hidden with a comment saying it's pending API
    // review and at least as of S, KeyEvent.META_SELECTING has been marked UnsupportedAppUsage
    // (maxTargetSdk R). after this long it seems unlikely for this to be released for apps to use,
    // and this could theoretically get changed in a future version, so it isn't completely safe to
    // just hard-code 0x800. I decided to skip it in EditText.ChangeWatcher#afterTextChanged and
    // Editor#extractTextInternal, so it might be good to not use this anywhere else.
    /**
     * @hide pending API review
     */
    public static final int META_SELECTING = /*KeyEvent.META_SELECTING*/0x800;

    protected static void resetLockedMeta(Spannable content) {
        MetaKeyKeyListener.resetLockedMeta(content);
    }
}
