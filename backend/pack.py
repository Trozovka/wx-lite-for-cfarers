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


def pack(grib_path, out_path, step=4):
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

    with open(out_path, "wb") as f:
        f.write(MAGIC)
        f.write(
            struct.pack(
                "<ffffHHI",
                float(lat_sub.min()),
                float(lat_sub.max()),
                float(lon_sub.min()),
                float(lon_sub.max()),
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
