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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

abstract class HandlerEvent<R, E : HandlerEvent<R, E>> {
    private val mutex = Mutex()

    @Volatile
    private var _onResult: ((R?) -> Unit)? = null

    suspend fun onResult(onResult: (R?) -> Unit): E {
        return mutex.withLock {
            _onResult = onResult
            @Suppress("UNCHECKED_CAST")
            this as E
        }
    }

    fun trySetOnResult(onResult: (R?) -> Unit): E? {
        return mutex.tryWithLock {
            _onResult = onResult
            @Suppress("UNCHECKED_CAST")
            this as E
        }
    }

    operator fun invoke(result: R?) {
        mutex.tryWithLock {
            _onResult?.invoke(result)
            _onResult = null
        }
    }
}
