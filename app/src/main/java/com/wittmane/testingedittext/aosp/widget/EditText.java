package com.wittmane.testingedittext.aosp.widget;

import android.app.assist.AssistStructure;
import android.app.assist.AssistStructure.ViewNode;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.Canvas;
import android.graphics.fonts.FontVariationAxis;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.icu.text.DecimalFormatSymbols;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.text.BoringLayout;
import android.text.DynamicLayout;
import android.text.Editable;
import android.text.GetChars;
import android.text.InputFilter;
import android.text.InputFilter.LengthFilter;
import android.text.InputType;
import android.text.Layout;
import android.text.ParcelableSpan;
import android.text.Selection;
import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristic;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
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
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewParent;
import android.view.ViewStructure;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AnimationUtils;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.textclassifier.TextClassificationManager;
import android.view.textclassifier.TextClassifier;
import android.widget.Scroller;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.CallSuper;
import androidx.annotation.ColorInt;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.RequiresApi;
import androidx.annotation.Size;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.annotation.XmlRes;
import androidx.core.content.ContextCompat;

import com.wittmane.testingedittext.CustomInputConnection;
import com.wittmane.testingedittext.HiddenTextUtils;
import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.aosp.os.ParcelableParcel;
import com.wittmane.testingedittext.aosp.text.comutil.Preconditions;
import com.wittmane.testingedittext.aosp.text.method.ArrowKeyMovementMethod;
import com.wittmane.testingedittext.aosp.text.method.MovementMethod;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Locale;

import static android.view.accessibility.AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH;
import static android.view.accessibility.AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX;
import static android.view.accessibility.AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY;
import static android.view.inputmethod.CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION;

public class EditText extends View implements ViewTreeObserver.OnPreDrawListener {
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
    private static final int[] MULTILINE_STATE_SET = { android.R.attr.state_multiline };

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
    @ViewDebug.ExportedProperty(category = "text")
    private int mCurTextColor;
    private int mCurHintTextColor;
    private boolean mFreezesText;
    private boolean mDispatchTemporaryDetach;

    /** Whether this view is temporarily detached from the parent view. */
    boolean mTemporaryDetach;

    private Editable.Factory mEditableFactory = Editable.Factory.getInstance();
    private Spannable.Factory mSpannableFactory = Spannable.Factory.getInstance();

    private float mShadowRadius, mShadowDx, mShadowDy;
    private int mShadowColor;

    private boolean mPreDrawRegistered;
    private boolean mPreDrawListenerDetached;

    // A flag to prevent repeated movements from escaping the enclosing text view. The idea here is
    // that if a user is holding down a movement key to traverse text, we shouldn't also traverse
    // the view hierarchy. On the other hand, if the user is using the movement key to traverse
    // views (i.e. the first movement was to traverse out of this view, or this view was traversed
    // into by the user holding the movement key down) then we shouldn't prevent the focus from
    // changing.
    private boolean mPreventDefaultMovement;

    private TextUtils.TruncateAt mEllipsize;

    private CharWrapper mCharWrapper;

    // Do not update following mText/mSpannable/mPrecomputed except for setTextInternal()
    @ViewDebug.ExportedProperty(category = "text")
    private @Nullable Editable mText;
    private @Nullable Spannable mSpannable;

    private CharSequence mTransformed;

    private CharSequence mHint;
    private Layout mHintLayout;

    private MovementMethod mMovement;

    private TransformationMethod mTransformation;
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

    /**
     * {@link EditText} specific data, created on demand when one of the Editor fields is used.
     */
    private final Editor mEditor = new Editor(this);

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

    /**
     * Interface definition for a callback to be invoked when an action is
     * performed on the editor.
     */
    public interface OnEditorActionListener {
        /**
         * Called when an action is being performed.
         *
         * @param v The view that was clicked.
         * @param actionId Identifier of the action.  This will be either the
         * identifier you supplied, or {@link EditorInfo#IME_NULL
         * EditorInfo.IME_NULL} if being called due to the enter key
         * being pressed.
         * @param event If triggered by an enter key, this is the event;
         * otherwise, this is null.
         * @return Return true if you have consumed the action, else false.
         */
        boolean onEditorAction(EditText v, int actionId, KeyEvent event);
    }

    public EditText(Context context) {
        this(context, null);
    }

    public EditText(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, android.R.attr.editTextStyle);
    }

    public EditText(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // TextView is important by default, unless app developer overrode attribute.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // autofill support started in O
            if (getImportantForAutofill() == IMPORTANT_FOR_AUTOFILL_AUTO) {
                setImportantForAutofill(IMPORTANT_FOR_AUTOFILL_YES);
            }
        }

        setTextInternal("");

        final Resources res = getResources();

        mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.density = res.getDisplayMetrics().density;

        mHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);


        mMovement = ArrowKeyMovementMethod.getInstance();

        final TextAppearanceAttributes attributes = new TextAppearanceAttributes();
        attributes.mTextColor = ColorStateList.valueOf(0xFF000000);
        attributes.mTextSize = 15;
//        mBreakStrategy = Layout.BREAK_STRATEGY_SIMPLE;
//        mHyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE;
        mJustificationMode = Layout.JUSTIFICATION_MODE_NONE;

        final Theme theme = context.getTheme();

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

        CharSequence digits = null;
        boolean selectAllOnFocus = false;
        Drawable drawableLeft = null, drawableTop = null, drawableRight = null,
                drawableBottom = null, drawableStart = null, drawableEnd = null;
        ColorStateList drawableTint = null;
        Mode drawableTintMode = null;
        int drawablePadding = 0;
        int ellipsize = ELLIPSIZE_NOT_SET;
        boolean singleLine = false;
        int maxlength = -1;
        CharSequence text = "";
        CharSequence hint = null;
        boolean password = false;
        int inputType = EditorInfo.TYPE_NULL;
        typedArray = theme.obtainStyledAttributes(
                attrs, R.styleable.EditText, defStyleAttr, /*defStyleRes*/0);
        int firstBaselineToTopHeight = -1;
        int lastBaselineToBottomHeight = -1;
        int lineHeight = -1;

        readTextAppearance(context, typedArray, attributes, true /* styleArray */);

        int attrCount = typedArray.getIndexCount();

        // Must set id in a temporary variable because it will be reset by setText()
        boolean textIsSetFromXml = false;
        for (int i = 0; i < attrCount; i++) {
            //TODO: (EW) get rid of the references to the deprecated attributes that we're skipping
            int attr = typedArray.getIndex(i);
            if (attr == R.styleable.EditText_android_editable) {
                // skipping - editable is deprecated: Use inputType instead
            } else if (attr == R.styleable.EditText_android_inputMethod) {
                // skipping - inputMethod is deprecated: Use inputType instead
            } else if (attr == R.styleable.EditText_android_numeric) {
                // skipping - numeric is deprecated: Use inputType instead
            } else if (attr == R.styleable.EditText_android_digits) {
                //TODO: probably implement - this supports more than just numbers (unsure if this is
                // intended functionality or just a hack). if this isn't added, at least add some
                // other means to filter input to only allow certain characters
                // If set, specifies that this TextView has a numeric input method and that these
                // specific characters are the ones that it will accept. If this is set, numeric is
                // implied to be true. The default is false.
            } else if (attr == R.styleable.EditText_android_phoneNumber) {
                // skipping - phoneNumber is deprecated: Use inputType instead
            } else if (attr == R.styleable.EditText_android_autoText) {
                // skipping - autoText is deprecated: Use inputType instead
            } else if (attr == R.styleable.EditText_android_capitalize) {
                //skipping - capitalize is deprecated: Use inputType instead
            } else if (attr == R.styleable.EditText_android_bufferType) {
                // skipping - EditText always returns Editable, even if you specify something less
                // powerful
            } else if (attr == R.styleable.EditText_android_selectAllOnFocus) {
                // If the text is selectable, select it all when the view takes focus.
                selectAllOnFocus = typedArray.getBoolean(attr, selectAllOnFocus);
            } else if (attr == R.styleable.EditText_android_autoLink) {
                // probably skip - EditText doesn't update the links as you type, so this doesn't
                // seem very beneficial
                // Controls whether links such as urls and email addresses are automatically found
                // and converted to clickable links. The default value is "none", disabling this
                // feature.
            } else if (attr == R.styleable.EditText_android_linksClickable) {
                // probably skip - If set to false, keeps the movement method from being set to the
                // link movement method even if autoLink causes links to be found.
            } else if (attr == R.styleable.EditText_android_drawableLeft) {
                // probably skip - The drawable to be drawn to the left of the text.
            } else if (attr == R.styleable.EditText_android_drawableTop) {
                // probably skip
            } else if (attr == R.styleable.EditText_android_drawableRight) {
                // probably skip
            } else if (attr == R.styleable.EditText_android_drawableBottom) {
                // probably skip
            } else if (attr == R.styleable.EditText_android_drawableStart) {
                // probably skip
            } else if (attr == R.styleable.EditText_android_drawableEnd) {
                // probably skip
//            } else if (&& attr == R.styleable.TextView_android_drawableTint) {
//                // probably skip
//                // can't use for some reason - maybe because it looks like it is just defined with
//                // an ID and not a type
//            } else if (attr == R.styleable.TextView_android_drawableTintMode) {
//                // probably skip
//                // can't use for some reason - maybe because it looks like it is just defined with
//                // an ID and not a type
            } else if (attr == R.styleable.EditText_android_drawablePadding) {
                // probably skip
            } else if (attr == R.styleable.EditText_android_maxLines) {
                // Makes the TextView be at most this many lines tall. The inputType attribute's
                // value must be combined with the textMultiLine flag for the maxLines attribute to
                // apply.
                setMaxLines(typedArray.getInt(attr, -1));
            } else if (attr == R.styleable.EditText_android_maxHeight) {
                // Makes the TextView be at most this many pixels tall.
                setMaxHeight(typedArray.getDimensionPixelSize(attr, -1));
            } else if (attr == R.styleable.EditText_android_lines) {
                // Makes the TextView be exactly this many lines tall.
                //TODO: this doesn't actually limit the number of lines (even in EditText) - it's
                // only the visual height - consider if it's worth not supporting
                setLines(typedArray.getInt(attr, -1));
            } else if (attr == R.styleable.EditText_android_height) {
                // Makes the TextView be exactly this tall. You could get the same effect by
                // specifying this number in the layout parameters.
                setHeight(typedArray.getDimensionPixelSize(attr, -1));
            } else if (attr == R.styleable.EditText_android_minLines) {
                // Makes the TextView be at least this many lines tall. The inputType attribute's
                // value must be combined with the textMultiLine flag for the minLines attribute to
                // apply.
                setMinLines(typedArray.getInt(attr, -1));
            } else if (attr == R.styleable.EditText_android_minHeight) {
                // Makes the TextView be at least this many pixels tall.
                setMinHeight(typedArray.getDimensionPixelSize(attr, -1));
            } else if (attr == R.styleable.EditText_android_maxEms) {
                // Makes the TextView be at most this many ems wide.
                setMaxEms(typedArray.getInt(attr, -1));
            } else if (attr == R.styleable.EditText_android_maxWidth) {
                // Makes the TextView be at most this many pixels wide.
                setMaxWidth(typedArray.getDimensionPixelSize(attr, -1));
            } else if (attr == R.styleable.EditText_android_ems) {
                // Makes the TextView be exactly this many ems wide.
                setEms(typedArray.getInt(attr, -1));
            } else if (attr == R.styleable.EditText_android_width) {
                // Makes the TextView be exactly this wide. You could get the same effect by
                // specifying this number in the layout parameters.
                setWidth(typedArray.getDimensionPixelSize(attr, -1));
            } else if (attr == R.styleable.EditText_android_minEms) {
                // Makes the TextView be at least this many ems wide.
                setMinEms(typedArray.getInt(attr, -1));
            } else if (attr == R.styleable.EditText_android_minWidth) {
                // Makes the TextView be at least this many pixels wide.
                setMinWidth(typedArray.getDimensionPixelSize(attr, -1));
            } else if (attr == R.styleable.EditText_android_gravity) {
                // Specifies how to align the text by the view's x- and/or y-axis when the text is
                // smaller than the view.
                setGravity(typedArray.getInt(attr, -1));
            } else if (attr == R.styleable.EditText_android_hint) {
                hint = typedArray.getText(attr);
            } else if (attr == R.styleable.EditText_android_text) {
                text = typedArray.getText(attr);
            } else if (attr == R.styleable.EditText_android_scrollHorizontally) {
                //skipping - doesn't seem to have any effect in an EditText
            } else if (attr == R.styleable.EditText_android_singleLine) {
                //skipping - deprecated. Use maxLines instead to change the layout of a static text,
                // and use the textMultiLine flag in the inputType attribute instead for editable
                // text views (if both singleLine and inputType are supplied, the inputType flags
                // will override the value of singleLine)
            } else if (attr == R.styleable.EditText_android_ellipsize) {
                // skipping - doesn't seem to work in EditText (although only marquee is called out
                // as not supported)
            } else if (attr == R.styleable.EditText_android_marqueeRepeatLimit) {
                // skipping - not supported in EditText
            } else if (attr == R.styleable.EditText_android_includeFontPadding) {
                // Leave enough room for ascenders and descenders instead of using the font ascent
                // and descent strictly. (Normally true).
                if (!typedArray.getBoolean(attr, true)) {
                    setIncludeFontPadding(false);
                }
            } else if (attr == R.styleable.EditText_android_cursorVisible) {
                //TODO: consider implementing if simple - not really sure why this would be wanted
                // Makes the cursor visible (the default) or invisible.
            } else if (attr == R.styleable.EditText_android_maxLength) {
                // Set an input filter to constrain the text length to the specified number.
                maxlength = typedArray.getInt(attr, -1);
            } else if (attr == R.styleable.EditText_android_textScaleX) {
                // Sets the horizontal scaling factor for the text.
                setTextScaleX(typedArray.getFloat(attr, 1.0f));
            } else if (attr == R.styleable.EditText_android_freezesText) {
                //skipping - If set, the text view will include its current complete text inside of
                // its frozen icicle in addition to meta-data such as the current cursor position.
                // By default this is disabled; it can be useful when the contents of a text view is
                // not stored in a persistent place such as a content provider. For
                // {@link android.widget.EditText} it is always enabled, regardless of the value of
                // the attribute.
            } else if (attr == R.styleable.EditText_android_enabled) {
                setEnabled(typedArray.getBoolean(attr, isEnabled()));
            } else if (attr == R.styleable.EditText_android_password) {
                //skipping - deprecated: Use inputType instead
            } else if (attr == R.styleable.EditText_android_lineSpacingExtra) {
                // Extra spacing between lines of text. The value will not be applied for the last
                // line of text.
                mSpacingAdd = typedArray.getDimensionPixelSize(attr, (int) mSpacingAdd);
            } else if (attr == R.styleable.EditText_android_lineSpacingMultiplier) {
                // Extra spacing between lines of text, as a multiplier. The value will not be
                // applied for the last line of text.
                mSpacingMult = typedArray.getFloat(attr, mSpacingMult);
            } else if (attr == R.styleable.EditText_android_inputType) {
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
            } else if (attr == R.styleable.EditText_android_allowUndo) {
                //TODO: consider implementing
                // Whether undo should be allowed for editable text. Defaults to true.
            } else if (attr == R.styleable.EditText_android_imeOptions) {
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
            } else if (attr == R.styleable.EditText_android_imeActionLabel) {
                // Supply a value for {@link android.view.inputmethod.EditorInfo#actionLabel
                // EditorInfo.actionLabel} used when an input method is connected to the text view.
                mEditor.createInputContentTypeIfNeeded();
                mEditor.mInputContentType.imeActionLabel = typedArray.getText(attr);
            } else if (attr == R.styleable.EditText_android_imeActionId) {
                // Supply a value for {@link android.view.inputmethod.EditorInfo#actionId
                // EditorInfo.actionId} used when an input method is connected to the text view.
                mEditor.createInputContentTypeIfNeeded();
                mEditor.mInputContentType.imeActionId = typedArray.getInt(attr,
                        mEditor.mInputContentType.imeActionId);
            } else if (attr == R.styleable.EditText_android_privateImeOptions) {
                // An addition content type description to supply to the input method attached to
                // the text view, which is private to the implementation of the input method. This
                // simply fills in the {@link android.view.inputmethod.EditorInfo#privateImeOptions
                // EditorInfo.privateImeOptions} field when the input method is connected.
                setPrivateImeOptions(typedArray.getString(attr));
            } else if (attr == R.styleable.EditText_android_editorExtras) {
                //skipping - this throws a NullPointerException even in the built-in EditText
            } else if (attr == R.styleable.EditText_android_textCursorDrawable) {
                mCursorDrawableRes = typedArray.getResourceId(attr, 0);
            } else if (attr == R.styleable.EditText_android_textSelectHandleLeft) {
                mTextSelectHandleLeftRes = typedArray.getResourceId(attr, 0);
            } else if (attr == R.styleable.EditText_android_textSelectHandleRight) {
                mTextSelectHandleRightRes = typedArray.getResourceId(attr, 0);
            } else if (attr == R.styleable.EditText_android_textSelectHandle) {
                mTextSelectHandleRes = typedArray.getResourceId(attr, 0);
            } else if (attr == R.styleable.EditText_android_textEditSuggestionItemLayout) {
                //skipping - Layout of the TextView item that will populate the suggestion
                // popup window.
//            } else if (attr == R.styleable.EditText_android_textEditSuggestionContainerLayout) {
//                //skipping - textEditSuggestionContainerLayout is private
//            } else if (attr == R.styleable.EditText_android_textEditSuggestionHighlightStyle) {
//                //skipping - textEditSuggestionHighlightStyle is private
            } else if (attr == R.styleable.EditText_android_textIsSelectable) {
                //skipping - doesn't seem to have any effect in an EditText
            } else if (attr == R.styleable.EditText_android_breakStrategy) {
                //skipping - Break strategy (control over paragraph layout).
                //TODO: verify if this could be useful
                // EditText defaults to Layout#BREAK_STRATEGY_SIMPLE to avoid the text "dancing"
                // when being edited
            } else if (attr == R.styleable.EditText_android_hyphenationFrequency) {
                //skipping - Frequency of automatic hyphenation
                //TODO: verify if this could be useful
            } else if (attr == R.styleable.EditText_android_autoSizeTextType) {
                //skipping - this feature is not supported by EditText
            } else if (attr == R.styleable.EditText_android_autoSizeStepGranularity) {
                //skipping - this feature is not supported by EditText
            } else if (attr == R.styleable.EditText_android_autoSizeMinTextSize) {
                //skipping - this feature is not supported by EditText
            } else if (attr == R.styleable.EditText_android_autoSizeMaxTextSize) {
                //skipping - this feature is not supported by EditText
            } else if (attr == R.styleable.EditText_android_autoSizePresetSizes) {
                //skipping - this feature is not supported by EditText
            } else if (attr == R.styleable.EditText_android_justificationMode) {
                // Mode for justification.
                //TODO: this doesn't apply the justification as you type (even in EditText), so it
                // may be better to just remove support for this
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mJustificationMode = typedArray.getInt(attr, Layout.JUSTIFICATION_MODE_NONE);
                }
            } else if (attr == R.styleable.EditText_android_firstBaselineToTopHeight) {
                // Distance from the top of the TextView to the first text baseline. If set, this
                // overrides the value set for paddingTop.
                // only used in API level 28 and higher
                firstBaselineToTopHeight = typedArray.getDimensionPixelSize(attr, -1);
            } else if (attr == R.styleable.EditText_android_lastBaselineToBottomHeight) {
                // Distance from the bottom of the TextView to the last text baseline. If set, this
                // overrides the value set for paddingBottom.
                // only used in API level 28 and higher
                lastBaselineToBottomHeight = typedArray.getDimensionPixelSize(attr, -1);
            } else if (attr == R.styleable.EditText_android_lineHeight) {
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
        } else {
            mEditor.mKeyListener = TextKeyListener.getInstance();
            mEditor.mInputType = EditorInfo.TYPE_CLASS_TEXT;
        }

        mEditor.adjustInputType(password, passwordInputType, webPasswordInputType,
                numberPasswordInputType);

        if (selectAllOnFocus) {
            mEditor.mSelectAllOnFocus = true;
        }

        // Same as setSingleLine(), but make sure the transformation method and the maximum number
        // of lines of height are unchanged for multi-line TextViews.
        setInputTypeSingleLine(singleLine);
        applySingleLine(singleLine, singleLine, singleLine);

        final boolean isPassword = password || passwordInputType || webPasswordInputType
                || numberPasswordInputType;
        final boolean isMonospaceEnforced = isPassword || ((mEditor.mInputType
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
            setFilters(new InputFilter[] { new LengthFilter(maxlength) });
        } else {
            setFilters(NO_FILTERS);
        }

        setText(text);
        if (textIsSetFromXml) {
            mTextSetFromXmlOrResourceId = true;
        }

        if (hint != null) setHint(hint);

        /*
         * Views are not normally clickable unless specified to be.
         * However, TextViews that have input or movement methods *are*
         * clickable by default. By setting clickable here, we implicitly set focusable as well
         * if not overridden by the developer.
         */
        //TODO: (EW) can this get merged with the other typedArray styleable
        typedArray = context.obtainStyledAttributes(
                attrs, /*com.android.internal.*/R.styleable.View, defStyleAttr, /*defStyleRes*/0);
        boolean canInputOrMove = (mMovement != null || getKeyListener() != null);
        boolean clickable = canInputOrMove || isClickable();
        boolean longClickable = canInputOrMove || isLongClickable();
        int focusable;
        boolean isFocusable;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusable = getFocusable();
            // doesn't matter. this won't be used. pacify java
            isFocusable = false;
        } else {
            isFocusable = mMovement != null || getKeyListener() != null;
            // doesn't matter. this won't be used. pacify java
            focusable = 0;
        }

        attrCount = typedArray.getIndexCount();
        for (int i = 0; i < attrCount; i++) {
            int attr = typedArray.getIndex(i);
            if (attr == R.styleable.View_android_focusable) {
                TypedValue val = new TypedValue();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (typedArray.getValue(attr, val)) {
                        focusable = (val.type == TypedValue.TYPE_INT_BOOLEAN)
                                ? (val.data == 0 ? NOT_FOCUSABLE : FOCUSABLE)
                                : val.data;
                    }
                } else {
                    //TODO: (EW) verify this works
                    isFocusable = typedArray.getBoolean(attr, isFocusable);
                }
            } else if (attr == R.styleable.View_android_clickable) {
                clickable = typedArray.getBoolean(attr, clickable);
            } else if (attr == R.styleable.View_android_longClickable) {
                longClickable = typedArray.getBoolean(attr, longClickable);
            }
        }
        typedArray.recycle();

        // Some apps were relying on the undefined behavior of focusable winning over
        // focusableInTouchMode != focusable in TextViews if both were specified in XML (usually
        // when starting with EditText and setting only focusable=false). To keep those apps from
        // breaking, re-apply the focusable attribute here.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusable != getFocusable()) {
                setFocusable(focusable);
            }
        } else {
            setFocusable(isFocusable);
        }
        setClickable(clickable);
        setLongClickable(longClickable);

