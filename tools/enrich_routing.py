#!/usr/bin/env python3
"""Enrich NV routing.db edges with OSM speed/highway/toll/ferry/surface/lanes metadata."""
from __future__ import annotations

import argparse
import re
import sqlite3
from pathlib import Path

MAXSPEED = re.compile(r"(\d+(?:\.\d+)?)")
TRUE = {"yes", "true", "1"}


def parse_int(value: str | None) -> int | None:
    if not value:
        return None
    match = MAXSPEED.search(value)
    if not match:
        return None
    number = float(match.group(1))
    if "mph" in value.lower():
        number *= 1.609344
    return int(round(number))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("pbf", type=Path)
    parser.add_argument("routing_db", type=Path)
    args = parser.parse_args()
    try:
        import osmium
    except ImportError as error:
        raise SystemExit("Install pyosmium first") from error

    db = sqlite3.connect(args.routing_db)
    columns = {row[1] for row in db.execute("PRAGMA table_info(edges)")}
    additions = {
        "speed_limit_kmh": "INTEGER",
        "highway_class": "TEXT",
        "toll": "INTEGER NOT NULL DEFAULT 0",
        "ferry": "INTEGER NOT NULL DEFAULT 0",
        "surface": "TEXT",
        "lane_count": "INTEGER",
    }
    for name, ddl in additions.items():
        if name not in columns:
            db.execute(f"ALTER TABLE edges ADD COLUMN {name} {ddl}")
    db.execute("CREATE INDEX IF NOT EXISTS edges_way_metadata_idx ON edges(way_id)")
    db.commit()

    class Handler(osmium.SimpleHandler):
        def __init__(self) -> None:
            super().__init__()
            self.rows: list[tuple] = []

        def way(self, way) -> None:
            tags = {tag.k: tag.v for tag in way.tags}
            highway = tags.get("highway")
            ferry = tags.get("route") == "ferry" or tags.get("ferry") in TRUE
            if not highway and not ferry:
                return
            speed = parse_int(tags.get("maxspeed"))
            toll = int(tags.get("toll", "").lower() in TRUE)
            surface = tags.get("surface")
            lanes = parse_int(tags.get("lanes"))
            self.rows.append((speed, highway, toll, int(ferry), surface, lanes, way.id))
            if len(self.rows) >= 10_000:
                self.flush()

        def flush(self) -> None:
            if not self.rows:
                return
            db.executemany(
                """
                UPDATE edges SET speed_limit_kmh=?, highway_class=?, toll=?, ferry=?, surface=?, lane_count=?
                WHERE way_id=?
                """,
                self.rows,
            )
            db.commit()
            self.rows.clear()

    handler = Handler()
    handler.apply_file(str(args.pbf), locations=False)
    handler.flush()
    db.execute("ANALYZE")
    db.execute("PRAGMA optimize")
    db.commit()
    count = db.execute(
        "SELECT COUNT(*) FROM edges WHERE highway_class IS NOT NULL OR ferry = 1"
    ).fetchone()[0]
    print(f"Enriched {count:,} routing edges")
    if count <= 0:
        raise SystemExit("No routing edges were enriched")
    db.close()


if __name__ == "__main__":
    main()
