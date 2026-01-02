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

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.animateBounds
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import top.ltfan.knowmad.ui.util.LocalSharedTransitionScope
import top.ltfan.knowmad.ui.viewmodel.LocalAppViewModel

@Composable
fun SnackbarHost(
    animatedVisibilityScope: AnimatedVisibilityScope = LocalNavAnimatedContentScope.current,
) {
    val viewModel = LocalAppViewModel.current

    with(LocalSharedTransitionScope.current) {
        SnackbarHost(
            viewModel.snackbarHostState,
            modifier = Modifier.animateBounds(this),
        ) { data ->
            Snackbar(
                snackbarData = data,
                modifier = Modifier.sharedBounds(
                    rememberSharedContentState(SnackbarSharedKey),
                    animatedVisibilityScope,
                ),
            )
        }
    }
}

@Immutable
data object SnackbarSharedKey
