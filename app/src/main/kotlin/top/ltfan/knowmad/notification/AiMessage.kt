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

import android.content.Context
import android.content.res.Resources
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import top.ltfan.knowmad.R

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

private const val AiMessageChannelId = "ai_message"

val Context.aiNotificationChannel: NotificationChannelCompat
    get() {
        val channel = NotificationChannelCompat.Builder(
            AiMessageChannelId,
            NotificationManagerCompat.IMPORTANCE_DEFAULT,
        ).apply {
            setName(getString(R.string.notification_channel_ai_message_label))
        }.build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
        return channel
    }
