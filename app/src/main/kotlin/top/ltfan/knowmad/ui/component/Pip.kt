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

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import top.ltfan.knowmad.R
import top.ltfan.knowmad.ui.theme.AppExtraSmallShape
import top.ltfan.knowmad.ui.viewmodel.LocalAgentViewModel
import top.ltfan.knowmad.ui.viewmodel.LocalAppViewModel
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Composable
fun PictureInPicture() {
    val appViewModel = LocalAppViewModel.current
    val agentViewModel = LocalAgentViewModel.current

    val density = LocalDensity.current

    val conversation by agentViewModel.currentConversationFlow.collectAsState(null)
    val messages = agentViewModel.currentMessagesFlow?.collectAsLazyPagingItems()

    Surface {
        Column(Modifier.fillMaxSize()) {
            Surface(
                shape = AppExtraSmallShape.copy(
                    topStart = CornerSize(0.dp),
                    topEnd = CornerSize(0.dp),
                ),
                tonalElevation = 4.dp,
            ) {
                Text(
                    conversation?.name ?: stringResource(R.string.agent_conversation_label_new),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    textAlign = Center,
                    overflow = Ellipsis,
                    softWrap = false,
                    maxLines = 1,
                )
            }
            val newDensity = remember(density) {
                Density(
                    density = density.density * .65f,
                    fontScale = density.fontScale,
                )
            }
            CompositionLocalProvider(LocalDensity provides newDensity) {
                AnimatedContent(
                    targetState = agentViewModel.selectedModelId == null,
                    transitionSpec = { fadeIn() togetherWith fadeOut() using null },
                ) { modelNotSelected ->
                    if (modelNotSelected) {
                        HintColumn {
                            HintIconBackground { HintIcon(R.drawable.error_24px) }
                            Text(stringResource(R.string.companion_mode_label_model_not_selected))
                        }
                        return@AnimatedContent
                    }
                    Box(Modifier.weight(1f)) {
                        if (messages != null) {
                            val state = rememberLazyListState()

                            ChatMessageList(
                                getMessageCount = { messages.itemCount },
                                getMessageKey = messages.itemKey { it.key },
                                getMessageAt = { agentViewModel.getMessage(messages[it]) },
                                mathJaxRendererState = appViewModel.mathJaxRendererState,
                                modifier = Modifier.fillMaxSize(),
                                initialReasoningVisibility = false,
                                initialToolVisibility = false,
                                lazyListState = state,
                                assistantMessageStates = agentViewModel.assistantMessageStates,
                                allowAssistantMessageActions = false,
                            )

                            LaunchedEffect(state, agentViewModel.canSendMessage) {
                                if (agentViewModel.canSendMessage) return@LaunchedEffect
                                snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }.collect {
                                    state.requestScrollToItem(0)
                                }
                            }

                            LaunchedEffect(state) {
                                var lastTime: Instant? = null

                                val backJob = SupervisorJob(coroutineContext.job)
                                val backScope = this + backJob

                                val baseDuration = 1.seconds
                                val stepDuration = 0.5.seconds
                                val resetTime = 2.seconds
                                val backThreshold = 5.seconds

                                for (_ in agentViewModel.pipScrollUpEvents) {
                                    backJob.cancelChildren()
                                    val now = Clock.System.now()
                                    val delta = if (lastTime == null) {
                                        baseDuration - stepDuration
                                    } else {
                                        now - lastTime
                                    }
                                    lastTime = now

                                    val step = state.layoutInfo.viewportSize.height / 2

                                    val minimumValue = step * -1f
                                    val maximumValue = step * 3f

                                    val value =
                                        ((baseDuration - delta) / stepDuration * step).toFloat()
                                            .fastCoerceAtLeast(minimumValue)
                                            .fastCoerceAtMost(maximumValue)

                                    backScope.launch {
                                        delay(resetTime)
                                        lastTime = null
                                    }

                                    backScope.launch {
                                        delay(backThreshold)
                                        state.animateScrollToItem(0)
                                    }

                                    launch {
                                        state.animateScrollBy(value)
                                    }
                                }
                            }
                        }

                        this@Column.AnimatedVisibility(
                            visible = agentViewModel.capturingScreen,
                            modifier = Modifier.fillMaxSize(),
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            HintColumn {
                                LoadingIndicator()
                                Text(stringResource(R.string.companion_mode_label_capturing))
                            }
                        }

                        AnimatedContent(
                            targetState = agentViewModel.pipWaitingStatus,
                            transitionSpec = { fadeIn() togetherWith fadeOut() using null },
                        ) { pipWaitingStatus ->
                            if (pipWaitingStatus == null) return@AnimatedContent
                            when (pipWaitingStatus) {
                                Click -> HintColumn {
                                    HintIconBackground { HintIcon(R.drawable.error_24px) }
                                    Text(stringResource(R.string.companion_mode_label_no_permission))
                                }

                                Service -> HintColumn {
                                    HintIconBackground { HintIcon(R.drawable.info_24px) }
                                    Text(stringResource(R.string.companion_mode_label_enable_service))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HintColumn(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun HintIconBackground(
    content: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialShapes.Cookie9Sided.toShape(),
        color = MaterialTheme.colorScheme.primaryContainer,
        content = content,
    )
}

@Composable
private fun HintIcon(
    @DrawableRes drawable: Int,
) {
    Icon(
        painterResource(drawable),
        contentDescription = null,
        modifier = Modifier
            .padding(8.dp)
            .size(32.dp),
    )
}

@Composable
fun rememberIsInPictureInPictureMode(): Boolean {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }

    var isInPipMode by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }

    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> {
            isInPipMode = it.isInPictureInPictureMode
        }

        activity?.addOnPictureInPictureModeChangedListener(listener)

        onDispose {
            activity?.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    return isInPipMode
}

@Stable
private fun Context.findComponentActivity(): ComponentActivity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is ComponentActivity) return context
        context = context.baseContext
    }
    return null
}
