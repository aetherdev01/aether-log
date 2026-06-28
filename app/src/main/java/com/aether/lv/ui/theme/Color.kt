package com.aether.lv.ui.theme

import androidx.compose.ui.graphics.Color

// ══════════════════════════════════════════════════════════════════════════════
//  Material 3 – Warm Amber Tonal Palette
//  Source color : #7B5800  (hue ~37, chroma ~48)
//  Generated following M3 HCT tonal system:
//    Primary   ← Accent1 palette  (same hue, high chroma)
//    Secondary ← Accent2 palette  (same hue, lower chroma)
//    Tertiary  ← Accent3 palette  (hue +60°, moderate chroma)
//    Neutral   ← neutral palette  (same hue, very low chroma) → surface/bg
//    NeutralVar← neutral variant  (same hue, slightly higher chroma) → surfaceVariant
//
//  Tone mapping per M3 spec:
//    Light  → primary=T40  container=T90  onContainer=T10  surface=T99  surfVar=T90
//    Dark   → primary=T80  container=T30  onContainer=T90  surface=T10  surfVar=T30
// ══════════════════════════════════════════════════════════════════════════════

// ── Light scheme ─────────────────────────────────────────────────────────────

val md_theme_light_primary              = Color(0xFF7B5800)   // Primary   T40
val md_theme_light_onPrimary            = Color(0xFFFFFFFF)   // onPrimary T100
val md_theme_light_primaryContainer     = Color(0xFFFFDEA8)   // PrimCont  T90
val md_theme_light_onPrimaryContainer   = Color(0xFF271900)   // onPrimCon T10

val md_theme_light_secondary            = Color(0xFF6E5B3F)   // Secondary T40
val md_theme_light_onSecondary          = Color(0xFFFFFFFF)   // onSec     T100
val md_theme_light_secondaryContainer   = Color(0xFFF8DFBB)   // SecCont   T90
val md_theme_light_onSecondaryContainer = Color(0xFF261904)   // onSecCont T10

val md_theme_light_tertiary             = Color(0xFF4C6543)   // Tertiary  T40
val md_theme_light_onTertiary           = Color(0xFFFFFFFF)   // onTert    T100
val md_theme_light_tertiaryContainer    = Color(0xFFCEEBBF)   // TertCont  T90
val md_theme_light_onTertiaryContainer  = Color(0xFF092006)   // onTertCon T10

val md_theme_light_error                = Color(0xFFBA1A1A)   // Error     T40
val md_theme_light_onError              = Color(0xFFFFFFFF)
val md_theme_light_errorContainer       = Color(0xFFFFDAD6)   // ErrCont   T90
val md_theme_light_onErrorContainer     = Color(0xFF410002)   // onErrCont T10

// Surface roles — from Neutral palette (low chroma, warm undertone)
val md_theme_light_background           = Color(0xFFFFF8F2)   // Neutral   T99
val md_theme_light_onBackground         = Color(0xFF201B13)   // Neutral   T10

val md_theme_light_surface              = Color(0xFFFFF8F2)   // Neutral   T99
val md_theme_light_onSurface            = Color(0xFF201B13)   // Neutral   T10

val md_theme_light_surfaceVariant       = Color(0xFFF0E0C8)   // NeutVar   T90
val md_theme_light_onSurfaceVariant     = Color(0xFF504535)   // NeutVar   T30

// Tone-based surface hierarchy (M3 Expressive / surfaceContainerLow/High)
val md_theme_light_surfaceContainerLowest = Color(0xFFFFFFFF)  // T100
val md_theme_light_surfaceContainerLow    = Color(0xFFFBF2E7)  // T96
val md_theme_light_surfaceContainer       = Color(0xFFF5ECE1)  // T94
val md_theme_light_surfaceContainerHigh   = Color(0xFFEFE6DB)  // T92
val md_theme_light_surfaceContainerHighest= Color(0xFFE9E0D5)  // T90

