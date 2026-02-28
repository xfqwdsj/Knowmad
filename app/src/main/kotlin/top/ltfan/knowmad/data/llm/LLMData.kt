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

package top.ltfan.knowmad.data.llm

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.dashscope.DashscopeClientSettings
import ai.koog.prompt.executor.clients.dashscope.DashscopeLLMClient
import ai.koog.prompt.executor.clients.dashscope.DashscopeModels
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModuleBuilder
import top.ltfan.knowmad.R
import top.ltfan.knowmad.util.CryptoManager
import kotlin.uuid.Uuid

val SupportedLLMProviders = mapOf(
    LLMProvider.DeepSeek to LLMProviderInfo(
        icon = R.drawable.llm_provider_deepseek,
        label = R.string.llm_provider_deepseek_label,
        description = R.string.llm_provider_deepseek_description,
        defaultBaseUrl = "https://api.deepseek.com",
        platformUrl = "https://platform.deepseek.com",
        predefinedModels = DeepSeekModels,
        getModelCapabilitiesUrl = { "https://api-docs.deepseek.com/quick_start/pricing" },
        polymorphic = {
            polymorphic(
                LLMProvider::class,
                LLMProvider.DeepSeek::class,
                LLMProvider.DeepSeek.serializer(),
            )
        },
    ) { apiKey, baseUrl ->
        DeepSeekLLMClient(
            apiKey = apiKey,
            settings = DeepSeekClientSettings(
                baseUrl = baseUrl ?: "https://api.deepseek.com",
            ),
        )
    },
    LLMProvider.OpenAI to LLMProviderInfo(
        icon = R.drawable.llm_provider_openai,
        label = R.string.llm_provider_openai_label,
        description = R.string.llm_provider_openai_description,
        defaultBaseUrl = "https://api.openai.com",
        platformUrl = "https://platform.openai.com",
        predefinedModels = OpenAIModels,
        getModelCapabilitiesUrl = { id -> "https://platform.openai.com/docs/models/$id" },
        polymorphic = {
            polymorphic(
                LLMProvider::class,
                LLMProvider.OpenAI::class,
                LLMProvider.OpenAI.serializer(),
            )
        },
    ) { apiKey, baseUrl ->
        OpenAILLMClient(
            apiKey = apiKey,
            settings = OpenAIClientSettings(
                baseUrl = baseUrl ?: "https://api.openai.com",
            ),
        )
    },
    LLMProvider.Anthropic to LLMProviderInfo(
        icon = R.drawable.llm_provider_anthropic,
        label = R.string.llm_provider_anthropic_label,
        description = R.string.llm_provider_anthropic_description,
        defaultBaseUrl = "https://api.anthropic.com",
        platformUrl = "https://console.anthropic.com",
        predefinedModels = AnthropicModels,
        getModelCapabilitiesUrl = { "https://platform.claude.com/docs/en/about-claude/models/overview" },
        polymorphic = {
            polymorphic(
                LLMProvider::class,
                LLMProvider.Anthropic::class,
                LLMProvider.Anthropic.serializer(),
            )
        },
    ) { apiKey, baseUrl ->
        AnthropicLLMClient(
            apiKey = apiKey,
            settings = AnthropicClientSettings(
                baseUrl = baseUrl ?: "https://api.anthropic.com",
            ),
        )
    },
    LLMProvider.Google to LLMProviderInfo(
        icon = R.drawable.llm_provider_google,
        label = R.string.llm_provider_google_label,
        description = R.string.llm_provider_google_description,
        defaultBaseUrl = "https://generativelanguage.googleapis.com",
        platformUrl = "https://aistudio.google.com/",
        predefinedModels = GoogleModels,
        getModelCapabilitiesUrl = { "https://ai.google.dev/gemini-api/docs/models" },
        polymorphic = {
            polymorphic(
                LLMProvider::class,
                LLMProvider.Google::class,
                LLMProvider.Google.serializer(),
            )
        },
    ) { apiKey, baseUrl ->
        GoogleLLMClient(
            apiKey = apiKey,
            settings = GoogleClientSettings(
                baseUrl = baseUrl ?: "https://generativelanguage.googleapis.com",
            ),
        )
    },
    LLMProvider.OpenRouter to LLMProviderInfo(
        icon = R.drawable.llm_provider_openrouter,
        label = R.string.llm_provider_openrouter_label,
        description = R.string.llm_provider_openrouter_description,
        defaultBaseUrl = "https://openrouter.ai",
        platformUrl = "https://openrouter.ai/keys",
        predefinedModels = OpenRouterModels,
        getModelCapabilitiesUrl = { id -> "https://openrouter.ai/$id" },
        polymorphic = {
            polymorphic(
                LLMProvider::class,
                LLMProvider.OpenRouter::class,
                LLMProvider.OpenRouter.serializer(),
            )
        },
    ) { apiKey, baseUrl ->
        OpenRouterLLMClient(
            apiKey = apiKey,
            settings = OpenRouterClientSettings(
                baseUrl = baseUrl ?: "https://openrouter.ai",
            ),
        )
    },
    LLMProvider.Alibaba to LLMProviderInfo(
        icon = R.drawable.llm_provider_alibaba,
        label = R.string.llm_provider_alibaba_label,
        description = R.string.llm_provider_alibaba_description,
        defaultBaseUrl = "https://dashscope.aliyuncs.com/",
        platformUrl = "https://dashscope.aliyun.com/",
        predefinedModels = DashscopeModels,
        getModelCapabilitiesUrl = { "https://dashscope.console.aliyun.com/model" },
        polymorphic = {
            polymorphic(
                LLMProvider::class,
                LLMProvider.Alibaba::class,
                LLMProvider.Alibaba.serializer(),
            )
        },
    ) { apiKey, baseUrl ->
        DashscopeLLMClient(
            apiKey = apiKey,
            settings = DashscopeClientSettings(
                baseUrl = baseUrl ?: "https://dashscope.aliyuncs.com/",
            ),
        )
    },
)

