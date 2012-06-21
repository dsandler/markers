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

import android.os.SystemClock;
import android.view.MotionEvent;

public class Spot {
	public float x, y;
	public float size, pressure;
	public long time; // ms, in SystemClock.currentThreadTimeMillis base
	public int tool;
	
	public Spot(float _x, float _y, float _s, float _p, long _t, int _tt) {
		update(_x, _y, _s, _p, _t, _tt);
	}
	
	public void update(float _x, float _y, float _s, float _p, long _t, int _tt) {
		x = _x;
		y = _y;
		size = _s;
		pressure = _p;
		time = _t;
		tool = _tt;
	}
	public Spot(Spot _src) {
		this(_src.x, _src.y, _src.size, _src.pressure, _src.time, _src.tool);
	}
	public Spot() {
		this(0, 0, 0, 0, SystemClock.currentThreadTimeMillis(), MotionEvent.TOOL_TYPE_FINGER);
	}
	public Spot(float _x, float _y, float _s) {
		this(_x, _y, _s, 0, SystemClock.currentThreadTimeMillis(), MotionEvent.TOOL_TYPE_FINGER);
	}
	public Spot(float _x, float _y) {
		this(_x, _y, 0, 0, SystemClock.currentThreadTimeMillis(), 0);
	}
	public Spot(MotionEvent.PointerCoords _pc) {
		this(_pc, SystemClock.currentThreadTimeMillis());
	}
	public Spot(MotionEvent.PointerCoords _pc, long time) {
		this(_pc, time, MotionEvent.TOOL_TYPE_FINGER);
	}
    public Spot(MotionEvent.PointerCoords _pc, long time, int _tool) {
        this(_pc.x, _pc.y, _pc.size, _pc.pressure, time, _tool);
    }
	public MotionEvent.PointerCoords toPointerCoords() {
		final MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
		pc.x = x;
		pc.y = y;
		pc.size = size;
		pc.pressure = pressure;
		return pc;
	}
	
	public Spot sub(Spot s) {
		return new Spot(x - s.x, y - s.y, size - s.size, pressure - s.pressure, time - s.time, tool);
	}
	public Spot sub(float _x, float _y) {
		return new Spot(x - _x, y - _y, size, pressure, time, tool);
	}
	public Spot add(Spot s) {
		return new Spot(x + s.x, y + s.y, size + s.size, pressure + s.pressure, time + s.time, tool);
	}
	public Spot add(float _x, float _y) {
		return new Spot(x + _x, y + _y, size, pressure, time, tool);
	}
}
