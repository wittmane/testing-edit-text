/*
 * Copyright (C) 2022 Eli Wittman
 * Copyright (C) 2006 The Android Open Source Project
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

package com.wittmane.testingedittext.aosp.widget;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.assist.AssistStructure;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Canvas;

import com.wittmane.testingedittext.aosp.graphics.HiddenMatrix;
import com.wittmane.testingedittext.aosp.internal.util.ArrayUtils;
import com.wittmane.testingedittext.aosp.text.style.SuggestionRangeSpan;
import com.wittmane.testingedittext.wrapper.Insets;

import android.graphics.Matrix;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.fonts.FontStyle;
import android.graphics.fonts.FontVariationAxis;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.icu.text.DecimalFormatSymbols;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.BoringLayout;
import android.text.BoringLayout.Metrics;
import android.text.DynamicLayout;
import android.text.Editable;
import android.text.InputFilter;
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
import android.view.ActionMode;
import android.view.ContentInfo;
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
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AnimationUtils;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;
import android.view.contentcapture.ContentCaptureManager;
import android.view.contentcapture.ContentCaptureSession;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.textservice.SpellCheckerSubtype;
import android.view.textservice.TextServicesManager;
import android.view.translation.TranslationSpec;
import android.view.translation.ViewTranslationRequest;
import android.widget.Scroller;
import android.widget.Toast;

import androidx.annotation.CallSuper;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.InspectableProperty;
import androidx.annotation.InspectableProperty.EnumEntry;
import androidx.annotation.InspectableProperty.FlagEntry;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.RequiresApi;
import androidx.annotation.Size;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat.FocusDirection;

import com.wittmane.testingedittext.aosp.internal.widget.EditableInputConnection;
import com.wittmane.testingedittext.aosp.text.HiddenLayout;
import com.wittmane.testingedittext.aosp.text.HiddenTextUtils;
import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.aosp.os.ParcelableParcel;
import com.wittmane.testingedittext.aosp.internal.util.Preconditions;
import com.wittmane.testingedittext.aosp.text.method.ArrowKeyMovementMethod;
import com.wittmane.testingedittext.aosp.text.method.MovementMethod;
import com.wittmane.testingedittext.aosp.text.method.WordIterator;
import com.wittmane.testingedittext.aosp.text.style.SpellCheckSpan;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import static android.view.ContentInfo.FLAG_CONVERT_TO_PLAIN_TEXT;
import static android.view.ContentInfo.SOURCE_AUTOFILL;
import static android.view.ContentInfo.SOURCE_CLIPBOARD;
import static android.view.ContentInfo.SOURCE_PROCESS_TEXT;
import static android.view.accessibility.AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH;
import static android.view.accessibility.AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX;
import static android.view.accessibility.AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY;
import static android.view.inputmethod.CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION;
import static com.wittmane.testingedittext.aosp.widget.Editor.logCursor;

public class EditText extends View implements ViewTreeObserver.OnPreDrawListener {
    private static final String TAG = EditText.class.getSimpleName();

    static final String LOG_TAG = "EditText";
    static final boolean DEBUG_EXTRACT = false;
    static final boolean DEBUG_CURSOR = false;
    private static final boolean AUTOFILL_HELPER_VERBOSE = false;

    private static final float[] TEMP_POSITION = new float[2];

    // Enum for the "typeface" XML parameter.
    // TODO: How can we get this from the XML instead of hardcoding it here?
    @IntDef(value = {DEFAULT_TYPEFACE, SANS, SERIF, MONOSPACE})
    @Retention(RetentionPolicy.SOURCE)
    private @interface XMLTypefaceAttr{}
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

    private static final int LINES = 1;
    private static final int EMS = LINES;
    private static final int PIXELS = 2;

    // Maximum text length for single line input.
    private static final int MAX_LENGTH_FOR_SINGLE_LINE_EDIT_TEXT = 5000;
    private InputFilter.LengthFilter mSingleLineLengthFilter = null;

    private static final RectF TEMP_RECTF = new RectF();

    static final int VERY_WIDE = 1024 * 1024; // XXX should be much larger
    private static final int ANIMATED_SCROLL_GAP = 250;

    private static final InputFilter[] NO_FILTERS = new InputFilter[0];
    private static final Spanned EMPTY_SPANNED = new SpannedString("");

    private static final int CHANGE_WATCHER_PRIORITY = 100;

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

    /**
     * {@link #setTextColor(int)} or {@link #getCurrentTextColor()} should be used instead.
     */
    @ViewDebug.ExportedProperty(category = "text")
    private int mCurTextColor;

    private int mCurHintTextColor;
    // (EW) removed in the AOSP version in Nougat, but we still need it for older versions since
    // View#isTemporarilyDetached doesn't exist yet.
    private boolean mDispatchTemporaryDetach;

    // (EW) removed in the AOSP version in Nougat, but we still need it for older versions since
    // View#isTemporarilyDetached doesn't exist yet.
    /** Whether this view is temporarily detached from the parent view. */
    boolean mTemporaryDetach;

    private float mShadowRadius;
    private float mShadowDx;
    private float mShadowDy;
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

    // A flag to indicate the cursor was hidden by IME.
    private boolean mImeIsConsumingInput;

    // Whether cursor is visible without regard to {@link mImeConsumesInput}.
    // {@code true} is the default value.
    private boolean mCursorVisibleFromAttr = true;

    private CharWrapper mCharWrapper;

    // Do not update following mText/mSpannable/mPrecomputed except for setTextInternal()
    @ViewDebug.ExportedProperty(category = "text")
    private @NonNull Editable mText = createEditable("");

    private CharSequence mTransformed;

    private CharSequence mHint;
    private Layout mHintLayout;

    private MovementMethod mMovement;

    private TransformationMethod mTransformation;
    private ChangeWatcher mChangeWatcher;

    private ArrayList<TextWatcher> mListeners;

    // display attributes
    private final TextPaint mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private boolean mUserSetTextScaleX;
    // (EW) the AOSP version was typed as Layout but this version currently always uses a
    // DynamicLayout, so specifying that for visibility, but this could be changed back if necessary
    private DynamicLayout mLayout;
    private boolean mLocalesChanged = false;
    private int mTextSizeUnit = -1;

    // This is used to reflect the current user preference for changing font weight and making text
    // more bold.
    private int mFontWeightAdjustment;
    private Typeface mOriginalTypeface;

    // True if setKeyListener() has been explicitly called
    private boolean mListenerChanged = false;
    // True if internationalized input should be used for numbers and date and time.
    // (EW) this should be final but can't since it's set in #init to avoid duplicate code
    private boolean mUseInternationalizedInput;
    // True if fallback fonts that end up getting used should be allowed to affect line spacing.
    /* package */ boolean mUseFallbackLineSpacing;

    @ViewDebug.ExportedProperty(category = "text")
    private int mGravity = Gravity.TOP | Gravity.START;
    private boolean mHorizontallyScrolling;

    private float mSpacingMultiplier = 1.0f;
    private float mSpacingAdd = 0.0f;

    private int mBreakStrategy;
    private int mHyphenationFrequency;

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

    private BoringLayout.Metrics mHintBoring;
    private BoringLayout mSavedHintLayout;

    private TextDirectionHeuristic mTextDir;

    private InputFilter[] mFilters = NO_FILTERS;

    private volatile Locale mCurrentSpellCheckerLocaleCache;

    // It is possible to have a selection even when mEditor is null (programmatically set, like when
    // a link is pressed). These highlight-related fields do not go in mEditor.
    int mHighlightColor = 0x6633B5E5;
    private Path mHighlightPath;
    private final Paint mHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean mHighlightPathBogus = true;

    // Although these fields are specific to editable text, they are not added to Editor because
    // they are defined by the EditText's style and are theme-dependent.
    int mCursorDrawableRes;
    private Drawable mCursorDrawable;
    // Note: this might be stale if setTextSelectHandleLeft is used. We could simplify the code
    // by removing it, but we would break apps targeting <= P that use it by reflection.
    //TODO: (EW) we don't have this same concern, so this could be removed
    private int mTextSelectHandleLeftRes;
    private Drawable mTextSelectHandleLeft;
    // Note: this might be stale if setTextSelectHandleRight is used. We could simplify the code
    // by removing it, but we would break apps targeting <= P that use it by reflection.
    //TODO: (EW) we don't have this same concern, so this could be removed
    private int mTextSelectHandleRightRes;
    private Drawable mTextSelectHandleRight;
    // Note: this might be stale if setTextSelectHandle is used. We could simplify the code
    // by removing it, but we would break apps targeting <= P that use it by reflection.
    //TODO: (EW) we don't have this same concern, so this could be removed
    private int mTextSelectHandleRes;
    private Drawable mTextSelectHandle;
    int mTextEditSuggestionItemLayout;
    int mTextEditSuggestionContainerLayout;
    int mTextEditSuggestionHighlightStyle;

    private static final int NO_POINTER_ID = -1;
    /**
     * The prime (the 1st finger) pointer id which is used as a lock to prevent multi touch among
     * EditText and the handle views which are rendered on popup windows.
     */
    private int mPrimePointerId = NO_POINTER_ID;

    /**
     * Whether the prime pointer is from the event delivered to selection handle or insertion
     * handle.
     */
    private boolean mIsPrimePointerFromHandleView;

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

    private static final int RESOURCES_ID_NULL =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? Resources.ID_NULL : 0;

    // Autofill-related attributes
    //
    // Indicates whether the text was set statically or dynamically, so it can be used to
    // sanitize autofill requests.
    private boolean mTextSetFromXmlOrResourceId = false;
    // Resource id used to set the text.
    private @StringRes int mTextId = RESOURCES_ID_NULL;
    // Resource id used to set the hint.
    private @StringRes int mHintId = RESOURCES_ID_NULL;
    //
    // End of autofill-related attributes

    private static final int FONT_WEIGHT_MIN = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            ? FontStyle.FONT_WEIGHT_MIN : 1;
    private static final int FONT_WEIGHT_MAX = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            ? FontStyle.FONT_WEIGHT_MAX : 1000;

    private static final Matrix IDENTITY_MATRIX = getIdentityMatrix();
    // (EW) Matrix#IDENTITY_MATRIX was made available in S, but it was actually added at least by
    // Kitkat, so it should be safe to call on these older versions, but to be extra safe we'll
    // wrap it in a try/catch and have a fallback
    @SuppressLint("NewApi")
    private static Matrix getIdentityMatrix() {
        try {
            return Matrix.IDENTITY_MATRIX;
        } catch (Exception e) {
            Log.w(TAG, "Matrix.IDENTITY_MATRIX couldn't be called: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return HiddenMatrix.IDENTITY_MATRIX;
    }

    private EditableInputConnection mInputConnection;

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

    // (EW) from View
    private static final int VIEW_STRUCTURE_FOR_ASSIST = 0;
    private  static final int VIEW_STRUCTURE_FOR_AUTOFILL = 1;
    private  static final int VIEW_STRUCTURE_FOR_CONTENT_CAPTURE = 2;
    @IntDef(flag = true, value = {
            VIEW_STRUCTURE_FOR_ASSIST,
            VIEW_STRUCTURE_FOR_AUTOFILL,
            VIEW_STRUCTURE_FOR_CONTENT_CAPTURE
    })
    @Retention(RetentionPolicy.SOURCE)
    private @interface ViewStructureType {}

    public EditText(Context context) {
        this(context, null);
    }

    public EditText(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, android.R.attr.editTextStyle);
    }

    public EditText(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public EditText(
            Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    private void init(
            Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        // EditText is important by default, unless app developer overrode attribute.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // (EW) autofill support started in Oreo
            if (getImportantForAutofill() == IMPORTANT_FOR_AUTOFILL_AUTO) {
                setImportantForAutofill(IMPORTANT_FOR_AUTOFILL_YES);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (getImportantForContentCapture() == IMPORTANT_FOR_CONTENT_CAPTURE_AUTO) {
                setImportantForContentCapture(IMPORTANT_FOR_CONTENT_CAPTURE_YES);
            }
        }

        setTextInternal("");

        final Resources res = getResources();

        mTextPaint.density = res.getDisplayMetrics().density;
        // (EW) the AOSP version calls Paint#setCompatibilityScaling using
        // Resources#getCompatibilityInfo, both of which are not accessible from apps. I don't
        // think there is really anything to do, and I'm not certain if it's really necessary.


        mMovement = ArrowKeyMovementMethod.getInstance();

        final TextAppearanceAttributes attributes = new TextAppearanceAttributes();
        attributes.mTextColor = ColorStateList.valueOf(0xFF000000);
        attributes.mTextSize = 15;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // (EW) the layout only started supporting break strategy in Marshmallow
            mBreakStrategy = Layout.BREAK_STRATEGY_SIMPLE;
            // (EW) the layout only started supporting hyphenation frequency in Marshmallow
            mHyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE;
        }

        final Theme theme = context.getTheme();

        /*
         * Look the appearance up without checking first if it exists because
         * almost every EditText has one and it greatly simplifies the logic
         * to be able to parse the appearance first and then let specific tags
         * for this View override it.
         */
        TypedArray typedArray = theme.obtainStyledAttributes(attrs,
                R.styleable.TextViewAppearance, defStyleAttr, defStyleRes);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveAttributeDataForStyleable(context, R.styleable.TextViewAppearance,
                    attrs, typedArray, defStyleAttr, defStyleRes);
        }
        TypedArray appearance = null;
        int ap = typedArray.getResourceId(
                R.styleable.TextViewAppearance_android_textAppearance, -1);
        typedArray.recycle();
        if (ap != -1) {
            appearance = theme.obtainStyledAttributes(ap, R.styleable.TextAppearance);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveAttributeDataForStyleable(context, R.styleable.TextAppearance,
                        null, appearance, 0, ap);
            }
        }
        if (appearance != null) {
            readTextAppearance(context, appearance, attributes, false /* styleArray */);
            attributes.mFontFamilyExplicit = false;
            appearance.recycle();
        }

        CharSequence digits = null;
        boolean selectAllOnFocus = false;
        int ellipsize = ELLIPSIZE_NOT_SET;
        boolean singleLine = false;
        int maxLength = -1;
        CharSequence text = "";
        CharSequence hint = null;
        int inputType = EditorInfo.TYPE_NULL;
        typedArray = theme.obtainStyledAttributes(
                attrs, R.styleable.EditText, defStyleAttr, defStyleRes);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveAttributeDataForStyleable(context, R.styleable.EditText, attrs, typedArray,
                    defStyleAttr, defStyleRes);
        }
        int firstBaselineToTopHeight = -1;
        int lastBaselineToBottomHeight = -1;
        int lineHeight = -1;

        // (EW) flags for whether the android attributes were specified in order to determine if a
        // custom copy of the attribute should be ignored
        boolean allowUndoSet = false;
        boolean firstBaselineToTopHeightSet = false;
        boolean lastBaselineToBottomHeightSet = false;

        readTextAppearance(context, typedArray, attributes, true /* styleArray */);

        int attrCount = typedArray.getIndexCount();

        // Must set id in a temporary variable because it will be reset by setText()
        boolean textIsSetFromXml = false;
        for (int i = 0; i < attrCount; i++) {
            // (EW) the following attributes are used in the AOSP version but I'm skipping them:
            // deprecated because inputType should be used instead:
            //     editable, inputMethod, numeric, phoneNumber, autoText, capitalize, password
            // deprecated because maxLines and textMultiLine should be used instead:
            //     singleLine
            // value ignored in EditText:
            //     bufferType - always uses Editable
            //     marqueeRepeatLimit - marquee not supported in EditText
            //     freezesText - always enabled
            //     autoSizeTextType, autoSizeStepGranularity, autoSizeMinTextSize,
            //     autoSizeMaxTextSize, autoSizePresetSizes - auto size not supported in EditText
            //     textIsSelectable - EditText content is always selectable
            //     justificationMode - this does work for the hint, but I couldn't get it to work
            //         for the editable text. https://stackoverflow.com/a/67658170 states that it
            //         only works if the text does not have to be a Spannable, which is never true
            //         since mText is an Editable (extends Spannable) at least in this version. the
            //         AOSP version does try to set the justification mode in a DynamicLayout, which
            //         seems like it might never work assuming the Spannable comment is correct
            //         based on when the DynamicLayout is used, although I didn't completely
            //         validate that. that might indicate that there is some case that it could
            //         work, but that's still probably some edge case.
            // feature not currently being implemented:
            //     autoLink - doesn't update links as you type, so this doesn't seem very beneficial
            //     linksClickable - related to autoLink
            //     drawableLeft, drawableTop, drawableRight, drawableBottom, drawableStart,
            //     drawableEnd, drawableTint, drawableTintMode, drawablePadding
            // broken:
            //     editorExtras - throws a NullPointerException even in the built-in EditText
            //         see https://issuetracker.google.com/issues/36956242
            int attr = typedArray.getIndex(i);
            if (attr == R.styleable.EditText_android_digits) {
                digits = typedArray.getText(attr);

            } else if (attr == R.styleable.EditText_android_selectAllOnFocus) {
                selectAllOnFocus = typedArray.getBoolean(attr, selectAllOnFocus);

            } else if (attr == R.styleable.EditText_android_maxLines) {
                setMaxLines(typedArray.getInt(attr, -1));

            } else if (attr == R.styleable.EditText_android_maxHeight) {
                setMaxHeight(typedArray.getDimensionPixelSize(attr, -1));

            } else if (attr == R.styleable.EditText_android_lines) {
                setLines(typedArray.getInt(attr, -1));

            } else if (attr == R.styleable.EditText_android_height) {
                setHeight(typedArray.getDimensionPixelSize(attr, -1));

            } else if (attr == R.styleable.EditText_android_minLines) {
                setMinLines(typedArray.getInt(attr, -1));

            } else if (attr == R.styleable.EditText_android_minHeight) {
                setMinHeight(typedArray.getDimensionPixelSize(attr, -1));

            } else if (attr == R.styleable.EditText_android_maxEms) {
                setMaxEms(typedArray.getInt(attr, -1));

            } else if (attr == R.styleable.EditText_android_maxWidth) {
                setMaxWidth(typedArray.getDimensionPixelSize(attr, -1));

            } else if (attr == R.styleable.EditText_android_ems) {
                setEms(typedArray.getInt(attr, -1));

            } else if (attr == R.styleable.EditText_android_width) {
                setWidth(typedArray.getDimensionPixelSize(attr, -1));

            } else if (attr == R.styleable.EditText_android_minEms) {
                setMinEms(typedArray.getInt(attr, -1));

            } else if (attr == R.styleable.EditText_android_minWidth) {
                setMinWidth(typedArray.getDimensionPixelSize(attr, -1));

            } else if (attr == R.styleable.EditText_android_gravity) {
                setGravity(typedArray.getInt(attr, -1));

            } else if (attr == R.styleable.EditText_android_hint) {
                mHintId = typedArray.getResourceId(attr, RESOURCES_ID_NULL);
                hint = typedArray.getText(attr);

            } else if (attr == R.styleable.EditText_android_text) {
                textIsSetFromXml = true;
                mTextId = typedArray.getResourceId(attr, RESOURCES_ID_NULL);
                text = typedArray.getText(attr);

            } else if (attr == R.styleable.EditText_android_scrollHorizontally) {
                //TODO: (EW) setting this via xml doesn't seem to work (even in the AOSP version
                // at least as of S), but if #setHorizontallyScrolling is used, it works fine. this
                // should be fixed so the xml setup also works.
                if (typedArray.getBoolean(attr, false)) {
                    setHorizontallyScrolling(true);
                }

            } else if (attr == R.styleable.EditText_android_ellipsize) {
                // (EW) the AOSP version of EditText#setEllipsize specifically calls out that
                // marquee isn't supported, which seems to imply that the rest of the options should
                // work, but from testing it seems limited. it seems to only work when the view
                // isn't editable. it works when inputType is unspecified and singleLine = true and
                // editable = false in the AOSP version (see  https://stackoverflow.com/q/19276320),
                // but those properties aren't supported here because they are deprecated in the
                // AOSP version. it also works when the key listener is null (see
                // https://stackoverflow.com/a/33872646), and since that blocks entering or
                // scrolling text, it probably makes the most sense to be done when disabling the
                // view.
                //TODO: (EW) it might be nice to allow this to work when disabled without having
                // to manually clear the key listener.
                ellipsize = typedArray.getInt(attr, ellipsize);

            } else if (attr == R.styleable.EditText_android_includeFontPadding) {
                if (!typedArray.getBoolean(attr, true)) {
                    setIncludeFontPadding(false);
                }

            } else if (attr == R.styleable.EditText_android_cursorVisible) {
                if (!typedArray.getBoolean(attr, true)) {
                    setCursorVisible(false);
                }

            } else if (attr == R.styleable.EditText_android_maxLength) {
                maxLength = typedArray.getInt(attr, -1);

            } else if (attr == R.styleable.EditText_android_textScaleX) {
                setTextScaleX(typedArray.getFloat(attr, 1.0f));

            } else if (attr == R.styleable.EditText_android_enabled) {
                setEnabled(typedArray.getBoolean(attr, isEnabled()));

            } else if (attr == R.styleable.EditText_android_lineSpacingExtra) {
                mSpacingAdd = typedArray.getDimensionPixelSize(attr, (int) mSpacingAdd);

            } else if (attr == R.styleable.EditText_android_lineSpacingMultiplier) {
                mSpacingMultiplier = typedArray.getFloat(attr, mSpacingMultiplier);

            } else if (attr == R.styleable.EditText_android_inputType) {
                inputType = typedArray.getInt(attr, EditorInfo.TYPE_NULL);

            } else if (attr == R.styleable.EditText_android_allowUndo) {
                // (EW) this Android attribute was added in API level 23 (Marshmallow), but I've
                // seen it still work as early as 22 (Lollipop MR1)
                mEditor.mAllowUndo = typedArray.getBoolean(attr, true);
                allowUndoSet = true;

            } else if (attr == R.styleable.EditText_allowUndo) {
                // (EW) copy of EditText_android_allowUndo to allow use in older versions, but the
                // android attribute should take precedence over this custom one if both were
                // entered
                if (!allowUndoSet) {
                    mEditor.mAllowUndo = typedArray.getBoolean(attr, true);
                }

            } else if (attr == R.styleable.EditText_android_imeOptions) {
                mEditor.createInputContentTypeIfNeeded();
                mEditor.mInputContentType.imeOptions = typedArray.getInt(attr,
                        mEditor.mInputContentType.imeOptions);

            } else if (attr == R.styleable.EditText_android_imeActionLabel) {
                mEditor.createInputContentTypeIfNeeded();
                mEditor.mInputContentType.imeActionLabel = typedArray.getText(attr);

            } else if (attr == R.styleable.EditText_android_imeActionId) {
                mEditor.createInputContentTypeIfNeeded();
                mEditor.mInputContentType.imeActionId = typedArray.getInt(attr,
                        mEditor.mInputContentType.imeActionId);

            } else if (attr == R.styleable.EditText_android_privateImeOptions) {
                setPrivateImeOptions(typedArray.getString(attr));

            } else if (attr == R.styleable.EditText_android_textCursorDrawable) {
                mCursorDrawableRes = typedArray.getResourceId(attr, 0);

            } else if (attr == R.styleable.EditText_android_textSelectHandleLeft) {
                mTextSelectHandleLeftRes = typedArray.getResourceId(attr, 0);

            } else if (attr == R.styleable.EditText_android_textSelectHandleRight) {
                mTextSelectHandleRightRes = typedArray.getResourceId(attr, 0);

            } else if (attr == R.styleable.EditText_android_textSelectHandle) {
                mTextSelectHandleRes = typedArray.getResourceId(attr, 0);

            } else if (attr == R.styleable.EditText_android_textEditSuggestionItemLayout) {
                mTextEditSuggestionItemLayout = typedArray.getResourceId(attr, 0);

            } else if (attr == R.styleable.EditText_textEditSuggestionContainerLayout) {
                mTextEditSuggestionContainerLayout = typedArray.getResourceId(attr, 0);

            } else if (attr == R.styleable.EditText_textEditSuggestionHighlightStyle) {
                mTextEditSuggestionHighlightStyle = typedArray.getResourceId(attr, 0);

            } else if (attr == R.styleable.EditText_android_breakStrategy) {
                // (EW) this Android attribute was added in API level 23 (Marshmallow) and actually
                // using it also requires Marshmallow, so it wouldn't even help to make a custom
                // attribute for this for older versions.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // (EW) the layout only started supporting break strategy in Marshmallow
                    mBreakStrategy = typedArray.getInt(attr, Layout.BREAK_STRATEGY_SIMPLE);
                }

            } else if (attr == R.styleable.EditText_android_hyphenationFrequency) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // (EW) the layout only started supporting hyphenation frequency in Marshmallow
                    mHyphenationFrequency = typedArray.getInt(attr,
                            Layout.HYPHENATION_FREQUENCY_NONE);
                }

            } else if (attr == R.styleable.EditText_android_firstBaselineToTopHeight) {
                // (EW) this Android attribute was added in API level 28 (Pie), but I've seen it
                // still work as early as 22 (Lollipop MR1). I'm not sure what allows it to work on
                // older version to be certain if it would always work back through Lollipop MR1 and
                // no further.
                firstBaselineToTopHeight = typedArray.getDimensionPixelSize(attr, -1);
                firstBaselineToTopHeightSet = true;

            } else if (attr == R.styleable.EditText_firstBaselineToTopHeight
                    && !firstBaselineToTopHeightSet) {
                // (EW) copy of EditText_android_firstBaselineToTopHeight to allow use in older
                // versions, but the android attribute should take precedence over this custom one
                // if both were entered
                firstBaselineToTopHeight = typedArray.getDimensionPixelSize(attr, -1);

            } else if (attr == R.styleable.EditText_android_lastBaselineToBottomHeight) {
                // (EW) this Android attribute was added in API level 28 (Pie), but I've seen it
                // still work as early as 22 (Lollipop MR1)
                lastBaselineToBottomHeight = typedArray.getDimensionPixelSize(attr, -1);
                lastBaselineToBottomHeightSet = true;

            } else if (attr == R.styleable.EditText_lastBaselineToBottomHeight
                    && !lastBaselineToBottomHeightSet) {
                // (EW) copy of EditText_android_lastBaselineToBottomHeight to allow use in older
                // versions, but the android attribute should take precedence over this custom one
                // if both were entered
                lastBaselineToBottomHeight = typedArray.getDimensionPixelSize(attr, -1);

            } else if (attr == R.styleable.EditText_android_lineHeight) {
                // (EW) this Android attribute was added in API level 28 (Pie), but I've seen the
                // attribute to still register as early as 22 (Lollipop MR1). the actual
                // functionality of specifying the line height seems unstable on older versions.
                // on Kitkat, it gets extra space below the line of text even a single line, rather
                // only being added space between lines. on Lollipop, it gets extra space only
                // between rows, but only if at least one row wraps. on Oreo, it seems to have no
                // effect. I didn't bother checking other versions. maybe there is something more we
                // can do to support these versions, but I'm not certain. I'll just hide it behind a
                // version check to avoid unexpected behavior changing between versions.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    lineHeight = typedArray.getDimensionPixelSize(attr, -1);
                }

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

        //TODO: (EW) I think this is only necessary if this is moved to a library
        final int targetSdkVersion = context.getApplicationInfo().targetSdkVersion;
        mUseInternationalizedInput = targetSdkVersion >= Build.VERSION_CODES.O;
        mUseFallbackLineSpacing = targetSdkVersion >= Build.VERSION_CODES.P;

        if (digits != null) {
            mEditor.mKeyListener = DigitsKeyListener.getInstance(digits.toString());
            // If no input type was specified, we will default to generic
            // text, since we can't tell the IME about the set of digits
            // that was selected.
            mEditor.mInputType = inputType != EditorInfo.TYPE_NULL
                    ? inputType : EditorInfo.TYPE_CLASS_TEXT;
        } else if (inputType != EditorInfo.TYPE_NULL) {
            setInputType(inputType, true);
            // If set, the input type overrides what was set using the deprecated singleLine flag.
            singleLine = !isMultilineInputType(inputType);
        } else {
            mEditor.mKeyListener = TextKeyListener.getInstance();
            mEditor.mInputType = EditorInfo.TYPE_CLASS_TEXT;
        }

        mEditor.adjustInputType(passwordInputType, webPasswordInputType, numberPasswordInputType);

        if (selectAllOnFocus) {
            mEditor.mSelectAllOnFocus = true;
        }

        // Same as setSingleLine(), but make sure the transformation method and the maximum number
        // of lines of height are unchanged for multi-line EditTexts.
        setInputTypeSingleLine(singleLine);
        applySingleLine(singleLine, singleLine, singleLine,
                // Does not apply automated max length filter since length filter will be resolved
                // later in this function.
                false
        );

        if (singleLine && getKeyListener() == null && ellipsize == ELLIPSIZE_NOT_SET) {
            ellipsize = ELLIPSIZE_END;
        }

        switch (ellipsize) {
            case ELLIPSIZE_START:
                setEllipsize(TextUtils.TruncateAt.START);
                break;
            case ELLIPSIZE_MIDDLE:
                setEllipsize(TextUtils.TruncateAt.MIDDLE);
                break;
            case ELLIPSIZE_END:
                setEllipsize(TextUtils.TruncateAt.END);
                break;
        }

        final boolean isPassword = passwordInputType || webPasswordInputType
                || numberPasswordInputType;
        final boolean isMonospaceEnforced = isPassword || ((mEditor.mInputType
                & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_MASK_VARIATION))
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD));
        if (isMonospaceEnforced) {
            // (EW) for some reason when using appcompat the framework EditText doesn't use
            // monospace while this code, which is copied from it, does. I'm not sure what's causing
            // that. if I just remove this line, it seems to look the same. maybe it's a bug in
            // appcompat, but either way, I don't think it's something worth trying to replicate.
            attributes.mTypefaceIndex = MONOSPACE;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mFontWeightAdjustment =
                    getContext().getResources().getConfiguration().fontWeightAdjustment;
        }
        applyTextAppearance(attributes);

        if (isPassword) {
            setTransformationMethod(PasswordTransformationMethod.getInstance());
        }

        // For addressing b/145128646
        // For the performance reason, we limit characters for single line text field.
        if (singleLine && maxLength == -1) {
            mSingleLineLengthFilter = new InputFilter.LengthFilter(
                MAX_LENGTH_FOR_SINGLE_LINE_EDIT_TEXT);
        }

        if (mSingleLineLengthFilter != null) {
            setFilters(new InputFilter[] { mSingleLineLengthFilter });
        } else if (maxLength >= 0) {
            setFilters(new InputFilter[] { new InputFilter.LengthFilter(maxLength) });
        } else {
            setFilters(NO_FILTERS);
        }

        setText(text);
        if (mTransformed == null) {
            mTransformed = "";
        }

        if (textIsSetFromXml) {
            mTextSetFromXmlOrResourceId = true;
        }

        if (hint != null) setHint(hint);

        /*
         * Views are not normally clickable unless specified to be.
         * However, EditTexts that have input or movement methods *are*
         * clickable by default. By setting clickable here, we implicitly set focusable as well
         * if not overridden by the developer.
         */
        typedArray = context.obtainStyledAttributes(
                attrs, R.styleable.View, defStyleAttr, defStyleRes);
        boolean canInputOrMove = (mMovement != null || getKeyListener() != null);
        boolean clickable = canInputOrMove || isClickable();
        boolean longClickable = canInputOrMove || isLongClickable();
        int focusable;
        boolean isFocusable;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusable = getFocusable();
            // (EW) doesn't matter. this won't be used. pacify java
            isFocusable = false;
        } else {
            isFocusable = mMovement != null || getKeyListener() != null;
            // (EW) doesn't matter. this won't be used. pacify java
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

        mEditor.prepareCursorControllers();

        // If not explicitly specified this view is important for accessibility.
        if (getImportantForAccessibility() == IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

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

    private void setTextInternal(@NonNull CharSequence text) {
        setTextInternal(createEditable(text));
    }

    // Update mText
    private void setTextInternal(@NonNull Editable text) {
        mText = text;
    }

    @NonNull
    private static Editable createEditable(@NonNull CharSequence text) {
        // (EW) note that Editable.Factory#newEditable isn't marked with @NonNull, but it has always
        // simply returned a new SpannableStringBuilder, and if it did refuse to create an editable,
        // the main functionality of this view would probably break regardless, so if something
        // crashes for not handling null, that's probably fine.
        return Editable.Factory.getInstance().newEditable(text);
    }

    // (EW) the AOSP version overrode View#onActivityResult, but that's hidden, so we have to manage
    // this slightly differently. see #startActivityForResult.
    @RequiresApi(api = Build.VERSION_CODES.M)
    private void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == PROCESS_TEXT_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                CharSequence result = data.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
                if (result != null) {
                    if (isTextEditable()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            ClipData clip = ClipData.newPlainText("", result);
                            ContentInfo payload =
                                    new ContentInfo.Builder(clip, SOURCE_PROCESS_TEXT).build();
                            performReceiveContent(payload);
                        } else {
                            mText.replace(getSelectionStart(), getSelectionEnd(), result);
                        }
                        mEditor.refreshTextActionMode();
                    } else {
                        if (result.length() > 0) {
                            Toast.makeText(getContext(), String.valueOf(result), Toast.LENGTH_LONG)
                                    .show();
                        }
                    }
                }
            } else {
                // Reset the selection.
                Selection.setSelection(mText, getSelectionEnd());
            }
        }
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
                                      @XMLTypefaceAttr int typefaceIndex, int style,
                                      @IntRange(from = -1, to = FontStyle.FONT_WEIGHT_MAX) int weight) {
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
    // (EW) for versions prior to Pie
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
    private void resolveStyleAndSetTypeface(@Nullable Typeface typeface, int style,
                                            @IntRange(from = -1, to = FontStyle.FONT_WEIGHT_MAX) int weight) {
        if (weight >= 0) {
            final int limitedWeight = Math.min(FONT_WEIGHT_MAX, Math.max(1, weight));
            final boolean italic = (style & Typeface.ITALIC) != 0;
            setTypeface(Typeface.create(typeface, limitedWeight, italic));
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
            // Hide the soft input if the currently active EditText is disabled
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
    @InspectableProperty
    @NonNull
    public Editable getText() {
        // (EW) starting in S, the AOSP version added padding to the original text so that the
        // transformed text length matches to avoid some issues, but since
        // #onCreateViewTranslationRequest doesn't support selectable and editable text yet, we
        // shouldn't need to do anything (also most of the AOSP handling for this is calling hidden
        // things that probably would be difficult or impossible to exactly replicate).
        return mText;
    }

    /**
     * Returns the length, in characters, of the text managed by this EditText
     * @return The length of the text managed by the EditText in characters.
     */
    public int length() {
        return mText.length();
    }

    /**
     * Return the text that EditText is displaying as an Editable object.
     *
     * @see #getText
     */
    @NonNull
    public Editable getEditableText() {
        // (EW) currently this is functionally equivalent with #getText, but the AOSP version has a
        // to-do comment to eventually add support for supporting selectable text translation, which
        // would make it relevant to add if we copy that functionality, so we'll keep this separate
        // in preparation for that potential and simpler comparison with the AOSP code. also, in
        // that case, #getText might not always return an Editable, which would make more separate.
        return mText;
    }

    /**
     * Gets the vertical distance between lines of text, in pixels.
     * Note that markup within the text can cause individual lines
     * to be taller or shorter than this height, and the layout may
     * contain additional first-or last-line padding.
     * @return The height of one standard line in pixels.
     */
    @InspectableProperty
    public int getLineHeight() {
        return Math.round(
                mTextPaint.getFontMetricsInt(null) * mSpacingMultiplier + mSpacingAdd);
    }

    /**
     * Gets the {@link android.text.Layout} that is currently being used to display the text.
     * This value can be null if the text or width has recently changed.
     * @return The Layout that is currently being used to display the text.
     */
    public final Layout getLayout() {
        return mLayout;
    }

    // (EW) added so the TextDirectionHeuristic used for mLayout so it can be passed to
    // HiddenLayout#getPrimaryHorizontal since that can't look it up from the Layout since it's
    // hidden.
    TextDirectionHeuristic getTextDir() {
        return mTextDir;
    }

    /**
     * Gets the current {@link KeyListener} for the EditText.
     * @return the current key listener for this EditText.
     *
     * @attr ref android.R.styleable#TextView_digits
     */
    public final KeyListener getKeyListener() {
        return mEditor.mKeyListener;
    }

    /**
     * Sets the key listener to be used with this EditText.  This can be null
     * to disallow user input.  Note that this method has significant and
     * subtle interactions with soft keyboards and other input method:
     * see {@link KeyListener#getInputType() KeyListener.getInputType()}
     * for important details.  Calling this method will replace the current
     * content type of the text view with the content type returned by the
     * key listener.
     * <p>
     * Be warned that if you want a EditText with a key listener or movement
     * method not to be focusable, or if you want a EditText without a
     * key listener or movement method to be focusable, you must call
     * {@link #setFocusable} again after calling this to get the focusability
     * back the way you want it.
     *
     * @attr ref android.R.styleable#TextView_digits
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

            setFilters(mText, mFilters);
        }
    }

    /**
     * Gets the {@link MovementMethod} being used for this EditText,
     * which provides positioning, scrolling, and text selection functionality.
     * @return the movement method being used for this EditText.
     * @see MovementMethod
     */
    public final MovementMethod getMovementMethod() {
        return mMovement;
    }

    /**
     * Sets the {@link MovementMethod} for handling arrow key movement
     * for this EditText. This can be null to disallow using the arrow keys to move the
     * cursor or scroll the view.
     * <p>
     * Be warned that if you want a EditText with a key listener or movement
     * method not to be focusable, or if you want a EditText without a
     * key listener or movement method to be focusable, you must call
     * {@link #setFocusable} again after calling this to get the focusability
     * back the way you want it.
     */
    public final void setMovementMethod(MovementMethod movement) {
        if (mMovement != movement) {
            mMovement = movement;

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
     * Gets the current {@link android.text.method.TransformationMethod} for the EditText.
     * This is frequently null, except for single-line and password fields.
     * @return the current transformation method for this EditText.
     */
    public final TransformationMethod getTransformationMethod() {
        return mTransformation;
    }

    /**
     * Sets the transformation that is applied to the text that this
     * EditText is displaying.
     */
    public final void setTransformationMethod(TransformationMethod method) {
        if (method == mTransformation) {
            // Avoid the setText() below if the transformation is
            // the same.
            return;
        }
        if (mTransformation != null) {
            mText.removeSpan(mTransformation);
        }

        mTransformation = method;

        setText(mText);

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
        int viewHeight = getHeight() - top - bottom;
        int layoutHeight = mLayout.getLineTop(mMaximum);

        if (layoutHeight >= viewHeight) {
            return top;
        }

        final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;
        if (gravity == Gravity.TOP) {
            return top;
        } else if (gravity == Gravity.BOTTOM) {
            return top + viewHeight - layoutHeight;
        } else { // (gravity == Gravity.CENTER_VERTICAL)
            return top + (viewHeight - layoutHeight) / 2;
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
        int viewHeight = getHeight() - top - bottom;
        int layoutHeight = mLayout.getLineTop(mMaximum);

        if (layoutHeight >= viewHeight) {
            return bottom;
        }

        final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;
        if (gravity == Gravity.TOP) {
            return bottom + viewHeight - layoutHeight;
        } else if (gravity == Gravity.BOTTOM) {
            return bottom;
        } else { // (gravity == Gravity.CENTER_VERTICAL)
            return bottom + (viewHeight - layoutHeight) / 2;
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
     * Updates the top padding of the EditText so that {@code firstBaselineToTopHeight} is
     * the distance between the top of the EditText and first line's baseline.
     * <p>
     * <img src="{@docRoot}reference/android/images/text/widget/first_last_baseline.png" />
     * <figcaption>First and last baseline metrics for a EditText.</figcaption>
     *
     * <strong>Note</strong> that if {@code FontMetrics.top} or {@code FontMetrics.ascent} was
     * already greater than {@code firstBaselineToTopHeight}, the top padding is not updated.
     * Moreover since this function sets the top padding, if the height of the EditText is less than
     * the sum of top padding, line height and bottom padding, top of the line will be pushed
     * down and bottom will be clipped.
     *
     * @param firstBaselineToTopHeight distance between first baseline to top of the container
     *      in pixels
     *
     * @see #getFirstBaselineToTopHeight()
     * @see #setLastBaselineToBottomHeight(int)
     * @see #setPadding(int, int, int, int)
     * @see #setPaddingRelative(int, int, int, int)
     *
     * @attr ref android.R.styleable#TextView_firstBaselineToTopHeight
     */
    public void setFirstBaselineToTopHeight(@Px @IntRange(from = 0) int firstBaselineToTopHeight) {
        Preconditions.checkArgumentNonnegative(firstBaselineToTopHeight);

        final FontMetricsInt fontMetrics = getPaint().getFontMetricsInt();
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
     * Updates the bottom padding of the EditText so that {@code lastBaselineToBottomHeight} is
     * the distance between the bottom of the EditText and the last line's baseline.
     * <p>
     * <img src="{@docRoot}reference/android/images/text/widget/first_last_baseline.png" />
     * <figcaption>First and last baseline metrics for a EditText.</figcaption>
     *
     * <strong>Note</strong> that if {@code FontMetrics.bottom} or {@code FontMetrics.descent} was
     * already greater than {@code lastBaselineToBottomHeight}, the bottom padding is not updated.
     * Moreover since this function sets the bottom padding, if the height of the EditText is less
     * than the sum of top padding, line height and bottom padding, bottom of the text will be
     * clipped.
     *
     * @param lastBaselineToBottomHeight distance between last baseline to bottom of the container
     *      in pixels
     *
     * @see #getLastBaselineToBottomHeight()
     * @see #setFirstBaselineToTopHeight(int)
     * @see #setPadding(int, int, int, int)
     * @see #setPaddingRelative(int, int, int, int)
     *
     * @attr ref android.R.styleable#TextView_lastBaselineToBottomHeight
     */
    public void setLastBaselineToBottomHeight(
            @Px @IntRange(from = 0) int lastBaselineToBottomHeight) {
        Preconditions.checkArgumentNonnegative(lastBaselineToBottomHeight);

        final FontMetricsInt fontMetrics = getPaint().getFontMetricsInt();
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
     * Returns the distance between the first text baseline and the top of this EditText.
     *
     * @see #setFirstBaselineToTopHeight(int)
     * @attr ref android.R.styleable#TextView_firstBaselineToTopHeight
     */
    @InspectableProperty
    public int getFirstBaselineToTopHeight() {
        return getPaddingTop() - getPaint().getFontMetricsInt().top;
    }

    /**
     * Returns the distance between the last text baseline and the bottom of this EditText.
     *
     * @see #setLastBaselineToBottomHeight(int)
     * @attr ref android.R.styleable#TextView_lastBaselineToBottomHeight
     */
    @InspectableProperty
    public int getLastBaselineToBottomHeight() {
        return getPaddingBottom() + getPaint().getFontMetricsInt().bottom;
    }

    /**
     * Sets the Drawable corresponding to the selection handle used for
     * positioning the cursor within text. The Drawable defaults to the value
     * of the textSelectHandle attribute.
     * Note that any change applied to the handle Drawable will not be visible
     * until the handle is hidden and then drawn again.
     *
     * @see #setTextSelectHandle(int)
     * @attr ref android.R.styleable#TextView_textSelectHandle
     */
    public void setTextSelectHandle(@NonNull Drawable textSelectHandle) {
        Objects.requireNonNull(textSelectHandle,
                "The text select handle should not be null.");
        mTextSelectHandle = textSelectHandle;
        mTextSelectHandleRes = 0;
        mEditor.loadHandleDrawables(true /* overwrite */);
    }

    /**
     * Sets the Drawable corresponding to the selection handle used for
     * positioning the cursor within text. The Drawable defaults to the value
     * of the textSelectHandle attribute.
     * Note that any change applied to the handle Drawable will not be visible
     * until the handle is hidden and then drawn again.
     *
     * @see #setTextSelectHandle(Drawable)
     * @attr ref android.R.styleable#TextView_textSelectHandle
     */
    public void setTextSelectHandle(@DrawableRes int textSelectHandle) {
        Preconditions.checkArgument(textSelectHandle != 0,
                "The text select handle should be a valid drawable resource id.");
        setTextSelectHandle(getDrawable(textSelectHandle));
    }

    /**
     * Returns the Drawable corresponding to the selection handle used
     * for positioning the cursor within text.
     * Note that any change applied to the handle Drawable will not be visible
     * until the handle is hidden and then drawn again.
     *
     * @return the text select handle drawable
     *
     * @see #setTextSelectHandle(Drawable)
     * @see #setTextSelectHandle(int)
     * @attr ref android.R.styleable#TextView_textSelectHandle
     */
    @Nullable public Drawable getTextSelectHandle() {
        if (mTextSelectHandle == null && mTextSelectHandleRes != 0) {
            mTextSelectHandle = getDrawable(mTextSelectHandleRes);
        }
        return mTextSelectHandle;
    }

    /**
     * Sets the Drawable corresponding to the left handle used
     * for selecting text. The Drawable defaults to the value of the
     * textSelectHandleLeft attribute.
     * Note that any change applied to the handle Drawable will not be visible
     * until the handle is hidden and then drawn again.
     *
     * @see #setTextSelectHandleLeft(int)
     * @attr ref android.R.styleable#TextView_textSelectHandleLeft
     */
    public void setTextSelectHandleLeft(@NonNull Drawable textSelectHandleLeft) {
        Objects.requireNonNull(textSelectHandleLeft,
                "The left text select handle should not be null.");
        mTextSelectHandleLeft = textSelectHandleLeft;
        mTextSelectHandleLeftRes = 0;
        mEditor.loadHandleDrawables(true /* overwrite */);
    }

    /**
     * Sets the Drawable corresponding to the left handle used
     * for selecting text. The Drawable defaults to the value of the
     * textSelectHandleLeft attribute.
     * Note that any change applied to the handle Drawable will not be visible
     * until the handle is hidden and then drawn again.
     *
     * @see #setTextSelectHandleLeft(Drawable)
     * @attr ref android.R.styleable#TextView_textSelectHandleLeft
     */
    public void setTextSelectHandleLeft(@DrawableRes int textSelectHandleLeft) {
        Preconditions.checkArgument(textSelectHandleLeft != 0,
                "The text select left handle should be a valid drawable resource id.");
        setTextSelectHandleLeft(getDrawable(textSelectHandleLeft));
    }

    /**
     * Returns the Drawable corresponding to the left handle used
     * for selecting text.
     * Note that any change applied to the handle Drawable will not be visible
     * until the handle is hidden and then drawn again.
     *
     * @return the left text selection handle drawable
     *
     * @see #setTextSelectHandleLeft(Drawable)
     * @see #setTextSelectHandleLeft(int)
     * @attr ref android.R.styleable#TextView_textSelectHandleLeft
     */
    @Nullable
    public Drawable getTextSelectHandleLeft() {
        if (mTextSelectHandleLeft == null && mTextSelectHandleLeftRes != 0) {
            mTextSelectHandleLeft = getDrawable(mTextSelectHandleLeftRes);
        }
        return mTextSelectHandleLeft;
    }

    /**
     * Sets the Drawable corresponding to the right handle used
     * for selecting text. The Drawable defaults to the value of the
     * textSelectHandleRight attribute.
     * Note that any change applied to the handle Drawable will not be visible
     * until the handle is hidden and then drawn again.
     *
     * @see #setTextSelectHandleRight(int)
     * @attr ref android.R.styleable#TextView_textSelectHandleRight
     */
    public void setTextSelectHandleRight(@NonNull Drawable textSelectHandleRight) {
        Objects.requireNonNull(textSelectHandleRight,
                "The right text select handle should not be null.");
        mTextSelectHandleRight = textSelectHandleRight;
        mTextSelectHandleRightRes = 0;
        mEditor.loadHandleDrawables(true /* overwrite */);
    }

    /**
     * Sets the Drawable corresponding to the right handle used
     * for selecting text. The Drawable defaults to the value of the
     * textSelectHandleRight attribute.
     * Note that any change applied to the handle Drawable will not be visible
     * until the handle is hidden and then drawn again.
     *
     * @see #setTextSelectHandleRight(Drawable)
     * @attr ref android.R.styleable#TextView_textSelectHandleRight
     */
    public void setTextSelectHandleRight(@DrawableRes int textSelectHandleRight) {
        Preconditions.checkArgument(textSelectHandleRight != 0,
                "The text select right handle should be a valid drawable resource id.");
        setTextSelectHandleRight(getDrawable(textSelectHandleRight));
    }

    /**
     * Returns the Drawable corresponding to the right handle used
     * for selecting text.
     * Note that any change applied to the handle Drawable will not be visible
     * until the handle is hidden and then drawn again.
     *
     * @return the right text selection handle drawable
     *
     * @see #setTextSelectHandleRight(Drawable)
     * @see #setTextSelectHandleRight(int)
     * @attr ref android.R.styleable#TextView_textSelectHandleRight
     */
    @Nullable
    public Drawable getTextSelectHandleRight() {
        if (mTextSelectHandleRight == null && mTextSelectHandleRightRes != 0) {
            mTextSelectHandleRight = getDrawable(mTextSelectHandleRightRes);
        }
        return mTextSelectHandleRight;
    }

    /**
     * Sets the Drawable corresponding to the text cursor. The Drawable defaults to the
     * value of the textCursorDrawable attribute.
     * Note that any change applied to the cursor Drawable will not be visible
     * until the cursor is hidden and then drawn again.
     *
     * @see #setTextCursorDrawable(int)
     * @attr ref android.R.styleable#TextView_textCursorDrawable
     */
    public void setTextCursorDrawable(@Nullable Drawable textCursorDrawable) {
        mCursorDrawable = textCursorDrawable;
        mCursorDrawableRes = 0;
        mEditor.loadCursorDrawable();
    }

    /**
     * Sets the Drawable corresponding to the text cursor. The Drawable defaults to the
     * value of the textCursorDrawable attribute.
     * Note that any change applied to the cursor Drawable will not be visible
     * until the cursor is hidden and then drawn again.
     *
     * @see #setTextCursorDrawable(Drawable)
     * @attr ref android.R.styleable#TextView_textCursorDrawable
     */
    public void setTextCursorDrawable(@DrawableRes int textCursorDrawable) {
        setTextCursorDrawable(
                textCursorDrawable != 0 ? getDrawable(textCursorDrawable) : null);
    }

    /**
     * Returns the Drawable corresponding to the text cursor.
     * Note that any change applied to the cursor Drawable will not be visible
     * until the cursor is hidden and then drawn again.
     *
     * @return the text cursor drawable
     *
     * @see #setTextCursorDrawable(Drawable)
     * @see #setTextCursorDrawable(int)
     * @attr ref android.R.styleable#TextView_textCursorDrawable
     */
    @Nullable
    public Drawable getTextCursorDrawable() {
        if (mCursorDrawable == null && mCursorDrawableRes != 0) {
            mCursorDrawable = getDrawable(mCursorDrawableRes);
        }
        return mCursorDrawable;
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
        int mTextSize = -1;
        int mTextSizeUnit = -1;
        LocaleList mTextLocales = null;
        String mFontFamily = null;
        Typeface mFontTypeface = null;
        boolean mFontFamilyExplicit = false;
        @XMLTypefaceAttr int mTypefaceIndex = DEFAULT_TYPEFACE;
        int mTextStyle = 0;
        int mFontWeight = -1;
        int mShadowColor = 0;
        float mShadowDx = 0, mShadowDy = 0, mShadowRadius = 0;
        boolean mHasElegant = false;
        boolean mElegant = false;
        boolean mHasFallbackLineSpacing = false;
        boolean mFallbackLineSpacing = false;
        boolean mHasLetterSpacing = false;
        float mLetterSpacing = 0;
        String mFontFeatureSettings = null;
        String mFontVariationSettings = null;

        @Override
        public String toString() {
            return "TextAppearanceAttributes {\n"
                    + "    mTextColorHighlight:" + mTextColorHighlight + "\n"
                    + "    mTextColor:" + mTextColor + "\n"
                    + "    mTextColorHint:" + mTextColorHint + "\n"
                    + "    mTextSize:" + mTextSize + "\n"
                    + "    mTextSizeUnit:" + mTextSizeUnit + "\n"
                    + "    mTextLocales:" + mTextLocales + "\n"
                    + "    mFontFamily:" + mFontFamily + "\n"
                    + "    mFontTypeface:" + mFontTypeface + "\n"
                    + "    mFontFamilyExplicit:" + mFontFamilyExplicit + "\n"
                    + "    mTypefaceIndex:" + mTypefaceIndex + "\n"
                    + "    mTextStyle:" + mTextStyle + "\n"
                    + "    mFontWeight:" + mFontWeight + "\n"
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

    // Maps styleable attributes that exist both in EditText style and TextAppearance.
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
        sAppearanceValues.put(R.styleable.TextAppearance_android_textLocale,
                R.styleable.TextAppearance_textLocale);
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
        sAppearanceValues.put(R.styleable.TextAppearance_android_fontVariationSettings,
                R.styleable.TextAppearance_fontVariationSettings);
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

            // (EW) the following attributes are used in the AOSP version but I'm skipping them:
            // textAllCaps - documentation for TextView#setAllCaps claims the setting will be
            //               ignored if this field is editable, but that appears to be incorrect
            //               since setting textAllCaps to true in the xml does capitalize all of the
            //               initial text, but it crashes with an IndexOutOfBoundsException when
            //               typing anything into the field.
            //               https://androidacademic.blogspot.com/2018/05/android-edittext-Index-Out-Of-Bounds-exception.html
            //               points this out and states that this property isn't for EditText.
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    // (EW) prior to Lollipop MR1 nothing was done here for the unit
                    attributes.mTextSizeUnit = appearance.peekValue(attr).getComplexUnit();
                }

            } else if (index == R.styleable.TextAppearance_android_textLocale) {
                // (EW) attribute android:textLocale is only used in API level 29 and higher
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    final String localeString = appearance.getString(attr);
                    if (localeString != null) {
                        final LocaleList localeList = LocaleList.forLanguageTags(localeString);
                        if (!localeList.isEmpty()) {
                            attributes.mTextLocales = localeList;
                        }
                    }
                }

            } else if (index == R.styleable.TextAppearance_android_typeface) {
                attributes.mTypefaceIndex = appearance.getInt(attr, attributes.mTypefaceIndex);
                if (attributes.mTypefaceIndex != DEFAULT_TYPEFACE
                        && !attributes.mFontFamilyExplicit) {
                    attributes.mFontFamily = null;
                }

            } else if (index == R.styleable.TextAppearance_android_fontFamily) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // (EW) prior to Oreo nothing was done here for the typeface
                    if (!context.isRestricted() && canLoadUnsafeResources(context)) {
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
                attributes.mTextStyle = appearance.getInt(attr, attributes.mTextStyle);

            } else if (index == R.styleable.TextAppearance_android_textFontWeight) {
                // (EW) attribute android:textFontWeight is only used in API level 28 (Pie) and
                // higher
                attributes.mFontWeight = appearance.getInt(attr, attributes.mFontWeight);

            } else if (index == R.styleable.TextAppearance_android_shadowColor) {
                attributes.mShadowColor = appearance.getInt(attr, attributes.mShadowColor);

            } else if (index == R.styleable.TextAppearance_android_shadowDx) {
                attributes.mShadowDx = appearance.getFloat(attr, attributes.mShadowDx);

            } else if (index == R.styleable.TextAppearance_android_shadowDy) {
                attributes.mShadowDy = appearance.getFloat(attr, attributes.mShadowDy);

            } else if (index == R.styleable.TextAppearance_android_shadowRadius) {
                attributes.mShadowRadius = appearance.getFloat(attr, attributes.mShadowRadius);

            } else if (index == R.styleable.TextAppearance_android_elegantTextHeight) {
                // (EW) attribute android:elegantTextHeight is only used in API level 21 (Lollipop)
                // and higher
                attributes.mHasElegant = true;
                attributes.mElegant = appearance.getBoolean(attr, attributes.mElegant);

            } else if (index == R.styleable.TextAppearance_android_fallbackLineSpacing) {
                attributes.mHasFallbackLineSpacing = true;
                attributes.mFallbackLineSpacing = appearance.getBoolean(attr,
                        attributes.mFallbackLineSpacing);

            } else if (index == R.styleable.TextAppearance_android_letterSpacing) {
                // (EW) attribute android:letterSpacing is only used in API level 21 (Lollipop) and
                // higher
                attributes.mHasLetterSpacing = true;
                attributes.mLetterSpacing =
                        appearance.getFloat(attr, attributes.mLetterSpacing);

            } else if (index == R.styleable.TextAppearance_android_fontFeatureSettings) {
                // (EW) attribute android:fontFeatureSettings is only used in API level 21
                // (Lollipop) and higher
                attributes.mFontFeatureSettings = appearance.getString(attr);

            } else if (index == R.styleable.TextAppearance_android_fontVariationSettings) {
                // (EW) attribute android:fontVariationSettings is only used in API level 28 (Pie)
                // and higher
                attributes.mFontVariationSettings = appearance.getString(attr);
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

        if (attributes.mTextSize != -1) {
            mTextSizeUnit = attributes.mTextSizeUnit;
            setRawTextSize(attributes.mTextSize, true /* shouldRequestLayout */);
        }

        if (attributes.mTextLocales != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            setTextLocales(attributes.mTextLocales);
        }

        if (attributes.mTypefaceIndex != -1 && !attributes.mFontFamilyExplicit) {
            attributes.mFontFamily = null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setTypefaceFromAttrs(attributes.mFontTypeface, attributes.mFontFamily,
                    attributes.mTypefaceIndex, attributes.mTextStyle, attributes.mFontWeight);
        } else {
            setTypefaceFromAttrs(attributes.mFontTypeface, attributes.mFontFamily,
                    attributes.mTypefaceIndex, attributes.mTextStyle);
        }

        if (attributes.mShadowColor != 0) {
            setShadowLayer(attributes.mShadowRadius, attributes.mShadowDx, attributes.mShadowDy,
                    attributes.mShadowColor);
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

        if (attributes.mFontFeatureSettings != null
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setFontFeatureSettings(attributes.mFontFeatureSettings);
        }

        if (attributes.mFontVariationSettings != null
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setFontVariationSettings(attributes.mFontVariationSettings);
        }
    }

    // (EW) from ContextImpl. Context#canLoadUnsafeResources is an abstract method and hidden, but I
    // only found ContextImpl that really implements it.
    // documentation says it "returns true if the context can load unsafe resources, e.g. fonts."
    private boolean canLoadUnsafeResources(Context context) {
        if (context.getPackageName().equals(getOpPackageName(context))) {
            return true;
        }
        // (EW) the AOSP version also checked if the Context.CONTEXT_IGNORE_SECURITY flag was set,
        // but we don't have access to that flag, so there isn't a good way to recreate this logic.
        // returning false to be extra restrictive to be safe. the alternative would be just
        // returning true, at which point this method would only return true, so it should be
        // removed. realistically I'm not certain if this method is even necessary for non-framework
        // views, so removing this is probably fine if this does turn out to cause issues.
        return false;
    }

    // (EW) Context#getOpPackageName existed since at least Kitkat, but it was hidden until Q, so it
    // should be able to be called normally, but adding a try/catch to be safe. the documentation
    // prior to making it visible stated that it is normally the same as getBasePackageName (also
    // hidden, but ContextImpl just returns getPackageName if the base package name was null), so
    // we'll use getPackageName as a fallback (getOpPackageName has been the same as getPackageName
    // in my testing for a regular app), but theoretically that shouldn't ever be used.
    @SuppressLint("NewApi")
    private String getOpPackageName(Context context) {
        try {
            return context.getOpPackageName();
        } catch (Exception e) {
            Log.w(TAG, "Context#getOpPackageName couldn't be called: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return context.getPackageName();
        }
    }

    /**
     * Get the default primary {@link Locale} of the text in this EditText. This will always be
     * the first member of {@link #getTextLocales()}.
     * @return the default primary {@link Locale} of the text in this EditText.
     */
    @NonNull
    public Locale getTextLocale() {
        return mTextPaint.getTextLocale();
    }

    /**
     * Get the default {@link LocaleList} of the text in this EditText.
     * @return the default {@link LocaleList} of the text in this EditText.
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
        KeyListener listener = mEditor.mKeyListener;
        if (listener instanceof DigitsKeyListener) {
            //TODO: (EW) the AOSP version calls a hidden overload of DigitsKeyListener#getInstance
            // that returns a DigitsKeyListener based on an the settings of a existing
            // DigitsKeyListener, with the locale modified. DigitsKeyListener doesn't seem to have
            // any way to check the sign or decimal, and since the listener could come from
            // setKeyListener, we can't really even track it ourself. Other than reflection, I'm not
            // sure how we can do this. I suppose there are other types of listeners that we don't
            // update the locale, so I guess not doing anything isn't that bad. note that
            // DigitsKeyListener (or any of the others) didn't even start supporting a locale until
            // Oreo, so not supporting it may not be too unreasonable. if DigitsKeyListener changes
            // to accomplish this, this should be updated to match functionality of AOSP.
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
     * Set the default {@link Locale} of the text in this EditText to a one-member
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
     * Set the default {@link LocaleList} of the text in this EditText to the given value.
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (mFontWeightAdjustment != newConfig.fontWeightAdjustment) {
                mFontWeightAdjustment = newConfig.fontWeightAdjustment;
                setTypeface(getTypeface());
            }
        }
    }

    /**
     * @return the size (in pixels) of the default text size in this EditText.
     */
    @InspectableProperty
    @ViewDebug.ExportedProperty(category = "text")
    public float getTextSize() {
        return mTextPaint.getTextSize();
    }

    @ViewDebug.ExportedProperty(category = "text", mapping = {
            @ViewDebug.IntToString(from = Typeface.NORMAL, to = "NORMAL"),
            @ViewDebug.IntToString(from = Typeface.BOLD, to = "BOLD"),
            @ViewDebug.IntToString(from = Typeface.ITALIC, to = "ITALIC"),
            @ViewDebug.IntToString(from = Typeface.BOLD_ITALIC, to = "BOLD_ITALIC")
    })
    private int getTypefaceStyle() {
        Typeface typeface = mTextPaint.getTypeface();
        return typeface != null ? typeface.getStyle() : Typeface.NORMAL;
    }

    /**
     * Set the default text size to the given value, interpreted as "scaled
     * pixel" units.  This size is adjusted based on the current density and
     * user font size preference.
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

        mTextSizeUnit = unit;
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
     * Gets the text size unit defined by the developer. It may be specified in resources or be
     * passed as the unit argument of {@link #setTextSize(int, float)} at runtime.
     *
     * @return the dimension type of the text size unit originally defined.
     * @see TypedValue#TYPE_DIMENSION
     */
    public int getTextSizeUnit() {
        return mTextSizeUnit;
    }

    /**
     * Gets the extent by which text should be stretched horizontally.
     * This will usually be 1.0.
     * @return The horizontal scale factor.
     */
    @InspectableProperty
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
        mOriginalTypeface = tf;
        // (EW) mFontWeightAdjustment is only set starting in S
        if (mFontWeightAdjustment != 0
                && mFontWeightAdjustment != Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED) {
            if (tf == null) {
                tf = Typeface.DEFAULT;
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    int newWeight = Math.min(
                            Math.max(tf.getWeight() + mFontWeightAdjustment, FONT_WEIGHT_MIN),
                            FONT_WEIGHT_MAX);
                    int typefaceStyle = tf.getStyle();
                    boolean italic = (typefaceStyle & Typeface.ITALIC) != 0;
                    tf = Typeface.create(tf, newWeight, italic);
                }
            }
        }
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
    @InspectableProperty
    public Typeface getTypeface() {
        return mOriginalTypeface;
    }

    /**
     * Set the EditText's elegant height metrics flag. This setting selects font
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
    @InspectableProperty
    public boolean isFallbackLineSpacing() {
        return mUseFallbackLineSpacing;
    }

    /**
     * Get the value of the EditText's elegant height metrics flag. This setting selects font
     * variants that have not been compacted to fit Latin-based vertical
     * metrics, and also increases top and bottom bounds to provide more space.
     * @return {@code true} if the elegant height metrics flag is set.
     *
     * @see #setElegantTextHeight(boolean)
     * @see Paint#setElegantTextHeight(boolean)
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @InspectableProperty
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
    @InspectableProperty
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
    @InspectableProperty
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
     * Sets the break strategy for breaking paragraphs into lines. The default value for EditText
     * is {@link Layout#BREAK_STRATEGY_SIMPLE} to avoid the text "dancing" when being edited.
     * <p/>
     * Enabling hyphenation with either using {@link Layout#HYPHENATION_FREQUENCY_NORMAL} or
     * {@link Layout#HYPHENATION_FREQUENCY_FULL} while line breaking is set to one of
     * {@link Layout#BREAK_STRATEGY_BALANCED}, {@link Layout#BREAK_STRATEGY_HIGH_QUALITY}
     * improves the structure of text layout however has performance impact and requires more time
     * to do the text layout.
     *
     * @attr ref android.R.styleable#TextView_breakStrategy
     * @see #getBreakStrategy()
     * @see #setHyphenationFrequency(int)
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public void setBreakStrategy(@HiddenLayout.BreakStrategy int breakStrategy) {
        mBreakStrategy = breakStrategy;
        if (mLayout != null) {
            nullLayouts();
            requestLayout();
            invalidate();
        }
    }

    /**
     * Gets the current strategy for breaking paragraphs into lines.
     * @return the current strategy for breaking paragraphs into lines.
     *
     * @attr ref android.R.styleable#TextView_breakStrategy
     * @see #setBreakStrategy(int)
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    @InspectableProperty(enumMapping = {
            @EnumEntry(name = "simple", value = Layout.BREAK_STRATEGY_SIMPLE),
            @EnumEntry(name = "high_quality", value = Layout.BREAK_STRATEGY_HIGH_QUALITY),
            @EnumEntry(name = "balanced", value = Layout.BREAK_STRATEGY_BALANCED)
    })
    public int getBreakStrategy() {
        return mBreakStrategy;
    }

    /**
     * Sets the frequency of automatic hyphenation to use when determining word breaks.
     * The default value for EditText is {@link Layout#HYPHENATION_FREQUENCY_NONE}. Note that the
     * default hyphenation frequency value is set from the theme.
     * <p/>
     * Enabling hyphenation with either using {@link Layout#HYPHENATION_FREQUENCY_NORMAL} or
     * {@link Layout#HYPHENATION_FREQUENCY_FULL} while line breaking is set to one of
     * {@link Layout#BREAK_STRATEGY_BALANCED}, {@link Layout#BREAK_STRATEGY_HIGH_QUALITY}
     * improves the structure of text layout however has performance impact and requires more time
     * to do the text layout.
     * <p/>
     * Note: Before Android Q, in the theme hyphenation frequency is set to
     * {@link Layout#HYPHENATION_FREQUENCY_NORMAL}. The default value is changed into
     * {@link Layout#HYPHENATION_FREQUENCY_NONE} on Q.
     *
     * @param hyphenationFrequency the hyphenation frequency to use, one of
     *                             {@link Layout#HYPHENATION_FREQUENCY_NONE},
     *                             {@link Layout#HYPHENATION_FREQUENCY_NORMAL},
     *                             {@link Layout#HYPHENATION_FREQUENCY_FULL}
     * @attr ref android.R.styleable#TextView_hyphenationFrequency
     * @see #getHyphenationFrequency()
     * @see #getBreakStrategy()
     */
    public void setHyphenationFrequency(
            @HiddenLayout.HyphenationFrequency int hyphenationFrequency) {
        mHyphenationFrequency = hyphenationFrequency;
        if (mLayout != null) {
            nullLayouts();
            requestLayout();
            invalidate();
        }
    }

    /**
     * Gets the current frequency of automatic hyphenation to be used when determining word breaks.
     * @return the current frequency of automatic hyphenation to be used when determining word
     * breaks.
     *
     * @attr ref android.R.styleable#TextView_hyphenationFrequency
     * @see #setHyphenationFrequency(int)
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    @InspectableProperty(enumMapping = {
            @EnumEntry(name = "none", value = Layout.HYPHENATION_FREQUENCY_NONE),
            @EnumEntry(name = "normal", value = Layout.HYPHENATION_FREQUENCY_NORMAL),
            @EnumEntry(name = "full", value = Layout.HYPHENATION_FREQUENCY_FULL)
    })
    public int getHyphenationFrequency() {
        return mHyphenationFrequency;
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
     *   EditText editText = (EditText) findViewById(R.id.editText);
     *   editText.setFontVariationSettings("'wdth' 150");
     * </code>
     * </pre>
     * </li>
     *
     * <li>Set the font slant to 20 degrees and ask for italic style.
     * <pre>
     * <code>
     *   EditText editText = (EditText) findViewById(R.id.editText);
     *   editText.setFontVariationSettings("'slnt' 20, 'ital' 1");
     * </code>
     * </pre>
     * </p>
     * </li>
     * </ul>
     *
     * @param fontVariationSettings font variation settings. You can pass null or empty string as
     *                              no variation settings.
     * @return true if the given settings is effective to at least one font file underlying this
     *         EditText. This function also returns true for empty settings string. Otherwise
     *         returns false.
     *
     * @throws IllegalArgumentException If given string is not a valid font variation settings
     *                                  format.
     *
     * @see #getFontVariationSettings()
     * @see FontVariationAxis
     *
     * @attr ref android.R.styleable#TextView_fontVariationSettings
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
     * Gets the text colors for the different states (normal, selected, focused) of the EditText.
     *
     * @see #setTextColor(ColorStateList)
     * @see #setTextColor(int)
     *
     * @attr ref android.R.styleable#TextView_textColor
     */
    @InspectableProperty(name = "textColor")
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
    @InspectableProperty(name = "textColorHighlight")
    @ColorInt
    public int getHighlightColor() {
        return mHighlightColor;
    }

    /**
     * Sets whether the soft input method will be made visible when this
     * EditText gets focused. The default is true.
     */
    public final void setShowSoftInputOnFocus(boolean show) {
        mEditor.mShowSoftInputOnFocus = show;
    }

    /**
     * Returns whether the soft input method will be made visible when this
     * EditText gets focused. The default is true.
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
    @InspectableProperty
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
    @InspectableProperty
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
    @InspectableProperty
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
    @InspectableProperty
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
     * EditText.
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
     * @return the color of the hint text, for the different states of this EditText.
     *
     * @see #setHintTextColor(ColorStateList)
     * @see #setHintTextColor(int)
     * @see #setTextColor(ColorStateList)
     *
     * @attr ref android.R.styleable#TextView_textColorHint
     */
    @InspectableProperty(name = "textColorHint")
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
     * in the EditText beyond what is required for the text itself.
     *
     * @see Gravity
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

            makeNewLayout(want, hintWant, UNKNOWN_BORING,
                    getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight(),
                    true);
        }
    }

    /**
     * Returns the horizontal and vertical alignment of this EditText.
     *
     * @see Gravity
     * @attr ref android.R.styleable#TextView_gravity
     */
    @InspectableProperty(valueType = InspectableProperty.ValueType.GRAVITY)
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
     * @see #setHorizontallyScrolling(boolean)
     */
    @InspectableProperty(name = "scrollHorizontally")
    public final boolean isHorizontallyScrollable() {
        return mHorizontallyScrolling;
    }

    /**
     * Sets the height of the EditText to be at least {@code minLines} tall.
     * <p>
     * This value is used for height calculation if LayoutParams does not force EditText to have an
     * exact height. Setting this value overrides other previous minimum height configurations such
     * as {@link #setMinHeight(int)} or {@link #setHeight(int)}.
     *
     * @param minLines the minimum height of EditText in terms of number of lines
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
     * Returns the minimum height of EditText in terms of number of lines or -1 if the minimum
     * height was set using {@link #setMinHeight(int)} or {@link #setHeight(int)}.
     *
     * @return the minimum height of EditText in terms of number of lines or -1 if the minimum
     *         height is not defined in lines
     *
     * @see #setMinLines(int)
     * @see #setLines(int)
     *
     * @attr ref android.R.styleable#TextView_minLines
     */
    @InspectableProperty
    public int getMinLines() {
        return mMinMode == LINES ? mMinimum : -1;
    }

    /**
     * Sets the height of the EditText to be at least {@code minPixels} tall.
     * <p>
     * This value is used for height calculation if LayoutParams does not force EditText to have an
     * exact height. Setting this value overrides previous minimum height configurations such as
     * {@link #setMinLines(int)} or {@link #setLines(int)}.
     * <p>
     * The value given here is different than {@link #setMinimumHeight(int)}. Between
     * {@code minHeight} and the value set in {@link #setMinimumHeight(int)}, the greater one is
     * used to decide the final height.
     *
     * @param minPixels the minimum height of EditText in terms of pixels
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
     * Returns the minimum height of EditText in terms of pixels or -1 if the minimum height was
     * set using {@link #setMinLines(int)} or {@link #setLines(int)}.
     *
     * @return the minimum height of EditText in terms of pixels or -1 if the minimum height is not
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
     * Sets the height of the EditText to be at most {@code maxLines} tall.
     * <p>
     * This value is used for height calculation if LayoutParams does not force EditText to have an
     * exact height. Setting this value overrides previous maximum height configurations such as
     * {@link #setMaxHeight(int)} or {@link #setLines(int)}.
     *
     * @param maxLines the maximum height of EditText in terms of number of lines
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
     * Returns the maximum height of EditText in terms of number of lines or -1 if the
     * maximum height was set using {@link #setMaxHeight(int)} or {@link #setHeight(int)}.
     *
     * @return the maximum height of EditText in terms of number of lines. -1 if the maximum height
     *         is not defined in lines.
     *
     * @see #setMaxLines(int)
     * @see #setLines(int)
     *
     * @attr ref android.R.styleable#TextView_maxLines
     */
    @InspectableProperty
    public int getMaxLines() {
        return mMaxMode == LINES ? mMaximum : -1;
    }

    /**
     * Sets the height of the EditText to be at most {@code maxPixels} tall.
     * <p>
     * This value is used for height calculation if LayoutParams does not force EditText to have an
     * exact height. Setting this value overrides previous maximum height configurations such as
     * {@link #setMaxLines(int)} or {@link #setLines(int)}.
     *
     * @param maxPixels the maximum height of EditText in terms of pixels
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
     * Returns the maximum height of EditText in terms of pixels or -1 if the maximum height was
     * set using {@link #setMaxLines(int)} or {@link #setLines(int)}.
     *
     * @return the maximum height of EditText in terms of pixels or -1 if the maximum height
     *         is not defined in pixels
     *
     * @see #setMaxHeight(int)
     * @see #setHeight(int)
     *
     * @attr ref android.R.styleable#TextView_maxHeight
     */
    @InspectableProperty
    public int getMaxHeight() {
        return mMaxMode == PIXELS ? mMaximum : -1;
    }

    /**
     * Sets the height of the EditText to be exactly {@code lines} tall.
     * <p>
     * This value is used for height calculation if LayoutParams does not force EditText to have an
     * exact height. Setting this value overrides previous minimum/maximum height configurations
     * such as {@link #setMinLines(int)} or {@link #setMaxLines(int)}.
     *
     * @param lines the exact height of the EditText in terms of lines
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
     * Sets the height of the EditText to be exactly <code>pixels</code> tall.
     * <p>
     * This value is used for height calculation if LayoutParams does not force EditText to have an
     * exact height. Setting this value overrides previous minimum/maximum height configurations
     * such as {@link #setMinHeight(int)} or {@link #setMaxHeight(int)}.
     *
     * @param pixels the exact height of the EditText in terms of pixels
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
     * Sets the width of the EditText to be at least {@code minEms} wide.
     * <p>
     * This value is used for width calculation if LayoutParams does not force EditText to have an
     * exact width. Setting this value overrides previous minimum width configurations such as
     * {@link #setMinWidth(int)} or {@link #setWidth(int)}.
     *
     * @param minEms the minimum width of EditText in terms of ems
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
     * Returns the minimum width of EditText in terms of ems or -1 if the minimum width was set
     * using {@link #setMinWidth(int)} or {@link #setWidth(int)}.
     *
     * @return the minimum width of EditText in terms of ems. -1 if the minimum width is not
     *         defined in ems
     *
     * @see #setMinEms(int)
     * @see #setEms(int)
     *
     * @attr ref android.R.styleable#TextView_minEms
     */
    @InspectableProperty
    public int getMinEms() {
        return mMinWidthMode == EMS ? mMinWidth : -1;
    }

    /**
     * Sets the width of the EditText to be at least {@code minPixels} wide.
     * <p>
     * This value is used for width calculation if LayoutParams does not force EditText to have an
     * exact width. Setting this value overrides previous minimum width configurations such as
     * {@link #setMinEms(int)} or {@link #setEms(int)}.
     * <p>
     * The value given here is different than {@link #setMinimumWidth(int)}. Between
     * {@code minWidth} and the value set in {@link #setMinimumWidth(int)}, the greater one is used
     * to decide the final width.
     *
     * @param minPixels the minimum width of EditText in terms of pixels
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
     * Returns the minimum width of EditText in terms of pixels or -1 if the minimum width was set
     * using {@link #setMinEms(int)} or {@link #setEms(int)}.
     *
     * @return the minimum width of EditText in terms of pixels or -1 if the minimum width is not
     *         defined in pixels
     *
     * @see #setMinWidth(int)
     * @see #setWidth(int)
     *
     * @attr ref android.R.styleable#TextView_minWidth
     */
    @InspectableProperty
    public int getMinWidth() {
        return mMinWidthMode == PIXELS ? mMinWidth : -1;
    }

    /**
     * Sets the width of the EditText to be at most {@code maxEms} wide.
     * <p>
     * This value is used for width calculation if LayoutParams does not force EditText to have an
     * exact width. Setting this value overrides previous maximum width configurations such as
     * {@link #setMaxWidth(int)} or {@link #setWidth(int)}.
     *
     * @param maxEms the maximum width of EditText in terms of ems
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
     * Returns the maximum width of EditText in terms of ems or -1 if the maximum width was set
     * using {@link #setMaxWidth(int)} or {@link #setWidth(int)}.
     *
     * @return the maximum width of EditText in terms of ems or -1 if the maximum width is not
     *         defined in ems
     *
     * @see #setMaxEms(int)
     * @see #setEms(int)
     *
     * @attr ref android.R.styleable#TextView_maxEms
     */
    @InspectableProperty
    public int getMaxEms() {
        return mMaxWidthMode == EMS ? mMaxWidth : -1;
    }

    /**
     * Sets the width of the EditText to be at most {@code maxPixels} wide.
     * <p>
     * This value is used for width calculation if LayoutParams does not force EditText to have an
     * exact width. Setting this value overrides previous maximum width configurations such as
     * {@link #setMaxEms(int)} or {@link #setEms(int)}.
     *
     * @param maxPixels the maximum width of EditText in terms of pixels
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
     * Returns the maximum width of EditText in terms of pixels or -1 if the maximum width was set
     * using {@link #setMaxEms(int)} or {@link #setEms(int)}.
     *
     * @return the maximum width of EditText in terms of pixels. -1 if the maximum width is not
     *         defined in pixels
     *
     * @see #setMaxWidth(int)
     * @see #setWidth(int)
     *
     * @attr ref android.R.styleable#TextView_maxWidth
     */
    @InspectableProperty
    public int getMaxWidth() {
        return mMaxWidthMode == PIXELS ? mMaxWidth : -1;
    }

    /**
     * Sets the width of the EditText to be exactly {@code ems} wide.
     *
     * This value is used for width calculation if LayoutParams does not force EditText to have an
     * exact width. Setting this value overrides previous minimum/maximum configurations such as
     * {@link #setMinEms(int)} or {@link #setMaxEms(int)}.
     *
     * @param ems the exact width of the EditText in terms of ems
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
     * Sets the width of the EditText to be exactly {@code pixels} wide.
     * <p>
     * This value is used for width calculation if LayoutParams does not force EditText to have an
     * exact width. Setting this value overrides previous minimum/maximum width configurations
     * such as {@link #setMinWidth(int)} or {@link #setMaxWidth(int)}.
     *
     * @param pixels the exact width of the EditText in terms of pixels
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
     * Sets line spacing for this EditText.  Each line other than the last line will have its height
     * multiplied by {@code mult} and have {@code add} added to it.
     *
     * @param add The value in pixels that should be added to each line other than the last line.
     *            This will be applied after the multiplier
     * @param multiplier The value by which each line height other than the last line will be
     *                   multiplied by
     *
     * @attr ref android.R.styleable#TextView_lineSpacingExtra
     * @attr ref android.R.styleable#TextView_lineSpacingMultiplier
     */
    public void setLineSpacing(float add, float multiplier) {
        if (mSpacingAdd != add || mSpacingMultiplier != multiplier) {
            mSpacingAdd = add;
            mSpacingMultiplier = multiplier;

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
    @InspectableProperty
    public float getLineSpacingMultiplier() {
        return mSpacingMultiplier;
    }

    /**
     * Gets the line spacing extra space
     *
     * @return the extra space that is added to the height of each lines of this EditText.
     *
     * @see #setLineSpacing(float, float)
     * @see #getLineSpacingMultiplier()
     *
     * @attr ref android.R.styleable#TextView_lineSpacingExtra
     */
    @InspectableProperty
    public float getLineSpacingExtra() {
        return mSpacingAdd;
    }

    /**
     * Sets an explicit line height for this EditText. This is equivalent to the vertical distance
     * between subsequent baselines in the EditText.
     *
     * @param lineHeight the line height in pixels
     *
     * @see #setLineSpacing(float, float)
     * @see #getLineSpacingExtra()
     *
     * @attr ref android.R.styleable#TextView_lineHeight
     */
    public void setLineHeight(@Px @IntRange(from = 0) int lineHeight) {
        Preconditions.checkArgumentNonnegative(lineHeight);

        final int fontHeight = getPaint().getFontMetricsInt(null);
        // Make sure we don't setLineSpacing if it's not needed to avoid unnecessary redraw.
        if (lineHeight != fontHeight) {
            // Set lineSpacingExtra by the difference of lineSpacing with lineHeight
            setLineSpacing(lineHeight - fontHeight, 1f);
        }
    }

    /**
     * Convenience method to append the specified text to the EditText's
     * display buffer.
     *
     * @param text text to be appended to the already displayed text
     */
    public final void append(CharSequence text) {
        append(text, 0, text.length());
    }

    /**
     * Convenience method to append the specified text slice to the EditText's
     * display buffer.
     *
     * @param text text to be appended to the already displayed text
     * @param start the index of the first character in the {@code text}
     * @param end the index of the character following the last character in the {@code text}
     *
     * @see Appendable#append(CharSequence, int, int)
     */
    public void append(CharSequence text, int start, int end) {
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
    protected void drawableStateChanged() {
        super.drawableStateChanged();

        if (mTextColor != null && mTextColor.isStateful()
                || (mHintTextColor != null && mHintTextColor.isStateful())) {
            updateTextColors();
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        // Save state if we are forced to
        boolean hasSelection = false;
        int start = -1;
        int end = -1;

        start = getSelectionStart();
        end = getSelectionEnd();
        if (start >= 0 || end >= 0) {
            // Or save state if there is a selection
            hasSelection = true;
        }

        SavedState ss = new SavedState(superState);

        final Spannable sp = new SpannableStringBuilder(mText);

        removeMisspelledSpans(sp);
        sp.removeSpan(mEditor.mSuggestionRangeSpan);

        ss.text = sp;

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
            int len = mText.length();

            if (ss.selStart > len || ss.selEnd > len) {
                String restored = "";

                if (ss.text != null) {
                    restored = "(restored) ";
                }

                Log.e(LOG_TAG, "Saved cursor position " + ss.selStart + "/" + ss.selEnd
                        + " out of range for " + restored + "text " + mText);
            } else {
                Selection.setSelection(mText, ss.selStart, ss.selEnd);

                if (ss.frozenWithFocus) {
                    mEditor.mFrozenWithFocus = true;
                }
            }
        }

        if (ss.editorState != null) {
            mEditor.restoreInstanceState(ss.editorState);
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    /**
     * Sets the text to be displayed. EditText <em>does not</em> accept
     * HTML-like formatting, which you can do with text strings in XML resource files.
     * To style your strings, attach android.text.style.* objects to a
     * {@link android.text.SpannableString}, or see the
     * <a href="{@docRoot}guide/topics/resources/available-resources.html#stringresources">
     * Available Resource Types</a> documentation for an example of setting
     * formatted text in the XML resource file.
     * <p/>
     * When required, EditText will use {@link android.text.Editable.Factory} to create final or
     * intermediate {@link Editable Editables}.
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

    private void setText(CharSequence text, boolean notifyBefore, int oldLength) {
        mTextSetFromXmlOrResourceId = false;
        if (text == null) {
            text = "";
        }

        // If suggestions are not enabled, remove the suggestion spans from the text
        if (!isSuggestionsEnabled()) {
            text = removeSuggestionSpans(text);
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
            oldLength = mText.length();
            sendBeforeTextChanged(mText, 0, oldLength, text.length());
        }

        boolean needEditableForNotification = false;

        if (mListeners != null && mListeners.size() != 0) {
            needEditableForNotification = true;
        }

        mEditor.forgetUndoRedo();
        mEditor.scheduleRestartInputForSetText();
        Editable t = createEditable(text);
        text = t;

        setFilters(t, mFilters);

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

        Spannable sp = (Spannable) text;

        // Remove any ChangeWatchers that might have come from other EditTexts.
        final ChangeWatcher[] watchers = sp.getSpans(0, sp.length(), ChangeWatcher.class);
        final int count = watchers.length;
        for (int i = 0; i < count; i++) {
            sp.removeSpan(watchers[i]);
        }

        if (mChangeWatcher == null) mChangeWatcher = new ChangeWatcher();

        sp.setSpan(mChangeWatcher, 0, textLength, Spanned.SPAN_INCLUSIVE_INCLUSIVE
                | (CHANGE_WATCHER_PRIORITY << Spanned.SPAN_PRIORITY_SHIFT));

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

        if (mLayout != null) {
            checkForRelayout();
        }

        sendOnTextChanged(text, 0, oldLength, textLength);
        onTextChanged(text, 0, oldLength, textLength);

        if (needEditableForNotification) {
            sendAfterTextChanged((Editable) text);
        } else {
            notifyListeningManagersAfterTextChanged();
        }

        // SelectionModifierCursorController depends on textCanBeSelected, which depends on text
        mEditor.prepareCursorControllers();

        mEditor.maybeFireScheduledRestartInputForSetText();
    }

    /**
     * Sets the EditText to display the specified slice of the specified
     * char array. You must promise that you will not change the contents
     * of the array except for right before another call to setText(),
     * since the EditText has no way to know that the text
     * has changed and that it needs to invalidate and re-layout.
     *
     * @param text char array to be displayed
     * @param start start index in the char array
     * @param len length of char count after {@code start}
     */
    public final void setText(@NonNull char[] text, int start, int len) {
        int oldLength = 0;

        if (start < 0 || len < 0 || start + len > text.length) {
            throw new IndexOutOfBoundsException(start + ", " + len);
        }

        /*
         * We must do the before-notification here ourselves because if
         * the old text is a CharWrapper we destroy it before calling
         * into the normal path.
         */
        oldLength = mText.length();
        sendBeforeTextChanged(mText, 0, oldLength, len);

        if (mCharWrapper == null) {
            mCharWrapper = new CharWrapper(text, start, len);
        } else {
            mCharWrapper.set(text, start, len);
        }

        setText(mCharWrapper, false, oldLength);
    }

    /**
     * Sets the text to be displayed but retains the cursor position. Same as
     * {@link #setText(CharSequence)} except that the cursor position (if any) is retained in the
     * new text.
     * <p/>
     * When required, EditText will use {@link android.text.Editable.Factory} to create final or
     * intermediate {@link Editable Editables}.
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
            Selection.setSelection(mText,
                                   Math.max(0, Math.min(start, len)),
                                   Math.max(0, Math.min(end, len)));
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
     * Sets the text to be displayed when the text of the EditText is empty.
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
    }

    /**
     * Sets the text to be displayed when the text of the EditText is empty,
     * from a resource.
     *
     * @attr ref android.R.styleable#TextView_hint
     */
    public final void setHint(@StringRes int resId) {
        mHintId = resId;
        setHint(getContext().getResources().getText(resId));
    }

    /**
     * Returns the hint that is displayed when the text of the EditText
     * is empty.
     *
     * @attr ref android.R.styleable#TextView_hint
     */
    @InspectableProperty
    @ViewDebug.CapturedViewProperty
    public CharSequence getHint() {
        return mHint;
    }

    /**
     * Returns if the text is constrained to a single horizontally scrolling line ignoring new
     * line characters instead of letting it wrap onto multiple lines.
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
    @NonNull
    CharSequence removeSuggestionSpans(@NonNull CharSequence text) {
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
            applySingleLine(singleLine, !isPassword, true, true);
        }

        if (!isSuggestionsEnabled()) {
            setTextInternal(removeSuggestionSpans(mText));
        }

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
        if (!mUseInternationalizedInput || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
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
            boolean autoText = (type & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) != 0;
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
            input = TextKeyListener.getInstance(autoText, cap);
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
    @InspectableProperty(flagMapping = {
            @FlagEntry(name = "none", mask = 0xffffffff, target = InputType.TYPE_NULL),
            @FlagEntry(
                    name = "text",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION,
                    target = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL),
            @FlagEntry(
                    name = "textUri",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION,
                    target = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI),
            @FlagEntry(
                    name = "textEmailAddress",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION,
                    target = InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
            @FlagEntry(
                    name = "textEmailSubject",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION,
                    target = InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT),
            @FlagEntry(
                    name = "textShortMessage",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION,
                    target = InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE),
            @FlagEntry(
                    name = "textLongMessage",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION,
                    target = InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE),
            @FlagEntry(
                    name = "textPersonName",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION,
                    target = InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_VARIATION_PERSON_NAME),
            @FlagEntry(
                    name = "textPostalAddress",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION,
                    target = InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS),
            @FlagEntry(
                    name = "textPassword",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION,
                    target = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD),
            @FlagEntry(
                    name = "textVisiblePassword",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION,
                    target = InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD),
            @FlagEntry(
                    name = "textWebEditText",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION,
                    target = InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT),
            @FlagEntry(
                    name = "textFilter",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION,
                    target = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_FILTER),
            @FlagEntry(
                    name = "textPhonetic",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION,
                    target = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PHONETIC),
            @FlagEntry(
                    name = "textWebEmailAddress",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION,
                    target = InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS),
            @FlagEntry(
                    name = "textWebPassword",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION,
                    target = InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD),
            @FlagEntry(
                    name = "number",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION,
                    target = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL),
            @FlagEntry(
                    name = "numberPassword",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION,
                    target = InputType.TYPE_CLASS_NUMBER
                            | InputType.TYPE_NUMBER_VARIATION_PASSWORD),
            @FlagEntry(
                    name = "phone",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION,
                    target = InputType.TYPE_CLASS_PHONE),
            @FlagEntry(
                    name = "datetime",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION,
                    target = InputType.TYPE_CLASS_DATETIME
                            | InputType.TYPE_DATETIME_VARIATION_NORMAL),
            @FlagEntry(
                    name = "date",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION,
                    target = InputType.TYPE_CLASS_DATETIME
                            | InputType.TYPE_DATETIME_VARIATION_DATE),
            @FlagEntry(
                    name = "time",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION,
                    target = InputType.TYPE_CLASS_DATETIME
                            | InputType.TYPE_DATETIME_VARIATION_TIME),
            @FlagEntry(
                    name = "textCapCharacters",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_FLAGS,
                    target = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS),
            @FlagEntry(
                    name = "textCapWords",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_FLAGS,
                    target = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS),
            @FlagEntry(
                    name = "textCapSentences",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_FLAGS,
                    target = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES),
            @FlagEntry(
                    name = "textAutoCorrect",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_FLAGS,
                    target = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT),
            @FlagEntry(
                    name = "textAutoComplete",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_FLAGS,
                    target = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE),
            @FlagEntry(
                    name = "textMultiLine",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_FLAGS,
                    target = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE),
            @FlagEntry(
                    name = "textImeMultiLine",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_FLAGS,
                    target = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE),
            @FlagEntry(
                    name = "textNoSuggestions",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_FLAGS,
                    target = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS),
            @FlagEntry(
                    name = "numberSigned",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_FLAGS,
                    target = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED),
            @FlagEntry(
                    name = "numberDecimal",
                    mask = InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_FLAGS,
                    target = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL),
    })
    public int getInputType() {
        return mEditor.mInputType;
    }

    /**
     * Change the editor type integer associated with the text view, which
     * is reported to an Input Method Editor (IME) with {@link EditorInfo#imeOptions}
     * when it has focus.
     * @see #getImeOptions
     * @see EditorInfo
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
     * @see EditorInfo
     */
    public int getImeOptions() {
        return mEditor.mInputContentType != null
                ? mEditor.mInputContentType.imeOptions : EditorInfo.IME_NULL;
    }

    /**
     * Change the custom IME action associated with the text view, which
     * will be reported to an IME with {@link EditorInfo#actionLabel}
     * and {@link EditorInfo#actionId} when it has focus.
     * @see #getImeActionLabel
     * @see #getImeActionId
     * @see EditorInfo
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
     * @see EditorInfo
     */
    @InspectableProperty
    public CharSequence getImeActionLabel() {
        return mEditor.mInputContentType != null ? mEditor.mInputContentType.imeActionLabel : null;
    }

    /**
     * Get the IME action ID previous set with {@link #setImeActionLabel}.
     *
     * @see #setImeActionLabel
     * @see EditorInfo
     */
    @InspectableProperty
    public int getImeActionId() {
        return mEditor.mInputContentType != null ? mEditor.mInputContentType.imeActionId : 0;
    }

    /**
     * Set a special listener to be called when an action is performed
     * on the text view.  This will be called when the enter key is pressed,
     * or when an action supplied to the IME is selected by the user.  Setting
     * this means that the normal hard key event will not insert a newline
     * into the text view, even if it is multi-line; holding down the ALT
     * modifier will, however, allow the user to insert a newline character.
     */
    public void setOnEditorActionListener(OnEditorActionListener l) {
        mEditor.createInputContentTypeIfNeeded();
        mEditor.mInputContentType.onEditorActionListener = l;
    }

    /**
     * Called when an attached input method calls
     * {@link InputConnection#performEditorAction(int)
     * InputConnection.performEditorAction()}
     * for this text view.  The default implementation will call your action
     * listener supplied to {@link #setOnEditorActionListener}, or perform
     * a standard operation for {@link EditorInfo#IME_ACTION_NEXT
     * EditorInfo.IME_ACTION_NEXT}, {@link EditorInfo#IME_ACTION_PREVIOUS
     * EditorInfo.IME_ACTION_PREVIOUS}, or {@link EditorInfo#IME_ACTION_DONE
     * EditorInfo.IME_ACTION_DONE}.
     *
     * <p>For backwards compatibility, if no IME options have been set and the
     * text view would not normally advance focus on enter, then
     * the NEXT and DONE actions received here will be turned into an enter
     * key down/up pair to go through the normal key handling.
     *
     * @param actionCode The code of the action being performed.
     *
     * @see #setOnEditorActionListener
     */
    public void onEditorAction(int actionCode) {
        final Editor.InputContentType ict = mEditor.mInputContentType;
        if (ict != null) {
            if (ict.onEditorActionListener != null) {
                if (ict.onEditorActionListener.onEditorAction(this,
                        actionCode, null)) {
                    return;
                }
            }

            // This is the handling for some default action.
            // Note that for backwards compatibility we don't do this
            // default handling if explicit ime options have not been given,
            // instead turning this into the normal enter key codes that an
            // app may be expecting.
            if (actionCode == EditorInfo.IME_ACTION_NEXT) {
                View v = focusSearchRelative(FOCUS_FORWARD);
                if (v != null) {
                    if (!v.requestFocus(FOCUS_FORWARD)) {
                        throw new IllegalStateException("focus search returned a view "
                                + "that wasn't able to take focus!");
                    }
                }
                return;

            } else if (actionCode == EditorInfo.IME_ACTION_PREVIOUS) {
                View v = focusSearchRelative(FOCUS_BACKWARD);
                if (v != null) {
                    if (!v.requestFocus(FOCUS_BACKWARD)) {
                        throw new IllegalStateException("focus search returned a view "
                                + "that wasn't able to take focus!");
                    }
                }
                return;

            } else if (actionCode == EditorInfo.IME_ACTION_DONE) {
                InputMethodManager imm = getInputMethodManager();
                if (imm != null && imm.isActive(this)) {
                    imm.hideSoftInputFromWindow(getWindowToken(), 0);
                }
                return;
            }
        }

        // (EW) the AOSP version called View#getViewRootImpl and then called
        // ViewRootImpl#dispatchKeyFromIme for an enter key down and up event, but ViewRootImpl is
        // hidden and ViewRootImpl#dispatchKeyFromIme is marked as UnsupportedAppUsage, so we can't
        // do that. that seems to just shift focus to the next field, so just setting the focus
        // (from above) may be good enough. the comment above indicates that the enter key is for
        // backwards compatibility for apps expecting that, but since apps aren't using this, that
        // might not really be necessary. the enter key functionality doesn't even seem to go to the
        // next field in some layouts, so this does deviate in functionality slightly, but this
        // might actually be better.
        View v = focusSearchRelative(FOCUS_FORWARD);
        if (v != null) {
            if (!v.requestFocus(FOCUS_FORWARD)) {
                throw new IllegalStateException("focus search returned a view "
                        + "that wasn't able to take focus!");
            }
        }
    }

    //TODO: (EW) View#focusSearch says is marked as only allowing @FocusRealDirection (like
    // @FocusDirection, but without forward/backward), but based on looking into the implementation
    // (as of S), ViewGroup#focusSearch and ViewRootImpl#focusSearch (only implementers of
    // ViewParent that I found) both call FocusFinder#findNextFocus, which doesn't have the same
    // limitation, and supports FOCUS_FORWARD and FOCUS_BACKWARD. the AOSP version calls
    // View#focusSearch with FOCUS_FORWARD and FOCUS_BACKWARD, which show up as errors, even though
    // it still runs, so I'm adding this wrapper to clean up the errors, but it might be a better
    // idea to actually respect the values that it claims to support and find some alternative to
    // handle this.
    public View focusSearchRelative(@FocusDirection int direction) {
        return focusSearch(direction);
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
    @InspectableProperty
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
     * @see EditorInfo#hintLocales
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void setImeHintLocales(@Nullable LocaleList hintLocales) {
        mEditor.createInputContentTypeIfNeeded();
        mEditor.mInputContentType.imeHintLocales = hintLocales;
        if (mUseInternationalizedInput && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            changeListenerLocaleTo(hintLocales == null ? null : hintLocales.get(0));
        }
    }

    /**
     * @return The current languages list "hint". {@code null} when no "hint" is available.
     * @see #setImeHintLocales(LocaleList)
     * @see EditorInfo#hintLocales
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

        setFilters(mText, filters);
    }

    /**
     * Sets the list of input filters on the specified Editable,
     * and includes mInput in the list if it is an InputFilter.
     */
    private void setFilters(Editable e, InputFilter[] filters) {
        final boolean undoFilter = mEditor.mUndoInputFilter != null;
        final boolean keyFilter = mEditor.mKeyListener instanceof InputFilter;
        int num = 0;
        if (undoFilter) num++;
        if (keyFilter) num++;
        if (num > 0) {
            InputFilter[] nf = new InputFilter[filters.length + num];

            System.arraycopy(filters, 0, nf, 0, filters.length);
            num = 0;
            if (undoFilter) {
                nf[filters.length] = mEditor.mUndoInputFilter;
                num++;
            }
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
        Insets opticalInsets = isLayoutModeOptical(getParent()) ? getOpticalInsets() : Insets.NONE;
        int padding = (l == mHintLayout)
                ? getCompoundPaddingTop() + getCompoundPaddingBottom()
                : getExtendedPaddingTop() + getExtendedPaddingBottom();
        return getMeasuredHeight() - padding + opticalInsets.top + opticalInsets.bottom;
    }

    int getVerticalOffset(boolean forceNormal) {
        int voffset = 0;
        final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;

        Layout l = mLayout;
        if (!forceNormal && mText.length() == 0 && mHintLayout != null) {
            l = mHintLayout;
        }

        if (gravity != Gravity.TOP) {
            int boxHeight = getBoxHeight(l);
            int textHeight = l.getHeight();

            if (textHeight < boxHeight) {
                if (gravity == Gravity.BOTTOM) {
                    voffset = boxHeight - textHeight;
                } else { // (gravity == Gravity.CENTER_VERTICAL)
                    voffset = (boxHeight - textHeight) >> 1;
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
            int boxHeight = getBoxHeight(l);
            int textHeight = l.getHeight();

            if (textHeight < boxHeight) {
                if (gravity == Gravity.TOP) {
                    voffset = boxHeight - textHeight;
                } else { // (gravity == Gravity.CENTER_VERTICAL)
                    voffset = (boxHeight - textHeight) >> 1;
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

    // (EW) the AOSP version overrides onDetachedFromWindowInternal, which is hidden and marked with
    // UnsupportedAppUsage. it's protected, and the only time View calls it is right after its only
    // call to onDetachedFromWindow, so putting the code here instead seems to be the best option
    // and is probably good enough.
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
        return mShadowRadius != 0;
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

    @Override
    public boolean hasOverlappingRendering() {
        // horizontal fading edge causes SaveLayerAlpha, which doesn't support alpha modulation
        return true;
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

        final int vSpace = bottom - top - compoundPaddingBottom - compoundPaddingTop;
        final int maxScrollY = mLayout.getHeight() - vSpace;

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

        int vOffsetText = 0;
        int vOffsetCursor = 0;

        // translate in by our padding
        /* short-circuit calling getVerticaOffset() */
        if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
            vOffsetText = getVerticalOffset(false);
            vOffsetCursor = getVerticalOffset(true);
        }
        canvas.translate(compoundPaddingLeft, extendedPaddingTop + vOffsetText);

        final int cursorOffsetVertical = vOffsetCursor - vOffsetText;

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

        if (isLayoutModeOptical(getParent())) {
            voffset -= getOpticalInsets().top;
        }

        return getExtendedPaddingTop() + voffset;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public PointerIcon onResolvePointerIcon(MotionEvent event, int pointerIndex) {
        if (isTextEditable()) {
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
    // ExtractEditText, that won't happen, so changed to private
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
            mEditor.mKeyListener.onKeyUp(this, mText, keyCode, up);
            while (--repeatCount > 0) {
                mEditor.mKeyListener.onKeyDown(this, mText, keyCode, down);
                mEditor.mKeyListener.onKeyUp(this, mText, keyCode, up);
            }

        } else if (which == KEY_DOWN_HANDLED_BY_MOVEMENT_METHOD) {
            // mMovement is not null from doKeyDown
            mMovement.onKeyUp(this, mText, keyCode, up);
            while (--repeatCount > 0) {
                mMovement.onKeyDown(this, mText, keyCode, down);
                mMovement.onKeyUp(this, mText, keyCode, up);
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
                    if (mEditor.mInputContentType != null) {
                        // If there is an action listener, given them a
                        // chance to consume the event.
                        if (mEditor.mInputContentType.onEditorActionListener != null
                                && mEditor.mInputContentType.onEditorActionListener.onEditorAction(
                                        this, EditorInfo.IME_NULL, event)) {
                            mEditor.mInputContentType.enterDown = true;
                            // We are consuming the enter key for them.
                            return KEY_EVENT_HANDLED;
                        }
                    }

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
                    final boolean handled = mEditor.mKeyListener.onKeyOther(this, mText,
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
                final boolean handled = mEditor.mKeyListener.onKeyDown(this, mText,
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
                    boolean handled = mMovement.onKeyOther(this, mText, otherEvent);
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
                if (mMovement.onKeyDown(this, mText, keyCode, event)) {
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
                        if (mMovement != null && mLayout != null && onCheckIsTextEditor()) {
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
                    if (mEditor.mInputContentType != null
                            && mEditor.mInputContentType.onEditorActionListener != null
                            && mEditor.mInputContentType.enterDown) {
                        mEditor.mInputContentType.enterDown = false;
                        if (mEditor.mInputContentType.onEditorActionListener.onEditorAction(
                                this, EditorInfo.IME_NULL, event)) {
                            return true;
                        }
                    }

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
            if (mEditor.mKeyListener.onKeyUp(this, mText, keyCode, event)) {
                return true;
            }
        }

        if (mMovement != null && mLayout != null) {
            if (mMovement.onKeyUp(this, mText, keyCode, event)) {
                return true;
            }
        }

        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        //TODO: (EW) this probably should be made to always be true since TYPE_NULL indicates text
        // is not editable, which doesn't make sense for an edit text. I'm leaving this for now to
        // allow #setKeyListener to pass null to allow an ellipsis to show (see comment in #init for
        // EditText_android_ellipsize), but it doesn't really make sense for an edit test to have no
        // key listener unless the view was disabled, so it probably makes more sense to do some of
        // that handling automatically when disabling the view and remove the option to specify the
        // input type as TYPE_NULL (probably use TYPE_CLASS_TEXT as the new default, which is
        // already done normally).
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
            if (com.wittmane.testingedittext.settings.Settings.shouldSkipExtractingText()) {
                // (EW) if InputConnection#getExtractedText returns null, no text is shown in the
                // full screen text field (landscape) so since we're forcing that to be null, we
                // should also block the full screen view, since that would just be broken
                outAttrs.imeOptions |= EditorInfo.IME_FLAG_NO_EXTRACT_UI;
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

            // (EW) the AOSP version starting in S adds
            // EditorInfo.IME_INTERNAL_FLAG_APP_WINDOW_PORTRAIT to outAttrs.internalImeOptions if
            // the orientation is portrait, but both outAttrs.internalImeOptions and
            // EditorInfo.IME_INTERNAL_FLAG_APP_WINDOW_PORTRAIT are hidden.
            // EditorInfo#internalImeOptions is documented as being the same as
            // EditorInfo#imeOptions but for framework's internal-use only. I only found it being
            // used in InputMethodService#onEvaluateFullscreenMode to prevent fullscreen mode. we
            // could add EditorInfo.IME_FLAG_NO_FULLSCREEN to outAttrs.imeOptions to achieve this,
            // but that might have other negative effects, so until I know that there is a real
            // issue for a non-framework view, it will be skipped. maybe there is a reason that it
            // would only be necessary for the framework and not have any alternative necessary
            // here.

            if (isMultilineInputType(outAttrs.inputType)) {
                // Multi-line text editors should always show an enter key.
                outAttrs.imeOptions |= EditorInfo.IME_FLAG_NO_ENTER_ACTION;
            }
            outAttrs.hintText = mHint;

            // (EW) the AOSP version starting in Q set outAttrs.targetInputMethodUser, which is
            // hidden and requires the permission INTERACT_ACROSS_USERS_FULL. there are similar
            // difficulties in managing mTextOperationUser like the AOSP version does, so I don't
            // think there is anything we can do here.

            EditableInputConnection ic = new EditableInputConnection(this);
            outAttrs.initialSelStart = getSelectionStart();
            outAttrs.initialSelEnd = getSelectionEnd();
            outAttrs.initialCapsMode = ic.getCursorCapsMode(getInputType());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                outAttrs.setInitialSurroundingText(mText);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                outAttrs.contentMimeTypes = getReceiveContentMimeTypes();
            }
            mInputConnection = ic;
            return ic.createWrapperIfNecessary();
        }
        mInputConnection = null;
        return null;
    }

    @Nullable EditableInputConnection getInputConnection() {
        return mInputConnection;
    }

    /**
     * Extract a portion of this EditText's editable content based on the information in
     * <var>request</var> in to <var>outText</var>.
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
        Object[] spans = Editor.getParcelableSpans(spannable, start, end);
        int i = spans.length;
        while (i > 0) {
            i--;
            spannable.removeSpan(spans[i]);
        }
    }

    /**
     * Apply to this text view the given extracted text, as previously
     * returned by {@link #extractText(ExtractedTextRequest, ExtractedText)}.
     */
    public void setExtractedText(ExtractedText text) {
        Editable content = getEditableText();
        if (text.text != null) {
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

        // Now set the selection position...  make sure it is in range, to
        // avoid crashes.  If this is a partial update, it is possible that
        // the underlying text may have changed, causing us problems here.
        // Also we just don't want to trust clients to do the right thing.
        Spannable sp = getText();
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
        //TODO: (EW) the AOSP version checked text.flags for ExtractedText.FLAG_SELECTING to either
        // call MetaKeyKeyListener.startSelecting or MetaKeyKeyListener.stopSelecting, but those are
        // hidden and have been defined at least since Kitkat, but it has been hidden with a comment
        // saying it's pending API review, and at least as of S, they have been marked with
        // UnsupportedAppUsage. there doesn't seem to be much to do here for now. implement this if
        // those APIs ever get approved.

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

    /**
     * Called by the framework in response to a text completion from
     * the current input method, provided by it calling
     * {@link InputConnection#commitCompletion
     * InputConnection.commitCompletion()}.  The default implementation does
     * nothing; text views that are supporting auto-completion should override
     * this to do their desired behavior.
     *
     * @param text The auto complete text the user has selected.
     */
    public void onCommitCompletion(CompletionInfo text) {
        // intentionally empty
    }

    /**
     * Called by the framework in response to a text auto-correction (such as fixing a typo using a
     * dictionary) from the current input method, provided by it calling
     * {@link InputConnection#commitCorrection(CorrectionInfo) InputConnection.commitCorrection()}.
     * The default implementation flashes the background of the corrected word to provide
     * feedback to the user.
     *
     * @param info The auto correct info about the text that was corrected.
     */
    public void onCommitCorrection(CorrectionInfo info) {
        mEditor.onCommitCorrection(info);
    }

    public void beginBatchEdit() {
        mEditor.beginBatchEdit();
    }

    public void endBatchEdit() {
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

    /** @hide */
    public void onPerformSpellCheck() {
        if (mEditor.mSpellChecker != null) {
            mEditor.mSpellChecker.onPerformSpellCheck();
        }
    }

    /**
     * Called by the framework in response to a private command from the
     * current method, provided by it calling
     * {@link InputConnection#performPrivateCommand
     * InputConnection.performPrivateCommand()}.
     *
     * @param action The action name of the command.
     * @param data Any additional data for the command.  This may be null.
     * @return Return true if you handled the command, else false.
     */
    public boolean onPrivateIMECommand(String action, Bundle data) {
        return false;
    }

    private void nullLayouts() {
        if (mHintLayout instanceof BoringLayout && mSavedHintLayout == null) {
            mSavedHintLayout = (BoringLayout) mHintLayout;
        }

        mLayout = null;
        mHintLayout = null;

        mHintBoring = null;

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

        makeNewLayout(width, physicalWidth, UNKNOWN_BORING, physicalWidth, false);
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
                    case Gravity.LEFT:
                        //TODO: (EW) this value eventually gets passed to Layout, which checks
                        // getParagraphDirection for each line in drawText to determine the
                        // conversion to ALIGN_NORMAL or ALIGN_OPPOSITE, so I don't think we can
                        // replicate this functionality just using those. we could check something
                        // like getResources().getConfiguration().getLayoutDirection() instead to at
                        // least base left/right on the current language. I think that's still
                        // incorrect, but that might be the best option if we want to try to handle
                        // this case in a normal acceptable way. realistically, Gravity.LEFT just
                        // shouldn't be used for this view (that seems to be what Android is pushing
                        // for).
                        alignment = HiddenLayout.Alignment.ALIGN_LEFT;
                        break;
                    case Gravity.RIGHT:
                        //TODO: (EW) see Gravity.LEFT case comment
                        alignment = HiddenLayout.Alignment.ALIGN_RIGHT;
                        break;
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
            case TEXT_ALIGNMENT_VIEW_START:
                //TODO: (EW) see Gravity.LEFT case comment
                alignment = (getLayoutDirection() == LAYOUT_DIRECTION_RTL)
                        ? HiddenLayout.Alignment.ALIGN_RIGHT : HiddenLayout.Alignment.ALIGN_LEFT;
                break;
            case TEXT_ALIGNMENT_VIEW_END:
                //TODO: (EW) see Gravity.LEFT case comment
                alignment = (getLayoutDirection() == LAYOUT_DIRECTION_RTL)
                        ? HiddenLayout.Alignment.ALIGN_LEFT : HiddenLayout.Alignment.ALIGN_RIGHT;
                break;
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
     */
    private void makeNewLayout(int wantWidth, int hintWidth,
                               BoringLayout.Metrics hintBoring,
                               int ellipsisWidth, boolean bringIntoView) {
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
        TruncateAt effectiveEllipsize = mEllipsize;

        if (mTextDir == null) {
            mTextDir = getTextDirectionHeuristic();
        }

        mLayout = makeSingleLayout(wantWidth, ellipsisWidth, alignment, effectiveEllipsize);

        boolean shouldEllipsize = mEllipsize != null;
        mHintLayout = null;

        if (mHint != null) {
            if (shouldEllipsize) hintWidth = wantWidth;

            if (hintBoring == UNKNOWN_BORING) {
                hintBoring = isBoring(mHint, mTextPaint, mTextDir,
                        mHintBoring);
                if (hintBoring != null) {
                    mHintBoring = hintBoring;
                }
            }

            if (hintBoring != null) {
                if (hintBoring.width <= hintWidth
                        && (!shouldEllipsize || hintBoring.width <= ellipsisWidth)) {
                    if (mSavedHintLayout != null) {
                        mHintLayout = mSavedHintLayout.replaceOrMake(mHint, mTextPaint,
                                hintWidth, alignment, mSpacingMultiplier, mSpacingAdd,
                                hintBoring, mIncludePad);
                    } else {
                        mHintLayout = BoringLayout.make(mHint, mTextPaint,
                                hintWidth, alignment, mSpacingMultiplier, mSpacingAdd,
                                hintBoring, mIncludePad);
                    }

                    mSavedHintLayout = (BoringLayout) mHintLayout;
                } else if (shouldEllipsize && hintBoring.width <= hintWidth) {
                    if (mSavedHintLayout != null) {
                        mHintLayout = mSavedHintLayout.replaceOrMake(mHint, mTextPaint,
                                hintWidth, alignment, mSpacingMultiplier, mSpacingAdd,
                                hintBoring, mIncludePad, mEllipsize,
                                ellipsisWidth);
                    } else {
                        mHintLayout = BoringLayout.make(mHint, mTextPaint,
                                hintWidth, alignment, mSpacingMultiplier, mSpacingAdd,
                                hintBoring, mIncludePad, mEllipsize,
                                ellipsisWidth);
                    }
                }
            }
            if (mHintLayout == null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    StaticLayout.Builder builder = StaticLayout.Builder.obtain(mHint, 0,
                            mHint.length(), mTextPaint, hintWidth)
                            .setAlignment(alignment)
                            .setTextDirection(mTextDir)
                            .setLineSpacing(mSpacingAdd, mSpacingMultiplier)
                            .setIncludePad(mIncludePad)
                            .setBreakStrategy(mBreakStrategy)
                            .setHyphenationFrequency(mHyphenationFrequency)
                            .setMaxLines(mMaxMode == LINES ? mMaximum : Integer.MAX_VALUE);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        builder.setUseLineSpacingFromFallbacks(mUseFallbackLineSpacing);
                    }
                    if (shouldEllipsize) {
                        builder.setEllipsize(mEllipsize)
                                .setEllipsizedWidth(ellipsisWidth);
                    }
                    mHintLayout = builder.build();
                } else {
                    mHintLayout = new StaticLayout(mHint, mTextPaint, hintWidth, alignment,
                            mSpacingMultiplier, mSpacingAdd, mIncludePad);
                }
            }
        }

        if (bringIntoView || (testDirChange && oldDir != mLayout.getParagraphDirection(0))) {
            registerForPreDraw();
        }
        // CursorControllers need a non-null mLayout
        mEditor.prepareCursorControllers();
    }

    // (EW) the AOSP version was typed as Layout but this version currently always uses a
    // DynamicLayout, so specifying that for visibility, but this could be changed back if necessary
    private DynamicLayout makeSingleLayout(int wantWidth, int ellipsisWidth,
                                      Layout.Alignment alignment, TruncateAt effectiveEllipsize) {
        DynamicLayout result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            final DynamicLayout.Builder builder = DynamicLayout.Builder.obtain(mText, mTextPaint,
                    wantWidth)
                    .setDisplayText(mTransformed)
                    .setAlignment(alignment)
                    .setTextDirection(mTextDir)
                    .setLineSpacing(mSpacingAdd, mSpacingMultiplier)
                    .setIncludePad(mIncludePad)
                    .setUseLineSpacingFromFallbacks(mUseFallbackLineSpacing)
                    .setBreakStrategy(mBreakStrategy)
                    .setHyphenationFrequency(mHyphenationFrequency)
                    .setEllipsize(getKeyListener() == null ? effectiveEllipsize : null)
                    .setEllipsizedWidth(ellipsisWidth);
            result = builder.build();
        } else {
            // (EW) the AOSP version called constructors for DynamicLayout that are hidden from
            // apps, which allow for more settings that are accessible later in
            // DynamicLayout.Builder. using reflection here is at least relatively safe since it's
            // only done on old versions so it shouldn't just stop working at some point in the
            // future, and we have a valid fallback that will just have to skip some settings.
            try {
                TruncateAt truncateAt = getKeyListener() == null ? effectiveEllipsize : null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Constructor<?> dynamicLayoutConstructor = DynamicLayout.class.getConstructor(
                            CharSequence.class, CharSequence.class, TextPaint.class, int.class,
                            Layout.Alignment.class, TextDirectionHeuristic.class, float.class,
                            float.class, boolean.class, int.class, int.class, int.class,
                            TruncateAt.class, int.class);
                    result = (DynamicLayout)dynamicLayoutConstructor.newInstance(mText,
                            mTransformed, mTextPaint, wantWidth, alignment, mTextDir,
                            mSpacingMultiplier, mSpacingAdd, mIncludePad, mBreakStrategy,
                            mHyphenationFrequency, Layout.BREAK_STRATEGY_SIMPLE, truncateAt,
                            ellipsisWidth);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Constructor<?> dynamicLayoutConstructor = DynamicLayout.class.getConstructor(
                            CharSequence.class, CharSequence.class, TextPaint.class, int.class,
                            Layout.Alignment.class, TextDirectionHeuristic.class, float.class,
                            float.class, boolean.class, int.class, int.class,
                            TruncateAt.class, int.class);
                    result = (DynamicLayout)dynamicLayoutConstructor.newInstance(mText,
                            mTransformed, mTextPaint, wantWidth, alignment, mTextDir,
                            mSpacingMultiplier, mSpacingAdd, mIncludePad, mBreakStrategy,
                            mHyphenationFrequency, truncateAt, ellipsisWidth);
                } else {
                    Constructor<?> dynamicLayoutConstructor = DynamicLayout.class.getConstructor(
                            CharSequence.class, CharSequence.class, TextPaint.class, int.class,
                            Layout.Alignment.class, TextDirectionHeuristic.class, float.class,
                            float.class, boolean.class,
                            TruncateAt.class, int.class);
                    result = (DynamicLayout)dynamicLayoutConstructor.newInstance(mText,
                            mTransformed, mTextPaint, wantWidth, alignment, mTextDir,
                            mSpacingMultiplier, mSpacingAdd, mIncludePad, truncateAt,
                            ellipsisWidth);
                }
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException
                    | InstantiationException e) {
                Log.e(TAG, "makeSingleLayout: " +
                        "Reflection failed on hidden DynamicLayout constructor: "
                        + e.getMessage());
                result = new DynamicLayout(mText, mTransformed, mTextPaint, wantWidth, alignment,
                        mSpacingMultiplier, mSpacingAdd, mIncludePad,
                        getKeyListener() == null ? effectiveEllipsize : null, ellipsisWidth);
            }
        }
        return result;
    }

    private static int desired(Layout layout) {
        int n = layout.getLineCount();
        CharSequence text = layout.getText();
        float max = 0;

        // if any line was wrapped, we can't use it.
        // but it's ok for the last line not to have a newline

        for (int i = 0; i < n - 1; i++) {
            if (text.charAt(layout.getLineEnd(i) - 1) != '\n') {
                return -1;
            }
        }

        for (int i = 0; i < n; i++) {
            max = Math.max(max, layout.getLineMax(i));
        }

        return (int) Math.ceil(max);
    }

    /**
     * Set whether the EditText includes extra top and bottom padding to make
     * room for accents that go above the normal ascent and descent.
     * The default is true.
     *
     * @see #getIncludeFontPadding()
     *
     * @attr ref android.R.styleable#TextView_includeFontPadding
     */
    public void setIncludeFontPadding(boolean includePad) {
        if (mIncludePad != includePad) {
            mIncludePad = includePad;

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * Gets whether the EditText includes extra top and bottom padding to make
     * room for accents that go above the normal ascent and descent.
     *
     * @see #setIncludeFontPadding(boolean)
     *
     * @attr ref android.R.styleable#TextView_includeFontPadding
     */
    @InspectableProperty
    public boolean getIncludeFontPadding() {
        return mIncludePad;
    }

    private static final BoringLayout.Metrics UNKNOWN_BORING = new BoringLayout.Metrics();

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        // (EW) the AOSP version had some handling for BoringLayout for the main text, but an
        // EditText never uses a BoringLayout, so that isn't necessary
        BoringLayout.Metrics hintBoring = UNKNOWN_BORING;

        if (mTextDir == null) {
            mTextDir = getTextDirectionHeuristic();
        }

        int des = -1;
        boolean fromexisting = false;
        final float widthLimit = (widthMode == MeasureSpec.AT_MOST)
                ?  (float) widthSize : Float.MAX_VALUE;

        if (widthMode == MeasureSpec.EXACTLY) {
            // Parent has told us how big to be. So be it.
            width = widthSize;
        } else {
            if (mLayout != null && mEllipsize == null) {
                des = desired(mLayout);
            }

            if (des >= 0) {
                fromexisting = true;
            } else {
                // (EW) Layout.getDesiredWidthWithLimit started getting called in Pie (instead
                // of Layout.getDesiredWidth). Layout.getDesiredWidthWithLimit was also created
                // in Pie.
                des = (int) Math.ceil(HiddenLayout.getDesiredWidthWithLimit(mTransformed, 0,
                        mTransformed.length(), mTextPaint, mTextDir, widthLimit));
            }
            width = des;

            if (mHint != null) {
                int hintDes = -1;
                int hintWidth;

                if (mHintLayout != null && mEllipsize == null) {
                    hintDes = desired(mHintLayout);
                }

                if (hintDes < 0) {
                    hintBoring = isBoring(mHint, mTextPaint, mTextDir, mHintBoring);
                    if (hintBoring != null) {
                        mHintBoring = hintBoring;
                    }
                }

                if (hintBoring == null || hintBoring == UNKNOWN_BORING) {
                    if (hintDes < 0) {
                        hintDes = (int) Math.ceil(HiddenLayout.getDesiredWidthWithLimit(mHint, 0,
                                mHint.length(), mTextPaint, mTextDir, widthLimit));
                    }
                    hintWidth = hintDes;
                } else {
                    hintWidth = hintBoring.width;
                }

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
            makeNewLayout(want, hintWant, hintBoring,
                    width - getCompoundPaddingLeft() - getCompoundPaddingRight(), false);
        } else {
            final boolean layoutChanged = (mLayout.getWidth() != want) || (hintWidth != hintWant)
                    || (mLayout.getEllipsizedWidth()
                            != width - getCompoundPaddingLeft() - getCompoundPaddingRight());

            final boolean widthChanged = (mHint == null) && (mEllipsize == null)
                    && (want > mLayout.getWidth())
                    && (fromexisting && des >= 0 && des <= want);

            final boolean maximumChanged = (mMaxMode != mOldMaxMode) || (mMaximum != mOldMaximum);

            if (layoutChanged || maximumChanged) {
                if (!maximumChanged && widthChanged) {
                    mLayout.increaseWidthTo(want);
                } else {
                    makeNewLayout(want, hintWant, hintBoring,
                            width - getCompoundPaddingLeft() - getCompoundPaddingRight(), false);
                }
            } else {
                // Nothing has changed
            }
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            // Parent has told us how big to be. So be it.
            height = heightSize;
            mDesiredHeightAtMeasure = -1;
        } else {
            int desired = getDesiredHeight();

            height = desired;
            mDesiredHeightAtMeasure = desired;

            if (heightMode == MeasureSpec.AT_MOST) {
                height = Math.min(desired, heightSize);
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
    }

    // (EW) based on hidden method from BoringLayout
    private static Metrics isBoring(CharSequence text, TextPaint paint,
                                   TextDirectionHeuristic textDir, Metrics metrics) {
        final int textLength = text.length();
        if (textDir != null && textDir.isRtl(text, 0, textLength)) {
            return null;  // The heuristic considers the whole text RTL. Not boring.
        }
        return BoringLayout.isBoring(text, paint, metrics);
    }

    private int getDesiredHeight() {
        return Math.max(
                getDesiredHeight(mLayout, true),
                getDesiredHeight(mHintLayout, mEllipsize != null));
    }

    private int getDesiredHeight(Layout layout, boolean cap) {
        if (layout == null) {
            return 0;
        }

        /*
         * Don't cap the hint to a certain number of lines.
         * (Do cap it, though, if we have a maximum pixel height.)
         */
        //TODO: (EW) Layout#getHeight(boolean) is hidden. the base implementation simply calls
        // Layout#getHeight(). DynamicLayout and BoringLayout don't override it, but StaticLayout
        // does, which means this only might cause an issue with the hint text in certain cases.
        // trying to copy the logic from StaticLayout is difficult because it uses some private
        // fields that don't have getters and trying to recalculate the values would be difficult
        // and very fragile. even without needing to recalculate the values, copying the logic is
        // probably a reasonably fragile solution. until there is a specific known issue that this
        // causes, this will just have to call the method that is accessible.
        int desired = layout.getHeight();

        int linecount = layout.getLineCount();
        final int padding = getCompoundPaddingTop() + getCompoundPaddingBottom();
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
        desired = Math.max(desired, getSuggestedMinimumHeight());

        return desired;
    }

    /**
     * Check whether a change to the existing text layout requires a
     * new view layout.
     */
    private void checkForResize() {
        boolean sizeChanged = false;

        if (mLayout != null) {
            final LayoutParams layoutParams = getLayoutParams();

            // Check if our width changed
            if (layoutParams.width == LayoutParams.WRAP_CONTENT) {
                sizeChanged = true;
                invalidate();
            }

            // Check if our height changed
            if (layoutParams.height == LayoutParams.WRAP_CONTENT) {
                int desiredHeight = getDesiredHeight();

                if (desiredHeight != this.getHeight()) {
                    sizeChanged = true;
                }
            } else if (layoutParams.height == LayoutParams.MATCH_PARENT) {
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

        final LayoutParams layoutParams = getLayoutParams();
        final int left = getLeft();
        final int right = getRight();

        if ((layoutParams.width != LayoutParams.WRAP_CONTENT
                || (mMaxWidthMode == mMinWidthMode && mMaxWidth == mMinWidth))
                && (mHint == null || mHintLayout != null)
                && (right - left - getCompoundPaddingLeft() - getCompoundPaddingRight() > 0)) {
            // Static width, so try making a new text layout.

            int oldHeight = mLayout.getHeight();
            int want = mLayout.getWidth();
            int hintWant = mHintLayout == null ? 0 : mHintLayout.getWidth();

            /*
             * No need to bring the text into view, since the size is not
             * changing (unless we do the requestLayout(), in which case it
             * will happen at measure).
             */
            makeNewLayout(want, hintWant, UNKNOWN_BORING,
                    right - left - getCompoundPaddingLeft() - getCompoundPaddingRight(),
                    false);

            // In a fixed-height view, so use our new text layout.
            if (layoutParams.height != LayoutParams.WRAP_CONTENT
                    && layoutParams.height != LayoutParams.MATCH_PARENT) {
                invalidate();
                return;
            }

            // Dynamic height, but height has stayed the same,
            // so use our new text layout.
            if (mLayout.getHeight() == oldHeight
                    && (mHintLayout == null || mHintLayout.getHeight() == oldHeight)) {
                invalidate();
                return;
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

        Layout.Alignment alignment = layout.getParagraphAlignment(line);
        int direction = layout.getParagraphDirection(line);
        int hSpace = getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        int vSpace = getBottom() - getTop() - getExtendedPaddingTop() - getExtendedPaddingBottom();
        int height = layout.getHeight();

        int scrollX, scrollY;

        // Convert to left, center, or right alignment.
        if (alignment == Layout.Alignment.ALIGN_NORMAL) {
            alignment = direction == Layout.DIR_LEFT_TO_RIGHT
                    ? HiddenLayout.Alignment.ALIGN_LEFT : HiddenLayout.Alignment.ALIGN_RIGHT;
        } else if (alignment == Layout.Alignment.ALIGN_OPPOSITE) {
            alignment = direction == Layout.DIR_LEFT_TO_RIGHT
                    ? HiddenLayout.Alignment.ALIGN_RIGHT : HiddenLayout.Alignment.ALIGN_LEFT;
        }

        if (alignment == Layout.Alignment.ALIGN_CENTER) {
            /*
             * Keep centered if possible, or, if it is too wide to fit,
             * keep leading edge in view.
             */

            int left = (int) Math.floor(layout.getLineLeft(line));
            int right = (int) Math.ceil(layout.getLineRight(line));

            if (right - left < hSpace) {
                scrollX = (right + left) / 2 - hSpace / 2;
            } else {
                if (direction < 0) {
                    scrollX = right - hSpace;
                } else {
                    scrollX = left;
                }
            }
        } else if (HiddenLayout.Alignment.isAlignRight(alignment)) {
            int right = (int) Math.ceil(layout.getLineRight(line));
            scrollX = right - hSpace;
        } else { // alignment == HiddenLayout.ALIGNMENT_ALIGN_LEFT (will also be the default)
            scrollX = (int) Math.floor(layout.getLineLeft(line));
        }

        if (height < vSpace) {
            scrollY = 0;
        } else {
            if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
                scrollY = height - vSpace;
            } else {
                scrollY = 0;
            }
        }

        if (scrollX != getScrollX() || scrollY != getScrollY()) {
            scrollTo(scrollX, scrollY);
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

        Layout.Alignment alignment = layout.getParagraphAlignment(line);
        if (HiddenLayout.Alignment.isAlignLeft(alignment)) {
            grav = 1;
        } else if (HiddenLayout.Alignment.isAlignRight(alignment)) {
            grav = -1;
        } else if (alignment == Layout.Alignment.ALIGN_NORMAL) {
            grav = layout.getParagraphDirection(line);
        } else if (alignment == Layout.Alignment.ALIGN_OPPOSITE) {
            grav = -layout.getParagraphDirection(line);
        } else {
            grav = 0;
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
        final int x = (int) HiddenLayout.getPrimaryHorizontal(layout, mTextDir, offset, clamped);
        final int top = layout.getLineTop(line);
        final int bottom = layout.getLineTop(line + 1);

        int left = (int) Math.floor(layout.getLineLeft(line));
        int right = (int) Math.ceil(layout.getLineRight(line));
        int height = layout.getHeight();

        int hSpace = getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        int vSpace = getBottom() - getTop() - getExtendedPaddingTop() - getExtendedPaddingBottom();
        if (!mHorizontallyScrolling && right - left > hSpace && right > x) {
            // If cursor has been clamped, make sure we don't scroll.
            right = Math.max(x, left + hSpace);
        }

        int hSlack = (bottom - top) / 2;
        int vSlack = hSlack;

        if (vSlack > vSpace / 4) {
            vSlack = vSpace / 4;
        }
        if (hSlack > hSpace / 4) {
            hSlack = hSpace / 4;
        }

        int hScroll = getScrollX();
        int vScroll = getScrollY();

        if (top - vScroll < vSlack) {
            vScroll = top - vSlack;
        }
        if (bottom - vScroll > vSpace - vSlack) {
            vScroll = bottom - (vSpace - vSlack);
        }
        if (height - vScroll < vSpace) {
            vScroll = height - vSpace;
        }
        if (0 - vScroll > 0) {
            vScroll = 0;
        }

        if (grav != 0) {
            if (x - hScroll < hSlack) {
                hScroll = x - hSlack;
            }
            if (x - hScroll > hSpace - hSlack) {
                hScroll = x - (hSpace - hSlack);
            }
        }

        if (grav < 0) {
            if (left - hScroll > 0) {
                hScroll = left;
            }
            if (right - hScroll < hSpace) {
                hScroll = right - hSpace;
            }
        } else if (grav > 0) {
            if (right - hScroll < hSpace) {
                hScroll = right - hSpace;
            }
            if (left - hScroll > 0) {
                hScroll = left;
            }
        } else /* grav == 0 */ {
            if (right - left <= hSpace) {
                /*
                 * If the entire text fits, center it exactly.
                 */
                hScroll = left - (hSpace - (right - left)) / 2;
            } else if (x > right - hSlack) {
                /*
                 * If we are near the right edge, keep the right edge
                 * at the edge of the view.
                 */
                hScroll = right - hSpace;
            } else if (x < left + hSlack) {
                /*
                 * If we are near the left edge, keep the left edge
                 * at the edge of the view.
                 */
                hScroll = left;
            } else if (left > hScroll) {
                /*
                 * Is there whitespace visible at the left?  Fix it if so.
                 */
                hScroll = left;
            } else if (right < hScroll + hSpace) {
                /*
                 * Is there whitespace visible at the right?  Fix it if so.
                 */
                hScroll = right - hSpace;
            } else {
                /*
                 * Otherwise, float as needed.
                 */
                if (x - hScroll < hSlack) {
                    hScroll = x - hSlack;
                }
                if (x - hScroll > hSpace - hSlack) {
                    hScroll = x - (hSpace - hSlack);
                }
            }
        }

        if (hScroll != getScrollX() || vScroll != getScrollY()) {
            if (mScroller == null) {
                scrollTo(hScroll, vScroll);
            } else {
                long duration = AnimationUtils.currentAnimationTimeMillis() - mLastScroll;
                int dx = hScroll - getScrollX();
                int dy = vScroll - getScrollY();

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
     * one character (a selection range).
     *
     * @return True if the cursor was actually moved, false otherwise.
     */
    public boolean moveCursorToVisibleOffset() {
        int start = getSelectionStart();
        int end = getSelectionEnd();
        if (start != end) {
            return false;
        }

        // First: make sure the line is visible on screen:

        int line = mLayout.getLineForOffset(start);

        final int top = mLayout.getLineTop(line);
        final int bottom = mLayout.getLineTop(line + 1);
        final int vSpace = getBottom() - getTop() - getExtendedPaddingTop() - getExtendedPaddingBottom();
        int vSlack = (bottom - top) / 2;
        if (vSlack > vSpace / 4) {
            vSlack = vSpace / 4;
        }
        final int vScroll = getScrollY();

        if (top < (vScroll + vSlack)) {
            line = mLayout.getLineForVertical(vScroll + vSlack + (bottom - top));
        } else if (bottom > (vSpace + vScroll - vSlack)) {
            line = mLayout.getLineForVertical(vSpace + vScroll - vSlack - (bottom - top));
        }

        // Next: make sure the character is visible on screen:

        final int hSpace = getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        final int hScroll = getScrollX();
        final int leftChar = mLayout.getOffsetForHorizontal(line, hScroll);
        final int rightChar = mLayout.getOffsetForHorizontal(line, hSpace + hScroll);

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
            Selection.setSelection(mText, newStart);
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

                // (EW) the AOSP version called View#invalidateParentCaches, which is hidden and
                // marked as UnsupportedAppUsage. the comment for it mentions it's used for hardware
                // acceleration, which isn't supported here, so I don't think anything needs to be done.

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
        return Selection.getSelectionStart(getText());
    }

    /**
     * Convenience for {@link Selection#getSelectionEnd}.
     */
    @ViewDebug.ExportedProperty(category = "text")
    public int getSelectionEnd() {
        return Selection.getSelectionEnd(getText());
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
                                 boolean changeMaxLines, boolean changeMaxLength) {
        mSingleLine = singleLine;

        if (singleLine) {
            setLines(1);
            setHorizontallyScrolling(true);
            if (applyTransformation) {
                setTransformationMethod(SingleLineTransformationMethod.getInstance());
            }

            if (!changeMaxLength) return;

            final InputFilter[] prevFilters = getFilters();
            for (InputFilter filter: getFilters()) {
                // We don't add LengthFilter if already there.
                if (filter instanceof InputFilter.LengthFilter) return;
            }

            if (mSingleLineLengthFilter == null) {
                mSingleLineLengthFilter = new InputFilter.LengthFilter(
                        MAX_LENGTH_FOR_SINGLE_LINE_EDIT_TEXT);
            }

            final InputFilter[] newFilters = new InputFilter[prevFilters.length + 1];
            System.arraycopy(prevFilters, 0, newFilters, 0, prevFilters.length);
            newFilters[prevFilters.length] = mSingleLineLengthFilter;

            setFilters(newFilters);

            // Since filter doesn't apply to existing text, trigger filter by setting text.
            setText(getText());
        } else {
            if (changeMaxLines) {
                setMaxLines(Integer.MAX_VALUE);
            }
            setHorizontallyScrolling(false);
            if (applyTransformation) {
                setTransformationMethod(null);
            }

            if (!changeMaxLength) return;

            final InputFilter[] prevFilters = getFilters();
            if (prevFilters.length == 0) return;

            // Short Circuit: if mSingleLineLengthFilter is not allocated, nobody sets automated
            // single line char limit filter.
            if (mSingleLineLengthFilter == null) return;

            // If we need to remove mSingleLineLengthFilter, we need to allocate another array.
            // Since filter list is expected to be small and want to avoid unnecessary array
            // allocation, check if there is mSingleLengthFilter first.
            int targetIndex = -1;
            for (int i = 0; i < prevFilters.length; ++i) {
                if (prevFilters[i] == mSingleLineLengthFilter) {
                    targetIndex = i;
                    break;
                }
            }
            if (targetIndex == -1) return;  // not found. Do nothing.

            if (prevFilters.length == 1) {
                setFilters(NO_FILTERS);
                return;
            }

            // Create new array which doesn't include mSingleLengthFilter.
            final InputFilter[] newFilters = new InputFilter[prevFilters.length - 1];
            System.arraycopy(prevFilters, 0, newFilters, 0, targetIndex);
            System.arraycopy(
                    prevFilters,
                    targetIndex + 1,
                    newFilters,
                    targetIndex,
                    prevFilters.length - targetIndex - 1);
            setFilters(newFilters);
            mSingleLineLengthFilter = null;
        }
    }

    /**
     * Causes words in the text that are longer than the view's width to be ellipsized instead of
     * broken in the middle. {@link TextUtils.TruncateAt#MARQUEE TextUtils.TruncateAt#MARQUEE} is
     * not supported.
     *
     * If {@link #setMaxLines} has been used to set two or more lines, only
     * {@link TextUtils.TruncateAt#END} is supported (other ellipsizing types will not do anything).
     *
     * @param ellipsis Type of ellipsis to be applied.
     * @throws IllegalArgumentException When the value of <code>ellipsis</code> parameter is
     *      {@link TextUtils.TruncateAt#MARQUEE}.
     * @see android.widget.TextView#setEllipsize(TextUtils.TruncateAt)
     */
    public void setEllipsize(TextUtils.TruncateAt ellipsis) {
        if (ellipsis == TextUtils.TruncateAt.MARQUEE) {
            throw new IllegalArgumentException("EditText cannot use the ellipsize mode "
                    + "TextUtils.TruncateAt.MARQUEE");
        }
        // TruncateAt is an enum. != comparison is ok between these singleton objects.
        if (mEllipsize != ellipsis) {
            mEllipsize = ellipsis;

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * Returns where, if anywhere, words that are longer than the view
     * is wide should be ellipsized.
     */
    @InspectableProperty
    @ViewDebug.ExportedProperty
    public TextUtils.TruncateAt getEllipsize() {
        return mEllipsize;
    }

    /**
     * Set the EditText so that when it takes focus, all the text is
     * selected.
     *
     * @attr ref android.R.styleable#TextView_selectAllOnFocus
     */
    public void setSelectAllOnFocus(boolean selectAllOnFocus) {
        mEditor.mSelectAllOnFocus = selectAllOnFocus;
    }

    /**
     * Set whether the cursor is visible. The default is true. If IME is consuming the input, the
     * cursor will always be invisible, visibility will be updated as the last state when IME does
     * not consume the input anymore.
     *
     * @see #isCursorVisible()
     *
     * @attr ref android.R.styleable#TextView_cursorVisible
     */
    public void setCursorVisible(boolean visible) {
        mCursorVisibleFromAttr = visible;
        updateCursorVisibleInternal();
    }

    /**
     * Sets the IME is consuming the input and make the cursor invisible if {@code imeConsumesInput}
     * is {@code true}. Otherwise, make the cursor visible.
     *
     * @param imeConsumesInput {@code true} if IME is consuming the input
     *
     * @hide
     */
    public void setImeConsumesInput(boolean imeConsumesInput) {
        mImeIsConsumingInput = imeConsumesInput;
        updateCursorVisibleInternal();
    }

    private void updateCursorVisibleInternal()  {
        boolean visible = mCursorVisibleFromAttr && !mImeIsConsumingInput;
        if (mEditor.mCursorVisible != visible) {
            mEditor.mCursorVisible = visible;
            invalidate();

            mEditor.makeBlink();

            // InsertionPointCursorController depends on mCursorVisible
            mEditor.prepareCursorControllers();
        }
    }

    /**
     * @return whether or not the cursor is visible. This method may return {@code false} when the
     * IME is consuming the input even if the {@code mEditor.mCursorVisible} attribute is
     * {@code true} or {@code #setCursorVisible(true)} is called.
     *
     * @see #setCursorVisible(boolean)
     *
     * @attr ref android.R.styleable#TextView_cursorVisible
     */
    @InspectableProperty
    public boolean isCursorVisible() {
        // true is the default value
        return mEditor.mCursorVisible;
    }

    /**
     * @return whether cursor is visible without regard to {@code mImeIsConsumingInput}.
     * {@code true} is the default value.
     *
     * @see #setCursorVisible(boolean)
     */
    boolean isCursorVisibleFromAttr() {
        return mCursorVisibleFromAttr;
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
     * @param text The text the EditText is displaying
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
    @CallSuper
    protected void onSelectionChanged(int selStart, int selEnd) {
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED);
    }

    /**
     * Adds a TextWatcher to the list of those whose methods are called
     * whenever this EditText's text changes.
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
     * whenever this EditText's text changes.
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
        removeIntersectingNonAdjacentSpans(start, start + before, SpellCheckSpan.class);
        removeIntersectingNonAdjacentSpans(start, start + before, SuggestionSpan.class);
    }

    // Removes all spans that are inside or actually overlap the start..end range
    private <T> void removeIntersectingNonAdjacentSpans(int start, int end, Class<T> type) {
        Editable text = mText;

        T[] spans = text.getSpans(start, end, type);
        ArrayList<T> spansToRemove = new ArrayList<>();
        for (T span : spans) {
            final int spanStart = text.getSpanStart(span);
            final int spanEnd = text.getSpanEnd(span);
            if (spanEnd == start || spanStart == end) continue;
            spansToRemove.add(span);
        }
        for (T span : spansToRemove) {
            text.removeSpan(span);
        }
    }

    void removeAdjacentSuggestionSpans(final int pos) {
        final Editable text = mText;

        final SuggestionSpan[] spans = text.getSpans(pos, pos, SuggestionSpan.class);
        final int length = spans.length;
        for (int i = 0; i < length; i++) {
            final int spanStart = text.getSpanStart(spans[i]);
            final int spanEnd = text.getSpanEnd(spans[i]);
            if (spanEnd == pos || spanStart == pos) {
                if (SpellChecker.haveWordBoundariesChanged(text, pos, pos, spanStart, spanEnd)) {
                    text.removeSpan(spans[i]);
                }
            }
        }
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

        notifyListeningManagersAfterTextChanged();
    }

    /**
     * Notify managers (such as {@link AutofillManager} and {@link ContentCaptureManager}) that are
     * interested on text changes.
     */
    private void notifyListeningManagersAfterTextChanged() {

        // Autofill
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isAutofillable()) {
            // It is important to not check whether the view is important for autofill
            // since the user can trigger autofill manually on not important views.
            final AutofillManager afm = getAutofillManager();
            if (afm != null) {
                if (AUTOFILL_HELPER_VERBOSE) {
                    Log.v(LOG_TAG, "notifyAutoFillManagerAfterTextChanged");
                }
                afm.notifyValueChanged(EditText.this);
            }
        }

        notifyContentCaptureTextChanged();
    }

    /**
     * Notifies the ContentCapture service that the text of the view has changed (only if
     * ContentCapture has been notified of this view's existence already).
     *
     * @hide
     */
    public void notifyContentCaptureTextChanged() {
        // TODO(b/121045053): should use a flag / boolean to keep status of SHOWN / HIDDEN instead
        // of using isLaidout(), so it's not called in cases where it's laid out but a
        // notifyAppeared was not sent.
        // (EW) the AOSP version also checked View#getNotifiedContentCaptureAppeared, which is
        // hidden. I'm not completely sure what the purpose is, but maybe it's only necessary for
        // the framework view. even if we need to do something, it seems that this might just call
        // notifyViewTextChanged a bit more often than it should, which doesn't seem too bad. I'm
        // not sure there is much to do now until there is a know issue that this causes.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isLaidOut()
                && isImportantForContentCapture()) {
            final ContentCaptureManager cm = getServiceManager(ContentCaptureManager.class);
            if (cm != null && cm.isContentCaptureEnabled()) {
                final ContentCaptureSession session = getContentCaptureSession();
                if (session != null) {
                    // TODO(b/111276913): pass flags when edited by user / add CTS test
                    session.notifyViewTextChanged(getAutofillId(), getText());
                }
            }
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

        if (isParcelableSpan(what)) {
            // If this is a span that can be sent to a remote process,
            // the current extract editor would be interested in it.
            if (ims != null && ims.mExtractedTextRequest != null) {
                if (ims.mBatchEditNesting != 0) {
                    //TODO: (EW) this expands the extracted text to include all of the
                    // ParcelableSpans, and in the case of spellcheck being enabled, this seems to
                    // result in a full extract, so this will need to be handled when adding options
                    // to limit extracting text
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

        if (mEditor.mSpellChecker != null && newStart < 0
                && what instanceof SpellCheckSpan) {
            mEditor.mSpellChecker.onSpellCheckSpanRemoved((SpellCheckSpan) what);
        }
    }

    // (EW) to match AOSP functionality, we need to create instances of SpellCheckSpan and
    // SuggestionRangeSpan, but those are hidden, so those had to be copied to be used. those
    // implement ParcelableSpan, which isn't intended for apps to implement, so our version just
    // implements Parcelable, but there is some handling from AOSP for ParcelableSpan, so this is a
    // wrapper to include those to match that same functionality.
    private static boolean isParcelableSpan(Object o) {
        return o instanceof ParcelableSpan || o instanceof SpellCheckSpan
                || o instanceof SuggestionRangeSpan;
    }

    // (EW) only relevant in versions prior to Nougat
    @Override
    public void dispatchFinishTemporaryDetach() {
        mDispatchTemporaryDetach = true;
        super.dispatchFinishTemporaryDetach();
        mDispatchTemporaryDetach = false;
    }

    // (EW) only relevant in versions prior to Nougat
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

    // (EW) only relevant in versions prior to Nougat
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
            MetaKeyKeyListener.resetMetaState(mText);
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
        BaseInputConnection.removeComposingSpans(mText);
    }

    /**
     * Called from onTouchEvent() to prevent the touches by secondary fingers.
     * Dragging on handles can revise cursor/selection, so can dragging on the text view.
     * This method is a lock to avoid processing multiple fingers on both text view and handles.
     * Note: multiple fingers on handles (e.g. 2 fingers on the 2 selection handles) should work.
     *
     * @param event The motion event that is being handled and carries the pointer info.
     * @param fromHandleView true if the event is delivered to selection handle or insertion
     * handle; false if this event is delivered to EditText.
     * @return Returns true to indicate that onTouchEvent() can continue processing the motion
     * event, otherwise false.
     *  - Always returns true for the first finger.
     *  - For secondary fingers, if the first or current finger is from EditText, returns false.
     *    This is to make touch mutually exclusive between the EditText and the handles, but
     *    not among the handles.
     */
    boolean isFromPrimePointer(MotionEvent event, boolean fromHandleView) {
        boolean res = true;
        if (mPrimePointerId == NO_POINTER_ID)  {
            mPrimePointerId = event.getPointerId(0);
            mIsPrimePointerFromHandleView = fromHandleView;
        } else if (mPrimePointerId != event.getPointerId(0)) {
            res = mIsPrimePointerFromHandleView && fromHandleView;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            mPrimePointerId = -1;
        }
        return res;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (DEBUG_CURSOR) {
            logCursor("onTouchEvent", "%s (%f,%f)",
                    MotionEvent.actionToString(event.getActionMasked()),
                    event.getX(), event.getY());
        }
        final int action = event.getActionMasked();
        if (!isFromPrimePointer(event, false)) {
            return true;
        }

        mEditor.onTouchEvent(event);

        if (mEditor.mInsertionPointCursorController != null
                && mEditor.mInsertionPointCursorController.isCursorBeingModified()) {
            return true;
        }
        if (mEditor.mSelectionModifierCursorController != null
                && mEditor.mSelectionModifierCursorController.isDragAcceleratorActive()) {
            return true;
        }

        final boolean superResult = super.onTouchEvent(event);
        if (DEBUG_CURSOR) {
            logCursor("onTouchEvent", "superResult=%s", superResult);
        }

        /*
         * Don't handle the release after a long press, because it will move the selection away from
         * whatever the menu action was trying to affect. If the long press should have triggered an
         * insertion action mode, we can now actually show it.
         */
        if (mEditor.mDiscardNextActionUp && action == MotionEvent.ACTION_UP) {
            mEditor.mDiscardNextActionUp = false;
            if (DEBUG_CURSOR) {
                logCursor("onTouchEvent", "release after long press detected");
            }
            if (mEditor.mIsInsertionActionModeStartPending) {
                mEditor.startInsertionActionMode();
                mEditor.mIsInsertionActionModeStartPending = false;
            }
            return superResult;
        }

        final boolean touchIsFinished = (action == MotionEvent.ACTION_UP)
                && (!mEditor.mIgnoreActionUpEvent) && isFocused();

        if ((mMovement != null || onCheckIsTextEditor()) && isEnabled() && mLayout != null) {
            boolean handled = false;

            if (mMovement != null) {
                handled |= mMovement.onTouchEvent(this, mText, event);
            }

            if (touchIsFinished && isTextEditable()) {
                // Show the IME, except when selecting in read-only text.
                final InputMethodManager imm = getInputMethodManager();
                viewClicked(imm);
                if (mEditor.mShowSoftInputOnFocus && imm != null) {
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
        if (mMovement != null && mLayout != null) {
            try {
                if (mMovement.onGenericMotionEvent(this, mText, event)) {
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

    @Override
    protected void onCreateContextMenu(ContextMenu menu) {
        mEditor.onCreateContextMenu(menu);
    }

    @Override
    public boolean showContextMenu() {
        mEditor.setContextMenuAnchor(Float.NaN, Float.NaN);
        return super.showContextMenu();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public boolean showContextMenu(float x, float y) {
        mEditor.setContextMenuAnchor(x, y);
        return super.showContextMenu(x, y);
    }

    /**
     * @return True iff this EditText contains a text that can be edited.
     */
    boolean isTextEditable() {
        return onCheckIsTextEditor() && isEnabled();
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
    public boolean onTrackballEvent(MotionEvent event) {
        if (mMovement != null && mLayout != null) {
            if (mMovement.onTrackballEvent(this, mText, event)) {
                return true;
            }
        }

        return super.onTrackballEvent(event);
    }

    /**
     * Sets the Scroller used for producing a scrolling animation
     *
     * @param s A Scroller instance
     */
    public void setScroller(Scroller s) {
        mScroller = s;
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
     * EditText. {@link #textCanBeSelected()} has to be true (this is one of the conditions to have
     * a selection controller (see {@link Editor#prepareCursorControllers()}), but this is not
     * sufficient.
     */
    boolean canSelectText() {
        return mText.length() != 0 && mEditor.hasSelectionController();
    }

    /**
     * Test based on the <i>intrinsic</i> characteristics of the EditText.
     * The text must be spannable and the movement method must allow for arbitrary selection.
     *
     * See also {@link #canSelectText()}.
     */
    boolean textCanBeSelected() {
        // prepareCursorController() relies on this method.
        // If you change this condition, make sure prepareCursorController is called anywhere
        // the value of this condition might be changed.
        if (mMovement == null || !mMovement.canSelectArbitrarily()) return false;
        return isTextEditable();
    }

    private Locale getTextServicesLocale(boolean allowNullLocale) {
        // Start fetching the text services locale asynchronously.
        updateTextServicesLocaleAsync();
        // If !allowNullLocale and there is no cached text services locale, just return the default
        // locale.
        return (mCurrentSpellCheckerLocaleCache == null && !allowNullLocale) ? Locale.getDefault()
                : mCurrentSpellCheckerLocaleCache;
    }

    @Nullable
    final TextServicesManager getTextServicesManager() {
        return getServiceManager(TextServicesManager.class, Context.TEXT_SERVICES_MANAGER_SERVICE);
    }

    @Nullable
    final ClipboardManager getClipboardManager() {
        return getServiceManager(ClipboardManager.class, Context.CLIPBOARD_SERVICE);
    }

    @Nullable
    final AccessibilityManager getAccessibilityManager() {
        return getServiceManager(AccessibilityManager.class, Context.ACCESSIBILITY_SERVICE);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Nullable
    final AutofillManager getAutofillManager() {
        return getServiceManager(AutofillManager.class);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Nullable
    final <T> T getServiceManager(Class<T> managerClazz) {
        return getContext().getSystemService(managerClazz);
    }

    @Nullable
    final <T> T getServiceManager(Class<T> managerClazz, String name) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getServiceManager(managerClazz);
        }
        return managerClazz.cast(getContext().getSystemService(name));
    }

    /**
     * This is a temporary method. Future versions may support multi-locale text.
     * Caveat: This method may not return the latest text services locale, but this should be
     * acceptable and it's more important to make this method asynchronous.
     *
     * @return The locale that should be used for a word iterator
     * in this EditText, based on the current spell checker settings,
     * the current IME's locale, or the system default locale.
     * Please note that a word iterator in this EditText is different from another word iterator
     * used by SpellChecker.java of EditText. This method should be used for the former.
     */
    // TODO: Support multi-locale
    // TODO: Update the text services locale immediately after the keyboard locale is switched
    // by catching intent of keyboard switch event
    Locale getTextServicesLocale() {
        return getTextServicesLocale(false /* allowNullLocale */);
    }

    /**
     * @return {@code true} if this EditText is specialized for showing and interacting with the
     * extracted text in a full-screen input method.
     */
    boolean isInExtractedMode() {
        return false;
    }

    /**
     * This is a temporary method. Future versions may support multi-locale text.
     * Caveat: This method may not return the latest spell checker locale, but this should be
     * acceptable and it's more important to make this method asynchronous.
     *
     * @return The locale that should be used for a spell checker in this EditText,
     * based on the current spell checker settings, the current IME's locale, or the system default
     * locale.
     */
    Locale getSpellCheckerLocale() {
        return getTextServicesLocale(true /* allowNullLocale */);
    }

    private void updateTextServicesLocaleAsync() {
        // AsyncTask.execute() uses a serial executor which means we don't have
        // to lock around updateTextServicesLocaleLocked() to prevent it from
        // being executed n times in parallel.
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                updateTextServicesLocaleLocked();
            }
        });
    }

    private void updateTextServicesLocaleLocked() {
        final TextServicesManager textServicesManager = getTextServicesManager();
        if (textServicesManager == null) {
            return;
        }
        // (EW) the AOSP version gets the locale from the hidden
        // TextServicesManager#getCurrentSpellCheckerSubtype since Ice Cream Sandwich MR1 through at
        // least S. that method did exist in Ice Cream Sandwich (the same version that
        // TextServicesManager was released), but it didn't specify the locale for the WordIterator
        // (it would just use Locale#getDefault) or the intent to add a word to the dictionary
        // (where this locale was used for in Ice Cream Sandwich MR1), and SpellChecker called
        // TextServicesManager#newSpellCheckerSession with no locale and a boolean to have it use
        // the languages defined in settings. TextServicesManager#newSpellCheckerSession calls
        // TextServicesManager#getCurrentSpellCheckerSubtype to get the locale when it is told to
        // refer to settings. in Ice Cream Sandwich MR1, SpellChecker started explicitly passing the
        // locale to TextServicesManager#newSpellCheckerSession. this was done originally in order
        // to match the locale from the IME's subtype and use that same locale in some WordIterators
        // (9d8d3f1539ce5bdf512bd47ec1648609d6cde5b1), but eventually TextView changed to use the
        // spell checker's subtype by the end of the release and moved the IME's subtype handling
        // into TextServicesManager#getCurrentSpellCheckerSubtype
        // (05f24700613fb4dce95fb6d5f8fe460d7a30c128). (the IME's subtype handling was removed in Q
        // (bb0a2247147139e7f01b66366e4552858b5747a4)). there have been a few minor changes in the
        // implementation of TextServicesManager#getCurrentSpellCheckerSubtype over the years, but
        // the method signature has stayed the same and hasn't been on the hidden API blacklist for
        // at least the first 4 versions that it could have been blacklisted, which hopefully means
        // it's less likely to be changed or blocked in the future, but there's still no guarantee.
        // there doesn't seem to be a direct alternative to determine what locale was specified for
        // the spell checker. we can go back to having the spell checker refer to settings when
        // creating a new session, which should be functionally equivalent to requesting the same
        // locale from TextServicesManager#getCurrentSpellCheckerSubtype (it was originally changed
        // away from that simply due to an intermediate change in functionality, and after that it
        // probably gave a better guarantee that the locale used there matched other places, so
        // there probably was no reason to revert that part of the change at that point), but there
        // are still other pieces that need the locale explicitly set. it would be weird and
        // probably have some unexpected (buggy) results to use one language to determine spelling
        // of words and another to determine what constitutes a word to check the spelling of, and
        // it would be bad to disregard the spell checker settings (at least in this case where we
        // don't have a specific language we want spell check). this means that our best option to
        // have reasonable functionality is to reflection to access this hidden method and hope it
        // doesn't break, but this locale should be used as minimally as possible (ie: things like
        // creating a spell checker session that can accomplish the same goal without directly using
        // this should do so to minimize bugs if reflection stops working) even if that gives the
        // slight potential for the locales used to be out of sync (I think using the right spell
        // checker locale is more important because it will have a larger and more obvious impact on
        // users).
        // for reference, the locale that is actually sent to the SpellCheckerService.Session is the
        // locale from the subtype selected in the settings activity if one was set, or if it was
        // set to use system languages, it would be the locale from the IME subtype (only prior to
        // Q) if it had one or the first language enabled in the system (except in the case that the
        // IME or system language doesn't match a spell checker subtype, where no session is
        // created, and it doesn't even try to fall back to something like secondary enabled
        // languages)
        Locale locale = null;
        try {
            // (EW) this line works, but logs the warning:
            // on Pie:
            // Accessing hidden method Landroid/view/textservice/TextServicesManager;->getCurrentSpellCheckerSubtype(Z)Landroid/view/textservice/SpellCheckerSubtype; (light greylist, reflection)
            // on Q and R:
            // Accessing hidden method Landroid/view/textservice/TextServicesManager;->getCurrentSpellCheckerSubtype(Z)Landroid/view/textservice/SpellCheckerSubtype; (greylist, reflection, allowed)
            // on S (last version checked):
            // Accessing hidden method Landroid/view/textservice/TextServicesManager;->getCurrentSpellCheckerSubtype(Z)Landroid/view/textservice/SpellCheckerSubtype; (unsupported, reflection, allowed)
            Method getCurrentSpellCheckerSubtypeMethod = TextServicesManager.class.getMethod(
                    "getCurrentSpellCheckerSubtype", boolean.class);

            final SpellCheckerSubtype subtype =
                    (SpellCheckerSubtype) getCurrentSpellCheckerSubtypeMethod.invoke(
                            textServicesManager, true);
            if (subtype != null) {
                String localeStr = subtype.getLocale();
                // (EW) from SpellCheckerSubtype#constructLocaleFromString since that's hidden
                if (!TextUtils.isEmpty(localeStr)) {
                    String[] localeParams = localeStr.split("_", 3);
                    // The length of localeStr is guaranteed to always return a 1 <= value <= 3
                    // because localeStr is not empty.
                    if (localeParams.length == 1) {
                        locale = new Locale(localeParams[0]);
                    } else if (localeParams.length == 2) {
                        locale = new Locale(localeParams[0], localeParams[1]);
                    } else if (localeParams.length == 3) {
                        locale = new Locale(localeParams[0], localeParams[1], localeParams[2]);
                    }
                }
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            Log.e(TAG, "updateTextServicesLocaleLocked: Reflection failed on getCurrentSpellCheckerSubtype"
                    + e);
            // (EW) this isn't the same as what the AOSP version does. this is just getting the
            // current locale, rather than the enabled locale for spell check in the system. it's
            // probably not very common for the enabled spell check locale to differ from the
            // current locale, so most of the time this would be the same. since it seems that apps
            // aren't allowed to get the enabled spell check locale, maybe this is just how spell
            // apps are expected to do this. simply trying to spell check based on the current
            // locale seems reasonable, but it would just be nice to match AOSP functionality.
            locale = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    ? getContext().getResources().getConfiguration().getLocales().get(0)
                    : getContext().getResources().getConfiguration().locale;
        }

        mCurrentSpellCheckerLocaleCache = locale;
    }

    void onLocaleChanged() {
        mEditor.onLocaleChanged();
    }

    /**
     * This method is used by the ArrowKeyMovementMethod to jump from one word to the other.
     * Made available to achieve a consistent behavior.
     * @hide
     */
    public WordIterator getWordIterator() {
        return mEditor.getWordIterator();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public CharSequence getAccessibilityClassName() {
        return EditText.class.getName();
    }

    // (EW) the AOSP version starting in Q overrode a different overload, but that's hidden, so we
    // need to still to this. as of S, that overload was protected and only called from
    // onProvideStructure, onProvideContentCaptureStructure, and onProvideAutofillStructure in View,
    // so overriding those 3 methods should allow matching functionality.
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onProvideStructure(ViewStructure structure) {
        super.onProvideStructure(structure);
        onProvideStructure(structure, VIEW_STRUCTURE_FOR_ASSIST);
    }

    // (EW) the AOSP version starting in Q overrode a different overload of onProvideStructure
    // instead of this, but that's hidden. View#onProvideAutofillStructure calls that hidden
    // overload, so we still need this to match functionality.
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onProvideAutofillStructure(ViewStructure structure, int flags) {
        super.onProvideAutofillStructure(structure, flags);
        onProvideStructure(structure, VIEW_STRUCTURE_FOR_AUTOFILL);
    }

    // (EW) the AOSP version never used this, but since the hidden override of onProvideStructure is
    // used and View#onProvideContentCaptureStructure calls it, we need to use this to match
    // functionality.
    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    public void onProvideContentCaptureStructure(ViewStructure structure, int flags) {
        super.onProvideContentCaptureStructure(structure, flags);
        onProvideStructure(structure, VIEW_STRUCTURE_FOR_CONTENT_CAPTURE);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void onProvideStructure(@NonNull ViewStructure structure,
                                    @ViewStructureType int viewFor) {
        final boolean isPassword = hasPasswordTransformationMethod()
                || isPasswordInputType(getInputType());
        if (viewFor == VIEW_STRUCTURE_FOR_AUTOFILL
                || viewFor == VIEW_STRUCTURE_FOR_CONTENT_CAPTURE) {
            if (viewFor == VIEW_STRUCTURE_FOR_AUTOFILL
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                structure.setDataIsSensitive(!mTextSetFromXmlOrResourceId);
            }
            if (mTextId != Resources.ID_NULL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // (EW) nothing was done for this prior to Pie
                try {
                    structure.setTextIdEntry(getResources().getResourceEntryName(mTextId));
                } catch (Resources.NotFoundException e) {
                    if (AUTOFILL_HELPER_VERBOSE) {
                        Log.v(LOG_TAG, "onProvideAutofillStructure(): cannot set name for text id "
                                + mTextId + ": " + e.getMessage());
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // (EW) nothing was done for this prior to S
                String[] mimeTypes = getReceiveContentMimeTypes();
                if (mimeTypes == null) {
                    // If the app hasn't set a listener for receiving content on this view (ie,
                    // getReceiveContentMimeTypes() returns null), check if it implements the
                    // keyboard image API and, if possible, use those MIME types as fallback.
                    // This fallback is only in place for autofill, not other mechanisms for
                    // inserting content. See AUTOFILL_NON_TEXT_REQUIRES_ON_RECEIVE_CONTENT_LISTENER
                    // in TextViewOnReceiveContentListener for more info.
                    mimeTypes = mEditor.getDefaultOnReceiveContentListener()
                            .getFallbackMimeTypesForAutofill(this);
                }
                structure.setReceiveContentMimeTypes(mimeTypes);
            }
        }

        if (!isPassword || viewFor == VIEW_STRUCTURE_FOR_AUTOFILL
                || viewFor == VIEW_STRUCTURE_FOR_CONTENT_CAPTURE) {
            if (mLayout == null) {
                if (viewFor == VIEW_STRUCTURE_FOR_CONTENT_CAPTURE) {
                    Log.w(LOG_TAG, "onProvideContentCaptureStructure(): calling assumeLayout()");
                }
                assumeLayout();
            }
            Layout layout = mLayout;
            final int lineCount = layout.getLineCount();
            if (lineCount <= 1) {
                // Simple case: this is a single line.
                final CharSequence text = getText();
                if (viewFor == VIEW_STRUCTURE_FOR_AUTOFILL) {
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
                    // Cap the offsets to avoid an OOB exception. That can happen if the
                    // displayed/layout text, on which these offsets are calculated, is longer
                    // than the original text (such as when the view is translated by the
                    // platform intelligence).
                    // TODO(b/196433694): Figure out how to better handle the offset
                    // calculations for this case (so we don't unnecessarily cutoff the original
                    // text, for example).
                    expandedTopChar = Math.min(expandedTopChar, text.length());
                    expandedBottomChar = Math.min(expandedBottomChar, text.length());
                    text = text.subSequence(expandedTopChar, expandedBottomChar);
                }

                if (viewFor == VIEW_STRUCTURE_FOR_AUTOFILL) {
                    structure.setText(text);
                } else {
                    structure.setText(text,
                            selStart - expandedTopChar,
                            selEnd - expandedTopChar);

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

            if (viewFor == VIEW_STRUCTURE_FOR_ASSIST
                    || viewFor == VIEW_STRUCTURE_FOR_CONTENT_CAPTURE) {
                // Extract style information that applies to the EditText as a whole.
                int style = 0;
                int typefaceStyle = getTypefaceStyle();
                if ((typefaceStyle & Typeface.BOLD) != 0) {
                    style |= AssistStructure.ViewNode.TEXT_STYLE_BOLD;
                }
                if ((typefaceStyle & Typeface.ITALIC) != 0) {
                    style |= AssistStructure.ViewNode.TEXT_STYLE_ITALIC;
                }

                // Global styles can also be set via EditText.setPaintFlags().
                int paintFlags = mTextPaint.getFlags();
                if ((paintFlags & Paint.FAKE_BOLD_TEXT_FLAG) != 0) {
                    style |= AssistStructure.ViewNode.TEXT_STYLE_BOLD;
                }
                if ((paintFlags & Paint.UNDERLINE_TEXT_FLAG) != 0) {
                    style |= AssistStructure.ViewNode.TEXT_STYLE_UNDERLINE;
                }
                if ((paintFlags & Paint.STRIKE_THRU_TEXT_FLAG) != 0) {
                    style |= AssistStructure.ViewNode.TEXT_STYLE_STRIKE_THRU;
                }

                // EditText does not have its own text background color. A background is either part
                // of the View (and can be any drawable) or a BackgroundColorSpan inside the text.
                structure.setTextStyle(getTextSize(), getCurrentTextColor(),
                        AssistStructure.ViewNode.TEXT_COLOR_UNDEFINED /* bgColor */, style);
            }
            if ((viewFor == VIEW_STRUCTURE_FOR_AUTOFILL
                    || viewFor == VIEW_STRUCTURE_FOR_CONTENT_CAPTURE)
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // (EW) nothing was done for this prior to P
                structure.setMinTextEms(getMinEms());
                structure.setMaxTextEms(getMaxEms());
                int maxLength = -1;
                for (InputFilter filter : getFilters()) {
                    if (filter instanceof InputFilter.LengthFilter) {
                        maxLength = ((InputFilter.LengthFilter) filter).getMax();
                        break;
                    }
                }
                structure.setMaxTextLength(maxLength);
            }
        }
        if (mHintId != Resources.ID_NULL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // (EW) nothing was done for this prior to R
            try {
                structure.setHintIdEntry(getResources().getResourceEntryName(mHintId));
            } catch (Resources.NotFoundException e) {
                if (AUTOFILL_HELPER_VERBOSE) {
                    Log.v(LOG_TAG, "onProvideAutofillStructure(): cannot set name for hint id "
                            + mHintId + ": " + e.getMessage());
                }
            }
        }
        structure.setHint(getHint());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // (EW) nothing was done for this prior to Oreo
            structure.setInputType(getInputType());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    boolean canRequestAutofill() {
        if (!isAutofillable()) {
            return false;
        }
        final AutofillManager afm = getAutofillManager();
        if (afm != null) {
            return afm.isEnabled();
        }
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void requestAutofill() {
        final AutofillManager afm = getAutofillManager();
        if (afm != null) {
            afm.requestAutofill(this);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void autofill(AutofillValue value) {
        if (!isTextEditable()) {
            Log.w(LOG_TAG, "cannot autofill non-editable EditText: " + this);
            return;
        }
        if (!value.isText()) {
            Log.w(LOG_TAG, "value of type " + value.describeContents()
                    + " cannot be autofilled into " + this);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            final ClipData clip = ClipData.newPlainText("", value.getTextValue());
            final ContentInfo payload = new ContentInfo.Builder(clip, SOURCE_AUTOFILL).build();
            performReceiveContent(payload);
        } else {
            final CharSequence autofilledValue = value.getTextValue();

            // First autofill it...
            setText(autofilledValue, true, 0);

            // ...then move cursor to the end.
            final Spannable text = getText();
            Selection.setSelection(text, text.length());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int getAutofillType() {
        return isTextEditable() ? AUTOFILL_TYPE_TEXT : AUTOFILL_TYPE_NONE;
    }

    /**
     * Gets the {@link EditText}'s current text for AutoFill. The value is trimmed to 100K
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
        if (arguments != null && extraDataKey.equals(EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY)) {
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
            return;
        }
        if (extraDataKey.equals(AccessibilityNodeInfo.EXTRA_DATA_RENDERING_INFO_KEY)) {
            //TODO: (EW) the AOSP version starting in R obtained a
            // AccessibilityNodeInfo.ExtraRenderingInfo to set the layout and text size in order to
            // call #setExtraRenderingInfo on info, but that method is hidden and there doesn't seem
            // to be a way for apps to even get the AccessibilityNodeInfo.ExtraRenderingInfo or
            // update those values, so I'm not sure what can be done.
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
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
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

    void sendAccessibilityEventTypeViewTextChanged(CharSequence beforeText, int fromIndex,
                                                   int removedCount, int addedCount) {
        AccessibilityEvent event =
                AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);
        event.setFromIndex(fromIndex);
        event.setRemovedCount(removedCount);
        event.setAddedCount(addedCount);
        event.setBeforeText(beforeText);
        sendAccessibilityEventUnchecked(event);
    }

    InputMethodManager getInputMethodManager() {
        return getServiceManager(InputMethodManager.class, Context.INPUT_METHOD_SERVICE);
    }

    /**
     * Returns whether this text view is a current input method target.  The
     * default implementation just checks with {@link InputMethodManager}.
     * @return True if the EditText is a current input method target; false otherwise.
     */
    public boolean isInputMethodTarget() {
        InputMethodManager imm = getInputMethodManager();
        return imm != null && imm.isActive(this);
    }

    static final int ID_SELECT_ALL = android.R.id.selectAll;
    // (EW) this feature didn't exist in older versions of AOSP, but this doesn't actually rely on
    // something from the system, so even though the ID doesn't exist on the older version, we just
    // need something to pass around internally to make this work.
    static final int ID_UNDO = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            ? android.R.id.undo : 16908338;
    // (EW) this feature didn't exist in older versions of AOSP, but this doesn't actually rely on
    // something from the system, so even though the ID doesn't exist on the older version, we just
    // need something to pass around internally to make this work.
    static final int ID_REDO = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            ? android.R.id.redo : 16908339;
    static final int ID_CUT = android.R.id.cut;
    static final int ID_COPY = android.R.id.copy;
    static final int ID_PASTE = android.R.id.paste;
    // (EW) this feature didn't exist in older versions of AOSP, but this doesn't actually rely on
    // something from the system, so even though the ID doesn't exist on the older version, we just
    // need something to pass around internally to make this work.
    static final int ID_SHARE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            ? android.R.id.shareText : 16908341;
    // (EW) this feature didn't exist in older versions of AOSP, but this doesn't actually rely on
    // something from the system, so even though the ID doesn't exist on the older version, we just
    // need something to pass around internally to make this work.
    static final int ID_PASTE_AS_PLAIN_TEXT = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            ? android.R.id.pasteAsPlainText : 16908337;
    // (EW) this feature didn't exist in older versions of AOSP, but this doesn't actually rely on
    // something from the system, so even though the ID doesn't exist on the older version, we just
    // need something to pass around internally to make this work.
    static final int ID_REPLACE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            ? android.R.id.replaceText : 16908340;
    @RequiresApi(api = Build.VERSION_CODES.O)
    static final int ID_ASSIST = android.R.id.textAssist;
    @RequiresApi(api = Build.VERSION_CODES.O)
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
        if (id == ID_SELECT_ALL) {
            final boolean hadSelection = hasSelection();
            selectAllText();
            if (hadSelection) {
                mEditor.invalidateActionModeAsync();
            }
            return true;
        }
        if (id == ID_UNDO) {
            mEditor.undo();
            return true;  // Returns true even if nothing was undone.
        }
        if (id == ID_REDO) {
            mEditor.redo();
            return true;  // Returns true even if nothing was undone.
        }
        if (id == ID_PASTE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                paste(true /* withFormatting */);
            } else {
                paste(min, max, true /* withFormatting */);
            }
            return true;
        }
        if (id == ID_PASTE_AS_PLAIN_TEXT) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                paste(false /* withFormatting */);
            } else {
                paste(min, max, false /* withFormatting */);
            }
            return true;
        }
        if (id == ID_CUT) {
            final ClipData cutData = ClipData.newPlainText(null, getTransformedText(min, max));
            if (setPrimaryClip(cutData)) {
                deleteText_internal(min, max);
            } else {
                Toast.makeText(getContext(),
                        R.string.failed_to_copy_to_clipboard,
                        Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        if (id == ID_COPY) {
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
        }
        if (id == ID_REPLACE) {
            mEditor.replace();
            return true;
        }
        if (id == ID_SHARE) {
            shareSelectedText();
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && id == ID_AUTOFILL) {
            requestAutofill();
            stopTextActionMode();
            return true;
        }
        return false;
    }

    CharSequence getTransformedText(int start, int end) {
        return removeSuggestionSpans(mTransformed.subSequence(start, end));
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public boolean performLongClick() {
        if (DEBUG_CURSOR) {
            logCursor("performLongClick", null);
        }

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
        }

        return handled;
    }

    @Override
    protected void onScrollChanged(int horiz, int vert, int oldHoriz, int oldVert) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert);
        mEditor.onScrollChanged();
    }

    /**
     * Return whether or not suggestions are enabled on this EditText. The suggestions are generated
     * by the IME or by the spell checker as the user types. This is done by adding
     * {@link SuggestionSpan}s to the text.
     *
     * When suggestions are enabled (default), this list of suggestions will be displayed when the
     * user asks for them on these parts of the text. This value depends on the inputType of this
     * EditText.
     *
     * The class of the input type must be {@link InputType#TYPE_CLASS_TEXT}.
     *
     * In addition, the type variation must be one of
     * {@link InputType#TYPE_TEXT_VARIATION_NORMAL},
     * {@link InputType#TYPE_TEXT_VARIATION_EMAIL_SUBJECT},
     * {@link InputType#TYPE_TEXT_VARIATION_LONG_MESSAGE},
     * {@link InputType#TYPE_TEXT_VARIATION_SHORT_MESSAGE} or
     * {@link InputType#TYPE_TEXT_VARIATION_WEB_EDIT_TEXT}.
     *
     * And finally, the {@link InputType#TYPE_TEXT_FLAG_NO_SUGGESTIONS} flag must <i>not</i> be set.
     *
     * @return true if the suggestions popup window is enabled, based on the inputType.
     */
    public boolean isSuggestionsEnabled() {
        if ((mEditor.mInputType & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) {
            return false;
        }
        if ((mEditor.mInputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) > 0) return false;

        final int variation = mEditor.mInputType & EditorInfo.TYPE_MASK_VARIATION;
        return (variation == EditorInfo.TYPE_TEXT_VARIATION_NORMAL
                || variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_SUBJECT
                || variation == EditorInfo.TYPE_TEXT_VARIATION_LONG_MESSAGE
                || variation == EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE
                || variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT);
    }

    /**
     * If provided, this ActionMode.Callback will be used to create the ActionMode when text
     * selection is initiated in this View.
     *
     * <p>The standard implementation populates the menu with a subset of Select All, Cut, Copy,
     * Paste, Replace and Share actions, depending on what this View supports.
     *
     * <p>A custom implementation can add new entries in the default menu in its
     * {@link android.view.ActionMode.Callback#onPrepareActionMode(ActionMode, android.view.Menu)}
     * method. The default actions can also be removed from the menu using
     * {@link android.view.Menu#removeItem(int)} and passing {@link android.R.id#selectAll},
     * {@link android.R.id#cut}, {@link android.R.id#copy}, {@link android.R.id#paste},
     * {@link android.R.id#replaceText} or {@link android.R.id#shareText} ids as parameters.
     *
     * <p>Returning false from
     * {@link android.view.ActionMode.Callback#onCreateActionMode(ActionMode, android.view.Menu)}
     * will prevent the action mode from being started.
     *
     * <p>Action click events should be handled by the custom implementation of
     * {@link android.view.ActionMode.Callback#onActionItemClicked(ActionMode,
     * android.view.MenuItem)}.
     *
     * <p>Note that text selection mode is not started when a EditText receives focus and the
     * {@link android.R.attr#selectAllOnFocus} flag has been set. The content is highlighted in
     * that case, to allow for quick replacement.
     */
    public void setCustomSelectionActionModeCallback(ActionMode.Callback actionModeCallback) {
        mEditor.mCustomSelectionActionModeCallback = actionModeCallback;
    }

    /**
     * Retrieves the value set in {@link #setCustomSelectionActionModeCallback}. Default is null.
     *
     * @return The current custom selection callback.
     */
    public ActionMode.Callback getCustomSelectionActionModeCallback() {
        return mEditor.mCustomSelectionActionModeCallback;
    }

    /**
     * If provided, this ActionMode.Callback will be used to create the ActionMode when text
     * insertion is initiated in this View.
     * The standard implementation populates the menu with a subset of Select All,
     * Paste and Replace actions, depending on what this View supports.
     *
     * <p>A custom implementation can add new entries in the default menu in its
     * {@link android.view.ActionMode.Callback#onPrepareActionMode(android.view.ActionMode,
     * android.view.Menu)} method. The default actions can also be removed from the menu using
     * {@link android.view.Menu#removeItem(int)} and passing {@link android.R.id#selectAll},
     * {@link android.R.id#paste} or {@link android.R.id#replaceText} ids as parameters.</p>
     *
     * <p>Returning false from
     * {@link android.view.ActionMode.Callback#onCreateActionMode(android.view.ActionMode,
     * android.view.Menu)} will prevent the action mode from being started.</p>
     *
     * <p>Action click events should be handled by the custom implementation of
     * {@link android.view.ActionMode.Callback#onActionItemClicked(android.view.ActionMode,
     * android.view.MenuItem)}.</p>
     *
     * <p>Note that text insertion mode is not started when a EditText receives focus and the
     * {@link android.R.attr#selectAllOnFocus} flag has been set.</p>
     */
    public void setCustomInsertionActionModeCallback(ActionMode.Callback actionModeCallback) {
        mEditor.mCustomInsertionActionModeCallback = actionModeCallback;
    }

    /**
     * Retrieves the value set in {@link #setCustomInsertionActionModeCallback}. Default is null.
     *
     * @return The current custom insertion callback.
     */
    public ActionMode.Callback getCustomInsertionActionModeCallback() {
        return mEditor.mCustomInsertionActionModeCallback;
    }

    protected void stopTextActionMode() {
        mEditor.stopTextActionMode();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void hideFloatingToolbar(int durationMs) {
        mEditor.hideFloatingToolbar(durationMs);
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

        if (mText.length() > 0 && hasSelection() && mEditor.mKeyListener != null) {
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
        // (EW) the AOSP version also checked Context#canStartActivityForResult, which is hidden and
        // marked as UnsupportedAppUsage, and it seems like it might only apply to a framework view,
        // so I'm not sure that there is anything to do for that.
        if (!isDeviceProvisioned()) {
            return false;
        }
        return canCopy();
    }

    boolean isDeviceProvisioned() {
        if (mDeviceProvisionedState == DEVICE_PROVISIONED_UNKNOWN) {
            mDeviceProvisionedState = Settings.Global.getInt(
                    getContext().getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 0) != 0
                    ? DEVICE_PROVISIONED_YES
                    : DEVICE_PROVISIONED_NO;
        }
        return mDeviceProvisionedState == DEVICE_PROVISIONED_YES;
    }

    boolean canPaste() {
        return (mEditor.mKeyListener != null
                && getSelectionStart() >= 0
                && getSelectionEnd() >= 0
                && getClipboardManager().hasPrimaryClip());
    }

    boolean canPasteAsPlainText() {
        if (!canPaste()) {
            return false;
        }

        final ClipboardManager clipboardManager = getClipboardManager();
        final ClipDescription description = clipboardManager.getPrimaryClipDescription();
        final boolean isPlainType = description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);
        if (isPlainType) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (description.isStyledText()) {
                    return true;
                }
            } else {
                final ClipData clipData = clipboardManager.getPrimaryClip();
                final CharSequence text = clipData.getItemAt(0).getText();
                if (text instanceof Spanned) {
                    Spanned spanned = (Spanned) text;
                    if (HiddenTextUtils.hasStyleSpan(spanned)) {
                        return true;
                    }
                }
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
        return canSelectText() && !hasPasswordTransformationMethod()
                && !(getSelectionStart() == 0 && getSelectionEnd() == mText.length());
    }

    boolean selectAllText() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Hide the toolbar before changing the selection to avoid flickering.
            hideFloatingToolbar(FLOATING_TOOLBAR_SELECT_ALL_REFRESH_DELAY);
        }
        final int length = mText.length();
        Selection.setSelection(mText, 0, length);
        return length > 0;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void paste(boolean withFormatting) {
        ClipboardManager clipboard = getClipboardManager();
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null) {
            return;
        }
        final ContentInfo payload = new ContentInfo.Builder(clip, SOURCE_CLIPBOARD)
                .setFlags(withFormatting ? 0 : FLAG_CONVERT_TO_PLAIN_TEXT)
                .build();
        performReceiveContent(payload);
        sLastCutCopyOrTextChangedTime = 0;
    }

    // (EW) only necessary for versions prior to S
    /**
     * Paste clipboard content between min and max positions.
     */
    private void paste(int min, int max, boolean withFormatting) {
        ClipboardManager clipboard = getClipboardManager();
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null) {
            return;
        }
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
                    Selection.setSelection(mText, max);
                    mText.replace(min, max, paste);
                    didFirst = true;
                } else {
                    mText.insert(getSelectionEnd(), "\n");
                    mText.insert(getSelectionEnd(), paste);
                }
            }
        }
        sLastCutCopyOrTextChangedTime = 0;
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
            Selection.setSelection(mText, getSelectionEnd());
        }
    }

    private boolean setPrimaryClip(ClipData clip) {
        ClipboardManager clipboard = getClipboardManager();
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

    /**
     * Handles drag events sent by the system following a call to
     * {@link View#startDragAndDrop(ClipData,DragShadowBuilder,Object,int) startDragAndDrop()}.
     *
     * <p>If this text view is not editable, delegates to the default {@link View#onDragEvent}
     * implementation.
     *
     * <p>If this text view is editable, accepts all drag actions (returns true for an
     * {@link DragEvent#ACTION_DRAG_STARTED ACTION_DRAG_STARTED} event and all
     * subsequent drag events). While the drag is in progress, updates the cursor position
     * to follow the touch location. Once a drop event is received, handles content insertion
     * via {@link #performReceiveContent}.
     *
     * @param event The {@link DragEvent} sent by the system.
     * The {@link DragEvent#getAction()} method returns an action type constant
     * defined in DragEvent, indicating the type of drag event represented by this object.
     * @return Returns true if this text view is editable and delegates to super otherwise.
     * See {@link View#onDragEvent}.
     */
    @Override
    public boolean onDragEvent(DragEvent event) {
        if (!mEditor.hasInsertionController()) {
            // If this EditText is not editable, defer to the default View implementation. This
            // will check for the presence of an OnReceiveContentListener and accept/reject
            // drag events depending on whether the listener is/isn't set.
            return super.onDragEvent(event);
        }
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return true;

            case DragEvent.ACTION_DRAG_ENTERED:
                EditText.this.requestFocus();
                return true;

            case DragEvent.ACTION_DRAG_LOCATION:
                final int offset = getOffsetForPosition(event.getX(), event.getY());
                Selection.setSelection(mText, offset);
                return true;

            case DragEvent.ACTION_DROP:
                mEditor.onDrop(event);
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
     * Returns resolved {@link TextDirectionHeuristic} that will be used for text layout.
     * The {@link TextDirectionHeuristic} that is used by EditText is only available after
     * {@link #getTextDirection()} and {@link #getLayoutDirection()} is resolved. Therefore the
     * return value may not be the same as the one EditText uses if the View's layout direction is
     * not resolved or detached from parent root view.
     */
    public @NonNull TextDirectionHeuristic getTextDirectionHeuristic() {
        if (hasPasswordTransformationMethod()) {
            // passwords fields should be LTR
            return TextDirectionHeuristics.LTR;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // (EW) nothing was done for this prior to O
            if ((mEditor.mInputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_PHONE) {
                // Phone numbers must be in the direction of the locale's digits. Most locales have LTR
                // digits, but some locales, such as those written in the Adlam or N'Ko scripts, have
                // RTL digits.
                final DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(getTextLocale());
                String zero;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    zero = symbols.getDigitStrings()[0];
                } else {
                    // (EW) DecimalFormatSymbols#getDigitStrings was used in Oreo, but it wasn't
                    // made accessible until Pie. this isn't great, but this use of reflection is at
                    // least relatively safe since it's only done on old versions so it shouldn't
                    // just stop working at some point in the future.
                    try {
                        Method getDigitStringsMethod =
                                DecimalFormatSymbols.class.getMethod("getDigitStrings");
                        zero = ((String[]) getDigitStringsMethod.invoke(symbols))[0];
                    } catch (NoSuchMethodException | IllegalAccessException
                            | InvocationTargetException e) {
                        Log.e(TAG, "getTextDirectionHeuristic: Reflection failed on getDigitStrings: "
                                + e.getClass().getSimpleName() + ": " + e.getMessage());
                        zero = null;
                    }
                }
                if (zero != null) {
                    // In case the zero digit is multi-codepoint, just use the first codepoint to
                    // determine direction.
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
     */
    void deleteText_internal(int start, int end) {
        mText.delete(start, end);
    }

    /**
     * Replaces the range of text [start, end[ by replacement text
     */
    void replaceText_internal(int start, int end, CharSequence text) {
        mText.replace(start, end, text);
    }

    /**
     * Sets a span on the specified range of text
     */
    void setSpan_internal(Object span, int start, int end, int flags) {
        mText.setSpan(span, start, end, flags);
    }

    /**
     * Moves the cursor to the specified offset position in text
     */
    void setCursorPosition_internal(int start, int end) {
        Selection.setSelection(mText, start, end);
    }

    /**
     * User interface state that is stored by EditText for implementing
     * {@link View#onSaveInstanceState}.
     */
    public static class SavedState extends BaseSavedState {
        int selStart = -1;
        int selEnd = -1;
        CharSequence text;
        boolean frozenWithFocus;
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
        public static final @NonNull Parcelable.Creator<SavedState> CREATOR =
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
                editorState = ParcelableParcel.CREATOR.createFromParcel(in);
            }
        }
    }

    private static class CharWrapper implements CharSequence {
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
    }

    private class ChangeWatcher implements TextWatcher, SpanWatcher {

        private CharSequence mBeforeText;

        public void beforeTextChanged(CharSequence buffer, int start,
                                      int before, int after) {
            if (DEBUG_EXTRACT) {
                Log.v(LOG_TAG, "beforeTextChanged start=" + start
                        + " before=" + before + " after=" + after + ": " + buffer);
            }

            AccessibilityManager accessibilityManager = getAccessibilityManager();
            if (accessibilityManager != null && accessibilityManager.isEnabled()
                    && mTransformed != null) {
                mBeforeText = mTransformed.toString();
            }

            EditText.this.sendBeforeTextChanged(buffer, start, before, after);
        }

        public void onTextChanged(CharSequence buffer, int start, int before, int after) {
            if (DEBUG_EXTRACT) {
                Log.v(LOG_TAG, "onTextChanged start=" + start
                        + " before=" + before + " after=" + after + ": " + buffer);
            }
            EditText.this.handleTextChanged(buffer, start, before, after);

            AccessibilityManager accessibilityManager = getAccessibilityManager();
            if (accessibilityManager != null && accessibilityManager.isEnabled()
                    && (isFocused() || isSelected() && isShown())) {
                sendAccessibilityEventTypeViewTextChanged(mBeforeText, start, before, after);
                mBeforeText = null;
            }
        }

        public void afterTextChanged(Editable buffer) {
            if (DEBUG_EXTRACT) {
                Log.v(LOG_TAG, "afterTextChanged: " + buffer);
            }
            EditText.this.sendAfterTextChanged(buffer);

            // (EW) the AOSP version also checked MetaKeyKeyListener#getMetaState with
            // MetaKeyKeyListener.META_SELECTING, which is hidden, to set call
            // MetaKeyKeyListener.stopSelecting.
            // MetaKeyKeyListener.META_SELECTING = KeyEvent.META_SELECTING = 0x800 has been defined
            // at least since Kitkat, but it has been hidden with a comment saying it's pending API
            // review, and at least as of S, KeyEvent.META_SELECTING has been marked
            // UnsupportedAppUsage (maxTargetSdk R). after this long it seems unlikely for this to
            // be released for apps to use, and this could theoretically get changed in a future
            // version, so it wouldn't be completely safe to just hard-code 0x800. I only found this
            // constant used in getMetaState throughout AOSP code, so skipping it probably won't
            // even cause a real lack of functionality (at least currently) since other apps
            // probably aren't using it either. same basic need to skip this in
            // Editor#extractTextInternal, ArrowKeyMovementMethod#handleMovementKey,
            // Touch#onTouchEvent, and EditableInputConnection#setSelection (originally
            // BaseInputConnection). also MetaKeyKeyListener#stopSelecting is hidden pending API
            // review and marked with UnsupportedAppUsage, so there isn't much we could do.
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

    /**
     * Default {@link EditText} implementation for receiving content. Apps wishing to provide
     * custom behavior should configure a listener via {@link #setOnReceiveContentListener}.
     *
     * <p>For editable EditTexts the default behavior is to insert text into the view, coercing
     * non-text content to text as needed. The MIME types "text/plain" and "text/html" have
     * well-defined behavior for this, while other MIME types have reasonable fallback behavior
     * (see {@link ClipData.Item#coerceToStyledText}).
     *
     * @param payload The content to insert and related metadata.
     *
     * @return The portion of the passed-in content that was not handled (may be all, some, or none
     * of the passed-in content).
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
    @Nullable
    @Override
    public ContentInfo onReceiveContent(@NonNull ContentInfo payload) {
        return mEditor.getDefaultOnReceiveContentListener().onReceiveContent(this, payload);
    }

    /**
     * Collects a {@link ViewTranslationRequest} which represents the content to be translated in
     * the view.
     *
     * <p>NOTE: When overriding the method, it should not translate the password. If the subclass
     * uses {@link TransformationMethod} to display the translated result, it's also not recommend
     * to translate text is selectable or editable.
     *
     * @param supportedFormats the supported translation format. The value could be {@link
     *                         android.view.translation.TranslationSpec#DATA_FORMAT_TEXT}.
     * @return the {@link ViewTranslationRequest} which contains the information to be translated.
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    public void onCreateViewTranslationRequest(@NonNull int[] supportedFormats,
            @NonNull Consumer<ViewTranslationRequest> requestsCollector) {
        if (supportedFormats == null || supportedFormats.length == 0) {
            // Do not provide the support translation formats
            return;
        }
        ViewTranslationRequest.Builder requestBuilder =
                new ViewTranslationRequest.Builder(getAutofillId());
        // Support Text translation
        if (ArrayUtils.contains(supportedFormats, TranslationSpec.DATA_FORMAT_TEXT)) {
            if (mText.length() == 0) {
                // Cannot create translation request for the empty text
                return;
            }
            // TODO(b/177214256): support selectable text translation.
            //  We use the TransformationMethod to implement showing the translated text. The
            //  EditText does not support the text length change for TransformationMethod. If the
            //  text is selectable or editable, it will crash while selecting the text. To support
            //  it, it needs broader changes to text APIs, we only allow to translate non selectable
            //  and editable text in S.
            // Cannot create translation request
            // (EW) because EditText content is always considered selectable
            return;
        }
        requestsCollector.accept(requestBuilder.build());
    }

    // (EW) wrapper to get a drawable on any version
    @SuppressLint("UseCompatLoadingForDrawables")
    Drawable getDrawable(int res) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return getContext().getDrawable(res);
        } else {
            return getContext().getResources().getDrawable(res);
        }
    }

    // (EW) from View
    void transformFromViewToWindowSpace(@Size(2) int[] inOutLocation) {
        if (inOutLocation == null || inOutLocation.length < 2) {
            throw new IllegalArgumentException("inOutLocation must be an array of two integers");
        }

        if (!isAttachedToWindow()) {
            // When the view is not attached to a window, this method does not make sense
            inOutLocation[0] = inOutLocation[1] = 0;
            return;
        }

        float[] position = new float[2];
        position[0] = inOutLocation[0];
        position[1] = inOutLocation[1];

        if (!hasIdentityMatrix(this)) {
            getMatrix().mapPoints(position);
        }

        position[0] += getLeft();
        position[1] += getTop();

        ViewParent viewParent = getParent();
        while (viewParent instanceof View) {
            final View view = (View) viewParent;

            position[0] -= view.getScrollX();
            position[1] -= view.getScrollY();

            if (!hasIdentityMatrix(view)) {
                view.getMatrix().mapPoints(position);
            }

            position[0] += view.getLeft();
            position[1] += view.getTop();

            viewParent = view.getParent();
        }

        // (EW) the AOSP version would subtract ViewRootImpl#mCurScrollY from position[1] if
        // viewParent was a ViewRootImpl, but ViewRootImpl is hidden and starting in Pie,
        // ViewRootImpl#mCurScrollY is a restricted API (warning logged specifies "dark greylist").
        // I'm not sure when this is actually necessary, but it seems that there isn't anything we
        // can do. until there is a known issue skipping this causes, there probably isn't a chance
        // of finding some alternative.

        inOutLocation[0] = Math.round(position[0]);
        inOutLocation[1] = Math.round(position[1]);
    }

    // (EW) from View
    /**
     * Returns true if the transform matrix is the identity matrix.
     * Recomputes the matrix if necessary.
     *
     * @return True if the transform matrix is the identity matrix, false otherwise.
     */
    private static boolean hasIdentityMatrix(View view) {
        // (EW) the AOSP version called RenderNode#hasIdentityMatrix, and documentation for that
        // states that it's just a faster way to do the otherwise equivalent
        // RenderNode#getMatrix(Matrix) Matrix#isIdentity(). View#getMatrix calls
        // RenderNode#getMatrix(Matrix), so we can just use that for an equivalent (but slower)
        // check.
        return view.getMatrix().isIdentity();
    }

    // (EW) from View
    /**
     * Transforms a motion event from on-screen coordinates to view-local
     * coordinates.
     *
     * @param ev the on-screen motion event
     * @return false if the transformation could not be applied
     */
    boolean toLocalMotionEvent(MotionEvent ev) {
        // (EW) the AOSP version checked if View#mAttachInfo was null directly, but that's hidden,
        // so we need to call the equivalent API
        if (!isAttachedToWindow()) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Matrix m = new Matrix();
            m.set(IDENTITY_MATRIX);
            // (EW) transformMatrixToLocal should be available prior to Q, but in case it isn't,
            // fallback to pre-Lollipop logic
            if (tryTransformMatrixToLocal(m)) {
                ev.transform(m);
                return true;
            }
        }
        // (EW) this is the logic from Kitkat
        // (EW) the AOSP version used the negative values of View#mAttachInfo.mWindowLeft and
        // View#mAttachInfo.mWindowTop directly to call MotionEvent#offsetLocation, but since
        // View#mAttachInfo is hidden, we would need to call View#getLocationOnScreen instead. I'm
        // not sure why it did that. At least in my testing, transformMotionEventToLocal does that
        // same offsetting of the location, so with both, it just doubles the shift, which is
        // incorrect. #transformMotionEventToLocal is more analogous to View#transformMatrixToLocal,
        // which replaced it, so I'm keeping that and skipping the offset from
        // View#getLocationOnScreen.
        transformMotionEventToLocal(this, ev);
        return true;
    }

    // (EW) View#transformMatrixToLocal was made available in Q, but it was actually added in
    // Lollipop, so it should be safe to call on these older versions, but to be extra safe we'll
    // wrap it in a try/catch
    @SuppressLint("NewApi")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private boolean tryTransformMatrixToLocal(@NonNull Matrix matrix) {
        try {
            transformMatrixToLocal(matrix);
        } catch (Exception e) {
            Log.w(TAG, "View#transformMatrixToLocal couldn't be called: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
        return true;
    }

    // (EW) from View (Kitkat)
    /**
     * Recursive helper method that applies transformations in post-order.
     *
     * @param ev the on-screen motion event
     */
    private static void transformMotionEventToLocal(View view, MotionEvent ev) {
        final ViewParent parent = view.getParent();
        if (parent instanceof View) {
            final View vp = (View) parent;
            transformMotionEventToLocal(vp, ev);
            ev.offsetLocation(vp.getScrollX(), vp.getScrollY());
        }
        // (EW) the AOSP version also used ViewRootImpl#mCurScrollY to call
        // MotionEvent#offsetLocation, but we can't get that scroll. see comment in
        // #transformFromViewToWindowSpace.

        ev.offsetLocation(-view.getLeft(), -view.getTop());

        if (!hasIdentityMatrix(view)) {
            ev.transform(getInverseMatrix(view));
        }
    }

    // (EW) from View based on Kitkat code (changed in Lollipop) since this should only be getting
    // called prior to Lollipop
    /**
     * Utility method to retrieve the inverse of the current mMatrix property.
     * We cache the matrix to avoid recalculating it when transform properties
     * have not changed.
     *
     * @return The inverse of the current matrix of this view.
     */
    private static Matrix getInverseMatrix(View view) {
        // (EW) the AOSP version used mTransformationInfo, which we don't have access to, and
        // verified that it wasn't null. View#getMatrix calls View#updateMatrix, which was done next
        // here in the AOSP version, and it gets the gets the matrix we need to work with. it also
        // verifies mTransformationInfo isn't null and returns the identity matrix otherwise, and
        // since the inverse of the identity matrix is itself, no work would need to be done.
        Matrix matrix = view.getMatrix();
        if (!matrix.isIdentity()) {
            // (EW) the AOSP version used mTransformationInfo.mInverseMatrix as a cached version as
            // long as it wasn't marked dirty, but we don't have access to that, so we'll just
            // always calculate the inverse
            Matrix inverseMatrix = new Matrix();
            matrix.invert(inverseMatrix);
        }
        return IDENTITY_MATRIX;
    }

    // (EW) since View's version of this is hidden, we need a replacement. View called
    // Context#startActivityForResult, which I believe would end up passing the result to
    // View#onActivityResult (which was overridden in the AOSP version), but both of those are
    // hidden. simply calling Activity#startActivityForResult isn't great because it sends the
    // result to the Activity, so it would require extra wiring to pass it back here. we can create
    // a temporary fragment to allow it to receive the result and pass it back (based on
    // https://stackoverflow.com/a/44864164).
    /**
     * Call { @link Context#startActivityForResult(String, Intent, int, Bundle)} for the View's
     * Context, creating a unique View identifier to retrieve the result.
     *
     * @param intent The Intent to be started.
     * @param requestCode The request code to use.
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    void startActivityForResult(Intent intent, int requestCode) {
        Fragment tempFragment = FragmentForResult.newInstance(this);
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        FragmentManager fragmentManager = activity.getFragmentManager();
        fragmentManager.beginTransaction().add(tempFragment, "FRAGMENT_TAG").commit();
        fragmentManager.executePendingTransactions();
        tempFragment.startActivityForResult(intent, requestCode);
    }

    // (EW) unfortunately the Fragment needs to be public
    @RequiresApi(api = Build.VERSION_CODES.M)
    public static class FragmentForResult extends Fragment {
        private EditText mEditText;

        // (EW) the constructor shouldn't be overridden. see https://stackoverflow.com/a/10450535
        public static FragmentForResult newInstance(EditText editText) {
            FragmentForResult fragmentInstance = new FragmentForResult();

            // (EW) we need the actual EditText object (not an equivalent replica), so we can't just
            // pass it in a Bundle in setArguments. if this fragment was recreated, that probably
            // means that the original view was recreated too, meaning that our original object
            // probably wouldn't even exist.
            // this does cause broken scenarios, such as when the device is rotated after in the
            // activity for result and the result is sent, the activity isn't updated because the
            // original activity was also recreated. this is also an issue in the framework version.
            //FUTURE: maybe there's some way to track the ID and look up the matching recreated
            // EditText. #canProcessText already only passes if it has an ID (AOSP logic), which I
            // thought was weird. I thought it might have been necessary for exactly this, but that
            // doesn't seem to be the case and doesn't look like it ever was. I thought about
            // removing that since it seems arbitrary, but to avoid a loss of functionality if this
            // does get implemented (and to match AOSP functionality), I'm leaving that.
            fragmentInstance.mEditText = editText;

            return fragmentInstance;
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (mEditText != null) {
                mEditText.onActivityResult(requestCode, resultCode, data);
            }
            super.onActivityResult(requestCode, resultCode, data);
            getActivity().getFragmentManager().beginTransaction().remove(this).commit();
        }
    }

    // (EW) the AOSP version used Context#canStartActivityForResult, but since we have a custom
    // #startActivityForResult, we need different logic to validate that it can be used. since our
    // implementation uses a temporary fragment to handle receiving the result, we need to make sure
    // that we can get the fragment manager (the Context needs to be an Activity).
    boolean canStartActivityForResult() {
        return getActivity() != null;
    }

    // (EW) from View
    /**
     * Map a rectangle from view-relative coordinates to screen-relative coordinates
     *
     * @param rect The rectangle to be mapped
     * @param clipToParent Whether to clip child bounds to the parent ones.
     */
    private void mapRectFromViewToScreenCoords(RectF rect, boolean clipToParent) {
        if (!hasIdentityMatrix(this)) {
            getMatrix().mapRect(rect);
        }

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

            if (!hasIdentityMatrix(parentView)) {
                parentView.getMatrix().mapRect(rect);
            }

            rect.offset(parentView.getLeft(), parentView.getTop());

            parent = parentView.getParent();
        }

        // (EW) the AOSP version used ViewRootImpl#mCurScrollY to update rect's offset, but we can't
        // get that scroll. see comment in #transformFromViewToWindowSpace.

        // (EW) the AOSP version used View#mAttachInfo.mWindowLeft and View#mAttachInfo.mWindowTop
        // directly, but those are hidden. those values are returned in View#getLocationOnScreen, so
        // we can use that instead.
        int[] windowLocation = getLocationOnScreen();
        rect.offset(windowLocation[0], windowLocation[1]);
    }

    // (EW) from View
    /**
     * Indicates whether or not this view's layout is right-to-left. This is resolved from
     * layout attribute and/or the inherited value from the parent
     *
     * @return true if the layout is right-to-left.
     */
    private boolean isLayoutRtl() {
        return (getLayoutDirection() == LAYOUT_DIRECTION_RTL);
    }

    // (EW) from View
    int[] getLocationOnScreen() {
        int[] location = new int[2];
        getLocationOnScreen(location);
        return location;
    }

    // (EW) from View
    private Insets getOpticalInsets() {
        // (EW) the AOSP version first checks for the value that was manually set from
        // View#setOpticalInsets, but there isn't a way to get that other than reflection, and I
        // only found one case where it was called, so it's probably unlikely that it would cause a
        // problem here.
        return computeOpticalInsets();
    }

    // (EW) from View
    private Insets computeOpticalInsets() {
        Drawable background = getBackground();
        if (background == null) {
            return Insets.NONE;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // (EW) despite not actually getting called, on Pie, simply having this code here causes
            // this warning to be logged:
            // Accessing hidden method Landroid/graphics/drawable/Drawable;->getOpticalInsets()Landroid/graphics/Insets; (light greylist, linking)
            return new Insets(background.getOpticalInsets());
        }

        try {
            Method getOpticalInsetsMethod = Drawable.class.getMethod("getOpticalInsets");
            Object opticalInsets = getOpticalInsetsMethod.invoke(background);
            return new Insets(opticalInsets);
        } catch (NoSuchMethodException | IllegalAccessException
                | InvocationTargetException e) {
            Log.e(TAG, "computeOpticalInsets: Reflection failed on Drawable#getOpticalInsets: "
                    + e.getMessage());
            return Insets.NONE;
        }
    }

    // (EW) from View
    private static boolean isLayoutModeOptical(Object o) {
        return o instanceof ViewGroup && isLayoutModeOptical((ViewGroup) o);
    }

    // (EW) from ViewGroup
    /** Return true if this ViewGroup is laying out using optical bounds. */
    private static boolean isLayoutModeOptical(ViewGroup viewGroup) {
        return viewGroup.getLayoutMode() == ViewGroup.LAYOUT_MODE_OPTICAL_BOUNDS;
    }

    // (EW) from MediaRouteButton. this is necessary because the way the AOSP Editor gets the
    // DragAndDropPermissions isn't accessible for apps, so we need to find the activity to get it.
    @Nullable Activity getActivity() {
        // Gross way of unwrapping the Activity so we can get the FragmentManager
        Context context = getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity)context;
            }
            context = ((ContextWrapper)context).getBaseContext();
        }
        // (EW) MediaRouteButton threw an IllegalStateException because its Context was not an
        // Activity, but an EditText could be added to a view with a Context that isn't an Activity,
        // so we'll null to allow it to be handled.
        return null;
    }
}
