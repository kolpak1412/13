package com.example.notifyaod

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln

/**
 * Чёрный экран: по краям цветная рамка, внутри — гуляющие иконки приложений, от которых
 * недавно пришли уведомления. Пока рамка горит, иконки отскакивают от её внутреннего края
 * (с учётом толщины и скругления); когда рамка гаснет — от краёв экрана.
 */
class AodActivity : ComponentActivity() {

    private val repo by lazy { SettingsRepository(applicationContext) }

    /** пакет -> время последнего уведомления (elapsedRealtime, мс) */
    private val active = mutableStateMapOf<String, Long>()

    // ---- Автояркость по датчику освещённости ----
    private val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    private val lightSensor by lazy { sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT) }
    private var smoothedLux = -1f
    private var autoBrightnessOn = false

    private val lightListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val lux = event.values.getOrNull(0) ?: return
            smoothedLux = if (smoothedLux < 0f) lux else smoothedLux + (lux - smoothedLux) * 0.25f
            setScreenBrightness(luxToBrightness(smoothedLux))
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** Логарифмическая шкала: разница между 1 и 10 люкс важнее, чем между 1000 и 1009. */
    private fun luxToBrightness(lux: Float): Float {
        val minB = 0.012f
        val maxB = 0.55f
        val clampedLux = lux.coerceIn(0f, 600f)
        val t = (ln(1f + clampedLux) / ln(1f + 600f)).coerceIn(0f, 1f)
        return minB + (maxB - minB) * t
    }

    private fun setScreenBrightness(value: Float) {
        val lp = window.attributes
        lp.screenBrightness = value.coerceIn(0.01f, 1f)
        window.attributes = lp
    }

    private fun startAutoBrightness() {
        autoBrightnessOn = true
        val sensor = lightSensor
        if (sensor != null) {
            smoothedLux = -1f
            sensorManager?.registerListener(lightListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            setScreenBrightness(0.05f)
        }
    }

    private fun stopAutoBrightness() {
        if (!autoBrightnessOn) return
        autoBrightnessOn = false
        sensorManager?.unregisterListener(lightListener)
    }

    private val finisher = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setBackgroundDrawableResource(android.R.color.black)
        super.onCreate(savedInstanceState)
        visible = true

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        addPackages(intent)

        val lp = window.attributes
        lp.screenBrightness = 0.02f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.attributes = lp

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, finisher, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        setContent { AodScreen() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        addPackages(intent)
    }

    override fun onDestroy() {
        visible = false
        stopAutoBrightness()
        try { unregisterReceiver(finisher) } catch (_: Exception) { }
        super.onDestroy()
    }

    private fun addPackages(intent: Intent) {
        val list = intent.getStringArrayListExtra(EXTRA_PACKAGES).orEmpty()
        val now = SystemClock.elapsedRealtime()
        list.forEach { active[it] = now }
        keepAwake(true)
    }

    private fun keepAwake(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun applyBrightness(s: AodSettings) {
        if (s.autoBrightness) {
            startAutoBrightness()
        } else {
            stopAutoBrightness()
            setScreenBrightness(s.brightnessPercent / 100f)
        }
    }

    @Composable
    private fun AodScreen() {
        val settings by repo.settings.collectAsState(initial = AodSettings())
        val latest by rememberUpdatedState(settings)
        var clockMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
        val density = LocalDensity.current

        LaunchedEffect(settings.autoBrightness, settings.brightnessPercent) {
            applyBrightness(settings)
        }

        LaunchedEffect(Unit) {
            var emptySince = 0L
            var lastCheck = 0L
            while (true) {
                withFrameNanos { }
                val now = SystemClock.elapsedRealtime()
                clockMs = now
                if (now - lastCheck < 250L) continue
                lastCheck = now

                if (QuietHours.isQuiet(latest)) {
                    if (active.isNotEmpty()) active.clear()
                    finish()
                    return@LaunchedEffect
                }

                if (!latest.timeoutUnlimited) {
                    val timeoutMs = latest.timeoutSec * 1000L
                    active.entries
                        .filter { now - it.value > timeoutMs }
                        .map { it.key }
                        .forEach { active.remove(it) }
                }

                if (active.isEmpty()) {
                    if (emptySince == 0L) {
                        emptySince = now
                        keepAwake(false)
                    } else if (now - emptySince > 20_000L) {
                        finish()
                        return@LaunchedEffect
                    }
                } else if (emptySince != 0L) {
                    emptySince = 0L
                    keepAwake(true)
                }
            }
        }

        // Горит ли сейчас рамка — от этого зависит, от чего отскакивают иконки
        val frameOn = settings.frameEnabled && (settings.frameUnlimited || settings.frameSec > 0)
        val frameVisible = frameOn && active.values.any { t ->
            val age = clockMs - t
            age >= 0L && (settings.frameUnlimited || age < settings.frameSec * 1000L)
        }
        val bounceInset = if (frameVisible) {
            with(density) { settings.frameThicknessDp.dp.toPx() }
        } else 0f
        val activeAnimation = FrameAnimation.of(settings.frameAnimation)
        val activeCornerDp = if (activeAnimation == FrameAnimation.FLUID)
            settings.fluidCornerDp else settings.frameCornerDp
        val bounceCorner = if (frameVisible) {
            with(density) {
                (activeCornerDp - settings.frameThicknessDp).coerceAtLeast(0).dp.toPx()
            }
        } else 0f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { finish() }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawFrames(active, settings, clockMs)
            }
            // ---- «Жидкий неон»: полноэкранный GL-эффект поверх всего ----
            if (settings.frameEnabled &&
                FrameAnimation.of(settings.frameAnimation) == FrameAnimation.FLUID
            ) {
                // цвет и время — от самого свежего уведомления
                val newest = active.entries.maxByOrNull { it.value }
                val fluidColor = Color(
                    settings.frameColors[newest?.key] ?: DEFAULT_FRAME_COLOR,
                )
                NotificationFluidEffect(
                    visible = frameVisible,
                    color = fluidColor,
                    intensity = settings.fluidIntensity / 100f,
                    // тот же ползунок скорости, что и у рамки: 2200 мс = «обычная» скорость
                    speed = (2200f / settings.frameSpeedMs.coerceAtLeast(300)) * 0.35f,
                    blur = settings.fluidBlur.toFloat(),
                    // толщина рамки в dp -> толщина неонового ядра
                    thickness = (settings.frameThicknessDp / 6f * 1.5f).coerceIn(0.4f, 4f),
                    // новое уведомление во время показа: вместо второго слоя — новая волна
                    pulseKey = newest?.value ?: 0L,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (settings.iconsEnabled) {
                for (pkg in active.keys.sorted()) {
                    key(pkg) { AppBouncer(pkg, latest, bounceInset, bounceCorner) }
                }
            }

        }
    }

    @Composable
    private fun AppBouncer(pkg: String, s: AodSettings, insetPx: Float, cornerPx: Float) {
        val icon = remember(pkg) { loadIcon(pkg) }
        if (icon != null) {
            BouncingIcon(
                icon = icon,
                sizeDp = s.iconSizeDp,
                speedDpPerSec = s.speedDpPerSec,
                seed = pkg.hashCode(),
                insetPx = insetPx,
                cornerPx = cornerPx,
            )
        }
    }

    private fun loadIcon(pkg: String): ImageBitmap? = try {
        packageManager.getApplicationIcon(pkg).toBitmap(256, 256).asImageBitmap()
    } catch (e: Exception) {
        null
    }

    companion object {
        const val EXTRA_PACKAGES = "extra_packages"

        @Volatile
        var visible = false

        /** HSV (hue 0..360, saturation/value 0..1) -> Color, через проверенный Android SDK API. */
        private fun hsvColor(hue: Float, saturation: Float, value: Float, alpha: Float = 1f): Color {
            val argb = android.graphics.Color.HSVToColor(
                floatArrayOf(hue, saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f)),
            )
            return Color(argb).copy(alpha = alpha)
        }

        /**
         * Рамка по периметру экрана. Анимации появления нет — выбранный в настройках стиль
         * начинает играть сразу. Гаснет плавно за 600 мс до конца отведённого времени
         * (если не включён режим «без ограничений»).
         */
        private fun DrawScope.drawFrames(active: Map<String, Long>, s: AodSettings, now: Long) {
            if (!s.frameEnabled) return
            val unlimited = s.frameUnlimited
            val frameMs = s.frameSec * 1000L
            if (!unlimited && frameMs <= 0L) return

            val thickness = s.frameThicknessDp.dp.toPx()
            val animation = FrameAnimation.of(s.frameAnimation)
            val cornerDp = if (animation == FrameAnimation.FLUID) s.fluidCornerDp else s.frameCornerDp
            val radius = cornerDp.dp.toPx()
            val periodMs = s.frameSpeedMs.toLong().coerceAtLeast(300L)
            val glowMultiplier = (s.frameGlowPercent / 100f).coerceIn(0.1f, 1.5f)

            val insetTopLeft = Offset(thickness / 2f, thickness / 2f)
            val insetSize = Size(size.width - thickness, size.height - thickness)
            val insetCorner = CornerRadius((radius - thickness / 2f).coerceAtLeast(0f))

            for ((pkg, t) in active) {
                val age = now - t
                if (age < 0L) continue
                if (!unlimited && age >= frameMs) continue

                val color = Color(s.frameColors[pkg] ?: DEFAULT_FRAME_COLOR)
                val alphaMul = if (unlimited) 1f else ((frameMs - age) / 600f).coerceIn(0f, 1f)

                drawFrameLoop(
                    animation = animation,
                    color = color,
                    alphaMul = alphaMul,
                    now = now,
                    periodMs = periodMs,
                    thickness = thickness,
                    insetTopLeft = insetTopLeft,
                    insetSize = insetSize,
                    insetCorner = insetCorner,
                    glowMultiplier = glowMultiplier,
                )
            }
        }

        private fun DrawScope.drawFrameLoop(
            animation: FrameAnimation,
            color: Color,
            alphaMul: Float,
            now: Long,
            periodMs: Long,
            thickness: Float,
            insetTopLeft: Offset,
            insetSize: Size,
            insetCorner: CornerRadius,
            glowMultiplier: Float,
        ) {
            if (alphaMul <= 0f) return
            val phase = (now % periodMs) / periodMs.toFloat()
            when (animation) {
                FrameAnimation.WAVE -> {
                    // Одна переливающаяся волна, бегущая по кругу.
                    val steps = 48
                    val stops = Array(steps + 1) { i ->
                        val p = i / steps.toFloat()
                        val a = 0.5f - 0.5f * cos(2.0 * PI * (p - phase)).toFloat()
                        p to color.copy(alpha = (a * alphaMul * glowMultiplier).coerceIn(0f, 1f))
                    }
                    drawRoundRect(
                        brush = Brush.sweepGradient(*stops, center = center),
                        topLeft = insetTopLeft,
                        size = insetSize,
                        cornerRadius = insetCorner,
                        style = Stroke(width = thickness),
                    )
                }

                FrameAnimation.DUAL_BARS -> {
                    // Не резкие "полосы", а плавная волна яркости с двумя неровными
                    // вспышками за один оборот — по заданной кривой (см. drawDualBarsGlow).
                    drawDualBarsGlow(
                        baseColor = color,
                        phase = phase,
                        alphaMul = alphaMul,
                        thickness = thickness,
                        insetTopLeft = insetTopLeft,
                        insetSize = insetSize,
                        insetCorner = insetCorner,
                        glowMultiplier = glowMultiplier,
                    )
                }

                FrameAnimation.PULSE -> {
                    // Плавное «дыхание» без ступенек: много тонких слоёв с непрерывным
                    // экспоненциальным затуханием прозрачности вместо 2-3 резких колец.
                    val raw = 0.5f - 0.5f * cos(2.0 * PI * phase).toFloat() // 0..1
                    val breathe = raw * raw * (3f - 2f * raw) // smoothstep — мягче на краях
                    val glow = lerp(color, Color.White, breathe * 0.18f)
                    val intensity = (0.10f + 0.55f * breathe) * glowMultiplier

                    val layers = 10
                    for (i in layers downTo 1) {
                        val t = i / layers.toFloat() // 1 (внешний) .. 1/layers (у ядра)
                        val falloff = exp(-3.4f * t * t) // непрерывная кривая, не ступени
                        val widthMul = 0.5f + t * 2.4f
                        drawRoundRect(
                            color = glow.copy(alpha = (falloff * intensity).coerceIn(0f, 1f) * alphaMul),
                            topLeft = insetTopLeft,
                            size = insetSize,
                            cornerRadius = insetCorner,
                            style = Stroke(width = thickness * widthMul),
                        )
                    }
                    // яркое ядро поверх
                    drawRoundRect(
                        color = glow.copy(alpha = ((0.55f + 0.45f * breathe) * alphaMul * glowMultiplier).coerceIn(0f, 1f)),
                        topLeft = insetTopLeft,
                        size = insetSize,
                        cornerRadius = insetCorner,
                        style = Stroke(width = thickness * (0.55f + 0.3f * breathe)),
                    )
                }

                // «Жидкий неон» рисуется отдельным GL-слоем (FluidNeon.kt), не на Canvas.
                FrameAnimation.FLUID -> Unit

                FrameAnimation.RAINBOW -> {
                    val steps = 60
                    val stops = Array(steps + 1) { i ->
                        val p = i / steps.toFloat()
                        val hue = ((p + phase) % 1f) * 360f
                        p to hsvColor(hue, 0.75f, 1f, (alphaMul * 0.9f * glowMultiplier).coerceIn(0f, 1f))
                    }
                    drawRoundRect(
                        brush = Brush.sweepGradient(*stops, center = center),
                        topLeft = insetTopLeft,
                        size = insetSize,
                        cornerRadius = insetCorner,
                        style = Stroke(width = thickness),
                    )
                }
            }
        }

        /**
         * «Две полосы» с нуля: один sweep-gradient с последовательностью
         * прозрачность 0 → цвет → цвет → прозрачность 0 → цвет → цвет.
         * Две яркие зоны движутся по периметру как две отдельные полосы.
         */
        private fun DrawScope.drawDualBarsGlow(
            baseColor: Color,
            phase: Float,
            alphaMul: Float,
            thickness: Float,
            insetTopLeft: Offset,
            insetSize: Size,
            insetCorner: CornerRadius,
            glowMultiplier: Float,
        ) {
            val c = baseColor
            val a = alphaMul * glowMultiplier
            val stops = arrayOf(
                0.00f to c.copy(alpha = 0f),
                0.18f to c.copy(alpha = a.coerceIn(0f, 1f)),
                0.36f to c.copy(alpha = a.coerceIn(0f, 1f)),
                0.52f to c.copy(alpha = 0f),
                0.70f to c.copy(alpha = a.coerceIn(0f, 1f)),
                1.00f to c.copy(alpha = a.coerceIn(0f, 1f)),
            )

            // Сдвигаем весь градиент по периметру — это и есть движение двух полос.
            val shiftedStops = Array(stops.size) { i ->
                val (position, color) = stops[i]
                (((position + phase) % 1f) + 1f) % 1f to color
            }.sortedBy { it.first }.toTypedArray()
            val brush = Brush.sweepGradient(*shiftedStops, center = center)

            // Мягкий ореол строится из той же самой маски, поэтому обе полосы
            // остаются синхронными и не превращаются в отдельные анимации.
            drawRoundRect(
                brush = brush,
                topLeft = insetTopLeft,
                size = insetSize,
                cornerRadius = insetCorner,
                style = Stroke(width = thickness + 9.dp.toPx()),
                alpha = 0.12f,
            )
            drawRoundRect(
                brush = brush,
                topLeft = insetTopLeft,
                size = insetSize,
                cornerRadius = insetCorner,
                style = Stroke(width = thickness + 5.dp.toPx()),
                alpha = 0.20f,
            )
            drawRoundRect(
                brush = brush,
                topLeft = insetTopLeft,
                size = insetSize,
                cornerRadius = insetCorner,
                style = Stroke(width = thickness + 2.dp.toPx()),
                alpha = 0.35f,
            )
            drawRoundRect(
                brush = brush,
                topLeft = insetTopLeft,
                size = insetSize,
                cornerRadius = insetCorner,
                style = Stroke(width = thickness),
                alpha = 1f,
            )
        }
    }
}