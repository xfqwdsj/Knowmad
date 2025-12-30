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

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.ltfan.knowmad.R
import top.ltfan.knowmad.ui.util.plus
import top.ltfan.knowmad.util.calculateLexoRankForReorderableList
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
fun ReorderableLazyColumn(
    itemCount: () -> Int,
    itemKey: (index: Int) -> Any,
    onMove: suspend CoroutineScope.(LazyListItemInfo, LazyListItemInfo) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical =
        if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    additionalScrollThresholdPadding: PaddingValues = PaddingValues(16.dp),
    item: @Composable ReorderableCollectionItemScope.(index: Int, isDragging: Boolean) -> Unit,
) {
    val reorderableLazyListState = rememberReorderableLazyListState(
        state,
        scrollThresholdPadding = contentPadding + additionalScrollThresholdPadding,
        onMove = onMove,
    )

    LazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
    ) {
        items(
            count = itemCount(),
            key = itemKey,
        ) { index ->
            ReorderableItem(
                state = reorderableLazyListState,
                key = itemKey(index),
            ) {
                item(index, it)
            }
        }
    }
}

@Composable
fun ReorderableCollectionItemScope.ReorderableDragHandle(
    interactionSource: MutableInteractionSource? = null,
    onDragStarted: (Offset) -> Unit = {},
    onDragStopped: () -> Unit = {},
) {
    IconButton(
        onClick = {},
        modifier = Modifier.draggableHandle(
            interactionSource = interactionSource,
            onDragStarted = onDragStarted,
            onDragStopped = onDragStopped,
        ),
    ) {
        Icon(
            painterResource(R.drawable.drag_handle_24px),
            contentDescription = null,
        )
    }
}

@Composable
fun ReorderableCollectionItemScope.ReorderableLongPressDragHandle(
    interactionSource: MutableInteractionSource? = null,
    onDragStarted: (Offset) -> Unit = {},
    onDragStopped: () -> Unit = {},
) {
    IconButton(
        onClick = {},
        modifier = Modifier.longPressDraggableHandle(
            interactionSource = interactionSource,
            onDragStarted = onDragStarted,
            onDragStopped = onDragStopped,
        ),
    ) {
        Icon(
            painterResource(R.drawable.drag_handle_24px),
            contentDescription = null,
        )
    }
}

inline fun <Entity : Any> databaseEntitiesOnReorder(
    entities: LazyPagingItems<Entity>,
    state: ReorderableState,
    hapticFeedback: HapticFeedback,
    crossinline getRank: (entity: Entity?) -> ByteArray?,
    crossinline update: suspend CoroutineScope.(entity: Entity, newRank: ByteArray) -> Unit,
): suspend CoroutineScope.(LazyListItemInfo, LazyListItemInfo) -> Unit = lambda@{ from, to ->
    val fromEntity = entities[from.index] ?: return@lambda

    val newRank = calculateLexoRankForReorderableList(
        itemCount = entities.itemCount,
        fromIndex = from.index,
        toIndex = to.index,
        getRankAtIndex = { getRank(entities[it]) },
    )

    val requestedAt = Clock.System.now()
    launch(Dispatchers.IO) {
        update(fromEntity, newRank)
    }

    state.listUpdated.first { it >= requestedAt }
    hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
}

@Composable
fun PagingReorderableUpdatedEffect(state: ReorderableState, entities: LazyPagingItems<*>) {
    LaunchedEffect(entities.itemSnapshotList) {
        val isReady = entities.loadState.refresh is LoadState.NotLoading &&
                entities.loadState.append is LoadState.NotLoading &&
                entities.loadState.prepend is LoadState.NotLoading
        if (isReady) {
            state.listUpdated.emit(Clock.System.now())
        }
    }
}

@Immutable
interface ReorderableState {
    val listUpdated: MutableSharedFlow<Instant>

    fun initializeListUpdated() = MutableSharedFlow<Instant>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
}
