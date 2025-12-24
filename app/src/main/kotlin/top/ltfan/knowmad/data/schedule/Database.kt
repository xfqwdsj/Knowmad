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

package top.ltfan.knowmad.data.schedule

import android.app.Application
import androidx.room.Database
import androidx.room.Room.databaseBuilder
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import top.ltfan.knowmad.data.DatabaseCompanion
import top.ltfan.knowmad.data.DurationListConverter
import top.ltfan.knowmad.data.InstantConverter
import top.ltfan.knowmad.data.LocalDateConverter
import top.ltfan.knowmad.data.TimeZoneConverter
import top.ltfan.knowmad.data.UuidConverter

@Database(
    entities = [SemesterEntity::class, CourseEntity::class, EventEntity::class],
    version = 1,
)
@TypeConverters(
    UuidConverter::class,
    LocalDateConverter::class,
    TimeZoneConverter::class,
    InstantConverter::class,
    DurationListConverter::class,
)
abstract class ScheduleDatabase : RoomDatabase() {
    abstract fun dao(): ScheduleDao

    companion object : DatabaseCompanion<ScheduleDatabase> {
        override val databaseName = "schedule"

        context(application: Application)
        override fun buildDatabase() = databaseBuilder(
            application,
            ScheduleDatabase::class.java,
            databaseName,
        ).build()
    }
}
