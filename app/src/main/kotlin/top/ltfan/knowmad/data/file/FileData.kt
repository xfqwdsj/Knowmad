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

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import top.ltfan.knowmad.data.database.AppDatabase
import top.ltfan.knowmad.util.HashComputationDispatcher
import top.ltfan.knowmad.util.Logger
import top.ltfan.knowmad.util.withLock
import kotlin.uuid.Uuid

val DbFileMutex = List(16) { Mutex() }

suspend fun getOrStoreFile(
    database: AppDatabase,
    filesDir: Path,
    type: String,
    name: String?,
    format: String,
    mimeType: String,
    content: ByteArray,
    precomputedHash: ByteString? = null,
    fs: FileSystem = FileSystem.SYSTEM,
): FileEntity {
    val logger = Logger("getOrStoreFile")

    val dao = database.fileDao()

    val hash = precomputedHash ?: withContext(HashComputationDispatcher) {
        content.toByteString().sha256()
    }

    val hashArray = hash.toByteArray()

    val typePath = type.toPath().normalized()
    val normalizedType = typePath.toString()

    require(normalizedType.isNotEmpty()) { "Type cannot be empty" }
    require(!normalizedType.startsWith("..")) { "Type cannot start with '..'" }

    val size = content.size
    return withContext(Dispatchers.IO) {
        DbFileMutex.withLock(hashArray.contentHashCode()) {
            val existingEntity = dao.getFileByMeta(normalizedType, hashArray)
            if (existingEntity != null && fs.exists(existingEntity.path)) {
                return@withContext existingEntity
            }

            require(filesDir.isAbsolute) { "Files directory must be an absolute path" }

            val uuid = Uuid.generateV7()
            val baseDir = filesDir.normalizedDbBaseDir(hash)
            val directory = baseDir.resolve(normalizedType).normalized()

            val baseDirString = baseDir.toString()
            val directoryString = directory.toString()

            require(baseDirString != directoryString) { "Directory cannot be the same as files directory" }
            require(directoryString.startsWith(baseDirString)) { "Directory must be a subdirectory of files directory" }

            val newPath = directory.resolve("$uuid.dat")
            val tempPath = directory.resolve("$uuid.tmp")

            if (fs.exists(directory)) {
                if (!fs.metadata(directory).isDirectory) {
                    logger.warn { "Expected a directory at $directory but found a file. Deleting it." }
                    fs.delete(directory)
                } else {
                    val fsPaths = fs.list(directory)
                    val fsIds = mutableSetOf<Uuid>()

                    for (fsPath in fsPaths) {
                        if (fs.metadata(fsPath).isDirectory) {
                            logger.warn { "Unexpected directory in files directory: $fsPath" }
                            fs.deleteRecursively(fsPath)
                            continue
                        }
                        val idString = fsPath.name.removeSuffix(".dat")
                        val id = Uuid.parseOrNull(idString) ?: run {
                            logger.warn { "Unexpected file in files directory with non-UUID name: $fsPath" }
                            fs.delete(fsPath)
                            continue
                        }
                        fsIds.add(id)
                    }

                    val existingIdsInDb = dao.getExistingFileIds(fsIds.toList()).toSet()
                    val orphanFileIds = fsIds - existingIdsInDb
                    for (orphanId in orphanFileIds) {
                        fs.delete(directory.resolve("$orphanId.dat"))
                    }
                }
            }

            fs.createDirectories(directory)
            fs.write(tempPath) { write(content) }

            try {
                fs.atomicMove(tempPath, newPath)

                val entity = database.withTransaction {
                    val existingEntity = dao.getFileByMeta(normalizedType, hashArray)
                    if (existingEntity != null) {
                        if (fs.exists(existingEntity.path)) {
                            return@withTransaction existingEntity
                        } else {
                            dao.deleteFile(existingEntity)
                        }
                    }

                    val newEntity = FileEntity(
                        id = uuid,
                        type = normalizedType,
                        hash = hashArray,
                        name = name,
                        path = newPath,
                        format = format,
                        mimeType = mimeType,
                        size = size,
                    )

                    dao.insertFile(newEntity)
                    newEntity
                }

                if (entity.id != uuid) {
                    fs.delete(newPath)
                }

                entity
            } finally {
                if (fs.exists(tempPath)) {
                    try {
                        fs.delete(tempPath)
                    } catch (e: Throwable) {
                        logger.warn(e) { "Failed to delete temp file at $tempPath" }
                    }
                }
            }
        }
    }
}

const val DbFileDir = "db_files"

fun Path.normalizedDbBaseDir(hash: ByteString): Path {
    val hashHex = hash.hex()
    var result = resolve(DbFileDir)
    repeat(2) { index ->
        if (index * 2 + 2 > hashHex.length) return result.normalized()
        result = result.resolve(hashHex.substring(index * 2, index * 2 + 2))
    }
    return result.normalized()
}
