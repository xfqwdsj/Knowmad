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
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.datetime.toDeprecatedClock
import top.ltfan.knowmad.data.chat.toUiMessage
import kotlin.test.Test
import kotlin.test.assertEquals

class UiMessageModificationTest {
    @Test
    fun `test message modification`() {
        val message = Message.Assistant(
            parts = listOf(
                ContentPart.Text("0123456789"),
                ContentPart.File(
                    content = AttachmentContent.PlainText("file"),
                    format = "file",
                    mimeType = "text/plain",
                ),
                ContentPart.Text("0123456789"),
            ),
            metaInfo = ResponseMetaInfo.create(TestClock.toDeprecatedClock()),
        ).toUiMessage()

        val modifiedMessage = message.modifiedContent { _, modify ->
            modify(2, 8, "76543210")
            modify(8, 10, "")
            modify(11, 21, "9876543210")
        }

        val expected = Message.Assistant(
            parts = listOf(
                ContentPart.Text("0176543210"),
                ContentPart.File(
                    content = AttachmentContent.PlainText("file"),
                    format = "file",
                    mimeType = "text/plain",
                ),
                ContentPart.Text("9876543210"),
            ),
            metaInfo = ResponseMetaInfo.create(TestClock.toDeprecatedClock()),
        ).toUiMessage()

        assertEquals(expected, modifiedMessage)
    }
}
