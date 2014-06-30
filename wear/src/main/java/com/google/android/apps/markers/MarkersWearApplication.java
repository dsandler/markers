package com.google.android.apps.markers;

import android.app.Application;
import android.os.Build;
import android.os.StrictMode;

public class MarkersWearApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        if (!"user".equals(Build.TYPE)) {
            StrictMode.enableDefaults();
        }
    }
}
