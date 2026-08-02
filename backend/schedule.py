"""Tiered forecast-hour schedule: finer detail near-term, coarser further
out — matches how forecast confidence actually degrades with lead time,
and keeps the far-out tail cheap in bandwidth."""


def forecast_hours():
    near = list(range(0, 73, 6))  # days 1-3, every 6h: 0,6,...,72 (13 steps)
    far = list(range(84, 241, 12))  # days 4-10, every 12h: 84,...,240 (14 steps)
    return near + far


def resolution_step(forecast_hour):
    """pack.py's downsample step: 4 -> ~1 degree, 8 -> ~2 degree."""
    return 4 if forecast_hour <= 72 else 8


if __name__ == "__main__":
    hours = forecast_hours()
    print(f"{len(hours)} forecast hours: {hours}")
