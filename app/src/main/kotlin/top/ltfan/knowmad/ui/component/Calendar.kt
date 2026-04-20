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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMapIndexed
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minusMonth
import kotlinx.datetime.number
import kotlinx.datetime.plusMonth
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.yearMonth
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.schedule.Event
import top.ltfan.knowmad.data.schedule.compose
import top.ltfan.knowmad.ui.theme.AppRadiusSmall
import top.ltfan.knowmad.ui.util.autoScale
import top.ltfan.knowmad.ui.util.contractColorFor
import top.ltfan.knowmad.ui.util.localSharedTransitionScope
import top.ltfan.knowmad.ui.util.matchParentShortestSide
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid
import com.kizitonwose.calendar.compose.CalendarState as MonthCalendarState

@Composable
fun Calendar(
    modifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier.padding(vertical = 4.dp),
    state: CalendarState = rememberCalendarState(),
    onSystemDateChanged: ((
        lastDay: LocalDate,
        newDay: LocalDate,
    ) -> Unit)? = null,
    onSystemTimeZoneChanged: ((
        lastTimeZone: TimeZone,
        newTimeZone: TimeZone,
    ) -> Unit)? = { _, it -> state.timeZone = it },
    locale: Locale = LocalConfiguration.current.locales[0],
    headerTextStyle: TextStyle = MaterialTheme.typography.bodyMediumEmphasized,
    getEvents: (startTime: Instant, endTime: Instant) -> Flow<List<Event>> = { _, _ ->
        flowOf(emptyList())
    },
    onDayClick: ((date: LocalDate, events: List<Event>?) -> Unit)? = null,
    onEventClick: ((date: LocalDate, clickedEvent: Event, events: List<Event>) -> Unit)? = null,
) {
    val today = rememberSystemDate(timeZone = state.timeZone)

    val tick = remember { MutableStateFlow(0u) }

    val allEvents by remember {
        snapshotFlow { state.currentMonth to state.timeZone }.flatMapLatest { (month, timeZone) ->
            val startTime = month.minusMonth().firstDay.atStartOfDayIn(timeZone)
            val endTime = month.plusMonth().lastDay.plusDays(1).atStartOfDayIn(timeZone)
            getEvents(startTime, endTime)
        }
    }.collectAsState(initial = emptyList())

    CompositionLocalProvider(LocalDaySecondaryTextTick provides tick) {
        // TODO: replace with self-implemented calendar to support week mode and better performance
        HorizontalCalendar(
            modifier = modifier.fillMaxSize(),
            state = state.monthCalendarState,
            contentHeightMode = Fill,
            dayContent = { day ->
                val events = remember(day, state.timeZone, allEvents) {
                    if (day.position != MonthDate) return@remember null
                    val startTime = day.date.atStartOfDayIn(state.timeZone)
                    val endTime = day.date.plusDays(1).atStartOfDayIn(state.timeZone)
                    allEvents.filter { it.startTime <= endTime && it.endTime >= startTime }
                }

                Day(
                    date = day.date,
                    onClick = onDayClick?.let { callback ->
                        { callback(day.date, events) }
                    },
                    events = events,
                    onEventClick = onEventClick?.let { callback ->
                        { clicked, events ->
                            callback(day.date, clicked, events)
                        }
                    },
                    outOfMonth = day.position != MonthDate,
                    border = if (day.date == today) BorderStroke(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    ) else null,
                )
            },
            monthHeader = {
                WeekHeader(
                    modifier = headerModifier,
                    daysOfWeek = state.daysOfWeek,
                    locale = locale,
                    textStyle = headerTextStyle,
                )
            },
        )
    }

    LaunchedEffect(today) {
        if (state.today != today) {
            onSystemDateChanged?.invoke(state.today, today)
            state.today = today
        }
    }

    val currentTimeZone = rememberSystemTimeZone()
    var lastTimeZone by remember { mutableStateOf(currentTimeZone) }
    LaunchedEffect(currentTimeZone) {
        if (lastTimeZone != currentTimeZone) {
            onSystemTimeZoneChanged?.invoke(lastTimeZone, currentTimeZone)
            lastTimeZone = currentTimeZone
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(STARTED) {
            while (isActive) {
                delay(5.seconds)
                tick.value += 1u
            }
        }
    }
}

@Composable
fun Day(
    date: LocalDate,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    events: List<Event>? = null,
    hasEvents: Boolean? = null,
    onEventClick: ((clicked: Event, events: List<Event>) -> Unit)? = null,
    outOfMonth: Boolean = false,
    border: BorderStroke? = null,
) {
    val tymeSolarDay = remember(date) { SolarDay.fromYmd(date.year, date.month.number, date.day) }
    val tymeTermDay = remember(tymeSolarDay) { tymeSolarDay.getTermDay() }
    val tymeLunarDay = remember(tymeSolarDay) { tymeSolarDay.getLunarDay() }
    val tymeLunarMonth = remember(tymeLunarDay) { tymeLunarDay.getLunarMonth() }
    val tymeSolarFestival = remember(tymeSolarDay) { tymeSolarDay.getFestival() }
    val tymeLunarFestival = remember(tymeLunarDay) { tymeLunarDay.getFestival() }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val modifier = Modifier
            .sizeIn(
                maxWidth = 64.dp,
                maxHeight = 64.dp,
            )
            .padding(2.dp)
            .matchParentShortestSide()
        val shape = MaterialTheme.shapes.small
        val color = if (!selected && !outOfMonth) MaterialTheme.colorScheme.surfaceContainer
        else if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface

        (@Composable {
            Column(
                modifier = Modifier.autoScale(maxWidth = 48.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    date.day.toString(),
                    color = if (!outOfMonth) MaterialTheme.colorScheme.onSurface
                    else LocalContentColor.current,
                    fontWeight = if (!selected) FontWeight.Bold else FontWeight.ExtraBold,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyLarge,
                )
                DaySecondaryText(
                    texts = listOfNotNull(
                        tymeSolarFestival?.getName(),
                        tymeLunarFestival?.getName(),
                        tymeTermDay.getName().takeIf { tymeTermDay.getDayIndex() == 0 },
                        tymeLunarDay.getName().takeIf { tymeLunarDay.day != 1 },
                        tymeLunarMonth.getName().takeIf { tymeLunarDay.day == 1 },
                    ),
                    modifier = Modifier
                        .wrapContentSize()
                        .basicMarquee(iterations = Int.MAX_VALUE),
                    color = if (!outOfMonth) MaterialTheme.colorScheme.onSurface
                    else LocalContentColor.current,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }).let { content ->
            if (onClick != null) {
                Surface(
                    onClick = onClick,
                    modifier = modifier,
                    shape = shape,
                    color = color,
                    border = border,
                    content = content,
                )
            } else {
                Surface(
                    modifier = modifier,
                    shape = shape,
                    color = color,
                    border = border,
                    content = content,
                )
            }
        }

        events?.let {
            Events(
                date = date,
                events = events,
                onEventClick = onEventClick?.let { callback ->
                    { callback(it, events) }
                },
            )
        } ?: hasEvents?.let { hasEvents ->
            AnimatedVisibility(
                visible = hasEvents,
                enter = expandIn(),
                exit = shrinkOut(),
            ) {
                Dot()
            }
        }
    }
}

@Composable
private fun DaySecondaryText(
    texts: List<String>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = 1,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    val tick by LocalDaySecondaryTextTick.current.collectAsStateWithLifecycle()
    val currentText = remember(texts, tick) {
        if (texts.size <= 1) texts.firstOrNull()
        else texts[(tick % texts.size.toUInt()).toInt()]
    }

    AnimatedContent(
        targetState = currentText,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = {
            (fadeIn() + slideInVertically { it / 2 }) togetherWith
                    (fadeOut() + slideOutVertically { -it / 2 }) using null
        },
        contentAlignment = Alignment.Center,
    ) { text ->
        if (text == null) return@AnimatedContent
        Text(
            text = text,
            modifier = modifier,
            color = color,
            maxLines = maxLines,
            style = style,
        )
    }
}

@Composable
fun Events(
    date: LocalDate,
    events: List<Event>,
    onEventClick: ((event: Event) -> Unit)? = null,
) {
    localSharedTransitionScope {
        val internalRenderList = remember { mutableStateListOf<Event>() }
        val activeIds = remember(events) { events.map { it.id }.toSet() }

        SideEffect {
            var iIdx = 0

            events.fastForEach { newEvent ->
                while (iIdx < internalRenderList.size && !activeIds.contains(internalRenderList[iIdx].id)) {
                    iIdx++
                }

                if (iIdx < internalRenderList.size) {
                    if (internalRenderList[iIdx].id == newEvent.id) {
                        if (internalRenderList[iIdx] !== newEvent) {
                            internalRenderList[iIdx] = newEvent
                        }
                    } else {
                        val existingIdx = internalRenderList.indexOfFirst { it.id == newEvent.id }
                        if (existingIdx != -1) {
                            internalRenderList.removeAt(existingIdx)
                        }
                        internalRenderList.add(iIdx, newEvent)
                    }
                } else {
                    internalRenderList.add(newEvent)
                }

                iIdx++
            }
        }

        SubcomposeLayout(
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp),
        ) { constraints ->
            val width = constraints.maxWidth
            val spacing = 2.dp.roundToPx()

            var remainingHeight = constraints.maxHeight
            val visibilityMap = mutableMapOf<Uuid, Boolean>()
            var globalShowDot = false

            internalRenderList.fastForEachIndexed { index, event ->
                val isCurrentlyActive = activeIds.contains(event.id)
                if (!isCurrentlyActive) {
                    visibilityMap[event.id] = false
                    return@fastForEachIndexed
                }

                val h =
                    subcompose("probe_${event.id}") { Event(event) }[0].maxIntrinsicHeight(width)

                if (index == 0) {
                    if (h > remainingHeight) {
                        globalShowDot = true
                        visibilityMap[event.id] = false
                    } else {
                        globalShowDot = false
                        visibilityMap[event.id] = true
                        remainingHeight -= (h + spacing)
                    }
                } else {
                    if (h <= remainingHeight && !globalShowDot) {
                        visibilityMap[event.id] = true
                        remainingHeight -= (h + spacing)
                    } else {
                        visibilityMap[event.id] = false
                    }
                }
            }

            val dotPlaceables = if (globalShowDot) {
                subcompose("dot") {
                    AnimatedVisibility(true, enter = expandIn(), exit = shrinkOut()) {
                        val radius by transition.animateDp { if (it == Visible) 2.dp else AppRadiusSmall }
                        Dot(
                            modifier = Modifier.sharedBounds(
                                rememberSharedContentState(CalendarSharedKey.Dot(date)),
                                animatedVisibilityScope = this,
                                resizeMode = RemeasureToBounds,
                                renderInOverlayDuringTransition = false,
                            ),
                            shape = ContinuousRoundedRectangle(radius),
                        )
                    }
                }.map { it.measure(constraints) }
            } else emptyList()

            val eventPlaceables = internalRenderList.fastMapIndexed { index, event ->
                subcompose("anim_${event.id}") {
                    AnimatedVisibility(
                        visible = visibilityMap[event.id] == true,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        val radius by transition.animateDp { if (it == Visible) AppRadiusSmall else 2.dp }
                        Event(
                            event = event,
                            modifier = if (index == 0) {
                                Modifier.sharedBounds(
                                    rememberSharedContentState(CalendarSharedKey.Dot(date)),
                                    animatedVisibilityScope = this,
                                    resizeMode = RemeasureToBounds,
                                    renderInOverlayDuringTransition = false,
                                )
                            } else Modifier,
                            onClick = onEventClick?.let { { it(event) } },
                            shape = ContinuousRoundedRectangle(radius),
                        )

                        if (!activeIds.contains(event.id) && transition.currentState == PostExit) {
                            SideEffect {
                                val target = internalRenderList.indexOfFirst { it.id == event.id }
                                if (target != -1) internalRenderList.removeAt(target)
                            }
                        }
                    }
                }.map { it.measure(constraints) }
            }

            val allPlaceables = dotPlaceables + eventPlaceables.flatten()
            val totalUsedHeight = if (allPlaceables.isEmpty()) 0
            else allPlaceables.sumOf { it.height } + (allPlaceables.size - 1).coerceAtLeast(0) * spacing

            layout(width, totalUsedHeight) {
                var y = 0
                allPlaceables.fastForEachIndexed { i, p ->
                    p.placeRelative(
                        x = (width - p.width) / 2,
                        y = y,
                        zIndex = -i.toFloat(),
                    )
                    y += p.height + spacing
                }
            }
        }
    }
}

@Composable
private fun Dot(
    modifier: Modifier = Modifier,
    requiredSize: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    shape: Shape = ContinuousRoundedRectangle(2.dp),
) {
    val description = stringResource(R.string.schedule_event_dot_label)
    Spacer(
        Modifier
            .size(requiredSize)
            .matchParentShortestSide()
            .clip(shape)
            .background(color)
            .then(modifier)
            .semantics {
                contentDescription = description
            },
    )
}

@Composable
fun Event(
    event: Event,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = event.color.compose,
    shape: Shape = MaterialTheme.shapes.small,
) {
    val contentColor = MaterialTheme.colorScheme.contentColorFor(color)
        .takeOrElse { contractColorFor(color) }

    CompositionLocalProvider(
        LocalContentColor provides contentColor,
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
    ) {
        (@Composable {
            Text(
                text = event.name,
                modifier = Modifier.padding(2.dp),
                overflow = Ellipsis,
                softWrap = false,
                maxLines = 1,
                style = MaterialTheme.typography.bodySmallEmphasized,
            )
        }).let {
            if (onClick != null) {
                Surface(
                    onClick = onClick,
                    modifier = modifier.fillMaxWidth(),
                    shape = shape,
                    color = color,
                    content = it,
                )
            } else {
                Surface(
                    modifier = modifier.fillMaxWidth(),
                    shape = shape,
                    color = color,
                    content = it,
                )
            }
        }
    }
}

@Composable
fun WeekHeader(
    modifier: Modifier = Modifier,
    daysOfWeek: List<DayOfWeek> = emptyList(),
    locale: Locale = LocalConfiguration.current.locales[0],
    textStyle: TextStyle = MaterialTheme.typography.bodyMediumEmphasized,
) {
    Row(modifier.fillMaxWidth()) {
        for (dayOfWeek in daysOfWeek) {
            Text(
                dayOfWeek.toJavaDayOfWeek().getDisplayName(NARROW, locale),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                style = textStyle,
            )
        }
    }
}

@Composable
fun rememberWeekHeaderTextMeasuredHeight(
    daysOfWeek: List<DayOfWeek> = rememberDaysOfWeek(),
    locale: Locale = LocalConfiguration.current.locales[0],
    textStyle: TextStyle = MaterialTheme.typography.bodyMediumEmphasized,
): Dp {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    return remember(density, textMeasurer, daysOfWeek, locale, textStyle) {
        with(density) {
            daysOfWeek.asSequence()
                .map { it.toJavaDayOfWeek().getDisplayName(NARROW, locale) }
                .maxOfOrNull {
                    textMeasurer.measure(
                        text = it,
                        style = textStyle,
                    ).size.height.toDp()
                }
        } ?: 0.dp
    }
}

@Composable
fun rememberCalendarState(
    initialTimeZone: TimeZone = remember { TimeZone.currentSystemDefault() },
    initialDate: LocalDate = remember { LocalDate.now(timeZone = initialTimeZone) },
    daysOfWeek: List<DayOfWeek> = rememberDaysOfWeek(),
): CalendarState {
    return remember(initialTimeZone, initialDate, daysOfWeek) {
        CalendarState(
            initialTimeZone = initialTimeZone,
            initialDate = initialDate,
            daysOfWeek = daysOfWeek,
        )
    }
}

@Stable
class CalendarState(
    initialTimeZone: TimeZone = TimeZone.currentSystemDefault(),
    initialDate: LocalDate = LocalDate.now(timeZone = initialTimeZone),
    val daysOfWeek: List<DayOfWeek> = daysOfWeek(),
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
    var today by mutableStateOf(LocalDate.now(timeZone = timeZone))

    suspend fun animateScrollToDate(date: LocalDate = today) {
        coroutineScope {
            launch {
                monthCalendarState.animateScrollToMonth(date.yearMonth)
            }
            launch {
                weekCalendarState.animateScrollToWeek(date)
            }
        }
    }

    private var lastFirstVisibleMonth = monthCalendarState.firstVisibleMonth.yearMonth
    private var lastLastVisibleMonth = monthCalendarState.lastVisibleMonth.yearMonth
    val currentMonth by derivedStateOf {
        val firstVisibleMonth = monthCalendarState.firstVisibleMonth.yearMonth
        val lastVisibleMonth = monthCalendarState.lastVisibleMonth.yearMonth
        when {
            firstVisibleMonth == lastVisibleMonth -> {
                lastFirstVisibleMonth = firstVisibleMonth
                lastLastVisibleMonth = lastVisibleMonth
                firstVisibleMonth
            }

            firstVisibleMonth == lastFirstVisibleMonth -> firstVisibleMonth
            lastVisibleMonth == lastLastVisibleMonth -> lastVisibleMonth
            firstVisibleMonth > lastFirstVisibleMonth -> firstVisibleMonth
            lastVisibleMonth < lastLastVisibleMonth -> lastVisibleMonth
            lastFirstVisibleMonth == lastLastVisibleMonth -> lastFirstVisibleMonth
            else -> error(
                "Unexpected state: " +
                        "firstVisibleMonth=$firstVisibleMonth, " +
                        "lastVisibleMonth=$lastVisibleMonth, " +
                        "lastFirstVisibleMonth=$lastFirstVisibleMonth, " +
                        "lastLastVisibleMonth=$lastLastVisibleMonth",
            )
        }
    }
}

enum class CalendarDisplayMode {
    Month, Week
}

@Composable
fun rememberSystemDate(
    clock: Clock = Clock.System,
    timeZone: TimeZone = rememberSystemTimeZone(),
): LocalDate {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val updatedClock by rememberUpdatedState(clock)
    val updatedTimeZone by rememberUpdatedState(timeZone)

    var systemDate by remember { mutableStateOf(LocalDate.now(clock, timeZone)) }

    LaunchedEffect(clock, timeZone) {
        systemDate = LocalDate.now(clock, timeZone)
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(RESUMED) {
            systemDate = LocalDate.now(updatedClock, updatedTimeZone)
        }
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

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(RESUMED) {
            systemTimeZone = TimeZone.currentSystemDefault()
        }
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
private sealed interface CalendarSharedKey {
    @Immutable
    data class Dot(val date: LocalDate) : CalendarSharedKey
}

const val AdjacentMonths = 50

private val LocalDaySecondaryTextTick = staticCompositionLocalOf<StateFlow<UInt>> {
    error("No LocalDaySecondaryTextTick provided")
}
