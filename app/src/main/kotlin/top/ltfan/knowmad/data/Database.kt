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

package top.ltfan.knowmad.data

import android.content.Context
import androidx.room.RoomDatabase

interface DatabaseCompanion<T : RoomDatabase> {
    val databaseName: String

    context(context: Context)
    fun get(): T
}

interface FtsDao {
    /**
     * SQLite FTS uses double quotes to wrap phrases and operators like AND,
     * OR, NOT.
     *
     * Raw user input might contain special characters that cause syntax
     * errors.
     *
     * We sanitize the input by:
     * 1. Removing double quotes to prevent syntax errors.
     * 2. Splitting by whitespace.
     * 3. Appending '*' to each token for prefix matching (search-as-you-type
     *    behavior).
     */
    fun String.sanitizeForFts() = replace("\"", "")
        .split(Regex("\\s+"))
        .asSequence()
        .filter { it.isNotBlank() }
        .joinToString(" ") { "$it*" }
}
