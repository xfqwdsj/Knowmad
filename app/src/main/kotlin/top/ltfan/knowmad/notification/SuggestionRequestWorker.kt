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

package top.ltfan.knowmad.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import top.ltfan.knowmad.agent.task.suggestion.generateAndShowNextSuggestion
import top.ltfan.knowmad.notification.SuggestionRequestReceiver.Companion.resolvePromptForGeneration
import top.ltfan.knowmad.notification.SuggestionRequestReceiver.Companion.scheduleNextSuggestionGeneration
import top.ltfan.knowmad.util.Logger

private val logger = Logger("SuggestionRequestWorker")

class SuggestionRequestWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val context = applicationContext

        val data = inputData

        context.scheduleNextSuggestionGeneration(override = false)

        val prompt = context.resolvePromptForGeneration(
            action = data.getString(DATA_ACTION),
            prompt = data.getString(DATA_PROMPT),
            pendingSuggestionId = data.getString(DATA_PENDING_SUGGESTION_ID),
        ) ?: run {
            logger.warn { "Skipping suggestion generation because no valid pending suggestion was found" }
            return Result.success()
        }

        logger.debug { "Enqueuing suggestion generation work with prompt: $prompt" }
        context.generateAndShowNextSuggestion(prompt)

        Result.success()
    } catch (e: Throwable) {
        logger.error(e) { "Failed to handle suggestion request" }
        Result.failure()
    }

    companion object {
        const val DATA_ACTION = "DATA_ACTION"
        const val DATA_PROMPT = "DATA_PROMPT"
        const val DATA_PENDING_SUGGESTION_ID = "DATA_PENDING_SUGGESTION_ID"

        fun buildRequest(
            action: String?,
            prompt: String?,
            pendingSuggestionId: String?,
        ) = OneTimeWorkRequestBuilder<SuggestionRequestWorker>().apply {
            val data = Data.Builder().apply {
                putString(DATA_ACTION, action)
                putString(DATA_PROMPT, prompt)
                putString(DATA_PENDING_SUGGESTION_ID, pendingSuggestionId)
            }.build()
            setInputData(data)
        }.build()
    }
}
