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

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.ltfan.knowmad.application.KnowmadApplication
import top.ltfan.knowmad.data.llm.LLMConfigEntry
import top.ltfan.knowmad.ui.page.Route
import top.ltfan.knowmad.ui.page.WizardPage

class AppViewModel(app: KnowmadApplication) : AndroidViewModel<KnowmadApplication>(app) {
    val backStack = NavBackStack<Route>()

    fun onFinishWizard(entry: LLMConfigEntry, onFailed: (message: String) -> Unit) {
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
            // TODO
        }
    }

    fun onSkipWizard() {

    }

    init {
        backStack.add(WizardPage(::onFinishWizard, ::onSkipWizard))
    }
}

val LocalAppViewModel = staticCompositionLocalOf<AppViewModel> {
    error("No AppViewModel provided")
}
