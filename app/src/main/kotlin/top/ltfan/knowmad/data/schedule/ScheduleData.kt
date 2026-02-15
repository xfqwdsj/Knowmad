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

import android.content.res.Resources
import biweekly.ICalendar
import biweekly.component.VAlarm
import biweekly.component.VEvent
import biweekly.io.TimezoneInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toJavaZoneId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.R
import top.ltfan.knowmad.util.Logger
import top.ltfan.omnical.icalendar.ICalendarColor
import top.ltfan.omnical.icalendar.ICalendarPriority
import top.ltfan.omnical.icalendar.ICalendarTrigger
import top.ltfan.omnical.icalendar.biweekly.format
import top.ltfan.omnical.icalendar.biweekly.toBiweeklyValue
import top.ltfan.omnical.icalendar.biweekly.toKotlinDuration
import top.ltfan.omnical.icalendar.biweekly.toOmnicalValue
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
        val eventName: String? = null,
        val eventInstructor: String? = null,
        val eventLocation: String? = null,
        override val color: ICalendarColor,
        override val startTime: Instant,
        override val endTime: Instant,
        override val reminders: Reminders = Empty,
        override val notes: String? = null,
        override val priority: ICalendarPriority = None,
        override val createdAt: Instant = Clock.System.now(),
        override val updatedAt: Instant = createdAt,
    ) : Event {
        override val name = eventName ?: course.name
        val instructor = eventInstructor ?: course.instructor
        override val location = eventLocation ?: course.location

        override fun toEntity() = EventEntity(
            id = id,
            semesterId = semester.id,
            courseId = course.id,
            recurrenceRuleId = null, // Expected to be stored in CourseEntity
            name = eventName,
            instructor = eventInstructor,
            location = eventLocation,
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

        override fun VEvent.summary() {
            setSummary(eventName)
        }

        fun VEvent.instructor() {
            eventInstructor?.let { eventInstructor ->
                addProperty(InstructorProperty(eventInstructor))
            }
        }

        override fun VEvent.location() {
            setLocation(eventLocation)
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
            setProperty(entity.toProperty())
        }
    }

    fun VEvent.summary() {
        setSummary(name)
    }

    fun VEvent.location() {
        setLocation(this@Event.location)
    }

    fun VEvent.color() {
        color = this@Event.color.toBiweeklyValue()
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
        priority = this@Event.priority.toBiweeklyValue()
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
            errors: MutableList<String>? = null,
        ): List<Event> {
            val id = vEvent.uid?.value?.let {
                Uuid.parseOrNull(it) ?: run {
                    errors?.add("Cannot parse UUID from UID in event: $it")
                    return emptyList()
                }
            } ?: Uuid.generateV7()
            val name = vEvent.summary?.value

            val identifier = name ?: id.toString()

            val semesterProperty = vEvent.getProperty(SemesterProperty::class.java) ?: run {
                errors?.add("Cannot parse semester in event: $identifier")
                return emptyList()
            }
            semesterProperty.errors?.let { errors?.addAll(it) }
            val semester = semesterProperty.semester ?: run {
                errors?.add("Some fields are missing or invalid in semester property ${SemesterProperty.PROPERTY_NAME} in event: $identifier")
                return emptyList()
            }
            val courseProperty = vEvent.getProperty(CourseProperty::class.java)
            courseProperty?.errors?.let { errors?.addAll(it) }
            val course = courseProperty?.course.let {
                if (it == null) {
                    if (courseProperty != null) {
                        errors?.add("Some fields are missing or invalid in course property ${CourseProperty.PROPERTY_NAME} in event: $identifier")
                    }
                    return@let null
                }
                if (it.semesterId == SemesterEntity.UnspecifiedSemesterId) {
                    return@let it.copy(
                        semesterId = semester.id,
                    )
                }
                if (it.semesterId != semester.id) {
                    errors?.add("Course semester ID ${it.semesterId} does not match event semester ID ${semester.id} in event: $identifier")
                    return emptyList()
                }
                it
            }

            val instructor = vEvent.getProperty(InstructorProperty::class.java)?.value
            val location = vEvent.location?.value
            val color = vEvent.color.convertOrDefault(course?.id, defaultId = semester.id)
            val startDate = vEvent.dateStart?.value ?: run {
                errors?.add("Start date not found in event: $identifier")
                return emptyList()
            }
            val startTime = startDate.toInstant().toKotlinInstant()
            val duration = vEvent.duration?.value?.toKotlinDuration()
                ?: vEvent.dateEnd?.value?.toInstant()?.toKotlinInstant()?.let { it - startTime }
                ?: run {
                    errors?.add("Cannot determine duration in event: $identifier")
                    return emptyList()
                }
            val reminders = vEvent.alarms.toReminders(name, errors)
            val notes = vEvent.description?.value
            val priority = vEvent.priority?.toOmnicalValue() ?: None
            val now = Clock.System.now()
            val createdAt = vEvent.created?.value?.toInstant()?.toKotlinInstant() ?: now
            val updatedAt = vEvent.lastModified?.value?.toInstant()?.toKotlinInstant() ?: now

            val exceptions = vEvent.exceptionDates?.asSequence()
                ?.flatMap { exDate ->
                    exDate.values.mapNotNull { it.toInstant().toKotlinInstant() }
                }?.toSet() ?: emptySet()

            val recurrenceRuleProperty =
                vEvent.getProperty(KnowmadRecurrenceRuleProperty::class.java)
            recurrenceRuleProperty?.errors?.let { errors?.addAll(it) }
            val recurrenceRuleEntity =
                recurrenceRuleProperty?.recurrenceRule ?: vEvent.recurrenceRule?.let { rule ->
                    val timeZone = timeZoneInfo?.getTimezone(rule)?.timeZone
                        ?: java.util.TimeZone.getTimeZone(defaultTimeZone.toJavaZoneId())
                    val entity = RecurrenceRuleEntity(
                        id = id,
                        rule = rule.value.toOmnicalValue(errors) ?: run {
                            errors?.add("Cannot parse recurrence rule in event: $identifier")
                            return emptyList()
                        },
                        startTime = startDate.toInstant().toKotlinInstant(),
                        duration = duration,
                        exceptions = exceptions,
                    )
                    val newCourse = onNewRecurrenceRule(entity, course) ?: course

                    val isInfinite = entity.rule.count == null && entity.rule.until == null
                    if (isInfinite && semester.id == SemesterEntity.DefaultSemesterId) {
                        errors?.add("Infinite recurrence rule found in default semester in event: $identifier")
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
                                errors?.add("Occurrence exceeds recurrence end bound $recurrenceEndBound in event: $identifier")
                                break
                            }
                        }

                        if (occurrenceStartDate.after(semesterEndDate)) {
                            errors?.add("Occurrence exceeds semester end date $semesterEndTime in event: $identifier")
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
                                    eventName = name,
                                    eventInstructor = instructor,
                                    eventLocation = location,
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
                                errors?.add("Name or location is required for normal event: $identifier at $occurrenceStartTime")
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
                errors?.add("Event is an exception: $identifier at $startTime")
                return emptyList()
            }

            val newCourse = recurrenceRuleEntity?.let { onNewRecurrenceRule(it, course) } ?: course

            val endTime = startTime + duration
            return if (newCourse != null) {
                listOf(
                    Course(
                        id = id,
                        semester = semester,
                        course = newCourse,
                        recurrenceRule = recurrenceRuleEntity,
                        eventName = name,
                        eventInstructor = instructor,
                        eventLocation = location,
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
                    errors?.add("Name or location is required for normal event: $identifier")
                    return emptyList()
                }
                listOf(
                    Normal(
                        id = id,
                        semester = semester,
                        recurrenceRule = recurrenceRuleEntity,
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
            eventName = event.name,
            eventInstructor = event.instructor,
            eventLocation = event.location,
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
    errors: MutableList<String>? = null,
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

    inline fun toVAlarms(defaultDisplayText: (Reminder) -> String? = { null }) =
        list.map { it.toVAlarm(defaultDisplayText(it)) }

    companion object {
        val Empty = Reminders(emptyList())

        fun of(vararg reminders: Reminder) = Reminders(reminders.toList())
    }
}

@JvmName("vAlarmCollectionToReminders")
fun Collection<VAlarm>?.toReminders(
    defaultDisplayText: String? = null,
    errors: MutableList<String>? = null,
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
        trigger.toBiweeklyValue(),
        displayText ?: defaultDisplayText,
    )
}

@JvmName("reminderCollectionToReminders")
fun Collection<Reminder>.toReminders(): Reminders {
    return Reminders(this)
}

fun VAlarm.toReminder(
    defaultDisplayText: String? = null,
    errors: MutableList<String>? = null,
): Reminder? {
    val triggerProperty = trigger ?: run {
        errors?.add("VAlarm does not have a trigger")
        return null
    }
    val trigger = triggerProperty.toOmnicalValue(errors) ?: run {
        errors?.add("Cannot parse VAlarm trigger: $triggerProperty")
        return null
    }
    val displayText = description?.value
        ?: defaultDisplayText
        ?: run {
            errors?.add("VAlarm does not have a description")
            return null
        }
    return Reminder(
        trigger = trigger,
        displayText = displayText,
    )
}

suspend inline fun ScheduleDao.importFromICalendar(
    iCalendar: ICalendar,
    resources: Resources,
    onQueryFailed: (Throwable) -> Nothing = { throw it },
    onSemesterShadowed: (existing: SemesterEntity, new: SemesterEntity) -> SemesterEntity = { _, new -> new },
    onSemesterConflict: (existing: SemesterEntity, new: SemesterEntity) -> ScheduleImportOnConflict = { _, _ -> Skip },
    onRecurrenceRuleShadowed: (existing: RecurrenceRuleEntity, new: RecurrenceRuleEntity) -> RecurrenceRuleEntity = { _, new -> new },
    onRecurrenceRuleConflict: (existing: RecurrenceRuleEntity, new: RecurrenceRuleEntity) -> ScheduleImportOnConflict = { _, _ -> Skip },
    onCourseShadowed: (existing: CourseEntity, new: CourseEntity) -> CourseEntity = { _, new -> new },
    onCourseConflict: (existing: CourseEntity, new: CourseEntity) -> ScheduleImportOnConflict = { _, _ -> Skip },
    errors: MutableList<String>? = null,
): List<Event>? {
    val logger = Logger("ImportICalendar")

    val events = iCalendar.parse(
        onNewRecurrenceRule = { rule, course -> course?.copy(recurrenceRuleId = rule.id) },
        errors = errors,
    ).toMutableList()

    if (events.isEmpty()) return null

    run {
        val semestersToInsert = mutableMapOf<Uuid, SemesterEntity>()
        val semesterIds = hashSetOf<Uuid>()
        val eventsSemesterIndex = mutableMapOf<Uuid, MutableList<Int>>()

        events.asSequence()
            .onEachIndexed { index, event ->
                eventsSemesterIndex
                    .getOrPut(event.semester.id) { mutableListOf() }
                    .add(index)
            }
            .map { it.semester }
            .forEach { semester ->
                semestersToInsert[semester.id]?.let { existing ->
                    if (existing == semester) return@let
                    semestersToInsert[semester.id] = onSemesterShadowed(existing, semester)
                } ?: run {
                    semestersToInsert[semester.id] = semester
                    semesterIds += semester.id
                }
            }

        withContext(Dispatchers.IO) {
            runCatching { getAllSemestersByIds(semesterIds.toList()) }
        }.onFailure { logger.error(it) { "Failed to query existing semesters from database" } }
            .getOrElse {
                onQueryFailed(
                    Throwable(
                        "Failed to query existing semesters from database",
                        it,
                    ),
                )
            }
            .forEach { existing ->
                errors?.removeAll { it.contains(existing.id.toString()) }
                val semester = semestersToInsert[existing.id]?.let { new ->
                    when (onSemesterConflict(existing, new)) {
                        Replace -> {
                            withContext(Dispatchers.IO) {
                                runCatching { updateSemester(new) }
                            }.onFailure {
                                logger.error(it) { "Failed to update semester ${new.name}" }
                                onQueryFailed(
                                    Throwable(
                                        "Failed to update semester ${new.name}",
                                        it,
                                    ),
                                )
                            }
                            new
                        }

                        Skip -> null
                    }
                } ?: existing
                semestersToInsert -= semester.id
                eventsSemesterIndex[semester.id]?.forEach { index ->
                    events[index] = when (val event = events[index]) {
                        is Course -> event.copy(semester = semester)
                        is Normal -> event.copy(semester = semester)
                    }
                }
            }

        withContext(Dispatchers.IO) {
            runCatching { insertAllSemesters(semestersToInsert.values.toList()) }
        }.onFailure { logger.error(it) { "Failed to insert semesters" } }
            .getOrElse { onQueryFailed(Throwable("Failed to insert semesters", it)) }
            .asSequence()
            .zip(semestersToInsert.asSequence())
            .forEach { (result, entry) ->
                val (_, semester) = entry
                if (result < 0L) {
                    errors?.add(
                        resources.getString(
                            R.string.schedule_import_from_icalendar_error_semester_insertion_failed,
                            semester.name,
                        ),
                    )
                    semesterIds -= semester.id
                }
            }

        events.retainAll { it.semester.id in semesterIds }
    }

    if (events.isEmpty()) return null

    run {
        val recurrenceRulesToInsert = mutableMapOf<Uuid, RecurrenceRuleEntity>()
        val recurrenceRuleIds = mutableSetOf<Uuid>()
        val eventsRecurrenceRuleIndex = mutableMapOf<Uuid, MutableList<Int>>()

        events.asSequence()
            .onEachIndexed { index, event ->
                val ruleId = event.recurrenceRule?.id ?: return@onEachIndexed
                eventsRecurrenceRuleIndex
                    .getOrPut(ruleId) { mutableListOf() }
                    .add(index)
            }
            .mapNotNull { it.recurrenceRule }
            .forEach { rule ->
                recurrenceRulesToInsert[rule.id]?.let { existing ->
                    if (existing == rule) return@let
                    recurrenceRulesToInsert[rule.id] = onRecurrenceRuleShadowed(existing, rule)
                } ?: run {
                    recurrenceRulesToInsert[rule.id] = rule
                    recurrenceRuleIds += rule.id
                }
            }

        withContext(Dispatchers.IO) {
            runCatching { getAllRecurrenceRulesByIds(recurrenceRuleIds.toList()) }
        }.onFailure { logger.error(it) { "Failed to query existing recurrence rules from database" } }
            .getOrElse {
                onQueryFailed(
                    Throwable(
                        "Failed to query existing recurrence rules from database",
                        it,
                    ),
                )
            }
            .forEach { existing ->
                errors?.removeAll { it.contains(existing.id.toString()) }
                val rule = recurrenceRulesToInsert[existing.id]?.let { new ->
                    when (onRecurrenceRuleConflict(existing, new)) {
                        Replace -> {
                            withContext(Dispatchers.IO) {
                                runCatching { updateRecurrenceRule(new) }
                            }.onFailure {
                                logger.error(it) { "Failed to update recurrence rule ${new.rule.format()}" }
                                onQueryFailed(
                                    Throwable(
                                        "Failed to update recurrence rule ${new.rule.format()}",
                                        it,
                                    ),
                                )
                            }
                            new
                        }

                        Skip -> null
                    }
                } ?: existing
                recurrenceRulesToInsert -= rule.id
                eventsRecurrenceRuleIndex[rule.id]?.forEach { index ->
                    events[index] = when (val event = events[index]) {
                        is Course -> event.copy(recurrenceRule = rule)
                        is Normal -> event.copy(recurrenceRule = rule)
                    }
                }
            }

        withContext(Dispatchers.IO) {
            runCatching { insertAllRecurrenceRules(recurrenceRulesToInsert.values.toList()) }
        }.onFailure { logger.error(it) { "Failed to insert recurrence rules" } }
            .getOrElse { onQueryFailed(Throwable("Failed to insert recurrence rules", it)) }
            .asSequence()
            .zip(recurrenceRulesToInsert.asSequence())
            .forEach { (result, entry) ->
                val (_, rule) = entry
                if (result < 0L) {
                    errors?.add(
                        resources.getString(
                            R.string.schedule_import_from_icalendar_error_recurrence_rule_insertion_failed,
                            rule.rule.format(),
                        ),
                    )
                    recurrenceRuleIds -= rule.id
                }
            }

        events.retainAll { event ->
            val ruleId = event.recurrenceRule?.id ?: return@retainAll true
            ruleId in recurrenceRuleIds
        }
    }

    if (events.isEmpty()) return null

    run {
        val coursesToInsert = mutableMapOf<Uuid, CourseEntity>()
        val courseIds = hashSetOf<Uuid>()
        val eventsCourseIndex = mutableMapOf<Uuid, MutableList<Int>>()

        events.asSequence()
            .onEachIndexed { index, event ->
                if (event !is Course) return@onEachIndexed
                eventsCourseIndex
                    .getOrPut(event.course.id) { mutableListOf() }
                    .add(index)
            }
            .filterIsInstance<Event.Course>()
            .map { it.course }
            .forEach { course ->
                coursesToInsert[course.id]?.let { existing ->
                    if (existing == course) return@let
                    coursesToInsert[course.id] = onCourseShadowed(existing, course)
                } ?: run {
                    coursesToInsert[course.id] = course
                    courseIds += course.id
                }
            }

        withContext(Dispatchers.IO) {
            runCatching { getAllCoursesByIds(courseIds.toList()) }
        }.onFailure { logger.error(it) { "Failed to query existing courses from database" } }
            .getOrElse {
                onQueryFailed(
                    Throwable(
                        "Failed to query existing courses from database",
                        it,
                    ),
                )
            }
            .forEach { (existingCourse, existingSemester) ->
                errors?.removeAll { it.contains(existingCourse.id.toString()) }
                val courseToInsert = coursesToInsert[existingCourse.id] ?: return@forEach
                coursesToInsert -= existingCourse.id
                if (courseToInsert.semesterId != existingSemester.id) {
                    errors?.add(
                        resources.getString(
                            R.string.schedule_import_from_icalendar_error_course_semester_mismatch,
                            existingCourse.name,
                        ),
                    )
                    courseIds -= existingCourse.id
                    return@forEach
                }
                val course = courseToInsert.let { new ->
                    when (onCourseConflict(existingCourse, new)) {
                        Replace -> {
                            withContext(Dispatchers.IO) {
                                runCatching { updateCourse(new) }
                            }.onFailure {
                                logger.error(it) { "Failed to update course ${new.name}" }
                                onQueryFailed(Throwable("Failed to update course ${new.name}", it))
                            }
                            new
                        }

                        Skip -> null
                    }
                } ?: existingCourse
                eventsCourseIndex[course.id]?.forEach { index ->
                    events[index] = when (val event = events[index]) {
                        is Course -> event.copy(course = course)
                        else -> event
                    }
                }
            }

        withContext(Dispatchers.IO) {
            runCatching { insertAllCourses(coursesToInsert.values.toList()) }
        }.onFailure { logger.error(it) { "Failed to insert courses" } }
            .getOrElse { onQueryFailed(Throwable("Failed to insert courses", it)) }
            .asSequence()
            .zip(coursesToInsert.asSequence())
            .forEach { (result, entry) ->
                val (_, course) = entry
                if (result < 0L) {
                    errors?.add(
                        resources.getString(
                            R.string.schedule_import_from_icalendar_error_course_insertion_failed,
                            course.name,
                        ),
                    )
                    courseIds -= course.id
                }
            }

        events.retainAll { event ->
            if (event !is Course) return@retainAll true
            event.course.id in courseIds
        }
    }

    if (events.isEmpty()) return null

    val eventsInsertionResults = withContext(Dispatchers.IO) {
        runCatching { insertAllEvents(events.map { it.toEntity() }) }
    }.onFailure { logger.error(it) { "Failed to insert events" } }
        .getOrElse { onQueryFailed(Throwable("Failed to insert events", it)) }

    for (i in eventsInsertionResults.indices.reversed()) {
        if (eventsInsertionResults[i] < 0L) {
            errors?.add(
                resources.getString(
                    R.string.schedule_import_from_icalendar_error_event_insertion_failed,
                    events[i].name,
                ),
            )
            events.removeAt(i)
        }
    }

    if (events.isEmpty()) return null

    return events
}

enum class ScheduleImportOnConflict {
    Skip, Replace,
}
