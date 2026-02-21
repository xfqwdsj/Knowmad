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
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
            flow.collect { event ->
                when (event) {
                    is Heartbeat -> event(true)
                    is GetUiTree -> {
                        val root = rootInActiveWindow
                        event(rootInActiveWindow?.parse())
                        @Suppress("DEPRECATION")
                        root?.recycle()
                    }
                }
            }
        }
    }

    private fun AccessibilityNodeInfo.parse(): Node? {
        if (!isVisibleToUser) return null

        data class TraversalPair(
            val info: AccessibilityNodeInfo,
            val node: Node,
            val parent: Node? = null,
            var processedChildren: Boolean = false,
        )

        val stack = ArrayDeque<TraversalPair>()
        val rootNode = toNode()

        var finalRoot: Node? = rootNode
        stack.addFirst(TraversalPair(this, rootNode, null))

        while (stack.isNotEmpty()) {
            val current = stack.first()

            if (!current.processedChildren) {
                val childCount = current.info.childCount
                val tempChildren = mutableListOf<Node>()

                for (i in 0 until childCount) {
                    val childInfo = current.info.getChild(i) ?: continue

                    if (childInfo.shouldSkip) {
                        @Suppress("DEPRECATION")
                        childInfo.recycle()
                        continue
                    }

                    val childNode = childInfo.toNode()
                    tempChildren.add(childNode)
                    stack.addFirst(TraversalPair(childInfo, childNode, current.node))
                }

                (current.node.children as? MutableList)?.addAll(tempChildren)
                current.processedChildren = true
            } else {
                val completed = stack.removeFirst()

                if (completed.info.shouldSimplify) {
                    val currentList = completed.node.children as? MutableList
                    if (currentList?.size == 1) {
                        val onlyChild = currentList[0]

                        if (onlyChild.id.isNullOrBlank() && !completed.node.id.isNullOrBlank()) {
                            currentList[0] = onlyChild.copy(id = completed.node.id)
                        }

                        val promotedChild = currentList[0]
                        val parentNode = completed.parent

                        if (parentNode != null) {
                            val siblings = parentNode.children as MutableList
                            val index = siblings.indexOf(completed.node)
                            if (index != -1) {
                                siblings[index] = promotedChild
                            }
                        } else {
                            finalRoot = promotedChild
                        }
                    }
                }

                if (completed.info != this) {
                    @Suppress("DEPRECATION")
                    completed.info.recycle()
                }
            }
        }

        return finalRoot
    }

    private fun AccessibilityNodeInfo.toNode() = Node(
        id = viewIdResourceName,
        className = className?.toString(),
        contentDescription = contentDescription?.toString(),
        text = text?.toString(),
        isClickable = isClickable,
        isFocusable = isFocusable,
        isVisibleToUser = isVisibleToUser,
        children = mutableListOf(),
    )

    private val AccessibilityNodeInfo.shouldSkip: Boolean
        get() {
            if (!isVisibleToUser) return true

            val rect = Rect()
            getBoundsInScreen(rect)
            if (rect.isEmpty) return true
            if (childCount > 0) return false
            if (text.isNullOrBlank() && contentDescription.isNullOrBlank() && !isClickable) return true
            return false
        }

    private val AccessibilityNodeInfo.shouldSimplify: Boolean
        get() {
            if (childCount != 1) return false
            if (!text.isNullOrBlank()) return false
            if (!contentDescription.isNullOrBlank()) return false
            if (isClickable || isFocusable) return false
            return true
        }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    companion object {
        suspend fun heartbeat() = execute(Heartbeat()) == true

        suspend fun getUiTree() = execute(GetUiTree())

        private val flow = MutableSharedFlow<Event<*>>()
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

                    event.onResult { result ->
                        if (result is R) {
                            safeResume(result)
                        } else {
                            safeResume(null)
                        }
                        monitorJob.cancel()
                    }

                    try {
                        flow.emit(event)
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
