package com.google.android.markersbeta;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.media.MediaScannerConnection;
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
import android.widget.LinearLayout;
import android.widget.Toast;

import com.android.slate.Slate;

public class MarkersActivity extends Activity
{
    final static int LOAD_IMAGE = 1000;

    private static final String TAG = "Markers";
    private static final boolean DEBUG = false;

    public static final String IMAGE_SAVE_DIRNAME = "Drawings";
    public static final String IMAGE_TEMP_DIRNAME = IMAGE_SAVE_DIRNAME + "/.temporary";
    public static final String WIP_FILENAME = "temporary.png";

    private boolean mJustLoadedImage = false;

    private Slate mSlate;

    private ToolButton mLastTool, mActiveTool;
    private ToolButton mLastColor, mActiveColor;
    private ToolButton mLastPenType, mActivePenType;

    private View mDebugButton;
    private View mColorsView;
    private View mActionBarView;
    private View mToolsView;
    private View mLogoView;
    private View mComboHudView;

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

        setContentView(R.layout.main);
        mSlate = (Slate) getLastNonConfigurationInstance();
        if (mSlate == null) {
        	mSlate = new Slate(this);

        	// Load the old buffer if necessary
            if (!mJustLoadedImage) {
                loadDrawing(WIP_FILENAME, true);
            } else {
                mJustLoadedImage = false;
            }
        }
        final ViewGroup root = ((ViewGroup)findViewById(R.id.root));
        root.addView(mSlate, 0);
        
        if (icicle != null) {
            onRestoreInstanceState(icicle);
        }

