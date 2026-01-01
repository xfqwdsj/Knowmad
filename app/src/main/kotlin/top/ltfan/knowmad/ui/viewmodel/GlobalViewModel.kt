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

package top.ltfan.knowmad.ui.viewmodel

import androidx.compose.material3.SnackbarDuration
import kotlinx.coroutines.flow.MutableSharedFlow
import top.ltfan.knowmad.ui.util.SnackbarAction
import top.ltfan.knowmad.ui.util.SnackbarEvent
import top.ltfan.knowmad.util.Resource

object GlobalViewModel {
    val snackbarEvent = MutableSharedFlow<SnackbarEvent>(replay = 1)

    suspend fun showSnackbar(
        message: Resource.String,
        action: SnackbarAction? = null,
        withDismissAction: Boolean = false,
        duration: SnackbarDuration = if (action == null) SnackbarDuration.Short else SnackbarDuration.Indefinite,
        onDismissed: (() -> Unit)? = null,
    ) = snackbarEvent.emit(
        SnackbarEvent(
            message,
            action,
            withDismissAction,
            duration,
            onDismissed,
        ),
    )
}
