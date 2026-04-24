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

package top.ltfan.knowmad.ui.component

import ai.koog.prompt.llm.LLModel
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.llm.LLMConfigEntity
import top.ltfan.knowmad.data.llm.LLMProviderConfigEntity
import top.ltfan.knowmad.data.llm.SupportedLLMProviders
import top.ltfan.knowmad.ui.page.OnDeviceModelPage
import top.ltfan.knowmad.ui.theme.TopAppBarColorsTransparent
import top.ltfan.knowmad.ui.util.AppWindowInsets
import top.ltfan.knowmad.ui.util.BackdropEffectsHeavy
import top.ltfan.knowmad.ui.util.BackdropEffectsLight
import top.ltfan.knowmad.ui.util.BackdropEffectsMedium
import top.ltfan.knowmad.ui.util.BackdropInteractiveHighlight
import top.ltfan.knowmad.ui.util.LinearBrushData
import top.ltfan.knowmad.ui.util.WindowInsetsToPaddingValuesBox
import top.ltfan.knowmad.ui.util.copy
import top.ltfan.knowmad.ui.util.localSharedTransitionScope
import top.ltfan.knowmad.ui.util.only
import top.ltfan.knowmad.ui.util.plus
import top.ltfan.knowmad.ui.util.progressiveBlurWithFallback
import top.ltfan.knowmad.ui.viewmodel.LocalAgentViewModel
import top.ltfan.knowmad.ui.viewmodel.LocalAppViewModel

@Composable
fun AgentScreen(
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val viewModel = LocalAgentViewModel.current

    NavDisplay(
        backStack = viewModel.backStack,
        modifier = Modifier
            .fillMaxSize()
            .run {
                if (animatedVisibilityScope == null) this
                else localSharedTransitionScope {
                    sharedBounds(
                        rememberSharedContentState(AgentScreenSharedKey),
                        animatedVisibilityScope,
                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(Crop),
                    )
                }
            },
        transitionSpec = {
            fadeIn() + slideInHorizontally { it / 2 } togetherWith fadeOut() + slideOutHorizontally()
        },
        popTransitionSpec = {
            fadeIn() + slideInHorizontally() togetherWith fadeOut() + slideOutHorizontally { it / 2 }
        },
        predictivePopTransitionSpec = { edge ->
            val factor = if (edge == NavigationEvent.EDGE_RIGHT) -1 else 1
            fadeIn() + slideInHorizontally { -it * factor / 2 } togetherWith fadeOut() + slideOutHorizontally { it * factor / 2 }
        },
        entryProvider = { it.navEntry(contentPadding) },
    )
}

