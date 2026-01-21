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

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import biweekly.component.VAlarm
import biweekly.parameter.Related
import biweekly.property.Trigger
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
@Entity
data class SemesterEntity(
    @PrimaryKey
    val id: Uuid = Uuid.generateV7(),
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val timeZone: TimeZone,
)

@Serializable
@Entity(
    indices = [Index("semesterId")],
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semesterId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class CourseEntity(
    @PrimaryKey
    val id: Uuid = Uuid.generateV7(),
    val semesterId: Uuid,
    val name: String,
    val instructor: String,
    val location: String,
)

@Entity(
    indices = [Index("semesterId"), Index("courseId")],
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semesterId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EventEntity(
    @PrimaryKey
    val id: Uuid = Uuid.generateV7(),
    val semesterId: Uuid,
    val courseId: Uuid?,
    val name: String?,
    val instructor: String?,
    val location: String?,
    val color: ICalendarColor,
    val startTime: Instant,
    val endTime: Instant,
    val reminders: List<Duration> = emptyList(),
    val notes: String? = null,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = createdAt,
) {
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
}

data class EventWithSemesterAndCourse(
    @Embedded val event: EventEntity,
    @Relation(
        parentColumn = "semesterId",
        entityColumn = "id",
    )
    val semester: SemesterEntity,
    @Relation(
        parentColumn = "courseId",
        entityColumn = "id",
    )
    val course: CourseEntity?,
)
