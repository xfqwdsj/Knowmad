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

import android.app.Application
import androidx.room.Database
import androidx.room.Room.databaseBuilder
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import top.ltfan.knowmad.data.AppDatabaseConverters
import top.ltfan.knowmad.data.DatabaseCompanion
import top.ltfan.knowmad.data.chat.ChatDao
import top.ltfan.knowmad.data.chat.ConversationEntity
import top.ltfan.knowmad.data.chat.MessageBranchSelectionEntity
import top.ltfan.knowmad.data.chat.MessageEntity
import top.ltfan.knowmad.data.chat.MessageFileCrossRef
import top.ltfan.knowmad.data.chat.MessageFtsEntity
import top.ltfan.knowmad.data.file.FileDao
import top.ltfan.knowmad.data.file.FileEntity
import top.ltfan.knowmad.data.llm.LLMConfigDao
import top.ltfan.knowmad.data.llm.LLMConfigEntity
import top.ltfan.knowmad.data.llm.LLMProviderConfigEntity
import top.ltfan.knowmad.data.schedule.CourseEntity
import top.ltfan.knowmad.data.schedule.CourseFtsEntity
import top.ltfan.knowmad.data.schedule.EventEntity
import top.ltfan.knowmad.data.schedule.EventFtsEntity
import top.ltfan.knowmad.data.schedule.RecurrenceRuleEntity
import top.ltfan.knowmad.data.schedule.ScheduleDao
import top.ltfan.knowmad.data.schedule.SemesterEntity
import top.ltfan.knowmad.data.schedule.SemesterFtsEntity

@Database(
    entities = [
        LLMProviderConfigEntity::class,
        LLMConfigEntity::class,
        SemesterEntity::class,
        SemesterFtsEntity::class,
        RecurrenceRuleEntity::class,
        CourseEntity::class,
        CourseFtsEntity::class,
        EventEntity::class,
        EventFtsEntity::class,
        FileEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        MessageBranchSelectionEntity::class,
        MessageFileCrossRef::class,
        MessageFtsEntity::class,
    ],
    version = 1,
)
@TypeConverters(AppDatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun llmConfigDao(): LLMConfigDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun fileDao(): FileDao
    abstract fun chatDao(): ChatDao

    companion object : DatabaseCompanion<AppDatabase> {
        override val databaseName = "db"

        private val RecurrenceRuleCleanup = object : Callback() {
            val ReferencedEntities = listOf(
                "CourseEntity",
                "EventEntity",
            )

            val Triggers = listOf(
                "DELETE",
                "UPDATE OF recurrenceRuleId",
            )

            override fun onOpen(db: SupportSQLiteDatabase) {
                for (currentEntity in ReferencedEntities) {
                    for (trigger in Triggers) {
                        db.execSQL(
                            buildString {
                                val suffix = trigger.replace(" ", "_")
                                val name = "trg_${currentEntity}_recurrence_rule_cleanup_$suffix"
                                appendLine("CREATE TRIGGER IF NOT EXISTS `$name`")
                                appendLine("AFTER $trigger ON `$currentEntity`")
                                appendLine("WHEN OLD.recurrenceRuleId IS NOT NULL")
                                if (trigger.startsWith("UPDATE")) {
                                    appendLine("AND (NEW.recurrenceRuleId IS NULL OR NEW.recurrenceRuleId != OLD.recurrenceRuleId)")
                                }

                                appendLine("BEGIN")
                                appendLine("DELETE FROM RecurrenceRuleEntity")
                                appendLine("WHERE id = OLD.recurrenceRuleId")
                                for (entity in ReferencedEntities) {
                                    appendLine("AND NOT EXISTS (")
                                    appendLine("SELECT 1 FROM `$entity`")
                                    appendLine("WHERE recurrenceRuleId = OLD.recurrenceRuleId")
                                    appendLine(")")
                                }
                                appendLine(";")
                                appendLine("END;")
                            },
                        )
                    }
                }
            }
        }

        context(application: Application)
        override fun buildDatabase() =
            databaseBuilder(
                application,
                AppDatabase::class.java,
                databaseName,
            )
                .addCallback(RecurrenceRuleCleanup)
                .build()
    }
}
