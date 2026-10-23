// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.printer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class P12ProfileTest {
    @Test
    fun `places content at the printable offset`() {
        val profile = P12Profile(printableOffsetDots = 4, printableWidthDots = 88)
        val content = MonoRaster.create(88, 1) { x, _ -> x == 0 || x == 87 }

        val head = profile.toHeadRaster(content)

        assertEquals(96, head.widthDots)
        assertEquals((0 until 96).filter { head[it, 0] }, listOf(4, 91))
    }

    @Test
    fun `narrower content starts at the offset and leaves the rest blank`() {
        val head = P12Profile(printableOffsetDots = 8).toHeadRaster(MonoRaster.create(2, 1) { _, _ -> true })

        assertEquals(listOf(8, 9), (0 until 96).filter { head[it, 0] })
    }

    @Test
    fun `rejects content wider than the printable band`() {
        assertFailsWith<IllegalArgumentException> {
            P12Profile(printableWidthDots = 88).toHeadRaster(MonoRaster.create(89, 1) { _, _ -> false })
        }
    }

    @Test
    fun `rejects a window outside the head`() {
        assertFailsWith<IllegalArgumentException> { P12Profile(printableOffsetDots = 9, printableWidthDots = 88) }
        assertFailsWith<IllegalArgumentException> { P12Profile(printableOffsetDots = -1) }
        assertFailsWith<IllegalArgumentException> { P12Profile(lengthFactor = 1.5) }
    }

    @Test
    fun `converts millimetres to rows at 203 dpi with the length factor`() {
        assertEquals(799, P12Profile().mmToRows(100.0)) // 100 * 203 / 25.4 = 799.2
        assertEquals(811, P12Profile(lengthFactor = 1.0152).mmToRows(100.0)) // 811.3
        assertEquals(0, P12Profile().mmToRows(0.0))
    }

    @Test
    fun `defaults match the third-party measurement`() {
        val profile = P12Profile()
        val head = profile.toHeadRaster(MonoRaster.create(88, 1) { _, _ -> true })

        assertFalse(head[3, 0])
        assertTrue(head[4, 0])
        assertTrue(head[91, 0])
        assertFalse(head[92, 0])
    }
}
