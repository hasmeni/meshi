package com.meshi.code.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.meshi.code.repository.AdvertEvent
import com.meshi.code.repository.ChatMessage
import com.meshi.code.ui.util.formatMessageTime
import com.meshi.code.viewmodel.ChatViewModel

private const val MAX_CHARS = 160

/**
 * Channel chat screen — works for any channel index.
 * [channelIdx] 0 = public channel, 1-7 = private channels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    channelIdx: Int = 0,
    viewModel: ChatViewModel,
    onDisconnect: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val channels by viewModel.channels.collectAsState()
    val channelName = channels[channelIdx]?.displayName
        ?: if (channelIdx == 0) "Public Channel" else "Channel #$channelIdx"

    val messages by viewModel.messagesForChannel(channelIdx)
        .collectAsState(initial = emptyList())
    val selfInfo by viewModel.selfInfo.collectAsState()
    val adverts by viewModel.adverts.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSosActive by viewModel.isSosActive.collectAsState()
    val isStealthMode by viewModel.isStealthMode.collectAsState()
    val reactions by viewModel.reactions.collectAsState()

    val context = LocalContext.current
    val prefs = remember { (context.applicationContext as com.meshi.code.MeshiApplication).prefs }
    val draftKey = "ch_$channelIdx"
    var inputText by remember { mutableStateOf(prefs.getDraft(draftKey)) }
    var replyTo by remember { mutableStateOf<com.meshi.code.repository.ChatMessage?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.sendError.collect { msg -> snackbarHostState.showSnackbar(msg) }
    }

    val displayMessages = if (showSearch && searchQuery.isNotBlank()) searchResults
                          else messages

    LaunchedEffect(displayMessages.size) {
        if (displayMessages.isNotEmpty() && !showSearch) listState.animateScrollToItem(displayMessages.size - 1)
    }

    var showNodeInfo by remember { mutableStateOf(false) }
    if (showNodeInfo) {
        NodeInfoDialog(viewModel = viewModel, onDismiss = { showNodeInfo = false })
    }

    val isLocationSharing by viewModel.isLocationSharing.collectAsState()
    var showUtilSheet by remember { mutableStateOf(false) }

    // Permission launcher for one-shot GPS advert
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val (lat, lon) = if (granted) getLastLocation(context) else null to null
        viewModel.sendAdvert(flood = true, lat = lat, lon = lon)
    }

    // Permission launcher for persistent location sharing toggle
    val locationSharingLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (lat, lon) = getLastLocation(context)
            viewModel.setLocationSharing(true, lat, lon)
        }
    }

    if (showUtilSheet) {
        ChannelUtilSheet(messages = messages, channelName = channelName, onDismiss = { showUtilSheet = false })
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (showSearch) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it; viewModel.searchMessages(it) },
                            placeholder = { Text("Search messages…") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            showSearch = false; searchQuery = ""; viewModel.clearSearch()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(channelName, fontWeight = FontWeight.Bold)
                            Text(
                                selfInfo?.nodeName?.let { "Node: $it" } ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        // Channel utilisation chart
                        IconButton(onClick = { showUtilSheet = true }) {
                            Icon(Icons.Default.BarChart, contentDescription = "Channel utilisation",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (channelIdx == 0) {
                            IconButton(onClick = { showNodeInfo = true }) {
                                Icon(Icons.Default.Info, contentDescription = "Node info",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        // Export button
                        IconButton(onClick = {
                            val text = viewModel.exportMessages(channelIdx)
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                                putExtra(Intent.EXTRA_SUBJECT, "Meshi — $channelName messages")
                            }
                            context.startActivity(Intent.createChooser(share, "Export messages"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Export messages",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // Persistent location sharing toggle
                        IconButton(onClick = {
                            if (isLocationSharing) {
                                viewModel.setLocationSharing(false)
                            } else {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    val (lat, lon) = getLastLocation(context)
                                    viewModel.setLocationSharing(true, lat, lon)
                                } else {
                                    locationSharingLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            }
                        }) {
                            Icon(
                                if (isLocationSharing) Icons.Default.LocationOn else Icons.Default.LocationOff,
                                contentDescription = if (isLocationSharing) "Location sharing on" else "Location sharing off",
                                tint = if (isLocationSharing) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                val (lat, lon) = getLastLocation(context)
                                viewModel.sendAdvert(flood = true, lat = lat, lon = lon)
                            } else {
                                locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        }) {
                            Icon(Icons.Filled.CellTower, contentDescription = "Send Advertisement",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                        // SOS beacon toggle (only on public channel tab)
                        if (channelIdx == 0) {
                            IconButton(onClick = { viewModel.toggleSos() }) {
                                Icon(
                                    Icons.Default.Sos,
                                    contentDescription = if (isSosActive) "SOS active" else "SOS off",
                                    tint = if (isSosActive) MaterialTheme.colorScheme.error
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { viewModel.toggleStealth() }) {
                                Icon(
                                    if (isStealthMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isStealthMode) "Stealth on" else "Stealth off",
                                    tint = if (isStealthMode) MaterialTheme.colorScheme.tertiary
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (onSettings != null) {
                            IconButton(onClick = onSettings) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding(),
                )
                .imePadding()
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (showSearch && searchQuery.isNotBlank() && searchResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No results for \"$searchQuery\"",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (displayMessages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No messages yet.\nSend one or tap \uD83D\uDCE1 to advertise.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = 8.dp,
                            start = 12.dp,
                            end = 12.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(displayMessages, key = { "${it.timestampMs}_${it.senderDisplayId}" }) { msg ->
                            MessageBubble(
                                message = msg,
                                reactions = reactions[msg.timestampMs] ?: emptyMap(),
                                onReply = { replyTo = it },
                                onReact = { emoji -> viewModel.sendReaction(msg, emoji) },
                            )
                        }
                    }
                }
            }
            if (!showSearch) {
                if (adverts.isNotEmpty()) AdvertStrip(adverts.takeLast(3))
                replyTo?.let { msg ->
                    ReplyBar(replyTo = msg, onClear = { replyTo = null })
                }
                MessageInput(
                    text = inputText,
                    placeholder = "Message $channelName…",
                    onTextChange = {
                        if (it.length <= MAX_CHARS) {
                            inputText = it
                            prefs.setDraft(draftKey, it)
                        }
                    },
                    onSend = {
                        if (inputText.isNotBlank()) {
                            val text = replyTo?.let { buildReplyText(it, inputText.trim()) }
                                ?: inputText.trim()
                            viewModel.sendChannelMessage(text, channelIdx)
                            inputText = ""
                            prefs.setDraft(draftKey, "")
                            replyTo = null
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AdvertStrip(recent: List<AdvertEvent>) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 1.dp) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text("Nearby", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                recent.forEach { adv ->
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                "${adv.name.ifBlank { adv.senderPubKeyPrefix.take(3).joinToString("") { "%02x".format(it) } }}" +
                                " ${"%.1f".format(adv.snrDb)}dB",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    )
                }
            }
        }
    }
}

private val REACTION_EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "👎")

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    reactions: Map<String, String>,
    onReply: (ChatMessage) -> Unit,
    onReact: (String) -> Unit,
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

    // Aggregate: emoji -> count
    val emojiCounts = remember(reactions) {
        reactions.values.groupingBy { it }.eachCount()
    }

    SwipeToReplyBox(onReply = { onReply(message) }) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
            if (!message.isSentByMe) {
                Text(
                    message.senderName.ifBlank { message.senderDisplayId },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
            }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        }
                    }
                }
                DropdownMenu(
                    expanded = showEmojiPicker,
                    onDismissRequest = { showEmojiPicker = false },
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                        REACTION_EMOJIS.forEach { emoji ->
                            TextButton(onClick = { onReact(emoji); showEmojiPicker = false },
                                contentPadding = PaddingValues(4.dp)) {
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

@Composable
private fun MessageInput(
    text: String,
    placeholder: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val remaining = MAX_CHARS - text.length
    val counterColor = when {
        remaining < 0  -> MaterialTheme.colorScheme.error
        remaining <= 20 -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }
    Surface(tonalElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text(placeholder) },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onSend, enabled = text.isNotBlank()) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (text.isNotBlank())
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (text.isNotEmpty()) {
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
}

// Composition local for passing snackbar host state into nested composables
val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState?> { null }

/** ModalBottomSheet showing hourly message counts as a bar chart over the last 24 hours. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelUtilSheet(
    messages: List<ChatMessage>,
    channelName: String,
    onDismiss: () -> Unit,
) {
    val nowHour = remember {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    val hourMs = 3_600_000L
    // 24 hourly buckets, newest = index 23
    val buckets = remember(messages) {
        IntArray(24) { i ->
            val start = nowHour - (23 - i) * hourMs
            val end = start + hourMs
            messages.count { it.timestampMs in start until end }
        }
    }
    val maxCount = buckets.max().coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "Channel Activity — $channelName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Messages per hour (last 24 h)",
                style = MaterialTheme.typography.bodySmall,
                color = labelColor,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                val barCount = 24
                val spacing = 2.dp.toPx()
                val barWidth = (size.width - spacing * (barCount - 1)) / barCount
                buckets.forEachIndexed { i, count ->
                    val barHeight = (count.toFloat() / maxCount) * size.height
                    val x = i * (barWidth + spacing)
                    val y = size.height - barHeight
                    drawRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                    )
                }
            }
            // Hour labels: show every 6 hours
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                val cal = java.util.Calendar.getInstance()
                (0 until 24).forEach { i ->
                    val hourTs = nowHour - (23 - i) * hourMs
                    cal.timeInMillis = hourTs
                    val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
                    if (i % 6 == 0) {
                        Text(
                            "%02d:00".format(h),
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor,
                            modifier = Modifier.weight(6f),
                        )
                    }
                }
            }
            Text(
                "Total: ${messages.size} messages",
                style = MaterialTheme.typography.bodySmall,
                color = labelColor,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
        }
    }
}

/** Returns the last known GPS location as (lat, lon) or (null, null) if unavailable. */
internal fun getLastLocation(context: Context): Pair<Double?, Double?> {
    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        @Suppress("MissingPermission")
        val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        loc?.latitude to loc?.longitude
    } catch (e: Exception) {
        null to null
    }
}
