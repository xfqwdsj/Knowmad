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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.ui.component.AgentConfigScreen
import top.ltfan.knowmad.ui.component.AgentMainScreen
import top.ltfan.knowmad.ui.component.AgentScreen
import top.ltfan.knowmad.ui.component.LocalAgentScreenIsStandalone

@Serializable
class AgentMainPage : AgentSubPage() {
    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        AgentMainScreen(contentPadding)
    }
}

@Serializable
class AgentConfigPage : AgentSubPage() {
    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        AgentConfigScreen(contentPadding)
    }
}

@Serializable
sealed class AgentSubPage : SubPage<AgentSubPage>()

@Serializable
class AgentPage : Page() {
    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        CompositionLocalProvider(LocalAgentScreenIsStandalone provides true) {
            AgentScreen(
                animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                contentPadding = contentPadding,
            )
        }
    }
}
