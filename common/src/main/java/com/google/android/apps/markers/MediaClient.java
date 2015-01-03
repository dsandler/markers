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

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;

public class MediaClient
        implements MediaScannerConnection.MediaScannerConnectionClient {
    public static String TAG = "Markers.MediaClient";
    public static boolean DEBUG = true;

    public static final String IMAGE_SAVE_DIRNAME = "Drawings";
    public static final String IMAGE_TEMP_DIRNAME = IMAGE_SAVE_DIRNAME + "/.temporary";
    public static final String WIP_FILENAME = "temporary.png";

    private LinkedList<String> mDrawingsToScan = new LinkedList<String>();

    protected MediaScannerConnection mMediaScannerConnection;
    private String mPendingShareFile;
    private Context mContext;

    private MediaClient(Context context) {
        mContext = context;

        mMediaScannerConnection = new MediaScannerConnection(context, this);
    }

    @Override
    public void onMediaScannerConnected() {
        if (DEBUG) Log.v(TAG, "media scanner connected");
        scanNext();
    }

    private void scanNext() {
        synchronized (mDrawingsToScan) {
            if (mDrawingsToScan.isEmpty()) {
                mMediaScannerConnection.disconnect();
                return;
            }
            String fn = mDrawingsToScan.removeFirst();
            mMediaScannerConnection.scanFile(fn, "image/png");
        }
    }

    @Override
    public void onScanCompleted(String path, Uri uri) {
        if (DEBUG) Log.v(TAG, "File scanned: " + path);
        synchronized (mDrawingsToScan) {
            if (path.equals(mPendingShareFile)) {
                Intent sendIntent = new Intent(Intent.ACTION_SEND);
                sendIntent.setType("image/png");
                sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
                mContext.startActivity(Intent.createChooser(sendIntent, "Send drawing to:"));
                mPendingShareFile = null;
            }
            scanNext();
        }
    }

    private static MediaClient sMediaClient;
    public static MediaClient get(Context context) {
        if (sMediaClient == null) {
            sMediaClient = new MediaClient(context);
        }
        return sMediaClient;
    }

    public void addFile(String fn, boolean _share) {
        synchronized(mDrawingsToScan) {
            mDrawingsToScan.add(fn);
            if (_share) {
                mPendingShareFile = fn;
            }
            if (!mMediaScannerConnection.isConnected()) {
                mMediaScannerConnection.connect(); // will scan the files and share them
            }
        }
    }


    public static void saveBitmap(final Context context, final Bitmap bitmap, final boolean dispose,
                                  final byte[] data, final int offset, final int length,
                                  final String _filename,
                                  final boolean _temporary, final boolean _animate, final boolean _share,
                                  final boolean _clear, final Slate slate) {


        new AsyncTask<Void,Void,String>() {
            @Override
            protected String doInBackground(Void... params) {
                String fn = null;
                try {
                    File d = MarkersUtils.getPicturesDirectory();
                    d = new File(d, _temporary ? IMAGE_TEMP_DIRNAME : IMAGE_SAVE_DIRNAME);
                    if (!d.exists()) {
                        if (d.mkdirs()) {
                            if (_temporary) {
                                final File noMediaFile = new File(d, MediaStore.MEDIA_IGNORE_FILENAME);
                                if (!noMediaFile.exists()) {
                                    new FileOutputStream(noMediaFile).write('\n');
                                }
                            }
                        } else {
                            throw new IOException("cannot create dirs: " + d);
                        }
                    }
                    File file = new File(d, _filename);
                    if (DEBUG) Log.d(TAG, "save: saving " + file);
                    OutputStream os = new FileOutputStream(file);
                    if (bitmap != null) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 0, os);
                        if (dispose) {
                            bitmap.recycle();
                        }
                    } else if (data != null && length > 0) {
                        os.write(data, offset, length);
                    } else {
                        throw new IllegalArgumentException("both bitmap and data are null!");
                    }
                    os.close();

                    fn = file.toString();
                } catch (IOException e) {
                    Log.e(TAG, "save: error: " + e);
                }
                return fn;
            }

            @Override
            protected void onPostExecute(String fn) {
                if (fn != null) {
                    MediaClient.get(context).addFile(fn, _share);
                }

                if (_clear && slate != null) slate.clear();
            }
        }.execute();

    }
}
