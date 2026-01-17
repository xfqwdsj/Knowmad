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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.toStdlibInstant
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.chat.AssistantMessageContent
import top.ltfan.knowmad.data.chat.AssistantStreamingMessageType
import top.ltfan.knowmad.data.chat.MessageEntityRole
import top.ltfan.knowmad.data.chat.MessageWithFilesAndBranchInfo
import top.ltfan.knowmad.ui.theme.TextFieldMaxWidth
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Composable
fun ChatInput(
    textState: TextFieldState,
    sendEnabled: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .widthIn(max = TextFieldMaxWidth)
            .padding(8.dp),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            BasicTextField(
                state = textState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                textStyle = LocalTextStyle.current,
                decorator = {
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (textState.text.isEmpty()) {
                            Text(
                                stringResource(R.string.chat_input_placeholder),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        it()
                    }
                },
            )
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onSend,
                    enabled = sendEnabled,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Icon(
                        painterResource(R.drawable.send_24px),
                        contentDescription = stringResource(R.string.chat_input_label_send),
                    )
                }
            }
        }
    }
}

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
    topToBottom: Boolean = false,
) {
    val coroutineScope = rememberCoroutineScope()

    val assistantMessageStates = remember { mutableStateMapOf<Any, AssistantMessageState>() }
    val userMessageStates = remember { mutableStateMapOf<Any, UserMessageState>() }

    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        reverseLayout = !topToBottom,
    ) {
        items(
            count = getMessageCount(),
            key = getMessageKey,
        ) {
            val index = getMessageCount() - 1 - it
            val key = getMessageKey(index)
            val data = getMessageAt(index)

            val messageEntity = data.message
            when (messageEntity.role) {
                MessageEntityRole.Assistant -> {
                    AssistantMessage(
                        state = assistantMessageStates.getOrPut(key) {
                            AssistantMessageState.Finished(
                                messageEntity.parts.mapNotNull { part ->
                                    when (part) {
                                        is Message.Assistant, is Message.Reasoning, is Message.Tool -> AssistantMessageContent.Finished(
                                            message = part,
                                            coroutineScope = coroutineScope,
                                        )

                                        is Message.System, is Message.User -> null
                                    }
                                },
                            )
                        },
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
                            state = userMessageStates.getOrPut(key) {
                                UserMessageState(
                                    message = messageEntity.parts.filterIsInstance<Message.User>()
                                        .firstOrNull()
                                        ?: error("User message must contain a User part."),
                                    coroutineScope = coroutineScope,
                                )
                            },
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
    state: AssistantMessageState,
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
        for (content in state.contents) {
            when (content) {
                is AssistantMessageContent.Streaming -> {
                    when (content.type) {
                        AssistantStreamingMessageType.Reasoning -> ReasoningMessage(
                            savedMarkdownState = content.markdownState,
                            startedAt = content.startedAt,
                            endedAt = content.endedAt,
                            initialVisibility = initialReasoningVisibility,
                            onVisibilityChange = onAnyReasoningVisibilityChange,
                            modifier = Modifier.padding(8.dp),
                        )

                        AssistantStreamingMessageType.Content -> AssistantMessageContent(
                            savedMarkdownState = content.markdownState,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }

                is AssistantMessageContent.Finished -> when (val message = content.message) {
                    is Message.Reasoning -> ReasoningMessage(
                        savedMarkdownState = content.markdownState,
                        startedAt = (message.metaInfo.metadata?.get("startedAt") as? JsonPrimitive)?.contentOrNull
                            ?.let { Instant.parseOrNull(it) }
                            ?: message.metaInfo.timestamp.toStdlibInstant(),
                        endedAt = message.metaInfo.timestamp.toStdlibInstant(),
                        initialVisibility = initialReasoningVisibility,
                        onVisibilityChange = onAnyReasoningVisibilityChange,
                        modifier = Modifier.padding(8.dp),
                    )

                    is Message.Assistant -> AssistantMessageContent(
                        savedMarkdownState = content.markdownState,
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
        }
        AnimatedVisibility(
            visible = state.finished,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            AssistantMessageActions(
                current = current,
                total = total,
                onPrevious = onPrevious,
                onNext = onNext,
                onCopy = {
                    null to state.contents.joinToString("\n") {
                        when (it) {
                            is AssistantMessageContent.Streaming -> it.flow.value
                            is AssistantMessageContent.Finished -> when (val message = it.message) {
                                is Message.Assistant -> message.content
                                is Message.Reasoning -> message.content
                                is Message.Tool -> message.content
                                is Message.System, is Message.User -> ""
                            }
                        }
                    }
                },
                onRegenerate = onRegenerate,
                enabled = state.finished,
            )
        }
        if (state.finished) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                // TODO
            }
        }
    }
}

@Composable
fun ReasoningMessage(
    savedMarkdownState: SavedMarkdownState,
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
                    savedMarkdownState,
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
    savedMarkdownState: SavedMarkdownState,
    modifier: Modifier = Modifier,
) {
    MarkdownView(
        savedMarkdownState,
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
    enabled: Boolean = true,
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
            enabled = enabled,
        )
        RetryIconButton(
            onRetry = onRegenerate,
            enabled = enabled,
            contentDescriptionRes = R.string.chat_message_action_label_retry,
        )
    }
}

@Composable
fun UserMessage(
    state: UserMessageState,
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
                savedMarkdownState = state.savedMarkdownState,
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
                            val clipData = ClipData.newPlainText(null, state.message.content)
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
    savedMarkdownState: SavedMarkdownState,
    modifier: Modifier = Modifier,
) {
    MarkdownView(
        savedMarkdownState = savedMarkdownState,
        modifier = modifier,
    )
}

@Composable
fun MessageBranchIndicator(
    current: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackChevronIconButton(
            onClick = onPrevious,
            enabled = enabled && current > 1,
            contentDescriptionRes = R.string.chat_message_branch_label_previous,
        )
        Text(stringResource(R.string.chat_message_branch_indicator, current, total))
        ForwardChevronIconButton(
            onClick = onNext,
            enabled = enabled && current < total,
            contentDescriptionRes = R.string.chat_message_branch_label_next,
        )
    }
}

sealed interface AssistantMessageState {
    val contents: List<AssistantMessageContent>
    val finished: Boolean

    @Stable
    class Streaming(
        eventFlow: Flow<AssistantMessageStreamingEvent>,
        model: LLModel?,
        coroutineScope: CoroutineScope,
    ) : AssistantMessageState {
        override val contents = mutableStateListOf<AssistantMessageContent>()
        override var finished by mutableStateOf(false)

        init {
            coroutineScope.launch {
                eventFlow.collect { event ->
                    when (event) {
                        is AssistantMessageStreamingEvent.AddString -> {
                            val content = contents.getOrElse(event.partIndex) { index ->
                                require(contents.size == index) { "Parts must be added in order." }

                                (contents.getOrNull(index - 1) as? AssistantMessageContent.Streaming)?.let {
                                    if (it.endedAt == null) {
                                        it.endedAt = Clock.System.now()
                                    }
                                }

                                val newContent = AssistantMessageContent.Streaming(
                                    type = event.messageType,
                                    flow = MutableStateFlow(""),
                                    model = model,
                                    coroutineScope = coroutineScope,
                                )
                                contents.add(newContent)
                                newContent
                            } as? AssistantMessageContent.Streaming ?: return@collect

                            val flow = content.flow as? MutableStateFlow<String> ?: return@collect
                            flow.value += event.content
                        }

                        is AssistantMessageStreamingEvent.SetMessage -> {
                            val index = event.partIndex
                            if (contents.size <= index) {
                                (contents.getOrNull(index - 1) as? AssistantMessageContent.Streaming)?.let {
                                    if (it.endedAt == null) {
                                        it.endedAt = Clock.System.now()
                                    }
                                }
                            }

                            contents.add(
                                index,
                                AssistantMessageContent.Finished(
                                    message = event.message,
                                    coroutineScope = coroutineScope,
                                ),
                            )
                        }

                        AssistantMessageStreamingEvent.Finish -> {
                            finished = true
                            val endedAt = Clock.System.now()
                            contents.asSequence()
                                .filterIsInstance<AssistantMessageContent.Streaming>()
                                .forEach {
                                    if (it.endedAt == null) {
                                        it.endedAt = endedAt
                                    }
                                }
                        }
                    }
                }
            }
        }
    }

    @Immutable
    data class Finished(
        override val contents: List<AssistantMessageContent.Finished>,
    ) : AssistantMessageState {
        override val finished: Boolean = true
    }
}

@Immutable
class UserMessageState(
    val message: Message.User,
    coroutineScope: CoroutineScope,
) {
    val savedMarkdownState = SavedMarkdownState(coroutineScope, flowOf(message.content))
}

sealed interface AssistantMessageStreamingEvent {
    @Immutable
    data class AddString(
        val partIndex: Int,
        val content: String,
        val messageType: AssistantStreamingMessageType,
    ) : AssistantMessageStreamingEvent

    @Immutable
    data class SetMessage(
        val partIndex: Int,
        val message: Message,
    ) : AssistantMessageStreamingEvent

    data object Finish : AssistantMessageStreamingEvent
}
