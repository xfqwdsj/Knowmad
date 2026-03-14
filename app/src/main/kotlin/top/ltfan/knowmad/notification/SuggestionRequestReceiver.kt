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

package top.ltfan.knowmad.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.os.SystemClock
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.getSystemService
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import top.ltfan.knowmad.agent.task.suggestion.GenerateNextSuggestionWorker
import top.ltfan.knowmad.data.database.AppDatabase.Companion.appDatabase
import top.ltfan.knowmad.data.suggestion.PendingSuggestionEntity
import top.ltfan.knowmad.data.suggestion.SuggestionDao
import top.ltfan.knowmad.util.Cbor
import top.ltfan.knowmad.util.Logger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

class SuggestionRequestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        intent.extractDowngradingInfo()?.let { suggestion ->
            context.cancelScheduledSuggestionDowngrading()
            logger.debug { "Received downgrading request for suggestion: $suggestion" }
            context.downgradeNextSuggestionNotification(suggestion)
            return
        }

        val context = context.applicationContext
        context.enqueueSuggestionRequestHandling(intent)
    }

    companion object {
        private val logger = Logger("SuggestionRequestReceiver")

        const val ACTION_GENERATE = "GENERATE"

        const val ACTION_DOWNGRADE = "DOWNGRADE"

        const val EXTRA_PROMPT = "EXTRA_PROMPT"

        const val EXTRA_PENDING_SUGGESTION_ID = "EXTRA_PENDING_SUGGESTION_ID"

        const val EXTRA_NOTIFICATION = "EXTRA_NOTIFICATION"

        const val DATA_ACTION = "DATA_ACTION"

        const val DATA_PROMPT = "DATA_PROMPT"

        const val DATA_PENDING_SUGGESTION_ID = "DATA_PENDING_SUGGESTION_ID"

        private val Context.defaultPrompt
            inline get() = getString(GenerateNextSuggestionWorker.DefaultPromptId)

        private suspend inline fun SuggestionDao.cleanupExpiredPendingSuggestions(
            now: Instant = Clock.System.now(),
        ): Int {
            val deleted = deletePendingSuggestionsBefore(now)
            if (deleted > 0) {
                logger.debug { "Deleted $deleted expired pending suggestions before continuing" }
            }
            return deleted
        }

        @Suppress("NOTHING_TO_INLINE")
        private inline fun Context.getGenerationIntent(
            pendingSuggestionId: Uuid?,
            prompt: String? = defaultPrompt,
            @PendingIntentCompat.Flags flags: Int = PendingIntent.FLAG_UPDATE_CURRENT,
        ) = PendingIntentCompat.getBroadcast(
            applicationContext,
            0,
            Intent(applicationContext, SuggestionRequestReceiver::class.java).apply {
                action = ACTION_GENERATE
                putExtra(EXTRA_PENDING_SUGGESTION_ID, pendingSuggestionId?.toString())
                putExtra(EXTRA_PROMPT, prompt)
            },
            flags,
            false,
        )

        private fun Context.scheduleSuggestionGenerationAlarm(
            pendingSuggestion: PendingSuggestionEntity,
            @PendingIntentCompat.Flags flags: Int = PendingIntent.FLAG_UPDATE_CURRENT,
        ) {
            val context = applicationContext

            val alarmManager = context.getSystemService<AlarmManager>() ?: run {
                logger.error { "Failed to get AlarmManager for scheduling next suggestion generation" }
                return
            }

            if (!AlarmManagerCompat.canScheduleExactAlarms(alarmManager)) {
                logger.warn { "Cannot schedule exact alarms, skipping scheduling next suggestion generation" }
                return
            }

            val pendingIntent = context.getGenerationIntent(
                pendingSuggestionId = pendingSuggestion.id,
                prompt = pendingSuggestion.prompt ?: defaultPrompt,
                flags = flags,
            ) ?: run {
                logger.warn { "Failed to create pending intent for scheduling next suggestion generation" }
                return
            }

            logger.debug { "Scheduling next suggestion generation at: ${pendingSuggestion.expected}" }

            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                pendingSuggestion.expected.toEpochMilliseconds(),
                pendingIntent,
            )
        }

        suspend fun Context.scheduleNextSuggestionGeneration(
            prompt: String? = null,
            override: Boolean = true,
            @PendingIntentCompat.Flags flags: Int = PendingIntent.FLAG_UPDATE_CURRENT,
            buildCalendar: Calendar.() -> Unit = {
                set(Calendar.HOUR_OF_DAY, 7)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DATE, 1)
                }
            },
        ) {
            val context = applicationContext
            val dao = context.appDatabase.suggestionDao()

            if (!override) {
                val existingPending = dao.getNextPendingSuggestion()
                if (existingPending != null) {
                    logger.debug { "Pending suggestion already exists and override is false, keeping existing schedule at ${existingPending.expected}" }
                    context.scheduleSuggestionGenerationAlarm(existingPending, flags)
                    return
                }
            }

            if (override) {
                val deleted = dao.deleteAllPendingSuggestions()
                if (deleted > 0) {
                    logger.debug { "Deleted $deleted pending suggestions before overriding schedule" }
                }
            }

            val prompt = prompt ?: defaultPrompt

            val calendar = Calendar.getInstance().apply(buildCalendar)
            val time = Instant.fromEpochMilliseconds(calendar.timeInMillis)
            val pendingSuggestion = PendingSuggestionEntity(
                expected = time,
                prompt = prompt,
            )
            dao.insertPendingSuggestion(pendingSuggestion)

            context.scheduleSuggestionGenerationAlarm(pendingSuggestion, flags)
        }

        fun Context.enqueueSuggestionRequestHandling(intent: Intent) {
            val context = applicationContext
            val data = Data.Builder().apply {
                putString(DATA_ACTION, intent.action)
                putString(DATA_PROMPT, intent.getStringExtra(EXTRA_PROMPT))
                putString(
                    DATA_PENDING_SUGGESTION_ID,
                    intent.getStringExtra(EXTRA_PENDING_SUGGESTION_ID),
                )
            }.build()

            val request = OneTimeWorkRequestBuilder<SuggestionRequestWorker>()
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }

        suspend fun Context.resolvePromptForGeneration(intent: Intent): String? {
            val context = applicationContext
            val dao = context.appDatabase.suggestionDao()
            val now = Clock.System.now()

            val pendingSuggestionId = intent.getStringExtra(EXTRA_PENDING_SUGGESTION_ID)?.let {
                Uuid.parseOrNull(it) ?: run {
                    logger.warn { "Failed to parse pending suggestion id: $it" }
                    null
                }
            }

            try {
                if (pendingSuggestionId != null) {
                    val pendingSuggestion =
                        dao.getPendingSuggestionById(pendingSuggestionId) ?: run {
                            logger.warn { "Pending suggestion not found for id: $pendingSuggestionId" }
                            return null
                        }

                    dao.deletePendingSuggestion(pendingSuggestionId)

                    return pendingSuggestion.prompt ?: defaultPrompt
                }

                if (intent.action != null && intent.action != ACTION_GENERATE) {
                    logger.warn { "Received intent with unsupported action for generation: ${intent.action}" }
                    return null
                }

                return intent.getStringExtra(EXTRA_PROMPT) ?: defaultPrompt
            } finally {
                dao.cleanupExpiredPendingSuggestions(now)
            }
        }

        @Suppress("NOTHING_TO_INLINE")
        inline fun Context.getSuggestionDowngradingPendingIntent(
            suggestion: NextSuggestionNotification?,
            @PendingIntentCompat.Flags flags: Int = PendingIntent.FLAG_UPDATE_CURRENT,
        ) = PendingIntentCompat.getBroadcast(
            applicationContext,
            1,
            Intent(applicationContext, SuggestionRequestReceiver::class.java).apply {
                action = ACTION_DOWNGRADE
                putExtra(EXTRA_NOTIFICATION, Cbor.encodeToByteArray(suggestion))
            },
            flags,
            false,
        )

        @Suppress("NOTHING_TO_INLINE")
        private inline fun Intent.extractDowngradingInfo(): NextSuggestionNotification? {
            if (action != ACTION_DOWNGRADE) {
                logger.debug { "Received intent with unsupported action: $action, ignoring" }
                return null
            }
            val notification = getByteArrayExtra(EXTRA_NOTIFICATION)
                ?.let { Cbor.decodeFromByteArray<NextSuggestionNotification>(it) }
                ?: run {
                    logger.warn { "Received downgrading request without notification data" }
                    return null
                }
            return notification
        }

        fun Context.scheduleNextSuggestionDowngrading(
            suggestion: NextSuggestionNotification,
            delay: Duration = 1.hours,
        ) {
            val context = applicationContext

            val alarmManager = context.getSystemService<AlarmManager>() ?: run {
                logger.error { "Failed to get AlarmManager for scheduling suggestion downgrading" }
                return
            }

            if (!AlarmManagerCompat.canScheduleExactAlarms(alarmManager)) {
                logger.warn { "Cannot schedule exact alarms, skipping scheduling suggestion downgrading" }
                return
            }

            val pendingIntent = context.getSuggestionDowngradingPendingIntent(suggestion) ?: run {
                logger.warn { "Failed to create PendingIntent for scheduling suggestion downgrading" }
                return
            }

            logger.debug { "Scheduling suggestion downgrading for suggestion: ${suggestion.notificationTitle} with delay: $delay" }

            alarmManager.setExact(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delay.inWholeMilliseconds,
                pendingIntent,
            )
        }

        fun Context.cancelScheduledSuggestionDowngrading() {
            val context = applicationContext

            val alarmManager = context.getSystemService<AlarmManager>() ?: run {
                logger.error { "Failed to get AlarmManager for canceling scheduled suggestion downgrading" }
                return
            }

            val pendingIntent = context.getSuggestionDowngradingPendingIntent(
                suggestion = null,
                flags = PendingIntent.FLAG_NO_CREATE,
            ) ?: run {
                logger.debug { "No existing PendingIntent for suggestion downgrading, nothing to cancel" }
                return
            }

            logger.debug { "Canceling scheduled suggestion downgrading" }
            alarmManager.cancel(pendingIntent)
        }
    }
}
