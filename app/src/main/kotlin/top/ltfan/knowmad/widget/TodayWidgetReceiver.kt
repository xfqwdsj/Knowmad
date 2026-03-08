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

package top.ltfan.knowmad.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.getSystemService
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.ltfan.knowmad.util.Logger
import kotlin.time.Instant

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TodayWidget()

    @OptIn(ExperimentalGlanceApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE) {
            logger.debug { "Received today widget update intent" }
            CoroutineScope(coroutineContext + SupervisorJob()).launch {
                glanceAppWidget.updateAll(context)
            }
        }
    }

    companion object {
        private val logger = Logger("TodayWidgetReceiver")

        const val ACTION_UPDATE = "ACTION_UPDATE"

        val Context.updateIntent
            inline get() = Intent(this, TodayWidgetReceiver::class.java).apply {
                action = ACTION_UPDATE
            }

        val Context.updatePendingIntent
            inline get() = PendingIntentCompat.getBroadcast(
                this,
                0,
                updateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT,
                false,
            )

        fun Context.scheduleTodayWidgetUpdate(at: Instant) {
            val alarmManager = getSystemService<AlarmManager>() ?: run {
                logger.error { "Failed to get AlarmManager service" }
                return
            }
            val pendingIntent = updatePendingIntent ?: run {
                logger.error { "Failed to create pending intent for today widget update" }
                return
            }
            if (!AlarmManagerCompat.canScheduleExactAlarms(alarmManager)) {
                logger.warn { "Cannot schedule exact alarms, skipping scheduling today widget update" }
                return
            }

            logger.debug { "Scheduling today widget update at: $at" }

            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                at.toEpochMilliseconds(),
                pendingIntent,
            )
        }
    }
}
