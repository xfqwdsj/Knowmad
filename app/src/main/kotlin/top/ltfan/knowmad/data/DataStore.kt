/*
 * Knowmad - Knowledge nomad
 * Copyright (C) 2025-2026 LTFan (aka xfqwdsj)
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

package top.ltfan.knowmad.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import top.ltfan.knowmad.ui.viewmodel.AndroidViewModel
import top.ltfan.knowmad.util.Cbor
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.time.Duration.Companion.seconds

class AppDataStore<T>(
    serializer: KSerializer<T>,
    val defaultValue: T,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    produceFile: () -> File,
) : DataStore<T> by DataStoreFactory.create(
    serializer = DatastoreSerializer(
        defaultValue = defaultValue,
        serializer = serializer,
    ),
    scope = scope,
    produceFile = produceFile,
) {
    fun asMutableState(
        coroutineScope: CoroutineScope,
        started: SharingStarted = SharingStarted.Eagerly,
    ) = object : ReadWriteProperty<Any?, T> {
        private val dataState = data.stateIn(
            scope = coroutineScope,
            started = started,
            initialValue = defaultValue,
        )

        private var state by mutableStateOf(dataState.value)

        private val writeRequest = MutableSharedFlow<T>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

        private suspend fun write(value: T) {
            withContext(Dispatchers.IO) {
                updateData { value }
            }
        }

        init {
            coroutineScope.launch {
                writeRequest
                    .debounce(1.seconds)
                    .collect { value ->
                        write(value)
                    }
            }

            coroutineScope.launch {
                dataState.collect { value ->
                    state = value
                }
            }
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return state
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state = value
            writeRequest.tryEmit(value)
        }
    }

    context(viewModel: ViewModel)
    fun asMutableState(
        started: SharingStarted = SharingStarted.Eagerly,
    ) = asMutableState(
        coroutineScope = viewModel.viewModelScope,
        started = started,
    )

    @Composable
    fun asMutableState(
        started: SharingStarted = SharingStarted.Eagerly,
    ): ReadWriteProperty<Any?, T> {
        val coroutineScope = rememberCoroutineScope()
        return remember(started, coroutineScope) {
            asMutableState(
                coroutineScope = coroutineScope,
                started = started,
            )
        }
    }
}

interface DataStoreCompanion<T> {
    val fileName: String
    val default: T
    fun serializer(): KSerializer<T>

    context(viewModel: AndroidViewModel<*>)
    fun createDataStore() = AppDataStore(
        serializer = serializer(),
        defaultValue = default,
        scope = viewModel.viewModelScope + Dispatchers.IO,
        produceFile = { viewModel.application.dataStoreFile(fileName) },
    )

    context(viewModel: AndroidViewModel<*>)
    fun deleteFile() {
        val file = viewModel.application.dataStoreFile(fileName)
        if (file.exists()) {
            file.delete()
        }
    }
}

class DatastoreSerializer<T>(
    override val defaultValue: T,
    val serializer: KSerializer<T>,
) : Serializer<T> {
    private val cbor = Cbor {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    override suspend fun readFrom(input: InputStream): T {
        return try {
            val bytes = input.readBytes()
            if (bytes.isEmpty()) return defaultValue
            cbor.decodeFromByteArray(serializer, bytes)
        } catch (e: SerializationException) {
            e.printStackTrace()
            defaultValue
        }
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        withContext(Dispatchers.IO) {
            output.write(cbor.encodeToByteArray(serializer, t))
        }
    }
}
