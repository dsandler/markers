/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.google.android.apps.markers;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;

import org.dsandler.apps.markers.R;

public class ToolButton extends View {
    public static class ToolCallback {
        public void setZoomMode(ToolButton me) {}
        public void setPenMode(ToolButton me, float min, float max) {}
        public void setPenColor(ToolButton me, int color) {}
        public void setBackgroundColor(ToolButton me, int color) {}
        public void restore(ToolButton me) {}
        public void setPenType(ToolButton penTypeButton, int penType) {}
    }

    // Enable a delay here to use "shifted" mode, where longpressing a tool will only assert that
    // tool until you lift your finger
    //    private static final long PERMANENT_TOOL_SWITCH_THRESHOLD = 300; // ms
    private static final long PERMANENT_TOOL_SWITCH_THRESHOLD = 0; // ms
    
    private ToolCallback mCallback;
    private long mDownTime;
    
    protected Paint mPaint;
    protected ColorStateList mFgColor, mBgColor;

    private Runnable mLongPressHandler = new Runnable() {
        @Override
        public void run() {
            if (Slate.DEBUG) {
                Log.d(Slate.TAG, "longpress on " + ToolButton.this + " pressed=" + isPressed());
            }
            if (isPressed()) {
                if (onLongClick(ToolButton.this)) {
                    deactivate();
                    invalidate();
                }
            }
        }
    };

    public ToolButton(Context context) {
        super(context);
    }

