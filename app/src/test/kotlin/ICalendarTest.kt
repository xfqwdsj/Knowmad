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

package top.ltfan.knowmad.test

import biweekly.ICalVersion
import biweekly.ICalendar
import biweekly.component.VEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import top.ltfan.knowmad.data.schedule.CombinedEvent
import top.ltfan.knowmad.data.schedule.CourseEntity
import top.ltfan.knowmad.data.schedule.CourseProperty
import top.ltfan.knowmad.data.schedule.Event
import top.ltfan.knowmad.data.schedule.EventEntity
import top.ltfan.knowmad.data.schedule.ICalendarColor
import top.ltfan.knowmad.data.schedule.InstructorProperty
import top.ltfan.knowmad.data.schedule.Reminder
import top.ltfan.knowmad.data.schedule.Reminders
import top.ltfan.knowmad.data.schedule.SemesterEntity
import top.ltfan.knowmad.data.schedule.SemesterProperty
import top.ltfan.knowmad.data.schedule.customICalReader
import top.ltfan.knowmad.data.schedule.customICalWriter
import top.ltfan.knowmad.data.schedule.toEvent
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.uuid.Uuid

class ICalendarTest {
    val testSemester = SemesterEntity(
        id = Uuid.parse("00000000-0000-0000-0000-000000000000"),
        name = "Fall 2024",
        startDate = LocalDate(2024, 9, 1),
        endDate = LocalDate(2024, 12, 31),
        timeZone = TimeZone.UTC,
    )

    val testCourse = CourseEntity(
        id = Uuid.parse("11111111-1111-1111-1111-111111111111"),
        semesterId = testSemester.id,
        name = "Introduction to Knowmad",
        instructor = "LTFan",
        location = "Room 101",
    )

    val testEvent1 = EventEntity(
        id = Uuid.parse("22222222-2222-2222-2222-222222222222"),
        semesterId = testSemester.id,
        courseId = testCourse.id,
        name = "Lecture 1",
        instructor = "LTFan's Office",
        location = "Room 101",
        color = ICalendarColor.Cyan,
        startTime = Instant.parse("2024-09-02T10:00:00Z"),
        endTime = Instant.parse("2024-09-02T11:00:00Z"),
        reminders = Reminders.of(Reminder(15.minutes, displayText = "Lecture 1")),
        notes = "Introduction to the course",
        createdAt = Instant.parse("2024-06-01T00:00:00Z"),
        updatedAt = Instant.parse("2024-06-01T00:00:00Z"),
    )

    val testEvent2 = Event.Course(
        id = Uuid.parse("33333333-3333-3333-3333-333333333333"),
        semester = testSemester,
        course = testCourse,
        name = "Lecture 2",
        color = ICalendarColor.Cyan,
        startTime = Instant.parse("2024-09-04T10:00:00Z"),
        endTime = Instant.parse("2024-09-04T11:00:00Z"),
        reminders = Reminders.of(Reminder(15.minutes, displayText = "Lecture 2")),
        createdAt = Instant.parse("2024-06-01T00:00:00Z"),
        updatedAt = Instant.parse("2024-06-01T00:00:00Z"),
    )

    val testEvent3 = Event.Normal(
        id = Uuid.parse("44444444-4444-4444-4444-444444444444"),
        semester = testSemester,
        name = "Midterm Exam",
        location = "Room 102",
        color = ICalendarColor.Orange,
        startTime = Instant.parse("2024-10-15T14:00:00Z"),
        endTime = Instant.parse("2024-10-15T16:00:00Z"),
        reminders = Reminders.of(Reminder(30.minutes, displayText = "Midterm Exam")),
        notes = "Closed book exam.",
        createdAt = Instant.parse("2024-09-01T00:00:00Z"),
        updatedAt = Instant.parse("2024-09-01T00:00:00Z"),
    )

    @Test
    fun `test iCalendar generation and parsing`() {
        val data = listOf(
            CombinedEvent(
                testEvent1, testSemester, testCourse,
            ).toEvent(),
            testEvent2,
            testEvent3,
        )

        val iCal = ICalendar().apply {
            version = ICalVersion.V2_0
            addName(testSemester.name)
            data.forEach { event ->
                addEvent(event.toVEvent())
            }
        }

        val validation = iCal.validate(ICalVersion.V2_0)
        if (!validation.isEmpty) {
            validation.forEach {
                println("ICal Error: $it")
            }
        }
        assertTrue { validation.isEmpty }

        val result = ByteArrayOutputStream().use { stream ->
            customICalWriter(stream).use { writer ->
                writer.write(iCal)
            }
            stream.toByteArray()
        }

        val parsedICal = ByteArrayInputStream(result).use { stream ->
            customICalReader(stream).use { reader ->
                reader.readNext()
            }
        }

        val parsedValidation = parsedICal.validate(ICalVersion.V2_0)
        if (!validation.isEmpty) {
            validation.forEach {
                println("ICal Error: $it")
            }
        }
        assertTrue { parsedValidation.isEmpty }

        assertEquals(iCal, parsedICal)

        val parsedEvent = parsedICal.events.map { Event.parse(it) }

        assertContentEquals(data, parsedEvent)
    }

