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
import androidx.annotation.IntRange
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.CircularProgressInfo
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.ProgressTextInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.R
import top.ltfan.knowmad.ui.theme.primaryDark
import top.ltfan.knowmad.ui.util.toHexString
import kotlin.uuid.Uuid

@Serializable
data class ClassProgressNotification(
    val eventId: Uuid,
    val status: String,
    val statusShort: String,
    val name: String,
    val time: String,
    val location: String,
    val showLocationOutside: Boolean = false,
    @param:IntRange(from = 0, to = 100) val progress: Int,
    val showProgressOutside: Boolean = true,
    val suggestion: String? = null,
)

fun Context.showClassProgressNotification(
    notification: ClassProgressNotification,
) {
    val notificationId = notification.eventId.hashCode()

    val notification = classProgressNotificationChannel.withNotificationBuilder { channel ->
        val progressStyle = NotificationCompat.ProgressStyle().kotlinApply {
            setProgress(notification.progress)
            addProgressSegment(NotificationCompat.ProgressStyle.Segment(100))
        }

        setSmallIcon(R.drawable.ic_logo)
        setContentTitle("${notification.status} \u2022 ${notification.time} \u2022 ${notification.location}")
        setShortCriticalText(notification.statusShort)
        setContentText(notification.suggestion)
        setSubText(notification.name)
        setOngoing(true)
        setAutoCancel(false)
        setOnlyAlertOnce(true)

        val context = applicationContext

        // Hyper Island will fail if we set the ProgressStyle.
        // So we only set it when Hyper Island is not supported.
        if (!HyperIslandNotification.isSupported(context)) {
            setStyle(progressStyle)
            setRequestPromotedOngoing(true)
        }

        val hyperIsland = HyperIslandNotification.Builder(
            context = context,
            businessName = channel.id,
            ticker = notification.status,
        ).apply {
            val picKeySmallIsland = "small_island"

            addPicture(HyperPicture(picKeySmallIsland, context, R.drawable.ic_logo))

            val color = "#" + primaryDark.toHexString()

            setIslandConfig(
                highlightColor = color,
            )
            setEnableFloat(false)

            setSmallIsland(picKeySmallIsland)
            val textInfoRight = TextInfo(
                title = notification.time,
                content = if (notification.showLocationOutside) notification.location else null,
            )
            setBigIslandInfo(
                left = ImageTextInfoLeft(
                    picInfo = PicInfo(pic = picKeySmallIsland),
                    textInfo = TextInfo(
                        title = notification.status,
                        showHighlightColor = true,
                    ),
                ),
                centerText = if (!notification.showProgressOutside) textInfoRight else null,
                progressText = if (notification.showProgressOutside) {
                    ProgressTextInfo(
                        progressInfo = CircularProgressInfo(
                            progress = notification.progress,
                            colorReach = color,
                        ),
                        textInfo = textInfoRight,
                    )
                } else null,
            )
            setBaseInfo(
                title = notification.status,
                subTitle = notification.time,
                extraTitle = notification.location,
                content = notification.name,
                subContent = notification.suggestion,
                showDivider = true,
                showContentDivider = true,
            )
            setProgressBar(
                progress = notification.progress,
                color = color,
            )

            setAodConfig(title = notification.status)
        }

        addExtras(hyperIsland.buildResourceBundle())
        addExtras(
            Bundle().apply {
                putString("miui.focus.param", hyperIsland.buildJsonParam())
            },
        )
    }.build()

    checkedNotificationPermission {
        notification.notifyCompat(notificationId)
    }
}

fun Context.cancelClassProgressNotification(
    eventId: Uuid,
) {
    val notificationId = eventId.hashCode()
    NotificationManagerCompat.from(this).cancel(notificationId)
}

private const val ClassProgressChannelId = "class_progress"

val Context.classProgressNotificationChannel: NotificationChannelCompat
    get() {
        val channel = NotificationChannelCompat.Builder(
            ClassProgressChannelId,
            NotificationManagerCompat.IMPORTANCE_DEFAULT,
        ).apply {
            setName(getString(R.string.notification_channel_class_progress_label))
        }.build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
        return channel
    }

private inline fun NotificationCompat.ProgressStyle.kotlinApply(
    block: NotificationCompat.ProgressStyle.() -> Unit,
) = run {
    block()
    this
}
