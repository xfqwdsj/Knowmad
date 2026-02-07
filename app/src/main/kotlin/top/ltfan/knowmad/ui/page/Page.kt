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

package top.ltfan.knowmad.ui.page

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
sealed class Route : NavKey

@Serializable
sealed class PageRoute<K : Route> : Route() {
    @Transient
    open val metadata: Map<String, Any> = emptyMap()

    @Composable
    context(contentPadding: PaddingValues)
    abstract fun Content()

    fun navEntry(
        contentPadding: PaddingValues = PaddingValues(),
    ) = @Suppress("UNCHECKED_CAST") NavEntry(
        key = this as K,
        metadata = metadata,
    ) {
        context(contentPadding) {
            Content()
        }
    }
}

/** A page in the navigation hierarchy. */
@Serializable
sealed class Page : PageRoute<Page>()

/**
 * A page managed by a parent route. This page will not appear in the
 * top-level navigation stack.
 */
@Serializable
sealed class SubPage<K : SubPage<K>> : PageRoute<K>()

@Serializable
abstract class BackStackRoute(
    val backStack: NavBackStack<Route>,
) : Route() {
    open fun onBack() {
        val last = backStack.lastOrNull() ?: return
        if (last is BackStackRoute) {
            last.onBack()
        } else {
            backStack.removeLastOrNull()
        }
    }
}

fun NavBackStack<Route>.back() {
    val last = lastOrNull() ?: return
    if (last is BackStackRoute) {
        last.onBack()
    } else {
        removeLastOrNull()
    }
}

val NavBackStack<Route>.expanded: List<Page>
    get() = buildList {
        for (entry in this@expanded) {
            when (entry) {
                is Page -> add(entry)
                is BackStackRoute -> addAll(entry.backStack.expanded)
                is SubPage<*> -> {
                    // Ignore SubPage entries at the top level
                }
            }
        }
    }
