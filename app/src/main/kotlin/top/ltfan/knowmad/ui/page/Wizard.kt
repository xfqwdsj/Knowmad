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

package top.ltfan.knowmad.ui.page

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NoEncryption
import androidx.compose.material.icons.filled.Token
import androidx.compose.material.icons.filled.Upcoming
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.llm.SupportedLLMProviders
import top.ltfan.knowmad.ui.component.AutoSuggestTextField
import top.ltfan.knowmad.ui.component.LLMProviderItem
import top.ltfan.knowmad.ui.component.ModelCapabilitiesFlow
import top.ltfan.knowmad.ui.component.StepItem
import top.ltfan.knowmad.ui.component.Stepper
import top.ltfan.knowmad.ui.theme.ProvideCompatibleShapes
import top.ltfan.knowmad.ui.util.AppWindowInsets
import top.ltfan.knowmad.ui.util.ListItemMaxWidth
import top.ltfan.knowmad.ui.util.TextFieldMaxWidth
import top.ltfan.knowmad.ui.util.only
import top.ltfan.knowmad.ui.util.plus
import top.ltfan.knowmad.ui.viewmodel.AppViewModel
import top.ltfan.knowmad.util.CryptoManager

@Serializable
class WizardPage(
    val backStack: NavBackStack<WizardSubPage> = NavBackStack(WelcomePage),
) : Page() {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    context(contentPadding: PaddingValues)
    override fun AppViewModel.Content() {
        val backStack = this@WizardPage.backStack
        val currentPage = backStack.last()

        val insets = AppWindowInsets + contentPadding
        val horizontalPadding = insets.only { horizontal }.asPaddingValues()

        Surface {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(insets.only { vertical }),
            ) {
                Spacer(Modifier.height(16.dp))
                Stepper(
                    steps = steps,
                    currentStep = backStack.size - 1,
                    contentPadding = horizontalPadding,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontalPadding)
                        .padding(horizontal = 8.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Column {
                        AnimatedContent(
                            targetState = messageItems,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                        ) { messageItems ->
                            val hasMessages = messageItems.any { it.second }
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 24.dp).run {
                                    if (hasMessages) padding(vertical = 8.dp)
                                    else this
                                },
                                verticalArrangement = if (hasMessages) Arrangement.spacedBy(8.dp) else Arrangement.Top,
                            ) {
                                for ((item, show) in messageItems) {
                                    if (show) {
                                        Message(
                                            imageVector = item.icon,
                                            message = stringResource(item.message),
                                        )
                                    }
                                }
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        ) {
                            NavDisplay(
                                backStack = backStack,
                                modifier = Modifier.fillMaxSize(),
                                transitionSpec = {
                                    fadeIn() + slideInHorizontally { it } togetherWith
                                            fadeOut() + slideOutHorizontally { -it }
                                },
                                popTransitionSpec = {
                                    fadeIn() + slideInHorizontally { -it } togetherWith
                                            fadeOut() + slideOutHorizontally { it }
                                },
                                predictivePopTransitionSpec = {
                                    fadeIn() + slideInHorizontally { -it } togetherWith
                                            fadeOut() + slideOutHorizontally { it }
                                },
                                entryProvider = @Suppress("UNCHECKED_CAST") {
                                    it.navEntry() as NavEntry<WizardSubPage>
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontalPadding),
                ) {
                    ProvideCompatibleShapes {
                        Spacer(Modifier.width(16.dp))
                        TextButton(
                            onClick = {},
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(R.string.label_skip_for_now))
                        }
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(
                            onClick = {
                                backStack.removeLastOrNull()
                            },
                            shapes = ButtonDefaults.shapes(),
                            enabled = backStack.size > 1,
                        ) {
                            Text(stringResource(R.string.label_back))
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                currentPage.nextPage?.invoke(this@WizardPage)
                            },
                            shapes = ButtonDefaults.shapes(),
                            enabled = currentPage.canContinue,
                        ) {
                            Text(stringResource(R.string.label_next))
                        }
                        Spacer(Modifier.width(16.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    @Composable
    fun Message(imageVector: ImageVector, message: String) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    var cryptoInitializationError by mutableStateOf(false)
    var isUsingPlaintext by mutableStateOf(false)
    var apiConfigurationError by mutableStateOf(false)

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

    var selectedProvider by mutableStateOf<LLMProvider?>(null)
    val currentProviderInfo inline get() = SupportedLLMProviders[selectedProvider]

    var baseUrl by mutableStateOf("")
    var apiKey by mutableStateOf("")

    var selectedModel by mutableStateOf<LLModel?>(null)

    var client: LLMClient? = null
    var firstMessage by mutableStateOf("")

    val steps
        @Composable inline get() = listOf(
            StepItem(stringResource(R.string.setup_wizard_welcome_label)),
            StepItem(stringResource(R.string.setup_wizard_provider_label)),
            StepItem(stringResource(R.string.setup_wizard_api_label)),
            StepItem(stringResource(R.string.setup_wizard_model_label)),
            StepItem(stringResource(R.string.setup_wizard_advanced_label)),
            StepItem(stringResource(R.string.setup_wizard_finish_label)),
        )
}

@Immutable
data class WizardMessageItem(
    val icon: ImageVector,
    @param:StringRes val message: Int,
)

@Serializable
private data object WelcomePage : WizardSubPage() {
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    context(contentPadding: PaddingValues)
    override fun AppViewModel.Content() {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding + PaddingValues(16.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StaticTitle(
                icon = {
                    TitleIcon(Icons.Default.AutoAwesome)
                },
                title = R.string.setup_wizard_welcome_title,
                message = R.string.setup_wizard_welcome_message,
            )
        }
    }

    override val canContinue = true
    override val nextPage: (wizardPage: WizardPage) -> Unit = { wizardPage ->
        wizardPage.backStack.add(ProviderPage(wizardPage))
    }
}

@Serializable
private class ProviderPage(val wizardPage: WizardPage) : WizardSubPage() {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    context(contentPadding: PaddingValues)
    override fun AppViewModel.Content() {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding + PaddingValues(16.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StaticTitle(
                icon = {
                    TitleIcon(Icons.Default.Upcoming)
                },
                title = R.string.setup_wizard_provider_title,
                message = R.string.setup_wizard_provider_message,
            )
            TitleContentSpacer()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for ((provider, info) in SupportedLLMProviders) {
                    LLMProviderItem(
                        info = info,
                        modifier = Modifier
                            .widthIn(max = ListItemMaxWidth)
                            .fillMaxWidth(),
                        selected = wizardPage.selectedProvider == provider,
                    ) {
                        if (wizardPage.selectedProvider == provider) {
                            return@LLMProviderItem
                        }
                        wizardPage.selectedProvider = provider
                        wizardPage.baseUrl = info.defaultBaseUrl
                        wizardPage.apiKey = ""
                        wizardPage.selectedModel = null
                        wizardPage.apiConfigurationError = false
                    }
                }
            }
        }
    }

    override val canContinue get() = wizardPage.selectedProvider != null
    override val nextPage: (wizardPage: WizardPage) -> Unit = { wizardPage ->
        wizardPage.backStack.add(ApiSetupPage(wizardPage))
    }
}

@Serializable
private class ApiSetupPage(val wizardPage: WizardPage) : WizardSubPage() {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
    @Composable
    context(contentPadding: PaddingValues)
    override fun AppViewModel.Content() {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding + PaddingValues(16.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedBackgroundIcon(
                shape1 = MaterialShapes.Cookie12Sided,
                color1 = MaterialTheme.colorScheme.errorContainer,
                rotation1 = 135f,
                icon1 = { TitleIcon(Icons.Default.Error) },
                shape2 = MaterialShapes.Cookie9Sided,
                color2 = MaterialTheme.colorScheme.primaryContainer,
                rotation2 = 0f,
                icon2 = { TitleIcon(Icons.Default.Key) },
                displayShape2 = isReady,
            )
            IconTitleSpacer()
            AnimatedContent(
                targetState = isReady,
                transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
            ) { isInitialized ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!isInitialized) {
                        ErrorElements()
                        return@AnimatedContent
                    }
                    TitleText(
                        title = R.string.setup_wizard_api_title,
                        message = R.string.setup_wizard_api_message,
                    )
                    TitleContentSpacer()
                    SecureTextField(
                        state = apiKeyTextFieldState,
                        modifier = Modifier
                            .widthIn(max = TextFieldMaxWidth)
                            .fillMaxWidth()
                            .onFocusChanged {
                                if (!it.isFocused) {
                                    wizardPage.apiKey = apiKey
                                }
                            },
                        label = {
                            Text(stringResource(R.string.llm_api_key_label))
                        },
                        leadingIcon = {
                            Icon(
                                if (wizardPage.isUsingPlaintext) Icons.Default.NoEncryption else Icons.Default.EnhancedEncryption,
                                contentDescription = null,
                            )
                        },
                        trailingIcon = {
                            Row {
                                val coroutineScope = rememberCoroutineScope()
                                val clipboard = LocalClipboard.current
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                        TooltipAnchorPosition.Above,
                                    ),
                                    tooltip = {
                                        PlainTooltip { Text(stringResource(R.string.label_paste)) }
                                    },
                                    state = rememberTooltipState(),
                                ) {
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                val data = clipboard.getClipEntry()?.clipData
                                                if (data?.itemCount == 0) {
                                                    return@launch
                                                }
                                                data?.getItemAt(0)?.text?.toString()?.let {
                                                    apiKeyTextFieldState
                                                        .setTextAndPlaceCursorAtEnd(it)
                                                }
                                            }
                                        },
                                    ) {
                                        Icon(
                                            Icons.Default.ContentPaste,
                                            contentDescription = stringResource(R.string.label_paste),
                                        )
                                    }
                                }
                                wizardPage.currentProviderInfo?.let { providerInfo ->
                                    Spacer(Modifier.width(4.dp))
                                    TooltipBox(
                                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                            TooltipAnchorPosition.Above,
                                        ),
                                        tooltip = {
                                            PlainTooltip { Text(stringResource(R.string.llm_api_key_guidance_get)) }
                                        },
                                        state = rememberTooltipState(),
                                    ) {
                                        val uriHandler = LocalUriHandler.current
                                        IconButton(
                                            onClick = {
                                                uriHandler.openUri(providerInfo.platformUrl)
                                            },
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Default.OpenInNew,
                                                contentDescription = stringResource(R.string.llm_api_key_guidance_get),
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        supportingText = {
                            Row {
                                AnimatedContent(
                                    targetState = wizardPage.isUsingPlaintext,
                                    modifier = Modifier.weight(1f),
                                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                                ) { isUsingPlaintext ->
                                    Text(
                                        stringResource(
                                            if (isUsingPlaintext) R.string.llm_api_key_message_unsecure
                                            else R.string.llm_api_key_message_secure,
                                        ),
                                    )
                                }
                                AnimatedVisibility(
                                    visible = wizardPage.isUsingPlaintext,
                                    enter = fadeIn() + expandHorizontally(
                                        expandFrom = Alignment.Start,
                                        clip = false,
                                    ),
                                    exit = fadeOut() + shrinkHorizontally(
                                        shrinkTowards = Alignment.Start,
                                        clip = false,
                                    ),
                                ) {
                                    Row {
                                        Spacer(Modifier.width(8.dp))
                                        TextButton(
                                            onClick = ::initializeCrypto,
                                        ) {
                                            Text(stringResource(R.string.crypto_key_initialization_error_retry_label))
                                        }
                                    }
                                }
                            }
                        },
                    )
                    Divider()
                    TextField(
                        value = wizardPage.baseUrl,
                        onValueChange = { wizardPage.baseUrl = it },
                        modifier = Modifier
                            .widthIn(max = TextFieldMaxWidth)
                            .fillMaxWidth(),
                        label = {
                            Text(stringResource(R.string.llm_api_base_url_input_label))
                        },
                        placeholder = wizardPage.currentProviderInfo?.let { providerInfo ->
                            {
                                Text(providerInfo.defaultBaseUrl)
                            }
                        },
                        supportingText = {
                            Text(stringResource(R.string.llm_api_base_url_input_message))
                        },
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun ErrorElements() {
        TitleText(
            title = R.string.crypto_key_initialization_error_title,
            message = R.string.crypto_key_initialization_error_message,
        )
        TitleContentSpacer()
        Button(
            onClick = ::initializeCrypto,
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
        ) {
            Text(stringResource(R.string.crypto_key_initialization_error_retry_label))
        }
        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = {
                wizardPage.isUsingPlaintext = true
                isReady = true
            },
        ) {
            Text(stringResource(R.string.crypto_use_plaintext_label))
        }
    }

    var isReady by mutableStateOf(!wizardPage.cryptoInitializationError)

    @Transient
    val apiKeyTextFieldState = TextFieldState(wizardPage.apiKey)
    val apiKey inline get() = apiKeyTextFieldState.text.toString()

    init {
        initializeCrypto()
    }

    fun initializeCrypto() {
        CryptoManager.LLMApiKey.generateKey()
        wizardPage.cryptoInitializationError = !CryptoManager.LLMApiKey.isKeyInitialized()
        isReady = !wizardPage.cryptoInitializationError
        if (!wizardPage.cryptoInitializationError) {
            wizardPage.isUsingPlaintext = false
        }
    }

    override val canContinue get() = apiKey.isNotEmpty()
    override val nextPage: (wizardPage: WizardPage) -> Unit = { wizardPage ->
        wizardPage.apiKey = apiKey
        if (wizardPage.baseUrl.isEmpty()) {
            wizardPage.currentProviderInfo?.let { providerInfo ->
                wizardPage.baseUrl = providerInfo.defaultBaseUrl
            }
        }
        wizardPage.backStack.add(ModelSetupPage(wizardPage))
    }
}

@Serializable
private class ModelSetupPage(val wizardPage: WizardPage) : WizardSubPage() {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
    @Composable
    context(contentPadding: PaddingValues)
    override fun AppViewModel.Content() {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding + PaddingValues(16.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StaticTitle(
                icon = {
                    TitleIcon(Icons.Default.Token)
                },
                title = R.string.setup_wizard_model_title,
                message = R.string.setup_wizard_model_message,
            )
            TitleContentSpacer()
            var modelMenuExpanded by remember { mutableStateOf(false) }
            AutoSuggestTextField(
                state = modelTextFieldState,
                options = knownModelIds,
                allowExpand = modelMenuExpanded,
                onExpandedChange = { modelMenuExpanded = it },
            ) {
                TextField(
                    state = modelTextFieldState,
                    modifier = Modifier
                        .widthIn(max = TextFieldMaxWidth)
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                    label = {
                        Text(stringResource(R.string.llm_model_id_label))
                    },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
            }
            Spacer(Modifier.height(16.dp))
            TextField(
                value = wizardPage.selectedModel?.contextLength?.toString()
                    .takeIf { it != "0" } ?: "",
                onValueChange = {
                    wizardPage.selectedModel = wizardPage.selectedModel?.copy(
                        contextLength = it.toLongOrNull() ?: 0,
                    )
                },
                modifier = Modifier
                    .widthIn(max = TextFieldMaxWidth)
                    .fillMaxWidth(),
                label = {
                    Text(stringResource(R.string.llm_context_length_label))
                },
                placeholder = { Text("0") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                ),
            )
            Spacer(Modifier.height(16.dp))
            TextField(
                value = wizardPage.selectedModel?.maxOutputTokens?.toString() ?: "",
                onValueChange = {
                    wizardPage.selectedModel = wizardPage.selectedModel?.copy(
                        maxOutputTokens = it.toLongOrNull(),
                    )
                },
                modifier = Modifier
                    .widthIn(max = TextFieldMaxWidth)
                    .fillMaxWidth(),
                label = {
                    Text(stringResource(R.string.llm_max_output_tokens_label))
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                ),
                singleLine = true,
            )
            AnimatedContent(
                targetState = wizardPage.selectedModel == null,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    fadeIn() togetherWith fadeOut() using SizeTransform(clip = false)
                },
                contentAlignment = Alignment.TopCenter,
            ) { modelNotSelected ->
                if (modelNotSelected) {
                    return@AnimatedContent
                }

                val model = wizardPage.selectedModel
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Divider {
                        Row(Modifier.fillMaxWidth()) {
                            Spacer(Modifier.width(16.dp))
                            Text(stringResource(R.string.llm_capability_label))
                            Spacer(Modifier.weight(1f))
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                    TooltipAnchorPosition.Above,
                                ),
                                tooltip = {
                                    PlainTooltip {
                                        Text(stringResource(R.string.llm_capability_guidance_query))
                                    }
                                },
                                state = rememberTooltipState(),
                            ) {
                                val uriHandler = LocalUriHandler.current
                                IconButton(
                                    onClick = {
                                        if (model == null) {
                                            return@IconButton
                                        }
                                        wizardPage.currentProviderInfo?.let { providerInfo ->
                                            uriHandler.openUri(
                                                providerInfo.getModelCapabilitiesUrl(model.id),
                                            )
                                        }
                                    },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Default.OpenInNew,
                                        contentDescription = stringResource(R.string.llm_capability_guidance_query),
                                    )
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                        }
                    }
                    ModelCapabilitiesFlow(
                        capabilities = model?.capabilities ?: emptyList(),
                        onAdd = {
                            if (model == null) {
                                return@ModelCapabilitiesFlow
                            }
                            wizardPage.selectedModel =
                                model.copy(capabilities = model.capabilities + it)
                        },
                        onRemove = {
                            if (model == null) {
                                return@ModelCapabilitiesFlow
                            }
                            wizardPage.selectedModel =
                                model.copy(capabilities = model.capabilities - it)
                        },
                        enabled = model != null,
                    )
                }
            }
        }

        LaunchedEffect(wizardPage.selectedProvider) {
            wizardPage.selectedProvider?.let { provider ->
                withContext(Dispatchers.IO) {
                    try {
                        wizardPage.client?.models()?.forEach { id ->
                            if (id !in knownModelIds) {
                                knownModels.add(LLModel(provider, id, listOf(), 0))
                            }
                        }
                        wizardPage.apiConfigurationError = false
                    } catch (e: Throwable) {
                        wizardPage.apiConfigurationError = true
                        e.printStackTrace()
                    }
                }
            }
        }

        var isFirstTimeLaunchedByModelId by remember { mutableStateOf(true) }
        LaunchedEffect(modelId) {
            if (isFirstTimeLaunchedByModelId) {
                isFirstTimeLaunchedByModelId = false
                return@LaunchedEffect
            }

            if (modelId.isEmpty()) {
                wizardPage.selectedModel = null
                return@LaunchedEffect
            }
            val model = knownModels.find { it.id == modelId }
            wizardPage.selectedModel = model ?: wizardPage.selectedProvider?.let { provider ->
                LLModel(provider, modelId, listOf(), contextLength, maxOutputTokens)
            }
        }
    }

    @Transient
    val knownModels = mutableStateListOf<LLModel>()
    val knownModelIds inline get() = knownModels.map { it.id }

    @Transient
    val modelTextFieldState = TextFieldState(wizardPage.selectedModel?.id ?: "")
    val modelId inline get() = modelTextFieldState.text.toString()

    val contextLength inline get() = wizardPage.selectedModel?.contextLength ?: 0

    val maxOutputTokens inline get() = wizardPage.selectedModel?.maxOutputTokens

    init {
        wizardPage.client = wizardPage.currentProviderInfo?.convertToClient(
            wizardPage.apiKey,
            wizardPage.baseUrl,
        )
        wizardPage.currentProviderInfo?.predefinedModels?.let { models ->
            knownModels.addAll(models)
        }
    }

    override val canContinue get() = modelId.isNotEmpty()
    override val nextPage: (wizardPage: WizardPage) -> Unit = { wizardPage ->
        wizardPage.backStack.add(AdvancedSettingsPage(wizardPage))
    }
}

@Serializable
private class AdvancedSettingsPage(val wizardPage: WizardPage) : WizardSubPage() {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    context(contentPadding: PaddingValues)
    override fun AppViewModel.Content() {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding + PaddingValues(16.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StaticTitle(
                icon = {
                    TitleIcon(Icons.Default.Code)
                },
                title = R.string.setup_wizard_advanced_title,
                message = R.string.setup_wizard_advanced_message,
            )
        }
    }

    override val canContinue = true
    override val nextPage: (wizardPage: WizardPage) -> Unit = { wizardPage ->
        wizardPage.firstMessage = ""
        wizardPage.backStack.add(FinishPage(wizardPage))
    }
}

@Serializable
private data class FinishPage(val wizardPage: WizardPage) : WizardSubPage() {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
    @Composable
    context(contentPadding: PaddingValues)
    override fun AppViewModel.Content() {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding + PaddingValues(16.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedBackgroundIcon(
                shape1 = MaterialShapes.Cookie9Sided,
                color1 = MaterialTheme.colorScheme.primaryContainer,
                rotation1 = 0f,
                icon1 = { TitleIcon(Icons.Default.Celebration) },
                shape2 = MaterialShapes.Cookie12Sided,
                color2 = MaterialTheme.colorScheme.errorContainer,
                rotation2 = 135f,
                icon2 = { TitleIcon(Icons.Default.Error) },
                displayShape2 = wizardPage.apiConfigurationError,
            )
            IconTitleSpacer()
            AnimatedContent(
                targetState = wizardPage.apiConfigurationError,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentAlignment = Alignment.Center,
            ) { apiConfigurationError ->
                TitleText(
                    title =
                        if (!apiConfigurationError) R.string.setup_wizard_finish_title
                        else R.string.label_need_attention,
                    message =
                        if (!apiConfigurationError) R.string.setup_wizard_finish_message
                        else R.string.llm_message_invalid,
                )
            }
            AnimatedVisibility(
                visible = wizardPage.firstMessage.isNotEmpty(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(
                            R.string.setup_wizard_finish_message_llm,
                            wizardPage.firstMessage,
                        ),
                    )
                }
            }
            TitleContentSpacer()
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above,
                ),
                tooltip = {
                    PlainTooltip {
                        Text(stringResource(R.string.label_finish))
                    }
                },
                state = rememberTooltipState(),
            ) {
                ProvideCompatibleShapes {
                    val size = ButtonDefaults.MediumContainerHeight
                    Button(
                        onClick = ::finish,
                        shapes = ButtonDefaults.shapesFor(size),
                        contentPadding = ButtonDefaults.contentPaddingFor(size),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowForward,
                            contentDescription = stringResource(R.string.label_finish),
                            modifier = Modifier.size(ButtonDefaults.iconSizeFor(size)),
                        )
                    }
                }
            }
        }

        val prompt = stringResource(R.string.setup_wizard_finish_llm_prompt)
        LaunchedEffect(Unit) {
            val client = wizardPage.client ?: return@LaunchedEffect
            val model = wizardPage.selectedModel ?: return@LaunchedEffect
            withContext(Dispatchers.IO) {
                try {
                    val response = client.executeStreaming(
                        prompt = prompt("first-message") {
                            system(prompt)
                        },
                        model = model,
                    )
                    response.collect {
                        when (it) {
                            is StreamFrame.Append -> {
                                wizardPage.firstMessage += it.text
                            }

                            is StreamFrame.End -> {}
                            is StreamFrame.ToolCall -> {}
                        }
                        wizardPage.apiConfigurationError = false
                    }
                    wizardPage.apiConfigurationError = false
                } catch (e: Throwable) {
                    wizardPage.apiConfigurationError = true
                    e.printStackTrace()
                }
            }
        }
    }

    fun finish() {

    }

    override val canContinue = true
    override val nextPage: (wizardPage: WizardPage) -> Unit = { finish() }
}

