package com.google.android.apps.markers;

import java.util.ArrayList;
import java.util.Collection;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.FloatMath;
import android.util.Log;

public class TiledBitmapCanvas implements CanvasLite {
    public static final boolean DEBUG = true;

    public static final int DEFAULT_TILE_SIZE = 256;
    private static final float INVALIDATE_PADDING = 4.0f;

    public static final int MAX_VERSIONS = 10;

    private int mTileSize = DEFAULT_TILE_SIZE;

    private class Tile {
        private class Version {
            int version; 
            Canvas canvas;
            Bitmap bitmap;
            public Version(int version) {
                this.version = version;
                this.bitmap = Bitmap.createBitmap(mTileSize, mTileSize, mConfig);
                if (this.bitmap != null) {
                    this.canvas = new Canvas(this.bitmap);
                    this.canvas.translate(-x*mTileSize, -y*mTileSize);
                }
            }
        }
        int x, y;
        int top, bottom;
        boolean dirty;
        ArrayList<Version> versions;
        public Tile(int x, int y, int version) {
            this.x = x;
            this.y = y;
            versions = new ArrayList<Version>(MAX_VERSIONS);
            top = bottom = makeVersion(version);
            if (top < 0) {
                throw new OutOfMemoryError("Could not create bitmap for tile " + x + "," + y);
            }
        };
        public int makeVersion(int version) {
            Version v = new Version(version);
            if (v.bitmap == null) {
                // XXX handle memory error
                return -1;
            }
            versions.add(v);
            return version;
        }
        public void clear() {
            for (int i=0; i<versions.size(); i++) {
                versions.get(i).bitmap.recycle();
            }
            versions.clear();
        }
        public Bitmap getBitmap() {
            return versions.get(0).bitmap;
        }
        public Bitmap getBitmap(int version) {
            return versions.get(findVersion(version)).bitmap;
        }
        public Canvas getCanvas() {
            return versions.get(0).canvas;
        }
        public Canvas getCanvas(int version) {
            return versions.get(findVersion(version)).canvas;
        }
        public void revert(int toVersion) {
            final int i = findVersion(toVersion);
            if (i > 0) versions.subList(0, i).clear();
            top = versions.get(i).version;
        }
        private int findVersion(int version) {
            for (int i=0; i<versions.size(); i++) {
                final Version v = versions.get(i);
                if (v.version <= version) {
                    return i;
                }
            }
            throw new RuntimeException(
                    "Version " + version + " does not exist in tile starting with version " + bottom);
        }
    }
    private Tile[] mTiles;

    private int mWidth, mHeight, mTilesX, mTilesY;

    private Config mConfig;

    public TiledBitmapCanvas() {
    }

    public TiledBitmapCanvas(int tileSize) {
        mTileSize = tileSize;
    }

    public TiledBitmapCanvas(Bitmap bitmap) {
        mWidth = bitmap.getWidth();
        mHeight = bitmap.getHeight();
        mConfig = bitmap.getConfig();
        load(bitmap);
    }
    public TiledBitmapCanvas(int w, int h, Bitmap.Config config) {
        mWidth = w;
        mHeight = h;
        mConfig = config;
        load(null);
    }

    public void recycleBitmaps() {
        for (int i=0; i<mTiles.length; i++) {
            mTiles[i].clear();
            mTiles[i] = null;
        }
        mTiles = null;
    }

    private void load(Bitmap bitmap) {
        mTilesX = mWidth / mTileSize + ((mWidth % mTileSize) == 0 ? 0 : 1);
        mTilesY = mHeight / mTileSize + ((mHeight % mTileSize) == 0 ? 0 : 1);
        mTiles = new Tile[mTilesX * mTilesY];
        
        final Paint paint = new Paint();
        for (int j=0; j<mTilesY; j++) {
            for (int i=0; i<mTilesX; i++) {
                final int p = j * mTilesX + i;
                final Tile t = new Tile(i, j, 0); // XXX: version
                mTiles[p] = t;
                if (bitmap != null) {
                    t.getCanvas().drawBitmap(bitmap, 0, 0, paint);
                }
            }
        }
    }

    public static final int max(int a, int b) {
        return (b > a) ? b : a;
    }

    public static final int min(int a, int b) {
        return (b < a) ? b : a;
    }

    public void drawRect(float l, float t, float r, float b, Paint paint) {
        final int tilel = max(0,(int)FloatMath.floor((l-INVALIDATE_PADDING) / mTileSize));
        final int tilet = max(0,(int)FloatMath.floor((t-INVALIDATE_PADDING) / mTileSize));
        final int tiler = min(mTilesX-1, (int)FloatMath.ceil((r+INVALIDATE_PADDING) / mTileSize));
        final int tileb = min(mTilesY-1, (int)FloatMath.ceil((b+INVALIDATE_PADDING) / mTileSize));
        for (int tiley = tilet; tiley <= tileb; tiley++) {
            for (int tilex = tilel; tilex <= tiler; tilex++) {
                final Tile tile = mTiles[tiley*mTilesX + tilex];
                tile.getCanvas().drawRect(l, t, r, b, paint);
                tile.dirty = true;
            }
        }
    }

