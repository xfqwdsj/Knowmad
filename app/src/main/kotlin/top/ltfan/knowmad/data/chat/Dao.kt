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

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlin.uuid.Uuid

@Dao
interface ChatDao {
    @Insert
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Insert
    suspend fun insertMessageWithoutFiles(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessageFileCrossRef(ref: MessageFileCrossRef): Long

    @Transaction
    suspend fun insertMessage(message: MessageEntity, fileIds: List<Uuid>): Long {
        val messageRowId = insertMessageWithoutFiles(message)
        val messageId = message.id
        for (fileId in fileIds) {
            val ref = MessageFileCrossRef(
                messageId = messageId,
                fileId = fileId,
            )
            insertMessageFileCrossRef(ref)
        }
        return messageRowId
    }

    @Delete
    suspend fun deleteConversation(conversation: ConversationEntity): Int

    @Delete
    suspend fun deleteMessage(message: MessageEntity): Int

    @Delete
    suspend fun deleteMessageFileCrossRef(ref: MessageFileCrossRef): Int

    @Update
    suspend fun updateConversation(conversation: ConversationEntity): Int

    @Query("SELECT * FROM ConversationEntity")
    suspend fun getAllConversations(): List<ConversationEntity>

    @Transaction
    @Query("SELECT * FROM MessageEntity WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun getAllMessagesByConversation(conversationId: Uuid): PagingSource<Int, MessageWithFiles>

    @Transaction
    @Query("SELECT * FROM FileEntity WHERE id = :fileId")
    suspend fun getAllMessagesAssociatedWithFile(fileId: Uuid): List<FileWithMessages>
}
