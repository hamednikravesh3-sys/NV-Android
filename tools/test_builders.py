from __future__ import annotations

import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

from build_places import build_database, normalize
from build_routing import direction, haversine, is_drivable, speed_kmh


class PlacesBuilderTest(unittest.TestCase):
    def test_streaming_input_has_stable_codes_and_persian_normalization(self) -> None:
        features = [
            {
                "type": "Feature",
                "id": "node/2",
                "properties": {"name": "تهران", "place": "city"},
                "geometry": {"type": "Point", "coordinates": [51.39, 35.69]},
            },
            {
                "type": "Feature",
                "id": "w7",
                "properties": {"name:fa": "آبادان", "tourism": "attraction"},
                "geometry": {
                    "type": "LineString",
                    "coordinates": [[48.2, 30.3], [48.3, 30.4]],
                },
            },
        ]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "places.geojsonseq"
            source.write_text(
                "".join("\x1e" + json.dumps(item, ensure_ascii=False) + "\n" for item in features),
                encoding="utf-8",
            )
            output = root / "places.db"
            self.assertEqual(2, build_database(source, output))
            with sqlite3.connect(output) as database:
                rows = database.execute(
                    "SELECT code, name, normalized_name FROM places ORDER BY code"
                ).fetchall()
            self.assertEqual([(1, "آبادان", "آبادان"), (2, "تهران", "تهران")], rows)
        self.assertEqual("یکی", normalize("  يكي  "))


class RoutingBuilderTest(unittest.TestCase):
    def test_access_direction_speed_and_distance(self) -> None:
        self.assertTrue(is_drivable({"highway": "primary"}))
        self.assertFalse(is_drivable({"highway": "primary", "motor_vehicle": "no"}))
        self.assertEqual(1, direction({"highway": "primary", "oneway": "yes"}))
        self.assertEqual(-1, direction({"highway": "primary", "oneway": "-1"}))
        self.assertAlmostEqual(80.0, speed_kmh({"highway": "primary"}))
        self.assertAlmostEqual(80.4672, speed_kmh({"highway": "primary", "maxspeed": "50 mph"}), places=3)
        self.assertGreater(haversine((35.0, 51.0), (35.01, 51.0)), 1_000)


if __name__ == "__main__":
    unittest.main()
