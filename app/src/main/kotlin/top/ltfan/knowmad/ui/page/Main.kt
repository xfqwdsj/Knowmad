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

import android.app.PictureInPictureParams
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.work.WorkManager
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.launch
import kotlinx.datetime.toJavaMonth
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.R
import top.ltfan.knowmad.agent.task.suggestion.GenerateNextSuggestionWorker
import top.ltfan.knowmad.data.schedule.syncEvents
import top.ltfan.knowmad.notification.ClassProgressReceiver.Companion.scheduleClassProgressNotificationScheduling
import top.ltfan.knowmad.notification.SuggestionRequestReceiver.Companion.scheduleNextSuggestionGeneration
import top.ltfan.knowmad.ui.component.AgentChatIcon
import top.ltfan.knowmad.ui.component.AgentScreenSharedKey
import top.ltfan.knowmad.ui.component.Calendar
import top.ltfan.knowmad.ui.component.CloseFullscreenIconButton
import top.ltfan.knowmad.ui.component.GenerateSuggestionIconButton
import top.ltfan.knowmad.ui.component.MonthBottomSheetContent
import top.ltfan.knowmad.ui.component.SnackbarHost
import top.ltfan.knowmad.ui.util.AppWindowInsets
import top.ltfan.knowmad.ui.util.BackdropEffectsLight
import top.ltfan.knowmad.ui.util.BackdropInteractiveHighlight
import top.ltfan.knowmad.ui.util.localSharedTransitionScope
import top.ltfan.knowmad.ui.util.plus
import top.ltfan.knowmad.ui.viewmodel.LocalAppViewModel
import top.ltfan.knowmad.util.canScheduleExactAlarms
import top.ltfan.knowmad.util.checkOrRequestExactAlarmsPermission

@Serializable
class MainPage : Page() {
    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        val viewModel = LocalAppViewModel.current

        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val coroutineScope = rememberCoroutineScope()

        val backgroundColor = MaterialTheme.colorScheme.background

        val backdrop = rememberLayerBackdrop {
            drawRect(backgroundColor)
            drawContent()
        }

