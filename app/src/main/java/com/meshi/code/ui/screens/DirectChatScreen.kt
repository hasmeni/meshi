package com.meshi.code.ui.screens

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshi.code.protocol.MeshContact
import com.meshi.code.protocol.toHexString
import com.meshi.code.repository.ChatMessage
import com.meshi.code.repository.DeliveryState
import com.meshi.code.ui.util.formatMessageTime
import com.meshi.code.viewmodel.ChatViewModel

private const val DM_MAX_CHARS = 160

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectChatScreen(
    contact: MeshContact,
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val allDirectMessages by viewModel.directMessages.collectAsState()
    val selfInfo by viewModel.selfInfo.collectAsState()
    val nicknames by viewModel.nicknames.collectAsState()
    val typingContacts by viewModel.typingContacts.collectAsState()
    val context = LocalContext.current

    val messages = remember(allDirectMessages, contact) {
        allDirectMessages.filter { msg ->
            msg.senderDisplayId == contact.displayId ||
            (msg.isSentByMe && msg.senderDisplayId == selfInfo?.displayId)
        }
    }

    val isTyping = contact.displayId in typingContacts
    val contactLabel = nicknames[contact.displayId] ?: contact.name.ifBlank { "Unknown" }

    val snrSamples by viewModel.snrSamplesForContact(contact.displayId)
        .collectAsState(initial = emptyList())
    val verifiedContacts by viewModel.verifiedContacts.collectAsState()
    val isVerified = verifiedContacts.contains(contact.displayId)
    val reactions by viewModel.reactions.collectAsState()

    val prefs = remember { (context.applicationContext as com.meshi.code.MeshiApplication).prefs }
    val draftKey = "dm_${contact.displayId}"
    var inputText by remember { mutableStateOf(prefs.getDraft(draftKey)) }
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    var showSnrGraph by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.sendError.collect { msg -> snackbarHostState.showSnackbar(msg) }
    }
    LaunchedEffect(viewModel, contact) {
        viewModel.pingResults.collect { (contactId, rttMs) ->
            if (contactId == contact.displayId) {
                val label = if (rttMs >= 1000) "~${"%.1f".format(rttMs / 1000f)}s" else "~${rttMs}ms"
                snackbarHostState.showSnackbar("Ping: $label RTT")
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // Send typing indicator when user starts typing
    LaunchedEffect(inputText) {
        if (inputText.isNotEmpty()) viewModel.notifyTyping(contact)
    }

    val remaining = DM_MAX_CHARS - inputText.length
    val counterColor = when {
        remaining < 0   -> MaterialTheme.colorScheme.error
        remaining <= 20 -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(contactLabel, fontWeight = FontWeight.Bold)
                            if (isVerified) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = "Verified",
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            }
                        }
                        if (isTyping) {
                            Text(
                                "typing…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Text(
                                contact.displayId,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (snrSamples.size >= 2) {
                        IconButton(onClick = { showSnrGraph = !showSnrGraph }) {
                            Icon(
                                Icons.Default.SignalCellularAlt,
                                contentDescription = "SNR trend",
                                tint = if (showSnrGraph) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // Ping button
                    IconButton(onClick = { viewModel.pingContact(contact) }) {
                        Icon(Icons.Default.NetworkPing, contentDescription = "Ping",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Full public key info
                    var showKeyDialog by remember { mutableStateOf(false) }
                    if (showKeyDialog) {
                        ContactKeyDialog(contact = contact, onDismiss = { showKeyDialog = false })
                    }
                    IconButton(onClick = { showKeyDialog = true }) {
                        Icon(Icons.Default.Key, contentDescription = "Public key",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Export
                    IconButton(onClick = {
                        val text = viewModel.exportMessages(-1)
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                            putExtra(Intent.EXTRA_SUBJECT, "Meshi — DMs with $contactLabel")
                        }
                        context.startActivity(Intent.createChooser(share, "Export messages"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.navigationBarsPadding().imePadding()) {
                    replyTo?.let { msg ->
                        ReplyBar(replyTo = msg, onClear = { replyTo = null })
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = {
                            if (it.length <= DM_MAX_CHARS) {
                                inputText = it
                                prefs.setDraft(draftKey, it)
                            }
                        },
                            placeholder = { Text("Message $contactLabel…") },
                            modifier = Modifier.weight(1f),
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    val text = replyTo?.let { buildReplyText(it, inputText.trim()) }
                                        ?: inputText.trim()
                                    viewModel.sendDirect(contact, text)
                                    inputText = ""
                                    prefs.setDraft(draftKey, "")
                                    replyTo = null
                                }
                            },
                            enabled = inputText.isNotBlank(),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank())
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (inputText.isNotEmpty()) {
                        Text(
                            "$remaining",
                            style = MaterialTheme.typography.labelSmall,
                            color = counterColor,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(end = 56.dp, bottom = 4.dp),
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No messages yet.\nSend the first one!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                if (showSnrGraph && snrSamples.size >= 2) {
                    item(key = "snr_graph") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                Text(
                                    "Signal Strength Trend (${snrSamples.size} readings)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                                )
                                SnrSparkline(samples = snrSamples)
                            }
                        }
                    }
                }
                items(messages, key = { "${it.timestampMs}_${it.senderDisplayId}" }) { msg ->
                    DmBubble(
                        msg,
                        reactions = reactions[msg.timestampMs] ?: emptyMap(),
                        onReply = { replyTo = it },
                        onReact = { emoji -> viewModel.sendReaction(msg, emoji) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactKeyDialog(contact: MeshContact, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(contact.name.ifBlank { "Contact" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Node ID (short):", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer { Text(contact.displayId, style = MaterialTheme.typography.bodySmall) }
                Text("Full public key:", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer {
                    Text(
                        contact.pubKey.toHexString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                }
                if (contact.lat != 0.0 || contact.lon != 0.0) {
                    Text("Last GPS:", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%.5f, %.5f".format(contact.lat, contact.lon),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun SelectionContainer(content: @Composable () -> Unit) {
    androidx.compose.foundation.text.selection.SelectionContainer { content() }
}

private val DM_REACTION_EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "👎")

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DmBubble(
    message: ChatMessage,
    reactions: Map<String, String>,
    onReply: (ChatMessage) -> Unit,
    onReact: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeStr = remember(message.timestampMs) { formatMessageTime(message.timestampMs) }
    val alignment = if (message.isSentByMe) Alignment.End else Alignment.Start
    val bubbleColor = if (message.isSentByMe)
        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (message.isSentByMe)
        MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    val clipboard = LocalClipboardManager.current
    val (quotedText, bodyText) = remember(message.text) { parseQuotedMessage(message.text) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    val emojiCounts = remember(reactions) {
        reactions.values.groupingBy { it }.eachCount()
    }

    SwipeToReplyBox(onReply = { onReply(message) }) {
        Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = alignment) {
            Box {
                Box(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bubbleColor)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                if (!message.isSentByMe) showEmojiPicker = true
                                else clipboard.setText(AnnotatedString(message.text))
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column {
                        if (quotedText != null) {
                            QuotedBlock(quotedText = quotedText, textColor = textColor)
                        }
                        Text(bodyText, color = textColor, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                timeStr,
                                color = textColor.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f),
                            )
                            if (message.snrDb != 0f) {
                                Text(
                                    "${"%.1f".format(message.snrDb)}dB",
                                    color = textColor.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            if (message.pathLen > 1) {
                                Text(
                                    "${message.pathLen}⇝",
                                    color = textColor.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            if (message.isSentByMe) {
                                when (message.deliveryState) {
                                    DeliveryState.DELIVERED -> {
                                        if (message.rttMs > 0) {
                                            val rttLabel = if (message.rttMs >= 1000)
                                                "~${"%.1f".format(message.rttMs / 1000f)}s"
                                            else "~${message.rttMs}ms"
                                            Text(
                                                rttLabel,
                                                color = textColor.copy(alpha = 0.55f),
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                        Icon(
                                            Icons.Default.DoneAll, contentDescription = "Delivered",
                                            modifier = Modifier.size(12.dp), tint = textColor.copy(alpha = 0.8f),
                                        )
                                    }
                                    DeliveryState.SENT -> Icon(
                                        Icons.Default.Done, contentDescription = "Sent",
                                        modifier = Modifier.size(12.dp), tint = textColor.copy(alpha = 0.6f),
                                    )
                                    DeliveryState.NONE -> Unit
                                }
                            }
                        }
                    }
                }
                DropdownMenu(
                    expanded = showEmojiPicker,
                    onDismissRequest = { showEmojiPicker = false },
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                        DM_REACTION_EMOJIS.forEach { emoji ->
                            TextButton(
                                onClick = { onReact(emoji); showEmojiPicker = false },
                                contentPadding = PaddingValues(4.dp),
                            ) {
                                Text(emoji, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
            // Reactions row
            if (emojiCounts.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    emojiCounts.entries.forEach { (emoji, count) ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 1.dp,
                        ) {
                            Text(
                                if (count > 1) "$emoji $count" else emoji,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
