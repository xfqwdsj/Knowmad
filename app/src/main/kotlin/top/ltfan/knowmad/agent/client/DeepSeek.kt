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

package top.ltfan.knowmad.agent.client

import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekParams
import ai.koog.prompt.executor.clients.deepseek.models.DeepSeekChatCompletionResponse
import ai.koog.prompt.executor.clients.openai.base.AbstractOpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.OpenAICompatibleToolDescriptorSchemaGenerator
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMRequest
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMStreamResponse
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIChoiceLogProbs.ContentLogProbs
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIResponseFormat
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStreamOptions
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStreamToolCall
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIUsage
import ai.koog.prompt.executor.clients.serialization.AdditionalPropertiesFlatteningSerializer
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrameFlowBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import kotlinx.datetime.toDeprecatedClock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.Clock

/**
 * Implementation of [LLMClient] for DeepSeek API.
 *
 * @param apiKey The API key for the DeepSeek API
 * @param settings The base URL, chat completion path, and timeouts for the
 *    DeepSeek API, defaults to "https://api.deepseek.com" and 900s
 * @param clock Clock instance used for tracking response metadata
 *    timestamps.
 */
class DeepSeekLLMClient(
    apiKey: String,
    private val settings: DeepSeekClientSettings = DeepSeekClientSettings(),
    baseClient: HttpClient = HttpClient(),
    clock: Clock = Clock.System,
    toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator(),
) : AbstractOpenAILLMClient<DeepSeekChatCompletionResponse, DeepSeekChatCompletionStreamResponse>(
    apiKey = apiKey,
    settings = settings,
    baseClient = baseClient,
    clock = clock.toDeprecatedClock(),
    logger = staticLogger,
    toolsConverter = toolsConverter,
) {

    private companion object {
        private val staticLogger = KotlinLogging.logger { }

        init {
            // On class load register custom OpenAI JSON schema generators for structured output.
            registerOpenAIJsonSchemaGenerators(LLMProvider.DeepSeek)
        }
    }

    /**
     * Returns the specific implementation of the `LLMProvider` associated with
     * this client.
     *
     * In this case, it identifies the `DeepSeek` provider as the designated
     * LLM provider for the client.
     *
     * @return The `LLMProvider` instance representing DeepSeek.
     */
    override fun llmProvider(): LLMProvider = LLMProvider.DeepSeek

    override fun serializeProviderChatRequest(
        messages: List<OpenAIMessage>,
        model: LLModel,
        tools: List<OpenAITool>?,
        toolChoice: OpenAIToolChoice?,
        params: LLMParams,
        stream: Boolean,
    ): String {
        val deepSeekParams = params.toDeepSeekParams()
        val responseFormat = createResponseFormat(params.schema, model)

        val request = DeepSeekChatCompletionRequest(
            messages = messages,
            model = model.id,
            frequencyPenalty = deepSeekParams.frequencyPenalty,
            logprobs = deepSeekParams.logprobs,
            maxTokens = deepSeekParams.maxTokens,
            presencePenalty = deepSeekParams.presencePenalty,
            responseFormat = responseFormat,
            stop = deepSeekParams.stop,
            stream = stream,
            temperature = deepSeekParams.temperature,
            toolChoice = deepSeekParams.toolChoice?.toOpenAIToolChoice(),
            tools = tools,
            topLogprobs = deepSeekParams.topLogprobs,
            topP = deepSeekParams.topP,
            additionalProperties = deepSeekParams.additionalProperties,
        )

        return json.encodeToString(DeepSeekChatCompletionRequestSerializer, request)
    }

    override fun processProviderChatResponse(response: DeepSeekChatCompletionResponse): List<LLMChoice> {
        require(response.choices.isNotEmpty()) { "Empty choices in response" }
        return response.choices.map {
            it.message.toMessageResponses(
                it.finishReason,
                createMetaInfo(response.usage),
            )
        }
    }

    override fun decodeStreamingResponse(data: String): DeepSeekChatCompletionStreamResponse =
        json.decodeFromString(data)

    override fun decodeResponse(data: String): DeepSeekChatCompletionResponse =
        json.decodeFromString(data)

    override suspend fun StreamFrameFlowBuilder.processStreamingChunk(chunk: DeepSeekChatCompletionStreamResponse) {
        chunk.choices.firstOrNull()?.let { choice ->
            choice.delta.content?.let { emitAppend(it) }
            choice.delta.toolCalls?.forEach { toolCall ->
                val index = toolCall.index
                val id = toolCall.id
                val name = toolCall.function?.name
                val arguments = toolCall.function?.arguments
                upsertToolCall(index, id, name, arguments)
            }
            choice.finishReason?.let { emitEnd(it, createMetaInfo(chunk.usage)) }
        }
    }

    override fun createResponseFormat(
        schema: LLMParams.Schema?,
        model: LLModel,
    ): OpenAIResponseFormat? {
        return schema?.let {
            require(it.capability in model.capabilities) {
                "Model ${model.id} does not support structured output schema ${it.name}"
            }
            when (it) {
                is LLMParams.Schema.JSON -> OpenAIResponseFormat.JsonObject()
            }
        }
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.warn { "Moderation is not supported by DeepSeek API" }
        throw UnsupportedOperationException("Moderation is not supported by DeepSeek API.")
    }

    /**
     * Fetches a list of available model identifiers from the DeepSeek service.
     * https://api-docs.deepseek.com/api/list-models
     *
     * @return A list of string identifiers representing the available models.
     */
    override suspend fun models(): List<String> {
        logger.debug { "Fetching available models from DeepSeek" }

        val openAIResponse = httpClient.get(
            path = settings.modelsPath,
            responseType = DeepSeekModelsResponse::class,
        )

        return openAIResponse.data.map { it.id }
    }
}

