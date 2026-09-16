-- Keep server-issued NV codes out of the offline place-code and OSM-derived ranges.
-- Future AUTOINCREMENT values start at 5,000,000,000,000 while existing rows remain valid.
INSERT INTO sqlite_sequence(name, seq)
SELECT 'nv_codes', 4999999999999
WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = 'nv_codes');

UPDATE sqlite_sequence
SET seq = CASE WHEN seq < 4999999999999 THEN 4999999999999 ELSE seq END
WHERE name = 'nv_codes';
