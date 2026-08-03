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
import com.trozovka.wxlite.chart.Coordinates
import com.trozovka.wxlite.chart.Isobars
import com.trozovka.wxlite.chart.PressureCenters
import com.trozovka.wxlite.chart.Storm
import com.trozovka.wxlite.chart.Storms
import com.trozovka.wxlite.chart.TrackedLow
import com.trozovka.wxlite.chart.WindBarb
import com.trozovka.wxlite.chart.WindBarbSymbol
import com.trozovka.wxlite.data.AreaPoint
import com.trozovka.wxlite.data.WxlFile
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-screen, pannable, pinch-zoomable map: a 1-degree lat/lon grid,
 * coastlines (bundled Natural Earth data), the passage-plan area outline,
 * a wind field (barbs + Beaufort force, F5 and above only), pressure
 * (isobars + H/L), and cyclone markers -- plus a fixed-center crosshair
 * that always reads out the exact lat/lon under it. Deliberately NOT a
 * tile-streaming basemap (no osmdroid/Mapbox/Maps SDK) -- panning and
 * zooming are a pure local Canvas transform (MapTransform) over data
 * already on disk. Nothing is fetched by panning; only an explicit sync
 * touches the network.
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
    private var areaPoints: List<AreaPoint?> = emptyList()
    private var lowPressureTracks: List<TrackedLow> = emptyList()

    /** Fires whenever the viewport (pan/zoom/programmatic recenter)
     * changes, i.e. whenever the crosshair's lat/lon reading would be
     * stale -- the caller re-reads [currentCenterLatLon] and updates its
     * own on-screen label instead of this view drawing that text itself,
     * since the label lives in the bottom bar alongside the date/time. */
    var onViewportChanged: (() -> Unit)? = null

    private val backgroundPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val gridPaint = Paint().apply {
        color = Color.rgb(210, 210, 210); strokeWidth = 1f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val gridLabelPaint = Paint().apply {
        color = Color.rgb(130, 130, 130); textSize = 18f; isAntiAlias = true
    }
    private val coastlinePaint = Paint().apply {
        color = Color.rgb(90, 90, 90); strokeWidth = 2f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val areaPaint = Paint().apply {
        color = Color.BLACK; strokeWidth = 1.5f; style = Paint.Style.STROKE; isAntiAlias = true
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 8f), 0f)
    }
    private val areaVertexPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL; isAntiAlias = true }
    private val areaLabelPaint = Paint().apply {
        color = Color.BLACK; textSize = 22f; isAntiAlias = true; textAlign = Paint.Align.CENTER
    }
    private val windBarbPaint = Paint().apply {
        color = Color.BLACK; strokeWidth = 3f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val beaufortPaint = Paint().apply {
        color = Color.BLACK; textSize = 32f; isAntiAlias = true; textAlign = Paint.Align.CENTER
    }
    private val isobarPaint = Paint().apply {
        color = Color.BLACK; strokeWidth = 2f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val isobarLabelPaint = Paint().apply {
        color = Color.BLACK; textSize = 20f; isAntiAlias = true; textAlign = Paint.Align.CENTER
    }
    private val centerLabelPaint = Paint().apply {
        color = Color.BLACK; textSize = 50f; isAntiAlias = true; textAlign = Paint.Align.CENTER
    }
    private val centerValuePaint = Paint().apply {
        color = Color.BLACK; textSize = 28f; isAntiAlias = true; textAlign = Paint.Align.CENTER
    }
    private val stormPaint = Paint().apply {
        color = Color.BLACK; strokeWidth = 4f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val stormLabelPaint = Paint().apply {
        color = Color.BLACK; textSize = 30f; isAntiAlias = true; textAlign = Paint.Align.CENTER
    }
    private val crosshairPaint = Paint().apply {
        color = Color.BLACK; strokeWidth = 2f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val lowTrackLinePaint = Paint().apply {
        color = Color.RED; strokeWidth = 5f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val lowTrackHeadPaint = Paint().apply {
        color = Color.RED; style = Paint.Style.FILL; isAntiAlias = true
    }

    // Barbs were reported as "almost invisible" on a real device -- ~65%
    // longer than the original 36px, and the Beaufort label is now placed
    // beside the shaft (perpendicular offset) instead of directly below
    // it, so the two no longer visually merge into e.g. "F4/".
    private val windBarbLengthPx = 60f
    private val beaufortLabelOffsetPx = 40f

    // Minimum on-screen spacing between wind barbs/Beaufort labels, in
    // pixels -- the stride actually used is derived from this and the
    // current zoom (see strideFor), so labels stay readable instead of
    // piling up at low zoom or wastefully sparse at high zoom.
    private val minLabelSpacingPx = 130.0

    // Fixed screen-space radius -- deliberately NOT multiplied by
    // transform.scale anywhere, so the crosshair stays the same size
    // regardless of zoom level. Per operator direction: if the user wants
    // a more precise position, they zoom in; the crosshair itself is not
    // the precision control.
    private val crosshairRadiusPx = 14f

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val panDetector = GestureDetector(context, PanListener())

    /** Renders wind + pressure for every tile passed in -- callers should
     * pass every cached tile for the selected hour, not just one region,
     * so panning shows data anywhere that's actually been synced (the
     * "camera" here only decides what's visible, it doesn't limit what
     * data exists). */
    fun setTiles(newTiles: List<WxlFile>) {
        tiles = newTiles
        invalidate()
    }

    fun setStorms(allStorms: List<Storm>) {
        storms = allStorms
        invalidate()
    }

    /** Up to 10 waypoints (nulls for skipped rows) outlining the
     * passage-plan area, connected in order. */
    fun setArea(points: List<AreaPoint?>) {
        areaPoints = points
        invalidate()
    }

    /** Red movement arrows for tracked low-pressure centers -- recomputed
     * by the caller whenever the selected forecast hour changes, since a
     * low's direction of movement is only meaningful relative to the next
     * (or previous) cached hour. */
    fun setLowPressureTracks(tracks: List<TrackedLow>) {
        lowPressureTracks = tracks
        invalidate()
    }

    /** The lat/lon currently under the fixed center crosshair -- the
     * inverse of the world/screen projection used for every drawn layer. */
    fun currentCenterLatLon(): Pair<Double, Double> {
        val (worldX, worldY) = transform.screenToWorld(width / 2.0, height / 2.0)
        return Pair(-worldY, worldX) // inverse of worldOf: lat = -worldY, lon = worldX
    }

    /** Recenters the map on (lat, lon) without changing the current zoom level. */
    fun centerOn(latDeg: Double, lonDeg: Double) {
        if (width == 0 || height == 0) {
            pendingCenter = Pair(latDeg, lonDeg)
            return
        }
        applyCenter(latDeg, lonDeg)
    }

    private fun applyCenter(latDeg: Double, lonDeg: Double) {
        val (worldX, worldY) = worldOf(latDeg, lonDeg)
        transform = transform.copy(
            translateX = width / 2.0 - worldX * transform.scale,
            translateY = height / 2.0 - worldY * transform.scale,
        )
        invalidate()
        onViewportChanged?.invoke()
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

    private fun screenOf(latDeg: Double, lonDeg: Double): Pair<Float, Float> {
        val (wx, wy) = worldOf(latDeg, lonDeg)
        val (sx, sy) = transform.worldToScreen(wx, wy)
        return Pair(sx.toFloat(), sy.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        ensureCoastlineLoaded()

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        drawGrid(canvas)
        drawCoastline(canvas)
        drawArea(canvas)
        drawPressure(canvas)
        drawLowPressureTracks(canvas)
        drawWind(canvas)
        drawStorms(canvas)
        drawCrosshair(canvas)
    }

    /** 1-degree lat/lon reference grid across whatever's currently visible
     * -- found by inverse-projecting the four screen corners, not by
     * iterating the whole world, so it stays cheap regardless of zoom. */
    private fun drawGrid(canvas: Canvas) {
        val corners = listOf(
            transform.screenToWorld(0.0, 0.0),
            transform.screenToWorld(width.toDouble(), 0.0),
            transform.screenToWorld(0.0, height.toDouble()),
            transform.screenToWorld(width.toDouble(), height.toDouble()),
        )
        val visibleLonMin = corners.minOf { it.first }.coerceAtLeast(-180.0)
        val visibleLonMax = corners.maxOf { it.first }.coerceAtMost(180.0)
        val visibleLatMin = corners.minOf { -it.second }.coerceAtLeast(-90.0)
        val visibleLatMax = corners.maxOf { -it.second }.coerceAtMost(90.0)

        // Sanity guard: shouldn't happen given MIN_SCALE, but cheap
        // insurance against ever drawing thousands of lines.
        if (visibleLonMax - visibleLonMin > 400 || visibleLatMax - visibleLatMin > 200) return

        var lat = Coordinates.floorDegree(visibleLatMin)
        val latTop = Coordinates.ceilDegree(visibleLatMax)
        while (lat <= latTop) {
            val (x1, y1) = screenOf(lat.toDouble(), visibleLonMin)
            val (x2, y2) = screenOf(lat.toDouble(), visibleLonMax)
            canvas.drawLine(x1, y1, x2, y2, gridPaint)
            canvas.drawText("$lat°", 4f, y1 - 4f, gridLabelPaint)
            lat++
        }

        var lon = Coordinates.floorDegree(visibleLonMin)
        val lonRight = Coordinates.ceilDegree(visibleLonMax)
        while (lon <= lonRight) {
            val (x1, y1) = screenOf(visibleLatMin, lon.toDouble())
            val (x2, y2) = screenOf(visibleLatMax, lon.toDouble())
            canvas.drawLine(x1, y1, x2, y2, gridPaint)
            canvas.drawText("$lon°", x1 + 4f, 20f, gridLabelPaint)
            lon++
        }
    }

    private fun drawArea(canvas: Canvas) {
        val filled = areaPoints.mapIndexedNotNull { i, p -> p?.let { Pair(i + 1, it) } }
        if (filled.isEmpty()) return

        val screenPoints = filled.map { (index, point) -> Pair(index, screenOf(point.lat, point.lon)) }

        if (screenPoints.size >= 2) {
            for (i in screenPoints.indices) {
                val (_, a) = screenPoints[i]
                val (_, b) = screenPoints[(i + 1) % screenPoints.size]
                canvas.drawLine(a.first, a.second, b.first, b.second, areaPaint)
            }
        }

        for ((index, point) in screenPoints) {
            canvas.drawCircle(point.first, point.second, 10f, areaVertexPaint)
            canvas.drawText(index.toString(), point.first, point.second - 18f, areaLabelPaint)
        }
    }

    /** Fixed screen-space crosshair -- always at the exact view center,
     * always the same pixel size regardless of zoom. */
    private fun drawCrosshair(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, crosshairRadiusPx, crosshairPaint)
        canvas.drawLine(cx, cy - crosshairRadiusPx * 1.8f, cx, cy + crosshairRadiusPx * 1.8f, crosshairPaint)
        canvas.drawLine(cx - crosshairRadiusPx * 1.8f, cy, cx + crosshairRadiusPx * 1.8f, cy, crosshairPaint)
    }

    /** Converts a tile-local grid position (row/col, fractional) to a
     * screen point, via the same lat/lon -> world -> screen path used for
     * every other layer. Shared by isobars and pressure centers. */
    private fun tileGridToScreen(file: WxlFile, row: Double, col: Double): Pair<Float, Float> {
        val latSpan = file.latMax - file.latMin
        val lonSpan = file.lonMax - file.lonMin
        val lat = file.latMin + latSpan * row / (file.nLat - 1)
        val lon = file.lonMin + lonSpan * col / (file.nLon - 1)
        return screenOf(lat.toDouble(), lon.toDouble())
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

    /** Red arrow from each tracked low's current position, pointing toward
     * where it's headed at the next cached forecast hour -- offset a
     * little from the low's own position so it doesn't sit on top of the
     * "L" label, and starting away from center like the wind barbs do. */
    private fun drawLowPressureTracks(canvas: Canvas) {
        for (track in lowPressureTracks) {
            val (cx, cy) = screenOf(track.lat, track.lon)
            val bearingRad = Math.toRadians(track.bearingDeg)
            val dx = sin(bearingRad).toFloat()
            val dy = -cos(bearingRad).toFloat()

            val startOffset = 55f
            val length = 75f
            val startX = cx + dx * startOffset
            val startY = cy + dy * startOffset
            val tipX = cx + dx * (startOffset + length)
            val tipY = cy + dy * (startOffset + length)
            canvas.drawLine(startX, startY, tipX, tipY, lowTrackLinePaint)

            val perpDx = -dy
            val perpDy = dx
            val headLen = 18f
            val headWidth = 11f
            val path = Path()
            path.moveTo(tipX, tipY)
            path.lineTo(tipX - dx * headLen + perpDx * headWidth, tipY - dy * headLen + perpDy * headWidth)
            path.lineTo(tipX - dx * headLen - perpDx * headWidth, tipY - dy * headLen - perpDy * headWidth)
            path.close()
            canvas.drawPath(path, lowTrackHeadPaint)
        }
    }

    private fun drawCoastline(canvas: Canvas) {
        for (polygon in coastline.orEmpty()) {
            val points = polygon.points
            if (points.size < 2) continue
            for (i in 0 until points.size - 1) {
                val (x1, y1) = screenOf(points[i].lat, points[i].lon)
                val (x2, y2) = screenOf(points[i + 1].lat, points[i + 1].lon)
                canvas.drawLine(x1, y1, x2, y2, coastlinePaint)
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
                val barb = WindBarb.fromComponents(point.windU.toDouble(), point.windV.toDouble())
                // Classified from the true unrounded speed, not the
                // 5kt-rounded barb symbol speed -- rounding first can shift
                // the force by a whole category near a boundary (see
                // WindBarbSymbol.trueSpeedKnots).
                val force = Beaufort.forceForKnots(barb.trueSpeedKnots)
                // Per operator direction: only F5 (fresh breeze) and above
                // are drawn -- below that clutters the chart without
                // adding passage-planning-relevant information.
                if (Beaufort.isSignificant(force)) {
                    val (sx, sy) = tileGridToScreen(file, row.toDouble(), col.toDouble())
                    drawSingleBarb(canvas, sx, sy, barb, "F$force")
                }
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
            val (x, y) = screenOf(storm.lat, storm.lon)

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
            onViewportChanged?.invoke()
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
            onViewportChanged?.invoke()
            return true
        }
    }

    companion object {
        private const val MIN_SCALE = 1.0
        private const val MAX_SCALE = 200.0
        private const val INITIAL_SCALE = 8.0
    }
}
