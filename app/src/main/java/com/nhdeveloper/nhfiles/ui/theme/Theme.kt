package com.nhdeveloper.nhfiles.ui.theme

import android.app.Activity
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// =============================================================================================
// Liquid Glass design system
//
// Compose has no CSS-style backdrop-filter, so the glass look is built from layers:
//   1. an ambient "aurora" backdrop (blue / violet / cyan light blobs) behind the whole app,
//   2. translucent, gradient-tinted surfaces that let that light show through,
//   3. a specular sheen on the upper part of every surface,
//   4. a thin gradient edge (bright top-left, dim bottom-right) that reads as a glass rim.
// Overlays that sit on top of content (dialogs, menus, sheets) are kept nearly opaque so text
// stays readable without a real blur.
// =============================================================================================

@Immutable
class GlassPalette(
    val isDark: Boolean,
    // backdrop
    val skyTop: Color,
    val skyMid: Color,
    val skyBottom: Color,
    val blobBlue: Color,
    val blobViolet: Color,
    val blobCyan: Color,
    // surface fills (top -> bottom gradient)
    val glassSubtleTop: Color,
    val glassSubtleBottom: Color,
    val glassTop: Color,
    val glassBottom: Color,
    val glassStrongTop: Color,
    val glassStrongBottom: Color,
    val barTop: Color,
    val barBottom: Color,
    // rim + highlights
    val edgeLight: Color,
    val edgeMid: Color,
    val edgeDim: Color,
    val barEdge: Color,
    val sheen: Color,
    val shadow: Color,
    // accents
    val accentBlue: Color,
    val accentViolet: Color,
    val accentCyan: Color,
    // generic faint tint for in-flow strips (breadcrumbs, gutters)
    val tint: Color,
)

val DarkGlass = GlassPalette(
    isDark = true,
    skyTop = Color(0xFF0C1535),
    skyMid = Color(0xFF0A0F26),
    skyBottom = Color(0xFF060812),
    blobBlue = Color(0xFF3D6BFF).copy(alpha = 0.46f),
    blobViolet = Color(0xFF8B5CF6).copy(alpha = 0.40f),
    blobCyan = Color(0xFF22D3EE).copy(alpha = 0.26f),
    glassSubtleTop = Color.White.copy(alpha = 0.080f),
    glassSubtleBottom = Color.White.copy(alpha = 0.030f),
    glassTop = Color.White.copy(alpha = 0.140f),
    glassBottom = Color.White.copy(alpha = 0.055f),
    glassStrongTop = Color(0xFF1E2852).copy(alpha = 0.90f),
    glassStrongBottom = Color(0xFF131B3C).copy(alpha = 0.94f),
    barTop = Color(0xFF16204A).copy(alpha = 0.62f),
    barBottom = Color(0xFF0E1433).copy(alpha = 0.52f),
    edgeLight = Color.White.copy(alpha = 0.42f),
    edgeMid = Color.White.copy(alpha = 0.10f),
    edgeDim = Color.White.copy(alpha = 0.05f),
    barEdge = Color.White.copy(alpha = 0.16f),
    sheen = Color.White.copy(alpha = 0.12f),
    shadow = Color.Black.copy(alpha = 0.60f),
    accentBlue = Color(0xFF6EA2FF),
    accentViolet = Color(0xFFA78BFA),
    accentCyan = Color(0xFF5CE1F5),
    tint = Color.White.copy(alpha = 0.05f),
)

