package com.trozovka.wxlite.data

import android.content.Context
import android.util.Log
import com.trozovka.wxlite.chart.Storm
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import org.json.JSONObject

/**
 * Offline-first: every read goes to local disk (app-private internal
 * storage, not cacheDir — cacheDir can be cleared by the OS under storage
 * pressure, which would violate "store the complete forecast offline").
 * Network is only ever touched by sync(), an explicit action — the app
 * must be fully usable from whatever's already on disk with zero
 * connectivity, per spec.
 *
 * [tier] bounds both what's read AND what's fetched — this (public,
 * free-tier) app never downloads paid-tier hours it's not licensed to
 * show, not just hides them in the UI.
 */
class ForecastRepository(
    private val context: Context,
    private val tier: ForecastTier = ForecastTier.FREE,
) {
    private val baseDir: File
        get() = File(context.filesDir, "weather").apply { mkdirs() }

    private val executor = Executors.newSingleThreadExecutor()

    // ---------- offline reads ----------

    fun cachedManifest(): JSONObject? {
        val file = File(baseDir, "manifest.json")
        if (!file.exists()) return null
        return runCatching { JSONObject(file.readText()) }.getOrNull()
    }

    fun cachedFile(tileId: String, hour: Int): WxlFile? {
        val file = tileFilePath(tileId, hour)
        if (!file.exists()) return null
        return runCatching { WxlFile.parse(file.readBytes()) }.getOrNull()
    }

    fun cachedStorms(): JSONObject? {
        val file = File(baseDir, "storms.json")
        if (!file.exists()) return null
        return runCatching { JSONObject(file.readText()) }.getOrNull()
    }

    /** Parsed into plain Storm objects — org.json itself can't be unit
     * tested outside Android (confirmed empirically, not assumed), so this
     * parsing step is intentionally the thin, untested part; Storms.
     * withinBounds is where the actual logic lives, and that is tested. */
    fun cachedStormsList(): List<Storm> {
        val json = cachedStorms() ?: return emptyList()
        val active = json.optJSONArray("activeStorms") ?: return emptyList()
        val result = ArrayList<Storm>(active.length())
        for (i in 0 until active.length()) {
            val s = active.optJSONObject(i) ?: continue
            result.add(
                Storm(
                    id = s.optString("id"),
                    name = s.optString("name"),
                    classification = s.optString("classification"),
                    pressureHpa = s.optString("pressure").toIntOrNull(),
                    lat = s.optDouble("latitudeNumeric"),
                    lon = s.optDouble("longitudeNumeric"),
                ),
            )
        }
        return result
    }

    fun lastSyncedAtMillis(): Long {
        val file = File(baseDir, "manifest.json")
        return if (file.exists()) file.lastModified() else 0L
    }

    /** Every tile directory currently on disk (i.e. synced at least once). */
    fun cachedTileIds(): List<String> =
        baseDir.listFiles { file -> file.isDirectory }?.map { it.name }?.sorted() ?: emptyList()

    /** All cached tiles that have data for [hour], keyed by tile ID. Lets
     * the map stitch together every tile that's actually been synced,
     * instead of only ever showing the single tile tied to whatever
     * lat/lon is currently entered. */
    fun cachedFilesForHour(hour: Int): Map<String, WxlFile> {
        val result = LinkedHashMap<String, WxlFile>()
        for (tileId in cachedTileIds()) {
            cachedFile(tileId, hour)?.let { result[tileId] = it }
        }
        return result
    }

    /** Every forecast hour cached for ANY synced tile, unioned and sorted --
     * the time slider browses the whole world's data, not just the tile
     * containing the ship's own saved position, so it shouldn't require a
     * location to be set at all. Every tile is synced under the same tier
     * limit already, so this can't leak paid-tier hours either way. */
    fun availableHoursAnyTile(): List<Int> =
        cachedTileIds().flatMap { availableHours(it) }.distinct().sorted()

    /** Which forecast hours are actually on disk for this tile right now. */
    fun availableHours(tileId: String): List<Int> {
        val manifest = cachedManifest() ?: return emptyList()
        val tile = manifest.optJSONObject("tiles")?.optJSONObject(tileId) ?: return emptyList()
        val files = tile.optJSONObject("files") ?: return emptyList()
        return files.keys().asSequence().mapNotNull { it.toIntOrNull() }.filter {
            tileFilePath(tileId, it).exists()
        }.sorted().toList().filterByTier(tier)
    }

    private fun tileFilePath(tileId: String, hour: Int): File =
        File(baseDir, "$tileId/f${"%03d".format(hour)}.wxl")

    // ---------- sync (network — the only place this class touches it) ----------

    sealed class SyncResult {
        data class Success(val filesDownloaded: Int) : SyncResult()
        data class Failure(val message: String) : SyncResult()
    }

    /** Runs on a background thread; callback fires back on that same
     * background thread — caller is responsible for posting to the UI
     * thread if it touches views. */
    fun sync(tileId: String, callback: (SyncResult) -> Unit) {
        executor.execute {
            try {
                val result = syncBlocking(tileId)
                callback(result)
            } catch (e: Exception) {
                Log.w(TAG, "Sync failed", e)
                callback(SyncResult.Failure(e.message ?: "Unknown error"))
            }
        }
    }

    private fun syncBlocking(tileId: String): SyncResult {
        val manifestBytes = downloadBytes("$BASE_URL/manifest.json")
        val manifest = JSONObject(String(manifestBytes))
        File(baseDir, "manifest.json").writeBytes(manifestBytes)

        val tile = manifest.optJSONObject("tiles")?.optJSONObject(tileId)
            ?: return SyncResult.Failure("Tile $tileId not in published manifest")
        val files = tile.optJSONObject("files") ?: return SyncResult.Failure("No files listed for $tileId")

        val tileDir = File(baseDir, tileId).apply { mkdirs() }
        var downloaded = 0
        val keys = files.keys()
        while (keys.hasNext()) {
            val hourKey = keys.next()
            val hour = hourKey.toIntOrNull()
            if (hour == null || hour > tier.maxHour) continue // never fetch what this tier isn't licensed to show
            val fileName = files.getString(hourKey)
            val bytes = downloadBytes("$BASE_URL/$tileId/$fileName")
            File(tileDir, fileName).writeBytes(bytes)
            downloaded++
        }

        runCatching {
            val storms = downloadBytes("$BASE_URL/storms.json")
            File(baseDir, "storms.json").writeBytes(storms)
        }

        return SyncResult.Success(downloaded)
    }

    private fun downloadBytes(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode} for $url" }
            return connection.inputStream.readBytes()
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "ForecastRepository"
        private const val BASE_URL = "https://trozovka.github.io/wx-lite-for-cfarers"
    }
}
