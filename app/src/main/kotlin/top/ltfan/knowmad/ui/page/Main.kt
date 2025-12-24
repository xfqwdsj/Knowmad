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

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.ui.component.SnackbarEffect
import top.ltfan.knowmad.ui.util.localSharedTransitionScope

@Serializable
class MainPage : Page() {
    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        val scaffoldState = rememberBottomSheetScaffoldState()

        BottomSheetScaffold(
            sheetContent = {

            },
            modifier = localSharedTransitionScope {
                Modifier.sharedBounds(
                    rememberSharedContentState(WizardSharedTransitionKey),
                    LocalNavAnimatedContentScope.current,
                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Crop),
                )
            },
            scaffoldState = scaffoldState,
            snackbarHost = { SnackbarHost(it) },
        ) {

        }

        SnackbarEffect(scaffoldState.snackbarHostState)
    }
}
