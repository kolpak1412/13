package com.example.notifyaod

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/*
 * «Liquid Neon Smoke»: тёмная жидкая масса с тонкой неоновой кромкой и мягким объёмным
 * свечением. Считается целиком на GPU одним фрагментным шейдером:
 *
 *   fbm noise -> domain warping -> fluid field -> edge detection (|field - threshold|)
 *   -> 4 уровня свечения (ядро / inner / middle / atmosphere) -> цвет -> premultiplied alpha
 *
 * Никаких CSS-подобных градиентов и box-shadow: линия светится именно на границе маски,
 * поэтому её толщина сама меняется там, где жидкость растягивается или сжимается.
 *
 * Анимация не трогает ни DOM, ни Compose-состояние: каждый кадр обновляются только uniform'ы.
 */

private const val VERTEX_SHADER = """
attribute vec2 a_pos;
void main() { gl_Position = vec4(a_pos, 0.0, 1.0); }
"""

private const val FRAGMENT_SHADER = """
precision highp float;

uniform vec2  u_res;        // размер буфера в пикселях
uniform float u_time;       // «жидкое» время (уже с учётом скорости)
uniform vec3  u_color;      // единственный источник цвета для всего эффекта
uniform float u_alpha;      // появление/исчезновение, 0..1
uniform float u_intensity;  // общая интенсивность, ~0.2..1.4
uniform float u_blur;       // ширина мягкого ореола, ~6..48
uniform float u_thickness;  // толщина яркого ядра линии, ~0.4..4
uniform float u_boost;      // всплеск от нового уведомления, 0..1

// ---------- gradient noise ----------
vec2 hash2(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return -1.0 + 2.0 * fract(sin(p) * 43758.5453123);
}

float gnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(dot(hash2(i + vec2(0.0, 0.0)), f - vec2(0.0, 0.0)),
                   dot(hash2(i + vec2(1.0, 0.0)), f - vec2(1.0, 0.0)), u.x),
               mix(dot(hash2(i + vec2(0.0, 1.0)), f - vec2(0.0, 1.0)),
                   dot(hash2(i + vec2(1.0, 1.0)), f - vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        sum += amp * gnoise(p);
        p = mat2(1.6, 1.2, -1.2, 1.6) * p;
        amp *= 0.5;
    }
    return sum;
}

void main() {
    vec2 frag = gl_FragCoord.xy;
    // sc: 0..1, y = 0 сверху экрана
    vec2 sc = vec2(frag.x / u_res.x, 1.0 - frag.y / u_res.y);
    // p: с сохранением пропорций, чтобы формы не растягивались
    vec2 p = (frag - 0.5 * u_res) / u_res.y;

    float t = u_time;
    vec2 q0 = p * 2.1;

    // ---------- domain warping: течение, а не «плавающие кружки» ----------
    vec2 q = vec2(fbm(q0 + vec2(0.0, 0.0) + vec2(0.0, 0.11 * t)),
                  fbm(q0 + vec2(5.2, 1.3) - vec2(0.09 * t, 0.0)));
    vec2 r = vec2(fbm(q0 + 3.4 * q + vec2(1.7, 9.2) + vec2(0.07 * t, -0.05 * t)),
                  fbm(q0 + 3.4 * q + vec2(8.3, 2.8) - vec2(0.04 * t, 0.06 * t)));
    float f = fbm(q0 + 3.2 * r + vec2(0.0, -0.03 * t));

    // ---------- полноэкранная композиция ----------
    // Раньше область эффекта была смещена преимущественно вверх.
    // Теперь жидкий неон имеет одинаковую область действия по всему экрану.
    float region = 1.0;

    // поле, по которому строится жидкая маска
    float field = f + 0.42 * (region - 0.58);

    // порог маски медленно дышит -> части формы исчезают, другие появляются
    float thr = 0.015 + 0.055 * sin(t * 0.11) + 0.03 * sin(t * 0.047 + 1.7);
    float d = abs(field - thr);

    // ---------- 4 уровня свечения вокруг границы ----------
    float th = clamp(u_thickness, 0.35, 4.0);
    float bl = clamp(u_blur, 4.0, 60.0);

    float core  = exp(-d * (300.0 / th));   // очень тонкое яркое ядро
    float inner = exp(-d * (110.0 / th));   // средний blur
    float mid   = exp(-d * (620.0 / bl));   // широкое мягкое свечение
    float outer = exp(-d * (215.0 / bl));   // атмосферное свечение

    // слабое свечение внутри самой «жидкости» + общая атмосфера
    float fill = smoothstep(0.0, 0.30, field - thr) * 0.055;
    float atmo = region * (0.55 + 0.45 * f) * 0.09;

    float boost = 1.0 + 0.85 * u_boost;
    float lum = core * 1.00 + inner * 0.50 + mid * 0.26 + outer * 0.12 + fill + atmo;
    lum *= u_intensity * boost * mix(0.12, 1.0, region);

    // цветовая иерархия: ядро уходит в белый, ореол остаётся чистым цветом
    vec3 col = mix(u_color, vec3(1.0), clamp(core * 0.60, 0.0, 0.70));

    float a = clamp(lum, 0.0, 1.0) * u_alpha;
    // лёгкий дизеринг — иначе на тёмных градиентах виден бандинг
    a += (fract(sin(dot(frag, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) * 0.004;
    a = clamp(a, 0.0, 1.0);

    gl_FragColor = vec4(col * a, a); // premultiplied alpha
}
"""

