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

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.launch
import kotlinx.datetime.toJavaMonth
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.R
import top.ltfan.knowmad.sync.requestCalendarSync
import top.ltfan.knowmad.ui.component.AgentChatIcon
import top.ltfan.knowmad.ui.component.AgentScreen
import top.ltfan.knowmad.ui.component.Calendar
import top.ltfan.knowmad.ui.component.LocalAgentScreenPreferredContainerColor
import top.ltfan.knowmad.ui.component.LocalAgentScreenTransparentContainer
import top.ltfan.knowmad.ui.component.MonthBottomSheetContent
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

        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val configuration = LocalConfiguration.current
        val coroutineScope = rememberCoroutineScope()

        val calendarPermissionsState = rememberMultiplePermissionsState(
            permissions = listOf(
                android.Manifest.permission.READ_CALENDAR,
                android.Manifest.permission.WRITE_CALENDAR,
            ),
        )
        val scaffoldState = rememberBottomSheetScaffoldState(
            bottomSheetState = rememberStandardBottomSheetState(
                initialValue = Hidden,
                skipHiddenState = false,
            ),
            snackbarHostState = viewModel.snackbarHostState,
        )

        var showCalendarPermissionRationale by remember { mutableStateOf(false) }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screenWidth = constraints.maxWidth

            BottomSheetScaffold(
                sheetContent = {
                    var backProgress by remember { mutableFloatStateOf(0f) }

                    val localEventDispatcher =
                        LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
                            ?: error("No NavigationEventDispatcherOwner provided")

                    val eventDispatcherOwner = remember(localEventDispatcher) {
                        object : NavigationEventDispatcherOwner {
                            override val navigationEventDispatcher =
                                NavigationEventDispatcher(localEventDispatcher)
                        }
                    }

                    BoxWithConstraints(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 600.dp)
                            .consumeWindowInsets(AppWindowInsets.only { top })
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                val offset = (200.dp * backProgress).roundToPx()
                                layout(placeable.width, placeable.height - offset) {
                                    placeable.placeRelative(x = 0, y = 0)
                                }
                            },
                    ) {
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
                            LocalNavigationEventDispatcherOwner provides eventDispatcherOwner,
                        ) {
                            Box(Modifier.consumeWindowInsets(consumedPadding)) {
                                AgentScreen(LocalNavAnimatedContentScope.current)
                            }
                        }
                    }

                    LaunchedEffect(scaffoldState.bottomSheetState.isVisible) {
                        eventDispatcherOwner.navigationEventDispatcher.isEnabled =
                            scaffoldState.bottomSheetState.isVisible
                    }

                    PredictiveBackHandler(
                        enabled = scaffoldState.bottomSheetState.targetValue != Hidden,
                    ) { progress ->
                        try {
                            progress.collect { backEvent ->
                                backProgress = backEvent.progress
                            }
                        } finally {
                            scaffoldState.bottomSheetState.hide()
                            backProgress = 0f
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
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(
                                    modifier = Modifier
                                        .clip(ContinuousCapsule)
                                        .clickable { viewModel.showMonthBottomSheet = true }
                                        .padding(ButtonDefaults.TextButtonWithIconContentPadding),
                                    horizontalArrangement = Arrangement.spacedBy(ButtonDefaults.IconSpacing),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        viewModel.calendarState.currentMonth.month.toJavaMonth()
                                            .getDisplayName(FULL, configuration.locales[0]),
                                    )
                                    Icon(
                                        painterResource(R.drawable.arrow_drop_down_24px),
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize),
                                    )
                                }
                            },
                            modifier = Modifier.combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onDoubleClick = viewModel::calendarBackToToday,
                                onClick = {},
                            ),
                        )
                    },
                    floatingActionButton = {
                        MediumFloatingActionButton(
                            onClick = {
                                coroutineScope.launch { scaffoldState.bottomSheetState.expand() }
                            },
                            content = { AgentChatIcon() },
                        )
                    },
                    contentWindowInsets = AppWindowInsets,
                ) {
                    val contentPadding = it + contentPadding
                    Calendar(
                        modifier = Modifier.padding(contentPadding),
                        headerModifier = Modifier.padding(vertical = 4.dp),
                        state = viewModel.calendarState,
                        onSystemDateChanged = viewModel::onSystemDateChanged,
                        getEvents = viewModel::getEvents,
                        onEventClick = viewModel::onCalendarEventClick,
                    )
                }
            }
        }

        if (calendarPermissionsState.allPermissionsGranted) {
            LaunchedEffect(Unit) {
                viewModel.application.requestCalendarSync()
            }
        } else {
            if (calendarPermissionsState.shouldShowRationale) {
                SideEffect {
                    showCalendarPermissionRationale = true
                }
            } else {
                SideEffect {
                    calendarPermissionsState.launchMultiplePermissionRequest()
                }
            }
        }

        if (viewModel.showMonthBottomSheet) {
            ModalBottomSheet(onDismissRequest = { viewModel.showMonthBottomSheet = false }) {
                MonthBottomSheetContent(
                    month = viewModel.calendarState.currentMonth,
                    semesters = viewModel.allSemesters,
                    notSelectedSemesters = viewModel.invisibleSemesters,
                    onSemesterSelectionChange = viewModel::onSemesterSelectionChange,
                    onExport = viewModel::exportSemester,
                    onBackup = viewModel::backupSemester,
                    onDelete = viewModel::deleteSemester,
                    onImport = viewModel::importFromICalendar,
                )
            }
        }

        if (showCalendarPermissionRationale) {
            AlertDialog(
                onDismissRequest = { showCalendarPermissionRationale = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showCalendarPermissionRationale = false
                            calendarPermissionsState.launchMultiplePermissionRequest()
                        },
                        content = { Text(stringResource(R.string.schedule_sync_rationale_dialog_confirm_label)) },
                    )
                },
                title = { Text(stringResource(R.string.schedule_sync_rationale_dialog_title)) },
                text = { Text(stringResource(R.string.schedule_sync_rationale_dialog_message)) },
            )
        }
    }
}
