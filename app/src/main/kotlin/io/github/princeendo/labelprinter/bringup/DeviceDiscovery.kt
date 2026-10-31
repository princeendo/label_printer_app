// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.bringup

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Parcelable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Finds candidate printers: bonded devices, Classic discovery, and a BLE scan, recording which
 * of these saw each device. Names and sources go to the log; addresses do not.
 */
@SuppressLint("MissingPermission") // Callers hold BLUETOOTH_SCAN and BLUETOOTH_CONNECT first.
class DeviceDiscovery(
    private val context: Context,
    private val adapter: BluetoothAdapter,
    private val log: DebugLog,
) {
    enum class Source { BONDED, CLASSIC, BLE }

    data class Found(
        val device: BluetoothDevice,
        val name: String?,
        val sources: Set<Source>,
    )

    private val found = MutableStateFlow<Map<String, Found>>(emptyMap())

    /** Seen devices keyed by address. */
    val devices: StateFlow<Map<String, Found>> = found.asStateFlow()

    fun loadBonded() {
        adapter.bondedDevices.orEmpty().forEach { record(it, Source.BONDED, rssi = null) }
    }

    /** Runs Classic discovery and a BLE scan together for [durationMillis]. */
    suspend fun scan(durationMillis: Long) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    val device = intent.parcelable<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    record(device, Source.CLASSIC, rssi.takeIf { it != Short.MIN_VALUE.toInt() })
                }
            }
        val leCallback =
            object : ScanCallback() {
                override fun onScanResult(
                    callbackType: Int,
                    result: ScanResult,
                ) {
                    record(result.device, Source.BLE, result.rssi)
                }

                override fun onScanFailed(errorCode: Int) {
                    log.add("BLE scan failed (error $errorCode)")
                }
            }

        context.registerSystemReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        val leScanner = adapter.bluetoothLeScanner
        try {
            val classic = if (adapter.startDiscovery()) "on" else "refused"
            log.add("Discovery started (Classic $classic, BLE ${if (leScanner != null) "on" else "unavailable"})")
            leScanner?.startScan(leCallback)
            delay(durationMillis)
        } finally {
            leScanner?.stopScan(leCallback)
            adapter.cancelDiscovery()
            context.unregisterReceiver(receiver)
            log.add("Discovery stopped")
        }
    }

    /** Logs the cached service UUIDs of [device], then asks it for fresh SDP records. */
    suspend fun logSdp(device: BluetoothDevice) {
        log.add("SDP cached UUIDs: ${device.uuids?.joinToString() ?: "none"}")
        val result = CompletableDeferred<List<Parcelable>>()
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    val from = intent.parcelable<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (from?.address != device.address) return
                    @Suppress("DEPRECATION")
                    result.complete(intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)?.toList().orEmpty())
                }
            }
        context.registerSystemReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_UUID))
        try {
            adapter.cancelDiscovery()
            if (!device.fetchUuidsWithSdp()) {
                log.add("SDP lookup refused")
                return
            }
            val uuids = withTimeoutOrNull(SDP_TIMEOUT_MILLIS) { result.await() }
            log.add(if (uuids == null) "SDP lookup timed out" else "SDP UUIDs: ${uuids.joinToString().ifEmpty { "none" }}")
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    private fun record(
        device: BluetoothDevice,
        source: Source,
        rssi: Int?,
    ) {
        val name = device.name
        var isNew = false
        found.update { current ->
            val previous = current[device.address]
            isNew = previous == null || source !in previous.sources
            val sources = previous?.sources.orEmpty() + source
            current + (device.address to Found(device, name ?: previous?.name, sources))
        }
        if (isNew) {
            val signal = rssi?.let { ", RSSI $it" } ?: ""
            log.add("Found ${name ?: "(no name)"} via $source, type ${typeName(device.type)}, bond ${bondName(device.bondState)}$signal")
        }
    }

    private companion object {
        const val SDP_TIMEOUT_MILLIS = 10_000L

        fun typeName(type: Int): String =
            when (type) {
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> "classic"
                BluetoothDevice.DEVICE_TYPE_LE -> "LE"
                BluetoothDevice.DEVICE_TYPE_DUAL -> "dual"
                else -> "unknown"
            }

        fun bondName(state: Int): String =
            when (state) {
                BluetoothDevice.BOND_BONDED -> "bonded"
                BluetoothDevice.BOND_BONDING -> "bonding"
                else -> "none"
            }
    }
}

private inline fun <reified T : Parcelable> Intent.parcelable(name: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(name)
    }

/** Registers for a protected system broadcast, which only the system can send. */
private fun Context.registerSystemReceiver(
    receiver: BroadcastReceiver,
    filter: IntentFilter,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
    } else {
        registerReceiver(receiver, filter)
    }
}
