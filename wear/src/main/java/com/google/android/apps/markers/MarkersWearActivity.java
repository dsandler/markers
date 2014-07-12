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

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

public class MarkersWearActivity extends Activity {
    private static final String TAG = "Markers";

    public static final boolean DEBUG = false;

    private static MicroSlateView mSlate;
    private static DismissView mDismisser;

    private static class MicroSlateView extends View {
        private final PressureCooker mPressureCooker;

        private Paint mPaint, mBlitPaint, mPointerPaint;
        private Bitmap mBitmap, mDrawingBitmap;
        private Canvas mCanvas, mDrawingCanvas;

        private float lx, ly, lr;
        private float sx, sy;
        private long downTime;
        private boolean mAbortDraw;

        private static final float LONGPRESS_SLOP = 3; // dp
        private static final long LONGPRESS_DURATION = 1000;

        private float dp;

        private Runnable mCheckLongpress = new Runnable() {
            @Override
            public void run() {
                if (SystemClock.uptimeMillis() - downTime > LONGPRESS_DURATION) {
                    mAbortDraw = true;
                    mDismisser.show();
                }
            }
        };

        public MicroSlateView(Context context) {
            this(context, null);
        }

        public MicroSlateView(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public MicroSlateView(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);

            mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mPaint.setColor(Color.BLACK);
            mBlitPaint = new Paint();
            mBlitPaint.setColor(Color.BLACK);
            mPointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mPointerPaint.setStyle(Paint.Style.STROKE);
            mPointerPaint.setStrokeWidth(2);
            mPointerPaint.setColor(0x80808080);

            mPressureCooker = new PressureCooker(getContext(),
                    0f, 0.25f); // HACK: default pressure values adjusted for Wear

            setWillNotDraw(false);
        }

        @Override
        protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
            Log.v(TAG, String.format("layout: (%d x %d)", getWidth(), getHeight()));
            super.onLayout(changed, left, top, right, bottom);
        }

        @Override
        public void onSizeChanged(int w, int h, int ow, int oh) {
            Log.v(TAG, String.format("resize: (%d x %d) -> (%d x %d)", ow, oh, w, h));
            if (w > 0 && h > 0) {
                final Bitmap oldBitmap = mBitmap;
                mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Log.v(TAG, String.format("new bitmap: (%d x %d)", mBitmap.getWidth(), mBitmap.getHeight()));
                mCanvas = new Canvas(mBitmap);
                if (oldBitmap != null) {
                    mCanvas.drawBitmap(oldBitmap, 0, 0, mBlitPaint);
                    oldBitmap.recycle();
                }

                mDrawingBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                mDrawingCanvas = new Canvas(mDrawingBitmap);
            }

            dp = getResources().getDisplayMetrics().density;
        }

        static float lerp(float a, float b, float t) {
            return a + (b-a)*t;
        }

        static float unlerp(float a, float b, float x) {
            return (x-a) / (b-a);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (mCanvas == null) return false;
            final float x = event.getX(0);
            final float y = event.getY(0);
            final float p = Math.max(0, mPressureCooker.getAdjustedPressure(event.getPressure(0)));
            final float s = event.getSize(0);
            final float r = lerp(2, 20, p) * dp;
            final float d = (float) Math.hypot(x-lx, y-ly);
            final float step = 1f/d;
            if (DEBUG) Log.v(TAG, String.format("touch: (%.1f,%.1f) p=%.3f s=%.3f", x, y, p, s));
            final int action = event.getAction();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    sx = x;
                    sy = y;
                    downTime = event.getEventTime();
                    mAbortDraw = false;
                    mDrawingCanvas.drawCircle(x, y, r, mPaint);
                    postDelayed(mCheckLongpress, LONGPRESS_DURATION);
                    break;
                case MotionEvent.ACTION_MOVE:
                case MotionEvent.ACTION_UP:
                    if (Math.abs(x - sx + y - sy) > LONGPRESS_SLOP) {
                        removeCallbacks(mCheckLongpress);
                    }
                    // fall through
                    for (float t=step; t<1; t+=step) {
                        mDrawingCanvas.drawCircle(lerp(lx, x, t),
                                           lerp(ly, y, t),
                                           lerp(lr, r, t),
                                           mPaint);
                    }
                    mDrawingCanvas.drawCircle(x, y, r, mPaint);
                    invalidate();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    mAbortDraw = true;
                    break;
            }

            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                lx = x;
                ly = y;
                lr = r;
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                lx = ly = lr = 0;
                removeCallbacks(mCheckLongpress);
                if (!mAbortDraw) {
                    mCanvas.drawBitmap(mDrawingBitmap, 0, 0, mBlitPaint);
                }
                clearCanvas(mDrawingCanvas);
            }
            return true;
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (mBitmap != null) {
                //c.drawColor(Color.WHITE);
                c.drawBitmap(mBitmap, 0, 0, mBlitPaint);
                c.drawBitmap(mDrawingBitmap, 0, 0, mBlitPaint);
            } else {
                c.drawColor(0xFFFF0000);
            }
            if (lr > 0) {
                c.drawCircle(lx, ly, lr, mPointerPaint);
            }

            if (DEBUG) {
                mPressureCooker.drawDebug(c, 4, c.getHeight() - 4);
            }
        }

        public void reset() {
            lx = ly = lr = 0;
            clearCanvas(mCanvas);
            invalidate();
        }
    }

    private static void clearCanvas(Canvas c) {
        c.drawColor(0, PorterDuff.Mode.SRC);
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        Log.d(TAG, "onCreate");

        mSlate = new MicroSlateView(this);
        mDismisser = new DismissView(this);

        FrameLayout fl = new FrameLayout(this);
        fl.addView(mSlate);
        fl.addView(mDismisser);

        setContentView(fl);
    }

    @Override
    public void onResume() {
        super.onResume();

    }

    @Override
    public void onPause() {
        super.onPause();
        mSlate.reset();
    }
}
