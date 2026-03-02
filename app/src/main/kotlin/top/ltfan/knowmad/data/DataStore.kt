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

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import top.ltfan.knowmad.ui.viewmodel.AndroidViewModel
import top.ltfan.knowmad.util.Cbor
import top.ltfan.knowmad.util.Logger
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.time.Clock
import kotlin.time.Instant

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

    context(lifecycle: LifecycleOwner)
    fun dataStateFlow(
        started: SharingStarted = SharingStarted.Eagerly,
        initialValue: T = defaultValue,
    ) = dataStateFlow(
        coroutineScope = lifecycle.lifecycleScope,
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
    private val initialValue: T = dataStore.defaultValue,
) : ReadWriteProperty<Any?, T>, AutoCloseable {
    private val logger = Logger("DataStoreMutableStateProperty")

    private val job = SupervisorJob(coroutineScope.coroutineContext.job)
    private val coroutineScope = coroutineScope + job

    private val stateMutex = Mutex()
    private var state by mutableStateOf<T?>(null)

    private var lastUpdate = Instant.DISTANT_PAST

    private val writesBeforeFirstValue = Channel<(T) -> T>(Channel.UNLIMITED)

    private val writeRequest = Channel<T>(Channel.CONFLATED)

    private suspend inline fun write(value: T) {
        dataStore.updateData { value }
    }

    init {
        coroutineScope.launch {
            state = dataStore.data.first()

            stateMutex.withLock {
                writesBeforeFirstValue.close()

                for (pendingWrite in writesBeforeFirstValue) {
                    val newValue = pendingWrite(state ?: error("State should not be null here"))
                    state = newValue
                }
            }

            write(state ?: error("State should not be null here"))
            lastUpdate = Clock.System.now()

            launch {
                dataStore.data.collect { diskValue ->
                    stateMutex.withLock {
                        if (!writeRequest.isEmpty) return@collect
                        if (Clock.System.now() < lastUpdate) return@collect

                        state = diskValue
                    }
                }
            }

            for (pendingWrite in writeRequest) {
                var finalWrite = pendingWrite
                while (true) {
                    finalWrite = writeRequest.tryReceive().getOrNull() ?: break
                }

                try {
                    write(finalWrite)
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    logger.error(e) { "Failed to write data store value" }
                }
            }
        }
    }

    private suspend inline fun setValueInternal(value: T) {
        stateMutex.withLock {
            state = value
            lastUpdate = Clock.System.now()
            writeRequest.send(value)
        }
    }

    var value
        get() = state ?: initialValue
        set(value) {
            coroutineScope.launch(Dispatchers.Main.immediate) {
                runCatching { writesBeforeFirstValue.send { value } }
                    .onFailure { setValueInternal(value) }
            }
        }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }

    fun <R> transformedGet(transform: T.() -> R): R {
        return value.transform()
    }

    fun <R> transformedSet(newValue: R, transform: T.(R) -> T,) {
        coroutineScope.launch(Dispatchers.Main.immediate) {
            runCatching { writesBeforeFirstValue.send { it.transform(newValue) } }
                .onFailure { setValueInternal(value.transform(newValue)) }
        }
    }

    override fun close() {
        coroutineScope.cancel()
    }
}

abstract class DataStoreCompanion<T> {
    abstract val fileName: String
    abstract val default: T
    abstract fun serializer(): KSerializer<T>

    private val lock = Any()

    private var _instance: AppDataStore<T>? = null

    context(context: Context)
    fun createDataStore(
        coroutineScope: CoroutineScope,
    ) = synchronized(lock) {
        _instance ?: AppDataStore(
            serializer = serializer(),
            defaultValue = default,
            scope = coroutineScope + Dispatchers.IO,
            produceFile = { context.applicationContext.dataStoreFile(fileName) },
        ).also {
            _instance = it
        }
    }

    @JvmName("_createDataStore")
    context(context: Context, coroutineScope: CoroutineScope)
    fun createDataStore() = context(context) { createDataStore(coroutineScope) }

    context(viewModel: AndroidViewModel<*>)
    fun createDataStore() = context(viewModel.application) {
        createDataStore(viewModel.viewModelScope)
    }

    context(lifecycle: LifecycleOwner, context: Context)
    fun createDataStore() = createDataStore(lifecycle.lifecycleScope)

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
    private val logger = Logger("DatastoreSerializer")

    private val cbor = Cbor {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    override suspend fun readFrom(input: InputStream): T {
        return try {
            val bytes = input.readBytes()
            if (bytes.isEmpty()) return defaultValue
            cbor.decodeFromByteArray(serializer, bytes)
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to read data store value, using default" }
            defaultValue
        }
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        withContext(Dispatchers.IO) {
            output.write(cbor.encodeToByteArray(serializer, t))
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T, R, P : DataStoreMutableStateProperty<T>> P.transform(
    noinline transformIn: T.() -> R,
    noinline transformOut: T.(R) -> T,
): ReadWriteProperty<Any?, R> = object : ReadWriteProperty<Any?, R> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): R {
        return this@transform.transformedGet(transformIn)
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: R) {
        this@transform.transformedSet(value, transformOut)
    }
}
