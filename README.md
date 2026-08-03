# WX Lite for C/Farers

An ultra-lightweight Android weather app built for merchant ship crews on slow, expensive maritime satellite internet (Marlink and similar) and low-spec Android devices. It's a digital replacement for traditional marine weatherfax: wind (Beaufort force 5 and above), pressure systems (H/L, isobars), and tropical cyclone tracks, rendered locally on-device from a small compressed data file — not streamed map tiles or weather images.

This is the free, open-source core. It shows a **1-day forecast**. A paid companion app (10-day forecast) depends on this core but is not part of this repo.

## Why it exists

Ships on Marlink-class connections pay per megabyte and get very little of it. Consumer weather apps assume broadband. This app is built around the opposite assumption: launch fast, show useful weather immediately from whatever's already synced, and only touch the network when the crew explicitly asks it to.

## Tech stack

- **Kotlin, native Android** (plain Views, no Compose/Material, no Google Maps/Mapbox/OSM tile streaming) — chosen over cross-platform frameworks specifically because this project's whole premise is minimizing APK size, RAM, and battery overhead. Split into a `:core` library module (all shared logic and the app screen itself) and a thin `:app` module, so the private paid app can depend on `:core` directly instead of duplicating it.
- **Backend data pipeline**: Python, pulling NOAA's public-domain GFS model data (pressure/wind/temperature) and NHC/JTWC tropical cyclone data, packed into a compact custom binary format (`.wxl`) — see [Section 18 of PROJECT_SPEC.md](PROJECT_SPEC.md#18-data-source-decision-resolved-2026-08-03) for why NOAA over commercial weather APIs.
- **Hosting**: GitHub Actions (scheduled build) + GitHub Pages (static file hosting) — no server to run or pay for.
- **Coastlines**: Natural Earth 110m public-domain land data, packed into a compact binary format and bundled as a static app asset.

Built independently, not a fork — no closely-matching open-source project was found to build on (see `PROJECT_SPEC.md` Section 5).

## Download

The easiest way to get the app: grab the APK from [GitHub Releases](https://github.com/Trozovka/wx-lite-for-cfarers/releases) or [Gumroad](https://trozovka.gumroad.com/l/wx-lite-for-seafarers) (free) and sideload it (this app is not distributed via Play Store — see Setup below for why, and for how to allow the install). Building from source (below) is the alternative if you'd rather compile it yourself.

## Setup and running

### Android app

Requires the Android SDK (platform 34, build-tools 34.0.0).

If you don't already have the SDK installed, the simplest path is [Android Studio](https://developer.android.com/studio) (its SDK Manager installs everything below through a UI). For a headless/CLI-only setup instead (verified working as of this writing; check [the official command-line tools page](https://developer.android.com/studio#command-line-tools-only) for the current download link if this one has aged out):

```bash
# Download and unpack the command-line tools (Linux example; see
# https://developer.android.com/studio#command-line-tools-only for other platforms)
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
curl -o cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip cmdline-tools.zip && mv cmdline-tools latest
export ANDROID_HOME=~/android-sdk
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

Then, from the repo root:

```bash
cd android
export ANDROID_HOME=~/android-sdk   # wherever your SDK lives
./gradlew test          # run the unit test suite
./gradlew assembleDebug # build app/build/outputs/apk/debug/app-debug.apk
```

Install the resulting APK on an Android device (sideload — this app is not distributed via Play Store).

### Backend (regenerates the published weather data — not required just to run the app)

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python build.py
```

Runs automatically on a schedule via `.github/workflows/build-weather-data.yml`; you only need this locally if you're changing the pipeline itself.

## Features (free tier)

- 1-day weather forecast: wind (speed, direction, traditional wind barbs, Beaufort force 5 and above only), pressure systems (H/L markers with central pressure, isobars every 4 hPa), tropical cyclone positions, low-pressure movement arrows.
- Full-screen pan/zoom map (no map tile downloads) with a 1-degree lat/lon reference grid and a fixed-center crosshair that reads out the exact lat/lon under it.
- Passage-plan area: up to 10 waypoints (degrees-minutes, the maritime convention) connected in order to outline the area relevant to a voyage — with per-point and clear-all controls.
- Forecast hour picker (Earlier/Later) showing each hour's actual valid UTC date/time.
- Fully offline after syncing — the forecast and passage-plan area are stored on-device; only the explicit "Sync now" action touches the network. A "Clear cache" option is available if you want to free up storage or force a completely fresh sync.

## Screenshot

_A screenshot of the main chart view belongs here — not yet added._

## Paid version

A private companion app, `wx-pro-for-cfarers`, extends this core with the full 10-day forecast — [WX Pro for Seafarers on Gumroad](https://trozovka.gumroad.com/l/wx-pro-for-seafarers). It is closed-source and distributed separately; this repository contains no paid-tier code.

## License

MIT — see [LICENSE](LICENSE) for the full text.

```
Copyright (c) 2026 Trozovka
Original Author: Trozovka
```

All derivative works must retain this notice and preserve attribution to the original author.
