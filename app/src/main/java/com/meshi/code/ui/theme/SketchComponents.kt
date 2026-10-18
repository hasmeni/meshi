package com.meshi.code.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Adds a hand-drawn double-stroke border, inspired by sketchbook-ui's SketchBorder component.
 *
 * Two slightly offset rounded-rect strokes at different opacities mimic the look
 * of a pen drawn twice around a shape — the signature sketchbook-ui border effect.
 */
fun Modifier.sketchBorder(
    color: Color,
    strokeWidth: Dp = 1.5.dp,
    cornerRadius: Dp = 10.dp,
): Modifier = drawWithContent {
    drawContent()
    val sw   = strokeWidth.toPx()
    val cr   = cornerRadius.toPx()
    val half = sw / 2f

    // Primary stroke — main border
    drawRoundRect(
        color      = color,
        topLeft    = Offset(half, half + 1f),
        size       = Size(size.width - half * 2f, size.height - half * 2f - 1f),
        cornerRadius = CornerRadius(cr),
        style      = Stroke(width = sw, join = StrokeJoin.Round, cap = StrokeCap.Round),
    )
    // Secondary stroke — slightly offset, lower opacity (hand-drawn double-line effect)
    drawRoundRect(
        color      = color.copy(alpha = color.alpha * 0.30f),
        topLeft    = Offset(half + 1.5f, half + 2.5f),
        size       = Size(size.width - half * 2f - 3f, size.height - half * 2f - 4f),
        cornerRadius = CornerRadius(cr + 1.5f),
        style      = Stroke(width = sw * 0.65f, join = StrokeJoin.Round, cap = StrokeCap.Round),
    )
}

/**
 * A card component styled to match the sketchbook-ui aesthetic:
 * - Slight rotation (like a note placed by hand)
 * - Warm paper background
 * - Hand-drawn double-stroke border
 * - Soft drop shadow mimicking ink weight
 */
@Composable
fun SketchCard(
    modifier: Modifier = Modifier,
    rotation: Float = -0.5f,
    containerColor: Color? = null,
    borderColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val bg     = containerColor ?: MaterialTheme.colorScheme.surface
    val border = borderColor    ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val shape  = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .graphicsLayer { rotationZ = rotation }
            .shadow(elevation = 3.dp, shape = shape, clip = false)
            .clip(shape)
            .background(bg)
            .sketchBorder(color = border)
            .padding(12.dp),
        content = content,
    )
}

/**
 * A section header with a hand-drawn underline — two offset strokes at different
 * opacities, matching the sketchbook-ui divider aesthetic.
 */
@Composable
fun SketchSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                val y  = size.height - 1.dp.toPx()
                val sw = 1.5.dp.toPx()
                // Primary underline
                drawLine(
                    color       = lineColor,
                    start       = Offset(0f, y),
                    end         = Offset(size.width * 0.88f, y),
                    strokeWidth = sw,
                    cap         = StrokeCap.Round,
                )
                // Secondary underline — offset for sketch double-line
                drawLine(
                    color       = lineColor.copy(alpha = 0.30f),
                    start       = Offset(3f, y + 2.dp.toPx()),
                    end         = Offset(size.width * 0.82f, y + 2.dp.toPx()),
                    strokeWidth = sw * 0.65f,
                    cap         = StrokeCap.Round,
                )
            }
            .padding(bottom = 6.dp),
    ) {
        Text(
            text  = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = FontFamily.Cursive,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
    }
}
