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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.llm.LLMProviderInfo
import top.ltfan.knowmad.ui.theme.TextFieldMaxWidth

@Composable
fun LLMProviderInfo(
    info: LLMProviderInfo,
    modifier: Modifier = Modifier,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        leadingContent = {
            LLMProviderIcon(info)
        },
        supportingContent = {
            Text(stringResource(info.description))
        },
    ) {
        Text(stringResource(info.label))
    }
}

@Composable
fun LLMProviderNameTextField(
    name: String,
    onNameChange: (String) -> Unit,
) {
    TextField(
        value = name,
        onValueChange = { onNameChange(it) },
        modifier = Modifier
            .widthIn(max = TextFieldMaxWidth)
            .fillMaxWidth(),
        label = {
            Text(stringResource(R.string.llm_provider_label_name))
        },
        singleLine = true,
    )
}

@Composable
fun LLMProviderApiKeyTextField(
    state: TextFieldState,
    isUsingPlaintext: Boolean,
    providerInfo: LLMProviderInfo?,
    onRetryCryptoKeyInitialization: () -> Unit,
    notChanged: Boolean = false,
) {
    SecureTextField(
        state = state,
        modifier = Modifier
            .widthIn(max = TextFieldMaxWidth)
            .fillMaxWidth(),
        label = {
            Text(stringResource(R.string.llm_api_key_label))
        },
        placeholder = if (!notChanged) null
        else {
            {
                Text(stringResource(R.string.label_not_changed))
            }
        },
        leadingIcon = {
            Icon(
                painterResource(if (isUsingPlaintext) R.drawable.encrypted_off_24px else R.drawable.encrypted_24px),
                contentDescription = null,
            )
        },
        trailingIcon = {
            Row {
                PasteIconButton(
                    onPaste = {
                        state.setTextAndPlaceCursorAtEnd(it.trim())
                    },
                )
                providerInfo?.let { providerInfo ->
                    OpenUriIconButton(
                        uri = providerInfo.platformUrl,
                        contentDescriptionRes = R.string.llm_api_key_guidance_get,
                    )
                }
            }
        },
        supportingText = {
            Row {
                AnimatedContent(
                    targetState = isUsingPlaintext,
                    modifier = Modifier.weight(1f),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                ) { isUsingPlaintext ->
                    Text(
                        stringResource(
                            if (isUsingPlaintext) R.string.llm_api_key_message_unsecure
                            else R.string.llm_api_key_message_secure,
                        ),
                    )
                }
                AnimatedVisibility(
                    visible = isUsingPlaintext,
                    enter = fadeIn() + expandHorizontally(
                        expandFrom = Alignment.Start,
                        clip = false,
                    ),
                    exit = fadeOut() + shrinkHorizontally(
                        shrinkTowards = Alignment.Start,
                        clip = false,
                    ),
                ) {
                    Row {
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = onRetryCryptoKeyInitialization,
                        ) {
                            Text(stringResource(R.string.crypto_key_initialization_error_retry_label))
                        }
                    }
                }
            }
        },
    )
}

@Composable
fun LLMProviderBaseUrlTextField(
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    providerInfo: LLMProviderInfo?,
) {
    TextField(
        value = baseUrl,
        onValueChange = { onBaseUrlChange(it.trim()) },
        modifier = Modifier
            .widthIn(max = TextFieldMaxWidth)
            .fillMaxWidth(),
        label = {
            Text(stringResource(R.string.llm_api_base_url_input_label))
        },
        placeholder = providerInfo?.let { providerInfo ->
            {
                Text(providerInfo.defaultBaseUrl)
            }
        },
        trailingIcon = {
            PasteIconButton(onPaste = { onBaseUrlChange(it.trim()) })
        },
        supportingText = {
            Text(stringResource(R.string.llm_api_base_url_input_message))
        },
        singleLine = true,
    )
}

@Composable
fun LLMProviderIcon(
    info: LLMProviderInfo,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier,
    ) {
        Icon(
            painterResource(info.icon),
            contentDescription = null,
            modifier = Modifier
                .padding(12.dp)
                .size(32.dp),
        )
    }
}
