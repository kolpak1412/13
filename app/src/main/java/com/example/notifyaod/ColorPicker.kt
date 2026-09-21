package com.example.notifyaod

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

private fun hsvToColorInt(h: Float, s: Float, v: Float): Int =
    android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))

/** Полноценный выбор цвета: поле насыщенность/яркость + полоса оттенка. Любой цвет, а не список. */
@Composable
fun ColorPickerDialog(initialColor: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val initialHsv = remember(initialColor) {
        val arr = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor, arr)
        arr
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2].coerceAtLeast(0.35f)) }
    val color = remember(hue, sat, value) { hsvToColorInt(hue, sat, value) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Цвет рамки") },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(color)),
                )
                Spacer(Modifier.height(20.dp))
                SatValueBox(hue = hue, sat = sat, value = value) { s, v -> sat = s; value = v }
                Spacer(Modifier.height(20.dp))
                HueBar(hue = hue) { hue = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(color) }) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun SatValueBox(hue: Float, sat: Float, value: Float, onChange: (Float, Float) -> Unit) {
    val hueColor = remember(hue) { Color(hsvToColorInt(hue, 1f, 1f)) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(hueColor)
                .background(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onChange(
                            (offset.x / widthPx).coerceIn(0f, 1f),
                            1f - (offset.y / heightPx).coerceIn(0f, 1f),
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        onChange(
                            (change.position.x / widthPx).coerceIn(0f, 1f),
                            1f - (change.position.y / heightPx).coerceIn(0f, 1f),
                        )
                    }
                },
        )
        val indicatorSize = 22.dp
        Box(
            modifier = Modifier
                .offset(
                    x = (maxWidth * sat) - indicatorSize / 2,
                    y = (maxHeight * (1f - value)) - indicatorSize / 2,
                )
                .size(indicatorSize)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, Color.Black.copy(alpha = 0.3f), CircleShape),
        )
    }
}

@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    val hueColors = remember {
        (0..360 step 30).map { Color(hsvToColorInt(it.toFloat() % 360f, 1f, 1f)) }
    }
    val barHeight = 36.dp
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(RoundedCornerShape(18.dp)),
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Brush.horizontalGradient(hueColors))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onChange((offset.x / widthPx).coerceIn(0f, 1f) * 360f)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        onChange((change.position.x / widthPx).coerceIn(0f, 1f) * 360f)
                    }
                },
        )
        val indicatorSize = 30.dp
        Box(
            modifier = Modifier
                .offset(
                    x = (maxWidth * (hue / 360f)) - indicatorSize / 2,
                    y = (barHeight - indicatorSize) / 2,
                )
                .size(indicatorSize)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, Color.Black.copy(alpha = 0.3f), CircleShape),
        )
    }
}
