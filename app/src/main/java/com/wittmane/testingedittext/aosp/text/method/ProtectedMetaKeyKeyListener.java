package com.wittmane.testingedittext.aosp.text.method;

//import android.text.method.MetaKeyKeyListener;

import android.text.Spannable;
import android.text.method.MetaKeyKeyListener;

/**
 * (EW) wrapper for MetaKeyKeyListener to access protected or hidden methods and constants
 */
public class ProtectedMetaKeyKeyListener extends android.text.method.MetaKeyKeyListener {

    //TODO: (EW) is checking META_SELECTING actually necessary or can we get rid of this
    // copy of an inaccessible constant?
    /**
     * @hide pending API review
     */
    public static final int META_SELECTING = /*KeyEvent.META_SELECTING*/0x800;

    protected static void resetLockedMeta(Spannable content) {
        MetaKeyKeyListener.resetLockedMeta(content);
    }
}
