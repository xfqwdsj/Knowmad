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

package top.ltfan.knowmad.ui.page

import ai.koog.prompt.llm.LLMProvider
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.llm.LLMConfigEntry
import top.ltfan.knowmad.data.llm.SupportedLLMProviders
import top.ltfan.knowmad.data.wizard.FirstJoinedData
import top.ltfan.knowmad.ui.component.CopyIconButton
import top.ltfan.knowmad.ui.component.LLMContextLengthTextField
import top.ltfan.knowmad.ui.component.LLMMaxOutputTokensTextField
import top.ltfan.knowmad.ui.component.LLMProviderApiKeyTextField
import top.ltfan.knowmad.ui.component.LLMProviderBaseUrlTextField
import top.ltfan.knowmad.ui.component.LLMProviderInfo
import top.ltfan.knowmad.ui.component.LLModelTextField
import top.ltfan.knowmad.ui.component.MarkdownView
import top.ltfan.knowmad.ui.component.ModelCapabilitiesFlow
import top.ltfan.knowmad.ui.component.OpenUriIconButton
import top.ltfan.knowmad.ui.component.RetryIconButton
import top.ltfan.knowmad.ui.component.SnackbarHost
import top.ltfan.knowmad.ui.component.StepItem
import top.ltfan.knowmad.ui.component.Stepper
import top.ltfan.knowmad.ui.theme.ContentContainerColor
import top.ltfan.knowmad.ui.theme.ContentContainerPadding
import top.ltfan.knowmad.ui.theme.ContentContainerShape
import top.ltfan.knowmad.ui.theme.ListItemMaxWidth
import top.ltfan.knowmad.ui.theme.ProvideCompatibleShapes
import top.ltfan.knowmad.ui.util.AppWindowInsets
import top.ltfan.knowmad.ui.util.WindowInsetsToPaddingValuesBox
import top.ltfan.knowmad.ui.util.localSharedTransitionScope
import top.ltfan.knowmad.ui.util.only
import top.ltfan.knowmad.ui.util.plus
import top.ltfan.knowmad.ui.viewmodel.LocalAppViewModel
import top.ltfan.knowmad.ui.viewmodel.WizardPageViewModel

@Serializable
class WizardPage(
    val onFinishWizard: (
        entry: LLMConfigEntry,
        firstJoinedData: FirstJoinedData,
        onFailed: (message: String) -> Unit,
    ) -> Unit,
    val onSkipWizard: () -> Unit,
) : Page() {
    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        val appViewModel = LocalAppViewModel.current
        val viewModel = viewModel<WizardPageViewModel> {
            WizardPageViewModel(
                appViewModel.application,
                WizardWelcomePage(),
                onFinishWizard,
                onSkipWizard,
            )
        }

        val backStack = viewModel.backStack
        val currentPage = backStack.last()

        val insets = AppWindowInsets + contentPadding

        val hasMessages = viewModel.messageItems.any { it.second }
        val messagesBackgroundColor by animateColorAsState(
            if (hasMessages) MaterialTheme.colorScheme.errorContainer
            else Color.Transparent,
        )

        WindowInsetsToPaddingValuesBox(insets.only { horizontal }) { horizontalPadding ->
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
                    Spacer(Modifier.height(ContentContainerPadding))
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontalPadding)
                            .padding(horizontal = ContentContainerPadding),
                        shape = ContentContainerShape,
                        color = messagesBackgroundColor,
                    ) {
                        Column {
                            AnimatedContent(
                                targetState = viewModel.messageItems,
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
                                                icon = item.icon,
                                                message = stringResource(item.message),
                                            )
                                        }
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    shape = ContentContainerShape,
                                    color = ContentContainerColor,
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
                                        entryProvider = { it.navEntry() },
                                    )
                                }
                                SnackbarHost()
                            }
                        }
                    }
                    Spacer(Modifier.height(ContentContainerPadding))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontalPadding),
                    ) {
                        ProvideCompatibleShapes {
                            Spacer(Modifier.width(16.dp))
                            TextButton(
                                onClick = {
                                    if (currentPage.isFinal) {
                                        viewModel.finishWizard()
                                        return@TextButton
                                    }
                                    viewModel.skipWizard()
                                },
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
                                Text(stringResource(R.string.label_previous))
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val pageOrNull = currentPage.nextPage(viewModel)
                                    if (pageOrNull == null) {
                                        viewModel.finishWizard()
                                        return@Button
                                    }
                                    viewModel.backStack.add(pageOrNull)
                                },
                                shapes = ButtonDefaults.shapes(),
                                enabled = currentPage.canContinue(viewModel),
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
    }

    @Composable
    fun Message(@DrawableRes icon: Int, message: String) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(icon),
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

    @Transient
    private lateinit var _steps: List<StepItem>

    val steps: List<StepItem>
        @Composable get() {
            if (::_steps.isInitialized) {
                return _steps
            }
            val list = mutableListOf<StepItem>()
            list.add(WizardWelcomePage.stepItem)
            var currentInfo: WizardSubPageInfo? = WizardWelcomePage.nextInfo
            while (currentInfo != null) {
                list.add(currentInfo.stepItem)
                currentInfo = currentInfo.nextInfo
            }
            _steps = list
            return _steps
        }
}

