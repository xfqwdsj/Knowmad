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

import android.annotation.SuppressLint
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceComposable
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.LazyListScope
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import com.materialkolor.hct.Hct
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.schedule.Event
import top.ltfan.knowmad.data.schedule.compose
import top.ltfan.knowmad.data.schedule.type
import top.ltfan.knowmad.ui.theme.AppRadiusLarge
import top.ltfan.knowmad.ui.theme.AppTypography
import top.ltfan.knowmad.ui.util.contractColorFor
import top.ltfan.knowmad.ui.util.provider
import top.ltfan.knowmad.ui.util.toGlanceTextStyle
import java.time.format.DateTimeFormatter
import java.util.Locale

@GlanceComposable
@Composable
fun WidgetEventList(
    upcomingEvents: List<Event>,
    completedEvents: List<Event>,
    supplementaryEvents: List<Event>?,
    getEventClickAction: (Event) -> Action,
    modifier: GlanceModifier = GlanceModifier.fillMaxSize(),
    contentPadding: Dp = 8.dp,
) {
    LazyColumn(modifier) {
        item {
            Spacer(GlanceModifier.height(contentPadding))
        }

        var hasPrevious = false
        spacedItems(upcomingEvents, showInitialSpacing = hasPrevious) { event ->
            Box(GlanceModifier.padding(horizontal = contentPadding)) {
                WidgetEventItem(
                    event = event,
                    completed = false,
                    onClick = getEventClickAction(event),
                )
            }
        }

        hasPrevious = hasPrevious || upcomingEvents.isNotEmpty()
        spacedItems(completedEvents, showInitialSpacing = hasPrevious) { event ->
            Box(GlanceModifier.padding(horizontal = contentPadding)) {
                WidgetEventItem(
                    event = event,
                    completed = true,
                    onClick = getEventClickAction(event),
                )
            }
        }

        hasPrevious = hasPrevious || completedEvents.isNotEmpty()
        if (!supplementaryEvents.isNullOrEmpty()) {
            if (hasPrevious) {
                item {
                    Column(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                    ) {
                        Spacer(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(GlanceTheme.colors.surfaceVariant),
                        )
                        Spacer(GlanceModifier.height(4.dp))
                        LabelWithIcon(
                            icon = R.drawable.info_24px,
                            label = LocalContext.current.getString(R.string.schedule_event_list_indicator_label_next_day),
                            modifier = GlanceModifier.padding(horizontal = contentPadding),
                        )
                    }
                }
            } else {
                item {
                    LabelWithIcon(
                        icon = R.drawable.info_24px,
                        label = LocalContext.current.getString(R.string.schedule_event_list_indicator_label_next_day),
                        modifier = GlanceModifier.padding(horizontal = contentPadding + 16.dp),
                    )
                }
                item {
                    Spacer(GlanceModifier.height(8.dp))
                }
            }
            spacedItems(supplementaryEvents) { event ->
                Box(GlanceModifier.padding(horizontal = contentPadding)) {
                    WidgetEventItem(
                        event = event,
                        completed = false,
                        onClick = getEventClickAction(event),
                    )
                }
            }
        }

        item {
            Spacer(GlanceModifier.height(contentPadding))
        }
    }
}

@GlanceComposable
@Composable
fun WidgetEventItem(
    event: Event,
    completed: Boolean,
    onClick: Action,
) {
    val context = LocalContext.current

    @SuppressLint("NonObservableLocale")
    val locale = Locale.getDefault()
    val timeZone = TimeZone.currentSystemDefault()

    val color = if (!completed) {
        event.color.compose
    } else {
        val original = Hct.fromInt(event.color.argb)
        val chroma = original.chroma * 0.3
        val tone = original.tone - 30
        Color(Hct.from(original.hue, chroma, tone).toInt())
    }
    val contentColor = contractColorFor(color)
    val colorProvider = contentColor.provider

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(AppRadiusLarge)
            .background(color)
            .clickable(onClick)
            .padding(16.dp),
        verticalAlignment = CenterVertically,
    ) {
        Column(
            modifier = GlanceModifier.defaultWeight(),
        ) {
            if (event.priority.type == High) {
                Image(
                    ImageProvider(R.drawable.priority_high_24px),
                    contentDescription = event.priority.name,
                    modifier = GlanceModifier.size(16.dp),
                    colorFilter = ColorFilter.tint(colorProvider),
                )
                Spacer(GlanceModifier.height(4.dp))
            }
            Text(
                event.name,
                style = AppTypography.titleMedium.toGlanceTextStyle(colorProvider).run {
                    if (!completed) this
                    else copy(textDecoration = LineThrough)
                },
            )
            Spacer(GlanceModifier.height(4.dp))
            LabelWithIcon(
                icon = R.drawable.location_on_24px,
                label = event.location.ifBlank { context.getString(R.string.schedule_event_location_label_none) },
                contentDescription = context.getString(R.string.schedule_event_location_label),
                tint = colorProvider,
            )
            val notes = event.notes
            if (!notes.isNullOrBlank()) {
                Spacer(GlanceModifier.height(4.dp))
                LabelWithIcon(
                    icon = R.drawable.notes_24px,
                    label = notes,
                    contentDescription = context.getString(R.string.schedule_event_notes_label),
                    tint = colorProvider,
                )
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        Column(
            horizontalAlignment = End,
        ) {
            val formatter = DateTimeFormatter.ofLocalizedTime(SHORT).withLocale(locale)

            val startTime = event.startTime.toLocalDateTime(timeZone).toJavaLocalDateTime()
            val start = formatter.format(startTime)
            Text(
                start,
                style = AppTypography.bodyLargeEmphasized.toGlanceTextStyle(colorProvider)
                    .copy(fontWeight = Bold),
            )

            Spacer(GlanceModifier.height(4.dp))

            val endTime = event.endTime.toLocalDateTime(timeZone).toJavaLocalDateTime()
            val end = formatter.format(endTime)
            Text(
                end,
                style = AppTypography.bodyMediumEmphasized.toGlanceTextStyle(contentColor.copy(alpha = .7f).provider),
            )
        }
    }
}

@GlanceComposable
@Composable
private fun LabelWithIcon(
    @DrawableRes icon: Int,
    label: String,
    modifier: GlanceModifier = GlanceModifier,
    contentDescription: String? = null,
    tint: ColorProvider = GlanceTheme.colors.secondary,
) {
    Row(
        modifier = modifier,
        verticalAlignment = if (label.count { it == '\n' } == 0) CenterVertically else Top,
    ) {
        Image(
            ImageProvider(icon),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(16.dp),
            colorFilter = ColorFilter.tint(tint),
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(label, style = AppTypography.labelMedium.toGlanceTextStyle(tint))
    }
}

private inline fun <T> LazyListScope.spacedItems(
    items: List<T>,
    spacing: Dp = 8.dp,
    showInitialSpacing: Boolean = false,
    crossinline itemContent: @Composable (T) -> Unit,
) {
    items.forEachIndexed { index, item ->
        if (index > 0 || showInitialSpacing) {
            item { Spacer(GlanceModifier.height(spacing)) }
        }
        item { itemContent(item) }
    }
}
