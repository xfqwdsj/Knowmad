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

package top.ltfan.knowmad.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L

private const val SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR
private const val SECONDS_PER_DAY = SECONDS_PER_HOUR * HOURS_PER_DAY

data class DurationParts(
    val sign: Int = 1,
    val days: Long = 0L,
    val hours: Long = 0L,
    val minutes: Long = 0L,
    val seconds: Long = 0L,
) {
    val hasValue: Boolean
        get() = days != 0L || hours != 0L || minutes != 0L || seconds != 0L

    fun toDuration(): Duration {
        val base =
            days.days +
                    hours.hours +
                    minutes.minutes +
                    seconds.seconds

        return if (sign < 0 && base != ZERO) -base else base
    }

    fun normalized(
        enableDays: Boolean = true,
        enableHours: Boolean = true,
        enableMinutes: Boolean = true,
        enableSeconds: Boolean = true,
    ): DurationParts = from(
        duration = toDuration(),
        enableDays = enableDays,
        enableHours = enableHours,
        enableMinutes = enableMinutes,
        enableSeconds = enableSeconds,
    )

    fun with(
        sign: Int = this.sign,
        days: Long = this.days,
        hours: Long = this.hours,
        minutes: Long = this.minutes,
        seconds: Long = this.seconds,
        enableDays: Boolean = true,
        enableHours: Boolean = true,
        enableMinutes: Boolean = true,
        enableSeconds: Boolean = true,
    ): DurationParts {
        return DurationParts(
            sign = sign,
            days = days,
            hours = hours,
            minutes = minutes,
            seconds = seconds,
        ).normalized(
            enableDays = enableDays,
            enableHours = enableHours,
            enableMinutes = enableMinutes,
            enableSeconds = enableSeconds,
        )
    }

    companion object {
        fun Duration.toParts(
            enableDays: Boolean = true,
            enableHours: Boolean = true,
            enableMinutes: Boolean = true,
            enableSeconds: Boolean = true,
        ): DurationParts = from(
            duration = this,
            enableDays = enableDays,
            enableHours = enableHours,
            enableMinutes = enableMinutes,
            enableSeconds = enableSeconds,
        )

        fun from(
            duration: Duration,
            enableDays: Boolean = true,
            enableHours: Boolean = true,
            enableMinutes: Boolean = true,
            enableSeconds: Boolean = true,
        ): DurationParts {
            if (!enableDays && !enableHours && !enableMinutes && !enableSeconds) {
                return DurationParts()
            }

            val negative = duration < ZERO
            var rest = (if (negative) -duration else duration).inWholeSeconds

            val days = if (enableDays) {
                val value = rest / SECONDS_PER_DAY
                rest %= SECONDS_PER_DAY
                value
            } else 0L

            val hours = if (enableHours) {
                val value = rest / SECONDS_PER_HOUR
                rest %= SECONDS_PER_HOUR
                value
            } else 0L

            val minutes = if (enableMinutes) {
                val value = rest / SECONDS_PER_MINUTE
                rest %= SECONDS_PER_MINUTE
                value
            } else 0L

            val seconds = if (enableSeconds) rest else 0L

            val hasValue = days > 0L || hours > 0L || minutes > 0L || seconds > 0L

            return DurationParts(
                sign = if (negative && hasValue) -1 else 1,
                days = days,
                hours = hours,
                minutes = minutes,
                seconds = seconds,
            )
        }
    }
}