@Serializable
sealed class WizardSubPage : SubPage() {
    abstract val canContinue: Boolean
    abstract val nextPage: ((wizardPage: WizardPage) -> Unit)?
}

@Composable
private fun StaticTitle(
    icon: @Composable () -> Unit,
    @StringRes title: Int,
    @StringRes message: Int,
) {
    StaticBackgroundIcon(icon)
    IconTitleSpacer()
    TitleText(title, message)
}

@Composable
private fun IconTitleSpacer() {
    Spacer(Modifier.height(32.dp))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StaticBackgroundIcon(
    icon: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialShapes.Cookie9Sided.toShape(),
        color = MaterialTheme.colorScheme.primaryContainer,
        content = icon,
    )
}

@Composable
private fun AnimatedBackgroundIcon(
    shape1: RoundedPolygon,
    color1: Color,
    rotation1: Float,
    icon1: @Composable () -> Unit,
    shape2: RoundedPolygon,
    color2: Color,
    rotation2: Float,
    icon2: @Composable () -> Unit,
    displayShape2: Boolean,
) {
    val matrix = remember { Matrix() }
    val morph = remember { Morph(shape1, shape2) }
    val progress by animateFloatAsState(
        if (displayShape2) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    )
    val color by animateColorAsState(
        if (displayShape2) color2 else color1,
    )
    val rotation by animateFloatAsState(
        if (displayShape2) rotation2 else rotation1,
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
    )

    Box(
        Modifier.drawWithCache {
            matrix.reset()
            matrix.translate(size.width / 2f, size.height / 2f)
            matrix.rotateZ(rotation)
            matrix.scale(size.width, size.height)
            matrix.translate(-0.5f, -0.5f)

            val path = morph.toPath(progress = progress).asComposePath()
            path.transform(matrix)

            onDrawBehind {
                drawPath(path, color)
            }
        },
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColorFor(color),
        ) {
            AnimatedContent(
                targetState = displayShape2,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentAlignment = Alignment.Center,
            ) { displayShape2 ->
                if (displayShape2) {
                    icon2()
                } else {
                    icon1()
                }
            }
        }
    }
}

@Composable
private fun TitleText(
    @StringRes title: Int,
    @StringRes message: Int,
) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TitleIcon(
    imageVector: ImageVector,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        modifier = Modifier
            .padding(16.dp)
            .size(48.dp),
    )
}

@Composable
private fun TitleContentSpacer() {
    Spacer(Modifier.height(36.dp))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Divider(
    label: @Composable (() -> Unit)? = null,
) {
    Column(
        Modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth(),
    ) {
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        if (label != null) {
            Spacer(Modifier.height(8.dp))
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.labelLargeEmphasized) {
                label()
            }
            Spacer(Modifier.height(16.dp))
        } else {
            Spacer(Modifier.height(32.dp))
        }
    }
}
