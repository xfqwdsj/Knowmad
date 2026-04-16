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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalTime
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.llm.LLMConfigEntity
import top.ltfan.knowmad.ui.component.ArrowBackIconButton
import top.ltfan.knowmad.ui.component.ModelSelectorDropdownMenuContent
import top.ltfan.knowmad.ui.component.SettingItemDropdown
import top.ltfan.knowmad.ui.component.SettingsBadge
import top.ltfan.knowmad.ui.component.SettingsItemSwitch
import top.ltfan.knowmad.ui.component.SettingsItemTimePicker
import top.ltfan.knowmad.ui.theme.TopAppBarColorsTransparent
import top.ltfan.knowmad.ui.util.AppWindowInsets
import top.ltfan.knowmad.ui.util.BackdropEffectsLight
import top.ltfan.knowmad.ui.util.BackdropInteractiveHighlight
import top.ltfan.knowmad.ui.util.LinearBrushData
import top.ltfan.knowmad.ui.util.plus
import top.ltfan.knowmad.ui.util.progressiveBlurWithFallback
import top.ltfan.knowmad.ui.viewmodel.LocalAgentViewModel
import top.ltfan.knowmad.ui.viewmodel.LocalAppViewModel

class SettingsPage : Page() {
    @Composable
    context(contentPadding: PaddingValues)
    override fun Content() {
        val viewModel = LocalAppViewModel.current

        val background = MaterialTheme.colorScheme.background
        val backdrop = rememberLayerBackdrop {
            drawRect(background)
            drawContent()
        }

        Scaffold(
            topBar = {
                val appBarBackdrop = rememberLayerBackdrop()
                TopAppBar(
                    title = {},
                    modifier = Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { RectangleShape },
                        effects = {
                            progressiveBlurWithFallback(
                                radius = 48.dp.toPx(),
                                data = LinearBrushData(start = Offset(0f, POSITIVE_INFINITY)),
                            )
                        },
                        highlight = null,
                        shadow = null,
                        exportedBackdrop = appBarBackdrop,
                    ),
                    navigationIcon = {
                        val coroutineScope = rememberCoroutineScope()
                        val interactiveHighlight = remember(coroutineScope) {
                            BackdropInteractiveHighlight(coroutineScope)
                        }
                        ArrowBackIconButton(
                            onClick = viewModel.backStack::removeLastOrNull,
                            modifier = Modifier
                                .drawBackdrop(
                                    backdrop = appBarBackdrop,
                                    shape = { CircleShape },
                                    effects = BackdropEffectsLight,
                                )
                                .then(interactiveHighlight.modifier)
                                .then(interactiveHighlight.gestureModifier),
                            enabled = viewModel.backStack.size > 1,
                        )
                    },
                    colors = TopAppBarColorsTransparent,
                )
            },
            contentWindowInsets = AppWindowInsets,
        ) { scaffoldPadding ->
            val contentPadding = scaffoldPadding + contentPadding + PaddingValues(16.dp)

            LazyColumn(
                modifier = Modifier.layerBackdrop(backdrop),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                modelSettings()
                chatSettings()
                nextSuggestionSettings()
                classProgressSettings()
            }
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun LazyListScope.modelSettings() {
        val simpleBadge = @Composable {
            SettingsBadge(
                text = stringResource(R.string.settings_model_task_type_simple_label),
                summary = stringResource(R.string.settings_model_task_type_simple_summary),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        val mediumBadge = @Composable {
            SettingsBadge(
                text = stringResource(R.string.settings_model_task_type_medium_label),
                summary = stringResource(R.string.settings_model_task_type_medium_summary),
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        val complexBadge = @Composable {
            SettingsBadge(
                text = stringResource(R.string.settings_model_task_type_complex_label),
                summary = stringResource(R.string.settings_model_task_type_complex_summary),
                color = MaterialTheme.colorScheme.error,
            )
        }

        item {
            val viewModel = LocalAgentViewModel.current

            Card(
                shape = MaterialTheme.shapes.large,
            ) {
                run {
                    val model: LLMConfigEntity? by produceState(null) {
                        viewModel.llmDataStateFlow.map { it.conversationNameGenerationModelId }
                            .distinctUntilChanged()
                            .map { it?.let { id -> viewModel.llmConfigDao.getModelById(id) } }
                            .collect { value = it }
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    SettingItemDropdown(
                        title = stringResource(R.string.settings_model_conversation_name_generation_label),
                        selectedValue = model?.name
                            ?: stringResource(R.string.settings_model_label_not_set),
                        showMenu = showMenu,
                        onShowMenuChange = { showMenu = it },
                        overlineContent = simpleBadge,
                        summary = stringResource(R.string.settings_model_conversation_name_generation_summary),
                    ) {
                        ModelSelectorDropdownMenuContent(
                            showMenu = showMenu,
                            onShowMenuChange = { showMenu = it },
                            providers = viewModel.providers,
                            getModels = viewModel::getModels,
                            onSelectModel = { selectedModel ->
                                viewModel.conversationNameGenerationModelId = selectedModel.id
                                showMenu = false
                            },
                        )
                    }
                }

                run {
                    val model: LLMConfigEntity? by produceState(null) {
                        viewModel.llmDataStateFlow.map { it.recurrenceRuleSummaryGenerationModelId }
                            .distinctUntilChanged()
                            .map { it?.let { id -> viewModel.llmConfigDao.getModelById(id) } }
                            .collect { value = it }
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    SettingItemDropdown(
                        title = stringResource(R.string.settings_model_recurrence_rule_summary_generation_label),
                        selectedValue = model?.name
                            ?: stringResource(R.string.settings_model_label_not_set),
                        showMenu = showMenu,
                        onShowMenuChange = { showMenu = it },
                        overlineContent = mediumBadge,
                        summary = stringResource(R.string.settings_model_recurrence_rule_summary_generation_summary),
                    ) {
                        ModelSelectorDropdownMenuContent(
                            showMenu = showMenu,
                            onShowMenuChange = { showMenu = it },
                            providers = viewModel.providers,
                            getModels = viewModel::getModels,
                            onSelectModel = { selectedModel ->
                                viewModel.recurrenceRuleSummaryGenerationModelId = selectedModel.id
                                showMenu = false
                            },
                        )
                    }
                }

                run {
                    val model: LLMConfigEntity? by produceState(null) {
                        viewModel.llmDataStateFlow.map { it.errorExplanationModelId }
                            .distinctUntilChanged()
                            .map { it?.let { id -> viewModel.llmConfigDao.getModelById(id) } }
                            .collect { value = it }
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    SettingItemDropdown(
                        title = stringResource(R.string.settings_model_error_explanation_label),
                        selectedValue = model?.name
                            ?: stringResource(R.string.settings_model_label_not_set),
                        showMenu = showMenu,
                        onShowMenuChange = { showMenu = it },
                        overlineContent = mediumBadge,
                        summary = stringResource(R.string.settings_model_error_explanation_summary),
                    ) {
                        ModelSelectorDropdownMenuContent(
                            showMenu = showMenu,
                            onShowMenuChange = { showMenu = it },
                            providers = viewModel.providers,
                            getModels = viewModel::getModels,
                            onSelectModel = { selectedModel ->
                                viewModel.errorExplanationModelId = selectedModel.id
                                showMenu = false
                            },
                        )
                    }
                }

                run {
                    val model: LLMConfigEntity? by produceState(null) {
                        viewModel.llmDataStateFlow.map { it.nextSuggestionGenerationModelId }
                            .distinctUntilChanged()
                            .map { it?.let { id -> viewModel.llmConfigDao.getModelById(id) } }
                            .collect { value = it }
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    SettingItemDropdown(
                        title = stringResource(R.string.settings_model_next_suggestion_generation_label),
                        selectedValue = model?.name
                            ?: stringResource(R.string.settings_model_label_not_set),
                        showMenu = showMenu,
                        onShowMenuChange = { showMenu = it },
                        overlineContent = complexBadge,
                        summary = stringResource(R.string.settings_model_next_suggestion_generation_summary),
                    ) {
                        ModelSelectorDropdownMenuContent(
                            showMenu = showMenu,
                            onShowMenuChange = { showMenu = it },
                            providers = viewModel.providers,
                            getModels = viewModel::getModels,
                            onSelectModel = { selectedModel ->
                                viewModel.nextSuggestionGenerationModelId = selectedModel.id
                                showMenu = false
                            },
                        )
                    }
                }
            }
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun LazyListScope.chatSettings() {
        item {
            val viewModel = LocalAgentViewModel.current

            Card {
                SettingsItemSwitch(
                    title = stringResource(R.string.settings_chat_default_reasoning_visibility_label),
                    checked = viewModel.defaultReasoningVisibility,
                    onCheckedChange = viewModel::defaultReasoningVisibility::set,
                    summary = stringResource(R.string.settings_chat_default_reasoning_visibility_summary),
                )
                SettingsItemSwitch(
                    title = stringResource(R.string.settings_chat_default_tool_visibility_label),
                    checked = viewModel.defaultToolVisibility,
                    onCheckedChange = viewModel::defaultToolVisibility::set,
                    summary = stringResource(R.string.settings_chat_default_tool_visibility_summary),
                )
            }
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun LazyListScope.nextSuggestionSettings() {
        item {
            val viewModel = LocalAppViewModel.current

            Card {
                SettingsItemSwitch(
                    title = stringResource(R.string.settings_next_suggestion_enabled_label),
                    checked = viewModel.nextSuggestionEnabled,
                    onCheckedChange = viewModel::nextSuggestionEnabled::set,
                    summary = stringResource(R.string.settings_next_suggestion_enabled_summary),
                )
                run {
                    val time = viewModel.nextSuggestionFallbackTime
                    val state = rememberTimePickerState(
                        initialHour = time.hour,
                        initialMinute = time.minute,
                    )
                    SettingsItemTimePicker(
                        title = stringResource(R.string.settings_next_suggestion_fallback_time_label),
                        state = state,
                        onConfirm = {
                            viewModel.nextSuggestionFallbackTime =
                                LocalTime(state.hour, state.minute)
                        },
                        summary = stringResource(R.string.settings_next_suggestion_fallback_time_summary),
                    )
                }
            }
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun LazyListScope.classProgressSettings() {
        item {
            val viewModel = LocalAppViewModel.current

            Card {
                SettingsItemSwitch(
                    title = stringResource(R.string.settings_class_progress_enabled_label),
                    checked = viewModel.classProgressEnabled,
                    onCheckedChange = viewModel::classProgressEnabled::set,
                    summary = stringResource(R.string.settings_class_progress_enabled_summary),
                )
            }
        }
    }
}
