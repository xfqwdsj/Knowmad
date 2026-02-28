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
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.intellij.markdown.ast.ASTNode
import top.ltfan.knowmad.MainActivity
import top.ltfan.knowmad.R
import top.ltfan.knowmad.accessibility.requestEnableAccessibilityService
import top.ltfan.knowmad.accessibility.semantic.SemanticAnalysisService
import top.ltfan.knowmad.agent.ModelService
import top.ltfan.knowmad.application.KnowmadApplication
import top.ltfan.knowmad.data.chat.ChatData
import top.ltfan.knowmad.data.chat.ChatListMessage
import top.ltfan.knowmad.data.chat.ConversationEntity
import top.ltfan.knowmad.data.chat.MessageWithFilesAndBranchInfo
import top.ltfan.knowmad.data.chat.UiMessage
import top.ltfan.knowmad.data.chat.allLoaded
import top.ltfan.knowmad.data.chat.toUiMessage
import top.ltfan.knowmad.data.llm.LLMConfigEntity
import top.ltfan.knowmad.data.llm.LLMProviderConfigEntity
import top.ltfan.knowmad.ui.component.AssistantMessageState
import top.ltfan.knowmad.ui.component.LLMProviderConfigLazyListState
import top.ltfan.knowmad.ui.component.PagingLazyListState
import top.ltfan.knowmad.ui.component.PipActions
import top.ltfan.knowmad.ui.component.PipActionsDelta
import top.ltfan.knowmad.ui.component.PipEvent
import top.ltfan.knowmad.ui.page.AgentMainPage
import top.ltfan.knowmad.ui.page.AgentSubPage
import top.ltfan.knowmad.util.Json
import top.ltfan.knowmad.util.Logger
import top.ltfan.knowmad.util.ServiceConnection
import top.ltfan.knowmad.util.ServiceConnectionStatus
import top.ltfan.knowmad.util.ServiceConnectionStatus.Closed
import top.ltfan.knowmad.util.SnapshotLruCache
import top.ltfan.knowmad.util.collectAsState
import top.ltfan.knowmad.util.transform
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class AgentViewModel(app: KnowmadApplication) : AndroidViewModel<KnowmadApplication>(app) {
    private val logger = Logger("AgentViewModel")

    val backStack = NavBackStack<AgentSubPage>(AgentMainPage())

    val chatDao = application.appDatabase.chatDao()
    val llmConfigDao = application.appDatabase.llmConfigDao()

    val chatDataStore = ChatData.createDataStore()
    val chatDataStateFlow = chatDataStore.dataStateFlow()
    val chatData = chatDataStore.asMutableState(chatDataStateFlow.value)

    val drawerState = DrawerState(DrawerValue.Closed)
    var savedMessagesFirstVisibleItemIndex by mutableIntStateOf(0)
    var savedMessagesFirstVisibleItemScrollOffset by mutableIntStateOf(0)
    val assistantMessageStates
        get() = modelService.value?.assistantMessageStates
            ?: SnapshotLruCache<Any, AssistantMessageState>(
                snapshotStateMap = mutableStateMapOf(),
                maxSize = 10,
            ).also {
                logger.warn { "ModelService not connected, using empty assistantMessageStates cache." }
            }

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
        transformOut = { copy(conversation = it) },
    )

    init {
        viewModelScope.launch {
            val id = currentConversationIdFlow.filterNotNull().first()
            chatDao.getConversationById(id) ?: run { currentConversationId = null }
        }
    }

    private val conversationAndChatData by currentConversationIdFlow
        .map { id ->
            messageListLoading = true
            try {
                ConversationAndChatData(
                    conversationFlow = id?.let {
                        chatDao.getConversationFlowById(it)
                    } ?: flowOf(null),
                    messageCountFlow = id?.let {
                        chatDao.getMessageCountFlowInCurrentTreeByConversation(it)
                    } ?: flowOf(0),
                    messagesFlow = id?.let { id ->
                        PagingLazyListState {
                            chatDao.getMessagesPagingByConversationReversed(id)
                        }.flow.map { data -> data.map { it.copy(message = it.message.allLoaded()) } }
                    },
                )
            } catch (e: Throwable) {
                logger.error(e) { "Error loading conversation and chat data for conversation id $id" }
                Empty
            } finally {
                messageListLoading = false
            }
        }
        .stateIn(
            viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = Empty,
        )
        .collectAsState()

    val currentConversationFlow get() = conversationAndChatData.conversationFlow
    val currentMessageCountFlow get() = conversationAndChatData.messageCountFlow
    val currentMessagesFlow get() = conversationAndChatData.messagesFlow

    val modelServiceConnection = application.ServiceConnection<ModelService>()
    val modelService = modelServiceConnection.status
        .onEach { status ->
            if (status is Closed) {
                modelServiceConnection.bind()
            }
        }
        .filterIsInstance<ServiceConnectionStatus.Connected<ModelService>>()
        .map { it.service }
        .stateIn(viewModelScope, Eagerly, null)

    fun getMessage(message: MessageWithFilesAndBranchInfo?): ChatListMessage? {
        if (message == null) return null
        val service = modelService.value ?: return null
        return service.getMessage(message)
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
        if (message !is Branched) return
        val service = modelService.value ?: return
        service.sendMessage(
            conversationId = message.conversationId,
            parts = emptyList(),
            includeEnvironmentContext = true,
            insertEnvironmentContext = false,
            insertAssistantMessageAndGet = { messageToInsert, fileIds, getUpdatedEntity ->
                insertSiblingMessageAndGet(
                    anchorMessageId = message.key,
                    message = messageToInsert,
                    fileIds = fileIds,
                    getUpdatedEntity = getUpdatedEntity,
                )
            },
        )
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

    suspend fun generateConversationName(conversationId: Uuid): String? {
        val service = modelService.value ?: return null
        val chatAgentService = service.chatAgentServiceFlow.value ?: return null
        return service.generateConversationName(
            conversationId = conversationId,
            executor = chatAgentService.promptExecutor,
            model = chatAgentService.agentConfig.model,
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

    val serviceCurrentTaskRunning: Boolean
        inline get() {
            val conversationId = currentConversationId ?: return false
            val service = modelService.value ?: return false
            return service.messageTasks[conversationId]?.value?.isNotEmpty() == true
        }

    val runCodeEnabled inline get() = modelService.value?.runCodeEnabled == true
    val runnableCodeComponents
        inline get() = modelService.value?.runnableCodeComponents ?: emptySet()

    fun runAssistantCode(
        state: AssistantMessageState,
        contentIndex: Int,
        node: ASTNode,
        components: List<String>,
        code: String,
    ) = modelService.value?.runAssistantCode(
        state = state,
        contentIndex = contentIndex,
        node = node,
        components = components,
        code = code,
    ) ?: Unit

    val canSendMessage
        get() = !serviceCurrentTaskRunning &&
                selectedModelEntity != null &&
                !messageListLoading

    val canSendMessageUi
        inline get() = canSendMessage &&
                chatMessageTextInputState.text.isNotEmpty()

    fun sendMessage(
        conversationId: Uuid? = currentConversationId,
        setCurrentConversationIfNew: Boolean = true,
        allowEmptyUserInput: Boolean = false,
        includeEnvironmentContext: Boolean = true,
        contextMessages: List<UiMessage>? = null,
        parts: List<ContentPart> = listOf(ContentPart.Text(chatMessageTextInputState.text.toString())),
        beforeStart: (() -> Unit)? = { chatMessageTextInputState.setTextAndPlaceCursorAtEnd("") },
        onEnd: ((isNewConversation: Boolean) -> Unit)? = null,
    ) {
        if (!allowEmptyUserInput && parts.all { it is ContentPart.Text && it.text.isEmpty() })
            return
        val service = modelService.value ?: return
        service.sendMessage(
            conversationId = conversationId,
            parts = parts,
            onNewConversation = setCurrentConversationIfNew.takeIf { it }?.let { _ ->
                { currentConversationId = it.id }
            },
            includeEnvironmentContext = includeEnvironmentContext,
            contextMessages = contextMessages,
            beforeStart = beforeStart,
            onEnd = onEnd,
        )
    }

    fun cancelGeneration() {
        logger.debug { "UI requested to cancel generation." }
        val conversationId = currentConversationId ?: return
        val service = modelService.value ?: return
        service.stopMessageTasks(conversationId)
    }

    val pipScrollEvents = Channel<Unit>(Channel.CONFLATED)

    private var pipWaitingJob: Job? = null
    private val pipStatusMutex = Mutex()
    var pipWaitingStatus by mutableStateOf<PipWaitingStatus?>(null)
        private set

    private val screenCapturingMutex = Mutex()
    var capturingScreen by mutableStateOf(false)
        private set

    enum class PipWaitingStatus {
        Click, Service
    }

    fun pipCaptureUi() = viewModelScope.launch {
        if (selectedModelId == null) {
            logger.debug { "No model selected, skipping pip capture." }
            return@launch
        }

        suspend fun clearWaitingStatus() {
            pipUpdateActions(PipActions.standard(this@AgentViewModel))
            if (pipStatusMutex.isLocked) {
                pipStatusMutex.unlock()
            }
            pipWaitingStatus = null
        }

        if (!SemanticAnalysisService.heartbeat()) {
            if (pipStatusMutex.tryLock()) {
                try {
                    pipUpdateActions(PipActions.grantPermission(this@AgentViewModel))
                    pipWaitingStatus = Click
                    pipWaitingJob = viewModelScope.launch {
                        delay(5.seconds)
                        clearWaitingStatus()
                    }
                } catch (e: Throwable) {
                    logger.error(e) { "Error while waiting for pip click" }
                    clearWaitingStatus()
                }
            } else if (pipWaitingStatus == Click) {
                pipWaitingJob?.cancel()
                application.requestEnableAccessibilityService<SemanticAnalysisService>()

                pipWaitingStatus = Service

                viewModelScope.launch {
                    try {
                        SemanticAnalysisService.waitAlive()
                    } catch (e: Throwable) {
                        logger.error(e) { "Error while waiting for SemanticAnalysisService to be alive" }
                    } finally {
                        clearWaitingStatus()
                    }
                }

                pipUpdateActions(PipActions.standard(this@AgentViewModel))
            } else if (pipWaitingStatus == Service) {
                clearWaitingStatus()
            }
            return@launch
        }

        clearWaitingStatus()
        if (!screenCapturingMutex.tryLock()) return@launch
        capturingScreen = true
        val node = try {
            SemanticAnalysisService.getUiTree().also {
                capturingScreen = false
            } ?: return@launch
        } finally {
            screenCapturingMutex.unlock()
        }
        val json = Json.encodeToString(node)
        sendMessage(
            allowEmptyUserInput = true,
            contextMessages = listOf(
                Message.User(
                    content = application.getString(R.string.llm_prompt_companion_mode_context),
                    metaInfo = RequestMetaInfo.create(Clock.System),
                ).toUiMessage(display = false),
                Message.User(
                    content = json,
                    metaInfo = RequestMetaInfo.create(Clock.System),
                ).toUiMessage(display = false),
            ),
            parts = listOf(),
            beforeStart = null,
            onEnd = { isNewConversation ->
                if (!isNewConversation) return@sendMessage
                val conversationId = currentConversationId ?: return@sendMessage
                viewModelScope.launch {
                    val newTitle = generateConversationName(conversationId) ?: return@launch
                    val conversation = chatDao.getConversationById(conversationId) ?: return@launch
                    chatDao.updateConversation(conversation.copy(name = newTitle))
                }
            },
        )
    }

    fun pipScroll() {
        if (selectedModelId == null) {
            logger.debug { "No model selected, skipping pip scroll up." }
            return
        }
        pipScrollEvents.trySend(Unit)
    }

    suspend fun pipUpdateActions(delta: PipActionsDelta) {
        MainActivity.pipEventFlow.emit(PipEvent.SetActions(delta))
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

    suspend inline fun generateErrorExplanation(
        errorMessage: String,
        crossinline onAppendExplanation: (String) -> Unit,
    ) {
        val service = modelService.value ?: return
        val chatAgentService = service.chatAgentServiceFlow.value ?: return

        service.generateErrorExplanation(
            errorMessage = errorMessage,
            executor = chatAgentService.promptExecutor,
            model = chatAgentService.agentConfig.model,
            onAppendExplanation = onAppendExplanation,
        )
    }
}

data class ConversationAndChatData(
    val conversationFlow: Flow<ConversationEntity?>,
    val messageCountFlow: Flow<Int>,
    val messagesFlow: Flow<PagingData<MessageWithFilesAndBranchInfo>>?,
) {
    companion object {
        val Empty = ConversationAndChatData(
            conversationFlow = flowOf(null),
            messageCountFlow = flowOf(0),
            messagesFlow = null,
        )
    }
}

val LocalAgentViewModel = staticCompositionLocalOf<AgentViewModel> {
    error("No AgentViewModel provided")
}
