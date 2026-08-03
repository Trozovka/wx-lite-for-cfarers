"""Packs a NOAA GFS GRIB2 subset into the app's compact binary format.

Format (little-endian):
  header:
    4s   magic       b"WXL1"
    f    lat_min
    f    lat_max
    f    lon_min
    f    lon_max
    H    n_lat
    H    n_lon
    I    valid_time  (unix seconds)
  per grid point, row-major from (lat_min, lon_min):
    h    pressure    (hPa * 10, i.e. 10132 == 1013.2 hPa)
    b    temperature (whole degrees C)
    b    wind_u      (m/s)
    b    wind_v      (m/s)

One point is 5 bytes. A 41x81 (1-degree, 40x80 box) grid is ~16.6KB
before gzip — this is the whole reason to downsample on the backend
instead of shipping raw 0.25-degree GRIB2 to the phone.
"""

import struct
import sys
from datetime import datetime, timezone

import numpy as np
import pygrib

MAGIC = b"WXL1"


def downsample(data, step):
    return data[::step, ::step]


def pack(grib_path, out_path, step=4, bounds=None):
    """bounds: optional (lat_min, lat_max, lon_min, lon_max) in this
    project's own -180..180 longitude convention -- when given, these are
    written to the header directly instead of being derived from the
    GRIB's own lat/lon arrays.

    This matters for correctness, not just style: GRIB2 data from NOMADS
    is natively in 0..360 longitude (not -180..180, which Tiles.py, the
    Kotlin app, and every user-entered coordinate in this project use
    throughout). Confirmed as a real, live bug affecting published data --
    a Western-hemisphere tile (e.g. -120 to -60) came back with header
    lon_min/lon_max of 240/300 (the same physical span, just the wrong
    convention, an unconverted +360 offset). Worse: for a tile straddling
    the 0/360 wraparound point (e.g. -60 to 0), the raw longitude array's
    own values wrap (e.g. ...,359.75, 0.0), so naively taking min()/max()
    of that array picks up the wrap point as if it were the lowest value
    instead of recognizing it's actually the top of one contiguous arc --
    that tile's header came back as lon_min=0/lon_max=359.75, i.e.
    "the whole world," when the actual data was a normal, correctly-sized
    60-degree-wide regional grid the whole time. Passing the tile's own
    already-known, already-correct request bounds sidesteps re-deriving
    (and getting wrong) anything from the GRIB data at all.
    """
    grbs = pygrib.open(grib_path)
    prmsl = grbs.select(shortName="prmsl")[0]
    temp = grbs.select(shortName="2t")[0]
    uwind = grbs.select(shortName="10u")[0]
    vwind = grbs.select(shortName="10v")[0]

    p_data, lats, lons = prmsl.data()
    t_data, _, _ = temp.data()
    u_data, _, _ = uwind.data()
    v_data, _, _ = vwind.data()

    p_sub = downsample(p_data, step)
    t_sub = downsample(t_data, step)
    u_sub = downsample(u_data, step)
    v_sub = downsample(v_data, step)
    lat_sub = downsample(lats, step)
    lon_sub = downsample(lons, step)

    n_lat, n_lon = p_sub.shape
    valid_time = int(prmsl.validDate.replace(tzinfo=timezone.utc).timestamp())

    if bounds is not None:
        lat_min, lat_max, lon_min, lon_max = bounds
    else:
        lat_min, lat_max = float(lat_sub.min()), float(lat_sub.max())
        lon_min, lon_max = float(lon_sub.min()), float(lon_sub.max())

    with open(out_path, "wb") as f:
        f.write(MAGIC)
        f.write(
            struct.pack(
                "<ffffHHI",
                float(lat_min),
                float(lat_max),
                float(lon_min),
                float(lon_max),
                n_lat,
                n_lon,
                valid_time,
            )
        )
        for i in range(n_lat):
            for j in range(n_lon):
                pressure_hpa_x10 = int(round(p_sub[i, j] / 10.0))  # Pa -> hPa*10
                temp_c = int(round(t_sub[i, j] - 273.15))
                wind_u = int(round(u_sub[i, j]))
                wind_v = int(round(v_sub[i, j]))
                f.write(struct.pack("<hbbb", pressure_hpa_x10, temp_c, wind_u, wind_v))

    return n_lat, n_lon


def unpack(path):
    """Round-trip check: read back what pack() wrote."""
    with open(path, "rb") as f:
        magic = f.read(4)
        assert magic == MAGIC, f"bad magic: {magic}"
        lat_min, lat_max, lon_min, lon_max, n_lat, n_lon, valid_time = struct.unpack("<ffffHHI", f.read(24))
        points = []
        for _ in range(n_lat * n_lon):
            pressure_x10, temp_c, wind_u, wind_v = struct.unpack("<hbbb", f.read(5))
            points.append((pressure_x10 / 10.0, temp_c, wind_u, wind_v))
    return {
        "lat_min": lat_min,
        "lat_max": lat_max,
        "lon_min": lon_min,
        "lon_max": lon_max,
        "n_lat": n_lat,
        "n_lon": n_lon,
        "valid_time": datetime.fromtimestamp(valid_time, tz=timezone.utc),
        "points": points,
    }


if __name__ == "__main__":
    grib_path = sys.argv[1] if len(sys.argv) > 1 else "test_subset.grib2"
    out_path = sys.argv[2] if len(sys.argv) > 2 else "test_output.wxl"
    n_lat, n_lon = pack(grib_path, out_path)
    print(f"Packed {n_lat}x{n_lon} = {n_lat * n_lon} points -> {out_path}")
