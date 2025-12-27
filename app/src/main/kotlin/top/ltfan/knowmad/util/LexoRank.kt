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

package top.ltfan.knowmad.util

val UnsafeEmptyRank = byteArrayOf(0x80.toByte())

fun calculateLexoRank(prev: ByteArray?, next: ByteArray?): ByteArray {
    val p = prev ?: byteArrayOf()
    val n = next ?: byteArrayOf()

    val result = mutableListOf<Byte>()
    var i = 0

    while (true) {
        val prevVal = if (i < p.size) p[i].toInt() and 0xFF else 0
        val nextVal = if (i < n.size) n[i].toInt() and 0xFF else 256

        if (prevVal == nextVal) {
            result.add(prevVal.toByte())
            i++
            continue
        }

        if (nextVal - prevVal > 1) {
            val mid = (prevVal + nextVal) / 2
            result.add(mid.toByte())
            break
        } else {
            result.add(prevVal.toByte())
            i++
        }
    }

    return result.toByteArray()
}