@Immutable
data class WizardMessageItem(
    @param:DrawableRes val icon: Int,
    @param:StringRes val message: Int,
)

interface WizardSubPageInfo {
    val stepItem: StepItem @Composable get
    val nextInfo: WizardSubPageInfo?
    val scrollState: ScrollState
}

@Serializable
class WizardWelcomePage : WizardSubPage() {
    companion object : WizardSubPageInfo {
        override val stepItem
            @Composable get() = StepItem(stringResource(R.string.setup_wizard_welcome_label))
        override val nextInfo: WizardSubPageInfo = WizardProviderPage

        @Transient
        override val scrollState = ScrollState(0)
    }

    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding + PaddingValues(16.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StaticTitle(
                icon = {
                    TitleIcon(R.drawable.stars_2_24px)
                },
                title = R.string.setup_wizard_welcome_title,
                message = R.string.setup_wizard_welcome_message,
            )
        }
    }

    override fun canContinue(viewModel: WizardPageViewModel) = true

    override fun nextPage(viewModel: WizardPageViewModel) = WizardProviderPage()
}

@Serializable
class WizardProviderPage : WizardSubPage() {
    companion object : WizardSubPageInfo {
        override val stepItem: StepItem
            @Composable get() = StepItem(stringResource(R.string.setup_wizard_provider_label))
        override val nextInfo: WizardSubPageInfo = WizardApiPage
        override val scrollState = ScrollState(0)
    }

    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        val viewModel = viewModel<WizardPageViewModel>()

        PageContent(
            contentPadding,
            selectedProvider = viewModel.selectedProvider,
            onSelectedProviderChange = { viewModel.selectedProvider = it },
        )
    }

    @Composable
    fun PageContent(
        contentPadding: PaddingValues,
        selectedProvider: LLMProvider?,
        onSelectedProviderChange: (LLMProvider?) -> Unit,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding + PaddingValues(16.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StaticTitle(
                icon = {
                    TitleIcon(R.drawable.computer_24px)
                },
                title = R.string.setup_wizard_provider_title,
                message = R.string.setup_wizard_provider_message,
            )
            TitleContentSpacer()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                for ((provider, info) in SupportedLLMProviders) {
                    LLMProviderInfo(
                        info = info,
                        modifier = Modifier
                            .fillMaxRowHeight()
                            .layout { mesurable, constraints ->
                                val width = constraints.constrainWidth(ListItemMaxWidth.roundToPx())
                                val placeable = mesurable.measure(
                                    Constraints(
                                        minWidth = width,
                                        maxWidth = width,
                                        minHeight = constraints.minHeight,
                                        maxHeight = constraints.maxHeight,
                                    ),
                                )
                                val height = placeable.height
                                layout(width, height) {
                                    placeable.place(0, 0)
                                }
                            },
                        checked = selectedProvider == provider,
                    ) {
                        if (!it) return@LLMProviderInfo
                        if (selectedProvider == provider) {
                            return@LLMProviderInfo
                        }
                        onSelectedProviderChange(provider)
                    }
                }
            }
        }
    }

    override fun canContinue(viewModel: WizardPageViewModel): Boolean {
        return viewModel.selectedProvider != null
    }

    override fun nextPage(viewModel: WizardPageViewModel) = WizardApiPage()
}

