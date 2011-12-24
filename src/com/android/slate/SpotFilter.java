package com.android.slate;

import java.util.Iterator;
import java.util.LinkedList;

import android.view.MotionEvent;

public class SpotFilter {
    public static boolean DEBUG = true;
    
    public static boolean PRECISE_STYLUS_INPUT = true;

    public static interface Plotter {
        public void plot(Spot s);
    }

    LinkedList<Spot> mSpots; // NOTE: newest now at front
    int mBufSize;
    Plotter mPlotter;
    Spot tmpSpot = new Spot();
    float mDecay;

    public SpotFilter(int size, float decay, Plotter plotter) {
        mSpots = new LinkedList<Spot>();
        mBufSize = size;
        mPlotter = plotter;
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
            
            if (PRECISE_STYLUS_INPUT && pi.tool == MotionEvent.TOOL_TYPE_STYLUS) {
                // just take the top one, no need to average
                break;
            }
        }

        out.x = x / w;
        out.y = y / w;
        out.pressure = pressure / w;
        out.size = size / w;
        out.time = time;
        out.tool = mSpots.get(0).tool;
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
        mPlotter.plot(tmpSpot);
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
            mPlotter.plot(tmpSpot);
        }

        mSpots.clear();
    }
}


