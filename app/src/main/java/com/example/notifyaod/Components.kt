@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.notifyaod

import android.app.TimePickerDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt

/** Тёмная ли сейчас тема приложения (определяем по фону, чтобы работало и при ручном выборе темы). */
@Composable
fun isDarkUi(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

// ------------------------------------------------------------------------------------------
// Карточки и заголовки
// ------------------------------------------------------------------------------------------

/** Карточка секции: в тёмной теме с тонким светящимся контуром, в светлой — белая с мягкой тенью. */
@Composable
fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    val dark = isDarkUi()
    val shape = RoundedCornerShape(28.dp)
    val decoration = if (dark) {
        Modifier.border(
            1.dp,
            Brush.linearGradient(listOf(Color(0x40FFFFFF), Color(0x0AFFFFFF))),
            shape,
        )
    } else {
        Modifier.shadow(
            elevation = 8.dp,
            shape = shape,
            ambientColor = Color(0x1A2F6BF0),
            spotColor = Color(0x262F6BF0),
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(decoration),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (dark) Color(0xEB0D1631) else Color.White,
            // Контейнер — произвольный HEX-цвет, а не слот темы, поэтому Compose не может сам
            // подобрать contentColor и молча отдаёт Color.Unspecified → текст рисуется чёрным
            // (на светлом фоне это незаметно, на тёмном — чёрный текст на тёмном фоне).
            // Указываем цвет текста явно из текущей схемы.
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp), content = content)
    }
}

/** Круглая «таблетка» с иконкой. */
@Composable
fun IconBadge(icon: ImageVector, size: Dp = 44.dp) {
    val dark = isDarkUi()
    val fill = if (dark) {
        Brush.linearGradient(listOf(Color(0xFF1B3E96), Color(0xFF3A2C8F)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFE4EDFF), Color(0xFFEAE6FF)))
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, if (dark) Color(0x33FFFFFF) else Color(0x142F6BF0), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (dark) Color(0xFFDDE8FF) else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

@Composable
fun CardHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

// ------------------------------------------------------------------------------------------
// Переключатели, выбор из списка, время
// ------------------------------------------------------------------------------------------

@Composable
fun GlowSwitch(checked: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkUi()
    Box(
        modifier = Modifier.drawBehind {
            if (checked && dark) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x883D8BFF), Color.Transparent),
                        center = center,
                        radius = size.width * 0.85f,
                    ),
                    radius = size.width * 0.85f,
                )
            }
        },
    ) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = scheme.primary,
                checkedBorderColor = scheme.primary,
                uncheckedThumbColor = scheme.onSurfaceVariant,
                uncheckedTrackColor = scheme.surfaceVariant,
                uncheckedBorderColor = scheme.outline,
            ),
        )
    }
}

/** Строка-переключатель. Когда включена — подсвечивается своей «внутренней карточкой». */
@Composable
fun ToggleRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val dark = isDarkUi()
    val shape = RoundedCornerShape(22.dp)
    val bg = if (checked) {
        if (dark) Color(0xCC14204A) else Color(0xFFEAF0FF)
    } else {
        Color.Transparent
    }
    val borderMod = if (checked && dark) {
        Modifier.border(
            1.dp,
            Brush.linearGradient(listOf(Color(0x663D8BFF), Color(0x14FFFFFF))),
            shape,
        )
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(shape)
            .background(bg)
            .then(borderMod)
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        GlowSwitch(checked)
    }
}

