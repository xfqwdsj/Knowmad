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

package top.ltfan.knowmad.sync

import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun Context.requestCalendarSync(
    isManual: Boolean = true,
) {
    withContext(Dispatchers.IO) {
        val account = getOrCreateSyncAccount()
        val bundle = Bundle().apply {
            putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, isManual)
            putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        }
        ContentResolver.requestSync(account, CalendarContract.AUTHORITY, bundle)
    }
}
