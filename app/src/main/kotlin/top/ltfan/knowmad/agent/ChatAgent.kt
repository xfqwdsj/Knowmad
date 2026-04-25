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

package top.ltfan.knowmad.agent

import ai.koog.agents.core.agent.AIAgentService
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.GraphAIAgentService
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.dsl.builder.AIAgentEdgeBuilderIntermediate
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.onMultipleToolCalls
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.result
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toMessageResponses
import android.content.Context
import android.content.res.Resources
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.ltfan.knowmad.R
import top.ltfan.knowmad.agent.task.suggestion.GenerateNextSuggestionConversationId
import top.ltfan.knowmad.agent.task.suggestion.generateAndShowNextSuggestion
import top.ltfan.knowmad.ui.component.AssistantMessageState
import top.ltfan.knowmad.ui.component.AssistantMessageStreamingEvent
import top.ltfan.knowmad.util.Logger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.uuid.Uuid

private val logger = Logger("ChatAgent")

val SpecialConversations: Map<Uuid, Context.(List<ContentPart>) -> Unit> = mapOf(
    GenerateNextSuggestionConversationId to Context::generateAndShowNextSuggestion,
)

typealias ChatAgentService = GraphAIAgentService<ChatAgentData<List<ContentPart>>, List<Message.Response>>
typealias ChatAgent = GraphAIAgent<ChatAgentData<List<ContentPart>>, List<Message.Response>>

private enum class StreamFrameType {
    Text, Reasoning, Tool,
}

fun getChatAgentService(
    promptExecutor: PromptExecutor,
    model: LLModel,
    maxAgentIterations: Int = 50,
    installFeatures: GraphAIAgent.FeatureContext.() -> Unit = {},
): ChatAgentService {
    val strategy = strategy("chat") {
        val nodeInitialization by node<ChatAgentData<List<ContentPart>>, ChatAgentData<List<ContentPart>>> { data ->
            for (tool in llm.toolRegistry.tools) {
                if (tool is SystemPromptInjectorTool) {
                    llm.writeSession {
                        appendPrompt {
                            system(tool.additionalSystemPrompt)
                        }
                    }
                }
                if (tool is ChatAgentContextualInitializationTool) {
                    tool.initializeWithChatAgentContext(llm, data.eventFlow)
                }
            }
            data
        }
        val nodeLLMRequest by node<ChatAgentData<List<ContentPart>>, ChatAgentData<List<Message.Response>>> { data ->
            val (eventFlow, state, userParts) = data
            var partIndex = data.partIndex
            llm.writeSession {
                appendPrompt {
                    userParts.takeIf { it.isNotEmpty() }?.let {
                        user(it)
                    }
                }
            }

            val frames = mutableListOf<StreamFrame>()

            coroutineScope {
                val llmRequest = launch {
                    llm.writeSession {
                        val stream = requestLLMStreaming()
                        logger.debug { "Streaming for ${state.id} started." }

                        var last: StreamFrameType? = null

                        stream.collect { frame ->
                            frames.add(frame)
                            when (frame) {
                                is TextDelta -> {
                                    if (frame.text.isEmpty()) return@collect
                                    if (last != Text) partIndex++
                                    last = Text
                                    eventFlow.emit(
                                        AssistantMessageStreamingEvent.AddString(
                                            partIndex = partIndex,
                                            content = frame.text,
                                            messageType = Content,
                                        ),
                                    )
                                }

                                is ReasoningDelta -> {
                                    val text = frame.text
                                    val summary = frame.summary
                                    val content = when {
                                        text != null && summary != null -> "$summary\n\n$text"
                                        text != null -> text
                                        summary != null -> summary
                                        else -> return@collect
                                    }
                                    if (content.isEmpty()) return@collect
                                    if (last != Reasoning) partIndex++
                                    last = Reasoning
                                    eventFlow.emit(
                                        AssistantMessageStreamingEvent.AddString(
                                            partIndex = partIndex,
                                            content = content,
                                            messageType = Reasoning,
                                        ),
                                    )
                                }

                                is ToolCallComplete -> {
                                    if (last != Tool) partIndex++
                                    last = Tool
                                    val toolCall = Message.Tool.Call(
                                        id = frame.id,
                                        tool = frame.name,
                                        content = frame.content,
                                        metaInfo = ResponseMetaInfo.create(Clock.System),
                                    )
                                    eventFlow.emit(
                                        AssistantMessageStreamingEvent.SetMessage(
                                            partIndex = partIndex,
                                            message = toolCall,
                                        ),
                                    )
                                }

                                is End -> eventFlow.emit(
                                    AssistantMessageStreamingEvent.SetMetaInfo(
                                        metaInfo = frame.metaInfo,
                                    ),
                                )

                                else -> {}
                            }
                        }
                    }
                }

                llmRequest.join()
            }

            val list = frames.toMessageResponses()
            llm.withPrompt {
                prompt(this) {
                    messages(list)
                }
            }
            data.copy(newContent = list, partIndex = partIndex).also {
                logger.debug {
                    "LLM request node completed " +
                            "for ${it.state.id} with ${it.content.size} messages. " +
                            "Part index: ${it.partIndex}"
                }
            }
        }
        val nodeExecuteTools by node<ChatAgentData<List<Message.Tool.Call>>, ChatAgentData<ToolResultWithContext>> { data ->
            logger.debug { "Executing tools for ${data.state.id}..." }
            val context = ChatAgentToolCallContext(llm, data)
            data.copy(
                newContent = ToolResultWithContext(
                    result = withContext(context) { environment.executeTools(data.content) },
                    context = context,
                ),
            ).also {
                logger.debug { "Executed tools for ${it.state.id}, got ${it.content.result.size} results." }
            }
        }
        val nodeAppendToolResults by node<ChatAgentData<ToolResultWithContext>, ChatAgentData<List<ContentPart>>> { data ->
            val (eventFlow, _, dataContent) = data
            val (toolResults, context) = dataContent
            var partIndex = data.partIndex
            llm.writeSession {
                appendPrompt {
                    tool {
                        toolResults.forEach { result(it) }
                    }
                }
            }
            toolResults.forEach { toolResult ->
                eventFlow.emit(
                    AssistantMessageStreamingEvent.SetMessage(
                        partIndex = ++partIndex,
                        message = toolResult.toMessage(),
                    ),
                )
            }
            data.copy<List<ContentPart>>(newContent = emptyList(), partIndex = partIndex).also {
                logger.debug {
                    "Appended tool results for ${it.state.id}. " +
                            "Part index: ${it.partIndex}"
                }
                context.rerun?.let { rerun -> throw rerun }
            }
        }

        // TODO: recognize and restore uncompleted tool calls at every session start
        edge(nodeStart forwardTo nodeInitialization)
        edge(nodeInitialization forwardTo nodeLLMRequest)
        edge(
            nodeLLMRequest forwardTo nodeExecuteTools
                    extracted { onMultipleToolCalls { true } },
        )
        edge(nodeExecuteTools forwardTo nodeAppendToolResults)
        edge(nodeAppendToolResults forwardTo nodeLLMRequest)
        edge(
            nodeLLMRequest forwardTo nodeFinish
                    transformed { it.content },
        )
    }

    val agentConfig = AIAgentConfig(
        prompt = prompt("chat") {},
        model = model,
        maxAgentIterations = maxAgentIterations,
    )

    return AIAgentService(
        promptExecutor = promptExecutor,
        strategy = strategy,
        agentConfig = agentConfig,
        installFeatures = installFeatures,
    )
}

