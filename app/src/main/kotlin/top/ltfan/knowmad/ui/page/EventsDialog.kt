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

import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.channels.Channel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import top.ltfan.knowmad.data.schedule.Event
import top.ltfan.knowmad.ui.component.DetailedEventList
import top.ltfan.knowmad.ui.component.Dialog
import top.ltfan.knowmad.ui.component.DialogMargin
import top.ltfan.knowmad.ui.component.EventEdit
import top.ltfan.knowmad.ui.component.EventEditScreen
import top.ltfan.knowmad.ui.component.EventInformationScreen
import top.ltfan.knowmad.ui.component.EventsDialogContent
import top.ltfan.knowmad.ui.util.copy
import top.ltfan.knowmad.ui.util.localSharedTransitionScope
import top.ltfan.knowmad.ui.util.plus
import top.ltfan.knowmad.ui.viewmodel.EventsDialogPageViewModel
import top.ltfan.knowmad.ui.viewmodel.LocalAppViewModel
import java.util.Locale

@Serializable
sealed class EventsDialogSubPage : SubPage<EventsDialogSubPage>() {
    companion object {
        fun first(highlight: Channel<Event>?): EventsDialogSubPage = ListPage(highlight)
    }
}

@Serializable
class EventsDialogPage(
    private val date: LocalDate,
    private val timeZone: TimeZone,
    private val localeLanguageTag: String,
    private val initialEvents: List<Event>,
    @Transient
    private val highlight: Channel<Event>? = null,
) : Page() {
    // TODO: uncomment on navigation3 1.1.0
//    @Transient
//    override val metadata = OverlayContentSceneStrategy.overlayContent()

    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        val appViewModel = LocalAppViewModel.current

        Dialog(
            onDismissRequest = { appViewModel.closeEventsDialog(this) },
        ) {
            Box(Modifier.padding(DialogMargin)) {
                DialogContent(contentPadding)
            }
        }
    }

    @Composable
    fun DialogContent(contentPadding: PaddingValues = PaddingValues()) {
        val appViewModel = LocalAppViewModel.current
        val viewModel = viewModel {
            EventsDialogPageViewModel(
                date = date,
                timeZone = timeZone,
                locale = Locale.forLanguageTag(localeLanguageTag),
                initialEvents = initialEvents,
                highlight = highlight,
                dao = appViewModel.scheduleDao,
            )
        }

        EventsDialogContent(
            date = viewModel.date,
            contentPadding = contentPadding,
            locale = viewModel.locale,
        ) {
            localSharedTransitionScope {
                NavDisplay(
                    backStack = viewModel.backStack,
                    contentAlignment = Alignment.TopCenter,
                    sharedTransitionScope = this,
                    sizeTransform = SizeTransform(),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    popTransitionSpec = { fadeIn() togetherWith fadeOut() },
                    predictivePopTransitionSpec = { fadeIn() togetherWith fadeOut() },
                    entryProvider = { it.navEntry() },
                )
            }
        }
    }
}

@Serializable
private class ListPage(
    @Transient
    private val highlight: Channel<Event>? = null,
) : EventsDialogSubPage() {
    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        val viewModel = viewModel<EventsDialogPageViewModel>()

        DetailedEventList(
            events = viewModel.events,
            selectedEvent = viewModel.selectedEvent,
            onEventSelected = { viewModel.backStack.add(DetailsPage(it)) },
            highlight = highlight,
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            locale = viewModel.locale,
            timeZone = viewModel.timeZone,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
        )
    }
}

@Serializable
sealed class EventDetailsSubPage : EventsDialogSubPage() {
    abstract val selectedEvent: Event

    override fun contentKey() = selectedEvent.id
}

@Serializable
private class DetailsPage(override val selectedEvent: Event) : EventDetailsSubPage() {
    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        val viewModel = viewModel<EventsDialogPageViewModel>()
        val layoutDirection = LocalLayoutDirection.current
        val contentPadding = contentPadding + PaddingValues(8.dp)

        EventInformationScreen(
            event = selectedEvent,
            onBack = viewModel::onBack,
            onRequestEdit = { viewModel.backStack.add(EditPage(selectedEvent, it)) },
            eventModifier = Modifier.padding(
                contentPadding.copy(
                    layoutDirection,
                    bottom = 0.dp,
                ),
            ),
            listModifier = {
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(
                        it + contentPadding.copy(
                            layoutDirection,
                            top = 0.dp,
                        ),
                    )
            },
            locale = viewModel.locale,
            timeZone = viewModel.timeZone,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
        )
    }
}

@Serializable
private class EditPage(
    override val selectedEvent: Event,
    val edit: EventEdit,
) : EventDetailsSubPage() {
    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        val viewModel = viewModel<EventsDialogPageViewModel>()
        val layoutDirection = LocalLayoutDirection.current
        val contentPadding = contentPadding + PaddingValues(8.dp)

        EventEditScreen(
            event = selectedEvent,
            edit = edit,
            onBack = viewModel::onBack,
            onEdit = viewModel::onEdit,
            eventModifier = Modifier.padding(
                contentPadding.copy(
                    layoutDirection,
                    bottom = 0.dp,
                ),
            ),
            editModifier = {
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(
                        it + contentPadding.copy(
                            layoutDirection,
                            top = 0.dp,
                        ),
                    )
            },
            locale = viewModel.locale,
            timeZone = viewModel.timeZone,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
        )
    }
}
