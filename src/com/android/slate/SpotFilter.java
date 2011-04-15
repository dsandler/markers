package com.android.slate;

import java.util.Iterator;
import java.util.LinkedList;

import android.view.MotionEvent;

public class SpotFilter {
    public static boolean DEBUG = true;

    public static interface Stroker {
        public void drawPoint(float x, float y, float pressure, float size, long time);
    }

    LinkedList<Spot> mSpots; // NOTE: newest now at front
    int mBufSize;
    Stroker mStroker;
    Spot tmpSpot = new Spot();
    float mDecay;

    public SpotFilter(int size, float decay, Stroker stroker) {
        mSpots = new LinkedList<Spot>();
        mBufSize = size;
        mStroker = stroker;
        mDecay = (decay >= 0 && decay <= 1) ? decay : 1f;
    }

    public Spot filteredOutput(Spot out) {
        if (out == null) out = new Spot();

        float wi = 1, w = 0;
        float x = 0, y = 0, pressure = 0, size = 0;
        long time = 0;
        for (Spot pi : mSpots) {
            x += pi.x * wi;
            y += pi.y * wi;
            pressure += pi.pressure * wi;
            size += pi.size * wi;
            time += pi.time * wi;

            w += wi;

            wi *= mDecay; // exponential backoff
        }

        out.x = x / w;
        out.y = y / w;
        out.pressure = pressure / w;
        out.size = size / w;
        out.time = time;
        return out;
    }

    public void add(MotionEvent.PointerCoords c, long time) {
    	addNoCopy(new Spot(c, time));
    }
    
    public void add(Spot c) {
    	addNoCopy(new Spot(c));
    }
    
    protected void addNoCopy(Spot c) {
        if (mSpots.size() == mBufSize) {
            mSpots.removeLast();
        }

        mSpots.add(0, c);

        tmpSpot = filteredOutput(tmpSpot);
        mStroker.drawPoint(tmpSpot.x, tmpSpot.y, tmpSpot.pressure, tmpSpot.size, tmpSpot.time);
    }

    public void add(MotionEvent.PointerCoords[] cv, long time) {
        for (MotionEvent.PointerCoords c : cv) {
            add(c, time);
        }
    }

    public void finish() {
        while (mSpots.size() > 0) {
            tmpSpot = filteredOutput(tmpSpot);
            mSpots.removeLast();
            mStroker.drawPoint(tmpSpot.x, tmpSpot.y, tmpSpot.pressure, tmpSpot.size, tmpSpot.time);
        }

        mSpots.clear();
    }
}


