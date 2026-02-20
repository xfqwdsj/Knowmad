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

package top.ltfan.knowmad.sync

import android.Manifest
import android.accounts.Account
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.OperationApplicationException
import android.content.SyncResult
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.RemoteException
import android.provider.CalendarContract
import android.provider.SyncStateContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.database.AppDatabase
import top.ltfan.knowmad.data.schedule.Event
import top.ltfan.knowmad.data.schedule.SemesterEntity
import top.ltfan.knowmad.util.Cbor
import top.ltfan.knowmad.util.Logger
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class CalendarSyncAdapter(context: Context) : AbstractThreadedSyncAdapter(context, true) {
    private val logger = Logger("CalendarSyncAdapter")

    override fun onPerformSync(
        account: Account,
        extras: Bundle,
        authority: String,
        provider: ContentProviderClient,
        syncResult: SyncResult,
    ) {
        val readPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        val writePermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)

        if (readPermission != PackageManager.PERMISSION_GRANTED ||
            writePermission != PackageManager.PERMISSION_GRANTED
        ) {
            syncResult.stats.numAuthExceptions++
            return
        }

        runBlocking(Dispatchers.IO) {
            try {
                val isFullSync = extras.getBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false)
                val lastSyncTime =
                    if (isFullSync) Instant.DISTANT_PAST else provider.getLastSyncTime(account)
                val dao = context(context) { AppDatabase.get() }.scheduleDao()

                val semesterIds = dao.getAllSemesterIds().toSet()
                provider.retainCalendars(semesterIds, account)

                val (events, tombstones) = dao.getAllEventsForSyncAfter(lastSyncTime, SystemSync)
                val map = events.groupBy { it.semester }

                val ops = mutableListOf<ContentProviderOperation>()

                val systemEventIdMap = provider.getSystemEventIdMap(account, ops)

                if (!isFullSync) {
                    val deleted = mutableListOf<Uuid>()
                    for (tombstone in tombstones) {
                        if (ops.size >= 100) {
                            provider.executeBatch(ops, syncResult)
                        }
                        deleted.add(tombstone.id)
                        val systemId = systemEventIdMap[tombstone.id] ?: continue
                        ops.add(buildDeleteOp(systemId, account))
                        syncResult.stats.numDeletes++
                    }

                    provider.executeBatch(ops, syncResult)
                    dao.deleteEventTombstonesForTarget(deleted, SystemSync)
                }

                for ((semester, events) in map) {
                    val calendarId = provider.getOrCreateCalendar(semester, account)

                    for (event in events) {
                        val estimatedSize = 2 + event.reminders.list.size
                        if (ops.size + estimatedSize > 100) {
                            provider.executeBatch(ops, syncResult)
                        }

                        val systemId = systemEventIdMap[event.id]
                        if (systemId == null) {
                            val eventIndex = ops.size
                            buildInsertOps(calendarId, event, account, ops, eventIndex)
                            syncResult.stats.numInserts++
                        } else {
                            buildUpdateOps(systemId, event, account, ops)
                            syncResult.stats.numUpdates++
                        }
                    }
                }

                provider.executeBatch(ops, syncResult)

                if (isFullSync) {
                    val localEventIds = events.map { it.id }.toSet()
                    for ((uuid, systemId) in systemEventIdMap) {
                        if (uuid !in localEventIds) {
                            ops.add(buildDeleteOp(systemId, account))
                            syncResult.stats.numDeletes++
                        }
                    }
                    provider.executeBatch(ops, syncResult)
                    dao.deleteAllEventTombstonesForTarget(SystemSync)
                }

                provider.saveLastSyncTime(account)
            } catch (e: RemoteException) {
                logger.error(e) { "RemoteException during calendar sync" }
                syncResult.stats.numIoExceptions++
            } catch (e: OperationApplicationException) {
                logger.error(e) { "OperationApplicationException during calendar sync" }
                syncResult.stats.numParseExceptions++
            } catch (e: Throwable) {
                logger.error(e) { "Unexpected exception during calendar sync" }
                syncResult.stats.numIoExceptions++
            }
        }
    }


    private fun ContentProviderClient.executeBatch(
        ops: MutableList<ContentProviderOperation>,
        syncResult: SyncResult,
    ) {
        if (ops.isEmpty()) return
        try {
            applyBatch(ArrayList(ops))
            ops.clear()
        } catch (e: Throwable) {
            syncResult.stats.numIoExceptions++
            throw e
        }
    }

    private fun buildInsertOps(
        calendarId: Long,
        event: Event,
        account: Account,
        ops: MutableList<ContentProviderOperation>,
        eventIndex: Int,
    ) {
        ops.add(
            ContentProviderOperation.newInsert(
                CalendarContract.Events.CONTENT_URI.addSyncParams(account),
            )
                .withValue(CalendarContract.Events._SYNC_ID, event.id.toString())
                .withValue(CalendarContract.Events.CALENDAR_ID, calendarId)
                .withValues(event.toContentValues())
                .build(),
        )

        event.reminders.list.forEach { reminder ->
            ops.add(
                ContentProviderOperation.newInsert(
                    CalendarContract.Reminders.CONTENT_URI.addSyncParams(account),
                )
                    .withValueBackReference(CalendarContract.Reminders.EVENT_ID, eventIndex)
                    .withValue(CalendarContract.Reminders.MINUTES, reminder.getPriorMinutes(event))
                    .withValue(
                        CalendarContract.Reminders.METHOD,
                        CalendarContract.Reminders.METHOD_ALERT,
                    )
                    .build(),
            )
        }
    }

    private fun buildUpdateOps(
        systemId: Long,
        event: Event,
        account: Account,
        ops: MutableList<ContentProviderOperation>,
    ) {
        val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, systemId)

        ops.add(
            ContentProviderOperation.newUpdate(eventUri.addSyncParams(account))
                .withValues(event.toContentValues())
                .build(),
        )

        ops.add(
            ContentProviderOperation.newDelete(
                CalendarContract.Reminders.CONTENT_URI.addSyncParams(
                    account,
                ),
            )
                .withSelection(
                    "${CalendarContract.Reminders.EVENT_ID} = ?",
                    arrayOf(systemId.toString()),
                )
                .build(),
        )

        event.reminders.list.forEach { reminder ->
            ops.add(
                ContentProviderOperation.newInsert(
                    CalendarContract.Reminders.CONTENT_URI.addSyncParams(account),
                )
                    .withValue(CalendarContract.Reminders.EVENT_ID, systemId)
                    .withValue(CalendarContract.Reminders.MINUTES, reminder.getPriorMinutes(event))
                    .withValue(
                        CalendarContract.Reminders.METHOD,
                        CalendarContract.Reminders.METHOD_ALERT,
                    )
                    .build(),
            )
        }
    }

    private fun buildDeleteOp(systemId: Long, account: Account): ContentProviderOperation {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, systemId)
        return ContentProviderOperation.newDelete(uri.addSyncParams(account))
            .build()
    }

    private fun Uri.addSyncParams(account: Account): Uri {
        return buildUpon().apply {
            appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account.name)
            appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, account.type)
        }.build()
    }

    private fun Event.toContentValues() = ContentValues().apply {
        put(CalendarContract.Events.TITLE, name)
        when (this@toContentValues) {
            is Normal -> {
                if (!notes.isNullOrBlank()) {
                    put(CalendarContract.Events.DESCRIPTION, notes.trim())
                }
            }

            is Course -> put(
                CalendarContract.Events.DESCRIPTION,
                buildString {
                    if (instructor.isNotBlank()) {
                        append(context.getString(R.string.schedule_event_instructor_label))
                        append(" ")
                        append(instructor)
                        appendLine()
                    }
                    if (!notes.isNullOrBlank()) {
                        append(notes)
                    }
                }.trim(),
            )
        }
        put(CalendarContract.Events.EVENT_LOCATION, location)
        put(CalendarContract.Events.EVENT_COLOR, color.argb)
        put(CalendarContract.Events.DTSTART, startTime.toEpochMilliseconds())
        put(CalendarContract.Events.DTEND, endTime.toEpochMilliseconds())
        put(CalendarContract.Events.EVENT_TIMEZONE, semester.timeZone.id)
        put(CalendarContract.Events.HAS_ALARM, reminders.list.isNotEmpty())
    }

    private fun ContentProviderClient.getSystemEventIdMap(
        account: Account,
        ops: MutableList<ContentProviderOperation>,
    ): Map<Uuid, Long> {
        val projection = arrayOf(CalendarContract.Events._ID, CalendarContract.Events._SYNC_ID)

        val selection =
            "${CalendarContract.Events.ACCOUNT_NAME} = ? AND ${CalendarContract.Events.ACCOUNT_TYPE} = ?"
        val selectionArgs = arrayOf(account.name, account.type)

        return query(
            CalendarContract.Events.CONTENT_URI.addSyncParams(account),
            projection,
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val sId = cursor.getString(1) ?: continue
                    val systemId = cursor.getLong(0)

                    val uuid = Uuid.parseOrNull(sId) ?: run {
                        ops.add(buildDeleteOp(systemId, account))
                        continue
                    }
                    put(uuid, systemId)
                }
            }
        } ?: emptyMap()
    }

    private fun ContentProviderClient.getOrCreateCalendar(
        semester: SemesterEntity,
        account: Account,
    ): Long {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection =
            "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars._SYNC_ID} = ?"

        query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            arrayOf(account.name, account.type, semester.id.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }

        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, account.name)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, account.type)
            put(CalendarContract.Calendars._SYNC_ID, semester.id.toString())
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, semester.name)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }

        val uri = CalendarContract.Calendars.CONTENT_URI.addSyncParams(account)

        val result = insert(uri, values)
        return ContentUris.parseId(result!!)
    }

    private fun ContentProviderClient.retainCalendars(
        semestersIds: Set<Uuid>,
        account: Account,
    ) {
        val projection =
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars._SYNC_ID)
        val selection =
            "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?"
        val selectionArgs = arrayOf(account.name, account.type)

        val calendarsToDelete = mutableListOf<Long>()

        query(
            CalendarContract.Calendars.CONTENT_URI.addSyncParams(account),
            projection,
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val calendarId = cursor.getLong(0)
                val sId = cursor.getString(1)
                val uuid = sId?.let { Uuid.parseOrNull(it) }

                if (uuid == null || uuid !in semestersIds) {
                    calendarsToDelete.add(calendarId)
                }
            }
        }

        if (calendarsToDelete.isNotEmpty()) {
            val ops = calendarsToDelete.map { calendarId ->
                ContentProviderOperation.newDelete(
                    ContentUris.withAppendedId(
                        CalendarContract.Calendars.CONTENT_URI,
                        calendarId,
                    ).addSyncParams(account),
                ).build()
            }
            applyBatch(ArrayList(ops))
        }
    }

    private fun ContentProviderClient.saveLastSyncTime(
        account: Account,
        lastTime: Instant = Clock.System.now(),
    ) {
        val data = Cbor.encodeToByteArray(lastTime)

        val uri = CalendarContract.SyncState.CONTENT_URI.addSyncParams(account)
        val projection = arrayOf(SyncStateContract.Columns._ID)
        val selection =
            "${SyncStateContract.Columns.ACCOUNT_NAME} = ? AND ${SyncStateContract.Columns.ACCOUNT_TYPE} = ?"

        val exists = query(uri, projection, selection, arrayOf(account.name, account.type), null)
            ?.use { it.count > 0 } ?: false

        val op = if (exists) {
            ContentProviderOperation.newUpdate(uri)
                .withSelection(selection, arrayOf(account.name, account.type))
                .withValue(SyncStateContract.Columns.DATA, data)
                .build()
        } else {
            ContentProviderOperation.newInsert(uri)
                .withValue(SyncStateContract.Columns.ACCOUNT_NAME, account.name)
                .withValue(SyncStateContract.Columns.ACCOUNT_TYPE, account.type)
                .withValue(SyncStateContract.Columns.DATA, data)
                .build()
        }
        applyBatch(arrayListOf(op))
    }

    private fun ContentProviderClient.getLastSyncTime(account: Account): Instant {
        val projection = arrayOf(SyncStateContract.Columns.DATA)
        val selection =
            "${SyncStateContract.Columns.ACCOUNT_NAME} = ? AND ${SyncStateContract.Columns.ACCOUNT_TYPE} = ?"

        query(
            CalendarContract.SyncState.CONTENT_URI,
            projection,
            selection,
            arrayOf(account.name, account.type),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val data = cursor.getBlob(0)
                return Cbor.decodeFromByteArray(data)
            }
        }
        return DISTANT_PAST
    }
}
