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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.datetime.LocalDate
import top.ltfan.knowmad.data.FtsDao
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Dao
interface ScheduleDao : FtsDao {
    @Insert
    suspend fun insertSemester(semester: SemesterEntity): Long

    @Insert
    suspend fun insertCourse(course: CourseEntity): Long

    @Insert
    suspend fun insertEvent(event: EventEntity): Long

    @Delete
    suspend fun deleteSemester(semester: SemesterEntity): Int

    @Delete
    suspend fun deleteCourse(course: CourseEntity): Int

    @Delete
    suspend fun deleteEvent(event: EventEntity): Int

    @Update
    suspend fun updateSemester(semester: SemesterEntity): Int

    @Update
    suspend fun updateCourse(course: CourseEntity): Int

    @Update
    suspend fun updateEventWithOutInstant(event: EventEntity): Int

    @Transaction
    suspend fun updateEvent(event: EventEntity): Int {
        val updatedEvent = event.copy(updatedAt = Clock.System.now())
        return updateEventWithOutInstant(updatedEvent)
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

    @Query("SELECT * FROM CourseEntity WHERE id = :id")
    suspend fun getCourseById(id: Uuid): CourseEntity?

    @Transaction
    @Query(
        """
            SELECT CourseEntity.* FROM CourseEntity
            JOIN CourseFtsEntity ON CourseEntity.rowid = CourseFtsEntity.rowid
            WHERE CourseEntity.semesterId = :semesterId AND CourseFtsEntity MATCH :query
    """,
    )
    suspend fun searchCoursesInternal(semesterId: Uuid, query: String): List<CourseEntity>

    @Transaction
    suspend fun searchCourses(semesterId: Uuid, query: String): List<CourseEntity> {
        val sanitized = query.sanitizeForFts().ifBlank { return emptyList() }
        return searchCoursesInternal(semesterId, sanitized)
    }

    @Transaction
    @Query("SELECT * FROM EventEntity WHERE id = :id")
    suspend fun getEventByIdInternal(id: Uuid): EventWithSemesterAndCourse?

    @Transaction
    suspend fun getEventById(id: Uuid): Event? {
        return getEventByIdInternal(id)?.toEvent()
    }

    @Query("SELECT * FROM EventEntity WHERE id = :id")
    suspend fun getEventEntityById(id: Uuid): EventEntity?

    @Transaction
    @Query(
        """
            SELECT * FROM EventEntity
            WHERE semesterId = :semesterId
            ORDER BY
                startTime ASC,
                endTime DESC,
                createdAt ASC
    """,
    )
    suspend fun getAllOriginalEventsBySemester(semesterId: Uuid): List<EventWithSemesterAndCourse>

    @Transaction
    suspend fun getAllEventsBySemester(semesterId: Uuid): List<Event> {
        return getAllOriginalEventsBySemester(semesterId).map { it.toEvent() }
    }

    @Transaction
    @Query(
        """
            SELECT * FROM EventEntity
            WHERE semesterId = :semesterId AND startTime <= :endTime AND endTime >= :startTime
            ORDER BY
                startTime ASC,
                endTime DESC,
                createdAt ASC
    """,
    )
    suspend fun getOriginalEventsInRange(
        semesterId: Uuid,
        startTime: Instant,
        endTime: Instant,
    ): List<EventWithSemesterAndCourse>

    @Transaction
    suspend fun getEventsInRange(
        semesterId: Uuid,
        startTime: Instant,
        endTime: Instant,
    ): List<Event> {
        return getOriginalEventsInRange(semesterId, startTime, endTime).map { it.toEvent() }
    }

    @Transaction
    @Query(
        """
            SELECT EventEntity.* FROM EventEntity
            JOIN EventFtsEntity ON EventEntity.rowid = EventFtsEntity.rowid
            WHERE EventEntity.semesterId = :semesterId AND EventFtsEntity MATCH :query
    """,
    )
    suspend fun searchEventsInternal(
        semesterId: Uuid,
        query: String,
    ): List<EventWithSemesterAndCourse>

    @Transaction
    suspend fun searchEvents(semesterId: Uuid, query: String): List<Event> {
        val sanitized = query.sanitizeForFts().ifBlank { return emptyList() }
        return searchEventsInternal(semesterId, sanitized).map { it.toEvent() }
    }
}