val LightGlass = GlassPalette(
    isDark = false,
    skyTop = Color(0xFFE9EFFF),
    skyMid = Color(0xFFF2F0FF),
    skyBottom = Color(0xFFE4F3FA),
    blobBlue = Color(0xFF7BA3FF).copy(alpha = 0.55f),
    blobViolet = Color(0xFFB89CFF).copy(alpha = 0.50f),
    blobCyan = Color(0xFF6FE3F3).copy(alpha = 0.45f),
    glassSubtleTop = Color.White.copy(alpha = 0.55f),
    glassSubtleBottom = Color.White.copy(alpha = 0.28f),
    glassTop = Color.White.copy(alpha = 0.72f),
    glassBottom = Color.White.copy(alpha = 0.40f),
    glassStrongTop = Color.White.copy(alpha = 0.94f),
    glassStrongBottom = Color(0xFFF1F4FF).copy(alpha = 0.96f),
    barTop = Color.White.copy(alpha = 0.66f),
    barBottom = Color.White.copy(alpha = 0.52f),
    edgeLight = Color.White.copy(alpha = 0.95f),
    edgeMid = Color(0xFF9FB1FF).copy(alpha = 0.28f),
    edgeDim = Color(0xFF5B6CFF).copy(alpha = 0.14f),
    barEdge = Color(0xFF5B6CFF).copy(alpha = 0.16f),
    sheen = Color.White.copy(alpha = 0.65f),
    shadow = Color(0xFF2A3A7A).copy(alpha = 0.32f),
    accentBlue = Color(0xFF3B63E0),
    accentViolet = Color(0xFF7048E8),
    accentCyan = Color(0xFF0A8CA3),
    tint = Color(0xFF3A4A8A).copy(alpha = 0.06f),
)

val LocalGlass = staticCompositionLocalOf { DarkGlass }

/** True when the system animation scale is 0 ("remove animations" / reduced motion). */
val LocalReducedMotion = staticCompositionLocalOf { false }

enum class GlassLevel { Subtle, Regular, Strong }

// Note on `background`: it is intentionally fully transparent (alpha = 0) so the aurora backdrop
// drawn by NHFilesTheme shows through every Scaffold. The RGB channels are kept dark/light on
// purpose because EditorScreen derives its syntax-highlighting mode from them.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF0A1B4D),
    primaryContainer = Color(0xFF2E4BA8),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFB197FC),
    onSecondary = Color(0xFF1E0F4D),
    secondaryContainer = Color(0xFF3E2F7A),
    onSecondaryContainer = Color(0xFFE6DDFF),
    tertiary = Color(0xFF67E8F9),
    onTertiary = Color(0xFF00363D),
    tertiaryContainer = Color(0xFF0E4F5C),
    onTertiaryContainer = Color(0xFFC8F6FF),
    background = Color(0xFF080C1E).copy(alpha = 0f),
    onBackground = Color(0xFFE8ECFF),
    surface = Color(0xFF101833).copy(alpha = 0.60f),
    onSurface = Color(0xFFEAEFFF),
    surfaceVariant = Color(0xFF222B52),
    onSurfaceVariant = Color(0xFFA9B3D6),
    outline = Color(0xFFAEBBEA).copy(alpha = 0.40f),
    outlineVariant = Color.White.copy(alpha = 0.16f),
    error = Color(0xFFFF7A8A),
    onError = Color(0xFF40000D),
    errorContainer = Color(0xFF5C1A2A),
    onErrorContainer = Color(0xFFFFD9DE),
    inverseSurface = Color(0xFFE4E8FF),
    inverseOnSurface = Color(0xFF131A33),
    inversePrimary = Color(0xFF3355C7),
    surfaceContainerLowest = Color(0xFF070B1C).copy(alpha = 0.85f),
    surfaceContainerLow = Color(0xFF121A38).copy(alpha = 0.94f),
    surfaceContainer = Color(0xFF161F40).copy(alpha = 0.95f),
    surfaceContainerHigh = Color(0xFF1B2549).copy(alpha = 0.96f),
    surfaceContainerHighest = Color(0xFF222D58).copy(alpha = 0.97f),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3659D6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E0FF),
    onPrimaryContainer = Color(0xFF0A1F66),
    secondary = Color(0xFF6D4BD8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4DBFF),
    onSecondaryContainer = Color(0xFF26104F),
    tertiary = Color(0xFF007C91),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC4F1FA),
    onTertiaryContainer = Color(0xFF00363D),
    background = Color(0xFFEEF2FF).copy(alpha = 0f),
    onBackground = Color(0xFF111936),
    surface = Color.White.copy(alpha = 0.60f),
    onSurface = Color(0xFF111936),
    surfaceVariant = Color(0xFFE2E7FA),
    onSurfaceVariant = Color(0xFF4A557F),
    outline = Color(0xFF3A4A8A).copy(alpha = 0.40f),
    outlineVariant = Color(0xFF3A4A8A).copy(alpha = 0.18f),
    error = Color(0xFFC62840),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDADF),
    onErrorContainer = Color(0xFF410010),
    inverseSurface = Color(0xFF1B2347),
    inverseOnSurface = Color(0xFFEEF1FF),
    inversePrimary = Color(0xFF9DB6FF),
    surfaceContainerLowest = Color.White.copy(alpha = 0.80f),
    surfaceContainerLow = Color(0xFFF7F9FF).copy(alpha = 0.95f),
    surfaceContainer = Color(0xFFF1F4FF).copy(alpha = 0.96f),
    surfaceContainerHigh = Color(0xFFEBEFFF).copy(alpha = 0.97f),
    surfaceContainerHighest = Color(0xFFE3E8FF).copy(alpha = 0.98f),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.1).sp),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
)

