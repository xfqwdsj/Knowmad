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

package top.ltfan.knowmad.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle

val AppRadiusExtraSmall = 8.dp
val AppExtraSmallShape = ContinuousRoundedRectangle(AppRadiusExtraSmall)

val AppRadiusSmall = 12.dp
val AppSmallShape = ContinuousRoundedRectangle(AppRadiusSmall)

val AppRadiusMedium = 16.dp
val AppMediumShape = ContinuousRoundedRectangle(AppRadiusMedium)

val AppRadiusLarge = 24.dp
val AppLargeShape = ContinuousRoundedRectangle(AppRadiusLarge)

val AppRadiusExtraLarge = 36.dp
val AppExtraLargeShape = ContinuousRoundedRectangle(AppRadiusExtraLarge)

val AppShapes = Shapes(
    extraSmall = AppExtraSmallShape,
    small = AppSmallShape,
    medium = AppMediumShape,
    large = AppLargeShape,
    extraLarge = AppExtraLargeShape,
)
