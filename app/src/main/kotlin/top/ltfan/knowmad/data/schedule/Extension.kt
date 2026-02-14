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
import biweekly.ICalendar
import biweekly.io.TimezoneAssignment
import biweekly.io.TimezoneInfo
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaZoneId
import kotlinx.datetime.toLocalDateTime
import top.ltfan.knowmad.R
import top.ltfan.knowmad.ui.util.format
import top.ltfan.omnical.icalendar.ICalendarColor
import top.ltfan.omnical.icalendar.ICalendarTrigger
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle.MEDIUM
import java.util.Locale
import kotlin.uuid.Uuid
import androidx.compose.ui.graphics.Color as ComposeColor
import biweekly.property.Color as BiweeklyColor
import kotlinx.datetime.TimeZone as KotlinTimeZone
import java.util.TimeZone as JavaTimeZone

fun SemesterEntity.constructICalendar(): ICalendar = ICalendar().apply {
    version = ICalendarVersion
    setUid(id.toString())
    addName(name)
    timezoneInfo = TimezoneInfo().apply {
        defaultTimezone = TimezoneAssignment(
            JavaTimeZone.getTimeZone(timeZone.toJavaZoneId()),
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
    errors: MutableList<String>? = null,
) = events.flatMap { vEvent ->
    vEvent.parse(
        timeZoneInfo = timezoneInfo,
        onNewRecurrenceRule = onNewRecurrenceRule,
        errors = errors,
    )
}

fun RecurrenceRuleEntity.toProperty() = KnowmadRecurrenceRuleProperty(this)

fun ICalendarColor.Companion.pickFromPalette(id: Uuid): ICalendarColor =
    pickFromPalette(id.hashCode().toUInt())

fun BiweeklyColor?.convertOrDefault(vararg ids: Uuid?, defaultId: Uuid): ICalendarColor {
    this?.value?.let { ICalendarColor.fromValue(it) }?.let { return it }
    for (id in ids) {
        id?.let { return ICalendarColor.pickFromPalette(it) }
    }
    return ICalendarColor.pickFromPalette(defaultId)
}

val ICalendarColor.compose inline get() = ComposeColor(argb)

fun ICalendarTrigger.getString(
    resources: Resources,
    timeZone: KotlinTimeZone = KotlinTimeZone.currentSystemDefault(),
    locale: Locale = Locale.getDefault(),
): String = when (this) {
    is Relative -> {
        val negative = offset.isNegative()
        val absOffset = if (negative) -offset else offset
        val templateId = when {
            negative && related == Start -> R.string.schedule_event_reminder_relative_label_before_start
            negative && related == End -> R.string.schedule_event_reminder_relative_label_before_end
            !negative && related == Start -> R.string.schedule_event_reminder_relative_label_after_start
            !negative && related == End -> R.string.schedule_event_reminder_relative_label_after_end
            else -> error("Unreachable")
        }

        resources.getString(templateId, absOffset.format(locale))
    }

    is Absolute -> {
        val localDateTime = time.toLocalDateTime(timeZone).toJavaLocalDateTime()

        val formatter = DateTimeFormatter
            .ofLocalizedDateTime(MEDIUM)
            .withLocale(locale)

        formatter.format(localDateTime)
    }
}
