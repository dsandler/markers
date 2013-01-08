package com.google.android.apps.markers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class ZoomTouchView extends View {
    public static final String TAG = Slate.TAG;

    public static final boolean DEBUG = true;
    public static final boolean DEBUG_OVERLAY = DEBUG;

    private Slate mSlate;
    private float[] mTouchPoint = new float[2]; // screen coordinates
    private float[] mTouchPointDoc = new float[2]; // doc coordinates
    private double mInitialSpan = 1.0f;

    private float[] mInitialPos = new float[2];

    private Matrix mInitialZoomMatrix = new Matrix();
    private Paint mZoomPaint;

    public ZoomTouchView(Context context) {
        this(context, null);
    }

    public ZoomTouchView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomTouchView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        
        mZoomPaint = new Paint();
        mZoomPaint.setTextSize(25f);
    }

    float[] getCenter(MotionEvent event, float[] pt) {
        int P = event.getPointerCount();
        pt = ((pt == null) ? new float[2] : pt);
        pt[0] = event.getX(0);
        pt[1] = event.getY(0);
//        final int zero[] = { 0, 0 };
//        getLocationOnScreen(zero);
        for (int j = 1; j < P; j++) {
            pt[0] += event.getX(j); // + zero[0];
            pt[1] += event.getY(j); // + zero[1];
        }
        pt[0] /= P;
        pt[1] /= P;
        return pt;
    }
    double getSpan(MotionEvent event) {
        int P = event.getPointerCount();
        if (P < 2) return 0;

        final double x0 = event.getX(0); // + zero[0];
        final double x1 = event.getX(1); // + zero[0];
        final double y0 = event.getY(0); // + zero[1];
        final double y1 = event.getY(1); // + zero[1];
        final double span = Math.hypot(x1 - x0, y1 - y0);

        return span; 
    }

    @SuppressLint("NewApi")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        if (isEnabled()) {
            //final Matrix currentM = mSlate.getMatrix();

            if (action == MotionEvent.ACTION_DOWN
                    || action == MotionEvent.ACTION_POINTER_DOWN
                    || action == MotionEvent.ACTION_POINTER_UP
                    || action == MotionEvent.ACTION_UP) {
                mTouchPoint[0] = mTouchPoint[1] = -1f;
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (mTouchPoint[0] < 0) {
                    mInitialZoomMatrix.set(mSlate.getZoom());
                    mSlate.getZoomPos(mInitialPos);

                    mInitialSpan = getSpan(event);
                    getCenter(event, mTouchPoint);
                    mTouchPointDoc[0] = mTouchPoint[0] - mSlate.getZoomPosX();
                    mTouchPointDoc[1] = mTouchPoint[1] - mSlate.getZoomPosY();
                    mSlate.getZoomInv().mapPoints(mTouchPointDoc);
                }
                if (mInitialSpan != 0) {
                    double span = getSpan(event);
                    float scale = (float) (span / mInitialSpan);

                    if (scale != 0f) {
                        Matrix m = new Matrix(mInitialZoomMatrix);

                        m.preScale(scale, scale, mTouchPointDoc[0], mTouchPointDoc[1]);
                        mSlate.setZoom(m);
                    }
                }

                float[] newCenter = getCenter(event, null);
                mSlate.setZoomPos(mInitialPos[0] + newCenter[0] - mTouchPoint[0],
                        mInitialPos[1] + newCenter[1] - mTouchPoint[1]);
            }
            if (DEBUG_OVERLAY) invalidate();
            return true;
        }
        return false;
    }

    public void setSlate(Slate slate) {
        mSlate = slate;
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!isEnabled()) return;
        
        final Matrix m = mSlate.getZoom();
        final int x = (int) mSlate.getZoomPosX();
        final int y = (int) mSlate.getZoomPosY();

        final float scale = m.mapRadius(1f);
        canvas.drawText(String.format("%d%% %+d,%+d",
                            (int)(scale * 100f),
                            x,
                            y),
                canvas.getWidth() - 200, canvas.getHeight() - 20, mZoomPaint);
        
        if (!DEBUG_OVERLAY) return;

        setVisibility(View.VISIBLE);
        //canvas.drawColor(0xFFFFFF00);
        setAlpha(0.5f);
        Paint pt = new Paint();
        pt.setFlags(Paint.ANTI_ALIAS_FLAG);
        pt.setTextSize(20f);
        pt.setColor(0x80FF0000);
        if (mTouchPoint[0] != 0)
        canvas.drawCircle(mTouchPoint[0], mTouchPoint[1], 50 * (float) mSlate.getScaleX(), pt);
    }
}
