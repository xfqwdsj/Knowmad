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

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.AIAgentEdgeBuilderIntermediate
import ai.koog.agents.core.dsl.builder.EdgeTransformationDslMarker
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
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
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toMessageResponses
import android.content.res.Resources
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.toDeprecatedClock
import top.ltfan.knowmad.R
import top.ltfan.knowmad.agent.tool.ExitTool
import top.ltfan.knowmad.agent.tool.TimeTool
import top.ltfan.knowmad.data.chat.AssistantStreamingMessageType
import top.ltfan.knowmad.ui.component.AssistantMessageState
import top.ltfan.knowmad.ui.component.AssistantMessageStreamingEvent
import top.ltfan.knowmad.ui.component.AssistantMessageStreamingEvent.AddString
import top.ltfan.knowmad.ui.component.AssistantMessageStreamingEvent.SetMessage
import top.ltfan.knowmad.util.Logger
import kotlin.time.Clock

private val logger = Logger("ChatAgent")

fun getChatAgent(
    promptExecutor: PromptExecutor,
    model: LLModel,
    newStreamingState: suspend (
        eventFlow: Flow<AssistantMessageStreamingEvent>,
        cancelStreaming: SendChannel<Unit>,
    ) -> AssistantMessageState.Streaming,
    onStreamingCancelled: (state: AssistantMessageState.Streaming) -> Unit,
    getUserMessage: suspend (state: AssistantMessageState.Streaming?) -> List<ContentPart>,
    resources: Resources,
    maxAgentIterations: Int = 50,
    toolRegistry: ToolRegistry = ToolRegistry {
        tool(TimeTool(resources))
    },
    systemPrompt: String = resources.systemPrompt(
        R.string.llm_prompt_head,
        R.string.llm_prompt_intro_medium,
        R.string.llm_agent_chat_prompt,
    ),
    buildPrompt: PromptBuilder.(
        system: PromptBuilder.() -> Message.System,
    ) -> Unit = { system ->
        system()
    },
): AIAgent<Unit, String> {
    suspend fun getUserMessage(state: AssistantMessageState.Streaming?): List<ContentPart> {
        logger.debug {
            "Waiting for user message... " +
                    "State ID: ${state?.id} " +
                    "Last message: ${state?.contents?.lastOrNull()?.content?.takeLast(50)}"
        }
        return getUserMessage.invoke(state).also {
            logger.debug {
                "Received user message. " +
                        "State ID: ${state?.id} " +
                        "Parts count: ${it.size} "
            }
        }
    }

    val strategy = strategy("chat") {
        val nodeGetState by node<List<ContentPart>, ChatAgentData<List<ContentPart>>> { parts ->
            val eventFlow =
                MutableSharedFlow<AssistantMessageStreamingEvent>(extraBufferCapacity = 10)
            val cancellation = Channel<Unit>()
            val state = newStreamingState(eventFlow, cancellation)
            ChatAgentData(
                eventFlow = eventFlow,
                cancelStreaming = cancellation,
                state = state,
                content = parts,
                partIndex = -1,
            ).also {
                logger.debug { "New $it" }
            }
        }
        val nodeLLMRequest by node<ChatAgentData<List<ContentPart>>, ChatAgentData<List<Message.Response>>> { data ->
            val (eventFlow, cancellation, state, userParts) = data
            var partIndex = data.partIndex
            llm.writeSession {
                appendPrompt {
                    userParts.takeIf { it.isNotEmpty() }?.let {
                        user(it)
                    }
                }
            }

            val frames = mutableListOf<StreamFrame>()
            var cancelled = false

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

                val cancellationReceiving = launch {
                    cancellation.receiveCatching().getOrNull()
                    logger.debug { "Streaming for ${state.id} is requesting cancellation." }
                    cancelled = true
                    llmRequest.cancel()
                    logger.debug { "Streaming job for ${state.id} cancelled." }
                    data.close()
                    onStreamingCancelled(state)
                    logger.debug { "Streaming for ${state.id} is cancelled." }
                }

                llmRequest.join().also {
                    cancellationReceiving.cancel()
                }
            }

            if (!cancelled) logger.debug { "Streaming for ${state.id} completed." }
            else logger.debug { "Streaming for ${state.id} ended due to cancellation." }

            val list = frames.toMessageResponses().run {
                if (!cancelled) this
                else filter { it !is Message.Tool }
            }
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
            val (eventFlow, _, _, toolResults) = data
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
        val nodeCompressHistory by nodeLLMCompressHistory<ChatAgentData<List<ReceivedToolResult>>>()
        val nodeReceiveFirstMessage by node<Unit, List<ContentPart>> {
            getUserMessage(null)
        }
        val nodeReceiveUserMessage by node<ChatAgentData<List<Message.Response>>, List<ContentPart>> {
            it.close()
            getUserMessage(it.state)
        }

        // TODO: recognize and restore uncompleted tool calls at every session start
        edge(nodeStart forwardTo nodeReceiveFirstMessage)
        edge(nodeReceiveFirstMessage forwardTo nodeGetState)
        edge(nodeGetState forwardTo nodeLLMRequest)
        edge(
            nodeLLMRequest forwardTo nodeExecuteTools
                    extracted { onMultipleToolCalls { true } },
        )
        edge(
            nodeExecuteTools forwardTo nodeCompressHistory
                    onCondition { llm.readSession { prompt.messages.size > 100 } }
                    transformed { it.also { logger.debug { "Compressing history for ${it.state.id}..." } } },
        )
        edge(nodeCompressHistory forwardTo nodeAppendToolResults)
        edge(nodeExecuteTools forwardTo nodeAppendToolResults)
        edge(nodeAppendToolResults forwardTo nodeLLMRequest)
        edge(nodeLLMRequest forwardTo nodeReceiveUserMessage)
        edge(nodeReceiveUserMessage forwardTo nodeGetState)
        edge(
            nodeExecuteTools forwardTo nodeFinish
                    onCondition { it.content.singleOrNull()?.tool == ExitTool.name }
                    transformed { it.also { it.close() } }
                    transformed { it.content.singleOrNull()?.result?.toString() ?: "" },
        )
    }

    val agentConfig = AIAgentConfig(
        prompt = prompt("chat") {
            buildPrompt {
                Message.System(
                    content = systemPrompt,
                    metaInfo = RequestMetaInfo.create(Clock.System.toDeprecatedClock()),
                ).also {
                    message(it)
                }
            }
        },
        model = model,
        maxAgentIterations = maxAgentIterations,
    )

    logger.debug { "Creating Agent instance." }
    return AIAgent(
        promptExecutor = promptExecutor,
        strategy = strategy,
        agentConfig = agentConfig,
        toolRegistry = toolRegistry,
    )
}

private data class ChatAgentData<T>(
    val eventFlow: MutableSharedFlow<AssistantMessageStreamingEvent>,
    val cancelStreaming: Channel<Unit>,
    val state: AssistantMessageState.Streaming,
    val content: T,
    val partIndex: Int,
) {
    suspend fun close() {
        logger.debug { "Closing $this" }
        cancelStreaming.trySend(Unit)
        cancelStreaming.close()
        eventFlow.emit(Finish)
        logger.debug { "Closed $this" }
    }

    fun <NewT> copy(
        newContent: NewT,
        partIndex: Int = this.partIndex,
    ) = ChatAgentData(
        eventFlow = eventFlow,
        cancelStreaming = cancelStreaming,
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
