package com.meshi.code.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshi.code.BuildConfig
import com.meshi.code.MeshiApplication
import com.meshi.code.backup.ContactBackupManager
import com.meshi.code.db.MeshiDatabase
import com.meshi.code.prefs.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCrashLog: () -> Unit = {},
    onLeaderboard: () -> Unit = {},
    onPanicWipe: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = (context.applicationContext as MeshiApplication).prefs

    var themeMode by remember { mutableStateOf(prefs.themeMode) }
    var notifyDms by remember { mutableStateOf(prefs.notifyDms) }
    var notifyChannel by remember { mutableStateOf(prefs.notifyChannel) }
    var batteryAlertEnabled by remember { mutableStateOf(prefs.batteryAlertEnabled) }
    var batteryAlertPct by remember { mutableStateOf(prefs.batteryAlertPct.toFloat()) }
    var showClearDeviceDialog by remember { mutableStateOf(false) }
    var showPanicDialog by remember { mutableStateOf(false) }
    var importResultMsg by remember { mutableStateOf<String?>(null) }
    var blockedContacts by remember { mutableStateOf(prefs.blockedContacts) }

    val scope = rememberCoroutineScope()
    val db = remember { MeshiDatabase.getInstance(context) }
    val dao = remember { db.contactNicknameDao() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val count = ContactBackupManager.import(context, uri, dao)
                importResultMsg = if (count >= 0) "Imported $count contact${if (count != 1) "s" else ""}."
                                  else "Import failed — invalid or unreadable file."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = 8.dp)
        ) {
            // --- Appearance ---
            SectionHeader("Appearance")

            Text(
                "Theme",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = {
                            themeMode = mode
                            prefs.themeMode = mode
                        },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.DARK   -> "Dark"
                                    ThemeMode.LIGHT  -> "Light"
                                    ThemeMode.SYSTEM -> "System"
                                }
                            )
                        },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // --- Notifications ---
            SectionHeader("Notifications")

            SwitchRow(
                label = "Direct message notifications",
                checked = notifyDms,
                onCheckedChange = { notifyDms = it; prefs.notifyDms = it },
            )
            SwitchRow(
                label = "Channel message notifications",
                checked = notifyChannel,
                onCheckedChange = { notifyChannel = it; prefs.notifyChannel = it },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // --- Battery Alert ---
            SectionHeader("Battery Alert")

            SwitchRow(
                label = "Low battery alert",
                checked = batteryAlertEnabled,
                onCheckedChange = { batteryAlertEnabled = it; prefs.batteryAlertEnabled = it },
            )

            if (batteryAlertEnabled) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        "Alert threshold: ${batteryAlertPct.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = batteryAlertPct,
                        onValueChange = { batteryAlertPct = it },
                        onValueChangeFinished = { prefs.batteryAlertPct = batteryAlertPct.toInt() },
                        valueRange = 5f..50f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("5%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("50%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // --- Device ---
            SectionHeader("Device")

            ListItem(
                headlineContent = { Text("Clear saved device") },
                supportingContent = { Text("Forget the last connected BLE device") },
                modifier = Modifier.clickable { showClearDeviceDialog = true },
            )
            ListItem(
                headlineContent = { Text("Backup contacts") },
                supportingContent = { Text("Share nicknames, tags, and verified flags as JSON") },
                modifier = Modifier.clickable {
                    scope.launch {
                        val json = ContactBackupManager.export(dao)
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_TEXT, json)
                            putExtra(Intent.EXTRA_SUBJECT, "Meshi contact backup")
                        }
                        context.startActivity(Intent.createChooser(share, "Backup contacts"))
                    }
                },
            )
            ListItem(
                headlineContent = { Text("Restore contacts") },
                supportingContent = { Text("Import contacts from a backup file") },
                modifier = Modifier.clickable { importLauncher.launch("*/*") },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // --- Blocked Contacts ---
            SectionHeader("Blocked Contacts")

            if (blockedContacts.isEmpty()) {
                ListItem(
                    headlineContent = {
                        Text("No blocked contacts", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    supportingContent = { Text("Block contacts from the Nodes tab") },
                )
            } else {
                blockedContacts.forEach { id ->
                    ListItem(
                        headlineContent = { Text(id, style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            TextButton(onClick = {
                                val updated = blockedContacts - id
                                blockedContacts = updated
                                prefs.blockedContacts = updated
                            }) {
                                Text("Unblock", color = MaterialTheme.colorScheme.primary)
                            }
                        },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // --- About ---
            SectionHeader("About")

            ListItem(
                headlineContent = { Text("Developer") },
                trailingContent = { Text("ironyLabs", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            ListItem(
                headlineContent = { Text("Version") },
                trailingContent = { Text(BuildConfig.VERSION_NAME, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            ListItem(
                headlineContent = { Text("Build") },
                trailingContent = { Text("${BuildConfig.VERSION_CODE}", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            ListItem(
                headlineContent = { Text("App ID") },
                trailingContent = { Text(BuildConfig.APPLICATION_ID, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // --- Debug ---
            SectionHeader("Debug")

            ListItem(
                headlineContent = { Text("Crash Log") },
                supportingContent = { Text("View recorded crash stack traces") },
                modifier = Modifier.clickable { onCrashLog() },
            )
            ListItem(
                headlineContent = { Text("Game History") },
                supportingContent = { Text("Win/loss records for Reversi, Chess, and Tic-tac-toe") },
                modifier = Modifier.clickable { onLeaderboard() },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // --- Danger Zone ---
            Text(
                text = "Danger Zone",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            ListItem(
                headlineContent = { Text("Panic Wipe", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Erase all messages, contacts, and settings. Cannot be undone.") },
                modifier = Modifier.clickable { showPanicDialog = true },
            )
        }
    }

    if (showClearDeviceDialog) {
        AlertDialog(
            onDismissRequest = { showClearDeviceDialog = false },
            title = { Text("Clear Saved Device?") },
            text = { Text("The app will no longer auto-connect to the last BLE device on launch.") },
            confirmButton = {
                TextButton(onClick = {
                    context.getSharedPreferences("meshi_ble", android.content.Context.MODE_PRIVATE)
                        .edit().remove("last_ble_address").apply()
                    showClearDeviceDialog = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDeviceDialog = false }) { Text("Cancel") }
            },
        )
    }

    importResultMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { importResultMsg = null },
            title = { Text("Restore contacts") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { importResultMsg = null }) { Text("OK") }
            },
        )
    }

    if (showPanicDialog) {
        AlertDialog(
            onDismissRequest = { showPanicDialog = false },
            title = { Text("Panic Wipe", color = MaterialTheme.colorScheme.error) },
            text = {
                Text(
                    "This will permanently delete ALL messages, contacts, nicknames, " +
                    "game records, and reset all settings.\n\nThis cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPanicDialog = false
                        scope.launch {
                            db.chatMessageDao().deleteAll()
                            db.contactNicknameDao().deleteAll()
                            db.snrSampleDao().deleteAll()
                            db.gameRecordDao().deleteAll()
                            // Clear all SharedPreferences
                            context.getSharedPreferences("meshi_app_prefs", android.content.Context.MODE_PRIVATE)
                                .edit().clear().apply()
                            context.getSharedPreferences("meshi_ble", android.content.Context.MODE_PRIVATE)
                                .edit().clear().apply()
                            onPanicWipe()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Wipe Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showPanicDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
