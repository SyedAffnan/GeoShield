# GeoShield

GeoShield is an AI-powered tourist safety and emergency-response system.

This repository contains the Spring Boot backend and Flutter client project structure.

## Projects

- `backend/` — Spring Boot 3.x, Java 21 modular monolith.
- `mobile/` — Flutter 3.x Android-first mobile client.

## Backend modules

`identity`, `location`, `historicaldata`, `risk`, `sos`, `incident`, `notification`, `emergencyservices`, and `admin` are the backend module boundaries. Modules communicate through service interfaces.

## Implementation status

- Implemented: Identity/Auth, Tourist Profile, Emergency Contacts, Device Tokens, and Location.
- Implemented: Historical Data Processing and `HistoricalSafetyRecords` persistence. The batch pipeline normalizes the approved MoRTH Road Accidents in India 2024 Annexure-4 CSV and NCRB Crime in India 2023 Table 13A.2 CSV into one aggregate-statistics table.
- IMPLEMENTED AND VERIFIED: Incident reporting, authenticated owner-scoped incident reads, request idempotency, and SHA-256 integrity verification.
- IMPLEMENTED: Baseline Contextual Risk Engine — deterministic weighted scoring, explainability, safety recommendations, and `RiskScores` audit persistence. It does not synthesize unavailable contextual data.
- IMPLEMENTED AND VERIFIED: Offline GPS → State/UT resolution — deterministic point-in-polygon lookup against a bundled derived boundary resource, feeding the historical-incident risk factor. No external geocoding service and no personal filesystem path is used.
- IMPLEMENTED AND VERIFIED: Risk API — `GET /api/v1/risk` returns the authenticated tourist's current baseline risk using their stored current location.
- Not implemented: Random Forest training, AI/ML training, Contextual Risk Fusion, SOS, Notifications, Emergency Services, Offline Sync, and Geofencing. Geographic resolution is at State/UT granularity only; city, district, and sub-district resolution are not implemented.

Historical ingestion is opt-in and has no public CRUD endpoint. Set `GEOSHIELD_HISTORICAL_INGESTION_ENABLED=true` and provide both source paths through `GEOSHIELD_HISTORICAL_MORTH_SOURCE` and `GEOSHIELD_HISTORICAL_NCRB_SOURCE`. A verified local import processed 220 MoRTH records and 66 NCRB records; repeating the same source files is idempotent.

`HistoricalSafetyRecords` holds aggregate government statistics only. It does not contain individual incidents, GPS coordinates, exact timestamps, or tourist identities.

## State/UT boundary resource

GPS coordinates are resolved to a State/UT by deterministic point-in-polygon lookup (ray casting with MultiPolygon and polygon-hole handling) against a bundled classpath resource, `backend/src/main/resources/geo/india-state-ut-boundaries.geojson`. No bounding boxes, centroids, coordinate-range rules, or external geocoding services are used, and a coordinate that no polygon contains returns an explicit unresolved result rather than a guess.

This bundled file is a **derived artifact**, not the original dataset. It is produced offline from the Survey of India (SOI) / NWIC `state_NWIC` administrative-boundary GeoJSON by the auditable, dependency-free scripts under `tools/geodata/` (a dev-only tool tree that is not a build or runtime dependency). The derivation:

- **Reprojects** every coordinate from the source CRS **EPSG:7755** (WGS 84 / India NSF LCC, projected metres) to **EPSG:4326** (WGS84 longitude/latitude). Because EPSG:7755's base geographic CRS is already WGS84, this is a pure inverse Lambert Conformal Conic map projection with no datum shift; the inverse is cross-validated against `proj4` to within ~1e-7 m over ~15,000 test points.
- **Simplifies** each polygon with Ramer–Douglas–Peucker at a ~11 m tolerance and **rounds** coordinates to 6 decimal places (~0.11 m), reducing the resource from ~47 MiB / ~923k vertices to ~8 MiB / ~393k vertices while preserving State/UT-level resolution.
- **Keeps only** the `state_name` and `stcode` properties.
- Applies **four explicit, documented State/UT name normalizations** so the resolved name matches the MoRTH `geographicUnit` character-for-character (no fuzzy matching and no global `&` replacement): `Andaman & Nicobar Island` → `Andaman and Nicobar Islands`; `Arunanchal Pradesh` → `Arunachal Pradesh`; `Dadra & Nagar Havelli and Daman & Diu` → `Dadra and Nagar Haveli and Daman and Diu`; `Jammu & Kashmir` → `Jammu and Kashmir`. The other 32 names are already identical. The script fails fast if a source name is unrecognized or a mapping key never matches.

The source dataset is Survey of India / NWIC intellectual property and is **not** copied into this repository; only the derived, reprojected, simplified resource is bundled. To regenerate it, see `tools/geodata/README` and run the preprocessing script against a local copy of the source GeoJSON, passing that path as an argument (no path is hard-coded).

Known limitation: each State/UT is simplified independently, so points within roughly the simplification tolerance of a shared border may fall in a small gap (returned as unresolved) or an overlap (resolved deterministically to the first matching polygon in dataset order). This is well within consumer-GPS error and does not affect interior resolution.

## Configuration

Copy `backend/.env.example` to an environment-specific secret store or inject its values as environment variables. Do not commit credentials or JWT signing keys.

Open decisions from the approved Architecture/SRS are marked with `TODO(architecture-open)` comments.
