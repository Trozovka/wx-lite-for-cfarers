package com.trozovka.wxlite.map

/**
 * A minimal 2D affine transform (uniform scale + translate) mapping world
 * coordinates to screen pixels: screen = world * scale + translate.
 *
 * Deliberately not android.graphics.Matrix — that class can't be exercised
 * in a plain JVM unit test (same category of problem as org.json elsewhere
 * in this codebase, confirmed empirically), and the actual pan/zoom math
 * needed here is simple enough not to need it. Keeping it as pure data
 * lets the pinch-zoom-around-focal-point and pan math be unit tested
 * without a device or emulator.
 */
data class MapTransform(
    val scale: Double,
    val translateX: Double,
    val translateY: Double,
) {
    fun worldToScreen(worldX: Double, worldY: Double): Pair<Double, Double> =
        Pair(worldX * scale + translateX, worldY * scale + translateY)

    fun screenToWorld(screenX: Double, screenY: Double): Pair<Double, Double> =
        Pair((screenX - translateX) / scale, (screenY - translateY) / scale)

    /** Shifts the view by ([dx], [dy]) screen pixels — a drag/scroll pan. */
    fun panned(dx: Double, dy: Double): MapTransform = copy(translateX = translateX + dx, translateY = translateY + dy)

    /** Returns a transform (same scale) that puts the given world point
     * exactly at the center of a [viewWidth] x [viewHeight] screen — the
     * math behind "recenter the camera on this location." Pulled out of
     * the (untestable) View layer specifically so this can be locked down
     * with a real test, independent of whether the caller remembers to
     * invoke it at the right time. */
    fun centeredOn(worldX: Double, worldY: Double, viewWidth: Double, viewHeight: Double): MapTransform =
        copy(
            translateX = viewWidth / 2.0 - worldX * scale,
            translateY = viewHeight / 2.0 - worldY * scale,
        )

    /**
     * Scales by [factor] around the screen point ([focusX], [focusY]) —
     * standard pinch-zoom behavior: whatever world point is currently under
     * the fingers stays under the fingers, clamped to [minScale]..[maxScale].
     */
    fun zoomed(factor: Double, focusX: Double, focusY: Double, minScale: Double, maxScale: Double): MapTransform {
        val newScale = (scale * factor).coerceIn(minScale, maxScale)
        val actualFactor = newScale / scale
        val newTranslateX = focusX - (focusX - translateX) * actualFactor
        val newTranslateY = focusY - (focusY - translateY) * actualFactor
        return copy(scale = newScale, translateX = newTranslateX, translateY = newTranslateY)
    }
}
