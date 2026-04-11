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
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.database.AppDatabase.Companion.appDatabase
import top.ltfan.knowmad.notification.ClassProgressReceiver.Companion.rescheduleClassProgressNotification
import top.ltfan.knowmad.notification.ClassProgressReceiver.Companion.scheduleClassProgressNotification
import top.ltfan.knowmad.notification.ClassProgressReceiver.Companion.scheduleClassProgressNotificationScheduling
import top.ltfan.knowmad.ui.util.format
import top.ltfan.knowmad.util.Cbor
import top.ltfan.knowmad.util.Logger
import kotlin.time.Clock
import kotlin.time.Duration
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
            context.scheduleClassProgressNotification(
                leadTime = data.leadTime,
                horizon = data.horizon,
                updateInterval = data.updateInterval,
            )
            context.scheduleClassProgressNotificationScheduling(
                leadTime = data.leadTime,
                horizon = data.horizon,
                updateInterval = data.updateInterval,
                runImmediately = false,
            )
            return Result.success()
        }

        if (data !is Show) {
            logger.error { "Invalid data type for ClassProgressWorker: expected Show, got ${data::class}" }
            return Result.failure()
        }

        val now = Clock.System.now()

        val dao = context.appDatabase.scheduleDao()

        val event = dao.getEventById(data.eventId) ?: run {
            logger.error { "No event found with ID ${data.eventId} for ClassProgressWorker" }
            return Result.failure()
        }

        if (event !is Course) {
            logger.error { "Event with ID ${data.eventId} is not a Course, cannot show class progress notification" }
            return Result.failure()
        }

        val currentPeriod = ((now - event.startTime) / data.updateInterval).toInt()
        val nextUpdateTime = event.startTime + (currentPeriod + 1) * data.updateInterval

        if (now > event.endTime) {
            val time = now - event.endTime
            if (time > data.stayDuration) {
                logger.debug { "Class with ID ${data.eventId} ended, canceling notification" }
                context.cancelClassProgressNotification(data.eventId)
                return Result.success()
            }

            context.rescheduleClassProgressNotification(
                eventId = data.eventId,
                endThreshold = data.endThreshold,
                stayDuration = data.stayDuration,
                time = nextUpdateTime,
                updateInterval = data.updateInterval,
            )

            val notification = ClassProgressNotification(
                eventId = data.eventId,
                status = context.getString(R.string.schedule_class_status_short_label_ended),
                name = event.name,
                time = time.format(enableSeconds = false),
                location = event.location,
                progress = 100,
            )
            context.showClassProgressNotification(notification)
            return Result.success()
        }

        context.rescheduleClassProgressNotification(
            eventId = data.eventId,
            endThreshold = data.endThreshold,
            stayDuration = data.stayDuration,
            time = nextUpdateTime,
            updateInterval = data.updateInterval,
        )

        if (now < event.startTime) {
            val time = event.startTime - now
            val notification = ClassProgressNotification(
                eventId = data.eventId,
                status = context.getString(R.string.schedule_class_status_short_label_close_to_start),
                name = event.name,
                time = time.format(enableSeconds = false),
                location = event.location,
                progress = 0,
            )
            context.showClassProgressNotification(notification)
            return Result.success()
        }

        val started = now - event.startTime
        val remaining = event.endTime - now
        val totalDuration = event.endTime - event.startTime
        val progress = if (totalDuration > ZERO) {
            ((started / totalDuration) * 100).toInt().coerceIn(0, 100)
        } else 100

        if (remaining <= data.endThreshold) {
            val notification = ClassProgressNotification(
                eventId = data.eventId,
                status = context.getString(R.string.schedule_class_status_short_label_close_to_end),
                name = event.name,
                time = remaining.format(enableSeconds = false),
                location = event.location,
                progress = progress,
            )
            context.showClassProgressNotification(notification)
            return Result.success()
        }

        val notification = ClassProgressNotification(
            eventId = data.eventId,
            status = context.getString(R.string.schedule_class_status_short_label_in_progress),
            name = event.name,
            time = started.format(enableSeconds = false),
            location = event.location,
            progress = progress,
        )
        context.showClassProgressNotification(notification)
        return Result.success()
    }

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
        data class Schedule(
            val leadTime: Duration,
            val horizon: Duration,
            val updateInterval: Duration,
        ) : Data

        @Serializable
        data class Show(
            val eventId: Uuid,
            val endThreshold: Duration,
            val stayDuration: Duration,
            val updateInterval: Duration,
        ) : Data
    }
}
