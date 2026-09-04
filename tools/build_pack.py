#!/usr/bin/env python3
"""Create a reproducible, checksummed NV Iran data pack."""

from __future__ import annotations

import argparse
import hashlib
import json
import sqlite3
import zipfile
from datetime import datetime, timezone
from pathlib import Path


def digest(path: Path) -> str:
    result = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def sqlite_count(path: Path, table: str) -> int:
    with sqlite3.connect(f"file:{path}?mode=ro", uri=True) as database:
        return int(database.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0])


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--map", type=Path, required=True)
    parser.add_argument("--places", type=Path, required=True)
    parser.add_argument("--routing", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--osm-timestamp", default="unknown")
    args = parser.parse_args()
    files = {"iran.map": args.map, "places.db": args.places, "routing.db": args.routing}
    manifest = {
        "schemaVersion": 2,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "osmTimestamp": args.osm_timestamp,
        "attribution": "© OpenStreetMap contributors, ODbL 1.0",
        "placeCount": sqlite_count(args.places, "places"),
        "routingNodeCount": sqlite_count(args.routing, "nodes"),
        "routingEdgeCount": sqlite_count(args.routing, "edges"),
        "files": {
            name: {"sha256": digest(path), "bytes": path.stat().st_size}
            for name, path in files.items()
        },
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, "w", allowZip64=True) as archive:
        archive.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2))
        for name, path in files.items():
            compression = zipfile.ZIP_STORED if name.endswith(".map") else zipfile.ZIP_DEFLATED
            archive.write(path, name, compress_type=compression, compresslevel=6)
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
