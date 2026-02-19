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

package top.ltfan.knowmad.data.geo

import kotlinx.serialization.Serializable

@Serializable
data class Wgs84Point(
    val longitude: Double,
    val latitude: Double,
    val altitude: Double? = null,
) {
    val zOrder by lazy { calculateZOrder(longitude, latitude) }
}

fun calculateZOrder(longitude: Double, latitude: Double): Long {
    val x = (((longitude + 180.0) / 360.0).coerceIn(0.0, 1.0) * 0xFFFFFFFFL).toLong()
    val y = (((latitude + 90.0) / 180.0).coerceIn(0.0, 1.0) * 0xFFFFFFFFL).toLong()

    return expandBits(x) or (expandBits(y) shl 1)
}

private fun expandBits(value: Long): Long {
    var x = value and 0xFFFFFFFFL
    x = (x or (x shl 16)) and 0x0000FFFF0000FFFFL
    x = (x or (x shl 8)) and 0x00FF00FF00FF00FFL
    x = (x or (x shl 4)) and 0x0F0F0F0F0F0F0F0FL
    x = (x or (x shl 2)) and 0x3333333333333333L
    x = (x or (x shl 1)) and 0x5555555555555555L
    return x
}

fun Collection<Wgs84Point>.calculateBoundingBox(): BoundingBox {
    require(isNotEmpty()) { "Cannot calculate bounding box of an empty collection." }

    var northernLatitude = Double.NEGATIVE_INFINITY
    var southernLatitude = Double.POSITIVE_INFINITY

    val sortedLongitudes = asSequence()
        .onEach {
            if (it.latitude > northernLatitude) northernLatitude = it.latitude
            if (it.latitude < southernLatitude) southernLatitude = it.latitude
        }
        .map { it.longitude }
        .distinct()
        .sorted()
        .toList()

    if (sortedLongitudes.size == 1) {
        return BoundingBox(
            sortedLongitudes[0],
            sortedLongitudes[0],
            northernLatitude,
            southernLatitude,
        )
    }

    var maxGap = 0.0
    var west = sortedLongitudes[0]
    var east = sortedLongitudes.last()

    for (i in 0 until sortedLongitudes.size - 1) {
        val gap = sortedLongitudes[i + 1] - sortedLongitudes[i]
        if (gap > maxGap) {
            maxGap = gap
            west = sortedLongitudes[i + 1]
            east = sortedLongitudes[i]
        }
    }

    val wrapGap = (360.0 - sortedLongitudes.last()) + sortedLongitudes[0]
    if (wrapGap > maxGap) {
        west = sortedLongitudes[0]
        east = sortedLongitudes.last()
    }

    return BoundingBox(
        west = west,
        east = east,
        north = northernLatitude,
        south = southernLatitude,
    )
}

@Serializable
data class BoundingBox(
    val west: Double,
    val east: Double,
    val north: Double,
    val south: Double,
) {
    val northWest by lazy { Wgs84Point(west, north) }
    val northEast by lazy { Wgs84Point(east, north) }
    val southWest by lazy { Wgs84Point(west, south) }
    val southEast by lazy { Wgs84Point(east, south) }
}
