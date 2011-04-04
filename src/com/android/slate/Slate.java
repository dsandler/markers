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
import android.os.Build;
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
    public static final int FLAG_DEBUG_PRESSURE = 1 << 1;
    public static final int FLAG_DEBUG_INVALIDATES = 1 << 2;
    public static final int FLAG_DEBUG_EVERYTHING = ~0;

    public static final int MAX_POINTERS = 10;

    private static final boolean BEZIER = false;
    private static final float BEZIER_CONTROL_POINT_DISTANCE=0.25f;
    private static final boolean WALK_PATHS = true;
    private static final float WALK_STEP_PX = 3.0f;

    private static final int SMOOTHING_FILTER_WLEN = 4;
    private static final float SMOOTHING_FILTER_DECAY = 0.75f;

    private static final int FIXED_DIMENSION = 0; // 1024;

    private static final float INVALIDATE_PADDING = 4.0f;

    private float mPressureExponent = 2.0f;

    private float mPressureMin = 0;
    private float mPressureMax = 1;

    public static final float PRESSURE_UPDATE_DECAY = 0.1f;
    public static final int PRESSURE_UPDATE_STEPS = 500; // points
    private float mPressureRecentMin = 1;
    private float mPressureRecentMax = 0;
    private int mPressureUpdateCountdown = PRESSURE_UPDATE_STEPS;

    private float mRadiusMin;
    private float mRadiusMax;

    private float mLastPressure;

    private int mPenColor;

    private float mDensity = 1.0f;

    private int mDebugFlags = 0;

    private Bitmap mDrawingBitmap, mStrokeBitmap;
    private Canvas mDrawingCanvas, mStrokeCanvas;
    private final Paint mDebugPaints[] = new Paint[10];

    private class StrokeState implements SpotFilter.Stroker {
        private SpotFilter mCoordBuffer;
        private float mLastX = 0, mLastY = 0, mLastLen = 0, mLastR = -1;
        private float mTan[] = new float[2];

        private final RectF mRect = new RectF();
        private final Paint mPaint, mStrokePaint;

        private Path mWorkPath = new Path();
        private PathMeasure mWorkPathMeasure = new PathMeasure();

        public StrokeState() {
            mCoordBuffer = new SpotFilter(SMOOTHING_FILTER_WLEN, SMOOTHING_FILTER_DECAY, this);

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
            invalidate();
        }

        public void finish() {
            mCoordBuffer.finish();
            reset();
        }

        public void reset() {
            mLastX = mLastY = mTan[0] = mTan[1] = 0;
            mLastR = -1;
        }

        public void addCoords(MotionEvent.PointerCoords pt, long time) {
            mCoordBuffer.add(pt, time);
        }
        
        public void add(Spot s) {
        	mCoordBuffer.add(s);
        }

        public void drawPoint(float x, float y, float pressure, float width, long time) {
    //        Log.i("TouchPaint", "Drawing: " + x + "x" + y + " p="
    //                + pressure + " width=" + width);
            mLastPressure = pressure;
            if (pressure < mPressureRecentMin) mPressureRecentMin = pressure;
            if (pressure > mPressureRecentMax) mPressureRecentMax = pressure;
            
            if (--mPressureUpdateCountdown == 0) {
                final float decay = PRESSURE_UPDATE_DECAY;
                mPressureMin = (1-decay) * mPressureMin + decay * mPressureRecentMin;
                mPressureMax = (1-decay) * mPressureMax + decay * mPressureRecentMax;
                mPressureUpdateCountdown = PRESSURE_UPDATE_STEPS;
                mPressureRecentMin = 1;
                mPressureRecentMax = 0;
            }

            final float pressureNorm = (pressure - mPressureMin)
                / (mPressureMax - mPressureMin);

            final float r = lerp(mRadiusMin, mRadiusMax,
                   (float) Math.pow(pressureNorm, mPressureExponent));
            if (false && 0 != (mDebugFlags)) {
                Log.d(TAG, String.format("(%.1f, %.1f): pressure=%.2f range=%.2f-%.2f obs=%.2f-%.2f pnorm=%.2f rad=%.2f",
                    x, y, pressure, mPressureMin, mPressureMax, mPressureRecentMin, mPressureRecentMax, pressureNorm, r));
            }

            if (mStrokeBitmap != null) {
                if (!WALK_PATHS || mLastR < 0) {
                    // poke!
                    mStrokeCanvas.drawCircle(x, y, r, mPaint);
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
                            mStrokeCanvas.drawCircle(posOut[0], posOut[1], ri, mPaint);

                            mRect.union(posOut[0] - ri, posOut[1] - ri, posOut[0] + ri, posOut[1] + ri);
                            
                            if (d == mLastLen) break;
                            d += WALK_STEP_PX;
                        }

                    } else { // STROKE_PATHS
                        // TODO: use a trapezoid from circle tangents
                        mStrokePaint.setStrokeWidth(r+mLastR); // average x 2
        //                mStrokeCanvas.drawLine(mLastX, mLastY, x, y, mStrokePaint);

                        mStrokeCanvas.drawPath(p, mStrokePaint);

                        mTan[0] = x - controlX;
                        mTan[1] = y - controlY;
        
                        mRect.union((mLastX - mLastR - INVALIDATE_PADDING),
                                    (mLastY - mLastR - INVALIDATE_PADDING));
                        mRect.union((mLastX + mLastR + INVALIDATE_PADDING),
                                    (mLastY + mLastR + INVALIDATE_PADDING));
                    }

                    if ((mDebugFlags & FLAG_DEBUG_STROKES) != 0) {
    //                    mStrokeCanvas.drawLine(mLastX, mLastY, controlX, controlY, mDebugPaints[0]);
    //                    mStrokeCanvas.drawLine(controlX, controlY, x, y, mDebugPaints[0]);
                        mStrokeCanvas.drawLine(mLastX, mLastY, x, y, mDebugPaints[1]);
                        mStrokeCanvas.drawPath(p, mDebugPaints[2]);
                        mStrokeCanvas.drawPoint(controlX, controlY, mDebugPaints[0]);
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
        
        public float getRadius() {
            return mLastR;
        }
    }

    private StrokeState[] mStrokes = new StrokeState[MAX_POINTERS];

    Spot mTmpSpot = new Spot();
    
    public Slate(Context c, AttributeSet as) {
        super(c, as);
        init();
    }
    
    public Slate(Context c) {
    	super(c);
    	init();
    }
    
    private void init() {
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
            mDebugPaints[4] = new Paint();
            mDebugPaints[4].setStyle(Paint.Style.FILL);
            mDebugPaints[4].setARGB(255, 128, 128, 128);
        }
    }

    public void setPenSize(float min, float max) {
        mRadiusMin = min * 0.5f;
        mRadiusMax = max * 0.5f;
    }

    public void setPressureRange(float min, float max) {
        mPressureMin = min;
        mPressureMax = max;
    }
    
    public float[] getPressureRange(float[] r) {
        r[0] = mPressureMin;
        r[1] = mPressureMax;
        return r;
    }
    
    public void recycle() {
    	// WARNING: the slate will not be usable until you call load() or clear() or something
    	if (mDrawingBitmap != null) {
	    	mDrawingBitmap.recycle(); 
	    	mDrawingBitmap = null;
    	}
    	if (mStrokeBitmap != null) {
	    	mStrokeBitmap.recycle();
	        mStrokeBitmap = null;
    	}
    }

    public void clear() {
//        if (mDrawingCanvas != null) {
//            mDrawingCanvas.drawColor(0x00000000, PorterDuff.Mode.SRC);
//            invalidate();
//        }
    	recycle();
        onSizeChanged(getWidth(), getHeight(), 0, 0);
        invalidate();
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

    public void commitStroke() {
        if (mDrawingCanvas == null) return;
        mDrawingCanvas.drawBitmap(mStrokeBitmap, 0, 0, null);
        mStrokeCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
    }

    public void undo() {
        mStrokeCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        invalidate();
    }

    public void paintBitmap(Bitmap b) {
        commitStroke();

        Matrix m = new Matrix();
        RectF s = new RectF(0, 0, b.getWidth(), b.getHeight());
        RectF d = new RectF(0, 0, mDrawingBitmap.getWidth(), mDrawingBitmap.getHeight());
        m.setRectToRect(s, d, Matrix.ScaleToFit.CENTER);
        mStrokeCanvas.drawBitmap(b, m, null);

        if (DEBUG) Log.d(TAG, String.format("paintBitmap(%s, %dx%d): bitmap=%s (%dx%d) mDrawingCanvas=%s",
            b.toString(), b.getWidth(), b.getHeight(),
            mDrawingBitmap.toString(), mDrawingBitmap.getWidth(), mDrawingBitmap.getHeight(),
            mDrawingCanvas.toString()));
    }

    public Bitmap getBitmap() {
        commitStroke();
        return mDrawingBitmap;
    }

    public void setBitmap(Bitmap b) {
        if (b == null) return;

        int newW = b.getWidth();
        int newH = b.getHeight();
        Bitmap newBitmap = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888);
        Canvas newCanvas = new Canvas();
        newCanvas.setBitmap(newBitmap);
        newCanvas.drawBitmap(b, 0, 0, null);
        
        if (mDrawingBitmap != null && !mDrawingBitmap.isRecycled()) mDrawingBitmap.recycle();
        mDrawingBitmap = newBitmap;
        mDrawingCanvas = newCanvas;

        if (mStrokeBitmap != null && !mStrokeBitmap.isRecycled()) mStrokeBitmap.recycle();
        mStrokeBitmap = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888);
        mStrokeCanvas = new Canvas();
        mStrokeCanvas.setBitmap(mStrokeBitmap);

        if (DEBUG) Log.d(TAG, String.format("setBitmap(%s, %dx%d): bitmap=%s (%dx%d) mDrawingCanvas=%s",
            b.toString(), b.getWidth(), b.getHeight(),
            mDrawingBitmap.toString(), mDrawingBitmap.getWidth(), mDrawingBitmap.getHeight(),
            mDrawingCanvas.toString()));
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

        int curW = mDrawingBitmap != null ? mDrawingBitmap.getWidth() : 0;
        int curH = mDrawingBitmap != null ? mDrawingBitmap.getHeight() : 0;
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
                Bitmap.Config.ARGB_8888);
        if (DEBUG) {
            Log.d(TAG, "new size: " + w + "x" + h);
            Log.d(TAG, "old bitmap: " + mDrawingBitmap);
            Log.d(TAG, "created bitmap " + curW + "x" + curH + ": " + newBitmap);
        }
        Canvas newCanvas = new Canvas();
        newCanvas.setBitmap(newBitmap);
        if (mDrawingBitmap != null) {
            // copy the old bitmap in? doesn't seem to work
            newCanvas.drawBitmap(mDrawingBitmap, 0, 0, null);
        }
        if (mDrawingBitmap != null && !mDrawingBitmap.isRecycled()) mDrawingBitmap.recycle();
        mDrawingBitmap = newBitmap;
        mDrawingCanvas = newCanvas;

        if (mStrokeBitmap != null && !mStrokeBitmap.isRecycled()) mStrokeBitmap.recycle();
        mStrokeBitmap = Bitmap.createBitmap(curW, curH, Bitmap.Config.ARGB_8888);
        mStrokeCanvas = new Canvas();
        mStrokeCanvas.setBitmap(mStrokeBitmap);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mDrawingBitmap != null) {
            canvas.drawBitmap(mDrawingBitmap, 0, 0, null);
            canvas.drawBitmap(mStrokeBitmap, 0, 0, null);

//            if (0 != mDebugFlags) {
//                canvas.drawRect(
            if (0 != (mDebugFlags & FLAG_DEBUG_PRESSURE)) {
                StringBuffer strokeInfo = new StringBuffer();
                for (StrokeState st : mStrokes) {
                    strokeInfo.append(String.format("[%.1f] ", st.getRadius()));
                }

                canvas.drawText(
                        String.format("p: %.2f (range: %.2f-%.2f) (recent: %.2f-%.2f)", 
                            mLastPressure,
                            mPressureMin, mPressureMax,
                            mPressureRecentMin, mPressureRecentMax),
                        2, 32, mDebugPaints[4]);
                canvas.drawText(strokeInfo.toString(), 2, 48, mDebugPaints[4]);
            }
        }
    }

    float dbgX = -1, dbgY = -1;
    RectF dbgRect = new RectF();
    
    static boolean hasPointerCoords() {
    	return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR_MR1);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int N = event.getHistorySize();
        int P = event.getPointerCount();
        long time = event.getEventTime();

        // starting a new touch? commit the previous state of the canvas
        if (action == MotionEvent.ACTION_DOWN) {
            commitStroke();
        }

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN
        		|| action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            int j = event.getActionIndex();
        	mTmpSpot.update(
        			event.getX(j),
        			event.getY(j),
        			event.getSize(j),
        			event.getPressure(j),
        			time
        			);
    		mStrokes[event.getPointerId(j)].add(mTmpSpot);
        	if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
	            mStrokes[event.getPointerId(j)].finish();
        	}
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (dbgX >= 0) {
                dbgRect.set(dbgX-1,dbgY-1,dbgX+1,dbgY+1);
            }

            for (int i = 0; i < N; i++) {
                for (int j = 0; j < P; j++) {
                	mTmpSpot.update(
                			event.getHistoricalX(j, i),
                			event.getHistoricalY(j, i),
                			event.getHistoricalSize(j, i),
                			event.getHistoricalPressure(j, i),
                			event.getHistoricalEventTime(i)
                			);
                    if ((mDebugFlags & FLAG_DEBUG_STROKES) != 0) {
                        if (dbgX >= 0) {
                            mStrokeCanvas.drawLine(dbgX, dbgY, mTmpSpot.x, mTmpSpot.y, mDebugPaints[3]);
                        }
                        dbgX = mTmpSpot.x;
                        dbgY = mTmpSpot.y;
                        dbgRect.union(dbgX-1, dbgY-1, dbgX+1, dbgY+1);
                    }
                    mStrokes[event.getPointerId(j)].add(mTmpSpot);
                }
            }
            for (int j = 0; j < P; j++) {
            	mTmpSpot.update(
            			event.getX(j),
            			event.getY(j),
            			event.getSize(j),
            			event.getPressure(j),
            			time
            			);
                if ((mDebugFlags & FLAG_DEBUG_STROKES) != 0) {
                    if (dbgX >= 0) {
                        mStrokeCanvas.drawLine(dbgX, dbgY, mTmpSpot.x, mTmpSpot.y, mDebugPaints[3]);
                    }
                    dbgX = mTmpSpot.x;
                    dbgY = mTmpSpot.y;
                    dbgRect.union(dbgX-1, dbgY-1, dbgX+1, dbgY+1);
                }
                mStrokes[event.getPointerId(j)].add(mTmpSpot);
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

