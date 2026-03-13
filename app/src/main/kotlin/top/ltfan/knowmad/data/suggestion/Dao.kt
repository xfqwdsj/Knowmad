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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import top.ltfan.knowmad.util.Logger
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val logger = Logger("SuggestionDao")

@Dao
interface SuggestionDao {
    @Insert
    suspend fun insertPendingSuggestion(suggestion: PendingSuggestionEntity): Long

    @Query("DELETE FROM PendingSuggestionEntity WHERE id = :id")
    suspend fun deletePendingSuggestion(id: Uuid): Int

    @Query("DELETE FROM PendingSuggestionEntity WHERE expected < :time")
    suspend fun deletePendingSuggestionsBefore(time: Instant): Int

    @Query("UPDATE PendingSuggestionEntity SET deletedAt = :now WHERE id = :id")
    suspend fun softDeletePendingSuggestion(id: Uuid, now: Instant = Clock.System.now()): Int

    @Query("SELECT * FROM PendingSuggestionEntity WHERE id = :id AND deletedAt IS NULL")
    suspend fun getPendingSuggestionById(id: Uuid): PendingSuggestionEntity?

    @Query("SELECT * FROM PendingSuggestionEntity WHERE deletedAt IS NULL ORDER BY expected ASC")
    suspend fun getAllPendingSuggestions(): List<PendingSuggestionEntity>

    @Query(
        """
            SELECT * FROM PendingSuggestionEntity
            WHERE expected <= :now AND deletedAt IS NULL
            ORDER BY expected ASC
            LIMIT 1
        """,
    )
    suspend fun getNextPendingSuggestionInternal(now: Instant = Clock.System.now()): PendingSuggestionEntity?

    suspend fun getNextPendingSuggestion(now: Instant = Clock.System.now()): PendingSuggestionEntity? {
        val suggestion = getNextPendingSuggestionInternal(now)
        try {
            deletePendingSuggestionsBefore(now)
        } catch (e: Throwable) {
            logger.error(e) { "Failed to delete pending suggestions before $now" }
        }
        return suggestion
    }
}
