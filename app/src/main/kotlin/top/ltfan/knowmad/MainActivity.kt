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

package top.ltfan.knowmad

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation3.ui.NavDisplay
import top.ltfan.knowmad.activity.KnowmadActivity
import top.ltfan.knowmad.ui.page.expanded
import top.ltfan.knowmad.ui.theme.AppTheme
import top.ltfan.knowmad.ui.viewmodel.AppViewModel

class MainActivity : KnowmadActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            with(viewModel) {
                AppTheme {
                    NavDisplay(
                        backStack = backStack.expanded,
                        entryProvider = { it.navEntry(PaddingValues()) },
                    )
                }
//                AppTheme {
//                    val testStack = remember { mutableStateListOf(1) }
//                    NavDisplay(
//                        backStack = SnapshotStateList<Int>().apply {
//                            addAll(testStack)
//                        },
//                        entryProvider = entryProvider {
//                            entry<Int> { key ->
//                                Column(
//                                    modifier = Modifier
//                                        .fillMaxSize()
//                                        .background(Color.White)
//                                        .windowInsetsPadding(AppWindowInsets),
//                                ) {
//                                    Text("Test Page: $key")
//                                    Button(
//                                        onClick = {
//                                            testStack.add(key + 1)
//                                        },
//                                    ) {
//                                        Text("Add Page")
//                                    }
////                                    val testSubStack = remember { mutableStateListOf(1) }
////                                    NavDisplay(
////                                        backStack = testSubStack,
////                                        entryProvider = entryProvider {
////                                            entry<Int> { subKey ->
////                                                Column(Modifier.background(Color.Blue)) {
////                                                    Text("  Sub Page: $subKey")
////                                                    Button(
////                                                        onClick = {
////                                                            testSubStack.add(subKey + 1)
////                                                        },
////                                                    ) {
////                                                        Text("  Add Sub Page")
////                                                    }
////                                                }
////                                            }
////                                        },
////                                    )
//                                    val steps = listOf(
//                                        StepItem("Welcome"),
//                                        StepItem("Provider", "Select one"),
//                                        StepItem("Credentials", "Setup key"),
//                                        StepItem("Verify", "Check info"),
//                                        StepItem("Verify", "Check info"),
//                                        StepItem("Verify", "Check info"),
//                                        StepItem(
//                                            "Verify",
//                                            "Check info info info info info info info info info info info info",
//                                        ),
//                                        StepItem("Finish"),
//                                    )
//
//                                    // 模拟状态
//                                    var currentStep by remember { mutableIntStateOf(2) }
//
//                                    Stepper(
//                                        steps = steps,
//                                        currentStep = currentStep,
//                                        allowUserScroll = true, // 允许用户手动滑动查看
//                                    )
//
//                                    Spacer(modifier = Modifier.height(40.dp))
//
//                                    // 控制按钮
//                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
//                                        OutlinedButton(
//                                            onClick = { if (currentStep > 0) currentStep-- },
//                                            enabled = currentStep > 0,
//                                        ) {
//                                            Text("Back")
//                                        }
//
//                                        Button(
//                                            onClick = { if (currentStep < steps.size - 1) currentStep++ },
//                                            enabled = currentStep < steps.size - 1,
//                                        ) {
//                                            Text("Next")
//                                        }
//                                    }
//                                }
//                            }
//                        },
//                    )
//                }
            }
        }
    }
}
