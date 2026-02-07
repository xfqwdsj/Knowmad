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

import android.content.res.Resources
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import biweekly.property.Trigger
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.R
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import biweekly.parameter.Related as BiweeklyRelated

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

            val property: BiweeklyRelated
                inline get() = when (this) {
                    Start -> START
                    End -> END
                }
        }

        override fun toProperty() = Trigger(offset.toProperty(), related.property)

        override fun getString(
            resources: Resources,
            timeZone: TimeZone,
            locale: Locale,
        ): String {
            val negative = offset.isNegative()
            val absOffset = if (negative) -offset else offset
            val templateId = when {
                negative && related == Start -> R.string.schedule_event_reminder_relative_label_before_start
                negative && related == End -> R.string.schedule_event_reminder_relative_label_before_end
                !negative && related == Start -> R.string.schedule_event_reminder_relative_label_after_start
                !negative && related == End -> R.string.schedule_event_reminder_relative_label_after_end
                else -> error("Unreachable")
            }

            val days = absOffset.inWholeDays
            val hours = absOffset.inWholeHours % 24
            val minutes = absOffset.inWholeMinutes % 60
            val seconds = absOffset.inWholeSeconds % 60

            val measures = buildList {
                if (days > 0) add(Measure(days, MeasureUnit.DAY))
                if (hours > 0) add(Measure(hours, MeasureUnit.HOUR))
                if (minutes > 0) add(Measure(minutes, MeasureUnit.MINUTE))
                if (seconds > 0) add(Measure(seconds, MeasureUnit.SECOND))
            }

            val format = MeasureFormat.getInstance(locale, SHORT)
            return resources.getString(templateId, format.formatMeasures(*measures.toTypedArray()))
        }
    }

    @Serializable
    @SerialName("Absolute")
    data class Absolute(val time: Instant) : ICalendarTrigger {
        override fun toProperty() = Trigger(Date.from(time.toJavaInstant()))

        override fun getString(
            resources: Resources,
            timeZone: TimeZone,
            locale: Locale,
        ): String {
            val localDateTime = time.toLocalDateTime(timeZone).toJavaLocalDateTime()

            val formatter = DateTimeFormatter
                .ofLocalizedDateTime(MEDIUM)
                .withLocale(locale)

            return formatter.format(localDateTime)
        }
    }

    fun toProperty(): Trigger

    fun getString(
        resources: Resources,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String
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
