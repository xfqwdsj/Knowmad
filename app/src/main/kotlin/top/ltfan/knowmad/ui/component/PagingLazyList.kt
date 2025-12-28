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

import androidx.compose.runtime.Immutable
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import kotlinx.coroutines.CoroutineScope

@Immutable
open class PagingLazyListState<Key : Any, Value : Any>(
    coroutineScope: CoroutineScope,
    pagingSourceFactory: () -> PagingSource<Key, Value>,
) {
    val flow = Pager(
        config = PagingConfig(
            pageSize = 20,
        ),
        pagingSourceFactory = pagingSourceFactory,
    ).flow.cachedIn(coroutineScope)
}
