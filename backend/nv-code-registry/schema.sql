CREATE TABLE IF NOT EXISTS nv_codes (
  code TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  latitude REAL NOT NULL,
  longitude REAL NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_nv_codes_code ON nv_codes(code);
