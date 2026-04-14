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

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreamingAndSendResults
import ai.koog.agents.core.dsl.extension.nodeLLMSendMultipleToolResults
import ai.koog.agents.core.dsl.extension.onMultipleToolCalls
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.serialization.kotlinx.toKotlinxJsonElement
import ai.koog.serialization.typeToken
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromJsonElement
import top.ltfan.knowmad.R
import top.ltfan.knowmad.agent.environmentSystemPrompt
import top.ltfan.knowmad.agent.getChatAgentService
import top.ltfan.knowmad.agent.tool.ScheduleTools
import top.ltfan.knowmad.agent.tool.formatAgentTime
import top.ltfan.knowmad.data.chat.ChatData
import top.ltfan.knowmad.data.database.AppDatabase.Companion.appDatabase
import top.ltfan.knowmad.data.llm.toClient
import top.ltfan.knowmad.data.schedule.ScheduleDao
import top.ltfan.knowmad.notification.NextSuggestionNotification
import top.ltfan.knowmad.notification.SuggestionRequestReceiver.Companion.scheduleNextSuggestionGeneration
import top.ltfan.knowmad.notification.showNextSuggestionNotification
import top.ltfan.knowmad.notification.withAgentRunningNotification
import top.ltfan.knowmad.util.Json
import top.ltfan.knowmad.util.Logger
import kotlin.time.Clock

private val logger = Logger("GenerateNextSuggestion")

