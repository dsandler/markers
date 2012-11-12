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

import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import org.dsandler.apps.markers.R;

public class Slate extends View {

    static final boolean DEBUG = false;
    static final String TAG = "Slate";
    
    public static final boolean HWLAYER = true;
    public static final boolean SWLAYER = false;
    public static final boolean FANCY_INVALIDATES = false; // doesn't work
    public static final boolean INVALIDATE_ALL_THE_THINGS = true; // invalidate() every frame

    public static final int FLAG_DEBUG_STROKES = 1;
    public static final int FLAG_DEBUG_PRESSURE = 1 << 1;
    public static final int FLAG_DEBUG_INVALIDATES = 1 << 2;
    public static final int FLAG_DEBUG_EVERYTHING = ~0;
    
    public static final int MAX_POINTERS = 10;

    private static final int SMOOTHING_FILTER_WLEN = 6;
    private static final float SMOOTHING_FILTER_POS_DECAY = 0.65f;
    private static final float SMOOTHING_FILTER_PRESSURE_DECAY = 0.9f;

    private static final int FIXED_DIMENSION = 0; // 1024;

    private static final float INVALIDATE_PADDING = 4.0f;
    public static final boolean ASSUME_STYLUS_CALIBRATED = true;
    
    // keep these in sync with penType in values/attrs.xml
    public static final int TYPE_WHITEBOARD = 0;
    public static final int TYPE_FELTTIP = 1;
    public static final int TYPE_AIRBRUSH = 2;
    public static final int TYPE_FOUNTAIN_PEN = 3;
    
    public static final int SHAPE_CIRCLE = 0;
    public static final int SHAPE_SQUARE = 1;
//    public static final int SHAPE_BITMAP_CIRCLE = 2;
    public static final int SHAPE_BITMAP_AIRBRUSH = 3;
    public static final int SHAPE_FOUNTAIN_PEN = 4;

    private float mPressureExponent = 2.0f;

    private float mRadiusMin;
    private float mRadiusMax;

    int mDebugFlags = 0;

    private TiledBitmapCanvas mTiledCanvas;
    private final Paint mDebugPaints[] = new Paint[10];
    
    private Bitmap mPendingPaintBitmap;
    
//    private Bitmap mCircleBits;
//    private Rect mCircleBitsFrame;
    private Bitmap mAirbrushBits;
    private Rect mAirbrushBitsFrame;
    private Bitmap mFountainPenBits;
    private Rect mFountainPenBitsFrame;
        
    private PressureCooker mPressureCooker;
    
    private boolean mEmpty;
    
    private Region mDirtyRegion = new Region();

    public interface SlateListener {
        void strokeStarted();
        void strokeEnded();
    }

    private class MarkersPlotter implements SpotFilter.Plotter {
        // Plotter receives pointer coordinates and draws them.
        // It implements the necessary interface to receive filtered Spots from the SpotFilter.
        // It hands off the drawing command to the renderer.
        
        private SpotFilter mCoordBuffer;
        private SmoothStroker mRenderer;
        
        private float mLastPressure = -1f;
        private int mLastTool = 0;
        
        public MarkersPlotter() {
            mCoordBuffer = new SpotFilter(SMOOTHING_FILTER_WLEN, SMOOTHING_FILTER_POS_DECAY, SMOOTHING_FILTER_PRESSURE_DECAY, this);
            mRenderer = new SmoothStroker();
        }

//        final Rect tmpDirtyRect = new Rect();
        @Override
        public void plot(Spot s) {
            final float pressureNorm;
        
            if (ASSUME_STYLUS_CALIBRATED && s.tool == MotionEvent.TOOL_TYPE_STYLUS) {
                pressureNorm = s.pressure;
            } else {
                pressureNorm = mPressureCooker.getAdjustedPressure(s.pressure);
            }

            final float radius = lerp(mRadiusMin, mRadiusMax,
                    (float) Math.pow(pressureNorm, mPressureExponent));
            
            final RectF dirtyF = mRenderer.strokeTo(mTiledCanvas, s.x, s.y, radius);
            dirty(dirtyF);
//            dirtyF.roundOut(tmpDirtyRect);
//            tmpDirtyRect.inset((int)-INVALIDATE_PADDING,(int)-INVALIDATE_PADDING);
//            invalidate(tmpDirtyRect);
        }
        
