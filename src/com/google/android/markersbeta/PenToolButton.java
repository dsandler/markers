package com.google.android.markersbeta;

import java.lang.ref.WeakReference;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

public class PenToolButton extends View {
    public interface PenToolCallback {
        public void setPenSize(float min, float max);
        public void restorePenSize();
    }

    private static final long PERMANENT_TOOL_SWITCH_THRESHOLD = 300; // ms
    
    public float strokeWidthMin, strokeWidthMax;
    private PenToolCallback mCallback;
    private long mDownTime;
    
    public PenToolButton(Context context) {
        super(context);
    }

    public PenToolButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PenToolButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs, 
                R.styleable.PenToolButton, defStyle, 0);
        
        strokeWidthMin = a.getDimension(R.styleable.PenToolButton_strokeWidthMin, -1);
        strokeWidthMax = a.getDimension(R.styleable.PenToolButton_strokeWidthMax, -1);
        
        setClickable(true);
        
        a.recycle();
    }
    
    void setCallback(PenToolCallback cb) {
        mCallback = cb;
    }
    
    void select() {
        final PenToolCallback cb = mCallback;
        if (cb != null) cb.setPenSize(strokeWidthMin, strokeWidthMax);
        setSelected(true);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getAction();
        
        final PenToolCallback cb = mCallback;
        if (cb == null) return false;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (!isSelected()) {
                    mDownTime = event.getEventTime();
                    cb.setPenSize(strokeWidthMin, strokeWidthMax);
                    setPressed(true);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (isPressed()) {
                    setPressed(false);
                    invalidate();
                    if (event.getEventTime() - mDownTime > PERMANENT_TOOL_SWITCH_THRESHOLD) {
                        cb.restorePenSize();
                        setSelected(false);
                    } else {
                        setSelected(true);
                    }
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
        
        final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        ColorStateList fg = res.getColorStateList(R.color.pentool_fg);
        pt.setColor(fg.getColorForState(getDrawableState(), fg.getDefaultColor()));
        
        final float r1 = strokeWidthMin * 0.5f;
        final float r2 = strokeWidthMax * 0.5f;
        final float start = getPaddingTop() + r1;
        final float end = getHeight() - getPaddingBottom() - r2;
        final float center = getWidth() / 2;
        final float iter = 1f / getHeight();
        for (float f = 0f; f < 1.0f; f += iter) {
            final float y = com.android.slate.Slate.lerp(start, end, f);
            final float x = (float) (center + 0.5*center*Math.sin(f * 2*Math.PI));
            final float r = com.android.slate.Slate.lerp(r1, r2, f);
            canvas.drawCircle(x, y, r, pt);
        }
        canvas.drawCircle(center, end, r2, pt);
    }
}
