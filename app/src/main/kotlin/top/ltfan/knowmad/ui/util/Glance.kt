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

import android.annotation.SuppressLint
import androidx.compose.ui.graphics.Color
import androidx.glance.text.FontWeight
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.text.TextStyle as UiTextStyle
import androidx.glance.text.TextStyle as GlanceTextStyle

@get:SuppressLint("RestrictedApi")
val Color.provider inline get() = ColorProvider(this)

fun UiTextStyle.toGlanceTextStyle(
    colorProvider: ColorProvider = color.provider,
): GlanceTextStyle {
    return GlanceTextStyle(
        color = colorProvider,
        fontSize = fontSize,
        fontWeight = fontWeight?.let {
            val normal = FontWeight.Normal
            val medium = FontWeight.Medium
            val bold = FontWeight.Bold
            when {
                it.weight < medium.value -> normal
                it.weight >= bold.value -> bold
                else -> medium
            }
        },
        fontStyle = fontStyle?.let {
            when (it) {
                Normal -> Normal
                Italic -> Italic
                else -> null
            }
        },
        textAlign = when (textAlign) {
            Left -> Left
            Right -> Right
            Center -> Center
            End -> End
            else -> Start
        },
        textDecoration = textDecoration?.let {
            when (it) {
                Underline -> Underline
                LineThrough -> LineThrough
                else -> null
            }
        },
        fontFamily = fontFamily?.let {
            when (it) {
                SansSerif -> SansSerif
                Serif -> Serif
                Monospace -> Monospace
                Cursive -> Cursive
                else -> null
            }
        },
    )
}
