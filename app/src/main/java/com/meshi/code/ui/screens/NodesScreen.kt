package com.meshi.code.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Anchor
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshi.code.protocol.MeshContact
import com.meshi.code.protocol.toHexString
import com.meshi.code.repository.AdvertEvent
import com.meshi.code.ui.theme.SketchCard
import com.meshi.code.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodesScreen(
    viewModel: ChatViewModel,
    onMessageContact: (MeshContact) -> Unit,
    onChallengeContact: (MeshContact) -> Unit = {},
    onChessContact: (MeshContact) -> Unit = {},
    onTttContact: (MeshContact) -> Unit = {},
    onBshContact: (MeshContact) -> Unit = {},
    onSettings: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val contacts by viewModel.contacts.collectAsState()
    val adverts by viewModel.adverts.collectAsState()
    val selfInfo by viewModel.selfInfo.collectAsState()
    val nicknames by viewModel.nicknames.collectAsState()
    val verifiedContacts by viewModel.verifiedContacts.collectAsState()
    val contactTags by viewModel.contactTags.collectAsState()
    val blockedContacts by viewModel.blockedContacts.collectAsState()
    var savedIds by remember { mutableStateOf(setOf<String>()) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.pingResults.collect { (contactId, rttMs) ->
            val name = contacts.find { it.displayId == contactId }
                ?.let { nicknames[it.displayId] ?: it.name.ifBlank { it.displayId } }
                ?: contactId
            val label = if (rttMs >= 1000) "~${"%.1f".format(rttMs / 1000f)}s" else "~${rttMs}ms"
            snackbarHostState.showSnackbar("Ping $name: $label RTT")
        }
    }

    val contactsByPrefix = remember(contacts) { contacts.associateBy { it.displayId } }
    val allTags = remember(contactTags) { contactTags.values.filter { it.isNotBlank() }.toSortedSet().toList() }
    val filteredContacts = remember(contacts, selectedTag, contactTags) {
        if (selectedTag == null) contacts
        else contacts.filter { contactTags[it.displayId] == selectedTag }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Nodes", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.refreshContacts() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh contacts")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
            modifier = Modifier.fillMaxSize(),
        ) {
            // Tag filter chips
            if (allTags.isNotEmpty()) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = selectedTag == null,
                                onClick = { selectedTag = null },
                                label = { Text("All") },
                            )
                        }
                        items(allTags) { tag ->
                            FilterChip(
                                selected = selectedTag == tag,
                                onClick = { selectedTag = if (selectedTag == tag) null else tag },
                                label = { Text(tag) },
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(icon = Icons.Default.Contacts, title = "Contacts", count = filteredContacts.size)
            }

            if (contacts.isEmpty()) {
                item { EmptyHint("No contacts yet. Nodes that advertise nearby will appear here.") }
            } else if (filteredContacts.isEmpty()) {
                item { EmptyHint("No contacts with tag \"$selectedTag\".") }
            } else {
                items(filteredContacts, key = { it.displayId }) { contact ->
                    val isSelf = selfInfo?.displayId == contact.displayId
                    ContactCard(
                        contact = contact,
                        isSelf = isSelf,
                        isSaved = savedIds.contains(contact.displayId),
                        nickname = nicknames[contact.displayId],
                        isVerified = verifiedContacts.contains(contact.displayId),
                        tag = contactTags[contact.displayId] ?: "",
                        isBlocked = blockedContacts.contains(contact.displayId),
                        onMessage = { onMessageContact(contact) },
                        onChallenge = { onChallengeContact(contact) },
                        onChess = { onChessContact(contact) },
                        onTtt = { onTttContact(contact) },
                        onBsh = { onBshContact(contact) },
                        onPing = { viewModel.pingContact(contact) },
                        onSave = { viewModel.saveContact(contact); savedIds = savedIds + contact.displayId },
                        onSetNickname = { nick -> viewModel.setNickname(contact.displayId, nick) },
                        onSetVerified = { v -> viewModel.setVerified(contact.displayId, v) },
                        onSetTag = { t -> viewModel.setTag(contact.displayId, t) },
                        onBlock = { viewModel.blockContact(contact.displayId) },
                        onUnblock = { viewModel.unblockContact(contact.displayId) },
                        onDelete = { permanent ->
                            if (permanent) viewModel.deleteContact(contact.displayId)
                            else viewModel.softDeleteContact(contact.displayId)
                        },
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
            item {
                SectionHeader(icon = Icons.Default.CellTower, title = "Recent Advertisements", count = adverts.size)
            }

            if (adverts.isEmpty()) {
                item { EmptyHint("No advertisements received yet. Tap the tower icon to broadcast yours.") }
            } else {
                val deduped = adverts
                    .groupBy { it.senderPubKeyPrefix.toHexString() }
                    .map { (_, events) -> events.last() }
                    .sortedByDescending { it.snrDb }

                items(deduped, key = { it.senderPubKeyPrefix.toHexString() }) { advert ->
                    val matchedContact = contactsByPrefix[advert.senderPubKeyPrefix.toHexString()]
                    AdvertCard(
                        advert = advert,
                        matchedContact = matchedContact,
                        isSaved = savedIds.contains(advert.senderPubKeyPrefix.toHexString()),
                        onMessage = { matchedContact?.let { onMessageContact(it) } },
                        onSave = {
                            matchedContact?.let { contact ->
                                viewModel.saveContact(contact)
                                savedIds = savedIds + contact.displayId
                            } ?: viewModel.refreshContacts()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text("$title ($count)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
}

@Composable
private fun ContactCard(
    contact: MeshContact,
    isSelf: Boolean,
    isSaved: Boolean,
    nickname: String?,
    isVerified: Boolean,
    tag: String,
    isBlocked: Boolean,
    onMessage: () -> Unit,
    onChallenge: () -> Unit,
    onChess: () -> Unit,
    onTtt: () -> Unit,
    onBsh: () -> Unit,
    onPing: () -> Unit,
    onSave: () -> Unit,
    onSetNickname: (String) -> Unit,
    onSetVerified: (Boolean) -> Unit,
    onSetTag: (String) -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
    onDelete: (permanent: Boolean) -> Unit,
) {
    var showNicknameDialog by remember { mutableStateOf(false) }
    var showKeyDialog by remember { mutableStateOf(false) }
    var showVerifyDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showNicknameDialog) {
        NicknameDialog(
            current = nickname ?: "",
            contactName = contact.name.ifBlank { contact.displayId },
            onDismiss = { showNicknameDialog = false },
            onSave = { onSetNickname(it); showNicknameDialog = false },
        )
    }

    if (showTagDialog) {
        TagDialog(
            current = tag,
            onDismiss = { showTagDialog = false },
            onSave = { onSetTag(it); showTagDialog = false },
        )
    }
    if (showKeyDialog) {
        AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            title = { Text(contact.name.ifBlank { "Contact" }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ID (short):", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(contact.displayId, style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                    Text("Full public key:", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(contact.pubKey.toHexString(), style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showKeyDialog = false }) { Text("Close") } },
        )
    }

    if (showVerifyDialog) {
        val fingerprint = contact.pubKey.take(8).joinToString(" ") { "%02X".format(it) }
        AlertDialog(
            onDismissRequest = { showVerifyDialog = false },
            title = { Text(if (isVerified) "Verified Contact" else "Verify Contact") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isVerified) {
                        Text(
                            "Confirm this fingerprint matches the contact's device in person or via a trusted channel.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("Fingerprint:", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            fingerprint,
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp),
                        )
                    }
                    if (isVerified) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text("This contact is verified", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = {
                if (!isVerified) {
                    TextButton(onClick = { onSetVerified(true); showVerifyDialog = false }) {
                        Text("Mark Verified")
                    }
                } else {
                    TextButton(onClick = { showVerifyDialog = false }) { Text("OK") }
                }
            },
            dismissButton = {
                if (isVerified) {
                    TextButton(onClick = { onSetVerified(false); showVerifyDialog = false }) {
                        Text("Remove Verification", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = { showVerifyDialog = false }) { Text("Cancel") }
                }
            },
        )
    }

    if (showDeleteDialog) {
        val name = nickname ?: contact.name.ifBlank { contact.displayId }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Remove Contact?") },
            text = {
                Text(
                    "Remove — hides $name now but they'll reappear if they broadcast again.\n\n" +
                    "Block — permanently suppresses $name even if they rebroadcast."
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                    OutlinedButton(
                        onClick = { onDelete(false); showDeleteDialog = false },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Remove") }
                    Button(
                        onClick = { onDelete(true); showDeleteDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text("Block") }
                }
            },
            dismissButton = {},
        )
    }

    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text(if (isBlocked) "Unblock Contact?" else "Block Contact?") },
            text = {
                val name = nickname ?: contact.name.ifBlank { contact.displayId }
                if (isBlocked) {
                    Text("Unblocking $name will allow their messages through again.")
                } else {
                    Text("Blocking $name will silently drop all future messages from them.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isBlocked) onUnblock() else onBlock()
                    showBlockDialog = false
                }) {
                    Text(
                        if (isBlocked) "Unblock" else "Block",
                        color = if (isBlocked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) { Text("Cancel") }
            },
        )
    }

    SketchCard(
        modifier = Modifier.fillMaxWidth(),
        rotation = if (isSelf) 0.3f else -0.4f,
        containerColor = if (isBlocked)
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        else
            MaterialTheme.colorScheme.surfaceVariant,
        borderColor = if (isBlocked) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else null,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isSelf) Icons.Default.AccountCircle else Icons.Default.Person,
                contentDescription = null,
                tint = if (isSelf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        (nickname ?: contact.name.ifBlank { "Unknown" }) + if (isSelf) " (you)" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isVerified) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = "Verified",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                if (nickname != null) {
                    Text(
                        contact.name.ifBlank { contact.displayId },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(contact.displayId, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (tag.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Text(
                            tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
                if (contact.lastAdvertTs > 0) {
                    Text("Last seen: ${formatTime(contact.lastAdvertTs)}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Key info button
                IconButton(onClick = { showKeyDialog = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Key, contentDescription = "Public key",
                        modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Verify button
                IconButton(onClick = { showVerifyDialog = true }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = if (isVerified) "Verified" else "Verify contact",
                        modifier = Modifier.size(16.dp),
                        tint = if (isVerified) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Nickname button
                IconButton(onClick = { showNicknameDialog = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Set nickname",
                        modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Tag button
                IconButton(onClick = { showTagDialog = true }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Label,
                        contentDescription = "Set tag",
                        modifier = Modifier.size(16.dp),
                        tint = if (tag.isNotBlank()) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!isSelf) {
                    // Block / unblock button
                    IconButton(onClick = { showBlockDialog = true }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (isBlocked) Icons.Default.Block else Icons.Default.Block,
                            contentDescription = if (isBlocked) "Unblock" else "Block",
                            modifier = Modifier.size(16.dp),
                            tint = if (isBlocked) MaterialTheme.colorScheme.error
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Delete contact button
                    IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete contact",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!isSelf) {
                    FilledTonalButton(onClick = onMessage,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Icon(Icons.Default.Message, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("DM", style = MaterialTheme.typography.labelSmall)
                    }
                    FilledTonalButton(onClick = onChallenge,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Icon(Icons.Outlined.Casino, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reversi", style = MaterialTheme.typography.labelSmall)
                    }
                    FilledTonalButton(onClick = onChess,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Icon(Icons.Outlined.SportsEsports, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Chess", style = MaterialTheme.typography.labelSmall)
                    }
                    FilledTonalButton(onClick = onTtt,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("TTT", style = MaterialTheme.typography.labelSmall)
                    }
                    FilledTonalButton(onClick = onBsh,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Icon(Icons.Outlined.Anchor, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("BSH", style = MaterialTheme.typography.labelSmall)
                    }
                    FilledTonalButton(onClick = onPing,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Icon(Icons.Default.NetworkPing, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ping", style = MaterialTheme.typography.labelSmall)
                    }
                    if (!isSaved) {
                        OutlinedButton(onClick = onSave,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save", style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Saved",
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NicknameDialog(
    current: String,
    contactName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nickname for $contactName") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 32) text = it },
                label = { Text("Nickname (leave blank to clear)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun TagDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    val suggestions = listOf("Family", "Work", "Friends", "Mesh", "Emergency")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 24) text = it },
                    label = { Text("Tag (leave blank to clear)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(suggestions) { s ->
                        SuggestionChip(onClick = { text = s }, label = { Text(s) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun AdvertCard(
    advert: AdvertEvent,
    matchedContact: MeshContact?,
    isSaved: Boolean,
    onMessage: () -> Unit,
    onSave: () -> Unit,
) {
    val prefix = advert.senderPubKeyPrefix.toHexString()
    SketchCard(
        modifier = Modifier.fillMaxWidth(),
        rotation = 0.3f,
        containerColor = if (advert.isNew)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else
            MaterialTheme.colorScheme.surfaceVariant,
        borderColor = if (advert.isNew) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else null,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CellTower, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        advert.name.ifBlank { matchedContact?.name ?: "Unknown" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (advert.isNew) {
                        Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
                            Text("NEW", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }
                Text(prefix, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SNR: ${"%.1f".format(advert.snrDb)} dB", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (advert.lat != 0.0 || advert.lon != 0.0) {
                        Text("%.4f, %.4f".format(advert.lat, advert.lon),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (matchedContact != null) {
                    FilledTonalButton(onClick = onMessage,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Icon(Icons.Default.Message, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("DM", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (!isSaved) {
                    OutlinedButton(onClick = onSave,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (matchedContact != null) "Save" else "Add",
                            style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Saved",
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private fun formatTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))

private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
