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

import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

val BackdropEffectsLight: BackdropEffectScope.() -> Unit = {
    vibrancy()
    blur(2.dp.toPx())
    lens(12.dp.toPx(), 24.dp.toPx())
}

val BackdropEffectsMedium: BackdropEffectScope.() -> Unit = {
    vibrancy()
    blur(4.dp.toPx())
    lens(16.dp.toPx(), 32.dp.toPx())
}

val BackdropEffectsHeavy: BackdropEffectScope.() -> Unit = {
    vibrancy()
    blur(12.dp.toPx())
    lens(24.dp.toPx(), 36.dp.toPx())
}
