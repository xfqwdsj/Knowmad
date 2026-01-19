/*
 * Knowmad - Knowledge nomad
 * Copyright (C) 2025-2026 LTFan (aka xfqwdsj)
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
import okio.Path
import okio.Path.Companion.toPath
import top.ltfan.knowmad.data.chat.UiMessage
import top.ltfan.knowmad.util.Cbor
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

object AppDatabaseConverters {
    @TypeConverter
    fun fromUuid(data: Uuid): ByteArray {
        return data.toByteArray()
    }

    @TypeConverter
    fun toUuid(data: ByteArray): Uuid {
        return Uuid.fromByteArray(data)
    }

    @TypeConverter
    fun fromLocalDate(data: LocalDate): String {
        return data.toString()
    }

    @TypeConverter
    fun toLocalDate(data: String): LocalDate {
        return LocalDate.parse(data)
    }

    @TypeConverter
    fun fromTimeZone(data: TimeZone): String {
        return data.id
    }

    @TypeConverter
    fun toTimeZone(data: String): TimeZone {
        return TimeZone.of(data)
    }

    @TypeConverter
    fun fromInstant(data: Instant): Long {
        return data.toEpochMilliseconds()
    }

    @TypeConverter
    fun toInstant(data: Long): Instant {
        return Instant.fromEpochMilliseconds(data)
    }

    @TypeConverter
    fun fromOkioPath(data: Path): String {
        return data.toString()
    }

    @TypeConverter
    fun toOkioPath(data: String): Path {
        return data.toPath()
    }

    @TypeConverter
    fun fromDurationList(data: List<Duration>): ByteArray {
        return Cbor.encodeToByteArray(data)
    }

    @TypeConverter
    fun toDurationList(data: ByteArray): List<Duration> {
        return Cbor.decodeFromByteArray(data)
    }

    @TypeConverter
    fun fromLLMProvider(data: LLMProvider): ByteArray {
        return Cbor.encodeToByteArray(data)
    }

    @TypeConverter
    fun toLLMProvider(data: ByteArray): LLMProvider {
        return Cbor.decodeFromByteArray(data)
    }

    @TypeConverter
    fun fromLLModel(data: LLModel): ByteArray {
        return Cbor.encodeToByteArray(data)
    }

    @TypeConverter
    fun toLLModel(data: ByteArray): LLModel {
        return Cbor.decodeFromByteArray(data)
    }

    @TypeConverter
    fun fromUiMessageList(data: List<UiMessage>): ByteArray {
        return Cbor.encodeToByteArray(data)
    }

    @TypeConverter
    fun toUiMessageList(data: ByteArray): List<UiMessage> {
        return Cbor.decodeFromByteArray(data)
    }
}
