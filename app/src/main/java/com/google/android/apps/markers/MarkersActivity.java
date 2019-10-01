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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.*;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import org.dsandler.apps.markers.R;

import com.google.android.apps.markers.ToolButton.SwatchButton;

public class MarkersActivity extends Activity
{
    final static int LOAD_IMAGE = 1000;

    private static final String TAG = "Markers";
    private static final boolean DEBUG = true;

    public static final String IMAGE_SAVE_DIRNAME = "Drawings";
    public static final String IMAGE_TEMP_DIRNAME = IMAGE_SAVE_DIRNAME + "/.temporary";
    public static final String WIP_FILENAME = "temporary.png";
    
    public static final String PREF_LAST_TOOL = "tool";
    public static final String PREF_LAST_TOOL_TYPE = "tool_type";
    public static final String PREF_LAST_COLOR = "color";
    public static final String PREF_LAST_HUDSTATE = "hudup";

    private boolean mJustLoadedImage = false;

    private Slate mSlate;
    private ZoomTouchView mZoomView;

    private ToolButton mLastTool, mActiveTool;
    private ToolButton mLastColor, mActiveColor;
    private ToolButton mLastPenType, mActivePenType;

    private View mDebugButton;
    private View mColorsView;
    private View mActionBarView;
    private View mToolsView;
    private View mLogoView;
    private View mComboHudView;
    
    private Dialog mMenuDialog;

    private SharedPreferences mPrefs;

    private LinkedList<String> mDrawingsToScan = new LinkedList<String>();

