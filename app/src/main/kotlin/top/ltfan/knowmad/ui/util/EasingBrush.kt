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

import androidx.annotation.IntRange
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradient

@Immutable
data class LinearBrushData(
    val easing: Easing = DefaultEasing,
    val start: Offset = Zero,
    val startIntensity: Float = 0f,
    val end: Offset = Zero,
    val endIntensity: Float = 1f,
    @param:IntRange(from = 2) val stops: Int = 16,
) {
    init {
        require(stops >= 2) { "stops must be at least 2" }
    }

    fun createBrush(): LinearGradient {
        val colors = List(stops) { i ->
            val t = i.toFloat() / (stops - 1)
            val intensity = easing.transform(t) * (endIntensity - startIntensity) + startIntensity
            Color.White.copy(alpha = intensity.coerceIn(0f, 1f))
        }
        return Brush.linearGradient(
            colors = colors,
            start = start,
            end = end,
        ) as LinearGradient
    }

    companion object {
        val DefaultEasing = CubicBezierEasing(.3f, .0f, .4f, 1f)
    }
}
