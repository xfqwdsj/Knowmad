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
import top.ltfan.knowmad.MainActivity.Companion.getViewSuggestionPendingIntent
import top.ltfan.knowmad.R
import top.ltfan.knowmad.notification.SuggestionRequestReceiver.Companion.getSuggestionDowngradingPendingIntent
import top.ltfan.knowmad.notification.SuggestionRequestReceiver.Companion.scheduleNextSuggestionDowngrading
import top.ltfan.knowmad.util.Logger
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val logger = Logger("NextSuggestion")

@Serializable
data class NextSuggestionNotification(
    val capsuleTitle: String,
    val notificationTitle: String,
    val notificationContent: String,
    val notificationSummary: String? = null,
    val suggestedNextGenerationTime: Instant? = null,
    val suggestedNextGenerationPrompt: String? = null,
    val createdAt: Instant = Clock.System.now(),
)

private val NotificationId = Uuid.parse("019c0c33-1400-7480-87e0-f12641ae67f7").hashCode()

fun Context.showNextSuggestionNotification(
    suggestion: NextSuggestionNotification,
) {
    createAiNotificationChannel()

    val content = suggestion.notificationSummary ?: suggestion.notificationContent

    val notification = NotificationCompat.Builder(this, AiMessageChannelId).apply {
        setSmallIcon(R.drawable.ic_launcher_foreground)
        setContentTitle(suggestion.capsuleTitle)
        setSubText(suggestion.notificationTitle)
        setContentText(content)
        setStyle(NotificationCompat.BigTextStyle().bigText(content))
        setOngoing(true)
        setAutoCancel(false)
        setRequestPromotedOngoing(true)
        setWhen(suggestion.createdAt.toEpochMilliseconds())
        setContentIntent(getViewSuggestionPendingIntent(suggestion))
        addAction(
            R.drawable.keep_off_24px,
            getString(R.string.label_unpin),
            getSuggestionDowngradingPendingIntent(suggestion),
        )
    }.build()

    checkedNotificationPermission {
        NotificationManagerCompat.from(this).notify(NotificationId, notification)
        scheduleNextSuggestionDowngrading(suggestion)
    } ?: logger.warn { "Failed to show suggestion notification due to missing permission" }
}

fun Context.downgradeNextSuggestionNotification(
    suggestion: NextSuggestionNotification,
) {
    createAiNotificationChannel()

    val content = suggestion.notificationSummary ?: suggestion.notificationContent

    val notification = NotificationCompat.Builder(this, AiMessageChannelId).apply {
        setSmallIcon(R.drawable.ic_launcher_foreground)
        setContentTitle(suggestion.capsuleTitle)
        setSubText(suggestion.notificationTitle)
        setContentText(content)
        setStyle(NotificationCompat.BigTextStyle().bigText(content))
        setOngoing(false)
        setAutoCancel(true)
        setRequestPromotedOngoing(false)
        setOnlyAlertOnce(true)
        setWhen(suggestion.createdAt.toEpochMilliseconds())
        setContentIntent(getViewSuggestionPendingIntent(suggestion))
    }.build()

    checkedNotificationPermission {
        NotificationManagerCompat.from(this).notify(NotificationId, notification)
    } ?: logger.warn { "Failed to downgrade suggestion notification due to missing permission" }
}
