# GeoShield geodata tools

Dev-only, offline preprocessing that derives the bundled State/UT boundary resource
consumed by the backend at `backend/src/main/resources/geo/india-state-ut-boundaries.geojson`.

**This tool tree is not a build or runtime dependency of the backend.** The backend only
reads the committed derived `.geojson` from its classpath. Nothing here runs during
`mvn package`, `mvn test`, or application startup, and `node_modules/` is git-ignored.

## Source dataset

Survey of India (SOI) / NWIC `state_NWIC` administrative-boundary GeoJSON — 36 State/UT
features, all `MultiPolygon`, declared as `urn:ogc:def:crs:EPSG::7755`
(WGS 84 / India NSF LCC) with coordinates stored as projected metres.

The source dataset is Survey of India / NWIC intellectual property and is **deliberately
not committed to this repository**. Obtain it separately and pass its path on the command
line; no path is hard-coded in either these scripts or the backend.

## Files

| File | Purpose |
| --- | --- |
| `lcc-epsg7755.mjs` | Dependency-free forward/inverse Lambert Conformal Conic (2SP, EPSG method 9802) for EPSG:7755, using the Snyder formulas (USGS Professional Paper 1395, pp. 107–109). |
| `validate-projection.mjs` | Cross-validates the projection above against `proj4` over a 0.25° grid covering India plus named landmarks. |
| `preprocess-boundaries.mjs` | The actual derivation: reproject → simplify → round → strip properties → normalize State/UT names. |
| `validate-resource.mjs` | Validates the **derived** file alone by point-in-polygon on known landmarks. Uses no `proj4` and does not read the source. |

## Reproducing the derived resource

```sh
cd tools/geodata
npm install                      # installs proj4, used only to validate the projection

# 1. Prove the hand-written inverse projection matches proj4.
node validate-projection.mjs

# 2. Derive the resource. Pass your own local path to the source GeoJSON.
node --max-old-space-size=4096 preprocess-boundaries.mjs \
  "/path/to/state_NWIC.GeoJSON" \
  ../../backend/src/main/resources/geo/india-state-ut-boundaries.geojson

# 3. Validate the derived resource on its own.
node validate-resource.mjs ../../backend/src/main/resources/geo/india-state-ut-boundaries.geojson
```

Step 1 currently reports agreement with `proj4` to within about 1e-7 m across ~15,000
points. Because EPSG:7755's base geographic CRS is already WGS84, EPSG:7755 → EPSG:4326 is
a pure inverse map projection with **no datum shift**.

## What the derivation does

1. **Reproject** EPSG:7755 projected metres → EPSG:4326 longitude/latitude.
2. **Simplify** each ring with Ramer–Douglas–Peucker at `SIMPLIFY_TOLERANCE_DEGREES`
   (~11 m), preserving ring closure and never dropping below `MIN_RING_POINTS`.
3. **Round** coordinates to `OUTPUT_DECIMALS` = 6 (~0.11 m).
4. **Keep only** the `state_name` and `stcode` properties.
5. **Normalize State/UT names** to the MoRTH `geographicUnit` spelling.

Result: ~47 MiB / ~923k vertices → ~8 MiB / ~393k vertices, with State/UT-level
resolution preserved. The output carries `_derivedFrom` and `_derivation` provenance
members and declares `urn:ogc:def:crs:OGC:1.3:CRS84`.

## State/UT name normalization

The boundary `state_name` must match the MoRTH `geographicUnit` character-for-character,
because the risk module matches on the name. Exactly four of the 36 source names differ;
the other 32 are already identical.

| Source `state_name` | Normalized to | Reason |
| --- | --- | --- |
| `Andaman & Nicobar Island` | `Andaman and Nicobar Islands` | `&` → `and`, and singular → plural |
| `Arunanchal Pradesh` | `Arunachal Pradesh` | source misspelling (extra `n`) |
| `Dadra & Nagar Havelli and Daman & Diu` | `Dadra and Nagar Haveli and Daman and Diu` | `&` → `and` twice, and `Havelli` → `Haveli` |
| `Jammu & Kashmir` | `Jammu and Kashmir` | `&` → `and` |

These mappings are **explicit and exhaustive**. There is deliberately no fuzzy matching,
no string-similarity matching, and no global `&` → `and` replacement. The script throws if
a source `state_name` is not recognized, and also throws if any mapping key never matched a
source feature, so the mapping table cannot silently drift from the dataset.

`StateBoundaryIndexTest` independently asserts that the 36 loaded names equal the 36 MoRTH
`geographicUnit` values exactly, so a regression here fails the build.
