/*
 * Knowmad - Knowledge nomad
 * Copyright (C) 2025 LTFan (aka xfqwdsj)
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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import top.ltfan.knowmad.application.KnowmadApplication
import top.ltfan.knowmad.data.llm.LLMConfigEntry
import top.ltfan.knowmad.data.wizard.FirstJoinedData
import top.ltfan.knowmad.data.wizard.WizardState
import top.ltfan.knowmad.ui.page.MainPage
import top.ltfan.knowmad.ui.page.Route
import top.ltfan.knowmad.ui.page.WizardPage
import top.ltfan.knowmad.util.transform

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
                val dao = application.llmDatabase.dao()
                val providerConfig = entry.getProviderConfig()
                val providerId = dao.insertProvider(providerConfig)
                val modelConfig = entry.getModelConfig(providerId)
                dao.insertModel(modelConfig)
            } catch (e: Throwable) {
                e.printStackTrace()
                onFailed(e.localizedMessage ?: "Unknown error")
                return@launch
            }
            this@AppViewModel.firstJoinedData = firstJoinedData
            navigateToMainPage()
            backStack.removeIf { it is WizardPage }
        }
    }

    fun onSkipWizard() {
        firstJoinedData = FirstJoinedData(instant = Clock.System.now())
        navigateToMainPage()
        backStack.removeIf { it is WizardPage }
    }

    fun navigateToMainPage() {
        backStack.add(MainPage())
    }

    init {
        viewModelScope.launch {
            if (wizardStateStore.data.first().data == null) {
                backStack.add(WizardPage(::onFinishWizard, ::onSkipWizard))
            } else {
                navigateToMainPage()
            }
            appReady = true
        }
    }
}

val LocalAppViewModel = staticCompositionLocalOf<AppViewModel> {
    error("No AppViewModel provided")
}
