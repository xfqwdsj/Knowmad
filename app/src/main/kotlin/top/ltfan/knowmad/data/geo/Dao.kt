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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import androidx.room.Update
import top.ltfan.knowmad.data.FtsDao
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Dao
interface GeoDao : FtsDao {
    @Insert
    suspend fun insertPlace(place: PlaceEntity): Long

    @Insert
    suspend fun insertNode(node: NodeEntity): Long

    @Insert
    suspend fun insertPlaceNodeCrossRef(crossRef: PlaceNodeCrossRef): Long

    @Insert
    suspend fun insertRoad(road: RoadEntity): Long

    @Delete
    suspend fun deletePlace(place: PlaceEntity): Int

    @Delete
    suspend fun deleteNode(node: NodeEntity): Int

    @Delete
    suspend fun deletePlaceNodeCrossRef(crossRef: PlaceNodeCrossRef): Int

    @Delete
    suspend fun deleteRoad(road: RoadEntity): Int

    @Update
    suspend fun updatePlaceInternal(place: PlaceEntity): Int

    @Transaction
    suspend fun updatePlace(place: PlaceEntity): Int {
        val (w, e, n, s) = place.points.calculateBoundingBox()

        return updatePlaceInternal(
            place.copy(
                westernLongitude = w,
                easternLongitude = e,
                northernLatitude = n,
                southernLatitude = s,
                updatedAt = Clock.System.now(),
            ),
        )
    }

    @Update
    suspend fun updateNodeInternal(node: NodeEntity): Int

    @Transaction
    suspend fun updateNode(node: NodeEntity) = updateNodeInternal(
        node.copy(updatedAt = Clock.System.now()),
    )

    @Update
    suspend fun updateRoadInternal(road: RoadEntity): Int

    @Transaction
    suspend fun updateRoad(road: RoadEntity) = updateRoadInternal(
        road.copy(updatedAt = Clock.System.now()),
    )

    @Query("SELECT * FROM PlaceEntity WHERE id = :id")
    suspend fun getPlaceById(id: Uuid): PlaceEntity?

    @Query(
        """
            SELECT * FROM PlaceEntity 
            WHERE (
                southernLatitude <= :north AND northernLatitude >= :south
            ) AND (
                CASE 
                    WHEN :west <= :east THEN 
                        (easternLongitude >= :west AND westernLongitude <= :east)
                    ELSE 
                        (easternLongitude >= :west OR westernLongitude <= :east)
                END
            )
        """,
    )
    suspend fun getPlacesIntersecting(
        west: Double,
        east: Double,
        north: Double,
        south: Double,
    ): List<PlaceEntity>

    @Transaction
    suspend fun getPlacesIntersecting(box: BoundingBox) = getPlacesIntersecting(
        west = box.west,
        east = box.east,
        north = box.north,
        south = box.south,
    )

    @Query(
        """
            SELECT * FROM PlaceEntity
            JOIN PlaceFtsEntity ON PlaceEntity.id = PlaceFtsEntity.rowid
            WHERE PlaceFtsEntity MATCH :query
        """,
    )
    suspend fun searchPlacesInternal(query: String): List<PlaceEntity>

    @Transaction
    suspend fun searchPlaces(query: String): List<PlaceEntity> {
        val sanitized = query.sanitizeForFts().ifBlank { return emptyList() }
        return searchPlacesInternal(sanitized)
    }

    @Query("SELECT * FROM NodeEntity WHERE id = :id")
    suspend fun getNodeById(id: Uuid): NodeEntity?

    @Query(
        """
            SELECT * FROM NodeEntity 
            WHERE latitude <= :north AND latitude >= :south
            AND (
                CASE 
                    WHEN :west <= :east THEN 
                        (longitude >= :west AND longitude <= :east)
                    ELSE 
                        (longitude >= :west OR longitude <= :east)
                END
            )
        """,
    )
    suspend fun getNodesWithin(
        west: Double,
        east: Double,
        north: Double,
        south: Double,
    ): List<NodeEntity>

