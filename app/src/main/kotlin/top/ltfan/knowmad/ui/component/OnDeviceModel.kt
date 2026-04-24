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

package top.ltfan.knowmad.ui.component

import ai.koog.prompt.llm.LLModel
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.head
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.ltfan.knowmad.R
import top.ltfan.knowmad.agent.client.DownloadSource
import top.ltfan.knowmad.application.KnowmadApplication
import top.ltfan.knowmad.ui.page.OnDeviceModelPage
import top.ltfan.knowmad.ui.theme.ListItemMaxWidth
import top.ltfan.knowmad.ui.theme.TopAppBarColorsTransparent
import top.ltfan.knowmad.ui.util.AppWindowInsets
import top.ltfan.knowmad.ui.util.BackdropEffectsLight
import top.ltfan.knowmad.ui.util.BackdropInteractiveHighlight
import top.ltfan.knowmad.ui.util.LinearBrushData
import top.ltfan.knowmad.ui.util.only
import top.ltfan.knowmad.ui.util.plus
import top.ltfan.knowmad.ui.util.progressiveBlurWithFallback
import top.ltfan.knowmad.ui.viewmodel.LocalAppViewModel
import top.ltfan.knowmad.ui.viewmodel.OnDeviceModelState
import top.ltfan.knowmad.ui.viewmodel.OnDeviceModelViewModel
import top.ltfan.knowmad.util.format
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun OnDeviceModelScreen(
    contentPadding: PaddingValues = PaddingValues(),
) {
    val appViewModel = LocalAppViewModel.current
    val context = LocalContext.current

    val viewModel = viewModel {
        OnDeviceModelViewModel(
            app = context.applicationContext as KnowmadApplication,
            providerConfigId = appViewModel.backStack
                .filterIsInstance<OnDeviceModelPage>()
                .last().providerConfigId,
            llmConfigDao = appViewModel.llmConfigDao,
        )
    }

    OnDeviceModelScreenContent(
        modelInfos = viewModel.modelInfos,
        modelStates = viewModel.modelStates,
        sourceOrder = viewModel.sourceOrder,
        onDownload = viewModel::downloadModel,
        onCancelDownload = viewModel::cancelDownload,
        onValidate = viewModel::validateModel,
        onDelete = viewModel::deleteModel,
        onAddToDatabase = viewModel::addModelToDatabase,
        isModelInDatabase = viewModel::isModelInDatabase,
        onUpdateSourceOrder = viewModel::sourceOrder::set,
        onBack = appViewModel.backStack::removeLastOrNull,
        contentPadding = contentPadding,
    )
}

