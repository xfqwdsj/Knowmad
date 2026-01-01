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

import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.database.AppDatabase
import top.ltfan.knowmad.data.llm.LLMConfigDao
import top.ltfan.knowmad.data.llm.LLMConfigEntity
import top.ltfan.knowmad.data.llm.LLMProviderConfigEntity
import top.ltfan.knowmad.data.llm.SupportedLLMProviders
import top.ltfan.knowmad.ui.theme.ListItemMaxWidth
import top.ltfan.knowmad.ui.theme.ProvideCompatibleShapes
import top.ltfan.knowmad.ui.util.AppWindowInsets
import top.ltfan.knowmad.ui.util.LocalSharedTransitionScope
import top.ltfan.knowmad.ui.util.SnackbarAction
import top.ltfan.knowmad.ui.util.only
import top.ltfan.knowmad.ui.util.plus
import top.ltfan.knowmad.ui.viewmodel.GlobalViewModel
import top.ltfan.knowmad.ui.viewmodel.LocalAgentViewModel
import top.ltfan.knowmad.util.CryptoManager
import top.ltfan.knowmad.util.asStringRes
import kotlin.uuid.Uuid

@Composable
fun LLMProviderConfig(
    editingProvider: LLMProviderConfigEntity?,
    onEditProvider: (LLMProviderConfigEntity?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    additionalScrollThresholdPadding: PaddingValues = PaddingValues(16.dp),
) {
    val viewModel = LocalAgentViewModel.current

    val coroutineScope = rememberCoroutineScope()

    LLMProviderConfigLazyColumn(
        dao = viewModel.llmConfigDao,
        state = viewModel.providerConfigLazyListState,
        modelState = rememberLLMConfigLazyListStateFactory(viewModel.llmConfigDao),
        onEditProvider = { onEditProvider(it) },
        onDeleteProvider = {
            viewModel.deleteProviderConfig(it) { onUndo ->
                coroutineScope.launch {
                    GlobalViewModel.showSnackbar(
                        message = R.string.label_deleted.asStringRes(),
                        action = SnackbarAction(
                            label = R.string.label_undo.asStringRes(),
                            onClick = onUndo,
                        ),
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                }
            }
        },
        onEditModel = {},
        onDeleteModel = {
            viewModel.deleteModelConfig(it) { onUndo ->
                coroutineScope.launch {
                    GlobalViewModel.showSnackbar(
                        message = R.string.label_deleted.asStringRes(),
                        action = SnackbarAction(
                            label = R.string.label_undo.asStringRes(),
                            onClick = onUndo,
                        ),
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                }
            }
        },
        onAddModel = {},
        modifier = modifier,
        contentPadding = contentPadding,
        additionalScrollThresholdPadding = additionalScrollThresholdPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    )

    editingProvider?.let { provider ->
        LLMProviderConfigEditingDialog(
            entity = provider,
            onDismissRequest = { onEditProvider(null) },
        )
    }
}

@Composable
fun LLMProviderSelectionDialog(
    onProviderSelected: (provider: LLMProvider) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var selectedProvider by remember { mutableStateOf<LLMProvider?>(null) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.llm_provider_label_select)) },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedProvider?.let { onProviderSelected(it) }
                    onDismissRequest()
                },
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                for ((provider, info) in SupportedLLMProviders) {
                    item(key = provider.id) {
                        LLMProviderInfo(
                            info = info,
                            modifier = Modifier
                                .widthIn(ListItemMaxWidth)
                                .fillMaxWidth(),
                            checked = selectedProvider == provider,
                            onCheckedChange = {
                                if (it) {
                                    selectedProvider = provider
                                }
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun LLMProviderConfigEditingDialog(
    entity: LLMProviderConfigEntity,
    onDismissRequest: () -> Unit,
    isNew: Boolean = false,
) {
    val info = SupportedLLMProviders[entity.provider] ?: return

    val viewModel = LocalAgentViewModel.current

    var name by remember { mutableStateOf(entity.name) }
    val apiKeyState = rememberTextFieldState()
    var baseUrl by remember { mutableStateOf(entity.baseUrl ?: info.defaultBaseUrl) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.llm_provider_label_edit)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    val newEntity = entity.copy(
                        name = name,
                        baseUrl = baseUrl,
                    ).run {
                        val apiKey =
                            apiKeyState.text.ifBlank { null }?.toString() ?: return@run this
                        val (apiKeyBytes, iv) = CryptoManager.LLMApiKey.encryptOrPlain(apiKey)
                        copy(
                            apiKey = apiKeyBytes,
                            iv = iv,
                        )
                    }
                    if (isNew) {
                        viewModel.addProviderConfig(newEntity) {}
                    } else {
                        viewModel.editProviderConfig(newEntity) {}
                    }
                },
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LLMProviderNameTextField(
                    name = name,
                    onNameChange = { name = it },
                )
                LLMProviderApiKeyTextField(
                    state = apiKeyState,
                    isUsingPlaintext = !CryptoManager.LLMApiKey.isKeyInitialized(),
                    providerInfo = info,
                    onRetryCryptoKeyInitialization = {
                        CryptoManager.LLMApiKey.generateKey()
                    },
                    notChanged = !isNew,
                )
                LLMProviderBaseUrlTextField(
                    baseUrl = baseUrl,
                    onBaseUrlChange = { baseUrl = it },
                    providerInfo = info,
                )
            }
        },
    )
}

@Composable
fun LLMProviderConfigLazyColumn(
    dao: LLMConfigDao,
    state: LLMProviderConfigLazyListState,
    modelState: (provider: LLMProviderConfigEntity) -> LLMConfigLazyListState,
    onEditProvider: (provider: LLMProviderConfigEntity) -> Unit,
    onDeleteProvider: (provider: LLMProviderConfigEntity) -> Unit,
    onEditModel: (model: LLMConfigEntity) -> Unit,
    onDeleteModel: (model: LLMConfigEntity) -> Unit,
    onAddModel: (provider: LLMProviderConfigEntity) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    additionalScrollThresholdPadding: PaddingValues = PaddingValues(16.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical =
        if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val entities = state.flow.collectAsLazyPagingItems()

    val hapticFeedback = LocalHapticFeedback.current

    ReorderableLazyColumn(
        itemCount = { entities.itemCount },
        itemKey = entities.itemKey { it.id },
        onMove = databaseEntitiesOnReorder(
            entities = entities,
            state = state,
            hapticFeedback = hapticFeedback,
            getRank = { it?.rank },
        ) { entity, newRank ->
            dao.updateProvider(entity.copy(rank = newRank))
        },
        modifier = modifier,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        additionalScrollThresholdPadding = additionalScrollThresholdPadding,
    ) { index, _ ->
        val entity = entities[index] ?: return@ReorderableLazyColumn

        val interactionSource = remember { MutableInteractionSource() }

        LLMProviderConfigItem(
            dao = dao,
            entity = entity,
            modelState = modelState(entity),
            onEditProvider = { onEditProvider(entity) },
            onDeleteProvider = { onDeleteProvider(entity) },
            onEditModel = onEditModel,
            onDeleteModel = onDeleteModel,
            onAddModel = { onAddModel(entity) },
            dragHandle = {
                ReorderableDragHandle(
                    interactionSource = interactionSource,
                    onDragStarted = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    },
                    onDragStopped = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                    },
                )
            },
            modifier = Modifier.run {
                if (animatedVisibilityScope == null) this
                else with(LocalSharedTransitionScope.current) {
                    sharedBounds(
                        rememberSharedContentState(
                            LLMProviderConfigItemSharedKey.Container(entity.id),
                        ),
                        animatedVisibilityScope,
                    )
                }
            },
            interactionSource = interactionSource,
        )
    }

    PagingReorderableUpdatedEffect(state, entities)
}

