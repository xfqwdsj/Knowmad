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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousCapsule
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.WebViewState
import com.multiplatform.webview.web.rememberWebViewNavigator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import top.ltfan.knowmad.R
import top.ltfan.knowmad.ui.util.AppWindowInsets
import top.ltfan.knowmad.ui.util.minus
import top.ltfan.knowmad.ui.util.only
import kotlin.time.Duration.Companion.seconds

@Composable
fun WebViewScaffold(
    state: WebViewState,
    modifier: Modifier = Modifier,
    navigator: WebViewNavigator = rememberWebViewNavigator(),
    urlTextFieldState: TextFieldState = rememberTextFieldState(state.lastLoadedUrl ?: ""),
    topBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    captureBackPresses: Boolean = true,
    windowInsets: WindowInsets = AppWindowInsets,
    onClose: () -> Unit,
) {
    var progressBarHeight by remember { mutableIntStateOf(0) }

    val progress by rememberAnimatedWebViewLoadingProgressAsState(state)

    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = progress in 0.0f..0.9f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged {
                            progressBarHeight = it.height
                        },
                    enter = fadeIn() + expandVertically(
                        expandFrom = Alignment.Top,
                        clip = false,
                    ),
                    exit = fadeOut() + shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        clip = false,
                    ),
                ) {
                    LinearWavyProgressIndicator(
                        { progress },
                        modifier = Modifier
                            .windowInsetsPadding(windowInsets.only { horizontal })
                            .fillMaxWidth(),
                    )
                }
                WebViewBottomController(
                    state = state,
                    navigator = navigator,
                    onClose = onClose,
                    urlTextFieldState = urlTextFieldState,
                    windowInsets = windowInsets,
                )
            }
        },
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = windowInsets.only { horizontal + top },
    ) { contentPadding ->
        WebView(
            state = state,
            modifier = Modifier
                .padding(
                    with(LocalDensity.current) {
                        contentPadding - PaddingValues(bottom = progressBarHeight.toDp())
                    },
                )
                .fillMaxSize(),
            captureBackPresses = captureBackPresses,
            navigator = navigator,
        )
    }
}

@Composable
fun WebViewBottomController(
    state: WebViewState,
    navigator: WebViewNavigator,
    onClose: () -> Unit,
    urlTextFieldState: TextFieldState = rememberTextFieldState(state.lastLoadedUrl ?: ""),
    windowInsets: WindowInsets = AppWindowInsets,
) {
    val focusManager = LocalFocusManager.current

    val urlEditingChannel = remember { Channel<Unit>(Channel.CONFLATED) }

    var expectedUrlActions by remember { mutableStateOf(false) }
    var textFieldFocused by remember { mutableStateOf(false) }

    val isEditing by remember {
        derivedStateOf {
            expectedUrlActions || textFieldFocused
        }
    }

    FlexibleBottomAppBar(
        windowInsets = windowInsets.only { horizontal + bottom },
    ) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                TooltipAnchorPosition.Above,
            ),
            tooltip = { PlainTooltip { Text(stringResource(R.string.label_close)) } },
            state = rememberTooltipState(),
        ) {
            IconButton(
                onClick = {
                    if (isEditing) {
                        expectedUrlActions = false
                        focusManager.moveFocus(FocusDirection.Up)
                        return@IconButton
                    }
                    onClose()
                },
            ) {
                Icon(
                    painterResource(R.drawable.close_24px),
                    contentDescription = stringResource(R.string.label_close),
                )
            }
        }
        TextField(
            state = urlTextFieldState,
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .onFocusEvent {
                    textFieldFocused = it.isFocused
                },
            textStyle = MaterialTheme.typography.bodySmallEmphasized,
            inputTransformation = InputTransformation {
                if (changes.changeCount == 0) return@InputTransformation
                expectedUrlActions = true
                urlEditingChannel.trySend(Unit)
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
            ),
            onKeyboardAction = KeyboardActionHandler {
                navigator.loadUrl(urlTextFieldState.text.toString())
                expectedUrlActions = false
            },
            lineLimits = TextFieldLineLimits.SingleLine,
            shape = ContinuousCapsule,
            contentPadding = PaddingValues(8.dp),
        )
        AnimatedVisibility(
            visible = isEditing,
            enter = fadeIn() + expandHorizontally(clip = false),
            exit = fadeOut() + shrinkHorizontally(clip = false),
        ) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above,
                ),
                tooltip = {
                    PlainTooltip {
                        Text(stringResource(R.string.label_go))
                    }
                },
                state = rememberTooltipState(),
            ) {
                IconButton(
                    onClick = {
                        navigator.loadUrl(urlTextFieldState.text.toString())
                        expectedUrlActions = false
                        focusManager.moveFocus(FocusDirection.Up)
                    },
                    enabled = isEditing,
                ) {
                    Icon(
                        painterResource(R.drawable.check_24px),
                        contentDescription = stringResource(R.string.label_go),
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = !isEditing,
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
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above,
                    ),
                    tooltip = { PlainTooltip { Text(stringResource(R.string.label_reload)) } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(
                        onClick = { navigator.reload() },
                    ) {
                        Icon(
                            painterResource(R.drawable.refresh_24px),
                            contentDescription = stringResource(R.string.label_reload),
                        )
                    }
                }
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above,
                    ),
                    tooltip = { PlainTooltip { Text(stringResource(R.string.label_back)) } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(
                        onClick = { navigator.navigateBack() },
                        enabled = navigator.canGoBack,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back_24px),
                            contentDescription = stringResource(R.string.label_back),
                        )
                    }
                }
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above,
                    ),
                    tooltip = { PlainTooltip { Text(stringResource(R.string.label_forward)) } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(
                        onClick = { navigator.navigateForward() },
                        enabled = navigator.canGoForward,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_forward_24px),
                            contentDescription = stringResource(R.string.label_forward),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(state.lastLoadedUrl) {
        if (!isEditing) {
            urlTextFieldState.setTextAndPlaceCursorAtEnd(state.lastLoadedUrl ?: "")
        }
    }

    LaunchedEffect(Unit) {
        for (_ in urlEditingChannel) {
            delay(5.seconds)
            if (!expectedUrlActions) continue
            expectedUrlActions = false
        }
    }

    LaunchedEffect(expectedUrlActions, textFieldFocused) {
        if (isEditing) return@LaunchedEffect
        if (urlTextFieldState.text.toString() == state.lastLoadedUrl) return@LaunchedEffect
        urlTextFieldState.setTextAndPlaceCursorAtEnd(state.lastLoadedUrl ?: "")
    }
}

@Composable
fun rememberAnimatedWebViewLoadingProgressAsState(
    state: WebViewState,
): State<Float> {
    val progress = remember(state) { Animatable(0f) }

    LaunchedEffect(state.loadingState) {
        val loadingState = state.loadingState
        if (loadingState is LoadingState.Initializing) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        val targetProgress = when (loadingState) {
            is LoadingState.Loading -> loadingState.progress
            is LoadingState.Finished -> 1.0f
        }
        progress.animateTo(targetProgress)
    }

    return progress.asState()
}
