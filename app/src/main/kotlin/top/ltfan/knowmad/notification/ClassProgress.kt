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
data class ClassProgressNotificationData(
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

inline fun <Result> Context.withClassProgressNotification(
    data: ClassProgressNotificationData,
    block: ClassProgressNotificationScope.() -> Result,
): Result? {
    val context = applicationContext

    val notificationId = data.eventId.hashCode()

    val useHyperIsland = HyperIslandNotification.isSupported(context)

    // Hyper Island will fail if we set the ProgressStyle.
    // So we only set it when Hyper Island is not supported.
    val progressStyle = if (!useHyperIsland) {
        NotificationCompat.ProgressStyle()
            .addProgressSegment(NotificationCompat.ProgressStyle.Segment(100))
    } else null

    val hyperIsland = if (useHyperIsland) {
        HyperIslandNotification.Builder(
            context = context,
            businessName = classProgressNotificationChannel.id,
            ticker = data.status,
        ).apply {
            val picKeySmallIsland = "small_island"

            addPicture(HyperPicture(picKeySmallIsland, context, R.drawable.ic_logo))

            val color = "#" + primaryDark.toHexString()

            setIslandConfig(
                highlightColor = color,
            )
            setEnableFloat(false)

            setSmallIsland(picKeySmallIsland)
        }
    } else null

    val builder = classProgressNotificationChannel.withNotificationBuilder {
        setSmallIcon(R.drawable.ic_logo)
        setOngoing(true)
        setAutoCancel(false)
        setOnlyAlertOnce(true)

        if (hyperIsland == null) {
            setRequestPromotedOngoing(true)
        }
    }.applyClassProgressData(data, progressStyle, hyperIsland)

    return withNotification(notificationId, builder) {
        ClassProgressNotificationScope(
            this,
            builder,
            progressStyle,
            hyperIsland,
            notificationId,
        ).block()
    }
}

class ClassProgressNotificationScope(
    context: Context,
    builder: NotificationCompat.Builder,
    val progressStyle: NotificationCompat.ProgressStyle?,
    val hyperIsland: HyperIslandNotification?,
    notificationId: Int,
) : NotificationScope(context, builder, notificationId) {
    fun update(data: ClassProgressNotificationData) {
        notification = builder.applyClassProgressData(data, progressStyle, hyperIsland).build()
    }
}

fun NotificationCompat.Builder.applyClassProgressData(
    data: ClassProgressNotificationData,
    progressStyle: NotificationCompat.ProgressStyle?,
    hyperIsland: HyperIslandNotification?,
) = apply {
    setContentTitle("${data.status} \u2022 ${data.time} \u2022 ${data.location}")
    setShortCriticalText(data.statusShort)
    setContentText(data.suggestion)
    setSubText(data.name)

    progressStyle?.let {
        it.setProgress(data.progress)
        setStyle(it)
    }

    hyperIsland?.let {
        it.apply {
            val picKeySmallIsland = "small_island"

            val color = "#" + primaryDark.toHexString()

            val textInfoRight = TextInfo(
                title = data.time,
                content = if (data.showLocationOutside) data.location else null,
            )
            setBigIslandInfo(
                left = ImageTextInfoLeft(
                    picInfo = PicInfo(pic = picKeySmallIsland),
                    textInfo = TextInfo(
                        title = data.status,
                        showHighlightColor = true,
                    ),
                ),
                centerText = if (!data.showProgressOutside) textInfoRight else null,
                progressText = if (data.showProgressOutside) {
                    ProgressTextInfo(
                        progressInfo = CircularProgressInfo(
                            progress = data.progress,
                            colorReach = color,
                        ),
                        textInfo = textInfoRight,
                    )
                } else null,
            )
            setBaseInfo(
                title = data.status,
                subTitle = data.time,
                extraTitle = data.location,
                content = data.name,
                subContent = data.suggestion,
                showDivider = true,
                showContentDivider = true,
            )
            setProgressBar(
                progress = data.progress,
                color = color,
            )

            setAodConfig(title = data.status)
        }

        addExtras(it.buildResourceBundle())
        addExtras(
            Bundle().apply {
                putString("miui.focus.param", it.buildJsonParam())
            },
        )
    }
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
