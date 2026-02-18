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

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
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
import kotlin.coroutines.cancellation.CancellationException
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class AppDataStore<T>(
    serializer: KSerializer<T>,
    val defaultValue: T,
    scope: CoroutineScope,
    produceFile: () -> File,
) : DataStore<T> by DataStoreFactory.create(
    serializer = DatastoreSerializer(
        defaultValue = defaultValue,
        serializer = serializer,
    ),
    scope = scope,
    produceFile = produceFile,
) {
    fun dataStateFlow(
        coroutineScope: CoroutineScope,
        started: SharingStarted = SharingStarted.Eagerly,
        initialValue: T = defaultValue,
    ) = data.stateIn(
        scope = coroutineScope,
        started = started,
        initialValue = initialValue,
    )

    context(viewModel: ViewModel)
    fun dataStateFlow(
        started: SharingStarted = SharingStarted.Eagerly,
        initialValue: T = defaultValue,
    ) = dataStateFlow(
        coroutineScope = viewModel.viewModelScope,
        started = started,
        initialValue = initialValue,
    )

    fun asMutableState(
        initialValue: T = defaultValue,
        coroutineScope: CoroutineScope,
    ) = DataStoreMutableStateProperty(
        dataStore = this,
        coroutineScope = coroutineScope,
        initialValue = initialValue,
    )

    context(viewModel: ViewModel)
    fun asMutableState(initialValue: T = defaultValue) = asMutableState(
        initialValue = initialValue,
        coroutineScope = viewModel.viewModelScope,
    ).also {
        viewModel.addCloseable(it)
    }
}

class DataStoreMutableStateProperty<T>(
    private val dataStore: AppDataStore<T>,
    coroutineScope: CoroutineScope,
    initialValue: T = dataStore.defaultValue,
) : ReadWriteProperty<Any?, T>, AutoCloseable {
    private val job = coroutineScope.coroutineContext.job
    private val coroutineScope =
        CoroutineScope(coroutineScope.coroutineContext + SupervisorJob(job))

    private var state by mutableStateOf(initialValue)

    private val writeRequest = Channel<T>(Channel.CONFLATED)

    private suspend fun write(value: T) {
        dataStore.updateData { value }
    }

    init {
        coroutineScope.launch {
            while (isActive) {
                val syncJob = launch {
                    dataStore.data.collect { diskValue ->
                        if (writeRequest.isEmpty) {
                            state = diskValue
                        }
                    }
                }

                val firstWrite = writeRequest.receive()
                syncJob.cancelAndJoin()

                var pendingWrite = firstWrite
                while (true) {
                    pendingWrite = writeRequest.tryReceive().getOrNull() ?: break
                }

                try {
                    write(pendingWrite)
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    e.printStackTrace()
                }
            }
        }
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return state
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        state = value
        writeRequest.trySend(value)
    }

    override fun close() {
        coroutineScope.cancel()
    }
}

abstract class DataStoreCompanion<T> {
    abstract val fileName: String
    abstract val default: T
    abstract fun serializer(): KSerializer<T>

    private var _instance: AppDataStore<T>? = null

    context(application: Application)
    fun createDataStore(
        coroutineScope: CoroutineScope,
    ) = _instance ?: AppDataStore(
        serializer = serializer(),
        defaultValue = default,
        scope = coroutineScope + Dispatchers.IO,
        produceFile = { application.dataStoreFile(fileName) },
    ).also {
        _instance = it
    }

    context(viewModel: AndroidViewModel<*>)
    fun createDataStore() = context(viewModel.application) {
        createDataStore(viewModel.viewModelScope)
    }

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
