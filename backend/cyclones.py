"""Fetches active tropical cyclone data.

NHC's CurrentStorms.json is already tiny (~5KB) and structured — no
custom packing needed, unlike the GRIB2 pressure/wind/temp data.

NHC covers the Atlantic and Eastern/Central Pacific. Western Pacific
typhoons (the JTWC's basin) are NOT yet covered here — a JTWC endpoint
needs verifying from within GitHub Actions, since it didn't resolve from
this dev sandbox and the exact URL wasn't confirmed. Tracked as a known
gap, not silently dropped.
"""

import requests

NHC_URL = "https://www.nhc.noaa.gov/CurrentStorms.json"


def fetch_nhc(out_path):
    resp = requests.get(NHC_URL, timeout=30)
    resp.raise_for_status()
    with open(out_path, "wb") as f:
        f.write(resp.content)
    return resp.json()


if __name__ == "__main__":
    import sys

    out_path = sys.argv[1] if len(sys.argv) > 1 else "storms.json"
    data = fetch_nhc(out_path)
    storms = data.get("activeStorms", [])
    print(f"{len(storms)} active storm(s) -> {out_path}")
    for s in storms:
        print(f"  {s['name']} ({s['classification']}) at {s['latitude']}, {s['longitude']}, {s['pressure']} hPa")
