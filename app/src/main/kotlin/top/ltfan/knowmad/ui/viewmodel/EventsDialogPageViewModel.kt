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

package top.ltfan.knowmad.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavBackStack
import com.kizitonwose.calendar.core.plusDays
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import top.ltfan.knowmad.data.schedule.Event
import top.ltfan.knowmad.data.schedule.ScheduleDao
import top.ltfan.knowmad.ui.component.EventEdit
import top.ltfan.knowmad.ui.component.EventEditChange
import top.ltfan.knowmad.ui.component.EventEditResult
import top.ltfan.knowmad.ui.page.EventDetailsSubPage
import top.ltfan.knowmad.ui.page.EventsDialogSubPage
import top.ltfan.knowmad.util.collectAsState
import java.util.Locale

class EventsDialogPageViewModel(
    val date: LocalDate,
    val timeZone: TimeZone,
    val locale: Locale,
    initialEvents: List<Event>,
    highlight: Channel<Event>?,
    private val dao: ScheduleDao,
) : ViewModel() {
    val backStack = NavBackStack(EventsDialogSubPage.first(highlight))

    val events by dao.getEventsFlowInRange(
        startTime = date.atStartOfDayIn(timeZone),
        endTime = date.plusDays(1).atStartOfDayIn(timeZone),
    ).stateIn(
        scope = viewModelScope,
        started = Eagerly,
        initialValue = initialEvents,
    ).collectAsState()

    val eventPages
        inline get() = backStack.asSequence().filterIsInstance<EventDetailsSubPage>()

    val selectedEvent inline get() = eventPages.lastOrNull()?.selectedEvent

    fun getPagesForEvent(event: Event) = eventPages.filter { it.selectedEvent == event }

    fun onBack() {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    fun onEdit(result: EventEditResult) {
        viewModelScope.launch {
            val event = result.applyWith(dao)
            val iterator = backStack.listIterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry is EventDetailsSubPage && entry.selectedEvent.id == event.id) {
                    iterator.set(entry.replaceEvent(event))
                }
            }
        }
    }

    fun onRequestBatchEdit(edit: EventEdit, change: EventEditChange) {

    }
}
