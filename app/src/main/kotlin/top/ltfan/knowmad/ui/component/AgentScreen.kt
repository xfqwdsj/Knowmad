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

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import top.ltfan.knowmad.application.KnowmadApplication
import top.ltfan.knowmad.ui.viewmodel.AgentViewModel

@Composable
fun AgentScreen() {
    val viewModel = viewModel<AgentViewModel>()

    NavDisplay(
        backStack = viewModel.backStack,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            fadeIn() + slideInHorizontally { it / 2 } togetherWith fadeOut() + slideOutHorizontally()
        },
        popTransitionSpec = {
            fadeIn() + slideInHorizontally() togetherWith fadeOut() + slideOutHorizontally { it / 2 }
        },
        predictivePopTransitionSpec = { edge ->
            val factor = if (edge == NavigationEvent.EDGE_RIGHT) -1 else 1
            fadeIn() + slideInHorizontally { -it * factor / 2 } togetherWith fadeOut() + slideOutHorizontally { it * factor / 2 }
        },
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
