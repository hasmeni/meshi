package com.meshi.code.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.meshi.code.protocol.MeshChannel
import com.meshi.code.ui.util.generateQrBitmap
import com.meshi.code.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    viewModel: ChatViewModel,
    onOpenChannel: (channelIdx: Int) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val channels by viewModel.channels.collectAsState()

    LaunchedEffect(Unit) { viewModel.fetchAllChannels() }

    // Private channels already configured (slots 1-7 with a real name)
    val myChannels = (1..7)
        .mapNotNull { idx -> channels[idx]?.takeIf { it.name.isNotBlank() }?.let { idx to it } }

    // Next free private slot (1-7)
    val nextFreeSlot = (1..7).firstOrNull { idx ->
        channels[idx]?.name?.isNotBlank() != true
    }

    var editingSlot   by remember { mutableStateOf<Int?>(null) }
    var deletingSlot  by remember { mutableStateOf<Int?>(null) }
    var shareSecret   by remember { mutableStateOf<String?>(null) }
    var qrChannel     by remember { mutableStateOf<MeshChannel?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Channels", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.fetchAllChannels() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() +
                         contentPadding.calculateBottomPadding() + 12.dp,
                start = 12.dp,
                end = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            // ---- Public channel ----
            item { SectionLabel("Public") }
            item {
                ChannelCard(
                    idx = 0,
                    channel = channels[0],
                    onOpen = { onOpenChannel(0) },
                    onEdit = null,
                    onDelete = null,
                )
            }

            // ---- My private channels ----
            item { Spacer(Modifier.height(4.dp)) }
            item { SectionLabel("My Channels") }

            if (myChannels.isEmpty()) {
                item {
                    Text(
                        "No private channels yet. " +
                        "Tap \"Add Channel\" to create your own or join one with a shared secret.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            } else {
                items(myChannels, key = { it.first }) { (idx, channel) ->
                    ChannelCard(
                        idx = idx,
                        channel = channel,
                        onOpen = { onOpenChannel(idx) },
                        onEdit = { editingSlot = idx },
                        onDelete = { deletingSlot = idx },
                        onQrCode = { qrChannel = channel },
                    )
                }
            }

            // ---- Add Channel button (inline card) ----
            if (nextFreeSlot != null) {
                item { Spacer(Modifier.height(4.dp)) }
                item {
                    OutlinedCard(
                        onClick = { editingSlot = nextFreeSlot },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Add Channel",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "All 7 private channel slots are in use.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // Create / Join / Edit dialog
    if (editingSlot != null) {
        val idx = editingSlot!!
        val existing = channels[idx]
        ChannelEditDialog(
            existing = existing,
            onDismiss = { editingSlot = null },
            onSave = { name, secretHex ->
                val secretBytes = secretHex?.let { hexToBytes(it) }
                val usedSecret = viewModel.createOrUpdateChannel(idx, name, secretBytes)
                editingSlot = null
                if (secretHex == null) {
                    // Show share dialog only when creating (auto-generated secret)
                    shareSecret = usedSecret.joinToString("") { "%02x".format(it) }
                }
            },
        )
    }

    // Delete confirmation dialog
    if (deletingSlot != null) {
        val idx = deletingSlot!!
        val name = channels[idx]?.displayName ?: "Channel $idx"
        AlertDialog(
            onDismissRequest = { deletingSlot = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Remove Channel") },
            text = { Text("Remove \"$name\"? You can rejoin later with the channel name and secret key.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteChannel(idx)
                        deletingSlot = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { deletingSlot = null }) { Text("Cancel") }
            },
        )
    }

    // Share secret after channel creation
    if (shareSecret != null) {
        ShareSecretDialog(
            secret = shareSecret!!,
            onDismiss = { shareSecret = null },
        )
    }

    // QR code share dialog
    if (qrChannel != null) {
        QrShareDialog(
            channel = qrChannel!!,
            onDismiss = { qrChannel = null },
        )
    }
}

// ---- UI components ----

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

@Composable
private fun ChannelCard(
    idx: Int,
    channel: MeshChannel?,
    onOpen: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onQrCode: (() -> Unit)? = null,
) {
    val isPublic = idx == 0
    val label = channel?.displayName ?: if (isPublic) "Public" else "Channel $idx"

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Index badge
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "#$idx",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (isPublic) "Public broadcast channel" else "Private channel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = onOpen,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Default.Chat, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Open", style = MaterialTheme.typography.labelSmall)
                }
                if (onQrCode != null) {
                    IconButton(onClick = onQrCode, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Outlined.QrCode,
                            contentDescription = "Share QR",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (onEdit != null) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

// ---- Dialogs ----

@Composable
private fun ChannelEditDialog(
    existing: MeshChannel?,
    onDismiss: () -> Unit,
    onSave: (name: String, secretHex: String?) -> Unit,
) {
    var name      by remember { mutableStateOf(existing?.name ?: "") }
    var secretHex by remember { mutableStateOf("") }
    val isEdit = existing != null && existing.name.isNotBlank()

    val secretValid = secretHex.isBlank() ||
        (secretHex.length == 32 && secretHex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' })

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    if (isEdit) "Edit Channel" else "Add Channel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Channel name") },
                    placeholder = { Text("e.g. Hiking Team") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = secretHex,
                    onValueChange = { secretHex = it.trim() },
                    label = { Text("Secret key (hex)") },
                    placeholder = { Text("Leave blank to generate a new key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = !secretValid,
                    supportingText = {
                        when {
                            !secretValid           -> Text("Must be exactly 32 hex characters")
                            secretHex.isNotBlank() -> Text("Will join an existing channel")
                            else                   -> Text("Blank = create new channel with random key")
                        }
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank() && secretValid) {
                                onSave(name.trim(), secretHex.ifBlank { null })
                            }
                        },
                        enabled = name.isNotBlank() && secretValid,
                    ) {
                        Text(when {
                            isEdit                 -> "Update"
                            secretHex.isNotBlank() -> "Join"
                            else                   -> "Create"
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareSecretDialog(secret: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Channel Created",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Share this secret with others so they can join your channel. " +
                    "They tap \"Add Channel\", enter the same name, paste this key, then tap Join.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            secret,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(secret))
                            copied = true
                        }) {
                            Icon(
                                if (copied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = if (copied) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun QrShareDialog(channel: MeshChannel, onDismiss: () -> Unit) {
    val secretHex = channel.secret.joinToString("") { "%02x".format(it) }
    val content = "meshichan://${channel.name}/$secretHex"
    val qrBitmap = remember(content) { generateQrBitmap(content) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Share \"${channel.displayName}\"",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Scan this QR code with another Meshi device to join the channel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR code for ${channel.displayName}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}

// ---- Helpers ----

private fun hexToBytes(hex: String): ByteArray =
    ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
