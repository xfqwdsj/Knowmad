/*
 * Knowmad - Knowledge nomad
 * Copyright (C) 2025-2026 LTFan (aka xfqwdsj)
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

package top.ltfan.knowmad.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalForInheritanceCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

val HashComputationDispatcher = Dispatchers.Default.limitedParallelism(
    Runtime.getRuntime().availableProcessors(),
)

/**
 * A [CancellationException] subtype that signals an **intentional,
 * caller-initiated** stop, as opposed to an incidental
 * cancellation (e.g. timeout or parent-scope cancellation).
 *
 * Throwing [StopException] instead of a plain [CancellationException]
 * lets catch-sites distinguish between "someone explicitly
 * asked to stop" and "the coroutine was cancelled for
 * an unrelated reason", using [stopExceptionOrNull].
 *
 * @param message An optional human-readable description of why the task
 *    was stopped.
 * @param cause An optional upstream [CancellationException] to attach as
 *    the cause. Because [CancellationException] does not expose a
 *    two-argument constructor on all JVM targets, the cause is installed
 *    via [Throwable.initCause] in the `init` block.
 */
class StopException(
    message: String? = null,
    cause: CancellationException? = null,
) : CancellationException(message) {
    init {
        cause?.let { initCause(it) }
    }
}

/**
 * Walks the [Throwable.cause] chain of this [CancellationException] and
 * returns the first [StopException] found, or `null` if none exists.
 *
 * Coroutine machinery (e.g. `withContext`, `Deferred.await`) often
 * wraps exceptions before re-throwing them, so a [StopException] raised
 * deep inside a coroutine may be wrapped inside one or more outer
 * [CancellationException]s by the time it surfaces. This property unwraps
 * the entire chain so callers can reliably detect an intentional stop
 * regardless of wrapping depth.
 */
val CancellationException.stopExceptionOrNull: StopException?
    inline get() {
        if (this is StopException) return this
        var cause = cause
        while (cause != null) {
            if (cause is StopException) return cause
            cause = cause.cause
        }
        return null
    }

/**
 * A [CoroutineScope] that can be explicitly stopped by the caller.
 *
 * Implementations combine the ability to launch coroutines (via the
 * [CoroutineScope] contract) with a [stop] method that cancels those
 * coroutines using a [StopException], allowing downstream catch-sites
 * to distinguish an intentional stop from an incidental cancellation.
 */
interface InterruptibleTask : CoroutineScope {
    fun stop(cause: StopException = StopException())
}

/**
 * A [Job]-backed [InterruptibleTask] that is also a
 * [CoroutineContext.Element], allowing it to be propagated
 * through and retrieved from a coroutine context.
 *
 * ## Coroutine context composition
 *
 * The [coroutineContext] of this task is `taskContext + job`. Coroutines
 * launched directly on `this` (as a [CoroutineScope]) therefore
 * become children of [job] and are canceled when [stop] is called.
 *
 * ## Being a context element
 *
 * Because [JobInterruptibleTask] is a [CoroutineContext.Element]
 * keyed by its [companion object][Key], instances can be
 * stored inside a coroutine context and retrieved later with
 * `coroutineContext[JobInterruptibleTask]`. [asyncInterruptible]
 * exploits this to locate a parent task when nesting calls.
 *
 * ## ⚠️ Being in the lambda does not mean being inside the task's Job tree
 *
 * When [asyncInterruptible] launches the work coroutine, the execution
 * model is:
 * ```
 * rootScope.async {          // actual coroutine Job is a child of rootScope
 *     withContext(task) {    // injects `task` as a CoroutineContext.Element only;
 *         task.block()       // `this` == task, but the coroutine's Job is still rootScope's child
 *     }
 * }
 * ```
 *
 * `withContext(task)` merges `task` (a [CoroutineContext.Element])
 * into the context but does **not** replace the running coroutine's
 * [Job] with [job]. As a result, code executed directly inside the
 * [asyncInterruptible] lambda is **not** a child of [job] and will **not**
 * be canceled by [stop].
 *
 * To execute code that is genuinely cancellable by [stop], wrap it in
 * [bind]:
 * ```kotlin
 * asyncInterruptible {
 *     // Code here runs under rootScope's Job — stop() has no effect on it.
 *
 *     bind {
 *         // Code here runs under task.job — stop() will cancel it.
 *     }
 * }
 * ```
 *
 * @param job The [Job] that represents this task's lifetime. Calling
 *    [stop] cancels this job. Typically a [SupervisorJob] so that
 *    individual child failures do not propagate upward.
 * @param taskContext The base coroutine context (without [job]) that is
 *    combined with [job] to form [coroutineContext]. Usually inherited
 *    from the parent task or the creating scope.
 * @param rootScope The scope used by [asyncInterruptible] to launch the
 *    actual work coroutine. Stored here so that nested
 *    [asyncInterruptible] calls can reuse the same root.
 */
