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

package top.ltfan.knowmad.data.task

import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.data.DataStoreCompanion
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@Serializable
data class NextSuggestionConfiguration(
    val enabled: Boolean = true,
    val fallbackTime: LocalTime = LocalTime(7, 0),
) {
    companion object : DataStoreCompanion<NextSuggestionConfiguration>() {
        override val fileName = "next_suggestion_configuration"
        override val default = NextSuggestionConfiguration()
    }
}

@Serializable
data class ClassProgressConfiguration(
    val enabled: Boolean = true,
    val scheduledUpdateTime: LocalTime = LocalTime(7, 0),
    val leadTime: Duration = 30.minutes,
    val schedulingHorizon: Duration = 7.days,
    val endThreshold: Duration = 5.minutes,
    val stayDuration: Duration = 5.minutes,
    val updateInterval: Duration = 1.minutes,
) {
    companion object : DataStoreCompanion<ClassProgressConfiguration>() {
        override val fileName = "class_progress_configuration"
        override val default = ClassProgressConfiguration()
    }
}
