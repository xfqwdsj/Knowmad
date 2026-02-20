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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
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
}
