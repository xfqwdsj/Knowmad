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

import ai.koog.agents.core.agent.context.AIAgentLLMContext
import kotlinx.coroutines.flow.MutableSharedFlow
import top.ltfan.knowmad.ui.component.AssistantMessageStreamingEvent

interface SystemPromptInjectorTool {
    val additionalSystemPrompt: String
}

interface CodeRunnerTool {
    val components: List<String>
    suspend fun runCode(components: List<String>, code: String): String
}

interface ChatAgentContextualInitializationTool {
    suspend fun initializeWithChatAgentContext(
        llm: AIAgentLLMContext,
        eventFlow: MutableSharedFlow<AssistantMessageStreamingEvent>,
    )
}
