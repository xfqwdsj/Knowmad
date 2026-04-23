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

package top.ltfan.knowmad.modelscope

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.BodyProgress
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.http.URLBuilder
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json
import top.ltfan.knowmad.util.Json
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class ModelScopeApi private constructor() : AutoCloseable {
    private val lazyClient = lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                    },
                )
            }
        }
    }
    private val client inline get() = lazyClient.value

    private val lazyDownloadClient = lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = MAX_VALUE
                connectTimeoutMillis = MAX_VALUE
                socketTimeoutMillis = MAX_VALUE
            }
            install(BodyProgress)
        }
    }
    private val downloadClient inline get() = lazyDownloadClient.value

    fun URLBuilder.listFilesUrl(
        repoId: String,
        revision: String? = null,
        recursive: Boolean = false,
        root: String? = null,
    ) {
        protocol = HTTPS
        host = "modelscope.cn"
        path("/api/v1/models", repoId, "repo/files")
        if (revision != null) {
            parameters.append("Revision", revision)
        }
        if (recursive) {
            parameters.append("Recursive", "true")
        }
        if (root != null) {
            parameters.append("Root", root)
        }
    }

    suspend fun listFiles(
        repoId: String,
        revision: String? = null,
        recursive: Boolean = false,
        root: String? = null,
        buildRequest: (HttpRequestBuilder.() -> Unit)? = null,
    ) = client.get {
        url.listFilesUrl(repoId, revision, recursive, root)
        buildRequest?.invoke(this)
    }

    fun URLBuilder.getFileUrl(
        repoId: String,
        filePath: String,
        revision: String? = null,
    ) {
        protocol = HTTPS
        host = "modelscope.cn"
        path("/api/v1/models", repoId, "repo")
        parameters.append("FilePath", filePath)
        if (revision != null) {
            parameters.append("Revision", revision)
        }
    }

    suspend fun prepareGetFile(
        repoId: String,
        filePath: String,
        revision: String? = null,
        buildRequest: (HttpRequestBuilder.() -> Unit)? = null,
    ) = downloadClient.prepareGet {
        url.getFileUrl(repoId, filePath, revision)
        buildRequest?.invoke(this)
    }

    override fun close() {
        if (lazyClient.isInitialized()) {
            client.close()
        }
        if (lazyDownloadClient.isInitialized()) {
            downloadClient.close()
        }
    }

    companion object {
        @OptIn(ExperimentalContracts::class)
        suspend operator fun <R> invoke(block: suspend ModelScopeApi.() -> R): R {
            contract {
                callsInPlace(block, EXACTLY_ONCE)
            }
            return ModelScopeApi().use {
                it.block()
            }
        }
    }
}
