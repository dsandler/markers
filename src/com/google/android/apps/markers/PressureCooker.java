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

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Log;

class PressureCooker {
    private static final String PREFS_NAME = "Markers";
    
    private static final String PREF_MIN_DIAMETER = "min_diameter";
    private static final String PREF_MAX_DIAMETER = "max_diameter";
    private static final String PREF_PRESSURE_MIN = "pressure_min";
    private static final String PREF_PRESSURE_MAX = "pressure_max";
    private static final String PREF_FIRST_RUN = "first_run";

    private static final float DEF_PRESSURE_MIN = 0.2f;
    private static final float DEF_PRESSURE_MAX = 0.9f;

    private float mLastPressure;

    private float mPressureMin = 0;
    private float mPressureMax = 1;

    public static final float PRESSURE_UPDATE_DECAY = 0.1f;
    public static final int PRESSURE_UPDATE_STEPS_FIRSTBOOT = 100; // points, a quick-training regimen
    public static final int PRESSURE_UPDATE_STEPS_NORMAL = 1000; // points, in normal use

    private static final boolean PARTNER_HACK = false;
    
    private int mPressureCountdownStart = PRESSURE_UPDATE_STEPS_NORMAL;
    private int mPressureUpdateCountdown = mPressureCountdownStart;
    private float mPressureRecentMin = 1;
    private float mPressureRecentMax = 0;
    
    private Context mContext;
    
    public PressureCooker(Context context) {
        mContext = context;
        loadStats();
    }
    
    public void loadStats() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);

        mPressureMin = prefs.getFloat(PREF_PRESSURE_MIN, DEF_PRESSURE_MIN);
        mPressureMax = prefs.getFloat(PREF_PRESSURE_MAX, DEF_PRESSURE_MAX);
        
        final boolean firstRun = prefs.getBoolean(PREF_FIRST_RUN, true);
        setFirstRun(firstRun);
    }

    public void saveStats() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
        SharedPreferences.Editor prefsE = prefs.edit();
        prefsE.putBoolean(PREF_FIRST_RUN, false);
    
        prefsE.putFloat(PREF_PRESSURE_MIN, mPressureMin);
        prefsE.putFloat(PREF_PRESSURE_MAX, mPressureMax);
    
        prefsE.commit();
    }
    
    // Adjusts pressure values on the fly based on historical maxima/minima.
    public float getAdjustedPressure(float pressure) {
        if (PARTNER_HACK) {
            return pressure; 
        }
        
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
            
            saveStats();
        }

        final float pressureNorm = (pressure - mPressureMin)
            / (mPressureMax - mPressureMin);

        /*
            Log.d(Slate.TAG, String.format("pressure=%.2f range=%.2f-%.2f obs=%.2f-%.2f pnorm=%.2f",
                pressure, mPressureMin, mPressureMax, mPressureRecentMin, mPressureRecentMax, pressureNorm));
        */

        return pressureNorm;
    }
    
    static final Paint mDebugPaint;
    static {
        mDebugPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDebugPaint.setColor(0xFFFF0000);
    }
    
    public void drawDebug(Canvas canvas) {
        if (PARTNER_HACK) return;
        canvas.drawText(
              String.format("[pressurecooker] pressure: %.2f (range: %.2f-%.2f) (recent: %.2f-%.2f) recal: %d", 
                      mLastPressure,
                      mPressureMin, mPressureMax,
                      mPressureRecentMin, mPressureRecentMax,
                      mPressureUpdateCountdown),
                  96, canvas.getHeight() - 64, mDebugPaint);
    }

    public void setFirstRun(boolean firstRun) {
        if (firstRun) {
            // "Why do my eyes hurt?"
            // "You've never used them before."
            
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
}
