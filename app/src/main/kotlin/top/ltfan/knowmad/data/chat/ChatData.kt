/*
 * Knowmad - Knowledge nomad
 * Copyright (C) 2025 LTFan (aka xfqwdsj)
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

import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import top.ltfan.knowmad.data.file.storeFileIfNotIndexed
import top.ltfan.knowmad.ui.viewmodel.AppViewModel
import top.ltfan.knowmad.util.HashComputationDispatcher
import kotlin.uuid.Uuid

const val ATTACHMENT_STORAGE_PATH = "attachments"
const val ATTACHMENT_STORAGE_SCHEME = "knowmad-attachment"

context(viewModel: AppViewModel)
suspend fun List<ContentPart>.storeAll(ref: MutableList<Uuid>) =
    coroutineScope { map { async { it.stored(ref) } }.awaitAll() }

context(viewModel: AppViewModel)
suspend fun ContentPart.stored(ref: MutableList<Uuid>): ContentPart {
    val part = this
    if (part !is ContentPart.Attachment) return part
    val content = part.content
    if (content !is AttachmentContent.Binary) return part

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
    is ContentPart.Attachment -> {
        val content = this.content
        if (content !is AttachmentContent.URL) return this

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
    is ContentPart.Audio -> copy(content = newContent)
    is ContentPart.File -> copy(content = newContent)
    is ContentPart.Image -> copy(content = newContent)
    is ContentPart.Video -> copy(content = newContent)
}