@Composable
fun AgentMainScreen(
    contentPadding: PaddingValues,
) {
    val appViewModel = LocalAppViewModel.current
    val viewModel = LocalAgentViewModel.current

    val isNewWindow = LocalAgentScreenIsNewWindow.current

    val layoutDirection = LocalLayoutDirection.current

    val containerColor = ContainerColor.filledContainer

    val backdrop = rememberLayerBackdrop {
        drawRect(containerColor)
        drawContent()
    }
    val backdropForDrawer = rememberLayerBackdrop {
        drawRect(containerColor)
        drawContent()
    }

    val currentConversation by viewModel.currentConversationFlow.collectAsState(null)

    ModalNavigationDrawer(
        drawerContent = {
            val drawerBackdrop = rememberLayerBackdrop()

            val shape = DrawerDefaults.shape
            val color = DrawerContainerColor
            ModalDrawerSheet(
                drawerState = viewModel.drawerState,
                modifier = Modifier.drawBackdrop(
                    backdrop = backdropForDrawer,
                    shape = { shape },
                    effects = BackdropEffectsHeavy,
                    exportedBackdrop = drawerBackdrop,
                    onDrawSurface = {
                        drawRect(color.copy(alpha = 0.6f))
                    },
                ),
                drawerContainerColor = Transparent,
                drawerContentColor = contentColorFor(color),
                windowInsets = AppWindowInsets.only { start } + contentPadding.copy(
                    layoutDirection,
                    top = 0.dp,
                    end = 0.dp,
                    bottom = 0.dp,
                ),
            ) {
                WindowInsetsToPaddingValuesBox(AppWindowInsets.only { vertical }) {
                    val contentPadding = it + contentPadding.copy(
                        layoutDirection,
                        start = 0.dp,
                        end = 0.dp,
                    )
                    ConversationList(contentPadding, drawerBackdrop)
                }
            }
        },
        drawerState = viewModel.drawerState,
        gesturesEnabled = !isNewWindow,
        scrimColor = DrawerScrimColor,
    ) {
        Scaffold(
            modifier = Modifier.layerBackdrop(backdropForDrawer),
            topBar = {
                val appBarBackdrop = rememberLayerBackdrop()

                var showDialog by remember { mutableStateOf(false) }

                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            currentConversation?.name ?: "",
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            maxLines = 1,
                        )
                    },
                    modifier = Modifier
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RectangleShape },
                            effects = {
                                progressiveBlurWithFallback(
                                    radius = 48.dp.toPx(),
                                    data = LinearBrushData(
                                        start = Offset(0f, POSITIVE_INFINITY),
                                    ),
                                )
                            },
                            highlight = null,
                            shadow = null,
                            exportedBackdrop = appBarBackdrop,
                        )
                        .clickable(
                            onClick = { showDialog = true },
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ),
                    navigationIcon = {
                        val coroutineScope = rememberCoroutineScope()
                        val interactiveHighlight = remember(coroutineScope) {
                            BackdropInteractiveHighlight(coroutineScope)
                        }
                        Box(
                            modifier = Modifier
                                .drawBackdrop(
                                    backdrop = appBarBackdrop,
                                    shape = { CircleShape },
                                    effects = BackdropEffectsLight,
                                    shadow = null,
                                )
                                .then(interactiveHighlight.modifier)
                                .then(interactiveHighlight.gestureModifier),
                        ) {
                            if (isNewWindow) {
                                val activity = LocalActivity.current
                                if (activity != null) {
                                    CloseIconButton(onClick = activity::finish)
                                }
                                return@CenterAlignedTopAppBar
                            }
                            TooltipBox(
                                TooltipDefaults.rememberTooltipPositionProvider(
                                    TooltipAnchorPosition.Below,
                                ),
                                tooltip = {
                                    PlainTooltip {
                                        Text(stringResource(if (viewModel.drawerState.isClosed) R.string.agent_drawer_label_open else R.string.agent_drawer_label_close))
                                    }
                                },
                                state = rememberTooltipState(),
                            ) {
                                IconButton(
                                    onClick = { coroutineScope.launch { viewModel.toggleDrawer() } },
                                ) {
                                    Icon(
                                        painterResource(R.drawable.menu_24px),
                                        contentDescription = stringResource(if (viewModel.drawerState.isClosed) R.string.agent_drawer_label_open else R.string.agent_drawer_label_close),
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        val coroutineScope = rememberCoroutineScope()
                        val interactiveHighlight = remember(coroutineScope) {
                            BackdropInteractiveHighlight(coroutineScope)
                        }
                        Row(
                            modifier = Modifier
                                .drawBackdrop(
                                    backdrop = appBarBackdrop,
                                    shape = { ContinuousCapsule },
                                    effects = BackdropEffectsLight,
                                    shadow = null,
                                )
                                .then(interactiveHighlight.modifier)
                                .then(interactiveHighlight.gestureModifier),
                        ) {
                            if (!LocalAgentScreenIsStandalone.current || appViewModel.canExitStandaloneAgentScreen) {
                                TooltipBox(
                                    TooltipDefaults.rememberTooltipPositionProvider(
                                        TooltipAnchorPosition.Below,
                                    ),
                                    tooltip = {
                                        PlainTooltip {
                                            Text(stringResource(if (LocalAgentScreenIsStandalone.current) R.string.agent_standalone_label_exit else R.string.agent_standalone_label_enter))
                                        }
                                    },
                                    state = rememberTooltipState(),
                                ) {
                                    IconButton(onClick = appViewModel::switchStandaloneAgentScreen) {
                                        Icon(
                                            painterResource(if (LocalAgentScreenIsStandalone.current) R.drawable.fullscreen_exit_24px else R.drawable.fullscreen_24px),
                                            contentDescription = stringResource(if (LocalAgentScreenIsStandalone.current) R.string.agent_standalone_label_exit else R.string.agent_standalone_label_enter),
                                        )
                                    }
                                }
                            }
                            if (!isNewWindow) {
                                TooltipBox(
                                    TooltipDefaults.rememberTooltipPositionProvider(
                                        TooltipAnchorPosition.Below,
                                    ),
                                    tooltip = {
                                        PlainTooltip { Text(stringResource(R.string.agent_conversation_label_new)) }
                                    },
                                    state = rememberTooltipState(),
                                ) {
                                    IconButton(onClick = viewModel::newConversation) {
                                        Icon(
                                            painterResource(R.drawable.edit_square_24px),
                                            contentDescription = stringResource(R.string.agent_conversation_label_new),
                                        )
                                    }
                                }
                            }
                        }
                    },
                    windowInsets = AppWindowInsets.only { horizontal + top },
                    colors = TopAppBarColorsTransparent,
                )

                if (showDialog) {
                    currentConversation?.let { currentConversation ->
                        ConversationEditingDialog(
                            conversation = currentConversation,
                            onDismissRequest = { showDialog = false },
                            onConfirm = {
                                viewModel.editConversation(it)
                                showDialog = false
                            },
                            onAutoGenerateName = {
                                viewModel.generateConversationName(currentConversation.id)
                            },
                        )
                    }
                }
            },
            bottomBar = {
                Box {
                    Spacer(
                        Modifier
                            .matchParentSize()
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { RectangleShape },
                                effects = {
                                    progressiveBlurWithFallback(
                                        radius = 48.dp.toPx(),
                                        data = LinearBrushData(
                                            end = Offset(0f, POSITIVE_INFINITY),
                                        ),
                                    )
                                },
                                highlight = null,
                                shadow = null,
                            ),
                    )

                    val coroutineScope = rememberCoroutineScope()

                    val interactiveHighlight = remember(coroutineScope) {
                        BackdropInteractiveHighlight(coroutineScope)
                    }

                    val shape = MaterialTheme.shapes.medium
                    val color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    ChatInput(
                        textState = viewModel.chatMessageTextInputState,
                        sendEnabled = viewModel.canSendMessageUi,
                        onSend = viewModel::sendMessage,
                        isRunning = viewModel.serviceCurrentTaskRunning,
                        onCancel = viewModel::cancelGeneration,
                        providers = viewModel.providers,
                        getModels = viewModel::getModels,
                        selectedModel = viewModel.selectedModelEntity,
                        onSelectModel = { model -> viewModel.selectedModelId = model.id },
                        modifier = Modifier
                            .padding(
                                contentPadding.copy(
                                    layoutDirection,
                                    top = 0.dp,
                                ),
                            )
                            .padding(16.dp)
                            .windowInsetsPadding(AppWindowInsets.only { horizontal + bottom })
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .padding(8.dp)
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { shape },
                                effects = BackdropEffectsMedium,
                                onDrawSurface = {
                                    drawRect(color)
                                },
                            )
                            .then(interactiveHighlight.modifier)
                            .then(interactiveHighlight.gestureModifier),
                    )
                }
            },
            snackbarHost = {
                if (LocalAgentScreenIsStandalone.current) {
                    SnackbarHost()
                }
            },
            containerColor = containerColor,
            contentColor = ScaffoldContentColor,
            contentWindowInsets = AppWindowInsets,
        ) { scaffoldPadding ->
            val contentPadding = scaffoldPadding + contentPadding + PaddingValues(16.dp)

            val messages by viewModel.currentMessagesFlow.collectAsState(emptyList())

            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                ) {

                }
                return@Scaffold
            }

            val lazyListState = rememberSaveable(
                messages.firstOrNull()?.conversationId,
                saver = LazyListState.Saver,
            ) {
                LazyListState(
                    firstVisibleItemIndex = (messages.size - 1).fastCoerceAtLeast(0),
                    firstVisibleItemScrollOffset = 0,
                )
            }

            CompositionLocalProvider(LocalMarkdownRunCodeEnabled provides viewModel.runCodeEnabled) {
                ChatMessageList(
                    getMessageCount = { messages.size },
                    getMessageKey = { messages[it].key },
                    getMessageAt = { viewModel.getMessage(messages[it]) },
                    mathJaxRendererState = appViewModel.mathJaxRendererState,
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(backdrop),
                    onPrevious = viewModel::messageOnPrevious,
                    onNext = viewModel::messageOnNext,
                    onRegenerate = viewModel::messageOnRegenerate,
                    initialReasoningVisibility = viewModel.defaultReasoningVisibility,
                    initialToolVisibility = viewModel.defaultToolVisibility,
                    contentPadding = contentPadding,
                    lazyListState = lazyListState,
                    assistantMessageStates = viewModel.assistantMessageStates,
                    runnableCodeComponents = viewModel.runnableCodeComponents,
                    runCode = viewModel::runAssistantCode,
                )
            }

            var followBottom by remember(lazyListState) { mutableStateOf(true) }

            data class ScrollInfo(
                val index: Int?,
                val size: Int?,
                val heightOffset: Int,
                val canScrollForward: Boolean,
            )

            val scrollInfoFlow = remember(lazyListState) {
                snapshotFlow {
                    val info = lazyListState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()

                    ScrollInfo(
                        index = last?.index,
                        size = last?.size,
                        heightOffset = info.beforeContentPadding + info.afterContentPadding - info.viewportSize.height,
                        canScrollForward = lazyListState.canScrollForward,
                    )
                }
            }

            LaunchedEffect(scrollInfoFlow) {
                scrollInfoFlow.conflate().collect { (index, size, heightOffset, canScrollForward) ->
                    if (index == null || size == null) return@collect

                    val atBottom = !canScrollForward

                    if (lazyListState.isScrollInProgress) {
                        if (!atBottom) {
                            followBottom = false
                        }
                        return@collect
                    }

                    if (atBottom) {
                        followBottom = true
                    } else if (!followBottom) {
                        return@collect
                    }

                    val requestedOffset = heightOffset + size
                    if (
                        index == lazyListState.firstVisibleItemIndex &&
                        requestedOffset == lazyListState.firstVisibleItemScrollOffset
                    ) return@collect

                    lazyListState.requestScrollToItem(index, requestedOffset)
                }
            }
        }
    }
}

