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
import biweekly.io.TimezoneInfo
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toJavaZoneId
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
    val recurrenceRule: RecurrenceRuleEntity?
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
        override val recurrenceRule: RecurrenceRuleEntity? = null,
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
            recurrenceRuleId = recurrenceRule?.id,
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
            customRecurrenceRule()
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
        override val recurrenceRule: RecurrenceRuleEntity? = null,
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
            recurrenceRuleId = null, // Expected to be stored in CourseEntity
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
            customRecurrenceRule()
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

    fun VEvent.customRecurrenceRule() {
        this@Event.recurrenceRule?.let { entity ->
            setProperty(entity.rule.toProperty())
        }
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
        fun parse(
            vEvent: VEvent,
            timeZoneInfo: TimezoneInfo? = null,
            defaultTimeZone: TimeZone = TimeZone.currentSystemDefault(),
            onNewRecurrenceRule: (
                rule: RecurrenceRuleEntity,
                course: CourseEntity?,
            ) -> CourseEntity? = { _, _ -> null },
            recurrenceEndBound: Instant? = null,
            errors: MutableList<String> = mutableListOf(),
        ): List<Event> {
            val id = vEvent.uid?.value?.let {
                Uuid.parseOrNull(it) ?: run {
                    errors.add("Cannot parse UUID from UID in event: $it")
                    return emptyList()
                }
            } ?: Uuid.generateV7()
            val name = vEvent.summary?.value

            val identifier = name ?: id.toString()

            val semesterProperty = vEvent.getProperty(SemesterProperty::class.java) ?: run {
                errors.add("Cannot parse semester in event: $identifier")
                return emptyList()
            }
            semesterProperty.errors?.let { errors.addAll(it) }
            val semester = semesterProperty.semester ?: run {
                errors.add("Some fields are missing or invalid in semester property in event: $identifier")
                return emptyList()
            }
            val courseProperty = vEvent.getProperty(CourseProperty::class.java)
            courseProperty?.errors?.let { errors.addAll(it) }
            val course = courseProperty?.course.let {
                if (it == null) {
                    if (courseProperty != null) {
                        errors.add("Some fields are missing or invalid in course property in event: $identifier")
                    }
                    return@let null
                }
                if (it.semesterId == SemesterEntity.UnspecifiedSemesterId) {
                    return@let it.copy(
                        semesterId = semester.id,
                    )
                }
                if (it.semesterId != semester.id) {
                    errors.add("Course semester ID ${it.semesterId} does not match event semester ID ${semester.id} in event: $identifier")
                    return emptyList()
                }
                it
            }

            val instructor = vEvent.getProperty(InstructorProperty::class.java)?.value
            val location = vEvent.location?.value
            val color = vEvent.color.toICalendarColor(course?.id, defaultId = semester.id)
            val startDate = vEvent.dateStart?.value ?: run {
                errors.add("Start date not found in event: $identifier")
                return emptyList()
            }
            val startTime = startDate.toInstant().toKotlinInstant()
            val duration = vEvent.duration?.value?.toDuration()
                ?: vEvent.dateEnd?.value?.toInstant()?.toKotlinInstant()?.let { it - startTime }
                ?: run {
                    errors.add("Cannot determine duration in event: $identifier")
                    return emptyList()
                }
            val reminders = vEvent.alarms.toReminders(name, errors)
            val notes = vEvent.description?.value
            val priority = vEvent.priority.toICalendarPriority()
            val now = Clock.System.now()
            val createdAt = vEvent.created?.value?.toInstant()?.toKotlinInstant() ?: now
            val updatedAt = vEvent.lastModified?.value?.toInstant()?.toKotlinInstant() ?: now

            val exceptions = vEvent.exceptionDates?.asSequence()
                ?.flatMap { exDate ->
                    exDate.values.mapNotNull { it.toInstant().toKotlinInstant() }
                }?.toSet() ?: emptySet()

            vEvent.recurrenceRule?.let { rule ->
                val timeZone = timeZoneInfo?.getTimezone(rule)?.timeZone
                    ?: java.util.TimeZone.getTimeZone(defaultTimeZone.toJavaZoneId())
                val entity = RecurrenceRuleEntity(
                    id = id,
                    rule = rule.value.toICalendarRecurrenceRule(errors) ?: run {
                        errors.add("Cannot parse recurrence rule in event: $identifier")
                        return emptyList()
                    },
                    startTime = startDate.toInstant().toKotlinInstant(),
                    duration = duration,
                    exceptions = exceptions,
                )
                val newCourse = onNewRecurrenceRule(entity, course) ?: course

                val isInfinite = entity.rule.count == null && entity.rule.until == null
                if (isInfinite && semester.id == SemesterEntity.DefaultSemesterId) {
                    errors.add("Infinite recurrence rule found in default semester in event: $identifier")
                    return emptyList()
                }
                val semesterEndTime = semester.endDate.atStartOfDayIn(semester.timeZone)
                val semesterEndDate = Date.from(semesterEndTime.toJavaInstant())
                val iterator = vEvent.getDateIterator(timeZone)
                val events = mutableListOf<Event>()
                while (iterator.hasNext()) {
                    val occurrenceStartDate = iterator.next()

                    recurrenceEndBound?.let { instant ->
                        val date = Date.from(instant.toJavaInstant())
                        if (occurrenceStartDate.after(date)) {
                            errors.add("Occurrence exceeds recurrence end bound $recurrenceEndBound in event: $identifier")
                            break
                        }
                    }

                    if (occurrenceStartDate.after(semesterEndDate)) {
                        errors.add("Occurrence exceeds semester end date $semesterEndTime in event: $identifier")
                        break
                    }

                    val occurrenceStartTime = occurrenceStartDate.toInstant().toKotlinInstant()
                    val occurrenceEndTime = occurrenceStartTime + duration
                    if (newCourse != null) {
                        events.add(
                            Course(
                                semester = semester,
                                course = newCourse,
                                recurrenceRule = entity,
                                name = name ?: newCourse.name,
                                instructor = instructor ?: newCourse.instructor,
                                location = location ?: newCourse.location,
                                color = color,
                                startTime = occurrenceStartTime,
                                endTime = occurrenceEndTime,
                                reminders = reminders,
                                notes = notes,
                                priority = priority,
                                createdAt = createdAt,
                                updatedAt = updatedAt,
                            ),
                        )
                    } else {
                        if (name == null || location == null) {
                            errors.add("Name or location is required for normal event: $identifier at $occurrenceStartTime")
                            continue
                        }
                        events.add(
                            Normal(
                                semester = semester,
                                recurrenceRule = entity,
                                name = name,
                                location = location,
                                color = color,
                                startTime = occurrenceStartTime,
                                endTime = occurrenceEndTime,
                                reminders = reminders,
                                notes = notes,
                                priority = priority,
                                createdAt = createdAt,
                                updatedAt = updatedAt,
                            ),
                        )
                    }
                }
                return events
            }

            if (startTime in exceptions) {
                errors.add("Event is an exception: $identifier at $startTime")
                return emptyList()
            }

            val endTime = startTime + duration
            return if (course != null) {
                listOf(
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
                        priority = priority,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                    ),
                )
            } else {
                if (name == null || location == null) {
                    errors.add("Name or location is required for normal event: $identifier")
                    return emptyList()
                }
                listOf(
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
                        priority = priority,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                    ),
                )
            }
        }
    }
}

