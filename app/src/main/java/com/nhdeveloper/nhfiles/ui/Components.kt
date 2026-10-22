package com.nhdeveloper.nhfiles.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nhdeveloper.nhfiles.data.FileItem
import com.nhdeveloper.nhfiles.data.FileKind
import com.nhdeveloper.nhfiles.data.FileUtils
import com.nhdeveloper.nhfiles.data.OpProgress
import com.nhdeveloper.nhfiles.ui.theme.GlassLevel
import com.nhdeveloper.nhfiles.ui.theme.LocalGlass
import com.nhdeveloper.nhfiles.ui.theme.LocalReducedMotion
import com.nhdeveloper.nhfiles.ui.theme.glass
import com.nhdeveloper.nhfiles.ui.theme.glassBar
import com.nhdeveloper.nhfiles.ui.theme.glassSweep
import com.nhdeveloper.nhfiles.ui.theme.glassTone
import com.nhdeveloper.nhfiles.ui.theme.pressScale

// ---------------------------------------------------------------------------------------------
// File kinds
// ---------------------------------------------------------------------------------------------

fun kindColor(k: FileKind): Color = when (k) {
    FileKind.FOLDER -> Color(0xFF6EA8FF)
    FileKind.IMAGE -> Color(0xFFFF9A76)
    FileKind.VIDEO -> Color(0xFFFF6B8A)
    FileKind.AUDIO -> Color(0xFFB88CFF)
    FileKind.ARCHIVE -> Color(0xFFFFCC66)
    FileKind.APK -> Color(0xFF5ADB9C)
    FileKind.TEXT -> Color(0xFF5EC4FF)
    FileKind.PDF -> Color(0xFFFF6B6B)
    FileKind.DOCUMENT -> Color(0xFF8FA8FF)
    FileKind.OTHER -> Color(0xFF9AA6CC)
}

fun kindIcon(k: FileKind): ImageVector = when (k) {
    FileKind.FOLDER -> Icons.Filled.Folder
    FileKind.IMAGE -> Icons.Filled.Image
    FileKind.VIDEO -> Icons.Filled.Videocam
    FileKind.AUDIO -> Icons.Filled.MusicNote
    FileKind.ARCHIVE -> Icons.Filled.FolderZip
    FileKind.APK -> Icons.Filled.Android
    FileKind.TEXT -> Icons.Filled.Code
    FileKind.PDF -> Icons.Filled.PictureAsPdf
    FileKind.DOCUMENT -> Icons.Filled.Description
    FileKind.OTHER -> Icons.Filled.InsertDriveFile
}

// ---------------------------------------------------------------------------------------------
// Liquid Glass building blocks
// ---------------------------------------------------------------------------------------------

/** A small tinted glass tile (file icons, shortcut tiles). */
@Composable
fun GlassTile(color: Color, size: Dp, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(size * 0.30f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(color.copy(alpha = if (g.isDark) 0.38f else 0.30f), color.copy(alpha = 0.10f)),
                ),
            )
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(g.sheen, Color.Transparent), endY = this.size.height * 0.5f))
            }
            .border(0.8.dp, Brush.linearGradient(listOf(g.edgeLight, g.edgeMid, g.edgeDim)), shape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Glass panel with optional press feedback and an optional gliding light sweep. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    level: GlassLevel = GlassLevel.Regular,
    onClick: (() -> Unit)? = null,
    sweep: Boolean = false,
    content: @Composable () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    var m: Modifier = modifier
    if (onClick != null) m = m.pressScale(src)
    m = m.glass(shape, level)
    if (sweep) m = m.glassSweep()
    if (onClick != null) {
        m = m.clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
    }
    Box(m, propagateMinConstraints = true) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() }
    }
}

/** Replacement for the flat `Surface(tonalElevation = 3.dp, color = surfaceContainer)` bars. */
@Composable
fun GlassBar(modifier: Modifier = Modifier, atTop: Boolean = false, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().glassBar(atTop)) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() }
    }
}

/** Same parameters as the Material TopAppBar the screens used, drawn as a glass strip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        modifier = modifier.glassBar(atTop = true),
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
    )
}

/**
 * Row/cell background used for list items: a faint glass fill when idle, a blue-violet glow and a
 * brighter rim when selected. The selection change is animated and drawn without recomposing.
 */
