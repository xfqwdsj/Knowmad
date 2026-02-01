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

package top.ltfan.knowmad.data.schedule

import biweekly.ICalDataType
import biweekly.ICalVersion
import biweekly.io.ParseContext
import biweekly.io.WriteContext
import biweekly.io.scribe.property.ICalPropertyScribe
import biweekly.io.scribe.property.RecurrencePropertyScribe
import biweekly.io.scribe.property.TextPropertyScribe
import biweekly.io.text.ICalReader
import biweekly.io.text.ICalWriter
import biweekly.parameter.ICalParameters
import biweekly.property.ICalProperty
import biweekly.property.RecurrenceProperty
import biweekly.property.TextProperty
import biweekly.util.Recurrence
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import java.io.InputStream
import java.io.OutputStream
import kotlin.reflect.KClass
import kotlin.uuid.Uuid

val ICalendarVersion = ICalVersion.V2_0

fun customICalReader(stream: InputStream) = ICalReader(stream).apply {
    registerScribe(SemesterProperty)
    registerScribe(CourseProperty)
    registerScribe(InstructorProperty)
    registerScribe(KnowmadRecurrenceProperty)
}

fun customICalWriter(stream: OutputStream) = ICalWriter(stream, ICalendarVersion).apply {
    registerScribe(SemesterProperty)
    registerScribe(CourseProperty)
    registerScribe(InstructorProperty)
    registerScribe(KnowmadRecurrenceProperty)
}

abstract class UuidProperty : ICalProperty() {
    abstract val uuid: Uuid?
}

abstract class UuidPropertyScribe<T : UuidProperty>(
    clazz: KClass<T>,
    propertyName: String,
) : ICalPropertyScribe<T>(clazz.java, propertyName, TEXT) {
    protected override fun _writeText(property: T?, context: WriteContext?) =
        property?.uuid?.toString()

    protected override fun _parseText(
        value: String?,
        dataType: ICalDataType?,
        parameters: ICalParameters?,
        context: ParseContext?,
    ) = newInstance(parse(value), dataType, parameters, context)

    abstract fun newInstance(
        value: Uuid?,
        dataType: ICalDataType?,
        parameters: ICalParameters?,
        context: ParseContext?,
    ): T?

    fun parse(value: String?): Uuid? = value?.let { Uuid.parseOrNull(it) }
}

data class SemesterProperty(val semester: SemesterEntity) : UuidProperty() {
    override val uuid = semester.id

    companion object : UuidPropertyScribe<SemesterProperty>(
        SemesterProperty::class, SemesterProperty.PROPERTY_NAME,
    ) {
        const val PROPERTY_NAME = "X-KNOWMAD-SEMESTER"

        const val PARAM_START_DATE = "X-START-DATE"
        const val PARAM_END_DATE = "X-END-DATE"
        const val PARAM_TIME_ZONE = "X-TIME-ZONE"

        override fun newInstance(
            value: Uuid?,
            dataType: ICalDataType?,
            parameters: ICalParameters?,
            context: ParseContext?,
        ): SemesterProperty? {
            return SemesterProperty(
                SemesterEntity(
                    id = value ?: Uuid.generateV7(),
                    name = parameters?.label ?: return null,
                    startDate = LocalDate.parse(
                        parameters.get(PARAM_START_DATE).singleOrNull() ?: return null,
                        LocalDate.Formats.ISO,
                    ),
                    endDate = LocalDate.parse(
                        parameters.get(PARAM_END_DATE).singleOrNull() ?: return null,
                        LocalDate.Formats.ISO,
                    ),
                    timeZone = TimeZone.of(
                        parameters.get(PARAM_TIME_ZONE).singleOrNull() ?: return null,
                    ),
                ),
            )
        }
    }

    init {
        parameters.apply {
            label = semester.name
            put(PARAM_START_DATE, semester.startDate.format(LocalDate.Formats.ISO))
            put(PARAM_END_DATE, semester.endDate.format(LocalDate.Formats.ISO))
            put(PARAM_TIME_ZONE, semester.timeZone.id)
        }
    }
}

data class CourseProperty(val course: CourseEntity) : UuidProperty() {
    override val uuid = course.id

    companion object : UuidPropertyScribe<CourseProperty>(
        CourseProperty::class, CourseProperty.PROPERTY_NAME,
    ) {
        const val PROPERTY_NAME = "X-KNOWMAD-COURSE"

        const val PARAM_SEMESTER_ID = "X-SEMESTER-ID"
        const val PARAM_INSTRUCTOR = "X-INSTRUCTOR"
        const val PARAM_LOCATION = "X-LOCATION"

        override fun newInstance(
            value: Uuid?,
            dataType: ICalDataType?,
            parameters: ICalParameters?,
            context: ParseContext?,
        ): CourseProperty? {
            return CourseProperty(
                CourseEntity(
                    id = value ?: Uuid.generateV7(),
                    semesterId = Uuid.parseOrNull(
                        parameters?.get(PARAM_SEMESTER_ID)?.singleOrNull() ?: return null,
                    ) ?: return null,
                    name = parameters.label ?: return null,
                    instructor = parameters.get(PARAM_INSTRUCTOR).singleOrNull() ?: return null,
                    location = parameters.get(PARAM_LOCATION).singleOrNull() ?: return null,
                ),
            )
        }
    }

    init {
        parameters.apply {
            put(PARAM_SEMESTER_ID, course.semesterId.toString())
            label = course.name
            put(PARAM_INSTRUCTOR, course.instructor)
            put(PARAM_LOCATION, course.location)
        }
    }
}

data class KnowmadRecurrenceProperty(
    val recurrenceRule: ICalendarRecurrenceRule,
) : RecurrenceProperty(recurrenceRule.toICalValue()) {
    companion object : RecurrencePropertyScribe<KnowmadRecurrenceProperty>(
        KnowmadRecurrenceProperty::class.java,
        KnowmadRecurrenceProperty.PROPERTY_NAME,
    ) {
        const val PROPERTY_NAME = "X-KNOWMAD-RECURRENCE"

        override fun newInstance(value: Recurrence?): KnowmadRecurrenceProperty? {
            val rule = value?.toICalendarRecurrenceRule() ?: return null
            return KnowmadRecurrenceProperty(rule)
        }
    }
}

data class InstructorProperty(val value: String?) : TextProperty(value) {
    companion object : TextPropertyScribe<InstructorProperty>(
        InstructorProperty::class.java, InstructorProperty.PROPERTY_NAME,
    ) {
        const val PROPERTY_NAME = "X-INSTRUCTOR"

        override fun newInstance(value: String?, version: ICalVersion?) = InstructorProperty(value)
    }
}
