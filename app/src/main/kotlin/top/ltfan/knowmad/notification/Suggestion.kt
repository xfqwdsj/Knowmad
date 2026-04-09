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
import android.os.Bundle
import androidx.core.app.NotificationCompat
import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.MainActivity.Companion.getViewSuggestionPendingIntent
import top.ltfan.knowmad.R
import top.ltfan.knowmad.notification.SuggestionRequestReceiver.Companion.getSuggestionDowngradingPendingIntent
import top.ltfan.knowmad.notification.SuggestionRequestReceiver.Companion.scheduleNextSuggestionDowngrading
import top.ltfan.knowmad.ui.theme.primaryDark
import top.ltfan.knowmad.ui.util.toHexString
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
    val content = suggestion.notificationSummary ?: suggestion.notificationContent

    val notification = aiNotificationChannel.withNotificationBuilder { channel ->
        val downgradingIntent = getSuggestionDowngradingPendingIntent(suggestion) ?: run {
            logger.warn { "Failed to create downgrading intent for suggestion: $suggestion" }
            return
        }

        setSmallIcon(R.drawable.ic_logo)
        setContentTitle(suggestion.capsuleTitle)
        setShortCriticalText(suggestion.capsuleTitle)
        setSubText(suggestion.notificationTitle)
        setStyle(NotificationCompat.BigTextStyle().bigText(content))
        setOngoing(true)
        setAutoCancel(false)
        setRequestPromotedOngoing(true)
        setOnlyAlertOnce(true)
        setWhen(suggestion.createdAt.toEpochMilliseconds())
        setContentIntent(getViewSuggestionPendingIntent(suggestion))
        addAction(
            R.drawable.keep_off_24px,
            getString(R.string.label_unpin),
            downgradingIntent,
        )

        val context = this@showNextSuggestionNotification.applicationContext

        val hyperIsland = HyperIslandNotification.Builder(
            context = context,
            businessName = channel.id,
            ticker = channel.name.toString(),
        ).apply {
            val picKeySmallIsland = "small_island"

            addPicture(HyperPicture(picKeySmallIsland, context, R.drawable.ic_logo))

            val actionKeyUnpin = "action_unpin"

            addAction(
                HyperAction(
                    key = actionKeyUnpin,
                    title = getString(R.string.label_unpin),
                    pendingIntent = downgradingIntent,
                    actionIntentType = 2,
                ),
            )

            setIslandConfig(
                highlightColor = "#" + primaryDark.toHexString(),
            )

            setSmallIsland(picKeySmallIsland)
            setBigIslandInfo(
                left = ImageTextInfoLeft(
                    picInfo = PicInfo(pic = picKeySmallIsland),
                    textInfo = TextInfo(
                        title = getString(R.string.app_name),
                        showHighlightColor = true,
                    ),
                ),
                centerText = TextInfo(title = suggestion.capsuleTitle),
            )
            setBaseInfo(
                title = suggestion.notificationTitle,
                content = content,
                type = 2,
                actionKeys = listOf(actionKeyUnpin),
            )

            setAodConfig(title = suggestion.capsuleTitle)
        }

        addExtras(hyperIsland.buildResourceBundle())
        addExtras(
            Bundle().apply {
                putString("miui.focus.param", hyperIsland.buildJsonParam())
            },
        )
    }.build()

    checkedNotificationPermission {
        notification.notifyCompat(NotificationId)
        scheduleNextSuggestionDowngrading(suggestion)
    } ?: logger.warn { "Failed to show suggestion notification due to missing permission" }
}

fun Context.downgradeNextSuggestionNotification(
    suggestion: NextSuggestionNotification,
) {
    val content = suggestion.notificationSummary ?: suggestion.notificationContent

    val notification = aiNotificationChannel.withNotificationBuilder {
        setSmallIcon(R.drawable.ic_logo)
        setContentTitle(suggestion.capsuleTitle)
        setShortCriticalText(suggestion.capsuleTitle)
        setSubText(suggestion.notificationTitle)
        setStyle(NotificationCompat.BigTextStyle().bigText(content))
        setOngoing(false)
        setAutoCancel(false)
        setRequestPromotedOngoing(false)
        setOnlyAlertOnce(true)
        setWhen(suggestion.createdAt.toEpochMilliseconds())
        setContentIntent(getViewSuggestionPendingIntent(suggestion))
    }.build()

    checkedNotificationPermission {
        notification.notifyCompat(NotificationId)
    } ?: logger.warn { "Failed to downgrade suggestion notification due to missing permission" }
}
