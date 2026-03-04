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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
    }.let {
        if (it != null) return@let
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
