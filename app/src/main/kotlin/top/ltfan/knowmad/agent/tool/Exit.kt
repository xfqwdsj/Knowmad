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
import ai.koog.serialization.typeToken
import android.content.res.Resources
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.R

class ExitTool(resources: Resources) : Tool<ExitTool.Args, String>(
    argsType = typeToken<Args>(),
    resultType = typeToken<String>(),
    descriptor = ToolDescriptor(
        name = name,
        description = resources.getString(R.string.llm_tool_exit_description),
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "message",
                description = resources.getString(R.string.llm_tool_exit_arg_message_description),
                type = ToolParameterType.String,
            ),
        ),
    ),
) {
    companion object {
        @Suppress("ConstPropertyName")
        const val name = "__exit__"
    }

    override suspend fun execute(args: Args) = args.message

    @Serializable
    data class Args(
        val message: String,
    )
}
