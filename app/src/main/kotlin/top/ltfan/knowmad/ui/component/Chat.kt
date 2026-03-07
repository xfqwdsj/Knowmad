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
import ai.koog.prompt.message.ResponseMetaInfo
import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateBounds
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import org.intellij.markdown.ast.ASTNode
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.chat.AssistantMessageContent
import top.ltfan.knowmad.data.chat.AssistantStreamingMessage
import top.ltfan.knowmad.data.chat.AssistantStreamingMessageType
import top.ltfan.knowmad.data.chat.ChatListMessage
import top.ltfan.knowmad.data.chat.ConversationMeta
import top.ltfan.knowmad.data.chat.MessageEntity
import top.ltfan.knowmad.data.chat.MessageEntityRole
import top.ltfan.knowmad.data.chat.MessageWithFilesAndBranchInfo
import top.ltfan.knowmad.data.chat.UiMessage
import top.ltfan.knowmad.data.chat.toUiMessage
import top.ltfan.knowmad.data.llm.LLMConfigEntity
import top.ltfan.knowmad.data.llm.LLMProviderConfigEntity
import top.ltfan.knowmad.model.UnknownLLModel
import top.ltfan.knowmad.ui.util.itemThemedShape
import top.ltfan.knowmad.util.Json
import top.ltfan.knowmad.util.Logger
import top.ltfan.knowmad.util.RemendProcessor
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Composable
fun ChatInput(
    textState: TextFieldState,
    sendEnabled: Boolean,
    onSend: () -> Unit,
    isRunning: Boolean,
    onCancel: () -> Unit,
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
                    .fillMaxWidth()
                    .weight(1f, fill = false),
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
                    onClick = if (isRunning) onCancel else onSend,
                    enabled = sendEnabled || isRunning,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    if (isRunning) {
                        Icon(
                            painterResource(R.drawable.stop_circle_24px),
                            contentDescription = stringResource(R.string.agent_label_stop_generation),
                        )
                        return@IconButton
                    }

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
        },
        trailingButton = {
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

            var expandedProvider by remember { mutableStateOf<LLMProviderConfigEntity?>(null) }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                    expandedProvider = null
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
                                    expandedProvider = provider
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
                            expanded = expandedProvider == provider && models != null,
                            onDismissRequest = { expandedProvider = null },
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
                                        expandedProvider = null
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
    mathJaxRendererState: MathJaxRendererState?,
    modifier: Modifier = Modifier,
    onPrevious: (message: ChatListMessage) -> Unit = {},
    onNext: (message: ChatListMessage) -> Unit = {},
    onRegenerate: (message: ChatListMessage) -> Unit = {},
    initialReasoningVisibility: Boolean = true,
    onAnyReasoningVisibilityChange: (visible: Boolean) -> Unit = {},
    initialToolVisibility: Boolean = true,
    onAnyToolVisibilityChange: (visible: Boolean) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    lazyListState: LazyListState = rememberLazyListState(),
    assistantMessageStates: MutableMap<Any, AssistantMessageState> = remember { mutableStateMapOf() },
    allowAssistantMessageActions: Boolean = true,
    topToBottom: Boolean = false,
    reverseIndexing: Boolean = false,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    runnableCodeComponents: Set<List<String>>? = null,
    runCode: ((
        state: AssistantMessageState,
        contentIndex: Int,
        node: ASTNode,
        components: List<String>,
        code: String,
    ) -> Unit)? = null,
) {
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier,
        state = lazyListState,
        contentPadding = contentPadding,
        reverseLayout = !topToBottom,
        verticalArrangement = verticalArrangement,
    ) {
        items(
            count = getMessageCount(),
            key = getMessageKey,
        ) {
            val index = if (!reverseIndexing) it else getMessageCount() - 1 - it
            val key = getMessageKey(index)
            when (val data = getMessageAt(index) ?: return@items) {
                is AssistantStreamingMessage -> {
                    var previousHeight by remember { mutableStateOf<Int?>(null) }
                    AssistantMessage(
                        state = assistantMessageStates.compute(key) { _, state ->
                            state as? AssistantMessageState.Streaming ?: data.state
                        } ?: error("`null` returned when getting assistant message state."),
                        mathJaxRendererState = mathJaxRendererState,
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
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .animateContentSize()
                            .onSizeChanged { newSize ->
                                val height = newSize.height
                                if (lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset != 0) {
                                    val delta = previousHeight?.let { previousHeight ->
                                        height - previousHeight
                                    } ?: 0
                                    lazyListState.requestScrollToItem(
                                        index = lazyListState.firstVisibleItemIndex,
                                        scrollOffset = lazyListState.firstVisibleItemScrollOffset + delta,
                                    )
                                }
                                previousHeight = height
                            },
                        allowActions = allowAssistantMessageActions,
                        runnableCodeComponents = runnableCodeComponents,
                        runCode = { _, _, _, _ -> },
                    )
                }

                is MessageWithFilesAndBranchInfo -> {
                    val messageEntity = data.message
                    when (messageEntity.role) {
                        Assistant -> {
                            val state = assistantMessageStates[key].let state@{ state ->
                                if (state is AssistantMessageState.Completed) return@state state
                                coroutineScope.launch {
                                    assistantMessageStates[key] = AssistantMessageState.fromEntity(
                                        entity = messageEntity,
                                        existingState = state,
                                    )
                                }
                                state?.let { state -> return@state state }
                                return@items // TODO: animation or placeholder
                            }
                            AssistantMessage(
                                state = state,
                                mathJaxRendererState = mathJaxRendererState,
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
                                allowActions = allowAssistantMessageActions,
                                runnableCodeComponents = runnableCodeComponents,
                                runCode = runCode?.let { runCode ->
                                    { contentIndex: Int, node: ASTNode, components: List<String>, code: String ->
                                        runCode(state, contentIndex, node, components, code)
                                    }
                                },
                            )
                        }

                        User -> Box(
                            modifier = Modifier.fillParentMaxWidth(),
                            contentAlignment = Alignment.TopEnd,
                        ) {
                            UserMessage(
                                content = messageEntity.parts.asSequence()
                                    .filterIsInstance<UiMessage.Koog>()
                                    .filter { uiMessage -> uiMessage.display }
                                    .map { uiMessage -> uiMessage.message }
                                    .filterIsInstance<Message.User>()
                                    .singleOrNull()?.content
                                    ?: return@items,
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
    mathJaxRendererState: MathJaxRendererState?,
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
    allowActions: Boolean = true,
    runnableCodeComponents: Set<List<String>>? = null,
    runCode: ((contentIndex: Int, node: ASTNode, components: List<String>, code: String) -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(modifier) { // TODO: add menu
        for ((index, content) in state.contents.withIndex()) {
            val runners = LocalMarkdownCodeFenceRunners.current
            val updatedIndex by rememberUpdatedState(index)
            val runnersToAppend = remember(runnableCodeComponents, runCode) {
                if (runnableCodeComponents == null || runCode == null) return@remember null
                runnableCodeComponents.associateWith {
                    MarkdownCodeFenceRunner { node, components, code ->
                        runCode(updatedIndex, node, components, code)
                    }
                }
            }
            val providedRunners = runnersToAppend?.plus(runners) ?: runners

            when (content) {
                is Streaming -> {
                    CompositionLocalProvider(
                        LocalMarkdownCodeFenceRunners provides providedRunners,
                        LocalMarkdownRunCodeEnabled provides false,
                    ) {
                        val trailing by content.trailing.collectAsState(null)
                        when (content.type) {
                            Reasoning -> ReasoningMessage(
                                savedMarkdownState = content.markdownState,
                                mathJaxRendererState = mathJaxRendererState,
                                startedAt = content.startedAt,
                                endedAt = content.metaInfo?.timestamp,
                                initialVisibility = initialReasoningVisibility,
                                onVisibilityChange = onAnyReasoningVisibilityChange,
                                modifier = Modifier.padding(8.dp),
                                trailing = trailing,
                            )

                            Content -> AssistantMessageContent(
                                savedMarkdownState = content.markdownState,
                                mathJaxRendererState = mathJaxRendererState,
                                modifier = Modifier.padding(8.dp),
                                trailing = trailing,
                            )
                        }
                    }
                }

                is Completed -> {
                    val uiMessage = content.uiMessage
                    if (!uiMessage.display) continue
                    CompositionLocalProvider(
                        LocalMarkdownCodeFenceRunners provides providedRunners,
                    ) {
                        when (uiMessage) {
                            is Koog -> when (val message = uiMessage.message) {
                                is Reasoning -> ReasoningMessage(
                                    savedMarkdownState = content.markdownState,
                                    mathJaxRendererState = mathJaxRendererState,
                                    startedAt = (message.metaInfo.metadata?.get("startedAt") as? JsonPrimitive)?.contentOrNull?.let {
                                        Instant.parseOrNull(it)
                                    } ?: message.metaInfo.timestamp,
                                    endedAt = message.metaInfo.timestamp,
                                    initialVisibility = initialReasoningVisibility,
                                    onVisibilityChange = onAnyReasoningVisibilityChange,
                                    modifier = Modifier.padding(8.dp),
                                )

                                is Assistant -> AssistantMessageContent(
                                    savedMarkdownState = content.markdownState,
                                    mathJaxRendererState = mathJaxRendererState,
                                    modifier = Modifier.padding(8.dp),
                                )

                                is Tool -> ToolMessage(
                                    message = message,
                                    mathJaxRendererState = mathJaxRendererState,
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

                            is NotDisplayed -> {}
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = allowActions && (total > 1 || state.completed),
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
    mathJaxRendererState: MathJaxRendererState?,
    startedAt: Instant,
    endedAt: Instant?,
    initialVisibility: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
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
                    mathJaxRendererState = mathJaxRendererState,
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp,
                    ),
                    success = { state, components, modifier ->
                        MarkdownSuccessContentWithTrailingText(
                            state = state,
                            components = components,
                            modifier = modifier,
                            trailing = trailing,
                        )
                    },
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
    mathJaxRendererState: MathJaxRendererState?,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    MarkdownView(
        savedMarkdownState,
        mathJaxRendererState = mathJaxRendererState,
        modifier = modifier,
        success = { state, components, modifier ->
            MarkdownSuccessContentWithTrailingText(
                state = state,
                components = components,
                modifier = modifier,
                trailing = trailing,
            )
        },
    )
}

@Composable
fun ToolMessage(
    message: Message.Tool,
    mathJaxRendererState: MathJaxRendererState?,
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

    val prettyJson = remember(message.content) {
        runCatching {
            val element = Json.decodeFromString<JsonElement>(message.content)
            val newJson = Json {
                prettyPrint = true
                prettyPrintIndent = " "
            }
            newJson.encodeToString(element)
        }.getOrNull()
    }

    val content = prettyJson?.let {
        "```json\n$it\n```"
    } ?: "```\n${message.content.trim()}\n```"

    CompositionLocalProvider(
        LocalMarkdownCodeEnableHeader provides (prettyJson != null),
        LocalMarkdownCodeMaxHeight provides 400.dp,
    ) {
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
                        rememberSavedMarkdownState(content),
                        mathJaxRendererState = mathJaxRendererState,
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
    content: String,
    current: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    var menuExpanded by remember { mutableStateOf(false) }
    var menuOriginalOffset by remember { mutableStateOf(Offset.Zero) }
    val menuOffset = remember(density, menuOriginalOffset) {
        with(density) {
            DpOffset(
                x = menuOriginalOffset.x.toDp(),
                y = menuOriginalOffset.y.toDp(),
            )
        }
    }

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
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        menuOriginalOffset = awaitFirstDown().position
                    }
                },
        ) {
            UserMessageContent(
                content = content,
                modifier = Modifier.padding(16.dp),
            )
            Box {
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    offset = menuOffset,
                ) {
                    val coroutineScope = rememberCoroutineScope()
                    val clipboard = LocalClipboard.current

                    DropdownMenuItem(
                        text = { Text(stringResource(android.R.string.copy)) },
                        shape = MenuDefaults.middleItemShape,
                        onClick = {
                            coroutineScope.launch {
                                val clipData = ClipData.newPlainText(null, content)
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
    content: String,
    modifier: Modifier = Modifier,
) {
    Text(
        content,
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
    val conversationId: Uuid
    val parentId: Uuid?
    val depth: Int
    val contents: List<AssistantMessageContent>
    val model: LLModel
    val completed: Boolean
    val createdAt: Instant

    val completedContents: List<AssistantMessageContent.Completed>
        get() = contents.filterIsInstance<AssistantMessageContent.Completed>()

    @Stable
    class Streaming(
        eventFlow: Flow<AssistantMessageStreamingEvent>,
        override val model: LLModel,
        coroutineScope: CoroutineScope,
        override val id: Uuid = Uuid.generateV7(),
        override val conversationId: Uuid,
        override var parentId: Uuid? = null,
        override var depth: Int = 0,
        override val createdAt: Instant = Clock.System.now(),
        remend: RemendProcessor? = null,
        onQueryConversationMeta: suspend () -> ConversationMeta = { ConversationMeta() },
        onUpdateConversationMeta: (ConversationMeta) -> Unit = {},
        onUpdate: Streaming.() -> Unit = {},
    ) : AssistantMessageState {
        private val logger = Logger("StreamingState")

        override val contents = mutableStateListOf<AssistantMessageContent>()
        override var completed by mutableStateOf(false)
        private val completedStateFlow = MutableStateFlow<Completed?>(null)

        init {
            coroutineScope.launch {
                eventFlow.collect { event ->
                    when (event) {
                        is AddString -> {
                            val content = contents.getOrElse(event.partIndex) { index ->
                                require(contents.size == index) { "Parts must be added in order." }

                                (contents.getOrNull(index - 1) as? AssistantMessageContent.Streaming)?.let {
                                    it.metaInfo ?: it.createMetaInfo()
                                } ?: logger.debug {
                                    "Trying to complete previous part at index ${index - 1}, but found the part is not Streaming or does not exist."
                                }

                                val newContent = AssistantMessageContent.Streaming(
                                    type = event.messageType,
                                    flow = MutableStateFlow(""),
                                    model = model,
                                    coroutineScope = coroutineScope,
                                    remend = remend,
                                )
                                contents.add(newContent)
                                onUpdate()
                                newContent
                            } as? AssistantMessageContent.Streaming ?: run {
                                logger.error {
                                    "Received AddString for part index ${event.partIndex}, but the part is not Streaming."
                                }
                                return@collect
                            }

                            val flow = content.flow as? MutableStateFlow<String> ?: return@collect
                            flow.value += event.content
                            onUpdate()
                        }

                        is SetMetaInfo -> {
                            val content =
                                contents.lastOrNull { it is AssistantMessageContent.Streaming } as? AssistantMessageContent.Streaming
                                    ?: run {
                                        logger.error {
                                            "Received SetMetaInfo, but no Streaming content found."
                                        }
                                        return@collect
                                    }
                            val metadata = buildJsonObject {
                                event.metaInfo.metadata?.forEach { put(it.key, it.value) }
                                put("startedAt", JsonPrimitive(content.startedAt.toString()))
                            }
                            content.metaInfo = event.metaInfo.copy(
                                metadata = metadata,
                            )
                            onUpdate()
                        }

                        is SetMessage -> {
                            val index = event.partIndex
                            if (contents.size <= index) {
                                (contents.getOrNull(index - 1) as? AssistantMessageContent.Streaming)?.let {
                                    it.metaInfo ?: it.createMetaInfo()
                                } ?: logger.debug {
                                    "Trying to complete previous part at index ${index - 1}, " + "but found the part is not Streaming or does not exist."
                                }
                            }

                            if (index == contents.size - 1) logger.warn {
                                "Received SetMessage for part index $index, but the part is already existing. Overwriting it."
                            }

                            contents.add(
                                index,
                                AssistantMessageContent.Completed(
                                    uiMessage = event.uiMessage,
                                    coroutineScope = coroutineScope,
                                ),
                            )
                            onUpdate()
                        }

                        is QueryConversationMeta -> event.onResult(onQueryConversationMeta())
                        is UpdateConversationMeta -> onUpdateConversationMeta(
                            event.updateMeta(onQueryConversationMeta()),
                        )

                        is Finish -> cancel()
                    }
                }
            }.invokeOnCompletion {
                it?.let { throwable ->
                    if (throwable is CancellationException) return@let
                    logger.error(throwable) { "Streaming event collection encountered an error." }
                }
                logger.debug { "Streaming event collection completed." }
                coroutineScope.launch {
                    complete()
                    val completedState = Completed(
                        contents = completedContents,
                        model = model,
                        id = id,
                        conversationId = conversationId,
                        parentId = parentId,
                        depth = depth,
                        createdAt = createdAt,
                    )
                    completedStateFlow.value = completedState
                }
            }
        }

        fun completedStateOrNull() = completedStateFlow.value

        suspend fun completeAndGetState(): Completed {
            complete()
            return awaitCompletedState()
        }

        suspend fun awaitCompletedState() = completedStateFlow.filterNotNull().first()

        private suspend fun complete() {
            completed = true
            replaceContentsToCompleted()
        }

        private suspend fun replaceContentsToCompleted(endedAt: Instant = Clock.System.now()) {
            val iterator = contents.listIterator()
            while (iterator.hasNext()) {
                val content = iterator.next()
                if (content is AssistantMessageContent.Streaming) {
                    content.metaInfo ?: content.createMetaInfo()
                    iterator.set(content.completed(endedAt))
                }
            }
            logger.debug { "Replaced streaming contents to completed contents." }
        }

        inline fun cleanUncompletedToolCalls(
            onFound: (
                iterator: MutableListIterator<AssistantMessageContent>,
                toolCall: Message.Tool.Call,
            ) -> Unit,
        ) {
            val iterator = contents.listIterator()
            var pendingToolCall: Message.Tool.Call? = null

            while (iterator.hasNext()) {
                val content = iterator.next()

                if (pendingToolCall != null) {
                    val isResult = (content.uiMessage as? Koog)?.message is Message.Tool.Result

                    if (!isResult) {
                        iterator.previous()
                        onFound(iterator, pendingToolCall)
                        iterator.next()
                        pendingToolCall = null
                    } else {
                        pendingToolCall = null
                    }
                }

                val uiMessage = content.uiMessage
                if (uiMessage is Koog) {
                    val message = uiMessage.message
                    if (message is Message.Tool.Call) {
                        pendingToolCall = message
                    }
                }
            }

            if (pendingToolCall != null) {
                onFound(iterator, pendingToolCall)
            }
        }

        override fun toString(): String {
            return "AssistantMessageState.Streaming(id=$id, conversationId=$conversationId, parentId=$parentId, depth=$depth, model=$model, completed=$completed, createdAt=$createdAt)"
        }
    }

    @Immutable
    data class Completed(
        override val contents: List<AssistantMessageContent.Completed>,
        override val model: LLModel,
        override val id: Uuid,
        override val conversationId: Uuid,
        override val parentId: Uuid? = null,
        override val depth: Int = 0,
        override val createdAt: Instant,
    ) : AssistantMessageState {
        override val completed: Boolean = true
    }

    fun toEntity() = MessageEntity(
        id = id,
        conversationId = conversationId,
        parentId = parentId,
        depth = depth,
        parts = contents.toUiMessageList(),
        role = MessageEntityRole.Assistant,
        generatedBy = model,
        completed = completed,
        createdAt = createdAt,
    )

    private fun List<AssistantMessageContent>.toUiMessageList() = toList() // TODO
        .mapNotNull { content ->
            when (val uiMessage = content.uiMessage) {
                is Koog -> when (uiMessage.message) {
                    is Assistant, is Reasoning, is Tool -> uiMessage
                    else -> null
                }

                else -> uiMessage
            }
        }

    companion object {
        private val logger = Logger("AssistantMessageState")

        suspend fun fromEntity(
            entity: MessageEntity,
            existingState: AssistantMessageState? = null,
        ): Completed {
            val existingCompletedContents = existingState?.completedContents?.toMutableList()
            return Completed(
                contents = entity.parts.mapNotNull { part ->
                    when (part) {
                        is Koog -> when (val message = part.message) {
                            is Assistant, is Reasoning, is Tool -> existingCompletedContents?.find { it.content == message.content }
                                ?.also {
                                    logger.debug {
                                        "Reusing existing Completed content for message: ${
                                            message.content.take(30)
                                        }"
                                    }
                                    existingCompletedContents.remove(it)
                                } ?: AssistantMessageContent.Completed(message = message)

                            else -> null
                        }

                        else -> AssistantMessageContent.Completed(uiMessage = part)
                    }
                },
                model = entity.generatedBy ?: UnknownLLModel,
                id = entity.id,
                conversationId = entity.conversationId,
                parentId = entity.parentId,
                depth = entity.depth,
                createdAt = entity.createdAt,
            )
        }
    }
}

sealed interface AssistantMessageStreamingEvent {
    @Immutable
    data class AddString(
        val partIndex: Int,
        val content: String,
        val messageType: AssistantStreamingMessageType,
    ) : AssistantMessageStreamingEvent

    @Immutable
    data class SetMetaInfo(
        val metaInfo: ResponseMetaInfo,
    ) : AssistantMessageStreamingEvent

    @Immutable
    data class SetMessage(
        val partIndex: Int,
        val uiMessage: UiMessage,
    ) : AssistantMessageStreamingEvent {
        constructor(
            partIndex: Int,
            message: Message,
        ) : this(
            partIndex,
            message.toUiMessage(),
        )
    }

    @Immutable
    data class QueryConversationMeta(
        val onResult: (ConversationMeta) -> Unit,
    ) : AssistantMessageStreamingEvent

    @Immutable
    data class UpdateConversationMeta(
        val updateMeta: (ConversationMeta) -> ConversationMeta,
    ) : AssistantMessageStreamingEvent

    data object Finish : AssistantMessageStreamingEvent
}
