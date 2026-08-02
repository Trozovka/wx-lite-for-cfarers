package com.trozovka.wxlite

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.trozovka.wxlite.chart.ChartCanvasView
import com.trozovka.wxlite.data.ForecastRepository
import com.trozovka.wxlite.data.LocationStore
import com.trozovka.wxlite.data.Tiles
import com.trozovka.wxlite.map.GlobeView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Skeleton screen exercising the real offline-first flow: saved location
 * -> tile lookup -> read from local cache -> explicit sync updates the
 * cache -> render, with a forecast-hour picker per spec's "bottom of the
 * screen must have simple controls allowing selection of forecast
 * date/time". Everything shown here works with zero connectivity except
 * the Sync button itself.
 */
class MainActivity : Activity() {
    private lateinit var locationStore: LocationStore
    private lateinit var repository: ForecastRepository
    private lateinit var statusView: TextView
    private lateinit var chartView: ChartCanvasView
    private lateinit var hourLabel: TextView
    private lateinit var prevHourBtn: Button
    private lateinit var nextHourBtn: Button
    private lateinit var globeView: GlobeView
    private lateinit var latInput: EditText
    private lateinit var lonInput: EditText

    private var availableHours: List<Int> = emptyList()
    private var currentHourIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationStore = LocationStore(this)
        repository = ForecastRepository(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
        }

        statusView = TextView(this).apply { textSize = 14f }
        root.addView(statusView)

        val setTestLocationBtn = Button(this).apply {
            text = "Use test location (Manila)"
            setOnClickListener { setLocation(14.6, 121.0) }
        }
        root.addView(setTestLocationBtn)

        // World-overview globe, per spec's map-view requirement — tapping
        // it sets the ship's saved location directly, no separate mode.
        globeView = GlobeView(this).apply {
            onLocationTapped = { lat, lon -> setLocation(lat, lon) }
        }
        root.addView(
            globeView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 600),
        )

        // Manual lat/lon entry, per spec — bridge crew may know their exact
        // position and shouldn't be forced to tap a small globe for it.
        val manualEntryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        latInput = EditText(this).apply {
            hint = "Lat"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        lonInput = EditText(this).apply {
            hint = "Lon"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val setManualLocationBtn = Button(this).apply {
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
        manualEntryRow.addView(latInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        manualEntryRow.addView(lonInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        manualEntryRow.addView(setManualLocationBtn)
        root.addView(manualEntryRow)

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
        root.addView(syncBtn)

        chartView = ChartCanvasView(this)
        root.addView(
            chartView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 800),
        )

        // Forecast date/time control, per spec — deliberately just two
        // buttons and a label: "minimal buttons", bridge-use simplicity,
        // not a calendar widget.
        val hourControls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        prevHourBtn = Button(this).apply {
            text = "< Earlier"
            setOnClickListener { stepHour(-1) }
        }
        hourLabel = TextView(this).apply {
            textSize = 14f
            gravity = android.view.Gravity.CENTER
        }
        nextHourBtn = Button(this).apply {
            text = "Later >"
            setOnClickListener { stepHour(1) }
        }
        hourControls.addView(prevHourBtn)
        hourControls.addView(
            hourLabel,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        hourControls.addView(nextHourBtn)
        root.addView(hourControls)

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
        val sb = StringBuilder()
        sb.append("Trozovka WX Lite\n\n")

        if (position == null) {
            sb.append("No ship location saved yet.\n")
            statusView.text = sb.toString()
            availableHours = emptyList()
            globeView.setShipPosition(null, null)
            updateHourControlsEnabled()
            return
        }

        globeView.setShipPosition(position.lat, position.lon)
        globeView.centerLonDeg = position.lon

        val tileId = Tiles.tileForPosition(position.lat, position.lon)
        sb.append("Ship position: ${position.lat}, ${position.lon}\n")
        sb.append("Tile: ${tileId ?: "out of covered range"}\n\n")

        if (tileId == null) {
            statusView.text = sb.toString()
            availableHours = emptyList()
            updateHourControlsEnabled()
            return
        }

        availableHours = repository.availableHours(tileId)
        currentHourIndex = currentHourIndex.coerceIn(0, (availableHours.size - 1).coerceAtLeast(0))

        val lastSync = repository.lastSyncedAtMillis()
        sb.append("Cached forecast hours: ${availableHours.size}\n")
        sb.append(
            if (lastSync > 0) {
                "Last synced: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(lastSync))}\n"
            } else {
                "Never synced — no offline data yet.\n"
            },
        )
        statusView.text = sb.toString()

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
            updateHourControlsEnabled()
            return
        }

        val hour = availableHours[currentHourIndex]
        val file = repository.cachedFile(tileId, hour)
        if (file != null) {
            chartView.setData(file)
            chartView.setStorms(repository.cachedStormsList())
            val validDate = SimpleDateFormat("MMM d, HH:mm 'UTC'", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(Date(file.validTimeSeconds * 1000))
            hourLabel.text = "+${hour}h — $validDate"
        } else {
            hourLabel.text = "+${hour}h (file missing)"
        }
        updateHourControlsEnabled()
    }
}
