// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.bringup

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import io.github.princeendo.labelprinter.printer.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.thread

/** Bluetooth Classic RFCOMM to the printer's Serial Port Profile service, found by SDP lookup. */
@SuppressLint("MissingPermission") // Callers hold BLUETOOTH_CONNECT before connecting.
class SppTransport private constructor(
    private val socket: BluetoothSocket,
) : Transport {
    private val replies = Channel<ByteArray>(Channel.UNLIMITED)
    override val incoming: Flow<ByteArray> = replies.receiveAsFlow()

    init {
        thread(name = "spp-reader", isDaemon = true) {
            val buffer = ByteArray(512)
            try {
                while (true) {
                    val count = socket.inputStream.read(buffer)
                    if (count < 0) break
                    replies.trySend(buffer.copyOf(count))
                }
            } catch (_: IOException) {
                // The socket was closed or the link dropped; the closed channel reports it.
            } finally {
                replies.close()
            }
        }
    }

    override suspend fun write(bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            socket.outputStream.write(bytes)
            socket.outputStream.flush()
        }
    }

    override suspend fun close() {
        withContext(Dispatchers.IO) { socket.close() }
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

        suspend fun connect(
            adapter: BluetoothAdapter,
            device: BluetoothDevice,
        ): SppTransport =
            withContext(Dispatchers.IO) {
                adapter.cancelDiscovery()
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                try {
                    socket.connect()
                } catch (e: IOException) {
                    socket.close()
                    throw e
                }
                SppTransport(socket)
            }
    }
}
