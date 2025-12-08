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

package top.ltfan.knowmad.ui.page

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import top.ltfan.knowmad.ui.viewmodel.AppViewModel

@Serializable
sealed class Route : NavKey {
    @Transient
    open var parent: BackStackRoute? = null
}

@Serializable
sealed class Page : Route() {
    @Transient
    open val metadata: Map<String, Any> = emptyMap()

    @Composable
    context(contentPadding: PaddingValues)
    abstract fun AppViewModel.Content()

    context(viewModel: AppViewModel)
    fun navEntry(contentPadding: PaddingValues) = NavEntry(
        key = this,
        metadata = metadata,
    ) {
        context(contentPadding) {
            viewModel.Content()
        }
    }
}

@Serializable
abstract class BackStackRoute(
    val backStack: NavBackStack<Route>,
) : Route()

val NavBackStack<Route>.expended: SnapshotStateList<Page>
    @Composable get() = buildList {
        for (entry in this@expended) {
            when (entry) {
                is Page -> add(entry)
                is BackStackRoute -> addAll(entry.backStack.expended)
            }
        }
    }.toMutableStateList()
