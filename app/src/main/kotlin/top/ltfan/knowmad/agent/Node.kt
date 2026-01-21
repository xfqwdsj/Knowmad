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

package top.ltfan.knowmad.agent

import ai.koog.agents.core.agent.context.DetachedPromptExecutorAPI
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import android.content.res.Resources
import top.ltfan.knowmad.R

@OptIn(DetachedPromptExecutorAPI::class)
@AIAgentBuilderDslMarker
inline fun <reified T> AIAgentSubgraphBuilderBase<*, *>.nodeLLMGenerateConversationTitle(
    name: String? = null,
    resources: Resources,
    crossinline onGenerated: (String) -> Unit,
) = node<T, T>(name) { ignored ->
    val chatMessages = llm.readSession { prompt }.messages
        .filterNot { message -> message is Message.System }
        .joinToString("\n\n") { it.content }

    val prompt = prompt("conversation-title") {
        system(
            resources.getString(R.string.llm_prompt_generate_conversation_title, chatMessages),
        )
    }

    runCatching {
        llm.promptExecutor.execute(
            prompt = prompt,
            model = llm.model,
        )
            .filterIsInstance<Message.Assistant>()
            .joinToString(" ") { it.content }
            .trim()
            .replace("\\s+".toRegex(), " ")
            .ifEmpty { error("LLM generated empty title") }
    }
        .onSuccess { onGenerated(it) }
        .onFailure { it.printStackTrace() }

    ignored
}
