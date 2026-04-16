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
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavBackStack
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import top.ltfan.knowmad.application.KnowmadApplication
import top.ltfan.knowmad.data.database.AppDatabase.Companion.appDatabase
import top.ltfan.knowmad.data.llm.LLMConfigEntry
import top.ltfan.knowmad.data.schedule.Event
import top.ltfan.knowmad.data.schedule.SemesterEntity
import top.ltfan.knowmad.data.schedule.exportICalendar
import top.ltfan.knowmad.data.schedule.readCustomizedICalendar
import top.ltfan.knowmad.data.schedule.syncEvents
import top.ltfan.knowmad.data.schedule.toICalendar
import top.ltfan.knowmad.data.schedule.writeCustomized
import top.ltfan.knowmad.data.schedule.writeStandard
import top.ltfan.knowmad.data.task.ClassProgressConfiguration
import top.ltfan.knowmad.data.task.NextSuggestionConfiguration
import top.ltfan.knowmad.data.transform
import top.ltfan.knowmad.data.wizard.FirstJoinedData
import top.ltfan.knowmad.data.wizard.WizardState
import top.ltfan.knowmad.notification.NextSuggestionNotification
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
import top.ltfan.knowmad.util.Logger
import top.ltfan.knowmad.util.collectAsState
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AppViewModel(
    app: KnowmadApplication,
    val partial: Boolean = false,
) : AndroidViewModel<KnowmadApplication>(app) {
    private val logger = Logger("AppViewModel")

    val backStack = NavBackStack<Route>()
    var appReady by mutableStateOf(false)
        private set

    private val wizardStateStore = WizardState.createDataStore()
    private val wizardStateFlow = wizardStateStore.dataStateFlow()
    private val wizardState = wizardStateStore.asMutableState(wizardStateFlow.value)
    var firstJoinedData by wizardState.transform(
        transformIn = { data },
        transformOut = { copy(data = it) },
    )

    fun initializeApp() {
        if (appReady) return
        if (partial) {
            appReady = true
            return
        }
        viewModelScope.launch {
            val firstJoinedData = withTimeoutOrNull(30.seconds) {
                wizardStateStore.data.first().data
            }
            if (firstJoinedData == null) {
                backStack.add(WizardPage())
            } else {
                navigateToMainPage()
            }
            appReady = true
        }
    }

    private val nextSuggestionConfigurationStore = NextSuggestionConfiguration.createDataStore()
    private val nextSuggestionConfigurationFlow = nextSuggestionConfigurationStore.dataStateFlow()
    private val nextSuggestionConfiguration =
        nextSuggestionConfigurationStore.asMutableState(nextSuggestionConfigurationFlow.value)

    var nextSuggestionEnabled by nextSuggestionConfiguration.transform(
        transformIn = { enabled },
        transformOut = { copy(enabled = it) },
    )

    var nextSuggestionFallbackTime by nextSuggestionConfiguration.transform(
        transformIn = { fallbackTime },
        transformOut = { copy(fallbackTime = it) },
    )

    private val classProgressConfigurationStore = ClassProgressConfiguration.createDataStore()
    private val classProgressConfigurationFlow = classProgressConfigurationStore.dataStateFlow()
    private val classProgressConfiguration =
        classProgressConfigurationStore.asMutableState(classProgressConfigurationFlow.value)

    var classProgressEnabled by classProgressConfiguration.transform(
        transformIn = { enabled },
        transformOut = { copy(enabled = it) },
    )

    private val httpClient = HttpClient().also {
        addCloseable(it)
    }

    private val database = application.appDatabase

    val scheduleDao = database.scheduleDao()
    val llmConfigDao = database.llmConfigDao()

    fun onFinishWizard(
        entry: LLMConfigEntry,
        firstJoinedData: FirstJoinedData,
        onFailed: (message: String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val providerConfig = entry.getProviderConfig()
                llmConfigDao.insertProviderAtEnd(providerConfig)
                val providerId = providerConfig.id
                val modelConfig = entry.getModelConfig(providerId)
                llmConfigDao.insertModelAtEnd(modelConfig)
            } catch (e: Throwable) {
                logger.error(e) { "Failed to save LLM config from wizard" }
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
    val canExitStandaloneAgentScreen inline get() = backStack.size > 1

    fun switchStandaloneAgentScreen() {
        val index = standaloneAgentScreenIndex
        if (index != -1) {
            if (!canExitStandaloneAgentScreen) return
            if (index != backStack.lastIndex) {
                for (i in backStack.lastIndex downTo index + 1) {
                    backStack.removeAt(i)
                }
                return
            }
            backStack.removeAt(index)
        } else {
            backStack.add(AgentPage())
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

    val allSemesters by scheduleDao.getAllSemestersFlow().stateIn(
        scope = viewModelScope,
        started = Eagerly,
        initialValue = emptyList(),
    ).collectAsState()

    val invisibleSemesters = mutableStateSetOf<SemesterEntity>()

    fun onSemesterSelectionChange(semester: SemesterEntity, selected: Boolean) {
        if (selected) {
            invisibleSemesters.remove(semester)
        } else {
            invisibleSemesters.add(semester)
        }
    }

    var showMonthBottomSheet by mutableStateOf(false)

    suspend fun exportSemester(semester: SemesterEntity) =
        semester.exportICalendar(scheduleDao.getAllEventsBySemester(semester.id))
            .writeStandard()

    suspend fun backupSemester(semester: SemesterEntity) =
        semester.toICalendar(scheduleDao.getAllEventsBySemester(semester.id))
            .writeCustomized()

    suspend fun deleteSemester(semester: SemesterEntity) {
        scheduleDao.deleteSemester(semester)
    }

    var iCalendarImportResult by mutableStateOf<Pair<Result<Int>, List<String>?>?>(null)

    fun importFromICalendar(content: String) = viewModelScope.launch {
        doImportFromICalendar(content)
    }

    private suspend fun doImportFromICalendar(content: String) {
        val errors = mutableListOf<String>()

        fun success(count: Int) {
            iCalendarImportResult = Result.success(count) to errors.takeIf { it.isNotEmpty() }
        }

        fun failure(throwable: Throwable) {
            iCalendarImportResult =
                Result.failure<Int>(throwable) to errors.takeIf { it.isNotEmpty() }
        }

        val iCal = runCatching { readCustomizedICalendar(content) }
            .getOrElse { return failure(it) }
            ?: return failure(Throwable("There wasn't any iCalendar data in the content"))

        val events = scheduleDao.importFromICalendar(
            iCalendar = iCal,
            resources = application.resources,
            errors = errors,
        ).getOrElse {
            return failure(it)
        }

        if (events.isEmpty()) {
            return failure(Throwable("No valid events found in the iCalendar data"))
        }

        application.syncEvents(fullSync = false)

        return success(events.size)
    }

    var viewingSuggestion by mutableStateOf<NextSuggestionNotification?>(null)

    private val eventsCache =
        object : LinkedHashMap<Pair<Instant, Instant>, List<Event>>(10, .75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<Pair<Instant, Instant>, List<Event>>): Boolean {
                return size > 10
            }
        }

    fun getEvents(startTime: Instant, endTime: Instant) =
        scheduleDao.getEventsFlowInRange(startTime, endTime).onStart {
            eventsCache[startTime to endTime]?.let { emit(it) }
        }.onEach {
            eventsCache[startTime to endTime] = it
        }.combine(snapshotFlow { invisibleSemesters.toSet() }) { events, invisibleSemesters ->
            events.filter { event -> event.semester !in invisibleSemesters }
        }

    fun onCalendarDayClick(date: LocalDate, initialEvents: List<Event>?) {
        backStack.add(
            EventsDialogPage(
                date = date,
                timeZone = calendarState.timeZone,
                localeLanguageTag = application.resources.configuration.locales[0].toLanguageTag(),
                initialEvents = initialEvents ?: emptyList(),
            ),
        )
    }

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

    fun deleteEvent(
        event: Event,
        onDeleted: (onUndo: () -> Unit) -> Unit,
    ) {
        viewModelScope.launch {
            scheduleDao.deleteEventById(event.id)

            onDeleted {
                viewModelScope.launch {
                    scheduleDao.insertEvent(event.toEntity())
                }
            }
        }
    }

    fun closeEventsDialog(page: EventsDialogPage) {
        backStack.removeIf { it == page }
    }

    suspend fun onSystemDateChanged(lastDay: LocalDate, newDay: LocalDate) {
        if (lastDay.month == calendarState.currentMonth.month) {
            calendarState.animateScrollToDate(newDay)
            return
        }

        // TODO: UI feedback for that
    }

    var mathJaxRendererState by mutableStateOf<MathJaxRendererState>(Initializing)

    init {
        val renderer = MathJaxRenderer(application.assets)
        addCloseable(renderer)
        viewModelScope.launch {
            renderer.initialize(jsDelivrMathJaxLoadExternal(httpClient))
            mathJaxRendererState = MathJaxRendererState.Ready(renderer)
        }
    }

    fun onBack() {
        backStack.back()
    }
}

val LocalAppViewModel = staticCompositionLocalOf<AppViewModel> {
    error("No AppViewModel provided")
}