@Composable
fun Modifier.glassItem(selected: Boolean, radius: Dp = 18.dp): Modifier {
    val g = LocalGlass.current
    val cs = MaterialTheme.colorScheme
    val sel by animateFloatAsState(if (selected) 1f else 0f, animationSpec = tween(200), label = "glassItemSel")
    val selTop = cs.primary.copy(alpha = if (g.isDark) 0.34f else 0.26f)
    val selBottom = cs.secondary.copy(alpha = if (g.isDark) 0.20f else 0.16f)
    val selEdgeA = cs.primary.copy(alpha = 0.90f)
    val selEdgeB = cs.secondary.copy(alpha = 0.35f)
    return this
        .clip(RoundedCornerShape(radius))
        .drawBehind {
            val s = sel
            drawRect(
                Brush.verticalGradient(
                    listOf(lerp(g.glassSubtleTop, selTop, s), lerp(g.glassSubtleBottom, selBottom, s)),
                ),
            )
            val sw = 0.8.dp.toPx()
            drawRoundRect(
                brush = Brush.linearGradient(listOf(lerp(g.edgeMid, selEdgeA, s), lerp(g.edgeDim, selEdgeB, s))),
                topLeft = Offset(sw / 2f, sw / 2f),
                size = Size(this.size.width - sw, this.size.height - sw),
                cornerRadius = CornerRadius(radius.toPx() - sw / 2f),
                style = Stroke(sw),
            )
        }
}

/** Selectable glass list row shared by file rows and ZIP entries. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassSelectableRow(
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    horizontal: Dp = 14.dp,
    vertical: Dp = 8.dp,
    minHeight: Dp = 0.dp,
    outerHorizontal: Dp = 8.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    Box(modifier.fillMaxWidth().padding(horizontal = outerHorizontal, vertical = 2.5.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .pressScale(src, 0.985f)
                .glassItem(selected, 18.dp)
                .combinedClickable(
                    interactionSource = src,
                    indication = LocalIndication.current,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = horizontal, vertical = vertical),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Fade + rise on first composition. Skipped for reduced motion. */
@Composable
fun Modifier.glassEntrance(): Modifier {
    val reduced = LocalReducedMotion.current
    var shown by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) { shown = true }
    val a by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(380, easing = FastOutSlowInEasing),
        label = "entrance",
    )
    return this.graphicsLayer {
        alpha = a
        translationY = (1f - a) * 14.dp.toPx()
    }
}

@Composable
fun glassFieldColors(): TextFieldColors {
    val g = LocalGlass.current
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = g.glassSubtleTop,
        unfocusedContainerColor = g.glassSubtleBottom,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        cursorColor = MaterialTheme.colorScheme.primary,
    )
}

/** Gradient ring used for storage usage. */
@Composable
fun GlassRing(progress: Float, modifier: Modifier = Modifier, strokeWidth: Dp = 8.dp) {
    val g = LocalGlass.current
    Canvas(modifier) {
        val sw = strokeWidth.toPx()
        val d = size.minDimension - sw
        val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
        val arc = Size(d, d)
        drawArc(
            color = g.glassTop,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arc,
            style = Stroke(sw),
        )
        val p = progress.coerceIn(0f, 1f)
        if (p > 0.004f) {
            rotate(-90f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(g.accentCyan, g.accentBlue, g.accentViolet), center = center),
                    startAngle = 0f,
                    sweepAngle = 360f * p,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arc,
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
        }
    }
}

/** Glass progress bar. Pass `null` for an indeterminate gliding segment. */
@Composable
fun GlassLinearProgress(progress: Float?, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val g = LocalGlass.current
    val reduced = LocalReducedMotion.current
    val shape = RoundedCornerShape(50)
    val shown by animateFloatAsState(progress ?: 0f, animationSpec = tween(250), label = "glassProgress")
    val sweep: State<Float> = if (progress == null && !reduced) {
        rememberInfiniteTransition(label = "indeterminate").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
            label = "indeterminatePhase",
        )
    } else {
        remember { mutableStateOf(0.3f) }
    }
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(g.glassTop)
            .border(0.6.dp, g.edgeMid, shape)
            .drawBehind {
                val colors = listOf(g.accentCyan, g.accentBlue, g.accentViolet)
                if (progress != null) {
                    val w = this.size.width * shown.coerceIn(0f, 1f)
                    if (w > 0f) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(colors, startX = 0f, endX = this.size.width.coerceAtLeast(1f)),
                            size = Size(w, this.size.height),
                            cornerRadius = CornerRadius(this.size.height / 2f),
                        )
                    }
                } else {
                    val seg = this.size.width * 0.38f
                    val x = -seg + (this.size.width + seg) * sweep.value
                    drawRoundRect(
                        brush = Brush.horizontalGradient(colors, startX = x, endX = x + seg),
                        topLeft = Offset(x, 0f),
                        size = Size(seg, this.size.height),
                        cornerRadius = CornerRadius(this.size.height / 2f),
                    )
                }
            },
    )
}

