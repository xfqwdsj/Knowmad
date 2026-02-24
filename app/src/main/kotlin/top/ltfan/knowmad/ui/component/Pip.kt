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

import android.app.PendingIntent
import android.app.RemoteAction
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.staticCompositionLocalOf
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
import top.ltfan.knowmad.ui.theme.AppSmallShape
import top.ltfan.knowmad.ui.viewmodel.AgentViewModel
import top.ltfan.knowmad.ui.viewmodel.LocalAgentViewModel
import top.ltfan.knowmad.ui.viewmodel.LocalAppViewModel
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Composable
fun PictureInPicture() {
    val appViewModel = LocalAppViewModel.current
    val agentViewModel = LocalAgentViewModel.current

    Container {
        Scaffold(
            topBar = {
                val conversation by agentViewModel.currentConversationFlow.collectAsState(null)

                Surface(
                    shape = AppSmallShape.copy(
                        topStart = CornerSize(0.dp),
                        topEnd = CornerSize(0.dp),
                    ),
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        conversation?.name ?: stringResource(R.string.agent_conversation_label_new),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        textAlign = Center,
                        overflow = Ellipsis,
                        softWrap = false,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                }
            },
            contentWindowInsets = WindowInsets(),
        ) { padding ->
            val messages = agentViewModel.currentMessagesFlow?.collectAsLazyPagingItems()

            CompositionLocalProvider(LocalPadding provides padding) {
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
                    Box(Modifier.fillMaxSize()) {
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
                                contentPadding = padding,
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

                                val minimumFactor = -1f
                                val maximumFactor = 3f

                                fun calcValue(
                                    lastTime: Instant?,
                                    setLastTime: ((Instant) -> Unit)? = null,
                                ): Float {
                                    val now = Clock.System.now()
                                    val delta = if (lastTime == null) {
                                        baseDuration - stepDuration
                                    } else {
                                        now - lastTime
                                    }
                                    setLastTime?.invoke(now)

                                    val step = state.layoutInfo.viewportSize.height / 2

                                    val minimumValue = step * minimumFactor
                                    val maximumValue = step * maximumFactor

                                    return ((baseDuration - delta) / stepDuration * step).toFloat()
                                        .fastCoerceAtLeast(minimumValue)
                                        .fastCoerceAtMost(maximumValue)
                                }

                                launch {
                                    while (true) {
                                        val value = calcValue(lastTime)

                                        if (value >= 0) {
                                            agentViewModel.pipUpdateActions(
                                                PipActions.scrollUp(agentViewModel),
                                            )
                                        } else {
                                            agentViewModel.pipUpdateActions(
                                                PipActions.scrollDown(agentViewModel),
                                            )
                                        }

                                        delay(100.milliseconds)
                                    }
                                }

                                for (_ in agentViewModel.pipScrollEvents) {
                                    backJob.cancelChildren()

                                    val value = calcValue(lastTime) { lastTime = it }

                                    backScope.launch {
                                        delay(resetTime)
                                        lastTime = null
                                    }

                                    backScope.launch {
                                        delay(backThreshold)
                                        state.animateScrollToItem(0)
                                    }

                                    launch {
                                        state.animateScrollBy(
                                            value = value,
                                            animationSpec = spring(
                                                stiffness = Spring.StiffnessLow,
                                            ),
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(
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

    LaunchedEffect(agentViewModel.isRunning) {
        if (agentViewModel.isRunning) {
            agentViewModel.pipUpdateActions(PipActions.stopGeneration(agentViewModel))
        } else {
            agentViewModel.pipUpdateActions(PipActions.newConversation(agentViewModel))
        }
    }
}

@Composable
private fun HintColumn(
    padding: PaddingValues = LocalPadding.current,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
            .padding(4.dp)
            .padding(padding),
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

sealed interface PipEvent {
    class SetActions(val delta: PipActionsDelta) : PipEvent
}

sealed interface PipAction {
    val icon: Int
    val title: Int
    val contentDescription: Int

    fun onClick()

    fun toRemoteAction(context: Context): RemoteAction {
        val actionCode = hashCode()
        return RemoteAction(
            Icon.createWithResource(context, icon),
            context.getString(title),
            context.getString(contentDescription),
            PendingIntent.getBroadcast(
                context,
                actionCode,
                Intent(ACTION).apply {
                    putExtra(EXTRA_ACTION, actionCode)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }

    class ScrollUp(val viewModel: AgentViewModel) : PipAction {
        override val icon = R.drawable.arrow_circle_up_24px
        override val title = R.string.companion_mode_label_scroll_up
        override val contentDescription = R.string.companion_mode_label_scroll_up_description

        override fun onClick() {
            viewModel.pipScroll()
        }
    }

    class ScrollDown(val viewModel: AgentViewModel) : PipAction {
        override val icon = R.drawable.arrow_circle_down_24px
        override val title = R.string.companion_mode_label_scroll_down
        override val contentDescription = R.string.companion_mode_label_scroll_down_description

        override fun onClick() {
            viewModel.pipScroll()
        }
    }

    class CaptureUI(val viewModel: AgentViewModel) : PipAction {
        override val icon = R.drawable.capture_24px
        override val title = R.string.service_semantic_analysis_capture_label
        override val contentDescription = R.string.service_semantic_analysis_capture_description

        override fun onClick() {
            viewModel.pipCaptureUi()
        }
    }

    class GrantPermission(val viewModel: AgentViewModel) : PipAction {
        override val icon = R.drawable.arrow_circle_right_24px
        override val title = R.string.companion_mode_label_grant_permission
        override val contentDescription = R.string.companion_mode_label_grant_permission

        override fun onClick() {
            viewModel.pipCaptureUi()
        }
    }

    class NewConversation(val viewModel: AgentViewModel) : PipAction {
        override val icon = R.drawable.edit_square_24px
        override val title = R.string.agent_conversation_label_new
        override val contentDescription = R.string.agent_conversation_label_new

        override fun onClick() {
            viewModel.newConversation()
        }
    }

    class StopGeneration(val viewModel: AgentViewModel) : PipAction {
        override val icon = R.drawable.stop_circle_24px
        override val title = R.string.agent_label_stop_generation
        override val contentDescription = R.string.agent_label_stop_generation

        override fun onClick() {
            viewModel.cancelGeneration()
        }
    }

    companion object {
        const val ACTION = "top.ltfan.knowmad.pip.ACTION"
        const val EXTRA_ACTION = "top.ltfan.knowmad.pip.EXTRA_ACTION"
    }
}

data class PipActions(
    val first: PipAction? = null,
    val second: PipAction? = null,
    val third: PipAction? = null,
) {
    companion object {
        @Suppress("NOTHING_TO_INLINE")
        inline fun standard(viewModel: AgentViewModel) = PipActionsDelta.Set(
            PipActions(
                PipAction.ScrollUp(viewModel),
                PipAction.CaptureUI(viewModel),
                PipAction.NewConversation(viewModel),
            ),
        )

        @Suppress("NOTHING_TO_INLINE")
        inline fun scrollUp(viewModel: AgentViewModel) = PipActionsDelta.Update(
            PipActions(
                first = PipAction.ScrollUp(viewModel),
            ),
        )

        @Suppress("NOTHING_TO_INLINE")
        inline fun scrollDown(viewModel: AgentViewModel) = PipActionsDelta.Update(
            PipActions(
                first = PipAction.ScrollDown(viewModel),
            ),
        )

        @Suppress("NOTHING_TO_INLINE")
        inline fun grantPermission(viewModel: AgentViewModel) = PipActionsDelta.Update(
            PipActions(
                second = PipAction.GrantPermission(viewModel),
            ),
        )

        @Suppress("NOTHING_TO_INLINE")
        inline fun newConversation(viewModel: AgentViewModel) = PipActionsDelta.Update(
            PipActions(
                third = PipAction.NewConversation(viewModel),
            ),
        )

        @Suppress("NOTHING_TO_INLINE")
        inline fun stopGeneration(viewModel: AgentViewModel) = PipActionsDelta.Update(
            PipActions(
                third = PipAction.StopGeneration(viewModel),
            ),
        )
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun toActionsWithMap(context: Context) = buildList {
        first?.let { add(it.toRemoteAction(context)) }
        second?.let { add(it.toRemoteAction(context)) }
        third?.let { add(it.toRemoteAction(context)) }
    } to buildMap {
        first?.let { put(it.hashCode(), it::onClick) }
        second?.let { put(it.hashCode(), it::onClick) }
        third?.let { put(it.hashCode(), it::onClick) }
    }
}

sealed interface PipActionsDelta {
    data class Update(val updateActions: PipActions) : PipActionsDelta {
        override fun applyTo(current: PipActions) = PipActions(
            first = updateActions.first ?: current.first,
            second = updateActions.second ?: current.second,
            third = updateActions.third ?: current.third,
        )
    }

    data class Set(val setActions: PipActions) : PipActionsDelta {
        override fun applyTo(current: PipActions) = setActions
    }

    fun applyTo(current: PipActions): PipActions
}

fun Intent.handlePipActions(actions: Map<Int, () -> Unit>): Boolean {
    if (action != PipAction.ACTION) return false
    val actionCode = getIntExtra(PipAction.EXTRA_ACTION, 0)
    val func = actions[actionCode] ?: return false
    func()
    return true
}

@Composable
private fun Container(content: @Composable () -> Unit) {
    val density = LocalDensity.current

    val scale = .65f
    val newDensity = remember(density) {
        Density(
            density = density.density * scale,
            fontScale = density.fontScale,
        )
    }

    CompositionLocalProvider(LocalDensity provides newDensity) {
        Surface(content = content)
    }
}

private val LocalPadding = staticCompositionLocalOf { PaddingValues() }
