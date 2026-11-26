package com.example.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class BusyInterval(
    val startMs: Long,
    val endMs: Long,
    val summary: String = ""
)

data class SuggestedTimeSlot(
    val id: String,
    val label: String,
    val startMs: Long,
    val endMs: Long
)

object IcsCalendarParser {
    fun parse(icsContent: String): List<BusyInterval> {
        val unfolded = unfoldLines(icsContent)
        val intervals = mutableListOf<BusyInterval>()

        var inEvent = false
        var dtStartRaw: String? = null
        var dtEndRaw: String? = null
        var summary = ""
        var cancelled = false

        val maxEvents = 5_000
        for (line in unfolded) {
            val trimmed = line.trim()
            when {
                trimmed.equals("BEGIN:VEVENT", ignoreCase = true) -> {
                    inEvent = true
                    dtStartRaw = null
                    dtEndRaw = null
                    summary = ""
                    cancelled = false
                }
                trimmed.equals("END:VEVENT", ignoreCase = true) -> {
                    if (inEvent && !cancelled && dtStartRaw != null) {
                        val startMs = parseIcsDateTime(dtStartRaw)
                        val endMs = dtEndRaw?.let { parseIcsDateTime(it) }
                            ?: startMs?.plus(60L * 60L * 1000L)
                        if (startMs != null && endMs != null && endMs > startMs) {
                            intervals.add(
                                BusyInterval(
                                    startMs = startMs,
                                    endMs = endMs,
                                    summary = sanitizeMeldInput(summary, maxLength = 120)
                                )
                            )
                            if (intervals.size >= maxEvents) break
                        }
                    }
                    inEvent = false
                }
                inEvent -> {
                    val colonIndex = trimmed.indexOf(':')
                    if (colonIndex > 0) {
                        val keyPart = trimmed.substring(0, colonIndex).uppercase(Locale.US)
                        val valuePart = trimmed.substring(colonIndex + 1).trim()
                        val propName = keyPart.substringBefore(';')
                        when (propName) {
                            "DTSTART" -> dtStartRaw = valuePart
                            "DTEND" -> dtEndRaw = valuePart
                            "SUMMARY" -> summary = valuePart
                            "STATUS" -> if (valuePart.equals("CANCELLED", ignoreCase = true)) {
                                cancelled = true
                            }
                        }
                    }
                }
            }
        }
        return intervals.sortedBy { it.startMs }
    }

    private fun unfoldLines(raw: String): List<String> {
        val normalized = raw.replace("\r\n", "\n").replace("\r", "\n")
        val lines = normalized.split('\n')
        val result = mutableListOf<String>()
        for (line in lines) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && result.isNotEmpty()) {
                result[result.lastIndex] = result.last() + line.drop(1)
            } else {
                result.add(line)
            }
        }
        return result
    }

    private fun parseIcsDateTime(value: String): Long? {
        val v = value.trim()
        val formats = listOf(
            "yyyyMMdd'T'HHmmss'Z'" to TimeZone.getTimeZone("UTC"),
            "yyyyMMdd'T'HHmmss" to TimeZone.getDefault(),
            "yyyyMMdd" to TimeZone.getDefault()
        )
        for ((pattern, tz) in formats) {
            runCatching {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    timeZone = tz
                }
                return sdf.parse(v)?.time
            }
        }
        return null
    }
}

object DeviceCalendarReader {
    fun hasCalendarPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun readBusyIntervals(
        context: Context,
        startWindowMs: Long,
        endWindowMs: Long
    ): List<BusyInterval> {
        if (!hasCalendarPermission(context)) return emptyList()

        val intervals = mutableListOf<BusyInterval>()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startWindowMs)
        ContentUris.appendId(builder, endWindowMs)

        val projection = arrayOf(
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.AVAILABILITY
        )

