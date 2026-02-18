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

package top.ltfan.knowmad.agent.tool

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import android.content.res.Resources
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.encodeToJsonElement
import top.ltfan.knowmad.R
import top.ltfan.knowmad.agent.CodeRunnerTool
import top.ltfan.knowmad.data.schedule.CombinedCourse
import top.ltfan.knowmad.data.schedule.CourseEntity
import top.ltfan.knowmad.data.schedule.Event
import top.ltfan.knowmad.data.schedule.EventEntity
import top.ltfan.knowmad.data.schedule.ICalendarRuleArguments
import top.ltfan.knowmad.data.schedule.RecurrenceRuleEntity
import top.ltfan.knowmad.data.schedule.Reminder
import top.ltfan.knowmad.data.schedule.Reminders.Companion.Empty
import top.ltfan.knowmad.data.schedule.ScheduleDao
import top.ltfan.knowmad.data.schedule.SemesterEntity
import top.ltfan.knowmad.data.schedule.iCalendarImportResultMessage
import top.ltfan.knowmad.data.schedule.pickFromPalette
import top.ltfan.knowmad.data.schedule.readCustomizedICalendar
import top.ltfan.knowmad.data.schedule.toReminders
import top.ltfan.knowmad.util.Json
import top.ltfan.knowmad.util.Logger
import top.ltfan.omnical.icalendar.ICalendarColor
import top.ltfan.omnical.icalendar.ICalendarPriority
import top.ltfan.omnical.icalendar.ICalendarTrigger
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

fun ToolRegistry.Builder.scheduleTools(
    resources: Resources, dao: ScheduleDao,
) {
    tool(ScheduleTools.QuerySemestersTool(resources, dao))
    tool(ScheduleTools.QueryEventsTool(resources, dao))
}

fun ToolRegistry.Builder.scheduleToolsExtended(
    resources: Resources, dao: ScheduleDao, registerCodeRunner: (CodeRunnerTool) -> Unit,
) {
    tool(ScheduleTools.ImportFromICalendarTool(resources, dao).also(registerCodeRunner))
    tool(ScheduleTools.SearchSemestersTool(resources, dao))
    tool(ScheduleTools.CreateSemesterTool(resources, dao))
    tool(ScheduleTools.UpdateSemesterTool(resources, dao))
    tool(ScheduleTools.SearchCoursesTool(resources, dao))
    tool(ScheduleTools.CreateCoursesTool(resources, dao))
    tool(ScheduleTools.UpdateCourseTool(resources, dao))
    tool(ScheduleTools.SearchEventsTool(resources, dao))
    tool(ScheduleTools.CreateEventsTool(resources, dao))
    tool(ScheduleTools.UpdateEventTool(resources, dao))
}

object ScheduleTools {
    private val logger = Logger("ScheduleTools")

    class ImportFromICalendarTool(
        private val resources: Resources,
        private val dao: ScheduleDao,
    ) : Tool<ImportFromICalendarTool.Args, ImportFromICalendarTool.Result>(
        argsSerializer = Args.serializer(),
        resultSerializer = Result.serializer(),
        descriptor = ToolDescriptor(
            name = "import_from_icalendar",
            description = resources.getString(R.string.llm_tool_schedule_import_from_icalendar_description),
        ),
    ), CodeRunnerTool, MessageWhenGatheringTool {
        override suspend fun execute(args: Args) = Result(resources)

        @Serializable
        @SerialName("Args")
        data object Args

        @Serializable
        @SerialName("Result")
        data class Result(
            val iCalendarRule: String,
            val contentRule: String,
        ) {
            constructor(resources: Resources) : this(
                iCalendarRule = resources.getString(R.string.icalendar_rule)
                    .trimIndent().format(*ICalendarRuleArguments),
                contentRule = resources.getString(R.string.llm_tool_schedule_import_from_icalendar_creation_rule)
                    .trimIndent().format(LANGUAGE_TAG),
            )
        }

        override suspend fun runCode(components: List<String>, code: String): String {
            val errors = mutableListOf<String>()

            val result = runCatching {
                val iCal = readCustomizedICalendar(code)
                    ?: error("There wasn't any iCalendar data in the content")

                val events = dao.importFromICalendar(
                    iCalendar = iCal,
                    resources = resources,
                    errors = errors,
                ).getOrThrow()

                if (events.isEmpty()) {
                    error("No valid events found in the iCalendar data")
                }

                events.size
            }

            return iCalendarImportResultMessage(
                successCount = result.getOrNull(),
                throwable = result.exceptionOrNull(),
                errors = errors.ifEmpty { null },
            )
        }

        override val components by lazy { LanguageComponents }

        override val messageWhenGathering = Json.encodeToJsonElement(Result(resources))

        companion object {
            const val LANGUAGE_TAG = "ics icalendar-import"
            val LanguageComponents by lazy { LANGUAGE_TAG.split(" ") }
        }
    }

    class QuerySemestersTool(
        private val resources: Resources,
        private val dao: ScheduleDao,
    ) : Tool<QuerySemestersTool.Args, QuerySemestersTool.Result>(
        argsSerializer = Args.serializer(),
        resultSerializer = Result.serializer(),
        descriptor = ToolDescriptor(
            name = "query_semesters",
            description = resources.getString(R.string.llm_tool_schedule_query_semesters_description),
            optionalParameters = listOf(
                ToolParameterDescriptor(
                    name = "startDate",
                    description = resources.getString(R.string.llm_tool_schedule_query_semesters_arg_start_date_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "endDate",
                    description = resources.getString(R.string.llm_tool_schedule_query_semesters_arg_end_date_description),
                    type = ToolParameterType.String,
                ),
            ),
        ),
    ) {
        override suspend fun execute(args: Args): Result {
            if (args.startDate == null && args.endDate == null) {
                val semesters = runCatching { dao.getAllSemesters() }
                    .onFailure { logger.error(it) { "Failed to query all semesters from database" } }
                    .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_query_semesters_result_failure_reason_internal_error)) }
                return Result.Success(semesters)
            }