    public void drawCircle(float x, float y, float r, Paint paint) {
        final float invalR = r + INVALIDATE_PADDING;
        final int tilel = max(0, (int)FloatMath.floor((x-invalR) / mTileSize));
        final int tilet = max(0, (int)FloatMath.floor((y-invalR) / mTileSize));
        final int tiler = min(mTilesX-1, (int)FloatMath.ceil((x+invalR) / mTileSize));
        final int tileb = min(mTilesY-1, (int)FloatMath.ceil((y+invalR) / mTileSize));
        for (int tiley = tilet; tiley <= tileb; tiley++) {
            for (int tilex = tilel; tilex <= tiler; tilex++) {
                final Tile tile = mTiles[tiley*mTilesX + tilex];
                tile.getCanvas().drawCircle(x, y, r, paint);
                tile.dirty = true;
            }
        }
    }

    public void drawColor(int color, PorterDuff.Mode mode) {
        for (int i=0; i<mTiles.length; i++) {
            final Tile tile = mTiles[i];
            tile.getCanvas().drawColor(color, mode);
            tile.dirty = true;
        }
    }

    @Override
    public void drawBitmap(Bitmap bitmap, Rect src, RectF dst, Paint paint) {
        final int tilel = max(0,(int)FloatMath.floor((dst.left-INVALIDATE_PADDING) / mTileSize));
        final int tilet = max(0,(int)FloatMath.floor((dst.top-INVALIDATE_PADDING) / mTileSize));
        final int tiler = min(mTilesX-1, (int)FloatMath.ceil((dst.right+INVALIDATE_PADDING) / mTileSize));
        final int tileb = min(mTilesY-1, (int)FloatMath.ceil((dst.bottom+INVALIDATE_PADDING) / mTileSize));
        for (int tiley = tilet; tiley <= tileb; tiley++) {
            for (int tilex = tilel; tilex <= tiler; tilex++) {
                final Tile tile = mTiles[tiley*mTilesX + tilex];
                tile.getCanvas().drawBitmap(bitmap, src, dst, paint);
                tile.dirty = true;
            }
        }
    }

    private int mDrawCount = 0;
    private Paint dbgPaint1 = new Paint();
    private Paint dbgPaint2 = new Paint();
    private Paint dbgStroke = new Paint();
    private Paint dbgTextPaint = new Paint();
    @Override
    public void drawTo(Canvas drawCanvas, float left, float top, Paint paint, boolean onlyDirty) {
        final Rect src = new Rect(0, 0, mTileSize, mTileSize);
        final Rect dst = new Rect(0, 0, mTileSize, mTileSize);
        dbgPaint1.setColor(0x40FF0000);
        dbgPaint2.setColor(0x400000FF);
        dbgStroke.setColor(0x80000000);
        dbgStroke.setStrokeWidth(3.0f);
        dbgStroke.setStyle(Paint.Style.STROKE);
        dbgTextPaint.setColor(0x80000000);
        dbgTextPaint.setTextSize(12.0f);
        drawCanvas.save();
        drawCanvas.translate(-left, -top);
        drawCanvas.clipRect(0, 0, mWidth, mHeight);
        for (int j=0; j<mTilesY; j++) {
            for (int i=0; i<mTilesX; i++) {
                dst.offsetTo(i*mTileSize, j*mTileSize);
                final int p = j * mTilesX + i;
                final Tile tile = mTiles[p];
                if (!onlyDirty || tile.dirty) {
                    drawCanvas.drawBitmap(tile.getBitmap(), src, dst, paint);
                    tile.dirty = false;
                    if (DEBUG) {
                        mDrawCount++;
                        drawCanvas.drawRect(dst, (mDrawCount % 2 == 0) ? dbgPaint1 : dbgPaint2);
                        drawCanvas.drawRect(dst, dbgStroke);
                        drawCanvas.drawText(
                                String.format("%d,%d v%d", tile.x, tile.y, tile.top),
                                dst.left + 4, dst.bottom - 4, dbgTextPaint);
                    }
                }
            }
        }
        drawCanvas.restore();
    }

    public int getWidth() {
        return mWidth;
    }
    public int getHeight() {
        return mHeight;
    }
    
    public Bitmap toBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(mWidth, mHeight, mConfig);
        Canvas canvas = new Canvas(bitmap);
        drawTo(canvas, 0, 0, null, false);
        return bitmap;
    }
}