        runCatching {
            context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val beginCol = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endCol = cursor.getColumnIndex(CalendarContract.Instances.END)
                val titleCol = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val availCol = cursor.getColumnIndex(CalendarContract.Instances.AVAILABILITY)

                while (cursor.moveToNext()) {
                    val avail = if (availCol >= 0) cursor.getInt(availCol)
                    else CalendarContract.Instances.AVAILABILITY_BUSY
                    if (avail == CalendarContract.Instances.AVAILABILITY_FREE) continue

                    val begin = if (beginCol >= 0) cursor.getLong(beginCol) else continue
                    val end = if (endCol >= 0) cursor.getLong(endCol) else continue
                    val title = if (titleCol >= 0) {
                        sanitizeMeldInput(cursor.getString(titleCol).orEmpty(), maxLength = 120)
                    } else {
                        ""
                    }
                    if (end > begin) {
                        intervals.add(BusyInterval(begin, end, title))
                    }
                }
            }
        }
        return intervals.sortedBy { it.startMs }
    }

    fun readIcsUri(context: Context, uri: android.net.Uri): String? {
        val maxBytes = 2 * 1024 * 1024 // 2 MB defensive cap against oversized files
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
                val buffer = CharArray(8192)
                val out = StringBuilder()
                var totalChars = 0
                while (totalChars < maxBytes) {
                    val read = reader.read(buffer, 0, minOf(buffer.size, maxBytes - totalChars))
                    if (read <= 0) break
                    out.appendRange(buffer, 0, read)
                    totalChars += read
                }
                out.toString()
            }
        }.getOrNull()
    }
}

object FreeSlotCalculator {
    fun suggestFreeBlocks(
        busyIntervals: List<BusyInterval>,
        nowMs: Long = System.currentTimeMillis(),
        daysAhead: Int = 4,
        slotDurationMinutes: Int = 60,
        maxSuggestions: Int = 6
    ): List<SuggestedTimeSlot> {
        val mergedBusy = mergeIntervals(busyIntervals)
        val suggestions = mutableListOf<SuggestedTimeSlot>()
        val slotDurationMs = slotDurationMinutes * 60L * 1000L
        val stepMs = 60L * 60L * 1000L
        val formatter = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault())

        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        // Round up to next full hour
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.HOUR_OF_DAY, 1)

        val minStartMs = cal.timeInMillis

        for (dayOffset in 0 until daysAhead) {
            val dayCal = Calendar.getInstance().apply {
                timeInMillis = nowMs
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Candidate social & work coordination hours: 10:00, 12:30/13:00, 17:00, 19:00
            val preferredHours = listOf(10, 13, 17, 19, 15, 20)
            for (hour in preferredHours) {
                dayCal.set(Calendar.HOUR_OF_DAY, hour)
                val start = dayCal.timeInMillis
                val end = start + slotDurationMs
                if (start < minStartMs) continue

                val overlapsBusy = mergedBusy.any { busy ->
                    start < busy.endMs && end > busy.startMs
                }
                if (!overlapsBusy) {
                    suggestions.add(
                        SuggestedTimeSlot(
                            id = "slot_${start}",
                            label = formatter.format(dayCal.time),
                            startMs = start,
                            endMs = end
                        )
                    )
                    if (suggestions.size >= maxSuggestions) {
                        return suggestions.sortedBy { it.startMs }
                    }
                }
            }

            // Fallback hourly scan 9 AM - 9 PM if preferred hours were busy
            var scan = dayCal.apply { set(Calendar.HOUR_OF_DAY, 9) }.timeInMillis
            val dayEnd = dayCal.apply { set(Calendar.HOUR_OF_DAY, 21) }.timeInMillis
            while (scan + slotDurationMs <= dayEnd) {
                if (scan >= minStartMs && suggestions.none { it.startMs == scan }) {
                    val end = scan + slotDurationMs
                    val overlapsBusy = mergedBusy.any { busy ->
                        scan < busy.endMs && end > busy.startMs
                    }
                    if (!overlapsBusy) {
                        suggestions.add(
                            SuggestedTimeSlot(
                                id = "slot_$scan",
                                label = formatter.format(java.util.Date(scan)),
                                startMs = scan,
                                endMs = end
                            )
                        )
                        if (suggestions.size >= maxSuggestions) {
                            return suggestions.sortedBy { it.startMs }
                        }
                    }
                }
                scan += stepMs
            }
        }

        return suggestions.sortedBy { it.startMs }
    }

    private fun mergeIntervals(intervals: List<BusyInterval>): List<BusyInterval> {
        if (intervals.isEmpty()) return emptyList()
        val sorted = intervals.sortedBy { it.startMs }
        val merged = mutableListOf(sorted.first())
        for (i in 1 until sorted.size) {
            val last = merged.last()
            val curr = sorted[i]
            if (curr.startMs <= last.endMs) {
                merged[merged.lastIndex] = last.copy(endMs = maxOf(last.endMs, curr.endMs))
            } else {
                merged.add(curr)
            }
        }
        return merged
    }
}
