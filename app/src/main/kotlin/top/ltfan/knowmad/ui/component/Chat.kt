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

import ai.koog.prompt.message.Message
import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.toStdlibInstant
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.chat.MessageEntityRole
import top.ltfan.knowmad.data.chat.MessageWithFilesAndBranchInfo
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Composable
fun ChatMessageList(
    getMessageCount: () -> Int,
    getMessageKey: (index: Int) -> Any,
    getMessageAt: (index: Int) -> MessageWithFilesAndBranchInfo,
    onPrevious: (index: Int) -> Unit,
    onNext: (index: Int) -> Unit,
    onRegenerate: (index: Int) -> Unit,
    initialReasoningVisibility: Boolean,
    onAnyReasoningVisibilityChange: (index: Int, visible: Boolean) -> Unit,
    initialToolVisibility: Boolean,
    onAnyToolVisibilityChange: (index: Int, visible: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    reverseLayout: Boolean = false,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
    ) {
        items(
            count = getMessageCount(),
            key = getMessageKey,
        ) {
            val data = getMessageAt(it)

            val messageEntity = data.message
            when (messageEntity.role) {
                MessageEntityRole.Assistant -> {
                    AssistantMessage(
                        parts = messageEntity.parts,
                        current = data.branchIndex,
                        total = data.branchCount,
                        onPrevious = { onPrevious(it) },
                        onNext = { onNext(it) },
                        onRegenerate = { onRegenerate(it) },
                        initialReasoningVisibility = initialReasoningVisibility,
                        onAnyReasoningVisibilityChange = { visible ->
                            onAnyReasoningVisibilityChange(it, visible)
                        },
                        initialToolVisibility = initialToolVisibility,
                        onAnyToolVisibilityChange = { visible ->
                            onAnyToolVisibilityChange(it, visible)
                        },
                        modifier = Modifier.fillParentMaxWidth(),
                    )
                }

                MessageEntityRole.User -> {
                    Box(
                        modifier = Modifier.fillParentMaxWidth(),
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        UserMessage(
                            message = messageEntity.parts.filterIsInstance<Message.User>()
                                .firstOrNull() ?: return@items,
                            current = data.branchIndex,
                            total = data.branchCount,
                            onPrevious = { onPrevious(it) },
                            onNext = { onNext(it) },
                        )
                    }
                }

                MessageEntityRole.System -> {}
            }
        }
    }
}

@Composable
fun AssistantMessage(
    parts: List<Message>,
    current: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRegenerate: () -> Unit,
    initialReasoningVisibility: Boolean,
    onAnyReasoningVisibilityChange: (Boolean) -> Unit,
    initialToolVisibility: Boolean,
    onAnyToolVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(modifier) { // TODO: add menu
        for (message in parts) {
            when (message) {
                is Message.Reasoning -> ReasoningMessage(
                    message = message,
                    startedAt = (message.metaInfo.metadata?.get("startedAt") as? JsonPrimitive)?.contentOrNull
                        ?.let { Instant.parseOrNull(it) }
                        ?: message.metaInfo.timestamp.toStdlibInstant(),
                    endedAt = message.metaInfo.timestamp.toStdlibInstant(),
                    initialVisibility = initialReasoningVisibility,
                    onVisibilityChange = onAnyReasoningVisibilityChange,
                    modifier = Modifier.padding(8.dp),
                )

                is Message.Assistant -> AssistantMessageContent(
                    message = message,
                    modifier = Modifier.padding(8.dp),
                )

                is Message.Tool -> ToolMessage(
                    message = message,
                    modifier = Modifier.padding(8.dp),
                    initialVisibility = initialToolVisibility,
                    onVisibilityChange = onAnyToolVisibilityChange,
                )

                is Message.System, is Message.User -> {}
            }
        }
        AssistantMessageActions(
            current = current,
            total = total,
            onPrevious = onPrevious,
            onNext = onNext,
            onCopy = {
                val combinedText = buildString {
                    for (message in parts) {
                        when (message) {
                            is Message.Assistant -> append(message.content)
                            is Message.Reasoning -> append(message.content)
                            is Message.Tool -> append(message.content)
                            is Message.System, is Message.User -> {}
                        }
                        append("\n")
                    }
                }.trim()
                null to combinedText
            },
            onRegenerate = onRegenerate,
        )
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            // TODO
        }
    }
}

@Composable
fun ReasoningMessage(
    message: Message.Reasoning,
    startedAt: Instant,
    endedAt: Instant,
    initialVisibility: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ReasoningMessage(
        flow = remember(message) { flowOf(message.content) },
        startedAt = startedAt,
        endedAt = endedAt,
        initialVisibility = initialVisibility,
        onVisibilityChange = onVisibilityChange,
        modifier = modifier,
    )
}

