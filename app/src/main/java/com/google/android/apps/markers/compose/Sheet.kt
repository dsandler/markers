/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.google.android.apps.markers.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.apps.markers.Spot
import com.google.android.apps.markers.SpotFilter
import com.google.android.apps.markers.TiledBitmapCanvas

data class SheetState(
    val backgroundColor: Color = Color.White,
    val penColor: Color = Color.Black,
    val penWidthMin: Float = 1f,
    val penWidthMax: Float = 25f,
    val penType: Int = 0, // TYPE_WHITEBOARD
    val penShape: Int = 0 // SHAPE_CIRCLE
)

class SheetViewModel {
    var width: Int = 1024
    var height: Int = 1024
    var state by mutableStateOf(SheetState())
    var refresh by mutableLongStateOf(0L)
    var tiledCanvas: TiledBitmapCanvas? = null
    private var spotFilter: SpotFilter? = null
    private var isDrawing = false
    
    fun initializeCanvas() {
        if (tiledCanvas == null || tiledCanvas!!.getWidth() != width || tiledCanvas!!.getHeight() != height) {
            tiledCanvas?.recycleBitmaps()
            tiledCanvas = TiledBitmapCanvas(width, height, Bitmap.Config.ARGB_8888)
            
            // Create a plotter that draws to the tiled canvas
            val plotter = object : SpotFilter.Plotter {
                override fun plot(spot: Spot) {
                    val paint = Paint()
                    paint.color = state.penColor.toArgb()
                    paint.isAntiAlias = true
                    
                    val radius = state.penWidthMin + (state.penWidthMax - state.penWidthMin) * spot.pressure
                    tiledCanvas?.drawCircle(spot.x, spot.y, radius, paint)
                }
            }
            
            spotFilter = SpotFilter(6, 0.65f, 0.9f, plotter)
            
            // Debug log
            android.util.Log.d("Sheet", "Initialized canvas: ${width}x${height}")
        }
    }
    
    fun updatePenColor(color: Color) {
        state = state.copy(penColor = color)
        // Force recreation of spotFilter to update the plotter with new color
        recreateSpotFilter()
    }
    
    fun updatePenWidth(min: Float, max: Float) {
        state = state.copy(penWidthMin = min, penWidthMax = max)
        // Force recreation of spotFilter to update the plotter with new width
        recreateSpotFilter()
    }
    
    fun updatePenType(type: Int, shape: Int) {
        state = state.copy(penType = type, penShape = shape)
    }
    
    private fun recreateSpotFilter() {
        if (tiledCanvas != null) {
            val plotter = object : SpotFilter.Plotter {
                override fun plot(spot: Spot) {
                    val paint = Paint()
                    paint.color = state.penColor.toArgb()
                    paint.isAntiAlias = true
                    
                    val radius = state.penWidthMin + (state.penWidthMax - state.penWidthMin) * spot.pressure
                    tiledCanvas?.drawCircle(spot.x, spot.y, radius, paint)
                }
            }
            
            spotFilter = SpotFilter(6, 0.65f, 0.9f, plotter)
        }
    }
    
    fun handleTouchDown(x: Float, y: Float, pressure: Float, size: Float, toolType: Int) {
        if (spotFilter == null) return
        
        isDrawing = true
        val spot = Spot(x, y, size, pressure + size, System.currentTimeMillis(), toolType)
        spotFilter?.add(spot)
    }
    
    fun handleTouchMove(x: Float, y: Float, pressure: Float, size: Float, toolType: Int) {
        if (!isDrawing || spotFilter == null) return
        
        val spot = Spot(x, y, size, pressure + size, System.currentTimeMillis(), toolType)
        spotFilter?.add(spot)
    }
    
    fun handleTouchUp() {
        if (!isDrawing || spotFilter == null) return
        
        isDrawing = false
        spotFilter?.finish()
        tiledCanvas?.commit()
    }
    
    fun clear() {
        tiledCanvas?.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR)
        tiledCanvas?.commit()
    }
    
    fun getBitmap(): Bitmap? {
        return tiledCanvas?.toBitmap()
    }
    
    fun dispose() {
        tiledCanvas?.recycleBitmaps()
        tiledCanvas = null
        spotFilter = null
    }

    fun processMotionEvent(event: MotionEvent, scale: Float): Boolean {
        val pointerIndex = event.actionIndex
        val x = event.getX(pointerIndex) * scale
        val y = event.getY(pointerIndex) * scale
        val pressure = event.getPressure(pointerIndex)
        val size = event.getSize(pointerIndex)
        val toolType = event.getToolType(pointerIndex)

        Log.v("Sheet", "processMotionEvent: $event")

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handleTouchDown(x, y, pressure, size, toolType)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1) return false
                // Handle all active pointers
                for (i in 0 until event.pointerCount) {
                    val px = event.getX(i)
                    val py = event.getY(i)
                    val ppressure = event.getPressure(i)
                    val psize = event.getSize(i)
                    val ptoolType = event.getToolType(i)
                    handleTouchMove(px, py, ppressure, psize, ptoolType)
                }
                refresh = System.currentTimeMillis()
                return true
            }
            MotionEvent.ACTION_UP -> {
                handleTouchUp()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount > 1) return false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Only finish if this was the last pointer
                if (event.pointerCount == 1) {
                    handleTouchUp()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handleTouchUp()
                return true
            }
        }
        return false
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Sheet(
    viewModel: SheetViewModel,
    modifier: Modifier = Modifier
) {
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            viewModel.dispose()
        }
    }

    Box(
        modifier = modifier
            .background(viewModel.state.backgroundColor)
            .clipToBounds()
            .pointerInteropFilter {
                return@pointerInteropFilter viewModel.processMotionEvent(it, size.width / tiledCanvas.width)
            }
            .drawWithContent {
                viewModel.tiledCanvas?.let { tiledCanvas ->
                    scale(scale = size.width / tiledCanvas.width, pivot = Offset.Zero) {
                        drawIntoCanvas { canvas ->
                            viewModel.refresh // read
                            tiledCanvas.drawTo(canvas.nativeCanvas, 0f, 0f, null, false)
                        }
                    }
                }
            }
    )
}
