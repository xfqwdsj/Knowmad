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

import androidx.compose.ui.util.fastForEach
import io.ktor.client.content.ProgressListener
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import okio.Path

private typealias DownloadFunction = suspend Downloader.Data.Download.() -> Unit
private typealias ValidationFunction = suspend Downloader.Data.Validation.() -> Downloader.ValidationResult

class Downloader(
    private val functions: Map<DownloadSource, PersistentList<DownloadFunction>>,
    validations: Map<DownloadSource, PersistentList<ValidationFunction>> = emptyMap(),
) {
    private val validations = validations.filterKeys { it in functions }

    val supportedSources get() = functions.keys

    suspend fun download(
        source: DownloadSource,
        basePath: Path,
        progressListener: ProgressListener? = null,
    ) {
        val functions = functions[source]
            ?: throw IllegalArgumentException("Unsupported download source: $source")

        val data = Data.Download(basePath, progressListener)
        functions.fastForEach { it(data) }
    }

    suspend fun validate(
        source: DownloadSource,
        basePath: Path,
        enforce: Boolean = false,
    ): ValidationResult {
        val validationFunctions = validations[source]
        if (validationFunctions.isNullOrEmpty()) return NoValidation

        val data = Data.Validation(basePath, enforce)

        var currentExistingResult: ValidationResult.Existing? = null

        for (validation in validationFunctions) {
            when (val result = validation(data)) {
                is ValidationResult.Existing.Invalid -> return result
                is NotExisting -> return result

                is Existing -> {
                    if (currentExistingResult == null) {
                        currentExistingResult = result
                        continue
                    }
                    if (currentExistingResult is Valid && result is NotValidated) {
                        currentExistingResult = result
                    }
                }

                is NoValidation -> continue
            }
        }

        return currentExistingResult ?: NoValidation
    }

    infix fun then(other: Downloader): Downloader {
        val allSources = this.supportedSources + other.supportedSources

        val combinedFunctions = allSources.associateWith { source ->
            (functions[source] ?: persistentListOf()) +
                    (other.functions[source] ?: persistentListOf())
        }

        val combinedValidations = allSources.associateWith { source ->
            (validations[source] ?: persistentListOf()) +
                    (other.validations[source] ?: persistentListOf())
        }

        return Downloader(combinedFunctions, combinedValidations)
    }

    sealed interface Data {
        val basePath: Path

        data class Download(
            override val basePath: Path,
            val progressListener: ProgressListener? = null,
        ) : Data

        data class Validation(
            override val basePath: Path,
            val enforce: Boolean = false,
        ) : Data
    }

    class Builder {
        private val functions = mutableMapOf<DownloadSource, PersistentList<DownloadFunction>>()
        private val validations = mutableMapOf<DownloadSource, PersistentList<ValidationFunction>>()

        operator fun DownloadSource.invoke(downloadFunc: DownloadFunction): Chain {
            functions[this] = persistentListOf(downloadFunc)
            return Chain(this)
        }

        fun build() = Downloader(functions, validations)

        inner class Chain(private val source: DownloadSource) {
            infix fun validateWith(validationFunc: ValidationFunction): Chain {
                validations[source] = persistentListOf(validationFunc)
                return this
            }
        }
    }

    sealed interface ValidationResult {
        sealed interface Existing : ValidationResult {
            data object NotValidated : Existing
            data object Valid : Existing
            data class Invalid(val reason: String) : Existing
        }

        data class NotExisting(val reason: String) : ValidationResult

        data object NoValidation : ValidationResult
    }

    companion object {
        operator fun invoke(builderAction: Builder.() -> Unit) =
            Builder().apply(builderAction).build()
    }
}
