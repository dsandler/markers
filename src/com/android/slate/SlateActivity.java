package com.android.slate;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import android.media.MediaScannerConnection;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class SlateActivity extends Activity
{
    final static int LOAD_IMAGE = 1000;

    static final String TAG = "Markers";

    public static final String IMAGE_SAVE_DIRNAME = "Drawings";
    public static final String WIP_FILENAME = ".temporary.png";

    Slate mSlate;

    boolean mJustLoadedImage = false;

    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        setContentView(R.layout.main);
        mSlate = (Slate) findViewById(R.id.slate);
    
        if (icicle != null) {
            onRestoreInstanceState(icicle);
        }

        clickColor(findViewById(R.id.black));
    }

    @Override
    public void onAttachedToWindow() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mSlate.setDensity(metrics.density);
    }

    @Override
    protected void onStop() {
        saveDrawing(WIP_FILENAME);
        super.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mJustLoadedImage) {
            loadDrawing(WIP_FILENAME);
        } else {
            mJustLoadedImage = false;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle icicle) {
    }

    @Override
    protected void onRestoreInstanceState(Bundle icicle) {
    }

    public void clickClear(View v) {
        mSlate.clear();
    }

    public void loadDrawing(String filename) {
        File d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        d = new File(d, IMAGE_SAVE_DIRNAME);
        if (!d.exists()) {
            return;
        }
        Bitmap bits = BitmapFactory.decodeFile(new File(d, filename).toString());
        if (bits != null) {
            mSlate.setBitmap(bits);
        }
    }

    public String saveDrawing(String filename) {
        String fn = null;
        Bitmap bits = mSlate.getBitmap();

        try {
            File d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            d = new File(d, IMAGE_SAVE_DIRNAME);
            if (!d.exists()) {
                if (!d.mkdirs()) { throw new IOException("cannot create dirs: " + d); }
            }
            File file = new File(d, filename);
            Log.d(TAG, "save: saving " + file);
            OutputStream os = new FileOutputStream(file);
            bits.compress(Bitmap.CompressFormat.PNG, 0, os);
            os.close();
            fn = file.toString();
            MediaScannerConnection.scanFile(this,
                    new String[] { fn }, null, null
                    );
        } catch (IOException e) {
            Log.d(TAG, "save: error: " + e);
        }
        return fn;
    }

    public void clickSave(View v) {
        String fn = saveDrawing(System.currentTimeMillis() + ".png");
        if (fn != null) {
            Toast.makeText(this, "Saved to " + fn, Toast.LENGTH_SHORT).show();
        }
    }

    public void clickLoad(View v) {
        Intent i = new Intent(Intent.ACTION_PICK,
                       android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        startActivityForResult(i, LOAD_IMAGE); 
    }
    public void clickDebug(View v) {
        mSlate.setDebugFlags(mSlate.getDebugFlags() == 0 ? Slate.FLAG_DEBUG_STROKES : 0);
        Toast.makeText(this, "Debug mode " + ((mSlate.getDebugFlags() == 0) ? "off" : "on"),
            Toast.LENGTH_SHORT).show();
    }
    public void clickColor(View v) {
        int color = 0xFF000000;
        switch (v.getId()) {
            case R.id.black:  color = 0xFF000000; break;
            case R.id.white:  color = 0xFFFFFFFF; break;
            case R.id.lgray:  color = 0xFFC0C0C0; break;
            case R.id.gray:   color = 0xFF808080; break;
            case R.id.dgray:  color = 0xFF404040; break;

            case R.id.red:    color = 0xFFFF0000; break;
            case R.id.green:  color = 0xFF00FF00; break;
            case R.id.blue:   color = 0xFF0000FF; break;

            case R.id.pink:   color = 0xFFFF00CC; break;
            case R.id.orange: color = 0xFFFF8000; break;
            case R.id.yellow: color = 0xFFFFFF00; break;
            case R.id.purple: color = 0xFF6000A0; break;

            case R.id.brown:  color = 0xFF804000; break;
        }
        setPenColor(color);

        ViewGroup list = (ViewGroup) findViewById(R.id.colors);
        float dip = getResources().getDisplayMetrics().density;
        for (int i=0; i<list.getChildCount(); i++) {
            Button c = (Button) list.getChildAt(i);
            c.setText(c==v?"\u25A0":"");
        }
    }

    public void clickUndo(View v) {
        mSlate.undo();
    }

    public void setPenColor(int color) {
        mSlate.setPenColor(color);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent imageReturnedIntent) { 
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent); 

        switch (requestCode) { 
        case LOAD_IMAGE:
            if (resultCode == RESULT_OK) {  
                Uri contentUri = imageReturnedIntent.getData();
                Toast.makeText(this, "Loading from " + contentUri, Toast.LENGTH_SHORT).show();

                loadDrawing(WIP_FILENAME);
                mJustLoadedImage = true;

                try {
                    Bitmap b = MediaStore.Images.Media.getBitmap(getContentResolver(), contentUri);
                    if (b != null) {
                        mSlate.paintBitmap(b);
                        Log.d(TAG, "successfully loaded bitmap: " + b);
                    } else {
                        Log.e(TAG, "couldn't get bitmap from " + contentUri);
                    }
                } catch (java.io.FileNotFoundException ex) {
                    Log.e(TAG, "error loading image from " + contentUri + ": " + ex);
                } catch (java.io.IOException ex) {
                    Log.e(TAG, "error loading image from " + contentUri + ": " + ex);
                }
            }
        }
    }
    
}
