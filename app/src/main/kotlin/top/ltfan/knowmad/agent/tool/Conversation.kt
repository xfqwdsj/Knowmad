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
import ai.koog.agents.core.tools.ToolRegistryBuilder
import ai.koog.serialization.typeToken
import android.content.res.Resources
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.chat.ConversationEntity
import top.ltfan.knowmad.util.Logger

fun ToolRegistryBuilder.conversationTools(
    resources: Resources,
    mutex: Mutex?,
    getConversation: suspend () -> ConversationEntity,
    setConversation: suspend (ConversationEntity) -> Unit,
) {
    tool(
        ConversationTools.UpdateNameTool(
            resources = resources,
            mutex = mutex,
            getConversation = getConversation,
            setConversation = setConversation,
        ),
    )
}

object ConversationTools {
    private val logger = Logger("ConversationTools")

    class UpdateNameTool(
        resources: Resources,
        private val mutex: Mutex?,
        private val getConversation: suspend () -> ConversationEntity,
        private val setConversation: suspend (ConversationEntity) -> Unit,
    ) : Tool<UpdateNameTool.Args, Boolean>(
        argsType = typeToken<Args>(),
        resultType = typeToken<Boolean>(),
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
            try {
                mutex?.lock()
                val updatedConversation = getConversation().copy(name = args.name)
                runCatching { setConversation(updatedConversation) }
                    .onFailure {
                        logger.error(it) { "Failed to update conversation name." }
                        return false
                    }
                return true
            } finally {
                mutex?.unlock()
            }
        }

        @Serializable
        data class Args(val name: String)
    }
}