/** Состояние, которое читает GL-поток. Обновляется только присваиванием полей. */
private class FluidState {
    @Volatile var colorR = 0.21f
    @Volatile var colorG = 0.85f
    @Volatile var colorB = 1.0f
    @Volatile var intensity = 0.8f
    @Volatile var speed = 0.35f
    @Volatile var blur = 18f
    @Volatile var thickness = 1.5f
    @Volatile var visible = false

    /** Новая волна: зовётся при повторном уведомлении. */
    @Volatile var pulse = false
}

private class FluidRenderer(private val state: FluidState) : GLSurfaceView.Renderer {

    private var program = 0
    private var aPos = 0
    private var uRes = 0
    private var uTime = 0
    private var uColor = 0
    private var uAlpha = 0
    private var uIntensity = 0
    private var uBlur = 0
    private var uThickness = 0
    private var uBoost = 0

    private lateinit var quad: FloatBuffer
    private var width = 1f
    private var height = 1f

    private var phase = 0f       // «жидкое» время
    private var alpha = 0f       // текущая непрозрачность (плавное появление/исчезновение)
    private var boost = 0f       // затухающий всплеск
    private var lastNanos = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val verts = floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f) // fullscreen triangle
        quad = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(verts); position(0) }

        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPos = GLES20.glGetAttribLocation(program, "a_pos")
        uRes = GLES20.glGetUniformLocation(program, "u_res")
        uTime = GLES20.glGetUniformLocation(program, "u_time")
        uColor = GLES20.glGetUniformLocation(program, "u_color")
        uAlpha = GLES20.glGetUniformLocation(program, "u_alpha")
        uIntensity = GLES20.glGetUniformLocation(program, "u_intensity")
        uBlur = GLES20.glGetUniformLocation(program, "u_blur")
        uThickness = GLES20.glGetUniformLocation(program, "u_thickness")
        uBoost = GLES20.glGetUniformLocation(program, "u_boost")

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        lastNanos = 0L
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w.toFloat()
        height = h.toFloat()
        GLES20.glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0f else ((now - lastNanos) / 1e9f).coerceIn(0f, 0.05f)
        lastNanos = now

        if (state.pulse) {
            state.pulse = false
            boost = 1f
        }
        // всплеск затухает за ~1.2 с, скорость возвращается к обычной
        boost = (boost - dt / 1.2f).coerceAtLeast(0f)

        // появление 300 мс, исчезновение 900 мс — без мгновенного выключения
        val target = if (state.visible) 1f else 0f
        val rate = if (state.visible) dt / 0.30f else dt / 0.90f
        alpha = if (alpha < target) (alpha + rate).coerceAtMost(target)
        else (alpha - rate).coerceAtLeast(target)

        phase += dt * state.speed * 6f * (1f + 0.9f * boost)

        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (alpha <= 0.001f) return

        GLES20.glUseProgram(program)
        GLES20.glUniform2f(uRes, width, height)
        GLES20.glUniform1f(uTime, phase)
        GLES20.glUniform3f(uColor, state.colorR, state.colorG, state.colorB)
        GLES20.glUniform1f(uAlpha, alpha)
        GLES20.glUniform1f(uIntensity, state.intensity)
        GLES20.glUniform1f(uBlur, state.blur)
        GLES20.glUniform1f(uThickness, state.thickness)
        GLES20.glUniform1f(uBoost, boost)

        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
        GLES20.glDisableVertexAttribArray(aPos)
    }

    private fun buildProgram(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, src)
            GLES20.glCompileShader(id)
            return id
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        return p
    }
}

