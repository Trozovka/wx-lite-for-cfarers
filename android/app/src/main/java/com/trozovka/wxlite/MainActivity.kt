package com.trozovka.wxlite

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.trozovka.wxlite.data.ForecastRepository
import com.trozovka.wxlite.data.LocationStore
import com.trozovka.wxlite.data.Tiles
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Skeleton screen exercising the real offline-first flow: saved location
 * -> tile lookup -> read from local cache -> explicit sync updates the
 * cache. Everything shown here works with zero connectivity except the
 * Sync button itself, per spec.
 */
class MainActivity : Activity() {
    private lateinit var locationStore: LocationStore
    private lateinit var repository: ForecastRepository
    private lateinit var statusView: TextView

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
            setOnClickListener {
                locationStore.save(14.6, 121.0)
                refreshStatus()
            }
        }
        root.addView(setTestLocationBtn)

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

        setContentView(root)
        refreshStatus()
    }

    private fun refreshStatus() {
        val position = locationStore.get()
        val sb = StringBuilder()
        sb.append("Trozovka WX Lite\n\n")

        if (position == null) {
            sb.append("No ship location saved yet.\n")
        } else {
            val tileId = Tiles.tileForPosition(position.lat, position.lon)
            sb.append("Ship position: ${position.lat}, ${position.lon}\n")
            sb.append("Tile: ${tileId ?: "out of covered range"}\n\n")

            if (tileId != null) {
                val hours = repository.availableHours(tileId)
                val lastSync = repository.lastSyncedAtMillis()
                sb.append("Cached forecast hours: ${hours.size} (${hours.take(5)}${if (hours.size > 5) "..." else ""})\n")
                sb.append(
                    if (lastSync > 0) {
                        "Last synced: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(lastSync))}\n"
                    } else {
                        "Never synced — no offline data yet.\n"
                    },
                )
            }
        }

        statusView.text = sb.toString()
    }
}
