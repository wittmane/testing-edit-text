package com.wittmane.testingedittext;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Region;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

// modified from https://stackoverflow.com/a/31168391
public class CustomSimpleTextView2 extends View {

//    private int borderWidthLeft = dp(4);
//
//    private int borderWidthRight = dp(4);
//
//    private int borderWidthTop = dp(4);
//
//    private int borderWidthBottom = dp(4);

    private int boderColor = Color.BLACK;

    private int backgroundColor = Color.BLUE;

    private int textColor = Color.WHITE;

    private int textSize = sp(14);

    private Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

    private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

//    private int backgroundRectWidth = dp(70);

//    private int backgroundRectHeight = dp(55);

    private Rect textBgRect = new Rect();

    private String defaultText = "A";

//    public CustomSimpleTextView2(Context context) {
//        this(context, null);
//    }
//
//    public CustomSimpleTextView2(Context context, AttributeSet attrs) {
//        super(context, attrs);
//        init(context);
//    }
    public CustomSimpleTextView2(Context context) {
        this(context, null);
    }
    public CustomSimpleTextView2(Context context, AttributeSet attrs) {
//        this(context, attrs, /*com.android.internal.*/R.attr.editTextStyle);
        this(context, attrs, 0);
    }
    public CustomSimpleTextView2(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);

        final Resources.Theme theme = context.getTheme();
        TypedArray typedArray = theme.obtainStyledAttributes(
                attrs, R.styleable.CustomSimpleTextView2, defStyleAttr, 0);
//        int textResId = typedArray.getResourceId(
//                R.styleable.CustomSimpleTextView2_android_text, -1);
//        if (textResId != -1) {
//            defaultText =
//        }
        for (int i = 0; i < typedArray.getIndexCount(); i++) {
            int attr = typedArray.getIndex(i);
            if (attr == R.styleable.CustomSimpleTextView2_android_text) {
//                mTextId = typedArray.getResourceId(attr, Resources.ID_NULL);
                CharSequence text = typedArray.getText(attr);
                if (text != null) {
                    defaultText = text.toString();
                }
            }/* else if (attr == R.styleable.CustomSimpleTextView2_android_layout_width) {
                typedArray.getDimension()
            } else if (attr == R.styleable.CustomSimpleTextView2_android_layout_height) {

            }*/
        }
    }

    private void init(Context context) {
        backgroundPaint.setColor(backgroundColor);
        textPaint.setColor(textColor);
        textPaint.setTextAlign(Paint.Align.LEFT);
        TextView textViewReference = new TextView(context);
        textPaint.setTextSize(textSize);
//        textPaint.setFakeBoldText(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawBackground(canvas);
        drawText(canvas);
    }

    private void getTextWidth() {
        int widthMeasureSpec = getMeasuredWidth();
        final int measuredWidth;
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
            measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
        } else {
            //TODO: need to handle splitting to multiple lines
            int textWidth = (int)Math.ceil(textPaint.measureText(defaultText));
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
                measuredWidth = Math.min(textWidth, MeasureSpec.getSize(widthMeasureSpec));
            } else {
                measuredWidth = textWidth;
            }
        }
    }

    private void drawBackground(Canvas canvas) {
        canvas.drawColor(boderColor);
        int left =  /*borderWidthLeft*/getPaddingLeft();
        int top =  /*borderWidthTop*/getPaddingTop();
        int right = /*borderWidthLeft + backgroundRectWidth*/getMeasuredWidth() - getPaddingRight();
        int bottom = /*borderWidthTop + backgroundRectHeight*/getMeasuredHeight() - getPaddingBottom();
        textBgRect.set(left, top, right, bottom);
        canvas.save();
//        canvas.clipRect(textBgRect, Region.Op.REPLACE);
        canvas.drawRect(textBgRect, backgroundPaint);
        canvas.restore();
    }

    private void drawText(Canvas canvas) {
        int bgCenterX = /*borderWidthLeft + backgroundRectWidth / 2*/getPaddingLeft();
        int bgCenterY = /*borderWidthTop + backgroundRectHeight / 2*/getPaddingTop();
        Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
        int textHeight = (int) Math.ceil(fontMetrics.descent - fontMetrics.ascent);
        int x = bgCenterX;
        int y = (int) (bgCenterY/* + textHeight / 2 - fontMetrics.descent*/);
        System.out.println(textHeight);
        System.out.println(y);
        System.out.println(bgCenterY);
        canvas.save();
        canvas.clipRect(textBgRect);
        canvas.drawText(defaultText, /*x*/getPaddingLeft(), /*y*/getPaddingTop() - fontMetrics.ascent, textPaint);
        canvas.restore();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Log.w("CustomTextView2", "onMeasure: widthMeasureSpec: spec=" + widthMeasureSpec
                + ", mode=" + MeasureSpec.getMode(widthMeasureSpec)
                + ", size=" + MeasureSpec.getSize(widthMeasureSpec)
                + ", string=" + MeasureSpec.toString(widthMeasureSpec));
        Log.w("CustomTextView2", "onMeasure: heightMeasureSpec: spec=" + heightMeasureSpec
                + ", mode=" + MeasureSpec.getMode(heightMeasureSpec)
                + ", size=" + MeasureSpec.getSize(heightMeasureSpec)
                + ", string=" + MeasureSpec.toString(heightMeasureSpec));
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        Log.w("CustomTextView2", "onMeasure: layoutParams.width=" + layoutParams.width
                + ", layoutParams.height=" + layoutParams.height);

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//        setMeasuredDimension(backgroundRectWidth + borderWidthLeft + borderWidthRight,
//                backgroundRectHeight + borderWidthTop + borderWidthBottom);


        Log.w("CustomTextView2", "onMeasure: paddingLeft=" + getPaddingLeft());
        final int measuredWidth;
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
            measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
        } else {
            //TODO: need to handle splitting to multiple lines
            int textWidth = (int)Math.ceil(textPaint.measureText(defaultText)) + getPaddingLeft() + getPaddingRight();
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
                measuredWidth = Math.min(textWidth, MeasureSpec.getSize(widthMeasureSpec));
            } else {
                measuredWidth = textWidth;
            }
        }
        final int measuredHeight;
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            measuredHeight = MeasureSpec.getSize(heightMeasureSpec);
        } else {
            Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
            //TODO: need to handle multiple lines
            int textHeight = (int) Math.ceil(fontMetrics.descent - fontMetrics.ascent) + getPaddingTop() + getPaddingBottom();
            if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
                measuredHeight = Math.min(textHeight, MeasureSpec.getSize(heightMeasureSpec));
            } else {
                measuredHeight = textHeight;
            }
        }
        setMeasuredDimension(measuredWidth, measuredHeight);


//        ViewGroup.LayoutParams layoutParams = getLayoutParams();
//        if (layoutParams.width == ViewGroup.LayoutParams.MATCH_PARENT) {
//
//        }
//        layoutParams.height
//
//        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
//        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
//        this.setMeasuredDimension(parentWidth/2, parentHeight);
//        this.setLayoutParams(new ViewGroup.LayoutParams(parentWidth/2,parentHeight));
//        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private int sp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }

}
