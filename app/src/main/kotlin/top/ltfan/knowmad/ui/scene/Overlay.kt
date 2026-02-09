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

package top.ltfan.knowmad.ui.scene

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.util.fastForEachReversed
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.ui.NavDisplay

class OverlayContentScene<T : Any>(
    override val key: Any,
    override val entries: List<NavEntry<T>>,
    override val previousEntries: List<NavEntry<T>>,
) : Scene<T> {
    override val content: @Composable () -> Unit = {
        entries.fastForEachReversed { it.Content() }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OverlayContentScene<*>

        if (key != other.key) return false
        if (entries != other.entries) return false
        if (previousEntries != other.previousEntries) return false
        if (content != other.content) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + entries.hashCode()
        result = 31 * result + previousEntries.hashCode()
        result = 31 * result + content.hashCode()
        return result
    }

    override fun toString(): String {
        return "OverlayContentScene(key=$key entries=$entries, previousEntries=$previousEntries, content=$content)"
    }
}

class OverlayContentSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(
        entries: List<NavEntry<T>>,
    ): Scene<T>? {
        val takenEntries = entries.takeLastWhile {
            it.metadata[KEY] as? Boolean ?: false
        }.toMutableList()
        if (takenEntries.isEmpty()) return null
        val key = takenEntries.last().contentKey
        entries.getOrNull(entries.size - takenEntries.size - 1)?.let { overlaidEntry ->
            takenEntries += overlaidEntry
        }

        return OverlayContentScene(
            key = key,
            entries = takenEntries,
            previousEntries = entries.dropLast(1),
        )
    }

    companion object {
        fun overlayContent(): Map<String, Any> = mapOf(KEY to true) +
                NavDisplay.transitionSpec { fadeIn() togetherWith fadeOut() } +
                NavDisplay.popTransitionSpec { fadeIn() togetherWith fadeOut() } +
                NavDisplay.predictivePopTransitionSpec { fadeIn() togetherWith fadeOut() }

        private const val KEY = "overlay-content"
    }
}
