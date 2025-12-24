/*
 * Knowmad - Knowledge nomad
 * Copyright (C) 2025 LTFan (aka xfqwdsj)
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import top.ltfan.knowmad.ui.viewmodel.AndroidViewModel
import top.ltfan.knowmad.util.Cbor
import top.ltfan.knowmad.util.collectAsState
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

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
    context(viewModel: ViewModel)
    fun stateInViewModel(started: SharingStarted = SharingStarted.Eagerly) = this.data.stateIn(
        scope = viewModel.viewModelScope,
        started = started,
        initialValue = defaultValue,
    )

    context(viewModel: ViewModel)
    fun asMutableState(
        started: SharingStarted = SharingStarted.Eagerly,
    ): ReadWriteProperty<Any?, T> {
        val state = stateInViewModel(started).collectAsState()
        return object : ReadWriteProperty<Any?, T> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): T {
                return state.value
            }

            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                viewModel.viewModelScope.launch(Dispatchers.IO) {
                    updateData { value }
                }
            }
        }
    }

    @Composable
    fun <R> rememberPropertyAsState(
        defaultValue: T = this.defaultValue,
        get: (T) -> R,
        set: (T, R) -> T,
    ): MutableState<R> {
        val coroutineScope = rememberCoroutineScope()
        val delegate = rememberSaveable { mutableStateOf(defaultValue) }

        LaunchedEffect(defaultValue, delegate) {
            data.collect { delegate.value = it }
        }

        return remember(get, set, coroutineScope, delegate) {
            object : MutableState<R> by mutableStateOf(get(delegate.value)) {
                override var value: R
                    get() = get(delegate.value)
                    set(newValue) {
                        coroutineScope.launch {
                            updateData { set(delegate.value, newValue) }
                        }
                    }
            }
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
