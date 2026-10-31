// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.printer

import kotlin.math.roundToInt

/**
 * Geometry of one P12 unit: which head dots can actually print on 12 mm tape, and how far the
 * tape really advances per row.
 *
 * The length factor is measured on this project's unit. The printable dots still come from a
 * third-party measurement of another P12 (dots 4..91) and await confirmation; see
 * docs/hardware/2026-10-30-first-prints.md.
 */
public data class P12Profile(
    val printableOffsetDots: Int = DEFAULT_PRINTABLE_OFFSET_DOTS,
    val printableWidthDots: Int = DEFAULT_PRINTABLE_WIDTH_DOTS,
    /** Rows per nominal millimetre are multiplied by this; > 1 when the printer under-feeds. */
    val lengthFactor: Double = DEFAULT_LENGTH_FACTOR,
) {
    init {
        require(printableOffsetDots >= 0) { "offset must not be negative, was $printableOffsetDots" }
        require(printableWidthDots > 0) { "printable width must be positive, was $printableWidthDots" }
        require(printableOffsetDots + printableWidthDots <= HEAD_DOTS) {
            "offset $printableOffsetDots + width $printableWidthDots exceeds $HEAD_DOTS head dots"
        }
        require(lengthFactor in 0.9..1.1) { "length factor $lengthFactor is outside 0.9..1.1" }
    }

    /** Converts a physical length along the tape to raster rows, applying [lengthFactor]. */
    public fun mmToRows(mm: Double): Int = (mm * DOTS_PER_MM * lengthFactor).roundToInt()

    /**
     * Places [content] on a full-width head raster, starting at [printableOffsetDots].
     * Content wider than [printableWidthDots] is rejected rather than clipped.
     */
    public fun toHeadRaster(content: MonoRaster): MonoRaster {
        require(content.widthDots <= printableWidthDots) {
            "content is ${content.widthDots} dots wide; the printable width is $printableWidthDots"
        }
        return MonoRaster.create(HEAD_DOTS, content.rows) { x, y ->
            val contentX = x - printableOffsetDots
            contentX in 0 until content.widthDots && content[contentX, y]
        }
    }

    public companion object {
        public const val HEAD_DOTS: Int = 96
        public const val BYTES_PER_LINE: Int = HEAD_DOTS / 8
        public const val DOTS_PER_INCH: Int = 203
        public const val DOTS_PER_MM: Double = DOTS_PER_INCH / 25.4
        public const val DEFAULT_PRINTABLE_OFFSET_DOTS: Int = 4
        public const val DEFAULT_PRINTABLE_WIDTH_DOTS: Int = 88

        /** This project's P12 printed a 100 mm ruler at 100.5–100.8 mm on 2026-10-30 (FW 0.1.9). */
        public const val DEFAULT_LENGTH_FACTOR: Double = 0.994
    }
}
