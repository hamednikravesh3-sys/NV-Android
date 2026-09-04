#!/usr/bin/env python3
"""Build the deterministic NV places database from an Osmium GeoJSON export."""

from __future__ import annotations

import argparse
import json
import sqlite3
import unicodedata
from pathlib import Path


SCHEMA = """
CREATE TABLE places (
  code INTEGER PRIMARY KEY,
  osm_type TEXT NOT NULL,
  osm_id INTEGER NOT NULL,
  name TEXT NOT NULL,
  normalized_name TEXT NOT NULL,
  latitude REAL NOT NULL,
  longitude REAL NOT NULL,
  category TEXT NOT NULL,
  UNIQUE (osm_type, osm_id)
);
CREATE INDEX places_normalized_name_idx ON places(normalized_name);
"""


def normalize(value: str) -> str:
    value = unicodedata.normalize("NFKC", value).strip().lower()
    return " ".join(value.replace("ي", "ی").replace("ك", "ک").replace("\u200c", "").split())


def representative_coordinate(geometry: dict) -> tuple[float, float] | None:
    kind = geometry.get("type")
    coordinates = geometry.get("coordinates")
    if kind == "Point" and coordinates:
        return float(coordinates[1]), float(coordinates[0])
    if kind == "LineString" and coordinates:
        middle = coordinates[len(coordinates) // 2]
        return float(middle[1]), float(middle[0])
    if kind == "Polygon" and coordinates and coordinates[0]:
        ring = coordinates[0]
        longitude = sum(point[0] for point in ring) / len(ring)
        latitude = sum(point[1] for point in ring) / len(ring)
        return float(latitude), float(longitude)
    return None


def category(tags: dict) -> str:
    for key in ("tourism", "place", "amenity", "natural", "historic", "shop"):
        if tags.get(key):
            return key + ":" + str(tags[key])
    return "named"


def load_features(path: Path) -> list[tuple[str, int, str, str, float, float, str]]:
    document = json.loads(path.read_text(encoding="utf-8"))
    rows = []
    for feature in document.get("features", []):
        properties = feature.get("properties", {})
        name = properties.get("name") or properties.get("name:fa")
        coordinate = representative_coordinate(feature.get("geometry", {}))
        raw_id = feature.get("id", "")
        if not name or not coordinate or "/" not in raw_id:
            continue
        osm_type, osm_id = raw_id.split("/", 1)
        latitude, longitude = coordinate
        rows.append(
            (
                osm_type,
                int(osm_id),
                str(name),
                normalize(str(name)),
                latitude,
                longitude,
                category(properties),
            )
        )
    return sorted(rows, key=lambda row: (row[3], row[0], row[1]))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("geojson", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    rows = load_features(args.geojson)
    if args.output.exists():
        args.output.unlink()
    with sqlite3.connect(args.output) as database:
        database.executescript(SCHEMA)
        database.executemany(
            """
            INSERT INTO places(
              code, osm_type, osm_id, name, normalized_name,
              latitude, longitude, category
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            ((code, *row) for code, row in enumerate(rows, start=1)),
        )
        database.execute("PRAGMA optimize")
    print("Created " + str(args.output) + " with " + str(len(rows)) + " named places")


if __name__ == "__main__":
    main()
