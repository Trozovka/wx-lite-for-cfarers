package com.trozovka.wxlite.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.trozovka.wxlite.data.WxlFile
import kotlin.math.cos
import kotlin.math.sin

/**
 * Monochrome weatherfax-style chart: isobars, H/L pressure centers, wind
 * barbs. Deliberately plain — no map tiles, no imagery, matching the
 * spec's explicit "black-and-white/monochrome weatherfax style".
 *
 * Data row 0 is the grid's SOUTHERNMOST row (confirmed against real GFS
 * output, not assumed) — screen Y is flipped accordingly so north renders
 * at the top, as expected.
 */
class ChartCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var wxlFile: WxlFile? = null
    private var storms: List<Storm> = emptyList()

    private val backgroundPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val isobarPaint = Paint().apply {
        color = Color.BLACK; strokeWidth = 2f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val labelPaint = Paint().apply {
        color = Color.BLACK; textSize = 40f; isAntiAlias = true; textAlign = Paint.Align.CENTER
    }
    private val windBarbPaint = Paint().apply {
        color = Color.BLACK; strokeWidth = 3f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val stormPaint = Paint().apply {
        color = Color.BLACK; strokeWidth = 4f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val stormLabelPaint = Paint().apply {
        color = Color.BLACK; textSize = 32f; isAntiAlias = true; textAlign = Paint.Align.CENTER
    }

    // Every Nth grid point gets a wind barb — one per isobar grid cell would
    // be far too dense to read.
    private val windBarbStride = 3
    private val windBarbLengthPx = 40f

    fun setData(file: WxlFile) {
        wxlFile = file
        invalidate()
    }

    fun setStorms(allStorms: List<Storm>) {
        storms = allStorms
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val file = wxlFile ?: return
        if (file.nLat < 2 || file.nLon < 2 || width == 0 || height == 0) return

        val cellW = width.toFloat() / (file.nLon - 1)
        val cellH = height.toFloat() / (file.nLat - 1)

        // row 0 = south -> bottom of screen, so Y is flipped relative to row.
        fun screenX(col: Double) = (col * cellW).toFloat()
        fun screenY(row: Double) = (height - row * cellH).toFloat()

        drawIsobars(canvas, file, ::screenX, ::screenY)
        drawPressureCenters(canvas, file, ::screenX, ::screenY)
        drawWindBarbs(canvas, file, ::screenX, ::screenY)
        drawStorms(canvas, file, ::screenX, ::screenY)
    }

    private fun drawStorms(
        canvas: Canvas,
        file: WxlFile,
        screenX: (Double) -> Float,
        screenY: (Double) -> Float,
    ) {
        val visible = Storms.withinBounds(
            storms,
            latMin = file.latMin.toDouble(),
            latMax = file.latMax.toDouble(),
            lonMin = file.lonMin.toDouble(),
            lonMax = file.lonMax.toDouble(),
        )
        val latSpan = file.latMax - file.latMin
        val lonSpan = file.lonMax - file.lonMin
        if (latSpan == 0f || lonSpan == 0f) return

        for (storm in visible) {
            val row = (storm.lat - file.latMin) / latSpan * (file.nLat - 1)
            val col = (storm.lon - file.lonMin) / lonSpan * (file.nLon - 1)
            val x = screenX(col)
            val y = screenY(row)

            // Simple distinguishing symbol: a circle with crossing spokes,
            // the traditional tropical-cyclone chart mark — kept plain to
            // match the monochrome weatherfax aesthetic, not a filled icon.
            val r = 22f
            canvas.drawCircle(x, y, r, stormPaint)
            canvas.drawLine(x - r * 1.6f, y, x - r, y, stormPaint)
            canvas.drawLine(x + r, y, x + r * 1.6f, y, stormPaint)
            canvas.drawLine(x, y - r * 1.6f, x, y - r, stormPaint)
            canvas.drawLine(x, y + r, x, y + r * 1.6f, stormPaint)

            val label = buildString {
                append(storm.name)
                if (storm.pressureHpa != null) append(" ${storm.pressureHpa}hPa")
            }
            canvas.drawText(label, x, y + r * 2.2f, stormLabelPaint)
        }
    }

    private fun drawIsobars(
        canvas: Canvas,
        file: WxlFile,
        screenX: (Double) -> Float,
        screenY: (Double) -> Float,
    ) {
        val segments = Isobars.generate(file.pressureGrid(), intervalHpa = 4.0)
        for (seg in segments) {
            canvas.drawLine(
                screenX(seg.p1.col), screenY(seg.p1.row),
                screenX(seg.p2.col), screenY(seg.p2.row),
                isobarPaint,
            )
        }
    }

    private fun drawPressureCenters(
        canvas: Canvas,
        file: WxlFile,
        screenX: (Double) -> Float,
        screenY: (Double) -> Float,
    ) {
        val centers = PressureCenters.find(file.pressureGrid())
        for (center in centers) {
            val label = if (center.type == CenterType.HIGH) "H" else "L"
            val x = screenX(center.col.toDouble())
            val y = screenY(center.row.toDouble())
            canvas.drawText(label, x, y, labelPaint)
            canvas.drawText("${center.valueHpa.toInt()}", x, y + labelPaint.textSize, labelPaint)
        }
    }

    private fun drawWindBarbs(
        canvas: Canvas,
        file: WxlFile,
        screenX: (Double) -> Float,
        screenY: (Double) -> Float,
    ) {
        var row = 0
        while (row < file.nLat) {
            var col = 0
            while (col < file.nLon) {
                val point = file.pointAt(row, col)
                val barb = WindBarb.fromComponents(point.windU.toDouble(), point.windV.toDouble())
                drawSingleBarb(canvas, screenX(col.toDouble()), screenY(row.toDouble()), barb)
                col += windBarbStride
            }
            row += windBarbStride
        }
    }

    private fun drawSingleBarb(canvas: Canvas, cx: Float, cy: Float, barb: WindBarbSymbol) {
        if (barb.isCalm) {
            canvas.drawCircle(cx, cy, 6f, windBarbPaint)
            return
        }

        // Staff points in the from-direction (compass bearing, clockwise
        // from north); screen angle needs the standard bearing-to-screen
        // conversion (0deg=up, clockwise) with Y inverted since screen Y
        // grows downward.
        val bearingRad = Math.toRadians(barb.fromDirectionDeg)
        val dx = sin(bearingRad).toFloat()
        val dy = -cos(bearingRad).toFloat()

        val tipX = cx + dx * windBarbLengthPx
        val tipY = cy + dy * windBarbLengthPx
        canvas.drawLine(cx, cy, tipX, tipY, windBarbPaint)

        // Barb marks are drawn perpendicular to the staff, near the tip,
        // stepping back toward the center for each successive mark.
        val perpDx = -dy
        val perpDy = dx
        var offset = 0f
        val step = 8f

        repeat(barb.pennants) {
            val baseX = tipX - dx * offset
            val baseY = tipY - dy * offset
            val farX = baseX - dx * step + perpDx * step
            val farY = baseY - dy * step + perpDy * step
            val path = android.graphics.Path()
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
}
