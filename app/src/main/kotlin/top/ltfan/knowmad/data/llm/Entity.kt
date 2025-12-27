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
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import top.ltfan.knowmad.util.UnsafeEmptyRank
import kotlin.uuid.Uuid

@Entity(
    indices = [
        Index(value = ["rank"], unique = true),
    ],
)
data class LLMProviderConfigEntity(
    @PrimaryKey
    val id: Uuid = Uuid.generateV7(),
    val provider: LLMProvider,
    val name: String = provider.display,
    val apiKey: ByteArray,
    val iv: ByteArray?,
    val rank: ByteArray = UnsafeEmptyRank,
    val baseUrl: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LLMProviderConfigEntity

        if (id != other.id) return false
        if (provider != other.provider) return false
        if (name != other.name) return false
        if (!apiKey.contentEquals(other.apiKey)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (!rank.contentEquals(other.rank)) return false
        if (baseUrl != other.baseUrl) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + provider.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + apiKey.contentHashCode()
        result = 31 * result + (iv?.contentHashCode() ?: 0)
        result = 31 * result + rank.contentHashCode()
        result = 31 * result + (baseUrl?.hashCode() ?: 0)
        return result
    }
}

@Entity(
    indices = [
        Index(value = ["providerConfigId", "rank"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = LLMProviderConfigEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerConfigId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class LLMConfigEntity(
    @PrimaryKey
    val id: Uuid = Uuid.generateV7(),
    val providerConfigId: Uuid,
    val model: LLModel,
    val name: String = model.id,
    val rank: ByteArray = UnsafeEmptyRank,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LLMConfigEntity

        if (id != other.id) return false
        if (providerConfigId != other.providerConfigId) return false
        if (model != other.model) return false
        if (name != other.name) return false
        if (!rank.contentEquals(other.rank)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + providerConfigId.hashCode()
        result = 31 * result + model.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + rank.contentHashCode()
        return result
    }
}
