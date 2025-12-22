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

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.llm.SupportedLLMProviders
import top.ltfan.knowmad.ui.page.WizardMessageItem
import top.ltfan.knowmad.ui.page.WizardSubPage
import top.ltfan.knowmad.util.CryptoManager

class WizardPageViewModel(
    firstPage: WizardSubPage,
    val onFinishWizard: () -> Unit,
    val onSkipWizard: () -> Unit,
) : ViewModel() {
    val backStack: NavBackStack<WizardSubPage> = NavBackStack(firstPage)

    var cryptoInitializationError by mutableStateOf(false)

    var isUsingPlaintext by mutableStateOf(false)

    fun initializeCrypto(
        setReady: (Boolean) -> Unit,
    ) {
        CryptoManager.LLMApiKey.generateKey()
        cryptoInitializationError = !CryptoManager.LLMApiKey.isKeyInitialized()
        setReady(!cryptoInitializationError)
        if (!cryptoInitializationError) {
            isUsingPlaintext = false
        }
    }

    private var _apiConfigurationError by mutableStateOf(false)
    var apiConfigurationError: Boolean
        get() = _apiConfigurationError
        set(value) {
            if (value && firstMessageGenerated) {
                return
            }
            _apiConfigurationError = value
        }

    val messageItems
        inline get() = listOf(
            WizardMessageItem(
                icon = Icons.Default.Error,
                message = R.string.crypto_key_initialization_error_message,
            ) to cryptoInitializationError,
            WizardMessageItem(
                icon = Icons.Default.Error,
                message = R.string.llm_message_invalid,
            ) to apiConfigurationError,
            WizardMessageItem(
                icon = Icons.Default.Warning,
                message = R.string.crypto_use_plaintext_warning,
            ) to isUsingPlaintext,
        )

    private var _selectedProvider by mutableStateOf<LLMProvider?>(null)
    var selectedProvider: LLMProvider?
        get() = _selectedProvider
        set(value) {
            if (_selectedProvider != value) {
                _selectedProvider = value
                baseUrl = currentProviderInfo?.defaultBaseUrl ?: ""
                apiKey = ""
                model = ""
                apiConfigurationError = false
            }
        }

    val currentProviderInfo inline get() = SupportedLLMProviders[selectedProvider]

    private var _baseUrl by mutableStateOf("")
    var baseUrl: String
        get() = _baseUrl
        set(value) {
            if (_baseUrl != value) {
                _baseUrl = value
                apiConfigurationError = false
                resetFirstMessage()
            }
        }

    val apiKeyTextFieldState = TextFieldState()
    var apiKey: String
        inline get() = apiKeyTextFieldState.text.toString()
        inline set(value) {
            apiKeyTextFieldState.setTextAndPlaceCursorAtEnd(value)
        }

    val knownModelsMap = mutableStateMapOf<String, LLModel>()
    val knownModelIds inline get() = knownModelsMap.keys.toList()
    val knownModels inline get() = knownModelsMap.values.toList()

    suspend fun fetchKnownModels() {
        val provider = selectedProvider
        val client = client
        val info = SupportedLLMProviders[provider]
        if (provider == null || client == null || provider != client.llmProvider() || info == null) {
            return
        }

        val apiModelIds = withContext(Dispatchers.IO) {
            try {
                client.models().also {
                    apiConfigurationError = false
                }
            } catch (e: Throwable) {
                apiConfigurationError = true
                e.printStackTrace()
                emptyList()
            }
        }

        knownModelsMap.clear()
        knownModelsMap.putAll(
            apiModelIds.associateWith {
                LLModel(
                    provider = provider,
                    id = it,
                    capabilities = listOf(),
                    contextLength = 0,
                )
            },
        )
        knownModelsMap.putAll(info.predefinedModels)
    }

    val modelTextFieldState = TextFieldState()
    var model: String
        inline get() = modelTextFieldState.text.toString()
        inline set(value) {
            modelTextFieldState.setTextAndPlaceCursorAtEnd(value)
        }

    private var _selectedModel by mutableStateOf<LLModel?>(null)
    var selectedModel: LLModel?
        get() = _selectedModel
        set(value) {
            if (_selectedModel != value) {
                _selectedModel = value
                resetFirstMessage()
            }
        }

    var client: LLMClient? = null

    val firstMessageFlow = MutableStateFlow("")
    var firstMessageGenerationStarted by mutableStateOf(false)
    var firstMessageGenerated by mutableStateOf(false)

    fun resetFirstMessage() {
        firstMessageFlow.value = ""
        firstMessageGenerationStarted = false
        firstMessageGenerated = false
    }

    suspend fun generateFirstMessage(prompt: String) {
        val client = client ?: return
        val model = selectedModel ?: return
        if (firstMessageGenerationStarted) {
            return
        }
        firstMessageGenerationStarted = true

        withContext(Dispatchers.IO) {
            try {
                val response = client.executeStreaming(
                    prompt = prompt("first-message") {
                        val datetime =
                            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                                .format(LocalDateTime.Formats.ISO)
                        system(prompt.format(datetime))
                    },
                    model = model,
                )
                firstMessageGenerated = false
                firstMessageFlow.value = ""
                response.collect {
                    when (it) {
                        is StreamFrame.Append -> {
                            firstMessageFlow.value += it.text
                        }

                        is StreamFrame.End -> {
                            firstMessageGenerated = true
                        }

                        is StreamFrame.ToolCall -> {}
                    }
                    apiConfigurationError = false
                }
                firstMessageGenerated = true
                apiConfigurationError = false
            } catch (e: Throwable) {
                apiConfigurationError = true
                e.printStackTrace()
            } finally {
                firstMessageGenerationStarted = false
            }
        }
    }

    fun finishWizard() {
        // TODO: Save the configuration
        onFinishWizard()
    }

    fun skipWizard() {
        // TODO: Handle skipping the wizard
        onSkipWizard()
    }

    init {
        viewModelScope.launch {
            snapshotFlow { apiKeyTextFieldState.text }
                .collect {
                    apiConfigurationError = false
                    resetFirstMessage()
                }
        }

        viewModelScope.launch {
            snapshotFlow { modelTextFieldState.text }
                .collect { text ->
                    val modelId = text.toString()
                    if (modelId.isEmpty()) {
                        selectedModel = null
                        return@collect
                    }
                    val currentContextLength = selectedModel?.contextLength ?: 0
                    val currentMaxOutputTokens = selectedModel?.maxOutputTokens
                    val model = knownModelsMap[modelId]
                    selectedModel =
                        model ?: selectedProvider?.let { provider ->
                            LLModel(
                                provider = provider,
                                id = modelId,
                                capabilities = listOf(),
                                contextLength = currentContextLength,
                                maxOutputTokens = currentMaxOutputTokens,
                            )
                        }
                }
        }
    }
}