            val inputDate1 = runCatching { args.startDate?.let { LocalDate.parse(it) } }
                .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_query_semesters_result_failure_reason_invalid_start_date)) }
            val inputDate2 = runCatching { args.endDate?.let { LocalDate.parse(it) } }
                .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_query_semesters_result_failure_reason_invalid_end_date)) }

            val (date1, date2) = when {
                inputDate1 != null && inputDate2 == null -> inputDate1 to inputDate1
                inputDate1 == null && inputDate2 != null -> inputDate2 to inputDate2
                inputDate1 != null && inputDate2 != null -> inputDate1 to inputDate2
                else -> return Result.Failure(resources.getString(R.string.llm_tool_schedule_query_semesters_result_failure_reason_internal_error))
            }

            val (startDate, endDate) = if (date1 <= date2) date1 to date2 else date2 to date1
            val semesters = runCatching { dao.getSemestersInRange(startDate, endDate) }
                .onFailure { logger.error(it) { "Failed to query semesters from database" } }
                .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_query_semesters_result_failure_reason_internal_error)) }
            return Result.Success(semesters)
        }

        @Serializable
        @SerialName("Args")
        data class Args(
            val startDate: String? = null,
            val endDate: String? = null,
        )

        @Serializable
        sealed interface Result {
            @Serializable
            @SerialName("Success")
            data class Success(val semesters: List<SemesterEntity>) : Result

            @Serializable
            @SerialName("Failure")
            data class Failure(val reason: String) : Result
        }
    }

    class SearchSemestersTool(
        private val resources: Resources,
        private val dao: ScheduleDao,
    ) : Tool<SearchSemestersTool.Args, SearchSemestersTool.Result>(
        argsSerializer = Args.serializer(),
        resultSerializer = Result.serializer(),
        descriptor = ToolDescriptor(
            name = "search_semesters",
            description = resources.getString(R.string.llm_tool_schedule_search_semesters_description),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "query",
                    description = resources.getString(R.string.llm_tool_schedule_search_semesters_arg_query_description),
                    type = ToolParameterType.String,
                ),
            ),
        ),
    ) {
        override suspend fun execute(args: Args): Result {
            args.query.ifBlank { return Result.Failure(resources.getString(R.string.llm_tool_schedule_search_semesters_result_failure_reason_empty_query)) }
            val semesters = runCatching { dao.searchSemesters(args.query) }
                .onFailure { logger.error(it) { "Failed to search semesters" } }
                .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_search_semesters_result_failure_reason_internal_error)) }
            return Result.Success(semesters)
        }

        @Serializable
        @SerialName("Args")
        data class Args(val query: String)

        @Serializable
        sealed interface Result {
            @Serializable
            @SerialName("Success")
            data class Success(val semesters: List<SemesterEntity>) : Result

            @Serializable
            @SerialName("Failure")
            data class Failure(val reason: String) : Result
        }
    }

    class CreateSemesterTool(
        private val resources: Resources,
        private val dao: ScheduleDao,
    ) : Tool<CreateSemesterTool.Args, CreateSemesterTool.Result>(
        argsSerializer = Args.serializer(),
        resultSerializer = Result.serializer(),
        descriptor = ToolDescriptor(
            name = "create_semester",
            description = resources.getString(R.string.llm_tool_schedule_create_semester_description),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "name",
                    description = resources.getString(R.string.llm_tool_schedule_create_semester_arg_name_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "startDate",
                    description = resources.getString(R.string.llm_tool_schedule_create_semester_arg_start_date_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "endDate",
                    description = resources.getString(R.string.llm_tool_schedule_create_semester_arg_end_date_description),
                    type = ToolParameterType.String,
                ),
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor(
                    name = "timeZone",
                    description = resources.getString(R.string.llm_tool_schedule_create_semester_arg_time_zone_description),
                    type = ToolParameterType.String,
                ),
            ),
        ),
    ) {
        override suspend fun execute(args: Args): Result {
            val date1 = runCatching { LocalDate.parse(args.startDate) }
                .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_create_semester_result_failure_reason_invalid_start_date)) }
            val date2 = runCatching { LocalDate.parse(args.endDate) }
                .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_create_semester_result_failure_reason_invalid_end_date)) }
            val (startDate, endDate) = if (date1 <= date2) date1 to date2 else date2 to date1
            val timeZone = runCatching {
                if (args.timeZone.isNullOrBlank()) TimeZone.currentSystemDefault()
                else TimeZone.of(args.timeZone)
            }.getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_create_semester_result_failure_reason_invalid_time_zone)) }
            val semester = SemesterEntity(
                name = args.name,
                startDate = startDate,
                endDate = endDate,
                timeZone = timeZone,
            )
            val inserted = runCatching { dao.insertSemester(semester) }
                .onFailure { logger.error(it) { "Failed to insert semester" } }
                .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_create_semester_result_failure_reason_internal_error)) }
            return if (inserted >= 0L) Result.Success(semester)
            else Result.Failure(resources.getString(R.string.llm_tool_schedule_create_semester_result_failure_reason_insert_failed))
        }

        @Serializable
        @SerialName("Args")
        data class Args(
            val name: String,
            val startDate: String,
            val endDate: String,
            val timeZone: String? = null,
        )

        @Serializable
        sealed interface Result {
            @Serializable
            @SerialName("Success")
            data class Success(val semester: SemesterEntity) : Result

            @Serializable
            @SerialName("Failure")
            data class Failure(val reason: String) : Result
        }
    }

    class UpdateSemesterTool(
        private val resources: Resources,
        private val dao: ScheduleDao,
    ) : Tool<UpdateSemesterTool.Args, UpdateSemesterTool.Result>(
        argsSerializer = Args.serializer(),
        resultSerializer = Result.serializer(),
        descriptor = ToolDescriptor(
            name = "update_semester",
            description = resources.getString(R.string.llm_tool_schedule_update_semester_description),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "semesterId",
                    description = resources.getString(R.string.llm_tool_schedule_update_semester_arg_semester_id_description),
                    type = ToolParameterType.String,
                ),
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor(
                    name = "name",
                    description = resources.getString(R.string.llm_tool_schedule_update_semester_arg_name_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "startDate",
                    description = resources.getString(R.string.llm_tool_schedule_update_semester_arg_start_date_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "endDate",
                    description = resources.getString(R.string.llm_tool_schedule_update_semester_arg_end_date_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "timeZone",
                    description = resources.getString(R.string.llm_tool_schedule_update_semester_arg_time_zone_description),
                    type = ToolParameterType.String,
                ),
            ),
        ),
    ) {
        override suspend fun execute(args: Args): Result {
            val semesterId = Uuid.parseOrNull(args.semesterId)
                ?: return Result.Failure(resources.getString(R.string.llm_tool_schedule_update_semester_result_failure_reason_invalid_semester_id))
            val existingSemester = runCatching { dao.getSemesterById(semesterId) }
                .onFailure { logger.error(it) { "Failed to fetch existing semester" } }
                .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_update_semester_result_failure_reason_internal_error)) }
                ?: return Result.Failure(resources.getString(R.string.llm_tool_schedule_update_semester_result_failure_reason_not_found))
            val name = args.name ?: existingSemester.name
            val date1 = args.startDate?.let {
                runCatching { LocalDate.parse(it) }
                    .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_update_semester_result_failure_reason_invalid_start_date)) }
            } ?: existingSemester.startDate
            val date2 = args.endDate?.let {
                runCatching { LocalDate.parse(it) }
                    .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_update_semester_result_failure_reason_invalid_end_date)) }
            } ?: existingSemester.endDate
            val (startDate, endDate) = if (date1 <= date2) date1 to date2 else date2 to date1
            val timeZone = args.timeZone?.let {
                runCatching { TimeZone.of(it) }
                    .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_update_semester_result_failure_reason_invalid_time_zone)) }
            } ?: existingSemester.timeZone
            val newSemester = existingSemester.copy(
                name = name,
                startDate = startDate,
                endDate = endDate,
                timeZone = timeZone,
            )
            if (newSemester == existingSemester) {
                return Result.Success(existingSemester)
            }
            val updated = runCatching { dao.updateSemester(newSemester) }
                .onFailure { logger.error(it) { "Failed to update semester" } }
                .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_update_semester_result_failure_reason_internal_error)) }
            return if (updated > 0) Result.Success(newSemester)
            else Result.Failure(resources.getString(R.string.llm_tool_schedule_update_semester_result_failure_reason_update_failed))
        }

        @Serializable
        @SerialName("Args")
        data class Args(
            val semesterId: String,
            val name: String? = null,
            val startDate: String? = null,
            val endDate: String? = null,
            val timeZone: String? = null,
        )

        @Serializable
        sealed interface Result {
            @Serializable
            @SerialName("Success")
            data class Success(val semester: SemesterEntity) : Result

            @Serializable
            @SerialName("Failure")
            data class Failure(val reason: String) : Result
        }
    }

    class SearchCoursesTool(
        private val resources: Resources,
        private val dao: ScheduleDao,
    ) : Tool<SearchCoursesTool.Args, SearchCoursesTool.Result>(
        argsSerializer = Args.serializer(),
        resultSerializer = Result.serializer(),
        descriptor = ToolDescriptor(
            name = "search_courses",
            description = resources.getString(R.string.llm_tool_schedule_search_courses_description),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "query",
                    description = resources.getString(R.string.llm_tool_schedule_search_courses_arg_query_description),
                    type = ToolParameterType.String,
                ),
            ),
        ),
    ) {
        override suspend fun execute(args: Args): Result {
            args.query.ifBlank { Result.Failure(resources.getString(R.string.llm_tool_schedule_search_courses_result_failure_reason_empty_query)) }
            val courses = runCatching { dao.searchCourses(args.query) }
                .onFailure { logger.error(it) { "Failed to search courses" } }
                .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_search_courses_result_failure_reason_internal_error)) }
            return Result.Success(courses)
        }

        @Serializable
        @SerialName("Args")
        data class Args(val query: String)

        @Serializable
        sealed interface Result {
            @Serializable
            @SerialName("Success")
            data class Success(
                val semesters: List<SemesterEntity>,
                val courses: List<CourseEntity>,
            ) : Result {
                constructor(courses: List<CombinedCourse>) : this(
                    semesters = courses.asSequence()
                        .map { it.semester }
                        .distinctBy { it.id }
                        .toList(),
                    courses = courses.map { it.course },
                )
            }

            @Serializable
            @SerialName("Failure")
            data class Failure(val reason: String) : Result
        }
    }

    class CreateCoursesTool(
        private val resources: Resources,
        private val dao: ScheduleDao,
    ) : Tool<CreateCoursesTool.Args, CreateCoursesTool.Result>(
        argsSerializer = Args.serializer(),
        resultSerializer = Result.serializer(),
        descriptor = ToolDescriptor(
            name = "create_courses",
            description = resources.getString(R.string.llm_tool_schedule_create_course_description),
            optionalParameters = parameters(resources) + listOf(
                ToolParameterDescriptor(
                    name = "iCalendarAcknowledgement",
                    description = resources.getString(R.string.llm_tool_schedule_create_course_arg_icalendar_acknowledgement_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "list",
                    description = resources.getString(R.string.llm_tool_schedule_create_course_arg_list_description),
                    type = ToolParameterType.List(
                        itemsType = ToolParameterType.Object(
                            properties = parameters(resources),
                            requiredProperties = listOf(
                                "semesterId", "name", "instructor", "location",
                            ),
                        ),
                    ),
                ),
            ),
        ),
    ) {
        override suspend fun execute(args: Args): Result {
            val errors = mutableListOf<String>()
            args.list?.let { coursesData ->
                val list = coursesData.mapNotNull { data ->
                    val semesterId = parseSemesterId(data.semesterId, errors) {
                        return@mapNotNull null
                    }
                    CourseEntity(
                        semesterId = semesterId,
                        name = data.name,
                        instructor = data.instructor,
                        location = data.location,
                    )
                }
                val insertResults = runCatching { dao.insertAllCourses(list) }
                    .onFailure { logger.error(it) { "Failed to insert courses" } }
                    .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_create_course_result_failure_reason_internal_error)) }
                val insertedList = (list.asSequence() zip insertResults.asSequence())
                    .filter {
                        (it.second >= 0L).also { success ->
                            if (!success) {
                                errors.add(
                                    resources.getString(
                                        R.string.llm_tool_schedule_create_course_result_error_insertion_failed,
                                        it.first.name,
                                    ),
                                )
                            }
                        }
                    }
                    .map { it.first }
                    .toList()
                return Result.Success(
                    courses = insertedList,
                    errors = errors.takeIf { it.isNotEmpty() },
                )
            }
            val semesterId = parseSemesterId(args.semesterId, errors) {
                return Result.Failure(it)
            }
            val name = args.name
                ?: return Result.Failure(resources.getString(R.string.llm_tool_schedule_create_course_result_failure_reason_fields_required))
            val instructor = args.instructor
                ?: return Result.Failure(resources.getString(R.string.llm_tool_schedule_create_course_result_failure_reason_fields_required))
            val location = args.location
                ?: return Result.Failure(resources.getString(R.string.llm_tool_schedule_create_course_result_failure_reason_fields_required))
            val course = CourseEntity(
                semesterId = semesterId,
                name = name,
                instructor = instructor,
                location = location,
            )
            val inserted = runCatching { dao.insertCourse(course) }
                .onFailure { logger.error(it) { "Failed to insert course" } }
                .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_create_course_result_failure_reason_internal_error)) }
            return if (inserted >= 0L) Result.Success(
                course = course,
                errors = errors.takeIf { it.isNotEmpty() },
            ) else Result.Failure(
                resources.getString(
                    R.string.llm_tool_schedule_create_course_result_failure_reason_insertion_failed,
                ),
            )
        }

        private inline fun parseSemesterId(
            semesterIdStr: String?,
            errors: MutableList<String>,
            onError: (String) -> Nothing,
        ): Uuid {
            if (semesterIdStr == null) {
                val error =
                    resources.getString(R.string.llm_tool_schedule_create_course_result_failure_reason_fields_required)
                errors.add(error)
                onError(error)
            }
            return Uuid.parseOrNull(semesterIdStr) ?: run {
                val error =
                    resources.getString(R.string.llm_tool_schedule_create_course_result_failure_reason_invalid_semester_id)
                errors.add(error)
                onError(error)
            }
        }

        @Serializable
        @SerialName("Args")
        data class Args(
            val semesterId: String?,
            val name: String?,
            val instructor: String?,
            val location: String?,
            val iCalendarAcknowledgement: String?,
            val list: List<Data>?,
        ) {
            @Serializable
            @SerialName("Data")
            data class Data(
                val semesterId: String,
                val name: String,
                val instructor: String,
                val location: String,
            )
        }

        @Serializable
        sealed interface Result {
            @Serializable
            @SerialName("Success")
            data class Success(
                val course: CourseEntity? = null,
                val courses: List<CourseEntity>? = null,
                val errors: List<String>? = null,
            ) : Result

            @Serializable
            @SerialName("Failure")
            data class Failure(val reason: String) : Result
        }

        companion object {
            private fun parameters(resources: Resources) = listOf(
                ToolParameterDescriptor(
                    name = "semesterId",
                    description = resources.getString(R.string.llm_tool_schedule_create_course_arg_semester_id_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "name",
                    description = resources.getString(R.string.llm_tool_schedule_create_course_arg_name_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "instructor",
                    description = resources.getString(R.string.llm_tool_schedule_create_course_arg_instructor_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "location",
                    description = resources.getString(R.string.llm_tool_schedule_create_course_arg_location_description),
                    type = ToolParameterType.String,
                ),
            )
        }
    }

    class UpdateCourseTool(
        private val resources: Resources,
        private val dao: ScheduleDao,
    ) : Tool<UpdateCourseTool.Args, UpdateCourseTool.Result>(
        argsSerializer = Args.serializer(),
        resultSerializer = Result.serializer(),
        descriptor = ToolDescriptor(
            name = "update_course",
            description = resources.getString(R.string.llm_tool_schedule_update_course_description),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "courseId",
                    description = resources.getString(R.string.llm_tool_schedule_update_course_arg_course_id_description),
                    type = ToolParameterType.String,
                ),
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor(
                    name = "name",
                    description = resources.getString(R.string.llm_tool_schedule_update_course_arg_name_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "instructor",
                    description = resources.getString(R.string.llm_tool_schedule_update_course_arg_instructor_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "location",
                    description = resources.getString(R.string.llm_tool_schedule_update_course_arg_location_description),
                    type = ToolParameterType.String,
                ),
            ),
        ),
    ) {
        override suspend fun execute(args: Args): Result {
            val courseId = Uuid.parseOrNull(args.courseId)
                ?: return Result.Failure(resources.getString(R.string.llm_tool_schedule_update_course_result_failure_reason_invalid_course_id))
            val existingCourse = runCatching { dao.getCourseEntityById(courseId) }
                .onFailure { logger.error(it) { "Failed to fetch existing course" } }
                .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_update_course_result_failure_reason_internal_error)) }
                ?: return Result.Failure(resources.getString(R.string.llm_tool_schedule_update_course_result_failure_reason_not_found))
            val name = args.name ?: existingCourse.name
            val instructor = args.instructor ?: existingCourse.instructor
            val location = args.location ?: existingCourse.location
            val newCourse = existingCourse.copy(
                name = name,
                instructor = instructor,
                location = location,
            )
            if (newCourse == existingCourse) {
                return Result.Success(existingCourse)
            }
            val updated = runCatching { dao.updateCourse(newCourse) }
                .onFailure { logger.error(it) { "Failed to update course" } }
                .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_update_course_result_failure_reason_internal_error)) }
            return if (updated > 0) Result.Success(newCourse)
            else Result.Failure(resources.getString(R.string.llm_tool_schedule_update_course_result_failure_reason_update_failed))
        }

        @Serializable
        @SerialName("Args")
        data class Args(
            val courseId: String,
            val name: String? = null,
            val instructor: String? = null,
            val location: String? = null,
        )

        @Serializable
        sealed interface Result {
            @Serializable
            @SerialName("Success")
            data class Success(val course: CourseEntity) : Result

            @Serializable
            @SerialName("Failure")
            data class Failure(val reason: String) : Result
        }
    }

    class QueryEventsTool(
        private val resources: Resources,
        private val dao: ScheduleDao,
    ) : Tool<QueryEventsTool.Args, QueryEventsTool.Result>(
        argsSerializer = Args.serializer(),
        resultSerializer = Result.serializer(),
        descriptor = ToolDescriptor(
            name = "query_events",
            description = resources.getString(R.string.llm_tool_schedule_query_events_description),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "startTime",
                    description = resources.getString(R.string.llm_tool_schedule_query_events_arg_start_time_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "endTime",
                    description = resources.getString(R.string.llm_tool_schedule_query_events_arg_end_time_description),
                    type = ToolParameterType.String,
                ),
            ),
        ),
    ) {
        override suspend fun execute(args: Args): Result {
            val instant1 = runCatching { Instant.parse(args.startTime) }
                .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_query_events_result_failure_reason_invalid_start_time)) }
            val instant2 = runCatching { Instant.parse(args.endTime) }
                .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_query_events_result_failure_reason_invalid_end_time)) }
            val (start, end) = if (instant1 <= instant2) instant1 to instant2 else instant2 to instant1
            val events = runCatching { dao.getEventsInRange(start, end) }
                .onFailure { logger.error(it) { "Failed to query events in range" } }
                .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_query_events_result_failure_reason_internal_error)) }
            return Result.Success(events)
        }

        @Serializable
        @SerialName("Args")
        data class Args(val startTime: String, val endTime: String)

        @Serializable
        sealed interface Result {
            @Serializable
            @SerialName("Success")
            data class Success(
                val semesters: List<SemesterEntity>,
                val courses: List<CourseEntity>?,
                val recurrenceRules: List<RecurrenceRuleEntity>?,
                val events: List<EventEntity>,
            ) : Result {
                constructor(events: List<Event>) : this(
                    semesters = events.asSequence()
                        .map { it.semester }
                        .distinctBy { it.id }
                        .toList(),
                    courses = events.asSequence()
                        .filterIsInstance<Event.Course>()
                        .map { it.course }
                        .distinctBy { it.id }
                        .toList()
                        .takeIf { it.isNotEmpty() },
                    recurrenceRules = events.asSequence()
                        .mapNotNull { it.recurrenceRule }
                        .distinctBy { it.id }
                        .toList()
                        .takeIf { it.isNotEmpty() },
                    events = events.map { it.toEntity() },
                )
            }

            @Serializable
            @SerialName("Failure")
            data class Failure(val reason: String) : Result
        }
    }

    class SearchEventsTool(
        private val resources: Resources,
        private val dao: ScheduleDao,
    ) : Tool<SearchEventsTool.Args, SearchEventsTool.Result>(
        argsSerializer = Args.serializer(),
        resultSerializer = Result.serializer(),
        descriptor = ToolDescriptor(
            name = "search_events",
            description = resources.getString(R.string.llm_tool_schedule_search_events_description),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "query",
                    description = resources.getString(R.string.llm_tool_schedule_search_events_arg_query_description),
                    type = ToolParameterType.String,
                ),
            ),
        ),
    ) {
        override suspend fun execute(args: Args): Result {
            args.query.ifBlank { return Result.Failure(resources.getString(R.string.llm_tool_schedule_search_events_result_failure_reason_empty_query)) }
            val events = runCatching { dao.searchEventsJoinedCourses(args.query) }
                .onFailure { logger.error(it) { "Failed to search events" } }
                .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_search_events_result_failure_reason_internal_error)) }
            return Result.Success(events)
        }

        @Serializable
        @SerialName("Args")
        data class Args(val query: String)

        @Serializable
        sealed interface Result {
            @Serializable
            @SerialName("Success")
            data class Success(
                val semesters: List<SemesterEntity>,
                val courses: List<CourseEntity>?,
                val recurrenceRules: List<RecurrenceRuleEntity>?,
                val events: List<EventEntity>,
            ) : Result {
                constructor(events: List<Event>) : this(
                    semesters = events.asSequence()
                        .map { it.semester }
                        .distinctBy { it.id }
                        .toList(),
                    courses = events.asSequence()
                        .filterIsInstance<Event.Course>()
                        .map { it.course }
                        .distinctBy { it.id }
                        .toList()
                        .takeIf { it.isNotEmpty() },
                    recurrenceRules = events.asSequence()
                        .mapNotNull { it.recurrenceRule }
                        .distinctBy { it.id }
                        .toList()
                        .takeIf { it.isNotEmpty() },
                    events = events.map { it.toEntity() },
                )
            }

            @Serializable
            @SerialName("Failure")
            data class Failure(val reason: String) : Result
        }
    }

    class CreateEventsTool(
        private val resources: Resources,
        private val dao: ScheduleDao,
    ) : Tool<CreateEventsTool.Args, CreateEventsTool.Result>(
        argsSerializer = Args.serializer(),
        resultSerializer = Result.serializer(),
        descriptor = ToolDescriptor(
            name = "create_events",
            description = resources.getString(R.string.llm_tool_schedule_create_event_description),
            optionalParameters = parameters(resources) + listOf(
                ToolParameterDescriptor(
                    name = "iCalendarAcknowledgement",
                    description = resources.getString(R.string.llm_tool_schedule_create_event_arg_icalendar_acknowledgement_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "list",
                    description = resources.getString(R.string.llm_tool_schedule_create_event_arg_list_description),
                    type = ToolParameterType.List(
                        itemsType = ToolParameterType.Object(
                            properties = parameters(resources),
                            requiredProperties = listOf(
                                "semesterId",
                                "startTime",
                                "endTime",
                            ),
                        ),
                    ),
                ),
            ),
        ),
    ) {
        override suspend fun execute(args: Args): Result {
            val errors = mutableListOf<String>()
            args.list?.let { eventsData ->
                val list = eventsData.mapNotNull { data ->
                    val semesterId = parseSemesterId(data.semesterId, errors) {
                        return@mapNotNull null
                    }
                    val instant1 = parseInstant1(data.startTime, errors) {
                        return@mapNotNull null
                    }
                    val instant2 = parseInstant2(data.endTime, errors) {
                        return@mapNotNull null
                    }
                    val (start, end) = if (instant1 <= instant2) instant1 to instant2 else instant2 to instant1
                    val courseId = parseCourseId(
                        data.courseId,
                        data.name,
                        data.location,
                        errors,
                    ) {
                        return@mapNotNull null
                    }
                    val color = parseColor(data.color, courseId, semesterId)
                    val reminders = parseReminders(data.reminders, errors)
                    EventEntity(
                        semesterId = semesterId,
                        courseId = courseId,
                        name = data.name,
                        instructor = data.instructor,
                        location = data.location,
                        color = color,
                        startTime = start,
                        endTime = end,
                        reminders = reminders,
                        notes = data.notes,
                        priority = data.priority,
                    )
                }
                val insertResults = runCatching { dao.insertAllEvents(list) }
                    .onFailure { logger.error(it) { "Failed to insert events" } }
                    .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_create_event_result_failure_reason_internal_error)) }
                val insertedList = (list.asSequence() zip insertResults.asSequence())
                    .filter {
                        (it.second >= 0L).also { success ->
                            if (!success) {
                                errors.add(
                                    resources.getString(
                                        R.string.llm_tool_schedule_create_event_result_error_insertion_failed,
                                        it.first.name,
                                    ),
                                )
                            }
                        }
                    }
                    .map { it.first }
                    .toList()
                return Result.Success(
                    events = insertedList,
                    errors = errors.takeIf { it.isNotEmpty() },
                )
            }
            val semesterId = parseSemesterId(args.semesterId, errors) {
                return Result.Failure(it)
            }
            val instant1 = parseInstant1(args.startTime, errors) {
                return Result.Failure(it)
            }
            val instant2 = parseInstant2(args.endTime, errors) {
                return Result.Failure(it)
            }
            val (start, end) = if (instant1 <= instant2) instant1 to instant2 else instant2 to instant1
            val courseId = parseCourseId(
                args.courseId,
                args.name,
                args.location,
                errors,
            ) {
                return Result.Failure(it)
            }
            val color = parseColor(args.color, courseId, semesterId)
            val reminders = parseReminders(args.reminders, errors)
            val event = EventEntity(
                semesterId = semesterId,
                courseId = courseId,
                name = args.name,
                instructor = args.instructor,
                location = args.location,
                color = color,
                startTime = start,
                endTime = end,
                reminders = reminders,
                notes = args.notes,
                priority = args.priority,
            )
            val inserted = runCatching { dao.insertEvent(event) }
                .onFailure { logger.error(it) { "Failed to insert event" } }
                .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_create_event_result_failure_reason_internal_error)) }
            return if (inserted >= 0L) Result.Success(
                event = event,
                errors = errors.takeIf { it.isNotEmpty() },
            ) else Result.Failure(
                resources.getString(
                    R.string.llm_tool_schedule_create_event_result_failure_reason_insertion_failed,
                ),
            )
        }

        private inline fun parseSemesterId(
            semesterIdStr: String?,
            errors: MutableList<String>,
            onError: (String) -> Nothing,
        ): Uuid {
            if (semesterIdStr == null) {
                val error =
                    resources.getString(R.string.llm_tool_schedule_create_event_result_failure_reason_fields_required)
                errors.add(error)
                onError(error)
            }
            return Uuid.parseOrNull(semesterIdStr) ?: run {
                val error =
                    resources.getString(R.string.llm_tool_schedule_create_event_result_failure_reason_invalid_semester_id)
                errors.add(error)
                onError(error)
            }
        }

        private inline fun parseInstant1(
            timeStr: String?,
            errors: MutableList<String>,
            onError: (String) -> Nothing,
        ): Instant {
            if (timeStr == null) {
                val error =
                    resources.getString(R.string.llm_tool_schedule_create_event_result_failure_reason_fields_required)
                errors.add(error)
                onError(error)
            }
            val time = Instant.parseOrNull(timeStr) ?: run {
                val error =
                    resources.getString(R.string.llm_tool_schedule_create_event_result_failure_reason_invalid_start_time)
                errors.add(error)
                onError(error)
            }
            return time
        }

        private inline fun parseInstant2(
            timeStr: String?,
            errors: MutableList<String>,
            onError: (String) -> Nothing,
        ): Instant {
            if (timeStr == null) {
                val error =
                    resources.getString(R.string.llm_tool_schedule_create_event_result_failure_reason_fields_required)
                errors.add(error)
                onError(error)
            }
            val time = Instant.parseOrNull(timeStr) ?: run {
                val error =
                    resources.getString(R.string.llm_tool_schedule_create_event_result_failure_reason_invalid_end_time)
                errors.add(error)
                onError(error)
            }
            return time
        }

        private inline fun parseCourseId(
            courseIdStr: String?,
            name: String?,
            location: String?,
            errors: MutableList<String>,
            onError: (String) -> Nothing,
        ): Uuid? {
            if (courseIdStr == null) {
                if (name.isNullOrBlank() || location.isNullOrBlank()) {
                    val error =
                        resources.getString(R.string.llm_tool_schedule_create_event_result_failure_reason_name_location_required)
                    errors.add(error)
                    onError(error)
                }
                return null
            }
            val courseId = Uuid.parseOrNull(courseIdStr) ?: run {
                val error =
                    resources.getString(R.string.llm_tool_schedule_create_event_result_failure_reason_invalid_course_id)
                errors.add(error)
                onError(error)
            }
            return courseId
        }

        private fun parseColor(
            colorStr: String?,
            courseId: Uuid?,
            semesterId: Uuid,
        ): ICalendarColor {
            return colorStr?.let {
                ICalendarColor.fromValue(it)
            } ?: ICalendarColor.pickFromPalette(courseId ?: semesterId)
        }

        private fun parseReminders(
            reminders: List<Args.ReminderData>?,
            errors: MutableList<String>,
        ) = reminders?.mapNotNull {
            it.time?.let { timeStr ->
                val time = Instant.parseOrNull(timeStr) ?: run {
                    errors.add(resources.getString(R.string.llm_tool_schedule_create_event_result_error_invalid_reminder_time))
                    return@let
                }
                return@mapNotNull Reminder(
                    trigger = ICalendarTrigger.Absolute(time),
                    displayText = it.displayText,
                )
            }
            it.offset?.let { offsetStr ->
                val duration = Duration.parseOrNull(offsetStr) ?: run {
                    errors.add(resources.getString(R.string.llm_tool_schedule_create_event_result_error_invalid_reminder_offset))
                    return@let
                }
                val related = it.related ?: Start
                return@mapNotNull Reminder(
                    trigger = ICalendarTrigger.Relative(duration, related),
                    displayText = it.displayText,
                )
            }
            return@mapNotNull null
        }?.toReminders() ?: Empty

        @Serializable
        @SerialName("Args")
        data class Args(
            val semesterId: String?,
            val startTime: String?,
            val endTime: String?,
            val courseId: String?,
            val name: String?,
            val instructor: String?,
            val location: String?,
            val color: String?,
            val reminders: List<ReminderData>?,
            val notes: String?,
            val priority: ICalendarPriority = None,
            val iCalendarAcknowledgement: String?,
            val list: List<Data>?,
        ) {
            @Serializable
            @SerialName("Data")
            data class Data(
                val semesterId: String,
                val startTime: String,
                val endTime: String,
                val courseId: String?,
                val name: String?,
                val instructor: String?,
                val location: String?,
                val color: String?,
                val reminders: List<ReminderData>?,
                val notes: String?,
                val priority: ICalendarPriority = None,
            )

            @Serializable
            @SerialName("Reminder")
            data class ReminderData(
                val time: String?,
                val offset: String?,
                val related: ICalendarTrigger.Relative.Related?,
                val displayText: String?,
            )
        }

        @Serializable
        sealed interface Result {
            @Serializable
            @SerialName("Success")
            data class Success(
                val event: EventEntity? = null,
                val events: List<EventEntity>? = null,
                val errors: List<String>? = null,
            ) : Result

            @Serializable
            @SerialName("Failure")
            data class Failure(val reason: String) : Result
        }

        companion object {
            private fun parameters(resources: Resources) = listOf(
                ToolParameterDescriptor(
                    name = "semesterId",
                    description = resources.getString(R.string.llm_tool_schedule_create_event_arg_semester_id_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "startTime",
                    description = resources.getString(R.string.llm_tool_schedule_create_event_arg_start_time_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "endTime",
                    description = resources.getString(R.string.llm_tool_schedule_create_event_arg_end_time_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "courseId",
                    description = resources.getString(R.string.llm_tool_schedule_create_event_arg_course_id_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "name",
                    description = resources.getString(R.string.llm_tool_schedule_create_event_arg_name_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "instructor",
                    description = resources.getString(R.string.llm_tool_schedule_create_event_arg_instructor_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "location",
                    description = resources.getString(R.string.llm_tool_schedule_create_event_arg_location_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "color",
                    description = resources.getString(R.string.llm_tool_schedule_create_event_arg_color_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "reminders",
                    description = resources.getString(R.string.llm_tool_schedule_create_event_arg_reminders_description),
                    type = ToolParameterType.List(
                        itemsType = ToolParameterType.Object(
                            properties = listOf(
                                ToolParameterDescriptor(
                                    name = "time",
                                    description = resources.getString(R.string.llm_tool_schedule_create_event_arg_reminders_property_time_description),
                                    type = ToolParameterType.String,
                                ),
                                ToolParameterDescriptor(
                                    name = "offset",
                                    description = resources.getString(R.string.llm_tool_schedule_create_event_arg_reminders_property_offset_description),
                                    type = ToolParameterType.String,
                                ),
                                ToolParameterDescriptor(
                                    name = "related",
                                    description = resources.getString(R.string.llm_tool_schedule_create_event_arg_reminders_property_related_description),
                                    type = ToolParameterType.Enum(ICalendarTrigger.Relative.Related.entries),
                                ),
                                ToolParameterDescriptor(
                                    name = "displayText",
                                    description = resources.getString(R.string.llm_tool_schedule_create_event_arg_reminders_property_display_text_description),
                                    type = ToolParameterType.String,
                                ),
                            ),
                        ),
                    ),
                ),
                ToolParameterDescriptor(
                    name = "notes",
                    description = resources.getString(R.string.llm_tool_schedule_create_event_arg_notes_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "priority",
                    description = resources.getString(R.string.llm_tool_schedule_create_event_arg_priority_description),
                    type = ToolParameterType.Enum(ICalendarPriority.entries),
                ),
            )
        }
    }

    class UpdateEventTool(
        private val resources: Resources,
        private val dao: ScheduleDao,
    ) : Tool<UpdateEventTool.Args, UpdateEventTool.Result>(
        argsSerializer = Args.serializer(),
        resultSerializer = Result.serializer(),
        descriptor = ToolDescriptor(
            name = "update_event",
            description = resources.getString(R.string.llm_tool_schedule_update_event_description),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "eventId",
                    description = resources.getString(R.string.llm_tool_schedule_update_event_arg_event_id_description),
                    type = ToolParameterType.String,
                ),
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor(
                    name = "name",
                    description = resources.getString(R.string.llm_tool_schedule_update_event_arg_name_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "instructor",
                    description = resources.getString(R.string.llm_tool_schedule_update_event_arg_instructor_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "location",
                    description = resources.getString(R.string.llm_tool_schedule_update_event_arg_location_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "startTime",
                    description = resources.getString(R.string.llm_tool_schedule_update_event_arg_start_time_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "endTime",
                    description = resources.getString(R.string.llm_tool_schedule_update_event_arg_end_time_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "color",
                    description = resources.getString(R.string.llm_tool_schedule_update_event_arg_color_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "reminders",
                    description = resources.getString(R.string.llm_tool_schedule_update_event_arg_reminders_description),
                    type = ToolParameterType.List(
                        itemsType = ToolParameterType.Object(
                            properties = listOf(
                                ToolParameterDescriptor(
                                    name = "time",
                                    description = resources.getString(R.string.llm_tool_schedule_update_event_arg_reminders_property_time_description),
                                    type = ToolParameterType.String,
                                ),
                                ToolParameterDescriptor(
                                    name = "offset",
                                    description = resources.getString(R.string.llm_tool_schedule_update_event_arg_reminders_property_offset_description),
                                    type = ToolParameterType.String,
                                ),
                                ToolParameterDescriptor(
                                    name = "related",
                                    description = resources.getString(R.string.llm_tool_schedule_update_event_arg_reminders_property_related_description),
                                    type = ToolParameterType.Enum(ICalendarTrigger.Relative.Related.entries),
                                ),
                                ToolParameterDescriptor(
                                    name = "displayText",
                                    description = resources.getString(R.string.llm_tool_schedule_update_event_arg_reminders_property_display_text_description),
                                    type = ToolParameterType.String,
                                ),
                            ),
                        ),
                    ),
                ),
                ToolParameterDescriptor(
                    name = "notes",
                    description = resources.getString(R.string.llm_tool_schedule_update_event_arg_notes_description),
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "priority",
                    description = resources.getString(R.string.llm_tool_schedule_update_event_arg_priority_description),
                    type = ToolParameterType.Enum(ICalendarPriority.entries),
                ),
            ),
        ),
    ) {
        override suspend fun execute(args: Args): Result {
            val errors = mutableListOf<String>()
            val eventId = Uuid.parseOrNull(args.eventId)
                ?: return Result.Failure(resources.getString(R.string.llm_tool_schedule_update_event_result_failure_reason_invalid_event_id))
            val existingEvent = runCatching { dao.getEventEntityById(eventId) }
                .onFailure { logger.error(it) { "Failed to fetch existing event" } }
                .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_update_event_result_failure_reason_internal_error)) }
                ?: return Result.Failure(resources.getString(R.string.llm_tool_schedule_update_event_result_failure_reason_not_found))
            val name = args.name ?: existingEvent.name
            val instructor = args.instructor ?: existingEvent.instructor
            val location = args.location ?: existingEvent.location
            val instant1 = args.startTime?.let {
                Instant.parseOrNull(it)
                    ?: return Result.Failure(resources.getString(R.string.llm_tool_schedule_update_event_result_failure_reason_invalid_start_time))
            } ?: existingEvent.startTime
            val instant2 = args.endTime?.let {
                Instant.parseOrNull(it)
                    ?: return Result.Failure(resources.getString(R.string.llm_tool_schedule_update_event_result_failure_reason_invalid_end_time))
            } ?: existingEvent.endTime
            val (start, end) = if (instant1 <= instant2) instant1 to instant2 else instant2 to instant1
            val color = args.color?.let { ICalendarColor.fromValue(it) }
                ?: existingEvent.color
            val reminders = parseReminders(args.reminders, errors) ?: existingEvent.reminders
            val notes = args.notes ?: existingEvent.notes
            val priority = args.priority ?: existingEvent.priority
            val newEvent = existingEvent.copy(
                name = name,
                instructor = instructor,
                location = location,
                startTime = start,
                endTime = end,
                color = color,
                reminders = reminders,
                notes = notes,
                priority = priority,
            )
            if (newEvent == existingEvent) {
                return Result.Success(existingEvent)
            }
            val updated = runCatching { dao.updateEvent(newEvent) }
                .onFailure { logger.error(it) { "Failed to update event" } }
                .getOrElse { return Result.Failure(resources.getString(R.string.llm_tool_schedule_update_event_result_failure_reason_internal_error)) }
            return if (updated > 0) Result.Success(
                event = newEvent,
                errors = errors.takeIf { it.isNotEmpty() },
            ) else Result.Failure(resources.getString(R.string.llm_tool_schedule_update_event_result_failure_reason_update_failed))
        }

        private fun parseReminders(
            reminders: List<Args.ReminderData>?,
            errors: MutableList<String>,
        ) = reminders?.mapNotNull {
            it.time?.let { timeStr ->
                val time = Instant.parseOrNull(timeStr) ?: run {
                    errors.add(resources.getString(R.string.llm_tool_schedule_create_event_result_error_invalid_reminder_time))
                    return@let
                }
                return@mapNotNull Reminder(
                    trigger = ICalendarTrigger.Absolute(time),
                    displayText = it.displayText,
                )
            }
            it.offset?.let { offsetStr ->
                val duration = Duration.parseOrNull(offsetStr) ?: run {
                    errors.add(resources.getString(R.string.llm_tool_schedule_create_event_result_error_invalid_reminder_offset))
                    return@let
                }
                val related = it.related ?: Start
                return@mapNotNull Reminder(
                    trigger = ICalendarTrigger.Relative(duration, related),
                    displayText = it.displayText,
                )
            }
            return@mapNotNull null
        }?.toReminders()?.notEmptyOrNull()

        @Serializable
        @SerialName("Args")
        data class Args(
            val eventId: String,
            val name: String? = null,
            val instructor: String? = null,
            val location: String? = null,
            val startTime: String? = null,
            val endTime: String? = null,
            val color: String? = null,
            val reminders: List<ReminderData>? = null,
            val notes: String? = null,
            val priority: ICalendarPriority? = null,
        ) {
            @Serializable
            @SerialName("Reminder")
            data class ReminderData(
                val time: String?,
                val offset: String?,
                val related: ICalendarTrigger.Relative.Related?,
                val displayText: String?,
            )
        }

        @Serializable
        sealed interface Result {
            @Serializable
            @SerialName("Success")
            data class Success(
                val event: EventEntity,
                val errors: List<String>? = null,
            ) : Result

            @Serializable
            @SerialName("Failure")
            data class Failure(val reason: String) : Result
        }
    }
}
