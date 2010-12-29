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
}
