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

package top.ltfan.knowmad.test

import kotlinx.serialization.Serializable
import okio.ByteString.Companion.toByteString
import top.ltfan.knowmad.data.file.FileEntity
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class PrintableFile(
    val id: Uuid,
    val type: String,
    val hash: String,
    val name: String?,
    val path: String,
    val format: String,
    val mimeType: String,
    val size: Int,
    val createdAt: Instant,
)

fun FileEntity.printable() = PrintableFile(
    id = id,
    type = type,
    hash = hash.toByteString().hex(),
    name = name,
    path = path.toString(),
    format = format,
    mimeType = mimeType,
    size = size,
    createdAt = createdAt,
)
