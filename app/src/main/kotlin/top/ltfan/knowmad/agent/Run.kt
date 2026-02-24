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

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message

suspend fun PromptExecutor.runPrompt(
    model: LLModel,
    prompt: Prompt,
    beforeStart: (() -> Unit)? = null,
    onSuccess: ((List<Message.Assistant>) -> Unit)? = null,
    onFailure: ((Throwable) -> Unit)? = null,
): List<Message.Assistant>? {
    return runCatching {
        beforeStart?.invoke()
        execute(
            prompt = prompt,
            model = model,
        ).filterIsInstance<Message.Assistant>()
    }
        .onSuccess { onSuccess?.invoke(it) }
        .onFailure { onFailure?.invoke(it) }
        .getOrNull()
}

suspend fun PromptExecutor.runPromptForSimpleResult(
    model: LLModel,
    prompt: Prompt,
    beforeStart: (() -> Unit)? = null,
    ifEmpty: (() -> Unit)? = null,
    onSuccess: ((String) -> Unit)? = null,
    onFailure: ((Throwable) -> Unit)? = null,
): String? {
    return runCatching {
        beforeStart?.invoke()
        execute(
            prompt = prompt,
            model = model,
        ).filterIsInstance<Message.Assistant>()
            .joinToString(" ") { it.content }
            .trim()
            .replace("\\s+".toRegex(), " ")
            .ifEmpty {
                ifEmpty?.invoke()
                return null
            }
    }
        .onSuccess { onSuccess?.invoke(it) }
        .onFailure { onFailure?.invoke(it) }
        .getOrNull()
}
