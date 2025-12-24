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

package top.ltfan.knowmad.ui.page

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import com.multiplatform.webview.web.WebContent
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.WebViewState
import com.multiplatform.webview.web.rememberWebViewNavigator
import top.ltfan.knowmad.ui.component.WebViewScaffold
import top.ltfan.knowmad.ui.util.AppWindowInsets
import top.ltfan.knowmad.ui.util.plus

class WebPage(
    val state: WebViewState,
    val navigator: WebViewNavigator? = null,
    val urlTextFieldState: TextFieldState = TextFieldState(state.lastLoadedUrl ?: ""),
    val captureBackPresses: Boolean = true,
    val onClose: () -> Unit,
) : Page() {
    constructor(
        url: String,
        captureBackPresses: Boolean = true,
        navigator: WebViewNavigator? = null,
        urlTextFieldState: TextFieldState = TextFieldState(url),
        onClose: () -> Unit,
    ) : this(
        state = WebViewState(WebContent.Url(url)),
        navigator = navigator,
        urlTextFieldState = urlTextFieldState,
        captureBackPresses = captureBackPresses,
        onClose = onClose,
    )

    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        val insets = AppWindowInsets + contentPadding

        val navigator = navigator ?: rememberWebViewNavigator()

        WebViewScaffold(
            state = state,
            navigator = navigator,
            urlTextFieldState = urlTextFieldState,
            captureBackPresses = captureBackPresses,
            windowInsets = insets,
            onClose = onClose,
        )
    }
}
