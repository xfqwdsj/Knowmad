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
import android.content.res.Resources
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.R
import top.ltfan.knowmad.agent.ChatAgentToolCallContext
import top.ltfan.knowmad.agent.ContextualInitializationTool
import top.ltfan.knowmad.agent.SystemPromptInjectorTool
import top.ltfan.knowmad.util.Logger

fun ToolRegistry.Builder.gatherToolsTool(
    resources: Resources,
    tools: ToolRegistry.Builder.() -> Unit,
) {
    tool(GatherMoreToolsTool(resources, tools))
    tools()
}

class GatherMoreToolsTool(
    private val resources: Resources,
    tools: ToolRegistry.Builder.() -> Unit,
) : Tool<GatherMoreToolsTool.Args, GatherMoreToolsTool.Result>(
    argsSerializer = Args.serializer(),
    resultSerializer = Result.serializer(),
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
), SystemPromptInjectorTool, ContextualInitializationTool {
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
        val context = currentCoroutineContext()[ChatAgentToolCallContext] ?: run {
            logger.error { "No ChatAgentToolCallContext in context" }
            return Result(
                success = null,
                notFound = args.tools,
            )
        }

        val foundNames = mutableListOf<String>()
        val notFoundNames = mutableListOf<String>()

        val toolsToAppend = mutableListOf<ToolDescriptor>()

        for (requestedName in args.tools) {
            if (requestedName in gatheredNames) {
                foundNames += requestedName
                continue
            }

            val descriptor = managedTools[requestedName]?.descriptor
            if (descriptor != null) {
                toolsToAppend += descriptor
                foundNames += requestedName
                gatheredNames += requestedName
            } else {
                notFoundNames += requestedName
            }
        }

        context.llm.writeSession {
            tools += toolsToAppend
        }

        return Result(
            success = foundNames.toSet().ifEmpty { null },
            notFound = notFoundNames.toSet().ifEmpty { null },
        )
    }

    @Serializable
    data class Args(val tools: Set<String>)

    @Serializable
    data class Result(
        val success: Set<String>?,
        val notFound: Set<String>?,
    )

    override suspend fun initialize(llm: AIAgentLLMContext) {
        llm.writeSession {
            tools = tools.filter { descriptor ->
                descriptor.name !in managedTools || descriptor.name in gatheredNames
            }
        }
    }
}
