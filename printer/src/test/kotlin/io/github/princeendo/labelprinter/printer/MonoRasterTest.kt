// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.printer

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MonoRasterTest {
    @Test
    fun `packs rows most significant bit first`() {
        // Row 0 burns dots 0 and 9; row 1 burns dot 15.
        val raster = MonoRaster.create(16, 2) { x, y -> (y == 0 && (x == 0 || x == 9)) || (y == 1 && x == 15) }

        assertContentEquals(byteArrayOf(0x80.toByte(), 0x40, 0x00, 0x01), raster.packedBytes())
        assertTrue(raster[0, 0])
        assertTrue(raster[9, 0])
        assertFalse(raster[1, 0])
        assertTrue(raster[15, 1])
    }

    @Test
    fun `pads partial bytes with zero bits`() {
        val raster = MonoRaster.create(10, 1) { _, _ -> true }

        assertEquals(2, raster.bytesPerRow)
        assertContentEquals(byteArrayOf(0xFF.toByte(), 0xC0.toByte()), raster.packedBytes())
    }

    @Test
    fun `fromPacked round trips and rejects set padding bits`() {
        val original = MonoRaster.create(10, 3) { x, y -> (x + y) % 3 == 0 }

        assertEquals(original, MonoRaster.fromPacked(10, 3, original.packedBytes()))
        assertFailsWith<IllegalArgumentException> { MonoRaster.fromPacked(10, 1, byteArrayOf(0x00, 0x01)) }
        assertFailsWith<IllegalArgumentException> { MonoRaster.fromPacked(10, 1, byteArrayOf(0x00)) }
    }

    @Test
    fun `rejects empty dimensions and out of range pixels`() {
        assertFailsWith<IllegalArgumentException> { MonoRaster.create(0, 1) { _, _ -> false } }
        assertFailsWith<IllegalArgumentException> { MonoRaster.create(8, 0) { _, _ -> false } }
        val raster = MonoRaster.create(8, 1) { _, _ -> false }
        assertFailsWith<IllegalArgumentException> { raster[8, 0] }
    }

    @Test
    fun `packed bytes are a defensive copy`() {
        val raster = MonoRaster.create(8, 1) { _, _ -> false }
        raster.packedBytes()[0] = 0x7F

        assertFalse(raster[1, 0])
    }
}