@Composable
private fun OnDeviceModelScreenContent(
    modelInfos: Map<LLModel, *>,
    modelStates: Map<LLModel, OnDeviceModelState>,
    sourceOrder: List<DownloadSource>,
    onDownload: (LLModel) -> Unit,
    onCancelDownload: (LLModel) -> Unit,
    onValidate: (LLModel) -> Unit,
    onDelete: (LLModel) -> Unit,
    onAddToDatabase: (LLModel, String) -> Unit,
    isModelInDatabase: (LLModel) -> kotlinx.coroutines.flow.Flow<Boolean>,
    onUpdateSourceOrder: (List<DownloadSource>) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val background = MaterialTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop {
        drawRect(background)
        drawContent()
    }

    var showSourceDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            val appBarBackdrop = rememberLayerBackdrop()
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.on_device_model_label)) },
                modifier = Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = {
                        progressiveBlurWithFallback(
                            radius = 48.dp.toPx(),
                            data = LinearBrushData(start = Offset(0f, POSITIVE_INFINITY)),
                        )
                    },
                    highlight = null,
                    shadow = null,
                    exportedBackdrop = appBarBackdrop,
                ),
                navigationIcon = {
                    val coroutineScope = rememberCoroutineScope()
                    val interactiveHighlight = remember(coroutineScope) {
                        BackdropInteractiveHighlight(coroutineScope)
                    }
                    ArrowBackIconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .drawBackdrop(
                                backdrop = appBarBackdrop,
                                shape = { CircleShape },
                                effects = BackdropEffectsLight,
                                shadow = null,
                            )
                            .then(interactiveHighlight.modifier)
                            .then(interactiveHighlight.gestureModifier),
                    )
                },
                actions = {
                    val coroutineScope = rememberCoroutineScope()
                    val interactiveHighlight = remember(coroutineScope) {
                        BackdropInteractiveHighlight(coroutineScope)
                    }
                    IconButton(
                        onClick = { showSourceDialog = true },
                        modifier = Modifier
                            .drawBackdrop(
                                backdrop = appBarBackdrop,
                                shape = { CircleShape },
                                effects = BackdropEffectsLight,
                                shadow = null,
                            )
                            .then(interactiveHighlight.modifier)
                            .then(interactiveHighlight.gestureModifier),
                    ) {
                        Icon(
                            painterResource(R.drawable.settings_24px),
                            contentDescription = stringResource(R.string.on_device_model_source_dialog_title),
                        )
                    }
                },
                windowInsets = AppWindowInsets.only { horizontal + top },
                colors = TopAppBarColorsTransparent,
            )
        },
        contentWindowInsets = AppWindowInsets,
    ) { scaffoldPadding ->
        val contentPadding = scaffoldPadding + contentPadding + PaddingValues(16.dp)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(
                items = modelInfos.entries.toList(),
                key = { it.key.id },
            ) { (model, _) ->
                val state = modelStates[model] ?: NotDownloaded
                val inDatabase by isModelInDatabase(model).collectAsState(false)

                OnDeviceModelItem(
                    model = model,
                    state = state,
                    inDatabase = inDatabase,
                    onDownload = { onDownload(model) },
                    onCancelDownload = { onCancelDownload(model) },
                    onValidate = { onValidate(model) },
                    onDelete = { onDelete(model) },
                    onAddToDatabase = { name -> onAddToDatabase(model, name) },
                )
            }
        }
    }

    if (showSourceDialog) {
        OnDeviceModelSourceDialog(
            sourceOrder = sourceOrder,
            onUpdateSourceOrder = onUpdateSourceOrder,
            onDismiss = { showSourceDialog = false },
        )
    }
}

