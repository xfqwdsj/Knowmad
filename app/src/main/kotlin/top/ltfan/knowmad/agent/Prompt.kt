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

import android.content.res.Resources
import androidx.annotation.StringRes
import kotlinx.datetime.TimeZone
import top.ltfan.knowmad.R
import java.util.Locale

@Suppress("NOTHING_TO_INLINE")
inline fun Resources.systemPrompt(
    @StringRes taskId: Int,
    taskFormatArgs: Array<Any> = emptyArray(),
    @StringRes headId: Int = R.string.llm_prompt_head,
    headFormatArgs: Array<Any> = emptyArray(),
    @StringRes introId: Int = R.string.llm_prompt_intro_short,
    introFormatArgs: Array<Any> = emptyArray(),
    environment: String? = environmentSystemPrompt(),
) = buildString {
    if (environment != null) {
        appendLine(environment)
        appendLine()
    }
    appendLine(getString(headId).trimIndent().format(*headFormatArgs))
    appendLine()
    appendLine(getString(introId).trimIndent().format(*introFormatArgs))
    appendLine()
    appendLine(getString(taskId).trimIndent().format(*taskFormatArgs))
}

@Suppress("NOTHING_TO_INLINE")
inline fun Resources.environmentSystemPrompt(
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) = getString(R.string.llm_prompt_environment_system).trimIndent()
    .format(
        locale.toLanguageTag(),
        timeZone.id,
    )