//        // these seem to be necessary only when the activity isn't appcompat
//        //TODO: investigate this more. are these getting set in the aosp version somewhere else?
//        // what is appcompat doing that doesn't require this?
////        setEnabled(true);
//        setClickable(true);
////        setFocusable(true);
//        setFocusableInTouchMode(true);
//
//        setFocusable(true);
//        setClickable(true);
//        setLongClickable(true);

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
    }

    // Update mText
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
    private void resolveStyleAndSetTypeface(@NonNull Typeface typeface, int style, int weight) {
        if (weight >= 0) {
            final boolean italic = (style & Typeface.ITALIC) != 0;
            setTypeface(Typeface.create(typeface, weight, italic));
        } else {
            setTypeface(typeface, style);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
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
        //TODO: (EW) mEditor.invalidateTextDisplayList doesn't exist, but should some invalidation
        // be done? I'm not sure if the text color change comment is accurate without this
        mEditor.prepareCursorControllers();

        // start or stop the cursor blinking as appropriate
        mEditor.makeBlink();
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
    public void setTypeface(@Nullable Typeface tf, int style) {
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
     * Return the text that EditText is displaying.
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
     * Returns the length, in characters, of the text managed by this TextView
     * @return The length of the text managed by the TextView in characters.
     */
    public int length() {
        return mText.length();
    }

    //TODO: (EW) remove - this is redundant. keeping for now to avoid breaking copy/pasted code
    /**
     * Return the text that TextView is displaying as an Editable object. If the text is not
     * editable, null is returned.
     *
     * @see #getText
     */
    public Editable getEditableText() {
        return getText();
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
        return mEditor.mKeyListener;
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
            mEditor.mInputType = EditorInfo.TYPE_NULL;
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
        if (mEditor.mKeyListener != input) {
            mEditor.mKeyListener = input;
            if (input != null && !(mText instanceof Editable)) {
                setText(mText);
            }

            setFilters((Editable) mText, mFilters);
        }
    }

    /**
     * Gets the {@link MovementMethod} being used for this TextView,
     * which provides positioning, scrolling, and text selection functionality.
     * This will frequently be null for non-EditText TextViews.
     * @return the movement method being used for this TextView.
     * @see MovementMethod
     */
    public final MovementMethod getMovementMethod() {
        return mMovement;
    }

    /**
     * Sets the {@link MovementMethod} for handling arrow key movement
     * for this TextView. This can be null to disallow using the arrow keys to move the
     * cursor or scroll the view.
     * <p>
     * Be warned that if you want a TextView with a key listener or movement
     * method not to be focusable, or if you want a TextView without a
     * key listener or movement method to be focusable, you must call
     * {@link #setFocusable} again after calling this to get the focusability
     * back the way you want it.
     */
    public final void setMovementMethod(MovementMethod movement) {
        if (mMovement != movement) {
            mMovement = movement;

            if (movement != null && mSpannable == null) {
                setText(mText);
            }

            fixFocusableAndClickableSettings();

            // SelectionModifierCursorController depends on textCanBeSelected, which depends on
            // mMovement
            mEditor.prepareCursorControllers();
        }
    }

    private void fixFocusableAndClickableSettings() {
        if (mMovement != null || mEditor.mKeyListener != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setFocusable(FOCUSABLE);
            } else {
                setFocusable(true);
            }
            setClickable(true);
            setLongClickable(true);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setFocusable(FOCUSABLE_AUTO);
            } else {
                setFocusable(false);
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

        setText(mText);

        if (hasPasswordTransformationMethod()) {
            //TODO: (EW) does accessibility need to be handled, or can this just be ignored?
//            notifyViewAccessibilityStateChangedIfNeeded(
//                    AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED);
        }

        // PasswordTransformationMethod always have LTR text direction heuristics returned by
        // getTextDirectionHeuristic, needs reset
        mTextDir = getTextDirectionHeuristic();
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
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
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
        Preconditions.checkArgumentNonnegative(firstBaselineToTopHeight);

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
        Preconditions.checkArgumentNonnegative(lastBaselineToBottomHeight);

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
     * Returns the distance between the first text baseline and the top of this TextView.
     *
     * @see #setFirstBaselineToTopHeight(int)
     * @attr ref android.R.styleable#TextView_firstBaselineToTopHeight
     */
    public int getFirstBaselineToTopHeight() {
        return getPaddingTop() - getPaint().getFontMetricsInt().top;
    }

    /**
     * Returns the distance between the last text baseline and the bottom of this TextView.
     *
     * @see #setLastBaselineToBottomHeight(int)
     * @attr ref android.R.styleable#TextView_lastBaselineToBottomHeight
     */
    public int getLastBaselineToBottomHeight() {
        return getPaddingBottom() + getPaint().getFontMetricsInt().bottom;
    }

    /**
     * Sets the text appearance from the specified style resource.
     * <p>
     * Use a framework-defined {@code TextAppearance} style like
     * {@link android.R.style#TextAppearance_Material_Body1 @android:style/TextAppearance.Material.Body1}
     * or see {@link R.styleable#TextAppearance TextAppearance} for the
     * set of attributes that can be used in a custom style.
     *
     * @param resId the resource identifier of the style to apply
     * @attr ref android.R.styleable#TextView_textAppearance
     */
    public void setTextAppearance(@StyleRes int resId) {
        Context context = getContext();
        final TypedArray ta = context.obtainStyledAttributes(resId, R.styleable.TextAppearance);
        final TextAppearanceAttributes attributes = new TextAppearanceAttributes();
        readTextAppearance(context, ta, attributes, false /* styleArray */);
        ta.recycle();
        applyTextAppearance(attributes);
    }

    /**
     * Set of attributes that can be defined in a Text Appearance. This is used to simplify the code
     * that reads these attributes in the constructor and in {@link #setTextAppearance}.
     */
    private static class TextAppearanceAttributes {
        int mTextColorHighlight = 0;
        ColorStateList mTextColor = null;
        ColorStateList mTextColorHint = null;
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
        sAppearanceValues.put(R.styleable.EditText_android_textColorHighlight,
                R.styleable.TextAppearance_android_textColorHighlight);
        sAppearanceValues.put(R.styleable.EditText_android_textColor,
                R.styleable.TextAppearance_android_textColor);
        sAppearanceValues.put(R.styleable.EditText_android_textColorHint,
                R.styleable.TextAppearance_android_textColorHint);
        sAppearanceValues.put(R.styleable.EditText_android_textColorLink,
                R.styleable.TextAppearance_android_textColorLink);
        sAppearanceValues.put(R.styleable.EditText_android_textSize,
                R.styleable.TextAppearance_android_textSize);
        sAppearanceValues.put(R.styleable.EditText_android_typeface,
                R.styleable.TextAppearance_android_typeface);
        sAppearanceValues.put(R.styleable.EditText_android_fontFamily,
                R.styleable.TextAppearance_android_fontFamily);
        sAppearanceValues.put(R.styleable.EditText_android_textStyle,
                R.styleable.TextAppearance_android_textStyle);
        sAppearanceValues.put(R.styleable.EditText_android_textFontWeight,
                R.styleable.TextAppearance_android_textFontWeight);
        sAppearanceValues.put(R.styleable.EditText_android_textAllCaps,
                R.styleable.TextAppearance_android_textAllCaps);
        sAppearanceValues.put(R.styleable.EditText_android_shadowColor,
                R.styleable.TextAppearance_android_shadowColor);
        sAppearanceValues.put(R.styleable.EditText_android_shadowDx,
                R.styleable.TextAppearance_android_shadowDx);
        sAppearanceValues.put(R.styleable.EditText_android_shadowDy,
                R.styleable.TextAppearance_android_shadowDy);
        sAppearanceValues.put(R.styleable.EditText_android_shadowRadius,
                R.styleable.TextAppearance_android_shadowRadius);
        sAppearanceValues.put(R.styleable.EditText_android_elegantTextHeight,
                R.styleable.TextAppearance_android_elegantTextHeight);
        sAppearanceValues.put(R.styleable.EditText_android_fallbackLineSpacing,
                R.styleable.TextAppearance_android_fallbackLineSpacing);
        sAppearanceValues.put(R.styleable.EditText_android_letterSpacing,
                R.styleable.TextAppearance_android_letterSpacing);
        sAppearanceValues.put(R.styleable.EditText_android_fontFeatureSettings,
                R.styleable.TextAppearance_android_fontFeatureSettings);
    }

    /**
     * Read the Text Appearance attributes from a given TypedArray and set its values to the given
     * set. If the TypedArray contains a value that was already set in the given attributes, that
     * will be overridden.
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
            } else if (index == R.styleable.TextAppearance_android_textSize) {
                attributes.mTextSize =
                        appearance.getDimensionPixelSize(attr, attributes.mTextSize);
            } else if (index == R.styleable.TextAppearance_android_typeface) {
                attributes.mTypefaceIndex = appearance.getInt(attr, attributes.mTypefaceIndex);
                if (attributes.mTypefaceIndex != -1 && !attributes.mFontFamilyExplicit) {
                    attributes.mFontFamily = null;
                }
            } else if (index == R.styleable.TextAppearance_android_fontFamily) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    //TODO: (EW) can we just ignore the canLoadUnsafeResources check?
                    if (!context.isRestricted()/* && context.canLoadUnsafeResources()*/) {
                        try {
                                attributes.mFontTypeface = appearance.getFont(attr);
                        } catch (UnsupportedOperationException | Resources.NotFoundException e) {
                            // Expected if it is not a font resource.
                        }
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
                // (EW) only used in API level 21 and higher
                attributes.mHasElegant = true;
                attributes.mElegant = appearance.getBoolean(attr, attributes.mElegant);
            } else if (index == R.styleable.TextAppearance_android_fallbackLineSpacing) {
                attributes.mHasFallbackLineSpacing = true;
                attributes.mFallbackLineSpacing = appearance.getBoolean(attr,
                        attributes.mFallbackLineSpacing);
            } else if (index == R.styleable.TextAppearance_android_letterSpacing) {
                // (EW) only used in API level 21 and higher
                attributes.mHasLetterSpacing = true;
                attributes.mLetterSpacing =
                        appearance.getFloat(attr, attributes.mLetterSpacing);
            } else if (index == R.styleable.TextAppearance_android_fontFeatureSettings) {
                // (EW) only used in API level 21 and higher
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
            //TODO: (EW) figure out if we can just use the restricted androidx version or need to
            // copy the aosp version
//            setTransformationMethod(new AllCapsTransformationMethod(getContext()));
        }

        if (attributes.mHasElegant && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setElegantTextHeight(attributes.mElegant);
        }

        if (attributes.mHasFallbackLineSpacing) {
            setFallbackLineSpacing(attributes.mFallbackLineSpacing);
        }

        if (attributes.mHasLetterSpacing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setLetterSpacing(attributes.mLetterSpacing);
        }

        if (attributes.mFontFeatureSettings != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void changeListenerLocaleTo(@Nullable Locale locale) {
        if (mListenerChanged) {
            // If a listener has been explicitly set, don't change it. We may break something.
            return;
        }
        // The following null check is not absolutely necessary since all calling points of
        // changeListenerLocaleTo() guarantee a non-null mEditor at the moment. But this is left
        // here in case others would want to call this method in the future.
        KeyListener listener = mEditor.mKeyListener;
        if (listener instanceof DigitsKeyListener) {
            //TODO: (EW) figure out how to handle
//            listener = DigitsKeyListener.getInstance(locale, (DigitsKeyListener) listener);
        } else if (listener instanceof DateKeyListener) {
            listener = DateKeyListener.getInstance(locale);
        } else if (listener instanceof TimeKeyListener) {
            listener = TimeKeyListener.getInstance(locale);
        } else if (listener instanceof DateTimeKeyListener) {
            listener = DateTimeKeyListener.getInstance(locale);
        } else {
            return;
        }
        final boolean wasPasswordType = isPasswordInputType(mEditor.mInputType);
        setKeyListenerOnly(listener);
        setInputTypeFromEditor();
        if (wasPasswordType) {
            final int newInputClass = mEditor.mInputType & EditorInfo.TYPE_MASK_CLASS;
            if (newInputClass == EditorInfo.TYPE_CLASS_TEXT) {
                mEditor.mInputType |= EditorInfo.TYPE_TEXT_VARIATION_PASSWORD;
            } else if (newInputClass == EditorInfo.TYPE_CLASS_NUMBER) {
                mEditor.mInputType |= EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD;
            }
        }
    }

    /**
     * Set the default {@link Locale} of the text in this TextView to a one-member
     * {@link LocaleList} containing just the given Locale.
     *
     * @param locale the {@link Locale} for drawing text, must not be null.
     *
     * @see #setTextLocales
     */
    public void setTextLocale(@NonNull Locale locale) {
        mLocalesChanged = true;
        mTextPaint.setTextLocale(locale);
        if (mLayout != null) {
            nullLayouts();
            requestLayout();
            invalidate();
        }
    }

    /**
     * Set the default {@link LocaleList} of the text in this TextView to the given value.
     *
     * This value is used to choose appropriate typefaces for ambiguous characters (typically used
     * for CJK locales to disambiguate Hanzi/Kanji/Hanja characters). It also affects
     * other aspects of text display, including line breaking.
     *
     * @param locales the {@link LocaleList} for drawing text, must not be null or empty.
     *
     * @see Paint#setTextLocales
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void setTextLocales(@NonNull @Size(min = 1) LocaleList locales) {
        mLocalesChanged = true;
        mTextPaint.setTextLocales(locales);
        if (mLayout != null) {
            nullLayouts();
            requestLayout();
            invalidate();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!mLocalesChanged) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mTextPaint.setTextLocales(LocaleList.getDefault());
            } else {
                mTextPaint.setTextLocale(Locale.getDefault());
            }
            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * @return the size (in pixels) of the default text size in this TextView.
     */
    @ViewDebug.ExportedProperty(category = "text")
    public float getTextSize() {
        return mTextPaint.getTextSize();
    }

    /** @hide */
    @ViewDebug.ExportedProperty(category = "text", mapping = {
            @ViewDebug.IntToString(from = Typeface.NORMAL, to = "NORMAL"),
            @ViewDebug.IntToString(from = Typeface.BOLD, to = "BOLD"),
            @ViewDebug.IntToString(from = Typeface.ITALIC, to = "ITALIC"),
            @ViewDebug.IntToString(from = Typeface.BOLD_ITALIC, to = "BOLD_ITALIC")
    })
    public int getTypefaceStyle() {
        Typeface typeface = mTextPaint.getTypeface();
        return typeface != null ? typeface.getStyle() : Typeface.NORMAL;
    }

    /**
     * Set the default text size to the given value, interpreted as "scaled
     * pixel" units.  This size is adjusted based on the current density and
     * user font size preference.
     *
     * <p>Note: if this TextView has the auto-size feature enabled than this function is no-op.
     *
     * @param size The scaled pixel size.
     *
     * @attr ref android.R.styleable#TextView_textSize
     */
    public void setTextSize(float size) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
    }

    /**
     * Set the default text size to a given unit and value. See {@link
     * TypedValue} for the possible dimension units.
     *
     * <p>Note: if this TextView has the auto-size feature enabled than this function is no-op.
     *
     * @param unit The desired dimension unit.
     * @param size The desired size in the given units.
     *
     * @attr ref android.R.styleable#TextView_textSize
     */
    public void setTextSize(int unit, float size) {
        setTextSizeInternal(unit, size, true /* shouldRequestLayout */);
    }

    private void setTextSizeInternal(int unit, float size, boolean shouldRequestLayout) {
        Context c = getContext();
        Resources r;

        if (c == null) {
            r = Resources.getSystem();
        } else {
            r = c.getResources();
        }

        setRawTextSize(TypedValue.applyDimension(unit, size, r.getDisplayMetrics()),
                shouldRequestLayout);
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
     * Gets the current {@link Typeface} that is used to style the text.
     * @return The current Typeface.
     *
     * @see #setTypeface(Typeface)
     *
     * @attr ref android.R.styleable#TextView_fontFamily
     * @attr ref android.R.styleable#TextView_typeface
     * @attr ref android.R.styleable#TextView_textStyle
     */
    public Typeface getTypeface() {
        return mTextPaint.getTypeface();
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
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
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
     * @return whether fallback line spacing is enabled, {@code true} by default
     *
     * @see #setFallbackLineSpacing(boolean)
     *
     * @attr ref android.R.styleable#TextView_fallbackLineSpacing
     */
    public boolean isFallbackLineSpacing() {
        return mUseFallbackLineSpacing;
    }

    /**
     * Get the value of the TextView's elegant height metrics flag. This setting selects font
     * variants that have not been compacted to fit Latin-based vertical
     * metrics, and also increases top and bottom bounds to provide more space.
     * @return {@code true} if the elegant height metrics flag is set.
     *
     * @see #setElegantTextHeight(boolean)
     * @see Paint#setElegantTextHeight(boolean)
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public boolean isElegantTextHeight() {
        return mTextPaint.isElegantTextHeight();
    }

    /**
     * Gets the text letter-space value, which determines the spacing between characters.
     * The value returned is in ems. Normally, this value is 0.0.
     * @return The text letter-space value in ems.
     *
     * @see #setLetterSpacing(float)
     * @see Paint#setLetterSpacing
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public float getLetterSpacing() {
        return mTextPaint.getLetterSpacing();
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
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
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
     * Returns the font feature settings. The format is the same as the CSS
     * font-feature-settings attribute:
     * <a href="https://www.w3.org/TR/css-fonts-3/#font-feature-settings-prop">
     *     https://www.w3.org/TR/css-fonts-3/#font-feature-settings-prop</a>
     *
     * @return the currently set font feature settings.  Default is null.
     *
     * @see #setFontFeatureSettings(String)
     * @see Paint#setFontFeatureSettings(String) Paint.setFontFeatureSettings(String)
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Nullable
    public String getFontFeatureSettings() {
        return mTextPaint.getFontFeatureSettings();
    }

    /**
     * Returns the font variation settings.
     *
     * @return the currently set font variation settings.  Returns null if no variation is
     * specified.
     *
     * @see #setFontVariationSettings(String)
     * @see Paint#setFontVariationSettings(String) Paint.setFontVariationSettings(String)
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Nullable
    public String getFontVariationSettings() {
        return mTextPaint.getFontVariationSettings();
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
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
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
     * Sets TrueType or OpenType font variation settings. The settings string is constructed from
     * multiple pairs of axis tag and style values. The axis tag must contain four ASCII characters
     * and must be wrapped with single quotes (U+0027) or double quotes (U+0022). Axis strings that
     * are longer or shorter than four characters, or contain characters outside of U+0020..U+007E
     * are invalid. If a specified axis name is not defined in the font, the settings will be
     * ignored.
     *
     * <p>
     * Examples,
     * <ul>
     * <li>Set font width to 150.
     * <pre>
     * <code>
     *   TextView textView = (TextView) findViewById(R.id.textView);
     *   textView.setFontVariationSettings("'wdth' 150");
     * </code>
     * </pre>
     * </li>
     *
     * <li>Set the font slant to 20 degrees and ask for italic style.
     * <pre>
     * <code>
     *   TextView textView = (TextView) findViewById(R.id.textView);
     *   textView.setFontVariationSettings("'slnt' 20, 'ital' 1");
     * </code>
     * </pre>
     * </p>
     * </li>
     * </ul>
     *
     * @param fontVariationSettings font variation settings. You can pass null or empty string as
     *                              no variation settings.
     * @return true if the given settings is effective to at least one font file underlying this
     *         TextView. This function also returns true for empty settings string. Otherwise
     *         returns false.
     *
     * @throws IllegalArgumentException If given string is not a valid font variation settings
     *                                  format.
     *
     * @see #getFontVariationSettings()
     * @see FontVariationAxis
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public boolean setFontVariationSettings(@Nullable String fontVariationSettings) {
        final String existingSettings = mTextPaint.getFontVariationSettings();
        if (fontVariationSettings == existingSettings
                || (fontVariationSettings != null
                        && fontVariationSettings.equals(existingSettings))) {
            return true;
        }
        boolean effective = mTextPaint.setFontVariationSettings(fontVariationSettings);

        if (effective && mLayout != null) {
            nullLayouts();
            requestLayout();
            invalidate();
        }
        return effective;
    }

    /**
     * Sets the text color for all the states (normal, selected,
     * focused) to be this color.
     *
     * @param color A color value in the form 0xAARRGGBB.
     * Do not pass a resource ID. To get a color value from a resource ID, call
     * {@link ContextCompat#getColor(Context, int) getColor}.
     *
     * @see #setTextColor(ColorStateList)
     * @see #getTextColors()
     *
     * @attr ref android.R.styleable#TextView_textColor
     */
    public void setTextColor(@ColorInt int color) {
        mTextColor = ColorStateList.valueOf(color);
        updateTextColors();
    }

    /**
     * Sets the text color.
     *
     * @see #setTextColor(int)
     * @see #getTextColors()
     * @see #setHintTextColor(ColorStateList)
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
     * Gets the text colors for the different states (normal, selected, focused) of the TextView.
     *
     * @see #setTextColor(ColorStateList)
     * @see #setTextColor(int)
     *
     * @attr ref android.R.styleable#TextView_textColor
     */
    public final ColorStateList getTextColors() {
        return mTextColor;
    }

    /**
     * Return the current color selected for normal text.
     *
     * @return Returns the current text color.
     */
    @ColorInt
    public final int getCurrentTextColor() {
        return mCurTextColor;
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
     * @return the color used to display the selection highlight
     *
     * @see #setHighlightColor(int)
     *
     * @attr ref android.R.styleable#TextView_textColorHighlight
     */
    @ColorInt
    public int getHighlightColor() {
        return mHighlightColor;
    }

    /**
     * Sets whether the soft input method will be made visible when this
     * TextView gets focused. The default is true.
     */
    public final void setShowSoftInputOnFocus(boolean show) {
        mEditor.mShowSoftInputOnFocus = show;
    }

    /**
     * Returns whether the soft input method will be made visible when this
     * TextView gets focused. The default is true.
     */
    public final boolean getShowSoftInputOnFocus() {
        // When there is no Editor, return default true value
        return mEditor.mShowSoftInputOnFocus;
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
        mEditor.invalidateHandlesAndActionMode();
        invalidate();
    }

    /**
     * Gets the radius of the shadow layer.
     *
     * @return the radius of the shadow layer. If 0, the shadow layer is not visible
     *
     * @see #setShadowLayer(float, float, float, int)
     *
     * @attr ref android.R.styleable#TextView_shadowRadius
     */
    public float getShadowRadius() {
        return mShadowRadius;
    }

    /**
     * @return the horizontal offset of the shadow layer
     *
     * @see #setShadowLayer(float, float, float, int)
     *
     * @attr ref android.R.styleable#TextView_shadowDx
     */
    public float getShadowDx() {
        return mShadowDx;
    }

    /**
     * Gets the vertical offset of the shadow layer.
     * @return The vertical offset of the shadow layer.
     *
     * @see #setShadowLayer(float, float, float, int)
     *
     * @attr ref android.R.styleable#TextView_shadowDy
     */
    public float getShadowDy() {
        return mShadowDy;
    }

    /**
     * Gets the color of the shadow layer.
     * @return the color of the shadow layer
     *
     * @see #setShadowLayer(float, float, float, int)
     *
     * @attr ref android.R.styleable#TextView_shadowColor
     */
    @ColorInt
    public int getShadowColor() {
        return mShadowColor;
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
     * Sets the color of the hint text for all the states (disabled, focussed, selected...) of this
     * TextView.
     *
     * @see #setHintTextColor(ColorStateList)
     * @see #getHintTextColors()
     * @see #setTextColor(int)
     *
     * @attr ref android.R.styleable#TextView_textColorHint
     */
    public final void setHintTextColor(@ColorInt int color) {
        mHintTextColor = ColorStateList.valueOf(color);
        updateTextColors();
    }

    /**
     * Sets the color of the hint text.
     *
     * @see #getHintTextColors()
     * @see #setHintTextColor(int)
     * @see #setTextColor(ColorStateList)
     *
     * @attr ref android.R.styleable#TextView_textColorHint
     */
    public final void setHintTextColor(ColorStateList colors) {
        mHintTextColor = colors;
        updateTextColors();
    }

    /**
     * @return the color of the hint text, for the different states of this TextView.
     *
     * @see #setHintTextColor(ColorStateList)
     * @see #setHintTextColor(int)
     * @see #setTextColor(ColorStateList)
     *
     * @attr ref android.R.styleable#TextView_textColorHint
     */
    public final ColorStateList getHintTextColors() {
        return mHintTextColor;
    }

    /**
     * <p>Return the current color selected to paint the hint text.</p>
     *
     * @return Returns the current hint text color.
     */
    @ColorInt
    public final int getCurrentHintTextColor() {
        return mHintTextColor != null ? mCurHintTextColor : mCurTextColor;
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
     * Gets the flags on the Paint being used to display the text.
     * @return The flags on the Paint being used to display the text.
     * @see Paint#getFlags
     */
    public int getPaintFlags() {
        return mTextPaint.getFlags();
    }

    /**
     * Sets flags on the Paint being used to display the text and
     * reflows the text if they are different from the old flags.
     * @see Paint#setFlags
     */
    public void setPaintFlags(int flags) {
        if (mTextPaint.getFlags() != flags) {
            mTextPaint.setFlags(flags);

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
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
     * as {@link #setMinHeight(int)} or {@link #setHeight(int)}.
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
     * Returns the minimum height of TextView in terms of number of lines or -1 if the minimum
     * height was set using {@link #setMinHeight(int)} or {@link #setHeight(int)}.
     *
     * @return the minimum height of TextView in terms of number of lines or -1 if the minimum
     *         height is not defined in lines
     *
     * @see #setMinLines(int)
     * @see #setLines(int)
     *
     * @attr ref android.R.styleable#TextView_minLines
     */
    public int getMinLines() {
        return mMinMode == LINES ? mMinimum : -1;
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
     * Returns the minimum height of TextView in terms of pixels or -1 if the minimum height was
     * set using {@link #setMinLines(int)} or {@link #setLines(int)}.
     *
     * @return the minimum height of TextView in terms of pixels or -1 if the minimum height is not
     *         defined in pixels
     *
     * @see #setMinHeight(int)
     * @see #setHeight(int)
     *
     * @attr ref android.R.styleable#TextView_minHeight
     */
    public int getMinHeight() {
        return mMinMode == PIXELS ? mMinimum : -1;
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
     * Returns the maximum height of TextView in terms of number of lines or -1 if the
     * maximum height was set using {@link #setMaxHeight(int)} or {@link #setHeight(int)}.
     *
     * @return the maximum height of TextView in terms of number of lines. -1 if the maximum height
     *         is not defined in lines.
     *
     * @see #setMaxLines(int)
     * @see #setLines(int)
     *
     * @attr ref android.R.styleable#TextView_maxLines
     */
    public int getMaxLines() {
        return mMaxMode == LINES ? mMaximum : -1;
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
     * Returns the maximum height of TextView in terms of pixels or -1 if the maximum height was
     * set using {@link #setMaxLines(int)} or {@link #setLines(int)}.
     *
     * @return the maximum height of TextView in terms of pixels or -1 if the maximum height
     *         is not defined in pixels
     *
     * @see #setMaxHeight(int)
     * @see #setHeight(int)
     *
     * @attr ref android.R.styleable#TextView_maxHeight
     */
    public int getMaxHeight() {
        return mMaxMode == PIXELS ? mMaximum : -1;
    }

    /**
     * Sets the height of the TextView to be exactly {@code lines} tall.
     * <p>
     * This value is used for height calculation if LayoutParams does not force TextView to have an
     * exact height. Setting this value overrides previous minimum/maximum height configurations
     * such as {@link #setMinLines(int)} or {@link #setMaxLines(int)}.
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
     * Returns the minimum width of TextView in terms of ems or -1 if the minimum width was set
     * using {@link #setMinWidth(int)} or {@link #setWidth(int)}.
     *
     * @return the minimum width of TextView in terms of ems. -1 if the minimum width is not
     *         defined in ems
     *
     * @see #setMinEms(int)
     * @see #setEms(int)
     *
     * @attr ref android.R.styleable#TextView_minEms
     */
    public int getMinEms() {
        return mMinWidthMode == EMS ? mMinWidth : -1;
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
     * Returns the minimum width of TextView in terms of pixels or -1 if the minimum width was set
     * using {@link #setMinEms(int)} or {@link #setEms(int)}.
     *
     * @return the minimum width of TextView in terms of pixels or -1 if the minimum width is not
     *         defined in pixels
     *
     * @see #setMinWidth(int)
     * @see #setWidth(int)
     *
     * @attr ref android.R.styleable#TextView_minWidth
     */
    public int getMinWidth() {
        return mMinWidthMode == PIXELS ? mMinWidth : -1;
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
     * Returns the maximum width of TextView in terms of ems or -1 if the maximum width was set
     * using {@link #setMaxWidth(int)} or {@link #setWidth(int)}.
     *
     * @return the maximum width of TextView in terms of ems or -1 if the maximum width is not
     *         defined in ems
     *
     * @see #setMaxEms(int)
     * @see #setEms(int)
     *
     * @attr ref android.R.styleable#TextView_maxEms
     */
    public int getMaxEms() {
        return mMaxWidthMode == EMS ? mMaxWidth : -1;
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
     * Returns the maximum width of TextView in terms of pixels or -1 if the maximum width was set
     * using {@link #setMaxEms(int)} or {@link #setEms(int)}.
     *
     * @return the maximum width of TextView in terms of pixels. -1 if the maximum width is not
     *         defined in pixels
     *
     * @see #setMaxWidth(int)
     * @see #setWidth(int)
     *
     * @attr ref android.R.styleable#TextView_maxWidth
     */
    public int getMaxWidth() {
        return mMaxWidthMode == PIXELS ? mMaxWidth : -1;
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
     * Gets the line spacing multiplier
     *
     * @return the value by which each line's height is multiplied to get its actual height.
     *
     * @see #setLineSpacing(float, float)
     * @see #getLineSpacingExtra()
     *
     * @attr ref android.R.styleable#TextView_lineSpacingMultiplier
     */
    public float getLineSpacingMultiplier() {
        return mSpacingMult;
    }

    /**
     * Gets the line spacing extra space
     *
     * @return the extra space that is added to the height of each lines of this TextView.
     *
     * @see #setLineSpacing(float, float)
     * @see #getLineSpacingMultiplier()
     *
     * @attr ref android.R.styleable#TextView_lineSpacingExtra
     */
    public float getLineSpacingExtra() {
        return mSpacingAdd;
    }

    /**
     * Sets an explicit line height for this TextView. This is equivalent to the vertical distance
     * between subsequent baselines in the TextView.
     *
     * @param lineHeight the line height in pixels
     *
     * @see #setLineSpacing(float, float)
     * @see #getLineSpacingExtra()
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

    /**
     * Convenience method to append the specified text to the TextView's
     * display buffer, upgrading it to {@link android.widget.TextView.BufferType#EDITABLE}
     * if it was not already editable.
     *
     * @param text text to be appended to the already displayed text
     */
    public final void append(CharSequence text) {
        append(text, 0, text.length());
    }

    /**
     * Convenience method to append the specified text slice to the TextView's
     * display buffer, upgrading it to {@link android.widget.TextView.BufferType#EDITABLE}
     * if it was not already editable.
     *
     * @param text text to be appended to the already displayed text
     * @param start the index of the first character in the {@code text}
     * @param end the index of the character following the last character in the {@code text}
     *
     * @see Appendable#append(CharSequence, int, int)
     */
    public void append(CharSequence text, int start, int end) {
        if (mText == null) {
            setText(mText);
        }

        mText.append(text, start, end);
    }

    private void updateTextColors() {
        boolean inval = false;
        final int[] drawableState = getDrawableState();
        int color = mTextColor.getColorForState(drawableState, 0);
        if (color != mCurTextColor) {
            mCurTextColor = color;
            inval = true;
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

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        // Save state if we are forced to
        boolean hasSelection = false;
        int start = -1;
        int end = -1;

        if (mText != null) {
            start = getSelectionStart();
            end = getSelectionEnd();
            if (start >= 0 || end >= 0) {
                // Or save state if there is a selection
                hasSelection = true;
            }
        }

        SavedState ss = new SavedState(superState);

        if (mText instanceof Spanned) {
            final Spannable sp = new SpannableStringBuilder(mText);

            //TODO: (EW) is it really necessary to have special handling for suggestion spans?
            removeMisspelledSpans(sp);
//            sp.removeSpan(mEditor.mSuggestionRangeSpan);

            ss.text = sp;
        } else {
            ss.text = mText.toString();
        }

        if (hasSelection) {
            // XXX Should also save the current scroll position!
            ss.selStart = start;
            ss.selEnd = end;
        }

        if (isFocused() && start >= 0 && end >= 0) {
            ss.frozenWithFocus = true;
        }

        ss.editorState = mEditor.saveInstanceState();
        return ss;
    }

    void removeMisspelledSpans(Spannable spannable) {
        SuggestionSpan[] suggestionSpans = spannable.getSpans(0, spannable.length(),
                SuggestionSpan.class);
        for (int i = 0; i < suggestionSpans.length; i++) {
            int flags = suggestionSpans[i].getFlags();
            if ((flags & SuggestionSpan.FLAG_EASY_CORRECT) != 0
                    && (flags & SuggestionSpan.FLAG_MISSPELLED) != 0) {
                spannable.removeSpan(suggestionSpans[i]);
            }
        }
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        // XXX restore buffer type too, as well as lots of other stuff
        if (ss.text != null) {
            setText(ss.text);
        }

        if (ss.selStart >= 0 && ss.selEnd >= 0) {
            if (mSpannable != null) {
                int len = mText.length();

                if (ss.selStart > len || ss.selEnd > len) {
                    String restored = "";

                    if (ss.text != null) {
                        restored = "(restored) ";
                    }

                    Log.e(LOG_TAG, "Saved cursor position " + ss.selStart + "/" + ss.selEnd
                            + " out of range for " + restored + "text " + mText);
                } else {
                    Selection.setSelection(mSpannable, ss.selStart, ss.selEnd);

                    if (ss.frozenWithFocus) {
                        mEditor.mFrozenWithFocus = true;
                    }
                }
            }
        }

        if (ss.error != null) {
            final CharSequence error = ss.error;
            // Display the error later, after the first layout pass
            post(new Runnable() {
                public void run() {
                    if (!mEditor.mErrorWasChanged) {
//                        setError(error);
                    }
                }
            });
        }

        if (ss.editorState != null) {
            mEditor.restoreInstanceState(ss.editorState);
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
     * @param text text to be displayed
     *
     * @attr ref android.R.styleable#TextView_text
     */
    public void setText(CharSequence text) {
        setText(text, true, 0);

        if (mCharWrapper != null) {
            mCharWrapper.mChars = null;
        }
    }

    private void setText(CharSequence text, boolean notifyBefore, int oldlen) {
        mTextSetFromXmlOrResourceId = false;
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

        if (notifyBefore) {
            if (mText != null) {
                oldlen = mText.length();
                sendBeforeTextChanged(mText, 0, oldlen, text.length());
            } else {
                sendBeforeTextChanged("", 0, 0, text.length());
            }
        }

        boolean needEditableForNotification = false;

        if (mListeners != null && mListeners.size() != 0) {
            needEditableForNotification = true;
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

            mEditor.addSpanWatchers(sp);

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
                mEditor.mSelectionMoved = false;
            }
        }

        if (mLayout != null) {
            checkForRelayout();
        }

        sendOnTextChanged(text, 0, oldlen, textLength);
        onTextChanged(text, 0, oldlen, textLength);

        //TODO: (EW) does accessibility need to be handled, or can this just be ignored?
//        notifyViewAccessibilityStateChangedIfNeeded(AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT);

        if (needEditableForNotification) {
            sendAfterTextChanged((Editable) text);
        } else {
            notifyAutoFillManagerAfterTextChangedIfNeeded();//notifyListeningManagersAfterTextChanged();
        }

        // SelectionModifierCursorController depends on textCanBeSelected, which depends on text
        mEditor.prepareCursorControllers();
    }

    /**
     * Sets the TextView to display the specified slice of the specified
     * char array. You must promise that you will not change the contents
     * of the array except for right before another call to setText(),
     * since the TextView has no way to know that the text
     * has changed and that it needs to invalidate and re-layout.
     *
     * @param text char array to be displayed
     * @param start start index in the char array
     * @param len length of char count after {@code start}
     */
    public final void setText(char[] text, int start, int len) {
        int oldlen = 0;

        if (start < 0 || len < 0 || start + len > text.length) {
            throw new IndexOutOfBoundsException(start + ", " + len);
        }

        /*
         * We must do the before-notification here ourselves because if
         * the old text is a CharWrapper we destroy it before calling
         * into the normal path.
         */
        if (mText != null) {
            oldlen = mText.length();
            sendBeforeTextChanged(mText, 0, oldlen, len);
        } else {
            sendBeforeTextChanged("", 0, 0, len);
        }

        if (mCharWrapper == null) {
            mCharWrapper = new CharWrapper(text, start, len);
        } else {
            mCharWrapper.set(text, start, len);
        }

        setText(mCharWrapper, false, oldlen);
    }

    /**
     * Sets the text to be displayed but retains the cursor position. Same as
     * {@link #setText(CharSequence)} except that the cursor position (if any) is retained in the
     * new text.
     * <p/>
     * When required, TextView will use {@link android.text.Spannable.Factory} to create final or
     * intermediate {@link Spannable Spannables}. Likewise it will use
     * {@link android.text.Editable.Factory} to create final or intermediate
     * {@link Editable Editables}.
     *
     * @param text text to be displayed
     *
     * @see #setText(CharSequence)
     */
    public final void setTextKeepState(CharSequence text) {
        int start = getSelectionStart();
        int end = getSelectionEnd();
        int len = text.length();

        setText(text);

        if (start >= 0 || end >= 0) {
            if (mSpannable != null) {
                Selection.setSelection(mSpannable,
                                       Math.max(0, Math.min(start, len)),
                                       Math.max(0, Math.min(end, len)));
            }
        }
    }

    /**
     * Sets the text to be displayed using a string resource identifier.
     *
     * @param resId the resource identifier of the string resource to be displayed
     *
     * @see #setText(CharSequence)
     *
     * @attr ref android.R.styleable#TextView_text
     */
    public final void setText(@StringRes int resId) {
        setText(getContext().getResources().getText(resId));
        mTextSetFromXmlOrResourceId = true;
        mTextId = resId;
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

        if (isInputMethodTarget()) {
            mEditor.reportExtractedText();
        }
    }

    private void setHintInternal(CharSequence hint) {
        mHint = TextUtils.stringOrSpannedString(hint);

        if (mLayout != null) {
            checkForRelayout();
        }

        if (mText.length() == 0) {
            invalidate();
        }

        // Invalidate display list if hint is currently used
        if (mText.length() == 0 && mHint != null) {
            //TODO: (EW) since mEditor.invalidateTextDisplayList isn't used, should something else
            // be done instead?
        }
    }

    /**
     * Sets the text to be displayed when the text of the TextView is empty,
     * from a resource.
     *
     * @attr ref android.R.styleable#TextView_hint
     */
    public final void setHint(@StringRes int resid) {
        setHint(getContext().getResources().getText(resid));
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
                setTypefaceFromAttrs(null/* fontTypeface */, null /* fontFamily */, MONOSPACE, 0);
            }
        } else if (isVisiblePassword) {
            if (mTransformation == PasswordTransformationMethod.getInstance()) {
                forceUpdate = true;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setTypefaceFromAttrs(null/* fontTypeface */, null /* fontFamily */, MONOSPACE,
                        Typeface.NORMAL, -1 /* weight, not specified */);
            } else {
                setTypefaceFromAttrs(null/* fontTypeface */, null /* fontFamily */, MONOSPACE, 0);
            }
        } else if (wasPassword || wasVisiblePassword) {
            // not in password mode, clean up typeface and transformation
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setTypefaceFromAttrs(null/* fontTypeface */, null /* fontFamily */,
                        DEFAULT_TYPEFACE /* typeface index */, Typeface.NORMAL,
                        -1 /* weight, not specified */);
            } else {
                setTypefaceFromAttrs(null/* fontTypeface */, null /* fontFamily */, -1, -1);
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
        if (/*!mUseInternationalizedInput*/Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // If the application does not target O, stick to the previous behavior.
            return null;
        }
        final LocaleList locales = getImeHintLocales();
        if (locales == null) {
            // If the application does not explicitly specify IME hint locale, also stick to the
            // previous behavior.
            return null;
        }
        return locales.get(0);
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
                input = DigitsKeyListener.getInstance(
                        (type & EditorInfo.TYPE_NUMBER_FLAG_SIGNED) != 0,
                        (type & EditorInfo.TYPE_NUMBER_FLAG_DECIMAL) != 0);
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
                        input = DateKeyListener.getInstance();
                    }
                    break;
                case EditorInfo.TYPE_DATETIME_VARIATION_TIME:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        input = TimeKeyListener.getInstance(locale);
                    } else {
                        input = TimeKeyListener.getInstance();
                    }
                    break;
                default:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        input = DateTimeKeyListener.getInstance(locale);
                    } else {
                        input = DateTimeKeyListener.getInstance();
                    }
                    break;
            }
            if (mUseInternationalizedInput) {//targetSdkVersion >= Build.VERSION_CODES.O
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
        return mEditor.mInputType;
    }

    /**
     * Change the editor type integer associated with the text view, which
     * is reported to an Input Method Editor (IME) with {@link EditorInfo#imeOptions}
     * when it has focus.
     * @see #getImeOptions
     * @see android.view.inputmethod.EditorInfo
     * @attr ref android.R.styleable#TextView_imeOptions
     */
    public void setImeOptions(int imeOptions) {
        mEditor.createInputContentTypeIfNeeded();
        mEditor.mInputContentType.imeOptions = imeOptions;
    }

    /**
     * Get the type of the Input Method Editor (IME).
     * @return the type of the IME
     * @see #setImeOptions(int)
     * @see android.view.inputmethod.EditorInfo
     */
    public int getImeOptions() {
        return mEditor != null && mEditor.mInputContentType != null
                ? mEditor.mInputContentType.imeOptions : EditorInfo.IME_NULL;
    }

    /**
     * Change the custom IME action associated with the text view, which
     * will be reported to an IME with {@link EditorInfo#actionLabel}
     * and {@link EditorInfo#actionId} when it has focus.
     * @see #getImeActionLabel
     * @see #getImeActionId
     * @see android.view.inputmethod.EditorInfo
     * @attr ref android.R.styleable#TextView_imeActionLabel
     * @attr ref android.R.styleable#TextView_imeActionId
     */
    public void setImeActionLabel(CharSequence label, int actionId) {
        mEditor.createInputContentTypeIfNeeded();
        mEditor.mInputContentType.imeActionLabel = label;
        mEditor.mInputContentType.imeActionId = actionId;
    }

    /**
     * Get the IME action label previous set with {@link #setImeActionLabel}.
     *
     * @see #setImeActionLabel
     * @see android.view.inputmethod.EditorInfo
     */
    public CharSequence getImeActionLabel() {
        return mEditor.mInputContentType != null ? mEditor.mInputContentType.imeActionLabel : null;
    }

    /**
     * Get the IME action ID previous set with {@link #setImeActionLabel}.
     *
     * @see #setImeActionLabel
     * @see android.view.inputmethod.EditorInfo
     */
    public int getImeActionId() {
        return mEditor.mInputContentType != null ? mEditor.mInputContentType.imeActionId : 0;
    }

    /**
     * Set the private content type of the text, which is the
     * {@link EditorInfo#privateImeOptions EditorInfo.privateImeOptions}
     * field that will be filled in when creating an input connection.
     *
     * @see #getPrivateImeOptions()
     * @see EditorInfo#privateImeOptions
     * @attr ref android.R.styleable#TextView_privateImeOptions
     */
    public void setPrivateImeOptions(String type) {
        mEditor.createInputContentTypeIfNeeded();
        mEditor.mInputContentType.privateImeOptions = type;
    }

    /**
     * Get the private type of the content.
     *
     * @see #setPrivateImeOptions(String)
     * @see EditorInfo#privateImeOptions
     */
    public String getPrivateImeOptions() {
        return mEditor.mInputContentType != null
                ? mEditor.mInputContentType.privateImeOptions : null;
    }

    /**
     * Change "hint" locales associated with the text view, which will be reported to an IME with
     * {@link EditorInfo#hintLocales} when it has focus.
     *
     * Starting with Android O, this also causes internationalized listeners to be created (or
     * change locale) based on the first locale in the input locale list.
     *
     * <p><strong>Note:</strong> If you want new "hint" to take effect immediately you need to
     * call {@link InputMethodManager#restartInput(View)}.</p>
     * @param hintLocales List of the languages that the user is supposed to switch to no matter
     * what input method subtype is currently used. Set {@code null} to clear the current "hint".
     * @see #getImeHintLocales()
     * @see android.view.inputmethod.EditorInfo#hintLocales
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void setImeHintLocales(@Nullable LocaleList hintLocales) {
        mEditor.createInputContentTypeIfNeeded();
        mEditor.mInputContentType.imeHintLocales = hintLocales;
        if (/*mUseInternationalizedInput*/Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            changeListenerLocaleTo(hintLocales == null ? null : hintLocales.get(0));
        }
    }

    /**
     * @return The current languages list "hint". {@code null} when no "hint" is available.
     * @see #setImeHintLocales(LocaleList)
     * @see android.view.inputmethod.EditorInfo#hintLocales
     */
    @Nullable
    public LocaleList getImeHintLocales() {
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
//        final boolean undoFilter = mEditor.mUndoInputFilter != null;
        final boolean keyFilter = mEditor.mKeyListener instanceof InputFilter;
        int num = 0;
//        if (undoFilter) num++;
        if (keyFilter) num++;
        if (num > 0) {
            InputFilter[] nf = new InputFilter[filters.length + num];

            System.arraycopy(filters, 0, nf, 0, filters.length);
            num = 0;
//            if (undoFilter) {
//                nf[filters.length] = mEditor.mUndoInputFilter;
//                num++;
//            }
            if (keyFilter) {
                nf[filters.length + num] = (InputFilter) mEditor.mKeyListener;
            }

            e.setFilters(nf);
            return;
        }
        e.setFilters(filters);
    }

    /**
     * Returns the current list of input filters.
     *
     * @attr ref android.R.styleable#TextView_maxLength
     */
    public InputFilter[] getFilters() {
        return mFilters;
    }

    /////////////////////////////////////////////////////////////////////////

    private int getBoxHeight(Layout l) {
//        Insets opticalInsets = isLayoutModeOptical(mParent) ? getOpticalInsets() : Insets.NONE;
        int padding = (l == mHintLayout)
                ? getCompoundPaddingTop() + getCompoundPaddingBottom()
                : getExtendedPaddingTop() + getExtendedPaddingBottom();
        // (EW) tested creating an EditText and compared getTotalPaddingTop and
        // getExtendedPaddingTop, which were the same, so this optical inset doesn't seem to apply
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
                if (gravity == Gravity.TOP) {
                    voffset = boxht - textht;
                } else { // (gravity == Gravity.CENTER_VERTICAL)
                    voffset = (boxht - textht) >> 1;
                }
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
            if (invalidateCursor && mEditor.mDrawableForCursor != null) {
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
            if (mEditor.mSelectionModifierCursorController != null
                    && mEditor.mSelectionModifierCursorController.isSelectionStartDragged()) {
                curs = getSelectionStart();
            }

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
        if (mEditor.mCreatedWithASelection) {
            mEditor.refreshTextActionMode();
            mEditor.mCreatedWithASelection = false;
        }

        unregisterForPreDraw();

        return true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mTemporaryDetach = false;

        mEditor.onAttachedToWindow();

        if (mPreDrawListenerDetached) {
            getViewTreeObserver().addOnPreDrawListener(this);
            mPreDrawListenerDetached = false;
        }
    }

    //TODO: (EW) validate this is fine. aosp overrides onDetachedFromWindowInternal, but View calls
    // onDetachedFromWindowInternal right after onDetachedFromWindow, so it seems this should be
    // good enough
    @Override
    protected void onDetachedFromWindow() {
        if (mPreDrawRegistered) {
            getViewTreeObserver().removeOnPreDrawListener(this);
            mPreDrawListenerDetached = true;
        }

        mEditor.onDetachedFromWindow();

        super.onDetachedFromWindow();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onScreenStateChanged(int screenState) {
        super.onScreenStateChanged(screenState);
        mEditor.onScreenStateChanged(screenState);
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

    //TODO: (EW) documentation says EditText can always be selected, so this probably should be
    // removed
    /**
     *
     * Returns the state of the {@code textIsSelectable} flag (See
     * {@link #setTextIsSelectable setTextIsSelectable()}). Although you have to set this flag
     * to allow users to select and copy text in a non-editable TextView, the content of an
     * {@link android.widget.EditText} can always be selected, independently of the value of this flag.
     * <p>
     *
     * @return True if the text displayed in this TextView can be selected by the user.
     *
     * @attr ref android.R.styleable#TextView_textIsSelectable
     */
    public boolean isTextSelectable() {
        return mEditor.mTextIsSelectable;
    }

    //TODO: (EW) documentation says EditText can always be selected, so this probably should be
    // removed
    /**
     * Sets whether the content of this view is selectable by the user. The default is
     * {@code false}, meaning that the content is not selectable.
     * <p>
     * When you use a TextView to display a useful piece of information to the user (such as a
     * contact's address), make it selectable, so that the user can select and copy its
     * content. You can also use set the XML attribute
     * {@link R.styleable#EditText_android_textIsSelectable} to "true".
     * <p>
     * When you call this method to set the value of {@code textIsSelectable}, it sets
     * the flags {@code focusable}, {@code focusableInTouchMode}, {@code clickable},
     * and {@code longClickable} to the same value. These flags correspond to the attributes
     * {@link R.styleable#View_android_focusable android:focusable},
     * {@link R.styleable#View_android_focusableInTouchMode android:focusableInTouchMode},
     * {@link R.styleable#View_android_clickable android:clickable}, and
     * {@link R.styleable#View_android_longClickable android:longClickable}. To restore any of these
     * flags to a state you had set previously, call one or more of the following methods:
     * {@link #setFocusable(boolean) setFocusable()},
     * {@link #setFocusableInTouchMode(boolean) setFocusableInTouchMode()},
     * {@link #setClickable(boolean) setClickable()} or
     * {@link #setLongClickable(boolean) setLongClickable()}.
     *
     * @param selectable Whether the content of this TextView should be selectable.
     */
    public void setTextIsSelectable(boolean selectable) {
        if (mEditor.mTextIsSelectable == selectable) return;

        mEditor.mTextIsSelectable = selectable;
        setFocusableInTouchMode(selectable);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setFocusable(FOCUSABLE_AUTO);
        } else {
            setFocusable(selectable);
        }
        setClickable(selectable);
        setLongClickable(selectable);

        // mInputType should already be EditorInfo.TYPE_NULL and mInput should be null

        setMovementMethod(selectable ? ArrowKeyMovementMethod.getInstance() : null);
        setText(mText);

        // Called by setText above, but safer in case of future code changes
        mEditor.prepareCursorControllers();
    }

    private Path getUpdatedHighlightPath() {
        Path highlight = null;
        Paint highlightPaint = mHighlightPaint;

        final int selStart = getSelectionStart();
        final int selEnd = getSelectionEnd();
        if (mMovement != null && (isFocused() || isPressed()) && selStart >= 0) {
            if (selStart == selEnd) {
                if (mEditor.shouldRenderCursor()) {
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
        final int scrollX = getScrollX();
        final int scrollY = getScrollY();
        final int right = getRight();
        final int left = getLeft();
        final int bottom = getBottom();
        final int top = getTop();
//        final boolean isLayoutRtl = isLayoutRtl();
//        final int offset = getHorizontalOffsetForDrawables();
//        final int leftOffset = isLayoutRtl ? 0 : offset;
//        final int rightOffset = isLayoutRtl ? offset : 0;

        int color = mCurTextColor;

        if (mLayout == null) {
            assumeLayout();
        }

        Layout layout = mLayout;

        if (mHint != null && mText.length() == 0) {
            if (mHintTextColor != null) {
                color = mCurHintTextColor;
            }

            layout = mHintLayout;
        }

        mTextPaint.setColor(color);

        canvas.save();
        /*  Would be faster if we didn't have to do this. Can we chop the
            (displayable) text so that we don't need to do this ever?
        */

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
        mEditor.onDraw(canvas, layout, highlight, mHighlightPaint, cursorOffsetVertical);

        canvas.restore();
    }

    @Override
    public void getFocusedRect(Rect r) {
        if (mLayout == null) {
            super.getFocusedRect(r);
            return;
        }

        int selEnd = getSelectionEnd();
        if (selEnd < 0) {
            super.getFocusedRect(r);
            return;
        }

        int selStart = getSelectionStart();
        if (selStart < 0 || selStart >= selEnd) {
            int line = mLayout.getLineForOffset(selEnd);
            r.top = mLayout.getLineTop(line);
            r.bottom = mLayout.getLineBottom(line);
            r.left = (int) mLayout.getPrimaryHorizontal(selEnd) - 2;
            r.right = r.left + 4;
        } else {
            int lineStart = mLayout.getLineForOffset(selStart);
            int lineEnd = mLayout.getLineForOffset(selEnd);
            r.top = mLayout.getLineTop(lineStart);
            r.bottom = mLayout.getLineBottom(lineEnd);
            if (lineStart == lineEnd) {
                r.left = (int) mLayout.getPrimaryHorizontal(selStart);
                r.right = (int) mLayout.getPrimaryHorizontal(selEnd);
            } else {
                // Selection extends across multiple lines -- make the focused
                // rect cover the entire width.
                if (mHighlightPathBogus) {
                    if (mHighlightPath == null) mHighlightPath = new Path();
                    mHighlightPath.reset();
                    mLayout.getSelectionPath(selStart, selEnd, mHighlightPath);
                    mHighlightPathBogus = false;
                }
                synchronized (TEMP_RECTF) {
                    mHighlightPath.computeBounds(TEMP_RECTF, true);
                    r.left = (int) TEMP_RECTF.left - 1;
                    r.right = (int) TEMP_RECTF.right + 1;
                }
            }
        }

        // Adjust for padding and gravity.
        int paddingLeft = getCompoundPaddingLeft();
        int paddingTop = getExtendedPaddingTop();
        if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
            paddingTop += getVerticalOffset(false);
        }
        r.offset(paddingLeft, paddingTop);
        int paddingBottom = getExtendedPaddingBottom();
        r.bottom += paddingBottom;
    }

    /**
     * Return the number of lines of text, or 0 if the internal Layout has not
     * been built.
     */
    public int getLineCount() {
        return mLayout != null ? mLayout.getLineCount() : 0;
    }

    /**
     * Return the baseline for the specified line (0...getLineCount() - 1)
     * If bounds is not null, return the top, left, right, bottom extents
     * of the specified line in it. If the internal Layout has not been built,
     * return 0 and set bounds to (0, 0, 0, 0)
     * @param line which line to examine (0..getLineCount() - 1)
     * @param bounds Optional. If not null, it returns the extent of the line
     * @return the Y-coordinate of the baseline
     */
    public int getLineBounds(int line, Rect bounds) {
        if (mLayout == null) {
            if (bounds != null) {
                bounds.set(0, 0, 0, 0);
            }
            return 0;
        } else {
            int baseline = mLayout.getLineBounds(line, bounds);

            int voffset = getExtendedPaddingTop();
            if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
                voffset += getVerticalOffset(true);
            }
            if (bounds != null) {
                bounds.offset(getCompoundPaddingLeft(), voffset);
            }
            return baseline + voffset;
        }
    }

    @Override
    public int getBaseline() {
        if (mLayout == null) {
            return super.getBaseline();
        }

        return getBaselineOffset() + mLayout.getLineBaseline(0);
    }

    int getBaselineOffset() {
        int voffset = 0;
        if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
            voffset = getVerticalOffset(true);
        }

        // (EW) tested creating an EditText and compared getTotalPaddingTop and
        // getExtendedPaddingTop, which were the same, so this optical inset doesn't seem to apply
//        if (isLayoutModeOptical(mParent)) {
//            voffset -= getOpticalInsets().top;
//        }

        return getExtendedPaddingTop() + voffset;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public PointerIcon onResolvePointerIcon(MotionEvent event, int pointerIndex) {
        if (isTextSelectable() || isTextEditable()) {
            return PointerIcon.getSystemIcon(getContext(), PointerIcon.TYPE_TEXT);
        }
        return super.onResolvePointerIcon(event, pointerIndex);
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        // Note: If the IME is in fullscreen mode and IMS#mExtractEditText is in text action mode,
        // InputMethodService#onKeyDown and InputMethodService#onKeyUp are responsible to call
        // InputMethodService#mExtractEditText.maybeHandleBackInTextActionMode(event).
        if (keyCode == KeyEvent.KEYCODE_BACK && handleBackInTextActionModeIfNeeded(event)) {
            return true;
        }
        return super.onKeyPreIme(keyCode, event);
    }

    // (EW) the AOSP version would get called from InputMethodService, but since this doesn't extend
    // ExtractEditText, that won't happen
    private boolean handleBackInTextActionModeIfNeeded(KeyEvent event) {
        // Do nothing unless mEditor is in text action mode.
        if (mEditor.getTextActionMode() == null) {
            return false;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            KeyEvent.DispatcherState state = getKeyDispatcherState();
            if (state != null) {
                state.startTracking(event, this);
            }
            return true;
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            KeyEvent.DispatcherState state = getKeyDispatcherState();
            if (state != null) {
                state.handleUpEvent(event);
            }
            if (event.isTracking() && !event.isCanceled()) {
                stopTextActionMode();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        final int which = doKeyDown(keyCode, event, null);
        if (which == KEY_EVENT_NOT_HANDLED) {
            return super.onKeyDown(keyCode, event);
        }

        return true;
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
        KeyEvent down = KeyEvent.changeAction(event, KeyEvent.ACTION_DOWN);
        final int which = doKeyDown(keyCode, down, event);
        if (which == KEY_EVENT_NOT_HANDLED) {
            // Go through default dispatching.
            return super.onKeyMultiple(keyCode, repeatCount, event);
        }
        if (which == KEY_EVENT_HANDLED) {
            // Consumed the whole thing.
            return true;
        }

        repeatCount--;

        // We are going to dispatch the remaining events to either the input
        // or movement method.  To do this, we will just send a repeated stream
        // of down and up events until we have done the complete repeatCount.
        // It would be nice if those interfaces had an onKeyMultiple() method,
        // but adding that is a more complicated change.
        KeyEvent up = KeyEvent.changeAction(event, KeyEvent.ACTION_UP);
        if (which == KEY_DOWN_HANDLED_BY_KEY_LISTENER) {
            // mEditor and mEditor.mInput are not null from doKeyDown
            mEditor.mKeyListener.onKeyUp(this, (Editable) mText, keyCode, up);
            while (--repeatCount > 0) {
                mEditor.mKeyListener.onKeyDown(this, (Editable) mText, keyCode, down);
                mEditor.mKeyListener.onKeyUp(this, (Editable) mText, keyCode, up);
            }

        } else if (which == KEY_DOWN_HANDLED_BY_MOVEMENT_METHOD) {
            // mMovement is not null from doKeyDown
            mMovement.onKeyUp(this, mSpannable, keyCode, up);
            while (--repeatCount > 0) {
                mMovement.onKeyDown(this, mSpannable, keyCode, down);
                mMovement.onKeyUp(this, mSpannable, keyCode, up);
            }
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

        if ((mEditor.mInputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT) {
            int variation = mEditor.mInputType & EditorInfo.TYPE_MASK_VARIATION;
            if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    || variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_SUBJECT) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true if pressing TAB in this field advances focus instead
     * of inserting the character.  Insert tabs only in multi-line editors.
     */
    private boolean shouldAdvanceFocusOnTab() {
        if (getKeyListener() != null && !mSingleLine
                && (mEditor.mInputType & EditorInfo.TYPE_MASK_CLASS)
                        == EditorInfo.TYPE_CLASS_TEXT) {
            int variation = mEditor.mInputType & EditorInfo.TYPE_MASK_VARIATION;
            if (variation == EditorInfo.TYPE_TEXT_FLAG_IME_MULTI_LINE
                    || variation == EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) {
                return false;
            }
        }
        return true;
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
                    //TODO: (EW) verify if this^ means that we don't need this handling
//                    if (mEditor.mInputContentType != null) {
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
                    //TODO: (EW) aosp28 checks shouldAdvanceFocusOnTab before returning this, but
                    // aosp30 doesn't. verify this is what we should do too.
                    return KEY_EVENT_NOT_HANDLED;
                }
                break;

            // Has to be done on key down (and not on key up) to correctly be intercepted.
            case KeyEvent.KEYCODE_BACK:
                if (mEditor.getTextActionMode() != null) {
                    stopTextActionMode();
                    return KEY_EVENT_HANDLED;
                }
                break;

            case KeyEvent.KEYCODE_CUT:
                if (event.hasNoModifiers() && canCut()) {
                    if (onTextContextMenuItem(ID_CUT)) {
                        return KEY_EVENT_HANDLED;
                    }
                }
                break;

            case KeyEvent.KEYCODE_COPY:
                if (event.hasNoModifiers() && canCopy()) {
                    if (onTextContextMenuItem(ID_COPY)) {
                        return KEY_EVENT_HANDLED;
                    }
                }
                break;

            case KeyEvent.KEYCODE_PASTE:
                if (event.hasNoModifiers() && canPaste()) {
                    if (onTextContextMenuItem(ID_PASTE)) {
                        return KEY_EVENT_HANDLED;
                    }
                }
                break;

            case KeyEvent.KEYCODE_FORWARD_DEL:
                if (event.hasModifiers(KeyEvent.META_SHIFT_ON) && canCut()) {
                    if (onTextContextMenuItem(ID_CUT)) {
                        return KEY_EVENT_HANDLED;
                    }
                }
                break;

            case KeyEvent.KEYCODE_INSERT:
                if (event.hasModifiers(KeyEvent.META_CTRL_ON) && canCopy()) {
                    if (onTextContextMenuItem(ID_COPY)) {
                        return KEY_EVENT_HANDLED;
                    }
                } else if (event.hasModifiers(KeyEvent.META_SHIFT_ON) && canPaste()) {
                    if (onTextContextMenuItem(ID_PASTE)) {
                        return KEY_EVENT_HANDLED;
                    }
                }
                break;
        }

        if (mEditor.mKeyListener != null) {
            boolean doDown = true;
            if (otherEvent != null) {
                try {
                    beginBatchEdit();
                    final boolean handled = mEditor.mKeyListener.onKeyOther(this, (Editable) mText,
                            otherEvent);
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
                            if (imm != null && getShowSoftInputOnFocus()) {
                                imm.showSoftInput(this, 0);
                            }
                        }
                    }
                }
                return super.onKeyUp(keyCode, event);

            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (event.hasNoModifiers()) {
                    //TODO: (EW) verify if the lack of handling in doKeyDown means this can be
                    // skipped too
//                    if (mEditor.mInputContentType != null
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

        if (mEditor.mKeyListener != null) {
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
        return mEditor.mInputType != EditorInfo.TYPE_NULL;
    }

    @Override
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
                InputConnection ic = new CustomInputConnection(this);
                outAttrs.initialSelStart = getSelectionStart();
                outAttrs.initialSelEnd = getSelectionEnd();
                outAttrs.initialCapsMode = ic.getCursorCapsMode(getInputType());
                return ic;
            }
        }
        return null;
    }

    /**
     * If this TextView contains editable content, extract a portion of it
     * based on the information in <var>request</var> in to <var>outText</var>.
     * @return Returns true if the text was successfully extracted, else false.
     */
    public boolean extractText(ExtractedTextRequest request, ExtractedText outText) {
        return mEditor.extractText(request, outText);
    }

    /**
     * This is used to remove all style-impacting spans from text before new
     * extracted text is being replaced into it, so that we don't have any
     * lingering spans applied during the replace.
     */
    static void removeParcelableSpans(Spannable spannable, int start, int end) {
        Object[] spans = spannable.getSpans(start, end, ParcelableSpan.class);
        int i = spans.length;
        while (i > 0) {
            i--;
            spannable.removeSpan(spans[i]);
        }
    }

    //TODO: (EW) I only see this getting called from InputMethodService for ExtractEditText, but it
    // is a public method so maybe app devs have some use to call it. consider if this can get
    // removed
    /**
     * Apply to this text view the given extracted text, as previously
     * returned by {@link #extractText(ExtractedTextRequest, ExtractedText)}.
     */
    public void setExtractedText(ExtractedText text) {
        Editable content = getEditableText();
        if (text.text != null) {
            if (content == null) {
                setText(text.text);
            } else {
                int start = 0;
                int end = content.length();

                if (text.partialStartOffset >= 0) {
                    final int N = content.length();
                    start = text.partialStartOffset;
                    if (start > N) start = N;
                    end = text.partialEndOffset;
                    if (end > N) end = N;
                }

                removeParcelableSpans(content, start, end);
                if (TextUtils.equals(content.subSequence(start, end), text.text)) {
                    if (text.text instanceof Spanned) {
                        // OK to copy spans only.
                        TextUtils.copySpansFrom((Spanned) text.text, 0, end - start,
                                Object.class, content, start);
                    }
                } else {
                    content.replace(start, end, text.text);
                }
            }
        }

        // Now set the selection position...  make sure it is in range, to
        // avoid crashes.  If this is a partial update, it is possible that
        // the underlying text may have changed, causing us problems here.
        // Also we just don't want to trust clients to do the right thing.
        Spannable sp = (Spannable) getText();
        final int N = sp.length();
        int start = text.selectionStart;
        if (start < 0) {
            start = 0;
        } else if (start > N) {
            start = N;
        }
        int end = text.selectionEnd;
        if (end < 0) {
            end = 0;
        } else if (end > N) {
            end = N;
        }
        Selection.setSelection(sp, start, end);

        // Finally, update the selection mode.
        if ((text.flags & ExtractedText.FLAG_SELECTING) != 0) {
            //TODO: (EW) @hide - figure out how to handle
//            MetaKeyKeyListener.startSelecting(this, sp);
        } else {
            //TODO: (EW) @hide - figure out how to handle
//            MetaKeyKeyListener.stopSelecting(this, sp);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setHintInternal(text.hint);
        }
    }

    /**
     * @hide
     */
    public void setExtracting(ExtractedTextRequest req) {
        if (mEditor.mInputMethodState != null) {
            mEditor.mInputMethodState.mExtractedTextRequest = req;
        }
        // This would stop a possible selection mode, but no such mode is started in case
        // extracted mode will start. Some text is selected though, and will trigger an action mode
        // in the extracted view.
        mEditor.hideCursorAndSpanControllers();
        stopTextActionMode();
        if (mEditor.mSelectionModifierCursorController != null) {
            mEditor.mSelectionModifierCursorController.resetTouchOffsets();
        }
    }

    public void beginBatchEdit() {
        Log.w(TAG, "beginBatchEdit");
        mEditor.beginBatchEdit();
    }

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

    private void nullLayouts() {
        mLayout = mHintLayout = null;
        // Since it depends on the value of mLayout
        mEditor.prepareCursorControllers();
    }

    /**
     * Make a new Layout based on the already-measured size of the view,
     * on the assumption that it was measured correctly at some point.
     */
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

    /**
     * The width passed in is now the desired layout width,
     * not the full view width with padding.
     * {@hide}
     */
    public void makeNewLayout(int wantWidth, int hintWidth, boolean bringIntoView) {
        // Update "old" cached values
        mOldMaximum = mMaximum;
        mOldMaxMode = mMaxMode;

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

        if (mTextDir == null) {
            mTextDir = getTextDirectionHeuristic();
        }

        mLayout = makeSingleLayout(wantWidth, alignment);

        mHintLayout = null;

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
        }

        if (bringIntoView || (testDirChange && oldDir != mLayout.getParagraphDirection(0))) {
            registerForPreDraw();
        }
        // CursorControllers need a non-null mLayout
        mEditor.prepareCursorControllers();
    }

    /**
     * @hide
     */
    protected Layout makeSingleLayout(int wantWidth, Layout.Alignment alignment) {
        Layout result = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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
            result = builder.build();
        } else {
//            mLayout = new DynamicLayout(mText, mTransformed, mTextPaint, wantWidth,
//                    alignment, mTextDir, mSpacingMult, mSpacingAdd, mIncludePad,
//                    mBreakStrategy, mHyphenationFrequency, mJustificationMode,
//                    getKeyListener() == null ? effectiveEllipsize : null, ellipsisWidth);
            result = new DynamicLayout(mText, mTransformed, mTextPaint, wantWidth, alignment, mSpacingMult, mSpacingAdd, mIncludePad);
        }
        return result;
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
        //TODO: (EW) I think there were a decent number of changes between aosp28 and aosp30 and I
        // simplified dome things to initially get things to work. clean this up
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        if (mTextDir == null) {
            mTextDir = getTextDirectionHeuristic();
        }

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
            scrollTo(0, 0);
        }

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

        //TODO: (EW) the override with cap isn't available. verify if this can just be ignored
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

    /**
     * Check whether entirely new text requires a new view layout
     * or merely a new text layout.
     */
    private void checkForRelayout() {
        // If we have a fixed width, we can just swap in a new text layout
        // if the text height stays the same or if the view height is fixed.

        if ((getLayoutParams().width != LayoutParams.WRAP_CONTENT
                || (mMaxWidthMode == mMinWidthMode && mMaxWidth == mMinWidth))
                && (mHint == null || mHintLayout != null)
                && (getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight() > 0)) {
            // Static width, so try making a new text layout.

            int oldht = mLayout.getHeight();
            int want = mLayout.getWidth();
            int hintWant = mHintLayout == null ? 0 : mHintLayout.getWidth();

            /*
             * No need to bring the text into view, since the size is not
             * changing (unless we do the requestLayout(), in which case it
             * will happen at measure).
             */
            makeNewLayout(want, hintWant, false);

            if (mEllipsize != TextUtils.TruncateAt.MARQUEE) {
                // In a fixed-height view, so use our new text layout.
                if (getLayoutParams().height != LayoutParams.WRAP_CONTENT
                        && getLayoutParams().height != LayoutParams.MATCH_PARENT) {
                    //TODO: (EW) since we're not auto-sizing the text, is invalidate necessary?
                    invalidate();
                    return;
                }

                // Dynamic height, but height has stayed the same,
                // so use our new text layout.
                if (mLayout.getHeight() == oldht
                        && (mHintLayout == null || mHintLayout.getHeight() == oldht)) {
                    //TODO: (EW) since we're not auto-sizing the text, is invalidate necessary?
                    invalidate();
                    return;
                }
            }

            // We lose: the height has changed and we have a dynamic height.
            // Request a new view layout using our new text layout.
            requestLayout();
            invalidate();
        } else {
            // Dynamic width, so we have no choice but to request a new
            // view layout with a new text layout.
            nullLayouts();
            requestLayout();
            invalidate();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mDeferScroll >= 0) {
            int curs = mDeferScroll;
            mDeferScroll = -1;
            bringPointIntoView(Math.min(curs, mText.length()));
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

        /*Layout.*/Alignment a = convertAlignment(layout.getParagraphAlignment(line));
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
        //TODO: (EW) verify if it's fine to just ignore clamped
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

    @Override
    public void computeScroll() {
        if (mScroller != null) {
            if (mScroller.computeScrollOffset()) {
                setScrollX(mScroller.getCurrX());
                setScrollY(mScroller.getCurrY());
                //TODO: (EW) figure out how to handle this
//                invalidateParentCaches();
                postInvalidate();  // So we draw again
            }
        }
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
        if ((mEditor.mInputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT) {
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
     * Set the TextView so that when it takes focus, all the text is
     * selected.
     *
     * @attr ref android.R.styleable#TextView_selectAllOnFocus
     */
    public void setSelectAllOnFocus(boolean selectAllOnFocus) {
        mEditor.mSelectAllOnFocus = selectAllOnFocus;

        if (selectAllOnFocus && !(mText instanceof Spannable)) {
            setText(mText);
        }
    }

    /**
     * Set whether the cursor is visible. The default is true. Note that this property only
     * makes sense for editable TextView.
     *
     * @see #isCursorVisible()
     *
     * @attr ref android.R.styleable#TextView_cursorVisible
     */
    public void setCursorVisible(boolean visible) {
        if (mEditor.mCursorVisible != visible) {
            mEditor.mCursorVisible = visible;
            invalidate();

            mEditor.makeBlink();

            // InsertionPointCursorController depends on mCursorVisible
            mEditor.prepareCursorControllers();
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
        // true is the default value
        return mEditor.mCursorVisible;
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

        mEditor.sendOnTextChanged(start, before, after);
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

        // Always notify AutoFillManager - it will return right away if autofill is disabled.
        notifyAutoFillManagerAfterTextChangedIfNeeded();//notifyListeningManagersAfterTextChanged();
    }

    private void notifyAutoFillManagerAfterTextChangedIfNeeded() {
        // It is important to not check whether the view is important for autofill
        // since the user can trigger autofill manually on not important views.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isAutofillable()) {
            return;
        }
        final AutofillManager afm;
        afm = getContext().getSystemService(AutofillManager.class);
        if (afm == null) {
            return;
        }

        if (mLastValueSentToAutofillManager == null
                || !mLastValueSentToAutofillManager.equals(mText)) {
//            if (android.view.autofill.Helper.sVerbose) {
//                Log.v(LOG_TAG, "notifying AFM after text changed");
//            }
            afm.notifyValueChanged(EditText.this);
            mLastValueSentToAutofillManager = mText;
        } else {
//            if (android.view.autofill.Helper.sVerbose) {
//                Log.v(LOG_TAG, "not notifying AFM on unchanged text");
//            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private boolean isAutofillable() {
        // It is important to not check whether the view is important for autofill
        // since the user can trigger autofill manually on not important views.
        return getAutofillType() != AUTOFILL_TYPE_NONE;
    }

    void updateAfterEdit() {
        invalidate();
        int curs = getSelectionStart();

        if (curs >= 0 || (mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
            registerForPreDraw();
        }

        checkForResize();

        if (curs >= 0) {
            mHighlightPathBogus = true;
            mEditor.makeBlink();
            bringPointIntoView(curs);
        }
    }

    /**
     * Not private so it can be called from an inner class without going
     * through a thunk.
     */
    void handleTextChanged(CharSequence buffer, int start, int before, int after) {
        sLastCutCopyOrTextChangedTime = 0;

        final Editor.InputMethodState ims = mEditor.mInputMethodState;
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

        final Editor.InputMethodState ims = mEditor.mInputMethodState;

        if (what == Selection.SELECTION_END) {
            selChanged = true;
            newSelEnd = newStart;

            if (oldStart >= 0 || newStart >= 0) {
                invalidateCursor(Selection.getSelectionStart(buf), oldStart, newStart);
                checkForResize();
                registerForPreDraw();
                mEditor.makeBlink();
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
            if (!isFocused()) mEditor.mSelectionMoved = true;

            if ((buf.getSpanFlags(what) & Spanned.SPAN_INTERMEDIATE) == 0) {
                if (newSelStart < 0) {
                    newSelStart = Selection.getSelectionStart(buf);
                }
                if (newSelEnd < 0) {
                    newSelEnd = Selection.getSelectionEnd(buf);
                }

                mEditor.refreshTextActionMode();
                if (!hasSelection()
                        && mEditor.getTextActionMode() == null && hasTransientState()) {
                    // User generated selection has been removed.
                    setHasTransientState(false);
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
            mEditor.invalidateHandlesAndActionMode();
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

//        if (mEditor.mSpellChecker != null && newStart < 0
//                && what instanceof SpellCheckSpan) {
//            mEditor.mSpellChecker.onSpellCheckSpanRemoved((SpellCheckSpan) what);
//        }
    }

    @Override
    public void dispatchFinishTemporaryDetach() {
        mDispatchTemporaryDetach = true;
        super.dispatchFinishTemporaryDetach();
        mDispatchTemporaryDetach = false;
    }

    @Override
    public void onStartTemporaryDetach() {
        super.onStartTemporaryDetach();
        // Only track when onStartTemporaryDetach() is called directly,
        // usually because this instance is an editable field in a list
        if (!mDispatchTemporaryDetach) mTemporaryDetach = true;
        // Tell the editor that we are temporarily detached. It can use this to preserve
        // selection state as needed.
        mEditor.mTemporaryDetach = true;
    }

    @Override
    public void onFinishTemporaryDetach() {
        super.onFinishTemporaryDetach();
        // Only track when onStartTemporaryDetach() is called directly,
        // usually because this instance is an editable field in a list
        if (!mDispatchTemporaryDetach) mTemporaryDetach = false;
        mEditor.mTemporaryDetach = false;
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        boolean isTemporarilyDetached;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            isTemporarilyDetached = isTemporarilyDetached();
        } else {
            isTemporarilyDetached = mTemporaryDetach;
        }
        if (isTemporarilyDetached) {
            // If we are temporarily in the detach state, then do nothing.
            super.onFocusChanged(focused, direction, previouslyFocusedRect);
            return;
        }

        mEditor.onFocusChanged(focused, direction);

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

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);

        mEditor.onWindowFocusChanged(hasWindowFocus);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != VISIBLE) {
            mEditor.hideCursorAndSpanControllers();
            stopTextActionMode();
        }
    }

    /**
     * Use {@link BaseInputConnection#removeComposingSpans
     * BaseInputConnection.removeComposingSpans()} to remove any IME composing
     * state from this text view.
     */
    public void clearComposingText() {
        if (mText instanceof Spannable) {
            BaseInputConnection.removeComposingSpans(mSpannable);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        mEditor.onTouchEvent(event);

        if (mEditor.mSelectionModifierCursorController != null
                && mEditor.mSelectionModifierCursorController.isDragAcceleratorActive()) {
            return true;
        }

        final boolean superResult = super.onTouchEvent(event);

        /*
         * Don't handle the release after a long press, because it will move the selection away from
         * whatever the menu action was trying to affect. If the long press should have triggered an
         * insertion action mode, we can now actually show it.
         */
        if (mEditor.mDiscardNextActionUp && action == MotionEvent.ACTION_UP) {
            mEditor.mDiscardNextActionUp = false;

            if (mEditor.mIsInsertionActionModeStartPending) {
                mEditor.startInsertionActionMode();
                mEditor.mIsInsertionActionModeStartPending = false;
            }
            return superResult;
        }

        final boolean touchIsFinished = (action == MotionEvent.ACTION_UP)
                && (!mEditor.mIgnoreActionUpEvent) && isFocused();

        if ((mMovement != null || onCheckIsTextEditor()) && isEnabled()
                && mText instanceof Spannable && mLayout != null) {
            boolean handled = false;

            if (mMovement != null) {
                handled |= mMovement.onTouchEvent(this, mSpannable, event);
            }

            final boolean textIsSelectable = isTextSelectable();

            if (touchIsFinished && (isTextEditable() || textIsSelectable)) {
                // Show the IME, except when selecting in read-only text.
                final InputMethodManager imm = getInputMethodManager();
                viewClicked(imm);
                if (isTextEditable() && mEditor.mShowSoftInputOnFocus && imm != null) {
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
        return mEditor.mTouchFocusSelected;
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mEditor.mIgnoreActionUpEvent = true;
    }

    @Override
    protected int computeHorizontalScrollRange() {
        if (mLayout != null) {
            return mSingleLine && (mGravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.LEFT
                    ? (int) mLayout.getLineWidth(0) : mLayout.getWidth();
        }

        return super.computeHorizontalScrollRange();
    }

    @Override
    protected int computeVerticalScrollRange() {
        if (mLayout != null) {
            return mLayout.getHeight();
        }
        return super.computeVerticalScrollRange();
    }

    @Override
    protected int computeVerticalScrollExtent() {
        return getHeight() - getCompoundPaddingTop() - getCompoundPaddingBottom();
    }

    @Override
    public void findViewsWithText(ArrayList<View> outViews, CharSequence searched, int flags) {
        super.findViewsWithText(outViews, searched, flags);
        if (!outViews.contains(this) && (flags & FIND_VIEWS_WITH_TEXT) != 0
                && !TextUtils.isEmpty(searched) && !TextUtils.isEmpty(mText)) {
            String searchedLowerCase = searched.toString().toLowerCase();
            String textLowerCase = mText.toString().toLowerCase();
            if (textLowerCase.contains(searchedLowerCase)) {
                outViews.add(this);
            }
        }
    }

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        if (event.hasModifiers(KeyEvent.META_CTRL_ON)) {
            // Handle Ctrl-only shortcuts.
            switch (keyCode) {
                case KeyEvent.KEYCODE_A:
                    if (canSelectText()) {
                        return onTextContextMenuItem(ID_SELECT_ALL);
                    }
                    break;
                case KeyEvent.KEYCODE_Z:
                    if (canUndo()) {
                        return onTextContextMenuItem(ID_UNDO);
                    }
                    break;
                case KeyEvent.KEYCODE_X:
                    if (canCut()) {
                        return onTextContextMenuItem(ID_CUT);
                    }
                    break;
                case KeyEvent.KEYCODE_C:
                    if (canCopy()) {
                        return onTextContextMenuItem(ID_COPY);
                    }
                    break;
                case KeyEvent.KEYCODE_V:
                    if (canPaste()) {
                        return onTextContextMenuItem(ID_PASTE);
                    }
                    break;
            }
        } else if (event.hasModifiers(KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON)) {
            // Handle Ctrl-Shift shortcuts.
            switch (keyCode) {
                case KeyEvent.KEYCODE_Z:
                    if (canRedo()) {
                        return onTextContextMenuItem(ID_REDO);
                    }
                    break;
                case KeyEvent.KEYCODE_V:
                    if (canPaste()) {
                        return onTextContextMenuItem(ID_PASTE_AS_PLAIN_TEXT);
                    }
            }
        }
        return super.onKeyShortcut(keyCode, event);
    }

    /**
     * Unlike {@link #textCanBeSelected()}, this method is based on the <i>current</i> state of the
     * TextView. {@link #textCanBeSelected()} has to be true (this is one of the conditions to have
     * a selection controller (see {@link Editor#prepareCursorControllers()}), but this is not
     * sufficient.
     */
    boolean canSelectText() {
        return mText.length() != 0 && mEditor.hasSelectionController();
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

    private Locale getTextServicesLocale(boolean allowNullLocale) {
        // Start fetching the text services locale asynchronously.
        //TODO: (EW) either implement or simplify if this is unnecessary
//        updateTextServicesLocaleAsync();
        // If !allowNullLocale and there is no cached text services locale, just return the default
        // locale.
        return (mCurrentSpellCheckerLocaleCache == null && !allowNullLocale) ? Locale.getDefault()
                : mCurrentSpellCheckerLocaleCache;
    }

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
        return getTextServicesLocale(false /* allowNullLocale */);
    }

    /**
     * @return {@code true} if this TextView is specialized for showing and interacting with the
     * extracted text in a full-screen input method.
     * @hide
     */
    public boolean isInExtractedMode() {
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public CharSequence getAccessibilityClassName() {
        return EditText.class.getName();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onProvideStructure(ViewStructure structure) {
        super.onProvideStructure(structure);
        onProvideAutoStructureForAssistOrAutofill(structure, false);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onProvideAutofillStructure(ViewStructure structure, int flags) {
        super.onProvideAutofillStructure(structure, flags);
        onProvideAutoStructureForAssistOrAutofill(structure, true);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void onProvideAutoStructureForAssistOrAutofill(ViewStructure structure,
                                                           boolean forAutofill) {
        final boolean isPassword = hasPasswordTransformationMethod()
                || isPasswordInputType(getInputType());
        if (forAutofill && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            structure.setDataIsSensitive(!mTextSetFromXmlOrResourceId);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && mTextId != /*ResourceId.ID_NULL*/0) {
                try {
                    structure.setTextIdEntry(getResources().getResourceEntryName(mTextId));
                } catch (NotFoundException e) {
//                    if (android.view.autofill.Helper.sVerbose) {
//                        Log.v(LOG_TAG, "onProvideAutofillStructure(): cannot set name for text id "
//                                + mTextId + ": " + e.getMessage());
//                    }
                }
            }
        }

        if (!isPassword || forAutofill) {
            if (mLayout == null) {
                assumeLayout();
            }
            Layout layout = mLayout;
            final int lineCount = layout.getLineCount();
            if (lineCount <= 1) {
                // Simple case: this is a single line.
                final CharSequence text = getText();
                if (forAutofill) {
                    structure.setText(text);
                } else {
                    structure.setText(text, getSelectionStart(), getSelectionEnd());
                }
            } else {
                // Complex case: multi-line, could be scrolled or within a scroll container
                // so some lines are not visible.
                final int[] tmpCords = new int[2];
                getLocationInWindow(tmpCords);
                final int topWindowLocation = tmpCords[1];
                View root = this;
                ViewParent viewParent = getParent();
                while (viewParent instanceof View) {
                    root = (View) viewParent;
                    viewParent = root.getParent();
                }
                final int windowHeight = root.getHeight();
                final int topLine;
                final int bottomLine;
                if (topWindowLocation >= 0) {
                    // The top of the view is fully within its window; start text at line 0.
                    topLine = getLineAtCoordinateUnclamped(0);
                    bottomLine = getLineAtCoordinateUnclamped(windowHeight - 1);
                } else {
                    // The top of hte window has scrolled off the top of the window; figure out
                    // the starting line for this.
                    topLine = getLineAtCoordinateUnclamped(-topWindowLocation);
                    bottomLine = getLineAtCoordinateUnclamped(windowHeight - 1 - topWindowLocation);
                }
                // We want to return some contextual lines above/below the lines that are
                // actually visible.
                int expandedTopLine = topLine - (bottomLine - topLine) / 2;
                if (expandedTopLine < 0) {
                    expandedTopLine = 0;
                }
                int expandedBottomLine = bottomLine + (bottomLine - topLine) / 2;
                if (expandedBottomLine >= lineCount) {
                    expandedBottomLine = lineCount - 1;
                }

                // Convert lines into character offsets.
                int expandedTopChar = layout.getLineStart(expandedTopLine);
                int expandedBottomChar = layout.getLineEnd(expandedBottomLine);

                // Take into account selection -- if there is a selection, we need to expand
                // the text we are returning to include that selection.
                final int selStart = getSelectionStart();
                final int selEnd = getSelectionEnd();
                if (selStart < selEnd) {
                    if (selStart < expandedTopChar) {
                        expandedTopChar = selStart;
                    }
                    if (selEnd > expandedBottomChar) {
                        expandedBottomChar = selEnd;
                    }
                }

                // Get the text and trim it to the range we are reporting.
                CharSequence text = getText();
                if (expandedTopChar > 0 || expandedBottomChar < text.length()) {
                    text = text.subSequence(expandedTopChar, expandedBottomChar);
                }

                if (forAutofill) {
                    structure.setText(text);
                } else {
                    structure.setText(text, selStart - expandedTopChar, selEnd - expandedTopChar);

                    final int[] lineOffsets = new int[bottomLine - topLine + 1];
                    final int[] lineBaselines = new int[bottomLine - topLine + 1];
                    final int baselineOffset = getBaselineOffset();
                    for (int i = topLine; i <= bottomLine; i++) {
                        lineOffsets[i - topLine] = layout.getLineStart(i);
                        lineBaselines[i - topLine] = layout.getLineBaseline(i) + baselineOffset;
                    }
                    structure.setTextLines(lineOffsets, lineBaselines);
                }
            }

            if (!forAutofill) {
                // Extract style information that applies to the TextView as a whole.
                int style = 0;
                int typefaceStyle = getTypefaceStyle();
                if ((typefaceStyle & Typeface.BOLD) != 0) {
                    style |= ViewNode.TEXT_STYLE_BOLD;
                }
                if ((typefaceStyle & Typeface.ITALIC) != 0) {
                    style |= ViewNode.TEXT_STYLE_ITALIC;
                }

                // Global styles can also be set via TextView.setPaintFlags().
                int paintFlags = mTextPaint.getFlags();
                if ((paintFlags & Paint.FAKE_BOLD_TEXT_FLAG) != 0) {
                    style |= ViewNode.TEXT_STYLE_BOLD;
                }
                if ((paintFlags & Paint.UNDERLINE_TEXT_FLAG) != 0) {
                    style |= ViewNode.TEXT_STYLE_UNDERLINE;
                }
                if ((paintFlags & Paint.STRIKE_THRU_TEXT_FLAG) != 0) {
                    style |= ViewNode.TEXT_STYLE_STRIKE_THRU;
                }

                // TextView does not have its own text background color. A background is either part
                // of the View (and can be any drawable) or a BackgroundColorSpan inside the text.
                structure.setTextStyle(getTextSize(), getCurrentTextColor(),
                        ViewNode.TEXT_COLOR_UNDEFINED /* bgColor */, style);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                structure.setMinTextEms(getMinEms());
                structure.setMaxTextEms(getMaxEms());
                int maxLength = -1;
                for (InputFilter filter: getFilters()) {
                    if (filter instanceof LengthFilter) {
                        maxLength = ((LengthFilter) filter).getMax();
                        break;
                    }
                }
                structure.setMaxTextLength(maxLength);
            }
        }
        structure.setHint(getHint());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            structure.setInputType(getInputType());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void autofill(AutofillValue value) {
        if (!value.isText() || !isTextEditable()) {
            Log.w(LOG_TAG, value + " could not be autofilled into " + this);
            return;
        }

        final CharSequence autofilledValue = value.getTextValue();

        // First autofill it...
        setText(autofilledValue, true, 0);

        // ...then move cursor to the end.
        final CharSequence text = getText();
        if ((text instanceof Spannable)) {
            Selection.setSelection((Spannable) text, text.length());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int getAutofillType() {
        return isTextEditable() ? AUTOFILL_TYPE_TEXT : AUTOFILL_TYPE_NONE;
    }

    /**
     * Gets the {@link TextView}'s current text for AutoFill. The value is trimmed to 100K
     * {@code char}s if longer.
     *
     * @return current text, {@code null} if the text is not editable
     *
     * @see View#getAutofillValue()
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    @Nullable
    public AutofillValue getAutofillValue() {
        if (isTextEditable()) {
            final CharSequence text = HiddenTextUtils.trimToParcelableSize(getText());
            return AutofillValue.forText(text);
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void addExtraDataToAccessibilityNodeInfo(
            AccessibilityNodeInfo info, String extraDataKey, Bundle arguments) {
        // The only extra data we support requires arguments.
        if (arguments == null) {
            return;
        }
        if (extraDataKey.equals(EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY)) {
            int positionInfoStartIndex = arguments.getInt(
                    EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, -1);
            int positionInfoLength = arguments.getInt(
                    EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, -1);
            if ((positionInfoLength <= 0) || (positionInfoStartIndex < 0)
                    || (positionInfoStartIndex >= mText.length())) {
                Log.e(LOG_TAG, "Invalid arguments for accessibility character locations");
                return;
            }
            RectF[] boundingRects = new RectF[positionInfoLength];
            final CursorAnchorInfo.Builder builder = new CursorAnchorInfo.Builder();
            populateCharacterBounds(builder, positionInfoStartIndex,
                    positionInfoStartIndex + positionInfoLength,
                    viewportToContentHorizontalOffset(), viewportToContentVerticalOffset());
            CursorAnchorInfo cursorAnchorInfo = builder.setMatrix(null).build();
            for (int i = 0; i < positionInfoLength; i++) {
                int flags = cursorAnchorInfo.getCharacterBoundsFlags(positionInfoStartIndex + i);
                if ((flags & FLAG_HAS_VISIBLE_REGION) == FLAG_HAS_VISIBLE_REGION) {
                    RectF bounds = cursorAnchorInfo
                            .getCharacterBounds(positionInfoStartIndex + i);
                    if (bounds != null) {
                        mapRectFromViewToScreenCoords(bounds, true);
                        boundingRects[i] = bounds;
                    }
                }
            }
            info.getExtras().putParcelableArray(extraDataKey, boundingRects);
        }
    }

    /**
     * Populate requested character bounds in a {@link CursorAnchorInfo.Builder}
     *
     * @param builder The builder to populate
     * @param startIndex The starting character index to populate
     * @param endIndex The ending character index to populate
     * @param viewportToContentHorizontalOffset The horizontal offset from the viewport to the
     * content
     * @param viewportToContentVerticalOffset The vertical offset from the viewport to the content
     * @hide
     */
    // (EW) although the API level doesn't need to be this high, this was added in this version, so
    // leaving this set this high for now
    @RequiresApi(api = Build.VERSION_CODES.O)
    void populateCharacterBounds(CursorAnchorInfo.Builder builder,
            int startIndex, int endIndex, float viewportToContentHorizontalOffset,
            float viewportToContentVerticalOffset) {
        final int minLine = mLayout.getLineForOffset(startIndex);
        final int maxLine = mLayout.getLineForOffset(endIndex - 1);
        for (int line = minLine; line <= maxLine; ++line) {
            final int lineStart = mLayout.getLineStart(line);
            final int lineEnd = mLayout.getLineEnd(line);
            final int offsetStart = Math.max(lineStart, startIndex);
            final int offsetEnd = Math.min(lineEnd, endIndex);
            final boolean ltrLine =
                    mLayout.getParagraphDirection(line) == Layout.DIR_LEFT_TO_RIGHT;
            final float[] widths = new float[offsetEnd - offsetStart];
            mLayout.getPaint().getTextWidths(mTransformed, offsetStart, offsetEnd, widths);
            final float top = mLayout.getLineTop(line);
            final float bottom = mLayout.getLineBottom(line);
            for (int offset = offsetStart; offset < offsetEnd; ++offset) {
                final float charWidth = widths[offset - offsetStart];
                final boolean isRtl = mLayout.isRtlCharAt(offset);
                final float primary = mLayout.getPrimaryHorizontal(offset);
                final float secondary = mLayout.getSecondaryHorizontal(offset);
                // TODO: This doesn't work perfectly for text with custom styles and
                // TAB chars.
                final float left;
                final float right;
                if (ltrLine) {
                    if (isRtl) {
                        left = secondary - charWidth;
                        right = secondary;
                    } else {
                        left = primary;
                        right = primary + charWidth;
                    }
                } else {
                    if (!isRtl) {
                        left = secondary;
                        right = secondary + charWidth;
                    } else {
                        left = primary - charWidth;
                        right = primary;
                    }
                }
                // TODO: Check top-right and bottom-left as well.
                final float localLeft = left + viewportToContentHorizontalOffset;
                final float localRight = right + viewportToContentHorizontalOffset;
                final float localTop = top + viewportToContentVerticalOffset;
                final float localBottom = bottom + viewportToContentVerticalOffset;
                final boolean isTopLeftVisible = isPositionVisible(localLeft, localTop);
                final boolean isBottomRightVisible =
                        isPositionVisible(localRight, localBottom);
                int characterBoundsFlags = 0;
                if (isTopLeftVisible || isBottomRightVisible) {
                    characterBoundsFlags |= FLAG_HAS_VISIBLE_REGION;
                }
                if (!isTopLeftVisible || !isBottomRightVisible) {
                    characterBoundsFlags |= CursorAnchorInfo.FLAG_HAS_INVISIBLE_REGION;
                }
                if (isRtl) {
                    characterBoundsFlags |= CursorAnchorInfo.FLAG_IS_RTL;
                }
                // Here offset is the index in Java chars.
                builder.addCharacterBounds(offset, localLeft, localTop, localRight,
                        localBottom, characterBoundsFlags);
            }
        }
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

    @Override
    public void sendAccessibilityEventUnchecked(AccessibilityEvent event) {
        // Do not send scroll events since first they are not interesting for
        // accessibility and second such events a generated too frequently.
        // For details see the implementation of bringTextIntoView().
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            return;
        }
        super.sendAccessibilityEventUnchecked(event);
    }

    public InputMethodManager getInputMethodManager() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getContext().getSystemService(InputMethodManager.class);
        }
        //TODO: verify this works
        return (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    /**
     * Returns whether this text view is a current input method target.  The
     * default implementation just checks with {@link InputMethodManager}.
     * @return True if the TextView is a current input method target; false otherwise.
     */
    public boolean isInputMethodTarget() {
        InputMethodManager imm = getInputMethodManager();
        return imm != null && imm.isActive(this);
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
                if (hadSelection) {
                    mEditor.invalidateActionModeAsync();
                }
                return true;

            case ID_UNDO:
                mEditor.undo();
                return true;  // Returns true even if nothing was undone.

            case ID_REDO:
                mEditor.redo();
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
//                mEditor.replace();
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
        //TODO: (EW) is it really necessary to have special handling for suggestion spans?
        return removeSuggestionSpans(mTransformed.subSequence(start, end));
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public boolean performLongClick() {
        boolean handled = false;
        boolean performedHapticFeedback = false;

        mEditor.mIsBeingLongClicked = true;

        if (super.performLongClick()) {
            handled = true;
            performedHapticFeedback = true;
        }

        handled |= mEditor.performLongClick(handled);
        mEditor.mIsBeingLongClicked = false;

        if (handled) {
            if (!performedHapticFeedback) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
            mEditor.mDiscardNextActionUp = true;
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
        mEditor.onScrollChanged();
    }

    protected void stopTextActionMode() {
        mEditor.stopTextActionMode();
    }

    boolean canUndo() {
        return mEditor.canUndo();
    }

    boolean canRedo() {
        return mEditor.canRedo();
    }

    boolean canCut() {
        if (hasPasswordTransformationMethod()) {
            return false;
        }

        if (mText.length() > 0 && hasSelection() && mText instanceof Editable
                && mEditor.mKeyListener != null) {
            return true;
        }

        return false;
    }

    boolean canCopy() {
        if (hasPasswordTransformationMethod()) {
            return false;
        }

        if (mText.length() > 0 && hasSelection()) {
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
                && mEditor.mKeyListener != null
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
//        // Hide the toolbar before changing the selection to avoid flickering.
//        hideFloatingToolbar(FLOATING_TOOLBAR_SELECT_ALL_REFRESH_DELAY);
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

    int getLineAtCoordinateUnclamped(float y) {
        y -= getTotalPaddingTop();
        y += getScrollY();
        return getLayout().getLineForVertical((int) y);
    }

    int getOffsetAtCoordinate(int line, float x) {
        x = convertToLocalHorizontalCoordinate(x);
        return getLayout().getOffsetForHorizontal(line, x);
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return mEditor.hasInsertionController();

            case DragEvent.ACTION_DRAG_ENTERED:
                EditText.this.requestFocus();
                return true;

            case DragEvent.ACTION_DRAG_LOCATION:
                if (mText instanceof Spannable) {
                    final int offset = getOffsetForPosition(event.getX(), event.getY());
                    Selection.setSelection(mSpannable, offset);
                }
                return true;

            case DragEvent.ACTION_DROP:
//                mEditor.onDrop(event);
                return true;

            case DragEvent.ACTION_DRAG_ENDED:
            case DragEvent.ACTION_DRAG_EXITED:
            default:
                return true;
        }
    }

    boolean isInBatchEditMode() {
        final Editor.InputMethodState ims = mEditor.mInputMethodState;
        if (ims != null) {
            return ims.mBatchEditNesting > 0;
        }
        return mEditor.mInBatchEditControllers;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);

        final TextDirectionHeuristic newTextDir = getTextDirectionHeuristic();
        if (mTextDir != newTextDir) {
            mTextDir = newTextDir;
            if (mLayout != null) {
                checkForRelayout();
            }
        }
    }

    /**
     * Returns the current {@link TextDirectionHeuristic}.
     *
     * @return the current {@link TextDirectionHeuristic}.
     * @hide
     */
    protected TextDirectionHeuristic getTextDirectionHeuristic() {
        if (hasPasswordTransformationMethod()) {
            // passwords fields should be LTR
            return TextDirectionHeuristics.LTR;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if ((mEditor.mInputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_PHONE) {
                // Phone numbers must be in the direction of the locale's digits. Most locales have LTR
                // digits, but some locales, such as those written in the Adlam or N'Ko scripts, have
                // RTL digits.
                final DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(getTextLocale());
                final String zero;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    zero = symbols.getDigitStrings()[0];
                } else {
                    //TODO: (EW) handle - O calls the same code. not sure how it's available or
                    // where the code is to try to copy it. maybe only do this block for P
                    zero = null;
                }
                // In case the zero digit is multi-codepoint, just use the first codepoint to determine
                // direction.
                final int firstCodepoint = zero.codePointAt(0);
                final byte digitDirection = Character.getDirectionality(firstCodepoint);
                if (digitDirection == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                        || digitDirection == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
                    return TextDirectionHeuristics.RTL;
                } else {
                    return TextDirectionHeuristics.LTR;
                }
            }
        }

        // Always need to resolve layout direction first
        final boolean defaultIsRtl = (getLayoutDirection() == LAYOUT_DIRECTION_RTL);

        // Now, we can select the heuristic
        switch (getTextDirection()) {
            default:
            case TEXT_DIRECTION_FIRST_STRONG:
                return (defaultIsRtl ? TextDirectionHeuristics.FIRSTSTRONG_RTL :
                        TextDirectionHeuristics.FIRSTSTRONG_LTR);
            case TEXT_DIRECTION_ANY_RTL:
                return TextDirectionHeuristics.ANYRTL_LTR;
            case TEXT_DIRECTION_LTR:
                return TextDirectionHeuristics.LTR;
            case TEXT_DIRECTION_RTL:
                return TextDirectionHeuristics.RTL;
            case TEXT_DIRECTION_LOCALE:
                return TextDirectionHeuristics.LOCALE;
            case TEXT_DIRECTION_FIRST_STRONG_LTR:
                return TextDirectionHeuristics.FIRSTSTRONG_LTR;
            case TEXT_DIRECTION_FIRST_STRONG_RTL:
                return TextDirectionHeuristics.FIRSTSTRONG_RTL;
        }
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

    /**
     * User interface state that is stored by TextView for implementing
     * {@link View#onSaveInstanceState}.
     */
    public static class SavedState extends BaseSavedState {
        int selStart = -1;
        int selEnd = -1;
        CharSequence text;
        boolean frozenWithFocus;
        CharSequence error;
        ParcelableParcel editorState;  // Optional state from Editor.

        SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(selStart);
            out.writeInt(selEnd);
            out.writeInt(frozenWithFocus ? 1 : 0);
            TextUtils.writeToParcel(text, out, flags);

            if (error == null) {
                out.writeInt(0);
            } else {
                out.writeInt(1);
                TextUtils.writeToParcel(error, out, flags);
            }

            if (editorState == null) {
                out.writeInt(0);
            } else {
                out.writeInt(1);
                editorState.writeToParcel(out, flags);
            }
        }

        @Override
        public String toString() {
            String str = "EditText.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " start=" + selStart + " end=" + selEnd;
            if (text != null) {
                str += " text=" + text;
            }
            return str + "}";
        }

        @SuppressWarnings("hiding")
        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };

        private SavedState(Parcel in) {
            super(in);
            selStart = in.readInt();
            selEnd = in.readInt();
            frozenWithFocus = (in.readInt() != 0);
            text = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);

            if (in.readInt() != 0) {
                error = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
            }

            if (in.readInt() != 0) {
                editorState = ParcelableParcel.CREATOR.createFromParcel(in);
            }
        }
    }

    private static class CharWrapper implements CharSequence, GetChars/*, GraphicsOperations*/ {
        private char[] mChars;
        private int mStart, mLength;

        public CharWrapper(char[] chars, int start, int len) {
            mChars = chars;
            mStart = start;
            mLength = len;
        }

        /* package */ void set(char[] chars, int start, int len) {
            mChars = chars;
            mStart = start;
            mLength = len;
        }

        public int length() {
            return mLength;
        }

        public char charAt(int off) {
            return mChars[off + mStart];
        }

        @Override
        public String toString() {
            return new String(mChars, mStart, mLength);
        }

        public CharSequence subSequence(int start, int end) {
            if (start < 0 || end < 0 || start > mLength || end > mLength) {
                throw new IndexOutOfBoundsException(start + ", " + end);
            }

            return new String(mChars, start + mStart, end - start);
        }

        public void getChars(int start, int end, char[] buf, int off) {
            if (start < 0 || end < 0 || start > mLength || end > mLength) {
                throw new IndexOutOfBoundsException(start + ", " + end);
            }

            System.arraycopy(mChars, start + mStart, buf, off, end - start);
        }

//        @Override
//        public void drawText(BaseCanvas c, int start, int end,
//                             float x, float y, Paint p) {
//            c.drawText(mChars, start + mStart, end - start, x, y, p);
//        }
//
//        @Override
//        public void drawTextRun(BaseCanvas c, int start, int end,
//                                int contextStart, int contextEnd, float x, float y, boolean isRtl, Paint p) {
//            int count = end - start;
//            int contextCount = contextEnd - contextStart;
//            c.drawTextRun(mChars, start + mStart, count, contextStart + mStart,
//                    contextCount, x, y, isRtl, p);
//        }

        public float measureText(int start, int end, Paint p) {
            return p.measureText(mChars, start + mStart, end - start);
        }

        public int getTextWidths(int start, int end, float[] widths, Paint p) {
            return p.getTextWidths(mChars, start + mStart, end - start, widths);
        }

//        public float getTextRunAdvances(int start, int end, int contextStart,
//                                        int contextEnd, boolean isRtl, float[] advances, int advancesIndex,
//                                        Paint p) {
//            int count = end - start;
//            int contextCount = contextEnd - contextStart;
//            return p.getTextRunAdvances(mChars, start + mStart, count,
//                    contextStart + mStart, contextCount, isRtl, advances,
//                    advancesIndex);
//        }
//
//        public int getTextRunCursor(int contextStart, int contextEnd, int dir,
//                                    int offset, int cursorOpt, Paint p) {
//            int contextCount = contextEnd - contextStart;
//            return p.getTextRunCursor(mChars, contextStart + mStart,
//                    contextCount, dir, offset + mStart, cursorOpt);
//        }
    }

    private class ChangeWatcher implements TextWatcher, SpanWatcher {

        private CharSequence mBeforeText;

        public void beforeTextChanged(CharSequence buffer, int start,
                                      int before, int after) {
            if (DEBUG_EXTRACT) {
                Log.v(LOG_TAG, "beforeTextChanged start=" + start
                        + " before=" + before + " after=" + after + ": " + buffer);
            }

            //TODO: (EW) figure out how to handle this or if it's necessary
//            if (AccessibilityManager.getInstance(getContext()).isEnabled() && (mTransformed != null)) {
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
     * Call { @link Context#startActivityForResult(String, Intent, int, Bundle)} for the View's
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

    // from View
    /**
     * Map a rectangle from view-relative coordinates to screen-relative coordinates
     *
     * @param rect The rectangle to be mapped
     * @param clipToParent Whether to clip child bounds to the parent ones.
     * @hide
     */
    public void mapRectFromViewToScreenCoords(RectF rect, boolean clipToParent) {
        //TODO: (EW) figure out what to do with this
//        if (!hasIdentityMatrix()) {
//            getMatrix().mapRect(rect);
//        }

        rect.offset(getLeft(), getTop());

        ViewParent parent = getParent();
        while (parent instanceof View) {
            View parentView = (View) parent;

            rect.offset(-parentView.getScrollX(), -parentView.getScrollY());

            if (clipToParent) {
                rect.left = Math.max(rect.left, 0);
                rect.top = Math.max(rect.top, 0);
                rect.right = Math.min(rect.right, parentView.getWidth());
                rect.bottom = Math.min(rect.bottom, parentView.getHeight());
            }

            //TODO: (EW) figure out what to do with this
//            if (!parentView.hasIdentityMatrix()) {
//                parentView.getMatrix().mapRect(rect);
//            }

            rect.offset(parentView.getLeft(), parentView.getTop());

            parent = parentView.getParent();
        }

        //TODO: (EW) figure out what to do with this
//        if (parent instanceof ViewRootImpl) {
//            ViewRootImpl viewRootImpl = (ViewRootImpl) parent;
//            rect.offset(0, -viewRootImpl.mCurScrollY);
//        }

        //TODO: (EW) figure out what to do with this
//        rect.offset(mAttachInfo.mWindowLeft, mAttachInfo.mWindowTop);
    }





    // from DigitsKeyListener
    /**
     * Returns a DigitsKeyListener based on an the settings of a existing DigitsKeyListener, with
     * the locale modified.
     *
     * @hide
     */
    @NonNull
    public static DigitsKeyListener getInstance(
            @Nullable Locale locale,
            @NonNull DigitsKeyListener listener) {
//        if (listener.mStringMode) {
//            return listener; // string-mode DigitsKeyListeners have no locale.
//        } else {
//            return getInstance(locale, listener.mSign, listener.mDecimal);
//        }
        return null;
    }

}
