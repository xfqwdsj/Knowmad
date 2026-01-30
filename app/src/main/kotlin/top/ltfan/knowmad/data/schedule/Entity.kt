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
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.R
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
@Entity(
    indices = [
        Index("startDate"),
        Index("startDate", "endDate")
    ],
)
data class SemesterEntity(
    @PrimaryKey
    val id: Uuid = Uuid.generateV7(),
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val timeZone: TimeZone,
) {
    companion object {
        val DefaultSemesterId = Uuid.parse("019c0c33-1400-7225-a55f-906660045bdc")

        fun createDefault(resources: Resources) = SemesterEntity(
            id = DefaultSemesterId,
            name = resources.getString(R.string.schedule_semester_label_default),
            startDate = Instant.DISTANT_PAST.toLocalDateTime(UTC).date,
            endDate = Instant.DISTANT_FUTURE.toLocalDateTime(UTC).date,
            timeZone = UTC,
        )

    }
}

@Fts4(
    tokenizer = FtsOptions.TOKENIZER_ICU,
    contentEntity = SemesterEntity::class,
)
@Entity
data class SemesterFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val name: String,
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

@Serializable
data class CourseWithSemester(
    @Embedded val course: CourseEntity,
    @Relation(
        parentColumn = "semesterId",
        entityColumn = "id",
    )
    val semester: SemesterEntity,
)

@Fts4(
    tokenizer = FtsOptions.TOKENIZER_ICU,
    contentEntity = CourseEntity::class,
)
@Entity
data class CourseFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val name: String,
    val instructor: String,
    val location: String,
)

@Serializable
@Entity(
    indices = [
        Index("semesterId"),
        Index("courseId"),
        Index(
            "startTime", "priority", "endTime", "createdAt",
            orders = [ASC, DESC, ASC],
        ),
        Index(
            "semesterId", "startTime", "priority", "endTime", "createdAt",
            orders = [ASC, ASC, DESC, ASC],
        ),
    ],
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
    val reminders: Reminders = Empty,
    val notes: String? = null,
    val priority: ICalendarPriority = None,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = createdAt,
) {
    val vAlarms inline get() = reminders.list.map { it.toVAlarm(defaultDisplayText = name) }
}

@Serializable
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

@Fts4(
    tokenizer = FtsOptions.TOKENIZER_ICU,
    contentEntity = EventEntity::class,
)
@Entity
data class EventFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val name: String?, // TODO: fix search when some fields are null
    val instructor: String?,
    val location: String?,
    val notes: String?,
)
