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

package top.ltfan.knowmad.ui.component

import android.content.ClipData
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
import top.ltfan.knowmad.R

@Composable
fun SettingsIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    @StringRes contentDescriptionRes: Int? = R.string.label_settings,
    @StringRes tooltipTextRes: Int? = contentDescriptionRes,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = {
            tooltipTextRes?.let { PlainTooltip { Text(stringResource(it)) } }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        ) {
            Icon(
                painterResource(R.drawable.settings_24px),
                contentDescription = contentDescriptionRes?.let { stringResource(it) },
                modifier = iconModifier,
            )
        }
    }
}

@Composable
fun RetryIconButton(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    @StringRes contentDescriptionRes: Int? = R.string.label_retry,
    @StringRes tooltipTextRes: Int? = contentDescriptionRes,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = {
            tooltipTextRes?.let { PlainTooltip { Text(stringResource(it)) } }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onRetry,
            modifier = modifier,
            enabled = enabled,
        ) {
            Icon(
                painterResource(R.drawable.refresh_24px),
                contentDescription = contentDescriptionRes?.let { stringResource(it) },
                modifier = iconModifier,
            )
        }
    }
}

@Composable
fun DeleteIconButton(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    @StringRes contentDescriptionRes: Int? = R.string.label_delete,
    @StringRes tooltipTextRes: Int? = contentDescriptionRes,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = {
            tooltipTextRes?.let { PlainTooltip { Text(stringResource(it)) } }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onDelete,
            modifier = modifier,
            enabled = enabled,
        ) {
            Icon(
                painterResource(R.drawable.delete_24px),
                contentDescription = contentDescriptionRes?.let { stringResource(it) },
                modifier = iconModifier,
            )
        }
    }
}

@Composable
fun ImportIconButton(
    onImport: () -> Unit,
    @StringRes contentDescriptionRes: Int?,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    @StringRes tooltipTextRes: Int? = contentDescriptionRes,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = {
            tooltipTextRes?.let { PlainTooltip { Text(stringResource(it)) } }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onImport,
            modifier = modifier,
            enabled = enabled,
        ) {
            Icon(
                painterResource(R.drawable.file_open_24px),
                contentDescription = contentDescriptionRes?.let { stringResource(it) },
                modifier = iconModifier,
            )
        }
    }
}

@Composable
fun RunIconButton(
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    @StringRes contentDescriptionRes: Int? = R.string.label_run,
    @StringRes tooltipTextRes: Int? = contentDescriptionRes,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = {
            tooltipTextRes?.let { PlainTooltip { Text(stringResource(it)) } }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onRun,
            modifier = modifier,
            enabled = enabled,
        ) {
            Icon(
                painterResource(R.drawable.play_arrow_24px),
                contentDescription = contentDescriptionRes?.let { stringResource(it) },
                modifier = iconModifier,
            )
        }
    }
}

@Composable
fun CopyIconButton(
    onCopy: () -> Pair<CharSequence?, CharSequence>,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    @StringRes contentDescriptionRes: Int? = android.R.string.copy,
    @StringRes tooltipTextRes: Int? = contentDescriptionRes,
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = {
            tooltipTextRes?.let { PlainTooltip { Text(stringResource(it)) } }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = {
                coroutineScope.launch {
                    val (label, text) = onCopy()
                    val clipData = ClipData.newPlainText(label, text)
                    clipboard.setClipEntry(ClipEntry(clipData))
                }
            },
            modifier = modifier,
            enabled = enabled,
        ) {
            Icon(
                painterResource(R.drawable.content_copy_24px),
                contentDescription = contentDescriptionRes?.let { stringResource(it) },
                modifier = iconModifier,
            )
        }
    }
}

@Composable
fun PasteIconButton(
    onPaste: (text: String) -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    @StringRes contentDescriptionRes: Int? = android.R.string.paste,
    @StringRes tooltipTextRes: Int? = contentDescriptionRes,
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = {
            tooltipTextRes?.let { PlainTooltip { Text(stringResource(it)) } }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = {
                coroutineScope.launch {
                    val data = clipboard.getClipEntry()?.clipData
                    if (data?.itemCount == 0) {
                        return@launch
                    }
                    data?.getItemAt(0)?.coerceToText(context)?.toString()?.let {
                        onPaste(it)
                    }
                }
            },
            modifier = modifier,
            enabled = enabled,
        ) {
            Icon(
                painterResource(R.drawable.content_paste_24px),
                contentDescription = contentDescriptionRes?.let { stringResource(it) },
                modifier = iconModifier,
            )
        }
    }
}

@Composable
fun OpenUriIconButton(
    uri: String?,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = uri != null,
    @StringRes contentDescriptionRes: Int?,
    @StringRes tooltipTextRes: Int? = contentDescriptionRes,
) {
    val uriHandler = LocalUriHandler.current

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = {
            tooltipTextRes?.let { PlainTooltip { Text(stringResource(it)) } }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = { uri?.let { uriHandler.openUri(it) } },
            modifier = modifier,
            enabled = enabled,
        ) {
            Icon(
                painterResource(R.drawable.open_in_new_24px),
                contentDescription = contentDescriptionRes?.let { stringResource(it) },
                modifier = iconModifier,
            )
        }
    }
}

@Composable
fun ArrowDropUpIcon(
    modifier: Modifier = Modifier,
    @StringRes contentDescriptionRes: Int? = R.string.label_collapse,
) {
    Icon(
        painterResource(R.drawable.arrow_drop_up_24px),
        contentDescription = contentDescriptionRes?.let { stringResource(it) },
        modifier = modifier,
    )
}