        public void setPenColor(int color) {
            mRenderer.setPenColor(color);
        }
        
        public void finish(long time) {
            mLastPressure = -1f;
            mCoordBuffer.finish();
            mRenderer.reset();
        }

//        public void addCoords(MotionEvent.PointerCoords pt, long time) {
//            mCoordBuffer.add(pt, time);
//            mLastPressure = pt.pressure;
//        }
        
        public void add(Spot s) {
            mCoordBuffer.add(s);
            mLastPressure = s.pressure;
            mLastTool = s.tool;
        }
        
//        public float getRadius() {
//            return mRenderer.getRadius();
//        }

        public float getLastPressure() {
            return mLastPressure;
        }
        
        public int getLastTool() {
            return mLastTool;
        }

        public void setPenType(int shape) {
            mRenderer.setPenType(shape);
        }
    }
    
    private class SmoothStroker {
        // The renderer. Given a stream of filtered points, converts it into draw calls.
        
        private float mLastX = 0, mLastY = 0, mLastLen = 0, mLastR = -1;
        private float mTan[] = new float[2];

        private int mPenColor;
        private int mPenType;

        private int mShape = SHAPE_CIRCLE; // SHAPE_BITMAP_AIRBRUSH;

        private Path mWorkPath = new Path();
        private PathMeasure mWorkPathMeasure = new PathMeasure();
        
        private Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        
        int mInkDensity = 0xff; // set to 0x20 or so for a felt-tip look, 0xff for traditional Markers
        
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
                
