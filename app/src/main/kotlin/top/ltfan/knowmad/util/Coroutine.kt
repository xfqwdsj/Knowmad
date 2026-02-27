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

class StopException(
    message: String? = null,
    cause: CancellationException? = null,
) : CancellationException(message) {
    init {
        cause?.let { initCause(it) }
    }
}

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

interface InterruptibleTask : CoroutineScope {
    fun stop(cause: StopException = StopException())
}

open class JobInterruptibleTask(
    val job: Job,
    taskContext: CoroutineContext,
    val rootScope: CoroutineScope,
) : InterruptibleTask, CoroutineContext.Element {
    override val coroutineContext = taskContext + job
    override fun stop(cause: StopException) = job.cancel(cause)

    @Suppress("CoroutineContextWithJob")
    suspend fun <R> bind(block: suspend CoroutineScope.() -> R) =
        withContext(coroutineContext, block)

    override val key: CoroutineContext.Key<*> = JobInterruptibleTask

    companion object Key : CoroutineContext.Key<JobInterruptibleTask>
}

@OptIn(InternalForInheritanceCoroutinesApi::class)
class InterruptibleDeferred<T>(
    deferred: Deferred<T>,
    task: InterruptibleTask,
) : Deferred<T> by deferred, InterruptibleTask by task

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
            withContext(task) {
                task.block()
            }
        } finally {
            job.complete()
        }
    }

    return InterruptibleDeferred(deferred, task)
}