        mActionBarView = findViewById(R.id.actionbar);
        mComboHudView = findViewById(R.id.hud);
        mToolsView = findViewById(R.id.tools);
        mColorsView = findViewById(R.id.colors);
        mLogoView = findViewById(R.id.logo);
        
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
            public void setPenType(ToolButton tool, int penType) {
                MarkersActivity.this.setPenType(penType);
                mLastPenType = mActivePenType;
                mActivePenType = tool;
                if (mLastPenType != mActivePenType) {
                    mLastPenType.deactivate();
                }
            }
            @Override
            public void restore(ToolButton tool) {
                if (tool == mActiveTool && tool != mLastTool) mLastTool.click();
                else if (tool == mActiveColor && tool != mLastColor) mLastColor.click();
            }
        };
        
        descend((ViewGroup) mColorsView, new ViewFunc() {
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

        final ToolButton typeWhiteboardButton = (ToolButton) findViewById(R.id.whiteboard_marker);
        typeWhiteboardButton.setCallback(toolCB);

        final ToolButton typeFeltTipButton = (ToolButton) findViewById(R.id.felttip_marker);
        if (typeFeltTipButton != null) {
            typeFeltTipButton.setCallback(toolCB);
        }
        
        final ToolButton typeAirbrushButton = (ToolButton) findViewById(R.id.airbrush_marker);
        if (typeAirbrushButton != null) {
            typeAirbrushButton.setCallback(toolCB);
        }
        
        mLastPenType = mActivePenType = typeWhiteboardButton;
        mActivePenType.click();

        mDebugButton = findViewById(R.id.debug);
        
        // clickDebug(null); // auto-debug mode for testing devices
   }

    @Override
    public void onPause() {
        super.onPause();
        saveDrawing(WIP_FILENAME, true);
    }

    @Override
    public void onResume() {
        super.onResume();
        
        String orientation = getString(R.string.orientation);
        
        setRequestedOrientation(
                "landscape".equals(orientation)
                    ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @Override
    public void onConfigurationChanged (Configuration newConfig) {
    	super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onAttachedToWindow() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
    }

    @Override
    protected void onStop() {
        super.onStop();
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
        Intent startIntent = getIntent();
        if (DEBUG) Log.d(TAG, "starting with intent=" + startIntent + " extras=" + dumpBundle(startIntent.getExtras()));
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
        return mActionBarView.getVisibility() == View.VISIBLE;
    }

    public void setHUDVisibility(boolean show, boolean animate) {
        if (!show) {
            if (hasAnimations() && animate) {
                if (mComboHudView != null) {
                    mComboHudView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                } else {
                    mToolsView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                    mColorsView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                }
                mActionBarView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

                AnimatorSet a = new AnimatorSet();
                AnimatorSet.Builder b = 
                        a.play(ObjectAnimator.ofFloat(mLogoView, "alpha", 1f, 0.5f))
                         .with(ObjectAnimator.ofFloat(mActionBarView, "alpha", 1f, 0f));
                if (mComboHudView != null) {
                    b.with(ObjectAnimator.ofFloat(mComboHudView, "alpha", 1f, 0f));
                } else {
                    b.with(ObjectAnimator.ofFloat(mColorsView, "alpha", 1f, 0f))
                     .with(ObjectAnimator.ofFloat(mToolsView, "alpha", 1f, 0f));
                }
                a.addListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator a) {
                        if (mComboHudView != null) {
                            mComboHudView.setVisibility(View.GONE);
                        } else {
                            mColorsView.setVisibility(View.GONE);
                            mToolsView.setVisibility(View.GONE);
                        }
                        mActionBarView.setVisibility(View.GONE);
                        
                        mToolsView.setLayerType(View.LAYER_TYPE_NONE, null);
                        mColorsView.setLayerType(View.LAYER_TYPE_NONE, null);
                        mActionBarView.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                });
                a.start();
            } else {
                if (mComboHudView != null) {
                    mComboHudView.setVisibility(View.GONE);
                } else {
                    mColorsView.setVisibility(View.GONE);
                    mToolsView.setVisibility(View.GONE);
                }
                mActionBarView.setVisibility(View.GONE);
            }
        } else {
            if (mComboHudView != null) {
                mComboHudView.setVisibility(View.VISIBLE);
            } else {
                mColorsView.setVisibility(View.VISIBLE);
                mToolsView.setVisibility(View.VISIBLE);
            }
            mActionBarView.setVisibility(View.VISIBLE);
            if (hasAnimations() && animate) {
                if (mComboHudView != null) {
                    mComboHudView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                } else {
                    mToolsView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                    mColorsView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                }
                mActionBarView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

                AnimatorSet a = new AnimatorSet();
                AnimatorSet.Builder b = 
                        a.play(ObjectAnimator.ofFloat(mLogoView, "alpha", 0.5f, 1f))
                         .with(ObjectAnimator.ofFloat(mActionBarView, "alpha", 0f, 1f));
                if (mComboHudView != null) {
                    b.with(ObjectAnimator.ofFloat(mComboHudView, "alpha", 0f, 1f));
                } else {
                    b.with(ObjectAnimator.ofFloat(mColorsView, "alpha", 0f, 1f))
                     .with(ObjectAnimator.ofFloat(mToolsView, "alpha", 0f, 1f));
                }
                a.addListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator a) {
                        if (mComboHudView != null) {
                            mComboHudView.setVisibility(View.VISIBLE);
                        } else {
                            mColorsView.setVisibility(View.VISIBLE);
                            mToolsView.setVisibility(View.VISIBLE);
                        }
                        mActionBarView.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                });
                a.start();
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
        if (DEBUG) Log.d(TAG, "loadDrawing: " + filePath);
        
        if (d.exists()) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inDither = false;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            opts.inScaled = false;
            Bitmap bits = BitmapFactory.decodeFile(filePath, opts);
            if (bits != null) {
                //mSlate.setBitmap(bits); // messes with the bounds
                mSlate.paintBitmap(bits);
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
        final Bitmap localBits;
        final Bitmap currentBuffer = mSlate.getBitmap();
        if (currentBuffer != null) {
            // clone bitmap to keep it safe
            localBits = currentBuffer.copy(currentBuffer.getConfig(), false);
        } else {
            if (DEBUG) Log.e(TAG, "save: null bitmap");
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
                    if (DEBUG) Log.d(TAG, "save: saving " + file);
                    OutputStream os = new FileOutputStream(file);
                    localBits.compress(Bitmap.CompressFormat.PNG, 0, os);
                    localBits.recycle();
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
    
    public void setPenType(int type) {
        mSlate.setPenType(type);
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
                if (DEBUG) Log.d(TAG, "successfully loaded bitmap: " + b);
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
