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

package top.ltfan.knowmad.data.database

import android.app.Application
import androidx.room.Database
import androidx.room.Room.databaseBuilder
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import top.ltfan.knowmad.data.AppDatabaseConverters
import top.ltfan.knowmad.data.DatabaseCompanion
import top.ltfan.knowmad.data.llm.LLMConfigDao
import top.ltfan.knowmad.data.llm.LLMConfigEntity
import top.ltfan.knowmad.data.llm.LLMProviderConfigEntity
import top.ltfan.knowmad.data.schedule.CourseEntity
import top.ltfan.knowmad.data.schedule.EventEntity
import top.ltfan.knowmad.data.schedule.ScheduleDao
import top.ltfan.knowmad.data.schedule.SemesterEntity

@Database(
    entities = [
        LLMProviderConfigEntity::class,
        LLMConfigEntity::class,
        SemesterEntity::class,
        CourseEntity::class,
        EventEntity::class
    ],
    version = 1,
)
@TypeConverters(AppDatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun llmConfigDao(): LLMConfigDao
    abstract fun scheduleDao(): ScheduleDao

    companion object : DatabaseCompanion<AppDatabase> {
        override val databaseName = "db"

        context(application: Application)
        override fun buildDatabase() = databaseBuilder(
            application,
            AppDatabase::class.java,
            databaseName,
        ).build()
    }
}