@Immutable
data class LLMProviderInfo(
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int,
    @param:StringRes val description: Int,
    val defaultBaseUrl: String,
    val platformUrl: String,
    val predefinedModels: LLModelDefinitions,
    val getModelCapabilitiesUrl: (id: String) -> String,
    val polymorphic: SerializersModuleBuilder.() -> Unit,
    val convertToClient: (apiKey: String, baseUrl: String?) -> LLMClient,
)

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

@Serializable
@Immutable
data class LLMConfigEntry(
    val provider: LLMProvider,
    val providerName: String = provider.display,
    val apiKey: ByteArray,
    val iv: ByteArray?,
    val providerRank: ByteArray = byteArrayOf(0x80.toByte()),
    val baseUrl: String? = null,
    val model: LLModel,
    val modelName: String = model.id,
    val modelRank: ByteArray = byteArrayOf(0x80.toByte()),
) {
    fun getProviderConfig() = LLMProviderConfigEntity(
        provider = provider,
        name = providerName,
        apiKey = apiKey,
        iv = iv,
        rank = providerRank,
        baseUrl = baseUrl,
    )

    fun getModelConfig(providerConfigId: Uuid) = LLMConfigEntity(
        providerConfigId = providerConfigId,
        model = model,
        name = modelName,
        rank = modelRank,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LLMConfigEntry

        if (provider != other.provider) return false
        if (providerName != other.providerName) return false
        if (!apiKey.contentEquals(other.apiKey)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (!providerRank.contentEquals(other.providerRank)) return false
        if (baseUrl != other.baseUrl) return false
        if (model != other.model) return false
        if (modelName != other.modelName) return false
        if (!modelRank.contentEquals(other.modelRank)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = provider.hashCode()
        result = 31 * result + providerName.hashCode()
        result = 31 * result + apiKey.contentHashCode()
        result = 31 * result + (iv?.contentHashCode() ?: 0)
        result = 31 * result + providerRank.contentHashCode()
        result = 31 * result + (baseUrl?.hashCode() ?: 0)
        result = 31 * result + model.hashCode()
        result = 31 * result + modelName.hashCode()
        result = 31 * result + modelRank.contentHashCode()
        return result
    }
}

fun LLMProviderConfigEntity.toClient() = SupportedLLMProviders[provider]
    ?.convertToClient(decryptedApiKey, baseUrl) ?: error("Unsupported LLM Provider: $provider")

val LLMProviderConfigEntity.decryptedApiKey: String
    inline get() {
        if (iv == null) {
            return apiKey.decodeToString()
        }
        val manager = CryptoManager.LLMApiKey
        if (!manager.isKeyInitialized()) {
            return apiKey.decodeToString()
        }
        val decryptedBytes = manager.decrypt(
            ciphertext = apiKey,
            iv = iv,
        )
        return decryptedBytes.decodeToString()
    }