@Composable
fun ReasoningMessage(
    flow: Flow<String>,
    startedAt: Instant,
    endedAt: Instant?,
    initialVisibility: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(initialVisibility) }

    val isEnded = endedAt != null
    var endedAt by remember(endedAt) { mutableStateOf(endedAt ?: Clock.System.now()) }

    val duration = remember(startedAt, endedAt) {
        (endedAt - startedAt).inWholeSeconds.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExpandOrCollapseIconButton(
                    expanded = visible,
                    onExpandedChange = {
                        visible = it
                        onVisibilityChange(it)
                    },
                    expandedContentDescriptionRes = R.string.chat_message_reasoning_label_collapse,
                    collapsedContentDescriptionRes = R.string.chat_message_reasoning_label_expand,
                )
                Text(
                    pluralStringResource(
                        if (!isEnded) R.plurals.chat_message_reasoning_label_in_progress
                        else R.plurals.chat_message_reasoning_label_completed,
                        duration, duration,
                    ),
                )
            }
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.fillMaxWidth(),
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                MarkdownView(
                    rememberSavedMarkdownState(flow),
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp,
                    ),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        while (!isEnded) {
            delay(1.seconds)
            endedAt = Clock.System.now()
        }
    }
}

@Composable
fun AssistantMessageContent(
    message: Message.Assistant,
    modifier: Modifier = Modifier,
) {
    AssistantMessageContent(
        flow = remember(message) { flowOf(message.content) },
        modifier = modifier,
    )
}

@Composable
fun AssistantMessageContent(
    flow: Flow<String>,
    modifier: Modifier = Modifier,
) {
    MarkdownView(
        rememberSavedMarkdownState(flow),
        modifier = modifier,
    )
}

@Composable
fun ToolMessage(
    message: Message.Tool,
    initialVisibility: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(initialVisibility) }

    val label = stringResource(
        when (message) {
            is Message.Tool.Call -> R.string.chat_message_tool_label_call
            is Message.Tool.Result -> R.string.chat_message_tool_label_result
        },
        message.tool,
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExpandOrCollapseIconButton(
                    expanded = visible,
                    onExpandedChange = {
                        visible = it
                        onVisibilityChange(it)
                    },
                )
                Text(label)
            }
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.fillMaxWidth(),
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                MarkdownView(
                    rememberSavedMarkdownState(
                        """
                            ```
                            ${message.content.trim()}
                            ```
                        """.trimIndent(),
                    ),
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp,
                    ),
                )
            }
        }
    }
}

@Composable
fun AssistantMessageActions(
    current: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCopy: () -> Pair<CharSequence?, CharSequence>,
    onRegenerate: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (total > 1) {
            MessageBranchIndicator(
                current = current,
                total = total,
                onPrevious = onPrevious,
                onNext = onNext,
            )
        }
        CopyIconButton(
            onCopy = onCopy,
        )
        RetryIconButton(
            onRetry = onRegenerate,
            contentDescriptionRes = R.string.chat_message_action_label_retry,
        )
    }
}

@Composable
fun UserMessage(
    message: Message.User,
    current: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Box(
            modifier = modifier
                .padding(8.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .combinedClickable(
                    onLongClick = { menuExpanded = true },
                    onClick = {},
                ),
            contentAlignment = Alignment.TopEnd,
        ) {
            UserMessageContent(
                message = message,
                modifier = Modifier.padding(16.dp),
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                val coroutineScope = rememberCoroutineScope()
                val clipboard = LocalClipboard.current

                DropdownMenuItem(
                    text = { Text(stringResource(android.R.string.copy)) },
                    shape = MenuDefaults.middleItemShape,
                    onClick = {
                        coroutineScope.launch {
                            val clipData = ClipData.newPlainText(null, message.content)
                            clipboard.setClipEntry(ClipEntry(clipData))
                        }
                        menuExpanded = false
                    },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.content_copy_24px),
                            contentDescription = null,
                        )
                    },
                )
            }
        }
        if (total > 1) {
            MessageBranchIndicator(
                current = current,
                total = total,
                onPrevious = onPrevious,
                onNext = onNext,
            )
        }
    }
}

@Composable
fun UserMessageContent(
    message: Message.User,
    modifier: Modifier = Modifier,
) {
    MarkdownView(
        rememberSavedMarkdownState(message.content),
        modifier = modifier,
    )
}

@Composable
fun MessageBranchIndicator(
    current: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackChevronIconButton(
            onClick = onPrevious,
            enabled = current > 1,
            contentDescriptionRes = R.string.chat_message_branch_label_previous,
        )
        Text(stringResource(R.string.chat_message_branch_indicator, current, total))
        ForwardChevronIconButton(
            onClick = onNext,
            enabled = current < total,
            contentDescriptionRes = R.string.chat_message_branch_label_next,
        )
    }
}
