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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct

fun contractColorFor(
    backgroundColor: Color,
    toneDelta: Double = 60.0,
    minTone: Double = 15.0,
    maxTone: Double = 85.0,
    chromaDelta: Double = -15.0,
): Color {
    val hct = Hct.fromInt(backgroundColor.toArgb())

    val originalTone = hct.tone
    val contrastTone = if (originalTone < 50) {
        (originalTone + toneDelta).coerceAtMost(maxTone)
    } else {
        (originalTone - toneDelta).coerceAtLeast(minTone)
    }

    val newChroma = (hct.chroma + chromaDelta).coerceAtLeast(0.0)

    val contrastHct = Hct.from(hct.hue, newChroma, contrastTone)

    return Color(contrastHct.toInt())
}
