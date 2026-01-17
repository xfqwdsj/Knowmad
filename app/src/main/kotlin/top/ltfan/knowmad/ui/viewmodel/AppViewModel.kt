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

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.ltfan.knowmad.application.KnowmadApplication
import top.ltfan.knowmad.data.llm.LLMConfigEntry
import top.ltfan.knowmad.data.wizard.FirstJoinedData
import top.ltfan.knowmad.data.wizard.WizardState
import top.ltfan.knowmad.ui.page.AgentPage
import top.ltfan.knowmad.ui.page.MainPage
import top.ltfan.knowmad.ui.page.Route
import top.ltfan.knowmad.ui.page.WizardPage
import top.ltfan.knowmad.util.transform
import kotlin.time.Clock

class AppViewModel(app: KnowmadApplication) : AndroidViewModel<KnowmadApplication>(app) {
    val backStack = NavBackStack<Route>()
    var appReady by mutableStateOf(false)

    private val wizardStateStore = WizardState.createDataStore()
    private val wizardState = wizardStateStore.asMutableState()
    var firstJoinedData by wizardState.transform(
        transformIn = { data },
        transformOut = { copy(data = it) },
    )

    fun onFinishWizard(
        entry: LLMConfigEntry,
        firstJoinedData: FirstJoinedData,
        onFailed: (message: String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dao = application.appDatabase.llmConfigDao()
                val providerConfig = entry.getProviderConfig()
                dao.insertProviderAtEnd(providerConfig)
                val providerId = providerConfig.id
                val modelConfig = entry.getModelConfig(providerId)
                dao.insertModelAtEnd(modelConfig)
            } catch (e: Throwable) {
                e.printStackTrace()
                onFailed(e.localizedMessage ?: "Unknown error")
                return@launch
            }
            this@AppViewModel.firstJoinedData = firstJoinedData
            navigateToMainPage()
            backStack.removeAll { it is WizardPage }
        }
    }

    fun onSkipWizard() {
        firstJoinedData = FirstJoinedData(instant = Clock.System.now())
        navigateToMainPage()
        backStack.removeAll { it is WizardPage }
    }

    fun navigateToMainPage() {
        backStack.add(MainPage())
    }

    val standaloneAgentScreenIndex inline get() = backStack.indexOfLast { it is AgentPage }

    fun switchStandaloneAgentScreen() {
        val index = standaloneAgentScreenIndex
        if (index != -1) {
            backStack.removeAt(index)
        } else {
            backStack.add(AgentPage())
        }
    }

    init {
        viewModelScope.launch {
            if (wizardStateStore.data.first().data == null) {
                backStack.add(WizardPage())
            } else {
                navigateToMainPage()
            }
            appReady = true
        }
    }

    val snackbarHostState = SnackbarHostState()

    init {
        val resources = application.resources
        viewModelScope.launch {
            GlobalViewModel.snackbarEvent.filterNotNull().collect { event ->
                val message = event.message.get(resources)
                val actionLabel = event.action?.label?.get(resources)
                launch {
                    snackbarHostState.showSnackbar(
                        message,
                        actionLabel,
                        event.withDismissAction,
                        event.duration,
                    ).let {
                        when (it) {
                            SnackbarResult.ActionPerformed -> {
                                event.action?.onClick?.invoke()
                            }

                            SnackbarResult.Dismissed -> {
                                event.onDismissed?.invoke()
                            }
                        }
                    }
                }
            }
        }
    }
}

val LocalAppViewModel = staticCompositionLocalOf<AppViewModel> {
    error("No AppViewModel provided")
}
