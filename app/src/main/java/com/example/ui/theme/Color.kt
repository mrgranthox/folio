package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// M3 Foundation Tokens from Visual Specification
// Primary Brand (Indigo)
val M3PrimaryLight = Color(0xFF4F46E5)
val M3PrimaryDark = Color(0xFF818CF8)

val M3OnPrimaryLight = Color(0xFFFFFFFF)
val M3OnPrimaryDark = Color(0xFF111827)

val M3BackgroundLight = Color(0xFFF0F2F6) // Soft modern light gray canvas
val M3SurfaceLight = Color(0xFFF6F8FA)    // Clean tinted surface
val M3SurfaceDark = Color(0xFF101216)

val M3SurfaceContainerLight = Color(0xFFE2E5EB) // Pronounced gray for cards and KPIs
val M3SurfaceContainerDark = Color(0xFF1C1F26)

// Dedicated Dock Tab bar colors: subtle off-white/gray in light mode and off-white tinted dark in dark mode
val M3TabDockContainerLight = Color(0xFFECEFF4) // Distinct soft off-white/light-gray separate from cards & sheets
val M3TabDockContainerDark = Color(0xFF222630)  // Distinct slightly elevated off-dark-gray separate from cards & sheets

val M3SurfaceContainerHighLight = Color(0xFFD6DAE2) // Darker gray for elevated/variant containers
val M3SurfaceContainerHighDark = Color(0xFF262A34)

val M3OnSurfaceLight = Color(0xFF111827)
val M3OnSurfaceDark = Color(0xFFF9FAFB)

val M3OnSurfaceVariantLight = Color(0xFF6B7280)
val M3OnSurfaceVariantDark = Color(0xFF9CA3AF)

val M3OutlineLight = Color(0xFFE5E7EB)
val M3OutlineDark = Color(0xFF374151)

// Status & Feedback Colors
val CreditGreen = Color(0xFF10B981) // Inflows, positive variance, success snackbars, "Verified" badges
val DebitRed = Color(0xFFEF4444)    // Outflows, destructive actions, form validation errors
val WarningOrange = Color(0xFFF59E0B) // OCR confidence warnings, duplicate transaction flags, missing data
val InfoBlue = Color(0xFF3B82F6)    // Cloud sync status, OTA update available, general tooltips

// Secondary accents
val PrimaryGreen = CreditGreen
val PrimaryGreenDark = Color(0xFF059669)
val PrimaryGreenContainer = Color(0xFFD1FAE5)
val OnPrimaryGreenContainer = Color(0xFF065F46)

val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate600 = Color(0xFF475569)
val Slate200 = Color(0xFFE2E8F0)
val Slate100 = Color(0xFFF1F5F9)
val Slate50 = Color(0xFFF8FAFC)
val WarningAmber = WarningOrange
val PurpleAccent = Color(0xFF8B5CF6)
val PinkAccent = Color(0xFFEC4899)
val TealAccent = Color(0xFF14B8A6)
