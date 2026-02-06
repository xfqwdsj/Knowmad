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

package top.ltfan.knowmad.data.schedule

import biweekly.property.Priority
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("ICalendarPriority")
enum class ICalendarPriority(val value: UByte, val type: PriorityType) {
    None(0u, PriorityType.None),
    P1(1u, High),
    P2(2u, High),
    P3(3u, High),
    P4(4u, High),
    P5(5u, Medium),
    P6(6u, Low),
    P7(7u, Low),
    P8(8u, Low),
    P9(9u, Low);

    companion object {
        private val map = entries.associateBy(ICalendarPriority::value)

        fun fromValue(value: UByte): ICalendarPriority {
            return map[value] ?: None
        }

        fun fromNumber(value: Number): ICalendarPriority {
            return fromValue(value.toByte().toUByte())
        }
    }

    val comparableValue = value.takeIf { it in 1u..9u } ?: 10u
    val property = Priority(value.toInt())
}

@Serializable
enum class PriorityType {
    High, Medium, Low, None;
}

fun Priority?.toICalendarPriority(): ICalendarPriority {
    return ICalendarPriority.fromNumber(this?.value ?: 0)
}
