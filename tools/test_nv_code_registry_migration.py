import sqlite3
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MIGRATION_V2 = ROOT / "backend" / "nv-code-registry" / "migrations" / "0002_location_key_uniqueness.sql"
MIGRATION_V3 = ROOT / "backend" / "nv-code-registry" / "migrations" / "0003_reserve_registry_code_range.sql"


LEGACY_SCHEMA = """
CREATE TABLE nv_codes (
  code INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  latitude REAL NOT NULL,
  longitude REAL NOT NULL,
  created_at INTEGER NOT NULL
);
"""


class NvCodeRegistryMigrationTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.executescript(LEGACY_SCHEMA)

    def tearDown(self):
        self.db.close()

    def run_migration(self):
        self.db.executescript(MIGRATION_V2.read_text(encoding="utf-8"))

    def run_registry_range_migration(self):
        self.db.executescript(MIGRATION_V3.read_text(encoding="utf-8"))

    def test_migration_keeps_oldest_code_and_enforces_location_uniqueness(self):
        rows = [
            (100, "قدیمی", 35.6891984, 51.3889736, 1000),
            (101, "تکراری", 35.68919849, 51.38897359, 2000),
            (102, "مکان دیگر", 36.260462, 59.616755, 3000),
        ]
        self.db.executemany(
            "INSERT INTO nv_codes(code, name, latitude, longitude, created_at) VALUES (?, ?, ?, ?, ?)",
            rows,
        )
        self.run_migration()

        migrated = self.db.execute(
            "SELECT code, location_key, name FROM nv_codes ORDER BY code"
        ).fetchall()
        self.assertEqual(
            migrated,
            [
                (100, "35.689198,51.388974", "قدیمی"),
                (102, "36.260462,59.616755", "مکان دیگر"),
            ],
        )

        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute(
                "INSERT INTO nv_codes(location_key, name, latitude, longitude, created_at) VALUES (?, ?, ?, ?, ?)",
                ("35.689198,51.388974", "نباید ثبت شود", 35.6891984, 51.3889736, 4000),
            )

    def test_autoincrement_continues_above_highest_preserved_code(self):
        self.db.execute(
            "INSERT INTO nv_codes(code, name, latitude, longitude, created_at) VALUES (?, ?, ?, ?, ?)",
            (987654, "آخرین کد", 32.654627, 51.667983, 1000),
        )
        self.run_migration()

        cursor = self.db.execute(
            "INSERT INTO nv_codes(location_key, name, latitude, longitude, created_at) VALUES (?, ?, ?, ?, ?)",
            ("29.591768,52.583698", "کد بعدی", 29.591768, 52.583698, 2000),
        )
        self.assertGreater(cursor.lastrowid, 987654)

    def test_registry_range_migration_moves_future_codes_to_reserved_namespace(self):
        self.run_migration()
        self.run_registry_range_migration()

        cursor = self.db.execute(
            "INSERT INTO nv_codes(location_key, name, latitude, longitude, created_at) VALUES (?, ?, ?, ?, ?)",
            ("35.700000,51.400000", "کد مرکزی", 35.7, 51.4, 3000),
        )
        self.assertGreaterEqual(cursor.lastrowid, 5_000_000_000_000)
        self.assertLess(cursor.lastrowid, 6_000_000_000_000)

    def test_registry_range_migration_preserves_existing_codes(self):
        self.db.execute(
            "INSERT INTO nv_codes(code, name, latitude, longitude, created_at) VALUES (?, ?, ?, ?, ?)",
            (1234, "کد قدیمی", 35.6892, 51.3890, 1000),
        )
        self.run_migration()
        self.run_registry_range_migration()
        code = self.db.execute("SELECT code FROM nv_codes WHERE name='کد قدیمی'").fetchone()[0]
        self.assertEqual(code, 1234)

    def test_expected_indexes_exist_after_migration(self):
        self.run_migration()
        indexes = {
            row[1]
            for row in self.db.execute("PRAGMA index_list('nv_codes')").fetchall()
        }
        self.assertIn("idx_nv_codes_code", indexes)
        self.assertIn("idx_nv_codes_location_key", indexes)
        self.assertIn("idx_nv_codes_location", indexes)


if __name__ == "__main__":
    unittest.main()
