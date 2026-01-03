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

import ai.koog.prompt.message.Message
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.toStdlibInstant
import top.ltfan.knowmad.R
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Composable
fun AssistantMessage(
    messages: List<Message.Response>,
    reasoningVisible: Boolean,
    onReasoningVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        for (message in messages) {
            when (message) {
                is Message.Reasoning -> ReasoningMessage(
                    message = message,
                    startedAt = message.metaInfo.metadata?.get("startedAt")
                        ?.let { Instant.parse(it.toString()) }
                        ?: message.metaInfo.timestamp.toStdlibInstant(),
                    endedAt = message.metaInfo.timestamp.toStdlibInstant(),
                    visible = reasoningVisible,
                    onVisibilityChange = onReasoningVisibilityChange,
                    modifier = Modifier.padding(8.dp),
                )

                is Message.Assistant -> {
                    /* TODO */
                }

                is Message.Tool.Call -> {
                    /* TODO */
                }
            }
        }
    }
}

@Composable
fun ReasoningMessage(
    message: Message.Reasoning,
    startedAt: Instant,
    endedAt: Instant,
    visible: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ReasoningMessage(
        flow = flowOf(message.content),
        startedAt = startedAt,
        endedAt = endedAt,
        visible = visible,
        onVisibilityChange = onVisibilityChange,
        modifier = modifier,
    )
}

@Composable
fun ReasoningMessage(
    flow: Flow<String>,
    startedAt: Instant,
    endedAt: Instant?,
    visible: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberSavedMarkdownState(flow)

    val isEnded = endedAt != null
    var endedAt by remember(endedAt) { mutableStateOf(endedAt ?: Clock.System.now()) }

    val duration = remember(startedAt, endedAt) {
        (endedAt - startedAt).inWholeSeconds.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExpandOrCollapseIconButton(
                    expanded = visible,
                    onExpandedChange = onVisibilityChange,
                )
                Text(
                    pluralStringResource(
                        if (!isEnded) R.plurals.chat_message_reasoning_label_in_progress
                        else R.plurals.chat_message_reasoning_label_completed,
                        duration, duration,
                    ),
                )
            }
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.fillMaxWidth(),
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                MarkdownView(
                    state,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        while (!isEnded) {
            delay(1.seconds)
            endedAt = Clock.System.now()
        }
    }
}

@Composable
fun MessageActions(
    current: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCopy: () -> Pair<CharSequence?, CharSequence>,
    onRegenerate: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MessageBranchIndicator(
            current = current,
            total = total,
            onPrevious = onPrevious,
            onNext = onNext,
        )
        CopyIconButton(
            onCopy = onCopy,
        )
        RetryIconButton(
            onRetry = onRegenerate,
            contentDescriptionRes = R.string.chat_message_action_label_retry,
        )
    }
}

@Composable
fun MessageBranchIndicator(
    current: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackChevronIconButton(
            onClick = onPrevious,
            enabled = current > 1,
            contentDescriptionRes = R.string.chat_message_branch_label_previous,
        )
        Text(stringResource(R.string.chat_message_branch_indicator, current, total))
        ForwardChevronIconButton(
            onClick = onNext,
            enabled = current < total,
            contentDescriptionRes = R.string.chat_message_branch_label_next,
        )
    }
}
