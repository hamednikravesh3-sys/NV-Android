#!/usr/bin/env python3
"""Compile an OSM PBF into NV's directed offline-routing SQLite graph."""

from __future__ import annotations

import argparse
import math
import re
import sqlite3
import struct
import sys
from pathlib import Path
from typing import Iterable


DRIVABLE_HIGHWAYS = {
    "motorway", "motorway_link", "trunk", "trunk_link", "primary", "primary_link",
    "secondary", "secondary_link", "tertiary", "tertiary_link", "unclassified",
    "residential", "living_street", "service", "road", "track",
}
DEFAULT_SPEED_KMH = {
    "motorway": 100, "motorway_link": 50, "trunk": 90, "trunk_link": 45,
    "primary": 80, "primary_link": 40, "secondary": 70, "secondary_link": 35,
    "tertiary": 60, "tertiary_link": 30, "unclassified": 50, "residential": 30,
    "living_street": 15, "service": 20, "road": 40, "track": 15,
}
DENIED_ACCESS = {"no", "private", "agricultural", "forestry"}
ONEWAY_TRUE = {"yes", "true", "1"}
ONEWAY_REVERSE = {"-1", "reverse"}
MAXSPEED_NUMBER = re.compile(r"(\d+(?:\.\d+)?)")
EARTH_RADIUS_METERS = 6_371_000.0


OUTPUT_SCHEMA = """
PRAGMA journal_mode=OFF;
PRAGMA synchronous=OFF;
PRAGMA temp_store=MEMORY;
CREATE TABLE nodes (id INTEGER PRIMARY KEY, latitude REAL NOT NULL, longitude REAL NOT NULL);
CREATE VIRTUAL TABLE nodes_index USING rtree(
  id, min_latitude, max_latitude, min_longitude, max_longitude
);
CREATE TABLE edges (
  id INTEGER PRIMARY KEY, from_node INTEGER NOT NULL, to_node INTEGER NOT NULL,
  distance_m REAL NOT NULL, travel_seconds REAL NOT NULL, road_name TEXT,
  way_id INTEGER NOT NULL
);
CREATE TABLE turn_restrictions (
  via_node INTEGER NOT NULL, from_edge INTEGER NOT NULL, to_edge INTEGER NOT NULL,
  PRIMARY KEY (via_node, from_edge, to_edge)
) WITHOUT ROWID;
"""


def is_drivable(tags: dict[str, str]) -> bool:
    highway = tags.get("highway", "")
    if highway not in DRIVABLE_HIGHWAYS:
        return False
    return not any(
        tags.get(key, "").lower() in DENIED_ACCESS
        for key in ("access", "vehicle", "motor_vehicle", "motorcar")
    )


def direction(tags: dict[str, str]) -> int:
    value = tags.get("oneway", "").lower()
    if value in ONEWAY_TRUE or tags.get("junction") == "roundabout":
        return 1
    if value in ONEWAY_REVERSE:
        return -1
    return 0


def speed_kmh(tags: dict[str, str]) -> float:
    value = tags.get("maxspeed", "")
    match = MAXSPEED_NUMBER.search(value)
    if match:
        speed = float(match.group(1))
        if "mph" in value.lower():
            speed *= 1.609344
        return min(max(speed, 5.0), 140.0)
    return float(DEFAULT_SPEED_KMH[tags["highway"]])


def haversine(a: tuple[float, float], b: tuple[float, float]) -> float:
    lat1, lon1 = map(math.radians, a)
    lat2, lon2 = map(math.radians, b)
    dlat = lat2 - lat1
    dlon = lon2 - lon1
    h = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 2 * EARTH_RADIUS_METERS * math.asin(math.sqrt(h))


def encode_refs(refs: Iterable[int]) -> bytes:
    values = tuple(refs)
    return struct.pack(f"<{len(values)}q", *values)


