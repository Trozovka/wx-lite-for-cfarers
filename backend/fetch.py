"""Fetches a subregion GRIB2 subset from NOAA's NOMADS grib-filter service.

Requesting just the fields/region we need (rather than a full global GRIB2)
keeps the download small at the source — the grib-filter service does the
first round of bandwidth reduction before pack.py does the second.
"""

import argparse
import sys
import time
from datetime import datetime, timedelta, timezone

import requests

NOMADS_BASE = "https://nomads.ncep.noaa.gov/cgi-bin/filter_gfs_0p25.pl"

# NOMADS publishes a run's files incrementally over its ~3.5-4.5h availability
# window; a later forecast hour can still 404 (or return a small HTML error
# page) minutes after an earlier hour of the same run already succeeded. Most
# of these clear up within a couple minutes, so retry before giving up.
MAX_ATTEMPTS = 4
RETRY_DELAY_SECONDS = 30

# Pressure/wind/temp fields this app needs; see pack.py for how they're used.
VARS = ["PRMSL", "TMP", "UGRD", "VGRD"]
LEVELS = ["mean_sea_level", "2_m_above_ground", "10_m_above_ground"]


def latest_available_run(now=None):
    """GFS runs at 00/06/12/18Z; each becomes available ~3-5h after the run
    time, so the most recently *started* run isn't necessarily ready yet."""
    now = now or datetime.now(timezone.utc)
    candidate = now - timedelta(hours=4)
    run_hour = (candidate.hour // 6) * 6
    run_date = candidate.replace(hour=run_hour, minute=0, second=0, microsecond=0)
    return run_date


def fetch(out_path, forecast_hour=0, bbox=(0, 40, 100, 180), run_time=None):
    """bbox = (bottom_lat, top_lat, left_lon, right_lon)"""
    run_time = run_time or latest_available_run()
    bottom_lat, top_lat, left_lon, right_lon = bbox

    params = {
        "file": f"gfs.t{run_time.hour:02d}z.pgrb2.0p25.f{forecast_hour:03d}",
        "subregion": "",
        "leftlon": left_lon,
        "rightlon": right_lon,
        "toplat": top_lat,
        "bottomlat": bottom_lat,
        "dir": f"/gfs.{run_time.strftime('%Y%m%d')}/{run_time.hour:02d}/atmos",
    }
    for var in VARS:
        params[f"var_{var}"] = "on"
    for level in LEVELS:
        params[f"lev_{level}"] = "on"

    last_error = None
    for attempt in range(1, MAX_ATTEMPTS + 1):
        try:
            resp = requests.get(NOMADS_BASE, params=params, timeout=60)
            resp.raise_for_status()
            if len(resp.content) < 1000:
                # NOMADS returns a small HTML error page (not a real GRIB2)
                # for a run/hour that isn't published yet — treat the same
                # as a transient failure rather than silently packing garbage.
                raise RuntimeError(f"Unexpectedly small response ({len(resp.content)} bytes) — run not ready? {resp.url}")
            with open(out_path, "wb") as f:
                f.write(resp.content)
            return run_time
        except (requests.exceptions.RequestException, RuntimeError) as e:
            last_error = e
            if attempt < MAX_ATTEMPTS:
                print(
                    f"  fetch attempt {attempt}/{MAX_ATTEMPTS} failed ({e}); retrying in {RETRY_DELAY_SECONDS}s",
                    file=sys.stderr,
                )
                time.sleep(RETRY_DELAY_SECONDS)
    raise last_error


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("out_path")
    ap.add_argument("--forecast-hour", type=int, default=0)
    args = ap.parse_args()
    run_time = fetch(args.out_path, forecast_hour=args.forecast_hour)
    print(f"Fetched GFS {run_time.strftime('%Y-%m-%d %HZ')} f{args.forecast_hour:03d} -> {args.out_path}", file=sys.stderr)
