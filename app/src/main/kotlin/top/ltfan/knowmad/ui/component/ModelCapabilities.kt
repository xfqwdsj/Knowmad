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

package top.ltfan.knowmad.ui.component

import ai.koog.prompt.llm.LLMCapability
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.llm.LLMCapabilities
import top.ltfan.knowmad.data.llm.LLMCapabilityInfo
import top.ltfan.knowmad.ui.util.LocalSharedTransitionScope

@Composable
fun ModelCapabilitiesFlow(
    capabilities: List<LLMCapability>,
    onAdd: (LLMCapability) -> Unit,
    onRemove: (LLMCapability) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    animatedContentScope: AnimatedContentScope? = null,
) {
    val allItems = remember { getAllCapabilities(LLMCapabilities) }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(
            space = 4.dp,
            alignment = Alignment.CenterHorizontally,
        ),
    ) {
        for (item in allItems) {
            val isSelected = item.capability in capabilities
            CapabilityToggleButton(
                item = item,
                enabled = enabled,
                selected = isSelected,
                onSelectedChange = { selected ->
                    if (selected) onAdd(item.capability) else onRemove(item.capability)
                },
                animatedContentScope = animatedContentScope,
            )
        }
    }
}

@Composable
fun ModelCapabilitiesList(
    capabilities: List<LLMCapability>,
    onAdd: (LLMCapability) -> Unit,
    onRemove: (LLMCapability) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    animatedContentScope: AnimatedContentScope? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (item in LLMCapabilities) {
            CapabilityListItem(
                capabilities = capabilities,
                item = item,
                enabled = enabled,
                onAdd = onAdd,
                onRemove = onRemove,
                animatedContentScope = animatedContentScope,
            )
        }
    }
}

@Composable
private fun CapabilityToggleButton(
    item: LLMCapabilityInfo.Capability,
    enabled: Boolean = true,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    animatedContentScope: AnimatedContentScope? = null,
) {
    val size = SplitButtonDefaults.ExtraSmallContainerHeight
    val colors =
        if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
    val border = if (selected) null else ButtonDefaults.outlinedButtonBorder()
    SplitButtonLayout(
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = { onSelectedChange(!selected) },
                modifier = Modifier
                    .semantics {
                        this.selected = selected
                    }.run {
                        if (animatedContentScope == null) this
                        else with(LocalSharedTransitionScope.current) {
                            sharedBounds(
                                rememberSharedContentState(SharedKey.Container(item)),
                                animatedContentScope,
                                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                            )
                        }
                    },
                enabled = enabled,
                shapes = SplitButtonDefaults.leadingButtonShapesFor(size),
                colors = colors,
                border = border,
                contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(size),
            ) {
                Text(
                    stringResource(item.label),
                    modifier = Modifier.run {
                        if (animatedContentScope == null) this
                        else with(LocalSharedTransitionScope.current) {
                            sharedBounds(
                                rememberSharedContentState(SharedKey.Label(item)),
                                animatedContentScope,
                            )
                        }
                    },
                    style = ButtonDefaults.textStyleFor(size),
                )
            }
        },
        trailingButton = {
            val tooltipState = rememberTooltipState()
            val coroutineScope = rememberCoroutineScope()
            TooltipBox(
                positionProvider =
                    TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(stringResource(item.description)) } },
                state = tooltipState,
            ) {
                SplitButtonDefaults.TrailingButton(
                    onClick = { coroutineScope.launch { tooltipState.show() } },
                    shapes = SplitButtonDefaults.trailingButtonShapesFor(size),
                    colors = colors,
                    border = border,
                    contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(size),
                ) {
                    Icon(
                        painterResource(R.drawable.help_24px),
                        contentDescription = stringResource(R.string.label_help),
                        modifier = Modifier.size(SplitButtonDefaults.trailingButtonIconSizeFor(size)),
                    )
                }
            }
        },
    )
}

@Composable
private fun CapabilityListItem(
    capabilities: List<LLMCapability>,
    item: LLMCapabilityInfo,
    enabled: Boolean = true,
    onAdd: (LLMCapability) -> Unit,
    onRemove: (LLMCapability) -> Unit,
    animatedContentScope: AnimatedContentScope? = null,
) {
    when (item) {
        is LLMCapabilityInfo.Category -> {
            ListCategory(
                capabilities = capabilities,
                item = item,
                enabled = enabled,
                onAdd = onAdd,
                onRemove = onRemove,
                animatedContentScope = animatedContentScope,
            )
        }

        is LLMCapabilityInfo.Capability -> {
            val isSelected = item.capability in capabilities
            CapabilityItem(
                item = item,
                enabled = enabled,
                selected = isSelected,
                onSelectedChange = { selected ->
                    if (selected) onAdd(item.capability) else onRemove(item.capability)
                },
                animatedContentScope = animatedContentScope,
            )
        }
    }
}

@Composable
private fun ListCategory(
    capabilities: List<LLMCapability>,
    item: LLMCapabilityInfo.Category,
    enabled: Boolean = true,
    onAdd: (LLMCapability) -> Unit,
    onRemove: (LLMCapability) -> Unit,
    animatedContentScope: AnimatedContentScope? = null,
) {
    OutlinedCard {
        Column(Modifier.padding(8.dp)) {
            Text(stringResource(item.label), style = MaterialTheme.typography.labelMediumEmphasized)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(item.description), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (child in item.items) {
                    CapabilityListItem(
                        capabilities = capabilities,
                        item = child,
                        enabled = enabled,
                        onAdd = onAdd,
                        onRemove = onRemove,
                        animatedContentScope = animatedContentScope,
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilityItem(
    item: LLMCapabilityInfo.Capability,
    enabled: Boolean = true,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    animatedContentScope: AnimatedContentScope? = null,
) {
    ListItem(
        selected = selected,
        onClick = { onSelectedChange(!selected) },
        modifier = Modifier.run {
            if (animatedContentScope == null) this
            else with(LocalSharedTransitionScope.current) {
                sharedBounds(
                    rememberSharedContentState(SharedKey.Container(item)),
                    animatedContentScope,
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                )
            }
        },
        enabled = enabled,
        supportingContent = { Text(stringResource(item.description)) },
    ) {
        Text(
            stringResource(item.label),
            modifier = Modifier.run {
                if (animatedContentScope == null) this
                else with(LocalSharedTransitionScope.current) {
                    sharedBounds(
                        rememberSharedContentState(SharedKey.Label(item)),
                        animatedContentScope,
                    )
                }
            },
        )
    }
}

private fun getAllCapabilities(items: List<LLMCapabilityInfo>): List<LLMCapabilityInfo.Capability> {
    val result = mutableListOf<LLMCapabilityInfo.Capability>()
    for (item in items) {
        when (item) {
            is LLMCapabilityInfo.Capability -> result.add(item)
            is LLMCapabilityInfo.Category -> result.addAll(getAllCapabilities(item.items))
        }
    }
    return result
}

@Immutable
private interface SharedKey {
    val item: LLMCapabilityInfo.Capability

    @Immutable
    data class Label(override val item: LLMCapabilityInfo.Capability) : SharedKey

    @Immutable
    data class Container(override val item: LLMCapabilityInfo.Capability) : SharedKey
}
