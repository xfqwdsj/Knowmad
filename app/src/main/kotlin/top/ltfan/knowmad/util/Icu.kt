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

package top.ltfan.knowmad.util

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import top.ltfan.knowmad.util.DurationParts.Companion.toParts
import java.util.Locale
import kotlin.time.Duration

fun Duration.format(
    locale: Locale = Locale.getDefault(),
    width: MeasureFormat.FormatWidth = SHORT,
    enableDays: Boolean = true,
    enableHours: Boolean = true,
    enableMinutes: Boolean = true,
    enableSeconds: Boolean = true,
    enableZero: Boolean = true,
): String {
    if (!enableDays && !enableHours && !enableMinutes && !enableSeconds) return ""

    val (sign, days, hours, minutes, seconds) = toParts(
        enableDays = enableDays,
        enableHours = enableHours,
        enableMinutes = enableMinutes,
        enableSeconds = enableSeconds,
    )

    val hasNonZero = days > 0 || hours > 0 || minutes > 0 || seconds > 0

    val measures = buildList {
        val sign = if (sign < 0 && hasNonZero) -1 else 1

        if (enableDays && days > 0) add(Measure(days * sign, DAY))

        if (enableHours && hours > 0) {
            val value = if (isEmpty()) hours * sign else hours
            add(Measure(value, HOUR))
        }
        if (enableMinutes && minutes > 0) {
            val value = if (isEmpty()) minutes * sign else minutes
            add(Measure(value, MINUTE))
        }
        if (enableSeconds && seconds > 0) {
            val value = if (isEmpty()) seconds * sign else seconds
            add(Measure(value, SECOND))
        }

        if (isEmpty() && enableZero) {
            when {
                enableSeconds -> add(Measure(0, SECOND))
                enableMinutes -> add(Measure(0, MINUTE))
                enableHours -> add(Measure(0, HOUR))
                else -> add(Measure(0, DAY))
            }
        }
    }

    if (measures.isEmpty()) return ""

    val format = MeasureFormat.getInstance(locale, width)
    return format.formatMeasures(*measures.toTypedArray())
}
