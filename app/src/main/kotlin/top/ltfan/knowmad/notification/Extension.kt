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

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

inline fun <R> Context.checkedNotificationPermission(block: () -> R): R? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionState =
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        if (permissionState != PackageManager.PERMISSION_GRANTED) return null
    }
    return block()
}

@OptIn(ExperimentalContracts::class)
context(context: Context)
inline fun NotificationChannelCompat.withNotificationBuilder(
    block: NotificationCompat.Builder.(channel: NotificationChannelCompat) -> Unit,
): NotificationCompat.Builder {
    contract { callsInPlace(block, EXACTLY_ONCE) }
    return NotificationCompat.Builder(context, id).apply {
        block(this@withNotificationBuilder)
    }
}

@RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
@Suppress("NOTHING_TO_INLINE")
context(context: Context)
inline fun Notification.notifyCompat(id: Int, tag: String? = null) {
    NotificationManagerCompat.from(context).notify(tag, id, this)
}
