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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.weekcalendar.WeekCalendarState
import com.kizitonwose.calendar.core.OutDateStyle
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import com.kizitonwose.calendar.core.minusMonths
import com.kizitonwose.calendar.core.now
import com.kizitonwose.calendar.core.plusDays
import com.kizitonwose.calendar.core.plusMonths
import com.kyant.capsule.ContinuousRoundedRectangle
import com.tyme.solar.SolarDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.yearMonth
import top.ltfan.knowmad.data.schedule.Event
import top.ltfan.knowmad.ui.theme.AppRadiusSmall
import top.ltfan.knowmad.ui.util.contractColorFor
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant
import com.kizitonwose.calendar.compose.CalendarState as MonthCalendarState

@Composable
fun Calendar(
    modifier: Modifier = Modifier,
    calendarState: CalendarState = rememberCalendarState(),
    onSystemDateChanged: (today: LocalDate) -> Unit = {},
    getEvents: (startTime: Instant, endTime: Instant) -> Flow<List<Event>> = { _, _ ->
        flowOf(emptyList())
    },
) {
    val today = rememberSystemDate(timeZone = calendarState.timeZone)

    Box(modifier) {
        HorizontalCalendar(
            modifier = Modifier.fillMaxSize(),
            state = calendarState.monthCalendarState,
            contentHeightMode = Fill,
            dayContent = { day ->
                val selected = day.date == calendarState.selectedDate

                val events by remember(day.date) {
                    snapshotFlow { calendarState.timeZone }.flatMapLatest {
                        val startOfDay = day.date.atStartOfDayIn(it)
                        val endOfDay = day.date.plusDays(1).atStartOfDayIn(it)
                        getEvents(startOfDay, endOfDay)
                    }
                }.collectAsState(initial = emptyList())

                Day(
                    date = day.date,
                    selected = selected && day.position == MonthDate,
                    onClick = { calendarState.selectedDate = day.date },
                    events = events,
                    outOfMonth = day.position != MonthDate,
                    border = if (day.date == today) BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary,
                    ) else null,
                )
            },
            monthHeader = {
                MonthHeader(
                    modifier = Modifier.padding(vertical = 4.dp),
                    daysOfWeek = calendarState.daysOfWeek,
                )
            },
        )
    }

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
    events: List<Event> = emptyList(),
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

        Events(events)
    }
}

@Composable
fun Events(events: List<Event>) {
    SharedTransitionLayout {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            var showDot by remember { mutableStateOf<Boolean?>(null) }
            AnimatedVisibility(
                visible = showDot == true,
                enter = expandIn(),
                exit = shrinkOut(),
            ) {
                var positioned by remember { mutableStateOf(false) }
                val radius by transition.animateDp {
                    if (it == Visible) 2.dp else AppRadiusSmall
                }
                Spacer(
                    Modifier
                        .size(4.dp)
                        .clip(ContinuousRoundedRectangle(radius))
                        .background(MaterialTheme.colorScheme.primary)
                        .run {
                            if (positioned) sharedBounds(
                                rememberSharedContentState(CalendarSharedKey),
                                animatedVisibilityScope = this@AnimatedVisibility,
                                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                            )
                            else onGloballyPositioned { positioned = true }
                        },
                )
            }
            for ((index, event) in events.withIndex()) {
                SubcomposeLayout(Modifier.zIndex((-index).toFloat())) { constraints ->
                    val width = constraints.maxWidth

                    val expandedHeight = subcompose("expanded-event") {
                        Event(event)
                    }[0].maxIntrinsicHeight(width)

                    if (showDot == null) showDot = expandedHeight > constraints.maxHeight
                    val isMinimized = if (index == 0) showDot ?: error("Uninitialized")
                    else expandedHeight > constraints.maxHeight
                    if (index == 0) {
                        showDot = expandedHeight > constraints.maxHeight
                    }

                    val placeable = subcompose("event") {
                        AnimatedVisibility(
                            visible = !isMinimized,
                            enter = fadeIn() + expandVertically(clip = false),
                            exit = fadeOut() + shrinkVertically(clip = false),
                        ) {
                            var positioned by remember { mutableStateOf(false) }
                            val radius by transition.animateDp {
                                if (it == Visible) AppRadiusSmall else 2.dp
                            }
                            Event(
                                event,
                                modifier = when (index) {
                                    0 if positioned -> Modifier
                                        .sharedBounds(
                                            rememberSharedContentState(CalendarSharedKey),
                                            animatedVisibilityScope = this@AnimatedVisibility,
                                            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                        )

                                    0 -> Modifier.onGloballyPositioned {
                                        positioned = true
                                    }

                                    else -> Modifier
                                },
                                shape = ContinuousRoundedRectangle(radius),
                            )
                        }
                    }.firstOrNull()?.measure(constraints)

                    layout(
                        width = width,
                        height = placeable?.height ?: 0,
                    ) {
                        placeable?.placeRelative(0, 0)
                    }
                }
            }
        }
    }
}

@Composable
fun Event(
    event: Event,
    modifier: Modifier = Modifier,
    color: Color = Color(
        red = event.color.r.toInt(),
        green = event.color.g.toInt(),
        blue = event.color.b.toInt(),
    ),
    shape: Shape = MaterialTheme.shapes.small,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = color,
    ) {
        Text(
            text = event.name,
            modifier = Modifier.padding(2.dp),
            color = MaterialTheme.colorScheme.contentColorFor(color)
                .takeOrElse { contractColorFor(color) },
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            maxLines = 1,
            style = MaterialTheme.typography.bodySmallEmphasized,
        )
    }
}

@Composable
fun MonthHeader(
    modifier: Modifier = Modifier,
    daysOfWeek: List<DayOfWeek> = emptyList(),
) {
    Row(modifier.fillMaxWidth()) {
        for (dayOfWeek in daysOfWeek) {
            Text(
                dayOfWeek.toJavaDayOfWeek().getDisplayName(NARROW, Locale.getDefault()),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMediumEmphasized,
            )
        }
    }
}

@Composable
fun rememberCalendarState(
    initialTimeZone: TimeZone = rememberSystemTimeZone(),
    initialDate: LocalDate = rememberSystemDate(timeZone = initialTimeZone),
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
    initialTimeZone: TimeZone = TimeZone.currentSystemDefault(),
    initialDate: LocalDate = LocalDate.now(timeZone = initialTimeZone),
    val daysOfWeek: List<DayOfWeek> = daysOfWeek(),
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

    var timeZone by mutableStateOf(initialTimeZone)

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
    timeZone: TimeZone = rememberSystemTimeZone(),
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
            addAction(Intent.ACTION_TIME_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    return systemDate
}

@Composable
fun rememberSystemTimeZone(): TimeZone {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var systemTimeZone by remember { mutableStateOf(TimeZone.currentSystemDefault()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == ON_RESUME) {
                systemTimeZone = TimeZone.currentSystemDefault()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                systemTimeZone = TimeZone.currentSystemDefault()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    return systemTimeZone
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

@Immutable
data object CalendarSharedKey

const val AdjacentMonths = 50
