package com.android.slate;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

public class Slate extends View {

    static final boolean DEBUG = true;
    static final String TAG = SlateActivity.TAG + "/Slate";

    public static final int FLAG_DEBUG_STROKES = 1;
    public static final int FLAG_DEBUG_INVALIDATES = 1 << 1;

    public static final int MAX_POINTERS = 10;

    private static final boolean BEZIER = false;
    private static final float BEZIER_CONTROL_POINT_DISTANCE=0.25f;
    private static final boolean WALK_PATHS = true;
    private static final float WALK_STEP_PX = 3.0f;

    private static final int SMOOTHING_FILTER_WLEN = 3;
    private static final float SMOOTHING_FILTER_DECAY = 0.75f;

    private static final int FIXED_DIMENSION = 0; // 1024;

    private static final float INVALIDATE_PADDING = 4.0f;

    private float mPressureVariation = 1.5f;
    private float mPressureExponent = 2.0f;

    private float mSizeVariation = 0;
    private float mSizeExponent = 1.0f;

    private float mRadius = 6.0f;

    private int mPenColor;

    private float mDensity = 1.0f;

    private static final float TOUCH_SIZE_RANGE_MIN = 1.0f;
    private static final float TOUCH_SIZE_RANGE_MAX = 70.0f;

    private int mDebugFlags = 0;

    private Bitmap mBitmap;
    private Canvas mCanvas;
    private final Paint mDebugPaints[] = new Paint[4];

    private class StrokeState implements CoordBuffer.Stroker {
        private CoordBuffer mCoordBuffer;
        private float mLastX = 0, mLastY = 0, mLastLen = 0, mLastR = -1;
        private float mTan[] = new float[2];

        private final RectF mRect = new RectF();
        private final Paint mPaint, mStrokePaint;

        private Path mWorkPath = new Path();
        private PathMeasure mWorkPathMeasure = new PathMeasure();

        public StrokeState() {
            mCoordBuffer = new CoordBuffer(SMOOTHING_FILTER_WLEN, SMOOTHING_FILTER_DECAY, this);

            mPaint = new Paint();
            mPaint.setAntiAlias(true);
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setARGB(255, 255, 255, 255);
    //        mPaint.setARGB(255, 0, 255, 0);
            mStrokePaint = new Paint(mPaint);
            mStrokePaint.setStyle(Paint.Style.STROKE);
            mStrokePaint.setStrokeCap(Paint.Cap.ROUND);
            setPenColor(0xFFFFFFFF);

        }

        public void setPenColor(int color) {
            mPenColor = color;
            mPaint.setColor(color);
            mStrokePaint.setColor(color);
        }

        public int getPenColor() {
            return mPenColor;
        }

        public void setDebugMode(boolean debug) {
            setPenColor(getPenColor() & 0xFFFFFF | (debug ? 0x80FFFFFF : 0xFFFFFFFF));
            mPaint.setStyle(debug ? Paint.Style.STROKE : Paint.Style.FILL);
        }

        public void finish() {
            mCoordBuffer.finish();
            reset();
        }

        public void reset() {
            mLastX = mLastY = mTan[0] = mTan[1] = 0;
            mLastR = -1;
        }

        public void addCoords(MotionEvent.PointerCoords pt) {
            mCoordBuffer.add(pt);
        }

