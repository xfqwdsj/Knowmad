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

package top.ltfan.knowmad.util

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.Instant

context(lifecycleOwner: LifecycleOwner)
inline fun <reified T : Service> Context.ServiceConnection(
    bind: Boolean = true,
) = ServiceConnection(
    context = applicationContext,
    klass = T::class,
).also {
    lifecycleOwner.lifecycle.addObserver(it)
    if (bind) {
        it.bind()
    }
}

inline fun <reified T : Service> Context.ServiceConnection(
    bind: Boolean = true,
) = ServiceConnection(
    context = applicationContext,
    klass = T::class,
).also {
    if (bind) {
        it.bind()
    }
}

abstract class ServiceInstanceBinder<T : Service> : Binder() {
    abstract val service: T
}

class ServiceConnection<T : Service>(
    private val context: Context,
    private val klass: KClass<T>,
) : AutoCloseable, DefaultLifecycleObserver {
    private val _status =
        MutableStateFlow<ServiceConnectionStatus<T>>(ServiceConnectionStatus.Connecting())
    val status = _status.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null) {
                _status.value = ServiceConnectionStatus.WrongBinding(name, binder)
                return
            }
            if (binder is ServiceInstanceBinder<*>) {
                val service = binder.service
                if (klass.isInstance(service)) {
                    @Suppress("UNCHECKED_CAST")
                    val typedService = service as T
                    _status.value = ServiceConnectionStatus.Connected(typedService)
                } else {
                    _status.value = ServiceConnectionStatus.WrongBinding(name, binder)
                }
            } else {
                _status.value = ServiceConnectionStatus.WrongBinding(name, binder)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _status.value = ServiceConnectionStatus.Disconnected(name)
        }

        override fun onBindingDied(name: ComponentName?) {
            unbind()
            _status.value = ServiceConnectionStatus.Died(name)
        }

        override fun onNullBinding(name: ComponentName?) {
            unbind()
            _status.value = ServiceConnectionStatus.NullBinding(name)
        }
    }

    fun bind(): Boolean {
        val intent = Intent(context, klass.java)
        return context.applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind(): Result<Unit> {
        return runCatching { context.applicationContext.unbindService(connection) }.onSuccess {
            _status.value = ServiceConnectionStatus.Unbounded()
        }
    }

    override fun close() {
        unbind()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        close()
    }
}

sealed interface ServiceConnectionStatus<T : Service> {
    data class Connecting<T : Service>(val at: Instant = Clock.System.now()) :
        ServiceConnectionStatus<T>

    data class Connected<T : Service>(val service: T) : ServiceConnectionStatus<T>

    sealed interface BindingError<T : Service> : ServiceConnectionStatus<T>

    data class WrongBinding<T : Service>(val name: ComponentName?, val service: IBinder?) :
        BindingError<T>

    data class NullBinding<T : Service>(val name: ComponentName?) : BindingError<T>

    data class Disconnected<T : Service>(val name: ComponentName?) : ServiceConnectionStatus<T>

    sealed interface Closed<T : Service> : ServiceConnectionStatus<T>

    data class Died<T : Service>(val name: ComponentName?) : Closed<T>
    class Unbounded<T : Service> : Closed<T>
}
