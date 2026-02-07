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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.schedule.Event
import top.ltfan.knowmad.ui.util.contractColorFor
import top.ltfan.knowmad.ui.util.localSharedTransitionScope
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.uuid.Uuid

@Composable
fun EventsDialogContent(
    date: LocalDate,
    events: List<Event>,
    selectedEvent: Event?,
    onEventSelected: (Event?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    locale: Locale = LocalConfiguration.current.locales[0],
    timeZone: TimeZone = rememberSystemTimeZone(),
    shape: Shape = MaterialTheme.shapes.large,
) {
    EventsDialogContent(
        date = date,
        modifier = modifier,
        contentPadding = contentPadding,
        locale = locale,
        shape = shape,
    ) {
        AnimatedContent(
            targetState = selectedEvent,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
        ) { event ->
            if (event == null) {
                DetailedEventList(
                    events = events,
                    selectedEvent = selectedEvent,
                    onEventSelected = onEventSelected,
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    locale = locale,
                    timeZone = timeZone,
                    animatedVisibilityScope = this@AnimatedContent,
                )
            } else {
                EventInformationScreen(
                    event = event,
                    onBack = { onEventSelected(null) },
                    modifier = Modifier.padding(8.dp),
                    locale = locale,
                    timeZone = timeZone,
                    animatedVisibilityScope = this@AnimatedContent,
                )
            }
        }
    }
}

@Composable
fun EventsDialogContent(
    date: LocalDate,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    shape: Shape = MaterialTheme.shapes.large,
    locale: Locale = LocalConfiguration.current.locales[0],
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .padding(DialogMargin)
            .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.sizeIn(minWidth = DialogMinWidth, maxWidth = DialogMaxWidth),
            shape = shape,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
        ) {
            Column(Modifier.padding(contentPadding)) {
                Spacer(Modifier.height(24.dp))
                EventsDateHeader(
                    date = date,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    locale = locale,
                )
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
fun EventsDateHeader(
    date: LocalDate,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    locale: Locale = LocalConfiguration.current.locales[0],
) {
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(SHORT).withLocale(locale)
    }

    val weekDayFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("E").withLocale(locale)
    }

    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
    ) {
        Text(
            text = remember(date, dateFormatter) {
                dateFormatter.format(date.toJavaLocalDate())
            },
            modifier = Modifier.alignByBaseline(),
            style = MaterialTheme.typography.headlineMediumEmphasized,
        )
        Text(
            text = remember(date, weekDayFormatter) {
                weekDayFormatter.format(date.toJavaLocalDate())
            },
            modifier = Modifier.alignByBaseline(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f),
        )
    }
}

