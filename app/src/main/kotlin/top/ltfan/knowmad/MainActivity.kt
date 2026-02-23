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

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.util.Rational
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.ltfan.knowmad.activity.KnowmadActivity
import top.ltfan.knowmad.application.KnowmadApplication
import top.ltfan.knowmad.ui.AppContent
import top.ltfan.knowmad.ui.theme.AppTheme
import top.ltfan.knowmad.ui.viewmodel.AgentViewModel
import top.ltfan.knowmad.ui.viewmodel.AppViewModel
import top.ltfan.knowmad.ui.viewmodel.LocalAgentViewModel
import top.ltfan.knowmad.ui.viewmodel.LocalAppViewModel
import top.ltfan.knowmad.util.Logger

class MainActivity : KnowmadActivity() {
    private val viewModel: AppViewModel by viewModels {
        viewModelFactory {
            addInitializer(AppViewModel::class) {
                AppViewModel(application as KnowmadApplication)
            }
        }
    }

    private val agentViewModel: AgentViewModel by viewModels {
        viewModelFactory {
            addInitializer(AgentViewModel::class) {
                AgentViewModel(application as KnowmadApplication)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        intent.handle()
        ContextCompat.registerReceiver(
            this,
            pipReceiver,
            IntentFilter(ACTION_PIP),
            ContextCompat.RECEIVER_EXPORTED,
        )
        setPictureInPictureParams(pipParams)
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

    private val pipActions by lazy {
        listOf(
            RemoteAction(
                Icon.createWithResource(this, R.drawable.arrow_circle_up_24px),
                getString(R.string.companion_mode_label_scroll_up),
                getString(R.string.companion_mode_label_scroll_up_description),
                PendingIntent.getBroadcast(
                    this,
                    CODE_SCROLL_UP,
                    Intent(ACTION_PIP).apply {
                        putExtra(EXTRA_ACTION, CODE_SCROLL_UP)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            ),
            RemoteAction(
                Icon.createWithResource(this, R.drawable.capture_24px),
                getString(R.string.service_semantic_analysis_capture_label),
                getString(R.string.service_semantic_analysis_capture_description),
                PendingIntent.getBroadcast(
                    this,
                    CODE_CAPTURE_UI,
                    Intent(ACTION_PIP).apply {
                        putExtra(EXTRA_ACTION, CODE_CAPTURE_UI)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            ),
            RemoteAction(
                Icon.createWithResource(this, R.drawable.edit_square_24px),
                getString(R.string.agent_conversation_label_new),
                getString(R.string.agent_conversation_label_new),
                PendingIntent.getBroadcast(
                    this,
                    CODE_NEW_CONVERSATION,
                    Intent(ACTION_PIP).apply {
                        putExtra(EXTRA_ACTION, CODE_NEW_CONVERSATION)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            ),
        )
    }

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_PIP) return
            when (intent.extras?.getInt(EXTRA_ACTION)) {
                CODE_SCROLL_UP -> agentViewModel.pipScrollUp()
                CODE_CAPTURE_UI -> agentViewModel.pipCaptureUi()
                CODE_NEW_CONVERSATION -> agentViewModel.newConversation()
            }
        }
    }

    private val pipParams by lazy {
        PictureInPictureParams.Builder().apply {
            setAspectRatio(Rational(9, 14))
            setActions(pipActions)
        }.build()
    }

    companion object {
        private const val ACTION_PIP = "top.ltfan.knowmad.action.PIP"
        private const val EXTRA_ACTION = "top.ltfan.knowmad.extra.PIP_ACTION"
        private const val CODE_SCROLL_UP = 0
        private const val CODE_CAPTURE_UI = 1
        private const val CODE_NEW_CONVERSATION = 2
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(pipReceiver)
    }
}
