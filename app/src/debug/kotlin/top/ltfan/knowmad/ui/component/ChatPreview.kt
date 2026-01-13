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
import ai.koog.prompt.message.ResponseMetaInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.datetime.toDeprecatedInstant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import top.ltfan.knowmad.data.chat.MessageEntity
import top.ltfan.knowmad.data.chat.MessageEntityRole
import top.ltfan.knowmad.data.chat.MessageWithFilesAndBranchInfo
import top.ltfan.knowmad.ui.theme.AppTheme
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

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
