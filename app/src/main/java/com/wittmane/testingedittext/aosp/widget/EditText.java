package com.wittmane.testingedittext.aosp.widget;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.LocaleList;
import android.os.SystemClock;
import android.text.BoringLayout;
import android.text.DynamicLayout;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Layout;
import android.text.ParcelableSpan;
import android.text.Selection;
import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristic;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DateKeyListener;
import android.text.method.DateTimeKeyListener;
import android.text.method.DialerKeyListener;
import android.text.method.DigitsKeyListener;
import android.text.method.KeyListener;
import android.text.method.MetaKeyKeyListener;
import android.text.method.PasswordTransformationMethod;
import android.text.method.SingleLineTransformationMethod;
import android.text.method.TextKeyListener;
import android.text.method.TimeKeyListener;
import android.text.method.TransformationMethod;
import android.text.style.CharacterStyle;
import android.text.style.ParagraphStyle;
import android.text.style.SuggestionSpan;
import android.text.style.UpdateAppearance;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.textclassifier.TextClassificationManager;
import android.view.textclassifier.TextClassifier;
import android.widget.Scroller;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.RequiresApi;
import androidx.annotation.Size;
import androidx.annotation.StringRes;

import com.wittmane.testingedittext.CustomInputConnection2;
import com.wittmane.testingedittext.HiddenTextUtils;
import com.wittmane.testingedittext.ICustomTextView;
import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.aosp.text.method.ArrowKeyMovementMethod;
import com.wittmane.testingedittext.aosp.text.method.MovementMethod;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Locale;

public class EditText extends View implements ICustomTextView, ViewTreeObserver.OnPreDrawListener {
    private static final String TAG = EditText.class.getSimpleName();

    static final String LOG_TAG = "TextView";
    static final boolean DEBUG_EXTRACT = false;
    private static final float[] TEMP_POSITION = new float[2];

    // Enum for the "typeface" XML parameter.
    // TODO: How can we get this from the XML instead of hardcoding it here?
    /** @hide */
    @IntDef(value = {DEFAULT_TYPEFACE, SANS, SERIF, MONOSPACE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface XMLTypefaceAttr{}
    private static final int DEFAULT_TYPEFACE = -1;
    private static final int SANS = 1;
    private static final int SERIF = 2;
    private static final int MONOSPACE = 3;

    // Enum for the "ellipsize" XML parameter.
    private static final int ELLIPSIZE_NOT_SET = -1;
    private static final int ELLIPSIZE_NONE = 0;
    private static final int ELLIPSIZE_START = 1;
    private static final int ELLIPSIZE_MIDDLE = 2;
    private static final int ELLIPSIZE_END = 3;

    // Bitfield for the "numeric" XML parameter.
    // TODO: How can we get this from the XML instead of hardcoding it here?
    private static final int SIGNED = 2;
    private static final int DECIMAL = 4;

    private static final int LINES = 1;
    private static final int EMS = LINES;
    private static final int PIXELS = 2;

    private static final RectF TEMP_RECTF = new RectF();

    /** @hide */
    static final int VERY_WIDE = 1024 * 1024; // XXX should be much larger
    private static final int ANIMATED_SCROLL_GAP = 250;

    private static final InputFilter[] NO_FILTERS = new InputFilter[0];
    private static final Spanned EMPTY_SPANNED = new SpannedString("");

    private static final int CHANGE_WATCHER_PRIORITY = 100;

    // New state used to change background based on whether this TextView is multiline.
//    private static final int[] MULTILINE_STATE_SET = { R.attr.state_multiline };

    // Accessibility action to share selected text.
    private static final int ACCESSIBILITY_ACTION_SHARE = 0x10000000;

    /**
     * @hide
     */
    // Accessibility action start id for "process text" actions.
    static final int ACCESSIBILITY_ACTION_PROCESS_TEXT_START_ID = 0x10000100;

    /**
     * @hide
     */
    static final int PROCESS_TEXT_REQUEST_CODE = 100;

    /**
     *  Return code of {@link #doKeyDown}.
     */
    private static final int KEY_EVENT_NOT_HANDLED = 0;
    private static final int KEY_EVENT_HANDLED = -1;
    private static final int KEY_DOWN_HANDLED_BY_KEY_LISTENER = 1;
    private static final int KEY_DOWN_HANDLED_BY_MOVEMENT_METHOD = 2;

    private static final int FLOATING_TOOLBAR_SELECT_ALL_REFRESH_DELAY = 500;

    // System wide time for last cut, copy or text changed action.
    static long sLastCutCopyOrTextChangedTime;

    private ColorStateList mTextColor;
    private ColorStateList mHintTextColor;
    private ColorStateList mLinkTextColor;
    @ViewDebug.ExportedProperty(category = "text")
    private int mCurTextColor;
    private int mCurHintTextColor;
    private boolean mFreezesText;

    private Editable.Factory mEditableFactory = Editable.Factory.getInstance();
    private Spannable.Factory mSpannableFactory = Spannable.Factory.getInstance();

    private float mShadowRadius, mShadowDx, mShadowDy;
    private int mShadowColor;

    private boolean mPreDrawRegistered;
    private boolean mPreDrawListenerDetached;

    private TextClassifier mTextClassifier;
    private TextClassifier mTextClassificationSession;

    // A flag to prevent repeated movements from escaping the enclosing text view. The idea here is
    // that if a user is holding down a movement key to traverse text, we shouldn't also traverse
    // the view hierarchy. On the other hand, if the user is using the movement key to traverse
    // views (i.e. the first movement was to traverse out of this view, or this view was traversed
    // into by the user holding the movement key down) then we shouldn't prevent the focus from
    // changing.
    private boolean mPreventDefaultMovement;

    private TextUtils.TruncateAt mEllipsize;

    // Do not update following mText/mSpannable/mPrecomputed except for setTextInternal()
    @ViewDebug.ExportedProperty(category = "text")
    private @Nullable Editable mText;
    private @Nullable Spannable mSpannable;
//    private @Nullable PrecomputedText mPrecomputed;

    private CharSequence mTransformed;
//    private TextView.BufferType mBufferType = TextView.BufferType.NORMAL;

    private CharSequence mHint;
    private Layout mHintLayout;

    private MovementMethod mMovement;

    private TransformationMethod mTransformation;
    private boolean mAllowTransformationLengthChange;
    private ChangeWatcher mChangeWatcher;

    private ArrayList<TextWatcher> mListeners;

    // display attributes
    private final TextPaint mTextPaint;
    private boolean mUserSetTextScaleX;
    private Layout mLayout;
    private boolean mLocalesChanged = false;

    // True if setKeyListener() has been explicitly called
    private boolean mListenerChanged = false;
    // True if internationalized input should be used for numbers and date and time.
    private final boolean mUseInternationalizedInput;
    // True if fallback fonts that end up getting used should be allowed to affect line spacing.
    /* package */ boolean mUseFallbackLineSpacing;

    @ViewDebug.ExportedProperty(category = "text")
    private int mGravity = Gravity.TOP | Gravity.START;
    private boolean mHorizontallyScrolling;

    private int mAutoLinkMask;
    private boolean mLinksClickable = true;

    private float mSpacingMult = 1.0f;
    private float mSpacingAdd = 0.0f;

    private int mBreakStrategy;
    private int mHyphenationFrequency;
    private int mJustificationMode;

    private int mMaximum = Integer.MAX_VALUE;
    private int mMaxMode = LINES;
    private int mMinimum = 0;
    private int mMinMode = LINES;

    private int mOldMaximum = mMaximum;
    private int mOldMaxMode = mMaxMode;

    private int mMaxWidth = Integer.MAX_VALUE;
    private int mMaxWidthMode = PIXELS;
    private int mMinWidth = 0;
    private int mMinWidthMode = PIXELS;

    private boolean mSingleLine;
    private int mDesiredHeightAtMeasure = -1;
    private boolean mIncludePad = true;
    private int mDeferScroll = -1;

    // tmp primitives, so we don't alloc them on each draw
    private Rect mTempRect;
    private long mLastScroll;
    private Scroller mScroller;
    private TextPaint mTempTextPaint;

    private BoringLayout.Metrics mBoring, mHintBoring;
    private BoringLayout mSavedLayout, mSavedHintLayout;

    private TextDirectionHeuristic mTextDir;

    private InputFilter[] mFilters = NO_FILTERS;

    private volatile Locale mCurrentSpellCheckerLocaleCache;

    // It is possible to have a selection even when mEditor is null (programmatically set, like when
    // a link is pressed). These highlight-related fields do not go in mEditor.
    int mHighlightColor = 0x6633B5E5;
    private Path mHighlightPath;
    private final Paint mHighlightPaint;
    private boolean mHighlightPathBogus = true;

    // Although these fields are specific to editable text, they are not added to Editor because
    // they are defined by the TextView's style and are theme-dependent.
    int mCursorDrawableRes;
    // These six fields, could be moved to Editor, since we know their default values and we
    // could condition the creation of the Editor to a non standard value. This is however
    // brittle since the hardcoded values here (such as
    // com.android.internal.R.drawable.text_select_handle_left) would have to be updated if the
    // default style is modified.
    int mTextSelectHandleLeftRes;
    int mTextSelectHandleRightRes;
    int mTextSelectHandleRes;
    int mTextEditSuggestionItemLayout;
    int mTextEditSuggestionContainerLayout;
    int mTextEditSuggestionHighlightStyle;

    /**
     * {@link EditText} specific data, created on demand when one of the Editor fields is used.
     */
    private Editor mEditor = new Editor(this);

    private static final int DEVICE_PROVISIONED_UNKNOWN = 0;
    private static final int DEVICE_PROVISIONED_NO = 1;
    private static final int DEVICE_PROVISIONED_YES = 2;

    /**
     * Some special options such as sharing selected text should only be shown if the device
     * is provisioned. Only check the provisioned state once for a given view instance.
     */
    private int mDeviceProvisionedState = DEVICE_PROVISIONED_UNKNOWN;

    // Autofill-related attributes
    //
    // Indicates whether the text was set statically or dynamically, so it can be used to
    // sanitize autofill requests.
    private boolean mTextSetFromXmlOrResourceId = false;
    // Resource id used to set the text.
    private @StringRes int mTextId = /*ResourceId.ID_NULL*/0;
    // Last value used on AFM.notifyValueChanged(), used to optimize autofill workflow by avoiding
    // calls when the value did not change
    private CharSequence mLastValueSentToAutofillManager;
    //
    // End of autofill-related attributes



    public EditText(Context context) {
        this(context, null);
    }

    public EditText(Context context, AttributeSet attrs) {
        // R.attr.editTextStyle pulls in the underline in the DynamicLayout and allows this view to
        // be focusable by default. it also adds the line at the bottom of the view. this only seems
        // to make a difference when using appcompat. removing this makes it look like when the
        // activity doesn't use appcompat.
        //TODO: investigate this more. should I build out some default so if someone else wants to
        // take and use this in a non-appcompat activity, it would work normally, or is there
        // anything else to handle better here?
        this(context, attrs, R.attr.editTextStyle);
    }

    public EditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        android.widget.EditText referenceEditText = new android.widget.EditText(context);
//        Log.w(TAG, "Reference EditText: totalPaddingTop=" + referenceEditText.getTotalPaddingTop()//30
//                + ", extendedPaddingTop=" + referenceEditText.getExtendedPaddingTop()//30
//                + ", compoundPaddingTop=" + referenceEditText.getCompoundPaddingTop()//30
//                + ", paddingTop=" + referenceEditText.getPaddingTop());//30
//        Log.w(TAG, "Reference EditText: totalPaddingBottom=" + referenceEditText.getTotalPaddingBottom()//33
//                + ", extendedPaddingBottom=" + referenceEditText.getExtendedPaddingBottom()//33
//                + ", compoundPaddingBottom=" + referenceEditText.getCompoundPaddingBottom()//33
//                + ", paddingBottom=" + referenceEditText.getPaddingBottom());//33
//        Log.w(TAG, "Reference EditText: totalPaddingRight=" + referenceEditText.getTotalPaddingRight()//12
////                + ", extendedPaddingRight=" + referenceEditText.getExtendedPaddingRight()//
//                + ", compoundPaddingRight=" + referenceEditText.getCompoundPaddingRight()//12
//                + ", paddingRight=" + referenceEditText.getPaddingRight());//12
//
//        Log.w(TAG, "Reference EditText: lineSpacingExtra=" + referenceEditText.getLineSpacingExtra()//0.0
//                + ", lineSpacingMultiplier=" + referenceEditText.getLineSpacingMultiplier()//1.0
//                + ", lineHeight=" + referenceEditText.getLineHeight()//63
//                + ", textSize=" + referenceEditText.getTextSize());//54.0
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//            Log.w(TAG, "Reference EditText: firstBaselineToTopHeight=" + referenceEditText.getFirstBaselineToTopHeight()//88
//                    + ", lastBaselineToBottomHeight=" + referenceEditText.getLastBaselineToBottomHeight());//48
//        }
//        Log.w(TAG, "Reference EditText: includeFontPadding=" + referenceEditText.getIncludeFontPadding()//true
//                + ", fontFeatureSettings=" + referenceEditText.getFontFeatureSettings()//null
//                + ", fontVariationSettings=" + referenceEditText.getFontVariationSettings());//null
        //mTextPaint.getFontMetricsInt(null)//(63-0.0)/1.0

        //android:textSize - impacts the text size and spacing before and after all lines
        //android:lineHeight - impacts spacing between lines of text
        //android:lineSpacingExtra - impacts spacing between lines of text
        //android:lineSpacingMultiplier - impacts spacing between lines of text
        //android:firstBaselineToTopHeight - impacts vertical spacing before the first line of text
        //android:lastBaselineToBottomHeight - impacts vertical spacing after the last line of text


        mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.density = getResources().getDisplayMetrics().density;

        mHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
//        mTextPaint.setColor(textColor);
//        mTextPaint.setTextAlign(Paint.Align.LEFT);
//        mTextPaint.setTextSize(textSize);
        //this gives better spacing, but it doesn't seem to be what the default edit text uses (off by default)
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            mTextPaint.setElegantTextHeight(true);
//        }
        //R.styleable.TextView_textScaleX -> mTextPaint.setTextScaleX
        //R.styleable.TextAppearance_fontFamily, R.styleable.TextAppearance_typeface, R.styleable.TextAppearance_textStyle, R.styleable.TextAppearance_textFontWeight -> mTextPaint.setTypeface
        //R.styleable.TextAppearance_elegantTextHeight -> mTextPaint.setElegantTextHeight - impacts vertical padding, but it isn't the default that I'm looking for
        //R.styleable.TextAppearance_letterSpacing -> mTextPaint.setLetterSpacing - doesn't seem to impact vertical padding
        //R.styleable.TextAppearance_fontFeatureSettings -> mTextPaint.setFontFeatureSettings - doesn't seem to impact vertical padding
        //R.styleable.TextAppearance_fontVariationSettings -> mTextPaint.setFontVariationSettings - doesn't seem to do anything
//        mTextPaint.setTextScaleX(1.0f);
        Log.w(TAG, "getFontMetricsInt=" + mTextPaint.getFontMetricsInt(null));

        Log.w(TAG, "paddingTop=" + getPaddingTop()//
                + ", paddingBottom=" + getPaddingBottom()//
                + ", paddingRight=" + getPaddingRight());//


        setTextInternal("");

        mMovement = ArrowKeyMovementMethod.getInstance();

        final TextAppearanceAttributes attributes = new TextAppearanceAttributes();
        attributes.mTextColor = ColorStateList.valueOf(0xFF000000);
        attributes.mTextSize = 15;
//        mBreakStrategy = Layout.BREAK_STRATEGY_SIMPLE;
//        mHyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE;
        mJustificationMode = Layout.JUSTIFICATION_MODE_NONE;

        final Resources.Theme theme = context.getTheme();

        /*
         * Look the appearance up without checking first if it exists because
         * almost every TextView has one and it greatly simplifies the logic
         * to be able to parse the appearance first and then let specific tags
         * for this View override it.
         */
        TypedArray typedArray = theme.obtainStyledAttributes(attrs,
                R.styleable.TextViewAppearance, defStyleAttr, /*defStyleRes*/0);
        TypedArray appearance = null;
        int ap = typedArray.getResourceId(
                R.styleable.TextViewAppearance_android_textAppearance, -1);
        typedArray.recycle();
        if (ap != -1) {
            appearance = theme.obtainStyledAttributes(ap, R.styleable.TextAppearance);
        }
        if (appearance != null) {
            readTextAppearance(context, appearance, attributes, false /* styleArray */);
            attributes.mFontFamilyExplicit = false;
            appearance.recycle();
        }

        boolean editable = /*getDefaultEditable()*/true;
        CharSequence inputMethod = null;
        int numeric = 0;
        CharSequence digits = null;
        boolean phone = false;
        boolean autotext = false;
        int autocap = -1;
        int buffertype = 0;
        boolean selectallonfocus = false;
        Drawable drawableLeft = null, drawableTop = null, drawableRight = null,
                drawableBottom = null, drawableStart = null, drawableEnd = null;
        ColorStateList drawableTint = null;
        PorterDuff.Mode drawableTintMode = null;
        int drawablePadding = 0;
//        int ellipsize = ELLIPSIZE_NOT_SET;
        boolean singleLine = false;
        int maxlength = -1;
        CharSequence text = "";
        CharSequence hint = null;
        boolean password = false;
        int inputType = EditorInfo.TYPE_NULL;
        typedArray = theme.obtainStyledAttributes(
                attrs, /*com.android.internal.R.styleable.TextView*/R.styleable.CustomEditTextView, defStyleAttr, /*defStyleRes*/0);
        int firstBaselineToTopHeight = -1;
        int lastBaselineToBottomHeight = -1;
        int lineHeight = -1;

        readTextAppearance(context, typedArray, attributes, true /* styleArray */);

        for (int i = 0; i < typedArray.getIndexCount(); i++) {
            int attr = typedArray.getIndex(i);
            if (attr == R.styleable.CustomEditTextView_android_editable) {
                // skipping - editable is deprecated: Use inputType instead
            } else if (attr == R.styleable.CustomEditTextView_android_inputMethod) {
                // skipping - inputMethod is deprecated: Use inputType instead
            } else if (attr == R.styleable.CustomEditTextView_android_numeric) {
                // skipping - numeric is deprecated: Use inputType instead
            } else if (attr == R.styleable.CustomEditTextView_android_digits) {
                //TODO: probably implement - this supports more than just numbers (unsure if this is
                // intended functionality or just a hack). if this isn't added, at least add some
                // other means to filter input to only allow certain characters
                // If set, specifies that this TextView has a numeric input method and that these
                // specific characters are the ones that it will accept. If this is set, numeric is
                // implied to be true. The default is false.
            } else if (attr == R.styleable.CustomEditTextView_android_phoneNumber) {
                // skipping - phoneNumber is deprecated: Use inputType instead
            } else if (attr == R.styleable.CustomEditTextView_android_autoText) {
                // skipping - autoText is deprecated: Use inputType instead
            } else if (attr == R.styleable.CustomEditTextView_android_capitalize) {
                //skipping - capitalize is deprecated: Use inputType instead
            } else if (attr == R.styleable.CustomEditTextView_android_bufferType) {
                // skipping - EditText always returns Editable, even if you specify something less
                // powerful
            } else if (attr == R.styleable.CustomEditTextView_android_selectAllOnFocus) {
                //TODO: consider implementing
                // If the text is selectable, select it all when the view takes focus.
            } else if (attr == R.styleable.CustomEditTextView_android_autoLink) {
                // probably skip - EditText doesn't update the links as you type, so this doesn't
                // seem very beneficial
                // Controls whether links such as urls and email addresses are automatically found
                // and converted to clickable links. The default value is "none", disabling this
                // feature.
            } else if (attr == R.styleable.CustomEditTextView_android_linksClickable) {
                // probably skip - If set to false, keeps the movement method from being set to the
                // link movement method even if autoLink causes links to be found.
            } else if (attr == R.styleable.CustomEditTextView_android_drawableLeft) {
                // probably skip - The drawable to be drawn to the left of the text.
            } else if (attr == R.styleable.CustomEditTextView_android_drawableTop) {
                // probably skip
            } else if (attr == R.styleable.CustomEditTextView_android_drawableRight) {
                // probably skip
            } else if (attr == R.styleable.CustomEditTextView_android_drawableBottom) {
                // probably skip
            } else if (attr == R.styleable.CustomEditTextView_android_drawableStart) {
                // probably skip
            } else if (attr == R.styleable.CustomEditTextView_android_drawableEnd) {
                // probably skip
//            } else if (&& attr == R.styleable.TextView_android_drawableTint) {
//                // probably skip
//                // can't use for some reason - maybe because it looks like it is just defined with
//                // an ID and not a type
//            } else if (attr == R.styleable.TextView_android_drawableTintMode) {
//                // probably skip
//                // can't use for some reason - maybe because it looks like it is just defined with
//                // an ID and not a type
            } else if (attr == R.styleable.CustomEditTextView_android_drawablePadding) {
                // probably skip
            } else if (attr == R.styleable.CustomEditTextView_android_maxLines) {
                // Makes the TextView be at most this many lines tall. The inputType attribute's
                // value must be combined with the textMultiLine flag for the maxLines attribute to
                // apply.
                setMaxLines(typedArray.getInt(attr, -1));
            } else if (attr == R.styleable.CustomEditTextView_android_maxHeight) {
                // Makes the TextView be at most this many pixels tall.
                setMaxHeight(typedArray.getDimensionPixelSize(attr, -1));
            } else if (attr == R.styleable.CustomEditTextView_android_lines) {
                // Makes the TextView be exactly this many lines tall.
                //TODO: this doesn't actually limit the number of lines (even in EditText) - it's
                // only the visual height - consider if it's worth not supporting
                setLines(typedArray.getInt(attr, -1));
            } else if (attr == R.styleable.CustomEditTextView_android_height) {
                // Makes the TextView be exactly this tall. You could get the same effect by
                // specifying this number in the layout parameters.
                setHeight(typedArray.getDimensionPixelSize(attr, -1));
            } else if (attr == R.styleable.CustomEditTextView_android_minLines) {
                // Makes the TextView be at least this many lines tall. The inputType attribute's
                // value must be combined with the textMultiLine flag for the minLines attribute to
                // apply.
                setMinLines(typedArray.getInt(attr, -1));
            } else if (attr == R.styleable.CustomEditTextView_android_minHeight) {
                // Makes the TextView be at least this many pixels tall.
                setMinHeight(typedArray.getDimensionPixelSize(attr, -1));
            } else if (attr == R.styleable.CustomEditTextView_android_maxEms) {
                // Makes the TextView be at most this many ems wide.
                setMaxEms(typedArray.getInt(attr, -1));
            } else if (attr == R.styleable.CustomEditTextView_android_maxWidth) {
                // Makes the TextView be at most this many pixels wide.
                setMaxWidth(typedArray.getDimensionPixelSize(attr, -1));
            } else if (attr == R.styleable.CustomEditTextView_android_ems) {
                // Makes the TextView be exactly this many ems wide.
                setEms(typedArray.getInt(attr, -1));
            } else if (attr == R.styleable.CustomEditTextView_android_width) {
                // Makes the TextView be exactly this wide. You could get the same effect by
                // specifying this number in the layout parameters.
                setWidth(typedArray.getDimensionPixelSize(attr, -1));
            } else if (attr == R.styleable.CustomEditTextView_android_minEms) {
                // Makes the TextView be at least this many ems wide.
                setMinEms(typedArray.getInt(attr, -1));
            } else if (attr == R.styleable.CustomEditTextView_android_minWidth) {
                // Makes the TextView be at least this many pixels wide.
                setMinWidth(typedArray.getDimensionPixelSize(attr, -1));
            } else if (attr == R.styleable.CustomEditTextView_android_gravity) {
                // Specifies how to align the text by the view's x- and/or y-axis when the text is
                // smaller than the view.
                setGravity(typedArray.getInt(attr, -1));
            } else if (attr == R.styleable.CustomEditTextView_android_hint) {
                hint = typedArray.getText(attr);
            } else if (attr == R.styleable.CustomEditTextView_android_text) {
                text = typedArray.getText(attr);
            } else if (attr == R.styleable.CustomEditTextView_android_scrollHorizontally) {
                //skipping - doesn't seem to have any effect in an EditText
            } else if (attr == R.styleable.CustomEditTextView_android_singleLine) {
                //skipping - deprecated. Use maxLines instead to change the layout of a static text,
                // and use the textMultiLine flag in the inputType attribute instead for editable
                // text views (if both singleLine and inputType are supplied, the inputType flags
                // will override the value of singleLine)
            } else if (attr == R.styleable.CustomEditTextView_android_ellipsize) {
                // skipping - doesn't seem to work in EditText (although only marquee is called out
                // as not supported)
            } else if (attr == R.styleable.CustomEditTextView_android_marqueeRepeatLimit) {
                // skipping - not supported in EditText
            } else if (attr == R.styleable.CustomEditTextView_android_includeFontPadding) {
                // Leave enough room for ascenders and descenders instead of using the font ascent
                // and descent strictly. (Normally true).
                if (!typedArray.getBoolean(attr, true)) {
                    setIncludeFontPadding(false);
                }
            } else if (attr == R.styleable.CustomEditTextView_android_cursorVisible) {
                //TODO: consider implementing if simple - not really sure why this would be wanted
                // Makes the cursor visible (the default) or invisible.
            } else if (attr == R.styleable.CustomEditTextView_android_maxLength) {
                // Set an input filter to constrain the text length to the specified number.
                maxlength = typedArray.getInt(attr, -1);
            } else if (attr == R.styleable.CustomEditTextView_android_textScaleX) {
                // Sets the horizontal scaling factor for the text.
                setTextScaleX(typedArray.getFloat(attr, 1.0f));
            } else if (attr == R.styleable.CustomEditTextView_android_freezesText) {
                //skipping - If set, the text view will include its current complete text inside of
                // its frozen icicle in addition to meta-data such as the current cursor position.
                // By default this is disabled; it can be useful when the contents of a text view is
                // not stored in a persistent place such as a content provider. For
                // {@link android.widget.EditText} it is always enabled, regardless of the value of
                // the attribute.
            } else if (attr == R.styleable.CustomEditTextView_android_enabled) {
                setEnabled(typedArray.getBoolean(attr, isEnabled()));
            } else if (attr == R.styleable.CustomEditTextView_android_password) {
                //skipping - deprecated: Use inputType instead
            } else if (attr == R.styleable.CustomEditTextView_android_lineSpacingExtra) {
                // Extra spacing between lines of text. The value will not be applied for the last
                // line of text.
                mSpacingAdd = typedArray.getDimensionPixelSize(attr, (int) mSpacingAdd);
            } else if (attr == R.styleable.CustomEditTextView_android_lineSpacingMultiplier) {
                // Extra spacing between lines of text, as a multiplier. The value will not be
                // applied for the last line of text.
                mSpacingMult = typedArray.getFloat(attr, mSpacingMult);
            } else if (attr == R.styleable.CustomEditTextView_android_inputType) {
                // The type of data being placed in a text field, used to help an input method
                // decide how to let the user enter text. The constants here correspond to those
                // defined by {@link android.text.InputType}. Generally you can select a single
                // value, though some can be combined together as indicated. Setting this attribute
                // to anything besides none also implies that the text is editable.
                // Values:
                // date, datetime, none, number, numberDecimal, numberPassword, numberSigned, phone,
                // text, textAutoComplete, textAutoCorrect, textCapCharacters, textCapSentences,
                // textCapWords, textEmailAddress, textEmailSubject, textFilter, textImeMultiLine,
                // textLongMessage, textMultiLine, textNoSuggestions, textPassword, textPersonName,
                // textPhonetic, textPostalAddress, textShortMessage, textUri, textVisiblePassword,
                // textWebEditText, textWebEmailAddress, textWebPassword, time
                inputType = typedArray.getInt(attr, EditorInfo.TYPE_NULL);
            } else if (attr == R.styleable.CustomEditTextView_android_allowUndo) {
                //TODO: consider implementing
                // Whether undo should be allowed for editable text. Defaults to true.
            } else if (attr == R.styleable.CustomEditTextView_android_imeOptions) {
                // Additional features you can enable in an IME associated with an editor to improve
                // the integration with your application. The constants here correspond to those
                // defined by {@link android.view.inputmethod.EditorInfo#imeOptions}.
                // Values:
                // actionDone, actionGo, actionNext, actionNone, actionPrevious, actionSearch,
                // actionSend, actionUnspecified, flagForceAscii, flagNavigateNext,
                // flagNavigatePrevious, flagNoAccessoryAction, flagNoEnterAction, flagNoExtractUi,
                // flagNoFullscreen, flagNoPersonalizedLearning, normal
                mEditor.createInputContentTypeIfNeeded();
                mEditor.mInputContentType.imeOptions = typedArray.getInt(attr,
                        mEditor.mInputContentType.imeOptions);
            } else if (attr == R.styleable.CustomEditTextView_android_imeActionLabel) {
                //TODO: implement
                // Supply a value for {@link android.view.inputmethod.EditorInfo#actionLabel
                // EditorInfo.actionLabel} used when an input method is connected to the text view.
            } else if (attr == R.styleable.CustomEditTextView_android_imeActionId) {
                //TODO: implement
                // Supply a value for {@link android.view.inputmethod.EditorInfo#actionId
                // EditorInfo.actionId} used when an input method is connected to the text view.
            } else if (attr == R.styleable.CustomEditTextView_android_privateImeOptions) {
                //TODO: implement
                // An addition content type description to supply to the input method attached to
                // the text view, which is private to the implementation of the input method. This
                // simply fills in the {@link android.view.inputmethod.EditorInfo#privateImeOptions
                // EditorInfo.privateImeOptions} field when the input method is connected.
            } else if (attr == R.styleable.CustomEditTextView_android_editorExtras) {
                //TODO: probably implement
                // Reference to an {@link android.R.styleable#InputExtras <input-extras>} XML
                // resource containing additional data to supply to an input method, which is
                // private to the implementation of the input method. This simply fills in the
                // {@link android.view.inputmethod.EditorInfo#extras EditorInfo.extras} field when
                // the input method is connected.
            } else if (attr == R.styleable.CustomEditTextView_android_textCursorDrawable) {
                mCursorDrawableRes = typedArray.getResourceId(attr, 0);
            } else if (attr == R.styleable.CustomEditTextView_android_textSelectHandleLeft) {
                mTextSelectHandleLeftRes = typedArray.getResourceId(attr, 0);
            } else if (attr == R.styleable.CustomEditTextView_android_textSelectHandleRight) {
                mTextSelectHandleRightRes = typedArray.getResourceId(attr, 0);
            } else if (attr == R.styleable.CustomEditTextView_android_textSelectHandle) {
                mTextSelectHandleRes = typedArray.getResourceId(attr, 0);
            } else if (attr == R.styleable.CustomEditTextView_android_textEditSuggestionItemLayout) {
                //probably skip - Layout of the TextView item that will populate the suggestion
                // popup window.
//            } else if (attr == R.styleable.TextView_android_textEditSuggestionContainerLayout) {
//                //skipping - textEditSuggestionContainerLayout is private
//            } else if (attr == R.styleable.TextView_android_textEditSuggestionHighlightStyle) {
//                //skipping - textEditSuggestionHighlightStyle is private
            } else if (attr == R.styleable.CustomEditTextView_android_textIsSelectable) {
                //skipping - doesn't seem to have any effect in an EditText
            } else if (attr == R.styleable.CustomEditTextView_android_breakStrategy) {
                //skipping - Break strategy (control over paragraph layout).
            } else if (attr == R.styleable.CustomEditTextView_android_hyphenationFrequency) {
                //skipping - Frequency of automatic hyphenation
            } else if (attr == R.styleable.CustomEditTextView_android_autoSizeTextType) {
                //skipping - this feature is not supported by EditText
            } else if (attr == R.styleable.CustomEditTextView_android_autoSizeStepGranularity) {
                //skipping - this feature is not supported by EditText
            } else if (attr == R.styleable.CustomEditTextView_android_autoSizeMinTextSize) {
                //skipping - this feature is not supported by EditText
            } else if (attr == R.styleable.CustomEditTextView_android_autoSizeMaxTextSize) {
                //skipping - this feature is not supported by EditText
            } else if (attr == R.styleable.CustomEditTextView_android_autoSizePresetSizes) {
                //skipping - this feature is not supported by EditText
            } else if (attr == R.styleable.CustomEditTextView_android_justificationMode) {
                // Mode for justification.
                //TODO: this doesn't apply the justification as you type (even in EditText), so it
                // may be better to just remove support for this
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mJustificationMode = typedArray.getInt(attr, Layout.JUSTIFICATION_MODE_NONE);
                }
            } else if (attr == R.styleable.CustomEditTextView_android_firstBaselineToTopHeight) {
                // Distance from the top of the TextView to the first text baseline. If set, this
                // overrides the value set for paddingTop.
                // only used in API level 28 and higher
                firstBaselineToTopHeight = typedArray.getDimensionPixelSize(attr, -1);
            } else if (attr == R.styleable.CustomEditTextView_android_lastBaselineToBottomHeight) {
                // Distance from the bottom of the TextView to the last text baseline. If set, this
                // overrides the value set for paddingBottom.
                // only used in API level 28 and higher
                lastBaselineToBottomHeight = typedArray.getDimensionPixelSize(attr, -1);
            } else if (attr == R.styleable.CustomEditTextView_android_lineHeight) {
                // Explicit height between lines of text. If set, this will override the values set
                // for lineSpacingExtra and lineSpacingMultiplier.
                // only used in API level 28 and higher
                lineHeight = typedArray.getDimensionPixelSize(attr, -1);
            }
        }

        typedArray.recycle();

        final int variation =
                inputType & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_MASK_VARIATION);
        final boolean passwordInputType = variation
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
        final boolean webPasswordInputType = variation
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD);
        final boolean numberPasswordInputType = variation
                == (EditorInfo.TYPE_CLASS_NUMBER | EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD);

        final int targetSdkVersion = context.getApplicationInfo().targetSdkVersion;
        mUseInternationalizedInput = targetSdkVersion >= Build.VERSION_CODES.O;
        mUseFallbackLineSpacing = targetSdkVersion >= Build.VERSION_CODES.P;

        if (inputType != EditorInfo.TYPE_NULL) {
            setInputType(inputType, true);
            // If set, the input type overrides what was set using the deprecated singleLine flag.
            singleLine = !isMultilineInputType(inputType);
            Log.w(TAG, "constructor: singleLine=" + singleLine);
        } else if (isTextSelectable()) {
            // Prevent text changes from keyboard.
            if (mEditor != null) {
                mEditor.mKeyListener = null;
                mEditor.mInputType = EditorInfo.TYPE_NULL;
            }
//            bufferType = TextView.BufferType.SPANNABLE;
//            // So that selection can be changed using arrow keys and touch is handled.
//            setMovementMethod(ArrowKeyMovementMethod.getInstance());
        } else {
            if (mEditor != null) mEditor.mKeyListener = null;

//            switch (buffertype) {
//                case 0:
//                    bufferType = TextView.BufferType.NORMAL;
//                    break;
//                case 1:
//                    bufferType = TextView.BufferType.SPANNABLE;
//                    break;
//                case 2:
//                    bufferType = TextView.BufferType.EDITABLE;
//                    break;
//            }
        }

        if (mEditor != null) {
            mEditor.adjustInputType(password, passwordInputType, webPasswordInputType,
                    numberPasswordInputType);
        }

        // Same as setSingleLine(), but make sure the transformation method and the maximum number
        // of lines of height are unchanged for multi-line TextViews.
        setInputTypeSingleLine(singleLine);
        applySingleLine(singleLine, singleLine, singleLine);

        final boolean isPassword = password || passwordInputType || webPasswordInputType
                || numberPasswordInputType;
        final boolean isMonospaceEnforced = isPassword || (mEditor != null
                && (mEditor.mInputType
                & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_MASK_VARIATION))
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD));
        if (isMonospaceEnforced) {
            //TODO: for some reason when using appcompat the default EditText doesn't use monospace
            // while this code, which is copied from it does. figure out if that's just a bug with
            // appcompat or if we should be pulling in something that would override the change to
            // monospace. if I set fontFamily="sans-serif" on this, it overrides the monospace and
            // looks like the default EditText, so maybe something like that is being set somewhere
            // and we just need to pull it in.
            attributes.mTypefaceIndex = MONOSPACE;
        }

        applyTextAppearance(attributes);

        if (isPassword) {
            setTransformationMethod(PasswordTransformationMethod.getInstance());
        }

        if (maxlength >= 0) {
            setFilters(new InputFilter[] { new InputFilter.LengthFilter(maxlength) });
        } else {
            setFilters(NO_FILTERS);
        }

        setText(text);
        if (hint != null) setHint(hint);


        // these seem to be necessary only when the activity isn't appcompat
        //TODO: investigate this more. are these getting set in the aosp version somewhere else?
        // what is appcompat doing that doesn't require this?
