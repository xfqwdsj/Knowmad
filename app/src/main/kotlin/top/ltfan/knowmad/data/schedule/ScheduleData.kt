/*
 * Knowmad - Knowledge nomad
 * Copyright (C) 2025-2026 LTFan (aka xfqwdsj)
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

import biweekly.component.VAlarm
import biweekly.component.VEvent
import biweekly.parameter.Related
import biweekly.property.Trigger
import kotlinx.serialization.Serializable
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import kotlin.uuid.Uuid

@Serializable
sealed interface Event {
    val id: Uuid
    val semester: SemesterEntity
    val name: String
    val location: String
    val color: ICalendarColor
    val startTime: Instant
    val endTime: Instant
    val reminders: List<Duration>
    val notes: String?
    val createdAt: Instant
    val updatedAt: Instant

    fun toEntity(): EventEntity
    fun toVEvent(): VEvent
    fun exportVEvent() = VEvent().apply {
        uid()
        semester()
        summary()
        location()
        color()
        dateStartAndEnd()
        alarms()
        description()
        metaTimes()
    }

    @Serializable
    data class Normal(
        override val id: Uuid = Uuid.generateV7(),
        override val semester: SemesterEntity,
        override val name: String,
        override val location: String,
        override val color: ICalendarColor,
        override val startTime: Instant,
        override val endTime: Instant,
        override val reminders: List<Duration> = emptyList(),
        override val notes: String? = null,
        override val createdAt: Instant = Clock.System.now(),
        override val updatedAt: Instant = createdAt,
    ) : Event {
        override fun toEntity() = EventEntity(
            id = id,
            semesterId = semester.id,
            courseId = null,
            name = name,
            instructor = null,
            location = location,
            color = color,
            startTime = startTime,
            endTime = endTime,
            reminders = reminders,
            notes = notes,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

        override fun toVEvent() = VEvent().apply {
            uid()
            semester()
            summary()
            location()
            color()
            dateStartAndEnd()
            alarms()
            description()
            metaTimes()
        }
    }

    @Serializable
    data class Course(
        override val id: Uuid = Uuid.generateV7(),
        override val semester: SemesterEntity,
        val course: CourseEntity,
        override val name: String = course.name,
        val instructor: String = course.instructor,
        override val location: String = course.location,
        override val color: ICalendarColor,
        override val startTime: Instant,
        override val endTime: Instant,
        override val reminders: List<Duration> = emptyList(),
        override val notes: String? = null,
        override val createdAt: Instant = Clock.System.now(),
        override val updatedAt: Instant = createdAt,
    ) : Event {
        override fun toEntity() = EventEntity(
            id = id,
            semesterId = semester.id,
            courseId = course.id,
            name = name,
            instructor = instructor,
            location = location,
            color = color,
            startTime = startTime,
            endTime = endTime,
            reminders = reminders,
            notes = notes,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

        override fun toVEvent() = VEvent().apply {
            uid()
            semester()
            course()
            summary()
            instructor()
            location()
            color()
            dateStartAndEnd()
            alarms()
            description()
            metaTimes()
        }

        fun VEvent.course() {
            addProperty(CourseProperty(course))
        }

        fun VEvent.instructor() {
            addProperty(InstructorProperty(instructor))
        }
    }

    fun VEvent.uid() {
        setUid(id.toString())
    }

    fun VEvent.semester() {
        addProperty(SemesterProperty(semester))
        addCategories(semester.name)
    }

    fun VEvent.summary() {
        setSummary(name)
    }

    fun VEvent.location() {
        setLocation(this@Event.location)
    }

    fun VEvent.color() {
        color = this@Event.color.property
    }

    fun VEvent.dateStartAndEnd() {
        setDateStart(Date.from(startTime.toJavaInstant()))
        setDateEnd(Date.from(endTime.toJavaInstant()))
    }

    val vAlarms
        get() = reminders.map { reminder ->
            VAlarm.display(
                Trigger(
                    biweekly.util.Duration.fromMillis(-reminder.inWholeMilliseconds),
                    Related.START,
                ),
                name,
            )
        }

    fun VEvent.alarms() {
        alarms += vAlarms
    }

    fun VEvent.description() {
        setDescription(notes)
    }

    fun VEvent.metaTimes() {
        setDateTimeStamp(Date.from(java.time.Instant.now().truncatedTo(ChronoUnit.SECONDS)))
        setCreated(Date.from(createdAt.toJavaInstant()))
        setLastModified(Date.from(updatedAt.toJavaInstant()))
    }

    companion object {
        fun parse(vEvent: VEvent): Event? {
            val semesterProperty = vEvent.getProperty(SemesterProperty::class.java) ?: return null
            val semester = semesterProperty.semester
            val courseProperty = vEvent.getProperty(CourseProperty::class.java)
            val course = courseProperty?.course

            val id = Uuid.parseOrNull(vEvent.uid.value) ?: return null
            val name = vEvent.summary?.value
            val instructor = vEvent.getProperty(InstructorProperty::class.java)?.value
            val location = vEvent.location?.value
            val color = vEvent.color?.let { ICalendarColor.fromValue(it.value) }
                ?: ICalendarColor.fromId(id)
            val startTime = vEvent.dateStart?.value?.toInstant()?.toKotlinInstant()
                ?: return null
            val endTime = vEvent.dateEnd?.value?.toInstant()?.toKotlinInstant()
                ?: return null
            val reminders = vEvent.alarms?.mapNotNull { alarm ->
                alarm.trigger?.let { trigger ->
                    val duration = trigger.duration
                    val date = trigger.date
                    if (duration != null) {
                        when (trigger.related) {
                            Related.START -> (-duration.toMillis()).milliseconds
                            Related.END -> startTime - endTime - duration.toMillis().milliseconds
                            else -> null
                        }
                    } else if (date != null) {
                        date.toInstant().toKotlinInstant() - startTime
                    } else {
                        null
                    }
                }
            } ?: emptyList()
            val notes = vEvent.description?.value
            val createdAt = vEvent.created?.value?.toInstant()?.toKotlinInstant()
                ?: Clock.System.now()
            val updatedAt = vEvent.lastModified?.value?.toInstant()?.toKotlinInstant()
                ?: Clock.System.now()

            return if (course != null) {
                Course(
                    id = id,
                    semester = semester,
                    course = course,
                    name = name ?: course.name,
                    instructor = instructor ?: course.instructor,
                    location = location ?: course.location,
                    color = color,
                    startTime = startTime,
                    endTime = endTime,
                    reminders = reminders,
                    notes = notes,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                )
            } else {
                if (name == null || location == null) {
                    return null
                }
                Normal(
                    id = id,
                    semester = semester,
                    name = name,
                    location = location,
                    color = color,
                    startTime = startTime,
                    endTime = endTime,
                    reminders = reminders,
                    notes = notes,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                )
            }
        }
    }
}

fun EventWithSemesterAndCourse.toEvent(): Event {
    return if (course != null) {
        Event.Course(
            id = event.id,
            semester = semester,
            course = course,
            name = event.name ?: course.name,
            instructor = event.instructor ?: course.instructor,
            location = event.location ?: course.location,
            color = event.color,
            startTime = event.startTime,
            endTime = event.endTime,
            reminders = event.reminders,
            notes = event.notes,
            createdAt = event.createdAt,
            updatedAt = event.updatedAt,
        )
    } else {
        Event.Normal(
            id = event.id,
            semester = semester,
            name = event.name ?: error("Event name is null for normal event with id ${event.id}"),
            location = event.location
                ?: error("Event location is null for normal event with id ${event.id}"),
            color = event.color,
            startTime = event.startTime,
            endTime = event.endTime,
            reminders = event.reminders,
            notes = event.notes,
            createdAt = event.createdAt,
            updatedAt = event.updatedAt,
        )
    }
}
