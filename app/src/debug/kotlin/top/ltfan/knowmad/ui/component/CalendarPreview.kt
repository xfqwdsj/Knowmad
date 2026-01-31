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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import top.ltfan.knowmad.data.schedule.Event
import top.ltfan.knowmad.data.schedule.ICalendarColor
import top.ltfan.knowmad.data.schedule.SemesterEntity
import top.ltfan.knowmad.ui.theme.AppTheme
import top.ltfan.knowmad.ui.util.AppWindowInsets
import java.util.Locale
import kotlin.random.Random
import kotlin.time.Instant

@Preview
@Composable
fun CalendarPreview() {
    val semester = remember {
        SemesterEntity(
            name = "Spring Semester",
            startDate = LocalDate(2024, 2, 15),
            endDate = LocalDate(2024, 6, 30),
            timeZone = kotlinx.datetime.TimeZone.UTC,
        )
    }

    val coroutineScope = rememberCoroutineScope()

    val state = rememberCalendarState()

    AppTheme {
        Surface {
            var height by remember { mutableStateOf(700.dp) }
            Column(
                modifier = Modifier
                    .windowInsetsPadding(AppWindowInsets)
                    .fillMaxWidth()
                    .height(800.dp),
            ) {
                Row {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                state.animateToMode(
                                    if (state.currentMode == Month) Week else Month,
                                )
                            }
                        },
                    ) {
                        Text("Toggle Mode")
                    }
                    Slider(
                        value = height / 700.dp,
                        onValueChange = { height = it * 700.dp },
                    )
                }
                Calendar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height),
                    state = state,
                    locale = Locale.getDefault(),
                ) { _, _ ->
                    flowOf(
                        listOf(
                            Event.Normal(
                                semester = semester,
                                name = "Mathematics Lecture",
                                startTime = Instant.parse("2024-03-01T09:00:00Z"),
                                endTime = Instant.parse("2024-03-01T10:30:00Z"),
                                location = "Room 101, Science Building",
                                color = ICalendarColor.fromRandom(Random),
                                notes = "Weekly mathematics lecture covering calculus and linear algebra.",
                            ),
                            Event.Normal(
                                semester = semester,
                                name = "Mathematics Lecture",
                                startTime = Instant.parse("2024-03-01T09:00:00Z"),
                                endTime = Instant.parse("2024-03-01T10:30:00Z"),
                                location = "Room 101, Science Building",
                                color = ICalendarColor.fromRandom(Random),
                                notes = "Weekly mathematics lecture covering calculus and linear algebra.",
                            ),
                        ),
                    )
                }
            }
        }
    }
}
