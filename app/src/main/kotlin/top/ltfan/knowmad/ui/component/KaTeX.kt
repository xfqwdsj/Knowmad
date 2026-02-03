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

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewStateWithHTMLFile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import top.ltfan.knowmad.util.Json

@Composable
fun KaTeX(
    expression: String,
    modifier: Modifier = Modifier,
    display: Boolean = false,
    onSize: (width: Dp, height: Dp) -> Unit = { _, _ -> },
    onError: (String, Throwable?) -> Unit = { _, _ -> },
) {
    val state = rememberWebViewStateWithHTMLFile("katex/index.html", ASSET_RESOURCES).apply {
        webSettings.apply {
            supportZoom = false
            allowFileAccessFromFileURLs = true
            androidWebSettings.apply {
                useWideViewPort = true
                allowFileAccess = true
            }
        }
    }
    val navigator = rememberWebViewNavigator()

    var formulaWidth by remember { mutableStateOf(Dp.Unspecified) }

    WebView(
        state = state,
        navigator = navigator,
        modifier = modifier
            .widthIn(max = formulaWidth)
            .focusable(false),
        captureBackPresses = false,
    )

    LaunchedEffect(state.loadingState) {
        if (state.loadingState is Finished) {
            snapshotFlow { expression }.collect { expression ->
                try {
                    val jsExpression = expression
                        .replace("\\", "\\\\")
                        .replace("`", "\\`")

                    navigator.evaluateJavaScript(
                        """
                            (function() {
                                try {
                                    const size = renderMath(
                                        `${jsExpression}`,
                                        {
                                            displayMode: $display,
                                        },
                                    );
                                    return { success: true, size };
                                } catch (err) {
                                    return { success: false, error: err.message };
                                }
                            })();
                    """.trimIndent(),
                    ) {
                        val obj = Json.decodeFromString<JsonObject>(it)
                        val success = (obj["success"] as? JsonPrimitive)?.booleanOrNull ?: false
                        if (success) {
                            val sizeObj = obj["size"] as? JsonObject ?: return@evaluateJavaScript
                            val width = (sizeObj["width"] as? JsonPrimitive)?.floatOrNull?.dp
                                ?: return@evaluateJavaScript
                            val height = (sizeObj["height"] as? JsonPrimitive)?.floatOrNull?.dp
                                ?: return@evaluateJavaScript
                            formulaWidth = width
                            onSize(width, height)
                        } else {
                            val errorMsg =
                                (obj["error"] as? JsonPrimitive)?.contentOrNull ?: "Unknown error"
                            onError(errorMsg, null)
                        }
                    }
                } catch (e: Throwable) {
                    onError(e.localizedMessage ?: "Unknown error", e)
                }
            }
        }
    }
}
