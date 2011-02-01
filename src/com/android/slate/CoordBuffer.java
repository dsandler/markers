package com.android.slate;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
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
import android.media.MediaScannerConnection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;

public class CoordBuffer {
    public static boolean DEBUG = true;

    public boolean mUseVelocity = false;

    public static interface Stroker {
        public void drawPoint(float x, float y, float pressure, float width);
    }

    LinkedList<MotionEvent.PointerCoords> mCoords;
    int mBufSize;
    Stroker mStroker;
    MotionEvent.PointerCoords tmpCoord = new MotionEvent.PointerCoords();
    float mDecay;

    public CoordBuffer(int size, float decay, Stroker stroker) {
        mCoords = new LinkedList<MotionEvent.PointerCoords>();
        mBufSize = size;
        mStroker = stroker;
        mDecay = (decay >= 0 && decay <= 1) ? decay : 1f;
    }

    public MotionEvent.PointerCoords filteredOutput(MotionEvent.PointerCoords out) {
        if (out == null) out = new MotionEvent.PointerCoords();

        float wi = 1, w = 0;
        float x = 0, y = 0, pressure = 0, size = 0;
        MotionEvent.PointerCoords pi = null, pi_1 = null, pi_2 = null;
        Iterator<MotionEvent.PointerCoords> iter = mCoords.descendingIterator();
        while (iter.hasNext()) {
            pi_2 = pi_1;
            pi_1 = pi;
            pi = iter.next();
            x += pi.x * wi;
            y += pi.y * wi;
            pressure += pi.pressure * wi;
            size += pi.size * wi;
            w += wi;

            if (mUseVelocity && pi_2 != null) {
                x += (2*pi_1.x - pi_2.x);
                y += (2*pi_1.y - pi_2.y);
                pressure += (2*pi_1.pressure - pi_2.pressure);
                size += (2*pi_1.size - pi_2.size);
                w += 1;
            }

            wi *= mDecay; // exponential backoff
        }

        out.x = x / w;
        out.y = y / w;
        out.pressure = pressure / w;
        out.size = size / w;
        return out;
    }

    public static MotionEvent.PointerCoords copyCoord(MotionEvent.PointerCoords _c) {
        MotionEvent.PointerCoords c = new MotionEvent.PointerCoords();
        c.x = _c.x;
        c.y = _c.y;
        c.size = _c.size;
        c.pressure = _c.pressure;
        // TODO: other fields
        return c;
    }

    public void add(MotionEvent.PointerCoords c) {
        if (mCoords.size() == mBufSize) {
            mCoords.removeFirst();
        }

        mCoords.add(copyCoord(c));

        tmpCoord = filteredOutput(tmpCoord);
        mStroker.drawPoint(tmpCoord.x, tmpCoord.y, tmpCoord.pressure, tmpCoord.size);
    }

    public void add(MotionEvent.PointerCoords[] cv) {
        for (MotionEvent.PointerCoords c : cv) {
            add(c);
        }
    }

    public void finish() {
        while (mCoords.size() > 0) {
            tmpCoord = filteredOutput(tmpCoord);
            mCoords.removeFirst();
            mStroker.drawPoint(tmpCoord.x, tmpCoord.y, tmpCoord.pressure, tmpCoord.size);
        }

        mCoords.clear();
    }
}


