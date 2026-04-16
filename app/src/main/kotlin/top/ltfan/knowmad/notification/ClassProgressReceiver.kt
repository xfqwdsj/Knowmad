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
import androidx.compose.ui.util.fastForEach
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.getSystemService
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import top.ltfan.knowmad.data.database.AppDatabase.Companion.appDatabase
import top.ltfan.knowmad.data.schedule.Event
import top.ltfan.knowmad.data.task.ClassProgressConfiguration
import top.ltfan.knowmad.util.Logger
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val logger = Logger("ClassProgressReceiver")

class ClassProgressReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val context = context.applicationContext
        context.enqueueClassProgressNotificationHandling(intent)
    }

    companion object {
        const val ACTION_SCHEDULE = "SCHEDULE"
        const val ACTION_SHOW = "SHOW"

        const val EXTRA_EVENT_ID = "EXTRA_EVENT_ID"

        @Suppress("NOTHING_TO_INLINE")
        private inline fun Context.getShowPendingIntent(
            eventId: Uuid,
            @PendingIntentCompat.Flags flags: Int = PendingIntent.FLAG_UPDATE_CURRENT,
        ) = PendingIntentCompat.getBroadcast(
            applicationContext,
            eventId.hashCode(),
            Intent(applicationContext, ClassProgressReceiver::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_EVENT_ID, eventId.toString())
            },
            flags,
            false,
        )

        suspend fun Context.scheduleClassProgressNotificationScheduling(
            runImmediately: Boolean = true,
            @PendingIntentCompat.Flags flags: Int = PendingIntent.FLAG_UPDATE_CURRENT,
        ) {
            val context = applicationContext

            val configuration = ClassProgressConfiguration.createDataStore().data.first()

            if (!configuration.enabled) {
                logger.debug { "Class progress notification is disabled in configuration, skipping scheduling" }
                return
            }

            val intent = Intent(context, ClassProgressReceiver::class.java).apply {
                action = ACTION_SCHEDULE
            }

            if (runImmediately) {
                logger.debug { "Running class progress notification scheduling immediately" }
                context.enqueueClassProgressNotificationHandling(intent)
            }

            val alarmManager = context.getSystemService<AlarmManager>() ?: run {
                logger.error { "Failed to get AlarmManager for scheduling class progress notification scheduling" }
                return
            }

            if (!AlarmManagerCompat.canScheduleExactAlarms(alarmManager)) {
                logger.warn { "Cannot schedule exact alarms, skipping class progress notification scheduling" }
                return
            }

            val scheduledTime = configuration.scheduledUpdateTime
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, scheduledTime.hour)
                set(Calendar.MINUTE, scheduledTime.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DATE, 1)
                }
            }

            val pendingIntent = PendingIntentCompat.getBroadcast(
                context, 0, intent, flags, false,
            ) ?: run {
                logger.error { "Failed to create pending intent for class progress notification scheduling" }
                return
            }

            logger.debug { "Scheduling class progress notification scheduling at ${calendar.time}" }

            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent,
            )
        }

        suspend fun Context.scheduleClassProgressNotification(
            now: Instant = Clock.System.now(),
            @PendingIntentCompat.Flags flags: Int = PendingIntent.FLAG_UPDATE_CURRENT,
        ) {
            val context = applicationContext
            val configuration = ClassProgressConfiguration.createDataStore().data.first()

            if (!configuration.enabled) {
                logger.debug { "Class progress notification is disabled in configuration, skipping scheduling" }
                return
            }

            val dao = context.appDatabase.scheduleDao()

            val queryEndTime = now + configuration.schedulingHorizon

            val events = dao.getEventsInRange(now, queryEndTime)
                .filterIsInstance<Event.Course>()
                .ifEmpty {
                    logger.debug { "No upcoming course events found within threshold, skipping class progress notification scheduling" }
                    return
                }

            val alarmManager = context.getSystemService<AlarmManager>() ?: run {
                logger.error { "Failed to get AlarmManager for scheduling class progress notification" }
                return
            }

            if (!AlarmManagerCompat.canScheduleExactAlarms(alarmManager)) {
                logger.warn { "Cannot schedule exact alarms, skipping class progress notification scheduling" }
                return
            }

            events.fastForEach { event ->
                val eventId = event.id
                val scheduledTime = event.startTime - configuration.leadTime

                val showIntent = context.getShowPendingIntent(
                    eventId = eventId,
                    flags = flags,
                ) ?: run {
                    logger.warn { "Failed to create show intent for event $eventId, skipping scheduling notification" }
                    return@fastForEach
                }

                logger.debug { "Scheduling class progress notification for event $eventId at $scheduledTime" }

                // `setExact` will fire the alarm immediately if the scheduled time is in the past,
                // so we don't need to check if the scheduled time is before now.
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    scheduledTime.toEpochMilliseconds(),
                    showIntent,
                )
            }
        }

        fun Context.enqueueClassProgressNotificationHandling(intent: Intent) {
            val context = applicationContext

            val manager = WorkManager.getInstance(context)

            when (intent.action) {
                ACTION_SCHEDULE -> {
                    val data = ClassProgressWorker.Data.Schedule

                    val request = ClassProgressWorker.buildRequest(data)
                    manager.enqueueUniqueWork(
                        uniqueWorkName = "ClassProgressWorker_Schedule",
                        existingWorkPolicy = REPLACE,
                        request = request,
                    )
                }

                ACTION_SHOW -> {
                    val eventId = Uuid.parse(intent.getStringExtra(EXTRA_EVENT_ID) ?: return)

                    val data = ClassProgressWorker.Data.Show(eventId)

                    val request = ClassProgressWorker.buildRequest(data)
                    manager.enqueueUniqueWork(
                        uniqueWorkName = "ClassProgressWorker_Show_$eventId",
                        existingWorkPolicy = KEEP,
                        request = request,
                    )
                }

                else -> {
                    logger.warn { "Unknown action ${intent.action} received in ClassProgressReceiver, skipping" }
                    return
                }
            }
        }
    }
}
