/*
 * Knowmad - Knowledge nomad
 * Copyright (C) 2025-2026 LTFan (aka xfqwdsj)
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

val AppRadiusExtraSmall = 12.dp
val AppExtraSmallShape = ContinuousRoundedRectangle(AppRadiusExtraSmall)

val AppRadiusSmall = 16.dp
val AppSmallShape = ContinuousRoundedRectangle(AppRadiusSmall)

val AppRadiusMedium = 24.dp
val AppMediumShape = ContinuousRoundedRectangle(AppRadiusMedium)

val AppRadiusLarge = 30.dp
val AppLargeShape = ContinuousRoundedRectangle(AppRadiusLarge)

val AppRadiusLargeIncreased = 32.dp
val AppLargeIncreasedShape = ContinuousRoundedRectangle(AppRadiusLargeIncreased)

val AppRadiusExtraLarge = 38.dp
val AppExtraLargeShape = ContinuousRoundedRectangle(AppRadiusExtraLarge)

val AppRadiusExtraLargeIncreased = 42.dp
val AppExtraLargeIncreasedShape = ContinuousRoundedRectangle(AppRadiusExtraLargeIncreased)

val AppRadiusExtraExtraLarge = 48.dp
val AppExtraExtraLargeShape = ContinuousRoundedRectangle(AppRadiusExtraExtraLarge)

val AppShapes = Shapes(
    extraSmall = AppExtraSmallShape,
    small = AppSmallShape,
    medium = AppMediumShape,
    large = AppLargeShape,
    largeIncreased = AppLargeIncreasedShape,
    extraLarge = AppExtraLargeShape,
    extraLargeIncreased = AppExtraLargeIncreasedShape,
    extraExtraLarge = AppExtraExtraLargeShape,
)
