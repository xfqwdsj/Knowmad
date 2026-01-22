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
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
import kotlinx.coroutines.withContext
import kotlinx.datetime.toDeprecatedClock
import top.ltfan.knowmad.R
import top.ltfan.knowmad.agent.chatSystemPrompt
import top.ltfan.knowmad.agent.defaultTools
import top.ltfan.knowmad.agent.getChatAgentService
import top.ltfan.knowmad.agent.run
import top.ltfan.knowmad.agent.tool.scheduleTools
import top.ltfan.knowmad.application.KnowmadApplication
import top.ltfan.knowmad.data.chat.AssistantStreamingMessage
import top.ltfan.knowmad.data.chat.ChatData
import top.ltfan.knowmad.data.chat.ChatListMessage
import top.ltfan.knowmad.data.chat.ConversationEntity
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
import top.ltfan.knowmad.ui.component.LLMProviderConfigLazyListState
import top.ltfan.knowmad.ui.component.PagingLazyListState
import top.ltfan.knowmad.ui.page.AgentMainPage
import top.ltfan.knowmad.ui.page.AgentSubPage
import top.ltfan.knowmad.util.Logger
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
    val chatData = chatDataStore.asMutableState(chatDataStateFlow)

    val drawerState = DrawerState(DrawerValue.Closed)

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

    private val conversationAndChatData by currentConversationIdFlow
        .map { id ->
            ConversationAndChatData(
                conversationFlow = id?.let {
                    chatDao.getConversationFlowById(it)
                } ?: run {
                    currentConversationId = null
                    flowOf(null)
                },
                messageCountFlow = id?.let {
                    chatDao.getMessageCountFlowInCurrentTreeByConversation(it)
                } ?: flowOf(0),
                messagesState = id?.let {
                    PagingLazyListState {
                        chatDao.getAllMessagesByConversation(it)
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

    fun setMessageToCompleted(message: MessageEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            chatDao.updateMessage(message.copy(completed = true))
            streamingMessages.remove(message.id)
        }
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
        onFinished: () -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            application.appDatabase.chatDao().updateConversation(conversation)
            onFinished()
        }
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
            val client = model?.let {
                withContext(Dispatchers.IO) { llmConfigDao.getProviderById(it.providerConfigId) }
            }?.toClient() ?: return@map null
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
            val eventFlow =
                MutableSharedFlow<AssistantMessageStreamingEvent>(extraBufferCapacity = 10)
            val service = currentAgentService.filterNotNull().first()
            val messages = withContext(Dispatchers.IO) {
                chatDao.getAllMessagesByConversationOnce(conversationId)
            }.flatMap { entity ->
                entity.message.parts.filterIsInstance<UiMessage.Koog>().map { it.message }
            }
            val system = if (messages.isEmpty()) {
                Message.System(
                    content = application.resources.chatSystemPrompt,
                    metaInfo = RequestMetaInfo.create(Clock.System.toDeprecatedClock()),
                )
            } else null
            withContext(Dispatchers.IO) {
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
            }
            val state = AssistantMessageState.Streaming(
                eventFlow = eventFlow,
                model = service.agentConfig.model,
                coroutineScope = viewModelScope,
                conversationId = conversationId,
                onUpdate = {
                    viewModelScope.launch(Dispatchers.IO) {
                        chatDao.updateMessage(toEntity())
                    }
                },
                onCompleted = {
                    viewModelScope.launch(Dispatchers.IO) {
                        saveCompletedMessage(it)
                    }
                },
            ).apply {
                withContext(Dispatchers.IO) {
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
            }
            try { // TODO: UI feedback
                coroutineScope {
                    logger.debug { "Running agent..." }
                    isRunning = true
                    chatMessageTextInputState.setTextAndPlaceCursorAtEnd("")
                    val agentJob = async {
                        service.run(
                            userParts = parts,
                            eventFlow = eventFlow,
                            state = state,
                            tools = {
                                defaultTools(application.resources)
                                scheduleTools(application.resources, scheduleDao)
                            },
                            buildPrompt = { system?.let { message(it) } ?: messages(messages) },
                        )
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
            } catch (e: AIAgentMaxNumberOfIterationsReachedException) {
                logger.info { "Agent reached max number of iterations: ${e.message}" }
            } catch (e: Throwable) {
                logger.error(e) { "Agent run error" }
            } finally {
                isRunning = false
                logger.debug { "Agent run completed." }
                eventFlow.emit(Finish)
                state.awaitCompletedState()
            }
        }
    }

    fun cancelGeneration() {
        logger.debug { "UI requested to cancel generation." }
        cancellationEvent.trySend(Unit)
    }

    private suspend fun saveCompletedMessage(state: AssistantMessageState) {
        withContext(Dispatchers.IO) {
            chatDao.updateMessage(state.toEntity())
            streamingMessages.remove(state.id)
        }
    }

    private suspend fun createNewConversation() {
        val conversation = ConversationEntity(
            name = application.getString(R.string.agent_conversation_label_new),
        )
        withContext(Dispatchers.IO) {
            application.appDatabase.chatDao().insertConversation(conversation)
        }
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
}

data class ConversationAndChatData(
    val conversationFlow: Flow<ConversationEntity?>,
    val messageCountFlow: Flow<Int>,
    val messagesState: PagingLazyListState<Int, MessageWithFilesAndBranchInfo>?,
)

val LocalAgentViewModel = staticCompositionLocalOf<AgentViewModel> {
    error("No AgentViewModel provided")
}
