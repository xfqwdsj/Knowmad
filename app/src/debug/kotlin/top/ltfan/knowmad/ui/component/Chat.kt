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

package top.ltfan.knowmad.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.flow.flowOf
import top.ltfan.knowmad.ui.theme.AppTheme
import kotlin.time.Clock
import kotlin.time.Instant

@Preview
@Composable
fun ReasoningMessagePreview() {
    var endedAt by remember { mutableStateOf<Instant?>(null) }
    var visible by remember { mutableStateOf(true) }
    AppTheme {
        Column {
            Button({ endedAt = Clock.System.now() }) {
                Text("End Reasoning")
            }
            ReasoningMessage(
                flow = flowOf("This is a sample reasoning message.\n\n- Step 1: Do this.\n- Step 2: Do that.\n\n**Conclusion:** This is the result."),
                startedAt = remember { Clock.System.now() },
                endedAt = endedAt,
                visible = visible,
                onVisibilityChange = { visible = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
fun MessageActionsPreview() {
    MessageActions(
        current = 1,
        total = 5,
        onPrevious = {},
        onNext = {},
        onCopy = { null to "Sample copied text" },
        onRegenerate = {},
    )
}
