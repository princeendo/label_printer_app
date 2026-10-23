// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.printer

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * A printer stand-in for tests and for development without hardware. It parses the written
 * bytes the way this project understands the P12 protocol, turns each completed raster back into
 * a [MonoRaster], and answers status queries with the replies observed on a real P12.
 *
 * It models the protocol as documented, not the real printer's timing or failure behavior. Not
 * thread-safe; use from one coroutine at a time.
 */
public class SimulatedP12(
    initialState: State = State(),
) : Transport {
    public data class State(
        val batteryPercent: Int = 75,
        val coverOpen: Boolean = false,
        val tapePresent: Boolean = true,
        val mediaCode: Int = 0x0B,
        val serial: String = "SIM000000000000",
        val firmware: P12Status.Firmware = P12Status.Firmware(1, 0, 0),
    ) {
        init {
            require(serial.length == 15 && serial.all { it.code in 0x20..0x7E }) { "serial must be 15 printable ASCII characters" }
        }
    }

    /** A completed raster and the feed lines sent after it. */
    public data class PrintedJob(
        val raster: MonoRaster,
        val feedLines: Int,
    )

    public var state: State = initialState
        private set

    private val replies = Channel<ByteArray>(Channel.UNLIMITED)
    override val incoming: Flow<ByteArray> = replies.receiveAsFlow()

    private val pending = ArrayList<Int>()
    private var rasterRowsExpected = 0
    private val printed = mutableListOf<PrintedJob>()
    private val errors = mutableListOf<String>()
    private var closed = false

    public val jobs: List<PrintedJob> get() = printed.toList()

    /** Bytes the simulator could not interpret. A correct encoder produces none. */
    public val protocolErrors: List<String> get() = errors.toList()

    override suspend fun write(bytes: ByteArray) {
        check(!closed) { "transport is closed" }
        bytes.forEach { pending += it.toInt() and 0xFF }
        parse()
    }

    override suspend fun close() {
        closed = true
        replies.close()
    }

    /** Changes the cover state and, like a real P12, reports it without being asked. */
    public fun setCoverOpen(open: Boolean) {
        state = state.copy(coverOpen = open)
        replies.trySend(reply(P12Query.COVER))
    }

    public fun updateState(transform: (State) -> State) {
        state = transform(state)
    }

    private fun parse() {
        while (pending.isNotEmpty()) {
            val consumed = if (rasterRowsExpected > 0) consumeRaster() else consumeCommand()
            if (consumed == 0) return
            pending.subList(0, consumed).clear()
        }
    }

    private fun consumeRaster(): Int {
        val needed = rasterRowsExpected * P12Profile.BYTES_PER_LINE
        if (pending.size < needed) return 0
        val packed = ByteArray(needed) { pending[it].toByte() }
        printed += PrintedJob(MonoRaster.fromPacked(P12Profile.HEAD_DOTS, rasterRowsExpected, packed), feedLines = 0)
        rasterRowsExpected = 0
        return needed
    }

    private fun consumeCommand(): Int {
        fun at(i: Int) = pending.getOrNull(i)
        return when {
            at(0) == 0x1F && at(1) == 0x11 -> {
                val code = at(2) ?: return 0
                val query = P12Query.fromCode(code)
                if (query == null) errors += "unknown query 1F 11 %02X".format(code) else replies.trySend(reply(query))
                3
            }
            at(0) == 0x1B && at(1) == 0x40 -> 2
            at(0) == 0x1B && at(1) == 0x64 -> {
                val lines = at(2) ?: return 0
                if (printed.isEmpty()) {
                    errors += "feed with no printed job"
                } else {
                    printed[printed.lastIndex] = printed.last().let { it.copy(feedLines = it.feedLines + lines) }
                }
                3
            }
            at(0) == 0x1D && at(1) == 0x76 -> {
                if (pending.size < 8) return 0
                val header = pending.subList(0, 8)
                val rows = header[6] or (header[7] shl 8)
                if (header.subList(2, 6) != listOf(0x30, 0x00, P12Profile.BYTES_PER_LINE, 0x00) || rows == 0) {
                    errors += "unsupported raster header ${header.joinToString(" ") { "%02X".format(it) }}"
                } else {
                    rasterRowsExpected = rows
                }
                8
            }
            pending.size < 2 && (at(0) == 0x1F || at(0) == 0x1B || at(0) == 0x1D) -> 0
            else -> {
                errors += "unexpected byte %02X".format(pending[0])
                1
            }
        }
    }

    private fun reply(query: P12Query): ByteArray {
        val s = state
        val (tag, payload) =
            when (query) {
                P12Query.FIRMWARE -> 0x07 to listOf(s.firmware.major, s.firmware.minor, s.firmware.patch)
                P12Query.BATTERY -> 0x04 to listOf(s.batteryPercent)
                P12Query.SERIAL -> 0x08 to s.serial.map { it.code }
                P12Query.TAPE -> 0x06 to listOf(if (s.tapePresent) 0x89 else 0x88)
                P12Query.COVER -> 0x05 to listOf(if (s.coverOpen) 0x99 else 0x98)
                P12Query.HEAT_STATE -> 0x03 to listOf(0xA8)
                P12Query.MEDIA_TYPE -> 0x0C to listOf(s.mediaCode)
                P12Query.CHIP_TYPE -> 0x17 to listOf(0x03)
            }
        return (listOf(0x1A, tag) + payload).map { it.toByte() }.toByteArray()
    }
}
