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

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.launch
import top.ltfan.knowmad.R
import top.ltfan.knowmad.application.KnowmadApplication
import top.ltfan.knowmad.data.chat.ConversationEntity
import top.ltfan.knowmad.ui.page.AgentConfigPage
import top.ltfan.knowmad.ui.theme.TextFieldMaxWidth
import top.ltfan.knowmad.ui.util.AppWindowInsets
import top.ltfan.knowmad.ui.util.SnackbarAction
import top.ltfan.knowmad.ui.util.copy
import top.ltfan.knowmad.ui.util.leadingItemThemedShape
import top.ltfan.knowmad.ui.util.plus
import top.ltfan.knowmad.ui.util.trailingItemThemedShape
import top.ltfan.knowmad.ui.viewmodel.AgentViewModel
import top.ltfan.knowmad.ui.viewmodel.GlobalViewModel
import top.ltfan.knowmad.ui.viewmodel.LocalAgentViewModel
import top.ltfan.knowmad.util.asStringRes
import kotlin.uuid.Uuid

@Composable
fun ConversationList(contentPadding: PaddingValues = PaddingValues()) {
    val viewModel = LocalAgentViewModel.current

    ConversationList(
        state = viewModel.conversationListState,
        currentConversationId = viewModel.currentConversationId,
        onConversationSelected = { viewModel.currentConversationId = it },
        onSettingsClick = { viewModel.backStack.add(AgentConfigPage()) },
        onEditConversation = viewModel::editConversation,
        onDeleteConversation = viewModel::deleteConversation,
        contentPadding = contentPadding,
    )
}

