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
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
suspend inline fun PromptExecutor.runPrompt(
    model: LLModel,
    prompt: Prompt,
    beforeStart: () -> Unit = {},
    onSuccess: (List<Message.Response>) -> Unit = {},
    onFailure: (Throwable) -> Unit = {},
): List<Message.Response>? {
    contract {
        callsInPlace(beforeStart, EXACTLY_ONCE)
        callsInPlace(onSuccess, AT_MOST_ONCE)
        callsInPlace(onFailure, AT_MOST_ONCE)
    }
    beforeStart()
    return try {
        execute(
            prompt = prompt,
            model = model,
        ).also(onSuccess)
    } catch (e: Throwable) {
        onFailure(e)
        null
    }
}

@OptIn(ExperimentalContracts::class)
suspend inline fun PromptExecutor.runPromptForSimpleResult(
    model: LLModel,
    prompt: Prompt,
    beforeStart: () -> Unit = {},
    ifEmpty: () -> Unit = {},
    onSuccess: (String) -> Unit = {},
    onFailure: (Throwable) -> Unit = {},
    transform: Sequence<Message.Response>.(
        default: Sequence<Message.Response>.() -> String,
    ) -> String = { it() },
): String? {
    contract {
        callsInPlace(beforeStart, EXACTLY_ONCE)
        callsInPlace(ifEmpty, AT_MOST_ONCE)
        callsInPlace(onSuccess, AT_MOST_ONCE)
        callsInPlace(onFailure, AT_MOST_ONCE)
        callsInPlace(transform, AT_MOST_ONCE)
    }
    beforeStart()
    return try {
        execute(
            prompt = prompt,
            model = model,
        ).asSequence().transform {
            this
                .joinToString(" ") { it.content }
                .trim()
                .replace("\\s+".toRegex(), " ")
        }.ifEmpty {
            ifEmpty()
            return null
        }.also(onSuccess)
    } catch (e: Throwable) {
        onFailure(e)
        null
    }
}
