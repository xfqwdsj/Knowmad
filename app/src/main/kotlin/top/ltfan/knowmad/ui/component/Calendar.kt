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
import androidx.annotation.FloatRange
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.rememberTransition
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.WeekCalendar
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
import kotlinx.coroutines.delay
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
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import top.ltfan.knowmad.data.schedule.Event
import top.ltfan.knowmad.ui.theme.AppRadiusSmall
import top.ltfan.knowmad.ui.util.ProvideLocalSharedTransitionScope
import top.ltfan.knowmad.ui.util.autoScale
import top.ltfan.knowmad.ui.util.contractColorFor
import top.ltfan.knowmad.ui.util.localSharedTransitionScope
import top.ltfan.knowmad.ui.util.matchParentShortestSide
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import com.kizitonwose.calendar.compose.CalendarState as MonthCalendarState

@Composable
fun Calendar(
    modifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier.padding(vertical = 4.dp),
    calendarState: CalendarState = rememberCalendarState(),
    onSystemDateChanged: (
        lastDay: LocalDate,
        newDay: LocalDate,
    ) -> Unit = { _, it -> calendarState.selectedDate = it },
    onSystemTimeZoneChanged: (
        lastTimeZone: TimeZone,
        newTimeZone: TimeZone,
    ) -> Unit = { _, it -> calendarState.timeZone = it },
    locale: Locale = LocalConfiguration.current.locales[0],
    headerTextStyle: TextStyle = MaterialTheme.typography.bodyMediumEmphasized,
    getEvents: (startTime: Instant, endTime: Instant) -> Flow<List<Event>> = { _, _ ->
        flowOf(emptyList())
    },
) {
    val today = rememberSystemDate(timeZone = calendarState.timeZone)

    val transition = rememberTransition(calendarState.transitionState)

    SharedTransitionLayout {
        ProvideLocalSharedTransitionScope {
            transition.AnimatedContent(
                modifier = modifier,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
            ) { mode ->
                when (mode) {
                    Month -> HorizontalCalendar(
                        modifier = Modifier.fillMaxSize(),
                        state = calendarState.monthCalendarState,
                        contentHeightMode = Fill,
                        dayContent = { day ->
                            val selected = day.date == calendarState.selectedDate

                            val events = remember(day) {
                                if (day.position != MonthDate) return@remember null
                                snapshotFlow { calendarState.timeZone }.flatMapLatest {
                                    val startOfDay = day.date.atStartOfDayIn(it)
                                    val endOfDay = day.date.plusDays(1).atStartOfDayIn(it)
                                    getEvents(startOfDay, endOfDay)
                                }
                            }?.collectAsState(initial = emptyList())

                            Day(
                                date = day.date,
                                selected = selected && day.position == MonthDate,
                                onClick = { calendarState.selectedDate = day.date },
                                modifier = Modifier.sharedElement(
                                    rememberSharedContentState(CalendarSharedKey.Day(day.date)),
                                    animatedVisibilityScope = this@AnimatedContent,
                                ),
                                events = events?.value,
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
                                daysOfWeek = calendarState.daysOfWeek,
                                locale = locale,
                                textStyle = headerTextStyle,
                            )
                        },
                    )

                    Week -> WeekCalendar(
                        modifier = Modifier.fillMaxSize(),
                        state = calendarState.weekCalendarState,
                        dayContent = { day ->
                            val selected = day.date == calendarState.selectedDate

                            val events by remember(day) {
                                snapshotFlow { calendarState.timeZone }.flatMapLatest {
                                    val startOfDay = day.date.atStartOfDayIn(it)
                                    val endOfDay = day.date.plusDays(1).atStartOfDayIn(it)
                                    getEvents(startOfDay, endOfDay)
                                }
                            }.collectAsState(initial = emptyList())

                            Day(
                                date = day.date,
                                selected = selected,
                                onClick = { calendarState.selectedDate = day.date },
                                modifier = Modifier.sharedElement(
                                    rememberSharedContentState(CalendarSharedKey.Day(day.date)),
                                    animatedVisibilityScope = this@AnimatedContent,
                                ),
                                hasEvents = events.isNotEmpty(),
                                outOfMonth = false,
                                border = if (day.date == today) BorderStroke(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                ) else null,
                            )
                        },
                        weekHeader = {
                            WeekHeader(
                                modifier = headerModifier,
                                daysOfWeek = calendarState.daysOfWeek,
                                locale = locale,
                                textStyle = headerTextStyle,
                            )
                        },
                    )
                }
            }
        }
    }

    LaunchedEffect(calendarState.selectedDate) {
        launch {
            calendarState.monthCalendarState.animateScrollToMonth(
                calendarState.selectedDate.yearMonth,
            )
        }
        launch {
            calendarState.weekCalendarState.animateScrollToWeek(
                calendarState.selectedDate,
            )
        }
    }

    var lastDay by remember { mutableStateOf(today) }
    LaunchedEffect(today) {
        if (lastDay != today) {
            onSystemDateChanged(lastDay, today)
            lastDay = today
        }
    }

    val currentTimeZone = rememberSystemTimeZone()
    var lastTimeZone by remember { mutableStateOf(currentTimeZone) }
    LaunchedEffect(currentTimeZone) {
        if (lastTimeZone != currentTimeZone) {
            onSystemTimeZoneChanged(lastTimeZone, currentTimeZone)
            lastTimeZone = currentTimeZone
        }
    }
}

