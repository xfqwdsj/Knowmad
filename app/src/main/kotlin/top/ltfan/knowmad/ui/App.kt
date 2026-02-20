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

package top.ltfan.knowmad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import top.ltfan.knowmad.ui.component.ICalendarImportResultDialog
import top.ltfan.knowmad.ui.page.Page
import top.ltfan.knowmad.ui.page.expanded
import top.ltfan.knowmad.ui.scene.OverlayContentSceneStrategy
import top.ltfan.knowmad.ui.util.localSharedTransitionScope
import top.ltfan.knowmad.ui.viewmodel.LocalAppViewModel

@Composable
fun AppContent() {
    val appViewModel = LocalAppViewModel.current

    if (appViewModel.appReady) {
        val overlayContentStrategy = remember { OverlayContentSceneStrategy<Page>() }

        NavDisplay(
            backStack = appViewModel.backStack.expanded,
            onBack = appViewModel::onBack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            sceneStrategy = overlayContentStrategy,
            sharedTransitionScope = localSharedTransitionScope { this },
            entryProvider = { it.navEntry() },
        )
    }

    appViewModel.iCalendarImportResult?.let { (result, errors) ->
        ICalendarImportResultDialog(
            onDismissRequest = { appViewModel.iCalendarImportResult = null },
            result = result,
            errors = errors,
        )
    }
}
