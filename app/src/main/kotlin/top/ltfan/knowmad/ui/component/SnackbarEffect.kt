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

package top.ltfan.knowmad.ui.component

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import top.ltfan.knowmad.ui.viewmodel.GlobalViewModel

@Composable
fun SnackbarEffect(snackbarHostState: SnackbarHostState) {
    val resources = LocalResources.current
    LaunchedEffect(resources) {
        GlobalViewModel.snackbarEvent.collect { event ->
            val message = event.message.get(resources)
            val actionLabel = event.action?.label?.get(resources)
            snackbarHostState.showSnackbar(
                message,
                actionLabel,
                event.withDismissAction,
                event.duration,
            ).let {
                when (it) {
                    SnackbarResult.ActionPerformed -> {
                        event.action?.onClick?.invoke()
                    }

                    SnackbarResult.Dismissed -> {
                        event.onDismissed?.invoke()
                    }
                }
            }
        }
    }
}
