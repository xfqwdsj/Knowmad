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

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import top.ltfan.knowmad.activity.KnowmadActivity
import top.ltfan.knowmad.application.KnowmadApplication
import top.ltfan.knowmad.ui.page.BackStackRoute
import top.ltfan.knowmad.ui.page.Page
import top.ltfan.knowmad.ui.page.expanded
import top.ltfan.knowmad.ui.scene.OverlayContentSceneStrategy
import top.ltfan.knowmad.ui.theme.AppTheme
import top.ltfan.knowmad.ui.util.localSharedTransitionScope
import top.ltfan.knowmad.ui.viewmodel.AgentViewModel
import top.ltfan.knowmad.ui.viewmodel.AppViewModel
import top.ltfan.knowmad.ui.viewmodel.LocalAgentViewModel
import top.ltfan.knowmad.ui.viewmodel.LocalAppViewModel

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

        splashScreen.setKeepOnScreenCondition { !viewModel.appReady }

        setContent {
            AppTheme {
                val overlayContentStrategy = remember { OverlayContentSceneStrategy<Page>() }

                localSharedTransitionScope {
                    CompositionLocalProvider(
                        LocalAppViewModel provides viewModel,
                        LocalAgentViewModel provides agentViewModel,
                    ) {
                        if (viewModel.appReady) {
                            NavDisplay(
                                backStack = viewModel.backStack.expanded,
                                onBack = {
                                    val last = viewModel.backStack.lastOrNull() ?: return@NavDisplay
                                    if (last is BackStackRoute) {
                                        last.onBack()
                                    } else {
                                        viewModel.backStack.removeLastOrNull()
                                    }
                                },
                                entryDecorators = listOf(
                                    rememberSaveableStateHolderNavEntryDecorator(),
                                    rememberViewModelStoreNavEntryDecorator(),
                                ),
                                sceneStrategy = overlayContentStrategy,
                                sharedTransitionScope = this,
                                entryProvider = { it.navEntry() },
                            )
                        }
                    }
                }
            }
        }
    }
}
