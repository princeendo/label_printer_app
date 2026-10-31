// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.printer

/**
 * Hardware calibration patterns from the validation plan in docs/RESEARCH.md. All return
 * full-width head rasters ready for [P12JobEncoder]. Text and QR samples need font rendering and
 * are added by the Android bring-up app, not here.
 *
 * P12 jobs send no feed command, so a job's last rows stay inside the printer, behind the cutter.
 * The calibration patterns therefore end with [TRAILER_ROWS] blank rows.
 */
public object TestPatterns {
    /** Blank rows ending each pattern: 10 mm, which carried 2026-10-30's ladder bars past the cutter. */
    public const val TRAILER_ROWS: Int = 80

    /** Returns [raster] followed by [TRAILER_ROWS] blank rows. */
    public fun withTrailer(raster: MonoRaster): MonoRaster =
        MonoRaster.create(raster.widthDots, raster.rows + TRAILER_ROWS) { x, y -> y < raster.rows && raster[x, y] }

    /** Rows per wedge step in [widthCalibration]: 1 mm. */
    public const val WEDGE_STEP_ROWS: Int = 8

    /** Wedge steps per edge in [widthCalibration]. */
    public const val WEDGE_STEPS: Int = 16

    /**
     * T1: finds which of the 96 head dots actually print, ignoring any profile.
     *
     * In feed order:
     * 1. a solid bar across all 96 dots;
     * 2. twelve 2 mm blocks, block `k` covering dots `8k..8k+7`, stepping across the tape;
     * 3. a low-edge wedge: step `d` burns dots `0..d`, 1 mm per step;
     * 4. a high-edge wedge: step `d` burns dots `95-d..95`.
     *
     * The number of blank wedge steps at each edge is the number of unprintable dots there.
     */
    public fun widthCalibration(): MonoRaster {
        val last = P12Profile.HEAD_DOTS - 1
        return withTrailer(
            buildPattern(P12Profile.HEAD_DOTS) {
                band(16) { _, _ -> true }
                gap(8)
                for (k in 0 until P12Profile.BYTES_PER_LINE) band(16) { x, _ -> x / 8 == k }
                gap(8)
                for (d in 0 until WEDGE_STEPS) band(WEDGE_STEP_ROWS) { x, _ -> x <= d }
                gap(8)
                for (d in 0 until WEDGE_STEPS) band(WEDGE_STEP_ROWS) { x, _ -> x >= last - d }
            },
        )
    }

    /**
     * T2: a ruler [lengthMm] long within the printable band, for measuring the length factor.
     * Every 10 mm is a full-width line two rows thick, every 5 mm a half-width line, every other
     * millimetre a quarter-width line. Both edges of the printable band are drawn full length.
     */
    public fun lengthRuler(
        profile: P12Profile,
        lengthMm: Int = 100,
    ): MonoRaster {
        require(lengthMm > 0) { "length must be positive, was $lengthMm" }
        val width = profile.printableWidthDots
        val tickWidthByRow = mutableMapOf<Int, Int>()
        for (mm in 0..lengthMm) {
            val row = profile.mmToRows(mm.toDouble())
            when {
                mm % 10 == 0 -> {
                    tickWidthByRow[row] = width
                    tickWidthByRow[row + 1] = width
                }
                mm % 5 == 0 -> tickWidthByRow[row] = width / 2
                else -> tickWidthByRow[row] = width / 4
            }
        }
        val rows = profile.mmToRows(lengthMm.toDouble()) + 2
        val content =
            MonoRaster.create(width, rows) { x, y ->
                x == 0 || x == width - 1 || x < (tickWidthByRow[y] ?: 0)
            }
        return withTrailer(profile.toHeadRaster(content))
    }

    /**
     * T3: print-quality sections within the printable band, each 5 mm long with 1 mm gaps:
     * solid, 1-dot checkerboard, 2-dot checkerboard, horizontal hairlines every 4 rows, and
     * vertical hairlines every 4 dots.
     */
    public fun qualityStrip(profile: P12Profile): MonoRaster {
        val section = profile.mmToRows(5.0)
        val gap = profile.mmToRows(1.0)
        val content =
            buildPattern(profile.printableWidthDots) {
                band(section) { _, _ -> true }
                gap(gap)
                band(section) { x, y -> (x + y) % 2 == 0 }
                gap(gap)
                band(section) { x, y -> (x / 2 + y / 2) % 2 == 0 }
                gap(gap)
                band(section) { _, y -> y % 4 == 0 }
                gap(gap)
                band(section) { x, _ -> x % 4 == 0 }
            }
        return withTrailer(profile.toHeadRaster(content))
    }

    /**
     * A 2 mm solid bar [widthDots] wide, centered in the printable band, with 2 mm blank before and
     * 10 mm after, so the bar clears the cutter without a feed command. Every bar row burns exactly
     * [widthDots] dots, for finding how many dots the printer can burn at once: on 2026-10-30 a
     * 96-dot solid bar switched the P12 off mid-job.
     */
    public fun loadBar(
        profile: P12Profile,
        widthDots: Int,
    ): MonoRaster {
        require(widthDots in 1..profile.printableWidthDots) { "bar width must be in 1..${profile.printableWidthDots}, was $widthDots" }
        val margin = profile.mmToRows(2.0)
        val start = (profile.printableWidthDots - widthDots) / 2
        val content =
            buildPattern(profile.printableWidthDots) {
                gap(margin)
                band(margin) { x, _ -> x in start until start + widthDots }
                gap(profile.mmToRows(10.0))
            }
        return profile.toHeadRaster(content)
    }

    private class PatternBuilder {
        val rows = mutableListOf<(Int) -> Boolean>()

        /** Appends [count] rows; [isBurned] receives the dot and the row index within the band. */
        fun band(
            count: Int,
            isBurned: (x: Int, rowInBand: Int) -> Boolean,
        ) {
            repeat(count) { row -> rows += { x -> isBurned(x, row) } }
        }

        fun gap(count: Int) = band(count) { _, _ -> false }
    }

    private fun buildPattern(
        width: Int,
        build: PatternBuilder.() -> Unit,
    ): MonoRaster {
        val rows = PatternBuilder().apply(build).rows
        return MonoRaster.create(width, rows.size) { x, y -> rows[y](x) }
    }
}
