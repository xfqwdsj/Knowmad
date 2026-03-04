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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import top.ltfan.knowmad.accessibility.retake
import top.ltfan.knowmad.accessibility.use
import top.ltfan.knowmad.util.HandlerEvent
import top.ltfan.knowmad.util.Logger
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@SuppressLint("AccessibilityPolicy")
class SemanticAnalysisService : AccessibilityService(), CoroutineScope {
    private val logger = Logger("SemanticAnalysisService")

    private var cacheTree: Triple<Int, Node, Instant>? = null

    private val serviceInfoMutex = Mutex()

    private val job = SupervisorJob().apply {
        invokeOnCompletion { e ->
            when (e) {
                null -> logger.error { "SemanticAnalysisService CoroutineScope unexpectedly completed without error" }
                is CancellationException -> logger.debug { "SemanticAnalysisService CoroutineScope cancelled" }
                else -> logger.error(e) { "SemanticAnalysisService CoroutineScope unexpectedly completed with error" }
            }
            stopSelf()
        }
    }
    override val coroutineContext = Dispatchers.Default + job

    override fun onServiceConnected() {
        super.onServiceConnected()
        launch {
            logger.debug { "SemanticAnalysisService connected, initializing..." }
            suspend()
        }
        launch {
            flow.collect { event ->
                if (event is Suspend) {
                    launch {
                        suspend()
                        event(Unit)
                    }
                    return@collect
                }
                when (event) {
                    is Heartbeat -> event(true)
                    is GetUiTree -> launch {
                        serviceInfoMutex.withLock {
                            doResumeUnsafe()

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

                            try {
                                withTimeoutOrNull(1.minutes) {
                                    var tries = 1

                                    w@ while (true) {
                                        rootInActiveWindow?.use { root ->
                                            val node = root.parse()
                                            if (node != null) {
                                                logger.debug { "Successfully retrieved UI tree after $tries tries" }
                                                event(node.withRoot(root))
                                                break@w
                                            } else {
                                                val (id, cache, updated) = cacheTree ?: continue@w
                                                val now = Clock.System.now()
                                                if (now - updated > CacheUseThreshold) continue@w
                                                if (root.windowId != id) continue@w
                                                logger.debug { "Using cached UI tree, try #$tries" }
                                                event(cache)
                                                break@w
                                            }
                                        }
                                            ?: logger.debug { "Failed to retrieve root window, try #$tries" }
                                        tries++
                                        delay(100.milliseconds)
                                    }
                                }
                            } catch (e: Throwable) {
                                logger.error(e) { "Failed to retrieve UI tree" }
                            } finally {
                                serviceInfo = originalInfo
                            }
                        }
                        event(null)
                    }
                }
            }
        }
        launch {
            suspendSignalFlow.collect {
                suspend()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> updateCache(event)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> updateCache(event)
            else -> {}
        }
    }

    private val updateCacheJobMutex = Mutex()
    private var updateCacheJob: Job? = null

    private fun updateCache(event: AccessibilityEvent) {
        val now = Clock.System.now()

        val info = event.source?.retake() ?: return

        launch {
            try {
                rootInActiveWindow?.use { root ->
                    info.use { info ->
                        if (info.windowId != root.windowId) return@launch
                        cacheTree?.third?.let { lastUpdate -> if (lastUpdate > now) return@launch }
                        val node = info.findAndParseWithRoot()
                        cacheTree?.third?.let { lastUpdate -> if (lastUpdate > now) return@launch }
                        if (node != null) {
                            updateCacheJobMutex.withLock {
                                val info = info.retake()
                                updateCacheJob?.cancel()
                                updateCacheJob = this@SemanticAnalysisService.launch {
                                    info.use { info ->
                                        val currentRootId = rootInActiveWindow?.use { it.windowId }
                                            ?: return@launch
                                        if (currentRootId != info.windowId) return@launch
                                        cacheTree?.third?.let { lastUpdate -> if (lastUpdate > now) return@launch }
                                        cacheTree = Triple(info.windowId, node, now)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                logger.error(e) { "Failed to update UI tree cache" }
            }
        }
    }

    override fun onInterrupt() {}

    private suspend fun suspend() {
        serviceInfoMutex.withLock {
            val originalInfo = serviceInfo ?: return

            serviceInfo = AccessibilityServiceInfo().apply {
                eventTypes = 0
                notificationTimeout = originalInfo.notificationTimeout
                flags = originalInfo.flags
                feedbackType = 0
            }

            logger.debug { "Service suspended, waiting for resume signal..." }
        }
    }

    private suspend fun resume() {
        serviceInfoMutex.withLock { doResumeUnsafe() }
    }

    private fun doResumeUnsafe() {
        val originalInfo = serviceInfo ?: return

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = EVENT_TYPES
            notificationTimeout = originalInfo.notificationTimeout
            flags = originalInfo.flags
            feedbackType = FEEDBACK_TYPE
        }

        logger.debug { "Service resumed, waiting for events..." }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    companion object {
        suspend fun waitAlive() = flow.subscriptionCount.first { it > 0 }

        fun notifySuspend(): Boolean {
            if (suspendSignalFlow.subscriptionCount.value <= 0) return false
            return suspendSignalFlow.tryEmit(Unit)
        }

        suspend fun suspend() = execute(Suspend())

        suspend fun heartbeat() = execute(Heartbeat()) == true

        suspend fun getUiTree() = execute(GetUiTree())

        private val flow = MutableSharedFlow<Event<*>>()
        private val suspendSignalFlow = MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

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
                        safeResume(result)
                        monitorJob.cancel()
                    }

                    try {
                        flow.emit(event)
                    } catch (_: CancellationException) {
                        safeResume(null)
                    }
                }

                continuation.invokeOnCancellation {
                    job.cancel()
                }
            }
        }

        val CacheUseThreshold = 1.seconds
        const val EVENT_TYPES = AccessibilityEvent.TYPES_ALL_MASK
        const val FEEDBACK_TYPE = AccessibilityServiceInfo.FEEDBACK_GENERIC
    }

    private fun AccessibilityNodeInfo.findAndParseWithRoot(): Node? {
        var current: AccessibilityNodeInfo = this
        var lastChild: AccessibilityNodeInfo? = null

        while (true) {
            val parent = current.parent ?: break
            lastChild = current
            current = parent
        }

        val childrenParam = if (current.childCount == 0 && lastChild != null) {
            listOf(lastChild)
        } else {
            null
        }

        val parsedNode = current.parse(children = childrenParam)
        return parsedNode?.withRoot(current)
    }

    private fun Node.withRoot(root: AccessibilityNodeInfo): Node {
        val title = root.window?.title?.toString()
            ?: root.packageName?.toString()
            ?: "Unknown App"
        return copy(
            name = title,
            children = listOf(this),
        )
    }

    private fun AccessibilityNodeInfo.parse(
        children: List<AccessibilityNodeInfo>? = null,
    ): Node? {
        if (!isVisibleToUser) return null

        data class NodeSnapshot(
            val info: AccessibilityNodeInfo,
            val node: Node,
            val parent: NodeSnapshot? = null,
        )

        val traversalOrder = mutableListOf<NodeSnapshot>()
        val queue = ArrayDeque<NodeSnapshot>()

        fun addChildToQueue(
            childInfo: AccessibilityNodeInfo,
            parentSnapshot: NodeSnapshot,
            queue: ArrayDeque<NodeSnapshot>,
        ) {
            val rect = Rect()
            childInfo.getBoundsInScreen(rect)
            if (!childInfo.isVisibleToUser || rect.isEmpty) {
                @Suppress("DEPRECATION")
                childInfo.recycle()
                return
            }

            val childSnapshot = NodeSnapshot(childInfo, childInfo.toNode(), parentSnapshot)
            queue += childSnapshot
        }

        val rootSnapshot = NodeSnapshot(this, this.toNode(), null)
        traversalOrder += rootSnapshot

        if (!children.isNullOrEmpty()) {
            for (childInfo in children) {
                addChildToQueue(childInfo, rootSnapshot, queue)
            }
        } else {
            for (i in 0 until childCount) {
                val childInfo = getChild(i) ?: continue
                addChildToQueue(childInfo, rootSnapshot, queue)
            }
        }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            traversalOrder += current

            for (i in 0 until current.info.childCount) {
                val childInfo = current.info.getChild(i) ?: continue
                addChildToQueue(childInfo, current, queue)
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

private sealed class Event<R> : HandlerEvent<R, Event<R>>()

private class Suspend : Event<Unit>()
private class Heartbeat : Event<Boolean>()
private class GetUiTree : Event<Node>()