        Scaffold(
            modifier = localSharedTransitionScope {
                Modifier.sharedBounds(
                    rememberSharedContentState(WizardSharedTransitionKey),
                    LocalNavAnimatedContentScope.current,
                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(Crop),
                )
            },
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
                        onDoubleClick = {
                            coroutineScope.launch {
                                viewModel.calendarState.animateScrollToDate()
                            }
                        },
                        onClick = {},
                    ),
                    actions = {
                        val context = LocalContext.current
                        val prompt =
                            stringResource(R.string.llm_task_generate_next_suggestion_prompt_manual)

                        GenerateSuggestionIconButton(
                            onClick = {
                                val request =
                                    GenerateNextSuggestionWorker.buildRequest(prompt)
                                WorkManager.getInstance(context).enqueue(request)
                            },
                        )

                        val activity = LocalActivity.current
                        if (activity != null) {
                            CloseFullscreenIconButton(
                                onClick = {
                                    activity.enterPictureInPictureMode(
                                        PictureInPictureParams.Builder().build(),
                                    )
                                },
                                contentDescriptionRes = R.string.companion_mode_label_enter,
                            )
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost() },
            floatingActionButton = {
                val coroutineScope = rememberCoroutineScope()

                val interactiveHighlight = remember(coroutineScope) {
                    BackdropInteractiveHighlight(coroutineScope)
                }

                val shape = FloatingActionButtonDefaults.shape
                val color = FloatingActionButtonDefaults.containerColor
                MediumFloatingActionButton(
                    onClick = viewModel::switchStandaloneAgentScreen,
                    modifier = localSharedTransitionScope {
                        Modifier
                            .sharedBounds(
                                rememberSharedContentState(AgentScreenSharedKey),
                                LocalNavAnimatedContentScope.current,
                                resizeMode = RemeasureToBounds,
                            )
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { shape },
                                effects = BackdropEffectsLight,
                                onDrawSurface = {
                                    drawRect(color, blendMode = Hue)
                                    drawRect(color.copy(alpha = 0.4f))
                                },
                            )
                            .then(interactiveHighlight.modifier)
                            .then(interactiveHighlight.gestureModifier)
                    },
                    containerColor = Transparent,
                    contentColor = contentColorFor(color),
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                    content = { AgentChatIcon() },
                )
            },
            containerColor = backgroundColor,
            contentWindowInsets = AppWindowInsets,
        ) {
            val contentPadding = it + contentPadding
            Calendar(
                modifier = Modifier
                    .layerBackdrop(backdrop)
                    .padding(contentPadding),
                headerModifier = Modifier.padding(vertical = 4.dp),
                state = viewModel.calendarState,
                onSystemDateChanged = { lastDay, newDay ->
                    coroutineScope.launch {
                        viewModel.onSystemDateChanged(lastDay, newDay)
                    }
                },
                getEvents = viewModel::getEvents,
                onDayClick = viewModel::onCalendarDayClick,
                onEventClick = viewModel::onCalendarEventClick,
            )
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

        var showCalendarPermissionRationale by remember { mutableStateOf(false) }
        val calendarPermissionsState = rememberMultiplePermissionsState(
            permissions = listOf(
                android.Manifest.permission.READ_CALENDAR,
                android.Manifest.permission.WRITE_CALENDAR,
            ),
        )

        if (calendarPermissionsState.allPermissionsGranted) {
            LaunchedEffect(Unit) {
                context.syncEvents()
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

        if (showCalendarPermissionRationale) {
            AlertDialog(
                onDismissRequest = { showCalendarPermissionRationale = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showCalendarPermissionRationale = false
                            calendarPermissionsState.launchMultiplePermissionRequest()
                        },
                        content = { Text(stringResource(R.string.permission_request_confirm_label)) },
                    )
                },
                dismissButton = {
                    TextButton(
                        onClick = { showCalendarPermissionRationale = false },
                        content = { Text(stringResource(android.R.string.cancel)) },
                    )
                },
                title = { Text(stringResource(R.string.permission_request_calendar_title)) },
                text = { Text(stringResource(R.string.permission_request_calendar_message)) },
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            var showNotificationPermissionRationale by remember { mutableStateOf(false) }
            val notificationPermissionState =
                rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)

            if (!notificationPermissionState.status.isGranted) {
                if (notificationPermissionState.status.shouldShowRationale) {
                    SideEffect {
                        showNotificationPermissionRationale = true
                    }
                } else {
                    SideEffect {
                        notificationPermissionState.launchPermissionRequest()
                    }
                }
            }

            if (showNotificationPermissionRationale) {
                AlertDialog(
                    onDismissRequest = { showNotificationPermissionRationale = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showNotificationPermissionRationale = false
                                notificationPermissionState.launchPermissionRequest()
                            },
                            content = { Text(stringResource(R.string.permission_request_confirm_label)) },
                        )
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showNotificationPermissionRationale = false },
                            content = { Text(stringResource(android.R.string.cancel)) },
                        )
                    },
                    title = { Text(stringResource(R.string.permission_request_notification_title)) },
                    text = { Text(stringResource(R.string.permission_request_notification_message)) },
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            var showAlarmPermissionRationale by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (!context.canScheduleExactAlarms) {
                    showAlarmPermissionRationale = true
                } else {
                    context.scheduleNextSuggestionGeneration()
                    context.scheduleClassProgressNotificationScheduling()
                }
            }

            if (showAlarmPermissionRationale) {
                AlertDialog(
                    onDismissRequest = { showAlarmPermissionRationale = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showAlarmPermissionRationale = false
                                context.checkOrRequestExactAlarmsPermission()
                            },
                            content = { Text(stringResource(R.string.permission_request_confirm_label)) },
                        )
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showAlarmPermissionRationale = false },
                            content = { Text(stringResource(android.R.string.cancel)) },
                        )
                    },
                    title = { Text(stringResource(R.string.permission_request_alarm_title)) },
                    text = { Text(stringResource(R.string.permission_request_alarm_message)) },
                )
            }
        }
    }
}
