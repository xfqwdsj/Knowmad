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

import biweekly.ICalendar
import biweekly.io.TimezoneAssignment
import biweekly.io.TimezoneInfo
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.toJavaZoneId
import java.util.TimeZone
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun SemesterEntity.constructICalendar(): ICalendar = ICalendar().apply {
    version = ICalendarVersion
    setUid(id.toString())
    addName(name)
    timezoneInfo = TimezoneInfo().apply {
        defaultTimezone = TimezoneAssignment(
            TimeZone.getTimeZone(timeZone.toJavaZoneId()),
            timeZone.id,
        )
    }
}

fun SemesterEntity.toICalendar(
    events: List<Event>? = null,
): ICalendar = constructICalendar().apply {
    events?.let { addEvents(it) }
}

fun SemesterEntity.exportICalendar(
    events: List<Event>,
): ICalendar = constructICalendar().apply {
    addExportedEvents(events)
}

fun ICalendar.addEvents(events: List<Event>) {
    events.forEach { event ->
        addEvent(event.toVEvent())
    }
}

fun ICalendar.addExportedEvents(events: List<Event>) {
    events.forEach { exportedEvent ->
        addEvent(exportedEvent.exportVEvent())
    }
}

fun ICalendar.parse(
    onNewRecurrenceRule: (
        rule: RecurrenceRuleEntity,
        course: CourseEntity?,
    ) -> CourseEntity? = { _, _ -> null },
    errors: MutableList<String> = mutableListOf(),
) = events.flatMap { vEvent ->
    vEvent.parse(
        timeZoneInfo = timezoneInfo,
        onNewRecurrenceRule = onNewRecurrenceRule,
        errors = errors,
    )
}

fun Duration.toProperty(): biweekly.util.Duration {
    return biweekly.util.Duration.fromMillis(this.inWholeMilliseconds)
}

fun biweekly.util.Duration.toDuration(): Duration {
    return toMillis().milliseconds
}

fun biweekly.util.DayOfWeek.toKotlinDayOfWeek(): DayOfWeek = when (this) {
    MONDAY -> MONDAY
    TUESDAY -> TUESDAY
    WEDNESDAY -> WEDNESDAY
    THURSDAY -> THURSDAY
    FRIDAY -> FRIDAY
    SATURDAY -> SATURDAY
    SUNDAY -> SUNDAY
}

fun DayOfWeek.toICalDayOfWeek(): biweekly.util.DayOfWeek = when (this) {
    MONDAY -> MONDAY
    TUESDAY -> TUESDAY
    WEDNESDAY -> WEDNESDAY
    THURSDAY -> THURSDAY
    FRIDAY -> FRIDAY
    SATURDAY -> SATURDAY
    SUNDAY -> SUNDAY
}
