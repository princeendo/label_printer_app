// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.printer

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SimulatedP12Test {
    private val profile = P12Profile()
    private val patterns =
        mapOf(
            "T1 width" to TestPatterns.widthCalibration(),
            "T2 ruler" to TestPatterns.lengthRuler(profile),
            "T3 quality" to TestPatterns.qualityStrip(profile),
        )

    @Test
    fun `every pattern round trips through the simulator with default chunks`() =
        runTest {
            for ((name, raster) in patterns) {
                val printer = SimulatedP12()
                runJob(printer, P12JobEncoder().encode(raster))

                assertEquals(listOf(SimulatedP12.PrintedJob(raster, P12Density.STANDARD)), printer.jobs, name)
                assertEquals(emptyList(), printer.protocolErrors, name)
            }
        }

    @Test
    fun `round trips with BLE-sized chunks that split rows`() =
        runTest {
            val raster = TestPatterns.qualityStrip(profile)
            val printer = SimulatedP12()
            runJob(printer, P12JobEncoder(P12JobEncoder.Options(rasterChunkBytes = 128, chunkPauseMillis = 20)).encode(raster))

            assertEquals(raster, printer.jobs.single().raster)
            assertEquals(emptyList(), printer.protocolErrors)
        }

    @Test
    fun `reports completion after each job, and the density set before it`() =
        runTest {
            val printer = SimulatedP12()
            val raster = TestPatterns.qualityStrip(profile)
            for (density in listOf(P12Density.LIGHT, P12Density.HEAVY)) {
                val received = runJob(printer, P12JobEncoder(P12JobEncoder.Options(density = density)).encode(raster))
                assertEquals(listOf(P12Status.JobComplete), P12StatusDecoder().feed(received))
            }

            assertEquals(listOf(P12Density.LIGHT, P12Density.HEAVY), printer.jobs.map { it.density })
        }

    @Test
    fun `runJob fails when the printer never reports completion`() =
        runTest {
            val silent =
                object : Transport {
                    override suspend fun write(bytes: ByteArray) = Unit

                    override val incoming: Flow<ByteArray> = emptyFlow()

                    override suspend fun close() = Unit
                }

            assertFailsWith<IllegalStateException> { runJob(silent, P12JobEncoder().encode(TestPatterns.qualityStrip(profile))) }
        }

    @Test
    fun `answers the connect queries with decodable status`() =
        runTest {
            val printer = SimulatedP12(SimulatedP12.State(batteryPercent = 40))
            val plan = JobPlan(P12Commands.connectQueries().map { JobStep.Write(it, JobStep.Kind.COMMAND, awaitReply = true) })
            val statuses = P12StatusDecoder().feed(runJob(printer, plan))

            // Queries per write: 1 + 4 + 1 + 2 + 1 + 1 = 10 replies.
            assertEquals(10, statuses.size)
            assertTrue(P12Status.Cover(open = false) in statuses)
            assertTrue(P12Status.Tape(present = true) in statuses)
            assertTrue(P12Status.Serial("SIM000000000000") in statuses)
            assertTrue(P12Status.Firmware(1, 0, 0) in statuses)
            assertTrue(statuses.none { it is P12Status.Unframed })
        }

    @Test
    fun `answers a battery query and reports cover changes unprompted`() =
        runTest {
            val printer = SimulatedP12(SimulatedP12.State(batteryPercent = 40))
            val plan = JobPlan(listOf(JobStep.Write(P12Query.BATTERY.bytes(), JobStep.Kind.COMMAND, awaitReply = true)))

            val decoder = P12StatusDecoder()
            assertEquals(listOf(P12Status.Battery(40)), decoder.feed(runJob(printer, plan)))

            printer.setCoverOpen(true)
            assertEquals(
                listOf(P12Status.Battery(40), P12Status.Cover(open = true)),
                decoder.feed(runJob(printer, plan)).sortedBy { it is P12Status.Cover },
            )
        }

    @Test
    fun `records bytes it cannot interpret`() =
        runTest {
            val printer = SimulatedP12()
            printer.write(byteArrayOf(0x1B, 0x37, 0x1F, 0x11, 0x0F))

            assertEquals(listOf("unexpected byte 1B", "unexpected byte 37", "unknown query 1F 11 0F"), printer.protocolErrors)
        }

    @Test
    fun `runJob refuses a plan with a forbidden command before writing anything`() =
        runTest {
            val printer = SimulatedP12()
            val plan =
                JobPlan(
                    listOf(
                        JobStep.Write(P12Query.BATTERY.bytes(), JobStep.Kind.COMMAND, awaitReply = true),
                        JobStep.Write(byteArrayOf(0x1F, 0x11, 0x0F), JobStep.Kind.COMMAND),
                    ),
                )

            assertFailsWith<IllegalArgumentException> { runJob(printer, plan) }
            assertEquals(emptyList(), printer.protocolErrors)
            assertTrue(printer.jobs.isEmpty())
        }

    @Test
    fun `a closed simulator rejects writes`() =
        runTest {
            val printer = SimulatedP12()
            printer.close()

            assertFailsWith<IllegalStateException> { printer.write(P12Query.BATTERY.bytes()) }
        }
}
