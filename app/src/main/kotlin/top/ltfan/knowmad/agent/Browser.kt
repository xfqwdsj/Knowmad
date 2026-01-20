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

package top.ltfan.knowmad.agent

import com.multiplatform.webview.web.WebViewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface BrowserState {
    data object NotStarted : BrowserState
    data class Running(val sessionId: String, val currentUrl: String?) : BrowserState
    data object Closed : BrowserState
}
/*
class BrowserSessionManager {
    private val mutex = Mutex()
    private val _state: MutableStateFlow<BrowserState> = MutableStateFlow(BrowserState.NotStarted)

    val state: BrowserState get() = _state.value
    val isRunning: Boolean get() = _state.value is BrowserState.Running

    // 你的 WebView 实例引用
    private var webViewState: WebViewState? = null

    suspend fun startBrowser(): Result<String> = mutex.withLock {
        if (_state.value is BrowserState.Running) {
            return@withLock Result.failure(IllegalStateException("浏览器已在运行中"))
        }

        return@withLock try {
            // 初始化你的 WebView
            webView = YourWebView().also { it.initialize() }
            val sessionId = generateSessionId()
            _state.value = BrowserState.Running(sessionId, null)
            Result.success(sessionId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun closeBrowser(): Result<Unit> = mutex.withLock {
        val currentState = _state.value
        if (currentState !is BrowserState.Running) {
            return@withLock Result.failure(IllegalStateException("浏览器未运行"))
        }

        return@withLock try {
            webView?.close()
            webView = null
            _state = BrowserState.Closed
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun <T> withBrowser(action: suspend (YourWebView) -> T): Result<T> = mutex.withLock {
        val view = webView ?: return@withLock Result.failure(
            IllegalStateException("浏览器未启动，请先调用 start_browser 工具"),
        )
        return@withLock try {
            Result.success(action(view))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCurrentUrl(url: String) = mutex.withLock {
        val currentState = _state
        if (currentState is BrowserState.Running) {
            _state = currentState.copy(currentUrl = url)
        }
    }

    private fun generateSessionId() = "browser-${System.currentTimeMillis()}"
}
*/
