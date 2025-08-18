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
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import com.google.android.apps.markers.MarkersActivity
import com.google.android.apps.markers.MarkersUtils
import com.google.android.apps.markers.MediaClient
import com.google.android.apps.markers.Slate
import com.google.android.apps.markers.compose.theme.MarkersTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.dsandler.apps.markers.R

const val DEBUG = false

data class PenState(
    val color: Color = Color.Black,
    val widthMin: Float = 1f,
    val widthMax: Float = 25f,
)

class MarkersBoardViewModel() : ViewModel() {
    var penState by mutableStateOf(PenState())
    var sheetViewModel = SheetViewModel()
    var onClick: Channel<ClickEventType> = Channel(capacity = 1)
    
    fun updatePenColor(color: Color) {
        penState = penState.copy(color = color)
        sheetViewModel.updatePenColor(color)
    }
    
    fun updatePenWidth(min: Float, max: Float) {
        penState = penState.copy(widthMin = min, widthMax = max)
        sheetViewModel.updatePenWidth(min, max)
    }
}

enum class ClickEventType {
    CLEAR,
    SAVE
}

@Preview(showBackground = true)
@Composable
fun MarkersScene() {
    val vm = MarkersBoardViewModel()

    MarkersBoardView(viewModel = vm)
    Box(modifier = Modifier.fillMaxSize()) {
        ActionPalette(
            viewModel = vm,
            modifier = Modifier
                .safeContentPadding()
                .wrapContentSize()
                .align(Alignment.TopEnd)
        )
        PenPalette(
            viewModel = vm,
            modifier = Modifier
                .safeContentPadding() // will always avoid transient system bars
                .wrapContentSize()
                .align(Alignment.TopStart)
        )
        ColorPalette(
            viewModel = vm,
            modifier = Modifier
                .safeContentPadding() // will always avoid transient system bars
                .wrapContentSize(align = Alignment.TopCenter)
                .align(Alignment.BottomStart)
        )
    }
}

class MarkersBoard : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val wiController = WindowInsetsControllerCompat(window, window.decorView)

        // Transient (translucent) status bars will appear on the first swipe, but will not
        // be interactive until the second touch
        wiController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        wiController.isAppearanceLightStatusBars = true
        /*
        window.decorView.setOnApplyWindowInsetsListener { view, windowInsets ->
            if (windowInsets.isVisible(WindowInsetsCompat.Type.navigationBars())
                || windowInsets.isVisible(WindowInsetsCompat.Type.statusBars())) {
//                binding.toggleFullscreenButton.setOnClickListener {
//                    // Hide both the status bar and the navigation bar.
//                    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
//                }

// if using the default systemBarsBehavior, you'll want this:
//                view.postDelayed({
//                     wiController.hide(WindowInsetsCompat.Type.systemBars())
//                }, 1000L)
            } else {
//                binding.toggleFullscreenButton.setOnClickListener {
//                    // Show both the status bar and the navigation bar.
//                    windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
//                }
            }
            view.onApplyWindowInsets(windowInsets)
        }
         */

        wiController.hide(WindowInsetsCompat.Type.systemBars())

        setContent {
            MarkersTheme {
                MarkersScene()
            }
        }
    }
}

//@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MarkersBoardPreview() {
    MarkersTheme {
        MarkersScene()
    }
}

fun saveBitmap(
    sheetViewModel: SheetViewModel,
    bitmap: Bitmap,
    filename: String?,
    temporary: Boolean,
    animate: Boolean,
    share: Boolean,
    clear: Boolean
) {
    // TODO: Implement save functionality for Sheet
    // For now, we'll use the bitmap passed in
    // MediaClient.saveBitmap(
    //     context,
    //     bitmap,
    //     true,
    //     null,
    //     -1,
    //     -1,
    //     filename,
    //     temporary,
    //     animate,
    //     share,
    //     clear,
    //     null
    // )
}

fun saveDrawing(sheetViewModel: SheetViewModel) {
    val bitmap = sheetViewModel.getBitmap()
    if (bitmap == null) return
    
    val filename = MarkersUtils.createDrawingFilename()
    saveBitmap(sheetViewModel, bitmap, filename, false, false, false, false)
    // Toast.makeText(context, "Drawing saved: $filename", Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MarkersBoardView(viewModel: MarkersBoardViewModel) {
    val scope = rememberCoroutineScope()

    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, offsetChange, rotationChange ->
        scale *= zoomChange
        rotation += rotationChange
        offset += offsetChange
    }

    viewModel.sheetViewModel.initializeCanvas()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Cyan)
            .padding(16.dp)
            .pointerInteropFilter {
                Log.v("MarkersBoard", "pointerInteropFilter: peeking at event: $it")
                false
            }
            //.transformable(state = state)
    ) {
        Sheet(
            viewModel = viewModel.sheetViewModel,
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxSize()
                .hyperBorder()
                .align(Alignment.Center)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                    rotationZ = rotation
                ),
        )
    }
    
    // Handle click events
    scope.launch {
        for (event in viewModel.onClick) {
            when (event) {
                ClickEventType.CLEAR -> viewModel.sheetViewModel.clear()
                ClickEventType.SAVE -> saveDrawing(viewModel.sheetViewModel)
            }
        }
    }
}