        public void drawPoint(float x, float y, float pressure, float width) {
    //        Log.i("TouchPaint", "Drawing: " + x + "x" + y + " p="
    //                + pressure + " width=" + width);
            

            if (width < TOUCH_SIZE_RANGE_MIN) width = TOUCH_SIZE_RANGE_MIN;
            else if (width > TOUCH_SIZE_RANGE_MAX) width = TOUCH_SIZE_RANGE_MAX;
            float widthNorm = (width - TOUCH_SIZE_RANGE_MIN)
                / (TOUCH_SIZE_RANGE_MAX - TOUCH_SIZE_RANGE_MIN);

            float pressureNorm = pressure;

            float r = mRadius * mDensity * (1
                    + (float) (Math.pow(widthNorm, mSizeExponent) - 0.5f) * mSizeVariation
                    + (float) (Math.pow(pressureNorm, mPressureExponent) - 0.5f) * mPressureVariation
                );
//            Log.d(TAG, String.format("(%g, %g): pnorm=%g wnorm=%g rad=%g", x, y, pressureNorm, widthNorm, r));

            if (mBitmap != null) {
                if (!WALK_PATHS || mLastR < 0) {
                    // poke!
                    mCanvas.drawCircle(x, y, r, mPaint);
                }

                mRect.set((x - r - INVALIDATE_PADDING),
                          (y - r - INVALIDATE_PADDING),
                          (x + r + INVALIDATE_PADDING),
                          (y + r + INVALIDATE_PADDING));

                if (mLastR >= 0) {
                    // connect the dots, la-la-la
                    
                    Path p = mWorkPath;
                    p.reset();
                    p.moveTo(mLastX, mLastY);

                    float controlX = mLastX + mTan[0]*BEZIER_CONTROL_POINT_DISTANCE;
                    float controlY = mLastY + mTan[1]*BEZIER_CONTROL_POINT_DISTANCE;

                    if (BEZIER && (mTan[0] != 0 || mTan[1] != 0)) {
                        p.quadTo(controlX, controlY, x, y);
                    } else {
                        p.lineTo(x, y);
                    }

                    if (WALK_PATHS) {
                        PathMeasure pm = mWorkPathMeasure;
                        pm.setPath(p, false);
                        mLastLen = pm.getLength();
                        float d = 0;
                        float posOut[] = new float[2];
                        float ri;
                        while (true) {
                            if (d > mLastLen) {
                                d = mLastLen;
                            }
                            pm.getPosTan(d, posOut, mTan);
                            // denormalize
                            mTan[0] *= mLastLen; mTan[1] *= mLastLen;

                            ri = lerp(mLastR, r, d / mLastLen);
                            mCanvas.drawCircle(posOut[0], posOut[1], ri, mPaint);

                            mRect.union(posOut[0] - ri, posOut[1] - ri, posOut[0] + ri, posOut[1] + ri);
                            
                            if (d == mLastLen) break;
                            d += WALK_STEP_PX;
                        }

                    } else { // STROKE_PATHS
                        // TODO: use a trapezoid from circle tangents
                        mStrokePaint.setStrokeWidth(r+mLastR); // average x 2
        //                mCanvas.drawLine(mLastX, mLastY, x, y, mStrokePaint);

                        mCanvas.drawPath(p, mStrokePaint);

                        mTan[0] = x - controlX;
                        mTan[1] = y - controlY;
        
                        mRect.union((mLastX - mLastR - INVALIDATE_PADDING),
                                    (mLastY - mLastR - INVALIDATE_PADDING));
                        mRect.union((mLastX + mLastR + INVALIDATE_PADDING),
                                    (mLastY + mLastR + INVALIDATE_PADDING));
                    }

                    if ((mDebugFlags & FLAG_DEBUG_STROKES) != 0) {
    //                    mCanvas.drawLine(mLastX, mLastY, controlX, controlY, mDebugPaints[0]);
    //                    mCanvas.drawLine(controlX, controlY, x, y, mDebugPaints[0]);
                        mCanvas.drawLine(mLastX, mLastY, x, y, mDebugPaints[1]);
                        mCanvas.drawPath(p, mDebugPaints[2]);
                        mCanvas.drawPoint(controlX, controlY, mDebugPaints[0]);
                    }
                }

                if ((mDebugFlags & FLAG_DEBUG_INVALIDATES) != 0) {
                    invalidate();
                } else {
                    Rect dirty = new Rect();
                    mRect.roundOut(dirty);
                    invalidate(dirty);
                }
            }

            mLastX = x;
            mLastY = y;
            mLastR = r;
        }
    }

    private StrokeState[] mStrokes = new StrokeState[MAX_POINTERS];

    public Slate(Context c, AttributeSet as) {
        super(c, as);
        for (int i=0; i<mStrokes.length; i++) {
            mStrokes[i] = new StrokeState();
        }

        setFocusable(true);
        
        if (true) {
            mDebugPaints[0] = new Paint();
            mDebugPaints[0].setStyle(Paint.Style.STROKE);
            mDebugPaints[0].setStrokeWidth(2.0f);
            mDebugPaints[0].setARGB(255, 0, 255, 255);
            mDebugPaints[1] = new Paint(mDebugPaints[0]);
            mDebugPaints[1].setARGB(255, 255, 0, 128);
            mDebugPaints[2] = new Paint(mDebugPaints[0]);
            mDebugPaints[2].setARGB(255, 0, 255, 0);
            mDebugPaints[3] = new Paint(mDebugPaints[0]);
            mDebugPaints[3].setARGB(255, 30, 30, 255);
        }
    }

    public void clear() {
        if (mCanvas != null) {
            mCanvas.drawColor(0x00000000, PorterDuff.Mode.SRC);
            invalidate();
        }
    }

    public int getDebugFlags() { return mDebugFlags; }
    public void setDebugFlags(int f) {
        if (f != mDebugFlags) {
            for (StrokeState stroke : mStrokes) {
                stroke.setDebugMode((f & FLAG_DEBUG_STROKES) != 0);
            }
        }

        mDebugFlags = f;
    }

