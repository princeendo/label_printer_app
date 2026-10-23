// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.printer

/**
 * An immutable 1-bit image in print-head orientation.
 *
 * `x` runs across the tape (one print-head dot per pixel) and `y` runs along the tape in feed
 * order, so row 0 prints first. Rows are packed most-significant-bit first, and a set bit burns
 * a dot. Padding bits at the end of a row are always zero.
 */
public class MonoRaster private constructor(
    public val widthDots: Int,
    public val rows: Int,
    private val packed: ByteArray,
) {
    public val bytesPerRow: Int get() = bytesPerRow(widthDots)

    public operator fun get(
        x: Int,
        y: Int,
    ): Boolean {
        require(x in 0 until widthDots && y in 0 until rows) { "($x, $y) is outside ${widthDots}x$rows" }
        val byte = packed[y * bytesPerRow + x / 8].toInt()
        return byte and (0x80 ushr (x % 8)) != 0
    }

    /** Returns a copy of the packed rows, `bytesPerRow * rows` bytes long. */
    public fun packedBytes(): ByteArray = packed.copyOf()

    override fun equals(other: Any?): Boolean =
        other is MonoRaster &&
            widthDots == other.widthDots &&
            rows == other.rows &&
            packed.contentEquals(other.packed)

    override fun hashCode(): Int = 31 * (31 * widthDots + rows) + packed.contentHashCode()

    override fun toString(): String = "MonoRaster(${widthDots}x$rows)"

    public companion object {
        public fun bytesPerRow(widthDots: Int): Int = (widthDots + 7) / 8

        /** Builds a raster by asking [isBurned] for every pixel. */
        public fun create(
            widthDots: Int,
            rows: Int,
            isBurned: (x: Int, y: Int) -> Boolean,
        ): MonoRaster {
            requireDimensions(widthDots, rows)
            val bytesPerRow = bytesPerRow(widthDots)
            val packed = ByteArray(bytesPerRow * rows)
            for (y in 0 until rows) {
                for (x in 0 until widthDots) {
                    if (isBurned(x, y)) {
                        val index = y * bytesPerRow + x / 8
                        packed[index] = (packed[index].toInt() or (0x80 ushr (x % 8))).toByte()
                    }
                }
            }
            return MonoRaster(widthDots, rows, packed)
        }

        /** Wraps already packed rows; rejects data whose padding bits are set. */
        public fun fromPacked(
            widthDots: Int,
            rows: Int,
            packed: ByteArray,
        ): MonoRaster {
            requireDimensions(widthDots, rows)
            val bytesPerRow = bytesPerRow(widthDots)
            require(packed.size == bytesPerRow * rows) {
                "expected ${bytesPerRow * rows} bytes for ${widthDots}x$rows, got ${packed.size}"
            }
            val paddingBits = bytesPerRow * 8 - widthDots
            if (paddingBits > 0) {
                val paddingMask = (1 shl paddingBits) - 1
                for (y in 0 until rows) {
                    require(packed[y * bytesPerRow + bytesPerRow - 1].toInt() and paddingMask == 0) {
                        "row $y has padding bits set"
                    }
                }
            }
            return MonoRaster(widthDots, rows, packed.copyOf())
        }

        private fun requireDimensions(
            widthDots: Int,
            rows: Int,
        ) {
            require(widthDots > 0) { "width must be positive, was $widthDots" }
            require(rows > 0) { "rows must be positive, was $rows" }
        }
    }
}
