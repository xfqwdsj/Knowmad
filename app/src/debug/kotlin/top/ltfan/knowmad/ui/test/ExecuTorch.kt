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

package top.ltfan.knowmad.ui.test

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import top.ltfan.knowmad.agent.client.Downloader
import top.ltfan.knowmad.agent.client.executorch.ExecuTorchClient
import top.ltfan.knowmad.ui.component.ApplicationPreview
import kotlin.math.sqrt

@Preview
@Composable
fun ExecuTorch() {
    val context = LocalContext.current
    val basePath = remember { context.filesDir.toOkioPath() }
    val client = remember { ExecuTorchClient(ModelScope, basePath) }

    var selectedModel by remember { mutableStateOf(client.modelInfos.keys.first()) }

    var isDownloading by remember { mutableStateOf(false) }

    ApplicationPreview {
        Scaffold(
            topBar = { TopAppBar(title = { Text("ExecuTorch") }) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding),
            ) {
                Text("Selected model: ${selectedModel.id}")
                Text("Available models:")
                client.modelInfos.keys.forEach { model ->
                    Text(model.id)
                    TextButton(onClick = { selectedModel = model }) {
                        Text("Select")
                    }
                }
                run {
                    val coroutineScope = rememberCoroutineScope()

                    var result by remember { mutableStateOf<Downloader.ValidationResult?>(null) }

                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                isDownloading = true
                                try {
                                    client.modelInfos[selectedModel]?.downloader?.download(
                                        ModelScope,
                                        basePath,
                                    )
                                } catch (e: Throwable) {
                                    e.printStackTrace()
                                    result = Downloader.ValidationResult.NotExisting(
                                        e.message ?: "Unknown error",
                                    )
                                } finally {
                                    isDownloading = false
                                }
                            }
                        },
                        content = { Text("Download") },
                        enabled = !isDownloading,
                    )
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                result = client.modelInfos[selectedModel]?.downloader?.validate(
                                    ModelScope,
                                    basePath,
                                    SizeAndHash,
                                )
                            }
                        },
                        content = { Text("Validate") },
                    )
                    Text("${result ?: "Not validated yet"}")
                }
                run {
                    val coroutineScope = rememberCoroutineScope()

                    var input1 by remember { mutableStateOf("Hello, world!") }
                    var input2 by remember { mutableStateOf("How are you?") }

                    var result1 by remember { mutableStateOf<List<Double>?>(null) }
                    var result2 by remember { mutableStateOf<List<Double>?>(null) }

                    var similarity by remember { mutableStateOf<Double?>(null) }

                    TextButton(
                        onClick = {
                            coroutineScope.launch(Dispatchers.Default) {
                                try {
                                    result1 = client.embed(input1, selectedModel)
                                } catch (e: Throwable) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        content = { Text("Embed 1") },
                    )
                    Text("Embedding result: $result1", maxLines = 3)
                    OutlinedTextField(
                        value = input1,
                        onValueChange = { input1 = it },
                        label = { Text("Input 1") },
                    )

                    TextButton(
                        onClick = {
                            coroutineScope.launch(Dispatchers.Default) {
                                try {
                                    result2 = client.embed(input2, selectedModel)
                                } catch (e: Throwable) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        content = { Text("Embed 2") },
                    )
                    Text("Embedding result: $result2", maxLines = 3)
                    OutlinedTextField(
                        value = input2,
                        onValueChange = { input2 = it },
                        label = { Text("Input 2") },
                    )

                    TextButton(
                        onClick = {
                            val result1 = result1
                            val result2 = result2
                            if (result1 != null && result2 != null) {
                                similarity = calculateCosineSimilarity(result1, result2)
                            }
                        },
                        content = { Text("Calculate Similarity") },
                    )
                    Text("${similarity ?: "Not calculated yet"}")
                }
            }
        }
    }
}

private fun calculateCosineSimilarity(vec1: List<Double>, vec2: List<Double>): Double {
    require(vec1.size == vec2.size) { "Vectors must be of the same size" }

    var dot = 0.0
    var norm1 = 0.0
    var norm2 = 0.0

    for (i in vec1.indices) {
        val x = vec1[i]
        val y = vec2[i]
        dot += x * y
        norm1 += x * x
        norm2 += y * y
    }

    val denominator = sqrt(norm1) * sqrt(norm2)
    return if (denominator > 0) dot / denominator else 0.0
}
