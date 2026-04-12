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

import android.os.Build
import androidx.annotation.FloatRange
import androidx.annotation.RequiresApi
import androidx.collection.LruCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

@Suppress("NOTHING_TO_INLINE")
inline fun BackdropEffectScope.appLightEffects(
    @FloatRange(from = 0.0, to = 1.0) progress: Float = 1f,
) {
    vibrancy()
    blur(2.dp.toPx() * progress)
    lens(12.dp.toPx() * progress, 24.dp.toPx() * progress)
}

val BackdropEffectsLight: BackdropEffectScope.() -> Unit = BackdropEffectScope::appLightEffects

@Suppress("NOTHING_TO_INLINE")
inline fun BackdropEffectScope.appMediumEffects(
    @FloatRange(from = 0.0, to = 1.0) progress: Float = 1f,
) {
    vibrancy()
    blur(4.dp.toPx() * progress)
    lens(16.dp.toPx() * progress, 32.dp.toPx() * progress)
}

val BackdropEffectsMedium: BackdropEffectScope.() -> Unit = BackdropEffectScope::appMediumEffects

@Suppress("NOTHING_TO_INLINE")
inline fun BackdropEffectScope.appHeavyEffects(
    @FloatRange(from = 0.0, to = 1.0) progress: Float = 1f,
) {
    vibrancy()
    blur(12.dp.toPx() * progress)
    lens(24.dp.toPx() * progress, 36.dp.toPx() * progress)
}

val BackdropEffectsHeavy: BackdropEffectScope.() -> Unit = BackdropEffectScope::appHeavyEffects

private val ProgressiveBlurEffectCache = LruCache<ProgressiveBlurEffectKey, RenderEffect>(64)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun BackdropEffectScope.progressiveBlur(
    @FloatRange(from = 0.0, fromInclusive = true) radius: Float,
    data: LinearBrushData,
) {
    val size = size
    if (!size.isSpecified) return

    val key = ProgressiveBlurEffectKey(radius, size, data)
    ProgressiveBlurEffectCache[key]?.let { effect ->
        effect(effect)
        return
    }

    val mask = data.createBrush().createShader(size)
    val renderEffect = createBlurRenderEffect(size, radius, mask).asComposeRenderEffect()
    ProgressiveBlurEffectCache.put(key, renderEffect)
    effect(renderEffect)
}

inline fun BackdropEffectScope.progressiveBlurWithFallback(
    @FloatRange(from = 0.0, fromInclusive = true) radius: Float,
    data: LinearBrushData,
    fallback: BackdropEffectScope.() -> Unit = { blur(radius) },
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        progressiveBlur(radius, data)
    } else {
        fallback()
    }
}

private data class ProgressiveBlurEffectKey(
    val radius: Float,
    val size: Size,
    val data: LinearBrushData,
)
