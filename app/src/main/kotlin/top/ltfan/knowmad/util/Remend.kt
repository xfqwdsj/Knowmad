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

import android.content.res.AssetManager
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

@Immutable
class RemendProcessor(
    val assets: AssetManager,
) : QuickJsHolder() {
    private val initialized = MutableStateFlow(false)

    suspend fun initialize() {
        val code = assets.open("remend/index.js").bufferedReader().use { it.readText() }
        quickJs.addModule(name = "remend", code)
        quickJs.evaluate<Unit>(
            "import remend from 'remend'; globalThis.remend = remend; void 0;",
            asModule = true,
        )
        initialized.value = true
    }

    suspend fun process(input: String): String {
        initialized.first { it }
        return quickJs.evaluate(
            "remend(${Json.encodeToString(input)})",
        )
    }
}