/**
 * DeepSeek Chat Completions API Request
 *
 * @property messages A list of messages comprising the conversation so
 *    far.
 * @property model ID of the model to use. You can use deepseek-chat.
 * @property stream If set, partial message deltas will be sent. Tokens
 *    will be sent as data-only server-sent events (SSE) as they become
 *    available, with the stream terminated by a data: [DONE] message.
 * @property temperature What sampling temperature to use, between 0 and 2.
 *    Higher values like 0.8 will make the output more random, while lower
 *    values like 0.2 will make it more focused and deterministic. We
 *    generally recommend altering this or [topP] but not both.
 * @property tools A list of tools the model may call. Currently, only
 *    functions are supported as a tool. Use this to provide a list of
 *    functions the model may generate JSON inputs for. A max of 128
 *    functions is supported.
 * @property toolChoice Controls which (if any) tool is called by the
 *    model.
 * - `none` means the model will not call any tool and instead generates a
 *   message.
 * - `auto` means the model can pick between generating a message or
 *   calling one or more tools.
 * - `required` means the model must call one or more tools. Specifying a
 *   particular tool via `{"type": "function", "function": {"name":
 *   "my_function"}}` forces the model to call that tool. `none` is the
 *   default when no tools are present. `auto` is the default if tools are
 *   present.
 *
 * @property topP An alternative to sampling with temperature, called
 *    nucleus sampling, where the model considers the results of the tokens
 *    with top_p probability mass. So 0.1 means only the tokens comprising
 *    the top 10% probability mass are considered. We generally recommend
 *    altering this or [temperature] but not both.
 * @property topLogprobs An integer between 0 and 20 specifying the number
 *    of most likely tokens to return at each token position, each with an
 *    associated log probability. [logprobs] must be set to true if this
 *    parameter is used.
 * @property maxTokens Integer between 1 and 8192. The maximum number of
 *    tokens that can be generated in the chat completion. The total length
 *    of input tokens and generated tokens is limited by the model's
 *    context length. If [maxTokens] is not specified, default value 4096
 *    is used.
 * @property frequencyPenalty Number between -2.0 and 2.0. Positive values
 *    penalize new tokens based on their existing frequency in the text so
 *    far, decreasing the model's likelihood to repeat the same line
 *    verbatim.
 * @property presencePenalty Number between -2.0 and 2.0. Positive values
 *    penalize new tokens based on whether they appear in the text so far,
 *    increasing the model's likelihood to talk about new topics.
 * @property responseFormat An object specifying the format that the model
 *    must output. Setting to `{ "type": "json_object" }` enables JSON
 *    Output, which guarantees the message the model generates is valid
 *    JSON.
 * @property stop Up to 16 sequences where the API will stop generating
 *    further tokens.
 * @property logprobs Whether to return log probabilities of the output
 *    tokens or not. If true, returns the log probabilities of each output
 *    token returned in the content of a message.
 * @property streamOptions Options for streaming response. Only set this
 *    when you set stream: true.
 */
@Serializable
class DeepSeekChatCompletionRequest(
    val messages: List<OpenAIMessage>,
    override val model: String,
    override val stream: Boolean? = null,
    override val temperature: Double? = null,
    val tools: List<OpenAITool>? = null,
    val toolChoice: OpenAIToolChoice? = null,
    override val topP: Double? = null,
    override val topLogprobs: Int? = null,
    val maxTokens: Int? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val responseFormat: OpenAIResponseFormat? = null,
    val stop: List<String>? = null,
    val logprobs: Boolean? = null,
    val streamOptions: OpenAIStreamOptions? = null,
    val additionalProperties: Map<String, JsonElement>? = null,
) : OpenAIBaseLLMRequest

object DeepSeekChatCompletionRequestSerializer :
    AdditionalPropertiesFlatteningSerializer<DeepSeekChatCompletionRequest>(
        DeepSeekChatCompletionRequest.serializer(),
    )

/** DeepSeek Chat Completion Streaming Response */
@Serializable
class DeepSeekChatCompletionStreamResponse(
    val choices: List<DeepSeekStreamChoice>,
    override val created: Long,
    override val id: String,
    override val model: String,
    val systemFingerprint: String,
    @SerialName("object")
    val objectType: String = "chat.completion.chunk",
    val usage: OpenAIUsage? = null,
) : OpenAIBaseLLMStreamResponse

@Serializable
class DeepSeekStreamChoice(
    val delta: DeepSeekStreamDelta,
    val finishReason: String? = null,
    val index: Int,
    val logprobs: DeepSeekChoiceLogProbs? = null,
)

@Serializable
class DeepSeekStreamDelta(
    val content: String? = null,
    val reasoningContent: String? = null,
    val toolCalls: List<OpenAIStreamToolCall>? = null,
    val role: String? = null,
)

@Serializable
class DeepSeekChoiceLogProbs(
    val content: List<ContentLogProbs>? = null,
    val reasoningContent: List<ContentLogProbs>? = null,
)

@Serializable
data class DeepSeekModelsResponse(
    val data: List<DeepSeekModel>,
    @SerialName("object")
    val objectType: String,
)

@Serializable
data class DeepSeekModel(
    val id: String,
    @SerialName("object")
    val objectType: String,
    @SerialName("owned_by")
    val ownedBy: String,
)

fun LLMParams.toDeepSeekParams(): DeepSeekParams {
    if (this is DeepSeekParams) return this
    return DeepSeekParams(
        temperature = temperature,
        maxTokens = maxTokens,
        numberOfChoices = numberOfChoices,
        speculation = speculation,
        schema = schema,
        toolChoice = toolChoice,
        user = user,
        additionalProperties = additionalProperties,
    )
}
