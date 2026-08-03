package com.trozovka.wxlite

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.trozovka.wxlite.chart.CenterType
import com.trozovka.wxlite.chart.Coordinates
import com.trozovka.wxlite.chart.LowPressureTracker
import com.trozovka.wxlite.chart.PressureCenters
import com.trozovka.wxlite.chart.TrackedLow
import com.trozovka.wxlite.data.AreaStore
import com.trozovka.wxlite.data.ForecastRepository
import com.trozovka.wxlite.data.ForecastTier
import com.trozovka.wxlite.data.Tiles
import com.trozovka.wxlite.data.WxlFile
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

        // Syncs the distinct tile(s) that actually CONTAIN each
        // passage-plan waypoint (falls back to the tile under the
        // crosshair if no area is set). Deliberately not the centroid
        // (the arithmetic mean of a spread-out area can land nowhere near
        // any of the actual points -- e.g. averaging Florida, the Irish
        // Sea, Brittany, and Cuba lands mid-Atlantic, in open ocean,
        // confirmed as a real bug this way) and not every tile the area's
        // bounding box touches (pulls in tiles the area barely grazes).
        // A tight-fitting single tile only exists for an area that's
        // genuinely compact; a route that legitimately spans multiple
        // regions needs multiple tiles synced to show real coverage --
        // that's an honest reflection of the area defined, not a bug.
        val syncBtn = Button(this).apply {
            text = "Sync now"
            setOnClickListener {
                val tileIds = tileIdsToSync()
                if (tileIds.isEmpty()) {
                    statusView.text = "Crosshair is outside covered range (60°S-60°N)."
                } else {
                    syncTiles(this, tileIds)
                }
            }
        }
        topPanel.addView(syncBtn)

        val aboutBtn = Button(this).apply {
            text = "About"
            setOnClickListener { showAboutDialog() }
        }
        topPanel.addView(aboutBtn)

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

    /** The distinct tile(s) each passage-plan waypoint actually falls in;
     * falls back to the tile under the crosshair if no area is set. */
    private fun tileIdsToSync(): List<String> {
        val points = areaStore.get().filterNotNull()
        if (points.isNotEmpty()) {
            val tileIds = Tiles.tilesFor(points.map { Pair(it.lat, it.lon) })
            if (tileIds.isNotEmpty()) return tileIds
        }
        val (lat, lon) = mapView.currentCenterLatLon()
        return listOfNotNull(Tiles.tileForPosition(lat, lon))
    }

    private fun syncTiles(button: Button, tileIds: List<String>) {
        button.isEnabled = false
        val total = tileIds.size
        var completed = 0
        button.text = if (total > 1) "Syncing 0/$total..." else "Syncing..."
        for (tileId in tileIds) {
            repository.sync(tileId) {
                runOnUiThread {
                    completed++
                    if (completed < total) {
                        button.text = "Syncing $completed/$total..."
                    } else {
                        button.text = "Sync now"
                        button.isEnabled = true
                        refreshStatus()
                    }
                }
            }
        }
    }

    private fun showAboutDialog() {
        val tierLabel = when (intent.getStringExtra(EXTRA_TIER)) {
            "PAID" -> "Pro — 10-day forecast"
            else -> "Lite — 1-day forecast"
        }
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "unknown"
        }
        val message = "Trozovka WX $tierLabel — v$versionName\n\n" +
            "An ultra-lightweight offline weather chart for ships on slow satellite " +
            "internet: wind, pressure, and typhoon data from NOAA, rendered entirely " +
            "on-device from a small compressed file, no map tiles or images downloaded.\n\n" +
            "By Trozovka\nhttps://github.com/Trozovka"
        AlertDialog.Builder(this)
            .setTitle("About")
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
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

    /** Matches each low-pressure center at the current hour to its
     * counterpart at the NEXT cached hour (same tile) to compute a
     * movement arrow -- this is what actually changes as the user steps
     * through forecast hours, per the operator's explicit ask, since a
     * low's direction of travel genuinely shifts hour to hour. Falls back
     * to no arrows if the next hour isn't cached for a given tile (can't
     * determine movement from a single snapshot). */
    private fun computeLowPressureTracks(tilesForHour: Map<String, WxlFile>): List<TrackedLow> {
        val nextHour = availableHours.getOrNull(currentHourIndex + 1) ?: return emptyList()
        val tilesForNextHour = repository.cachedFilesForHour(nextHour)

        val tracks = mutableListOf<TrackedLow>()
        for ((tileId, file) in tilesForHour) {
            val nextFile = tilesForNextHour[tileId] ?: continue
            val currentLows = PressureCenters.find(file.pressureGrid())
                .filter { it.type == CenterType.LOW }
                .map { file.latLonAt(it.row, it.col) }
            val nextLows = PressureCenters.find(nextFile.pressureGrid())
                .filter { it.type == CenterType.LOW }
                .map { nextFile.latLonAt(it.row, it.col) }
            tracks.addAll(LowPressureTracker.track(currentLows, nextLows))
        }
        return tracks
    }

    private fun updateHourControlsEnabled() {
        prevHourBtn.isEnabled = availableHours.isNotEmpty() && currentHourIndex > 0
        nextHourBtn.isEnabled = availableHours.isNotEmpty() && currentHourIndex < availableHours.size - 1
    }

    private fun renderCurrentHour() {
        if (availableHours.isEmpty()) {
            hourLabel.text = "No data — tap Sync now"
            mapView.setTiles(emptyList())
            mapView.setLowPressureTracks(emptyList())
            updateHourControlsEnabled()
            return
        }

        val hour = availableHours[currentHourIndex]
        // Render every tile that's been synced for this hour, across the
        // whole world, not just whichever tile the crosshair is over.
        val tilesForHour = repository.cachedFilesForHour(hour)
        mapView.setTiles(tilesForHour.values.toList())
        mapView.setLowPressureTracks(computeLowPressureTracks(tilesForHour))

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
