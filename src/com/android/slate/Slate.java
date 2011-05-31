package com.android.slate;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
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

    private static final boolean BEZIER = false;
    private static final float BEZIER_CONTROL_POINT_DISTANCE=0.25f;
    private static final float WALK_STEP_PX = 3.0f;

    private static final int SMOOTHING_FILTER_WLEN = 6;
    private static final float SMOOTHING_FILTER_DECAY = 0.7f;

    private static final int FIXED_DIMENSION = 0; // 1024;

    private static final float INVALIDATE_PADDING = 4.0f;

    private float mPressureExponent = 2.0f;

    private float mPressureMin = 0;
    private float mPressureMax = 1;

    public static final float PRESSURE_UPDATE_DECAY = 0.1f;
    public static final int PRESSURE_UPDATE_STEPS_FIRSTBOOT = 100; // points, a quick-training regimen
    public static final int PRESSURE_UPDATE_STEPS_NORMAL = 1000; // points, in normal use
    private int mPressureCountdownStart = PRESSURE_UPDATE_STEPS_NORMAL;
    private int mPressureUpdateCountdown = mPressureCountdownStart;
    private float mPressureRecentMin = 1;
    private float mPressureRecentMax = 0;

    private float mRadiusMin;
    private float mRadiusMax;

    private float mLastPressure;

    private int mDebugFlags = 0;

    private Bitmap mPreviousBitmap, mCurrentBitmap;
    private Canvas mPreviousCanvas, mCurrentCanvas;
    private final Paint mDebugPaints[] = new Paint[10];
    
    public static class DrawStream {
        private static class DrawOp {
            public long time;
            
            public int kind;
            public static final int KIND_MARK = 0;
            public static final int KIND_CIRCLE = 1;
            public static final int KIND_BITMAP = 2;
            public static final String[] KIND_NAMES = { "mrk", "cir", "bit" };
            
            public float f0, f1, f2;
            public int i0, i1, i2, i3;
            public byte[] b;
            public Bitmap bitmap;
            
            public DrawOp(long _time, int _kind) {
                time = _time;
                kind = _kind;
            }
            public static DrawOp make(long time, int kind, float f0, float f1, float f2, int i0, int i1, int i2, int i3) {
                DrawOp op = new DrawOp(time, kind);
                op.f0 = f0;
                op.f1 = f1;
                op.f2 = f2;
                op.i0 = i0;
                op.i1 = i1;
                op.i2 = i2;
                op.i3 = i3;
                return op;
            }
            public String toString() {
                return String.format("<%s @ %d : %g %g %g %d %d %d>",
                        KIND_NAMES[kind],
                        time,
                        f0, f1, f2, i0, i1, i2);
            }
            public void draw(Canvas c, Paint p) {
                switch (kind) {
                    case KIND_CIRCLE:
                        final float x=f0, y=f1, r=f2;
                        if (i0 == 0) {
                            // eraser: DST_OUT
                            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                            p.setColor(0xFF000000);
                        } else {
                            p.setXfermode(null);
                            p.setColor(i0);
                        }
                        c.drawCircle(x, y, r, p);
                        break;
                    case KIND_BITMAP:
                        if (bitmap == null && b != null) {
                            bitmap = BitmapFactory.decodeByteArray(b, 0, b.length);
                        }
                        if (bitmap != null) {
                            p.setXfermode(null);
                            p.setColor(0xFF000000);
                            c.drawBitmap(bitmap, new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()), new Rect(i0, i1, i2, i3), p);
                        }
                        break;
                }
                
            }
            public void dirty(RectF dirty) {
                switch (kind) {
                    case KIND_CIRCLE:
                        final float x=f0, y=f1, r=f2;
                        dirty.union(x-r, y-r, x+r, y+r);
                        break;
                    case KIND_BITMAP:
                        dirty.union(i0, i1, i2, i3);
                        break;
                }
            }
        }
        
        public String toString() {
            // summarize
            String s = "[";
            int lastKind = -1;
            int count = 0;
            for (DrawOp op : stream) {
                if (op.kind != lastKind) {
                    if (count > 0) {
                        s += lastKind + " (" + count + "), ";
                    }
                    lastKind = op.kind;
                    count = 1;
                } else count++;
            }
            if (count > 0) {
                s += lastKind + " (" + count + ")";
            }
            return s + "]";
        }

        private ArrayList<DrawOp> stream;
        final Paint mPaint;
        
        private int mDrawingStep;
        
        public DrawStream() {
            stream = new ArrayList<DrawOp>(10000);
            mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mDrawingStep = 0;
        }
        public void loadFromFile() {
            // TODO
            // - update mStrokeId to be last+1
        }
        
        public void writeToFile() {
            // TODO
        }
        
        
        // Drawing calls
        public void plotCircle(long time, float x, float y, float r, int color) {
            stream.add(DrawOp.make(time, DrawOp.KIND_CIRCLE, x, y, r, color, 0, 0, 0));
        }
        
        public void setUndoMark(long time) {
            stream.add(DrawOp.make(time, DrawOp.KIND_MARK, 0, 0, 0, 0, 0, 0, 0));
        }
        
        public void plotBitmap(long time, Bitmap bits, Rect dst) {
            final DrawOp d = DrawOp.make(time, DrawOp.KIND_BITMAP, 0, 0, 0, dst.left, dst.top, dst.right, dst.bottom);
            d.bitmap = bits;
            stream.add(d);
        }
        
        public Rect undo() {
            Log.d(TAG, "undo: stream: " + toString());
            int i = stream.size() - 2; 
            while (i --> 0) {
                if (stream.get(i).kind == DrawOp.KIND_MARK) 
                    return trunc(i+1);
            }
            return trunc(0);
        }
        
        public Rect trunc(int step) {
            RectF dirty = new RectF();
            RectF dirtyDeedsDone = new RectF();
            Log.d(TAG, "trunc will discard " + (stream.size()-step) + " steps");
            while (step < stream.size()) {
                DrawOp op = stream.get(step);
                //Log.d(TAG, "trunc removing #" + (step) + ": " + op);
                op.dirty(dirty);
                stream.remove(step);
                dirtyDeedsDone.union(dirty);
            }
            if (mDrawingStep > stream.size())
                mDrawingStep = stream.size();
            Rect r2 = new Rect();
            dirtyDeedsDone.roundOut(r2);
            return r2;
        }
        
        public void setDrawingStep(int step) {
            mDrawingStep = step;
        }
        
        public Rect play(Canvas c) {
            return play(c, stream.size()-1-mDrawingStep);
        }
        
        public Rect play(Canvas c, int steps) {
            if (mDrawingStep + steps > stream.size() - 1)
                steps = stream.size() - 1 - mDrawingStep;

            RectF dirty = new RectF();
            RectF dirtyDeedsDone = new RectF();
            while (steps-- > 0) {
                DrawOp op = stream.get(mDrawingStep++);
                dirty.setEmpty();
                op.dirty(dirty);
                if (! c.quickReject(dirty, Canvas.EdgeType.AA)) {
                    op.draw(c, mPaint);
                    dirtyDeedsDone.union(dirty);
                }
            }
            Rect r2 = new Rect();
            dirtyDeedsDone.roundOut(r2);
            return r2;
        }
        
        public void clear() {
            mDrawingStep = 0;
            stream.clear();
        }
    }
    
    private class PressureCooker {
        // Adjusts pressure values on the fly based on historical maxima/minima.
        public float getAdjustedPressure(float pressure) {
            mLastPressure = pressure;
            if (pressure < mPressureRecentMin) mPressureRecentMin = pressure;
            if (pressure > mPressureRecentMax) mPressureRecentMax = pressure;
            
            if (--mPressureUpdateCountdown == 0) {
                final float decay = PRESSURE_UPDATE_DECAY;
                mPressureMin = (1-decay) * mPressureMin + decay * mPressureRecentMin;
                mPressureMax = (1-decay) * mPressureMax + decay * mPressureRecentMax;

                // upside-down values, will be overwritten on the next point
                mPressureRecentMin = 1;
                mPressureRecentMax = 0;

                // walk the countdown up to the maximum value
                if (mPressureCountdownStart < PRESSURE_UPDATE_STEPS_NORMAL) {
                    mPressureCountdownStart = (int) (mPressureCountdownStart * 1.5f);
                    if (mPressureCountdownStart > PRESSURE_UPDATE_STEPS_NORMAL)
                        mPressureCountdownStart = PRESSURE_UPDATE_STEPS_NORMAL;
                }
                mPressureUpdateCountdown = mPressureCountdownStart;
            }

            final float pressureNorm = (pressure - mPressureMin)
                / (mPressureMax - mPressureMin);

            if (false && 0 != (mDebugFlags)) {
                Log.d(TAG, String.format("pressure=%.2f range=%.2f-%.2f obs=%.2f-%.2f pnorm=%.2f",
                    pressure, mPressureMin, mPressureMax, mPressureRecentMin, mPressureRecentMax, pressureNorm));
            }

            return pressureNorm;
        }
    }
    
    private final PressureCooker mPressureCooker = new PressureCooker();

    private class Plotter implements SpotFilter.Plotter {
        // Plotter receives pointer coordinates and draws them.
        // It implements the necessary interface to receive filtered Spots from the SpotFilter.
        // It hands off the drawing command to the renderer.
        
        private SpotFilter mCoordBuffer;
        private SmoothStroker mRenderer;
        private DrawStream mStream;
        
        public Plotter(DrawStream stream) {
            mCoordBuffer = new SpotFilter(SMOOTHING_FILTER_WLEN, SMOOTHING_FILTER_DECAY, this);
            mStream = stream;
            mRenderer = new SmoothStroker(stream);
        }

        @Override
        public void plot(Spot s) {
            final float pressureNorm = mPressureCooker.getAdjustedPressure(s.pressure);
            
            final float radius = lerp(mRadiusMin, mRadiusMax,
                   (float) Math.pow(pressureNorm, mPressureExponent));
            
            mRenderer.strokeTo(s.time, s.x, s.y, radius);
            
            Rect dirty = mStream.play(mCurrentCanvas);
            //dirty.inset((int)-INVALIDATE_PADDING,(int)-INVALIDATE_PADDING);
            dirty.offset((int)mViewportTranslationX, (int)mViewportTranslationY);
            invalidate(dirty);
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
        
        private DrawStream mStream;

        public SmoothStroker(DrawStream stream) {
            mStream = stream;
        }

        public void setPenColor(int color) {
            mPenColor = color;
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

        public void strokeTo(long time, float x, float y, float r) {
            if (mLastR < 0) {
                // always draw the first point
                mStream.plotCircle(time, x, y, r, mPenColor);
            } else {
                // connect the dots, la-la-la
                
                Path p = mWorkPath;
                p.reset();
                p.moveTo(mLastX, mLastY);

                if (BEZIER && (mTan[0] != 0 || mTan[1] != 0)) {
                    float controlX = mLastX + mTan[0]*BEZIER_CONTROL_POINT_DISTANCE;
                    float controlY = mLastY + mTan[1]*BEZIER_CONTROL_POINT_DISTANCE;

                    p.quadTo(controlX, controlY, x, y);
                } else {
                    p.lineTo(x, y);
                }

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
                    mStream.plotCircle(time, posOut[0], posOut[1], ri, mPenColor);

                    if (d == mLastLen) break;
                    d += Math.min(ri, WALK_STEP_PX); // for very narrow lines we must step one radius at a time
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

    private Plotter[] mStrokes = new Plotter[MAX_POINTERS];

    Spot mTmpSpot = new Spot();
    
    DrawStream mStream;
    
    public Slate(Context c, AttributeSet as) {
        super(c, as);
        init();
    }
    
    public Slate(Context c) {
    	super(c);
    	init();
    }
    
    private void init() {
        mStream = new DrawStream();
        
        for (int i=0; i<mStrokes.length; i++) {
            mStrokes[i] = new Plotter(mStream);
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

    public void setFirstRun(boolean firstRun) {
        // "Why do my eyes hurt?"
        // "You've never used them before."
        if (firstRun) {
            mPressureUpdateCountdown = mPressureCountdownStart = PRESSURE_UPDATE_STEPS_FIRSTBOOT;
        }
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
//        if (mPreviousCanvas != null) {
//            mPreviousCanvas.drawColor(0x00000000, PorterDuff.Mode.SRC);
//            invalidate();
//        }
        mViewportTranslationY = mViewportTranslationX = 0;
        mStream.clear();
        recycle();
    	onSizeChanged(getWidth(), getHeight(), 0, 0);
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
        /*
        mCurrentCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        mCurrentCanvas.drawBitmap(mPreviousBitmap, 0, 0, null);
        */
        
        Rect dirty = mStream.undo();
        //mCurrentCanvas.save();
        //mCurrentCanvas.clipRect(dirty);
        mCurrentCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        mStream.setDrawingStep(0);
        mStream.play(mCurrentCanvas);
        //mCurrentCanvas.restore();

        invalidate(dirty);
    }

    public void paintBitmap(Bitmap b) {
        commitStroke();

        Matrix m = new Matrix();
        RectF s = new RectF(0, 0, b.getWidth(), b.getHeight());
        RectF d = new RectF(0, 0, mPreviousBitmap.getWidth(), mPreviousBitmap.getHeight());
        m.setRectToRect(s, d, Matrix.ScaleToFit.CENTER);
        
        m.mapRect(d);
        Rect r = new Rect();
        d.roundOut(r);
        mStream.plotBitmap(0, b, r);
        mStream.setUndoMark(0);
        mStream.play(mCurrentCanvas);
        invalidate(r);

        //mCurrentCanvas.drawBitmap(b, m, null);

        if (DEBUG) Log.d(TAG, String.format("paintBitmap(%s, %dx%d): bitmap=%s (%dx%d) mPreviousCanvas=%s",
            b.toString(), b.getWidth(), b.getHeight(),
            mPreviousBitmap.toString(), mPreviousBitmap.getWidth(), mPreviousBitmap.getHeight(),
            mPreviousCanvas.toString()));
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
        for (Plotter plotter : mStrokes) {
            // XXX: todo: only do this if the stroke hasn't begun already
            // ...or not; the current behavior allows RAINBOW MODE!!!1!
            plotter.setPenColor(color);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw,
            int oldh) {

        int curW = mPreviousBitmap != null ? mPreviousBitmap.getWidth() : 0;
        int curH = mPreviousBitmap != null ? mPreviousBitmap.getHeight() : 0;
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
            Log.d(TAG, "old bitmap: " + mPreviousBitmap);
            Log.d(TAG, "created bitmap " + curW + "x" + curH + ": " + newBitmap);
        }
        Canvas newCanvas = new Canvas();
        newCanvas.setBitmap(newBitmap);
        if (mPreviousBitmap != null) {
            // copy the old bitmap in? doesn't seem to work
            newCanvas.drawBitmap(mPreviousBitmap, 0, 0, null);
        }
        if (mCurrentBitmap != null && !mCurrentBitmap.isRecycled()) mCurrentBitmap.recycle();
        mCurrentBitmap = newBitmap;
        mCurrentCanvas = newCanvas;

        if (mPreviousBitmap != null && !mPreviousBitmap.isRecycled()) mPreviousBitmap.recycle();
        mPreviousBitmap = Bitmap.createBitmap(curW, curH, Bitmap.Config.ARGB_8888);
        mPreviousCanvas = new Canvas();
        mPreviousCanvas.setBitmap(mPreviousBitmap);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mCurrentBitmap != null) {
            canvas.drawBitmap(mCurrentBitmap, 0, 0, null);

//            if (0 != mDebugFlags) {
//                canvas.drawRect(
            if (0 != (mDebugFlags & FLAG_DEBUG_PRESSURE)) {
                StringBuffer strokeInfo = new StringBuffer();
                /*for (SmoothStroker st : mStrokes) {
                    final float r = st.getRadius();
                    strokeInfo.append(
                        (r < 0)
                            ? "[-] "
                            : String.format("[%.1f] ", r));
                }*/

                canvas.drawText(
                        String.format("p: %.2f (range: %.2f-%.2f) (recent: %.2f-%.2f) recal: %d", 
                            mLastPressure,
                            mPressureMin, mPressureMax,
                            mPressureRecentMin, mPressureRecentMax,
                            mPressureUpdateCountdown),
                        52, canvas.getHeight() - 64, mDebugPaints[4]);
                canvas.drawText(strokeInfo.toString(), 52, canvas.getHeight() - 52, mDebugPaints[4]);
            }
        }
    }

    float dbgX = -1, dbgY = -1;
    RectF dbgRect = new RectF();
    private boolean mZoomMode = false;
    private float mViewportTranslationX;
    private float mViewportTranslationY;
    private float mZoomTouchY;
    private float mZoomTouchX;
    
    static boolean hasPointerCoords() {
    	return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR_MR1);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int N = event.getHistorySize();
        int P = event.getPointerCount();
        long time = event.getEventTime();
        
        if (mZoomMode) {
            if (action == MotionEvent.ACTION_DOWN) {
                mZoomTouchX = event.getX() - mViewportTranslationX;
                mZoomTouchY = event.getY() - mViewportTranslationY;
            } else if (action == MotionEvent.ACTION_MOVE) {
                setViewportTranslation(event.getX() - mZoomTouchX, event.getY() - mZoomTouchY);
            }
            Log.d(TAG, "zoom mode (action="
                    + action
                    +"): xlation=(" + mViewportTranslationX + ", " + mViewportTranslationY + ")");
            return true;
        }

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
            mTmpSpot.x -= mViewportTranslationX;
            mTmpSpot.y -= mViewportTranslationY;
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
                    mTmpSpot.x -= mViewportTranslationX;
                    mTmpSpot.y -= mViewportTranslationY;
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
            	mTmpSpot.x -= mViewportTranslationX;
                mTmpSpot.y -= mViewportTranslationY;
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
                mStream.setUndoMark(time);
            }
            dbgX = dbgY = -1;
        }
        return true;
    }

    private void setViewportTranslation(float x, float y) {
        mViewportTranslationX = x;
        mViewportTranslationY = y;
        
        mPreviousCanvas.setMatrix(null);
        mPreviousCanvas.translate(x,y);
        mCurrentCanvas.setMatrix(null);
        mCurrentCanvas.translate(x,y);
        
        mCurrentCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        mStream.setDrawingStep(0);
        mStream.play(mCurrentCanvas);
        
        invalidate();
    }

    public static float lerp(float a, float b, float f) {
        return a + f * (b - a);
    }

    public void setZoomMode(boolean b) {
        mZoomMode = b;
    }
    
    @Override
    public void invalidate(Rect r) {
        if (r.isEmpty()) {
            Log.w(TAG, "invalidating empty rect!");
        }
        super.invalidate(r);
    }
}

