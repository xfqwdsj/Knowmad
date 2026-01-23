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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.ui.component.AgentChatIcon
import top.ltfan.knowmad.ui.component.AgentScreen
import top.ltfan.knowmad.ui.component.LocalAgentScreenPreferredContainerColor
import top.ltfan.knowmad.ui.component.LocalAgentScreenTransparentContainer
import top.ltfan.knowmad.ui.component.SnackbarHost
import top.ltfan.knowmad.ui.util.AppWindowInsets
import top.ltfan.knowmad.ui.util.localSharedTransitionScope
import top.ltfan.knowmad.ui.util.only
import top.ltfan.knowmad.ui.util.plus
import top.ltfan.knowmad.ui.viewmodel.LocalAppViewModel

@Serializable
class MainPage : Page() {
    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        val viewModel = LocalAppViewModel.current

        val coroutineScope = rememberCoroutineScope()

        val scaffoldState = rememberBottomSheetScaffoldState(
            bottomSheetState = rememberStandardBottomSheetState(
                initialValue = Hidden,
                skipHiddenState = false,
            ),
            snackbarHostState = viewModel.snackbarHostState,
        )

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screenWidth = constraints.maxWidth

            BottomSheetScaffold(
                sheetContent = {
                    BoxWithConstraints(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 600.dp)
                            .consumeWindowInsets(AppWindowInsets.only { top }),
                    ) {
                        val density = LocalDensity.current
                        val layoutDirection = LocalLayoutDirection.current

                        val sheetWidth = constraints.maxWidth

                        val gap = (screenWidth - sheetWidth) / 2

                        val leftInsets = AppWindowInsets.getLeft(density, layoutDirection)
                        val rightInsets = AppWindowInsets.getRight(density, layoutDirection)

                        val leftConsumed =
                            with(density) { (gap).coerceAtMost(leftInsets).toDp() }
                        val rightConsumed =
                            with(density) { (gap).coerceAtMost(rightInsets).toDp() }

                        val consumedPadding = PaddingValues(
                            start = if (layoutDirection == LayoutDirection.Ltr) leftConsumed else rightConsumed,
                            end = if (layoutDirection == LayoutDirection.Ltr) rightConsumed else leftConsumed,
                        )

                        CompositionLocalProvider(
                            LocalAgentScreenTransparentContainer provides true,
                            LocalAgentScreenPreferredContainerColor provides BottomSheetDefaults.ContainerColor,
                        ) {
                            Box(Modifier.consumeWindowInsets(consumedPadding)) {
                                AgentScreen(LocalNavAnimatedContentScope.current)
                            }
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
                sheetPeekHeight = 0.dp,
                snackbarHost = { SnackbarHost() },
            ) {
                Scaffold(
                    floatingActionButton = {
                        MediumFloatingActionButton(
                            onClick = {
                                coroutineScope.launch { scaffoldState.bottomSheetState.expand() }
                            },
                        ) {
                            AgentChatIcon()
                        }
                    },
                    contentWindowInsets = AppWindowInsets,
                ) {
                    val contentPadding = it + contentPadding

                }
            }
        }
    }

}
