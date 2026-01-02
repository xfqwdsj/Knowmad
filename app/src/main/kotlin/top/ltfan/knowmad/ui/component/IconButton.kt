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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import top.ltfan.knowmad.R

@Composable
fun RetryIconButton(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    @StringRes tooltipTextRes: Int? = R.string.label_retry,
    @StringRes contentDescriptionRes: Int? = tooltipTextRes,
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
