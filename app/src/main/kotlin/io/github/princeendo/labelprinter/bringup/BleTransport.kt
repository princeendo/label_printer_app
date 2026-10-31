// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.bringup

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import io.github.princeendo.labelprinter.printer.Transport
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID

/**
 * BLE GATT to the printer: writes go to `FF02` and replies arrive as `FF03` notifications, both
 * in service `FF00`. Each write is split to fit the negotiated MTU and waits for the stack to
 * accept it before the next is issued. Pacing between raster chunks is the encoder's job.
 */
@SuppressLint("MissingPermission") // Callers hold BLUETOOTH_CONNECT before connecting.
class BleTransport private constructor(
    private val log: (String) -> Unit,
) : Transport {
    private sealed interface Event {
        data class State(
            val status: Int,
            val newState: Int,
        ) : Event

        data class Services(
            val status: Int,
        ) : Event

        data class Mtu(
            val mtu: Int,
            val status: Int,
        ) : Event

        data class DescriptorWritten(
            val status: Int,
        ) : Event

        data class Written(
            val status: Int,
        ) : Event
    }

    private lateinit var gatt: BluetoothGatt
    private lateinit var writeCharacteristic: BluetoothGattCharacteristic
    private var writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    private val events = Channel<Event>(Channel.UNLIMITED)
    private val replies = Channel<ByteArray>(Channel.UNLIMITED)
    private val writeLock = Mutex()

    /** ATT MTU in effect; a write carries at most `mtu - 3` bytes. */
    var mtu: Int = DEFAULT_MTU
        private set

    override val incoming: Flow<ByteArray> = replies.receiveAsFlow()

    private val callback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                events.trySend(Event.State(status, newState))
                if (newState == BluetoothProfile.STATE_DISCONNECTED) replies.close()
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                events.trySend(Event.Services(status))
            }

            override fun onMtuChanged(
                gatt: BluetoothGatt,
                mtu: Int,
                status: Int,
            ) {
                events.trySend(Event.Mtu(mtu, status))
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) {
                events.trySend(Event.DescriptorWritten(status))
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                events.trySend(Event.Written(status))
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                replies.trySend(value.copyOf())
            }

            @Deprecated("Called instead of the three-argument overload before Android 13")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                @Suppress("DEPRECATION")
                replies.trySend(characteristic.value.copyOf())
            }
        }

    private suspend fun open(
        context: Context,
        device: BluetoothDevice,
    ) {
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        val state = awaitEvent<Event.State>("connection")
        if (state.status != BluetoothGatt.GATT_SUCCESS || state.newState != BluetoothProfile.STATE_CONNECTED) {
            throw IOException("GATT connection failed (status ${state.status})")
        }

        gatt.requestMtu(REQUESTED_MTU)
        try {
            val result = awaitEvent<Event.Mtu>("MTU", timeoutMillis = 5_000)
            if (result.status == BluetoothGatt.GATT_SUCCESS) mtu = result.mtu
            log("BLE MTU ${result.mtu} (status ${result.status}); requested $REQUESTED_MTU")
        } catch (_: TimeoutCancellationException) {
            log("BLE MTU request timed out; using $mtu")
        }

        gatt.discoverServices()
        val discovered = awaitEvent<Event.Services>("service discovery")
        if (discovered.status != BluetoothGatt.GATT_SUCCESS) throw IOException("service discovery failed (status ${discovered.status})")
        gatt.services.forEach { service ->
            log("GATT service ${service.uuid}")
            service.characteristics.forEach { log("  characteristic ${it.uuid} properties ${describeProperties(it.properties)}") }
        }

        val service = gatt.getService(SERVICE_UUID) ?: throw IOException("printer service $SERVICE_UUID not found")
        writeCharacteristic = service.getCharacteristic(WRITE_UUID) ?: throw IOException("write characteristic $WRITE_UUID not found")
        val notify = service.getCharacteristic(NOTIFY_UUID) ?: throw IOException("notify characteristic $NOTIFY_UUID not found")

        val writeProperties = writeCharacteristic.properties
        writeType =
            if (writeProperties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
        val noResponse = writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        log("BLE writes use ${if (noResponse) "write-without-response" else "write-with-response"}")

        enableNotifications(notify)
    }

    private suspend fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
        check(gatt.setCharacteristicNotification(characteristic, true)) { "could not enable notifications locally" }
        val descriptor = characteristic.getDescriptor(CCCD_UUID) ?: throw IOException("notify characteristic has no CCCD")
        val value =
            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            }
        val started =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = value
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        if (!started) throw IOException("could not start the CCCD write")
        val written = awaitEvent<Event.DescriptorWritten>("CCCD write")
        if (written.status != BluetoothGatt.GATT_SUCCESS) throw IOException("CCCD write failed (status ${written.status})")
    }

    override suspend fun write(bytes: ByteArray) {
        writeLock.withLock {
            val maxChunk = mtu - ATT_HEADER_BYTES
            for (start in bytes.indices step maxChunk) {
                writeOne(bytes.copyOfRange(start, minOf(start + maxChunk, bytes.size)))
            }
        }
    }

    private suspend fun writeOne(chunk: ByteArray) {
        var attempts = 0
        while (!startWrite(chunk)) {
            // The stack refuses a write while the previous one is still queued; back off briefly.
            if (++attempts >= MAX_BUSY_RETRIES) throw IOException("BLE write refused $attempts times")
            delay(BUSY_RETRY_MILLIS)
        }
        val written = awaitEvent<Event.Written>("write acknowledgement")
        if (written.status != BluetoothGatt.GATT_SUCCESS) throw IOException("BLE write failed (status ${written.status})")
    }

    private fun startWrite(chunk: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(writeCharacteristic, chunk, writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            writeCharacteristic.writeType = writeType
            @Suppress("DEPRECATION")
            writeCharacteristic.value = chunk
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(writeCharacteristic)
        }

    override suspend fun close() {
        if (::gatt.isInitialized) {
            gatt.disconnect()
            gatt.close()
        }
        replies.close()
    }

    /** Waits for the next [T], failing if the link drops first. Other stale events are skipped. */
    private suspend inline fun <reified T : Event> awaitEvent(
        what: String,
        timeoutMillis: Long = 10_000,
    ): T =
        withTimeout(timeoutMillis) {
            var event = events.receive()
            while (event !is T) {
                if (event is Event.State && event.newState == BluetoothProfile.STATE_DISCONNECTED) {
                    throw IOException("disconnected while waiting for $what (status ${event.status})")
                }
                event = events.receive()
            }
            event
        }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")
        val WRITE_UUID: UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")
        val NOTIFY_UUID: UUID = UUID.fromString("0000ff03-0000-1000-8000-00805f9b34fb")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val DEFAULT_MTU = 23
        private const val REQUESTED_MTU = 517
        private const val ATT_HEADER_BYTES = 3
        private const val MAX_BUSY_RETRIES = 50
        private const val BUSY_RETRY_MILLIS = 10L

        suspend fun connect(
            context: Context,
            device: BluetoothDevice,
            log: (String) -> Unit,
        ): BleTransport {
            val transport = BleTransport(log)
            try {
                transport.open(context, device)
            } catch (e: Throwable) {
                transport.close()
                throw e
            }
            return transport
        }

        private fun describeProperties(properties: Int): String =
            listOf(
                BluetoothGattCharacteristic.PROPERTY_READ to "read",
                BluetoothGattCharacteristic.PROPERTY_WRITE to "write",
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE to "write-no-response",
                BluetoothGattCharacteristic.PROPERTY_NOTIFY to "notify",
                BluetoothGattCharacteristic.PROPERTY_INDICATE to "indicate",
            ).filter { properties and it.first != 0 }.joinToString(",") { it.second }.ifEmpty { "none" }
    }
}
