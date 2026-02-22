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
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import top.ltfan.knowmad.util.Logger
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@SuppressLint("AccessibilityPolicy")
class SemanticAnalysisService : AccessibilityService(), CoroutineScope {
    private val logger = Logger("SemanticAnalysisService")

    private val job = Job()
    override val coroutineContext = Dispatchers.Default + job

    override fun onServiceConnected() {
        super.onServiceConnected()
        launch {
            flow.collect { event ->
                when (event) {
                    is Heartbeat -> event(true)
                    is GetUiTree -> launch {
                        val originalInfo = serviceInfo ?: return@launch event(null)

                        val elevatedInfo = AccessibilityServiceInfo().apply {
                            eventTypes = originalInfo.eventTypes
                            notificationTimeout = originalInfo.notificationTimeout
                            flags = originalInfo.flags

                            feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN
                            flags =
                                flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
                        }

                        serviceInfo = elevatedInfo

                        withTimeoutOrNull(1.minutes) {
                            var tries = 1

                            while (true) {
                                val root = rootInActiveWindow
                                val node = root?.parse()
                                if (node != null) {
                                    logger.debug { "Successfully retrieved UI tree after $tries tries" }
                                    val title = root.window?.title?.toString()
                                        ?: root.packageName?.toString()
                                        ?: "Unknown App"
                                    val rootNode = Node(
                                        name = title,
                                        children = listOf(node),
                                    )
                                    event(rootNode)
                                    @Suppress("DEPRECATION")
                                    root.recycle()
                                    break
                                }
                                @Suppress("DEPRECATION")
                                root?.recycle()
                                tries++
                                delay(100.milliseconds)
                            }
                        }

                        serviceInfo = originalInfo
                        event(null)
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
        suspend fun waitAlive() = flow.subscriptionCount.first { it > 0 }

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

    private fun AccessibilityNodeInfo.parse(): Node? {
        data class NodeSnapshot(
            val info: AccessibilityNodeInfo,
            val node: Node,
            val parent: NodeSnapshot? = null,
        )

        val traversalOrder = mutableListOf<NodeSnapshot>()
        val queue = ArrayDeque<NodeSnapshot>()

        if (!isVisibleToUser) return null
        val rootSnapshot = NodeSnapshot(this, this.toNode(), null)
        queue.add(rootSnapshot)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            traversalOrder.add(current)

            for (i in 0 until current.info.childCount) {
                val childInfo = current.info.getChild(i) ?: continue

                val rect = Rect()
                childInfo.getBoundsInScreen(rect)
                if (!childInfo.isVisibleToUser || rect.isEmpty) {
                    @Suppress("DEPRECATION")
                    childInfo.recycle()
                    continue
                }

                val childSnapshot = NodeSnapshot(childInfo, childInfo.toNode(), current)
                queue.add(childSnapshot)
            }
        }

        var finalRoot: Node? = rootSnapshot.node

        for (i in traversalOrder.indices.reversed()) {
            val current = traversalOrder[i]
            val currentNode = current.node
            val info = current.info

            val isMeaningless = currentNode.text.isNullOrBlank() &&
                    currentNode.contentDescription.isNullOrBlank() &&
                    !currentNode.isClickable &&
                    !currentNode.isFocusable

            val children = currentNode.children as MutableList<Node>
            var replacement: Node? = currentNode

            if (isMeaningless) {
                if (children.isEmpty()) {
                    replacement = null
                } else if (children.size == 1) {
                    val onlyChild = children[0]
                    replacement =
                        if (!currentNode.id.isNullOrBlank() && onlyChild.id.isNullOrBlank()) {
                            onlyChild.copy(id = currentNode.id)
                        } else {
                            onlyChild
                        }
                }
            }

            val parentSnapshot = current.parent
            if (parentSnapshot != null) {
                if (replacement != null) {
                    (parentSnapshot.node.children as MutableList<Node>).add(replacement)
                }
            } else {
                finalRoot = replacement
            }

            if (info != this) {
                @Suppress("DEPRECATION")
                info.recycle()
            }
        }

        return finalRoot
    }

    private fun AccessibilityNodeInfo.toNode() = Node(
        id = viewIdResourceName,
        name = className?.toString(),
        contentDescription = contentDescription?.toString(),
        text = text?.toString(),
        isClickable = isClickable,
        isFocusable = isFocusable,
        isVisibleToUser = isVisibleToUser,
        children = mutableListOf(),
    )
}
