package com.example.notifyaod

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RootContent() }
    }
}

// ------------------------------------------------------------------------------------------
// Тема приложения: 0 = как в системе (по умолчанию), 1 = тёмная, 2 = светлая
// ------------------------------------------------------------------------------------------

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF4C8DFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF15295C),
    onPrimaryContainer = Color(0xFFC9DBFF),
    secondary = Color(0xFF8F7CFF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1C2452),
    onSecondaryContainer = Color(0xFFD6D2FF),
    background = Color(0xFF060A16),
    onBackground = Color(0xFFE9EEFF),
    surface = Color(0xFF0B1226),
    onSurface = Color(0xFFE9EEFF),
    surfaceVariant = Color(0xFF16213F),
    onSurfaceVariant = Color(0xFF93A3CC),
    surfaceContainer = Color(0xFF0E1731),
    surfaceContainerHigh = Color(0xFF121D3B),
    outline = Color(0xFF2B3B66),
    outlineVariant = Color(0xFF1B2748),
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF2F6BF0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2EBFF),
    onPrimaryContainer = Color(0xFF14337A),
    secondary = Color(0xFF6C5CE7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E7FF),
    onSecondaryContainer = Color(0xFF2B2270),
    background = Color(0xFFF2F5FC),
    onBackground = Color(0xFF0F1630),
    surface = Color.White,
    onSurface = Color(0xFF0F1630),
    surfaceVariant = Color(0xFFE8EEFC),
    onSurfaceVariant = Color(0xFF5F6C8C),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    outline = Color(0xFFC5CFE8),
    outlineVariant = Color(0xFFDDE4F5),
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun AppTheme(themeMode: Int, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        1 -> true
        2 -> false
        else -> isSystemInDarkTheme()
    }
    // Цвет значков в строке состояния и навигации должен быть виден на выбранной теме
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}

@Composable
private fun RootContent() {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val loaded: AodSettings? by repo.settings.collectAsState(initial = null)
    AppTheme(themeMode = loaded?.themeMode ?: 0) {
        val current = loaded
        if (current == null) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        } else {
            MainScreen(current, repo)
        }
    }
}

// ------------------------------------------------------------------------------------------
// Вспомогательные функции
// ------------------------------------------------------------------------------------------

data class AppInfo(val pkg: String, val label: String)

private fun loadLaunchableApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    val resolved = pm.queryIntentActivities(intent, 0)
    return resolved
        .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.pkg != context.packageName }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

// Диапазон хранимого значения "мс на один круг анимации рамки": 400 (быстро) .. 6000 (медленно).
private const val FRAME_SPEED_MIN_MS = 400
private const val FRAME_SPEED_MAX_MS = 6000
private const val FRAME_RPM_MIN = 10   // об/мин для обычных анимаций
private const val FRAME_RPM_MAX = 150  // об/мин при самом быстром значении (400 мс/круг)
private const val FLUID_RPM_MIN = 1      // локальный минимум для «Жидкого неона»

/** Показываем скорость рамки в понятных "оборотах в минуту" — чем больше, тем быстрее. */
private fun frameSpeedMsToRpm(ms: Int, minRpm: Int = FRAME_RPM_MIN): Int {
    val maxMs = (60000f / minRpm).roundToInt()
    val clamped = ms.coerceIn(FRAME_SPEED_MIN_MS, maxMs)
    return (60000f / clamped).roundToInt().coerceIn(minRpm, FRAME_RPM_MAX)
}

private fun rpmToFrameSpeedMs(rpm: Int, minRpm: Int = FRAME_RPM_MIN): Int {
    val clamped = rpm.coerceIn(minRpm, FRAME_RPM_MAX)
    val maxMs = (60000f / minRpm).roundToInt()
    return (60000f / clamped).roundToInt().coerceIn(FRAME_SPEED_MIN_MS, maxMs)
}

private fun launchSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Не удалось открыть настройки", Toast.LENGTH_SHORT).show()
    }
}

// ------------------------------------------------------------------------------------------
// Главный экран
// ------------------------------------------------------------------------------------------

@Composable
fun MainScreen(settings: AodSettings, repo: SettingsRepository) {
    var showAppPicker by remember { mutableStateOf(false) }
    if (showAppPicker) {
        BackHandler { showAppPicker = false }
        AppSelectionScreen(
            settings = settings,
            repo = repo,
            onBack = { showAppPicker = false },
        )
    } else {
        MainSettingsScreen(settings, repo, onOpenAppPicker = { showAppPicker = true })
    }
}

