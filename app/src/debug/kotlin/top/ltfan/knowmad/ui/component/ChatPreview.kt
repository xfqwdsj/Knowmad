/*
 * Knowmad - Knowledge nomad
 * Copyright (C) 2026 LTFan (aka xfqwdsj)
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

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.toDeprecatedClock
import kotlinx.datetime.toDeprecatedInstant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import top.ltfan.knowmad.data.chat.AssistantStreamingMessageType
import top.ltfan.knowmad.data.chat.MessageEntity
import top.ltfan.knowmad.data.chat.MessageEntityRole
import top.ltfan.knowmad.data.chat.MessageWithFilesAndBranchInfo
import top.ltfan.knowmad.ui.theme.AppTheme
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

@Preview
@Composable
fun ChatInputPreviewEnabled(
    @PreviewParameter(ChatPreviewStateProvider::class) state: ChatPreviewState,
) {
    AppTheme {
        Surface(
            color = MaterialTheme.colorScheme.background,
        ) {
            ChatInput(
                textState = rememberTextFieldState(state.text),
                sendEnabled = state.sendEnabled,
                onSend = {},
            )
        }
    }
}

class ChatPreviewStateProvider : PreviewParameterProvider<ChatPreviewState> {
    override val values = sequenceOf(
        ChatPreviewState(
            text = "",
            sendEnabled = false,
        ),
        ChatPreviewState(
            text = "Hello, world!",
            sendEnabled = true,
        ),
        ChatPreviewState(
            text = "This is a longer message to test the chat input field. It should handle multiple lines and wrap text correctly.",
            sendEnabled = true,
        ),
        ChatPreviewState(
            text = "Here is a message with special characters: !@#$%^&*()_+-=[]{}|;':\",.<>/?`~",
            sendEnabled = true,
        ),
        ChatPreviewState(
            text = "最后，这是一条包含非拉丁字符的消息，以测试国际化支持。",
            sendEnabled = true,
        ),
    )
}

data class ChatPreviewState(
    val text: String,
    val sendEnabled: Boolean,
)

@Preview
@Composable
fun ChatInputInteractivePreview() {
    val state = rememberTextFieldState()

    AppTheme {
        Surface(
            color = MaterialTheme.colorScheme.background,
        ) {
            ChatInput(
                textState = state,
                sendEnabled = state.text.isNotBlank(),
                onSend = { state.clearText() },
            )
        }
    }
}

@Preview
@Composable
fun ChatMessageListPreview() {
    val messages = remember {
        val conversationId = Uuid.generateV7()
        listOf(
            MessageWithFilesAndBranchInfo(
                message = MessageEntity(
                    conversationId = conversationId,
                    depth = 0,
                    parts = prompt("1") {
                        user("Hello, world!")
                    }.messages,
                    role = MessageEntityRole.User,
                    generatedBy = null,
                ),
                files = emptyList(),
                branchIndex = 1,
                branchCount = 1,
            ),
            MessageWithFilesAndBranchInfo(
                message = MessageEntity(
                    conversationId = conversationId,
                    depth = 0,
                    parts = prompt("1") {
                        user("How are you today?")
                    }.messages,
                    role = MessageEntityRole.User,
                    generatedBy = null,
                ),
                files = emptyList(),
                branchIndex = 1,
                branchCount = 2,
            ),
            MessageWithFilesAndBranchInfo(
                message = MessageEntity(
                    conversationId = conversationId,
                    depth = 1,
                    parts = prompt("2") {
                        message(
                            Message.Reasoning(
                                content = "This is a sample reasoning message.\n\n- Step 1: Do this.\n- Step 2: Do that.\n\n**Conclusion:** This is the result.",
                                metaInfo = ResponseMetaInfo(
                                    timestamp = Clock.System.now().toDeprecatedInstant(),
                                    metadata = JsonObject(
                                        mapOf(
                                            "startedAt" to JsonPrimitive(
                                                (Clock.System.now() -
                                                        Random.nextInt(600).seconds).toString(),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        )
                        assistant("Here is the final answer based on the reasoning above.")
                        tool {
                            call(
                                id = "calculator",
                                tool = "Calculator",
                                content = "2 + 2",
                            )
                            result(
                                id = "calculator",
                                tool = "Calculator",
                                content = "4",
                            )
                        }
                        assistant("Is there anything else I can help you with?")
                    }.messages,
                    role = MessageEntityRole.Assistant,
                    generatedBy = DeepSeekModels.DeepSeekChat,
                ),
                files = emptyList(),
                branchIndex = 2,
                branchCount = 5,
            ),
            MessageWithFilesAndBranchInfo(
                message = MessageEntity(
                    conversationId = conversationId,
                    depth = 0,
                    parts = prompt("1") {
                        assistant("Bye!")
                    }.messages,
                    role = MessageEntityRole.Assistant,
                    generatedBy = null,
                ),
                files = emptyList(),
                branchIndex = 1,
                branchCount = 1,
            ),
        )
    }

    AppTheme {
        Surface(
            color = MaterialTheme.colorScheme.background,
        ) {
            CompositionLocalProvider(LocalMarkdownViewBlockParsing provides true) {
                ChatMessageList(
                    getMessageCount = { messages.size },
                    getMessageKey = { messages[it].message.id },
                    getMessageAt = { messages[it] },
                    onPrevious = {},
                    onNext = {},
                    onRegenerate = {},
                    initialReasoningVisibility = true,
                    onAnyReasoningVisibilityChange = { _, _ -> },
                    initialToolVisibility = true,
                    onAnyToolVisibilityChange = { _, _ -> },
                )
            }
        }
    }
}

@Preview
@Composable
fun StreamingAssistantMessagePreview() {
    if (LocalInspectionMode.current) {
        Text("Streaming preview not available in Inspection Mode")
        return
    }

    val eventFlow = remember { MutableSharedFlow<AssistantMessageStreamingEvent>() }
    val coroutineScope = rememberCoroutineScope()

    var state by
    remember(coroutineScope) {
        mutableStateOf(
            AssistantMessageState.Streaming(
                eventFlow,
                DeepSeekModels.DeepSeekReasoner,
                coroutineScope,
            ),
        )
    }

    suspend fun generate() {
        eventFlow.emit(
            AssistantMessageStreamingEvent.AddString(
                0, "This is a streaming ", AssistantStreamingMessageType.Reasoning,
            ),
        )
        delay(600.milliseconds)
        eventFlow.emit(
            AssistantMessageStreamingEvent.AddString(
                0, "message with ", AssistantStreamingMessageType.Reasoning,
            ),
        )
        delay(600.milliseconds)
        eventFlow.emit(
            AssistantMessageStreamingEvent.AddString(
                0, "some reasoning content.\n\n", AssistantStreamingMessageType.Reasoning,
            ),
        )
        delay(300.milliseconds)
        eventFlow.emit(
            AssistantMessageStreamingEvent.SetMessage(
                1,
                Message.Tool.Call(
                    id = "web_search",
                    tool = "Web Search",
                    content = "Knowmad",
                    metaInfo = ResponseMetaInfo.create(Clock.System.toDeprecatedClock()),
                ),
            ),
        )
        delay(300.milliseconds)
        eventFlow.emit(
            AssistantMessageStreamingEvent.SetMessage(
                2,
                Message.Tool.Result(
                    id = "web_search",
                    tool = "Web Search",
                    content = "Knowmad is an AI-powered knowledge nomad application designed to help users manage and explore information seamlessly.",
                    metaInfo = RequestMetaInfo.create(Clock.System.toDeprecatedClock()),
                ),
            ),
        )
        delay(300.milliseconds)
        eventFlow.emit(
            AssistantMessageStreamingEvent.AddString(
                3, "Now adding some contents.", AssistantStreamingMessageType.Content,
            ),
        )
        delay(300.milliseconds)
        eventFlow.emit(
            AssistantMessageStreamingEvent.AddString(
                3,
                " Is there anything else I can help you with?",
                AssistantStreamingMessageType.Content,
            ),
        )
        delay(300.milliseconds)
        eventFlow.emit(
            AssistantMessageStreamingEvent.SetMessage(
                4,
                Message.Tool.Call(
                    id = "calculator",
                    tool = "Calculator",
                    content = "2 + 2",
                    metaInfo = ResponseMetaInfo.create(Clock.System.toDeprecatedClock()),
                ),
            ),
        )
        delay(300.milliseconds)
        eventFlow.emit(
            AssistantMessageStreamingEvent.SetMessage(
                5,
                Message.Tool.Result(
                    id = "calculator",
                    tool = "Calculator",
                    content = "4",
                    metaInfo = RequestMetaInfo.create(Clock.System.toDeprecatedClock()),
                ),
            ),
        )
        delay(300.milliseconds)
        eventFlow.emit(
            AssistantMessageStreamingEvent.AddString(
                6, "Wow, the result", AssistantStreamingMessageType.Content,
            ),
        )
        delay(300.milliseconds)
        eventFlow.emit(
            AssistantMessageStreamingEvent.AddString(
                6, " of `2 + 2` is `4`!", AssistantStreamingMessageType.Content,
            ),
        )
        delay(300.milliseconds)
        eventFlow.emit(
            AssistantMessageStreamingEvent.AddString(
                7, "Now I need", AssistantStreamingMessageType.Reasoning,
            ),
        )
        delay(600.milliseconds)
        eventFlow.emit(
            AssistantMessageStreamingEvent.AddString(
                7, " to wrap up.", AssistantStreamingMessageType.Reasoning,
            ),
        )
        delay(300.milliseconds)
        eventFlow.emit(
            AssistantMessageStreamingEvent.AddString(
                8,
                "**Conclusion:** This is the end of the streaming message.",
                AssistantStreamingMessageType.Content,
            ),
        )
        eventFlow.emit(AssistantMessageStreamingEvent.Finish)
    }

    AppTheme {
        Surface(
            color = MaterialTheme.colorScheme.background,
        ) {
            AssistantMessage(
                state = state,
                current = 2,
                total = 5,
                onPrevious = {},
                onNext = {},
                onRegenerate = {
                    state = AssistantMessageState.Streaming(
                        eventFlow,
                        DeepSeekModels.DeepSeekReasoner,
                        coroutineScope,
                    )
                    coroutineScope.launch {
                        generate()
                    }
                },
                initialReasoningVisibility = true,
                onAnyReasoningVisibilityChange = {},
                initialToolVisibility = true,
                onAnyToolVisibilityChange = {},
            )
        }
    }

    LaunchedEffect(Unit) {
        generate()
    }
}