@Composable
fun Day(
    date: LocalDate,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    events: List<Event>? = null,
    hasEvents: Boolean? = null,
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
        modifier = modifier.fillMaxSize(),
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
                .matchParentShortestSide(),
            shape = MaterialTheme.shapes.small,
            color = if (!selected && !outOfMonth) MaterialTheme.colorScheme.surfaceContainer
            else if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
            border = border,
        ) {
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
        }

        events?.let { Events(it) } ?: hasEvents?.let { hasEvents ->
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
    var currentText by remember { mutableStateOf(texts.firstOrNull()) }

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

    LaunchedEffect(texts) {
        var index = 0
        while (index in texts.indices) {
            currentText = texts[index]
            index++
            if (index >= texts.size) index = 0
            delay(5.seconds)
        }
    }
}

@Composable
fun Events(events: List<Event>) {
    localSharedTransitionScope {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            var canUpdateEvents by remember { mutableStateOf(true) }
            val eventsSnapshot = remember { mutableStateListOf<Event>() }
            val displayedEvents = remember { mutableStateSetOf<Event>() }
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
                Dot(
                    modifier = if (positioned) Modifier.sharedBounds(
                        rememberSharedContentState(CalendarSharedKey.Dot),
                        animatedVisibilityScope = this@AnimatedVisibility,
                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    )
                    else Modifier.onGloballyPositioned { positioned = true },
                    shape = ContinuousRoundedRectangle(radius),
                )
            }

            if (canUpdateEvents) {
                eventsSnapshot.clear()
                eventsSnapshot.addAll(events)
            }

            for ((index, event) in eventsSnapshot.withIndex()) {
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
                            visible = displayedEvents.contains(event) && !isMinimized,
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
                                            rememberSharedContentState(CalendarSharedKey.Dot),
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
                            DisposableEffect(Unit) {
                                onDispose {
                                    eventsSnapshot.remove(event)
                                }
                            }
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

            canUpdateEvents = true

            LaunchedEffect(events) {
                canUpdateEvents = false
                displayedEvents.clear()
                displayedEvents.addAll(events)
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
    // TODO: semantic description
    Spacer(
        Modifier
            .size(requiredSize)
            .matchParentShortestSide()
            .clip(shape)
            .background(color)
            .then(modifier),
    )
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
            modifier = Modifier
                .basicMarquee(iterations = Int.MAX_VALUE)
                .padding(2.dp),
            color = MaterialTheme.colorScheme.contentColorFor(color)
                .takeOrElse { contractColorFor(color) },
            softWrap = false,
            maxLines = 1,
            style = MaterialTheme.typography.bodySmallEmphasized,
        )
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

    val transitionState = SeekableTransitionState<CalendarDisplayMode>(Month)
    val currentMode get() = transitionState.currentState

    suspend fun snapToMode(mode: CalendarDisplayMode) {
        transitionState.snapTo(mode)
    }

    suspend fun seekToMode(
        @FloatRange(from = 0.0, to = 1.0) fraction: Float,
        targetMode: CalendarDisplayMode = transitionState.targetState,
    ) {
        transitionState.seekTo(fraction, targetMode)
    }

    suspend fun animateToMode(mode: CalendarDisplayMode) {
        transitionState.animateTo(mode)
    }

    private var _timeZone by mutableStateOf(initialTimeZone)
    var timeZone: TimeZone
        get() = _timeZone
        set(value) {
            if (_timeZone != value) {
                selectedDate = selectedDate.atStartOfDayIn(_timeZone).toLocalDateTime(value).date
                _timeZone = value
            }
        }

    var selectedDate by mutableStateOf(initialDate)

    private var lastFirstVisibleMonth by mutableStateOf(monthCalendarState.firstVisibleMonth.yearMonth)
    private var lastLastVisibleMonth by mutableStateOf(monthCalendarState.lastVisibleMonth.yearMonth)
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
private sealed interface CalendarSharedKey {
    @Immutable
    data object Dot

    @Immutable
    data class Day(val date: LocalDate)
}

const val AdjacentMonths = 50
