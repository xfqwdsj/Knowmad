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

package top.ltfan.knowmad.accessibility.semantic

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

@SuppressLint("AccessibilityPolicy")
class SemanticAnalysisService : AccessibilityService(), CoroutineScope {
    private val job = Job()
    override val coroutineContext = Dispatchers.Default + job

    override fun onServiceConnected() {
        super.onServiceConnected()
        launch {
            flow.collect { (event, onResult) ->
                when (event) {
                    is GetUiTree -> {
                        val rootNode = CloseableAccessibilityNodeInfo(rootInActiveWindow)
                        onResult(rootNode)
                    }
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    companion object {
        private val flow = MutableSharedFlow<EventWithCallback<*>>()

        suspend fun getUiTree() = execute(GetUiTree)

        private suspend inline fun <reified R> execute(event: Event<R>): R? = coroutineScope {
            if (flow.subscriptionCount.value <= 0) return@coroutineScope null

            suspendCancellableCoroutine { continuation ->
                val isResumed = AtomicBoolean(false)

                val safeResume: (R?) -> Unit = { result ->
                    if (isResumed.compareAndSet(expectedValue = false, newValue = true)) {
                        continuation.resume(result)
                    }
                }

                val job = launch job@{
                    val monitorJob = launch {
                        flow.subscriptionCount.collect { count ->
                            if (count <= 0) {
                                safeResume(null)
                                this@job.cancel()
                            }
                        }
                    }

                    try {
                        flow.emit(
                            event { result ->
                                if (result is R) {
                                    safeResume(result)
                                } else {
                                    safeResume(null)
                                }
                                monitorJob.cancel()
                            },
                        )
                    } catch (e: CancellationException) {
                        safeResume(null)
                    }
                }

                continuation.invokeOnCancellation {
                    job.cancel()
                }
            }
        }
    }
}
