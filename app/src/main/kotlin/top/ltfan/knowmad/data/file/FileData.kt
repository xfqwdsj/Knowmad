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

package top.ltfan.knowmad.data.file

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import top.ltfan.knowmad.ui.viewmodel.AppViewModel
import kotlin.uuid.Uuid

private val FileMutex = Mutex()

context(viewModel: AppViewModel)
suspend fun storeFileIfNotIndexed(
    type: String,
    name: String?,
    format: String,
    mimeType: String,
    content: ByteArray,
    precomputedHash: ByteArray? = null,
): FileEntity = withContext(Dispatchers.IO) {
    val fs = FileSystem.SYSTEM
    val dao = viewModel.application.appDatabase.fileDao()

    val hash = precomputedHash ?: run {
        content.toByteString().sha256().toByteArray()
    }
    val size = content.size

    var existingEntity = dao.getFileByTypeAndHash(type, hash)

    if (existingEntity != null && fs.exists(existingEntity.path)) {
        return@withContext existingEntity
    }

    val uuid = Uuid.generateV7()
    val directory = viewModel.application.filesDir.toOkioPath().resolve(type)
    val finalPath = directory.resolve(uuid.toString())
    val tempPath = directory.resolve("${uuid}.tmp")

    fs.createDirectories(directory)
    fs.write(tempPath) { write(content) }

    try {
        FileMutex.withLock {
            existingEntity = dao.getFileByTypeAndHash(type, hash)

            if (existingEntity != null) {
                val existingPath = existingEntity.path
                if (fs.exists(existingPath)) {
                    return@withContext existingEntity
                }

                if (existingPath.parent != null) {
                    fs.createDirectories(existingPath.parent!!)
                }
                fs.atomicMove(tempPath, existingPath)
                return@withContext existingEntity
            }

            val newEntity = FileEntity(
                id = uuid,
                type = type,
                hash = hash,
                name = name,
                path = finalPath,
                format = format,
                mimeType = mimeType,
                size = size,
            )

            dao.insertFile(newEntity)
            fs.atomicMove(tempPath, finalPath)
            return@withContext newEntity
        }
    } finally {
        if (fs.exists(tempPath)) {
            try {
                fs.delete(tempPath)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}
