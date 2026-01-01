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

package top.ltfan.knowmad.data.llm

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import top.ltfan.knowmad.util.calculateLexoRank
import kotlin.uuid.Uuid

@Dao
interface LLMConfigDao {
    @Insert
    suspend fun insertProviderUnsafeRanking(config: LLMProviderConfigEntity): Long

    @Transaction
    suspend fun insertProviderAtBeginning(config: LLMProviderConfigEntity): Long {
        val firstRank = getProviderRankAt(0)
        val newRank = calculateLexoRank(null, firstRank)
        val newConfig = config.copy(rank = newRank)
        return insertProviderUnsafeRanking(newConfig)
    }

    @Transaction
    suspend fun insertProviderAtEnd(config: LLMProviderConfigEntity): Long {
        val lastRank = getProviderRankDescAt(0)
        val newRank = calculateLexoRank(lastRank, null)
        val newConfig = config.copy(rank = newRank)
        return insertProviderUnsafeRanking(newConfig)
    }

    @Insert
    suspend fun insertModelUnsafeRanking(model: LLMConfigEntity): Long

    @Transaction
    suspend fun insertModelAtBeginning(model: LLMConfigEntity): Long {
        val firstRank = getModelRankAt(model.providerConfigId, 0)
        val newRank = calculateLexoRank(null, firstRank)
        val newModel = model.copy(rank = newRank)
        return insertModelUnsafeRanking(newModel)
    }

    @Transaction
    suspend fun insertModelAtEnd(model: LLMConfigEntity): Long {
        val lastRank = getModelRankDescAt(model.providerConfigId, 0)
        val newRank = calculateLexoRank(lastRank, null)
        val newModel = model.copy(rank = newRank)
        return insertModelUnsafeRanking(newModel)
    }

    @Delete
    suspend fun deleteProvider(config: LLMProviderConfigEntity): Int

    @Delete
    suspend fun deleteModel(model: LLMConfigEntity): Int

    @Query("DELETE FROM LLMProviderConfigEntity WHERE id = :id")
    suspend fun deleteProviderById(id: Long): Int

    @Query("DELETE FROM LLMConfigEntity WHERE id = :id")
    suspend fun deleteModelById(id: Long): Int

    @Update
    suspend fun updateProvider(config: LLMProviderConfigEntity): Int

    @Update
    suspend fun updateModel(model: LLMConfigEntity): Int

    @Query("SELECT * FROM LLMProviderConfigEntity ORDER BY rank ASC")
    fun getAllProviders(): PagingSource<Int, LLMProviderConfigEntity>

    @Query("SELECT rank FROM LLMProviderConfigEntity ORDER BY rank ASC LIMIT 1 OFFSET :pos")
    suspend fun getProviderRankAt(pos: Int): ByteArray?

    @Query("SELECT rank FROM LLMProviderConfigEntity ORDER BY rank DESC LIMIT 1 OFFSET :pos")
    suspend fun getProviderRankDescAt(pos: Int): ByteArray?

    @Query("SELECT * FROM LLMConfigEntity WHERE providerConfigId = :providerConfigId ORDER BY rank ASC")
    fun getModelsByProvider(providerConfigId: Uuid): PagingSource<Int, LLMConfigEntity>

    @Query("SELECT * FROM LLMConfigEntity WHERE providerConfigId = :providerConfigId ORDER BY rank ASC")
    suspend fun getModelsByProviderOnce(providerConfigId: Uuid): List<LLMConfigEntity>

    @Query("SELECT COUNT(*) FROM LLMConfigEntity WHERE providerConfigId = :providerConfigId")
    fun getModelCountByProvider(providerConfigId: Uuid): Flow<Int>

    @Query("SELECT rank FROM LLMConfigEntity WHERE providerConfigId = :providerConfigId ORDER BY rank ASC LIMIT 1 OFFSET :pos")
    suspend fun getModelRankAt(providerConfigId: Uuid, pos: Int): ByteArray?

    @Query("SELECT rank FROM LLMConfigEntity WHERE providerConfigId = :providerConfigId ORDER BY rank DESC LIMIT 1 OFFSET :pos")
    suspend fun getModelRankDescAt(providerConfigId: Uuid, pos: Int): ByteArray?
}
