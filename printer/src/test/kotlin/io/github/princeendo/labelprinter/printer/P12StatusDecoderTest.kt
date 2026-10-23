// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.printer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class P12StatusDecoderTest {
    private fun hex(text: String): ByteArray = text.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `decodes the replies observed on a P12`() {
        val serial = "SN0123456789ABC".map { "%02X".format(it.code) }.joinToString(" ")
        val replies =
            P12StatusDecoder().feed(
                hex("1A 04 4B 1A 05 98 1A 06 89 1A 0C 0B 1A 08 $serial 1A 07 01 00 00 1A 03 A8 1A 17 03"),
            )

        assertEquals(
            listOf(
                P12Status.Battery(75),
                P12Status.Cover(open = false),
                P12Status.Tape(present = true),
                P12Status.Media(0x0B),
                P12Status.Serial("SN0123456789ABC"),
                P12Status.Firmware(1, 0, 0),
                P12Status.Other(0x03, listOf(0xA8)),
                P12Status.Other(0x17, listOf(0x03)),
            ),
            replies,
        )
        assertEquals(75, (replies[0] as P12Status.Battery).percent)
        assertEquals(true, (replies[3] as P12Status.Media).continuous)
    }

    @Test
    fun `cover 99 means open on the P12`() {
        assertEquals(listOf(P12Status.Cover(open = true)), P12StatusDecoder().feed(hex("1A 05 99")))
    }

    @Test
    fun `reassembles replies split across reads`() {
        val decoder = P12StatusDecoder()

        assertEquals(emptyList(), decoder.feed(hex("1A")))
        assertEquals(emptyList(), decoder.feed(hex("07 01")))
        assertEquals(listOf(P12Status.Firmware(1, 0, 0), P12Status.Battery(50)), decoder.feed(hex("00 00 1A 04 32")))
    }

    @Test
    fun `a payload byte equal to the frame start does not split the frame`() {
        assertEquals(listOf(P12Status.Firmware(1, 0x1A, 0)), P12StatusDecoder().feed(hex("1A 07 01 1A 00")))
    }

    @Test
    fun `keeps duplicated and unsolicited replies`() {
        val replies = P12StatusDecoder().feed(hex("1A 05 98 1A 05 98 1A 05 99"))

        assertEquals(
            listOf(P12Status.Cover(open = false), P12Status.Cover(open = false), P12Status.Cover(open = true)),
            replies,
        )
    }

    @Test
    fun `reports bytes outside frames, then resynchronizes`() {
        val replies = P12StatusDecoder().feed(hex("01 01 1A 04 4B"))

        assertEquals(listOf(P12Status.Unframed(listOf(0x01, 0x01)), P12Status.Battery(75)), replies)
    }

    @Test
    fun `an unknown tag runs to the next frame, or to the end on flush`() {
        val decoder = P12StatusDecoder()

        assertEquals(listOf(P12Status.Other(0x40, listOf(0x01, 0x02))), decoder.feed(hex("1A 40 01 02 1A")))
        assertEquals(emptyList(), decoder.feed(hex("41 09")))
        assertEquals(listOf(P12Status.Other(0x41, listOf(0x09))), decoder.flush())
    }

    @Test
    fun `flush reports an incomplete known frame as unframed`() {
        val decoder = P12StatusDecoder()
        decoder.feed(hex("1A 07 01"))

        assertEquals(listOf(P12Status.Unframed(listOf(0x1A, 0x07, 0x01))), decoder.flush())
        assertEquals(emptyList(), decoder.flush())
    }

    @Test
    fun `undocumented values decode as other or without a percentage`() {
        val replies = P12StatusDecoder().feed(hex("1A 05 42 1A 06 00 1A 04 A1"))

        assertEquals(P12Status.Other(0x05, listOf(0x42)), replies[0])
        assertEquals(P12Status.Other(0x06, listOf(0x00)), replies[1])
        assertNull((replies[2] as P12Status.Battery).percent)
    }
}
