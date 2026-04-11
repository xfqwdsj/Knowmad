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

package top.ltfan.knowmad.agent.client.executorch

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMEmbeddingProviderAPI
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import okio.Path
import org.pytorch.executorch.Module
import top.ltfan.knowmad.agent.client.AgentClientBasePath
import top.ltfan.knowmad.agent.client.DownloadSource
import top.ltfan.knowmad.agent.client.executorch.model.Qwen3Embedding06BModels

val ExecuTorchClientBasePath = AgentClientBasePath / "executorch"

class ExecuTorchClient(
    private val downloadSource: DownloadSource,
    private val basePath: Path,
) : LLMClient(), LLMEmbeddingProviderAPI {
    override suspend fun embed(text: String, model: LLModel): List<Double> {
        model.requireCapability(Embed)

        val basePath = basePath / ExecuTorchClientBasePath

        val info = modelInfos[model]

        require(info != null) { "Model ${model.id} is not recognized by ExecuTorchClient" }

        if (info.downloader.validate(downloadSource, basePath) !is Existing) {
            error("Model ${model.id} is currently not available")
        }

        val modelPath = basePath / model.id

        return info.createTokenizer(basePath).use { tokenizer ->
            val (ids, attentionMask) = tokenizer.encode(text)

            val idsEValue = ids.toTensor().toEValue()
            val attentionMaskEValue = attentionMask.toTensor().toEValue()

            val module = Module.load(modelPath.toString())

            module.forward(idsEValue, attentionMaskEValue)
                .first().toTensor().dataAsDoubleArray.toList().also {
                    module.destroy()
                }
        }
    }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ) = throw UnsupportedOperationException("Not implemented for this client")

    override suspend fun moderate(prompt: Prompt, model: LLModel) =
        throw UnsupportedOperationException("Not implemented for this client")

    override fun llmProvider() = ExecuTorchLLMProvider

    val models = setOf(
        Qwen3Embedding06BModels,
    )

    val modelInfos = models.flatMap { it.modelInfos.entries }.associate { it.key to it.value }

    override suspend fun models() = modelInfos.keys.toList()

    override fun close() {}

    @Suppress("NOTHING_TO_INLINE")
    private inline fun LLModel.requireCapability(capability: LLMCapability) {
        require(supports(capability)) { "Model $id does not support capability $capability" }
    }
}

object ExecuTorchLLMProvider : LLMProvider("km-executorch", "Knowmad ExecuTorch")