@Composable
fun AgentConfigScreen(
    contentPadding: PaddingValues,
) {
    val viewModel = LocalAgentViewModel.current
    val appViewModel = LocalAppViewModel.current

    val containerColor = ContainerColor.filledContainer

    val backdrop = rememberLayerBackdrop {
        drawRect(containerColor)
        drawContent()
    }

    var isSelectingNewProvider by remember { mutableStateOf(false) }
    var creatingProvider by remember { mutableStateOf<LLMProviderConfigEntity?>(null) }
    var editingProvider by remember { mutableStateOf<LLMProviderConfigEntity?>(null) }

    var editingModel by remember {
        mutableStateOf<Pair<LLMProviderConfigEntity, LLMConfigEntity>?>(
            null,
        )
    }

    Scaffold(
        topBar = {
            val appBarBackdrop = rememberLayerBackdrop()
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.llm_config_label_settings)) },
                modifier = Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = {
                        progressiveBlurWithFallback(
                            radius = 48.dp.toPx(),
                            data = LinearBrushData(
                                start = Offset(0f, POSITIVE_INFINITY),
                            ),
                        )
                    },
                    highlight = null,
                    shadow = null,
                    exportedBackdrop = appBarBackdrop,
                ),
                navigationIcon = {
                    val coroutineScope = rememberCoroutineScope()
                    val interactiveHighlight = remember(coroutineScope) {
                        BackdropInteractiveHighlight(coroutineScope)
                    }
                    ArrowBackIconButton(
                        onClick = viewModel.backStack::removeLastOrNull,
                        modifier = Modifier
                            .drawBackdrop(
                                backdrop = appBarBackdrop,
                                shape = { CircleShape },
                                effects = BackdropEffectsLight,
                                shadow = null,
                            )
                            .then(interactiveHighlight.modifier)
                            .then(interactiveHighlight.gestureModifier),
                        enabled = viewModel.backStack.size > 1,
                    )
                },
                actions = {
                    val coroutineScope = rememberCoroutineScope()
                    val interactiveHighlight = remember(coroutineScope) {
                        BackdropInteractiveHighlight(coroutineScope)
                    }
                    TooltipBox(
                        TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Below,
                        ),
                        tooltip = {
                            PlainTooltip { Text(stringResource(R.string.llm_provider_label_add)) }
                        },
                        state = rememberTooltipState(),
                        modifier = Modifier
                            .drawBackdrop(
                                backdrop = appBarBackdrop,
                                shape = { ContinuousCapsule },
                                effects = BackdropEffectsLight,
                                shadow = null,
                            )
                            .then(interactiveHighlight.modifier)
                            .then(interactiveHighlight.gestureModifier),
                    ) {
                        IconButton(onClick = { isSelectingNewProvider = true }) {
                            Icon(
                                painterResource(R.drawable.add_24px),
                                contentDescription = stringResource(R.string.llm_provider_label_add),
                            )
                        }
                    }
                },
                windowInsets = AppWindowInsets.only { horizontal + top },
                colors = TopAppBarColorsTransparent,
            )
        },
        snackbarHost = {
            if (LocalAgentScreenIsStandalone.current) {
                SnackbarHost()
            }
        },
        containerColor = containerColor,
        contentColor = ScaffoldContentColor,
        contentWindowInsets = AppWindowInsets,
    ) { scaffoldPadding ->
        val contentPadding = scaffoldPadding + contentPadding + PaddingValues(16.dp)

        LLMProviderConfig(
            editingProvider = editingProvider,
            onEditProvider = { editingProvider = it },
            onEditAppProvider = { provider ->
                appViewModel.backStack.add(OnDeviceModelPage(providerConfigId = provider.id))
            },
            editingModel = editingModel,
            onEditModel = { editingModel = it },
            onAddModel = {
                editingModel = it to LLMConfigEntity(
                    providerConfigId = it.id,
                    model = LLModel(provider = it.provider, id = ""),
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
            contentPadding = contentPadding,
        )
    }

    if (isSelectingNewProvider) {
        LLMProviderSelectionDialog(
            onProviderSelected = { provider ->
                creatingProvider = LLMProviderConfigEntity(
                    provider = provider,
                    apiKey = byteArrayOf(),
                    iv = null,
                )
            },
            onDismissRequest = { isSelectingNewProvider = false },
        )
    }

    creatingProvider?.let { provider ->
        if (SupportedLLMProviders[provider.provider] is App) {
            LaunchedEffect(provider) {
                viewModel.editProviderConfig(provider) {}
                creatingProvider = null
                appViewModel.backStack.add(OnDeviceModelPage(providerConfigId = provider.id))
            }
        } else {
            LLMProviderConfigEditingDialog(
                entity = provider,
                onDismissRequest = { creatingProvider = null },
                isNew = true,
            )
        }
    }
}

private val DrawerScrimColor
    @Composable inline get() = if (LocalAgentScreenTransparentContainer.current) Color.Transparent else DrawerDefaults.scrimColor

private val DrawerContainerColor
    @Composable inline get() = if (LocalAgentScreenTransparentContainer.current) MaterialTheme.colorScheme.surface else DrawerDefaults.modalContainerColor

private val ContainerColor
    @Composable inline get() = if (LocalAgentScreenTransparentContainer.current) LocalAgentScreenPreferredContainerColor.current.takeOrElse { Color.Transparent }
    else Color.Unspecified

private val Color.filledContainer
    @Composable inline get() = takeOrElse { MaterialTheme.colorScheme.background }

private val ScaffoldContentColor
    @Composable inline get() = MaterialTheme.colorScheme.onBackground

@Immutable
data object AgentScreenSharedKey

val LocalAgentScreenTransparentContainer = staticCompositionLocalOf { false }
val LocalAgentScreenPreferredContainerColor = staticCompositionLocalOf { Color.Unspecified }
val LocalAgentScreenIsStandalone = staticCompositionLocalOf { false }
val LocalAgentScreenIsNewWindow = staticCompositionLocalOf { false }
