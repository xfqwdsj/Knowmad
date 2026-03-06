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

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import top.ltfan.knowmad.R
import top.ltfan.knowmad.util.Logger
import kotlin.uuid.Uuid

private val logger = Logger("ServiceNotification")

val AgentRunningNotificationId =
    Uuid.parse("019c0c33-1400-79a1-b180-c0178365ec90").hashCode()

inline fun <Result> Context.withAgentRunningNotification(
    initialContent: String? = null,
    notificationId: Int = AgentRunningNotificationId,
    block: AgentRunningNotificationScope.() -> Result,
) = checkedNotificationPermission {
    val builder = NotificationCompat.Builder(this, AiMessageChannelId).apply {
        setSmallIcon(R.drawable.ic_launcher_foreground)
        setContentTitle(getString(R.string.notification_agent_running_title))
        initialContent?.let { setContentText(it) }
        setShowWhen(false)
        setOngoing(true)
        setAutoCancel(false)
        setOnlyAlertOnce(true)
    }

    val manager = NotificationManagerCompat.from(this)
    val notification = builder.build()
    manager.notify(notificationId, notification)

    val scope = AgentRunningNotificationScope(this, manager, builder, notificationId, notification)

    try {
        scope.block()
    } finally {
        manager.cancel(notificationId)
    }
}

class AgentRunningNotificationScope(
    private val context: Context,
    private val manager: NotificationManagerCompat,
    private val builder: NotificationCompat.Builder,
    val notificationId: Int,
    var notification: Notification,
) {
    fun updateContent(content: String) {
        val updatedNotification = builder.setContentText(content).build()
        context.checkedNotificationPermission {
            manager.notify(notificationId, updatedNotification)
        }
    }
}
