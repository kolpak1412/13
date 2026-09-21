package com.example.notifyaod

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

/** Цвет рамки по умолчанию (синий). */
val DEFAULT_FRAME_COLOR: Int = 0xFF2196F3.toInt()

/** Стиль анимации рамки. Выбирается во всплывающем меню в настройках. */
enum class FrameAnimation(val id: Int, val label: String) {
    WAVE(0, "Волна"),
    DUAL_BARS(1, "Две полосы"),
    PULSE(2, "Пульс"),
    RAINBOW(4, "Радуга"),
    FLUID(5, "Жидкий неон");

    companion object {
        /**
         * Сохранённые в настройках номера совпадают со старыми версиями:
         * 0, 1, 2 и 4 (номер 3 раньше занимала «Комета», её больше нет). 5 — «Жидкий неон».
         */
        fun of(id: Int): FrameAnimation = entries.firstOrNull { it.id == id } ?: WAVE
    }
}

data class AodSettings(
    val selectedPackages: Set<String> = emptySet(),
    val quietEnabled: Boolean = false,
    val quietStartMin: Int = 23 * 60,   // минуты от полуночи
    val quietEndMin: Int = 7 * 60,

    // ---- иконки ----
    val iconsEnabled: Boolean = true,      // показывать иконки приложений вообще
    val timeoutUnlimited: Boolean = true,  // не скрывать иконку по времени
    val timeoutSec: Int = 30,              // сколько секунд показывать иконку
    val speedDpPerSec: Int = 70,
    val iconSizeDp: Int = 96,

    // ---- рамка ----
    val frameEnabled: Boolean = true,      // показывать рамку вообще
    val frameUnlimited: Boolean = false,   // рамка не гаснет сама
    val frameSec: Int = 15,                // сколько секунд показывать рамку
    val frameCornerDp: Int = 40,           // скругление углов рамки
    val frameThicknessDp: Int = 6,         // толщина рамки
    val frameGlowPercent: Int = 100,       // яркость свечения рамки, 10..150%
    val frameSpeedMs: Int = 2200,          // за сколько мс проходит один цикл анимации
    val frameColors: Map<String, Int> = emptyMap(), // пакет -> цвет рамки (ARGB)
    val frameAnimation: Int = 0,           // индекс FrameAnimation

    // ---- эффект «Жидкий неон» ----
    val fluidIntensity: Int = 80,          // 10..150, общая яркость эффекта (%)
    val fluidBlur: Int = 18,               // 6..48, ширина мягкого ореола
    val fluidCornerDp: Int = 40,           // 0..200, локальное скругление для «Жидкого неона»

    // ---- яркость ----
    val autoBrightness: Boolean = true,
    val brightnessPercent: Int = 2,        // 1..100, если autoBrightness = false

    // ---- оформление приложения ----
    val themeMode: Int = 0,                // 0 = как в системе, 1 = тёмная, 2 = светлая
)

