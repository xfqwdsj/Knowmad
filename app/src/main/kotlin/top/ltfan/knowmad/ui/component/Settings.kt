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

package top.ltfan.knowmad.ui.component

import android.icu.text.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemElevation
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.launch
import top.ltfan.knowmad.ui.util.contractColorFor
import top.ltfan.knowmad.ui.util.detectPointerFirstDown
import top.ltfan.knowmad.ui.util.rememberOffsetToDpOffset
import java.util.Calendar

@Composable
fun SettingsItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    shapes: ListItemShapes = ListItemDefaults.shapes(),
    colors: ListItemColors = ListItemDefaults.colors(containerColor = Transparent),
    elevation: ListItemElevation = ListItemDefaults.elevation(),
    contentPadding: PaddingValues = ListItemDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    ListItem(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        leadingContent = leadingContent,
        trailingContent = trailingContent?.let {
            {
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                    content = it,
                )
            }
        },
        overlineContent = overlineContent,
        supportingContent = supportingContent,
        verticalAlignment = verticalAlignment,
        onLongClick = onLongClick,
        onLongClickLabel = onLongClickLabel,
        shapes = shapes,
        colors = colors,
        elevation = elevation,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun SettingItemDropdown(
    title: String,
    selectedValue: String,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    overlineContent: (@Composable (() -> Unit))? = null,
    summary: String? = null,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    shapes: ListItemShapes = ListItemDefaults.shapes(),
    colors: ListItemColors = ListItemDefaults.colors(containerColor = Transparent),
    elevation: ListItemElevation = ListItemDefaults.elevation(),
    contentPadding: PaddingValues = ListItemDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    menuContent: @Composable ColumnScope.() -> Unit,
) {
    val menuOffset = rememberOffsetToDpOffset()

    Box {
        SettingsItem(
            onClick = { onShowMenuChange(true) },
            modifier = modifier.detectPointerFirstDown(pass = Initial) {
                menuOffset.originalOffset = it.position
            },
            enabled = enabled,
            trailingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(selectedValue)
                    ArrowDropDownIcon()
                }
            },
            overlineContent = overlineContent,
            supportingContent = summary?.let { { Text(it) } },
            verticalAlignment = verticalAlignment,
            shapes = shapes,
            colors = colors,
            elevation = elevation,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = { Text(title) },
        )

        Box {
            val offset by menuOffset.offsetFlow.collectAsState(Zero)
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { onShowMenuChange(false) },
                offset = offset,
                content = menuContent,
            )
        }
    }
}

@Composable
fun SettingsItemSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    overlineContent: (@Composable (() -> Unit))? = null,
    summary: String? = null,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    shapes: ListItemShapes = ListItemDefaults.shapes(),
    colors: ListItemColors = ListItemDefaults.colors(containerColor = Transparent),
    elevation: ListItemElevation = ListItemDefaults.elevation(),
    contentPadding: PaddingValues = ListItemDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
) {
    SettingsItem(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        enabled = enabled,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
        overlineContent = overlineContent,
        supportingContent = summary?.let { { Text(it) } },
        verticalAlignment = verticalAlignment,
        shapes = shapes,
        colors = colors,
        elevation = elevation,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = { Text(title) },
    )
}

@Composable
fun SettingsItemTimePicker(
    title: String,
    state: TimePickerState,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    locale: Locale = LocalLocale.current,
    format: DateFormat = remember(locale) {
        DateFormat.getInstanceForSkeleton("jm", locale.platformLocale)
    },
    overlineContent: (@Composable (() -> Unit))? = null,
    summary: String? = null,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    shapes: ListItemShapes = ListItemDefaults.shapes(),
    colors: ListItemColors = ListItemDefaults.colors(containerColor = Transparent),
    elevation: ListItemElevation = ListItemDefaults.elevation(),
    contentPadding: PaddingValues = ListItemDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
) {
    var showDialog by remember { mutableStateOf(false) }

    SettingsItem(
        onClick = { showDialog = true },
        modifier = modifier,
        enabled = enabled,
        trailingContent = {
            val currentTime = remember(format, state.hour, state.minute) {
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, state.hour)
                    set(Calendar.MINUTE, state.minute)
                }
                format.format(calendar.time)
            }

            Text(currentTime)
        },
        overlineContent = overlineContent,
        supportingContent = summary?.let { { Text(it) } },
        verticalAlignment = verticalAlignment,
        shapes = shapes,
        colors = colors,
        elevation = elevation,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = { Text(title) },
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm()
                        showDialog = false
                    },
                    content = { Text(stringResource(android.R.string.ok)) },
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false },
                    content = { Text(stringResource(android.R.string.cancel)) },
                )
            },
            title = { Text(title) },
            text = { TimePicker(state) },
        )

        SideEffect {
            state.selection = Hour
        }
    }
}

@Composable
fun SettingsBadge(
    text: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = contractColorFor(color),
) {
    if (summary == null) {
        PlainSettingsBadge(
            text = text,
            modifier = modifier,
            color = color,
            contentColor = contentColor,
        )
        return
    }

    val tooltipState = rememberTooltipState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(summary) } },
        state = tooltipState,
    ) {
        val coroutineScope = rememberCoroutineScope()
        PlainSettingsBadge(
            text = text,
            onClick = { coroutineScope.launch { tooltipState.show() } },
            color = color,
            contentColor = contentColor,
        )
    }
}

@Composable
private fun PlainSettingsBadge(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = contractColorFor(color),
) {
    Box(
        modifier = modifier
            .clip(ContinuousCapsule)
            .background(color)
            .run { if (onClick != null) clickable(onClick = onClick) else this }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
