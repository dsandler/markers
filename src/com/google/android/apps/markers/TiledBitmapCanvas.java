package com.google.android.apps.markers;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.FloatMath;

public class TiledBitmapCanvas implements CanvasLite {
    public static final boolean DEBUG = true;

    public static final int DEFAULT_TILE_SIZE = 256;
    private static final float INVALIDATE_PADDING = 4.0f;

    private int mTileSize = DEFAULT_TILE_SIZE;

    private class Tile {
        int x, y;
        Canvas canvas;
        Bitmap bitmap;
        boolean dirty;
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
            mTiles[i].bitmap.recycle();
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
                final Tile t = new Tile();
                mTiles[p] = t; 
                t.bitmap = Bitmap.createBitmap(mTileSize, mTileSize, mConfig);
                if (mTiles[p] == null) {
                    // XXX handle memory error
                    return;
                }
                t.canvas = new Canvas(t.bitmap);
                t.canvas.translate(-i*mTileSize, -j*mTileSize);
                if (bitmap != null) {
                    t.canvas.drawBitmap(bitmap, 0, 0, paint);
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
                tile.canvas.drawRect(l, t, r, b, paint);
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
                tile.canvas.drawCircle(x, y, r, paint);
                tile.dirty = true;
            }
        }
    }

    public void drawColor(int color, PorterDuff.Mode mode) {
        for (int i=0; i<mTiles.length; i++) {
            mTiles[i].canvas.drawColor(color, mode);
            mTiles[i].dirty = true;
        }
    }

    private int mDrawCount = 0;
    private Paint dbgPaint1 = new Paint();
    private Paint dbgPaint2 = new Paint();
    private Paint dbgStroke = new Paint();
    @Override
    public void drawTo(Canvas drawCanvas, float left, float top, Paint paint, boolean onlyDirty) {
        final Rect src = new Rect(0, 0, mTileSize, mTileSize);
        final Rect dst = new Rect(0, 0, mTileSize, mTileSize);
        dbgPaint1.setColor(0x40FF0000);
        dbgPaint2.setColor(0x400000FF);
        dbgStroke.setColor(0x80000000);
        dbgStroke.setStrokeWidth(3.0f);
        dbgStroke.setStyle(Paint.Style.STROKE);
        drawCanvas.save();
        drawCanvas.translate(-left, -top);
        drawCanvas.clipRect(0, 0, mWidth, mHeight);
        for (int j=0; j<mTilesY; j++) {
            for (int i=0; i<mTilesX; i++) {
                dst.offsetTo(i*mTileSize, j*mTileSize);
                final int p = j * mTilesX + i;
                final Tile tile = mTiles[p];
                if (!onlyDirty || tile.dirty) {
                    drawCanvas.drawBitmap(tile.bitmap, src, dst, paint);
                    tile.dirty = false;
                    if (DEBUG) {
                        mDrawCount++;
                        drawCanvas.drawRect(dst, (mDrawCount % 2 == 0) ? dbgPaint1 : dbgPaint2);
                        drawCanvas.drawRect(dst, dbgStroke);
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
