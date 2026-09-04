#!/usr/bin/env python3
"""Build NV's deterministic places database from GeoJSON or GeoJSON Text Sequences."""

from __future__ import annotations

import argparse
import json
import re
import sqlite3
import unicodedata
from pathlib import Path
from typing import Iterable, Iterator


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
CREATE INDEX places_location_idx ON places(latitude, longitude);
"""

OSM_ID = re.compile(r"^(node|way|relation)[/:](\d+)$")
OSM_SHORT_ID = re.compile(r"^([nwr])(\d+)$")
OSM_SHORT_TYPES = {"n": "node", "w": "way", "r": "relation"}


def normalize(value: str) -> str:
    value = unicodedata.normalize("NFKC", value).strip().lower()
    return " ".join(
        value.replace("ي", "ی").replace("ك", "ک").replace("\u200c", "").split()
    )


def iter_coordinate_pairs(value: object) -> Iterator[tuple[float, float]]:
    if not isinstance(value, list) or not value:
        return
    if len(value) >= 2 and all(isinstance(item, (int, float)) for item in value[:2]):
        yield float(value[0]), float(value[1])
        return
    for child in value:
        yield from iter_coordinate_pairs(child)


def representative_coordinate(geometry: dict) -> tuple[float, float] | None:
    pairs = list(iter_coordinate_pairs(geometry.get("coordinates")))
    if not pairs:
        return None
    longitude = sum(point[0] for point in pairs) / len(pairs)
    latitude = sum(point[1] for point in pairs) / len(pairs)
    if not (-90 <= latitude <= 90 and -180 <= longitude <= 180):
        return None
    return latitude, longitude


def category(tags: dict) -> str:
    for key in (
        "tourism", "historic", "place", "amenity", "natural", "shop", "leisure",
        "office", "craft", "man_made",
    ):
        if tags.get(key):
            return key + ":" + str(tags[key])
    return "named"


def parse_osm_id(feature: dict) -> tuple[str, int] | None:
    properties = feature.get("properties") or {}
    raw_id = str(feature.get("id") or properties.get("@id") or "")
    match = OSM_ID.match(raw_id)
    if match:
        return match.group(1), int(match.group(2))
    short = OSM_SHORT_ID.match(raw_id)
    if short:
        return OSM_SHORT_TYPES[short.group(1)], int(short.group(2))
    raw_type = properties.get("@type")
    numeric_id = properties.get("id") or properties.get("@osm_id")
    if raw_type in {"node", "way", "relation"} and str(numeric_id).isdigit():
        return str(raw_type), int(numeric_id)
    return None


def feature_row(feature: dict) -> tuple[str, int, str, str, float, float, str] | None:
    properties = feature.get("properties") or {}
    name = properties.get("name:fa") or properties.get("name")
    coordinate = representative_coordinate(feature.get("geometry") or {})
    osm_id = parse_osm_id(feature)
    if not name or coordinate is None or osm_id is None:
        return None
    latitude, longitude = coordinate
    osm_type, numeric_id = osm_id
    clean_name = str(name).strip()
    if not clean_name:
        return None
    return (
        osm_type, numeric_id, clean_name, normalize(clean_name), latitude, longitude,
        category(properties),
    )


def iter_features(path: Path) -> Iterable[dict]:
    with path.open("r", encoding="utf-8-sig") as source:
        first = source.read(1)
        source.seek(0)
        if first == "{":
            document = json.load(source)
            if document.get("type") == "FeatureCollection":
                yield from document.get("features", [])
            elif document.get("type") == "Feature":
                yield document
            return
        for line in source:
            payload = line.lstrip("\x1e").strip()
            if payload:
                feature = json.loads(payload)
                if feature.get("type") == "Feature":
                    yield feature


def build_database(source: Path, output: Path) -> int:
    if output.exists():
        output.unlink()
    with sqlite3.connect(output) as database:
        database.executescript(SCHEMA)
        database.execute(
            """
            CREATE TEMP TABLE raw_places (
              osm_type TEXT NOT NULL, osm_id INTEGER NOT NULL, name TEXT NOT NULL,
              normalized_name TEXT NOT NULL, latitude REAL NOT NULL,
              longitude REAL NOT NULL, category TEXT NOT NULL,
              PRIMARY KEY (osm_type, osm_id)
            ) WITHOUT ROWID
            """
        )
        batch: list[tuple[str, int, str, str, float, float, str]] = []
        for feature in iter_features(source):
            row = feature_row(feature)
            if row is not None:
                batch.append(row)
            if len(batch) >= 10_000:
                database.executemany(
                    "INSERT OR REPLACE INTO raw_places VALUES (?, ?, ?, ?, ?, ?, ?)", batch
                )
                batch.clear()
        if batch:
            database.executemany(
                "INSERT OR REPLACE INTO raw_places VALUES (?, ?, ?, ?, ?, ?, ?)", batch
            )

        ordered = database.execute(
            """
            SELECT osm_type, osm_id, name, normalized_name, latitude, longitude, category
            FROM raw_places ORDER BY normalized_name, osm_type, osm_id
            """
        )
        insert_batch = []
        count = 0
        for count, row in enumerate(ordered, start=1):
            insert_batch.append((count, *row))
            if len(insert_batch) >= 10_000:
                database.executemany(
                    "INSERT INTO places VALUES (?, ?, ?, ?, ?, ?, ?, ?)", insert_batch
                )
                insert_batch.clear()
        if insert_batch:
            database.executemany(
                "INSERT INTO places VALUES (?, ?, ?, ?, ?, ?, ?, ?)", insert_batch
            )
        database.execute("ANALYZE")
        database.execute("PRAGMA optimize")
        database.commit()
    return count


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("geojson", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    count = build_database(args.geojson, args.output)
    print(f"Created {args.output} with {count} named places")


if __name__ == "__main__":
    main()