@Immutable
class LLMProviderConfigLazyListState(
    coroutineScope: CoroutineScope,
    pagingConfig: PagingConfig = PagingConfig(
        pageSize = 20,
        enablePlaceholders = false,
    ),
    entitiesFactory: () -> PagingSource<Int, LLMProviderConfigEntity>,
) : PagingLazyListState<Int, LLMProviderConfigEntity>(
    coroutineScope,
    pagingConfig,
    entitiesFactory,
), ReorderableState {
    override val listUpdated = initializeListUpdated()
}

@Composable
fun rememberLLMProviderConfigLazyListState(
    pagingConfig: PagingConfig = PagingConfig(
        pageSize = 20,
        enablePlaceholders = false,
    ),
    entitiesFactory: () -> PagingSource<Int, LLMProviderConfigEntity>,
): LLMProviderConfigLazyListState {
    val coroutineScope = rememberCoroutineScope()
    return remember(coroutineScope, pagingConfig, entitiesFactory) {
        LLMProviderConfigLazyListState(coroutineScope, pagingConfig, entitiesFactory)
    }
}

context(viewModel: ViewModel)
fun LLMProviderConfigLazyListState(
    entitiesFactory: () -> PagingSource<Int, LLMProviderConfigEntity>,
) = LLMProviderConfigLazyListState(
    coroutineScope = viewModel.viewModelScope,
    entitiesFactory = entitiesFactory,
)