@Composable
fun NHFilesTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val ctx = LocalContext.current
    val reduced = remember(ctx) {
        try {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (e: Exception) {
            false
        }
    }
    val palette = if (dark) DarkGlass else LightGlass
    CompositionLocalProvider(LocalGlass provides palette, LocalReducedMotion provides reduced) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
        ) {
            GlassBackdrop(palette, reduced, content)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Aurora backdrop
// ---------------------------------------------------------------------------------------------

@Composable
private fun rememberAuroraPhase(animate: Boolean): State<Float> {
    if (!animate) return remember { mutableStateOf(0.5f) }
    val transition = rememberInfiniteTransition(label = "aurora")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 48000, easing = LinearEasing), RepeatMode.Restart),
        label = "auroraPhase",
    )
}

@Composable
private fun GlassBackdrop(g: GlassPalette, reduced: Boolean, content: @Composable () -> Unit) {
    val phase = rememberAuroraPhase(!reduced)
    val cfg = LocalConfiguration.current
    val w = cfg.screenWidthDp.dp
    val h = cfg.screenHeightDp.dp
    val blob = maxOf(w * 1.25f, h * 0.62f)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(g.skyTop, g.skyMid, g.skyBottom))),
    ) {
        // Blobs are plain layers that only get translated, so the drift never re-draws them.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AuroraBlob(g.blobBlue, blob, w * -0.20f, h * -0.30f, 56f, 36f, 0.00f, phase)
            AuroraBlob(g.blobViolet, blob * 0.95f, w * 0.26f, h * 0.02f, 48f, 52f, 0.33f, phase)
            AuroraBlob(g.blobCyan, blob * 0.85f, w * -0.16f, h * 0.34f, 52f, 40f, 0.66f, phase)
        }
        content()
    }
}

@Composable
private fun AuroraBlob(
    color: Color,
    size: Dp,
    dx: Dp,
    dy: Dp,
    driftX: Float,
    driftY: Float,
    shift: Float,
    phase: State<Float>,
) {
    Box(
        Modifier
            .offset(dx, dy)
            .requiredSize(size)
            .graphicsLayer {
                val p = (phase.value + shift) % 1f
                val tri = if (p < 0.5f) p * 2f else (1f - p) * 2f
                translationX = (tri - 0.5f) * driftX * density
                translationY = (0.5f - tri) * driftY * density
            }
            .background(Brush.radialGradient(listOf(color, Color.Transparent))),
    )
}

// ---------------------------------------------------------------------------------------------
// Glass modifiers
// ---------------------------------------------------------------------------------------------

