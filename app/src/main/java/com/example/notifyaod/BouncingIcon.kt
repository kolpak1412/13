package com.example.notifyaod

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.util.Random
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Иконка, отскакивающая от границ области, как логотип DVD. Без анимации появления —
 * иконка просто сразу начинает двигаться.
 *
 * Область отскока задаётся [insetPx] (отступ от края экрана) и [cornerPx] (скругление углов
 * этой области). Когда на экране горит рамка, сюда передаётся её внутренний край — и иконка
 * отскакивает именно от рамки, включая скруглённые углы. Когда рамка гаснет, значения
 * становятся нулевыми и иконка начинает отскакивать от краёв экрана. Читаются они каждый
 * кадр, поэтому переключение происходит на лету.
 */
@Composable
fun BouncingIcon(
    icon: ImageBitmap,
    sizeDp: Int,
    speedDpPerSec: Int,
    seed: Int,
    insetPx: Float,
    cornerPx: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val inset by rememberUpdatedState(insetPx)
    val corner by rememberUpdatedState(cornerPx)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sizePx = with(density) { sizeDp.dp.toPx() }
        val speedPx = with(density) { speedDpPerSec.dp.toPx() }
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()

        val rnd = remember(seed) { Random(seed.toLong()) }
        // Скорость по осям: слегка разная, чтобы иконки не ходили одинаково
        val vel = remember(seed) {
            floatArrayOf(
                if (rnd.nextBoolean()) 1f else -1f,
                (if (rnd.nextBoolean()) 1f else -1f) * (0.5f + 0.4f * rnd.nextFloat()),
            )
        }
        // Центр иконки в пикселях
        var center by remember(seed) { mutableStateOf(Offset.Zero) }
        var placed by remember(seed) { mutableStateOf(false) }

        LaunchedEffect(boxW, boxH, sizePx, speedPx) {
            val radius = sizePx / 2f
            if (!placed) {
                center = Offset(
                    boxW * (0.2f + 0.6f * rnd.nextFloat()),
                    boxH * (0.2f + 0.6f * rnd.nextFloat()),
                )
                placed = true
            }

            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now

                var x = center.x + vel[0] * speedPx * 0.8f * dt
                var y = center.y + vel[1] * speedPx * 0.8f * dt

                val ins = inset
                val cor = corner

                // Прямоугольник, внутри которого может находиться ЦЕНТР иконки
                val minX = ins + radius
                val maxX = boxW - ins - radius
                val minY = ins + radius
                val maxY = boxH - ins - radius

                if (maxX > minX) {
                    if (x < minX) { x = minX; vel[0] = kotlin.math.abs(vel[0]) }
                    else if (x > maxX) { x = maxX; vel[0] = -kotlin.math.abs(vel[0]) }
                } else {
                    x = (minX + maxX) / 2f
                }
                if (maxY > minY) {
                    if (y < minY) { y = minY; vel[1] = kotlin.math.abs(vel[1]) }
                    else if (y > maxY) { y = maxY; vel[1] = -kotlin.math.abs(vel[1]) }
                } else {
                    y = (minY + maxY) / 2f
                }

                // Скруглённые углы: центр угловой окружности не смещается при сжатии
                // области на радиус иконки, меняется только допустимый радиус.
                if (cor > 0f && maxX > minX && maxY > minY) {
                    val allowed = (cor - radius).coerceAtLeast(0f)
                    val cxLeft = ins + cor
                    val cxRight = boxW - ins - cor
                    val cyTop = ins + cor
                    val cyBottom = boxH - ins - cor

                    val ccx = when {
                        x < cxLeft -> cxLeft
                        x > cxRight -> cxRight
                        else -> Float.NaN
                    }
                    val ccy = when {
                        y < cyTop -> cyTop
                        y > cyBottom -> cyBottom
                        else -> Float.NaN
                    }
                    if (!ccx.isNaN() && !ccy.isNaN()) {
                        val dx = x - ccx
                        val dy = y - ccy
                        val d = sqrt(dx * dx + dy * dy)
                        if (d > allowed && d > 0.0001f) {
                            val nx = dx / d
                            val ny = dy / d
                            x = ccx + nx * allowed
                            y = ccy + ny * allowed
                            val dot = vel[0] * nx + vel[1] * ny
                            if (dot > 0f) {
                                vel[0] -= 2f * dot * nx
                                vel[1] -= 2f * dot * ny
                            }
                        }
                    }
                }

                center = Offset(x, y)
            }
        }

        if (placed) {
            val radius = sizePx / 2f
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (center.x - radius).roundToInt(),
                            (center.y - radius).roundToInt(),
                        )
                    }
                    .size(sizeDp.dp),
            )
        }
    }
}
