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
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import top.ltfan.knowmad.agent.task.suggestion.GenerateNextSuggestionWorker
import top.ltfan.knowmad.notification.SuggestionRequestReceiver.Companion.resolvePromptForGeneration
import top.ltfan.knowmad.notification.SuggestionRequestReceiver.Companion.scheduleNextSuggestionGeneration
import top.ltfan.knowmad.util.Logger

class SuggestionRequestWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val context = applicationContext
        context.scheduleNextSuggestionGeneration(override = false)

        val intent = Intent(context, SuggestionRequestReceiver::class.java).apply {
            action = inputData.getString(SuggestionRequestReceiver.DATA_ACTION)
            putExtra(
                SuggestionRequestReceiver.EXTRA_PROMPT,
                inputData.getString(SuggestionRequestReceiver.DATA_PROMPT),
            )
            putExtra(
                SuggestionRequestReceiver.EXTRA_PENDING_SUGGESTION_ID,
                inputData.getString(SuggestionRequestReceiver.DATA_PENDING_SUGGESTION_ID),
            )
        }

        val prompt = context.resolvePromptForGeneration(intent) ?: run {
            logger.warn { "Skipping suggestion generation because no valid pending suggestion was found" }
            return Result.success()
        }

        val request = GenerateNextSuggestionWorker.buildRequest(prompt)
        logger.debug { "Enqueuing suggestion generation work with prompt: $prompt" }
        WorkManager.getInstance(context).enqueue(request)

        Result.success()
    } catch (e: Throwable) {
        logger.error(e) { "Failed to handle suggestion request" }
        Result.failure()
    }

    companion object {
        private val logger = Logger("SuggestionRequestWorker")
    }
}