    public void paintBitmap(Bitmap b) {
        Matrix m = new Matrix();
        RectF s = new RectF(0, 0, b.getWidth(), b.getHeight());
        RectF d = new RectF(0, 0, mBitmap.getWidth(), mBitmap.getHeight());
        m.setRectToRect(s, d, Matrix.ScaleToFit.CENTER);
        mCanvas.drawBitmap(b, m, null);
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public void setBitmap(Bitmap b) {
        if (b == null) return;

        Bitmap newBitmap = Bitmap.createBitmap(b.getWidth(), b.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas newCanvas = new Canvas();
        newCanvas.setBitmap(newBitmap);
        newCanvas.drawBitmap(b, 0, 0, null);
        mBitmap = newBitmap;
        mCanvas = newCanvas;

    }

    public void setPenColor(int color) {
        for (StrokeState stroke : mStrokes) {
            // XXX: todo: only do this if the stroke hasn't begun already
            stroke.setPenColor(color);
        }
    }

    public void setDensity(float d) {
        mDensity = d;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw,
            int oldh) {

        int curW = mBitmap != null ? mBitmap.getWidth() : 0;
        int curH = mBitmap != null ? mBitmap.getHeight() : 0;
        if (curW >= w && curH >= h) {
            return;
        }

        if (FIXED_DIMENSION > 0) {
            curW = curH = FIXED_DIMENSION;
        } else {
            if (curW < w) curW = w;
            if (curH < h) curH = h;
        }

        Bitmap newBitmap = Bitmap.createBitmap(curW, curH,
//                Bitmap.Config.RGB_565);
                Bitmap.Config.ARGB_8888);
        Log.d(TAG, "new size: " + w + "x" + h);
        Log.d(TAG, "old bitmap: " + mBitmap);
        Log.d(TAG, "created bitmap " + curW + "x" + curH + ": " + newBitmap);
        Canvas newCanvas = new Canvas();
        newCanvas.setBitmap(newBitmap);
        if (mBitmap != null) {
            // copy the old bitmap in? doesn't seem to work
            newCanvas.drawBitmap(mBitmap, 0, 0, null);
        }
        mBitmap = newBitmap;
        mCanvas = newCanvas;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mBitmap != null) {
            canvas.drawBitmap(mBitmap, 0, 0, null);
        }
    }

    float dbgX = -1, dbgY = -1;
    RectF dbgRect = new RectF();
    MotionEvent.PointerCoords mTmpCoords = new MotionEvent.PointerCoords();
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int N = event.getHistorySize();
        int P = event.getPointerCount();

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int j = event.getActionIndex();
            event.getPointerCoords(j, mTmpCoords);
            mStrokes[event.getPointerId(j)].addCoords(mTmpCoords);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            int j = event.getActionIndex();
            event.getPointerCoords(j, mTmpCoords);
            mStrokes[event.getPointerId(j)].addCoords(mTmpCoords);
            mStrokes[event.getPointerId(j)].finish();
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (dbgX >= 0) {
                dbgRect.set(dbgX-1,dbgY-1,dbgX+1,dbgY+1);
            }

            for (int i = 0; i < N; i++) {
                for (int j = 0; j < P; j++) {
                    event.getHistoricalPointerCoords(j, i, mTmpCoords);
                    if ((mDebugFlags & FLAG_DEBUG_STROKES) != 0) {
                        if (dbgX >= 0) {
                            mCanvas.drawLine(dbgX, dbgY, mTmpCoords.x, mTmpCoords.y, mDebugPaints[3]);
                        }
                        dbgX = mTmpCoords.x;
                        dbgY = mTmpCoords.y;
                        dbgRect.union(dbgX-1, dbgY-1, dbgX+1, dbgY+1);
                    }
                    mStrokes[event.getPointerId(j)].addCoords(mTmpCoords);
                }
            }
            for (int j = 0; j < P; j++) {
                event.getPointerCoords(j, mTmpCoords);
                if ((mDebugFlags & FLAG_DEBUG_STROKES) != 0) {
                    if (dbgX >= 0) {
                        mCanvas.drawLine(dbgX, dbgY, mTmpCoords.x, mTmpCoords.y, mDebugPaints[3]);
                    }
                    dbgX = mTmpCoords.x;
                    dbgY = mTmpCoords.y;
                    dbgRect.union(dbgX-1, dbgY-1, dbgX+1, dbgY+1);
                }
                mStrokes[event.getPointerId(j)].addCoords(mTmpCoords);
            }

            if ((mDebugFlags & FLAG_DEBUG_STROKES) != 0) {
                Rect dirty = new Rect();
                dbgRect.roundOut(dirty);
                invalidate(dirty);
            }
        }
        
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            for (int j = 0; j < P; j++) {
                mStrokes[event.getPointerId(j)].finish();
            }
            dbgX = dbgY = -1;
        }
        return true;
    }

    private static float lerp(float a, float b, float f) {
        return a + f * (b - a);
    }
}

