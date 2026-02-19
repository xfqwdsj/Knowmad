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

package top.ltfan.knowmad.agent

import ai.koog.agents.core.agent.AIAgentService
import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.agent.createAgentTool
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import android.content.res.Resources
import top.ltfan.knowmad.R

fun updateScheduleFromWebAgentTool(
    promptExecutor: PromptExecutor,
    llmModel: LLModel,
    resources: Resources,
    installFeatures: FeatureContext.() -> Unit = {},
) = updateScheduleFromWebAgentService(
    promptExecutor = promptExecutor,
    llmModel = llmModel,
    resources = resources,
    installFeatures = installFeatures,
).createAgentTool(
    agentName = resources.getString(R.string.llm_agent_update_schedule_from_web_name),
    agentDescription = resources.getString(R.string.llm_agent_update_schedule_from_web_description),
    inputDescription = resources.getString(R.string.llm_agent_update_schedule_from_web_description_input),
)

fun updateScheduleFromWebAgentService(
    promptExecutor: PromptExecutor,
    llmModel: LLModel,
    resources: Resources,
    installFeatures: FeatureContext.() -> Unit = {},
) = AIAgentService(
    promptExecutor = promptExecutor,
    llmModel = llmModel,
    strategy = updateScheduleFromWebStrategy(resources),
    systemPrompt = resources.systemPrompt(R.string.llm_agent_update_schedule_from_web_prompt),
    toolRegistry = ToolRegistry {

    },
    installFeatures = installFeatures,
)

fun updateScheduleFromWebStrategy(
    resources: Resources,
) = strategy<String, String>("schedule-from-web") {

}
