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

package top.ltfan.knowmad.agent.client

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLModel
import okio.Path

abstract class LocalModels : LLModelDefinitions {
    abstract val basePath: Path

    abstract val modelInfos: Map<LLModel, LocalModelInfo>

    override val models: List<LLModel>
        get() = modelInfos.keys.toList()

    final override fun addCustomModel(model: LLModel) {
        throw UnsupportedOperationException("addCustomModel(LLModel) is not supported in LocalModels")
    }

    abstract fun addCustomModel(model: LLModel, info: LocalModelInfo)
}