    public ToolButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFgColor = getResources().getColorStateList(R.color.pentool_fg);
        mBgColor = getResources().getColorStateList(R.color.pentool_bg);
    }
    
    public static class PenToolButton extends ToolButton {
        public float strokeWidthMin, strokeWidthMax;

        public PenToolButton(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);

            TypedArray a = context.obtainStyledAttributes(attrs, 
                    R.styleable.PenToolButton, defStyle, 0);
            
            strokeWidthMin = a.getDimension(R.styleable.PenToolButton_strokeWidthMin, 1);
            strokeWidthMax = a.getDimension(R.styleable.PenToolButton_strokeWidthMax, 10);
            
            a.recycle();
        }
        
        public PenToolButton(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }
        
        @Override
        void activate() {
            super.activate();
            final ToolCallback cb = getCallback();
            if (cb != null) cb.setPenMode(this, strokeWidthMin, strokeWidthMax);
        }
        
        @Override
        public void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            
            mPaint.setColor(mFgColor.getColorForState(getDrawableState(), mFgColor.getDefaultColor()));
            
            float r1 = strokeWidthMin * 0.5f;
            float r2 = strokeWidthMax * 0.5f;
            
            final boolean vertical = getHeight() > getWidth();
            final float start = (vertical ? getPaddingTop() : getPaddingLeft()) + r1;
            final float end = (vertical ? (getHeight() - getPaddingBottom()) : (getWidth() - getPaddingRight())) - r2;
            final float center = (vertical ? getWidth() : getHeight()) / 2;
            final float iter = 1f / (vertical ? getHeight() : getWidth());
            final float amplitude = (center-r2)*0.5f;

            if (r1 > center) r1 = center;
            if (r2 > center) r2 = center;

            for (float f = 0f; f < 1.0f; f += iter) {
                final float y = Slate.lerp(start, end, f);
                final float x = (float) (center + amplitude*Math.sin(f * 2*Math.PI));
                final float r = Slate.lerp(r1, r2, f);
                canvas.drawCircle(vertical ? x : y, vertical ? y : x, r, mPaint);
            }
            canvas.drawCircle(vertical ? center : end, vertical ? end : center, r2, mPaint);
        }
    }

    protected boolean onLongClick(View v) {
        // do nothing
        return false;
    }

    public static class PenTypeButton extends ToolButton {
        public int penType;
        public Bitmap icon;
        public Rect frame;

        public PenTypeButton(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);

            TypedArray a = context.obtainStyledAttributes(attrs, 
                    R.styleable.PenTypeButton, defStyle, 0);
            
            penType = a.getInt(R.styleable.PenTypeButton_penType, 0);
            
            a.recycle();
        }
        
        public PenTypeButton(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }
        
        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (penType == Slate.TYPE_AIRBRUSH) {
                icon = BitmapFactory.decodeResource(getResources(), R.drawable.airbrush_dark);
                if (icon == null) {
                    throw new RuntimeException("PenTypeButton: could not load airbrush bitmap");
                }
                frame = new Rect(0, 0, icon.getWidth(), icon.getHeight());
            } else if (penType == Slate.TYPE_FOUNTAIN_PEN) {
                icon = BitmapFactory.decodeResource(getResources(), R.drawable.fountainpen);
                if (icon == null) {
                    throw new RuntimeException("PenTypeButton: could not load fountainpen bitmap");
                }
                frame = new Rect(0, 0, icon.getWidth(), icon.getHeight());
            }
        }
        
        @Override
        void activate() {
            super.activate();
            final ToolCallback cb = getCallback();
            if (cb != null) cb.setPenType(this, penType);
        }

        private RectF tmpRF = new RectF();
        @Override
        public void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (mPaint == null) return;

            float x = 0.5f*getWidth();
            float y = 0.5f*getHeight();
            float r = Math.min(getWidth()-getPaddingLeft()-getPaddingRight(),
                             getHeight()-getPaddingTop()-getPaddingBottom()) * 0.5f;


            int color = mFgColor.getColorForState(getDrawableState(), mFgColor.getDefaultColor());
            mPaint.setColor(color);
            mPaint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)); // SRC_IN ??
            tmpRF.set(x-r,y-r,x+r,y+r);

            switch (penType) {
                case Slate.TYPE_FELTTIP:
                    mPaint.setAlpha(0x80);
                    canvas.drawCircle(x, y, r, mPaint);
                    break;
                case Slate.TYPE_AIRBRUSH:
                case Slate.TYPE_FOUNTAIN_PEN:
                    mPaint.setAlpha(0xFF);
                    if (icon != null) {
                        canvas.drawBitmap(icon, frame, tmpRF, mPaint);
                    }
                    break;
                case Slate.TYPE_WHITEBOARD:
                default:
                    mPaint.setAlpha(0xFF);
                    canvas.drawCircle(x, y, r, mPaint);
                    break;
            }
        }
    }

    public static class SwatchButton extends ToolButton {
        public int color;
        private Drawable mTransparentTile;
        
        public SwatchButton(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);

            TypedArray a = context.obtainStyledAttributes(attrs, 
                    R.styleable.SwatchButton, defStyle, 0);
            
            color = a.getColor(R.styleable.SwatchButton_color, 0xFFFFFF00);

            a.recycle();
        }
        
        public SwatchButton(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }
        
        @Override
        void activate() {
            super.activate();
            final ToolCallback cb = getCallback();
            if (cb != null) cb.setPenColor(this, color);
        }
        final int HIGHLIGHT_STROKE_COLOR = 0xFFFFFFFF;
        final int HIGHLIGHT_STROKE_COLOR_ALT = 0xFFC0C0C0;
        
        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            final Resources res = getResources();
            mTransparentTile = res.getDrawable(R.drawable.transparent_tool);
        }
        
        @Override
        public void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (mPaint == null) return;

            int p = this.getPaddingLeft();
            if ((color & 0xFF000000) == 0) { // transparent
                mTransparentTile.setBounds(canvas.getClipBounds());
                mTransparentTile.draw(canvas);
            } else {
                canvas.drawColor(color);
            }
            if (isSelected() || isPressed()) {
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(p);
                mPaint.setColor(color == HIGHLIGHT_STROKE_COLOR 
                        ? HIGHLIGHT_STROKE_COLOR_ALT 
                        : HIGHLIGHT_STROKE_COLOR);
                p /= 2;
                canvas.drawRect(p, p, getWidth()-p, getHeight()-p, mPaint);
            }
        }

        @Override
        protected boolean onLongClick(View v) {
            final ToolCallback cb = getCallback();
            if (cb != null) cb.setBackgroundColor(this, color);
            return true;
        }
    }

    public static class ZoomToolButton extends ToolButton {
        public ZoomToolButton(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }
        
        public ZoomToolButton(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }
        
        @Override
        void activate() {
            super.activate();
            final ToolCallback cb = getCallback();
            if (cb != null) cb.setZoomMode(this);
        }
    }

    public ToolButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setClickable(true);
    }
    
    void setCallback(ToolCallback cb) {
        mCallback = cb;
    }
    
    ToolCallback getCallback() {
        return mCallback;
    }
    
    public void click() {
        activate();
        commit();
    }
    
    void activate() {
        // pass
    }
    
    void deactivate() {
        setSelected(false);
        setPressed(false);
    }
    
    void commit() {
        setPressed(false);
        setSelected(true);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getAction();
        
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (Slate.DEBUG) Log.d(Slate.TAG, "DOWN on " + ToolButton.this + " lph=" + mLongPressHandler);
                postDelayed(mLongPressHandler, ViewConfiguration.getLongPressTimeout());
                mDownTime = event.getEventTime();
                setPressed(true);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (isPressed()) {
                    if (!isSelected()) {
                        activate();
                        commit();
                    }
                    invalidate();
                }
                removeCallbacks(mLongPressHandler);
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(mLongPressHandler);
                return true;
        }
        return false;
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(mBgColor.getColorForState(getDrawableState(), mBgColor.getDefaultColor()));
    }
}
