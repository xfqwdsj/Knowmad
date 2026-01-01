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

package top.ltfan.knowmad.ui.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.ui.NavDisplay
import top.ltfan.knowmad.application.KnowmadApplication
import top.ltfan.knowmad.ui.viewmodel.AgentViewModel

@Composable
fun AgentScreen() {
    val viewModel = viewModel<AgentViewModel>()

    NavDisplay(
        backStack = viewModel.backStack,
        modifier = Modifier.fillMaxSize(),
        entryProvider = { it.navEntry() },
    )
}

@Preview
@Composable
fun AgentScreenPreview() {
    ApplicationPreview {
        (this as? KnowmadApplication)?.let {
            viewModel<AgentViewModel> {
                AgentViewModel(it)
            }
        }

        AgentScreen()
    }
}
