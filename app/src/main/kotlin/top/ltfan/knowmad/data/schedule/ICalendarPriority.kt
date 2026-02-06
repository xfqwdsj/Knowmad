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
enum class ICalendarPriority(val value: UByte) {
    None(0u),
    P1(1u),
    P2(2u),
    P3(3u),
    P4(4u),
    P5(5u),
    P6(6u),
    P7(7u),
    P8(8u),
    P9(9u);

    companion object {
        private val map = entries.associateBy(ICalendarPriority::value)

        fun fromValue(value: UByte): ICalendarPriority {
            return map[value] ?: None
        }

        fun fromNumber(value: Number): ICalendarPriority {
            return fromValue(value.toByte().toUByte())
        }
    }

    val type by lazy {
        for (priorityType in PriorityType.entries) {
            if (value <= priorityType.maxPriorityValue) {
                return@lazy priorityType
            }
        }
        PriorityType.None
    }
    val reversedValueInType by lazy {
        if (type == PriorityType.None) {
            0u
        } else {
            type.maxPriorityValue - value + 1u
        }
    }
    val comparableValue = value.takeIf { it in 1u..9u } ?: 10u
    val property = Priority(value.toInt())
}

@Serializable
enum class PriorityType(val maxPriorityValue: UByte) {
    None(0u),
    High(4u),
    Medium(5u),
    Low(9u);
}

fun Priority?.toICalendarPriority(): ICalendarPriority {
    return ICalendarPriority.fromNumber(this?.value ?: 0)
}