@Composable
private fun AppSelectionScreen(
    settings: AodSettings,
    repo: SettingsRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkUi()
    var query by remember { mutableStateOf("") }

    // Список приложений загружается ТОЛЬКО после открытия этого меню.
    val apps by produceState(initialValue = emptyList<AppInfo>()) {
        value = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }
    val filtered = remember(apps, query, settings.selectedPackages) {
        val base = if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) }
        base.sortedWith(
            compareByDescending<AppInfo> { it.pkg in settings.selectedPackages }
                .thenBy { it.label.lowercase() }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        Scaffold(containerColor = Color.Transparent, contentColor = scheme.onBackground) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = scheme.onBackground,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onBack() }
                                .padding(10.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Выбор приложений", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Включите только нужные уведомления", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    GlassCard {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Поиск приложений") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = scheme.primary,
                                unfocusedBorderColor = scheme.outline,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        HintText("Отмеченные приложения показываются первыми. Чем меньше выбранных приложений, тем меньше лишней работы у приложения.")
                    }
                }
                items(filtered, key = { it.pkg }) { app ->
                    AppTile(
                        app = app,
                        checked = app.pkg in settings.selectedPackages,
                        frameColor = settings.frameColors[app.pkg] ?: DEFAULT_FRAME_COLOR,
                        onChange = { checked -> scope.launch { repo.setAppSelected(app.pkg, checked) } },
                        onColor = { color -> scope.launch { repo.setAppColor(app.pkg, color) } },
                    )
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun MainSettingsScreen(settings: AodSettings, repo: SettingsRepository, onOpenAppPicker: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkUi()

    // Статус разрешений обновляем при возврате из системных настроек
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val hasListener = remember(tick) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    val hasOverlay = remember(tick) { Settings.canDrawOverlays(context) }
    val hasBattery = remember(tick) {
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
    }

    val allRequiredPermissionsGranted = hasListener && hasOverlay && hasBattery
    var sawIncompletePermissions by remember { mutableStateOf(!allRequiredPermissionsGranted) }
    LaunchedEffect(allRequiredPermissionsGranted) {
        if (!allRequiredPermissionsGranted) {
            sawIncompletePermissions = true
        } else if (sawIncompletePermissions) {
            sawIncompletePermissions = false
            // После выдачи последнего обязательного разрешения убираем приложение с экрана.
            // При обычном запуске приложения, когда права уже выданы, сворачивание не происходит.
            Handler(Looper.getMainLooper()).postDelayed({
                context.findActivity()?.moveTaskToBack(true)
            }, 250L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .drawBehind {
                if (dark) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x552F6BF0), Color.Transparent),
                            center = Offset(size.width * 0.95f, size.height * 0.02f),
                            radius = size.width,
                        ),
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x448B5CFF), Color.Transparent),
                            center = Offset(0f, size.height * 0.55f),
                            radius = size.width * 0.9f,
                        ),
                    )
                } else {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x332F6BF0), Color.Transparent),
                            center = Offset(size.width * 0.9f, 0f),
                            radius = size.width,
                        ),
                    )
                }
            },
    ) {
        Scaffold(containerColor = Color.Transparent, contentColor = scheme.onBackground) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
            ) {

                // ---------- Шапка ----------
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.app_logo),
                            contentDescription = null,
                            modifier = Modifier.size(54.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Иконка и рамка при новых уведомлениях",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // ---------- Тема приложения ----------
                item {
                    GlassCard {
                        CardHeader(Icons.Filled.DarkMode, "Тема приложения")
                        ThemeSelector(settings.themeMode) { mode ->
                            scope.launch { repo.setThemeMode(mode) }
                        }
                        HintText("«Система» — тема меняется вместе с системной")
                    }
                }

                // ---------- Проверка ----------
                item {
                    GlassCard {
                        CardHeader(Icons.Filled.PlayArrow, "Проверка")
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            GradientButton("Показать сейчас (предпросмотр)") {
                                AodLauncher.show(context.applicationContext, settings.selectedPackages.toList())
                            }
                            OutlinedActionButton("Тест через 5 секунд (погасите экран)") {
                                Toast.makeText(
                                    context,
                                    "Погасите экран: через 5 секунд появится иконка",
                                    Toast.LENGTH_LONG,
                                ).show()
                                val app = context.applicationContext
                                Handler(Looper.getMainLooper()).postDelayed(
                                    { AodLauncher.show(app, settings.selectedPackages.toList()) },
                                    5000L,
                                )
                            }
                        }
                        HintText(
                            "Тест показывает все выбранные приложения сразу. " +
                                "Тап по экрану закрывает предпросмотр."
                        )
                    }
                }

                // ---------- Разрешения ----------
                item {
                    GlassCard {
                        CardHeader(Icons.Filled.Security, "Разрешения")
                        PermissionRow(
                            "Доступ к уведомлениям",
                            "Нужен, чтобы видеть новые уведомления",
                            hasListener,
                        ) { launchSettings(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                        PermissionRow(
                            "Поверх других приложений",
                            "Нужно, чтобы показать экран при выключенном дисплее",
                            hasOverlay,
                        ) {
                            launchSettings(
                                context,
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }
                        PermissionRow(
                            "Без ограничений батареи",
                            "Чтобы система не убивала фоновый сервис",
                            hasBattery,
                        ) { launchSettings(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                        PermissionRow(
                            "Настройки Xiaomi / HyperOS",
                            "Права → Другие разрешения: «Всплывающие окна в фоне» и «Экран блокировки». " +
                                "Ещё: «Автозапуск» и «Экономия заряда → Нет ограничений»",
                            null,
                        ) {
                            launchSettings(
                                context,
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }
                    }
                }

                // ---------- Ночной режим ----------
                item {
                    GlassCard {
                        CardHeader(Icons.Filled.Bedtime, "Ночной режим")
                        ToggleRow(
                            "Не показывать в это время",
                            "В выбранный промежуток экран не будет включаться, все иконки скрыты",
                            settings.quietEnabled,
                        ) { scope.launch { repo.setQuietEnabled(it) } }
                        TimeRangeRow(
                            label = "Период",
                            startMinutes = settings.quietStartMin,
                            endMinutes = settings.quietEndMin,
                            enabled = settings.quietEnabled,
                            onPickStart = { m -> scope.launch { repo.setQuietStart(m) } },
                            onPickEnd = { m -> scope.launch { repo.setQuietEnd(m) } },
                        )
                    }
                }

                // ---------- Яркость ----------
                item {
                    GlassCard {
                        CardHeader(Icons.Filled.Brightness6, "Яркость экрана")
                        ToggleRow(
                            "Автояркость",
                            "Экран сам подстраивается под то, насколько сейчас темно вокруг " +
                                "(по датчику освещённости телефона), а не под системную настройку яркости",
                            settings.autoBrightness,
                        ) { scope.launch { repo.setAutoBrightness(it) } }
                        if (!settings.autoBrightness) {
                            SliderSetting(
                                icon = Icons.Filled.Brightness6,
                                title = "Яркость экрана уведомлений",
                                value = settings.brightnessPercent,
                                range = 1..100,
                                unit = "%",
                            ) { scope.launch { repo.setBrightness(it) } }
                        }
                    }
                }

                // ---------- Рамка ----------
                item {
                    GlassCard {
                        CardHeader(Icons.Filled.Palette, "Рамка вокруг экрана")
                        ToggleRow(
                            "Показывать рамку",
                            "Цветная рамка по краю экрана после уведомления. " +
                                "Цвет задаётся для каждого приложения в списке ниже",
                            settings.frameEnabled,
                        ) { scope.launch { repo.setFrameEnabled(it) } }

                        if (settings.frameEnabled) {
                            val animations = FrameAnimation.entries
                            MenuChoiceRow(
                                title = "Стиль анимации",
                                hint = "Выберите из списка",
                                options = animations.map { it.label },
                                selected = animations.indexOf(FrameAnimation.of(settings.frameAnimation)),
                            ) { index -> scope.launch { repo.setFrameAnimation(animations[index].id) } }
                            DurationRow(
                                unlimitedTitle = "Рамка без ограничения по времени",
                                unlimitedHint = "Рамка не будет гаснуть сама — только по тапу, разблокировке или ночному режиму",
                                fieldLabel = "Сколько секунд показывать рамку",
                                unlimited = settings.frameUnlimited,
                                seconds = settings.frameSec,
                                onUnlimitedChange = { scope.launch { repo.setFrameUnlimited(it) } },
                                onSecondsChange = { scope.launch { repo.setFrameSec(it) } },
                            )
                            SliderSetting(
                                icon = Icons.Filled.FlashOn,
                                title = "Скорость рамки",
                                // Ползунок инвертирован: чем правее, тем быстрее анимация.
                                // Единица измерения — обороты в минуту, чтобы было понятно, что это.
                                value = frameSpeedMsToRpm(
                                    settings.frameSpeedMs,
                                    if (FrameAnimation.of(settings.frameAnimation) == FrameAnimation.FLUID)
                                        FLUID_RPM_MIN else FRAME_RPM_MIN,
                                ),
                                range = (if (FrameAnimation.of(settings.frameAnimation) == FrameAnimation.FLUID)
                                    FLUID_RPM_MIN else FRAME_RPM_MIN)..FRAME_RPM_MAX,
                                unit = "об/мин",
                            ) { rpm ->
                                scope.launch {
                                    repo.setFrameSpeed(
                                        rpmToFrameSpeedMs(
                                            rpm,
                                            if (FrameAnimation.of(settings.frameAnimation) == FrameAnimation.FLUID)
                                                FLUID_RPM_MIN else FRAME_RPM_MIN,
                                        )
                                    )
                                }
                            }
                            SliderSetting(
                                icon = Icons.Filled.Smartphone,
                                title = if (FrameAnimation.of(settings.frameAnimation) == FrameAnimation.FLUID)
                                    "Скругление углов «Жидкий неон»"
                                else
                                    "Скругление углов (под ваш телефон)",
                                value = if (FrameAnimation.of(settings.frameAnimation) == FrameAnimation.FLUID)
                                    settings.fluidCornerDp else settings.frameCornerDp,
                                range = 0..if (FrameAnimation.of(settings.frameAnimation) == FrameAnimation.FLUID) 200 else 100,
                                unit = "dp",
                            ) {
                                scope.launch {
                                    if (FrameAnimation.of(settings.frameAnimation) == FrameAnimation.FLUID)
                                        repo.setFluidCorner(it)
                                    else
                                        repo.setFrameCorner(it)
                                }
                            }
                            SliderSetting(
                                icon = Icons.Filled.LineWeight,
                                title = "Толщина рамки",
                                value = settings.frameThicknessDp,
                                range = 1..24,
                                unit = "dp",
                            ) { scope.launch { repo.setFrameThickness(it) } }

                            SliderSetting(
                                icon = Icons.Filled.FlashOn,
                                title = "Яркость свечения рамки",
                                value = settings.frameGlowPercent,
                                range = 10..150,
                                unit = "%",
                            ) { scope.launch { repo.setFrameGlowPercent(it) } }

                            // Дополнительные настройки эффекта «Жидкий неон»
                            if (FrameAnimation.of(settings.frameAnimation) == FrameAnimation.FLUID) {
                                SliderSetting(
                                    icon = Icons.Filled.FlashOn,
                                    title = "Яркость свечения",
                                    value = settings.fluidIntensity,
                                    range = 10..150,
                                    unit = "%",
                                ) { scope.launch { repo.setFluidIntensity(it) } }
                                SliderSetting(
                                    icon = Icons.Filled.BlurOn,
                                    title = "Мягкость свечения",
                                    value = settings.fluidBlur,
                                    range = 6..48,
                                    unit = "",
                                ) { scope.launch { repo.setFluidBlur(it) } }
                            }
                        }
                    }
                }

                // ---------- Иконки ----------
                item {
                    GlassCard {
                        CardHeader(Icons.Filled.Timer, "Иконки")
                        ToggleRow(
                            "Показывать иконки",
                            "Иконки приложений гуляют по экрану. Пока горит рамка, они отскакивают " +
                                "от её внутреннего края, потом — от краёв экрана",
                            settings.iconsEnabled,
                        ) { scope.launch { repo.setIconsEnabled(it) } }

                        if (settings.iconsEnabled) {
                            DurationRow(
                                unlimitedTitle = "Иконки без ограничения по времени",
                                unlimitedHint = "Иконка не будет скрываться сама — только по тапу, разблокировке или ночному режиму",
                                fieldLabel = "Сколько секунд показывать иконку",
                                unlimited = settings.timeoutUnlimited,
                                seconds = settings.timeoutSec,
                                onUnlimitedChange = { scope.launch { repo.setTimeoutUnlimited(it) } },
                                onSecondsChange = { scope.launch { repo.setTimeout(it) } },
                            )
                            SliderSetting(
                                icon = Icons.Filled.Speed,
                                title = "Скорость движения",
                                value = settings.speedDpPerSec,
                                range = 10..250,
                                unit = "dp/с",
                            ) { scope.launch { repo.setSpeed(it) } }
                            SliderSetting(
                                icon = Icons.Filled.AspectRatio,
                                title = "Размер иконки",
                                value = settings.iconSizeDp,
                                range = 48..200,
                                unit = "dp",
                            ) { scope.launch { repo.setIconSize(it) } }
                        }
                    }
                }

                // ---------- Выбранные приложения ----------
                item {
                    GlassCard {
                        CardHeader(Icons.Filled.Apps, "Приложения")
                        HintText(
                            if (settings.selectedPackages.isEmpty())
                                "Приложения ещё не выбраны. Откройте меню и включите уведомления для нужных приложений."
                            else
                                "В основном меню отображаются только приложения, для которых включены уведомления."
                        )
                        if (settings.selectedPackages.isNotEmpty()) {
                            val pm = context.packageManager
                            val selected = settings.selectedPackages.sortedBy { pkg ->
                                try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString().lowercase() }
                                catch (_: Exception) { pkg.lowercase() }
                            }
                            for (pkg in selected) {
                                val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                                catch (_: Exception) { pkg }
                                AppSummaryRow(
                                    label = label,
                                    color = settings.frameColors[pkg] ?: DEFAULT_FRAME_COLOR,
                                )
                            }
                        }
                        OutlinedActionButton(
                            if (settings.selectedPackages.isEmpty()) "Выбрать приложения" else "Изменить приложения"
                        ) { onOpenAppPicker() }
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}
