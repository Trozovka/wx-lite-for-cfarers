"""Orchestrates a full data build: every tile x every scheduled forecast
hour, plus cyclone data, into output/ with a manifest the app can read
to discover what's available.

Run for real (all 24 tiles x 27 hours = 648 fetches) inside GitHub
Actions on a schedule — not meant to be run at full scale from a dev
machine against NOMADS repeatedly. --tiles/--hours let you run a small
slice for testing.
"""

import argparse
import json
import os
import sys
import time
from datetime import datetime, timezone

from cyclones import fetch_nhc
from fetch import fetch
from pack import pack
from schedule import forecast_hours, resolution_step
from tiles import all_tiles

OUTPUT_DIR = "output"
REQUEST_DELAY_SECONDS = 2  # be a reasonable citizen of NOMADS' free service


def build(tile_filter=None, hour_filter=None, output_dir=OUTPUT_DIR):
    os.makedirs(output_dir, exist_ok=True)
    tiles = all_tiles()
    if tile_filter:
        tiles = [t for t in tiles if t["id"] in tile_filter]
    hours = forecast_hours()
    if hour_filter is not None:
        hours = [h for h in hours if h in hour_filter]

    manifest = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "run_time": None,
        "tiles": {},
        "hours": hours,
    }

    total = len(tiles) * len(hours)
    done = 0
    for tile in tiles:
        tile_dir = os.path.join(output_dir, tile["id"])
        os.makedirs(tile_dir, exist_ok=True)
        manifest["tiles"][tile["id"]] = {
            "bounds": tile,
            "files": {},
        }
        for hour in hours:
            done += 1
            grib_path = os.path.join(tile_dir, f"_tmp_f{hour:03d}.grib2")
            out_name = f"f{hour:03d}.wxl"
            out_path = os.path.join(tile_dir, out_name)
            print(f"[{done}/{total}] tile={tile['id']} hour={hour}", file=sys.stderr)
            try:
                run_time = fetch(
                    grib_path,
                    forecast_hour=hour,
                    bbox=(tile["bottom_lat"], tile["top_lat"], tile["left_lon"], tile["right_lon"]),
                )
                # Pass the tile's own already-known bounds through to pack()
                # instead of letting it re-derive them from the GRIB's raw
                # (0..360-convention, wraparound-unsafe) longitude array --
                # see pack.py's own docstring for why that was a real bug.
                pack(
                    grib_path,
                    out_path,
                    step=resolution_step(hour),
                    bounds=(tile["bottom_lat"], tile["top_lat"], tile["left_lon"], tile["right_lon"]),
                )
                manifest["run_time"] = run_time.isoformat()
                manifest["tiles"][tile["id"]]["files"][str(hour)] = out_name
            finally:
                if os.path.exists(grib_path):
                    os.remove(grib_path)
            time.sleep(REQUEST_DELAY_SECONDS)

    storms_path = os.path.join(output_dir, "storms.json")
    try:
        fetch_nhc(storms_path)
        manifest["storms_file"] = "storms.json"
    except Exception as e:
        print(f"WARNING: cyclone fetch failed: {e}", file=sys.stderr)
        manifest["storms_file"] = None

    manifest_path = os.path.join(output_dir, "manifest.json")
    with open(manifest_path, "w") as f:
        json.dump(manifest, f, indent=2)

    return manifest


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--tiles", nargs="*", help="tile IDs to build (default: all)")
    ap.add_argument("--hours", nargs="*", type=int, help="forecast hours to build (default: all)")
    ap.add_argument("--output", default=OUTPUT_DIR)
    args = ap.parse_args()

    manifest = build(tile_filter=args.tiles, hour_filter=args.hours, output_dir=args.output)
    print(f"Built {sum(len(t['files']) for t in manifest['tiles'].values())} files", file=sys.stderr)
