// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.printer

import java.io.ByteArrayOutputStream
import kotlin.math.ceil

/** One step of a print job, executed in order by [runJob]. */
public sealed interface JobStep {
    /**
     * Sends [bytes]. A [Kind.COMMAND] write must pass [P12CommandAllowlist]. When [awaitReply] is
     * set, the runner waits briefly for the printer to answer before continuing.
     */
    public class Write(
        bytes: ByteArray,
        public val kind: Kind,
        public val awaitReply: Boolean = false,
    ) : JobStep {
        private val content = bytes.copyOf()

        public val bytes: ByteArray get() = content.copyOf()
        public val size: Int get() = content.size

        override fun toString(): String = "Write($kind, $size bytes, awaitReply=$awaitReply)"
    }

    public data class Pause(
        val millis: Long,
    ) : JobStep

    /** Waits for the printer's job-complete reply; the job fails if none arrives within [timeoutMillis]. */
    public data class AwaitCompletion(
        val timeoutMillis: Long,
    ) : JobStep

    public enum class Kind { COMMAND, RASTER }
}

public data class JobPlan(
    val steps: List<JobStep>,
) {
    /** Every byte the plan writes, in order. */
    public fun writtenBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        steps.filterIsInstance<JobStep.Write>().forEach { out.write(it.bytes) }
        return out.toByteArray()
    }
}

/**
 * Turns a full-width head raster into a P12 job framed as Print Master 5.23.1 sends it: the
 * density, `ESC @` plus the `GS v 0` header, and the raster in chunks, followed by a wait for the
 * printer's job-complete reply. Closing the connection before that reply may cut the print short.
 */
public class P12JobEncoder(
    private val options: Options = Options(),
) {
    public data class Options(
        val density: P12Density = P12Density.STANDARD,
        /** Raster bytes per write. 3072 is 256 rows; BLE needs much smaller chunks. */
        val rasterChunkBytes: Int = 256 * P12Profile.BYTES_PER_LINE,
        /** Pause after each raster chunk except the last. */
        val chunkPauseMillis: Long = 0,
        /**
         * The slowest print speed allowed for before giving up on the completion reply. A 33 mm
         * label took 8.1 s at heavy density on FW 0.1.9, about 4 mm/s.
         */
        val slowestMmPerSecond: Double = 2.0,
        /** Added to the completion timeout for leader feed and start-up. */
        val completionMarginMillis: Long = 10_000,
    ) {
        init {
            require(rasterChunkBytes > 0) { "chunk size must be positive, was $rasterChunkBytes" }
            require(chunkPauseMillis >= 0) { "chunk pause must not be negative, was $chunkPauseMillis" }
            require(slowestMmPerSecond > 0) { "slowest speed must be positive, was $slowestMmPerSecond" }
            require(completionMarginMillis >= 0) { "completion margin must not be negative, was $completionMarginMillis" }
        }
    }

    public fun encode(raster: MonoRaster): JobPlan {
        require(raster.widthDots == P12Profile.HEAD_DOTS) {
            "raster must be ${P12Profile.HEAD_DOTS} dots wide (use P12Profile.toHeadRaster), was ${raster.widthDots}"
        }
        require(raster.rows <= P12Commands.MAX_ROWS) {
            "raster has ${raster.rows} rows; one job holds at most ${P12Commands.MAX_ROWS}"
        }

        val steps = mutableListOf<JobStep>()
        steps += JobStep.Write(P12Commands.density(options.density), JobStep.Kind.COMMAND)
        steps += JobStep.Write(P12Commands.initialize() + P12Commands.rasterHeader(raster.rows), JobStep.Kind.COMMAND)

        val packed = raster.packedBytes()
        for (start in packed.indices step options.rasterChunkBytes) {
            if (start > 0 && options.chunkPauseMillis > 0) steps += JobStep.Pause(options.chunkPauseMillis)
            val end = minOf(start + options.rasterChunkBytes, packed.size)
            steps += JobStep.Write(packed.copyOfRange(start, end), JobStep.Kind.RASTER)
        }

        steps += JobStep.AwaitCompletion(completionTimeoutMillis(raster.rows))
        return JobPlan(steps)
    }

    internal fun completionTimeoutMillis(rows: Int): Long =
        ceil(rows / P12Profile.DOTS_PER_MM / options.slowestMmPerSecond * 1000).toLong() + options.completionMarginMillis
}
