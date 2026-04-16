/*
 * Knowmad - Knowledge nomad
 * Copyright (C) 2026 LTFan (aka xfqwdsj)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package top.ltfan.knowmad.ui.component

import android.icu.text.MeasureFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.ltfan.knowmad.ui.util.DurationParts.Companion.toParts
import top.ltfan.knowmad.ui.util.format
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import java.util.Locale as JavaLocale

@Composable
fun DurationInput(
    state: DurationInputState,
    modifier: Modifier = Modifier,
    locale: Locale = LocalLocale.current,
    formatWidth: MeasureFormat.FormatWidth = SHORT,
) {
    val format = remember(locale, formatWidth) {
        MeasureFormat.getInstance(locale.platformLocale, formatWidth)
    }

    val enterAnim = remember {
        expandIn(expandFrom = Center, clip = false) + scaleIn()
    }
    val exitAnim = remember {
        shrinkOut(shrinkTowards = Center, clip = false) + scaleOut()
    }

    val parts = state.parts

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        AnimatedVisibility(
            visible = state.dayEnabled,
            modifier = Modifier.weight(1f),
            enter = enterAnim,
            exit = exitAnim,
        ) {
            UnitInput(
                value = parts.days,
                onValueChange = { state.value += (it - parts.days).days },
                unitName = remember(format) { format.getUnitDisplayName(DAY) },
            )
        }

        AnimatedVisibility(
            visible = state.hourEnabled,
            modifier = Modifier.weight(1f),
            enter = enterAnim,
            exit = exitAnim,
        ) {
            UnitInput(
                value = parts.hours,
                onValueChange = { state.value += (it - parts.hours).hours },
                unitName = remember(format) { format.getUnitDisplayName(HOUR) },
            )
        }

        AnimatedVisibility(
            visible = state.minuteEnabled,
            modifier = Modifier.weight(1f),
            enter = enterAnim,
            exit = exitAnim,
        ) {
            UnitInput(
                value = parts.minutes,
                onValueChange = { state.value += (it - parts.minutes).minutes },
                unitName = remember(format) { format.getUnitDisplayName(MINUTE) },
            )
        }

        AnimatedVisibility(
            visible = state.secondEnabled,
            modifier = Modifier.weight(1f),
            enter = enterAnim,
            exit = exitAnim,
        ) {
            UnitInput(
                value = parts.seconds,
                onValueChange = { state.value += (it - parts.seconds).seconds },
                unitName = remember(format) { format.getUnitDisplayName(SECOND) },
            )
        }
    }
}

@Composable
private fun UnitInput(
    value: Long,
    onValueChange: (Long) -> Unit,
    unitName: String,
    modifier: Modifier = Modifier,
    dragSensitivity: Dp = 24.dp,
) {
    val density = LocalDensity.current
    val pxSensitivity = with(density) { dragSensitivity.toPx() }

    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)

    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    val dragState = rememberDraggableState { delta ->
        dragAccumulator += delta

        var currentValue = latestValue

        while (dragAccumulator <= -pxSensitivity) {
            if (currentValue < Long.MAX_VALUE) {
                currentValue += 1
                latestOnValueChange(currentValue)
            }
            dragAccumulator += pxSensitivity
        }

        while (dragAccumulator >= pxSensitivity) {
            if (currentValue > 0L) {
                currentValue -= 1
                latestOnValueChange(currentValue)
            } else {
                currentValue = 0L
            }
            dragAccumulator -= pxSensitivity
        }
    }

    OutlinedTextField(
        value = if (value == 0L) "" else value.toString(),
        onValueChange = { input ->
            val newValue = input.filter(Char::isDigit).toLongOrNull() ?: 0L
            onValueChange(newValue)
        },
        modifier = modifier
            .fillMaxWidth()
            .draggable(
                state = dragState,
                orientation = Vertical,
                onDragStarted = { dragAccumulator = 0f },
                onDragStopped = { dragAccumulator = 0f },
            ),
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = Center),
        suffix = { Text(unitName) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}

@Stable
class DurationInputState(
    initialValue: Duration = ZERO,
    initialDayEnabled: Boolean = true,
    initialHourEnabled: Boolean = true,
    initialMinuteEnabled: Boolean = true,
    initialSecondEnabled: Boolean = true,
) {
    var value by mutableStateOf(initialValue)
    var dayEnabled by mutableStateOf(initialDayEnabled)
    var hourEnabled by mutableStateOf(initialHourEnabled)
    var minuteEnabled by mutableStateOf(initialMinuteEnabled)
    var secondEnabled by mutableStateOf(initialSecondEnabled)

    val parts by derivedStateOf {
        value.toParts(
            enableDays = dayEnabled,
            enableHours = hourEnabled,
            enableMinutes = minuteEnabled,
            enableSeconds = secondEnabled,
        )
    }

    fun format(
        locale: JavaLocale = JavaLocale.getDefault(),
        width: MeasureFormat.FormatWidth = SHORT,
        value: Duration = this.value,
    ) = value.format(
        locale = locale,
        width = width,
        enableDays = dayEnabled,
        enableHours = hourEnabled,
        enableMinutes = minuteEnabled,
        enableSeconds = secondEnabled,
    )
}
