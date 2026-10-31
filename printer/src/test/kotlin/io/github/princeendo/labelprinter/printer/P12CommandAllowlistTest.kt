// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.printer

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class P12CommandAllowlistTest {
    private fun hex(text: String): ByteArray = text.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `allows documented commands, alone and concatenated`() {
        listOf(
            "1F 11 38",
            "1F 11 08",
            "1F 11 11 1F 11 12 1F 11 09 1F 11 13",
            "1B 40",
            "1B 40 1D 76 30 00 0C 00 EF 01",
            "1F 11 02 01",
            "1F 11 02 02",
            "1F 11 02 04",
            "1F 11 02 04 1B 40 1D 76 30 00 0C 00 0B 01",
        ).forEach { assertNull(P12CommandAllowlist.refusal(hex(it)), it) }
    }

    @Test
    fun `refuses commands that can damage the printer`() {
        listOf(
            "1B 37 07 A0 02", // ESC 7 heat parameters
            "1F 11 0F", // firmware-update mode
            "1B 4E 08 41", // serial rewrite
        ).forEach { assertNotNull(P12CommandAllowlist.refusal(hex(it)), it) }
    }

    @Test
    fun `refuses unverified, malformed, and smuggled commands`() {
        listOf(
            "1F 11 02 03", // density level Print Master never sends
            "1F 11 02", // truncated density
            "1F 11 0E", // observed from Print Master but not understood
            "1F 11 4B", // observed from Print Master but not understood
            "1F 11", // truncated query
            "1D 76 30 00 30 00 01 00", // 48-byte line width
            "1D 76 30 00 0C 00 00 00", // zero rows
            "1D 76 30 00 0C 00 01", // truncated header
            "1B 64 0D", // feed, not sent by Print Master on FW 0.1.9
            "1B 40 1F 11 0F", // allowed command followed by a forbidden one
            "00",
        ).forEach { assertNotNull(P12CommandAllowlist.refusal(hex(it)), it) }
        assertNotNull(P12CommandAllowlist.refusal(ByteArray(0)))
    }

    @Test
    fun `requireAllowed throws for refused commands`() {
        assertFailsWith<IllegalArgumentException> { P12CommandAllowlist.requireAllowed(hex("1F 11 0F")) }
    }
}
