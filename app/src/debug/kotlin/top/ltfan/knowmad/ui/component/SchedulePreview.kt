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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kizitonwose.calendar.core.minusDays
import com.kizitonwose.calendar.core.now
import com.kizitonwose.calendar.core.plusDays
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import top.ltfan.knowmad.data.schedule.Event
import top.ltfan.knowmad.data.schedule.SemesterEntity
import top.ltfan.knowmad.ui.theme.AppTheme
import top.ltfan.knowmad.ui.util.AppWindowInsets
import top.ltfan.omnical.icalendar.ICalendarColor
import java.util.Locale
import kotlin.time.Instant

@Preview
@Composable
fun MonthBottomSheetContentPreview() {
    val semester1 = remember {
        SemesterEntity(
            name = "Semester 1",
            startDate = LocalDate(2024, 1, 1),
            endDate = LocalDate(2024, 12, 31),
            timeZone = UTC,
        )
    }

    val semester2 = remember {
        SemesterEntity(
            name = "Semester 2",
            startDate = LocalDate.now().minusDays(279),
            endDate = LocalDate.now().plusDays(201),
            timeZone = TimeZone.currentSystemDefault(),
        )
    }

    val semester3 = remember {
        SemesterEntity(
            name = "Semester 3",
            startDate = LocalDate.now().minusDays(279),
            endDate = LocalDate.now().plusDays(201),
            timeZone = UTC,
        )
    }

    val semester4 = remember {
        SemesterEntity(
            name = "Semester 4",
            startDate = LocalDate.now().plusDays(30),
            endDate = LocalDate.now().plusDays(300),
            timeZone = TimeZone.currentSystemDefault(),
        )
    }

    AppTheme {
        Surface {
            MonthBottomSheetContent(
                month = YearMonth.now(),
                semesters = listOf(semester1, semester2, semester3, semester4),
                locale = Locale.getDefault(),
                currentTimeZone = TimeZone.currentSystemDefault(),
                today = LocalDate.now(),
            )
        }
    }
}

@Preview
@Composable
fun SemesterInformationPreview() {
    val semester1 = remember {
        SemesterEntity(
            name = "Semester 1",
            startDate = LocalDate(2024, 1, 1),
            endDate = LocalDate(2024, 12, 31),
            timeZone = UTC,
        )
    }

    val semester2 = remember {
        SemesterEntity(
            name = "Semester 2",
            startDate = LocalDate.now().minusDays(279),
            endDate = LocalDate.now().plusDays(201),
            timeZone = TimeZone.currentSystemDefault(),
        )
    }

    val semester3 = remember {
        SemesterEntity(
            name = "Semester 3",
            startDate = LocalDate.now().minusDays(279),
            endDate = LocalDate.now().plusDays(201),
            timeZone = UTC,
        )
    }

    val semester4 = remember {
        SemesterEntity(
            name = "Semester 4",
            startDate = LocalDate.now().plusDays(30),
            endDate = LocalDate.now().plusDays(300),
            timeZone = TimeZone.currentSystemDefault(),
        )
    }

    AppTheme {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SemesterInformation(
                    semester = semester1,
                    modifier = Modifier.windowInsetsPadding(AppWindowInsets),
                    locale = Locale.getDefault(),
                    currentTimeZone = TimeZone.currentSystemDefault(),
                    today = LocalDate.now(),
                )
                SemesterInformation(
                    semester = semester2,
                    modifier = Modifier.windowInsetsPadding(AppWindowInsets),
                    locale = Locale.getDefault(),
                    currentTimeZone = TimeZone.currentSystemDefault(),
                    today = LocalDate.now(),
                )
                SemesterInformation(
                    semester = semester3,
                    modifier = Modifier.windowInsetsPadding(AppWindowInsets),
                    locale = Locale.getDefault(),
                    currentTimeZone = TimeZone.currentSystemDefault(),
                    today = LocalDate.now(),
                )
                SemesterInformation(
                    semester = semester4,
                    modifier = Modifier.windowInsetsPadding(AppWindowInsets),
                    locale = Locale.getDefault(),
                    currentTimeZone = TimeZone.currentSystemDefault(),
                    today = LocalDate.now(),
                )
            }
        }
    }
}

@Preview
@Composable
fun EventsDialogContentPreview() {
    val semester = remember {
        SemesterEntity(
            name = "Semester 1",
            startDate = LocalDate(2024, 1, 1),
            endDate = LocalDate(2024, 12, 31),
            timeZone = UTC,
        )
    }

    var selectedEvent by remember { mutableStateOf<Event?>(null) }

    AppTheme {
        Surface {
            EventsDialogContent(
                date = LocalDate(2024, 6, 1),
                events = listOf(
                    Event.Normal(
                        semester = semester,
                        name = "Event 1",
                        location = "",
                        color = ICalendarColor.pickFromPalette(8u),
                        startTime = Instant.parse("2024-06-01T09:00:00Z"),
                        endTime = Instant.parse("2024-06-01T10:00:00Z"),
                        notes = "Description for Event 1",
                    ),
                    Event.Normal(
                        semester = semester,
                        name = "Event 2",
                        location = "",
                        color = ICalendarColor.pickFromPalette(18u),
                        startTime = Instant.parse("2024-06-01T11:00:00Z"),
                        endTime = Instant.parse("2024-06-01T12:00:00Z"),
                        notes = "Description for Event 2",
                        priority = P1,
                    ),
                    Event.Normal(
                        semester = semester,
                        name = "Event 3",
                        location = "",
                        color = ICalendarColor.pickFromPalette(0u),
                        startTime = Instant.parse("2024-06-01T14:00:00Z"),
                        endTime = Instant.parse("2024-06-01T15:00:00Z"),
                        priority = P4,
                    ),
                    Event.Normal(
                        semester = semester,
                        name = "Event 4",
                        location = "",
                        color = ICalendarColor.pickFromPalette(0u),
                        startTime = Instant.parse("2024-06-01T16:00:00Z"),
                        endTime = Instant.parse("2024-06-01T17:00:00Z"),
                        notes = "Description for Event 4",
                        priority = P2,
                    ),
                ),
                selectedEvent = selectedEvent,
                onEventSelected = { selectedEvent = it },
                modifier = Modifier.windowInsetsPadding(AppWindowInsets),
                locale = Locale.getDefault(),
                timeZone = UTC,
            )
        }
    }
}
