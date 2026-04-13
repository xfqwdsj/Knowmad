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

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import com.kizitonwose.calendar.core.plusDays
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.database.AppDatabase
import top.ltfan.knowmad.data.schedule.getCalendarEventIntent
import top.ltfan.knowmad.ui.component.WidgetEventList
import top.ltfan.knowmad.ui.theme.AppRadiusLarge
import top.ltfan.knowmad.ui.theme.AppTypography
import top.ltfan.knowmad.ui.util.toGlanceTextStyle
import top.ltfan.knowmad.widget.TodayWidgetReceiver.Companion.scheduleTodayWidgetUpdate
import kotlin.time.Clock

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = context(context) { AppDatabase.get() }
        val scheduleDao = database.scheduleDao()
        val timeZone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val date = now.toLocalDateTime(timeZone).date
        val events = scheduleDao.getEventsInRange(
            startTime = date.atStartOfDayIn(timeZone),
            endTime = date.plusDays(1).atStartOfDayIn(timeZone),
        )
        val (upcomingEvents, completedEvents) = events.partition { it.endTime > now }
        val supplementaryEvents = upcomingEvents.takeIf { it.isEmpty() }?.let {
            scheduleDao.getEventsInRange(
                startTime = date.plusDays(1).atStartOfDayIn(timeZone),
                endTime = date.plusDays(2).atStartOfDayIn(timeZone),
            )
        }
        val nextEndTime = events.asSequence()
            .run {
                if (!supplementaryEvents.isNullOrEmpty()) plus(supplementaryEvents)
                else this
            }
            .map { it.endTime }
            .filter { it > now }
            .minOrNull()
        nextEndTime?.let { context.scheduleTodayWidgetUpdate(it) }
        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.widgetBackground)
                    .cornerRadius(AppRadiusLarge),
                contentAlignment = Center,
            ) {
                if (
                    upcomingEvents.isEmpty() &&
                    completedEvents.isEmpty() &&
                    supplementaryEvents.isNullOrEmpty()
                ) {
                    Text(
                        text = context.getString(R.string.schedule_event_list_label_widget_no_events),
                        modifier = GlanceModifier.padding(16.dp),
                        style = AppTypography.titleLargeEmphasized
                            .toGlanceTextStyle(GlanceTheme.colors.secondary)
                            .copy(textAlign = Center),
                    )
                    return@Box
                }

                WidgetEventList(
                    upcomingEvents = upcomingEvents,
                    completedEvents = completedEvents,
                    supplementaryEvents = supplementaryEvents,
                    getEventClickAction = { event ->
                        actionStartActivity(context.getCalendarEventIntent(event.id))
                    },
                    contentPadding = 8.dp,
                )
            }
        }
    }
}
