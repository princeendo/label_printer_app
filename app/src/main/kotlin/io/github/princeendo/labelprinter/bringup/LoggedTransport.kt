// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.bringup

import io.github.princeendo.labelprinter.printer.P12StatusDecoder
import io.github.princeendo.labelprinter.printer.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Wraps a connected transport so every byte in either direction reaches the log. One pump
 * collects [inner]'s replies for the whole connection, so unsolicited replies such as cover
 * changes are logged between jobs too, and re-emits them to whoever is collecting [incoming].
 * [onEnded] runs if the reply stream ends, such as when the link drops.
 */
class LoggedTransport(
    private val inner: Transport,
    private val log: DebugLog,
    scope: CoroutineScope,
    onEnded: () -> Unit,
) : Transport {
    private val decoder = P12StatusDecoder()
    private val replies = MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)

    override val incoming: Flow<ByteArray> = replies

    private val pump =
        scope.launch {
            inner.incoming.collect { bytes ->
                log.add("RX ${redactedHex(bytes)}")
                decoder.feed(bytes).forEach { log.add("     ${describe(it)}") }
                replies.emit(bytes)
            }
            decoder.flush().forEach { log.add("     ${describe(it)}") }
            log.add("Printer stream ended")
            onEnded()
        }

    override suspend fun write(bytes: ByteArray) {
        log.add("TX ${redactedHex(bytes, limit = 24)}")
        inner.write(bytes)
    }

    override suspend fun close() {
        inner.close()
        pump.cancel()
    }
}
