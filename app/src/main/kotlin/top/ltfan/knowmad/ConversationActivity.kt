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

package top.ltfan.knowmad

import android.app.ActivityManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import top.ltfan.knowmad.activity.KnowmadActivity
import top.ltfan.knowmad.application.KnowmadApplication
import top.ltfan.knowmad.data.chat.toConversationIdFromChatLink
import top.ltfan.knowmad.ui.component.AgentScreen
import top.ltfan.knowmad.ui.component.LocalAgentScreenIsNewWindow
import top.ltfan.knowmad.ui.component.LocalAgentScreenIsStandalone
import top.ltfan.knowmad.ui.page.AgentPage
import top.ltfan.knowmad.ui.theme.AppTheme
import top.ltfan.knowmad.ui.viewmodel.AgentViewModel
import top.ltfan.knowmad.ui.viewmodel.AppViewModel
import top.ltfan.knowmad.ui.viewmodel.LocalAgentViewModel
import top.ltfan.knowmad.ui.viewmodel.LocalAppViewModel
import top.ltfan.knowmad.util.Logger

private val logger = Logger("ConversationActivity")

class ConversationActivity : KnowmadActivity() {
    val viewModel: AppViewModel by viewModels {
        viewModelFactory {
            addInitializer(AppViewModel::class) {
                AppViewModel(
                    app = application as KnowmadApplication,
                    partial = true,
                )
            }
        }
    }

    val agentViewModel: AgentViewModel by viewModels {
        viewModelFactory {
            addInitializer(AgentViewModel::class) {
                AgentViewModel(
                    app = application as KnowmadApplication,
                    partial = true,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.handle()
        viewModel.initializeApp()
        setContent {
            AppTheme {
                CompositionLocalProvider(
                    LocalAppViewModel provides viewModel,
                    LocalAgentViewModel provides agentViewModel,
                    LocalAgentScreenIsStandalone provides true,
                    LocalAgentScreenIsNewWindow provides true,
                    content = ::AgentScreen,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        intent.handle()
        viewModel.initializeApp()
    }

    private fun Intent?.handle() {
        val isViewAction = this?.action == Intent.ACTION_VIEW
        val data = this?.data

        if (isViewAction && data != null) {
            val conversationId = data.toConversationIdFromChatLink()
            if (conversationId != null) {
                lifecycleScope.launch {
                    if (viewModel.backStack.lastOrNull() !is AgentPage) {
                        viewModel.backStack.add(AgentPage())
                    }
                    agentViewModel.currentConversationId = conversationId
                    viewModel.initializeApp()
                    val conversation = agentViewModel.chatDao.getConversationById(conversationId)
                    if (conversation == null) {
                        logger.error { "Conversation not found for ID: $conversationId" }
                        finish()
                        return@launch
                    }
                    val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ActivityManager.TaskDescription.Builder().apply {
                            setLabel(conversation.name)
                        }.build()
                    } else {
                        @Suppress("DEPRECATION")
                        ActivityManager.TaskDescription(conversation.name)
                    }
                    setTaskDescription(description)
                }
                return
            }
            return
        }
    }
}
