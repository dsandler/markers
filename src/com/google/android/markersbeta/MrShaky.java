package com.android.slate;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.os.SystemClock;

import android.util.Log;

public class MrShaky implements SensorEventListener {
    private final static float SHAKE_THRESH = 10.0f; // m/s^2
    private final static float SHAKE_DURATION = 590; // msec
    private final static float SHAKE_GAP = 130; // msec

    private final static String TAG = "MrShaky";
    private final static boolean DEBUG = false;

    public interface Listener {
        public void onShake();
    }

    private final SensorManager mSM;
    private final Sensor mLA;

    private float mCurrentMagnitude;

    private long mShakeStart;
    private long mRisingEdge;
    private long mFallingEdge;

    public final Listener mListener;

    public MrShaky(Context context, Listener listener) {
         mSM = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
         mLA = mSM.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
         mListener = listener;
         resume();
    }

    public void resume() {
         mSM.registerListener(this, mLA, SensorManager.SENSOR_DELAY_UI);
         mShakeStart = mRisingEdge = mFallingEdge = -1;
    }

    public void pause() {
         mSM.unregisterListener(this);
    }

    public float getCurrentMagnitude() {
        return mCurrentMagnitude;
    }

    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            final float hypot = 
                (float) Math.sqrt(
                    Math.pow(event.values[0], 2) +
                    Math.pow(event.values[1], 2) +
                    Math.pow(event.values[2], 2));

            mCurrentMagnitude = hypot;

            final boolean shaking = hypot > SHAKE_THRESH;
            final long now = SystemClock.uptimeMillis();

            final String stars = "********************";
            if (DEBUG) Log.d(TAG, String.format("accel: [%" + stars.length() + "s] (%.2f, %.2f, %.2f) hypot: %.2f %s %s %s",
                stars.substring(0, (int) Math.min(hypot, stars.length()-1)),
                event.values[0], event.values[1], event.values[2], hypot,
                (mShakeStart > 0 ? (now-mShakeStart + "ms") : "~"),
                (mRisingEdge > 0 ? (now-mRisingEdge + "ms") : "~"),
                shaking ? "<<< >>>" : ""
                ));

            if (shaking) {
                if (mRisingEdge < 0) {
                    if (DEBUG) Log.d(TAG, "shaking... (gap=" + (now - mFallingEdge) + "ms)");
                    mFallingEdge = -1;
                    mRisingEdge = now;
                    if (mShakeStart < 0) mShakeStart = now;
                }
                if (now - mShakeStart > SHAKE_DURATION) {
                    mListener.onShake();
                    mShakeStart = mRisingEdge = -1; // reset
                    if (DEBUG) Log.d(TAG, "SHAKE DETECTED");
                }
            } else {
                if (mFallingEdge < 0) {
                    // we just stopped shaking
                    if (DEBUG) Log.d(TAG, "shaking paused (dur=" + (now - mRisingEdge) + "ms)");
                    mFallingEdge = now;
                    mRisingEdge = -1;
                } else if (mShakeStart > 0 && now - mFallingEdge > SHAKE_GAP) {
                    // been quiet too long to consider the next shake continuous
                    if (DEBUG) Log.d(TAG, "shaking stopped due to gap (dur=" + (mFallingEdge - mShakeStart) + "ms)");
                    mRisingEdge = mShakeStart = -1;
                }
            }
        }
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
