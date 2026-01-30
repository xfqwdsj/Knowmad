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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Duration
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
    val reminders: Reminders
    val notes: String?
    val priority: ICalendarPriority
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
        priority()
        metaTimes()
    }

    @Serializable
    @SerialName("Normal")
    data class Normal(
        override val id: Uuid = Uuid.generateV7(),
        override val semester: SemesterEntity,
        override val name: String,
        override val location: String,
        override val color: ICalendarColor,
        override val startTime: Instant,
        override val endTime: Instant,
        override val reminders: Reminders = Empty,
        override val notes: String? = null,
        override val priority: ICalendarPriority = None,
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
            priority = priority,
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
            priority()
            metaTimes()
        }
    }

    @Serializable
    @SerialName("Course")
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
        override val reminders: Reminders = Empty,
        override val notes: String? = null,
        override val priority: ICalendarPriority = None,
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
            priority = priority,
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
            priority()
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

    val vAlarms get() = reminders.list.map { it.toVAlarm(defaultDisplayText = name) }

    fun VEvent.alarms() {
        alarms += vAlarms
    }

    fun VEvent.description() {
        setDescription(notes)
    }

    fun VEvent.priority() {
        priority = this@Event.priority.property
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
                ?: ICalendarColor.fromId(course?.id ?: semester.id)
            val startTime = vEvent.dateStart?.value?.toInstant()?.toKotlinInstant()
                ?: return null
            val endTime = vEvent.dateEnd?.value?.toInstant()?.toKotlinInstant()
                ?: return null
            val reminders = vEvent.alarms.toReminders()
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
            priority = event.priority,
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
            priority = event.priority,
            createdAt = event.createdAt,
            updatedAt = event.updatedAt,
        )
    }
}

@JvmInline
@Serializable
value class Reminders(
    val list: List<Reminder> = emptyList(),
) {
    constructor(collection: Collection<Reminder>) : this(collection.toList())

    fun notEmptyOrNull(): Reminders? = if (list.isNotEmpty()) this else null

    fun toVAlarms() = list.map { it.toVAlarm() }

    companion object {
        val Empty = Reminders(emptyList())

        fun of(vararg reminders: Reminder) = Reminders(reminders.toList())
    }
}

@JvmName("vAlarmCollectionToReminders")
fun Collection<VAlarm>?.toReminders(): Reminders {
    val reminders = this?.mapNotNull { it.toReminder() } ?: emptyList()
    return Reminders(reminders)
}

@Serializable
data class Reminder(
    val trigger: ICalendarTrigger,
    val displayText: String? = null,
) {
    constructor(
        offset: Duration,
        related: ICalendarTrigger.Relative.Related = Start,
        displayText: String? = null,
    ) : this(
        trigger = ICalendarTrigger.Relative(offset, related),
        displayText = displayText,
    )

    fun toVAlarm(defaultDisplayText: String? = null): VAlarm = VAlarm.display(
        trigger.toProperty(),
        displayText ?: defaultDisplayText,
    )
}

@JvmName("reminderCollectionToReminders")
fun Collection<Reminder>.toReminders(): Reminders {
    return Reminders(this)
}

fun VAlarm.toReminder(): Reminder? {
    val trigger = trigger?.toICalendarTrigger() ?: return null
    val displayText = description?.value ?: return null
    return Reminder(
        trigger = trigger,
        displayText = displayText,
    )
}
