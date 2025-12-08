/*
 * Knowmad - Knowledge nomad
 * Copyright (C) 2025 LTFan (aka xfqwdsj)
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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavBackStack
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.R
import top.ltfan.knowmad.ui.util.AppWindowInsets
import top.ltfan.knowmad.ui.util.plus
import top.ltfan.knowmad.ui.viewmodel.AppViewModel

@Serializable
class ConfigurationPage() : BackStackRoute(
    backStack = NavBackStack(),
) {
    init {
        val apiConfigurationPage = ApiConfigurationPage()
        apiConfigurationPage.parent = this
        backStack.add(apiConfigurationPage)
    }
}

@Serializable
private class ApiConfigurationPage : Page() {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    context(contentPadding: PaddingValues)
    override fun AppViewModel.Content() {
        Scaffold(
            topBar = {
                LargeTopAppBar(
                    title = {
                        Text(stringResource(R.string.app_name))
                    },
                )
            },
            contentWindowInsets = AppWindowInsets + contentPadding,
        ) { contentPadding ->
            Box(Modifier.padding(contentPadding)) {
                Text(stringResource(R.string.app_name))
            }
        }
    }
}
