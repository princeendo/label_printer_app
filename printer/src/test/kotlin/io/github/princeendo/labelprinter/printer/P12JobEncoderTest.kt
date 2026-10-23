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
    fun `starts with the six observed query writes, each awaiting a reply`() {
        val writes = P12JobEncoder().encode(blank(1)).writes()

        val expected =
            listOf(
                "1F 11 38",
                "1F 11 11 1F 11 12 1F 11 09 1F 11 13",
                "1F 11 09",
                "1F 11 19 1F 11 11",
                "1F 11 19",
                "1F 11 07",
            )
        expected.forEachIndexed { i, bytes ->
            assertContentEquals(hex(bytes), writes[i].bytes, "query write $i")
            assertTrue(writes[i].awaitReply)
            assertEquals(JobStep.Kind.COMMAND, writes[i].kind)
        }
    }

    @Test
    fun `header encodes the row count little-endian`() {
        val cases =
            mapOf(
                1 to "1B 40 1D 76 30 00 0C 00 01 00",
                255 to "1B 40 1D 76 30 00 0C 00 FF 00",
                256 to "1B 40 1D 76 30 00 0C 00 00 01",
                495 to "1B 40 1D 76 30 00 0C 00 EF 01",
                65535 to "1B 40 1D 76 30 00 0C 00 FF FF",
            )
        for ((rows, header) in cases) {
            val write = P12JobEncoder().encode(blank(rows)).writes()[6]
            assertContentEquals(hex(header), write.bytes, "header for $rows rows")
            assertEquals(false, write.awaitReply)
        }
    }

    @Test
    fun `raster bytes follow the header in row order, then the end-of-job feed`() {
        // Row 0 burns dot 0 (first byte 80); row 1 burns dot 95 (last byte 01).
        val raster = MonoRaster.create(96, 2) { x, y -> (y == 0 && x == 0) || (y == 1 && x == 95) }

        val writes = P12JobEncoder().encode(raster).writes()
        val rasterBytes = writes.filter { it.kind == JobStep.Kind.RASTER }.flatMap { it.bytes.toList() }

        val expected = ByteArray(24)
        expected[0] = 0x80.toByte()
        expected[23] = 0x01
        assertContentEquals(expected, rasterBytes.toByteArray())
        assertContentEquals(hex("1B 64 0D 1B 64 0D"), writes.last().bytes)
    }

    @Test
    fun `total length matches the documented stream layout`() {
        // 30 query bytes + 10 header bytes + 12 per row + 6 feed bytes.
        assertEquals(30 + 10 + 12 * 495 + 6, P12JobEncoder().encode(blank(495)).writtenBytes().size)
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
        assertEquals(1, steps.count { it is JobStep.Pause })
    }

    @Test
    fun `ends with a wait covering estimated print time plus settling`() {
        // 799 rows / (203 / 25.4) rows per mm = 99.97 mm; at 60 mm/s that is 1666.2 ms, rounded up.
        val plan = P12JobEncoder().encode(blank(799))

        assertEquals(JobStep.Pause(1667 + 3000), plan.steps.last())
    }

    @Test
    fun `every command write passes the allowlist`() {
        P12JobEncoder()
            .encode(blank(10))
            .writes()
            .filter { it.kind == JobStep.Kind.COMMAND }
            .forEach { P12CommandAllowlist.requireAllowed(it.bytes) }
    }

    @Test
    fun `rejects rasters that are not head width or too long`() {
        assertFailsWith<IllegalArgumentException> { P12JobEncoder().encode(MonoRaster.create(88, 1) { _, _ -> false }) }
        assertFailsWith<IllegalArgumentException> { P12JobEncoder().encode(MonoRaster.create(104, 1) { _, _ -> false }) }
        assertFailsWith<IllegalArgumentException> { P12JobEncoder().encode(blank(65536)) }
    }
}
