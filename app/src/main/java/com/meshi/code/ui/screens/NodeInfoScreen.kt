package com.meshi.code.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.meshi.code.protocol.NodeFrame
import com.meshi.code.viewmodel.ChatViewModel

@Composable
fun NodeInfoDialog(viewModel: ChatViewModel, onDismiss: () -> Unit) {
    val selfInfo by viewModel.selfInfo.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val nodeStats by viewModel.nodeStats.collectAsState()
    val radioStats by viewModel.radioStats.collectAsState()

    var showLoginDialog by remember { mutableStateOf(false) }

    if (showLoginDialog) {
        LoginDialog(
            onDismiss = { showLoginDialog = false },
            onLogin = { pw -> viewModel.sendLogin(pw); showLoginDialog = false },
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.loginResult.collect { (ok, msg) ->
            snackbarHostState.showSnackbar(if (ok) "✓ $msg" else "✗ $msg")
        }
    }

    LaunchedEffect(Unit) { viewModel.fetchNodeStats() }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Node Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row {
                        IconButton(onClick = { viewModel.fetchNodeStats(); viewModel.fetchRadioStats() },
                            modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Node identity
                if (selfInfo != null) {
                    InfoSection(icon = Icons.Default.Router, title = "Node") {
                        InfoRow("Name", selfInfo!!.nodeName.ifBlank { "—" })
                        InfoRow("ID", selfInfo!!.displayId)
                        InfoRow("Type", nodeTypeName(selfInfo!!.nodeType))
                        InfoRow("Radio", buildRadioString(selfInfo!!))
                    }
                }

                // Firmware
                if (deviceInfo != null) {
                    InfoSection(icon = Icons.Default.Memory, title = "Firmware") {
                        InfoRow("Version", deviceInfo!!.fwVersionStr.ifBlank { "v${deviceInfo!!.fwVerCode}" })
                        InfoRow("Model", deviceInfo!!.model.ifBlank { "—" })
                    }
                }

                // Battery + storage
                InfoSection(icon = Icons.Default.BatteryFull, title = "Battery & Storage") {
                    if (nodeStats != null) {
                        InfoRow("Battery", "${nodeStats!!.battPct}%  (${nodeStats!!.battMv} mV)")
                        InfoRow("Storage used", "${nodeStats!!.usedStorageBytes / 1024} KB")
                        InfoRow("Storage free", "${nodeStats!!.freeStorageBytes / 1024} KB")
                    } else {
                        Text("Fetching…", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Radio stats
                InfoSection(icon = Icons.Default.BarChart, title = "Radio Stats") {
                    if (radioStats != null) {
                        val rs = radioStats!!
                        InfoRow("Packets sent", "${rs.numSent}")
                        InfoRow("Packets recv", "${rs.numReceived}")
                        InfoRow("Direct sent", "${rs.numDirectSent}")
                        InfoRow("Channel sent", "${rs.numChannelSent}")
                        InfoRow("TX queue", "${rs.txQueueLen} / ${rs.txQueueLen + rs.txQueueFree}")
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("—", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { viewModel.fetchRadioStats() },
                                contentPadding = PaddingValues(horizontal = 4.dp)) {
                                Text("Fetch", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // Repeater login
                InfoSection(icon = Icons.Default.Lock, title = "Repeater / Room Login") {
                    Text("If this node requires authentication, enter the password below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { showLoginDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Login…", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun LoginDialog(onDismiss: () -> Unit, onLogin: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Login to Node") },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onLogin(password) }, enabled = password.isNotBlank()) { Text("Login") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun InfoSection(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                content()
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.45f))
        Text(value, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.55f))
    }
}

private fun nodeTypeName(type: Int) = when (type) {
    0 -> "Client"; 1 -> "Router"; 2 -> "Repeater"; else -> "Unknown ($type)"
}

private fun buildRadioString(info: NodeFrame.SelfInfo): String {
    val freqMhz = "%.3f MHz".format(info.radioFreqHz / 1_000_000.0)
    val bwKhz = "${info.radioBwHz / 1000} kHz"
    return "$freqMhz  SF${info.radioSf}  BW $bwKhz"
}
