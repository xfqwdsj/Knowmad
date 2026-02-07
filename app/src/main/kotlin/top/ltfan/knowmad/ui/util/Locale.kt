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

package top.ltfan.knowmad.ui.util

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import java.util.Locale
import kotlin.time.Duration

fun Duration.format(
    locale: Locale = Locale.getDefault(),
    width: MeasureFormat.FormatWidth = SHORT,
): String {
    val days = inWholeDays
    val hours = inWholeHours % 24
    val minutes = inWholeMinutes % 60
    val seconds = inWholeSeconds % 60

    val measures = buildList {
        if (days > 0) add(Measure(days, MeasureUnit.DAY))
        if (hours > 0) add(Measure(hours, MeasureUnit.HOUR))
        if (minutes > 0) add(Measure(minutes, MeasureUnit.MINUTE))
        if (seconds > 0) add(Measure(seconds, MeasureUnit.SECOND))
        if (isEmpty()) add(Measure(0, MeasureUnit.SECOND))
    }

    val format = MeasureFormat.getInstance(locale, width)
    return format.formatMeasures(*measures.toTypedArray())
}
