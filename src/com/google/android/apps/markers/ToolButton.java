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

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;

import android.widget.FrameLayout;
import org.dsandler.apps.markers.R;

public class ToolButton extends View implements View.OnLongClickListener, View.OnClickListener {
    public static class ToolCallback {
        public void setZoomMode(ToolButton me) {}
        public void setPenMode(ToolButton me, float min, float max) {}
        public void setPenColor(ToolButton me, int color) {}
        public void setBackgroundColor(ToolButton me, int color) {}
        public void restore(ToolButton me) {}
        public void setPenType(ToolButton penTypeButton, int penType) {}
        public void resetZoom(ToolButton zoomToolButton) { }
    }

    private static boolean EPHEMERAL_TOOLS = false;
    
    private ToolCallback mCallback;
    private long mDownTime;
    
    protected Paint mPaint;
    protected ColorStateList mFgColor, mBgColor;
    SharedPreferences mPrefs;

    public ToolButton(Context context) {
        this(context, null, 0);
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
        private static final String PREF_STROKE_MIN = ":min";
        private static final String PREF_STROKE_MAX = ":max";

        public float strokeWidthMin, strokeWidthMax;

        public PenToolButton(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);

            if (isInEditMode()) return;

            TypedArray a = context.obtainStyledAttributes(attrs,
                    R.styleable.PenToolButton, defStyle, 0);

            final float min = mPrefs.getFloat(getId() + PREF_STROKE_MIN,
                        a.getDimension(R.styleable.PenToolButton_strokeWidthMin, 1));
            final float max = mPrefs.getFloat(getId() + PREF_STROKE_MAX,
                        a.getDimension(R.styleable.PenToolButton_strokeWidthMax, 10));

            setWidths(min, max);

            a.recycle();
        }
        
        public PenToolButton(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public void setWidths(float min, float max) {
            if (min != strokeWidthMin || max != strokeWidthMax) {
                strokeWidthMin = min;
                strokeWidthMax = max;
                invalidate();
                if (isSelected()) {
                    // assume activated
                    activate();
                }
                SharedPreferences.Editor edit = mPrefs.edit();
                edit.putFloat(getId() + PREF_STROKE_MIN, min);
                edit.putFloat(getId() + PREF_STROKE_MAX, max);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                    edit.apply();
                } else {
                    edit.commit(); // I guess we can wait for it.
                }
            }
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
            final boolean vertical = getHeight() > getWidth();

            float r1 = strokeWidthMin * 0.5f;
            float r2 = strokeWidthMax * 0.5f;

            final float center = (vertical ? getWidth() : getHeight()) / 2;

            if (r1 > center) r1 = center;
            if (r2 > center) r2 = center;

            final float start = (vertical ? getPaddingTop() : getPaddingLeft()) + r1;
            final float end = (vertical ? (getHeight() - getPaddingBottom()) : (getWidth() - getPaddingRight())) - r2;
            final float iter = 1f / (vertical ? getHeight() : getWidth());
            final float amplitude = (center-r2)*0.5f;

            for (float f = 0f; f < 1.0f; f += iter) {
                final float y = Slate.lerp(start, end, f);
                final float x = (float) (center + amplitude*Math.sin(f * 2*Math.PI));
                final float r = Slate.lerp(r1, r2, f);
                canvas.drawCircle(vertical ? x : y, vertical ? y : x, r, mPaint);
            }
            canvas.drawCircle(vertical ? center : end, vertical ? end : center, r2, mPaint);

            if (r2 == center) {
                mPaint.setColor(Color.WHITE);
                mPaint.setTextAlign(Paint.Align.CENTER);
                mPaint.setTextSize(r2/2);
                final String maxStr = String.format("%.0f", strokeWidthMax);
                canvas.drawText(maxStr, vertical ? center : end, -5 + r2/4 + (vertical ? end : center), mPaint);
            }
        }
        
        @Override
        public boolean onLongClick(View view) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            final View layout =
                    inflate(getContext(), R.layout.pen_editor, null);
            builder.setView(layout);
            final PenWidthEditorView editor = (PenWidthEditorView) layout.findViewById(R.id.editor);
            editor.setTool((PenToolButton) view);
            AlertDialog dlg = builder.create();
            dlg.show();
            return true;
        }
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
        public boolean onLongClick(View v) {
            final ToolCallback cb = getCallback();
            if (cb != null) cb.setBackgroundColor(this, color);
            return true;
        }
    }

    public static class ZoomToolButton extends ToolButton {
        public Bitmap icon;
        public Rect frame;
        public final RectF tmpRF = new RectF();

        public ZoomToolButton(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
            
            icon = BitmapFactory.decodeResource(getResources(), R.drawable.grabber);
            frame = new Rect(0, 0, icon.getWidth(), icon.getHeight());
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

            canvas.drawBitmap(icon, frame, tmpRF, mPaint);
        }

        @Override
        public boolean onLongClick(View v) {
            final ToolCallback cb = getCallback();
            if (cb != null) cb.resetZoom(this);
            return true;
        }
    }

    public ToolButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mPrefs = context.getSharedPreferences("ToolButton", Context.MODE_PRIVATE);

        setClickable(true);
        setOnClickListener(this);
        setOnLongClickListener(this);
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
    public void onClick(View view) {
        activate();
        commit();
    }

    @Override
    public boolean onLongClick(View view) {
        return false;
    }

    // the following overrides are in place until I fix up the backgrounds to just use statelist drawables
    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        invalidate();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(mBgColor.getColorForState(getDrawableState(), mBgColor.getDefaultColor()));
    }
}
