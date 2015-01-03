/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.util.Log;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.*;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class WearableService extends WearableListenerService {
    private static final String TAG = "Markers.WearableService";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "onCreate");
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d(TAG, "onDataChanged: " + dataEvents);

        final List<DataEvent> events = FreezableUtils
                .freezeIterable(dataEvents);

        GoogleApiClient googleApiClient = WearableUtils.getApiClient(this);

        ConnectionResult connectionResult =
                googleApiClient.blockingConnect(30, TimeUnit.SECONDS);

        if (!connectionResult.isSuccess()) {
            Log.e(TAG, "Failed to connect to GoogleApiClient.");
            return;
        }

        for (DataEvent event : events) {
            DataItem item = event.getDataItem();
            Log.v(TAG, "got data item at " + item.getUri() + ": " + item);

            final DataItemAsset dia = item.getAssets().get("drawing");
            if (dia == null) return;

            final PendingResult<DataApi.GetFdForAssetResult> result
                    = Wearable.DataApi.getFdForAsset(googleApiClient, dia);
            final InputStream assetStream = result.await().getInputStream();
            final Bitmap bitmap = BitmapFactory.decodeStream(assetStream);

            // resume Markers privileges
            long token = Binder.clearCallingIdentity();
            try {
                MediaClient.saveBitmap(this, bitmap, true,
                        null, -1, -1,
                        MarkersUtils.createDrawingFilename(), false, false, false, false, null);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        googleApiClient.disconnect();
    }
}
