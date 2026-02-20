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
import com.tyme.solar.SolarDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateRange
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.number
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.R
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant

fun ToolRegistry.Builder.timeTool(
    resources: Resources,
    chatAgentStyle: Boolean = false,
) = tool(TimeTool(resources, chatAgentStyle))

class TimeTool(
    private val resources: Resources,
    val chatAgentStyle: Boolean = false,
) : Tool<TimeTool.Args, TimeTool.Result>(
    argsSerializer = Args.serializer(),
    resultSerializer = Result.serializer(),
    descriptor = ToolDescriptor(
        name = "time",
        description = if (!chatAgentStyle) {
            resources.getString(R.string.llm_tool_time_description)
        } else {
            resources.getString(R.string.llm_tool_time_description_chat_agent)
        },
        optionalParameters = listOf(
            ToolParameterDescriptor(
                name = "day",
                description = resources.getString(R.string.llm_tool_time_arg_day_description),
                type = ToolParameterType.String,
            ),
            ToolParameterDescriptor(
                name = "days",
                description = resources.getString(R.string.llm_tool_time_arg_days_description),
                type = ToolParameterType.List(ToolParameterType.String),
            ),
        ),
    ),
) {
    override suspend fun execute(args: Args): Result = if (args.days != null) {
        args.days.map {
            runCatching { LocalDate.parse(it) }.getOrNull()?.formatAgentTime()
                ?: return Result.Failure(resources.getString(R.string.llm_tool_time_result_failure_reason_invalid_format))
        }.let { Result.Success(list = it) }
    } else if (args.day != null) {
        runCatching { LocalDate.parse(args.day) }.getOrNull()?.formatAgentTime()
            ?.let { Result.Success(it) }
            ?: Result.Failure(resources.getString(R.string.llm_tool_time_result_failure_reason_invalid_format))
    } else if (chatAgentStyle) {
        Result.Failure(resources.getString(R.string.llm_tool_time_result_failure_reason_current_prohibited))
    } else {
        Result.Success(Clock.System.now().formatAgentTime())
    }

    @Serializable
    @SerialName("Args")
    data class Args(
        val day: String? = null,
        val days: Set<String>? = null,
    )

    @Serializable
    @SerialName("Result")
    sealed interface Result {
        @Serializable
        @SerialName("Success")
        data class Success(
            val formatted: String? = null,
            val list: List<String>? = null,
        ) : Result

        @Serializable
        @SerialName("Failure")
        data class Failure(
            val message: String,
        ) : Result
    }
}

fun Instant.formatAgentTime(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val dateTime = toLocalDateTime(timeZone)
    return formatAgentTime(dateTime.date, dateTime.time, timeZone, locale)
}

fun LocalDate.formatAgentTime(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    locale: Locale = Locale.getDefault(),
) = formatAgentTime(this, null, timeZone, locale)

fun formatAgentTime(
    date: LocalDate,
    time: LocalTime? = null,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    locale: Locale = Locale.getDefault(),
) = buildString {
    val tymeSolarDay = SolarDay.fromYmd(date.year, date.month.number, date.day)
    val tymeTermDay = tymeSolarDay.getTermDay()
    val tymeLunarDay = tymeSolarDay.getLunarDay()
    val tymeLunarMonth = tymeLunarDay.getLunarMonth()
    val tymeLunarYear = tymeLunarMonth.getLunarYear()
    val tymeSixtyCycle = tymeLunarYear.getSixtyCycle()
    val tymeEarthBranch = tymeSixtyCycle.getEarthBranch()
    val tymeZodiac = tymeEarthBranch.getZodiac()
    val tymeSolarFestival = tymeSolarDay.getFestival()
    val tymeLunarFestival = tymeLunarDay.getFestival()

    if (time != null) {
        val dateTime = LocalDateTime(date, time)
        val instant = dateTime.toInstant(timeZone)

        append(dateTime.format(LocalDateTime.Formats.ISO))
        append(timeZone.offsetAt(instant))
    } else {
        append(date.format(LocalDate.Formats.ISO))
    }
    append(" ")
    append(date.dayOfWeek.toJavaDayOfWeek().getDisplayName(FULL, locale))
    append(" ")
    append(tymeTermDay.getName())
    append("第")
    append(tymeTermDay.getDayIndex() + 1)
    append("天 ")
    append(tymeLunarYear.getName())
    append(" ")
    append(tymeZodiac.getName())
    append("年 ")
    append(tymeLunarMonth.getName())
    append(" ")
    append(tymeLunarDay.getName())
    if (tymeSolarFestival != null) {
        append(" ")
        append(tymeSolarFestival.getName())
    }
    if (tymeLunarFestival != null) {
        append(" ")
        append(tymeLunarFestival.getName())
    }
}

fun ClosedRange<Instant>.toFormattedAgentTimeList(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    locale: Locale = Locale.getDefault(),
): List<String> {
    val startDate = start.toLocalDateTime(timeZone).date
    val endDate = endInclusive.toLocalDateTime(timeZone).date

    return (startDate..endDate).toFormattedAgentTimeList(timeZone, locale)
}

fun LocalDateRange.toFormattedAgentTimeList(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    locale: Locale = Locale.getDefault(),
) = map { it.formatAgentTime(timeZone, locale) }
