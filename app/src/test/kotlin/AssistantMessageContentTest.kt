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

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import com.mikepenz.markdown.model.State
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.toDeprecatedClock
import org.intellij.markdown.ast.ASTNode
import top.ltfan.knowmad.data.chat.AssistantMessageContent
import top.ltfan.knowmad.data.chat.toUiMessage
import top.ltfan.knowmad.ui.component.codeBlockOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class AssistantMessageContentTest {
    @Test
    fun `test code result appending`() = runTest {
        val content = AssistantMessageContent.Completed(
            Message.Assistant(
                content = """
                    Hello, this is a message with runnable code.

                    ```runnable
                    println("Hello, World!")
                    ```
                """.trimIndent(),
                metaInfo = ResponseMetaInfo.create(
                    object : Clock {
                        override fun now() = Instant.DISTANT_PAST
                    }.toDeprecatedClock(),
                ),
            ).toUiMessage(),
        )

        val codeNodes = mutableListOf<ASTNode>()

        val node = content.markdownState.state.filterIsInstance<State.Success>().first().node

        val stack = ArrayDeque<ASTNode>()
        stack.add(node)

        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            if (current.codeBlockOrNull(content.content)?.language == "runnable") {
                codeNodes.add(current)
            }
            current.children.forEach { stack.add(it) }
        }

        assertEquals(1, codeNodes.size, "There should be exactly one runnable code block.")

        val codeNode = codeNodes.first()

        val expected = """
            Hello, this is a message with runnable code.

            ```runnable
            println("Hello, World!")
            ```
            ```result
            [{"result":"Hello, World!","createdAt":"-100001-12-31T23:59:59.999999999Z"}]
            ```
        """.trimIndent()

        assertEquals(
            expected = expected,
            actual = content.appendedCodeResults(
                Triple(codeNode, "Hello, World!", Instant.DISTANT_PAST),
            ).content,
        )
    }

    @Test
    fun `test code result appending with existing result`() = runTest {
        val content = AssistantMessageContent.Completed(
            Message.Assistant(
                content = """
                    Hello, this is a message with runnable code.

                    ```runnable
                    println("Hello, World!")
                    ```
                    ```result
                    [{"result":"Hello, World! First.","createdAt":"2026-02-16T06:00:00Z"}]
                    ```
                """.trimIndent(),
                metaInfo = ResponseMetaInfo.create(
                    object : Clock {
                        override fun now() = Instant.DISTANT_PAST
                    }.toDeprecatedClock(),
                ),
            ).toUiMessage(),
        )

        val codeNodes = mutableListOf<ASTNode>()

        val node = content.markdownState.state.filterIsInstance<State.Success>().first().node

        val stack = ArrayDeque<ASTNode>()
        stack.add(node)

        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            if (current.codeBlockOrNull(content.content)?.language == "runnable") {
                codeNodes.add(current)
            }
            current.children.forEach { stack.add(it) }
        }

        assertEquals(1, codeNodes.size, "There should be exactly one runnable code block.")

        val codeNode = codeNodes.first()

        val expected = """
            Hello, this is a message with runnable code.

            ```runnable
            println("Hello, World!")
            ```
            ```result
            [{"result":"Hello, World! First.","createdAt":"2026-02-16T06:00:00Z"},{"result":"Hello, World! Appended.","createdAt":"-100001-12-31T23:59:59.999999999Z"}]
            ```
        """.trimIndent()

        assertEquals(
            expected = expected,
            actual = content.appendedCodeResults(
                Triple(codeNode, "Hello, World! Appended.", Instant.DISTANT_PAST),
            ).content,
        )
    }

    @Test
    fun `test code result appending with non-runnable code block`() = runTest {
        val content = AssistantMessageContent.Completed(
            Message.Assistant(
                content = """
                    Hello, this is a message with runnable code.

                    ```runnable
                    println("Hello, World!")
                    ```

                    ```non-runnable
                    println("Hello, World!")
                    ```
                    ```result
                    [{"result":"Hello, World!","createdAt":"2026-02-16T06:00:00Z"}]
                    ```
                """.trimIndent(),
                metaInfo = ResponseMetaInfo.create(
                    object : Clock {
                        override fun now() = Instant.DISTANT_PAST
                    }.toDeprecatedClock(),
                ),
            ).toUiMessage(),
        )

        val codeNodes = mutableListOf<ASTNode>()

        val node = content.markdownState.state.filterIsInstance<State.Success>().first().node

        val stack = ArrayDeque<ASTNode>()
        stack.add(node)

        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            if (current.codeBlockOrNull(content.content)?.language == "runnable") {
                codeNodes.add(current)
            }
            current.children.forEach { stack.add(it) }
        }

        assertEquals(1, codeNodes.size, "There should be exactly one runnable code block.")

        val codeNode = codeNodes.first()

        val expected = """
            Hello, this is a message with runnable code.

            ```runnable
            println("Hello, World!")
            ```
            ```result
            [{"result":"Hello, World!","createdAt":"-100001-12-31T23:59:59.999999999Z"}]
            ```

            ```non-runnable
            println("Hello, World!")
            ```
            ```result
            [{"result":"Hello, World!","createdAt":"2026-02-16T06:00:00Z"}]
            ```
        """.trimIndent()

        assertEquals(
            expected = expected,
            actual = content.appendedCodeResults(
                Triple(codeNode, "Hello, World!", Instant.DISTANT_PAST),
            ).content,
        )
    }

    @Test
    fun `test code result appending with indent`() = runTest {
        val content = AssistantMessageContent.Completed(
            Message.Assistant(
                content = """
                    Hello, this is a message with runnable code.

                    - ```runnable
                      println("Hello, World!")
                      ```
                """.trimIndent(),
                metaInfo = ResponseMetaInfo.create(
                    object : Clock {
                        override fun now() = Instant.DISTANT_PAST
                    }.toDeprecatedClock(),
                ),
            ).toUiMessage(),
        )

        val codeNodes = mutableListOf<ASTNode>()

        val node = content.markdownState.state.filterIsInstance<State.Success>().first().node

        val stack = ArrayDeque<ASTNode>()
        stack.add(node)

        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            if (current.codeBlockOrNull(content.content)?.language == "runnable") {
                codeNodes.add(current)
            }
            current.children.forEach { stack.add(it) }
        }

        assertEquals(1, codeNodes.size, "There should be exactly one runnable code block.")

        val codeNode = codeNodes.first()

        val expected = """
            Hello, this is a message with runnable code.

            - ```runnable
              println("Hello, World!")
              ```
              ```result
              [{"result":"Hello, World!","createdAt":"-100001-12-31T23:59:59.999999999Z"}]
              ```
        """.trimIndent()

        assertEquals(
            expected = expected,
            actual = content.appendedCodeResults(
                Triple(codeNode, "Hello, World!", Instant.DISTANT_PAST),
            ).content,
        )
    }

    @Test
    fun `test code result appending with distant result`() = runTest {
        val content = AssistantMessageContent.Completed(
            Message.Assistant(
                content = """
                    Hello, this is a message with runnable code.

                    ```runnable
                    println("Hello, World!")
                    ```





                    ```result
                    [{"result":"Hello, World!","createdAt":"2026-02-16T06:00:00Z"}]
                    ```
                """.trimIndent(),
                metaInfo = ResponseMetaInfo.create(
                    object : Clock {
                        override fun now() = Instant.DISTANT_PAST
                    }.toDeprecatedClock(),
                ),
            ).toUiMessage(),
        )

        val codeNodes = mutableListOf<ASTNode>()

        val node = content.markdownState.state.filterIsInstance<State.Success>().first().node

        val stack = ArrayDeque<ASTNode>()
        stack.add(node)

        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            if (current.codeBlockOrNull(content.content)?.language == "runnable") {
                codeNodes.add(current)
            }
            current.children.forEach { stack.add(it) }
        }

        assertEquals(1, codeNodes.size, "There should be exactly one runnable code block.")

        val codeNode = codeNodes.first()

        val expected = """
            Hello, this is a message with runnable code.

            ```runnable
            println("Hello, World!")
            ```
            ```result
            [{"result":"Hello, World!","createdAt":"2026-02-16T06:00:00Z"},{"result":"Hello, World!","createdAt":"-100001-12-31T23:59:59.999999999Z"}]
            ```
        """.trimIndent()

        assertEquals(
            expected = expected,
            actual = content.appendedCodeResults(
                Triple(codeNode, "Hello, World!", Instant.DISTANT_PAST),
            ).content,
        )
    }

    @Test
    fun `test code result appending with multiple code blocks`() = runTest {
        val content = AssistantMessageContent.Completed(
            Message.Assistant(
                content = """
                    Hello, this is a message with multiple runnable code blocks.

                    ```runnable
                    println("Hello, World!")
                    ```

                    ```runnable
                    println("Hello, Again!")
                    ```
                """.trimIndent(),
                metaInfo = ResponseMetaInfo.create(
                    object : Clock {
                        override fun now() = Instant.DISTANT_PAST
                    }.toDeprecatedClock(),
                ),
            ).toUiMessage(),
        )

        val codeNodes = mutableListOf<ASTNode>()

        val stack = ArrayDeque<ASTNode>()
        stack.add(content.markdownState.state.filterIsInstance<State.Success>().first().node)

        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            if (current.codeBlockOrNull(content.content)?.language == "runnable") {
                codeNodes.add(current)
            }
            current.children.forEach { stack.add(it) }
        }

        assertEquals(2, codeNodes.size, "There should be exactly two runnable code blocks.")

        val firstCodeNode = codeNodes[0]
        val secondCodeNode = codeNodes[1]

        val expectedFirst = """
            Hello, this is a message with multiple runnable code blocks.

            ```runnable
            println("Hello, World!")
            ```
            ```result
            [{"result":"Hello, World!","createdAt":"-100001-12-31T23:59:59.999999999Z"}]
            ```

            ```runnable
            println("Hello, Again!")
            ```
        """.trimIndent()

        val expectedSecond = """
            Hello, this is a message with multiple runnable code blocks.

            ```runnable
            println("Hello, World!")
            ```

            ```runnable
            println("Hello, Again!")
            ```
            ```result
            [{"result":"Hello, Again!","createdAt":"-100001-12-31T23:59:59.999999999Z"}]
            ```
        """.trimIndent()

        assertEquals(
            expected = expectedFirst,
            actual = content.appendedCodeResults(
                Triple(firstCodeNode, "Hello, World!", Instant.DISTANT_PAST),
            ).content,
        )

        assertEquals(
            expected = expectedSecond,
            actual = content.appendedCodeResults(
                Triple(secondCodeNode, "Hello, Again!", Instant.DISTANT_PAST),
            ).content,
        )

        val expectedFinalOutput = """
            Hello, this is a message with multiple runnable code blocks.

            ```runnable
            println("Hello, World!")
            ```
            ```result
            [{"result":"Hello, World!","createdAt":"-100001-12-31T23:59:59.999999999Z"}]
            ```

            ```runnable
            println("Hello, Again!")
            ```
            ```result
            [{"result":"Hello, Again!","createdAt":"-100001-12-31T23:59:59.999999999Z"}]
            ```
        """.trimIndent()

        assertEquals(
            expected = expectedFinalOutput,
            actual = content.appendedCodeResults(
                Triple(firstCodeNode, "Hello, World!", Instant.DISTANT_PAST),
                Triple(secondCodeNode, "Hello, Again!", Instant.DISTANT_PAST),
            ).content,
        )
    }
}
