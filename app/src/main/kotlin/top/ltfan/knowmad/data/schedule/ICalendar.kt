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

val ICalendarRuleArguments = arrayOf(
    SemesterProperty.PROPERTY_NAME,
    SemesterProperty.PARAM_START_DATE,
    SemesterProperty.PARAM_END_DATE,
    SemesterProperty.PARAM_TIME_ZONE,
    CourseProperty.PROPERTY_NAME,
    CourseProperty.PARAM_INSTRUCTOR,
    CourseProperty.PARAM_LOCATION,
    InstructorProperty.PROPERTY_NAME,
)

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

data class SemesterProperty(
    val semester: SemesterEntity?,
    override val errors: List<String>? = null,
) : UuidProperty(), ParsingStatusProperty {
    override val uuid = semester?.id

    companion object : UuidPropertyScribe<SemesterProperty>(
        SemesterProperty::class, SemesterProperty.PROPERTY_NAME,
    ) {
        const val PROPERTY_NAME = "X-KNOWMAD-SEMESTER"

        const val PARAM_START_DATE = "X-START-DATE"
        const val PARAM_END_DATE = "X-END-DATE"
        const val PARAM_TIME_ZONE = "X-TIME-ZONE"

        fun empty(errors: List<String>? = null) = SemesterProperty(null, errors)

        override fun newInstance(
            value: Uuid?,
            dataType: ICalDataType?,
            parameters: ICalParameters?,
            context: ParseContext?,
        ): SemesterProperty {
            val errors = mutableListOf<String>()
            if (parameters == null) {
                errors.add("Semester parameters are missing")
                return empty(errors)
            }
            val id = value ?: run {
                errors.add("Semester ID is missing")
                return empty(errors)
            }

            val defaultSemester = SemesterEntity.createDefault(null)

            val name = parameters.label ?: run {
                errors.add("Name of semester $id is missing")
                defaultSemester.name
            }

            val startDateString = parameters.get(PARAM_START_DATE).singleOrNull() ?: run {
                errors.add("Start date of semester $id is missing")
                null
            }
            val endDateString = parameters.get(PARAM_END_DATE).singleOrNull() ?: run {
                errors.add("End date of semester $id is missing")
                null
            }
            val timeZoneId = parameters.get(PARAM_TIME_ZONE).singleOrNull() ?: run {
                errors.add("Time zone of semester $id is missing")
                null
            }
            val startDate = startDateString?.let {
                runCatching {
                    LocalDate.parse(
                        startDateString,
                        LocalDate.Formats.ISO,
                    )
                }.getOrElse {
                    errors.add("Start date of semester $id is invalid: $startDateString")
                    return empty(errors)
                }
            } ?: defaultSemester.startDate
            val endDate = endDateString?.let {
                runCatching {
                    LocalDate.parse(
                        endDateString,
                        LocalDate.Formats.ISO,
                    )
                }.getOrElse {
                    errors.add("End date of semester $id is invalid: $endDateString")
                    return empty(errors)
                }
            } ?: defaultSemester.endDate
            val timeZone = timeZoneId?.let {
                runCatching {
                    TimeZone.of(timeZoneId)
                }.getOrElse {
                    errors.add("Time zone of semester $id is invalid: $timeZoneId")
                    return empty(errors)
                }
            } ?: defaultSemester.timeZone

            return SemesterProperty(
                SemesterEntity(
                    id = id,
                    name = name,
                    startDate = startDate,
                    endDate = endDate,
                    timeZone = timeZone,
                ),
                errors = errors.takeIf { it.isNotEmpty() },
            )
        }
    }

    init {
        parameters.apply {
            label = semester?.name
            put(PARAM_START_DATE, semester?.startDate?.format(LocalDate.Formats.ISO))
            put(PARAM_END_DATE, semester?.endDate?.format(LocalDate.Formats.ISO))
            put(PARAM_TIME_ZONE, semester?.timeZone?.id)
        }
    }
}

data class CourseProperty(
    val course: CourseEntity?,
    override val errors: List<String>? = null,
) : UuidProperty(), ParsingStatusProperty {
    override val uuid = course?.id

    companion object : UuidPropertyScribe<CourseProperty>(
        CourseProperty::class, CourseProperty.PROPERTY_NAME,
    ) {
        const val PROPERTY_NAME = "X-KNOWMAD-COURSE"

        const val PARAM_SEMESTER_ID = "X-SEMESTER-ID"
        const val PARAM_INSTRUCTOR = "X-INSTRUCTOR"
        const val PARAM_LOCATION = "X-LOCATION"

        fun empty(errors: List<String>? = null) = CourseProperty(null, errors)

        override fun newInstance(
            value: Uuid?,
            dataType: ICalDataType?,
            parameters: ICalParameters?,
            context: ParseContext?,
        ): CourseProperty {
            val errors = mutableListOf<String>()
            if (parameters == null) {
                errors.add("Course parameters are missing")
                return empty(errors)
            }

            val identifier = value?.toString() ?: "<no-id>"
            val name = parameters.label ?: run {
                errors.add("Name of course $identifier is missing")
                ""
            }

            val semesterIdString = parameters.get(PARAM_SEMESTER_ID)?.singleOrNull()
            val semesterId = semesterIdString?.let {
                Uuid.parseOrNull(semesterIdString) ?: run {
                    errors.add("Semester ID of course $identifier is invalid")
                    return empty(errors)
                }
            } ?: SemesterEntity.DefaultSemesterId
            val instructor = parameters.get(PARAM_INSTRUCTOR)?.singleOrNull() ?: run {
                errors.add("Instructor of course $identifier is missing")
                ""
            }
            val location = parameters.get(PARAM_LOCATION)?.singleOrNull() ?: run {
                errors.add("Location of course $identifier is missing")
                ""
            }

            return CourseProperty(
                CourseEntity(
                    id = value ?: Uuid.generateV7(),
                    semesterId = semesterId,
                    name = name,
                    instructor = instructor,
                    location = location,
                ),
                errors = errors.takeIf { it.isNotEmpty() },
            )
        }
    }

    init {
        parameters.apply {
            put(PARAM_SEMESTER_ID, course?.semesterId?.toString())
            label = course?.name
            put(PARAM_INSTRUCTOR, course?.instructor)
            put(PARAM_LOCATION, course?.location)
        }
    }
}

data class KnowmadRecurrenceProperty(
    val recurrenceRule: ICalendarRecurrenceRule?,
) : RecurrenceProperty(recurrenceRule?.toICalValue()) {
    companion object : RecurrencePropertyScribe<KnowmadRecurrenceProperty>(
        KnowmadRecurrenceProperty::class.java,
        KnowmadRecurrenceProperty.PROPERTY_NAME,
    ) {
        const val PROPERTY_NAME = "X-KNOWMAD-RECURRENCE"

        override fun newInstance(value: Recurrence?): KnowmadRecurrenceProperty {
            val rule = value?.toICalendarRecurrenceRule()
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

interface ParsingStatusProperty {
    val errors: List<String>?
}
