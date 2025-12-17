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

package top.ltfan.knowmad.data.llm

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface LLMConfigDao {
    @Insert
    suspend fun insertProvider(config: LLMProviderConfigEntity): Long

    @Insert
    suspend fun insertModel(model: LLMEntity): Long

    @Delete
    suspend fun deleteProvider(config: LLMProviderConfigEntity): Int

    @Delete
    suspend fun deleteModel(model: LLMEntity): Int

    @Query("DELETE FROM LLMProviderConfigEntity WHERE id = :id")
    suspend fun deleteProviderById(id: Long): Int

    @Query("DELETE FROM LLMEntity WHERE id = :id")
    suspend fun deleteModelById(id: Long): Int

    @Update
    suspend fun updateProvider(config: LLMProviderConfigEntity): Int

    @Update
    suspend fun updateModel(model: LLMEntity): Int

    @Query("SELECT * FROM LLMProviderConfigEntity ORDER BY `order` ASC")
    suspend fun getAllProviders(): List<LLMProviderConfigEntity>

    @Query("SELECT * FROM LLMEntity WHERE providerConfigId = :providerConfigId ORDER BY `order` ASC")
    suspend fun getModelsByProvider(providerConfigId: Long): List<LLMEntity>
}
