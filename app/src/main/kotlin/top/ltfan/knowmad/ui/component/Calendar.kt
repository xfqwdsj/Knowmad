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

package top.ltfan.knowmad.ui.component

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.weekcalendar.WeekCalendarState
import com.kizitonwose.calendar.core.OutDateStyle
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import com.kizitonwose.calendar.core.minusMonths
import com.kizitonwose.calendar.core.now
import com.kizitonwose.calendar.core.plusMonths
import com.tyme.solar.SolarDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import com.kizitonwose.calendar.compose.CalendarState as MonthCalendarState

@Composable
fun Calendar(
    calendarState: CalendarState = rememberCalendarState(),
    onSystemDateChanged: (today: LocalDate) -> Unit = {},
) {
    val today = rememberSystemDate()

    HorizontalCalendar(
        modifier = Modifier.fillMaxSize(),
        state = calendarState.monthCalendarState,
        contentHeightMode = Fill,
        dayContent = { day ->
            val selected = day.date == calendarState.selectedDate
            Day(
                date = day.date,
                selected = selected && day.position == MonthDate,
                onClick = { calendarState.selectedDate = day.date },
                outOfMonth = day.position != MonthDate,
                border = if (day.date == today) BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary,
                ) else null,
            )
        },
    )

    var lastDay by remember { mutableStateOf(today) }
    LaunchedEffect(today) {
        if (lastDay != today) {
            lastDay = today
            onSystemDateChanged(today)
        }
    }
}

@Composable
fun Day(
    date: LocalDate,
    selected: Boolean,
    onClick: () -> Unit,
    outOfMonth: Boolean = false,
    border: BorderStroke? = null,
) {
    val tymeSolarDay = remember(date) { SolarDay.fromYmd(date.year, date.month.number, date.day) }
    val tymeTermDay = remember(tymeSolarDay) { tymeSolarDay.getTermDay() }
    val tymeLunarDay = remember(tymeSolarDay) { tymeSolarDay.getLunarDay() }
    val tymeLunarMonth = remember(tymeLunarDay) { tymeLunarDay.getLunarMonth() }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .sizeIn(
                    maxWidth = 64.dp,
                    maxHeight = 64.dp,
                )
                .padding(2.dp)
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = MaterialTheme.shapes.small,
            color = if (!selected && !outOfMonth) MaterialTheme.colorScheme.surfaceContainer
            else if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
            border = border,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    date.day.toString(),
                    color = if (!outOfMonth) MaterialTheme.colorScheme.onSurface
                    else LocalContentColor.current,
                    fontWeight = if (!selected) FontWeight.Bold else FontWeight.ExtraBold,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    if (tymeTermDay.getDayIndex() == 0) tymeTermDay.getName()
                    else if (tymeLunarDay.day != 1) tymeLunarDay.getName()
                    else tymeLunarMonth.getName(),
                    color = if (!outOfMonth) MaterialTheme.colorScheme.onSurface
                    else LocalContentColor.current,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun rememberCalendarState(
    initialDate: LocalDate = rememberSystemDate(),
    daysOfWeek: List<DayOfWeek> = rememberDaysOfWeek(),
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): CalendarState {
    return remember(initialDate, daysOfWeek, coroutineScope) {
        CalendarState(
            initialDate = initialDate,
            daysOfWeek = daysOfWeek,
            coroutineScope = coroutineScope,
        )
    }
}

@Stable
class CalendarState(
    initialDate: LocalDate = LocalDate.now(),
    daysOfWeek: List<DayOfWeek> = daysOfWeek(),
    private val coroutineScope: CoroutineScope,
) {
    val monthCalendarState = MonthCalendarState::class.constructors.first().call(
        initialDate.yearMonth.minusMonths(AdjacentMonths),
        initialDate.yearMonth.plusMonths(AdjacentMonths),
        daysOfWeek.first(),
        initialDate.yearMonth,
        OutDateStyle.EndOfRow,
        null,
    )

    val weekCalendarState = WeekCalendarState::class.constructors.first().call(
        initialDate.minusMonths(AdjacentMonths),
        initialDate.plusMonths(AdjacentMonths),
        initialDate,
        daysOfWeek.first(),
        null,
    )

    private var _selectedDate by mutableStateOf(initialDate)
    var selectedDate
        get() = _selectedDate
        set(value) {
            _selectedDate = value
            coroutineScope.launch {
                monthCalendarState.animateScrollToMonth(value.yearMonth)
            }
            coroutineScope.launch {
                weekCalendarState.animateScrollToWeek(value)
            }
        }
}

@Composable
fun rememberSystemDate(
    clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): LocalDate {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var systemDate by remember { mutableStateOf(LocalDate.now(clock, timeZone)) }

    LaunchedEffect(clock, timeZone) {
        systemDate = LocalDate.now(clock, timeZone)
    }

    DisposableEffect(lifecycleOwner, clock, timeZone) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == ON_RESUME) {
                systemDate = LocalDate.now(clock, timeZone)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context, clock, timeZone) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                systemDate = LocalDate.now(clock, timeZone)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    return systemDate
}

@Composable
fun rememberDaysOfWeek(
    firstDayOfWeek: DayOfWeek = rememberFirstDayOfWeek(),
): List<DayOfWeek> {
    return remember(firstDayOfWeek) {
        daysOfWeek(firstDayOfWeek)
    }
}

@Composable
fun rememberFirstDayOfWeek(): DayOfWeek {
    val configuration = LocalConfiguration.current

    var firstDayOfWeek by remember { mutableStateOf(firstDayOfWeekFromLocale()) }

    LaunchedEffect(configuration.locales) {
        firstDayOfWeek = firstDayOfWeekFromLocale()
    }

    return firstDayOfWeek
}

const val AdjacentMonths = 50
