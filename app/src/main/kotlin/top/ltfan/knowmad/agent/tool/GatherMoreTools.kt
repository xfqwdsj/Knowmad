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

package top.ltfan.knowmad.agent.tool

import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.ToolRegistryBuilder
import ai.koog.serialization.typeToken
import android.content.res.Resources
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import top.ltfan.knowmad.R
import top.ltfan.knowmad.agent.ChatAgentContextualInitializationTool
import top.ltfan.knowmad.agent.ChatAgentToolCallContext
import top.ltfan.knowmad.agent.SystemPromptInjectorTool
import top.ltfan.knowmad.ui.component.AssistantMessageStreamingEvent
import top.ltfan.knowmad.util.Logger
import kotlin.coroutines.resume

fun ToolRegistryBuilder.gatherToolsTool(
    resources: Resources,
    tools: ToolRegistryBuilder.() -> Unit,
) {
    tool(GatherMoreToolsTool(resources, tools))
    tools()
}

class GatherMoreToolsTool(
    private val resources: Resources,
    tools: ToolRegistryBuilder.() -> Unit,
) : Tool<GatherMoreToolsTool.Args, GatherMoreToolsTool.Result>(
    argsType = typeToken<Args>(),
    resultType = typeToken<Result>(),
    descriptor = ToolDescriptor(
        name = "gather_more_tools",
        description = resources.getString(R.string.llm_tool_gather_more_tools_description),
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "tools",
                description = resources.getString(R.string.llm_tool_gather_more_tools_arg_tools_description),
                type = ToolParameterType.List(ToolParameterType.String),
            ),
        ),
    ),
), SystemPromptInjectorTool, ChatAgentContextualInitializationTool {
    private val logger = Logger("GatherMoreToolsTool")

    private val registry = ToolRegistry(tools)
    private val managedTools = registry.tools.associateBy { it.name }
    private val gatheredNames = mutableSetOf<String>()

    override val additionalSystemPrompt
        get() = buildString {
            appendLine(resources.getString(R.string.llm_tool_gather_more_tools_prompt))
            appendLine()
            for (tool in registry.tools) {
                if (tool.name in gatheredNames) continue
                appendLine("- `${tool.name}`: ${tool.descriptor.description}")
            }
        }

    override suspend fun execute(args: Args): Result {
        val (llm, data) = currentCoroutineContext()[ChatAgentToolCallContext] ?: run {
            logger.error { "No ChatAgentToolCallContext in context" }
            return Result(notFound = args.tools)
        }
        val (eventFlow) = data

        val foundNames = mutableSetOf<String>()
        val notFoundNames = mutableSetOf<String>()
        val messages = mutableListOf<JsonElement>()

        val toolsToAppend = mutableListOf<ToolDescriptor>()

        for (requestedName in args.tools) {
            if (requestedName in gatheredNames) {
                foundNames += requestedName
                continue
            }

            val tool = managedTools[requestedName]
            if (tool != null) {
                val descriptor = tool.descriptor
                toolsToAppend += descriptor
                foundNames += requestedName
                gatheredNames += requestedName
                if (tool is MessageWhenGatheringTool) {
                    messages += tool.messageWhenGathering
                }
            } else {
                notFoundNames += requestedName
            }
        }

        llm.writeSession {
            tools += toolsToAppend
        }

        eventFlow.emit(
            AssistantMessageStreamingEvent.UpdateConversationMeta {
                it.copy(gatheredTools = gatheredNames)
            },
        )

        return Result(
            success = foundNames.ifEmpty { null },
            notFound = notFoundNames.ifEmpty { null },
            messages = messages.ifEmpty { null },
        )
    }

    @Serializable
    data class Args(val tools: Set<String>)

    @Serializable
    data class Result(
        val success: Set<String>? = null,
        val notFound: Set<String>? = null,
        val messages: List<JsonElement>? = null,
    )

    override suspend fun initializeWithChatAgentContext(
        llm: AIAgentLLMContext,
        eventFlow: MutableSharedFlow<AssistantMessageStreamingEvent>,
    ) {
        coroutineScope {
            suspendCancellableCoroutine { continuation ->
                launch {
                    eventFlow.emit(
                        AssistantMessageStreamingEvent.QueryConversationMeta {
                            gatheredNames += it.gatheredTools
                            continuation.resume(Unit)
                        },
                    )
                }
            }
            llm.writeSession {
                tools = tools.filter { descriptor ->
                    descriptor.name !in managedTools || descriptor.name in gatheredNames
                }
            }
        }
    }
}

interface MessageWhenGatheringTool {
    val messageWhenGathering: JsonElement
}
