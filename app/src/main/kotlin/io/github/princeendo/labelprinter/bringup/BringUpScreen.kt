// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.bringup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BringUpScreen(
    viewModel: BringUpViewModel,
    permissions: Array<String>,
    hasPermissions: () -> Boolean,
) {
    val state by viewModel.state.collectAsState()
    val logLines by viewModel.log.lines.collectAsState()
    var granted by remember { mutableStateOf(hasPermissions()) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            granted = result.values.all { it }
            if (granted) viewModel.onPermissionsGranted()
        }
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            uri?.let(viewModel::exportLog)
        }
    LaunchedEffect(Unit) { if (granted) viewModel.onPermissionsGranted() }

    val connected = state.connectedTo != null
    val idle = state.busy == null
    val simulatorSelected = state.selectedKey == state.devices.first().key

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("P12 bring-up", style = MaterialTheme.typography.headlineSmall)
                val status = state.connectedTo?.let { "Connected: $it" } ?: "Not connected"
                Text(state.busy?.let { "$status · $it…" } ?: status)
                if (!state.bluetoothAvailable) Text("This device has no Bluetooth; only the simulator works.")
            }

            if (!granted) {
                item { Button(onClick = { permissionLauncher.launch(permissions) }) { Text("Grant Bluetooth permissions") } }
            }

            item { SectionTitle("Device") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::scan, enabled = granted && idle && !connected) { Text("Scan 12 s") }
                    OutlinedButton(onClick = viewModel::lookUpSdp, enabled = granted && idle && !simulatorSelected) { Text("SDP lookup") }
                }
            }
            items(state.devices, key = { it.key }) { device ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = device.key == state.selectedKey,
                                enabled = !connected,
                                role = Role.RadioButton,
                                onClick = { viewModel.select(device.key) },
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = device.key == state.selectedKey, onClick = null, enabled = !connected)
                    Text("${device.name}\n${device.detail}", modifier = Modifier.padding(start = 8.dp))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BringUpViewModel.Link.entries.forEach { link ->
                        FilterChip(
                            selected = state.link == link,
                            onClick = { viewModel.setLink(link) },
                            label = { Text(link.name) },
                            enabled = !connected && !simulatorSelected,
                        )
                    }
                }
            }
            if (state.link == BringUpViewModel.Link.BLE && !simulatorSelected) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField("Raster chunk bytes", state.bleChunkBytes, viewModel::setBleChunkBytes, Modifier.width(160.dp))
                        NumberField("Pause ms", state.blePauseMillis, viewModel::setBlePauseMillis, Modifier.width(120.dp))
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::connect, enabled = idle && !connected && (granted || simulatorSelected)) { Text("Connect") }
                    OutlinedButton(
                        onClick = viewModel::disconnect,
                        enabled = connected || state.busy == "Connecting",
                    ) { Text("Disconnect") }
                }
            }

            item { SectionTitle("Queries") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::sendJobQueries, enabled = connected && idle) { Text("Job queries") }
                    Button(onClick = viewModel::sendEachQuery, enabled = connected && idle) { Text("Each query") }
                }
            }

            item { SectionTitle("Print") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BringUpViewModel.Sample.entries.forEach { sample ->
                        Button(onClick = { viewModel.print(sample) }, enabled = connected && idle) { Text(sample.label) }
                    }
                }
            }
            state.preview?.let { raster ->
                item {
                    val bitmap = remember(raster) { previewBitmap(raster).asImageBitmap() }
                    Text("Last job: ${state.previewName}, ${raster.rows} rows. Feed runs left to right.")
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Raster preview",
                            modifier = Modifier.height(72.dp).aspectRatio(raster.rows / raster.widthDots.toFloat()),
                            contentScale = ContentScale.FillBounds,
                            filterQuality = FilterQuality.None,
                        )
                    }
                }
            }

            item { SectionTitle("Log") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch(BringUpViewModel.LOG_FILE_NAME) }) { Text("Export") }
                    OutlinedButton(onClick = viewModel::clearLog) { Text("Clear") }
                }
            }
            items(logLines.asReversed()) { line ->
                Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 13.sp)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
