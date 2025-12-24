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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.ltfan.knowmad.R

@Immutable
data class StepItem(
    val title: String,
    val subtitle: String? = null,
)

@Immutable
private data class StepPosition(val x: Float, val width: Int)

@Composable
fun Stepper(
    steps: List<StepItem>,
    currentStep: Int,
    modifier: Modifier = Modifier,
    allowUserScroll: Boolean = true,
    leadScrollOffset: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val stepPositions = remember { mutableStateMapOf<Int, StepPosition>() }
    val contentWidthState = remember { mutableIntStateOf(0) }

    LaunchedEffect(currentStep) {
        if (stepPositions.isNotEmpty()) {
            val stepInfo = stepPositions[currentStep]
            val targetX = stepInfo?.x ?: 0f
            val itemWidth = stepInfo?.width ?: 0
            val contentWidth = contentWidthState.intValue

            val leadOffsetPx = with(density) { leadScrollOffset.toPx() }

            val scrollTo = if (layoutDirection == LayoutDirection.Ltr) {
                if (currentStep == 0) 0f else (targetX - leadOffsetPx).coerceAtLeast(0f)
            } else {
                val distFromRight = contentWidth - (targetX + itemWidth)
                if (currentStep == 0) 0f else (distFromRight - leadOffsetPx).coerceAtLeast(0f)
            }

            scrollState.animateScrollTo(
                scrollTo.toInt(),
                animationSpec = spring(stiffness = Spring.StiffnessLow),
            )
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState, enabled = allowUserScroll)
            .onSizeChanged { contentWidthState.intValue = it.width }
            .padding(8.dp)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
    ) {
        steps.forEachIndexed { index, step ->
            val status = when {
                index < currentStep -> StepStatus.COMPLETED
                index == currentStep -> StepStatus.CURRENT
                else -> StepStatus.INCOMPLETE
            }

            StepperItem(
                index = index + 1,
                step = step,
                status = status,
                isLast = index == steps.lastIndex,
                onPositioned = { x, width ->
                    stepPositions[index] = StepPosition(x, width)
                },
            )
        }
    }
}

private enum class StepStatus { INCOMPLETE, CURRENT, COMPLETED }

@Composable
private fun StepperItem(
    index: Int,
    step: StepItem,
    status: StepStatus,
    isLast: Boolean,
    onPositioned: (Float, Int) -> Unit,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    val onActiveColor = MaterialTheme.colorScheme.onPrimary
    val onInactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    val animatedColor by animateColorAsState(
        targetValue = if (status == StepStatus.INCOMPLETE) inactiveColor else activeColor,
        label = "color",
    )

    val lineProgress by animateFloatAsState(
        targetValue = if (status == StepStatus.COMPLETED) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "line",
    )

    Column(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .onGloballyPositioned { layoutCoordinates ->
                onPositioned(layoutCoordinates.positionInParent().x, layoutCoordinates.size.width)
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(animatedColor),
            ) {
                AnimatedContent(
                    targetState = status,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    contentAlignment = Alignment.Center,
                    label = "content",
                ) { currentStatus ->
                    if (currentStatus == StepStatus.COMPLETED) {
                        Icon(
                            painterResource(R.drawable.check_24px),
                            contentDescription = "Completed",
                            tint = onActiveColor,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Text(
                            text = index.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (currentStatus == StepStatus.INCOMPLETE) onInactiveColor else onActiveColor,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            if (!isLast) {
                Spacer(modifier = Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { lineProgress },
                    modifier = Modifier.width(60.dp),
                    color = activeColor,
                    trackColor = inactiveColor,
                    drawStopIndicator = {},
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 100.dp),
        ) {
            Text(
                text = step.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (status == StepStatus.INCOMPLETE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (step.subtitle != null) {
                Text(
                    text = step.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
