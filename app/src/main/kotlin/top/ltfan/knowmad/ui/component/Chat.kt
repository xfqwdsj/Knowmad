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
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateBounds
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.toStdlibInstant
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.chat.AssistantMessageContent
import top.ltfan.knowmad.data.chat.AssistantStreamingMessage
import top.ltfan.knowmad.data.chat.AssistantStreamingMessageType
import top.ltfan.knowmad.data.chat.ChatListMessage
import top.ltfan.knowmad.data.chat.MessageWithFilesAndBranchInfo
import top.ltfan.knowmad.data.chat.UiMessage
import top.ltfan.knowmad.data.llm.LLMConfigEntity
import top.ltfan.knowmad.data.llm.LLMProviderConfigEntity
import top.ltfan.knowmad.ui.theme.ProvideCompatibleShapes
import top.ltfan.knowmad.ui.util.itemThemedShape
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Composable
fun ChatInput(
    textState: TextFieldState,
    sendEnabled: Boolean,
    onSend: () -> Unit,
    providers: List<LLMProviderConfigEntity>,
    getModels: suspend (provider: LLMProviderConfigEntity) -> List<LLMConfigEntity>,
    selectedModel: LLMConfigEntity?,
    onSelectModel: (LLMConfigEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .then(modifier)
            .padding(8.dp),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                state = textState,
                modifier = Modifier
                    .fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    errorBorderColor = Color.Transparent,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(4.dp))
                ChatModelSelector(
                    providers = providers,
                    getModels = getModels,
                    selectedModel = selectedModel,
                    onSelectModel = onSelectModel,
                )
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
fun ChatModelSelector(
    providers: List<LLMProviderConfigEntity>,
    getModels: suspend (provider: LLMProviderConfigEntity) -> List<LLMConfigEntity>,
    selectedModel: LLMConfigEntity?,
    onSelectModel: (LLMConfigEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()

    val size = SplitButtonDefaults.ExtraSmallContainerHeight
    val colors = ButtonDefaults.textButtonColors()
    val border = null

    var expanded by remember { mutableStateOf(false) }

    SplitButtonLayout(
        leadingButton = {
            ProvideCompatibleShapes {
                SplitButtonDefaults.LeadingButton(
                    onClick = { expanded = true },
                    shapes = SplitButtonDefaults.leadingButtonShapesFor(size),
                    colors = colors,
                    border = border,
                    contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(size),
                ) {
                    SharedTransitionLayout {
                        Text(
                            selectedModel?.name
                                ?: stringResource(R.string.chat_input_model_label_select),
                            modifier = Modifier
                                .animateBounds(this)
                                .skipToLookaheadSize { true },
                        )
                    }
                }
            }
        },
        trailingButton = {
            ProvideCompatibleShapes {
                SplitButtonDefaults.TrailingButton(
                    checked = expanded,
                    onCheckedChange = { expanded = it },
                    shapes = SplitButtonDefaults.trailingButtonShapesFor(size),
                    colors = colors,
                    border = border,
                    contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(size),
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_drop_down_24px),
                        contentDescription = stringResource(
                            if (!expanded) R.string.chat_input_model_label_expand
                            else R.string.chat_input_model_label_collapse,
                        ),
                    )
                }
            }

            var modelsExpanded by remember { mutableStateOf(false) }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                    modelsExpanded = false
                },
            ) {
                if (providers.isEmpty()) {
                    DropdownMenuItem(
                        onClick = {},
                        text = { Text(stringResource(R.string.chat_input_model_label_no_providers)) },
                        enabled = false,
                    )
                    return@DropdownMenu
                }

                providers.forEachIndexed { index, provider ->
                    var models by remember { mutableStateOf<List<LLMConfigEntity>?>(null) }

                    val density = LocalDensity.current
                    val layoutDirection = LocalLayoutDirection.current
                    var offset by remember { mutableStateOf(DpOffset.Zero) }

                    Box(contentAlignment = Alignment.BottomEnd) {
                        DropdownMenuItem(
                            onClick = {
                                coroutineScope.launch {
                                    models = getModels(provider)
                                    modelsExpanded = true
                                }
                            },
                            text = { Text(provider.name) },
                            shape = MenuDefaults.itemThemedShape(
                                index,
                                providers.size,
                            ),
                            modifier = Modifier.onGloballyPositioned {
                                with(density) {
                                    val factor = if (layoutDirection == Ltr) 1 else -1
                                    offset = DpOffset(
                                        it.size.width.toDp() * factor,
                                        0.dp,
                                    )
                                }
                            },
                        )

                        DropdownMenu(
                            expanded = modelsExpanded && models != null,
                            onDismissRequest = { modelsExpanded = false },
                            offset = offset,
                        ) {
                            val models = models

                            if (models.isNullOrEmpty()) {
                                DropdownMenuItem(
                                    onClick = {},
                                    text = { Text(stringResource(R.string.chat_input_model_label_no_models)) },
                                    enabled = false,
                                )
                                return@DropdownMenu
                            }

                            models.forEachIndexed { modelIndex, model ->
                                DropdownMenuItem(
                                    onClick = {
                                        onSelectModel(model)
                                        modelsExpanded = false
                                        expanded = false
                                    },
                                    text = { Text(model.name) },
                                    shape = MenuDefaults.itemThemedShape(
                                        modelIndex,
                                        models.size,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
fun ChatMessageList(
    getMessageCount: () -> Int,
    getMessageKey: (index: Int) -> Any,
    getMessageAt: (index: Int) -> ChatListMessage?,
    onPrevious: (message: ChatListMessage) -> Unit,
    onNext: (message: ChatListMessage) -> Unit,
    onRegenerate: (message: ChatListMessage) -> Unit,
    initialReasoningVisibility: Boolean,
    onAnyReasoningVisibilityChange: (visible: Boolean) -> Unit,
    initialToolVisibility: Boolean,
    onAnyToolVisibilityChange: (visible: Boolean) -> Unit,
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
            when (val data = getMessageAt(index) ?: return@items) {
                is AssistantStreamingMessage -> {
                    assistantMessageStates.compute(key) { _, state ->
                        state as? AssistantMessageState.Streaming ?: data.state
                    }
                    AssistantMessage(
                        state = assistantMessageStates.compute(key) { _, state ->
                            state as? AssistantMessageState.Streaming ?: data.state
                        } ?: error("`null` returned when getting assistant message state."),
                        current = data.branchIndex,
                        total = data.branchCount,
                        onPrevious = { onPrevious(data) },
                        onNext = { onNext(data) },
                        onRegenerate = { onRegenerate(data) },
                        initialReasoningVisibility = initialReasoningVisibility,
                        onAnyReasoningVisibilityChange = { visible ->
                            onAnyReasoningVisibilityChange(visible)
                        },
                        initialToolVisibility = initialToolVisibility,
                        onAnyToolVisibilityChange = { visible ->
                            onAnyToolVisibilityChange(visible)
                        },
                        modifier = Modifier.fillParentMaxWidth(),
                    )
                }

                is MessageWithFilesAndBranchInfo -> {
                    val messageEntity = data.message
                    when (messageEntity.role) {
                        Assistant -> AssistantMessage(
                            state = assistantMessageStates.compute(key) { _, state ->
                                state as? AssistantMessageState.Completed
                                    ?: AssistantMessageState.Completed(
                                        contents = messageEntity.parts.mapNotNull { part ->
                                            when (part) {
                                                is Koog -> when (val message = part.message) {
                                                    is Assistant, is Reasoning, is Tool -> AssistantMessageContent.Completed(
                                                        message = message,
                                                        coroutineScope = coroutineScope,
                                                    )

                                                    else -> null
                                                }

                                                else -> AssistantMessageContent.Completed(
                                                    message = part,
                                                    coroutineScope = coroutineScope,
                                                )
                                            }
                                        },
                                        id = messageEntity.id,
                                    )
                            } ?: error("`null` returned when getting assistant message state."),
                            current = data.branchIndex,
                            total = data.branchCount,
                            onPrevious = { onPrevious(data) },
                            onNext = { onNext(data) },
                            onRegenerate = { onRegenerate(data) },
                            initialReasoningVisibility = initialReasoningVisibility,
                            onAnyReasoningVisibilityChange = { visible ->
                                onAnyReasoningVisibilityChange(visible)
                            },
                            initialToolVisibility = initialToolVisibility,
                            onAnyToolVisibilityChange = { visible ->
                                onAnyToolVisibilityChange(visible)
                            },
                            modifier = Modifier.fillParentMaxWidth(),
                        )

                        User -> Box(
                            modifier = Modifier.fillParentMaxWidth(),
                            contentAlignment = Alignment.TopEnd,
                        ) {
                            UserMessage(
                                state = userMessageStates.getOrPut(key) {
                                    UserMessageState(
                                        message = messageEntity.parts.asSequence()
                                            .filterIsInstance<UiMessage.Koog>()
                                            .map { uiMessage -> uiMessage.message }
                                            .filterIsInstance<Message.User>()
                                            .firstOrNull()
                                            ?: error("User message must contain a User part."),
                                        coroutineScope = coroutineScope,
                                    )
                                },
                                current = data.branchIndex,
                                total = data.branchCount,
                                onPrevious = { onPrevious(data) },
                                onNext = { onNext(data) },
                            )
                        }

                        else -> {}
                    }
                }
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
                is Streaming -> {
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

                is Completed -> when (val uiMessage = content.uiMessage) {
                    is Koog -> when (val message = uiMessage.message) {
                        is Reasoning -> ReasoningMessage(
                            savedMarkdownState = content.markdownState,
                            startedAt = (message.metaInfo.metadata?.get("startedAt") as? JsonPrimitive)?.contentOrNull
                                ?.let { Instant.parseOrNull(it) }
                                ?: message.metaInfo.timestamp.toStdlibInstant(),
                            endedAt = message.metaInfo.timestamp.toStdlibInstant(),
                            initialVisibility = initialReasoningVisibility,
                            onVisibilityChange = onAnyReasoningVisibilityChange,
                            modifier = Modifier.padding(8.dp),
                        )

                        is Assistant -> AssistantMessageContent(
                            savedMarkdownState = content.markdownState,
                            modifier = Modifier.padding(8.dp),
                        )

                        is Tool -> ToolMessage(
                            message = message,
                            modifier = Modifier.padding(8.dp),
                            initialVisibility = initialToolVisibility,
                            onVisibilityChange = onAnyToolVisibilityChange,
                        )

                        else -> {}
                    }

                    is UiMessage.Error -> ErrorMessage(
                        uiMessage.content,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = total > 1 || state.completed,
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
                            is Streaming -> it.flow.value
                            is Completed -> when (val uiMessage = it.uiMessage) {
                                is Koog -> when (val message = uiMessage.message) {
                                    is Assistant, is Reasoning, is Tool -> message.content
                                    else -> ""
                                }

                                else -> uiMessage.content
                            }
                        }
                    }
                },
                onRegenerate = onRegenerate,
                indicatorVisible = total > 1,
                otherActionsVisible = state.completed,
            )
        }
        if (state.completed) {
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
    indicatorVisible: Boolean = true,
    indicatorEnabled: Boolean = indicatorVisible,
    otherActionsVisible: Boolean = true,
    otherActionsEnabled: Boolean = otherActionsVisible,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = indicatorVisible,
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
        ) {
            MessageBranchIndicator(
                current = current,
                total = total,
                onPrevious = onPrevious,
                onNext = onNext,
                enabled = indicatorEnabled,
            )
        }
        AnimatedVisibility(
            visible = otherActionsVisible,
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CopyIconButton(
                    onCopy = onCopy,
                    enabled = otherActionsEnabled,
                )
                RetryIconButton(
                    onRetry = onRegenerate,
                    enabled = otherActionsEnabled,
                    contentDescriptionRes = R.string.chat_message_action_label_retry,
                )
            }
        }
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
        SharedTransitionLayout {
            Text(
                stringResource(R.string.chat_message_branch_indicator, current, total),
                modifier = Modifier
                    .animateBounds(this)
                    .skipToLookaheadSize { true },
            )
        }
        ForwardChevronIconButton(
            onClick = onNext,
            enabled = enabled && current < total,
            contentDescriptionRes = R.string.chat_message_branch_label_next,
        )
    }
}

@Composable
fun ErrorMessage(
    message: String,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.error_24px),
                contentDescription = null,
            )
            Text(message)
            Spacer(Modifier.weight(1f))
            if (onDelete != null) {
                DeleteIconButton(onDelete)
            }
        }
    }
}

sealed interface AssistantMessageState {
    val id: Uuid
    val contents: List<AssistantMessageContent>
    val completed: Boolean

    @Stable
    class Streaming(
        eventFlow: Flow<AssistantMessageStreamingEvent>,
        model: LLModel?,
        coroutineScope: CoroutineScope,
        override val id: Uuid = Uuid.generateV7(),
    ) : AssistantMessageState {
        override val contents = mutableStateListOf<AssistantMessageContent>()
        override var completed by mutableStateOf(false)
        private val completedlyFinished = MutableStateFlow(false)

        init {
            coroutineScope.launch {
                eventFlow.collect { event ->
                    when (event) {
                        is AddString -> {
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

                        is SetMessage -> {
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
                                AssistantMessageContent.Completed(
                                    message = event.message,
                                    coroutineScope = coroutineScope,
                                ),
                            )
                        }

                        is Finish -> {
                            completed = true
                            replaceContentsToCompleted()
                            completedlyFinished.value = true
                            cancel()
                        }
                    }
                }
            }
        }

        val completedContents inline get() = contents.filterIsInstance<AssistantMessageContent.Completed>()

        fun completedStateOrNull() = if (completedlyFinished.value) {
            Completed(completedContents, id)
        } else {
            null
        }

        suspend fun completeAndGetState(): Completed {
            complete()
            return awaitCompletedState()
        }

        suspend fun awaitCompletedState(): Completed {
            completedlyFinished.first { it }
            return Completed(completedContents, id)
        }

        private suspend fun complete() {
            completed = true
            replaceContentsToCompleted()
            completedlyFinished.value = true
        }

        private suspend fun replaceContentsToCompleted(endedAt: Instant = Clock.System.now()) {
            val iterator = contents.listIterator()
            while (iterator.hasNext()) {
                val content = iterator.next()
                if (content is AssistantMessageContent.Streaming) {
                    content.endedAt = endedAt
                    iterator.set(content.completed(endedAt))
                }
            }
        }
    }

    @Immutable
    data class Completed(
        override val contents: List<AssistantMessageContent.Completed>,
        override val id: Uuid = Uuid.generateV7(),
    ) : AssistantMessageState {
        override val completed: Boolean = true
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
