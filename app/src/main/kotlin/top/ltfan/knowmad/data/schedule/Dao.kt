/*
 * Knowmad - Knowledge nomad
 * Copyright (C) 2025 LTFan (aka xfqwdsj)
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
import kotlin.uuid.Uuid

@Dao
interface ScheduleDao {
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
    suspend fun updateEvent(event: EventEntity): Int

    @Query("SELECT * FROM SemesterEntity ORDER BY startDate ASC")
    suspend fun getAllSemesters(): List<SemesterEntity>

    @Transaction
    @Query("SELECT * FROM EventEntity WHERE semesterId = :semesterId ORDER BY startTime ASC")
    suspend fun getAllEventsBySemester(semesterId: Uuid): List<EventWithSemesterAndCourse>
}
