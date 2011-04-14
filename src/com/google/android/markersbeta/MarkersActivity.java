package com.google.android.markersbeta;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
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

import com.android.slate.Slate;

public class MarkersActivity extends Activity implements MrShaky.Listener
{
    final static int LOAD_IMAGE = 1000;

    static final String TAG = "Markers";

    public static final String IMAGE_SAVE_DIRNAME = "Drawings";
    public static final String WIP_FILENAME = ".temporary.png";

    private static final String PREFS_NAME = "MarkersPrefs";

    private static final String PREF_MIN_DIAMETER = "min_diameter";
    private static final String PREF_MAX_DIAMETER = "max_diameter";
    private static final String PREF_PRESSURE_MIN = "pressure_min";
    private static final String PREF_PRESSURE_MAX = "pressure_max";
    
    private static final float DEF_PRESSURE_MIN = 0.2f;
    private static final float DEF_PRESSURE_MAX = 0.9f;

    private static final int[] BUTTON_COLORS = {
        0xFF000000,
        0xFFFFFFFF,
        0xFFC0C0C0,0xFF808080,
        0xFF404040,0xFFFF0000,
        0xFF00FF00,0xFF0000FF,
        0xFFFF00CC,0xFFFF8000,
        0xFFFFFF00,0xFF6000A0,0xFF804000,
    };

    Slate mSlate;

    MrShaky mShaky;

    boolean mJustLoadedImage = false;

    public static class ColorList extends LinearLayout {
        public ColorList(Context c, AttributeSet as) {
            super(c, as);
        }
        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            return true;
        }
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
    	((ViewGroup)mSlate.getParent()).removeView(mSlate);
        return mSlate;
    }
    
    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        mShaky = new MrShaky(this, this);

        setContentView(R.layout.main);
        mSlate = (Slate) getLastNonConfigurationInstance();
        if (mSlate == null) {
        	mSlate = new Slate(this);
        }
        ((ViewGroup)findViewById(R.id.root)).addView(mSlate, 0);
    
        if (icicle != null) {
            onRestoreInstanceState(icicle);
        }

        final ViewGroup colors = (ViewGroup) findViewById(R.id.colors);
        colors.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                //Log.d(TAG, "onTouch: " + event);
                if (event.getAction() == MotionEvent.ACTION_DOWN
                        || event.getAction() == MotionEvent.ACTION_MOVE) {
                    int index = (int) (event.getX() / colors.getWidth() 
                            * colors.getChildCount());
                    //Log.d(TAG, "touch index: " + index);
                    if (index >= colors.getChildCount()) return false;
                    View button = colors.getChildAt(index);
                    clickColor(button);
                }
                return true;
            }
        });

        clickColor(colors.getChildAt(0));

        Resources res = getResources();
        float minDiameter = res.getDimension(R.dimen.default_pen_size_min);
        float maxDiameter = res.getDimension(R.dimen.default_pen_size_max);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
        
        minDiameter = prefs.getFloat(PREF_MIN_DIAMETER, minDiameter);
        maxDiameter = prefs.getFloat(PREF_MAX_DIAMETER, maxDiameter);
        mSlate.setPenSize(minDiameter, maxDiameter);
        
        float pMin = prefs.getFloat(PREF_PRESSURE_MIN, DEF_PRESSURE_MIN);
        float pMax = prefs.getFloat(PREF_PRESSURE_MAX, DEF_PRESSURE_MAX);
        mSlate.setPressureRange(pMin, pMax);
    }

    // MrShaky.Listener
    public void onShake() {
        mSlate.undo();
    }

    public float getAccel() {
        return mShaky.getCurrentMagnitude();
    }

    @Override
    public void onPause() {
        super.onPause();
        mShaky.pause();
    }

    @Override
    public void onResume() {
        super.onResume();
        setRequestedOrientation(
        	(Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD)
        		? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        		: ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        mShaky.resume();
    }
    
    @Override
    public void onConfigurationChanged (Configuration newConfig) {
    	super.onConfigurationChanged(newConfig);
//    	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
//            setContentView(R.layout.main);
//            findViewById
//    	}
    }

    @Override
    public void onAttachedToWindow() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mSlate.setDensity(metrics.density);
    }

    @Override
    protected void onStop() {
        super.onStop();
        
        saveDrawing(WIP_FILENAME);
        mSlate.recycle();
        
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor prefsE = prefs.edit();
        float[] range = new float[2];
        mSlate.getPressureRange(range);
        prefsE.putFloat(PREF_PRESSURE_MIN, range[0]);
        prefsE.putFloat(PREF_PRESSURE_MAX, range[1]);
        prefsE.commit();
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
    
    final static boolean hasAnimations() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB);
    }
    
    public void clickLogo(View v) {
        final View bar = findViewById(R.id.actionbar_contents);
        final View logo = findViewById(R.id.logo);
        boolean showing = bar.getVisibility() == View.VISIBLE;
        if (showing) {
            if (hasAnimations()) {
                ObjectAnimator.ofFloat(logo, "alpha", 1f, 0.5f).start();
                ObjectAnimator.ofFloat(bar, "translationY", 0f, -20f).start();
                Animator a = ObjectAnimator.ofFloat(bar, "alpha", 1f, 0f);
                a.addListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator a) {
                        bar.setVisibility(View.GONE);
                    }
                });
                a.start();
            } else {
                bar.setVisibility(View.GONE);
            }
        } else {
            bar.setVisibility(View.VISIBLE);
            if (hasAnimations()) {
                ObjectAnimator.ofFloat(logo, "alpha", 0.5f, 1f).start();
                ObjectAnimator.ofFloat(bar, "translationY", -20f, 0f).start();
                ObjectAnimator.ofFloat(bar, "alpha", 0f, 1f).start();
            }
        }
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
        if (bits == null) {
            Log.e(TAG, "save: null bitmap");
            return null;
        }

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
            Log.e(TAG, "save: error: " + e);
        }
        return fn;
    }

    public void clickSave(View v) {
        v.setEnabled(false);
        String fn = saveDrawing(System.currentTimeMillis() + ".png");
        v.setEnabled(true);
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
        mSlate.setDebugFlags(mSlate.getDebugFlags() == 0 
            ? Slate.FLAG_DEBUG_EVERYTHING
            : 0);
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
