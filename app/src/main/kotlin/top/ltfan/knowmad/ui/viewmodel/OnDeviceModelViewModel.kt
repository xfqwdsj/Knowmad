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

package top.ltfan.knowmad.ui.viewmodel

import ai.koog.prompt.llm.LLModel
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import top.ltfan.knowmad.agent.client.DownloadSource
import top.ltfan.knowmad.agent.client.Downloader
import top.ltfan.knowmad.agent.client.executorch.ExecuTorchClient
import top.ltfan.knowmad.agent.client.executorch.ExecuTorchClientBasePath
import top.ltfan.knowmad.application.KnowmadApplication
import top.ltfan.knowmad.data.llm.LLMConfigDao
import top.ltfan.knowmad.data.llm.LLMConfigEntity
import top.ltfan.knowmad.data.ondevice.DownloadSourceOrder
import top.ltfan.knowmad.data.transform
import kotlin.time.Clock
import kotlin.uuid.Uuid

class OnDeviceModelViewModel(
    app: KnowmadApplication,
    private val providerConfigId: Uuid,
    private val llmConfigDao: LLMConfigDao,
) : AndroidViewModel<KnowmadApplication>(app) {
    val modelInfos = ExecuTorchClient.modelInfos

    private val basePath = app.filesDir.toOkioPath()

    private val sourceOrderStore = DownloadSourceOrder.createDataStore()
    private val sourceOrderFlow = sourceOrderStore.dataStateFlow()
    private val sourceOrderState = sourceOrderStore.asMutableState(sourceOrderFlow.value)
    var sourceOrder: List<DownloadSource> by sourceOrderState.transform(
        transformIn = { toOrderedSources() },
        transformOut = { DownloadSourceOrder(it) },
    )

    private val _modelStates = mutableStateMapOf<LLModel, OnDeviceModelState>()
    val modelStates: Map<LLModel, OnDeviceModelState> get() = _modelStates

    private val downloadJobs = mutableMapOf<LLModel, Job>()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            for ((model, info) in modelInfos) {
                _modelStates[model] = validateWithSources(info.downloader)
            }
        }
    }

    private suspend fun validateWithSources(
        downloader: Downloader,
        level: Downloader.ValidationLevel = Offline,
    ): OnDeviceModelState {
        for (source in sourceOrder) {
            try {
                val result = downloader.validate(source, basePath, level)
                if (result is NoValidation) continue
                return OnDeviceModelState.fromValidationResult(result, level)
            } catch (_: Downloader.UnsupportedSourceException) {
                continue
            }
        }
        return NotDownloaded
    }

    fun downloadModel(model: LLModel) {
        val info = modelInfos[model] ?: return
        if (_modelStates[model] is Downloading) return

        val job = viewModelScope.launch(Dispatchers.Default) {
            try {
                _modelStates[model] = OnDeviceModelState.Downloading(
                    bytesDownloaded = 0,
                    totalBytes = null,
                    speed = null,
                    etaMs = null,
                )

                val startTime = Clock.System.now()

                var downloaded = false
                for (source in sourceOrder) {
                    try {
                        info.downloader.download(source, basePath) { total, length ->
                            val now = Clock.System.now()
                            val elapsed = (now - startTime).inWholeMilliseconds
                            val speed = if (elapsed > 0) total * 1000.0 / elapsed else null
                            val etaMs =
                                if (speed != null && speed > 0 && length != null && length > total) {
                                    ((length - total) / speed * 1000).toLong()
                                } else null

                            _modelStates[model] = OnDeviceModelState.Downloading(
                                bytesDownloaded = total,
                                totalBytes = length,
                                speed = speed,
                                etaMs = etaMs,
                            )
                        }
                        downloaded = true
                        break
                    } catch (_: Downloader.UnsupportedSourceException) {
                        continue
                    }
                }

                if (!downloaded) {
                    _modelStates[model] = OnDeviceModelState.Error("No supported download source")
                    return@launch
                }

                val result = validateWithSources(info.downloader, SizeAndHash)
                _modelStates[model] = when (result) {
                    is NotDownloaded -> OnDeviceModelState.Error(
                        "No supported validation source",
                        hasFile = true,
                    )

                    else -> result
                }
            } catch (e: Throwable) {
                _modelStates[model] = OnDeviceModelState.Error(e.message ?: "Unknown error")
            }
        }

        downloadJobs[model]?.cancel()
        downloadJobs[model] = job
        job.invokeOnCompletion { downloadJobs.remove(model) }
    }

    fun cancelDownload(model: LLModel) {
        downloadJobs[model]?.cancel()
        downloadJobs.remove(model)
        val info = modelInfos[model] ?: run {
            _modelStates[model] = NotDownloaded
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            _modelStates[model] = try {
                validateWithSources(info.downloader, SizeAndHash)
            } catch (_: Throwable) {
                NotDownloaded
            }
        }
    }

    fun deleteModel(model: LLModel) {
        viewModelScope.launch(Dispatchers.Default) {
            val modelPath = basePath / ExecuTorchClientBasePath / model.id
            try {
                FileSystem.SYSTEM.delete(modelPath)
            } catch (_: Throwable) {
            }
            _modelStates[model] = NotDownloaded
        }
        viewModelScope.launch {
            llmConfigDao.getModelsByProviderOnce(providerConfigId)
                .firstOrNull { it.model == model }
                ?.let { llmConfigDao.deleteModel(it) }
        }
    }

    fun validateModel(model: LLModel) {
        val info = modelInfos[model] ?: return
        if (_modelStates[model] is Validating) return

        viewModelScope.launch(Dispatchers.Default) {
            _modelStates[model] = Validating
            try {
                val result = validateWithSources(info.downloader, SizeAndHash)
                _modelStates[model] = when (result) {
                    is NotDownloaded -> OnDeviceModelState.Error("No supported validation source")
                    else -> result
                }
            } catch (e: Throwable) {
                _modelStates[model] = OnDeviceModelState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun addModelToDatabase(model: LLModel, name: String) {
        val entity = LLMConfigEntity(
            providerConfigId = providerConfigId,
            model = model,
            name = name,
        )
        viewModelScope.launch {
            llmConfigDao.insertAtEndOrUpdateModel(entity)
        }
    }

    fun isModelInDatabase(model: LLModel): Flow<Boolean> {
        return llmConfigDao.getModelsByProviderFlow(providerConfigId).map { models ->
            models.any { it.model == model }
        }
    }
}

@Immutable
sealed interface OnDeviceModelState {
    val hasFile: Boolean

    data object NotDownloaded : OnDeviceModelState {
        override val hasFile = false
    }

    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long?,
        val speed: Double?,
        val etaMs: Long?,
    ) : OnDeviceModelState {
        override val hasFile = false
    }

    data object Validating : OnDeviceModelState {
        override val hasFile = true
    }

    data class Downloaded(val verification: Downloader.ValidationLevel) : OnDeviceModelState {
        override val hasFile = true
    }

    data class Invalid(val reason: String) : OnDeviceModelState {
        override val hasFile = true
    }

    data class Error(val message: String, override val hasFile: Boolean = false) :
        OnDeviceModelState

    companion object {
        fun fromValidationResult(
            result: Downloader.ValidationResult,
            level: Downloader.ValidationLevel = Offline,
        ): OnDeviceModelState = when (result) {
            is Downloader.ValidationResult.Existing.Valid -> Downloaded(level)
            is Downloader.ValidationResult.Existing.NotValidated -> Downloaded(Offline)
            is Downloader.ValidationResult.Existing.Invalid -> Invalid(result.reason)
            is Downloader.ValidationResult.NotExisting -> NotDownloaded
            is Downloader.ValidationResult.NoValidation -> NotDownloaded
        }
    }
}