@Serializable
class WizardApiPage : WizardSubPage() {
    companion object : WizardSubPageInfo {
        override val stepItem: StepItem
            @Composable get() = StepItem(stringResource(R.string.setup_wizard_api_label))
        override val nextInfo: WizardSubPageInfo = WizardModelPage
        override val scrollState = ScrollState(0)
    }

    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        val viewModel = viewModel<WizardPageViewModel>()

        var isReady by remember { mutableStateOf(!viewModel.cryptoInitializationError) }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding + PaddingValues(16.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedBackgroundIcon(
                shape1 = MaterialShapes.Cookie12Sided,
                color1 = MaterialTheme.colorScheme.errorContainer,
                rotation1 = 135f,
                icon1 = { TitleIcon(R.drawable.error_24px) },
                shape2 = MaterialShapes.Cookie9Sided,
                color2 = MaterialTheme.colorScheme.primaryContainer,
                rotation2 = 0f,
                icon2 = { TitleIcon(R.drawable.key_24px) },
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
                        ErrorElements { isReady = it }
                        return@AnimatedContent
                    }
                    TitleText(
                        title = R.string.setup_wizard_api_title,
                        message = R.string.setup_wizard_api_message,
                    )
                    TitleContentSpacer()
                    LLMProviderApiKeyTextField(
                        state = viewModel.apiKeyTextFieldState,
                        isUsingPlaintext = viewModel.isUsingPlaintext,
                        providerInfo = viewModel.currentProviderInfo,
                        onRetryCryptoKeyInitialization = {
                            viewModel.generateCryptoKey {
                                isReady = it
                            }
                        },
                    )
                    Divider()
                    LLMProviderBaseUrlTextField(
                        baseUrl = viewModel.baseUrl,
                        onBaseUrlChange = { viewModel.baseUrl = it },
                        providerInfo = viewModel.currentProviderInfo,
                    )
                }
            }
        }

        LaunchedEffect(Unit) {
            if (!isReady) {
                viewModel.generateCryptoKey { isReady = it }
            }
        }
    }

    @Composable
    fun ErrorElements(
        setReady: (Boolean) -> Unit,
    ) {
        val viewModel = viewModel<WizardPageViewModel>()

        TitleText(
            title = R.string.crypto_key_initialization_error_title,
            message = R.string.crypto_key_initialization_error_message,
        )
        TitleContentSpacer()
        Button(
            onClick = { viewModel.generateCryptoKey(setReady) },
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
        ) {
            Text(stringResource(R.string.crypto_key_initialization_error_retry_label))
        }
        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = {
                viewModel.isUsingPlaintext = true
                setReady(true)
            },
        ) {
            Text(stringResource(R.string.crypto_use_plaintext_label))
        }
    }

    override fun canContinue(viewModel: WizardPageViewModel): Boolean {
        return viewModel.apiKey.isNotBlank()
    }

    override fun nextPage(viewModel: WizardPageViewModel): WizardSubPage {
        if (viewModel.baseUrl.isBlank()) {
            viewModel.currentProviderInfo?.let { providerInfo ->
                viewModel.baseUrl = providerInfo.defaultBaseUrl
            }
        }
        viewModel.client = viewModel.currentProviderInfo?.convertToClient(
            viewModel.apiKey,
            viewModel.baseUrl,
        )
        return WizardModelPage()
    }
}

