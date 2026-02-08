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

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.navigation3.scene.Scene
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.schedule.Event
import top.ltfan.knowmad.data.schedule.ICalendarColor
import top.ltfan.knowmad.data.schedule.ICalendarPriority
import top.ltfan.knowmad.data.schedule.Reminder
import top.ltfan.knowmad.data.schedule.ScheduleDao
import top.ltfan.knowmad.ui.theme.ProvideCompatibleShapes
import top.ltfan.knowmad.ui.theme.ProvideShapes
import top.ltfan.knowmad.ui.theme.TextFieldMaxWidth
import top.ltfan.knowmad.ui.util.contractColorFor
import top.ltfan.knowmad.ui.util.format
import top.ltfan.knowmad.ui.util.localSharedTransitionScope
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
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
                    onRequestEdit = {},
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
    Surface(
        modifier = modifier
            .sizeIn(minWidth = DialogMinWidth, maxWidth = DialogMaxWidth)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.asSequence()
                            .filter { it.changedToUp() }
                            .forEach { it.consume() }
                    }
                }
            },
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
    highlight: Channel<Event>? = null,
    contentPadding: PaddingValues = PaddingValues(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    locale: Locale = LocalConfiguration.current.locales[0],
    timeZone: TimeZone = rememberSystemTimeZone(),
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val resources = LocalResources.current

    var highlighted by remember { mutableStateOf(highlight?.tryReceive()?.getOrNull()) }
    var indicator by remember {
        mutableStateOf(
            calculateIndicator(
                events,
                resources,
                locale = locale,
            ),
        )
    }

    LazyColumn(
        modifier = modifier,
        state = lazyListState,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
    ) {
        items(
            count = events.size,
            key = { events[it].id },
        ) { index ->
            val event = events[index]

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AnimatedVisibility(
                    visible = indicator.index == index && !indicator.isIn,
                    enter = fadeIn() + expandVertically(
                        expandFrom = Alignment.Top,
                        clip = false,
                    ),
                    exit = fadeOut() + shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        clip = false,
                    ),
                ) {
                    IndicatorLabel(indicator.text)
                }
                AnimatedVisibility(
                    visible = event == highlighted,
                    enter = fadeIn() + expandVertically(
                        expandFrom = Alignment.Top,
                        clip = false,
                    ),
                    exit = fadeOut() + shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        clip = false,
                    ),
                ) {
                    JustClickedLabel()
                }
                DetailedEvent(
                    event = event,
                    onClick = { onEventSelected(event) },
                    selected = event == selectedEvent,
                    locale = locale,
                    timeZone = timeZone,
                    animatedVisibilityScope = animatedVisibilityScope,
                ) {
                    AnimatedVisibility(
                        visible = indicator.index == index && indicator.isIn,
                        enter = fadeIn() + expandVertically(
                            expandFrom = Alignment.Top,
                            clip = false,
                        ),
                        exit = fadeOut() + shrinkVertically(
                            shrinkTowards = Alignment.Top,
                            clip = false,
                        ),
                    ) {
                        IndicatorLabel(
                            indicator.text,
                            tint = LocalContentColor.current,
                        )
                    }
                }
                AnimatedVisibility(
                    visible = indicator.index == index + 1 && index == events.lastIndex,
                    enter = fadeIn() + expandVertically(
                        expandFrom = Alignment.Top,
                        clip = false,
                    ),
                    exit = fadeOut() + shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        clip = false,
                    ),
                ) {
                    IndicatorLabel(
                        indicator.text,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }

    highlight?.let { highlight ->
        LaunchedEffect(highlight) {
            for (event in highlight) {
                highlighted = event
            }
        }
    }

    LaunchedEffect(highlighted) {
        val index = events.indexOfFirst { it.id == highlighted?.id }
        if (index != -1) {
            lazyListState.animateScrollToItem(index)
        }
    }

    LaunchedEffect(events, resources, locale) {
        while (true) {
            delay(1.seconds)
            indicator = calculateIndicator(events, resources, locale = locale)
        }
    }
}

