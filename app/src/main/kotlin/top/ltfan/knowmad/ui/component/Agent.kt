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
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxOfOrNull
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.launch
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.llm.LLMConfigEntity
import top.ltfan.knowmad.data.llm.LLMProviderConfigEntity
import top.ltfan.knowmad.ui.util.AppWindowInsets
import top.ltfan.knowmad.ui.util.WindowInsetsToPaddingValuesBox
import top.ltfan.knowmad.ui.util.copy
import top.ltfan.knowmad.ui.util.localSharedTransitionScope
import top.ltfan.knowmad.ui.util.only
import top.ltfan.knowmad.ui.util.plus
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
                        resizeMode = RemeasureToBounds,
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

    val coroutineScope = rememberCoroutineScope()
    val layoutDirection = LocalLayoutDirection.current

    val currentConversation by viewModel.currentConversationFlow.collectAsState(null)

    ModalNavigationDrawer(
        drawerContent = {
            ModalDrawerSheet(
                viewModel.drawerState,
                drawerContainerColor = DrawerContainerColor,
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
                    ) + PaddingValues(
                        top = 16.dp,
                    )
                    ConversationList(contentPadding)
                }
            }
        },
        drawerState = viewModel.drawerState,
        gesturesEnabled = !appViewModel.partial,
        scrimColor = DrawerScrimColor,
    ) {
        Scaffold(
            topBar = {
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
                    modifier = Modifier.clickable(
                        onClick = { showDialog = true },
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ),
                    navigationIcon = {
                        if (appViewModel.partial) return@CenterAlignedTopAppBar
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
                    },
                    actions = {
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
                                IconButton(
                                    onClick = { appViewModel.switchStandaloneAgentScreen() },
                                ) {
                                    Icon(
                                        painterResource(if (LocalAgentScreenIsStandalone.current) R.drawable.fullscreen_exit_24px else R.drawable.fullscreen_24px),
                                        contentDescription = stringResource(if (LocalAgentScreenIsStandalone.current) R.string.agent_standalone_label_exit else R.string.agent_standalone_label_enter),
                                    )
                                }
                            }
                        }
                        if (!appViewModel.partial) {
                            TooltipBox(
                                TooltipDefaults.rememberTooltipPositionProvider(
                                    TooltipAnchorPosition.Below,
                                ),
                                tooltip = {
                                    PlainTooltip { Text(stringResource(R.string.agent_conversation_label_new)) }
                                },
                                state = rememberTooltipState(),
                            ) {
                                IconButton(onClick = { viewModel.newConversation() }) {
                                    Icon(
                                        painterResource(R.drawable.edit_square_24px),
                                        contentDescription = stringResource(R.string.agent_conversation_label_new),
                                    )
                                }
                            }
                        }
                    },
                    windowInsets = AppWindowInsets.only { horizontal + top },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ContainerColor,
                        scrolledContainerColor = ContainerColor,
                    ),
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
            snackbarHost = {
                if (LocalAgentScreenIsStandalone.current) {
                    SnackbarHost()
                }
            },
            containerColor = ContainerColor.scaffoldContainer,
            contentColor = ScaffoldContentColor,
            contentWindowInsets = AppWindowInsets,
        ) { scaffoldPaddingValues ->
            val safeContentPadding = scaffoldPaddingValues + contentPadding
            val contentPadding = safeContentPadding + PaddingValues(16.dp)

            val messages = viewModel.currentMessagesFlow?.collectAsLazyPagingItems()

            SubcomposeLayout { constraints ->
                val inputPlaceables = subcompose("input") {
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
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                    )
                }.fastMap { it.measure(constraints) }

                val inputHeight = inputPlaceables.fastMaxOfOrNull { it.height } ?: 0

                val scrimPlaceables = subcompose("scrim") {
                    val color = ContainerColor.scaffoldContainer.copy(alpha = .8f)

                    Column(Modifier.fillMaxSize()) {
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 64.dp)
                                .fillMaxHeight()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            color,
                                        ),
                                    ),
                                ),
                        )
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(color),
                        )
                    }
                }.fastMap {
                    it.measure(
                        constraints.copy(
                            minHeight = inputHeight,
                            maxHeight = inputHeight,
                        ),
                    )
                }

                val listPlaceables = subcompose("messageList") {
                    val padding = contentPadding.copy(
                        layoutDirection,
                        bottom = inputHeight.toDp(),
                    )

                    if (messages == null) {
                        Column(
                            modifier = Modifier
                                .padding(padding)
                                .fillMaxSize(),
                        ) {

                        }
                        return@subcompose
                    }

                    val lazyListState = rememberLazyListState(
                        initialFirstVisibleItemIndex = viewModel.savedMessagesFirstVisibleItemIndex,
                        initialFirstVisibleItemScrollOffset = viewModel.savedMessagesFirstVisibleItemScrollOffset,
                    )

                    CompositionLocalProvider(LocalMarkdownRunCodeEnabled provides viewModel.runCodeEnabled) {
                        ChatMessageList(
                            getMessageCount = { messages.itemCount },
                            getMessageKey = messages.itemKey { it.key },
                            getMessageAt = { viewModel.getMessage(messages[it]) },
                            mathJaxRendererState = appViewModel.mathJaxRendererState,
                            modifier = Modifier.fillMaxSize(),
                            onPrevious = viewModel::messageOnPrevious,
                            onNext = viewModel::messageOnNext,
                            onRegenerate = viewModel::messageOnRegenerate,
                            initialReasoningVisibility = viewModel.defaultReasoningVisibility,
                            onAnyReasoningVisibilityChange = viewModel::defaultReasoningVisibility::set,
                            initialToolVisibility = viewModel.defaultToolVisibility,
                            onAnyToolVisibilityChange = viewModel::defaultToolVisibility::set,
                            contentPadding = padding,
                            lazyListState = lazyListState,
                            assistantMessageStates = viewModel.assistantMessageStates,
                            runnableCodeComponents = viewModel.runnableCodeComponents,
                            runCode = viewModel::runAssistantCode,
                        )
                    }

                    LaunchedEffect(lazyListState) {
                        snapshotFlow {
                            lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset
                        }
                            .collect { (index, offset) ->
                                viewModel.savedMessagesFirstVisibleItemIndex = index
                                viewModel.savedMessagesFirstVisibleItemScrollOffset = offset
                            }
                    }

                    var isAtBottom by remember { mutableStateOf(true) }

                    val isScrolling = lazyListState.isScrollInProgress
                    LaunchedEffect(isScrolling) {
                        if (!isScrolling) {
                            isAtBottom =
                                lazyListState.firstVisibleItemIndex == 0 &&
                                        lazyListState.firstVisibleItemScrollOffset == 0
                        }
                    }

                    val currentFirstMessageKey = messages.itemSnapshotList.firstOrNull()?.key
                    LaunchedEffect(currentFirstMessageKey) {
                        if (isAtBottom) {
                            lazyListState.animateScrollToItem(0)
                        }
                    }

                    var isFirstTimeScroll by remember { mutableStateOf(true) }
                    LaunchedEffect(viewModel.currentConversationId) {
                        if (isFirstTimeScroll) {
                            isFirstTimeScroll = false
                            return@LaunchedEffect
                        }
                        lazyListState.requestScrollToItem(0)
                    }
                }.fastMap { it.measure(constraints) }

                layout(constraints.maxWidth, constraints.maxHeight) {
                    listPlaceables.fastForEach {
                        it.placeRelative(0, 0)
                    }
                    scrimPlaceables.fastForEach {
                        it.placeRelative(
                            0,
                            constraints.maxHeight - it.height,
                        )
                    }
                    inputPlaceables.fastForEach {
                        it.placeRelative(
                            (constraints.maxWidth - it.width) / 2,
                            constraints.maxHeight - it.height,
                        )
                    }
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
            TopAppBar(
                title = {
                    Text(stringResource(R.string.llm_config_label_settings))
                },
                navigationIcon = {
                    TooltipBox(
                        TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Below,
                        ),
                        tooltip = {
                            PlainTooltip { Text(stringResource(R.string.label_back)) }
                        },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(
                            onClick = { viewModel.backStack.removeLastOrNull() },
                            enabled = viewModel.backStack.size > 1,
                        ) {
                            Icon(
                                painterResource(R.drawable.arrow_back_24px),
                                contentDescription = stringResource(R.string.label_back),
                            )
                        }
                    }
                },
                actions = {
                    TooltipBox(
                        TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Below,
                        ),
                        tooltip = {
                            PlainTooltip { Text(stringResource(R.string.llm_provider_label_add)) }
                        },
                        state = rememberTooltipState(),
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ContainerColor,
                    scrolledContainerColor = ContainerColor,
                ),
            )
        },
        snackbarHost = {
            if (LocalAgentScreenIsStandalone.current) {
                SnackbarHost()
            }
        },
        containerColor = ContainerColor.scaffoldContainer,
        contentColor = ScaffoldContentColor,
        contentWindowInsets = AppWindowInsets,
    ) { scaffoldPadding ->
        val contentPadding = scaffoldPadding + contentPadding + PaddingValues(16.dp)

        LLMProviderConfig(
            editingProvider = editingProvider,
            onEditProvider = { editingProvider = it },
            editingModel = editingModel,
            onEditModel = { editingModel = it },
            onAddModel = {
                editingModel = it to LLMConfigEntity(
                    providerConfigId = it.id,
                    model = LLModel(provider = it.provider, id = ""),
                )
            },
            modifier = Modifier.fillMaxSize(),
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
        LLMProviderConfigEditingDialog(
            entity = provider,
            onDismissRequest = { creatingProvider = null },
            isNew = true,
        )
    }
}

private val DrawerScrimColor
    @Composable inline get() = if (LocalAgentScreenTransparentContainer.current) Color.Transparent else DrawerDefaults.scrimColor

private val DrawerContainerColor
    @Composable inline get() = if (LocalAgentScreenTransparentContainer.current) MaterialTheme.colorScheme.surface else DrawerDefaults.modalContainerColor

private val ContainerColor
    @Composable inline get() = if (LocalAgentScreenTransparentContainer.current) LocalAgentScreenPreferredContainerColor.current.takeOrElse { Color.Transparent }
    else Color.Unspecified

private val Color.scaffoldContainer
    @Composable inline get() = takeOrElse { MaterialTheme.colorScheme.background }

private val ScaffoldContentColor
    @Composable inline get() = MaterialTheme.colorScheme.onBackground

@Immutable
data object AgentScreenSharedKey

val LocalAgentScreenTransparentContainer = staticCompositionLocalOf { false }
val LocalAgentScreenPreferredContainerColor = staticCompositionLocalOf { Color.Unspecified }
val LocalAgentScreenIsStandalone = staticCompositionLocalOf { false }