@Composable
fun DetailedEventList(
    events: List<Event>,
    selectedEvent: Event?,
    onEventSelected: (Event) -> Unit,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
    highlight: Flow<Event>? = null,
    contentPadding: PaddingValues = PaddingValues(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    locale: Locale = LocalConfiguration.current.locales[0],
    timeZone: TimeZone = rememberSystemTimeZone(),
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    var highlighted by remember { mutableStateOf<Event?>(null) }

    LazyColumn(
        modifier = modifier,
        state = lazyListState,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
    ) {
        items(events, key = { it.id }) {
            DetailedEvent(
                event = it,
                onClick = { onEventSelected(it) },
                selected = it == selectedEvent,
                highlighted = it == highlighted,
                locale = locale,
                timeZone = timeZone,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    }

    highlight?.let { highlight ->
        LaunchedEffect(highlight) {
            highlight.collect { event ->
                val index = events.indexOfFirst { it.id == event.id }
                if (index != -1) {
                    lazyListState.animateScrollToItem(index)
                    highlighted = event
                }
            }
        }
    }
}

@Composable
fun EventInformationScreen(
    event: Event,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = LocalConfiguration.current.locales[0],
    timeZone: TimeZone = rememberSystemTimeZone(),
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    Column(modifier) {
        DetailedEvent(
            event = event,
            onClick = { onBack() },
            selected = true,
            locale = locale,
            timeZone = timeZone,
            animatedVisibilityScope = animatedVisibilityScope,
        )
        DetailedEventInformation(event = event)
    }
}

@Composable
fun DetailedEvent(
    event: Event,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    highlighted: Boolean = false,
    locale: Locale = LocalConfiguration.current.locales[0],
    timeZone: TimeZone = rememberSystemTimeZone(),
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = event.color.compose,
    interactionSource: MutableInteractionSource? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val contentColor =
        MaterialTheme.colorScheme.contentColorFor(color).takeOrElse { contractColorFor(color) }

    val minShadowElevation by animatedVisibilityScope?.transition?.animateDp {
        if (selected && it == Visible) 4.dp else 0.dp
    } ?: animateDpAsState(if (selected) 4.dp else 0.dp)

    localSharedTransitionScope {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().run {
                if (animatedVisibilityScope != null) sharedElement(
                    rememberSharedContentState(ScheduleSharedKey.Event(event.id)),
                    animatedVisibilityScope = animatedVisibilityScope,
                ) else this
            },
            shape = shape,
            color = color,
            contentColor = contentColor,
            shadowElevation = if (event.priority.type == High) {
                event.priority.reversedValueInType.toInt().dp
            } else {
                0.dp
            }.coerceAtLeast(minShadowElevation),
            border = if (highlighted) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
            } else null,
            interactionSource = interactionSource,
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (event.priority.type == High) {
                            Icon(
                                painterResource(R.drawable.priority_high_24px),
                                contentDescription = event.priority.name,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Text(text = event.name, style = MaterialTheme.typography.titleMedium)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(R.drawable.location_on_24px),
                            contentDescription = stringResource(R.string.schedule_event_location_label),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = event.location.ifBlank { stringResource(R.string.schedule_event_location_label_none) },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    val notes = event.notes
                    if (!notes.isNullOrBlank()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painterResource(R.drawable.notes_24px),
                                contentDescription = stringResource(R.string.schedule_event_notes_label),
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    val formatter = remember(locale) {
                        DateTimeFormatter.ofLocalizedTime(SHORT).withLocale(locale)
                    }

                    Text(
                        text = remember(event.startTime, formatter, timeZone) {
                            val localDateTime =
                                event.startTime.toLocalDateTime(timeZone).toJavaLocalDateTime()
                            formatter.format(localDateTime)
                        },
                        style = MaterialTheme.typography.bodyMediumEmphasized.copy(
                            fontFeatureSettings = "tnum",
                        ),
                        fontWeight = Bold,
                    )
                    Text(
                        text = remember(event.endTime, formatter, timeZone) {
                            val localDateTime =
                                event.endTime.toLocalDateTime(timeZone).toJavaLocalDateTime()
                            formatter.format(localDateTime)
                        },
                        color = contentColor.copy(alpha = .7f),
                        style = MaterialTheme.typography.bodyMediumEmphasized.copy(
                            fontFeatureSettings = "tnum",
                        ),
                        fontWeight = Bold,
                    )
                }
            }
        }
    }
}

@Composable
fun DetailedEventInformation(
    event: Event,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val notes = event.notes
        if (notes != null) {
            Text(
                text = stringResource(R.string.schedule_event_notes_label),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(text = notes, style = MaterialTheme.typography.bodyMedium)
        }

        when (event) {
            is Event.Course -> {
                Text(
                    text = stringResource(R.string.schedule_event_course_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(text = event.course.name, style = MaterialTheme.typography.bodyMedium)

                Text(
                    text = stringResource(R.string.schedule_event_instructor_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(text = event.instructor, style = MaterialTheme.typography.bodyMedium)
            }

            is Event.Normal -> Unit
        }

        Text(
            text = stringResource(R.string.schedule_event_semester_label),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(text = event.semester.name, style = MaterialTheme.typography.bodyMedium)

        Text(
            text = stringResource(R.string.schedule_event_priority_label),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(text = event.priority.name, style = MaterialTheme.typography.bodyMedium)

        if (event.reminders.list.isNotEmpty()) {
            Text(
                text = stringResource(R.string.schedule_event_reminders_label),
                style = MaterialTheme.typography.titleMedium,
            )
            for (reminder in event.reminders.list) {
                Text(text = reminder.toString(), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Immutable
sealed interface ScheduleSharedKey {
    @Immutable
    data class Event(val id: Uuid) : ScheduleSharedKey
}
