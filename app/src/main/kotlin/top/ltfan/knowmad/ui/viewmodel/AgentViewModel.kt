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

import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.ltfan.knowmad.application.KnowmadApplication
import top.ltfan.knowmad.data.chat.ChatData
import top.ltfan.knowmad.data.chat.ConversationEntity
import top.ltfan.knowmad.ui.component.LLMProviderConfigLazyListState
import top.ltfan.knowmad.ui.component.PagingLazyListState
import top.ltfan.knowmad.ui.page.AgentMainPage
import top.ltfan.knowmad.ui.page.AgentSubPage
import top.ltfan.knowmad.util.collectAsState
import top.ltfan.knowmad.util.transform

class AgentViewModel(app: KnowmadApplication) : AndroidViewModel<KnowmadApplication>(app) {
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

    var currentConversationId by chatData.transform(
        transformIn = { conversation },
        transformOut = { copy(conversation = it) },
    )
    val currentConversation by snapshotFlow { currentConversationId }
        .flatMapLatest { id ->
            id?.let { chatDao.getConversationById(it) } ?: flowOf(null)
        }
        .stateIn(
            viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )
        .collectAsState()

    fun newConversation() {
        currentConversationId = null
//        viewModelScope.launch(Dispatchers.IO) {
//            val conversation = ConversationEntity(
//                name = application.getString(R.string.agent_conversation_label_new),
//            )
//            application.appDatabase.chatDao().insertConversation(conversation)
//            currentConversationId = conversation.id
//        }
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
}

val LocalAgentViewModel = staticCompositionLocalOf<AgentViewModel> {
    error("No AgentViewModel provided")
}
