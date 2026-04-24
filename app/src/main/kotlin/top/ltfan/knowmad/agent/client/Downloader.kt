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
    private val functions: Map<DownloadSource, PersistentList<Pair<FunctionRole, DownloadFunction>>>,
    validations: Map<DownloadSource, PersistentList<Pair<FunctionRole, ValidationFunction>>> = emptyMap(),
) {
    private val validations = validations.filterKeys { it in functions }

    val supportedSources get() = functions.keys

    suspend fun download(
        source: DownloadSource,
        basePath: Path,
        skip: Set<FunctionRole>? = null,
        progressListener: ProgressListener? = null,
    ) {
        val functions = functions[source] ?: throw UnsupportedSourceException(source)

        val data = Data.Download(basePath, progressListener)
        functions.fastForEach { (role, download) ->
            if (skip == null || role !in skip) {
                download(data)
            }
        }
    }

    suspend fun validate(
        source: DownloadSource,
        basePath: Path,
        level: ValidationLevel = Offline,
        skip: Set<FunctionRole>? = null,
    ): ValidationResult {
        val validationFunctions = validations[source]
        if (validationFunctions.isNullOrEmpty()) return NoValidation

        val data = Data.Validation(basePath, level)

        var currentExistingResult: ValidationResult.Existing? = null

        for ((role, validate) in validationFunctions) {
            if (skip != null && role in skip) continue
            when (val result = validate(data)) {
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
            val level: ValidationLevel = Offline,
        ) : Data
    }

    class Builder {
        private val functions =
            mutableMapOf<DownloadSource, PersistentList<Pair<FunctionRole, DownloadFunction>>>()
        private val validations =
            mutableMapOf<DownloadSource, PersistentList<Pair<FunctionRole, ValidationFunction>>>()

        operator fun DownloadSource.invoke(
            role: FunctionRole,
            downloadFunc: DownloadFunction,
        ): Chain {
            functions[this] = persistentListOf(role to downloadFunc)
            return Chain(this, role)
        }

        fun build() = Downloader(functions, validations)

        inner class Chain(
            private val source: DownloadSource,
            private val role: FunctionRole,
        ) {
            infix fun validateWith(validationFunc: ValidationFunction): Chain {
                validations[source] = persistentListOf(role to validationFunc)
                return this
            }
        }
    }

    enum class ValidationLevel {
        Offline,
        SizeOnly,
        SizeAndHash,
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

    enum class FunctionRole {
        Tokenizer,
        Model,
    }

    class UnsupportedSourceException(source: DownloadSource) :
        IllegalArgumentException("Unsupported download source: $source")

    companion object {
        operator fun invoke(builderAction: Builder.() -> Unit) =
            Builder().apply(builderAction).build()
    }
}
