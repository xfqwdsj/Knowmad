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

package top.ltfan.knowmad.data.llm

import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

@Entity
@TypeConverters(LLMProviderConfigEntity.Converters::class)
data class LLMProviderConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val provider: LLMProvider,
    val apiKey: List<Byte>,
    val order: Int = 0,
    val baseUrl: String? = null,
    val chatCompletionsPath: String? = null,
    val requestTimeoutMillis: Long? = null,
    val connectTimeoutMillis: Long? = null,
    val socketTimeoutMillis: Long? = null,
) {
    object Converters {
        @OptIn(ExperimentalSerializationApi::class)
        @TypeConverter
        fun fromProvider(provider: LLMProvider): ByteArray {
            return Cbor.encodeToByteArray<LLMProvider>(provider)
        }

        @OptIn(ExperimentalSerializationApi::class)
        @TypeConverter
        fun toProvider(data: ByteArray): LLMProvider {
            return Cbor.decodeFromByteArray<LLMProvider>(data)
        }

        @TypeConverter
        fun fromApiKey(apiKey: List<Byte>): ByteArray {
            return apiKey.toByteArray()
        }

        @TypeConverter
        fun toApiKey(data: ByteArray): List<Byte> {
            return data.toList()
        }
    }
}

@Entity(
    indices = [Index(value = ["providerConfigId"])],
    foreignKeys = [
        ForeignKey(
            entity = LLMProviderConfigEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerConfigId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
@TypeConverters(LLMEntity.Converters::class)
data class LLMEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val order: Int = 0,
    val providerConfigId: Long,
    val model: LLModel,
    val params: LLMParams,
) {
    object Converters {
        @OptIn(ExperimentalSerializationApi::class)
        @TypeConverter
        fun fromModel(model: LLModel): ByteArray {
            return Cbor.encodeToByteArray<LLModel>(model)
        }

        @OptIn(ExperimentalSerializationApi::class)
        @TypeConverter
        fun toModel(data: ByteArray): LLModel {
            return Cbor.decodeFromByteArray<LLModel>(data)
        }

        @OptIn(ExperimentalSerializationApi::class)
        @TypeConverter
        fun fromParams(params: LLMParams): ByteArray {
            return Cbor.encodeToByteArray<LLMParams>(params)
        }

        @OptIn(ExperimentalSerializationApi::class)
        @TypeConverter
        fun toParams(data: ByteArray): LLMParams {
            return Cbor.decodeFromByteArray<LLMParams>(data)
        }
    }
}
