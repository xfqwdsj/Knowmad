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
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import biweekly.ICalendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.FtsDao
import top.ltfan.knowmad.util.Logger
import top.ltfan.omnical.icalendar.biweekly.format
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Dao
interface ScheduleDao : FtsDao {
    @Insert
    suspend fun insertSemester(semester: SemesterEntity): Long

    @Insert
    suspend fun insertAllSemesters(semesters: List<SemesterEntity>): List<Long>

    @Transaction
    suspend fun checkOrCreateDefaultSemester(resources: Resources) {
        val existing = getSemesterById(SemesterEntity.DefaultSemesterId)
        if (existing == null) {
            val defaultSemester = SemesterEntity.createDefault(resources)
            insertSemester(defaultSemester)
        }
    }

    @Insert
    suspend fun insertRecurrenceRule(rule: RecurrenceRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllRecurrenceRules(rules: List<RecurrenceRuleEntity>): List<Long>

    @Insert
    suspend fun insertCourse(course: CourseEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllCourses(courses: List<CourseEntity>): List<Long>

    @Insert
    suspend fun insertEvent(event: EventEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllEvents(events: List<EventEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllEventTombstones(tombstones: List<EventTombstoneEntity>): List<Long>

    @Delete
    suspend fun deleteSemesterInternal(semester: SemesterEntity): Int

    @Transaction
    suspend fun deleteSemester(semester: SemesterEntity): Int {
        val eventIds = getAllEventIdsBySemester(semester.id)
        val tombstones = eventIds.flatMap { eventId ->
            EventDeletionTarget.entries.map { target ->
                EventTombstoneEntity(
                    id = eventId,
                    target = target,
                )
            }
        }
        insertAllEventTombstones(tombstones)
        return deleteSemesterInternal(semester)
    }

    @Delete
    suspend fun deleteRecurrenceRuleInternal(rule: RecurrenceRuleEntity): Int

    @Transaction
    suspend fun deleteRecurrenceRule(rule: RecurrenceRuleEntity): Int {
        val courseIds = getAllCourseIdsByRecurrenceRule(rule.id)
        val eventIds = getAllEventIdsByRecurrenceRule(rule.id) +
                getAllEventIdsByCourses(courseIds)
        val tombstones = eventIds.flatMap { eventId ->
            EventDeletionTarget.entries.map { target ->
                EventTombstoneEntity(
                    id = eventId,
                    target = target,
                )
            }
        }
        insertAllEventTombstones(tombstones)
        return deleteRecurrenceRuleInternal(rule)
    }

    @Delete
    suspend fun deleteCourseInternal(course: CourseEntity): Int

    @Transaction
    suspend fun deleteCourse(course: CourseEntity): Int {
        val eventIds = getAllEventIdsByCourse(course.id)
        val tombstones = eventIds.flatMap { eventId ->
            EventDeletionTarget.entries.map { target ->
                EventTombstoneEntity(
                    id = eventId,
                    target = target,
                )
            }
        }
        insertAllEventTombstones(tombstones)
        return deleteCourseInternal(course)
    }

    @Delete
    suspend fun deleteEventInternal(event: EventEntity): Int

    @Transaction
    suspend fun deleteEvent(event: EventEntity): Int {
        val tombstones = EventDeletionTarget.entries.map { target ->
            EventTombstoneEntity(
                id = event.id,
                target = target,
            )
        }
        insertAllEventTombstones(tombstones)
        return deleteEventInternal(event)
    }

    @Query(
        """
            DELETE FROM EventTombstoneEntity
            WHERE id IN (:ids) AND target = :target
        """,
    )
    suspend fun deleteEventTombstonesForTarget(
        ids: List<Uuid>,
        target: EventDeletionTarget,
    )

    @Update
    suspend fun updateSemester(semester: SemesterEntity): Int

    @Update
    suspend fun updateRecurrenceRule(rule: RecurrenceRuleEntity): Int

    @Update
    suspend fun updateCourse(course: CourseEntity): Int

    @Update
    suspend fun updateEventWithoutInstant(event: EventEntity): Int

    @Transaction
    suspend fun updateEvent(event: EventEntity): Int {
        val updatedEvent = event.copy(updatedAt = Clock.System.now())
        return updateEventWithoutInstant(updatedEvent)
    }

    @Query("SELECT * FROM SemesterEntity ORDER BY startDate ASC")
    suspend fun getAllSemesters(): List<SemesterEntity>

    @Query("SELECT * FROM SemesterEntity ORDER BY startDate ASC")
    fun getAllSemestersFlow(): Flow<List<SemesterEntity>>

    @Query("SELECT * FROM SemesterEntity WHERE id = :id")
    suspend fun getSemesterById(id: Uuid): SemesterEntity?

    @Query("SELECT * FROM SemesterEntity WHERE id in (:ids) ORDER BY startDate ASC")
    suspend fun getAllSemestersByIds(ids: List<Uuid>): List<SemesterEntity>

    @Query(
        """
            SELECT * FROM SemesterEntity
            WHERE startDate <= :endDate AND endDate >= :startDate
            ORDER BY startDate ASC
    """,
    )
    suspend fun getSemestersInRange(startDate: LocalDate, endDate: LocalDate): List<SemesterEntity>

    @Transaction
    @Query(
        """
            SELECT SemesterEntity.* FROM SemesterEntity
            JOIN SemesterFtsEntity ON SemesterEntity.rowid = SemesterFtsEntity.rowid
            WHERE SemesterFtsEntity MATCH :query
        """,
    )
    suspend fun searchSemestersInternal(query: String): List<SemesterEntity>

    @Transaction
    suspend fun searchSemesters(query: String): List<SemesterEntity> {
        val sanitized = query.sanitizeForFts().ifBlank { return emptyList() }
        return searchSemestersInternal(sanitized)
    }

    @Query("SELECT * FROM RecurrenceRuleEntity WHERE id = :id")
    suspend fun getRecurrenceRuleById(id: Uuid): RecurrenceRuleEntity?

    @Query("SELECT * FROM RecurrenceRuleEntity WHERE id IN (:ids)")
    suspend fun getAllRecurrenceRulesByIds(ids: List<Uuid>): List<RecurrenceRuleEntity>

    @Transaction
    suspend fun getRecurrenceRuleByCourse(course: CourseEntity): RecurrenceRuleEntity? {
        val ruleId = course.recurrenceRuleId ?: return null
        return getRecurrenceRuleById(ruleId)
    }

    @Transaction
    suspend fun getRecurrenceRuleByCombinedEvent(combinedEvent: CombinedEvent): RecurrenceRuleEntity? {
        val ruleId = combinedEvent.course?.recurrenceRuleId ?: combinedEvent.event.recurrenceRuleId
        return getRecurrenceRuleById(ruleId ?: return null)
    }

    @Transaction
    @Query("SELECT * FROM CourseEntity WHERE id = :id")
    suspend fun getCourseById(id: Uuid): CombinedCourse?

    @Transaction
    @Query("SELECT * FROM CourseEntity WHERE id IN (:ids)")
    suspend fun getAllCoursesByIds(ids: List<Uuid>): List<CombinedCourse>

    @Query(
        """
            SELECT id FROM CourseEntity
            WHERE recurrenceRuleId = :recurrenceRuleId
        """,
    )
    suspend fun getAllCourseIdsByRecurrenceRule(recurrenceRuleId: Uuid): List<Uuid>

    @Query("SELECT * FROM CourseEntity WHERE id = :id")
    suspend fun getCourseEntityById(id: Uuid): CourseEntity?

    @Transaction
    @Query(
        """
            SELECT * FROM CourseEntity
            WHERE semesterId = :semesterId
            ORDER BY name ASC
    """,
    )
    suspend fun getAllCoursesInSemester(semesterId: Uuid): List<CombinedCourse>

    @Transaction
    suspend fun getAllCourses(): List<CombinedCourse> {
        return getAllSemesters().flatMap { semester ->
            getAllCoursesInSemester(semester.id)
        }
    }

    @Transaction
    @Query(
        """
            SELECT CourseEntity.* FROM CourseEntity
            JOIN CourseFtsEntity ON CourseEntity.rowid = CourseFtsEntity.rowid
            WHERE CourseFtsEntity MATCH :query
    """,
    )
    suspend fun searchCoursesInternal(query: String): List<CombinedCourse>

    @Transaction
    suspend fun searchCourses(query: String): List<CombinedCourse> {
        val sanitized = query.sanitizeForFts().ifBlank { return emptyList() }
        return searchCoursesInternal(sanitized)
    }

    @Transaction
    @Query("SELECT * FROM EventEntity WHERE id = :id")
    suspend fun getEventByIdInternal(id: Uuid): CombinedEvent?

    @Transaction
    suspend fun getEventById(id: Uuid): Event? {
        return getEventByIdInternal(id)?.let {
            it.toEvent(getRecurrenceRuleByCombinedEvent(it))
        }
    }

    @Transaction
    @Query(
        """
            SELECT * FROM EventEntity
            WHERE updatedAt > :lastUpdate
            ORDER BY
                startTime ASC,
                priority ASC,
                endTime DESC,
                createdAt ASC
    """,
    )
    suspend fun getAllCombinedEventsAfter(lastUpdate: Instant): List<CombinedEvent>

    @Transaction
    suspend fun getAllEventsForSyncAfter(
        lastUpdate: Instant,
        target: EventDeletionTarget,
    ): Pair<List<Event>, List<EventTombstoneEntity>> {
        return getAllCombinedEventsAfter(lastUpdate).map { it.toEvent() } to
                getAllEventTombstonesByTarget(target)
    }

    @Query("SELECT * FROM EventEntity WHERE id = :id")
    suspend fun getEventEntityById(id: Uuid): EventEntity?

    @Transaction
    @Query(
        """
            SELECT * FROM EventEntity
            WHERE startTime <= :endTime AND endTime >= :startTime
            ORDER BY
                startTime ASC,
                priority ASC,
                endTime DESC,
                createdAt ASC
    """,
    )
    suspend fun getOriginalEventsInRange(
        startTime: Instant,
        endTime: Instant,
    ): List<CombinedEvent>

    @Transaction
    suspend fun getEventsInRange(startTime: Instant, endTime: Instant): List<Event> {
        return getOriginalEventsInRange(startTime, endTime).map {
            it.toEvent(getRecurrenceRuleByCombinedEvent(it))
        }
    }

    @Transaction
    @Query(
        """
            SELECT * FROM EventEntity
            WHERE startTime <= :endTime AND endTime >= :startTime
            ORDER BY
                startTime ASC,
                priority ASC,
                endTime DESC,
                createdAt ASC
    """,
    )
    fun getOriginalEventsFlowInRange(
        startTime: Instant,
        endTime: Instant,
    ): Flow<List<CombinedEvent>>

    fun getEventsFlowInRange(startTime: Instant, endTime: Instant): Flow<List<Event>> {
        return getOriginalEventsFlowInRange(startTime, endTime).map { list ->
            list.map { it.toEvent(getRecurrenceRuleByCombinedEvent(it)) }
        }
    }

    @Query(
        """
            SELECT id FROM EventEntity
            WHERE semesterId = :semesterId
        """,
    )
    suspend fun getAllEventIdsBySemester(semesterId: Uuid): List<Uuid>

    @Query(
        """
            SELECT id FROM EventEntity
            WHERE recurrenceRuleId = :recurrenceRuleId
        """,
    )
    suspend fun getAllEventIdsByRecurrenceRule(recurrenceRuleId: Uuid): List<Uuid>

    @Query(
        """
            SELECT id FROM EventEntity
            WHERE courseId = :courseId
        """,
    )
    suspend fun getAllEventIdsByCourse(courseId: Uuid): List<Uuid>

    @Query(
        """
            SELECT id FROM EventEntity
            WHERE courseId IN (:courseIds)
        """,
    )
    suspend fun getAllEventIdsByCourses(courseIds: List<Uuid>): List<Uuid>

    @Transaction
    @Query(
        """
            SELECT * FROM EventEntity
            WHERE semesterId = :semesterId
            ORDER BY
                startTime ASC,
                priority ASC,
                endTime DESC,
                createdAt ASC
    """,
    )
    suspend fun getAllOriginalEventsBySemester(semesterId: Uuid): List<CombinedEvent>

    @Transaction
    suspend fun getAllEventsBySemester(semesterId: Uuid): List<Event> {
        return getAllOriginalEventsBySemester(semesterId).map {
            it.toEvent(getRecurrenceRuleByCombinedEvent(it))
        }
    }

    @Transaction
    @Query(
        """
            SELECT * FROM EventEntity
            WHERE courseId = :courseId
            ORDER BY
                startTime ASC,
                priority ASC,
                endTime DESC,
                createdAt ASC
    """,
    )
    suspend fun getAllOriginalEventsByCourse(courseId: Uuid): List<CombinedEvent>

    @Transaction
    suspend fun getAllEventsByCourse(courseId: Uuid): List<Event> {
        return getAllOriginalEventsByCourse(courseId).map {
            it.toEvent(getRecurrenceRuleByCombinedEvent(it))
        }
    }

    @Transaction
    @Query(
        """
            SELECT * FROM EventEntity
            WHERE courseId IN (:courseIds)
            ORDER BY
                startTime ASC,
                priority ASC,
                endTime DESC,
                createdAt ASC
    """,
    )
    suspend fun getAllOriginalEventsByCourses(courseIds: List<Uuid>): List<CombinedEvent>

    @Transaction
    suspend fun getAllEventsByCourses(courseIds: List<Uuid>): List<Event> {
        return getAllOriginalEventsByCourses(courseIds).map {
            it.toEvent(getRecurrenceRuleByCombinedEvent(it))
        }
    }

    @Transaction
    @Query(
        """
            SELECT EventEntity.* FROM EventEntity
            JOIN EventFtsEntity ON EventEntity.rowid = EventFtsEntity.rowid
            WHERE EventFtsEntity MATCH :query
    """,
    )
    suspend fun searchEventsInternal(query: String): List<CombinedEvent>

    @Transaction
    suspend fun searchOriginalEvents(query: String): List<CombinedEvent> {
        val sanitized = query.sanitizeForFts().ifBlank { return emptyList() }
        return searchEventsInternal(sanitized)
    }

    @Transaction
    suspend fun searchEvents(query: String): List<Event> {
        return searchOriginalEvents(query).map {
            it.toEvent(getRecurrenceRuleByCombinedEvent(it))
        }
    }

    @Transaction
    suspend fun searchOriginalEventsJoinedCourses(query: String): List<CombinedEvent> {
        val events = searchOriginalEvents(query)
        val courseIds = searchCourses(query).map { it.course.id }
        val eventsByCourse = getAllOriginalEventsByCourses(courseIds)
        val allEvents = (events + eventsByCourse).asSequence()
            .distinctBy { it.event.id }
            .sortedWith(
                compareBy<CombinedEvent> { it.event.startTime }
                    .thenBy { it.event.priority }
                    .thenByDescending { it.event.endTime }
                    .thenBy { it.event.createdAt },
            )
            .toList()
        return allEvents
    }

    @Transaction
    suspend fun searchEventsJoinedCourses(query: String): List<Event> {
        return searchOriginalEventsJoinedCourses(query).map {
            it.toEvent(getRecurrenceRuleByCombinedEvent(it))
        }
    }

    @Query(
        """
            SELECT * FROM EventTombstoneEntity
            WHERE target = :target
        """,
    )
    suspend fun getAllEventTombstonesByTarget(target: EventDeletionTarget): List<EventTombstoneEntity>

    @Transaction
    suspend fun importFromICalendar(
        iCalendar: ICalendar,
        resources: Resources,
        onSemesterShadowed: (existing: SemesterEntity, new: SemesterEntity) -> SemesterEntity = { _, new -> new },
        onSemesterConflict: (existing: SemesterEntity, new: SemesterEntity) -> ScheduleImportOnConflict = { _, _ -> Skip },
        onRecurrenceRuleShadowed: (existing: RecurrenceRuleEntity, new: RecurrenceRuleEntity) -> RecurrenceRuleEntity = { _, new -> new },
        onRecurrenceRuleConflict: (existing: RecurrenceRuleEntity, new: RecurrenceRuleEntity) -> ScheduleImportOnConflict = { _, _ -> Skip },
        onCourseShadowed: (existing: CourseEntity, new: CourseEntity) -> CourseEntity = { _, new -> new },
        onCourseConflict: (existing: CourseEntity, new: CourseEntity) -> ScheduleImportOnConflict = { _, _ -> Skip },
        errors: MutableList<String>? = null,
    ): Result<List<Event>> {
        val logger = Logger("ImportICalendar")

        val events = iCalendar.parse(
            onNewRecurrenceRule = { rule, course -> course?.copy(recurrenceRuleId = rule.id) },
            errors = errors,
        ).toMutableList()

        if (events.isEmpty()) return Result.failure(Throwable("No valid events found in the provided iCalendar data"))

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

            runCatching { getAllSemestersByIds(semesterIds.toList()) }
                .onFailure { logger.error(it) { "Failed to query existing semesters from database" } }
                .getOrElse {
                    return Result.failure(
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
                                runCatching { updateSemester(new) }
                                    .onFailure {
                                        logger.error(it) { "Failed to update semester ${new.name}" }
                                        return Result.failure(
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

            runCatching { insertAllSemesters(semestersToInsert.values.toList()) }
                .onFailure { logger.error(it) { "Failed to insert semesters" } }
                .getOrElse { return Result.failure(Throwable("Failed to insert semesters", it)) }
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

        if (events.isEmpty()) return Result.failure(Throwable("All events have invalid or conflicting semesters"))

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

            runCatching { getAllRecurrenceRulesByIds(recurrenceRuleIds.toList()) }
                .onFailure { logger.error(it) { "Failed to query existing recurrence rules from database" } }
                .getOrElse {
                    return Result.failure(
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
                                runCatching { updateRecurrenceRule(new) }
                                    .onFailure {
                                        logger.error(it) { "Failed to update recurrence rule ${new.rule.format()}" }
                                        return Result.failure(
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

            runCatching { insertAllRecurrenceRules(recurrenceRulesToInsert.values.toList()) }
                .onFailure { logger.error(it) { "Failed to insert recurrence rules" } }
                .getOrElse {
                    return Result.failure(
                        Throwable(
                            "Failed to insert recurrence rules",
                            it,
                        ),
                    )
                }
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

        if (events.isEmpty()) return Result.failure(Throwable("All events have invalid or conflicting recurrence rules"))

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

            runCatching { getAllCoursesByIds(courseIds.toList()) }
                .onFailure { logger.error(it) { "Failed to query existing courses from database" } }
                .getOrElse {
                    return Result.failure(
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
                                runCatching { updateCourse(new) }
                                    .onFailure {
                                        logger.error(it) { "Failed to update course ${new.name}" }
                                        return Result.failure(
                                            Throwable(
                                                "Failed to update course ${new.name}",
                                                it,
                                            ),
                                        )
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

            runCatching { insertAllCourses(coursesToInsert.values.toList()) }
                .onFailure { logger.error(it) { "Failed to insert courses" } }
                .getOrElse { return Result.failure(Throwable("Failed to insert courses", it)) }
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

        if (events.isEmpty()) return Result.failure(Throwable("All events have invalid or conflicting courses"))

        val eventsInsertionResults = runCatching { insertAllEvents(events.map { it.toEntity() }) }
            .onFailure { logger.error(it) { "Failed to insert events" } }
            .getOrElse { return Result.failure(Throwable("Failed to insert events", it)) }

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

        if (events.isEmpty()) return Result.failure(Throwable("All events failed to be inserted into the database"))

        return Result.success(events)
    }
}
