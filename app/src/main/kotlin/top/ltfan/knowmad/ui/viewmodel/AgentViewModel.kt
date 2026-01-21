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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.toDeprecatedClock
import top.ltfan.knowmad.R
import top.ltfan.knowmad.agent.getChatAgent
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

    val chatDataStore = ChatData.createDataStore()
    val chatData = chatDataStore.asMutableState()

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

    private val currentConversationIdFlow = chatDataStore.dataFlowOf { it.conversation }
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
                conversation = id?.let {
                    chatDao.getConversationById(it)
                },
                messageCount = id?.let {
                    chatDao.getMessageCountInCurrentTreeByConversation(it)
                } ?: 0,
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
            initialValue = ConversationAndChatData(null, 0, null),
        )
        .collectAsState()

    val currentConversation get() = conversationAndChatData.conversation
    val messageCount get() = conversationAndChatData.messageCount
    val messagesState get() = conversationAndChatData.messagesState

    val streamingMessages = mutableStateMapOf<Uuid, AssistantStreamingMessage>()

    fun setMessageToCompleted(message: MessageEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            chatDao.updateMessage(message.copy(completed = true))
            streamingMessages.remove(message.id)
        }
    }

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
    val selectedModelEntityFlow = chatDataStore.dataFlowOf { it.selectedModelId }
        .map { it?.let { id -> llmConfigDao.getModelById(id) } }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            null,
        )
    val selectedModelEntity by selectedModelEntityFlow.collectAsState()
    private val currentAgent = selectedModelEntityFlow
        .combine(currentConversationIdFlow) { model, conversationId ->
            val client = model?.let {
                withContext(Dispatchers.IO) { llmConfigDao.getProviderById(it.providerConfigId) }
            }?.toClient() ?: return@combine null
            conversationId ?: return@combine null
            val messages = withContext(Dispatchers.IO) {
                chatDao.getAllMessagesByConversationOnce(conversationId)
            }.flatMap { entity ->
                entity.message.parts.filterIsInstance<UiMessage.Koog>().map { it.message }
            }
            getChatAgent(
                promptExecutor = SingleLLMPromptExecutor(client),
                model = model.model,
                newStreamingState = { eventFlow, cancelStreaming ->
                    AssistantMessageState.Streaming(
                        eventFlow = eventFlow,
                        model = model.model,
                        coroutineScope = viewModelScope,
                        conversationId = conversationId,
                        requestCancellation = {
                            viewModelScope.launch {
                                runCatching { cancelStreaming.send(Unit) }
                            }
                        },
                        onUpdate = {
                            viewModelScope.launch(Dispatchers.IO) {
                                chatDao.updateMessage(toEntity())
                            }
                        },
                        onCompleted = {
                            viewModelScope.launch(Dispatchers.IO) {
                                chatDao.updateMessage(it.toEntity())
                                streamingMessages.remove(it.id)
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
                },
                onStreamingCancelled = {
                    userMessageFlow.replayCache.firstOrNull()?.let { parts ->
                        chatMessageTextInputState.setTextAndPlaceCursorAtEnd(
                            parts.filterIsInstance<ContentPart.Text>()
                                .joinToString("\n") { it.text },
                        )
                    }
                    userMessageFlow.resetReplayCache()
                },
                getUserMessage = {
                    userMessageFlow.first().also {
                        withContext(Dispatchers.IO) {
                            chatDao.insertMessage(
                                message = MessageEntity(
                                    conversationId = conversationId,
                                    parts = listOf(
                                        Message.User(
                                            parts = it,
                                            metaInfo = RequestMetaInfo.create(Clock.System.toDeprecatedClock()),
                                        ).toUiMessage(),
                                    ),
                                    role = MessageEntityRole.User,
                                    generatedBy = null,
                                ),
                                fileIds = emptyList(),
                            )
                        }
                    }
                },
                resources = application.resources,
            ) { system ->
                if (messages.isEmpty()) {
                    system().also {
                        viewModelScope.launch(Dispatchers.IO) {
                            chatDao.insertMessage(
                                message = MessageEntity(
                                    conversationId = conversationId,
                                    parts = listOf(it.toUiMessage()),
                                    role = MessageEntityRole.System,
                                    generatedBy = model.model,
                                ),
                                fileIds = emptyList(),
                            )
                        }
                    }
                } else {
                    messages(messages)
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            null,
        )

    init {
        viewModelScope.launch {
            currentAgent.collectLatest {
                it ?: run {
                    logger.debug { "Current agent changed to null" }
                    return@collectLatest
                }
                logger.debug { "Current agent changed: ${it.id}" }
                try { // TODO: recreate agent on network error
                    it.run(Unit)
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }

    private val userMessageFlow = MutableSharedFlow<List<ContentPart>>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val canSendMessage
        get() = selectedModelEntity != null &&
                !messageListLoading &&
                chatMessageTextInputState.text.isNotEmpty()

    fun sendMessage() {
        if (!canSendMessage) return
        val parts = listOf(ContentPart.Text(chatMessageTextInputState.text.toString()))
        chatMessageTextInputState.setTextAndPlaceCursorAtEnd("")
        viewModelScope.launch {
            if (currentConversationId == null) {
                createNewConversation()
            }
            currentAgent.filterNotNull().first()
            userMessageFlow.emit(parts)
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
    val conversation: ConversationEntity?,
    val messageCount: Int,
    val messagesState: PagingLazyListState<Int, MessageWithFilesAndBranchInfo>?,
)

val LocalAgentViewModel = staticCompositionLocalOf<AgentViewModel> {
    error("No AgentViewModel provided")
}
