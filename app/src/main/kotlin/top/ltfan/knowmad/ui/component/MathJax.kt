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

import android.content.res.AssetManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.JsObject
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.converter.JsObjectConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import top.ltfan.knowmad.util.Json
import top.ltfan.knowmad.util.Logger
import kotlin.math.roundToInt
import kotlin.reflect.typeOf

@Composable
fun MathJax(
    renderer: MathJaxRenderer,
    tex: String,
    modifier: Modifier = Modifier,
    display: Boolean = false,
    colorFilter: ColorFilter? = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
    exFontSize: TextUnit = 6.sp,
    onSize: ((width: Dp, height: Dp) -> Unit)? = null,
    failure: @Composable ((Throwable) -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val ex = remember(textMeasurer, exFontSize) {
        textMeasurer.measure(
            text = "x",
            style = TextStyle(fontSize = exFontSize),
        ).size.height
    }

    val renderResult by produceState<Result<MathJaxRenderResult>?>(initialValue = null, renderer) {
        value = runCatching { renderer.renderToSvg(tex, display) }
    }

    renderResult?.onSuccess { result ->
        val (width, height) = with(density) {
            remember(result, ex) {
                result.attributes.singleOrNull()?.let { attributes ->
                    val width =
                        attributes["width"]?.removeSuffix("ex")?.toFloatOrNull()
                            ?.times(ex)?.roundToInt() ?: return@let null
                    val height =
                        attributes["height"]?.removeSuffix("ex")?.toFloatOrNull()
                            ?.times(ex)?.roundToInt() ?: return@let null
                    width.toDp() to height.toDp()
                }?.also { (width, height) ->
                    onSize?.invoke(width, height)
                } ?: (Dp.Unspecified to Dp.Unspecified)
            }
        }

        AsyncImage(
            model = remember(context, result) {
                ImageRequest.Builder(context)
                    .data(result.html.encodeToByteArray())
                    .decoderFactory(SvgDecoder.Factory())
                    .build()
            },
            contentDescription = tex,
            modifier = modifier
                .size(width, height)
                .horizontalScroll(rememberScrollState()),
            colorFilter = colorFilter,
        )
    }?.onFailure {
        failure?.invoke(it)
    }
}

@Immutable
class MathJaxRenderer(
    val assets: AssetManager,
) : AutoCloseable {
    private val files = listOf(
        "mathjax/startup.js",
        "mathjax/core.js",
        "mathjax/adaptors/liteDOM.js",
        "mathjax/input/tex.js",
        "mathjax/output/svg.js",
        "@mathjax/mathjax-newcm-font/svg.js",
        "index.js",
    )

    private val logger = Logger("MathJaxRenderer")

    private val quickJs = QuickJs.create(Dispatchers.Default).apply {
        addTypeConverters(MathJaxRenderResult)

        define("console") {
            function("log") { args ->
                logger.debug { args.joinToString(" ") }
                null
            }
        }
    }

    suspend fun initialize(
        loadExternal: suspend MathJaxRenderer.(path: String) -> String,
    ) {
        quickJs.asyncFunction("loadExternal") { path: String ->
            loadExternal(path)
        }
        files.forEach { file ->
            val code = assets.open("mathjax/$file").bufferedReader().use { it.readText() } +
                    "\n;void 0;"
            quickJs.evaluate<Unit>(code, filename = file)
        }
    }

    suspend fun renderToSvg(tex: String, display: Boolean = false) = renderToSvg(
        tex = tex,
        options = JsonObject(
            mapOf(
                "display" to Json.encodeToJsonElement(display),
            ),
        ),
    )

    suspend fun renderToSvg(tex: String, options: JsonObject): MathJaxRenderResult =
        quickJs.evaluate(
            "await renderToSvg(${Json.encodeToString(tex)}, ${Json.encodeToString(options)})",
        )

    override fun close() {
        quickJs.close()
    }

    companion object {
        suspend operator fun invoke(
            assets: AssetManager,
            loadExternal: suspend MathJaxRenderer.(path: String) -> String,
        ) = MathJaxRenderer(assets).apply {
            initialize(loadExternal)
        }
    }
}

@Serializable
@Immutable
data class MathJaxRenderResult(
    val html: String,
    val attributes: List<Map<String, String>>,
) {
    companion object : JsObjectConverter<MathJaxRenderResult> {
        override val targetType = typeOf<MathJaxRenderResult>()

        @Suppress("UNCHECKED_CAST")
        override fun convertToTarget(value: JsObject) = MathJaxRenderResult(
            html = value["html"] as String,
            attributes = value["attributes"] as List<Map<String, String>>,
        )
    }
}

@Composable
fun rememberMathJaxRenderer(
    loadExternal: suspend MathJaxRenderer.(path: String) -> String,
): MathJaxRenderer? {
    val assets = LocalContext.current.assets

    val currentLoadExternal by rememberUpdatedState(loadExternal)

    val renderer = remember(assets) { MathJaxRenderer(assets) }

    return produceState<MathJaxRenderer?>(initialValue = null, renderer) {
        renderer.initialize(currentLoadExternal)
        value = renderer

        awaitDispose {
            renderer.close()
        }
    }.value
}
