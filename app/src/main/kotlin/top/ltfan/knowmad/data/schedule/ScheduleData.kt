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

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
sealed interface Event {
    val id: Uuid
    val semester: SemesterEntity
    val name: String
    val location: String
    val color: Int
    val startTime: Instant
    val endTime: Instant
    val reminders: List<Duration>
    val notes: String?

    fun toEntity(): EventEntity

    @Serializable
    data class Normal(
        override val id: Uuid = Uuid.generateV7(),
        override val semester: SemesterEntity,
        override val name: String,
        override val location: String,
        override val color: Int,
        override val startTime: Instant,
        override val endTime: Instant,
        override val reminders: List<Duration> = emptyList(),
        override val notes: String? = null,
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
        )
    }

    @Serializable
    data class Course(
        override val id: Uuid,
        override val semester: SemesterEntity,
        val course: CourseEntity,
        override val name: String,
        val instructor: String,
        override val location: String,
        override val color: Int,
        override val startTime: Instant,
        override val endTime: Instant,
        override val reminders: List<Duration> = emptyList(),
        override val notes: String? = null,
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
        )
    }
}

fun EventWithSemesterAndCourse.toEvent(): Event {
    return if (course != null) {
        Event.Course(
            id = event.id,
            semester = semester,
            course = course,
            name = event.name ?: course.name,
            instructor = course.instructor,
            location = event.location ?: course.location,
            color = event.color ?: course.color,
            startTime = event.startTime,
            endTime = event.endTime,
            reminders = event.reminders,
            notes = event.notes,
        )
    } else {
        requireNotNull(event.location) {
            "Event location is null for normal event with id ${event.id}"
        }
        requireNotNull(event.name) {
            "Event name is null for normal event with id ${event.id}"
        }
        requireNotNull(event.color) {
            "Event color is null for normal event with id ${event.id}"
        }
        Event.Normal(
            id = event.id,
            semester = semester,
            name = event.name,
            location = event.location,
            color = event.color,
            startTime = event.startTime,
            endTime = event.endTime,
            reminders = event.reminders,
            notes = event.notes,
        )
    }
}