/** Строка с выбором из выпадающего списка. */
@Composable
fun MenuChoiceRow(
    title: String,
    hint: String? = null,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Box {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        options.getOrElse(selected) { "—" },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { index, label ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        },
                        trailingIcon = {
                            if (index == selected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun TimeRow(label: String, minutes: Int, enabled: Boolean, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(enabled = enabled) {
                TimePickerDialog(
                    context,
                    { _, h, m -> onPick(h * 60 + m) },
                    minutes / 60,
                    minutes % 60,
                    true,
                ).show()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = if (enabled) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                "%02d:%02d".format(minutes / 60, minutes % 60),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

/** Период «с [время] до [время]» одной строкой — оба времени кликабельны по отдельности. */
@Composable
fun TimeRangeRow(
    label: String,
    startMinutes: Int,
    endMinutes: Int,
    enabled: Boolean,
    onPickStart: (Int) -> Unit,
    onPickEnd: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val textColor = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.4f)
    val chipBg = if (enabled) scheme.primaryContainer else scheme.surfaceVariant
    val chipText = if (enabled) scheme.onPrimaryContainer else scheme.onSurfaceVariant.copy(alpha = 0.6f)

    fun fmt(m: Int) = "%02d:%02d".format(m / 60, m % 60)
    fun pick(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, h, m -> onPicked(h * 60 + m) },
            current / 60,
            current % 60,
            true,
        ).show()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
        )
        Spacer(Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(50), color = chipBg, contentColor = chipText) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    fmt(startMinutes),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = enabled) { pick(startMinutes, onPickStart) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Text("–", style = MaterialTheme.typography.labelLarge)
                Text(
                    fmt(endMinutes),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = enabled) { pick(endMinutes, onPickEnd) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Ползунок с градиентом и иконкой
// ------------------------------------------------------------------------------------------

@Composable
private fun GradientSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkUi()
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onFinished,
        valueRange = range,
        thumb = {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .shadow(if (dark) 0.dp else 3.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .border(4.dp, scheme.primary, CircleShape),
            )
        },
        track = { state ->
            val span = state.valueRange.endInclusive - state.valueRange.start
            val fraction = if (span <= 0f) 0f
            else ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (dark) Color(0xFF1A2544) else Color(0xFFE3E9F7)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(listOf(Color(0xFF1E9BFF), Color(0xFF8B5CFF))),
                        ),
                )
            }
        },
    )
}

/** Настройка-ползунок: иконка слева, название и значение в «таблетке», под ними ползунок. */
@Composable
fun SliderSetting(
    icon: ImageVector,
    title: String,
    value: Int,
    range: IntRange,
    unit: String,
    onCommit: (Int) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        if (unit.isBlank()) "${local.roundToInt()}" else "${local.roundToInt()} $unit",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            GradientSlider(
                value = local,
                range = range.first.toFloat()..range.last.toFloat(),
                onValueChange = { local = it },
                onFinished = { onCommit(local.roundToInt()) },
            )
        }
    }
}

// ------------------------------------------------------------------------------------------
// Ввод числа секунд
// ------------------------------------------------------------------------------------------

/** Поле для ввода числа секунд с кнопками «больше / меньше». */
@Composable
fun SecondsField(label: String, seconds: Int, onChange: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by remember(seconds) { mutableStateOf(seconds.toString()) }
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(6)
                text = digits
                val n = digits.toIntOrNull()
                if (n != null && n > 0) onChange(n)
            },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = scheme.primary)
            },
            suffix = { Text("с") },
            trailingIcon = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Больше",
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .clickable { onChange((seconds + 1).coerceAtMost(999999)) },
                    )
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Меньше",
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .clickable { onChange((seconds - 1).coerceAtLeast(1)) },
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = scheme.primary,
                unfocusedBorderColor = scheme.outline,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Введите любое число секунд вручную",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Длительность показа: либо «без ограничений», либо вручную введённое число секунд. */
@Composable
fun DurationRow(
    unlimitedTitle: String,
    unlimitedHint: String,
    fieldLabel: String,
    unlimited: Boolean,
    seconds: Int,
    onUnlimitedChange: (Boolean) -> Unit,
    onSecondsChange: (Int) -> Unit,
) {
    Column {
        ToggleRow(unlimitedTitle, unlimitedHint, unlimited, onUnlimitedChange)
        if (!unlimited) {
            SecondsField(fieldLabel, seconds, onSecondsChange)
        }
    }
}

// ------------------------------------------------------------------------------------------
// Разрешения
// ------------------------------------------------------------------------------------------