/**
 * Прозрачная GL-поверхность поверх контента. Рендерится в уменьшенный буфер
 * (RENDER_SCALE) — эффект мягкий, поэтому разницы не видно, зато шейдер с domain
 * warping спокойно тянет 60 fps на слабых телефонах.
 */
private class FluidNeonView(context: Context, val state: FluidState) : GLSurfaceView(context) {

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        setZOrderOnTop(false)
        setZOrderMediaOverlay(true)   // эффект остаётся над фоном, но иконки Compose могут быть поверх него
        isClickable = false             // ...но не перехватывает касания
        isFocusable = false
        setRenderer(FluidRenderer(state))
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        onResume()
    }

    override fun onDetachedFromWindow() {
        onPause()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            holder.setFixedSize(
                (w * RENDER_SCALE).toInt().coerceAtLeast(1),
                (h * RENDER_SCALE).toInt().coerceAtLeast(1),
            )
        }
    }

    private companion object { const val RENDER_SCALE = 0.5f }
}

/**
 * Полноэкранный эффект «жидкий неон».
 *
 * @param visible показывать ли эффект (появление/затухание считается внутри)
 * @param color единственный источник цвета: ядро, свечение и атмосфера берутся из него
 * @param intensity общая яркость, 0..1.4
 * @param speed скорость течения жидкости, ~0.1..1
 * @param blur ширина мягкого ореола (аналог Gaussian blur), ~6..48
 * @param thickness толщина яркого ядра линии, ~0.4..4
 * @param pulseKey любое изменение значения запускает новую волну свечения
 *                 (используется для повторных уведомлений — второй слой не создаётся)
 */
@Composable
fun NotificationFluidEffect(
    visible: Boolean,
    color: Color,
    intensity: Float = 0.8f,
    speed: Float = 0.35f,
    blur: Float = 18f,
    thickness: Float = 1.5f,
    pulseKey: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state = remember { FluidState() }
    val view = remember { FluidNeonView(context, state) }

    state.colorR = color.red
    state.colorG = color.green
    state.colorB = color.blue
    state.intensity = intensity
    state.speed = speed
    state.blur = blur
    state.thickness = thickness
    state.visible = visible

    LaunchedEffect(pulseKey) { if (pulseKey != 0L) state.pulse = true }

    DisposableEffect(Unit) {
        onDispose { state.visible = false } // GL-поток останавливается в onDetachedFromWindow
    }

    AndroidView(factory = { view }, modifier = modifier)
}

/** Текущее время для pulseKey. */
fun fluidPulseKey(): Long = SystemClock.elapsedRealtime()
