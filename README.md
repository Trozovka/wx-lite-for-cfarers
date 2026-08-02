# WX Lite for C/Farers

An ultra-lightweight Android weather app built for merchant ship crews on slow, expensive maritime satellite internet (Marlink and similar) and low-spec Android devices. It's a digital replacement for traditional marine weatherfax: pressure systems (H/L, isobars), wind barbs, tropical cyclone tracks, and temperature, rendered locally on-device from a small compressed data file — not streamed map tiles or weather images.

This is the free, open-source core. It shows a **1-day forecast**. A paid companion app (10-day forecast, plus voyage-planning features) depends on this core but is not part of this repo.

## Why it exists

Ships on Marlink-class connections pay per megabyte and get very little of it. Consumer weather apps assume broadband. This app is built around the opposite assumption: launch fast, show useful weather immediately from whatever's already synced, and only touch the network when the crew explicitly asks it to.

## Tech stack

- **Kotlin, native Android** (plain Views, no Compose/Material, no Google Maps/Mapbox/OSM tile streaming) — chosen over cross-platform frameworks specifically because this project's whole premise is minimizing APK size, RAM, and battery overhead.
- **Backend data pipeline**: Python, pulling NOAA's public-domain GFS model data (pressure/wind/temperature) and NHC/JTWC tropical cyclone data, packed into a compact custom binary format (`.wxl`) — see [Section 18 of PROJECT_SPEC.md](PROJECT_SPEC.md#18-data-source-decision-resolved-2026-08-03) for why NOAA over commercial weather APIs.
- **Hosting**: GitHub Actions (scheduled build) + GitHub Pages (static file hosting) — no server to run or pay for.
- **Coastlines**: Natural Earth 110m public-domain land data, packed into a compact binary format and bundled as a static app asset.

## Setup and running

### Backend (regenerates the published weather data — not required just to run the app)

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python build.py
```

Runs automatically on a schedule via `.github/workflows/build-weather-data.yml`; you only need this locally if you're changing the pipeline itself.

### Android app

Requires the Android SDK (platform 34, build-tools 34.0.0) and `ANDROID_HOME` set.

```bash
cd android
export ANDROID_HOME=~/android-sdk   # wherever your SDK lives
./gradlew test          # run the unit test suite
./gradlew assembleDebug # build app/build/outputs/apk/debug/app-debug.apk
```

Install the resulting APK on an Android device (sideload — this app is not distributed via Play Store).

## Features (free tier)

- 1-day weather forecast: wind (speed, direction, traditional wind barbs), pressure systems (H/L markers, isobars, hPa values), tropical cyclone positions.
- Temperature values overlaid on the chart.
- Two map views: zoomed-out orthographic globe and zoomed-in regional weatherfax-style chart, both rendered locally — no map tile downloads.
- Ship position: tap the globe to set it, or enter latitude/longitude manually. Saved locally.
- Forecast hour picker (Earlier/Later) showing each hour's actual valid UTC date/time.
- Fully offline after syncing — the forecast and your saved ship position are stored on-device; only the explicit "Sync now" action touches the network.

## Paid version

A private companion app, `wx-pro-for-cfarers`, extends this core with the full 10-day forecast and additional paid-only features. It is closed-source and distributed separately; this repository contains no paid-tier code.

## License

MIT — see [LICENSE](LICENSE).