@Composable
fun StatusChip(granted: Boolean?) {
    val label: String
    val containerColor: Color
    val contentColor: Color
    val icon: ImageVector
    when (granted) {
        true -> {
            label = "Выдано"
            containerColor = Color(0xFF1B5E20)
            contentColor = Color(0xFFB9F6CA)
            icon = Icons.Filled.Check
        }
        false -> {
            label = "Нет"
            containerColor = Color(0xFFB71C1C)
            contentColor = Color(0xFFFFCDD2)
            icon = Icons.Filled.Close
        }
        null -> {
            label = "Настроить"
            containerColor = MaterialTheme.colorScheme.secondaryContainer
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            icon = Icons.Filled.ChevronRight
        }
    }
    Surface(shape = RoundedCornerShape(50), color = containerColor) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = contentColor, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun PermissionRow(title: String, hint: String, granted: Boolean?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        StatusChip(granted)
    }
}

// ------------------------------------------------------------------------------------------
// Кнопки
// ------------------------------------------------------------------------------------------

@Composable
fun GradientButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF2F6BF0), Color(0xFF7B5CFF))))
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun OutlinedActionButton(text: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, scheme.outline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = scheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

// ------------------------------------------------------------------------------------------
// Выбор темы приложения
// ------------------------------------------------------------------------------------------

private data class ThemeOption(val id: Int, val label: String, val icon: ImageVector)

/** Переключатель темы: как в системе (по умолчанию) / тёмная / светлая. */
@Composable
fun ThemeSelector(mode: Int, onChange: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val options = listOf(
        ThemeOption(0, "Система", Icons.Filled.SettingsBrightness),
        ThemeOption(1, "Тёмная", Icons.Filled.DarkMode),
        ThemeOption(2, "Светлая", Icons.Filled.LightMode),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val selected = mode == option.id
            val content = if (selected) Color.White else scheme.onSurfaceVariant
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (selected) {
                            Brush.horizontalGradient(listOf(Color(0xFF2F6BF0), Color(0xFF7B5CFF)))
                        } else {
                            Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                        },
                    )
                    .clickable { onChange(option.id) }
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(option.icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    option.label,
                    color = content,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Приложения
// ------------------------------------------------------------------------------------------

// Компактная строка выбранного приложения в главном меню.
@Composable
fun AppSummaryRow(label: String, color: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color(color))
        )
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("Включено", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun AppTile(
    app: AppInfo,
    checked: Boolean,
    frameColor: Int,
    onChange: (Boolean) -> Unit,
    onColor: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkUi()
    val icon = remember(app.pkg) {
        try {
            context.packageManager.getApplicationIcon(app.pkg).toBitmap(96, 96).asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    var showPicker by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    val tint = Color(frameColor)
    val baseFill = if (dark) Color(0x990D1631) else Color.White

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .border(
                1.dp,
                if (checked) tint.copy(alpha = 0.7f) else scheme.outlineVariant,
                shape,
            ),
        shape = shape,
        // Заливаем карточку выбранным для приложения цветом рамки (полупрозрачно, чтобы
        // текст поверх оставался читаемым), а не одним и тем же фиксированным синим для всех.
        color = if (checked) {
            tint.copy(alpha = if (dark) 0.30f else 0.20f).compositeOver(baseFill)
        } else {
            baseFill
        },
        // Цвет фона здесь всегда наш собственный (не слот темы), поэтому Compose не может
        // вычислить contentColor сам — указываем явно, иначе текст в тёмной теме чернеет.
        contentColor = scheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChange(!checked) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(11.dp)),
                )
            } else {
                Spacer(Modifier.size(42.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    app.pkg,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (checked) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(frameColor))
                        .border(2.dp, Color.White.copy(alpha = if (dark) 0.35f else 0.9f), CircleShape)
                        .clickable { showPicker = true },
                )
                Spacer(Modifier.width(10.dp))
            }
            GlowSwitch(checked)
        }
    }

    if (showPicker) {
        ColorPickerDialog(
            initialColor = frameColor,
            onDismiss = { showPicker = false },
            onConfirm = { c ->
                onColor(c)
                showPicker = false
            },
        )
    }
}
