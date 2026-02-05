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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size
import coil3.svg.SvgDecoder
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.JsObject
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.converter.JsObjectConverter
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import top.ltfan.knowmad.ui.util.rememberEx
import top.ltfan.knowmad.util.Json
import top.ltfan.knowmad.util.Logger
import kotlin.math.roundToInt
import kotlin.reflect.typeOf

val MathJaxDefaultFontSize = 6.5f.sp
const val MathJaxDefaultRenderingScale = 1.6f

typealias MathJaxLoadExternal = suspend MathJaxRenderer.(path: String) -> String

@Composable
fun MathJax(
    renderer: MathJaxRenderer,
    tex: String,
    modifier: Modifier = Modifier,
    display: Boolean = false,
    contentDescription: String? = tex,
    renderingScale: Float = MathJaxDefaultRenderingScale,
    filterQuality: FilterQuality = High,
    colorFilter: ColorFilter? = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
    ex: Int = rememberEx(TextStyle(fontSize = MathJaxDefaultFontSize)),
    failure: @Composable ((Throwable) -> Unit)? = null,
) {
    val renderResult = rememberMathJaxRenderResult(
        renderer = renderer,
        tex = tex,
        display = display,
    )

    renderResult?.onSuccess { result ->
        MathJax(
            rendererResult = result,
            contentDescription = contentDescription,
            modifier = modifier,
            renderingScale = renderingScale,
            filterQuality = filterQuality,
            colorFilter = colorFilter,
            ex = ex,
        )
    }?.onFailure {
        failure?.invoke(it)
    }
}

@Composable
fun MathJax(
    rendererResult: MathJaxRenderResult?,
    modifier: Modifier = Modifier,
    contentDescription: String? = rendererResult?.tex,
    renderingScale: Float = MathJaxDefaultRenderingScale,
    filterQuality: FilterQuality = High,
    colorFilter: ColorFilter? = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
    ex: Int = rememberEx(TextStyle(fontSize = MathJaxDefaultFontSize)),
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val size = remember(rendererResult, ex) { rendererResult?.pxSizeOrNull(ex) }
    val dpSize = with(density) { size?.let { DpSize(it.width.toDp(), it.height.toDp()) } }
        ?: DpSize.Unspecified

    val imageRequest = remember(context, rendererResult, size) {
        if (rendererResult == null || size == null) {
            return@remember null
        }
        val width = size.width.times(renderingScale).roundToInt()
        val height = size.height.times(renderingScale).roundToInt()
        ImageRequest.Builder(context)
            .data(rendererResult.html.encodeToByteArray())
            .size(Size(width, height))
            .decoderFactory(SvgDecoder.Factory())
            .build()
    }

    val asyncPainter = rememberAsyncImagePainter(
        model = imageRequest,
        filterQuality = filterQuality,
    )
    val painter by remember {
        snapshotFlow { asyncPainter }
            .flatMapLatest { asyncPainter ->
                asyncPainter.state.map {
                    if (it is AsyncImagePainter.State.Success) {
                        asyncPainter
                    } else {
                        null
                    }
                }
            }
            .filterNotNull()
    }.collectAsState(null)

    painter?.let {
        Image(
            it,
            contentDescription = contentDescription,
            modifier = modifier.size(dpSize),
            colorFilter = colorFilter,
        )
    }
}

@Immutable
class MathJaxRenderer(
    val assets: AssetManager,
    val cacheCapacity: Int = 100,
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

    private val mutex = Mutex()

    private val cache = object : LinkedHashMap<Pair<String, JsonObject>, MathJaxRenderResult>(
        cacheCapacity, 0.75f, true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, JsonObject>, MathJaxRenderResult>): Boolean {
            return size > cacheCapacity
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
        mutex.withLock {
            val tex = tex.trim()
            val key = tex to options
            cache[key]?.let {
                return it
            }
            val result = quickJs.evaluate<MathJaxRenderResult>(
                "await renderToSvg(${Json.encodeToString(tex)}, ${Json.encodeToString(options)})",
            )
            cache[key] = result
            return result
        }

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

fun MathJaxRenderResult.sizeOrNull(): Pair<String, String>? {
    val attrs = attributes.singleOrNull() ?: return null
    val width = attrs["width"] ?: return null
    val height = attrs["height"] ?: return null
    return width to height
}

fun MathJaxRenderResult.exSizeOrNull(): Pair<Float, Float>? {
    val (width, height) = sizeOrNull() ?: return null
    val widthEx = width.removeSuffix("ex").toFloatOrNull() ?: return null
    val heightEx = height.removeSuffix("ex").toFloatOrNull() ?: return null
    return widthEx to heightEx
}

fun MathJaxRenderResult.pxSizeOrNull(ex: Int): IntSize? {
    val (widthEx, heightEx) = exSizeOrNull() ?: return null
    return IntSize((ex * widthEx).roundToInt(), (ex * heightEx).roundToInt())
}

fun MathJaxRenderResult.dpSizeOrNull(ex: Dp): DpSize? {
    val (widthEx, heightEx) = exSizeOrNull() ?: return null
    return DpSize(ex * widthEx, ex * heightEx)
}

fun MathJaxRenderResult.dpSizeOrNull(ex: Int, density: Density): DpSize? {
    return with(density) { dpSizeOrNull(ex.toDp()) }
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

@Composable
fun rememberMathJaxRenderResult(
    renderer: MathJaxRenderer?,
    tex: String,
    display: Boolean = false,
): Result<MathJaxRenderResult>? {
    val currentTex by rememberUpdatedState(tex)
    val currentDisplay by rememberUpdatedState(display)

    return produceState<Result<MathJaxRenderResult>?>(initialValue = null, renderer) {
        if (renderer == null) {
            value = null
            return@produceState
        }
        value = runCatching { renderer.renderToSvg(currentTex, currentDisplay) }
    }.value
}

fun jsDelivrMathJaxLoadExternal(
    client: HttpClient = HttpClient(),
    logger: Logger? = Logger("JsDelivrMathJaxLoadExternal"),
): MathJaxLoadExternal = { path ->
    logger?.debug { "Fetching MathJax asset: $path" }
    val baseUrl = "https://cdn.jsdelivr.net/npm"
    val segments = path.split('/').ifEmpty { error("Invalid path: $path") }
    val (name, file) = if (segments.first().startsWith("@")) {
        segments.take(2).joinToString("/") to segments.drop(2).joinToString("/")
    } else {
        segments.first() to segments.drop(1).joinToString("/")
    }
    logger?.debug { "Resolved to name=$name, file=$file" }
    val version = assets.open("mathjax/version").bufferedReader()
        .use { it.readText().trim() }
    logger?.debug { "Using MathJax version: $version" }
    withContext(Dispatchers.IO) { client.get("$baseUrl/$name@$version/$file") }
        .bodyAsText()
        .also { logger?.debug { "Fetched ${it.take(30)}" } }
}
