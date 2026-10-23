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
 * Turns a full-width head raster into the documented P12 job: the six status queries, `ESC @`
 * plus the `GS v 0` header, the raster in chunks, the end-of-job feed, and a wait long enough for
 * the printer to finish. The P12 sends no completion signal, so closing the connection earlier
 * may cut the print short.
 */
public class P12JobEncoder(
    private val options: Options = Options(),
) {
    public data class Options(
        /** Raster bytes per write. 3072 is 256 rows; BLE needs much smaller chunks. */
        val rasterChunkBytes: Int = 256 * P12Profile.BYTES_PER_LINE,
        /** Pause after each raster chunk except the last. */
        val chunkPauseMillis: Long = 0,
        /** Assumed print speed, used only to estimate when the job has finished. */
        val feedMmPerSecond: Double = 60.0,
        /** Extra wait after the estimated print time, covering feed and mechanical settling. */
        val settleMillis: Long = 3_000,
    ) {
        init {
            require(rasterChunkBytes > 0) { "chunk size must be positive, was $rasterChunkBytes" }
            require(chunkPauseMillis >= 0) { "chunk pause must not be negative, was $chunkPauseMillis" }
            require(feedMmPerSecond > 0) { "feed speed must be positive, was $feedMmPerSecond" }
            require(settleMillis >= 0) { "settle time must not be negative, was $settleMillis" }
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
        P12Commands.jobQueries().forEach { steps += JobStep.Write(it, JobStep.Kind.COMMAND, awaitReply = true) }
        steps += JobStep.Write(P12Commands.initialize() + P12Commands.rasterHeader(raster.rows), JobStep.Kind.COMMAND)

        val packed = raster.packedBytes()
        for (start in packed.indices step options.rasterChunkBytes) {
            if (start > 0 && options.chunkPauseMillis > 0) steps += JobStep.Pause(options.chunkPauseMillis)
            val end = minOf(start + options.rasterChunkBytes, packed.size)
            steps += JobStep.Write(packed.copyOfRange(start, end), JobStep.Kind.RASTER)
        }

        steps += JobStep.Write(P12Commands.endOfJob(), JobStep.Kind.COMMAND)
        steps += JobStep.Pause(estimatedPrintMillis(raster.rows) + options.settleMillis)
        return JobPlan(steps)
    }

    internal fun estimatedPrintMillis(rows: Int): Long = ceil(rows / P12Profile.DOTS_PER_MM / options.feedMmPerSecond * 1000).toLong()
}
