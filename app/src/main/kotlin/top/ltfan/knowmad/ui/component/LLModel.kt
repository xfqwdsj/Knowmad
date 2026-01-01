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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import top.ltfan.knowmad.R
import top.ltfan.knowmad.ui.theme.TextFieldMaxWidth

@Composable
fun LLModelTextField(
    state: TextFieldState,
    knownModelIds: List<String>,
    inputTransformation: InputTransformation? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    AutoSuggestTextField(
        state = state,
        options = knownModelIds,
        allowExpansion = expanded,
        onExpandedChange = { expanded = it },
    ) { expanded ->
        TextField(
            state = state,
            modifier = Modifier
                .widthIn(max = TextFieldMaxWidth)
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
            label = {
                Text(stringResource(R.string.llm_model_label_id))
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded,
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable),
                    )
                    PasteIconButton(
                        onPaste = {
                            state.setTextAndPlaceCursorAtEnd(it.trim())
                        },
                    )
                }
            },
            inputTransformation = inputTransformation,
            lineLimits = TextFieldLineLimits.SingleLine,
        )
    }
}

@Composable
fun LLMContextLengthTextField(
    contextLength: Long?,
    onContextLengthChange: (Long) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    TextField(
        value = contextLength?.toString()
            .takeIf { it != "0" || !focused } ?: "",
        onValueChange = {
            onContextLengthChange(it.trim().toLongOrNull() ?: 0)
        },
        modifier = Modifier
            .widthIn(max = TextFieldMaxWidth)
            .fillMaxWidth()
            .onFocusEvent {
                focused = it.isFocused
            },
        label = {
            Text(stringResource(R.string.llm_context_length_label))
        },
        trailingIcon = {
            PasteIconButton(
                onPaste = {
                    onContextLengthChange(it.trim().toLongOrNull() ?: return@PasteIconButton)
                },
            )
        },
        placeholder = { Text("0") },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
        ),
    )
}

@Composable
fun LLMMaxOutputTokensTextField(
    maxOutputTokens: Long?,
    onMaxOutputTokensChange: (Long?) -> Unit,
) {
    TextField(
        value = maxOutputTokens?.toString() ?: "",
        onValueChange = {
            onMaxOutputTokensChange(it.trim().toLongOrNull())
        },
        modifier = Modifier
            .widthIn(max = TextFieldMaxWidth)
            .fillMaxWidth(),
        label = {
            Text(stringResource(R.string.llm_max_output_tokens_label))
        },
        trailingIcon = {
            PasteIconButton(
                onPaste = {
                    onMaxOutputTokensChange(it.trim().toLongOrNull() ?: return@PasteIconButton)
                },
            )
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
        ),
        singleLine = true,
    )
}

@Composable
fun LLMNameTextField(
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
            Text(stringResource(R.string.llm_model_label_name))
        },
        singleLine = true,
    )
}
