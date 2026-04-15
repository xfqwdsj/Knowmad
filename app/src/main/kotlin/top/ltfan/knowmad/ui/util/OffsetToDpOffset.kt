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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

@Composable
fun rememberOffsetToDpOffset(): OffsetToDpOffset {
    val density = LocalDensity.current

    val instance = remember { OffsetToDpOffset(density) }

    LaunchedEffect(density) {
        instance.density = density
    }

    return instance
}

@Immutable
class OffsetToDpOffset(initialDensity: Density) {
    private val densityFlow = MutableStateFlow(initialDensity)
    private val originalOffsetFlow = MutableStateFlow(Offset.Zero)

    var density: Density
        get() = densityFlow.value
        set(value) {
            densityFlow.value = value
        }

    var originalOffset: Offset
        get() = originalOffsetFlow.value
        set(value) {
            originalOffsetFlow.value = value
        }

    val offsetFlow = densityFlow.combine(originalOffsetFlow) { density, originalOffset ->
        with(density) {
            DpOffset(
                x = originalOffset.x.toDp(),
                y = originalOffset.y.toDp(),
            )
        }
    }
}
