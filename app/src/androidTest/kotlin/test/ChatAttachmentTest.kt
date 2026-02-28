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

package top.ltfan.knowmad.test

import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import top.ltfan.knowmad.application.KnowmadApplication
import top.ltfan.knowmad.data.chat.allLoaded
import top.ltfan.knowmad.data.chat.allStored
import top.ltfan.knowmad.data.database.AppDatabase
import top.ltfan.knowmad.util.Json
import kotlin.io.encoding.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ChatAttachmentTest {
    val json = Json {
        prettyPrint = true
        prettyPrintIndent = " "
    }

    val fs = FakeFileSystem()
    lateinit var db: AppDatabase

    @Test
    fun testStoreAndLoadAttachments() = runTest {
        val fileContent = "This is a test file.".encodeToByteArray()
        val fileName = "test.txt"
        val fileFormat = "txt"
        val mimeType = "text/plain"
        val filesDir = "/files".toPath()

        val originalMessage = Message.User(
            parts = listOf(
                ContentPart.Text("Here is a file:"),
                ContentPart.File(
                    content = AttachmentContent.Binary.Bytes(fileContent),
                    format = fileFormat,
                    mimeType = mimeType,
                    fileName = fileName,
                ),
            ),
            metaInfo = RequestMetaInfo.create(TestClock),
        )

        val fileIds = mutableListOf<Uuid>()
        val storedMessage = originalMessage.allStored(db, filesDir, fileIds, fs)

        assertEquals(1, fileIds.size)
        val fileId = fileIds.first()

        val storedPart = storedMessage.parts[1]
        assertIs<ContentPart.File>(storedPart)
        val storedContent = storedPart.content
        assertIs<AttachmentContent.URL>(storedContent)
        assertEquals("knowmad-attachment://$fileId", storedContent.url)

        val fileEntity = db.fileDao().getFileById(fileId)
        assertNotNull(fileEntity)
        assertEquals(fileName, fileEntity.name)
        assertTrue(fs.exists(fileEntity.path))
        assertContentEquals(fileContent, fs.read(fileEntity.path) { readByteArray() })

        val loadedMessage = storedMessage.allLoaded(db.fileDao(), fs)

        val loadedPart = loadedMessage.parts[1]
        assertIs<ContentPart.File>(loadedPart)
        val loadedFileContent = loadedPart.content
        assertIs<AttachmentContent.Binary>(loadedFileContent)
        assertContentEquals(fileContent, loadedFileContent.asBytes())

        fs.delete(fileEntity.path)
        val loadedMessageWithMissingFile = storedMessage.allLoaded(db.fileDao(), fs)
        val partWithMissingFile = loadedMessageWithMissingFile.parts[1]
        assertIs<ContentPart.File>(partWithMissingFile)
        assertIs<AttachmentContent.URL>(partWithMissingFile.content)
    }

    @Test
    fun testStoreAndLoadBase64Attachments() = runTest {
        val fileContent = "This is a Base64 test file.".encodeToByteArray()
        val base64Content = Base64.encode(fileContent)
        val fileName = "test_base64.txt"
        val fileFormat = "txt"
        val mimeType = "text/plain"
        val filesDir = "/files".toPath()

        val originalMessage = Message.User(
            parts = listOf(
                ContentPart.Text("Here is a Base64 file:"),
                ContentPart.File(
                    content = AttachmentContent.Binary.Base64(base64Content),
                    format = fileFormat,
                    mimeType = mimeType,
                    fileName = fileName,
                ),
            ),
            metaInfo = RequestMetaInfo.create(TestClock),
        )

        val fileIds = mutableListOf<Uuid>()
        val storedMessage = originalMessage.allStored(db, filesDir, fileIds, fs)

        assertEquals(1, fileIds.size)
        val fileId = fileIds.first()

        val storedPart = storedMessage.parts[1]
        assertIs<ContentPart.File>(storedPart)
        val storedContent = storedPart.content
        assertIs<AttachmentContent.URL>(storedContent)
        assertEquals("knowmad-attachment://$fileId", storedContent.url)

        val fileEntity = db.fileDao().getFileById(fileId)
        assertNotNull(fileEntity)
        assertTrue(fs.exists(fileEntity.path))
        assertContentEquals(fileContent, fs.read(fileEntity.path) { readByteArray() })

        val loadedMessage = storedMessage.allLoaded(db.fileDao(), fs)

        val loadedPart = loadedMessage.parts[1]
        assertIs<ContentPart.File>(loadedPart)
        val loadedFileContent = loadedPart.content
        assertIs<AttachmentContent.Binary>(loadedFileContent)
        assertContentEquals(fileContent, loadedFileContent.asBytes())
    }

    @Test
    fun testStoreAndLoadImageAttachment() = runTest {
        val imageContent = "This is a fake image.".encodeToByteArray()
        val fileFormat = "png"
        val mimeType = "image/png"
        val filesDir = "/files".toPath()

        val originalMessage = Message.User(
            parts = listOf(
                ContentPart.Text("Here is an image:"),
                ContentPart.Image(
                    content = AttachmentContent.Binary.Bytes(imageContent),
                    format = fileFormat,
                    mimeType = mimeType,
                ),
            ),
            metaInfo = RequestMetaInfo.create(TestClock),
        )

        val fileIds = mutableListOf<Uuid>()
        val storedMessage = originalMessage.allStored(db, filesDir, fileIds, fs)

        assertEquals(1, fileIds.size)
        val fileId = fileIds.first()

        val storedPart = storedMessage.parts[1]
        assertIs<ContentPart.Image>(storedPart)
        val storedContent = storedPart.content
        assertIs<AttachmentContent.URL>(storedContent)
        assertEquals("knowmad-attachment://$fileId", storedContent.url)

        val fileEntity = db.fileDao().getFileById(fileId)
        assertNotNull(fileEntity)
        assertNull(fileEntity.name)
        assertEquals("png", fileEntity.format)
        assertTrue(fs.exists(fileEntity.path))
        assertContentEquals(imageContent, fs.read(fileEntity.path) { readByteArray() })

        val loadedMessage = storedMessage.allLoaded(db.fileDao(), fs)

        val loadedPart = loadedMessage.parts[1]
        assertIs<ContentPart.Image>(loadedPart)
        val loadedFileContent = loadedPart.content
        assertIs<AttachmentContent.Binary>(loadedFileContent)
        assertContentEquals(imageContent, loadedFileContent.asBytes())
    }

    @Test
    fun testNonBinaryPartsAreUnchanged() = runTest {
        val originalMessage = Message.User(
            parts = listOf(
                ContentPart.Text("This should not change."),
                ContentPart.File(
                    content = AttachmentContent.PlainText("This is not a binary attachment."),
                    format = "txt",
                    mimeType = "text/plain",
                    fileName = "some.txt",
                ),
                ContentPart.File(
                    content = AttachmentContent.URL("https://example.com/image.png"),
                    format = "png",
                    mimeType = "image/png",
                    fileName = "external.png",
                ),
            ),
            metaInfo = RequestMetaInfo.create(TestClock),
        )

        val fileIds = mutableListOf<Uuid>()
        val storedMessage = originalMessage.allStored(db, "/files".toPath(), fileIds, fs)

        assertTrue(fileIds.isEmpty(), "No files should have been stored.")
        assertEquals(
            originalMessage,
            storedMessage,
            "Message with non-binary parts should not be modified by storing.",
        )

        val loadedMessage = storedMessage.allLoaded(db.fileDao(), fs)
        assertEquals(
            originalMessage,
            loadedMessage,
            "Message with non-binary parts should not be modified by loading.",
        )
    }

    @Test
    fun testStoreAndLoadMultipleAndEmptyAttachments() = runTest {
        val fileContent1 = "File 1".encodeToByteArray()
        val base64Content2 = Base64.encode("File 2".encodeToByteArray())
        val imageContent3 = "Image 3".encodeToByteArray()
        val emptyContent4 = byteArrayOf()

        val originalMessage = Message.User(
            parts = listOf(
                ContentPart.File(
                    content = AttachmentContent.Binary.Bytes(fileContent1),
                    format = "txt",
                    mimeType = "text/plain",
                    fileName = "f1.txt",
                ),
                ContentPart.File(
                    content = AttachmentContent.Binary.Base64(base64Content2),
                    format = "txt",
                    mimeType = "text/plain",
                    fileName = "f2.txt",
                ),
                ContentPart.Image(
                    content = AttachmentContent.Binary.Bytes(imageContent3),
                    format = "png",
                    mimeType = "image/png",
                    fileName = "i3.png",
                ),
                ContentPart.File(
                    content = AttachmentContent.Binary.Bytes(emptyContent4),
                    format = "dat",
                    mimeType = "application/octet-stream",
                    fileName = "empty.dat",
                ),
            ),
            metaInfo = RequestMetaInfo.create(TestClock),
        )

        val fileIds = mutableListOf<Uuid>()
        val storedMessage = originalMessage.allStored(db, "/files".toPath(), fileIds, fs)
        val replacedOriginalMessage = originalMessage.copy(
            parts = originalMessage.parts.toMutableList().apply {
                set(
                    1,
                    ContentPart.File(
                        content = AttachmentContent.Binary.Bytes(Base64.decode(base64Content2)),
                        format = "txt",
                        mimeType = "text/plain",
                        fileName = "f2.txt",
                    ),
                )
            },
        )

        assertEquals(4, fileIds.size)
        assertEquals(4, storedMessage.parts.size)
        storedMessage.parts.forEach { part ->
            val filePart = assertIs<ContentPart.Attachment>(part)
            assertIs<AttachmentContent.URL>(filePart.content)
        }

        val loadedMessage = storedMessage.allLoaded(db.fileDao(), fs)
        assertEquals(replacedOriginalMessage, loadedMessage)
    }

    @Test
    fun testLoadingWithInvalidUrl() = runTest {
        val nonExistentUuid = Uuid.parse("badc0ffe-dead-beef-baad-f00d00000000")
        val invalidUrlMessage = Message.User(
            parts = listOf(
                ContentPart.File(
                    content = AttachmentContent.URL("knowmad-attachment://not-a-uuid"),
                    format = "txt",
                    mimeType = "text/plain",
                    fileName = "invalid1.txt",
                ),
                ContentPart.File(
                    content = AttachmentContent.URL("knowmad-attachment://$nonExistentUuid"), // a non-existent but valid UUID
                    format = "txt",
                    mimeType = "text/plain",
                    fileName = "invalid2.txt",
                ),
                ContentPart.File(
                    content = AttachmentContent.URL("https://google.com"), // non-knowmad scheme
                    format = "txt",
                    mimeType = "text/plain",
                    fileName = "invalid3.txt",
                ),
            ),
            metaInfo = RequestMetaInfo.create(TestClock),
        )

        val loadedMessage = invalidUrlMessage.allLoaded(db.fileDao(), fs)

        // The loading should fail gracefully, and the content should remain a URL.
        assertEquals(invalidUrlMessage, loadedMessage)
        assertIs<AttachmentContent.URL>((loadedMessage.parts[0] as ContentPart.File).content)
        assertIs<AttachmentContent.URL>((loadedMessage.parts[1] as ContentPart.File).content)
        assertIs<AttachmentContent.URL>((loadedMessage.parts[2] as ContentPart.File).content)
    }

    @BeforeTest
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<KnowmadApplication>()
        db = with(AppDatabase) {
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .buildAppDatabase()
        }
    }

    @AfterTest
    fun teardown() {
        val allFiles = runBlocking { db.fileDao().getAllFiles() }.map { it.printable() }
        println("📦 Files in Database:")
        println(json.encodeToString(allFiles).replaceIndent("  "))
        db.close()
        fs.createDirectories("/".toPath())
        fs.dumpTree()
        fs.checkNoOpenFiles()
    }
}
