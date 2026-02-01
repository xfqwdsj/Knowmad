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

import biweekly.ICalVersion
import biweekly.io.ParseContext
import biweekly.io.scribe.property.RecurrencePropertyScribe
import biweekly.io.scribe.property.RecurrenceRuleScribe
import biweekly.property.RecurrenceProperty
import biweekly.util.ByDay
import biweekly.util.Frequency
import biweekly.util.ICalDate
import biweekly.util.Recurrence
import kotlinx.datetime.DayOfWeek
import kotlinx.serialization.Serializable
import java.util.Date
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

@Serializable
data class ICalendarRecurrenceRule(
    val frequency: ICalendarRecurrenceRuleFrequency,
    val interval: Int? = null,
    val count: Int? = null,
    val until: Instant? = null,
    val bySecond: List<Int> = emptyList(),
    val byMinute: List<Int> = emptyList(),
    val byHour: List<Int> = emptyList(),
    val byDay: List<ICalendarRecurrenceRuleByDay> = emptyList(),
    val byMonthDay: List<Int> = emptyList(),
    val byYearDay: List<Int> = emptyList(),
    val byWeekNo: List<Int> = emptyList(),
    val byMonth: List<Int> = emptyList(),
    val bySetPos: List<Int> = emptyList(),
    val weekStart: DayOfWeek? = null,
) {
    fun toProperty() = KnowmadRecurrenceProperty(this)

    fun toICalValue(): Recurrence = Recurrence.Builder(frequency.iCalValue).apply {
        interval(interval)
        count(count)
        until?.toJavaInstant()?.let { until(ICalDate(Date.from(it))) }
        bySecond(bySecond)
        byMinute(byMinute)
        byHour(byHour)
        byDay.forEach {
            byDay(it.ordinal, it.dayOfWeek.toICalDayOfWeek())
        }
        byMonthDay(byMonthDay)
        byYearDay(byYearDay)
        byWeekNo(byWeekNo)
        byMonth(byMonth)
        bySetPos(bySetPos)
        workweekStarts(weekStart?.toICalDayOfWeek())
    }.build()

    companion object {
        fun fromICalValue(
            value: Recurrence,
            errors: MutableList<String> = mutableListOf(),
        ): ICalendarRecurrenceRule? {
            return ICalendarRecurrenceRule(
                frequency = value.frequency?.toICalendarRecurrenceRuleFrequency() ?: run {
                    errors.add("Frequency is null")
                    return null
                },
                interval = value.interval,
                count = value.count,
                until = value.until?.toInstant()?.toKotlinInstant(),
                bySecond = value.bySecond,
                byMinute = value.byMinute,
                byHour = value.byHour,
                byDay = value.byDay.map { it.toICalendarRecurrenceRuleByDay() },
                byMonthDay = value.byMonthDay,
                byYearDay = value.byYearDay,
                byWeekNo = value.byWeekNo,
                byMonth = value.byMonth,
                bySetPos = value.bySetPos,
                weekStart = value.workweekStarts?.toKotlinDayOfWeek(),
            )
        }

        fun parse(
            value: String,
            version: ICalVersion = ICalendarVersion,
        ): ICalendarRecurrenceRule? {
            val parseContext = ParseContext().apply {
                this.version = version
            }
            val recurrence = runCatching {
                RecurrenceRuleScribe()
                    .parseText(value, null, null, parseContext)
                    ?.value ?: return null
            }.getOrNull() ?: return null
            return fromICalValue(recurrence)
        }
    }
}

fun Recurrence?.toICalendarRecurrenceRule(
    errors: MutableList<String> = mutableListOf(),
): ICalendarRecurrenceRule? {
    return ICalendarRecurrenceRule.fromICalValue(
        value = this ?: run {
            errors.add("Recurrence is null")
            return null
        },
        errors = errors,
    )
}

data class KnowmadRecurrenceProperty(
    val recurrenceRule: ICalendarRecurrenceRule,
) : RecurrenceProperty(recurrenceRule.toICalValue()) {
    companion object : RecurrencePropertyScribe<KnowmadRecurrenceProperty>(
        KnowmadRecurrenceProperty::class.java,
        KnowmadRecurrenceProperty.PROPERTY_NAME,
    ) {
        const val PROPERTY_NAME = "X-KNOWMAD-RECURRENCE"

        override fun newInstance(value: Recurrence?): KnowmadRecurrenceProperty? {
            val rule = value?.toICalendarRecurrenceRule() ?: return null
            return KnowmadRecurrenceProperty(rule)
        }
    }
}

@Serializable
enum class ICalendarRecurrenceRuleFrequency(val iCalValue: Frequency) {
    Secondly(SECONDLY),
    Minutely(MINUTELY),
    Hourly(HOURLY),
    Daily(DAILY),
    Weekly(WEEKLY),
    Monthly(MONTHLY),
    Yearly(YEARLY);

    companion object {
        private val map = entries.associateBy(ICalendarRecurrenceRuleFrequency::iCalValue)
        fun fromICalValue(value: Frequency) =
            map[value] ?: error("Unknown ICalendarRecurrenceRuleFrequency iCalValue: $value")
    }
}

fun Frequency.toICalendarRecurrenceRuleFrequency() =
    ICalendarRecurrenceRuleFrequency.fromICalValue(this)

@Serializable
data class ICalendarRecurrenceRuleByDay(
    val dayOfWeek: DayOfWeek,
    val ordinal: Int? = null,
) {
    companion object {
        fun fromICalValue(value: ByDay): ICalendarRecurrenceRuleByDay {
            return ICalendarRecurrenceRuleByDay(value.day.toKotlinDayOfWeek(), value.num)
        }
    }
}

fun ByDay.toICalendarRecurrenceRuleByDay() = ICalendarRecurrenceRuleByDay.fromICalValue(this)
