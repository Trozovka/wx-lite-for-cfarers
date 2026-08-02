package com.trozovka.wxlite.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * Zoomed-out world-overview map: an orthographic globe with coastlines,
 * the saved ship position, and tap-to-set-location — per spec's manual
 * lat/lon entry and position requirements. Deliberately low-detail (same
 * bundled coastline asset as the regional chart), no rotation gestures,
 * no 3D engine: a static disc the crew can glance at and tap.
 */
class GlobeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Called with (lat, lon) when the user taps inside the globe's disc. */
    var onLocationTapped: ((Double, Double) -> Unit)? = null

    var centerLonDeg: Double = 0.0
        set(value) {
            field = value
            invalidate()
        }

    private var shipLat: Double? = null
    private var shipLon: Double? = null

    private var coastline: List<CoastlinePolygon>? = null

    private val oceanPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true }
    private val outlinePaint = Paint().apply {
        color = Color.BLACK; strokeWidth = 3f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val coastlinePaint = Paint().apply {
        color = Color.rgb(120, 120, 120); strokeWidth = 2f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val shipPaint = Paint().apply {
        color = Color.BLACK; strokeWidth = 4f; style = Paint.Style.STROKE; isAntiAlias = true
    }

    fun setShipPosition(lat: Double?, lon: Double?) {
        shipLat = lat
        shipLon = lon
        invalidate()
    }

    private fun ensureCoastlineLoaded() {
        if (coastline != null) return
        coastline = try {
            context.assets.open("coastline.bin").use { CoastlineData.parse(it.readBytes()) }
        } catch (e: Exception) {
            Log.w("GlobeView", "Failed to load coastline asset", e)
            emptyList()
        }
    }

    private fun radius(): Float = min(width, height) / 2f * 0.9f
    private fun centerX(): Float = width / 2f
    private fun centerY(): Float = height / 2f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        ensureCoastlineLoaded()

        val r = radius()
        val cx = centerX()
        val cy = centerY()

        canvas.drawCircle(cx, cy, r, oceanPaint)
        canvas.drawCircle(cx, cy, r, outlinePaint)

        for (polygon in coastline.orEmpty()) {
            val points = polygon.points
            if (points.size < 2) continue
            for (i in 0 until points.size - 1) {
                val a = GlobeProjection.project(points[i].lat, points[i].lon, centerLonDeg, r.toDouble())
                val b = GlobeProjection.project(points[i + 1].lat, points[i + 1].lon, centerLonDeg, r.toDouble())
                if (a == null || b == null) continue
                canvas.drawLine(
                    cx + a.x.toFloat(), cy + a.y.toFloat(),
                    cx + b.x.toFloat(), cy + b.y.toFloat(),
                    coastlinePaint,
                )
            }
        }

        val lat = shipLat
        val lon = shipLon
        if (lat != null && lon != null) {
            val p = GlobeProjection.project(lat, lon, centerLonDeg, r.toDouble())
            if (p != null) {
                val x = cx + p.x.toFloat()
                val y = cy + p.y.toFloat()
                val markerR = 12f
                canvas.drawCircle(x, y, markerR, shipPaint)
                canvas.drawLine(x, y - markerR * 1.8f, x, y + markerR * 1.8f, shipPaint)
                canvas.drawLine(x - markerR * 1.8f, y, x + markerR * 1.8f, y, shipPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val r = radius()
        val xRel = (event.x - centerX()).toDouble()
        val yRel = (event.y - centerY()).toDouble()
        val latLon = GlobeProjection.unproject(xRel, yRel, centerLonDeg, r.toDouble()) ?: return true
        onLocationTapped?.invoke(latLon.lat, latLon.lon)
        return true
    }
}
