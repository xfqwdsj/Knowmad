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

fun calculateLexoRank(prev: ByteArray?, next: ByteArray?): ByteArray {
    val p = prev ?: byteArrayOf()
    val n = next ?: byteArrayOf()

    val result = ArrayList<Byte>(maxOf(p.size, n.size) + 1)

    var i = 0
    while (true) {
        val byteP = if (i < p.size) p[i].toInt() and 0xff else 0
        val byteN = when {
            i < n.size -> n[i].toInt() and 0xff
            next == null -> 256          // 逻辑上界（末尾插入）
            else -> 256                  // next 存在但已到末尾
        }

        if (byteN - byteP > 1) {
            result.add(((byteP + byteN) ushr 1).toByte())
            break
        }

        // 无法分裂，复制 prev 位
        result.add(byteP.toByte())
        i++

        // 兜底：防止理论 bug / 恶意数据
        if (i > 64) {
            // 强行扩展空间
            result.add(0x80.toByte())
            break
        }
    }

    return result.toByteArray()
}
