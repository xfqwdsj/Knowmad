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

import ai.koog.agents.core.agent.exception.AIAgentMaxNumberOfIterationsReachedException
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import android.content.Intent
import android.os.IBinder
import androidx.annotation.StringRes
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.intellij.markdown.ast.ASTNode
import top.ltfan.knowmad.R
import top.ltfan.knowmad.agent.tool.conversationTools
import top.ltfan.knowmad.agent.tool.formatAgentTime
import top.ltfan.knowmad.agent.tool.gatherToolsTool
import top.ltfan.knowmad.agent.tool.scheduleTools
import top.ltfan.knowmad.agent.tool.scheduleToolsExtended
import top.ltfan.knowmad.agent.tool.timeTool
import top.ltfan.knowmad.data.chat.AssistantMessageContent
import top.ltfan.knowmad.data.chat.AssistantStreamingMessage
import top.ltfan.knowmad.data.chat.ChatDao
import top.ltfan.knowmad.data.chat.ChatData
import top.ltfan.knowmad.data.chat.ChatListMessage
import top.ltfan.knowmad.data.chat.ConversationEntity
import top.ltfan.knowmad.data.chat.ConversationMeta
import top.ltfan.knowmad.data.chat.MessageEntity
import top.ltfan.knowmad.data.chat.MessageEntityRole
import top.ltfan.knowmad.data.chat.MessageWithFilesAndBranchInfo
import top.ltfan.knowmad.data.chat.UiMessage
import top.ltfan.knowmad.data.chat.allStored
import top.ltfan.knowmad.data.chat.toUiMessage
import top.ltfan.knowmad.data.database.AppDatabase
import top.ltfan.knowmad.data.llm.LLMData
import top.ltfan.knowmad.data.llm.toClient
import top.ltfan.knowmad.notification.NotificationMessage
import top.ltfan.knowmad.notification.ReplyReceiver
import top.ltfan.knowmad.notification.showChatNotification
import top.ltfan.knowmad.notification.toNotificationMessage
import top.ltfan.knowmad.ui.component.AssistantMessageState
import top.ltfan.knowmad.ui.component.AssistantMessageStreamingEvent
import top.ltfan.knowmad.ui.component.AssistantMessageStreamingEvent.Finish
import top.ltfan.knowmad.util.JobInterruptibleTask
import top.ltfan.knowmad.util.Logger
import top.ltfan.knowmad.util.RemendProcessor
import top.ltfan.knowmad.util.ServiceInstanceBinder
import top.ltfan.knowmad.util.SnapshotLruCache
import top.ltfan.knowmad.util.asyncInterruptible
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class ModelService : LifecycleService() {
    private val defaultScope = lifecycleScope + Dispatchers.Default

    private val appDatabase by lazy { AppDatabase.get() }

    private val scheduleDao by lazy { appDatabase.scheduleDao() }
    private val llmConfigDao by lazy { appDatabase.llmConfigDao() }
    private val chatDao by lazy { appDatabase.chatDao() }

    private val chatDataStore by lazy { ChatData.createDataStore() }
    private val chatDataStateFlow by lazy { chatDataStore.dataStateFlow() }

    private val selectedModelEntityFlow by lazy {
        chatDataStateFlow.map { it.selectedModelId }
            .distinctUntilChanged()
            .map { it?.let { id -> llmConfigDao.getModelById(id) } }
            .stateIn(
                defaultScope,
                SharingStarted.Eagerly,
                null,
            )
    }

    val chatAgentServiceFlow by lazy {
        selectedModelEntityFlow
            .map { model ->
                val client = model?.let { llmConfigDao.getProviderById(it.providerConfigId) }
                    ?.toClient() ?: return@map null
                getChatAgentService(
                    promptExecutor = MultiLLMPromptExecutor(client),
                    model = model.model,
                )
            }
            .stateIn(
                defaultScope,
                SharingStarted.Eagerly,
                null,
            )
    }

    private val chatAgentToolRegistry by lazy {
        ToolRegistry {
            timeTool(application.resources, chatAgentStyle = true)
            scheduleTools(application, scheduleDao)
            gatherToolsTool(application.resources) {
                scheduleToolsExtended(application, scheduleDao) {
                    codeRunnerTools[it.components] = it
                }
            }
        }
    }

    val streamingMessages = mutableStateMapOf<Uuid, AssistantStreamingMessage>()
    val assistantMessageStates = SnapshotLruCache<Any, AssistantMessageState>(
        snapshotStateMap = mutableStateMapOf(),
        maxSize = 100,
    )

    fun getMessage(message: MessageWithFilesAndBranchInfo?): ChatListMessage? {
        if (message == null) return null

        if (!message.message.completed) {
            val streamingMessage = streamingMessages[message.key]
            if (streamingMessage == null) {
                logger.warn { "Streaming message not found for incomplete message ${message.key}" }
                defaultScope.launch {
                    chatDao.updateMessage(message.message.copy(completed = true))
                }
            } else {
                return streamingMessage
            }
        }

        streamingMessages.remove(message.key)
        return message
    }

    suspend fun createNewConversation(): ConversationEntity {
        val conversation = ConversationEntity(
            name = application.getString(R.string.agent_conversation_label_new),
        )
        chatDao.insertConversation(conversation, application)
        return conversation
    }

    private suspend inline fun modelWithFallback(
        modelId: Uuid?,
        task: String,
    ) = if (modelId == null) {
        logger.warn { "No model selected for $task, using chat selected model" }
        val service = chatAgentServiceFlow.value
            ?: error("Chat agent service not available for $task")
        service.promptExecutor to service.agentConfig.model
    } else {
        val modelEntity = llmConfigDao.getModelById(modelId)
            ?: error("Selected model for $task not found in database")
        val providerEntity = llmConfigDao.getProviderById(modelEntity.providerConfigId)
            ?: error("Provider for selected model for $task not found in database")
        MultiLLMPromptExecutor(providerEntity.toClient()) to modelEntity.model
    }

    suspend fun generateConversationName(conversationId: Uuid) = withContext(Dispatchers.Default) {
        val chatMessages = chatDao.getAllMessagesByConversation(conversationId).first().reversed()
            .asSequence()
            .flatMap { it.message.parts }
            .filterIsInstance<UiMessage.Koog>()
            .filter { it.display }
            .map { it.message }
            .filterNot { message -> message is Message.System }
            .fold("") { acc, message ->
                if (acc.length >= 2000) return@fold acc
                val content = message.content.replace("\\s+".toRegex(), " ")
                "[${message.role}] $content\n" + acc
            }
            .trim()
            .ifBlank { return@withContext null }

        val prompt = prompt("conversation-title") {
            system(application.resources.environmentSystemPrompt())
            system(
                application.getString(R.string.llm_prompt_generate_conversation_title)
                    .trimIndent().format(chatMessages),
            )
        }

        val (executor, model) = try {
            modelWithFallback(
                modelId = LLMData.createDataStore().data.first().conversationNameGenerationModelId,
                task = "conversation name generation",
            )
        } catch (e: Throwable) {
            logger.error(e) { "Error setting up model for conversation name generation, aborting generation" }
            return@withContext null
        }

        executor.runPromptForSimpleResult(
            model = model,
            prompt = prompt,
            beforeStart = { logger.debug { "Generating conversation title with LLM..." } },
            ifEmpty = { logger.warn { "LLM generated empty title" } },
            onSuccess = { logger.debug { "Generated conversation title: $it" } },
            onFailure = { logger.error(it) { "Error generating conversation title" } },
            transform = { filterIsInstance<Message.Assistant>().it() },
        )
    }

    suspend fun generateAssistantMessageSummary(
        conversationId: Uuid,
    ) = withContext(Dispatchers.Default) {
        var assistantMessage: String? = null

        val chatMessages = chatDao.getAllMessagesByConversation(conversationId).first().reversed()
            .asSequence()
            .map { it.message }
            .mapNotNull { entity ->
                val contentStr = entity.parts.asSequence()
                    .filterIsInstance<UiMessage.Koog>()
                    .filter { it.display }
                    .map { it.message }
                    .filterNot { it is Message.System }
                    .filterNot { it is Message.Tool.Result }
                    .fold("") { acc, message ->
                        val content = message.content
                            .replace("\\s+".toRegex(), " ")
                            .trim()
                        "$acc\n[${message.role}] $content"
                    }

                if (assistantMessage != null) return@mapNotNull contentStr
                if (entity.role != Assistant) return@mapNotNull null
                assistantMessage = contentStr
                null
            }
            .fold("") { acc, contentStr ->
                if (acc.length >= 2000) return@fold acc
                "$contentStr\n$acc"
            }

        if (assistantMessage == null) {
            logger.warn { "No assistant message found for generating summary." }
            return@withContext null
        }

        val prompt = prompt("assistant-message-summary") {
            system(application.resources.environmentSystemPrompt())
            system(
                application.getString(R.string.llm_prompt_generate_message_summary)
                    .trimIndent().format(chatMessages, assistantMessage),
            )
        }

        val (executor, model) = try {
            modelWithFallback(
                modelId = LLMData.createDataStore().data.first().conversationSummaryGenerationModelId,
                task = "assistant message summary generation",
            )
        } catch (e: Throwable) {
            logger.error(e) { "Error setting up model for assistant message summary generation, aborting generation" }
            return@withContext null
        }

        executor.runPrompt(
            model = model,
            prompt = prompt,
            beforeStart = { logger.debug { "Generating assistant message summary with LLM..." } },
            onSuccess = { logger.debug { "Generated assistant message summary" } },
            onFailure = { logger.error(it) { "Error generating assistant message summary" } },
        )?.mapNotNull {
            it.toNotificationMessage(application.resources)
        }
    }

    suspend fun generateErrorExplanation(
        errorMessage: String,
        onAppendExplanation: (String) -> Unit,
    ) = withContext(Dispatchers.Default) {
        val prompt = prompt("error-explanation") {
            system(application.resources.environmentSystemPrompt())
            system(
                application.getString(R.string.llm_prompt_generate_error_explanation)
                    .trimIndent().format(errorMessage.trim()),
            )
        }

        val (executor, model) = try {
            modelWithFallback(
                modelId = LLMData.createDataStore().data.first().errorExplanationModelId,
                task = "error explanation generation",
            )
        } catch (e: Throwable) {
            logger.error(e) { "Error setting up model for error explanation generation, aborting generation" }
            return@withContext
        }

        executor.executeStreaming(
            model = model,
            prompt = prompt,
        ).collect {
            if (it is TextDelta) {
                onAppendExplanation(it.text)
            }
        }
    }

    private val messageTaskSchedulerMutex = Mutex()
    private val messageTaskScheduler = mutableMapOf<Uuid, Channel<TaskPermit>>()
    private val messageTasksMutex = Mutex()
    private val _messageTasks = mutableStateMapOf<Uuid, MutableMessageJobDeque>()
    val messageTasks: Map<Uuid, MessageJobDeque> = _messageTasks

    fun stopMessageTasks(conversationId: Uuid) {
        lifecycleScope.launch {
            val tasksToStop = messageTasksMutex.withLock {
                _messageTasks[conversationId]?.value?.also {
                    _messageTasks.remove(conversationId)
                }
            }
            withContext(Dispatchers.Default) { tasksToStop?.forEach { it.stop() } }
        }
    }

    private suspend inline fun JobInterruptibleTask.scheduleMessageTask(
        conversationId: Uuid,
    ) = messageTaskSchedulerMutex.withLock {
        val permitChannel = messageTaskScheduler.getOrPut(conversationId) {
            Channel<TaskPermit>(1).also { permitChannel ->
                defaultScope.launch {
                    val grantChannel = Channel<TaskPermit>(Channel.UNLIMITED)

                    val workerJob = launch {
                        try {
                            while (true) {
                                val permit = withTimeoutOrNull(30.seconds) {
                                    grantChannel.receive()
                                }

                                if (permit == null) {
                                    if (tryCleanupMessageTaskScheduler(conversationId)) {
                                        logger.debug { "No pending message tasks for conversation $conversationId, cleaned up scheduler." }
                                        permitChannel.close()
                                        break
                                    }
                                    logger.debug { "No pending message tasks for conversation $conversationId, but scheduler not cleaned up, continuing to wait." }
                                    continue
                                }

                                try {
                                    permit.grantPermission()
                                    permit.task.job.join()
                                } finally {
                                    messageTasksMutex.withLock {
                                        val deque =
                                            _messageTasks[conversationId] ?: return@withLock
                                        deque.value = deque.value.removeAt(0)
                                        if (deque.value.isEmpty()) {
                                            _messageTasks.remove(conversationId)
                                        }
                                    }
                                }
                            }
                        } finally {
                            grantChannel.close()
                        }
                    }

                    try {
                        for (permit in permitChannel) {
                            messageTasksMutex.withLock {
                                _messageTasks.getOrPut(conversationId) {
                                    mutableStateOf(persistentListOf())
                                }.value += permit.task
                            }
                            grantChannel.send(permit)
                        }

                        tryCleanupMessageTaskScheduler(conversationId)
                    } finally {
                        workerJob.cancelAndJoin()
                    }
                }
            }
        }

        TaskPermit(this).also {
            permitChannel.send(it)
        }
    }.awaitPermission()

    private suspend inline fun tryCleanupMessageTaskScheduler(
        conversationId: Uuid,
    ) = withContext(NonCancellable) {
        messageTaskSchedulerMutex.withLock {
            val channel = messageTaskScheduler[conversationId] ?: return@withLock true
            if (channel.isEmpty) {
                messageTaskScheduler.remove(conversationId)
                true
            } else {
                false
            }
        }
    }

    fun sendMessage(
        conversationId: Uuid?,
        parts: List<ContentPart>,
        tools: ToolRegistry? = chatAgentToolRegistry,
        onNewConversation: ((ConversationEntity) -> Unit)? = null,
        getAllMessages: suspend ChatDao.(
            conversationId: Uuid,
        ) -> List<MessageWithFilesAndBranchInfo> = {
            getAllMessagesByConversation(it).first()
        },
        includeEnvironmentContext: Boolean = true,
        insertEnvironmentContext: Boolean = includeEnvironmentContext,
        contextMessages: List<UiMessage>? = null,
        generateConversationNameFromInitialInput: Boolean = true,
        insertAssistantMessageAndGet: suspend ChatDao.(
            message: MessageEntity,
            fileIds: List<Uuid>,
            getUpdatedEntity: (MessageWithFilesAndBranchInfo?) -> Unit,
        ) -> Long = ChatDao::insertMessageAndGet,
        beforeStart: (() -> Unit)? = null,
        onEnd: ((
            conversationId: Uuid,
            isNewConversation: Boolean,
        ) -> Unit)? = e@{ conversationId, isNewConversation ->
            if (!isNewConversation) return@e
            val service = chatAgentServiceFlow.value ?: return@e
            defaultScope.launch {
                val newName = generateConversationName(conversationId) ?: return@launch
                val conversation = chatDao.getConversationById(conversationId) ?: return@launch
                val updated = conversation.copy(name = newName)
                chatDao.updateConversation(updated, application)
            }
        },
    ) = defaultScope.asyncInterruptible task@{
        require(!insertEnvironmentContext || includeEnvironmentContext) {
            "insertEnvironmentContext can only be true if includeEnvironmentContext is also true"
        }

        val isNewConversation = conversationId == null
        val conversationId = if (isNewConversation) {
            appDatabase.withTransaction {
                createNewConversation().also {
                    onNewConversation?.invoke(it)
                }
            }.id
        } else {
            conversationId
        }
        val service = chatAgentServiceFlow.filterNotNull().first()

        scheduleMessageTask(conversationId)

        val eventFlow =
            MutableSharedFlow<AssistantMessageStreamingEvent>(extraBufferCapacity = 10)

        val updateChannel = Channel<Unit>(Channel.CONFLATED)

        val conversationMutex = Mutex()

        var initialConversationNameGenerationJob: Job? = null

        val agentDeferred = appDatabase.withTransaction {
            bind {
                var conversation =
                    chatDao.getConversationById(conversationId) ?: return@bind null

                val databaseMessages = chatDao.getAllMessages(conversationId)
                val messages = databaseMessages.flatMap { entity ->
                    entity.message.parts.filterIsInstance<UiMessage.Koog>().map { it.message }
                }
                val system = if (messages.isEmpty()) {
                    Message.System(
                        content = application.resources.chatSystemPrompt,
                        metaInfo = RequestMetaInfo.create(Clock.System),
                    )
                } else null
                val environmentMessage = Message.User(
                    content = getString(
                        R.string.llm_prompt_environment_context,
                        Clock.System.now().formatAgentTime(),
                        conversation.name,
                    ),
                    metaInfo = RequestMetaInfo.create(Clock.System),
                )

                system?.let {
                    chatDao.insertMessage(
                        message = MessageEntity(
                            conversationId = conversationId,
                            parts = listOf(it.toUiMessage()),
                            role = MessageEntityRole.System,
                            generatedBy = null,
                        ),
                        fileIds = emptyList(),
                    )
                }

                run {
                    val environmentContextFlag =
                        includeEnvironmentContext && insertEnvironmentContext
                    val shouldContinue = environmentContextFlag ||
                            contextMessages?.isNotEmpty() == true ||
                            parts.isNotEmpty()

                    if (!shouldContinue) return@run

                    val tempIds = mutableListOf<Uuid>()
                    chatDao.insertMessage(
                        message = MessageEntity(
                            conversationId = conversationId,
                            parts = buildList {
                                if (environmentContextFlag) {
                                    add(environmentMessage.toUiMessage(display = false))
                                }
                                contextMessages?.takeIf { it.isNotEmpty() }?.let {
                                    addAll(it.allStored(tempIds))
                                }
                                parts.takeIf { it.isNotEmpty() }?.let { parts ->
                                    add(
                                        Message.User(
                                            parts = parts.allStored(tempIds),
                                            metaInfo = RequestMetaInfo.create(Clock.System),
                                        ).toUiMessage(),
                                    )
                                }
                            },
                            role = MessageEntityRole.User,
                            generatedBy = null,
                        ),
                        fileIds = tempIds,
                    )
                }

                val state = AssistantMessageState.Streaming(
                    eventFlow = eventFlow,
                    model = service.agentConfig.model,
                    coroutineScope = defaultScope,
                    conversationId = conversationId,
                    remend = remend,
                    onQueryConversationMeta = {
                        chatDao.getConversationById(conversationId)?.also {
                            conversationMutex.withLock { conversation = it }
                        }?.meta ?: ConversationMeta()
                    },
                    onUpdateConversationMeta = { newMeta ->
                        launch {
                            conversationMutex.withLock {
                                val updated = conversation.copy(meta = newMeta)
                                chatDao.updateConversation(updated, application)
                                conversation = updated
                            }
                        }
                    },
                    onUpdate = { updateChannel.trySend(Unit) },
                ).apply {
                    val tempIds = mutableListOf<Uuid>()
                    chatDao.insertAssistantMessageAndGet(
                        toEntity().allStored(tempIds),
                        tempIds,
                    ) {
                        it ?: return@insertAssistantMessageAndGet
                        parentId = it.message.parentId
                        depth = it.message.depth
                        streamingMessages[it.message.id] = AssistantStreamingMessage(
                            state = this@apply,
                            branchIndex = it.branchIndex,
                            branchCount = it.branchCount,
                        )
                    }
                };

                {
                    val conversationToolRegistry = ToolRegistry {
                        conversationTools(
                            resources = application.resources,
                            mutex = conversationMutex,
                            getConversation = { conversation },
                            setConversation = { chatDao.updateConversation(it, application) },
                        )
                    }

                    val tools = if (tools != null) {
                        tools + conversationToolRegistry
                    } else {
                        conversationToolRegistry
                    }

                    logger.debug { "Running agent..." }
                    beforeStart?.invoke()

                    if (generateConversationNameFromInitialInput && databaseMessages.isEmpty()) {
                        initialConversationNameGenerationJob = defaultScope.launch {
                            val title = generateConversationName(conversationId) ?: return@launch
                            conversationMutex.withLock {
                                val updated = conversation.copy(name = title)
                                chatDao.updateConversation(updated, application)
                                conversation = updated
                            }
                        }
                    }

                    this@task.runAgent(
                        state = state,
                        eventFlow = eventFlow,
                    ) {
                        service.run(
                            userParts = parts,
                            eventFlow = eventFlow,
                            state = state,
                            tools = tools,
                            buildPrompt = {
                                system(application.resources.environmentSystemPrompt())
                                system?.let { message(it) } ?: messages(messages)
                                if (includeEnvironmentContext) {
                                    message(environmentMessage)
                                }
                                contextMessages?.forEach {
                                    if (it !is Koog) return@forEach
                                    message(it.message)
                                }
                            },
                        )
                    }.also {
                        this@task.launch {
                            for (_ in updateChannel) {
                                val result = chatDao.updateMessage(state.toEntity())
                                if (result < 1) {
                                    logger.warn { "Failed to update message with id ${state.id} on streaming update." }
                                    it.stop()
                                }
                            }
                        }
                    }
                }
            }
        }?.invoke()

        logger.debug { "Agent job started." }

        val completed = try {
            agentDeferred?.await()
        } catch (e: Throwable) {
            logger.error(e) { "Agent run error" }
            null
        } finally {
            updateChannel.close()
        }

        withContext(NonCancellable) {
            completed?.let { completed ->
                val entity = completed.toEntity()
                chatDao.updateMessage(entity)
                logger.debug { "Message completed" }
                entity
            }.also {
                initialConversationNameGenerationJob?.cancelAndJoin()
                onEnd?.invoke(conversationId, isNewConversation)
                showNotification(conversationId)
            }
        }
    }

    private fun CoroutineScope.runAgent(
        state: AssistantMessageState.Streaming,
        eventFlow: MutableSharedFlow<AssistantMessageStreamingEvent>,
        block: suspend CoroutineScope.() -> Unit,
    ) = asyncInterruptible {
        suspend fun cleanState(@StringRes message: Int) {
            val errorMessage = getString(message)

            state.cleanUncompletedToolCalls { iterator, toolCall ->
                iterator.add(
                    AssistantMessageContent.Completed(
                        message = Message.Tool.Result(
                            id = toolCall.id,
                            tool = toolCall.tool,
                            content = errorMessage,
                            metaInfo = RequestMetaInfo.create(Clock.System),
                        ),
                    ),
                )
            }

            state.contents += AssistantMessageContent.Completed(
                uiMessage = UiMessage.Error(errorMessage),
            )
        }

        var block: (suspend CoroutineScope.() -> Unit)? = block

        while (block != null) {
            val blockToRun = block
            block = null

            try {
                bind { blockToRun() }
            } catch (e: CancellationException) {
                logger.info(e) { "Agent run cancelled." }
            } catch (rerun: ChatAgentRerun) {
                logger.info(rerun) { "Agent will rerun" }
                block = { rerun.run() }
                continue
            } catch (e: AIAgentMaxNumberOfIterationsReachedException) {
                logger.info(e) { "Agent reached max number of iterations" }
                cleanState(R.string.chat_message_error_max_iterations_exceeded)
            } catch (e: Throwable) {
                logger.error(e) { "Agent run error" }
                cleanState(R.string.chat_message_error)
            }
        }

        logger.debug { "Agent run completed." }

        try {
            eventFlow.emit(Finish)
            state.awaitCompletedState()
        } catch (e: Throwable) {
            logger.error(e) { "Failed to complete message state after agent run completion" }
            null
        }
    }

    fun showNotification(conversationId: Uuid) {
        lifecycleScope.launch {
            val summary =
                generateAssistantMessageSummary(conversationId)?.toMutableList() ?: return@launch
            val quickReplies = withContext(Dispatchers.Default) {
                summary.extractQuickReplies().takeIf { it.isNotEmpty() }
            }
            val conversation = chatDao.getConversationById(conversationId) ?: return@launch
            application.showChatNotification(
                conversationId = conversationId,
                conversationName = conversation.name,
                messages = summary,
                quickReplies = quickReplies,
            )
        }
    }

    private fun MutableList<NotificationMessage>.extractQuickReplies(onlyLast: Boolean = true): List<String> {
        val tagRegex = Regex("""(?s)<quick_reply>(.*?)</quick_reply>""")
        val horizontalWhitespace = Regex("""[^\S\r\n]+""")
        val lineEdgeWhitespace = Regex("""(?m)^[^\S\r\n]+|[^\S\r\n]+$""")
        val multiNewlineRegex = Regex("""\n+""")

        val allExtracted = mutableListOf<String>()

        for (i in this.indices) {
            var processed = this[i].text

            if (!onlyLast || i == this.size - 1) {
                tagRegex.findAll(processed).forEach {
                    allExtracted.add(it.groupValues[1])
                }
            }

            processed = processed.replace(tagRegex, "\n")
            processed = processed.replace(horizontalWhitespace, " ")
            processed = processed.replace(lineEdgeWhitespace, "")
            processed = processed.replace(multiNewlineRegex, "\n")

            this[i] = this[i].copy(text = processed.trim())
        }

        return allExtracted
    }

    val remend by lazy {
        RemendProcessor(application.assets).also {
            defaultScope.launch {
                withContext(Dispatchers.Main) { lifecycle.addObserver(it) }
                it.initialize()
            }
        }
    }

    private val codeRunnerTools = mutableStateMapOf<List<String>, CodeRunnerTool>()
    var runCodeEnabled by mutableStateOf(true)
        private set
    val runnableCodeComponents get() = codeRunnerTools.keys.toSet()

    private val runCodeMutex = Mutex()
    fun runAssistantCode(
        state: AssistantMessageState,
        contentIndex: Int,
        node: ASTNode,
        components: List<String>,
        code: String,
    ) {
        defaultScope.launch {
            runCodeMutex.withLock {
                runCodeEnabled = false
                val tool = codeRunnerTools[components] ?: run {
                    logger.warn { "No code runner tool found for components $components" }
                    return@launch
                }
                if (state !is Completed) {
                    logger.warn { "Message state is not completed when running code, skipping." }
                    return@launch
                }
                val content = state.contents.getOrNull(contentIndex) ?: run {
                    logger.warn { "Content index $contentIndex out of bounds for message ${state.id}" }
                    return@launch
                }
                val result = runCatching { tool.runCode(components, code) }
                    .onFailure {
                        logger.error(it) { "Error running code with tool for components $components" }
                    }
                    .getOrNull()
                if (result != null) {
                    val newContent = content.appendedCodeResults(
                        Triple(node, result, Clock.System.now()),
                    )
                    val newContents =
                        state.contents.toMutableList().also { it[contentIndex] = newContent }
                    val newState = state.copy(
                        contents = newContents,
                    )
                    val newEntity = newState.toEntity()
                    runCatching { chatDao.updateMessage(newEntity) }
                        .onFailure {
                            logger.error(it) { "Failed to update message with new code result." }
                        }.onSuccess {
                            assistantMessageStates[state.id] = newState
                        }
                }
                runCodeEnabled = true
            }
        }
    }

    init {
        defaultScope.launch {
            for ((conversationId, text) in ReplyReceiver.channel) {
                sendMessage(
                    conversationId = conversationId,
                    parts = listOf(ContentPart.Text(text)),
                )
            }
        }
    }

    private val binder = Binder()
    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    inner class Binder : ServiceInstanceBinder<ModelService>() {
        override val service = this@ModelService
    }

    companion object {
        private val logger = Logger("ModelService")
    }
}

private class TaskPermit(
    val task: JobInterruptibleTask,
) {
    private val channel = Channel<Unit>(1)
    suspend fun awaitPermission() = channel.receive()
    suspend fun grantPermission() = try {
        channel.send(Unit)
    } finally {
        channel.close()
    }
}

private typealias MutableMessageJobDeque = MutableState<PersistentList<JobInterruptibleTask>>
private typealias MessageJobDeque = State<PersistentList<JobInterruptibleTask>>
