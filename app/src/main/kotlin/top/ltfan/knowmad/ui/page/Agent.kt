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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.R
import top.ltfan.knowmad.ui.component.ConversationList
import top.ltfan.knowmad.ui.component.LLMProviderConfigLazyColumn
import top.ltfan.knowmad.ui.util.AppWindowInsets
import top.ltfan.knowmad.ui.util.copy
import top.ltfan.knowmad.ui.util.only
import top.ltfan.knowmad.ui.util.plus
import top.ltfan.knowmad.ui.viewmodel.LocalAgentViewModel

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
        val viewModel = LocalAgentViewModel.current

        val coroutineScope = rememberCoroutineScope()
        val layoutDirection = LocalLayoutDirection.current

        ModalNavigationDrawer(
            drawerContent = {
                ModalDrawerSheet(
                    viewModel.drawerState,
                    windowInsets = AppWindowInsets.only { start } + contentPadding.copy(
                        layoutDirection,
                        top = 0.dp,
                        end = 0.dp,
                        bottom = 0.dp,
                    ),
                ) {
                    val contentPadding = AppWindowInsets.only { vertical }.asPaddingValues() +
                            contentPadding.copy(
                                layoutDirection,
                                start = 0.dp,
                                end = 0.dp,
                            )
                    ConversationList(contentPadding)
                }
            },
            drawerState = viewModel.drawerState,
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
                    )
                },
                contentWindowInsets = AppWindowInsets,
            ) { }
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
                )
            },
            contentWindowInsets = AppWindowInsets,
        ) {
            val contentPadding = it + contentPadding + PaddingValues(16.dp)

            LLMProviderConfigLazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            )
        }
    }

}

@Serializable
sealed class AgentSubPage : SubPage<AgentSubPage>()
