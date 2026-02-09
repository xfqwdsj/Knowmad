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

package top.ltfan.knowmad.ui.viewmodel

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavBackStack
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import top.ltfan.knowmad.application.KnowmadApplication
import top.ltfan.knowmad.data.llm.LLMConfigEntry
import top.ltfan.knowmad.data.schedule.Event
import top.ltfan.knowmad.data.wizard.FirstJoinedData
import top.ltfan.knowmad.data.wizard.WizardState
import top.ltfan.knowmad.ui.component.CalendarState
import top.ltfan.knowmad.ui.component.MathJaxRenderer
import top.ltfan.knowmad.ui.component.MathJaxRendererState
import top.ltfan.knowmad.ui.component.MathJaxRendererState.Initializing
import top.ltfan.knowmad.ui.component.jsDelivrMathJaxLoadExternal
import top.ltfan.knowmad.ui.page.AgentPage
import top.ltfan.knowmad.ui.page.EventsDialogPage
import top.ltfan.knowmad.ui.page.MainPage
import top.ltfan.knowmad.ui.page.Route
import top.ltfan.knowmad.ui.page.WizardPage
import top.ltfan.knowmad.ui.page.back
import top.ltfan.knowmad.util.transform
import kotlin.time.Clock
import kotlin.time.Instant

class AppViewModel(app: KnowmadApplication) : AndroidViewModel<KnowmadApplication>(app) {
    private val httpClient = HttpClient().also {
        addCloseable(it)
    }

    val backStack = NavBackStack<Route>()
    var appReady by mutableStateOf(false)

    var mathJaxRendererState by mutableStateOf<MathJaxRendererState>(Initializing)

    init {
        val renderer = MathJaxRenderer(application.assets)
        addCloseable(renderer)
        viewModelScope.launch {
            renderer.initialize(jsDelivrMathJaxLoadExternal(httpClient))
            mathJaxRendererState = MathJaxRendererState.Ready(renderer)
        }
    }

    val scheduleDao = application.appDatabase.scheduleDao()

    private val wizardStateStore = WizardState.createDataStore()
    private val wizardStateFlow = wizardStateStore.dataStateFlow()
    private val wizardState = wizardStateStore.asMutableState(wizardStateFlow)
    var firstJoinedData by wizardState.transform(
        transformIn = { data },
        transformOut = { copy(data = it) },
    )

    fun getEvents(startTime: Instant, endTime: Instant) =
        scheduleDao.getEventsFlowInRange(startTime, endTime)

    fun onFinishWizard(
        entry: LLMConfigEntry,
        firstJoinedData: FirstJoinedData,
        onFailed: (message: String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dao = application.appDatabase.llmConfigDao()
                val providerConfig = entry.getProviderConfig()
                dao.insertProviderAtEnd(providerConfig)
                val providerId = providerConfig.id
                val modelConfig = entry.getModelConfig(providerId)
                dao.insertModelAtEnd(modelConfig)
            } catch (e: Throwable) {
                e.printStackTrace()
                onFailed(e.localizedMessage ?: "Unknown error")
                return@launch
            }
            this@AppViewModel.firstJoinedData = firstJoinedData
            navigateToMainPage()
            backStack.removeAll { it is WizardPage }
        }
    }

    fun onSkipWizard() {
        firstJoinedData = FirstJoinedData(instant = Clock.System.now())
        navigateToMainPage()
        backStack.removeAll { it is WizardPage }
    }

    fun navigateToMainPage() {
        backStack.add(MainPage())
    }

    val standaloneAgentScreenIndex inline get() = backStack.indexOfLast { it is AgentPage }

    fun switchStandaloneAgentScreen() {
        val index = standaloneAgentScreenIndex
        if (index != -1) {
            backStack.removeAt(index)
        } else {
            backStack.add(AgentPage())
        }
    }

    init {
        viewModelScope.launch {
            if (wizardStateStore.data.first().data == null) {
                backStack.add(WizardPage())
            } else {
                navigateToMainPage()
            }
            appReady = true
        }
    }

    val snackbarHostState = SnackbarHostState()

    init {
        val resources = application.resources
        viewModelScope.launch {
            GlobalViewModel.snackbarEvent.filterNotNull().collect { event ->
                val message = event.message.get(resources)
                val actionLabel = event.action?.label?.get(resources)
                launch {
                    snackbarHostState.showSnackbar(
                        message,
                        actionLabel,
                        event.withDismissAction,
                        event.duration,
                    ).let {
                        when (it) {
                            SnackbarResult.ActionPerformed -> {
                                event.action?.onClick?.invoke()
                            }

                            SnackbarResult.Dismissed -> {
                                event.onDismissed?.invoke()
                            }
                        }
                    }
                }
            }
        }
    }

    val calendarState = CalendarState()

    fun onCalendarEventClick(date: LocalDate, clickedEvent: Event, initialEvents: List<Event>) {
        val highlight = Channel<Event>(capacity = 1).apply {
            trySend(clickedEvent)
        }

        backStack.add(
            EventsDialogPage(
                date = date,
                timeZone = calendarState.timeZone,
                localeLanguageTag = application.resources.configuration.locales[0].toLanguageTag(),
                initialEvents = initialEvents,
                highlight = highlight,
            ),
        )
    }

    fun closeEventsDialog(page: EventsDialogPage) {
        backStack.removeIf { it == page }
    }

    fun onSystemDateChanged(lastDay: LocalDate, newDay: LocalDate) {
        if (lastDay == calendarState.selectedDate) {
            calendarState.selectedDate = newDay
            return
        }

        // TODO: UI feedback for that
    }

    fun onBack() {
        backStack.back()
    }
}

val LocalAppViewModel = staticCompositionLocalOf<AppViewModel> {
    error("No AppViewModel provided")
}
