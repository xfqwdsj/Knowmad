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

import kotlinx.serialization.Serializable
import top.ltfan.omnical.icalendar.ICalendarPriority

val PriorityTypeMap = mutableMapOf<ICalendarPriority, PriorityType>()
val ReversedPriorityValueMap = mutableMapOf<ICalendarPriority, UByte>()

@Serializable
enum class PriorityType(val maxPriorityValue: UByte) {
    None(0u),
    High(4u),
    Medium(5u),
    Low(9u);
}

val ICalendarPriority.type
    inline get() = PriorityTypeMap.getOrPut(this) {
        for (priorityType in PriorityType.entries) {
            if (value <= priorityType.maxPriorityValue) {
                return@getOrPut priorityType
            }
        }
        None
    }

val ICalendarPriority.reversedValueInType
    inline get() = ReversedPriorityValueMap.getOrPut(this) {
        if (type == None) {
            0u
        } else {
            type.maxPriorityValue - value + 1u
        }.toUByte()
    }
