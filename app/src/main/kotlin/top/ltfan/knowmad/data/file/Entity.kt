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

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import okio.Path
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Entity(
    indices = [
        Index("type", "hash", unique = true),
        Index("path", unique = true),
        Index("type", "size"),
    ],
)
data class FileEntity(
    @PrimaryKey
    val id: Uuid = Uuid.generateV7(),
    val type: String,
    val hash: ByteArray,
    val name: String?,
    val path: Path,
    val format: String,
    val mimeType: String,
    val size: Int,
    val createdAt: Instant = Clock.System.now(),
    val deletedAt: Instant? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileEntity

        if (size != other.size) return false
        if (id != other.id) return false
        if (type != other.type) return false
        if (!hash.contentEquals(other.hash)) return false
        if (name != other.name) return false
        if (path != other.path) return false
        if (format != other.format) return false
        if (mimeType != other.mimeType) return false
        if (createdAt != other.createdAt) return false
        if (deletedAt != other.deletedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = size
        result = 31 * result + id.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + hash.contentHashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + path.hashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (deletedAt?.hashCode() ?: 0)
        return result
    }
}
