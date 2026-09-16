CREATE TABLE IF NOT EXISTS nv_codes (
  code INTEGER PRIMARY KEY AUTOINCREMENT,
  location_key TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  latitude REAL NOT NULL,
  longitude REAL NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_nv_codes_code ON nv_codes(code);
CREATE UNIQUE INDEX IF NOT EXISTS idx_nv_codes_location_key ON nv_codes(location_key);
CREATE INDEX IF NOT EXISTS idx_nv_codes_location ON nv_codes(latitude, longitude);
