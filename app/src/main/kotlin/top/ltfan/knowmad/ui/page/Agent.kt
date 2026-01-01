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

package top.ltfan.knowmad.ui.page

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.ui.component.ConversationList
import top.ltfan.knowmad.ui.util.AppWindowInsets
import top.ltfan.knowmad.ui.util.copy
import top.ltfan.knowmad.ui.util.only
import top.ltfan.knowmad.ui.util.plus
import top.ltfan.knowmad.ui.viewmodel.AgentViewModel

@Serializable
class AgentMainPage : AgentSubPage() {
    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        PageContent(contentPadding)
    }

    @Composable
    fun PageContent(
        contentPadding: PaddingValues,
    ) {
        val viewModel = viewModel<AgentViewModel>()

        val layoutDirection = LocalLayoutDirection.current

        ModalNavigationDrawer(
            drawerContent = {
                ModalDrawerSheet(
                    viewModel.drawerState,
                    windowInsets = AppWindowInsets.only { start } + contentPadding.copy(
                        layoutDirection,
                        top = 0.dp,
                        end = 0.dp,
                        bottom = 0.dp,
                    ),
                ) {
                    val contentPadding = AppWindowInsets.only { vertical }.asPaddingValues() +
                            contentPadding.copy(
                                layoutDirection,
                                start = 0.dp,
                                end = 0.dp,
                            )
                    ConversationList(contentPadding)
                }
            },
            drawerState = viewModel.drawerState,
        ) {

        }
    }

}

@Serializable
sealed class AgentSubPage : SubPage<AgentSubPage>()
