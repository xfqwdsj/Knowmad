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

package top.ltfan.knowmad.application

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.ltfan.knowmad.notification.SuggestionRequestReceiver.Companion.scheduleNextSuggestionGeneration
import top.ltfan.knowmad.sync.getOrCreateSyncAccount
import top.ltfan.knowmad.util.isMainProcess

class KnowmadApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (isMainProcess) {
            CoroutineScope(Dispatchers.IO).launch {
                getOrCreateSyncAccount()
            }
            scheduleNextSuggestionGeneration()
        }
    }
}