val Resources.chatSystemPrompt
    inline get() = systemPrompt(
        taskId = R.string.llm_agent_chat_prompt,
        environment = null,
    )

suspend fun ChatAgentService.create(
    state: AssistantMessageState.Streaming,
    tools: ToolRegistry = EMPTY,
    basePrompt: Prompt = agentConfig.prompt,
    buildPrompt: PromptBuilder.() -> Unit = {},
) = createAgent(
    id = state.id.toString(),
    additionalToolRegistry = tools,
    agentConfig = AIAgentConfig(
        prompt = prompt(basePrompt) {
            buildPrompt()
        },
        model = agentConfig.model,
        maxAgentIterations = agentConfig.maxAgentIterations,
        missingToolsConversionStrategy = agentConfig.missingToolsConversionStrategy,
        responseProcessor = agentConfig.responseProcessor,
    ),
)

suspend fun ChatAgentService.run(
    userParts: List<ContentPart>,
    eventFlow: MutableSharedFlow<AssistantMessageStreamingEvent>,
    state: AssistantMessageState.Streaming,
    tools: ToolRegistry = EMPTY,
    buildPrompt: PromptBuilder.() -> Unit = {},
) {
    withContext(ChatAgentContext(this)) {
        create(
            state = state,
            tools = tools,
            buildPrompt = buildPrompt,
        ).run(
            agentInput = ChatAgentData(
                eventFlow = eventFlow,
                state = state,
                content = userParts,
                partIndex = -1,
            ),
        )
    }
}

data class ChatAgentData<T>(
    val eventFlow: MutableSharedFlow<AssistantMessageStreamingEvent>,
    val state: AssistantMessageState.Streaming,
    val content: T,
    val partIndex: Int,
) {
    suspend fun close() {
        logger.debug { "Closing $this" }
        eventFlow.emit(Finish)
        logger.debug { "Closed $this" }
    }

    fun <NewT> copy(
        newContent: NewT,
        partIndex: Int = this.partIndex,
    ) = ChatAgentData(
        eventFlow = eventFlow,
        state = state,
        content = newContent,
        partIndex = partIndex,
    )
}

private data class ToolResultWithContext(
    val result: List<ReceivedToolResult>,
    val context: ChatAgentToolCallContext,
)

data class ChatAgentContext(
    val service: ChatAgentService,
) : AbstractCoroutineContextElement(ChatAgentContext) {
    companion object Key : CoroutineContext.Key<ChatAgentContext>
}

data class ChatAgentToolCallContext(
    val llm: AIAgentLLMContext,
    val data: ChatAgentData<List<Message.Tool.Call>>,
    var rerun: ChatAgentRerun? = null,
) : AbstractCoroutineContextElement(ChatAgentToolCallContext) {
    companion object Key : CoroutineContext.Key<ChatAgentToolCallContext>
}

data class ChatAgentRerun(
    private val service: ChatAgentService,
    private val agent: ChatAgent,
    private val data: ChatAgentData<List<ContentPart>>,
) : Throwable() {
    suspend fun run() {
        withContext(ChatAgentContext(service)) {
            agent.run(agentInput = data)
        }
    }
}

private inline infix fun <IncomingOutput, IntermediateOutput, OutgoingInput, NewIncomingOutput, NewIntermediateOutput, NewOutgoingInput> AIAgentEdgeBuilderIntermediate<IncomingOutput, ChatAgentData<IntermediateOutput>, OutgoingInput>.extracted(
    block: AIAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput>.() -> AIAgentEdgeBuilderIntermediate<NewIncomingOutput, NewIntermediateOutput, NewOutgoingInput>,
): AIAgentEdgeBuilderIntermediate<NewIncomingOutput, ChatAgentData<NewIntermediateOutput>, NewOutgoingInput> {
    var state: ChatAgentData<IntermediateOutput>? = null
    return this.transformed {
        state = it
        it.content
    }.block().transformed {
        state ?: error("State not captured")
        state.copy(newContent = it)
    }
}
