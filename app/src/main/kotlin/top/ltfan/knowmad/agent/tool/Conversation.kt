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

package top.ltfan.knowmad.agent.tool

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import android.content.res.Resources
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.chat.ChatDao
import top.ltfan.knowmad.data.chat.ConversationEntity
import top.ltfan.knowmad.util.Logger

fun ToolRegistry.Builder.conversationTools(
    resources: Resources,
    conversation: ConversationEntity,
    chatDao: ChatDao,
) {
    tool(
        ConversationTools.UpdateNameTool(
            resources = resources,
            conversation = conversation,
            chatDao = chatDao,
        ),
    )
}

object ConversationTools {
    private val logger = Logger("ConversationTools")

    class UpdateNameTool(
        resources: Resources,
        private val conversation: ConversationEntity,
        private val chatDao: ChatDao,
    ) : Tool<UpdateNameTool.Args, Boolean>(
        argsSerializer = Args.serializer(),
        resultSerializer = Boolean.serializer(),
        descriptor = ToolDescriptor(
            name = "update_conversation_name",
            description = resources.getString(R.string.llm_tool_conversation_update_name_description),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "name",
                    description = resources.getString(R.string.llm_tool_conversation_update_name_arg_name_description),
                    type = ToolParameterType.String,
                ),
            ),
        ),
    ) {
        override suspend fun execute(args: Args): Boolean {
            val updatedConversation = conversation.copy(name = args.name)
            runCatching { chatDao.updateConversation(updatedConversation) }
                .onFailure {
                    logger.error(it) { "Failed to update conversation name." }
                    return false
                }
            return true
        }

        @Serializable
        data class Args(val name: String)
    }
}
