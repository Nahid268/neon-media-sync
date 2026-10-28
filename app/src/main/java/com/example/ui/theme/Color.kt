package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Elegant Dark Theme Palette
val NeonBackground = Color(0xFF0B0F19)
val NeonBackgroundDarker = Color(0xFF070A12)
val NeonSurface = Color(0xFF111827)
val NeonSurfaceGlass = Color(0x0DFFFFFF) // bg-white/5
val NeonSurfaceGlassMedium = Color(0x14FFFFFF) // bg-white/8
val NeonSurfaceGlassLight = Color(0x801E293B) // bg-slate-800/50
val NeonSurfaceHighlight = Color(0x3300F2FE)

// Glowing Accents
val NeonCyan = Color(0xFF00F2FE)
val NeonCyanGlow = Color(0xCC00F2FE)
val NeonCyanAlpha10 = Color(0x1A00F2FE)
val NeonBlue = Color(0xFF4FACFE)
val NeonPurple = Color(0xFF7F00FF)
val NeonViolet = Color(0xFF8A2BE2)
val NeonPink = Color(0xFFFF007F)
val NeonGreen = Color(0xFF00FF88)
val NeonYellow = Color(0xFFFFE600)
val NeonError = Color(0xFFFF3366)

// Slate Hierarchy (Tailwind Slate Match)
val Slate100 = Color(0xFFF1F5F9)
val Slate300 = Color(0xFFCBD5E1)
val Slate400 = Color(0xFF94A3B8)
val Slate500 = Color(0xFF64748B)
val Slate600 = Color(0xFF475569)
val Slate700 = Color(0xFF334155)
val Slate800 = Color(0xFF1E293B)
val Slate900 = Color(0xFF0F172A)

// Text Colors
val NeonTextPrimary = Color(0xFFF8FAFC)
val NeonTextSecondary = Color(0xFF94A3B8)
val NeonTextMuted = Color(0xFF64748B)

// Border Accents
val NeonBorderGlass = Color(0x1AFFFFFF) // border-white/10
val NeonBorderGlassSubtle = Color(0x0DFFFFFF) // border-white/5
val NeonBorderCyan = Color(0x4D00F2FE)
val NeonBorderBlue = Color(0x444FACFE)
val NeonBorderPink = Color(0x66FF007F)
val NeonBorderGreen = Color(0x6600FF88)

// Neon Gradient Brushes
val NeonCyanBlueGradient = Brush.horizontalGradient(
    colors = listOf(NeonCyan, NeonBlue)
)

val NeonPurplePinkGradient = Brush.horizontalGradient(
    colors = listOf(NeonPurple, NeonPink)
)

val NeonGreenCyanGradient = Brush.horizontalGradient(
    colors = listOf(NeonGreen, NeonCyan)
)

val NeonCardGlassGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0x14FFFFFF),
        Color(0x08FFFFFF)
    )
)

val NeonDarkGlowGradient = Brush.radialGradient(
    colors = listOf(
        Color(0x2500F2FE),
        Color(0x000B0F19)
    )
)
