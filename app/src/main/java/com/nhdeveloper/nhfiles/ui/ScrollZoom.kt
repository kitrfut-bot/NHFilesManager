package com.nhdeveloper.nhfiles.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Scroll-to-zoom effect: rows shrink and fade slightly while they enter or leave the
 * edges of the list and are full size in the middle. Runs in the graphics layer, so it
 * never triggers recomposition while scrolling.
 */
fun Modifier.scrollZoom(state: LazyListState, key: Any): Modifier = graphicsLayer {
    val li = state.layoutInfo
    val info = li.visibleItemsInfo.firstOrNull { it.key == key } ?: return@graphicsLayer
    applyZoom(this, info.offset.toFloat(), info.size.toFloat(), li.viewportStartOffset.toFloat(), li.viewportEndOffset.toFloat())
}

fun Modifier.scrollZoom(state: LazyGridState, key: Any): Modifier = graphicsLayer {
    val li = state.layoutInfo
    val info = li.visibleItemsInfo.firstOrNull { it.key == key } ?: return@graphicsLayer
    applyZoom(this, info.offset.y.toFloat(), info.size.height.toFloat(), li.viewportStartOffset.toFloat(), li.viewportEndOffset.toFloat())
}

private fun applyZoom(scope: GraphicsLayerScope, offset: Float, size: Float, start: Float, end: Float) {
    if (size <= 0f) return
    val center = offset + size / 2f
    val edge = minOf(center - start, end - center)
    val t = (edge / (size * 1.2f)).coerceIn(0f, 1f)
    val scale = 0.86f + 0.14f * t
    scope.scaleX = scale
    scope.scaleY = scale
    scope.alpha = 0.55f + 0.45f * t
}
