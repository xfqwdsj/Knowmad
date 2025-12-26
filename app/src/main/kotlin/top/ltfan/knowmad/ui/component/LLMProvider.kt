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

import ai.koog.prompt.llm.LLMProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.ltfan.knowmad.data.llm.LLMProviderInfo
import top.ltfan.knowmad.data.llm.SupportedLLMProviders
import top.ltfan.knowmad.ui.theme.AppTheme
import top.ltfan.knowmad.ui.theme.ProvideCompatibleShapes
import top.ltfan.knowmad.ui.theme.ProvideShapes

@Composable
fun LLMProviderInfo(
    info: LLMProviderInfo,
    modifier: Modifier = Modifier,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ProvideCompatibleShapes {
        ListItem(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            leadingContent = {
                ProvideShapes {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            painterResource(info.icon),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(36.dp),
                        )
                    }
                }
            },
            supportingContent = {
                Text(stringResource(info.description))
            },
        ) {
            Text(stringResource(info.label))
        }
    }
}

@Preview
@Composable
fun LLMProviderInfoPreview() {
    AppTheme {
        Column {
            LLMProviderInfo(
                info = SupportedLLMProviders[LLMProvider.DeepSeek]!!,
                checked = true,
                onCheckedChange = {},
            )
            LLMProviderInfo(
                info = SupportedLLMProviders[LLMProvider.OpenAI]!!,
                checked = false,
                onCheckedChange = {},
            )
        }
    }
}