open class JobInterruptibleTask(
    val job: Job,
    taskContext: CoroutineContext,
    val rootScope: CoroutineScope,
) : InterruptibleTask, CoroutineContext.Element {
    override val coroutineContext by lazy { taskContext + this + job }
    override fun stop(cause: StopException) = job.cancel(cause)

    /**
     * Executes [block] under this task's full [coroutineContext] (including
     * [job]), making the suspended code a genuine child of [job] and therefore
     * cancellable by [stop].
     *
     * This is the correct way to enter the task's Job tree from inside an
     * [asyncInterruptible] lambda. Without calling [bind], suspended code
     * inside the lambda runs under the root scope's coroutine Job and is
     * unaffected by [stop].
     */
    @Suppress("CoroutineContextWithJob")
    suspend fun <R> bind(block: suspend CoroutineScope.() -> R) =
        withContext(coroutineContext, block)

    override val key: CoroutineContext.Key<*> = JobInterruptibleTask

    companion object Key : CoroutineContext.Key<JobInterruptibleTask>
}

/**
 * A value that is both a [Deferred] result and an [InterruptibleTask],
 * implemented via interface delegation.
 *
 * Callers can `await()` the eventual result and, at any point before
 * completion, call `stop()` to cancel the underlying task with a
 * [StopException].
 *
 * @param T The type of the deferred result.
 * @param deferred The [Deferred] whose interface is delegated to.
 * @param task The [InterruptibleTask] whose interface is delegated to.
 */
@OptIn(InternalForInheritanceCoroutinesApi::class)
class InterruptibleDeferred<T>(
    deferred: Deferred<T>,
    task: InterruptibleTask,
) : Deferred<T> by deferred, InterruptibleTask by task

/**
 * Launches an asynchronous, stoppable task on this [CoroutineScope] and
 * returns an [InterruptibleDeferred] that can both `await()` the result
 * and `stop()` the task early.
 *
 * ## Execution model
 *
 * Internally, the function creates a [SupervisorJob] and a
 * [JobInterruptibleTask], then launches the actual coroutine on
 * [rootScope][JobInterruptibleTask.rootScope] using [async]:
 * ```
 * rootScope.async {
 *     withContext(task) {   // inject task as a CoroutineContext.Element
 *         task.block()
 *     }
 * }
 * ```
 *
 * `withContext(task)` makes `task` accessible via
 * `coroutineContext[JobInterruptibleTask]` inside the lambda, but
 * the **running coroutine's Job** remains a child of `rootScope`,
 * not of `task.job`. Therefore, code executed directly in the lambda
 * is not canceled by [InterruptibleTask.stop]. Only code inside
 * a [JobInterruptibleTask.bind] call truly runs under `task.job`
 * and is subject to cancellation via [InterruptibleTask.stop].
 *
 * ## SupervisorJob completion
 *
 * A [SupervisorJob] does not complete automatically when it has no more
 * children. The `finally` block inside the launched coroutine calls
 * `job.complete()` to ensure the [Job] is finished when the coroutine ends
 * (normally or exceptionally), preventing resource leaks in the Job tree.
 *
 * ## Nested tasks (`appendToParent = true`)
 *
 * When `appendToParent` is `true` (the default) and the current coroutine
 * context already contains a [JobInterruptibleTask] element (injected by
 * an outer [asyncInterruptible] call), the new task inherits the parent
 * task's context and root scope:
 * - The new [SupervisorJob] is created as a child of the parent task's
 *   [job][JobInterruptibleTask.job], so stopping the parent also stops the
 *   child.
 * - The same [rootScope][JobInterruptibleTask.rootScope] is reused to
 *   launch the coroutine.
 *
 * When no parent task is found, the current scope's context and `this` are
 * used as the defaults.
 *
 * @param appendToParent If `true`, attempts to attach the new task to an
 *    existing [JobInterruptibleTask] found in the current coroutine
 *    context, enabling hierarchical cancellation. Defaults to `true`.
 * @param block The task body. The receiver is the [JobInterruptibleTask]
 *    for this invocation. Code that must be cancellable by
 *    [InterruptibleTask.stop] must be wrapped in
 *    [JobInterruptibleTask.bind].
 * @return An [InterruptibleDeferred] providing both the deferred result
 *    and the ability to stop the task.
 */
inline fun <T> CoroutineScope.asyncInterruptible(
    appendToParent: Boolean = true,
    crossinline block: suspend JobInterruptibleTask.() -> T,
): InterruptibleDeferred<T> {
    val (coroutineContext, rootScope) = appendToParent.takeIf { it }?.let {
        coroutineContext[JobInterruptibleTask]?.let { parentTask ->
            parentTask.coroutineContext to parentTask.rootScope
        }
    } ?: (coroutineContext to this)

    val job = SupervisorJob(coroutineContext.job)
    val task = JobInterruptibleTask(job, coroutineContext, rootScope)

    val deferred = rootScope.async {
        try {
            task.block()
        } finally {
            job.complete()
        }
    }

    return InterruptibleDeferred(deferred, task)
}