    @Test
    fun `test iCalendar restoring`() {
        val iCal = ICalendar().apply {
            version = ICalVersion.V2_0
            addName(testSemester.name)
            addEvent(
                // Missing required semester property
                VEvent().apply {
                    setUid(testEvent1.id.toString())
                    setSummary(testEvent1.name)
                    setLocation(testEvent1.location)
                    setDateStart(Date.from(testEvent1.startTime.toJavaInstant()))
                    setDateEnd(Date.from(testEvent1.endTime.toJavaInstant()))
                    setDescription(testEvent1.notes)
                    setDateTimeStamp(Date.from(testEvent1.updatedAt.toJavaInstant()))
                    setCreated(Date.from(testEvent1.createdAt.toJavaInstant()))
                    setLastModified(Date.from(testEvent1.updatedAt.toJavaInstant()))
                },
            )
            addEvent(
                // Missing course property, but can still be restored as normal event
                VEvent().apply {
                    setUid(testEvent1.id.toString())
                    setProperty(SemesterProperty(testSemester))
                    setSummary(testEvent1.name)
                    setLocation(testEvent1.location)
                    setDateStart(Date.from(testEvent1.startTime.toJavaInstant()))
                    setDateEnd(Date.from(testEvent1.endTime.toJavaInstant()))
                    setDescription(testEvent1.notes)
                    setDateTimeStamp(Date.from(testEvent1.updatedAt.toJavaInstant()))
                    setCreated(Date.from(testEvent1.createdAt.toJavaInstant()))
                    setLastModified(Date.from(testEvent1.updatedAt.toJavaInstant()))
                },
            )
            addEvent(
                // Missing instructor property, but can still be restored from course
                VEvent().apply {
                    setUid(testEvent1.id.toString())
                    setProperty(SemesterProperty(testSemester))
                    setProperty(CourseProperty(testCourse))
                    setSummary(testEvent1.name)
                    setLocation(testEvent1.location)
                    setDateStart(Date.from(testEvent1.startTime.toJavaInstant()))
                    setDateEnd(Date.from(testEvent1.endTime.toJavaInstant()))
                    setDescription(testEvent1.notes)
                    setDateTimeStamp(Date.from(testEvent1.updatedAt.toJavaInstant()))
                    setCreated(Date.from(testEvent1.createdAt.toJavaInstant()))
                    setLastModified(Date.from(testEvent1.updatedAt.toJavaInstant()))
                },
            )
            addEvent(
                VEvent().apply {
                    setUid(testEvent1.id.toString())
                    setProperty(SemesterProperty(testSemester))
                    setProperty(CourseProperty(testCourse))
                    setSummary(testEvent1.name)
                    setProperty(InstructorProperty(testEvent1.instructor))
                    setLocation(testEvent1.location)
                    color = testEvent1.color.property
                    setDateStart(Date.from(testEvent1.startTime.toJavaInstant()))
                    setDateEnd(Date.from(testEvent1.endTime.toJavaInstant()))
                    alarms += testEvent1.vAlarms
                    setDescription(testEvent1.notes)
                    setDateTimeStamp(Date.from(testEvent1.updatedAt.toJavaInstant()))
                    setCreated(Date.from(testEvent1.createdAt.toJavaInstant()))
                    setLastModified(Date.from(testEvent1.updatedAt.toJavaInstant()))
                },
            )
        }

        val restoredEvents = iCal.events.map { Event.parse(it) }

        val event = CombinedEvent(
            testEvent1, testSemester, testCourse,
        ).toEvent()

        val expectations = listOf(
            null,
            Event.Normal(
                id = event.id,
                semester = testSemester,
                name = event.name,
                location = event.location,
                color = ICalendarColor.fromId(event.id),
                startTime = event.startTime,
                endTime = event.endTime,
                reminders = Empty,
                notes = event.notes,
                createdAt = event.createdAt,
                updatedAt = event.updatedAt,
            ),
            Event.Course(
                id = event.id,
                semester = testSemester,
                course = testCourse,
                name = event.name,
                color = ICalendarColor.fromId(event.id),
                startTime = event.startTime,
                endTime = event.endTime,
                reminders = Empty,
                createdAt = event.createdAt,
                updatedAt = event.updatedAt,
                notes = event.notes,
            ),
            event,
        )

        assertContentEquals(expectations, restoredEvents)
    }
}
