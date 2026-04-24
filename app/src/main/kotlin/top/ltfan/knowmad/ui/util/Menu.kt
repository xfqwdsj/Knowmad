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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuGroupShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import com.kyant.capsule.ContinuousRoundedRectangle
import top.ltfan.knowmad.ui.theme.AppRadiusMedium
import top.ltfan.knowmad.ui.theme.AppRadiusSmall

@Suppress("UnusedReceiverParameter")
val MenuDefaults.leadingItemThemedShape
    @Composable inline get() = ContinuousRoundedRectangle(
        topStart = AppRadiusMedium,
        topEnd = AppRadiusMedium,
        bottomEnd = AppRadiusSmall,
        bottomStart = AppRadiusSmall,
    )

@Suppress("UnusedReceiverParameter")
val MenuDefaults.trailingItemThemedShape
    @Composable inline get() = ContinuousRoundedRectangle(
        topStart = AppRadiusSmall,
        topEnd = AppRadiusSmall,
        bottomEnd = AppRadiusMedium,
        bottomStart = AppRadiusMedium,
    )

@Composable
fun MenuDefaults.itemThemedShape(index: Int, count: Int): Shape {
    if (count == 1) {
        return MaterialTheme.shapes.medium
    }

    return when (index) {
        0 -> leadingItemThemedShape
        count - 1 -> trailingItemThemedShape
        else -> MaterialTheme.shapes.medium
    }
}

@Suppress("UnusedReceiverParameter")
val MenuDefaults.leadingGroupThemedShape
    @Composable inline get() = ContinuousRoundedRectangle(
        topStart = AppRadiusMedium,
        topEnd = AppRadiusMedium,
        bottomEnd = AppRadiusSmall,
        bottomStart = AppRadiusSmall,
    )

@Suppress("UnusedReceiverParameter")
val MenuDefaults.trailingGroupThemedShape
    @Composable inline get() = ContinuousRoundedRectangle(
        topStart = AppRadiusSmall,
        topEnd = AppRadiusSmall,
        bottomEnd = AppRadiusMedium,
        bottomStart = AppRadiusMedium,
    )

@Composable
fun MenuDefaults.groupThemedShape(index: Int, count: Int): MenuGroupShapes {
    if (count == 1) {
        return MenuGroupShapes(
            shape = MaterialTheme.shapes.medium,
            inactiveShape = MaterialTheme.shapes.small,
        )
    }

    return when (index) {
        0 -> MenuGroupShapes(
            shape = leadingGroupThemedShape,
            inactiveShape = MaterialTheme.shapes.small,
        )

        count - 1 -> MenuGroupShapes(
            shape = trailingGroupThemedShape,
            inactiveShape = MaterialTheme.shapes.small,
        )

        else -> MenuGroupShapes(
            shape = MaterialTheme.shapes.medium,
            inactiveShape = MaterialTheme.shapes.small,
        )
    }
}
