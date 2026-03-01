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

package top.ltfan.knowmad

import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.util.Rational
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.ltfan.knowmad.accessibility.semantic.SemanticAnalysisService
import top.ltfan.knowmad.activity.KnowmadActivity
import top.ltfan.knowmad.application.KnowmadApplication
import top.ltfan.knowmad.data.chat.toConversationIdFromChatLink
import top.ltfan.knowmad.ui.AppContent
import top.ltfan.knowmad.ui.component.PipAction
import top.ltfan.knowmad.ui.component.PipActions
import top.ltfan.knowmad.ui.component.PipActionsDelta
import top.ltfan.knowmad.ui.component.PipEvent
import top.ltfan.knowmad.ui.component.handlePipActions
import top.ltfan.knowmad.ui.page.AgentPage
import top.ltfan.knowmad.ui.theme.AppTheme
import top.ltfan.knowmad.ui.viewmodel.AgentViewModel
import top.ltfan.knowmad.ui.viewmodel.AppViewModel
import top.ltfan.knowmad.ui.viewmodel.LocalAgentViewModel
import top.ltfan.knowmad.ui.viewmodel.LocalAppViewModel
import top.ltfan.knowmad.util.Logger

class MainActivity : KnowmadActivity() {
    val appPartial by lazy { intent?.data?.toConversationIdFromChatLink() != null }

    val viewModel: AppViewModel by viewModels {
        viewModelFactory {
            addInitializer(AppViewModel::class) {
                AppViewModel(
                    app = application as KnowmadApplication,
                    partial = appPartial,
                )
            }
        }
    }

    val agentViewModel: AgentViewModel by viewModels {
        viewModelFactory {
            addInitializer(AgentViewModel::class) {
                AgentViewModel(
                    app = application as KnowmadApplication,
                    partial = appPartial,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            updatePipActions(PipActions.standard(agentViewModel))
            pipEventFlow.collect { event ->
                when (event) {
                    is SetActions -> lifecycleScope.launch { updatePipActions(event.delta) }
                }
            }
        }

        intent.handle()
        ContextCompat.registerReceiver(
            this,
            pipReceiver,
            IntentFilter(PipAction.ACTION),
            ContextCompat.RECEIVER_EXPORTED,
        )
        splashScreen.setKeepOnScreenCondition { !viewModel.appReady }

        setContent {
            AppTheme {
                CompositionLocalProvider(
                    LocalAppViewModel provides viewModel,
                    LocalAgentViewModel provides agentViewModel,
                    content = ::AppContent,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.handle()
    }

    private fun Intent?.handle() {
        val isViewAction = this?.action == Intent.ACTION_VIEW
        val data = this?.data
        val mimeType = this?.type

        if (isViewAction && data != null) {
            val conversationId = data.toConversationIdFromChatLink()
            if (conversationId != null) {
                lifecycleScope.launch {
                    if (!appPartial) {
                        snapshotFlow { viewModel.appReady }.filter { it }.first()
                    }
                    if (viewModel.backStack.lastOrNull() !is AgentPage) {
                        viewModel.backStack.add(AgentPage())
                    }
                    agentViewModel.currentConversationId = conversationId
                    if (appPartial) {
                        viewModel.appReady = true
                    }
                }
                return
            }
            val isIcsFile = mimeType == "text/calendar" ||
                    mimeType == "application/ics" ||
                    data.path?.endsWith(".ics", ignoreCase = true) == true
            if (isIcsFile) {
                data.handleIcsFile()
            }
        }
    }

    private fun Uri.handleIcsFile() {
        val logger = Logger("handleIcsFile")

        lifecycleScope.launch(Dispatchers.IO) {
            val content = runCatching {
                contentResolver.openInputStream(this@handleIcsFile)?.use {
                    it.bufferedReader().use { reader -> reader.readText() }
                }
            }.onFailure { logger.error(it) { "Failed to read ICS file content" } }
                .getOrNull()

            if (content != null) {
                viewModel.importFromICalendar(content)
            }
        }
    }

    private val pipBuilder = PictureInPictureParams.Builder().apply {
        setAspectRatio(Rational(9, 14))
    }

    private val pipActionsMutex = Mutex()
    private var currentPipActions = PipActions()
    private var pipActionsMap = mapOf<Int, () -> Unit>()

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            lifecycleScope.launch {
                pipActionsMutex.withLock {
                    intent.handlePipActions(pipActionsMap)
                }
            }
        }
    }

    suspend fun updatePipActions(delta: PipActionsDelta) {
        pipActionsMutex.withLock {
            currentPipActions = delta.applyTo(currentPipActions)
            val (actions, map) = currentPipActions.toActionsWithMap(this)
            pipActionsMap = map
            pipBuilder.setActions(actions)
            setPictureInPictureParams(pipBuilder.build())
        }
    }

    companion object {
        val pipEventFlow = MutableSharedFlow<PipEvent>()
    }

    override fun onStop() {
        super.onStop()
        SemanticAnalysisService.notifySuspend()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(pipReceiver)
    }
}
