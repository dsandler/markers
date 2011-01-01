package com.android.slate;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.util.Log;

public class SlateActivity extends Activity
{
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
        mSlate.save();
    }
    public void clickDebug(View v) {
        mSlate.setDebugFlags(mSlate.getDebugFlags() == 0 ? Slate.FLAG_DEBUG_STROKES : 0);
    }
    public void clickColor(View v) {
        int color = 0xFFFFFFFF;
        switch (v.getId()) {
            case R.id.black: color = 0xFF000000; break;
            case R.id.white: color = 0xFFFFFFFF; break;
            case R.id.red:   color = 0xFFFF0000; break;
            case R.id.green: color = 0xFF00FF00; break;
            case R.id.blue:  color = 0xFF0000FF; break;
        }
        mSlate.setPenColor(color);
    }
}
