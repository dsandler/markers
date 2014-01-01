/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.ArrayList;

public class DecorTracker {
    static DecorTracker INSTANCE = new DecorTracker();

    private Rect mCurrentInsets = new Rect();
    private Rect mPreviousInsets = new Rect();
    private ArrayList<View> mInsettables = new ArrayList<View>();

    private DecorTracker() {
    }

    public static DecorTracker get() {
        return INSTANCE;
    }

    public void setInsets(Rect insets) {
        mCurrentInsets.set(insets);
        final int N = mInsettables.size();
        for (int i=0; i<N; i++) {
            final View v = mInsettables.get(i);
            final ViewGroup.LayoutParams vglp = v.getLayoutParams();
            if (!(vglp instanceof ViewGroup.MarginLayoutParams)) continue;
            final ViewGroup.MarginLayoutParams lp
                    = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.topMargin += (mCurrentInsets.top - mPreviousInsets.top);
            lp.leftMargin += (mCurrentInsets.left - mPreviousInsets.left);
            lp.rightMargin += (mCurrentInsets.right - mPreviousInsets.right);
            lp.bottomMargin += (mCurrentInsets.bottom - mPreviousInsets.bottom);
            v.setLayoutParams(lp);
        }
        mPreviousInsets.set(insets);
    }

    public Rect getInsets() {
        return mCurrentInsets;
    }

    public void addInsettableView(View v) {
        mInsettables.add(v);
    }

    public void removeInsettableView(View v) {
        mInsettables.remove(v);
    }
}