@Composable
fun ArrowDropDownIcon(
    modifier: Modifier = Modifier,
    @StringRes contentDescriptionRes: Int? = R.string.label_expand,
) {
    Icon(
        painterResource(R.drawable.arrow_drop_down_24px),
        contentDescription = contentDescriptionRes?.let { stringResource(it) },
        modifier = modifier,
    )
}

@Composable
fun ExpandOrCollapseIconButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    @StringRes expandedContentDescriptionRes: Int = R.string.label_collapse,
    @StringRes expandedTooltipTextRes: Int = expandedContentDescriptionRes,
    @StringRes collapsedContentDescriptionRes: Int = R.string.label_expand,
    @StringRes collapsedTooltipTextRes: Int = collapsedContentDescriptionRes,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = {
            PlainTooltip {
                Text(
                    stringResource(
                        if (expanded) expandedTooltipTextRes
                        else collapsedTooltipTextRes,
                    ),
                )
            }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = { onExpandedChange(!expanded) },
            modifier = modifier,
            enabled = enabled,
        ) {
            val expandedRotation =
                if (LocalLayoutDirection.current == LayoutDirection.Ltr) -180f else 180f
            val expandIconRotation by animateFloatAsState(if (!expanded) 0f else expandedRotation)
            val collapseIconRotation by animateFloatAsState(if (expanded) 0f else -expandedRotation)
            AnimatedContent(
                targetState = expanded,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
            ) { expanded ->
                if (!expanded) {
                    ArrowDropDownIcon(
                        modifier = iconModifier.rotate(expandIconRotation),
                        contentDescriptionRes = collapsedContentDescriptionRes,
                    )
                } else {
                    ArrowDropUpIcon(
                        modifier = iconModifier.rotate(collapseIconRotation),
                        contentDescriptionRes = expandedContentDescriptionRes,
                    )
                }
            }
        }
    }
}

@Composable
fun ArrowBackIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    @StringRes contentDescriptionRes: Int? = R.string.label_back,
    @StringRes tooltipTextRes: Int? = contentDescriptionRes,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = {
            tooltipTextRes?.let { PlainTooltip { Text(stringResource(it)) } }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        ) {
            Icon(
                painterResource(R.drawable.arrow_back_24px),
                contentDescription = contentDescriptionRes?.let { stringResource(it) },
                modifier = iconModifier,
            )
        }
    }
}

@Composable
fun BackChevronIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    @StringRes contentDescriptionRes: Int? = R.string.label_back,
    @StringRes tooltipTextRes: Int? = contentDescriptionRes,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = {
            tooltipTextRes?.let { PlainTooltip { Text(stringResource(it)) } }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        ) {
            Icon(
                painterResource(R.drawable.chevron_backward_24px),
                contentDescription = contentDescriptionRes?.let { stringResource(it) },
                modifier = iconModifier,
            )
        }
    }
}

@Composable
fun ForwardChevronIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    @StringRes contentDescriptionRes: Int? = R.string.label_forward,
    @StringRes tooltipTextRes: Int? = contentDescriptionRes,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = {
            tooltipTextRes?.let { PlainTooltip { Text(stringResource(it)) } }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        ) {
            Icon(
                painterResource(R.drawable.chevron_forward_24px),
                contentDescription = contentDescriptionRes?.let { stringResource(it) },
                modifier = iconModifier,
            )
        }
    }
}

@Composable
fun CloseFullscreenIconButton(
    onClick: () -> Unit,
    @StringRes contentDescriptionRes: Int?,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    @StringRes tooltipTextRes: Int? = contentDescriptionRes,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = {
            tooltipTextRes?.let { PlainTooltip { Text(stringResource(it)) } }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        ) {
            Icon(
                painterResource(R.drawable.close_fullscreen_24px),
                contentDescription = contentDescriptionRes?.let { stringResource(it) },
                modifier = iconModifier,
            )
        }
    }
}

@Composable
fun CloseIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    @StringRes contentDescriptionRes: Int? = R.string.label_close,
    @StringRes tooltipTextRes: Int? = contentDescriptionRes,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = {
            tooltipTextRes?.let { PlainTooltip { Text(stringResource(it)) } }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        ) {
            Icon(
                painterResource(R.drawable.close_24px),
                contentDescription = contentDescriptionRes?.let { stringResource(it) },
                modifier = iconModifier,
            )
        }
    }
}

@Composable
fun AgentChatIcon(
    modifier: Modifier = Modifier,
    @StringRes contentDescriptionRes: Int? = R.string.agent_label_chat,
) {
    Icon(
        painterResource(R.drawable.chat_24px),
        contentDescription = contentDescriptionRes?.let { stringResource(it) },
        modifier = modifier,
    )
}

@Composable
fun AgentChatIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    @StringRes contentDescriptionRes: Int? = R.string.agent_label_chat,
    @StringRes tooltipTextRes: Int? = contentDescriptionRes,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = {
            tooltipTextRes?.let { PlainTooltip { Text(stringResource(it)) } }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        ) {
            AgentChatIcon(
                modifier = iconModifier,
                contentDescriptionRes = contentDescriptionRes,
            )
        }
    }
}

@Composable
fun GenerateSuggestionIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    @StringRes contentDescriptionRes: Int? = R.string.llm_task_generate_next_suggestion_user_label,
    @StringRes tooltipTextRes: Int? = contentDescriptionRes,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = {
            tooltipTextRes?.let { PlainTooltip { Text(stringResource(it)) } }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        ) {
            Icon(
                painterResource(R.drawable.wand_stars_24px),
                contentDescription = contentDescriptionRes?.let { stringResource(it) },
                modifier = iconModifier,
            )
        }
    }
}
