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

package top.ltfan.knowmad.data.file

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import okio.Path
import kotlin.uuid.Uuid

@Dao
interface FileDao {
    @Insert
    suspend fun insertFile(fileEntity: FileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(fileEntities: List<FileEntity>): List<Long>

    @Delete
    suspend fun deleteFile(fileEntity: FileEntity): Int

    @Query("DELETE FROM FileEntity WHERE id IN (:ids)")
    suspend fun deleteFilesByIds(ids: List<Uuid>): Int

    @Update
    suspend fun updateFile(fileEntity: FileEntity): Int

    @Query("SELECT * FROM FileEntity")
    suspend fun getAllFiles(): List<FileEntity>

    @Query("SELECT id FROM FileEntity WHERE id IN (:ids)")
    suspend fun getExistingFileIds(ids: List<Uuid>): List<Uuid>

    @Query("SELECT * FROM FileEntity WHERE id = :id")
    suspend fun getFileById(id: Uuid): FileEntity?

    @Query("SELECT * FROM FileEntity WHERE type = :type AND hash = :hash LIMIT 1")
    suspend fun getFileByMeta(type: String, hash: ByteArray): FileEntity?

    @Query("SELECT * FROM FileEntity WHERE path = :path LIMIT 1")
    suspend fun getFileByPath(path: Path): FileEntity?
}