@Composable
fun LLMProviderConfigItem(
    dao: LLMConfigDao,
    entity: LLMProviderConfigEntity,
    modelState: LLMConfigLazyListState,
    onEditProvider: () -> Unit,
    onDeleteProvider: () -> Unit,
    onEditModel: (model: LLMConfigEntity) -> Unit,
    onDeleteModel: (model: LLMConfigEntity) -> Unit,
    onAddModel: () -> Unit,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
) {
    val info = SupportedLLMProviders[entity.provider] ?: return

    val modelCount by modelState.modelCountFlow.collectAsState(0)
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        onClick = onEditProvider,
        modifier = modifier
            .widthIn(max = ListItemMaxWidth)
            .fillMaxWidth(),
        interactionSource = interactionSource,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        entity.provider.id,
                        style = MaterialTheme.typography.titleSmall,
                    )

                    Text(
                        entity.name,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onEditProvider) {
                        Icon(
                            painterResource(R.drawable.edit_24px),
                            contentDescription = stringResource(R.string.label_edit),
                        )
                    }
                    IconButton(onClick = onDeleteProvider) {
                        Icon(
                            painterResource(R.drawable.delete_24px),
                            contentDescription = stringResource(R.string.label_delete),
                        )
                    }
                    dragHandle()
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(info.label),
                style = MaterialTheme.typography.labelLarge,
            )

            Spacer(Modifier.height(8.dp))

            entity.baseUrl?.let { baseUrl ->
                Text(
                    text = baseUrl,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    val expandedRotation =
                        if (LocalLayoutDirection.current == LayoutDirection.Ltr) 180f else -180f
                    val expandIconRotation by animateFloatAsState(if (!isExpanded) 0f else expandedRotation)
                    val collapseIconRotation by animateFloatAsState(if (isExpanded) 0f else -expandedRotation)
                    AnimatedContent(
                        targetState = isExpanded,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                    ) { isExpanded ->
                        Icon(
                            painterResource(if (isExpanded) R.drawable.arrow_drop_down_24px else R.drawable.arrow_drop_up_24px),
                            contentDescription = stringResource(if (isExpanded) R.string.label_collapse else R.string.label_expand),
                            modifier = Modifier
                                .then(
                                    if (isExpanded) Modifier
                                        .rotate(collapseIconRotation)
                                    else Modifier
                                        .rotate(expandIconRotation),
                                ),
                        )
                    }
                }

                Text(
                    text = pluralStringResource(
                        R.plurals.llm_model_count_label,
                        modelCount,
                        modelCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                modifier = Modifier.fillMaxWidth(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Spacer(Modifier.height(8.dp))

                    LLMConfigLazyColumn(
                        dao = dao,
                        state = modelState,
                        onEditModel = onEditModel,
                        onDeleteModel = onDeleteModel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp),
                        additionalScrollThresholdPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.Start,
                    )

                    ProvideCompatibleShapes {
                        TextButton(
                            onClick = onAddModel,
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Icon(
                                painterResource(R.drawable.add_24px),
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.llm_model_add_label))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LLMConfigLazyColumn(
    dao: LLMConfigDao,
    state: LLMConfigLazyListState,
    onEditModel: (model: LLMConfigEntity) -> Unit,
    onDeleteModel: (model: LLMConfigEntity) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    additionalScrollThresholdPadding: PaddingValues = PaddingValues(16.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical =
        if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val entities = state.flow.collectAsLazyPagingItems()

    val hapticFeedback = LocalHapticFeedback.current

    ReorderableLazyColumn(
        itemCount = { entities.itemCount },
        itemKey = entities.itemKey { it.id },
        onMove = databaseEntitiesOnReorder(
            entities = entities,
            state = state,
            hapticFeedback = hapticFeedback,
            getRank = { it?.rank },
        ) { entity, newRank ->
            dao.updateModel(entity.copy(rank = newRank))
        },
        modifier = modifier,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        additionalScrollThresholdPadding = additionalScrollThresholdPadding,
    ) { index, _ ->
        val entity = entities[index] ?: return@ReorderableLazyColumn

        val interactionSource = remember { MutableInteractionSource() }

        LLMConfigItem(
            entity = entity,
            onEdit = { onEditModel(entity) },
            onDelete = { onDeleteModel(entity) },
            modifier = Modifier.run {
                if (animatedVisibilityScope == null) this
                else with(LocalSharedTransitionScope.current) {
                    sharedBounds(
                        rememberSharedContentState(
                            LLMProviderConfigItemSharedKey.Container(entity.id),
                        ),
                        animatedVisibilityScope,
                    )
                }
            },
            dragHandle = {
                ReorderableDragHandle(
                    interactionSource = interactionSource,
                    onDragStarted = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    },
                    onDragStopped = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                    },
                )
            },
            interactionSource = interactionSource,
        )
    }

    PagingReorderableUpdatedEffect(state, entities)
}

@Immutable
class LLMConfigLazyListState(
    coroutineScope: CoroutineScope,
    modelCountFlow: Flow<Int>,
    pagingConfig: PagingConfig = PagingConfig(
        pageSize = 20,
        enablePlaceholders = false,
    ),
    entitiesFactory: () -> PagingSource<Int, LLMConfigEntity>,
) : PagingLazyListState<Int, LLMConfigEntity>(
    coroutineScope,
    pagingConfig,
    entitiesFactory,
), ReorderableState {
    override val listUpdated = initializeListUpdated()
    val modelCountFlow = modelCountFlow.shareIn(
        coroutineScope,
        started = SharingStarted.Eagerly,
        replay = 1,
    )
}

@Composable
fun rememberLLMConfigLazyListState(
    modelCountFlow: Flow<Int>,
    pagingConfig: PagingConfig = PagingConfig(
        pageSize = 20,
        enablePlaceholders = false,
    ),
    entitiesFactory: () -> PagingSource<Int, LLMConfigEntity>,
): LLMConfigLazyListState {
    val coroutineScope = rememberCoroutineScope()
    return remember(coroutineScope, modelCountFlow, entitiesFactory) {
        LLMConfigLazyListState(
            coroutineScope,
            modelCountFlow,
            pagingConfig,
            entitiesFactory,
        )
    }
}

@Composable
fun rememberLLMConfigLazyListStateFactory(
    dao: LLMConfigDao,
): (provider: LLMProviderConfigEntity) -> LLMConfigLazyListState {
    val coroutineScope = rememberCoroutineScope()
    val map = remember { mutableStateMapOf<LLMProviderConfigEntity, LLMConfigLazyListState>() }
    return remember(dao, coroutineScope) {
        { provider: LLMProviderConfigEntity ->
            map.getOrPut(provider) {
                LLMConfigLazyListState(
                    coroutineScope = coroutineScope,
                    modelCountFlow = dao.getModelCountByProvider(provider.id),
                    entitiesFactory = { dao.getModelsByProvider(provider.id) },
                )
            }
        }
    }
}

context(viewModel: ViewModel)
fun LLMConfigLazyListState(
    modelCountFlow: Flow<Int>,
    entitiesFactory: () -> PagingSource<Int, LLMConfigEntity>,
) = LLMConfigLazyListState(
    coroutineScope = viewModel.viewModelScope,
    modelCountFlow = modelCountFlow,
    entitiesFactory = entitiesFactory,
)

@Composable
fun LLMConfigItem(
    entity: LLMConfigEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    elevation: ListItemElevation = ListItemDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
) {
    ProvideCompatibleShapes {
        ListItem(
            onClick = onEdit,
            modifier = modifier,
            trailingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            painterResource(R.drawable.edit_24px),
                            contentDescription = stringResource(R.string.label_edit),
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            painterResource(R.drawable.delete_24px),
                            contentDescription = stringResource(R.string.label_delete),
                        )
                    }
                    dragHandle()
                }
            },
            overlineContent = {
                Text(entity.model.id)
            },
            elevation = elevation,
            interactionSource = interactionSource,
        ) {
            Text(entity.name)
        }
    }
}

