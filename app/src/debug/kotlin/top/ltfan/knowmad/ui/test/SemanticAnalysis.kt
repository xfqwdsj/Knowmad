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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.launch
import top.ltfan.knowmad.accessibility.semantic.SemanticAnalysisService
import top.ltfan.knowmad.accessibility.semantic.requestEnableAccessibilityService
import top.ltfan.knowmad.ui.theme.AppTheme
import top.ltfan.knowmad.ui.util.AppWindowInsets
import top.ltfan.knowmad.util.Json

private val json = Json {
    prettyPrint = true
    prettyPrintIndent = " "
}

@Preview
@Composable
fun SemanticAnalysis() {
    if (LocalInspectionMode.current) return

    AppTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.windowInsetsPadding(AppWindowInsets)) {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                var uiTree by remember { mutableStateOf("") }

                Row {
                    Button(
                        onClick = { context.requestEnableAccessibilityService<SemanticAnalysisService>() },
                        content = { Text("Enable Accessibility Service") },
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                uiTree = json.encodeToString(SemanticAnalysisService.getUiTree())
                            }
                        },
                        content = { Text("Get UI Tree") },
                    )
                }

                Text(
                    text = uiTree,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                    fontFamily = Monospace,
                )
            }
        }
    }
}
