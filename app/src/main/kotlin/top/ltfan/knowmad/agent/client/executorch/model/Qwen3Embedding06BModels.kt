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

package top.ltfan.knowmad.agent.client.executorch.model

import ai.koog.prompt.llm.LLModel
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.copyTo
import okio.FileSystem
import okio.Path
import top.ltfan.knowmad.agent.client.DownloadSource
import top.ltfan.knowmad.agent.client.Downloader
import top.ltfan.knowmad.agent.client.LocalModelInfo
import top.ltfan.knowmad.agent.client.LocalModels
import top.ltfan.knowmad.agent.client.ModelsWithTokenizer
import top.ltfan.knowmad.agent.client.executorch.ExecuTorchClientBasePath
import top.ltfan.knowmad.agent.client.executorch.ExecuTorchLLMProvider
import top.ltfan.knowmad.agent.tokenizer.HuggingFaceTokenizer
import top.ltfan.knowmad.modelscope.ModelScopeApi
import top.ltfan.knowmad.util.Logger
import kotlin.io.path.outputStream

private val logger = Logger("Qwen3Embedding06BModels")

object Qwen3Embedding06BModels : LocalModels(), ModelsWithTokenizer {
    override val basePath = ExecuTorchClientBasePath / "qwen3_embedding_0.6b"

    override val modelInfos = mapOf(
        ModelInfo(
            idSuffix = "8da4w_128",
            contextLength = 128,
        ).entry,
        ModelInfo(
            idSuffix = "8da4w_256",
            contextLength = 256,
        ).entry,
        ModelInfo(
            idSuffix = "8da4w_512",
            contextLength = 512,
        ).entry,
        ModelInfo(
            idSuffix = "8da4w_1024",
            contextLength = 1024,
        ).entry,
    )

    override val tokenizerDownloader = Downloader {
        DownloadSource.ModelScope { basePath ->
            ModelScopeApi {
                val repoId = "Qwen/Qwen3-Embedding-0.6B"

                val basePath = basePath / Qwen3Embedding06BModels.basePath

                run {
                    val fileName = "tokenizer.json"
                    val path = basePath / fileName

                    val response = getFile(
                        repoId = repoId,
                        filePath = fileName,
                    )

                    if (!response.status.isSuccess()) {
                        throw RuntimeException("Failed to download tokenizer: ${response.status}")
                    }

                    path.toNioPath().outputStream().use { output ->
                        response.bodyAsChannel().copyTo(output)
                    }

                    logger.debug { "Downloaded tokenizer to $path" }
                }

                run {
                    val fileName = "tokenizer_config.json"
                    val path = basePath / fileName

                    val response = getFile(
                        repoId = repoId,
                        filePath = fileName,
                    )

                    if (!response.status.isSuccess()) {
                        throw RuntimeException("Failed to download tokenizer config: ${response.status}")
                    }

                    path.toNioPath().outputStream().use { output ->
                        response.bodyAsChannel().copyTo(output)
                    }

                    logger.debug { "Downloaded tokenizer config to $path" }
                }
            }
        } validateWith { basePath, enforce ->
            val fs = FileSystem.SYSTEM

            val tokenizerPath = basePath / Qwen3Embedding06BModels.basePath / "tokenizer.json"
            val configPath = basePath / Qwen3Embedding06BModels.basePath / "tokenizer_config.json"

            when {
                !fs.exists(tokenizerPath) -> Downloader.ValidationResult.Existing.Invalid("Tokenizer file not found at $tokenizerPath")
                !fs.exists(configPath) -> Downloader.ValidationResult.Existing.Invalid("Tokenizer config file not found at $configPath")
                else -> Downloader.ValidationResult.Existing.NotValidated
            }
        }
    }

    override fun addCustomModel(model: LLModel, info: LocalModelInfo) {
        throw UnsupportedOperationException("Custom models are not supported in Qwen3Embedding06BModels")
    }

    private class ModelInfo(
        idSuffix: String,
        contextLength: Long,
    ) : LocalModelInfo {
        val fileName = "qwen3_embedding_0.6b_${idSuffix}.pte"

        val model = LLModel(
            provider = ExecuTorchLLMProvider,
            id = "qwen3_embedding_0.6b/$fileName",
            capabilities = listOf(Embed),
            contextLength = contextLength,
        )

        val entry = model to this as LocalModelInfo

        override val downloader = tokenizerDownloader then Downloader {
            DownloadSource.ModelScope { basePath ->
                ModelScopeApi {
                    val basePath = basePath / Qwen3Embedding06BModels.basePath
                    val path = basePath / fileName

                    val response = getFile(
                        repoId = "HMLTFan/qwen3-embedding-0.6b-cpu-int8-dynamic-8da4w-executorch",
                        filePath = fileName,
                    )

                    if (!response.status.isSuccess()) {
                        throw RuntimeException("Failed to download model ${model.id}: ${response.status}")
                    }

                    path.toNioPath().outputStream().use { output ->
                        response.bodyAsChannel().copyTo(output)
                    }

                    logger.debug { "Downloaded model ${model.id} to $path" }
                }
            } validateWith { basePath, enforce ->
                val fs = FileSystem.SYSTEM

                val path = basePath / Qwen3Embedding06BModels.basePath / fileName

                when {
                    !fs.exists(path) -> Downloader.ValidationResult.Existing.Invalid("Model file not found at $path")
                    else -> Downloader.ValidationResult.Existing.NotValidated
                }
            }
        }

        override fun createTokenizer(basePath: Path) = HuggingFaceTokenizer(
            tokenizerJson = basePath / Qwen3Embedding06BModels.basePath / "tokenizer.json",
            tokenizerConfigJson = basePath / Qwen3Embedding06BModels.basePath / "tokenizer_config.json",
        )
    }
}