//        setEnabled(true);
        setClickable(true);
//        setFocusable(true);
        setFocusableInTouchMode(true);

        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        mEditor.prepareCursorControllers();

        if (firstBaselineToTopHeight >= 0) {
            setFirstBaselineToTopHeight(firstBaselineToTopHeight);
        }
        if (lastBaselineToBottomHeight >= 0) {
            setLastBaselineToBottomHeight(lastBaselineToBottomHeight);
        }
        if (lineHeight >= 0) {
            setLineHeight(lineHeight);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.w(TAG, "getFocusable=" + getFocusable());
        }
        Log.w(TAG, "isClickable=" + isClickable());
    }

    // Update mText and mPrecomputed
    private void setTextInternal(@Nullable CharSequence text) {
        setTextInternal(text == null ? null : Editable.Factory.getInstance().newEditable(text));
    }
    private void setTextInternal(@Nullable Editable text) {
        mText = text;
        mSpannable = (text instanceof Spannable) ? (Spannable) text : null;
    }

    /**
     * Sets the Typeface taking into account the given attributes.
     *
     * @param typeface a typeface
     * @param familyName family name string, e.g. "serif"
     * @param typefaceIndex an index of the typeface enum, e.g. SANS, SERIF.
     * @param style a typeface style
     * @param weight a weight value for the Typeface or -1 if not specified.
     */
    @RequiresApi(api = Build.VERSION_CODES.P)
    private void setTypefaceFromAttrs(@Nullable Typeface typeface, @Nullable String familyName,
                                      int typefaceIndex, int style, int weight) {
        if (typeface == null && familyName != null) {
            // Lookup normal Typeface from system font map.
            final Typeface normalTypeface = Typeface.create(familyName, Typeface.NORMAL);
            resolveStyleAndSetTypeface(normalTypeface, style, weight);
        } else if (typeface != null) {
            resolveStyleAndSetTypeface(typeface, style, weight);
        } else {  // both typeface and familyName is null.
            switch (typefaceIndex) {
                case SANS:
                    resolveStyleAndSetTypeface(Typeface.SANS_SERIF, style, weight);
                    break;
                case SERIF:
                    resolveStyleAndSetTypeface(Typeface.SERIF, style, weight);
                    break;
                case MONOSPACE:
                    resolveStyleAndSetTypeface(Typeface.MONOSPACE, style, weight);
                    break;
                case DEFAULT_TYPEFACE:
                default:
                    resolveStyleAndSetTypeface(null, style, weight);
                    break;
            }
        }
    }
    private void setTypefaceFromAttrs(Typeface fontTypeface, String familyName, int typefaceIndex,
                                      int style) {
        Typeface tf = fontTypeface;
        if (tf == null && familyName != null) {
            tf = Typeface.create(familyName, style);
        } else if (tf != null && tf.getStyle() != style) {
            tf = Typeface.create(tf, style);
        }
        if (tf != null) {
            setTypeface(tf);
            return;
        }
        switch (typefaceIndex) {
            case SANS:
                tf = Typeface.SANS_SERIF;
                break;
            case SERIF:
                tf = Typeface.SERIF;
                break;
            case MONOSPACE:
                tf = Typeface.MONOSPACE;
                break;
        }
        setTypeface(tf, style);
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private void resolveStyleAndSetTypeface(Typeface typeface, int style, int weight) {
        if (weight >= 0) {
            final boolean italic = (style & Typeface.ITALIC) != 0;
            setTypeface(Typeface.create(typeface, weight, italic));
        } else {
            setTypeface(typeface, style);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        Log.w(TAG, "setEnabled " + enabled);
        if (enabled == isEnabled()) {
            return;
        }

        if (!enabled) {
            // Hide the soft input if the currently active TextView is disabled
            InputMethodManager imm = getInputMethodManager();
            if (imm != null && imm.isActive(this)) {
                imm.hideSoftInputFromWindow(getWindowToken(), 0);
            }
        }

        super.setEnabled(enabled);

        if (enabled) {
            // Make sure IME is updated with current editor info.
            InputMethodManager imm = getInputMethodManager();
            if (imm != null) imm.restartInput(this);
        }

        // Will change text color
//        if (mEditor != null) {
//            mEditor.invalidateTextDisplayList();
        mEditor.prepareCursorControllers();

        // start or stop the cursor blinking as appropriate
        mEditor.makeBlink();
//        }
    }

    /**
     * Sets the typeface and style in which the text should be displayed,
     * and turns on the fake bold and italic bits in the Paint if the
     * Typeface that you provided does not have all the bits in the
     * style that you specified.
     *
     * @attr ref android.R.styleable#TextView_typeface
     * @attr ref android.R.styleable#TextView_textStyle
     */
    public void setTypeface(@Nullable Typeface tf, /*@Typeface.Style */int style) {
        if (style > 0) {
            if (tf == null) {
                tf = Typeface.defaultFromStyle(style);
            } else {
                tf = Typeface.create(tf, style);
            }

            setTypeface(tf);
            // now compute what (if any) algorithmic styling is needed
            int typefaceStyle = tf != null ? tf.getStyle() : 0;
            int need = style & ~typefaceStyle;
            mTextPaint.setFakeBoldText((need & Typeface.BOLD) != 0);
            mTextPaint.setTextSkewX((need & Typeface.ITALIC) != 0 ? -0.25f : 0);
        } else {
            mTextPaint.setFakeBoldText(false);
            mTextPaint.setTextSkewX(0);
            setTypeface(tf);
        }
    }

    /**
     * Return the text that TextView is displaying. If {@link #setText(CharSequence)} was called
     * with an argument of {@link android.widget.TextView.BufferType#SPANNABLE BufferType.SPANNABLE}
     * or {@link android.widget.TextView.BufferType#EDITABLE BufferType.EDITABLE}, you can cast
     * the return value from this method to Spannable or Editable, respectively.
     *
     * <p>The content of the return value should not be modified. If you want a modifiable one, you
     * should make your own copy first.</p>
     *
     * @return The text displayed by the text view.
     * @attr ref android.R.styleable#TextView_text
     */
    public Editable getText() {
        return mText;
    }

    /**
     * Return the text that TextView is displaying as an Editable object. If the text is not
     * editable, null is returned.
     *
     * @see #getText
     */
    @Override
    public Editable getEditableText() {
        return mText;
    }

    /**
     * Gets the vertical distance between lines of text, in pixels.
     * Note that markup within the text can cause individual lines
     * to be taller or shorter than this height, and the layout may
     * contain additional first-or last-line padding.
     * @return The height of one standard line in pixels.
     */
    public int getLineHeight() {
        return Math.round(mTextPaint.getFontMetricsInt(null) * mSpacingMult + mSpacingAdd);
    }

    /**
     * Gets the {@link android.text.Layout} that is currently being used to display the text.
     * This value can be null if the text or width has recently changed.
     * @return The Layout that is currently being used to display the text.
     */
    public final Layout getLayout() {
        return mLayout;
    }

    /**
     * @return the {@link android.text.Layout} that is currently being used to
     * display the hint text. This can be null.
     */
    final Layout getHintLayout() {
        return mHintLayout;
    }

    /**
     * Gets the current {@link KeyListener} for the TextView.
     * This will frequently be null for non-EditText TextViews.
     * @return the current key listener for this TextView.
     *
     * @attr ref android.R.styleable#TextView_numeric
     * @attr ref android.R.styleable#TextView_digits
     * @attr ref android.R.styleable#TextView_phoneNumber
     * @attr ref android.R.styleable#TextView_inputMethod
     * @attr ref android.R.styleable#TextView_capitalize
     * @attr ref android.R.styleable#TextView_autoText
     */
    public final KeyListener getKeyListener() {
        return mEditor == null ? null : mEditor.mKeyListener;
    }

    /**
     * Sets the key listener to be used with this TextView.  This can be null
     * to disallow user input.  Note that this method has significant and
     * subtle interactions with soft keyboards and other input method:
     * see {@link KeyListener#getInputType() KeyListener.getInputType()}
     * for important details.  Calling this method will replace the current
     * content type of the text view with the content type returned by the
     * key listener.
     * <p>
     * Be warned that if you want a TextView with a key listener or movement
     * method not to be focusable, or if you want a TextView without a
     * key listener or movement method to be focusable, you must call
     * {@link #setFocusable} again after calling this to get the focusability
     * back the way you want it.
     *
     * @attr ref android.R.styleable#TextView_numeric
     * @attr ref android.R.styleable#TextView_digits
     * @attr ref android.R.styleable#TextView_phoneNumber
     * @attr ref android.R.styleable#TextView_inputMethod
     * @attr ref android.R.styleable#TextView_capitalize
     * @attr ref android.R.styleable#TextView_autoText
     */
    public void setKeyListener(KeyListener input) {
        mListenerChanged = true;
        setKeyListenerOnly(input);
        fixFocusableAndClickableSettings();

        if (input != null) {
            setInputTypeFromEditor();
        } else {
            if (mEditor != null) mEditor.mInputType = EditorInfo.TYPE_NULL;
        }

        InputMethodManager imm = getInputMethodManager();
        if (imm != null) imm.restartInput(this);
    }

    private void setInputTypeFromEditor() {
        try {
            mEditor.mInputType = mEditor.mKeyListener.getInputType();
        } catch (IncompatibleClassChangeError e) {
            mEditor.mInputType = EditorInfo.TYPE_CLASS_TEXT;
        }
        // Change inputType, without affecting transformation.
        // No need to applySingleLine since mSingleLine is unchanged.
        setInputTypeSingleLine(mSingleLine);
    }

    private void setKeyListenerOnly(KeyListener input) {
        if (mEditor == null && input == null) return; // null is the default value

        if (mEditor.mKeyListener != input) {
            mEditor.mKeyListener = input;
            if (input != null && !(mText instanceof Editable)) {
                setText(mText);
            }

            setFilters((Editable) mText, mFilters);
        }
    }

    /**
     * Gets the {@link android.text.method.MovementMethod} being used for this TextView,
     * which provides positioning, scrolling, and text selection functionality.
     * This will frequently be null for non-EditText TextViews.
     * @return the movement method being used for this TextView.
     * @see android.text.method.MovementMethod
     */
    public final MovementMethod getMovementMethod() {
        return mMovement;
    }

    private void fixFocusableAndClickableSettings() {
        if (mMovement != null || (mEditor != null && mEditor.mKeyListener != null)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setFocusable(FOCUSABLE);
            }
            setClickable(true);
            setLongClickable(true);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setFocusable(FOCUSABLE_AUTO);
            }
            setClickable(false);
            setLongClickable(false);
        }
    }

    /**
     * Gets the current {@link android.text.method.TransformationMethod} for the TextView.
     * This is frequently null, except for single-line and password fields.
     * @return the current transformation method for this TextView.
     *
     * @attr ref android.R.styleable#TextView_password
     * @attr ref android.R.styleable#TextView_singleLine
     */
    public final TransformationMethod getTransformationMethod() {
        return mTransformation;
    }

    /**
     * Sets the transformation that is applied to the text that this
     * TextView is displaying.
     *
     * @attr ref android.R.styleable#TextView_password
     * @attr ref android.R.styleable#TextView_singleLine
     */
    public final void setTransformationMethod(TransformationMethod method) {
        if (method == mTransformation) {
            // Avoid the setText() below if the transformation is
            // the same.
            return;
        }
        if (mTransformation != null) {
            if (mSpannable != null) {
                mSpannable.removeSpan(mTransformation);
            }
        }

        mTransformation = method;

//        if (method instanceof TransformationMethod2) {
//            TransformationMethod2 method2 = (TransformationMethod2) method;
//            mAllowTransformationLengthChange = !isTextSelectable() && !(mText instanceof Editable);
//            method2.setLengthChangesAllowed(mAllowTransformationLengthChange);
//        } else {
//        mAllowTransformationLengthChange = false;
//        }

        setText(mText);

        if (hasPasswordTransformationMethod()) {
//            notifyViewAccessibilityStateChangedIfNeeded(
//                    AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED);
        }

//        // PasswordTransformationMethod always have LTR text direction heuristics returned by
//        // getTextDirectionHeuristic, needs reset
//        mTextDir = getTextDirectionHeuristic();
    }

    /**
     * Returns the top padding of the view, plus space for the top
     * Drawable if any.
     */
    public int getCompoundPaddingTop() {
        return getPaddingTop();
    }

    /**
     * Returns the bottom padding of the view, plus space for the bottom
     * Drawable if any.
     */
    public int getCompoundPaddingBottom() {
        return getPaddingBottom();
    }

    /**
     * Returns the left padding of the view, plus space for the left
     * Drawable if any.
     */
    public int getCompoundPaddingLeft() {
        return getPaddingLeft();
    }

    /**
     * Returns the right padding of the view, plus space for the right
     * Drawable if any.
     */
    public int getCompoundPaddingRight() {
        return getPaddingRight();
    }

    /**
     * Returns the start padding of the view, plus space for the start
     * Drawable if any.
     */
    public int getCompoundPaddingStart() {
        switch(getLayoutDirection()) {
            default:
            case LAYOUT_DIRECTION_LTR:
                return getCompoundPaddingLeft();
            case LAYOUT_DIRECTION_RTL:
                return getCompoundPaddingRight();
        }
    }

    /**
     * Returns the end padding of the view, plus space for the end
     * Drawable if any.
     */
    public int getCompoundPaddingEnd() {
        switch(getLayoutDirection()) {
            default:
            case LAYOUT_DIRECTION_LTR:
                return getCompoundPaddingRight();
            case LAYOUT_DIRECTION_RTL:
                return getCompoundPaddingLeft();
        }
    }

    /**
     * Returns the extended top padding of the view, including both the
     * top Drawable if any and any extra space to keep more than maxLines
     * of text from showing.  It is only valid to call this after measuring.
     */
    public int getExtendedPaddingTop() {
        if (mMaxMode != LINES) {
            return getCompoundPaddingTop();
        }

        if (mLayout == null) {
            assumeLayout();
        }

        if (mLayout.getLineCount() <= mMaximum) {
            return getCompoundPaddingTop();
        }

        int top = getCompoundPaddingTop();
        int bottom = getCompoundPaddingBottom();
        int viewht = getHeight() - top - bottom;
        int layoutht = mLayout.getLineTop(mMaximum);

        if (layoutht >= viewht) {
            return top;
        }

        final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;
        if (gravity == Gravity.TOP) {
            return top;
        } else if (gravity == Gravity.BOTTOM) {
            return top + viewht - layoutht;
        } else { // (gravity == Gravity.CENTER_VERTICAL)
            return top + (viewht - layoutht) / 2;
        }
    }

    /**
     * Returns the extended bottom padding of the view, including both the
     * bottom Drawable if any and any extra space to keep more than maxLines
     * of text from showing.  It is only valid to call this after measuring.
     */
    public int getExtendedPaddingBottom() {
        if (mMaxMode != LINES) {
            return getCompoundPaddingBottom();
        }

        if (mLayout == null) {
            assumeLayout();
        }

        if (mLayout.getLineCount() <= mMaximum) {
            return getCompoundPaddingBottom();
        }

        int top = getCompoundPaddingTop();
        int bottom = getCompoundPaddingBottom();
        int viewht = getHeight() - top - bottom;
        int layoutht = mLayout.getLineTop(mMaximum);

        if (layoutht >= viewht) {
            return bottom;
        }

        final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;
        if (gravity == Gravity.TOP) {
            return bottom + viewht - layoutht;
        } else if (gravity == Gravity.BOTTOM) {
            return bottom;
        } else { // (gravity == Gravity.CENTER_VERTICAL)
            return bottom + (viewht - layoutht) / 2;
        }
    }

    /**
     * Returns the total left padding of the view, including the left
     * Drawable if any.
     */
    public int getTotalPaddingLeft() {
        return getCompoundPaddingLeft();
    }

    /**
     * Returns the total right padding of the view, including the right
     * Drawable if any.
     */
    public int getTotalPaddingRight() {
        return getCompoundPaddingRight();
    }

    /**
     * Returns the total start padding of the view, including the start
     * Drawable if any.
     */
    public int getTotalPaddingStart() {
        return getCompoundPaddingStart();
    }

    /**
     * Returns the total end padding of the view, including the end
     * Drawable if any.
     */
    public int getTotalPaddingEnd() {
        return getCompoundPaddingEnd();
    }

    /**
     * Returns the total top padding of the view, including the top
     * Drawable if any, the extra space to keep more than maxLines
     * from showing, and the vertical offset for gravity, if any.
     */
    public int getTotalPaddingTop() {
        return getExtendedPaddingTop() + getVerticalOffset(true);
    }

    /**
     * Returns the total bottom padding of the view, including the bottom
     * Drawable if any, the extra space to keep more than maxLines
     * from showing, and the vertical offset for gravity, if any.
     */
    public int getTotalPaddingBottom() {
        return getExtendedPaddingBottom() + getBottomVerticalOffset(true);
    }

    /**
     * @inheritDoc
     *
     * @see #setFirstBaselineToTopHeight(int)
     * @see #setLastBaselineToBottomHeight(int)
     */
    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        if (left != getPaddingLeft()
                || right != getPaddingRight()
                || top != getPaddingTop()
                ||  bottom != getPaddingBottom()) {
            nullLayouts();
        }

        // the super call will requestLayout()
        super.setPadding(left, top, right, bottom);
        invalidate();
    }

    /**
     * @inheritDoc
     *
     * @see #setFirstBaselineToTopHeight(int)
     * @see #setLastBaselineToBottomHeight(int)
     */
    @Override
    public void setPaddingRelative(int start, int top, int end, int bottom) {
        if (start != getPaddingStart()
                || end != getPaddingEnd()
                || top != getPaddingTop()
                || bottom != getPaddingBottom()) {
            nullLayouts();
        }

        // the super call will requestLayout()
        super.setPaddingRelative(start, top, end, bottom);
        invalidate();
    }

    /**
     * Updates the top padding of the TextView so that {@code firstBaselineToTopHeight} is
     * equal to the distance between the firt text baseline and the top of this TextView.
     * <strong>Note</strong> that if {@code FontMetrics.top} or {@code FontMetrics.ascent} was
     * already greater than {@code firstBaselineToTopHeight}, the top padding is not updated.
     *
     * @param firstBaselineToTopHeight distance between first baseline to top of the container
     *      in pixels
     *
     * @see #getFirstBaselineToTopHeight()
     * @see #setPadding(int, int, int, int)
     * @see #setPaddingRelative(int, int, int, int)
     *
     * @attr ref android.R.styleable#TextView_firstBaselineToTopHeight
     */
    public void setFirstBaselineToTopHeight(@Px @IntRange(from = 0) int firstBaselineToTopHeight) {

        final Paint.FontMetricsInt fontMetrics = getPaint().getFontMetricsInt();
        final int fontMetricsTop;
        if (getIncludeFontPadding()) {
            fontMetricsTop = fontMetrics.top;
        } else {
            fontMetricsTop = fontMetrics.ascent;
        }

        // TODO: Decide if we want to ignore density ratio (i.e. when the user changes font size
        // in settings). At the moment, we don't.

        if (firstBaselineToTopHeight > Math.abs(fontMetricsTop)) {
            final int paddingTop = firstBaselineToTopHeight - (-fontMetricsTop);
            setPadding(getPaddingLeft(), paddingTop, getPaddingRight(), getPaddingBottom());
        }
    }

    /**
     * Updates the bottom padding of the TextView so that {@code lastBaselineToBottomHeight} is
     * equal to the distance between the last text baseline and the bottom of this TextView.
     * <strong>Note</strong> that if {@code FontMetrics.bottom} or {@code FontMetrics.descent} was
     * already greater than {@code lastBaselineToBottomHeight}, the bottom padding is not updated.
     *
     * @param lastBaselineToBottomHeight distance between last baseline to bottom of the container
     *      in pixels
     *
     * @see #getLastBaselineToBottomHeight()
     * @see #setPadding(int, int, int, int)
     * @see #setPaddingRelative(int, int, int, int)
     *
     * @attr ref android.R.styleable#TextView_lastBaselineToBottomHeight
     */
    public void setLastBaselineToBottomHeight(
            @Px @IntRange(from = 0) int lastBaselineToBottomHeight) {

        final Paint.FontMetricsInt fontMetrics = getPaint().getFontMetricsInt();
        final int fontMetricsBottom;
        if (getIncludeFontPadding()) {
            fontMetricsBottom = fontMetrics.bottom;
        } else {
            fontMetricsBottom = fontMetrics.descent;
        }

        // TODO: Decide if we want to ignore density ratio (i.e. when the user changes font size
        // in settings). At the moment, we don't.

        if (lastBaselineToBottomHeight > Math.abs(fontMetricsBottom)) {
            final int paddingBottom = lastBaselineToBottomHeight - fontMetricsBottom;
            setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), paddingBottom);
        }
    }

    /**
     * Set of attributes that can be defined in a Text Appearance. This is used to simplify the code
     * that reads these attributes in the constructor and in {@link #setTextAppearance}.
     */
    private static class TextAppearanceAttributes {
        int mTextColorHighlight = 0;
        ColorStateList mTextColor = null;
        ColorStateList mTextColorHint = null;
        ColorStateList mTextColorLink = null;
        int mTextSize = 0;
        String mFontFamily = null;
        Typeface mFontTypeface = null;
        boolean mFontFamilyExplicit = false;
        int mTypefaceIndex = -1;
        int mStyleIndex = -1;
        int mFontWeight = -1;
        boolean mAllCaps = false;
        int mShadowColor = 0;
        float mShadowDx = 0, mShadowDy = 0, mShadowRadius = 0;
        boolean mHasElegant = false;
        boolean mElegant = false;
        boolean mHasFallbackLineSpacing = false;
        boolean mFallbackLineSpacing = false;
        boolean mHasLetterSpacing = false;
        float mLetterSpacing = 0;
        String mFontFeatureSettings = null;

        @Override
        public String toString() {
            return "TextAppearanceAttributes {\n"
                    + "    mTextColorHighlight:" + mTextColorHighlight + "\n"
                    + "    mTextColor:" + mTextColor + "\n"
                    + "    mTextColorHint:" + mTextColorHint + "\n"
                    + "    mTextColorLink:" + mTextColorLink + "\n"
                    + "    mTextSize:" + mTextSize + "\n"
                    + "    mFontFamily:" + mFontFamily + "\n"
                    + "    mFontTypeface:" + mFontTypeface + "\n"
                    + "    mFontFamilyExplicit:" + mFontFamilyExplicit + "\n"
                    + "    mTypefaceIndex:" + mTypefaceIndex + "\n"
                    + "    mStyleIndex:" + mStyleIndex + "\n"
                    + "    mFontWeight:" + mFontWeight + "\n"
                    + "    mAllCaps:" + mAllCaps + "\n"
                    + "    mShadowColor:" + mShadowColor + "\n"
                    + "    mShadowDx:" + mShadowDx + "\n"
                    + "    mShadowDy:" + mShadowDy + "\n"
                    + "    mShadowRadius:" + mShadowRadius + "\n"
                    + "    mHasElegant:" + mHasElegant + "\n"
                    + "    mElegant:" + mElegant + "\n"
                    + "    mHasFallbackLineSpacing:" + mHasFallbackLineSpacing + "\n"
                    + "    mFallbackLineSpacing:" + mFallbackLineSpacing + "\n"
                    + "    mHasLetterSpacing:" + mHasLetterSpacing + "\n"
                    + "    mLetterSpacing:" + mLetterSpacing + "\n"
                    + "    mFontFeatureSettings:" + mFontFeatureSettings + "\n"
                    + "}";
        }
    }

    // Maps styleable attributes that exist both in TextView style and TextAppearance.
    private static final SparseIntArray sAppearanceValues = new SparseIntArray();
    static {
        sAppearanceValues.put(R.styleable.CustomEditTextView_android_textColorHighlight,
                R.styleable.TextAppearance_android_textColorHighlight);
        sAppearanceValues.put(R.styleable.CustomEditTextView_android_textColor,
                R.styleable.TextAppearance_android_textColor);
        sAppearanceValues.put(R.styleable.CustomEditTextView_android_textColorHint,
                R.styleable.TextAppearance_android_textColorHint);
        sAppearanceValues.put(R.styleable.CustomEditTextView_android_textColorLink,
                R.styleable.TextAppearance_android_textColorLink);
        sAppearanceValues.put(R.styleable.CustomEditTextView_android_textSize,
                R.styleable.TextAppearance_android_textSize);
        sAppearanceValues.put(R.styleable.CustomEditTextView_android_typeface,
                R.styleable.TextAppearance_android_typeface);
        sAppearanceValues.put(R.styleable.CustomEditTextView_android_fontFamily,
                R.styleable.TextAppearance_android_fontFamily);
        sAppearanceValues.put(R.styleable.CustomEditTextView_android_textStyle,
                R.styleable.TextAppearance_android_textStyle);
        sAppearanceValues.put(R.styleable.CustomEditTextView_android_textFontWeight,
                R.styleable.TextAppearance_android_textFontWeight);
        sAppearanceValues.put(R.styleable.CustomEditTextView_android_textAllCaps,
                R.styleable.TextAppearance_android_textAllCaps);
        sAppearanceValues.put(R.styleable.CustomEditTextView_android_shadowColor,
                R.styleable.TextAppearance_android_shadowColor);
        sAppearanceValues.put(R.styleable.CustomEditTextView_android_shadowDx,
                R.styleable.TextAppearance_android_shadowDx);
        sAppearanceValues.put(R.styleable.CustomEditTextView_android_shadowDy,
                R.styleable.TextAppearance_android_shadowDy);
        sAppearanceValues.put(R.styleable.CustomEditTextView_android_shadowRadius,
                R.styleable.TextAppearance_android_shadowRadius);
        sAppearanceValues.put(R.styleable.CustomEditTextView_android_elegantTextHeight,
                R.styleable.TextAppearance_android_elegantTextHeight);
        sAppearanceValues.put(R.styleable.CustomEditTextView_android_fallbackLineSpacing,
                R.styleable.TextAppearance_android_fallbackLineSpacing);
        sAppearanceValues.put(R.styleable.CustomEditTextView_android_letterSpacing,
                R.styleable.TextAppearance_android_letterSpacing);
        sAppearanceValues.put(R.styleable.CustomEditTextView_android_fontFeatureSettings,
                R.styleable.TextAppearance_android_fontFeatureSettings);
    }

    /**
     * Read the Text Appearance attributes from a given TypedArray and set its values to the given
     * set. If the TypedArray contains a value that was already set in the given attributes, that
     * will be overriden.
     *
     * @param context The Context to be used
     * @param appearance The TypedArray to read properties from
     * @param attributes the TextAppearanceAttributes to fill in
     * @param styleArray Whether the given TypedArray is a style or a TextAppearance. This defines
     *                   what attribute indexes will be used to read the properties.
     */
    private void readTextAppearance(Context context, TypedArray appearance,
                                    TextAppearanceAttributes attributes, boolean styleArray) {
        final int n = appearance.getIndexCount();
        for (int i = 0; i < n; i++) {
            final int attr = appearance.getIndex(i);
            int index = attr;
            // Translate style array index ids to TextAppearance ids.
            if (styleArray) {
                index = sAppearanceValues.get(attr, -1);
                if (index == -1) {
                    // This value is not part of a Text Appearance and should be ignored.
                    continue;
                }
            }

            if (index == R.styleable.TextAppearance_android_textColorHighlight) {
                attributes.mTextColorHighlight =
                        appearance.getColor(attr, attributes.mTextColorHighlight);
            } else if (index == R.styleable.TextAppearance_android_textColor) {
                attributes.mTextColor = appearance.getColorStateList(attr);
            } else if (index == R.styleable.TextAppearance_android_textColorHint) {
                attributes.mTextColorHint = appearance.getColorStateList(attr);
            } else if (index == R.styleable.TextAppearance_android_textColorLink) {
                attributes.mTextColorLink = appearance.getColorStateList(attr);
            } else if (index == R.styleable.TextAppearance_android_textSize) {
                attributes.mTextSize =
                        appearance.getDimensionPixelSize(attr, attributes.mTextSize);
            } else if (index == R.styleable.TextAppearance_android_typeface) {
                attributes.mTypefaceIndex = appearance.getInt(attr, attributes.mTypefaceIndex);
                if (attributes.mTypefaceIndex != -1 && !attributes.mFontFamilyExplicit) {
                    attributes.mFontFamily = null;
                }
            } else if (index == R.styleable.TextAppearance_android_fontFamily) {
                if (!context.isRestricted()/* && context.canLoadUnsafeResources()*/) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            attributes.mFontTypeface = appearance.getFont(attr);
                        } else {
                            //TODO: (EW) handle?
                        }
                    } catch (UnsupportedOperationException | Resources.NotFoundException e) {
                        // Expected if it is not a font resource.
                    }
                }
                if (attributes.mFontTypeface == null) {
                    attributes.mFontFamily = appearance.getString(attr);
                }
                attributes.mFontFamilyExplicit = true;
            } else if (index == R.styleable.TextAppearance_android_textStyle) {
                attributes.mStyleIndex = appearance.getInt(attr, attributes.mStyleIndex);
            } else if (index == R.styleable.TextAppearance_android_textFontWeight) {
                // API 28
                attributes.mFontWeight = appearance.getInt(attr, attributes.mFontWeight);
            } else if (index == R.styleable.TextAppearance_android_textAllCaps) {
                attributes.mAllCaps = appearance.getBoolean(attr, attributes.mAllCaps);
            } else if (index == R.styleable.TextAppearance_android_shadowColor) {
                attributes.mShadowColor = appearance.getInt(attr, attributes.mShadowColor);
            } else if (index == R.styleable.TextAppearance_android_shadowDx) {
                attributes.mShadowDx = appearance.getFloat(attr, attributes.mShadowDx);
            } else if (index == R.styleable.TextAppearance_android_shadowDy) {
                attributes.mShadowDy = appearance.getFloat(attr, attributes.mShadowDy);
            } else if (index == R.styleable.TextAppearance_android_shadowRadius) {
                attributes.mShadowRadius = appearance.getFloat(attr, attributes.mShadowRadius);
            } else if (index == R.styleable.TextAppearance_android_elegantTextHeight) {
                attributes.mHasElegant = true;
                attributes.mElegant = appearance.getBoolean(attr, attributes.mElegant);
            } else if (index == R.styleable.TextAppearance_android_fallbackLineSpacing) {
                attributes.mHasFallbackLineSpacing = true;
                attributes.mFallbackLineSpacing = appearance.getBoolean(attr,
                        attributes.mFallbackLineSpacing);
            } else if (index == R.styleable.TextAppearance_android_letterSpacing) {
                attributes.mHasLetterSpacing = true;
                attributes.mLetterSpacing =
                        appearance.getFloat(attr, attributes.mLetterSpacing);
            } else if (index == R.styleable.TextAppearance_android_fontFeatureSettings) {
                attributes.mFontFeatureSettings = appearance.getString(attr);
            }
        }
    }

    private void applyTextAppearance(TextAppearanceAttributes attributes) {
        if (attributes.mTextColor != null) {
            setTextColor(attributes.mTextColor);
        }

        if (attributes.mTextColorHint != null) {
            setHintTextColor(attributes.mTextColorHint);
        }

        if (attributes.mTextColorLink != null) {
            setLinkTextColor(attributes.mTextColorLink);
        }

        if (attributes.mTextColorHighlight != 0) {
            setHighlightColor(attributes.mTextColorHighlight);
        }

        if (attributes.mTextSize != 0) {
            setRawTextSize(attributes.mTextSize, true /* shouldRequestLayout */);
        }

        if (attributes.mTypefaceIndex != -1 && !attributes.mFontFamilyExplicit) {
            attributes.mFontFamily = null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setTypefaceFromAttrs(attributes.mFontTypeface, attributes.mFontFamily,
                    attributes.mTypefaceIndex, attributes.mStyleIndex, attributes.mFontWeight);
        } else {
            setTypefaceFromAttrs(attributes.mFontTypeface, attributes.mFontFamily,
                    attributes.mTypefaceIndex, attributes.mStyleIndex);
        }

        if (attributes.mShadowColor != 0) {
            setShadowLayer(attributes.mShadowRadius, attributes.mShadowDx, attributes.mShadowDy,
                    attributes.mShadowColor);
        }

        if (attributes.mAllCaps) {
//            setTransformationMethod(new AllCapsTransformationMethod(getContext()));
        }

        if (attributes.mHasElegant) {
            setElegantTextHeight(attributes.mElegant);
        }

        if (attributes.mHasFallbackLineSpacing) {
            setFallbackLineSpacing(attributes.mFallbackLineSpacing);
        }

        if (attributes.mHasLetterSpacing) {
            setLetterSpacing(attributes.mLetterSpacing);
        }

        if (attributes.mFontFeatureSettings != null) {
            setFontFeatureSettings(attributes.mFontFeatureSettings);
        }
    }

    /**
     * Get the default primary {@link Locale} of the text in this TextView. This will always be
     * the first member of {@link #getTextLocales()}.
     * @return the default primary {@link Locale} of the text in this TextView.
     */
    @NonNull
    public Locale getTextLocale() {
        return mTextPaint.getTextLocale();
    }

    /**
     * Get the default {@link LocaleList} of the text in this TextView.
     * @return the default {@link LocaleList} of the text in this TextView.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    @NonNull @Size(min = 1)
    public LocaleList getTextLocales() {
        return mTextPaint.getTextLocales();
    }

    private void setRawTextSize(float size, boolean shouldRequestLayout) {
        if (size != mTextPaint.getTextSize()) {
            mTextPaint.setTextSize(size);

            if (shouldRequestLayout && mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * Gets the extent by which text should be stretched horizontally.
     * This will usually be 1.0.
     * @return The horizontal scale factor.
     */
    public float getTextScaleX() {
        return mTextPaint.getTextScaleX();
    }

    /**
     * Sets the horizontal scale factor for text. The default value
     * is 1.0. Values greater than 1.0 stretch the text wider.
     * Values less than 1.0 make the text narrower. By default, this value is 1.0.
     * @param size The horizontal scale factor.
     * @attr ref android.R.styleable#TextView_textScaleX
     */
    public void setTextScaleX(float size) {
        if (size != mTextPaint.getTextScaleX()) {
            mUserSetTextScaleX = true;
            mTextPaint.setTextScaleX(size);

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * Sets the typeface and style in which the text should be displayed.
     * Note that not all Typeface families actually have bold and italic
     * variants, so you may need to use
     * {@link #setTypeface(Typeface, int)} to get the appearance
     * that you actually want.
     *
     * @see #getTypeface()
     *
     * @attr ref android.R.styleable#TextView_fontFamily
     * @attr ref android.R.styleable#TextView_typeface
     * @attr ref android.R.styleable#TextView_textStyle
     */
    public void setTypeface(@Nullable Typeface tf) {
        if (mTextPaint.getTypeface() != tf) {
            mTextPaint.setTypeface(tf);

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * Set the TextView's elegant height metrics flag. This setting selects font
     * variants that have not been compacted to fit Latin-based vertical
     * metrics, and also increases top and bottom bounds to provide more space.
     *
     * @param elegant set the paint's elegant metrics flag.
     *
     * @see #isElegantTextHeight()
     * @see Paint#isElegantTextHeight()
     *
     * @attr ref android.R.styleable#TextView_elegantTextHeight
     */
    public void setElegantTextHeight(boolean elegant) {
        if (elegant != mTextPaint.isElegantTextHeight()) {
            mTextPaint.setElegantTextHeight(elegant);
            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * Set whether to respect the ascent and descent of the fallback fonts that are used in
     * displaying the text (which is needed to avoid text from consecutive lines running into
     * each other). If set, fallback fonts that end up getting used can increase the ascent
     * and descent of the lines that they are used on.
     * <p/>
     * It is required to be true if text could be in languages like Burmese or Tibetan where text
     * is typically much taller or deeper than Latin text.
     *
     * @param enabled whether to expand linespacing based on fallback fonts, {@code true} by default
     *
     * @see StaticLayout.Builder#setUseLineSpacingFromFallbacks(boolean)
     *
     * @attr ref android.R.styleable#TextView_fallbackLineSpacing
     */
    public void setFallbackLineSpacing(boolean enabled) {
        if (mUseFallbackLineSpacing != enabled) {
            mUseFallbackLineSpacing = enabled;
            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * Sets text letter-spacing in em units.  Typical values
     * for slight expansion will be around 0.05.  Negative values tighten text.
     *
     * @see #getLetterSpacing()
     * @see Paint#getLetterSpacing
     *
     * @param letterSpacing A text letter-space value in ems.
     * @attr ref android.R.styleable#TextView_letterSpacing
     */
    public void setLetterSpacing(float letterSpacing) {
        if (letterSpacing != mTextPaint.getLetterSpacing()) {
            mTextPaint.setLetterSpacing(letterSpacing);

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * Sets font feature settings. The format is the same as the CSS
     * font-feature-settings attribute:
     * <a href="https://www.w3.org/TR/css-fonts-3/#font-feature-settings-prop">
     *     https://www.w3.org/TR/css-fonts-3/#font-feature-settings-prop</a>
     *
     * @param fontFeatureSettings font feature settings represented as CSS compatible string
     *
     * @see #getFontFeatureSettings()
     * @see Paint#getFontFeatureSettings() Paint.getFontFeatureSettings()
     *
     * @attr ref android.R.styleable#TextView_fontFeatureSettings
     */
    public void setFontFeatureSettings(@Nullable String fontFeatureSettings) {
        if (fontFeatureSettings != mTextPaint.getFontFeatureSettings()) {
            mTextPaint.setFontFeatureSettings(fontFeatureSettings);

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * Sets the text color.
     *
     * @see #setTextColor(int)
     * @see #getTextColors()
     * @see #setHintTextColor(ColorStateList)
     * @see #setLinkTextColor(ColorStateList)
     *
     * @attr ref android.R.styleable#TextView_textColor
     */
    public void setTextColor(ColorStateList colors) {
        if (colors == null) {
            throw new NullPointerException();
        }

        mTextColor = colors;
        updateTextColors();
    }

    /**
     * Sets the color used to display the selection highlight.
     *
     * @attr ref android.R.styleable#TextView_textColorHighlight
     */
    public void setHighlightColor(@ColorInt int color) {
        if (mHighlightColor != color) {
            mHighlightColor = color;
            invalidate();
        }
    }

    /**
     * Returns whether the soft input method will be made visible when this
     * TextView gets focused. The default is true.
     */
    public final boolean getShowSoftInputOnFocus() {
        // When there is no Editor, return default true value
        return mEditor == null || mEditor.mShowSoftInputOnFocus;
    }

    /**
     * Gives the text a shadow of the specified blur radius and color, the specified
     * distance from its drawn position.
     * <p>
     * The text shadow produced does not interact with the properties on view
     * that are responsible for real time shadows,
     * {@link View#getElevation() elevation} and
     * {@link View#getTranslationZ() translationZ}.
     *
     * @see Paint#setShadowLayer(float, float, float, int)
     *
     * @attr ref android.R.styleable#TextView_shadowColor
     * @attr ref android.R.styleable#TextView_shadowDx
     * @attr ref android.R.styleable#TextView_shadowDy
     * @attr ref android.R.styleable#TextView_shadowRadius
     */
    public void setShadowLayer(float radius, float dx, float dy, int color) {
        mTextPaint.setShadowLayer(radius, dx, dy, color);

        mShadowRadius = radius;
        mShadowDx = dx;
        mShadowDy = dy;
        mShadowColor = color;

        // Will change text clip region
        if (mEditor != null) {
            mEditor.invalidateHandlesAndActionMode();
        }
        invalidate();
    }

    /**
     * Gets the {@link TextPaint} used for the text.
     * Use this only to consult the Paint's properties and not to change them.
     * @return The base paint used for the text.
     */
    public TextPaint getPaint() {
        return mTextPaint;
    }

    /**
     * Sets the color of the hint text.
     *
     * @see #getHintTextColors()
     * @see #setHintTextColor(int)
     * @see #setTextColor(ColorStateList)
     * @see #setLinkTextColor(ColorStateList)
     *
     * @attr ref android.R.styleable#TextView_textColorHint
     */
    public final void setHintTextColor(ColorStateList colors) {
        mHintTextColor = colors;
        updateTextColors();
    }

    /**
     * Sets the color of links in the text.
     *
     * @see #setLinkTextColor(int)
     * @see #getLinkTextColors()
     * @see #setTextColor(ColorStateList)
     * @see #setHintTextColor(ColorStateList)
     *
     * @attr ref android.R.styleable#TextView_textColorLink
     */
    public final void setLinkTextColor(ColorStateList colors) {
        mLinkTextColor = colors;
        updateTextColors();
    }

    /**
     * Sets the horizontal alignment of the text and the
     * vertical gravity that will be used when there is extra space
     * in the TextView beyond what is required for the text itself.
     *
     * @see android.view.Gravity
     * @attr ref android.R.styleable#TextView_gravity
     */
    public void setGravity(int gravity) {
        if ((gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == 0) {
            gravity |= Gravity.START;
        }
        if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == 0) {
            gravity |= Gravity.TOP;
        }

        boolean newLayout = false;

        if ((gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)
                != (mGravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)) {
            newLayout = true;
        }

        if (gravity != mGravity) {
            invalidate();
        }

        mGravity = gravity;

        if (mLayout != null && newLayout) {
            // XXX this is heavy-handed because no actual content changes.
            int want = mLayout.getWidth();
            int hintWant = mHintLayout == null ? 0 : mHintLayout.getWidth();

            makeNewLayout(want, hintWant, /*UNKNOWN_BORING, UNKNOWN_BORING,
                    getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight(),*/ true);
        }
    }

    /**
     * Returns the horizontal and vertical alignment of this TextView.
     *
     * @see android.view.Gravity
     * @attr ref android.R.styleable#TextView_gravity
     */
    public int getGravity() {
        return mGravity;
    }

    /**
     * Sets whether the text should be allowed to be wider than the
     * View is.  If false, it will be wrapped to the width of the View.
     *
     * @attr ref android.R.styleable#TextView_scrollHorizontally
     */
    public void setHorizontallyScrolling(boolean whether) {
        if (mHorizontallyScrolling != whether) {
            mHorizontallyScrolling = whether;

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * Returns whether the text is allowed to be wider than the View.
     * If false, the text will be wrapped to the width of the View.
     *
     * @attr ref android.R.styleable#TextView_scrollHorizontally
     * @hide
     */
    public boolean getHorizontallyScrolling() {
        return mHorizontallyScrolling;
    }

    /**
     * Sets the height of the TextView to be at least {@code minLines} tall.
     * <p>
     * This value is used for height calculation if LayoutParams does not force TextView to have an
     * exact height. Setting this value overrides other previous minimum height configurations such
     * as {@link #setMinHeight(int)} or {@link #setHeight(int)}. {@link #setSingleLine()} will set
     * this value to 1.
     *
     * @param minLines the minimum height of TextView in terms of number of lines
     *
     * @see #getMinLines()
     * @see #setLines(int)
     *
     * @attr ref android.R.styleable#TextView_minLines
     */
    public void setMinLines(int minLines) {
        mMinimum = minLines;
        mMinMode = LINES;

        requestLayout();
        invalidate();
    }

    /**
     * Sets the height of the TextView to be at least {@code minPixels} tall.
     * <p>
     * This value is used for height calculation if LayoutParams does not force TextView to have an
     * exact height. Setting this value overrides previous minimum height configurations such as
     * {@link #setMinLines(int)} or {@link #setLines(int)}.
     * <p>
     * The value given here is different than {@link #setMinimumHeight(int)}. Between
     * {@code minHeight} and the value set in {@link #setMinimumHeight(int)}, the greater one is
     * used to decide the final height.
     *
     * @param minPixels the minimum height of TextView in terms of pixels
     *
     * @see #getMinHeight()
     * @see #setHeight(int)
     *
     * @attr ref android.R.styleable#TextView_minHeight
     */
    public void setMinHeight(int minPixels) {
        mMinimum = minPixels;
        mMinMode = PIXELS;

        requestLayout();
        invalidate();
    }

    /**
     * Sets the height of the TextView to be at most {@code maxLines} tall.
     * <p>
     * This value is used for height calculation if LayoutParams does not force TextView to have an
     * exact height. Setting this value overrides previous maximum height configurations such as
     * {@link #setMaxHeight(int)} or {@link #setLines(int)}.
     *
     * @param maxLines the maximum height of TextView in terms of number of lines
     *
     * @see #getMaxLines()
     * @see #setLines(int)
     *
     * @attr ref android.R.styleable#TextView_maxLines
     */
    public void setMaxLines(int maxLines) {
        mMaximum = maxLines;
        mMaxMode = LINES;

        requestLayout();
        invalidate();
    }

    /**
     * Sets the height of the TextView to be at most {@code maxPixels} tall.
     * <p>
     * This value is used for height calculation if LayoutParams does not force TextView to have an
     * exact height. Setting this value overrides previous maximum height configurations such as
     * {@link #setMaxLines(int)} or {@link #setLines(int)}.
     *
     * @param maxPixels the maximum height of TextView in terms of pixels
     *
     * @see #getMaxHeight()
     * @see #setHeight(int)
     *
     * @attr ref android.R.styleable#TextView_maxHeight
     */
    public void setMaxHeight(int maxPixels) {
        mMaximum = maxPixels;
        mMaxMode = PIXELS;

        requestLayout();
        invalidate();
    }

    /**
     * Sets the height of the TextView to be exactly {@code lines} tall.
     * <p>
     * This value is used for height calculation if LayoutParams does not force TextView to have an
     * exact height. Setting this value overrides previous minimum/maximum height configurations
     * such as {@link #setMinLines(int)} or {@link #setMaxLines(int)}. {@link #setSingleLine()} will
     * set this value to 1.
     *
     * @param lines the exact height of the TextView in terms of lines
     *
     * @see #setHeight(int)
     *
     * @attr ref android.R.styleable#TextView_lines
     */
    public void setLines(int lines) {
        mMaximum = mMinimum = lines;
        mMaxMode = mMinMode = LINES;

        requestLayout();
        invalidate();
    }

    /**
     * Sets the height of the TextView to be exactly <code>pixels</code> tall.
     * <p>
     * This value is used for height calculation if LayoutParams does not force TextView to have an
     * exact height. Setting this value overrides previous minimum/maximum height configurations
     * such as {@link #setMinHeight(int)} or {@link #setMaxHeight(int)}.
     *
     * @param pixels the exact height of the TextView in terms of pixels
     *
     * @see #setLines(int)
     *
     * @attr ref android.R.styleable#TextView_height
     */
    public void setHeight(int pixels) {
        mMaximum = mMinimum = pixels;
        mMaxMode = mMinMode = PIXELS;

        requestLayout();
        invalidate();
    }

    /**
     * Sets the width of the TextView to be at least {@code minEms} wide.
     * <p>
     * This value is used for width calculation if LayoutParams does not force TextView to have an
     * exact width. Setting this value overrides previous minimum width configurations such as
     * {@link #setMinWidth(int)} or {@link #setWidth(int)}.
     *
     * @param minEms the minimum width of TextView in terms of ems
     *
     * @see #getMinEms()
     * @see #setEms(int)
     *
     * @attr ref android.R.styleable#TextView_minEms
     */
    public void setMinEms(int minEms) {
        mMinWidth = minEms;
        mMinWidthMode = EMS;

        requestLayout();
        invalidate();
    }

    /**
     * Sets the width of the TextView to be at least {@code minPixels} wide.
     * <p>
     * This value is used for width calculation if LayoutParams does not force TextView to have an
     * exact width. Setting this value overrides previous minimum width configurations such as
     * {@link #setMinEms(int)} or {@link #setEms(int)}.
     * <p>
     * The value given here is different than {@link #setMinimumWidth(int)}. Between
     * {@code minWidth} and the value set in {@link #setMinimumWidth(int)}, the greater one is used
     * to decide the final width.
     *
     * @param minPixels the minimum width of TextView in terms of pixels
     *
     * @see #getMinWidth()
     * @see #setWidth(int)
     *
     * @attr ref android.R.styleable#TextView_minWidth
     */
    public void setMinWidth(int minPixels) {
        mMinWidth = minPixels;
        mMinWidthMode = PIXELS;

        requestLayout();
        invalidate();
    }

    /**
     * Sets the width of the TextView to be at most {@code maxEms} wide.
     * <p>
     * This value is used for width calculation if LayoutParams does not force TextView to have an
     * exact width. Setting this value overrides previous maximum width configurations such as
     * {@link #setMaxWidth(int)} or {@link #setWidth(int)}.
     *
     * @param maxEms the maximum width of TextView in terms of ems
     *
     * @see #getMaxEms()
     * @see #setEms(int)
     *
     * @attr ref android.R.styleable#TextView_maxEms
     */
    public void setMaxEms(int maxEms) {
        mMaxWidth = maxEms;
        mMaxWidthMode = EMS;

        requestLayout();
        invalidate();
    }

    /**
     * Sets the width of the TextView to be at most {@code maxPixels} wide.
     * <p>
     * This value is used for width calculation if LayoutParams does not force TextView to have an
     * exact width. Setting this value overrides previous maximum width configurations such as
     * {@link #setMaxEms(int)} or {@link #setEms(int)}.
     *
     * @param maxPixels the maximum width of TextView in terms of pixels
     *
     * @see #getMaxWidth()
     * @see #setWidth(int)
     *
     * @attr ref android.R.styleable#TextView_maxWidth
     */
    public void setMaxWidth(int maxPixels) {
        mMaxWidth = maxPixels;
        mMaxWidthMode = PIXELS;

        requestLayout();
        invalidate();
    }

    /**
     * Sets the width of the TextView to be exactly {@code ems} wide.
     *
     * This value is used for width calculation if LayoutParams does not force TextView to have an
     * exact width. Setting this value overrides previous minimum/maximum configurations such as
     * {@link #setMinEms(int)} or {@link #setMaxEms(int)}.
     *
     * @param ems the exact width of the TextView in terms of ems
     *
     * @see #setWidth(int)
     *
     * @attr ref android.R.styleable#TextView_ems
     */
    public void setEms(int ems) {
        mMaxWidth = mMinWidth = ems;
        mMaxWidthMode = mMinWidthMode = EMS;

        requestLayout();
        invalidate();
    }

    /**
     * Sets the width of the TextView to be exactly {@code pixels} wide.
     * <p>
     * This value is used for width calculation if LayoutParams does not force TextView to have an
     * exact width. Setting this value overrides previous minimum/maximum width configurations
     * such as {@link #setMinWidth(int)} or {@link #setMaxWidth(int)}.
     *
     * @param pixels the exact width of the TextView in terms of pixels
     *
     * @see #setEms(int)
     *
     * @attr ref android.R.styleable#TextView_width
     */
    public void setWidth(int pixels) {
        mMaxWidth = mMinWidth = pixels;
        mMaxWidthMode = mMinWidthMode = PIXELS;

        requestLayout();
        invalidate();
    }

    /**
     * Sets line spacing for this TextView.  Each line other than the last line will have its height
     * multiplied by {@code mult} and have {@code add} added to it.
     *
     * @param add The value in pixels that should be added to each line other than the last line.
     *            This will be applied after the multiplier
     * @param mult The value by which each line height other than the last line will be multiplied
     *             by
     *
     * @attr ref android.R.styleable#TextView_lineSpacingExtra
     * @attr ref android.R.styleable#TextView_lineSpacingMultiplier
     */
    public void setLineSpacing(float add, float mult) {
        if (mSpacingAdd != add || mSpacingMult != mult) {
            mSpacingAdd = add;
            mSpacingMult = mult;

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * Sets an explicit line height for this TextView. This is equivalent to the vertical distance
     * between subsequent baselines in the TextView.
     *
     * @param lineHeight the line height in pixels
     *
     * @see #setLineSpacing(float, float)
     * @see #getLineSpacing()
     *
     * @attr ref android.R.styleable#TextView_lineHeight
     */
    public void setLineHeight(@Px @IntRange(from = 0) int lineHeight) {

        final int fontHeight = getPaint().getFontMetricsInt(null);
        // Make sure we don't setLineSpacing if it's not needed to avoid unnecessary redraw.
        if (lineHeight != fontHeight) {
            // Set lineSpacingExtra by the difference of lineSpacing with lineHeight
            setLineSpacing(lineHeight - fontHeight, 1f);
        }
    }

    private void updateTextColors() {
        boolean inval = false;
        final int[] drawableState = getDrawableState();
        int color = mTextColor.getColorForState(drawableState, 0);
        if (color != mCurTextColor) {
            mCurTextColor = color;
            inval = true;
        }
        if (mLinkTextColor != null) {
            color = mLinkTextColor.getColorForState(drawableState, 0);
            if (color != mTextPaint.linkColor) {
                mTextPaint.linkColor = color;
                inval = true;
            }
        }
        if (mHintTextColor != null) {
            color = mHintTextColor.getColorForState(drawableState, 0);
            if (color != mCurHintTextColor) {
                mCurHintTextColor = color;
                if (mText.length() == 0) {
                    inval = true;
                }
            }
        }
        if (inval) {
            // Text needs to be redrawn with the new color
            invalidate();
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    /**
     * Sets the text to be displayed. TextView <em>does not</em> accept
     * HTML-like formatting, which you can do with text strings in XML resource files.
     * To style your strings, attach android.text.style.* objects to a
     * {@link android.text.SpannableString}, or see the
     * <a href="{@docRoot}guide/topics/resources/available-resources.html#stringresources">
     * Available Resource Types</a> documentation for an example of setting
     * formatted text in the XML resource file.
     * <p/>
     * When required, TextView will use {@link android.text.Spannable.Factory} to create final or
     * intermediate {@link Spannable Spannables}. Likewise it will use
     * {@link android.text.Editable.Factory} to create final or intermediate
     * {@link Editable Editables}.
     *
     * If the passed text is a {@link PrecomputedText} but the parameters used to create the
     * PrecomputedText mismatches with this TextView, IllegalArgumentException is thrown. To ensure
     * the parameters match, you can call {@link TextView#setTextMetricsParams} before calling this.
     *
     * @param text text to be displayed
     *
     * @attr ref android.R.styleable#TextView_text
     * @throws IllegalArgumentException if the passed text is a {@link PrecomputedText} but the
     *                                  parameters used to create the PrecomputedText mismatches
     *                                  with this TextView.
     */
//    public final void setText(CharSequence text) {
//        setText(text/*, mBufferType*/);
//    }
    /**
     * Sets the text to be displayed and the {@link android.widget.TextView.BufferType}.
     * <p/>
     * When required, TextView will use {@link android.text.Spannable.Factory} to create final or
     * intermediate {@link Spannable Spannables}. Likewise it will use
     * {@link android.text.Editable.Factory} to create final or intermediate
     * {@link Editable Editables}.
     *
     * @param text text to be displayed
     * @param type a {@link android.widget.TextView.BufferType} which defines whether the text is
     *              stored as a static text, styleable/spannable text, or editable text
     *
     * @see #setText(CharSequence)
     * @see android.widget.TextView.BufferType
     * @see #setSpannableFactory(Spannable.Factory)
     * @see #setEditableFactory(Editable.Factory)
     *
     * @attr ref android.R.styleable#TextView_text
     * @attr ref android.R.styleable#TextView_bufferType
     */
    public void setText(CharSequence text/*, TextView.BufferType type*/) {
        setText(text/*, type*/, true, 0);

//        if (mCharWrapper != null) {
//            mCharWrapper.mChars = null;
//        }
    }

    private void setText(CharSequence text/*, TextView.BufferType type*/,
                         boolean notifyBefore, int oldlen) {
        if (text == null) {
            text = "";
        }

        if (!mUserSetTextScaleX) mTextPaint.setTextScaleX(1.0f);

        int n = mFilters.length;
        for (int i = 0; i < n; i++) {
            CharSequence out = mFilters[i].filter(text, 0, text.length(), EMPTY_SPANNED, 0, 0);
            if (out != null) {
                text = out;
            }
        }

        Editable t = Editable.Factory.getInstance().newEditable(text);
        text = t;

        setFilters(t, mFilters);

        InputMethodManager imm = getInputMethodManager();
        if (imm != null) {
            imm.restartInput(this);
        }

        setTextInternal(t);

        if (mTransformation == null) {
            mTransformed = text;
        } else {
            mTransformed = mTransformation.getTransformation(text, this);
        }
        if (mTransformed == null) {
            // Should not happen if the transformation method follows the non-null postcondition.
            mTransformed = "";
        }

        final int textLength = text.length();

        if (text instanceof Spannable) {
            Spannable sp = (Spannable) text;

            // Remove any ChangeWatchers that might have come from other TextViews.
            final ChangeWatcher[] watchers = sp.getSpans(0, sp.length(), ChangeWatcher.class);
            final int count = watchers.length;
            for (int i = 0; i < count; i++) {
                sp.removeSpan(watchers[i]);
            }

            if (mChangeWatcher == null) mChangeWatcher = new ChangeWatcher();

            sp.setSpan(mChangeWatcher, 0, textLength, Spanned.SPAN_INCLUSIVE_INCLUSIVE |
                    (CHANGE_WATCHER_PRIORITY << Spanned.SPAN_PRIORITY_SHIFT));

            if (mEditor != null) mEditor.addSpanWatchers(sp);

            if (mTransformation != null) {
                sp.setSpan(mTransformation, 0, textLength, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            }

            if (mMovement != null) {
                mMovement.initialize(this, (Spannable) text);

                /*
                 * Initializing the movement method will have set the
                 * selection, so reset mSelectionMoved to keep that from
                 * interfering with the normal on-focus selection-setting.
                 */
                if (mEditor != null) mEditor.mSelectionMoved = false;
            }
        }

        if (mLayout != null) {
//            checkForRelayout();
        }

        sendOnTextChanged(text, 0, /*oldlen*/0, textLength);
        onTextChanged(text, 0, /*oldlen*/0, textLength);

//        notifyViewAccessibilityStateChangedIfNeeded(AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT);
//
//        if (needEditableForNotification) {
//            sendAfterTextChanged((Editable) text);
//        } else {
//            notifyListeningManagersAfterTextChanged();
//        }

        // SelectionModifierCursorController depends on textCanBeSelected, which depends on text
        if (mEditor != null) mEditor.prepareCursorControllers();
    }

    /**
     * Sets the text to be displayed when the text of the TextView is empty.
     * Null means to use the normal empty text. The hint does not currently
     * participate in determining the size of the view.
     *
     * @attr ref android.R.styleable#TextView_hint
     */
    public final void setHint(CharSequence hint) {
        setHintInternal(hint);

//        if (mEditor != null && isInputMethodTarget()) {
//            mEditor.reportExtractedText();
//        }
    }

    private void setHintInternal(CharSequence hint) {
        mHint = TextUtils.stringOrSpannedString(hint);

        if (mLayout != null) {
//            checkForRelayout();
        }

        if (mText.length() == 0) {
            invalidate();
        }

        // Invalidate display list if hint is currently used
        if (mEditor != null && mText.length() == 0 && mHint != null) {
//            mEditor.invalidateTextDisplayList();
        }
    }

    /**
     * Returns the hint that is displayed when the text of the TextView
     * is empty.
     *
     * @attr ref android.R.styleable#TextView_hint
     */
    public CharSequence getHint() {
        return mHint;
    }

    /**
     * Returns if the text is constrained to a single horizontally scrolling line ignoring new
     * line characters instead of letting it wrap onto multiple lines.
     *
     * @attr ref android.R.styleable#TextView_singleLine
     */
    boolean isSingleLine() {
        return mSingleLine;
    }

    private static boolean isMultilineInputType(int type) {
        return (type & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE))
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
    }

    /**
     * Removes the suggestion spans.
     */
    CharSequence removeSuggestionSpans(CharSequence text) {
        if (text instanceof Spanned) {
            Spannable spannable;
            if (text instanceof Spannable) {
                spannable = (Spannable) text;
            } else {
                spannable = Spannable.Factory.getInstance().newSpannable(text);
            }

            SuggestionSpan[] spans = spannable.getSpans(0, text.length(), SuggestionSpan.class);
            if (spans.length == 0) {
                return text;
            } else {
                text = spannable;
            }

            for (int i = 0; i < spans.length; i++) {
                spannable.removeSpan(spans[i]);
            }
        }
        return text;
    }

    /**
     * Set the type of the content with a constant as defined for {@link EditorInfo#inputType}. This
     * will take care of changing the key listener, by calling {@link #setKeyListener(KeyListener)},
     * to match the given content type.  If the given content type is {@link EditorInfo#TYPE_NULL}
     * then a soft keyboard will not be displayed for this text view.
     *
     * Note that the maximum number of displayed lines (see {@link #setMaxLines(int)}) will be
     * modified if you change the {@link EditorInfo#TYPE_TEXT_FLAG_MULTI_LINE} flag of the input
     * type.
     *
     * @see #getInputType()
     * @see #setRawInputType(int)
     * @see InputType
     * @attr ref android.R.styleable#TextView_inputType
     */
    public void setInputType(int type) {
        final boolean wasPassword = isPasswordInputType(getInputType());
        final boolean wasVisiblePassword = isVisiblePasswordInputType(getInputType());
        setInputType(type, false);
        final boolean isPassword = isPasswordInputType(type);
        final boolean isVisiblePassword = isVisiblePasswordInputType(type);
        boolean forceUpdate = false;
        if (isPassword) {
            setTransformationMethod(PasswordTransformationMethod.getInstance());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setTypefaceFromAttrs(null/* fontTypeface */, null /* fontFamily */, MONOSPACE,
                        Typeface.NORMAL, -1 /* weight, not specifeid */);
            } else {
                //TODO: (EW) handle
            }
        } else if (isVisiblePassword) {
            if (mTransformation == PasswordTransformationMethod.getInstance()) {
                forceUpdate = true;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setTypefaceFromAttrs(null/* fontTypeface */, null /* fontFamily */, MONOSPACE,
                        Typeface.NORMAL, -1 /* weight, not specified */);
            } else {
                //TODO: (EW) handle
            }
        } else if (wasPassword || wasVisiblePassword) {
            // not in password mode, clean up typeface and transformation
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setTypefaceFromAttrs(null/* fontTypeface */, null /* fontFamily */,
                        DEFAULT_TYPEFACE /* typeface index */, Typeface.NORMAL,
                        -1 /* weight, not specified */);
            } else {
                //TODO: (EW) handle
            }
            if (mTransformation == PasswordTransformationMethod.getInstance()) {
                forceUpdate = true;
            }
        }

        boolean singleLine = !isMultilineInputType(type);

        // We need to update the single line mode if it has changed or we
        // were previously in password mode.
        if (mSingleLine != singleLine || forceUpdate) {
            // Change single line mode, but only change the transformation if
            // we are not in password mode.
            applySingleLine(singleLine, !isPassword, true);
        }

//        if (!isSuggestionsEnabled()) {
//            setTextInternal(removeSuggestionSpans(mText));
//        }

        InputMethodManager imm = getInputMethodManager();
        if (imm != null) imm.restartInput(this);
    }

    /**
     * It would be better to rely on the input type for everything. A password inputType should have
     * a password transformation. We should hence use isPasswordInputType instead of this method.
     *
     * We should:
     * - Call setInputType in setKeyListener instead of changing the input type directly (which
     * would install the correct transformation).
     * - Refuse the installation of a non-password transformation in setTransformation if the input
     * type is password.
     *
     * However, this is like this for legacy reasons and we cannot break existing apps. This method
     * is useful since it matches what the user can see (obfuscated text or not).
     *
     * @return true if the current transformation method is of the password type.
     */
    boolean hasPasswordTransformationMethod() {
        return mTransformation instanceof PasswordTransformationMethod;
    }

    static boolean isPasswordInputType(int inputType) {
        final int variation =
                inputType & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_MASK_VARIATION);
        return variation
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD)
                || variation
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                || variation
                == (EditorInfo.TYPE_CLASS_NUMBER | EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD);
    }

    private static boolean isVisiblePasswordInputType(int inputType) {
        final int variation =
                inputType & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_MASK_VARIATION);
        return variation
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
    }

    /**
     * Directly change the content type integer of the text view, without
     * modifying any other state.
     * @see #setInputType(int)
     * @see android.text.InputType
     * @attr ref android.R.styleable#TextView_inputType
     */
    public void setRawInputType(int type) {
        if (type == InputType.TYPE_NULL && mEditor == null) return; //TYPE_NULL is the default value
        mEditor.mInputType = type;
    }

    /**
     * @return {@code null} if the key listener should use pre-O (locale-independent). Otherwise
     *         a {@code Locale} object that can be used to customize key various listeners.
     * @see DateKeyListener#getInstance(Locale)
     * @see DateTimeKeyListener#getInstance(Locale)
     * @see DigitsKeyListener#getInstance(Locale)
     * @see TimeKeyListener#getInstance(Locale)
     */
    @Nullable
    private Locale getCustomLocaleForKeyListenerOrNull() {
        if (!mUseInternationalizedInput) {
            // If the application does not target O, stick to the previous behavior.
            return null;
        }
        final LocaleList locales = getImeHintLocales();
        if (locales == null) {
            // If the application does not explicitly specify IME hint locale, also stick to the
            // previous behavior.
            return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return locales.get(0);
        } else {
            //TODO: (EW) handle
            return null;
        }
    }

    private void setInputType(int type, boolean direct) {
        final int cls = type & EditorInfo.TYPE_MASK_CLASS;
        KeyListener input;
        if (cls == EditorInfo.TYPE_CLASS_TEXT) {
            boolean autotext = (type & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) != 0;
            TextKeyListener.Capitalize cap;
            if ((type & EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) {
                cap = TextKeyListener.Capitalize.CHARACTERS;
            } else if ((type & EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS) != 0) {
                cap = TextKeyListener.Capitalize.WORDS;
            } else if ((type & EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0) {
                cap = TextKeyListener.Capitalize.SENTENCES;
            } else {
                cap = TextKeyListener.Capitalize.NONE;
            }
            input = TextKeyListener.getInstance(autotext, cap);
        } else if (cls == EditorInfo.TYPE_CLASS_NUMBER) {
            final Locale locale = getCustomLocaleForKeyListenerOrNull();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                input = DigitsKeyListener.getInstance(
                        locale,
                        (type & EditorInfo.TYPE_NUMBER_FLAG_SIGNED) != 0,
                        (type & EditorInfo.TYPE_NUMBER_FLAG_DECIMAL) != 0);
            } else {
                //TODO: (EW) handle
                input = null;
            }
            if (locale != null) {
                // Override type, if necessary for i18n.
                int newType = input.getInputType();
                final int newClass = newType & EditorInfo.TYPE_MASK_CLASS;
                if (newClass != EditorInfo.TYPE_CLASS_NUMBER) {
                    // The class is different from the original class. So we need to override
                    // 'type'. But we want to keep the password flag if it's there.
                    if ((type & EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD) != 0) {
                        newType |= EditorInfo.TYPE_TEXT_VARIATION_PASSWORD;
                    }
                    type = newType;
                }
            }
        } else if (cls == EditorInfo.TYPE_CLASS_DATETIME) {
            final Locale locale = getCustomLocaleForKeyListenerOrNull();
            switch (type & EditorInfo.TYPE_MASK_VARIATION) {
                case EditorInfo.TYPE_DATETIME_VARIATION_DATE:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        input = DateKeyListener.getInstance(locale);
                    } else {
                        //TODO: (EW) handle
                        input = null;
                    }
                    break;
                case EditorInfo.TYPE_DATETIME_VARIATION_TIME:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        input = TimeKeyListener.getInstance(locale);
                    } else {
                        //TODO: (EW) handle
                        input = null;
                    }
                    break;
                default:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        input = DateTimeKeyListener.getInstance(locale);
                    } else {
                        //TODO: (EW) handle
                        input = null;
                    }
                    break;
            }
            if (mUseInternationalizedInput) {
                type = input.getInputType(); // Override type, if necessary for i18n.
            }
        } else if (cls == EditorInfo.TYPE_CLASS_PHONE) {
            input = DialerKeyListener.getInstance();
        } else {
            input = TextKeyListener.getInstance();
        }
        setRawInputType(type);
        mListenerChanged = false;
        if (direct) {
            mEditor.mKeyListener = input;
        } else {
            setKeyListenerOnly(input);
        }
    }

    /**
     * Get the type of the editable content.
     *
     * @see #setInputType(int)
     * @see android.text.InputType
     */
    public int getInputType() {
        return mEditor == null ? EditorInfo.TYPE_NULL : mEditor.mInputType;
    }

    /**
     * @return The current languages list "hint". {@code null} when no "hint" is available.
     * @see #setImeHintLocales(LocaleList)
     * @see android.view.inputmethod.EditorInfo#hintLocales
     */
    @Nullable
    public LocaleList getImeHintLocales() {
        if (mEditor == null) {
            return null;
        }
        if (mEditor.mInputContentType == null) {
            return null;
        }
        return mEditor.mInputContentType.imeHintLocales;
    }

    /**
     * Sets the list of input filters that will be used if the buffer is
     * Editable. Has no effect otherwise.
     *
     * @attr ref android.R.styleable#TextView_maxLength
     */
    public void setFilters(InputFilter[] filters) {
        if (filters == null) {
            throw new IllegalArgumentException();
        }

        mFilters = filters;

        if (mText instanceof Editable) {
            setFilters((Editable) mText, filters);
        }
    }

    /**
     * Sets the list of input filters on the specified Editable,
     * and includes mInput in the list if it is an InputFilter.
     */
    private void setFilters(Editable e, InputFilter[] filters) {
        if (mEditor != null) {
//            final boolean undoFilter = mEditor.mUndoInputFilter != null;
            final boolean keyFilter = mEditor.mKeyListener instanceof InputFilter;
            int num = 0;
//            if (undoFilter) num++;
            if (keyFilter) num++;
            if (num > 0) {
                InputFilter[] nf = new InputFilter[filters.length + num];

                System.arraycopy(filters, 0, nf, 0, filters.length);
                num = 0;
//                if (undoFilter) {
//                    nf[filters.length] = mEditor.mUndoInputFilter;
//                    num++;
//                }
                if (keyFilter) {
                    nf[filters.length + num] = (InputFilter) mEditor.mKeyListener;
                }

                e.setFilters(nf);
                return;
            }
        }
        e.setFilters(filters);
    }

    /////////////////////////////////////////////////////////////////////////

    private int getBoxHeight(Layout l) {
//        Insets opticalInsets = isLayoutModeOptical(mParent) ? getOpticalInsets() : Insets.NONE;
        int padding = (l == mHintLayout)
                ? getCompoundPaddingTop() + getCompoundPaddingBottom()
                : getExtendedPaddingTop() + getExtendedPaddingBottom();
        // tested creating an EditText and compared getTotalPaddingTop and getExtendedPaddingTop, which were the same, so this optical inset doesn't seem to apply
        return getMeasuredHeight() - padding/* + opticalInsets.top + opticalInsets.bottom*/;
    }

    int getVerticalOffset(boolean forceNormal) {
        int voffset = 0;
        final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;

        Layout l = mLayout;
        if (!forceNormal && mText.length() == 0 && mHintLayout != null) {
            l = mHintLayout;
        }

        if (gravity != Gravity.TOP) {
            int boxht = getBoxHeight(l);
            int textht = l.getHeight();

            if (textht < boxht) {
                if (gravity == Gravity.BOTTOM) {
                    voffset = boxht - textht;
                } else { // (gravity == Gravity.CENTER_VERTICAL)
                    voffset = (boxht - textht) >> 1;
                }
            }
        }
        return voffset;
    }

    private int getBottomVerticalOffset(boolean forceNormal) {
        int voffset = 0;
        final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;

        Layout l = mLayout;
        if (!forceNormal && mText.length() == 0 && mHintLayout != null) {
            l = mHintLayout;
        }

        if (gravity != Gravity.BOTTOM) {
            int boxht = getBoxHeight(l);
            int textht = l.getHeight();

            if (textht < boxht) {
                if (gravity == Gravity.TOP)
                    voffset = boxht - textht;
                else // (gravity == Gravity.CENTER_VERTICAL)
                    voffset = (boxht - textht) >> 1;
            }
        }
        return voffset;
    }

    void invalidateCursorPath() {
        if (mHighlightPathBogus) {
            invalidateCursor();
        } else {
            final int horizontalPadding = getCompoundPaddingLeft();
            final int verticalPadding = getExtendedPaddingTop() + getVerticalOffset(true);

            if (mEditor.mDrawableForCursor == null) {
                synchronized (TEMP_RECTF) {
                    /*
                     * The reason for this concern about the thickness of the
                     * cursor and doing the floor/ceil on the coordinates is that
                     * some EditTexts (notably textfields in the Browser) have
                     * anti-aliased text where not all the characters are
                     * necessarily at integer-multiple locations.  This should
                     * make sure the entire cursor gets invalidated instead of
                     * sometimes missing half a pixel.
                     */
                    float thick = (float) Math.ceil(mTextPaint.getStrokeWidth());
                    if (thick < 1.0f) {
                        thick = 1.0f;
                    }

                    thick /= 2.0f;

                    // mHighlightPath is guaranteed to be non null at that point.
                    mHighlightPath.computeBounds(TEMP_RECTF, false);

                    invalidate((int) Math.floor(horizontalPadding + TEMP_RECTF.left - thick),
                            (int) Math.floor(verticalPadding + TEMP_RECTF.top - thick),
                            (int) Math.ceil(horizontalPadding + TEMP_RECTF.right + thick),
                            (int) Math.ceil(verticalPadding + TEMP_RECTF.bottom + thick));
                }
            } else {
                final Rect bounds = mEditor.mDrawableForCursor.getBounds();
                invalidate(bounds.left + horizontalPadding, bounds.top + verticalPadding,
                        bounds.right + horizontalPadding, bounds.bottom + verticalPadding);
            }
        }
    }

    void invalidateCursor() {
        int where = getSelectionEnd();

        invalidateCursor(where, where, where);
    }

    private void invalidateCursor(int a, int b, int c) {
        if (a >= 0 || b >= 0 || c >= 0) {
            int start = Math.min(Math.min(a, b), c);
            int end = Math.max(Math.max(a, b), c);
            invalidateRegion(start, end, true /* Also invalidates blinking cursor */);
        }
    }

    /**
     * Invalidates the region of text enclosed between the start and end text offsets.
     */
    void invalidateRegion(int start, int end, boolean invalidateCursor) {
        if (mLayout == null) {
            invalidate();
        } else {
            int lineStart = mLayout.getLineForOffset(start);
            int top = mLayout.getLineTop(lineStart);

            // This is ridiculous, but the descent from the line above
            // can hang down into the line we really want to redraw,
            // so we have to invalidate part of the line above to make
            // sure everything that needs to be redrawn really is.
            // (But not the whole line above, because that would cause
            // the same problem with the descenders on the line above it!)
            if (lineStart > 0) {
                top -= mLayout.getLineDescent(lineStart - 1);
            }

            int lineEnd;

            if (start == end) {
                lineEnd = lineStart;
            } else {
                lineEnd = mLayout.getLineForOffset(end);
            }

            int bottom = mLayout.getLineBottom(lineEnd);

            // mEditor can be null in case selection is set programmatically.
            if (invalidateCursor && mEditor != null && mEditor.mDrawableForCursor != null) {
                final Rect bounds = mEditor.mDrawableForCursor.getBounds();
                top = Math.min(top, bounds.top);
                bottom = Math.max(bottom, bounds.bottom);
            }

            final int compoundPaddingLeft = getCompoundPaddingLeft();
            final int verticalPadding = getExtendedPaddingTop() + getVerticalOffset(true);

            int left, right;
            if (lineStart == lineEnd && !invalidateCursor) {
                left = (int) mLayout.getPrimaryHorizontal(start);
                right = (int) (mLayout.getPrimaryHorizontal(end) + 1.0);
                left += compoundPaddingLeft;
                right += compoundPaddingLeft;
            } else {
                // Rectangle bounding box when the region spans several lines
                left = compoundPaddingLeft;
                right = getWidth() - getCompoundPaddingRight();
            }

            invalidate(getScrollX() + left, verticalPadding + top,
                    getScrollX() + right, verticalPadding + bottom);
        }
    }

    private void registerForPreDraw() {
        if (!mPreDrawRegistered) {
            getViewTreeObserver().addOnPreDrawListener(this);
            mPreDrawRegistered = true;
        }
    }

    private void unregisterForPreDraw() {
        getViewTreeObserver().removeOnPreDrawListener(this);
        mPreDrawRegistered = false;
        mPreDrawListenerDetached = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onPreDraw() {
        if (mLayout == null) {
            assumeLayout();
        }

        if (mMovement != null) {
            /* This code also provides auto-scrolling when a cursor is moved using a
             * CursorController (insertion point or selection limits).
             * For selection, ensure start or end is visible depending on controller's state.
             */
            int curs = getSelectionEnd();
            // Do not create the controller if it is not already created.
//            if (mEditor != null && mEditor.mSelectionModifierCursorController != null
//                    && mEditor.mSelectionModifierCursorController.isSelectionStartDragged()) {
//                curs = getSelectionStart();
//            }

            /*
             * TODO: This should really only keep the end in view if
             * it already was before the text changed.  I'm not sure
             * of a good way to tell from here if it was.
             */
            if (curs < 0 && (mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
                curs = mText.length();
            }

            if (curs >= 0) {
                bringPointIntoView(curs);
            }
        } else {
            bringTextIntoView();
        }

        // This has to be checked here since:
        // - onFocusChanged cannot start it when focus is given to a view with selected text (after
        //   a screen rotation) since layout is not yet initialized at that point.
//        if (mEditor != null && mEditor.mCreatedWithASelection) {
//            mEditor.refreshTextActionMode();
//            mEditor.mCreatedWithASelection = false;
//        }

        unregisterForPreDraw();

        return true;
    }

    @Override
    protected boolean isPaddingOffsetRequired() {
        return mShadowRadius != 0/* || mDrawables != null*/;
    }

    @Override
    protected int getLeftPaddingOffset() {
        return getCompoundPaddingLeft() - getPaddingLeft()
                + (int) Math.min(0, mShadowDx - mShadowRadius);
    }

    @Override
    protected int getTopPaddingOffset() {
        return (int) Math.min(0, mShadowDy - mShadowRadius);
    }

    @Override
    protected int getBottomPaddingOffset() {
        return (int) Math.max(0, mShadowDy + mShadowRadius);
    }

    @Override
    protected int getRightPaddingOffset() {
        return -(getCompoundPaddingRight() - getPaddingRight())
                + (int) Math.max(0, mShadowDx + mShadowRadius);
    }

    /**
     *
     * Returns the state of the {@code textIsSelectable} flag (See
     * { @link #setTextIsSelectable setTextIsSelectable()}). Although you have to set this flag
     * to allow users to select and copy text in a non-editable TextView, the content of an
     * {@link android.widget.EditText} can always be selected, independently of the value of this flag.
     * <p>
     *
     * @return True if the text displayed in this TextView can be selected by the user.
     *
     * @attr ref android.R.styleable#TextView_textIsSelectable
     */
    public boolean isTextSelectable() {
        return mEditor == null ? false : mEditor.mTextIsSelectable;
    }

    private Path getUpdatedHighlightPath() {
        Path highlight = null;
        Paint highlightPaint = mHighlightPaint;

        final int selStart = getSelectionStart();
        final int selEnd = getSelectionEnd();
        if (mMovement != null && (isFocused() || isPressed()) && selStart >= 0) {
            if (selStart == selEnd) {
                if (mEditor != null && mEditor.shouldRenderCursor()) {
                    if (mHighlightPathBogus) {
                        if (mHighlightPath == null) mHighlightPath = new Path();
                        mHighlightPath.reset();
                        mLayout.getCursorPath(selStart, mHighlightPath, mText);
                        mEditor.updateCursorPosition();
                        mHighlightPathBogus = false;
                    }

                    // XXX should pass to skin instead of drawing directly
                    highlightPaint.setColor(mCurTextColor);
                    highlightPaint.setStyle(Paint.Style.STROKE);
                    highlight = mHighlightPath;
                }
            } else {
                if (mHighlightPathBogus) {
                    if (mHighlightPath == null) mHighlightPath = new Path();
                    mHighlightPath.reset();
                    mLayout.getSelectionPath(selStart, selEnd, mHighlightPath);
                    mHighlightPathBogus = false;
                }

                // XXX should pass to skin instead of drawing directly
                highlightPaint.setColor(mHighlightColor);
                highlightPaint.setStyle(Paint.Style.FILL);

                highlight = mHighlightPath;
            }
        }
        return highlight;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final int compoundPaddingLeft = getCompoundPaddingLeft();
        final int compoundPaddingTop = getCompoundPaddingTop();
        final int compoundPaddingRight = getCompoundPaddingRight();
        final int compoundPaddingBottom = getCompoundPaddingBottom();

        if (mLayout == null) {
            assumeLayout();
        }

        int color = mCurTextColor;
        Layout layout = mLayout;

        if (mHint != null && mText.length() == 0) {
//            if (mHintTextColor != null) {
            color = mCurHintTextColor;
//            }

            layout = mHintLayout;
        }
        mTextPaint.setColor(color);

        canvas.save();
        /*  Would be faster if we didn't have to do this. Can we chop the
            (displayable) text so that we don't need to do this ever?
        */

        final int scrollX = getScrollX();
        final int scrollY = getScrollY();
//        Log.w(TAG, "onDraw: scrollX=" + scrollX + ", scrollY=" + scrollY);
        final int right = getRight();
        final int left = getLeft();
        final int bottom = getBottom();
        final int top = getTop();

        int extendedPaddingTop = getExtendedPaddingTop();
        int extendedPaddingBottom = getExtendedPaddingBottom();

        final int vspace = bottom - top - compoundPaddingBottom - compoundPaddingTop;
        final int maxScrollY = mLayout.getHeight() - vspace;

        float clipLeft = compoundPaddingLeft + scrollX;
        float clipTop = (scrollY == 0) ? 0 : extendedPaddingTop + scrollY;
        float clipRight = right - left - compoundPaddingRight + scrollX;
        float clipBottom = bottom - top + scrollY
                - ((scrollY == maxScrollY) ? 0 : extendedPaddingBottom);

        if (mShadowRadius != 0) {
            clipLeft += Math.min(0, mShadowDx - mShadowRadius);
            clipRight += Math.max(0, mShadowDx + mShadowRadius);

            clipTop += Math.min(0, mShadowDy - mShadowRadius);
            clipBottom += Math.max(0, mShadowDy + mShadowRadius);
        }
//        final boolean isLayoutRtl = isLayoutRtl();
//        final int offset = getHorizontalOffsetForDrawables();
//        final int leftOffset = isLayoutRtl ? 0 : offset;
//        final int rightOffset = isLayoutRtl ? offset : 0;

        canvas.clipRect(clipLeft, clipTop, clipRight, clipBottom);

        int voffsetText = 0;
        int voffsetCursor = 0;

        // translate in by our padding
        /* shortcircuit calling getVerticaOffset() */
        if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
            voffsetText = getVerticalOffset(false);
            voffsetCursor = getVerticalOffset(true);
        }
        canvas.translate(compoundPaddingLeft, extendedPaddingTop + voffsetText);

        final int layoutDirection = getLayoutDirection();
        final int absoluteGravity = Gravity.getAbsoluteGravity(mGravity, layoutDirection);

        final int cursorOffsetVertical = voffsetCursor - voffsetText;

        Path highlight = getUpdatedHighlightPath();
        if (mEditor != null) {
//            final int selectionStart = getSelectionStart();
//            final int selectionEnd = getSelectionEnd();
//            if (highlight != null && selectionStart == selectionEnd && mEditor.mDrawableForCursor != null) {
//                drawCursor(canvas, cursorOffsetVertical);
//                // Rely on the drawable entirely, do not draw the cursor line.
//                // Has to be done after the IMM related code above which relies on the highlight.
//                highlight = null;
//            }
            mEditor.onDraw(canvas, layout, highlight, mHighlightPaint, cursorOffsetVertical);
        } else {
            layout.draw(canvas, highlight, mHighlightPaint, cursorOffsetVertical);
        }

        canvas.restore();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        final int which = doKeyDown(keyCode, event, null);
        if (which == KEY_EVENT_NOT_HANDLED) {
            return super.onKeyDown(keyCode, event);
        }

        return true;
    }

    /**
     * Returns true if pressing ENTER in this field advances focus instead
     * of inserting the character.  This is true mostly in single-line fields,
     * but also in mail addresses and subjects which will display on multiple
     * lines but where it doesn't make sense to insert newlines.
     */
    private boolean shouldAdvanceFocusOnEnter() {
        if (getKeyListener() == null) {
            return false;
        }

        if (mSingleLine) {
            return true;
        }

        if (mEditor != null
                && (mEditor.mInputType & EditorInfo.TYPE_MASK_CLASS)
                == EditorInfo.TYPE_CLASS_TEXT) {
            int variation = mEditor.mInputType & EditorInfo.TYPE_MASK_VARIATION;
            if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    || variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_SUBJECT) {
                return true;
            }
        }

        return false;
    }

    private boolean isDirectionalNavigationKey(int keyCode) {
        switch(keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true;
        }
        return false;
    }

    private int doKeyDown(int keyCode, KeyEvent event, KeyEvent otherEvent) {
        if (!isEnabled()) {
            return KEY_EVENT_NOT_HANDLED;
        }

        // If this is the initial keydown, we don't want to prevent a movement away from this view.
        // While this shouldn't be necessary because any time we're preventing default movement we
        // should be restricting the focus to remain within this view, thus we'll also receive
        // the key up event, occasionally key up events will get dropped and we don't want to
        // prevent the user from traversing out of this on the next key down.
        if (event.getRepeatCount() == 0 && !KeyEvent.isModifierKey(keyCode)) {
            mPreventDefaultMovement = false;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (event.hasNoModifiers()) {
                    // When mInputContentType is set, we know that we are
                    // running in a "modern" cupcake environment, so don't need
                    // to worry about the application trying to capture
                    // enter key events.
//                    if (mEditor != null && mEditor.mInputContentType != null) {
//                        // If there is an action listener, given them a
//                        // chance to consume the event.
//                        if (mEditor.mInputContentType.onEditorActionListener != null
//                                && mEditor.mInputContentType.onEditorActionListener.onEditorAction(
//                                this, EditorInfo.IME_NULL, event)) {
//                            mEditor.mInputContentType.enterDown = true;
//                            // We are consuming the enter key for them.
//                            return KEY_EVENT_HANDLED;
//                        }
//                    }

                    // If our editor should move focus when enter is pressed, or
                    // this is a generated event from an IME action button, then
                    // don't let it be inserted into the text.
                    if ((event.getFlags() & KeyEvent.FLAG_EDITOR_ACTION) != 0
                            || shouldAdvanceFocusOnEnter()) {
                        if (hasOnClickListeners()) {
                            return KEY_EVENT_NOT_HANDLED;
                        }
                        return KEY_EVENT_HANDLED;
                    }
                }
                break;

            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (event.hasNoModifiers()) {
                    if (shouldAdvanceFocusOnEnter()) {
                        return KEY_EVENT_NOT_HANDLED;
                    }
                }
                break;

            case KeyEvent.KEYCODE_TAB:
                if (event.hasNoModifiers() || event.hasModifiers(KeyEvent.META_SHIFT_ON)) {
                    // Tab is used to move focus.
                    return KEY_EVENT_NOT_HANDLED;
                }
                break;

//            // Has to be done on key down (and not on key up) to correctly be intercepted.
//            case KeyEvent.KEYCODE_BACK:
//                if (mEditor != null && mEditor.getTextActionMode() != null) {
//                    stopTextActionMode();
//                    return KEY_EVENT_HANDLED;
//                }
//                break;
//
//            case KeyEvent.KEYCODE_CUT:
//                if (event.hasNoModifiers() && canCut()) {
//                    if (onTextContextMenuItem(ID_CUT)) {
//                        return KEY_EVENT_HANDLED;
//                    }
//                }
//                break;
//
//            case KeyEvent.KEYCODE_COPY:
//                if (event.hasNoModifiers() && canCopy()) {
//                    if (onTextContextMenuItem(ID_COPY)) {
//                        return KEY_EVENT_HANDLED;
//                    }
//                }
//                break;
//
//            case KeyEvent.KEYCODE_PASTE:
//                if (event.hasNoModifiers() && canPaste()) {
//                    if (onTextContextMenuItem(ID_PASTE)) {
//                        return KEY_EVENT_HANDLED;
//                    }
//                }
//                break;
//
//            case KeyEvent.KEYCODE_FORWARD_DEL:
//                if (event.hasModifiers(KeyEvent.META_SHIFT_ON) && canCut()) {
//                    if (onTextContextMenuItem(ID_CUT)) {
//                        return KEY_EVENT_HANDLED;
//                    }
//                }
//                break;
//
//            case KeyEvent.KEYCODE_INSERT:
//                if (event.hasModifiers(KeyEvent.META_CTRL_ON) && canCopy()) {
//                    if (onTextContextMenuItem(ID_COPY)) {
//                        return KEY_EVENT_HANDLED;
//                    }
//                } else if (event.hasModifiers(KeyEvent.META_SHIFT_ON) && canPaste()) {
//                    if (onTextContextMenuItem(ID_PASTE)) {
//                        return KEY_EVENT_HANDLED;
//                    }
//                }
//                break;
        }

        if (/*mEditor != null &&*/ mEditor.mKeyListener != null) {
            boolean doDown = true;
            if (otherEvent != null) {
                try {
                    beginBatchEdit();
                    final boolean handled = mEditor.mKeyListener.onKeyOther(this, (Editable) mText,
                            otherEvent);
//                    hideErrorIfUnchanged();
                    doDown = false;
                    if (handled) {
                        return KEY_EVENT_HANDLED;
                    }
                } catch (AbstractMethodError e) {
                    // onKeyOther was added after 1.0, so if it isn't
                    // implemented we need to try to dispatch as a regular down.
                } finally {
                    endBatchEdit();
                }
            }

            if (doDown) {
                beginBatchEdit();
                final boolean handled = mEditor.mKeyListener.onKeyDown(this, (Editable) mText,
                        keyCode, event);
                endBatchEdit();
//                hideErrorIfUnchanged();
                if (handled) return KEY_DOWN_HANDLED_BY_KEY_LISTENER;
            }
        }

        // bug 650865: sometimes we get a key event before a layout.
        // don't try to move around if we don't know the layout.

        if (mMovement != null && mLayout != null) {
            boolean doDown = true;
            if (otherEvent != null) {
                try {
                    boolean handled = mMovement.onKeyOther(this, mSpannable, otherEvent);
                    doDown = false;
                    if (handled) {
                        return KEY_EVENT_HANDLED;
                    }
                } catch (AbstractMethodError e) {
                    // onKeyOther was added after 1.0, so if it isn't
                    // implemented we need to try to dispatch as a regular down.
                }
            }
            if (doDown) {
                if (mMovement.onKeyDown(this, mSpannable, keyCode, event)) {
                    if (event.getRepeatCount() == 0 && !KeyEvent.isModifierKey(keyCode)) {
                        mPreventDefaultMovement = true;
                    }
                    return KEY_DOWN_HANDLED_BY_MOVEMENT_METHOD;
                }
            }
            // Consume arrows from keyboard devices to prevent focus leaving the editor.
            // DPAD/JOY devices (Gamepads, TV remotes) often lack a TAB key so allow those
            // to move focus with arrows.
            if (event.getSource() == InputDevice.SOURCE_KEYBOARD
                    && isDirectionalNavigationKey(keyCode)) {
                return KEY_EVENT_HANDLED;
            }
        }

        return mPreventDefaultMovement && !KeyEvent.isModifierKey(keyCode)
                ? KEY_EVENT_HANDLED : KEY_EVENT_NOT_HANDLED;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (!isEnabled()) {
            return super.onKeyUp(keyCode, event);
        }

        if (!KeyEvent.isModifierKey(keyCode)) {
            mPreventDefaultMovement = false;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (event.hasNoModifiers()) {
                    /*
                     * If there is a click listener, just call through to
                     * super, which will invoke it.
                     *
                     * If there isn't a click listener, try to show the soft
                     * input method.  (It will also
                     * call performClick(), but that won't do anything in
                     * this case.)
                     */
                    if (!hasOnClickListeners()) {
                        if (mMovement != null && mText instanceof Editable
                                && mLayout != null && onCheckIsTextEditor()) {
                            InputMethodManager imm = getInputMethodManager();
                            viewClicked(imm);
                            if (imm != null/* && getShowSoftInputOnFocus()*/) {
                                imm.showSoftInput(this, 0);
                            }
                        }
                    }
                }
                return super.onKeyUp(keyCode, event);

            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (event.hasNoModifiers()) {
//                    if (mEditor != null && mEditor.mInputContentType != null
//                            && mEditor.mInputContentType.onEditorActionListener != null
//                            && mEditor.mInputContentType.enterDown) {
//                        mEditor.mInputContentType.enterDown = false;
//                        if (mEditor.mInputContentType.onEditorActionListener.onEditorAction(
//                                this, EditorInfo.IME_NULL, event)) {
//                            return true;
//                        }
//                    }

                    if ((event.getFlags() & KeyEvent.FLAG_EDITOR_ACTION) != 0
                            || shouldAdvanceFocusOnEnter()) {
                        /*
                         * If there is a click listener, just call through to
                         * super, which will invoke it.
                         *
                         * If there isn't a click listener, try to advance focus,
                         * but still call through to super, which will reset the
                         * pressed state and longpress state.  (It will also
                         * call performClick(), but that won't do anything in
                         * this case.)
                         */
                        if (!hasOnClickListeners()) {
                            View v = focusSearch(FOCUS_DOWN);

                            if (v != null) {
                                if (!v.requestFocus(FOCUS_DOWN)) {
                                    throw new IllegalStateException("focus search returned a view "
                                            + "that wasn't able to take focus!");
                                }

                                /*
                                 * Return true because we handled the key; super
                                 * will return false because there was no click
                                 * listener.
                                 */
                                super.onKeyUp(keyCode, event);
                                return true;
                            } else if ((event.getFlags()
                                    & KeyEvent.FLAG_EDITOR_ACTION) != 0) {
                                // No target for next focus, but make sure the IME
                                // if this came from it.
                                InputMethodManager imm = getInputMethodManager();
                                if (imm != null && imm.isActive(this)) {
                                    imm.hideSoftInputFromWindow(getWindowToken(), 0);
                                }
                            }
                        }
                    }
                    return super.onKeyUp(keyCode, event);
                }
                break;
        }

        if (/*mEditor != null && */mEditor.mKeyListener != null) {
            if (mEditor.mKeyListener.onKeyUp(this, (Editable) mText, keyCode, event)) {
                return true;
            }
        }

        if (mMovement != null && mLayout != null) {
            if (mMovement.onKeyUp(this, mSpannable, keyCode, event)) {
                return true;
            }
        }

        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        //return mEditor != null && mEditor.mInputType != EditorInfo.TYPE_NULL;
        return true;
    }

    @Override
//    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
//        //TODO: send appropriate outAttrs, especially imeOptions
//        if (onCheckIsTextEditor() && isEnabled()) {
//            mEditor.createInputMethodStateIfNeeded();
//            outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE;
//            return new CustomInputConnection2(this);
//        }
//
//        return null;
//    }
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        if (onCheckIsTextEditor() && isEnabled()) {
            mEditor.createInputMethodStateIfNeeded();
            outAttrs.inputType = getInputType();
            if (mEditor.mInputContentType != null) {
                outAttrs.imeOptions = mEditor.mInputContentType.imeOptions;
                outAttrs.privateImeOptions = mEditor.mInputContentType.privateImeOptions;
                outAttrs.actionLabel = mEditor.mInputContentType.imeActionLabel;
                outAttrs.actionId = mEditor.mInputContentType.imeActionId;
                outAttrs.extras = mEditor.mInputContentType.extras;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    outAttrs.hintLocales = mEditor.mInputContentType.imeHintLocales;
                }
            } else {
                outAttrs.imeOptions = EditorInfo.IME_NULL;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    outAttrs.hintLocales = null;
                }
            }
            if (focusSearch(FOCUS_DOWN) != null) {
                outAttrs.imeOptions |= EditorInfo.IME_FLAG_NAVIGATE_NEXT;
            }
            if (focusSearch(FOCUS_UP) != null) {
                outAttrs.imeOptions |= EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS;
            }
            if ((outAttrs.imeOptions & EditorInfo.IME_MASK_ACTION)
                    == EditorInfo.IME_ACTION_UNSPECIFIED) {
                if ((outAttrs.imeOptions & EditorInfo.IME_FLAG_NAVIGATE_NEXT) != 0) {
                    // An action has not been set, but the enter key will move to
                    // the next focus, so set the action to that.
                    outAttrs.imeOptions |= EditorInfo.IME_ACTION_NEXT;
                } else {
                    // An action has not been set, and there is no focus to move
                    // to, so let's just supply a "done" action.
                    outAttrs.imeOptions |= EditorInfo.IME_ACTION_DONE;
                }
                if (!shouldAdvanceFocusOnEnter()) {
                    outAttrs.imeOptions |= EditorInfo.IME_FLAG_NO_ENTER_ACTION;
                }
            }
            if (isMultilineInputType(outAttrs.inputType)) {
                // Multi-line text editors should always show an enter key.
                outAttrs.imeOptions |= EditorInfo.IME_FLAG_NO_ENTER_ACTION;
            }
            outAttrs.hintText = mHint;
            if (mText instanceof Editable) {
                InputConnection ic = new CustomInputConnection2(this);
                outAttrs.initialSelStart = getSelectionStart();
                outAttrs.initialSelEnd = getSelectionEnd();
                outAttrs.initialCapsMode = ic.getCursorCapsMode(getInputType());
                return ic;
            }
        }
        return null;
    }

    @Override
    public void beginBatchEdit() {
        Log.w(TAG, "beginBatchEdit");
        mEditor.beginBatchEdit();
    }

    @Override
    public void endBatchEdit() {
        Log.w(TAG, "endBatchEdit");
        mEditor.endBatchEdit();
    }

    /**
     * Called by the framework in response to a request to begin a batch
     * of edit operations through a call to link {@link #beginBatchEdit()}.
     */
    public void onBeginBatchEdit() {
        // intentionally empty
    }

    /**
     * Called by the framework in response to a request to end a batch
     * of edit operations through a call to link {@link #endBatchEdit}.
     */
    public void onEndBatchEdit() {
        // intentionally empty
    }

    public void nullLayouts() {
        mLayout = mHintLayout = null;
        // Since it depends on the value of mLayout
        if (mEditor != null) mEditor.prepareCursorControllers();
    }

    private void assumeLayout() {
        int width = getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight();

        if (width < 1) {
            width = 0;
        }

        int physicalWidth = width;

        if (mHorizontallyScrolling) {
            width = VERY_WIDE;
        }

        makeNewLayout(width, physicalWidth, false);
    }

    private Layout.Alignment getLayoutAlignment() {
        Layout.Alignment alignment;
        switch (getTextAlignment()) {
            case TEXT_ALIGNMENT_GRAVITY:
                switch (mGravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) {
                    case Gravity.START:
                        alignment = Layout.Alignment.ALIGN_NORMAL;
                        break;
                    case Gravity.END:
                        alignment = Layout.Alignment.ALIGN_OPPOSITE;
                        break;
//                    case Gravity.LEFT:
//                        alignment = Layout.Alignment.ALIGN_LEFT;
//                        break;
//                    case Gravity.RIGHT:
//                        alignment = Layout.Alignment.ALIGN_RIGHT;
//                        break;
                    case Gravity.CENTER_HORIZONTAL:
                        alignment = Layout.Alignment.ALIGN_CENTER;
                        break;
                    default:
                        alignment = Layout.Alignment.ALIGN_NORMAL;
                        break;
                }
                break;
            case TEXT_ALIGNMENT_TEXT_START:
                alignment = Layout.Alignment.ALIGN_NORMAL;
                break;
            case TEXT_ALIGNMENT_TEXT_END:
                alignment = Layout.Alignment.ALIGN_OPPOSITE;
                break;
            case TEXT_ALIGNMENT_CENTER:
                alignment = Layout.Alignment.ALIGN_CENTER;
                break;
//            case TEXT_ALIGNMENT_VIEW_START:
//                alignment = (getLayoutDirection() == LAYOUT_DIRECTION_RTL)
//                        ? Layout.Alignment.ALIGN_RIGHT : Layout.Alignment.ALIGN_LEFT;
//                break;
//            case TEXT_ALIGNMENT_VIEW_END:
//                alignment = (getLayoutDirection() == LAYOUT_DIRECTION_RTL)
//                        ? Layout.Alignment.ALIGN_LEFT : Layout.Alignment.ALIGN_RIGHT;
//                break;
            case TEXT_ALIGNMENT_INHERIT:
                // This should never happen as we have already resolved the text alignment
                // but better safe than sorry so we just fall through
            default:
                alignment = Layout.Alignment.ALIGN_NORMAL;
                break;
        }
        return alignment;
    }

    public void makeNewLayout(int wantWidth, int hintWidth, boolean bringIntoView) {
        mHighlightPathBogus = true;

        if (wantWidth < 0) {
            wantWidth = 0;
        }
        if (hintWidth < 0) {
            hintWidth = 0;
        }

        Layout.Alignment alignment = getLayoutAlignment();
        final boolean testDirChange = mSingleLine && mLayout != null
                && (alignment == Layout.Alignment.ALIGN_NORMAL
                || alignment == Layout.Alignment.ALIGN_OPPOSITE);
        int oldDir = 0;
        if (testDirChange) oldDir = mLayout.getParagraphDirection(0);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            final DynamicLayout.Builder builder = DynamicLayout.Builder.obtain(mText, mTextPaint,
                    wantWidth)
                    .setDisplayText(mTransformed)
                    .setAlignment(alignment)
//                    .setTextDirection(mTextDir)
                    .setLineSpacing(mSpacingAdd, mSpacingMult)
                    .setIncludePad(mIncludePad)
                    .setUseLineSpacingFromFallbacks(mUseFallbackLineSpacing)
//                    .setBreakStrategy(mBreakStrategy)
//                    .setHyphenationFrequency(mHyphenationFrequency)
                    .setJustificationMode(mJustificationMode);
            mLayout = builder.build();
        } else {
//            mLayout = new DynamicLayout(mText, mTransformed, mTextPaint, wantWidth,
//                    alignment, mTextDir, mSpacingMult, mSpacingAdd, mIncludePad,
//                    mBreakStrategy, mHyphenationFrequency, mJustificationMode,
//                    getKeyListener() == null ? effectiveEllipsize : null, ellipsisWidth);
            mLayout = new DynamicLayout(mText, mTransformed, mTextPaint, wantWidth, alignment, mSpacingMult, mSpacingAdd, mIncludePad);
        }

        if (mHint != null) {

            StaticLayout.Builder builder = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                builder = StaticLayout.Builder.obtain(mHint, 0,
                        mHint.length(), mTextPaint, hintWidth)
                        .setAlignment(alignment)
//                        .setTextDirection(mTextDir)
                        .setLineSpacing(mSpacingAdd, mSpacingMult)
                        .setIncludePad(mIncludePad)
//                        .setBreakStrategy(mBreakStrategy)
//                        .setHyphenationFrequency(mHyphenationFrequency)
                        .setMaxLines(mMaxMode == LINES ? mMaximum : Integer.MAX_VALUE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder.setJustificationMode(mJustificationMode);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    builder.setUseLineSpacingFromFallbacks(mUseFallbackLineSpacing);
                }
                mHintLayout = builder.build();
            } else {
                mHintLayout = new StaticLayout(mHint, mTextPaint, hintWidth, alignment, mSpacingMult, mSpacingAdd, mIncludePad);
            }
        } else {
            mHintLayout = null;
        }

        if (bringIntoView || (testDirChange && oldDir != mLayout.getParagraphDirection(0))) {
            registerForPreDraw();
        }
        // CursorControllers need a non-null mLayout
        if (mEditor != null) mEditor.prepareCursorControllers();
    }

    /**
     * Set whether the TextView includes extra top and bottom padding to make
     * room for accents that go above the normal ascent and descent.
     * The default is true.
     *
     * @see #getIncludeFontPadding()
     *
     * @attr ref android.R.styleable#TextView_includeFontPadding
     */
    public void setIncludeFontPadding(boolean includepad) {
        if (mIncludePad != includepad) {
            mIncludePad = includepad;

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * Gets whether the TextView includes extra top and bottom padding to make
     * room for accents that go above the normal ascent and descent.
     *
     * @see #setIncludeFontPadding(boolean)
     *
     * @attr ref android.R.styleable#TextView_includeFontPadding
     */
    public boolean getIncludeFontPadding() {
        return mIncludePad;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Log.w(TAG, "onMeasure: widthMeasureSpec: spec=" + widthMeasureSpec
                + ", mode=" + MeasureSpec.getMode(widthMeasureSpec)
                + ", size=" + MeasureSpec.getSize(widthMeasureSpec)
                + ", string=" + MeasureSpec.toString(widthMeasureSpec));
        Log.w(TAG, "onMeasure: heightMeasureSpec: spec=" + heightMeasureSpec
                + ", mode=" + MeasureSpec.getMode(heightMeasureSpec)
                + ", size=" + MeasureSpec.getSize(heightMeasureSpec)
                + ", string=" + MeasureSpec.toString(heightMeasureSpec));
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        Log.w(TAG, "onMeasure: layoutParams.width=" + layoutParams.width
                + ", layoutParams.height=" + layoutParams.height);
        Log.w(TAG, "onMeasure: text=\"" + mText.toString() + "\"");


        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

//        if (mTextDir == null) {
//            mTextDir = /*getTextDirectionHeuristic()*/TextDirectionHeuristics.LTR;
//        }

        int des = -1;
        boolean fromexisting = false;

        if (widthMode == MeasureSpec.EXACTLY) {
            // Parent has told us how big to be. So be it.
            width = widthSize;
        } else {
//                    des = (int) Math.ceil(/*Layout*/HiddenLayoutInfo.getDesiredWidthWithLimit(mTransformed, 0,
//                            mTransformed.length(), mTextPaint, mTextDir, widthLimit));
            width = (int) /*FloatMath*/Math.ceil(Layout.getDesiredWidth(mTransformed, mTextPaint));

            if (mHint != null) {
                int hintWidth;

//                        hintDes = (int) Math.ceil(/*Layout*/HiddenLayoutInfo.getDesiredWidthWithLimit(mHint, 0,
//                                mHint.length(), mTextPaint, mTextDir, widthLimit));
                hintWidth = (int) /*FloatMath*/Math.ceil(Layout.getDesiredWidth(mHint, mTextPaint));

                if (hintWidth > width) {
                    width = hintWidth;
                }
            }

            width += getCompoundPaddingLeft() + getCompoundPaddingRight();

            if (mMaxWidthMode == EMS) {
                width = Math.min(width, mMaxWidth * getLineHeight());
            } else {
                width = Math.min(width, mMaxWidth);
            }

            if (mMinWidthMode == EMS) {
                width = Math.max(width, mMinWidth * getLineHeight());
            } else {
                width = Math.max(width, mMinWidth);
            }

            // Check against our minimum width
            width = Math.max(width, getSuggestedMinimumWidth());

            if (widthMode == MeasureSpec.AT_MOST) {
                width = Math.min(widthSize, width);
            }
        }

        int want = width - getCompoundPaddingLeft() - getCompoundPaddingRight();
        int unpaddedWidth = want;

        if (mHorizontallyScrolling) want = VERY_WIDE;

        int hintWant = want;
        int hintWidth = (mHintLayout == null) ? hintWant : mHintLayout.getWidth();

        if (mLayout == null) {
            makeNewLayout(want, hintWant, false);
        } else {
            final boolean layoutChanged = (mLayout.getWidth() != want) || (hintWidth != hintWant)
                    || (mLayout.getEllipsizedWidth()
                    != width - getCompoundPaddingLeft() - getCompoundPaddingRight());

//            final boolean widthChanged = (mHint == null) && (mEllipsize == null)
//                    && (want > mLayout.getWidth())
//                    && (mLayout instanceof BoringLayout);

            final boolean maximumChanged = (mMaxMode != mOldMaxMode) || (mMaximum != mOldMaximum);

            if (layoutChanged || maximumChanged) {
//                if (!maximumChanged && widthChanged) {
//                    mLayout.increaseWidthTo(want);
//                } else {
                makeNewLayout(want, hintWant, false);
//                }
            } else {
                // Nothing has changed
            }
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            // Parent has told us how big to be. So be it.
            height = heightSize;
            mDesiredHeightAtMeasure = -1;
        } else {
            height = getDesiredHeight();
            mDesiredHeightAtMeasure = height;
            Log.w(TAG, "onMeasure: desiredHeight=" + height);// 97, 160 (px)
            //30px top padding, 21px bottom padding
            //46px 1 line, 109px 2 lines -> 17px space between lines
            //should be: 85px 1 line, 148px 2 lines -> -22px space between lines
            //overall height should be: 136, 199

            if (heightMode == MeasureSpec.AT_MOST) {
                height = Math.min(height, heightSize);
            }
        }

        int unpaddedHeight = height - getCompoundPaddingTop() - getCompoundPaddingBottom();
        if (mMaxMode == LINES && mLayout.getLineCount() > mMaximum) {
            unpaddedHeight = Math.min(unpaddedHeight, mLayout.getLineTop(mMaximum));
        }

        /*
         * We didn't let makeNewLayout() register to bring the cursor into view,
         * so do it here if there is any possibility that it is needed.
         */
        if (mMovement != null
                || mLayout.getWidth() > unpaddedWidth
                || mLayout.getHeight() > unpaddedHeight) {
            registerForPreDraw();
        } else {
//            scrollTo(0, 0);
        }
//            scrollTo(50, 0);

        setMeasuredDimension(width, height);
        Log.w(TAG, "onMeasure: setMeasuredDimension: width=" + width + ", height=" + height);
    }

    private int getDesiredHeight() {
        return Math.max(
                getDesiredHeight(mLayout, true),
                getDesiredHeight(mHintLayout, /*mEllipsize != null*/false));
    }

    private int getDesiredHeight(Layout layout, boolean cap) {
        if (layout == null) {
            return 0;
        }

        /*
         * Don't cap the hint to a certain number of lines.
         * (Do cap it, though, if we have a maximum pixel height.)
         */
        int desired = layout.getHeight(/*cap*/);

        int linecount = layout.getLineCount();
        final int padding = getCompoundPaddingTop() + getCompoundPaddingBottom();
        Log.w(TAG, "getDesiredHeight: layout.getHeight=" + desired + ", padding=" + padding + ", linecount=" + linecount);
        desired += padding;

        if (mMaxMode != LINES) {
            desired = Math.min(desired, mMaximum);
        } else if (cap && linecount > mMaximum && (layout instanceof DynamicLayout
                || layout instanceof BoringLayout)) {
            desired = layout.getLineTop(mMaximum);

            desired += padding;
            linecount = mMaximum;
        }

        if (mMinMode == LINES) {
            if (linecount < mMinimum) {
                desired += getLineHeight() * (mMinimum - linecount);
            }
        } else {
            desired = Math.max(desired, mMinimum);
        }

        // Check against our minimum height
        int suggested = getSuggestedMinimumHeight();
        Log.w(TAG, "getDesiredHeight: desired=" + desired + ", suggested=" + suggested);
        desired = Math.max(desired, suggested);

        return desired;
    }

    /**
     * Check whether a change to the existing text layout requires a
     * new view layout.
     */
    private void checkForResize() {
        boolean sizeChanged = false;

        if (mLayout != null) {
            // Check if our width changed
            if (getLayoutParams().width == ViewGroup.LayoutParams.WRAP_CONTENT) {
                sizeChanged = true;
                invalidate();
            }

            // Check if our height changed
            if (getLayoutParams().height == ViewGroup.LayoutParams.WRAP_CONTENT) {
                int desiredHeight = getDesiredHeight();

                if (desiredHeight != this.getHeight()) {
                    sizeChanged = true;
                }
            } else if (getLayoutParams().height == ViewGroup.LayoutParams.MATCH_PARENT) {
                if (mDesiredHeightAtMeasure >= 0) {
                    int desiredHeight = getDesiredHeight();

                    if (desiredHeight != mDesiredHeightAtMeasure) {
                        sizeChanged = true;
                    }
                }
            }
        }

        if (sizeChanged) {
            requestLayout();
            // caller will have already invalidated
        }
    }

    private boolean isShowingHint() {
        return TextUtils.isEmpty(mText) && !TextUtils.isEmpty(mHint);
    }
    /**
     * Returns true if anything changed.
     */
    private boolean bringTextIntoView() {
        Layout layout = isShowingHint() ? mHintLayout : mLayout;
        int line = 0;
        if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
            line = layout.getLineCount() - 1;
        }

        /*Layout.*/
        Alignment a = convertAlignment(layout.getParagraphAlignment(line));
        int dir = layout.getParagraphDirection(line);
        int hspace = getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        int vspace = getBottom() - getTop() - getExtendedPaddingTop() - getExtendedPaddingBottom();
        int ht = layout.getHeight();

        int scrollx, scrolly;

        // Convert to left, center, or right alignment.
        if (a == /*Layout.*/Alignment.ALIGN_NORMAL) {
            a = dir == Layout.DIR_LEFT_TO_RIGHT
                    ? /*Layout.*/Alignment.ALIGN_LEFT : /*Layout.*/Alignment.ALIGN_RIGHT;
        } else if (a == /*Layout.*/Alignment.ALIGN_OPPOSITE) {
            a = dir == Layout.DIR_LEFT_TO_RIGHT
                    ? /*Layout.*/Alignment.ALIGN_RIGHT : /*Layout.*/Alignment.ALIGN_LEFT;
        }

        if (a == /*Layout.*/Alignment.ALIGN_CENTER) {
            /*
             * Keep centered if possible, or, if it is too wide to fit,
             * keep leading edge in view.
             */

            int left = (int) Math.floor(layout.getLineLeft(line));
            int right = (int) Math.ceil(layout.getLineRight(line));

            if (right - left < hspace) {
                scrollx = (right + left) / 2 - hspace / 2;
            } else {
                if (dir < 0) {
                    scrollx = right - hspace;
                } else {
                    scrollx = left;
                }
            }
        } else if (a == /*Layout.*/Alignment.ALIGN_RIGHT) {
            int right = (int) Math.ceil(layout.getLineRight(line));
            scrollx = right - hspace;
        } else { // a == Layout.Alignment.ALIGN_LEFT (will also be the default)
            scrollx = (int) Math.floor(layout.getLineLeft(line));
        }

        if (ht < vspace) {
            scrolly = 0;
        } else {
            if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
                scrolly = ht - vspace;
            } else {
                scrolly = 0;
            }
        }

        if (scrollx != getScrollX() || scrolly != getScrollY()) {
            scrollTo(scrollx, scrolly);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Move the point, specified by the offset, into the view if it is needed.
     * This has to be called after layout. Returns true if anything changed.
     */
    public boolean bringPointIntoView(int offset) {
        if (isLayoutRequested()) {
            mDeferScroll = offset;
            return false;
        }
        boolean changed = false;

        Layout layout = isShowingHint() ? mHintLayout : mLayout;

        if (layout == null) return changed;

        int line = layout.getLineForOffset(offset);

        int grav;

        switch (layout.getParagraphAlignment(line)) {
//            case ALIGN_LEFT:
//                grav = 1;
//                break;
//            case ALIGN_RIGHT:
//                grav = -1;
//                break;
            case ALIGN_NORMAL:
                grav = layout.getParagraphDirection(line);
                break;
            case ALIGN_OPPOSITE:
                grav = -layout.getParagraphDirection(line);
                break;
            case ALIGN_CENTER:
            default:
                grav = 0;
                break;
        }

        // We only want to clamp the cursor to fit within the layout width
        // in left-to-right modes, because in a right to left alignment,
        // we want to scroll to keep the line-right on the screen, as other
        // lines are likely to have text flush with the right margin, which
        // we want to keep visible.
        // A better long-term solution would probably be to measure both
        // the full line and a blank-trimmed version, and, for example, use
        // the latter measurement for centering and right alignment, but for
        // the time being we only implement the cursor clamping in left to
        // right where it is most likely to be annoying.
        final boolean clamped = grav > 0;
        // FIXME: Is it okay to truncate this, or should we round?
        final int x = (int) layout.getPrimaryHorizontal(offset/*, clamped*/);
        final int top = layout.getLineTop(line);
        final int bottom = layout.getLineTop(line + 1);

        int left = (int) Math.floor(layout.getLineLeft(line));
        int right = (int) Math.ceil(layout.getLineRight(line));
        int ht = layout.getHeight();

        int hspace = getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        int vspace = getBottom() - getTop() - getExtendedPaddingTop() - getExtendedPaddingBottom();
        if (!mHorizontallyScrolling && right - left > hspace && right > x) {
            // If cursor has been clamped, make sure we don't scroll.
            right = Math.max(x, left + hspace);
        }

        int hslack = (bottom - top) / 2;
        int vslack = hslack;

        if (vslack > vspace / 4) {
            vslack = vspace / 4;
        }
        if (hslack > hspace / 4) {
            hslack = hspace / 4;
        }

        int hs = getScrollX();
        int vs = getScrollY();

        if (top - vs < vslack) {
            vs = top - vslack;
        }
        if (bottom - vs > vspace - vslack) {
            vs = bottom - (vspace - vslack);
        }
        if (ht - vs < vspace) {
            vs = ht - vspace;
        }
        if (0 - vs > 0) {
            vs = 0;
        }

        if (grav != 0) {
            if (x - hs < hslack) {
                hs = x - hslack;
            }
            if (x - hs > hspace - hslack) {
                hs = x - (hspace - hslack);
            }
        }

        if (grav < 0) {
            if (left - hs > 0) {
                hs = left;
            }
            if (right - hs < hspace) {
                hs = right - hspace;
            }
        } else if (grav > 0) {
            if (right - hs < hspace) {
                hs = right - hspace;
            }
            if (left - hs > 0) {
                hs = left;
            }
        } else /* grav == 0 */ {
            if (right - left <= hspace) {
                /*
                 * If the entire text fits, center it exactly.
                 */
                hs = left - (hspace - (right - left)) / 2;
            } else if (x > right - hslack) {
                /*
                 * If we are near the right edge, keep the right edge
                 * at the edge of the view.
                 */
                hs = right - hspace;
            } else if (x < left + hslack) {
                /*
                 * If we are near the left edge, keep the left edge
                 * at the edge of the view.
                 */
                hs = left;
            } else if (left > hs) {
                /*
                 * Is there whitespace visible at the left?  Fix it if so.
                 */
                hs = left;
            } else if (right < hs + hspace) {
                /*
                 * Is there whitespace visible at the right?  Fix it if so.
                 */
                hs = right - hspace;
            } else {
                /*
                 * Otherwise, float as needed.
                 */
                if (x - hs < hslack) {
                    hs = x - hslack;
                }
                if (x - hs > hspace - hslack) {
                    hs = x - (hspace - hslack);
                }
            }
        }

        if (hs != getScrollX() || vs != getScrollY()) {
            if (mScroller == null) {
                scrollTo(hs, vs);
            } else {
                long duration = AnimationUtils.currentAnimationTimeMillis() - mLastScroll;
                int dx = hs - getScrollX();
                int dy = vs - getScrollY();

                if (duration > ANIMATED_SCROLL_GAP) {
                    mScroller.startScroll(getScrollX(), getScrollY(), dx, dy);
                    awakenScrollBars(mScroller.getDuration());
                    invalidate();
                } else {
                    if (!mScroller.isFinished()) {
                        mScroller.abortAnimation();
                    }

                    scrollBy(dx, dy);
                }

                mLastScroll = AnimationUtils.currentAnimationTimeMillis();
            }

            changed = true;
        }

        if (isFocused()) {
            // This offsets because getInterestingRect() is in terms of viewport coordinates, but
            // requestRectangleOnScreen() is in terms of content coordinates.

            // The offsets here are to ensure the rectangle we are using is
            // within our view bounds, in case the cursor is on the far left
            // or right.  If it isn't withing the bounds, then this request
            // will be ignored.
            if (mTempRect == null) mTempRect = new Rect();
            mTempRect.set(x - 2, top, x + 2, bottom);
            getInterestingRect(mTempRect, line);
            mTempRect.offset(getScrollX(), getScrollY());

            if (requestRectangleOnScreen(mTempRect)) {
                changed = true;
            }
        }

        return changed;
    }

    /**
     * Move the cursor, if needed, so that it is at an offset that is visible
     * to the user.  This will not move the cursor if it represents more than
     * one character (a selection range).  This will only work if the
     * TextView contains spannable text; otherwise it will do nothing.
     *
     * @return True if the cursor was actually moved, false otherwise.
     */
    public boolean moveCursorToVisibleOffset() {
        if (!(mText instanceof Spannable)) {
            return false;
        }
        int start = getSelectionStart();
        int end = getSelectionEnd();
        if (start != end) {
            return false;
        }

        // First: make sure the line is visible on screen:

        int line = mLayout.getLineForOffset(start);

        final int top = mLayout.getLineTop(line);
        final int bottom = mLayout.getLineTop(line + 1);
        final int vspace = getBottom() - getTop() - getExtendedPaddingTop() - getExtendedPaddingBottom();
        int vslack = (bottom - top) / 2;
        if (vslack > vspace / 4) {
            vslack = vspace / 4;
        }
        final int vs = getScrollY();

        if (top < (vs + vslack)) {
            line = mLayout.getLineForVertical(vs + vslack + (bottom - top));
        } else if (bottom > (vspace + vs - vslack)) {
            line = mLayout.getLineForVertical(vspace + vs - vslack - (bottom - top));
        }

        // Next: make sure the character is visible on screen:

        final int hspace = getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        final int hs = getScrollX();
        final int leftChar = mLayout.getOffsetForHorizontal(line, hs);
        final int rightChar = mLayout.getOffsetForHorizontal(line, hspace + hs);

        // line might contain bidirectional text
        final int lowChar = leftChar < rightChar ? leftChar : rightChar;
        final int highChar = leftChar > rightChar ? leftChar : rightChar;

        int newStart = start;
        if (newStart < lowChar) {
            newStart = lowChar;
        } else if (newStart > highChar) {
            newStart = highChar;
        }

        if (newStart != start) {
            Selection.setSelection(mSpannable, newStart);
            return true;
        }

        return false;
    }

    private void getInterestingRect(Rect r, int line) {
        convertFromViewportToContentCoordinates(r);

        // Rectangle can can be expanded on first and last line to take
        // padding into account.
        // TODO Take left/right padding into account too?
        if (line == 0) r.top -= getExtendedPaddingTop();
        if (line == mLayout.getLineCount() - 1) r.bottom += getExtendedPaddingBottom();
    }

    private void convertFromViewportToContentCoordinates(Rect r) {
        final int horizontalOffset = viewportToContentHorizontalOffset();
        r.left += horizontalOffset;
        r.right += horizontalOffset;

        final int verticalOffset = viewportToContentVerticalOffset();
        r.top += verticalOffset;
        r.bottom += verticalOffset;
    }

    int viewportToContentHorizontalOffset() {
        return getCompoundPaddingLeft() - getScrollX();
    }

    int viewportToContentVerticalOffset() {
        int offset = getExtendedPaddingTop() - getScrollY();
        if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
            offset += getVerticalOffset(false);
        }
        return offset;
    }

    /**
     * Convenience for {@link Selection#getSelectionStart}.
     */
    @ViewDebug.ExportedProperty(category = "text")
    public int getSelectionStart() {
        return Selection.getSelectionStart(mText);
    }

    /**
     * Convenience for {@link Selection#getSelectionEnd}.
     */
    @ViewDebug.ExportedProperty(category = "text")
    public int getSelectionEnd() {
        return Selection.getSelectionEnd(mText);
    }

    /**
     * Return true iff there is a selection of nonzero length inside this text view.
     */
    public boolean hasSelection() {
        final int selectionStart = getSelectionStart();
        final int selectionEnd = getSelectionEnd();

        return selectionStart >= 0 && selectionEnd > 0 && selectionStart != selectionEnd;
    }

    String getSelectedText() {
        if (!hasSelection()) {
            return null;
        }

        final int start = getSelectionStart();
        final int end = getSelectionEnd();
        return String.valueOf(
                start > end ? mText.subSequence(end, start) : mText.subSequence(start, end));
    }

    /**
     * Adds or remove the EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE on the mInputType.
     * @param singleLine
     */
    private void setInputTypeSingleLine(boolean singleLine) {
        if (mEditor != null
                && (mEditor.mInputType & EditorInfo.TYPE_MASK_CLASS)
                == EditorInfo.TYPE_CLASS_TEXT) {
            if (singleLine) {
                mEditor.mInputType &= ~EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
            } else {
                mEditor.mInputType |= EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
            }
        }
    }

    private void applySingleLine(boolean singleLine, boolean applyTransformation,
                                 boolean changeMaxLines) {
        mSingleLine = singleLine;
        if (singleLine) {
            setLines(1);
            setHorizontallyScrolling(true);
            if (applyTransformation) {
                setTransformationMethod(SingleLineTransformationMethod.getInstance());
            }
        } else {
            if (changeMaxLines) {
                setMaxLines(Integer.MAX_VALUE);
            }
            setHorizontallyScrolling(false);
            if (applyTransformation) {
                setTransformationMethod(null);
            }
        }
    }

    /**
     * @return whether or not the cursor is visible (assuming this TextView is editable)
     *
     * @see #setCursorVisible(boolean)
     *
     * @attr ref android.R.styleable#TextView_cursorVisible
     */
    public boolean isCursorVisible() {
        // The default value is true, even when there is no associated Editor
//        return mCursorVisible && isTextEditable();
        return true;
    }

    /**
     * This method is called when the text is changed, in case any subclasses
     * would like to know.
     *
     * Within <code>text</code>, the <code>lengthAfter</code> characters
     * beginning at <code>start</code> have just replaced old text that had
     * length <code>lengthBefore</code>. It is an error to attempt to make
     * changes to <code>text</code> from this callback.
     *
     * @param text The text the TextView is displaying
     * @param start The offset of the start of the range of the text that was
     * modified
     * @param lengthBefore The length of the former text that has been replaced
     * @param lengthAfter The length of the replacement modified text
     */
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        // intentionally empty, template pattern method can be overridden by subclasses
    }

    /**
     * This method is called when the selection has changed, in case any
     * subclasses would like to know.
     * </p>
     * <p class="note"><strong>Note:</strong> Always call the super implementation, which informs
     * the accessibility subsystem about the selection change.
     * </p>
     *
     * @param selStart The new selection start location.
     * @param selEnd The new selection end location.
     */
//    @CallSuper
    protected void onSelectionChanged(int selStart, int selEnd) {
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED);
    }


    /**
     * Adds a TextWatcher to the list of those whose methods are called
     * whenever this TextView's text changes.
     * <p>
     * In 1.0, the {@link TextWatcher#afterTextChanged} method was erroneously
     * not called after {@link #setText} calls.  Now, doing {@link #setText}
     * if there are any text changed listeners forces the buffer type to
     * Editable if it would not otherwise be and does call this method.
     */
    public void addTextChangedListener(TextWatcher watcher) {
        if (mListeners == null) {
            mListeners = new ArrayList<TextWatcher>();
        }

        mListeners.add(watcher);
    }

    /**
     * Removes the specified TextWatcher from the list of those whose
     * methods are called
     * whenever this TextView's text changes.
     */
    public void removeTextChangedListener(TextWatcher watcher) {
        if (mListeners != null) {
            int i = mListeners.indexOf(watcher);

            if (i >= 0) {
                mListeners.remove(i);
            }
        }
    }

    private void sendBeforeTextChanged(CharSequence text, int start, int before, int after) {
        if (mListeners != null) {
            final ArrayList<TextWatcher> list = mListeners;
            final int count = list.size();
            for (int i = 0; i < count; i++) {
                list.get(i).beforeTextChanged(text, start, before, after);
            }
        }

        // The spans that are inside or intersect the modified region no longer make sense
//        removeIntersectingNonAdjacentSpans(start, start + before, SpellCheckSpan.class);
//        removeIntersectingNonAdjacentSpans(start, start + before, SuggestionSpan.class);
    }

    /**
     * Not private so it can be called from an inner class without going
     * through a thunk.
     */
    void sendOnTextChanged(CharSequence text, int start, int before, int after) {
        if (mListeners != null) {
            final ArrayList<TextWatcher> list = mListeners;
            final int count = list.size();
            for (int i = 0; i < count; i++) {
                list.get(i).onTextChanged(text, start, before, after);
            }
        }

        if (mEditor != null) mEditor.sendOnTextChanged(start, before, after);
    }

    /**
     * Not private so it can be called from an inner class without going
     * through a thunk.
     */
    void sendAfterTextChanged(Editable text) {
        if (mListeners != null) {
            final ArrayList<TextWatcher> list = mListeners;
            final int count = list.size();
            for (int i = 0; i < count; i++) {
                list.get(i).afterTextChanged(text);
            }
        }

//        notifyListeningManagersAfterTextChanged();

//        hideErrorIfUnchanged();
    }

    void updateAfterEdit() {
        invalidate();
        int curs = getSelectionStart();

        if (curs >= 0 || (mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
            //TODO: (EW) is this necessary?
            registerForPreDraw();
        }

        checkForResize();

        if (curs >= 0) {
            mHighlightPathBogus = true;
            if (mEditor != null) mEditor.makeBlink();
            bringPointIntoView(curs);
        }
    }

    /**
     * Not private so it can be called from an inner class without going
     * through a thunk.
     */
    void handleTextChanged(CharSequence buffer, int start, int before, int after) {
//        sLastCutCopyOrTextChangedTime = 0;

        final Editor.InputMethodState ims = mEditor == null ? null : mEditor.mInputMethodState;
        if (ims == null || ims.mBatchEditNesting == 0) {
            updateAfterEdit();
        }
        if (ims != null) {
            ims.mContentChanged = true;
            if (ims.mChangedStart < 0) {
                ims.mChangedStart = start;
                ims.mChangedEnd = start + before;
            } else {
                ims.mChangedStart = Math.min(ims.mChangedStart, start);
                ims.mChangedEnd = Math.max(ims.mChangedEnd, start + before - ims.mChangedDelta);
            }
            ims.mChangedDelta += after - before;
        }
//        resetErrorChangedFlag();
        sendOnTextChanged(buffer, start, before, after);
        onTextChanged(buffer, start, before, after);
    }

    /**
     * Not private so it can be called from an inner class without going
     * through a thunk.
     */
    void spanChange(Spanned buf, Object what, int oldStart, int newStart, int oldEnd, int newEnd) {
        // XXX Make the start and end move together if this ends up
        // spending too much time invalidating.

        boolean selChanged = false;
        int newSelStart = -1, newSelEnd = -1;

        final Editor.InputMethodState ims = mEditor == null ? null : mEditor.mInputMethodState;

        if (what == Selection.SELECTION_END) {
            selChanged = true;
            newSelEnd = newStart;

            if (oldStart >= 0 || newStart >= 0) {
                invalidateCursor(Selection.getSelectionStart(buf), oldStart, newStart);
                checkForResize();
                registerForPreDraw();
                if (mEditor != null) mEditor.makeBlink();
            }
        }

        if (what == Selection.SELECTION_START) {
            selChanged = true;
            newSelStart = newStart;

            if (oldStart >= 0 || newStart >= 0) {
                int end = Selection.getSelectionEnd(buf);
                invalidateCursor(end, oldStart, newStart);
            }
        }

        if (selChanged) {
            mHighlightPathBogus = true;
            if (mEditor != null && !isFocused()) mEditor.mSelectionMoved = true;

            if ((buf.getSpanFlags(what) & Spanned.SPAN_INTERMEDIATE) == 0) {
                if (newSelStart < 0) {
                    newSelStart = Selection.getSelectionStart(buf);
                }
                if (newSelEnd < 0) {
                    newSelEnd = Selection.getSelectionEnd(buf);
                }

                if (mEditor != null) {
                    mEditor.refreshTextActionMode();
                    if (!hasSelection()
                            && mEditor.getTextActionMode() == null && hasTransientState()) {
                        // User generated selection has been removed.
                        setHasTransientState(false);
                    }
                }
                onSelectionChanged(newSelStart, newSelEnd);
            }
        }

        if (what instanceof UpdateAppearance || what instanceof ParagraphStyle
                || what instanceof CharacterStyle) {
            if (ims == null || ims.mBatchEditNesting == 0) {
                invalidate();
                mHighlightPathBogus = true;
                checkForResize();
            } else {
                ims.mContentChanged = true;
            }
            if (mEditor != null) {
////                if (oldStart >= 0) mEditor.invalidateTextDisplayList(mLayout, oldStart, oldEnd);
////                if (newStart >= 0) mEditor.invalidateTextDisplayList(mLayout, newStart, newEnd);
//                mEditor.invalidateHandlesAndActionMode();
            }
        }

        if (MetaKeyKeyListener.isMetaTracker(buf, what)) {
            mHighlightPathBogus = true;
            if (ims != null && MetaKeyKeyListener.isSelectingMetaTracker(buf, what)) {
                ims.mSelectionModeChanged = true;
            }

            if (Selection.getSelectionStart(buf) >= 0) {
                if (ims == null || ims.mBatchEditNesting == 0) {
                    invalidateCursor();
                } else {
                    ims.mCursorChanged = true;
                }
            }
        }

        if (what instanceof ParcelableSpan) {
            // If this is a span that can be sent to a remote process,
            // the current extract editor would be interested in it.
            if (ims != null && ims.mExtractedTextRequest != null) {
                if (ims.mBatchEditNesting != 0) {
                    if (oldStart >= 0) {
                        if (ims.mChangedStart > oldStart) {
                            ims.mChangedStart = oldStart;
                        }
                        if (ims.mChangedStart > oldEnd) {
                            ims.mChangedStart = oldEnd;
                        }
                    }
                    if (newStart >= 0) {
                        if (ims.mChangedStart > newStart) {
                            ims.mChangedStart = newStart;
                        }
                        if (ims.mChangedStart > newEnd) {
                            ims.mChangedStart = newEnd;
                        }
                    }
                } else {
                    if (DEBUG_EXTRACT) {
                        Log.v(LOG_TAG, "Span change outside of batch: "
                                + oldStart + "-" + oldEnd + ","
                                + newStart + "-" + newEnd + " " + what);
                    }
                    ims.mContentChanged = true;
                }
            }
        }

//        if (mEditor != null && mEditor.mSpellChecker != null && newStart < 0
//                && what instanceof SpellCheckSpan) {
//            mEditor.mSpellChecker.onSpellCheckSpanRemoved((SpellCheckSpan) what);
//        }
    }

//    @Override
//    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
//        Log.w(TAG, "onFocusChanged: focused=" + focused + ", direction=" + direction);
////        if (mTemporaryDetach) {
////            // If we are temporarily in the detach state, then do nothing.
////            super.onFocusChanged(focused, direction, previouslyFocusedRect);
////            return;
////        }
//
////        if (mEditor != null) mEditor.onFocusChanged(focused, direction);
//        mShowCursor = SystemClock.uptimeMillis();
//        mEditor.ensureEndedBatchEdit();
//        if (focused) {
//            int selStart = getSelectionStart();
//            int selEnd = getSelectionEnd();
//
//            // SelectAllOnFocus fields are highlighted and not selected. Do not start text selection
//            // mode for these, unless there was a specific selection already started.
//            final boolean isFocusHighlighted = mSelectAllOnFocus && selStart == 0
//                    && selEnd == mText.length();
//
//            mCreatedWithASelection = mFrozenWithFocus && hasSelection()
//                    && !isFocusHighlighted;
//
//            if (!mFrozenWithFocus || (selStart < 0 || selEnd < 0)) {
//                // If a tap was used to give focus to that view, move cursor at tap position.
//                // Has to be done before onTakeFocus, which can be overloaded.
//                final int lastTapPosition = getLastTapPosition();
//                if (lastTapPosition >= 0) {
//                    Log.w(TAG, String.format("onFocusChanged: setting cursor position: %d", lastTapPosition));
//                    Selection.setSelection((Spannable) mText, lastTapPosition);
//                }
//
//                // Note this may have to be moved out of the Editor class
////                MovementMethod mMovement = mTextView.getMovementMethod();
//                if (mMovement != null) {
//                    mMovement.onTakeFocus(this, (Spannable) mText, direction);
//                }
//
//                // The DecorView does not have focus when the 'Done' ExtractEditText button is
//                // pressed. Since it is the ViewAncestor's mView, it requests focus before
//                // ExtractEditText clears focus, which gives focus to the ExtractEditText.
//                // This special case ensure that we keep current selection in that case.
//                // It would be better to know why the DecorView does not have focus at that time.
//                if (((isInExtractedMode()) || mSelectionMoved)
//                        && selStart >= 0 && selEnd >= 0) {
//                    /*
//                     * Someone intentionally set the selection, so let them
//                     * do whatever it is that they wanted to do instead of
//                     * the default on-focus behavior.  We reset the selection
//                     * here instead of just skipping the onTakeFocus() call
//                     * because some movement methods do something other than
//                     * just setting the selection in theirs and we still
//                     * need to go through that path.
//                     */
//                    Selection.setSelection((Spannable) mText, selStart, selEnd);
//                }
//
//                if (mSelectAllOnFocus) {
//                    selectAllText();
//                }
//
//                mTouchFocusSelected = true;
//            }
//
//            mFrozenWithFocus = false;
//            mSelectionMoved = false;
////
////            if (mError != null) {
////                showError();
////            }
////
//            makeBlink();
//        } else {
////            if (mError != null) {
////                hideError();
////            }
//            // Don't leave us in the middle of a batch edit.
//            onEndBatchEdit();
//
////            if (mTextView.isInExtractedMode()) {
////                hideCursorAndSpanControllers();
////                stopTextActionModeWithPreservingSelection();
////            } else {
////                hideCursorAndSpanControllers();
////                if (mTextView.isTemporarilyDetached()) {
////                    stopTextActionModeWithPreservingSelection();
////                } else {
////                    stopTextActionMode();
////                }
////                downgradeEasyCorrectionSpans();
////            }
////            // No need to create the controller
////            if (mSelectionModifierCursorController != null) {
////                mSelectionModifierCursorController.resetTouchOffsets();
////            }
////
////            ensureNoSelectionIfNonSelectable();
//        }
//
//        if (focused) {
//            if (mText instanceof Spannable) {
//                Spannable sp = (Spannable) mText;
//                MetaKeyKeyListener.resetMetaState(sp);
//            }
//        }
//
//        if (mTransformation != null) {
//            mTransformation.onFocusChanged(this, mText, focused, direction, previouslyFocusedRect);
//        }
//
//        super.onFocusChanged(focused, direction, previouslyFocusedRect);
//    }
    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (isTemporarilyDetached()) {
                // If we are temporarily in the detach state, then do nothing.
                super.onFocusChanged(focused, direction, previouslyFocusedRect);
                return;
            }
        }

        if (mEditor != null) mEditor.onFocusChanged(focused, direction);

        if (focused) {
            if (mSpannable != null) {
                MetaKeyKeyListener.resetMetaState(mSpannable);
            }
        }

        if (mTransformation != null) {
            mTransformation.onFocusChanged(this, mText, focused, direction, previouslyFocusedRect);
        }

        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

//    @Override
//    public boolean onTouchEvent(MotionEvent event) {
////        if (DEBUG_CURSOR) {
////            logCursor("onTouchEvent", "%d: %s (%f,%f)",
////                    event.getSequenceNumber(),
////                    MotionEvent.actionToString(event.getActionMasked()),
////                    event.getX(), event.getY());
////        }
//        Log.w(TAG, String.format("onTouchEvent: %s (%f,%f)",
//                MotionEvent.actionToString(event.getActionMasked()),
//                event.getX(), event.getY()));
////        if (!isFromPrimePointer(event, false)) {
////            return true;
////        }
////
//        final int action = event.getActionMasked();
//////        if (mEditor != null) {
//////            mEditor.onTouchEvent(event);
////        editorOnTouchEvent(event);
//////
//////            if (mEditor.mInsertionPointCursorController != null
//////                    && mEditor.mInsertionPointCursorController.isCursorBeingModified()) {
//////                return true;
//////            }
////            if (/*mEditor.*/mSelectionModifierCursorController != null
////                    && /*mEditor.*/mSelectionModifierCursorController.isDragAcceleratorActive()) {
////                return true;
////            }
//////        }
//////
//        if (mTouchManager.onTouchEventPre2(event)) {
//            return true;
//        }
//
//        final boolean superResult = super.onTouchEvent(event);
//////        if (DEBUG_CURSOR) {
//////            logCursor("onTouchEvent", "superResult=%s", superResult);
//////        }
//////
//////        /*
//////         * Don't handle the release after a long press, because it will move the selection away from
//////         * whatever the menu action was trying to affect. If the long press should have triggered an
//////         * insertion action mode, we can now actually show it.
//////         */
//////        if (mEditor != null && mEditor.mDiscardNextActionUp && action == MotionEvent.ACTION_UP) {
//////            mEditor.mDiscardNextActionUp = false;
//////            if (DEBUG_CURSOR) {
//////                logCursor("onTouchEvent", "release after long press detected");
//////            }
//////            if (mEditor.mIsInsertionActionModeStartPending) {
//////                mEditor.startInsertionActionMode();
//////                mEditor.mIsInsertionActionModeStartPending = false;
//////            }
//////            return superResult;
//////        }
//////
////        final boolean touchIsFinished = (action == MotionEvent.ACTION_UP)
////                /*&& (mEditor == null || !mEditor.mIgnoreActionUpEvent)*/ && isFocused();
//////
//////        if ((mMovement != null || onCheckIsTextEditor()) && isEnabled()
//////                && mText instanceof Spannable && mLayout != null) {
////        boolean handled = false;
//////
//////            if (mMovement != null) {
//////                handled |= mMovement.onTouchEvent(this, mSpannable, event);
//////            }
//////
////        final boolean textIsSelectable = /*isTextSelectable()*/true;
//////            if (touchIsFinished && mLinksClickable && mAutoLinkMask != 0 && textIsSelectable) {
//////                // The LinkMovementMethod which should handle taps on links has not been installed
//////                // on non editable text that support text selection.
//////                // We reproduce its behavior here to open links for these.
//////                ClickableSpan[] links = mSpannable.getSpans(getSelectionStart(),
//////                        getSelectionEnd(), ClickableSpan.class);
//////
//////                if (links.length > 0) {
//////                    links[0].onClick(this);
//////                    handled = true;
//////                }
//////            }
//////
////        if (touchIsFinished && (/*isTextEditable()*/true || textIsSelectable)) {
////            // Show the IME, except when selecting in read-only text.
//////            final InputMethodManager imm = getInputMethodManager();
//////            viewClicked(imm);
//////            if (/*isTextEditable()*/true && /*mEditor.mShowSoftInputOnFocus*/true && imm != null) {
//////                imm.showSoftInput(this, 0);
//////            }
//////
//////                // The above condition ensures that the mEditor is not null
//////                editorOnTouchUpEvent(event);
//////
//////            handled = true;
////        }
////
////        if (handled) {
////            return true;
////        }
//////        }
//////
//        Log.w(TAG, "onTouchEvent: isFocused=" + isFocused());
//
////        return superResult;
//        return mTouchManager.onTouchEventPost(event, superResult);
//    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        if (mEditor != null) {
            mEditor.onTouchEvent(event);

//            if (mEditor.mSelectionModifierCursorController != null
//                    && mEditor.mSelectionModifierCursorController.isDragAcceleratorActive()) {
//                return true;
//            }
        }

        final boolean superResult = super.onTouchEvent(event);

        /*
         * Don't handle the release after a long press, because it will move the selection away from
         * whatever the menu action was trying to affect. If the long press should have triggered an
         * insertion action mode, we can now actually show it.
         */
        if (mEditor != null && mEditor.mDiscardNextActionUp && action == MotionEvent.ACTION_UP) {
            mEditor.mDiscardNextActionUp = false;

            if (mEditor.mIsInsertionActionModeStartPending) {
                mEditor.startInsertionActionMode();
                mEditor.mIsInsertionActionModeStartPending = false;
            }
            return superResult;
        }

        final boolean touchIsFinished = (action == MotionEvent.ACTION_UP)
                && (mEditor == null || !mEditor.mIgnoreActionUpEvent) && isFocused();

        if ((mMovement != null || onCheckIsTextEditor()) && isEnabled()
                && mText instanceof Spannable && mLayout != null) {
            boolean handled = false;

            if (mMovement != null) {
                handled |= mMovement.onTouchEvent(this, mSpannable, event);
            }

            final boolean textIsSelectable = isTextSelectable();
//            if (touchIsFinished && mLinksClickable && mAutoLinkMask != 0 && textIsSelectable) {
//                // The LinkMovementMethod which should handle taps on links has not been installed
//                // on non editable text that support text selection.
//                // We reproduce its behavior here to open links for these.
//                ClickableSpan[] links = mSpannable.getSpans(getSelectionStart(),
//                        getSelectionEnd(), ClickableSpan.class);
//
//                if (links.length > 0) {
//                    links[0].onClick(this);
//                    handled = true;
//                }
//            }

            if (touchIsFinished && (isTextEditable() || textIsSelectable)) {
                // Show the IME, except when selecting in read-only text.
                final InputMethodManager imm = /*InputMethodManager.peekInstance()*/getInputMethodManager();
                viewClicked(imm);
                if (isTextEditable() /*&& mEditor.mShowSoftInputOnFocus*/ && imm != null) {
                    imm.showSoftInput(this, 0);
                }

                // The above condition ensures that the mEditor is not null
                mEditor.onTouchUpEvent(event);

                handled = true;
            }

            if (handled) {
                return true;
            }
        }

        return superResult;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        Log.w(TAG, "onGenericMotionEvent: " + event);
        if (mMovement != null && mText instanceof Spannable && mLayout != null) {
            try {
                if (mMovement.onGenericMotionEvent(this, mSpannable, event)) {
                    return true;
                }
            } catch (AbstractMethodError ex) {
                // onGenericMotionEvent was added to the MovementMethod interface in API 12.
                // Ignore its absence in case third party applications implemented the
                // interface directly.
            }
        }
        return super.onGenericMotionEvent(event);
    }

    /**
     * @return True iff this TextView contains a text that can be edited, or if this is
     * a selectable TextView.
     */
    boolean isTextEditable() {
        return mText instanceof Editable && onCheckIsTextEditor() && isEnabled();
    }

    /**
     * Returns true, only while processing a touch gesture, if the initial
     * touch down event caused focus to move to the text view and as a result
     * its selection changed.  Only valid while processing the touch gesture
     * of interest, in an editable text view.
     */
    public boolean didTouchFocusSelect() {
        return mEditor != null && mEditor.mTouchFocusSelected;
    }

    /**
     * Unlike {@link #textCanBeSelected()}, this method is based on the <i>current</i> state of the
     * TextView. {@link #textCanBeSelected()} has to be true (this is one of the conditions to have
     * a selection controller (see {@link Editor#prepareCursorControllers()}), but this is not
     * sufficient.
     */
    boolean canSelectText() {
        return mText.length() != 0 && mEditor != null && mEditor.hasSelectionController();
    }

    /**
     * Test based on the <i>intrinsic</i> charateristics of the TextView.
     * The text must be spannable and the movement method must allow for arbitary selection.
     *
     * See also {@link #canSelectText()}.
     */
    boolean textCanBeSelected() {
        // prepareCursorController() relies on this method.
        // If you change this condition, make sure prepareCursorController is called anywhere
        // the value of this condition might be changed.
        if (mMovement == null || !mMovement.canSelectArbitrarily()) return false;
        return isTextEditable()
                || (isTextSelectable() && mText instanceof Spannable && isEnabled());
    }

//    private Locale getTextServicesLocale(boolean allowNullLocale) {
//        // Start fetching the text services locale asynchronously.
//        updateTextServicesLocaleAsync();
//        // If !allowNullLocale and there is no cached text services locale, just return the default
//        // locale.
//        return (mCurrentSpellCheckerLocaleCache == null && !allowNullLocale) ? Locale.getDefault()
//                : mCurrentSpellCheckerLocaleCache;
//    }

    /**
     * This is a temporary method. Future versions may support multi-locale text.
     * Caveat: This method may not return the latest text services locale, but this should be
     * acceptable and it's more important to make this method asynchronous.
     *
     * @return The locale that should be used for a word iterator
     * in this TextView, based on the current spell checker settings,
     * the current IME's locale, or the system default locale.
     * Please note that a word iterator in this TextView is different from another word iterator
     * used by SpellChecker.java of TextView. This method should be used for the former.
     * @hide
     */
    // TODO: Support multi-locale
    // TODO: Update the text services locale immediately after the keyboard locale is switched
    // by catching intent of keyboard switch event
    public Locale getTextServicesLocale() {
//        return getTextServicesLocale(false /* allowNullLocale */);
        return new Locale("en", "US");
    }

    /**
     * @return {@code true} if this TextView is specialized for showing and interacting with the
     * extracted text in a full-screen input method.
     * @hide
     */
    public boolean isInExtractedMode() {
        return false;
    }
    public boolean isPositionVisible(final float positionX, final float positionY) {
        synchronized (TEMP_POSITION) {
            final float[] position = TEMP_POSITION;
            position[0] = positionX;
            position[1] = positionY;
            View view = this;

            while (view != null) {
                if (view != this) {
                    // Local scroll is already taken into account in positionX/Y
                    position[0] -= view.getScrollX();
                    position[1] -= view.getScrollY();
                }

                if (position[0] < 0 || position[1] < 0 || position[0] > view.getWidth()
                        || position[1] > view.getHeight()) {
                    return false;
                }

                if (!view.getMatrix().isIdentity()) {
                    view.getMatrix().mapPoints(position);
                }

                position[0] += view.getLeft();
                position[1] += view.getTop();

                final ViewParent parent = view.getParent();
                if (parent instanceof View) {
                    view = (View) parent;
                } else {
                    // We've reached the ViewRoot, stop iterating
                    view = null;
                }
            }
        }

        // We've been able to walk up the view hierarchy and the position was never clipped
        return true;
    }

    public InputMethodManager getInputMethodManager() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getContext().getSystemService(InputMethodManager.class);
        }
        //TODO: verify this works
        return (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    static final int ID_SELECT_ALL = android.R.id.selectAll;
    static final int ID_UNDO = android.R.id.undo;
    static final int ID_REDO = android.R.id.redo;
    static final int ID_CUT = android.R.id.cut;
    static final int ID_COPY = android.R.id.copy;
    static final int ID_PASTE = android.R.id.paste;
    static final int ID_SHARE = android.R.id.shareText;
    static final int ID_PASTE_AS_PLAIN_TEXT = android.R.id.pasteAsPlainText;
    static final int ID_REPLACE = android.R.id.replaceText;
    static final int ID_ASSIST = android.R.id.textAssist;
    static final int ID_AUTOFILL = android.R.id.autofill;

    /**
     * Called when a context menu option for the text view is selected.  Currently
     * this will be one of {@link android.R.id#selectAll}, {@link android.R.id#cut},
     * {@link android.R.id#copy}, {@link android.R.id#paste} or {@link android.R.id#shareText}.
     *
     * @return true if the context menu item action was performed.
     */
    public boolean onTextContextMenuItem(int id) {
        int min = 0;
        int max = mText.length();

        if (isFocused()) {
            final int selStart = getSelectionStart();
            final int selEnd = getSelectionEnd();

            min = Math.max(0, Math.min(selStart, selEnd));
            max = Math.max(0, Math.max(selStart, selEnd));
        }

        switch (id) {
            case ID_SELECT_ALL:
                final boolean hadSelection = hasSelection();
                selectAllText();
                if (mEditor != null && hadSelection) {
                    mEditor.invalidateActionModeAsync();
                }
                return true;

            case ID_UNDO:
                if (mEditor != null) {
                    mEditor.undo();
                }
                return true;  // Returns true even if nothing was undone.

            case ID_REDO:
                if (mEditor != null) {
                    mEditor.redo();
                }
                return true;  // Returns true even if nothing was undone.

            case ID_PASTE:
                paste(min, max, true /* withFormatting */);
                return true;

            case ID_PASTE_AS_PLAIN_TEXT:
                paste(min, max, false /* withFormatting */);
                return true;

            case ID_CUT:
                final ClipData cutData = ClipData.newPlainText(null, getTransformedText(min, max));
                if (setPrimaryClip(cutData)) {
                    deleteText_internal(min, max);
                } else {
                    Toast.makeText(getContext(),
                            R.string.failed_to_copy_to_clipboard,
                            Toast.LENGTH_SHORT).show();
                }
                return true;

            case ID_COPY:
                // For link action mode in a non-selectable/non-focusable TextView,
                // make sure that we set the appropriate min/max.
                final int selStart = getSelectionStart();
                final int selEnd = getSelectionEnd();
                min = Math.max(0, Math.min(selStart, selEnd));
                max = Math.max(0, Math.max(selStart, selEnd));
                final ClipData copyData = ClipData.newPlainText(null, getTransformedText(min, max));
                if (setPrimaryClip(copyData)) {
                    stopTextActionMode();
                } else {
                    Toast.makeText(getContext(),
                            R.string.failed_to_copy_to_clipboard,
                            Toast.LENGTH_SHORT).show();
                }
                return true;

            case ID_REPLACE:
                if (mEditor != null) {
//                    mEditor.replace();
                }
                return true;

            case ID_SHARE:
//                shareSelectedText();
                return true;

            case ID_AUTOFILL:
//                requestAutofill();
//                stopTextActionMode();
                return true;
        }
        return false;
    }

    CharSequence getTransformedText(int start, int end) {
        return removeSuggestionSpans(mTransformed.subSequence(start, end));
    }

    @Override
    public boolean performLongClick() {
        Log.w(TAG, "performLongClick");
        boolean handled = false;
        boolean performedHapticFeedback = false;

        if (mEditor != null) {
            mEditor.mIsBeingLongClicked = true;
        }

        if (super.performLongClick()) {
            handled = true;
            performedHapticFeedback = true;
        }

        if (mEditor != null) {
            handled |= mEditor.performLongClick(handled);
            mEditor.mIsBeingLongClicked = false;
        }

        if (handled) {
            if (!performedHapticFeedback) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
            if (mEditor != null) mEditor.mDiscardNextActionUp = true;
        } else {
//            MetricsLogger.action(
//                    mContext,
//                    MetricsEvent.TEXT_LONGPRESS,
//                    TextViewMetrics.SUBTYPE_LONG_PRESS_OTHER);
        }

        return handled;
    }

    @Override
    protected void onScrollChanged(int horiz, int vert, int oldHoriz, int oldVert) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert);
        Log.w(TAG, "onScrollChanged: horiz=" + horiz + ", vert=" + vert + ", oldHoriz=" + oldHoriz + ", oldVert=" + oldVert);
        if (mEditor != null) {
            mEditor.onScrollChanged();
        }
    }

    /**
     * Returns the {@link TextClassifier} used by this TextView.
     * If no TextClassifier has been set, this TextView uses the default set by the
     * {@link TextClassificationManager}.
     */
    @NonNull
    public TextClassifier getTextClassifier() {
//        if (mTextClassifier == null) {
//            final TextClassificationManager tcm =
//                    mContext.getSystemService(TextClassificationManager.class);
//            if (tcm != null) {
//                return tcm.getTextClassifier();
//            }
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                return TextClassifier.NO_OP;
//            } else {
//                //TODO: handle
//                return null;
//            }
//        }
//        return mTextClassifier;
        return TextClassifier.NO_OP;
    }

    /**
     * Returns a session-aware text classifier.
     * This method creates one if none already exists or the current one is destroyed.
     */
    @NonNull
    TextClassifier getTextClassificationSession() {
//        if (mTextClassificationSession == null || mTextClassificationSession.isDestroyed()) {
//            final TextClassificationManager tcm =
//                    mContext.getSystemService(TextClassificationManager.class);
//            if (tcm != null) {
//                final String widgetType;
//                if (isTextEditable()) {
//                    widgetType = TextClassifier.WIDGET_TYPE_EDITTEXT;
//                } else if (isTextSelectable()) {
//                    widgetType = TextClassifier.WIDGET_TYPE_TEXTVIEW;
//                } else {
//                    widgetType = TextClassifier.WIDGET_TYPE_UNSELECTABLE_TEXTVIEW;
//                }
//                final TextClassificationContext textClassificationContext =
//                        new TextClassificationContext.Builder(
//                                mContext.getPackageName(), widgetType)
//                                .build();
//                if (mTextClassifier != null) {
//                    mTextClassificationSession = tcm.createTextClassificationSession(
//                            textClassificationContext, mTextClassifier);
//                } else {
//                    mTextClassificationSession = tcm.createTextClassificationSession(
//                            textClassificationContext);
//                }
//            } else {
//                mTextClassificationSession = TextClassifier.NO_OP;
//            }
//        }
//        return mTextClassificationSession;
        return TextClassifier.NO_OP;
    }

    /**
     * Returns true if this TextView uses a no-op TextClassifier.
     */
    boolean usesNoOpTextClassifier() {
        return getTextClassifier() == TextClassifier.NO_OP;
    }

    protected void stopTextActionMode() {
        if (mEditor != null) {
            mEditor.stopTextActionMode();
        }
    }

    boolean canUndo() {
        return mEditor != null && mEditor.canUndo();
    }

    boolean canRedo() {
        return mEditor != null && mEditor.canRedo();
    }

    boolean canCut() {
        if (hasPasswordTransformationMethod()) {
            return false;
        }

        if (mText.length() > 0 && hasSelection() && mText instanceof Editable && mEditor != null
                && mEditor.mKeyListener != null) {
            return true;
        }

        return false;
    }

    boolean canCopy() {
        if (hasPasswordTransformationMethod()) {
            return false;
        }

        if (mText.length() > 0 && hasSelection() && mEditor != null) {
            return true;
        }

        return false;
    }

    boolean canShare() {
//        if (!getContext().canStartActivityForResult() || !isDeviceProvisioned()) {
//            return false;
//        }
        return canCopy();
    }

    boolean canPaste() {
        return (mText instanceof Editable
                && mEditor != null && mEditor.mKeyListener != null
                && getSelectionStart() >= 0
                && getSelectionEnd() >= 0
                && ((ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE))
                .hasPrimaryClip());
    }

    boolean canPasteAsPlainText() {
        if (!canPaste()) {
            return false;
        }

        final ClipData clipData =
                ((ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE))
                        .getPrimaryClip();
        final ClipDescription description = clipData.getDescription();
        final boolean isPlainType = description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);
        final CharSequence text = clipData.getItemAt(0).getText();
        if (isPlainType && (text instanceof Spanned)) {
            Spanned spanned = (Spanned) text;
            if (HiddenTextUtils.hasStyleSpan(spanned)) {
                return true;
            }
        }
        return description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML);
    }

    boolean canProcessText() {
        if (getId() == View.NO_ID) {
            return false;
        }
        return canShare();
    }

    boolean canSelectAllText() {
        return /*canSelectText() &&*/ !hasPasswordTransformationMethod()
                && !(getSelectionStart() == 0 && getSelectionEnd() == mText.length());
    }

    boolean selectAllText() {
//        if (mEditor != null) {
//            // Hide the toolbar before changing the selection to avoid flickering.
//            hideFloatingToolbar(FLOATING_TOOLBAR_SELECT_ALL_REFRESH_DELAY);
//        }
        final int length = mText.length();
        Selection.setSelection(mSpannable, 0, length);
        return length > 0;
    }

    void replaceSelectionWithText(CharSequence text) {
        ((Editable) mText).replace(getSelectionStart(), getSelectionEnd(), text);
    }

    /**
     * Paste clipboard content between min and max positions.
     */
    private void paste(int min, int max, boolean withFormatting) {
        ClipboardManager clipboard =
                (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null) {
            boolean didFirst = false;
            for (int i = 0; i < clip.getItemCount(); i++) {
                final CharSequence paste;
                if (withFormatting) {
                    paste = clip.getItemAt(i).coerceToStyledText(getContext());
                } else {
                    // Get an item as text and remove all spans by toString().
                    final CharSequence text = clip.getItemAt(i).coerceToText(getContext());
                    paste = (text instanceof Spanned) ? text.toString() : text;
                }
                if (paste != null) {
                    if (!didFirst) {
                        Selection.setSelection(mSpannable, max);
                        ((Editable) mText).replace(min, max, paste);
                        didFirst = true;
                    } else {
                        ((Editable) mText).insert(getSelectionEnd(), "\n");
                        ((Editable) mText).insert(getSelectionEnd(), paste);
                    }
                }
            }
            sLastCutCopyOrTextChangedTime = 0;
        }
    }

    private void shareSelectedText() {
        String selectedText = getSelectedText();
        if (selectedText != null && !selectedText.isEmpty()) {
            Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
            sharingIntent.setType("text/plain");
            sharingIntent.removeExtra(android.content.Intent.EXTRA_TEXT);
            selectedText = HiddenTextUtils.trimToParcelableSize(selectedText);
            sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, selectedText);
            getContext().startActivity(Intent.createChooser(sharingIntent, null));
            Selection.setSelection(mSpannable, getSelectionEnd());
        }
    }

    private boolean setPrimaryClip(ClipData clip) {
        ClipboardManager clipboard =
                (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        try {
            clipboard.setPrimaryClip(clip);
        } catch (Throwable t) {
            return false;
        }
        sLastCutCopyOrTextChangedTime = SystemClock.uptimeMillis();
        return true;
    }

    /**
     * Get the character offset closest to the specified absolute position. A typical use case is to
     * pass the result of {@link MotionEvent#getX()} and {@link MotionEvent#getY()} to this method.
     *
     * @param x The horizontal absolute position of a point on screen
     * @param y The vertical absolute position of a point on screen
     * @return the character offset for the character whose position is closest to the specified
     *  position. Returns -1 if there is no layout.
     */
    public int getOffsetForPosition(float x, float y) {
        if (getLayout() == null) return -1;
        final int line = getLineAtCoordinate(y);
        final int offset = getOffsetAtCoordinate(line, x);
        return offset;
    }

    float convertToLocalHorizontalCoordinate(float x) {
        x -= getTotalPaddingLeft();
        // Clamp the position to inside of the view.
        x = Math.max(0.0f, x);
        x = Math.min(getWidth() - getTotalPaddingRight() - 1, x);
        x += getScrollX();
        return x;
    }

    int getLineAtCoordinate(float y) {
        y -= getTotalPaddingTop();
        // Clamp the position to inside of the view.
        y = Math.max(0.0f, y);
        y = Math.min(getHeight() - getTotalPaddingBottom() - 1, y);
        y += getScrollY();
        return getLayout().getLineForVertical((int) y);
    }

    int getOffsetAtCoordinate(int line, float x) {
        x = convertToLocalHorizontalCoordinate(x);
        return getLayout().getOffsetForHorizontal(line, x);
    }

    boolean isInBatchEditMode() {
        if (mEditor == null) return false;
        final Editor.InputMethodState ims = mEditor.mInputMethodState;
        if (ims != null) {
            return ims.mBatchEditNesting > 0;
        }
        return mEditor.mInBatchEditControllers;
    }

    protected void viewClicked(InputMethodManager imm) {
        if (imm != null) {
            imm.viewClicked(this);
        }
    }

    /**
     * Deletes the range of text [start, end[.
     * @hide
     */
    protected void deleteText_internal(int start, int end) {
        ((Editable) mText).delete(start, end);
    }

    private class ChangeWatcher implements TextWatcher, SpanWatcher {

        private CharSequence mBeforeText;

        public void beforeTextChanged(CharSequence buffer, int start,
                                      int before, int after) {
            if (DEBUG_EXTRACT) {
                Log.v(LOG_TAG, "beforeTextChanged start=" + start
                        + " before=" + before + " after=" + after + ": " + buffer);
            }

//            if (AccessibilityManager.getInstance(mContext).isEnabled() && (mTransformed != null)) {
//                mBeforeText = mTransformed.toString();
//            }

            EditText.this.sendBeforeTextChanged(buffer, start, before, after);
        }

        public void onTextChanged(CharSequence buffer, int start, int before, int after) {
            if (DEBUG_EXTRACT) {
                Log.v(LOG_TAG, "onTextChanged start=" + start
                        + " before=" + before + " after=" + after + ": " + buffer);
            }
            EditText.this.handleTextChanged(buffer, start, before, after);

//            if (AccessibilityManager.getInstance(mContext).isEnabled()
//                    && (isFocused() || isSelected() && isShown())) {
//                sendAccessibilityEventTypeViewTextChanged(mBeforeText, start, before, after);
//                mBeforeText = null;
//            }
        }

        public void afterTextChanged(Editable buffer) {
            if (DEBUG_EXTRACT) {
                Log.v(LOG_TAG, "afterTextChanged: " + buffer);
            }
            EditText.this.sendAfterTextChanged(buffer);

//            if (MetaKeyKeyListener.getMetaState(buffer, MetaKeyKeyListener.META_SELECTING) != 0) {
//                MetaKeyKeyListener.stopSelecting(EditText.this, buffer);
//            }
        }

        public void onSpanChanged(Spannable buf, Object what, int s, int e, int st, int en) {
            if (DEBUG_EXTRACT) {
                Log.v(LOG_TAG, "onSpanChanged s=" + s + " e=" + e
                        + " st=" + st + " en=" + en + " what=" + what + ": " + buf);
            }
            EditText.this.spanChange(buf, what, s, st, e, en);
        }

        public void onSpanAdded(Spannable buf, Object what, int s, int e) {
            if (DEBUG_EXTRACT) {
                Log.v(LOG_TAG, "onSpanAdded s=" + s + " e=" + e + " what=" + what + ": " + buf);
            }
            EditText.this.spanChange(buf, what, -1, s, -1, e);
        }

        public void onSpanRemoved(Spannable buf, Object what, int s, int e) {
            if (DEBUG_EXTRACT) {
                Log.v(LOG_TAG, "onSpanRemoved s=" + s + " e=" + e + " what=" + what + ": " + buf);
            }
            EditText.this.spanChange(buf, what, s, -1, e, -1);
        }
    }













    // from Layout
    //TODO: (EW) copied from Layout - see if this can be removed
    private enum Alignment {
        ALIGN_NORMAL,
        ALIGN_OPPOSITE,
        ALIGN_CENTER,
        /** @hide */
        ALIGN_LEFT,
        /** @hide */
        ALIGN_RIGHT,
    }
    private Alignment convertAlignment(Layout.Alignment alignment) {
        switch (alignment) {
            case ALIGN_NORMAL:
                return Alignment.ALIGN_NORMAL;
            case ALIGN_OPPOSITE:
                return Alignment.ALIGN_OPPOSITE;
            case ALIGN_CENTER:
                return Alignment.ALIGN_CENTER;
        }
        //TODO: (EW) could this ever happen?
        Log.e(TAG, "convertAlignment: " + alignment);
        return Alignment.ALIGN_LEFT;
    }
















    // from View
    /** @hide */
    public void transformFromViewToWindowSpace(@Size(2) int[] inOutLocation) {
        if (inOutLocation == null || inOutLocation.length < 2) {
            throw new IllegalArgumentException("inOutLocation must be an array of two integers");
        }

        if (!isAttachedToWindow()) {
            // When the view is not attached to a window, this method does not make sense
            inOutLocation[0] = inOutLocation[1] = 0;
            return;
        }

        float position[] = /*mAttachInfo.mTmpTransformLocation*/new float[2];
        position[0] = inOutLocation[0];
        position[1] = inOutLocation[1];

        //TODO: (EW) figure out what to do with this
//        if (!hasIdentityMatrix()) {
//            getMatrix().mapPoints(position);
//        }

        position[0] += getLeft();
        position[1] += getTop();

        ViewParent viewParent = getParent();
        while (viewParent instanceof View) {
            final View view = (View) viewParent;

            position[0] -= view.getScrollX();
            position[1] -= view.getScrollY();

            //TODO: (EW) figure out what to do with this
//            if (!view.hasIdentityMatrix()) {
//                view.getMatrix().mapPoints(position);
//            }

            position[0] += view.getLeft();
            position[1] += view.getTop();

            viewParent = view.getParent();
        }

        //TODO: (EW) figure out what to do with this
//        if (viewParent instanceof ViewRootImpl) {
//            // *cough*
//            final ViewRootImpl vr = (ViewRootImpl) viewParent;
//            position[1] -= vr.mCurScrollY;
//        }

        inOutLocation[0] = Math.round(position[0]);
        inOutLocation[1] = Math.round(position[1]);
    }

    // from View
    /**
     * Transforms a motion event from on-screen coordinates to view-local
     * coordinates.
     *
     * @param ev the on-screen motion event
     * @return false if the transformation could not be applied
     * @hide
     */
    public boolean toLocalMotionEvent(MotionEvent ev) {
////        final AttachInfo info = mAttachInfo;
//        if (/*info == null*/!isAttachedToWindow()) {
//            return false;
//        }
//
//        final Matrix m = info.mTmpMatrix;
//        m.set(Matrix.IDENTITY_MATRIX);
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            transformMatrixToLocal(m);
//        }
//        ev.transform(m);
//        return true;
        return false;
    }

    // from View
    /**
     * Call {@link Context#startActivityForResult(String, Intent, int, Bundle)} for the View's
     * Context, creating a unique View identifier to retrieve the result.
     *
     * @param intent The Intent to be started.
     * @param requestCode The request code to use.
     * @hide
     */
    public void startActivityForResult(Intent intent, int requestCode) {
//        mStartActivityRequestWho = "@android:view:" + System.identityHashCode(this);
//        getContext().startActivityForResult(mStartActivityRequestWho, intent, requestCode, null);
    }
}
