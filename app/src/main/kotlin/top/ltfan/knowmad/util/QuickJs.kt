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

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.define
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

abstract class QuickJsHolder(
    jobDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AutoCloseable {
    protected open val logger = Logger("QuickJs")

    protected val quickJs = QuickJs.create(jobDispatcher)

    protected open fun prepare() {
        quickJs.define("console") {
            function("log") { args ->
                logger.debug { args.joinToString(" ") }
                null
            }
        }
    }

    init {
        prepare()
    }

    override fun close() {
        quickJs.close()
    }
}