@Composable
fun EventInformationScreen(
    event: Event,
    onBack: () -> Unit,
    onRequestEdit: (EventEdit) -> Unit,
    modifier: Modifier = Modifier,
    eventModifier: Modifier = Modifier,
    listModifier: @Composable (padding: PaddingValues) -> Modifier = {
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(it)
    },
    locale: Locale = LocalConfiguration.current.locales[0],
    timeZone: TimeZone = rememberSystemTimeZone(),
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    SubcomposeLayout(modifier) { constraints ->
        val eventPlaceable = subcompose("event") {
            DetailedEvent(
                event = event,
                onClick = { onBack() },
                modifier = eventModifier,
                selected = true,
                locale = locale,
                timeZone = timeZone,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }.first().measure(constraints)

        val eventWidth = eventPlaceable.width
        val eventHeight = eventPlaceable.height + 16.dp.roundToPx()

        val listPlaceable = subcompose("list") {
            DetailedEventInformation(
                event = event,
                onRequestEdit = onRequestEdit,
                modifier = listModifier(PaddingValues(top = eventHeight.toDp())),
            )
        }.first().measure(constraints)

        val listWidth = listPlaceable.width
        val listHeight = listPlaceable.height

        val width = maxOf(eventWidth, listWidth)
        val height = maxOf(eventHeight, listHeight)

        layout(width, height) {
            listPlaceable.placeRelative(0, 0)
            eventPlaceable.placeRelative(0, 0)
        }
    }
}

@Composable
fun EventEditScreen(
    event: Event,
    edit: EventEdit,
    onBack: () -> Unit,
    onEdit: (EventEditResult) -> Unit,
    modifier: Modifier = Modifier,
    eventModifier: Modifier = Modifier,
    editModifier: @Composable (padding: PaddingValues) -> Modifier = {
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(it)
    },
    locale: Locale = LocalConfiguration.current.locales[0],
    timeZone: TimeZone = rememberSystemTimeZone(),
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    SubcomposeLayout(modifier) { constraints ->
        val eventPlaceable = subcompose("event") {
            DetailedEvent(
                event = event,
                onClick = { onBack() },
                modifier = eventModifier,
                selected = true,
                locale = locale,
                timeZone = timeZone,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }.first().measure(constraints)

        val eventWidth = eventPlaceable.width
        val eventHeight = eventPlaceable.height + 16.dp.roundToPx()

        val editPlaceable = subcompose("edit") {
            edit.EditContent(
                event = event,
                onBack = onBack,
                onEdit = onEdit,
                modifier = editModifier(PaddingValues(top = eventHeight.toDp())),
            )
        }.first().measure(constraints)

        val editWidth = editPlaceable.width
        val editHeight = editPlaceable.height

        val width = maxOf(eventWidth, editWidth)
        val height = maxOf(eventHeight, editHeight)

        layout(width, height) {
            editPlaceable.placeRelative(0, 0)
            eventPlaceable.placeRelative(0, 0)
        }
    }
}

@Serializable
@Immutable
sealed class EventEdit {
    @Composable
    abstract fun EditContent(
        event: Event,
        onBack: () -> Unit,
        onEdit: (EventEditResult) -> Unit,
        modifier: Modifier = Modifier,
    )
}

@Immutable
fun interface EventEditResult {
    suspend fun apply(dao: ScheduleDao): Event
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
    additionalContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val contentColor = MaterialTheme.colorScheme.contentColorFor(color)
        .takeOrElse { contractColorFor(color) }

    val minShadowElevation by animatedVisibilityScope?.transition?.animateDp {
        val sceneTransition = animatedVisibilityScope.transition.parentTransition
        val targetScene = sceneTransition?.targetState as? Scene<*>
        val targetContentKey = targetScene?.entries?.lastOrNull()?.contentKey
        val currentScene = sceneTransition?.currentState as? Scene<*>
        val currentContentKey = currentScene?.entries?.lastOrNull()?.contentKey
        val isForward = (targetContentKey == event.id && it == Visible) ||
                (currentContentKey == event.id && it != Visible)
        if (isForward) 4.dp else 0.dp
    } ?: animateDpAsState(if (selected) 4.dp else 0.dp)

    localSharedTransitionScope {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().run {
                if (animatedVisibilityScope != null) sharedBounds(
                    rememberSharedContentState(ScheduleSharedKey.Event(event.id)),
                    animatedVisibilityScope = animatedVisibilityScope,
                    resizeMode = RemeasureToBounds,
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
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
                    EventLocationLabel(event)
                    EventNotesLabel(event)
                    additionalContent?.let { it() }
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
                        style = MaterialTheme.typography.bodyLargeEmphasized,
                        fontWeight = ExtraBold,
                    )
                    Text(
                        text = remember(event.endTime, formatter, timeZone) {
                            val localDateTime =
                                event.endTime.toLocalDateTime(timeZone).toJavaLocalDateTime()
                            formatter.format(localDateTime)
                        },
                        color = contentColor.copy(alpha = .7f),
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                        fontWeight = Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun EventLocationLabel(event: Event) {
    LabelWithIcon(
        icon = R.drawable.location_on_24px,
        label = event.location.ifBlank { stringResource(R.string.schedule_event_location_label_none) },
        contentDescription = stringResource(R.string.schedule_event_location_label),
        tint = LocalContentColor.current,
    )
}

@Composable
private fun EventNotesLabel(event: Event) {
    val notes = event.notes
    if (!notes.isNullOrBlank()) {
        LabelWithIcon(
            icon = R.drawable.notes_24px,
            label = notes,
            contentDescription = stringResource(R.string.schedule_event_notes_label),
            tint = LocalContentColor.current,
        )
    }
}

@Composable
fun DetailedEventInformation(
    event: Event,
    onRequestEdit: (EventEdit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DetailedEventInformationEntry(
            icon = R.drawable.label_24px,
            label = R.string.schedule_event_name_label,
            content = { Text(event.name.ifBlank { stringResource(R.string.schedule_event_name_label_none) }) },
        )

        DetailedEventInformationEntry(
            icon = R.drawable.location_on_24px,
            label = R.string.schedule_event_location_label,
        ) {
            Text(
                text = event.location.ifBlank { stringResource(R.string.schedule_event_location_label_none) },
            )
        }

        DetailedEventInformationEntry(
            icon = R.drawable.notes_24px,
            label = R.string.schedule_event_notes_label,
            onClick = { onRequestEdit(NotesEdit) },
        ) {
            Text(
                text = event.notes?.ifBlank { null }
                    ?: stringResource(R.string.schedule_event_notes_label_none),
            )
        }
        DetailedEventInformationEntry(
            icon = R.drawable.book_24px,
            label = R.string.schedule_event_course_label,
        ) {
            if (event is Course) {
                Text(event.course.name)
            } else {
                Text(stringResource(R.string.schedule_event_course_label_none))
            }
        }

        if (event is Course) {
            DetailedEventInformationEntry(
                icon = R.drawable.podium_24px,
                label = R.string.schedule_event_instructor_label,
            ) {
                Text(
                    text = event.instructor.ifBlank { stringResource(R.string.schedule_event_instructor_label_none) },
                )
            }
        }

        DetailedEventInformationEntry(
            icon = R.drawable.event_repeat_24px,
            label = R.string.schedule_event_semester_label,
            content = { Text(event.semester.name) },
        )

        DetailedEventInformationEntry(
            icon = R.drawable.priority_24px,
            label = R.string.schedule_event_priority_label,
            content = { event.priority.Label() },
        )

        DetailedEventInformationEntry(
            icon = R.drawable.alarm_24px,
            label = R.string.schedule_event_reminders_label,
        ) {
            if (event.reminders.list.isEmpty()) {
                Text(stringResource(R.string.schedule_event_reminders_label_none))
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Spacer(Modifier.height(4.dp))
                    event.reminders.list.forEach { reminder ->
                        reminder.Label()
                    }
                }
            }
        }

        DetailedEventInformationEntry(
            icon = R.drawable.palette_24px,
            label = R.string.schedule_event_color_label,
            content = { event.color.Label() },
        )
    }
}

@Immutable
private object NotesEdit : EventEdit() {
    @Composable
    override fun EditContent(
        event: Event,
        onBack: () -> Unit,
        onEdit: (EventEditResult) -> Unit,
        modifier: Modifier,
    ) {
        Column(modifier) {
            var text by remember { mutableStateOf(event.notes ?: "") }
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .widthIn(max = TextFieldMaxWidth)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
                label = { Text(stringResource(R.string.schedule_event_edit_notes_label_input)) },
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, alignment = Alignment.End),
            ) {
                TextButton(onClick = onBack) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        onEdit(
                            EventEditResult { dao ->
                                when (event) {
                                    is Normal -> event.copy(notes = text.ifBlank { null })
                                    is Course -> event.copy(notes = text.ifBlank { null })
                                }.also {
                                    dao.updateEvent(it.toEntity())
                                }
                            },
                        )
                        onBack()
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        }
    }
}

@Composable
private fun DetailedEventInformationEntry(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    ProvideCompatibleShapes {
        if (onClick != null) {
            ListItem(
                onClick = onClick,
                modifier = modifier,
                leadingContent = { DetailedEventInformationEntryIcon(icon) },
                overlineContent = { Text(stringResource(label)) },
                content = content,
            )
        } else {
            ListItem(
                headlineContent = content,
                modifier = modifier,
                leadingContent = { DetailedEventInformationEntryIcon(icon) },
                overlineContent = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
private fun DetailedEventInformationEntryIcon(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
) {
    ProvideShapes {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = modifier,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                modifier = Modifier
                    .padding(12.dp)
                    .size(24.dp),
            )
        }
    }
}

@Composable
fun ICalendarPriority.Label() {
    ColoredLabel(
        color = when (type) {
            High -> MaterialTheme.colorScheme.error
            Medium -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.secondary
        },
    ) {
        Text(
            text = name.takeIf { this@Label != None }
                ?: stringResource(R.string.schedule_event_priority_label_none),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun Reminder.Label(
    timeZone: TimeZone = rememberSystemTimeZone(),
    locale: Locale = LocalConfiguration.current.locales[0],
) {
    val resources = LocalResources.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.notifications_24px),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = trigger.getString(resources, timeZone, locale),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
fun ICalendarColor.Label() {
    ColoredLabel(color = compose) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun ColoredLabel(
    color: Color,
    contentColor: Color = MaterialTheme.colorScheme.contentColorFor(color)
        .takeOrElse { contractColorFor(color) },
    content: @Composable () -> Unit,
) {
    Box(Modifier.padding(2.dp)) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = color,
            contentColor = contentColor,
            content = content,
        )
    }
}

@Composable
fun JustClickedLabel() {
    LabelWithIcon(
        icon = R.drawable.asterisk_24px,
        label = stringResource(R.string.label_just_clicked),
    )
}

@Composable
context(animatedVisibilityScope: AnimatedVisibilityScope)
private fun IndicatorLabel(
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.secondary,
) {
    localSharedTransitionScope {
        LabelWithIcon(
            icon = R.drawable.info_24px,
            label = label,
            modifier = modifier
                .sharedElement(
                    rememberSharedContentState("indicator"),
                    animatedVisibilityScope = animatedVisibilityScope,
                ),
            tint = tint,
        )
    }
}

@Stable
private fun calculateIndicator(
    events: List<Event>,
    resources: Resources,
    now: Instant = Clock.System.now(),
    locale: Locale = Locale.getDefault(),
): IndicatorData {
    val candidateIndex = events.indexOfFirst { it.endTime > now }

    if (candidateIndex == -1) return IndicatorData(
        index = events.size,
        isIn = false,
        text = resources.getString(R.string.schedule_event_list_indicator_label_day_ended),
    )

    val event = events[candidateIndex]

    val isIn = now >= event.startTime
    val offset = if (isIn) event.endTime - now else event.startTime - now
    val isNear = offset <= 15.minutes

    if (!isIn) return when {
        candidateIndex == 0 && !isNear -> IndicatorData(
            index = candidateIndex,
            isIn = false,
            text = resources.getString(R.string.schedule_event_list_indicator_label_new_day),
        )

        !isNear -> IndicatorData(
            index = candidateIndex,
            isIn = false,
            text = resources.getString(R.string.schedule_event_list_indicator_label_not_started),
        )

        else -> IndicatorData(
            index = candidateIndex,
            isIn = false,
            text = resources.getString(
                R.string.schedule_event_list_indicator_label_close_to_start,
                offset.format(locale),
            ),
        )
    }

    val templateId = if (isNear) {
        R.string.schedule_event_list_indicator_label_close_to_end
    } else {
        R.string.schedule_event_list_indicator_label_started
    }

    return IndicatorData(
        index = candidateIndex,
        isIn = true,
        text = resources.getString(templateId, offset.format(locale)),
    )
}

@Immutable
private data class IndicatorData(
    val index: Int,
    val isIn: Boolean,
    val text: String,
)

@Composable
private fun LabelWithIcon(
    @DrawableRes icon: Int,
    label: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.secondary,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = tint,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
    }
}

@Immutable
sealed interface ScheduleSharedKey {
    @Immutable
    data class Event(val id: Uuid) : ScheduleSharedKey
}
