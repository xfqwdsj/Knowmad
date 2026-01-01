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

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.ui.component.AgentScreen
import top.ltfan.knowmad.ui.component.LocalAgentScreenTransparentBackground
import top.ltfan.knowmad.ui.component.SnackbarEffect
import top.ltfan.knowmad.ui.util.AppWindowInsets
import top.ltfan.knowmad.ui.util.localSharedTransitionScope
import top.ltfan.knowmad.ui.util.only
import top.ltfan.knowmad.ui.viewmodel.LocalAppViewModel

@Serializable
class MainPage : Page() {
    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        val viewModel = LocalAppViewModel.current

        val scaffoldState = rememberBottomSheetScaffoldState(
            bottomSheetState = rememberStandardBottomSheetState(remember { viewModel.mainSheetExpectedValue.value }),
        )

        BottomSheetScaffold(
            sheetContent = {
                Box(
                    Modifier.consumeWindowInsets(AppWindowInsets.only { top }),
                ) {
                    CompositionLocalProvider(
                        LocalAgentScreenTransparentBackground provides true,
                    ) {
                        AgentScreen(LocalNavAnimatedContentScope.current)
                    }
                }
            },
            modifier = localSharedTransitionScope {
                Modifier.sharedBounds(
                    rememberSharedContentState(WizardSharedTransitionKey),
                    LocalNavAnimatedContentScope.current,
                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Crop),
                )
            },
            scaffoldState = scaffoldState,
            sheetPeekHeight = 128.dp,
            snackbarHost = { SnackbarHost(it) },
        ) {

        }

        SnackbarEffect(scaffoldState.snackbarHostState)

        LaunchedEffect(Unit) {
            viewModel.mainSheetExpectedValue.collectLatest { value ->
                when (value) {
                    SheetValue.Expanded -> scaffoldState.bottomSheetState.expand()
                    SheetValue.PartiallyExpanded -> scaffoldState.bottomSheetState.partialExpand()
                    else -> {}
                }
            }
        }
    }

}
