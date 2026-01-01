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

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Dao
interface ChatDao {
    @Insert
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Insert
    suspend fun insertMessageInternalUnsafe(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessageFileCrossRef(ref: MessageFileCrossRef): Long

    @Transaction
    suspend fun insertMessage(message: MessageEntity, fileIds: List<Uuid>): Long {
        val messageRowId = insertMessageInternalUnsafe(message)
        val messageId = message.id

        for (fileId in fileIds) {
            val ref = MessageFileCrossRef(
                messageId = messageId,
                fileId = fileId,
            )
            insertMessageFileCrossRef(ref)
        }

        message.parentId?.let { parentId ->
            val selection = MessageBranchSelectionEntity(
                conversationId = message.conversationId,
                parentId = parentId,
                selectedChildId = message.id,
            )
            setMessageBranchSelection(selection)
        }

        return messageRowId
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMessageBranchSelection(selection: MessageBranchSelectionEntity)

    @Delete
    suspend fun deleteConversation(conversation: ConversationEntity): Int

    @Delete
    suspend fun deleteMessage(message: MessageEntity): Int

    @Delete
    suspend fun deleteMessageFileCrossRef(ref: MessageFileCrossRef): Int

    @Update
    suspend fun updateConversationWithoutInstant(conversation: ConversationEntity): Int

    @Transaction
    suspend fun updateConversation(conversation: ConversationEntity): Int {
        val updatedConversation = conversation.copy(
            updatedAt = Clock.System.now(),
        )
        return updateConversationWithoutInstant(updatedConversation)
    }

    @Query("SELECT * FROM ConversationEntity WHERE isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllConversations(): PagingSource<Int, ConversationEntity>

    @Query("SELECT * FROM ConversationEntity WHERE id = :conversationId")
    fun getConversationById(conversationId: Uuid): Flow<ConversationEntity?>

    @Query("SELECT * FROM MessageEntity WHERE id = :messageId")
    suspend fun getMessageById(messageId: Uuid): MessageEntity?

    @Transaction
    @Query(
        """
        WITH RECURSIVE SelectedPath AS (
            SELECT * FROM MessageEntity
            WHERE conversationId = :conversationId AND parentId IS NULL

            UNION ALL

            SELECT child.* FROM MessageEntity AS child
            JOIN SelectedPath AS parent ON child.parentId = parent.id 
                AND child.depth = parent.depth + 1
                AND child.conversationId = parent.conversationId
            JOIN MessageBranchSelectionEntity AS bs ON bs.parentId = parent.id AND bs.selectedChildId = child.id
        )

        SELECT 
            sp.*,
            (
                SELECT COUNT(*) + 1 
                FROM MessageEntity AS s 
                WHERE (s.parentId IS sp.parentId)
                  AND s.createdAt < sp.createdAt
            ) AS branchIndex,

            (
                SELECT COUNT(*) 
                FROM MessageEntity AS s 
                WHERE (s.parentId IS sp.parentId)
            ) AS branchCount

        FROM SelectedPath AS sp
        ORDER BY sp.depth ASC
    """,
    )
    fun getAllMessagesByConversation(conversationId: Uuid): PagingSource<Int, MessageWithFilesAndBranchInfo>

    @Query(
        """
        SELECT id FROM MessageEntity 
        WHERE (parentId IS :parentId) 
        ORDER BY createdAt ASC 
        LIMIT 1 OFFSET :targetIndex
    """,
    )
    suspend fun getSiblingMessageIdByIndex(parentId: Uuid?, targetIndex: Int): Uuid?

    @Transaction
    @Query("SELECT * FROM FileEntity WHERE id = :fileId")
    suspend fun getAllMessagesAssociatedWithFile(fileId: Uuid): List<FileWithMessages>

    @Transaction
    @Query(
        """
        SELECT MessageEntity.* FROM MessageEntity
        JOIN MessageFtsEntity ON MessageEntity.rowid = MessageFtsEntity.rowid
        WHERE MessageFtsEntity MATCH :query
    """,
    )
    suspend fun searchMessagesInternal(query: String): List<MessageEntity>

    @Transaction
    suspend fun searchMessages(query: String): List<MessageEntity> {
        // SQLite FTS uses double quotes to wrap phrases and operators like AND, OR, NOT.
        // Raw user input might contain special characters that cause syntax errors.
        // We sanitize the input by:
        // 1. Removing double quotes to prevent syntax errors.
        // 2. Splitting by whitespace.
        // 3. Appending '*' to each token for prefix matching (search-as-you-type behavior).
        val sanitized = query.replace("\"", "")
            .split(Regex("\\s+"))
            .asSequence()
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }

        if (sanitized.isBlank()) return emptyList()

        return searchMessagesInternal(sanitized)
    }
}
