/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class PenWidthEditorView extends View {
    static final float WIDTH_MIN = 0.25f; // px
    static final float WIDTH_MAX = 256f; // px
    static final float TEXT_DP = 16;

    private float mTextSize;
    private float mTouchFudge;

    private Paint mPaint, mLabelPaint, mPaintTouching;
    private float strokeWidthMin, strokeWidthMax;
    private ToolButton.PenToolButton mTool;
    private boolean mDown = false;
    private boolean mTouchingMin = false;
    private boolean mTouchingMax = false;

    public PenWidthEditorView(Context context) {
        this(context, null);
    }

    public PenWidthEditorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PenWidthEditorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final float density = getResources().getDisplayMetrics().density;
        mTextSize = density * TEXT_DP;

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(0xFF666666);
        mLabelPaint = new Paint();
        mLabelPaint.setTextSize(mTextSize);
        mLabelPaint.setColor(0xFFFF0000);
        mLabelPaint.setTextAlign(Paint.Align.CENTER);
        mPaintTouching = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintTouching.setStyle(Paint.Style.STROKE);
        mPaintTouching.setColor(0xFFFF0000);
        mPaintTouching.setStrokeWidth(density * 2);

        strokeWidthMin = 1;
        strokeWidthMax = 20;

        mTouchFudge = 5 * density; // make the radius this many dp smaller
    }

    PointF getStartPoint() {
        final boolean vertical = getHeight() > getWidth();
        final float center = (vertical ? getWidth() : getHeight()) / 2;
        final float quarter = (vertical ? getPaddingTop() : getPaddingLeft())
                + WIDTH_MAX*0.5f;
        return new PointF(vertical ? center : quarter, vertical ? quarter : center);
    }

    PointF getEndPoint() {
        final boolean vertical = getHeight() > getWidth();
        final float center = (vertical ? getWidth() : getHeight()) / 2;
        final float quarter = (vertical ? getHeight() - getPaddingBottom() : getWidth() - getPaddingRight())
                - WIDTH_MAX*0.5f;
        return new PointF(vertical ? center : quarter, vertical ? quarter : center);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float r1 = strokeWidthMin * 0.5f;
        float r2 = strokeWidthMax * 0.5f;

        final boolean vertical = getHeight() > getWidth();
        final PointF startP = getStartPoint();
        final PointF endP = getEndPoint();

        final float center = (vertical ? getWidth() : getHeight()) / 2;
        final float amplitude = (center-r2)*0.5f;

        r1 = Slate.clamp(0.5f*WIDTH_MIN, 0.5f*WIDTH_MAX, r1);
        r2 = Slate.clamp(0.5f*WIDTH_MIN, 0.5f*WIDTH_MAX, r2);

        final float iter = Math.min(8f,r1) / (vertical ? getHeight() : getWidth());

        for (float f = 0f; f < 1.0f; f += iter) {
            final float y = Slate.lerp(vertical ? startP.y : startP.x,
                                       vertical ? endP.y   : endP.x,
                                       f);
            final float x = (float) (center + amplitude*Math.sin(f * 2*Math.PI));
            final float r = Slate.lerp(r1, r2, f);
            canvas.drawCircle(vertical ? x : y, vertical ? y : x, r, mPaint);
        }
        canvas.drawCircle(endP.x, endP.y, r2, mPaint);

        if (mTouchingMin) {
            canvas.drawCircle(startP.x, startP.y, r1, mPaintTouching);
            final float xoff = vertical?0:r1;
            final float yoff = vertical?r1:0;
            canvas.drawCircle(startP.x, startP.y, r1, mPaintTouching);
            //canvas.drawLine(startP.x, startP.y, startP.x - xoff, startP.y - yoff, mPaintTouching);
        } else if (mTouchingMax) {
            final float xoff = vertical?0:r2;
            final float yoff = vertical?r2:0;
            canvas.drawCircle(endP.x, endP.y, r2, mPaintTouching);
            //canvas.drawLine(endP.x, endP.y, endP.x + xoff, endP.y + yoff, mPaintTouching);
        }

        final String minStr = String.format((strokeWidthMin < 3) ? "%.1f" : "%.0f",
                strokeWidthMin);
        final String maxStr = String.format((strokeWidthMax < 3) ? "%.1f" : "%.0f",
                strokeWidthMax);
        float textoff1, textoff2;
        if (r1 < 2*mTextSize) {
            textoff1 = r1+mTextSize*1.25f;
            mLabelPaint.setColor(0xFF000000);
        } else {
            textoff1 = 0;
            mLabelPaint.setColor(0xFFFFFFFF);
        }
        canvas.drawText(minStr,
                startP.x - (vertical?0:textoff1),
                startP.y - (vertical?textoff1:0) + mTextSize*.3f,
                mLabelPaint);
        if (r2 < 2*mTextSize) {
            textoff2 = r2+mTextSize*1.25f;
            mLabelPaint.setColor(0xFF000000);
        } else {
            textoff2 = 0;
            mLabelPaint.setColor(0xFFFFFFFF);
        }
        canvas.drawText(maxStr,
                endP.x + (vertical?0:textoff2),
                endP.y + (vertical?textoff2:0) + mTextSize*.3f,
                mLabelPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getAction();
        final boolean vertical = getHeight() > getWidth();
        final float len_axis = vertical ? getHeight() : getWidth();
        final float len_cross = vertical ? getWidth() : getHeight();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mDown = true;
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                mDown = false;
                mTouchingMin = mTouchingMax = false;
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                float dist = 0f;
                final PointF start = getStartPoint();
                final PointF end = getEndPoint();
                final float x = event.getX();
                final float y = event.getY();
                float d1 = (float) Math.hypot(x - start.x, y - start.y);
                float d2 = (float) Math.hypot(x - end.x, y - end.y);
                if (!mTouchingMin && !mTouchingMax) {
                    mTouchingMin = d1 < d2;
                    mTouchingMax = !mTouchingMin;
                }

                dist = mTouchingMin ? d1 : d2;
                dist = Slate.clamp(0.5f*WIDTH_MIN, 0.5f*WIDTH_MAX, dist - mTouchFudge);
                // make it easier to target finer weights more exactly
                dist = (float) Math.pow(dist/(0.5f*WIDTH_MAX),3)*0.5f*WIDTH_MAX;

                if (mTouchingMin || mTouchingMax) {
                    if (mTouchingMin) {
                        strokeWidthMin = Math.min(
                                dist * 2,
                                strokeWidthMax);
                    } else if (mTouchingMax) {
                        strokeWidthMax = Math.max(
                                dist * 2,
                                strokeWidthMin);
                    }
                    mTool.setWidths(strokeWidthMin, strokeWidthMax);

                    invalidate();
                }
                break;
        }
        return true;
    }

    public void setTool(ToolButton.PenToolButton view) {
        mTool = view;
        strokeWidthMin = view.strokeWidthMin;
        strokeWidthMax = view.strokeWidthMax;
    }
}