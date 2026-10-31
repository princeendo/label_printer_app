// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.bringup

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import io.github.princeendo.labelprinter.printer.MonoRaster
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SampleRastersTest {
    @Test
    fun `landscape top-left lands at the high head dot of the first row`() {
        // 3 dots along the tape, 2 across; only the top-left pixel burns.
        val raster = landscapeToRaster(lengthDots = 3, heightDots = 2) { u, v -> u == 0 && v == 0 }

        assertEquals(2, raster.widthDots)
        assertEquals(3, raster.rows)
        assertTrue(raster[1, 0])
        assertEquals(1, (0 until 2).sumOf { x -> (0 until 3).count { y -> raster[x, y] } })
    }

    @Test
    fun `stacked parts are centered and separated by blank rows`() {
        val a = MonoRaster.create(2, 1) { _, _ -> true }
        val b = MonoRaster.create(4, 2) { _, _ -> true }

        val stacked = stackAlongTape(listOf(a, b), widthDots = 6, gapRows = 1)

        assertEquals(4, stacked.rows)
        assertEquals(listOf(2, 3), (0 until 6).filter { stacked[it, 0] })
        assertTrue((0 until 6).none { stacked[it, 1] })
        assertEquals(listOf(1, 2, 3, 4), (0 until 6).filter { stacked[it, 2] })
        assertEquals(listOf(1, 2, 3, 4), (0 until 6).filter { stacked[it, 3] })
    }

    @Test
    fun `QR samples fit the printable band and decode`() {
        for (moduleDots in listOf(3, 4)) {
            val text = "P12 QR $moduleDots-DOT"
            val raster = qrRaster(text, moduleDots)

            assertTrue(raster.widthDots <= 88, "QR with $moduleDots-dot modules is ${raster.widthDots} dots wide")
            assertEquals(text, decodeQr(raster))
        }
    }

    @Test
    fun `serial digits after the prefix are masked`() {
        val reply = byteArrayOf(0x1A, 0x08) + "ABCD12345678901".toByteArray()

        val hex = redactedHex(reply)

        assertEquals("1A 08 41 42 43 44" + " **".repeat(11), hex)
        assertFalse("31" in hex)
    }

    @Test
    fun `other replies and long writes are shown plainly`() {
        assertEquals("1A 04 4B", redactedHex(byteArrayOf(0x1A, 0x04, 0x4B)))
        assertEquals("00 00 … (5 bytes)", redactedHex(ByteArray(5), limit = 2))
    }

    private fun decodeQr(raster: MonoRaster): String {
        val quiet = 16
        val width = raster.widthDots + 2 * quiet
        val height = raster.rows + 2 * quiet
        val pixels =
            IntArray(width * height) { i ->
                val x = i % width - quiet
                val y = i / width - quiet
                val burned = x in 0 until raster.widthDots && y in 0 until raster.rows && raster[x, y]
                if (burned) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))
        return QRCodeReader().decode(bitmap).text
    }
}
