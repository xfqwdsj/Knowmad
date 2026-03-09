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

package top.ltfan.knowmad.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration260201To260300 : Migration(260201, 260300) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `EventTombstoneEntity`")
        db.execSQL(
            """
                CREATE TABLE IF NOT EXISTS `EventTombstoneEntity` (
                    `id` BLOB NOT NULL, 
                    `eventId` BLOB NOT NULL, 
                    `target` TEXT NOT NULL, 
                    `deletedAt` INTEGER NOT NULL, 
                    PRIMARY KEY(`id`)
                )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_EventTombstoneEntity_eventId` ON `EventTombstoneEntity` (`eventId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_EventTombstoneEntity_target` ON `EventTombstoneEntity` (`target`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_EventTombstoneEntity_eventId_target` ON `EventTombstoneEntity` (`eventId`, `target`)")
    }
}
