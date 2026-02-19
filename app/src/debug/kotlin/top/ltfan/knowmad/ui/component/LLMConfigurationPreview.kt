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

import ai.koog.prompt.llm.LLModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.ltfan.knowmad.data.database.AppDatabase
import top.ltfan.knowmad.data.llm.LLMConfigEntity
import top.ltfan.knowmad.data.llm.LLMProviderConfigEntity
import top.ltfan.knowmad.data.llm.SupportedLLMProviders
import top.ltfan.knowmad.ui.util.AppWindowInsets
import top.ltfan.knowmad.ui.util.only
import top.ltfan.knowmad.ui.util.plus

@Preview
@Composable
fun LLMProviderConfigLazyColumnPreview() {
    ApplicationPreview {
        val coroutineScope = rememberCoroutineScope()
        val dao = remember { AppDatabase.get().llmConfigDao() }

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.windowInsetsPadding(AppWindowInsets.only { top }),
            ) {
                Button(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            SupportedLLMProviders.keys.forEach { provider ->
                                dao.insertProviderAtEnd(
                                    LLMProviderConfigEntity(
                                        provider = provider,
                                        apiKey = "sample_api_key_for_${provider.id}".toByteArray(),
                                        iv = null,
                                    ),
                                )
                            }
                        }
                    },
                ) {
                    Text("Add Sample Data")
                }
                Button(
                    onClick = {},
                ) {
                    Text("Clear All")
                }
            }
            LLMProviderConfigLazyColumn(
                dao = dao,
                state = rememberLLMProviderConfigLazyListState(
                    entitiesFactory = { dao.getAllProviders() },
                ),
                modelState = rememberLLMConfigLazyListStateFactory(dao),
                onEditProvider = {},
                onDeleteProvider = {
                    coroutineScope.launch(Dispatchers.IO) {
                        dao.deleteProvider(it)
                    }
                },
                onEditModel = { _, _ -> },
                onDeleteModel = {
                    coroutineScope.launch(Dispatchers.IO) {
                        dao.deleteModel(it)
                    }
                },
                onAddModel = {
                    coroutineScope.launch(Dispatchers.IO) {
                        dao.insertModelAtEnd(
                            LLMConfigEntity(
                                providerConfigId = it.id,
                                model = LLModel(
                                    provider = it.provider,
                                    id = "sample-model-for-${it.provider.id}",
                                    capabilities = listOf(),
                                    contextLength = 1024,
                                ),
                            ),
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = AppWindowInsets.only { horizontal + bottom }
                    .asPaddingValues() + PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            )
        }
    }
}
