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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.lifecycle.viewModelScope
import com.google.android.apps.markers.ToolButton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.dsandler.apps.markers.R
import kotlin.math.max
import kotlin.math.min

val HYPER_LINE_THICKNESS = 4.dp

val enterAnim = expandIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
val exitAnim = shrinkOut(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessHigh))

@Composable
fun Palette(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .hyperBorder(
                width = HYPER_LINE_THICKNESS,
                depth = HYPER_LINE_THICKNESS,
                cornerRadius = 2 * HYPER_LINE_THICKNESS,
                style = HyperStyle.Rounded)
            .background(color = Color(0xFFCCCCCC))
    ) {
        AnimatedVisibility(visible = expanded,
            enter = enterAnim,
            exit = exitAnim,
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }
        Box(Modifier.clickable { expanded = !expanded }) {
            Text("(X)")
        }
    }
}

@Composable
fun PenSizeButton(
    widthMin: Float,
    widthMax: Float,
    viewModel: MarkersBoardViewModel,
    modifier: Modifier = Modifier,
) {
    var m = modifier
        .background(color = Color.White)
        .drawBehind {
            //drawCircle(color = Color.Yellow, center = center, radius = 100.dp.toPx())
            drawCircle(color = Color(0f, 0f, 0f, 0.5f), radius = min(widthMax, 24.dp.toPx()), center = center)
            drawCircle(color = Color.Black, radius = max(widthMin, 1.dp.toPx()), center = center)
        }
    if (viewModel.penState.widthMin == widthMin && viewModel.penState.widthMax == widthMax) {
        val borderWidth = 6.dp
        m = m.border(
            width = borderWidth,
            color = Color.Black,
            shape = RoundedCornerShape(3.dp)
        )
    }
    SmallButton(
        modifier = m,
        onClick = {
            viewModel.updatePenWidth(widthMin, widthMax)
        }
    )
}
@Composable
fun PenPalette(modifier: Modifier = Modifier, viewModel: MarkersBoardViewModel) {
    Palette(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PenSizeButton(4f, 8f, viewModel)
            PenSizeButton(1f, 25f, viewModel)
            PenSizeButton(12f, 48f, viewModel)
        }
    }
}

@Composable
fun ColorSwatch(
    color: Color,
    viewModel: MarkersBoardViewModel,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    width: Dp = 48.dp,
) {
    var m = modifier
        .background(color)
    if (viewModel.penState.color == color) {
        val borderWidth = 6.dp
        m = m.border(
            width = borderWidth,
            color = if (color == Color.Black) Color.Red else Color.Black,
            shape = RoundedCornerShape(3.dp)
        )
    }
    SmallButton(
        modifier = m,
        height = height,
        width = width,
        onClick = {
            viewModel.updatePenColor(color)
        }
    )
}

@Composable
fun ColorPalette(modifier: Modifier = Modifier, viewModel: MarkersBoardViewModel) {
    Palette(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val grays = floatArrayOf(0.25f, 0.5f, 0.75f)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ColorSwatch(color = Color.Black, viewModel = viewModel)
                grays.forEach { l ->
                    ColorSwatch(color = Color.hsl(0f, 0f, l), viewModel = viewModel)
                }
                ColorSwatch(color = Color.White, viewModel = viewModel)
            }
            val lvalues = floatArrayOf(0.25f, 0.5f, 0.75f, 0.82f, 0.9f)
            val hues = floatArrayOf(
                0f, 30f, 60f, 120f, 240f, 300f
                )
            hues.forEach { h ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    lvalues.forEach { l ->
                        ColorSwatch(
                            color = Color.hsl(h, 1f, l),
                            viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun ActionPalette(modifier: Modifier = Modifier, viewModel: MarkersBoardViewModel) {
    val scope = rememberCoroutineScope()

    Palette(modifier = modifier) {
        Row {
            SmallButton(onClick = {
                scope.launch { viewModel.onClick.send(ClickEventType.CLEAR) }
            }) {
                Image(
                    painter = painterResource(R.drawable.scribble),
                    contentDescription = "Clear",
                    colorFilter = ColorFilter.tint(Color.Black),
                    modifier = Modifier
                        .fillMaxSize()
                )
            }
            SmallButton(onClick = {
                scope.launch { viewModel.onClick.send(ClickEventType.SAVE) }
            }) {
                Image(
                    painter = painterResource(R.drawable.check),
                    contentDescription = "Save",
                    colorFilter = ColorFilter.tint(Color.Black),
                    modifier = Modifier
                        .fillMaxSize()
                )
            }
        }
    }

}


@Composable
fun SmallButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    width: Dp = 48.dp,
    cornerRadius: Dp = 4.dp,
    content: @Composable () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(onClick = onClick)
            .then(modifier)
    ) {
        content()
    }
}

@Preview(
    showBackground = false,
    widthDp = 300,
    heightDp = 500,
)
@Composable
fun PalettePreview() {
    Box {
        PenPalette(
            modifier = Modifier
                .wrapContentSize()
                .align(Alignment.TopStart),
            viewModel = MarkersBoardViewModel()
        )
        ColorPalette(
            modifier = Modifier
                .wrapContentSize()
                .align(Alignment.BottomStart),
            viewModel = MarkersBoardViewModel()
        )
    }
}