    protected MediaScannerConnection mMediaScannerConnection;
    private String mPendingShareFile;
    private MediaScannerConnectionClient mMediaScannerClient = 
            new MediaScannerConnection.MediaScannerConnectionClient() {
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
                            startActivity(Intent.createChooser(sendIntent, "Send drawing to:"));
                            mPendingShareFile = null;
                        }
                        scanNext();
                    }
                }
            };


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
        public void apply(View v);
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

    @TargetApi(11)
    private void setupLayers() {
        if (!hasAnimations()) return;

        if (mComboHudView != null) {
            mComboHudView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        } else {
            mToolsView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            mColorsView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        mActionBarView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);

        final Window win = getWindow();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(win.getAttributes());
        lp.format = PixelFormat.RGBA_8888;
        win.setBackgroundDrawableResource(R.drawable.transparent);
        win.setAttributes(lp);
        win.requestFeature(Window.FEATURE_NO_TITLE);

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
        mZoomView = new ZoomTouchView(this);
        mZoomView.setSlate(mSlate);
        mZoomView.setEnabled(false);
        if (hasAnimations()) {
            mZoomView.setAlpha(0);
        }
        root.addView(mZoomView, 0);
        
        mMediaScannerConnection =
                new MediaScannerConnection(MarkersActivity.this, mMediaScannerClient); 

        
        if (icicle != null) {
            onRestoreInstanceState(icicle);
        }

        mActionBarView = findViewById(R.id.actionbar);
        mComboHudView = findViewById(R.id.hud);
        mToolsView = findViewById(R.id.tools);
        mColorsView = findViewById(R.id.colors);
        mLogoView = findViewById(R.id.logo);

        DecorTracker dt = DecorTracker.get();
        dt.addInsettableView(mLogoView);
        dt.addInsettableView(mActionBarView);
        if (mComboHudView != null) {
            dt.addInsettableView(mComboHudView);
        } else {
            dt.addInsettableView(mToolsView);
            dt.addInsettableView(mColorsView);
        }

        setupLayers(); // the HUD needs to have a software layer at all times
                       // so we can draw through it quickly

        mDebugButton = findViewById(R.id.debug);

        TextView title = (TextView) mActionBarView.findViewById(R.id.logotype);
        Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        title.setTypeface(light);

        final ToolButton.ToolCallback toolCB = new ToolButton.ToolCallback() {
            @Override
            public void setPenMode(ToolButton tool, float min, float max) {
                mSlate.setZoomMode(false);
                mZoomView.setEnabled(false);
                mSlate.setPenSize(min, max);
                mLastTool = mActiveTool;
                mActiveTool = tool;
                
                if (mLastTool != mActiveTool) {
                    mLastTool.deactivate();
                    mPrefs.edit().putString(PREF_LAST_TOOL, (String) mActiveTool.getTag())
                        .commit();
                }
            }
            @Override
            public void setPenColor(ToolButton tool, int color) {
                MarkersActivity.this.setPenColor(color);
                mLastColor = mActiveColor;
                mActiveColor = tool;
                if (mLastColor != mActiveColor) {
                    mLastColor.deactivate();
                    mPrefs.edit().putInt(PREF_LAST_COLOR, color).commit();
                }
                if (mActiveTool instanceof ToolButton.ZoomToolButton) {
                    // you probably want to use a pen now
                    restore(mActiveTool);
                }
            }
            @Override
            public void setPenType(ToolButton tool, int penType) {
                MarkersActivity.this.setPenType(penType);
                mLastPenType = mActivePenType;
                mActivePenType = tool;
                if (mLastPenType != mActivePenType) {
                    mLastPenType.deactivate();
                    mPrefs.edit().putString(PREF_LAST_TOOL_TYPE, (String) mActivePenType.getTag())
                        .commit();
                }
            }
            @Override
            public void restore(ToolButton tool) {
                if (tool == mActiveTool && tool != mLastTool) {
                    mLastTool.click();
                    mPrefs.edit().putString(PREF_LAST_TOOL, (String) mActiveTool.getTag())
                        .commit();
                } else if (tool == mActiveColor && tool != mLastColor) {
                    mLastColor.click();
                    mPrefs.edit().putInt(PREF_LAST_COLOR, ((SwatchButton) mLastColor).color)
                        .commit();
                }
            }
            @Override
            public void setBackgroundColor(ToolButton tool, int color) {
                mSlate.setDrawingBackground(color);
            }
            @Override
            public void setZoomMode(ToolButton me) {
                mSlate.setZoomMode(true);
                mZoomView.setEnabled(true);
                mLastTool = mActiveTool;
                mActiveTool = me;
                
                if (mLastTool != mActiveTool) {
                    mLastTool.deactivate();
                    mPrefs.edit().putString(PREF_LAST_TOOL, (String) mActiveTool.getTag())
                        .commit();
                }
            }

            @Override
            public void resetZoom(ToolButton tool) {
                mSlate.resetZoom();
            }
        };
        
        descend((ViewGroup) mColorsView, new ViewFunc() {
            @Override
            public void apply(View v) {
                final ToolButton.SwatchButton swatch = (ToolButton.SwatchButton) v;
                if (swatch != null) {
                    swatch.setCallback(toolCB);
                }
            }
        });

        final ToolButton zoomButton = (ToolButton) findViewById(R.id.tool_zoom);
        zoomButton.setCallback(toolCB);

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
        
        final ToolButton typeFountainPenButton = (ToolButton) findViewById(R.id.fountainpen_marker);
        if (typeFountainPenButton != null) {
            typeFountainPenButton.setCallback(toolCB);
        }
        
        mLastPenType = mActivePenType = typeWhiteboardButton;

        loadSettings();

        mActiveTool.click();
        mActivePenType.click();

        // clickDebug(null); // auto-debug mode for testing devices
    }

    private void loadSettings() {
        mPrefs = getPreferences(MODE_PRIVATE);

        final String toolTag = mPrefs.getString(PREF_LAST_TOOL, null);
        if (toolTag != null) {
            mActiveTool = (ToolButton) mToolsView.findViewWithTag(toolTag);
        }
        if (mActiveTool == null) {
            mActiveTool = (ToolButton) mToolsView.findViewById(R.id.pen_thick);
        }
        if (mActiveTool == null) {
            mActiveTool = (ToolButton) mToolsView.findViewById(R.id.pen_thin);
        }
        mLastTool = mActiveTool;
        if (mActiveTool != null) mActiveTool.click();

        final String typeTag = mPrefs.getString(PREF_LAST_TOOL_TYPE, "type_whiteboard");
        mLastPenType = mActivePenType = (ToolButton) mToolsView.findViewWithTag(typeTag);
        if (mActivePenType != null) mActivePenType.click();

        final int color = mPrefs.getInt(PREF_LAST_COLOR, 0xFF000000);
        descend((ViewGroup) mColorsView, new ViewFunc() {
            @Override
            public void apply(View v) {
                final ToolButton.SwatchButton swatch = (ToolButton.SwatchButton) v;
                if (swatch != null) {
                    if (color == swatch.color) {
                        mActiveColor = swatch;
                    }
                }
            }
        });
        mLastColor = mActiveColor;
        if (mActiveColor != null) mActiveColor.click();

        setHUDVisibility(mPrefs.getBoolean(PREF_LAST_HUDSTATE, false), false);
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
        String a = startIntent.getAction();
        if (DEBUG) Log.d(TAG, "starting with intent=" + startIntent + " action=" + a + " extras=" + dumpBundle(startIntent.getExtras()));
        if (a == null) return;
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

    final static boolean hasSystemUiFlags() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN);
    }

    final static boolean hasImmersive() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT);
    }

    public void clickLogo(View v) {
        setHUDVisibility(!getHUDVisibility(), true);
    }

    public boolean getHUDVisibility() {
        return mActionBarView.getVisibility() == View.VISIBLE;
    }

    @TargetApi(11)
    public void setHUDVisibility(boolean show, boolean animate) {
        if (hasSystemUiFlags()) {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

            if (!show) {
                flags |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            }

            if (hasImmersive()) {
                flags |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;

                if (!show) {
                    flags |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                }
            }

            mSlate.setSystemUiVisibility(flags);
        }

        final int actionBarHeight = mActionBarView.getHeight(); // use for animation distances
        if (!show) {
            if (hasAnimations() && animate) {
                AnimatorSet a = new AnimatorSet();
                AnimatorSet.Builder b = 
                        a.play(ObjectAnimator.ofFloat(mLogoView, "alpha", 1f, 0.5f))
                         .with(ObjectAnimator.ofFloat(mActionBarView, "alpha", 1f, 0f))
                         .with(ObjectAnimator.ofFloat(mActionBarView, "translationY",
                                 0f, -actionBarHeight));
                if (mComboHudView != null) {
                    b.with(ObjectAnimator.ofFloat(mComboHudView, "alpha", 1f, 0f));
                } else {
                    b.with(ObjectAnimator.ofFloat(mColorsView, "alpha", 1f, 0f))
                     .with(ObjectAnimator.ofFloat(mColorsView, "translationY",
                             0f, actionBarHeight))
                     .with(ObjectAnimator.ofFloat(mToolsView, "alpha", 1f, 0f))
                     .with(ObjectAnimator.ofFloat(mToolsView, "translationX",
                             0f, -actionBarHeight));
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
                    }
                });
                a.setDuration(200);
                a.start();
            } else {
                if (mComboHudView != null) {
                    mComboHudView.setVisibility(View.GONE);
                } else {
                    mColorsView.setVisibility(View.GONE);
                    mToolsView.setVisibility(View.GONE);
                }
                mActionBarView.setVisibility(View.GONE);
                if (hasAnimations()) {
                    mLogoView.setAlpha(0.5f);
                }
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
                AnimatorSet a = new AnimatorSet();
                AnimatorSet.Builder b = 
                        a.play(ObjectAnimator.ofFloat(mLogoView, "alpha", 0.5f, 1f))
                         .with(ObjectAnimator.ofFloat(mActionBarView, "alpha", 0f, 1f))
                         .with(ObjectAnimator.ofFloat(mActionBarView, "translationY",
                                 -actionBarHeight, 0f));
                if (mComboHudView != null) {
                    b.with(ObjectAnimator.ofFloat(mComboHudView, "alpha", 0f, 1f));
                } else {
                    b.with(ObjectAnimator.ofFloat(mColorsView, "alpha", 0f, 1f))
                     .with(ObjectAnimator.ofFloat(mColorsView, "translationY",
                             actionBarHeight, 0f))
                     .with(ObjectAnimator.ofFloat(mToolsView, "alpha", 0f, 1f))
                     .with(ObjectAnimator.ofFloat(mToolsView, "translationX",
                             -actionBarHeight, 0f));
                }
                a.setDuration(200);
                a.start();
            } else {
                if (hasAnimations()) {
                    mLogoView.setAlpha(1f);
                }
            }
        }
        mPrefs.edit().putBoolean(PREF_LAST_HUDSTATE, show).commit();
    }

    public void clickClear(View v) {
        mSlate.clear();
    }

    public boolean loadDrawing(String filename) {
        return loadDrawing(filename, false);
    }

    @TargetApi(8)
    public File getPicturesDirectory() {
        final File d;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
            d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        } else {
            d = new File("/sdcard/Pictures");
        }
        return d;
    }

    public boolean loadDrawing(String filename, boolean temporary) {
        File d = getPicturesDirectory();
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
        final Bitmap localBits = mSlate.copyBitmap(/*withBackground=*/!temporary);
        if (localBits == null) {
            if (DEBUG) Log.e(TAG, "save: null bitmap");
            return;
        }
        
        final String _filename = filename;
        final boolean _temporary = temporary;
        final boolean _share = share;
        final boolean _clear = clear;

        new AsyncTask<Void,Void,String>() {
            @Override
            protected String doInBackground(Void... params) {
                String fn = null;
                try {
                    File d = getPicturesDirectory();
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
                if (fn != null) {
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

                if (_clear) mSlate.clear();
            }
        }.execute();
        
    }

    public void clickSave(View v) {
        if (mSlate.isEmpty()) return;
        
        v.setEnabled(false);
        final String filename = System.currentTimeMillis() + ".png"; 
        saveDrawing(filename);
        Toast.makeText(this, "Drawing saved: " + filename, Toast.LENGTH_SHORT).show();
        v.setEnabled(true);
    }

    public void clickSaveAndClear(View v) {
        if (mSlate.isEmpty()) return;

        v.setEnabled(false);
        final String filename = System.currentTimeMillis() + ".png"; 
        saveDrawing(filename, 
                /*temporary=*/ false, /*animate=*/ true, /*share=*/ false, /*clear=*/ true);
        Toast.makeText(this, "Drawing saved: " + filename, Toast.LENGTH_SHORT).show();
        v.setEnabled(true);
    }

    private void setThingyEnabled(Object v, boolean enabled) {
        if (v == null) return;
        if (v instanceof View) ((View)v).setEnabled(enabled);
        else if (v instanceof MenuItem) ((MenuItem)v).setEnabled(enabled);
    }

    public void clickShare(View v) {
        hideOverflow();
        setThingyEnabled(v, false);
        final String filename = System.currentTimeMillis() + ".png";
        // can't use a truly temporary file because:
        // - we want mediascanner to give us a content: URI for it; some apps don't like file: URIs
        // - if mediascanner scans it, it will show up in Gallery, so it might as well be a regular drawing
        saveDrawing(filename,
                /*temporary=*/ false, /*animate=*/ false, /*share=*/ true, /*clear=*/ false);
        setThingyEnabled(v, true);
    }

    public void clickLoad(View unused) {
        hideOverflow();
        Intent i = new Intent(Intent.ACTION_PICK,
                       android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        startActivityForResult(i, LOAD_IMAGE); 
    }

    public void clickDebug(View unused) {
        hideOverflow();
        boolean debugMode = (mSlate.getDebugFlags() == 0); // toggle 
        mSlate.setDebugFlags(debugMode
            ? Slate.FLAG_DEBUG_EVERYTHING
            : 0);
        mDebugButton.setSelected(debugMode);
        Toast.makeText(this, "Debug mode " + ((mSlate.getDebugFlags() == 0) ? "off" : "on"),
            Toast.LENGTH_SHORT).show();
    }

    public void clickUndo(View unused) {
        mSlate.undo();
    }

    public void clickAbout(View unused) {
        hideOverflow();
        About.show(this);
    }

    public void clickQr(View unused) {
        hideOverflow();
        QrCode.show(this);
    }

    public void clickShareMarketLink(View unused) {
        hideOverflow();
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
        sendIntent.putExtra(Intent.EXTRA_TEXT,
                "http://play.google.com/store/apps/details?id=" + getPackageName());
        startActivity(Intent.createChooser(sendIntent, "Share the Markers app with:"));
    }

    public void clickMarketLink(View unused) {
        hideOverflow();
        Intent urlIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=" + getPackageName()));
        startActivity(urlIntent);
    }

    public void clickSiteLink(View unused) {
        hideOverflow();
        Intent urlIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("http://dsandler.org/markers?from=app"));
        startActivity(urlIntent);
    }

    private void showOverflow() {
        mMenuDialog.show();
    }
    private void hideOverflow() {
        mMenuDialog.dismiss();
    }
    public void clickOverflow(View v) {
        if (mMenuDialog == null) {
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            View layout = inflater.inflate(R.layout.overflow_menu, null);
    
    //        TextView text = (TextView) layout.findViewById(R.id.text);
    //        text.setText("Hello, this is a custom dialog!");
    //        ImageView image = (ImageView) layout.findViewById(R.id.image);
    //        image.setImageResource(R.drawable.android);
    
            mMenuDialog = new Dialog(this);
            //mMenuDialog = new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK).create();
            Window dialogWin  = mMenuDialog.getWindow();
            dialogWin.requestFeature(Window.FEATURE_NO_TITLE);
            dialogWin.setGravity(Gravity.TOP|Gravity.RIGHT);
            WindowManager.LayoutParams winParams = dialogWin.getAttributes();
            winParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
            winParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            winParams.y = getResources().getDimensionPixelOffset(R.dimen.action_bar_height);
            dialogWin.setAttributes(winParams);
            dialogWin.setWindowAnimations(android.R.style.Animation_Translucent);
            dialogWin.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            mMenuDialog.setCanceledOnTouchOutside(true); 

            mMenuDialog.setContentView(layout);
            // bash the background
            final View decor = layout.getRootView();

            decor.setBackgroundDrawable(null);
            decor.setPadding(0,0,0,0);
        }

        showOverflow();
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
