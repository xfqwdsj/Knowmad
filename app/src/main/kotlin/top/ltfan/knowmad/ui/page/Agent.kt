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

import ai.koog.prompt.llm.LLModel
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.llm.LLMConfigEntity
import top.ltfan.knowmad.data.llm.LLMProviderConfigEntity
import top.ltfan.knowmad.ui.component.AgentScreen
import top.ltfan.knowmad.ui.component.ConversationList
import top.ltfan.knowmad.ui.component.LLMProviderConfig
import top.ltfan.knowmad.ui.component.LLMProviderConfigEditingDialog
import top.ltfan.knowmad.ui.component.LLMProviderSelectionDialog
import top.ltfan.knowmad.ui.component.LocalAgentScreenIsStandalone
import top.ltfan.knowmad.ui.component.LocalAgentScreenTransparentBackground
import top.ltfan.knowmad.ui.util.AppWindowInsets
import top.ltfan.knowmad.ui.util.WindowInsetsToPaddingValuesBox
import top.ltfan.knowmad.ui.util.copy
import top.ltfan.knowmad.ui.util.only
import top.ltfan.knowmad.ui.util.plus
import top.ltfan.knowmad.ui.viewmodel.LocalAgentViewModel
import top.ltfan.knowmad.ui.viewmodel.LocalAppViewModel

@Serializable
class AgentMainPage : AgentSubPage() {
    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        PageContent(contentPadding)
    }

    @Composable
    fun PageContent(
        contentPadding: PaddingValues,
    ) {
        val appViewModel = LocalAppViewModel.current
        val viewModel = LocalAgentViewModel.current

        val coroutineScope = rememberCoroutineScope()
        val layoutDirection = LocalLayoutDirection.current

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
            scrimColor = DrawerScrimColor,
        ) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(viewModel.currentConversation?.name ?: "")
                        },
                        navigationIcon = {
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
                        },
                        windowInsets = AppWindowInsets.only { horizontal + top },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = ContainerColor,
                            scrolledContainerColor = ContainerColor,
                        ),
                    )
                },
                containerColor = ContainerColor.scaffoldContainer,
                contentColor = ScaffoldContentColor,
                contentWindowInsets = AppWindowInsets,
            ) {
                val contentPadding = it + contentPadding + PaddingValues(16.dp)


            }
        }
    }

}

@Serializable
class AgentConfigPage : AgentSubPage() {
    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        PageContent(contentPadding)
    }

    @Composable
    fun PageContent(
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
                        model = LLModel(
                            provider = it.provider,
                            id = "",
                            capabilities = emptyList(),
                            contextLength = 0,
                        ),
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
}

@Serializable
sealed class AgentSubPage : SubPage<AgentSubPage>()

@Serializable
class AgentPage : Page() {
    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        CompositionLocalProvider(LocalAgentScreenIsStandalone provides true) {
            AgentScreen(
                animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                contentPadding = contentPadding,
            )
        }
    }
}

private val DrawerScrimColor
    @Composable inline get() = if (LocalAgentScreenTransparentBackground.current) Color.Transparent else DrawerDefaults.scrimColor

private val DrawerContainerColor
    @Composable inline get() = if (LocalAgentScreenTransparentBackground.current) MaterialTheme.colorScheme.surface else DrawerDefaults.modalContainerColor

private val ContainerColor
    @Composable inline get() = if (LocalAgentScreenTransparentBackground.current) Color.Transparent else Color.Unspecified

private val Color.scaffoldContainer
    @Composable inline get() = takeOrElse { MaterialTheme.colorScheme.background }

private val ScaffoldContentColor
    @Composable inline get() = MaterialTheme.colorScheme.onBackground
