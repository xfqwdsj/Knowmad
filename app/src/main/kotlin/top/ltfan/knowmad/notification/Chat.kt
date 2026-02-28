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

package top.ltfan.knowmad.notification

import ai.koog.prompt.message.Message
import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.chat.getChatIntent
import top.ltfan.knowmad.data.chat.getChatPendingIntent
import kotlin.time.Instant
import kotlin.uuid.Uuid

const val UserPersonId = "user"
const val AssistantPersonId = "019c0c33-1400-7442-a182-60384f02b954"

val UserPersonBuilder = Person.Builder().apply {
    setKey(UserPersonId)
    setImportant(true)
}

val AssistantPersonBuilder = Person.Builder().apply {
    setKey(AssistantPersonId)
}

fun Resources.getUserPerson(): Person {
    return UserPersonBuilder.setName(getString(R.string.person_label_you)).build()
}

fun Resources.getAssistantPerson(): Person {
    return AssistantPersonBuilder.setName(getString(R.string.person_label_ai)).build()
}

const val AiMessageChannelId = "ai_message"

fun Resources.getAiNotificationChannel() = NotificationChannelCompat.Builder(
    AiMessageChannelId,
    NotificationManagerCompat.IMPORTANCE_DEFAULT,
).apply {
    setName(getString(R.string.notification_channel_ai_message_label))
}.build()

fun Resources.getChatRemoteInput(
    quickReplies: List<String>? = null,
) = RemoteInput.Builder(ReplyReceiver.TEXT_KEY).apply {
    setLabel(getString(R.string.chat_input_placeholder))
    quickReplies?.takeIf { it.isNotEmpty() }?.let {
        setChoices(it.toTypedArray())
        setEditChoicesBeforeSending(RemoteInput.EDIT_CHOICES_BEFORE_SENDING_ENABLED)
    }
}.build()

fun Context.getChatPendingIntent(
    conversationId: Uuid,
    conversationName: String,
) = PendingIntentCompat.getBroadcast(
    this,
    conversationId.hashCode(),
    Intent(this, ReplyReceiver::class.java).apply {
        putExtra(ReplyReceiver.EXTRA_CONVERSATION_ID, conversationId.toString())
        putExtra(ReplyReceiver.EXTRA_CONVERSATION_NAME, conversationName)
    },
    PendingIntent.FLAG_UPDATE_CURRENT,
    true,
)

fun Context.getReplyAction(
    conversationId: Uuid,
    conversationName: String,
    quickReplies: List<String>? = null,
) = NotificationCompat.Action.Builder(
    R.drawable.send_24px,
    getString(R.string.chat_input_label_send),
    getChatPendingIntent(conversationId, conversationName),
).apply {
    addRemoteInput(resources.getChatRemoteInput(quickReplies))
    setAllowGeneratedReplies(quickReplies.isNullOrEmpty())
    setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
}.build()

private inline fun NotificationCompat.MessagingStyle.kotlinApply(
    block: NotificationCompat.MessagingStyle.() -> Unit,
) = run {
    block()
    this
}

fun Resources.getMessagingStyle(
    messages: List<NotificationMessage>,
) = NotificationCompat.MessagingStyle(getUserPerson()).kotlinApply {
    messages.forEach { (text, time, sender) ->
        addMessage(text, time.toEpochMilliseconds(), sender)
    }
}

fun Context.showChatNotification(
    conversationId: Uuid,
    conversationName: String,
    messages: List<NotificationMessage>,
    quickReplies: List<String>? = null,
    unreadCount: Int = 1,
) {
    pushChatShortcut(conversationId, conversationName)

    val notification = NotificationCompat.Builder(this, AiMessageChannelId).apply {
        setSmallIcon(R.drawable.ic_launcher_foreground)
        setStyle(resources.getMessagingStyle(messages))
        addAction(getReplyAction(conversationId, conversationName, quickReplies))
        setShortcutId(conversationId.toString())
        setLocusId(LocusIdCompat(conversationId.toString()))
        setAutoCancel(true)
        setNumber(unreadCount)
        setCategory(NotificationCompat.CATEGORY_MESSAGE)
        setContentIntent(getChatPendingIntent(conversationId))
    }.build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionState =
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)

        if (permissionState != PackageManager.PERMISSION_GRANTED) return
    }

    NotificationManagerCompat.from(this).notify(conversationId.hashCode(), notification)
}

fun Context.getBubbleMetadata(
    conversationId: Uuid,
): NotificationCompat.BubbleMetadata? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    NotificationCompat.BubbleMetadata.Builder(
        conversationId.toString(),
    ).apply {
        setDesiredHeight(600)
        setAutoExpandBubble(true)
        setSuppressNotification(true)
    }.build()
} else {
    NotificationCompat.BubbleMetadata.Builder(
        getChatPendingIntent(conversationId),
        IconCompat.createWithResource(this, R.drawable.ic_launcher_foreground),
    ).build()
}

fun Context.pushChatShortcut(conversationId: Uuid, conversationName: String) {
    val shortcutId = conversationId.toString()

    val intent = getChatIntent(conversationId)

    val shortcut = ShortcutInfoCompat.Builder(this, shortcutId).apply {
        setShortLabel(conversationName)
        setLongLived(true)
        setIntent(intent)
        setPerson(resources.getAssistantPerson())
    }.build()

    ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
}

data class NotificationMessage(
    val text: String,
    val time: Instant,
    val sender: Person,
)

fun Message.toNotificationMessage(resources: Resources): NotificationMessage? {
    val (text, time) = when (this) {
        is User -> content.trim() to metaInfo.timestamp
        is Assistant -> content.trim() to metaInfo.timestamp
        else -> return null
    }
    val sender = when (this) {
        is User -> resources.getUserPerson()
        is Assistant -> resources.getAssistantPerson()
        else -> return null
    }
    return NotificationMessage(text, time, sender)
}
