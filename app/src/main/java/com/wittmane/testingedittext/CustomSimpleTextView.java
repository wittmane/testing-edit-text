package com.wittmane.testingedittext;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

//copied from https://gist.github.com/udacityandroid/47592c621d32450d7dbc
public class CustomSimpleTextView extends View {

    // String value
    private String mText;

    // Text color of the text
    private int mTextColor;

    // Context of the app
    private final Context mContext;

    /**
     * Constructs a new TextView with initial values for text and text color.
     */
//    public CustomSimpleTextView(Context context) {
//        super(context);
//        mText = "default text";
//        mTextColor = 0;
//        mContext = context;
//    }
    public CustomSimpleTextView(Context context) {
        this(context, null);
    }
    public CustomSimpleTextView(Context context, AttributeSet attrs) {
//        this(context, attrs, /*com.android.internal.*/R.attr.editTextStyle);
        this(context, attrs, 0);
    }
    public CustomSimpleTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mText = "default text";
        mTextColor = 0;
        mContext = context;
    }

    /**
     * Sets the string value in the TextView.
     *
     * @param text is the updated string to be displayed.
     */
    public void setText(String text) {
        mText = text;
    }

    /**
     * Sets the text color of the TextView.
     *
     * @param color of text to be displayed.
     */
    public void setTextColor(int color) {
        mTextColor = color;
    }

    /**
     * Gets the string value in the TextView.
     *
     * @return current text in the TextView.
     */
    public String getText() {
        return mText;
    }

    /**
     * Gets the text color of the TextView.
     *
     * @return current text color.
     */
    public int getTextColor() {
        return mTextColor;
    }
}
