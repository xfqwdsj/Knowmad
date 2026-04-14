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

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo

open class NotificationScope(
    protected val context: Context,
    protected val builder: NotificationCompat.Builder,
    val notificationId: Int,
) {
    private val manager = NotificationManagerCompat.from(context)

    @SuppressLint("MissingPermission")
    var notification = run {
        builder.build().also {
            context.checkedNotificationPermission {
                manager.notify(notificationId, it)
            }
        }
    }
        protected set(value) {
            field = value
            context.checkedNotificationPermission {
                manager.notify(notificationId, value)
            }
        }

    context(coroutineWorker: CoroutineWorker)
    suspend inline fun setForeground(foregroundServiceType: Int) {
        coroutineWorker.setForeground(
            ForegroundInfo(
                notificationId,
                notification,
                foregroundServiceType,
            ),
        )
    }
}
