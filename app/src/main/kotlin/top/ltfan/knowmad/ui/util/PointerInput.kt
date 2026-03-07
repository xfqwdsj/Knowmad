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

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

inline fun Modifier.detectPointerFirstDown(
    requireUnconsumed: Boolean = true,
    pass: PointerEventPass = Main,
    crossinline onFirstDown: (firstDown: PointerInputChange) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        onFirstDown(awaitFirstDown(requireUnconsumed, pass))
    }
}

inline fun Modifier.detectLongPress(
    requireUnconsumed: Boolean = true,
    firstDownPass: PointerEventPass = Main,
    upOrCancellationPass: PointerEventPass = Main,
    crossinline onFinish: (up: PointerInputChange) -> Unit = {},
    crossinline onFinallyUp: (up: PointerInputChange?) -> Unit = {},
    crossinline onLongPress: (firstDown: PointerInputChange) -> Unit,
): Modifier = pointerInput(Unit) {
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed, firstDownPass)
            val longPressTimeout = viewConfiguration.longPressTimeoutMillis

            var longPressed = false

            val job = launch {
                delay(longPressTimeout)
                longPressed = true
                onLongPress(down)
            }

            val upOrCancellation = waitForUpOrCancellation(upOrCancellationPass)
            job.cancel()

            if (upOrCancellation != null && longPressed) {
                onFinish(upOrCancellation)
            } else {
                onFinallyUp(upOrCancellation)
            }
        }
    }
}
