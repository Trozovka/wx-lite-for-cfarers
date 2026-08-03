package com.trozovka.wxlite

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.trozovka.wxlite.chart.Coordinates
import com.trozovka.wxlite.data.AreaStore
import com.trozovka.wxlite.data.ForecastRepository
import com.trozovka.wxlite.data.ForecastTier
import com.trozovka.wxlite.data.Tiles
import com.trozovka.wxlite.map.MapCanvasView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen pan/zoom map (MapCanvasView) with a fixed center crosshair
 * (its exact lat/lon shown above the date/time bar), a passage-plan area
 * outline (up to 10 waypoints, set on a separate screen), and forecast
 * date/time controls at the bottom. Offline-first flow unchanged
 * underneath: read from local cache -> explicit sync updates the cache ->
 * render. Everything shown here works with zero connectivity except the
 * Sync button itself.
 *
 * Lives in :core (not the free app's own module) so both wx-lite's free
 * app and wx-pro's paid app launch the exact same screen instead of
 * maintaining two copies -- the only difference between tiers is the
 * [EXTRA_TIER] intent extra, defaulting to FREE when absent (the free
 * app's manifest launches this directly, with no extras). The paid app
 * launches it explicitly with EXTRA_TIER=PAID after its own license gate.
 */
class MainActivity : Activity() {
    private lateinit var areaStore: AreaStore
    private lateinit var repository: ForecastRepository
    private lateinit var statusView: TextView
    private lateinit var mapView: MapCanvasView
    private lateinit var crosshairLabel: TextView
    private lateinit var hourLabel: TextView
    private lateinit var prevHourBtn: Button
    private lateinit var nextHourBtn: Button

    private var availableHours: List<Int> = emptyList()
    private var currentHourIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tier = when (intent.getStringExtra(EXTRA_TIER)) {
            "PAID" -> ForecastTier.PAID
            else -> ForecastTier.FREE
        }

        areaStore = AreaStore(this)
        repository = ForecastRepository(this, tier)

        val root = FrameLayout(this)

        mapView = MapCanvasView(this)
        root.addView(mapView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        mapView.onViewportChanged = { updateCrosshairLabel() }

        // Top-left: opens the passage-plan area screen (10 waypoints) and
        // syncs the tile currently under the crosshair.
        val topPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 64, 24, 16)
            setBackgroundColor(Color.argb(200, 255, 255, 255))
        }

        val setAreaBtn = Button(this).apply {
            text = "Set Passage Area (10 pts)"
            setOnClickListener {
                startActivityForResult(Intent(this@MainActivity, AreaWaypointActivity::class.java), REQUEST_SET_AREA)
            }
        }
        topPanel.addView(setAreaBtn)

        // Syncs only the tile under the crosshair (wherever the map is
        // currently centered) -- ~150KB for a full 10-day forecast
        // (measured against the real published data), vs. ~3.6MB for all
        // 24 world tiles. On a Marlink-class satellite link that gap is
        // the difference between a routine sync and one that could take a
        // very long time or cost real money, so this stays scoped to one
        // region at a time. To check a different region (e.g. home, while
        // sailing elsewhere), pan the crosshair there and Sync now again
        // -- each synced region stays cached and rendered afterward.
        val syncBtn = Button(this).apply {
            text = "Sync now"
            setOnClickListener {
                text = "Syncing..."
                isEnabled = false
                val (lat, lon) = mapView.currentCenterLatLon()
                val tileId = Tiles.tileForPosition(lat, lon)
                if (tileId == null) {
                    text = "Sync now"
                    isEnabled = true
                    statusView.text = "Crosshair is outside covered range (60°S-60°N)."
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

        // Bottom: crosshair position, then forecast date/time -- the
        // crosshair readout sits directly above the date/time row, same
        // font size, both inside one bottom panel.
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 32)
            setBackgroundColor(Color.argb(200, 255, 255, 255))
        }

        crosshairLabel = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
        }
        bottomBar.addView(crosshairLabel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        hourLabel = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
        }
        bottomBar.addView(hourLabel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val hourButtonsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        prevHourBtn = Button(this).apply {
            text = "< Earlier"
            setOnClickListener { stepHour(-1) }
        }
        nextHourBtn = Button(this).apply {
            text = "Later >"
            setOnClickListener { stepHour(1) }
        }
        hourButtonsRow.addView(prevHourBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        hourButtonsRow.addView(nextHourBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bottomBar.addView(hourButtonsRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        root.addView(
            bottomBar,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
            },
        )

        setContentView(root)
        loadArea(recenterOnPointOne = true)
        updateCrosshairLabel()
        refreshStatus()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SET_AREA) {
            loadArea(recenterOnPointOne = true)
        }
    }

    private fun loadArea(recenterOnPointOne: Boolean) {
        val points = areaStore.get()
        mapView.setArea(points)
        if (recenterOnPointOne) {
            points.firstOrNull()?.let { mapView.centerOn(it.lat, it.lon) }
        }
    }

    private fun updateCrosshairLabel() {
        val (lat, lon) = mapView.currentCenterLatLon()
        crosshairLabel.text = "Lat = ${Coordinates.formatLat(lat)}, Lon = ${Coordinates.formatLon(lon)}"
    }

    private fun stepHour(delta: Int) {
        if (availableHours.isEmpty()) return
        currentHourIndex = (currentHourIndex + delta).coerceIn(0, availableHours.size - 1)
        renderCurrentHour()
    }

    private fun refreshStatus() {
        availableHours = repository.availableHoursAnyTile()
        currentHourIndex = currentHourIndex.coerceIn(0, (availableHours.size - 1).coerceAtLeast(0))

        val lastSync = repository.lastSyncedAtMillis()
        val syncText = if (lastSync > 0) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(lastSync))
        } else {
            "never"
        }
        statusView.text = "Cached tiles: ${repository.cachedTileIds().size}/24 | " +
            "Hours: ${availableHours.size} | Synced: $syncText"

        mapView.setStorms(repository.cachedStormsList())

        updateHourControlsEnabled()
        renderCurrentHour()
    }

    private fun updateHourControlsEnabled() {
        prevHourBtn.isEnabled = availableHours.isNotEmpty() && currentHourIndex > 0
        nextHourBtn.isEnabled = availableHours.isNotEmpty() && currentHourIndex < availableHours.size - 1
    }

    private fun renderCurrentHour() {
        if (availableHours.isEmpty()) {
            hourLabel.text = "No data — tap Sync now"
            mapView.setTiles(emptyList())
            updateHourControlsEnabled()
            return
        }

        val hour = availableHours[currentHourIndex]
        // Render every tile that's been synced for this hour, across the
        // whole world, not just whichever tile the crosshair is over.
        val tilesForHour = repository.cachedFilesForHour(hour)
        mapView.setTiles(tilesForHour.values.toList())

        // Every tile for a given hour shares the same forecast valid time
        // (same run), so any one of them gives the right date/time label.
        val anyFile = tilesForHour.values.firstOrNull()
        if (anyFile != null) {
            val validDate = SimpleDateFormat("MMM d, HH:mm 'UTC'", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(Date(anyFile.validTimeSeconds * 1000))
            hourLabel.text = "+${hour}h — $validDate"
        } else {
            hourLabel.text = "+${hour}h (file missing)"
        }
        updateHourControlsEnabled()
    }

    companion object {
        /** Intent extra: "PAID" or "FREE" (default when absent). */
        const val EXTRA_TIER = "com.trozovka.wxlite.EXTRA_TIER"

        private const val REQUEST_SET_AREA = 1001
    }
}
