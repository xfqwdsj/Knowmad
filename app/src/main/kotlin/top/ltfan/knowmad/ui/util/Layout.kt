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

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

fun Modifier.matchParentShortestSide() = layout { measurable, constraints ->
    val size = minOf(constraints.maxWidth, constraints.maxHeight)
    val placeable = measurable.measure(Constraints.fixed(size, size))
    layout(size, size) {
        placeable.placeRelative(0, 0)
    }
}

fun Modifier.autoScale() = layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints(
            minWidth = 0,
            maxWidth = Constraints.Infinity,
            minHeight = 0,
            maxHeight = Constraints.Infinity,
        ),
    )

    val maxWidth = constraints.maxWidth
    val maxHeight = constraints.maxHeight

    val scale = minOf(
        1f,
        minOf(
            maxWidth.toFloat() / placeable.width,
            maxHeight.toFloat() / placeable.height,
        ),
    )

    val width = (placeable.width * scale).roundToInt()
    val height = (placeable.height * scale).roundToInt()

    layout(width, height) {
        placeable.placeRelativeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}
