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

package top.ltfan.knowmad.ui.util

import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Immutable
import top.ltfan.knowmad.util.Resource

@Immutable
data class SnackbarEvent(
    val message: Resource.String,
    val action: SnackbarAction? = null,
    val withDismissAction: Boolean = false,
    val duration: SnackbarDuration = if (action == null) SnackbarDuration.Short else SnackbarDuration.Indefinite,
    val onDismissed: (() -> Unit)? = null,
)

@Immutable
data class SnackbarAction(
    val label: Resource.String,
    val onClick: (dismiss: () -> Unit) -> Unit,
)
