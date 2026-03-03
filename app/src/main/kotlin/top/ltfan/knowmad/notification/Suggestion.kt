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
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.R
import top.ltfan.knowmad.notification.SuggestionRequestReceiver.Companion.scheduleNextSuggestionDowngrading
import top.ltfan.knowmad.util.Logger

private val logger = Logger("NextSuggestion")

@Serializable
data class NextSuggestionNotification(
    val capsuleTitle: String,
    val notificationTitle: String,
    val notificationContent: String,
)

private val NotificationId = NextSuggestionNotification::class.java.name.hashCode()

val Context.nextSuggestionGenerationPendingIntent
    inline get() = PendingIntentCompat.getBroadcast(
        applicationContext,
        0,
        Intent(applicationContext, SuggestionRequestReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT,
        false,
    )

fun Context.scheduleNextSuggestionGeneration() {
    val context = applicationContext

    val alarmManager = context.getSystemService<AlarmManager>() ?: run {
        logger.error { "Failed to get AlarmManager for scheduling next suggestion generation" }
        return
    }

    if (!AlarmManagerCompat.canScheduleExactAlarms(alarmManager)) {
        logger.warn { "Cannot schedule exact alarms, skipping scheduling next suggestion generation" }
        return
    }

    val pendingIntent = context.nextSuggestionGenerationPendingIntent ?: run {
        logger.error { "Failed to create pending intent for scheduling next suggestion generation" }
        return
    }

    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 7)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        if (before(Calendar.getInstance())) {
            add(Calendar.DATE, 1)
        }
    }

    alarmManager.setExact(
        AlarmManager.RTC_WAKEUP,
        calendar.timeInMillis,
        pendingIntent,
    )
}

fun Context.showNextSuggestionNotification(suggestion: NextSuggestionNotification) {
    createAiNotificationChannel()

    val notification = NotificationCompat.Builder(this, AiMessageChannelId).apply {
        setSmallIcon(R.drawable.ic_launcher_foreground)
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
        scheduleNextSuggestionDowngrading(suggestion)
    }?.let {
        logger.warn { "Failed to show next suggestion notification due to missing permission" }
    }
}

fun Context.downgradeNextSuggestionNotification(suggestion: NextSuggestionNotification) {
    createAiNotificationChannel()

    val notification = NotificationCompat.Builder(this, AiMessageChannelId).apply {
        setSmallIcon(R.drawable.ic_launcher_foreground)
        setContentTitle(suggestion.capsuleTitle)
        setSubText(suggestion.notificationTitle)
        setContentText(suggestion.notificationContent)
        setStyle(NotificationCompat.BigTextStyle().bigText(suggestion.notificationContent))
        setOngoing(false)
        setAutoCancel(true)
        setRequestPromotedOngoing(false)
        setOnlyAlertOnce(true)
    }.build()

    checkedNotificationPermission {
        NotificationManagerCompat.from(this).notify(NotificationId, notification)
    }
}
