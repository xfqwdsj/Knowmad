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

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import top.ltfan.knowmad.data.FtsDao
import top.ltfan.knowmad.notification.pushChatShortcut
import top.ltfan.knowmad.notification.removeChatShortcut
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Dao
interface ChatDao : FtsDao {
    @Insert
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Transaction
    suspend fun insertConversation(conversation: ConversationEntity, context: Context): Long {
        context.pushChatShortcut(conversation.id, conversation.name)
        return insertConversation(conversation)
    }

    @Insert
    suspend fun insertMessageInternalUnsafe(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessageFileCrossRef(ref: MessageFileCrossRef): Long

    @Transaction
    suspend fun insertMessageWithoutSettingBranch(
        message: MessageEntity,
        fileIds: List<Uuid>,
    ): Long {
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

    @Transaction
    suspend fun insertMessage(
        message: MessageEntity,
        fileIds: List<Uuid>,
    ): Long {
        val lastMessage = getLastMessageInCurrentTreeByConversation(message.conversationId)
        val updatedMessage = message.copy(
            parentId = lastMessage?.id,
            depth = if (lastMessage == null) 0 else lastMessage.depth + 1,
        )
        return insertMessageWithoutSettingBranch(
            message = updatedMessage,
            fileIds = fileIds,
        )
    }

    @Transaction
    suspend fun insertMessageAndGet(
        message: MessageEntity,
        fileIds: List<Uuid>,
        getUpdatedEntity: (MessageWithFilesAndBranchInfo?) -> Unit,
    ): Long {
        return insertMessage(
            message = message,
            fileIds = fileIds,
        ).also {
            getUpdatedEntity(getMessageWithFilesAndBranchInfoById(message.id))
        }
    }

    @Transaction
    suspend fun insertSiblingMessage(
        anchorMessageId: Uuid,
        message: MessageEntity,
        fileIds: List<Uuid>,
    ): Long {
        val anchorMessage = getMessageById(anchorMessageId)
        requireNotNull(anchorMessage) { "Anchor message with ID $anchorMessageId not found" }
        require(anchorMessage.conversationId == message.conversationId) { "Anchor message must be in the same conversation" }

        val updatedMessage = message.copy(
            parentId = anchorMessage.parentId,
            depth = anchorMessage.depth,
        )

        return insertMessageWithoutSettingBranch(
            message = updatedMessage,
            fileIds = fileIds,
        )
    }

    @Transaction
    suspend fun insertSiblingMessageAndGet(
        anchorMessageId: Uuid,
        message: MessageEntity,
        fileIds: List<Uuid>,
        getUpdatedEntity: (MessageWithFilesAndBranchInfo?) -> Unit,
    ): Long {
        return insertSiblingMessage(
            anchorMessageId = anchorMessageId,
            message = message,
            fileIds = fileIds,
        ).also {
            getUpdatedEntity(getMessageWithFilesAndBranchInfoById(message.id))
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMessageBranchSelection(selection: MessageBranchSelectionEntity)

    @Transaction
    suspend fun selectMessageOnBranch(selectedMessageId: Uuid) {
        val message = getMessageById(selectedMessageId) ?: return
        val parentId = message.parentId ?: return

        val selection = MessageBranchSelectionEntity(
            conversationId = message.conversationId,
            parentId = parentId,
            selectedChildId = selectedMessageId,
        )
        setMessageBranchSelection(selection)
    }

    @Transaction
    suspend fun selectPreviousMessageOnBranch(currentMessageId: Uuid) {
        val currentMessage = getMessageWithFilesAndBranchInfoById(currentMessageId) ?: return
        val parentId = currentMessage.message.parentId ?: return

        // branchIndex is 1-based; convert to zero-based for offset query
        val previousIndex = currentMessage.branchIndex - 2
        if (previousIndex < 0) return

        val previousSiblingId = getSiblingMessageIdByIndex(parentId, previousIndex) ?: return
        selectMessageOnBranch(previousSiblingId)
    }

    @Transaction
    suspend fun selectNextMessageOnBranch(currentMessageId: Uuid) {
        val currentMessage = getMessageWithFilesAndBranchInfoById(currentMessageId) ?: return
        val parentId = currentMessage.message.parentId ?: return

        // branchIndex is 1-based; convert to zero-based for offset query
        val nextIndex = currentMessage.branchIndex
        if (nextIndex >= currentMessage.branchCount) return

        val nextSiblingId = getSiblingMessageIdByIndex(parentId, nextIndex) ?: return
        selectMessageOnBranch(nextSiblingId)
    }

    @Delete
    suspend fun deleteConversation(conversation: ConversationEntity): Int

    @Transaction
    suspend fun deleteConversation(conversation: ConversationEntity, context: Context): Int {
        context.removeChatShortcut(conversation.id)
        return deleteConversation(conversation)
    }

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

    @Transaction
    suspend fun updateConversation(conversation: ConversationEntity, context: Context): Int {
        context.pushChatShortcut(conversation.id, conversation.name)
        return updateConversation(conversation)
    }

    @Update
    suspend fun updateMessageInternal(message: MessageEntity): Int

    @Transaction
    suspend fun updateMessage(message: MessageEntity): Int {
        val updatedMessage = message.copy(
            searchableContent = MessageEntity.getSearchableContent(message.parts),
        )
        return updateMessageInternal(updatedMessage)
    }

    @Query("SELECT * FROM ConversationEntity WHERE isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllConversations(): PagingSource<Int, ConversationEntity>

    @Query("SELECT * FROM ConversationEntity WHERE id = :conversationId")
    fun getConversationFlowById(conversationId: Uuid): Flow<ConversationEntity?>

    @Query("SELECT * FROM ConversationEntity WHERE id = :conversationId")
    suspend fun getConversationById(conversationId: Uuid): ConversationEntity?

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
            COUNT(CASE WHEN s.createdAt < sp.createdAt THEN 1 END) + 1 AS branchIndex,
            COUNT(s.id) AS branchCount
        FROM SelectedPath AS sp
        LEFT JOIN MessageEntity AS s 
            ON s.conversationId = sp.conversationId
            AND s.parentId IS sp.parentId
        GROUP BY sp.id
        ORDER BY sp.depth ASC
    """,
    )
    fun getAllMessagesByConversation(conversationId: Uuid): Flow<List<MessageWithFilesAndBranchInfo>>

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

        SELECT *
        FROM SelectedPath
        ORDER BY depth DESC
        LIMIT 1
    """,
    )
    suspend fun getLastMessageInCurrentTreeByConversation(conversationId: Uuid): MessageEntity?

    @Transaction
    @Query(
        """
        SELECT
            m.*,
            COUNT(CASE WHEN s.createdAt < m.createdAt THEN 1 END) + 1 AS branchIndex,
            COUNT(s.id) AS branchCount
        FROM MessageEntity AS m
        LEFT JOIN MessageEntity AS s 
            ON s.conversationId = m.conversationId 
            AND s.parentId IS m.parentId
        WHERE m.id = :messageId
        GROUP BY m.id
    """,
    )
    suspend fun getMessageWithFilesAndBranchInfoById(messageId: Uuid): MessageWithFilesAndBranchInfo?

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

        SELECT COUNT(*) FROM SelectedPath
    """,
    )
    fun getMessageCountFlowInCurrentTreeByConversation(conversationId: Uuid): Flow<Int>

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

        SELECT COUNT(*) FROM SelectedPath
    """,
    )
    suspend fun getMessageCountInCurrentTreeByConversation(conversationId: Uuid): Int

    @Query("SELECT COUNT(*) FROM MessageEntity WHERE conversationId = :conversationId AND completed = 1")
    suspend fun getMessageCountByConversation(conversationId: Uuid): Int

    @Query(
        """
        SELECT id FROM MessageEntity 
            WHERE conversationId = (SELECT conversationId FROM MessageEntity WHERE id = :parentId OR (id IS NULL AND :parentId IS NULL) LIMIT 1)
              AND (parentId IS :parentId) 
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
        val sanitized = query.sanitizeForFts().ifBlank { return emptyList() }
        return searchMessagesInternal(sanitized)
    }
}