@Serializable
class WizardModelPage : WizardSubPage() {
    companion object : WizardSubPageInfo {
        override val stepItem: StepItem
            @Composable get() = StepItem(stringResource(R.string.setup_wizard_model_label))
        override val nextInfo: WizardSubPageInfo = WizardAdvancedPage
        override val scrollState = ScrollState(0)
    }

    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        val viewModel = viewModel<WizardPageViewModel>()

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding + PaddingValues(16.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StaticTitle(
                icon = {
                    TitleIcon(R.drawable.token_24px)
                },
                title = R.string.setup_wizard_model_title,
                message = R.string.setup_wizard_model_message,
            )
            TitleContentSpacer()
            LLModelTextField(
                state = viewModel.modelTextFieldState,
                knownModelIds = viewModel.knownModelIds,
            )
            Spacer(Modifier.height(16.dp))
            LLMContextLengthTextField(
                contextLength = viewModel.selectedModel?.contextLength,
                onContextLengthChange = {
                    viewModel.selectedModel?.let { model ->
                        viewModel.selectedModel = model.copy(contextLength = it)
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
            LLMMaxOutputTokensTextField(
                maxOutputTokens = viewModel.selectedModel?.maxOutputTokens,
                onMaxOutputTokensChange = {
                    viewModel.selectedModel?.let { model ->
                        viewModel.selectedModel = model.copy(maxOutputTokens = it)
                    }
                },
            )
            AnimatedContent(
                targetState = viewModel.selectedModel == null,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    fadeIn() togetherWith fadeOut() using SizeTransform(clip = false)
                },
                contentAlignment = Alignment.TopCenter,
            ) { modelNotSelected ->
                if (modelNotSelected) {
                    return@AnimatedContent
                }

                val model = viewModel.selectedModel
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Divider {
                        Row(Modifier.fillMaxWidth()) {
                            Spacer(Modifier.width(16.dp))
                            Text(stringResource(R.string.llm_capability_label))
                            Spacer(Modifier.weight(1f))
                            OpenUriIconButton(
                                uri = viewModel.currentProviderInfo?.let { providerInfo ->
                                    model?.let { providerInfo.getModelCapabilitiesUrl(it.id) }
                                },
                                modifier = Modifier.size(24.dp),
                                contentDescriptionRes = R.string.llm_capability_guidance_query,
                            )
                            Spacer(Modifier.width(16.dp))
                        }
                    }
                    ModelCapabilitiesFlow(
                        capabilities = model?.capabilities ?: emptyList(),
                        onAdd = {
                            if (model == null) {
                                return@ModelCapabilitiesFlow
                            }
                            viewModel.selectedModel =
                                model.copy(capabilities = model.capabilities + it)
                        },
                        onRemove = {
                            if (model == null) {
                                return@ModelCapabilitiesFlow
                            }
                            viewModel.selectedModel =
                                model.copy(capabilities = model.capabilities - it)
                        },
                        enabled = model != null,
                    )
                }
            }
        }

        LaunchedEffect(Unit) {
            viewModel.fetchKnownModels()
        }
    }

    override fun canContinue(viewModel: WizardPageViewModel) = viewModel.selectedModel != null

    override fun nextPage(viewModel: WizardPageViewModel) = WizardAdvancedPage()
}

@Serializable
class WizardAdvancedPage : WizardSubPage() {
    companion object : WizardSubPageInfo {
        override val stepItem: StepItem
            @Composable get() = StepItem(stringResource(R.string.setup_wizard_advanced_label))
        override val nextInfo: WizardSubPageInfo = WizardFinishPage
        override val scrollState = ScrollState(0)
    }

    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding + PaddingValues(16.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StaticTitle(
                icon = {
                    TitleIcon(R.drawable.code_24px)
                },
                title = R.string.setup_wizard_advanced_title,
                message = R.string.setup_wizard_advanced_message,
            )
        }
    }

    override fun canContinue(viewModel: WizardPageViewModel): Boolean {
        return true
    }

    override fun nextPage(viewModel: WizardPageViewModel) = WizardFinishPage()
}

@Serializable
class WizardFinishPage : WizardSubPage() {
    companion object : WizardSubPageInfo {
        override val stepItem: StepItem
            @Composable get() = StepItem(stringResource(R.string.setup_wizard_finish_label))
        override val nextInfo: WizardSubPageInfo? = null
        override val scrollState = ScrollState(0)
    }

    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        val viewModel = viewModel<WizardPageViewModel>()

