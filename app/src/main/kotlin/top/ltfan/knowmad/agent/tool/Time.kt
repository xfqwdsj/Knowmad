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

import ai.koog.agents.core.tools.SimpleTool
import android.content.res.Resources
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.alternativeParsing
import kotlinx.datetime.format.char
import kotlinx.datetime.format.optional
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.R
import kotlin.time.Clock

class TimeTool(resources: Resources) : SimpleTool<TimeTool.Args>(
    argsSerializer = Args.serializer(),
    name = "time",
    description = resources.getString(R.string.llm_tool_time_description),
) {
    override suspend fun execute(args: Args) = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .format(
            LocalDateTime.Format {
                year(); char('-'); monthNumber(); char('-'); day()
                alternativeParsing({ char('t') }) { char('T') }
                hour()
                char(':')
                minute()
                alternativeParsing({}) {
                    char(':')
                    second()
                    optional {
                        char('.')
                        secondFraction(1, 9)
                    }
                }
                char(' ')
                dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
            },
        )

    @Serializable
    data object Args
}
