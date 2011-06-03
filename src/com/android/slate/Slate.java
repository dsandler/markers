package com.android.slate;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class Slate extends View {

    static final boolean DEBUG = true;
    static final String TAG = "Slate";

    public static final int FLAG_DEBUG_STROKES = 1;
    public static final int FLAG_DEBUG_PRESSURE = 1 << 1;
    public static final int FLAG_DEBUG_INVALIDATES = 1 << 2;
    public static final int FLAG_DEBUG_EVERYTHING = ~0;

    public static final int MAX_POINTERS = 10;

    private static final float WALK_STEP_PX = 3.0f;

    private static final int SMOOTHING_FILTER_WLEN = 6;
    private static final float SMOOTHING_FILTER_DECAY = 0.7f;

    private static final int FIXED_DIMENSION = 0; // 1024;

    private static final float INVALIDATE_PADDING = 4.0f;

    private float mPressureExponent = 2.0f;

    private float mRadiusMin;
    private float mRadiusMax;

    int mDebugFlags = 0;

    private Bitmap mPreviousBitmap, mCurrentBitmap;
    private Canvas mPreviousCanvas, mCurrentCanvas;
    private final Paint mDebugPaints[] = new Paint[10];
    
    private PressureCooker mPressureCooker;

    private class MarkersPlotter implements SpotFilter.Plotter {
        // Plotter receives pointer coordinates and draws them.
        // It implements the necessary interface to receive filtered Spots from the SpotFilter.
        // It hands off the drawing command to the renderer.
        
        private SpotFilter mCoordBuffer;
        private SmoothStroker mRenderer;
        
        public MarkersPlotter() {
            mCoordBuffer = new SpotFilter(SMOOTHING_FILTER_WLEN, SMOOTHING_FILTER_DECAY, this);
            mRenderer = new SmoothStroker();
        }

        final Rect tmpDirtyRect = new Rect();
        @Override
        public void plot(Spot s) {
            final float pressureNorm = mPressureCooker.getAdjustedPressure(s.pressure);
            
            final float radius = lerp(mRadiusMin, mRadiusMax,
                   (float) Math.pow(pressureNorm, mPressureExponent));
            
            RectF dirtyF = mRenderer.strokeTo(mCurrentCanvas, s.x, s.y, radius);
            dirtyF.roundOut(tmpDirtyRect);
            tmpDirtyRect.inset((int)-INVALIDATE_PADDING,(int)-INVALIDATE_PADDING);
            invalidate(tmpDirtyRect);
        }
        
        public void setPenColor(int color) {
            mRenderer.setPenColor(color);
        }
        
        public void finish(long time) {
            mCoordBuffer.finish();
            mRenderer.reset();
        }

        public void addCoords(MotionEvent.PointerCoords pt, long time) {
            mCoordBuffer.add(pt, time);
        }
        
        public void add(Spot s) {
            mCoordBuffer.add(s);
        }
    }
    
    private static class SmoothStroker {
        // The renderer. Given a stream of filtered points, converts it into draw calls.
        
        private float mLastX = 0, mLastY = 0, mLastLen = 0, mLastR = -1;
        private float mTan[] = new float[2];

        private int mPenColor;

        private Path mWorkPath = new Path();
        private PathMeasure mWorkPathMeasure = new PathMeasure();
        
        private Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        
        public SmoothStroker() {
            
        }

        public void setPenColor(int color) {
            mPenColor = color;
            if (color == 0) {
                // eraser: DST_OUT
                mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                mPaint.setColor(Color.BLACK);
            } else {
                mPaint.setXfermode(null);
                mPaint.setColor(color);
            }
        }

        public int getPenColor() {
            return mPenColor;
        }

        public void setDebugMode(boolean debug) {
        }

        public void reset() {
            mLastX = mLastY = mTan[0] = mTan[1] = 0;
            mLastR = -1;
        }

        final static RectF tmpDirtyRectF = new RectF();
        public RectF strokeTo(Canvas c, float x, float y, float r) {
            final RectF dirty = tmpDirtyRectF;
            dirty.setEmpty();
            
            if (mLastR < 0) {
                // always draw the first point
                c.drawCircle(x, y, r, mPaint);
                dirty.union(x-r, y-r, x+r, y+r);
            } else {
                // connect the dots, la-la-la
                
                Path p = mWorkPath;
                p.reset();
                p.moveTo(mLastX, mLastY);
                p.lineTo(x, y);
                
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
                    c.drawCircle(posOut[0], posOut[1], ri, mPaint);
                    dirty.union(posOut[0] - ri, posOut[1] - ri, posOut[0] + ri, posOut[1] + ri);

                    if (d == mLastLen) break;
                    d += Math.min(ri, WALK_STEP_PX); // for very narrow lines we must step one radius at a time
                }
            }

            mLastX = x;
            mLastY = y;
            mLastR = r;
            
            return dirty;
        }
        
        public float getRadius() {
            return mLastR;
        }
    }

    private MarkersPlotter[] mStrokes = new MarkersPlotter[MAX_POINTERS];

    Spot mTmpSpot = new Spot();
    
    private static Paint sBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    
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
            mStrokes[i] = new MarkersPlotter();
        }
        
        mPressureCooker = new PressureCooker(getContext());

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

    public void recycle() {
    	// WARNING: the slate will not be usable until you call load() or clear() or something
    	if (mPreviousBitmap != null) {
	    	mPreviousBitmap.recycle(); 
	    	mPreviousBitmap = null;
    	}
    	if (mCurrentBitmap != null) {
	    	mCurrentBitmap.recycle();
	        mCurrentBitmap = null;
    	}
    }

    public void clear() {
        commitStroke();
        mCurrentCanvas.drawColor(0x00000000, PorterDuff.Mode.SRC);
        invalidate();
    }

    public int getDebugFlags() { return mDebugFlags; }
    public void setDebugFlags(int f) {
        if (f != mDebugFlags) {
            // XXX
        }

        mDebugFlags = f;
    }

    public void commitStroke() {
        if (mPreviousCanvas == null) return;

        Canvas swapCanvas = mPreviousCanvas;
        Bitmap swapBitmap = mPreviousBitmap;

        mPreviousCanvas = mCurrentCanvas;
        mPreviousBitmap = mCurrentBitmap;

        swapCanvas.save();
        swapCanvas.setMatrix(null);
        swapCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        swapCanvas.drawBitmap(mPreviousBitmap, 0, 0, null);
        swapCanvas.restore();

        mCurrentCanvas = swapCanvas;
        mCurrentBitmap = swapBitmap;
    }

    public void undo() {
        mCurrentCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        mCurrentCanvas.drawBitmap(mPreviousBitmap, 0, 0, null);

        invalidate();
    }

    public void paintBitmap(Bitmap b) {
        commitStroke();

        Matrix m = new Matrix();
        RectF s = new RectF(0, 0, b.getWidth(), b.getHeight());
        RectF d = new RectF(0, 0, mCurrentBitmap.getWidth(), mCurrentBitmap.getHeight());
        m.setRectToRect(s, d, Matrix.ScaleToFit.CENTER);
        
        mCurrentCanvas.drawBitmap(b, m, sBitmapPaint);
        invalidate();

        if (DEBUG) Log.d(TAG, String.format("paintBitmap(%s, %dx%d): bitmap=%s (%dx%d) canvas=%s",
            b.toString(), b.getWidth(), b.getHeight(),
            mCurrentBitmap.toString(), mCurrentBitmap.getWidth(), mCurrentBitmap.getHeight(),
            mCurrentCanvas.toString()));
    }

    public Bitmap getBitmap() {
        commitStroke();
        return mPreviousBitmap;
    }

    public void setBitmap(Bitmap b) {
        if (b == null) return;

        int newW = b.getWidth();
        int newH = b.getHeight();
        Bitmap newBitmap = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888);
        Canvas newCanvas = new Canvas();
        newCanvas.setBitmap(newBitmap);
        newCanvas.drawBitmap(b, 0, 0, null);
        
        if (mCurrentBitmap != null && !mCurrentBitmap.isRecycled()) mCurrentBitmap.recycle();
        mCurrentBitmap = newBitmap;
        mCurrentCanvas = newCanvas;

        if (mPreviousBitmap != null && !mPreviousBitmap.isRecycled()) mPreviousBitmap.recycle();
        mPreviousBitmap = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888);
        mPreviousCanvas = new Canvas();
        mPreviousCanvas.setBitmap(mPreviousBitmap);

        if (DEBUG) Log.d(TAG, String.format("setBitmap(%s, %dx%d): bitmap=%s (%dx%d) mPreviousCanvas=%s",
            b.toString(), b.getWidth(), b.getHeight(),
            mPreviousBitmap.toString(), mPreviousBitmap.getWidth(), mPreviousBitmap.getHeight(),
            mPreviousCanvas.toString()));
    }

    public void setPenColor(int color) {
        for (MarkersPlotter plotter : mStrokes) {
            // XXX: todo: only do this if the stroke hasn't begun already
            // ...or not; the current behavior allows RAINBOW MODE!!!1!
            plotter.setPenColor(color);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw,
            int oldh) {
        if (mCurrentBitmap != null) return;

        mCurrentBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        mCurrentCanvas = new Canvas();
        mCurrentCanvas.setBitmap(mCurrentBitmap);

        mPreviousBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        mPreviousCanvas = new Canvas();
        mPreviousCanvas.setBitmap(mPreviousBitmap);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mCurrentBitmap != null) {
            canvas.drawBitmap(mCurrentBitmap, 0, 0, null);
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
	            mStrokes[event.getPointerId(j)].finish(time);
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
                            mCurrentCanvas.drawLine(dbgX, dbgY, mTmpSpot.x, mTmpSpot.y, mDebugPaints[3]);
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
                        mCurrentCanvas.drawLine(dbgX, dbgY, mTmpSpot.x, mTmpSpot.y, mDebugPaints[3]);
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
                mStrokes[event.getPointerId(j)].finish(time);
            }
            dbgX = dbgY = -1;
        }
        return true;
    }

    public static float lerp(float a, float b, float f) {
        return a + f * (b - a);
    }
    
    @Override
    public void invalidate(Rect r) {
        if (r.isEmpty()) {
            Log.w(TAG, "invalidating empty rect!");
        }
        super.invalidate(r);
    }
}