fun CombinedEvent.toEvent(
    recurrenceRule: RecurrenceRuleEntity? = null,
): Event {
    return if (course != null) {
        Event.Course(
            id = event.id,
            semester = semester,
            course = course,
            recurrenceRule = recurrenceRule,
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
            recurrenceRule = recurrenceRule,
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

fun VEvent.parse(
    timeZoneInfo: TimezoneInfo? = null,
    defaultTimeZone: TimeZone = TimeZone.currentSystemDefault(),
    onNewRecurrenceRule: (
        rule: RecurrenceRuleEntity,
        course: CourseEntity?,
    ) -> CourseEntity? = { _, _ -> null },
    recurrenceEndBound: Instant? = null,
    errors: MutableList<String> = mutableListOf(),
) = Event.parse(
    vEvent = this,
    timeZoneInfo = timeZoneInfo,
    defaultTimeZone = defaultTimeZone,
    onNewRecurrenceRule = onNewRecurrenceRule,
    recurrenceEndBound = recurrenceEndBound,
    errors = errors,
)

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
fun Collection<VAlarm>?.toReminders(
    defaultDisplayText: String? = null,
    errors: MutableList<String> = mutableListOf(),
): Reminders {
    val reminders = this?.mapNotNull { it.toReminder(defaultDisplayText, errors) } ?: emptyList()
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

fun VAlarm.toReminder(
    defaultDisplayText: String? = null,
    errors: MutableList<String> = mutableListOf(),
): Reminder? {
    val triggerProperty = trigger ?: run {
        errors += "VAlarm does not have a trigger"
        return null
    }
    val trigger = triggerProperty.toICalendarTrigger(errors) ?: run {
        errors += "Cannot parse VAlarm trigger: $triggerProperty"
        return null
    }
    val displayText = description?.value
        ?: defaultDisplayText
        ?: run {
            errors += "VAlarm does not have a description"
            return null
        }
    return Reminder(
        trigger = trigger,
        displayText = displayText,
    )
}
