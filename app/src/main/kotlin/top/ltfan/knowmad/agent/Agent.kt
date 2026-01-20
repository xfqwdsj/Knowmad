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
import ai.koog.agents.features.eventHandler.feature.EventHandler
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
import kotlin.time.Clock

fun getChatAgent(
    promptExecutor: PromptExecutor,
    model: LLModel,
    newStreamingState: (
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
    val strategy = strategy("chat") {
        val nodeGetState by node<List<ContentPart>, ChatAgentData<List<ContentPart>>> {
            val eventFlow =
                MutableSharedFlow<AssistantMessageStreamingEvent>(extraBufferCapacity = 10)
            val cancellation = Channel<Unit>()
            val state = newStreamingState(eventFlow, cancellation)
            ChatAgentData(
                eventFlow = eventFlow,
                cancelStreaming = cancellation,
                state = state,
                content = it,
                partIndex = 0,
            )
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
                        var lastIsToolCall: Boolean? = null

                        stream.collect { frame ->
                            frames.add(frame)
                            when (frame) {
                                is Append -> {
                                    if (lastIsToolCall == true) partIndex++
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
                                    partIndex++
                                    lastIsToolCall = true
                                    val toolCall = Message.Tool.Call(
                                        id = frame.id,
                                        tool = frame.name,
                                        content = frame.content,
                                        metaInfo = ResponseMetaInfo.create(Clock.System.toDeprecatedClock()),
                                    )
                                    eventFlow.emit(
                                        SetMessage(
                                            partIndex = partIndex,
                                            message = toolCall,
                                        ),
                                    )
                                }

                                is StreamFrame.End -> {}
                            }
                        }
                    }
                }

                val cancellationReceiving = launch {
                    cancellation.receiveCatching().getOrNull()
                    cancelled = true
                    llmRequest.cancel()
                    data.close()
                    onStreamingCancelled(state)
                }

                llmRequest.join().also {
                    cancellationReceiving.cancel()
                }
            }

            val list = frames.toMessageResponses().run {
                if (!cancelled) this
                else filter { it !is Message.Tool }
            }
            llm.withPrompt {
                prompt(this) {
                    messages(list)
                }
            }
            data.copy(newContent = list, partIndex = partIndex)
        }
        val nodeExecuteTools by node<ChatAgentData<List<Message.Tool.Call>>, ChatAgentData<List<ReceivedToolResult>>> { data ->
            data.copy(newContent = environment.executeTools(data.content))
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
            data.copy(newContent = emptyList(), partIndex = partIndex)
        }
        val nodeCompressHistory by nodeLLMCompressHistory<ChatAgentData<List<ReceivedToolResult>>>()
        val nodeReceiveFirstMessage by node<Unit, List<ContentPart>> {
            getUserMessage(null)
        }
        val nodeReceiveUserMessage by node<ChatAgentData<List<Message.Response>>, List<ContentPart>> {
            it.close()
            getUserMessage(it.state)
        }

        edge(nodeStart forwardTo nodeReceiveFirstMessage)
        edge(nodeReceiveFirstMessage forwardTo nodeGetState)
        edge(nodeGetState forwardTo nodeLLMRequest)
        edge(
            nodeLLMRequest forwardTo nodeExecuteTools
                    extracted { onMultipleToolCalls { true } },
        )
        edge(
            nodeExecuteTools forwardTo nodeCompressHistory
                    onCondition { llm.readSession { prompt.messages.size > 100 } },
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

    return AIAgent(
        promptExecutor = promptExecutor,
        strategy = strategy,
        agentConfig = agentConfig,
        toolRegistry = toolRegistry,
    ) {
        install(EventHandler) {
            onLLMStreamingFailed { }
        }
    }
}

private data class ChatAgentData<T>(
    val eventFlow: MutableSharedFlow<AssistantMessageStreamingEvent>,
    val cancelStreaming: Channel<Unit>,
    val state: AssistantMessageState.Streaming,
    val content: T,
    val partIndex: Int,
) {
    suspend fun close() {
        cancelStreaming.trySend(Unit)
        cancelStreaming.close()
        eventFlow.emit(Finish)
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
