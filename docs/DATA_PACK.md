# NV Iran data-pack contract

The app downloads exactly one versioned file named iran.nvpack. It is a ZIP
archive with these files at its root:

| File | Purpose |
|---|---|
| manifest.json | Version, generation date, OSM attribution and checksums |
| iran.mbtiles | Raster map tiles for the Iran bounding polygon |
| places.db | Every named OSM feature in Iran, with deterministic codes 1…N |
| routing.db | Directed driving graph and prohibited-turn records |

The archive is installed only after path-safety, required-file and optional
SHA-256 checks. No world map is requested by the Android client.

## Places schema

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

Codes are assigned after sorting by normalized Persian name, OSM type and OSM
identifier. A rebuild from the same OSM extract therefore produces the same
sequence.

## Routing schema

    CREATE TABLE nodes (
      id INTEGER PRIMARY KEY,
      latitude REAL NOT NULL,
      longitude REAL NOT NULL
    );
    CREATE TABLE edges (
      id INTEGER PRIMARY KEY,
      from_node INTEGER NOT NULL,
      to_node INTEGER NOT NULL,
      distance_m REAL NOT NULL,
      travel_seconds REAL NOT NULL,
      road_name TEXT
    );
    CREATE INDEX edges_from_node_idx ON edges(from_node);
    CREATE TABLE turn_restrictions (
      via_node INTEGER NOT NULL,
      from_edge INTEGER NOT NULL,
      to_edge INTEGER NOT NULL,
      PRIMARY KEY (via_node, from_edge, to_edge)
    );

One-way roads are represented as directed edges. A two-way road has two edges.
OSM restriction relations are compiled into turn_restrictions. The app's A*
state includes the incoming edge, so prohibited turns cannot be selected.

## Release location

Publish the data pack as the iran.nvpack asset in the map-v1 GitHub Release.
The app URL is configured in app/build.gradle.kts. For production, set
IRAN_PACK_SHA256 to the exact digest before publishing an APK.
