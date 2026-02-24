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
import top.ltfan.knowmad.R
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

fun Resources.getChatRemoteInput() = RemoteInput.Builder(ReplyReceiver.TEXT_KEY).apply {
    setLabel(getString(R.string.chat_input_placeholder))
}.build()

fun Context.getChatPendingIntent(conversationId: Uuid) = PendingIntentCompat.getBroadcast(
    this,
    conversationId.hashCode(),
    Intent(this, ReplyReceiver::class.java).apply {
        putExtra(ReplyReceiver.EXTRA_CONVERSATION_ID, conversationId.toString())
    },
    PendingIntent.FLAG_UPDATE_CURRENT,
    true,
)

fun Context.getReplyAction(conversationId: Uuid) = NotificationCompat.Action.Builder(
    R.drawable.send_24px,
    getString(R.string.chat_input_label_send),
    getChatPendingIntent(conversationId),
).addRemoteInput(resources.getChatRemoteInput()).build()

private inline fun NotificationCompat.MessagingStyle.kotlinApply(
    block: NotificationCompat.MessagingStyle.() -> Unit,
) = run {
    block()
    this
}

fun Resources.getMessagingStyle(
    conversationName: String,
    messages: List<Message>,
) = NotificationCompat.MessagingStyle(getUserPerson()).kotlinApply {
    setConversationTitle(conversationName)
    messages.forEach {
        when (it) {
            is User -> addMessage(
                it.content.trim(),
                System.currentTimeMillis(),
                getUserPerson(),
            )

            is Assistant -> addMessage(
                it.content.trim(),
                System.currentTimeMillis(),
                getAssistantPerson(),
            )

            else -> {}
        }
    }
}

fun Context.showChatNotification(
    conversationId: Uuid,
    conversationName: String,
    messages: List<Message>,
) {
    val notification = NotificationCompat.Builder(this, AiMessageChannelId).apply {
        setSmallIcon(R.drawable.ic_launcher_foreground)
        setStyle(resources.getMessagingStyle(conversationName, messages))
        addAction(getReplyAction(conversationId))
        setAutoCancel(true)
    }.build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionState =
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)

        if (permissionState != PackageManager.PERMISSION_GRANTED) return
    }

    NotificationManagerCompat.from(this).notify(conversationId.hashCode(), notification)
}
