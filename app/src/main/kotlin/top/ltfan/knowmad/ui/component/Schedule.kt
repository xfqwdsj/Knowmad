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

import android.content.ClipData
import android.content.res.Resources
import android.icu.text.DateTimePatternGenerator
import android.icu.text.NumberFormat
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxBy
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.scene.Scene
import com.kizitonwose.calendar.core.minusDays
import com.kizitonwose.calendar.core.plusDays
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaYearMonth
import kotlinx.datetime.toJavaZoneId
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.schedule.Event
import top.ltfan.knowmad.data.schedule.Event.Course
import top.ltfan.knowmad.data.schedule.Event.Normal
import top.ltfan.knowmad.data.schedule.Reminder
import top.ltfan.knowmad.data.schedule.ScheduleDao
import top.ltfan.knowmad.data.schedule.SemesterEntity
import top.ltfan.knowmad.data.schedule.compose
import top.ltfan.knowmad.data.schedule.getString
import top.ltfan.knowmad.data.schedule.iCalendarImportResultMessage
import top.ltfan.knowmad.data.schedule.reversedValueInType
import top.ltfan.knowmad.data.schedule.type
import top.ltfan.knowmad.ui.page.EventDetailsSubPageKey
import top.ltfan.knowmad.ui.theme.TextFieldMaxWidth
import top.ltfan.knowmad.ui.util.SnackbarAction
import top.ltfan.knowmad.ui.util.contractColorFor
import top.ltfan.knowmad.ui.util.detectLongPress
import top.ltfan.knowmad.ui.util.format
import top.ltfan.knowmad.ui.util.itemThemedShape
import top.ltfan.knowmad.ui.util.leadingItemThemedShape
import top.ltfan.knowmad.ui.util.localSharedTransitionScope
import top.ltfan.knowmad.ui.util.trailingItemThemedShape
import top.ltfan.knowmad.ui.viewmodel.GlobalViewModel
import top.ltfan.knowmad.ui.viewmodel.LocalAgentViewModel
import top.ltfan.knowmad.util.asStringRes
import top.ltfan.omnical.icalendar.ICalendarColor
import top.ltfan.omnical.icalendar.ICalendarPriority
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Composable
fun MonthBottomSheetContent(
    month: YearMonth,
    semesters: List<SemesterEntity>,
    notSelectedSemesters: Set<SemesterEntity> = emptySet(),
    onSemesterSelectionChange: ((SemesterEntity, Boolean) -> Unit)? = null,
    locale: Locale = LocalConfiguration.current.locales[0],
    currentTimeZone: TimeZone = rememberSystemTimeZone(),
    today: LocalDate = rememberSystemDate(timeZone = currentTimeZone),
    onExport: (suspend (SemesterEntity) -> String)? = null,
    onBackup: (suspend (SemesterEntity) -> String)? = null,
    onDelete: (suspend (SemesterEntity) -> Unit)? = null,
    onImport: ((String) -> Unit)? = null,
) {
    val currentSemesters = remember(month, semesters) {
        semesters.filter {
            it.startDate < month.lastDay.plusDays(1) && it.endDate > month.firstDay
        }
    }

    val formatter = remember(locale) {
        val pattern = DateTimePatternGenerator.getInstance(locale).getBestPattern("yMMM")
        DateTimeFormatter.ofPattern(pattern).withLocale(locale)
    }

    var showImportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = remember(month, formatter) {
                    formatter.format(month.toJavaYearMonth())
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMediumEmphasized,
            )
            if (onImport != null) {
                ImportIconButton(
                    onImport = { showImportDialog = true },
                    contentDescriptionRes = R.string.schedule_import_label,
                )
            }
        }
        currentSemesters.fastForEach { semester ->
            SemesterInformation(
                semester = semester,
                selected = semester !in notSelectedSemesters,
                onSelectionChange = onSemesterSelectionChange?.let {
                    { selected ->
                        it(semester, selected)
                    }
                },
                locale = locale,
                currentTimeZone = currentTimeZone,
                today = today,
                onExport = onExport?.let { { it(semester) } },
                onBackup = onBackup?.let { { it(semester) } },
                onDelete = onDelete?.let { { it(semester) } },
            )
        }
    }

    if (onImport != null && showImportDialog) {
        ICalendarImportDialog(
            onDismissRequest = { showImportDialog = false },
            onImport = { content ->
                showImportDialog = false
                onImport(content)
            },
        )
    }
}

