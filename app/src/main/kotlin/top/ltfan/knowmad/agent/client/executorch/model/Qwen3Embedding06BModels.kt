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
import io.ktor.client.call.body
import io.ktor.client.plugins.onDownload
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import okio.FileSystem
import okio.HashingSource
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
import top.ltfan.knowmad.modelscope.ModelScopeFilesResponse
import top.ltfan.knowmad.util.Logger
import top.ltfan.knowmad.util.useResultBlackholeReading

private val logger = Logger("Qwen3Embedding06BModels")

object Qwen3Embedding06BModels : LocalModels(), ModelsWithTokenizer {
    override val basePath = ExecuTorchClientBasePath / "qwen3_embedding_0.6b"

    override val tokenizerDownloader = Downloader {
        DownloadSource.ModelScope(Tokenizer) {
            ModelScopeApi {
                val repoId = "Qwen/Qwen3-Embedding-0.6B"

                val fs = FileSystem.SYSTEM
                val basePath = basePath / Qwen3Embedding06BModels.basePath

                run {
                    val fileName = "tokenizer.json"
                    val path = basePath / fileName

                    path.parent?.let { fs.createDirectories(it) } ?: run {
                        logger.warn { "Tokenizer file path $path does not have a parent directory" }
                    }

                    prepareGetFile(
                        repoId = repoId,
                        filePath = fileName,
                        buildRequest = { onDownload(progressListener) },
                    ).execute { response ->
                        if (!response.status.isSuccess()) {
                            throw RuntimeException("Failed to download tokenizer: ${response.status}")
                        }

                        response.bodyAsChannel().copyAndClose(path.toFile().writeChannel())

                        logger.debug { "Downloaded tokenizer to $path" }
                    }
                }

                run {
                    val fileName = "tokenizer_config.json"
                    val path = basePath / fileName

                    path.parent?.let { fs.createDirectories(it) } ?: run {
                        logger.warn { "Tokenizer config file path $path does not have a parent directory" }
                    }

                    prepareGetFile(
                        repoId = repoId,
                        filePath = fileName,
                        buildRequest = { onDownload(progressListener) },
                    ).execute { response ->
                        if (!response.status.isSuccess()) {
                            throw RuntimeException("Failed to download tokenizer config: ${response.status}")
                        }

                        response.bodyAsChannel().copyAndClose(path.toFile().writeChannel())

                        logger.debug { "Downloaded tokenizer config to $path" }
                    }
                }
            }
        } validateWith {
            val fs = FileSystem.SYSTEM
            val basePath = basePath / Qwen3Embedding06BModels.basePath

            val tokenizerName = "tokenizer.json"
            val configName = "tokenizer_config.json"

            val tokenizerPath = basePath / tokenizerName
            val configPath = basePath / configName

            when {
                !fs.exists(tokenizerPath) -> {
                    return@validateWith Downloader.ValidationResult.NotExisting("Tokenizer file not found at $tokenizerPath")
                }

                !fs.exists(configPath) -> {
                    return@validateWith Downloader.ValidationResult.NotExisting("Tokenizer config file not found at $configPath")
                }
            }

            val tokenizerSize = fs.metadata(tokenizerPath).size ?: 0L
            val configSize = fs.metadata(configPath).size ?: 0L

            when {
                tokenizerSize == 0L -> {
                    return@validateWith Downloader.ValidationResult.NotExisting("Tokenizer file at $tokenizerPath is empty")
                }

                configSize == 0L -> {
                    return@validateWith Downloader.ValidationResult.NotExisting("Tokenizer config file at $configPath is empty")
                }
            }

            if (level == Offline) return@validateWith Downloader.ValidationResult.Existing.NotValidated

            ModelScopeApi {
                val repoId = "Qwen/Qwen3-Embedding-0.6B"

                val response = listFiles(repoId, recursive = true).body<ModelScopeFilesResponse>()

                if (!response.success || response.data == null) {
                    logger.warn { "Failed to list files in model scope repo $repoId: ${response.message}" }
                    return@ModelScopeApi Downloader.ValidationResult.Existing.NotValidated
                }

                val files = response.data.files.associateBy { it.name }

                run {
                    val file = files[tokenizerName]
                    if (file == null) {
                        logger.warn { "Tokenizer file $tokenizerName not found in model scope repo $repoId" }
                        return@ModelScopeApi Downloader.ValidationResult.Existing.NotValidated
                    }
                    if (file.size != tokenizerSize) {
                        return@ModelScopeApi Downloader.ValidationResult.Existing.Invalid("Tokenizer file size mismatch: local size $tokenizerSize, remote size ${file.size}")
                    }
                    if (level == SizeOnly) return@run

                    val sha256 = HashingSource.sha256(fs.source(tokenizerPath))
                        .useResultBlackholeReading().hex()
                    if (sha256 != file.sha256) {
                        return@ModelScopeApi Downloader.ValidationResult.Existing.Invalid("Tokenizer file SHA256 mismatch: local $sha256, remote ${file.sha256}")
                    }
                }

                run {
                    val file = files[configName]
                    if (file == null) {
                        logger.warn { "Tokenizer config file $configName not found in model scope repo $repoId" }
                        return@ModelScopeApi Downloader.ValidationResult.Existing.NotValidated
                    }
                    if (file.size != configSize) {
                        return@ModelScopeApi Downloader.ValidationResult.Existing.Invalid("Tokenizer config file size mismatch: local size $configSize, remote size ${file.size}")
                    }
                    if (level == SizeOnly) return@run

                    val sha256 = HashingSource.sha256(fs.source(configPath))
                        .useResultBlackholeReading().hex()
                    if (sha256 != file.sha256) {
                        return@ModelScopeApi Downloader.ValidationResult.Existing.Invalid("Tokenizer config file SHA256 mismatch: local $sha256, remote ${file.sha256}")
                    }
                }

                Downloader.ValidationResult.Existing.Valid
            }
        }
    }

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
            DownloadSource.ModelScope(Model) {
                ModelScopeApi {
                    val fs = FileSystem.SYSTEM

                    val basePath = basePath / Qwen3Embedding06BModels.basePath
                    val path = basePath / fileName

                    path.parent?.let { fs.createDirectories(it) } ?: run {
                        logger.warn { "Model file path $path does not have a parent directory" }
                    }

                    prepareGetFile(
                        repoId = "HMLTFan/qwen3-embedding-0.6b-cpu-int8-dynamic-8da4w-executorch",
                        filePath = fileName,
                        buildRequest = { onDownload(progressListener) },
                    ).execute { response ->
                        if (!response.status.isSuccess()) {
                            throw RuntimeException("Failed to download model ${model.id}: ${response.status}")
                        }

                        response.bodyAsChannel().copyAndClose(path.toFile().writeChannel())

                        logger.debug { "Downloaded model ${model.id} to $path" }
                    }
                }
            } validateWith {
                val fs = FileSystem.SYSTEM
                val basePath = basePath / Qwen3Embedding06BModels.basePath

                val path = basePath / fileName

                if (!fs.exists(path)) {
                    return@validateWith Downloader.ValidationResult.NotExisting("Model file not found at $path")
                }

                val size = fs.metadata(path).size ?: 0L
                if (size == 0L) {
                    return@validateWith Downloader.ValidationResult.NotExisting("Model file at $path is empty")
                }

                if (level == Offline) return@validateWith Downloader.ValidationResult.Existing.NotValidated

                ModelScopeApi {
                    val repoId = "HMLTFan/qwen3-embedding-0.6b-cpu-int8-dynamic-8da4w-executorch"

                    val response =
                        listFiles(repoId, recursive = true).body<ModelScopeFilesResponse>()

                    if (!response.success || response.data == null) {
                        logger.warn { "Failed to list files in model scope repo $repoId: ${response.message}" }
                        return@ModelScopeApi Downloader.ValidationResult.Existing.NotValidated
                    }

                    val file = response.data.files.find { it.name == fileName }
                    if (file == null) {
                        logger.warn { "Model file $fileName not found in model scope repo $repoId" }
                        return@ModelScopeApi Downloader.ValidationResult.Existing.NotValidated
                    }
                    if (file.size != size) {
                        return@ModelScopeApi Downloader.ValidationResult.Existing.Invalid("Model file size mismatch: local size $size, remote size ${file.size}")
                    }
                    if (level == SizeOnly) return@ModelScopeApi Downloader.ValidationResult.Existing.Valid

                    val sha256 = HashingSource.sha256(fs.source(path))
                        .useResultBlackholeReading().hex()
                    if (sha256 != file.sha256) {
                        return@ModelScopeApi Downloader.ValidationResult.Existing.Invalid("Model file SHA256 mismatch: local $sha256, remote ${file.sha256}")
                    }

                    Downloader.ValidationResult.Existing.Valid
                }
            }
        }

        override fun createTokenizer(basePath: Path) = HuggingFaceTokenizer(
            tokenizerJson = basePath / Qwen3Embedding06BModels.basePath / "tokenizer.json",
            tokenizerConfigJson = basePath / Qwen3Embedding06BModels.basePath / "tokenizer_config.json",
        )
    }
}
