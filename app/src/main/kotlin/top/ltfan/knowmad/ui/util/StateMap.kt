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

package top.ltfan.knowmad.ui.util

import androidx.compose.runtime.snapshots.SnapshotStateMap

class SnapshotLruCache<K, V>(
    private val snapshotStateMap: SnapshotStateMap<K, V>,
    private val maxSize: Int,
) : MutableMap<K, V> by snapshotStateMap {
    private val lockObject = Any()

    private val tracker = object : LinkedHashMap<K, Unit>(
        (maxSize / 0.75f).toInt() + 1,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Unit>): Boolean {
            val shouldRemove = snapshotStateMap.size > maxSize
            if (shouldRemove) {
                val keyToRemove = eldest.key
                snapshotStateMap.remove(keyToRemove)
            }
            return shouldRemove
        }
    }

    override fun get(key: K): V? {
        return synchronized(lockObject) {
            snapshotStateMap[key].also {
                tracker[key]
            }
        }
    }

    override fun put(key: K, value: V): V? {
        return synchronized(lockObject) {
            snapshotStateMap.put(key, value).also {
                tracker[key] = Unit
            }
        }
    }

    override fun putAll(from: Map<out K, V>) {
        synchronized(lockObject) {
            from.forEach { (key, value) ->
                snapshotStateMap[key] = value
                tracker[key] = Unit
            }
        }
    }

    override fun remove(key: K): V? {
        return synchronized(lockObject) {
            snapshotStateMap.remove(key).also {
                tracker.remove(key)
            }
        }
    }

    override fun clear() {
        synchronized(lockObject) {
            snapshotStateMap.clear()
            tracker.clear()
        }
    }
}
