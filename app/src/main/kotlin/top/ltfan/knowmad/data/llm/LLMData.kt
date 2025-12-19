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

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.llm.LLMCapability
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
        defaultBaseUrl = "https://api.deepseek.com",
        defaultChatCompletionsPath = "chat/completions",
        platformUrl = "https://platform.deepseek.com",
        predefinedModels = setOf(
            DeepSeekModels.DeepSeekChat,
            DeepSeekModels.DeepSeekReasoner,
        ),
    ) { apiKey, baseUrl ->
        DeepSeekLLMClient(
            apiKey = apiKey,
            settings = DeepSeekClientSettings(
                baseUrl = baseUrl ?: "https://api.deepseek.com",
            ),
        )
    },
    LLMProvider.OpenAI to LLMProviderItem(
        icon = R.drawable.ic_llm_provider_openai,
        label = R.string.llm_provider_openai_label,
        description = R.string.llm_provider_openai_description,
        defaultBaseUrl = "https://api.openai.com",
        defaultChatCompletionsPath = "v1/chat/completions",
        platformUrl = "https://platform.openai.com",
    ) { apiKey, baseUrl ->
        OpenAILLMClient(
            apiKey = apiKey,
            settings = OpenAIClientSettings(
                baseUrl = baseUrl ?: "https://api.openai.com",
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
    val defaultBaseUrl: String,
    val defaultChatCompletionsPath: String,
    val platformUrl: String,
    val predefinedModels: Set<LLModel> = emptySet(),
    val convertToClient: (apiKey: String, baseUrl: String?) -> LLMClient,
)

@Suppress("UnstableApiUsage")
val LLMCapabilities = listOf(
    LLMCapabilityItem.Capability(
        capability = LLMCapability.Speculation,
        label = R.string.llm_capability_speculation_label,
        description = R.string.llm_capability_speculation_description,
    ),
    LLMCapabilityItem.Capability(
        capability = LLMCapability.Temperature,
        label = R.string.llm_capability_temperature_label,
        description = R.string.llm_capability_temperature_description,
    ),
    LLMCapabilityItem.Capability(
        capability = LLMCapability.Tools,
        label = R.string.llm_capability_tools_label,
        description = R.string.llm_capability_tools_description,
    ),
    LLMCapabilityItem.Capability(
        capability = LLMCapability.ToolChoice,
        label = R.string.llm_capability_tool_choice_label,
        description = R.string.llm_capability_tool_choice_description,
    ),
    LLMCapabilityItem.Capability(
        capability = LLMCapability.MultipleChoices,
        label = R.string.llm_capability_multiple_choices_label,
        description = R.string.llm_capability_multiple_choices_description,
    ),
    LLMCapabilityItem.Category(
        label = R.string.llm_capability_vision_label,
        description = R.string.llm_capability_vision_description,
        items = listOf(
            LLMCapabilityItem.Capability(
                capability = LLMCapability.Vision.Image,
                label = R.string.llm_capability_image_label,
                description = R.string.llm_capability_image_description,
            ),
            LLMCapabilityItem.Capability(
                capability = LLMCapability.Vision.Video,
                label = R.string.llm_capability_video_label,
                description = R.string.llm_capability_video_description,
            ),
        ),
    ),
    LLMCapabilityItem.Capability(
        capability = LLMCapability.Audio,
        label = R.string.llm_capability_audio_label,
        description = R.string.llm_capability_audio_description,
    ),
    LLMCapabilityItem.Capability(
        capability = LLMCapability.Document,
        label = R.string.llm_capability_document_label,
        description = R.string.llm_capability_document_description,
    ),
    LLMCapabilityItem.Capability(
        capability = LLMCapability.Embed,
        label = R.string.llm_capability_embed_label,
        description = R.string.llm_capability_embed_description,
    ),
    LLMCapabilityItem.Capability(
        capability = LLMCapability.Completion,
        label = R.string.llm_capability_openai_endpoint_completion_label,
        description = R.string.llm_capability_openai_endpoint_completion_description,
    ),
    LLMCapabilityItem.Capability(
        capability = LLMCapability.PromptCaching,
        label = R.string.llm_capability_prompt_caching_label,
        description = R.string.llm_capability_prompt_caching_description,
    ),
    LLMCapabilityItem.Capability(
        capability = LLMCapability.Moderation,
        label = R.string.llm_capability_moderation_label,
        description = R.string.llm_capability_moderation_description,
    ),
    LLMCapabilityItem.Category(
        label = R.string.llm_capability_schema_label,
        description = R.string.llm_capability_schema_description,
        items = listOf(
            LLMCapabilityItem.Category(
                label = R.string.llm_capability_json_schema_label,
                description = R.string.llm_capability_json_schema_description,
                items = listOf(
                    LLMCapabilityItem.Capability(
                        capability = LLMCapability.Schema.JSON.Basic,
                        label = R.string.llm_capability_basic_json_schema_label,
                        description = R.string.llm_capability_basic_json_schema_description,
                    ),
                    LLMCapabilityItem.Capability(
                        capability = LLMCapability.Schema.JSON.Standard,
                        label = R.string.llm_capability_standard_json_schema_label,
                        description = R.string.llm_capability_standard_json_schema_description,
                    ),
                ),
            ),
        ),
    ),
    LLMCapabilityItem.Category(
        label = R.string.llm_capability_openai_endpoint_label,
        description = R.string.llm_capability_openai_endpoint_description,
        items = listOf(
            LLMCapabilityItem.Capability(
                capability = LLMCapability.OpenAIEndpoint.Completions,
                label = R.string.llm_capability_openai_endpoint_completion_label,
                description = R.string.llm_capability_openai_endpoint_completion_description,
            ),
            LLMCapabilityItem.Capability(
                capability = LLMCapability.OpenAIEndpoint.Responses,
                label = R.string.llm_capability_openai_endpoint_responses_label,
                description = R.string.llm_capability_openai_endpoint_responses_description,
            ),
        ),
    ),
)

@Serializable
@Immutable
sealed interface LLMCapabilityItem {
    val label: Int
    val description: Int

    @Serializable
    @Immutable
    data class Capability(
        val capability: LLMCapability,
        @param:StringRes override val label: Int,
        @param:StringRes override val description: Int,
    ) : LLMCapabilityItem

    @Serializable
    @Immutable
    data class Category(
        @param:StringRes override val label: Int,
        @param:StringRes override val description: Int,
        val items: List<LLMCapabilityItem>,
    ) : LLMCapabilityItem
}

fun LLMProviderConfigEntity.toClient() = SupportedLLMProviders[provider]
    ?.convertToClient(decryptedApiKey, baseUrl) ?: error("Unsupported LLM Provider: $provider")

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
