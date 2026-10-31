// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.printer

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import java.io.ByteArrayOutputStream

/** A byte pipe to one printer, such as an SPP socket, a BLE characteristic pair, or [SimulatedP12]. */
public interface Transport {
    public suspend fun write(bytes: ByteArray)

    /** Bytes received from the printer, in arrival order. Collected by one consumer at a time. */
    public val incoming: Flow<ByteArray>

    public suspend fun close()
}

/**
 * Executes [plan] on [transport] and returns every byte the printer sent while it ran, for
 * [P12StatusDecoder]. Command writes are checked against [P12CommandAllowlist] before anything is
 * sent. Waiting for a query reply only paces the job; a missing reply is not an error. A missing
 * job-complete reply is: it throws [IllegalStateException], and the print may still be running.
 */
public suspend fun runJob(
    transport: Transport,
    plan: JobPlan,
    replyTimeoutMillis: Long = 500,
): ByteArray {
    plan.steps
        .filterIsInstance<JobStep.Write>()
        .filter { it.kind == JobStep.Kind.COMMAND }
        .forEach { P12CommandAllowlist.requireAllowed(it.bytes) }

    return coroutineScope {
        val received = Channel<ByteArray>(Channel.UNLIMITED)
        // Undispatched so collection is subscribed before the first write can trigger a reply.
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { transport.incoming.collect { received.send(it) } }
        val log = ByteArrayOutputStream()

        for (step in plan.steps) {
            when (step) {
                is JobStep.Write -> {
                    transport.write(step.bytes)
                    if (step.awaitReply) withTimeoutOrNull(replyTimeoutMillis) { received.receive() }?.let(log::write)
                }
                is JobStep.Pause -> delay(step.millis)
                is JobStep.AwaitCompletion -> {
                    val decoder = P12StatusDecoder()
                    withTimeoutOrNull(step.timeoutMillis) {
                        while (true) {
                            val bytes = received.receive()
                            log.write(bytes)
                            if (P12Status.JobComplete in decoder.feed(bytes)) break
                        }
                    } ?: error("printer did not report completion within ${step.timeoutMillis} ms")
                }
            }
        }

        // Let the collector forward anything the transport already delivered before stopping it.
        yield()
        collector.cancel()
        while (true) log.write(received.tryReceive().getOrNull() ?: break)
        log.toByteArray()
    }
}
