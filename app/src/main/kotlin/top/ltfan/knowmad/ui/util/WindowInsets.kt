/*
 * Knowmad - Knowledge nomad
 * Copyright (C) 2025 LTFan (aka xfqwdsj)
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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import top.ltfan.dslutilities.ValueDsl

val AppWindowInsets @Composable inline get() = WindowInsets.safeDrawing

@WindowInsetsSidesBuilder.Dsl
object WindowInsetsSidesBuilder {
    val start = WindowInsetsSides.Start
    val top = WindowInsetsSides.Top
    val end = WindowInsetsSides.End
    val bottom = WindowInsetsSides.Bottom
    val vertical = WindowInsetsSides.Vertical
    val horizontal = WindowInsetsSides.Horizontal

    @DslMarker
    annotation class Dsl
}

inline fun WindowInsets.only(block: WindowInsetsSidesBuilder.() -> WindowInsetsSides) =
    this.only(WindowInsetsSidesBuilder.block())

@Stable
@WindowInsetsOperationScope.Dsl
class WindowInsetsOperationScope(private val insets: WindowInsets) : ValueDsl() {
    var left by prepared<(inset: Dp) -> Dp>({ it })
    var top by prepared<(inset: Dp) -> Dp>({ it })
    var right by prepared<(inset: Dp) -> Dp>({ it })
    var bottom by prepared<(inset: Dp) -> Dp>({ it })

    fun left(block: (inset: Dp) -> Dp) {
        left = block
    }

    fun top(block: (inset: Dp) -> Dp) {
        top = block
    }

    fun right(block: (inset: Dp) -> Dp) {
        right = block
    }

    fun bottom(block: (inset: Dp) -> Dp) {
        bottom = block
    }

    fun asWindowInsets(): WindowInsets {
        return object : WindowInsets {
            override fun getLeft(density: Density, layoutDirection: LayoutDirection) =
                with(density) {
                    left.invoke(insets.getLeft(density, layoutDirection).toDp()).roundToPx()
                }

            override fun getTop(density: Density) =
                with(density) {
                    top.invoke(insets.getTop(density).toDp()).roundToPx()
                }

            override fun getRight(density: Density, layoutDirection: LayoutDirection) =
                with(density) {
                    right.invoke(insets.getRight(density, layoutDirection).toDp()).roundToPx()
                }

            override fun getBottom(density: Density) =
                with(density) {
                    bottom.invoke(insets.getBottom(density).toDp()).roundToPx()
                }
        }
    }

    @Composable
    fun asPaddingValues(): PaddingValues {
        return PaddingValues.build {
            val density = LocalDensity.current
            val layoutDirection = LocalLayoutDirection.current

            val getLeft = this@WindowInsetsOperationScope.left
            val getTop = this@WindowInsetsOperationScope.top
            val getRight = this@WindowInsetsOperationScope.right
            val getBottom = this@WindowInsetsOperationScope.bottom
            val insets = this@WindowInsetsOperationScope.insets

            with(density) {
                start = when (layoutDirection) {
                    LayoutDirection.Ltr -> getLeft(
                        insets.getLeft(density, layoutDirection).toDp(),
                    )

                    LayoutDirection.Rtl -> getRight(
                        insets.getRight(density, layoutDirection).toDp(),
                    )
                }

                top = getTop(insets.getTop(density).toDp())

                end = when (layoutDirection) {
                    LayoutDirection.Ltr -> getRight(
                        insets.getRight(density, layoutDirection).toDp(),
                    )

                    LayoutDirection.Rtl -> getLeft(
                        insets.getLeft(density, layoutDirection).toDp(),
                    )
                }

                bottom = getBottom(insets.getBottom(density).toDp())
            }
        }
    }

    @DslMarker
    annotation class Dsl
}

@Composable
fun WindowInsets.operateAsWindowInsets(
    block: WindowInsetsOperationScope.() -> Unit,
): WindowInsets {
    val currentBlock by rememberUpdatedState(block)

    return remember(this, currentBlock) {
        WindowInsetsOperationScope(this).apply(currentBlock).asWindowInsets()
    }
}

@Composable
operator fun WindowInsets.plus(padding: PaddingValues): WindowInsets {
    val getLeft = padding.getLeft
    val getTop = padding.getTop
    val getRight = padding.getRight
    val getBottom = padding.getBottom

    return operateAsWindowInsets {
        left { it + getLeft() }
        top { it + getTop() }
        right { it + getRight() }
        bottom { it + getBottom() }
    }
}
