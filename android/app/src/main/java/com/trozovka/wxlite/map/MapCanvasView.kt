package com.trozovka.wxlite.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.trozovka.wxlite.chart.Beaufort
import com.trozovka.wxlite.chart.CenterType
import com.trozovka.wxlite.chart.Isobars
import com.trozovka.wxlite.chart.PressureCenters
import com.trozovka.wxlite.chart.Storm
import com.trozovka.wxlite.chart.Storms
import com.trozovka.wxlite.chart.WindBarb
import com.trozovka.wxlite.chart.WindBarbSymbol
import com.trozovka.wxlite.data.WxlFile
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-screen, pannable, pinch-zoomable map: coastlines (bundled Natural
 * Earth data) plus a wind field (barbs + Beaufort force) and cyclone
 * markers drawn as an overlay on top. Deliberately NOT a tile-streaming
 * basemap (no osmdroid/Mapbox/Maps SDK) — panning and zooming are a pure
 * local Canvas transform (MapTransform) over data already on disk. Nothing
 * is fetched by panning; only an explicit sync touches the network.
 *
 * Replaces the old fixed-size ChartCanvasView + orthographic GlobeView,
 * which real device testing showed were the wrong interaction model
 * (small fixed panel, no zoom/pan, broken in landscape).
 */
class MapCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var transform = MapTransform(scale = INITIAL_SCALE, translateX = 0.0, translateY = 0.0)
    private var pendingCenter: Pair<Double, Double>? = null

    private var coastline: List<CoastlinePolygon>? = null
    private var tiles: List<WxlFile> = emptyList()
    private var storms: List<Storm> = emptyList()

    private val backgroundPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val coastlinePaint = Paint().apply {
        color = Color.rgb(90, 90, 90); strokeWidth = 2f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val windBarbPaint = Paint().apply {
        color = Color.BLACK; strokeWidth = 3f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val beaufortPaint = Paint().apply {
        color = Color.BLACK; textSize = 22f; isAntiAlias = true; textAlign = Paint.Align.CENTER
    }
    private val isobarPaint = Paint().apply {
        color = Color.BLACK; strokeWidth = 2f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val isobarLabelPaint = Paint().apply {
        color = Color.BLACK; textSize = 20f; isAntiAlias = true; textAlign = Paint.Align.CENTER
    }
    private val centerLabelPaint = Paint().apply {
        color = Color.BLACK; textSize = 38f; isAntiAlias = true; textAlign = Paint.Align.CENTER
    }
    private val centerValuePaint = Paint().apply {
        color = Color.BLACK; textSize = 24f; isAntiAlias = true; textAlign = Paint.Align.CENTER
    }
    private val stormPaint = Paint().apply {
        color = Color.BLACK; strokeWidth = 4f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val stormLabelPaint = Paint().apply {
        color = Color.BLACK; textSize = 30f; isAntiAlias = true; textAlign = Paint.Align.CENTER
    }

    // Barbs were reported as "almost invisible" on a real device -- ~65%
    // longer than the original 36px, and the Beaufort label is now placed
    // beside the shaft (perpendicular offset) instead of directly below
    // it, so the two no longer visually merge into e.g. "F4/".
    private val windBarbLengthPx = 60f
    private val beaufortLabelOffsetPx = 26f

    // Minimum on-screen spacing between wind barbs/Beaufort labels, in
    // pixels -- the stride actually used is derived from this and the
    // current zoom (see strideFor), so labels stay readable instead of
    // piling up at low zoom or wastefully sparse at high zoom. Widened
    // to match the longer barbs above.
    private val minLabelSpacingPx = 90.0

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val panDetector = GestureDetector(context, PanListener())

    /** Renders wind + pressure for every tile passed in -- callers should
     * pass every cached tile for the selected hour, not just the tile
     * nearest the ship, so panning shows data anywhere that's actually
     * been synced (the "camera" here only decides what's visible, per the
     * world/viewport distinction; it doesn't limit what data exists). */
    fun setTiles(newTiles: List<WxlFile>) {
        tiles = newTiles
        invalidate()
    }

    fun setStorms(allStorms: List<Storm>) {
        storms = allStorms
        invalidate()
    }

    /** Recenters the map on (lat, lon) without changing the current zoom level. */
    fun centerOn(latDeg: Double, lonDeg: Double) {
        if (width == 0 || height == 0) {
            pendingCenter = Pair(latDeg, lonDeg)
            return
        }
        applyCenter(latDeg, lonDeg)
    }

    /** The lat/lon currently at the center of the screen -- used by "Sync
     * this area" to know which tile to fetch, since the viewport can be
     * panned away from the saved ship location. */
    fun currentCenterLatLon(): Pair<Double, Double> {
        val (worldX, worldY) = transform.screenToWorld(width / 2.0, height / 2.0)
        return Pair(-worldY, worldX) // inverse of worldOf: lat = -worldY, lon = worldX
    }

    private fun applyCenter(latDeg: Double, lonDeg: Double) {
        val (worldX, worldY) = worldOf(latDeg, lonDeg)
        transform = transform.copy(
            translateX = width / 2.0 - worldX * transform.scale,
            translateY = height / 2.0 - worldY * transform.scale,
        )
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pendingCenter?.let { (lat, lon) ->
            applyCenter(lat, lon)
            pendingCenter = null
        }
    }

    private fun ensureCoastlineLoaded() {
        if (coastline != null) return
        coastline = try {
            context.assets.open("coastline.bin").use { CoastlineData.parse(it.readBytes()) }
        } catch (e: Exception) {
            Log.w("MapCanvasView", "Failed to load coastline asset", e)
            emptyList()
        }
    }

    /** World space: plain equirectangular degrees, north up (screen Y grows down, so lat is negated). */
    private fun worldOf(latDeg: Double, lonDeg: Double): Pair<Double, Double> = Pair(lonDeg, -latDeg)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        ensureCoastlineLoaded()

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        drawCoastline(canvas)
        drawPressure(canvas)
        drawWind(canvas)
        drawStorms(canvas)
    }

    /** Converts a tile-local grid position (row/col, fractional) to a
     * screen point, via the same lat/lon -> world -> screen path used for
     * every other layer. Shared by isobars and pressure centers. */
    private fun tileGridToScreen(file: WxlFile, row: Double, col: Double): Pair<Float, Float> {
        val latSpan = file.latMax - file.latMin
        val lonSpan = file.lonMax - file.lonMin
        val lat = file.latMin + latSpan * row / (file.nLat - 1)
        val lon = file.lonMin + lonSpan * col / (file.nLon - 1)
        val (worldX, worldY) = worldOf(lat.toDouble(), lon.toDouble())
        val (sx, sy) = transform.worldToScreen(worldX, worldY)
        return Pair(sx.toFloat(), sy.toFloat())
    }

    private fun drawPressure(canvas: Canvas) {
        for (file in tiles) {
            drawIsobars(canvas, file)
            drawPressureCenters(canvas, file)
        }
    }

    private fun drawIsobars(canvas: Canvas, file: WxlFile) {
        if (file.nLat < 2 || file.nLon < 2) return
        val segments = Isobars.generate(file.pressureGrid(), intervalHpa = 4.0)

        for (seg in segments) {
            val (x1, y1) = tileGridToScreen(file, seg.p1.row, seg.p1.col)
            val (x2, y2) = tileGridToScreen(file, seg.p2.row, seg.p2.col)
            canvas.drawLine(x1, y1, x2, y2, isobarPaint)
        }

        // Label every second contour (every 8 hPa, not every 4) at one
        // representative segment's midpoint, rather than repeating the
        // value along the whole line -- keeps the value readable without
        // the clutter of stamping it on every segment.
        val byLevel = segments.groupBy { it.levelHpa }
        for ((level, segsForLevel) in byLevel) {
            if (Math.round(level / 4.0) % 2 != 0L) continue
            val anchor = segsForLevel[segsForLevel.size / 2]
            val midRow = (anchor.p1.row + anchor.p2.row) / 2.0
            val midCol = (anchor.p1.col + anchor.p2.col) / 2.0
            val (lx, ly) = tileGridToScreen(file, midRow, midCol)
            canvas.drawText(level.toInt().toString(), lx, ly, isobarLabelPaint)
        }
    }

    private fun drawPressureCenters(canvas: Canvas, file: WxlFile) {
        if (file.nLat < 2 || file.nLon < 2) return
        val centers = PressureCenters.find(file.pressureGrid())
        for (center in centers) {
            val (x, y) = tileGridToScreen(file, center.row.toDouble(), center.col.toDouble())
            val label = if (center.type == CenterType.HIGH) "H" else "L"
            canvas.drawText(label, x, y, centerLabelPaint)
            canvas.drawText(
                center.valueHpa.toInt().toString(),
                x,
                y + centerLabelPaint.textSize * 0.8f,
                centerValuePaint,
            )
        }
    }

    private fun drawCoastline(canvas: Canvas) {
        for (polygon in coastline.orEmpty()) {
            val points = polygon.points
            if (points.size < 2) continue
            for (i in 0 until points.size - 1) {
                val (ax, ay) = worldOf(points[i].lat, points[i].lon)
                val (bx, by) = worldOf(points[i + 1].lat, points[i + 1].lon)
                val (sx1, sy1) = transform.worldToScreen(ax, ay)
                val (sx2, sy2) = transform.worldToScreen(bx, by)
                canvas.drawLine(sx1.toFloat(), sy1.toFloat(), sx2.toFloat(), sy2.toFloat(), coastlinePaint)
            }
        }
    }

    private fun drawWind(canvas: Canvas) {
        for (file in tiles) {
            drawWindTile(canvas, file)
        }
    }

    private fun drawWindTile(canvas: Canvas, file: WxlFile) {
        if (file.nLat < 2 || file.nLon < 2) return
        val stride = strideFor(file)

        var row = 0
        while (row < file.nLat) {
            var col = 0
            while (col < file.nLon) {
                val point = file.pointAt(row, col)
                val (sx, sy) = tileGridToScreen(file, row.toDouble(), col.toDouble())

                val barb = WindBarb.fromComponents(point.windU.toDouble(), point.windV.toDouble())
                val force = Beaufort.forceForKnots(barb.speedKnots.toDouble())
                drawSingleBarb(canvas, sx, sy, barb, "F$force")

                col += stride
            }
            row += stride
        }
    }

    /** How many grid points to skip between drawn barbs, derived from the
     * grid's actual degree spacing and the current zoom, so barbs stay
     * roughly [minLabelSpacingPx] apart on screen instead of overlapping
     * at low zoom (previously a fixed stride regardless of zoom level). */
    private fun strideFor(file: WxlFile): Int {
        val gridSpacingDeg = (file.latMax - file.latMin) / (file.nLat - 1)
        if (gridSpacingDeg <= 0f || transform.scale <= 0.0) return 1
        val rawStride = minLabelSpacingPx / (transform.scale * gridSpacingDeg)
        return rawStride.toInt().coerceAtLeast(1)
    }

    private fun drawSingleBarb(canvas: Canvas, cx: Float, cy: Float, barb: WindBarbSymbol, forceLabel: String) {
        if (barb.isCalm) {
            canvas.drawCircle(cx, cy, 5f, windBarbPaint)
            canvas.drawText(forceLabel, cx, cy + beaufortPaint.textSize + 14f, beaufortPaint)
            return
        }

        val bearingRad = Math.toRadians(barb.fromDirectionDeg)
        val dx = sin(bearingRad).toFloat()
        val dy = -cos(bearingRad).toFloat()

        val tipX = cx + dx * windBarbLengthPx
        val tipY = cy + dy * windBarbLengthPx
        canvas.drawLine(cx, cy, tipX, tipY, windBarbPaint)

        val perpDx = -dy
        val perpDy = dx

        // Beaufort label sits beside the shaft (perpendicular offset), not
        // below the center point -- on a real device the two previously
        // merged into unreadable text like "F4/" for barbs pointing
        // downward, since the label sat directly on the shaft's own line.
        canvas.drawText(
            forceLabel,
            cx + perpDx * beaufortLabelOffsetPx,
            cy + perpDy * beaufortLabelOffsetPx,
            beaufortPaint,
        )

        var offset = 0f
        val step = 7f

        repeat(barb.pennants) {
            val baseX = tipX - dx * offset
            val baseY = tipY - dy * offset
            val farX = baseX - dx * step + perpDx * step
            val farY = baseY - dy * step + perpDy * step
            val path = Path()
            path.moveTo(baseX, baseY)
            path.lineTo(farX, farY)
            path.lineTo(baseX - dx * step, baseY - dy * step)
            path.close()
            canvas.drawPath(path, windBarbPaint)
            offset += step
        }
        repeat(barb.fullBarbs) {
            val baseX = tipX - dx * offset
            val baseY = tipY - dy * offset
            canvas.drawLine(baseX, baseY, baseX + perpDx * step, baseY + perpDy * step, windBarbPaint)
            offset += step * 0.7f
        }
        repeat(barb.halfBarbs) {
            val baseX = tipX - dx * offset
            val baseY = tipY - dy * offset
            canvas.drawLine(baseX, baseY, baseX + perpDx * step * 0.5f, baseY + perpDy * step * 0.5f, windBarbPaint)
            offset += step * 0.7f
        }
    }

    private fun drawStorms(canvas: Canvas) {
        // No bounds filter here (unlike the old tile-scoped chart) — the
        // map is a free-pan world view, so every known storm is a
        // candidate; Storms.withinBounds still does the actual pure
        // filtering logic, just against the whole world.
        val visible = Storms.withinBounds(storms, latMin = -90.0, latMax = 90.0, lonMin = -180.0, lonMax = 180.0)
        for (storm in visible) {
            val (worldX, worldY) = worldOf(storm.lat, storm.lon)
            val (sx, sy) = transform.worldToScreen(worldX, worldY)
            val x = sx.toFloat()
            val y = sy.toFloat()

            val r = 20f
            canvas.drawCircle(x, y, r, stormPaint)
            canvas.drawLine(x - r * 1.6f, y, x - r, y, stormPaint)
            canvas.drawLine(x + r, y, x + r * 1.6f, y, stormPaint)
            canvas.drawLine(x, y - r * 1.6f, x, y - r, stormPaint)
            canvas.drawLine(x, y + r, x, y + r * 1.6f, stormPaint)

            canvas.drawText(storm.name, x, y + r * 2.2f, stormLabelPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        panDetector.onTouchEvent(event)
        return true
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            transform = transform.zoomed(
                factor = detector.scaleFactor.toDouble(),
                focusX = detector.focusX.toDouble(),
                focusY = detector.focusY.toDouble(),
                minScale = MIN_SCALE,
                maxScale = MAX_SCALE,
            )
            invalidate()
            return true
        }
    }

    private inner class PanListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            // onScroll's distance is "how far the finger moved since the last
            // call", already in the direction opposite to how the content
            // should move, so it's subtracted directly (not negated again).
            transform = transform.panned(-distanceX.toDouble(), -distanceY.toDouble())
            invalidate()
            return true
        }
    }

    companion object {
        private const val MIN_SCALE = 1.0
        private const val MAX_SCALE = 200.0
        private const val INITIAL_SCALE = 8.0
    }
}
