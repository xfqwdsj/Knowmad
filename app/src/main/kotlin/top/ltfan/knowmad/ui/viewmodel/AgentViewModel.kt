/*
 * Knowmad - Knowledge nomad
 * Copyright (C) 2025-2026 LTFan (aka xfqwdsj)
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

package top.ltfan.knowmad.ui.viewmodel

import ai.koog.agents.core.agent.exception.AIAgentMaxNumberOfIterationsReachedException
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavBackStack
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.toDeprecatedClock
import org.intellij.markdown.ast.ASTNode
import top.ltfan.knowmad.R
import top.ltfan.knowmad.agent.ChatAgentRerun
import top.ltfan.knowmad.agent.CodeRunnerTool
import top.ltfan.knowmad.agent.chatSystemPrompt
import top.ltfan.knowmad.agent.environmentSystemPrompt
import top.ltfan.knowmad.agent.getChatAgentService
import top.ltfan.knowmad.agent.run
import top.ltfan.knowmad.agent.runPromptForSimpleResult
import top.ltfan.knowmad.agent.tool.conversationTools
import top.ltfan.knowmad.agent.tool.formatAgentTime
import top.ltfan.knowmad.agent.tool.gatherToolsTool
import top.ltfan.knowmad.agent.tool.scheduleTools
import top.ltfan.knowmad.agent.tool.scheduleToolsExtended
import top.ltfan.knowmad.agent.tool.timeTool
import top.ltfan.knowmad.application.KnowmadApplication
import top.ltfan.knowmad.data.chat.AssistantMessageContent
import top.ltfan.knowmad.data.chat.AssistantStreamingMessage
import top.ltfan.knowmad.data.chat.ChatData
import top.ltfan.knowmad.data.chat.ChatListMessage
import top.ltfan.knowmad.data.chat.ConversationEntity
import top.ltfan.knowmad.data.chat.ConversationMeta
import top.ltfan.knowmad.data.chat.MessageEntity
import top.ltfan.knowmad.data.chat.MessageEntityRole
import top.ltfan.knowmad.data.chat.MessageWithFilesAndBranchInfo
import top.ltfan.knowmad.data.chat.UiMessage
import top.ltfan.knowmad.data.chat.toUiMessage
import top.ltfan.knowmad.data.llm.LLMConfigEntity
import top.ltfan.knowmad.data.llm.LLMProviderConfigEntity
import top.ltfan.knowmad.data.llm.toClient
import top.ltfan.knowmad.ui.component.AssistantMessageState
import top.ltfan.knowmad.ui.component.AssistantMessageStreamingEvent
import top.ltfan.knowmad.ui.component.AssistantMessageStreamingEvent.Finish
import top.ltfan.knowmad.ui.component.LLMProviderConfigLazyListState
import top.ltfan.knowmad.ui.component.PagingLazyListState
import top.ltfan.knowmad.ui.page.AgentMainPage
import top.ltfan.knowmad.ui.page.AgentSubPage
import top.ltfan.knowmad.ui.util.SnapshotLruCache
import top.ltfan.knowmad.util.Logger
import top.ltfan.knowmad.util.RemendProcessor
import top.ltfan.knowmad.util.collectAsState
import top.ltfan.knowmad.util.transform
import kotlin.time.Clock
import kotlin.uuid.Uuid

class AgentViewModel(app: KnowmadApplication) : AndroidViewModel<KnowmadApplication>(app) {
    private val logger = Logger("AgentViewModel")

    val backStack = NavBackStack<AgentSubPage>(AgentMainPage())

    val chatDao = application.appDatabase.chatDao()
    val llmConfigDao = application.appDatabase.llmConfigDao()
    val scheduleDao = application.appDatabase.scheduleDao()

    val chatDataStore = ChatData.createDataStore()
    val chatDataStateFlow = chatDataStore.dataStateFlow()
    val chatData = chatDataStore.asMutableState(chatDataStateFlow.value)

    val drawerState = DrawerState(DrawerValue.Closed)
    var savedMessagesFirstVisibleItemIndex by mutableIntStateOf(0)
    var savedMessagesFirstVisibleItemScrollOffset by mutableIntStateOf(0)
    val assistantMessageStates = SnapshotLruCache<Any, AssistantMessageState>(
        snapshotStateMap = mutableStateMapOf(),
        maxSize = 100,
    )

    suspend fun toggleDrawer() {
        if (drawerState.isClosed) {
            drawerState.open()
        } else {
            drawerState.close()
        }
    }

    val conversationListState = PagingLazyListState(viewModelScope) {
        application.appDatabase.chatDao().getAllConversations()
    }

    var messageListLoading by mutableStateOf(false)

    private val currentConversationIdFlow = chatDataStateFlow.map { it.conversation }
        .distinctUntilChanged()
    var currentConversationId by chatData.transform(
        transformIn = { conversation },
        transformOut = {
            messageListLoading = true
            copy(conversation = it)
        },
    )

    init {
        viewModelScope.launch {
            val id = currentConversationIdFlow.filterNotNull().first()
            chatDao.getConversationById(id) ?: run { currentConversationId = null }
        }
    }

    private val conversationAndChatData by currentConversationIdFlow
        .map { id ->
            ConversationAndChatData(
                conversationFlow = id?.let {
                    chatDao.getConversationFlowById(it)
                } ?: flowOf(null),
                messageCountFlow = id?.let {
                    chatDao.getMessageCountFlowInCurrentTreeByConversation(it)
                } ?: flowOf(0),
                messagesState = id?.let {
                    PagingLazyListState {
                        chatDao.getMessagesPagingByConversationReversed(it)
                    }
                },
            )
        }
        .onEach {
            messageListLoading = false
        }
        .stateIn(
            viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ConversationAndChatData(
                conversationFlow = flowOf(null),
                messageCountFlow = flowOf(0),
                messagesState = null,
            ),
        )
        .collectAsState()

    val currentConversationFlow get() = conversationAndChatData.conversationFlow
    val currentMessageCountFlow get() = conversationAndChatData.messageCountFlow
    val currentMessagesState get() = conversationAndChatData.messagesState

    val streamingMessages = mutableStateMapOf<Uuid, AssistantStreamingMessage>()

    fun getMessage(message: MessageWithFilesAndBranchInfo?): ChatListMessage? {
        if (message == null) return null

        if (!message.message.completed) {
            val streamingMessage = streamingMessages[message.key]
            if (streamingMessage == null) {
                logger.warn { "Streaming message not found for incomplete message ${message.key}" }
                viewModelScope.launch(Dispatchers.IO) {
                    chatDao.updateMessage(message.message.copy(completed = true))
                }
            } else {
                return streamingMessage
            }
        }

        streamingMessages.remove(message.key)
        return message
    }

    fun messageOnPrevious(message: ChatListMessage) {
        if (message is Branched) {
            viewModelScope.launch(Dispatchers.IO) {
                chatDao.selectPreviousMessageOnBranch(message.key)
            }
        }
    }

    fun messageOnNext(message: ChatListMessage) {
        if (message is Branched) {
            viewModelScope.launch(Dispatchers.IO) {
                chatDao.selectNextMessageOnBranch(message.key)
            }
        }
    }

    fun messageOnRegenerate(message: ChatListMessage) {

    }

    fun newConversation() {
        currentConversationId = null
    }

    fun editConversation(
        conversation: ConversationEntity,
        onFinished: () -> Unit = {},
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            application.appDatabase.chatDao().updateConversation(conversation)
            onFinished()
        }
    }

    suspend fun autoGenerateConversationName(conversation: ConversationEntity): String? {
        val chatMessages = chatDao.getAllMessagesByConversationOnce(conversation.id).reversed()
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
            .ifBlank { return null }

        val service = currentAgentService.value ?: return null // TODO: use custom executor
        val executor = service.promptExecutor
        val model = service.agentConfig.model

        val prompt = prompt("conversation-title") {
            system(application.resources.environmentSystemPrompt())
            system(
                application.getString(R.string.llm_prompt_generate_conversation_title)
                    .trimIndent().format(chatMessages),
            )
        }

        return executor.runPromptForSimpleResult(
            model = model,
            prompt = prompt,
            beforeStart = { logger.debug { "Generating conversation title with LLM..." } },
            ifEmpty = { logger.warn { "LLM generated empty title" } },
            onSuccess = { logger.debug { "Generated conversation title: $it" } },
            onFailure = { logger.error(it) { "Error generating conversation title" } },
        )
    }

    fun deleteConversation(
        conversation: ConversationEntity,
        onDeleted: (onUndo: () -> Unit) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = application.appDatabase.chatDao()
            dao.deleteConversation(conversation)
            onDeleted {
                viewModelScope.launch(Dispatchers.IO) {
                    dao.insertConversation(conversation)
                }
            }
        }
    }

    val providerConfigLazyListState = LLMProviderConfigLazyListState {
        llmConfigDao.getAllProviders()
    }
    val providers by llmConfigDao.getAllProvidersFlow()
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptyList(),
        )
        .collectAsState()

    suspend fun getModels(provider: LLMProviderConfigEntity) =
        llmConfigDao.getModelsByProviderOnce(provider.id)

    var selectedModelId by chatData.transform(
        transformIn = { selectedModelId },
        transformOut = { copy(selectedModelId = it) },
    )
    val selectedModelEntityFlow = chatDataStateFlow.map { it.selectedModelId }
        .distinctUntilChanged()
        .map { it?.let { id -> llmConfigDao.getModelById(id) } }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            null,
        )
    val selectedModelEntity by selectedModelEntityFlow.collectAsState()
    private val currentAgentService = selectedModelEntityFlow
        .map { model ->
            val client = model?.let { llmConfigDao.getProviderById(it.providerConfigId) }
                ?.toClient() ?: return@map null
            getChatAgentService(
                promptExecutor = SingleLLMPromptExecutor(client),
                model = model.model,
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            null,
        )

    private val cancellationEvent = Channel<Unit>(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    var isRunning by mutableStateOf(false)
        private set

    val chatMessageTextInputState = TextFieldState("")

    init {
        viewModelScope.launch {
            val initialDraft = chatDataStore.data.first().draftMessageText

            if (chatMessageTextInputState.text.isEmpty()) {
                chatMessageTextInputState.setTextAndPlaceCursorAtEnd(initialDraft)
            }

            snapshotFlow { chatMessageTextInputState.text.toString() }
                .drop(1)
                .distinctUntilChanged()
                .collect { text ->
                    chatDataStore.updateData { it.copy(draftMessageText = text) }
                }
        }
    }

    val remend = RemendProcessor(application.assets)

    init {
        addCloseable(remend)
        viewModelScope.launch {
            remend.initialize()
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
        viewModelScope.launch {
            runCodeEnabled = false
            runCodeMutex.lock()
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
            runCodeMutex.unlock()
            runCodeEnabled = true
        }
    }

    private val chatAgentToolRegistry = ToolRegistry {
        timeTool(application.resources, chatAgentStyle = true)
        scheduleTools(application.resources, scheduleDao)
        gatherToolsTool(application.resources) {
            scheduleToolsExtended(application.resources, scheduleDao) {
                codeRunnerTools[it.components] = it
            }
        }
    }

    val canSendMessage
        get() = !isRunning &&
                selectedModelEntity != null &&
                !messageListLoading &&
                chatMessageTextInputState.text.isNotEmpty()

    fun sendMessage() {
        if (!canSendMessage) return
        val parts = listOf(ContentPart.Text(chatMessageTextInputState.text.toString()))
        viewModelScope.launch {
            if (currentConversationId == null) {
                createNewConversation()
            }
            val conversationId = currentConversationId ?: return@launch
            application.appDatabase.withTransaction {
                var conversation =
                    chatDao.getConversationById(conversationId) ?: return@withTransaction null
                val eventFlow =
                    MutableSharedFlow<AssistantMessageStreamingEvent>(extraBufferCapacity = 10)
                val service = currentAgentService.filterNotNull().first()
                val databaseMessages = chatDao.getAllMessagesByConversationOnce(conversationId)
                val messages = databaseMessages.flatMap { entity ->
                    entity.message.parts.filterIsInstance<UiMessage.Koog>().map { it.message }
                }
                val system = if (messages.isEmpty()) {
                    Message.System(
                        content = application.resources.chatSystemPrompt,
                        metaInfo = RequestMetaInfo.create(Clock.System.toDeprecatedClock()),
                    )
                } else null
                val environmentMessage = Message.User(
                    content = application.getString(
                        R.string.llm_prompt_environment_context,
                        Clock.System.now().formatAgentTime(),
                        conversation.name,
                    ),
                    metaInfo = RequestMetaInfo.create(Clock.System.toDeprecatedClock()),
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
                chatDao.insertMessage(
                    message = MessageEntity(
                        conversationId = conversationId,
                        parts = listOf(
                            environmentMessage.toUiMessage(display = false),
                            Message.User(
                                parts = parts,
                                metaInfo = RequestMetaInfo.create(Clock.System.toDeprecatedClock()),
                            ).toUiMessage(),
                        ),
                        role = MessageEntityRole.User,
                        generatedBy = null,
                    ),
                    fileIds = emptyList(),
                )
                if (databaseMessages.isEmpty()) {
                    viewModelScope.launch {
                        val title = autoGenerateConversationName(conversation) ?: return@launch
                        val newConversation = conversation.copy(name = title)
                        chatDao.updateConversation(newConversation)
                        conversation = newConversation
                    }
                }
                val updateChannel = Channel<Unit>(Channel.CONFLATED)
                val state = AssistantMessageState.Streaming(
                    eventFlow = eventFlow,
                    model = service.agentConfig.model,
                    coroutineScope = viewModelScope,
                    conversationId = conversationId,
                    remend = remend,
                    onQueryConversationMeta = {
                        chatDao.getConversationById(conversationId)?.also {
                            conversation = it
                        }?.meta ?: ConversationMeta()
                    },
                    onUpdateConversationMeta = { newMeta ->
                        launch {
                            val newConversation = conversation.copy(meta = newMeta)
                            chatDao.updateConversation(newConversation)
                            conversation = newConversation
                        }
                    },
                    onUpdate = {
                        updateChannel.trySend(Unit)
                    },
                    onCompleted = {
                        viewModelScope.launch {
                            updateChannel.close()
                            while (updateChannel.receiveCatching().isSuccess) {
                                logger.trace { "Dropping update event before completing message." }
                            }
                            chatDao.updateMessage(it.toEntity())
                            logger.debug { "Message completed." }
                        }
                    },
                ).apply {
                    chatDao.insertMessageAndGet(
                        message = toEntity(),
                        fileIds = emptyList(),
                    ) {
                        it ?: return@insertMessageAndGet
                        parentId = it.message.parentId
                        depth = it.message.depth
                        streamingMessages[it.message.id] = AssistantStreamingMessage(
                            state = this@apply,
                            branchIndex = it.branchIndex,
                            branchCount = it.branchCount,
                        )
                    }
                }
                launch {
                    while (updateChannel.receiveCatching().isSuccess) {
                        val result = chatDao.updateMessage(state.toEntity())
                        if (result < 1) {
                            logger.warn { "Failed to update message with id ${state.id} on streaming update." }
                            cancellationEvent.send(Unit)
                        }
                    }
                }
                logger.debug { "Running agent..." }
                chatMessageTextInputState.setTextAndPlaceCursorAtEnd("")
                suspend {
                    runAgent(
                        state = state,
                        eventFlow = eventFlow,
                    ) {
                        service.run(
                            userParts = parts,
                            eventFlow = eventFlow,
                            state = state,
                            tools = chatAgentToolRegistry + ToolRegistry {
                                conversationTools(application.resources, conversation, chatDao)
                            },
                            buildPrompt = {
                                system(application.resources.environmentSystemPrompt())
                                system?.let { message(it) } ?: messages(messages)
                                message(environmentMessage)
                            },
                        )
                    }
                }
            }?.invoke()
        }
    }

    private suspend fun CoroutineScope.runAgent(
        state: AssistantMessageState.Streaming,
        eventFlow: MutableSharedFlow<AssistantMessageStreamingEvent>,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        val channel = Channel<suspend CoroutineScope.() -> Unit>(1)
        channel.send(block)

        for (block in channel) {
            try { // TODO: UI feedback
                coroutineScope {
                    isRunning = true
                    val agentJob = async {
                        block()
                        false
                    }

                    val cancellationReceiver = async {
                        cancellationEvent.receive()
                        logger.debug { "Cancellation signal received." }
                        true
                    }

                    select {
                        agentJob.onAwait { it }
                        cancellationReceiver.onAwait { it }
                    }.also {
                        agentJob.cancel()
                        cancellationReceiver.cancel()
                    }
                }.takeIf { it }?.let { logger.info { "Agent run cancelled." } }
            } catch (rerun: ChatAgentRerun) {
                logger.info { "Agent will rerun" }
                channel.send { rerun.run() }
                continue
            } catch (e: AIAgentMaxNumberOfIterationsReachedException) {
                logger.info { "Agent reached max number of iterations: ${e.message}" }

                val errorMessage =
                    application.getString(R.string.chat_message_error_max_iterations_exceeded)

                state.cleanUncompletedToolCalls { iterator, toolCall ->
                    iterator.add(
                        AssistantMessageContent.Completed(
                            message = Message.Tool.Result(
                                id = toolCall.id,
                                tool = toolCall.tool,
                                content = errorMessage,
                                metaInfo = RequestMetaInfo.create(Clock.System.toDeprecatedClock()),
                            ),
                            coroutineScope = viewModelScope,
                        ),
                    )
                }

                state.contents += AssistantMessageContent.Completed(
                    uiMessage = UiMessage.Error(errorMessage),
                    coroutineScope = viewModelScope,
                )
            } catch (e: Throwable) {
                logger.error(e) { "Agent run error" }

                val errorMessage = application.getString(R.string.chat_message_error)

                state.cleanUncompletedToolCalls { iterator, toolCall ->
                    iterator.add(
                        AssistantMessageContent.Completed(
                            message = Message.Tool.Result(
                                id = toolCall.id,
                                tool = toolCall.tool,
                                content = errorMessage,
                                metaInfo = RequestMetaInfo.create(Clock.System.toDeprecatedClock()),
                            ),
                            coroutineScope = viewModelScope,
                        ),
                    )
                }

                state.contents += AssistantMessageContent.Completed(
                    uiMessage = UiMessage.Error(errorMessage),
                    coroutineScope = viewModelScope,
                )
            }

            isRunning = false
            logger.debug { "Agent run completed." }
            eventFlow.emit(Finish)
            state.awaitCompletedState()
            cancel()
        }
    }

    fun cancelGeneration() {
        logger.debug { "UI requested to cancel generation." }
        cancellationEvent.trySend(Unit)
    }

    private suspend fun createNewConversation() {
        val conversation = ConversationEntity(
            name = application.getString(R.string.agent_conversation_label_new),
        )
        application.appDatabase.chatDao().insertConversation(conversation)
        currentConversationId = conversation.id
    }

    fun editProviderConfig(
        config: LLMProviderConfigEntity,
        onFinished: () -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            llmConfigDao.insertAtEndOrUpdateProvider(config)
            onFinished()
        }
    }

    fun deleteProviderConfig(
        config: LLMProviderConfigEntity,
        onDeleted: (onUndo: () -> Unit) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val models = llmConfigDao.getModelsByProviderOnce(config.id)
            llmConfigDao.deleteProvider(config)
            onDeleted {
                viewModelScope.launch(Dispatchers.IO) {
                    llmConfigDao.insertProviderAtBeginning(config)
                    for (model in models) {
                        llmConfigDao.insertModelAtEnd(model)
                    }
                }
            }
        }
    }

    fun editModelConfig(
        config: LLMConfigEntity,
        onFinished: () -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            llmConfigDao.insertAtEndOrUpdateModel(config)
            onFinished()
        }
    }

    fun deleteModelConfig(
        config: LLMConfigEntity,
        onDeleted: (onUndo: () -> Unit) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            llmConfigDao.deleteModel(config)
            onDeleted {
                viewModelScope.launch(Dispatchers.IO) {
                    llmConfigDao.insertModelAtBeginning(config)
                }
            }
        }
    }

    var defaultReasoningVisibility by chatData.transform(
        transformIn = { defaultReasoningVisibility },
        transformOut = {
            copy(defaultReasoningVisibility = it)
        },
    )

    var defaultToolVisibility by chatData.transform(
        transformIn = { defaultToolVisibility },
        transformOut = {
            copy(defaultToolVisibility = it)
        },
    )

    suspend fun generateErrorExplanation(message: String, onAppend: (String) -> Unit) {
        val service = currentAgentService.value ?: return
        val executor = service.promptExecutor
        val model = service.agentConfig.model

        val prompt = prompt("error-explanation") {
            system(application.resources.environmentSystemPrompt())
            system(
                application.getString(R.string.llm_prompt_generate_error_explanation)
                    .trimIndent().format(message.trim()),
            )
        }

        executor.executeStreaming(
            model = model,
            prompt = prompt,
        ).collect {
            if (it is Append) {
                onAppend(it.text)
            }
        }
    }
}

data class ConversationAndChatData(
    val conversationFlow: Flow<ConversationEntity?>,
    val messageCountFlow: Flow<Int>,
    val messagesState: PagingLazyListState<Int, MessageWithFilesAndBranchInfo>?,
)

val LocalAgentViewModel = staticCompositionLocalOf<AgentViewModel> {
    error("No AgentViewModel provided")
}
