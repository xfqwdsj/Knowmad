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

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import top.ltfan.knowmad.R
import top.ltfan.knowmad.agent.ModelService
import top.ltfan.knowmad.agent.getChatAgentService
import top.ltfan.knowmad.agent.tool.ScheduleTools
import top.ltfan.knowmad.data.chat.ChatData
import top.ltfan.knowmad.data.chat.ConversationEntity
import top.ltfan.knowmad.data.chat.MessageWithFilesAndBranchInfo
import top.ltfan.knowmad.data.database.AppDatabase.Companion.appDatabase
import top.ltfan.knowmad.data.llm.LLMData
import top.ltfan.knowmad.data.llm.toClient
import top.ltfan.knowmad.data.schedule.ScheduleDao
import top.ltfan.knowmad.notification.SuggestionRequestReceiver.Companion.scheduleNextSuggestionGeneration
import top.ltfan.knowmad.notification.showNextSuggestionNotification
import top.ltfan.knowmad.notification.withAgentRunningNotification
import top.ltfan.knowmad.util.Logger
import top.ltfan.knowmad.util.ServiceConnection
import top.ltfan.knowmad.util.filterConnected
import kotlin.time.Clock
import kotlin.uuid.Uuid

private val logger = Logger("GenerateNextSuggestion")

val GenerateNextSuggestionConversationId = Uuid.parse("019c0c33-1400-7467-be26-4710cc7f1f7d")

suspend fun Context.createSuggestionConversationIfNotExists() {
    val dao = appDatabase.chatDao()
    if (dao.getConversationById(GenerateNextSuggestionConversationId) == null) {
        dao.insertConversation(
            ConversationEntity(
                id = GenerateNextSuggestionConversationId,
                name = getString(R.string.llm_task_generate_next_suggestion_conversation_name),
                isPinned = true,
            ),
        )
    }
}

fun Context.generateAndShowNextSuggestion(prompt: String) {
    WorkManager.getInstance(applicationContext)
        .enqueue(GenerateNextSuggestionWorker.buildRequest(prompt))
}

fun Context.generateAndShowNextSuggestion(parts: List<ContentPart>) =
    generateAndShowNextSuggestion(
        parts.asSequence().filterIsInstance<ContentPart.Text>().joinToString("\n") { it.text },
    )

private suspend fun Context.doSuggest(
    prompt: String,
    scheduleDao: ScheduleDao,
    promptExecutor: PromptExecutor,
    model: LLModel,
    maxAgentIterations: Int = 50,
    onSummary: (String) -> Unit,
) = coroutineScope {
    val context = applicationContext

    val modelServiceConnection = context.ServiceConnection<ModelService>()

    try {
        val modelService = modelServiceConnection.status.filterConnected().first().service
        val agentService = getChatAgentService(promptExecutor, model, maxAgentIterations) {
            install(EventHandler) {
                onLLMCallStarting { logger.debug { "LLM call starting" } }
                onLLMCallCompleted { logger.debug { "LLM call completed" } }
                onToolCallCompleted { logger.debug { "Tool call completed" } }
            }
        }

        val toolRegistry = ToolRegistry {
            tool(ScheduleTools.QuerySemestersTool(context.resources, scheduleDao))
            tool(ScheduleTools.SearchSemestersTool(context.resources, scheduleDao))
            tool(ScheduleTools.QueryEventsTool(context.resources, scheduleDao))
            tool(ScheduleTools.SearchCoursesTool(context.resources, scheduleDao))
            suggestionTools(context, onSummary) { notification ->
                coroutineContext.job.cancelChildren()

                logger.debug { "Next suggestion generated: $notification" }

                showNextSuggestionNotification(notification)

                notification.suggestedNextGenerationTime?.let { time ->
                    logger.debug { "Scheduling next suggestion generation at suggested time: $time" }
                    scheduleNextSuggestionGeneration(notification.suggestedNextGenerationPrompt) {
                        timeInMillis = time.toEpochMilliseconds()
                    }
                }
            }
        }

        context.createSuggestionConversationIfNotExists()

        modelService.sendMessage(
            conversationId = GenerateNextSuggestionConversationId,
            parts = listOf(ContentPart.Text(prompt)),
            getService = { agentService },
            tools = toolRegistry,
            appendConversationTools = false,
            getAllMessages = {
                val messages =
                    getAllMessagesByConversation(GenerateNextSuggestionConversationId).first()
                val result = mutableListOf<MessageWithFilesAndBranchInfo>()
                var rounds = 0
                for (i in messages.indices.reversed()) {
                    val message = messages[i]
                    result.add(0, message)
                    if (message.message.role == User && (i == 0 || messages[i - 1].message.role != User)) {
                        rounds++
                        if (rounds >= 3) {
                            break
                        }
                    }
                }
                result
            },
            getSystemMessage = {
                Message.System(
                    content = context.getString(R.string.llm_task_generate_next_suggestion_prompt)
                        .trimIndent(),
                    metaInfo = RequestMetaInfo.create(Clock.System),
                )
            },
            insertSystemMessage = false,
            generateConversationNameFromInitialInput = false,
            onEnd = null,
            showNotification = false,
        ).join()
    } catch (e: Throwable) {
        logger.error(e) { "Failed to generate and show next suggestion" }
    } finally {
        modelServiceConnection.close()
    }
}

class GenerateNextSuggestionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val context = applicationContext

        context.withAgentRunningNotification {
            setForeground(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

            val database = context.appDatabase
            val llmConfigDao = database.llmConfigDao()
            val scheduleDao = database.scheduleDao()

            logger.debug { "Retrieving selected model from database" }

            val selectedModelId =
                LLMData.createDataStore(context).data.first().nextSuggestionGenerationModelId
                    ?: run {
                        logger.warn { "No model selected for next suggestion generation, using chat selected model" }
                        ChatData.createDataStore(context).data.first().selectedModelId
                    } ?: run {
                        logger.error { "No model selected for next suggestion generation" }
                        return Result.failure()
                    }

            logger.debug { "Querying model and provider from database" }

            val model = llmConfigDao.getModelById(selectedModelId) ?: run {
                logger.error { "Selected model with id $selectedModelId not found in database" }
                return Result.failure()
            }
            val client = llmConfigDao.getProviderById(model.providerConfigId)?.toClient() ?: run {
                logger.error { "Provider for selected model with id ${model.providerConfigId} not found in database" }
                return Result.failure()
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

            context.doSuggest(
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
