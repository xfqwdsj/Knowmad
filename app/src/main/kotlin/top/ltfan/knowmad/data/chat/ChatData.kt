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

package top.ltfan.knowmad.data.chat

import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.toDeprecatedInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okio.ByteString.Companion.toByteString
import top.ltfan.knowmad.data.file.storeFileIfNotIndexed
import top.ltfan.knowmad.ui.component.AssistantMessageState
import top.ltfan.knowmad.ui.component.SavedMarkdownState
import top.ltfan.knowmad.ui.viewmodel.AppViewModel
import top.ltfan.knowmad.util.HashComputationDispatcher
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

const val ATTACHMENT_STORAGE_PATH = "attachments"
const val ATTACHMENT_STORAGE_SCHEME = "knowmad-attachment"

enum class MessageEntityRole {
    System, User, Assistant
}

context(viewModel: AppViewModel)
suspend fun List<ContentPart>.storeAll(ref: MutableList<Uuid>) =
    coroutineScope { map { async { it.stored(ref) } }.awaitAll() }

context(viewModel: AppViewModel)
suspend fun ContentPart.stored(ref: MutableList<Uuid>): ContentPart {
    val part = this
    if (part !is Attachment) return part
    val content = part.content
    if (content !is Binary) return part

    val bytes = content.asBytes()

    val hash = withContext(HashComputationDispatcher) {
        bytes.toByteString().sha256().toByteArray()
    }

    val entity = storeFileIfNotIndexed(
        type = ATTACHMENT_STORAGE_PATH,
        name = part.fileName,
        format = part.format,
        mimeType = part.mimeType,
        content = bytes,
        precomputedHash = hash,
    )

    ref.add(entity.id)

    val url = URLBuilder().apply {
        protocol = URLProtocol.createOrDefault(ATTACHMENT_STORAGE_SCHEME)
        host = entity.id.toString()
    }.build().toString()

    return part.updateContent(AttachmentContent.URL(url))
}

context(viewModel: AppViewModel)
suspend fun ContentPart.load(): ContentPart = when (this) {
    is Attachment -> {
        val content = this.content
        if (content !is URL) return this

        val url = URLBuilder(content.url).build()
        if (url.protocol.name != ATTACHMENT_STORAGE_SCHEME) return this

        val uuid = try {
            Uuid.parse(url.host)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            return this
        }

        val entity = viewModel.application.appDatabase.fileDao()
            .getFileById(uuid) ?: return this

        val fs = okio.FileSystem.SYSTEM

        val bytes = withContext(Dispatchers.IO) {
            if (!fs.exists(entity.path)) return@withContext null

            fs.read(entity.path) { readByteArray() }
        } ?: return this

        this.updateContent(AttachmentContent.Binary.Bytes(bytes))
    }

    else -> this
}

fun ContentPart.Attachment.updateContent(newContent: AttachmentContent) = when (this) {
    is Audio -> copy(content = newContent)
    is File -> copy(content = newContent)
    is Image -> copy(content = newContent)
    is Video -> copy(content = newContent)
}

sealed interface MessageWithBranchInfo {
    /** 1-based */
    val branchIndex: Int
    val branchCount: Int
}

@Serializable
sealed interface UiMessage {
    val content: String
    val display: Boolean

    @Serializable
    @Immutable
    class Koog(
        val message: Message,
        override val display: Boolean = true,
    ) : UiMessage {
        override val content get() = message.content
    }

    @Serializable
    @Immutable
    data class Error(
        override val content: String,
        override val display: Boolean = true,
    ) : UiMessage

}

sealed interface ChatListMessage {
    val key: Any

    sealed interface Branched : ChatListMessage, MessageWithBranchInfo {
        override val key: Uuid
    }

    sealed interface Standalone : ChatListMessage
}

@Stable
data class AssistantStreamingMessage(
    val state: AssistantMessageState.Streaming,
    override val branchIndex: Int,
    override val branchCount: Int,
) : ChatListMessage.Branched {
    override val key = state.id
}

sealed class AssistantMessageContent(val markdownState: SavedMarkdownState) {
    constructor(
        coroutineScope: CoroutineScope,
        contentFlow: Flow<String>,
    ) : this(SavedMarkdownState(coroutineScope, contentFlow))

    @Stable
    class Streaming(
        val type: AssistantStreamingMessageType,
        val flow: StateFlow<String>,
        val model: LLModel?,
        coroutineScope: CoroutineScope,
        val startedAt: Instant = Clock.System.now(),
    ) : AssistantMessageContent(
        coroutineScope = coroutineScope,
        contentFlow = flow.map { it.substringBeforeLast('\n', "") },
    ) {
        var metaInfo by mutableStateOf<ResponseMetaInfo?>(null)
        override val content get() = flow.value
        val trailing = flow.map {
            it.substringAfterLast('\n').ifEmpty { null }
        }

        fun toMessage(defaultEndedAt: Instant = Clock.System.now()): Message.Response {
            val metaInfo = metaInfo ?: createMetaInfo(defaultEndedAt)
            return when (type) {
                Content -> Message.Assistant(
                    content = flow.value,
                    metaInfo = metaInfo,
                )

                Reasoning -> Message.Reasoning(
                    content = flow.value,
                    metaInfo = metaInfo,
                )
            }
        }

        fun createMetaInfo(timestamp: Instant = Clock.System.now()) = ResponseMetaInfo(
            timestamp = timestamp.toDeprecatedInstant(),
            metadata = JsonObject(mapOf("startedAt" to JsonPrimitive(startedAt.toString()))),
        ).also { metaInfo = it }

        suspend fun completed(
            defaultEndedAt: Instant = Clock.System.now(),
        ) = Completed.fromStreaming(
            this,
            defaultEndedAt,
        )

        override val uiMessage get() = toMessage().toUiMessage()
    }

    @Immutable
    class Completed(
        override val uiMessage: UiMessage,
        markdownState: SavedMarkdownState,
    ) : AssistantMessageContent(markdownState) {
        override val content get() = uiMessage.content

        constructor(
            uiMessage: UiMessage,
            coroutineScope: CoroutineScope,
        ) : this(
            uiMessage,
            SavedMarkdownState(
                coroutineScope,
                flowOf(uiMessage.filteredContent),
            ),
        )

        constructor(
            message: Message,
            coroutineScope: CoroutineScope,
        ) : this(
            message.toUiMessage(),
            coroutineScope,
        )

        companion object {
            suspend fun fromStreaming(
                streaming: Streaming,
                defaultEndedAt: Instant = Clock.System.now(),
            ): Completed {
                return Completed(
                    streaming.toMessage(defaultEndedAt).toUiMessage(),
                    SavedMarkdownState.Fixed(streaming.flow.value),
                )
            }
        }
    }

    companion object {
        suspend fun Completed(uiMessage: UiMessage) = Completed(
            uiMessage = uiMessage,
            markdownState = SavedMarkdownState.Fixed(uiMessage.filteredContent),
        )

        suspend fun Completed(message: Message) = Completed(
            uiMessage = message.toUiMessage(),
        )

        private val UiMessage.filteredContent
            inline get() = when (this) {
                is Koog -> when (message) {
                    is Reasoning, is Assistant -> message.content
                    else -> ""
                }

                else -> content
            }
    }

    abstract val uiMessage: UiMessage
    abstract val content: String
}

enum class AssistantStreamingMessageType {
    Reasoning, Content
}

fun Message.toUiMessage(display: Boolean = true): UiMessage = UiMessage.Koog(this, display)
