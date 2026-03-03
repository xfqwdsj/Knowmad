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
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.getSystemService
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.R
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

@Serializable
data class NextSuggestionNotification(
    val capsuleTitle: String,
    val notificationTitle: String,
    val notificationContent: String,
)

private val NotificationId = NextSuggestionNotification::class.java.name.hashCode()

fun Context.scheduleNextSuggestionGeneration() {
    val alarmManager = getSystemService<AlarmManager>() ?: return

    val pendingIntent = PendingIntentCompat.getBroadcast(
        applicationContext,
        0,
        Intent(applicationContext, SuggestionRequestReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT,
        false,
    ) ?: return

    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 7)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        if (before(Calendar.getInstance())) {
            add(Calendar.DATE, 1)
        }
    }

    if (!AlarmManagerCompat.canScheduleExactAlarms(alarmManager)) return

    alarmManager.setExact(
        AlarmManager.RTC_WAKEUP,
        calendar.timeInMillis,
        pendingIntent,
    )
}

fun Context.showNextSuggestionNotification(suggestion: NextSuggestionNotification) {
    createAiNotificationChannel()

    val notification = NotificationCompat.Builder(this, AiMessageChannelId).apply {
        setSmallIcon(R.mipmap.ic_launcher)
        setContentTitle(suggestion.capsuleTitle)
        setSubText(suggestion.notificationTitle)
        setContentText(suggestion.notificationContent)
        setStyle(NotificationCompat.BigTextStyle().bigText(suggestion.notificationContent))
        setOngoing(true)
        setAutoCancel(false)
        setRequestPromotedOngoing(true)
    }.build()

    checkedNotificationPermission {
        NotificationManagerCompat.from(this).notify(NotificationId, notification)
        applicationContext.enqueueCancelLiveUpdateWork(suggestion)
    }
}

private fun Context.enqueueCancelLiveUpdateWork(suggestion: NextSuggestionNotification) {
    WorkManager.getInstance(this).enqueueUniqueWork(
        uniqueWorkName = "top.ltfan.knowmad.notification.CancelLiveUpdateWorker_$NotificationId",
        existingWorkPolicy = REPLACE,
        request = OneTimeWorkRequestBuilder<CancelLiveUpdateWorker>().apply {
            setInitialDelay(1.hours.toJavaDuration())
            setInputData(
                workDataOf(
                    CancelLiveUpdateWorker.TITLE to suggestion.notificationTitle,
                    CancelLiveUpdateWorker.CONTENT to suggestion.notificationContent,
                ),
            )
            setConstraints(NONE)
        }.build(),
    )
}

class CancelLiveUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {
    override fun doWork(): Result {
        val context = applicationContext

        val title = inputData.getString(TITLE) ?: ""
        val content = inputData.getString(CONTENT) ?: ""

        val manager = NotificationManagerCompat.from(context)

        val notification = NotificationCompat.Builder(context, AiMessageChannelId).apply {
            setSmallIcon(R.mipmap.ic_launcher)
            setContentTitle(title)
            setContentText(content)
            setStyle(NotificationCompat.BigTextStyle().bigText(content))
            setOngoing(false)
        }.build()

        context.checkedNotificationPermission {
            manager.notify(NotificationId, notification)
        }

        return Result.success()
    }

    companion object {
        const val TITLE = "title"
        const val CONTENT = "content"
    }
}
