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
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.llm.LLMCapabilities
import top.ltfan.knowmad.data.llm.LLMCapabilityItem
import top.ltfan.knowmad.ui.util.SharedTransitionScopes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelCapabilitiesFlow(
    capabilities: List<LLMCapability>,
    onAdd: (LLMCapability) -> Unit,
    onRemove: (LLMCapability) -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScopes: SharedTransitionScopes? = null,
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
                selected = isSelected,
                onSelectedChange = { selected ->
                    if (selected) onAdd(item.capability) else onRemove(item.capability)
                },
                sharedTransitionScopes = sharedTransitionScopes,
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
    sharedTransitionScopes: SharedTransitionScopes? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (item in LLMCapabilities) {
            CapabilityListItem(
                capabilities = capabilities,
                item = item,
                onAdd = onAdd,
                onRemove = onRemove,
                sharedTransitionScopes = sharedTransitionScopes,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CapabilityToggleButton(
    item: LLMCapabilityItem.Capability,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    sharedTransitionScopes: SharedTransitionScopes? = null,
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
                        if (sharedTransitionScopes == null) this
                        else with(sharedTransitionScopes.sharedTransitionScope) {
                            sharedBounds(
                                rememberSharedContentState(SharedKey.Container(item)),
                                sharedTransitionScopes.animatedContentScope,
                                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                            )
                        }
                    },
                shapes = SplitButtonDefaults.leadingButtonShapesFor(size),
                colors = colors,
                border = border,
                contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(size),
            ) {
                Text(
                    stringResource(item.label),
                    modifier = Modifier.run {
                        if (sharedTransitionScopes == null) this
                        else with(sharedTransitionScopes.sharedTransitionScope) {
                            sharedBounds(
                                rememberSharedContentState(SharedKey.Label(item)),
                                sharedTransitionScopes.animatedContentScope,
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
                        Icons.AutoMirrored.Default.Help,
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
    item: LLMCapabilityItem,
    onAdd: (LLMCapability) -> Unit,
    onRemove: (LLMCapability) -> Unit,
    sharedTransitionScopes: SharedTransitionScopes? = null,
) {
    when (item) {
        is LLMCapabilityItem.Category -> {
            ListCategory(
                capabilities = capabilities,
                item = item,
                onAdd = onAdd,
                onRemove = onRemove,
                sharedTransitionScopes = sharedTransitionScopes,
            )
        }

        is LLMCapabilityItem.Capability -> {
            val isSelected = item.capability in capabilities
            CapabilityItem(
                item = item,
                selected = isSelected,
                onSelectedChange = { selected ->
                    if (selected) onAdd(item.capability) else onRemove(item.capability)
                },
                sharedTransitionScopes = sharedTransitionScopes,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ListCategory(
    capabilities: List<LLMCapability>,
    item: LLMCapabilityItem.Category,
    onAdd: (LLMCapability) -> Unit,
    onRemove: (LLMCapability) -> Unit,
    sharedTransitionScopes: SharedTransitionScopes? = null,
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
                        onAdd = onAdd,
                        onRemove = onRemove,
                        sharedTransitionScopes = sharedTransitionScopes,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CapabilityItem(
    item: LLMCapabilityItem.Capability,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    sharedTransitionScopes: SharedTransitionScopes? = null,
) {
    ListItem(
        selected = selected,
        onClick = { onSelectedChange(!selected) },
        modifier = Modifier.run {
            if (sharedTransitionScopes == null) this
            else with(sharedTransitionScopes.sharedTransitionScope) {
                sharedBounds(
                    rememberSharedContentState(SharedKey.Container(item)),
                    sharedTransitionScopes.animatedContentScope,
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                )
            }
        },
        supportingContent = { Text(stringResource(item.description)) },
    ) {
        Text(
            stringResource(item.label),
            modifier = Modifier.run {
                if (sharedTransitionScopes == null) this
                else with(sharedTransitionScopes.sharedTransitionScope) {
                    sharedBounds(
                        rememberSharedContentState(SharedKey.Label(item)),
                        sharedTransitionScopes.animatedContentScope,
                    )
                }
            },
        )
    }
}

private fun getAllCapabilities(items: List<LLMCapabilityItem>): List<LLMCapabilityItem.Capability> {
    val result = mutableListOf<LLMCapabilityItem.Capability>()
    for (item in items) {
        when (item) {
            is LLMCapabilityItem.Capability -> result.add(item)
            is LLMCapabilityItem.Category -> result.addAll(getAllCapabilities(item.items))
        }
    }
    return result
}

@Immutable
private interface SharedKey {
    val item: LLMCapabilityItem.Capability

    @Immutable
    data class Label(override val item: LLMCapabilityItem.Capability) : SharedKey

    @Immutable
    data class Container(override val item: LLMCapabilityItem.Capability) : SharedKey
}
