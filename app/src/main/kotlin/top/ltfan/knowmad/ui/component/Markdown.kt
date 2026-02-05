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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mikepenz.markdown.compose.LocalMarkdownAnimations
import com.mikepenz.markdown.compose.MarkdownElement
import com.mikepenz.markdown.compose.MarkdownSuccess
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.MarkdownState
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownInlineContent
import com.mikepenz.markdown.model.parseMarkdownFlow
import com.mikepenz.markdown.model.rememberMarkdownState
import com.mikepenz.markdown.utils.EntityConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import top.ltfan.knowmad.ui.util.rememberEx

typealias MarkdownSuccessContent = @Composable (
    state: State.Success,
    components: MarkdownComponents,
    modifier: Modifier,
) -> Unit

val DefaultMarkdownSuccessContent: MarkdownSuccessContent = { state, components, modifier ->
    MarkdownSuccess(state = state, components = components, modifier = modifier)
}

const val InlineMathContentIdPrefix = "inline-math-"
const val BlockMathContentIdPrefix = "block-math-"

@Composable
fun MarkdownView(
    state: State,
    modifier: Modifier = Modifier,
    success: MarkdownSuccessContent = DefaultMarkdownSuccessContent,
) {
    val mathIds = remember(state) { mutableStateSetOf<String>() }

    val mathJaxRenderer = rememberMathJaxRenderer { it } // TODO: remove this

    Markdown(
        state,
        modifier = modifier,
        annotator = markdownAnnotator { content, child ->
            when (child.type) {
                GFMElementTypes.INLINE_MATH -> {
                    val expression =
                        getMathContent(content, child) ?: return@markdownAnnotator false
                    val id = InlineMathContentIdPrefix + expression
                    appendInlineContent(
                        id = id,
                        alternateText = expression,
                    )
                    mathIds += id
                    true
                }

                GFMElementTypes.BLOCK_MATH -> {
                    val expression =
                        getMathContent(content, child) ?: return@markdownAnnotator false
                    val id = BlockMathContentIdPrefix + expression
                    appendInlineContent(
                        id = id,
                        alternateText = expression,
                    )
                    mathIds += id
                    true
                }

                else -> false
            }
        },
        inlineContent = markdownInlineContent(
            mathIds.associateWith { id ->
                val (type, expression) = id.split("-math-", limit = 2)
                val renderResult = rememberMathJaxRenderResult(
                    renderer = mathJaxRenderer,
                    tex = expression,
                    display = type == "block",
                )

                // TODO: refactor
                val placeHolder = renderResult?.getOrNull()?.let { result ->
                    val ex = rememberEx(TextStyle(fontSize = MathJaxDefaultFontSize))
                    val density = LocalDensity.current
                    val dpSize = result.dpSizeOrNull(
                        ex = ex,
                        density = density,
                    )
                    remember(id, dpSize) {
                        dpSize?.let {
                            calculatePlaceHolder(
                                width = it.width,
                                height = dpSize.height,
                                density = density,
                            )
                        }
                    }
                } ?: Placeholder(
                    width = 0.sp,
                    height = 0.sp,
                    placeholderVerticalAlign = Center,
                )

                InlineTextContent(placeHolder) {
                    MathJax(
                        rendererResult = renderResult?.getOrNull(),
                        contentDescription = expression,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
        ),
        components = markdownComponents { type, model ->
            when (type) {
                GFMElementTypes.INLINE_MATH -> MathJax(
                    renderer = mathJaxRenderer
                        ?: return@markdownComponents,
                    tex = getMathContent(model.content, model.node)
                        ?: return@markdownComponents,
                    display = false,
                )

                GFMElementTypes.BLOCK_MATH -> MathJax(
                    renderer = mathJaxRenderer
                        ?: return@markdownComponents,
                    tex = getMathContent(model.content, model.node)
                        ?: return@markdownComponents,
                    display = true,
                )
            }
        },
        success = success,
    )
}

private fun getMathContent(fullText: String, node: ASTNode): String? {
    return when (node.type) {
        GFMElementTypes.INLINE_MATH, GFMElementTypes.BLOCK_MATH -> {
            val nodes = node.children.drop(1).dropLast(1)
            if (nodes.isEmpty()) return null
            val escapedContent =
                fullText.subSequence(nodes.first().startOffset, nodes.last().endOffset)
            EntityConverter.replaceEntities(
                escapedContent,
                processEntities = false,
                processEscapes = true,
            ).trimIndent()
        }

        else -> null
    }
}

private fun calculatePlaceHolder(
    width: Dp,
    height: Dp,
    density: Density,
): Placeholder {
    with(density) {
        return Placeholder(
            width = width.toSp(),
            height = height.toSp(),
            placeholderVerticalAlign = Center,
        )
    }
}

@Composable
fun MarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
    success: MarkdownSuccessContent = DefaultMarkdownSuccessContent,
) {
    val markdownState = rememberMarkdownState(markdown, retainState = true)
    MarkdownView(
        markdownState,
        modifier = modifier,
        success = success,
    )
}

@Composable
fun MarkdownView(
    stateFlow: StateFlow<State>,
    modifier: Modifier = Modifier,
    success: MarkdownSuccessContent = DefaultMarkdownSuccessContent,
) {
    val blockParsing = LocalMarkdownViewBlockParsing.current
    val initialState = remember(blockParsing) {
        if (blockParsing) {
            runBlocking(Dispatchers.Default) { stateFlow.first { it !is State.Loading } }
        } else {
            stateFlow.value
        }
    }

    val state by stateFlow.collectAsStateWithLifecycle(initialState)

    MarkdownView(
        state,
        modifier = modifier,
        success = success,
    )
}

@Composable
fun MarkdownView(
    markdownState: MarkdownState,
    modifier: Modifier = Modifier,
    success: MarkdownSuccessContent = DefaultMarkdownSuccessContent,
) {
    MarkdownView(
        markdownState.state,
        modifier = modifier,
        success = success,
    )
}

@Composable
fun MarkdownView(
    savedMarkdownState: SavedMarkdownState,
    modifier: Modifier = Modifier,
    success: MarkdownSuccessContent = DefaultMarkdownSuccessContent,
) {
    MarkdownView(
        savedMarkdownState.state,
        modifier = modifier,
        success = success,
    )
}

@Composable
fun MarkdownSuccessContentWithTrailingText(
    state: State.Success,
    components: MarkdownComponents,
    modifier: Modifier,
    trailing: String?,
) {
    val animations = LocalMarkdownAnimations.current

    Column(modifier) {
        state.node.children.forEach { node ->
            MarkdownElement(node, components, state.content)
        }
        trailing?.let { trailing ->
            Text(
                trailing,
                modifier = animations.animateTextSize(Modifier),
            )
        }
    }
}

sealed interface SavedMarkdownState {
    val state: StateFlow<State>

    @Immutable
    class Dynamic(
        coroutineScope: CoroutineScope,
        markdownFlow: Flow<String>,
    ) : SavedMarkdownState {
        private val originalState =
            markdownFlow.flatMapLatest { parseMarkdownFlow(it) }.flowOn(Dispatchers.Default)
                .stateIn(
                    coroutineScope + Dispatchers.Default,
                    SharingStarted.Eagerly,
                    State.Loading(),
                )

        override val state = originalState.filter { it !is State.Loading }
            .stateIn(coroutineScope + Dispatchers.Default, SharingStarted.Eagerly, State.Loading())

        suspend fun fixed() = Fixed(originalState.first { it !is State.Loading })
    }

    @Immutable
    class Fixed(state: State) : SavedMarkdownState {
        override val state = MutableStateFlow(state).asStateFlow()
    }

    companion object {
        suspend fun Fixed(markdownText: String): Fixed {
            val state = parseMarkdownFlow(markdownText).flowOn(Dispatchers.Default)
                .first { it !is State.Loading }
            return Fixed(state)
        }
    }
}

fun SavedMarkdownState(
    coroutineScope: CoroutineScope,
    markdownFlow: Flow<String>,
) = SavedMarkdownState.Dynamic(
    coroutineScope,
    markdownFlow,
)

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

val LocalMarkdownViewBlockParsing = staticCompositionLocalOf { false }