def decode_refs(value: bytes) -> tuple[int, ...]:
    return struct.unpack(f"<{len(value) // 8}q", value)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("pbf", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--work-db", type=Path)
    args = parser.parse_args()
    try:
        import osmium
    except ImportError as error:
        raise SystemExit("Install pyosmium first: python -m pip install osmium") from error

    work_path = args.work_db or args.output.with_suffix(".work.db")
    for path in (args.output, work_path):
        if path.exists():
            path.unlink()

    work = sqlite3.connect(work_path)
    work.executescript(
        """
        PRAGMA journal_mode=OFF; PRAGMA synchronous=OFF; PRAGMA temp_store=MEMORY;
        CREATE TABLE ways (
          id INTEGER PRIMARY KEY, refs BLOB NOT NULL, highway TEXT NOT NULL,
          speed REAL NOT NULL, direction INTEGER NOT NULL, name TEXT
        );
        CREATE TABLE restrictions (
          id INTEGER PRIMARY KEY AUTOINCREMENT, from_way INTEGER NOT NULL,
          via_node INTEGER NOT NULL, to_way INTEGER NOT NULL, kind TEXT NOT NULL
        );
        """
    )
    needed_nodes: set[int] = set()

    class WayAndRestrictionCollector(osmium.SimpleHandler):
        def __init__(self) -> None:
            super().__init__()
            self.ways: list[tuple] = []
            self.restrictions: list[tuple] = []

        def way(self, way) -> None:
            tags = {tag.k: tag.v for tag in way.tags}
            if not is_drivable(tags):
                return
            refs = tuple(node.ref for node in way.nodes)
            if len(refs) < 2:
                return
            needed_nodes.update(refs)
            self.ways.append((
                way.id, encode_refs(refs), tags["highway"], speed_kmh(tags),
                direction(tags), tags.get("name:fa") or tags.get("name"),
            ))
            if len(self.ways) >= 5_000:
                self.flush_ways()

        def relation(self, relation) -> None:
            tags = {tag.k: tag.v for tag in relation.tags}
            kind = tags.get("restriction:motorcar") or tags.get("restriction")
            if tags.get("type") != "restriction" or not kind:
                return
            from_ways = [m.ref for m in relation.members if m.role == "from" and m.type == "w"]
            to_ways = [m.ref for m in relation.members if m.role == "to" and m.type == "w"]
            via_nodes = [m.ref for m in relation.members if m.role == "via" and m.type == "n"]
            if len(from_ways) == len(to_ways) == len(via_nodes) == 1:
                self.restrictions.append((from_ways[0], via_nodes[0], to_ways[0], kind))
            if len(self.restrictions) >= 2_000:
                self.flush_restrictions()

        def flush_ways(self) -> None:
            work.executemany("INSERT OR REPLACE INTO ways VALUES (?, ?, ?, ?, ?, ?)", self.ways)
            self.ways.clear()

        def flush_restrictions(self) -> None:
            work.executemany(
                "INSERT INTO restrictions(from_way, via_node, to_way, kind) VALUES (?, ?, ?, ?)",
                self.restrictions,
            )
            self.restrictions.clear()

        def finish(self) -> None:
            self.flush_ways()
            self.flush_restrictions()
            work.commit()

    collector = WayAndRestrictionCollector()
    collector.apply_file(str(args.pbf), locations=False)
    collector.finish()
    print(f"Collected roads referencing {len(needed_nodes):,} nodes", file=sys.stderr)

    output = sqlite3.connect(args.output)
    output.executescript(OUTPUT_SCHEMA)

    class NodeCollector(osmium.SimpleHandler):
        def __init__(self) -> None:
            super().__init__()
            self.rows: list[tuple[int, float, float]] = []

        def node(self, node) -> None:
            if node.id not in needed_nodes or not node.location.valid():
                return
            self.rows.append((node.id, node.location.lat, node.location.lon))
            if len(self.rows) >= 20_000:
                self.flush()

        def flush(self) -> None:
            output.executemany("INSERT INTO nodes VALUES (?, ?, ?)", self.rows)
            output.executemany(
                "INSERT INTO nodes_index VALUES (?, ?, ?, ?, ?)",
                ((node_id, lat, lat, lon, lon) for node_id, lat, lon in self.rows),
            )
            self.rows.clear()

    nodes = NodeCollector()
    nodes.apply_file(str(args.pbf), locations=False)
    nodes.flush()
    output.commit()
    needed_nodes.clear()

    coordinates = {
        row[0]: (row[1], row[2])
        for row in output.execute("SELECT id, latitude, longitude FROM nodes")
    }
    edge_id = 0
    edge_rows: list[tuple] = []
    for way_id, refs_blob, _highway, speed, way_direction, name in work.execute(
        "SELECT id, refs, highway, speed, direction, name FROM ways ORDER BY id"
    ):
        refs = decode_refs(refs_blob)
        for from_node, to_node in zip(refs, refs[1:]):
            a, b = coordinates.get(from_node), coordinates.get(to_node)
            if a is None or b is None:
                continue
            distance = haversine(a, b)
            seconds = max(1.0, distance / (speed / 3.6))
            if way_direction != -1:
                edge_id += 1
                edge_rows.append((edge_id, from_node, to_node, distance, seconds, name, way_id))
            if way_direction != 1:
                edge_id += 1
                edge_rows.append((edge_id, to_node, from_node, distance, seconds, name, way_id))
            if len(edge_rows) >= 50_000:
                output.executemany("INSERT INTO edges VALUES (?, ?, ?, ?, ?, ?, ?)", edge_rows)
                edge_rows.clear()
    if edge_rows:
        output.executemany("INSERT INTO edges VALUES (?, ?, ?, ?, ?, ?, ?)", edge_rows)
    output.commit()
    del coordinates
    output.executescript(
        """
        CREATE INDEX edges_from_node_idx ON edges(from_node);
        CREATE INDEX edges_to_node_idx ON edges(to_node);
        CREATE INDEX edges_way_idx ON edges(way_id, from_node, to_node);
        """
    )
    output.commit()

    restriction_rows: list[tuple[int, int, int]] = []
    restriction_count = 0
    for from_way, via_node, to_way, kind in work.execute(
        "SELECT from_way, via_node, to_way, kind FROM restrictions"
    ):
        incoming = [row[0] for row in output.execute(
            "SELECT id FROM edges WHERE way_id = ? AND to_node = ?", (from_way, via_node)
        )]
        allowed = {row[0] for row in output.execute(
            "SELECT id FROM edges WHERE way_id = ? AND from_node = ?", (to_way, via_node)
        )}
        if kind.startswith("only_"):
            outgoing = [row[0] for row in output.execute(
                "SELECT id FROM edges WHERE from_node = ?", (via_node,)
            ) if row[0] not in allowed]
        else:
            outgoing = list(allowed)
        for from_edge in incoming:
            restriction_rows.extend((via_node, from_edge, to_edge) for to_edge in outgoing)
        if len(restriction_rows) >= 20_000:
            output.executemany(
                "INSERT OR IGNORE INTO turn_restrictions VALUES (?, ?, ?)", restriction_rows
            )
            restriction_count += len(restriction_rows)
            restriction_rows.clear()
    if restriction_rows:
        output.executemany(
            "INSERT OR IGNORE INTO turn_restrictions VALUES (?, ?, ?)", restriction_rows
        )
        restriction_count += len(restriction_rows)

    output.execute("ANALYZE")
    output.execute("PRAGMA optimize")
    output.commit()
    output.execute("VACUUM")
    output.close()
    work.close()
    work_path.unlink(missing_ok=True)
    print(
        f"Created {args.output} with {edge_id:,} directed edges and "
        f"{restriction_count:,} prohibited turns"
    )


if __name__ == "__main__":
    main()
