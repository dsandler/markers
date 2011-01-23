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
import java.util.LinkedList;

public class CoordBuffer {
    public static boolean DEBUG = true;

    public static interface Stroker {
        public void drawPoint(float x, float y, float pressure, float width);
    }

//        Bitmap mWorkBits;
//        Canvas mWork, mDestCanvas;
    LinkedList<MotionEvent.PointerCoords> mCoords;
    int mBufSize;
    Stroker mStroker;

    public CoordBuffer(int size, Stroker stroker) {
        mCoords = new LinkedList<MotionEvent.PointerCoords>();
        mBufSize = size;
        mStroker = stroker;
//            mDestCanvas = dest;
//            mWorkBits = Bitmap.createBitmap(
//                dest.getWidth(),
//                dest.getHeight(),
//                Bitmap.Config.ALPHA_8);
//            mWorkCanvas = new Canvas();
//            mWorkCanvas.setBitmap(mWorkBits);
    }

    public void commitOldestCoord() {
        float x = 0, y = 0, pressure = 0, size = 0;
        int N = mCoords.size();
        for (MotionEvent.PointerCoords p : mCoords) {
            x += p.x; y += p.y; pressure += p.pressure; size += p.size;
        }
        mStroker.drawPoint(x / N, y / N, pressure / N, size / N);
        mCoords.removeFirst();
    }

    public void add(MotionEvent.PointerCoords _c) {
        if (mCoords.size() == mBufSize) {
            // drop the first point
            commitOldestCoord();
        }

        MotionEvent.PointerCoords c = new MotionEvent.PointerCoords();
        c.x = _c.x;
        c.y = _c.y;
        c.size = _c.size;
        c.pressure = _c.pressure;
        // TODO: other fields

        mCoords.add(c);

    }

    public void add(MotionEvent.PointerCoords[] cv) {
        for (MotionEvent.PointerCoords c : cv) {
            add(c);
        }
    }

    public void finish() {
        while (mCoords.size() > 0) {
            commitOldestCoord();
        }

        mCoords.clear();
    }
}


