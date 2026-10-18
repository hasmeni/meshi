package com.meshi.code.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.meshi.code.repository.ChatMessage
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Wraps [content] with a right-swipe gesture. Triggers [onReply] when swiped past threshold. */
@Composable
fun SwipeToReplyBox(
    onReply: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val threshold = with(density) { 64.dp.toPx() }
    var triggered by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        val progress = (offsetX.value / threshold).coerceIn(0f, 1f)
        if (progress > 0f) {
            Icon(
                Icons.Default.Reply,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .graphicsLayer {
                        alpha = progress
                        scaleX = 0.6f + progress * 0.4f
                        scaleY = 0.6f + progress * 0.4f
                    },
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(0f, threshold * 1.5f))
                        }
                    },
                    onDragStopped = {
                        val wasTriggered = offsetX.value >= threshold
                        if (wasTriggered && !triggered) {
                            triggered = true
                            onReply()
                        }
                        scope.launch {
                            offsetX.animateTo(
                                0f,
                                spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                            triggered = false
                        }
                    }
                )
        ) {
            content()
        }
    }
}

/** Strip above the input showing the message being quoted, with a cancel button. */
@Composable
fun ReplyBar(replyTo: ChatMessage, onClear: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Reply,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                val senderLabel = if (replyTo.isSentByMe) "You"
                                  else replyTo.senderName.ifBlank { replyTo.senderDisplayId }
                Text(
                    senderLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                // Show the first non-blank, non-quote line as preview
                val previewLine = replyTo.text.lines()
                    .firstOrNull { it.isNotBlank() && !it.startsWith("> ") }
                    ?: replyTo.text.take(80)
                Text(
                    previewLine.take(80),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cancel reply",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Renders the quoted block inside a bubble for messages that start with "> …\n". */
@Composable
fun QuotedBlock(quotedText: String, textColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(IntrinsicSize.Min)
                .background(
                    textColor.copy(alpha = 0.5f),
                    RoundedCornerShape(2.dp),
                )
                .height(with(LocalDensity.current) { 32.dp })
        )
        Spacer(Modifier.width(6.dp))
        Text(
            quotedText.take(100),
            color = textColor.copy(alpha = 0.65f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

/**
 * Splits a message that may start with "> quoted\n" into (quotedText, bodyText).
 * Returns (null, fullText) if the message is not a quote.
 */
fun parseQuotedMessage(text: String): Pair<String?, String> {
    if (!text.startsWith("> ")) return null to text
    val nl = text.indexOf('\n')
    if (nl == -1) return null to text
    return text.substring(2, nl) to text.substring(nl + 1)
}

/** Formats a reply message with the quoted text prefix. */
fun buildReplyText(quotedMessage: ChatMessage, replyBody: String): String {
    val quoteLine = quotedMessage.text.lines()
        .firstOrNull { it.isNotBlank() && !it.startsWith("> ") }
        ?: quotedMessage.text.take(80)
    return "> ${quoteLine.take(80)}\n$replyBody"
}

/**
 * Sparkline chart for SNR trend data.
 * [samples] are in newest-first order. The chart draws oldest→newest left→right.
 */
@Composable
fun SnrSparkline(
    samples: List<Float>,
    modifier: Modifier = Modifier,
) {
    if (samples.size < 2) return
    val ordered = samples.reversed() // oldest first
    val minSnr = ordered.min()
    val maxSnr = ordered.max()
    val range = (maxSnr - minSnr).takeIf { it > 0f } ?: 1f

    val lineColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Text("${"%.0f".format(maxSnr)}dB", style = MaterialTheme.typography.labelSmall, color = labelColor)
            Spacer(Modifier.height(16.dp))
            Text("${"%.0f".format(minSnr)}dB", style = MaterialTheme.typography.labelSmall, color = labelColor)
        }
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            val step = size.width / (ordered.size - 1)
            val points = ordered.mapIndexed { i, snr ->
                val x = i * step
                val y = size.height - ((snr - minSnr) / range) * size.height
                androidx.compose.ui.geometry.Offset(x, y)
            }
            for (i in 0 until points.size - 1) {
                drawLine(
                    color = lineColor,
                    start = points[i],
                    end = points[i + 1],
                    strokeWidth = 2.5f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
            // Draw dots at each sample
            points.forEach { pt ->
                drawCircle(color = lineColor, radius = 3f, center = pt)
            }
        }
        Text(
            "${ordered.last().let { "%.1f".format(it) }}dB",
            style = MaterialTheme.typography.labelSmall,
            color = lineColor,
        )
    }
}
