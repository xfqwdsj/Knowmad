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

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials

val DefaultHazeStyle @Composable inline get() = HazeMaterials.ultraThick()
val HazeEasing = CubicBezierEasing(.2f, .0f, .2f, 1f)

@Stable
@Composable
fun Modifier.hazeEffect(
    state: HazeState,
    block: (HazeEffectScope.() -> Unit)? = null,
) = hazeEffect(state, DefaultHazeStyle, block)

@Stable
@Composable
fun Modifier.hazeEffectStart(
    state: HazeState,
    style: HazeStyle = DefaultHazeStyle,
    easing: Easing = HazeEasing,
    block: (HazeEffectScope.() -> Unit)? = null,
) = hazeEffect(state, style) {
    progressive = HazeProgressive.horizontalGradient(
        easing = easing,
        startX = Float.POSITIVE_INFINITY,
        endX = 0f,
    )
    block?.invoke(this)
}

@Stable
@Composable
fun Modifier.hazeEffectTop(
    state: HazeState,
    style: HazeStyle = DefaultHazeStyle,
    easing: Easing = HazeEasing,
    block: (HazeEffectScope.() -> Unit)? = null,
) = hazeEffect(state, style) {
    progressive = HazeProgressive.verticalGradient(
        easing = easing,
        startY = Float.POSITIVE_INFINITY,
        endY = 0f,
    )
    block?.invoke(this)
}

@Stable
@Composable
fun Modifier.hazeEffectEnd(
    state: HazeState,
    style: HazeStyle = DefaultHazeStyle,
    easing: Easing = HazeEasing,
    block: (HazeEffectScope.() -> Unit)? = null,
) = hazeEffect(state, style) {
    progressive = HazeProgressive.horizontalGradient(easing = easing)
    block?.invoke(this)
}

@Composable
fun Modifier.hazeEffectBottom(
    state: HazeState,
    style: HazeStyle = DefaultHazeStyle,
    easing: Easing = HazeEasing,
    block: (HazeEffectScope.() -> Unit)? = null,
) = hazeEffect(state, style) {
    progressive = HazeProgressive.verticalGradient(easing = easing)
    block?.invoke(this)
}

@Stable
fun Modifier.contentHazeSource(
    state: HazeState,
    zIndexDelta: Float = 0f,
) = hazeSource(
    state = state,
    zIndex = HazeZIndex.content + zIndexDelta,
)

@Stable
@Composable
fun Modifier.appBarHaze(
    state: HazeState,
    style: HazeStyle = DefaultHazeStyle,
    easing: Easing = HazeEasing,
    zIndexDelta: Float = 0f,
    block: (HazeEffectScope.() -> Unit)? = null,
) = this
    .hazeSource(
        state = state,
        zIndex = HazeZIndex.topBar + zIndexDelta,
    )
    .hazeEffectTop(state, style, easing) {
        block?.invoke(this)
    }

@Stable
fun Modifier.contentOverlayHaze(
    state: HazeState,
    style: HazeStyle = HazeStyle.Unspecified,
    zIndexDelta: Float = 0f,
    block: (HazeEffectScope.() -> Unit)? = null,
) = this
    .hazeSource(
        state = state,
        zIndex = HazeZIndex.contentOverlay + zIndexDelta,
    )
    .hazeEffect(state, style) {
        block?.invoke(this)
    }

object HazeZIndex {
    private var currentNegative = -1f
        get() = field.also { field -= 1f }

    private var currentNotNegative = 0f
        get() = field.also { field += 1f }

    val content = currentNotNegative
    val topBar = currentNotNegative
    val contentOverlay = currentNotNegative
    val navDisplay = currentNotNegative
    val bottomBar = currentNotNegative
    val app = currentNotNegative
}