    @Query(
        """
            SELECT * FROM NodeEntity 
            WHERE (zOrder BETWEEN :minZ AND :maxZ)
            AND (latitude <= :north AND latitude >= :south)
            AND (
                CASE 
                    WHEN :west <= :east THEN 
                        (longitude >= :west AND longitude <= :east)
                    ELSE 
                        (longitude >= :west OR longitude <= :east)
                END
            )
        """,
    )
    suspend fun getNodesWithin(
        minZ: Long,
        maxZ: Long,
        west: Double,
        east: Double,
        north: Double,
        south: Double,
    ): List<NodeEntity>

    @Transaction
    suspend fun getNodesWithin(box: BoundingBox) = if (box.west > box.east) {
        val leftBox = box.copy(east = 180.0)
        val rightBox = box.copy(west = -180.0)

        getNodesWithinSingleBox(leftBox) + getNodesWithinSingleBox(rightBox)
    } else {
        getNodesWithinSingleBox(box)
    }

    @Transaction
    suspend fun getNodesWithinSingleBox(box: BoundingBox): List<NodeEntity> {
        val z1 = calculateZOrder(box.west, box.south)
        val z2 = calculateZOrder(box.east, box.north)
        val z3 = calculateZOrder(box.west, box.north)
        val z4 = calculateZOrder(box.east, box.south)

        val minZ = minOf(z1, z2, z3, z4)
        val maxZ = maxOf(z1, z2, z3, z4)

        return getNodesWithin(
            minZ = minZ,
            maxZ = maxZ,
            west = box.west,
            east = box.east,
            north = box.north,
            south = box.south,
        )
    }

    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
            SELECT * FROM NodeEntity 
            INNER JOIN PlaceNodeCrossRef ON NodeEntity.id = PlaceNodeCrossRef.nodeId 
            WHERE PlaceNodeCrossRef.placeId = :placeId
        """,
    )
    suspend fun getNodesForPlace(placeId: Uuid): List<NodeEntity>

    @Query(
        """
            SELECT * FROM NodeEntity
            JOIN NodeFtsEntity ON NodeEntity.id = NodeFtsEntity.rowid
            WHERE NodeFtsEntity MATCH :query
        """,
    )
    suspend fun searchNodesInternal(query: String): List<NodeEntity>

    @Transaction
    suspend fun searchNodes(query: String): List<NodeEntity> {
        val sanitized = query.sanitizeForFts().ifBlank { return emptyList() }
        return searchNodesInternal(sanitized)
    }

    @Query("SELECT * FROM RoadEntity WHERE id = :id")
    suspend fun getRoadById(id: Uuid): RoadEntity?

    @Query("SELECT * FROM RoadEntity WHERE fromNodeId = :nodeId OR toNodeId = :nodeId")
    suspend fun getRoadsConnectedTo(nodeId: Uuid): List<RoadEntity>

    @Transaction
    @Query(
        """
            SELECT DISTINCT RoadEntity.* FROM RoadEntity
            INNER JOIN NodeEntity ON RoadEntity.fromNodeId = NodeEntity.id OR RoadEntity.toNodeId = NodeEntity.id
            WHERE NodeEntity.latitude <= :north AND NodeEntity.latitude >= :south
            AND (
                CASE 
                    WHEN :west <= :east THEN (NodeEntity.longitude >= :west AND NodeEntity.longitude <= :east)
                    ELSE (NodeEntity.longitude >= :west OR NodeEntity.longitude <= :east)
                END
            )
        """,
    )
    suspend fun getRoadsWithin(
        west: Double,
        east: Double,
        north: Double,
        south: Double,
    ): List<RoadEntity>

    @Transaction
    suspend fun getRoadsWithin(box: BoundingBox) =
        getRoadsWithin(box.west, box.east, box.north, box.south)

    @Query(
        """
            SELECT * FROM RoadEntity
            JOIN RoadFtsEntity ON RoadEntity.id = RoadFtsEntity.rowid
            WHERE RoadFtsEntity MATCH :query
        """,
    )
    suspend fun searchRoadsInternal(query: String): List<RoadEntity>

    @Transaction
    suspend fun searchRoads(query: String): List<RoadEntity> {
        val sanitized = query.sanitizeForFts().ifBlank { return emptyList() }
        return searchRoadsInternal(sanitized)
    }
}
