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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import top.ltfan.knowmad.agent.task.suggestion.GenerateNextSuggestionWorker
import top.ltfan.knowmad.util.Logger

class SuggestionRequestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val context = context.applicationContext
        context.scheduleNextSuggestionGeneration()
        val request = OneTimeWorkRequestBuilder<GenerateNextSuggestionWorker>().apply {
            setExpedited(RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }.build()
        WorkManager.getInstance(context).enqueue(request)
    }

    companion object {
        private val logger = Logger("SuggestionRequestReceiver")
    }
}
