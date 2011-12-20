package com.google.android.markersbeta;

import java.lang.ref.WeakReference;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

public class ToolButton extends View {
    public static class ToolCallback {
        public void setZoomMode(ToolButton me) {}
        public void setPenMode(ToolButton me, float min, float max) {}
        public void setPenColor(ToolButton me, int color) {}
        public void restore(ToolButton me) {}
    }

    private static final long PERMANENT_TOOL_SWITCH_THRESHOLD = 300; // ms
    
    private ToolCallback mCallback;
    private long mDownTime;
    
    public ToolButton(Context context) {
        super(context);
    }

    public ToolButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public static class PenToolButton extends ToolButton {
        public float strokeWidthMin, strokeWidthMax;

        public PenToolButton(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);

            TypedArray a = context.obtainStyledAttributes(attrs, 
                    R.styleable.PenToolButton, defStyle, 0);
            
            strokeWidthMin = a.getDimension(R.styleable.PenToolButton_strokeWidthMin, -1);
            strokeWidthMax = a.getDimension(R.styleable.PenToolButton_strokeWidthMax, -1);
            
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
            
            final Resources res = getResources();
            final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
            ColorStateList fg = res.getColorStateList(R.color.pentool_fg);
            pt.setColor(fg.getColorForState(getDrawableState(), fg.getDefaultColor()));
            
            final float r1 = strokeWidthMin * 0.5f;
            final float r2 = strokeWidthMax * 0.5f;
            final boolean vertical = getHeight() > getWidth();
            final float start = (vertical ? getPaddingTop() : getPaddingLeft()) + r1;
            final float end = (vertical ? (getHeight() - getPaddingBottom()) : (getWidth() - getPaddingRight())) - r2;
            final float center = (vertical ? getWidth() : getHeight()) / 2;
            final float iter = 1f / (vertical ? getHeight() : getWidth());
            final float amplitude = (center-r2)*0.5f;
            for (float f = 0f; f < 1.0f; f += iter) {
                final float y = com.android.slate.Slate.lerp(start, end, f);
                final float x = (float) (center + amplitude*Math.sin(f * 2*Math.PI));
                final float r = com.android.slate.Slate.lerp(r1, r2, f);
                canvas.drawCircle(vertical ? x : y, vertical ? y : x, r, pt);
            }
            canvas.drawCircle(vertical ? center : end, vertical ? end : center, r2, pt);
        }
    }
    
    public static class SwatchButton extends ToolButton {
        public int color;
        
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
        
        @Override
        public void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final int p = this.getPaddingLeft();
            if (isSelected() || isPressed()) {
                canvas.clipRect(p, p, canvas.getWidth()-p, canvas.getHeight() - p);
            }
            if ((color & 0xFF000000) == 0) { // transparent
                final Resources res = getResources();
                final Drawable tile = res.getDrawable(R.drawable.transparent_tool);
                tile.setBounds(canvas.getClipBounds());
                tile.draw(canvas);
            } else {
                canvas.drawColor(color);
            }
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
                if (!isSelected()) {
                    mDownTime = event.getEventTime();
                    setPressed(true);
                    activate();
                    invalidate();
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    return true;
                } else {
                    // FALL THROUGH.
                    // Split touch events only appeared in Honeycomb; before this we
                    // want to simply switch the tool on DOWN, end of story.
                }
            case MotionEvent.ACTION_UP:
                if (isPressed()) {
                    setPressed(false);
                    if (event.getEventTime() - mDownTime > PERMANENT_TOOL_SWITCH_THRESHOLD) {
                        deactivate();
                        final ToolCallback cb = mCallback;
                        if (cb != null) cb.restore(this);
                    } else {
                        commit();
                    }
                    invalidate();
                }
                return true;
        }
        return false;
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        final Resources res = getResources();
        ColorStateList bg = res.getColorStateList(R.color.pentool_bg);
        canvas.drawColor(bg.getColorForState(getDrawableState(), bg.getDefaultColor()));
    }
}