@Composable
private fun ICalendarImportDialog(
    onDismissRequest: () -> Unit,
    onImport: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.schedule_import_label)) },
        confirmButton = {
            TextButton(
                onClick = { onImport(text) },
                content = { Text(stringResource(android.R.string.ok)) },
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                content = { Text(stringResource(android.R.string.cancel)) },
            )
        },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .widthIn(max = TextFieldMaxWidth)
                    .fillMaxWidth(),
            )
        },
    )
}

@Composable
fun ICalendarImportResultDialog(
    onDismissRequest: () -> Unit,
    result: Result<Int>,
    errors: List<String>? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = result.getOrNull()?.let {
                    pluralStringResource(
                        R.plurals.schedule_import_label_success,
                        it, it,
                    )
                } ?: stringResource(R.string.schedule_import_label_failure),
            )
        },
        text = {
            (result.exceptionOrNull() to errors).let { (e, errors) ->
                if (e == null && errors.isNullOrEmpty()) {
                    Text(stringResource(R.string.schedule_import_message_success))
                    return@let
                }
                val agentViewModel = LocalAgentViewModel.current
                val message = iCalendarImportResultMessage(
                    successCount = result.getOrNull(),
                    throwable = e,
                    errors = errors,
                )

                var explanation by remember { mutableStateOf("") }

                Box(contentAlignment = Alignment.Center) {
                    MarkdownView(
                        explanation,
                        mathJaxRendererState = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    )

                    AnimatedVisibility(
                        visible = explanation.isBlank(),
                        enter = fadeIn() + expandIn(
                            expandFrom = Alignment.Center,
                            clip = false,
                        ),
                        exit = fadeOut() + shrinkOut(
                            shrinkTowards = Alignment.Center,
                            clip = false,
                        ),
                    ) {
                        LoadingIndicator()
                    }
                }

                LaunchedEffect(Unit) {
                    agentViewModel.generateErrorExplanation(message) {
                        explanation += it
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
                content = { Text(stringResource(android.R.string.ok)) },
            )
        },
    )
}

