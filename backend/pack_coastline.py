"""Packs Natural Earth's public-domain 110m land polygons into a compact
binary format, bundled as a static Android asset (not fetched over the
network — it never changes, so there's no reason to spend bandwidth on it
at runtime).

Format (little-endian):
  4s  magic        b"COST"
  I   num_polygons
  per polygon:
    H   num_points
    per point:
      h   lat_x100   (degrees * 100, i.e. 1456 == 14.56)
      h   lon_x100
"""

import json
import struct
import sys

MAGIC = b"COST"


def pack(geojson_path, out_path):
    with open(geojson_path) as f:
        data = json.load(f)

    polygons = []
    for feature in data["features"]:
        geom = feature["geometry"]
        rings = geom["coordinates"] if geom["type"] == "Polygon" else [r for poly in geom["coordinates"] for r in poly]
        for ring in rings:
            polygons.append(ring)

    with open(out_path, "wb") as f:
        f.write(MAGIC)
        f.write(struct.pack("<I", len(polygons)))
        for ring in polygons:
            f.write(struct.pack("<H", len(ring)))
            for lon, lat in ring:  # GeoJSON coordinates are [lon, lat]
                lat_x100 = int(round(lat * 100))
                lon_x100 = int(round(lon * 100))
                f.write(struct.pack("<hh", lat_x100, lon_x100))

    return len(polygons)


if __name__ == "__main__":
    geojson_path = sys.argv[1] if len(sys.argv) > 1 else "/tmp/ne_110m_land.geojson"
    out_path = sys.argv[2] if len(sys.argv) > 2 else "coastline.bin"
    n = pack(geojson_path, out_path)
    print(f"Packed {n} polygons -> {out_path}", file=sys.stderr)
