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
import androidx.work.WorkerParameters
import top.ltfan.knowmad.notification.SuggestionRequestReceiver.Companion.scheduleNextSuggestionGeneration
import top.ltfan.knowmad.util.Logger

class SuggestionRestoreWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val context = applicationContext
        val action = inputData.getString(DATA_ACTION)
        logger.debug { "Restoring pending next suggestion schedule for action: $action" }
        context.scheduleNextSuggestionGeneration(override = false)
        Result.success()
    } catch (e: Throwable) {
        logger.error(e) { "Failed to restore next suggestion schedule" }
        Result.failure()
    }

    companion object {
        private val logger = Logger("SuggestionRestoreWorker")

        const val UNIQUE_WORK_NAME = "SuggestionRestoreWorker"

        const val DATA_ACTION = "DATA_ACTION"
    }
}
