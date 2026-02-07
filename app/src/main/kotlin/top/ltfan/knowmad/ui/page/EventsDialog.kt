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

package top.ltfan.knowmad.ui.page

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.ui.NavDisplay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.data.schedule.Event
import top.ltfan.knowmad.ui.component.Dialog
import top.ltfan.knowmad.ui.component.EventsDialogContent
import top.ltfan.knowmad.ui.scene.OverlayContentSceneStrategy
import top.ltfan.knowmad.ui.util.localSharedTransitionScope
import top.ltfan.knowmad.ui.viewmodel.EventsDialogPageViewModel
import top.ltfan.knowmad.ui.viewmodel.LocalAppViewModel
import java.util.Locale

@Serializable
sealed class EventsDialogSubPage : SubPage<EventsDialogSubPage>() {
    companion object {
        val First: EventsDialogSubPage = ListPage()
    }
}

@Serializable
class EventsDialogPage(
    private val date: LocalDate,
    private val timeZone: TimeZone,
    private val localeLanguageTag: String,
    private val initialEvents: List<Event>,
) : Page() {
    override val metadata = OverlayContentSceneStrategy.overlayContent()

    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        val appViewModel = LocalAppViewModel.current
        val viewModel = viewModel {
            EventsDialogPageViewModel(
                date = date,
                timeZone = timeZone,
                locale = Locale.forLanguageTag(localeLanguageTag),
                initialEvents = initialEvents,
                dao = appViewModel.scheduleDao,
            )
        }

        Dialog(
            onDismissRequest = appViewModel::onBack,
        ) {
            EventsDialogContent(
                date = viewModel.date,
                locale = viewModel.locale,
            ) {
                localSharedTransitionScope {
                    NavDisplay(
                        backStack = viewModel.backStack,
                        contentAlignment = Alignment.TopCenter,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        popTransitionSpec = { fadeIn() togetherWith fadeOut() },
                        predictivePopTransitionSpec = { fadeIn() togetherWith fadeOut() },
                        sharedTransitionScope = this,
                        entryProvider = { it.navEntry() },
                    )
                }
            }
        }
    }
}

@Serializable
private class ListPage : EventsDialogSubPage() {
    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {

    }
}

@Serializable
private class DetailsPage : EventsDialogSubPage() {
    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {

    }
}

@Serializable
private class EditPage : EventsDialogSubPage() {
    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {

    }
}
