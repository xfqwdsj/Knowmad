/*
 * Knowmad - Knowledge nomad
 * Copyright (C) 2026 LTFan (aka xfqwdsj)
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

package top.ltfan.knowmad.data.suggestion

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Entity(
    indices = [
        Index(value = ["expected"], unique = true),
    ],
)
data class PendingSuggestionEntity(
    @PrimaryKey
    val id: Uuid = Uuid.generateV7(),
    val expected: Instant,
    val prompt: String? = null,
    val createdAt: Instant = Clock.System.now(),
    val deletedAt: Instant? = null,
)

@Serializable
@Entity(
    indices = [
        Index(value = ["pendingSuggestionId"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = PendingSuggestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["pendingSuggestionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class SuggestionEntity(
    @PrimaryKey
    val id: Uuid = Uuid.generateV7(),
    val prompt: String,
    val capsuleTitle: String,
    val notificationTitle: String,
    val notificationContent: String,
    val notificationSummary: String? = null,
    val pendingSuggestionId: Uuid? = null,
    val createdAt: Instant = Clock.System.now(),
    val deletedAt: Instant? = null,
) {
    constructor(
        id: Uuid = Uuid.generateV7(),
        pendingSuggestion: PendingSuggestionEntity,
        capsuleTitle: String,
        notificationTitle: String,
        notificationContent: String,
        notificationSummary: String? = null,
        createdAt: Instant = Clock.System.now(),
        deletedAt: Instant? = null,
    ) : this(
        id = id,
        prompt = pendingSuggestion.prompt ?: error("Pending suggestion must have a prompt"),
        capsuleTitle = capsuleTitle,
        notificationTitle = notificationTitle,
        notificationContent = notificationContent,
        notificationSummary = notificationSummary,
        pendingSuggestionId = pendingSuggestion.id,
        createdAt = createdAt,
        deletedAt = deletedAt,
    )
}

@Fts4(
    tokenizer = FtsOptions.TOKENIZER_ICU,
    contentEntity = SuggestionEntity::class,
)
@Entity
data class SuggestionFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val prompt: String,
    val capsuleTitle: String,
    val notificationTitle: String,
    val notificationContent: String,
    val notificationSummary: String? = null,
)
