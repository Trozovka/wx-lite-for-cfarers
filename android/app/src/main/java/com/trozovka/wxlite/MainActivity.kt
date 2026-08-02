package com.trozovka.wxlite

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.trozovka.wxlite.data.ForecastRepository
import com.trozovka.wxlite.data.LocationStore
import com.trozovka.wxlite.data.Tiles
import com.trozovka.wxlite.map.MapCanvasView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen pan/zoom map (MapCanvasView) with two thin overlay panels:
 * lat/lon entry top-left, forecast date/time (Earlier/Later) at the
 * bottom. Offline-first flow unchanged underneath: saved location -> tile
 * lookup -> read from local cache -> explicit sync updates the cache ->
 * render. Everything shown here works with zero connectivity except the
 * Sync button itself.
 *
 * Replaces the previous scrolling-column layout (fixed-size chart panel +
 * small globe), which real device testing showed broke in landscape and
 * had no zoom/pan anywhere.
 */
class MainActivity : Activity() {
    private lateinit var locationStore: LocationStore
    private lateinit var repository: ForecastRepository
    private lateinit var statusView: TextView
    private lateinit var mapView: MapCanvasView
    private lateinit var hourLabel: TextView
    private lateinit var prevHourBtn: Button
    private lateinit var nextHourBtn: Button
    private lateinit var latInput: EditText
    private lateinit var lonInput: EditText

    private var availableHours: List<Int> = emptyList()
    private var currentHourIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationStore = LocationStore(this)
        repository = ForecastRepository(this)

        val root = FrameLayout(this)

        mapView = MapCanvasView(this)
        root.addView(mapView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // Top-left: lat/lon entry, defaulted to the same Manila coordinates
        // as before, so the fields double as a worked example of correct
        // input format. Overlaid on the map, not a layout row competing
        // with it for vertical space (that's what broke in landscape).
        val topPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 64, 24, 16)
            setBackgroundColor(Color.argb(200, 255, 255, 255))
        }

        val latLonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        latInput = EditText(this).apply {
            hint = "Lat"
            setText(MANILA_LAT.toString())
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        lonInput = EditText(this).apply {
            hint = "Lon"
            setText(MANILA_LON.toString())
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val setLocationBtn = Button(this).apply {
            text = "Set"
            setOnClickListener {
                val lat = latInput.text.toString().toDoubleOrNull()
                val lon = lonInput.text.toString().toDoubleOrNull()
                if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                    Toast.makeText(this@MainActivity, "Enter a valid lat (-90..90) and lon (-180..180)", Toast.LENGTH_SHORT).show()
                } else {
                    setLocation(lat, lon)
                }
            }
        }
        latLonRow.addView(latInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        latLonRow.addView(lonInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        latLonRow.addView(setLocationBtn)
        topPanel.addView(latLonRow)

        val syncBtn = Button(this).apply {
            text = "Sync now"
            setOnClickListener {
                text = "Syncing..."
                isEnabled = false
                val position = locationStore.get()
                val tileId = position?.let { Tiles.tileForPosition(it.lat, it.lon) }
                if (tileId == null) {
                    statusView.text = "Set a location first."
                    text = "Sync now"
                    isEnabled = true
                } else {
                    repository.sync(tileId) {
                        runOnUiThread {
                            text = "Sync now"
                            isEnabled = true
                            refreshStatus()
                        }
                    }
                }
            }
        }
        topPanel.addView(syncBtn)

        statusView = TextView(this).apply { textSize = 12f }
        topPanel.addView(statusView)

        root.addView(
            topPanel,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.START
            },
        )

        // Bottom: forecast date/time, the sole bottom control per spec —
        // lat/lon now lives in topPanel instead of a four-field bottom row.
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 32)
            setBackgroundColor(Color.argb(200, 255, 255, 255))
        }
        prevHourBtn = Button(this).apply {
            text = "< Earlier"
            setOnClickListener { stepHour(-1) }
        }
        hourLabel = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
        }
        nextHourBtn = Button(this).apply {
            text = "Later >"
            setOnClickListener { stepHour(1) }
        }
        bottomBar.addView(prevHourBtn)
        bottomBar.addView(hourLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bottomBar.addView(nextHourBtn)

        root.addView(
            bottomBar,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
            },
        )

        setContentView(root)
        refreshStatus()
    }

    private fun currentTileId(): String? = locationStore.get()?.let { Tiles.tileForPosition(it.lat, it.lon) }

    private fun setLocation(lat: Double, lon: Double) {
        locationStore.save(lat, lon)
        refreshStatus()
    }

    private fun stepHour(delta: Int) {
        if (availableHours.isEmpty()) return
        currentHourIndex = (currentHourIndex + delta).coerceIn(0, availableHours.size - 1)
        renderCurrentHour()
    }

    private fun refreshStatus() {
        val position = locationStore.get()

        if (position == null) {
            statusView.text = "No ship location saved yet."
            availableHours = emptyList()
            updateHourControlsEnabled()
            return
        }

        mapView.centerOn(position.lat, position.lon)

        val tileId = Tiles.tileForPosition(position.lat, position.lon)
        if (tileId == null) {
            statusView.text = "Position ${position.lat}, ${position.lon} — out of covered range."
            availableHours = emptyList()
            updateHourControlsEnabled()
            return
        }

        availableHours = repository.availableHours(tileId)
        currentHourIndex = currentHourIndex.coerceIn(0, (availableHours.size - 1).coerceAtLeast(0))

        val lastSync = repository.lastSyncedAtMillis()
        val syncText = if (lastSync > 0) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(lastSync))
        } else {
            "never"
        }
        statusView.text = "Tile: $tileId | Cached: ${availableHours.size}h | Synced: $syncText"

        mapView.setStorms(repository.cachedStormsList())

        updateHourControlsEnabled()
        renderCurrentHour()
    }

    private fun updateHourControlsEnabled() {
        prevHourBtn.isEnabled = availableHours.isNotEmpty() && currentHourIndex > 0
        nextHourBtn.isEnabled = availableHours.isNotEmpty() && currentHourIndex < availableHours.size - 1
    }

    private fun renderCurrentHour() {
        val tileId = currentTileId()
        if (tileId == null || availableHours.isEmpty()) {
            hourLabel.text = "No data"
            mapView.setWindData(null)
            updateHourControlsEnabled()
            return
        }

        val hour = availableHours[currentHourIndex]
        val file = repository.cachedFile(tileId, hour)
        if (file != null) {
            mapView.setWindData(file)
            val validDate = SimpleDateFormat("MMM d, HH:mm 'UTC'", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(Date(file.validTimeSeconds * 1000))
            hourLabel.text = "+${hour}h — $validDate"
        } else {
            mapView.setWindData(null)
            hourLabel.text = "+${hour}h (file missing)"
        }
        updateHourControlsEnabled()
    }

    companion object {
        // Same worked-example position used as the default lat/lon input
        // values — kept as one named constant so the two can't drift.
        private const val MANILA_LAT = 14.6
        private const val MANILA_LON = 121.0
    }
}
