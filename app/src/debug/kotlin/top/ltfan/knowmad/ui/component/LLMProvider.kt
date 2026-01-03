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

import ai.koog.prompt.llm.LLMProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import top.ltfan.knowmad.data.llm.SupportedLLMProviders
import top.ltfan.knowmad.ui.theme.AppTheme

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
