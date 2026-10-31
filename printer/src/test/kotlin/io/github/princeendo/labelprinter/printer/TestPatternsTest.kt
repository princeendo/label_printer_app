// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.printer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestPatternsTest {
    private fun MonoRaster.burnedDots(y: Int) = (0 until widthDots).filter { this[it, y] }

    @Test
    fun `width calibration covers the whole head in the documented order`() {
        val t1 = TestPatterns.widthCalibration()

        assertEquals(96, t1.widthDots)
        assertEquals(16 + 8 + 12 * 16 + 8 + 16 * 8 + 8 + 16 * 8 + TestPatterns.TRAILER_ROWS, t1.rows)
        assertTrue(t1.burnedDots(t1.rows - TestPatterns.TRAILER_ROWS).isEmpty())
        assertEquals((0 until 96).toList(), t1.burnedDots(0))
        // Byte blocks start after the solid bar and gap.
        for (k in 0 until 12) assertEquals((8 * k until 8 * k + 8).toList(), t1.burnedDots(24 + 16 * k), "block $k")
        // Wedges: step d burns d + 1 dots from each edge.
        val lowStart = 24 + 12 * 16 + 8
        val highStart = lowStart + 16 * 8 + 8
        assertEquals(listOf(0), t1.burnedDots(lowStart))
        assertEquals((0..15).toList(), t1.burnedDots(lowStart + 15 * 8))
        assertEquals(listOf(95), t1.burnedDots(highStart))
        assertEquals((80..95).toList(), t1.burnedDots(highStart + 15 * 8))
    }

    @Test
    fun `ruler ticks land on millimetre rows inside the printable band`() {
        val profile = P12Profile(lengthFactor = 1.0152)
        val ruler = TestPatterns.lengthRuler(profile, lengthMm = 20)

        assertEquals(profile.mmToRows(20.0) + 2 + TestPatterns.TRAILER_ROWS, ruler.rows)
        val band = 4..91
        assertEquals(band.toList(), ruler.burnedDots(profile.mmToRows(10.0)))
        assertEquals(band.toList(), ruler.burnedDots(profile.mmToRows(10.0) + 1))
        assertEquals((4 until 4 + 44).toList() + 91, ruler.burnedDots(profile.mmToRows(5.0)))
        assertEquals((4 until 4 + 22).toList() + 91, ruler.burnedDots(profile.mmToRows(1.0)))
        assertEquals(listOf(4, 91), ruler.burnedDots(profile.mmToRows(1.0) + 2))
    }

    @Test
    fun `profile-based patterns never burn outside the printable band`() {
        val profile = P12Profile(printableOffsetDots = 8, printableWidthDots = 80)
        for (raster in listOf(TestPatterns.lengthRuler(profile), TestPatterns.qualityStrip(profile))) {
            val outside = (0 until raster.rows).flatMap { y -> raster.burnedDots(y).filter { it !in 8 until 88 } }
            assertTrue(outside.isEmpty(), "dots outside band: $outside")
        }
    }

    @Test
    fun `load bar burns exactly the requested dots per row, centered in the band`() {
        val profile = P12Profile()
        val bar = TestPatterns.loadBar(profile, 24)
        val margin = profile.mmToRows(2.0)

        assertEquals(2 * margin + profile.mmToRows(10.0), bar.rows)
        assertTrue(bar.burnedDots(margin - 1).isEmpty())
        assertEquals((36 until 60).toList(), bar.burnedDots(margin))
        assertEquals(24, (0 until bar.rows).maxOf { bar.burnedDots(it).size })
        assertTrue(bar.burnedDots(2 * margin).isEmpty())
    }

    @Test
    fun `quality strip has five 5 mm sections separated by 1 mm gaps`() {
        val profile = P12Profile()
        val strip = TestPatterns.qualityStrip(profile)
        val section = profile.mmToRows(5.0)
        val gap = profile.mmToRows(1.0)

        assertEquals(5 * section + 4 * gap + TestPatterns.TRAILER_ROWS, strip.rows)
        assertEquals((4..91).toList(), strip.burnedDots(0))
        assertTrue(strip.burnedDots(section).isEmpty())
        assertEquals((4..91 step 2).toList(), strip.burnedDots(section + gap))
    }
}
