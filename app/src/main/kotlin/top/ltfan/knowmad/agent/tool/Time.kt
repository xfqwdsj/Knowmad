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
import com.tyme.solar.SolarDay
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.number
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.R
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant

class TimeTool(resources: Resources) : SimpleTool<TimeTool.Args>(
    argsSerializer = Args.serializer(),
    name = "time",
    description = resources.getString(R.string.llm_tool_time_description),
) {
    override suspend fun execute(args: Args) = Clock.System.now().formatAgentTime()

    @Serializable
    data object Args
}

fun Instant.formatAgentTime(): String = buildString {
    val timeZone = TimeZone.currentSystemDefault()
    val date = toLocalDateTime(timeZone)
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

    append(date.format(LocalDateTime.Formats.ISO))
    append(timeZone.offsetAt(this@formatAgentTime))
    append(" ")
    append(date.dayOfWeek.toJavaDayOfWeek().getDisplayName(FULL, Locale.getDefault()))
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
