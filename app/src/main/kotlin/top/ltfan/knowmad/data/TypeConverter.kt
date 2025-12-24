/*
 * Knowmad - Knowledge nomad
 * Copyright (C) 2025 LTFan (aka xfqwdsj)
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

package top.ltfan.knowmad.data

import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import androidx.room.TypeConverter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import top.ltfan.knowmad.util.Cbor
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

object UuidConverter {
    @TypeConverter
    fun fromUuid(uuid: Uuid): ByteArray {
        return uuid.toByteArray()
    }

    @TypeConverter
    fun toUuid(data: ByteArray): Uuid {
        return Uuid.fromByteArray(data)
    }
}

object LocalDateConverter {
    @TypeConverter
    fun fromLocalDate(date: LocalDate): String {
        return date.toString()
    }

    @TypeConverter
    fun toLocalDate(data: String): LocalDate {
        return LocalDate.parse(data)
    }
}

object TimeZoneConverter {
    @TypeConverter
    fun fromTimeZone(timeZone: TimeZone): String {
        return timeZone.id
    }

    @TypeConverter
    fun toTimeZone(id: String): TimeZone {
        return TimeZone.of(id)
    }
}

object InstantConverter {
    @TypeConverter
    fun fromInstant(instant: Instant): Long {
        return instant.toEpochMilliseconds()
    }

    @TypeConverter
    fun toInstant(data: Long): Instant {
        return Instant.fromEpochMilliseconds(data)
    }
}

object DurationListConverter {
    @TypeConverter
    fun fromDurationList(durations: List<Duration>): ByteArray {
        return Cbor.encodeToByteArray(durations)
    }

    @TypeConverter
    fun toDurationList(data: ByteArray): List<Duration> {
        return Cbor.decodeFromByteArray(data)
    }
}

object LLMProviderConverter {
    @TypeConverter
    fun fromLLMProvider(provider: LLMProvider): ByteArray {
        return Cbor.encodeToByteArray<LLMProvider>(provider)
    }

    @TypeConverter
    fun toLLMProvider(data: ByteArray): LLMProvider {
        return Cbor.decodeFromByteArray<LLMProvider>(data)
    }
}

object LLModelConverter {
    @TypeConverter
    fun fromLLModel(model: LLModel): ByteArray {
        return Cbor.encodeToByteArray<LLModel>(model)
    }

    @TypeConverter
    fun toLLModel(data: ByteArray): LLModel {
        return Cbor.decodeFromByteArray<LLModel>(data)
    }
}
