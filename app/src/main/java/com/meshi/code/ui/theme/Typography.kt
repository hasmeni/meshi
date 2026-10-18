package com.meshi.code.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Caveat / handwriting-style font for display and heading text.
// FontFamily.Cursive maps to the device handwriting font (Caveat on most Android devices).
// Body and label text uses the system sans-serif for chat readability.
private val Handwriting = FontFamily.Cursive
private val Body = FontFamily.Default

val SketchTypography = Typography(
    // Display — large headers
    displayLarge  = TextStyle(fontFamily = Handwriting, fontWeight = FontWeight.Bold,   fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = Handwriting, fontWeight = FontWeight.Bold,   fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall  = TextStyle(fontFamily = Handwriting, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp),

    // Headline — screen titles, section headers
    headlineLarge  = TextStyle(fontFamily = Handwriting, fontWeight = FontWeight.Bold,      fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = Handwriting, fontWeight = FontWeight.SemiBold,  fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall  = TextStyle(fontFamily = Handwriting, fontWeight = FontWeight.SemiBold,  fontSize = 24.sp, lineHeight = 32.sp),

    // Title — TopAppBar, card titles, dialog titles
    titleLarge  = TextStyle(fontFamily = Handwriting, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Handwriting, fontWeight = FontWeight.Medium,   fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall  = TextStyle(fontFamily = Handwriting, fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.10.sp),

    // Body — chat messages, contact names, descriptions (kept readable)
    bodyLarge  = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall  = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),

    // Label — chips, badges, timestamps (kept readable)
    labelLarge  = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall  = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)
