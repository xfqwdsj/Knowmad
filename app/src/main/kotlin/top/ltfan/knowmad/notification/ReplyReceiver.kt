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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlin.uuid.Uuid

class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val conversationIdStr = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: return
        val conversationId = Uuid.parseOrNull(conversationIdStr) ?: return

        val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return
        val text = remoteInput.getCharSequence(TEXT_KEY)?.toString() ?: return
        _channel.trySend(conversationId to text)
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "notification.reply.CONVERSATION_ID"
        const val TEXT_KEY = "notification.reply.TEXT"

        private val _channel = Channel<Pair<Uuid, String>>(Channel.BUFFERED)
        val channel = object : ReceiveChannel<Pair<Uuid, String>> by _channel {
            override fun cancel(cause: CancellationException?) {}
        }
    }
}