@Composable
fun GlassSnackbar(data: SnackbarData) {
    val cs = MaterialTheme.colorScheme
    val g = LocalGlass.current
    val shape = RoundedCornerShape(20.dp)
    Snackbar(
        data,
        modifier = Modifier.padding(horizontal = 12.dp).border(0.8.dp, g.edgeMid, shape),
        shape = shape,
        containerColor = cs.surfaceContainerHighest,
        contentColor = cs.onSurface,
        actionColor = cs.primary,
    )
}

// ---------------------------------------------------------------------------------------------
// File rows
// ---------------------------------------------------------------------------------------------

@Composable
fun FileIcon(kind: FileKind, size: Dp = 40.dp) {
    val c = kindColor(kind)
    GlassTile(c, size) {
        Icon(kindIcon(kind), contentDescription = null, tint = c.glassTone(), modifier = Modifier.size(size * 0.56f))
    }
}

fun displayName(item: FileItem, showExt: Boolean): String =
    if (showExt || item.isDir) item.name else FileUtils.baseName(item.name)

@Composable
fun FileRow(
    item: FileItem,
    selected: Boolean,
    selecting: Boolean,
    showExt: Boolean,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    GlassSelectableRow(
        selected = selected,
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        horizontal = if (compact) 8.dp else 14.dp,
        vertical = 8.dp,
        minHeight = 58.dp,
        outerHorizontal = if (compact) 3.dp else 8.dp,
    ) {
        FileIcon(item.kind, if (compact) 38.dp else 42.dp)
        Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                displayName(item, showExt),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle ?: if (compact) {
                    if (item.isDir) FileUtils.formatDate(item.modified)
                    else FileUtils.formatSize(item.size) + " · " + FileUtils.formatDate(item.modified)
                } else if (item.isDir) "${item.childCount} items" else FileUtils.formatSize(item.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (compact) {
            if (selecting) {
                Spacer(Modifier.width(6.dp))
                Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.size(22.dp))
            }
        } else {
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                Text(
                    FileUtils.formatDate(item.modified),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (selecting) {
                    Spacer(Modifier.height(2.dp))
                    Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
    )
}

// ---------------------------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------------------------

/**
 * Glass replacement for the Material AlertDialog. Same slots; the panel is near-opaque so text
 * stays readable over any content, with a spring-in entrance.
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(28.dp),
) {
    val cs = MaterialTheme.colorScheme
    val reduced = LocalReducedMotion.current
    Dialog(onDismissRequest = onDismissRequest) {
        val visible = remember { MutableTransitionState(reduced).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = visible,
            enter = if (reduced) {
                fadeIn(tween(1))
            } else {
                fadeIn(tween(180)) + scaleIn(initialScale = 0.9f, animationSpec = tween(260, easing = FastOutSlowInEasing))
            },
            exit = fadeOut(tween(100)),
        ) {
            Column(
                modifier
                    .widthIn(min = 280.dp, max = 560.dp)
                    .glass(shape, GlassLevel.Strong)
                    .padding(24.dp),
            ) {
                CompositionLocalProvider(LocalContentColor provides cs.onSurface) {
                    if (title != null) {
                        ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
                        Spacer(Modifier.height(14.dp))
                    }
                    if (text != null) {
                        Box(Modifier.weight(1f, fill = false)) {
                            CompositionLocalProvider(LocalContentColor provides cs.onSurfaceVariant) {
                                ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dismissButton != null) dismissButton()
                        confirmButton()
                    }
                }
            }
        }
    }
}

@Composable
fun NameDialog(
    title: String,
    label: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var v by remember { mutableStateOf(initial) }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = v,
                onValueChange = { v = it },
                singleLine = true,
                label = { Text(label) },
                shape = RoundedCornerShape(16.dp),
                colors = glassFieldColors(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(v.trim()) }, enabled = v.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun MessageDialog(title: String, text: String, onDismiss: () -> Unit) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
fun ChoiceDialog(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, label ->
                    val on = i == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable { onSelect(i) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = on, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun ProgressDialog(p: OpProgress, onCancel: () -> Unit) {
    GlassAlertDialog(
        onDismissRequest = {},
        title = { Text(p.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassLinearProgress(
                    if (p.total > 0) (p.done.toFloat() / p.total.toFloat()).coerceIn(0f, 1f) else null,
                    Modifier.fillMaxWidth(),
                )
                if (p.current.isNotEmpty()) {
                    Text(p.current, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                if (p.total > 0) {
                    Text(
                        if (p.bytes) "${FileUtils.formatSize(p.done)} / ${FileUtils.formatSize(p.total)}" else "${p.done} / ${p.total}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .glassEntrance(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val c = MaterialTheme.colorScheme.primary
        GlassTile(c, 72.dp) {
            Icon(Icons.Filled.Folder, contentDescription = null, tint = c.glassTone(), modifier = Modifier.size(38.dp))
        }
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