sealed interface LLMProviderConfigItemSharedKey {
    val id: Uuid

    data class Container(override val id: Uuid) : LLMProviderConfigItemSharedKey
}

sealed interface LLMConfigItemSharedKey {
    val id: Uuid

    data class Container(override val id: Uuid) : LLMConfigItemSharedKey
}

@Preview
@Composable
fun LLMProviderConfigLazyColumnPreview() {
    ApplicationPreview {
        val coroutineScope = rememberCoroutineScope()
        val dao = remember { AppDatabase.buildDatabase().llmConfigDao() }

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.windowInsetsPadding(AppWindowInsets.only { top }),
            ) {
                Button(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            SupportedLLMProviders.keys.forEach { provider ->
                                dao.insertProviderAtEnd(
                                    LLMProviderConfigEntity(
                                        provider = provider,
                                        apiKey = "sample_api_key_for_${provider.id}".toByteArray(),
                                        iv = null,
                                    ),
                                )
                            }
                        }
                    },
                ) {
                    Text("Add Sample Data")
                }
                Button(
                    onClick = {},
                ) {
                    Text("Clear All")
                }
            }
            LLMProviderConfigLazyColumn(
                dao = dao,
                state = rememberLLMProviderConfigLazyListState(
                    entitiesFactory = { dao.getAllProviders() },
                ),
                modelState = rememberLLMConfigLazyListStateFactory(dao),
                onEditProvider = {},
                onDeleteProvider = {
                    coroutineScope.launch(Dispatchers.IO) {
                        dao.deleteProvider(it)
                    }
                },
                onEditModel = {},
                onDeleteModel = {
                    coroutineScope.launch(Dispatchers.IO) {
                        dao.deleteModel(it)
                    }
                },
                onAddModel = {
                    coroutineScope.launch(Dispatchers.IO) {
                        dao.insertModelAtEnd(
                            LLMConfigEntity(
                                providerConfigId = it.id,
                                model = LLModel(
                                    provider = it.provider,
                                    id = "sample-model-for-${it.provider.id}",
                                    capabilities = listOf(),
                                    contextLength = 1024,
                                ),
                            ),
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = AppWindowInsets.only { horizontal + bottom }
                    .asPaddingValues() + PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            )
        }
    }
}