val md_theme_light_outline              = Color(0xFF837464)   // NeutVar   T50
val md_theme_light_outlineVariant       = Color(0xFFD3C4AF)   // NeutVar   T80
val md_theme_light_inverseSurface       = Color(0xFF363027)   // Neutral   T20
val md_theme_light_inverseOnSurface     = Color(0xFFFBEFE2)   // Neutral   T95
val md_theme_light_inversePrimary       = Color(0xFFF8BB4A)   // Primary   T80
val md_theme_light_scrim               = Color(0xFF000000)

// ── Dark scheme ──────────────────────────────────────────────────────────────

val md_theme_dark_primary               = Color(0xFFF8BB4A)   // Primary   T80
val md_theme_dark_onPrimary             = Color(0xFF402D00)   // Primary   T20
val md_theme_dark_primaryContainer      = Color(0xFF5C4200)   // Primary   T30
val md_theme_dark_onPrimaryContainer    = Color(0xFFFFDEA8)   // Primary   T90

val md_theme_dark_secondary             = Color(0xFFDBC49F)   // Secondary T80
val md_theme_dark_onSecondary           = Color(0xFF3C2E16)   // Secondary T20
val md_theme_dark_secondaryContainer    = Color(0xFF55432A)   // Secondary T30
val md_theme_dark_onSecondaryContainer  = Color(0xFFF8DFBB)   // Secondary T90

val md_theme_dark_tertiary              = Color(0xFFB3CFA6)   // Tertiary  T80
val md_theme_dark_onTertiary            = Color(0xFF1F3619)   // Tertiary  T20
val md_theme_dark_tertiaryContainer     = Color(0xFF354D2D)   // Tertiary  T30
val md_theme_dark_onTertiaryContainer   = Color(0xFFCEEBBF)   // Tertiary  T90

val md_theme_dark_error                 = Color(0xFFFFB4AB)   // Error     T80
val md_theme_dark_onError               = Color(0xFF690005)   // Error     T20
val md_theme_dark_errorContainer        = Color(0xFF93000A)   // Error     T30
val md_theme_dark_onErrorContainer      = Color(0xFFFFDAD6)   // Error     T90

// Surface roles — Neutral T10/T6/T4
val md_theme_dark_background            = Color(0xFF18130C)   // Neutral   T6
val md_theme_dark_onBackground          = Color(0xFFEDE1D4)   // Neutral   T90

val md_theme_dark_surface               = Color(0xFF18130C)   // Neutral   T6
val md_theme_dark_onSurface             = Color(0xFFEDE1D4)   // Neutral   T90

val md_theme_dark_surfaceVariant        = Color(0xFF504535)   // NeutVar   T30
val md_theme_dark_onSurfaceVariant      = Color(0xFFD3C4AF)   // NeutVar   T80

// Tone-based surface hierarchy
val md_theme_dark_surfaceContainerLowest  = Color(0xFF120E08)  // T4
val md_theme_dark_surfaceContainerLow     = Color(0xFF201B13)  // T10
val md_theme_dark_surfaceContainer        = Color(0xFF251F17)  // T12
val md_theme_dark_surfaceContainerHigh    = Color(0xFF2F2921)  // T17
val md_theme_dark_surfaceContainerHighest = Color(0xFF3A342B)  // T22

val md_theme_dark_outline               = Color(0xFF9E8E7A)   // NeutVar   T60
val md_theme_dark_outlineVariant        = Color(0xFF504535)   // NeutVar   T30
val md_theme_dark_inverseSurface        = Color(0xFFEDE1D4)   // Neutral   T90
val md_theme_dark_inverseOnSurface      = Color(0xFF363027)   // Neutral   T20
val md_theme_dark_inversePrimary        = Color(0xFF7B5800)   // Primary   T40
val md_theme_dark_scrim                = Color(0xFF000000)

// ── Log level tag colors ──────────────────────────────────────────────────────
val LogColorVerbose  = Color(0xFF9E9E9E)
val LogColorDebug    = Color(0xFF42A5F5)
val LogColorInfo     = Color(0xFF66BB6A)
val LogColorWarning  = Color(0xFFFFCA28)
val LogColorError    = Color(0xFFEF5350)
val LogColorFatal    = Color(0xFFAB47BC)
val LogColorDefault  = Color(0xFFB0BEC5)
