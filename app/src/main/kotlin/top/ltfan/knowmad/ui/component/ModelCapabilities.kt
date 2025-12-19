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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.llm.LLMCapabilities
import top.ltfan.knowmad.data.llm.LLMCapabilityItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelCapabilitiesFlow(
    capabilities: List<LLMCapability>,
    onAdd: (LLMCapability) -> Unit,
    onRemove: (LLMCapability) -> Unit,
) {
    val allItems = remember { getAllCapabilities(LLMCapabilities) }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (item in allItems) {
            val isSelected = item.capability in capabilities
            Capability(
                item = item,
                selected = isSelected,
                onSelectedChange = { selected ->
                    if (selected) onAdd(item.capability) else onRemove(item.capability)
                },
            )
        }
    }
}

@Composable
fun ModelCapabilitiesList(
    capabilities: List<LLMCapability>,
    onAdd: (LLMCapability) -> Unit,
    onRemove: (LLMCapability) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (item in LLMCapabilities) {
            CapabilityItem(
                capabilities = capabilities,
                item = item,
                onAdd = onAdd,
                onRemove = onRemove,
            )
        }
    }
}

@Composable
private fun CapabilityItem(
    capabilities: List<LLMCapability>,
    item: LLMCapabilityItem,
    onAdd: (LLMCapability) -> Unit,
    onRemove: (LLMCapability) -> Unit,
) {
    when (item) {
        is LLMCapabilityItem.Capability -> {
            val isSelected = item.capability in capabilities
            CapabilityListItem(
                item = item,
                selected = isSelected,
                onSelectedChange = { selected ->
                    if (selected) onAdd(item.capability) else onRemove(item.capability)
                },
            )
        }

        is LLMCapabilityItem.Category -> {
            Category(
                capabilities = capabilities,
                item = item,
                onAdd = onAdd,
                onRemove = onRemove,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CapabilityListItem(
    item: LLMCapabilityItem.Capability,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
) {
    ListItem(
        selected = selected,
        onClick = { onSelectedChange(!selected) },
        supportingContent = { Text(stringResource(item.description)) },
    ) {
        Text(stringResource(item.label))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Category(
    capabilities: List<LLMCapability>,
    item: LLMCapabilityItem.Category,
    onAdd: (LLMCapability) -> Unit,
    onRemove: (LLMCapability) -> Unit,
) {
    OutlinedCard {
        Column(Modifier.padding(8.dp)) {
            Text(stringResource(item.label), style = MaterialTheme.typography.labelMediumEmphasized)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(item.description), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            for (child in item.items) {
                CapabilityItem(
                    capabilities = capabilities,
                    item = child,
                    onAdd = onAdd,
                    onRemove = onRemove,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun Capability(
    item: LLMCapabilityItem.Capability,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
) {
    val colors =
        if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
    val border = if (selected) null else ButtonDefaults.outlinedButtonBorder()
    SplitButtonLayout(
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = { onSelectedChange(!selected) },
                colors = colors,
                border = border,
            ) {
                Text(stringResource(item.label))
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
                    colors = colors,
                    border = border,
                ) {
                    Icon(
                        Icons.AutoMirrored.Default.Help,
                        contentDescription = stringResource(R.string.label_help),
                    )
                }
            }
        },
    )
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
