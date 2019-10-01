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

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Build;
import android.widget.ImageView;

import org.dsandler.apps.markers.R;

class QrCode {
	static void show(final Activity activity) {
        final AlertDialog.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
		    builder = new AlertDialog.Builder(activity, android.R.style.Theme_Light_Panel);
        } else {
		    builder = new AlertDialog.Builder(activity);
        }
		builder.setTitle(null);
		builder.setCancelable(true);
		ImageView iv = new ImageView(activity);
		iv.setImageResource(R.drawable.qr);
		builder.setView(iv);
		builder.create().show();
	}
}