@Composable
fun SemesterInformation(
    semester: SemesterEntity,
    modifier: Modifier = Modifier,
    selected: Boolean = true,
    onSelectionChange: ((Boolean) -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    tonalElevation: Dp = animateDpAsState(if (!selected) 6.dp else 4.dp).value,
    shadowElevation: Dp = animateDpAsState(if (!selected) 2.dp else 0.dp).value,
    locale: Locale = LocalConfiguration.current.locales[0],
    currentTimeZone: TimeZone = rememberSystemTimeZone(),
    today: LocalDate = rememberSystemDate(timeZone = currentTimeZone),
    onExport: (suspend () -> String)? = null,
    onBackup: (suspend () -> String)? = null,
    onDelete: (suspend () -> Unit)? = null,
) {
    val density = LocalDensity.current

    val coroutineScope = rememberCoroutineScope()

    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(SHORT).withLocale(locale)
    }

    val (destinationStart, destinationEnd) = remember(semester, dateFormatter) {
        val startDate = dateFormatter.format(semester.startDate.toJavaLocalDate())
        val endDate = dateFormatter.format(semester.endDate.minusDays(1).toJavaLocalDate())
        startDate to endDate
    }

    val destinationToday = remember(today, currentTimeZone, semester) {
        if (currentTimeZone == semester.timeZone) {
            today
        } else {
            today.atStartOfDayIn(currentTimeZone).toLocalDateTime(semester.timeZone).date
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var menuOriginalOffset by remember { mutableStateOf(Offset.Zero) }
    val menuOffset = remember(density, menuOriginalOffset) {
        with(density) {
            DpOffset(
                x = menuOriginalOffset.x.toDp(),
                y = menuOriginalOffset.y.toDp(),
            )
        }
    }

    Surface(
        modifier = modifier,
        shape = shape,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onLongClick = { showMenu = true },
                    onClick = { onSelectionChange?.invoke(!selected) },
                )
                .detectLongPress { menuOriginalOffset = it.position }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) content@{
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = semester.name,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    if (semester.id == SemesterEntity.DefaultSemesterId) return@content
                    LabelWithIcon(
                        icon = R.drawable.date_range_24px,
                        label = if (currentTimeZone == semester.timeZone) {
                            stringResource(
                                R.string.schedule_semester_label_date_range,
                                destinationStart,
                                destinationEnd,
                            )
                        } else {
                            val timeZone = remember(semester, locale) {
                                semester.timeZone.toJavaZoneId().getDisplayName(SHORT, locale)
                            }
                            stringResource(
                                R.string.schedule_semester_label_date_range_with_time_zone,
                                destinationStart,
                                destinationEnd,
                                timeZone,
                            )
                        },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (currentTimeZone != semester.timeZone) {
                        val timeFormatter = remember(locale) {
                            DateTimeFormatter.ofLocalizedDateTime(SHORT).withLocale(locale)
                        }
                        val (localStart, localEnd) = remember(
                            semester, currentTimeZone, timeFormatter,
                        ) {
                            val start = semester.startDate.atStartOfDayIn(semester.timeZone)
                                .toLocalDateTime(currentTimeZone).toJavaLocalDateTime()
                            val end = semester.endDate.atStartOfDayIn(semester.timeZone)
                                .toLocalDateTime(currentTimeZone).toJavaLocalDateTime()
                            val startTime = timeFormatter.format(start)
                            val endTime = timeFormatter.format(end)
                            startTime to endTime
                        }
                        val timeZone = remember(currentTimeZone, locale) {
                            currentTimeZone.toJavaZoneId().getDisplayName(FULL, locale)
                        }
                        LabelWithIcon(
                            icon = R.drawable.date_range_24px,
                            label = stringResource(
                                R.string.schedule_semester_label_date_range_with_time_zone,
                                localStart,
                                localEnd,
                                timeZone,
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LabelWithIcon(
                        icon = R.drawable.schedule_24px,
                        label = stringResource(
                            R.string.schedule_semester_label_length,
                            remember(semester, locale) {
                                semester.startDate.daysUntil(semester.endDate).days.format(locale)
                            },
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (destinationToday < semester.startDate) {
                        LabelWithIcon(
                            icon = R.drawable.event_upcoming_24px,
                            label = stringResource(
                                R.string.schedule_semester_label_start_in,
                                remember(destinationToday, semester, locale) {
                                    destinationToday.daysUntil(semester.startDate).days.format(
                                        locale,
                                    )
                                },
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (destinationToday >= semester.startDate && destinationToday < semester.endDate) {
                val progressFormatter = remember(locale) {
                    NumberFormat.getPercentInstance(locale)
                }

                val (length, spent) = remember(destinationToday, currentTimeZone, semester) {
                    val length = semester.startDate.daysUntil(semester.endDate)
                    val spent = semester.startDate.daysUntil(destinationToday)
                    length to spent
                }

                val progress = if (length == 0) 1f else spent.toFloat() / length

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = remember(progressFormatter, progress) {
                            progressFormatter.format(progress)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.schedule_semester_label_remaining,
                            remember(length, spent, locale) {
                                (length - spent).days.format(locale)
                            },
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        var exportationContent by remember { mutableStateOf<String?>(null) }
        var backupContent by remember { mutableStateOf<String?>(null) }

        Box {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                offset = menuOffset,
            ) {
                DropdownMenuItem(
                    text = { Text(semester.name) },
                    onClick = {},
                    enabled = false,
                )
                DropdownMenuItem(
                    onClick = {
                        showMenu = false
                        coroutineScope.launch {
                            exportationContent = onExport?.invoke()
                        }
                    },
                    text = { Text(stringResource(R.string.schedule_semester_label_export)) },
                    shape = MenuDefaults.leadingItemThemedShape,
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.file_export_24px),
                            contentDescription = null,
                        )
                    },
                    enabled = onExport != null,
                )
                DropdownMenuItem(
                    onClick = {
                        showMenu = false
                        coroutineScope.launch {
                            backupContent = onBackup?.invoke()
                        }
                    },
                    text = { Text(stringResource(R.string.schedule_semester_label_backup)) },
                    shape = MenuDefaults.middleItemShape,
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.file_export_24px),
                            contentDescription = null,
                        )
                    },
                    enabled = onBackup != null,
                )
                DropdownMenuItem(
                    onClick = {
                        showMenu = false
                        coroutineScope.launch {
                            onDelete?.invoke()
                        }
                    },
                    text = { Text(stringResource(R.string.schedule_semester_label_delete)) },
                    shape = MenuDefaults.trailingItemThemedShape,
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.delete_24px),
                            contentDescription = null,
                        )
                    },
                    enabled = onDelete != null && semester.id != SemesterEntity.DefaultSemesterId,
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.error,
                        leadingIconColor = MaterialTheme.colorScheme.error,
                    ),
                )
            }
        }

        exportationContent?.let {
            ICalendarContentDialog(
                onDismissRequest = { exportationContent = null },
                title = stringResource(R.string.schedule_semester_label_export),
                content = it,
            )
        }

        backupContent?.let {
            ICalendarContentDialog(
                onDismissRequest = { backupContent = null },
                title = stringResource(R.string.schedule_semester_label_backup),
                content = it,
            )
        }
    }
}

@Composable
private fun ICalendarContentDialog(
    onDismissRequest: () -> Unit,
    title: String,
    content: String,
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        confirmButton = {
            TextButton(
                onClick = {
                    val data = ClipData.newPlainText(null, content)
                    coroutineScope.launch {
                        clipboard.setClipEntry(ClipEntry(data))
                        onDismissRequest()
                    }
                },
                content = { Text(stringResource(android.R.string.copy)) },
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                content = { Text(stringResource(android.R.string.cancel)) },
            )
        },
        text = {
            // TODO: use MarkdownCode directly after refactor
            SelectionContainer {
                Text(
                    text = content,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(rememberScrollState()),
                    fontFamily = Monospace,
                    softWrap = false,
                )
            }
        },
    )
}

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
                    onDeleteEvent = { _, _ -> },
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
                    onEdit = {},
                    onRequestBatchEdit = {},
                    onDelete = {},
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
    shape: Shape = MaterialTheme.shapes.extraLarge,
    locale: Locale = LocalConfiguration.current.locales[0],
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalScreenId provides Uuid.generateV7()) {
        Surface(
            modifier = modifier
                .sizeIn(minWidth = DialogMinWidth, maxWidth = DialogMaxWidth)
                .dialogContentPointerInput(),
            shape = shape,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
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
    onDeleteEvent: (
        event: Event,
        onDeleted: (onUndo: () -> Unit) -> Unit,
    ) -> Unit,
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
        modifier = modifier.animateContentSize(),
        state = lazyListState,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
    ) {
        items(
            count = events.size,
            key = { events[it].id },
        ) { index ->
            val event = events[index]

            Column(
                modifier = Modifier.animateItem(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
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
                    onDelete = { onDeleteEvent(event, it) },
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

    val updatedEvents by rememberUpdatedState(events)
    val updatedResources by rememberUpdatedState(resources)
    val updatedLocale by rememberUpdatedState(locale)

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(STARTED) {
            while (true) {
                delay(1.seconds)
                indicator =
                    calculateIndicator(updatedEvents, updatedResources, locale = updatedLocale)
            }
        }
    }
}

private typealias OnRequestEdit = (EventEdit) -> Unit
private typealias OnEdit = (EventEditResult) -> Unit
private typealias OnRequestBatchEdit = (EventEditChange) -> Unit

@Composable
fun EventInformationScreen(
    event: Event,
    onBack: () -> Unit,
    onRequestEdit: OnRequestEdit,
    onEdit: OnEdit,
    onRequestBatchEdit: OnRequestBatchEdit,
    onDelete: (
        onDeleted: (onUndo: () -> Unit) -> Unit,
    ) -> Unit,
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
                onClick = onBack,
                onDelete = onDelete,
                modifier = eventModifier,
                selected = true,
                locale = locale,
                timeZone = timeZone,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }.first().measure(constraints)

        val eventWidth = eventPlaceable.width
        val eventHeight = eventPlaceable.height + 16.dp.roundToPx()

        val scrimPlaceable = subcompose("scrim") {
            val tonalElevation = LocalAbsoluteTonalElevation.current
            val color = MaterialTheme.colorScheme.surfaceColorAtElevation(tonalElevation)

            Column(Modifier.fillMaxSize()) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(color),
                )
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 64.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    color,
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
        }.first().measure(Constraints.fixed(eventWidth, eventHeight))

        val listPlaceable = subcompose("list") {
            DetailedEventInformation(
                event = event,
                onRequestEdit = onRequestEdit,
                onEdit = onEdit,
                onRequestBatchEdit = onRequestBatchEdit,
                modifier = listModifier(PaddingValues(top = eventHeight.toDp())),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }.first().measure(constraints)

        val listWidth = listPlaceable.width
        val listHeight = listPlaceable.height

        val width = maxOf(eventWidth, listWidth)
        val height = maxOf(eventHeight, listHeight)

        layout(width, height) {
            listPlaceable.placeRelative(0, 0)
            scrimPlaceable.placeRelative(0, 0)
            eventPlaceable.placeRelative(0, 0)
        }
    }
}

@Composable
fun EventEditScreen(
    event: Event,
    edit: EventEdit,
    onBack: () -> Unit,
    onEdit: OnEdit,
    onDelete: (
        onDeleted: (onUndo: () -> Unit) -> Unit,
    ) -> Unit,
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
            Column(
                modifier = eventModifier,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailedEvent(
                    event = event,
                    onClick = onBack,
                    onDelete = onDelete,
                    selected = true,
                    locale = locale,
                    timeZone = timeZone,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
                edit.SharedListItem(
                    event = event,
                    onRequestEdit = {},
                    onEdit = {},
                    onRequestBatchEdit = {},
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        }.first().measure(constraints)

        val eventWidth = eventPlaceable.width
        val eventHeight = eventPlaceable.height + 16.dp.roundToPx()

        val editPlaceables = subcompose("edit") {
            edit.EditContent(
                event = event,
                onBack = onBack,
                onEdit = onEdit,
                modifier = editModifier(PaddingValues(top = eventHeight.toDp())),
            )
        }.fastMap { it.measure(constraints) }

        val editWidth = editPlaceables.fastMaxBy { it.width }?.width ?: 0
        val editHeight = editPlaceables.fastMaxBy { it.height }?.height ?: 0

        val width = maxOf(eventWidth, editWidth)
        val height = maxOf(eventHeight, editHeight)

        layout(width, height) {
            editPlaceables.fastForEach { it.placeRelative(0, 0) }
            eventPlaceable.placeRelative(0, 0)
        }
    }
}

@Serializable
@Immutable
sealed class EventEdit {
    @Composable
    open fun EditContent(
        event: Event,
        onBack: () -> Unit,
        onEdit: OnEdit,
        modifier: Modifier = Modifier,
    ) {
    }

    @Composable
    fun SharedListItem(
        event: Event,
        onRequestEdit: OnRequestEdit,
        onEdit: OnEdit,
        onRequestBatchEdit: OnRequestBatchEdit,
        animatedVisibilityScope: AnimatedVisibilityScope?,
    ) {
        localSharedTransitionScope {
            ListItem(
                event = event,
                onRequestEdit = onRequestEdit,
                onEdit = onEdit,
                onRequestBatchEdit = onRequestBatchEdit,
                modifier = Modifier.run {
                    if (animatedVisibilityScope != null) {
                        sharedElement(
                            rememberSharedContentState(
                                ScheduleSharedKey.EditItem(
                                    id = event.id,
                                    edit = this@EventEdit,
                                    screenId = LocalScreenId.current,
                                ),
                            ),
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    } else this
                },
            )
        }
    }

    @Composable
    abstract fun ListItem(
        event: Event,
        onRequestEdit: OnRequestEdit,
        onEdit: OnEdit,
        onRequestBatchEdit: OnRequestBatchEdit,
        modifier: Modifier = Modifier,
    )

    protected val OnRequestEdit.onClick: () -> Unit
        get() = { this(this@EventEdit) }

    companion object {
        val allEdits = listOf(
            LabelEdit,
            LocationEdit,
            NotesEdit,
            CourseEdit,
            PriorityEdit,
            RemindersEdit,
            InstructorEdit,
            RecurrenceRuleEdit,
            SemesterEdit,
            ColorEdit,
        )
    }
}

@Immutable
fun interface EventEditChange {
    fun Event.applyChange(): Event
}

@Immutable
fun interface EventEditResult {
    suspend fun applyWith(dao: ScheduleDao): Event
}

@Composable
fun DetailedEvent(
    event: Event,
    onClick: () -> Unit,
    onDelete: (
        onDeleted: (onUndo: () -> Unit) -> Unit,
    ) -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    highlighted: Boolean = false,
    locale: Locale = LocalConfiguration.current.locales[0],
    timeZone: TimeZone = rememberSystemTimeZone(),
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = event.color.compose,
    interactionSource: MutableInteractionSource? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    additionalContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current

    val coroutineScope = rememberCoroutineScope()

    val contentColor = MaterialTheme.colorScheme.contentColorFor(color)
        .takeOrElse { contractColorFor(color) }

    val minShadowElevation by animatedVisibilityScope?.transition?.animateDp {
        val sceneTransition = animatedVisibilityScope.transition.parentTransition
        val targetScene = sceneTransition?.targetState as? Scene<*>
        val targetContentKey =
            targetScene?.entries?.lastOrNull()?.contentKey as? EventDetailsSubPageKey
        val currentScene = sceneTransition?.currentState as? Scene<*>
        val currentContentKey =
            currentScene?.entries?.lastOrNull()?.contentKey as? EventDetailsSubPageKey
        val isForward = (targetContentKey?.eventId == event.id && it == Visible) ||
                (currentContentKey?.eventId == event.id && it != Visible)
        if (isForward) 4.dp else 0.dp
    } ?: animateDpAsState(if (selected) 4.dp else 0.dp)

    var showMenu by remember { mutableStateOf(false) }
    var menuOriginalOffset by remember { mutableStateOf(Offset.Zero) }
    val menuOffset = remember(density, menuOriginalOffset) {
        with(density) {
            DpOffset(
                x = menuOriginalOffset.x.toDp(),
                y = menuOriginalOffset.y.toDp(),
            )
        }
    }

    Box {
        localSharedTransitionScope {
            Surface(
                onClick = onClick,
                modifier = modifier
                    .fillMaxWidth()
                    .detectLongPress(
                        requireUnconsumed = false,
                        firstDownPass = Initial,
                        upOrCancellationPass = Initial,
                        onFinish = { it.consume() },
                    ) {
                        hapticFeedback.performHapticFeedback(LongPress)
                        menuOriginalOffset = it.position
                        showMenu = true
                    }
                    .run {
                        if (animatedVisibilityScope != null) sharedBounds(
                            rememberSharedContentState(
                                ScheduleSharedKey.Event(
                                    id = event.id,
                                    screenId = LocalScreenId.current,
                                ),
                            ),
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

        Box {
            EventDropdownMenu(
                event = event,
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                onDelete = {
                    onDelete { onUndo ->
                        coroutineScope.launch {
                            GlobalViewModel.showSnackbar(
                                message = R.string.label_deleted.asStringRes(event.name),
                                action = SnackbarAction(
                                    R.string.label_undo.asStringRes(),
                                    onUndo,
                                ),
                                withDismissAction = true,
                                duration = SnackbarDuration.Long,
                            )
                        }
                    }
                },
                offset = menuOffset,
            )
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
fun EventDropdownMenu(
    event: Event,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
    offset: DpOffset = Zero,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
    ) {
        DropdownMenuItem(
            text = { Text(event.name) },
            onClick = {},
            enabled = false,
        )
        DeleteDropdownMenuItem(
            onDelete = onDelete,
        )
    }
}

@Composable
fun DetailedEventInformation(
    event: Event,
    onRequestEdit: OnRequestEdit,
    onEdit: OnEdit,
    onRequestBatchEdit: OnRequestBatchEdit,
    modifier: Modifier = Modifier,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        EventEdit.allEdits.fastForEach {
            it.SharedListItem(
                event,
                onRequestEdit,
                onEdit,
                onRequestBatchEdit,
                animatedVisibilityScope,
            )
        }
    }
}

@Immutable
private object LabelEdit : EventEdit() {
    @Composable
    override fun ListItem(
        event: Event,
        onRequestEdit: OnRequestEdit,
        onEdit: OnEdit,
        onRequestBatchEdit: OnRequestBatchEdit,
        modifier: Modifier,
    ) {
        DetailedEventInformationEntry(
            icon = R.drawable.label_24px,
            label = R.string.schedule_event_name_label,
            modifier = modifier,
            onClick = onRequestEdit.onClick,
            content = { Text(event.name.ifBlank { stringResource(R.string.schedule_event_name_label_none) }) },
        )
    }
}

@Immutable
private object LocationEdit : EventEdit() {
    @Composable
    override fun ListItem(
        event: Event,
        onRequestEdit: OnRequestEdit,
        onEdit: OnEdit,
        onRequestBatchEdit: OnRequestBatchEdit,
        modifier: Modifier,
    ) {
        DetailedEventInformationEntry(
            icon = R.drawable.location_on_24px,
            label = R.string.schedule_event_location_label,
            modifier = modifier,
            onClick = onRequestEdit.onClick,
            content = { Text(event.location.ifBlank { stringResource(R.string.schedule_event_location_label_none) }) },
        )
    }
}

@Immutable
private object NotesEdit : EventEdit() {
    @Composable
    override fun EditContent(
        event: Event,
        onBack: () -> Unit,
        onEdit: OnEdit,
        modifier: Modifier,
    ) {
        Column(
            modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            var text by remember { mutableStateOf(event.notes ?: "") }
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .widthIn(max = TextFieldMaxWidth)
                    .fillMaxWidth(),
                label = { Text(stringResource(R.string.schedule_event_edit_notes_label_input)) },
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, alignment = Alignment.End),
            ) {
                TextButton(onClick = onBack) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        onEdit(
                            EventEditResult { dao ->
                                val text = text.trim().ifBlank { null }
                                when (event) {
                                    is Normal -> event.copy(notes = text)
                                    is Course -> event.copy(notes = text)
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

    @Composable
    override fun ListItem(
        event: Event,
        onRequestEdit: OnRequestEdit,
        onEdit: OnEdit,
        onRequestBatchEdit: OnRequestBatchEdit,
        modifier: Modifier,
    ) {
        DetailedEventInformationEntry(
            icon = R.drawable.notes_24px,
            label = R.string.schedule_event_notes_label,
            modifier = modifier,
            onClick = { onRequestEdit(NotesEdit) },
        ) {
            Text(
                text = event.notes?.ifBlank { null }
                    ?: stringResource(R.string.schedule_event_notes_label_none),
            )
        }
    }
}

@Immutable
private object CourseEdit : EventEdit() {
    @Composable
    override fun ListItem(
        event: Event,
        onRequestEdit: OnRequestEdit,
        onEdit: OnEdit,
        onRequestBatchEdit: OnRequestBatchEdit,
        modifier: Modifier,
    ) {
        DetailedEventInformationEntry(
            icon = R.drawable.book_24px,
            label = R.string.schedule_event_course_label,
            modifier = modifier,
            onClick = onRequestEdit.onClick,
        ) {
            if (event is Course) {
                Text(event.course.name)
            } else {
                Text(stringResource(R.string.schedule_event_course_label_none))
            }
        }
    }
}

@Immutable
private object PriorityEdit : EventEdit() {
    @Composable
    override fun ListItem(
        event: Event,
        onRequestEdit: OnRequestEdit,
        onEdit: OnEdit,
        onRequestBatchEdit: OnRequestBatchEdit,
        modifier: Modifier,
    ) {
        var showMenu by remember { mutableStateOf(false) }
        var lastChange by remember { mutableStateOf<EventEditChange?>(null) }

        DetailedEventInformationEntry(
            icon = R.drawable.priority_24px,
            label = R.string.schedule_event_priority_label,
            modifier = modifier,
            onClick = { showMenu = true },
            trailingContent = {
                AnimatedVisibility(
                    visible = lastChange != null,
                    enter = fadeIn() + expandHorizontally(
                        expandFrom = Alignment.Start,
                        clip = false,
                    ),
                    exit = fadeOut() + shrinkHorizontally(
                        shrinkTowards = Alignment.Start,
                        clip = false,
                    ),
                ) {
                    BatchEditButton {
                        lastChange?.let { onRequestBatchEdit(it) }
                    }
                }
            },
        ) {
            Box {
                event.priority.Label()

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    val entries = ICalendarPriority.entries

                    entries.forEachIndexed { index, priority ->
                        DropdownMenuItem(
                            onClick = {
                                onEdit(
                                    EventEditResult { dao ->
                                        EventEditChange {
                                            when (this) {
                                                is Normal -> copy(priority = priority)
                                                is Course -> copy(priority = priority)
                                            }
                                        }.run {
                                            event.applyChange().also {
                                                dao.updateEvent(it.toEntity())
                                                lastChange = this
                                            }
                                        }
                                    },
                                )
                                showMenu = false
                            },
                            text = { priority.Label() },
                            shape = MenuDefaults.itemThemedShape(index, entries.size),
                        )
                    }
                }
            }
        }
    }
}

@Immutable
private object RemindersEdit : EventEdit() {
    @Composable
    override fun ListItem(
        event: Event,
        onRequestEdit: OnRequestEdit,
        onEdit: OnEdit,
        onRequestBatchEdit: OnRequestBatchEdit,
        modifier: Modifier,
    ) {
        DetailedEventInformationEntry(
            icon = R.drawable.alarm_24px,
            label = R.string.schedule_event_reminders_label,
            modifier = modifier,
            onClick = onRequestEdit.onClick,
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
    }
}

@Immutable
private object InstructorEdit : EventEdit() {
    @Composable
    override fun ListItem(
        event: Event,
        onRequestEdit: OnRequestEdit,
        onEdit: OnEdit,
        onRequestBatchEdit: OnRequestBatchEdit,
        modifier: Modifier,
    ) {
        if (event !is Course) return
        DetailedEventInformationEntry(
            icon = R.drawable.podium_24px,
            label = R.string.schedule_event_instructor_label,
            modifier = modifier,
            onClick = onRequestEdit.onClick,
            content = { Text(event.instructor.ifBlank { stringResource(R.string.schedule_event_instructor_label_none) }) },
        )
    }
}

@Immutable
private object RecurrenceRuleEdit : EventEdit() {
    @Composable
    override fun ListItem(
        event: Event,
        onRequestEdit: OnRequestEdit,
        onEdit: OnEdit,
        onRequestBatchEdit: OnRequestBatchEdit,
        modifier: Modifier,
    ) {
        DetailedEventInformationEntry(
            icon = R.drawable.event_repeat_24px,
            label = R.string.schedule_event_recurrence_rule_label,
            modifier = modifier,
            onClick = onRequestEdit.onClick,
            content = {},
        )
    }
}

@Immutable
private object SemesterEdit : EventEdit() {
    @Composable
    override fun ListItem(
        event: Event,
        onRequestEdit: OnRequestEdit,
        onEdit: OnEdit,
        onRequestBatchEdit: OnRequestBatchEdit,
        modifier: Modifier,
    ) {
        DetailedEventInformationEntry(
            icon = R.drawable.cycle_24px,
            label = R.string.schedule_event_semester_label,
            modifier = modifier,
            onClick = {},
            content = { Text(event.semester.name) },
        )
    }
}

@Immutable
private object ColorEdit : EventEdit() {
    @Composable
    override fun ListItem(
        event: Event,
        onRequestEdit: OnRequestEdit,
        onEdit: OnEdit,
        onRequestBatchEdit: OnRequestBatchEdit,
        modifier: Modifier,
    ) {
        DetailedEventInformationEntry(
            icon = R.drawable.palette_24px,
            label = R.string.schedule_event_color_label,
            modifier = modifier,
            onClick = {},
            content = { event.color.Label() },
        )
    }
}

@Composable
private fun DetailedEventInformationEntry(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    shapes: ListItemShapes = DetailedEventInformationEntryDefaults.shapes,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        ListItem(
            onClick = onClick,
            modifier = modifier,
            leadingContent = { DetailedEventInformationEntryIcon(icon) },
            trailingContent = trailingContent,
            overlineContent = { Text(stringResource(label)) },
            shapes = shapes,
            content = content,
        )
    } else {
        ListItem(
            headlineContent = content,
            modifier = modifier,
            leadingContent = { DetailedEventInformationEntryIcon(icon) },
            trailingContent = trailingContent,
            overlineContent = { Text(stringResource(label)) },
        )
    }
}

private object DetailedEventInformationEntryDefaults {
    private var _shapes: Pair<Shapes, ListItemShapes>? = null
    val shapes
        @Composable get() = MaterialTheme.shapes.run {
            get() ?: ListItemDefaults.shapes(
                shape = MaterialTheme.shapes.large,
                selectedShape = MaterialTheme.shapes.extraLarge,
                pressedShape = MaterialTheme.shapes.extraLarge,
                focusedShape = MaterialTheme.shapes.extraLarge,
                hoveredShape = MaterialTheme.shapes.largeIncreased,
                draggedShape = MaterialTheme.shapes.extraLarge,
            ).also { set(it) }
        }

    private fun Shapes.get() = _shapes?.takeIf { it.first == this }?.second
    private fun Shapes.set(shapes: ListItemShapes) {
        _shapes = this to shapes
    }
}

@Composable
private fun BatchEditButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(
            painterResource(R.drawable.checklist_24px),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.schedule_event_edit_batch_label))
    }
}

@Composable
fun EventBatchEditDialog(
    event: Event,
    change: EventEditChange,
    getAffection: suspend (List<EventEdit>) -> Int,
    onConfirm: (List<EventEdit>) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_event_edit_batch_label)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {

            }
        },
        confirmButton = {
            TextButton(onClick = {}) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun DetailedEventInformationEntryIcon(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
) {
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

    val indicatorText = if (isNear) {
        resources.getString(
            R.string.schedule_event_list_indicator_label_close_to_end,
            offset.format(locale),
        )
    } else {
        val timeSinceStart = now - event.startTime
        resources.getString(
            R.string.schedule_event_list_indicator_label_started,
            timeSinceStart.format(locale),
        )
    }

    return IndicatorData(
        index = candidateIndex,
        isIn = true,
        text = indicatorText,
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
        verticalAlignment = if (label.count { it == '\n' } == 0) {
            Alignment.CenterVertically
        } else {
            Alignment.Top
        },
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
enum class BatchEditScope {
    Recurrence, Semester
}

@Immutable
private sealed interface ScheduleSharedKey {
    @Immutable
    data class Event(
        val id: Uuid,
        val screenId: Uuid? = Uuid.generateV7(),
    ) : ScheduleSharedKey

    @Immutable
    data class EditItem(
        val id: Uuid,
        val edit: EventEdit,
        val screenId: Uuid? = Uuid.generateV7(),
    ) : ScheduleSharedKey
}

private val LocalScreenId = staticCompositionLocalOf<Uuid?> { null }
