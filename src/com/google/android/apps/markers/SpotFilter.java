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
    private float mPosDecay;
    private float mPressureDecay;

    public SpotFilter(int size, float posDecay, float pressureDecay, Plotter plotter) {
        mSpots = new LinkedList<Spot>();
        mBufSize = size;
        mPlotter = plotter;
        mPosDecay = (posDecay >= 0 && posDecay <= 1) ? posDecay : 1f;
        mPressureDecay = (pressureDecay >= 0 && pressureDecay <= 1) ? pressureDecay : 1f;
    }

    public Spot filteredOutput(Spot out) {
        if (out == null) out = new Spot();
        
        float wi = 1, w = 0;
        float wi_press = 1, w_press = 0;
        float x = 0, y = 0, pressure = 0, size = 0;
        long time = 0;
        for (Spot pi : mSpots) {
            x += pi.x * wi;
            y += pi.y * wi;
            time += pi.time * wi;
            
            pressure += pi.pressure * wi_press;
            size += pi.size * wi_press;

            w += wi;
            wi *= mPosDecay; // exponential backoff

            w_press += wi_press;
            wi_press *= mPressureDecay;

            if (PRECISE_STYLUS_INPUT && pi.tool == MotionEvent.TOOL_TYPE_STYLUS) {
                // just take the top one, no need to average
                break;
            }
        }

        out.x = x / w;
        out.y = y / w;
        out.pressure = pressure / w_press;
        out.size = size / w_press;
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


