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

import biweekly.property.Trigger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

@Serializable
sealed interface ICalendarTrigger {
    @Serializable
    @SerialName("Relative")
    data class Relative(
        val offset: Duration,
        val related: Related = Related.Start,
    ) : ICalendarTrigger {
        @Serializable
        @SerialName("Related")
        enum class Related {
            Start, End;

            val property: biweekly.parameter.Related
                inline get() = when (this) {
                    Start -> biweekly.parameter.Related.START
                    End -> biweekly.parameter.Related.END
                }
        }

        override fun toProperty() = Trigger(offset.toProperty(), related.property)
    }

    @Serializable
    @SerialName("Absolute")
    data class Absolute(val time: Instant) : ICalendarTrigger {
        override fun toProperty() = Trigger(Date.from(time.toJavaInstant()))
    }

    fun toProperty(): Trigger
}

fun Trigger.toICalendarTrigger(errors: MutableList<String> = mutableListOf()): ICalendarTrigger? {
    duration?.let { duration ->
        return ICalendarTrigger.Relative(
            offset = duration.toDuration(),
            related = when (related) {
                END -> End
                else -> Start
            },
        )
    }
    date?.let { date ->
        return ICalendarTrigger.Absolute(
            time = date.toInstant().toKotlinInstant(),
        )
    }
    errors += "Trigger property is neither duration-based nor date-based."
    return null
}
