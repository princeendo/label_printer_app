// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.printer

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class P12JobEncoderTest {
    private fun blank(rows: Int) = MonoRaster.create(96, rows) { _, _ -> false }

    private fun hex(text: String): ByteArray = text.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    private fun JobPlan.writes() = steps.filterIsInstance<JobStep.Write>()

    @Test
    fun `starts with the density, standard by default`() {
        val first = P12JobEncoder().encode(blank(1)).writes().first()
        assertContentEquals(hex("1F 11 02 02"), first.bytes)
        assertEquals(JobStep.Kind.COMMAND, first.kind)

        val heavy = P12JobEncoder(P12JobEncoder.Options(density = P12Density.HEAVY)).encode(blank(1)).writes().first()
        assertContentEquals(hex("1F 11 02 04"), heavy.bytes)
    }

    @Test
    fun `header encodes the row count little-endian`() {
        val cases =
            mapOf(
                1 to "1B 40 1D 76 30 00 0C 00 01 00",
                255 to "1B 40 1D 76 30 00 0C 00 FF 00",
                256 to "1B 40 1D 76 30 00 0C 00 00 01",
                267 to "1B 40 1D 76 30 00 0C 00 0B 01",
                65535 to "1B 40 1D 76 30 00 0C 00 FF FF",
            )
        for ((rows, header) in cases) {
            val write = P12JobEncoder().encode(blank(rows)).writes()[1]
            assertContentEquals(hex(header), write.bytes, "header for $rows rows")
            assertEquals(false, write.awaitReply)
        }
    }

    @Test
    fun `raster bytes follow the header in row order, and nothing is written after them`() {
        // Row 0 burns dot 0 (first byte 80); row 1 burns dot 95 (last byte 01).
        val raster = MonoRaster.create(96, 2) { x, y -> (y == 0 && x == 0) || (y == 1 && x == 95) }

        val writes = P12JobEncoder().encode(raster).writes()

        val expected = ByteArray(24)
        expected[0] = 0x80.toByte()
        expected[23] = 0x01
        assertEquals(listOf(JobStep.Kind.COMMAND, JobStep.Kind.COMMAND, JobStep.Kind.RASTER), writes.map { it.kind })
        assertContentEquals(expected, writes.last().bytes)
    }

    @Test
    fun `total length matches the stream Print Master sends`() {
        // 4 density bytes + 10 header bytes + 12 per row; Print Master's 267-row TEST label was 3218 bytes.
        assertEquals(3218, P12JobEncoder().encode(blank(267)).writtenBytes().size)
    }

    @Test
    fun `splits the raster into chunks with pauses between them`() {
        val options = P12JobEncoder.Options(rasterChunkBytes = 128, chunkPauseMillis = 20)
        val steps = P12JobEncoder(options).encode(blank(25)).steps // 300 raster bytes

        val rasterIndex = steps.indexOfFirst { it is JobStep.Write && it.kind == JobStep.Kind.RASTER }
        val rasterSteps = steps.subList(rasterIndex, rasterIndex + 5)
        assertEquals(listOf(128, 128, 44), rasterSteps.filterIsInstance<JobStep.Write>().map { it.size })
        assertEquals(listOf(JobStep.Pause(20), JobStep.Pause(20)), rasterSteps.filterIsInstance<JobStep.Pause>())
        assertTrue(rasterSteps[1] is JobStep.Pause && rasterSteps[3] is JobStep.Pause)
    }

    @Test
    fun `default chunks are 256 rows with no pauses`() {
        val steps = P12JobEncoder().encode(blank(600)).steps
        val rasterWrites = steps.filterIsInstance<JobStep.Write>().filter { it.kind == JobStep.Kind.RASTER }

        assertEquals(listOf(3072, 3072, 1056), rasterWrites.map { it.size })
        assertTrue(steps.none { it is JobStep.Pause })
    }

    @Test
    fun `ends by awaiting completion for the slowest allowed speed plus a margin`() {
        // 799 rows / (203 / 25.4) rows per mm = 99.973 mm; at 2 mm/s that is 49986.7 ms, rounded up.
        val plan = P12JobEncoder().encode(blank(799))

        assertEquals(JobStep.AwaitCompletion(49987 + 10_000), plan.steps.last())
    }

    @Test
    fun `every command write passes the allowlist`() {
        for (density in P12Density.entries) {
            P12JobEncoder(P12JobEncoder.Options(density = density))
                .encode(blank(10))
                .writes()
                .filter { it.kind == JobStep.Kind.COMMAND }
                .forEach { P12CommandAllowlist.requireAllowed(it.bytes) }
        }
    }

    @Test
    fun `rejects rasters that are not head width or too long`() {
        assertFailsWith<IllegalArgumentException> { P12JobEncoder().encode(MonoRaster.create(88, 1) { _, _ -> false }) }
        assertFailsWith<IllegalArgumentException> { P12JobEncoder().encode(MonoRaster.create(104, 1) { _, _ -> false }) }
        assertFailsWith<IllegalArgumentException> { P12JobEncoder().encode(blank(65536)) }
    }
}
