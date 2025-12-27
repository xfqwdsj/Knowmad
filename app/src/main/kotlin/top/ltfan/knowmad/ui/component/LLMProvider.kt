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

package top.ltfan.knowmad.ui.component

import ai.koog.prompt.llm.LLMProvider
import android.app.Application
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.database.AppDatabase
import top.ltfan.knowmad.data.llm.LLMConfigDao
import top.ltfan.knowmad.data.llm.LLMProviderConfigEntity
import top.ltfan.knowmad.data.llm.LLMProviderInfo
import top.ltfan.knowmad.data.llm.SupportedLLMProviders
import top.ltfan.knowmad.ui.theme.AppTheme
import top.ltfan.knowmad.ui.theme.ProvideCompatibleShapes
import top.ltfan.knowmad.ui.theme.ProvideShapes
import top.ltfan.knowmad.ui.util.LocalSharedTransitionScope
import top.ltfan.knowmad.ui.util.plus
import top.ltfan.knowmad.util.calculateLexoRank
import kotlin.random.Random
import kotlin.uuid.Uuid

@Composable
fun LLMProviderLazyColumn(
    dao: LLMConfigDao,
    onProviderClick: (LLMProviderConfigEntity) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    additionalScrollThresholdPadding: PaddingValues = PaddingValues(16.dp),
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    key: String = rememberSaveable { Random.nextLong().toString() },
) {
    val contentPadding = contentPadding + PaddingValues(vertical = 16.dp)

    val viewModel =
        viewModel(key = key) { LLMProviderLazyColumnViewModel { dao.getAllProviders() } }
    val entities = viewModel.entitiesFlow.collectAsLazyPagingItems()

    val hapticFeedback = LocalHapticFeedback.current

    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(
        lazyListState,
        scrollThresholdPadding = contentPadding + additionalScrollThresholdPadding,
    ) { from, to ->
        viewModel.listUpdatedChannel.tryReceive()

        val fromEntity = entities[from.index] ?: return@rememberReorderableLazyListState
        val toIndex = to.index
        val (prevRank, nextRank) = if (toIndex < from.index) {
            val prev = if (toIndex > 0) entities[toIndex - 1]?.rank else null
            val next = entities[toIndex]?.rank
            prev to next
        } else {
            val prev = entities[toIndex]?.rank
            val next = if (toIndex < entities.itemCount - 1) entities[toIndex + 1]?.rank else null
            prev to next
        }
        val newRank = calculateLexoRank(prevRank, nextRank)
        val updatedEntity = fromEntity.copy(rank = newRank)

        launch(Dispatchers.IO) {
            dao.updateProvider(updatedEntity)
        }

        viewModel.listUpdatedChannel.receive()
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    LazyColumn(
        modifier = modifier,
        state = lazyListState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(
            count = entities.itemCount,
            key = entities.itemKey { it.id },
        ) { index ->
            val entity = entities[index] ?: return@items
            ReorderableItem(
                state = reorderableLazyListState,
                key = entity.id,
            ) {
                val interactionSource = remember { MutableInteractionSource() }

                LLMProviderItem(
                    entity = entity,
                    onClick = { onProviderClick(entity) },
                    modifier = Modifier.run {
                        if (animatedVisibilityScope == null) this
                        else with(LocalSharedTransitionScope.current) {
                            sharedBounds(
                                rememberSharedContentState(
                                    LLMProviderItemSharedKey.Container(entity.id),
                                ),
                                animatedVisibilityScope,
                            )
                        }
                    },
                    trailingContent = {
                        IconButton(
                            onClick = {},
                            modifier = Modifier.draggableHandle(
                                interactionSource = interactionSource,
                                onDragStarted = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                },
                                onDragStopped = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                },
                            ),
                        ) {
                            Icon(
                                painterResource(R.drawable.drag_handle_24px),
                                contentDescription = null,
                            )
                        }
                    },
                    interactionSource = interactionSource,
                )
            }
        }
    }

    LaunchedEffect(entities.loadState) {
        if (entities.loadState.refresh is LoadState.NotLoading) {
            viewModel.listUpdatedChannel.trySend(Unit)
        }
    }
}

private class LLMProviderLazyColumnViewModel(
    entitiesFactory: () -> PagingSource<Int, LLMProviderConfigEntity>,
) : ViewModel() {
    val listUpdatedChannel = Channel<Unit>()

    val entitiesFlow = Pager(
        config = PagingConfig(
            pageSize = 20,
        ),
        pagingSourceFactory = entitiesFactory,
    ).flow.cachedIn(viewModelScope)
}

@Composable
fun LLMProviderItem(
    entity: LLMProviderConfigEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = {
        Icon(
            painterResource(R.drawable.drag_handle_24px),
            contentDescription = null,
        )
    },
    elevation: ListItemElevation = ListItemDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
) {
    val info = SupportedLLMProviders[entity.provider] ?: return
    ProvideCompatibleShapes {
        ListItem(
            onClick = onClick,
            modifier = modifier,
            leadingContent = {
                LLMProviderIcon(info)
            },
            trailingContent = trailingContent,
            overlineContent = {
                Text(stringResource(info.label))
            },
            supportingContent = {
                Text(stringResource(info.description))
            },
            elevation = elevation,
            interactionSource = interactionSource,
        ) {
            Text(entity.name)
        }
    }
}

sealed interface LLMProviderItemSharedKey {
    val id: Uuid

    data class Container(override val id: Uuid) : LLMProviderItemSharedKey
}

@Preview
@Composable
fun LLMProviderLazyColumnPreview() {
    AppTheme {
        Surface {
            if (LocalInspectionMode.current) {
                Text("Inspection Mode")
                return@Surface
            }

            val application = LocalContext.current.applicationContext as? Application ?: run {
                Text("No Application Context")
                return@Surface
            }

            val coroutineScope = rememberCoroutineScope()
            val dao =
                remember { context(application) { AppDatabase.buildDatabase() }.llmConfigDao() }

            Column(Modifier.fillMaxSize()) {
                Row {
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
                LLMProviderLazyColumn(
                    dao = dao,
                    onProviderClick = {},
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Preview
@Composable
fun LLMProviderItemPreview() {
    AppTheme {
        LLMProviderItem(
            entity = LLMProviderConfigEntity(
                provider = LLMProvider.DeepSeek,
                name = "DeepSeek",
                apiKey = ByteArray(0),
                iv = null,
            ),
            onClick = {},
        )
    }
}

@Composable
fun LLMProviderInfo(
    info: LLMProviderInfo,
    modifier: Modifier = Modifier,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ProvideCompatibleShapes {
        ListItem(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            leadingContent = {
                LLMProviderIcon(info)
            },
            supportingContent = {
                Text(stringResource(info.description))
            },
        ) {
            Text(stringResource(info.label))
        }
    }
}

@Preview
@Composable
fun LLMProviderInfoPreview() {
    AppTheme {
        Column {
            LLMProviderInfo(
                info = SupportedLLMProviders[LLMProvider.DeepSeek]!!,
                checked = true,
                onCheckedChange = {},
            )
            LLMProviderInfo(
                info = SupportedLLMProviders[LLMProvider.OpenAI]!!,
                checked = false,
                onCheckedChange = {},
            )
        }
    }
}

@Composable
private fun LLMProviderIcon(
    info: LLMProviderInfo,
    modifier: Modifier = Modifier,
) {
    ProvideShapes {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = modifier,
        ) {
            Icon(
                painterResource(info.icon),
                contentDescription = null,
                modifier = Modifier
                    .padding(12.dp)
                    .size(32.dp),
            )
        }
    }
}
