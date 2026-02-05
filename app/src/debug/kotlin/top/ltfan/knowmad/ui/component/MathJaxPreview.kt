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

import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import io.ktor.client.HttpClient
import top.ltfan.knowmad.ui.theme.AppTheme
import top.ltfan.knowmad.ui.util.AppWindowInsets
import top.ltfan.knowmad.util.Logger

private val logger = Logger("MathJaxPreview")

@Preview
@Composable
fun MathJaxPreview() {
    if (LocalInspectionMode.current) {
        Text("Inspection Mode")
        return
    }

    val client = remember { HttpClient() }

    AppTheme {
        Surface {
            val rendererState = rememberMathJaxRendererState(
                loadExternal = remember(client) { jsDelivrMathJaxLoadExternal(client) },
            )

            if (rendererState is Ready) {
                MathJax(
                    renderer = rendererState.renderer,
                    tex = "\\int_{\\mathbb{R}^n} \\left( \\frac{1}{(2\\pi)^{n/2} |\\mathbf{\\Sigma}|^{1/2}} \n" +
                            "\\exp\\left[ -\\frac{1}{2} (\\mathbf{x} - \\mathbf{\\mu})^\\top \\mathbf{\\Sigma}^{-1} (\\mathbf{x} - \\mathbf{\\mu}) \\right] \\right) \n" +
                            "d\\mathbf{x} \n" +
                            "= 1",
                    modifier = Modifier.windowInsetsPadding(AppWindowInsets),
                    display = true,
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            client.close()
        }
    }
}
