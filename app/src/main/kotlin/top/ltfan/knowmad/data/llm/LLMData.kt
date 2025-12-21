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
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.dashscope.DashscopeClientSettings
import ai.koog.prompt.executor.clients.dashscope.DashscopeLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
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
    LLMProvider.DeepSeek to LLMProviderInfo(
        icon = R.drawable.ic_llm_provider_deepseek,
        label = R.string.llm_provider_deepseek_label,
        description = R.string.llm_provider_deepseek_description,
        defaultBaseUrl = "https://api.deepseek.com",
        platformUrl = "https://platform.deepseek.com",
        predefinedModels = setOf(
            DeepSeekModels.DeepSeekChat,
            DeepSeekModels.DeepSeekReasoner,
        ),
        getModelCapabilitiesUrl = { "https://api-docs.deepseek.com/quick_start/pricing" },
    ) { apiKey, baseUrl ->
        DeepSeekLLMClient(
            apiKey = apiKey,
            settings = DeepSeekClientSettings(
                baseUrl = baseUrl ?: "https://api.deepseek.com",
            ),
        )
    },
    LLMProvider.OpenAI to LLMProviderInfo(
        icon = R.drawable.ic_llm_provider_openai,
        label = R.string.llm_provider_openai_label,
        description = R.string.llm_provider_openai_description,
        defaultBaseUrl = "https://api.openai.com",
        platformUrl = "https://platform.openai.com",
        getModelCapabilitiesUrl = { id -> "https://platform.openai.com/docs/models/$id" },
    ) { apiKey, baseUrl ->
        OpenAILLMClient(
            apiKey = apiKey,
            settings = OpenAIClientSettings(
                baseUrl = baseUrl ?: "https://api.openai.com",
            ),
        )
    },
    LLMProvider.Anthropic to LLMProviderInfo(
        icon = R.drawable.ic_llm_provider_anthropic,
        label = R.string.llm_provider_anthropic_label,
        description = R.string.llm_provider_anthropic_description,
        defaultBaseUrl = "https://api.anthropic.com",
        platformUrl = "https://console.anthropic.com",
        getModelCapabilitiesUrl = { "https://platform.claude.com/docs/en/about-claude/models/overview" },
    ) { apiKey, baseUrl ->
        AnthropicLLMClient(
            apiKey = apiKey,
            settings = AnthropicClientSettings(
                baseUrl = baseUrl ?: "https://api.anthropic.com",
            ),
        )
    },
    LLMProvider.Google to LLMProviderInfo(
        icon = R.drawable.ic_llm_provider_google,
        label = R.string.llm_provider_google_label,
        description = R.string.llm_provider_google_description,
        defaultBaseUrl = "https://generativelanguage.googleapis.com",
        platformUrl = "https://aistudio.google.com/",
        getModelCapabilitiesUrl = { "https://ai.google.dev/gemini-api/docs/models" },
    ) { apiKey, baseUrl ->
        GoogleLLMClient(
            apiKey = apiKey,
            settings = GoogleClientSettings(
                baseUrl = baseUrl ?: "https://generativelanguage.googleapis.com",
            ),
        )
    },
    LLMProvider.OpenRouter to LLMProviderInfo(
        icon = R.drawable.ic_llm_provider_openrouter,
        label = R.string.llm_provider_openrouter_label,
        description = R.string.llm_provider_openrouter_description,
        defaultBaseUrl = "https://openrouter.ai",
        platformUrl = "https://openrouter.ai/keys",
        getModelCapabilitiesUrl = { id -> "https://openrouter.ai/$id" },
    ) { apiKey, baseUrl ->
        OpenRouterLLMClient(
            apiKey = apiKey,
            settings = OpenRouterClientSettings(
                baseUrl = baseUrl ?: "https://openrouter.ai",
            ),
        )
    },
    LLMProvider.Alibaba to LLMProviderInfo(
        icon = R.drawable.ic_llm_provider_alibaba,
        label = R.string.llm_provider_alibaba_label,
        description = R.string.llm_provider_alibaba_description,
        defaultBaseUrl = "https://dashscope.aliyuncs.com/",
        platformUrl = "https://dashscope.aliyun.com/",
        getModelCapabilitiesUrl = { "https://dashscope.console.aliyun.com/model" },
    ) { apiKey, baseUrl ->
        DashscopeLLMClient(
            apiKey = apiKey,
            settings = DashscopeClientSettings(
                baseUrl = baseUrl ?: "https://dashscope.aliyuncs.com/",
            ),
        )
    },
)

@Serializable
@Immutable
data class LLMProviderInfo(
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int,
    @param:StringRes val description: Int,
    val defaultBaseUrl: String,
    val platformUrl: String,
    val predefinedModels: Map<String, LLModel> = emptyMap(),
    val getModelCapabilitiesUrl: (id: String) -> String,
    val convertToClient: (apiKey: String, baseUrl: String?) -> LLMClient,
) {
    constructor(
        @DrawableRes icon: Int,
        @StringRes label: Int,
        @StringRes description: Int,
        defaultBaseUrl: String,
        platformUrl: String,
        predefinedModels: Set<LLModel>,
        getModelCapabilitiesUrl: (id: String) -> String,
        convertToClient: (apiKey: String, baseUrl: String?) -> LLMClient,
    ) : this(
        icon = icon,
        label = label,
        description = description,
        defaultBaseUrl = defaultBaseUrl,
        platformUrl = platformUrl,
        predefinedModels = predefinedModels.associateBy { it.id },
        getModelCapabilitiesUrl = getModelCapabilitiesUrl,
        convertToClient = convertToClient,
    )
}

