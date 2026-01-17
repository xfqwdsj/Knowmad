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
import ai.koog.prompt.message.Message
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import top.ltfan.knowmad.data.file.FileEntity
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Entity(
    indices = [
        Index("isArchived", "isPinned", "updatedAt"),
        Index("createdAt"),
    ],
)
data class ConversationEntity(
    @PrimaryKey
    val id: Uuid = Uuid.generateV7(),
    val name: String,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
)

@Entity(
    indices = [
        Index("conversationId", "parentId", "depth"),
        Index("parentId", "createdAt", "id"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MessageEntity(
    @PrimaryKey
    val id: Uuid = Uuid.generateV7(),
    val conversationId: Uuid,
    val parentId: Uuid? = null,
    val depth: Int,
    val parts: List<Message>,
    val role: MessageEntityRole,
    val searchableContent: String = parts.joinToString("\n") { it.content },
    val generatedBy: LLModel?,
    val createdAt: Instant = Clock.System.now(),
)

@Entity(
    indices = [
        Index("parentId"),
        Index("selectedChildId"),
        Index("parentId", "selectedChildId"),
        Index("conversationId", "parentId", unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["selectedChildId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MessageBranchSelectionEntity(
    @PrimaryKey
    val id: Uuid = Uuid.generateV7(),
    val conversationId: Uuid,
    val parentId: Uuid,
    val selectedChildId: Uuid,
    val updatedAt: Instant = Clock.System.now(),
)

@Entity(
    indices = [
        Index("messageId"),
        Index("fileId"),
    ],
    primaryKeys = ["messageId", "fileId"],
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FileEntity::class,
            parentColumns = ["id"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MessageFileCrossRef(
    val messageId: Uuid,
    val fileId: Uuid,
)

data class MessageWithFilesAndBranchInfo(
    @Embedded
    val message: MessageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = MessageFileCrossRef::class,
            parentColumn = "messageId",
            entityColumn = "fileId",
        ),
    )
    val files: List<FileEntity>,
    override val branchIndex: Int,
    override val branchCount: Int,
) : MessageWithBranchInfo, ChatListMessage.Branched {
    @Ignore
    override val key = message.id
}

data class MessageWithConversation(
    @Embedded
    val message: MessageEntity,
    @Relation(
        parentColumn = "conversationId",
        entityColumn = "id",
    )
    val conversation: ConversationEntity,
)

data class FileWithMessages(
    @Embedded
    val file: FileEntity,
    @Relation(
        entity = MessageEntity::class,
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = MessageFileCrossRef::class,
            parentColumn = "fileId",
            entityColumn = "messageId",
        ),
    )
    val messages: List<MessageWithConversation>,
)

@Fts4(contentEntity = MessageEntity::class)
@Entity
data class MessageFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val searchableContent: String,
)