private val Context.dataStore by preferencesDataStore(name = "aod_settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<AodSettings> = store.data.map { p ->
        AodSettings(
            selectedPackages = p[SELECTED] ?: emptySet(),
            quietEnabled = p[QUIET_ENABLED] ?: false,
            quietStartMin = p[QUIET_START] ?: (23 * 60),
            quietEndMin = p[QUIET_END] ?: (7 * 60),
            iconsEnabled = p[ICONS_ENABLED] ?: true,
            timeoutUnlimited = p[TIMEOUT_UNLIMITED] ?: true,
            timeoutSec = p[TIMEOUT] ?: 30,
            speedDpPerSec = p[SPEED] ?: 70,
            iconSizeDp = p[SIZE] ?: 96,
            frameEnabled = p[FRAME_ENABLED] ?: true,
            frameUnlimited = p[FRAME_UNLIMITED] ?: false,
            frameSec = p[FRAME_SEC] ?: 15,
            frameCornerDp = p[FRAME_CORNER] ?: 40,
            frameThicknessDp = p[FRAME_THICKNESS] ?: 6,
            frameGlowPercent = p[FRAME_GLOW] ?: 100,
            frameSpeedMs = p[FRAME_SPEED] ?: 2200,
            frameColors = parseColors(p[FRAME_COLORS] ?: emptySet()),
            frameAnimation = p[FRAME_ANIM] ?: 0,
            fluidIntensity = p[FLUID_INTENSITY] ?: 80,
            fluidBlur = p[FLUID_BLUR] ?: 18,
            fluidCornerDp = p[FLUID_CORNER] ?: 40,
            autoBrightness = p[AUTO_BRIGHTNESS] ?: true,
            brightnessPercent = p[BRIGHTNESS] ?: 2,
            themeMode = p[THEME_MODE] ?: 0,
        )
    }

    suspend fun setAppSelected(pkg: String, selected: Boolean) {
        store.edit { p ->
            val current = p[SELECTED] ?: emptySet()
            p[SELECTED] = if (selected) current + pkg else current - pkg
        }
    }

    suspend fun setAppColor(pkg: String, color: Int) {
        store.edit { p ->
            val current = p[FRAME_COLORS] ?: emptySet()
            p[FRAME_COLORS] = current.filterNot { it.startsWith("$pkg|") }.toSet() + "$pkg|$color"
        }
    }

    suspend fun setQuietEnabled(value: Boolean) { store.edit { it[QUIET_ENABLED] = value } }
    suspend fun setQuietStart(minutes: Int) { store.edit { it[QUIET_START] = minutes } }
    suspend fun setQuietEnd(minutes: Int) { store.edit { it[QUIET_END] = minutes } }

    suspend fun setIconsEnabled(value: Boolean) { store.edit { it[ICONS_ENABLED] = value } }
    suspend fun setTimeoutUnlimited(value: Boolean) { store.edit { it[TIMEOUT_UNLIMITED] = value } }
    suspend fun setTimeout(sec: Int) { store.edit { it[TIMEOUT] = sec } }
    suspend fun setSpeed(dpPerSec: Int) { store.edit { it[SPEED] = dpPerSec } }
    suspend fun setIconSize(dp: Int) { store.edit { it[SIZE] = dp } }

    suspend fun setFrameEnabled(value: Boolean) { store.edit { it[FRAME_ENABLED] = value } }
    suspend fun setFrameUnlimited(value: Boolean) { store.edit { it[FRAME_UNLIMITED] = value } }
    suspend fun setFrameSec(sec: Int) { store.edit { it[FRAME_SEC] = sec } }
    suspend fun setFrameCorner(dp: Int) { store.edit { it[FRAME_CORNER] = dp } }
    suspend fun setFrameThickness(dp: Int) { store.edit { it[FRAME_THICKNESS] = dp } }
    suspend fun setFrameGlowPercent(percent: Int) { store.edit { it[FRAME_GLOW] = percent.coerceIn(10, 150) } }
    suspend fun setFrameSpeed(ms: Int) { store.edit { it[FRAME_SPEED] = ms } }
    suspend fun setFrameAnimation(id: Int) { store.edit { it[FRAME_ANIM] = id } }
    suspend fun setFluidIntensity(percent: Int) { store.edit { it[FLUID_INTENSITY] = percent } }
    suspend fun setFluidBlur(value: Int) { store.edit { it[FLUID_BLUR] = value } }
    suspend fun setFluidCorner(dp: Int) { store.edit { it[FLUID_CORNER] = dp.coerceIn(0, 200) } }
    suspend fun setThemeMode(mode: Int) { store.edit { it[THEME_MODE] = mode } }

    suspend fun setAutoBrightness(value: Boolean) { store.edit { it[AUTO_BRIGHTNESS] = value } }
    suspend fun setBrightness(percent: Int) { store.edit { it[BRIGHTNESS] = percent } }

    private fun parseColors(set: Set<String>): Map<String, Int> =
        set.mapNotNull { entry ->
            val i = entry.lastIndexOf('|')
            if (i <= 0) null
            else entry.substring(i + 1).toIntOrNull()?.let { entry.substring(0, i) to it }
        }.toMap()

    private companion object {
        val SELECTED = stringSetPreferencesKey("selected_packages")
        val QUIET_ENABLED = booleanPreferencesKey("quiet_enabled")
        val QUIET_START = intPreferencesKey("quiet_start")
        val QUIET_END = intPreferencesKey("quiet_end")
        val ICONS_ENABLED = booleanPreferencesKey("icons_enabled")
        val TIMEOUT_UNLIMITED = booleanPreferencesKey("timeout_unlimited")
        val TIMEOUT = intPreferencesKey("timeout_sec")
        val SPEED = intPreferencesKey("speed")
        val SIZE = intPreferencesKey("icon_size")
        val FRAME_ENABLED = booleanPreferencesKey("frame_enabled")
        val FRAME_UNLIMITED = booleanPreferencesKey("frame_unlimited")
        val FRAME_SEC = intPreferencesKey("frame_sec")
        val FRAME_CORNER = intPreferencesKey("frame_corner")
        val FRAME_THICKNESS = intPreferencesKey("frame_thickness")
        val FRAME_GLOW = intPreferencesKey("frame_glow_percent")
        val FRAME_SPEED = intPreferencesKey("frame_speed")
        val FRAME_COLORS = stringSetPreferencesKey("frame_colors")
        val FRAME_ANIM = intPreferencesKey("frame_animation")
        val FLUID_INTENSITY = intPreferencesKey("fluid_intensity")
        val FLUID_BLUR = intPreferencesKey("fluid_blur")
        val FLUID_CORNER = intPreferencesKey("fluid_corner")
        val AUTO_BRIGHTNESS = booleanPreferencesKey("auto_brightness")
        val BRIGHTNESS = intPreferencesKey("brightness_percent")
        val THEME_MODE = intPreferencesKey("theme_mode")
    }
}

object QuietHours {
    /** true, если сейчас «ночь». Поддерживает интервал через полночь (23:00–07:00). */
    fun isQuiet(s: AodSettings, nowMinutes: Int = currentMinutes()): Boolean {
        if (!s.quietEnabled) return false
        val start = s.quietStartMin
        val end = s.quietEndMin
        return when {
            start == end -> false
            start < end -> nowMinutes in start until end
            else -> nowMinutes >= start || nowMinutes < end
        }
    }

    private fun currentMinutes(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }
}