@Suppress("UnstableApiUsage")
val LLMCapabilities = listOf(
    LLMCapabilityInfo.Capability(
        capability = LLMCapability.Speculation,
        label = R.string.llm_capability_speculation_label,
        description = R.string.llm_capability_speculation_description,
    ),
    LLMCapabilityInfo.Capability(
        capability = LLMCapability.Temperature,
        label = R.string.llm_capability_temperature_label,
        description = R.string.llm_capability_temperature_description,
    ),
    LLMCapabilityInfo.Capability(
        capability = LLMCapability.Tools,
        label = R.string.llm_capability_tools_label,
        description = R.string.llm_capability_tools_description,
    ),
    LLMCapabilityInfo.Capability(
        capability = LLMCapability.ToolChoice,
        label = R.string.llm_capability_tool_choice_label,
        description = R.string.llm_capability_tool_choice_description,
    ),
    LLMCapabilityInfo.Capability(
        capability = LLMCapability.MultipleChoices,
        label = R.string.llm_capability_multiple_choices_label,
        description = R.string.llm_capability_multiple_choices_description,
    ),
    LLMCapabilityInfo.Category(
        label = R.string.llm_capability_vision_label,
        description = R.string.llm_capability_vision_description,
        items = listOf(
            LLMCapabilityInfo.Capability(
                capability = LLMCapability.Vision.Image,
                label = R.string.llm_capability_image_label,
                description = R.string.llm_capability_image_description,
            ),
            LLMCapabilityInfo.Capability(
                capability = LLMCapability.Vision.Video,
                label = R.string.llm_capability_video_label,
                description = R.string.llm_capability_video_description,
            ),
        ),
    ),
    LLMCapabilityInfo.Capability(
        capability = LLMCapability.Audio,
        label = R.string.llm_capability_audio_label,
        description = R.string.llm_capability_audio_description,
    ),
    LLMCapabilityInfo.Capability(
        capability = LLMCapability.Document,
        label = R.string.llm_capability_document_label,
        description = R.string.llm_capability_document_description,
    ),
    LLMCapabilityInfo.Capability(
        capability = LLMCapability.Embed,
        label = R.string.llm_capability_embed_label,
        description = R.string.llm_capability_embed_description,
    ),
    LLMCapabilityInfo.Capability(
        capability = LLMCapability.Completion,
        label = R.string.llm_capability_completion_label,
        description = R.string.llm_capability_completion_description,
    ),
    LLMCapabilityInfo.Capability(
        capability = LLMCapability.PromptCaching,
        label = R.string.llm_capability_prompt_caching_label,
        description = R.string.llm_capability_prompt_caching_description,
    ),
    LLMCapabilityInfo.Capability(
        capability = LLMCapability.Moderation,
        label = R.string.llm_capability_moderation_label,
        description = R.string.llm_capability_moderation_description,
    ),
    LLMCapabilityInfo.Category(
        label = R.string.llm_capability_schema_label,
        description = R.string.llm_capability_schema_description,
        items = listOf(
            LLMCapabilityInfo.Category(
                label = R.string.llm_capability_json_schema_label,
                description = R.string.llm_capability_json_schema_description,
                items = listOf(
                    LLMCapabilityInfo.Capability(
                        capability = LLMCapability.Schema.JSON.Basic,
                        label = R.string.llm_capability_basic_json_schema_label,
                        description = R.string.llm_capability_basic_json_schema_description,
                    ),
                    LLMCapabilityInfo.Capability(
                        capability = LLMCapability.Schema.JSON.Standard,
                        label = R.string.llm_capability_standard_json_schema_label,
                        description = R.string.llm_capability_standard_json_schema_description,
                    ),
                ),
            ),
        ),
    ),
    LLMCapabilityInfo.Category(
        label = R.string.llm_capability_openai_endpoint_label,
        description = R.string.llm_capability_openai_endpoint_description,
        items = listOf(
            LLMCapabilityInfo.Capability(
                capability = LLMCapability.OpenAIEndpoint.Completions,
                label = R.string.llm_capability_openai_endpoint_chat_completions_label,
                description = R.string.llm_capability_openai_endpoint_chat_completions_description,
            ),
            LLMCapabilityInfo.Capability(
                capability = LLMCapability.OpenAIEndpoint.Responses,
                label = R.string.llm_capability_openai_endpoint_responses_label,
                description = R.string.llm_capability_openai_endpoint_responses_description,
            ),
        ),
    ),
)

@Serializable
@Immutable
sealed interface LLMCapabilityInfo {
    val label: Int
    val description: Int

    @Serializable
    @Immutable
    data class Capability(
        val capability: LLMCapability,
        @param:StringRes override val label: Int,
        @param:StringRes override val description: Int,
    ) : LLMCapabilityInfo

    @Serializable
    @Immutable
    data class Category(
        @param:StringRes override val label: Int,
        @param:StringRes override val description: Int,
        val items: List<LLMCapabilityInfo>,
    ) : LLMCapabilityInfo
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
