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

package top.ltfan.knowmad.agent.task.suggestion

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistryBuilder
import ai.koog.serialization.typeToken
import android.content.Context
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.R
import top.ltfan.knowmad.notification.NextSuggestionNotification
import kotlin.time.Clock

class UpdateProgressTool(
    context: Context,
    private val onSummary: (String) -> Unit,
) : Tool<UpdateProgressTool.Args, String>(
    argsType = typeToken<Args>(),
    resultType = typeToken<String>(),
    descriptor = ToolDescriptor(
        name = "update_progress",
        description = context.getString(R.string.llm_task_generate_next_suggestion_tool_update_progress_description),
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "summary",
                description = context.getString(R.string.llm_task_generate_next_suggestion_tool_update_progress_arg_summary_description),
                type = ToolParameterType.String,
            ),
        ),
        optionalParameters = emptyList(),
    ),
) {
    override suspend fun execute(args: Args): String {
        onSummary(args.summary)
        return "OK"
    }

    @Serializable
    data class Args(
        val summary: String,
    )
}

class SendNotificationTool(
    context: Context,
    private val onResult: suspend (NextSuggestionNotification) -> Unit,
) : Tool<NextSuggestionNotification, String>(
    argsType = typeToken<NextSuggestionNotification>(),
    resultType = typeToken<String>(),
    descriptor = ToolDescriptor(
        name = "send_notification",
        description = "Submit the final suggestion notification. Must be called exactly once with the complete result after thinking is done.",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "capsuleTitle",
                description = context.getString(R.string.llm_task_generate_next_suggestion_result_capsule_title),
                type = ToolParameterType.String,
            ),
            ToolParameterDescriptor(
                name = "notificationTitle",
                description = context.getString(R.string.llm_task_generate_next_suggestion_result_notification_title),
                type = ToolParameterType.String,
            ),
            ToolParameterDescriptor(
                name = "notificationContent",
                description = context.getString(R.string.llm_task_generate_next_suggestion_result_notification_content),
                type = ToolParameterType.String,
            ),
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor(
                name = "capsuleSubtitle",
                description = context.getString(R.string.llm_task_generate_next_suggestion_result_capsule_subtitle),
                type = ToolParameterType.String,
            ),
            ToolParameterDescriptor(
                name = "notificationSummary",
                description = context.getString(R.string.llm_task_generate_next_suggestion_result_notification_summary),
                type = ToolParameterType.String,
            ),
            ToolParameterDescriptor(
                name = "suggestedNextGenerationTime",
                description = context.getString(R.string.llm_task_generate_next_suggestion_result_suggested_next_generation_time),
                type = ToolParameterType.String,
            ),
            ToolParameterDescriptor(
                name = "suggestedNextGenerationPrompt",
                description = context.getString(R.string.llm_task_generate_next_suggestion_result_suggested_next_generation_prompt),
                type = ToolParameterType.String,
            ),
        ),
    ),
) {
    override suspend fun execute(args: NextSuggestionNotification): String {
        val suggestedNextGenerationTime = args.suggestedNextGenerationTime
        if (suggestedNextGenerationTime != null) {
            val now = Clock.System.now()
            require(suggestedNextGenerationTime >= now) { "Suggested next generation time must be in the future" }
        }
        onResult(args)
        return "OK"
    }
}

class GenerateSuggestionTool(
    private val context: Context,
) : Tool<GenerateSuggestionTool.Args, GenerateSuggestionTool.Result>(
    argsType = typeToken<Args>(),
    resultType = typeToken<Result>(),
    descriptor = ToolDescriptor(
        name = "generate_suggestion",
        description = context.getString(R.string.llm_tool_generate_suggestion_description),
        requiredParameters = emptyList(),
        optionalParameters = listOf(
            ToolParameterDescriptor(
                name = "prompt",
                description = context.getString(R.string.llm_tool_generate_suggestion_arg_prompt),
                type = ToolParameterType.String,
            ),
        ),
    ),
) {
    override suspend fun execute(args: Args): Result {
        return try {
            val prompt = args.prompt
                ?: context.getString(R.string.llm_task_generate_next_suggestion_prompt_manual)
            context.generateAndShowNextSuggestion(prompt)
            Result(success = true)
        } catch (e: Throwable) {
            Result(success = false, error = e.message)
        }
    }

    @Serializable
    data class Args(val prompt: String? = null)

    @Serializable
    data class Result(val success: Boolean, val error: String? = null)
}

fun ToolRegistryBuilder.suggestionToolsInChat(context: Context) {
    tool(GenerateSuggestionTool(context))
}

fun ToolRegistryBuilder.suggestionTools(
    context: Context,
    onSummary: (String) -> Unit,
    onResult: suspend (NextSuggestionNotification) -> Unit,
) {
    tool(UpdateProgressTool(context, onSummary))
    tool(SendNotificationTool(context, onResult))
}
