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
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import top.ltfan.knowmad.ui.viewmodel.AppViewModel

@Serializable
sealed class Route : NavKey

sealed interface PageInterface {
    val metadata: Map<String, Any>

    @Composable
    context(contentPadding: PaddingValues)
    fun AppViewModel.Content()
}

/** A page in the navigation hierarchy. */
@Serializable
sealed class Page : Route(), PageInterface {
    @Transient
    override val metadata: Map<String, Any> = emptyMap()

    context(viewModel: AppViewModel)
    fun navEntry(
        contentPadding: PaddingValues = PaddingValues(),
    ) = NavEntry(
        key = this,
        metadata = metadata,
    ) {
        context(contentPadding) {
            viewModel.Content()
        }
    }
}

/**
 * A page managed by a parent route. This page will not appear in the
 * top-level navigation stack.
 */
@Serializable
sealed class SubPage : Route(), PageInterface {
    @Transient
    override val metadata: Map<String, Any> = emptyMap()

    context(viewModel: AppViewModel)
    fun navEntry(
        contentPadding: PaddingValues = PaddingValues(),
    ) = NavEntry(
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

val NavBackStack<Route>.expanded: List<Page>
    get() = buildList {
        for (entry in this@expanded) {
            when (entry) {
                is Page -> add(entry)
                is BackStackRoute -> addAll(entry.backStack.expanded)
                is SubPage -> {
                    // Ignore SubPage entries at the top level
                }
            }
        }
    }
