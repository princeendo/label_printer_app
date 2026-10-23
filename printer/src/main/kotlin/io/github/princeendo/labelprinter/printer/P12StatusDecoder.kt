// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.printer

/** A status message from the printer. Replies are framed as `1A <tag> <payload>`. */
public sealed interface P12Status {
    /** Values outside 0..100 have been seen on other models with unknown meaning. */
    public data class Battery(
        val raw: Int,
    ) : P12Status {
        val percent: Int? get() = raw.takeIf { it in 0..100 }
    }

    /** The P12 reports `99` for open and `98` for closed, the inverse of some other models. */
    public data class Cover(
        val open: Boolean,
    ) : P12Status

    /**
     * `89` means tape present. `88` is assumed to mean absent but has not been observed: a
     * third-party test with an empty roll still reported `89`, so this is not reliable.
     */
    public data class Tape(
        val present: Boolean,
    ) : P12Status

    public data class Media(
        val code: Int,
    ) : P12Status {
        val continuous: Boolean get() = code == 0x0B
    }

    public data class Serial(
        val value: String,
    ) : P12Status

    public data class Firmware(
        val major: Int,
        val minor: Int,
        val patch: Int,
    ) : P12Status {
        override fun toString(): String = "Firmware($major.$minor.$patch)"
    }

    /** A framed reply whose meaning is not decoded. */
    public data class Other(
        val tag: Int,
        val payload: List<Int>,
    ) : P12Status

    /** Bytes outside any `1A` frame, such as BLE write acknowledgements on sibling models. */
    public data class Unframed(
        val bytes: List<Int>,
    ) : P12Status
}

/**
 * Incrementally decodes the printer's reply stream. Replies may arrive split across reads,
 * concatenated, duplicated, or unsolicited (the P12 reports cover changes on its own).
 *
 * Payload lengths come from observed replies. For an unknown tag the frame is assumed to end at
 * the next `1A` byte, which is a guess; such frames are reported as [P12Status.Other].
 */
public class P12StatusDecoder {
    private val buffer = ArrayList<Int>()

    public fun feed(bytes: ByteArray): List<P12Status> {
        bytes.forEach { buffer += it.toInt() and 0xFF }
        return drain(endOfStream = false)
    }

    /** Emits anything still buffered, such as an incomplete frame at the end of a session. */
    public fun flush(): List<P12Status> = drain(endOfStream = true)

    private fun drain(endOfStream: Boolean): List<P12Status> {
        val out = mutableListOf<P12Status>()
        while (buffer.isNotEmpty()) {
            val consumed = decodeOne(out, endOfStream)
            if (consumed == 0) break
            buffer.subList(0, consumed).clear()
        }
        return out
    }

    /** Appends at most one message to [out] and returns how many buffered bytes it used. */
    private fun decodeOne(
        out: MutableList<P12Status>,
        endOfStream: Boolean,
    ): Int {
        if (buffer[0] != FRAME_START) {
            val end = buffer.indexOf(FRAME_START).takeIf { it > 0 } ?: buffer.size
            out += P12Status.Unframed(buffer.subList(0, end).toList())
            return end
        }
        if (buffer.size < 2) return incomplete(out, endOfStream)

        val tag = buffer[1]
        val payloadLength = PAYLOAD_LENGTHS[tag]
        if (payloadLength == null) {
            val next = buffer.subList(2, buffer.size).indexOf(FRAME_START)
            if (next < 0 && !endOfStream) return 0
            val end = if (next < 0) buffer.size else next + 2
            out += P12Status.Other(tag, buffer.subList(2, end).toList())
            return end
        }
        if (buffer.size < 2 + payloadLength) return incomplete(out, endOfStream)

        out += decode(tag, buffer.subList(2, 2 + payloadLength).toList())
        return 2 + payloadLength
    }

    private fun incomplete(
        out: MutableList<P12Status>,
        endOfStream: Boolean,
    ): Int {
        if (!endOfStream) return 0
        out += P12Status.Unframed(buffer.toList())
        return buffer.size
    }

    private fun decode(
        tag: Int,
        payload: List<Int>,
    ): P12Status =
        when (tag) {
            TAG_BATTERY -> P12Status.Battery(payload[0])
            TAG_COVER ->
                when (payload[0]) {
                    0x99 -> P12Status.Cover(open = true)
                    0x98 -> P12Status.Cover(open = false)
                    else -> P12Status.Other(tag, payload)
                }
            TAG_TAPE ->
                when (payload[0]) {
                    0x89 -> P12Status.Tape(present = true)
                    0x88 -> P12Status.Tape(present = false)
                    else -> P12Status.Other(tag, payload)
                }
            TAG_MEDIA -> P12Status.Media(payload[0])
            TAG_SERIAL -> P12Status.Serial(String(ByteArray(payload.size) { payload[it].toByte() }, Charsets.US_ASCII))
            TAG_FIRMWARE -> P12Status.Firmware(payload[0], payload[1], payload[2])
            else -> P12Status.Other(tag, payload)
        }

    private companion object {
        const val FRAME_START = 0x1A
        const val TAG_BATTERY = 0x04
        const val TAG_COVER = 0x05
        const val TAG_TAPE = 0x06
        const val TAG_FIRMWARE = 0x07
        const val TAG_SERIAL = 0x08
        const val TAG_MEDIA = 0x0C

        /** Observed on a P12 unless noted; 09, 0B and 0F are from sibling models. */
        val PAYLOAD_LENGTHS =
            mapOf(
                0x03 to 1,
                TAG_BATTERY to 1,
                TAG_COVER to 1,
                TAG_TAPE to 1,
                TAG_FIRMWARE to 3,
                TAG_SERIAL to 15,
                0x09 to 1,
                0x0B to 1,
                TAG_MEDIA to 1,
                0x0F to 1,
                0x17 to 1,
            )
    }
}