@Composable
fun ConversationList(
    state: PagingLazyListState<Int, ConversationEntity>,
    currentConversationId: Uuid?,
    onConversationSelected: (Uuid?) -> Unit,
    onSettingsClick: () -> Unit,
    onEditConversation: (newEntity: ConversationEntity, onFinished: () -> Unit) -> Unit,
    onDeleteConversation: (conversation: ConversationEntity, onDeleted: (onUndo: () -> Unit) -> Unit) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val coroutineScope = rememberCoroutineScope()

    val conversations = state.flow.collectAsLazyPagingItems()

    val layoutDirection = LocalLayoutDirection.current

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(
                contentPadding.copy(
                    layoutDirection,
                    bottom = 0.dp,
                ),
            ),
        ) {
            Spacer(Modifier.width(4.dp))
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TooltipBox(
                    TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Below,
                    ),
                    tooltip = {
                        PlainTooltip {
                            Text(stringResource(R.string.llm_config_label_settings))
                        }
                    },
                    state = rememberTooltipState(),
                ) {
                    IconButton(
                        onClick = onSettingsClick,
                    ) {
                        Icon(
                            painterResource(R.drawable.settings_24px),
                            contentDescription = stringResource(R.string.llm_config_label_settings),
                        )
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
        }
        Modifier.height(4.dp)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(16.dp) + contentPadding.copy(
                layoutDirection,
                top = 0.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(
                count = conversations.itemCount,
                key = conversations.itemKey { it.id },
            ) { index ->
                val conversation = conversations[index] ?: return@items
                NavigationDrawerItem(
                    label = { Text(conversation.name) },
                    selected = currentConversationId == conversation.id,
                    onClick = { onConversationSelected(conversation.id) },
                    modifier = Modifier.animateItem(),
                    icon = {
                        AnimatedContent(
                            targetState = conversation.isPinned,
                        ) { pinned ->
                            if (!pinned) return@AnimatedContent
                            Icon(
                                painterResource(R.drawable.keep_24px),
                                contentDescription = stringResource(R.string.label_pinned),
                            )
                        }
                    },
                    badge = {
                        Box(Modifier.wrapContentSize(Alignment.TopEnd)) {
                            var showMenu by remember { mutableStateOf(false) }
                            var showDialog by remember { mutableStateOf(false) }
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    painterResource(R.drawable.more_vert_24px),
                                    contentDescription = stringResource(R.string.label_more_options),
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                            ) {
                                DropdownMenuItem(
                                    onClick = {
                                        onEditConversation(
                                            conversation.copy(isPinned = !conversation.isPinned),
                                        ) {}
                                        showMenu = false
                                    },
                                    text = {
                                        Text(stringResource(if (!conversation.isPinned) R.string.label_pin else R.string.label_unpin))
                                    },
                                    shape = MenuDefaults.leadingItemThemedShape,
                                    leadingIcon = {
                                        Icon(
                                            painterResource(if (!conversation.isPinned) R.drawable.keep_24px else R.drawable.keep_off_24px),
                                            contentDescription = null,
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    onClick = {
                                        showDialog = true
                                        showMenu = false
                                    },
                                    text = { Text(stringResource(R.string.label_edit)) },
                                    shape = MenuDefaults.middleItemShape,
                                    leadingIcon = {
                                        Icon(
                                            painterResource(R.drawable.edit_24px),
                                            contentDescription = null,
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    onClick = {
                                        onEditConversation(
                                            conversation.copy(isArchived = true),
                                        ) {
                                            if (currentConversationId == conversation.id) {
                                                onConversationSelected(null)
                                            }
                                            coroutineScope.launch {
                                                GlobalViewModel.showSnackbar(
                                                    message = R.string.label_archived.asStringRes(),
                                                    action = SnackbarAction(R.string.label_undo.asStringRes()) {
                                                        onEditConversation(
                                                            conversation.copy(isArchived = false),
                                                        ) {}
                                                    },
                                                    withDismissAction = true,
                                                    duration = SnackbarDuration.Long,
                                                )
                                            }
                                        }
                                        showMenu = false
                                    },
                                    text = { Text(stringResource(R.string.label_archive)) },
                                    shape = MenuDefaults.middleItemShape,
                                    leadingIcon = {
                                        Icon(
                                            painterResource(R.drawable.archive_24px),
                                            contentDescription = null,
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    onClick = {
                                        onDeleteConversation(conversation) { onUndo ->
                                            if (currentConversationId == conversation.id) {
                                                onConversationSelected(null)
                                            }
                                            coroutineScope.launch {
                                                GlobalViewModel.showSnackbar(
                                                    message = R.string.label_deleted.asStringRes(),
                                                    action = SnackbarAction(
                                                        R.string.label_undo.asStringRes(),
                                                        onClick = onUndo,
                                                    ),
                                                    withDismissAction = true,
                                                    duration = SnackbarDuration.Long,
                                                )
                                            }
                                        }
                                        showMenu = false
                                    },
                                    text = { Text(stringResource(R.string.label_delete)) },
                                    shape = MenuDefaults.trailingItemThemedShape,
                                    leadingIcon = {
                                        Icon(
                                            painterResource(R.drawable.delete_24px),
                                            contentDescription = null,
                                        )
                                    },
                                    colors = MenuDefaults.itemColors(
                                        textColor = MaterialTheme.colorScheme.error,
                                        leadingIconColor = MaterialTheme.colorScheme.error,
                                    ),
                                )
                            }
                            if (showDialog) {
                                ConversationEditingDialog(
                                    conversation = conversation,
                                    onDismissRequest = { showDialog = false },
                                    onConfirm = { newEntity ->
                                        onEditConversation(newEntity) {}
                                        showDialog = false
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun ConversationEditingDialog(
    conversation: ConversationEntity,
    onDismissRequest: () -> Unit,
    onConfirm: (newEntity: ConversationEntity) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    val state = rememberTextFieldState(conversation.name)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(conversation.copy(name = state.text.toString()))
                },
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        title = { Text(stringResource(R.string.agent_conversation_label_edit)) },
        text = {
            ConversationNameTextField(
                state = state,
                focusRequester = focusRequester,
            )

            LaunchedEffect(Unit) {
                state.edit { placeCursorAtEnd() }
                focusRequester.requestFocus()
            }
        },
    )
}

@Composable
fun ConversationNameTextField(
    state: TextFieldState,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    TextField(
        state = state,
        modifier = Modifier
            .widthIn(max = TextFieldMaxWidth)
            .focusRequester(focusRequester),
        label = { Text(stringResource(R.string.agent_conversation_label_name)) },
        lineLimits = TextFieldLineLimits.SingleLine,
    )
}

@Preview
@Composable
fun ConversationListPreview() {
    ApplicationPreview {
        val viewModel = (this as? KnowmadApplication)?.let {
            viewModel<AgentViewModel> {
                AgentViewModel(it)
            }
        } ?: run {
            Text("Preview not available")
            return@ApplicationPreview
        }

        val snackbarHostState = remember { SnackbarHostState() }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = AppWindowInsets,
        ) { contentPadding ->
            CompositionLocalProvider(LocalAgentViewModel provides viewModel) {
                ConversationList(contentPadding)
            }
        }

        SnackbarEffect(snackbarHostState)
    }
}
