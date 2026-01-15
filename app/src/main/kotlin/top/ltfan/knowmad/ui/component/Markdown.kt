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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.MarkdownState
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.parseMarkdownFlow
import com.mikepenz.markdown.model.rememberMarkdownState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

@Composable
fun MarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val markdownState = rememberMarkdownState(markdown, retainState = true)
    MarkdownView(
        markdownState,
        modifier = modifier,
    )
}

@Composable
fun MarkdownView(
    markdownState: MarkdownState,
    modifier: Modifier = Modifier,
) {
    val state by markdownState.state.collectAsState()
    MarkdownView(
        state,
        modifier = modifier,
    )
}

@Composable
fun MarkdownView(
    state: State,
    modifier: Modifier = Modifier,
) {
    Markdown(
        state,
        modifier = modifier,
        components = markdownComponents { type, model ->

        },
    )
}

@Composable
fun MarkdownView(
    savedMarkdownState: SavedMarkdownState,
    modifier: Modifier = Modifier,
) {
    val markdownState by savedMarkdownState.state.collectAsStateWithLifecycle()

    MarkdownView(
        markdownState,
        modifier = modifier,
    )
}

@Immutable
class SavedMarkdownState(coroutineScope: CoroutineScope, markdownFlow: Flow<String>) {
    val state = markdownFlow
        .flatMapLatest { parseMarkdownFlow(it) }
        .filter { it !is State.Loading }
        .flowOn(Dispatchers.Default)
        .stateIn(coroutineScope, SharingStarted.Eagerly, State.Loading())
}

@Composable
fun rememberSavedMarkdownState(markdownFlow: Flow<String>): SavedMarkdownState {
    val coroutineScope = rememberCoroutineScope()
    return remember(coroutineScope, markdownFlow) {
        SavedMarkdownState(coroutineScope, markdownFlow)
    }
}

@Composable
fun rememberSavedMarkdownState(markdownText: String): SavedMarkdownState {
    val coroutineScope = rememberCoroutineScope()

    val flow = remember { MutableStateFlow(markdownText) }

    LaunchedEffect(markdownText) {
        flow.value = markdownText
    }

    return remember(coroutineScope, flow) {
        SavedMarkdownState(coroutineScope, flow)
    }
}

context(viewModel: ViewModel)
fun SavedMarkdownState(markdownFlow: Flow<String>) = SavedMarkdownState(
    viewModel.viewModelScope,
    markdownFlow,
)
