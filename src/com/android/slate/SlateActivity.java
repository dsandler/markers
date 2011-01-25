package com.android.slate;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

public class SlateActivity extends Activity
{
    final static int LOAD_IMAGE = 1000;

    Slate mSlate;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mSlate = (Slate) findViewById(R.id.slate);
    }

    public void clickClear(View v) {
        mSlate.clear();
    }
    public void clickInvert(View v) {
        mSlate.invert();
    }
    public void clickSave(View v) {
        String filename = mSlate.save();
        Toast.makeText(this, "Saved to " + filename, Toast.LENGTH_SHORT).show();
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
        int color = 0xFFFFFFFF;
        switch (v.getId()) {
            case R.id.black:  color = 0xFF000000; break;
            case R.id.white:  color = 0xFFFFFFFF; break;
            case R.id.red:    color = 0xFFFF0000; break;
            case R.id.green:  color = 0xFF00FF00; break;
            case R.id.blue:   color = 0xFF0000FF; break;

            case R.id.gray:   color = 0xFF808080; break;
            case R.id.pink:   color = 0xFFFF00CC; break;
            case R.id.orange: color = 0xFFFF8000; break;
            case R.id.yellow: color = 0xFFFFFF00; break;
            case R.id.purple: color = 0xFF6000A0; break;
        }
        mSlate.setPenColor(color);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent imageReturnedIntent) { 
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent); 

        switch (requestCode) { 
        case LOAD_IMAGE:
            if (resultCode == RESULT_OK){  
                Uri contentUri = imageReturnedIntent.getData();
                Toast.makeText(this, "Loading from " + contentUri, Toast.LENGTH_SHORT).show();

                try {
                    Bitmap b = MediaStore.Images.Media.getBitmap(getContentResolver(), contentUri);
                    if (b != null) {
                        mSlate.paintBitmap(b);
                    }
                } catch (java.io.FileNotFoundException ex) {
                } catch (java.io.IOException ex) {
                }
            }
        }
    }
    
}
