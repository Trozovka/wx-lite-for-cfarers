"""Fixed regional tile grid.

Ships travel established routes, not the whole planet at once — global
coverage was tested and found far too large for satellite bandwidth
(3.3MB+/sync at global extent vs ~150KB for one relevant tile). Instead
the backend pre-generates every tile; the app downloads only the tile(s)
covering the ship's saved position.

Covers -60 to 60 latitude (virtually all merchant shipping lanes; polar
routes are a tiny fraction of traffic and are out of scope for now).
30-degree lat bands x 60-degree lon bands = 24 tiles.
"""

TILE_LAT_SIZE = 30
TILE_LON_SIZE = 60
LAT_RANGE = (-60, 60)
LON_RANGE = (-180, 180)


def all_tiles():
    tiles = []
    lat = LAT_RANGE[0]
    while lat < LAT_RANGE[1]:
        lon = LON_RANGE[0]
        while lon < LON_RANGE[1]:
            tile_id = f"lat{lat}_lon{lon}"
            tiles.append(
                {
                    "id": tile_id,
                    "bottom_lat": lat,
                    "top_lat": lat + TILE_LAT_SIZE,
                    "left_lon": lon,
                    "right_lon": lon + TILE_LON_SIZE,
                }
            )
            lon += TILE_LON_SIZE
        lat += TILE_LAT_SIZE
    return tiles


def tile_for_position(lat, lon):
    """Which tile covers a given ship position — used by the app, but kept
    here so both sides agree on the exact same grid math."""
    lon = ((lon + 180) % 360) - 180  # normalize to -180..180
    if not (LAT_RANGE[0] <= lat < LAT_RANGE[1]):
        return None
    tile_lat = LAT_RANGE[0] + ((lat - LAT_RANGE[0]) // TILE_LAT_SIZE) * TILE_LAT_SIZE
    tile_lon = LON_RANGE[0] + ((lon - LON_RANGE[0]) // TILE_LON_SIZE) * TILE_LON_SIZE
    return f"lat{int(tile_lat)}_lon{int(tile_lon)}"


if __name__ == "__main__":
    tiles = all_tiles()
    print(f"{len(tiles)} tiles")
    for t in tiles[:3]:
        print(t)
