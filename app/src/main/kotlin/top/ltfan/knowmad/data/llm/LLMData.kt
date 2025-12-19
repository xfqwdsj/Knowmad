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

import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.R
import top.ltfan.knowmad.util.CryptoManager

val SupportedLLMProviders = mapOf(
    LLMProvider.DeepSeek to LLMProviderItem(
        icon = R.drawable.ic_llm_provider_deepseek,
        label = R.string.llm_provider_deepseek_label,
        description = R.string.llm_provider_deepseek_description,
        platformUrl = "https://platform.deepseek.com",
        predefinedModels = setOf(
            DeepSeekModels.DeepSeekChat,
            DeepSeekModels.DeepSeekReasoner,
        ),
    ) { entity ->
        DeepSeekLLMClient(
            apiKey = entity.decryptedApiKey,
            settings = DeepSeekClientSettings(
                baseUrl = entity.baseUrl ?: "https://api.deepseek.com",
                chatCompletionsPath = entity.chatCompletionsPath ?: "chat/completions",
                timeoutConfig = ConnectionTimeoutConfig(
                    requestTimeoutMillis = entity.requestTimeoutMillis ?: 900_000,
                    connectTimeoutMillis = entity.connectTimeoutMillis ?: 60_000,
                    socketTimeoutMillis = entity.socketTimeoutMillis ?: 900_000,
                ),
            ),
        )
    },
    LLMProvider.OpenAI to LLMProviderItem(
        icon = R.drawable.ic_llm_provider_openai,
        label = R.string.llm_provider_openai_label,
        description = R.string.llm_provider_openai_description,
        platformUrl = "https://platform.openai.com",
    ) { entity ->
        OpenAILLMClient(
            apiKey = entity.decryptedApiKey,
            settings = OpenAIClientSettings(
                baseUrl = entity.baseUrl ?: "https://api.openai.com",
                chatCompletionsPath = entity.chatCompletionsPath ?: "v1/chat/completions",
                timeoutConfig = ConnectionTimeoutConfig(
                    requestTimeoutMillis = entity.requestTimeoutMillis ?: 900_000,
                    connectTimeoutMillis = entity.connectTimeoutMillis ?: 60_000,
                    socketTimeoutMillis = entity.socketTimeoutMillis ?: 900_000,
                ),
            ),
        )
    },
)

@Serializable
@Immutable
data class LLMProviderItem(
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int,
    @param:StringRes val description: Int,
    val platformUrl: String,
    val predefinedModels: Set<LLModel> = emptySet(),
    val convertToClient: (entity: LLMProviderConfigEntity) -> LLMClient,
)

fun LLMProviderConfigEntity.toClient() = SupportedLLMProviders[provider]
    ?.convertToClient(this) ?: error("Unsupported LLM Provider: $provider")

val LLMProviderConfigEntity.decryptedApiKey: String
    inline get() {
        if (iv == null) {
            return apiKey.toByteArray().decodeToString()
        }
        val manager = CryptoManager.LLMApiKey
        if (!manager.isKeyInitialized()) {
            return apiKey.toByteArray().decodeToString()
        }
        val decryptedBytes = manager.decrypt(
            ciphertext = apiKey.toByteArray(),
            iv = iv.toByteArray(),
        )
        return decryptedBytes.decodeToString()
    }
