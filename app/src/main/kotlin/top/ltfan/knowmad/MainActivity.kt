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

import android.app.ActivityManager
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
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
    val appPartial by lazy { intent?.getBooleanExtra(EXTRA_IS_PARTIAL, false) ?: false }

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

        if (!appPartial) {
            lifecycleScope.launch {
                updatePipActions(PipActions.standard(agentViewModel))
                pipEventFlow.collect { event ->
                    when (event) {
                        is SetActions -> lifecycleScope.launch { updatePipActions(event.delta) }
                    }
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
        this.intent = intent
        intent.handle()
    }

    private fun Intent?.handle() {
        val isViewAction = this?.action == Intent.ACTION_VIEW
        val data = this?.data

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
            val mimeType = type
            val isIcsFile = mimeType == "text/calendar" ||
                    mimeType == "application/ics" ||
                    data.path?.endsWith(".ics", ignoreCase = true) == true
            if (isIcsFile) {
                data.handleIcsFile()
            }
            return
        }

        val isPipAction = this?.action == ACTION_PIP
        if (isPipAction) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        }
    }

    private fun Uri.handleIcsFile() {
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
        const val ACTION_PIP = "ACTION_PIP"

        const val EXTRA_IS_PARTIAL = "IS_PARTIAL"

        private val logger = Logger("MainActivity")
        val pipEventFlow = MutableSharedFlow<PipEvent>()

        val Context.launchMainActivityInPipIntent
            inline get() = Intent(this, MainActivity::class.java).apply {
                action = ACTION_PIP
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
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
