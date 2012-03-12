package com.google.android.markersbeta;

import android.animation.Animator;
import android.animation.AnimatorSet;
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
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnimationSet;
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
    public static final String IMAGE_TEMP_DIRNAME = IMAGE_SAVE_DIRNAME + "/.temporary";
    public static final String WIP_FILENAME = "temporary.png";

    private static final String PREFS_NAME = "MarkersPrefs";

    Slate mSlate;

    MrShaky mShaky;

    boolean mJustLoadedImage = false;

    protected ToolButton mLastTool, mActiveTool;

    View mDebugButton;

    protected ToolButton mLastColor, mActiveColor;

    public static class ColorList extends LinearLayout {
        public ColorList(Context c, AttributeSet as) {
            super(c, as);
        }
        
        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            int newOrientation = (((right-left) > (bottom-top)) ? HORIZONTAL : VERTICAL);
            if (newOrientation != getOrientation()) {
                setOrientation(newOrientation);
            }
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
    
    public static interface ViewFunc {
        void apply(View v);
    }
    public static void descend(ViewGroup parent, ViewFunc func) {
        for (int i=0; i<parent.getChildCount(); i++) {
            final View v = parent.getChildAt(i);
            if (v instanceof ViewGroup) {
                descend((ViewGroup) v, func);
            } else {
                func.apply(v);
            }
        }
    }

    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(getWindow().getAttributes());
        lp.format = PixelFormat.RGBA_8888;
        getWindow().setBackgroundDrawableResource(R.drawable.transparent);
        getWindow().setAttributes(lp);

        //Log.d(TAG, "window format: " + getWindow().getAttributes().format);
        
        mShaky = new MrShaky(this, this);
        
        setContentView(R.layout.main);
        mSlate = (Slate) getLastNonConfigurationInstance();
        if (mSlate == null) {
        	mSlate = new Slate(this);
        }
        final ViewGroup root = ((ViewGroup)findViewById(R.id.root));
        root.addView(mSlate, 0);
        
        if (icicle != null) {
            onRestoreInstanceState(icicle);
        }

        final ViewGroup colors = (ViewGroup) findViewById(R.id.colors);
        /*
         * colors.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                //Log.d(TAG, "onTouch: " + event);
                if (event.getAction() == MotionEvent.ACTION_DOWN
                        || event.getAction() == MotionEvent.ACTION_MOVE) {
                   final boolean horizontal = (colors.getWidth() > colors.getHeight());

                   int index = (int) 
                        ((horizontal
                                ? (event.getX() / colors.getWidth())
                                : (event.getY() / colors.getHeight()))
                            * colors.getChildCount());
                    //Log.d(TAG, "touch index: " + index);
                    if (index >= colors.getChildCount()) return false;
                    View button = colors.getChildAt(index);
                    clickColor(button);
                }
                return true;
            }
        });*/
        
        setHUDVisibility(false, false);

        final ToolButton.ToolCallback toolCB = new ToolButton.ToolCallback() {
            @Override
            public void setPenMode(ToolButton tool, float min, float max) {
                mSlate.setPenSize(min, max);
                mLastTool = mActiveTool;
                mActiveTool = tool;
                
                if (mLastTool != mActiveTool) {
                    mLastTool.deactivate();
                }
            }
            @Override
            public void setPenColor(ToolButton tool, int color) {
                MarkersActivity.this.setPenColor(color);
                mLastColor = mActiveColor;
                mActiveColor = tool;
                if (mLastColor != mActiveColor) {
                    mLastColor.deactivate();
                }
            }
            @Override
            public void restore(ToolButton tool) {
                if (tool == mActiveTool && tool != mLastTool) mLastTool.click();
                else if (tool == mActiveColor && tool != mLastColor) mLastColor.click();
            }
        };
        
        descend(colors, new ViewFunc() {
            @Override
            public void apply(View v) {
                final ToolButton.SwatchButton swatch = (ToolButton.SwatchButton) v;
                if (swatch != null) {
                    swatch.setCallback(toolCB);
                    if (mActiveColor == null) {
                        mLastColor = mActiveColor = swatch;
                        swatch.click();
                    }
                }
            }
        });
        
        final ToolButton penThinButton = (ToolButton) findViewById(R.id.pen_thin);
        penThinButton.setCallback(toolCB);

        final ToolButton penMediumButton = (ToolButton) findViewById(R.id.pen_medium);
        if (penMediumButton != null) {
            penMediumButton.setCallback(toolCB);
        }
        
        final ToolButton penThickButton = (ToolButton) findViewById(R.id.pen_thick);
        penThickButton.setCallback(toolCB);

        final ToolButton fatMarkerButton = (ToolButton) findViewById(R.id.fat_marker);
        if (fatMarkerButton != null) {
            fatMarkerButton.setCallback(toolCB);
        }
        
        mLastTool = mActiveTool = (penThickButton != null) ? penThickButton : penThinButton;
        mActiveTool.click();
        
        mDebugButton = findViewById(R.id.debug);
        
        // clickDebug(null); // auto-debug mode for partners
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
        saveDrawing(WIP_FILENAME, true);
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
        
    }

    @Override
    protected void onStop() {
        super.onStop();

        //mSlate.recycle(); -- interferes with newly asynchronous saving code when sharing
    }

    private String dumpBundle(Bundle b) {
        if (b == null) return "null";
        StringBuilder sb = new StringBuilder("Bundle{");
        boolean first = true;
        for (String key : b.keySet()) {
            if (!first) sb.append(" ");
            first = false;
            sb.append(key+"=(");
            sb.append(b.get(key));
        }
        sb.append("}");
        return sb.toString();
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        
        if (!mJustLoadedImage) {
            loadDrawing(WIP_FILENAME, true);
        } else {
            mJustLoadedImage = false;
        }

        Intent startIntent = getIntent();
        Log.d(TAG, "starting with intent=" + startIntent + " extras=" + dumpBundle(startIntent.getExtras()));
        String a = startIntent.getAction();
        if (a.equals(Intent.ACTION_EDIT)) {
            // XXX: what happens to the old drawing? we should really move to auto-save
            mSlate.clear();
            loadImageFromIntent(startIntent);
        } else if (a.equals(Intent.ACTION_SEND)) {
            // XXX: what happens to the old drawing? we should really move to auto-save
            mSlate.clear();
            loadImageFromContentUri((Uri)startIntent.getParcelableExtra(Intent.EXTRA_STREAM));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle icicle) {
    }

    @Override
    protected void onRestoreInstanceState(Bundle icicle) {
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            setHUDVisibility(!getHUDVisibility(), true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    final static boolean hasAnimations() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB);
    }

    public void clickLogo(View v) {
        setHUDVisibility(!getHUDVisibility(), true);
    }

    public boolean getHUDVisibility() {
        final View bar = findViewById(R.id.hud);
        return bar.getVisibility() == View.VISIBLE;
    }

    public void setHUDVisibility(boolean show, boolean animate) {
        final View hud = findViewById(R.id.hud);
        final View logo = findViewById(R.id.logo);
        if (!show) {
            if (hasAnimations() && animate) {
                ObjectAnimator.ofFloat(logo, "alpha", 1f, 0.5f).start();
                Animator a = ObjectAnimator.ofFloat(hud, "alpha", 1f, 0f);
                a.addListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator a) {
                        hud.setVisibility(View.GONE);
                    }
                });
                a.start();
            } else {
                hud.setVisibility(View.GONE);
            }
        } else {
            hud.setVisibility(View.VISIBLE);
            if (hasAnimations() && animate) {
                ObjectAnimator.ofFloat(logo, "alpha", 0.5f, 1f).start();
                ObjectAnimator.ofFloat(hud, "alpha", 0f, 1f).start();
            }
        }
    }

    public void clickClear(View v) {
        mSlate.clear();
    }

    public boolean loadDrawing(String filename) {
        return loadDrawing(filename, false);
    }
    public boolean loadDrawing(String filename, boolean temporary) {
        File d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        d = new File(d, temporary ? IMAGE_TEMP_DIRNAME : IMAGE_SAVE_DIRNAME);
        final String filePath = new File(d, filename).toString();
        //Log.d(TAG, "loadDrawing: " + filePath);
        
        if (d.exists()) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inDither = false;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            opts.inScaled = false;
            Bitmap bits = BitmapFactory.decodeFile(filePath, opts);
            if (bits != null) {
                mSlate.setBitmap(bits);
                return true;
            }
        }
        return false;
    }

    public void saveDrawing(String filename) {
        saveDrawing(filename, false);
    }

    public void saveDrawing(String filename, boolean temporary) {
        saveDrawing(filename, temporary, /*animate=*/ false, /*share=*/ false, /*clear=*/ false);
    }

    public void saveDrawing(String filename, boolean temporary, boolean animate, boolean share, boolean clear) {
        final Bitmap bits = mSlate.getBitmap();
        if (bits == null) {
            Log.e(TAG, "save: null bitmap");
            return;
        }
        
        final String _filename = filename;
        final boolean _temporary = temporary;
        final boolean _animate = animate;
        final boolean _share = share;
        final boolean _clear = clear;

        new AsyncTask<Void,Void,String>() {
            @Override
            protected String doInBackground(Void... params) {
                String fn = null;
                try {
                    File d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                    d = new File(d, _temporary ? IMAGE_TEMP_DIRNAME : IMAGE_SAVE_DIRNAME);
                    if (!d.exists()) {
                        if (d.mkdirs()) {
                            if (_temporary) {
                                new FileOutputStream(new File(d, ".nomedia")).write('\n');
                            }
                        } else {
                            throw new IOException("cannot create dirs: " + d);
                        }
                    }
                    File file = new File(d, _filename);
                    Log.d(TAG, "save: saving " + file);
                    OutputStream os = new FileOutputStream(file);
                    bits.compress(Bitmap.CompressFormat.PNG, 0, os);
                    os.close();
                    
                    fn = file.toString();
                } catch (IOException e) {
                    Log.e(TAG, "save: error: " + e);
                }
                return fn;
            }
            
            @Override
            protected void onPostExecute(String fn) {
                if (_share && fn != null) {
                    Uri streamUri = Uri.fromFile(new File(fn));
                    Intent sendIntent = new Intent(Intent.ACTION_SEND);
                    sendIntent.setType("image/jpeg");
                    sendIntent.putExtra(Intent.EXTRA_STREAM, streamUri);
                    startActivity(Intent.createChooser(sendIntent, "Send drawing to:"));
                }
                
                if (_clear) mSlate.clear();
                
                if (!_temporary) {
                    MediaScannerConnection.scanFile(MarkersActivity.this,
                            new String[] { fn }, null, null
                            );
                }
            }
        }.execute();
        
    }

    public void clickSave(View v) {
        if (mSlate.isEmpty()) return;
        
        v.setEnabled(false);
        saveDrawing(System.currentTimeMillis() + ".png");
        Toast.makeText(this, "Drawing saved.", Toast.LENGTH_SHORT).show();
        v.setEnabled(true);
    }

    public void clickSaveAndClear(View v) {
        if (mSlate.isEmpty()) return;

        v.setEnabled(false);
        saveDrawing(System.currentTimeMillis() + ".png", 
                /*temporary=*/ false, /*animate=*/ true, /*share=*/ false, /*clear=*/ true);
        Toast.makeText(this, "Drawing saved.", Toast.LENGTH_SHORT).show();
        v.setEnabled(true);
    }

    public void clickShare(View v) {
        v.setEnabled(false);
        saveDrawing("from_markers.png", /*temporary=*/ true, /*animate=*/ false, /*share=*/ true, /*clear=*/ false);
        v.setEnabled(true);
    }

    public void clickLoad(View v) {
        Intent i = new Intent(Intent.ACTION_PICK,
                       android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        startActivityForResult(i, LOAD_IMAGE); 
    }
    public void clickDebug(View v) {
        boolean debugMode = (mSlate.getDebugFlags() == 0); // toggle 
        mSlate.setDebugFlags(debugMode
            ? Slate.FLAG_DEBUG_EVERYTHING
            : 0);
        mDebugButton.setSelected(debugMode);
        Toast.makeText(this, "Debug mode " + ((mSlate.getDebugFlags() == 0) ? "off" : "on"),
            Toast.LENGTH_SHORT).show();
    }

    public void clickUndo(View v) {
        mSlate.undo();
    }

    public void setPenColor(int color) {
        mSlate.setPenColor(color);
    }
    
    protected void loadImageFromIntent(Intent imageReturnedIntent) {
        Uri contentUri = imageReturnedIntent.getData();
        loadImageFromContentUri(contentUri);
    }
    
    protected void loadImageFromContentUri(Uri contentUri) {
        Toast.makeText(this, "Loading from " + contentUri, Toast.LENGTH_SHORT).show();

        loadDrawing(WIP_FILENAME, true);
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

    protected void onActivityResult(int requestCode, int resultCode, Intent imageReturnedIntent) { 
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent); 

        switch (requestCode) { 
        case LOAD_IMAGE:
            if (resultCode == RESULT_OK) {
                loadImageFromIntent(imageReturnedIntent);
            }
        }
    }

}