                //mPaint.setColor(color); 
                mPaint.setColor(Color.BLACK); // or collor? or color & (mInkDensity << 24)?
                mPaint.setAlpha(mInkDensity);
                
//                mPaint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                mPaint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP));
            }
        }

        public int getPenColor() {
            return mPenColor;
        }
        
        public void setPenType(int type) {
            mPenType = type;
            switch (type) {
            case TYPE_WHITEBOARD:
                mShape = SHAPE_CIRCLE;
                mInkDensity = 0xff;
                break;
            case TYPE_FELTTIP:
                mShape = SHAPE_CIRCLE;
                mInkDensity = 0x10;
                break;
            case TYPE_AIRBRUSH:
                mShape = SHAPE_BITMAP_AIRBRUSH;
                mInkDensity = 0x80;
                break;
            case TYPE_FOUNTAIN_PEN:
                mShape = SHAPE_FOUNTAIN_PEN;
                mInkDensity = 0xff;
                break;
            }
            setPenColor(mPenColor);
        }
        
        public int getPenType() {
            return mPenType;
        }

        public void setDebugMode(boolean debug) {
        }

        public void reset() {
            mLastX = mLastY = mTan[0] = mTan[1] = 0;
            mLastR = -1;
        }

        final float dist (float x1, float y1, float x2, float y2) {
            x2-=x1;
            y2-=y1;
            return (float) Math.sqrt(x2*x2 + y2*y2);
        }
        
        private final RectF tmpRF = new RectF();
        final void drawStrokePoint(CanvasLite c, float x, float y, float r, RectF dirty) {
            switch (mShape) {
            case SHAPE_SQUARE:
                c.drawRect(x-r,y-r,x+r,y+r, mPaint);
                break;
//            case SHAPE_BITMAP_CIRCLE:
//                tmpRF.set(x-r,y-r,x+r,y+r);
//                if (mCircleBits == null || mCircleBitsFrame == null) {
//                    throw new RuntimeException("Slate.drawStrokePoint: no circle bitmap - frame=" + mCircleBitsFrame);
//                }
//                c.drawBitmap(mCircleBits, mCircleBitsFrame, tmpRF, mPaint);
//                break;
            case SHAPE_BITMAP_AIRBRUSH:
                tmpRF.set(x-r,y-r,x+r,y+r);
                if (mAirbrushBits == null || mAirbrushBitsFrame == null) {
                    throw new RuntimeException("Slate.drawStrokePoint: no airbrush bitmap - frame=" + mAirbrushBitsFrame);
                }
                c.drawBitmap(mAirbrushBits, mAirbrushBitsFrame, tmpRF, mPaint);
                break;
            case SHAPE_FOUNTAIN_PEN:
                tmpRF.set(x-r,y-r,x+r,y+r);
                if (mFountainPenBits == null || mFountainPenBitsFrame == null) {
                    throw new RuntimeException("Slate.drawStrokePoint: no fountainpen bitmap - frame=" + mFountainPenBitsFrame);
                }
                c.drawBitmap(mFountainPenBits, mFountainPenBitsFrame, tmpRF, mPaint);
                break;
            case SHAPE_CIRCLE:
            default:
                c.drawCircle(x, y, r, mPaint);
                break;
            }
            dirty.union(x-r, y-r, x+r, y+r);
        }
        
        private final RectF tmpDirtyRectF = new RectF();
        public RectF strokeTo(CanvasLite c, float x, float y, float r) {
            final RectF dirty = tmpDirtyRectF;
            dirty.setEmpty();
            
            if (mLastR < 0) {
                // always draw the first point
                drawStrokePoint(c,x,y,r,dirty);
            } else {
                // connect the dots, la-la-la
                
                mLastLen = dist(mLastX, mLastY, x, y);
                float xi, yi, ri, frac;
                float d = 0;
                while (true) {
                    if (d > mLastLen) {
                        break;
                    }
                    frac = d == 0 ? 0 : (d / mLastLen);
                    ri = lerp(mLastR, r, frac);
                    xi = lerp(mLastX, x, frac);
                    yi = lerp(mLastY, y, frac);
                    drawStrokePoint(c,xi,yi,ri,dirty);

                    // for very narrow lines we must step (not much more than) one radius at a time
                    final float MIN = 1f;
                    final float THRESH = 16f;
                    final float SLOPE = 0.1f; // asymptote: the spacing will increase as SLOPE*x
                    if (ri <= THRESH) {
                        d += MIN;
                    } else {
                        d += Math.sqrt(SLOPE * Math.pow(ri - THRESH, 2) + MIN);
                    }
                }
                
                /* 
                // for curved paths
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
                */
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

    private MarkersPlotter[] mStrokes;

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
    
    @SuppressLint("NewApi")
    private void init() {
//        setWillNotCacheDrawing(true);
//        setDrawingCacheEnabled(false);
        
        mEmpty = true;

        // setup brush bitmaps
        final int memClass;
        final ActivityManager am = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            memClass = am.getLargeMemoryClass();
        } else {
            memClass = am.getMemoryClass();
        }
        final boolean lowMem = (memClass <= 16);
        if (DEBUG) {
            Log.v(TAG, "Slate.init: memClass=" + memClass + (lowMem ? " LOW" : ""));
        }

        final Resources res = getContext().getResources();

//        mCircleBits = BitmapFactory.decodeResource(res, R.drawable.circle_1bpp);
//        if (mCircleBits == null) { Log.e(TAG, "SmoothStroker: Couldn't load circle bitmap"); }
//        mCircleBitsFrame = new Rect(0, 0, mCircleBits.getWidth(), mCircleBits.getHeight());

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ALPHA_8;
        if (lowMem) { // let's see how this works in practice
            opts.inSampleSize = 4;
        }
        mAirbrushBits = BitmapFactory.decodeResource(res, R.drawable.airbrush_light, opts);
        if (mAirbrushBits == null) { Log.e(TAG, "SmoothStroker: Couldn't load airbrush bitmap"); }
        mAirbrushBitsFrame = new Rect(0, 0, mAirbrushBits.getWidth(), mAirbrushBits.getHeight());
        //Log.v(TAG, "airbrush: " + mAirbrushBitsFrame.right + "x" + mAirbrushBitsFrame.bottom);
        mFountainPenBits = BitmapFactory.decodeResource(res, R.drawable.fountainpen, opts);
        if (mFountainPenBits == null) { Log.e(TAG, "SmoothStroker: Couldn't load fountainpen bitmap"); }
        mFountainPenBitsFrame = new Rect(0, 0, mFountainPenBits.getWidth(), mFountainPenBits.getHeight());

        // set up individual strokers for each pointer
        mStrokes = new MarkersPlotter[MAX_POINTERS]; // TODO: don't bother unless hasSystemFeature(MULTITOUCH_DISTINCT)
        for (int i=0; i<mStrokes.length; i++) {
            mStrokes[i] = new MarkersPlotter();
        }
        
        mPressureCooker = new PressureCooker(getContext());

        setFocusable(true);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            if (HWLAYER) {
                setLayerType(View.LAYER_TYPE_HARDWARE, null);
            } else if (SWLAYER) {
                setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            } else {
                setLayerType(View.LAYER_TYPE_NONE, null);
            }
        }
        
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

    public boolean isEmpty() { return mEmpty; }
    
    public void setPenSize(float min, float max) {
        mRadiusMin = min * 0.5f;
        mRadiusMax = max * 0.5f;
    }

    public void recycle() {
    	// WARNING: the slate will not be usable until you call load() or clear() or something
    	if (mTiledCanvas != null) {
	    	mTiledCanvas.recycleBitmaps();
	        mTiledCanvas = null;
    	}
    }

    public void clear() {
        if (mTiledCanvas != null) {
            commitStroke();
            mTiledCanvas.drawColor(0x00000000, PorterDuff.Mode.SRC);
            invalidate();
        } else if (mPendingPaintBitmap != null) { // FIXME for tiling
            mPendingPaintBitmap.recycle();
            mPendingPaintBitmap = null;
        }
        mEmpty = true;
    }

    public int getDebugFlags() { return mDebugFlags; }
    public void setDebugFlags(int f) {
        if (f != mDebugFlags) {
            mDebugFlags = f;
            invalidate();
        }
    }
    
    private Bitmap mStrokeDebugGraph;
    private int mGraphX = 0;
    private Paint mGraphPaint1;
    private int mBackgroundColor = Color.TRANSPARENT;

    private void drawStrokeDebugInfo(Canvas c) {
        final int ROW_HEIGHT = 24;
        final int ROW_MARGIN = 6;
        final int COLUMN_WIDTH = 55;
        
        final float FIRM_PRESSURE_LOW = 0.85f;
        final float FIRM_PRESSURE_HIGH = 1.25f;
        
        if (mStrokeDebugGraph == null) {
            final int width = c.getWidth() - 128;
            final int height = ROW_HEIGHT * mStrokes.length + 2 * ROW_MARGIN;
            mStrokeDebugGraph = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            if (mStrokeDebugGraph == null) {
                throw new RuntimeException("drawStrokeDebugInfo: couldn't create debug bitmap (" + width + "x" + height + ")");
            }
            mGraphPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        }
        
        Canvas graph = new Canvas(mStrokeDebugGraph);
        graph.save();
        graph.clipRect(new Rect(0, 0, COLUMN_WIDTH, graph.getHeight()));
        graph.drawColor(0, PorterDuff.Mode.CLEAR);
        graph.restore();
        
        int left = 4;
        int bottom = graph.getHeight() - ROW_MARGIN;
        final int STEP = 4; 
        for (MarkersPlotter st : mStrokes) {
            float r = st.getLastPressure();
            
            if (r >= FIRM_PRESSURE_LOW && r <= FIRM_PRESSURE_HIGH) 
                mGraphPaint1.setColor(0xFF33FF33);
            else if (r < FIRM_PRESSURE_LOW)
                mGraphPaint1.setColor(0xFF808080);
            else
                mGraphPaint1.setColor(0xFFFF8000);

            String s = (r < 0) ? "--" : String.format("%s %.4f",
                    ((st.getLastTool() == MotionEvent.TOOL_TYPE_STYLUS) ? "S" : "F"),
                    r);
            
            graph.drawText(s, left, bottom - 2, mGraphPaint1);
            
            if (mGraphX + COLUMN_WIDTH > graph.getWidth()) {
                mGraphX = 0;
                graph.save();
                graph.clipRect(new Rect(30, 0, graph.getWidth(), graph.getHeight()));
                graph.drawColor(0, PorterDuff.Mode.CLEAR);
                graph.restore();
            }
            
            if (r >= 0) {
                int barsize = (int)(r * ROW_HEIGHT);
                graph.drawRect(mGraphX + COLUMN_WIDTH, bottom - barsize, 
                        mGraphX + COLUMN_WIDTH + STEP, bottom, mGraphPaint1);
            } else {
                graph.drawPoint(mGraphX + COLUMN_WIDTH + STEP, bottom, mGraphPaint1);
            }
            bottom -= (ROW_HEIGHT + ROW_MARGIN);
        }
        
        mGraphX += STEP;
        
        final int x = 96;
        final int y = 64;
        
        c.drawBitmap(mStrokeDebugGraph, x, y, null);
        invalidate(new Rect(x, y, x+c.getWidth(), y+c.getHeight()));
    }

    public void commitStroke() {
        if (mTiledCanvas == null) {
            final Throwable e = new Throwable();
            e.fillInStackTrace();
            Log.v(TAG, "commitStroke before mTiledCanvas inited", e);
            return;
        }
        mTiledCanvas.commit();
    }

    public void undo() {
        if (mTiledCanvas == null) {
            Log.v(TAG, "undo before mTiledCanvas inited");
        }
        mTiledCanvas.step(-1);

        invalidate();
    }

    public void paintBitmap(Bitmap b) {
        if (mTiledCanvas == null) {
            mPendingPaintBitmap = b;
            return;
        }

        commitStroke();

        Matrix m = new Matrix();
        RectF s = new RectF(0, 0, b.getWidth(), b.getHeight());
        RectF d = new RectF(0, 0, mTiledCanvas.getWidth(), mTiledCanvas.getHeight());
        m.setRectToRect(s, d, Matrix.ScaleToFit.CENTER);
        
        if (DEBUG) { 
            Log.v(TAG, "paintBitmap: drawing new bits into current canvas");
        }
        mTiledCanvas.drawBitmap(b, m, sBitmapPaint);
        invalidate();

        if (DEBUG) Log.d(TAG, String.format("paintBitmap(%s, %dx%d): canvas=%s",
            b.toString(), b.getWidth(), b.getHeight(),
            mTiledCanvas.toString()));
    }

    public void setDrawingBackground(int color) {
        mBackgroundColor  = color;
        setBackgroundColor(color);
        invalidate();
    }

    public Bitmap getBitmap() {
        commitStroke();
        // FIXME for tiling
        return mTiledCanvas.toBitmap();
    }

    public Bitmap copyBitmap(boolean withBackground) {
        Bitmap b = getBitmap();
        Bitmap newb = Bitmap.createBitmap(b.getWidth(), b.getHeight(), b.getConfig());
        if (newb != null) {
            Canvas newc = new Canvas(newb);
            if (mBackgroundColor != Color.TRANSPARENT && withBackground) {
                newc.drawColor(mBackgroundColor);
            }
            newc.drawBitmap(b, 0, 0, null);
        }
        return newb;
    }

    public void setBitmap(Bitmap b) {
        if (b == null) return;
        
        mTiledCanvas.recycleBitmaps();
        mTiledCanvas = new TiledBitmapCanvas(b);
        mEmpty = false;
        
        // XXX FIXME for tiling

        if (DEBUG) { 
            Log.v(TAG, "setBitmap: drawing new bits into current canvas");
        }
        // mTiledCanvas.drawBitmap(b, 0, 0, null);

        if (DEBUG) Log.d(TAG, String.format("setBitmap(%s, %dx%d): mTiledCanvas=%s",
            b.toString(), b.getWidth(), b.getHeight(),
            mTiledCanvas.toString()));

        mEmpty = false;
    }

    public void setPenColor(int color) {
        for (MarkersPlotter plotter : mStrokes) {
            // XXX: todo: only do this if the stroke hasn't begun already
            // ...or not; the current behavior allows RAINBOW MODE!!!1!
            plotter.setPenColor(color);
        }
    }
    
    public void setPenType(int shape) {
        for (MarkersPlotter plotter : mStrokes) {
            plotter.setPenType(shape);
        }
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw,
            int oldh) {
        if (mTiledCanvas != null) return;
        
        mTiledCanvas = new TiledBitmapCanvas(w, h, Bitmap.Config.ARGB_8888);
        if (mTiledCanvas == null) {
            throw new RuntimeException("onSizeChanged: Unable to allocate main buffer (" + w + "x" + h + ")");
        }

        final Bitmap b = mPendingPaintBitmap; 
        if (b != null) {
            mPendingPaintBitmap = null;
            paintBitmap(b);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mTiledCanvas != null) {
            if (!mDirtyRegion.isEmpty()) {
                canvas.clipRegion(mDirtyRegion);
                mDirtyRegion.setEmpty();
            }
            mTiledCanvas.drawTo(canvas, 0, 0, null, false); // @@ set to true for dirty tile updates
            if (0 != (mDebugFlags & FLAG_DEBUG_STROKES)) {
                drawStrokeDebugInfo(canvas);
            }
            
            if (0 != (mDebugFlags & FLAG_DEBUG_PRESSURE)) {
                mPressureCooker.drawDebug(canvas);
            }
        }
    }

    float dbgX = -1, dbgY = -1;
    RectF dbgRect = new RectF();
    
    final static boolean hasPointerCoords() {
    	return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR_MR1);
    }

    final static boolean hasToolType() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH);
    }
    
    @SuppressLint("NewApi")
    final static int getToolTypeCompat(MotionEvent me, int index) {
        if (hasToolType()) {
            return me.getToolType(index);
        }
        
        // dirty hack for the HTC Flyer
        if ("flyer".equals(Build.HARDWARE)) {
            if (me.getSize(index) <= 0.1f) {
                // with very high probability this is the stylus
                return MotionEvent.TOOL_TYPE_STYLUS;
            }
        }
        
        return MotionEvent.TOOL_TYPE_FINGER;
    }

    @SuppressLint("NewApi")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int N = event.getHistorySize();
        int P = event.getPointerCount();
        long time = event.getEventTime();

        mEmpty = false;

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
        			event.getPressure(j) + event.getSize(j),
        			time,
        			getToolTypeCompat(event, j)
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
                			event.getHistoricalPressure(j, i)
                                + event.getHistoricalSize(j, i),
                			event.getHistoricalEventTime(i),
                            getToolTypeCompat(event, j)
                			);
                    if ((mDebugFlags & FLAG_DEBUG_STROKES) != 0) {
                        if (dbgX >= 0) {
                            //mTiledCanvas.drawLine(dbgX, dbgY, mTmpSpot.x, mTmpSpot.y, mDebugPaints[3]);
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
            			event.getPressure(j) + event.getSize(j),
            			time,
                        getToolTypeCompat(event, j)
           			);
                if ((mDebugFlags & FLAG_DEBUG_STROKES) != 0) {
                    if (dbgX >= 0) {
                        //mTiledCanvas.drawLine(dbgX, dbgY, mTmpSpot.x, mTmpSpot.y, mDebugPaints[3]);
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

    final Rect tmpDirtyRect = new Rect();
    private void dirty(RectF r) {
        r.roundOut(tmpDirtyRect);
        tmpDirtyRect.inset((int)-INVALIDATE_PADDING,(int)-INVALIDATE_PADDING);
        if (INVALIDATE_ALL_THE_THINGS) {
            invalidate();
        } else if (FANCY_INVALIDATES) {
            mDirtyRegion.union(tmpDirtyRect);
            invalidate(); // enqueue invalidation
        } else {
            invalidate(tmpDirtyRect);
        }
    }
}
