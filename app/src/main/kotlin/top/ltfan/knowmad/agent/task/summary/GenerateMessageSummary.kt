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

package top.ltfan.knowmad.agent.task.summary

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import android.content.res.Resources
import kotlinx.coroutines.CancellationException
import top.ltfan.knowmad.R
import top.ltfan.knowmad.agent.environmentSystemPrompt
import top.ltfan.knowmad.agent.runPromptForSimpleResult
import top.ltfan.knowmad.util.Logger

private val logger = Logger("GenerateMessageSummary")

suspend fun generateMessageSummary(
    contextMessages: List<Message>,
    targetMessages: List<Message>,
    executor: PromptExecutor,
    model: LLModel,
    resources: Resources,
    maxContextLength: Int = 2000,
): String? {
    val contextString = buildString {
        for (message in contextMessages.asReversed()) {
            val content = message.content
                .replace("\\s+".toRegex(), " ")
                .trim()
            val messageString = "[${message.role}] ${content}\n"
            if (length + messageString.length > maxContextLength) break
            insert(0, messageString)
        }
    }.trim()

    val targetString = targetMessages.joinToString("\n") { message ->
        val content = message.content.replace("\\s+".toRegex(), " ").trim()
        "[${message.role}] $content"
    }

    val contentHash = run {
        var hash = contextString.hashCode()
        hash = hash * 31 + targetString.hashCode()
        hash.toUInt().toString(36)
    }

    val prompt = prompt("message-summary-$contentHash") {
        system(resources.environmentSystemPrompt())
        user(
            resources.getString(R.string.llm_prompt_generate_message_summary_disabled_quick_reply)
                .trimIndent().format(contextString, targetString),
        )
    }

    return executor.runPromptForSimpleResult(
        model = model,
        prompt = prompt,
        beforeStart = { logger.debug { "Generating message summary with LLM..." } },
        onSuccess = { logger.debug { "Generated message summary" } },
        onFailure = {
            if (it is CancellationException) throw it
            logger.error(it) { "Error generating message summary" }
        },
    )
}