@Composable
private fun OnDeviceModelItem(
    model: LLModel,
    state: OnDeviceModelState,
    inDatabase: Boolean,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onValidate: () -> Unit,
    onDelete: () -> Unit,
    onAddToDatabase: (String) -> Unit,
) {
    var showNameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .widthIn(max = ListItemMaxWidth)
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = model.id,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            model.capabilities?.let { capabilities ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    capabilities.forEach { capability ->
                        Text(
                            text = capability.id,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            OnDeviceModelStatus(state)

            OnDeviceModelActions(
                state = state,
                inDatabase = inDatabase,
                onDownload = onDownload,
                onCancelDownload = onCancelDownload,
                onValidate = onValidate,
                onDelete = { showDeleteDialog = true },
                onAddToDatabase = { showNameDialog = true },
            )
        }
    }

    if (showNameDialog) {
        OnDeviceModelNameDialog(
            defaultName = model.id,
            onConfirm = { name ->
                onAddToDatabase(name)
                showNameDialog = false
            },
            onDismiss = { showNameDialog = false },
        )
    }

    if (showDeleteDialog) {
        OnDeviceModelDeleteDialog(
            modelName = model.id,
            onConfirm = {
                onDelete()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun OnDeviceModelStatus(state: OnDeviceModelState) {
    val locale = LocalLocale.current
    val measureFormat = remember(locale) {
        MeasureFormat.getInstance(locale.platformLocale, MeasureFormat.FormatWidth.SHORT)
    }

    when (state) {
        is NotDownloaded -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painterResource(R.drawable.arrow_circle_down_24px),
                    contentDescription = null,
                )
                Text(
                    text = stringResource(R.string.on_device_model_status_not_downloaded_label),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        is Validating -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = stringResource(R.string.on_device_model_status_validating_label),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        is Downloading -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.on_device_model_status_downloading_label),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (state.totalBytes != null) {
                        Text(
                            text = "${
                                formatByteMeasure(
                                    state.bytesDownloaded,
                                    measureFormat,
                                )
                            } / ${formatByteMeasure(state.totalBytes, measureFormat)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = formatByteMeasure(state.bytesDownloaded, measureFormat),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (state.totalBytes != null && state.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = {
                            (state.bytesDownloaded.toFloat() / state.totalBytes)
                                .coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    state.speed?.let { speed ->
                        Text(
                            text = "${formatByteMeasure(speed.toLong(), measureFormat)}/s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.etaMs?.let { eta ->
                        Text(
                            text = stringResource(
                                R.string.on_device_model_eta_label,
                                eta.milliseconds.format(locale = locale.platformLocale),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        is Downloaded -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painterResource(R.drawable.check_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = when (state.verification) {
                        Offline -> stringResource(R.string.on_device_model_status_downloaded_label)
                        SizeOnly -> stringResource(R.string.on_device_model_status_verified_size_label)
                        SizeAndHash -> stringResource(R.string.on_device_model_status_verified_hash_label)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        is Invalid -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painterResource(R.drawable.error_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringResource(
                        R.string.on_device_model_status_invalid_label,
                        state.reason,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        is OnDeviceModelState.Error -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painterResource(R.drawable.error_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun OnDeviceModelActions(
    state: OnDeviceModelState,
    inDatabase: Boolean,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onValidate: () -> Unit,
    onDelete: () -> Unit,
    onAddToDatabase: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        when (state) {
            is NotDownloaded, is OnDeviceModelState.Error -> {
                Button(onClick = onDownload) {
                    Icon(
                        painterResource(R.drawable.arrow_circle_down_24px),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.on_device_model_action_download_label))
                }
                if (state.hasFile) {
                    OutlinedButton(onClick = onDelete) {
                        Icon(
                            painterResource(R.drawable.delete_24px),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.label_delete))
                    }
                    OutlinedButton(onClick = onValidate) {
                        Icon(
                            painterResource(R.drawable.refresh_24px),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.on_device_model_action_validate_label))
                    }
                }
            }

            is Downloading -> {
                OutlinedButton(onClick = onCancelDownload) {
                    Icon(
                        painterResource(R.drawable.close_24px),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(android.R.string.cancel))
                }
            }

            is Validating -> {
                OutlinedButton(onClick = {}, enabled = false) {
                    Icon(
                        painterResource(R.drawable.delete_24px),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.label_delete))
                }
                OutlinedButton(onClick = {}, enabled = false) {
                    Icon(
                        painterResource(R.drawable.refresh_24px),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.on_device_model_action_validate_label))
                }
                if (!inDatabase) {
                    FilledTonalButton(onClick = {}, enabled = false) {
                        Icon(
                            painterResource(R.drawable.add_24px),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.on_device_model_action_database_add_label))
                    }
                } else {
                    FilledTonalButton(onClick = {}, enabled = false) {
                        Icon(
                            painterResource(R.drawable.check_24px),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.on_device_model_action_database_added_label))
                    }
                }
            }

            is Invalid -> {
                Button(onClick = onDownload) {
                    Icon(
                        painterResource(R.drawable.arrow_circle_down_24px),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.on_device_model_action_download_label))
                }
                OutlinedButton(onClick = onDelete) {
                    Icon(
                        painterResource(R.drawable.delete_24px),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.label_delete))
                }
                OutlinedButton(onClick = onValidate) {
                    Icon(
                        painterResource(R.drawable.refresh_24px),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.on_device_model_action_validate_label))
                }
            }

            is Downloaded -> {
                OutlinedButton(onClick = onDelete) {
                    Icon(
                        painterResource(R.drawable.delete_24px),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.label_delete))
                }
                OutlinedButton(onClick = onValidate) {
                    Icon(
                        painterResource(R.drawable.refresh_24px),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.on_device_model_action_validate_label))
                }
                if (!inDatabase) {
                    FilledTonalButton(onClick = onAddToDatabase) {
                        Icon(
                            painterResource(R.drawable.add_24px),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.on_device_model_action_database_add_label))
                    }
                } else {
                    FilledTonalButton(onClick = {}, enabled = false) {
                        Icon(
                            painterResource(R.drawable.check_24px),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.on_device_model_action_database_added_label))
                    }
                }
            }
        }
    }
}

@Composable
private fun OnDeviceModelNameDialog(
    defaultName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(defaultName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.on_device_model_name_dialog_title)) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.on_device_model_name_dialog_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun OnDeviceModelDeleteDialog(
    modelName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.on_device_model_delete_dialog_title)) },
        text = { Text(stringResource(R.string.on_device_model_delete_dialog_message, modelName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

private fun formatByteMeasure(bytes: Long, format: MeasureFormat): String {
    val value = bytes.toDouble()
    return when {
        value >= 1_073_741_824 -> format.formatMeasures(Measure(value / 1_073_741_824, GIGABYTE))
        value >= 1_048_576 -> format.formatMeasures(Measure(value / 1_048_576, MEGABYTE))
        value >= 1_024 -> format.formatMeasures(Measure(value / 1_024, KILOBYTE))
        else -> format.formatMeasures(Measure(value, BYTE))
    }
}

private sealed interface SourceTestResult {
    data object Testing : SourceTestResult
    data object Connected : SourceTestResult
    data class Failed(val message: String) : SourceTestResult
}

@Composable
private fun OnDeviceModelSourceDialog(
    sourceOrder: List<DownloadSource>,
    onUpdateSourceOrder: (List<DownloadSource>) -> Unit,
    onDismiss: () -> Unit,
) {
    val testResults = remember { mutableStateMapOf<DownloadSource, SourceTestResult>() }
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    val items = remember(sourceOrder) { sourceOrder.toMutableStateList() }

    val lazyListState = rememberLazyListState()
    val state = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to -> items.add(to.index, items.removeAt(from.index)) },
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.on_device_model_source_dialog_title)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onUpdateSourceOrder(items)
                    onDismiss()
                },
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        text = {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(items, key = { it }) { source ->
                    ReorderableItem(state, key = source) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ReorderableDragHandle(
                                onDragStarted = {
                                    hapticFeedback.performHapticFeedback(GestureThresholdActivate)
                                },
                                onDragStopped = {
                                    hapticFeedback.performHapticFeedback(GestureEnd)
                                },
                            )

                            Text(
                                text = source.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )

                            val testResult = testResults[source]
                            val testAction: () -> Unit = {
                                coroutineScope.launch(Dispatchers.Default) {
                                    testResults[source] = Testing
                                    try {
                                        val response = HttpClient {
                                            install(HttpTimeout) { requestTimeoutMillis = 10_000 }
                                        }.use { client -> client.head(source.testUrl) }
                                        testResults[source] = if (response.status.isSuccess()) {
                                            SourceTestResult.Connected
                                        } else {
                                            SourceTestResult.Failed("HTTP ${response.status}")
                                        }
                                    } catch (e: Throwable) {
                                        testResults[source] =
                                            SourceTestResult.Failed(e.message ?: "Unknown error")
                                    }
                                }
                            }

                            when (testResult) {
                                is Testing -> IconButton(onClick = {}, enabled = false) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }

                                is Connected -> IconButton(onClick = testAction) {
                                    Icon(
                                        painterResource(R.drawable.check_24px),
                                        contentDescription = stringResource(R.string.on_device_model_source_connected_label),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                is Failed -> IconButton(onClick = testAction) {
                                    Icon(
                                        painterResource(R.drawable.error_24px),
                                        contentDescription = stringResource(
                                            R.string.on_device_model_source_failed_label,
                                            testResult.message,
                                        ),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }

                                null -> IconButton(onClick = testAction) {
                                    Icon(
                                        painterResource(R.drawable.play_arrow_24px),
                                        contentDescription = stringResource(R.string.on_device_model_source_test_label),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}