private class UpdateProgress(
    context: Context,
    private val onSummary: (String) -> Unit,
) : Tool<UpdateProgress.Args, String>(
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

private class SetResult(
    context: Context,
) : Tool<NextSuggestionNotification, NextSuggestionNotification>(
    argsType = typeToken<NextSuggestionNotification>(),
    resultType = typeToken<NextSuggestionNotification>(),
    descriptor = ToolDescriptor(
        name = "set_result",
        description = "set_result",
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
    override suspend fun execute(args: NextSuggestionNotification): NextSuggestionNotification {
        val suggestedNextGenerationTime = args.suggestedNextGenerationTime
        if (suggestedNextGenerationTime != null) {
            val now = Clock.System.now()
            require(suggestedNextGenerationTime >= now) { "Suggested next generation time must be in the future" }
        }
        return args
    }
}

suspend fun Context.generateAndShowNextSuggestion(
    prompt: String,
    scheduleDao: ScheduleDao,
    promptExecutor: PromptExecutor,
    model: LLModel,
    maxAgentIterations: Int = 50,
    onSummary: (String) -> Unit,
) = coroutineScope {
    try {
        val context = applicationContext

        val strategy = strategy<Unit, NextSuggestionNotification>("generate_next_suggestion") {
            val nodeLLMRequest by nodeLLMRequestStreamingAndSendResults<Unit>()
            val nodeExecuteTools by nodeExecuteMultipleTools(parallelTools = true)
            val nodeLLMSendToolResults by nodeLLMSendMultipleToolResults()

            edge(nodeStart forwardTo nodeLLMRequest)
            edge(
                nodeLLMRequest forwardTo nodeExecuteTools
                        onMultipleToolCalls { true },
            )
            edge(
                nodeExecuteTools forwardTo nodeFinish onCondition {
                    val singleOrNull = it.singleOrNull() ?: return@onCondition false
                    if (singleOrNull.resultKind != Success) return@onCondition false
                    singleOrNull.tool == "set_result"
                } transformed {
                    // TODO: use a more robust way to extract the result
                    it.singleOrNull()?.result?.let { json -> Json.decodeFromJsonElement(json.toKotlinxJsonElement()) }
                        ?: error("This should never happen")
                },
            )
            edge(nodeExecuteTools forwardTo nodeLLMSendToolResults)
            edge(
                nodeLLMSendToolResults forwardTo nodeExecuteTools
                        onMultipleToolCalls { true },
            )
            edge(
                nodeLLMSendToolResults forwardTo nodeLLMRequest
                        transformed {},
            )
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("next-suggestion") {
                system {
                    !context.resources.environmentSystemPrompt()
                    br()
                    !context.getString(R.string.llm_task_generate_next_suggestion_prompt)
                        .trimIndent().format(Clock.System.now().formatAgentTime(), prompt)
                }
            },
            model = model,
            maxAgentIterations = maxAgentIterations,
        )

        val toolRegistry = ToolRegistry {
            tool(ScheduleTools.QuerySemestersTool(context.resources, scheduleDao))
            tool(ScheduleTools.SearchSemestersTool(context.resources, scheduleDao))
            tool(ScheduleTools.QueryEventsTool(context.resources, scheduleDao))
            tool(ScheduleTools.SearchCoursesTool(context.resources, scheduleDao))
            tool(UpdateProgress(context, onSummary))
            tool(SetResult(context))
        }

        val agent = AIAgent(
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            strategy = strategy,
            toolRegistry = toolRegistry,
        ) {
            install(EventHandler) {
                onLLMCallStarting { logger.debug { "LLM call starting" } }
                onLLMCallCompleted { logger.debug { "LLM call completed" } }
                onToolCallCompleted { logger.debug { "Tool call completed" } }
            }
        }

        val notification = agent.run(Unit)
        coroutineContext.job.cancelChildren()

        logger.debug { "Next suggestion generated: $notification" }

        showNextSuggestionNotification(notification)

        notification.suggestedNextGenerationTime?.let { time ->
            logger.debug { "Scheduling next suggestion generation at suggested time: $time" }
            scheduleNextSuggestionGeneration(notification.suggestedNextGenerationPrompt) {
                timeInMillis = time.toEpochMilliseconds()
            }
        }
    } catch (e: Throwable) {
        logger.error(e) { "Failed to generate and show next suggestion" }
    }
}

class GenerateNextSuggestionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        coroutineScope {
            val context = applicationContext

            context.withAgentRunningNotification {
                setForeground(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

                val database = context.appDatabase
                val llmConfigDao = database.llmConfigDao()
                val scheduleDao = database.scheduleDao()

                logger.debug { "Retrieving selected model from database" }

                val chatDataStore = context(context) { ChatData.createDataStore() }
                val selectedModelId = chatDataStore.data
                    .map { it.selectedModelId }
                    .filterNotNull()
                    .first()

                logger.debug { "Querying model and provider from database" }

                val model = llmConfigDao.getModelById(selectedModelId) ?: run {
                    logger.error { "Selected model with id $selectedModelId not found in database" }
                    return@coroutineScope Result.failure()
                }
                val client =
                    llmConfigDao.getProviderById(model.providerConfigId)?.toClient() ?: run {
                        logger.error { "Provider for selected model with id ${model.providerConfigId} not found in database" }
                        return@coroutineScope Result.failure()
                    }

                val service = getChatAgentService(
                    promptExecutor = MultiLLMPromptExecutor(client),
                    model = model.model,
                )

                val prompt = inputData.getString(DATA_PROMPT) ?: run {
                    logger.warn { "No prompt provided for generating next suggestion, using default prompt" }
                    context.getString(DefaultPromptId)
                }

                logger.debug { "Starting to generate next suggestion with prompt: $prompt" }

                context.generateAndShowNextSuggestion(
                    prompt = prompt,
                    scheduleDao = scheduleDao,
                    promptExecutor = service.promptExecutor,
                    model = service.agentConfig.model,
                    onSummary = ::updateContent,
                )

                Result.success()
            } ?: run {
                logger.error { "Failed to show agent running notification" }
                Result.failure()
            }
        }
    } catch (e: Throwable) {
        logger.error(e) { "Failed to generate next suggestion" }
        Result.failure()
    }

    companion object {
        const val DATA_PROMPT = "DATA_PROMPT"

        val DefaultPromptId inline get() = R.string.llm_task_generate_next_suggestion_prompt_default

        fun buildRequest(
            prompt: String,
        ) = OneTimeWorkRequestBuilder<GenerateNextSuggestionWorker>().apply {
            setExpedited(RUN_AS_NON_EXPEDITED_WORK_REQUEST)

            val data = Data.Builder().apply {
                putString(DATA_PROMPT, prompt)
            }.build()
            setInputData(data)
        }.build()
    }
}
