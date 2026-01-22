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
import ai.koog.agents.core.agent.GraphAIAgentService
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.AIAgentEdgeBuilderIntermediate
import ai.koog.agents.core.dsl.builder.EdgeTransformationDslMarker
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.onMultipleToolCalls
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.executeTools
import ai.koog.agents.core.environment.result
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toMessageResponses
import android.content.res.Resources
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.toDeprecatedClock
import top.ltfan.knowmad.R
import top.ltfan.knowmad.agent.tool.TimeTool
import top.ltfan.knowmad.data.chat.AssistantStreamingMessageType
import top.ltfan.knowmad.ui.component.AssistantMessageState
import top.ltfan.knowmad.ui.component.AssistantMessageStreamingEvent
import top.ltfan.knowmad.ui.component.AssistantMessageStreamingEvent.AddString
import top.ltfan.knowmad.ui.component.AssistantMessageStreamingEvent.SetMessage
import top.ltfan.knowmad.util.Logger
import kotlin.time.Clock

private val logger = Logger("ChatAgent")

fun getChatAgentService(
    promptExecutor: PromptExecutor,
    model: LLModel,
    maxAgentIterations: Int = 50,
): GraphAIAgentService<ChatAgentData<List<ContentPart>>, List<Message.Response>> {
    val strategy = strategy("chat") {
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
                        var lastIsToolCall: Boolean? = null

                        stream.collect { frame ->
                            frames.add(frame)
                            when (frame) {
                                is Append -> {
                                    if (frame.text.isEmpty()) return@collect
                                    if (lastIsToolCall != false) partIndex++
                                    lastIsToolCall = false
                                    eventFlow.emit(
                                        AddString(
                                            partIndex = partIndex,
                                            content = frame.text,
                                            messageType = AssistantStreamingMessageType.Content,
                                        ),
                                    )
                                }

                                is StreamFrame.ToolCall -> {
                                    lastIsToolCall = true
                                    val toolCall = Message.Tool.Call(
                                        id = frame.id,
                                        tool = frame.name,
                                        content = frame.content,
                                        metaInfo = ResponseMetaInfo.create(Clock.System.toDeprecatedClock()),
                                    )
                                    eventFlow.emit(
                                        SetMessage(
                                            partIndex = ++partIndex,
                                            message = toolCall,
                                        ),
                                    )
                                }

                                is StreamFrame.End -> {} // TODO: make use of end frame
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
        val nodeExecuteTools by node<ChatAgentData<List<Message.Tool.Call>>, ChatAgentData<List<ReceivedToolResult>>> { data ->
            logger.debug { "Executing tools for ${data.state.id}..." }
            data.copy(newContent = environment.executeTools(data.content)).also {
                logger.debug { "Executed tools for ${it.state.id}, got ${it.content.size} results." }
            }
        }
        val nodeAppendToolResults by node<ChatAgentData<List<ReceivedToolResult>>, ChatAgentData<List<ContentPart>>> { data ->
            val (eventFlow, _, toolResults) = data
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
                    SetMessage(
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
            }
        }

        // TODO: recognize and restore uncompleted tool calls at every session start
        edge(nodeStart forwardTo nodeLLMRequest)
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
    )
}

val Resources.chatSystemPrompt
    inline get() = systemPrompt(
        R.string.llm_prompt_head,
        R.string.llm_prompt_intro_medium,
        R.string.llm_agent_chat_prompt,
    )

suspend fun GraphAIAgentService<ChatAgentData<List<ContentPart>>, List<Message.Response>>.run(
    userParts: List<ContentPart>,
    eventFlow: MutableSharedFlow<AssistantMessageStreamingEvent>,
    state: AssistantMessageState.Streaming,
    tools: ToolRegistry.Builder.() -> Unit = {},
    buildPrompt: PromptBuilder.() -> Unit = {},
) {
    createAgentAndRun(
        agentInput = ChatAgentData(
            eventFlow = eventFlow,
            state = state,
            content = userParts,
            partIndex = -1,
        ),
        id = state.id.toString(),
        additionalToolRegistry = ToolRegistry {
            tools()
        },
        agentConfig = AIAgentConfig(
            prompt = prompt(agentConfig.prompt) {
                buildPrompt()
            },
            model = agentConfig.model,
            maxAgentIterations = agentConfig.maxAgentIterations,
            missingToolsConversionStrategy = agentConfig.missingToolsConversionStrategy,
            responseProcessor = agentConfig.responseProcessor,
        ),
    )
}

fun ToolRegistry.Builder.defaultTools(resources: Resources) {
    tool(TimeTool(resources))
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

@EdgeTransformationDslMarker
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
