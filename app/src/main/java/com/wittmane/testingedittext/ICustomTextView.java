package com.wittmane.testingedittext;

import android.text.Editable;

//TODO: added to accommodate multiple versions of custom text views - delete once narrowed to 1
public interface ICustomTextView {
    Editable getEditableText();
    void beginBatchEdit();
    void endBatchEdit();
}