/**
 * Glass panel: tinted gradient fill, specular sheen over the top half and a gradient rim.
 * [shadowElevation] is only meant for near-opaque levels (floating nav, overlays); on
 * translucent glass a platform shadow would show through the fill.
 */
@Composable
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(20.dp),
    level: GlassLevel = GlassLevel.Regular,
    shadowElevation: Dp = 0.dp,
): Modifier {
    val g = LocalGlass.current
    val top = when (level) {
        GlassLevel.Subtle -> g.glassSubtleTop
        GlassLevel.Regular -> g.glassTop
        GlassLevel.Strong -> g.glassStrongTop
    }
    val bottom = when (level) {
        GlassLevel.Subtle -> g.glassSubtleBottom
        GlassLevel.Regular -> g.glassBottom
        GlassLevel.Strong -> g.glassStrongBottom
    }
    val sheenColor = if (level == GlassLevel.Strong) g.sheen.copy(alpha = g.sheen.alpha * 0.6f) else g.sheen
    var m: Modifier = this
    if (shadowElevation > 0.dp) {
        m = m.shadow(shadowElevation, shape, clip = false, ambientColor = g.shadow, spotColor = g.shadow)
    }
    return m
        .clip(shape)
        .background(Brush.verticalGradient(listOf(top, bottom)))
        .drawBehind {
            drawRect(
                Brush.verticalGradient(
                    listOf(sheenColor, Color.Transparent),
                    startY = 0f,
                    endY = size.height * 0.55f,
                ),
            )
        }
        .border(0.8.dp, Brush.linearGradient(listOf(g.edgeLight, g.edgeMid, g.edgeDim)), shape)
}

/** Full-width translucent strip for top and bottom bars with a hairline on the inner edge. */
@Composable
fun Modifier.glassBar(atTop: Boolean): Modifier {
    val g = LocalGlass.current
    return this
        .background(Brush.verticalGradient(listOf(g.barTop, g.barBottom)))
        .drawBehind {
            drawRect(
                Brush.verticalGradient(
                    listOf(g.sheen, Color.Transparent),
                    startY = 0f,
                    endY = size.height * 0.6f,
                ),
            )
            val line = 1.dp.toPx()
            val y = if (atTop) size.height - line else 0f
            drawRect(g.barEdge, topLeft = Offset(0f, y), size = Size(size.width, line))
        }
}

/** Gentle scale-down while pressed. Skipped when the system asks for reduced motion. */
@Composable
fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = 0.97f): Modifier {
    val reduced = LocalReducedMotion.current
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) pressedScale else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * A soft diagonal light band that glides across the surface every few seconds. The idle part
 * of the cycle holds a constant value, so nothing is redrawn between sweeps.
 */
@Composable
fun Modifier.glassSweep(): Modifier {
    if (LocalReducedMotion.current) return this
    val g = LocalGlass.current
    val transition = rememberInfiniteTransition(label = "sweep")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 9000
                0f at 0
                1f at 2600
                1f at 9000
            },
        ),
        label = "sweepPhase",
    )
    return this.drawWithContent {
        drawContent()
        val q = p
        if (q > 0f && q < 1f) {
            val band = size.width * 0.36f
            val x = -band + (size.width + band * 2f) * q
            drawRect(
                Brush.linearGradient(
                    colors = listOf(Color.Transparent, g.sheen.copy(alpha = g.sheen.alpha * 0.9f), Color.Transparent),
                    start = Offset(x, 0f),
                    end = Offset(x + band, size.height * 0.45f),
                ),
            )
        }
    }
}

/** Faint tint for in-flow strips that used to use a flat container colour. */
@Composable
fun glassTint(): Color = LocalGlass.current.tint

/** Lifts a saturated accent to a pastel on dark glass, and deepens it on light glass. */
@Composable
fun Color.glassTone(): Color =
    if (LocalGlass.current.isDark) lerp(this, Color.White, 0.22f) else lerp(this, Color.Black, 0.28f)
