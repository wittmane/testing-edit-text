package com.wittmane.testingedittext.aosp.text.method;

import android.text.Spannable;
import android.text.method.MetaKeyKeyListener;

/**
 * (EW) wrapper for MetaKeyKeyListener to access protected or hidden methods and constants
 */
public class ProtectedMetaKeyKeyListener extends android.text.method.MetaKeyKeyListener {

    protected static void resetLockedMeta(Spannable content) {
        MetaKeyKeyListener.resetLockedMeta(content);
    }
}
