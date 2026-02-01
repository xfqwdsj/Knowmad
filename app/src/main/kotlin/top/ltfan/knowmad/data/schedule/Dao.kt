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
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import top.ltfan.knowmad.data.FtsDao
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Dao
interface ScheduleDao : FtsDao {
    @Insert
    suspend fun insertSemester(semester: SemesterEntity): Long

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

    @Insert
    suspend fun insertCourse(course: CourseEntity): Long

    @Insert
    suspend fun insertAllCourses(courses: List<CourseEntity>): List<Long>

    @Insert
    suspend fun insertEvent(event: EventEntity): Long

    @Insert
    suspend fun insertAllEvents(events: List<EventEntity>): List<Long>

    @Delete
    suspend fun deleteSemester(semester: SemesterEntity): Int

    @Delete
    suspend fun deleteRecurrenceRule(rule: RecurrenceRuleEntity): Int

    @Delete
    suspend fun deleteCourse(course: CourseEntity): Int

    @Delete
    suspend fun deleteEvent(event: EventEntity): Int

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

    @Query("SELECT * FROM SemesterEntity WHERE id = :id")
    suspend fun getSemesterById(id: Uuid): SemesterEntity?

    @Query("SELECT * FROM SemesterEntity ORDER BY startDate ASC")
    suspend fun getAllSemesters(): List<SemesterEntity>

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
}
