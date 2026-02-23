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

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import top.ltfan.knowmad.application.KnowmadApplication
import top.ltfan.knowmad.data.database.AppDatabase
import top.ltfan.knowmad.data.file.DbFileMutex
import top.ltfan.knowmad.data.file.getOrStoreFile
import top.ltfan.knowmad.data.file.normalizedDbBaseDir
import top.ltfan.knowmad.util.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileDatabaseTest {
    val json = Json {
        prettyPrint = true
        prettyPrintIndent = " "
    }

    val fs = FakeFileSystem()
    lateinit var db: AppDatabase

    @Test
    fun testStoreAndGetFile() = runTest {
        val content = "hello world".encodeToByteArray()
        val fileName = "hello.txt"
        val fileType = "text"
        val fileFormat = "txt"
        val mimeType = "text/plain"

        val fileEntity = getOrStoreFile(
            database = db,
            filesDir = "/".toPath(),
            type = fileType,
            name = fileName,
            format = fileFormat,
            mimeType = mimeType,
            content = content,
            fs = fs,
        )

        assertEquals(fileName, fileEntity.name)
        assertEquals(fileType, fileEntity.type)
        assertEquals(fileFormat, fileEntity.format)
        assertEquals(mimeType, fileEntity.mimeType)
        assertEquals(content.size, fileEntity.size)

        val expectedPath =
            "/".toPath().normalizedDbBaseDir(content.toByteString().sha256()).resolve(fileType)
                .resolve("${fileEntity.id}.dat")

        assertEquals(expectedPath, fileEntity.path)
        assertTrue(fs.exists(fileEntity.path))
        val storedContent = fs.read(fileEntity.path) { readByteArray() }
        assertContentEquals(content, storedContent)

        val sameFileEntity = getOrStoreFile(
            database = db,
            filesDir = "/".toPath(),
            type = fileType,
            name = "another.txt",
            format = fileFormat,
            mimeType = mimeType,
            content = content,
            fs = fs,
        )

        assertEquals(fileEntity.id, sameFileEntity.id)
        assertContentEquals(fileEntity.hash, sameFileEntity.hash)
    }

    @Test
    fun testStoreAndGetEmptyFile() = runTest {
        val content = byteArrayOf()
        val fileName = "empty.txt"
        val fileType = "text"
        val fileFormat = "txt"
        val mimeType = "text/plain"

        val fileEntity = getOrStoreFile(
            database = db,
            filesDir = "/".toPath(),
            type = fileType,
            name = fileName,
            format = fileFormat,
            mimeType = mimeType,
            content = content,
            fs = fs,
        )

        assertEquals(fileName, fileEntity.name)
        assertEquals(0, fileEntity.size)

        val expectedPath =
            "/".toPath().normalizedDbBaseDir(content.toByteString().sha256()).resolve(fileType)
                .resolve("${fileEntity.id}.dat")

        assertEquals(expectedPath, fileEntity.path)
        assertTrue(fs.exists(fileEntity.path))
        val storedContent = fs.read(fileEntity.path) { readByteArray() }
        assertContentEquals(content, storedContent)
    }

    @Test
    fun testFileIsRecreatedIfMissing() = runTest {
        val content = "a file that will be deleted".encodeToByteArray()
        val fileName = "ephemeral.txt"
        val fileType = "text"
        val fileFormat = "txt"
        val mimeType = "text/plain"

        val fileEntity = getOrStoreFile(
            database = db,
            filesDir = "/".toPath(),
            type = fileType,
            name = fileName,
            format = fileFormat,
            mimeType = mimeType,
            content = content,
            fs = fs,
        )

        assertTrue(fs.exists(fileEntity.path))

        fs.delete(fileEntity.path)

        val recreatedFileEntity = getOrStoreFile(
            database = db,
            filesDir = "/".toPath(),
            type = fileType,
            name = "another.txt",
            format = fileFormat,
            mimeType = mimeType,
            content = content,
            fs = fs,
        )

        assertNotEquals(fileEntity.id, recreatedFileEntity.id)
        assertContentEquals(fileEntity.hash, recreatedFileEntity.hash)

        val expectedPath =
            "/".toPath().normalizedDbBaseDir(content.toByteString().sha256()).resolve(fileType)
                .resolve("${recreatedFileEntity.id}.dat")
        assertEquals(expectedPath, recreatedFileEntity.path)

        assertTrue(
            fs.exists(recreatedFileEntity.path),
            "File should be recreated in the filesystem",
        )
        val recreatedContent = fs.read(recreatedFileEntity.path) { readByteArray() }
        assertContentEquals(content, recreatedContent)

        val oldEntityFromDb = db.fileDao().getFileById(fileEntity.id)
        assertNull(
            oldEntityFromDb,
            "The old database record for the missing file should be deleted.",
        )
    }

    @Test
    fun testConcurrentGetOrStoreFile() = runTest {
        val content = "concurrent content".encodeToByteArray()
        val fileName = "concurrent.txt"
        val fileType = "text"
        val fileFormat = "txt"
        val mimeType = "text/plain"

        val deferredEntities = listOf(
            async {
                getOrStoreFile(
                    database = db,
                    filesDir = "/".toPath(),
                    type = fileType,
                    name = fileName,
                    format = fileFormat,
                    mimeType = mimeType,
                    content = content,
                    fs = fs,
                )
            },
            async {
                getOrStoreFile(
                    database = db,
                    filesDir = "/".toPath(),
                    type = fileType,
                    name = "anotherName.txt",
                    format = fileFormat,
                    mimeType = mimeType,
                    content = content,
                    fs = fs,
                )
            },
        )

        val (entity1, entity2) = deferredEntities.awaitAll()

        assertEquals(entity1.id, entity2.id)

        val expectedDir =
            "/".toPath().normalizedDbBaseDir(content.toByteString().sha256()).resolve(fileType)

        val filesInDir = fs.list(expectedDir)
        assertEquals(1, filesInDir.size)
        assertTrue(fs.exists(entity1.path))
        assertContentEquals(content, fs.read(entity1.path) { readByteArray() })
    }

    @Test
    fun testStoreSameContentDifferentType() = runTest {
        val content = "some content".encodeToByteArray()
        val fileType1 = "type1"
        val fileType2 = "type2"

        val entity1 = getOrStoreFile(
            database = db,
            filesDir = "/".toPath(),
            type = fileType1,
            name = "name.txt",
            format = "txt",
            mimeType = "text/plain",
            content = content,
            fs = fs,
        )

        val entity2 = getOrStoreFile(
            database = db,
            filesDir = "/".toPath(),
            type = fileType2,
            name = "name.txt",
            format = "txt",
            mimeType = "text/plain",
            content = content,
            fs = fs,
        )

        assertNotEquals(entity1.id, entity2.id)
        assertNotEquals(entity1.path, entity2.path)
        assertEquals(fileType1, entity1.type)
        assertEquals(fileType2, entity2.type)
        assertContentEquals(entity1.hash, entity2.hash)

        assertTrue(fs.exists(entity1.path))
        assertTrue(fs.exists(entity2.path))

        val baseDir = "/".toPath().normalizedDbBaseDir(content.toByteString().sha256())

        val filesInType1Dir = fs.list(baseDir.resolve(fileType1))
        assertEquals(1, filesInType1Dir.size)
        val filesInType2Dir = fs.list(baseDir.resolve(fileType2))
        assertEquals(1, filesInType2Dir.size)
    }

    @Test
    fun testOrphanedFileIsCleanedUp() = runTest {
        val content = "orphan me".encodeToByteArray()
        val fileType = "text"

        val entity1 = getOrStoreFile(
            database = db,
            filesDir = "/".toPath(),
            type = fileType,
            name = "first.txt",
            format = "txt",
            mimeType = "text/plain",
            content = content,
            fs = fs,
        )
        assertTrue(fs.exists(entity1.path), "File should exist after initial creation.")
        assertNotNull(
            db.fileDao().getFileById(entity1.id),
            "Database record should exist for the created file.",
        )

        db.fileDao().deleteFile(entity1)
        assertNull(
            db.fileDao().getFileById(entity1.id),
            "Database record should be deleted to create an orphan.",
        )

        val entity2 = getOrStoreFile(
            database = db,
            filesDir = "/".toPath(),
            type = fileType,
            name = "second.txt",
            format = "txt",
            mimeType = "text/plain",
            content = content,
            fs = fs,
        )

        assertNotEquals(entity1.id, entity2.id, "A new entity should be created.")
        assertTrue(fs.exists(entity2.path), "The new file should exist.")
        assertFalse(
            fs.exists(entity1.path),
            "The orphaned file should have been deleted by the cleanup mechanism.",
        )

        val expectedDir =
            "/".toPath().normalizedDbBaseDir(content.toByteString().sha256()).resolve(fileType)

        val filesInDir = fs.list(expectedDir)
        assertEquals(
            1,
            filesInDir.size,
            "Directory should only contain the new file after cleanup.",
        )
        assertEquals(entity2.path, filesInDir.first())
    }

    @Test
    fun testStoreWithIncorrectPrecomputedHash() = runTest {
        val content = "some real content".encodeToByteArray()
        val incorrectHash = "this is a bad hash".encodeToByteArray().toByteString()

        val entity1 = getOrStoreFile(
            database = db,
            filesDir = "/".toPath(),
            type = "text",
            name = "bad_hash.txt",
            format = "txt",
            mimeType = "text/plain",
            content = content,
            precomputedHash = incorrectHash,
            fs = fs,
        )

        assertContentEquals(incorrectHash.toByteArray(), entity1.hash)

        val entity2 = getOrStoreFile(
            database = db,
            filesDir = "/".toPath(),
            type = "text",
            name = "good_hash.txt",
            format = "txt",
            mimeType = "text/plain",
            content = content,
            fs = fs,
        )

        assertNotEquals(entity1.id, entity2.id)
        assertNotEquals(entity1.hash.contentToString(), entity2.hash.contentToString())

        val correctHash = content.toByteString().sha256().toByteArray()
        assertContentEquals(correctHash, entity2.hash)

        assertTrue(fs.exists(entity1.path))
        assertTrue(fs.exists(entity2.path))
    }

    @Test
    fun testConcurrentStoreWithMutexCollision() = runTest {
        val mutexPoolSize = DbFileMutex.size

        var i = 0
        var content1: ByteArray
        var content2: ByteArray

        while (true) {
            content1 = "content A $i".encodeToByteArray()
            content2 = "content B $i".encodeToByteArray()
            val hash1 = content1.toByteString().sha256().toByteArray().contentHashCode()
            val hash2 = content2.toByteString().sha256().toByteArray().contentHashCode()
            if ((hash1 and 0x7FFFFFFF) % mutexPoolSize == (hash2 and 0x7FFFFFFF) % mutexPoolSize) {
                break
            }
            i++
        }

        val deferredEntities = listOf(
            async {
                getOrStoreFile(
                    database = db,
                    filesDir = "/".toPath(),
                    type = "text",
                    name = "file1.txt",
                    format = "txt",
                    mimeType = "text/plain",
                    content = content1,
                    fs = fs,
                )
            },
            async {
                getOrStoreFile(
                    database = db,
                    filesDir = "/".toPath(),
                    type = "text",
                    name = "file2.txt",
                    format = "txt",
                    mimeType = "text/plain",
                    content = content2,
                    fs = fs,
                )
            },
        )

        val (entity1, entity2) = deferredEntities.awaitAll()

        assertNotEquals(entity1.id, entity2.id)

        assertTrue(fs.exists(entity1.path))
        assertTrue(fs.exists(entity2.path))
        assertContentEquals(content1, fs.read(entity1.path) { readByteArray() })
        assertContentEquals(content2, fs.read(entity2.path) { readByteArray() })
    }

    @Test
    fun testInvalidTypeIsRejected() = runTest {
        val content = "some content".encodeToByteArray()

        assertFailsWith<IllegalArgumentException>("Empty type should be rejected") {
            getOrStoreFile(
                database = db,
                filesDir = "/".toPath(),
                type = "",
                name = "test.txt",
                format = "txt",
                mimeType = "text/plain",
                content = content,
                fs = fs,
            )
        }

        assertFailsWith<IllegalArgumentException>("Path traversal in type should be rejected") {
            getOrStoreFile(
                database = db,
                filesDir = "/files".toPath(),
                type = "../../etc",
                name = "passwd",
                format = "txt",
                mimeType = "text/plain",
                content = content,
                fs = fs,
            )
        }

        assertFailsWith<IllegalArgumentException>("Path traversal in type should be rejected") {
            getOrStoreFile(
                database = db,
                filesDir = "/files".toPath(),
                type = "hello/world/../../../../etc",
                name = "passwd",
                format = "txt",
                mimeType = "text/plain",
                content = content,
                fs = fs,
            )
        }
    }

    @Test
    fun testTypeNormalizationIsHandled() = runTest {
        val content = "some content".encodeToByteArray()
        val nonNormalizedType = "a/../b/../c"
        val normalizedType = "c"

        val entity = getOrStoreFile(
            database = db,
            filesDir = "/".toPath(),
            type = nonNormalizedType,
            name = "test.txt",
            format = "txt",
            mimeType = "text/plain",
            content = content,
            fs = fs,
        )

        assertEquals(normalizedType, entity.type)

        val expectedPath = "/".toPath().normalizedDbBaseDir(content.toByteString().sha256())
            .resolve(normalizedType).resolve("${entity.id}.dat")
        assertEquals(expectedPath, entity.path)
        assertTrue(fs.exists(entity.path))
    }

    @Test
    fun testNameWithSeparators() = runTest {
        val pathLikeName = "a/b/c.txt"
        val content = "name with slashes".encodeToByteArray()

        val entity = getOrStoreFile(
            database = db,
            filesDir = "/".toPath(),
            type = "text",
            name = pathLikeName,
            format = "txt",
            mimeType = "text/plain",
            content = content,
            fs = fs,
        )

        assertEquals(pathLikeName, entity.name)

        val expectedPath =
            "/".toPath().normalizedDbBaseDir(content.toByteString().sha256()).resolve("text")
                .resolve("${entity.id}.dat")
        assertEquals(expectedPath, entity.path)
        assertTrue(fs.exists(entity.path))
    }

    @Test
    fun testConflictingFileInDirPathIsHandled() = runTest {
        val content = "cant write this".encodeToByteArray()
        val fileType = "blocked"

        val hash = content.toByteString().sha256()
        val directory = "/".toPath().normalizedDbBaseDir(hash).resolve(fileType)

        val parent = directory.parent
        assertNotNull(parent, "Directory should have a parent")

        fs.createDirectories(parent)
        fs.write(directory) { write("blocking content".encodeToByteArray()) }

        val entity = getOrStoreFile(
            database = db,
            filesDir = "/".toPath(),
            type = fileType,
            name = "test.txt",
            format = "txt",
            mimeType = "text/plain",
            content = content,
            fs = fs,
        )

        assertNotNull(entity)
        assertTrue(fs.exists(entity.path))
        assertContentEquals(content, fs.read(entity.path) { readByteArray() })

        assertTrue(!fs.listRecursively("/".toPath()).any { it.name.endsWith(".tmp") })
    }

    @Test
    fun testNonAsciiInType() = runTest {
        val fileType = "中文类型"
        val content = "你好，世界".encodeToByteArray()

        val entity = getOrStoreFile(
            database = db,
            filesDir = "/".toPath(),
            type = fileType,
            name = "文件.txt",
            format = "txt",
            mimeType = "text/plain",
            content = content,
            fs = fs,
        )

        assertEquals(fileType, entity.type)

        val hash = content.toByteString().sha256()
        val expectedDir = "/".toPath().normalizedDbBaseDir(hash).resolve(fileType)

        assertTrue(fs.exists(expectedDir))
        assertTrue(fs.exists(entity.path))
        assertEquals(expectedDir.resolve("${entity.id}.dat"), entity.path)

        val storedContent = fs.read(entity.path) { readByteArray() }
        assertContentEquals(content, storedContent)
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
