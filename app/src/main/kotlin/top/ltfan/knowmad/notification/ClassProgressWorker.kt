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

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.database.AppDatabase.Companion.appDatabase
import top.ltfan.knowmad.data.task.ClassProgressConfiguration
import top.ltfan.knowmad.notification.ClassProgressReceiver.Companion.scheduleClassProgressNotification
import top.ltfan.knowmad.notification.ClassProgressReceiver.Companion.scheduleClassProgressNotificationScheduling
import top.ltfan.knowmad.ui.util.format
import top.ltfan.knowmad.util.Cbor
import top.ltfan.knowmad.util.Logger
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.times
import kotlin.uuid.Uuid
import androidx.work.Data as WorkData

private val logger = Logger("ClassProgressWorker")

class ClassProgressWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext

        val data = inputData.getByteArray(DATA)?.let {
            Cbor.decodeFromByteArray<Data>(it)
        } ?: run {
            logger.error { "No valid input data found for ClassProgressWorker" }
            return Result.failure()
        }

        if (data is Schedule) {
            context.scheduleClassProgressNotification()
            context.scheduleClassProgressNotificationScheduling(runImmediately = false)
            return Result.success()
        }

        if (data !is Show) {
            logger.error { "Invalid data type for ClassProgressWorker: expected Show, got ${data::class}" }
            return Result.failure()
        }

        val notificationData = try {
            buildNotificationData(data) ?: run {
                logger.debug { "Class with ID ${data.eventId} ended, no need to show notification" }
                return Result.success()
            }
        } catch (_: Failure) {
            logger.debug { "Stopping ClassProgressWorker due to failure in building notification data" }
            return Result.failure()
        }

        context.withClassProgressNotification(notificationData) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                setForeground(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                setForeground(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            }

            while (true) {
                val newData = try {
                    buildNotificationData(data) ?: run {
                        logger.debug { "Class with ID ${data.eventId} ended, stopping worker" }
                        return Result.success()
                    }
                } catch (_: Failure) {
                    logger.debug { "Stopping ClassProgressWorker due to failure in building notification data" }
                    return Result.failure()
                }
                update(newData)
            }
        }

        return Result.failure()
    }

    private suspend fun buildNotificationData(
        data: Data.Show,
        now: Instant = Clock.System.now(),
    ): ClassProgressNotificationData? {
        val context = applicationContext

        val configuration = ClassProgressConfiguration.createDataStore(context).data.first()

        if (!configuration.enabled) {
            logger.debug { "Class progress notification is disabled in configuration, skipping notification update" }
            throw Failure()
        }

        val dao = context.appDatabase.scheduleDao()

        val event = dao.getEventById(data.eventId) ?: run {
            logger.error { "No event found with ID ${data.eventId} for ClassProgressWorker" }
            throw Failure()
        }

        if (event !is Course) {
            logger.error { "Event with ID ${data.eventId} is not a Course, cannot show class progress notification" }
            throw Failure()
        }

        val validTimeRange =
            (event.startTime - configuration.leadTime)..(event.endTime + configuration.stayDuration)
        if (now !in validTimeRange) {
            logger.debug { "Current time $now is outside of valid range $validTimeRange for event ${data.eventId}, skipping notification update" }
            throw Failure()
        }

        val currentPeriod = ((now - event.startTime) / configuration.updateInterval).toInt()
        val delayTime = (event.startTime + currentPeriod * configuration.updateInterval) - now
        delay(delayTime)

        return when {
            now > event.endTime -> {
                val time = now - event.endTime
                if (time > configuration.stayDuration) return null

                ClassProgressNotificationData(
                    eventId = data.eventId,
                    status = context.getString(R.string.schedule_class_status_label_ended),
                    statusShort = context.getString(R.string.schedule_class_status_short_label_ended),
                    name = event.name,
                    time = time.format(width = NARROW, enableSeconds = false),
                    location = event.location,
                    progress = 100,
                    showProgressOutside = false,
                )
            }

            now < event.startTime -> {
                val time = event.startTime - now
                ClassProgressNotificationData(
                    eventId = data.eventId,
                    status = context.getString(R.string.schedule_class_status_label_close_to_start),
                    statusShort = context.getString(R.string.schedule_class_status_short_label_close_to_start),
                    name = event.name,
                    time = time.format(width = NARROW, enableSeconds = false),
                    location = event.location,
                    showLocationOutside = true,
                    progress = 0,
                    showProgressOutside = false,
                )
            }

            else -> {
                val started = now - event.startTime
                val remaining = event.endTime - now
                val totalDuration = event.endTime - event.startTime
                val progress = if (totalDuration > ZERO) {
                    ((started / totalDuration) * 100).toInt().coerceIn(0, 100)
                } else 100

                if (remaining <= configuration.endThreshold) {
                    ClassProgressNotificationData(
                        eventId = data.eventId,
                        status = context.getString(R.string.schedule_class_status_label_close_to_end),
                        statusShort = context.getString(R.string.schedule_class_status_short_label_close_to_end),
                        name = event.name,
                        time = remaining.format(width = NARROW, enableSeconds = false),
                        location = event.location,
                        progress = progress,
                    )
                } else {
                    ClassProgressNotificationData(
                        eventId = data.eventId,
                        status = context.getString(R.string.schedule_class_status_label_in_progress),
                        statusShort = context.getString(R.string.schedule_class_status_short_label_in_progress),
                        name = event.name,
                        time = started.format(width = NARROW, enableSeconds = false),
                        location = event.location,
                        progress = progress,
                    )
                }
            }
        }
    }

    private class Failure : Throwable()

    companion object {
        const val DATA = "DATA"

        fun buildRequest(
            data: Data,
        ) = OneTimeWorkRequestBuilder<ClassProgressWorker>().apply {
            val data = WorkData.Builder().apply {
                putByteArray(DATA, Cbor.encodeToByteArray(data))
            }.build()
            setInputData(data)
        }.build()
    }

    @Serializable
    sealed interface Data {
        @Serializable
        data object Schedule : Data

        @Serializable
        data class Show(val eventId: Uuid) : Data
    }
}
