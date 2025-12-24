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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.MarkdownState
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.parseMarkdownFlow
import com.mikepenz.markdown.model.rememberMarkdownState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlin.random.Random

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

private class MarkdownViewModel(markdownFlow: Flow<String>) : ViewModel() {
    val markdownState = markdownFlow
        .flatMapLatest { parseMarkdownFlow(it) }
        .filter { it !is State.Loading }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, State.Loading())
}

@Composable
fun MarkdownView(
    markdownFlow: Flow<String>,
    modifier: Modifier = Modifier,
    key: String = rememberSaveable { Random.nextLong().toString() },
) {
    val viewModel = viewModel(key = key) { MarkdownViewModel(markdownFlow) }
    val markdownState by viewModel.markdownState.collectAsStateWithLifecycle()

    MarkdownView(
        markdownState,
        modifier = modifier,
    )
}
