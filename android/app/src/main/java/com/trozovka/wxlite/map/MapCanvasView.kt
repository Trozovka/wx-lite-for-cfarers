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
    private var windData: WxlFile? = null
    private var storms: List<Storm> = emptyList()

    private val backgroundPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val coastlinePaint = Paint().apply {
        color = Color.rgb(90, 90, 90); strokeWidth = 2f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val windBarbPaint = Paint().apply {
        color = Color.BLACK; strokeWidth = 3f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val beaufortPaint = Paint().apply {
        color = Color.BLACK; textSize = 24f; isAntiAlias = true; textAlign = Paint.Align.CENTER
    }
    private val stormPaint = Paint().apply {
        color = Color.BLACK; strokeWidth = 4f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val stormLabelPaint = Paint().apply {
        color = Color.BLACK; textSize = 30f; isAntiAlias = true; textAlign = Paint.Align.CENTER
    }

    private val windStride = 3
    private val windBarbLengthPx = 36f

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val panDetector = GestureDetector(context, PanListener())

    fun setWindData(file: WxlFile?) {
        windData = file
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
        drawWind(canvas)
        drawStorms(canvas)
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
        val file = windData ?: return
        val latSpan = file.latMax - file.latMin
        val lonSpan = file.lonMax - file.lonMin
        if (file.nLat < 2 || file.nLon < 2) return

        var row = 0
        while (row < file.nLat) {
            var col = 0
            while (col < file.nLon) {
                val point = file.pointAt(row, col)
                val lat = file.latMin + latSpan * row / (file.nLat - 1)
                val lon = file.lonMin + lonSpan * col / (file.nLon - 1)
                val (worldX, worldY) = worldOf(lat.toDouble(), lon.toDouble())
                val (sx, sy) = transform.worldToScreen(worldX, worldY)

                val barb = WindBarb.fromComponents(point.windU.toDouble(), point.windV.toDouble())
                drawSingleBarb(canvas, sx.toFloat(), sy.toFloat(), barb)

                val force = Beaufort.forceForKnots(barb.speedKnots.toDouble())
                canvas.drawText("F$force", sx.toFloat(), sy.toFloat() + beaufortPaint.textSize + 12f, beaufortPaint)

                col += windStride
            }
            row += windStride
        }
    }

    private fun drawSingleBarb(canvas: Canvas, cx: Float, cy: Float, barb: WindBarbSymbol) {
        if (barb.isCalm) {
            canvas.drawCircle(cx, cy, 5f, windBarbPaint)
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
