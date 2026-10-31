// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.bringup

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.princeendo.labelprinter.printer.JobPlan
import io.github.princeendo.labelprinter.printer.JobStep
import io.github.princeendo.labelprinter.printer.MonoRaster
import io.github.princeendo.labelprinter.printer.P12Commands
import io.github.princeendo.labelprinter.printer.P12Density
import io.github.princeendo.labelprinter.printer.P12JobEncoder
import io.github.princeendo.labelprinter.printer.P12Profile
import io.github.princeendo.labelprinter.printer.P12Query
import io.github.princeendo.labelprinter.printer.SimulatedP12
import io.github.princeendo.labelprinter.printer.TestPatterns
import io.github.princeendo.labelprinter.printer.runJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

/** State and actions for the hardware bring-up screen. One action runs at a time. */
@SuppressLint("MissingPermission") // The screen requests Bluetooth permissions before enabling these actions.
class BringUpViewModel(
    application: Application,
) : AndroidViewModel(application) {
    enum class Link { SPP, BLE }

    enum class Sample(
        val label: String,
    ) {
        T1("T1 width"),
        T2("T2 100 mm"),
        T3("T3 quality"),
        TEXT_QR("Text + QR"),
        FIVE_T3("5× T3"),
        RULER_300("300 mm"),
        RULER_1000("1 m"),
        BAR_LADDER("Bar ladder"),
    }

    data class DeviceRow(
        val key: String,
        val name: String,
        val detail: String,
    )

    data class UiState(
        val devices: List<DeviceRow> = listOf(SIMULATOR_ROW),
        val selectedKey: String = SIMULATOR_KEY,
        val link: Link = Link.SPP,
        val density: P12Density = P12Density.STANDARD,
        val connectedTo: String? = null,
        val busy: String? = null,
        val bleChunkBytes: String = "128",
        val blePauseMillis: String = "20",
        val previewName: String? = null,
        val preview: MonoRaster? = null,
        val bluetoothAvailable: Boolean = true,
    )

    val log = DebugLog(File(application.filesDir, LOG_FILE_NAME))

    private val adapter = application.getSystemService(BluetoothManager::class.java)?.adapter
    private val discovery = adapter?.let { DeviceDiscovery(application, it, log) }
    private val profile = P12Profile()
    private var transport: LoggedTransport? = null
    private var simulator: SimulatedP12? = null
    private var action: Job? = null

    private val mutableState = MutableStateFlow(UiState(bluetoothAvailable = adapter != null))
    val state: StateFlow<UiState> = mutableState.asStateFlow()

    init {
        val lastDot = profile.printableOffsetDots + profile.printableWidthDots - 1
        log.add("Profile: printable dots ${profile.printableOffsetDots}..$lastDot, length factor ${profile.lengthFactor}")
        discovery?.let { d ->
            viewModelScope.launch {
                d.devices.collect { found ->
                    val rows =
                        found.values
                            .sortedWith(printersFirst)
                            .map { DeviceRow(it.device.address, it.name ?: "(no name)", "${it.device.address} via ${it.sources}") }
                    mutableState.update { it.copy(devices = listOf(SIMULATOR_ROW) + rows) }
                }
            }
        }
    }

    fun onPermissionsGranted() {
        if (adapter?.isEnabled == true) discovery?.loadBonded() else log.add("Bluetooth is off or unavailable")
    }

    fun select(key: String) = mutableState.update { it.copy(selectedKey = key) }

    fun setLink(link: Link) = mutableState.update { it.copy(link = link) }

    fun setDensity(density: P12Density) = mutableState.update { it.copy(density = density) }

    fun setBleChunkBytes(text: String) = mutableState.update { it.copy(bleChunkBytes = text.filter(Char::isDigit).take(4)) }

    fun setBlePauseMillis(text: String) = mutableState.update { it.copy(blePauseMillis = text.filter(Char::isDigit).take(4)) }

    fun scan() = runAction("Scanning") { requireNotNull(discovery) { "Bluetooth unavailable" }.scan(SCAN_MILLIS) }

    fun lookUpSdp() =
        runAction("SDP lookup") {
            val found = discovery?.devices?.value?.get(state.value.selectedKey) ?: error("select a Bluetooth device first")
            log.add("SDP lookup for ${found.name ?: "(no name)"}")
            discovery.logSdp(found.device)
        }

    fun connect() =
        runAction("Connecting") {
            check(transport == null) { "already connected" }
            val selected = state.value.selectedKey
            val link = state.value.link
            val started = System.nanoTime()
            val (inner, label) =
                if (selected == SIMULATOR_KEY) {
                    val sim = SimulatedP12()
                    simulator = sim
                    sim to "Simulator"
                } else {
                    val found = discovery?.devices?.value?.get(selected) ?: error("device not found")
                    val name = found.name ?: "(no name)"
                    log.add("Connecting $link to $name (bond ${found.device.bondState})")
                    val connected =
                        when (link) {
                            Link.SPP -> SppTransport.connect(adapter!!, found.device)
                            Link.BLE -> BleTransport.connect(getApplication(), found.device, log::add)
                        }
                    connected to "$name over $link"
                }
            transport = LoggedTransport(inner, log, viewModelScope) { viewModelScope.launch { closeTransport() } }
            log.add("Connected to $label in ${elapsedMillis(started)} ms")
            mutableState.update { it.copy(connectedTo = label) }
            runConnectQueries()
        }

    fun disconnect() {
        action?.cancel()
        viewModelScope.launch {
            closeTransport()
            log.add("Disconnected")
        }
    }

    /** Sends the status queries an app sends on connecting; [connect] does this automatically. */
    fun sendConnectQueries() = runAction("Connect queries") { runConnectQueries() }

    private suspend fun runConnectQueries() {
        runPlan(JobPlan(P12Commands.connectQueries().map { JobStep.Write(it, JobStep.Kind.COMMAND, awaitReply = true) }))
    }

    /** Sends every known query on its own, so each reply can be timed. */
    fun sendEachQuery() =
        runAction("Each query") {
            runPlan(JobPlan(P12Query.entries.map { JobStep.Write(it.bytes(), JobStep.Kind.COMMAND, awaitReply = true) }))
        }

    fun print(sample: Sample) =
        runAction(sample.label) {
            when (sample) {
                Sample.T1 -> printRaster("T1", TestPatterns.widthCalibration())
                Sample.T2 -> printRaster("T2", TestPatterns.lengthRuler(profile))
                Sample.T3 -> printRaster("T3", TestPatterns.qualityStrip(profile))
                Sample.TEXT_QR -> printRaster("Text + QR", withContext(Dispatchers.Default) { textAndQrSample(profile) })
                Sample.FIVE_T3 -> repeat(5) { printRaster("T3 #${it + 1}", TestPatterns.qualityStrip(profile)) }
                Sample.RULER_300 -> printRaster("300 mm ruler", TestPatterns.lengthRuler(profile, 300))
                Sample.RULER_1000 -> printRaster("1 m ruler", TestPatterns.lengthRuler(profile, 1000))
                // Stops at the first job without a completion reply, since runJob throws.
                Sample.BAR_LADDER -> LADDER_DOTS.forEach { printRaster("Bar $it dots", TestPatterns.loadBar(profile, it)) }
            }
        }

    fun clearLog() = log.clear()

    fun exportLog(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(log.text().toByteArray()) }
                    ?: error("could not open the export file")
            }.onSuccess { log.add("Log exported") }
                .onFailure { log.add("Log export failed: ${it.message}") }
        }
    }

    private suspend fun printRaster(
        name: String,
        raster: MonoRaster,
    ) {
        mutableState.update { it.copy(previewName = name, preview = raster) }
        val density = state.value.density
        val plan = encoder(density).encode(raster)
        log.add(
            "Job $name: ${raster.rows} rows (%.1f mm), density $density, ${plan.steps.size} steps".format(
                raster.rows / P12Profile.DOTS_PER_MM,
            ),
        )
        val started = System.nanoTime()
        runPlan(plan)
        log.add("Job $name finished after ${elapsedMillis(started)} ms")
        simulator?.let { sim ->
            val matches = sim.jobs.lastOrNull()?.raster == raster
            log.add("Simulator: ${sim.jobs.size} jobs, last matches sent raster: $matches, protocol errors: ${sim.protocolErrors.size}")
            check(matches && sim.protocolErrors.isEmpty()) { "simulator did not receive the job intact" }
        }
    }

    private suspend fun runPlan(plan: JobPlan) {
        val connected = transport ?: error("not connected")
        runJob(connected, plan)
    }

    private fun encoder(density: P12Density): P12JobEncoder {
        val current = state.value
        if (simulator != null || current.link == Link.SPP) return P12JobEncoder(P12JobEncoder.Options(density = density))
        val chunk = current.bleChunkBytes.toIntOrNull()?.takeIf { it > 0 } ?: error("BLE chunk size must be a positive number")
        val pause = current.blePauseMillis.toLongOrNull() ?: error("BLE pause must be a number")
        log.add("BLE raster chunks of $chunk bytes with $pause ms pauses")
        return P12JobEncoder(P12JobEncoder.Options(density = density, rasterChunkBytes = chunk, chunkPauseMillis = pause))
    }

    private fun runAction(
        label: String,
        block: suspend () -> Unit,
    ) {
        if (action?.isActive == true) return
        mutableState.update { it.copy(busy = label) }
        action =
            viewModelScope.launch {
                try {
                    block()
                } catch (e: CancellationException) {
                    log.add("$label cancelled")
                    throw e
                } catch (e: Exception) {
                    log.add("$label failed: ${e.javaClass.simpleName}: ${e.message}")
                } finally {
                    mutableState.update { it.copy(busy = null) }
                }
            }
    }

    private suspend fun closeTransport() {
        val current = transport ?: return
        transport = null
        simulator = null
        mutableState.update { it.copy(connectedTo = null) }
        runCatching { current.close() }.onFailure { log.add("Close failed: ${it.message}") }
    }

    override fun onCleared() {
        runBlocking { transport?.close() }
    }

    private fun elapsedMillis(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

    companion object {
        const val LOG_FILE_NAME = "bringup-log.txt"
        private const val SIMULATOR_KEY = "simulator"
        private val SIMULATOR_ROW = DeviceRow(SIMULATOR_KEY, "Simulated P12", "No hardware; checks the byte stream")
        private const val SCAN_MILLIS = 12_000L
        private val LADDER_DOTS = listOf(24, 48, 72, 88)

        /** Devices named like a P12 first, then by name, unnamed last. */
        private val printersFirst =
            compareByDescending<DeviceDiscovery.Found> { it.name?.startsWith("P12") == true }.thenBy { it.name ?: "\uFFFF" }
    }
}
