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
import androidx.compose.runtime.mutableStateMapOf
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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import kotlinx.datetime.toDeprecatedInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okio.ByteString.Companion.toByteString
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import top.ltfan.knowmad.data.file.storeFileIfNotIndexed
import top.ltfan.knowmad.ui.component.AssistantMessageState
import top.ltfan.knowmad.ui.component.MathJaxRenderResult
import top.ltfan.knowmad.ui.component.SavedMarkdownState
import top.ltfan.knowmad.ui.component.codeBlockOrNull
import top.ltfan.knowmad.ui.viewmodel.AppViewModel
import top.ltfan.knowmad.util.HashComputationDispatcher
import top.ltfan.knowmad.util.Json
import top.ltfan.knowmad.util.RemendProcessor
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
    data class Koog(
        val message: Message,
        override val display: Boolean = true,
    ) : UiMessage {
        override val content get() = message.content

        inline fun modifiedContent(
            block: (
                content: String,
                modify: (start: Int, end: Int, text: String) -> Unit,
            ) -> Unit,
        ): Koog {
            @Suppress("UNCHECKED_CAST")
            val textPartEntries = message.parts.withIndex()
                .mapNotNull {
                    if (it.value is ContentPart.Text) it as IndexedValue<ContentPart.Text>
                    else null
                }

            if (textPartEntries.isEmpty()) return this

            val originalSeparatorIndices = mutableListOf<Int>()

            val originalString = buildString {
                for (i in textPartEntries.indices) {
                    val part = textPartEntries[i].value
                    append(part.text)
                    if (i < textPartEntries.size - 1) {
                        originalSeparatorIndices.add(length)
                        append('\n')
                    }
                }
            }

            val modifications = mutableListOf<Triple<Int, Int, String>>()
            block(originalString) { start, end, text ->
                modifications.add(Triple(start, end, text))
            }
            modifications.sortBy { it.first }

            val newSeparatorIndices = mutableListOf<Int>()
            var currentOriginalIndex = 0
            var accumulatedShift = 0

            val finalString = buildString {
                for ((start, end, text) in modifications) {
                    if (start > currentOriginalIndex) {
                        append(originalString.substring(currentOriginalIndex, start))
                    }
                    while (newSeparatorIndices.size < originalSeparatorIndices.size) {
                        val nextSepIndex = originalSeparatorIndices[newSeparatorIndices.size]
                        if (nextSepIndex < start) {
                            newSeparatorIndices.add(nextSepIndex + accumulatedShift)
                        } else break
                    }
                    append(text)
                    val currentShift = text.length - (end - start)
                    while (newSeparatorIndices.size < originalSeparatorIndices.size) {
                        val nextSepIndex = originalSeparatorIndices[newSeparatorIndices.size]
                        if (nextSepIndex < end) {
                            newSeparatorIndices.add(length)
                        } else break
                    }
                    accumulatedShift += currentShift
                    currentOriginalIndex = end
                }
                if (currentOriginalIndex < originalString.length) {
                    append(originalString.substring(currentOriginalIndex))
                }
            }

            while (newSeparatorIndices.size < originalSeparatorIndices.size) {
                val nextSepIndex = originalSeparatorIndices[newSeparatorIndices.size]
                newSeparatorIndices.add(nextSepIndex + accumulatedShift)
            }

            val resultParts = message.parts.toMutableList()
            var lastSliceEnd = 0

            for (i in textPartEntries.indices) {
                val originalPartIndex = textPartEntries[i].index
                val newTextValue: String

                if (i < newSeparatorIndices.size) {
                    val splitPoint = newSeparatorIndices[i]

                    val originalSepIndex = originalSeparatorIndices[i]
                    val isConsumed = modifications.any { mod ->
                        originalSepIndex >= mod.first && originalSepIndex < mod.second
                    }
                    val gap = if (isConsumed) 0 else 1

                    val safeEnd = splitPoint.coerceAtMost(finalString.length)
                    newTextValue = finalString.substring(lastSliceEnd, safeEnd)
                    lastSliceEnd = (safeEnd + gap).coerceAtMost(finalString.length)
                } else {
                    newTextValue = if (lastSliceEnd <= finalString.length) {
                        finalString.substring(lastSliceEnd)
                    } else ""
                }

                resultParts[originalPartIndex] = ContentPart.Text(newTextValue)
            }

            return copy(
                message = when (message) {
                    is Message.System -> message.copy(parts = resultParts.filterIsInstance<ContentPart.Text>())
                    is Message.Tool.Result -> message.copy(parts = resultParts.filterIsInstance<ContentPart.Text>())
                    is Message.User -> message.copy(parts = resultParts)
                    is Message.Assistant -> message.copy(parts = resultParts)
                    is Message.Reasoning -> message.copy(parts = resultParts.filterIsInstance<ContentPart.Text>())
                    is Message.Tool.Call -> message.copy(parts = resultParts.filterIsInstance<ContentPart.Text>())
                },
            )
        }
    }

    @Serializable
    @Immutable
    data class Error(
        override val content: String,
        override val display: Boolean = true,
    ) : UiMessage

    sealed class NotDisplayed : UiMessage {
        override val content: String = ""
        override val display = false
    }

    @Serializable
    @Immutable
    data class MetaInfo(
        val gatheredTools: Set<String> = emptySet(),
    ) : NotDisplayed()
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
        remend: RemendProcessor? = null,
        val startedAt: Instant = Clock.System.now(),
    ) : AssistantMessageContent(
        coroutineScope = coroutineScope,
        contentFlow = flow.mapLatest {
            remend?.process(it) ?: it.substringBeforeLast('\n', "")
        },
    ) {
        var metaInfo by mutableStateOf<ResponseMetaInfo?>(null)
        override val content get() = flow.value
        val trailing = if (remend == null) {
            flow.map {
                it.substringAfterLast('\n').ifEmpty { null }
            }
        } else flowOf(null)

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
            mathResults: MutableMap<String, Result<MathJaxRenderResult>?> = markdownState.mathResults,
        ) = Completed.fromStreaming(
            streaming = this,
            defaultEndedAt = defaultEndedAt,
            mathResults = mathResults,
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

        private val codeResultJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        suspend fun appendedCodeResults(
            vararg results: Triple<ASTNode, String, Instant>,
        ): Completed {
            if (uiMessage !is Koog || uiMessage.message !is Assistant || results.isEmpty()) return this

            val newUiMessage = uiMessage.modifiedContent { originalContent, modify ->
                for ((codeNode, result, createdAt) in results) {
                    val parent = codeNode.parent ?: continue
                    val nodeIndex = parent.children.indexOf(codeNode)

                    val nextElement = parent.children.subList(nodeIndex + 1, parent.children.size)
                        .find { it.type != MarkdownTokenTypes.EOL && it.type != MarkdownTokenTypes.WHITE_SPACE }

                    var replaceEnd = codeNode.endOffset

                    val resultContent = runCatching {
                        nextElement?.updateCodeResult(result, createdAt)?.also {
                            replaceEnd = nextElement.endOffset
                        }
                    }.onFailure {
                        nextElement?.let {
                            replaceEnd = nextElement.endOffset
                        }
                    }.getOrNull() ?: codeResultJson.encodeToString(
                        listOf(AssistantMessageCodeResult(result, createdAt)),
                    )

                    val start = codeNode.startOffset
                    val indent = originalContent.lastIndexOf('\n', start - 1)
                        .takeIf { it >= 0 }?.let { start - it - 1 } ?: start

                    // TODO: this will cause the llm trying to generate a fake result block after several rounds of conversation, need a better way to handle this
                    val block = "```result\n$resultContent\n```".prependIndent(" ".repeat(indent))

                    val textToInsert = "\n$block"

                    modify(codeNode.endOffset, replaceEnd, textToInsert)
                }
            }

            return Completed(uiMessage = newUiMessage)
        }

        private fun ASTNode.updateCodeResult(
            appendResult: String,
            createdAt: Instant = Clock.System.now(),
        ): String? {
            val (result, language) = codeBlockOrNull(content) ?: return null
            if (language == null || language != "result") return null

            val results = codeResultJson.decodeFromString<List<AssistantMessageCodeResult>>(result)
                .toMutableList()

            results.removeAll { !it.isVerified }

            results.add(AssistantMessageCodeResult(appendResult, createdAt))

            return codeResultJson.encodeToString(results)
        }

        companion object {
            suspend fun fromStreaming(
                streaming: Streaming,
                defaultEndedAt: Instant = Clock.System.now(),
                mathResults: MutableMap<String, Result<MathJaxRenderResult>?> = streaming.markdownState.mathResults,
            ): Completed {
                return Completed(
                    streaming.toMessage(defaultEndedAt).toUiMessage(),
                    SavedMarkdownState.Fixed(
                        markdownText = streaming.flow.value,
                        mathResults = mathResults,
                    ),
                )
            }
        }
    }

    companion object {
        suspend fun Completed(
            uiMessage: UiMessage,
            mathResults: MutableMap<String, Result<MathJaxRenderResult>?> = mutableStateMapOf(),
        ) = Completed(
            uiMessage = uiMessage,
            markdownState = SavedMarkdownState.Fixed(
                markdownText = uiMessage.filteredContent,
                mathResults = mathResults,
            ),
        )

        suspend fun Completed(
            message: Message,
            mathResults: MutableMap<String, Result<MathJaxRenderResult>?> = mutableStateMapOf(),
        ) = Completed(
            uiMessage = message.toUiMessage(),
            mathResults = mathResults,
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

@Serializable
private data class AssistantMessageCodeResult(
    val result: String,
    val signature: String,
    val createdAt: Instant = Clock.System.now(),
) {
    constructor(
        result: String,
        createdAt: Instant = Clock.System.now(),
    ) : this(
        result = result,
        signature = calculateSignature(result, createdAt),
        createdAt = createdAt,
    )

    @Transient
    val isVerified = signature == calculateSignature(result, createdAt)

    companion object {
        fun calculateSignature(result: String, createdAt: Instant): String {
            val input = "$result|$createdAt"
            return input.toByteArray().toByteString().md5().hex()
        }
    }
}

enum class AssistantStreamingMessageType {
    Reasoning, Content
}

fun Message.toUiMessage(display: Boolean = true) = UiMessage.Koog(this, display)