        val coroutineScope = rememberCoroutineScope()
        val resources = LocalResources.current
        val firstMessage by viewModel.firstMessageFlow.collectAsState()

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding + PaddingValues(16.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedBackgroundIcon(
                shape1 = MaterialShapes.Cookie9Sided,
                color1 = MaterialTheme.colorScheme.primaryContainer,
                rotation1 = 0f,
                icon1 = { TitleIcon(R.drawable.celebration_24px) },
                shape2 = MaterialShapes.Cookie12Sided,
                color2 = MaterialTheme.colorScheme.errorContainer,
                rotation2 = 135f,
                icon2 = { TitleIcon(R.drawable.error_24px) },
                displayShape2 = viewModel.apiConfigurationError,
            )
            IconTitleSpacer()
            AnimatedContent(
                targetState = viewModel.apiConfigurationError,
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
                visible = !viewModel.apiConfigurationError,
                enter = fadeIn() + expandVertically(
                    expandFrom = Alignment.Top,
                    clip = false,
                ),
                exit = fadeOut() + shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    clip = false,
                ),
            ) {
                Column {
                    Spacer(Modifier.height(24.dp))
                    AnimatedContent(
                        targetState = firstMessage,
                        modifier = Modifier.fillMaxWidth(),
                        transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
                        contentAlignment = Alignment.TopCenter,
                        contentKey = { it.isBlank() },
                    ) { firstMessage ->
                        if (firstMessage.isNotBlank()) {
                            MarkdownView(
                                viewModel.firstMessageState,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        } else {
                            LoadingIndicator()
                        }
                    }
                }
            }
            TitleContentSpacer()
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = viewModel.firstMessageGenerated,
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
                        RetryIconButton(
                            onRetry = {
                                coroutineScope.launch {
                                    viewModel.generateFirstMessage(resources)
                                }
                            },
                            enabled = !viewModel.firstMessageGenerationStarted,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
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
                            onClick = viewModel::finishWizard,
                            shapes = ButtonDefaults.shapesFor(size),
                            modifier = localSharedTransitionScope {
                                Modifier.sharedBounds(
                                    rememberSharedContentState(WizardSharedTransitionKey),
                                    LocalNavAnimatedContentScope.current,
                                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                )
                            },
                            contentPadding = ButtonDefaults.contentPaddingFor(size),
                        ) {
                            Icon(
                                painterResource(R.drawable.arrow_forward_24px),
                                contentDescription = stringResource(R.string.label_finish),
                                modifier = Modifier.size(ButtonDefaults.iconSizeFor(size)),
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = viewModel.firstMessageGenerated,
                    enter = fadeIn() + expandHorizontally(
                        expandFrom = Alignment.End,
                        clip = false,
                    ),
                    exit = fadeOut() + shrinkHorizontally(
                        shrinkTowards = Alignment.End,
                        clip = false,
                    ),
                ) {
                    Row {
                        Spacer(Modifier.width(8.dp))
                        CopyIconButton(
                            onCopy = { null to firstMessage },
                            enabled = !viewModel.firstMessageGenerationStarted,
                        )
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            if (!viewModel.firstMessageGenerated) {
                viewModel.generateFirstMessage(resources)
            }
        }

        var shouldFollow by remember { mutableStateOf(false) }
        val isScrolling by scrollState.interactionSource.collectIsDraggedAsState()

        LaunchedEffect(isScrolling) {
            if (!isScrolling) {
                shouldFollow = scrollState.value >= scrollState.maxValue - 100
            }
        }

        var isFirstTimeLaunchedByMaxValue by remember { mutableStateOf(true) }
        LaunchedEffect(scrollState.maxValue) {
            if (isFirstTimeLaunchedByMaxValue) {
                isFirstTimeLaunchedByMaxValue = false
                if (viewModel.firstMessageGenerated) {
                    return@LaunchedEffect
                }
            }
            if (shouldFollow) {
                scrollState.scrollTo(scrollState.maxValue)
            }
        }
    }

    override val isFinal = true
    override fun canContinue(viewModel: WizardPageViewModel) = true
    override fun nextPage(viewModel: WizardPageViewModel) = null
}

@Serializable
sealed class WizardSubPage : SubPage<WizardSubPage>() {
    @Transient
    open val isFinal = false
    abstract fun canContinue(viewModel: WizardPageViewModel): Boolean
    abstract fun nextPage(viewModel: WizardPageViewModel): WizardSubPage?
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
    @DrawableRes drawable: Int,
) {
    Icon(
        painterResource(drawable),
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

@Immutable
data object WizardSharedTransitionKey
