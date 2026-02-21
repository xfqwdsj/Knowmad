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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kyant.capsule.ContinuousRoundedRectangle
import com.mikepenz.markdown.annotator.AnnotatorSettings
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.compose.LocalMarkdownAnimations
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownDimens
import com.mikepenz.markdown.compose.LocalMarkdownInlineContent
import com.mikepenz.markdown.compose.LocalMarkdownPadding
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.MarkdownElement
import com.mikepenz.markdown.compose.MarkdownSuccess
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownDivider
import com.mikepenz.markdown.compose.elements.MarkdownText
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.DefaultMarkdownInlineContent
import com.mikepenz.markdown.model.MarkdownState
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.markdownAnnotator
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
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

const val MathContentIdPrefix = "math"

@Composable
fun MarkdownView(
    state: State,
    mathJaxRendererState: MathJaxRendererState?,
    modifier: Modifier = Modifier,
    contentKey: Any? = null,
    mathResults: Map<String, Result<MathJaxRenderResult>?>? = null,
    mathFontSize: MathJaxFontSize = remember { MathJaxFontSize() },
    success: MarkdownSuccessContent = DefaultMarkdownSuccessContent,
) {
    val coroutineScope = rememberCoroutineScope()

    val mathResults = mathResults ?: remember(contentKey ?: state) { mutableStateMapOf() }

    Markdown(
        state,
        modifier = modifier,
        annotator = markdownAnnotator { content, child ->
            when (val type = child.type) {
                GFMElementTypes.INLINE_MATH, GFMElementTypes.BLOCK_MATH -> {
                    val expression =
                        getMathContent(content, child) ?: return@markdownAnnotator false
                    val id = "$MathContentIdPrefix-$type-${child.startOffset}"
                    val display = type == GFMElementTypes.BLOCK_MATH

                    appendInlineContent(
                        id = id,
                        alternateText = expression.ifBlank { "<math expression>" },
                    )
                    val mutableResults = mathResults as? MutableMap
                    mutableResults?.getOrPut(id) { null }?.getOrNull().let { result ->
                        if (mathJaxRendererState != null && result?.tex != expression) {
                            coroutineScope.launch {
                                mutableResults?.set(
                                    id,
                                    runCatching {
                                        val state =
                                            snapshotFlow { mathJaxRendererState }.first { it !is Initializing }
                                        if (state is MathJaxRendererState.Error) throw state.throwable
                                        if (state !is Ready) error("MathJax renderer is not ready")
                                        state.renderer.renderToSvg(
                                            tex = expression,
                                            display = display,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    true
                }

                else -> false
            }
        },
        components = markdownComponents(
            codeFence = { (content, node, typography) ->
                MarkdownCodeFence(content, node, typography.code)
            },
            codeBlock = { (content, node, typography) ->
                MarkdownCodeBlock(content, node, typography.code)
            },
            paragraph = { (content, node, typography) ->
                MarkdownParagraph(
                    content = content,
                    node = node,
                    mathResults = mathResults,
                    style = typography.paragraph,
                    mathFontSize = mathFontSize,
                )
            },
            custom = { type, model ->
                when (type) {
                    GFMElementTypes.INLINE_MATH, GFMElementTypes.BLOCK_MATH -> {
                        val expression = getMathContent(model.content, model.node)
                            ?: return@markdownComponents
                        val id = "$MathContentIdPrefix-$type-${model.node.startOffset}"
                        val display = type == GFMElementTypes.BLOCK_MATH
                        if (mathResults is MutableMap) {
                            val result = mathResults.getOrPut(id) { null }

                            Box(
                                modifier = Modifier
                                    .run {
                                        if (display) fillMaxWidth() else this
                                    }
                                    .horizontalScroll(rememberScrollState()),
                                contentAlignment = Alignment.Center,
                            ) {
                                MathJax(rendererResult = result?.getOrNull())
                            }

                            LaunchedEffect(id, mathJaxRendererState, result, expression) {
                                if (mathJaxRendererState != null && result?.getOrNull()?.tex != expression) {
                                    mathResults[id] = runCatching {
                                        val state =
                                            snapshotFlow { mathJaxRendererState }.first { it !is Initializing }
                                        if (state is MathJaxRendererState.Error) throw state.throwable
                                        if (state !is Ready) error("MathJax renderer is not ready")
                                        state.renderer.renderToSvg(
                                            tex = expression,
                                            display = display,
                                        )
                                    }
                                }
                            }
                        } else {
                            mathResults[id]?.let { result ->
                                Box(
                                    modifier = Modifier
                                        .run {
                                            if (display) fillMaxWidth()
                                            else this
                                        }
                                        .horizontalScroll(rememberScrollState()),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    MathJax(rendererResult = result.getOrNull())
                                }
                            } ?: (mathJaxRendererState as? Ready)?.renderer?.let {
                                Box(
                                    modifier = Modifier
                                        .run {
                                            if (display) fillMaxWidth() else this
                                        }
                                        .horizontalScroll(rememberScrollState()),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    MathJax(
                                        renderer = it,
                                        tex = expression,
                                    )
                                }
                            }
                        }
                    }
                }
            },
        ),
        success = success,
    )
}

@Composable
fun MarkdownCodeFence(
    content: String,
    node: ASTNode,
    style: TextStyle = LocalMarkdownTypography.current.code,
    block: @Composable (ASTNode, String, String?, TextStyle) -> Unit = { node, code, language, style ->
        MarkdownCode(node = node, code = code, language = language, style = style)
    },
) {
    val (code, language) = node.codeBlockOrNull(content) ?: return
    block(node, code, language, style)
}

@Composable
fun MarkdownCodeBlock(
    content: String,
    node: ASTNode,
    style: TextStyle = LocalMarkdownTypography.current.code,
    block: @Composable (ASTNode, String, String?, TextStyle) -> Unit = { node, code, language, style ->
        MarkdownCode(node = node, code = code, language = language, style = style)
    },
) {
    val (code, language) = node.codeBlockOrNull(content) ?: return
    block(node, code, language, style)
}

fun ASTNode.codeBlockOrNull(content: String) = when (type) {
    MarkdownElementTypes.CODE_FENCE -> {
        // CODE_FENCE_START, FENCE_LANG, EOL, {content // CODE_FENCE_CONTENT // x-times}, CODE_FENCE_END
        // CODE_FENCE_START, EOL, {content // CODE_FENCE_CONTENT // x-times}, EOL
        // CODE_FENCE_START, EOL, {content // CODE_FENCE_CONTENT // x-times}
        // CODE_FENCE_START, FENCE_LANG, EOL, {content // CODE_FENCE_CONTENT // x-times}

        if (children.size >= 3) {
            val language = findChildOfType(MarkdownTokenTypes.FENCE_LANG)?.getTextInNode(content)
                ?.toString()
            val start = children[2].startOffset
            val minCodeFenceCount = if (language != null && children.size > 3) 3 else 2
            val end = children[(children.size - 2).coerceAtLeast(minCodeFenceCount)].endOffset
            MarkdownCodeBlockData(
                code = content.subSequence(start, end).toString().replaceIndent(),
                language = language,
                codeStartOffset = start,
                codeEndOffset = end,
            )
        } else {
            // invalid code block, skipping
            null
        }
    }

    MarkdownElementTypes.CODE_BLOCK -> {
        val language =
            findChildOfType(MarkdownTokenTypes.FENCE_LANG)?.getTextInNode(content)?.toString()
        val start = children[0].startOffset
        val end = children[children.size - 1].endOffset
        MarkdownCodeBlockData(
            code = content.subSequence(start, end).toString().replaceIndent(),
            language = language,
            codeStartOffset = start,
            codeEndOffset = end,
        )
    }

    else -> null
}

@Immutable
data class MarkdownCodeBlockData(
    val code: String,
    val language: String?,
    val codeStartOffset: Int,
    val codeEndOffset: Int,
)

@Composable
fun MarkdownCode(
    node: ASTNode,
    code: String,
    language: String? = null,
    style: TextStyle = LocalMarkdownTypography.current.code,
    showHeader: Boolean = LocalMarkdownCodeEnableHeader.current,
) {
    val backgroundCodeColor = LocalMarkdownColors.current.codeBackground
    val codeBackgroundCornerSize = LocalMarkdownDimens.current.codeBackgroundCornerSize
    val codeBlockMaxHeight = LocalMarkdownCodeMaxHeight.current
    val codeBlockReversedScroll = LocalMarkdownReversedVerticalScroll.current
    val codeBlockPadding = LocalMarkdownPadding.current.codeBlock
    MarkdownCodeBackground(
        node = node,
        color = backgroundCodeColor,
        shape = ContinuousRoundedRectangle(codeBackgroundCornerSize),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        showHeader = showHeader,
        language = language,
        code = code,
    ) {
        SelectionContainer {
            Text(
                text = code,
                style = style,
                modifier = Modifier
                    .heightIn(max = codeBlockMaxHeight)
                    .horizontalScroll(rememberScrollState())
                    .run {
                        if (codeBlockMaxHeight.isSpecified) {
                            verticalScroll(
                                rememberScrollState(),
                                reverseScrolling = codeBlockReversedScroll,
                            )
                        } else this
                    }
                    .padding(codeBlockPadding),
            )
        }
    }
}

@Composable
fun MarkdownCodeBackground(
    node: ASTNode,
    color: Color,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    border: BorderStroke? = null,
    elevation: Dp = 0.dp,
    showHeader: Boolean = false,
    language: String? = null,
    code: String = "",
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(elevation, shape, clip = false)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .background(color = color, shape = shape)
            .clip(shape)
            .semantics(mergeDescendants = false) {
                isTraversalGroup = true
            }
            .pointerInput(Unit) {},
        propagateMinConstraints = true,
    ) {
        if (showHeader) {
            Column {
                MarkdownCodeTopBar(
                    node = node,
                    language = language,
                    code = code,
                )
                MarkdownDivider(
                    color = LocalMarkdownColors.current.dividerColor.copy(alpha = 0.3f),
                    thickness = 0.5.dp,
                )
                content()
            }
        } else {
            content()
        }
    }
}

@Composable
fun MarkdownCodeTopBar(
    node: ASTNode,
    language: String?,
    code: String,
    modifier: Modifier = Modifier,
) {
    val textColor = LocalMarkdownColors.current.text
    val languageComponents = language?.split("\\s+".toRegex())
    val runner = languageComponents?.let { components ->
        LocalMarkdownCodeFenceRunners.current[
            components.dropWhile {
                it.startsWith("{") && it.endsWith("}")
            }
        ]
    }

    CompositionLocalProvider(LocalContentColor provides textColor.copy(alpha = .6f)) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            Text(
                text = language?.uppercase() ?: "CODE",
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                style = TextStyle(
                    fontSize = 10.sp,
                    fontFamily = Monospace,
                ),
                softWrap = false,
                maxLines = 1,
            )

            if (languageComponents != null && runner != null) {
                val runEnabled = LocalMarkdownRunCodeEnabled.current
                RunIconButton(
                    onRun = { runner.run(node, languageComponents, code) },
                    modifier = Modifier.size(24.dp),
                    iconModifier = Modifier.size(16.dp),
                    enabled = runEnabled,
                )
            }

            CopyIconButton(
                onCopy = { null to code },
                modifier = Modifier.size(24.dp),
                iconModifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun MarkdownParagraph(
    content: String,
    node: ASTNode,
    mathResults: Map<String, Result<MathJaxRenderResult>?>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalMarkdownTypography.current.paragraph,
    mathFontSize: MathJaxFontSize = remember { MathJaxFontSize() },
    annotatorSettings: AnnotatorSettings = annotatorSettings(),
) {
    val density = LocalDensity.current
    val inlineContent = LocalMarkdownInlineContent.current

    val styledText = buildAnnotatedString {
        pushStyle(style.toSpanStyle())
        buildMarkdownAnnotatedString(
            content = content,
            node = node,
            annotatorSettings = annotatorSettings,
        )
        pop()
    }

    BoxWithConstraints {
        CompositionLocalProvider(
            LocalMarkdownInlineContent provides DefaultMarkdownInlineContent(
                inlineContent.inlineContent + mathResults.mapValues { (_, renderResult) ->
                    val result = renderResult?.getOrNull()
                    val display = result?.display == true

                    val ex = rememberEx(TextStyle(fontSize = mathFontSize.get(display)))

                    var placeholder by remember {
                        mutableStateOf(
                            result?.let { result ->
                                result.dpSizeOrNull(
                                    ex = ex,
                                    density = density,
                                )?.let {
                                    calculatePlaceHolder(
                                        width = if (display) maxWidth
                                        else it.width.coerceAtMost(maxWidth),
                                        height = it.height,
                                        density = density,
                                    )
                                }
                            } ?: Placeholder(
                                width = 0.sp,
                                height = 0.sp,
                                placeholderVerticalAlign = Center,
                            ),
                        )
                    }

                    LaunchedEffect(result, ex, density) {
                        result?.dpSizeOrNull(
                            ex = ex,
                            density = density,
                        )?.let {
                            placeholder = calculatePlaceHolder(
                                width = if (display) maxWidth
                                else it.width.coerceAtMost(maxWidth),
                                height = it.height,
                                density = density,
                            )
                        }
                    }

                    InlineTextContent(placeholder) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center,
                        ) {
                            MathJax(
                                rendererResult = result,
                                ex = ex,
                            )
                        }
                    }
                },
            ),
        ) {
            MarkdownText(
                styledText,
                modifier = modifier,
                style = style,
            )
        }
    }
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
    mathJaxRendererState: MathJaxRendererState?,
    modifier: Modifier = Modifier,
    contentKey: Any? = null,
    mathResults: Map<String, Result<MathJaxRenderResult>?>? = null,
    mathFontSize: MathJaxFontSize = remember { MathJaxFontSize() },
    success: MarkdownSuccessContent = DefaultMarkdownSuccessContent,
) {
    val markdownState = rememberMarkdownState(markdown, retainState = true)
    MarkdownView(
        markdownState,
        mathJaxRendererState = mathJaxRendererState,
        modifier = modifier,
        contentKey = contentKey ?: markdownState,
        mathResults = mathResults,
        mathFontSize = mathFontSize,
        success = success,
    )
}

@Composable
fun MarkdownView(
    stateFlow: StateFlow<State>,
    mathJaxRendererState: MathJaxRendererState?,
    modifier: Modifier = Modifier,
    contentKey: Any? = stateFlow,
    mathResults: Map<String, Result<MathJaxRenderResult>?>? = null,
    mathFontSize: MathJaxFontSize = remember { MathJaxFontSize() },
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
        mathJaxRendererState = mathJaxRendererState,
        modifier = modifier,
        contentKey = contentKey,
        mathResults = mathResults,
        mathFontSize = mathFontSize,
        success = success,
    )
}

@Composable
fun MarkdownView(
    markdownState: MarkdownState,
    mathJaxRendererState: MathJaxRendererState?,
    modifier: Modifier = Modifier,
    contentKey: Any? = markdownState,
    mathResults: Map<String, Result<MathJaxRenderResult>?>? = null,
    mathFontSize: MathJaxFontSize = remember { MathJaxFontSize() },
    success: MarkdownSuccessContent = DefaultMarkdownSuccessContent,
) {
    MarkdownView(
        markdownState.state,
        mathJaxRendererState = mathJaxRendererState,
        modifier = modifier,
        contentKey = contentKey,
        mathResults = mathResults,
        mathFontSize = mathFontSize,
        success = success,
    )
}

@Composable
fun MarkdownView(
    savedMarkdownState: SavedMarkdownState,
    mathJaxRendererState: MathJaxRendererState?,
    modifier: Modifier = Modifier,
    mathFontSize: MathJaxFontSize = remember { MathJaxFontSize() },
    success: MarkdownSuccessContent = DefaultMarkdownSuccessContent,
) {
    MarkdownView(
        savedMarkdownState.state,
        mathJaxRendererState = mathJaxRendererState,
        modifier = modifier,
        contentKey = savedMarkdownState,
        mathResults = savedMarkdownState.mathResults,
        mathFontSize = mathFontSize,
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
    val mathResults: MutableMap<String, Result<MathJaxRenderResult>?>

    @Immutable
    class Dynamic(
        coroutineScope: CoroutineScope,
        markdownFlow: Flow<String>,
        override val mathResults: MutableMap<String, Result<MathJaxRenderResult>?> = mutableStateMapOf(),
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

        suspend fun fixed() = Fixed(
            state = originalState.first { it !is State.Loading },
            mathResults = mathResults,
        )
    }

    @Immutable
    class Fixed(
        state: State,
        override val mathResults: MutableMap<String, Result<MathJaxRenderResult>?> = mutableStateMapOf(),
    ) : SavedMarkdownState {
        override val state = MutableStateFlow(state).asStateFlow()
    }

    companion object {
        suspend fun Fixed(
            markdownText: String,
            mathResults: MutableMap<String, Result<MathJaxRenderResult>?> = mutableStateMapOf(),
        ): Fixed {
            val state = parseMarkdownFlow(markdownText).flowOn(Dispatchers.Default)
                .first { it !is State.Loading }
            return Fixed(state, mathResults)
        }
    }
}

fun SavedMarkdownState(
    coroutineScope: CoroutineScope,
    markdownFlow: Flow<String>,
    mathResults: MutableMap<String, Result<MathJaxRenderResult>?> = mutableStateMapOf(),
) = SavedMarkdownState.Dynamic(
    coroutineScope,
    markdownFlow,
    mathResults,
)

@Composable
fun rememberSavedMarkdownState(
    markdownFlow: Flow<String>,
    mathResults: MutableMap<String, Result<MathJaxRenderResult>?> = remember { mutableStateMapOf() },
): SavedMarkdownState {
    val coroutineScope = rememberCoroutineScope()
    return remember(coroutineScope, markdownFlow, mathResults) {
        SavedMarkdownState(coroutineScope, markdownFlow, mathResults)
    }
}

@Composable
fun rememberSavedMarkdownState(
    markdownText: String,
    mathResults: MutableMap<String, Result<MathJaxRenderResult>?> = remember { mutableStateMapOf() },
): SavedMarkdownState {
    val coroutineScope = rememberCoroutineScope()

    val flow = remember { MutableStateFlow(markdownText) }

    LaunchedEffect(markdownText) {
        flow.value = markdownText
    }

    return remember(coroutineScope, flow, mathResults) {
        SavedMarkdownState(coroutineScope, flow, mathResults)
    }
}

context(viewModel: ViewModel)
fun SavedMarkdownState(
    markdownFlow: Flow<String>,
    mathResults: MutableMap<String, Result<MathJaxRenderResult>?> = mutableStateMapOf(),
) = SavedMarkdownState(
    viewModel.viewModelScope,
    markdownFlow,
    mathResults,
)

fun interface MarkdownCodeFenceRunner {
    fun run(node: ASTNode, components: List<String>, code: String)
}

val LocalMarkdownViewBlockParsing = staticCompositionLocalOf { false }

val LocalMarkdownCodeEnableHeader = staticCompositionLocalOf { true }
val LocalMarkdownCodeMaxHeight = staticCompositionLocalOf { Dp.Unspecified }
val LocalMarkdownReversedVerticalScroll = staticCompositionLocalOf { false }

val LocalMarkdownRunCodeEnabled = compositionLocalOf { false }
val LocalMarkdownCodeFenceRunners =
    compositionLocalOf { emptyMap<List<String>, MarkdownCodeFenceRunner>() }
