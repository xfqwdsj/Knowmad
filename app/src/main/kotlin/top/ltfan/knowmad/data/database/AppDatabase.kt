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

package top.ltfan.knowmad.data.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import top.ltfan.knowmad.data.AppDatabaseConverters
import top.ltfan.knowmad.data.DatabaseCompanion
import top.ltfan.knowmad.data.chat.ChatDao
import top.ltfan.knowmad.data.chat.ConversationEntity
import top.ltfan.knowmad.data.chat.MessageBranchSelectionEntity
import top.ltfan.knowmad.data.chat.MessageCodeResultEntity
import top.ltfan.knowmad.data.chat.MessageEntity
import top.ltfan.knowmad.data.chat.MessageFileCrossRef
import top.ltfan.knowmad.data.chat.MessageFtsEntity
import top.ltfan.knowmad.data.database.callback.RecurrenceRuleCleanup
import top.ltfan.knowmad.data.file.FileDao
import top.ltfan.knowmad.data.file.FileEntity
import top.ltfan.knowmad.data.geo.GeoDao
import top.ltfan.knowmad.data.geo.NodeEntity
import top.ltfan.knowmad.data.geo.NodeFtsEntity
import top.ltfan.knowmad.data.geo.PlaceEntity
import top.ltfan.knowmad.data.geo.PlaceFtsEntity
import top.ltfan.knowmad.data.geo.PlaceNodeCrossRef
import top.ltfan.knowmad.data.geo.RoadEntity
import top.ltfan.knowmad.data.geo.RoadFtsEntity
import top.ltfan.knowmad.data.llm.LLMConfigDao
import top.ltfan.knowmad.data.llm.LLMConfigEntity
import top.ltfan.knowmad.data.llm.LLMProviderConfigEntity
import top.ltfan.knowmad.data.schedule.CourseEntity
import top.ltfan.knowmad.data.schedule.CourseFtsEntity
import top.ltfan.knowmad.data.schedule.EventEntity
import top.ltfan.knowmad.data.schedule.EventFtsEntity
import top.ltfan.knowmad.data.schedule.EventTombstoneEntity
import top.ltfan.knowmad.data.schedule.RecurrenceRuleEntity
import top.ltfan.knowmad.data.schedule.RecurrenceRuleSummaryEntity
import top.ltfan.knowmad.data.schedule.RecurrenceRuleSummaryFtsEntity
import top.ltfan.knowmad.data.schedule.ScheduleDao
import top.ltfan.knowmad.data.schedule.SemesterEntity
import top.ltfan.knowmad.data.schedule.SemesterFtsEntity
import top.ltfan.knowmad.data.suggestion.PendingSuggestionEntity
import top.ltfan.knowmad.data.suggestion.SuggestionDao
import top.ltfan.knowmad.data.suggestion.SuggestionEntity
import top.ltfan.knowmad.data.suggestion.SuggestionFtsEntity

@Database(
    entities = [
        LLMProviderConfigEntity::class,
        LLMConfigEntity::class,
        SemesterEntity::class,
        SemesterFtsEntity::class,
        RecurrenceRuleEntity::class,
        RecurrenceRuleSummaryEntity::class,
        RecurrenceRuleSummaryFtsEntity::class,
        CourseEntity::class,
        CourseFtsEntity::class,
        EventEntity::class,
        EventTombstoneEntity::class,
        EventFtsEntity::class,
        FileEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        MessageBranchSelectionEntity::class,
        MessageFileCrossRef::class,
        MessageCodeResultEntity::class,
        MessageFtsEntity::class,
        PendingSuggestionEntity::class,
        SuggestionEntity::class,
        SuggestionFtsEntity::class,
        PlaceEntity::class,
        PlaceFtsEntity::class,
        NodeEntity::class,
        NodeFtsEntity::class,
        PlaceNodeCrossRef::class,
        RoadEntity::class,
        RoadFtsEntity::class,
    ],
    version = 260301,
    autoMigrations = [
        AutoMigration(from = 260200, to = 260201),
        AutoMigration(from = 260300, to = 260301),
    ],
)
@TypeConverters(AppDatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun llmConfigDao(): LLMConfigDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun fileDao(): FileDao
    abstract fun chatDao(): ChatDao
    abstract fun suggestionDao(): SuggestionDao
    abstract fun geoDao(): GeoDao

    companion object : DatabaseCompanion<AppDatabase> {
        override val databaseName = "db"

        private val lock = Any()

        private var _instance: AppDatabase? = null

        context(context: Context)
        override fun get() = synchronized(lock) {
            _instance ?: Room.databaseBuilder(
                context = context.applicationContext,
                klass = AppDatabase::class.java,
                name = databaseName,
            ).buildAppDatabase().also {
                _instance = it
            }
        }

        @Suppress("NOTHING_TO_INLINE")
        inline fun Builder<AppDatabase>.buildAppDatabase() = apply {
            addCallback(RecurrenceRuleCleanup)
            addMigrations(Migration260201To260300)
        }.build()

        val Context.appDatabase inline get() = get()
    }
}
