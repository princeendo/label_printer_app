// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.printer

/** Status queries, each sent as `1F 11 <code>`. Meanings marked unverified come from other models. */
public enum class P12Query(
    public val code: Int,
) {
    FIRMWARE(0x07),
    BATTERY(0x08),
    SERIAL(0x09),
    TAPE(0x11),
    COVER(0x12),

    /** Reply `1A 03 A8` observed on a P12; meaning unverified. */
    HEAT_STATE(0x13),
    MEDIA_TYPE(0x19),

    /** Reply `1A 17 03` observed on a P12; meaning unverified. */
    CHIP_TYPE(0x38),
    ;

    public fun bytes(): ByteArray = byteArrayOf(0x1F, 0x11, code.toByte())

    public companion object {
        public fun fromCode(code: Int): P12Query? = entries.firstOrNull { it.code == code }
    }
}

/** Print density levels offered by Print Master, with the value it sends for each. */
public enum class P12Density(
    public val code: Int,
) {
    LIGHT(0x01),
    STANDARD(0x02),
    HEAVY(0x04),
    ;

    public companion object {
        public fun fromCode(code: Int): P12Density? = entries.firstOrNull { it.code == code }
    }
}

/** The P12 command bytes this project has evidence for. See docs/RESEARCH.md, "Job Framing". */
public object P12Commands {
    public const val MAX_ROWS: Int = 0xFFFF

    /**
     * Status queries to send after connecting. Print Master 5.23.1 sends a similar set once per
     * connection, not per job; older captures sent these six writes before each job.
     */
    public fun connectQueries(): List<ByteArray> =
        listOf(
            P12Query.CHIP_TYPE.bytes(),
            P12Query.TAPE.bytes() + P12Query.COVER.bytes() + P12Query.SERIAL.bytes() + P12Query.HEAT_STATE.bytes(),
            P12Query.SERIAL.bytes(),
            P12Query.MEDIA_TYPE.bytes() + P12Query.TAPE.bytes(),
            P12Query.MEDIA_TYPE.bytes(),
            P12Query.FIRMWARE.bytes(),
        )

    /** `1F 11 02 n`: sets the density for the job that follows. */
    public fun density(level: P12Density): ByteArray = byteArrayOf(0x1F, 0x11, 0x02, level.code.toByte())

    /** `ESC @`. */
    public fun initialize(): ByteArray = byteArrayOf(0x1B, 0x40)

    /** `GS v 0` with mode 0, a 12-byte line width, and [rows] as little-endian 16 bits. */
    public fun rasterHeader(rows: Int): ByteArray {
        require(rows in 1..MAX_ROWS) { "rows must be in 1..$MAX_ROWS, was $rows" }
        return byteArrayOf(
            0x1D,
            0x76,
            0x30,
            0x00,
            P12Profile.BYTES_PER_LINE.toByte(),
            0x00,
            (rows and 0xFF).toByte(),
            (rows ushr 8).toByte(),
        )
    }
}

/**
 * Accepts only command writes built from documented P12 commands. Anything else is refused,
 * including heat parameters (`ESC 7`), firmware-update mode (`1F 11 0F`), and serial rewrite
 * (`1B 4E 08`), which can damage or brick a printer.
 *
 * Raster payload bytes are not commands and are not checked here.
 */
public object P12CommandAllowlist {
    /** Returns null when [bytes] is allowed, otherwise the reason it is refused. */
    public fun refusal(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return "empty command write"
        var i = 0
        while (i < bytes.size) {
            val length = allowedCommandLength(bytes, i) ?: return "unrecognized command at byte $i: ${bytes.toHex(i)}"
            i += length
        }
        return null
    }

    public fun requireAllowed(bytes: ByteArray) {
        refusal(bytes)?.let { throw IllegalArgumentException("refusing to send command: $it") }
    }

    private fun allowedCommandLength(
        bytes: ByteArray,
        start: Int,
    ): Int? {
        fun at(offset: Int): Int? = bytes.getOrNull(start + offset)?.toInt()?.and(0xFF)
        return when {
            at(0) == 0x1F && at(1) == 0x11 && at(2) == DENSITY_CODE -> at(3)?.let { if (P12Density.fromCode(it) != null) 4 else null }
            at(0) == 0x1F && at(1) == 0x11 -> at(2)?.let { if (P12Query.fromCode(it) != null) 3 else null }
            at(0) == 0x1B && at(1) == 0x40 -> 2
            at(0) == 0x1D && at(1) == 0x76 -> rasterHeaderLength(::at)
            else -> null
        }
    }

    private fun rasterHeaderLength(at: (Int) -> Int?): Int? {
        val expectedPrefix = listOf(0x1D, 0x76, 0x30, 0x00, P12Profile.BYTES_PER_LINE, 0x00)
        if (expectedPrefix.indices.any { at(it) != expectedPrefix[it] }) return null
        val low = at(6) ?: return null
        val high = at(7) ?: return null
        return if (low or (high shl 8) >= 1) 8 else null
    }

    private const val DENSITY_CODE = 0x02
}

internal fun ByteArray.toHex(
    from: Int = 0,
    limit: Int = 16,
): String {
    val end = minOf(size, from + limit)
    val hex = (from until end).joinToString(" ") { "%02X".format(this[it].toInt() and 0xFF) }
    return if (end < size) "$hex …" else hex
}
