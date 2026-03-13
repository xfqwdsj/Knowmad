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

package top.ltfan.knowmad.data.geo

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

@Serializable
@Entity(
    indices = [
        Index("westernLongitude"),
        Index("easternLongitude"),
        Index("northernLatitude"),
        Index("southernLatitude"),
    ],
)
data class PlaceEntity(
    @PrimaryKey
    val id: Uuid = Uuid.generateV7(),
    val name: String,
    val points: List<Wgs84Point>,
    val westernLongitude: Double,
    val easternLongitude: Double,
    val northernLatitude: Double,
    val southernLatitude: Double,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = createdAt,
    val deletedAt: Instant? = null,
)

fun PlaceEntity(
    id: Uuid = Uuid.generateV7(),
    name: String,
    points: List<Wgs84Point>,
    createdAt: Instant = Clock.System.now(),
    updatedAt: Instant = createdAt,
): PlaceEntity {
    require(points.isNotEmpty()) { "A place must have at least one point." }

    val (w, e, n, s) = points.calculateBoundingBox()

    return PlaceEntity(
        id = id,
        name = name,
        points = points,
        westernLongitude = w,
        easternLongitude = e,
        northernLatitude = n,
        southernLatitude = s,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

@Fts4(
    tokenizer = FtsOptions.TOKENIZER_ICU,
    contentEntity = PlaceEntity::class,
)
@Entity
data class PlaceFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val name: String,
)

@Serializable
@Entity(
    indices = [
        Index("type"),
        Index("longitude"),
        Index("latitude"),
        Index("zOrder"),
    ],
)
data class NodeEntity(
    @PrimaryKey
    val id: Uuid = Uuid.generateV7(),
    val name: String? = null,
    val type: NodeType = Junction,
    val longitude: Double,
    val latitude: Double,
    val altitude: Double? = null,
    val zOrder: Long,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = createdAt,
    val deletedAt: Instant? = null,
) {
    constructor(
        id: Uuid = Uuid.generateV7(),
        name: String? = null,
        type: NodeType = Junction,
        point: Wgs84Point,
        createdAt: Instant = Clock.System.now(),
        updatedAt: Instant = createdAt,
        deletedAt: Instant? = null,
    ) : this(
        id = id,
        name = name,
        type = type,
        longitude = point.longitude,
        latitude = point.latitude,
        altitude = point.altitude,
        zOrder = point.zOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
}

@Fts4(
    tokenizer = FtsOptions.TOKENIZER_ICU,
    contentEntity = NodeEntity::class,
)
@Entity
data class NodeFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val name: String?,
)

@Entity(
    indices = [
        Index("placeId"),
        Index("nodeId"),
    ],
    primaryKeys = ["placeId", "nodeId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["nodeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaceNodeCrossRef(
    val placeId: Uuid,
    val nodeId: Uuid,
)

@Entity(
    indices = [
        Index("fromNodeId"),
        Index("toNodeId"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = NodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromNodeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["toNodeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RoadEntity(
    @PrimaryKey
    val id: Uuid = Uuid.generateV7(),
    val name: String,
    val fromNodeId: Uuid,
    val toNodeId: Uuid,
    val length: Double,
    val maxSpeed: Double,
    val width: Double = 0.0,
    val isOneWay: Boolean = false,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = createdAt,
    val deletedAt: Instant? = null,
)

@Fts4(
    tokenizer = FtsOptions.TOKENIZER_ICU,
    contentEntity = RoadEntity::class,
)
@Entity
data class RoadFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val name: String,
)

@Serializable
enum class NodeType {
    Junction,
    PlaceEntry,
    TransportStop,
    Gate,
}
