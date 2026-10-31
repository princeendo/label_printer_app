// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.bringup

import io.github.princeendo.labelprinter.printer.P12Status
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.ZonedDateTime

/**
 * The bring-up session log. Every line carries seconds since the log started, so reply timing can
 * be read from it, and is appended to [file] as it is written. Serial numbers are reduced to their
 * prefix before they reach the log; Bluetooth addresses are never written to it.
 */
class DebugLog(
    private val file: File,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val start = nanoTime()
    private val recent = MutableStateFlow<List<String>>(emptyList())

    /** The most recent lines, oldest first. The file holds the full session. */
    val lines: StateFlow<List<String>> = recent.asStateFlow()

    init {
        file.writeText("")
        add("Session started ${ZonedDateTime.now()}")
    }

    @Synchronized
    fun add(message: String) {
        val line = "%9.3f  %s".format((nanoTime() - start) / 1e9, message)
        file.appendText(line + "\n")
        recent.value = (recent.value + line).takeLast(MAX_RECENT_LINES)
    }

    @Synchronized
    fun clear() {
        file.writeText("")
        recent.value = emptyList()
        add("Log cleared")
    }

    fun text(): String = file.readText()

    private companion object {
        const val MAX_RECENT_LINES = 400
    }
}

/** Characters of a printer serial number kept in logs; the rest are masked. */
const val SERIAL_PREFIX_CHARS: Int = 4

private const val SERIAL_TAG = 0x08
private const val SERIAL_LENGTH = 15

/**
 * Formats [bytes] as hex, masking serial digits after the prefix in any `1A 08` reply that starts
 * within this read. A reply split across reads is masked only in the read where it starts.
 */
fun redactedHex(
    bytes: ByteArray,
    limit: Int = Int.MAX_VALUE,
): String {
    val masked = BooleanArray(bytes.size)
    for (i in 0 until bytes.size - 1) {
        if (bytes[i].toInt() == 0x1A && bytes[i + 1].toInt() == SERIAL_TAG) {
            val payloadStart = i + 2
            for (j in payloadStart + SERIAL_PREFIX_CHARS until minOf(payloadStart + SERIAL_LENGTH, bytes.size)) masked[j] = true
        }
    }
    val end = minOf(bytes.size, limit)
    val hex = (0 until end).joinToString(" ") { if (masked[it]) "**" else "%02X".format(bytes[it].toInt() and 0xFF) }
    return if (end < bytes.size) "$hex … (${bytes.size} bytes)" else hex
}

fun describe(status: P12Status): String =
    when (status) {
        is P12Status.Serial -> "Serial(${status.value.take(SERIAL_PREFIX_CHARS).padEnd(status.value.length, '*')})"
        is P12Status.Media -> "Media(code=%02X%s)".format(status.code, if (status.continuous) ", continuous" else "")
        is P12Status.Other -> "Other(tag=%02X, payload=%s)".format(status.tag, status.payload.joinToString(" ") { "%02X".format(it) })
        is P12Status.Unframed -> "Unframed(${status.bytes.joinToString(" ") { "%02X".format(it) }})"
        else -> status.toString()
    }
