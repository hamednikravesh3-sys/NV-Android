-- NV Code registry migration: legacy schema -> location-key uniqueness.
--
-- This migration is intentionally explicit and one-way. Run it only on a
-- database that still has the legacy nv_codes table without location_key.
-- It preserves the lowest (oldest) numeric NV Code for each normalized
-- coordinate and removes later duplicates so the production uniqueness
-- invariant can be enforced without a failed UNIQUE-index creation.

BEGIN TRANSACTION;

CREATE TABLE nv_codes_v2 (
  code INTEGER PRIMARY KEY AUTOINCREMENT,
  location_key TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  latitude REAL NOT NULL,
  longitude REAL NOT NULL,
  created_at INTEGER NOT NULL
);

INSERT INTO nv_codes_v2 (code, location_key, name, latitude, longitude, created_at)
SELECT
  legacy.code,
  printf('%.6f,%.6f', legacy.latitude, legacy.longitude) AS location_key,
  legacy.name,
  legacy.latitude,
  legacy.longitude,
  legacy.created_at
FROM nv_codes AS legacy
WHERE legacy.code = (
  SELECT MIN(candidate.code)
  FROM nv_codes AS candidate
  WHERE printf('%.6f,%.6f', candidate.latitude, candidate.longitude) =
        printf('%.6f,%.6f', legacy.latitude, legacy.longitude)
);

DROP TABLE nv_codes;
ALTER TABLE nv_codes_v2 RENAME TO nv_codes;

CREATE UNIQUE INDEX idx_nv_codes_code ON nv_codes(code);
CREATE UNIQUE INDEX idx_nv_codes_location_key ON nv_codes(location_key);
CREATE INDEX idx_nv_codes_location ON nv_codes(latitude, longitude);

COMMIT;
