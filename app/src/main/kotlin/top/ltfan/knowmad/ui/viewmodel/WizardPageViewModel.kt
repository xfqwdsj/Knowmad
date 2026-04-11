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

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import android.content.res.Resources
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavBackStack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import top.ltfan.knowmad.R
import top.ltfan.knowmad.agent.systemPrompt
import top.ltfan.knowmad.agent.tool.formatAgentTime
import top.ltfan.knowmad.application.KnowmadApplication
import top.ltfan.knowmad.data.database.AppDatabase.Companion.appDatabase
import top.ltfan.knowmad.data.llm.LLMConfigEntry
import top.ltfan.knowmad.data.llm.SupportedLLMProviders
import top.ltfan.knowmad.data.transform
import top.ltfan.knowmad.data.wizard.FirstJoinedData
import top.ltfan.knowmad.data.wizard.WizardData
import top.ltfan.knowmad.ui.component.SavedMarkdownState
import top.ltfan.knowmad.ui.page.WizardMessageItem
import top.ltfan.knowmad.ui.page.WizardSubPage
import top.ltfan.knowmad.ui.util.SnackbarAction
import top.ltfan.knowmad.util.CryptoData
import top.ltfan.knowmad.util.CryptoManager
import top.ltfan.knowmad.util.Logger
import top.ltfan.knowmad.util.RemendProcessor
import top.ltfan.knowmad.util.asResource
import top.ltfan.knowmad.util.asStringRes
import kotlin.time.Clock
import kotlin.time.Instant

private val logger = Logger("WizardPageViewModel")

class WizardPageViewModel(
    app: KnowmadApplication,
    firstPage: WizardSubPage,
    val onFinishWizard: (
        entry: LLMConfigEntry,
        firstJoinedData: FirstJoinedData,
        onFailed: (message: String) -> Unit,
    ) -> Unit,
    val onSkipWizard: () -> Unit,
) : AndroidViewModel<KnowmadApplication>(app) {
    val backStack: NavBackStack<WizardSubPage> = NavBackStack(firstPage)

    private val wizardDataStore = WizardData.createDataStore()
    private val wizardDataStateFlow = wizardDataStore.dataStateFlow()
    private val wizardData = wizardDataStore.asMutableState(wizardDataStateFlow.value)

    private val database = application.appDatabase
    private val llmConfigDao = database.llmConfigDao()

    fun generateCryptoKey(
        setReady: (Boolean) -> Unit,
    ) {
        CryptoManager.LLMApiKey.generateKey()
        cryptoInitializationError = !CryptoManager.LLMApiKey.isKeyInitialized()
        setReady(!cryptoInitializationError)
        if (!cryptoInitializationError) {
            isUsingPlaintext = false
        }
    }

    init {
        CryptoManager.LLMApiKey.generateKey()
    }

    var cryptoInitializationError by mutableStateOf(!CryptoManager.LLMApiKey.isKeyInitialized())
    var isUsingPlaintext by mutableStateOf(false)

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
                icon = R.drawable.error_24px,
                message = R.string.crypto_key_initialization_error_message,
            ) to cryptoInitializationError,
            WizardMessageItem(
                icon = R.drawable.error_24px,
                message = R.string.llm_message_invalid,
            ) to apiConfigurationError,
            WizardMessageItem(
                icon = R.drawable.warning_24px,
                message = R.string.crypto_use_plaintext_warning,
            ) to isUsingPlaintext,
        )

    private var _selectedProvider by wizardData.transform(
        transformIn = { provider },
        transformOut = { copy(provider = it) },
    )
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

    private var _baseUrl by wizardData.transform(
        transformIn = { baseUrl },
        transformOut = { copy(baseUrl = it) },
    )
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

    var knownModelsMap by mutableStateOf(emptyMap<String, LLModel>())
    val knownModelIds inline get() = knownModelsMap.keys.toList()
    val knownModels inline get() = knownModelsMap.values.toList()

    suspend fun fetchKnownModels() {
        val provider = selectedProvider
        val client = client
        val info = currentProviderInfo
        if (provider == null || client == null || provider != client.llmProvider() || info == null) {
            return
        }

        val predefined = info.predefinedModels.models.associateBy { it.id }
        knownModelsMap = predefined

        val apiModels = try {
            client.models().associateBy { it.id }.also {
                apiConfigurationError = false
            }
        } catch (e: Throwable) {
            apiConfigurationError = true
            logger.error(e) { "Failed to fetch models from API" }
            predefined
        }

        knownModelsMap = apiModels
    }

    val modelTextFieldState = TextFieldState()
    var model: String
        inline get() = modelTextFieldState.text.toString()
        inline set(value) {
            modelTextFieldState.setTextAndPlaceCursorAtEnd(value)
        }

    private var _selectedModel by wizardData.transform(
        transformIn = { model },
        transformOut = { copy(model = it) },
    )
    var selectedModel: LLModel?
        get() = _selectedModel
        set(value) {
            if (_selectedModel != value) {
                _selectedModel = value
                resetFirstMessage()
            }
        }

    init {
        viewModelScope.launch {
            val savedModel = wizardDataStore.data.first().model
            if (savedModel != null) {
                selectedModel = savedModel
                model = savedModel.id
            }
        }
    }

    var client: LLMClient? = null
        set(value) {
            if (field != value) {
                field?.close()
                field = value
            }
        }

    private val remend = RemendProcessor(application.assets)

    init {
        addCloseable(remend)
        viewModelScope.launch {
            remend.initialize()
        }
    }

    var firstJoinedTime: Instant? = null
    val firstMessageFlow = MutableStateFlow("")
    val firstMessageState = SavedMarkdownState(firstMessageFlow.map { remend.process(it) })
    var firstMessageGenerationStarted by mutableStateOf(false)
    var firstMessageGenerated by mutableStateOf(false)

    fun resetFirstMessage() {
        firstMessageFlow.value = ""
        firstMessageGenerationStarted = false
        firstMessageGenerated = false
    }

    suspend fun generateFirstMessage(resources: Resources) {
        val client = client ?: return
        val model = selectedModel ?: return
        if (firstMessageGenerationStarted) {
            return
        }
        firstMessageGenerationStarted = true

        try {
            val instant = Clock.System.now()
            firstJoinedTime = instant
            val datetime = instant.formatAgentTime()
            val prompt = resources.systemPrompt(
                taskId = R.string.llm_prompt_setup_wizard_finish,
                taskFormatArgs = arrayOf(datetime),
            )
            val response = client.executeStreaming(
                prompt = prompt("first-message") {
                    system(prompt)
                },
                model = model,
            )
            firstMessageGenerated = false
            firstMessageFlow.value = ""
            response.collect {
                when (it) {
                    is TextDelta -> firstMessageFlow.value += it.text
                    is End -> firstMessageGenerated = true

                    else -> {}
                }
                apiConfigurationError = false
            }
            firstMessageGenerated = true
            apiConfigurationError = false
        } catch (e: Throwable) {
            apiConfigurationError = true
            logger.warn(e) { "Failed to generate first message" }
        } finally {
            firstMessageGenerationStarted = false
        }
    }

    var isWizardFinished by mutableStateOf(false)

    fun finishWizard() {
        isWizardFinished = true

        val (apiKeyBytes, iv) = CryptoManager.LLMApiKey.encryptOrPlain(apiKey).also {
            isUsingPlaintext = it is CryptoData.Plain
        }

        val provider = selectedProvider ?: return
        val model = selectedModel ?: return

        val instant = firstJoinedTime ?: return
        val message = firstMessageFlow.value

        val entry = LLMConfigEntry(
            provider = provider,
            apiKey = apiKeyBytes,
            iv = iv,
            baseUrl = baseUrl.ifBlank { null },
            model = model,
        )

        val firstJoinedData = FirstJoinedData(instant, message)

        onFinishWizard(entry, firstJoinedData, ::onFinishWizardFailed)
    }

    fun onFinishWizardFailed(message: String) {
        isWizardFinished = false
        viewModelScope.launch {
            GlobalViewModel.showSnackbar(message.asResource())
        }
    }

    fun skipWizard() {
        var isSkipped = false
        viewModelScope.launch {
            GlobalViewModel.showSnackbar(
                message = R.string.setup_wizard_skip_message_confirmation.asStringRes(),
                action = SnackbarAction(android.R.string.ok.asStringRes()) {
                    if (isSkipped) return@SnackbarAction
                    isSkipped = true
                    onSkipWizard()
                },
                withDismissAction = true,
                duration = SnackbarDuration.Short,
            )
        }
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
                    if (modelId == selectedModel?.id) {
                        return@collect
                    }
                    if (modelId.isBlank()) {
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

        viewModelScope.launch {
            val modelCount = llmConfigDao.getTotalModelCount()
            if (modelCount >= 1) {
                GlobalViewModel.showSnackbar(
                    message = R.string.setup_wizard_skip_message_suggestion_has_model.asStringRes(),
                    action = SnackbarAction(R.string.label_skip.asStringRes(), onSkipWizard),
                    withDismissAction = true,
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        client?.close()
    }
}
